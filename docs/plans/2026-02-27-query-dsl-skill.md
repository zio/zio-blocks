# Query DSL Skill Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Create a reusable skill at `.claude/skills/query-dsl/SKILL.md` that guides an AI assistant (or developer) in building a complete, type-safe query DSL for any domain using ZIO Blocks' `SchemaExpr` + reified optics — covering all four layers from the existing guide series.

**Architecture:** The skill is a process guide in Markdown. It does not generate code itself — it tells the implementer *how* to guide a user through: (1) domain setup with `Schema` + `CompanionOptics`, (2) in-memory evaluation with `SchemaExpr`, (3) target-language interpretation (SQL, MongoDB, etc.), (4) extension with a custom `Expr` ADT, and (5) optional fluent statement builders. The skill has a discovery phase, a decision tree for choosing layers, layer-by-layer implementation checklists, and verification steps.

**Tech Stack:** Markdown skill file; no code compilation needed for the skill itself.

---

## Task 1: Read Existing Skill Structure

**Files:**
- Read: `.claude/skills/write-how-to-guide/SKILL.md` (already done — reference template)
- Read: `.claude/skills/write-data-type-ref/SKILL.md` (for comparison)
- Read: `.claude/skills/finding-undocumented/SKILL.md` (shorter reference)

**Step 1: Confirm skill frontmatter format**

From the existing `write-how-to-guide/SKILL.md`, the format is:

```markdown
---
name: <kebab-case>
description: <one-liner for skill triggering>
argument-hint: "[what the user provides]"
allowed-tools: Read, Glob, Grep, Bash(sbt:*)
---
```

**Step 2: Confirm skill directory creation**

```bash
mkdir -p /home/milad/sources/scala/zio-blocks-modern/.claude/skills/query-dsl
```

Expected: directory created.

**Step 3: Commit nothing yet** — this task is read-only research.

---

## Task 2: Write the Skill File

**Files:**
- Create: `.claude/skills/query-dsl/SKILL.md`

**Step 1: Create the skills directory**

```bash
mkdir -p /home/milad/sources/scala/zio-blocks-modern/.claude/skills/query-dsl
```

**Step 2: Write the skill file**

Write `.claude/skills/query-dsl/SKILL.md` with the following content (complete, no placeholders):

The skill must cover these sections:

### Skill Structure

```
# Frontmatter
name: query-dsl
description: Build a type-safe query DSL for a domain using ZIO Blocks reified optics and SchemaExpr. Use when user wants to implement query filtering, SQL generation, expression trees, or a fluent query builder for their data types.
argument-hint: "[domain description or query target, e.g. 'SQL queries for User and Order', 'MongoDB filter for Product']"
allowed-tools: Read, Glob, Grep, Bash(sbt:*)

# Body

## Overview
What this skill produces (the 4 layers)

## Phase 1: Discovery
- Understand the user's domain types
- Understand the query target (SQL, MongoDB, in-memory, API filter, etc.)
- Decide which layers to implement (decision tree)

## Layer 1: Domain Setup + SchemaExpr Expressions
- Define domain types with Schema.derived + CompanionOptics
- Define lenses for all queryable fields (incl. nested via optic(_.a.b))
- Add traversals for collection fields
- Demonstrate equality/comparison/boolean/arithmetic/string operators
- Show .eval() usage + filter[A] generic function
- Checklist

## Layer 2: Target-Language Interpreter
- Core pattern: pattern match on SchemaExpr sealed cases
- Extract column/field names from optic.toDynamic.nodes
- Handle all node types (Literal, Optic, Relational, Logical, Not, Arithmetic, StringConcat, StringRegexMatch, StringLength)
- Inline rendering vs. parameterized rendering (SQL injection safety)
- Handle nested paths (multi-segment Field nodes)
- Checklist

## Layer 3: Extension with Custom Expr ADT (optional)
- When to use: need IN, BETWEEN, IS NULL, LIKE, aggregates, CASE WHEN, or any op SchemaExpr lacks
- Pattern: define Expr[S,A] as superset of SchemaExpr cases + new cases
- Provide fromSchemaExpr translation function
- Add bridge implicit classes (SchemaExprBooleanBridge, ExprBooleanOps)
- Write unified interpreter for Expr
- Checklist

## Layer 4: Fluent Statement Builder (optional, SQL-specific)
- When to use: need type-safe SELECT/UPDATE/INSERT/DELETE construction
- Table[S] type + schema-driven table names via Modifier.config
- Immutable builder case classes with copy-based fluent API
- .where() overloads accepting both Expr and SchemaExpr
- Renderers for each statement type
- Checklist

## Verification
- Compile all examples
- Check eval() results match expected predicates
- Verify SQL output is syntactically correct
- Test SQL injection safety (parameterized form)

## Common Mistakes
- Not using mdoc modifiers correctly in docs
- Forgetting Schema[A] on Lit/In/Between nodes (needed for sqlLiteral)
- Shadowing SchemaExpr.&& with Expr.&& without bridge implicits
- Using regex patterns instead of SQL LIKE patterns
```

