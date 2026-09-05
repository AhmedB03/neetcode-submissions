package com.ahmedb.internship.ingest.gmail;

import com.ahmedb.internship.ingest.IngestedEmail;
import com.ahmedb.internship.ingest.MailSource;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.ListMessagesResponse;
import com.google.api.services.gmail.model.Message;
import com.google.api.services.gmail.model.MessagePartHeader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reads mail from Gmail. The only class in the system that knows Gmail exists.
 *
 * <p>Uses exactly two API calls -- {@code users.messages.list} and {@code users.messages.get} --
 * both read-only. Messages are fetched with {@code format=METADATA}, so Google returns headers and
 * the preview snippet but never the message body: enough to classify, and nothing more than needed.
 */
public class GmailMailSource implements MailSource {

    private static final Logger log = LoggerFactory.getLogger(GmailMailSource.class);

    private static final List<String> METADATA_HEADERS = List.of("From", "Subject", "Date");
    private static final long GMAIL_PAGE_SIZE = 100L;

    private final Gmail gmail;
    private final GmailProperties properties;

    public GmailMailSource(Gmail gmail, GmailProperties properties) {
        this.gmail = gmail;
        this.properties = properties;
    }

    @Override
    public String describe() {
        return "gmail(" + properties.userId() + ", query=\"" + properties.query() + "\")";
    }

    @Override
    public List<IngestedEmail> fetchSince(Instant since, int maxMessages) {
        String query = properties.query() + " after:" + since.getEpochSecond();
        log.info("Fetching up to {} Gmail messages matching: {}", maxMessages, query);

        try {
            List<String> messageIds = listMessageIds(query, maxMessages);
            List<IngestedEmail> emails = new ArrayList<>(messageIds.size());
            for (String id : messageIds) {
                toIngestedEmail(fetchMetadata(id)).ifPresent(emails::add);
            }
            log.info("Fetched {} Gmail messages", emails.size());
            return emails;
        } catch (IOException e) {
            throw new UncheckedIOException("Gmail fetch failed", e);
        }
    }

    private List<String> listMessageIds(String query, int maxMessages) throws IOException {
        List<String> ids = new ArrayList<>();
        String pageToken = null;

        do {
            long pageSize = Math.min(GMAIL_PAGE_SIZE, (long) maxMessages - ids.size());
            ListMessagesResponse response =
                    gmail.users()
                            .messages()
                            .list(properties.userId())
                            .setQ(query)
                            .setMaxResults(pageSize)
                            .setPageToken(pageToken)
                            .execute();

            if (response.getMessages() != null) {
                response.getMessages().stream().map(Message::getId).forEach(ids::add);
            }
            pageToken = response.getNextPageToken();
        } while (pageToken != null && ids.size() < maxMessages);

        return ids.size() > maxMessages ? ids.subList(0, maxMessages) : ids;
    }

    private Message fetchMetadata(String messageId) throws IOException {
        return gmail.users()
                .messages()
                .get(properties.userId(), messageId)
                .setFormat("METADATA")
                .setMetadataHeaders(METADATA_HEADERS)
                .execute();
    }

    /**
     * Converts a Gmail message to the provider-neutral form.
     *
     * <p>Static and package-private so the mapping can be tested against real message shapes without
     * a network.
     */
    static java.util.Optional<IngestedEmail> toIngestedEmail(Message message) {
        if (message == null || message.getId() == null) {
            return java.util.Optional.empty();
        }

        Map<String, String> headers = headerMap(message);
        MimeHeaders.Sender sender = MimeHeaders.parseSender(headers.get("from"));
        String subject = MimeHeaders.decode(headers.get("subject"));

        // internalDate is Gmail's own receipt timestamp in epoch millis, which is what we want:
        // the Date header is written by the sender and can be wrong or absent.
        Instant receivedAt =
                message.getInternalDate() != null
                        ? Instant.ofEpochMilli(message.getInternalDate())
                        : Instant.now();

        return java.util.Optional.of(
                new IngestedEmail(
                        message.getId(),
                        message.getThreadId(),
                        subject,
                        sender.address(),
                        sender.displayName(),
                        receivedAt,
                        message.getSnippet()));
    }

    private static Map<String, String> headerMap(Message message) {
        if (message.getPayload() == null || message.getPayload().getHeaders() == null) {
            return Map.of();
        }
        return message.getPayload().getHeaders().stream()
                .filter(h -> h.getName() != null && h.getValue() != null)
                .collect(
                        Collectors.toMap(
                                h -> h.getName().toLowerCase(Locale.ROOT),
                                MessagePartHeader::getValue,
                                (first, second) -> first,
                                java.util.HashMap::new));
    }
}
