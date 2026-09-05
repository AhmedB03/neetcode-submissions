package com.ahmedb.internship.service;

import com.ahmedb.internship.config.TrackerProperties;
import com.ahmedb.internship.domain.Application;
import com.ahmedb.internship.domain.ApplicationStatus;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Decides when an application counts as ghosted.
 *
 * <p>GHOSTED is derived and never written. Every read goes through here, so the answer cannot drift
 * from the data: it is a function of the newest event's timestamp and the threshold, computed fresh
 * each time. There is no job to run, nothing to backfill, and no way for a stored row to disagree.
 *
 * <p>Un-ghosting is free as a result -- a reply after two months of silence moves the application
 * back the moment its event lands, with no special handling.
 */
@Component
public class GhostPolicy {

    private final Duration threshold;
    private final Clock clock;

    public GhostPolicy(TrackerProperties properties, Clock clock) {
        this.threshold = Duration.ofDays(properties.ghost().thresholdDays());
        this.clock = clock;
    }

    /** Statuses that can go stale. Settled outcomes and never-started applications cannot. */
    public static List<ApplicationStatus> ghostableStatuses() {
        return Arrays.stream(ApplicationStatus.values()).filter(ApplicationStatus::canGhost).toList();
    }

    /** Anything quieter than this instant is a ghosting candidate. */
    public Instant staleCutoff() {
        return clock.instant().minus(threshold);
    }

    public boolean isGhosted(Application application) {
        return application.getStatus().canGhost()
                && application.lastActivityAt() != null
                && application.lastActivityAt().isBefore(staleCutoff());
    }

    /**
     * The status to show a user: the stored one, or GHOSTED when the application has gone quiet.
     * This is the only status the API ever reports.
     */
    public ApplicationStatus effectiveStatus(Application application) {
        return isGhosted(application) ? ApplicationStatus.GHOSTED : application.getStatus();
    }

    /** Days since the newest event, or since creation for an application that never had one. */
    public long daysSinceLastActivity(Application application) {
        Instant last = application.lastActivityAt();
        return last == null ? 0 : ChronoUnit.DAYS.between(last, clock.instant());
    }

    public int thresholdDays() {
        return (int) threshold.toDays();
    }
}
