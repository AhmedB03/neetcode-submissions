package com.ahmedb.internship.service;

import com.ahmedb.internship.classify.Classification;
import com.ahmedb.internship.domain.Application;
import com.ahmedb.internship.domain.ApplicationStatus;
import com.ahmedb.internship.ingest.IngestedEmail;
import com.ahmedb.internship.repository.ApplicationRepository;
import com.ahmedb.internship.repository.CompanyRepository;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Works out which application an email is about.
 *
 * <p>Kept out of the classifier deliberately: the classifier says what an email <em>means</em>, this
 * says what it is <em>about</em>. Splitting them means an LLM can be swapped in for the first
 * without inheriting database access, and matching stays deterministic and cheap either way.
 *
 * <p>The bias throughout is to decline rather than guess. An unmatched email costs one click in the
 * review queue; a wrongly matched one writes a false event into a real application's history.
 */
@Service
public class ApplicationMatcher {

    private static final Logger log = LoggerFactory.getLogger(ApplicationMatcher.class);

    private static final List<ApplicationStatus> TERMINAL =
            List.of(ApplicationStatus.OFFER, ApplicationStatus.REJECTED);

    private final CompanyRepository companies;
    private final ApplicationRepository applications;

    public ApplicationMatcher(CompanyRepository companies, ApplicationRepository applications) {
        this.companies = companies;
        this.applications = applications;
    }

    /**
     * @return the application this email belongs to, or empty if it cannot be established
     */
    @Transactional(readOnly = true)
    public Optional<Application> match(IngestedEmail email, Classification classification) {
        return resolveCompany(email, classification).flatMap(companyId -> pickApplication(companyId, classification));
    }

    /** Sender domain first -- it is unforgeable in practice -- then the classifier's company hint. */
    private Optional<Long> resolveCompany(IngestedEmail email, Classification classification) {
        Optional<Long> byDomain = companies.findByEmailDomain(email.senderDomain()).map(c -> c.getId());
        if (byDomain.isPresent()) {
            return byDomain;
        }
        String hint = classification.companyHint();
        if (hint == null || hint.isBlank()) {
            return Optional.empty();
        }
        return companies.findByNameIgnoreCase(hint.trim()).map(c -> c.getId());
    }

    private Optional<Application> pickApplication(Long companyId, Classification classification) {
        List<Application> open = applications.findOpenByCompanyId(companyId, TERMINAL);

        if (open.isEmpty()) {
            // Either nothing was ever recorded for this company, or every application is settled.
            // Both are cases where writing a new event would be inventing history.
            return Optional.empty();
        }
        if (open.size() == 1) {
            return Optional.of(open.get(0));
        }

        // Several open applications at one company: only a role hint can separate them.
        List<Application> byRole = narrowByRole(open, classification.roleHint());
        if (byRole.size() == 1) {
            return Optional.of(byRole.get(0));
        }

        log.debug(
                "Company {} has {} open applications and the role hint {} does not single one out; "
                        + "queueing for review",
                companyId,
                open.size(),
                classification.roleHint());
        return Optional.empty();
    }

    /** Keeps applications whose title overlaps the hint in either direction. */
    private List<Application> narrowByRole(List<Application> candidates, String roleHint) {
        if (roleHint == null || roleHint.isBlank()) {
            return List.of();
        }
        String hint = normalise(roleHint);
        return candidates.stream()
                .filter(
                        application -> {
                            String title = normalise(application.getRoleTitle());
                            return title.contains(hint) || hint.contains(title);
                        })
                .toList();
    }

    private static String normalise(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }
}
