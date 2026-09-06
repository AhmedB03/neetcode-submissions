package com.ahmedb.internship.demo;

import com.ahmedb.internship.ingest.IngestedEmail;
import com.ahmedb.internship.ingest.MailSource;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * A scripted mailbox for the demo profile, standing in for Gmail.
 *
 * <p>Every message is dated relative to now, so the demo tells the same story whenever it is run: a
 * full progression, a rejection, an application that goes quiet, a job alert that gets ignored, an
 * email for a company that was never applied to, and a stale acknowledgement arriving out of order.
 */
public class FixtureMailSource implements MailSource {

    private final Clock clock;

    public FixtureMailSource(Clock clock) {
        this.clock = clock;
    }

    @Override
    public String describe() {
        return "demo-fixture-mailbox";
    }

    @Override
    public List<IngestedEmail> fetchSince(Instant since, int maxMessages) {
        return script().stream()
                .filter(email -> !email.receivedAt().isBefore(since))
                // Newest first, the order a real mail API returns -- the pipeline re-sorts.
                .sorted(Comparator.comparing(IngestedEmail::receivedAt).reversed())
                .limit(maxMessages)
                .toList();
    }

    private Instant daysAgo(int days) {
        return clock.instant().minus(days, ChronoUnit.DAYS);
    }

    private List<IngestedEmail> script() {
        List<IngestedEmail> mail = new ArrayList<>();
        int id = 0;

        // --- Stripe: the full progression, in order -----------------------------------------
        mail.add(email(++id, daysAgo(62), "Thank you for applying to Stripe",
                "no-reply@stripe.com", "Stripe Recruiting",
                "Thank you for applying to the Software Engineer Intern role. We have received your application."));
        mail.add(email(++id, daysAgo(55), "Your online assessment for Stripe",
                "no-reply@stripe.com", "Stripe Recruiting",
                "Please complete the online assessment within 5 days."));
        mail.add(email(++id, daysAgo(53), "We have received your assessment",
                "no-reply@stripe.com", "Stripe Recruiting",
                "Thank you for completing the technical assessment."));
        mail.add(email(++id, daysAgo(21), "Interview invitation - Stripe",
                "recruiting@stripe.com", "Stripe Recruiting",
                "We would like to schedule an interview with you next week."));
        mail.add(email(++id, daysAgo(4), "Final round interview at Stripe",
                "recruiting@stripe.com", "Stripe Recruiting",
                "Congratulations on reaching the final round."));

        // A duplicate acknowledgement arriving late. It is recorded, but must not drag Stripe
        // back from FINAL_ROUND to APPLIED.
        mail.add(email(++id, daysAgo(1), "Thank you for applying to Stripe",
                "no-reply@stripe.com", "Stripe Recruiting",
                "This is a copy of your application confirmation."));

        // --- Datadog: applied, then rejected ------------------------------------------------
        mail.add(email(++id, daysAgo(40), "Thank you for applying",
                "careers@datadoghq.com", "Datadog Careers",
                "We have received your application for the Backend Engineer Intern role."));
        mail.add(email(++id, daysAgo(9), "Update on your Datadog application",
                "careers@datadoghq.com", "Datadog Careers",
                "Thank you for your interest. Unfortunately, we have decided to move forward "
                        + "with other candidates."));

        // --- Jane Street: applied, then a superday ------------------------------------------
        mail.add(email(++id, daysAgo(35), "Thank you for applying to Jane Street",
                "recruiting@janestreet.com", "Jane Street Recruiting",
                "We have received your application."));
        mail.add(email(++id, daysAgo(2), "Superday invitation",
                "recruiting@janestreet.com", "Jane Street Recruiting",
                "We would like to invite you to our final round superday."));

        // --- Ramp: applied, then silence. This is what ghosting looks like. -----------------
        mail.add(email(++id, daysAgo(71), "Thank you for applying to Ramp",
                "no-reply@ramp.com", "Ramp Recruiting",
                "We have received your application for the Product Engineer Intern role."));

        // --- Noise: a job alert, which must be ignored rather than classified ---------------
        mail.add(email(++id, daysAgo(3), "Job alert: 12 new software engineering internships",
                "jobs-noreply@linkedin.com", "LinkedIn Job Alerts",
                "Jobs you may be interested in. Apply now!"));

        // --- A real transition for a company that was never applied to ----------------------
        // Goes to the review queue rather than inventing a company and an application.
        mail.add(email(++id, daysAgo(5), "Your application to Figma",
                "no-reply@greenhouse.io", "Figma Recruiting",
                "Thank you for applying to Figma. We would like to schedule an interview."));

        // --- Ordinary mail, which the classifier should decline to interpret ----------------
        mail.add(email(++id, daysAgo(6), "Lunch on Friday?",
                "friend@gmail.com", "Sam",
                "Are you free around noon?"));

        return mail;
    }

    private IngestedEmail email(
            int id, Instant receivedAt, String subject, String from, String displayName, String snippet) {
        String messageId = "demo-%03d".formatted(id);
        return new IngestedEmail(
                messageId, "demo-thread-" + id, subject, from, displayName, receivedAt, snippet);
    }
}
