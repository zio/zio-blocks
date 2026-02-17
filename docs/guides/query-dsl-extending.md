---
id: query-dsl-extending
title: "Query DSL with Reified Optics — Part 3: Extending the Expression Language"
---

In this guide, we will extend the ZIO Blocks query DSL with custom expression nodes that go beyond what `SchemaExpr` provides out of the box. By the end, you will have a wrapper ADT that adds SQL-specific predicates (`IN`, `BETWEEN`, `IS NULL`, `LIKE`), aggregate functions (`COUNT`, `SUM`, `AVG`), and conditional expressions (`CASE WHEN`) — all composable with the built-in `SchemaExpr` operators from Parts 1 and 2.

This is Part 3 of the Query DSL series. [Part 1](./query-dsl-reified-optics.md) covered building query expressions, and [Part 2](./query-dsl-sql.md) covered translating them to SQL. Here, we handle the cases where the built-in expression language is not enough.

**What we'll cover:**

- Why `SchemaExpr` is deliberately closed and what that means for extension
- Designing a wrapper ADT that embeds `SchemaExpr` as one case
- Adding SQL-specific predicates: `IN`, `BETWEEN`, `IS NULL`, `LIKE`
- Writing ergonomic extension methods on `Optic`
- Composing custom expressions with built-in `SchemaExpr` queries
- Building an extended SQL interpreter that delegates to the Part 2 interpreter
- Adding aggregate functions and `CASE WHEN` for advanced SQL generation

## The Problem

The built-in `SchemaExpr` operators cover the fundamentals: equality, comparisons, boolean logic, arithmetic, and basic string operations. But real-world SQL requires more. Consider these common queries:

```sql
-- Membership test
SELECT * FROM products WHERE category IN ('Electronics', 'Books', 'Toys')

-- Range check
SELECT * FROM products WHERE price BETWEEN 10.0 AND 100.0

-- Null handling
SELECT * FROM products WHERE description IS NULL

-- Pattern matching with SQL wildcards
SELECT * FROM products WHERE name LIKE 'Lap%'

-- Aggregation
SELECT category, COUNT(*), AVG(price)
FROM products GROUP BY category HAVING COUNT(*) > 2

-- Conditional logic
SELECT name, CASE WHEN price > 100 THEN 'expensive' ELSE 'cheap' END AS tier
FROM products
```

None of these can be expressed with `SchemaExpr` alone. You could generate the SQL strings manually, but then you lose composability — you can no longer mix these operations with the type-safe `SchemaExpr` predicates from Parts 1 and 2.

Since `SchemaExpr` is a sealed trait, you cannot add new cases to it. Instead, we wrap it in our own ADT that adds the missing operations while preserving full interoperability with the built-in expression language.

## Prerequisites

This guide builds on [Part 1: Expressions](./query-dsl-reified-optics.md) and [Part 2: SQL Generation](./query-dsl-sql.md). You should be comfortable building `SchemaExpr` values and translating them to SQL.

```scala
libraryDependencies += "dev.zio" %% "zio-blocks-schema" % "@VERSION@"
```

```scala mdoc:silent
import zio.blocks.schema._
```

## Domain Setup

We reuse the product catalog domain and the Part 2 SQL helpers:

