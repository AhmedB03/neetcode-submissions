package com.ahmedb.internship.service;

import static com.ahmedb.internship.TestFixtures.NOW;
import static com.ahmedb.internship.TestFixtures.application;
import static com.ahmedb.internship.TestFixtures.company;
import static com.ahmedb.internship.TestFixtures.daysAgo;
import static org.assertj.core.api.Assertions.assertThat;

import com.ahmedb.internship.config.TrackerProperties;
import com.ahmedb.internship.domain.Application;
import com.ahmedb.internship.domain.ApplicationStatus;
import java.time.Clock;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class GhostPolicyTest {

    private final GhostPolicy policy =
            new GhostPolicy(
                    new TrackerProperties(new TrackerProperties.Ghost(30), new TrackerProperties.Digest(7)),
                    Clock.fixed(NOW, ZoneOffset.UTC));

    private Application quietFor(ApplicationStatus status, int days) {
        Application application = application(company("Stripe"), "SWE Intern", status);
        application.setLastEventAt(daysAgo(days));
        return application;
    }

    @Test
    @DisplayName("silence past the threshold reads as ghosted")
    void ghostedAfterThreshold() {
        assertThat(policy.isGhosted(quietFor(ApplicationStatus.APPLIED, 31))).isTrue();
        assertThat(policy.effectiveStatus(quietFor(ApplicationStatus.APPLIED, 31)))
                .isEqualTo(ApplicationStatus.GHOSTED);
    }

    @Test
    @DisplayName("the threshold boundary is not yet ghosted")
    void notGhostedAtExactlyThreshold() {
        assertThat(policy.isGhosted(quietFor(ApplicationStatus.APPLIED, 30))).isFalse();
        assertThat(policy.isGhosted(quietFor(ApplicationStatus.APPLIED, 29))).isFalse();
    }

    @ParameterizedTest
    @EnumSource(
            value = ApplicationStatus.class,
            names = {"APPLIED", "OA_PENDING", "OA_SUBMITTED", "INTERVIEW", "FINAL_ROUND"})
    @DisplayName("every live status can go quiet")
    void liveStatusesCanGhost(ApplicationStatus status) {
        assertThat(policy.isGhosted(quietFor(status, 60))).isTrue();
    }

    @ParameterizedTest
    @EnumSource(
            value = ApplicationStatus.class,
            names = {"OFFER", "REJECTED", "NOT_APPLIED"})
    @DisplayName("settled and never-started applications never ghost, however long the silence")
    void exemptStatusesNeverGhost(ApplicationStatus status) {
        Application application = quietFor(status, 365);

        assertThat(policy.isGhosted(application)).isFalse();
        assertThat(policy.effectiveStatus(application)).isEqualTo(status);
    }

    @Test
    @DisplayName("GHOSTED is derived on every read, so a late reply un-ghosts an application")
    void unGhostingIsAutomatic() {
        Application application = quietFor(ApplicationStatus.APPLIED, 60);
        assertThat(policy.isGhosted(application)).isTrue();

        // A recruiter finally replies. No job, no backfill -- the next read simply sees the event.
        application.setLastEventAt(daysAgo(1));

        assertThat(policy.isGhosted(application)).isFalse();
        assertThat(policy.effectiveStatus(application)).isEqualTo(ApplicationStatus.APPLIED);
    }

    @Test
    @DisplayName("ghosting is never written back to the application")
    void ghostingDoesNotMutate() {
        Application application = quietFor(ApplicationStatus.INTERVIEW, 90);

        policy.effectiveStatus(application);

        assertThat(application.getStatus()).isEqualTo(ApplicationStatus.INTERVIEW);
    }

    @Test
    void reportsDaysOfSilenceAndCutoff() {
        assertThat(policy.daysSinceLastActivity(quietFor(ApplicationStatus.APPLIED, 45))).isEqualTo(45);
        assertThat(policy.staleCutoff()).isEqualTo(daysAgo(30));
        assertThat(policy.thresholdDays()).isEqualTo(30);
    }

    @Test
    @DisplayName("the ghostable set is derived from the enum, not restated")
    void ghostableStatusesComeFromTheEnum() {
        assertThat(GhostPolicy.ghostableStatuses())
                .containsExactlyInAnyOrder(
                        ApplicationStatus.APPLIED,
                        ApplicationStatus.OA_PENDING,
                        ApplicationStatus.OA_SUBMITTED,
                        ApplicationStatus.INTERVIEW,
                        ApplicationStatus.FINAL_ROUND);
    }

    @Test
    @DisplayName("a configurable threshold is honoured")
    void thresholdIsConfigurable() {
        GhostPolicy strict =
                new GhostPolicy(
                        new TrackerProperties(new TrackerProperties.Ghost(7), new TrackerProperties.Digest(7)),
                        Clock.fixed(NOW, ZoneOffset.UTC));

        assertThat(strict.isGhosted(quietFor(ApplicationStatus.APPLIED, 10))).isTrue();
        assertThat(policy.isGhosted(quietFor(ApplicationStatus.APPLIED, 10))).isFalse();
    }
}
