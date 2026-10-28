---
id: csv
title: "CSV Codec Module"
---

import Tabs from '@theme/Tabs';
import TabItem from '@theme/TabItem';

`zio-blocks-schema-csv` is a **schema-driven CSV codec module** for serializing and deserializing Scala types to and from CSV format. It provides RFC 4180-compliant parsing and generation with zero dependencies and support for 27 primitive types plus flat record (case class) types. Core types: `CsvCodec`, `CsvConfig`, `CsvError`, `CsvReader`, `CsvWriter`, `CsvFormat`.

See also the [How to Read and Write CSV and TSV Files](../../../guides/read-and-write-csv-and-tsv-files.md) guide, which reads files by header name, reports every bad row, writes TSV through `CsvReader` and `CsvWriter`, and processes large files one row at a time.

The main public API is `CsvCodec[A]`, which extends `TextCodec[A]` and provides CSV-specific header support:

```scala mdoc:compile-only
import java.nio.CharBuffer
import zio.blocks.schema.SchemaError

// API signature (conceptual - simplified for clarity)
trait CsvCodec[A] {
  def headerNames: IndexedSeq[String]
  def encode(value: A, output: CharBuffer): Unit
  def decode(input: CharBuffer): Either[SchemaError, A]
}
```

## Motivation

