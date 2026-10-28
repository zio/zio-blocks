---
id: read-and-write-csv-and-tsv-files
title: "How to Read and Write CSV and TSV Files"
description: "Read CSV and TSV files into case classes by header name, report every bad row, write records back out and process large files row by row."
keywords:
  - "CSV"
  - "TSV"
  - "Tabular Data"
  - "CsvReader"
  - "CsvWriter"
  - "CsvFormat"
---

In this guide, we will read a bookshop's inventory file into Scala records and write it back out, using the CSV module of ZIO Blocks. By the end, you will have a handful of small functions that read a CSV or TSV file by column name, list every bad row with its row number, write records with correct quoting, and process a file of any size one row at a time.

We start with a three-line CSV file and grow the code step by step:

- Read a CSV file with a header into records.
- Report every bad row instead of stopping at the first one.
- Write records back out as CSV.
- Read and write TSV, which needs a different delimiter.
- Process a large file without loading it into memory.
- Turn records with lists and optional fields into flat rows.

## The Problem

A CSV file looks easy to read: split each line at the commas, then convert each cell. That works until a cell contains a comma. The authors of a book are a good example, because a CSV writer wraps such a cell in double quotes:

```scala mdoc
val inventoryLine = "Programming in Scala,2008,\"Martin Odersky, Lex Spoon, Bill Venners\",7"

println(inventoryLine.split(",").toList)
```

The code above splits a line with four columns into six cells, and the quote characters end up inside the cells. Converting the fourth cell to the number of copies now throws a `NumberFormatException`. A correct reader also has to handle doubled quotes (`""`) inside a quoted cell, line breaks inside a quoted cell, files whose columns come in a different order, and files that use tabs instead of commas. Hand-written parsers rarely handle all of these, and they fail on real exports from spreadsheets and databases.

In this guide, we solve this with `zio-blocks-schema-csv`: `CsvReader` and `CsvWriter` split and join rows by the rules of RFC 4180, and a codec derived from a `Schema` converts each row to and from a case class.

## Prerequisites

To follow along, add the schema and CSV modules to your build:

```scala
// Scala 2.13 and Scala 3.3 to 3.8
libraryDependencies ++= Seq(
  "dev.zio" %% "zio-blocks-schema"     % "@VERSION@",
  "dev.zio" %% "zio-blocks-schema-csv" % "@VERSION@"
)

// Scala 3.9 and newer (also needs scalacOptions += "-experimental"): the same modules with the zio-blocks-next- prefix
libraryDependencies ++= Seq(
  "dev.zio" %% "zio-blocks-next-schema"     % "@VERSION@",
  "dev.zio" %% "zio-blocks-next-schema-csv" % "@VERSION@"
)
```

