---
id: map-custom-column-types
title: "How to Map Custom Column Types"
description: "Store newtypes, opaque types, enums, JSON documents, PostgreSQL arrays and timestamps in SQL columns with the ZIO Blocks sql module."
keywords:
  - "Column Types"
  - "JSONB"
  - "PostgreSQL Arrays"
  - "Timestamps"
  - "DbCodec"
  - "PgCodec"
---

In this guide, we store a bookstore's `Book` in one SQL table, field by field, without flattening the model or writing conversions by hand. By the end, you will have a repository that writes and reads a validated ISBN newtype, an opaque ID, an enum, a nested case class stored as JSON, a list of tags stored as a PostgreSQL array, and a publication timestamp that comes back exactly as it went in, on SQLite and on PostgreSQL.

We take one column type at a time:

- A newtype and an opaque type, each in one plain column
- An enum, stored as the name of its case
- A nested case class, stored as JSON (`TEXT` on SQLite, `jsonb` on PostgreSQL)
- A `List[String]`, stored as JSON on SQLite and as a `text[]` array on PostgreSQL
- An `Instant`, stored without time-zone shifts
- Nullable versions of these columns
- One repository with every custom column

This guide builds on [Build Your First Database Repository on SQLite](./your-first-sqlite-repository.md). We use `JdbcTransactor`, `Repo`, `Table` and `sql"..."` the way that tutorial introduces them and don't explain them again.

## The Problem

The `sql` module maps simple fields such as `String`, `Int` and `Long` to columns without any work. A real model has more than that: an `Isbn` that must be valid, an enum for the format, a nested record of publisher details, a list of tags and a timestamp. Without a mapping for those types, we either flatten the model into strings and numbers, or we read and write every column by hand.

Written with plain JDBC, reading one book looks like this:

```scala mdoc:compile-only
import java.sql.{Connection, Timestamp}
import java.time.Instant
import java.util.{Calendar, TimeZone}

enum Format { case Hardcover, Paperback, Ebook }
case class Metadata(publisher: String, edition: Int, pages: Int)
case class Book(isbn: String, title: String, format: Format, metadata: Metadata, tags: List[String], publishedAt: Instant)

def findBook(conn: Connection, isbn: String): Option[Book] = {
  val query = conn.prepareStatement("SELECT isbn, title, format, metadata, tags, published_at FROM books WHERE isbn = ?")
  query.setString(1, isbn)
  val rs = query.executeQuery()
  try {
    if (!rs.next()) None
    else {
      // The JSON document: parse it by hand, or pull in a JSON library
      val json      = rs.getString("metadata")
      val fields    = json.stripPrefix("{").stripSuffix("}").split(",").map(_.split(":", 2)).map(kv => kv(0).trim.stripPrefix("\"").stripSuffix("\"") -> kv(1).trim).toMap
      val metadata  = Metadata(fields("publisher").stripPrefix("\"").stripSuffix("\""), fields("edition").toInt, fields("pages").toInt)
      // The array: one more JDBC call, and a cast
      val tags      = rs.getArray("tags").getArray.asInstanceOf[Array[String]].toList
      // The timestamp: forget the calendar and the JVM's time zone shifts it
      val utc       = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
      val published = rs.getTimestamp("published_at", utc).toInstant
      Some(Book(rs.getString("isbn"), rs.getString("title"), Format.valueOf(rs.getString("format")), metadata, tags, published))
    }
  } finally query.close()
}
```

Every custom type adds its own conversion, and each one is a place to get it wrong. The ISBN is a plain `String`, so nothing checks it. `Format.valueOf` throws on a value written by another program. The hand-written JSON parser breaks on the first comma inside a publisher's name. Writing the row back needs the same conversions in reverse, and they have to stay in sync with this function.

In this guide, we give each type a single description, mostly through its `Schema`, and let the `sql` module derive the column mapping from it, in both directions.

## Prerequisites

Add the `sql` module and the JDBC drivers to your `build.sbt`. [How to Add ZIO Blocks to Your Build](./add-zio-blocks-to-your-build.md) explains the two artifact names:

```scala
// Scala 3.3 to 3.8
libraryDependencies += "dev.zio" %% "zio-blocks-sql" % "@VERSION@"

// Scala 3.9 and newer
libraryDependencies += "dev.zio" %% "zio-blocks-next-sql" % "@VERSION@"
scalacOptions += "-experimental"

// The JDBC drivers, for every Scala version
libraryDependencies += "org.xerial"     % "sqlite-jdbc" % "3.53.4.0"
libraryDependencies += "org.postgresql" % "postgresql"  % "42.7.14"
```

The `sql` module is published for Scala 3 only, and `JdbcTransactor` runs on the JVM and needs JDK 21 or newer. Opaque types and `enum` are Scala 3 features, so the code in this guide uses Scala 3 syntax throughout.

The SQLite parts run on a temporary database file. For the PostgreSQL parts, you need a PostgreSQL server; [How to Connect to PostgreSQL with a Connection Pool](./connect-to-postgresql-with-a-connection-pool.md) shows a `docker run` command that starts one with a `bookstore` database, user and password. The PostgreSQL code reads the password from the `BOOKSTORE_DB_PASSWORD` environment variable.

## The Bookstore Table

Our `books` table holds one row per book, and every field except the title needs a decision about its column. This table is the plan for the rest of the guide:

| Field         | Scala type                  | SQLite column            | PostgreSQL column | What we write                                     |
| ------------- | --------------------------- | ------------------------ | ----------------- | ------------------------------------------------- |
| `id`          | `opaque type BookId = Long` | `INTEGER`                | `BIGINT`          | Nothing                                           |
| `isbn`        | `Isbn` newtype              | `TEXT`                   | `TEXT`            | A `Schema` built with `Schema#transform`          |
| `format`      | `enum Format`               | `TEXT`                   | `TEXT`            | Nothing (the case name is stored)                 |
| `metadata`    | `case class Metadata`       | `TEXT` holding JSON      | `JSONB`           | A `Repo` built from the codec's columns           |
| `tags`        | `List[String]`              | `TEXT` holding JSON      | `TEXT[]`          | `PgCodec.listStringCodec` on PostgreSQL           |
| `publishedAt` | `java.time.Instant`         | `INTEGER` (milliseconds) | `TIMESTAMPTZ`     | Nothing, once the column type is right            |

