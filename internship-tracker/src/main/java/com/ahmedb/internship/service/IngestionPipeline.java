package com.ahmedb.internship.service;

import com.ahmedb.internship.classify.ClassificationContext;
import com.ahmedb.internship.domain.Company;
import com.ahmedb.internship.domain.ProcessedMessage;
import com.ahmedb.internship.ingest.IngestedEmail;
import com.ahmedb.internship.ingest.MailSource;
import com.ahmedb.internship.ingest.gmail.GmailProperties;
import com.ahmedb.internship.repository.CompanyRepository;
import com.ahmedb.internship.repository.ProcessedMessageRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/** Drives a polling run: fetch, deduplicate, order, then hand each message to {@link EmailProcessor}. */
@Service
public class IngestionPipeline {

    private static final Logger log = LoggerFactory.getLogger(IngestionPipeline.class);

    /**
     * How far back to re-scan beyond the newest message already processed.
     *
     * <p>Mail does not arrive in timestamp order, so resuming exactly at the watermark can step over
     * a message delivered late. Re-scanning a day costs nothing -- the processed-message ledger
     * discards the duplicates before they are fetched in full.
     */
    private static final Duration RESUME_OVERLAP = Duration.ofDays(1);

    private final ObjectProvider<MailSource> mailSource;
    private final EmailProcessor emailProcessor;
    private final ProcessedMessageRepository processedMessages;
    private final CompanyRepository companies;
    private final GmailProperties gmailProperties;
    private final Clock clock;

    public IngestionPipeline(
            ObjectProvider<MailSource> mailSource,
            EmailProcessor emailProcessor,
            ProcessedMessageRepository processedMessages,
            CompanyRepository companies,
            GmailProperties gmailProperties,
            Clock clock) {
        this.mailSource = mailSource;
        this.emailProcessor = emailProcessor;
        this.processedMessages = processedMessages;
        this.companies = companies;
        this.gmailProperties = gmailProperties;
        this.clock = clock;
    }

    /** One polling run. */
    public IngestionResult run() {
        MailSource source = mailSource.getIfAvailable();
        if (source == null) {
            log.debug("No mail source configured; ingestion is a no-op");
            return IngestionResult.notConfigured();
        }

        Instant since = resumePoint();
        List<IngestedEmail> fetched = source.fetchSince(since, gmailProperties.maxResultsPerPoll());
        List<IngestedEmail> fresh = withoutAlreadyProcessed(fetched);

        // Oldest first: an application's status is built up by replaying its mail in order, and a
        // backfill that ran newest-first would apply the ending before the beginning.
        List<IngestedEmail> ordered =
                fresh.stream().sorted(Comparator.comparing(IngestedEmail::receivedAt)).toList();

        ClassificationContext context = buildContext();
        Map<ProcessedMessage.Outcome, Integer> tally = new EnumMap<>(ProcessedMessage.Outcome.class);
        int failed = 0;

        for (IngestedEmail email : ordered) {
            try {
                ProcessedMessage.Outcome outcome = emailProcessor.process(email, context);
                tally.merge(outcome, 1, Integer::sum);
            } catch (RuntimeException e) {
                // One bad message must not abandon the rest of a 90-day backfill. It is left out of
                // the ledger, so the next poll retries it.
                failed++;
                log.warn("Failed to process message {}; it will be retried next poll", email.messageId(), e);
            }
        }

        IngestionResult result =
                new IngestionResult(
                        source.describe(), since, fetched.size(), ordered.size(), failed, Map.copyOf(tally));
        log.info("Ingestion run complete: {}", result);
        return result;
    }

    /**
     * Where to resume: just before the newest message already processed, or the configured lookback
     * window on a first run.
     */
    private Instant resumePoint() {
        Instant lookbackStart = clock.instant().minus(Duration.ofDays(gmailProperties.lookbackDays()));
        return processedMessages
                .findLatestMessageReceivedAt()
                .map(latest -> latest.minus(RESUME_OVERLAP))
                .filter(resume -> resume.isAfter(lookbackStart))
                .orElse(lookbackStart);
    }

    private List<IngestedEmail> withoutAlreadyProcessed(List<IngestedEmail> emails) {
        if (emails.isEmpty()) {
            return List.of();
        }
        Set<String> seen =
                new HashSet<>(
                        processedMessages.findExistingMessageIds(
                                emails.stream().map(IngestedEmail::messageId).toList()));
        return emails.stream().filter(email -> !seen.contains(email.messageId())).toList();
    }

    /**
     * The tracked companies, as grounding for the classifier.
     *
     * <p>Reads through {@code findAllWithEmailDomains} rather than {@code findAll}: the domains are
     * a lazy collection and this runs outside a transaction, so they have to arrive with the query.
     */
    private ClassificationContext buildContext() {
        List<ClassificationContext.KnownCompany> known =
                companies.findAllWithEmailDomains().stream()
                        .map(
                                (Company company) ->
                                        new ClassificationContext.KnownCompany(
                                                company.getId(), company.getName(), Set.copyOf(company.getEmailDomains())))
                        .toList();
        return new ClassificationContext(known);
    }

    /** What one polling run did. */
    public record IngestionResult(
            String source,
            Instant since,
            int fetched,
            int processed,
            int failed,
            Map<ProcessedMessage.Outcome, Integer> outcomes) {

        public static IngestionResult notConfigured() {
            return new IngestionResult("none", Instant.EPOCH, 0, 0, 0, Map.of());
        }

        public int countOf(ProcessedMessage.Outcome outcome) {
            return outcomes.getOrDefault(outcome, 0);
        }

        @Override
        public String toString() {
            return "source=%s since=%s fetched=%d processed=%d failed=%d outcomes=%s"
                    .formatted(source, since, fetched, processed, failed, outcomes);
        }
    }
}
