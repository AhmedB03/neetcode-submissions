package com.ahmedb.internship.api;

import com.ahmedb.internship.api.dto.Requests;
import com.ahmedb.internship.domain.Company;
import com.ahmedb.internship.service.CompanyService;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/companies")
public class CompanyController {

    private final CompanyService companies;

    public CompanyController(CompanyService companies) {
        this.companies = companies;
    }

    /** A company as the API reports it. */
    public record CompanyView(
            Long id,
            String name,
            String careersUrl,
            String notes,
            Set<String> emailDomains,
            Instant createdAt) {

        static CompanyView from(Company company) {
            return new CompanyView(
                    company.getId(),
                    company.getName(),
                    company.getCareersUrl(),
                    company.getNotes(),
                    Set.copyOf(company.getEmailDomains()),
                    company.getCreatedAt());
        }
    }

    @GetMapping
    public List<CompanyView> list() {
        return companies.findAll().stream().map(CompanyView::from).toList();
    }

    @PostMapping
    public ResponseEntity<CompanyView> create(@Valid @RequestBody Requests.CreateCompany request) {
        Company created =
                companies.create(
                        request.name(), request.careersUrl(), request.notes(), request.emailDomains());
        return ResponseEntity.created(URI.create("/companies/" + created.getId()))
                .body(CompanyView.from(created));
    }
}
