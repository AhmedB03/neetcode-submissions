package com.ahmedb.internship.api.dto;

import com.ahmedb.internship.domain.Application;
import com.ahmedb.internship.domain.ApplicationStatus;
import com.ahmedb.internship.service.GhostPolicy;
import java.time.Instant;
import java.time.LocalDate;

/**
 * One row in the applications list.
 *
 * <p>{@code status} is the effective status, so GHOSTED appears here even though it is never stored.
 * {@code storedStatus} is kept alongside it so a client can tell "went quiet while at INTERVIEW"
 * from "was rejected", which the effective status alone would flatten.
 */
public record ApplicationSummary(
        Long id,
        Long companyId,
        String companyName,
        String roleTitle,
        String cycle,
        ApplicationStatus status,
        ApplicationStatus storedStatus,
        boolean ghosted,
        long daysSinceLastActivity,
        LocalDate appliedDate,
        String nextAction,
        Instant nextDeadline,
        String sourceUrl,
        Instant lastEventAt) {

    public static ApplicationSummary from(Application application, GhostPolicy ghostPolicy) {
        return new ApplicationSummary(
                application.getId(),
                application.getCompany().getId(),
                application.getCompany().getName(),
                application.getRoleTitle(),
                application.getCycle(),
                ghostPolicy.effectiveStatus(application),
                application.getStatus(),
                ghostPolicy.isGhosted(application),
                ghostPolicy.daysSinceLastActivity(application),
                application.getAppliedDate(),
                application.getNextAction(),
                application.getNextDeadline(),
                application.getSourceUrl(),
                application.getLastEventAt());
    }
}
