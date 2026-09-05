package com.ahmedb.internship.api.dto;

import com.ahmedb.internship.service.DigestService;
import com.ahmedb.internship.service.GhostPolicy;
import java.time.Instant;
import java.util.List;

/**
 * What needs attention: deadlines inside the horizon, and applications that have gone quiet.
 *
 * <p>An application can legitimately appear in both lists -- a quiet application with an assessment
 * still due is exactly the case worth surfacing twice.
 */
public record DigestResponse(
        Instant generatedAt,
        Instant horizon,
        int ghostThresholdDays,
        Counts counts,
        List<ApplicationSummary> closingSoon,
        List<ApplicationSummary> ghosted) {

    public record Counts(int closingSoon, int ghosted) {}

    public static DigestResponse from(DigestService.Digest digest, GhostPolicy ghostPolicy) {
        List<ApplicationSummary> closingSoon =
                digest.closingSoon().stream().map(a -> ApplicationSummary.from(a, ghostPolicy)).toList();
        List<ApplicationSummary> ghosted =
                digest.ghosted().stream().map(a -> ApplicationSummary.from(a, ghostPolicy)).toList();

        return new DigestResponse(
                digest.generatedAt(),
                digest.horizon(),
                digest.ghostThresholdDays(),
                new Counts(closingSoon.size(), ghosted.size()),
                closingSoon,
                ghosted);
    }
}
