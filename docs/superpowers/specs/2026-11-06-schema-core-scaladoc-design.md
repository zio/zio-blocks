# schema core: Scaladoc for the remaining 543 members (design)

## Problem

The `schema` module was the first Scaladoc rollout (waves 0-8b, #1813-#1843). It stopped at 3232 of 3775 public
members (85%) and judged that every undocumented member "inherits its doc or is tier 3". Every later module went
to 100%. This closes the gap.

Coverage at base `07f71de18`: `schema` 1478/1948 (470 missing), `schema.binding` 429/471 (42), `schema.migration`
154/176 (22), `schema.json` 865/872 (7), `schema.codec` 19/21 (2). `patch`, `comptime`, `derive` are complete.

## Phase 0 finding

Of the 543 gaps, 521 implement a member whose parent already has Scaladoc (a), 22 are macro `*Impl` helpers (b),
and none is a gap with no parent doc (c). Both doc tools (2.13.18, 3.9.0) inherit the parent text: checked on
`PrimitiveValue.Unit#primitiveType` and `Register.Boolean#set`.

Files with gaps: PrimitiveValue 118, Reflect 110, DynamicSchemaExpr 89, PrimitiveType 77, SchemaExpr 43,
Register 30, DynamicValue 18, MigrationBuilderSyntax (scala-2) 13, OpticCheck 9, BindingResolver 9,
MigrationBuilderMacros (scala-3) 9, JsonSchemaType 7, DynamicValueType 6, Format 2, Constructor 2,
SeqDeconstructor (scala-2) 1.

## Decisions (Milad, 2026-11-06)

- Scope: all 543. One PR. No tier-1 types, no new examples.
- Text: per-case. Say what this implementation does (the compare order, the index, the value returned, the
  error produced), never a copy of the parent. One to two sentences. Tier 3 helpers (`*Impl`): one line.

## Rules

Comments only; truth rule (every claim probed on 2.13.18 and 3.9.0); identical text in scala-2/scala-3 copies;
no `docs/dev/` mentions; bugs go to the coordinator as one file. Gates: scaladoc-check on 2.13.18/3.9.0/3.10.0,
`schemaJVM/doc` and `schemaJS/doc` on 3.9.0 and 2.13.18 with no new warnings, coverage 3775/3775, `--drift`
empty, comments-only prints 0 code changes, `check; headerCheckAll`, `scalafmtAll`.

## Review

Fresh-context Opus fact-check re-running probes and bug reproducers, a fix round, a scoped re-review.