One thing applies to every row of this table: we write the `CREATE TABLE` statement ourselves. `Table#createTable` declares no primary key in version 0.0.56, and it picks the wrong column type for some of these fields, as the sections below show.

## Store a Newtype or an Opaque Type in One Column

A newtype is a one-field case class that gives a plain value its own type, so an `Isbn` can't be passed where a title is expected. The obvious version, a case class with a derived `Schema`, doesn't map to one column. To the `sql` module it is a record with a field called `value`:

```scala mdoc:silent
import zio.blocks.schema.{Schema, SchemaError}
import zio.blocks.sql.*
import scala.util.Try

final case class PlainIsbn(value: String)

object PlainIsbn {
  given Schema[PlainIsbn] = Schema.derived
}

case class PlainBook(id: Long, isbn: PlainIsbn)

object PlainBook {
  given Schema[PlainBook] = Schema.derived
}
```

The table expects a column named after the record's field, and the codec writes the value as a JSON object:

```scala mdoc
println(Table.derived[PlainBook].columns)
println(DbCodec.derived[PlainBook].toDbValues(PlainBook(2L, PlainIsbn("9780981531601"))))
```

What we want is a `TEXT` column that holds `9780981531601`. The fix is to describe `Isbn` as a `String` with a check on the way in. This is the validated newtype from [How to Define Validated Newtypes](./validated-newtypes.md): a private constructor, a smart constructor `Isbn.parse` that returns `Either[SchemaError, Isbn]`, and a `Schema` built with `Schema#transform`, which takes a function from `String` to `Isbn` and one back:

```scala mdoc:silent
final case class Isbn private (value: String)

object Isbn {
  def parse(input: String): Either[SchemaError, Isbn] = {
    val digits = input.filterNot(c => c == '-' || c == ' ')
    if (digits.length != 13 || !digits.forall(_.isDigit))
      Left(SchemaError.validationFailed(s"ISBN must have 13 digits, got '$input'"))
    else {
      val weighted = digits.init.zipWithIndex.map { case (c, i) => c.asDigit * (if (i % 2 == 0) 1 else 3) }
      if ((10 - weighted.sum % 10) % 10 == digits.last.asDigit) Right(new Isbn(digits))
      else Left(SchemaError.validationFailed(s"ISBN check digit is wrong in '$input'"))
    }
  }

  def unsafe(input: String): Isbn = parse(input).fold(e => throw e, identity)

  given Schema[Isbn] = Schema[String].transform(unsafe, _.value)
}
```

A Scala 3 opaque type needs even less. `BookId` is a `Long` at runtime, and the `Schema` and `DbCodec` macros see through it, so it maps to an integer column with no `Schema` of its own. Its companion needs `BookId.apply` and `BookId.unwrap`, which the `DbCodec` for the ID column uses to convert to and from `Long`:

```scala mdoc:silent
object ids {
  opaque type BookId = Long

  object BookId {
    def apply(value: Long): BookId = value
    def unwrap(id: BookId): Long   = id
  }
}

import ids.BookId

case class Book(id: BookId, title: String, isbn: Isbn)

object Book {
  given Schema[Book] = Schema.derived
}

val books = Repo.derived[Book, BookId]("books", "id", _.id)

val dbFile     = java.nio.file.Files.createTempFile("bookstore", ".db")
val transactor = JdbcTransactor.fromUrl(s"jdbc:sqlite:$dbFile", SqlDialect.SQLite)
```

With both fields mapped, the row stores plain values, and `Repo#all` turns them back into `BookId` and `Isbn`:

```scala mdoc
transactor.connect {
  sql"""CREATE TABLE books (
    id    INTEGER PRIMARY KEY,
    title TEXT NOT NULL,
    isbn  TEXT NOT NULL
  )""".update
  books.insert(Book(BookId(1L), "Programming in Scala", Isbn.unsafe("978-0-9815316-0-1")))
  books.insert(Book(BookId(2L), "Functional Programming in Scala", Isbn.unsafe("978-1-61729-065-7")))
  println(sql"SELECT id, title, isbn FROM books ORDER BY id".query[(Long, String, String)])
  books.all.foreach(println)
}
```

`Isbn.parse` stored the bare digits, so `978-0-9815316-0-1` went in as `9780981531601`. To filter by ISBN in our own SQL, we pass the underlying `String`. The `sql"..."` interpolator has no parameter instance for `Isbn`, because the `DbCodec` it looks for can't be derived for a case class with a private constructor:

```scala mdoc
transactor.connect {
  val isbn = Isbn.unsafe("9780981531601")
  println(sql"SELECT title FROM books WHERE isbn = ${isbn.value}".query[String])
}
```

The check also runs when we read. If another program writes an ISBN with a wrong check digit, every query that returns that row fails with the `SchemaError` from `Isbn.parse`, while queries that skip the row still work:

```scala mdoc
transactor.connect {
  sql"INSERT INTO books (id, title, isbn) VALUES (3, 'Scala with Cats', '9781617290658')".update
  println(Try(books.all))
  println(books.find(BookId(2L)))
}
```

:::tip
A newtype doesn't have to validate. For a plain wrapper, `Schema[String].transform(Isbn(_), _.value)` stores it the same way.
:::

## Store an Enum as Its Case Name

An enum whose cases have no fields needs no work at all: the derived codec stores the name of the case in a `TEXT` column and reads it back by name. We start a fresh scope for this section, with its own database file:

