package com.ahmedb.internship.service;

import com.ahmedb.internship.ingest.IngestedEmail;
import com.ahmedb.internship.ingest.MailSource;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** A mailbox the test writes, standing in for Gmail. */
public class StubMailSource implements MailSource {

    private final List<IngestedEmail> inbox = new ArrayList<>();
    private Instant lastRequestedSince;

    public void deliver(IngestedEmail... emails) {
        inbox.addAll(List.of(emails));
    }

    public void clear() {
        inbox.clear();
        lastRequestedSince = null;
    }

    public Instant lastRequestedSince() {
        return lastRequestedSince;
    }

    @Override
    public List<IngestedEmail> fetchSince(Instant since, int maxMessages) {
        lastRequestedSince = since;
        return inbox.stream()
                .filter(email -> !email.receivedAt().isBefore(since))
                .limit(maxMessages)
                .toList();
    }

    @Override
    public String describe() {
        return "stub-mailbox";
    }
}
