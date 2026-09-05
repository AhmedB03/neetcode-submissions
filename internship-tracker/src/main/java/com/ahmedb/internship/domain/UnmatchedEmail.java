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
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;

/**
 * The review queue. An email that classified as a real transition but could not be tied to a known
 * application lands here instead of inventing pipeline entries on your behalf.
 *
 * <p>Nothing enters the pipeline from this table until you resolve an entry by linking it to an
 * application.
 */
@Entity
@Table(
        name = "unmatched_email",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uk_unmatched_email_message_id",
                        columnNames = "evidence_message_id"))
public class UnmatchedEmail {

    public enum Resolution {
        /** Awaiting your decision. */
        PENDING,
        /** Linked to an application; a StatusEvent was written. */
        LINKED,
        /** You decided it was noise. */
        DISMISSED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Embedded
    private EmailEvidence evidence;

    /** What the classifier thought this email meant. */
    @Enumerated(EnumType.STRING)
    @Column(name = "proposed_status", length = 32)
    private ApplicationStatus proposedStatus;

    @Column(name = "company_hint", length = 255)
    private String companyHint;

    @Column(name = "role_hint", length = 255)
    private String roleHint;

    @Column private Double confidence;

    @Column(length = 500)
    private String reason;

    @Column(name = "classifier_id", length = 64)
    private String classifierId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Resolution resolution = Resolution.PENDING;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "linked_application_id",
            foreignKey = @ForeignKey(name = "fk_unmatched_email_application"))
    private Application linkedApplication;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected UnmatchedEmail() {
        // JPA
    }

    public UnmatchedEmail(
            EmailEvidence evidence,
            ApplicationStatus proposedStatus,
            String companyHint,
            String roleHint,
            Double confidence,
            String reason,
            String classifierId) {
        this.evidence = evidence;
        this.proposedStatus = proposedStatus;
        this.companyHint = companyHint;
        this.roleHint = roleHint;
        this.confidence = confidence;
        this.reason = reason;
        this.classifierId = classifierId;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public void linkTo(Application application) {
        this.linkedApplication = application;
        this.resolution = Resolution.LINKED;
        this.resolvedAt = Instant.now();
    }

    public void dismiss() {
        this.resolution = Resolution.DISMISSED;
        this.resolvedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public EmailEvidence getEvidence() {
        return evidence;
    }

    public ApplicationStatus getProposedStatus() {
        return proposedStatus;
    }

    public String getCompanyHint() {
        return companyHint;
    }

    public String getRoleHint() {
        return roleHint;
    }

    public Double getConfidence() {
        return confidence;
    }

    public String getReason() {
        return reason;
    }

    public String getClassifierId() {
        return classifierId;
    }

    public Resolution getResolution() {
        return resolution;
    }

    public Application getLinkedApplication() {
        return linkedApplication;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
