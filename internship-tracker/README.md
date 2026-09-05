# Internship pipeline tracker

Tracks internship applications by reading the mail they generate. It classifies recruiting email
into status transitions, records each one with the message that justified it, and surfaces what
needs attention.

**It reads and tracks. It never acts on your behalf.** No application is ever submitted, no email is
ever sent or replied to, nothing in your mailbox is labelled, archived or deleted. The Gmail scope is
read-only and pinned in code.

Java 21 · Spring Boot 3.5 · PostgreSQL · Gradle. The API is JSON-only and returns DTOs rather than
entities, so a React client can be added without touching the backend.

## Phase 1 status

| Capability | State |
|---|---|
| Gmail ingestion, read-only OAuth, 90-day backfill | Done |
| Rule-based classifier behind a swappable interface | Done |
| StatusEvents persisted, parent application updated | Done |
| Applications by deadline, detail with timeline, manual override | Done |
| `/digest` — closing in 7 days, plus anything ghosted | Done |
| Review queue for mail that matches no application | Done |
| Listing entity | Schema only; nothing collects postings yet |
| LLM classifier | Interface and composite ready; no implementation |

## Quick start

```bash
createdb internship_tracker
cp .env.example .env          # fill in, never commit
set -a && source .env && set +a
./gradlew bootRun
```

Flyway creates the schema on first start. With `GMAIL_ENABLED=false` the app runs with no
credentials at all, which is the easiest way to look around.

```bash
# Record a company and an application
curl -X POST localhost:8080/companies -H 'Content-Type: application/json' \
  -d '{"name":"Stripe","careersUrl":"https://stripe.com/jobs","emailDomains":["stripe.com"]}'

curl -X POST localhost:8080/applications -H 'Content-Type: application/json' \
  -d '{"companyId":1,"roleTitle":"Software Engineer Intern","cycle":"Summer 2027",
       "status":"APPLIED","nextAction":"Finish the OA","nextDeadline":"2026-09-12T23:59:00Z"}'

# Pull the last 90 days of mail and classify it
curl -X POST localhost:8080/ingest/run

curl localhost:8080/digest
```

## Gmail setup

1. Google Cloud Console → APIs & Services → enable the **Gmail API**.
2. Credentials → Create credentials → **OAuth client ID** → application type **Desktop app**.
3. Put the client id and secret in `.env`.

Then either path works:

- **Interactive** — leave `GMAIL_REFRESH_TOKEN` empty. On the first run the app opens a consent
  screen and caches the token under `.gmail-tokens/` (gitignored).
- **Headless** — set `GMAIL_REFRESH_TOKEN` and no browser is involved.

Google will warn that the app is unverified. That is expected for a personal OAuth client; the
consent screen still shows the single read-only scope being granted.

Polling is off by default. `POST /ingest/run` triggers a cycle by hand; `GMAIL_POLL_ENABLED=true`
turns on the schedule in `GMAIL_POLL_CRON`.

