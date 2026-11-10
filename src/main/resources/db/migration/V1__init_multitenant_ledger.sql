-- ============================================================================
-- 1. BASE LAYER ENUMS & EXTENSIONS
-- ============================================================================
-- installs and enables the uuid-ossp extension - Universally Unique Identifiers
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Restricts account behaviors based on traditional accounting classifications
CREATE TYPE account_type AS ENUM ('ASSET', 'LIABILITY', 'EQUITY', 'REVENUE', 'EXPENSE');
-- \dT+ account_type

-- ============================================================================
-- 2. ORGANISATIONAL LAYER TABLES (TENANTS & USERS)
-- ============================================================================

-- Tenants Table: Represents the core isolated billing/business boundaries
CREATE TABLE tenants (
    id VARCHAR(64) PRIMARY KEY, -- String ID matching your app identifiers (e.g. 'tenant-a')
    name VARCHAR(255) NOT NULL,
    plan_tier VARCHAR(32) NOT NULL DEFAULT 'FREE',
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Users Table: Explicit individual human actors
CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    email VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL, -- Managed safely by your security framework
    first_name VARCHAR(128) NOT NULL,
    last_name VARCHAR(128) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- Emails must be unique WITHIN their respective company boundaries
    CONSTRAINT uq_users_email UNIQUE (email)
);

CREATE TABLE users_tenants (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES users(id)  ON DELETE RESTRICT,
    tenant_id VARCHAR(64) NOT NULL REFERENCES tenants(id) ON DELETE RESTRICT,
    
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP, 
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP, 
    
    CONSTRAINT uq_users_tenants UNIQUE (user_id, tenant_id)
);
-- ============================================================================
-- 3. CORE FINANCIAL LEDGER SCHEMAS AND TABLES
-- ============================================================================

-- Accounts Table : Defines the Chart of Accounts (COA)
CREATE TABLE accounts (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id VARCHAR(64) NOT NULL REFERENCES tenants(id) ON DELETE RESTRICT,
    account_number VARCHAR(64) NOT NULL,
    name VARCHAR(255) NOT NULL,
    type account_type NOT NULL,
    currency VARCHAR(3) NOT NULL, -- ISO 4217 Currency Code (e.g., 'GBP')
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_accounts_tenant_number UNIQUE (tenant_id, account_number)
);

-- Ledger Transactions Table - The immutable header block
CREATE TABLE ledger_transactions (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id VARCHAR(64) NOT NULL REFERENCES tenants(id) ON DELETE RESTRICT,
    description VARCHAR(512) NOT NULL,
    posted_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- IDEMPOTENCY GUARD: Prevents duplicate processing from creating multiple entries
    correlation_id VARCHAR(255) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_transactions_tenant_correlation UNIQUE (tenant_id, correlation_id)
);

-- Ledger Entries Table:  The transactional lines (Splits)
-- integer columns (minor units, e.g. cents). Each line is EITHER a debit OR a
-- credit, never both and never zero.
CREATE TABLE ledger_entries (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id VARCHAR(64) NOT NULL REFERENCES tenants(id) ON DELETE RESTRICT,
    transaction_id UUID NOT NULL REFERENCES ledger_transactions(id) ON DELETE RESTRICT,
    account_id UUID NOT NULL REFERENCES accounts(id) ON DELETE RESTRICT,

    -- MINOR UNITS REQUIREMENT: Stored as a signed integer (cents/pence). 
    -- Zero value is strictly rejected.
    debit  BIGINT NOT NULL DEFAULT 0 CHECK (debit  >= 0),
    credit BIGINT NOT NULL DEFAULT 0 CHECK (credit >= 0),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- exactly one side is populated (this also replaces the old "amount <> 0")
    CONSTRAINT ck_entry_one_side CHECK (
        (debit > 0 AND credit = 0) OR (debit = 0 AND credit > 0)
    )
);


-- account_holds
-- Reservations and their lifecycle


-- outbox_events
-- Events awaiting publication


-- account_balance_projection
-- Optional rebuildable balance cache


-- idempotency_records
-- Request fingerprint and original outcome

