package com.ahmedb.internship.service;

import static com.ahmedb.internship.TestFixtures.daysAgo;
import static org.assertj.core.api.Assertions.assertThat;

import com.ahmedb.internship.classify.Classification;
import com.ahmedb.internship.domain.Application;
import com.ahmedb.internship.domain.ApplicationStatus;
import com.ahmedb.internship.domain.Company;
import com.ahmedb.internship.ingest.IngestedEmail;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class ApplicationMatcherTest extends ServiceTestBase {

    @Autowired private ApplicationMatcher matcher;

    private Company stripe;

    @BeforeEach
    void seed() {
        Company company = new Company("Stripe");
        company.addEmailDomain("stripe.com");
        stripe = companies.save(company);
    }

    private Application saveApplication(String role, ApplicationStatus status) {
        Application application = new Application(stripe, role, "Summer 2027");
        application.setStatus(status);
        application.setLastEventAt(daysAgo(1));
        return applications.save(application);
    }

    private IngestedEmail emailFrom(String address) {
        return new IngestedEmail("m1", "t1", "Interview invitation", address, null, Instant.now(), "");
    }

    private Classification transitionWithHints(String companyHint, String roleHint) {
        return Classification.transition(
                ApplicationStatus.INTERVIEW, companyHint, roleHint, 0.9, "matched", "rules:v1");
    }

    @Test
    @DisplayName("a known sender domain with one open application matches it")
    void matchesSingleOpenApplication() {
        Application swe = saveApplication("Software Engineer Intern", ApplicationStatus.APPLIED);

        Optional<Application> match =
                matcher.match(emailFrom("recruiting@stripe.com"), transitionWithHints("Stripe", null));

        assertThat(match).map(Application::getId).contains(swe.getId());
    }

    @Test
    @DisplayName("the sender domain outranks the classifier's company hint")
    void domainBeatsHint() {
        Application swe = saveApplication("Software Engineer Intern", ApplicationStatus.APPLIED);
        companies.save(new Company("Datadog"));

        Optional<Application> match =
                matcher.match(emailFrom("recruiting@stripe.com"), transitionWithHints("Datadog", null));

        assertThat(match).map(Application::getId).contains(swe.getId());
    }

    @Test
    @DisplayName("an unknown domain falls back to matching the company by name")
    void fallsBackToCompanyName() {
        Application swe = saveApplication("Software Engineer Intern", ApplicationStatus.APPLIED);

        Optional<Application> match =
                matcher.match(emailFrom("no-reply@greenhouse.io"), transitionWithHints("stripe", null));

        assertThat(match).map(Application::getId).contains(swe.getId());
    }

    @Test
    @DisplayName("two open applications at one company are separated by the role hint")
    void roleHintDisambiguates() {
        Application swe = saveApplication("Software Engineer Intern", ApplicationStatus.APPLIED);
        saveApplication("Data Science Intern", ApplicationStatus.APPLIED);

        Optional<Application> match =
                matcher.match(
                        emailFrom("recruiting@stripe.com"),
                        transitionWithHints("Stripe", "Software Engineer Intern"));

        assertThat(match).map(Application::getId).contains(swe.getId());
    }

    @Test
    @DisplayName("an ambiguous company declines to match rather than guessing")
    void ambiguityDeclines() {
        saveApplication("Software Engineer Intern", ApplicationStatus.APPLIED);
        saveApplication("Data Science Intern", ApplicationStatus.APPLIED);

        // No role hint at all, and a hint that fits neither, both decline.
        assertThat(matcher.match(emailFrom("recruiting@stripe.com"), transitionWithHints("Stripe", null)))
                .isEmpty();
        assertThat(
                        matcher.match(
                                emailFrom("recruiting@stripe.com"),
                                transitionWithHints("Stripe", "Product Manager Intern")))
                .isEmpty();
    }

    @Test
    @DisplayName("a role hint matching both applications is still ambiguous")
    void overlappingRoleHintDeclines() {
        saveApplication("Software Engineer Intern", ApplicationStatus.APPLIED);
        saveApplication("Software Engineer Intern (Infrastructure)", ApplicationStatus.APPLIED);

        assertThat(
                        matcher.match(
                                emailFrom("recruiting@stripe.com"),
                                transitionWithHints("Stripe", "Software Engineer Intern")))
                .isEmpty();
    }

    @Test
    @DisplayName("a settled application is not reopened by new mail")
    void terminalApplicationsAreNotMatched() {
        saveApplication("Software Engineer Intern", ApplicationStatus.REJECTED);

        assertThat(matcher.match(emailFrom("recruiting@stripe.com"), transitionWithHints("Stripe", null)))
                .isEmpty();
    }

    @Test
    @DisplayName("an unknown company matches nothing")
    void unknownCompanyDeclines() {
        saveApplication("Software Engineer Intern", ApplicationStatus.APPLIED);

        assertThat(
                        matcher.match(
                                emailFrom("careers@unknown.example"), transitionWithHints("Unknown Corp", null)))
                .isEmpty();
        assertThat(matcher.match(emailFrom("careers@unknown.example"), transitionWithHints(null, null)))
                .isEmpty();
    }

    @Test
    @DisplayName("a company with no applications matches nothing")
    void companyWithoutApplicationsDeclines() {
        assertThat(matcher.match(emailFrom("recruiting@stripe.com"), transitionWithHints("Stripe", null)))
                .isEmpty();
    }
}
