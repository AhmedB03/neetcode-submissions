package com.ahmedb.internship.api;

import com.ahmedb.internship.api.dto.ApplicationDetail;
import com.ahmedb.internship.api.dto.ApplicationSummary;
import com.ahmedb.internship.api.dto.Requests;
import com.ahmedb.internship.api.dto.StatusEventView;
import com.ahmedb.internship.domain.Application;
import com.ahmedb.internship.service.ApplicationService;
import com.ahmedb.internship.service.GhostPolicy;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The applications API.
 *
 * <p>Entities never cross this boundary -- everything is a DTO, so the React client gets a contract
 * that survives schema changes. Every status reported here is the effective status, so GHOSTED shows
 * up without ever having been stored.
 */
@RestController
@RequestMapping("/applications")
public class ApplicationController {

    private final ApplicationService applications;
    private final GhostPolicy ghostPolicy;

    public ApplicationController(ApplicationService applications, GhostPolicy ghostPolicy) {
        this.applications = applications;
        this.ghostPolicy = ghostPolicy;
    }

    /** Everything, most urgent first. Applications without a deadline sort last. */
    @GetMapping
    public List<ApplicationSummary> list() {
        return applications.findAllByNextDeadline().stream()
                .map(application -> ApplicationSummary.from(application, ghostPolicy))
                .toList();
    }

    /** One application with its full event timeline. */
    @GetMapping("/{id}")
    public ApplicationDetail get(@PathVariable Long id) {
        return ApplicationDetail.from(applications.findWithTimeline(id), ghostPolicy);
    }

    @PostMapping
    public ResponseEntity<ApplicationSummary> create(@Valid @RequestBody Requests.CreateApplication request) {
        Application created =
                applications.create(
                        request.companyId(),
                        request.roleTitle(),
                        request.cycle(),
                        request.status(),
                        request.appliedDate(),
                        request.nextAction(),
                        request.nextDeadline(),
                        request.sourceUrl());
        return ResponseEntity.created(URI.create("/applications/" + created.getId()))
                .body(ApplicationSummary.from(created, ghostPolicy));
    }

    /** Updates tracking fields. Status changes go through the override endpoint so they leave a trail. */
    @PatchMapping("/{id}")
    public ApplicationSummary update(
            @PathVariable Long id, @Valid @RequestBody Requests.UpdateApplication request) {
        Application updated =
                applications.updateTracking(
                        id,
                        request.nextAction(),
                        request.nextDeadline(),
                        request.appliedDate(),
                        request.sourceUrl());
        return ApplicationSummary.from(updated, ghostPolicy);
    }

    /**
     * Sets the status by hand, recording a MANUAL event on the timeline.
     *
     * <p>Takes effect even when it moves backwards: an override exists to correct the tracker, so
     * the user outranks the progression rule that governs email-driven transitions.
     */
    @PostMapping("/{id}/status")
    public ApplicationDetail overrideStatus(
            @PathVariable Long id, @Valid @RequestBody Requests.OverrideStatus request) {
        applications.overrideStatus(id, request.status(), request.note());
        return ApplicationDetail.from(applications.findWithTimeline(id), ghostPolicy);
    }

    /** The timeline on its own, for a client that already has the summary. */
    @GetMapping("/{id}/events")
    public List<StatusEventView> timeline(@PathVariable Long id) {
        return ApplicationDetail.from(applications.findWithTimeline(id), ghostPolicy).timeline();
    }
}
