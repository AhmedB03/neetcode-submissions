package com.ahmedb.internship.ingest.gmail;

import com.ahmedb.internship.ingest.MailSource;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.gmail.Gmail;
import java.io.IOException;
import java.security.GeneralSecurityException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the Gmail adapter, and only when ingestion is switched on.
 *
 * <p>With {@code tracker.gmail.enabled=false} no {@link MailSource} bean exists at all, so the
 * application cannot contact Gmail even by accident -- which is what lets tests and local runs work
 * against an empty or seeded database with no credentials present.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "tracker.gmail", name = "enabled", havingValue = "true")
public class GmailConfiguration {

    private static final String APPLICATION_NAME = "internship-tracker";

    @Bean
    public JsonFactory googleJsonFactory() {
        return GsonFactory.getDefaultInstance();
    }

    @Bean
    public HttpTransport googleHttpTransport() throws GeneralSecurityException, IOException {
        return GoogleNetHttpTransport.newTrustedTransport();
    }

    @Bean
    public Gmail gmail(HttpTransport transport, JsonFactory jsonFactory, GmailProperties properties)
            throws GeneralSecurityException, IOException {
        HttpRequestInitializer credentials =
                new GmailAuthorizer(properties, transport, jsonFactory).authorize();
        return new Gmail.Builder(transport, jsonFactory, credentials)
                .setApplicationName(APPLICATION_NAME)
                .build();
    }

    @Bean
    public MailSource gmailMailSource(Gmail gmail, GmailProperties properties) {
        return new GmailMailSource(gmail, properties);
    }
}
