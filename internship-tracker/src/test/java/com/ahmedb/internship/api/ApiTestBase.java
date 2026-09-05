package com.ahmedb.internship.api;

import com.ahmedb.internship.TestClockConfiguration;
import com.ahmedb.internship.repository.ApplicationRepository;
import com.ahmedb.internship.repository.CompanyRepository;
import com.ahmedb.internship.repository.ListingRepository;
import com.ahmedb.internship.repository.ProcessedMessageRepository;
import com.ahmedb.internship.repository.StatusEventRepository;
import com.ahmedb.internship.repository.UnmatchedEmailRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import com.ahmedb.internship.TestProfilesResolver;
import com.ahmedb.internship.TestcontainersConfiguration;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles(resolver = TestProfilesResolver.class)
@Import({TestClockConfiguration.class, TestcontainersConfiguration.class})
abstract class ApiTestBase {

    @Autowired protected MockMvc mockMvc;
    @Autowired protected ObjectMapper objectMapper;

    @Autowired protected ApplicationRepository applications;
    @Autowired protected CompanyRepository companies;
    @Autowired protected StatusEventRepository statusEvents;
    @Autowired protected UnmatchedEmailRepository unmatchedEmails;
    @Autowired protected ProcessedMessageRepository processedMessages;
    @Autowired protected ListingRepository listings;

    @BeforeEach
    void resetDatabase() {
        listings.deleteAll();
        unmatchedEmails.deleteAll();
        statusEvents.deleteAll();
        applications.deleteAll();
        companies.deleteAll();
        processedMessages.deleteAll();
    }

    protected String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }
}
