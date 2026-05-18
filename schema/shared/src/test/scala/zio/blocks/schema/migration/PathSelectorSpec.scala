package zio.blocks.schema.migration

import zio.blocks.schema._
import zio.test._

/**
 * Tests for DynamicOptic path selectors.
 *
 * Covers:
 *   - Root path
 *   - Field navigation
 *   - Element access
 *   - Variant case access
 *   - Path composition
 *   - Path display/toString
 *   - Modify operations at paths
 */
object PathSelectorSpec extends SchemaBaseSpec {

  // ─────────────────────────────────────────────────────────────────────────
  // Test Helpers
  // ─────────────────────────────────────────────────────────────────────────

  def dynamicRecord(fields: (String, DynamicValue)*): DynamicValue =
    DynamicValue.Record(fields: _*)

  def dynamicString(s: String): DynamicValue =
    DynamicValue.Primitive(PrimitiveValue.String(s))

  def dynamicInt(i: Int): DynamicValue =
    DynamicValue.Primitive(PrimitiveValue.Int(i))

  def dynamicSequence(elements: DynamicValue*): DynamicValue =
    DynamicValue.Sequence(elements: _*)

  def dynamicVariant(caseName: String, value: DynamicValue): DynamicValue =
    DynamicValue.Variant(caseName, value)

  def dynamicMap(entries: (DynamicValue, DynamicValue)*): DynamicValue =
    DynamicValue.Map(entries: _*)

  // ─────────────────────────────────────────────────────────────────────────
  // Tests
  // ─────────────────────────────────────────────────────────────────────────

