-- Natural-person lending platform.
-- PostgreSQL 18+ | Idempotent initial schema.
--
-- Design rules:
--   * No stored procedures, functions, or triggers are created here.
--   * The backend owns UUID generation, timestamps, financial calculations,
--     installment generation, and business-state transitions.
--   * The script can be safely re-run against a database containing this exact
--     schema. Schema evolution requires a new versioned initial script; never
--     edit a script already used in a deployed environment.

CREATE SCHEMA IF NOT EXISTS loans;
SET search_path TO loans, public;

-- ---------------------------------------------------------------------------
-- Identity, access, and sessions
-- ---------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS people (
    person_id                UUID PRIMARY KEY,
    first_name               VARCHAR(120),
    last_name                VARCHAR(120),
    document_type            VARCHAR(30),
    document_number          VARCHAR(60),
    status                   VARCHAR(20) NOT NULL,
    created_at               TIMESTAMPTZ NOT NULL,
    updated_at               TIMESTAMPTZ NOT NULL,
    version                  BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_people_status CHECK (status IN ('PENDING', 'ACTIVE', 'BLOCKED', 'INACTIVE')),
    CONSTRAINT ck_people_version CHECK (version >= 0)
);

CREATE TABLE IF NOT EXISTS person_emails (
    person_email_id          UUID PRIMARY KEY,
    person_id                UUID NOT NULL,
    original_email           VARCHAR(254) NOT NULL,
    normalized_email         VARCHAR(254) NOT NULL,
    is_primary               BOOLEAN NOT NULL DEFAULT FALSE,
    verified_at              TIMESTAMPTZ,
    created_at               TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_person_emails_person FOREIGN KEY (person_id) REFERENCES people (person_id),
    CONSTRAINT uq_person_emails_normalized_email UNIQUE (normalized_email),
    CONSTRAINT ck_person_emails_normalized_email CHECK (normalized_email = lower(normalized_email)),
    CONSTRAINT ck_person_emails_format CHECK (position('@' IN normalized_email) > 1)
);

CREATE TABLE IF NOT EXISTS user_accounts (
    user_account_id          UUID PRIMARY KEY,
    person_id                UUID NOT NULL,
    password_hash            VARCHAR(255) NOT NULL,
    status                   VARCHAR(25) NOT NULL,
    password_changed_at      TIMESTAMPTZ NOT NULL,
    last_sign_in_at          TIMESTAMPTZ,
    authorization_version    BIGINT NOT NULL DEFAULT 0,
    created_at               TIMESTAMPTZ NOT NULL,
    updated_at               TIMESTAMPTZ NOT NULL,
    version                  BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_user_accounts_person FOREIGN KEY (person_id) REFERENCES people (person_id),
    CONSTRAINT uq_user_accounts_person UNIQUE (person_id),
    CONSTRAINT ck_user_accounts_status
        CHECK (status IN ('PENDING_VERIFICATION', 'ACTIVE', 'BLOCKED', 'SUSPENDED', 'INACTIVE')),
    CONSTRAINT ck_user_accounts_version CHECK (version >= 0),
    CONSTRAINT ck_user_accounts_authorization_version CHECK (authorization_version >= 0)
);

CREATE TABLE IF NOT EXISTS roles (
    code                     VARCHAR(30) PRIMARY KEY,
    description              VARCHAR(120) NOT NULL
);

CREATE TABLE IF NOT EXISTS user_account_roles (
    user_account_id          UUID NOT NULL,
    role_code                VARCHAR(30) NOT NULL,
    granted_at               TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (user_account_id, role_code),
    CONSTRAINT fk_user_account_roles_account FOREIGN KEY (user_account_id)
        REFERENCES user_accounts (user_account_id),
    CONSTRAINT fk_user_account_roles_role FOREIGN KEY (role_code) REFERENCES roles (code)
);

