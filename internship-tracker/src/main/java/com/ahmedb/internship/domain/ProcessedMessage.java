package com.ahmedb.internship.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Every Gmail message the pipeline has already decided about, keyed by Gmail's own message id.
 *
 * <p>This is what makes polling idempotent. A StatusEvent alone is not enough: emails that classify
 * as IGNORE or ABSTAIN produce no event, and without a record here every poll would re-examine them
 * forever.
 */
@Entity
@Table(name = "processed_message")
public class ProcessedMessage {

    public enum Outcome {
        /** Produced a StatusEvent on a matched application. */
        TRANSITION_RECORDED,
        /** Classified as a transition but no application matched; queued for review. */
        QUEUED_FOR_REVIEW,
        /** Confidently not pipeline mail. */
        IGNORED,
        /** The classifier had no opinion. */
        ABSTAINED
    }

    @Id
    @Column(name = "message_id", nullable = false, length = 255)
    private String messageId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private Outcome outcome;

    @Column(name = "classifier_id", length = 64)
    private String classifierId;

    /** The email's own receipt time, used to advance the ingestion watermark. */
    @Column(name = "message_received_at")
    private Instant messageReceivedAt;

    @Column(name = "processed_at", nullable = false, updatable = false)
    private Instant processedAt;

    protected ProcessedMessage() {
        // JPA
    }

    public ProcessedMessage(
            String messageId, Outcome outcome, String classifierId, Instant messageReceivedAt) {
        this.messageId = messageId;
        this.outcome = outcome;
        this.classifierId = classifierId;
        this.messageReceivedAt = messageReceivedAt;
    }

    @PrePersist
    void onCreate() {
        processedAt = Instant.now();
    }

    public String getMessageId() {
        return messageId;
    }

    public Outcome getOutcome() {
        return outcome;
    }

    public String getClassifierId() {
        return classifierId;
    }

    public Instant getMessageReceivedAt() {
        return messageReceivedAt;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }
}
