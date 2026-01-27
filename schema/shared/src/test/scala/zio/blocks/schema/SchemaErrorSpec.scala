package zio.blocks.schema

import zio.test._

/**
 * Tests for SchemaError to increase branch coverage. Specifically tests:
 *   - ConversionFailed.message with various cause structures
 *   - prependPath for all Single error types
 *   - Error combination via ++
 */
object SchemaErrorSpec extends SchemaBaseSpec {
  def spec: Spec[Any, Any] = suite("SchemaErrorSpec")(
    suite("ConversionFailed message formatting")(
      test("message without cause returns details only") {
        val error = SchemaError.conversionFailed(Nil, "conversion failed")
        assertTrue(error.message == "conversion failed")
      },
      test("message with single-error cause shows 'Caused by'") {
        val inner = SchemaError.conversionFailed(Nil, "inner error")
        val outer = SchemaError.conversionFailed("outer context", inner)
        assertTrue(
          outer.message.contains("outer context"),
          outer.message.contains("Caused by:"),
          outer.message.contains("inner error")
        )
      },
      test("message with multi-error cause shows all errors") {
        val inner1   = SchemaError.missingField(Nil, "field1")
        val inner2   = SchemaError.missingField(Nil, "field2")
        val combined = inner1 ++ inner2
        val outer    = SchemaError.conversionFailed("outer context", combined)
        assertTrue(
          outer.message.contains("outer context"),
          outer.message.contains("field1"),
          outer.message.contains("field2")
        )
      },
      test("getMessage returns same as message") {
        val error = SchemaError.conversionFailed(Nil, "test details")
        assertTrue(error.getMessage == error.message)
      }
    ),
    suite("prependPath for all error types")(
      test("prependPath with empty trace returns same error") {
        val error     = SchemaError.conversionFailed(Nil, "test")
        val prepended = error.prependPath(Nil)
        assertTrue(prepended eq error)
      },
      test("prependPath for ConversionFailed") {
        val error     = SchemaError.conversionFailed(Nil, "test")
        val prepended = error.prependPath(List(DynamicOptic.Node.Field("foo")))
        assertTrue(prepended.errors.head.source.nodes.nonEmpty)
      },
      test("prependPath for ExpectationMismatch") {
        val error     = SchemaError.expectationMismatch(Nil, "expected X")
        val prepended = error.prependPath(List(DynamicOptic.Node.Field("bar")))
        assertTrue(
          prepended.errors.head.source.nodes.nonEmpty,
          prepended.errors.head.message.contains("expected X")
        )
      },
      test("prependPath for MissingField") {
        val error     = SchemaError.missingField(Nil, "requiredField")
        val prepended = error.prependPath(List(DynamicOptic.Node.Field("parent")))
        assertTrue(
          prepended.errors.head.source.nodes.nonEmpty,
          prepended.errors.head.message.contains("requiredField")
        )
      },
      test("prependPath for DuplicatedField") {
        val error     = SchemaError.duplicatedField(Nil, "dupField")
        val prepended = error.prependPath(List(DynamicOptic.Node.Case("Variant")))
        assertTrue(
          prepended.errors.head.source.nodes.nonEmpty,
          prepended.errors.head.message.contains("dupField")
        )
      },
      test("prependPath for UnknownCase") {
        val error     = SchemaError.unknownCase(Nil, "BadCase")
        val prepended = error.prependPath(List(DynamicOptic.Node.AtIndex(0)))
        assertTrue(
          prepended.errors.head.source.nodes.nonEmpty,
          prepended.errors.head.message.contains("BadCase")
        )
      }
    ),
    suite("error combination")(
      test("combining two errors preserves both") {
        val error1   = SchemaError.missingField(Nil, "a")
        val error2   = SchemaError.missingField(Nil, "b")
        val combined = error1 ++ error2
        assertTrue(
          combined.errors.length == 2,
          combined.message.contains("a"),
          combined.message.contains("b")
        )
      },
      test("combined errors message contains newlines") {
        val error1   = SchemaError.missingField(Nil, "first")
        val error2   = SchemaError.missingField(Nil, "second")
        val combined = error1 ++ error2
        assertTrue(combined.message.contains("\n"))
      }
    ),
    suite("error message construction")(
      test("MissingField message includes field name and path") {
        val error = SchemaError.missingField(
          List(DynamicOptic.Node.Field("nested")),
          "targetField"
        )
        assertTrue(
          error.message.contains("targetField"),
          error.message.contains("Missing field")
        )
      },
      test("DuplicatedField message includes field name") {
        val error = SchemaError.duplicatedField(Nil, "myField")
        assertTrue(
          error.message.contains("myField"),
          error.message.contains("Duplicated field")
        )
      },
      test("ExpectationMismatch message includes expectation") {
        val error = SchemaError.expectationMismatch(Nil, "Expected Int but got String")
        assertTrue(error.message.contains("Expected Int but got String"))
      },
      test("UnknownCase message includes case name") {
        val error = SchemaError.unknownCase(Nil, "UnknownVariant")
        assertTrue(
          error.message.contains("UnknownVariant"),
          error.message.contains("Unknown case")
        )
      }
    ),
    suite("validationFailed")(
      test("validationFailed creates ConversionFailed at root") {
        val error = SchemaError.validationFailed("must be positive")
        assertTrue(
          error.message.contains("must be positive"),
          error.errors.head.isInstanceOf[SchemaError.ConversionFailed]
        )
      }
    ),
    suite("errors collection")(
      test("single error has size 1") {
        val error = SchemaError.missingField(Nil, "field")
        assertTrue(error.errors.size == 1)
      },
      test("combined error has size equal to sum") {
        val e1       = SchemaError.missingField(Nil, "a")
        val e2       = SchemaError.missingField(Nil, "b")
        val e3       = SchemaError.missingField(Nil, "c")
        val combined = e1 ++ e2 ++ e3
        assertTrue(combined.errors.size == 3)
      }
    ),
    suite("error source paths")(
      test("error at root has empty source") {
        val error = SchemaError.conversionFailed(Nil, "test")
        assertTrue(error.errors.head.source.nodes.isEmpty)
      },
      test("error at nested path has non-empty source") {
        val path = List(
          DynamicOptic.Node.Field("outer"),
          DynamicOptic.Node.Field("inner")
        )
        val error = SchemaError.conversionFailed(path, "test")
        assertTrue(error.errors.head.source.nodes.size == 2)
      },
      test("prependPath with multiple nodes") {
        val error = SchemaError.conversionFailed(Nil, "test")
        val path  = List(
          DynamicOptic.Node.Field("a"),
          DynamicOptic.Node.AtIndex(0),
          DynamicOptic.Node.Field("b")
        )
        val prepended = error.prependPath(path)
        assertTrue(prepended.errors.head.source.nodes.size == 3)
      }
    ),
    suite("error type inspection")(
      test("ConversionFailed is correctly typed") {
        val error = SchemaError.conversionFailed(Nil, "test")
        assertTrue(error.errors.head.isInstanceOf[SchemaError.ConversionFailed])
      },
      test("MissingField is correctly typed") {
        val error = SchemaError.missingField(Nil, "field")
        assertTrue(error.errors.head.isInstanceOf[SchemaError.MissingField])
      },
      test("DuplicatedField is correctly typed") {
        val error = SchemaError.duplicatedField(Nil, "field")
        assertTrue(error.errors.head.isInstanceOf[SchemaError.DuplicatedField])
      },
      test("UnknownCase is correctly typed") {
        val error = SchemaError.unknownCase(Nil, "Case")
        assertTrue(error.errors.head.isInstanceOf[SchemaError.UnknownCase])
      },
      test("ExpectationMismatch is correctly typed") {
        val error = SchemaError.expectationMismatch(Nil, "expected X")
        assertTrue(error.errors.head.isInstanceOf[SchemaError.ExpectationMismatch])
      }
    ),
    suite("error throwable behavior")(
      test("SchemaError extends Throwable") {
        val error: Throwable = SchemaError.conversionFailed(Nil, "test")
        assertTrue(error.isInstanceOf[Throwable])
      },
      test("getMessage returns message") {
        val error = SchemaError.missingField(Nil, "testField")
        assertTrue(error.getMessage.contains("testField"))
      },
      test("error can be thrown and caught") {
        val error  = SchemaError.conversionFailed(Nil, "intentional")
        val caught = try {
          throw error
        } catch {
          case e: SchemaError => e
        }
        assertTrue(caught.message.contains("intentional"))
      }
    ),
    suite("complex nested error scenarios")(
      test("deeply nested cause chain") {
        val inner1 = SchemaError.conversionFailed(Nil, "level 3")
        val inner2 = SchemaError.conversionFailed("level 2", inner1)
        val outer  = SchemaError.conversionFailed("level 1", inner2)
        assertTrue(
          outer.message.contains("level 1"),
          outer.message.contains("level 2"),
          outer.message.contains("level 3")
        )
      },
      test("combining errors from different paths") {
        val e1       = SchemaError.missingField(List(DynamicOptic.Node.Field("users")), "name")
        val e2       = SchemaError.missingField(List(DynamicOptic.Node.Field("posts")), "title")
        val combined = e1 ++ e2
        assertTrue(
          combined.message.contains("name"),
          combined.message.contains("title")
        )
      }
    ),
    suite("source DynamicOptic")(
      test("source returns correct optic for Field") {
        val error = SchemaError.conversionFailed(
          List(DynamicOptic.Node.Field("test")),
          "error"
        )
        assertTrue(error.errors.head.source.nodes.head == DynamicOptic.Node.Field("test"))
      },
      test("source returns correct optic for AtIndex") {
        val error = SchemaError.conversionFailed(
          List(DynamicOptic.Node.AtIndex(5)),
          "error"
        )
        assertTrue(error.errors.head.source.nodes.head == DynamicOptic.Node.AtIndex(5))
      },
      test("source returns correct optic for Case") {
        val error = SchemaError.conversionFailed(
          List(DynamicOptic.Node.Case("SomeCase")),
          "error"
        )
        assertTrue(error.errors.head.source.nodes.head == DynamicOptic.Node.Case("SomeCase"))
      }
    )
  )
}
