package com.ahmedb.internship.classify;

import com.ahmedb.internship.classify.rules.ClassificationRule;
import com.ahmedb.internship.classify.rules.DefaultRuleSet;
import com.ahmedb.internship.classify.rules.HintExtractor;
import com.ahmedb.internship.classify.rules.SenderDomains;
import com.ahmedb.internship.ingest.IngestedEmail;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Phase 1 classifier: ordered patterns over sender domain and subject.
 *
 * <p>Cheap, deterministic and fully testable offline. It is also the baseline any LLM classifier has
 * to beat -- swap it by replacing this bean, or leave it in front of one via {@link
 * CompositeEmailClassifier} so the model only sees what the rules could not decide.
 */
@Component
public class RuleBasedEmailClassifier implements EmailClassifier {

    /** Bump when rule behaviour changes, so old events stay attributable to the logic that made them. */
    public static final String ID = "rules:v1";

    private static final double ATS_SENDER_BONUS = 0.05;
    private static final double MAX_CONFIDENCE = 0.98;

    private final List<ClassificationRule> rules;

    public RuleBasedEmailClassifier() {
        this(DefaultRuleSet.rules());
    }

    /** Test seam for exercising a rule in isolation. */
    public RuleBasedEmailClassifier(List<ClassificationRule> rules) {
        this.rules = List.copyOf(rules);
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public Classification classify(IngestedEmail email, ClassificationContext context) {
        if (email == null) {
            return Classification.abstain("no email", ID);
        }
        ClassificationContext safeContext = context == null ? ClassificationContext.empty() : context;

        if (email.subjectOrEmpty().isBlank() && (email.snippet() == null || email.snippet().isBlank())) {
            return Classification.abstain("no subject or preview text to match on", ID);
        }

        for (ClassificationRule rule : rules) {
            Optional<ClassificationRule.RuleMatch> match = rule.evaluate(email, safeContext);
            if (match.isEmpty()) {
                continue;
            }
            ClassificationRule.RuleMatch hit = match.get();

            if (hit.outcome() == Classification.Outcome.IGNORE) {
                return Classification.ignore(hit.reason(), ID);
            }

            return Classification.transition(
                    hit.status(),
                    HintExtractor.companyHint(email, safeContext),
                    HintExtractor.roleHint(email),
                    adjustConfidence(hit.confidence(), email, safeContext),
                    hit.reason(),
                    ID);
        }

        return Classification.abstain("no rule matched", ID);
    }

    /**
     * Nudges confidence by how much the sender corroborates the pattern.
     *
     * <p>Mail from an applicant tracking system, or from a domain already tied to a company you
     * track, is structurally recruiting mail -- the same phrase is more trustworthy there than in a
     * message from an unknown address.
     */
    private double adjustConfidence(
            double base, IngestedEmail email, ClassificationContext context) {
        String domain = email.senderDomain();
        boolean corroborated =
                SenderDomains.isRecruitingSender(domain) || context.byDomain(domain).isPresent();
        return corroborated ? Math.min(MAX_CONFIDENCE, base + ATS_SENDER_BONUS) : base;
    }
}
