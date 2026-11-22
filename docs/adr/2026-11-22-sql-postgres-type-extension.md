---
id: adr-002-sql-postgres-type-extension
title: "ADR-002: PostgreSQL/Dialect Type Extension Point"
status: accepted
date: 2026-11-22
---

# ADR-002: PostgreSQL/Dialect Type Extension Point

## Status

Accepted. The core pieces ship in the `sql` module; the dedicated
`zio-blocks-sql-postgres` module remains deferred.

## Context

`DbValue` is a closed ADT and `DbCodecDeriver` is schema-driven. Before this
ADR, a PostgreSQL-only type such as `vector` (pgvector), `tsvector`, or a
`PGobject`-backed value had no supported representation:

- `DbValue` had no variant for a driver object, so the documented workaround
  was `SqlLiteral`, which inlines the value into SQL text (injection risk, no
  prepared-statement reuse).
- SQL array reads were limited to `Array[String]`.
- A `given DbCodec[FieldType]` was not consulted for a record field, so a
  vector column was decoded as JSONB and failed with an opaque error.
- Every new dialect type would have required editing the core `DbValue`,
  `Frag`, writer, and deriver.

The `sql` module cross-builds for Scala 3.9 and 3.10 and has no compile-time
dependency on a JDBC driver or on any dialect-specific class.

## Decisions

### Decision 1: Keep driver-specific values parameterized with `DbValue.Raw`

**Decision:** Add `DbValue.Raw(sqlType: String, value: Any)` and
`DbParamWriter.setRaw(index, sqlType, value)`. The JDBC writer binds with
`PreparedStatement.setObject(index, value, java.sql.Types.OTHER)`.

**Rationale:** This is the smallest safe escape hatch. It keeps the value out
of the SQL text, works with any driver object that understands `Types.OTHER`
(including `PGobject` and pgvector), and needs no driver dependency in main
sources. `sqlType` is metadata for DDL/logging and future dialect modules; the
driver infers the concrete type from the object.

**Trust boundary:** `Raw` passes the object to the driver unchanged. It is for
values that cannot be expressed as a standard `DbValue`, not a replacement for
normal parameter binding, and never a place to pass raw SQL.

### Decision 2: Field overrides are explicit; JSONB is not a fallback for user codecs

**Decision:** Add `DbCodecDeriver.withFieldCodec(parentTypeId, fieldName, codec)`
and make `DbCodec.derivedWith(...instance(...))` register the same override.
Field overrides are resolved before the JSONB fallback for non-inline nested
records and sequences. A `given DbCodec[FieldType]` is not summoned
automatically.

**Rationale:** Derivation walks a `Reflect` tree at runtime; there is no
compiler hook that could summon a per-field given while the tree is walked.
An explicit `(parent TypeId, field name)` key is precise, works on both Scala 3
versions, and preserves existing derivations. The JSONB fallback stays for
fields that genuinely are JSON.

### Decision 3: An immutable `DbTypeRegistry` is the dialect extension point

**Decision:** Add `DbTypeMapping[A](typeId, sqlTypeName, codec)` and an
immutable `DbTypeRegistry` (`.register`, `.get`, `.mappings`). Pass it to
`DbCodecDeriver.withTypeRegistry`; mappings apply to record fields and to
top-level record/wrapper derivation. A field-specific override still wins.

**Rationale:** A registry keyed by `TypeId` lets a dialect register `vector`,
`jsonb`, `tsvector`, and similar types once, without editing the core deriver.
It is explicit and immutable, so it composes with the existing
`Schema.deriving(deriver)` API and has no global mutable state. A typeclass
alone cannot be used because the deriver has no field-type implicits at
runtime.

### Decision 4: No new sbt module yet

**Decision:** Ship the extension point in `sql` and describe the target
`zio-blocks-sql-postgres` shape in this ADR. Do not create a new module in this
change.

**Rationale:** Wiring a new cross-built module (JVM-only, test deps, MiMa,
`testJVM`/`testJS`/`docJVM`/`docJS` aliases, docs sidebar) is a separate,
reviewable build change. The registry is useful before the module exists:
applications can register a mapping locally with code they already have.

## Intended `zio-blocks-sql-postgres` shape

```scala
package zio.blocks.sql.postgres

object PgTypes {
  val vector: DbTypeMapping[Embedding] =
    DbTypeMapping(TypeId.of[Embedding], "vector", Embedding.codec)

  val tsVector: DbTypeMapping[TsVector] =
    DbTypeMapping(TypeId.of[TsVector], "tsvector", TsVector.codec)

  val registry: DbTypeRegistry =
    DbTypeRegistry.empty.register(vector).register(tsVector)
}
```

A `PGobject`-backed codec writes `DbValue.Raw("vector", pgObject)` and reads
the driver's text form, so `DbCodecDeriver.withTypeRegistry(PgTypes.registry)`
makes ordinary `Schema.derived[Row].deriving(...)` work with no per-field
annotations. The module would own driver-specific convenience wrappers and
integration tests; the core `sql` module stays driver-free.

## Consequences

- Driver-specific values no longer need `SqlLiteral`; they stay bound
  parameters.
- Field-level overrides and type-registry mappings are explicit and
  testable with sentinel codecs.
- JSONB decode errors now identify the column, schema path, and input prefix,
  making a missed override diagnosable.
- `DbValue.Raw` is a trust boundary and is documented as such.
- Existing derivations are unchanged when no registry/override is supplied.

## Deferred

- The `zio-blocks-sql-postgres` sbt module and its build/docs wiring.
- Automatic read mapping from arbitrary driver objects (the codec's reader
  still chooses the getter; PG-specific `PGobject` readers are module work).
- Registry-driven DDL (`Table`/`Dump` type names) and array element-type
  registry integration.
- Implicit/global registries or annotation-based codec lookup by name.
- A dedicated `DbValue` variant per dialect type (intentionally avoided).
