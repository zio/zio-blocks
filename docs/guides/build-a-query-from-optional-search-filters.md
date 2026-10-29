---
id: build-a-query-from-optional-search-filters
title: "How to Build a Query from Optional Search Filters"
description: "Turn a search form with optional fields into one parameterized SQL query with a WHERE clause, ordering and paging, using Frag."
keywords:
  - "Dynamic SQL"
  - "Search Filters"
  - "Optional WHERE Clause"
  - "SQL Paging"
  - "Frag"
  - "SqlQuery"
---

In this guide, we build the query behind a support desk's ticket search screen. The screen has optional fields: a text box, a customer, a set of status checkboxes and a date range. The user fills in some of them and leaves the rest empty. By the end, you will have one function that turns whatever the user filled in into one SQL query whose `WHERE` clause holds only those conditions, joined with `AND`, followed by a sort order and a page of results, with every value the user typed bound as a parameter.

We build it one field at a time with [`Frag`](../reference/sql/frag.md), the `sql` module's piece of SQL text with its parameters kept separate:

- One condition per filled-in field, and nothing for an empty one
- A small helper that joins the conditions with `AND`
- A `LIKE` search on part of a text, a date range and a multi-select with `IN`
- A sort order chosen from a fixed list, and paging with `LIMIT` and `OFFSET`
- The finished search, with the SQL and parameters it sends
- The same search written with the `SqlQuery` builder, and when to choose each

This guide builds on [Build Your First Database Repository on SQLite](./your-first-sqlite-repository.md). We use `JdbcTransactor`, `Table`, `Repo` and the `sql"..."` interpolator the way that tutorial introduces them and don't explain them again.

## The Problem

A search form with five optional fields can be filled in 32 different ways, and each way needs a different `WHERE` clause. Writing 32 queries is out of the question, so the query has to be assembled at run time from the fields that have a value.

Assembling SQL at run time is where bugs and security holes come from. With plain JDBC, the usual approach keeps a `StringBuilder` for the SQL and a list for the parameters, and appends to both in step:

```scala mdoc:compile-only
import java.sql.{Connection, Date}
import java.time.LocalDate
import scala.collection.mutable.ListBuffer

def searchTickets(
  conn: Connection,
  text: Option[String],
  customer: Option[String],
  statuses: Set[String],
  openedFrom: Option[LocalDate]
): List[String] = {
  // "1 = 1" lets every condition start with " AND "
  val sql    = new StringBuilder("SELECT subject FROM ticket WHERE 1 = 1")
  val params = ListBuffer.empty[Any]
  text.foreach { t => sql.append(" AND subject LIKE ?"); params += s"%$t%" }
  customer.foreach { c => sql.append(" AND customer = ?"); params += c }
  if (statuses.nonEmpty) {
    sql.append(statuses.map(_ => "?").mkString(" AND status IN (", ", ", ")"))
    params ++= statuses
  }
  openedFrom.foreach { d => sql.append(" AND opened_on >= ?"); params += Date.valueOf(d) }
  sql.append(" ORDER BY opened_on DESC")

  val statement = conn.prepareStatement(sql.toString)
  params.zipWithIndex.foreach { case (p, i) => statement.setObject(i + 1, p) }
  val rows     = statement.executeQuery()
  val subjects = ListBuffer.empty[String]
  while (rows.next()) subjects += rows.getString("subject")
  subjects.toList
}
```

Every `?` in the text must line up with exactly one entry in the `params` list, and nothing checks that they do: add a condition to one and forget the other, and the query binds the wrong values or fails. The `%` and `_` characters a user types are wildcards inside `LIKE`, so a search for `50%` matches more than the user asked for. The shortcut that skips the parameter list, `sql.append(s" AND customer = '$c'")`, lets a user who types a quote rewrite the query: that is SQL injection.

In this guide, each condition is a `Frag` that carries its own SQL text and its own parameters, so the two can't drift apart, and the user's values never become part of the SQL text.

## Prerequisites

Add the `sql` module and the SQLite JDBC driver to your `build.sbt`:

