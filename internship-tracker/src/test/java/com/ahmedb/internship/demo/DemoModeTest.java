package com.ahmedb.internship.demo;

import static org.assertj.core.api.Assertions.assertThat;

import com.ahmedb.internship.domain.Application;
import com.ahmedb.internship.domain.ApplicationStatus;
import com.ahmedb.internship.domain.StatusEvent;
import com.ahmedb.internship.domain.UnmatchedEmail;
import com.ahmedb.internship.repository.ApplicationRepository;
import com.ahmedb.internship.repository.CompanyRepository;
import com.ahmedb.internship.repository.StatusEventRepository;
import com.ahmedb.internship.repository.UnmatchedEmailRepository;
import com.ahmedb.internship.service.DigestService;
import com.ahmedb.internship.service.GhostPolicy;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Demo mode is the thing someone is shown first, so its story is worth pinning down.
 *
 * <p>Booting under the {@code demo} profile seeds the companies and replays the fixture mailbox
 * through the real pipeline. These assertions describe the result a reader should see, so a change
 * that quietly breaks the demo fails here rather than in front of an audience.
 */
@SpringBootTest
@ActiveProfiles("demo")
class DemoModeTest {

    @Autowired private ApplicationRepository applications;
    @Autowired private CompanyRepository companies;
    @Autowired private StatusEventRepository statusEvents;
    @Autowired private UnmatchedEmailRepository unmatchedEmails;
    @Autowired private DigestService digestService;
    @Autowired private GhostPolicy ghostPolicy;

    private Application byCompany(String companyName) {
        return applications.findAllByNextDeadline().stream()
                .filter(a -> a.getCompany().getName().equals(companyName))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no demo application for " + companyName));
    }

    @Test
    @DisplayName("the demo seeds four companies and their applications")
    void seedsThePipeline() {
        assertThat(companies.findAll())
                .extracting(c -> c.getName())
                .containsExactlyInAnyOrder("Stripe", "Datadog", "Jane Street", "Ramp");
        assertThat(applications.count()).isEqualTo(4);
    }

    @Test
    @DisplayName("statuses are derived by replaying the mailbox, not seeded")
    void statusesComeFromTheMailbox() {
        // Every application is seeded at NOT_APPLIED; these values were produced by the classifier.
        assertThat(byCompany("Stripe").getStatus()).isEqualTo(ApplicationStatus.FINAL_ROUND);
        assertThat(byCompany("Jane Street").getStatus()).isEqualTo(ApplicationStatus.FINAL_ROUND);
        assertThat(byCompany("Datadog").getStatus()).isEqualTo(ApplicationStatus.REJECTED);
        assertThat(byCompany("Ramp").getStatus()).isEqualTo(ApplicationStatus.APPLIED);
    }

    @Test
    @DisplayName("Stripe walks the full progression in order")
    void stripeShowsTheWholePipeline() {
        List<StatusEvent> timeline =
                statusEvents.findByApplicationIdOrderByOccurredAtAscIdAsc(byCompany("Stripe").getId());

        assertThat(timeline)
                .extracting(StatusEvent::getNewStatus)
                .containsExactly(
                        ApplicationStatus.APPLIED,
                        ApplicationStatus.OA_PENDING,
                        ApplicationStatus.OA_SUBMITTED,
                        ApplicationStatus.INTERVIEW,
                        ApplicationStatus.FINAL_ROUND,
                        // A duplicate acknowledgement that arrived late.
                        ApplicationStatus.APPLIED);
    }

    @Test
    @DisplayName("the late duplicate is recorded but does not drag Stripe backwards")
    void lateDuplicateDoesNotRegress() {
        List<StatusEvent> timeline =
                statusEvents.findByApplicationIdOrderByOccurredAtAscIdAsc(byCompany("Stripe").getId());
        StatusEvent last = timeline.get(timeline.size() - 1);

        assertThat(last.getNewStatus()).isEqualTo(ApplicationStatus.APPLIED);
        assertThat(last.isAdvancedStatus()).isFalse();
        assertThat(byCompany("Stripe").getStatus()).isEqualTo(ApplicationStatus.FINAL_ROUND);
    }

    @Test
    @DisplayName("Ramp went quiet and reads as ghosted without that being stored")
    void rampIsGhosted() {
        Application ramp = byCompany("Ramp");

        assertThat(ghostPolicy.isGhosted(ramp)).isTrue();
        assertThat(ghostPolicy.effectiveStatus(ramp)).isEqualTo(ApplicationStatus.GHOSTED);
        assertThat(ramp.getStatus()).isEqualTo(ApplicationStatus.APPLIED);
    }

    @Test
    @DisplayName("an email for a company never applied to goes to the review queue")
    void figmaGoesToTheReviewQueue() {
        List<UnmatchedEmail> queue =
                unmatchedEmails.findByResolutionOrderByCreatedAtDesc(UnmatchedEmail.Resolution.PENDING);

        assertThat(queue).hasSize(1);
        assertThat(queue.get(0).getCompanyHint()).isEqualTo("Figma");
        assertThat(queue.get(0).getProposedStatus()).isEqualTo(ApplicationStatus.INTERVIEW);
        // And nothing was invented on its behalf.
        assertThat(companies.findByNameIgnoreCase("Figma")).isEmpty();
    }

    @Test
    @DisplayName("noise and ordinary mail leave no events behind")
    void noiseProducesNoEvents() {
        // 11 transitions across four applications; the job alert and the lunch email produced none.
        assertThat(statusEvents.count()).isEqualTo(11);
    }

    @Test
    @DisplayName("the digest shows all three buckets, with Ramp in two of them")
    void digestTellsTheStory() {
        DigestService.Digest digest = digestService.build();

        assertThat(digest.overdue()).extracting(a -> a.getCompany().getName()).containsExactly("Ramp");
        assertThat(digest.closingSoon())
                .extracting(a -> a.getCompany().getName())
                .containsExactly("Stripe", "Jane Street");
        assertThat(digest.ghosted()).extracting(a -> a.getCompany().getName()).containsExactly("Ramp");
    }
}
