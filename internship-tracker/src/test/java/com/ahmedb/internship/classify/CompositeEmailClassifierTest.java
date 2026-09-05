package com.ahmedb.internship.classify;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ahmedb.internship.domain.ApplicationStatus;
import com.ahmedb.internship.ingest.IngestedEmail;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CompositeEmailClassifierTest {

    private static final IngestedEmail EMAIL =
            new IngestedEmail(
                    "m1", "t1", "Some subject", "a@example.com", null, Instant.parse("2026-09-05T09:00:00Z"), "");

    /** Stands in for the LLM classifier this seam exists to accommodate. */
    private record StubClassifier(String id, Classification result, AtomicInteger calls)
            implements EmailClassifier {

        static StubClassifier answering(String id, Classification result) {
            return new StubClassifier(id, result, new AtomicInteger());
        }

        @Override
        public Classification classify(IngestedEmail email, ClassificationContext context) {
            calls.incrementAndGet();
            return result;
        }
    }

    @Test
    @DisplayName("the first classifier to commit wins and later ones are never called")
    void firstCommitmentWins() {
        StubClassifier rules =
                StubClassifier.answering(
                        "rules:v1",
                        Classification.transition(
                                ApplicationStatus.APPLIED, "Stripe", null, 0.9, "matched", "rules:v1"));
        StubClassifier llm = StubClassifier.answering("llm:test", Classification.abstain("unused", "llm:test"));

        Classification result =
                new CompositeEmailClassifier(List.of(rules, llm)).classify(EMAIL, ClassificationContext.empty());

        assertThat(result.newStatus()).isEqualTo(ApplicationStatus.APPLIED);
        assertThat(result.classifierId()).isEqualTo("rules:v1");
        assertThat(rules.calls()).hasValue(1);
        assertThat(llm.calls()).hasValue(0);
    }

    @Test
    @DisplayName("an abstention falls through to the next classifier")
    void abstentionFallsThrough() {
        StubClassifier rules = StubClassifier.answering("rules:v1", Classification.abstain("no rule", "rules:v1"));
        StubClassifier llm =
                StubClassifier.answering(
                        "llm:test",
                        Classification.transition(
                                ApplicationStatus.INTERVIEW, "Stripe", null, 0.8, "model call", "llm:test"));

        Classification result =
                new CompositeEmailClassifier(List.of(rules, llm)).classify(EMAIL, ClassificationContext.empty());

        assertThat(result.newStatus()).isEqualTo(ApplicationStatus.INTERVIEW);
        assertThat(result.classifierId()).isEqualTo("llm:test");
        assertThat(llm.calls()).hasValue(1);
    }

    @Test
    @DisplayName("an ignore is a commitment, so nothing downstream reconsiders it")
    void ignoreStopsTheChain() {
        StubClassifier rules = StubClassifier.answering("rules:v1", Classification.ignore("job alert", "rules:v1"));
        StubClassifier llm = StubClassifier.answering("llm:test", Classification.abstain("unused", "llm:test"));

        Classification result =
                new CompositeEmailClassifier(List.of(rules, llm)).classify(EMAIL, ClassificationContext.empty());

        assertThat(result.outcome()).isEqualTo(Classification.Outcome.IGNORE);
        assertThat(llm.calls()).hasValue(0);
    }

    @Test
    @DisplayName("a classifier that throws degrades to the next instead of failing the poll")
    void throwingClassifierIsSkipped() {
        EmailClassifier exploding =
                new EmailClassifier() {
                    @Override
                    public String id() {
                        return "llm:flaky";
                    }

                    @Override
                    public Classification classify(IngestedEmail email, ClassificationContext context) {
                        throw new IllegalStateException("model call timed out");
                    }
                };
        StubClassifier fallback =
                StubClassifier.answering(
                        "rules:v1",
                        Classification.transition(
                                ApplicationStatus.APPLIED, "Stripe", null, 0.9, "matched", "rules:v1"));

        Classification result =
                new CompositeEmailClassifier(List.of(exploding, fallback))
                        .classify(EMAIL, ClassificationContext.empty());

        assertThat(result.newStatus()).isEqualTo(ApplicationStatus.APPLIED);
    }

    @Test
    @DisplayName("when everything abstains the composite abstains too")
    void allAbstaining() {
        Classification result =
                new CompositeEmailClassifier(
                                List.of(
                                        StubClassifier.answering("a", Classification.abstain("nope", "a")),
                                        StubClassifier.answering("b", Classification.abstain("also nope", "b"))))
                        .classify(EMAIL, ClassificationContext.empty());

        assertThat(result.outcome()).isEqualTo(Classification.Outcome.ABSTAIN);
    }

    @Test
    @DisplayName("when every classifier fails the composite still returns a verdict")
    void allFailing() {
        EmailClassifier exploding =
                new EmailClassifier() {
                    @Override
                    public String id() {
                        return "broken";
                    }

                    @Override
                    public Classification classify(IngestedEmail email, ClassificationContext context) {
                        throw new IllegalStateException("boom");
                    }
                };

        Classification result =
                new CompositeEmailClassifier(List.of(exploding)).classify(EMAIL, ClassificationContext.empty());

        assertThat(result.outcome()).isEqualTo(Classification.Outcome.ABSTAIN);
        assertThat(result.reason()).contains("abstained or failed");
    }

    @Test
    void requiresAtLeastOneDelegate() {
        assertThatThrownBy(() -> new CompositeEmailClassifier(List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CompositeEmailClassifier(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void idReportsTheChain() {
        assertThat(
                        new CompositeEmailClassifier(
                                        List.of(
                                                StubClassifier.answering("rules:v1", Classification.abstain("x", "rules:v1")),
                                                StubClassifier.answering("llm:opus", Classification.abstain("y", "llm:opus"))))
                                .id())
                .isEqualTo("rules:v1>llm:opus");
    }
}