CREATE TABLE IF NOT EXISTS user_sessions (
    session_id               UUID PRIMARY KEY,
    user_account_id          UUID NOT NULL,
    user_agent               VARCHAR(1000),
    source_ip                INET,
    created_at               TIMESTAMPTZ NOT NULL,
    expires_at               TIMESTAMPTZ NOT NULL,
    revoked_at               TIMESTAMPTZ,
    revocation_reason        VARCHAR(200),
    CONSTRAINT fk_user_sessions_account FOREIGN KEY (user_account_id) REFERENCES user_accounts (user_account_id),
    CONSTRAINT ck_user_sessions_dates CHECK (expires_at > created_at)
);

CREATE TABLE IF NOT EXISTS refresh_tokens (
    refresh_token_id         UUID PRIMARY KEY,
    session_id               UUID NOT NULL,
    token_hash               CHAR(64) NOT NULL,
    issued_at                TIMESTAMPTZ NOT NULL,
    expires_at               TIMESTAMPTZ NOT NULL,
    consumed_at              TIMESTAMPTZ,
    revoked_at               TIMESTAMPTZ,
    replaced_by_id           UUID,
    CONSTRAINT fk_refresh_tokens_session FOREIGN KEY (session_id) REFERENCES user_sessions (session_id),
    CONSTRAINT fk_refresh_tokens_replacement FOREIGN KEY (replaced_by_id) REFERENCES refresh_tokens (refresh_token_id),
    CONSTRAINT uq_refresh_tokens_hash UNIQUE (token_hash),
    CONSTRAINT ck_refresh_tokens_dates CHECK (expires_at > issued_at)
);

CREATE TABLE IF NOT EXISTS sign_in_attempts (
    sign_in_attempt_id       UUID PRIMARY KEY,
    normalized_email         VARCHAR(254) NOT NULL,
    source_ip                INET,
    succeeded                BOOLEAN NOT NULL,
    occurred_at              TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_sign_in_attempts_normalized_email CHECK (normalized_email = lower(normalized_email))
);

-- ---------------------------------------------------------------------------
-- Loans, contractual terms, and payment schedules
-- ---------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS loans (
    loan_id                  UUID PRIMARY KEY,
    lender_person_id         UUID NOT NULL,
    payer_person_id          UUID NOT NULL,
    currency_code            VARCHAR(3) NOT NULL,
    status                   VARCHAR(30) NOT NULL,
    created_at               TIMESTAMPTZ NOT NULL,
    activated_at             TIMESTAMPTZ,
    completed_at             TIMESTAMPTZ,
    cancelled_at             TIMESTAMPTZ,
    updated_at               TIMESTAMPTZ NOT NULL,
    version                  BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_loans_lender FOREIGN KEY (lender_person_id) REFERENCES people (person_id),
    CONSTRAINT fk_loans_payer FOREIGN KEY (payer_person_id) REFERENCES people (person_id),
    CONSTRAINT ck_loans_distinct_parties CHECK (lender_person_id <> payer_person_id),
    CONSTRAINT ck_loans_currency CHECK (currency_code ~ '^[A-Z]{3}$'),
    CONSTRAINT ck_loans_status
        CHECK (status IN ('DRAFT', 'PENDING_ACCEPTANCE', 'ACTIVE', 'COMPLETED', 'DEFAULTED', 'CANCELLED')),
    CONSTRAINT ck_loans_version CHECK (version >= 0)
);

