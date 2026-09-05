package com.ahmedb.internship.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

@Entity
@Table(name = "company", uniqueConstraints = @UniqueConstraint(name = "uk_company_name", columnNames = "name"))
public class Company {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(nullable = false, length = 255)
    private String name;

    @Column(name = "careers_url", length = 2048)
    private String careersUrl;

    @Column(columnDefinition = "text")
    private String notes;

    /**
     * Sender domains that belong to this company, lowercased. Matching an email to an application
     * starts here: {@code no-reply@stripe.com} and {@code careers@greenhouse.io} for a Stripe
     * posting both need to resolve to Stripe.
     */
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
            name = "company_email_domain",
            joinColumns = @JoinColumn(name = "company_id", foreignKey = @jakarta.persistence.ForeignKey(name = "fk_company_email_domain_company")))
    @Column(name = "domain", nullable = false, length = 255)
    private Set<String> emailDomains = new LinkedHashSet<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Company() {
        // JPA
    }

    public Company(String name) {
        this.name = name;
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

    public void addEmailDomain(String domain) {
        if (domain != null && !domain.isBlank()) {
            emailDomains.add(domain.trim().toLowerCase(Locale.ROOT));
        }
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCareersUrl() {
        return careersUrl;
    }

    public void setCareersUrl(String careersUrl) {
        this.careersUrl = careersUrl;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public Set<String> getEmailDomains() {
        return emailDomains;
    }

    public void setEmailDomains(Set<String> emailDomains) {
        this.emailDomains = emailDomains == null ? new LinkedHashSet<>() : emailDomains;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