```scala mdoc:silent
case class Product(
  name: String,
  price: Double,
  category: String,
  inStock: Boolean,
  rating: Int
)

object Product extends CompanionOptics[Product] {
  implicit val schema: Schema[Product] = Schema.derived

  val name: Lens[Product, String]     = optic(_.name)
  val price: Lens[Product, Double]    = optic(_.price)
  val category: Lens[Product, String] = optic(_.category)
  val inStock: Lens[Product, Boolean] = optic(_.inStock)
  val rating: Lens[Product, Int]      = optic(_.rating)
}

// --- Part 2 SQL helpers (unchanged) ---

def columnName(optic: zio.blocks.schema.Optic[?, ?]): String =
  optic.toDynamic.nodes.collect { case f: DynamicOptic.Node.Field => f.name }.mkString("_")

def sqlLiteral(value: Any): String = value match {
  case s: String  => s"'${s.replace("'", "''")}'"
  case b: Boolean => if (b) "TRUE" else "FALSE"
  case n: Number  => n.toString
  case other      => other.toString
}

def toSql[A, B](expr: SchemaExpr[A, B]): String = expr match {
  case SchemaExpr.Optic(optic)                    => columnName(optic)
  case SchemaExpr.Literal(value, _)               => sqlLiteral(value)
  case SchemaExpr.Relational(left, right, op) =>
    val sqlOp = op match {
      case SchemaExpr.RelationalOperator.Equal              => "="
      case SchemaExpr.RelationalOperator.NotEqual           => "<>"
      case SchemaExpr.RelationalOperator.LessThan           => "<"
      case SchemaExpr.RelationalOperator.LessThanOrEqual    => "<="
      case SchemaExpr.RelationalOperator.GreaterThan        => ">"
      case SchemaExpr.RelationalOperator.GreaterThanOrEqual => ">="
    }
    s"(${toSql(left)} $sqlOp ${toSql(right)})"
  case SchemaExpr.Logical(left, right, op) =>
    val sqlOp = op match {
      case SchemaExpr.LogicalOperator.And => "AND"
      case SchemaExpr.LogicalOperator.Or  => "OR"
    }
    s"(${toSql(left)} $sqlOp ${toSql(right)})"
  case SchemaExpr.Not(inner)                      => s"NOT (${toSql(inner)})"
  case SchemaExpr.Arithmetic(left, right, op, _) =>
    val sqlOp = op match {
      case SchemaExpr.ArithmeticOperator.Add      => "+"
      case SchemaExpr.ArithmeticOperator.Subtract => "-"
      case SchemaExpr.ArithmeticOperator.Multiply => "*"
    }
    s"(${toSql(left)} $sqlOp ${toSql(right)})"
  case SchemaExpr.StringConcat(left, right)       => s"CONCAT(${toSql(left)}, ${toSql(right)})"
  case SchemaExpr.StringRegexMatch(regex, string) => s"(${toSql(string)} LIKE ${toSql(regex)})"
  case SchemaExpr.StringLength(string)            => s"LENGTH(${toSql(string)})"
}
```

## Designing the Extended AST

The key insight is the **wrapper pattern**: define your own sealed trait that embeds `SchemaExpr` as one case, then add your custom cases alongside it. The interpreter handles both — delegating to the Part 2 `toSql` for `SchemaExpr` nodes, and generating SQL directly for custom nodes.

```
    Built-in (sealed, not extensible)         Your extension (open to your needs)
  ┌───────────────────────────────┐       ┌─────────────────────────────────────┐
  │  SchemaExpr[S, A]             │       │  Expr[S, A]                         │
  │  ├── Literal                  │       │  ├── Wrapped(SchemaExpr) ───────────┼──┐
  │  ├── Optic                    │       │  ├── Column(Optic)                  │  │
  │  ├── Relational               │       │  ├── Lit(value)                     │  │
  │  ├── Logical                  │       │  ├── In(expr, values)               │  │
  │  ├── Not                      │       │  ├── Between(expr, low, high)       │  │
  │  ├── Arithmetic               │       │  ├── IsNull(expr)                   │  │
  │  ├── StringConcat             │       │  ├── Like(expr, pattern)            │  │
  │  ├── StringRegexMatch         │       │  ├── And / Or / Not                 │  │
  │  └── StringLength             │       │  ├── Agg(function, expr)            │  │
  └───────────────────────────────┘       │  └── CaseWhen(branches, otherwise)  │  │
              ▲                           └─────────────────────────────────────┘  │
              │                                                                    │
              └──── delegates to Part 2 toSql() ◄──────────────────────────────────┘
```

Here is the full `Expr` ADT:

