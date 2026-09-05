package com.ahmedb.internship.ingest.gmail;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Guards the hard constraint: this application reads mail and does nothing else.
 *
 * <p>If someone adds a scope to make a feature work, this test fails and makes them justify it.
 */
class GmailScopesTest {

    /** Every Gmail scope that grants more than reading. */
    private static final List<String> FORBIDDEN_SCOPE_FRAGMENTS =
            List.of("modify", "compose", "send", "insert", "labels", "settings", "full", "mail.google.com");

    @Test
    @DisplayName("exactly one scope is requested, and it is read-only")
    void onlyReadonlyScopeIsRequested() {
        assertThat(GmailScopes.SCOPES)
                .containsExactly("https://www.googleapis.com/auth/gmail.readonly");
    }

    @Test
    @DisplayName("no requested scope can write to a mailbox")
    void noWriteScopes() {
        for (String scope : GmailScopes.SCOPES) {
            assertThat(scope)
                    .as("scope %s", scope)
                    .doesNotContainAnyWhitespaces()
                    .startsWith("https://www.googleapis.com/auth/gmail.");
            for (String forbidden : FORBIDDEN_SCOPE_FRAGMENTS) {
                assertThat(scope).as("scope %s must not grant %s", scope, forbidden).doesNotContain(forbidden);
            }
        }
    }

    @Test
    @DisplayName("the scope list is immutable, so nothing can widen it at runtime")
    void scopeListIsImmutable() {
        assertThat(GmailScopes.SCOPES.getClass().getName()).contains("Immutable");
    }
}
