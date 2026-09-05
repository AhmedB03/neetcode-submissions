package com.ahmedb.internship.classify.rules;

import com.ahmedb.internship.domain.ApplicationStatus;
import java.util.List;

/**
 * The phase 1 rule set, in precedence order. First match wins.
 *
 * <p>Ordering is the whole design. Recruiting mail layers its vocabulary: a rejection opens with
 * "thank you for applying", an assessment invitation opens with "thanks for your interest", and an
 * offer mentions the interview it followed. Reading later stages first means the most advanced
 * signal in an email is the one that counts.
 *
 * <p>Patterns match against subject plus the provider's preview text, lowercased.
 */
public final class DefaultRuleSet {

    private DefaultRuleSet() {}

    public static List<ClassificationRule> rules() {
        return List.of(
                // 0. Alerts and marketing never reach a transition rule.
                new JobAlertNoiseRule(),

                // 1. Rejection outranks everything: these emails are polite and mention every
                //    earlier stage on their way to saying no.
                new PatternTransitionRule(
                        "rejection",
                        ApplicationStatus.REJECTED,
                        0.92,
                        "we regret to inform",
                        "regret to inform you",
                        "(will )?not (be )?mov(e|ing) forward",
                        "won'?t be mov(e|ing) forward",
                        "not be proceeding",
                        "decided (not )?to (move forward|proceed) with",
                        "mov(e|ing) forward with other candidates",
                        "pursu(e|ing) other candidates",
                        "no longer under consideration",
                        "not selected",
                        "were not (chosen|selected)",
                        "unable to offer you",
                        "unsuccessful on this occasion",
                        "your application was unsuccessful",
                        "we have decided to move forward with other",
                        "not (be )?advancing",
                        "unfortunately,? (we|after|your|at this time)"),

                // 2. Offers.
                new PatternTransitionRule(
                        "offer",
                        ApplicationStatus.OFFER,
                        0.93,
                        "pleased to offer",
                        "delighted to offer",
                        "excited to offer",
                        "happy to offer",
                        "offer of (employment|internship)",
                        "offer letter",
                        "extend(ing)? (you )?an offer",
                        "we would like to offer you",
                        "your offer (from|with|details)"),

                // 3. Final round, before the generic interview rule -- a superday invitation is
                //    still an interview invitation and would match the weaker rule.
                new PatternTransitionRule(
                        "final-round",
                        ApplicationStatus.FINAL_ROUND,
                        0.88,
                        "final round",
                        "final[- ]stage",
                        "final interview",
                        "super ?day",
                        "on-?site interview",
                        "onsite (loop|round)",
                        "last round"),

                // 4. Interviews.
                new PatternTransitionRule(
                        "interview",
                        ApplicationStatus.INTERVIEW,
                        0.87,
                        "interview invitation",
                        "invitation to interview",
                        "invit(e|ing|ed) you to interview",
                        "schedul(e|ing) (your|an|a) interview",
                        "interview (request|scheduling|confirmation)",
                        "phone screen",
                        "technical (interview|screen)",
                        "recruiter (call|chat|screen)",
                        "would like to (speak|chat|meet|connect) with you",
                        "set up (a|an) (call|chat|interview)",
                        "book (a|your) interview",
                        "next steps? in (your|the) (application|interview|process)"),

                // 5. Assessment completed, before the invitation rule -- a confirmation repeats the
                //    words "online assessment" it is confirming.
                new PatternTransitionRule(
                        "assessment-submitted",
                        ApplicationStatus.OA_SUBMITTED,
                        0.86,
                        "(have )?received your (online )?(assessment|submission|challenge)",
                        "thank you for (completing|submitting)",
                        "(assessment|challenge|submission) (has been )?(received|submitted|completed)",
                        "successfully submitted"),

                // 6. Assessment invitations.
                new PatternTransitionRule(
                        "assessment-invited",
                        ApplicationStatus.OA_PENDING,
                        0.85,
                        "online assessment",
                        "coding (assessment|challenge|test)",
                        "technical assessment",
                        "take[- ]home (assignment|assessment|challenge|exercise)",
                        "complete (the|your|this) (assessment|challenge|test)",
                        "invitation to complete",
                        "hackerrank",
                        "codesignal",
                        "codility",
                        "hackerearth",
                        "karat interview"),

                // 7. Application acknowledgements -- the weakest signal, so it runs last.
                new PatternTransitionRule(
                        "application-received",
                        ApplicationStatus.APPLIED,
                        0.84,
                        "thank you for applying",
                        "thanks for applying",
                        "(have )?received your application",
                        "your application (has been )?(been )?(received|submitted)",
                        "application (received|submitted|confirmation)",
                        "we('ve| have) got your application",
                        "thank you for your interest in",
                        "appreciate your interest in",
                        "successfully applied"));
    }
}
