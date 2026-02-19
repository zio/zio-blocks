---
id: query-dsl-fluent-builder
title: "Query DSL with Reified Optics — Part 4: A Fluent SQL Builder"
---

In this guide, we will build a fluent, type-safe SQL statement builder on top of the query expression language from Parts 1–3. By the end, you will have builder functions for SELECT, UPDATE, INSERT, and DELETE that compose naturally with both built-in `SchemaExpr` operators and extended `Expr` operators — no manual `.toExpr` lifting required.

We'll take an incremental approach: starting with seamless condition composition, then adding table references and fluent statement builders for each SQL operation until we have a complete, native-feeling SQL DSL.

This is Part 4 of the Query DSL series. [Part 1](./query-dsl-reified-optics.md) covered expressions, [Part 2](./query-dsl-sql.md) covered SQL generation, and [Part 3](./query-dsl-extending.md) covered extending the expression language.

**What we'll cover:**

- Bridging `SchemaExpr` and `Expr` for seamless `&&` / `||` composition
- Defining table references with `Table[S]`
- Building fluent SELECT queries with `.columns()`, `.where()`, `.orderBy()`, `.limit()`
- Building UPDATE statements with `.set()` and `.where()`
- Building INSERT and DELETE statements
- Rendering all statement types to SQL strings

## The Problem

Parts 1–3 gave us a powerful expression language for building WHERE clauses. But constructing full SQL statements still requires string concatenation:

```scala
val whereSql = exprToSql(myCondition)
val sql = s"UPDATE products SET price = 9.99 WHERE $whereSql"
```

Column names in SET clauses are hand-written strings that can drift from your schema, table names are repeated magic strings, and there is no compile-time connection between the statement structure and your domain types.

Additionally, mixing built-in `SchemaExpr` operators (`===`, `>=`) with extended `Expr` operators (`between`, `like`) required explicit `.toExpr` calls in Part 3:

```scala
// Part 3: explicit .toExpr required
val q = (Product.rating >= 4).toExpr && Product.price.between(10.0, 500.0)
```

In a fluent builder, this friction breaks the flow. We want to write:

```scala
update(Product.table)
  .set(Product.price, 9.99)
  .where(
    Product.category === "Books" &&
      Product.rating >= 4 &&
      Product.price.between(10.0, 30.0) &&
      Product.name.like("M%")
  )
```

In this guide, we solve both problems: bridge extensions eliminate `.toExpr`, and type-safe statement builders eliminate string manipulation for SQL construction.

## Prerequisites

This guide builds on [Part 1: Expressions](./query-dsl-reified-optics.md), [Part 2: SQL Generation](./query-dsl-sql.md), and [Part 3: Extending the Expression Language](./query-dsl-extending.md).

```scala
libraryDependencies += "dev.zio" %% "zio-blocks-schema" % "@VERSION@"
```

## Domain Setup

We carry forward the product catalog domain and the Part 3 independent `Expr` ADT. The key additions in Part 4 are bridge extension methods and statement builder types. The `Expr` ADT used here is the same independent design from Part 3.

