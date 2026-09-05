package com.ahmedb.internship.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class ApplicationStatusTest {

    @Test
    @DisplayName("GHOSTED is derived and can never be written")
    void ghostedIsNotStorable() {
        assertThat(ApplicationStatus.GHOSTED.isDerived()).isTrue();

        assertThatThrownBy(() -> ApplicationStatus.requireStorable(ApplicationStatus.GHOSTED))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("derived");
    }

    @ParameterizedTest
    @EnumSource(
            value = ApplicationStatus.class,
            names = {"GHOSTED"},
            mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("every other status is storable")
    void allOtherStatusesAreStorable(ApplicationStatus status) {
        assertThat(ApplicationStatus.requireStorable(status)).isEqualTo(status);
    }

    @Test
    void requireStorableRejectsNull() {
        assertThatThrownBy(() -> ApplicationStatus.requireStorable(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("only live, started applications can go stale")
    void canGhost() {
        assertThat(ApplicationStatus.APPLIED.canGhost()).isTrue();
        assertThat(ApplicationStatus.OA_PENDING.canGhost()).isTrue();
        assertThat(ApplicationStatus.INTERVIEW.canGhost()).isTrue();
        assertThat(ApplicationStatus.FINAL_ROUND.canGhost()).isTrue();

        // Settled: silence means nothing.
        assertThat(ApplicationStatus.OFFER.canGhost()).isFalse();
        assertThat(ApplicationStatus.REJECTED.canGhost()).isFalse();
        // Never started: silence is expected.
        assertThat(ApplicationStatus.NOT_APPLIED.canGhost()).isFalse();
        assertThat(ApplicationStatus.GHOSTED.canGhost()).isFalse();
    }

    @Test
    @DisplayName("progress moves forward")
    void advancesTo_deeperStatus() {
        assertThat(ApplicationStatus.APPLIED.advancesTo(ApplicationStatus.INTERVIEW)).isTrue();
        assertThat(ApplicationStatus.NOT_APPLIED.advancesTo(ApplicationStatus.APPLIED)).isTrue();
        assertThat(ApplicationStatus.INTERVIEW.advancesTo(ApplicationStatus.FINAL_ROUND)).isTrue();
    }

    @Test
    @DisplayName("a late-arriving email cannot drag an application backwards")
    void advancesTo_rejectsRegression() {
        // Backfilling 90 days delivers mail out of order; a stale "thanks for applying" must not
        // undo an interview that has already been recorded.
        assertThat(ApplicationStatus.INTERVIEW.advancesTo(ApplicationStatus.APPLIED)).isFalse();
        assertThat(ApplicationStatus.FINAL_ROUND.advancesTo(ApplicationStatus.OA_PENDING)).isFalse();
    }

    @Test
    @DisplayName("a rejection lands at any stage, even though it scores no deeper than an offer")
    void advancesTo_allowsTerminalFromAnywhere() {
        assertThat(ApplicationStatus.APPLIED.advancesTo(ApplicationStatus.REJECTED)).isTrue();
        assertThat(ApplicationStatus.FINAL_ROUND.advancesTo(ApplicationStatus.REJECTED)).isTrue();
        assertThat(ApplicationStatus.FINAL_ROUND.advancesTo(ApplicationStatus.OFFER)).isTrue();
        assertThat(ApplicationStatus.OFFER.depth()).isEqualTo(ApplicationStatus.REJECTED.depth());
    }

    @Test
    @DisplayName("nothing moves once an outcome is settled")
    void advancesTo_terminalIsFinal() {
        assertThat(ApplicationStatus.OFFER.advancesTo(ApplicationStatus.REJECTED)).isFalse();
        assertThat(ApplicationStatus.REJECTED.advancesTo(ApplicationStatus.INTERVIEW)).isFalse();
        assertThat(ApplicationStatus.REJECTED.advancesTo(ApplicationStatus.OFFER)).isFalse();
    }

    @Test
    @DisplayName("no-ops and derived targets are not advances")
    void advancesTo_edgeCases() {
        assertThat(ApplicationStatus.APPLIED.advancesTo(ApplicationStatus.APPLIED)).isFalse();
        assertThat(ApplicationStatus.APPLIED.advancesTo(ApplicationStatus.GHOSTED)).isFalse();
        assertThat(ApplicationStatus.APPLIED.advancesTo(null)).isFalse();
    }
}
