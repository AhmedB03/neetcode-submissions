package com.ahmedb.internship.service;

import com.ahmedb.internship.classify.Classification;
import com.ahmedb.internship.domain.Application;
import com.ahmedb.internship.domain.ApplicationStatus;
import com.ahmedb.internship.domain.Company;
import com.ahmedb.internship.domain.StatusEvent;
import com.ahmedb.internship.ingest.IngestedEmail;
import com.ahmedb.internship.repository.ApplicationRepository;
import com.ahmedb.internship.repository.CompanyRepository;
import java.time.Clock;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Reads and writes applications, and owns the rule for when an event moves the head status. */
@Service
public class ApplicationService {

    private static final Logger log = LoggerFactory.getLogger(ApplicationService.class);

    private final ApplicationRepository applications;
    private final CompanyRepository companies;
    private final Clock clock;

    public ApplicationService(
            ApplicationRepository applications, CompanyRepository companies, Clock clock) {
        this.applications = applications;
        this.companies = companies;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<Application> findAllByNextDeadline() {
        return applications.findAllByNextDeadline();
    }

    @Transactional(readOnly = true)
    public Application findWithTimeline(Long id) {
        return applications
                .findByIdWithEvents(id)
                .orElseThrow(() -> new ApplicationNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public Application findById(Long id) {
        return applications.findById(id).orElseThrow(() -> new ApplicationNotFoundException(id));
    }

    /**
     * Records a classified email against an application.
     *
     * <p>The event is always written -- the timeline is an audit log and a superseded email is still
     * evidence of what arrived. Whether it moves the head status is a separate question, answered by
     * {@link ApplicationStatus#advancesTo}: a 90-day backfill delivers mail out of order, so a stale
     * acknowledgement must not undo an interview that already happened.
     */
    @Transactional
    public StatusEvent recordClassifiedEmail(
            Application application, Classification classification, IngestedEmail email) {
        ApplicationStatus current = application.getStatus();
        ApplicationStatus proposed = classification.newStatus();
        boolean advances = current.advancesTo(proposed);

        StatusEvent event =
                StatusEvent.fromEmail(
                        current,
                        proposed,
                        email.receivedAt(),
                        email.toEvidence(),
                        classification.classifierId(),
                        classification.confidence(),
                        classification.reason());
        event.setAdvancedStatus(advances);
        application.recordEvent(event);

        if (advances) {
            application.setStatus(proposed);
            log.info(
                    "Application {} moved {} -> {} on message {}",
                    application.getId(),
                    current,
                    proposed,
                    email.messageId());
        } else {
            log.debug(
                    "Application {} stays at {}; message {} classified as {} does not advance it",
                    application.getId(),
                    current,
                    email.messageId(),
                    proposed);
        }

        applications.save(application);
        return event;
    }

    /**
     * Sets the status by hand.
     *
     * <p>Unlike an email-driven transition this always takes effect, including backwards. The point
     * of an override is to correct the tracker -- a misclassification, or something that happened
     * off-email -- so the user's judgement wins over the progression rule.
     *
     * @throws IllegalArgumentException if asked to store GHOSTED, which is derived
     */
    @Transactional
    public StatusEvent overrideStatus(Long applicationId, ApplicationStatus newStatus, String note) {
        Application application = findById(applicationId);
        ApplicationStatus current = application.getStatus();

        StatusEvent event =
                StatusEvent.manual(
                        current,
                        ApplicationStatus.requireStorable(newStatus),
                        clock.instant(),
                        note == null || note.isBlank() ? "manual override" : note);
        event.setAdvancedStatus(true);
        application.recordEvent(event);
        application.setStatus(newStatus);
        applications.save(application);

        log.info("Application {} manually set {} -> {}", applicationId, current, newStatus);
        return event;
    }

    @Transactional
    public Application create(
            Long companyId,
            String roleTitle,
            String cycle,
            ApplicationStatus status,
            java.time.LocalDate appliedDate,
            String nextAction,
            java.time.Instant nextDeadline,
            String sourceUrl) {
        Company company =
                companies.findById(companyId).orElseThrow(() -> new CompanyNotFoundException(companyId));

        Application application = new Application(company, roleTitle, cycle);
        application.setStatus(status == null ? ApplicationStatus.NOT_APPLIED : status);
        application.setAppliedDate(appliedDate);
        application.setNextAction(nextAction);
        application.setNextDeadline(nextDeadline);
        application.setSourceUrl(sourceUrl);
        return applications.save(application);
    }

    /**
     * Updates the tracking fields. Null means "leave alone" -- status is not settable here, since
     * every status change has to go through {@link #overrideStatus} to leave a timeline entry.
     */
    @Transactional
    public Application updateTracking(
            Long applicationId,
            String nextAction,
            java.time.Instant nextDeadline,
            java.time.LocalDate appliedDate,
            String sourceUrl) {
        Application application = findById(applicationId);
        if (nextAction != null) {
            application.setNextAction(nextAction);
        }
        if (nextDeadline != null) {
            application.setNextDeadline(nextDeadline);
        }
        if (appliedDate != null) {
            application.setAppliedDate(appliedDate);
        }
        if (sourceUrl != null) {
            application.setSourceUrl(sourceUrl);
        }
        return applications.save(application);
    }

    /** Thrown when an application id does not exist. Mapped to 404 by the API layer. */
    public static class ApplicationNotFoundException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public ApplicationNotFoundException(Long id) {
            super("No application with id " + id);
        }
    }

    /** Thrown when a company id does not exist. Mapped to 404 by the API layer. */
    public static class CompanyNotFoundException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public CompanyNotFoundException(Long id) {
            super("No company with id " + id);
        }
    }
}
