# schema core Scaladoc: plan

## Problem
543 public members of `schema` have no Scaladoc of their own. Readers see the parent's text, which cannot say what
this implementation does.

## Goal
Coverage 3775/3775 with comments that are true for each case, in one PR.

## Steps
1. **Task 1 (Opus): data-model files.** `PrimitiveValue.scala`, `PrimitiveType.scala`, `DynamicValue.scala`,
   `DynamicValueType.scala`, `Reflect.scala`. Probe the claims on 2.13.18 and 3.9.0 with scala-cli.
2. **Task 2 (Opus): expressions, bindings, rest.** `DynamicSchemaExpr.scala`, `SchemaExpr.scala`, `OpticCheck.scala`,
   `binding/*` (Register, BindingResolver, Constructor, SeqDeconstructor), `json/JsonSchemaType.scala`,
   `codec/Format.scala`, `migration/*` (scala-2 and scala-3 macro helpers, same text).
3. Controller runs the gates, comments-only check, drift check; commits per file group.
4. Fresh Opus fact-check review (two reviewers, one per task), fix round, scoped re-review.
5. Merge `origin/main`, re-run gates, push to `khajavi`, open PR (body file), watch CI. Do not merge.
6. Send the coordinator the bug file and the backlog §13o text.

Only one agent runs sbt at a time; the writers use scala-cli only and commit with explicit paths.
