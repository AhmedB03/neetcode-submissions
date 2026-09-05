package com.ahmedb.internship.repository;

import static com.ahmedb.internship.TestFixtures.application;
import static com.ahmedb.internship.TestFixtures.company;
import static com.ahmedb.internship.TestFixtures.evidence;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ahmedb.internship.domain.Application;
import com.ahmedb.internship.domain.ApplicationStatus;
import com.ahmedb.internship.domain.Company;
import com.ahmedb.internship.domain.UnmatchedEmail;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class UnmatchedEmailRepositoryTest extends RepositoryTestBase {

    @Autowired private UnmatchedEmailRepository unmatched;
    @Autowired private ApplicationRepository applications;
    @Autowired private CompanyRepository companies;

    private UnmatchedEmail pending(String messageId, String companyHint) {
        return new UnmatchedEmail(
                evidence(messageId, "Your application to " + companyHint, "careers@" + companyHint + ".example"),
                ApplicationStatus.APPLIED,
                companyHint,
                "SWE Intern",
                0.72,
                "sender domain not recognised",
                "rules:v1");
    }

    @Test
    @DisplayName("the review queue shows pending entries only")
    void findByResolution_returnsPendingOnly() {
        unmatched.save(pending("m1", "acme"));
        UnmatchedEmail dismissed = pending("m2", "spammy");
        dismissed.dismiss();
        unmatched.save(dismissed);

        assertThat(unmatched.findByResolutionOrderByCreatedAtDesc(UnmatchedEmail.Resolution.PENDING))
                .extracting(UnmatchedEmail::getCompanyHint)
                .containsExactly("acme");
        assertThat(unmatched.findByResolutionOrderByCreatedAtDesc(UnmatchedEmail.Resolution.DISMISSED))
                .hasSize(1);
    }

    @Test
    @DisplayName("linking an entry records which application it went to")
    void linkTo_resolvesEntry() {
        Company stripe = companies.save(company("Stripe", "stripe.com"));
        Application target = applications.save(application(stripe, "SWE Intern", ApplicationStatus.APPLIED));
        UnmatchedEmail entry = unmatched.save(pending("m1", "stripe"));

        entry.linkTo(target);
        unmatched.saveAndFlush(entry);

        UnmatchedEmail reloaded = unmatched.findById(entry.getId()).orElseThrow();
        assertThat(reloaded.getResolution()).isEqualTo(UnmatchedEmail.Resolution.LINKED);
        assertThat(reloaded.getLinkedApplication().getId()).isEqualTo(target.getId());
        assertThat(reloaded.getResolvedAt()).isNotNull();
    }

    @Test
    @DisplayName("the same message cannot be queued twice")
    void messageIdIsUnique() {
        unmatched.saveAndFlush(pending("dup", "acme"));

        assertThatThrownBy(() -> unmatched.saveAndFlush(pending("dup", "acme")))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test
    void findByEvidenceMessageId() {
        unmatched.save(pending("m1", "acme"));

        assertThat(unmatched.findByEvidenceMessageId("m1")).isPresent();
        assertThat(unmatched.findByEvidenceMessageId("nope")).isEmpty();
    }
}