## API

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/applications` | All applications, soonest deadline first, undated last |
| `GET` | `/applications/{id}` | One application with its full event timeline |
| `GET` | `/applications/{id}/events` | The timeline alone |
| `POST` | `/applications` | Record an application |
| `PATCH` | `/applications/{id}` | Update next action, deadline, applied date, source URL |
| `POST` | `/applications/{id}/status` | Override the status by hand |
| `GET` | `/digest` | Closing within 7 days, plus anything now ghosted |
| `GET` | `/companies`, `POST` `/companies` | Companies and their sender domains |
| `GET` | `/unmatched` | Review queue |
| `POST` | `/unmatched/{id}/link` | Attach a queued email to an application |
| `POST` | `/unmatched/{id}/dismiss` | Discard a queued email |
| `POST` | `/ingest/run` | Run one polling cycle now |

`POST /applications`, `PATCH /applications/{id}` and `POST /companies` are additions to the four
endpoints in the phase 1 brief. Without them there is no way to get an application or a deadline
into the system, so `/digest` would have nothing to report.

## How it decides things

### GHOSTED is derived, never stored

`GHOSTED` is absent from the `CHECK` constraint on `application.status`, and
`ApplicationStatus.requireStorable` rejects it at every persistence boundary. It is computed on each
read by `GhostPolicy`: non-terminal status, and no `StatusEvent` within `tracker.ghost.threshold-days`
(default 30).

The API reports it as `status` and keeps the real one in `storedStatus`, so a client can tell "went
quiet while at INTERVIEW" from "was rejected". Because it is a function of the data rather than a
stored flag, there is no job to run, nothing to backfill, and un-ghosting is automatic — a reply
after two months of silence takes effect the moment its event lands.

Two statuses are exempt. `OFFER` and `REJECTED` are settled, and `NOT_APPLIED` has not started —
silence there is expected, not evidence of being dropped.

### Classification is a swappable interface

```java
public interface EmailClassifier {
    Classification classify(IngestedEmail email, ClassificationContext context);
    String id();
}
```

Implementations are **pure**: no database, no writes, no I/O beyond what arrives in the two
arguments. Matching an email to an application, persisting events and staying idempotent all live on
the other side of this interface, so an LLM classifier is a bean swap rather than a rewrite.

A verdict is `TRANSITION`, `IGNORE` or `ABSTAIN`. The last two are different on purpose: `IGNORE`
means "confidently not pipeline mail" (a job alert), `ABSTAIN` means "I don't know". Only abstentions
are worth escalating.

`CompositeEmailClassifier` runs classifiers in order and takes the first that commits, so an LLM can
sit behind the rules and see only what they could not decide:

```java
@Bean
EmailClassifier emailClassifier(RuleBasedEmailClassifier rules, LlmEmailClassifier llm) {
    return new CompositeEmailClassifier(List.of(rules, llm));
}
```

`ClassificationContext` carries the tracked companies as grounding — the same payload an LLM prompt
will need. A classifier that throws is skipped rather than failing the poll.

`RuleBasedEmailClassifier` (`rules:v1`) reads ordered patterns over sender domain and subject.
Ordering is the design: recruiting mail layers its vocabulary, so a rejection that opens with "thank
you for applying" must read as a rejection, a superday invitation as a final round rather than a
generic interview, and an assessment confirmation as submitted rather than a fresh invitation. The
`classifierId` is stored on every event, so decisions stay attributable to the logic that made them.

### Unmatched mail goes to a review queue

An email that classifies as a real transition but matches no known application is parked in
`unmatched_email` rather than auto-creating a company and application. A recruiter blast or a
misparsed sender would otherwise invent pipeline entries you never applied to.

`POST /unmatched/{id}/link` writes the event it should have produced and, by default, teaches the
company that sender's domain — so mail like it matches on its own next time.

### Out-of-order mail cannot rewind an application

A 90-day backfill delivers mail out of order. Every classified email is recorded — the timeline is an
audit log — but only one that advances the pipeline moves the head status, and `advancedStatus` on
each event says which happened. Rejections still land from any stage.

A manual override is the exception: it always applies, including backwards, because it exists to
correct the tracker.

### Polling is idempotent

`processed_message` records every message the pipeline has decided about, including ones that
produced no event. Without it, ignored and abstained mail would be reconsidered on every poll. Runs
resume from a watermark with a day of overlap, since mail does not arrive in timestamp order.

## Constraints, enforced in code

- **Read-only Gmail.** `GmailScopes.SCOPES` holds exactly
  `https://www.googleapis.com/auth/gmail.readonly`, with no configuration hook to widen it.
  `GmailScopesTest` fails the build if a scope containing `modify`, `send`, `compose`, `insert`,
  `labels`, `settings` or `full` ever appears.
- **Never acts.** `MailSource` exposes only `fetchSince`. There is no send, reply, label or delete
  path anywhere in the codebase.
- **No LinkedIn scraping.** No LinkedIn code path exists. LinkedIn is only present as a domain the
  classifier recognises so it can *ignore* job-alert mail. Connection data, if added later, comes from
  your own data export parsed locally.
- **Bodies are never stored.** Messages are fetched with `format=METADATA`, so Google returns headers
  and a short preview. Evidence keeps message id, thread id, subject, sender and timestamp only.
- **Secrets are environment-only.** `.env`, `*.p12`, `credentials.json`, `client_secret*.json` and
  `.gmail-tokens/` are gitignored. No credential has a default in `application.yml`.

## Tests

```bash
./gradlew test                    # 213 tests, H2 in PostgreSQL mode, no Docker needed
./gradlew test -Ptestcontainers   # the same suite against real PostgreSQL
```

The default suite lets Hibernate build the schema, so `SchemaMigrationCoverageTest` compares the
migration against the entity mappings and fails if a mapped table or column is missing from
`V1__initial_schema.sql`. The Testcontainers profile is the stronger check: it applies the migration
to real PostgreSQL and starts with `ddl-auto=validate`, so types and constraints are verified too.

> The Testcontainers profile was wired up but has not been executed — the environment this was built
> in has no Docker. Expect to run it once locally before trusting it.

## Configuration

| Variable | Default | Purpose |
|---|---|---|
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | localhost | PostgreSQL connection |
| `GMAIL_ENABLED` | `true` | With `false`, no Gmail bean exists at all |
| `GMAIL_CLIENT_ID`, `GMAIL_CLIENT_SECRET` | — | OAuth desktop client |
| `GMAIL_REFRESH_TOKEN` | — | Set for headless auth; unset for the browser flow |
| `GMAIL_TOKEN_DIRECTORY` | `./.gmail-tokens` | Where the cached token lives |
| `GMAIL_LOOKBACK_DAYS` | `90` | How far the first poll reaches back |
| `GMAIL_POLL_ENABLED` | `false` | Scheduled polling |
| `GMAIL_POLL_CRON` | `0 */15 * * * *` | Poll schedule |

Also in `application.yml`: `tracker.ghost.threshold-days` (30), `tracker.digest.horizon-days` (7),
and `tracker.gmail.query` (`in:inbox -in:chats`; widen to `-in:chats -in:spam -in:trash` to include
archived mail).

## Known gaps

- The digest window is forward-looking, so an already-overdue deadline is not in it.
- Nothing populates `Listing` yet.
- No authentication. Bind to localhost; this is a single-user tool.
- `application.status` and `last_event_at` are denormalised from the event log, maintained in the
  same transaction that writes an event.
