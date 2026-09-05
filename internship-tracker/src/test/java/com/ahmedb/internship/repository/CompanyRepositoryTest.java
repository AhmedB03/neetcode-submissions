package com.ahmedb.internship.repository;

import static com.ahmedb.internship.TestFixtures.company;
import static org.assertj.core.api.Assertions.assertThat;

import com.ahmedb.internship.domain.Company;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class CompanyRepositoryTest extends RepositoryTestBase {

    @Autowired private CompanyRepository companies;

    @Test
    @DisplayName("resolves a sender domain back to its company, whatever the casing")
    void findByEmailDomain_isCaseInsensitive() {
        companies.save(company("Stripe", "stripe.com", "greenhouse.io"));

        assertThat(companies.findByEmailDomain("stripe.com")).map(Company::getName).contains("Stripe");
        assertThat(companies.findByEmailDomain("STRIPE.COM")).map(Company::getName).contains("Stripe");
        assertThat(companies.findByEmailDomain("greenhouse.io")).map(Company::getName).contains("Stripe");
        assertThat(companies.findByEmailDomain("unknown.example")).isEmpty();
    }

    @Test
    @DisplayName("domains are normalised to lowercase on the way in")
    void addEmailDomain_normalises() {
        Company company = new Company("Datadog");
        company.addEmailDomain("  DataDogHQ.com  ");
        company.addEmailDomain("");
        company.addEmailDomain(null);

        assertThat(companies.save(company).getEmailDomains()).containsExactly("datadoghq.com");
    }

    @Test
    void findByNameIgnoreCase() {
        companies.save(company("Jane Street", "janestreet.com"));

        assertThat(companies.findByNameIgnoreCase("jane street")).isPresent();
        assertThat(companies.findByNameIgnoreCase("JANE STREET")).isPresent();
    }
}
