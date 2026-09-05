package com.ahmedb.internship.repository;

import com.ahmedb.internship.domain.UnmatchedEmail;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UnmatchedEmailRepository extends JpaRepository<UnmatchedEmail, Long> {

    List<UnmatchedEmail> findByResolutionOrderByCreatedAtDesc(UnmatchedEmail.Resolution resolution);

    Optional<UnmatchedEmail> findByEvidenceMessageId(String messageId);
}
