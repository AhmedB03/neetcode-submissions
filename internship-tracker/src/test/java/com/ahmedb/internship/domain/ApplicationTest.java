package com.ahmedb.internship.domain;

import static com.ahmedb.internship.TestFixtures.daysAgo;
import static com.ahmedb.internship.TestFixtures.emailEvent;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ApplicationTest {

    private Application newApplication() {
        return new Application(new Company("Stripe"), "SWE Intern", "Summer 2027");
    }

    @Test
    @DisplayName("recording an event links both sides and moves the activity clock")
    void recordEvent_maintainsBothSides() {
        Application application = newApplication();
        StatusEvent event =
                emailEvent(ApplicationStatus.NOT_APPLIED, ApplicationStatus.APPLIED, daysAgo(5), "m1");

        application.recordEvent(event);

        assertThat(application.getEvents()).containsExactly(event);
        assertThat(event.getApplication()).isSameAs(application);
        assertThat(application.getLastEventAt()).isEqualTo(daysAgo(5));
    }

    @Test
    @DisplayName("an out-of-order event joins the timeline without rewinding the activity clock")
    void recordEvent_keepsLatestActivity() {
        Application application = newApplication();
        application.recordEvent(
                emailEvent(ApplicationStatus.APPLIED, ApplicationStatus.INTERVIEW, daysAgo(3), "recent"));
        application.recordEvent(
                emailEvent(ApplicationStatus.NOT_APPLIED, ApplicationStatus.APPLIED, daysAgo(30), "stale"));

        assertThat(application.getEvents()).hasSize(2);
        assertThat(application.getLastEventAt()).isEqualTo(daysAgo(3));
    }

    @Test
    @DisplayName("recording an event never moves the head status on its own")
    void recordEvent_doesNotTouchStatus() {
        // Whether an event advances the head is a policy decision in the service layer.
        Application application = newApplication();
        application.setStatus(ApplicationStatus.INTERVIEW);

        application.recordEvent(
                emailEvent(ApplicationStatus.NOT_APPLIED, ApplicationStatus.APPLIED, daysAgo(30), "stale"));

        assertThat(application.getStatus()).isEqualTo(ApplicationStatus.INTERVIEW);
    }

    @Test
    @DisplayName("the status setter refuses a derived status")
    void setStatus_rejectsGhosted() {
        assertThatThrownBy(() -> newApplication().setStatus(ApplicationStatus.GHOSTED))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("a StatusEvent cannot be built with a derived status either")
    void statusEvent_rejectsGhosted() {
        assertThatThrownBy(
                        () ->
                                StatusEvent.manual(
                                        ApplicationStatus.APPLIED, ApplicationStatus.GHOSTED, daysAgo(1), "nope"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
