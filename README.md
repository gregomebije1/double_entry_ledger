# Multi-Tenant Double-Entry Ledger Core

An immutable financial system of record operating as a multi-tenant kernel. This service has zero visibility into user identities or banking configurations; it strictly verifies that multi-line transaction arrays satisfy the zero-sum accounting rule (\(\sum \text{Debits} - \sum \text{Credits} = 0\)).

## Core System Requirements

### 1. Functional Requirements
* **Strict Double-Entry Enforcement:** Automatically aborts and rolls back any transaction modification block that fails to balance precisely to zero.
* **Multi-Stage Balance Isolation (Holds):** Supports native transaction holds, moving funds from `available_balance` to `locked_balance` to freeze resources during cross-network execution safely.
* **Append-Only Immutability:** Enforces a structural write path where row changes are forbidden. Any financial adjustments require generating an independent balancing row entry.

### 2. Non-Functional Distributed Requirements
* **Strong Linearizable Consistency:** Employs database topologies (e.g., CockroachDB running Raft consensus) to guarantee strict serializable isolation levels across horizontally scaled database shards. Read-after-write operations must reflect the exact global state.
* **Network Partition Tolerance (CAP Theorem Selection):** Chooses Consistency over Availability (CP) during split-brain routing scenarios. Nodes that lose heartbeat connection to the Raft consensus quorum must instantly decline incoming write operations to block balance manipulation.
* **Zero-Knowledge Multi-Tenancy Segregation:** Access is governed via unique cryptographic tenant signing keys. Row-level policies automatically segment execution paths, ensuring `Tenant A` cannot read, write, or infer the existence of accounts owned by `Tenant C`.
* **Fixed-Point Precision Math:** Balances are processed and stored exclusively as 64-bit integers in minor units (e.g., cents) to eliminate floating-point precision rounding errors across distributed runtimes.
* **Cryptographic Ledger Chaining:** Chains transaction entries using a cryptographic SHA-256 HMAC hash block that incorporates the prior entry's signature, turning database tables into an easily auditable tamper-evident record.

## Essential Core API Endpoints

### 1. Create Segregated Tenant Account
* **Endpoint:** `POST /api/v1/ledgers/accounts`
* **Headers:** `Authorization: Bearer <tenant_private_signing_key>`
* **Payload:**
```json
{
  "account_id": "partition_a_wallet_usr_8821",
  "currency": "USD"
}
```

### 2. Post Balanced Ledger Lines
* **Endpoint:** `POST /api/v1/ledgers/transactions`
* **Headers:** `Authorization: Bearer <tenant_private_signing_key>`
* **Payload:**
```json
{
  "narration": "Tenant Account P2P Value Reallocation",
  "lines": [
    { "account_id": "partition_a_wallet_usr_8821", "type": "DEBIT", "amount": 2500 },
    { "account_id": "partition_a_wallet_usr_9934", "type": "CREDIT", "amount": 2500 }
  ]
}
```


## Architecture & Key Standards

### 1. Double-Entry Ledger Engine
To ensure compliance and auditability, all internal financial updates are treated as immutable rows following standard ledger constraints:
$$\sum \text{Debits} - \sum \text{Credits} = 0$$


---

## Testing Strategy

### 1. Unit Testing
*   **Ledger Validation:** Validates that balance updates with mismatching debits and credits throw an explicit `UnbalancedLedgerException` entirely within memory, preventing any database traffic.

### 2. Database Ledger Integrity Verification
*   Executes systematic validation checks across all rows in the journal table to ensure zero variance across the ecosystem.

```sql
-- Must evaluate to exactly 0 to pass audit verification
SELECT SUM(amount) FROM journal_entries;
```

# Useful commands

docker compose up -d 
docker compose down -v

./mvnw test
./mvnw spring-boot:run

./mvnw clean test-compile

