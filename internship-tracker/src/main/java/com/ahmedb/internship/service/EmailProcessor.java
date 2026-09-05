package com.ahmedb.internship.service;

import com.ahmedb.internship.classify.Classification;
import com.ahmedb.internship.classify.ClassificationContext;
import com.ahmedb.internship.classify.EmailClassifier;
import com.ahmedb.internship.domain.Application;
import com.ahmedb.internship.domain.ProcessedMessage;
import com.ahmedb.internship.domain.UnmatchedEmail;
import com.ahmedb.internship.ingest.IngestedEmail;
import com.ahmedb.internship.repository.ProcessedMessageRepository;
import com.ahmedb.internship.repository.UnmatchedEmailRepository;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handles one email end to end: classify, match, persist.
 *
 * <p>Separate from {@link IngestionPipeline} so each message commits in its own transaction. One
 * malformed message then costs one message, not the whole poll -- which matters most on the first
 * run, when 90 days of mail arrives at once.
 */
@Service
public class EmailProcessor {

    private static final Logger log = LoggerFactory.getLogger(EmailProcessor.class);

    private final EmailClassifier classifier;
    private final ApplicationMatcher matcher;
    private final ApplicationService applicationService;
    private final UnmatchedEmailRepository unmatchedEmails;
    private final ProcessedMessageRepository processedMessages;

    public EmailProcessor(
            EmailClassifier classifier,
            ApplicationMatcher matcher,
            ApplicationService applicationService,
            UnmatchedEmailRepository unmatchedEmails,
            ProcessedMessageRepository processedMessages) {
        this.classifier = classifier;
        this.matcher = matcher;
        this.applicationService = applicationService;
        this.unmatchedEmails = unmatchedEmails;
        this.processedMessages = processedMessages;
    }

    /**
     * @return what was decided, which the caller only tallies
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ProcessedMessage.Outcome process(IngestedEmail email, ClassificationContext context) {
        Classification classification = classifier.classify(email, context);

        ProcessedMessage.Outcome outcome =
                switch (classification.outcome()) {
                    case IGNORE -> {
                        log.debug("Ignoring message {}: {}", email.messageId(), classification.reason());
                        yield ProcessedMessage.Outcome.IGNORED;
                    }
                    case ABSTAIN -> {
                        log.debug("No verdict on message {}: {}", email.messageId(), classification.reason());
                        yield ProcessedMessage.Outcome.ABSTAINED;
                    }
                    case TRANSITION -> recordTransition(email, classification);
                };

        // Written whatever happened, so the next poll never re-examines this message. Emails that
        // produce no event -- ignored and abstained alike -- would otherwise be reconsidered forever.
        processedMessages.save(
                new ProcessedMessage(
                        email.messageId(), outcome, classification.classifierId(), email.receivedAt()));
        return outcome;
    }

    private ProcessedMessage.Outcome recordTransition(
            IngestedEmail email, Classification classification) {
        Optional<Application> match = matcher.match(email, classification);

        if (match.isPresent()) {
            applicationService.recordClassifiedEmail(match.get(), classification, email);
            return ProcessedMessage.Outcome.TRANSITION_RECORDED;
        }

        queueForReview(email, classification);
        return ProcessedMessage.Outcome.QUEUED_FOR_REVIEW;
    }

    /**
     * Parks an unmatched transition for the user to resolve.
     *
     * <p>Nothing is created on the classifier's word alone: a recruiter blast or a misparse would
     * otherwise invent companies and applications that were never applied to.
     */
    private void queueForReview(IngestedEmail email, Classification classification) {
        if (unmatchedEmails.findByEvidenceMessageId(email.messageId()).isPresent()) {
            return;
        }
        unmatchedEmails.save(
                new UnmatchedEmail(
                        email.toEvidence(),
                        classification.newStatus(),
                        classification.companyHint(),
                        classification.roleHint(),
                        classification.confidence(),
                        classification.reason(),
                        classification.classifierId()));
        log.info(
                "Queued message {} for review: {} for company hint \"{}\"",
                email.messageId(),
                classification.newStatus(),
                classification.companyHint());
    }
}
