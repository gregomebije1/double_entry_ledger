# Multi-Tenant Double-Entry Ledger Core

A backend engineering project exploring how to enforce financial correctness at the data layer using **double-entry accounting, immutable journal entries, transaction boundaries, and tenant isolation**.

## Problem

Financial systems must preserve a consistent record of every movement of value. A partial write, unbalanced transaction, duplicate request, or overwritten historical entry can compromise balances and make reconciliation difficult.

This project models a ledger service that records monetary movements as balanced debit and credit entries rather than treating a mutable balance field as the source of truth.

## Invariants

### 1. Double-entry accounting - Every posted transaction balances

Within each transaction and currency, total debits must equal total credits. A transaction must never commit with missing, partial, or unbalanced journal entries:

$$
\forall c,\quad \sum \text{Debits}_{c} = \sum \text{Credits}_{c}
$$
where c = currency, otherwise, a USD debit could incorrectly balance against a GBP credit.

- enforced through a database trigger
### 2. Transactional integrity - A posting is atomic

A transaction uses a header-and-lines model:

- `ledger_transactions` stores transaction-level metadata and correlation or idempotency information.
- `ledger_entries` records the individual monetary movements associated with the transaction.

The transaction header, all journal lines, and the idempotency result commit together or roll back together.

### 3. Append-only financial history - Posted history is immutable

No editing or deleting posted journal lines. Corrections use new, linked reversal or adjustment transactions.
Historical entries should not be silently overwritten to correct a balance. Any financial adjustments require generating an independent balancing row entry.

### 4. Monetary precision

Amounts are represented in integer minor units, such as pounds, rather than binary floating-point values. This avoids common floating-point rounding errors in monetary arithmetic.

### 5. Holds and available balances - Money cannot be spent twice

The design considers separating available funds from locked funds. A hold reserves value during an operation that has not yet completed, reducing the risk of spending the same funds twice while an external workflow is in progress. Concurrent transfers must not collectively spend more available funds than an account holds. This requires a concurrency-control strategy, not just a balance check.

- serialization or locking

### 6. Tenant isolation - Tenant boundaries are enforced

The API model includes tenant-scoped access to ledger accounts and transactions. `Tenant A` cannot read, write, or infer the existence of accounts owned by `Tenant B`. Tenant identity and authorization must be enforced at the service and database boundaries, not merely assumed from an account identifier supplied by a caller.


### 7. Retries do not duplicate financial effects

Repeating a request with the same idempotency key and same payload returns the original result. Reusing that key with a different payload is rejected.

## Architecture 
REST API 
Authentication . validation . idempotency key
      |
      |
     \|/

Spring Boot Application
Account Service . posting service . hold service
Tenant context . authorization . domain rules
      |
      |  
     \|/
PostgreSQL
Constraints - row locks - atomic posting - immuatable journal
      |
      |
     \|/
Transactional Outbox
Commited events -> Kafka or another message broker
Notifications . projectsion .reconciliation consumers

## Tech choices
- Language and API: 	Java 21+  Spring Boot, Spring MVC
- Persistence: PpstgreSQL, Flyway, JDBC or jOOQ
- Transactions:	Spring @Transactional, PostgreSQL row locks
- Tenant security:	OAuth2 Resource Server, tenant-scoped authorization, PostgreSQL RLS
- Testing:	JUnit 5, Testcontainers, real PostgreSQL
- Retry safety:	Database-backed idempotency keys
- Asynchronous integration:	Transactional outbox; Kafka when needed
- Observability:	Micrometer, OpenTelemetry, structured logs
- Deployment:	Docker, managed PostgreSQL, migration-controlled releases

## DB Schema
/home/gregomebije/double_entry_ledger/src/main/resources/db/migration/V1__init_multitenant_ledger.sql


## API examples
### Register a Tenant/User-1
```
curl -X POST "http://localhost:8080/api/v1/onboarding/register" \
-H "Content-Type: application/json" \
-d '{
"tenantId": "tenant-1",
"tenantName": "Mangital ltd",
"adminEmail": "johndoe@mangital.com",
"adminPassword": "password",
"firstName": "John",
"lastName": "Doe"
}'
```

