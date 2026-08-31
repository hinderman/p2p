-- Self-service account registration: proof of address ownership and abuse control.
--
-- Until now an account could only be created by redeeming a loan invitation, so
-- the invitation itself proved the address was reachable. A person who signs up
-- unprompted has proved nothing, hence a dedicated single-use token.

CREATE TABLE loans.account_verifications (
    account_verification_id   UUID PRIMARY KEY,
    user_account_id           UUID NOT NULL,
    purpose                   VARCHAR(30) NOT NULL,
    -- The raw token is emailed and never stored; this is its SHA-256 digest.
    token_hash                CHAR(64) NOT NULL,
    status                    VARCHAR(20) NOT NULL,
    expires_at                TIMESTAMPTZ NOT NULL,
    confirmed_at              TIMESTAMPTZ,
    revoked_at                TIMESTAMPTZ,
    created_at                TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_account_verifications_account FOREIGN KEY (user_account_id)
        REFERENCES loans.user_accounts (user_account_id),
    CONSTRAINT uq_account_verifications_token_hash UNIQUE (token_hash),
    CONSTRAINT ck_account_verifications_purpose CHECK (purpose IN ('EMAIL_VERIFICATION')),
    CONSTRAINT ck_account_verifications_status
        CHECK (status IN ('PENDING', 'CONFIRMED', 'EXPIRED', 'REVOKED')),
    CONSTRAINT ck_account_verifications_token_hash CHECK (token_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_account_verifications_expiration CHECK (expires_at > created_at),
    CONSTRAINT ck_account_verifications_confirmed
        CHECK ((status = 'CONFIRMED') = (confirmed_at IS NOT NULL)),
    CONSTRAINT ck_account_verifications_revoked
        CHECK ((status = 'REVOKED') = (revoked_at IS NOT NULL))
);

-- At most one live token per account and purpose: issuing a replacement must
-- revoke the previous one, so an old link in an old email stops working.
CREATE UNIQUE INDEX ux_account_verifications_pending
    ON loans.account_verifications (user_account_id, purpose) WHERE status = 'PENDING';

CREATE TABLE loans.registration_attempts (
    registration_attempt_id   UUID PRIMARY KEY,
    normalized_email          VARCHAR(254) NOT NULL,
    source_ip                 INET,
    occurred_at               TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_registration_attempts_email CHECK (normalized_email = lower(normalized_email))
);

-- Registration answers identically whether or not the address exists, so every
-- attempt counts towards the window, not only the failed ones.
CREATE INDEX ix_registration_attempts_email_occurred_at
    ON loans.registration_attempts (normalized_email, occurred_at DESC);

CREATE INDEX ix_registration_attempts_ip_occurred_at
    ON loans.registration_attempts (source_ip, occurred_at DESC) WHERE source_ip IS NOT NULL;
