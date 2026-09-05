package com.ahmedb.internship.ingest.gmail;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Gmail ingestion settings. Every credential arrives from the environment; nothing sensitive has a
 * default and nothing sensitive is committed.
 *
 * <p>Note what is absent: the OAuth scope is not configurable. It is pinned in {@link GmailScopes}
 * so no configuration change, however well-intentioned, can widen this application's access to a
 * mailbox.
 */
@ConfigurationProperties(prefix = "tracker.gmail")
public record GmailProperties(
        @DefaultValue("false") boolean enabled,
        String clientId,
        String clientSecret,
        /** Optional. When present the app runs headless and never opens a browser. */
        String refreshToken,
        @DefaultValue("./.gmail-tokens") String tokenDirectory,
        @DefaultValue("me") String userId,
        @DefaultValue("90") int lookbackDays,
        @DefaultValue("250") int maxResultsPerPoll,
        /**
         * Gmail search restricting what is polled. Defaults to the inbox; widen to
         * {@code -in:chats -in:spam -in:trash} to include mail you have already archived.
         */
        @DefaultValue("in:inbox -in:chats") String query,
        @DefaultValue Poll poll) {

    public record Poll(@DefaultValue("false") boolean enabled, @DefaultValue("0 */15 * * * *") String cron) {}

    public boolean hasStaticRefreshToken() {
        return refreshToken != null && !refreshToken.isBlank();
    }

    public boolean hasClientCredentials() {
        return clientId != null && !clientId.isBlank() && clientSecret != null && !clientSecret.isBlank();
    }
}
