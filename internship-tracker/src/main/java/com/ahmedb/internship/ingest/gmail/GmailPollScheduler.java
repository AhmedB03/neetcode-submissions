package com.ahmedb.internship.ingest.gmail;

import com.ahmedb.internship.service.IngestionPipeline;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Polls Gmail on a schedule.
 *
 * <p>Opt-in: without {@code GMAIL_POLL_ENABLED=true} this bean does not exist and ingestion happens
 * only when {@code POST /ingest/run} is called. Reaching out to someone's mailbox on a timer should
 * be a decision, not a default.
 */
@Component
@ConditionalOnProperty(prefix = "tracker.gmail.poll", name = "enabled", havingValue = "true")
public class GmailPollScheduler {

    private static final Logger log = LoggerFactory.getLogger(GmailPollScheduler.class);

    private final IngestionPipeline pipeline;

    public GmailPollScheduler(IngestionPipeline pipeline) {
        this.pipeline = pipeline;
    }

    @Scheduled(cron = "${tracker.gmail.poll.cron}")
    public void poll() {
        try {
            pipeline.run();
        } catch (RuntimeException e) {
            // A scheduled task that throws is silently unscheduled by some executors; swallow and
            // log so a transient Gmail outage does not quietly end all future polling.
            log.error("Scheduled Gmail poll failed; the next run will retry", e);
        }
    }
}