**Step 3: Verify the file was created**

```bash
ls -la /home/milad/sources/scala/zio-blocks-modern/.claude/skills/query-dsl/SKILL.md
```

Expected: file exists, non-empty.

**Step 4: Commit**

```bash
cd /home/milad/sources/scala/zio-blocks-modern
git add .claude/skills/query-dsl/SKILL.md
git commit -m "Add query-dsl skill for building type-safe query DSLs with ZIO Blocks

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>"
```

---

## Task 3: Verify Skill Invocability

**Files:**
- Read: `.claude/skills/query-dsl/SKILL.md` (verify the written file)

**Step 1: Read the written file to confirm correctness**

Read the entire file and verify:
- [ ] Frontmatter is valid YAML
- [ ] `name` matches the directory name (`query-dsl`)
- [ ] `description` is specific enough to trigger on realistic user requests
- [ ] All 4 layers are covered with checklists
- [ ] Bridge implicit pattern is explained correctly (SchemaExprBooleanBridge + ExprBooleanOps)
- [ ] `fromSchemaExpr` pattern is fully specified with all SchemaExpr cases mapped
- [ ] Table[S] pattern and schema-driven name derivation are covered
- [ ] Modifier.config("sql.table_name", ...) annotation pattern is documented
- [ ] Verification steps are actionable

**Step 2: Check no broken cross-references**

The skill references these guide files as context pointers. Verify they exist:

```bash
ls /home/milad/sources/scala/zio-blocks-modern/docs/guides/query-dsl-*.md
```

Expected: 4 files (reified-optics, sql, extending, fluent-builder).

---

## Skill Content Specification (Full)

The SKILL.md file must contain the complete content below. This is the source of truth — Task 2 writes exactly this.

### Frontmatter

```yaml
---
name: query-dsl
description: Build a type-safe query DSL for a domain using ZIO Blocks reified optics and SchemaExpr. Use when the user wants to implement query filtering, SQL generation, expression trees, or a fluent query builder for their data types.
argument-hint: "[describe the domain and query target, e.g. 'SQL queries for User and Order', 'in-memory filter for Product catalog']"
allowed-tools: Read, Glob, Grep, Bash(sbt:*)
---
```

### Body sections

**## Overview** — 1 paragraph explaining the 4-layer arc the skill implements.

**## Phase 1: Discovery Questions** — ask the user:
1. What are your domain types? (names, key fields, nesting)
2. What is the query target? (SQL, MongoDB, Elasticsearch, in-memory filter, API filter, other)
3. Do you need in-memory evaluation, target-language generation, or both?
4. Do you need operators beyond `===`, `>`, `<`, `&&`, `||` (i.e., IN, BETWEEN, LIKE, IS NULL)?
5. Do you need full statement construction (SELECT/UPDATE/INSERT/DELETE) or just WHERE clauses?

Based on answers, choose layers:
- Always implement Layer 1 (foundation)
- Always implement Layer 2 unless target is in-memory only
- Add Layer 3 if answer to Q4 is yes
- Add Layer 4 if answer to Q5 is yes AND target is SQL

