package com.ahmedb.internship.classify.rules;

import com.ahmedb.internship.classify.ClassificationContext;
import com.ahmedb.internship.ingest.IngestedEmail;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Drops job-board alerts and recruiting marketing before any transition rule sees them.
 *
 * <p>Runs first because alert mail borrows the vocabulary of real pipeline mail -- "we found jobs
 * for you" sits next to a genuine "we received your application" and would otherwise be classified
 * as one.
 */
public final class JobAlertNoiseRule implements ClassificationRule {

    /** Phrasing that is an alert wherever it comes from. */
    private static final Pattern ALERT_SUBJECT =
            Pattern.compile(
                    "job alert"
                            + "|jobs? you may (be interested in|like)"
                            + "|(new|recommended|top) jobs?"
                            + "|jobs? for you"
                            + "|top job picks"
                            + "|your job (search|alert)"
                            + "|hiring now"
                            + "|\\d+ new (jobs?|openings?)"
                            + "|apply now"
                            + "|we're hiring"
                            + "|job digest",
                    Pattern.CASE_INSENSITIVE);

    /** Weaker phrasing, trusted only when it arrives from a job board. */
    private static final Pattern JOB_BOARD_CHATTER =
            Pattern.compile(
                    "your (weekly|daily) "
                            + "|invitation to apply"
                            + "|companies (hiring|looking)"
                            + "|profile views?"
                            + "|who viewed"
                            + "|newsletter"
                            + "|webinar"
                            + "|career (fair|event)",
                    Pattern.CASE_INSENSITIVE);

    @Override
    public String id() {
        return "job-alert-noise";
    }

    @Override
    public Optional<RuleMatch> evaluate(IngestedEmail email, ClassificationContext context) {
        String text = email.searchableText();

        var alert = ALERT_SUBJECT.matcher(text);
        if (alert.find()) {
            return Optional.of(RuleMatch.ignore("job alert phrasing: \"" + alert.group().trim() + "\""));
        }

        if (SenderDomains.isJobBoard(email.senderDomain())) {
            var chatter = JOB_BOARD_CHATTER.matcher(text);
            if (chatter.find()) {
                return Optional.of(
                        RuleMatch.ignore(
                                "job board " + email.senderDomain() + ": \"" + chatter.group().trim() + "\""));
            }
        }

        return Optional.empty();
    }
}