```scala mdoc:reset:silent
import zio.blocks.schema.{Modifier, Schema}
import zio.blocks.sql.*
import scala.util.Try

enum Format {
  case Hardcover, Paperback, Ebook
}

object Format {
  given Schema[Format] = Schema.derived
}

case class Book(id: Long, title: String, format: Format)

object Book {
  given Schema[Book] = Schema.derived
}

val books = Repo.derived[Book, Long]("books", "id", _.id)

val dbFile     = java.nio.file.Files.createTempFile("bookstore", ".db")
val transactor = JdbcTransactor.fromUrl(s"jdbc:sqlite:$dbFile", SqlDialect.SQLite)
```

The column holds `Ebook` and `Paperback`, and an enum value works as a parameter in our own SQL:

```scala mdoc
transactor.connect {
  sql"CREATE TABLE books (id INTEGER PRIMARY KEY, title TEXT NOT NULL, format TEXT NOT NULL)".update
  books.insert(Book(1L, "Zionomicon", Format.Ebook))
  books.insert(Book(2L, "Programming in Scala", Format.Paperback))
  println(sql"SELECT title, format FROM books ORDER BY id".query[(String, String)])
  println(sql"SELECT title FROM books WHERE format = ${Format.Ebook}".query[String])
}
```

The codec never stores the ordinal, so reordering or inserting cases doesn't change what existing rows mean. Renaming a case does. When the stored values don't match the case names, for example in an existing table that uses lower case, `@Modifier.rename` sets the stored name of a case:

```scala mdoc:silent
enum LegacyFormat {
  @Modifier.rename("hardcover") case Hardcover
  @Modifier.rename("paperback") case Paperback
  @Modifier.rename("ebook") case Ebook
}

object LegacyFormat {
  given Schema[LegacyFormat] = Schema.derived
}

case class LegacyBook(id: Long, title: String, format: LegacyFormat)

object LegacyBook {
  given Schema[LegacyBook] = Schema.derived
}

val legacyBooks = Repo.derived[LegacyBook, Long]("legacy_books", "id", _.id)
```

The renamed case is written as `ebook` and read back as `LegacyFormat.Ebook`:

```scala mdoc
transactor.connect {
  sql"CREATE TABLE legacy_books (id INTEGER PRIMARY KEY, title TEXT NOT NULL, format TEXT NOT NULL)".update
  legacyBooks.insert(LegacyBook(1L, "Zionomicon", LegacyFormat.Ebook))
  println(sql"SELECT title, format FROM legacy_books".query[(String, String)])
  println(legacyBooks.all)
}
```

A value that matches no case fails the read with an `IllegalArgumentException` that names the value and the cases it expected:

```scala mdoc
transactor.connect {
  sql"INSERT INTO books (id, title, format) VALUES (3, 'Functional Programming in Scala', 'Audiobook')".update
  println(Try(books.all))
}
```

:::warning
Add a new case to the enum before any program writes its name to the table. Until then, every query that returns such a row fails.
:::

## Store a Nested Case Class as JSON

The `Metadata` record has three fields, and we want it in one column. The derived codec already does this: a field whose type is a case class is stored as one JSON column. The catch, in version 0.0.56, is that `Repo.derived` and `Table.derived` compute the table's columns separately from the codec, and they flatten the record into one column per field:

```scala mdoc:reset:silent
import zio.blocks.schema.Schema
import zio.blocks.sql.*
import scala.util.Try

case class Metadata(publisher: String, edition: Int, pages: Int)

object Metadata {
  given Schema[Metadata] = Schema.derived
}

case class Book(id: Long, title: String, metadata: Metadata)

object Book {
  given Schema[Book] = Schema.derived
}

// Page counts are illustrative
val zionomicon         = Book(1L, "Zionomicon", Metadata("Ziverge", 1, 700))
val programmingInScala = Book(2L, "Programming in Scala", Metadata("Artima", 1, 776))

val dbFile     = java.nio.file.Files.createTempFile("bookstore", ".db")
val transactor = JdbcTransactor.fromUrl(s"jdbc:sqlite:$dbFile", SqlDialect.SQLite)
```

The table and the codec disagree about the columns:

```scala mdoc
println(Table.derived[Book].columns)
println(DbCodec.derived[Book].columns)
```

A `Repo` from `Repo.derived` names five columns in its `INSERT` but has only three values to bind, so every insert fails:

```scala mdoc
transactor.connect {
  sql"""CREATE TABLE books (
    id       INTEGER PRIMARY KEY,
    title    TEXT NOT NULL,
    metadata TEXT NOT NULL
  )""".update
  println(Try(Repo.derived[Book, Long]("books", "id", _.id).insert(zionomicon)))
}
```

Until this is fixed, we build the `Table` from the codec's own columns and pass it to `Repo.apply`. `Table` takes a name, a codec and one `ColumnMeta` per column; the column type in a `ColumnMeta` is read only by `Table#createTable`, which we don't call:

```scala mdoc:silent
def tableFor[A](name: String, codec: DbCodec[A]): Table[A] =
  Table(name, codec, codec.columns.map(column => ColumnMeta(column, DbValue.DbString(""), nullable = false)))

val bookCodec: DbCodec[Book] = DbCodec.derived[Book]
val books                    = Repo(tableFor("books", bookCodec), "id", DbCodec[Long], (book: Book) => book.id)
```

Now the column holds a JSON document, and `Repo#all` decodes it back into `Metadata`. SQLite's `json_extract` reads a field inside the document, so we can filter on the publisher in our own SQL:

```scala mdoc
transactor.connect {
  books.insert(zionomicon)
  books.insert(programmingInScala)
  println(sql"SELECT title, metadata FROM books ORDER BY id".query[(String, String)])
  books.all.foreach(println)

  val publisher = "Artima"
  println(sql"SELECT id, title, metadata FROM books WHERE json_extract(metadata, '$$.publisher') = $publisher".query[Book])
}
```

The `$$` in `'$$.publisher'` is an escaped `$`, so SQLite receives the JSON path `$.publisher`. A document that doesn't match `Metadata` fails when the row is read, with the JSON error inside the message:

