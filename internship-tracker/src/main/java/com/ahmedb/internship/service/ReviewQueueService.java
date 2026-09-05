package com.ahmedb.internship.service;

import com.ahmedb.internship.classify.Classification;
import com.ahmedb.internship.domain.Application;
import com.ahmedb.internship.domain.Company;
import com.ahmedb.internship.domain.EmailEvidence;
import com.ahmedb.internship.domain.StatusEvent;
import com.ahmedb.internship.domain.UnmatchedEmail;
import com.ahmedb.internship.ingest.IngestedEmail;
import com.ahmedb.internship.repository.CompanyRepository;
import com.ahmedb.internship.repository.UnmatchedEmailRepository;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The review queue: emails the classifier understood but could not attribute.
 *
 * <p>This is the deliberate alternative to auto-creating companies and applications. A recruiter
 * blast or a misparsed sender would otherwise invent pipeline entries that were never applied to,
 * and a tracker you cannot trust is worse than no tracker.
 */
@Service
public class ReviewQueueService {

    private static final Logger log = LoggerFactory.getLogger(ReviewQueueService.class);

    private final UnmatchedEmailRepository unmatchedEmails;
    private final CompanyRepository companies;
    private final ApplicationService applicationService;

    public ReviewQueueService(
            UnmatchedEmailRepository unmatchedEmails,
            CompanyRepository companies,
            ApplicationService applicationService) {
        this.unmatchedEmails = unmatchedEmails;
        this.companies = companies;
        this.applicationService = applicationService;
    }

    @Transactional(readOnly = true)
    public List<UnmatchedEmail> pending() {
        return unmatchedEmails.findByResolutionOrderByCreatedAtDesc(UnmatchedEmail.Resolution.PENDING);
    }

    /**
     * Attaches a queued email to an application, writing the event it should have produced.
     *
     * <p>Replays it through the same path a matched email takes, so a resolved email behaves
     * identically to one that matched on arrival -- including the rule that a stale message is
     * recorded without dragging the application backwards.
     *
     * @param learnSenderDomain remember this sender's domain for the application's company, so mail
     *     like it matches on its own next time. This is how matching improves with use.
     */
    @Transactional
    public StatusEvent link(
            Long unmatchedEmailId, Long applicationId, boolean learnSenderDomain, String note) {
        UnmatchedEmail queued = find(unmatchedEmailId);
        if (queued.getResolution() != UnmatchedEmail.Resolution.PENDING) {
            throw new AlreadyResolvedException(unmatchedEmailId, queued.getResolution());
        }

        Application application = applicationService.findById(applicationId);
        EmailEvidence evidence = queued.getEvidence();

        StatusEvent event =
                applicationService.recordClassifiedEmail(
                        application,
                        Classification.transition(
                                queued.getProposedStatus(),
                                queued.getCompanyHint(),
                                queued.getRoleHint(),
                                queued.getConfidence() == null ? 0.0 : queued.getConfidence(),
                                note == null || note.isBlank()
                                        ? "linked from the review queue: " + queued.getReason()
                                        : note,
                                queued.getClassifierId()),
                        toIngestedEmail(evidence));

        if (learnSenderDomain) {
            learnDomain(application.getCompany(), evidence.getFromAddress());
        }

        queued.linkTo(application);
        unmatchedEmails.save(queued);
        return event;
    }

    @Transactional
    public UnmatchedEmail dismiss(Long unmatchedEmailId) {
        UnmatchedEmail queued = find(unmatchedEmailId);
        if (queued.getResolution() != UnmatchedEmail.Resolution.PENDING) {
            throw new AlreadyResolvedException(unmatchedEmailId, queued.getResolution());
        }
        queued.dismiss();
        return unmatchedEmails.save(queued);
    }

    private UnmatchedEmail find(Long id) {
        return unmatchedEmails.findById(id).orElseThrow(() -> new UnmatchedEmailNotFoundException(id));
    }

    private void learnDomain(Company company, String fromAddress) {
        if (fromAddress == null) {
            return;
        }
        int at = fromAddress.lastIndexOf('@');
        if (at < 0) {
            return;
        }
        String domain = fromAddress.substring(at + 1).trim().toLowerCase(Locale.ROOT);
        if (domain.isBlank() || company.getEmailDomains().contains(domain)) {
            return;
        }
        company.addEmailDomain(domain);
        companies.save(company);
        log.info("Learned sender domain {} for company {}", domain, company.getName());
    }

    private static IngestedEmail toIngestedEmail(EmailEvidence evidence) {
        return new IngestedEmail(
                evidence.getMessageId(),
                evidence.getThreadId(),
                evidence.getSubject(),
                evidence.getFromAddress(),
                null,
                evidence.getReceivedAt(),
                null);
    }

    /** Thrown when a queued email id does not exist. Mapped to 404. */
    public static class UnmatchedEmailNotFoundException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public UnmatchedEmailNotFoundException(Long id) {
            super("No queued email with id " + id);
        }
    }

    /** Thrown when a queued email has already been linked or dismissed. Mapped to 409. */
    public static class AlreadyResolvedException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public AlreadyResolvedException(Long id, UnmatchedEmail.Resolution resolution) {
            super("Queued email " + id + " was already " + resolution.name().toLowerCase(Locale.ROOT));
        }
    }
}