```scala mdoc:silent
import zio.blocks.schema._

// --- Table reference ---

case class Table[S](name: String)

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

  val table: Table[Product] = Table("products")

  val name: Lens[Product, String]     = optic(_.name)
  val price: Lens[Product, Double]    = optic(_.price)
  val category: Lens[Product, String] = optic(_.category)
  val inStock: Lens[Product, Boolean] = optic(_.inStock)
  val rating: Lens[Product, Int]      = optic(_.rating)
}

// --- Expr ADT (from Part 3) ---

sealed trait Expr[S, A]

object Expr {
  final case class Column[S, A](optic: Optic[S, A]) extends Expr[S, A]
  final case class Lit[S, A](value: A, schema: Schema[A]) extends Expr[S, A]

  final case class Relational[S, A](left: Expr[S, A], right: Expr[S, A], op: RelOp) extends Expr[S, Boolean]
  final case class And[S](left: Expr[S, Boolean], right: Expr[S, Boolean]) extends Expr[S, Boolean]
  final case class Or[S](left: Expr[S, Boolean], right: Expr[S, Boolean]) extends Expr[S, Boolean]
  final case class Not[S](expr: Expr[S, Boolean]) extends Expr[S, Boolean]
  final case class Arithmetic[S, A](left: Expr[S, A], right: Expr[S, A], op: ArithOp) extends Expr[S, A]
  final case class StringConcat[S](left: Expr[S, String], right: Expr[S, String]) extends Expr[S, String]
  final case class StringRegexMatch[S](regex: Expr[S, String], string: Expr[S, String]) extends Expr[S, Boolean]
  final case class StringLength[S](string: Expr[S, String]) extends Expr[S, Int]

  final case class In[S, A](expr: Expr[S, A], values: List[A], schema: Schema[A]) extends Expr[S, Boolean]
  final case class Between[S, A](expr: Expr[S, A], low: A, high: A, schema: Schema[A]) extends Expr[S, Boolean]
  final case class IsNull[S, A](expr: Expr[S, A]) extends Expr[S, Boolean]
  final case class Like[S](expr: Expr[S, String], pattern: String) extends Expr[S, Boolean]

  def col[S, A](optic: Optic[S, A]): Expr[S, A] = Column(optic)
  def lit[S, A](value: A)(implicit schema: Schema[A]): Expr[S, A] = Lit(value, schema)

  def fromSchemaExpr[S, A](se: SchemaExpr[S, A]): Expr[S, A] = {
    val result = se match {
      case SchemaExpr.Optic(optic)      => Column(optic)
      case l: SchemaExpr.Literal[_, _]  => Lit(l.value, l.schema)
      case SchemaExpr.Relational(l, r, op) =>
        val relOp = op match {
          case SchemaExpr.RelationalOperator.Equal              => RelOp.Equal
          case SchemaExpr.RelationalOperator.NotEqual           => RelOp.NotEqual
          case SchemaExpr.RelationalOperator.LessThan           => RelOp.LessThan
          case SchemaExpr.RelationalOperator.LessThanOrEqual    => RelOp.LessThanOrEqual
          case SchemaExpr.RelationalOperator.GreaterThan        => RelOp.GreaterThan
          case SchemaExpr.RelationalOperator.GreaterThanOrEqual => RelOp.GreaterThanOrEqual
        }
        Relational(fromSchemaExpr(l), fromSchemaExpr(r), relOp)
      case SchemaExpr.Logical(l, r, op) => op match {
        case SchemaExpr.LogicalOperator.And => And(fromSchemaExpr(l), fromSchemaExpr(r))
        case SchemaExpr.LogicalOperator.Or  => Or(fromSchemaExpr(l), fromSchemaExpr(r))
      }
      case SchemaExpr.Not(inner) => Not(fromSchemaExpr(inner))
      case SchemaExpr.Arithmetic(l, r, op, _) =>
        val arithOp = op match {
          case SchemaExpr.ArithmeticOperator.Add      => ArithOp.Add
          case SchemaExpr.ArithmeticOperator.Subtract => ArithOp.Subtract
          case SchemaExpr.ArithmeticOperator.Multiply => ArithOp.Multiply
        }
        Arithmetic(fromSchemaExpr(l), fromSchemaExpr(r), arithOp)
      case SchemaExpr.StringConcat(l, r)              => StringConcat(fromSchemaExpr(l), fromSchemaExpr(r))
      case SchemaExpr.StringRegexMatch(regex, string) => StringRegexMatch(fromSchemaExpr(regex), fromSchemaExpr(string))
      case SchemaExpr.StringLength(string)            => StringLength(fromSchemaExpr(string))
    }
    result.asInstanceOf[Expr[S, A]]
  }
}

sealed trait RelOp
object RelOp {
  case object Equal              extends RelOp
  case object NotEqual           extends RelOp
  case object LessThan           extends RelOp
  case object LessThanOrEqual    extends RelOp
  case object GreaterThan        extends RelOp
  case object GreaterThanOrEqual extends RelOp
}

sealed trait ArithOp
object ArithOp {
  case object Add      extends ArithOp
  case object Subtract extends ArithOp
  case object Multiply extends ArithOp
}

// --- SQL helpers ---

def columnName(optic: zio.blocks.schema.Optic[_, _]): String =
  optic.toDynamic.nodes.collect { case f: DynamicOptic.Node.Field => f.name }.mkString("_")

def sqlLiteral[A](value: A, schema: Schema[A]): String = {
  val dv = schema.toDynamicValue(value)
  dv match {
    case p: DynamicValue.Primitive => p.value match {
      case _: PrimitiveValue.String  => s"'${value.toString.replace("'", "''")}'"
      case b: PrimitiveValue.Boolean => if (b.value) "TRUE" else "FALSE"
      case _                         => value.toString
    }
    case _ => value.toString
  }
}

// --- Optic extension methods ---

implicit final class OpticExprOps[S, A](private val optic: Optic[S, A]) {
  def in(values: A*)(implicit schema: Schema[A]): Expr[S, Boolean]           = Expr.In(Expr.col(optic), values.toList, schema)
  def between(low: A, high: A)(implicit schema: Schema[A]): Expr[S, Boolean] = Expr.Between(Expr.col(optic), low, high, schema)
  def isNull: Expr[S, Boolean]                   = Expr.IsNull(Expr.col(optic))
  def isNotNull: Expr[S, Boolean]                = Expr.Not(Expr.IsNull(Expr.col(optic)))
}

implicit final class StringOpticExprOps[S](private val optic: Optic[S, String]) {
  def like(pattern: String): Expr[S, Boolean] = Expr.Like(Expr.col(optic), pattern)
}

// --- Boolean combinators with bridge extensions ---

implicit final class ExprBooleanOps[S](private val self: Expr[S, Boolean]) {
  def &&(other: Expr[S, Boolean]): Expr[S, Boolean]      = Expr.And(self, other)
  def &&(other: SchemaExpr[S, Boolean]): Expr[S, Boolean] = Expr.And(self, Expr.fromSchemaExpr(other))
  def ||(other: Expr[S, Boolean]): Expr[S, Boolean]      = Expr.Or(self, other)
  def ||(other: SchemaExpr[S, Boolean]): Expr[S, Boolean] = Expr.Or(self, Expr.fromSchemaExpr(other))
  def unary_! : Expr[S, Boolean]                          = Expr.Not(self)
}

implicit final class SchemaExprBooleanBridge[S](private val self: SchemaExpr[S, Boolean]) {
  def &&(other: Expr[S, Boolean]): Expr[S, Boolean] = Expr.And(Expr.fromSchemaExpr(self), other)
  def ||(other: Expr[S, Boolean]): Expr[S, Boolean] = Expr.Or(Expr.fromSchemaExpr(self), other)
  def toExpr: Expr[S, Boolean] = Expr.fromSchemaExpr(self)
}

// --- Single unified SQL interpreter ---

def exprToSql[S, A](expr: Expr[S, A]): String = expr match {
  case Expr.Column(optic)      => columnName(optic)
  case Expr.Lit(value, schema) => sqlLiteral(value, schema)
  case Expr.Relational(left, right, op) =>
    val sqlOp = op match {
      case RelOp.Equal              => "="
      case RelOp.NotEqual           => "<>"
      case RelOp.LessThan           => "<"
      case RelOp.LessThanOrEqual    => "<="
      case RelOp.GreaterThan        => ">"
      case RelOp.GreaterThanOrEqual => ">="
    }
    s"(${exprToSql(left)} $sqlOp ${exprToSql(right)})"
  case Expr.And(l, r) => s"(${exprToSql(l)} AND ${exprToSql(r)})"
  case Expr.Or(l, r)  => s"(${exprToSql(l)} OR ${exprToSql(r)})"
  case Expr.Not(e)    => s"NOT (${exprToSql(e)})"
  case Expr.Arithmetic(left, right, op) =>
    val sqlOp = op match {
      case ArithOp.Add      => "+"
      case ArithOp.Subtract => "-"
      case ArithOp.Multiply => "*"
    }
    s"(${exprToSql(left)} $sqlOp ${exprToSql(right)})"
  case Expr.StringConcat(l, r)         => s"CONCAT(${exprToSql(l)}, ${exprToSql(r)})"
  case Expr.StringRegexMatch(regex, s) => s"(${exprToSql(s)} LIKE ${exprToSql(regex)})"
  case Expr.StringLength(s)            => s"LENGTH(${exprToSql(s)})"
  case Expr.In(e, values, schema) =>
    s"${exprToSql(e)} IN (${values.map(v => sqlLiteral(v, schema)).mkString(", ")})"
  case Expr.Between(e, low, high, schema) =>
    s"(${exprToSql(e)} BETWEEN ${sqlLiteral(low, schema)} AND ${sqlLiteral(high, schema)})"
  case Expr.IsNull(e)        => s"${exprToSql(e)} IS NULL"
  case Expr.Like(e, pattern) => s"${exprToSql(e)} LIKE '${pattern.replace("'", "''")}'"
}
```