```scala mdoc:silent
sealed trait Expr[S, A]

object Expr {
  // --- Core: bridge to SchemaExpr ---
  final case class Wrapped[S, A](expr: SchemaExpr[S, A]) extends Expr[S, A]
  final case class Column[S, A](optic: Optic[S, A]) extends Expr[S, A]
  final case class Lit[S, A](value: A) extends Expr[S, A]

  // --- SQL-specific predicates ---
  final case class In[S, A](expr: Expr[S, A], values: List[A]) extends Expr[S, Boolean]
  final case class Between[S, A](expr: Expr[S, A], low: A, high: A) extends Expr[S, Boolean]
  final case class IsNull[S, A](expr: Expr[S, A]) extends Expr[S, Boolean]
  final case class Like[S](expr: Expr[S, String], pattern: String) extends Expr[S, Boolean]

  // --- Boolean combinators ---
  final case class And[S](left: Expr[S, Boolean], right: Expr[S, Boolean]) extends Expr[S, Boolean]
  final case class Or[S](left: Expr[S, Boolean], right: Expr[S, Boolean]) extends Expr[S, Boolean]
  final case class Not[S](expr: Expr[S, Boolean]) extends Expr[S, Boolean]

  // --- Aggregates ---
  final case class Agg[S, A](function: AggFunction, expr: Expr[S, A]) extends Expr[S, A]

  // --- Conditional ---
  final case class CaseWhen[S, A](
    branches: List[(Expr[S, Boolean], Expr[S, A])],
    otherwise: Option[Expr[S, A]]
  ) extends Expr[S, A]

  // --- Factory methods ---
  def wrap[S, A](expr: SchemaExpr[S, A]): Expr[S, A] = Wrapped(expr)
  def col[S, A](optic: Optic[S, A]): Expr[S, A] = Column(optic)
  def lit[S, A](value: A): Expr[S, A] = Lit(value)

  def count[S, A](expr: Expr[S, A]): Expr[S, A] = Agg(AggFunction.Count, expr)
  def sum[S, A](expr: Expr[S, A]): Expr[S, A] = Agg(AggFunction.Sum, expr)
  def avg[S, A](expr: Expr[S, A]): Expr[S, A] = Agg(AggFunction.Avg, expr)
  def min[S, A](expr: Expr[S, A]): Expr[S, A] = Agg(AggFunction.Min, expr)
  def max[S, A](expr: Expr[S, A]): Expr[S, A] = Agg(AggFunction.Max, expr)

  def caseWhen[S, A](branches: (Expr[S, Boolean], Expr[S, A])*): CaseWhenBuilder[S, A] =
    CaseWhenBuilder(branches.toList)

  case class CaseWhenBuilder[S, A](branches: List[(Expr[S, Boolean], Expr[S, A])]) {
    def otherwise(value: Expr[S, A]): Expr[S, A] = CaseWhen(branches, Some(value))
    def end: Expr[S, A] = CaseWhen(branches, None)
  }
}

sealed trait AggFunction
object AggFunction {
  case object Count extends AggFunction
  case object Sum   extends AggFunction
  case object Avg   extends AggFunction
  case object Min   extends AggFunction
  case object Max   extends AggFunction
}
```

The three core nodes bridge the two worlds:

- **`Wrapped(expr)`** — embeds any `SchemaExpr` inside `Expr`, enabling the extended interpreter to delegate to the Part 2 `toSql` function
- **`Column(optic)`** — references a field directly via its optic (used by extension methods)
- **`Lit(value)`** — a literal value (used in `CASE WHEN` branches and similar contexts)

## Extension Methods

To make the new operations feel natural, we define extension methods on `Optic` and `Expr`:

