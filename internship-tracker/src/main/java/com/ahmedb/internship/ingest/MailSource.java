package com.ahmedb.internship.ingest;

import java.time.Instant;
import java.util.List;

/**
 * A read-only source of mail.
 *
 * <p>The provider boundary. Everything downstream consumes {@link IngestedEmail}, so swapping Gmail
 * for IMAP or Outlook is a new implementation of this and nothing else.
 *
 * <p>Read-only is a property of the interface, not just of today's implementation: there is no
 * method here that mutates a mailbox, and none may be added. The tracker never sends, replies to,
 * labels, archives or deletes anything.
 */
public interface MailSource {

    /**
     * Fetches messages received at or after {@code since}, newest-first as the provider returns
     * them.
     *
     * @param since lower bound on receipt time
     * @param maxMessages upper bound on how many to return in this call
     * @return messages, possibly empty; never null
     */
    List<IngestedEmail> fetchSince(Instant since, int maxMessages);

    /** Human-readable identification for logs and the ingest endpoint's response. */
    String describe();
}
