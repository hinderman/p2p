-- Static reference data for the loans schema.
-- Run after 00_loans_schema.sql.
-- Idempotent: existing reference rows are not modified.

SET search_path TO loans, public;

INSERT INTO roles (code, description) VALUES
    ('LENDER', 'Can create loans and review payments for owned loans'),
    ('PAYER', 'Can view owned loans and report external payments')
ON CONFLICT (code) DO NOTHING;