```scala mdoc:silent
extension [S, A](optic: Optic[S, A]) {
  def in(values: A*): Expr[S, Boolean] =
    Expr.In(Expr.col(optic), values.toList)
  def between(low: A, high: A): Expr[S, Boolean] =
    Expr.Between(Expr.col(optic), low, high)
  def isNull: Expr[S, Boolean] =
    Expr.IsNull(Expr.col(optic))
  def isNotNull: Expr[S, Boolean] =
    Expr.Not(Expr.IsNull(Expr.col(optic)))
}

extension [S](optic: Optic[S, String]) {
  def like(pattern: String): Expr[S, Boolean] =
    Expr.Like(Expr.col(optic), pattern)
}

extension [S](expr: Expr[S, Boolean]) {
  def &&(other: Expr[S, Boolean]): Expr[S, Boolean] = Expr.And(expr, other)
  def ||(other: Expr[S, Boolean]): Expr[S, Boolean] = Expr.Or(expr, other)
  def unary_! : Expr[S, Boolean] = Expr.Not(expr)
}

extension [S, A](expr: SchemaExpr[S, A]) {
  def toExpr: Expr[S, A] = Expr.Wrapped(expr)
}
```

The `toExpr` extension on `SchemaExpr` is the bridge between the two expression worlds. Calling `.toExpr` lifts a built-in expression into the `Expr` ADT so it can be combined with custom nodes.

## The Extended SQL Interpreter

The interpreter pattern-matches on `Expr` and delegates to the Part 2 `toSql` for `Wrapped` nodes:

```scala mdoc:silent
def exprToSql[S, A](expr: Expr[S, A]): String = expr match {
  // Delegate to Part 2 interpreter
  case Expr.Wrapped(schemaExpr) => toSql(schemaExpr)

  // Column and literal
  case Expr.Column(optic) => columnName(optic)
  case Expr.Lit(value)    => sqlLiteral(value)

  // SQL-specific predicates
  case Expr.In(e, values) =>
    s"${exprToSql(e)} IN (${values.map(v => sqlLiteral(v)).mkString(", ")})"
  case Expr.Between(e, low, high) =>
    s"(${exprToSql(e)} BETWEEN ${sqlLiteral(low)} AND ${sqlLiteral(high)})"
  case Expr.IsNull(e) =>
    s"${exprToSql(e)} IS NULL"
  case Expr.Like(e, pattern) =>
    s"${exprToSql(e)} LIKE '${pattern.replace("'", "''")}'"

  // Boolean combinators
  case Expr.And(l, r) => s"(${exprToSql(l)} AND ${exprToSql(r)})"
  case Expr.Or(l, r)  => s"(${exprToSql(l)} OR ${exprToSql(r)})"
  case Expr.Not(e)    => s"NOT (${exprToSql(e)})"

  // Aggregates
  case Expr.Agg(func, e) =>
    val name = func match {
      case AggFunction.Count => "COUNT"
      case AggFunction.Sum   => "SUM"
      case AggFunction.Avg   => "AVG"
      case AggFunction.Min   => "MIN"
      case AggFunction.Max   => "MAX"
    }
    s"$name(${exprToSql(e)})"

  // CASE WHEN
  case Expr.CaseWhen(branches, otherwise) =>
    val cases = branches.map { (cond, value) =>
      s"WHEN ${exprToSql(cond)} THEN ${exprToSql(value)}"
    }.mkString(" ")
    val elseClause = otherwise.map(e => s" ELSE ${exprToSql(e)}").getOrElse("")
    s"CASE $cases$elseClause END"
}
```

The `Wrapped` case is the crucial bridge — any `SchemaExpr` built with the Part 1 operators (`===`, `>`, `&&`, etc.) passes straight through to the Part 2 interpreter unchanged.

## SQL-Specific Predicates

With the ADT, extensions, and interpreter in place, the new operators work directly on optics:

```scala mdoc
exprToSql(Product.category.in("Electronics", "Books", "Toys"))

exprToSql(Product.price.between(10.0, 100.0))

exprToSql(Product.name.isNull)

exprToSql(Product.name.isNotNull)

exprToSql(Product.name.like("Lap%"))
```

Each extension method on `Optic` returns an `Expr` node. The interpreter handles it and produces the corresponding SQL fragment.

## Composing with SchemaExpr