```scala mdoc
transactor.connect {
  sql"""INSERT INTO books (id, title, metadata) VALUES (3, 'Functional Programming in Scala', '{"publisher":"Manning"}')""".update
  println(Try(books.find(3L)))
}
```

:::tip
To store the record's fields as separate columns instead, annotate the field with `@Modifier.config("sql.inline", "true")`. The codec then writes `metadata_publisher`, `metadata_edition` and `metadata_pages`, the same columns `Table.derived` lists, and `Repo.derived` works unchanged.
:::

## Use a jsonb Column on PostgreSQL

On PostgreSQL, the natural column type for a JSON document is `jsonb`: it is stored parsed, so the server can index it and query inside it. The codec binds the JSON as a string parameter, and by default the PostgreSQL JDBC driver sends string parameters as `character varying`, which PostgreSQL refuses to put into a `jsonb` column without a cast. The driver's `stringtype=unspecified` URL setting sends them untyped instead, and PostgreSQL then converts the text to `jsonb` itself. The same `books` repository works on PostgreSQL once the URL has that setting:

```scala mdoc:compile-only
val baseUrl  = sys.env.getOrElse("BOOKSTORE_DB_URL", "jdbc:postgresql://localhost:5432/bookstore")
val user     = sys.env.getOrElse("BOOKSTORE_DB_USER", "bookstore")
val password = sys.env("BOOKSTORE_DB_PASSWORD")

// Without the setting: the insert fails
JdbcTransactor.fromUrl(baseUrl, user, password, SqlDialect.PostgreSQL).connect {
  sql"DROP TABLE IF EXISTS books".update
  sql"""CREATE TABLE books (
    id       BIGINT PRIMARY KEY,
    title    TEXT NOT NULL,
    metadata JSONB NOT NULL
  )""".update
  println(Try(books.insert(zionomicon)).fold(_.getMessage.linesIterator.next(), n => s"Inserted $n"))
}

// With the setting: the same insert works, and ->> reads a field inside the document
val jsonbUrl   = baseUrl + (if (baseUrl.contains("?")) "&" else "?") + "stringtype=unspecified"
val postgresTx = JdbcTransactor.fromUrl(jsonbUrl, user, password, SqlDialect.PostgreSQL)
postgresTx.connect {
  books.insert(zionomicon)
  books.insert(programmingInScala)
  println(sql"SELECT title, metadata::text FROM books ORDER BY id".query[(String, String)])
  books.all.foreach(println)

  val publisher = "Artima"
  println(sql"SELECT id, title, metadata FROM books WHERE metadata->>'publisher' = $publisher".query[Book])
}
```

Run against PostgreSQL 16 in Docker, it prints:

```text
ERROR: column "metadata" is of type jsonb but expression is of type character varying
List((Zionomicon,{"pages": 700, "edition": 1, "publisher": "Ziverge"}), (Programming in Scala,{"pages": 776, "edition": 1, "publisher": "Artima"}))
Book(1,Zionomicon,Metadata(Ziverge,1,700))
Book(2,Programming in Scala,Metadata(Artima,1,776))
List(Book(2,Programming in Scala,Metadata(Artima,1,776)))
```

PostgreSQL reorders the keys when it stores a `jsonb` document, which doesn't matter to the decoder. `stringtype=unspecified` applies to every string parameter on the connection, and PostgreSQL then infers each one's type from where it appears. If you'd rather keep the default, write the `INSERT` yourself with a cast, `${json}::jsonb`, instead of using `Repo#insert`.

## Store a List as a PostgreSQL Array

A `List[String]` field goes through the same JSON path as a nested record: the derived codec stores it as a JSON array in one column. `Table.derived` hits the same bug in a different way and throws when it meets the list, so we use `tableFor` again:

```scala mdoc:reset:silent
import zio.blocks.schema.Schema
import zio.blocks.typeid.TypeId
import zio.blocks.sql.*
import scala.util.Try

case class Book(id: Long, title: String, tags: List[String])

object Book {
  given Schema[Book] = Schema.derived
}

def tableFor[A](name: String, codec: DbCodec[A]): Table[A] =
  Table(name, codec, codec.columns.map(column => ColumnMeta(column, DbValue.DbString(""), nullable = false)))

val bookCodec: DbCodec[Book] = DbCodec.derived[Book]
val books                    = Repo(tableFor("books", bookCodec), "id", DbCodec[Long], (book: Book) => book.id)

val dbFile     = java.nio.file.Files.createTempFile("bookstore", ".db")
val transactor = JdbcTransactor.fromUrl(s"jdbc:sqlite:$dbFile", SqlDialect.SQLite)
```

On SQLite, the tags land in a `TEXT` column as JSON, and SQLite's `json_each` lets us filter on one tag:

```scala mdoc
println(Try(Table.derived[Book]))

transactor.connect {
  sql"CREATE TABLE books (id INTEGER PRIMARY KEY, title TEXT NOT NULL, tags TEXT NOT NULL)".update
  books.insert(Book(1L, "Zionomicon", List("zio", "effects", "concurrency")))
  books.insert(Book(2L, "Programming in Scala", List("scala", "fundamentals")))
  books.insert(Book(3L, "Functional Programming in Scala", List("scala", "functional-programming")))
  println(sql"SELECT title, tags FROM books ORDER BY id".query[(String, String)])

  val tag = "scala"
  println(
    sql"SELECT id, title, tags FROM books WHERE EXISTS (SELECT 1 FROM json_each(books.tags) WHERE value = $tag) ORDER BY id"
      .query[Book]
  )
}
```

PostgreSQL has real arrays. A `text[]` column holds the tags as SQL values, so `ANY` and the overlap operator `&&` work on it and a GIN index can speed them up. `PgCodec`, which is JVM-only, provides the codec: `PgCodec.listStringCodec` reads and writes a `List[String]` as a SQL array (there is also `PgCodec.iArrayStringCodec` for `IArray[String]`; strings are the only element type it reads).

