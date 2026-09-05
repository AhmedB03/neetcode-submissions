package com.ahmedb.internship;

import java.time.Clock;
import java.time.ZoneOffset;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** Freezes time at {@link TestFixtures#NOW} so "30 days of silence" is stated, not slept through. */
@TestConfiguration
public class TestClockConfiguration {

    @Bean
    @Primary
    public Clock fixedClock() {
        return Clock.fixed(TestFixtures.NOW, ZoneOffset.UTC);
    }
}
