package com.ahmedb.internship.api.dto;

import com.ahmedb.internship.domain.ApplicationStatus;
import com.ahmedb.internship.domain.UnmatchedEmail;
import java.time.Instant;

/** A queued email awaiting a decision. */
public record UnmatchedEmailView(
        Long id,
        ApplicationStatus proposedStatus,
        String companyHint,
        String roleHint,
        Double confidence,
        String reason,
        String classifierId,
        UnmatchedEmail.Resolution resolution,
        Long linkedApplicationId,
        Instant createdAt,
        StatusEventView.EvidenceView evidence) {

    public static UnmatchedEmailView from(UnmatchedEmail email) {
        StatusEventView.EvidenceView evidence = null;
        if (email.getEvidence() != null && !email.getEvidence().isEmpty()) {
            evidence =
                    new StatusEventView.EvidenceView(
                            email.getEvidence().getMessageId(),
                            email.getEvidence().getThreadId(),
                            email.getEvidence().getSubject(),
                            email.getEvidence().getFromAddress(),
                            email.getEvidence().getReceivedAt());
        }
        return new UnmatchedEmailView(
                email.getId(),
                email.getProposedStatus(),
                email.getCompanyHint(),
                email.getRoleHint(),
                email.getConfidence(),
                email.getReason(),
                email.getClassifierId(),
                email.getResolution(),
                email.getLinkedApplication() == null ? null : email.getLinkedApplication().getId(),
                email.getCreatedAt(),
                evidence);
    }
}
