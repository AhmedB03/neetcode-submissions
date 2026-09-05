package com.ahmedb.internship.repository;

import static com.ahmedb.internship.TestFixtures.NOW;
import static org.assertj.core.api.Assertions.assertThat;

import com.ahmedb.internship.domain.ProcessedMessage;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class ProcessedMessageRepositoryTest extends RepositoryTestBase {

    @Autowired private ProcessedMessageRepository processed;

    @Test
    @DisplayName("a whole page of message ids is filtered in one round trip")
    void findExistingMessageIds_filtersInBulk() {
        processed.save(
                new ProcessedMessage("m1", ProcessedMessage.Outcome.TRANSITION_RECORDED, "rules:v1", NOW));
        processed.save(new ProcessedMessage("m2", ProcessedMessage.Outcome.IGNORED, "rules:v1", NOW));

        List<String> existing = processed.findExistingMessageIds(List.of("m1", "m2", "m3", "m4"));

        assertThat(existing).containsExactlyInAnyOrder("m1", "m2");
    }

    @Test
    @DisplayName("messages that produced no event are still recorded, so polling stays idempotent")
    void ignoredAndAbstainedAreRecorded() {
        processed.save(new ProcessedMessage("ignored", ProcessedMessage.Outcome.IGNORED, "rules:v1", NOW));
        processed.save(
                new ProcessedMessage("abstained", ProcessedMessage.Outcome.ABSTAINED, "rules:v1", NOW));
        processed.save(
                new ProcessedMessage("queued", ProcessedMessage.Outcome.QUEUED_FOR_REVIEW, "rules:v1", NOW));

        assertThat(processed.findExistingMessageIds(List.of("ignored", "abstained", "queued")))
                .hasSize(3);
        assertThat(processed.findById("ignored"))
                .get()
                .extracting(ProcessedMessage::getOutcome)
                .isEqualTo(ProcessedMessage.Outcome.IGNORED);
    }
}
