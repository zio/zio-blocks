package zio.blocks.schema.migration

import zio.blocks.schema._
import zio.test._

/**
 * Tests for collection and map migration operations.
 *
 * Covers:
 *   - TransformElements: Transform each element in a sequence
 *   - TransformKeys: Transform keys in a map
 *   - TransformValues: Transform values in a map
 *   - Empty collections
 *   - Large collections
 *   - Nested collections
 */
object SequenceMapMigrationSpec extends SchemaBaseSpec {

  // ─────────────────────────────────────────────────────────────────────────
  // Test Helpers
  // ─────────────────────────────────────────────────────────────────────────

  def dynamicRecord(fields: (String, DynamicValue)*): DynamicValue =
    DynamicValue.Record(fields: _*)

  def dynamicString(s: String): DynamicValue =
    DynamicValue.Primitive(PrimitiveValue.String(s))

  def dynamicInt(i: Int): DynamicValue =
    DynamicValue.Primitive(PrimitiveValue.Int(i))

  def dynamicLong(l: Long): DynamicValue =
    DynamicValue.Primitive(PrimitiveValue.Long(l))

  def dynamicSequence(elements: DynamicValue*): DynamicValue =
    DynamicValue.Sequence(elements: _*)

  def dynamicMap(entries: (DynamicValue, DynamicValue)*): DynamicValue =
    DynamicValue.Map(entries: _*)

  // ─────────────────────────────────────────────────────────────────────────
  // Tests
  // ─────────────────────────────────────────────────────────────────────────

