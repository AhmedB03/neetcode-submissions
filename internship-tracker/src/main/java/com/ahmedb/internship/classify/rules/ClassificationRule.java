package com.ahmedb.internship.classify.rules;

import com.ahmedb.internship.classify.Classification;
import com.ahmedb.internship.classify.ClassificationContext;
import com.ahmedb.internship.domain.ApplicationStatus;
import com.ahmedb.internship.ingest.IngestedEmail;
import java.util.Optional;

/**
 * One decision an email can trigger. Rules are consulted in order and the first match wins, so
 * ordering encodes precedence -- see {@link DefaultRuleSet}.
 *
 * <p>A rule decides only <em>what an email means</em>. Company and role extraction is shared and
 * happens once in the classifier, so no rule has to repeat it.
 */
public interface ClassificationRule {

    /** Stable name, recorded in the event's reason so a misfire is traceable to its rule. */
    String id();

    Optional<RuleMatch> evaluate(IngestedEmail email, ClassificationContext context);

    /**
     * A rule firing.
     *
     * @param outcome TRANSITION or IGNORE; a rule never abstains, it simply does not match
     * @param status the resulting status, non-null exactly when outcome is TRANSITION
     * @param confidence base confidence before sender-signal adjustment
     * @param reason human-readable justification, surfaced in the timeline
     */
    record RuleMatch(
            Classification.Outcome outcome, ApplicationStatus status, double confidence, String reason) {

        public RuleMatch {
            if (outcome == Classification.Outcome.TRANSITION) {
                ApplicationStatus.requireStorable(status);
            }
        }

        public static RuleMatch transition(ApplicationStatus status, double confidence, String reason) {
            return new RuleMatch(Classification.Outcome.TRANSITION, status, confidence, reason);
        }

        public static RuleMatch ignore(String reason) {
            return new RuleMatch(Classification.Outcome.IGNORE, null, 0.0, reason);
        }
    }
}
