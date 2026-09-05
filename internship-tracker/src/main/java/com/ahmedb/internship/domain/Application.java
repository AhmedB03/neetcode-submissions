package com.ahmedb.internship.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * One role you applied to (or intend to). The stored {@link #status} is the pipeline head; the
 * {@link StatusEvent} list is the immutable log that produced it.
 *
 * <p>{@link ApplicationStatus#GHOSTED} is never stored here -- it is derived from {@link
 * #lastEventAt} at read time.
 */
@Entity
@Table(
        name = "application",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uk_application_company_role_cycle",
                        columnNames = {"company_id", "role_title", "cycle"}))
public class Application {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "company_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_application_company"))
    private Company company;

    @NotBlank
    @Column(name = "role_title", nullable = false, length = 255)
    private String roleTitle;

    /** Recruiting cycle, e.g. "Summer 2027". */
    @NotBlank
    @Column(nullable = false, length = 64)
    private String cycle;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ApplicationStatus status = ApplicationStatus.NOT_APPLIED;

    @Column(name = "applied_date")
    private LocalDate appliedDate;

    @Column(name = "next_action", length = 500)
    private String nextAction;

    @Column(name = "next_deadline")
    private Instant nextDeadline;

    @Column(name = "source_url", length = 2048)
    private String sourceUrl;

    /**
     * Timestamp of the newest {@link StatusEvent}, maintained in the same transaction that writes
     * one. Denormalized so ghost detection and deadline sorting stay a single indexed scan instead
     * of a correlated subquery per row.
     */
    @Column(name = "last_event_at")
    private Instant lastEventAt;

    @OneToMany(
            mappedBy = "application",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.LAZY)
    @OrderBy("occurredAt ASC, id ASC")
    private List<StatusEvent> events = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Application() {
        // JPA
    }

    public Application(Company company, String roleTitle, String cycle) {
        this.company = company;
        this.roleTitle = roleTitle;
        this.cycle = cycle;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    /**
     * Attaches an event to the timeline and moves {@link #lastEventAt} forward.
     *
     * <p>Deliberately does <em>not</em> touch {@link #status}: whether an event advances the head is
     * a policy decision that lives in the service layer, and out-of-order backfill means not every
     * recorded event should. Every event is logged either way.
     */
    public void recordEvent(StatusEvent event) {
        events.add(event);
        event.setApplication(this);
        if (lastEventAt == null || event.getOccurredAt().isAfter(lastEventAt)) {
            lastEventAt = event.getOccurredAt();
        }
    }

    /** The instant ghost detection counts from: the newest event, or creation if there are none. */
    public Instant lastActivityAt() {
        return lastEventAt != null ? lastEventAt : createdAt;
    }

    public Long getId() {
        return id;
    }

    public Company getCompany() {
        return company;
    }

    public void setCompany(Company company) {
        this.company = company;
    }

    public String getRoleTitle() {
        return roleTitle;
    }

    public void setRoleTitle(String roleTitle) {
        this.roleTitle = roleTitle;
    }

    public String getCycle() {
        return cycle;
    }

    public void setCycle(String cycle) {
        this.cycle = cycle;
    }

    public ApplicationStatus getStatus() {
        return status;
    }

    /** @throws IllegalArgumentException if given a derived status such as GHOSTED. */
    public void setStatus(ApplicationStatus status) {
        this.status = ApplicationStatus.requireStorable(status);
    }

    public LocalDate getAppliedDate() {
        return appliedDate;
    }

    public void setAppliedDate(LocalDate appliedDate) {
        this.appliedDate = appliedDate;
    }

    public String getNextAction() {
        return nextAction;
    }

    public void setNextAction(String nextAction) {
        this.nextAction = nextAction;
    }

    public Instant getNextDeadline() {
        return nextDeadline;
    }

    public void setNextDeadline(Instant nextDeadline) {
        this.nextDeadline = nextDeadline;
    }

    public String getSourceUrl() {
        return sourceUrl;
    }

    public void setSourceUrl(String sourceUrl) {
        this.sourceUrl = sourceUrl;
    }

    public Instant getLastEventAt() {
        return lastEventAt;
    }

    public void setLastEventAt(Instant lastEventAt) {
        this.lastEventAt = lastEventAt;
    }

    public List<StatusEvent> getEvents() {
        return events;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
