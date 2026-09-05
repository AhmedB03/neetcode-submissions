package com.ahmedb.internship.api.dto;

import com.ahmedb.internship.domain.ApplicationStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;

/** Request bodies. Grouped in one file because each is a handful of fields with no behaviour. */
public final class Requests {

    private Requests() {}

    public record CreateCompany(
            @NotBlank @Size(max = 255) String name,
            @Size(max = 2048) String careersUrl,
            String notes,
            /** Sender domains for this company. Matching an email starts by resolving one of these. */
            Set<String> emailDomains) {}

    public record CreateApplication(
            @NotNull Long companyId,
            @NotBlank @Size(max = 255) String roleTitle,
            @NotBlank @Size(max = 64) String cycle,
            ApplicationStatus status,
            LocalDate appliedDate,
            @Size(max = 500) String nextAction,
            Instant nextDeadline,
            @Size(max = 2048) String sourceUrl) {}

    /** Null fields are left unchanged. Status is not settable here -- it goes through the override. */
    public record UpdateApplication(
            @Size(max = 500) String nextAction,
            Instant nextDeadline,
            LocalDate appliedDate,
            @Size(max = 2048) String sourceUrl) {}

    /**
     * @param status the new status; GHOSTED is rejected, since it is derived rather than set
     * @param note why, recorded on the timeline entry
     */
    public record OverrideStatus(@NotNull ApplicationStatus status, @Size(max = 500) String note) {}

    /**
     * Resolves a queued email by attaching it to an application.
     *
     * @param applicationId the application this email was really about
     * @param learnSenderDomain whether to remember the sender's domain for that company, so mail
     *     like it matches automatically next time
     */
    public record LinkUnmatchedEmail(
            @NotNull Long applicationId, Boolean learnSenderDomain, @Size(max = 500) String note) {}
}
