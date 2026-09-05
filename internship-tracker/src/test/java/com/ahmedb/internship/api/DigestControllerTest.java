package com.ahmedb.internship.api;

import static com.ahmedb.internship.TestFixtures.NOW;
import static com.ahmedb.internship.TestFixtures.daysAgo;
import static com.ahmedb.internship.TestFixtures.daysFromNow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ahmedb.internship.domain.Application;
import com.ahmedb.internship.domain.ApplicationStatus;
import com.ahmedb.internship.domain.Company;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DigestControllerTest extends ApiTestBase {

    private Company stripe;

    @BeforeEach
    void seed() {
        stripe = companies.save(new Company("Stripe"));
    }

    private void save(String role, ApplicationStatus status, Instant deadline, Instant lastEvent) {
        Application application = new Application(stripe, role, "Summer 2027");
        application.setStatus(status);
        application.setNextDeadline(deadline);
        application.setLastEventAt(lastEvent);
        applications.save(application);
    }

    @Test
    @DisplayName("GET /digest returns what closes in the next 7 days and what has gone quiet")
    void returnsBothHalves() throws Exception {
        save("Due in 2 days", ApplicationStatus.OA_PENDING, daysFromNow(2), daysAgo(1));
        save("Due in 6 days", ApplicationStatus.APPLIED, daysFromNow(6), daysAgo(2));
        save("Due in 30 days", ApplicationStatus.APPLIED, daysFromNow(30), daysAgo(1));
        save("Silent since June", ApplicationStatus.INTERVIEW, null, daysAgo(70));
        save("Recently active", ApplicationStatus.APPLIED, null, daysAgo(3));

        mockMvc
                .perform(get("/digest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.generatedAt").value(NOW.toString()))
                .andExpect(jsonPath("$.horizon").value(daysFromNow(7).toString()))
                .andExpect(jsonPath("$.ghostThresholdDays").value(30))
                .andExpect(jsonPath("$.counts.closingSoon").value(2))
                .andExpect(jsonPath("$.counts.ghosted").value(1))
                .andExpect(jsonPath("$.closingSoon[0].roleTitle").value("Due in 2 days"))
                .andExpect(jsonPath("$.closingSoon[1].roleTitle").value("Due in 6 days"))
                .andExpect(jsonPath("$.ghosted[0].roleTitle").value("Silent since June"))
                .andExpect(jsonPath("$.ghosted[0].status").value("GHOSTED"))
                .andExpect(jsonPath("$.ghosted[0].storedStatus").value("INTERVIEW"))
                .andExpect(jsonPath("$.ghosted[0].daysSinceLastActivity").value(70));
    }

    @Test
    @DisplayName("a quiet application with a deadline appears in both halves")
    void appearsInBothHalves() throws Exception {
        save("Quiet with an OA due", ApplicationStatus.OA_PENDING, daysFromNow(3), daysAgo(60));

        mockMvc
                .perform(get("/digest"))
                .andExpect(jsonPath("$.counts.closingSoon").value(1))
                .andExpect(jsonPath("$.counts.ghosted").value(1));
    }

    @Test
    @DisplayName("settled applications appear in neither half")
    void terminalApplicationsAreExcluded() throws Exception {
        save("Offered", ApplicationStatus.OFFER, daysFromNow(3), daysAgo(200));
        save("Rejected", ApplicationStatus.REJECTED, daysFromNow(3), daysAgo(200));

        mockMvc
                .perform(get("/digest"))
                .andExpect(jsonPath("$.counts.closingSoon").value(0))
                .andExpect(jsonPath("$.counts.ghosted").value(0));
    }

    @Test
    @DisplayName("an empty pipeline returns an empty digest, not an error")
    void emptyPipeline() throws Exception {
        mockMvc
                .perform(get("/digest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.counts.closingSoon").value(0))
                .andExpect(jsonPath("$.counts.ghosted").value(0))
                .andExpect(jsonPath("$.closingSoon").isArray())
                .andExpect(jsonPath("$.ghosted").isArray());
    }
}