```scala
// Scala 3.3 to 3.8
libraryDependencies += "dev.zio" %% "zio-blocks-sql" % "@VERSION@"

// Scala 3.9 and newer
libraryDependencies += "dev.zio" %% "zio-blocks-next-sql" % "@VERSION@"
scalacOptions += "-experimental"

// The SQLite JDBC driver, for every Scala version
libraryDependencies += "org.xerial" % "sqlite-jdbc" % "3.53.4.0"
```

The `sql` module is Scala 3 only, so the code on this page uses Scala 3 syntax.

## The Ticket Table and the Search Form

Our support desk stores one row per ticket: a subject line, the customer who opened it, a status (`open`, `pending` or `closed`), a priority from 1 (urgent) to 3 (low) and the date it was opened. We create the table in a SQLite file and add eight tickets:

```scala mdoc:silent
import java.time.LocalDate
import zio.blocks.schema.Schema
import zio.blocks.sql.query.{SortOrder, SqlQuery}
import zio.blocks.sql.*

case class Ticket(id: Long, subject: String, customer: String, status: String, priority: Int, openedOn: LocalDate)

object Ticket {
  given Schema[Ticket] = Schema.derived
}

val ticketTable = Table.derived[Ticket]
val tickets     = Repo.derived[Ticket, Long]

val dbFile     = java.nio.file.Files.createTempFile("helpdesk", ".db")
val transactor = JdbcTransactor.fromUrl(s"jdbc:sqlite:$dbFile", SqlDialect.SQLite)

transactor.connect {
  ticketTable.createTable(SqlDialect.SQLite).update
  List(
    Ticket(1L, "Cannot log in after password reset", "Acme", "open", 1, LocalDate.of(2026, 9, 28)),
    Ticket(2L, "Invoice shows the 50% discount twice", "Globex", "closed", 2, LocalDate.of(2026, 10, 2)),
    Ticket(3L, "Login page loads slowly", "Acme", "pending", 2, LocalDate.of(2026, 10, 6)),
    Ticket(4L, "Export to CSV drops the last row", "Initech", "open", 3, LocalDate.of(2026, 10, 9)),
    Ticket(5L, "Two-factor code never arrives", "Globex", "open", 1, LocalDate.of(2026, 10, 14)),
    Ticket(6L, "Typo on the pricing page", "Acme", "closed", 3, LocalDate.of(2026, 10, 17)),
    Ticket(7L, "API returns 500 on large uploads", "Initech", "pending", 1, LocalDate.of(2026, 10, 21)),
    Ticket(8L, "Cannot change the billing email", "Acme", "open", 2, LocalDate.of(2026, 10, 24))
  ).foreach(tickets.insert)
}
```

We import `SqlQuery` and `SortOrder` from `zio.blocks.sql.query` now, for the last section, and keep `import zio.blocks.sql.*` after every other `zio` import, as the tutorial explains.

The search form arrives as a case class. Every filter is optional: an `Option` is `None` when the user left the field empty, and an empty `Set` means no status box is ticked. The sort order is a choice from a fixed list, and the page number and page size have defaults:

```scala mdoc:silent
enum SortBy {
  case Newest, Oldest, MostUrgent
}

case class TicketSearch(
  text: Option[String] = None,
  customer: Option[String] = None,
  statuses: Set[String] = Set.empty,
  openedFrom: Option[LocalDate] = None,
  openedTo: Option[LocalDate] = None,
  sortBy: SortBy = SortBy.Newest,
  page: Int = 1,
  pageSize: Int = 20
)
```

`TicketSearch()` is the empty form, which should list every ticket.

<details>
  <summary>Runnable version, which prints the empty form and the number of tickets: <code>Step3TicketTableExample.scala</code></summary>

```scala mdoc:embed:sql-examples/src/main/scala/buildaqueryfromoptionalsearchfilters/Step3TicketTableExample.scala:show-line-numbers
```

</details>

<details>
  <summary>The model and seed data, shared by every example file: <code>Helpdesk.scala</code></summary>