==== 
To build a production-grade General Ledger in PostgreSQL, you must treat your database as an immutable write-only transaction log. In financial ledger engineering, balances are never overwritten using an UPDATE statement. Instead, a balance is an aggregate sum calculated over an unchanging history of balancing debits and credits.This schema implements a bulletproof Double-Entry Accounting Architecture using PostgreSQL-native triggers and constraints to guarantee that the absolute core rule of accounting—every transaction must balance exactly to zero—is enforced directly by the engine before a single byte hits the disk.Core Structural ArchitectureTo minimize data corruption, this schema separates transactions into two tables using a Header/Line pattern:ledger_transactions: Contains the structural header metadata (who, what, when, idempotency tracking).ledger_entries: Contains the actual monetary movements (the individual debit and credit distribution splits). ┌─────────────────────────────┐
 │    ledger_transactions      │
 ├─────────────────────────────┤
 │ id (PK)                     │
 │ correlation_id (Idempotency)│◄───┐
 └──────────────┬──────────────┘    │ Enforces atomic header metadata
                │ 1                 │
                ├───┐               │
                │   │ M             │
                ▼   ▼               │
 ┌─────────────────────────────┐    │
 │       ledger_entries        │    │
 ├─────────────────────────────┤    │
 │ id (PK)                     │    │
 │ transaction_id (FK)         ├────┘ Enforces balanced ledger splits
 │ account_id (FK)             │      (SUM(amount) = 0)
 │ amount (Minor unit integer) │
 └─────────────────────────────┘
Complete Production PostgreSQL DDL Scriptsql-- ============================================================================
-- 1. BASE LAYER ENUMS & EXTENSIONS
-- ============================================================================
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Restricts account behaviors based on traditional accounting classifications
CREATE TYPE account_type AS ENUM ('ASSET', 'LIABILITY', 'EQUITY', 'REVENUE', 'EXPENSE');

-- ============================================================================
-- 2. CORE SCHEMAS AND TABLES
-- ============================================================================

