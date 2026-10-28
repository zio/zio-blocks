---
id: sql-query
title: "SqlQuery"
description: "Reference for SqlQuery[A], the sql module's SELECT builder: joins through Rel, WHERE, GROUP BY, ORDER BY, LIMIT and OFFSET, rendered to a Frag."
keywords:
  - "SQL Query Builder"
  - "SQL Joins"
  - "Generated SQL"
  - "JoinKind"
  - "SortOrder"
  - "SqlQuery"
---

`SqlQuery[A]` is an **immutable `SELECT` builder** in the `sql` module: it starts from a [`Table[A]`](./table.md), adds joins described by [`Rel`](./rel.md) values, `WHERE` conditions, grouping, sorting and paging, and renders the result to a [`Frag`](./frag.md) that runs like any `sql"..."` fragment. It writes the parts of a join that are tedious and easy to get wrong by hand: the full column list of every table, the table aliases and each `ON` condition. The type parameter `A` is the row type of the table the query starts from; joins don't change it, and you choose the type each row decodes into when you run the rendered `Frag`.

`SqlQuery` lives in the `zio.blocks.sql.query` package of the `zio-blocks-sql` module, which is published for Scala 3 only, for the JVM and Scala.js (see the [SQL module](./index.md) page for the dependency). Its key properties are:

- Every column is selected, from every table, in schema order; there is no projection to a subset of columns.
- Every table and column name is checked as a plain SQL identifier and double-quoted.
- Table aliases are deterministic: `t0` is the `FROM` table, and `t1`, `t2` and so on are the joined tables in the order you add them.
- Values never become SQL text: conditions are `Frag`s, so interpolated values bind as `?` parameters.
- Each builder method returns a new `SqlQuery`; the original is unchanged.

The case class and its companion have this shape:

```scala
final case class SqlQuery[A] private[query] (
  source: Table[A],
  joins: Vector[JoinNode],
  filters: Vector[Frag],
  groupBy: Vector[String],
  having: Option[Frag],
  orderBy: Vector[OrderBy],
  limit: Option[Int],
  offset: Option[Int]
)

object SqlQuery {
  def from[A](table: Table[A]): SqlQuery[A]
}

final case class OrderBy(column: String, direction: SortOrder)

sealed trait JoinKind
object JoinKind {
  case object Inner extends JoinKind
  case object Left  extends JoinKind
}

sealed trait SortOrder
object SortOrder {
  case object Asc  extends SortOrder
  case object Desc extends SortOrder
}
```

`SqlQuery` is not the query DSL of the [Query DSL with Reified Optics](../../guides/query-dsl-reified-optics.md) guides, which build their own SQL generator from `SchemaExpr` optics. For a task-oriented walkthrough of joins, see [How to Write Joins and Inspect the Generated SQL](../../guides/write-joins-and-inspect-generated-sql.md).

## Usage

We join each invoice to the customer who placed it, keep the invoices above 100.00, newest first, print the SQL and run the query on a temporary SQLite database:

```scala mdoc:reset:silent
import zio.blocks.schema.Schema
import zio.blocks.sql.query._
import zio.blocks.sql._

case class Customer(customerId: Int, name: String)
object Customer { implicit val schema: Schema[Customer] = Schema.derived }

case class Invoice(invoiceId: Int, buyerId: Int, year: Int, totalCents: Long)
object Invoice { implicit val schema: Schema[Invoice] = Schema.derived }

val customers = Table.derived[Customer]
val invoices  = Table.derived[Invoice]

val largeInvoices = SqlQuery
  .from(invoices)
  .join(Rel.manyToOne(invoices, _.buyerId, customers, _.customerId))
  .where(sql"""t0."total_cents" > ${10000L}""")
  .orderBy("year", SortOrder.Desc)

val usageDb = java.nio.file.Files.createTempFile("usage", ".db")
val usageTx = JdbcTransactor.fromUrl(s"jdbc:sqlite:$usageDb", SqlDialect.SQLite)

usageTx.connect {
  customers.createTable(SqlDialect.SQLite).update
  invoices.createTable(SqlDialect.SQLite).update
  (Frag.literal("INSERT INTO customer VALUES ") ++ Frag.values(List(Customer(1, "Ada"), Customer(2, "Bahram")))).update
  (Frag.literal("INSERT INTO invoice VALUES ") ++ Frag.values(List(Invoice(101, 1, 2025, 12000L), Invoice(102, 2, 2026, 4500L), Invoice(103, 2, 2026, 25000L)))).update
}
```