The row codec gets each field's codec from the field's `Schema`, so a `DbCodec[List[String]]` in scope doesn't change how `tags` is stored. To swap one field's codec, `DbCodec.derivedWith` derives the row codec with overrides: `DerivationBuilder#instance` takes the record's `TypeId`, the field name and the codec to use for it:

```scala mdoc:silent
val pgBookCodec: DbCodec[Book] =
  DbCodec.derivedWith[Book](_.instance(TypeId.of[Book], "tags", PgCodec.listStringCodec))

val pgBooks = Repo(tableFor("books", pgBookCodec), "id", DbCodec[Long], (book: Book) => book.id)
```

`pgBooks` writes the tags as an array. In our own queries, `query[Book]` needs the same codec, so we make it the `given DbCodec[Book]` in that scope. To pass a Scala list as an array parameter, `import PgCodec.given` brings parameter instances for lists of `String`, `Int`, `Long`, `Double`, `Boolean` and `UUID`:

```scala mdoc:compile-only
val baseUrl  = sys.env.getOrElse("BOOKSTORE_DB_URL", "jdbc:postgresql://localhost:5432/bookstore")
val user     = sys.env.getOrElse("BOOKSTORE_DB_USER", "bookstore")
val password = sys.env("BOOKSTORE_DB_PASSWORD")

JdbcTransactor.fromUrl(baseUrl, user, password, SqlDialect.PostgreSQL).connect {
  given DbCodec[Book] = pgBookCodec

  sql"DROP TABLE IF EXISTS books".update
  sql"CREATE TABLE books (id BIGINT PRIMARY KEY, title TEXT NOT NULL, tags TEXT[])".update

  // The derived codec writes JSON text, which a text[] column refuses
  println(Try(books.insert(Book(1L, "Zionomicon", List("zio")))).fold(_.getMessage.linesIterator.next(), n => s"Inserted $n"))

  pgBooks.insert(Book(1L, "Zionomicon", List("zio", "effects", "concurrency")))
  pgBooks.insert(Book(2L, "Programming in Scala", List("scala", "fundamentals")))
  pgBooks.insert(Book(3L, "Functional Programming in Scala", List("scala", "functional-programming")))
  println(sql"SELECT title, tags::text FROM books ORDER BY id".query[(String, String)])

  val tag = "scala"
  println(sql"SELECT id, title, tags FROM books WHERE $tag = ANY(tags) ORDER BY id".query[Book])

  import PgCodec.given
  val wanted = List("effects", "functional-programming")
  println(
    Try(sql"SELECT title FROM books WHERE tags && $wanted ORDER BY id".query[String])
      .fold(_.getMessage.linesIterator.next(), _.toString)
  )
  println(sql"SELECT title FROM books WHERE tags && $wanted::text[] ORDER BY id".query[String])
}
```

Run against PostgreSQL 16 in Docker, it prints:

```text
ERROR: column "tags" is of type text[] but expression is of type character varying
List((Zionomicon,{zio,effects,concurrency}), (Programming in Scala,{scala,fundamentals}), (Functional Programming in Scala,{scala,functional-programming}))
List(Book(2,Programming in Scala,List(scala, fundamentals)), Book(3,Functional Programming in Scala,List(scala, functional-programming)))
ERROR: operator does not exist: text[] && character varying[]
List(Zionomicon, Functional Programming in Scala)
```

`PgCodec` binds a list of strings as a `varchar[]` array. Inserting it into a `text[]` column works, because PostgreSQL converts it on assignment, but an operator such as `&&` needs both sides to have the same type, so the parameter needs a `::text[]` cast. A list of numbers works with `ANY` as it is: `id = ANY($ids)` with `ids: List[Long]` binds a `bigint[]`.

## Store Timestamps Without Time-Zone Shifts

An `Instant` is a moment on the timeline, with no time zone. The `sql` module writes it with JDBC's `setTimestamp` and reads it with `getTimestamp`, both with a UTC calendar, so the JVM's default time zone never shifts the value. What can still go wrong is the column type, and the right one differs between SQLite and PostgreSQL.

### Use an INTEGER Column on SQLite

SQLite has no timestamp type, and its JDBC driver stores the timestamp as the number of milliseconds since 1970. We start a fresh scope with a `Book` that has a publication date:

```scala mdoc:reset:silent
import java.time.Instant
import zio.blocks.schema.Schema
import zio.blocks.sql.*
import scala.util.Try

case class Book(id: Long, title: String, publishedAt: Instant)

object Book {
  given Schema[Book] = Schema.derived
}

val books = Repo.derived[Book, Long]("books", "id", _.id)

// Illustrative date, with microseconds to show the precision SQLite keeps
val zionomicon = Book(1L, "Zionomicon", Instant.parse("2021-07-15T09:30:00.123456Z"))

val dbFile     = java.nio.file.Files.createTempFile("bookstore", ".db")
val transactor = JdbcTransactor.fromUrl(s"jdbc:sqlite:$dbFile", SqlDialect.SQLite)
```

`Table#createTable` declares the column as `TEXT`. SQLite then stores the milliseconds as text, and the driver can't parse them back, so the row is written but can't be read:

```scala mdoc
println(books.table.createTable(SqlDialect.SQLite).sql(SqlDialect.SQLite))

transactor.connect {
  sql"CREATE TABLE books (id INTEGER PRIMARY KEY, title TEXT NOT NULL, published_at TEXT NOT NULL)".update
  books.insert(zionomicon)
  println(sql"SELECT published_at, typeof(published_at) FROM books".query[(String, String)])
  println(Try(books.all))
}
```

We replace the table with one whose column is `INTEGER`. It stores the same number as a number, and it reads back. Comparisons in SQL work too, because an `Instant` parameter is bound the same way:

```scala mdoc
transactor.connect {
  sql"DROP TABLE books".update
  sql"CREATE TABLE books (id INTEGER PRIMARY KEY, title TEXT NOT NULL, published_at INTEGER NOT NULL)".update
  books.insert(zionomicon)
  println(sql"SELECT published_at, typeof(published_at) FROM books".query[(Long, String)])
  println(books.all)

  val cutoff = Instant.parse("2020-01-01T00:00:00Z")
  println(sql"SELECT title FROM books WHERE published_at >= $cutoff".query[String])
}
```