-- Accounts Table: Defines the Chart of Accounts (COA)
CREATE TABLE accounts (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    account_number VARCHAR(64) UNIQUE NOT NULL,
    name VARCHAR(255) NOT NULL,
    type account_type NOT NULL,
    currency VARCHAR(3) NOT NULL, -- ISO 4217 Currency Code (e.g., 'GBP')
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Ledger Transactions Table: The immutable header block
CREATE TABLE ledger_transactions (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    description VARCHAR(512) NOT NULL,
    posted_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    -- IDEMPOTENCY GUARD: Prevents duplicate Kafka message processing from creating multiple entries
    correlation_id VARCHAR(255) UNIQUE NOT NULL,
    
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Ledger Entries Table: The transactional lines (Splits)
CREATE TABLE ledger_entries (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    transaction_id UUID NOT NULL REFERENCES ledger_transactions(id) ON DELETE RESTRICT,
    account_id UUID NOT NULL REFERENCES accounts(id) ON DELETE RESTRICT,
    
    -- MINOR UNITS REQUIREMENT: Stored as a signed integer (cents/pence). 
    -- Positives are DEBITS, Negatives are CREDITS. Zero value is strictly rejected.
    amount BIGINT NOT NULL CHECK (amount <> 0),
    
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ============================================================================
-- 3. PRODUCTION-GRADE PERFORMANCE INDEXING (DDIA Chapter 3)
-- ============================================================================

-- Speeds up financial statement compilation and ledger reconciliation lookups
CREATE INDEX idx_ledger_entries_account_date 
ON ledger_entries (account_id, created_at DESC);

-- Speeds up transaction history inspections
CREATE INDEX idx_ledger_entries_transaction_id 
ON ledger_entries (transaction_id);

-- ============================================================================
-- 4. INTEGRITY RULES & TRIGGER DEFENCIES
-- ============================================================================

/**
 * CONSTRAINT RULE: Zero-Sum Balancing Guarantee
 * This deferred trigger verifies that the sum of all entry amounts inside a
 * transaction equals EXACTLY zero before the transaction is permanently committed.
 */
CREATE OR REPLACE FUNCTION verify_ledger_transaction_balances()
RETURNS TRIGGER AS $$
DECLARE
    v_transaction_balance BIGINT;
    v_entry_count INT;
BEGIN
    -- Sum up the lines for the target transaction ID
    SELECT SUM(amount), COUNT(*)
    INTO v_transaction_balance, v_entry_count
    FROM ledger_entries
    WHERE transaction_id = NEW.transaction_id;

    -- Financial Validation 1: A transaction cannot be empty
    IF v_entry_count < 2 THEN
        RAISE EXCEPTION 'Ledger transaction % must contain at least 2 entries for double-entry bookkeeping.', NEW.transaction_id;
    END IF;

    -- Financial Validation 2: Sum of Debits (+) and Credits (-) must cancel out to 0
    IF v_transaction_balance <> 0 THEN
        RAISE EXCEPTION 'Ledger transaction % is unbalanced. Sum total is % minor units (Must equal 0).', 
            NEW.transaction_id, v_transaction_balance;
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

CREATE TRIGGER trg_immutable_entries
BEFORE UPDATE OR DELETE ON ledger_entries
FOR EACH ROW
EXECUTE FUNCTION protect_ledger_immutability();

CREATE TRIGGER trg_immutable_transactions
BEFORE UPDATE OR DELETE ON ledger_transactions
FOR EACH ROW
EXECUTE FUNCTION protect_ledger_immutability();
Use code with caution.How to insert a balanced transaction cleanlyBecause the trigger is configured as DEFERRABLE INITIALLY DEFERRED, PostgreSQL allows your application to insert individual entries one at a time. The database only checks the balance at the final step when you send a COMMIT statement.sqlBEGIN;

-- 1. Create the Transaction Header with an Idempotency Correlation ID
INSERT INTO ledger_transactions (id, description, correlation_id) 
VALUES ('d6854cb0-e547-49f3-af8e-8cf40eb37452', 'P2P Wallet Transfer Ref: #1024', 'kafka_msg_offset_89234');

-- 2. Debit the Sender's Asset Account (Deducting £50.00 -> -5000 credits)
INSERT INTO ledger_entries (transaction_id, account_id, amount)
VALUES ('d6854cb0-e547-49f3-af8e-8cf40eb37452', 'user_1_wallet_account_uuid', -5000);

-- 3. Credit the Receiver's Asset Account (Adding £50.00 -> +5000 debits)
INSERT INTO ledger_entries (transaction_id, account_id, amount)
VALUES ('d6854cb0-e547-49f3-af8e-8cf40eb37452', 'user_2_wallet_account_uuid', 5000);

-- The trigger evaluates here: -5000 + 5000 = 0. Commit succeeds!
COMMIT;
Use code with caution.Try pushing an unbalanced entry to test the security constraint:sqlBEGIN;

INSERT INTO ledger_transactions (id, description, correlation_id) 
VALUES ('fa4340d2-9705-4c07-ba71-fa58197bf4a0', 'Malicious Hack Attack', 'bad_actor_999');

-- Attempt to mint £1,000 out of thin air without a matching debit/credit counterweight
INSERT INTO ledger_entries (transaction_id, account_id, amount)
VALUES ('fa4340d2-9705-4c07-ba71-fa58197bf4a0', 'hacker_wallet_account_uuid', 100000);

-- The engine evaluates here and blocks the insertion automatically!
COMMIT; 
-- ERROR: Ledger transaction fa4340d2-9705-4c07-ba71-fa58197bf4a0 is unbalanced. 
-- Sum total is 100000 minor units (Must equal 0).
Use code with caution.

======
Calculating real-time account balances by scanning millions of rows using SUM(amount) will quickly degrade database performance as your transaction history grows.To solve this, production financial platforms use an asynchronously refreshed Materialized View acting as a high-performance cache layer, combined with a Snapshot Delta pattern to calculate real-time values instantly.Here is the complete production SQL architecture script to set up high-performance balance caching in PostgreSQL.1. The High-Performance Materialized View ScriptThis script builds a materialized view that snapshots account totals, paired with localized composite indexes designed for index-only scans.sql-- ============================================================================
-- 1. DEFINE THE MATERIALIZED VIEW (THE BALANCE SNAPSHOT)
-- ============================================================================
CREATE MATERIALIZED VIEW mv_account_balances AS
SELECT 
    account_id,
    -- Summing all minor units up to the snapshot execution moment
    SUM(amount) AS snapshot_balance,
    MAX(created_at) AS last_entry_at,
    COUNT(*) AS total_entries_processed
FROM ledger_entries
GROUP BY account_id;

-- ============================================================================
-- 2. CRITICAL INDEX FOR HIGH-CONCURRENCY REFRESHES & LOOKUPS
-- ============================================================================

-- UNIQUE index is MANDATORY to allow "REFRESH MATERIALIZED VIEW CONCURRENTLY"
-- This prevents the view from locking out read requests while updating!
CREATE UNIQUE INDEX idx_mv_account_balances_id 
ON mv_account_balances (account_id);

-- Composite covering index to allow rapid balance extractions
CREATE INDEX idx_mv_balances_lookup 
ON mv_account_balances (account_id, snapshot_balance);
Use code with caution.2. The Production "Real-Time Balance" Hybrid QueryWhile a materialized view is fast, it only reflects data up to the moment it was last refreshed. If a payment was made 1 second ago, a stale view won't see it.To achieve sub-millisecond real-time accuracy, the query below reads the fast snapshot balance and adds any new delta entries written after the snapshot timestamp. This allows you to inspect millions of historical lines instantly while maintaining real-time accuracy down to the millisecond.sqlCREATE OR REPLACE FUNCTION get_real_time_balance(p_account_id UUID)
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
Use code with caution.3. Automated Refresh OrchestrationTo keep the materialized view background cache fresh without slowing down your active web requests, you can refresh it concurrently. This means the view updates in the background without locking your tables or blocking user API reads.How to Refresh Concurrently (SQL Command)Run this command periodically via a background worker or cron utility (e.g., every 5 to 15 minutes):sqlREFRESH MATERIALIZED VIEW CONCURRENTLY mv_account_balances;
Use code with caution.Automated Database-Level Trigger (Optional alternative for lower volume)If your ledger does not process hundreds of transactions per second, you can automate updates using a database trigger rule that refreshes every N transactions:sqlCREATE OR REPLACE FUNCTION schedule_ledger_refresh()
RETURNS TRIGGER AS $$
BEGIN
    -- For production high-volume, call this via pg_cron or Spring Boot @Scheduled instead
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
Use code with caution.

=============

=====
package com.gregomebije.ledger;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

import com.gregomebije.ledger.model.*;
import com.gregomebije.ledger.enumtype.*;
import com.gregomebije.ledger.exception.*;

@SpringBootTest // Boots the entire Spring ApplicationContext
@Testcontainers // Enables Testcontainers lifecycle management
class LedgerValidatorTest {

    // Automatically spins up a real PostgreSQL Docker container for this test class
    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    // Spring can now inject your components because the application context is active
    @Autowired
    private LedgerValidator validator;

    @Test
    void balancedDebitAndCreditShouldBeAccepted() {
        var lines = List.of(
                new LedgerLine("account-a", LedgerLineType.DEBIT, 2500L),
                new LedgerLine("account-b", LedgerLineType.CREDIT, 2500L)
        );

        assertDoesNotThrow(() -> validator.validate(lines));
    }

    @Test
    void multipleDebitsAndCreditsShouldBalance() {
        var lines = List.of(
                new LedgerLine("account-a", LedgerLineType.DEBIT, 1000L),
                new LedgerLine("account-b", LedgerLineType.DEBIT, 1500L),
                new LedgerLine("account-c", LedgerLineType.CREDIT, 2500L)
        );

        assertDoesNotThrow(() -> validator.validate(lines));
    }

    @Test
    void unbalancedLedgerShouldThrowExplicitException() {
        var lines = List.of(
                new LedgerLine("account-a", LedgerLineType.DEBIT, 2500L),
                new LedgerLine("account-b", LedgerLineType.CREDIT, 2499L)
        );

        var exception = assertThrows(
                UnbalancedLedgerException.class,
                () -> validator.validate(lines)
        );

        assertTrue(exception.getMessage().contains("1"));
    }

    @Test
    void debitOnlyLedgerShouldBeRejected() {
        var lines = List.of(
                new LedgerLine("account-a", LedgerLineType.DEBIT, 1000L)
        );

        assertThrows(
                UnbalancedLedgerException.class,
                () -> validator.validate(lines)
        );
    }

    @Test
    void creditOnlyLedgerShouldBeRejected() {
        var lines = List.of(
                new LedgerLine("account-a", LedgerLineType.CREDIT, 1000L)
        );

        assertThrows(
                UnbalancedLedgerException.class,
                () -> validator.validate(lines)
        );
    }

    @Test
    void emptyLedgerShouldBeRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> validator.validate(List.of())
        );
    }

    @Test
    void negativeAmountShouldBeRejected() {
        var lines = List.of(
                new LedgerLine("account-a", LedgerLineType.DEBIT, -100L),
                new LedgerLine("account-b", LedgerLineType.CREDIT, -100L)
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> validator.validate(lines)
        );
    }

    @Test
    void zeroAmountShouldBeRejected() {
        var lines = List.of(
                new LedgerLine("account-a", LedgerLineType.DEBIT, 0L),
                new LedgerLine("account-b", LedgerLineType.CREDIT, 0L)
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> validator.validate(lines)
        );
    }

    @Test
    void LongMaxValueShouldBeHandledWithoutFloatingPointMath() {
        var lines = List.of(
                new LedgerLine("a", LedgerLineType.DEBIT, Long.MAX_VALUE),
                new LedgerLine("b", LedgerLineType.CREDIT, Long.MAX_VALUE)
        );

        assertDoesNotThrow(() -> validator.validate(lines));
    }

    @Test
    void balancingShouldUseMinorUnitsAsIntegers() {
        var lines = List.of(
                new LedgerLine("a", LedgerLineType.DEBIT, 1L),
                new LedgerLine("b", LedgerLineType.CREDIT, 1L)
        );

        assertDoesNotThrow(() -> validator.validate(lines));
    }
}
====
# Distributed Financial Systems Architecture: Accounting Ledger Manual

This manual defines the production-grade Chart of Accounts (COA) and Double-Entry Journal Entries for two tightly decoupled systems:

- **The E-Wallet Orchestrator Engine:** A customer-facing, assetless pass-through engine that leases balances, holds no physical cash custody, and delegates funding/payout actions to an external gateway.
- **The Payment Gateway Clearing Engine:** An infrastructure pipeline that manages incoming processor receivables, calculates basis-point transaction fee splits, and drives merchant settlements.

---

## Part 1: The E-Wallet Orchestrator Engine

The E-Wallet engine serves as a customer-facing transactional interface. Because it holds no direct custody of the underlying cash assets, its ledger focuses on managing receivable claims against the external gateway and tracking user balances as platform liabilities.

### 1. Chart of Accounts (COA)

| Account Number | Account Name | Type | Description |
|---|---|---|---|
| 1210 | Gateway Funding Receivables | Asset | Floating claims against the external gateway for incoming customer wallet deposits. |
| 1220 | Gateway Payout Clearing | Asset | Transient tracking lane for outbound customer bank withdrawals routed through the gateway. |
| 1230 | Settlement Bank Account | Asset | Corporate bank account where the gateway deposits bulk payouts. |
| 2210 | User Wallet Balances (Liability Pool) | Liability | The digital ledger balance leased to the user, reflecting their available spending power. |
| 2220 | Saga Transaction Holds | Liability | Temporary escrow pool holding user funds during active multi-network workflows (e.g., utility purchases). |
| 4210 | Commission & Markup Revenue | Revenue | Margins earned by adding a convenience fee on top of the gateway's base pricing. |
| 5210 | Gateway Processing Expense | Expense | Interchange and processing costs deducted by the external provider. |

### 2. Core Transactional Flows & Journal Entries

#### Flow A: Multi-Channel Wallet Funding (Incoming Deposit)

A user initiates a $100.00 deposit via credit card inside the wallet app. The wallet engine charges the user a $3.00 convenience fee margin on top of the transaction.

**Step 1: Upon Webhook Ingress Affirmation** *(Customer's card is successfully charged by the gateway)*

| Entry | Account | Amount | Effect |
|---|---|---|---|
| Debit | 1210 – Gateway Funding Receivables | +$103.00 | Asset increases: The gateway now owes you the deposit + your margin |
| Credit | 2210 – User Wallet Balances | +$100.00 | Liability increases: User's available digital balance is topped up |
| Credit | 4210 – Commission & Markup Revenue | +$3.00 | Revenue increases: Your platform fee margin is recognized |

**Step 2: Gateway Bulk Settlement** *(Occurs 24–48 hours later)*

The external gateway takes its own processing fee cut ($1.50) and deposits the remaining net balance ($101.50) into your corporate settlement bank account.

| Entry | Account | Amount | Effect |
|---|---|---|---|
| Debit | 1230 – Settlement Bank Account | +$101.50 | Asset increases: Physical cash hits your actual corporate bank |
| Debit | 5210 – Gateway Processing Expense | +$1.50 | Expense increases: The gateway's fee cut is recorded |
| Credit | 1210 – Gateway Funding Receivables | -$103.00 | Asset decreases: Clears out your pending claim against the gateway |

> **Net platform profit tracked on this transaction block:** $3.00 markup revenue − $1.50 gateway expense = **$1.50 clear margin**.

---

#### Flow B: Peer-to-Peer (P2P) Internal Transfer

User 1 transfers $25.00 to User 2 within the wallet ecosystem. Because this action is entirely internal and does not involve external banking movements, no asset accounts are altered.

| Entry | Account | Amount | Effect |
|---|---|---|---|
| Debit | 2210 – User Wallet Balances (User 1) | -$25.00 | Liability decreases: Platform liability to User 1 decreases |
| Credit | 2210 – User Wallet Balances (User 2) | +$25.00 | Liability increases: Platform liability to User 2 increases |

---

#### Flow C: Value-Added Service (VAS) Utility Purchase

A user purchases an airtime data subscription for $10.00.

**Step 1: Saga Orchestrator Initialization** *(Place a hold on funds to prevent double-spending race conditions)*

| Entry | Account | Amount | Effect |
|---|---|---|---|
| Debit | 2210 – User Wallet Balances | -$10.00 | Deducted from the user's active spending lane |
| Credit | 2220 – Saga Transaction Holds | +$10.00 | Moved into a temporary escrow liability lock |

**Step 2: Third-Party Utility API returns a success payload**

The hold is released and the external gateway is instructed to pay out the utility vendor from your corporate settlement account.

| Entry | Account | Amount | Effect |
|---|---|---|---|
| Debit | 2220 – Saga Transaction Holds | -$10.00 | The internal transaction lock is cleared |
| Credit | 1220 – Gateway Payout Clearing | +$10.00 | Asset decreases: Your clearing account tracks that cash is leaving the system |

---

## Part 2: The Payment Gateway Clearing Engine

The Payment Gateway operates as a clearing broker for merchant tenants. It interfaces directly with card networks and acquiring banks, processing gross receivables, extracting platform processing fees, and managing payout corridors.

### 1. Chart of Accounts (COA)

| Account Number | Account Name | Type | Description |
|---|---|---|---|
| 1110 | Processor Receivables (In-Transit) | Asset | Funds captured by card networks but not yet deposited into the gateway's bank. |
| 1120 | Settlement Bank Account | Asset | Corporate bank account used to receive network settlements and dispatch payouts. |
| 2110 | Merchant Settlement Payables | Liability | Net funds owed to your merchant tenants that are available for withdrawal. |
| 4110 | Gateway Processing Fee Revenue | Revenue | Gross margins earned by the platform from your variable/fixed transaction fee splits. |
| 5110 | Interchange/Processor Expense | Expense | Upstream interchange fees charged by core Visa/Mastercard card networks or card factories. |

### 2. Core Transactional Flows & Journal Entries

#### Flow A: A Customer Pays a Merchant $100.00

The customer buys a product from a merchant using your gateway proxy. Your automated fee engine calculates a $2.80 platform processing fee split.

| Entry | Account | Amount | Effect |
|---|---|---|---|
| Debit | 1110 – Processor Receivables (In-Transit) | +$100.00 | Asset increases: Core network card processor owes you the gross transaction amount |
| Credit | 2110 – Merchant Settlement Payables | +$97.20 | Liability increases: Net balance owed to your merchant tenant |
| Credit | 4110 – Gateway Processing Fee Revenue | +$2.80 | Revenue increases: Your platform's gross processing fee margin |

---

#### Flow B: Card Network Settles Cash into the Gateway Account

The card network settles the transaction, deducting an upstream interchange cost of $1.10 before moving the cash into your account.

| Entry | Account | Amount | Effect |
|---|---|---|---|
| Debit | 1120 – Settlement Bank Account | +$98.90 | Asset increases: Cash physically received from the processor |
| Debit | 5110 – Interchange/Processor Expense | +$1.10 | Expense increases: Cost deducted by the upstream processing card bank |
| Credit | 1110 – Processor Receivables (In-Transit) | -$100.00 | Asset decreases: In-transit clearing balance resets to zero |

> **Net platform profit for this specific lifecycle:** $2.80 Gross Revenue − $1.10 Interchange Expense = **$1.70 net margin**.

---

#### Flow C: Asynchronous Payout Dispatched to Merchant Account

The merchant triggers a withdrawal request to transfer their settled earnings ($97.20) out of the gateway into their local corporate business bank account.

| Entry | Account | Amount | Effect |
|---|---|---|---|
| Debit | 2110 – Merchant Settlement Payables | -$97.20 | Liability decreases: The platform has cleared its debt obligation to the merchant |
| Credit | 1120 – Settlement Bank Account | -$97.20 | Asset decreases: Cash leaves your settlement bank account |

---

## Part 3: Automated Three-Way Reconciliation

Because the E-Wallet engine relies on external systems, it uses an asynchronous background worker loop to perform Three-Way Reconciliation. It matches internal saga execution logs against incoming gateway transaction files and actual bank data feeds.

```
┌───────────────────────────────┐        ┌───────────────────────────────┐
│     Saga Orchestration Logs   │        │   Gateway Settlement Export   │
│   (Internal Intent Register)  │        │   (External API / CSV Feed)   │
└───────────────┬───────────────┘        └───────────────┬───────────────┘
                │                                         │
                └───────────────────┬─────────────────────┘
                                     ▼
                     ┌──────────────────────────────┐
                     │  Automated Reconciler Engine  │
                     └──────────────┬───────────────┘
                                     │
                                     ▼
                     ┌──────────────────────────────┐
                     │    Bank Statement Feed        │
                     │     (MT940 / BAI2 Format)     │
                     └──────────────────────────────┘
```

The background thread executes this reconciliation process by running a high-performance filtering query to identify discrepancy anomalies where internal ledger intents do not match external reality records:

```sql
SELECT 
    sol.id AS saga_id,
    sol.trace_id,
    sol.amount AS internal_expected_amount,
    wfi.amount AS gateway_intended_amount,
    wfi.provider_intent_id AS external_reference,
    (sol.amount - wfi.amount) AS balance_discrepancy_delta
FROM saga_orchestration_logs sol
JOIN wallets w ON sol.wallet_id = w.id
JOIN wallet_funding_intents wfi ON wfi.wallet_id = w.id
WHERE sol.status = 'COMPLETED'
  AND wfi.status = 'SUCCESS'
  -- Flag an exception anomaly if the internal ledger does not match gateway intent data
  AND sol.amount <> wfi.amount;
```

If the query returns data rows, the automated workflow flags those transaction records as `DISCREPANCY_LOCKED` and sends them to a manual audit review stream, ensuring zero data drift passes into your system.
===================
Scalling a springboot application on AWS
========
To scale a Spring Boot application on AWS, you must transition to a stateless architecture and deploy using horizontal scaling mechanisms.
1. Compute & Architecture
- AWS ECS (Fargate): Containerize the application and use Application Auto Scaling to automatically adjust task counts based on CPU or memory usage.
- AWS EKS: Deploy as containers and scale using the Horizontal Pod Autoscaler (HPA) based on custom CloudWatch metrics.
- Amazon EC2 ASG: Place virtual servers in an Auto Scaling Group with target tracking policies to automatically add or remove instances.

2. Traffic Routing
- Application Load Balancer (ALB): Route incoming traffic evenly across your scaled instances.
- Health Checks: Configure the ALB to monitor the /actuator/health endpoint to ensure traffic only reaches healthy nodes.

3. State Management
- Stateless Services: Remove local session storage so any instance can handle any incoming request.
- Distributed Sessions: Use Spring Session Redis backed by Amazon ElastiCache to manage user sessions externally.
- Distributed Caching: Replace in-memory caches with a shared Amazon ElastiCache cluster.

4. Database Optimization
- Read Replicas: Use Amazon RDS Read Replicas to offload read traffic from the primary database instance
- Connection Pooling: Optimize HikariCP limits in application.properties so scaled instances do not overwhelm database connection limits.

5. Startup Optimization
- GraalVM Native Images: Compile the application to a native executable using Spring Native to slash boot times from seconds to milliseconds.
- Lightweight Containers: Use minimal base images to reduce deployment time when AWS provisions new instances.

=====
For a distributed financial system, one should go for  CP Systems (Consistency + Partition tolerance). This means
* Reject requests during partitions
* Prefer correctness over uptime

There is a tendency of a split brain problem. if partitioned nodes both accept writes:
* Each side develops a different history
* State diverges
* Extremely difficult to reconcile
