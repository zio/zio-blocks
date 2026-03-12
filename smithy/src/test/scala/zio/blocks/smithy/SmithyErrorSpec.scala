package zio.blocks.smithy

import zio.test._

object SmithyErrorSpec extends ZIOSpecDefault {
  def spec = suite("SmithyError")(
    suite("construction")(
      test("creates ParseError with message, line, and column") {
        val error = SmithyError.ParseError("Unexpected token", 5, 12, None)
        assertTrue(
          error.message == "Unexpected token",
          error.line == 5,
          error.column == 12,
          error.source == None
        )
      },
      test("creates ParseError with source info") {
        val error = SmithyError.ParseError("Invalid shape", 10, 8, Some("smithy file content"))
        assertTrue(
          error.message == "Invalid shape",
          error.line == 10,
          error.column == 8,
          error.source == Some("smithy file content")
        )
      },
      test("creates ParseError with edge case line 0") {
        val error = SmithyError.ParseError("Error at start", 0, 0, None)
        assertTrue(error.line == 0, error.column == 0)
      }
    ),
    suite("formatMessage")(
      test("formats with line and column only") {
        val error = SmithyError.ParseError("Unexpected token", 5, 12, None)
        assertTrue(error.formatMessage == "Parse error at line 5, column 12: Unexpected token")
      },
      test("formats with source info included") {
        val error = SmithyError.ParseError("Invalid shape", 3, 5, Some("namespace com.example"))
        assertTrue(
          error.formatMessage == "Parse error at line 3, column 5: Invalid shape\nSource: namespace com.example"
        )
      },
      test("formats with empty source (None)") {
        val error = SmithyError.ParseError("Error message", 1, 1, None)
        assertTrue(!error.formatMessage.contains("Source:"))
      },
      test("formats with multi-line source") {
        val source = "line 1\nline 2\nline 3"
        val error  = SmithyError.ParseError("Error on line 2", 2, 4, Some(source))
        assertTrue(
          error.formatMessage.contains("Parse error at line 2, column 4:"),
          error.formatMessage.contains("Source:"),
          error.formatMessage.contains("line 1\nline 2\nline 3")
        )
      }
    ),
    suite("edge cases")(
      test("handles high line and column numbers") {
        val error = SmithyError.ParseError("Error", 999, 500, None)
        assertTrue(
          error.line == 999,
          error.column == 500,
          error.formatMessage == "Parse error at line 999, column 500: Error"
        )
      },
      test("handles empty message") {
        val error = SmithyError.ParseError("", 1, 1, None)
        assertTrue(error.formatMessage == "Parse error at line 1, column 1: ")
      }
    )
  )
}
