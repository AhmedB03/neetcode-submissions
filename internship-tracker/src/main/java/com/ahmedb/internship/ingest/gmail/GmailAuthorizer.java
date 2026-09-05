package com.ahmedb.internship.ingest.gmail;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.UserCredentials;
import java.io.File;
import java.io.IOException;
import java.security.GeneralSecurityException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Obtains read-only Gmail credentials.
 *
 * <p>Two paths, chosen by what is configured:
 *
 * <ol>
 *   <li>{@code GMAIL_REFRESH_TOKEN} set -- use it directly. No browser, no local state; this is the
 *       path for a container or a headless box.
 *   <li>Otherwise -- run the installed-app consent flow once and cache the resulting token under
 *       {@code tokenDirectory}, which is gitignored.
 * </ol>
 *
 * <p>Both request {@link GmailScopes#SCOPES} and nothing else.
 */
public class GmailAuthorizer {

    private static final Logger log = LoggerFactory.getLogger(GmailAuthorizer.class);

    private final GmailProperties properties;
    private final HttpTransport transport;
    private final JsonFactory jsonFactory;

    public GmailAuthorizer(GmailProperties properties, HttpTransport transport, JsonFactory jsonFactory) {
        this.properties = properties;
        this.transport = transport;
        this.jsonFactory = jsonFactory;
    }

    public HttpRequestInitializer authorize() throws IOException, GeneralSecurityException {
        if (!properties.hasClientCredentials()) {
            throw new IllegalStateException(
                    "Gmail is enabled but GMAIL_CLIENT_ID/GMAIL_CLIENT_SECRET are not set. "
                            + "Create an OAuth client of type \"Desktop app\" in the Google Cloud Console "
                            + "and export both, or disable ingestion with GMAIL_ENABLED=false.");
        }
        return properties.hasStaticRefreshToken() ? fromRefreshToken() : fromInstalledAppFlow();
    }

    /** Headless: a refresh token supplied by the environment. */
    private HttpRequestInitializer fromRefreshToken() {
        log.info("Authorizing Gmail with the refresh token from the environment (read-only scope)");
        UserCredentials credentials =
                UserCredentials.newBuilder()
                        .setClientId(properties.clientId())
                        .setClientSecret(properties.clientSecret())
                        .setRefreshToken(properties.refreshToken())
                        .build();
        return new HttpCredentialsAdapter(credentials);
    }

    /** Interactive: one-time local consent, token cached on disk for subsequent runs. */
    private HttpRequestInitializer fromInstalledAppFlow() throws IOException, GeneralSecurityException {
        File tokenDirectory = new File(properties.tokenDirectory());
        log.info(
                "No GMAIL_REFRESH_TOKEN set; running the installed-app consent flow. "
                        + "The token will be cached in {} (gitignored).",
                tokenDirectory.getAbsolutePath());

        GoogleClientSecrets.Details details = new GoogleClientSecrets.Details();
        details.setClientId(properties.clientId());
        details.setClientSecret(properties.clientSecret());
        details.setAuthUri("https://accounts.google.com/o/oauth2/auth");
        details.setTokenUri("https://oauth2.googleapis.com/token");
        GoogleClientSecrets clientSecrets = new GoogleClientSecrets().setInstalled(details);

        GoogleAuthorizationCodeFlow flow =
                new GoogleAuthorizationCodeFlow.Builder(
                                transport, jsonFactory, clientSecrets, GmailScopes.SCOPES)
                        .setDataStoreFactory(new FileDataStoreFactory(tokenDirectory))
                        .setAccessType("offline")
                        .build();

        Credential credential =
                new AuthorizationCodeInstalledApp(flow, new LocalServerReceiver.Builder().setPort(8888).build())
                        .authorize(properties.userId());
        return credential;
    }
}
