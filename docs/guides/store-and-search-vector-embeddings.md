---
id: store-and-search-vector-embeddings
title: "How to Store and Search Vector Embeddings in PostgreSQL"
description: "Store embeddings in a pgvector column with a DbCodec built on String, search by nearest neighbour, pick a distance operator and add an HNSW index."
keywords:
  - "pgvector"
  - "Vector Embeddings"
  - "PostgreSQL"
  - "Nearest Neighbour Search"
  - "DbCodec"
---

In this guide, we store vector embeddings in a PostgreSQL [pgvector](https://github.com/pgvector/pgvector) column and search them by similarity with the ZIO Blocks `sql` module. By the end, you will have an `Embedding` type that you pass to `sql"..."` like any other parameter, a table of book embeddings, a nearest-neighbour query, and an HNSW index that speeds the query up.

We start from a working PostgreSQL setup and add one piece at a time:

- Describe an `Embedding` as text with a [`DbCodec`](../reference/sql/db-codec.md)
- Create a table with a `vector` column
- Insert embeddings with a cast
- Find the nearest books to a query embedding
- Pick a distance operator
- Read embeddings back
- Reject a vector with the wrong number of dimensions
- Speed up the search with an HNSW index
- Skip the casts with a driver setting

This guide assumes you already have a PostgreSQL repository running, as in [How to Connect to PostgreSQL with a Connection Pool](./connect-to-postgresql-with-a-connection-pool.md), and you know how to give a type its own column mapping with `DbCodec#transform`, as in [How to Map Custom Column Types](./map-custom-column-types.md). We don't explain either again here.

## The Problem

An embedding is a list of numbers, usually the output of a machine-learning model, that stands for the meaning of a piece of text. Two texts with similar meanings get embeddings that are close to each other, so a search for similar texts becomes a search for the nearest vectors. The pgvector extension adds a `vector(n)` column type and distance operators to PostgreSQL for exactly this.

The JDBC driver has no standard type for `vector`, so plain JDBC code wraps the value in a driver-specific `PGobject` every time it writes one. This is the plain JDBC code, PostgreSQL driver 42.7.14 (not compiled here, because the docs build has no PostgreSQL driver; we compiled and ran it against PostgreSQL 16 with pgvector):

```scala
import java.sql.Connection
import org.postgresql.util.PGobject

def insertEmbedding(connection: Connection, title: String, values: Array[Float]): Unit = {
  val vector = new PGobject()
  vector.setType("vector")
  vector.setValue(values.mkString("[", ",", "]"))

  val statement = connection.prepareStatement("INSERT INTO book_embeddings (title, embedding) VALUES (?, ?)")
  try {
    statement.setString(1, title)
    statement.setObject(2, vector)
    statement.executeUpdate()
  } finally statement.close()
}
```

This works, but every call site repeats the wrapping, the text format lives in the caller, and nothing ties the Scala type to the column. The obvious next step, passing the `PGobject` to a `sql"..."` interpolator, doesn't compile, as the next section shows.

In this guide, we describe the embedding once, as a type with a `DbCodec`, and let the `sql` module bind it wherever it appears in a query.

## Prerequisites

Add the `sql` module and the PostgreSQL JDBC driver to your `build.sbt`. [How to Add ZIO Blocks to Your Build](../getting-started/installation.md) explains the two artifact names:

```scala
// Scala 3.3 to 3.8
libraryDependencies += "dev.zio" %% "zio-blocks-sql" % "@VERSION@"

// Scala 3.9 and newer
libraryDependencies += "dev.zio" %% "zio-blocks-next-sql" % "@VERSION@"
scalacOptions += "-experimental"

// The PostgreSQL JDBC driver, for every Scala version
libraryDependencies += "org.postgresql" % "postgresql" % "42.7.14"
```

The `sql` module is published for Scala 3 only, and [`JdbcTransactor`](../reference/sql/transactor.md) runs on the JVM and needs JDK 21 or newer, so the code in this guide uses Scala 3 syntax. We don't need a pgvector client library: the codec in this guide is a few lines on top of `String`, and adds no dependency.

You need a PostgreSQL server with the pgvector extension installed. If you have Docker, the `pgvector/pgvector` image is PostgreSQL 16 with the extension built in. This command starts it with the same `bookstore` database, user and password as the pool guide:

```bash
docker run --rm -d --name bookstore-db -p 5432:5432 \
  -e POSTGRES_USER=bookstore -e POSTGRES_PASSWORD=bookstore -e POSTGRES_DB=bookstore \
  pgvector/pgvector:pg16
```

The code in this guide reads the database URL, user and password from `BOOKSTORE_DB_URL`, `BOOKSTORE_DB_USER` and `BOOKSTORE_DB_PASSWORD`, with defaults that match the Docker command, so it runs without any variables set. It builds a `JdbcTransactor` straight from the URL to stay short. To run the same code on a connection pool, build the transactor from the pool as the pool guide shows.

## Describe an Embedding as Text

The interpolator accepts a parameter only if it has a [`DbParam`](../reference/sql/db-param.md), and the driver's `PGobject` has none. Passing one, as in `sql"INSERT INTO book_embeddings (title, embedding) VALUES (${"Zionomicon"}, $vector)"` with `vector` a `PGobject`, stops the compiler with this error:

```text
No DbParam/Frag/SqlLiteral/DbValue instance found for type org.postgresql.util.PGobject. Supported: DbParam[T], Frag (verbatim), SqlLiteral (verbatim), DbValue. Use sql"..." or StringContext(...).sql(table)(...) accordingly.
```

The fix is to give our own type a `DbCodec`, which gives it a `DbParam` for free, instead of reaching for a driver type.

pgvector writes a vector as text, as `[0.8,1.0,0.9]`, and PostgreSQL accepts the same text as input. That makes a `String` the simplest bridge: we convert an `Embedding` to that text on the way in, parse it on the way out, and `DbCodec#transform` builds the codec from the two functions:

```scala mdoc:silent
import zio.blocks.sql.*

final case class Embedding(values: IndexedSeq[Float])

object Embedding {

  def render(embedding: Embedding): String = embedding.values.mkString("[", ",", "]")

  def parse(text: String): Embedding = {
    val inner = text.substring(1, text.length - 1)
    if (inner.isEmpty) Embedding(IndexedSeq.empty)
    else Embedding(inner.split(',').toIndexedSeq.map(_.toFloat))
  }

  given DbCodec[Embedding] = DbCodec[String].transform(parse)(render)
}
```

The sections below read the database settings and create the transactor the same way each time, so we define that once:

```scala mdoc:silent
def shelfTransactor(): JdbcTransactor =
  JdbcTransactor.fromUrl(
    sys.env.getOrElse("BOOKSTORE_DB_URL", "jdbc:postgresql://localhost:5432/bookstore"),
    sys.env.getOrElse("BOOKSTORE_DB_USER", "bookstore"),
    sys.env.getOrElse("BOOKSTORE_DB_PASSWORD", "bookstore"),
    SqlDialect.PostgreSQL
  )
```

The sample data for the rest of the guide is five real Scala books. Real embedding models produce hundreds or thousands of numbers per text; we use three, scored by hand for functional programming, effects and concurrency, so that every output stays readable:

```scala mdoc:silent
val shelfBooks: List[(String, Embedding)] = List(
  "Zionomicon"                      -> Embedding(IndexedSeq(0.8f, 1.0f, 0.9f)),
  "Programming in Scala"            -> Embedding(IndexedSeq(0.5f, 0.1f, 0.1f)),
  "Functional Programming in Scala" -> Embedding(IndexedSeq(1.0f, 0.5f, 0.2f)),
  "Scala Cookbook"                  -> Embedding(IndexedSeq(0.3f, 0.1f, 0.2f)),
  "Scala with Cats"                 -> Embedding(IndexedSeq(0.9f, 0.7f, 0.1f))
)
```

Rendering and parsing are inverses, so an embedding that goes through the text form comes back unchanged:

```scala mdoc
val zionomicon = shelfBooks.head._2

Embedding.render(zionomicon)

Embedding.parse(Embedding.render(zionomicon)) == zionomicon
```

Passing the embedding to the `sql` interpolator now compiles, because the `DbCodec` gives it a `DbParam`. To see what the interpolator hands to the driver, we render the statement for PostgreSQL and list its parameters:

```scala mdoc
val insert = sql"INSERT INTO book_embeddings (title, embedding) VALUES (${"Zionomicon"}, $zionomicon)"

insert.sql(SqlDialect.PostgreSQL)

insert.queryParams
```

The embedding travels as one `DbString` holding the text form, which the driver binds as a `character varying` parameter. That detail matters in two sections, because PostgreSQL treats a `varchar` and a `vector` as different types.

## Create the Table

The table keeps one row per book, with a `vector(3)` column whose size must match the embeddings we store. We write the `CREATE TABLE` statement ourselves, because `Table#createTable` has no way to declare a `vector(n)` column, and the `vector` extension has to exist before the table does:

```scala mdoc:silent
def createShelf()(using DbCon): Unit = {
  sql"CREATE EXTENSION IF NOT EXISTS vector".update
  sql"DROP TABLE IF EXISTS book_embeddings".update
  sql"""CREATE TABLE book_embeddings (
          id        BIGSERIAL PRIMARY KEY,
          title     TEXT        NOT NULL,
          embedding VECTOR(3)   NOT NULL
        )""".update
}
```

`CREATE EXTENSION` needs a role that is allowed to create extensions; the superuser that the Docker image creates is one. The `DROP TABLE` line makes the examples in this guide repeatable.

:::warning
In version 0.0.56, `Repo.derived` and `Table.derived` throw `UnsupportedOperationException: Typed DDL does not support reflect node IndexedSeq[Float]` for a case class with an `Embedding` field, because they compute the table's columns from the `Schema` and ignore the `DbCodec`. [How to Map Custom Column Types](./map-custom-column-types.md) describes the same limitation for a `List[String]` field. This guide writes its statements with `sql"..."` instead.
:::

## Insert Embeddings with a Cast

An insert passes each embedding as a parameter and casts it to `vector` in the SQL. The cast is what tells PostgreSQL to convert the text into a vector, so we write it right after the parameter:

```scala mdoc:silent
def stockShelf()(using DbCon): Unit =
  shelfBooks.foreach { (title, embedding) =>
    sql"INSERT INTO book_embeddings (title, embedding) VALUES ($title, $embedding::vector)".update
  }
```

To run both statements in one transaction and count the rows:

```scala mdoc:compile-only
val transactor = shelfTransactor()

val count = transactor.transact {
  createShelf()
  stockShelf()
  sql"SELECT COUNT(*) FROM book_embeddings".queryOne[Long].getOrElse(0L)
}

println(s"Stored $count embeddings")
```

Run against PostgreSQL 16 with pgvector, it prints:

```text
Stored 5 embeddings
```

Without the cast, the same insert fails. The driver sends the embedding as `character varying`, and PostgreSQL doesn't convert that to `vector` on its own:

```text
ERROR: column "embedding" is of type vector but expression is of type character varying
```

The same cast is needed wherever a query parameter meets a `vector`, not only in an `INSERT`. The next section shows it in a search.

## Find the Nearest Books

A nearest-neighbour search orders the rows by their distance to a query embedding and keeps the first few. pgvector's `<->` operator is the Euclidean (L2) distance between two vectors, so we order by it and take three rows. The query is an embedding for "a book about effects and concurrency", scored on the same three topics:

```scala mdoc:compile-only
val transactor = shelfTransactor()

val query = Embedding(IndexedSeq(0.6f, 0.9f, 1.0f))

val nearest = transactor.transact {
  createShelf()
  stockShelf()
  sql"SELECT title FROM book_embeddings ORDER BY embedding <-> $query::vector LIMIT 3".query[String]
}

nearest.foreach(println)
```

The three closest books, nearest first, are:

```text
Zionomicon
Scala with Cats
Functional Programming in Scala
```

Because `Embedding` has a `DbCodec`, `$query::vector` is an ordinary parameter, and the [`Frag#query`](../reference/sql/frag.md) result type comes from the `[String]` type argument. The rows come back in distance order, which is the order the `ORDER BY` gives them.

## Pick a Distance Operator

pgvector offers three distance operators, and which one ranks the books best depends on what your embedding model means by "close":

| Operator | Distance                | Smaller means         | Pair it with an index using |
| -------- | ----------------------- | --------------------- | --------------------------- |
| `<->`    | Euclidean (L2)          | Closer in space       | `vector_l2_ops`             |
| `<=>`    | Cosine                  | Closer in direction   | `vector_cosine_ops`         |
| `<#>`    | Negative inner product  | Larger inner product  | `vector_ip_ops`             |

Cosine distance compares direction only and ignores length, which is why many embedding models are trained for it; check the documentation of yours. The inner product grows with length as well as with direction. pgvector returns the *negative* inner product, because an index can only order rows from smallest to largest, so the best match has the smallest, most negative value and `ORDER BY embedding <#> ...` stays ascending. For embeddings normalised to length 1, the inner product and cosine distance rank the rows the same way.

To see how the choice changes the ranking, we run the same query with each operator and print the distance next to every title:

```scala mdoc:compile-only
val transactor = shelfTransactor()

val query = Embedding(IndexedSeq(0.6f, 0.9f, 1.0f))

transactor.transact {
  createShelf()
  stockShelf()

  println("Euclidean distance (<->):")
  sql"SELECT title, embedding <-> $query::vector FROM book_embeddings ORDER BY embedding <-> $query::vector LIMIT 3"
    .query[(String, Double)]
    .foreach((title, distance) => println(f"  $title%-32s $distance%.4f"))

  println("Cosine distance (<=>):")
  sql"SELECT title, embedding <=> $query::vector FROM book_embeddings ORDER BY embedding <=> $query::vector LIMIT 3"
    .query[(String, Double)]
    .foreach((title, distance) => println(f"  $title%-32s $distance%.4f"))

  println("Negative inner product (<#>):")
  sql"SELECT title, embedding <#> $query::vector FROM book_embeddings ORDER BY embedding <#> $query::vector LIMIT 3"
    .query[(String, Double)]
    .foreach((title, distance) => println(f"  $title%-32s $distance%.4f"))
}
```

Against the sample data, it prints:

```text
Euclidean distance (<->):
  Zionomicon                       0.2449
  Scala with Cats                  0.9695
  Functional Programming in Scala  0.9798
Cosine distance (<=>):
  Zionomicon                       0.0112
  Scala Cookbook                   0.1473
  Scala with Cats                  0.2468
Negative inner product (<#>):
  Zionomicon                       -2.2800
  Scala with Cats                  -1.2700
  Functional Programming in Scala  -1.2500
```

Zionomicon is first in every list. Scala Cookbook appears only in the cosine list: its scores are small, so it is far from the query in space, but its direction is closer to the query's than Functional Programming in Scala's is.

## Read Embeddings Back

A `vector` column reads back as text, and the same `DbCodec` that wrote the embedding parses it. To read a single embedding, ask for the column and give `Frag#queryOne` the `Embedding` type:

```scala mdoc:compile-only
val transactor = shelfTransactor()

transactor.transact {
  createShelf()
  stockShelf()

  val stored = sql"SELECT embedding FROM book_embeddings WHERE title = ${"Zionomicon"}".queryOne[Embedding]
  println(s"Stored:   ${stored.map(_.values.mkString("[", ", ", "]"))}")
  println(s"Expected: ${Some(zionomicon.values.mkString("[", ", ", "]"))}")
  println(s"Same:     ${stored.contains(zionomicon)}")

  val rows = sql"SELECT title, embedding FROM book_embeddings ORDER BY id LIMIT 2".query[(String, Embedding)]
  rows.foreach((title, embedding) => println(s"$title -> ${embedding.values.size} dimensions"))
}
```

The values come back exactly as stored, and an `Embedding` works as one element of a row tuple:

```text
Stored:   Some([0.8, 1.0, 0.9])
Expected: Some([0.8, 1.0, 0.9])
Same:     true
Zionomicon -> 3 dimensions
Programming in Scala -> 3 dimensions
```

The parser handles the text PostgreSQL produces: a vector that is stored from `0.1` comes back as `0.1`, and spaces that were typed in the input, as in `[1, 2.5 ,3]`, are normalised to `[1,2.5,3]` by the server before we read them. A vector must have at least one dimension, so `Embedding.parse` never sees `[]` from a real column; the `isEmpty` branch only keeps the function total.

## Reject the Wrong Number of Dimensions

The `3` in `vector(3)` is enforced by the server. An embedding with four numbers is rejected, the driver throws an `SQLException`, and the surrounding `JdbcTransactor#transact` rolls the transaction back. To see the error and check that nothing was stored:

```scala mdoc:compile-only
import java.sql.SQLException

val transactor = shelfTransactor()

transactor.transact {
  createShelf()
  stockShelf()
}

val tooLong = Embedding(IndexedSeq(0.1f, 0.2f, 0.3f, 0.4f))

try
  transactor.transact {
    sql"INSERT INTO book_embeddings (title, embedding) VALUES (${"Too Long"}, $tooLong::vector)".update
  }
catch {
  case e: SQLException => println(s"Rejected: ${e.getMessage}")
}

val count = transactor.connect(sql"SELECT COUNT(*) FROM book_embeddings".queryOne[Long].getOrElse(0L))
println(s"Rows stored: $count")
```

The message names both sizes, and the table still holds the five books:

```text
Rejected: ERROR: expected 3 dimensions, not 4
Rows stored: 5
```

A search with the wrong number of dimensions fails too, with `different vector dimensions 3 and 4` when PostgreSQL compares the two vectors directly. So a model that is replaced by one with a different output size shows up at the first insert or search instead of silently mixing vectors of different sizes in one column.

## Speed Up Search with an HNSW Index

Without an index, PostgreSQL measures the distance to every row for every search. An HNSW index, a graph that pgvector builds over the vectors, finds near neighbours without visiting every row, at the price of returning approximate results. We create the index with the operator class that matches the distance operator the queries use:

```scala mdoc:compile-only
val transactor = shelfTransactor()

transactor.transact {
  createShelf()
  stockShelf()

  sql"CREATE INDEX book_embeddings_hnsw ON book_embeddings USING hnsw (embedding vector_cosine_ops)".update
}
```

Only a query whose `ORDER BY` operator matches the operator class can use the index: `vector_cosine_ops` serves `<=>`, and a search with `<->` ignores it. To check this, we ask PostgreSQL for the query plan of each search. With five rows PostgreSQL would choose a sequential scan whatever the index offers, so the example turns sequential scans off inside the transaction with `SET LOCAL`:

```scala mdoc:compile-only
val transactor = shelfTransactor()

val query = Embedding(IndexedSeq(0.6f, 0.9f, 1.0f))

transactor.transact {
  createShelf()
  stockShelf()

  sql"CREATE INDEX book_embeddings_hnsw ON book_embeddings USING hnsw (embedding vector_cosine_ops)".update
  sql"SET LOCAL enable_seqscan = off".update

  val cosinePlan = sql"EXPLAIN SELECT title FROM book_embeddings ORDER BY embedding <=> $query::vector LIMIT 3".query[String]
  val l2Plan     = sql"EXPLAIN SELECT title FROM book_embeddings ORDER BY embedding <-> $query::vector LIMIT 3".query[String]

  println(s"Cosine search uses the index:    ${cosinePlan.exists(_.contains("book_embeddings_hnsw"))}")
  println(s"Euclidean search uses the index: ${l2Plan.exists(_.contains("book_embeddings_hnsw"))}")
}
```

Only the cosine search uses the cosine index:

```text
Cosine search uses the index:    true
Euclidean search uses the index: false
```

The query also has to order by the distance operator itself, ascending, and end with a `LIMIT`. A query that orders by an expression of the distance, such as `ORDER BY 1 - (embedding <=> ...) DESC` to sort by similarity, can't use the index.

If your queries use two operators, create one index for each operator class. An index is worth its build time and memory only once the table is large; the plan check above forces it onto a five-row table only to show the matching rule.

## Skip the Casts with a Driver Setting

The casts exist because the driver sends every `String` parameter as `character varying`. The driver's `stringtype=unspecified` URL setting sends them without a type instead, and PostgreSQL then infers the type from the place where the parameter appears, which turns the text into a `vector` for both the insert and the search. This is the same setting that the [jsonb section](./map-custom-column-types.md#use-a-jsonb-column-on-postgresql) of the custom-types guide uses. To run the same cast-free statements on both kinds of connection:

```scala mdoc:compile-only
import java.sql.SQLException

val baseUrl = sys.env.getOrElse("BOOKSTORE_DB_URL", "jdbc:postgresql://localhost:5432/bookstore")
val user    = sys.env.getOrElse("BOOKSTORE_DB_USER", "bookstore")
val secret  = sys.env.getOrElse("BOOKSTORE_DB_PASSWORD", "bookstore")

val query = Embedding(IndexedSeq(0.6f, 0.9f, 1.0f))

def withoutCasts(label: String, url: String): Unit = {
  val transactor = JdbcTransactor.fromUrl(url, user, secret, SqlDialect.PostgreSQL)
  try {
    val nearest = transactor.transact {
      createShelf()
      sql"INSERT INTO book_embeddings (title, embedding) VALUES (${"Zionomicon"}, $query)".update
      sql"SELECT title FROM book_embeddings ORDER BY embedding <-> $query LIMIT 1".query[String]
    }
    println(s"$label: stored and found ${nearest.mkString(", ")}")
  } catch {
    case e: SQLException => println(s"$label: ${e.getMessage.linesIterator.next()}")
  }
}

withoutCasts("default settings          ", baseUrl)
withoutCasts("stringtype=unspecified    ", baseUrl + (if (baseUrl.contains("?")) "&" else "?") + "stringtype=unspecified")
```

The default connection rejects the statements, and the connection with the setting accepts them:

```text
default settings          : ERROR: column "embedding" is of type vector but expression is of type character varying
stringtype=unspecified    : stored and found Zionomicon
```

The setting applies to every `String` parameter on the connection, and PostgreSQL then infers each one's type from where it appears, so a query that depends on a `varchar` parameter, such as a call to a function with several overloads, can become ambiguous. The cast in the SQL changes one statement and nothing else, so it is the safer default; the setting is worth it when many statements bind embeddings and you would rather not repeat the cast.

## Putting It Together

The complete example creates the table, stores five books, searches by cosine distance, reads one embedding back, shows the error for a vector with the wrong number of dimensions and adds an HNSW index:

```scala mdoc:embed:sql-examples/src/main/scala/storeandsearchvectorembeddings/CompleteExample.scala
```

## Running the Examples

All examples in this guide have runnable Scala files in the `sql-examples` project. They need a running PostgreSQL server with pgvector, so start one with the Docker command from [Prerequisites](#prerequisites) first. Then clone the repository and change into the examples project:

```bash
git clone https://github.com/zio/zio-blocks.git
cd zio-blocks/sql-examples
```

The examples share the `Embedding` type, the transactor, the table and the five books, which live in one file:

<details>
  <summary>sql-examples/src/main/scala/storeandsearchvectorembeddings/Shelf.scala</summary>

```scala mdoc:embed:sql-examples/src/main/scala/storeandsearchvectorembeddings/Shelf.scala:show-line-numbers
```

</details>

### Insert Embeddings

This example creates the table, inserts the five books with a cast and counts the rows.

<details>
  <summary>sql-examples/src/main/scala/storeandsearchvectorembeddings/InsertExample.scala</summary>

```scala mdoc:embed:sql-examples/src/main/scala/storeandsearchvectorembeddings/InsertExample.scala:show-line-numbers
```

</details>

Run it from the `sql-examples` directory:

```bash
sbt "runMain storeandsearchvectorembeddings.InsertExample"
```

### Find the Nearest Books

This example asks for the three books closest to a query embedding by Euclidean distance.

<details>
  <summary>sql-examples/src/main/scala/storeandsearchvectorembeddings/NearestExample.scala</summary>

```scala mdoc:embed:sql-examples/src/main/scala/storeandsearchvectorembeddings/NearestExample.scala:show-line-numbers
```

</details>

Run it:

```bash
sbt "runMain storeandsearchvectorembeddings.NearestExample"
```

### Pick a Distance Operator

This example ranks the same books with each of the three distance operators and prints the distances.

<details>
  <summary>sql-examples/src/main/scala/storeandsearchvectorembeddings/OperatorsExample.scala</summary>

```scala mdoc:embed:sql-examples/src/main/scala/storeandsearchvectorembeddings/OperatorsExample.scala:show-line-numbers
```

</details>

Run it:

```bash
sbt "runMain storeandsearchvectorembeddings.OperatorsExample"
```

### Read Embeddings Back

This example reads one embedding and two rows, and checks that the values survive the round trip.

<details>
  <summary>sql-examples/src/main/scala/storeandsearchvectorembeddings/ReadBackExample.scala</summary>

```scala mdoc:embed:sql-examples/src/main/scala/storeandsearchvectorembeddings/ReadBackExample.scala:show-line-numbers
```

</details>

Run it:

```bash
sbt "runMain storeandsearchvectorembeddings.ReadBackExample"
```

### Reject the Wrong Number of Dimensions

This example inserts an embedding with four numbers into a `vector(3)` column and prints the error.

<details>
  <summary>sql-examples/src/main/scala/storeandsearchvectorembeddings/DimensionMismatchExample.scala</summary>

```scala mdoc:embed:sql-examples/src/main/scala/storeandsearchvectorembeddings/DimensionMismatchExample.scala:show-line-numbers
```

</details>

Run it:

```bash
sbt "runMain storeandsearchvectorembeddings.DimensionMismatchExample"
```

### Speed Up Search with an HNSW Index

This example creates an HNSW index and checks which of two searches can use it.

<details>
  <summary>sql-examples/src/main/scala/storeandsearchvectorembeddings/HnswIndexExample.scala</summary>

```scala mdoc:embed:sql-examples/src/main/scala/storeandsearchvectorembeddings/HnswIndexExample.scala:show-line-numbers
```

</details>

Run it:

```bash
sbt "runMain storeandsearchvectorembeddings.HnswIndexExample"
```

### Skip the Casts with a Driver Setting

This example runs the same cast-free statements on a default connection and on one with `stringtype=unspecified`.

<details>
  <summary>sql-examples/src/main/scala/storeandsearchvectorembeddings/StringTypeExample.scala</summary>

```scala mdoc:embed:sql-examples/src/main/scala/storeandsearchvectorembeddings/StringTypeExample.scala:show-line-numbers
```

</details>

Run it:

```bash
sbt "runMain storeandsearchvectorembeddings.StringTypeExample"
```

### The Complete Example

The complete example from "Putting It Together" runs the whole flow in one program:

```bash
sbt "runMain storeandsearchvectorembeddings.CompleteExample"
```

To compile every example without running them, which needs no database:

```bash
sbt compile
```

When you are done, stop the database container:

```bash
docker stop bookstore-db
```

## Going Further

- [DbCodec](../reference/sql/db-codec.md) describes `DbCodec#transform` and the other ways to build a codec.
- [How to Map Custom Column Types](./map-custom-column-types.md) covers the same pattern for newtypes, enums, JSON documents and arrays.
- [How to Connect to PostgreSQL with a Connection Pool](./connect-to-postgresql-with-a-connection-pool.md) builds the transactor on a HikariCP pool for production use.
- [The SQL Module](../reference/sql/index.md) overview links to every `sql` reference page.
- The `pgvector` client library for Java, in the `com.pgvector` group, offers a `PGvector` type for the driver. It is an alternative to the `String` codec in this guide; this guide adds no dependency for it.
