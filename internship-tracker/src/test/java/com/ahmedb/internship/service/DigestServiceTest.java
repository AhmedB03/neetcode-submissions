package com.ahmedb.internship.service;

import static com.ahmedb.internship.TestFixtures.NOW;
import static com.ahmedb.internship.TestFixtures.daysAgo;
import static com.ahmedb.internship.TestFixtures.daysFromNow;
import static org.assertj.core.api.Assertions.assertThat;

import com.ahmedb.internship.domain.Application;
import com.ahmedb.internship.domain.ApplicationStatus;
import com.ahmedb.internship.domain.Company;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class DigestServiceTest extends ServiceTestBase {

    @Autowired private DigestService digestService;

    private Company stripe;

    @BeforeEach
    void seed() {
        stripe = companies.save(new Company("Stripe"));
    }

    private Application save(
            String role, ApplicationStatus status, Instant deadline, Instant lastEvent) {
        Application application = new Application(stripe, role, "Summer 2027");
        application.setStatus(status);
        application.setNextDeadline(deadline);
        application.setLastEventAt(lastEvent);
        return applications.save(application);
    }

    @Test
    @DisplayName("the digest lists what closes inside the horizon, soonest first")
    void listsClosingSoon() {
        save("Due in 6 days", ApplicationStatus.OA_PENDING, daysFromNow(6), daysAgo(1));
        save("Due in 2 days", ApplicationStatus.OA_PENDING, daysFromNow(2), daysAgo(1));
        save("Due in 20 days", ApplicationStatus.APPLIED, daysFromNow(20), daysAgo(1));
        save("No deadline", ApplicationStatus.APPLIED, null, daysAgo(1));

        DigestService.Digest digest = digestService.build();

        assertThat(digest.closingSoon())
                .extracting(Application::getRoleTitle)
                .containsExactly("Due in 2 days", "Due in 6 days");
        assertThat(digest.generatedAt()).isEqualTo(NOW);
        assertThat(digest.horizon()).isEqualTo(daysFromNow(7));
    }

    @Test
    @DisplayName("the window is forward-looking, so an overdue deadline is not in it")
    void pastDeadlinesAreExcludedFromTheForwardWindow() {
        save("Overdue", ApplicationStatus.OA_PENDING, daysAgo(2), daysAgo(1));
        save("Upcoming", ApplicationStatus.OA_PENDING, daysFromNow(3), daysAgo(1));

        DigestService.Digest digest = digestService.build();

        assertThat(digest.closingSoon()).extracting(Application::getRoleTitle).containsExactly("Upcoming");
    }

    @Test
    @DisplayName("settled applications have no deadline left to hit")
    void terminalApplicationsAreExcluded() {
        save("Offered", ApplicationStatus.OFFER, daysFromNow(3), daysAgo(1));
        save("Rejected", ApplicationStatus.REJECTED, daysFromNow(3), daysAgo(1));
        save("Live", ApplicationStatus.INTERVIEW, daysFromNow(3), daysAgo(1));

        assertThat(digestService.build().closingSoon())
                .extracting(Application::getRoleTitle)
                .containsExactly("Live");
    }

    @Test
    @DisplayName("the digest reports what has gone quiet")
    void listsGhosted() {
        save("Quiet 45 days", ApplicationStatus.APPLIED, null, daysAgo(45));
        save("Quiet 31 days", ApplicationStatus.INTERVIEW, null, daysAgo(31));
        save("Active", ApplicationStatus.APPLIED, null, daysAgo(3));

        DigestService.Digest digest = digestService.build();

        assertThat(digest.ghosted())
                .extracting(Application::getRoleTitle)
                .containsExactlyInAnyOrder("Quiet 45 days", "Quiet 31 days");
        assertThat(digest.ghostThresholdDays()).isEqualTo(30);
    }

    @Test
    @DisplayName("settled and never-started applications are never reported as ghosted")
    void exemptStatusesAreNotGhosted() {
        save("Rejected long ago", ApplicationStatus.REJECTED, null, daysAgo(200));
        save("Offer accepted", ApplicationStatus.OFFER, null, daysAgo(200));
        save("Never applied", ApplicationStatus.NOT_APPLIED, null, daysAgo(200));

        assertThat(digestService.build().ghosted()).isEmpty();
    }

    @Test
    @DisplayName("an application can be both closing soon and ghosted")
    void anApplicationCanAppearInBothLists() {
        save("Quiet with a deadline", ApplicationStatus.OA_PENDING, daysFromNow(3), daysAgo(60));

        DigestService.Digest digest = digestService.build();

        assertThat(digest.closingSoon()).hasSize(1);
        assertThat(digest.ghosted()).hasSize(1);
    }

    @Test
    @DisplayName("an empty pipeline produces an empty digest rather than failing")
    void emptyPipeline() {
        DigestService.Digest digest = digestService.build();

        assertThat(digest.closingSoon()).isEmpty();
        assertThat(digest.ghosted()).isEmpty();
    }
}
