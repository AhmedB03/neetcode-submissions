package com.ahmedb.internship.api.dto;

import com.ahmedb.internship.domain.ApplicationStatus;
import com.ahmedb.internship.domain.EventSource;
import com.ahmedb.internship.domain.StatusEvent;
import java.time.Instant;

/**
 * One entry in an application's timeline.
 *
 * <p>Carries the evidence and the classifier's reasoning so a wrong call is visible and traceable
 * rather than silently baked into the history.
 */
public record StatusEventView(
        Long id,
        ApplicationStatus oldStatus,
        ApplicationStatus newStatus,
        Instant occurredAt,
        EventSource source,
        boolean advancedStatus,
        String classifierId,
        Double confidence,
        String reason,
        EvidenceView evidence) {

    /** Email headers only. Message bodies are never stored, so none can be exposed. */
    public record EvidenceView(
            String messageId, String threadId, String subject, String fromAddress, Instant receivedAt) {}

    public static StatusEventView from(StatusEvent event) {
        EvidenceView evidence = null;
        if (event.getEvidence() != null && !event.getEvidence().isEmpty()) {
            evidence =
                    new EvidenceView(
                            event.getEvidence().getMessageId(),
                            event.getEvidence().getThreadId(),
                            event.getEvidence().getSubject(),
                            event.getEvidence().getFromAddress(),
                            event.getEvidence().getReceivedAt());
        }
        return new StatusEventView(
                event.getId(),
                event.getOldStatus(),
                event.getNewStatus(),
                event.getOccurredAt(),
                event.getSource(),
                event.isAdvancedStatus(),
                event.getClassifierId(),
                event.getConfidence(),
                event.getReason(),
                evidence);
    }
}
