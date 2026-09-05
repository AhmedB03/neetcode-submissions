package com.ahmedb.internship.ingest;

import com.ahmedb.internship.domain.EmailEvidence;
import java.time.Instant;
import java.util.Locale;

/**
 * A mail message, stripped of its provider.
 *
 * <p>Nothing downstream of ingestion knows about Gmail: the Google client types stop at the adapter
 * that produces this record, so a different mail source is a new adapter and nothing else.
 *
 * <p>{@code snippet} is the short preview the provider supplies, not the full body. Phase 1
 * deliberately never fetches or stores message bodies -- headers plus a preview are enough for rule
 * classification, and it keeps the blast radius of a leak small.
 */
public record IngestedEmail(
        String messageId,
        String threadId,
        String subject,
        String fromAddress,
        String fromDisplayName,
        Instant receivedAt,
        String snippet) {

    /** The sender's domain, lowercased, or empty if the address is unparseable. */
    public String senderDomain() {
        if (fromAddress == null) {
            return "";
        }
        int at = fromAddress.lastIndexOf('@');
        return at < 0 ? "" : fromAddress.substring(at + 1).trim().toLowerCase(Locale.ROOT);
    }

    /** Subject and preview folded together, lowercased, for pattern matching. */
    public String searchableText() {
        return ((subject == null ? "" : subject) + " \n " + (snippet == null ? "" : snippet))
                .toLowerCase(Locale.ROOT);
    }

    public String subjectOrEmpty() {
        return subject == null ? "" : subject;
    }

    public EmailEvidence toEvidence() {
        return new EmailEvidence(messageId, threadId, subject, fromAddress, receivedAt);
    }
}