This guide assumes you can derive a `Schema` for a case class, as shown in [Your First Schema](./your-first-schema.md). The CSV section of [One Type, Many Formats](./one-type-many-formats.md#10-flatten-the-catalog-for-csv) shows how a CSV codec encodes and decodes a single row; here we build whole files on top of it.

## The Inventory Row

Each row of the inventory file describes one book: its title, its authors, the year it was published and the number of copies in stock. We model one row as a flat case class and derive its CSV codec with `Schema#derive(CsvFormat)`:

```scala mdoc:silent
import java.nio.CharBuffer
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path}
import zio.blocks.schema._
import zio.blocks.schema.csv._

case class BookRow(title: String, authors: String, year: Int, copies: Int)

object BookRow {
  implicit val schema: Schema[BookRow] = Schema.derived
}

val bookCodec: CsvCodec[BookRow] = BookRow.schema.derive(CsvFormat)
```

The codec names one column for each field, in declaration order. `CsvCodec#headerNames` returns these names:

```scala mdoc
println(bookCodec.headerNames)
```

The `authors` field holds all the authors of a book in one string, such as `"Martin Odersky, Lex Spoon, Bill Venners"`. A CSV cell holds one value, so a list of authors has to become a single string; we come back to this in [Flatten Lists and Optional Fields](#flatten-lists-and-optional-fields).

## Read a CSV File With a Header

Our sample file lists its columns in a different order from `BookRow`: the year comes before the authors. Files exported by other tools often do this, so we match columns by their header name, not by position. First, we write the file to a temporary path:

```scala mdoc:silent
val inventoryCsv: String =
  """title,year,authors,copies
    |Zionomicon,2024,"John A. De Goes, Adam Fraser and Milad Khajavi",12
    |Programming in Scala,2008,"Martin Odersky, Lex Spoon, Bill Venners",7
    |Functional Programming in Scala,2014,"Paul Chiusano, Rúnar Bjarnason",5
    |""".stripMargin

val inventoryPath: Path = Files.createTempFile("inventory", ".csv")
Files.writeString(inventoryPath, inventoryCsv, UTF_8)
```

`CsvReader.readAll` splits a whole document into a header and a list of rows, where each row is a list of cell strings. It takes the text and a `CsvConfig`; `CsvConfig.default` means commas between cells and double quotes around cells that need them:

```scala mdoc
val inventory = CsvReader.readAll(Files.readString(inventoryPath, UTF_8), CsvConfig.default)
```

The quoted authors are now single cells, without their quotes. Next, we turn each row of cells into a `BookRow`. The derived codec decodes one CSV line whose cells are in `CsvCodec#headerNames` order, so we pick the cells in that order, join them back into a line with `CsvWriter.writeRow`, and decode the line. A small `RowDecoder` does this, and its `RowDecoder.fromHeader` constructor finds the position of each column in the file's header:

```scala mdoc:silent
final class RowDecoder[A](codec: CsvCodec[A], width: Int, positions: IndexedSeq[Int]) {
  def decode(cells: IndexedSeq[String]): Either[String, A] =
    if (cells.length != width) Left(s"expected $width cells, got ${cells.length}")
    else {
      val line = CsvWriter.writeRow(positions.map(cells), CsvConfig.default)
      codec.decode(CharBuffer.wrap(line)).left.map(_.getMessage)
    }
}

object RowDecoder {
  def fromHeader[A](codec: CsvCodec[A], header: IndexedSeq[String]): Either[String, RowDecoder[A]] = {
    val missing = codec.headerNames.filterNot(header.contains)
    if (missing.nonEmpty) Left(s"missing column(s): ${missing.mkString(", ")}")
    else Right(new RowDecoder(codec, header.length, codec.headerNames.map(name => header.indexOf(name))))
  }
}
```

In the code above, `positions` holds, for each column of `BookRow`, its index in the file. `RowDecoder#decode` first checks that the row has as many cells as the header, because a short row would otherwise put cells in the wrong columns. `CsvWriter.writeRow` quotes any cell that needs it, so a cell such as `John A. De Goes, Adam Fraser and Milad Khajavi` survives the trip back into a line. With the decoder in place, we decode every row of the inventory:

```scala mdoc
inventory.foreach { case (header, rows) =>
  RowDecoder.fromHeader(bookCodec, header) match {
    case Left(error)    => println(error)
    case Right(decoder) => rows.map(decoder.decode).foreach(println)
  }
}
```

Each row decodes to a `BookRow`, with the year and the number of copies parsed as `Int` values.

## Report Every Bad Row

Real files contain mistakes, and a reader that stops at the first one makes the user fix a file one error at a time. We decode every row, keep the good records, and collect a message with the row number for each bad one. The header is row 1, so the first data row is row 2:

```scala mdoc:silent
final case class Report[A](records: IndexedSeq[A], errors: IndexedSeq[String])

def readCsv[A](codec: CsvCodec[A], text: String, config: CsvConfig): Either[String, Report[A]] =
  CsvReader.readAll(text, config) match {
    case Left(error)           => Left(error.getMessage)
    case Right((header, rows)) =>
      RowDecoder.fromHeader(codec, header).map { decoder =>
        val results = rows.zipWithIndex.map { case (cells, index) =>
          decoder.decode(cells).left.map(error => s"row ${index + 2}: $error")
        }
        Report(results.collect { case Right(a) => a }, results.collect { case Left(e) => e })
      }
  }
```

The following file has two mistakes: a year written as a Roman numeral, and a row that lost its last cell:

```scala mdoc:silent
val damagedCsv: String =
  """title,year,authors,copies
    |Zionomicon,2024,"John A. De Goes, Adam Fraser and Milad Khajavi",12
    |Programming in Scala,MMVIII,"Martin Odersky, Lex Spoon, Bill Venners",7
    |Functional Programming in Scala,2014,"Paul Chiusano, Rúnar Bjarnason"
    |""".stripMargin
```

Reading it returns the one good record and both errors:

```scala mdoc
readCsv(bookCodec, damagedCsv, CsvConfig.default).foreach { report =>
  report.records.foreach(println)
  report.errors.foreach(println)
}
```

The message for row 3 comes from the derived codec. It quotes the bad value, but it doesn't name the column: the trailing `at: .` is the position inside the record, which the CSV codec leaves empty. The cell count message for row 4 comes from our `RowDecoder`. A file that lacks a whole column fails before any row is decoded:

```scala mdoc
println(readCsv(bookCodec, "title,year,authors\nZionomicon,2024,\"John A. De Goes, Adam Fraser and Milad Khajavi\"\n", CsvConfig.default))
```

A quote that is opened but never closed is a different kind of error. Everything after the opening quote, up to the end of the file, belongs to one cell, so no later row can be trusted. `CsvReader.readAll` returns a `CsvError.ParseError` for the whole document. In the following file, the authors cell on the last line has no closing quote:

```scala mdoc:silent
val unclosedCsv: String =
  """title,year,authors,copies
    |Zionomicon,2024,"John A. De Goes, Adam Fraser and Milad Khajavi",12
    |Programming in Scala,2008,"Martin Odersky, Lex Spoon, Bill Venners,7
    |""".stripMargin
```

Reading it returns the parse error and no records:

```scala mdoc
println(readCsv(bookCodec, unclosedCsv, CsvConfig.default))
```

:::warning
The row and column in a `CsvError.ParseError` count from the start of the row that failed, not from the start of the file. Above, the unclosed quote is on line 3 of the file, but the error says `row 2, column 1`: the count starts again at 1 for the row being parsed, and the open cell runs past that row's line break to the end of the file. To report file line numbers, count the lines yourself, as the reader in [Process a Large File Row by Row](#process-a-large-file-row-by-row) does.
:::

## Write Records as CSV

Writing is the reverse: the derived codec encodes one record as one CSV line, ending with `\r\n`. `CsvCodec#encode` writes into a `java.nio.CharBuffer`, so we allocate a buffer, encode, and read the line back out:

```scala mdoc:silent
def encodeLine[A](codec: CsvCodec[A], value: A): String = {
  val buffer = CharBuffer.allocate(4096)
  codec.encode(value, buffer)
  buffer.flip()
  buffer.toString
}

def writeCsv[A](codec: CsvCodec[A], values: Iterable[A]): String =
  CsvWriter.writeHeader(codec.headerNames, CsvConfig.default) + values.map(value => encodeLine(codec, value)).mkString
```

The buffer must be large enough for one line; `CharBuffer#put` throws a `java.nio.BufferOverflowException` when a line doesn't fit. `CsvWriter.writeHeader` writes the column names as the first line, quoted by the same rules as data. Here is the inventory after a delivery of new copies, written as CSV text:

```scala mdoc:silent
val restocked: List[BookRow] = List(
  BookRow("Zionomicon", "John A. De Goes, Adam Fraser and Milad Khajavi", 2024, 20),
  BookRow("Programming in Scala", "Martin Odersky, Lex Spoon, Bill Venners", 2008, 7),
  BookRow("Functional Programming in Scala", "Paul Chiusano, Rúnar Bjarnason", 2014, 9)
)

val restockedCsv: String = writeCsv(bookCodec, restocked)
```

Printing the text line by line shows the CSV file:

```scala mdoc
restockedCsv.linesIterator.foreach(println)
```

The writer quotes only the cells that need it: the authors contain commas, so they are wrapped in double quotes, and the titles are not. A cell that contains a double quote is quoted too, and each quote inside it is doubled:

```scala mdoc
println(CsvWriter.writeRow(IndexedSeq("Zionomicon", "the \"ZIO\" book"), CsvConfig.default).stripLineEnd)
```

Finally, we save the file:

```scala mdoc:silent
val restockedPath: Path = Files.createTempFile("restocked", ".csv")
Files.writeString(restockedPath, restockedCsv, UTF_8)
```

Then we read it back with `readCsv` from the previous section, to check that every record survives the round trip:

```scala mdoc
readCsv(bookCodec, Files.readString(restockedPath, UTF_8), CsvConfig.default).map(_.records == restocked)
```

## Read and Write TSV

A tab-separated file (TSV) is a CSV file with a tab between the cells. `CsvConfig.tsv` is the preset for it, and `CsvReader` and `CsvWriter` accept it in place of `CsvConfig.default`. The derived codec does not: it always reads and writes commas, whatever `CsvConfig` the rest of your code uses. A TSV line given straight to the codec is split at its commas, not at its tabs:

```scala mdoc
println(bookCodec.decode(CharBuffer.wrap("Zionomicon\tJohn A. De Goes, Adam Fraser and Milad Khajavi\t2024\t12")))
```

Our `readCsv` already works for TSV, because it splits rows with `CsvReader` and the configuration we pass, and only hands the codec lines that `CsvWriter.writeRow` joined with commas. For writing, we turn each record into a list of cells: we encode it with the codec and split the CSV line again with `CsvReader.readRow`. `CsvWriter.writeAll` then joins the header and the rows with any delimiter:

```scala mdoc:silent
def encodeCells[A](codec: CsvCodec[A], value: A): IndexedSeq[String] =
  CsvReader.readRow(encodeLine(codec, value), 0, CsvConfig.default) match {
    case Right((cells, _)) => cells
    case Left(error)       => throw error
  }

def writeDelimited[A](codec: CsvCodec[A], values: Iterable[A], config: CsvConfig): String =
  CsvWriter.writeAll(codec.headerNames, values.map(value => encodeCells(codec, value)), config)

val restockedTsv: String = writeDelimited(bookCodec, restocked, CsvConfig.tsv)
```

`CsvReader.readRow` parses one row starting at a character offset (here 0) and returns the cells and the offset after the row. Since the codec wrote the line, it always parses, and the `throw` never runs. Printing the TSV text shows tabs between the cells:

```scala mdoc
restockedTsv.linesIterator.foreach(println)
```

The authors are no longer quoted, because a comma is an ordinary character in a TSV file; only a cell that contains a tab, a quote or a line break is quoted. Reading the TSV text back with `CsvConfig.tsv` returns the same records:

```scala mdoc
readCsv(bookCodec, restockedTsv, CsvConfig.tsv).map(_.records == restocked)
```

The same two functions work for any delimiter, such as `CsvConfig(delimiter = ';')` for the semicolon-separated files that spreadsheets write in many European locales.

## Process a Large File Row by Row

`CsvReader.readAll` needs the whole file in one `String`, and returns all rows at once. For a file of several gigabytes, we read one record at a time instead and decode each one as it arrives. The module has no file or stream support of its own, so we read lines with a `java.io.BufferedReader` and give each record to `CsvReader.readRow`.

There is one catch: a quoted cell may contain a line break, so one record can span several lines. RFC 4180 doubles every quote inside a quoted cell, so a record is complete when it contains an even number of quote characters. The following iterator joins lines until that is true, and remembers the line number where each record starts:

```scala mdoc:silent
import java.io.BufferedReader
import scala.annotation.tailrec
import scala.util.Using

def csvRecords(reader: BufferedReader, quoteChar: Char): Iterator[(Int, String)] = {
  val lines = Iterator.continually(reader.readLine()).takeWhile(_ != null).zip(Iterator.from(1))

  @tailrec
  def complete(record: String): String =
    if (record.count(_ == quoteChar) % 2 == 0 || !lines.hasNext) record
    else complete(record + "\n" + lines.next()._1)

  lines.map { case (firstLine, lineNumber) => (lineNumber, complete(firstLine)) }
}
```

`csvRecords` pairs each line with its number, then maps every line that starts a record to the complete record. `complete` takes the following lines from the same `lines` iterator, so a line that continues a record is never seen as the start of a new one.

The parity rule relies on the file following RFC 4180. A file with a stray quote in the middle of an unquoted cell, such as `5" floppy`, breaks it; `CsvReader` accepts such a cell as text, but the iterator then joins lines until it finds another quote. `BufferedReader#readLine` also drops the line ending, so a line break inside a cell comes back as `\n` even if the file has `\r\n`.

Next, `withCsvFile` opens a file, reads its header, and hands the caller an iterator of decoded rows, each with its line number. The file stays open only while the caller's function runs, so the caller must consume the iterator inside it:

```scala mdoc:silent
def withCsvFile[A, B](path: Path, codec: CsvCodec[A], config: CsvConfig)(
  use: Iterator[(Int, Either[String, A])] => B
): Either[String, B] =
  Using.resource(Files.newBufferedReader(path, UTF_8)) { reader =>
    val records = csvRecords(reader, config.quoteChar)
    if (!records.hasNext) Left("the file is empty")
    else {
      val (_, headerText) = records.next()
      CsvReader.readRow(headerText, 0, config).left.map(_.getMessage).flatMap { case (header, _) =>
        RowDecoder.fromHeader(codec, header).map { decoder =>
          use(records.filter(_._2.nonEmpty).map { case (line, text) =>
            val decoded = CsvReader.readRow(text, 0, config) match {
              case Left(error)       => Left(s"line ${line + error.row - 1}, column ${error.column}: ${error.message}")
              case Right((cells, _)) => decoder.decode(cells).left.map(error => s"line $line: $error")
            }
            (line, decoded)
          })
        }
      }
    }
  }
```

Because each record is parsed on its own, a parse error such as a stray character after a closing quote affects only that record. `CsvError#row` counts lines from the start of the record, so `line + error.row - 1` is the line in the file, and `CsvError#column` is the column within that line. Blank lines are skipped. To try it, we write a small file with a cell that spans two lines, a bad year and a stray character after a closing quote:

```scala mdoc:silent
val awkwardPath: Path = Files.createTempFile("awkward", ".csv")
Files.writeString(
  awkwardPath,
  """title,year,authors,copies
    |Zionomicon,2024,"John A. De Goes,
    |Adam Fraser and Milad Khajavi",12
    |Programming in Scala,MMVIII,"Martin Odersky, Lex Spoon, Bill Venners",7
    |"Functional Programming in Scala"!,2014,"Paul Chiusano, Rúnar Bjarnason",5
    |""".stripMargin,
  UTF_8
)
```

Reading it prints each result as it is decoded:

```scala mdoc
withCsvFile(awkwardPath, bookCodec, CsvConfig.default)(rows => rows.foreach(println))
```

The first record spans lines 2 and 3, and its authors cell keeps the line break. The errors point to lines 4 and 5 of the file.

Writing a large file needs no new code: open a `java.io.BufferedWriter`, write the header, then write one encoded line per record. The following code writes 100,000 rows by cycling through the restocked inventory:

```scala mdoc:silent
def writeCsvFile[A](path: Path, codec: CsvCodec[A], values: Iterator[A]): Unit =
  Using.resource(Files.newBufferedWriter(path, UTF_8)) { writer =>
    writer.write(CsvWriter.writeHeader(codec.headerNames, CsvConfig.default))
    values.foreach(value => writer.write(encodeLine(codec, value)))
  }

val largePath: Path = Files.createTempFile("large-inventory", ".csv")
writeCsvFile(largePath, bookCodec, Iterator.continually(restocked).flatten.take(100000))
```

Then we read it back and add up the copies in stock, holding one record in memory at a time:

```scala mdoc
withCsvFile(largePath, bookCodec, CsvConfig.default) { rows =>
  rows.foldLeft((0, 0L)) {
    case ((count, copies), (_, Right(book))) => (count + 1, copies + book.copies)
    case (totals, (_, Left(error)))          => println(error); totals
  }
}
```

The result is the number of rows and the total number of copies. Both functions stream: `Iterator#foldLeft` pulls one record at a time from the reader, and `writeCsvFile` writes each line as soon as it is encoded.

## Flatten Lists and Optional Fields

A CSV codec can only be derived for records whose fields are plain values: strings, numbers, dates and the other primitive types, or wrappers around them. A field of type `List`, `Map` or `Option`, or a sealed trait, has no single cell to go into. The deriver rejects such a record when `Schema#derive` runs, with an `UnsupportedOperationException`; the code compiles, and the error comes at run time:

```scala mdoc
case class Book(title: String, authors: List[String], year: Int, isbn: Option[String])

object Book {
  implicit val schema: Schema[Book] = Schema.derived
}

println(scala.util.Try(Book.schema.derive(CsvFormat)))
```

The message is about the `Option` field: the schema models `Option` as a sum type of `Some` and `None`, and CSV has no way to say which one a cell holds. A record whose only problem is a `List` field fails with `CSV does not support sequence fields.` instead. Neither message names the field, so check every field type when a derivation fails.

The workaround is a flat row type for the file, and a pair of functions that convert between it and the domain type. Each conversion is a choice about the file format: here the authors are joined with `"; "`, and a missing ISBN becomes an empty cell:

```scala mdoc:silent
case class BookExportRow(title: String, authors: String, year: Int, isbn: String)

object BookExportRow {
  implicit val schema: Schema[BookExportRow] = Schema.derived

  def fromBook(book: Book): BookExportRow =
    BookExportRow(book.title, book.authors.mkString("; "), book.year, book.isbn.getOrElse(""))

  def toBook(row: BookExportRow): Book =
    Book(row.title, row.authors.split("; ").toList, row.year, Some(row.isbn).filter(_.nonEmpty))
}

val exportCodec: CsvCodec[BookExportRow] = BookExportRow.schema.derive(CsvFormat)

val catalog: List[Book] = List(
  Book("Zionomicon", List("John A. De Goes", "Adam Fraser", "Milad Khajavi"), 2024, None),
  Book("Programming in Scala", List("Martin Odersky", "Lex Spoon", "Bill Venners"), 2008, Some("978-0-9815316-0-1"))
)

val catalogCsv: String = writeCsv(exportCodec, catalog.map(BookExportRow.fromBook))
```

The CSV text has one cell per field, and the empty ISBN is an empty cell:

```scala mdoc
catalogCsv.linesIterator.foreach(println)
```

Reading the text back and converting each row restores the original books:

```scala mdoc
readCsv(exportCodec, catalogCsv, CsvConfig.default).map(_.records.map(BookExportRow.toBook) == catalog)
```

:::warning
A field whose type is another case class is not rejected, but it doesn't become separate columns either. Take a row with a nested `Edition` record:

```scala mdoc:silent
case class Edition(number: Int, year: Int)

object Edition {
  implicit val schema: Schema[Edition] = Schema.derived
}

case class EditionRow(title: String, edition: Edition)

object EditionRow {
  implicit val schema: Schema[EditionRow] = Schema.derived
}

val editionCodec: CsvCodec[EditionRow] = EditionRow.schema.derive(CsvFormat)
```

The codec writes the nested record as one quoted cell that ends with a line break (shown here as `\r\n`), and `CsvCodec#headerNames` has a single column for it:

```scala mdoc
println(editionCodec.headerNames)
println(encodeLine(editionCodec, EditionRow("Programming in Scala", Edition(5, 2021))).replace("\r\n", "\\r\\n"))
```

Spreadsheets show that cell as a two-line value. Flatten nested records into separate fields, such as `editionNumber` and `editionYear`, in the same way as lists and options.
:::

## Putting It Together

The complete example reads an inventory file row by row, reports its bad rows with their line numbers, and writes the good records to a TSV file:

```scala mdoc:embed:schema-examples/src/main/scala/readandwritecsvandtsvfiles/CompleteExample.scala
```

## Running the Examples

All examples in this guide have runnable Scala files in the `schema-examples` project. To get them, clone the repository and change into the examples project:

```bash
git clone https://github.com/zio/zio-blocks.git
cd zio-blocks/schema-examples
```

Each example below writes its sample files to temporary paths, so it runs anywhere without setup.

### Read a CSV File With a Header

This example writes the inventory to a temporary file, reads it with `CsvReader.readAll`, and decodes each row by column name.

<details>
  <summary>schema-examples/src/main/scala/readandwritecsvandtsvfiles/Step1ReadCsvExample.scala</summary>

```scala mdoc:embed:schema-examples/src/main/scala/readandwritecsvandtsvfiles/Step1ReadCsvExample.scala:show-line-numbers
```

</details>

Run it from the `schema-examples` directory:

```bash
sbt "runMain readandwritecsvandtsvfiles.Step1ReadCsvExample"
```

### Report Every Bad Row

This example reads a damaged file, keeps the good records, and prints every bad row with its row number, a missing column and an unclosed quote.

<details>
  <summary>schema-examples/src/main/scala/readandwritecsvandtsvfiles/Step2ReportBadRowsExample.scala</summary>

```scala mdoc:embed:schema-examples/src/main/scala/readandwritecsvandtsvfiles/Step2ReportBadRowsExample.scala:show-line-numbers
```

</details>

Run it from the `schema-examples` directory:

```bash
sbt "runMain readandwritecsvandtsvfiles.Step2ReportBadRowsExample"
```

### Write Records as CSV

This example encodes records with the derived codec, writes the CSV text to a file, and reads it back.

<details>
  <summary>schema-examples/src/main/scala/readandwritecsvandtsvfiles/Step3WriteCsvExample.scala</summary>

```scala mdoc:embed:schema-examples/src/main/scala/readandwritecsvandtsvfiles/Step3WriteCsvExample.scala:show-line-numbers
```

</details>

Run it from the `schema-examples` directory:

```bash
sbt "runMain readandwritecsvandtsvfiles.Step3WriteCsvExample"
```

### Read and Write TSV

This example writes the inventory with `CsvConfig.tsv` and reads it back, and shows what the derived codec does with a TSV line.

<details>
  <summary>schema-examples/src/main/scala/readandwritecsvandtsvfiles/Step4TsvExample.scala</summary>

```scala mdoc:embed:schema-examples/src/main/scala/readandwritecsvandtsvfiles/Step4TsvExample.scala:show-line-numbers
```

</details>

Run it from the `schema-examples` directory:

```bash
sbt "runMain readandwritecsvandtsvfiles.Step4TsvExample"
```

### Process a Large File Row by Row

This example reads a file with a multi-line cell and bad rows one record at a time, then writes and sums a file of 100,000 rows.

<details>
  <summary>schema-examples/src/main/scala/readandwritecsvandtsvfiles/Step5LargeFileExample.scala</summary>

```scala mdoc:embed:schema-examples/src/main/scala/readandwritecsvandtsvfiles/Step5LargeFileExample.scala:show-line-numbers
```

</details>

Run it from the `schema-examples` directory:

```bash
sbt "runMain readandwritecsvandtsvfiles.Step5LargeFileExample"
```

### Flatten Lists and Optional Fields

This example shows the deriver rejecting a `List` field, then exports and imports books through a flat row type.

<details>
  <summary>schema-examples/src/main/scala/readandwritecsvandtsvfiles/Step6FlatRowsExample.scala</summary>

```scala mdoc:embed:schema-examples/src/main/scala/readandwritecsvandtsvfiles/Step6FlatRowsExample.scala:show-line-numbers
```

</details>

Run it from the `schema-examples` directory:

```bash
sbt "runMain readandwritecsvandtsvfiles.Step6FlatRowsExample"
```

### The Complete Example

Run the complete example from the `schema-examples` directory:

```bash
sbt "runMain readandwritecsvandtsvfiles.CompleteExample"
```

### Compile All Examples

To compile every example in the project without running any of them, run this from the `schema-examples` directory:

```bash
sbt compile
```

## Going Further

- The [CSV reference page](../reference/schema/built-in-codecs/csv.md) lists every `CsvConfig` field and every method of `CsvReader`, `CsvWriter` and `CsvCodec`.
- [One Type, Many Formats](./one-type-many-formats.md) derives JSON, YAML, XML, TOON, MessagePack and Avro codecs from the same kind of schema, for data that doesn't fit in a table.
- [Schema](../reference/schema/schema.md) explains `Schema#derive` and the formats it accepts.
