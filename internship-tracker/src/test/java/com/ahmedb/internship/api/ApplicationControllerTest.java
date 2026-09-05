package com.ahmedb.internship.api;

import static com.ahmedb.internship.TestFixtures.daysAgo;
import static com.ahmedb.internship.TestFixtures.daysFromNow;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ahmedb.internship.domain.Application;
import com.ahmedb.internship.domain.ApplicationStatus;
import com.ahmedb.internship.domain.Company;
import com.ahmedb.internship.domain.StatusEvent;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class ApplicationControllerTest extends ApiTestBase {

    private Company stripe;

    @BeforeEach
    void seed() {
        stripe = companies.save(new Company("Stripe"));
    }

    private Application save(
            String role, ApplicationStatus status, Instant deadline, Instant lastEvent) {
        Application application = new Application(stripe, role, "Summer 2027");
        application.setStatus(status);
        application.setNextDeadline(deadline);
        application.setLastEventAt(lastEvent);
        return applications.save(application);
    }

    @Test
    @DisplayName("GET /applications sorts by next deadline, undated last")
    void listsSortedByDeadline() throws Exception {
        save("Later", ApplicationStatus.APPLIED, daysFromNow(10), daysAgo(1));
        save("Sooner", ApplicationStatus.OA_PENDING, daysFromNow(2), daysAgo(1));
        save("Undated", ApplicationStatus.APPLIED, null, daysAgo(1));

        mockMvc
                .perform(get("/applications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].roleTitle").value("Sooner"))
                .andExpect(jsonPath("$[1].roleTitle").value("Later"))
                .andExpect(jsonPath("$[2].roleTitle").value("Undated"))
                .andExpect(jsonPath("$[2].nextDeadline").doesNotExist())
                .andExpect(jsonPath("$[0].companyName").value("Stripe"));
    }

    @Test
    @DisplayName("a quiet application is reported as GHOSTED without that ever being stored")
    void reportsDerivedGhostedStatus() throws Exception {
        Application quiet = save("Quiet", ApplicationStatus.INTERVIEW, null, daysAgo(45));

        mockMvc
                .perform(get("/applications"))
                .andExpect(jsonPath("$[0].status").value("GHOSTED"))
                .andExpect(jsonPath("$[0].storedStatus").value("INTERVIEW"))
                .andExpect(jsonPath("$[0].ghosted").value(true))
                .andExpect(jsonPath("$[0].daysSinceLastActivity").value(45));

        // The database still holds the real status.
        assertThat(applications.findById(quiet.getId()).orElseThrow().getStatus())
                .isEqualTo(ApplicationStatus.INTERVIEW);
    }

    @Test
    @DisplayName("GET /applications/{id} returns the application with its timeline, oldest first")
    void returnsDetailWithTimeline() throws Exception {
        Application application = save("SWE Intern", ApplicationStatus.INTERVIEW, null, daysAgo(3));
        application.recordEvent(
                StatusEvent.fromEmail(
                        ApplicationStatus.NOT_APPLIED,
                        ApplicationStatus.APPLIED,
                        daysAgo(30),
                        new com.ahmedb.internship.domain.EmailEvidence(
                                "m1", "t1", "Thank you for applying", "careers@stripe.com", daysAgo(30)),
                        "rules:v1",
                        0.89,
                        "application-received matched"));
        application.recordEvent(
                StatusEvent.fromEmail(
                        ApplicationStatus.APPLIED,
                        ApplicationStatus.INTERVIEW,
                        daysAgo(3),
                        new com.ahmedb.internship.domain.EmailEvidence(
                                "m2", "t2", "Interview invitation", "careers@stripe.com", daysAgo(3)),
                        "rules:v1",
                        0.92,
                        "interview matched"));
        applications.save(application);

        mockMvc
                .perform(get("/applications/" + application.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.application.roleTitle").value("SWE Intern"))
                .andExpect(jsonPath("$.timeline.length()").value(2))
                .andExpect(jsonPath("$.timeline[0].newStatus").value("APPLIED"))
                .andExpect(jsonPath("$.timeline[0].evidence.subject").value("Thank you for applying"))
                .andExpect(jsonPath("$.timeline[0].evidence.messageId").value("m1"))
                .andExpect(jsonPath("$.timeline[0].classifierId").value("rules:v1"))
                .andExpect(jsonPath("$.timeline[0].reason").value("application-received matched"))
                .andExpect(jsonPath("$.timeline[1].newStatus").value("INTERVIEW"));
    }

    @Test
    @DisplayName("an unknown application is a 404, not a stack trace")
    void unknownApplicationIsNotFound() throws Exception {
        mockMvc
                .perform(get("/applications/9999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("No application with id 9999"));
    }

    @Test
    @DisplayName("a manual override sets the status and records a MANUAL event")
    void overrideStatusRecordsEvent() throws Exception {
        Application application = save("SWE Intern", ApplicationStatus.APPLIED, null, daysAgo(5));

        mockMvc
                .perform(
                        post("/applications/" + application.getId() + "/status")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json(Map.of("status", "INTERVIEW", "note", "recruiter called me"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.application.status").value("INTERVIEW"))
                .andExpect(jsonPath("$.timeline[-1:].source").value("MANUAL"))
                .andExpect(jsonPath("$.timeline[-1:].reason").value("recruiter called me"))
                .andExpect(jsonPath("$.timeline[-1:].evidence").doesNotExist());

        assertThat(applications.findById(application.getId()).orElseThrow().getStatus())
                .isEqualTo(ApplicationStatus.INTERVIEW);
    }

    @Test
    @DisplayName("an override may move an application backwards, since it exists to correct mistakes")
    void overrideCanRegress() throws Exception {
        Application application = save("SWE Intern", ApplicationStatus.INTERVIEW, null, daysAgo(5));

        mockMvc
                .perform(
                        post("/applications/" + application.getId() + "/status")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json(Map.of("status", "APPLIED", "note", "that email was misclassified"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.application.status").value("APPLIED"));
    }

    @Test
    @DisplayName("overriding to GHOSTED is refused, because it is derived rather than set")
    void cannotOverrideToGhosted() throws Exception {
        Application application = save("SWE Intern", ApplicationStatus.APPLIED, null, daysAgo(5));

        mockMvc
                .perform(
                        post("/applications/" + application.getId() + "/status")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json(Map.of("status", "GHOSTED"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("derived")));

        assertThat(applications.findById(application.getId()).orElseThrow().getStatus())
                .isEqualTo(ApplicationStatus.APPLIED);
    }

    @Test
    @DisplayName("an override on an unknown application is a 404")
    void overrideUnknownApplication() throws Exception {
        mockMvc
                .perform(
                        post("/applications/9999/status")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json(Map.of("status", "INTERVIEW"))))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("POST /applications creates one against a company")
    void createsApplication() throws Exception {
        mockMvc
                .perform(
                        post("/applications")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        json(
                                                Map.of(
                                                        "companyId", stripe.getId(),
                                                        "roleTitle", "Software Engineer Intern",
                                                        "cycle", "Summer 2027",
                                                        "status", "APPLIED",
                                                        "nextAction", "Complete the OA",
                                                        "nextDeadline", daysFromNow(3).toString()))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.roleTitle").value("Software Engineer Intern"))
                .andExpect(jsonPath("$.status").value("APPLIED"))
                .andExpect(jsonPath("$.nextAction").value("Complete the OA"));

        assertThat(applications.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("creating against an unknown company is a 404")
    void createAgainstUnknownCompany() throws Exception {
        mockMvc
                .perform(
                        post("/applications")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        json(Map.of("companyId", 9999, "roleTitle", "SWE Intern", "cycle", "Summer 2027"))))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("a request missing required fields is a 400 naming them")
    void validationFailureNamesFields() throws Exception {
        mockMvc
                .perform(
                        post("/applications")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json(Map.of("roleTitle", ""))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Request validation failed"))
                .andExpect(jsonPath("$.details.companyId").exists())
                .andExpect(jsonPath("$.details.roleTitle").exists())
                .andExpect(jsonPath("$.details.cycle").exists());
    }

    @Test
    @DisplayName("PATCH updates tracking fields and leaves the rest alone")
    void patchesTrackingFields() throws Exception {
        Application application = save("SWE Intern", ApplicationStatus.OA_PENDING, null, daysAgo(2));

        mockMvc
                .perform(
                        patch("/applications/" + application.getId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        json(
                                                Map.of(
                                                        "nextAction", "Finish the HackerRank",
                                                        "nextDeadline", daysFromNow(4).toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nextAction").value("Finish the HackerRank"))
                .andExpect(jsonPath("$.status").value("OA_PENDING"))
                .andExpect(jsonPath("$.roleTitle").value("SWE Intern"));
    }

    @Test
    @DisplayName("the same role in the same cycle cannot be created twice")
    void duplicateApplicationIsAConflict() throws Exception {
        save("SWE Intern", ApplicationStatus.APPLIED, null, daysAgo(2));

        mockMvc
                .perform(
                        post("/applications")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        json(
                                                Map.of(
                                                        "companyId", stripe.getId(),
                                                        "roleTitle", "SWE Intern",
                                                        "cycle", "Summer 2027"))))
                .andExpect(status().isConflict());
    }
}