## Seamless Condition Composition

In Part 3, composing `SchemaExpr` and `Expr` values required explicit `.toExpr` calls because `SchemaExpr.&&` is a direct method that only accepts other `SchemaExpr` values. Part 4 adds **bridge implicit classes** that handle the cross-type case automatically:

```scala
// When SchemaExpr.&& receives an Expr argument, the direct method
// doesn't match (Expr is not SchemaExpr), so this implicit class kicks in:
implicit final class SchemaExprBooleanBridge[S](
  private val self: SchemaExpr[S, Boolean]
) extends AnyVal {
  def &&(other: Expr[S, Boolean]): Expr[S, Boolean] = ...
}

// And vice versa — Expr.&& can accept SchemaExpr directly:
implicit final class ExprBooleanOps[S](
  private val self: Expr[S, Boolean]
) extends AnyVal {
  def &&(other: SchemaExpr[S, Boolean]): Expr[S, Boolean] = ...
}
```

Scala's implicit conversion resolution makes this work:

1. **`SchemaExpr && SchemaExpr`** — the built-in direct method matches, returns `SchemaExpr`
2. **`SchemaExpr && Expr`** — the direct method doesn't match, the bridge implicit class kicks in, returns `Expr`
3. **`Expr && SchemaExpr`** — the bridge overload on `ExprBooleanOps` matches, returns `Expr`
4. **`Expr && Expr`** — the standard `ExprBooleanOps.&&` overload matches, returns `Expr`

The result type automatically widens to `Expr` whenever an `Expr` value enters the chain. No `.toExpr` needed:

```scala mdoc
val condition =
  Product.price.between(10.0, 500.0) &&
  (Product.category === "Electronics") &&
  (Product.rating >= 4) &&
  Product.name.like("L%")

exprToSql(condition)
```

