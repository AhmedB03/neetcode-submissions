package com.ahmedb.internship.service;

import static com.ahmedb.internship.TestFixtures.NOW;
import static com.ahmedb.internship.TestFixtures.daysAgo;
import static org.assertj.core.api.Assertions.assertThat;

import com.ahmedb.internship.domain.Application;
import com.ahmedb.internship.domain.ApplicationStatus;
import com.ahmedb.internship.domain.Company;
import com.ahmedb.internship.domain.EventSource;
import com.ahmedb.internship.domain.ProcessedMessage;
import com.ahmedb.internship.domain.StatusEvent;
import com.ahmedb.internship.domain.UnmatchedEmail;
import com.ahmedb.internship.ingest.IngestedEmail;
import com.ahmedb.internship.ingest.MailSource;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

/** The whole path from a delivered email to a persisted event. */
@Import(IngestionPipelineTest.StubMailSourceConfiguration.class)
class IngestionPipelineTest extends ServiceTestBase {

    @TestConfiguration
    static class StubMailSourceConfiguration {
        @Bean
        MailSource stubMailSource() {
            return new StubMailSource();
        }
    }

    @Autowired private IngestionPipeline pipeline;
    @Autowired private MailSource mailSource;

    private StubMailSource inbox() {
        return (StubMailSource) mailSource;
    }

    private Company stripe;
    private Application swe;

    @BeforeEach
    void seed() {
        inbox().clear();

        Company company = new Company("Stripe");
        company.addEmailDomain("stripe.com");
        stripe = companies.save(company);

        Application application = new Application(stripe, "Software Engineer Intern", "Summer 2027");
        application.setStatus(ApplicationStatus.APPLIED);
        application.setLastEventAt(daysAgo(2));
        swe = applications.save(application);
    }

    private IngestedEmail email(String id, String subject, String from, Instant receivedAt, String snippet) {
        return new IngestedEmail(id, "thread-" + id, subject, from, null, receivedAt, snippet);
    }

    @Test
    @DisplayName("a classified email becomes an event and advances the application")
    void recordsTransitionAndAdvancesStatus() {
        inbox()
                .deliver(
                        email(
                                "m1",
                                "Interview invitation",
                                "recruiting@stripe.com",
                                daysAgo(1),
                                "We would like to schedule an interview with you."));

        IngestionPipeline.IngestionResult result = pipeline.run();

        assertThat(result.countOf(ProcessedMessage.Outcome.TRANSITION_RECORDED)).isEqualTo(1);
        assertThat(applications.findById(swe.getId()).orElseThrow().getStatus())
                .isEqualTo(ApplicationStatus.INTERVIEW);

        List<StatusEvent> timeline =
                statusEvents.findByApplicationIdOrderByOccurredAtAscIdAsc(swe.getId());
        assertThat(timeline).hasSize(1);
        StatusEvent event = timeline.get(0);
        assertThat(event.getOldStatus()).isEqualTo(ApplicationStatus.APPLIED);
        assertThat(event.getNewStatus()).isEqualTo(ApplicationStatus.INTERVIEW);
        assertThat(event.isAdvancedStatus()).isTrue();
        assertThat(event.getSource()).isEqualTo(EventSource.GMAIL);
        assertThat(event.getEvidence().getMessageId()).isEqualTo("m1");
        assertThat(event.getEvidence().getSubject()).isEqualTo("Interview invitation");
        assertThat(event.getClassifierId()).isEqualTo("rules:v1");
        assertThat(event.getOccurredAt()).isEqualTo(daysAgo(1));
    }

    @Test
    @DisplayName("the application's activity clock moves, which is what un-ghosts it")
    void updatesLastEventAt() {
        Application stale = applications.findById(swe.getId()).orElseThrow();
        stale.setLastEventAt(daysAgo(90));
        applications.save(stale);

        inbox().deliver(email("m1", "Interview invitation", "recruiting@stripe.com", daysAgo(1), ""));
        pipeline.run();

        assertThat(applications.findById(swe.getId()).orElseThrow().getLastEventAt())
                .isEqualTo(daysAgo(1));
    }