### Login tenant-1
```
curl -X POST "http://localhost:8080/api/v1/onboarding/login" \
-H "Content-Type: application/json" \
-d '{
"tenantId": "tenant-1",
"email": "johndoe@mangital.com",
"password": "password"
}'
```

### Create ledger account for tenant-1
```
curl -X POST "http://localhost:8080/api/v1/ledgers/accounts" \
-H "Authorization: Bearer eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJqb2huZG9lQG1hbmdpdGFsLmNvbSIsInRlbmFudF9pZCI6InRlbmFudC0xIiwiaWF0IjoxNzkzNTk0MzkxLCJleHAiOjE3OTM1OTc5OTF9.Jhi9DCvnpaYgMYjt3aYh3ZZ_V2R9qe-8BPDgAoeyMJw" \
-H "Content-Type: application/json" \
-H "Accept: application/json" \
-d '{"accountNumber": "1001", 
     "name": "Alice wallet",
     "type": "ASSET",
     "currency": "USD"}'


curl -X POST "http://localhost:8080/api/v1/ledgers/accounts" \
-H "Authorization: Bearer eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJqb2huZG9lQG1hbmdpdGFsLmNvbSIsInRlbmFudF9pZCI6InRlbmFudC0xIiwiaWF0IjoxNzkzNTk0MzkxLCJleHAiOjE3OTM1OTc5OTF9.Jhi9DCvnpaYgMYjt3aYh3ZZ_V2R9qe-8BPDgAoeyMJw" \
-H "Content-Type: application/json" \
-H "Accept: application/json" \
-d '{"accountNumber": "1002", 
     "name": "Bob wallet",
     "type": "ASSET",
     "currency": "USD"}'


curl -X POST "http://localhost:8080/api/v1/ledgers/accounts" \
-H "Authorization: Bearer eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJqb2huZG9lQG1hbmdpdGFsLmNvbSIsInRlbmFudF9pZCI6InRlbmFudC0xIiwiaWF0IjoxNzkzNTk0MzkxLCJleHAiOjE3OTM1OTc5OTF9.Jhi9DCvnpaYgMYjt3aYh3ZZ_V2R9qe-8BPDgAoeyMJw" \
-H "Content-Type: application/json" \
-H "Accept: application/json" \
-d '{"accountNumber": "3001", 
     "name": "Equity (USD)",
     "type": "EQUITY",
     "currency": "USD"}'


curl -X POST "http://localhost:8080/api/v1/ledgers/accounts" \
-H "Authorization: Bearer eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJqb2huZG9lQG1hbmdpdGFsLmNvbSIsInRlbmFudF9pZCI6InRlbmFudC0xIiwiaWF0IjoxNzkzNTk0MzkxLCJleHAiOjE3OTM1OTc5OTF9.Jhi9DCvnpaYgMYjt3aYh3ZZ_V2R9qe-8BPDgAoeyMJw" \
-H "Content-Type: application/json" \
-H "Accept: application/json" \
-d '{"accountNumber": "1003", 
     "name": "GBP wallet",
     "type": "ASSET",
     "currency": "GBP"}'

```



### Post a balanced transaction
curl -X POST "http://localhost:8080/api/v1/ledgers/transactions" \
-H "Authorization: Bearer eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJqb2huZG9lQG1hbmdpdGFsLmNvbSIsInRlbmFudF9pZCI6InRlbmFudC0xIiwiaWF0IjoxNzkzNjAxNzQ3LCJleHAiOjE3OTM2MDUzNDd9.QXacmoPJV7pE9BHLoqbTp8dhEuEG8CT5Uhn6mW3MO8g" \
-H "Content-Type: application/json" \
-H "Accept: application/json" \
-d '{
  "description": "Fund Alice",
  "lines": [
    {
      "accountId": "d880a0fe-ce74-4877-98f9-73dbb2ffe46b",
      "debit": 100000,
      "credit": 0
    },
    {
      "accountId": "40eb54c1-291b-4986-a51e-00bf2e99a40e",
      "debit": 0,
      "credit": 100000
    }
  ],
  "idempotencyKey": "fund-alice-1"
}'


