package com.ahmedb.internship.domain;

/**
 * Where an application sits in the pipeline.
 *
 * <p>{@link #GHOSTED} is special: it is <em>derived</em>, never stored and never set by hand. An
 * application is reported as ghosted when it is otherwise eligible and no {@link StatusEvent} has
 * landed within the configured threshold. See {@code GhostPolicy}.
 */
public enum ApplicationStatus {

    NOT_APPLIED(0),
    APPLIED(1),
    OA_PENDING(2),
    OA_SUBMITTED(3),
    INTERVIEW(4),
    FINAL_ROUND(5),
    OFFER(6),
    REJECTED(6),

    /** Derived only. Rejected by {@link #requireStorable}. */
    GHOSTED(-1);

    private final int depth;

    ApplicationStatus(int depth) {
        this.depth = depth;
    }

    /**
     * How far along the pipeline this status sits. Used to stop a late-arriving email from knocking
     * an application backwards -- backfilling 90 days of mail delivers events out of order, and a
     * stale "thanks for applying" must not undo an interview.
     */
    public int depth() {
        return depth;
    }

    /** Terminal outcomes. Nothing advances past these, and they never ghost. */
    public boolean isTerminal() {
        return this == OFFER || this == REJECTED;
    }

    /** True for statuses computed at read time rather than persisted. */
    public boolean isDerived() {
        return this == GHOSTED;
    }

    /**
     * Whether an application at this status can be reported as ghosted. Terminal outcomes are
     * settled, and NOT_APPLIED means the clock has not started -- silence there is expected, not
     * evidence of being dropped.
     */
    public boolean canGhost() {
        return !isDerived() && !isTerminal() && this != NOT_APPLIED;
    }

    /**
     * Whether moving from this status to {@code next} is a real advance.
     *
     * <p>Deeper is always an advance. A terminal status is always an advance from a non-terminal one
     * -- a rejection is legitimate at any stage, even though it scores no deeper than an offer.
     */
    public boolean advancesTo(ApplicationStatus next) {
        if (next == null || next.isDerived() || this == next) {
            return false;
        }
        if (isTerminal()) {
            return false;
        }
        return next.isTerminal() || next.depth > depth;
    }

    /** Guards the persistence boundary so a derived status can never be written. */
    public static ApplicationStatus requireStorable(ApplicationStatus status) {
        if (status == null) {
            throw new IllegalArgumentException("status must not be null");
        }
        if (status.isDerived()) {
            throw new IllegalArgumentException(
                    status + " is derived and cannot be stored; it is computed at read time");
        }
        return status;
    }
}