The first two `&&` calls stay in `SchemaExpr` land (direct method). The third `&&` encounters `between` (returns `Expr`), triggering the bridge. From that point on, everything is `Expr`.

## Table References

The `Table[S]` type associates a SQL table name with a schema type. Add it to each companion object:

```scala
case class Table[S](name: String)

object Product extends CompanionOptics[Product] {
  val table: Table[Product] = Table("products")
  // ... lenses
}
```

`Table[S]` is the entry point for every statement builder. The type parameter `S` connects the table to its domain type, ensuring you can only use `Product` lenses in queries against `Product.table`.

## The SELECT Builder

The SELECT builder uses immutable case classes with `copy` for a fluent API:

```scala mdoc:silent
sealed trait SortOrder
object SortOrder {
  case object Asc  extends SortOrder
  case object Desc extends SortOrder
}

case class SelectStmt[S](
  table: Table[S],
  columnList: List[String] = List("*"),
  whereExpr: Option[Expr[S, Boolean]] = None,
  orderByList: List[(String, SortOrder)] = Nil,
  limitCount: Option[Int] = None
) {
  def columns(optics: Optic[S, _]*): SelectStmt[S] =
    copy(columnList = optics.map(columnName).toList)
  def where(cond: Expr[S, Boolean]): SelectStmt[S] =
    copy(whereExpr = Some(cond))
  def where(cond: SchemaExpr[S, Boolean]): SelectStmt[S] =
    copy(whereExpr = Some(Expr.fromSchemaExpr(cond)))
  def orderBy(optic: Optic[S, _], order: SortOrder = SortOrder.Asc): SelectStmt[S] =
    copy(orderByList = orderByList :+ (columnName(optic), order))
  def limit(n: Int): SelectStmt[S] =
    copy(limitCount = Some(n))
}

def select[S](table: Table[S]): SelectStmt[S] = SelectStmt(table)

def renderSelect[S](stmt: SelectStmt[S]): String = {
  val cols = stmt.columnList.mkString(", ")
  val where = stmt.whereExpr.map(c => s" WHERE ${exprToSql(c)}").getOrElse("")
  val orderBy = if (stmt.orderByList.isEmpty) "" else {
    val orders = stmt.orderByList.map { case (col, order) =>
      val dir = order match { case SortOrder.Asc => "ASC"; case SortOrder.Desc => "DESC" }
      s"$col $dir"
    }.mkString(", ")
    s" ORDER BY $orders"
  }
  val limit = stmt.limitCount.map(n => s" LIMIT $n").getOrElse("")
  s"SELECT $cols FROM ${stmt.table.name}$where$orderBy$limit"
}
```

Each builder method returns a new `SelectStmt` with the updated field. The `.where()` method is overloaded to accept both `Expr` and `SchemaExpr` — `SchemaExpr` values are translated via `fromSchemaExpr`, so pure `SchemaExpr` chains and mixed chains both work:

```scala mdoc
// Pure SchemaExpr conditions
val basicSelect = select(Product.table)
  .columns(Product.name, Product.price)
  .where(Product.inStock === true)

renderSelect(basicSelect)

// Mixed conditions with ordering and limit
val advancedSelect = select(Product.table)
  .columns(Product.name, Product.price, Product.category)
  .where(
    Product.price.between(10.0, 500.0) &&
    (Product.category === "Electronics") &&
    (Product.rating >= 4)
  )
  .orderBy(Product.price, SortOrder.Desc)
  .limit(10)

renderSelect(advancedSelect)
```

## The UPDATE Builder

The UPDATE builder accumulates `.set()` calls, each pairing an optic with a value:

```scala mdoc:silent
case class Assignment(column: String, value: String)

case class UpdateStmt[S](
  table: Table[S],
  assignments: List[Assignment] = Nil,
  whereExpr: Option[Expr[S, Boolean]] = None
) {
  def set[A](optic: Optic[S, A], value: A)(implicit schema: Schema[A]): UpdateStmt[S] =
    copy(assignments = assignments :+ Assignment(columnName(optic), sqlLiteral(value, schema)))
  def where(cond: Expr[S, Boolean]): UpdateStmt[S] =
    copy(whereExpr = Some(cond))
  def where(cond: SchemaExpr[S, Boolean]): UpdateStmt[S] =
    copy(whereExpr = Some(Expr.fromSchemaExpr(cond)))
}

def update[S](table: Table[S]): UpdateStmt[S] = UpdateStmt(table)

def renderUpdate[S](stmt: UpdateStmt[S]): String = {
  val sets = stmt.assignments.map(a => s"${a.column} = ${a.value}").mkString(", ")
  val where = stmt.whereExpr.map(c => s" WHERE ${exprToSql(c)}").getOrElse("")
  s"UPDATE ${stmt.table.name} SET $sets$where"
}
```

The `.set()` method uses the optic to extract the column name and `sqlLiteral` (via the implicit `Schema[A]`) to render the value. The type parameter `A` on `set[A](optic: Optic[S, A], value: A)` ensures you cannot assign a `String` to a `Double` field.