-- ============================================================================
-- 4. PRODUCTION-GRADE PERFORMANCE INDEXING
-- ============================================================================

-- Isolate user lookups by tenant context
CREATE INDEX idx_users_tenant_lookup ON users_tenants (user_id, tenant_id);

-- Leading with tenant_id to isolate financial statement compilation instantly
CREATE INDEX idx_ledger_entries_tenant_account_date
ON ledger_entries (tenant_id, account_id, created_at DESC);

-- Speeds up transaction history inspections while respecting isolation
CREATE INDEX idx_ledger_entries_tenant_transaction_id
ON ledger_entries (tenant_id, transaction_id);

-- ============================================================================
-- 5. INTEGRITY RULES & TRIGGER DEPENDENCIES (TENANT AWARE)
-- ============================================================================
/**
 * CONSTRAINT RULE: Zero-Sum Balancing Guarantee
 * This deferred trigger verifies that the sum of all entry amounts inside a
 * transaction equals EXACTLY zero before the transaction is permanently committed.
 */
-- Entries carry no currency of their own, so it is taken from the account.
-- (Joined on account id only: tenant/account consistency is a foreign-key
-- concern and is deliberately not hidden inside this check.)
CREATE OR REPLACE FUNCTION verify_ledger_transaction_balances()
RETURNS TRIGGER AS $$
DECLARE
    v_entry_count INT;
    v_currency    TEXT;
    v_debits      BIGINT;
    v_credits     BIGINT;
BEGIN
    -- Sum up the lines for the target transaction ID
    SELECT COUNT(*)
    INTO v_entry_count
    FROM ledger_entries
    WHERE transaction_id = NEW.transaction_id
      AND tenant_id = NEW.tenant_id;

     -- Financial Validation 1: A transaction cannot be empty
    IF v_entry_count < 2 THEN
        RAISE EXCEPTION 'Ledger transaction % (Tenant: %) must contain at least 2 entries for double-entry bookkeeping.',
            NEW.transaction_id, NEW.tenant_id;
    END IF;

    SELECT a.currency, SUM(e.debit), SUM(e.credit)
    INTO v_currency, v_debits, v_credits
    FROM ledger_entries e
    JOIN accounts a ON a.id = e.account_id
    WHERE e.transaction_id = NEW.transaction_id
      AND e.tenant_id = NEW.tenant_id
    GROUP BY a.currency
    HAVING SUM(e.debit) <> SUM(e.credit)
    LIMIT 1;

    -- Financial Validation 2: Sum of Debits (+) and Credits (-) must cancel out to 0
    IF FOUND THEN
        RAISE EXCEPTION 'Ledger transaction % (Tenant: %) is unbalanced in currency %: debits % <> credits % minor units.',
            NEW.transaction_id, NEW.tenant_id, v_currency, v_debits, v_credits;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Apply the balancing constraint trigger across the transaction lifecycle
CREATE CONSTRAINT TRIGGER trg_enforce_double_entry_balance
AFTER INSERT OR UPDATE OR DELETE ON ledger_entries
DEFERRABLE INITIALLY DEFERRED -- Runs at the very end of the transaction commit block
FOR EACH ROW
EXECUTE FUNCTION verify_ledger_transaction_balances();

-- Immutability Protection Block
/**
 * CONSTRAIN RULE: Absolute Immutability Block
 * Financial compliance frameworks state that historical ledger data cannot be updated or deleted.
 * Errors are resolved by posting a new, reversing balancing transaction.
 */
CREATE OR REPLACE FUNCTION protect_ledger_immutability()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'Immutability Violation: Changing or deleting historical ledger lines is strictly forbidden.';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_immutable_entries BEFORE UPDATE OR DELETE ON ledger_entries FOR EACH ROW EXECUTE FUNCTION protect_ledger_immutability();
CREATE TRIGGER trg_immutable_transactions BEFORE UPDATE OR DELETE ON ledger_transactions FOR EACH ROW EXECUTE FUNCTION protect_ledger_immutability();


