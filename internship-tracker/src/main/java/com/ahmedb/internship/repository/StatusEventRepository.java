package com.ahmedb.internship.repository;

import com.ahmedb.internship.domain.StatusEvent;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StatusEventRepository extends JpaRepository<StatusEvent, Long> {

    /** The full timeline for one application, oldest first. */
    List<StatusEvent> findByApplicationIdOrderByOccurredAtAscIdAsc(Long applicationId);

    /** Newest event for an application -- the one the head status came from. */
    Optional<StatusEvent> findFirstByApplicationIdOrderByOccurredAtDescIdDesc(Long applicationId);

    boolean existsByEvidenceMessageId(String messageId);
}
