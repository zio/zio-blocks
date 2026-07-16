# Scala DB Migration System — Learnings (2026-07-16)

## Core Model (Todo 1)

- `DataVersion(epoch, major, minor, timestampMillis?)` — simple case class with non-negative guards + lexicographic compare.
- `MigrationPath[A,B](from, to, migration: Migration[A,B])` — requires `from < to` (downgrades rejected at construction via `require`).
- `ExecutionModel` sealed trait: `Tiny | Small | Large` (exactly three cases, minimal abstraction).
- `TargetStrategy` sealed trait: `InPlace | ShadowTable(name)` — orthogonal to execution model.
- Package: `zio.blocks.data.migration`.
- No `Persisted[A]` wrapper (per user directive).
- No SQL/DDL/execution logic — pure model only.
- Follows zio-blocks conventions: Apache 2.0 header, `zio.blocks.*` package, ZIO Test specs.

## Patterns Observed from Existing Code

- `sql.Repo` uses abstract class + protected primary constructor + `derivedMetadata` pattern.
- `schema.migration.Migration[A,B]` wraps `DynamicMigration` + source/target `Schema`.
- Module layout mirrors `sql/` (shared/src/main + shared/src/test).

## Next Steps (from plan)

- Todo 2: SchemaVersion + VersionedSchema (attach DataVersion to Schema).
- Todo 3: MigrationRegistry (path discovery + validation).
- Todo 4: Execution engine (respecting Tiny/Small/Large + TargetStrategy).

## Risks / Notes

- `require` in case class secondary logic is acceptable for model invariants.
- Test uses a minimal identity `DynamicMigration` stub — sufficient for construction tests.

# 2026-07-16 — Common Primitives Implementation (Todo 2)

## Decisions
- QueueTable uses Frag.literal + SqlIdentifier.validate for all identifiers (consistent with Ddl.scala pattern).
- MigrationMetadata stores DataVersion as TEXT "epoch.major.minor" for simplicity; parsing is lenient.
- ShadowTable.create uses `CREATE TABLE ... (LIKE ... INCLUDING ALL)` — Postgres-specific but matches requirement.
- All helpers are pure functions over DbCon/Transactor; no coupling to ExecutionModel or TargetStrategy.

## API Notes
- `QueueTable.dequeue` deletes rows after SELECT FOR UPDATE SKIP LOCKED (standard pattern).
- `pending` returns Long (COUNT(*)) — callers decide batching strategy.
- No `Persisted[A]` wrapper introduced (per MUST NOT).

## Risks Mitigated
- Identifier injection prevented via SqlIdentifier.validate on every table/column name.
- Empty batch guards in enqueue/dequeue.

## Next (for later todos)
- A-Small/B-Large will compose these primitives directly.
- Real DB tests (Postgres) deferred to execution model verification.

# 2026-07-16 — Core Model Integration (Todo 1 complete)

## Verification Results
- Core model types compile cleanly on Scala 3.8.3 (DataVersion, MigrationPath, ExecutionModel, TargetStrategy).
- CoreSpec compiles and passes all tests (5 tests, including the intentional `@@ TestAspect.failing` for downgrade rejection).
- No implicit/given issues; no Maybe/Option mismatches; `implicit` used for Schema[V1]/Schema[V2] (cross-version compatible).
- `dataMigration` crossProject added to build.sbt following `sql` pattern: dependsOn(schema, maybe), stdSettings + crossProjectSettings + buildInfoSettings + BuildInfoPlugin enabled.
- Command: `sbt --client "++3.8.3; dataMigrationJVM/test"` → success (compile + test green).

## Build Integration Notes
- Placed `lazy val dataMigration` immediately after `sql` block (before `sql-zio`).
- Uses `stdSettings("zio-blocks-data-migration")` (no Scala version restriction — inherits default cross versions).
- Test deps use 2.1.26 (consistent with `maybe` pattern).
- No new external dependencies added.

## Cross-Platform
- Cross-built for JS + JVM (crossProject(JSPlatform, JVMPlatform)).
- dataMigrationJS falls back to 3.3.7 (expected, matches other modules).

## Findings for Future Work
- Core model is intentionally minimal — no Persisted[A], no SQL execution, no registry yet.
- MigrationPath require guard works as intended (downgrades fail construction).
- Test uses identity DynamicMigration stub — sufficient for model verification.
