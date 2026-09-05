package com.ahmedb.internship.repository;

import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import com.ahmedb.internship.TestProfilesResolver;
import com.ahmedb.internship.TestcontainersConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * Shared wiring for repository tests.
 *
 * <p>{@code replace = NONE} keeps the explicitly configured datasource from application-test.yml
 * (H2 in PostgreSQL mode) rather than letting Boot substitute a plain embedded database -- the
 * point is to exercise PostgreSQL-shaped SQL.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles(resolver = TestProfilesResolver.class)
@Import(TestcontainersConfiguration.class)
abstract class RepositoryTestBase {}
