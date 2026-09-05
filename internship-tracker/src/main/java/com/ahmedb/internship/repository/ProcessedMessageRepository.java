package com.ahmedb.internship.repository;

import com.ahmedb.internship.domain.ProcessedMessage;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProcessedMessageRepository extends JpaRepository<ProcessedMessage, String> {

    /** Bulk existence check so a poll filters an entire page of message ids in one round trip. */
    @Query("select p.messageId from ProcessedMessage p where p.messageId in :messageIds")
    List<String> findExistingMessageIds(@Param("messageIds") Collection<String> messageIds);
}
