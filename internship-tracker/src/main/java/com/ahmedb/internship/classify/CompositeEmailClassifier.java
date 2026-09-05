package com.ahmedb.internship.classify;

import com.ahmedb.internship.ingest.IngestedEmail;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs classifiers in order and takes the first one that commits to an answer.
 *
 * <p>This is how an LLM gets added later without disturbing anything: put the rules first and the
 * model second, and the model is consulted only for mail the rules abstain on -- which keeps cost
 * proportional to the hard cases and keeps the common path deterministic.
 *
 * <pre>{@code
 * new CompositeEmailClassifier(List.of(ruleBased, llmBacked));
 * }</pre>
 *
 * <p>{@link Classification.Outcome#IGNORE} is a commitment, not an abstention: once the rules
 * recognise a job alert, there is nothing for a later classifier to reconsider.
 */
public class CompositeEmailClassifier implements EmailClassifier {

    private static final Logger log = LoggerFactory.getLogger(CompositeEmailClassifier.class);

    private final List<EmailClassifier> delegates;

    public CompositeEmailClassifier(List<EmailClassifier> delegates) {
        if (delegates == null || delegates.isEmpty()) {
            throw new IllegalArgumentException("a composite needs at least one delegate");
        }
        this.delegates = List.copyOf(delegates);
    }

    @Override
    public String id() {
        return delegates.stream().map(EmailClassifier::id).reduce((a, b) -> a + ">" + b).orElseThrow();
    }

    @Override
    public Classification classify(IngestedEmail email, ClassificationContext context) {
        Classification lastAbstention = null;

        for (EmailClassifier delegate : delegates) {
            Classification result;
            try {
                result = delegate.classify(email, context);
            } catch (RuntimeException e) {
                // A classifier is required to be total. If one breaks its contract -- an LLM call
                // timing out, say -- the pipeline degrades to the next classifier rather than
                // failing the whole poll.
                log.warn("classifier {} threw; falling through to the next", delegate.id(), e);
                continue;
            }

            if (result == null) {
                log.warn("classifier {} returned null; falling through to the next", delegate.id());
                continue;
            }
            if (!result.isAbstain()) {
                return result;
            }
            lastAbstention = result;
        }

        return lastAbstention != null
                ? lastAbstention
                : Classification.abstain("every classifier abstained or failed", id());
    }
}
