package com.ahmedb.internship.repository;

import com.ahmedb.internship.domain.ProcessedMessage;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProcessedMessageRepository extends JpaRepository<ProcessedMessage, String> {

    /** Bulk existence check so a poll filters an entire page of message ids in one round trip. */
    @Query("select p.messageId from ProcessedMessage p where p.messageId in :messageIds")
    List<String> findExistingMessageIds(@Param("messageIds") Collection<String> messageIds);

    /**
     * Receipt time of the newest message already handled -- the watermark a poll resumes from.
     * Empty on a first run, which is what triggers the full lookback window.
     */
    @Query("select max(p.messageReceivedAt) from ProcessedMessage p")
    Optional<Instant> findLatestMessageReceivedAt();
}
