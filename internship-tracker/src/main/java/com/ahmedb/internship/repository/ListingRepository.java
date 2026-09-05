package com.ahmedb.internship.repository;

import com.ahmedb.internship.domain.Listing;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ListingRepository extends JpaRepository<Listing, Long> {

    Optional<Listing> findBySourceUrl(String sourceUrl);

    List<Listing> findByCompanyId(Long companyId);

    /** Postings not yet tied to an application. */
    List<Listing> findByMatchedApplicationIsNull();
}