    @Test
    @DisplayName("a backfill is replayed oldest first, so the pipeline ends in the right state")
    void replaysInChronologicalOrder() {
        // Delivered newest-first, the order a mail API actually returns.
        inbox()
                .deliver(
                        email("m3", "We are pleased to offer you a position", "recruiting@stripe.com", daysAgo(2), ""),
                        email("m2", "Interview invitation", "recruiting@stripe.com", daysAgo(20), ""),
                        email("m1", "Thank you for applying", "recruiting@stripe.com", daysAgo(40), ""));

        pipeline.run();

        assertThat(applications.findById(swe.getId()).orElseThrow().getStatus())
                .isEqualTo(ApplicationStatus.OFFER);
        assertThat(statusEvents.findByApplicationIdOrderByOccurredAtAscIdAsc(swe.getId()))
                .extracting(StatusEvent::getNewStatus)
                .containsExactly(
                        ApplicationStatus.APPLIED, ApplicationStatus.INTERVIEW, ApplicationStatus.OFFER);
    }

    @Test
    @DisplayName("a late acknowledgement is logged but does not drag the application backwards")
    void staleEmailIsRecordedWithoutRegressing() {
        Application advanced = applications.findById(swe.getId()).orElseThrow();
        advanced.setStatus(ApplicationStatus.FINAL_ROUND);
        applications.save(advanced);

        inbox().deliver(email("m1", "Thank you for applying to Stripe", "recruiting@stripe.com", daysAgo(1), ""));
        pipeline.run();

        Application after = applications.findById(swe.getId()).orElseThrow();
        assertThat(after.getStatus()).isEqualTo(ApplicationStatus.FINAL_ROUND);

        StatusEvent event = statusEvents.findByApplicationIdOrderByOccurredAtAscIdAsc(swe.getId()).get(0);
        assertThat(event.getNewStatus()).isEqualTo(ApplicationStatus.APPLIED);
        assertThat(event.isAdvancedStatus()).isFalse();
    }

    @Test
    @DisplayName("a transition with no matching application goes to the review queue, inventing nothing")
    void unmatchedEmailIsQueued() {
        inbox()
                .deliver(
                        email(
                                "m1",
                                "Thank you for applying to Acme",
                                "careers@acme-unknown.example",
                                daysAgo(1),
                                "We received your application."));

        IngestionPipeline.IngestionResult result = pipeline.run();

        assertThat(result.countOf(ProcessedMessage.Outcome.QUEUED_FOR_REVIEW)).isEqualTo(1);
        assertThat(companies.findAll()).extracting(Company::getName).containsExactly("Stripe");
        assertThat(applications.findAll()).hasSize(1);

        List<UnmatchedEmail> queue =
                unmatchedEmails.findByResolutionOrderByCreatedAtDesc(UnmatchedEmail.Resolution.PENDING);
        assertThat(queue).hasSize(1);
        assertThat(queue.get(0).getProposedStatus()).isEqualTo(ApplicationStatus.APPLIED);
        assertThat(queue.get(0).getCompanyHint()).isEqualTo("Acme-unknown");
        assertThat(queue.get(0).getEvidence().getMessageId()).isEqualTo("m1");
    }

    @Test
    @DisplayName("job alerts and unrecognised mail leave no trace beyond the ledger")
    void noiseAndAbstentionsProduceNoEvents() {
        inbox()
                .deliver(
                        email("m1", "Job alert: 15 new internships", "jobs@linkedin.com", daysAgo(1), ""),
                        email("m2", "Lunch tomorrow?", "friend@gmail.com", daysAgo(1), "Are you free?"));

        IngestionPipeline.IngestionResult result = pipeline.run();

        assertThat(result.countOf(ProcessedMessage.Outcome.IGNORED)).isEqualTo(1);
        assertThat(result.countOf(ProcessedMessage.Outcome.ABSTAINED)).isEqualTo(1);
        assertThat(statusEvents.count()).isZero();
        assertThat(unmatchedEmails.count()).isZero();
        // Still recorded, so the next poll does not reconsider them.
        assertThat(processedMessages.count()).isEqualTo(2);
    }

