package com.ahmedb.internship;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/**
 * Guards the Flyway migration against the entity mappings.
 *
 * <p>The default test profile lets Hibernate generate the schema, so nothing else in this suite ever
 * reads {@code V1__initial_schema.sql} -- a field added to an entity without a matching migration
 * would pass every other test and fail on the first real PostgreSQL start.
 *
 * <p>Hibernate is asked to write the schema it would create; this compares that against the
 * migration and fails if anything mapped is missing. It checks coverage of tables and columns, not
 * types or constraints -- for full fidelity, run {@code ./gradlew test -Ptestcontainers}, which
 * applies the migration to real PostgreSQL and validates the mappings against it.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(
        properties = {
            "spring.jpa.properties.jakarta.persistence.schema-generation.scripts.action=create",
            "spring.jpa.properties.jakarta.persistence.schema-generation.scripts.create-target="
                    + SchemaMigrationCoverageTest.GENERATED_SCHEMA
        })
class SchemaMigrationCoverageTest {

    static final String GENERATED_SCHEMA = "build/hibernate-schema.sql";
    private static final Path MIGRATION = Path.of("src/main/resources/db/migration/V1__initial_schema.sql");

    private static final Pattern CREATE_TABLE =
            Pattern.compile("create\\s+table\\s+(?:if\\s+not\\s+exists\\s+)?([\\w.]+)\\s*\\((.*?)\\)\\s*;",
                    Pattern.DOTALL | Pattern.CASE_INSENSITIVE);

    /** Clauses that open a table-level constraint rather than a column definition. */
    private static final Set<String> CONSTRAINT_KEYWORDS =
            Set.of("primary", "constraint", "foreign", "unique", "check", "key", "index");

    @Test
    @DisplayName("every mapped table and column exists in the Flyway migration")
    void migrationCoversTheEntityModel() throws IOException {
        Map<String, Set<String>> mapped = parse(Files.readString(Path.of(GENERATED_SCHEMA)));
        Map<String, Set<String>> migrated = parse(Files.readString(MIGRATION));

        assertThat(mapped).as("Hibernate produced no schema to compare against").isNotEmpty();
        assertThat(migrated.keySet())
                .as("tables mapped by an entity but absent from the migration")
                .containsAll(mapped.keySet());

        for (Map.Entry<String, Set<String>> table : mapped.entrySet()) {
            assertThat(migrated.get(table.getKey()))
                    .as("columns mapped on %s but absent from the migration", table.getKey())
                    .containsAll(table.getValue());
        }
    }

    /** Table name to column names, from the CREATE TABLE statements in a script. */
    private static Map<String, Set<String>> parse(String sql) {
        String withoutComments = sql.replaceAll("(?m)--.*$", "");
        Map<String, Set<String>> tables = new LinkedHashMap<>();

        Matcher matcher = CREATE_TABLE.matcher(withoutComments);
        while (matcher.find()) {
            tables.put(unquote(matcher.group(1)), columnsOf(matcher.group(2)));
        }
        return tables;
    }

    private static Set<String> columnsOf(String body) {
        Set<String> columns = new LinkedHashSet<>();
        for (String definition : splitTopLevel(body)) {
            String trimmed = definition.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            String first = unquote(trimmed.split("\\s+")[0]);
            if (!CONSTRAINT_KEYWORDS.contains(first)) {
                columns.add(first);
            }
        }
        return columns;
    }

    /** Splits on commas outside parentheses, so {@code varchar(255)} stays in one piece. */
    private static java.util.List<String> splitTopLevel(String body) {
        java.util.List<String> parts = new java.util.ArrayList<>();
        StringBuilder current = new StringBuilder();
        int depth = 0;
        for (char c : body.toCharArray()) {
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
            }
            if (c == ',' && depth == 0) {
                parts.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        parts.add(current.toString());
        return parts;
    }

    private static String unquote(String value) {
        return value.replace("\"", "").replace("`", "").toLowerCase(java.util.Locale.ROOT).trim();
    }
}