```scala mdoc:embed:sql-examples/src/main/scala/buildaqueryfromoptionalsearchfilters/Helpdesk.scala:show-line-numbers
```

</details>

## Turn a Filled-In Field into a Condition

Each field becomes an `Option[Frag]`: `Some` condition when the user filled it in, `None` when they didn't. `Option#map` does exactly that, so the customer field takes one line. A browser sends an empty text box as an empty string, not as a missing field, so we first treat blank text as empty:

```scala mdoc:silent
def filled(field: Option[String]): Option[String] =
  field.map(_.trim).filter(_.nonEmpty)

def customerIs(search: TicketSearch): Option[Frag] =
  filled(search.customer).map(customer => sql"customer = $customer")
```

To see what a condition holds, we print its SQL text and its parameters. `Frag#sql` renders the text for a dialect, and `Frag#params` holds the values that are bound to the `?` placeholders:

```scala mdoc
def describe(frag: Frag): Unit = {
  println(frag.sql(SqlDialect.SQLite))
  println(frag.params)
}

customerIs(TicketSearch(customer = Some("Acme"))).foreach(describe)
customerIs(TicketSearch(customer = Some("  ")))
customerIs(TicketSearch())
```

The customer name is a `?` in the text and a `DbString` in the parameters. A blank or missing customer gives `None`, so the query gets no condition for that field.

