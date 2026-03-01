package zio.blocks.schema.csv

import zio.blocks.schema.SchemaBaseSpec
import zio.test._

object CsvWriterSpec extends SchemaBaseSpec {
  def spec = suite("CsvWriterSpec")(
    suite("escapeField via writeRow")(
      test("simple fields are unquoted") {
        val row = CsvWriter.writeRow(Vector("hello", "world"), CsvConfig.default)
        assertTrue(row == "hello,world\r\n")
      },
      test("field containing comma gets quoted") {
        val row = CsvWriter.writeRow(Vector("hello, world", "foo"), CsvConfig.default)
        assertTrue(row == "\"hello, world\",foo\r\n")
      },
      test("field containing double quote gets quoted with doubled quotes") {
        val row = CsvWriter.writeRow(Vector("he said \"hi\"", "bar"), CsvConfig.default)
        assertTrue(row == "\"he said \"\"hi\"\"\",bar\r\n")
      },
      test("field containing LF gets quoted") {
        val row = CsvWriter.writeRow(Vector("line1\nline2", "ok"), CsvConfig.default)
        assertTrue(row == "\"line1\nline2\",ok\r\n")
      },
      test("field containing CR gets quoted") {
        val row = CsvWriter.writeRow(Vector("line1\rline2", "ok"), CsvConfig.default)
        assertTrue(row == "\"line1\rline2\",ok\r\n")
      },
      test("field containing CRLF gets quoted") {
        val row = CsvWriter.writeRow(Vector("line1\r\nline2", "ok"), CsvConfig.default)
        assertTrue(row == "\"line1\r\nline2\",ok\r\n")
      },
      test("empty field is unquoted empty string") {
        val row = CsvWriter.writeRow(Vector("", "a"), CsvConfig.default)
        assertTrue(row == ",a\r\n")
      },
      test("field that is only a quote character") {
        val row = CsvWriter.writeRow(Vector("\""), CsvConfig.default)
        assertTrue(row == "\"\"\"\"\r\n")
      },
      test("field that is only a delimiter character") {
        val row = CsvWriter.writeRow(Vector(","), CsvConfig.default)
        assertTrue(row == "\",\"\r\n")
      },
      test("single field row") {
        val row = CsvWriter.writeRow(Vector("only"), CsvConfig.default)
        assertTrue(row == "only\r\n")
      },
      test("multiple fields without special characters") {
        val row = CsvWriter.writeRow(Vector("a", "b", "c", "d"), CsvConfig.default)
        assertTrue(row == "a,b,c,d\r\n")
      }
    ),
    suite("writeHeader")(
      test("writes header row same as data row") {
        val header = CsvWriter.writeHeader(Vector("name", "age", "city"), CsvConfig.default)
        assertTrue(header == "name,age,city\r\n")
      },
      test("header with special characters gets quoted") {
        val header = CsvWriter.writeHeader(Vector("first,name", "age"), CsvConfig.default)
        assertTrue(header == "\"first,name\",age\r\n")
      }
    ),
    suite("writeAll")(
      test("writes header followed by data rows") {
        val csv = CsvWriter.writeAll(
          Vector("name", "age"),
          List(Vector("Alice", "30"), Vector("Bob", "25")),
          CsvConfig.default
        )
        assertTrue(csv == "name,age\r\nAlice,30\r\nBob,25\r\n")
      },
      test("writeAll with no data rows produces header only") {
        val csv = CsvWriter.writeAll(
          Vector("name", "age"),
          List.empty,
          CsvConfig.default
        )
        assertTrue(csv == "name,age\r\n")
      },
      test("writeAll with special characters in data") {
        val csv = CsvWriter.writeAll(
          Vector("name", "bio"),
          List(Vector("Alice", "likes \"cats\"")),
          CsvConfig.default
        )
        assertTrue(csv == "name,bio\r\nAlice,\"likes \"\"cats\"\"\"\r\n")
      }
    ),
    suite("custom configs")(
      test("tab-separated output with CsvConfig.tsv") {
        val row = CsvWriter.writeRow(Vector("hello", "world"), CsvConfig.tsv)
        assertTrue(row == "hello\tworld\r\n")
      },
      test("tab in field gets quoted with TSV config") {
        val row = CsvWriter.writeRow(Vector("hello\tworld", "ok"), CsvConfig.tsv)
        assertTrue(row == "\"hello\tworld\"\tok\r\n")
      },
      test("custom delimiter semicolon") {
        val config = CsvConfig(delimiter = ';')
        val row    = CsvWriter.writeRow(Vector("a", "b", "c"), config)
        assertTrue(row == "a;b;c\r\n")
      },
      test("semicolon in field gets quoted with semicolon delimiter") {
        val config = CsvConfig(delimiter = ';')
        val row    = CsvWriter.writeRow(Vector("a;b", "c"), config)
        assertTrue(row == "\"a;b\";c\r\n")
      },
      test("custom line terminator LF only") {
        val config = CsvConfig(lineTerminator = "\n")
        val row    = CsvWriter.writeRow(Vector("a", "b"), config)
        assertTrue(row == "a,b\n")
      }
    )
  )
}