The value comes back as `09:30:00.123Z`: SQLite keeps milliseconds, so the microseconds are gone. If you need finer precision on SQLite, store the value yourself, for example as a `Long` of microseconds.

### Use a timestamptz Column on PostgreSQL

PostgreSQL has two timestamp types. `timestamptz` (`timestamp with time zone`) stores a moment, like `Instant`. `timestamp` (`timestamp without time zone`) stores a date and a clock time with no zone. Our own `Repo` round-trips an `Instant` through either one, because it always writes and reads UTC clock times. The trouble starts when anything else writes to a `timestamp` column: PostgreSQL converts a moment to the clock time of that session's time zone, and our UTC reader then gets a different moment.

To see it, we store the same `Instant` in one column of each type, then let plain SQL write the same moment into both from a session that runs in Tehran:

```scala mdoc:compile-only
case class Release(id: Long, withZone: Instant, withoutZone: Instant)

object Release {
  given Schema[Release] = Schema.derived
}

val releases = Repo.derived[Release, Long]("releases", "id", _.id)

val baseUrl  = sys.env.getOrElse("BOOKSTORE_DB_URL", "jdbc:postgresql://localhost:5432/bookstore")
val user     = sys.env.getOrElse("BOOKSTORE_DB_USER", "bookstore")
val password = sys.env("BOOKSTORE_DB_PASSWORD")

// Illustrative date
val publishedAt = Instant.parse("2021-07-15T09:30:00.123456Z")

JdbcTransactor.fromUrl(baseUrl, user, password, SqlDialect.PostgreSQL).connect {
  sql"DROP TABLE IF EXISTS releases".update
  sql"""CREATE TABLE releases (
    id           BIGINT PRIMARY KEY,
    with_zone    TIMESTAMPTZ NOT NULL,
    without_zone TIMESTAMP NOT NULL
  )""".update

  // Written by our Repo
  releases.insert(Release(1L, publishedAt, publishedAt))

  // Written by another client whose session runs in Tehran
  sql"SET TIME ZONE 'Asia/Tehran'".update
  sql"""INSERT INTO releases VALUES (2,
    TIMESTAMPTZ '2021-07-15 09:30:00.123456+00',
    TIMESTAMPTZ '2021-07-15 09:30:00.123456+00')""".update
  sql"SET TIME ZONE 'UTC'".update

  println(sql"SELECT id, with_zone::text, without_zone::text FROM releases ORDER BY id".query[(Long, String, String)])
  releases.all.foreach(println)
}
```

Run against PostgreSQL 16 in Docker, it prints:

```text
List((1,2021-07-15 09:30:00.123456+00,2021-07-15 09:30:00.123456), (2,2021-07-15 09:30:00.123456+00,2021-07-15 14:00:00.123456))
Release(1,2021-07-15T09:30:00.123456Z,2021-07-15T09:30:00.123456Z)
Release(2,2021-07-15T09:30:00.123456Z,2021-07-15T14:00:00.123456Z)
```

Row 2's `timestamp` column holds 14:00, the clock time in Tehran (UTC+04:30 in July 2021), and we read it back as `14:00:00Z`, four and a half hours late. The `timestamptz` column gives the right moment in both rows. The same shift happens with a column default such as `DEFAULT now()`, or with a client whose session time zone isn't UTC. Use `timestamptz` for every `Instant`; it is also the type `Table#createTable` picks for one on PostgreSQL. PostgreSQL keeps microseconds, so the value round-trips exactly.

:::tip
If a field is a date and clock time with no zone, such as a store's opening time, model it as `java.time.LocalDateTime` with a `timestamp` column. The `sql` module writes a `LocalDateTime` without any time-zone conversion.
:::

## Make a Custom Column Nullable

A nullable column needs an `Option` field. A non-optional field doesn't fail on SQL `NULL`: a `String` reads it as `null` and an `Int` as `0`, so a `NULL` turns into a value that looks real:

```scala mdoc:reset:silent
import zio.blocks.schema.{Schema, SchemaError}
import zio.blocks.schema.json.{JsonCodec, JsonFormat}
import zio.blocks.typeid.TypeId
import zio.blocks.sql.*
import scala.util.Try

val dbFile     = java.nio.file.Files.createTempFile("bookstore", ".db")
val transactor = JdbcTransactor.fromUrl(s"jdbc:sqlite:$dbFile", SqlDialect.SQLite)
```

Reading two `NULL`s into a `String` and an `Int`:

```scala mdoc
transactor.connect {
  println(sql"SELECT CAST(NULL AS TEXT), CAST(NULL AS INTEGER)".query[(String, Int)])
}
```

`Option` fields of plain types, such as `Option[String]`, read `NULL` as `None`. Two custom types need more care in version 0.0.56. We bring back the `Isbn` newtype and the `Metadata` record. `Metadata` also gets a `JsonCodec`, for the JSON codec we use further down:

```scala mdoc:silent
final case class Isbn private (value: String)

object Isbn {
  def parse(input: String): Either[SchemaError, Isbn] = {
    val digits = input.filterNot(c => c == '-' || c == ' ')
    if (digits.length != 13 || !digits.forall(_.isDigit))
      Left(SchemaError.validationFailed(s"ISBN must have 13 digits, got '$input'"))
    else {
      val weighted = digits.init.zipWithIndex.map { case (c, i) => c.asDigit * (if (i % 2 == 0) 1 else 3) }
      if ((10 - weighted.sum % 10) % 10 == digits.last.asDigit) Right(new Isbn(digits))
      else Left(SchemaError.validationFailed(s"ISBN check digit is wrong in '$input'"))
    }
  }

  def unsafe(input: String): Isbn = parse(input).fold(e => throw e, identity)

  given Schema[Isbn] = Schema[String].transform(unsafe, _.value)
}

case class Metadata(publisher: String, edition: Int, pages: Int)

object Metadata {
  given Schema[Metadata]    = Schema.derived
  given JsonCodec[Metadata] = Schema[Metadata].derive(JsonFormat)
}

case class Book(id: Long, title: String, isbn: Option[Isbn], metadata: Option[Metadata])

object Book {
  given Schema[Book] = Schema.derived
}

case class ShelfBook(id: Long, title: String, isbn: Option[Isbn])

object ShelfBook {
  given Schema[ShelfBook] = Schema.derived
}
```

