package zio.blocks.schema.csv

import zio.blocks.schema.SchemaBaseSpec
import zio.test._

object CsvErrorSpec extends SchemaBaseSpec {
  def spec = suite("CsvErrorSpec")(
    suite("ParseError")(
      test("creates ParseError with message, row, and column") {
        val error = CsvError.ParseError("Invalid CSV format", 1, 5)
        assertTrue(error.message == "Invalid CSV format" && error.row == 1 && error.column == 5)
      },
      test("ParseError extends Throwable with no stack trace") {
        val error = CsvError.ParseError("Test error", 2, 3)
        assertTrue(error.isInstanceOf[Throwable])
      },
      test("formatMessage produces human-readable error for ParseError") {
        val error = CsvError.ParseError("Invalid CSV format", 3, 5)
        assertTrue(error.formatMessage == "CSV error at row 3, column 5: Invalid CSV format")
      }
    ),
    suite("TypeError")(
      test("creates TypeError with message, row, column, and fieldName") {
        val error = CsvError.TypeError("Invalid integer value", 1, 2, "age")
        assertTrue(
          error.message == "Invalid integer value" &&
            error.row == 1 &&
            error.column == 2 &&
            error.fieldName == "age"
        )
      },
      test("TypeError extends Throwable with no stack trace") {
        val error = CsvError.TypeError("Type conversion failed", 2, 3, "salary")
        assertTrue(error.isInstanceOf[Throwable])
      },
      test("formatMessage produces human-readable error for TypeError") {
        val error = CsvError.TypeError("Invalid integer value", 2, 4, "age")
        assertTrue(error.formatMessage == "CSV error at row 2, column 4: Invalid integer value (field: age)")
      }
    ),
    suite("CsvError hierarchy")(
      test("ParseError is a CsvError") {
        val error: CsvError = CsvError.ParseError("test", 1, 1)
        assertTrue(error.isInstanceOf[CsvError])
      },
      test("TypeError is a CsvError") {
        val error: CsvError = CsvError.TypeError("test", 1, 1, "field")
        assertTrue(error.isInstanceOf[CsvError])
      }
    ),
    suite("Throwable behavior")(
      test("getMessage returns formatMessage") {
        val error = CsvError.ParseError("Invalid format", 5, 10)
        assertTrue(error.getMessage() == error.formatMessage)
      },
      test("CsvError with no stack trace for performance") {
        val error = CsvError.ParseError("test", 1, 1)
        assertTrue(error.getStackTrace.isEmpty)
      }
    )
  )
}
