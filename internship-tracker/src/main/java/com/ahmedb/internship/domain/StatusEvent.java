package com.ahmedb.internship.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

/**
 * An immutable entry in an application's timeline.
 *
 * <p>Every classified email that resolves to a transition is recorded, whether or not it moved the
 * application's head status. {@link #advancedStatus} says which happened: a late-arriving
 * "thanks for applying" against an application already at INTERVIEW is still worth keeping as
 * history, but must not drag the pipeline backwards.
 */
@Entity
@Table(name = "status_event")
public class StatusEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "application_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_status_event_application"))
    private Application application;

    /** Head status of the application when this event landed. */
    @Enumerated(EnumType.STRING)
    @Column(name = "old_status", length = 32)
    private ApplicationStatus oldStatus;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "new_status", nullable = false, length = 32)
    private ApplicationStatus newStatus;

    /** When the underlying evidence happened -- the email's receipt time, not the write time. */
    @NotNull
    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private EventSource source;

    @Embedded
    private EmailEvidence evidence;

    /** Which classifier produced this, e.g. "rules:v1". Null for manual overrides. */
    @Column(name = "classifier_id", length = 64)
    private String classifierId;

    @Column(name = "confidence")
    private Double confidence;

    /** Human-readable justification, surfaced in the timeline so a wrong call is easy to spot. */
    @Column(length = 500)
    private String reason;

    /** Whether this event moved the parent application's head status. */
    @Column(name = "advanced_status", nullable = false)
    private boolean advancedStatus;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected StatusEvent() {
        // JPA
    }

    private StatusEvent(
            ApplicationStatus oldStatus,
            ApplicationStatus newStatus,
            Instant occurredAt,
            EventSource source) {
        this.oldStatus = oldStatus;
        this.newStatus = ApplicationStatus.requireStorable(newStatus);
        this.occurredAt = occurredAt;
        this.source = source;
    }

    /** An event classified from an ingested email. */
    public static StatusEvent fromEmail(
            ApplicationStatus oldStatus,
            ApplicationStatus newStatus,
            Instant occurredAt,
            EmailEvidence evidence,
            String classifierId,
            Double confidence,
            String reason) {
        StatusEvent event = new StatusEvent(oldStatus, newStatus, occurredAt, EventSource.GMAIL);
        event.evidence = evidence;
        event.classifierId = classifierId;
        event.confidence = confidence;
        event.reason = reason;
        return event;
    }

    /** An event the user created by overriding the status by hand. */
    public static StatusEvent manual(
            ApplicationStatus oldStatus, ApplicationStatus newStatus, Instant occurredAt, String reason) {
        StatusEvent event = new StatusEvent(oldStatus, newStatus, occurredAt, EventSource.MANUAL);
        event.reason = reason;
        return event;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        if (occurredAt == null) {
            occurredAt = createdAt;
        }
    }

    public Long getId() {
        return id;
    }

    public Application getApplication() {
        return application;
    }

    void setApplication(Application application) {
        this.application = application;
    }

    public ApplicationStatus getOldStatus() {
        return oldStatus;
    }

    public ApplicationStatus getNewStatus() {
        return newStatus;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public EventSource getSource() {
        return source;
    }

    public EmailEvidence getEvidence() {
        return evidence;
    }

    public String getClassifierId() {
        return classifierId;
    }

    public Double getConfidence() {
        return confidence;
    }

    public String getReason() {
        return reason;
    }

    public boolean isAdvancedStatus() {
        return advancedStatus;
    }

    public void setAdvancedStatus(boolean advancedStatus) {
        this.advancedStatus = advancedStatus;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