/*Calculating real-time account balances by scanning millions of rows using 
SUM(amount) will quickly degrade database performance as your transaction 
history grows.To solve this, use an asynchronously refreshed Materialized View acting 
as a high-performance cache layer, combined with a Snapshot Delta pattern 
to calculate real-time values instantly.
*/
-- ============================================================================
-- 6. MATERIALIZED VIEW CACHE LAYER (TENANT AGGREGATED) - PERFORMANCE BOOSTS
-- snapshot_balance is DEBIT-POSITIVE (right for ASSET/EXPENSE accounts).
-- For LIABILITY/EQUITY/REVENUE the "natural" balance is credits - debits,
-- i.e. -snapshot_balance; derive that from accounts.type when presenting.
CREATE MATERIALIZED VIEW mv_account_balances AS
SELECT
    tenant_id,
    account_id,
    SUM(debit)  AS total_debits,
    SUM(credit) AS total_credits,
    SUM(debit) - SUM(credit) AS snapshot_balance,
    MAX(created_at) AS last_calculated_at
FROM ledger_entries
GROUP BY tenant_id, account_id;

-- UNIQUE index is MANDATORY to allow "REFRESH MATERIALIZED VIEW CONCURRENTLY"
-- This prevents the view from locking out read requests while updating!
CREATE UNIQUE INDEX idx_mv_balances_tenant_account ON mv_account_balances (tenant_id, account_id);

-- Composite covering index to allow rapid balance extractions
-- CREATE INDEX idx_mv_balances_lookup ON mv_account_balances (account_id, snapshot_balance);
-- REFRESH MATERIALIZED VIEW CONCURRENTLY mv_account_balances;



-- ############################################################################
-- Reusable "look at the ledger" query: run it after any step.
-- ############################################################################
-- ============================================================================
-- MATERIALIZED VIEW OF LEDGER ENTRIES
CREATE MATERIALIZED VIEW mv_ledger_entries AS
SELECT
    t.tenant_id, 
    t.correlation_id,
    a.name AS account, 
    a.currency, 
    e.debit, 
    e.credit
FROM ledger_entries e
JOIN ledger_transactions t ON t.id = e.transaction_id
JOIN accounts a            ON a.id = e.account_id
ORDER BY t.created_at, t.correlation_id, e.debit DESC;

CREATE UNIQUE INDEX idx_mv_ledger_entries  ON mv_ledger_entries  (tenant_id, correlation_id, account);

REFRESH MATERIALIZED VIEW CONCURRENTLY mv_ledger_entries ;
SELECT * FROM mv_ledger_entries;


-- ============================================================================
-- MATERIALIZED VIEW OF TRIAL BALANCE
-- ...and a balance query. "balance" is the account's NATURAL balance:
--   ASSET/EXPENSE   = debits - credits
--   everything else = credits - debits      (so Equity shows POSITIVE after funding)
CREATE MATERIALIZED VIEW mv_trial_balance AS 
SELECT a.tenant_id, a.name, a.type, a.currency,
       COALESCE(SUM(e.debit), 0)  AS debits,
       COALESCE(SUM(e.credit), 0) AS credits,
       CASE WHEN a.type IN ('ASSET', 'EXPENSE')
            THEN COALESCE(SUM(e.debit), 0)  - COALESCE(SUM(e.credit), 0)
            ELSE COALESCE(SUM(e.credit), 0) - COALESCE(SUM(e.debit), 0)
       END AS balance
FROM accounts a
LEFT JOIN ledger_entries e ON e.account_id = a.id
GROUP BY a.id
ORDER BY a.tenant_id, a.name;

CREATE UNIQUE INDEX idx_mv_trial_balance  ON mv_trial_balance  (tenant_id, name);

REFRESH MATERIALIZED VIEW CONCURRENTLY mv_trial_balance ;
SELECT * FROM mv_trial_balance;


/*
account_balance_projection
Ran after every insert 
*/
CREATE TABLE account_balance_projection (
    tenant_id    VARCHAR(64) NOT NULL REFERENCES tenants(id),
    account_id   UUID        NOT NULL REFERENCES accounts(id),
    total_debits  BIGINT     NOT NULL DEFAULT 0,
    total_credits BIGINT     NOT NULL DEFAULT 0,
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (tenant_id, account_id)
);

