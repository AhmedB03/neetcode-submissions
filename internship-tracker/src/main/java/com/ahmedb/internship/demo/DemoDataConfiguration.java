package com.ahmedb.internship.demo;

import com.ahmedb.internship.domain.Application;
import com.ahmedb.internship.domain.ApplicationStatus;
import com.ahmedb.internship.domain.Company;
import com.ahmedb.internship.domain.Listing;
import com.ahmedb.internship.ingest.MailSource;
import com.ahmedb.internship.repository.ApplicationRepository;
import com.ahmedb.internship.repository.CompanyRepository;
import com.ahmedb.internship.repository.ListingRepository;
import com.ahmedb.internship.service.IngestionPipeline;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Demo mode: a working pipeline with no Gmail credentials and no database setup.
 *
 * <pre>{@code
 * ./gradlew bootRun --args='--spring.profiles.active=demo'
 * }</pre>
 *
 * <p>Seeds four companies and their applications, then replays a scripted mailbox through the real
 * ingestion pipeline -- the same classifier, matcher and persistence the Gmail path uses. Nothing
 * here is a mock of the pipeline; only the mailbox is substituted.
 *
 * <p>Active only under the {@code demo} profile, and it refuses to seed a database that already has
 * companies in it, so pointing demo mode at a real database cannot overwrite anything.
 */
@Configuration(proxyBeanMethods = false)
@Profile("demo")
public class DemoDataConfiguration {

    private static final Logger log = LoggerFactory.getLogger(DemoDataConfiguration.class);
    private static final String CYCLE = "Summer 2027";

    /** The mailbox the pipeline reads in demo mode. */
    @Bean
    public MailSource demoMailSource(Clock clock) {
        return new FixtureMailSource(clock);
    }

    @Bean
    public ApplicationRunner demoSeeder(
            CompanyRepository companies,
            ApplicationRepository applications,
            ListingRepository listings,
            IngestionPipeline pipeline,
            Clock clock) {
        return args -> {
            if (companies.count() > 0) {
                log.info("Demo seed skipped: this database already has companies in it");
                return;
            }

            Company stripe = companies.save(company("Stripe", "https://stripe.com/jobs", "stripe.com"));
            Company datadog =
                    companies.save(company("Datadog", "https://careers.datadoghq.com", "datadoghq.com"));
            Company janeStreet =
                    companies.save(company("Jane Street", "https://janestreet.com/join-jane-street", "janestreet.com"));
            Company ramp = companies.save(company("Ramp", "https://ramp.com/careers", "ramp.com"));

            // Stripe is furthest along and has the nearest deadline.
            applications.save(
                    application(stripe, "Software Engineer Intern", clock, 2, "Prepare for the final round"));
            applications.save(application(datadog, "Backend Engineer Intern", clock, null, null));
            applications.save(
                    application(janeStreet, "Quantitative Trader Intern", clock, 5, "Superday prep"));
            // Ramp has an assessment that is already overdue -- the digest surfaces it separately.
            applications.save(
                    application(ramp, "Product Engineer Intern", clock, -3, "Finish the take-home"));

            Listing listing = new Listing(stripe, "Software Engineer Intern");
            listing.setLocation("Remote (US)");
            listing.setPostedDate(LocalDate.now(clock).minusDays(70));
            listing.setSourceUrl("https://stripe.com/jobs/listing/software-engineer-intern");
            listing.setSource("careers-page");
            listings.save(listing);

            log.info("Demo seed written; replaying the fixture mailbox through the pipeline");
            log.info("Demo ingestion: {}", pipeline.run());
            log.info("Demo ready. Try: curl localhost:8080/digest | jq");
        };
    }

    private static Company company(String name, String careersUrl, String domain) {
        Company company = new Company(name);
        company.setCareersUrl(careersUrl);
        company.addEmailDomain(domain);
        return company;
    }

    /**
     * All applications start at NOT_APPLIED. Their real status comes from replaying the mailbox,
     * which is the point of the demo -- the statuses you see were derived, not seeded.
     */
    private static Application application(
            Company company, String roleTitle, Clock clock, Integer deadlineInDays, String nextAction) {
        Application application = new Application(company, roleTitle, CYCLE);
        application.setStatus(ApplicationStatus.NOT_APPLIED);
        application.setSourceUrl(company.getCareersUrl());
        application.setNextAction(nextAction);
        if (deadlineInDays != null) {
            application.setNextDeadline(
                    clock.instant().plus(deadlineInDays, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MINUTES));
        }
        application.setAppliedDate(LocalDate.now(clock.withZone(ZoneOffset.UTC)).minusDays(60));
        return application;
    }
}