CSV is the de facto standard for tabular data exchange across systems, but parsing and serialization are often error-prone when done manually. `zio-blocks-schema-csv` eliminates this friction by deriving codec instances directly from your Scala types using ZIO Schema. You describe your data shape once, and the module handles:
- RFC 4180-compliant parsing and generation with proper quote escaping
- Error messages that quote the bad value, and parse errors that carry a row and column, with the limits described in [CsvError](#csverror)
- Zero-overhead errors (no stack traces) optimized for CSV stream processing
- Cross-platform support (JVM and Scala.js)

Rather than writing custom parsers or relying on string-based configuration, you work with strongly-typed schemas that the compiler validates.

## Installation

Add the module to your `build.sbt`:

```sbt
libraryDependencies += "dev.zio" %% "zio-blocks-schema-csv" % "@VERSION@"
```

For Scala.js, use `%%%` instead of `%%`:

```sbt
libraryDependencies += "dev.zio" %%% "zio-blocks-schema-csv" % "@VERSION@"
```

Supported Scala versions: 2.13.x and 3.x

## Introduction

The module provides a complete pipeline for CSV codec derivation and usage:

1. **Define your type** — Any Scala type with a `Schema` instance
2. **Derive a codec** — Use `CsvFormat` to obtain a `CsvCodec[A]`
3. **Parse or serialize** — Call `CsvCodec#decode` or `CsvCodec#encode`
4. **Handle errors** — Catch `CsvError` for malformed input, and `SchemaError` for a cell that doesn't convert to its field's type

The derivation process is automatic for supported types (all 27 primitives and flat records). Unsupported types (sealed traits, `Option` fields, sequences, maps) are rejected at derivation time, when `Schema#derive` runs, with an `UnsupportedOperationException`.

## How They Work Together

The CSV codec pipeline flows through these layers:

```
1. User defines Schema[A] for their type
                 ↓
2. Schema.derive(CsvFormat) creates CsvCodec[A]
                 ↓
3. CsvCodecDeriver converts Schema to codec implementation
   - For primitives: type-specific encoders/decoders
   - For records: field-by-field composition
                 ↓
4. CsvCodec#encode writes values to CSV rows (via CsvWriter)
   CsvCodec#decode reads CSV rows to values (via CsvReader)
                 ↓
5. CsvReader/CsvWriter handle RFC 4180 quote escaping
   CsvConfig customizes delimiters, terminators, quoting
                 ↓
6. CsvError reports ParseError for malformed input; CsvCodec#decode reports a bad cell as SchemaError
```

**Typical workflow:**

A user type flows through the derivation and encoding pipeline as follows:

```
User type (e.g., case class Person)
    ↓
Schema.derived (automatic via macro)
    ↓
.derive(CsvFormat) → CsvCodec[Person]
    ↓
Use codec.headerNames for column names
    ↓
Use codec.encode(person, buffer) to serialize
Use codec.decode(buffer) to deserialize
    ↓
Handle CsvError.ParseError or SchemaError on failure
```

## Common Patterns

This section shows 4 practical patterns for working with CSV codecs in real-world scenarios.

### Pattern 1: Derive and Use a Simple Codec

To derive and use a CSV codec for a record type:

```scala
import zio.blocks.schema._
import zio.blocks.schema.csv._
import java.nio.CharBuffer

case class Person(name: String, age: Int, email: String)

object Person {
  implicit val schema: Schema[Person] = Schema.derived
}

val codec = Person.schema.derive(CsvFormat)
val person = Person("Alice", 30, "alice@example.com")
val buffer = CharBuffer.allocate(256)
codec.encode(person, buffer)
buffer.flip()
val encoded = buffer.toString
```

### Pattern 2: Customize CSV Format with CsvConfig

A codec derived with `Schema#derive(CsvFormat)` always uses `CsvConfig.default`: a comma between fields, double quotes and a `\r\n` line ending. `CsvFormat` takes no configuration. When you need tab-separated values (TSV) or a different delimiter, pass a `CsvConfig` to `CsvWriter` and `CsvReader`, which work on rows of field strings.

To write and read a tab-separated document:

```scala mdoc:compile-only
import zio.blocks.schema.csv._

val tsv: String = CsvWriter.writeAll(
  IndexedSeq("id", "value"),
  List(IndexedSeq("1", "first"), IndexedSeq("2", "second")),
  CsvConfig.tsv
)

val parsed: Either[CsvError, (IndexedSeq[String], IndexedSeq[IndexedSeq[String]])] =
  CsvReader.readAll(tsv, CsvConfig.tsv)
```

### Pattern 3: Report Bad Rows by Number

A decode error names the bad value, so a file with several bad rows can report each one. `CsvCodec#decode` decodes one row, not the header, so the example reads the document with `CsvReader.readAll` and decodes each row, counting the header as row 1:

```scala mdoc:silent:reset
import zio.blocks.schema._
import zio.blocks.schema.csv._
import java.nio.CharBuffer

case class Employee(id: Int, name: String, salary: BigDecimal)

object Employee {
  implicit val schema: Schema[Employee] = Schema.derived
}

val codec   = Employee.schema.derive(CsvFormat)
val csvData = "id,name,salary\n1,Alice,50000\n2,Bob,invalid\n"
```

The example joins each row's cells back into a line, which is safe here because no cell contains a comma:

```scala mdoc
CsvReader.readAll(csvData, CsvConfig.default) match {
  case Left(error) => println(s"Parse error: ${error.getMessage}")
  case Right((_, rows)) =>
    rows.zipWithIndex.foreach { case (cells, index) =>
      codec.decode(CharBuffer.wrap(cells.mkString(","))) match {
        case Right(employee) => println(s"Row ${index + 2}: $employee")
        case Left(error)     => println(s"Row ${index + 2}: ${error.getMessage}")
      }
    }
}
```

The message for row 3 quotes `invalid`, but it does not name the `salary` column. A known bug in 0.0.56: the decode error ends in the bare path `.`, so the column is missing from the message.

### Pattern 4: Working with Headers

Access the derived header names and use them to construct CSV output or validate input.

To get the column headers for a record type:

```scala
import zio.blocks.schema._
import zio.blocks.schema.csv._

case class Product(sku: String, name: String, price: Double)

object Product {
  implicit val schema: Schema[Product] = Schema.derived
}

val codec = Product.schema.derive(CsvFormat)
val headers = codec.headerNames
// headers: IndexedSeq[String] = Vector("sku", "name", "price")
```

## CsvCodec[A]

Abstract codec for encoding and decoding values to and from CSV format. Extends `TextCodec[A]` and provides CSV-specific header support.

### Construction

Codecs are derived automatically from `Schema[A]` using `CsvFormat`:

```scala
import zio.blocks.schema._
import zio.blocks.schema.csv._

case class User(id: Int, username: String)

object User {
  implicit val schema: Schema[User] = Schema.derived
}

val codec: CsvCodec[User] = User.schema.derive(CsvFormat)
```

### Header Names

To access the CSV column headers derived from a record type:

```scala
import zio.blocks.schema._
import zio.blocks.schema.csv._

case class Book(title: String, author: String, isbn: String)

object Book {
  implicit val schema: Schema[Book] = Schema.derived
}

val codec = Book.schema.derive(CsvFormat)
val headers: IndexedSeq[String] = codec.headerNames
```

### Encoding Values

To serialize a value to CSV format in a `CharBuffer`:

```scala
import zio.blocks.schema._
import zio.blocks.schema.csv._
import java.nio.CharBuffer

case class Item(name: String, quantity: Int)

object Item {
  implicit val schema: Schema[Item] = Schema.derived
}

val codec = Item.schema.derive(CsvFormat)
val item = Item("Widget", 42)
val buffer = CharBuffer.allocate(512)
codec.encode(item, buffer)
buffer.flip()
val csvLine = buffer.toString
```

### Decoding Values

To parse one CSV row from a `CharBuffer` into a value, with its cells in the order of `CsvCodec#headerNames`, and without the header line:

```scala mdoc:silent:reset
import zio.blocks.schema._
import zio.blocks.schema.csv._
import java.nio.CharBuffer

case class Sales(product: String, amount: BigDecimal)

object Sales {
  implicit val schema: Schema[Sales] = Schema.derived
}

val codec = Sales.schema.derive(CsvFormat)
```

```scala mdoc
println(codec.headerNames)
println(codec.decode(CharBuffer.wrap("Widget,99.99\r\n")))
```

---

## CsvConfig

Configuration for CSV parsing and generation, controlling delimiters, quoting, line termination, and header handling.

### Overview

`CsvConfig` is a case class with sensible RFC 4180 defaults. Customize it when you need different delimiters (e.g., tabs), custom line endings, or different quoting behavior. A custom `CsvConfig` applies to `CsvReader` and `CsvWriter`; codecs derived with `CsvFormat` always use `CsvConfig.default`.

### Defaults and Presets

The standard RFC 4180 CSV format:

```scala
import zio.blocks.schema.csv._

val config: CsvConfig = CsvConfig.default
// CsvConfig(',', '"', "\r\n", hasHeader = true, nullValue = "")
```

A tab-separated values (TSV) preset with tabs as delimiters:

```scala
import zio.blocks.schema.csv._

val tsvConfig: CsvConfig = CsvConfig.tsv
// CsvConfig('\t', '"', "\r\n", hasHeader = true, nullValue = "")
```

### Configuration Fields

- **`delimiter`** (default: `','`) — Character separating fields in a row
- **`quoteChar`** (default: `'"'`) — Character to quote fields containing delimiters or newlines; escapes quotes within quoted fields by doubling
- **`lineTerminator`** (default: `"\r\n"`) — Sequence terminating each row (RFC 4180 standard is CRLF)
- **`hasHeader`** (default: `true`) — Whether the first row contains column headers
- **`nullValue`** (default: `""`) — String representation for null values. A known bug in 0.0.56: no code in the module reads this field, so changing it has no effect on `CsvReader`, `CsvWriter` or the derived codecs.

### Custom Configuration

To use a pipe-delimited format:

```scala mdoc:compile-only
import zio.blocks.schema.csv._

val customConfig = CsvConfig(
  delimiter = '|',
  quoteChar = '"',
  lineTerminator = "\n",
  hasHeader = true
)
```

---

## CsvError

Sealed abstract class representing errors during CSV parsing and type conversion. Designed for high-performance CSV processing with zero-overhead exceptions (no stack traces).

### Error Hierarchy

**ParseError** — Occurs when CSV format is invalid. It carries a `row` and a `column`, both 1-based. A known bug in 0.0.56: `CsvReader.readAll` counts the row from the start of the row that failed, not from the start of the document, so a stray character on line 4 of a file is reported as row 1:

```scala mdoc:compile-only
import zio.blocks.schema.csv._

val parseError = CsvError.ParseError("Unclosed quoted field", row = 2, column = 15)
```

**TypeError** — Defined for a field that cannot be converted to the expected type, but no code in 0.0.56 constructs it. A cell that doesn't convert decodes to a `SchemaError` from `CsvCodec#decode`, which does not name the column (see [Pattern 3](#pattern-3-report-bad-rows-by-number)). Do not match on `CsvError.TypeError` in your code.

### Error Information

`ParseError` exposes its message, row and column:

```scala mdoc:compile-only
import zio.blocks.schema.csv._

val error = CsvError.ParseError("Unexpected character after closing quote", row = 1, column = 10)
val message: String = error.getMessage
val row: Int = error.row
val column: Int = error.column
```

---

## CsvReader

Low-level CSV row parser implementing RFC 4180 with a state machine approach. Provides stateless utility methods for parsing individual rows, headers, and complete documents.

### Parsing a Single Row

To parse one CSV row starting at a given offset in the input:

```scala
import zio.blocks.schema.csv._

val input = "Alice,30,alice@example.com\nBob,25,bob@example.com\n"
val config = CsvConfig.default

val result: Either[CsvError, (IndexedSeq[String], Int)] =
  CsvReader.readRow(input, offset = 0, config)

result match {
  case Right((fields, nextOffset)) =>
    println(s"Fields: $fields")
    println(s"Next offset: $nextOffset")
  case Left(err) =>
    println(s"Parse error: ${err.getMessage}")
}
```

### Parsing the Header Row

To parse just the first row as column headers:

```scala
import zio.blocks.schema.csv._

val input = "name,age,email\nAlice,30,alice@example.com\n"
val config = CsvConfig.default

val result: Either[CsvError, (IndexedSeq[String], Int)] =
  CsvReader.readHeader(input, config)
```

### Parsing Complete CSV

To parse a complete CSV document (header and all data rows):

```scala
import zio.blocks.schema.csv._

val csv = "name,age\nAlice,30\nBob,25\n"
val config = CsvConfig.default

val result: Either[CsvError, (IndexedSeq[String], IndexedSeq[IndexedSeq[String]])] =
  CsvReader.readAll(csv, config)

result match {
  case Right((header, rows)) =>
    println(s"Header: $header")
    rows.foreach(row => println(s"Row: $row"))
  case Left(err) =>
    println(s"Parse error: ${err.getMessage}")
}
```

---

## CsvWriter

Low-level CSV row serializer implementing RFC 4180 with proper field escaping.

### Writing a Single Row

To serialize one row of fields to CSV format:

```scala
import zio.blocks.schema.csv._

val fields: IndexedSeq[String] = Vector("Alice", "30", "alice@example.com")
val config = CsvConfig.default

val csvLine: String = CsvWriter.writeRow(fields, config)
// Result: "Alice,30,alice@example.com\r\n"
```

### Writing Headers

To write a header row (semantically identical to `CsvWriter#writeRow` but communicates intent):

```scala
import zio.blocks.schema.csv._

val columnNames: IndexedSeq[String] = Vector("name", "age", "email")
val config = CsvConfig.default

val csvHeader: String = CsvWriter.writeHeader(columnNames, config)
// Result: "name,age,email\r\n"
```

### Writing Complete CSV

To write a complete CSV document with headers and data rows:

```scala
import zio.blocks.schema.csv._

val header: IndexedSeq[String] = Vector("product", "quantity")
val rows: Seq[IndexedSeq[String]] = Seq(
  Vector("Widget", "10"),
  Vector("Gadget", "5"),
  Vector("Gizmo", "8")
)
val config = CsvConfig.default

val csv: String = CsvWriter.writeAll(header, rows, config)
```

### RFC 4180 Escaping

`CsvWriter` automatically escapes fields according to RFC 4180:
- Fields containing the delimiter, quote character, or newlines are wrapped in quotes
- Quote characters within quoted fields are doubled (e.g., `Alice "The Expert" Smith` becomes `"Alice ""The Expert"" Smith"`)
- Fields not requiring escaping are written as-is for readability

---

## CsvCodecDeriver

Implements schema-driven derivation of `CsvCodec[A]` instances. Automatically handles 27 primitive types and flat record types, rejecting unsupported types (sealed traits, `Option` fields, sequences, maps) with clear error messages.

### Primitive Type Support

All 27 ZIO Schema primitives are supported:
- Numeric: `Byte`, `Short`, `Int`, `Long`, `Float`, `Double`, `BigInt`, `BigDecimal`
- Logical: `Boolean`, `Char`, `String`
- Temporal: `Instant`, `LocalDate`, `LocalDateTime`, `LocalTime`, `Duration`, `Period`, `Year`, `YearMonth`, `MonthDay`, `Month`, `DayOfWeek`, `ZonedDateTime`, `OffsetDateTime`, `OffsetTime`, `ZoneId`, `ZoneOffset`
- Special: `UUID`, `Currency`, `Unit`

### Record Type Support

Flat case classes (records with no variant or sequence fields) are supported. Each field becomes a CSV column with the field name as the header:

```scala mdoc:silent:reset
import zio.blocks.schema._
import zio.blocks.schema.csv._

case class Address(street: String, city: String, zip: String)

object Address {
  implicit val schema: Schema[Address] = Schema.derived
}

val codec = Address.schema.derive(CsvFormat)
```

```scala mdoc
println(codec.headerNames)
```

A field whose type is another case class is not rejected, but it does not become separate columns either. A known bug in 0.0.56: the deriver writes the nested record as one cell, including its `\r\n` line terminator, so the cell spans two lines in a spreadsheet. Flatten a nested record into separate fields before you derive the codec.

### Unsupported Types

Attempting to derive a codec for unsupported types (sealed traits, `Option` fields, sequences, maps, dynamic types) throws an `UnsupportedOperationException` at run time, when `Schema#derive` runs; the code still compiles. `Option` is rejected as a variant, with the message `CSV does not support variant/sum types. Use a flat case class instead.` The deriver rejects these to prevent silent failures in CSV processing.

---

## CsvFormat

Integration point with ZIO Schema's format system. Provides `TextFormat[CsvCodec]` to enable `Schema[A].derive(CsvFormat)` for any supported type.

### Using CsvFormat

To derive a CSV codec using the standard format:

```scala
import zio.blocks.schema._
import zio.blocks.schema.csv._

case class Sensor(id: Long, temperature: Double, humidity: Float)

object Sensor {
  implicit val schema: Schema[Sensor] = Schema.derived
}

val codec = Sensor.schema.derive(CsvFormat)
```

`CsvFormat` is a singleton object extending `TextFormat[CsvCodec]` with the MIME type `"text/csv"` and the `CsvCodecDeriver` as its derivation strategy.
