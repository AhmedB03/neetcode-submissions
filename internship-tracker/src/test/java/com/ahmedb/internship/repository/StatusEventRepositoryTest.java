package com.ahmedb.internship.repository;

import static com.ahmedb.internship.TestFixtures.application;
import static com.ahmedb.internship.TestFixtures.company;
import static com.ahmedb.internship.TestFixtures.daysAgo;
import static com.ahmedb.internship.TestFixtures.emailEvent;
import static org.assertj.core.api.Assertions.assertThat;

import com.ahmedb.internship.domain.Application;
import com.ahmedb.internship.domain.ApplicationStatus;
import com.ahmedb.internship.domain.Company;
import com.ahmedb.internship.domain.EventSource;
import com.ahmedb.internship.domain.StatusEvent;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class StatusEventRepositoryTest extends RepositoryTestBase {

    @Autowired private StatusEventRepository events;
    @Autowired private ApplicationRepository applications;
    @Autowired private CompanyRepository companies;

    private Application savedApplicationWithTimeline() {
        Company stripe = companies.save(company("Stripe", "stripe.com"));
        Application application = application(stripe, "SWE Intern", ApplicationStatus.INTERVIEW);
        // Recorded out of order on purpose: the timeline must sort by when the mail arrived,
        // not by insertion order.
        application.recordEvent(
                emailEvent(ApplicationStatus.APPLIED, ApplicationStatus.OA_PENDING, daysAgo(10), "m2"));
        application.recordEvent(
                emailEvent(ApplicationStatus.NOT_APPLIED, ApplicationStatus.APPLIED, daysAgo(20), "m1"));
        application.recordEvent(
                emailEvent(ApplicationStatus.OA_PENDING, ApplicationStatus.INTERVIEW, daysAgo(3), "m3"));
        return applications.save(application);
    }

    @Test
    @DisplayName("timeline reads oldest first regardless of insertion order")
    void findByApplicationId_ordersByOccurredAt() {
        Application application = savedApplicationWithTimeline();

        List<StatusEvent> timeline =
                events.findByApplicationIdOrderByOccurredAtAscIdAsc(application.getId());

        assertThat(timeline)
                .extracting(e -> e.getEvidence().getMessageId())
                .containsExactly("m1", "m2", "m3");
    }

    @Test
    @DisplayName("the newest event is the one the head status came from")
    void findFirstByApplicationId_returnsNewest() {
        Application application = savedApplicationWithTimeline();

        StatusEvent newest =
                events.findFirstByApplicationIdOrderByOccurredAtDescIdDesc(application.getId()).orElseThrow();

        assertThat(newest.getNewStatus()).isEqualTo(ApplicationStatus.INTERVIEW);
        assertThat(newest.getOccurredAt()).isEqualTo(daysAgo(3));
    }

    @Test
    @DisplayName("evidence is kept so an automated transition can be traced to a message")
    void evidenceIsPersisted() {
        Application application = savedApplicationWithTimeline();

        StatusEvent event = events.findByApplicationIdOrderByOccurredAtAscIdAsc(application.getId()).get(0);

        assertThat(event.getSource()).isEqualTo(EventSource.GMAIL);
        assertThat(event.getClassifierId()).isEqualTo("rules:v1");
        assertThat(event.getConfidence()).isEqualTo(0.9);
        assertThat(event.getEvidence().getSubject()).isEqualTo("Subject m1");
        assertThat(event.getEvidence().getFromAddress()).isEqualTo("noreply@example.com");
        assertThat(events.existsByEvidenceMessageId("m1")).isTrue();
        assertThat(events.existsByEvidenceMessageId("nope")).isFalse();
    }

    @Test
    @DisplayName("a manual override records no email evidence")
    void manualEventHasNoEvidence() {
        Company stripe = companies.save(company("Stripe", "stripe.com"));
        Application application = application(stripe, "SWE Intern", ApplicationStatus.APPLIED);
        application.recordEvent(
                StatusEvent.manual(
                        ApplicationStatus.APPLIED, ApplicationStatus.INTERVIEW, daysAgo(1), "recruiter called"));
        applications.save(application);

        StatusEvent event = events.findByApplicationIdOrderByOccurredAtAscIdAsc(application.getId()).get(0);

        assertThat(event.getSource()).isEqualTo(EventSource.MANUAL);
        assertThat(event.getClassifierId()).isNull();
        assertThat(event.getEvidence() == null || event.getEvidence().isEmpty()).isTrue();
    }
}
