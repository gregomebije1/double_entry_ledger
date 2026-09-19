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
