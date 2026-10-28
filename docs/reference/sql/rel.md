---
id: rel
title: "Rel"
description: "Reference for Rel[From, To], the foreign-key link between two tables that SqlQuery joins use: selector and string constructors, validation and join direction."
keywords:
  - "SQL Joins"
  - "Foreign Key Relation"
  - "Many-to-One"
  - "One-to-Many"
  - "Rel"
---

`Rel[From, To]` is a **foreign-key link between two tables**: it states that a column of one table equals a column of another, `fromTable.fkColumn = toTable.pkColumn`. You describe each link once as a `Rel` and pass it to the join methods of [`SqlQuery`](./sql-query.md), which turn it into an `ON` condition. `From` is the row type of the table that holds the foreign key, and `To` is the row type of the table it points at.

`Rel` lives in the `zio.blocks.sql.query` package of the `zio-blocks-sql` module, which is published for Scala 3 only (see the [SQL module](./index.md) page). Its key properties are:

- The selector constructors check at compile time that each selector names a field of its table's row type.
- Every `Rel` checks when it is built that both column names are plain SQL identifiers and exist in their tables, and throws an `IllegalArgumentException` otherwise.
- A `Rel` has no direction of its own: `Rel.manyToOne` and `Rel.oneToMany` build the same value, and a query can join it from either table.

`Rel` is a case class with four fields:

```scala
final case class Rel[From, To](
  fromTable: Table[From],
  fkColumn: String,
  toTable: Table[To],
  pkColumn: String
)
```

## Usage

We link each invoice to the customer who placed it:

```scala mdoc:reset:silent
import zio.blocks.schema.Schema
import zio.blocks.sql.query._
import zio.blocks.sql._

case class Customer(customerId: Int, name: String, referrerId: Option[Int])
object Customer { implicit val schema: Schema[Customer] = Schema.derived }

case class Invoice(invoiceId: Int, buyerId: Int, totalCents: Long)
object Invoice { implicit val schema: Schema[Invoice] = Schema.derived }

val customers = Table.derived[Customer]
val invoices  = Table.derived[Invoice]

val invoiceBuyer = Rel.manyToOne(invoices, _.buyerId, customers, _.customerId)
```

We read back the column names the `Rel` resolved and use it in a join:

```scala mdoc
println(s"${invoiceBuyer.fromTable.name}.${invoiceBuyer.fkColumn} = ${invoiceBuyer.toTable.name}.${invoiceBuyer.pkColumn}")
println(SqlQuery.from(invoices).join(invoiceBuyer).sql(SqlDialect.SQLite))
```

The selectors name Scala fields, `buyerId` and `customerId`, and the `Rel` holds their snake-case column names, `buyer_id` and `customer_id`.

## Creating Values

