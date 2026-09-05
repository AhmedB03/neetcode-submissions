package com.ahmedb.internship.repository;

import static com.ahmedb.internship.TestFixtures.application;
import static com.ahmedb.internship.TestFixtures.company;
import static org.assertj.core.api.Assertions.assertThat;

import com.ahmedb.internship.domain.Application;
import com.ahmedb.internship.domain.ApplicationStatus;
import com.ahmedb.internship.domain.Company;
import com.ahmedb.internship.domain.Listing;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class ListingRepositoryTest extends RepositoryTestBase {

    @Autowired private ListingRepository listings;
    @Autowired private ApplicationRepository applications;
    @Autowired private CompanyRepository companies;

    @Test
    @DisplayName("unmatched postings are the ones with no application attached")
    void findByMatchedApplicationIsNull() {
        Company stripe = companies.save(company("Stripe", "stripe.com"));
        Application applied = applications.save(application(stripe, "SWE Intern", ApplicationStatus.APPLIED));

        Listing matched = new Listing(stripe, "SWE Intern");
        matched.setSourceUrl("https://stripe.example/jobs/1");
        matched.setMatchedApplication(applied);
        listings.save(matched);

        Listing loose = new Listing(stripe, "Infra Intern");
        loose.setSourceUrl("https://stripe.example/jobs/2");
        loose.setLocation("Remote");
        loose.setPostedDate(LocalDate.of(2026, 9, 1));
        listings.save(loose);

        assertThat(listings.findByMatchedApplicationIsNull())
                .extracting(Listing::getTitle)
                .containsExactly("Infra Intern");
        assertThat(matched.isMatched()).isTrue();
        assertThat(loose.isMatched()).isFalse();
    }

    @Test
    void findBySourceUrlAndCompany() {
        Company stripe = companies.save(company("Stripe", "stripe.com"));
        Listing listing = new Listing(stripe, "SWE Intern");
        listing.setSourceUrl("https://stripe.example/jobs/1");
        listings.save(listing);

        assertThat(listings.findBySourceUrl("https://stripe.example/jobs/1")).isPresent();
        assertThat(listings.findByCompanyId(stripe.getId())).hasSize(1);
    }
}