  def spec: Spec[TestEnvironment, Any] = suite("PathSelectorSpec")(
    suite("Root path")(
      test("root path is identity") {
        val path   = DynamicOptic.root
        val input  = dynamicRecord("x" -> dynamicInt(1))
        val result = input.get(path).one
        assertTrue(result == Right(input))
      },
      test("root path modify replaces entire value") {
        val path   = DynamicOptic.root
        val input  = dynamicInt(1)
        val result = input.modify(path)(_ => dynamicInt(99))
        assertTrue(result == dynamicInt(99))
      },
      test("root path set replaces value") {
        val path   = DynamicOptic.root
        val input  = dynamicString("old")
        val result = input.set(path, dynamicString("new"))
        assertTrue(result == dynamicString("new"))
      }
    ),
    suite("Field path")(
      test("field path accesses record field") {
        val path   = DynamicOptic.root.field("name")
        val input  = dynamicRecord("name" -> dynamicString("Alice"), "age" -> dynamicInt(30))
        val result = input.get(path).one
        assertTrue(result == Right(dynamicString("Alice")))
      },
      test("field path modifies record field") {
        val path   = DynamicOptic.root.field("name")
        val input  = dynamicRecord("name" -> dynamicString("Alice"), "age" -> dynamicInt(30))
        val result = input.modify(path)(_ => dynamicString("Bob"))
        assertTrue(
          result == dynamicRecord(
            "name" -> dynamicString("Bob"),
            "age"  -> dynamicInt(30)
          )
        )
      },
      test("field path returns error for missing field") {
        val path   = DynamicOptic.root.field("missing")
        val input  = dynamicRecord("other" -> dynamicInt(1))
        val result = input.get(path).one
        assertTrue(result.isLeft)
      },
      test("field path returns error for non-record") {
        val path   = DynamicOptic.root.field("name")
        val input  = dynamicInt(42)
        val result = input.get(path).one
        assertTrue(result.isLeft)
      }
    ),
    suite("Nested field paths")(
      test("two-level field path") {
        val path  = DynamicOptic.root.field("address").field("city")
        val input = dynamicRecord(
          "name"    -> dynamicString("Alice"),
          "address" -> dynamicRecord("city" -> dynamicString("Boston"))
        )
        val result = input.get(path).one
        assertTrue(result == Right(dynamicString("Boston")))
      },
      test("two-level field path modify") {
        val path  = DynamicOptic.root.field("address").field("city")
        val input = dynamicRecord(
          "name"    -> dynamicString("Alice"),
          "address" -> dynamicRecord(
            "city" -> dynamicString("Boston"),
            "zip"  -> dynamicString("02101")
          )
        )
        val result = input.modify(path)(_ => dynamicString("NYC"))
        assertTrue(
          result == dynamicRecord(
            "name"    -> dynamicString("Alice"),
            "address" -> dynamicRecord(
              "city" -> dynamicString("NYC"),
              "zip"  -> dynamicString("02101")
            )
          )
        )
      },
      test("three-level field path") {
        val path  = DynamicOptic.root.field("a").field("b").field("c")
        val input = dynamicRecord(
          "a" -> dynamicRecord(
            "b" -> dynamicRecord(
              "c" -> dynamicInt(42)
            )
          )
        )
        val result = input.get(path).one
        assertTrue(result == Right(dynamicInt(42)))
      },
      test("deep path preserves siblings") {
        val path  = DynamicOptic.root.field("a").field("b")
        val input = dynamicRecord(
          "a" -> dynamicRecord(
            "b" -> dynamicInt(1),
            "c" -> dynamicInt(2)
          ),
          "d" -> dynamicInt(3)
        )
        val result = input.modify(path)(_ => dynamicInt(99))
        assertTrue(
          result == dynamicRecord(
            "a" -> dynamicRecord(
              "b" -> dynamicInt(99),
              "c" -> dynamicInt(2)
            ),
            "d" -> dynamicInt(3)
          )
        )
      }
    ),
    suite("Element path")(
      test("element path accesses sequence element") {
        val path   = DynamicOptic.root.at(1)
        val input  = dynamicSequence(dynamicInt(10), dynamicInt(20), dynamicInt(30))
        val result = input.get(path).one
        assertTrue(result == Right(dynamicInt(20)))
      },
      test("element path modifies sequence element") {
        val path   = DynamicOptic.root.at(1)
        val input  = dynamicSequence(dynamicInt(10), dynamicInt(20), dynamicInt(30))
        val result = input.modify(path)(_ => dynamicInt(99))
        assertTrue(result == dynamicSequence(dynamicInt(10), dynamicInt(99), dynamicInt(30)))
      },
      test("element path returns error for out of bounds") {
        val path   = DynamicOptic.root.at(10)
        val input  = dynamicSequence(dynamicInt(1), dynamicInt(2))
        val result = input.get(path).one
        assertTrue(result.isLeft)
      },
      test("element path returns error for non-sequence") {
        val path   = DynamicOptic.root.at(0)
        val input  = dynamicRecord("x" -> dynamicInt(1))
        val result = input.get(path).one
        assertTrue(result.isLeft)
      },
      test("first element (index 0)") {
        val path   = DynamicOptic.root.at(0)
        val input  = dynamicSequence(dynamicInt(1), dynamicInt(2))
        val result = input.get(path).one
        assertTrue(result == Right(dynamicInt(1)))
      },
      test("last element") {
        val path   = DynamicOptic.root.at(2)
        val input  = dynamicSequence(dynamicInt(1), dynamicInt(2), dynamicInt(3))
        val result = input.get(path).one
        assertTrue(result == Right(dynamicInt(3)))
      }
    ),
    suite("Case path")(
      test("case path accesses variant case value") {
        val path   = DynamicOptic.root.caseOf("Some")
        val input  = dynamicVariant("Some", dynamicInt(42))
        val result = input.get(path).one
        assertTrue(result == Right(dynamicInt(42)))
      },
      test("case path returns error for wrong case") {
        val path   = DynamicOptic.root.caseOf("Some")
        val input  = dynamicVariant("None", DynamicValue.Primitive(PrimitiveValue.Unit))
        val result = input.get(path).one
        assertTrue(result.isLeft)
      },
      test("case path returns error for non-variant") {
        val path   = DynamicOptic.root.caseOf("Some")
        val input  = dynamicRecord("x" -> dynamicInt(1))
        val result = input.get(path).one
        assertTrue(result.isLeft)
      },
      test("case path modifies variant value") {
        val path   = DynamicOptic.root.caseOf("Success")
        val input  = dynamicVariant("Success", dynamicInt(42))
        val result = input.modify(path)(_ => dynamicInt(100))
        assertTrue(result == dynamicVariant("Success", dynamicInt(100)))
      }
    ),
    suite("Key path")(
      test("key path accesses map value by key") {
        val path  = DynamicOptic.root.atKey(dynamicString("foo"))
        val input = dynamicMap(
          dynamicString("foo") -> dynamicInt(1),
          dynamicString("bar") -> dynamicInt(2)
        )
        val result = input.get(path).one
        assertTrue(result == Right(dynamicInt(1)))
      },
      test("key path returns error for missing key") {
        val path   = DynamicOptic.root.atKey(dynamicString("missing"))
        val input  = dynamicMap(dynamicString("foo") -> dynamicInt(1))
        val result = input.get(path).one
        assertTrue(result.isLeft)
      },
      test("key path modifies map value") {
        val path  = DynamicOptic.root.atKey(dynamicString("foo"))
        val input = dynamicMap(
          dynamicString("foo") -> dynamicInt(1),
          dynamicString("bar") -> dynamicInt(2)
        )
        val result = input.modify(path)(_ => dynamicInt(99))
        assertTrue(
          result == dynamicMap(
            dynamicString("foo") -> dynamicInt(99),
            dynamicString("bar") -> dynamicInt(2)
          )
        )
      }
    ),
    suite("Combined paths")(
      test("field then element") {
        val path  = DynamicOptic.root.field("items").at(0)
        val input = dynamicRecord(
          "items" -> dynamicSequence(dynamicInt(1), dynamicInt(2))
        )
        val result = input.get(path).one
        assertTrue(result == Right(dynamicInt(1)))
      },
      test("element then field") {
        val path  = DynamicOptic.root.at(0).field("name")
        val input = dynamicSequence(
          dynamicRecord("name" -> dynamicString("first")),
          dynamicRecord("name" -> dynamicString("second"))
        )
        val result = input.get(path).one
        assertTrue(result == Right(dynamicString("first")))
      },
      test("field then case then field") {
        val path  = DynamicOptic.root.field("status").caseOf("Success").field("value")
        val input = dynamicRecord(
          "status" -> dynamicVariant("Success", dynamicRecord("value" -> dynamicInt(42)))
        )
        val result = input.get(path).one
        assertTrue(result == Right(dynamicInt(42)))
      },
      test("deeply combined path") {
        val path = DynamicOptic.root
          .field("data")
          .at(0)
          .field("result")
          .caseOf("Ok")
          .field("value")
        val input = dynamicRecord(
          "data" -> dynamicSequence(
            dynamicRecord(
              "result" -> dynamicVariant(
                "Ok",
                dynamicRecord("value" -> dynamicString("success"))
              )
            )
          )
        )
        val result = input.get(path).one
        assertTrue(result == Right(dynamicString("success")))
      }
    ),
    suite("Path operations with MigrationAction")(
      test("AddField uses path correctly") {
        val path   = DynamicOptic.root.field("nested")
        val action = MigrationAction.AddField(path, "newField", Resolved.Literal.int(42))
        val input  = dynamicRecord(
          "nested" -> dynamicRecord("existing" -> dynamicInt(1))
        )
        val result = action.apply(input)
        assertTrue(
          result == Right(
            dynamicRecord(
              "nested" -> dynamicRecord(
                "existing" -> dynamicInt(1),
                "newField" -> dynamicInt(42)
              )
            )
          )
        )
      },
      test("Rename uses path correctly") {
        val path   = DynamicOptic.root.field("level1").field("level2")
        val action = MigrationAction.Rename(path, "old", "new")
        val input  = dynamicRecord(
          "level1" -> dynamicRecord(
            "level2" -> dynamicRecord("old" -> dynamicInt(1))
          )
        )
        val result = action.apply(input)
        assertTrue(
          result == Right(
            dynamicRecord(
              "level1" -> dynamicRecord(
                "level2" -> dynamicRecord("new" -> dynamicInt(1))
              )
            )
          )
        )
      },
      test("TransformElements uses path correctly") {
        val path   = DynamicOptic.root.field("items")
        val action = MigrationAction.TransformElements(
          path,
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
      }
    ),
    suite("Path equality and comparison")(
      test("same paths are equal") {
        val path1 = DynamicOptic.root.field("a").field("b")
        val path2 = DynamicOptic.root.field("a").field("b")
        assertTrue(path1 == path2)
      },
      test("different paths are not equal") {
        val path1 = DynamicOptic.root.field("a")
        val path2 = DynamicOptic.root.field("b")
        assertTrue(path1 != path2)
      },
      test("root path equals root path") {
        val path1 = DynamicOptic.root
        val path2 = DynamicOptic.root
        assertTrue(path1 == path2)
      },
      test("different depth paths are not equal") {
        val path1 = DynamicOptic.root.field("a")
        val path2 = DynamicOptic.root.field("a").field("b")
        assertTrue(path1 != path2)
      }
    ),
    suite("Edge cases")(
      test("empty sequence element access fails") {
        val path  = DynamicOptic.root.at(0)
        val input = dynamicSequence()
        assertTrue(input.get(path).one.isLeft)
      },
      test("empty map key access fails") {
        val path  = DynamicOptic.root.atKey(dynamicString("any"))
        val input = dynamicMap()
        assertTrue(input.get(path).one.isLeft)
      },
      test("modify empty sequence element fails") {
        val path  = DynamicOptic.root.at(0)
        val input = dynamicSequence()
        assertTrue(input.modifyOrFail(path) { case _ => dynamicInt(1) }.isLeft)
      },
      test("negative index handling") {
        // Behavior depends on implementation - likely error or wrapping
        val path  = DynamicOptic.root.at(-1)
        val input = dynamicSequence(dynamicInt(1), dynamicInt(2))
        // Should likely fail
        assertTrue(input.get(path).one.isLeft)
      }
    )
  )
}