:::warning[Skip the condition, don't bind `None`]
The interpolator accepts an `Option` value and binds `None` as SQL `NULL`. So `sql"customer = ${search.customer}"` compiles, but for an empty field it sends `customer = NULL`, which is never true in SQL, and the search returns no rows at all.

A query with a fixed text can still handle an empty field: `sql"(${search.customer} IS NULL OR customer = ${search.customer})"` binds the value twice and lets the database skip the comparison when it is `NULL`. That works for an exact match, but not for the `LIKE` pattern or the `IN` list below, so this guide leaves out each empty field's condition instead.
:::

## Join the Conditions with AND

With one `Option[Frag]` per field, we need a `WHERE` clause that holds only the conditions that are present, joined with `AND`, and nothing at all when the form is empty. `Frag` has the parts for it: `Frag#++` joins two fragments, `Frag.literal` makes a fragment from fixed SQL text, and `Frag.empty` adds nothing. In zio-blocks 0.0.56 there is no helper that joins fragments with a separator, such as doobie's `whereAndOpt`, so we write one:

```scala mdoc:silent
def whereAnd(conditions: Option[Frag]*): Frag = {
  val present = conditions.flatten
  if (present.isEmpty) Frag.empty
  else Frag.literal(" WHERE ") ++ present.map(c => sql"($c)").reduce((a, b) => a ++ Frag.literal(" AND ") ++ b)
}
```

The helper is our own code, not part of the library. `conditions.flatten` drops the `None`s. When nothing is left, the helper returns `Frag.empty`, so an empty form gets no `WHERE` at all. Otherwise, it wraps each condition in parentheses and puts `AND` between them. Interpolating a `Frag` into `sql"..."` splices its text and its parameters in place; only other values become `?` parameters. The parentheses matter when a condition contains `OR`: without them, `a AND b OR c` means `(a AND b) OR c` in SQL.

We keep the fixed `SELECT` in a value, since every search starts with it:

```scala mdoc:silent
val selectTickets = Frag.literal("SELECT id, subject, customer, status, priority, opened_on FROM ticket")
```

We put the clause after it, first with no conditions and then with a customer:

```scala mdoc
describe(selectTickets ++ whereAnd())
describe(selectTickets ++ whereAnd(customerIs(TicketSearch(customer = Some("Acme")))))
```

The query with a condition runs like any other `Frag`:

```scala mdoc
transactor.connect {
  (selectTickets ++ whereAnd(customerIs(TicketSearch(customer = Some("Acme"))))).query[Ticket].foreach(println)
}
```

Acme's four tickets come back. Every following filter is one more `Option[Frag]` passed to `whereAnd`.

<details>
  <summary>Runnable version, with the setup from section 3 and the functions from earlier sections: <code>Step5JoinWithAndExample.scala</code></summary>

```scala mdoc:embed:sql-examples/src/main/scala/buildaqueryfromoptionalsearchfilters/Step5JoinWithAndExample.scala:show-line-numbers
```

</details>

## Match Part of the Subject with LIKE

The text box searches the subject line. `LIKE` with `%` on both sides finds the text anywhere in the subject, but `%` and `_` in the user's own text are wildcards too. We escape them with an escape character of our choosing, `!`, and name it in the `ESCAPE` clause:

```scala mdoc:silent
def likePattern(text: String): String =
  "%" + text.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%"

def subjectContains(search: TicketSearch): Option[Frag] =
  filled(search.text).map(text => sql"subject LIKE ${likePattern(text)} ESCAPE '!'")
```

`likePattern` escapes `!` first, so the `!` characters it adds afterwards aren't escaped a second time. The pattern itself is a bound parameter, like any other value.

To compare, we search for `50%`, once without escaping and once with `subjectContains`:

```scala mdoc
transactor.connect {
  println(sql"SELECT subject FROM ticket WHERE subject LIKE ${"%50%%"}".query[String])
  println((Frag.literal("SELECT subject FROM ticket") ++ whereAnd(subjectContains(TicketSearch(text = Some("50%"))))).query[String])
}
```

Without escaping, the `%` the user typed is a wildcard, so the API ticket's "500" matches as well. With the escaped pattern, only the invoice ticket matches.

We use `!` rather than the more common backslash because the `sql"..."` interpolator does not process escape sequences: `ESCAPE '\\'` inside it sends two backslashes, and SQLite rejects that with "ESCAPE expression must be a single character". Also note that SQLite's `LIKE` ignores the case of ASCII letters, while PostgreSQL's `LIKE` is case-sensitive and its `ILIKE` is not.

<details>
  <summary>Runnable version, with the setup from section 3 and the functions from earlier sections: <code>Step6LikeSearchExample.scala</code></summary>

```scala mdoc:embed:sql-examples/src/main/scala/buildaqueryfromoptionalsearchfilters/Step6LikeSearchExample.scala:show-line-numbers
```

</details>

## Filter a Date Range

The date range is two separate fields, so it gives two conditions, either of which may be missing. A `LocalDate` binds like any other value:

```scala mdoc:silent
def openedOnOrAfter(search: TicketSearch): Option[Frag] =
  search.openedFrom.map(from => sql"opened_on >= $from")

def openedOnOrBefore(search: TicketSearch): Option[Frag] =
  search.openedTo.map(to => sql"opened_on <= $to")
```

To see both ends of the range, we ask for the tickets opened in the first half of October, and then for those opened from October 20 on, with no end date:

```scala mdoc
transactor.connect {
  val firstHalf = TicketSearch(openedFrom = Some(LocalDate.of(2026, 10, 1)), openedTo = Some(LocalDate.of(2026, 10, 15)))
  val fromOct20 = TicketSearch(openedFrom = Some(LocalDate.of(2026, 10, 20)))

  println((Frag.literal("SELECT id FROM ticket") ++ whereAnd(openedOnOrAfter(firstHalf), openedOnOrBefore(firstHalf))).query[Long])
  println((Frag.literal("SELECT id FROM ticket") ++ whereAnd(openedOnOrAfter(fromOct20), openedOnOrBefore(fromOct20))).query[Long])
}
```

Both ends of the range are inclusive. On SQLite, the table stores a `LocalDate` as ISO text such as `2026-10-14`, which sorts in date order, so `>=` and `<=` compare dates correctly; PostgreSQL has a native `DATE` type.

<details>
  <summary>Runnable version, with the setup from section 3 and the functions from earlier sections: <code>Step7DateRangeExample.scala</code></summary>

```scala mdoc:embed:sql-examples/src/main/scala/buildaqueryfromoptionalsearchfilters/Step7DateRangeExample.scala:show-line-numbers
```

</details>

## Match Any of Several Statuses with IN

The status checkboxes allow several values at once, which is an `IN (...)` list with one `?` per value. The interpolator binds one value per `$`, and a `List` or `Set` there still compiles: it binds as one parameter that holds the whole collection as JSON text. So the obvious version renders `status IN (?)` and matches no row:

```scala mdoc
describe(sql"status IN (${List("open", "pending")})")

transactor.connect {
  println((Frag.literal("SELECT id FROM ticket") ++ whereAnd(Some(sql"status IN (${List("open", "pending")})"))).query[Long])
}
```

The query runs without an error and returns an empty list: the database compares each status with the text `["open","pending"]`, which no ticket has.

We build the list from one `sql"$v"` fragment per value instead, joined with commas:

```scala mdoc:silent
def commaSeparated(values: Seq[String]): Frag =
  values.map(v => sql"$v").reduce((a, b) => a ++ Frag.literal(", ") ++ b)

def statusIn(search: TicketSearch): Option[Frag] =
  if (search.statuses.isEmpty) None
  else Some(sql"status IN (${commaSeparated(search.statuses.toSeq.sorted)})")
```

No ticked box means no condition, so the search shows every status. Sorting the values keeps the SQL and parameter order the same for the same set of boxes, whatever order the `Set` holds them in.

We tick two boxes:

```scala mdoc:silent
val openOrPending = TicketSearch(statuses = Set("pending", "open"))
```

Then we look at the condition and run it:

```scala mdoc
statusIn(openOrPending).foreach(describe)

transactor.connect {
  println((Frag.literal("SELECT id FROM ticket") ++ whereAnd(statusIn(openOrPending))).query[Long])
}
```

The SQL has two placeholders, and the six open or pending tickets match.

<details>
  <summary>Runnable version, with the setup from section 3 and the functions from earlier sections: <code>Step8StatusInExample.scala</code></summary>

```scala mdoc:embed:sql-examples/src/main/scala/buildaqueryfromoptionalsearchfilters/Step8StatusInExample.scala:show-line-numbers
```

</details>

## Sort by an Allowed Column

A sort order can't be a parameter: `ORDER BY ?` sorts by a constant value, not by the column named in it. So the column names must be part of the SQL text, which is exactly where user input must never go. The form therefore sends a choice from a fixed list, our `SortBy` enum, and each choice maps to a fixed piece of SQL:

```scala mdoc:silent
def orderBy(sortBy: SortBy): Frag = sortBy match {
  case SortBy.Newest     => Frag.literal(" ORDER BY opened_on DESC, id DESC")
  case SortBy.Oldest     => Frag.literal(" ORDER BY opened_on ASC, id ASC")
  case SortBy.MostUrgent => Frag.literal(" ORDER BY priority ASC, opened_on DESC, id DESC")
}
```

Each order ends with `id`, so tickets with the same date or priority always come back in the same order, and a ticket can't move from one page to another between two requests.

A web request carries the choice as a string. We turn it into a `SortBy` with a default for anything we don't recognize, rather than with `SortBy.valueOf`, which throws:

```scala mdoc
def parseSortBy(param: String): SortBy =
  SortBy.values.find(_.toString == param).getOrElse(SortBy.Newest)

parseSortBy("MostUrgent")
parseSortBy("subject; DROP TABLE ticket")
```

The unknown value falls back to `Newest`, and the text of the request never reaches the SQL.

:::danger
Never build the `ORDER BY` from the request's text with `Frag.literal` or `SqlLiteral`. Both splice their text into the SQL as it is, without parameters or checks, so they turn a sort field into an injection point.
:::

<details>
  <summary>Runnable version, which also prints the <code>ORDER BY</code> text of each choice: <code>Step9SortByExample.scala</code></summary>

```scala mdoc:embed:sql-examples/src/main/scala/buildaqueryfromoptionalsearchfilters/Step9SortByExample.scala:show-line-numbers
```

</details>

## Return One Page of Results

A search screen shows one page at a time. `LIMIT` takes the page size and `OFFSET` skips the earlier pages. Both are numbers that come from the request, so we keep them in a sane range and bind them as parameters:

```scala mdoc:silent
def pageSizeOf(search: TicketSearch): Int = search.pageSize.max(1).min(100)

def offsetOf(search: TicketSearch): Int = (search.page.max(1) - 1) * pageSizeOf(search)

def pageOf(search: TicketSearch): Frag =
  sql" LIMIT ${pageSizeOf(search)} OFFSET ${offsetOf(search)}"
```

A page number below 1 becomes 1, and the page size stays between 1 and 100, so a request can't ask for a million rows at once.

The screen also shows how many tickets match, to draw the page links. The count uses the same `WHERE` clause with a different `SELECT` and no sort order or paging. We ask for the second page of three tickets, newest first:

```scala mdoc
transactor.connect {
  val secondPage = TicketSearch(sortBy = SortBy.Newest, page = 2, pageSize = 3)
  val where = whereAnd()
  println((sql"SELECT COUNT(*) FROM ticket" ++ where).queryOne[Long])
  println((Frag.literal("SELECT id FROM ticket") ++ where ++ orderBy(secondPage.sortBy) ++ pageOf(secondPage)).query[Long])
}
```

All eight tickets match, and the second page holds the fourth to the sixth newest.

:::tip
`OFFSET` makes the database read and throw away every row before the page, so deep pages get slower as the table grows, and a row inserted between two requests shifts every later page by one. For long result lists, page with a cursor instead: [How to Upsert Rows and Paginate with Keyset Cursors](./upsert-and-paginate-with-keyset-cursors.md) shows `Frag.keysetAfter`, which writes its own `WHERE`, so a filtered search goes into a subquery that it pages through.
:::

<details>
  <summary>Runnable version, with the setup from section 3 and the functions from earlier sections: <code>Step10PagingExample.scala</code></summary>

```scala mdoc:embed:sql-examples/src/main/scala/buildaqueryfromoptionalsearchfilters/Step10PagingExample.scala:show-line-numbers
```

</details>

## Run the Whole Search

Every field now has a function that gives its condition. We collect them in one list, and the search query is the `SELECT`, the `WHERE` clause, the sort order and the page, joined with `Frag#++`:

```scala mdoc:silent
def conditions(search: TicketSearch): Seq[Option[Frag]] = Seq(
  subjectContains(search),
  customerIs(search),
  statusIn(search),
  openedOnOrAfter(search),
  openedOnOrBefore(search)
)

def searchQuery(search: TicketSearch): Frag =
  selectTickets ++ whereAnd(conditions(search)*) ++ orderBy(search.sortBy) ++ pageOf(search)

def countQuery(search: TicketSearch): Frag =
  sql"SELECT COUNT(*) FROM ticket" ++ whereAnd(conditions(search)*)
```

A new filter is one more function and one more line in `conditions`. We fill in three of the fields, the way a support agent looking for Acme's unresolved login problems would:

```scala mdoc:silent
val loginProblems = TicketSearch(text = Some("log"), customer = Some("Acme"), statuses = Set("open", "pending"), sortBy = SortBy.MostUrgent)
```

Next, we print the query, count the matching tickets and run it:

```scala mdoc
describe(searchQuery(loginProblems))

transactor.connect {
  println(s"matching tickets: ${countQuery(loginProblems).queryOne[Long]}")
  searchQuery(loginProblems).query[Ticket].foreach(println)
}
```

The `WHERE` clause holds the three filled-in conditions and nothing for the two empty date fields. Every value is a `?` in the SQL text and an entry in the parameters, in the same order.

That also holds for hostile input. We type a classic injection attempt into the text box:

```scala mdoc:silent
val hostile = TicketSearch(text = Some("'; DROP TABLE ticket; --"))
```

Then we print the query and run it:

```scala mdoc
describe(searchQuery(hostile))

transactor.connect {
  println(searchQuery(hostile).query[Ticket])
  println(s"tickets in the table: ${tickets.count}")
}
```

The input is a parameter like any other: it is a pattern no subject matches, so the search finds nothing and the table still holds all eight tickets.

<details>
  <summary>Every condition function from sections 4 to 11, in the shared file the examples use: <code>SearchConditions.scala</code></summary>

```scala mdoc:embed:sql-examples/src/main/scala/buildaqueryfromoptionalsearchfilters/SearchConditions.scala:show-line-numbers
```

</details>

<details>
  <summary>Runnable version, with the setup from section 3 and the functions from earlier sections: <code>Step11WholeSearchExample.scala</code></summary>

```scala mdoc:embed:sql-examples/src/main/scala/buildaqueryfromoptionalsearchfilters/Step11WholeSearchExample.scala:show-line-numbers
```

</details>

## The Same Search with the Query Builder

The `sql` module also has a query builder, [`SqlQuery`](../reference/sql/sql-query.md). It writes the `SELECT` list from the table's schema, joins related tables, and takes `WHERE` conditions as `Frag`s. `SqlQuery#where` adds one condition per call and the builder joins them with `AND`, so optional filters fold into it without a helper. We reuse the condition functions from the earlier sections:

```scala mdoc:silent
def searchWithBuilder(search: TicketSearch): SqlQuery[Ticket] = {
  val filtered = conditions(search).flatten.foldLeft(SqlQuery.from(ticketTable)) { (query, condition) =>
    query.where(sql"($condition)")
  }
  val sorted = search.sortBy match {
    case SortBy.Newest     => filtered.orderBy("opened_on", SortOrder.Desc).orderBy("id", SortOrder.Desc)
    case SortBy.Oldest     => filtered.orderBy("opened_on").orderBy("id")
    case SortBy.MostUrgent =>
      filtered.orderBy("priority").orderBy("opened_on", SortOrder.Desc).orderBy("id", SortOrder.Desc)
  }
  sorted.limit(pageSizeOf(search)).offset(offsetOf(search))
}
```

`conditions(search).flatten` keeps only the filled-in conditions, and `Seq#foldLeft` adds them to the query one by one. An empty form adds none, and the builder then writes no `WHERE`. `SqlQuery#orderBy` checks that each name is a plain SQL identifier, and `SqlQuery#limit` and `SqlQuery#offset` write their numbers into the SQL text.

:::warning
`SqlQuery#where` joins its conditions with `AND` without parentheses, a known bug in 0.0.56. A condition that contains `OR` then mixes with the others: `query.where(a).where(b OR c)` means `(a AND b) OR c`, which returns rows that fail `a`. That's why the fold wraps each condition in `sql"($condition)"`. For the same reason, never pass `Frag.empty` to `SqlQuery#where`: it renders `WHERE` with nothing after it.
:::

We build the support agent's search with it:

```scala mdoc:silent
val builderQuery = searchWithBuilder(loginProblems)
```

`SqlQuery#explain` prints the SQL with numbered placeholders and the type of each parameter. `SqlQuery#toFrag` renders the query as a `Frag`, which runs like the others:

```scala mdoc
println(builderQuery.explain(SqlDialect.SQLite))

transactor.connect {
  builderQuery.toFrag(SqlDialect.SQLite).query[Ticket].foreach(println)
}
```

The builder returns the same tickets in the same order as the `Frag` version. Here is how the two approaches compare:

| Concern                     | `Frag` and `whereAnd`                                       | `SqlQuery`                                           |
| --------------------------- | ----------------------------------------------------------- | ---------------------------------------------------- |
| `SELECT` list               | Written by hand                                             | Generated from the table's schema                    |
| Joins                       | Written by hand                                             | Typed relations; see the joins guide                 |
| Joining conditions with AND | Our `whereAnd` helper                                       | Built in: one `SqlQuery#where` call per condition    |
| A condition with `OR`       | `whereAnd` adds the parentheses                             | Add the parentheses yourself (a known bug in 0.0.56) |
| Sort order                  | Fixed `Frag.literal` per choice                             | `SqlQuery#orderBy`, which checks identifiers         |
| `LIMIT` and `OFFSET`        | Bound parameters                                            | Numbers written into the SQL text                    |
| Other SQL                   | Anything SQL allows, such as `COUNT(*)` on the same `WHERE` | Only what the builder models                         |

Choose `SqlQuery` when the search reads whole rows from one or more joined tables, so the builder can write the column list and the joins for you; [How to Write Joins and Inspect the Generated SQL](./write-joins-and-inspect-generated-sql.md) shows how. Choose `Frag` when the query needs SQL the builder doesn't model, or when you want to reuse the same `WHERE` clause for a count, as `countQuery` does. Either way, the conditions are the same `Option[Frag]` values.

<details>
  <summary>Runnable version, with the setup from section 3 and the functions from earlier sections: <code>Step12QueryBuilderExample.scala</code></summary>

```scala mdoc:embed:sql-examples/src/main/scala/buildaqueryfromoptionalsearchfilters/Step12QueryBuilderExample.scala:show-line-numbers
```

</details>

## Putting It Together

The complete example runs the whole search from one program. It creates and fills the ticket table, defines the search form and every condition function from the sections above, runs the support agent's search with its count, the second page of all tickets, and the hostile input, and then runs the same search through `SqlQuery`. It labels each part of its output and deletes its SQLite file when it finishes:

```scala mdoc:embed:sql-examples/src/main/scala/buildaqueryfromoptionalsearchfilters/CompleteExample.scala
```

Running it prints:

```text
support agent's search:
SELECT id, subject, customer, status, priority, opened_on FROM ticket WHERE (subject LIKE ? ESCAPE '!') AND (customer = ?) AND (status IN (?, ?)) ORDER BY priority ASC, opened_on DESC, id DESC LIMIT ? OFFSET ?
Vector(DbString(%log%), DbString(Acme), DbString(open), DbString(pending), DbInt(20), DbInt(0))
matching tickets: 2
Ticket(1,Cannot log in after password reset,Acme,open,1,2026-09-28)
Ticket(3,Login page loads slowly,Acme,pending,2,2026-10-06)
all tickets: 8
second page of three, newest first: List(5, 4, 3)
hostile input:
SELECT id, subject, customer, status, priority, opened_on FROM ticket WHERE (subject LIKE ? ESCAPE '!') ORDER BY opened_on DESC, id DESC LIMIT ? OFFSET ?
Vector(DbString(%'; DROP TABLE ticket; --%), DbInt(20), DbInt(0))
hostile search finds: List()
tickets in the table: 8
same search with SqlQuery:
SELECT t0."id", t0."subject", t0."customer", t0."status", t0."priority", t0."opened_on" FROM "ticket" AS t0 WHERE (subject LIKE ?1 ESCAPE '!') AND (customer = ?2) AND (status IN (?3, ?4)) ORDER BY t0."priority" ASC, t0."opened_on" DESC, t0."id" DESC LIMIT 20 OFFSET 0
-- params: 1:String, 2:String, 3:String, 4:String
Ticket(1,Cannot log in after password reset,Acme,open,1,2026-09-28)
Ticket(3,Login page loads slowly,Acme,pending,2,2026-10-06)
```

The output matches the sections: the same two tickets from both the `Frag` search and `SqlQuery`, and the hostile input bound as a parameter.

## Running the Examples

All runnable code from this guide is in [`sql-examples/src/main/scala/buildaqueryfromoptionalsearchfilters/`](https://github.com/zio/zio-blocks/tree/main/sql-examples/src/main/scala/buildaqueryfromoptionalsearchfilters). Run any file from that folder with `scala-cli run <File>.scala`, or from `sql-examples` with `sbt "runMain buildaqueryfromoptionalsearchfilters.<ObjectName>"`.

## Going Further

- The [Frag reference](../reference/sql/frag.md) lists every way to build and run a fragment, including `Frag.sequence` and `Frag.values`.
- The [SqlQuery reference](../reference/sql/sql-query.md) covers grouping, `HAVING` and inspecting a built query.
- [sql(table) Checked Interpolation](./sql-checked-interpolation.md) checks table and column names in `sql"..."` at compile time, and explains when `SqlLiteral` is safe to use.
- [How to Upsert Rows and Paginate with Keyset Cursors](./upsert-and-paginate-with-keyset-cursors.md) pages through long results without `OFFSET`.
