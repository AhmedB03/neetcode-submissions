package com.ahmedb.internship;

import org.springframework.test.context.ActiveProfilesResolver;

/**
 * Chooses the test profile at runtime.
 *
 * <p>Default is {@code test} (H2 in PostgreSQL mode, no Docker). Running with
 * {@code ./gradlew test -Ptestcontainers} sets {@code spring.profiles.active} and adds the
 * {@code testcontainers} profile, which swaps in real PostgreSQL and turns Flyway back on.
 *
 * <p>A plain {@code @ActiveProfiles("test")} would win over the system property, which is why this
 * exists rather than the annotation's simple form.
 */
public class TestProfilesResolver implements ActiveProfilesResolver {

    @Override
    public String[] resolve(Class<?> testClass) {
        String requested = System.getProperty("spring.profiles.active", "");
        return requested.contains("testcontainers")
                ? new String[] {"test", "testcontainers"}
                : new String[] {"test"};
    }
}