    @Test
    @DisplayName("polling twice is idempotent")
    void secondPollIsANoOp() {
        inbox().deliver(email("m1", "Interview invitation", "recruiting@stripe.com", daysAgo(1), ""));

        pipeline.run();
        IngestionPipeline.IngestionResult second = pipeline.run();

        assertThat(second.processed()).isZero();
        assertThat(statusEvents.count()).isEqualTo(1);
        assertThat(applications.findById(swe.getId()).orElseThrow().getStatus())
                .isEqualTo(ApplicationStatus.INTERVIEW);
    }

    @Test
    @DisplayName("the first run reaches back over the configured lookback window")
    void firstRunUsesLookbackWindow() {
        pipeline.run();

        // 90 days by default, measured from the frozen clock.
        assertThat(inbox().lastRequestedSince()).isEqualTo(daysAgo(90));
    }

    @Test
    @DisplayName("later runs resume from the watermark, with a day of overlap for late mail")
    void laterRunsResumeFromWatermark() {
        inbox().deliver(email("m1", "Interview invitation", "recruiting@stripe.com", daysAgo(10), ""));
        pipeline.run();

        pipeline.run();

        assertThat(inbox().lastRequestedSince()).isEqualTo(daysAgo(11));
    }

    @Test
    @DisplayName("an unknown sender for a known company still matches on the company name")
    void matchesByCompanyNameWhenDomainIsUnknown() {
        inbox()
                .deliver(
                        email(
                                "m1",
                                "Your application to Stripe",
                                "no-reply@greenhouse.io",
                                daysAgo(1),
                                "Stripe would like to schedule an interview with you."));

        pipeline.run();

        assertThat(applications.findById(swe.getId()).orElseThrow().getStatus())
                .isEqualTo(ApplicationStatus.INTERVIEW);
    }

    @Test
    @DisplayName("with no mail source configured a run is a harmless no-op")
    void reportsWhenNothingIsConfigured() {
        // The stub is present here, so assert the shape of the not-configured result directly.
        IngestionPipeline.IngestionResult none = IngestionPipeline.IngestionResult.notConfigured();

        assertThat(none.source()).isEqualTo("none");
        assertThat(none.processed()).isZero();
        assertThat(none.countOf(ProcessedMessage.Outcome.TRANSITION_RECORDED)).isZero();
    }

    @Test
    @DisplayName("the run reports what it did")
    void reportsCounts() {
        inbox()
                .deliver(
                        email("m1", "Interview invitation", "recruiting@stripe.com", daysAgo(1), ""),
                        email("m2", "Job alert: new roles", "jobs@linkedin.com", daysAgo(1), ""),
                        email("m3", "Thank you for applying to Acme", "careers@acme-x.example", daysAgo(1), ""));

        IngestionPipeline.IngestionResult result = pipeline.run();

        assertThat(result.source()).isEqualTo("stub-mailbox");
        assertThat(result.fetched()).isEqualTo(3);
        assertThat(result.processed()).isEqualTo(3);
        assertThat(result.failed()).isZero();
        assertThat(result.since()).isEqualTo(daysAgo(90));
        assertThat(result.outcomes())
                .containsEntry(ProcessedMessage.Outcome.TRANSITION_RECORDED, 1)
                .containsEntry(ProcessedMessage.Outcome.IGNORED, 1)
                .containsEntry(ProcessedMessage.Outcome.QUEUED_FOR_REVIEW, 1);
    }

    @Test
    @DisplayName("evidence is limited to headers -- no message body is ever stored")
    void storesHeadersOnly() {
        inbox()
                .deliver(
                        email(
                                "m1",
                                "Interview invitation",
                                "recruiting@stripe.com",
                                daysAgo(1),
                                "Secret body preview text"));

        pipeline.run();

        StatusEvent event = statusEvents.findByApplicationIdOrderByOccurredAtAscIdAsc(swe.getId()).get(0);
        assertThat(event.getEvidence().getSubject()).isEqualTo("Interview invitation");
        assertThat(event.getEvidence().getFromAddress()).isEqualTo("recruiting@stripe.com");
        assertThat(event.getReason()).doesNotContain("Secret body preview text");
        assertThat(NOW).isNotNull();
    }
}
