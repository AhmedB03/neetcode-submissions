package com.ahmedb.internship.classify;

import com.ahmedb.internship.ingest.IngestedEmail;

/**
 * Maps an email to what it means for the pipeline.
 *
 * <p>The one seam phase 1 exists to protect. Implementations must be:
 *
 * <ul>
 *   <li><b>Pure.</b> No database access, no writes, no side effects. Everything an implementation
 *       needs arrives in its two arguments.
 *   <li><b>Total.</b> Never throw on odd input. An email with a null subject, a malformed sender or
 *       an empty body is an {@link Classification.Outcome#ABSTAIN}, not an exception.
 *   <li><b>Honest about uncertainty.</b> Prefer abstaining to guessing. An abstention lands in the
 *       review queue where it costs a click; a wrong confident answer corrupts the timeline.
 * </ul>
 *
 * <p>Swapping {@code RuleBasedEmailClassifier} for an LLM-backed one is therefore a bean
 * replacement: persistence, matching and idempotency live entirely on the other side of this
 * interface. {@link CompositeEmailClassifier} lets an LLM handle only what the rules abstain on.
 */
public interface EmailClassifier {

    /**
     * @param email the message to classify; never null
     * @param context tracked companies, for grounding; never null, possibly empty
     * @return a verdict; never null
     */
    Classification classify(IngestedEmail email, ClassificationContext context);

    /**
     * Stable identifier recorded on every event this classifier produces, e.g. {@code "rules:v1"}.
     * Change it when behaviour changes, so old decisions stay attributable to the logic that made
     * them.
     */
    String id();
}
