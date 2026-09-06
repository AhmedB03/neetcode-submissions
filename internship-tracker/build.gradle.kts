plugins {
    java
    id("org.springframework.boot") version "3.5.16"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.ahmedb"
version = "0.1.0-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

extra["testcontainersVersion"] = "1.20.4"

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")

    // Gmail ingestion. READ-ONLY scope only -- see GmailProperties.
    implementation("com.google.apis:google-api-services-gmail:v1-rev20260727-2.0.0")
    implementation("com.google.auth:google-auth-library-oauth2-http:1.30.1")
    implementation("com.google.oauth-client:google-oauth-client-jetty:1.39.0")
    implementation("com.google.http-client:google-http-client-jackson2:1.45.3")

    runtimeOnly("org.postgresql:postgresql")
    implementation("org.flywaydb:flyway-core")
    runtimeOnly("org.flywaydb:flyway-database-postgresql")

    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

    // Demo mode only: on the bootRun classpath, deliberately excluded from the packaged jar.
    developmentOnly("com.h2database:h2")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("com.h2database:h2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    // Real-Postgres fidelity where Docker is available: -Ptestcontainers
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
}

dependencyManagement {
    imports {
        mavenBom("org.testcontainers:testcontainers-bom:${property("testcontainersVersion")}")
    }
}

tasks.withType<JavaCompile> {
    options.compilerArgs.addAll(listOf("-Xlint:all", "-parameters"))
}

tasks.withType<Test> {
    useJUnitPlatform()
    // Default: H2 in PostgreSQL compatibility mode (no Docker needed).
    // Opt in to real Postgres via Testcontainers with: ./gradlew test -Ptestcontainers
    if (project.hasProperty("testcontainers")) {
        systemProperty("spring.profiles.active", "testcontainers")
        doFirst {
            // Checked up front so the failure names the cause. Without this, a missing daemon
            // surfaces as a context-load error in every Spring test, which says nothing useful.
            val dockerRunning =
                try {
                    ProcessBuilder("docker", "info")
                        .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                        .redirectError(ProcessBuilder.Redirect.DISCARD)
                        .start()
                        .waitFor() == 0
                } catch (e: Exception) {
                    false
                }
            if (!dockerRunning) {
                throw GradleException(
                    "-Ptestcontainers needs a running Docker daemon, and none was reachable.\n" +
                        "Start Docker and retry, or run './gradlew test' to use H2 in PostgreSQL mode."
                )
            }
        }
    }
    testLogging {
        events("passed", "skipped", "failed")
    }
}
