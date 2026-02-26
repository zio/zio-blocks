package zio.blocks.schema.csv

import zio.blocks.schema.SchemaBaseSpec
import zio.test._

object CsvReaderSpec extends SchemaBaseSpec {
  def spec = suite("CsvReaderSpec")(
    suite("readRow")(
      test("parses simple unquoted fields") {
        val result = CsvReader.readRow("Alice,30\r\n", 0, CsvConfig.default)
        assertTrue(result == Right((Vector("Alice", "30"), 10)))
      },
      test("parses quoted field containing delimiter") {
        val result = CsvReader.readRow("\"hello, world\",foo\r\n", 0, CsvConfig.default)
        assertTrue(result == Right((Vector("hello, world", "foo"), 20)))
      },
      test("parses escaped quotes inside quoted field") {
        val result = CsvReader.readRow("\"he said \"\"hi\"\"\",bar\r\n", 0, CsvConfig.default)
        assertTrue(result == Right((Vector("he said \"hi\"", "bar"), 22)))
      },
      test("parses multiline quoted field") {
        val input  = "\"line1\nline2\",ok\r\n"
        val result = CsvReader.readRow(input, 0, CsvConfig.default)
        assertTrue(result == Right((Vector("line1\nline2", "ok"), 18)))
      },
      test("parses empty fields") {
        val result = CsvReader.readRow(",,\r\n", 0, CsvConfig.default)
        assertTrue(result == Right((Vector("", "", ""), 4)))
      },
      test("parses empty quoted field") {
        val result = CsvReader.readRow("\"\",a\r\n", 0, CsvConfig.default)
        assertTrue(result == Right((Vector("", "a"), 6)))
      },
      test("parses single column") {
        val result = CsvReader.readRow("hello\r\n", 0, CsvConfig.default)
        assertTrue(result == Right((Vector("hello"), 7)))
      },
      test("returns empty vector for empty input at offset") {
        val result = CsvReader.readRow("", 0, CsvConfig.default)
        assertTrue(result == Right((Vector.empty[String], 0)))
      },
      test("parses at given offset") {
        val input  = "name,age\r\nAlice,30\r\n"
        val result = CsvReader.readRow(input, 10, CsvConfig.default)
        assertTrue(result == Right((Vector("Alice", "30"), 20)))
      },
      test("handles LF line ending") {
        val result = CsvReader.readRow("a,b\n", 0, CsvConfig.default)
        assertTrue(result == Right((Vector("a", "b"), 4)))
      },
      test("handles CR line ending") {
        val result = CsvReader.readRow("a,b\r", 0, CsvConfig.default)
        assertTrue(result == Right((Vector("a", "b"), 4)))
      },
      test("handles CRLF line ending") {
        val result = CsvReader.readRow("a,b\r\n", 0, CsvConfig.default)
        assertTrue(result == Right((Vector("a", "b"), 5)))
      },
      test("handles row ending at EOF without newline") {
        val result = CsvReader.readRow("a,b", 0, CsvConfig.default)
        assertTrue(result == Right((Vector("a", "b"), 3)))
      },
      test("returns ParseError for unclosed quote") {
        val result = CsvReader.readRow("\"unclosed,field\r\n", 0, CsvConfig.default)
        assertTrue(result.isLeft && result.left.exists(_.isInstanceOf[CsvError.ParseError]))
      },
      test("returns ParseError for unexpected character after closing quote") {
        val result = CsvReader.readRow("\"field\"x,b\r\n", 0, CsvConfig.default)
        assertTrue(result.isLeft && result.left.exists(_.isInstanceOf[CsvError.ParseError]))
      },
      test("handles CRLF inside quoted field") {
        val input  = "\"line1\r\nline2\",ok\r\n"
        val result = CsvReader.readRow(input, 0, CsvConfig.default)
        assertTrue(result == Right((Vector("line1\r\nline2", "ok"), 19)))
      }
    ),
    suite("readHeader")(
      test("reads first row as header") {
        val result = CsvReader.readHeader("name,age\r\nAlice,30\r\n", CsvConfig.default)
        assertTrue(result == Right((Vector("name", "age"), 10)))
      },
      test("reads header with quoted fields") {
        val result = CsvReader.readHeader("\"first,name\",age\r\n", CsvConfig.default)
        assertTrue(result == Right((Vector("first,name", "age"), 18)))
      },
      test("returns empty for empty input") {
        val result = CsvReader.readHeader("", CsvConfig.default)
        assertTrue(result == Right((Vector.empty[String], 0)))
      }
    ),
    suite("readAll")(
      test("parses header and data rows") {
        val input  = "name,age\r\nAlice,30\r\n"
        val result = CsvReader.readAll(input, CsvConfig.default)
        assertTrue(result == Right((Vector("name", "age"), Vector(Vector("Alice", "30")))))
      },
      test("parses multiple data rows") {
        val input  = "name,age\r\nAlice,30\r\nBob,25\r\n"
        val result = CsvReader.readAll(input, CsvConfig.default)
        assertTrue(result == Right((Vector("name", "age"), Vector(Vector("Alice", "30"), Vector("Bob", "25")))))
      },
      test("handles empty input") {
        val result = CsvReader.readAll("", CsvConfig.default)
        assertTrue(result == Right((Vector.empty[String], Vector.empty[IndexedSeq[String]])))
      },
      test("handles header only with no data rows") {
        val input  = "name,age\r\n"
        val result = CsvReader.readAll(input, CsvConfig.default)
        assertTrue(result == Right((Vector("name", "age"), Vector.empty[IndexedSeq[String]])))
      },
      test("handles trailing newline without extra empty row") {
        val input  = "name,age\r\nAlice,30\r\n"
        val result = CsvReader.readAll(input, CsvConfig.default)
        result match {
          case Right((_, rows)) => assertTrue(rows.length == 1)
          case Left(_)          => assertTrue(false)
        }
      },
      test("hasHeader=false returns empty header and all rows as data") {
        val config = CsvConfig.default.copy(hasHeader = false)
        val input  = "Alice,30\r\nBob,25\r\n"
        val result = CsvReader.readAll(input, config)
        assertTrue(
          result == Right((Vector.empty[String], Vector(Vector("Alice", "30"), Vector("Bob", "25"))))
        )
      },
      test("parses with quoted fields in data rows") {
        val input  = "name,bio\r\nAlice,\"likes \"\"cats\"\"\"\r\n"
        val result = CsvReader.readAll(input, CsvConfig.default)
        assertTrue(result == Right((Vector("name", "bio"), Vector(Vector("Alice", "likes \"cats\"")))))
      },
      test("parses with multiline quoted field in data") {
        val input  = "name,bio\r\nAlice,\"line1\nline2\"\r\n"
        val result = CsvReader.readAll(input, CsvConfig.default)
        assertTrue(result == Right((Vector("name", "bio"), Vector(Vector("Alice", "line1\nline2")))))
      },
      test("returns error for malformed data row") {
        val input  = "name,age\r\n\"unclosed,30\r\n"
        val result = CsvReader.readAll(input, CsvConfig.default)
        assertTrue(result.isLeft)
      },
      test("single column CSV") {
        val input  = "name\r\nAlice\r\nBob\r\n"
        val result = CsvReader.readAll(input, CsvConfig.default)
        assertTrue(result == Right((Vector("name"), Vector(Vector("Alice"), Vector("Bob")))))
      }
    ),
    suite("custom configs")(
      test("TSV config uses tab delimiter") {
        val input  = "name\tage\r\nAlice\t30\r\n"
        val result = CsvReader.readAll(input, CsvConfig.tsv)
        assertTrue(result == Right((Vector("name", "age"), Vector(Vector("Alice", "30")))))
      },
      test("custom semicolon delimiter") {
        val config = CsvConfig(delimiter = ';')
        val input  = "name;age\r\nAlice;30\r\n"
        val result = CsvReader.readAll(input, config)
        assertTrue(result == Right((Vector("name", "age"), Vector(Vector("Alice", "30")))))
      }
    ),
    suite("round-trip with CsvWriter")(
      test("readAll can parse writeAll output") {
        val header = Vector("name", "age", "city")
        val rows   = List(Vector("Alice", "30", "NYC"), Vector("Bob", "25", "LA"))
        val csv    = CsvWriter.writeAll(header, rows, CsvConfig.default)
        val result = CsvReader.readAll(csv, CsvConfig.default)
        assertTrue(result == Right((header, rows.map(_.toVector).toVector)))
      },
      test("round-trip with special characters") {
        val header = Vector("name", "bio")
        val rows   = List(Vector("Alice", "likes \"cats\""), Vector("Bob", "hello, world"))
        val csv    = CsvWriter.writeAll(header, rows, CsvConfig.default)
        val result = CsvReader.readAll(csv, CsvConfig.default)
        assertTrue(result == Right((header, rows.map(_.toVector).toVector)))
      },
      test("round-trip with multiline fields") {
        val header = Vector("name", "address")
        val rows   = List(Vector("Alice", "123 Main St\nApt 4\nNYC"))
        val csv    = CsvWriter.writeAll(header, rows, CsvConfig.default)
        val result = CsvReader.readAll(csv, CsvConfig.default)
        assertTrue(result == Right((header, rows.map(_.toVector).toVector)))
      }
    ),
    suite("line ending handling")(
      test("LF-terminated rows") {
        val config = CsvConfig.default
        val input  = "name,age\nAlice,30\nBob,25\n"
        val result = CsvReader.readAll(input, config)
        assertTrue(result == Right((Vector("name", "age"), Vector(Vector("Alice", "30"), Vector("Bob", "25")))))
      },
      test("CR-terminated rows") {
        val config = CsvConfig.default
        val input  = "name,age\rAlice,30\rBob,25\r"
        val result = CsvReader.readAll(input, config)
        assertTrue(result == Right((Vector("name", "age"), Vector(Vector("Alice", "30"), Vector("Bob", "25")))))
      },
      test("CRLF-terminated rows") {
        val config = CsvConfig.default
        val input  = "name,age\r\nAlice,30\r\nBob,25\r\n"
        val result = CsvReader.readAll(input, config)
        assertTrue(result == Right((Vector("name", "age"), Vector(Vector("Alice", "30"), Vector("Bob", "25")))))
      },
      test("mixed line endings") {
        val config = CsvConfig.default
        val input  = "name,age\nAlice,30\r\nBob,25\r"
        val result = CsvReader.readAll(input, config)
        assertTrue(result == Right((Vector("name", "age"), Vector(Vector("Alice", "30"), Vector("Bob", "25")))))
      }
    )
  )
}
