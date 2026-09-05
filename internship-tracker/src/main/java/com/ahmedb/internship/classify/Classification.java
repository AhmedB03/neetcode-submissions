package com.ahmedb.internship.classify;

import com.ahmedb.internship.domain.ApplicationStatus;

/**
 * A classifier's verdict on one email.
 *
 * <p>Carries no database identity on purpose: a classifier reads an email and says what it means.
 * Tying that meaning to a specific application is the service layer's job, which is what keeps a
 * classifier swappable and unit-testable without a database.
 */
public record Classification(
        Outcome outcome,
        ApplicationStatus newStatus,
        String companyHint,
        String roleHint,
        double confidence,
        String reason,
        String classifierId) {

    public enum Outcome {
        /** This email means the application moved. */
        TRANSITION,
        /**
         * No opinion. Distinct from {@link #IGNORE}: an abstention is a gap in coverage and belongs
         * in the review queue or in front of a stronger classifier.
         */
        ABSTAIN,
        /** Confidently not pipeline mail -- a newsletter, a job alert, marketing. Drop it. */
        IGNORE
    }

    public Classification {
        if (outcome == null) {
            throw new IllegalArgumentException("outcome must not be null");
        }
        if (outcome == Outcome.TRANSITION) {
            ApplicationStatus.requireStorable(newStatus);
        } else if (newStatus != null) {
            throw new IllegalArgumentException(outcome + " must not carry a status, got " + newStatus);
        }
        if (confidence < 0.0 || confidence > 1.0) {
            throw new IllegalArgumentException("confidence must be within [0,1], got " + confidence);
        }
    }

    public static Classification transition(
            ApplicationStatus newStatus,
            String companyHint,
            String roleHint,
            double confidence,
            String reason,
            String classifierId) {
        return new Classification(
                Outcome.TRANSITION, newStatus, companyHint, roleHint, confidence, reason, classifierId);
    }

    public static Classification abstain(String reason, String classifierId) {
        return new Classification(Outcome.ABSTAIN, null, null, null, 0.0, reason, classifierId);
    }

    public static Classification ignore(String reason, String classifierId) {
        return new Classification(Outcome.IGNORE, null, null, null, 0.0, reason, classifierId);
    }

    public boolean isTransition() {
        return outcome == Outcome.TRANSITION;
    }

    public boolean isAbstain() {
        return outcome == Outcome.ABSTAIN;
    }

    /** Copy carrying a different classifier id, used when a composite reports which member decided. */
    public Classification withClassifierId(String id) {
        return new Classification(outcome, newStatus, companyHint, roleHint, confidence, reason, id);
    }
}