CREATE TABLE IF NOT EXISTS loan_terms (
    loan_term_id             UUID PRIMARY KEY,
    loan_id                  UUID NOT NULL,
    version_number           INTEGER NOT NULL,
    status                   VARCHAR(25) NOT NULL,
    original_principal       NUMERIC(19, 4) NOT NULL,
    interest_rate_percentage NUMERIC(12, 8) NOT NULL,
    rate_period              VARCHAR(25) NOT NULL,
    interest_method          VARCHAR(25) NOT NULL,
    day_count_basis          VARCHAR(15) NOT NULL,
    amortization_method      VARCHAR(30) NOT NULL,
    capital_prepayment_policy VARCHAR(25) NOT NULL,
    installment_count        INTEGER NOT NULL,
    first_due_date           DATE NOT NULL,
    time_zone                VARCHAR(64) NOT NULL,
    effective_from           TIMESTAMPTZ NOT NULL,
    effective_to             TIMESTAMPTZ,
    created_at               TIMESTAMPTZ NOT NULL,
    created_by_user_account_id UUID NOT NULL,
    CONSTRAINT fk_loan_terms_loan FOREIGN KEY (loan_id) REFERENCES loans (loan_id),
    CONSTRAINT fk_loan_terms_created_by FOREIGN KEY (created_by_user_account_id)
        REFERENCES user_accounts (user_account_id),
    CONSTRAINT uq_loan_terms_loan_version UNIQUE (loan_id, version_number),
    CONSTRAINT ck_loan_terms_version CHECK (version_number > 0),
    CONSTRAINT ck_loan_terms_status CHECK (status IN ('PROPOSED', 'ACCEPTED', 'SUPERSEDED', 'CANCELLED')),
    CONSTRAINT ck_loan_terms_principal CHECK (original_principal > 0),
    CONSTRAINT ck_loan_terms_interest_rate CHECK (interest_rate_percentage >= 0),
    CONSTRAINT ck_loan_terms_rate_period
        CHECK (rate_period IN ('DAILY', 'MONTHLY_NOMINAL', 'MONTHLY_EFFECTIVE', 'ANNUAL_NOMINAL', 'ANNUAL_EFFECTIVE')),
    CONSTRAINT ck_loan_terms_interest_method CHECK (interest_method IN ('SIMPLE', 'COMPOUND')),
    CONSTRAINT ck_loan_terms_day_count_basis CHECK (day_count_basis IN ('THIRTY_360', 'ACTUAL_360', 'ACTUAL_365')),
    CONSTRAINT ck_loan_terms_amortization_method
        CHECK (amortization_method IN ('FIXED_PAYMENT', 'FIXED_PRINCIPAL', 'INTEREST_AT_MATURITY')),
    CONSTRAINT ck_loan_terms_capital_prepayment_policy
        CHECK (capital_prepayment_policy IN ('SHORTEN_TERM', 'REDUCE_PAYMENT', 'NO_RECALCULATION')),
    CONSTRAINT ck_loan_terms_installment_count CHECK (installment_count > 0),
    CONSTRAINT ck_loan_terms_effective_dates CHECK (effective_to IS NULL OR effective_to > effective_from)
);

CREATE TABLE IF NOT EXISTS payment_schedule_rules (
    payment_schedule_rule_id UUID PRIMARY KEY,
    loan_term_id             UUID NOT NULL,
    frequency                VARCHAR(20) NOT NULL,
    interval_days            INTEGER,
    non_business_day_adjustment VARCHAR(25) NOT NULL,
    created_at               TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_payment_schedule_rules_term FOREIGN KEY (loan_term_id) REFERENCES loan_terms (loan_term_id),
    CONSTRAINT uq_payment_schedule_rules_term UNIQUE (loan_term_id),
    CONSTRAINT ck_payment_schedule_rules_frequency CHECK (frequency IN ('WEEKLY', 'BIWEEKLY', 'MONTHLY', 'EVERY_N_DAYS')),
    CONSTRAINT ck_payment_schedule_rules_interval
        CHECK ((frequency = 'EVERY_N_DAYS' AND interval_days IS NOT NULL AND interval_days > 0)
            OR (frequency <> 'EVERY_N_DAYS' AND interval_days IS NULL)),
    CONSTRAINT ck_payment_schedule_rules_adjustment
        CHECK (non_business_day_adjustment IN ('NEXT_BUSINESS_DAY', 'PREVIOUS_BUSINESS_DAY', 'NO_ADJUSTMENT'))
);

