package com.ahmedb.internship.classify;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Read-only grounding handed to a classifier: the companies you are actually tracking.
 *
 * <p>Without it, "Your application status has been updated" from a generic applicant-tracking
 * domain is unclassifiable. With it, the sender domain or a name in the subject resolves to a real
 * company. This is also the payload a future LLM classifier puts in its prompt.
 *
 * <p>It deliberately carries companies rather than applications. Deciding <em>which</em> application
 * an email belongs to is the service layer's job; the classifier only says what the email means and
 * who it appears to be from.
 */
public record ClassificationContext(List<KnownCompany> knownCompanies) {

    public record KnownCompany(Long companyId, String name, Set<String> emailDomains) {}

    public ClassificationContext {
        knownCompanies = knownCompanies == null ? List.of() : List.copyOf(knownCompanies);
    }

    public static ClassificationContext empty() {
        return new ClassificationContext(List.of());
    }

    /** Exact sender-domain match, e.g. {@code no-reply@stripe.com} to Stripe. */
    public Optional<KnownCompany> byDomain(String domain) {
        if (domain == null || domain.isBlank()) {
            return Optional.empty();
        }
        String needle = domain.trim().toLowerCase(Locale.ROOT);
        return knownCompanies.stream()
                .filter(c -> c.emailDomains().stream().anyMatch(d -> d.equalsIgnoreCase(needle)))
                .findFirst();
    }

    /**
     * A tracked company named in free text.
     *
     * <p>Longest name first, so "Jane Street Capital" is not shadowed by a company called "Jane".
     */
    public Optional<KnownCompany> mentionedIn(String text) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        String haystack = text.toLowerCase(Locale.ROOT);
        return knownCompanies.stream()
                .filter(c -> c.name() != null && !c.name().isBlank())
                .filter(c -> haystack.contains(c.name().toLowerCase(Locale.ROOT)))
                .max((a, b) -> Integer.compare(a.name().length(), b.name().length()));
    }
}
