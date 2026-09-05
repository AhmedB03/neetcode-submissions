package com.ahmedb.internship.classify.rules;

import com.ahmedb.internship.classify.ClassificationContext;
import com.ahmedb.internship.domain.ApplicationStatus;
import com.ahmedb.internship.ingest.IngestedEmail;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Fires a status transition when any of its patterns appears in the subject or preview.
 *
 * <p>Most rules are just a status plus a phrase list, so they are all instances of this rather than
 * a class each. The interesting part is the ordering in {@link DefaultRuleSet}, not the mechanics.
 */
public final class PatternTransitionRule implements ClassificationRule {

    private final String id;
    private final ApplicationStatus status;
    private final double confidence;
    private final List<Pattern> patterns;

    public PatternTransitionRule(
            String id, ApplicationStatus status, double confidence, String... regexes) {
        this.id = id;
        this.status = ApplicationStatus.requireStorable(status);
        this.confidence = confidence;
        this.patterns =
                java.util.Arrays.stream(regexes)
                        .map(r -> Pattern.compile(r, Pattern.CASE_INSENSITIVE))
                        .toList();
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public Optional<RuleMatch> evaluate(IngestedEmail email, ClassificationContext context) {
        String text = email.searchableText();
        for (Pattern pattern : patterns) {
            var matcher = pattern.matcher(text);
            if (matcher.find()) {
                return Optional.of(
                        RuleMatch.transition(
                                status, confidence, id + " matched \"" + matcher.group().trim() + "\""));
            }
        }
        return Optional.empty();
    }
}
