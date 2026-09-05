package com.ahmedb.internship.classify.rules;

import java.util.Locale;
import java.util.Set;

/** Sender-domain knowledge shared across rules. */
public final class SenderDomains {

    /**
     * Applicant tracking systems. Mail from these is recruiting mail by construction, which raises
     * confidence -- but the domain says nothing about <em>which</em> company, so company extraction
     * has to fall back to the display name or the subject.
     */
    private static final Set<String> ATS_DOMAINS =
            Set.of(
                    "greenhouse.io",
                    "us.greenhouse-mail.io",
                    "greenhouse-mail.io",
                    "lever.co",
                    "hire.lever.co",
                    "myworkday.com",
                    "workday.com",
                    "myworkdayjobs.com",
                    "ashbyhq.com",
                    "smartrecruiters.com",
                    "icims.com",
                    "taleo.net",
                    "jobvite.com",
                    "breezy.hr",
                    "workable.com",
                    "recruitee.com",
                    "teamtailor.com",
                    "successfactors.com",
                    "avature.net",
                    "eightfold.ai",
                    "paradox.ai",
                    "gem.com",
                    "ripplingats.com",
                    "hire.withgoogle.com");

    /** Job boards and aggregators. Their mail is overwhelmingly alerts rather than pipeline events. */
    private static final Set<String> JOB_BOARD_DOMAINS =
            Set.of(
                    "linkedin.com",
                    "e.linkedin.com",
                    "indeed.com",
                    "match.indeed.com",
                    "glassdoor.com",
                    "ziprecruiter.com",
                    "monster.com",
                    "dice.com",
                    "wellfound.com",
                    "angel.co",
                    "builtin.com");

    /** Consumer mail providers, never a company identity. */
    private static final Set<String> GENERIC_MAIL_DOMAINS =
            Set.of(
                    "gmail.com",
                    "googlemail.com",
                    "outlook.com",
                    "hotmail.com",
                    "yahoo.com",
                    "icloud.com",
                    "proton.me",
                    "protonmail.com",
                    "aol.com");

    private SenderDomains() {}

    private static boolean matches(Set<String> domains, String domain) {
        if (domain == null || domain.isBlank()) {
            return false;
        }
        String needle = domain.trim().toLowerCase(Locale.ROOT);
        // Suffix match so subdomains such as mail.greenhouse.io resolve too.
        return domains.stream().anyMatch(d -> needle.equals(d) || needle.endsWith("." + d));
    }

    public static boolean isApplicantTrackingSystem(String domain) {
        return matches(ATS_DOMAINS, domain);
    }

    public static boolean isJobBoard(String domain) {
        return matches(JOB_BOARD_DOMAINS, domain);
    }

    public static boolean isGenericMailProvider(String domain) {
        return matches(GENERIC_MAIL_DOMAINS, domain);
    }

    /** True when the sender is structurally recruiting infrastructure rather than a person. */
    public static boolean isRecruitingSender(String domain) {
        return isApplicantTrackingSystem(domain);
    }
}