```scala mdoc
val basicUpdate =
  update(Product.table)
    .set(Product.price, 9.99)
    .where(
      Product.price.between(10.0, 30.0) &&
        Product.name.like("M%") &&
        (Product.category === "Books") &&
        (Product.rating >= 4) &&
        (Product.inStock === true)
    )

renderUpdate(basicUpdate)
```

Multiple `.set()` calls accumulate:

```scala mdoc
val multiUpdate =
  update(Product.table)
    .set(Product.price, 19.99)
    .set(Product.inStock, false)
    .where(Product.category === "Clearance")

renderUpdate(multiUpdate)
```

## INSERT and DELETE

The INSERT builder collects column-value pairs via `.set()`:

```scala mdoc:silent
case class InsertStmt[S](
  table: Table[S],
  assignments: List[Assignment] = Nil
) {
  def set[A](optic: Optic[S, A], value: A)(implicit schema: Schema[A]): InsertStmt[S] =
    copy(assignments = assignments :+ Assignment(columnName(optic), sqlLiteral(value, schema)))
}

def insertInto[S](table: Table[S]): InsertStmt[S] = InsertStmt(table)

def renderInsert[S](stmt: InsertStmt[S]): String = {
  val cols = stmt.assignments.map(_.column).mkString(", ")
  val vals = stmt.assignments.map(_.value).mkString(", ")
  s"INSERT INTO ${stmt.table.name} ($cols) VALUES ($vals)"
}
```

The DELETE builder takes an optional WHERE clause:

```scala mdoc:silent
case class DeleteStmt[S](
  table: Table[S],
  whereExpr: Option[Expr[S, Boolean]] = None
) {
  def where(cond: Expr[S, Boolean]): DeleteStmt[S] =
    copy(whereExpr = Some(cond))
  def where(cond: SchemaExpr[S, Boolean]): DeleteStmt[S] =
    copy(whereExpr = Some(Expr.fromSchemaExpr(cond)))
}

def deleteFrom[S](table: Table[S]): DeleteStmt[S] = DeleteStmt(table)

def renderDelete[S](stmt: DeleteStmt[S]): String = {
  val where = stmt.whereExpr.map(c => s" WHERE ${exprToSql(c)}").getOrElse("")
  s"DELETE FROM ${stmt.table.name}$where"
}
```

```scala mdoc
val ins = insertInto(Product.table)
  .set(Product.name, "Wireless Mouse")
  .set(Product.price, 29.99)
  .set(Product.category, "Electronics")
  .set(Product.inStock, true)
  .set(Product.rating, 4)

renderInsert(ins)

val del = deleteFrom(Product.table)
  .where(
    Product.price.between(0.0, 1.0) &&
    (Product.inStock === false)
  )

renderDelete(del)
```

:::tip
For batch inserts, create one `InsertStmt` per row and render each separately. The builder pattern keeps each row type-safe.
:::

## Putting It Together

Here is a complete example combining the bridge extensions, all four statement builders, and the renderers. The `Expr` ADT, extension methods, SQL rendering, and builder types are defined in `Common.scala` and `package.scala` — the usage code stays focused on building queries:

