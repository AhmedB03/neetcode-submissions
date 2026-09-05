package com.ahmedb.internship.repository;

import com.ahmedb.internship.domain.Company;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CompanyRepository extends JpaRepository<Company, Long> {

    Optional<Company> findByNameIgnoreCase(String name);

    /** Resolves a sender domain (e.g. "stripe.com") back to the company that owns it. */
    @Query("select c from Company c join c.emailDomains d where lower(d) = lower(:domain)")
    Optional<Company> findByEmailDomain(@Param("domain") String domain);

    /**
     * Every company with its sender domains already loaded.
     *
     * <p>The domains are a lazy element collection, and building the classification context reads
     * them outside any transaction. Fetching them here makes that safe and costs one query instead
     * of one per company.
     */
    @Query("select distinct c from Company c left join fetch c.emailDomains")
    List<Company> findAllWithEmailDomains();
}
