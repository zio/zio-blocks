---
id: adr-001-data-migration
title: "ADR-001: SQL Data Migration Architecture"
status: accepted
date: 2026-07-18
amended: 2026-07-20
---

# ADR-001: SQL Data Migration Architecture

## Status

Accepted (amended 2026-07-20 by PR #1534)

## Context

The `zio-blocks` library needed an online data migration system that allows evolving database schemas from application code. The system builds on two existing foundations:

- `zio.blocks.schema.migration.Migration[A, B]` for typed schema evolution
- `zio.blocks.sql.Repo[E, ID]` for database access

Key requirements:

- No hand-written SQL in migrations
- No XML or Liquibase-style migration files
- Support for both PostgreSQL and SQLite
- Safe cutover for large tables with zero data loss

The module is Scala 3 only.

## Decisions

### Decision 1: Three Execution Models

**Decision:** Provide three execution tiers, each suited to a different scale of change:

- **A-Tiny** (`TinyMigrator`): Simple startup DDL via `Transactor.transact`. For schema-only changes that are fast and non-blocking.
- **A-Small** (`SmallMigrator`): Queue-based batch worker for moderate tables. Lifecycle: `init()` then loop `processBatch()` then `complete()`.
- **B-Large** (`LargeMigrator`): Incremental worker with safe completion protocol. Lifecycle: `init()` then `fence()` then `drain()` then `complete()`.

**Rationale:** Different migration scales need different guarantees. Tiny is lightweight and runs at startup. Small is transactional and batch-oriented. Large has distributed coordination and a safe cutover protocol. A single execution model would either be too heavy for simple DDL changes or too weak for large-table migrations.

### Decision 2: Coalesced Dirty-Key Queue with Operation Tracking

**Decision:** Queue entries are `(id, op, payload)` triples. The `id` column stores the primary key of the affected source row. The `op` column (`TEXT NOT NULL DEFAULT 'I'`) records the operation type: `'I'` (insert), `'U'` (update), or `'D'` (delete). The `payload` column (`TEXT`, nullable) stores a JSON-serialized copy of the deleted row, captured only for `'D'` operations on PostgreSQL via `row_to_json(OLD.*)::text`. Entries are coalesced by primary key: multiple operations on the same row between processing cycles collapse into a single queue entry with the latest operation type and payload.

**Rationale:** The original design stored only `(migration_id, primary_key)` pairs with no operation type or payload. That design could not distinguish deletes from inserts or updates, and could not recover row data after the source row was gone. PR #1534 added `op` and `payload` columns so the worker can dispatch on operation type and, for deletes on PostgreSQL, deserialize the original row even though the source row no longer exists. The source table remains authoritative for `'I'` and `'U'` operations. Bounded queue storage is preserved since `payload` is only populated for deletes and only on dialects that support `row_to_json`.

*(Amended by PR #1534, 2026-07-20)*

### Decision 3: Source-Authoritative Replay with Op-Based Dispatch

**Decision:** Workers claim a dirty key and dispatch based on the operation type. For `'I'` and `'U'` entries, the worker rereads the source row (without taking a source-row lock), applies the `Migration[A, B]`, and upserts the result into the target. For `'D'` entries, the worker deserializes the stored payload JSON via `migration.sourceSchema.jsonCodec` to recover the V1 entity, applies the migration to produce the V2 identity, and then handles the delete according to the target strategy:

- **`TargetStrategy.InPlace`**: The source row is already gone, so the delete is a no-op.
- **`TargetStrategy.ShadowTable`**: The worker deletes the corresponding V2 row from the shadow table using the recovered identity.

If the source row is missing at processing time for an `'I'` or `'U'` entry (deleted between enqueue and dequeue), the entry is silently skipped since `findAll` returns only the rows that still exist. This is not treated as a delete.

**Rationale:** Prevents deadlock with concurrent source writers. The worker holds only the dirty-key claim, not the source row lock. A source writer can proceed without waiting for the worker to finish reading. The original design treated any missing source row as a delete, which was incorrect when the missing row was caused by a delete that happened between enqueue and dequeue (the worker had no way to distinguish a late-arriving delete from a row that was never there). Op-based dispatch with payload deserialization for deletes gives the worker enough information to handle each operation correctly.

*(Amended by PR #1534, 2026-07-20)*

### Decision 4: Target Strategy Orthogonality

**Decision:** `TargetStrategy` is a thin enum (`InPlace` | `ShadowTable(suffix)`) orthogonal to the execution model. ShadowTable uses `CREATE TABLE IF NOT EXISTS ... LIKE ... INCLUDING ALL` and a DDL swap at completion.

**Rationale:** Decouples "how to write" from "when to write." The same worker code works for both strategies. InPlace updates rows directly in the source table. ShadowTable writes to a separate table and swaps it in at cutover. Callers choose the strategy based on whether the schema change is backward compatible.

### Decision 5: Safe Completion Protocol (B-Large)

**Decision:** State machine: `Initialized -> Fenced -> Drained -> Completed`. Fenced state prevents new source writes. Drain processes remaining queue items. Complete swaps the shadow table.

**Rationale:** Guarantees zero data loss during cutover. Queue persistence across restarts enables crash recovery. State is checked at each transition with `require()` guards. The protocol ensures every row modified between `init()` and `complete()` is migrated, even if the application crashes and restarts mid-migration.

### Decision 6: PostgreSQL-Specific Features

**Decision:** `QueueTable.dequeue` uses `SELECT ... FOR UPDATE SKIP LOCKED` for concurrent workers on PostgreSQL. Shadow table uses `CREATE TABLE ... LIKE ... INCLUDING ALL`. DDL swap is atomic. PostgreSQL capture triggers use `row_to_json(OLD.*)::text` to serialize the full deleted row into the `payload` column for `'D'` operations. SQLite capture triggers record the `'D'` operation but do not populate `payload` since SQLite lacks `row_to_json`.

**Rationale:** PostgreSQL's row-level locking and DDL transactionality enable safe multi-worker migrations. `row_to_json` provides a zero-schema-effort way to capture the full row image at delete time, which the worker can later deserialize via the source schema's `jsonCodec`. SQLite uses `dequeueSQLite` without `SKIP LOCKED` since SQLite serializes writes via `BEGIN IMMEDIATE` (single-writer model). The dialect split is necessary because the two databases have fundamentally different concurrency models and JSON serialization capabilities.

*(Amended by PR #1534, 2026-07-20)*

### Decision 7: ID Type Flexibility

**Decision:** `LargeMigrator` and `SmallMigrator` accept separate `ID1` and `ID2` type parameters, allowing V1 and V2 repos to use different ID types. The queue stores V1 IDs.

**Rationale:** A migration might change the primary key type (for example, `Int` to `Long`). The queue exclusively stores V1 IDs since producers enqueue V1 IDs before migration. This flexibility lets callers migrate between repos with different key schemas without forcing a unified key type.

**Note:** The `'D'` dispatch path recovers the V1 entity from the JSON payload and applies the migration to produce the V2 identity. For `TargetStrategy.ShadowTable`, the worker then deletes the corresponding V2 row from the shadow table using this recovered identity. The `ID1` to `ID2` cast (`asInstanceOf`) is still used in the shadow-table delete path. This is safe when `ID1 = ID2` (the common case). For genuinely different ID types, a conversion function would be needed.

*(Amended by PR #1534, 2026-07-20)*

### Decision 8: Pre-Commit State Enforcement

**Decision:** `complete()` requires `Drained` state. `drain()` requires `Fenced` state. `fence()` requires `Initialized` state. `drain()` only transitions to `Drained` if not paused.

**Rationale:** Prevents misordered lifecycle calls. The pause guard prevents `complete()` from being called when the queue is not empty (paused mid-drain). State transitions are enforced at runtime with `require()` checks, making invalid sequences fail fast rather than producing subtle data inconsistencies.

### Decision 9: Failure Policy

**Decision:** On worker transaction failure: rollback, retain the dirty key, return failure to caller. No automatic retry, no dead-letter queue.

**Rationale:** Simple, predictable semantics. The caller decides retry policy. No silent data loss. No configuration surface for retry counts, backoff strategies, or dead-letter routing. The dirty key remains in the queue, so the row can be retried by calling `processBatch()` or `drain()` again.

### Decision 10: Scope Exclusions

**Decision:** The following are explicitly out of scope:

- No schema-diff or automatic migration derivation (Derivation.scala removed per review)
- No CLI (MigrationCLI removed per review)
- No external brokers or LISTEN/NOTIFY
- No composite keys, configurable retry, configuration files, or third-party dialects

The following were previously out of scope but have since been adopted or are under consideration:

- ~~No JSON or full-row outbox payloads~~ **Adopted by PR #1534**: PostgreSQL capture triggers now serialize the full deleted row as JSON via `row_to_json(OLD.*)::text` into the queue table's `payload` column. This is limited to DELETE operations and is dialect-specific (PostgreSQL only; SQLite does not capture payload).
- ~~No automatic target-schema DDL generation~~ **Under consideration**: DDL expansion is pending review. The current `CREATE TABLE ... LIKE ... INCLUDING ALL` approach for shadow tables copies the source schema verbatim, but generating target-schema DDL from the `Migration[A, B]` definition is being explored.

**Rationale:** Keeps scope narrow. `Migration[A, B]` provides the typed transformation. `Repo` provides the database surface. The data migration module is a thin orchestration layer. Callers supply the migration logic; the module handles queueing, worker coordination, and cutover. The adoption of JSON payloads for DELETE capture was driven by the need to handle deletes correctly when the source row is no longer available at processing time.

*(Amended by PR #1534, 2026-07-20)*

## Consequences

### Positive

- Clean separation of concerns: `Migration[A, B]` (what) vs execution model (how) vs target strategy (where)
- Queue-based dirty-key tracking is storage-bounded and crash-recoverable
- Safe completion protocol prevents data loss during cutover
- ID type flexibility supports realistic migration scenarios
- Op-based dispatch with payload deserialization handles deletes correctly even when the source row is gone
- No hand-written SQL, no XML, no external dependencies beyond the database

### Tradeoffs

- Delete payload capture is dialect-specific: PostgreSQL provides full row data via `row_to_json`, but SQLite cannot capture a payload for deletes. On SQLite, delete handling for `TargetStrategy.ShadowTable` is limited to cases where the V2 identity can be recovered without the payload.
- The `'D'` payload deserialization path relies on `migration.sourceSchema.jsonCodec` being available and compatible with the `row_to_json` output format. Schema evolution between capture and replay could break deserialization.
- No automatic retry requires caller-side error handling. Callers must implement their own retry logic if desired.
- PostgreSQL-only `SKIP LOCKED` limits full multi-worker support. SQLite is single-worker due to its write serialization model.
- State is in-memory (not persisted). Restart resets the state machine, though the queue table persists across restarts for crash recovery.
- Per-key writer blocking during worker processing is accepted. A source writer waits while the worker holds the dirty-key claim. No tuning configuration is provided for this.

*(Amended by PR #1534, 2026-07-20)*

## References

- [Data Migration Reference Documentation](../reference/data-migration.md)
- PR #1534 (implementation)
