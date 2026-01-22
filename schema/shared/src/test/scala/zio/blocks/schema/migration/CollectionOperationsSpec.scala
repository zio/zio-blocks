package zio.blocks.schema.migration

import zio.blocks.schema._
import zio.test._

/**
 * Tests for collection migration operations:
 *   - TransformElements: maps SchemaExpr over Sequence elements
 *   - TransformKeys: maps SchemaExpr over Map keys
 *   - TransformValues: maps SchemaExpr over Map values
 */
object CollectionOperationsSpec extends ZIOSpecDefault {

  def spec = suite("CollectionOperationsSpec")(
    suite("TransformElements")(
      test("transforms all elements in a sequence") {
        // Test: Increment all integers in a list [1, 2, 3] -> [2, 3, 4]
        val sequence = DynamicValue.Sequence(
          Vector(
            DynamicValue.Primitive(PrimitiveValue.Int(1)),
            DynamicValue.Primitive(PrimitiveValue.Int(2)),
            DynamicValue.Primitive(PrimitiveValue.Int(3))
          )
        )

        // Transform: add 1 to each element
        val addOne = SchemaExpr.Arithmetic(
          SchemaExpr.Dynamic[DynamicValue, Int](DynamicOptic.root),
          SchemaExpr.Literal[DynamicValue, Int](1, Schema.int),
          SchemaExpr.ArithmeticOperator.Add,
          IsNumeric.IsInt
        )

        val action = MigrationAction.TransformElements(
          at = DynamicOptic.root,
          transform = addOne
        )

        val result = action.execute(sequence)

        val expected = DynamicValue.Sequence(
          Vector(
            DynamicValue.Primitive(PrimitiveValue.Int(2)),
            DynamicValue.Primitive(PrimitiveValue.Int(3)),
            DynamicValue.Primitive(PrimitiveValue.Int(4))
          )
        )

        assertTrue(result == Right(expected))
      },
      test("transforms string elements to uppercase") {
        // Test: Uppercase all strings ["hello", "world"] -> ["HELLO", "WORLD"]
        val sequence = DynamicValue.Sequence(
          Vector(
            DynamicValue.Primitive(PrimitiveValue.String("hello")),
            DynamicValue.Primitive(PrimitiveValue.String("world"))
          )
        )

        // Transform: uppercase each string
        val uppercase = SchemaExpr.StringUppercase[DynamicValue](
          SchemaExpr.Dynamic[DynamicValue, String](DynamicOptic.root)
        )

        val action = MigrationAction.TransformElements(
          at = DynamicOptic.root,
          transform = uppercase
        )

        val result = action.execute(sequence)

        val expected = DynamicValue.Sequence(
          Vector(
            DynamicValue.Primitive(PrimitiveValue.String("HELLO")),
            DynamicValue.Primitive(PrimitiveValue.String("WORLD"))
          )
        )

        assertTrue(result == Right(expected))
      },
      test("handles empty sequence") {
        val emptySequence = DynamicValue.Sequence(Vector.empty)

        val action = MigrationAction.TransformElements(
          at = DynamicOptic.root,
          transform = SchemaExpr.Literal[DynamicValue, Int](0, Schema.int)
        )

        val result = action.execute(emptySequence)

        assertTrue(result == Right(emptySequence))
      },
      test("transforms elements in a nested field") {
        // Test: Record with a sequence field
        val record = DynamicValue.Record(
          Vector(
            "numbers" -> DynamicValue.Sequence(
              Vector(
                DynamicValue.Primitive(PrimitiveValue.Int(10)),
                DynamicValue.Primitive(PrimitiveValue.Int(20))
              )
            )
          )
        )

        val multiplyByTwo = SchemaExpr.Arithmetic(
          SchemaExpr.Dynamic[DynamicValue, Int](DynamicOptic.root),
          SchemaExpr.Literal[DynamicValue, Int](2, Schema.int),
          SchemaExpr.ArithmeticOperator.Multiply,
          IsNumeric.IsInt
        )

        val action = MigrationAction.TransformElements(
          at = DynamicOptic.root.field("numbers"),
          transform = multiplyByTwo
        )

        val result = action.execute(record)

        val expected = DynamicValue.Record(
          Vector(
            "numbers" -> DynamicValue.Sequence(
              Vector(
                DynamicValue.Primitive(PrimitiveValue.Int(20)),
                DynamicValue.Primitive(PrimitiveValue.Int(40))
              )
            )
          )
        )

        assertTrue(result == Right(expected))
      },
      test("returns error if transform fails on an element") {
        // Test: Trying to access a field on a primitive will fail
        val sequence = DynamicValue.Sequence(
          Vector(
            DynamicValue.Primitive(PrimitiveValue.Int(10)),
            DynamicValue.Primitive(PrimitiveValue.Int(5))
          )
        )

        // This will fail because we're trying to access a field on a primitive
        val invalidTransform = SchemaExpr.Dynamic[DynamicValue, Int](
          DynamicOptic.root.field("nonexistent")
        )

        val action = MigrationAction.TransformElements(
          at = DynamicOptic.root,
          transform = invalidTransform
        )

        val result = action.execute(sequence)

        assertTrue(result.isLeft)
      },
      test("returns error if applied to non-sequence") {
        val notASequence = DynamicValue.Primitive(PrimitiveValue.Int(42))

        val action = MigrationAction.TransformElements(
          at = DynamicOptic.root,
          transform = SchemaExpr.Literal[DynamicValue, Int](0, Schema.int)
        )

        val result = action.execute(notASequence)

        assertTrue(result.isLeft)
      }
    ),
    suite("TransformKeys")(
      test("transforms all keys in a map") {
        // Test: Uppercase all string keys in a map
        val map = DynamicValue.Map(
          Vector(
            (
              DynamicValue.Primitive(PrimitiveValue.String("name")),
              DynamicValue.Primitive(PrimitiveValue.String("Alice"))
            ),
            (
              DynamicValue.Primitive(PrimitiveValue.String("city")),
              DynamicValue.Primitive(PrimitiveValue.String("NYC"))
            )
          )
        )

        val uppercase = SchemaExpr.StringUppercase[DynamicValue](
          SchemaExpr.Dynamic[DynamicValue, String](DynamicOptic.root)
        )

        val action = MigrationAction.TransformKeys(
          at = DynamicOptic.root,
          transform = uppercase
        )

        val result = action.execute(map)

        val expected = DynamicValue.Map(
          Vector(
            (
              DynamicValue.Primitive(PrimitiveValue.String("NAME")),
              DynamicValue.Primitive(PrimitiveValue.String("Alice"))
            ),
            (
              DynamicValue.Primitive(PrimitiveValue.String("CITY")),
              DynamicValue.Primitive(PrimitiveValue.String("NYC"))
            )
          )
        )

        assertTrue(result == Right(expected))
      },
      test("handles empty map") {
        val emptyMap = DynamicValue.Map(Vector.empty)

        val action = MigrationAction.TransformKeys(
          at = DynamicOptic.root,
          transform = SchemaExpr.Literal[DynamicValue, String]("key", Schema.string)
        )

        val result = action.execute(emptyMap)

        assertTrue(result == Right(emptyMap))
      },
      test("transforms keys in a nested field") {
        val record = DynamicValue.Record(
          Vector(
            "metadata" -> DynamicValue.Map(
              Vector(
                (
                  DynamicValue.Primitive(PrimitiveValue.String("version")),
                  DynamicValue.Primitive(PrimitiveValue.Int(1))
                )
              )
            )
          )
        )

        val uppercase = SchemaExpr.StringUppercase[DynamicValue](
          SchemaExpr.Dynamic[DynamicValue, String](DynamicOptic.root)
        )

        val action = MigrationAction.TransformKeys(
          at = DynamicOptic.root.field("metadata"),
          transform = uppercase
        )

        val result = action.execute(record)

        val expected = DynamicValue.Record(
          Vector(
            "metadata" -> DynamicValue.Map(
              Vector(
                (
                  DynamicValue.Primitive(PrimitiveValue.String("VERSION")),
                  DynamicValue.Primitive(PrimitiveValue.Int(1))
                )
              )
            )
          )
        )

        assertTrue(result == Right(expected))
      },
      test("returns error if applied to non-map") {
        val notAMap = DynamicValue.Primitive(PrimitiveValue.String("not a map"))

        val action = MigrationAction.TransformKeys(
          at = DynamicOptic.root,
          transform = SchemaExpr.Literal[DynamicValue, String]("key", Schema.string)
        )

        val result = action.execute(notAMap)

        assertTrue(result.isLeft)
      }
    ),
    suite("TransformValues")(
      test("transforms all values in a map") {
        // Test: Increment all integer values in a map
        val map = DynamicValue.Map(
          Vector(
            (
              DynamicValue.Primitive(PrimitiveValue.String("a")),
              DynamicValue.Primitive(PrimitiveValue.Int(1))
            ),
            (
              DynamicValue.Primitive(PrimitiveValue.String("b")),
              DynamicValue.Primitive(PrimitiveValue.Int(2))
            )
          )
        )

        val addOne = SchemaExpr.Arithmetic(
          SchemaExpr.Dynamic[DynamicValue, Int](DynamicOptic.root),
          SchemaExpr.Literal[DynamicValue, Int](1, Schema.int),
          SchemaExpr.ArithmeticOperator.Add,
          IsNumeric.IsInt
        )

        val action = MigrationAction.TransformValues(
          at = DynamicOptic.root,
          transform = addOne
        )

        val result = action.execute(map)

        val expected = DynamicValue.Map(
          Vector(
            (
              DynamicValue.Primitive(PrimitiveValue.String("a")),
              DynamicValue.Primitive(PrimitiveValue.Int(2))
            ),
            (
              DynamicValue.Primitive(PrimitiveValue.String("b")),
              DynamicValue.Primitive(PrimitiveValue.Int(3))
            )
          )
        )

        assertTrue(result == Right(expected))
      },
      test("transforms string values to lowercase") {
        val map = DynamicValue.Map(
          Vector(
            (
              DynamicValue.Primitive(PrimitiveValue.String("greeting")),
              DynamicValue.Primitive(PrimitiveValue.String("HELLO"))
            ),
            (
              DynamicValue.Primitive(PrimitiveValue.String("farewell")),
              DynamicValue.Primitive(PrimitiveValue.String("GOODBYE"))
            )
          )
        )

        val lowercase = SchemaExpr.StringLowercase[DynamicValue](
          SchemaExpr.Dynamic[DynamicValue, String](DynamicOptic.root)
        )

        val action = MigrationAction.TransformValues(
          at = DynamicOptic.root,
          transform = lowercase
        )

        val result = action.execute(map)

        val expected = DynamicValue.Map(
          Vector(
            (
              DynamicValue.Primitive(PrimitiveValue.String("greeting")),
              DynamicValue.Primitive(PrimitiveValue.String("hello"))
            ),
            (
              DynamicValue.Primitive(PrimitiveValue.String("farewell")),
              DynamicValue.Primitive(PrimitiveValue.String("goodbye"))
            )
          )
        )

        assertTrue(result == Right(expected))
      },
      test("handles empty map") {
        val emptyMap = DynamicValue.Map(Vector.empty)

        val action = MigrationAction.TransformValues(
          at = DynamicOptic.root,
          transform = SchemaExpr.Literal[DynamicValue, Int](0, Schema.int)
        )

        val result = action.execute(emptyMap)

        assertTrue(result == Right(emptyMap))
      },
      test("transforms values in a nested field") {
        val record = DynamicValue.Record(
          Vector(
            "scores" -> DynamicValue.Map(
              Vector(
                (
                  DynamicValue.Primitive(PrimitiveValue.String("math")),
                  DynamicValue.Primitive(PrimitiveValue.Int(85))
                ),
                (
                  DynamicValue.Primitive(PrimitiveValue.String("science")),
                  DynamicValue.Primitive(PrimitiveValue.Int(90))
                )
              )
            )
          )
        )

        val addBonus = SchemaExpr.Arithmetic(
          SchemaExpr.Dynamic[DynamicValue, Int](DynamicOptic.root),
          SchemaExpr.Literal[DynamicValue, Int](5, Schema.int),
          SchemaExpr.ArithmeticOperator.Add,
          IsNumeric.IsInt
        )

        val action = MigrationAction.TransformValues(
          at = DynamicOptic.root.field("scores"),
          transform = addBonus
        )

        val result = action.execute(record)

        val expected = DynamicValue.Record(
          Vector(
            "scores" -> DynamicValue.Map(
              Vector(
                (
                  DynamicValue.Primitive(PrimitiveValue.String("math")),
                  DynamicValue.Primitive(PrimitiveValue.Int(90))
                ),
                (
                  DynamicValue.Primitive(PrimitiveValue.String("science")),
                  DynamicValue.Primitive(PrimitiveValue.Int(95))
                )
              )
            )
          )
        )

        assertTrue(result == Right(expected))
      },
      test("returns error if applied to non-map") {
        val notAMap = DynamicValue.Sequence(Vector.empty)

        val action = MigrationAction.TransformValues(
          at = DynamicOptic.root,
          transform = SchemaExpr.Literal[DynamicValue, Int](0, Schema.int)
        )

        val result = action.execute(notAMap)

        assertTrue(result.isLeft)
      }
    ),
    suite("Reverse operations")(
      test("TransformElements.reverse is itself") {
        val transform = SchemaExpr.Literal[DynamicValue, Int](0, Schema.int)
        val action    = MigrationAction.TransformElements(DynamicOptic.root, transform)

        assertTrue(action.reverse == action)
      },
      test("TransformKeys.reverse is itself") {
        val transform = SchemaExpr.Literal[DynamicValue, String]("key", Schema.string)
        val action    = MigrationAction.TransformKeys(DynamicOptic.root, transform)

        assertTrue(action.reverse == action)
      },
      test("TransformValues.reverse is itself") {
        val transform = SchemaExpr.Literal[DynamicValue, Int](0, Schema.int)
        val action    = MigrationAction.TransformValues(DynamicOptic.root, transform)

        assertTrue(action.reverse == action)
      }
    )
  )
}
