package com.ahmedb.internship.repository;

import com.ahmedb.internship.domain.Application;
import com.ahmedb.internship.domain.ApplicationStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ApplicationRepository extends JpaRepository<Application, Long> {

    /**
     * Everything, ordered by urgency: the soonest deadline first, applications without one last.
     *
     * <p>{@code nulls last} is spelled out rather than left to the database -- PostgreSQL sorts
     * NULLs last on ASC by default but that is a dialect detail, and an undated application showing
     * up at the top of the list would be actively misleading.
     */
    @EntityGraph(attributePaths = "company")
    @Query(
            """
            select a from Application a
            order by case when a.nextDeadline is null then 1 else 0 end asc,
                     a.nextDeadline asc,
                     a.id asc
            """)
    List<Application> findAllByNextDeadline();

    /** One application with its timeline and company already loaded, to avoid an N+1 on the detail view. */
    @EntityGraph(attributePaths = {"company", "events"})
    @Query("select a from Application a where a.id = :id")
    Optional<Application> findByIdWithEvents(@Param("id") Long id);

    /**
     * One application with its company loaded.
     *
     * <p>{@code open-in-view} is off, so a lazy company proxy would fail the moment a controller
     * serialises the response. Anything that returns an application to the API layer reads through
     * here.
     */
    @EntityGraph(attributePaths = "company")
    @Query("select a from Application a where a.id = :id")
    Optional<Application> findByIdWithCompany(@Param("id") Long id);

    @EntityGraph(attributePaths = "company")
    List<Application> findByCompanyId(Long companyId);

    Optional<Application> findByCompanyIdAndRoleTitleIgnoreCaseAndCycleIgnoreCase(
            Long companyId, String roleTitle, String cycle);

    /** Open applications for a company, newest activity first -- the candidate set for matching an email. */
    @EntityGraph(attributePaths = "company")
    @Query(
            """
            select a from Application a
            where a.company.id = :companyId and a.status not in :terminalStatuses
            order by coalesce(a.lastEventAt, a.createdAt) desc
            """)
    List<Application> findOpenByCompanyId(
            @Param("companyId") Long companyId,
            @Param("terminalStatuses") List<ApplicationStatus> terminalStatuses);

    /**
     * Applications with a deadline inside the digest window.
     *
     * <p>Terminal applications are excluded: an offer or a rejection has no deadline left to hit.
     */
    @EntityGraph(attributePaths = "company")
    @Query(
            """
            select a from Application a
            where a.nextDeadline is not null
              and a.nextDeadline >= :from
              and a.nextDeadline < :until
              and a.status not in :terminalStatuses
            order by a.nextDeadline asc, a.id asc
            """)
    List<Application> findWithDeadlineBetween(
            @Param("from") Instant from,
            @Param("until") Instant until,
            @Param("terminalStatuses") List<ApplicationStatus> terminalStatuses);

    /**
     * Candidates for ghosting: no activity since {@code cutoff} and in a status that can go stale.
     *
     * <p>Ghosting is decided in {@code GhostPolicy}; this only narrows the rows it has to consider.
     * {@code coalesce} covers applications that have never had an event, where creation time is the
     * clock's start.
     */
    @EntityGraph(attributePaths = "company")
    @Query(
            """
            select a from Application a
            where a.status in :ghostableStatuses
              and coalesce(a.lastEventAt, a.createdAt) < :cutoff
            order by coalesce(a.lastEventAt, a.createdAt) asc, a.id asc
            """)
    List<Application> findStaleCandidates(
            @Param("cutoff") Instant cutoff,
            @Param("ghostableStatuses") List<ApplicationStatus> ghostableStatuses);
}
