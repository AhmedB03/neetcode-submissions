package com.ahmedb.internship.service;

import com.ahmedb.internship.config.TrackerProperties;
import com.ahmedb.internship.domain.Application;
import com.ahmedb.internship.domain.ApplicationStatus;
import com.ahmedb.internship.repository.ApplicationRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Builds the digest: what closes soon, and what has gone quiet.
 *
 * <p>Both halves are computed live. Nothing about the digest is stored, so it cannot go stale
 * between polls.
 */
@Service
public class DigestService {

    private static final List<ApplicationStatus> TERMINAL =
            List.of(ApplicationStatus.OFFER, ApplicationStatus.REJECTED);

    private final ApplicationRepository applications;
    private final GhostPolicy ghostPolicy;
    private final Duration horizon;
    private final Clock clock;

    public DigestService(
            ApplicationRepository applications,
            GhostPolicy ghostPolicy,
            TrackerProperties properties,
            Clock clock) {
        this.applications = applications;
        this.ghostPolicy = ghostPolicy;
        this.horizon = Duration.ofDays(properties.digest().horizonDays());
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Digest build() {
        Instant now = clock.instant();
        Instant until = now.plus(horizon);

        List<Application> closingSoon = applications.findWithDeadlineBetween(now, until, TERMINAL);

        // Most overdue last: the list reads forward in time, so the newest miss is at the top.
        List<Application> overdue = applications.findOverdue(now, TERMINAL);

        // Narrowed in SQL to applications that are quiet and in a ghostable status, then confirmed
        // by the same policy the rest of the API reads through, so one definition governs both.
        List<Application> ghosted =
                applications.findStaleCandidates(ghostPolicy.staleCutoff(), GhostPolicy.ghostableStatuses())
                        .stream()
                        .filter(ghostPolicy::isGhosted)
                        .toList();

        return new Digest(now, until, ghostPolicy.thresholdDays(), overdue, closingSoon, ghosted);
    }

    /**
     * @param generatedAt when this digest was computed
     * @param horizon the end of the deadline window, exclusive
     * @param ghostThresholdDays the silence threshold that produced the ghosted list
     * @param overdue live applications whose deadline has already passed
     * @param closingSoon live applications with a deadline inside the horizon
     * @param ghosted live applications that have gone quiet
     */
    public record Digest(
            Instant generatedAt,
            Instant horizon,
            int ghostThresholdDays,
            List<Application> overdue,
            List<Application> closingSoon,
            List<Application> ghosted) {}
}
