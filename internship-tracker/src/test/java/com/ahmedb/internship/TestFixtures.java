package com.ahmedb.internship;

import com.ahmedb.internship.domain.Application;
import com.ahmedb.internship.domain.ApplicationStatus;
import com.ahmedb.internship.domain.Company;
import com.ahmedb.internship.domain.EmailEvidence;
import com.ahmedb.internship.domain.StatusEvent;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/** Builders for the entity graph, so tests read as scenarios rather than setup. */
public final class TestFixtures {

    public static final Instant NOW = Instant.parse("2026-09-05T12:00:00Z");

    private TestFixtures() {}

    public static Instant daysAgo(int days) {
        return NOW.minus(days, ChronoUnit.DAYS);
    }

    public static Instant daysFromNow(int days) {
        return NOW.plus(days, ChronoUnit.DAYS);
    }

    public static Company company(String name, String... emailDomains) {
        Company company = new Company(name);
        company.setCareersUrl("https://" + name.toLowerCase() + ".example/careers");
        for (String domain : emailDomains) {
            company.addEmailDomain(domain);
        }
        return company;
    }

    public static Application application(Company company, String role, ApplicationStatus status) {
        Application application = new Application(company, role, "Summer 2027");
        application.setStatus(status);
        return application;
    }

    public static Application application(
            Company company, String role, ApplicationStatus status, Instant nextDeadline) {
        Application application = application(company, role, status);
        application.setNextDeadline(nextDeadline);
        return application;
    }

    public static EmailEvidence evidence(String messageId, String subject, String from) {
        return new EmailEvidence(messageId, "thread-" + messageId, subject, from, NOW);
    }

    public static StatusEvent emailEvent(
            ApplicationStatus from, ApplicationStatus to, Instant occurredAt, String messageId) {
        return StatusEvent.fromEmail(
                from,
                to,
                occurredAt,
                evidence(messageId, "Subject " + messageId, "noreply@example.com"),
                "rules:v1",
                0.9,
                "test fixture");
    }
}