6e4faa68-1982-410c-a47e-f53224e1bb5c

    
### Create ledger account for tenant-2
```
curl -X POST "http://localhost:8080/api/v1/onboarding/register" \
-H "Content-Type: application/json" \
-d '{
"tenantId": "tenant-2",
"tenantName": "Living Hope ltd",
"adminEmail": "janeme@livinghope.com.ng",
"adminPassword": "password",
"firstName": "Jane",
"lastName": "Me"
}'
```

### Login tenant-2
```
curl -X POST "http://localhost:8080/api/v1/onboarding/login" \
-H "Content-Type: application/json" \
-d '{
"tenantId": "tenant-2",
"email": "janeme@livinghope.com.ng",
"password": "password"
}'
```

### Create ledger account for tenant-2
```
curl -X POST "http://localhost:8080/api/v1/ledgers/accounts" \
-H "Authorization: Bearer eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJqYW5lbWVAbGl2aW5naG9wZS5jb20ubmciLCJ0ZW5hbnRfaWQiOiJ0ZW5hbnQtMiIsImlhdCI6MTc5MzU5NjcxNywiZXhwIjoxNzkzNjAwMzE3fQ.ExMg7Xk3UMkpuwQg1uX4dqSFnkudtTI0UysRLOYT5nk" \
-H "Content-Type: application/json" \
-H "Accept: application/json" \
-d '{"accountNumber": "1001", 
     "name": "B wallet",
     "type": "ASSET",
     "currency": "USD"}'


curl -X POST "http://localhost:8080/api/v1/ledgers/accounts" \
-H "Authorization: Bearer eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJqYW5lbWVAbGl2aW5naG9wZS5jb20ubmciLCJ0ZW5hbnRfaWQiOiJ0ZW5hbnQtMiIsImlhdCI6MTc5MzU5NjcxNywiZXhwIjoxNzkzNjAwMzE3fQ.ExMg7Xk3UMkpuwQg1uX4dqSFnkudtTI0UysRLOYT5nk" \
-H "Content-Type: application/json" \
-H "Accept: application/json" \
-d '{"accountNumber": "3001", 
     "name": "B equity",
     "type": "EQUITY",
     "currency": "USD"}'
```


# curl -X GET "http://localhost:8080/api/auth/verify-otp/0b48c1b9-af16-4ab7-ac48-37387a5c6ab7/824354"


Amounts in this example are expressed in minor units. The actual API contract and validation rules should be confirmed against the implementation.

## Testing and correctness

The testing strategy described in this project focuses on:

- Rejecting transactions whose debit and credit totals do not balance.
- Verifying that invalid operations do not leave partial database writes.
- Checking journal integrity across recorded entries.
- Exercising database-level constraints and transaction behaviour.

A useful integrity check must evaluate the debit and credit sides of each transaction independently. A global sum across all journal entries is not sufficient to prove that every individual transaction balances.

## Technology and implementation notes

The repository describes a Java/Spring Boot and PostgreSQL-oriented design. Database constraints and transaction boundaries are especially important because application-level validation alone cannot protect against every invalid write path.

## Engineering focus

This project demonstrates work on:

- Financial data modelling and accounting invariants.
- Transactional consistency and failure containment.
- Immutable audit history.
- Integer-based monetary arithmetic.
- API design for ledger operations.
- Testing correctness at application and database boundaries.

## Scope

This is a portfolio engineering project, not a claim of a certified or production-deployed banking ledger. 

## Commands

# Useful commands

docker compose up -d 
docker compose down -v
docker exec -it dev-postgres psql -U postgres
\c double_entry_ledger

psql -h localhost -p 5432 -U postgres


----
./mvnw clean package -DskipTests
----

./mvnw test
./mvnw spring-boot:run
./mvnw spring-boot:run -DskipTests

./mvnw clean test-compile


