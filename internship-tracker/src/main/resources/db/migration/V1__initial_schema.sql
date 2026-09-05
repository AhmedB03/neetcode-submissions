-- Internship pipeline tracker -- initial schema.
--
-- Note on ApplicationStatus: GHOSTED is deliberately absent from the CHECK constraint on
-- application.status. It is derived at read time from last_event_at and must never be persisted.
-- status_event.new_status excludes it for the same reason.

CREATE TABLE company (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(255) NOT NULL,
    careers_url VARCHAR(2048),
    notes       TEXT,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_company_name UNIQUE (name)
);

CREATE TABLE company_email_domain (
    company_id BIGINT       NOT NULL,
    domain     VARCHAR(255) NOT NULL,
    PRIMARY KEY (company_id, domain),
    CONSTRAINT fk_company_email_domain_company
        FOREIGN KEY (company_id) REFERENCES company (id) ON DELETE CASCADE
);

CREATE TABLE application (
    id            BIGSERIAL PRIMARY KEY,
    company_id    BIGINT       NOT NULL,
    role_title    VARCHAR(255) NOT NULL,
    cycle         VARCHAR(64)  NOT NULL,
    status        VARCHAR(32)  NOT NULL,
    applied_date  DATE,
    next_action   VARCHAR(500),
    next_deadline TIMESTAMP WITH TIME ZONE,
    source_url    VARCHAR(2048),
    last_event_at TIMESTAMP WITH TIME ZONE,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_application_company_role_cycle UNIQUE (company_id, role_title, cycle),
    CONSTRAINT fk_application_company FOREIGN KEY (company_id) REFERENCES company (id),
    CONSTRAINT ck_application_status CHECK (status IN (
        'NOT_APPLIED', 'APPLIED', 'OA_PENDING', 'OA_SUBMITTED',
        'INTERVIEW', 'FINAL_ROUND', 'OFFER', 'REJECTED'))
);

CREATE INDEX idx_application_next_deadline ON application (next_deadline);
CREATE INDEX idx_application_company ON application (company_id);
CREATE INDEX idx_application_status ON application (status);
CREATE INDEX idx_application_last_event_at ON application (last_event_at);

CREATE TABLE status_event (
    id                    BIGSERIAL PRIMARY KEY,
    application_id        BIGINT      NOT NULL,
    old_status            VARCHAR(32),
    new_status            VARCHAR(32) NOT NULL,
    occurred_at           TIMESTAMP WITH TIME ZONE NOT NULL,
    source                VARCHAR(16) NOT NULL,
    evidence_message_id   VARCHAR(255),
    evidence_thread_id    VARCHAR(255),
    evidence_subject      VARCHAR(998),
    evidence_from_address VARCHAR(320),
    evidence_received_at  TIMESTAMP WITH TIME ZONE,
    classifier_id         VARCHAR(64),
    confidence            DOUBLE PRECISION,
    reason                VARCHAR(500),
    advanced_status       BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at            TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_status_event_application
        FOREIGN KEY (application_id) REFERENCES application (id) ON DELETE CASCADE,
    CONSTRAINT ck_status_event_new_status CHECK (new_status IN (
        'NOT_APPLIED', 'APPLIED', 'OA_PENDING', 'OA_SUBMITTED',
        'INTERVIEW', 'FINAL_ROUND', 'OFFER', 'REJECTED')),
    CONSTRAINT ck_status_event_source CHECK (source IN ('GMAIL', 'MANUAL', 'SYSTEM'))
);

CREATE INDEX idx_status_event_application_occurred ON status_event (application_id, occurred_at);
CREATE INDEX idx_status_event_message_id ON status_event (evidence_message_id);

CREATE TABLE listing (
    id                    BIGSERIAL PRIMARY KEY,
    company_id            BIGINT,
    title                 VARCHAR(255) NOT NULL,
    location              VARCHAR(255),
    posted_date           DATE,
    source_url            VARCHAR(2048),
    source                VARCHAR(64),
    matched_application_id BIGINT,
    first_seen_at         TIMESTAMP WITH TIME ZONE NOT NULL,
    last_seen_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_listing_source_url UNIQUE (source_url),
    CONSTRAINT fk_listing_company FOREIGN KEY (company_id) REFERENCES company (id),
    CONSTRAINT fk_listing_matched_application
        FOREIGN KEY (matched_application_id) REFERENCES application (id) ON DELETE SET NULL
);

CREATE INDEX idx_listing_company ON listing (company_id);
CREATE INDEX idx_listing_matched_application ON listing (matched_application_id);

-- Review queue: classified transitions that matched no known application.
CREATE TABLE unmatched_email (
    id                    BIGSERIAL PRIMARY KEY,
    evidence_message_id   VARCHAR(255),
    evidence_thread_id    VARCHAR(255),
    evidence_subject      VARCHAR(998),
    evidence_from_address VARCHAR(320),
    evidence_received_at  TIMESTAMP WITH TIME ZONE,
    proposed_status       VARCHAR(32),
    company_hint          VARCHAR(255),
    role_hint             VARCHAR(255),
    confidence            DOUBLE PRECISION,
    reason                VARCHAR(500),
    classifier_id         VARCHAR(64),
    resolution            VARCHAR(16) NOT NULL,
    linked_application_id BIGINT,
    resolved_at           TIMESTAMP WITH TIME ZONE,
    created_at            TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_unmatched_email_message_id UNIQUE (evidence_message_id),
    CONSTRAINT fk_unmatched_email_application
        FOREIGN KEY (linked_application_id) REFERENCES application (id) ON DELETE SET NULL,
    CONSTRAINT ck_unmatched_email_resolution
        CHECK (resolution IN ('PENDING', 'LINKED', 'DISMISSED'))
);

CREATE INDEX idx_unmatched_email_resolution ON unmatched_email (resolution);

-- Idempotency ledger: every message the pipeline has already decided about.
CREATE TABLE processed_message (
    message_id          VARCHAR(255) PRIMARY KEY,
    outcome             VARCHAR(32) NOT NULL,
    classifier_id       VARCHAR(64),
    message_received_at TIMESTAMP WITH TIME ZONE,
    processed_at        TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ck_processed_message_outcome CHECK (outcome IN (
        'TRANSITION_RECORDED', 'QUEUED_FOR_REVIEW', 'IGNORED', 'ABSTAINED'))
);

CREATE INDEX idx_processed_message_received_at ON processed_message (message_received_at);
