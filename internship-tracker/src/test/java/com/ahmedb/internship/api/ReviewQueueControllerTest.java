package com.ahmedb.internship.api;

import static com.ahmedb.internship.TestFixtures.daysAgo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ahmedb.internship.domain.Application;
import com.ahmedb.internship.domain.ApplicationStatus;
import com.ahmedb.internship.domain.Company;
import com.ahmedb.internship.domain.EmailEvidence;
import com.ahmedb.internship.domain.UnmatchedEmail;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class ReviewQueueControllerTest extends ApiTestBase {

    private Company stripe;
    private Application swe;

    @BeforeEach
    void seed() {
        stripe = companies.save(new Company("Stripe"));
        Application application = new Application(stripe, "Software Engineer Intern", "Summer 2027");
        application.setStatus(ApplicationStatus.APPLIED);
        application.setLastEventAt(daysAgo(10));
        swe = applications.save(application);
    }

    private UnmatchedEmail queue(String messageId, ApplicationStatus proposed, String from) {
        return unmatchedEmails.save(
                new UnmatchedEmail(
                        new EmailEvidence(messageId, "t1", "Interview invitation", from, daysAgo(1)),
                        proposed,
                        "Stripe",
                        "Software Engineer Intern",
                        0.87,
                        "interview matched",
                        "rules:v1"));
    }

    @Test
    @DisplayName("GET /unmatched lists what is waiting on a decision")
    void listsPending() throws Exception {
        queue("m1", ApplicationStatus.INTERVIEW, "no-reply@greenhouse.io");

        mockMvc
                .perform(get("/unmatched"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].proposedStatus").value("INTERVIEW"))
                .andExpect(jsonPath("$[0].companyHint").value("Stripe"))
                .andExpect(jsonPath("$[0].resolution").value("PENDING"))
                .andExpect(jsonPath("$[0].evidence.subject").value("Interview invitation"));
    }

    @Test
    @DisplayName("linking writes the event the email should have produced")
    void linkWritesEvent() throws Exception {
        UnmatchedEmail queued = queue("m1", ApplicationStatus.INTERVIEW, "no-reply@greenhouse.io");

        mockMvc
                .perform(
                        post("/unmatched/" + queued.getId() + "/link")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json(Map.of("applicationId", swe.getId()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.newStatus").value("INTERVIEW"))
                .andExpect(jsonPath("$.oldStatus").value("APPLIED"))
                .andExpect(jsonPath("$.advancedStatus").value(true))
                .andExpect(jsonPath("$.evidence.messageId").value("m1"));

        assertThat(applications.findById(swe.getId()).orElseThrow().getStatus())
                .isEqualTo(ApplicationStatus.INTERVIEW);
        assertThat(unmatchedEmails.findById(queued.getId()).orElseThrow().getResolution())
                .isEqualTo(UnmatchedEmail.Resolution.LINKED);
    }

    @Test
    @DisplayName("linking teaches the company the sender's domain, so the next one matches itself")
    void linkLearnsSenderDomain() throws Exception {
        UnmatchedEmail queued = queue("m1", ApplicationStatus.INTERVIEW, "careers@stripe-ats.example");

        mockMvc
                .perform(
                        post("/unmatched/" + queued.getId() + "/link")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json(Map.of("applicationId", swe.getId(), "learnSenderDomain", true))))
                .andExpect(status().isOk());

        assertThat(companies.findByEmailDomain("stripe-ats.example"))
                .map(Company::getName)
                .contains("Stripe");
    }

    @Test
    @DisplayName("domain learning can be declined")
    void linkCanSkipLearning() throws Exception {
        UnmatchedEmail queued = queue("m1", ApplicationStatus.INTERVIEW, "careers@one-off.example");

        mockMvc
                .perform(
                        post("/unmatched/" + queued.getId() + "/link")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json(Map.of("applicationId", swe.getId(), "learnSenderDomain", false))))
                .andExpect(status().isOk());

        assertThat(companies.findByEmailDomain("one-off.example")).isEmpty();
    }

    @Test
    @DisplayName("a linked email that is stale is recorded without dragging the application back")
    void linkRespectsProgressionRule() throws Exception {
        Application advanced = applications.findById(swe.getId()).orElseThrow();
        advanced.setStatus(ApplicationStatus.FINAL_ROUND);
        applications.save(advanced);

        UnmatchedEmail queued = queue("m1", ApplicationStatus.APPLIED, "no-reply@greenhouse.io");

        mockMvc
                .perform(
                        post("/unmatched/" + queued.getId() + "/link")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json(Map.of("applicationId", swe.getId()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.advancedStatus").value(false));

        assertThat(applications.findById(swe.getId()).orElseThrow().getStatus())
                .isEqualTo(ApplicationStatus.FINAL_ROUND);
    }

    @Test
    @DisplayName("dismissing resolves the entry and writes no event")
    void dismissWritesNoEvent() throws Exception {
        UnmatchedEmail queued = queue("m1", ApplicationStatus.INTERVIEW, "no-reply@greenhouse.io");

        mockMvc
                .perform(post("/unmatched/" + queued.getId() + "/dismiss"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resolution").value("DISMISSED"));

        assertThat(statusEvents.count()).isZero();
        assertThat(applications.findById(swe.getId()).orElseThrow().getStatus())
                .isEqualTo(ApplicationStatus.APPLIED);
    }

    @Test
    @DisplayName("resolving the same entry twice is a conflict, not a duplicate event")
    void doubleResolutionIsAConflict() throws Exception {
        UnmatchedEmail queued = queue("m1", ApplicationStatus.INTERVIEW, "no-reply@greenhouse.io");

        mockMvc
                .perform(
                        post("/unmatched/" + queued.getId() + "/link")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json(Map.of("applicationId", swe.getId()))))
                .andExpect(status().isOk());

        mockMvc
                .perform(
                        post("/unmatched/" + queued.getId() + "/link")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json(Map.of("applicationId", swe.getId()))))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/unmatched/" + queued.getId() + "/dismiss")).andExpect(status().isConflict());
        assertThat(statusEvents.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("unknown ids are 404s")
    void unknownIds() throws Exception {
        mockMvc
                .perform(
                        post("/unmatched/9999/link")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json(Map.of("applicationId", swe.getId()))))
                .andExpect(status().isNotFound());

        UnmatchedEmail queued = queue("m1", ApplicationStatus.INTERVIEW, "no-reply@greenhouse.io");
        mockMvc
                .perform(
                        post("/unmatched/" + queued.getId() + "/link")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json(Map.of("applicationId", 9999))))
                .andExpect(status().isNotFound());
    }
}