**## Layer 1: Domain Setup + SchemaExpr Expressions**

Step-by-step with code snippets (using the Product catalog as the worked example):

1. Add `Schema.derived` implicit to each domain type
2. Extend companion object with `CompanionOptics[T]`
3. Define a lens for each queryable field using `optic(_.fieldName)`
4. For nested fields: `optic(_.address.city)` composes automatically
5. For collections: `optic(_.items.each.price)` produces `Traversal[S, A]`
6. Show operators: `===`, `!=`, `>`, `>=`, `<`, `<=`
7. Show boolean combinators: `&&`, `||`, `!`
8. Show arithmetic: `+`, `-`, `*` on numeric fields
9. Show string ops: `.matches(regex)`, `.concat(suffix)`, `.length`
10. Show `.eval(instance)` → `Either[OpticCheck, Seq[A]]`
11. Show generic `filter[A](items: List[A], predicate: SchemaExpr[A, Boolean]): List[A]`

Checklist:
- [ ] All domain types have `implicit val schema: Schema[T] = Schema.derived`
- [ ] Companion objects extend `CompanionOptics[T]`
- [ ] All queryable fields have named lenses
- [ ] Nested paths compile (test with a simple `===` query)
- [ ] `.eval()` returns `Right(Seq(...))` for lens-based queries

**## Layer 2: Target-Language Interpreter**

Core `columnName` helper:
```scala
def columnName(optic: Optic[_, _]): String =
  optic.toDynamic.nodes.collect { case f: DynamicOptic.Node.Field => f.name }.mkString("_")
```

Full `SchemaExpr` case coverage — the implementer must handle ALL of these:
- `SchemaExpr.Optic(optic)` → extract column name
- `SchemaExpr.Literal(value, _)` → format as literal
- `SchemaExpr.Relational(l, r, op)` → map `RelationalOperator` to target syntax
- `SchemaExpr.Logical(l, r, op)` → map `LogicalOperator.And/Or`
- `SchemaExpr.Not(inner)` → negate
- `SchemaExpr.Arithmetic(l, r, op, _)` → map `ArithmeticOperator`
- `SchemaExpr.StringConcat(l, r)` → target string concatenation function
- `SchemaExpr.StringRegexMatch(regex, string)` → target regex/pattern matching
- `SchemaExpr.StringLength(string)` → target length function

Literal formatting for SQL (schema-aware):
```scala
def sqlLiteral[A](value: A, schema: Schema[A]): String = {
  schema.toDynamicValue(value) match {
    case p: DynamicValue.Primitive => p.value match {
      case _: PrimitiveValue.String  => s"'${value.toString.replace("'", "''")}'"
      case b: PrimitiveValue.Boolean => if (b.value) "TRUE" else "FALSE"
      case _                         => value.toString
    }
    case _ => value.toString
  }
}
```

Parameterized form (for SQL injection safety):
- Replace `Literal(value, _)` → emit `"?"`, collect value into `params: List[Any]`
- Return `case class SqlQuery(sql: String, params: List[Any])`

Nested path strategy for SQL JOINs:
- `nodes.length > 1` → `"tableName.column"` format for JOIN queries

Checklist:
- [ ] All 9 SchemaExpr cases are handled (compiler warns on missing cases — sealed trait)
- [ ] String literals are single-quoted with internal quote escaping
- [ ] Boolean literals render as TRUE/FALSE not 'true'/'false'
- [ ] Parameterized form implemented for production use
- [ ] Nested optic paths render correctly for the target language

**## Layer 3: Extension with Custom Expr ADT**

When to add this layer: user needs `IN`, `BETWEEN`, `IS NULL`, `LIKE`, aggregate functions (`COUNT`, `SUM`, `AVG`, `MIN`, `MAX`), or `CASE WHEN`.