The derived codec rejects `Option[Metadata]` outright, because an `Option` field must map to one column and it treats the optional record as three. For `Option[Isbn]`, a known bug in 0.0.56 makes the codec read the column before it checks for `NULL`, so a `NULL` reaches `Isbn.unsafe` as `null` and the read throws. An `Option` of an enum fails the same way, with `Unknown enum variant 'null'`:

```scala mdoc
println(Try(DbCodec.derived[Book]).fold(_.getMessage, _ => "derived"))

transactor.connect {
  sql"""CREATE TABLE books (
    id       INTEGER PRIMARY KEY,
    title    TEXT NOT NULL,
    isbn     TEXT,
    metadata TEXT
  )""".update
  sql"INSERT INTO books (id, title, isbn) VALUES (1, 'Zionomicon', NULL)".update
  println(Try(sql"SELECT id, title, isbn FROM books".query[ShelfBook]))
}
```

Both fields get a codec of their own through `DbCodec.derivedWith`. For the ISBN, `DbCodec#transform` builds a codec on top of `DbCodec[Option[String]]`, which handles `NULL` correctly, so `Isbn.unsafe` only ever sees a real string. For the metadata, `DbCodec.jsonbOption` stores `Some` as JSON and `None` as `NULL`, using the `JsonCodec` we gave `Metadata`:

```scala mdoc:silent
def tableFor[A](name: String, codec: DbCodec[A]): Table[A] =
  Table(name, codec, codec.columns.map(column => ColumnMeta(column, DbValue.DbString(""), nullable = false)))

val optionalIsbn: DbCodec[Option[Isbn]] =
  DbCodec[Option[String]].transform(_.map(Isbn.unsafe))(_.map(_.value))

val bookCodec: DbCodec[Book] =
  DbCodec.derivedWith[Book](
    _.instance(TypeId.of[Book], "isbn", optionalIsbn)
      .instance(TypeId.of[Book], "metadata", DbCodec.jsonbOption[Metadata])
  )

val books = Repo(tableFor("books", bookCodec), "id", DbCodec[Long], (book: Book) => book.id)
```

Zionomicon has no ISBN in our records and no metadata yet, and both come back as `None`:

```scala mdoc
transactor.connect {
  sql"DELETE FROM books".update
  // Page counts are illustrative
  books.insert(Book(1L, "Zionomicon", None, None))
  books.insert(Book(2L, "Programming in Scala", Some(Isbn.unsafe("978-0-9815316-0-1")), Some(Metadata("Artima", 1, 776))))
  println(sql"SELECT title, isbn, metadata FROM books ORDER BY id".query[(String, Option[String], Option[String])])
  books.all.foreach(println)
}
```

:::warning
`PgCodec.listStringCodec` reads a `NULL` array as a `null` list, not as an empty one. Declare array columns `NOT NULL`, or check for `null` after reading.
:::

## Putting It Together

The complete example puts every column type from this guide into one `books` table on SQLite: the opaque `BookId`, the optional `Isbn`, the `Format` enum, `Metadata` as JSON, the tags as a JSON array and the publication date as milliseconds. It inserts three books, checks that they read back equal to what went in, and runs one query per column type:

```scala mdoc:embed:sql-examples/src/main/scala/mapcustomcolumntypes/CompleteExample.scala
```

On PostgreSQL, three things change: the `CREATE TABLE` statement uses `JSONB`, `TEXT[]` and `TIMESTAMPTZ`; the row codec also overrides `tags` with `PgCodec.listStringCodec`; and the JDBC URL has `stringtype=unspecified`. `CompletePostgresExample.scala`, listed in the next section, is the same program with those changes.

## Running the Examples

All examples in this guide have corresponding runnable Scala files in the `sql-examples` project. To get them, clone the repository and change into the examples project:

```bash
git clone https://github.com/zio/zio-blocks.git
cd zio-blocks/sql-examples
```

To check that every example compiles without running any of them, use `sbt compile` instead of `sbt "runMain ..."`:

```bash
sbt compile
```