CREATE OR REPLACE FUNCTION apply_entry_to_projection() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    INSERT INTO account_balance_projection (tenant_id, account_id, total_debits, total_credits)
    VALUES (NEW.tenant_id, NEW.account_id, NEW.debit, NEW.credit)
    ON CONFLICT (tenant_id, account_id) DO UPDATE
       SET total_debits  = account_balance_projection.total_debits  + EXCLUDED.total_debits,
           total_credits = account_balance_projection.total_credits + EXCLUDED.total_credits,
           updated_at    = now();
    RETURN NULL;
END $$;

CREATE TRIGGER trg_project_entry
AFTER INSERT ON ledger_entries
FOR EACH ROW EXECUTE FUNCTION apply_entry_to_projection();

/*
SELECT tenant_id, account_id, SUM(debit), SUM(credit) FROM ledger_entries GROUP BY 1,2

SELECT p.* FROM account_balance_projection p 
JOIN 
(SELECT account_id, SUM(debit) d, SUM(credit) c FROM ledger_entries GROUP BY 1) 
 l USING (account_id) WHERE p.total_debits <> l.d OR p.total_credits <> l.c;
*/


/**

The Production "Real-Time Balance" Hybrid Query
While a materialized view is fast, it only reflects data up to the moment it was last refreshed. 
If a payment was made 1 second ago, a stale view won't see it.
To achieve sub-millisecond real-time accuracy, the query below reads the fast snapshot balance 
and adds any new delta entries written after the snapshot timestamp. 
This allows you to inspect millions of historical lines instantly 
while maintaining real-time accuracy down to the millisecond

CREATE OR REPLACE FUNCTION get_real_time_balance(p_account_id UUID)
RETURNS BIGINT AS $$
DECLARE
    v_snapshot_balance BIGINT;
    v_last_snapshot_time TIMESTAMP WITH TIME ZONE;
    v_delta_balance BIGINT;
BEGIN
    -- 1. Grab the cached snapshot values
    SELECT snapshot_balance, last_entry_at
    INTO v_snapshot_balance, v_last_snapshot_time
    FROM mv_account_balances
    WHERE account_id = p_account_id;

    -- Handle case where account hasn't been snapshotted yet
    IF NOT FOUND THEN
        v_snapshot_balance := 0;
        v_last_snapshot_time := '1970-01-01 00:00:00+00'::TIMESTAMPTZ;
    END IF;

    -- 2. Calculate only the tiny delta since the last snapshot time (Extremely Fast)
    SELECT COALESCE(SUM(amount), 0)
    INTO v_delta_balance
    FROM ledger_entries
    WHERE account_id = p_account_id
      AND created_at > v_last_snapshot_time;

    -- 3. Return the absolute up-to-the-millisecond balance
    RETURN v_snapshot_balance + v_delta_balance;
END;
$$ LANGUAGE plpgsql STABLE;


Automated Refresh Orchestration
To keep the materialized view background cache fresh without slowing down your active web requests, 
you can refresh it concurrently. This means the view updates in the background without locking your
 tables or blocking user API reads.
How to Refresh Concurrently (SQL Command)Run this command periodically 
via a background worker or cron utility (e.g., every 5 to 15 minutes):
REFRESH MATERIALIZED VIEW CONCURRENTLY mv_account_balances;

Automated Database-Level Trigger (Optional alternative for lower volume)
If your ledger does not process hundreds of transactions per second, 
you can automate updates using a database trigger rule that refreshes 
every N transactions

CREATE OR REPLACE FUNCTION schedule_ledger_refresh()
RETURNS TRIGGER AS $$
BEGIN
    -- For production high-volume, I will call this via pg_cron or Spring Boot @Scheduled instead
    -- to avoid adding overhead to the core transaction path.
    REFRESH MATERIALIZED VIEW CONCURRENTLY mv_account_balances;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_refresh_balances_layer
AFTER INSERT ON ledger_entries
REFERENCING NEW TABLE AS new_entries
FOR EACH STATEMENT
EXECUTE FUNCTION schedule_ledger_refresh();
*/