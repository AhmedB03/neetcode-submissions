package com.ahmedb.internship.ingest.gmail;

import java.util.List;

/**
 * The OAuth scopes this application requests. There is exactly one, and it is read-only.
 *
 * <p>This is a hard product constraint, not a default: the tracker reads mail to work out what
 * happened to an application and does nothing else. It never sends, replies, drafts, labels,
 * archives, trashes or modifies anything in a mailbox.
 *
 * <p>Kept as a constant with no configuration hook so widening access requires a deliberate code
 * change and shows up in review. {@code GmailScopesTest} fails the build if anything else appears
 * here.
 */
public final class GmailScopes {

    /** {@code https://www.googleapis.com/auth/gmail.readonly} -- read all resources and metadata. */
    public static final String GMAIL_READONLY =
            com.google.api.services.gmail.GmailScopes.GMAIL_READONLY;

    public static final List<String> SCOPES = List.of(GMAIL_READONLY);

    private GmailScopes() {}
}