The built-in `SchemaExpr` operators (`===`, `>`, `&&`, etc.) and the new `Expr` operators live in different type hierarchies. To combine them, lift `SchemaExpr` values into `Expr` using `.toExpr` or `Expr.wrap`:

```scala mdoc:silent
// Built-in: uses SchemaExpr operators
val highRated: SchemaExpr[Product, Boolean] = Product.rating >= 4

// Extended: uses Expr operators
val inCategory: Expr[Product, Boolean] = Product.category.in("Electronics", "Books")
val priceRange: Expr[Product, Boolean] = Product.price.between(10.0, 500.0)

// Compose: lift SchemaExpr into Expr world with .toExpr
val combined: Expr[Product, Boolean] =
  highRated.toExpr && inCategory && priceRange
```

```scala mdoc
exprToSql(combined)
```

:::warning
You must explicitly lift `SchemaExpr` values with `.toExpr` before combining them with `Expr` values using `&&` or `||`. The built-in `SchemaExpr.&&` method expects another `SchemaExpr`, not an `Expr`, so writing `(Product.rating >= 4) && Product.price.between(10.0, 500.0)` directly will not compile.
:::

You can also build entirely within the `Expr` world by converting SchemaExpr subexpressions inline:

```scala mdoc:silent
val query: Expr[Product, Boolean] =
  Product.category.in("Electronics", "Books") &&
  Product.price.between(10.0, 500.0) &&
  (Product.rating >= 4).toExpr &&
  Product.name.like("M%")
```

```scala mdoc
exprToSql(query)
```

A helper function to generate full SELECT statements from `Expr` predicates:

```scala mdoc:silent
def selectWhere(table: String, predicate: Expr[?, Boolean]): String =
  s"SELECT * FROM $table WHERE ${exprToSql(predicate)}"
```

```scala mdoc
selectWhere("products", query)
```

## Aggregate Expressions

The `Agg` node wraps any column expression with an aggregate function. Use the factory methods `Expr.count`, `Expr.sum`, `Expr.avg`, `Expr.min`, and `Expr.max`:

```scala mdoc
exprToSql(Expr.count(Expr.col(Product.name)))

exprToSql(Expr.avg(Expr.col(Product.price)))

exprToSql(Expr.max(Expr.col(Product.rating)))
```

Aggregates compose with the rest of the ADT. Build a `GROUP BY` query by combining aggregate SQL fragments with a select builder:

```scala mdoc:silent
def selectGroupBy(
  table: String,
  columns: List[String],
  groupBy: List[String],
  having: Option[String] = None
): String = {
  val base = s"SELECT ${columns.mkString(", ")} FROM $table GROUP BY ${groupBy.mkString(", ")}"
  having.fold(base)(h => s"$base HAVING $h")
}
```

```scala mdoc
selectGroupBy(
  "products",
  columns = List(
    "category",
    s"${exprToSql(Expr.count(Expr.col(Product.name)))} AS product_count",
    s"${exprToSql(Expr.avg(Expr.col(Product.price)))} AS avg_price"
  ),
  groupBy = List("category"),
  having = Some(s"${exprToSql(Expr.count(Expr.col(Product.name)))} > 2")
)
```

## CASE WHEN Expressions

The `CaseWhen` node represents SQL's conditional expression. Use the `Expr.caseWhen` builder with `(condition -> result)` pairs and an optional `.otherwise` clause:

```scala mdoc:silent
val priceLabel: Expr[Product, String] = Expr.caseWhen[Product, String](
  (Product.price > 100.0).toExpr  -> Expr.lit[Product, String]("expensive"),
  (Product.price > 10.0).toExpr   -> Expr.lit[Product, String]("moderate")
).otherwise(Expr.lit[Product, String]("cheap"))
```

```scala mdoc
exprToSql(priceLabel)
```

`CASE WHEN` is useful for computed columns in SELECT lists:

```scala mdoc:silent
val stockStatus: Expr[Product, String] = Expr.caseWhen[Product, String](
  (Product.inStock === true).toExpr -> Expr.lit[Product, String]("available")
).otherwise(Expr.lit[Product, String]("out of stock"))
```

