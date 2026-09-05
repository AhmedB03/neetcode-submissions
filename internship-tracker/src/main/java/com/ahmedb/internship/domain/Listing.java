package com.ahmedb.internship.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.time.LocalDate;

/**
 * A job posting picked up from a source you control -- a company careers page or an aggregator feed.
 *
 * <p>Listings are inert in phase 1: nothing scrapes them yet and nothing acts on them. The entity
 * exists so the schema is stable when a collector lands. Sources are restricted to public careers
 * pages and your own data exports; LinkedIn is explicitly out of scope.
 */
@Entity
@Table(
        name = "listing",
        uniqueConstraints =
                @UniqueConstraint(name = "uk_listing_source_url", columnNames = "source_url"))
public class Listing {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", foreignKey = @ForeignKey(name = "fk_listing_company"))
    private Company company;

    @NotBlank
    @Column(nullable = false, length = 255)
    private String title;

    @Column(length = 255)
    private String location;

    @Column(name = "posted_date")
    private LocalDate postedDate;

    @Column(name = "source_url", length = 2048)
    private String sourceUrl;

    /** Where this posting came from, e.g. "careers-page" or "manual". */
    @Column(length = 64)
    private String source;

    /** The application this posting corresponds to, once matched. Null while unmatched. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "matched_application_id",
            foreignKey = @ForeignKey(name = "fk_listing_matched_application"))
    private Application matchedApplication;

    @Column(name = "first_seen_at", nullable = false)
    private Instant firstSeenAt;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    protected Listing() {
        // JPA
    }

    public Listing(Company company, String title) {
        this.company = company;
        this.title = title;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        firstSeenAt = now;
        lastSeenAt = now;
    }

    @PreUpdate
    void onUpdate() {
        lastSeenAt = Instant.now();
    }

    public boolean isMatched() {
        return matchedApplication != null;
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

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public LocalDate getPostedDate() {
        return postedDate;
    }

    public void setPostedDate(LocalDate postedDate) {
        this.postedDate = postedDate;
    }

    public String getSourceUrl() {
        return sourceUrl;
    }

    public void setSourceUrl(String sourceUrl) {
        this.sourceUrl = sourceUrl;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public Application getMatchedApplication() {
        return matchedApplication;
    }

    public void setMatchedApplication(Application matchedApplication) {
        this.matchedApplication = matchedApplication;
    }

    public Instant getFirstSeenAt() {
        return firstSeenAt;
    }

    public Instant getLastSeenAt() {
        return lastSeenAt;
    }
}
