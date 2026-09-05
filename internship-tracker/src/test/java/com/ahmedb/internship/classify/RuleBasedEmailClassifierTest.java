package com.ahmedb.internship.classify;

import static org.assertj.core.api.Assertions.assertThat;

import com.ahmedb.internship.domain.ApplicationStatus;
import com.ahmedb.internship.ingest.IngestedEmail;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class RuleBasedEmailClassifierTest {

    private static final Instant RECEIVED = Instant.parse("2026-09-05T09:00:00Z");

    private final RuleBasedEmailClassifier classifier = new RuleBasedEmailClassifier();

    private final ClassificationContext context =
            new ClassificationContext(
                    List.of(
                            new ClassificationContext.KnownCompany(1L, "Stripe", Set.of("stripe.com")),
                            new ClassificationContext.KnownCompany(2L, "Datadog", Set.of("datadoghq.com")),
                            new ClassificationContext.KnownCompany(3L, "Jane Street", Set.of("janestreet.com"))));

    private IngestedEmail email(String subject, String from, String snippet) {
        return new IngestedEmail("msg-1", "thread-1", subject, from, null, RECEIVED, snippet);
    }

    private IngestedEmail email(String subject, String from, String displayName, String snippet) {
        return new IngestedEmail("msg-1", "thread-1", subject, from, displayName, RECEIVED, snippet);
    }

    private Classification classify(IngestedEmail email) {
        return classifier.classify(email, context);
    }

    @Nested
    @DisplayName("status mapping")
    class StatusMapping {

        @ParameterizedTest(name = "{0} -> {1}")
        @CsvSource({
            "'Thank you for applying to Stripe', APPLIED",
            "'We received your application', APPLIED",
            "'Application received: Software Engineer Intern', APPLIED",
            "'Your online assessment for Datadog', OA_PENDING",
            "'Invitation to complete a coding challenge', OA_PENDING",
            "'Complete your HackerRank assessment', OA_PENDING",
            "'We have received your assessment', OA_SUBMITTED",
            "'Thank you for completing the technical assessment', OA_SUBMITTED",
            "'Interview invitation - Stripe', INTERVIEW",
            "'Scheduling your phone screen', INTERVIEW",
            "'We would like to schedule an interview with you', INTERVIEW",
            "'Final round interview at Jane Street', FINAL_ROUND",
            "'Superday invitation', FINAL_ROUND",
            "'Onsite loop scheduling', FINAL_ROUND",
            "'We are pleased to offer you the internship', OFFER",
            "'Your offer letter from Datadog', OFFER",
            "'We regret to inform you', REJECTED",
            "'Update on your application - not moving forward', REJECTED",
            "'Your application was unsuccessful', REJECTED",
        })
        void mapsSubjectToStatus(String subject, ApplicationStatus expected) {
            Classification result = classify(email(subject, "careers@stripe.com", ""));

            assertThat(result.outcome()).isEqualTo(Classification.Outcome.TRANSITION);
            assertThat(result.newStatus()).isEqualTo(expected);
            assertThat(result.classifierId()).isEqualTo("rules:v1");
            assertThat(result.reason()).isNotBlank();
        }
    }

    @Nested
    @DisplayName("precedence between overlapping signals")
    class Precedence {

        @Test
        @DisplayName("a rejection that opens by thanking you for applying is still a rejection")
        void rejectionBeatsApplicationAcknowledgement() {
            Classification result =
                    classify(
                            email(
                                    "Your application to Stripe",
                                    "no-reply@greenhouse.io",
                                    "Thank you for applying to Stripe. After careful consideration, "
                                            + "we regret to inform you that we will not be moving forward."));

            assertThat(result.newStatus()).isEqualTo(ApplicationStatus.REJECTED);
        }

        @Test
        @DisplayName("a rejection after a final round does not read as a final round")
        void rejectionBeatsInterviewMention() {
            Classification result =
                    classify(
                            email(
                                    "Following up on your final round interview",
                                    "recruiting@stripe.com",
                                    "Thank you for taking the time to interview. Unfortunately, we have "
                                            + "decided to move forward with other candidates."));

            assertThat(result.newStatus()).isEqualTo(ApplicationStatus.REJECTED);
        }

        @Test
        @DisplayName("a superday invitation is a final round, not a generic interview")
        void finalRoundBeatsInterview() {
            Classification result =
                    classify(
                            email(
                                    "Interview invitation: Superday",
                                    "recruiting@janestreet.com",
                                    "We would like to schedule an interview for our final round superday."));

            assertThat(result.newStatus()).isEqualTo(ApplicationStatus.FINAL_ROUND);
        }

        @Test
        @DisplayName("an assessment confirmation is not a fresh assessment invitation")
        void submittedBeatsPending() {
            Classification result =
                    classify(
                            email(
                                    "Your online assessment",
                                    "no-reply@hackerrank.com",
                                    "We have received your assessment submission. Thank you for completing it."));

            assertThat(result.newStatus()).isEqualTo(ApplicationStatus.OA_SUBMITTED);
        }

        @Test
        @DisplayName("an assessment invitation outranks the application acknowledgement it repeats")
        void assessmentBeatsApplied() {
            Classification result =
                    classify(
                            email(
                                    "Next step: online assessment",
                                    "no-reply@greenhouse.io",
                                    "Thank you for applying! Please complete the online assessment within 5 days."));

            assertThat(result.newStatus()).isEqualTo(ApplicationStatus.OA_PENDING);
        }

        @Test
        @DisplayName("an offer is not read as the interview it refers back to")
        void offerBeatsInterview() {
            Classification result =
                    classify(
                            email(
                                    "Your offer from Datadog",
                                    "recruiting@datadoghq.com",
                                    "Following your final round interview, we are pleased to offer you a position."));

            assertThat(result.newStatus()).isEqualTo(ApplicationStatus.OFFER);
        }
    }

    @Nested
    @DisplayName("noise")
    class Noise {

        @ParameterizedTest
        @ValueSource(
                strings = {
                    "Job alert: 15 new software engineering internships",
                    "Jobs you may be interested in",
                    "Top job picks for you",
                    "5 new jobs matching your search",
                    "Your weekly job digest",
                })
        @DisplayName("job alerts are ignored, not classified")
        void jobAlertsAreIgnored(String subject) {
            Classification result = classify(email(subject, "jobs-noreply@linkedin.com", ""));

            assertThat(result.outcome()).isEqualTo(Classification.Outcome.IGNORE);
            assertThat(result.newStatus()).isNull();
        }

        @Test
        @DisplayName("job board chatter is ignored only when it comes from a job board")
        void chatterIsIgnoredOnlyFromJobBoards() {
            assertThat(classify(email("Who viewed your profile", "noreply@linkedin.com", "")).outcome())
                    .isEqualTo(Classification.Outcome.IGNORE);

            // The same phrasing from a company address is not automatically noise.
            assertThat(classify(email("Who viewed your profile", "someone@stripe.com", "")).outcome())
                    .isEqualTo(Classification.Outcome.ABSTAIN);
        }

        @Test
        @DisplayName("an alert that borrows pipeline vocabulary is still an alert")
        void alertsOutrankTransitionPhrasing() {
            Classification result =
                    classify(
                            email(
                                    "Job alert: companies are hiring interns",
                                    "jobs@indeed.com",
                                    "Thank you for your interest in these roles. Apply now!"));

            assertThat(result.outcome()).isEqualTo(Classification.Outcome.IGNORE);
        }
    }

    @Nested
    @DisplayName("abstention")
    class Abstention {

        @Test
        @DisplayName("unrecognised mail abstains rather than guessing")
        void unknownMailAbstains() {
            Classification result =
                    classify(email("Lunch tomorrow?", "friend@gmail.com", "Are you free at noon?"));

            assertThat(result.outcome()).isEqualTo(Classification.Outcome.ABSTAIN);
            assertThat(result.newStatus()).isNull();
            assertThat(result.reason()).isEqualTo("no rule matched");
        }

        @Test
        @DisplayName("an email with nothing to match on abstains instead of throwing")
        void emptyEmailAbstains() {
            assertThat(classify(email(null, "someone@example.com", null)).outcome())
                    .isEqualTo(Classification.Outcome.ABSTAIN);
            assertThat(classify(email("   ", "someone@example.com", "  ")).outcome())
                    .isEqualTo(Classification.Outcome.ABSTAIN);
        }

        @Test
        @DisplayName("a malformed sender does not break classification")
        void malformedSenderIsTolerated() {
            IngestedEmail broken =
                    new IngestedEmail("m", "t", "Thank you for applying", "not-an-address", null, RECEIVED, null);

            Classification result = classifier.classify(broken, context);

            assertThat(result.newStatus()).isEqualTo(ApplicationStatus.APPLIED);
        }

        @Test
        void nullEmailAndNullContextAreTolerated() {
            assertThat(classifier.classify(null, context).outcome())
                    .isEqualTo(Classification.Outcome.ABSTAIN);
            assertThat(classifier.classify(email("Thank you for applying", "a@stripe.com", ""), null).newStatus())
                    .isEqualTo(ApplicationStatus.APPLIED);
        }
    }

    @Nested
    @DisplayName("hints and confidence")
    class HintsAndConfidence {

        @Test
        @DisplayName("a tracked sender domain names the company")
        void companyHintFromTrackedDomain() {
            Classification result = classify(email("Thank you for applying", "careers@stripe.com", ""));

            assertThat(result.companyHint()).isEqualTo("Stripe");
        }

        @Test
        @DisplayName("an ATS domain identifies the vendor, so the company comes from the text")
        void companyHintFromTextWhenSenderIsAts() {
            Classification result =
                    classify(
                            email(
                                    "Your application to Jane Street",
                                    "no-reply@greenhouse.io",
                                    "Jane Street received your application."));

            assertThat(result.companyHint()).isEqualTo("Jane Street");
        }

        @Test
        @DisplayName("an untracked sender falls back to its display name, minus the boilerplate")
        void companyHintFromDisplayName() {
            Classification result =
                    classify(
                            email(
                                    "Thank you for applying",
                                    "no-reply@greenhouse.io",
                                    "Ramp Recruiting Team",
                                    "We received your application."));

            assertThat(result.companyHint()).isEqualTo("Ramp");
        }

        @Test
        @DisplayName("an untracked company domain becomes the company name")
        void companyHintFromDomain() {
            Classification result = classify(email("Thank you for applying", "careers@ramp.com", ""));

            assertThat(result.companyHint()).isEqualTo("Ramp");
        }

        @Test
        @DisplayName("the role title is pulled off the subject line")
        void roleHintFromSubject() {
            assertThat(
                            classify(
                                            email(
                                                    "Application received for Software Engineer Intern at Stripe",
                                                    "a@stripe.com",
                                                    ""))
                                    .roleHint())
                    .isEqualTo("Software Engineer Intern");
            assertThat(
                            classify(
                                            email(
                                                    "Thank you for applying",
                                                    "a@stripe.com",
                                                    "Your application to Backend Engineering Intern was received."))
                                    .roleHint())
                    .isNull(); // role hints come from the subject only; the preview is not parsed
        }

        @Test
        @DisplayName("hints are only attached to a transition")
        void hintsAbsentWhenNotATransition() {
            // A subject naming a role but matching no rule is ambiguous, so it abstains -- and an
            // abstention carries no company or role to act on.
            Classification abstained =
                    classify(email("Your application for Software Engineer Intern at Stripe", "a@stripe.com", ""));

            assertThat(abstained.outcome()).isEqualTo(Classification.Outcome.ABSTAIN);
            assertThat(abstained.roleHint()).isNull();
            assertThat(abstained.companyHint()).isNull();
        }

        @Test
        @DisplayName("no role is guessed when the subject does not carry one")
        void roleHintAbsent() {
            assertThat(classify(email("Thank you for applying", "a@stripe.com", "")).roleHint()).isNull();
        }

        @Test
        @DisplayName("a recruiting sender corroborates the pattern and raises confidence")
        void confidenceRisesForRecruitingSenders() {
            double fromAts = classify(email("Thank you for applying", "no-reply@greenhouse.io", "")).confidence();
            double fromTracked = classify(email("Thank you for applying", "x@stripe.com", "")).confidence();
            double fromUnknown = classify(email("Thank you for applying", "x@unknown.example", "")).confidence();

            assertThat(fromAts).isGreaterThan(fromUnknown);
            assertThat(fromTracked).isGreaterThan(fromUnknown);
            assertThat(fromAts).isLessThanOrEqualTo(1.0);
        }

        @Test
        @DisplayName("confidence always stays a probability")
        void confidenceStaysInRange() {
            for (String subject :
                    List.of(
                            "We regret to inform you",
                            "We are pleased to offer you",
                            "Final round interview",
                            "Thank you for applying")) {
                assertThat(classify(email(subject, "no-reply@greenhouse.io", "")).confidence())
                        .isBetween(0.0, 1.0);
            }
        }
    }
}