CREATE TABLE IF NOT EXISTS payment_schedule_rule_days (
    payment_schedule_rule_id UUID NOT NULL,
    day_of_month             SMALLINT NOT NULL,
    PRIMARY KEY (payment_schedule_rule_id, day_of_month),
    CONSTRAINT fk_payment_schedule_rule_days_rule FOREIGN KEY (payment_schedule_rule_id)
        REFERENCES payment_schedule_rules (payment_schedule_rule_id),
    CONSTRAINT ck_payment_schedule_rule_days_day CHECK (day_of_month BETWEEN 1 AND 31)
);

CREATE TABLE IF NOT EXISTS stored_objects (
    stored_object_id         UUID PRIMARY KEY,
    storage_key              VARCHAR(500) NOT NULL,
    original_name            VARCHAR(500) NOT NULL,
    content_type             VARCHAR(150) NOT NULL,
    size_bytes               BIGINT NOT NULL,
    sha256                   CHAR(64) NOT NULL,
    scan_status              VARCHAR(20) NOT NULL,
    uploaded_by_user_account_id UUID NOT NULL,
    created_at               TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_stored_objects_uploaded_by FOREIGN KEY (uploaded_by_user_account_id)
        REFERENCES user_accounts (user_account_id),
    CONSTRAINT uq_stored_objects_storage_key UNIQUE (storage_key),
    CONSTRAINT ck_stored_objects_size CHECK (size_bytes > 0),
    CONSTRAINT ck_stored_objects_sha256 CHECK (sha256 ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_stored_objects_scan_status CHECK (scan_status IN ('PENDING', 'SAFE', 'REJECTED', 'DELETED'))
);

CREATE TABLE IF NOT EXISTS loan_term_documents (
    loan_term_document_id    UUID PRIMARY KEY,
    loan_term_id             UUID NOT NULL,
    stored_object_id         UUID NOT NULL,
    document_type            VARCHAR(30) NOT NULL,
    created_at               TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_loan_term_documents_term FOREIGN KEY (loan_term_id) REFERENCES loan_terms (loan_term_id),
    CONSTRAINT fk_loan_term_documents_object FOREIGN KEY (stored_object_id) REFERENCES stored_objects (stored_object_id),
    CONSTRAINT uq_loan_term_documents_object UNIQUE (stored_object_id),
    CONSTRAINT ck_loan_term_documents_type CHECK (document_type IN ('AGREEMENT', 'ADDENDUM', 'OTHER'))
);

CREATE TABLE IF NOT EXISTS loan_invitations (
    loan_invitation_id       UUID PRIMARY KEY,
    loan_term_id             UUID NOT NULL,
    normalized_email         VARCHAR(254) NOT NULL,
    token_hash               CHAR(64) NOT NULL,
    status                   VARCHAR(20) NOT NULL,
    expires_at               TIMESTAMPTZ NOT NULL,
    accepted_at              TIMESTAMPTZ,
    revoked_at               TIMESTAMPTZ,
    created_at               TIMESTAMPTZ NOT NULL,
    created_by_user_account_id UUID NOT NULL,
    CONSTRAINT fk_loan_invitations_term FOREIGN KEY (loan_term_id) REFERENCES loan_terms (loan_term_id),
    CONSTRAINT fk_loan_invitations_created_by FOREIGN KEY (created_by_user_account_id)
        REFERENCES user_accounts (user_account_id),
    CONSTRAINT uq_loan_invitations_token_hash UNIQUE (token_hash),
    CONSTRAINT ck_loan_invitations_email CHECK (normalized_email = lower(normalized_email)),
    CONSTRAINT ck_loan_invitations_status CHECK (status IN ('PENDING', 'ACCEPTED', 'EXPIRED', 'REVOKED')),
    CONSTRAINT ck_loan_invitations_expiration CHECK (expires_at > created_at)
);

CREATE TABLE IF NOT EXISTS loan_term_acceptances (
    loan_term_acceptance_id  UUID PRIMARY KEY,
    loan_term_id             UUID NOT NULL,
    user_account_id          UUID NOT NULL,
    accepting_role           VARCHAR(30) NOT NULL,
    loan_invitation_id       UUID,
    source_ip                INET,
    user_agent               VARCHAR(1000),
    accepted_at              TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_loan_term_acceptances_term FOREIGN KEY (loan_term_id) REFERENCES loan_terms (loan_term_id),
    CONSTRAINT fk_loan_term_acceptances_account FOREIGN KEY (user_account_id) REFERENCES user_accounts (user_account_id),
    CONSTRAINT fk_loan_term_acceptances_invitation FOREIGN KEY (loan_invitation_id)
        REFERENCES loan_invitations (loan_invitation_id),
    CONSTRAINT uq_loan_term_acceptances_term_account_role UNIQUE (loan_term_id, user_account_id, accepting_role),
    CONSTRAINT ck_loan_term_acceptances_role CHECK (accepting_role IN ('LENDER', 'PAYER'))
);

CREATE TABLE IF NOT EXISTS payment_plans (
    payment_plan_id          UUID PRIMARY KEY,
    loan_term_id             UUID NOT NULL,
    version_number           INTEGER NOT NULL,
    status                   VARCHAR(20) NOT NULL,
    reason                   VARCHAR(25) NOT NULL,
    created_at               TIMESTAMPTZ NOT NULL,
    superseded_at            TIMESTAMPTZ,
    created_by_user_account_id UUID NOT NULL,
    CONSTRAINT fk_payment_plans_term FOREIGN KEY (loan_term_id) REFERENCES loan_terms (loan_term_id),
    CONSTRAINT fk_payment_plans_created_by FOREIGN KEY (created_by_user_account_id)
        REFERENCES user_accounts (user_account_id),
    CONSTRAINT uq_payment_plans_term_version UNIQUE (loan_term_id, version_number),
    CONSTRAINT ck_payment_plans_version CHECK (version_number > 0),
    CONSTRAINT ck_payment_plans_status CHECK (status IN ('CURRENT', 'SUPERSEDED', 'CANCELLED')),
    CONSTRAINT ck_payment_plans_reason CHECK (reason IN ('ORIGINAL', 'CAPITAL_PREPAYMENT', 'REFINANCING', 'CORRECTION'))
);

CREATE TABLE IF NOT EXISTS installments (
    installment_id           UUID PRIMARY KEY,
    payment_plan_id          UUID NOT NULL,
    installment_number       INTEGER NOT NULL,
    due_date                 DATE NOT NULL,
    agreed_principal         NUMERIC(19, 4) NOT NULL,
    agreed_interest          NUMERIC(19, 4) NOT NULL,
    agreed_fee               NUMERIC(19, 4) NOT NULL DEFAULT 0,
    created_at               TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_installments_plan FOREIGN KEY (payment_plan_id) REFERENCES payment_plans (payment_plan_id),
    CONSTRAINT uq_installments_plan_number UNIQUE (payment_plan_id, installment_number),
    CONSTRAINT ck_installments_number CHECK (installment_number > 0),
    CONSTRAINT ck_installments_principal CHECK (agreed_principal >= 0),
    CONSTRAINT ck_installments_interest CHECK (agreed_interest >= 0),
    CONSTRAINT ck_installments_fee CHECK (agreed_fee >= 0),
    CONSTRAINT ck_installments_non_zero_amount CHECK (agreed_principal + agreed_interest + agreed_fee > 0)
);

-- ---------------------------------------------------------------------------
-- Payment reporting, evidence, review, and allocation
-- ---------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS reported_payments (
    reported_payment_id      UUID PRIMARY KEY,
    loan_id                  UUID NOT NULL,
    payer_person_id          UUID NOT NULL,
    reported_by_user_account_id UUID NOT NULL,
    payment_type             VARCHAR(25) NOT NULL,
    reported_amount          NUMERIC(19, 4) NOT NULL,
    reported_payment_date    DATE NOT NULL,
    external_reference       VARCHAR(150),
    idempotency_key          UUID NOT NULL,
    status                   VARCHAR(30) NOT NULL,
    submitted_at             TIMESTAMPTZ NOT NULL,
    updated_at               TIMESTAMPTZ NOT NULL,
    version                  BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_reported_payments_loan FOREIGN KEY (loan_id) REFERENCES loans (loan_id),
    CONSTRAINT fk_reported_payments_payer FOREIGN KEY (payer_person_id) REFERENCES people (person_id),
    CONSTRAINT fk_reported_payments_reported_by FOREIGN KEY (reported_by_user_account_id)
        REFERENCES user_accounts (user_account_id),
    CONSTRAINT uq_reported_payments_idempotency_key UNIQUE (idempotency_key),
    CONSTRAINT ck_reported_payments_type CHECK (payment_type IN ('INSTALLMENT', 'CAPITAL_PREPAYMENT', 'PAYOFF')),
    CONSTRAINT ck_reported_payments_amount CHECK (reported_amount > 0),
    CONSTRAINT ck_reported_payments_status
        CHECK (status IN ('DRAFT', 'SUBMITTED', 'PENDING_REVIEW', 'APPROVED', 'REJECTED', 'REVERSED')),
    CONSTRAINT ck_reported_payments_version CHECK (version >= 0)
);

CREATE TABLE IF NOT EXISTS payment_evidence (
    payment_evidence_id      UUID PRIMARY KEY,
    reported_payment_id      UUID NOT NULL,
    stored_object_id         UUID NOT NULL,
    created_at               TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_payment_evidence_payment FOREIGN KEY (reported_payment_id)
        REFERENCES reported_payments (reported_payment_id),
    CONSTRAINT fk_payment_evidence_object FOREIGN KEY (stored_object_id) REFERENCES stored_objects (stored_object_id),
    CONSTRAINT uq_payment_evidence_object UNIQUE (stored_object_id)
);

CREATE TABLE IF NOT EXISTS payment_reviews (
    payment_review_id        UUID PRIMARY KEY,
    reported_payment_id      UUID NOT NULL,
    review_number            INTEGER NOT NULL,
    reviewer_user_account_id UUID NOT NULL,
    decision                 VARCHAR(20) NOT NULL,
    validated_amount         NUMERIC(19, 4),
    reason                   VARCHAR(1000),
    reviewed_at              TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_payment_reviews_payment FOREIGN KEY (reported_payment_id)
        REFERENCES reported_payments (reported_payment_id),
    CONSTRAINT fk_payment_reviews_reviewer FOREIGN KEY (reviewer_user_account_id)
        REFERENCES user_accounts (user_account_id),
    CONSTRAINT uq_payment_reviews_payment_number UNIQUE (reported_payment_id, review_number),
    CONSTRAINT ck_payment_reviews_number CHECK (review_number > 0),
    CONSTRAINT ck_payment_reviews_decision CHECK (decision IN ('APPROVED', 'REJECTED')),
    CONSTRAINT ck_payment_reviews_validated_amount CHECK (validated_amount IS NULL OR validated_amount > 0),
    CONSTRAINT ck_payment_reviews_rejection_reason CHECK (decision <> 'REJECTED' OR reason IS NOT NULL)
);

CREATE TABLE IF NOT EXISTS payment_allocations (
    payment_allocation_id    UUID PRIMARY KEY,
    reported_payment_id      UUID NOT NULL,
    installment_id           UUID,
    allocation_type          VARCHAR(25) NOT NULL,
    allocated_amount         NUMERIC(19, 4) NOT NULL,
    created_at               TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_payment_allocations_payment FOREIGN KEY (reported_payment_id)
        REFERENCES reported_payments (reported_payment_id),
    CONSTRAINT fk_payment_allocations_installment FOREIGN KEY (installment_id) REFERENCES installments (installment_id),
    CONSTRAINT ck_payment_allocations_type
        CHECK (allocation_type IN ('INTEREST', 'INSTALLMENT_PRINCIPAL', 'FEE', 'DIRECT_PRINCIPAL')),
    CONSTRAINT ck_payment_allocations_amount CHECK (allocated_amount > 0),
    CONSTRAINT ck_payment_allocations_installment
        CHECK ((allocation_type = 'DIRECT_PRINCIPAL' AND installment_id IS NULL)
            OR (allocation_type <> 'DIRECT_PRINCIPAL' AND installment_id IS NOT NULL))
);

CREATE TABLE IF NOT EXISTS payment_reversals (
    payment_reversal_id      UUID PRIMARY KEY,
    reported_payment_id      UUID NOT NULL,
    reviewer_user_account_id UUID NOT NULL,
    reason                   VARCHAR(1000) NOT NULL,
    reversed_at              TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_payment_reversals_payment FOREIGN KEY (reported_payment_id)
        REFERENCES reported_payments (reported_payment_id),
    CONSTRAINT fk_payment_reversals_reviewer FOREIGN KEY (reviewer_user_account_id)
        REFERENCES user_accounts (user_account_id),
    CONSTRAINT uq_payment_reversals_payment UNIQUE (reported_payment_id)
);

-- ---------------------------------------------------------------------------
-- Notifications, audit trail, and reliable event delivery
-- ---------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS notifications (
    notification_id          UUID PRIMARY KEY,
    recipient_person_id      UUID NOT NULL,
    notification_type        VARCHAR(40) NOT NULL,
    title                    VARCHAR(200) NOT NULL,
    body                     VARCHAR(2000) NOT NULL,
    reference_type           VARCHAR(50),
    reference_id             UUID,
    read_at                  TIMESTAMPTZ,
    created_at               TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_notifications_recipient FOREIGN KEY (recipient_person_id) REFERENCES people (person_id)
);

CREATE TABLE IF NOT EXISTS notification_deliveries (
    notification_delivery_id UUID PRIMARY KEY,
    notification_id          UUID NOT NULL,
    channel                  VARCHAR(20) NOT NULL,
    status                   VARCHAR(20) NOT NULL,
    provider_message_id      VARCHAR(255),
    attempted_at             TIMESTAMPTZ NOT NULL,
    delivered_at             TIMESTAMPTZ,
    error_detail             VARCHAR(1000),
    CONSTRAINT fk_notification_deliveries_notification FOREIGN KEY (notification_id)
        REFERENCES notifications (notification_id),
    CONSTRAINT ck_notification_deliveries_channel CHECK (channel IN ('IN_APP', 'EMAIL', 'FCM_PUSH', 'HUAWEI_PUSH')),
    CONSTRAINT ck_notification_deliveries_status CHECK (status IN ('PENDING', 'SENT', 'DELIVERED', 'FAILED'))
);

CREATE TABLE IF NOT EXISTS outbox_events (
    outbox_event_id          UUID PRIMARY KEY,
    aggregate_type           VARCHAR(50) NOT NULL,
    aggregate_id             UUID NOT NULL,
    event_type               VARCHAR(100) NOT NULL,
    payload                  JSONB NOT NULL,
    occurred_at              TIMESTAMPTZ NOT NULL,
    published_at             TIMESTAMPTZ,
    attempts                 INTEGER NOT NULL DEFAULT 0,
    last_error               VARCHAR(1000),
    CONSTRAINT ck_outbox_events_attempts CHECK (attempts >= 0)
);

CREATE TABLE IF NOT EXISTS audit_events (
    audit_event_id           UUID PRIMARY KEY,
    actor_user_account_id    UUID,
    action                   VARCHAR(100) NOT NULL,
    resource_type            VARCHAR(50) NOT NULL,
    resource_id              UUID,
    source_ip                INET,
    user_agent               VARCHAR(1000),
    metadata                 JSONB NOT NULL DEFAULT '{}'::JSONB,
    occurred_at              TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_audit_events_actor FOREIGN KEY (actor_user_account_id) REFERENCES user_accounts (user_account_id)
);

-- ---------------------------------------------------------------------------
-- Logical-integrity and query indexes.
-- CREATE INDEX IF NOT EXISTS makes repeated execution safe.
-- ---------------------------------------------------------------------------

CREATE UNIQUE INDEX IF NOT EXISTS ux_person_emails_primary
    ON person_emails (person_id) WHERE is_primary;

CREATE INDEX IF NOT EXISTS ix_user_accounts_person ON user_accounts (person_id);

CREATE INDEX IF NOT EXISTS ix_user_account_roles_role ON user_account_roles (role_code, user_account_id);

CREATE INDEX IF NOT EXISTS ix_user_sessions_active
    ON user_sessions (user_account_id, expires_at) WHERE revoked_at IS NULL;

CREATE INDEX IF NOT EXISTS ix_refresh_tokens_active
    ON refresh_tokens (session_id, expires_at) WHERE revoked_at IS NULL AND consumed_at IS NULL;

CREATE INDEX IF NOT EXISTS ix_sign_in_attempts_email_occurred_at
    ON sign_in_attempts (normalized_email, occurred_at DESC);

CREATE INDEX IF NOT EXISTS ix_loans_lender_status ON loans (lender_person_id, status, created_at DESC);

CREATE INDEX IF NOT EXISTS ix_loans_payer_status ON loans (payer_person_id, status, created_at DESC);

CREATE UNIQUE INDEX IF NOT EXISTS ux_loan_terms_accepted_loan
    ON loan_terms (loan_id) WHERE status = 'ACCEPTED';

CREATE INDEX IF NOT EXISTS ix_loan_invitations_email_status
    ON loan_invitations (normalized_email, status, expires_at);

CREATE UNIQUE INDEX IF NOT EXISTS ux_loan_invitations_pending_term
    ON loan_invitations (loan_term_id) WHERE status = 'PENDING';

CREATE UNIQUE INDEX IF NOT EXISTS ux_payment_plans_current_term
    ON payment_plans (loan_term_id) WHERE status = 'CURRENT';

CREATE INDEX IF NOT EXISTS ix_installments_plan_due_date
    ON installments (payment_plan_id, due_date, installment_number);

CREATE INDEX IF NOT EXISTS ix_reported_payments_loan_status
    ON reported_payments (loan_id, status, submitted_at DESC);

CREATE INDEX IF NOT EXISTS ix_reported_payments_payer_status
    ON reported_payments (payer_person_id, status, submitted_at DESC);

CREATE INDEX IF NOT EXISTS ix_payment_evidence_payment ON payment_evidence (reported_payment_id);

CREATE INDEX IF NOT EXISTS ix_payment_reviews_payment ON payment_reviews (reported_payment_id, review_number DESC);

CREATE UNIQUE INDEX IF NOT EXISTS ux_payment_allocations_installment_type
    ON payment_allocations (reported_payment_id, installment_id, allocation_type) WHERE installment_id IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS ux_payment_allocations_direct_principal
    ON payment_allocations (reported_payment_id) WHERE allocation_type = 'DIRECT_PRINCIPAL';

CREATE INDEX IF NOT EXISTS ix_notifications_unread_recipient
    ON notifications (recipient_person_id, created_at DESC) WHERE read_at IS NULL;

CREATE INDEX IF NOT EXISTS ix_notification_deliveries_pending
    ON notification_deliveries (status, attempted_at) WHERE status IN ('PENDING', 'FAILED');

CREATE INDEX IF NOT EXISTS ix_outbox_events_unpublished
    ON outbox_events (occurred_at) WHERE published_at IS NULL;

CREATE INDEX IF NOT EXISTS ix_audit_events_resource
    ON audit_events (resource_type, resource_id, occurred_at DESC);

CREATE INDEX IF NOT EXISTS ix_audit_events_actor
    ON audit_events (actor_user_account_id, occurred_at DESC) WHERE actor_user_account_id IS NOT NULL;