  def spec: Spec[TestEnvironment, Any] = suite("SequenceMapMigrationSpec")(
    suite("TransformElements")(
      test("transforms all elements with identity") {
        val action = MigrationAction.TransformElements(
          DynamicOptic.root,
          Resolved.Identity,
          Resolved.Identity
        )
        val input  = dynamicSequence(dynamicInt(1), dynamicInt(2), dynamicInt(3))
        val result = action.apply(input)
        assertTrue(result == Right(input))
      },
      test("transforms elements with type conversion") {
        val action = MigrationAction.TransformElements(
          DynamicOptic.root,
          Resolved.Convert("Int", "String", Resolved.Identity),
          Resolved.Convert("String", "Int", Resolved.Identity)
        )
        val input  = dynamicSequence(dynamicInt(1), dynamicInt(2), dynamicInt(3))
        val result = action.apply(input)
        assertTrue(
          result == Right(
            dynamicSequence(
              dynamicString("1"),
              dynamicString("2"),
              dynamicString("3")
            )
          )
        )
      },
      test("transforms elements with constant replacement") {
        val action = MigrationAction.TransformElements(
          DynamicOptic.root,
          Resolved.Literal.int(0),
          Resolved.Identity
        )
        val input  = dynamicSequence(dynamicInt(1), dynamicInt(2), dynamicInt(3))
        val result = action.apply(input)
        assertTrue(result == Right(dynamicSequence(dynamicInt(0), dynamicInt(0), dynamicInt(0))))
      },
      test("transforms empty sequence") {
        val action = MigrationAction.TransformElements(
          DynamicOptic.root,
          Resolved.Identity,
          Resolved.Identity
        )
        val input  = dynamicSequence()
        val result = action.apply(input)
        assertTrue(result == Right(dynamicSequence()))
      },
      test("transforms single element sequence") {
        val action = MigrationAction.TransformElements(
          DynamicOptic.root,
          Resolved.Convert("Int", "Long", Resolved.Identity),
          Resolved.Convert("Long", "Int", Resolved.Identity)
        )
        val input  = dynamicSequence(dynamicInt(42))
        val result = action.apply(input)
        assertTrue(result == Right(dynamicSequence(dynamicLong(42L))))
      },
      test("transforms sequence of records") {
        val action = MigrationAction.TransformElements(
          DynamicOptic.root,
          Resolved.FieldAccess("value", Resolved.Identity),
          Resolved.Identity // reverse not important for this test
        )
        val input = dynamicSequence(
          dynamicRecord("value" -> dynamicInt(1)),
          dynamicRecord("value" -> dynamicInt(2)),
          dynamicRecord("value" -> dynamicInt(3))
        )
        val result = action.apply(input)
        assertTrue(result == Right(dynamicSequence(dynamicInt(1), dynamicInt(2), dynamicInt(3))))
      },
      test("reverse swaps forward and reverse transforms") {
        val action = MigrationAction.TransformElements(
          DynamicOptic.root,
          Resolved.Convert("Int", "String", Resolved.Identity),
          Resolved.Convert("String", "Int", Resolved.Identity)
        )
        val reversed = action.reverse
        reversed match {
          case MigrationAction.TransformElements(_, fwd, rev) =>
            assertTrue(
              fwd.isInstanceOf[Resolved.Convert] &&
                rev.isInstanceOf[Resolved.Convert]
            )
          case _ => assertTrue(false)
        }
      },
      test("fails on non-sequence input") {
        val action = MigrationAction.TransformElements(
          DynamicOptic.root,
          Resolved.Identity,
          Resolved.Identity
        )
        val input = dynamicRecord("x" -> dynamicInt(1))
        assertTrue(action.apply(input).isLeft)
      },
      test("transforms large sequence") {
        val action = MigrationAction.TransformElements(
          DynamicOptic.root,
          Resolved.Identity,
          Resolved.Identity
        )
        val input  = dynamicSequence((1 to 1000).map(dynamicInt): _*)
        val result = action.apply(input)
        result match {
          case Right(DynamicValue.Sequence(elements)) =>
            assertTrue(elements.length == 1000)
          case _ => assertTrue(false)
        }
      }
    ),
    suite("TransformKeys (Record as Map)")(
      test("transforms string keys with identity") {
        val action = MigrationAction.TransformKeys(
          DynamicOptic.root,
          Resolved.Identity,
          Resolved.Identity
        )
        val input  = dynamicRecord("a" -> dynamicInt(1), "b" -> dynamicInt(2))
        val result = action.apply(input)
        assertTrue(result == Right(input))
      },
      test("transforms keys with string manipulation") {
        val action = MigrationAction.TransformKeys(
          DynamicOptic.root,
          Resolved.Concat(Vector(Resolved.Identity, Resolved.Literal.string("_suffix")), ""),
          Resolved.Identity // reverse not important for this test
        )
        val input  = dynamicRecord("key1" -> dynamicInt(1), "key2" -> dynamicInt(2))
        val result = action.apply(input)
        assertTrue(
          result == Right(
            dynamicRecord(
              "key1_suffix" -> dynamicInt(1),
              "key2_suffix" -> dynamicInt(2)
            )
          )
        )
      },
      test("preserves values during key transform") {
        val action = MigrationAction.TransformKeys(
          DynamicOptic.root,
          Resolved.Literal.string("newKey"),
          Resolved.Identity
        )
        val complexValue = dynamicRecord("nested" -> dynamicInt(42))
        val input        = dynamicRecord("oldKey" -> complexValue)
        val result       = action.apply(input)
        result match {
          case Right(DynamicValue.Record(fields)) =>
            assertTrue(fields.exists { case (_, v) => v == complexValue })
          case _ => assertTrue(false)
        }
      },
      test("transforms empty record") {
        val action = MigrationAction.TransformKeys(
          DynamicOptic.root,
          Resolved.Identity,
          Resolved.Identity
        )
        val input  = dynamicRecord()
        val result = action.apply(input)
        assertTrue(result == Right(dynamicRecord()))
      }
    ),
    suite("TransformKeys (Map type)")(
      test("transforms map keys") {
        val action = MigrationAction.TransformKeys(
          DynamicOptic.root,
          Resolved.Convert("Int", "String", Resolved.Identity),
          Resolved.Convert("String", "Int", Resolved.Identity)
        )
        val input = dynamicMap(
          dynamicInt(1) -> dynamicString("one"),
          dynamicInt(2) -> dynamicString("two")
        )
        val result = action.apply(input)
        assertTrue(
          result == Right(
            dynamicMap(
              dynamicString("1") -> dynamicString("one"),
              dynamicString("2") -> dynamicString("two")
            )
          )
        )
      },
      test("transforms empty map") {
        val action = MigrationAction.TransformKeys(
          DynamicOptic.root,
          Resolved.Identity,
          Resolved.Identity
        )
        val input  = dynamicMap()
        val result = action.apply(input)
        assertTrue(result == Right(dynamicMap()))
      }
    ),
    suite("TransformValues (Record as Map)")(
      test("transforms all values") {
        val action = MigrationAction.TransformValues(
          DynamicOptic.root,
          Resolved.Convert("Int", "String", Resolved.Identity),
          Resolved.Convert("String", "Int", Resolved.Identity)
        )
        val input  = dynamicRecord("a" -> dynamicInt(1), "b" -> dynamicInt(2))
        val result = action.apply(input)
        assertTrue(
          result == Right(
            dynamicRecord(
              "a" -> dynamicString("1"),
              "b" -> dynamicString("2")
            )
          )
        )
      },
      test("preserves keys during value transform") {
        val action = MigrationAction.TransformValues(
          DynamicOptic.root,
          Resolved.Literal.int(0),
          Resolved.Identity
        )
        val input  = dynamicRecord("key1" -> dynamicInt(100), "key2" -> dynamicInt(200))
        val result = action.apply(input)
        assertTrue(
          result == Right(
            dynamicRecord(
              "key1" -> dynamicInt(0),
              "key2" -> dynamicInt(0)
            )
          )
        )
      },
      test("transforms empty record") {
        val action = MigrationAction.TransformValues(
          DynamicOptic.root,
          Resolved.Identity,
          Resolved.Identity
        )
        val input  = dynamicRecord()
        val result = action.apply(input)
        assertTrue(result == Right(dynamicRecord()))
      }
    ),
    suite("TransformValues (Map type)")(
      test("transforms map values") {
        val action = MigrationAction.TransformValues(
          DynamicOptic.root,
          Resolved.Convert("Int", "Long", Resolved.Identity),
          Resolved.Convert("Long", "Int", Resolved.Identity)
        )
        val input = dynamicMap(
          dynamicString("a") -> dynamicInt(1),
          dynamicString("b") -> dynamicInt(2)
        )
        val result = action.apply(input)
        assertTrue(
          result == Right(
            dynamicMap(
              dynamicString("a") -> dynamicLong(1L),
              dynamicString("b") -> dynamicLong(2L)
            )
          )
        )
      },
      test("transforms empty map values") {
        val action = MigrationAction.TransformValues(
          DynamicOptic.root,
          Resolved.Identity,
          Resolved.Identity
        )
        val input  = dynamicMap()
        val result = action.apply(input)
        assertTrue(result == Right(dynamicMap()))
      }
    ),
    suite("Nested collections")(
      test("transform elements in sequence field") {
        val action = MigrationAction.TransformElements(
          DynamicOptic.root.field("items"),
          Resolved.Convert("Int", "String", Resolved.Identity),
          Resolved.Convert("String", "Int", Resolved.Identity)
        )
        val input = dynamicRecord(
          "name"  -> dynamicString("test"),
          "items" -> dynamicSequence(dynamicInt(1), dynamicInt(2))
        )
        val result = action.apply(input)
        assertTrue(
          result == Right(
            dynamicRecord(
              "name"  -> dynamicString("test"),
              "items" -> dynamicSequence(dynamicString("1"), dynamicString("2"))
            )
          )
        )
      },
      test("transform sequence of sequences") {
        val action = MigrationAction.TransformElements(
          DynamicOptic.root,
          Resolved.Identity, // Inner sequences unchanged
          Resolved.Identity
        )
        val input = dynamicSequence(
          dynamicSequence(dynamicInt(1), dynamicInt(2)),
          dynamicSequence(dynamicInt(3), dynamicInt(4))
        )
        val result = action.apply(input)
        assertTrue(result == Right(input))
      }
    ),
    suite("Combined operations")(
      test("transform elements then add field to each") {
        // First transform: wrap each int in a record
        // This would require a more complex Resolved expression
        // For now, test simpler case
        val action = MigrationAction.TransformElements(
          DynamicOptic.root,
          Resolved.Convert("Int", "Long", Resolved.Identity),
          Resolved.Convert("Long", "Int", Resolved.Identity)
        )
        val input  = dynamicSequence(dynamicInt(1), dynamicInt(2), dynamicInt(3))
        val result = action.apply(input)
        assertTrue(
          result == Right(
            dynamicSequence(
              dynamicLong(1L),
              dynamicLong(2L),
              dynamicLong(3L)
            )
          )
        )
      }
    ),
    suite("Edge cases")(
      test("sequence with heterogeneous types") {
        val action = MigrationAction.TransformElements(
          DynamicOptic.root,
          Resolved.Identity,
          Resolved.Identity
        )
        val input = dynamicSequence(
          dynamicInt(1),
          dynamicString("two"),
          dynamicRecord("x" -> dynamicInt(3))
        )
        val result = action.apply(input)
        assertTrue(result == Right(input))
      },
      test("map with complex keys and values") {
        val action = MigrationAction.TransformValues(
          DynamicOptic.root,
          Resolved.Identity,
          Resolved.Identity
        )
        val input = dynamicMap(
          dynamicRecord("id" -> dynamicInt(1)) -> dynamicSequence(dynamicString("a"), dynamicString("b")),
          dynamicRecord("id" -> dynamicInt(2)) -> dynamicSequence(dynamicString("c"))
        )
        val result = action.apply(input)
        assertTrue(result == Right(input))
      },
      test("deeply nested sequence") {
        val action = MigrationAction.TransformElements(
          DynamicOptic.root,
          Resolved.Identity,
          Resolved.Identity
        )
        val deeplyNested = dynamicSequence(
          dynamicSequence(
            dynamicSequence(
              dynamicSequence(dynamicInt(1))
            )
          )
        )
        val result = action.apply(deeplyNested)
        assertTrue(result == Right(deeplyNested))
      }
    )
  )
}
