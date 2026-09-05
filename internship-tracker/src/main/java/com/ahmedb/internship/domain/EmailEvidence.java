package com.ahmedb.internship.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.time.Instant;
import java.util.Objects;

/**
 * The mail that justified a status change, kept so every automated transition can be traced back to
 * a specific message. Absent on manual overrides.
 *
 * <p>Only headers are stored -- message id, thread id, subject, sender, timestamp. Body text is
 * classified in memory and never persisted.
 */
@Embeddable
public class EmailEvidence {

    @Column(name = "evidence_message_id", length = 255)
    private String messageId;

    @Column(name = "evidence_thread_id", length = 255)
    private String threadId;

    @Column(name = "evidence_subject", length = 998)
    private String subject;

    @Column(name = "evidence_from_address", length = 320)
    private String fromAddress;

    @Column(name = "evidence_received_at")
    private Instant receivedAt;

    protected EmailEvidence() {
        // JPA
    }

    public EmailEvidence(
            String messageId, String threadId, String subject, String fromAddress, Instant receivedAt) {
        this.messageId = messageId;
        this.threadId = threadId;
        this.subject = subject;
        this.fromAddress = fromAddress;
        this.receivedAt = receivedAt;
    }

    public String getMessageId() {
        return messageId;
    }

    public String getThreadId() {
        return threadId;
    }

    public String getSubject() {
        return subject;
    }

    public String getFromAddress() {
        return fromAddress;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }

    /** True when nothing was captured -- the shape JPA leaves behind for a manual override. */
    public boolean isEmpty() {
        return messageId == null && subject == null && fromAddress == null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof EmailEvidence other)) {
            return false;
        }
        return Objects.equals(messageId, other.messageId)
                && Objects.equals(threadId, other.threadId)
                && Objects.equals(subject, other.subject)
                && Objects.equals(fromAddress, other.fromAddress)
                && Objects.equals(receivedAt, other.receivedAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(messageId, threadId, subject, fromAddress, receivedAt);
    }

    @Override
    public String toString() {
        return "EmailEvidence[messageId=" + messageId + ", subject=" + subject + "]";
    }
}
