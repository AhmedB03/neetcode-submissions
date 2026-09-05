package com.ahmedb.internship.repository;

import static com.ahmedb.internship.TestFixtures.application;
import static com.ahmedb.internship.TestFixtures.company;
import static com.ahmedb.internship.TestFixtures.daysAgo;
import static com.ahmedb.internship.TestFixtures.daysFromNow;
import static com.ahmedb.internship.TestFixtures.emailEvent;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ahmedb.internship.TestFixtures;
import com.ahmedb.internship.domain.Application;
import com.ahmedb.internship.domain.ApplicationStatus;
import com.ahmedb.internship.domain.Company;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class ApplicationRepositoryTest extends RepositoryTestBase {

    private static final List<ApplicationStatus> TERMINAL =
            List.of(ApplicationStatus.OFFER, ApplicationStatus.REJECTED);

    @Autowired private ApplicationRepository applications;
    @Autowired private CompanyRepository companies;

    private Company stripe;
    private Company datadog;

    @BeforeEach
    void setUp() {
        stripe = companies.save(company("Stripe", "stripe.com"));
        datadog = companies.save(company("Datadog", "datadoghq.com"));
    }

    @Test
    @DisplayName("orders by soonest deadline, with undated applications last")
    void findAllByNextDeadline_ordersByUrgency() {
        applications.save(application(stripe, "SWE Intern", ApplicationStatus.APPLIED, daysFromNow(10)));
        applications.save(application(datadog, "Backend Intern", ApplicationStatus.OA_PENDING, daysFromNow(2)));
        Application undated = applications.save(application(stripe, "Infra Intern", ApplicationStatus.APPLIED));

        List<Application> result = applications.findAllByNextDeadline();

        assertThat(result)
                .extracting(Application::getRoleTitle)
                .containsExactly("Backend Intern", "SWE Intern", "Infra Intern");
        assertThat(result.get(2).getId()).isEqualTo(undated.getId());
        assertThat(result.get(2).getNextDeadline()).isNull();
    }

    @Test
    @DisplayName("loads the full timeline for the detail view")
    void findByIdWithEvents_loadsTimelineOldestFirst() {
        Application application = application(stripe, "SWE Intern", ApplicationStatus.INTERVIEW);
        application.recordEvent(
                emailEvent(ApplicationStatus.NOT_APPLIED, ApplicationStatus.APPLIED, daysAgo(20), "m1"));
        application.recordEvent(
                emailEvent(ApplicationStatus.APPLIED, ApplicationStatus.OA_PENDING, daysAgo(10), "m2"));
        application.recordEvent(
                emailEvent(ApplicationStatus.OA_PENDING, ApplicationStatus.INTERVIEW, daysAgo(3), "m3"));
        Long id = applications.save(application).getId();

        Application loaded = applications.findByIdWithEvents(id).orElseThrow();

        assertThat(loaded.getEvents())
                .extracting(e -> e.getEvidence().getMessageId())
                .containsExactly("m1", "m2", "m3");
        assertThat(loaded.getLastEventAt()).isEqualTo(daysAgo(3));
    }

    @Test
    @DisplayName("matching candidates exclude settled applications")
    void findOpenByCompanyId_excludesTerminal() {
        applications.save(application(stripe, "SWE Intern", ApplicationStatus.INTERVIEW));
        applications.save(application(stripe, "Data Intern", ApplicationStatus.REJECTED));
        applications.save(application(stripe, "ML Intern", ApplicationStatus.OFFER));
        applications.save(application(datadog, "Backend Intern", ApplicationStatus.APPLIED));

        List<Application> open = applications.findOpenByCompanyId(stripe.getId(), TERMINAL);

        assertThat(open).extracting(Application::getRoleTitle).containsExactly("SWE Intern");
    }

    @Test
    @DisplayName("deadline window includes its lower bound and excludes its upper")
    void findWithDeadlineBetween_isHalfOpen() {
        Application onLowerBound =
                applications.save(application(stripe, "Lower", ApplicationStatus.APPLIED, TestFixtures.NOW));
        applications.save(application(stripe, "Inside", ApplicationStatus.APPLIED, daysFromNow(3)));
        applications.save(application(stripe, "OnUpperBound", ApplicationStatus.APPLIED, daysFromNow(7)));
        applications.save(application(stripe, "Beyond", ApplicationStatus.APPLIED, daysFromNow(9)));

        List<Application> due =
                applications.findWithDeadlineBetween(TestFixtures.NOW, daysFromNow(7), TERMINAL);

        assertThat(due).extracting(Application::getRoleTitle).containsExactly("Lower", "Inside");
        assertThat(due.get(0).getId()).isEqualTo(onLowerBound.getId());
    }

    @Test
    @DisplayName("a settled application has no deadline left to hit")
    void findWithDeadlineBetween_excludesTerminal() {
        applications.save(application(stripe, "Open", ApplicationStatus.OA_PENDING, daysFromNow(2)));
        applications.save(application(stripe, "Rejected", ApplicationStatus.REJECTED, daysFromNow(2)));
        applications.save(application(stripe, "Offered", ApplicationStatus.OFFER, daysFromNow(2)));

        List<Application> due =
                applications.findWithDeadlineBetween(TestFixtures.NOW, daysFromNow(7), TERMINAL);

        assertThat(due).extracting(Application::getRoleTitle).containsExactly("Open");
    }

    @Test
    @DisplayName("stale candidates are quiet applications in a status that can go stale")
    void findStaleCandidates_filtersByCutoffAndStatus() {
        List<ApplicationStatus> ghostable =
                List.of(
                        ApplicationStatus.APPLIED,
                        ApplicationStatus.OA_PENDING,
                        ApplicationStatus.OA_SUBMITTED,
                        ApplicationStatus.INTERVIEW,
                        ApplicationStatus.FINAL_ROUND);

        Application quiet = application(stripe, "Quiet", ApplicationStatus.APPLIED);
        quiet.setLastEventAt(daysAgo(45));
        applications.save(quiet);

        Application recent = application(stripe, "Recent", ApplicationStatus.INTERVIEW);
        recent.setLastEventAt(daysAgo(5));
        applications.save(recent);

        Application quietButRejected = application(stripe, "QuietRejected", ApplicationStatus.REJECTED);
        quietButRejected.setLastEventAt(daysAgo(90));
        applications.save(quietButRejected);

        Application quietNotApplied = application(datadog, "NotApplied", ApplicationStatus.NOT_APPLIED);
        quietNotApplied.setLastEventAt(daysAgo(90));
        applications.save(quietNotApplied);

        List<Application> stale = applications.findStaleCandidates(daysAgo(30), ghostable);

        assertThat(stale).extracting(Application::getRoleTitle).containsExactly("Quiet");
    }

    @Test
    @DisplayName("an application with no events falls back to its creation time")
    void findStaleCandidates_usesCreatedAtWhenNoEvents() {
        Application neverHeardBack = applications.save(application(stripe, "Silent", ApplicationStatus.APPLIED));

        assertThat(neverHeardBack.getLastEventAt()).isNull();
        assertThat(neverHeardBack.lastActivityAt()).isEqualTo(neverHeardBack.getCreatedAt());

        // Created just now, so a 30-day cutoff must not catch it...
        assertThat(applications.findStaleCandidates(daysAgo(30), List.of(ApplicationStatus.APPLIED)))
                .isEmpty();
        // ...but a cutoff in the future does, proving createdAt is what the query reads.
        assertThat(
                        applications.findStaleCandidates(
                                java.time.Instant.now().plusSeconds(60), List.of(ApplicationStatus.APPLIED)))
                .extracting(Application::getRoleTitle)
                .containsExactly("Silent");
    }

    @Test
    @DisplayName("the same role in the same cycle cannot be recorded twice")
    void uniqueConstraint_onCompanyRoleCycle() {
        applications.save(application(stripe, "SWE Intern", ApplicationStatus.APPLIED));

        assertThatThrownBy(
                        () -> applications.saveAndFlush(application(stripe, "SWE Intern", ApplicationStatus.APPLIED)))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("case-insensitive lookup finds an existing application for matching")
    void findByCompanyIdAndRoleTitleAndCycle_ignoresCase() {
        applications.save(application(stripe, "SWE Intern", ApplicationStatus.APPLIED));

        assertThat(
                        applications.findByCompanyIdAndRoleTitleIgnoreCaseAndCycleIgnoreCase(
                                stripe.getId(), "swe intern", "summer 2027"))
                .isPresent();
    }
}