A `Rel` is built from field selectors, which most code uses, or from column names, for tables whose columns don't follow the row type's field names. The examples below reuse the tables from [Usage](#usage).

### From Field Selectors

`Rel.manyToOne` and `Rel.oneToMany` take the table holding the foreign key, a selector for that field, the table it points at and a selector for its key field. They are macros: each selector must be a plain field access such as `_.buyerId`, and the macro turns the field name into a column name with `SqlNameMapper.SnakeCase`:

```scala
object Rel {
  transparent inline def manyToOne[From, To](
    inline fromTable: Table[From],
    inline fkSelector: From => Any,
    inline toTable: Table[To],
    inline pkSelector: To => Any
  ): Rel[From, To]

  transparent inline def oneToMany[From, To](
    inline fromTable: Table[From],
    inline fkSelector: From => Any,
    inline toTable: Table[To],
    inline pkSelector: To => Any
  ): Rel[From, To]
}
```

A misspelled field fails to compile as any unknown member does. The macro reads the first name the selector applies to the row: a chained selector such as `_.name.length` isn't rejected but is read as `_.name`, so write the plain field access. A name that isn't a field of the row type, such as a method called on the row, is rejected by the macro, which lists the valid fields:

```scala mdoc:fail
Rel.manyToOne(invoices, _.hashCode, customers, _.customerId)
```

The two constructors differ only in name. Each puts its first table and selector in `Rel#fromTable` and `Rel#fkColumn`, and its second pair in `Rel#toTable` and `Rel#pkColumn`. Pick the one that reads like the link as you think of it: many invoices point at one customer, and one customer has many invoices. Both links below join the same two columns:

```scala mdoc:silent
val customerInvoices = Rel.oneToMany(customers, _.customerId, invoices, _.buyerId)
```

We join each of them to a query that starts from `customers`:

```scala mdoc
println(SqlQuery.from(customers).join(invoiceBuyer).sql(SqlDialect.SQLite))
println(SqlQuery.from(customers).join(customerInvoices).sql(SqlDialect.SQLite))
```

The `ON` condition always writes the column in `Rel#fkColumn` on the left of `=`, so the two conditions compare the same columns in opposite order and return the same rows.

:::warning
The selector macros always convert the field name to snake case, and ignore `@Modifier.rename`, a known bug in 0.0.56. `Table.derived` honours the annotation, so for a field declared as `@Modifier.rename("buyer_id") customerId: Int` the macro asks for `customer_id`, the table has `buyer_id`, and the `Rel` throws `IllegalArgumentException: FK column invoice.customer_id ... not found in table 'invoice'` when it is built. Build a `Rel` on a renamed column from its column names instead, as in the next section.
:::

### From Column Names

`Rel.manyToOne` and `Rel.oneToMany` also have overloads that take the column names as strings, and the case class itself can be built directly; all three build the same value. Use them for renamed columns, for tables built with a name mapper other than snake case, or for tables constructed at run time:

```scala
object Rel {
  def manyToOne[From, To](fromTable: Table[From], fkColumn: String, toTable: Table[To], pkColumn: String): Rel[From, To]
  def oneToMany[From, To](fromTable: Table[From], fkColumn: String, toTable: Table[To], pkColumn: String): Rel[From, To]
}
```

The string forms give the same `Rel` as the selector form:

```scala mdoc
println(Rel.manyToOne(invoices, "buyer_id", customers, "customer_id") == invoiceBuyer)
println(Rel(invoices, "buyer_id", customers, "customer_id") == invoiceBuyer)
```

Nothing checks a string column name at compile time. When the `Rel` is built, each name must be a plain SQL identifier (ASCII letters, digits and underscores, starting with a letter or underscore) that appears in its table's `columns`; anything else throws an `IllegalArgumentException` that lists the table's columns:

```scala mdoc:crash
Rel(invoices, "buyer", customers, "customer_id")
```

## Using a Rel in a Query

A `Rel` is passed to `SqlQuery#join`, `SqlQuery#innerJoin`, `SqlQuery#leftJoin`, `SqlQuery#joinLeft` or `SqlQuery#join(rel, kind)`. The query looks up which of the `Rel`'s two tables it already has, keeps that table's alias and joins the other one, so the same `Rel` serves queries that start from either table. The [aliases and join order](./sql-query.md#aliases-and-join-order) section of the `SqlQuery` page describes the rules, including what happens when neither table is in the query yet.

A `Rel` between two columns of the same table is a self-join. The query gives the second copy of the table its own alias and reads the foreign-key column from `t0`:

```scala mdoc:silent
val referredBy = Rel.manyToOne(customers, _.referrerId, customers, _.customerId)
```

A left join through it lists each customer next to their referrer, if any:

```scala mdoc
println(SqlQuery.from(customers).leftJoin(referredBy).sql(SqlDialect.SQLite))
```

Joined rows decode as tuples, which has pitfalls when tables share column names; [reading joined rows](./sql-query.md#reading-joined-rows) on the `SqlQuery` page covers them. To write a join's SQL to a file at compile time with [`Dump`](./dump.md), build the `Rel` inside the dumped query rather than referring to a `Rel` stored in a `val`, so the macro can see its columns.

## Integration

`Rel` connects the module's tables to its query builder:

- [`Table`](./table.md) supplies the table names and the `columns` that a `Rel` checks its column names against.
- [`SqlNameMapper`](./sql-name-mapper.md) documents the snake-case conversion the selector constructors apply.
- [`SqlQuery`](./sql-query.md) turns each `Rel` into a `JOIN ... ON` clause.
- [How to Write Joins and Inspect the Generated SQL](../../guides/write-joins-and-inspect-generated-sql.md) uses `Rel.manyToOne` and `Rel.oneToMany` in a many-to-one, a one-to-many and a left join.
