package com.ahmedb.internship.classify.rules;

import com.ahmedb.internship.classify.ClassificationContext;
import com.ahmedb.internship.ingest.IngestedEmail;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pulls a company name and role title out of an email.
 *
 * <p>These are <em>hints</em>. The service layer decides whether a hint resolves to a real
 * application; a wrong guess here costs a trip through the review queue, not a corrupted timeline.
 */
public final class HintExtractor {

    /** Words that decorate a sender's display name without being part of the company's name. */
    private static final Pattern DISPLAY_NAME_NOISE =
            Pattern.compile(
                    // Longest alternative first: regex alternation is leftmost-first, so listing
                    // "talent" before "talent acquisition" would strip only the first word and
                    // leave "Acquisition" behind.
                    "\\b(talent acquisition|recruiting|recruitment|recruiter|talent|careers?|"
                            + "hiring|jobs?|team|hr|people operations|people ops|people|no[- ]?reply|"
                            + "do[- ]?not[- ]?reply|notifications?|university|campus|"
                            + "early careers?|internships?)\\b",
                    Pattern.CASE_INSENSITIVE);

    private static final Pattern TRAILING_PUNCTUATION = Pattern.compile("^[\\s\\-|,:;.]+|[\\s\\-|,:;.]+$");

    /**
     * Role phrasings, most specific first. Each has exactly one capture group holding the title.
     */
    private static final String ROLE_NOUN =
            "(?:Intern(?:ship)?|Engineer(?:ing)?|Analyst|Scientist|Developer|Manager|Researcher)";

    private static final List<Pattern> ROLE_PATTERNS =
            List.of(
                    Pattern.compile(
                            "applic(?:ation|ant) (?:for|to)(?: the)? (.{3,80}?)(?: at | with |$|[-|,.!?\\n])",
                            Pattern.CASE_INSENSITIVE),
                    Pattern.compile("your (.{3,80}?) applic(?:ation|ant)", Pattern.CASE_INSENSITIVE),
                    Pattern.compile(
                            "(?:role|position|opening|opportunity) (?:of|as|for)(?: the)? (.{3,80}?)(?: at | with |$|[-|,.!?\\n])",
                            Pattern.CASE_INSENSITIVE),
                    // Catch-all: a run of Title Case words leading into one or more role nouns.
                    // Deliberately case-SENSITIVE -- capitalisation is what stops the match from
                    // reaching back across ordinary prose ("Application received for ...") and
                    // dragging it into the title.
                    Pattern.compile(
                            "\\b((?:[A-Z][\\w/&+.-]*\\s+){0,4}" + ROLE_NOUN + "(?:\\s+" + ROLE_NOUN + ")*)\\b"));

    private HintExtractor() {}

    /**
     * Best guess at the company, in descending order of reliability: a tracked sender domain, a
     * tracked name appearing in the text, the sender's display name, then the sender domain itself.
     */
    public static String companyHint(IngestedEmail email, ClassificationContext context) {
        String domain = email.senderDomain();

        Optional<String> tracked = context.byDomain(domain).map(ClassificationContext.KnownCompany::name);
        if (tracked.isPresent()) {
            return tracked.get();
        }

        // An ATS domain identifies the vendor, not the employer, so search the text for a company
        // you already track before falling back to anything domain-derived.
        Optional<String> mentioned =
                context
                        .mentionedIn(email.subjectOrEmpty() + " " + nullToEmpty(email.fromDisplayName()))
                        .or(() -> context.mentionedIn(email.searchableText()))
                        .map(ClassificationContext.KnownCompany::name);
        if (mentioned.isPresent()) {
            return mentioned.get();
        }

        String fromDisplayName = cleanDisplayName(email.fromDisplayName());
        if (!fromDisplayName.isBlank()) {
            return fromDisplayName;
        }

        return companyFromDomain(domain);
    }

    /** Strips recruiting boilerplate off a display name, e.g. "Stripe Recruiting" to "Stripe". */
    static String cleanDisplayName(String displayName) {
        if (displayName == null || displayName.isBlank()) {
            return "";
        }
        String cleaned = DISPLAY_NAME_NOISE.matcher(displayName).replaceAll(" ");
        cleaned = cleaned.replaceAll("\\s+", " ");
        cleaned = TRAILING_PUNCTUATION.matcher(cleaned).replaceAll("");
        // A display name that was only boilerplate ("Careers Team") tells us nothing.
        return cleaned.length() < 2 ? "" : cleaned;
    }

    /** "no-reply@stripe.com" to "Stripe". Empty for ATS vendors and consumer mail providers. */
    static String companyFromDomain(String domain) {
        if (domain == null
                || domain.isBlank()
                || SenderDomains.isApplicantTrackingSystem(domain)
                || SenderDomains.isJobBoard(domain)
                || SenderDomains.isGenericMailProvider(domain)) {
            return "";
        }
        String[] labels = domain.split("\\.");
        if (labels.length < 2) {
            return "";
        }
        // Second-level label, skipping common mail subdomains.
        String label = labels[labels.length - 2];
        if (label.isBlank()) {
            return "";
        }
        return Character.toUpperCase(label.charAt(0)) + label.substring(1).toLowerCase(Locale.ROOT);
    }

    /** Best guess at the role title from the subject line. Null when nothing convincing appears. */
    public static String roleHint(IngestedEmail email) {
        String subject = email.subjectOrEmpty();
        for (Pattern pattern : ROLE_PATTERNS) {
            Matcher matcher = pattern.matcher(subject);
            if (matcher.find()) {
                String candidate = TRAILING_PUNCTUATION.matcher(matcher.group(1)).replaceAll("").trim();
                if (candidate.length() >= 3 && candidate.length() <= 120) {
                    return candidate;
                }
            }
        }
        return null;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
