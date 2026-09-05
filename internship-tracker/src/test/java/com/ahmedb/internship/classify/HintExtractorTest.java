package com.ahmedb.internship.classify;

import static org.assertj.core.api.Assertions.assertThat;

import com.ahmedb.internship.classify.rules.HintExtractor;
import com.ahmedb.internship.ingest.IngestedEmail;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Hint extraction on its own, independent of whether a rule happened to fire. */
class HintExtractorTest {

    private static final Instant RECEIVED = Instant.parse("2026-09-05T09:00:00Z");

    private final ClassificationContext context =
            new ClassificationContext(
                    List.of(
                            new ClassificationContext.KnownCompany(1L, "Stripe", Set.of("stripe.com")),
                            new ClassificationContext.KnownCompany(2L, "Jane", Set.of("jane.example")),
                            new ClassificationContext.KnownCompany(
                                    3L, "Jane Street", Set.of("janestreet.com"))));

    private IngestedEmail email(String subject, String from, String displayName, String snippet) {
        return new IngestedEmail("m", "t", subject, from, displayName, RECEIVED, snippet);
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
        "'Your application for Software Engineer Intern at Stripe', 'Software Engineer Intern'",
        "'Application to Backend Engineering Intern', 'Backend Engineering Intern'",
        "'Your application for Data Scientist Intern - Summer 2027', 'Data Scientist Intern'",
        "'Application for the Quantitative Trading Intern role', 'Quantitative Trading Intern role'",
        "'Regarding the position of Machine Learning Engineer at Ramp', 'Machine Learning Engineer'",
        "'Software Engineering Intern', 'Software Engineering Intern'",
    })
    void extractsRoleTitles(String subject, String expected) {
        assertThat(HintExtractor.roleHint(email(subject, "a@stripe.com", null, ""))).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Thank you for applying", "Update on your candidacy", "Hello", ""})
    @DisplayName("no role is invented when the subject does not carry one")
    void returnsNullWhenNoRolePresent(String subject) {
        assertThat(HintExtractor.roleHint(email(subject, "a@stripe.com", null, ""))).isNull();
    }

    @Test
    @DisplayName("a tracked domain wins over everything else")
    void trackedDomainWins() {
        assertThat(
                        HintExtractor.companyHint(
                                email("Some other company mentioned: Jane Street", "x@stripe.com", "Acme", ""),
                                context))
                .isEqualTo("Stripe");
    }

    @Test
    @DisplayName("the longest tracked name wins, so a prefix company does not shadow it")
    void longestNameWins() {
        assertThat(
                        HintExtractor.companyHint(
                                email("Your application to Jane Street", "no-reply@greenhouse.io", null, ""),
                                context))
                .isEqualTo("Jane Street");
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
        "'Stripe Recruiting', 'Stripe'",
        "'Ramp Talent Acquisition', 'Ramp'",
        "'Datadog Careers Team', 'Datadog'",
        "'Notion University Recruiting', 'Notion'",
        "'Figma - Early Careers', 'Figma'",
    })
    void stripsBoilerplateFromDisplayNames(String displayName, String expected) {
        assertThat(
                        HintExtractor.companyHint(
                                email("Thank you for applying", "no-reply@greenhouse.io", displayName, ""),
                                ClassificationContext.empty()))
                .isEqualTo(expected);
    }

    @Test
    @DisplayName("a display name that is only boilerplate yields nothing")
    void boilerplateOnlyDisplayNameIsDiscarded() {
        assertThat(
                        HintExtractor.companyHint(
                                email("Thank you for applying", "no-reply@greenhouse.io", "Recruiting Team", ""),
                                ClassificationContext.empty()))
                .isEmpty();
    }

    @Test
    @DisplayName("a company domain becomes a company name, but a vendor domain does not")
    void companyFromDomain() {
        assertThat(
                        HintExtractor.companyHint(
                                email("Thank you for applying", "careers@ramp.com", null, ""),
                                ClassificationContext.empty()))
                .isEqualTo("Ramp");

        // ATS, job board and consumer domains name no employer.
        for (String from :
                List.of("no-reply@greenhouse.io", "jobs@linkedin.com", "someone@gmail.com")) {
            assertThat(
                            HintExtractor.companyHint(
                                    email("Thank you for applying", from, null, ""), ClassificationContext.empty()))
                    .as("from %s", from)
                    .isEmpty();
        }
    }

    @Test
    @DisplayName("a malformed sender does not throw")
    void malformedSenderYieldsEmptyHint() {
        assertThat(
                        HintExtractor.companyHint(
                                email("Thank you for applying", "garbage", null, ""), ClassificationContext.empty()))
                .isEmpty();
        assertThat(
                        HintExtractor.companyHint(
                                email("Thank you for applying", null, null, ""), ClassificationContext.empty()))
                .isEmpty();
    }
}
