package com.ahmedb.internship.service;

import com.ahmedb.internship.TestClockConfiguration;
import com.ahmedb.internship.repository.ApplicationRepository;
import com.ahmedb.internship.repository.CompanyRepository;
import com.ahmedb.internship.repository.ListingRepository;
import com.ahmedb.internship.repository.ProcessedMessageRepository;
import com.ahmedb.internship.repository.StatusEventRepository;
import com.ahmedb.internship.repository.UnmatchedEmailRepository;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * Full-context service tests.
 *
 * <p>Deliberately not {@code @Transactional}: the ingestion pipeline commits each message in its own
 * transaction, and wrapping the test in one would hide exactly the behaviour worth checking. Tables
 * are truncated between tests instead.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestClockConfiguration.class)
abstract class ServiceTestBase {

    @Autowired protected ApplicationRepository applications;
    @Autowired protected CompanyRepository companies;
    @Autowired protected StatusEventRepository statusEvents;
    @Autowired protected UnmatchedEmailRepository unmatchedEmails;
    @Autowired protected ProcessedMessageRepository processedMessages;
    @Autowired protected ListingRepository listings;

    @BeforeEach
    void resetDatabase() {
        // Child rows first: listings and queued emails both point at applications.
        listings.deleteAll();
        unmatchedEmails.deleteAll();
        statusEvents.deleteAll();
        applications.deleteAll();
        companies.deleteAll();
        processedMessages.deleteAll();
    }
}