The rendered SQL lists the columns of both tables, invoices first, so each row decodes as an `(Invoice, Customer)` tuple:

```scala mdoc
println(largeInvoices.sql(SqlDialect.SQLite))

usageTx.connect {
  largeInvoices.toFrag(SqlDialect.SQLite).query[(Invoice, Customer)].foreach(println)
}
```

The rest of this page covers each part of that query in turn.

## Creating Queries

The examples in the rest of this page share one small shop database. Customers place invoices, each invoice has lines, and a customer may have been referred by another customer. Every key column has a name that no other table uses, which matters when joined rows are decoded (see [Reading Joined Rows](#reading-joined-rows)):

```scala mdoc:reset:silent
import zio.blocks.schema.Schema
import zio.blocks.sql.query._
import zio.blocks.sql._

case class Customer(customerId: Int, name: String, city: String, referrerId: Option[Int])
object Customer { implicit val schema: Schema[Customer] = Schema.derived }

case class Invoice(invoiceId: Int, buyerId: Int, year: Int, totalCents: Long)
object Invoice { implicit val schema: Schema[Invoice] = Schema.derived }

case class InvoiceLine(lineId: Int, invoiceRef: Int, product: String, quantity: Int)
object InvoiceLine { implicit val schema: Schema[InvoiceLine] = Schema.derived }

val customers = Table.derived[Customer]
val invoices  = Table.derived[Invoice]
val lines     = Table.derived[InvoiceLine]

val invoiceBuyer = Rel.manyToOne(invoices, _.buyerId, customers, _.customerId)
val lineInvoice  = Rel.manyToOne(lines, _.invoiceRef, invoices, _.invoiceId)

val dbFile = java.nio.file.Files.createTempFile("shop", ".db")
val tx     = JdbcTransactor.fromUrl(s"jdbc:sqlite:$dbFile", SqlDialect.SQLite)

tx.connect {
  customers.createTable(SqlDialect.SQLite).update
  invoices.createTable(SqlDialect.SQLite).update
  lines.createTable(SqlDialect.SQLite).update

  val shopCustomers = List(
    Customer(1, "Ada", "Tehran", None),
    Customer(2, "Bahram", "Shiraz", Some(1)),
    Customer(3, "Chloe", "Berlin", Some(1)),
    Customer(4, "Dara", "Tehran", Some(2))
  )
  val shopInvoices = List(
    Invoice(101, 1, 2025, 12000L),
    Invoice(102, 1, 2026, 4500L),
    Invoice(103, 2, 2026, 25000L),
    Invoice(104, 3, 2024, 8000L)
  )
  val shopLines = List(
    InvoiceLine(1, 101, "Keyboard", 2),
    InvoiceLine(2, 101, "Mouse", 1),
    InvoiceLine(3, 103, "Monitor", 1),
    InvoiceLine(4, 104, "Cable", 4)
  )
  (Frag.literal("INSERT INTO customer VALUES ") ++ Frag.values(shopCustomers)).update
  (Frag.literal("INSERT INTO invoice VALUES ") ++ Frag.values(shopInvoices)).update
  (Frag.literal("INSERT INTO invoice_line VALUES ") ++ Frag.values(shopLines)).update
}
```

The database is a temporary file rather than `jdbc:sqlite::memory:`, because each `Transactor#connect` opens a new connection and an in-memory SQLite database starts empty on every connection.

`SqlQuery.from` is the only way to create a query; the case class constructor is private. It takes the `Table` to select from:

```scala
object SqlQuery {
  def from[A](table: Table[A]): SqlQuery[A]
}
```

A query with nothing added selects every column of that table, aliased as `t0`:

```scala mdoc
println(SqlQuery.from(customers).sql(SqlDialect.SQLite))
```

## Core Operations

The builder methods fall into four groups: joining tables, filtering rows, grouping and sorting, and rendering the result. Every method returns a new `SqlQuery[A]`, so a query can be stored in a `val` and extended in several directions.

### Joining Tables

A join adds a table to the query through a `Rel`, the description of a foreign-key link between two tables. This section covers the join methods, how aliases and `ON` conditions are chosen, self-joins, and how to decode the joined rows.

#### Inner and Left Joins

`SqlQuery#innerJoin` adds an `INNER JOIN`, and `SqlQuery#leftJoin` adds a `LEFT JOIN`. `SqlQuery#join(rel)` is the same method as `innerJoin` under another name, and `SqlQuery#joinLeft` is the same as `leftJoin`. `SqlQuery#join(rel, kind)` takes the kind as a [`JoinKind`](#joinkind-and-sortorder) value, which suits code that chooses it at run time:

```scala
final case class SqlQuery[A] {
  def innerJoin[From, To](rel: Rel[From, To]): SqlQuery[A]
  def join[From, To](rel: Rel[From, To]): SqlQuery[A]
  def leftJoin[From, To](rel: Rel[From, To]): SqlQuery[A]
  def joinLeft[From, To](rel: Rel[From, To]): SqlQuery[A]
  def join[From, To](rel: Rel[From, To], kind: JoinKind): SqlQuery[A]
}
```

The five methods build equal queries for the same kind:

```scala mdoc
println(SqlQuery.from(invoices).innerJoin(invoiceBuyer) == SqlQuery.from(invoices).join(invoiceBuyer))
println(SqlQuery.from(customers).leftJoin(invoiceBuyer) == SqlQuery.from(customers).join(invoiceBuyer, JoinKind.Left))
```

A left join keeps the customers who have no invoices. Dara has none, so her row carries SQL `NULL` in every `invoice` column:

```scala mdoc
println(SqlQuery.from(customers).leftJoin(invoiceBuyer).sql(SqlDialect.SQLite))
```

#### Aliases and Join Order

Each join gets the next alias, `t1`, `t2` and so on, and its `ON` condition compares the `Rel`'s foreign-key column with its key column. A `Rel` links two tables, and the builder looks up which of them is already in the query: that side keeps its alias, and the other side is the table the join adds. So the same `Rel` works from either end. Starting from `customers` adds `invoice` as `t1`, and the next `Rel` attaches `invoice_line` to `t1`:

```scala mdoc:silent
val customerLines = SqlQuery.from(customers).join(invoiceBuyer).join(lineInvoice)
```

The rendered SQL has one `ON` condition per join, each comparing a column of the new alias with a column of a table already joined:

```scala mdoc
println(customerLines.sql(SqlDialect.SQLite))
```

When both tables of a `Rel` are already in the query, the `Rel`'s second table (the one holding the key column) is joined again under a new alias.

:::caution
The builder doesn't check that a `Rel` touches a table already in the query. When neither of its tables is there, it reads the `Rel`'s foreign-key column from `t0` and joins the `Rel`'s second table. The SQL is then wrong, and only the database reports it. Here, `lineInvoice` links `invoice_line` and `invoice`, but the query only has `customer`:

```scala mdoc
println(SqlQuery.from(customers).join(lineInvoice).sql(SqlDialect.SQLite))
```

`customer` has no `invoice_ref` column, so this query fails when it runs. Add the joins in an order where each `Rel` reaches a table that is already joined.
:::

#### Self-Joins

A `Rel` whose two tables have the same name is a self-join. The joined copy of the table gets its own alias, and the `ON` condition always takes the foreign-key column from `t0` and the key column from the new alias. Here, each customer's `referrer_id` points at the customer who referred them:

```scala mdoc:silent
val referredBy = Rel.manyToOne(customers, _.referrerId, customers, _.customerId)
```

A left join keeps the customers nobody referred, and the SQL selects the `customer` columns twice, once per alias:

```scala mdoc
println(SqlQuery.from(customers).leftJoin(referredBy).sql(SqlDialect.SQLite))
```

:::warning
A self-join can't be decoded into `(Customer, Customer)` in 0.0.56: both halves of the row have the same column names, and a known bug reads every repeated column name from the first table, so the second `Customer` is a copy of the first. Render the SQL with `SqlQuery#sql` to check it, and run a self-join as a hand-written `sql"..."` query that gives the second table's columns distinct names with `AS`.
:::

#### Reading Joined Rows

A rendered join returns the columns of all its tables in one row: the `FROM` table first, then one table per join, in the order they were added. Decode the row as a tuple with one element per table, in the same order. The three-table query above decodes as `(Customer, Invoice, InvoiceLine)`:

```scala mdoc
tx.connect {
  customerLines.toFrag(SqlDialect.SQLite).query[(Customer, Invoice, InvoiceLine)].foreach { case (c, i, l) =>
    println(s"${c.name}: invoice ${i.invoiceId}, ${l.quantity} x ${l.product}")
  }
}
```

In a left join, the missing side's columns are SQL `NULL`. Decoding them as a plain case class doesn't fail: SQL `NULL` decodes to `0` for an `Int` and to `null` for a `String`. To tell a missing row apart, decode that side into a row type with the same fields as `Option`s:

```scala mdoc
case class InvoiceColumns(invoiceId: Option[Int], buyerId: Option[Int], year: Option[Int], totalCents: Option[Long])
object InvoiceColumns { implicit val schema: Schema[InvoiceColumns] = Schema.derived }

tx.connect {
  SqlQuery.from(customers).leftJoin(invoiceBuyer).toFrag(SqlDialect.SQLite).query[(Customer, InvoiceColumns)].foreach {
    case (c, i) => println(s"${c.name}: ${i.invoiceId.fold("no invoices")(id => s"invoice $id")}")
  }
}
```

:::warning
When two joined tables have a column with the same name, such as an `id` key in every table, a known bug in 0.0.56 reads every column with that name from the first table that has it. The SQL is correct, but the decoded values of the later tables are wrong, and in a left join the missing side's key comes back as the other table's key instead of `None`. Give every column a name that is unique across the tables you join, as `customer_id`, `buyer_id` and `invoice_id` are here.
:::

The [How to Write Joins and Inspect the Generated SQL](../../guides/write-joins-and-inspect-generated-sql.md) guide walks through many-to-one, one-to-many and left joins step by step.

### Filtering Rows

`SqlQuery#where` adds a `WHERE` condition, written as a `Frag`, usually with the `sql"..."` interpolator. `SqlQuery#filter` is the same method. Each call adds a condition, and the conditions are combined with `AND` in the order they were added:

```scala
final case class SqlQuery[A] {
  def where(frag: Frag): SqlQuery[A]
  def filter(frag: Frag): SqlQuery[A]
}
```

The condition is SQL text, so it names columns through the query's aliases, double-quoted as the generated SQL quotes them. Interpolated values become bound parameters. Here are the 2026 invoices of customers from Shiraz:

```scala mdoc:silent
val city = "Shiraz"

val shirazIn2026 = SqlQuery
  .from(invoices)
  .join(invoiceBuyer)
  .where(sql"""t1."city" = $city""")
  .filter(sql"""t0."year" = ${2026}""")
```

The two conditions are joined with `AND`, each value becomes a `?`, and only Bahram's invoice matches both:

```scala mdoc
println(shirazIn2026.sql(SqlDialect.SQLite))

tx.connect {
  shirazIn2026.toFrag(SqlDialect.SQLite).query[(Invoice, Customer)].foreach { case (i, c) =>
    println(s"${c.name}: invoice ${i.invoiceId}, ${i.totalCents / 100.0}")
  }
}
```

:::caution
Aliases and column names inside a `where` fragment are not checked. A typo such as `t1."citty"` only fails when the database runs the query. Check the query with `SqlQuery#explain` while you write it.
:::

### Grouping and Sorting

These methods add the `GROUP BY`, `HAVING`, `ORDER BY`, `LIMIT` and `OFFSET` clauses. Every column name they take refers to the `FROM` table: the renderer writes it as `t0."<column>"`.

#### Grouping Rows

`SqlQuery#groupBy` sets the `GROUP BY` columns, and `SqlQuery#having` sets the `HAVING` condition as a `Frag`. Both replace the value of an earlier call instead of adding to it. `groupBy` checks each name as a plain SQL identifier and throws an `IllegalArgumentException` for anything else, including a dotted name such as `t1.city`:

```scala
final case class SqlQuery[A] {
  def groupBy(cols: String*): SqlQuery[A]
  def having(frag: Frag): SqlQuery[A]
}
```

The `SELECT` list is always every column, so a grouped query can't select an aggregate such as `COUNT(*)`; `HAVING` can still use one. That makes `groupBy` useful for keeping the rows whose group passes a test. Here are the customers with at least two invoices, decoded as `Customer` alone, because the first columns of each row are the customer's:

```scala mdoc:silent
val repeatBuyers = SqlQuery
  .from(customers)
  .join(invoiceBuyer)
  .groupBy("customer_id")
  .having(sql"COUNT(*) >= ${2}")
```

Ada is the only customer with two invoices:

```scala mdoc
println(repeatBuyers.sql(SqlDialect.SQLite))

tx.connect {
  repeatBuyers.toFrag(SqlDialect.SQLite).query[Customer].foreach(println)
}
```

:::caution
SQLite accepts a `GROUP BY` that leaves selected columns out of the grouping, and returns the values of an arbitrary row of each group for them; here the invoice columns come from one of Ada's invoices. PostgreSQL accepts such a query only when every selected column depends on the grouped columns, for example a single-table query grouped by its primary key. Otherwise it fails with `column "..." must appear in the GROUP BY clause or be used in an aggregate function`, so the query above fails on PostgreSQL. For aggregates in the result, write the query with the `sql"..."` interpolator.
:::

#### Sorting Rows

`SqlQuery#orderBy` adds one `ORDER BY` column, ascending unless you pass `SortOrder.Desc`; each call adds a column after the earlier ones. `SqlQuery#orderByMany` adds several `OrderBy` values at once:

```scala
final case class SqlQuery[A] {
  def orderBy(col: String, dir: SortOrder = SortOrder.Asc): SqlQuery[A]
  def orderByMany(cols: OrderBy*): SqlQuery[A]
}
```

`orderBy` checks the column name when you call it, and throws an `IllegalArgumentException` for a name that isn't a plain SQL identifier. `orderByMany` checks its names only when the query is rendered, so a bad name surfaces in `SqlQuery#toFrag`, `SqlQuery#sql` or `SqlQuery#explain`. The two forms below sort the same way, by city and then by name in reverse:

```scala mdoc:silent
val byCityThenName = SqlQuery.from(customers).orderBy("city").orderBy("name", SortOrder.Desc)
val sameOrder      = SqlQuery.from(customers).orderByMany(OrderBy("city", SortOrder.Asc), OrderBy("name", SortOrder.Desc))
```

Both render one `ORDER BY` with the columns in the order they were added, and the two queries are equal:

```scala mdoc
println(byCityThenName.sql(SqlDialect.SQLite))
println(byCityThenName == sameOrder)
```

:::caution
`orderBy` and `groupBy` can only name columns of the `FROM` table, a known bug in 0.0.56. A name like `t1.name` throws `IllegalArgumentException: Invalid SQL column identifier 't1.name'`, and a column that exists only in a joined table renders as `t0."<column>"` and fails when the database runs the query. To sort by a joined table's column, start the query from that table, or sort the decoded rows in Scala.
:::

#### Paging Rows

`SqlQuery#limit` caps the number of rows, and `SqlQuery#offset` skips rows before the first one returned. Each replaces the value of an earlier call. Both require `n >= 0` and throw an `IllegalArgumentException` ("requirement failed: limit must be >= 0") otherwise. The numbers are written into the SQL text, not bound as parameters:

```scala
final case class SqlQuery[A] {
  def limit(n: Int): SqlQuery[A]
  def offset(n: Int): SqlQuery[A]
}
```

Paging needs a stable order, so we sort by the key and read the second page of two customers:

```scala mdoc:silent
val secondPage = SqlQuery.from(customers).orderBy("customer_id").limit(2).offset(2)
```

`LIMIT` and `OFFSET` come last in the SQL, as literal numbers, and the page holds the third and fourth customers:

```scala mdoc
println(secondPage.sql(SqlDialect.SQLite))

tx.connect {
  secondPage.toFrag(SqlDialect.SQLite).query[Customer].foreach(println)
}
```

:::caution
`SqlQuery#offset` without `SqlQuery#limit` renders `OFFSET n` with no `LIMIT`, a known bug in 0.0.56. PostgreSQL accepts that, but SQLite only allows `OFFSET` after `LIMIT` and fails with `near "OFFSET": syntax error`. On SQLite, always pair `SqlQuery#offset` with `SqlQuery#limit`.
:::

### Rendering and Running

A query does nothing until it is rendered. `SqlQuery#toFrag` renders it to a `Frag` for a dialect, `SqlQuery#sql` returns the SQL string that `Frag` sends to the JDBC driver, and `SqlQuery#explain` returns a version for logs and code review:

```scala
final case class SqlQuery[A] {
  def toFrag(dialect: SqlDialect): Frag
  def sql(dialect: SqlDialect): String
  def explain(dialect: SqlDialect): String
}
```

#### Running the Rendered Fragment

`SqlQuery` has no methods that run it: the `Frag` from `SqlQuery#toFrag` runs inside a `Transactor` block with `Frag#query`, `Frag#queryOne`, `Frag#queryLimit`, `Frag#queryStream` or `Frag#queryChunked`, as described on the [Frag](./frag.md) page. `Frag#queryOne` returns the first row, if any:

```scala mdoc
tx.connect {
  println(SqlQuery.from(invoices).orderBy("total_cents", SortOrder.Desc).toFrag(SqlDialect.SQLite).queryOne[Invoice])
}
```

#### Printing the SQL

`SqlQuery#explain` returns the SQL on one line with numbered `?1`, `?2` placeholders, followed by a `-- params:` line that lists each parameter's position and type, never its value. `-- params: (none)` means the query binds nothing:

```scala mdoc
println(shirazIn2026.explain(SqlDialect.PostgreSQL))
println(secondPage.explain(SqlDialect.PostgreSQL))
```

PostgreSQL and SQLite render the same SQL: both use `?` placeholders and double-quoted identifiers, and the `dialect` parameter is kept for a future dialect with another placeholder syntax. To write the SQL of each query to a `.sql` file while the code compiles, see [Dump](./dump.md).

#### Inspecting a Query

The case class fields are public, so you can read back what a query holds. Some field names are also builder methods: `SqlQuery#limit` without arguments is the `Option[Int]` field, and `SqlQuery#limit(10)` is the builder:

```scala mdoc
println(secondPage.limit)
println(secondPage.offset)
println(secondPage.orderBy)
println(shirazIn2026.filters.size)
println(shirazIn2026.source.name)
```

`joins` holds the query's join nodes, whose type is private to the module.

#### Rendering with QueryRenderer

You rarely call this directly; it is the renderer behind `SqlQuery#toFrag`, which you should prefer. `QueryRenderer.render` turns a query into a `Frag` by composing `Frag.literal` pieces and the query's own condition fragments:

```scala
object QueryRenderer {
  def render[A](query: SqlQuery[A], dialect: SqlDialect): Frag
}
```

It renders the clauses in a fixed order, whatever order the builder methods were called in: `SELECT`, `FROM`, the joins, `WHERE`, `GROUP BY`, `HAVING`, `ORDER BY`, `LIMIT` and `OFFSET`. `SqlQuery#toFrag(dialect)` is `QueryRenderer.render(this, dialect)`:

```scala mdoc
println(QueryRenderer.render(secondPage, SqlDialect.SQLite) == secondPage.toFrag(SqlDialect.SQLite))
```

## JoinKind and SortOrder

`JoinKind` and `SortOrder` are sealed traits with two case objects each, and `OrderBy` pairs a column with a `SortOrder`:

| Type        | Value or field          | Meaning                                                    |
|-------------|-------------------------|------------------------------------------------------------|
| `JoinKind`  | `JoinKind.Inner`        | `INNER JOIN`: keep only rows with a match in both tables   |
| `JoinKind`  | `JoinKind.Left`         | `LEFT JOIN`: keep every row of the existing side           |
| `SortOrder` | `SortOrder.Asc`         | `ASC`, the default of `SqlQuery#orderBy`                   |
| `SortOrder` | `SortOrder.Desc`        | `DESC`                                                     |
| `OrderBy`   | `column: String`        | A column of the `FROM` table, checked when rendered        |
| `OrderBy`   | `direction: SortOrder`  | The sort direction for that column                         |

A join kind chosen at run time goes through `SqlQuery#join(rel, kind)`:

```scala mdoc
def invoicesOfCustomers(includeWithoutInvoices: Boolean): SqlQuery[Customer] = {
  val kind = if (includeWithoutInvoices) JoinKind.Left else JoinKind.Inner
  SqlQuery.from(customers).join(invoiceBuyer, kind)
}

println(invoicesOfCustomers(includeWithoutInvoices = true).sql(SqlDialect.SQLite))
```

## Integration

`SqlQuery` sits between the schema-derived tables and the fragments that run:

- [`Table`](./table.md) supplies each table's name and column list; `Table.derived` names them in snake case.
- [`Rel`](./rel.md) describes each join, with its column names checked at compile time when built from field selectors.
- [`Frag`](./frag.md) carries the conditions in and the rendered query out; its `query` methods and a [`DbCodec`](./db-codec.md) for the row type run it and decode the rows.
- [`Dump`](./dump.md) writes a query's SQL to a file at compile time.
- [`SqlLogger`](./sql-logger.md) records the SQL of every query a transactor runs, including rendered `SqlQuery`s.