The SQLite examples each create their own temporary database file, so you can run them in any order. The PostgreSQL examples need the server from the [Prerequisites](#prerequisites) and the `BOOKSTORE_DB_PASSWORD` environment variable; set `BOOKSTORE_DB_URL` and `BOOKSTORE_DB_USER` too if they differ from `jdbc:postgresql://localhost:5432/bookstore` and `bookstore`. Each PostgreSQL example drops the tables it creates.

### Store a Newtype or an Opaque Type in One Column

This example shows the `isbn_value` column a derived newtype `Schema` produces, then stores a validated `Isbn` and an opaque `BookId` as plain columns and reads back a row with a bad check digit.

<details>
  <summary>sql-examples/src/main/scala/mapcustomcolumntypes/Step1NewtypeExample.scala</summary>

```scala mdoc:embed:sql-examples/src/main/scala/mapcustomcolumntypes/Step1NewtypeExample.scala:show-line-numbers
```

</details>

```bash
sbt "runMain mapcustomcolumntypes.Step1NewtypeExample"
```

### Store an Enum as Its Case Name

This example stores `Format` by case name, renames the stored values of `LegacyFormat` with `@Modifier.rename`, and reads back a value that matches no case.

<details>
  <summary>sql-examples/src/main/scala/mapcustomcolumntypes/Step2EnumExample.scala</summary>

```scala mdoc:embed:sql-examples/src/main/scala/mapcustomcolumntypes/Step2EnumExample.scala:show-line-numbers
```

</details>

```bash
sbt "runMain mapcustomcolumntypes.Step2EnumExample"
```

### Store a Nested Case Class as JSON on SQLite

This example shows `Repo.derived` failing on the `Metadata` field, then stores it as JSON through a `Repo` built from the codec's columns and filters with `json_extract`.

<details>
  <summary>sql-examples/src/main/scala/mapcustomcolumntypes/Step3JsonColumnExample.scala</summary>

```scala mdoc:embed:sql-examples/src/main/scala/mapcustomcolumntypes/Step3JsonColumnExample.scala:show-line-numbers
```

</details>

```bash
sbt "runMain mapcustomcolumntypes.Step3JsonColumnExample"
```

### Use a jsonb Column on PostgreSQL

This example inserts into a `jsonb` column without and with `stringtype=unspecified` and filters with `->>`. It needs PostgreSQL.

<details>
  <summary>sql-examples/src/main/scala/mapcustomcolumntypes/Step4JsonbPostgresExample.scala</summary>

```scala mdoc:embed:sql-examples/src/main/scala/mapcustomcolumntypes/Step4JsonbPostgresExample.scala:show-line-numbers
```

</details>

```bash
sbt "runMain mapcustomcolumntypes.Step4JsonbPostgresExample"
```

### Store a List as JSON on SQLite

This example shows `Table.derived` throwing on a `List[String]` field, then stores the tags as a JSON array and filters with `json_each`.

<details>
  <summary>sql-examples/src/main/scala/mapcustomcolumntypes/Step5ListColumnExample.scala</summary>

```scala mdoc:embed:sql-examples/src/main/scala/mapcustomcolumntypes/Step5ListColumnExample.scala:show-line-numbers
```

</details>

```bash
sbt "runMain mapcustomcolumntypes.Step5ListColumnExample"
```

### Store a List as a PostgreSQL Array

This example overrides the `tags` codec with `PgCodec.listStringCodec`, filters with `ANY` and `&&`, and reads back an empty array and a `NULL` one. It needs PostgreSQL.

<details>
  <summary>sql-examples/src/main/scala/mapcustomcolumntypes/Step6ArrayPostgresExample.scala</summary>

```scala mdoc:embed:sql-examples/src/main/scala/mapcustomcolumntypes/Step6ArrayPostgresExample.scala:show-line-numbers
```

</details>

```bash
sbt "runMain mapcustomcolumntypes.Step6ArrayPostgresExample"
```

### Use an INTEGER Column for Timestamps on SQLite

This example stores an `Instant` in a `TEXT` column, which can't be read back, and then in an `INTEGER` column, which can.

<details>
  <summary>sql-examples/src/main/scala/mapcustomcolumntypes/Step7InstantSqliteExample.scala</summary>

```scala mdoc:embed:sql-examples/src/main/scala/mapcustomcolumntypes/Step7InstantSqliteExample.scala:show-line-numbers
```

</details>

```bash
sbt "runMain mapcustomcolumntypes.Step7InstantSqliteExample"
```

### Use a timestamptz Column on PostgreSQL

This example writes the same moment to a `timestamptz` and a `timestamp` column, from our `Repo` and from a session in another time zone. It needs PostgreSQL.

<details>
  <summary>sql-examples/src/main/scala/mapcustomcolumntypes/Step8InstantPostgresExample.scala</summary>

```scala mdoc:embed:sql-examples/src/main/scala/mapcustomcolumntypes/Step8InstantPostgresExample.scala:show-line-numbers
```

</details>

```bash
sbt "runMain mapcustomcolumntypes.Step8InstantPostgresExample"
```

### Make a Custom Column Nullable

This example reads `NULL` into non-optional fields, shows the two `Option` fields that fail, and fixes both with `DbCodec.derivedWith`.

<details>
  <summary>sql-examples/src/main/scala/mapcustomcolumntypes/Step9NullableExample.scala</summary>

```scala mdoc:embed:sql-examples/src/main/scala/mapcustomcolumntypes/Step9NullableExample.scala:show-line-numbers
```

</details>

```bash
sbt "runMain mapcustomcolumntypes.Step9NullableExample"
```

### The Complete Example on SQLite

This example puts every column type from this guide into one `books` table on SQLite, inserts three books, checks that they read back equal and runs one query per column type.

<details>
  <summary>sql-examples/src/main/scala/mapcustomcolumntypes/CompleteExample.scala</summary>

```scala mdoc:embed:sql-examples/src/main/scala/mapcustomcolumntypes/CompleteExample.scala:show-line-numbers
```

</details>

```bash
sbt "runMain mapcustomcolumntypes.CompleteExample"
```

### The Complete Example on PostgreSQL

This example is the complete example with a `jsonb`, a `text[]` and a `timestamptz` column. It needs PostgreSQL.

<details>
  <summary>sql-examples/src/main/scala/mapcustomcolumntypes/CompletePostgresExample.scala</summary>

```scala mdoc:embed:sql-examples/src/main/scala/mapcustomcolumntypes/CompletePostgresExample.scala:show-line-numbers
```

</details>

```bash
sbt "runMain mapcustomcolumntypes.CompletePostgresExample"
```

## Going Further

- [DbCodec](../reference/sql/db-codec.md) lists every built-in codec and the `DbCodec#transform`, `DbCodec.jsonb` and `DbCodec.derivedWith` constructors in full.
- [DbCodecDeriver](../reference/sql/db-codec-deriver.md) describes how the row codec maps each kind of field, including the `sql.inline` and `sql.inline_fields` settings.
- [SqlDialect](../reference/sql/sql-dialect.md) shows the column type `Table#createTable` picks for each value on SQLite and PostgreSQL.
- [How to Test Database Code Against SQLite](./test-database-code-against-sqlite.md) runs code like this in tests, with a fresh database per test.
- [How to Define Validated Newtypes](./validated-newtypes.md) collects every validation error of a record at once, instead of stopping at the first.