```scala mdoc
val selectSql = s"SELECT name, price, ${exprToSql(priceLabel)} AS tier, ${exprToSql(stockStatus)} AS status FROM products"
println(selectSql)
```

## Putting It Together

Here is a complete, self-contained example that defines the extended expression ADT, writes the interpreter, and generates advanced SQL:

```scala mdoc:compile-only
import zio.blocks.schema._

// --- Domain ---

case class Product(
  name: String,
  price: Double,
  category: String,
  inStock: Boolean,
  rating: Int
)

object Product extends CompanionOptics[Product] {
  implicit val schema: Schema[Product] = Schema.derived

  val name: Lens[Product, String]     = optic(_.name)
  val price: Lens[Product, Double]    = optic(_.price)
  val category: Lens[Product, String] = optic(_.category)
  val inStock: Lens[Product, Boolean] = optic(_.inStock)
  val rating: Lens[Product, Int]      = optic(_.rating)
}

// --- Part 2 SQL helpers ---

def columnName(optic: zio.blocks.schema.Optic[?, ?]): String =
  optic.toDynamic.nodes.collect { case f: DynamicOptic.Node.Field => f.name }.mkString("_")

def sqlLiteral(value: Any): String = value match {
  case s: String  => s"'${s.replace("'", "''")}'"
  case b: Boolean => if (b) "TRUE" else "FALSE"
  case n: Number  => n.toString
  case other      => other.toString
}

def toSql[A, B](expr: SchemaExpr[A, B]): String = expr match {
  case SchemaExpr.Optic(optic)      => columnName(optic)
  case SchemaExpr.Literal(value, _) => sqlLiteral(value)
  case SchemaExpr.Relational(left, right, op) =>
    val sqlOp = op match {
      case SchemaExpr.RelationalOperator.Equal              => "="
      case SchemaExpr.RelationalOperator.NotEqual           => "<>"
      case SchemaExpr.RelationalOperator.LessThan           => "<"
      case SchemaExpr.RelationalOperator.LessThanOrEqual    => "<="
      case SchemaExpr.RelationalOperator.GreaterThan        => ">"
      case SchemaExpr.RelationalOperator.GreaterThanOrEqual => ">="
    }
    s"(${toSql(left)} $sqlOp ${toSql(right)})"
  case SchemaExpr.Logical(left, right, op) =>
    val sqlOp = op match {
      case SchemaExpr.LogicalOperator.And => "AND"
      case SchemaExpr.LogicalOperator.Or  => "OR"
    }
    s"(${toSql(left)} $sqlOp ${toSql(right)})"
  case SchemaExpr.Not(inner)                      => s"NOT (${toSql(inner)})"
  case SchemaExpr.Arithmetic(left, right, op, _) =>
    val sqlOp = op match {
      case SchemaExpr.ArithmeticOperator.Add      => "+"
      case SchemaExpr.ArithmeticOperator.Subtract => "-"
      case SchemaExpr.ArithmeticOperator.Multiply => "*"
    }
    s"(${toSql(left)} $sqlOp ${toSql(right)})"
  case SchemaExpr.StringConcat(left, right)       => s"CONCAT(${toSql(left)}, ${toSql(right)})"
  case SchemaExpr.StringRegexMatch(regex, string) => s"(${toSql(string)} LIKE ${toSql(regex)})"
  case SchemaExpr.StringLength(string)            => s"LENGTH(${toSql(string)})"
}

// --- Extended Expression ADT ---

sealed trait Expr[S, A]

object Expr {
  final case class Wrapped[S, A](expr: SchemaExpr[S, A]) extends Expr[S, A]
  final case class Column[S, A](optic: Optic[S, A]) extends Expr[S, A]
  final case class Lit[S, A](value: A) extends Expr[S, A]

  final case class In[S, A](expr: Expr[S, A], values: List[A]) extends Expr[S, Boolean]
  final case class Between[S, A](expr: Expr[S, A], low: A, high: A) extends Expr[S, Boolean]
  final case class IsNull[S, A](expr: Expr[S, A]) extends Expr[S, Boolean]
  final case class Like[S](expr: Expr[S, String], pattern: String) extends Expr[S, Boolean]

  final case class And[S](left: Expr[S, Boolean], right: Expr[S, Boolean]) extends Expr[S, Boolean]
  final case class Or[S](left: Expr[S, Boolean], right: Expr[S, Boolean]) extends Expr[S, Boolean]
  final case class Not[S](expr: Expr[S, Boolean]) extends Expr[S, Boolean]

  final case class Agg[S, A](function: AggFunction, expr: Expr[S, A]) extends Expr[S, A]
  final case class CaseWhen[S, A](
    branches: List[(Expr[S, Boolean], Expr[S, A])],
    otherwise: Option[Expr[S, A]]
  ) extends Expr[S, A]

  def wrap[S, A](expr: SchemaExpr[S, A]): Expr[S, A] = Wrapped(expr)
  def col[S, A](optic: Optic[S, A]): Expr[S, A] = Column(optic)
  def lit[S, A](value: A): Expr[S, A] = Lit(value)
  def count[S, A](expr: Expr[S, A]): Expr[S, A] = Agg(AggFunction.Count, expr)
  def sum[S, A](expr: Expr[S, A]): Expr[S, A] = Agg(AggFunction.Sum, expr)
  def avg[S, A](expr: Expr[S, A]): Expr[S, A] = Agg(AggFunction.Avg, expr)
  def min[S, A](expr: Expr[S, A]): Expr[S, A] = Agg(AggFunction.Min, expr)
  def max[S, A](expr: Expr[S, A]): Expr[S, A] = Agg(AggFunction.Max, expr)

  def caseWhen[S, A](branches: (Expr[S, Boolean], Expr[S, A])*): CaseWhenBuilder[S, A] =
    CaseWhenBuilder(branches.toList)

  case class CaseWhenBuilder[S, A](branches: List[(Expr[S, Boolean], Expr[S, A])]) {
    def otherwise(value: Expr[S, A]): Expr[S, A] = CaseWhen(branches, Some(value))
    def end: Expr[S, A] = CaseWhen(branches, None)
  }
}

sealed trait AggFunction
object AggFunction {
  case object Count extends AggFunction
  case object Sum   extends AggFunction
  case object Avg   extends AggFunction
  case object Min   extends AggFunction
  case object Max   extends AggFunction
}

// --- Extension methods ---

extension [S, A](optic: Optic[S, A]) {
  def in(values: A*): Expr[S, Boolean] = Expr.In(Expr.col(optic), values.toList)
  def between(low: A, high: A): Expr[S, Boolean] = Expr.Between(Expr.col(optic), low, high)
  def isNull: Expr[S, Boolean] = Expr.IsNull(Expr.col(optic))
  def isNotNull: Expr[S, Boolean] = Expr.Not(Expr.IsNull(Expr.col(optic)))
}

extension [S](optic: Optic[S, String]) {
  def like(pattern: String): Expr[S, Boolean] = Expr.Like(Expr.col(optic), pattern)
}

extension [S](expr: Expr[S, Boolean]) {
  def &&(other: Expr[S, Boolean]): Expr[S, Boolean] = Expr.And(expr, other)
  def ||(other: Expr[S, Boolean]): Expr[S, Boolean] = Expr.Or(expr, other)
  def unary_! : Expr[S, Boolean] = Expr.Not(expr)
}

extension [S, A](expr: SchemaExpr[S, A]) {
  def toExpr: Expr[S, A] = Expr.Wrapped(expr)
}

// --- Extended SQL interpreter ---

def exprToSql[S, A](expr: Expr[S, A]): String = expr match {
  case Expr.Wrapped(schemaExpr) => toSql(schemaExpr)
  case Expr.Column(optic)      => columnName(optic)
  case Expr.Lit(value)         => sqlLiteral(value)
  case Expr.In(e, values)      =>
    s"${exprToSql(e)} IN (${values.map(v => sqlLiteral(v)).mkString(", ")})"
  case Expr.Between(e, low, high) =>
    s"(${exprToSql(e)} BETWEEN ${sqlLiteral(low)} AND ${sqlLiteral(high)})"
  case Expr.IsNull(e)          => s"${exprToSql(e)} IS NULL"
  case Expr.Like(e, pattern)   => s"${exprToSql(e)} LIKE '${pattern.replace("'", "''")}'"
  case Expr.And(l, r)          => s"(${exprToSql(l)} AND ${exprToSql(r)})"
  case Expr.Or(l, r)           => s"(${exprToSql(l)} OR ${exprToSql(r)})"
  case Expr.Not(e)             => s"NOT (${exprToSql(e)})"
  case Expr.Agg(func, e)      =>
    val name = func match {
      case AggFunction.Count => "COUNT"
      case AggFunction.Sum   => "SUM"
      case AggFunction.Avg   => "AVG"
      case AggFunction.Min   => "MIN"
      case AggFunction.Max   => "MAX"
    }
    s"$name(${exprToSql(e)})"
  case Expr.CaseWhen(branches, otherwise) =>
    val cases = branches.map { (cond, value) =>
      s"WHEN ${exprToSql(cond)} THEN ${exprToSql(value)}"
    }.mkString(" ")
    val elseClause = otherwise.map(e => s" ELSE ${exprToSql(e)}").getOrElse("")
    s"CASE $cases$elseClause END"
}

// --- Usage ---

// 1. SQL-specific predicates
val q1 = Product.category.in("Electronics", "Books") &&
         Product.price.between(10.0, 500.0) &&
         (Product.rating >= 4).toExpr &&
         Product.name.like("M%")

println(s"SELECT * FROM products WHERE ${exprToSql(q1)}")

// 2. Aggregation with GROUP BY
val countSql = exprToSql(Expr.count(Expr.col(Product.name)))
val avgSql   = exprToSql(Expr.avg(Expr.col(Product.price)))
println(s"SELECT category, $countSql AS cnt, $avgSql AS avg_price FROM products GROUP BY category")

// 3. CASE WHEN
val tier = Expr.caseWhen[Product, String](
  (Product.price > 100.0).toExpr -> Expr.lit[Product, String]("expensive"),
  (Product.price > 10.0).toExpr  -> Expr.lit[Product, String]("moderate")
).otherwise(Expr.lit[Product, String]("cheap"))

println(s"SELECT name, price, ${exprToSql(tier)} AS tier FROM products")
```

## Going Further

- **[Part 1: Expressions](./query-dsl-reified-optics.md)** -- Building query expressions with reified optics
- **[Part 2: SQL Generation](./query-dsl-sql.md)** -- Translating built-in expressions to SQL
- **[Part 4: A Fluent SQL Builder](./query-dsl-fluent-builder.md)** -- Type-safe SELECT, UPDATE, INSERT, DELETE with seamless condition mixing
- **[SchemaExpr Reference](../reference/schema-expr.md)** -- Full API coverage of expression types
- **[Optics Reference](../reference/optics.md)** -- Lens, Prism, Optional, and Traversal

The wrapper pattern shown here extends to any domain where `SchemaExpr` falls short. The same approach works for MongoDB operators (`$in`, `$exists`, `$elemMatch`), Elasticsearch queries (`terms`, `range`, `exists`), or GraphQL filters. Define a custom ADT, embed `SchemaExpr` as one case, add your domain-specific nodes, and write an interpreter that delegates to the built-in `toSql` (or equivalent) for the `Wrapped` case.

You can also add parameterized query support to the extended interpreter by following the same pattern from Part 2: replace `exprToSql` with `exprToParameterized` that returns `SqlQuery(sql, params)` and emits `?` placeholders for literal values.
