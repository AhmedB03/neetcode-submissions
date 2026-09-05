package com.ahmedb.internship.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * A single injectable clock.
 *
 * <p>Ghosting and the digest are both "what is true right now" questions, and testing them against
 * {@code Instant.now()} means sleeping or fudging tolerances. An injected clock lets a test state
 * the date outright.
 */
@Configuration(proxyBeanMethods = false)
public class ClockConfiguration {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