Architecture: define `sealed trait Expr[S, A]` with:
- Mirror nodes for all SchemaExpr cases (same names, same semantics)
- Extension nodes: `In[S,A]`, `Between[S,A]`, `IsNull[S,A]`, `Like[S]`
- Optional: `Agg[S,A,B]` (needs typed `AggFunction[A,B]`), `CaseWhen[S,A]`

Translation bridge:
```scala
def fromSchemaExpr[S, A](se: SchemaExpr[S, A]): Expr[S, A]
```
Must recursively map every SchemaExpr case. Critical — do NOT omit any case.

Bridge implicit classes for seamless `&&`/`||` composition:
```scala
// When Expr is on the left, SchemaExpr on the right:
implicit final class ExprBooleanOps[S](private val self: Expr[S, Boolean]) {
  def &&(other: Expr[S, Boolean]): Expr[S, Boolean]       = Expr.And(self, other)
  def &&(other: SchemaExpr[S, Boolean]): Expr[S, Boolean] = Expr.And(self, Expr.fromSchemaExpr(other))
  def ||(other: Expr[S, Boolean]): Expr[S, Boolean]       = Expr.Or(self, other)
  def ||(other: SchemaExpr[S, Boolean]): Expr[S, Boolean] = Expr.Or(self, Expr.fromSchemaExpr(other))
  def unary_! : Expr[S, Boolean]                          = Expr.Not(self)
}

// When SchemaExpr is on the left, Expr on the right:
implicit final class SchemaExprBooleanBridge[S](private val self: SchemaExpr[S, Boolean]) {
  def &&(other: Expr[S, Boolean]): Expr[S, Boolean] = Expr.And(Expr.fromSchemaExpr(self), other)
  def ||(other: Expr[S, Boolean]): Expr[S, Boolean] = Expr.Or(Expr.fromSchemaExpr(self), other)
  def toExpr: Expr[S, Boolean]                       = Expr.fromSchemaExpr(self)
}
```

Extension methods on Optic:
```scala
implicit final class OpticExprOps[S, A](private val optic: Optic[S, A]) {
  def in(values: A*)(implicit schema: Schema[A]): Expr[S, Boolean]           = Expr.In(Expr.col(optic), values.toList, schema)
  def between(low: A, high: A)(implicit schema: Schema[A]): Expr[S, Boolean] = Expr.Between(Expr.col(optic), low, high, schema)
  def isNull: Expr[S, Boolean]    = Expr.IsNull(Expr.col(optic))
  def isNotNull: Expr[S, Boolean] = Expr.Not(Expr.IsNull(Expr.col(optic)))
}

implicit final class StringOpticExprOps[S](private val optic: Optic[S, String]) {
  def like(pattern: String): Expr[S, Boolean] = Expr.Like(Expr.col(optic), pattern)
}
```

Checklist:
- [ ] `Expr` ADT has mirror nodes for ALL SchemaExpr cases
- [ ] `fromSchemaExpr` handles all 9 cases (sealed — compiler warns on missing)
- [ ] Bridge implicits allow `SchemaExpr && Expr` and `Expr && SchemaExpr` without `.toExpr`
- [ ] `in`, `between`, `isNull`, `isNotNull` work on any `Optic[S, A]`
- [ ] `like` works on `Optic[S, String]`
- [ ] Unified `exprToSql` handles all Expr cases in a single function

**## Layer 4: Fluent Statement Builder (SQL-specific)**

`Table[S]` type:
```scala
case class Table[S](name: String)
object Table {
  def derived[S](implicit schema: Schema[S]): Table[S] = Table(tableName(schema))
}
```

Schema-driven table name derivation:
```scala
def tableName[S](schema: Schema[S]): String =
  schema.reflect.modifiers.collectFirst {
    case Modifier.config(key, value) if key == "sql.table_name" => value
  }.getOrElse(pluralize(schema.reflect.typeId.name.toLowerCase))
```

Override with annotation:
```scala
implicit val schema: Schema[OrderItem] = Schema.derived
  .modifier(Modifier.config("sql.table_name", "order_items"))
```

