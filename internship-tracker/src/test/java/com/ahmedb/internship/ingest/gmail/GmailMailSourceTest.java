package com.ahmedb.internship.ingest.gmail;

import static org.assertj.core.api.Assertions.assertThat;

import com.ahmedb.internship.ingest.IngestedEmail;
import com.google.api.services.gmail.model.Message;
import com.google.api.services.gmail.model.MessagePart;
import com.google.api.services.gmail.model.MessagePartHeader;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The Gmail-to-neutral mapping, exercised against real message shapes without a network. */
class GmailMailSourceTest {

    private Message message(String id, Long internalDate, String snippet, String... headerPairs) {
        MessagePart payload = new MessagePart();
        List<MessagePartHeader> headers = new java.util.ArrayList<>();
        for (int i = 0; i < headerPairs.length; i += 2) {
            headers.add(new MessagePartHeader().setName(headerPairs[i]).setValue(headerPairs[i + 1]));
        }
        payload.setHeaders(headers);

        return new Message()
                .setId(id)
                .setThreadId("thread-" + id)
                .setInternalDate(internalDate)
                .setSnippet(snippet)
                .setPayload(payload);
    }

    @Test
    @DisplayName("a Gmail message maps onto the provider-neutral form")
    void mapsFullMessage() {
        Message message =
                message(
                        "18f2a",
                        1_757_070_000_000L,
                        "Thank you for applying to Stripe.",
                        "From",
                        "Stripe Recruiting <no-reply@stripe.com>",
                        "Subject",
                        "Your application to Stripe");

        IngestedEmail email = GmailMailSource.toIngestedEmail(message).orElseThrow();

        assertThat(email.messageId()).isEqualTo("18f2a");
        assertThat(email.threadId()).isEqualTo("thread-18f2a");
        assertThat(email.subject()).isEqualTo("Your application to Stripe");
        assertThat(email.fromAddress()).isEqualTo("no-reply@stripe.com");
        assertThat(email.fromDisplayName()).isEqualTo("Stripe Recruiting");
        assertThat(email.senderDomain()).isEqualTo("stripe.com");
        assertThat(email.receivedAt()).isEqualTo(Instant.ofEpochMilli(1_757_070_000_000L));
        assertThat(email.snippet()).isEqualTo("Thank you for applying to Stripe.");
    }

    @Test
    @DisplayName("receipt time comes from Gmail, not from the sender-written Date header")
    void prefersInternalDate() {
        Message message =
                message(
                        "m1",
                        1_757_070_000_000L,
                        "",
                        "Date",
                        "Tue, 1 Jan 1980 00:00:00 +0000",
                        "From",
                        "a@stripe.com",
                        "Subject",
                        "Hello");

        assertThat(GmailMailSource.toIngestedEmail(message).orElseThrow().receivedAt())
                .isEqualTo(Instant.ofEpochMilli(1_757_070_000_000L));
    }

    @Test
    @DisplayName("headers are matched case-insensitively, as SMTP allows any casing")
    void headerLookupIsCaseInsensitive() {
        Message message = message("m1", 1L, "", "FROM", "a@stripe.com", "subject", "Hi");

        IngestedEmail email = GmailMailSource.toIngestedEmail(message).orElseThrow();

        assertThat(email.fromAddress()).isEqualTo("a@stripe.com");
        assertThat(email.subject()).isEqualTo("Hi");
    }

    @Test
    @DisplayName("an encoded subject is decoded during mapping")
    void decodesEncodedSubject() {
        Message message =
                message("m1", 1L, "", "From", "a@stripe.com", "Subject", "=?UTF-8?B?VGhhbmsgeW91?=");

        assertThat(GmailMailSource.toIngestedEmail(message).orElseThrow().subject()).isEqualTo("Thank you");
    }

    @Test
    @DisplayName("a message missing headers or a payload still maps, with nulls")
    void toleratesMissingPieces() {
        IngestedEmail noPayload =
                GmailMailSource.toIngestedEmail(new Message().setId("m1").setThreadId("t1")).orElseThrow();

        assertThat(noPayload.subject()).isNull();
        assertThat(noPayload.fromAddress()).isNull();
        assertThat(noPayload.senderDomain()).isEmpty();
        assertThat(noPayload.receivedAt()).isNotNull();

        IngestedEmail noHeaders = GmailMailSource.toIngestedEmail(message("m2", 1L, "snip")).orElseThrow();
        assertThat(noHeaders.subject()).isNull();
        assertThat(noHeaders.snippet()).isEqualTo("snip");
    }

    @Test
    @DisplayName("a message with no id is dropped rather than mapped to something unusable")
    void dropsUnidentifiableMessages() {
        assertThat(GmailMailSource.toIngestedEmail(null)).isEmpty();
        assertThat(GmailMailSource.toIngestedEmail(new Message())).isEqualTo(Optional.empty());
    }
}