```scala mdoc:compile-only
import zio.blocks.schema._

// --- Table reference ---

case class Table[S](name: String)

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

  val table: Table[Product] = Table("products")

  val name: Lens[Product, String]     = optic(_.name)
  val price: Lens[Product, Double]    = optic(_.price)
  val category: Lens[Product, String] = optic(_.category)
  val inStock: Lens[Product, Boolean] = optic(_.inStock)
  val rating: Lens[Product, Int]      = optic(_.rating)
}

// --- Expr ADT ---

sealed trait Expr[S, A]

object Expr {
  final case class Column[S, A](optic: Optic[S, A]) extends Expr[S, A]
  final case class Lit[S, A](value: A, schema: Schema[A]) extends Expr[S, A]

  final case class Relational[S, A](left: Expr[S, A], right: Expr[S, A], op: RelOp) extends Expr[S, Boolean]
  final case class And[S](left: Expr[S, Boolean], right: Expr[S, Boolean]) extends Expr[S, Boolean]
  final case class Or[S](left: Expr[S, Boolean], right: Expr[S, Boolean]) extends Expr[S, Boolean]
  final case class Not[S](expr: Expr[S, Boolean]) extends Expr[S, Boolean]
  final case class Arithmetic[S, A](left: Expr[S, A], right: Expr[S, A], op: ArithOp) extends Expr[S, A]
  final case class StringConcat[S](left: Expr[S, String], right: Expr[S, String]) extends Expr[S, String]
  final case class StringRegexMatch[S](regex: Expr[S, String], string: Expr[S, String]) extends Expr[S, Boolean]
  final case class StringLength[S](string: Expr[S, String]) extends Expr[S, Int]

  final case class In[S, A](expr: Expr[S, A], values: List[A], schema: Schema[A]) extends Expr[S, Boolean]
  final case class Between[S, A](expr: Expr[S, A], low: A, high: A, schema: Schema[A]) extends Expr[S, Boolean]
  final case class IsNull[S, A](expr: Expr[S, A]) extends Expr[S, Boolean]
  final case class Like[S](expr: Expr[S, String], pattern: String) extends Expr[S, Boolean]

  def col[S, A](optic: Optic[S, A]): Expr[S, A] = Column(optic)
  def lit[S, A](value: A)(implicit schema: Schema[A]): Expr[S, A] = Lit(value, schema)

  def fromSchemaExpr[S, A](se: SchemaExpr[S, A]): Expr[S, A] = {
    val result = se match {
      case SchemaExpr.Optic(optic)      => Column(optic)
      case l: SchemaExpr.Literal[_, _]  => Lit(l.value, l.schema)
      case SchemaExpr.Relational(l, r, op) =>
        val relOp = op match {
          case SchemaExpr.RelationalOperator.Equal              => RelOp.Equal
          case SchemaExpr.RelationalOperator.NotEqual           => RelOp.NotEqual
          case SchemaExpr.RelationalOperator.LessThan           => RelOp.LessThan
          case SchemaExpr.RelationalOperator.LessThanOrEqual    => RelOp.LessThanOrEqual
          case SchemaExpr.RelationalOperator.GreaterThan        => RelOp.GreaterThan
          case SchemaExpr.RelationalOperator.GreaterThanOrEqual => RelOp.GreaterThanOrEqual
        }
        Relational(fromSchemaExpr(l), fromSchemaExpr(r), relOp)
      case SchemaExpr.Logical(l, r, op) => op match {
        case SchemaExpr.LogicalOperator.And => And(fromSchemaExpr(l), fromSchemaExpr(r))
        case SchemaExpr.LogicalOperator.Or  => Or(fromSchemaExpr(l), fromSchemaExpr(r))
      }
      case SchemaExpr.Not(inner) => Not(fromSchemaExpr(inner))
      case SchemaExpr.Arithmetic(l, r, op, _) =>
        val arithOp = op match {
          case SchemaExpr.ArithmeticOperator.Add      => ArithOp.Add
          case SchemaExpr.ArithmeticOperator.Subtract => ArithOp.Subtract
          case SchemaExpr.ArithmeticOperator.Multiply => ArithOp.Multiply
        }
        Arithmetic(fromSchemaExpr(l), fromSchemaExpr(r), arithOp)
      case SchemaExpr.StringConcat(l, r)              => StringConcat(fromSchemaExpr(l), fromSchemaExpr(r))
      case SchemaExpr.StringRegexMatch(regex, string) => StringRegexMatch(fromSchemaExpr(regex), fromSchemaExpr(string))
      case SchemaExpr.StringLength(string)            => StringLength(fromSchemaExpr(string))
    }
    result.asInstanceOf[Expr[S, A]]
  }
}

sealed trait RelOp
object RelOp {
  case object Equal              extends RelOp
  case object NotEqual           extends RelOp
  case object LessThan           extends RelOp
  case object LessThanOrEqual    extends RelOp
  case object GreaterThan        extends RelOp
  case object GreaterThanOrEqual extends RelOp
}

sealed trait ArithOp
object ArithOp {
  case object Add      extends ArithOp
  case object Subtract extends ArithOp
  case object Multiply extends ArithOp
}

// --- Extension methods with bridge ---

implicit final class OpticExprOps[S, A](private val optic: Optic[S, A]) {
  def in(values: A*)(implicit schema: Schema[A]): Expr[S, Boolean]           = Expr.In(Expr.col(optic), values.toList, schema)
  def between(low: A, high: A)(implicit schema: Schema[A]): Expr[S, Boolean] = Expr.Between(Expr.col(optic), low, high, schema)
  def isNull: Expr[S, Boolean]                   = Expr.IsNull(Expr.col(optic))
  def isNotNull: Expr[S, Boolean]                = Expr.Not(Expr.IsNull(Expr.col(optic)))
}

implicit final class StringOpticExprOps[S](private val optic: Optic[S, String]) {
  def like(pattern: String): Expr[S, Boolean] = Expr.Like(Expr.col(optic), pattern)
}

implicit final class ExprBooleanOps[S](private val self: Expr[S, Boolean]) {
  def &&(other: Expr[S, Boolean]): Expr[S, Boolean]      = Expr.And(self, other)
  def &&(other: SchemaExpr[S, Boolean]): Expr[S, Boolean] = Expr.And(self, Expr.fromSchemaExpr(other))
  def ||(other: Expr[S, Boolean]): Expr[S, Boolean]      = Expr.Or(self, other)
  def ||(other: SchemaExpr[S, Boolean]): Expr[S, Boolean] = Expr.Or(self, Expr.fromSchemaExpr(other))
  def unary_! : Expr[S, Boolean]                          = Expr.Not(self)
}

implicit final class SchemaExprBooleanBridge[S](private val self: SchemaExpr[S, Boolean]) {
  def &&(other: Expr[S, Boolean]): Expr[S, Boolean] = Expr.And(Expr.fromSchemaExpr(self), other)
  def ||(other: Expr[S, Boolean]): Expr[S, Boolean] = Expr.Or(Expr.fromSchemaExpr(self), other)
  def toExpr: Expr[S, Boolean] = Expr.fromSchemaExpr(self)
}

// --- SQL rendering ---

def columnName(optic: zio.blocks.schema.Optic[_, _]): String =
  optic.toDynamic.nodes.collect { case f: DynamicOptic.Node.Field => f.name }.mkString("_")

def sqlLiteral[A](value: A, schema: Schema[A]): String = {
  val dv = schema.toDynamicValue(value)
  dv match {
    case p: DynamicValue.Primitive => p.value match {
      case _: PrimitiveValue.String  => s"'${value.toString.replace("'", "''")}'"
      case b: PrimitiveValue.Boolean => if (b.value) "TRUE" else "FALSE"
      case _                         => value.toString
    }
    case _ => value.toString
  }
}

def exprToSql[S, A](expr: Expr[S, A]): String = expr match {
  case Expr.Column(optic)      => columnName(optic)
  case Expr.Lit(value, schema) => sqlLiteral(value, schema)
  case Expr.Relational(left, right, op) =>
    val sqlOp = op match {
      case RelOp.Equal              => "="
      case RelOp.NotEqual           => "<>"
      case RelOp.LessThan           => "<"
      case RelOp.LessThanOrEqual    => "<="
      case RelOp.GreaterThan        => ">"
      case RelOp.GreaterThanOrEqual => ">="
    }
    s"(${exprToSql(left)} $sqlOp ${exprToSql(right)})"
  case Expr.And(l, r) => s"(${exprToSql(l)} AND ${exprToSql(r)})"
  case Expr.Or(l, r)  => s"(${exprToSql(l)} OR ${exprToSql(r)})"
  case Expr.Not(e)    => s"NOT (${exprToSql(e)})"
  case Expr.Arithmetic(left, right, op) =>
    val sqlOp = op match {
      case ArithOp.Add      => "+"
      case ArithOp.Subtract => "-"
      case ArithOp.Multiply => "*"
    }
    s"(${exprToSql(left)} $sqlOp ${exprToSql(right)})"
  case Expr.StringConcat(l, r)         => s"CONCAT(${exprToSql(l)}, ${exprToSql(r)})"
  case Expr.StringRegexMatch(regex, s) => s"(${exprToSql(s)} LIKE ${exprToSql(regex)})"
  case Expr.StringLength(s)            => s"LENGTH(${exprToSql(s)})"
  case Expr.In(e, values, schema) =>
    s"${exprToSql(e)} IN (${values.map(v => sqlLiteral(v, schema)).mkString(", ")})"
  case Expr.Between(e, low, high, schema) =>
    s"(${exprToSql(e)} BETWEEN ${sqlLiteral(low, schema)} AND ${sqlLiteral(high, schema)})"
  case Expr.IsNull(e)        => s"${exprToSql(e)} IS NULL"
  case Expr.Like(e, pattern) => s"${exprToSql(e)} LIKE '${pattern.replace("'", "''")}'"
}

// --- Statement builders ---

sealed trait SortOrder
object SortOrder {
  case object Asc  extends SortOrder
  case object Desc extends SortOrder
}

case class Assignment(column: String, value: String)

case class SelectStmt[S](
  table: Table[S],
  columnList: List[String] = List("*"),
  whereExpr: Option[Expr[S, Boolean]] = None,
  orderByList: List[(String, SortOrder)] = Nil,
  limitCount: Option[Int] = None
) {
  def columns(optics: Optic[S, _]*): SelectStmt[S] =
    copy(columnList = optics.map(columnName).toList)
  def where(cond: Expr[S, Boolean]): SelectStmt[S] =
    copy(whereExpr = Some(cond))
  def where(cond: SchemaExpr[S, Boolean]): SelectStmt[S] =
    copy(whereExpr = Some(Expr.fromSchemaExpr(cond)))
  def orderBy(optic: Optic[S, _], order: SortOrder = SortOrder.Asc): SelectStmt[S] =
    copy(orderByList = orderByList :+ (columnName(optic), order))
  def limit(n: Int): SelectStmt[S] =
    copy(limitCount = Some(n))
}

case class UpdateStmt[S](
  table: Table[S],
  assignments: List[Assignment] = Nil,
  whereExpr: Option[Expr[S, Boolean]] = None
) {
  def set[A](optic: Optic[S, A], value: A)(implicit schema: Schema[A]): UpdateStmt[S] =
    copy(assignments = assignments :+ Assignment(columnName(optic), sqlLiteral(value, schema)))
  def where(cond: Expr[S, Boolean]): UpdateStmt[S] =
    copy(whereExpr = Some(cond))
  def where(cond: SchemaExpr[S, Boolean]): UpdateStmt[S] =
    copy(whereExpr = Some(Expr.fromSchemaExpr(cond)))
}

case class InsertStmt[S](
  table: Table[S],
  assignments: List[Assignment] = Nil
) {
  def set[A](optic: Optic[S, A], value: A)(implicit schema: Schema[A]): InsertStmt[S] =
    copy(assignments = assignments :+ Assignment(columnName(optic), sqlLiteral(value, schema)))
}

case class DeleteStmt[S](
  table: Table[S],
  whereExpr: Option[Expr[S, Boolean]] = None
) {
  def where(cond: Expr[S, Boolean]): DeleteStmt[S] =
    copy(whereExpr = Some(cond))
  def where(cond: SchemaExpr[S, Boolean]): DeleteStmt[S] =
    copy(whereExpr = Some(Expr.fromSchemaExpr(cond)))
}

def select[S](table: Table[S]): SelectStmt[S]       = SelectStmt(table)
def update[S](table: Table[S]): UpdateStmt[S]       = UpdateStmt(table)
def insertInto[S](table: Table[S]): InsertStmt[S]   = InsertStmt(table)
def deleteFrom[S](table: Table[S]): DeleteStmt[S]   = DeleteStmt(table)

// --- Renderers ---

def renderSelect[S](stmt: SelectStmt[S]): String = {
  val cols = stmt.columnList.mkString(", ")
  val where = stmt.whereExpr.map(c => s" WHERE ${exprToSql(c)}").getOrElse("")
  val orderBy = if (stmt.orderByList.isEmpty) "" else {
    val orders = stmt.orderByList.map { case (col, order) =>
      val dir = order match { case SortOrder.Asc => "ASC"; case SortOrder.Desc => "DESC" }
      s"$col $dir"
    }.mkString(", ")
    s" ORDER BY $orders"
  }
  val limit = stmt.limitCount.map(n => s" LIMIT $n").getOrElse("")
  s"SELECT $cols FROM ${stmt.table.name}$where$orderBy$limit"
}

def renderUpdate[S](stmt: UpdateStmt[S]): String = {
  val sets = stmt.assignments.map(a => s"${a.column} = ${a.value}").mkString(", ")
  val where = stmt.whereExpr.map(c => s" WHERE ${exprToSql(c)}").getOrElse("")
  s"UPDATE ${stmt.table.name} SET $sets$where"
}

def renderInsert[S](stmt: InsertStmt[S]): String = {
  val cols = stmt.assignments.map(_.column).mkString(", ")
  val vals = stmt.assignments.map(_.value).mkString(", ")
  s"INSERT INTO ${stmt.table.name} ($cols) VALUES ($vals)"
}

def renderDelete[S](stmt: DeleteStmt[S]): String = {
  val where = stmt.whereExpr.map(c => s" WHERE ${exprToSql(c)}").getOrElse("")
  s"DELETE FROM ${stmt.table.name}$where"
}

// --- Usage ---

// SELECT with mixed conditions
val q = select(Product.table)
  .columns(Product.name, Product.price, Product.category)
  .where(
    Product.category.in("Electronics", "Books") &&
    Product.price.between(10.0, 500.0) &&
    (Product.rating >= 4).toExpr
  )
  .orderBy(Product.price, SortOrder.Desc)
  .limit(20)

println(renderSelect(q))

// UPDATE with seamless condition mixing
val u = update(Product.table)
  .set(Product.price, 9.99)
  .where(
    Product.price.between(10.0, 30.0) &&
      Product.name.like("M%") &&
      (Product.category === "Books") &&
      (Product.rating >= 4) &&
      (Product.inStock === true)
  )

println(renderUpdate(u))

// INSERT
val i = insertInto(Product.table)
  .set(Product.name, "Wireless Mouse")
  .set(Product.price, 29.99)
  .set(Product.category, "Electronics")
  .set(Product.inStock, true)
  .set(Product.rating, 4)

println(renderInsert(i))

// DELETE
val d = deleteFrom(Product.table)
  .where(Product.price.between(0.0, 1.0) && (Product.inStock === false))

println(renderDelete(d))
```

## Going Further

- **[Part 1: Expressions](./query-dsl-reified-optics.md)** — Building query expressions with reified optics
- **[Part 2: SQL Generation](./query-dsl-sql.md)** — Translating built-in expressions to SQL
- **[Part 3: Extending the Expression Language](./query-dsl-extending.md)** — Adding custom operators beyond SchemaExpr
- **[SchemaExpr Reference](../reference/schema-expr.md)** — Full API coverage of expression types
- **[Optics Reference](../reference/optics.md)** — Lens, Prism, Optional, and Traversal

The builder pattern shown here extends naturally to JOIN clauses (using lenses from multiple table types), subqueries (nesting `SelectStmt` in WHERE conditions), and parameterized queries (collecting `?` placeholders and parameter values during rendering). Each of these builds on the same foundation: optics for column names, `Expr` for conditions, and immutable builders for statement structure.
