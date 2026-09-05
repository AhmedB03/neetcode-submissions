package com.ahmedb.internship.classify;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ahmedb.internship.domain.ApplicationStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ClassificationTest {

    @Test
    @DisplayName("a transition must carry a storable status")
    void transitionRequiresStatus() {
        assertThatThrownBy(
                        () ->
                                new Classification(
                                        Classification.Outcome.TRANSITION, null, null, null, 0.5, "r", "c"))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(
                        () ->
                                Classification.transition(
                                        ApplicationStatus.GHOSTED, "Stripe", null, 0.5, "r", "c"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("derived");
    }

    @Test
    @DisplayName("a non-transition must not carry a status")
    void nonTransitionRejectsStatus() {
        assertThatThrownBy(
                        () ->
                                new Classification(
                                        Classification.Outcome.IGNORE,
                                        ApplicationStatus.APPLIED,
                                        null,
                                        null,
                                        0.0,
                                        "r",
                                        "c"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void confidenceMustBeAProbability() {
        assertThatThrownBy(
                        () -> Classification.transition(ApplicationStatus.APPLIED, "S", null, 1.5, "r", "c"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                        () -> Classification.transition(ApplicationStatus.APPLIED, "S", null, -0.1, "r", "c"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void withClassifierIdPreservesEverythingElse() {
        Classification original =
                Classification.transition(ApplicationStatus.OFFER, "Stripe", "SWE Intern", 0.9, "why", "rules:v1");

        Classification renamed = original.withClassifierId("llm:opus");

        assertThat(renamed.classifierId()).isEqualTo("llm:opus");
        assertThat(renamed.newStatus()).isEqualTo(ApplicationStatus.OFFER);
        assertThat(renamed.companyHint()).isEqualTo("Stripe");
        assertThat(renamed.roleHint()).isEqualTo("SWE Intern");
        assertThat(renamed.confidence()).isEqualTo(0.9);
        assertThat(renamed.reason()).isEqualTo("why");
    }

    @Test
    void outcomeHelpers() {
        assertThat(Classification.transition(ApplicationStatus.APPLIED, "S", null, 0.9, "r", "c").isTransition())
                .isTrue();
        assertThat(Classification.abstain("r", "c").isAbstain()).isTrue();
        assertThat(Classification.ignore("r", "c").isTransition()).isFalse();
    }
}
