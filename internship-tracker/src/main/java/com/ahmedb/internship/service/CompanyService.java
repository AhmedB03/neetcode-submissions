package com.ahmedb.internship.service;

import com.ahmedb.internship.domain.Company;
import com.ahmedb.internship.repository.CompanyRepository;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CompanyService {

    private final CompanyRepository companies;

    public CompanyService(CompanyRepository companies) {
        this.companies = companies;
    }

    @Transactional(readOnly = true)
    public List<Company> findAll() {
        return companies.findAllWithEmailDomains();
    }

    @Transactional
    public Company create(String name, String careersUrl, String notes, Set<String> emailDomains) {
        Company company = new Company(name);
        company.setCareersUrl(careersUrl);
        company.setNotes(notes);
        if (emailDomains != null) {
            emailDomains.forEach(company::addEmailDomain);
        }
        return companies.save(company);
    }
}
