package com.ahmedb.internship.api.dto;

import com.ahmedb.internship.service.DigestService;
import com.ahmedb.internship.service.GhostPolicy;
import java.time.Instant;
import java.util.List;

/**
 * What needs attention: deadlines inside the horizon, and applications that have gone quiet.
 *
 * <p>An application can legitimately appear in more than one list -- a quiet application with an
 * overdue assessment is exactly the case worth surfacing more than once.
 */
public record DigestResponse(
        Instant generatedAt,
        Instant horizon,
        int ghostThresholdDays,
        Counts counts,
        List<ApplicationSummary> overdue,
        List<ApplicationSummary> closingSoon,
        List<ApplicationSummary> ghosted) {

    public record Counts(int overdue, int closingSoon, int ghosted) {}

    public static DigestResponse from(DigestService.Digest digest, GhostPolicy ghostPolicy) {
        List<ApplicationSummary> overdue =
                digest.overdue().stream().map(a -> ApplicationSummary.from(a, ghostPolicy)).toList();
        List<ApplicationSummary> closingSoon =
                digest.closingSoon().stream().map(a -> ApplicationSummary.from(a, ghostPolicy)).toList();
        List<ApplicationSummary> ghosted =
                digest.ghosted().stream().map(a -> ApplicationSummary.from(a, ghostPolicy)).toList();

        return new DigestResponse(
                digest.generatedAt(),
                digest.horizon(),
                digest.ghostThresholdDays(),
                new Counts(overdue.size(), closingSoon.size(), ghosted.size()),
                overdue,
                closingSoon,
                ghosted);
    }
}