Statement types (immutable case classes, fluent via `copy`):
- `SelectStmt[S]`: `columnList`, `whereExpr`, `orderByList`, `limitCount` + methods `.columns()`, `.where()`, `.orderBy()`, `.limit()`
- `UpdateStmt[S]`: `assignments`, `whereExpr` + methods `.set[A](optic, value)`, `.where()`
- `InsertStmt[S]`: `assignments` + method `.set[A](optic, value)`
- `DeleteStmt[S]`: `whereExpr` + method `.where()`

Each `.where()` is overloaded for both `Expr[S, Boolean]` and `SchemaExpr[S, Boolean]`.

Entry-point functions: `select(table)`, `update(table)`, `insertInto(table)`, `deleteFrom(table)`.

Renderer functions: `renderSelect`, `renderUpdate`, `renderInsert`, `renderDelete`.

Checklist:
- [ ] `Table[S]` defined with `derived` factory using schema metadata
- [ ] Auto-pluralization handles regular words (noun + "s") and irregular endings
- [ ] `Modifier.config("sql.table_name", ...)` annotation overrides auto-pluralization
- [ ] All 4 statement types have immutable builders
- [ ] `.where()` on each builder accepts both `Expr` and `SchemaExpr`
- [ ] `.set[A]` uses schema-derived `sqlLiteral` (not `toString`)
- [ ] All 4 renderers produce syntactically correct SQL

**## Common Mistakes to Avoid**

1. **Forgetting `Schema[A]` on `Lit`/`In`/`Between` nodes** — these carry a `schema` field so `sqlLiteral` can format correctly without runtime type checks.
2. **Using regex syntax in `.matches()`** — `SchemaExpr.StringRegexMatch` is evaluated with regex in-memory but should use SQL LIKE patterns (e.g., `"L%"` not `"L.*"`) when interpreting for SQL.
3. **Shadowing `SchemaExpr.&&` without bridge implicits** — if you start a chain with `SchemaExpr` on the left and then use an `Expr` on the right, the direct `SchemaExpr.&&` method only accepts `SchemaExpr`. Without `SchemaExprBooleanBridge`, you must call `.toExpr` explicitly.
4. **Missing cases in `fromSchemaExpr`** — `SchemaExpr` is a sealed trait; forgetting a case causes a match error at runtime. The Scala compiler gives an exhaustiveness warning — treat it as an error.
5. **`result.asInstanceOf[Expr[S,A]]` in `fromSchemaExpr`** — this cast is necessary due to how the sealed trait is structured. It is safe because the recursive translation preserves types structurally.
6. **Using `toString` for boolean literals** — `true.toString` is `"true"` but SQL expects `TRUE`. Always use `DynamicValue.Primitive` inspection via the schema.

**## Verification Steps**

After implementing each layer:

```bash
# Compile everything
sbt "schema-examples/compile"

# Run the complete example
sbt "schema-examples/runMain <packagename>.CompleteExample"
```

Manual verification for SQL output:
- Run each query expression through the interpreter and read the output SQL
- Verify string literals are single-quoted: `'Electronics'` not `Electronics`
- Verify boolean literals: `TRUE`/`FALSE` not `true`/`false`
- Verify nested paths: `address_city` or `address.city` as intended
- Verify parameterized form: `?` placeholders, correct param list order and count

**## References**

- [Part 1: Expressions](../guides/query-dsl-reified-optics.md) — SchemaExpr operators and in-memory evaluation
- [Part 2: SQL Generation](../guides/query-dsl-sql.md) — The sealed AST interpreter pattern
- [Part 3: Extending the Expression Language](../guides/query-dsl-extending.md) — Custom Expr ADT and bridge implicits
- [Part 4: A Fluent SQL Builder](../guides/query-dsl-fluent-builder.md) — Table[S], statement builders, and renderers
- [Optics Reference](../reference/optics.md) — Lens, Prism, Optional, Traversal
- [DynamicOptic Reference](../reference/dynamic-optic.md) — Runtime optic path extraction
- [Schema Reference](../reference/schema.md) — Schema derivation and Modifier.config
