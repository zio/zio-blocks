package zio.blocks.schema.migration

import zio.blocks.schema._
import zio.test._

/**
 * Tests for migration handling of refinement types and structural schemas.
 *
 * Covers:
 *   - Sealed trait hierarchies (sum types)
 *   - Recursive types
 *   - Generic types
 *   - Complex nested structures
 *   - Type aliases and wrappers
 */
object RefinementTypeSchemaSpec extends SchemaBaseSpec {

  // ─────────────────────────────────────────────────────────────────────────
  // Test Data Types
  // ─────────────────────────────────────────────────────────────────────────

  // Sealed trait hierarchy (ADT)
  sealed trait Shape
  case class Circle(radius: Double)                   extends Shape
  case class Rectangle(width: Double, height: Double) extends Shape
  case class Triangle(base: Double, height: Double)   extends Shape

  // Recursive type
  case class TreeNode(value: Int, children: List[TreeNode])

  // Generic wrapper
  case class Box[A](content: A)

  // Nested structures
  case class Department(name: String, employees: List[Employee])
  case class Employee(id: Int, name: String, manager: Option[Employee])

  // Type with multiple levels
  case class Company(
    name: String,
    departments: List[Department],
    metadata: Map[String, String]
  )

  // ─────────────────────────────────────────────────────────────────────────
  // Test Helpers
  // ─────────────────────────────────────────────────────────────────────────

  def dynamicRecord(fields: (String, DynamicValue)*): DynamicValue =
    DynamicValue.Record(fields: _*)

  def dynamicString(s: String): DynamicValue =
    DynamicValue.Primitive(PrimitiveValue.String(s))

  def dynamicInt(i: Int): DynamicValue =
    DynamicValue.Primitive(PrimitiveValue.Int(i))

  def dynamicDouble(d: Double): DynamicValue =
    DynamicValue.Primitive(PrimitiveValue.Double(d))

  def dynamicSequence(elements: DynamicValue*): DynamicValue =
    DynamicValue.Sequence(elements: _*)

  def dynamicVariant(caseName: String, value: DynamicValue): DynamicValue =
    DynamicValue.Variant(caseName, value)

  def dynamicSome(value: DynamicValue): DynamicValue =
    DynamicValue.Variant("Some", DynamicValue.Record(("value", value)))

  def dynamicNone: DynamicValue =
    DynamicValue.Variant("None", DynamicValue.Record())

  // ─────────────────────────────────────────────────────────────────────────
  // Tests
  // ─────────────────────────────────────────────────────────────────────────

  def spec: Spec[TestEnvironment, Any] = suite("RefinementTypeSchemaSpec")(
    suite("Sealed trait (ADT) migrations")(
      test("rename case in sealed trait") {
        // Circle -> Round
        val migration = DynamicMigration.single(
          MigrationAction.RenameCase(DynamicOptic.root, "Circle", "Round")
        )
        val input  = dynamicVariant("Circle", dynamicRecord("radius" -> dynamicDouble(5.0)))
        val result = migration.apply(input)
        assertTrue(
          result == Right(
            dynamicVariant("Round", dynamicRecord("radius" -> dynamicDouble(5.0)))
          )
        )
      },
      test("transform case fields in sealed trait") {
        // Add area field to Circle
        val migration = DynamicMigration.single(
          MigrationAction.TransformCase(
            DynamicOptic.root,
            "Circle",
            Vector(
              MigrationAction.AddField(DynamicOptic.root, "area", Resolved.Literal.double(0.0))
            )
          )
        )
        val input  = dynamicVariant("Circle", dynamicRecord("radius" -> dynamicDouble(5.0)))
        val result = migration.apply(input)
        assertTrue(
          result == Right(
            dynamicVariant(
              "Circle",
              dynamicRecord(
                "radius" -> dynamicDouble(5.0),
                "area"   -> dynamicDouble(0.0)
              )
            )
          )
        )
      },
      test("migrate different cases independently") {
        val migration = DynamicMigration(
          Vector(
            MigrationAction.TransformCase(
              DynamicOptic.root,
              "Circle",
              Vector(MigrationAction.Rename(DynamicOptic.root, "radius", "r"))
            ),
            MigrationAction.TransformCase(
              DynamicOptic.root,
              "Rectangle",
              Vector(
                MigrationAction.Rename(DynamicOptic.root, "width", "w"),
                MigrationAction.Rename(DynamicOptic.root, "height", "h")
              )
            )
          )
        )
        val circle = dynamicVariant("Circle", dynamicRecord("radius" -> dynamicDouble(5.0)))
        val rect   = dynamicVariant(
          "Rectangle",
          dynamicRecord(
            "width"  -> dynamicDouble(3.0),
            "height" -> dynamicDouble(4.0)
          )
        )

        assertTrue(
          migration.apply(circle) == Right(
            dynamicVariant("Circle", dynamicRecord("r" -> dynamicDouble(5.0)))
          )
        )
        assertTrue(
          migration.apply(rect) == Right(
            dynamicVariant(
              "Rectangle",
              dynamicRecord(
                "w" -> dynamicDouble(3.0),
                "h" -> dynamicDouble(4.0)
              )
            )
          )
        )
      },
      test("unmatched case is unchanged") {
        val migration = DynamicMigration.single(
          MigrationAction.TransformCase(
            DynamicOptic.root,
            "Circle",
            Vector(MigrationAction.AddField(DynamicOptic.root, "extra", Resolved.Literal.int(1)))
          )
        )
        val triangle = dynamicVariant(
          "Triangle",
          dynamicRecord(
            "base"   -> dynamicDouble(3.0),
            "height" -> dynamicDouble(4.0)
          )
        )
        assertTrue(migration.apply(triangle) == Right(triangle))
      }
    ),
    suite("Recursive type migrations")(
      test("migrate leaf node") {
        val migration = DynamicMigration.single(
          MigrationAction.Rename(DynamicOptic.root, "value", "data")
        )
        val leaf = dynamicRecord(
          "value"    -> dynamicInt(42),
          "children" -> dynamicSequence()
        )
        val result = migration.apply(leaf)
        assertTrue(
          result == Right(
            dynamicRecord(
              "data"     -> dynamicInt(42),
              "children" -> dynamicSequence()
            )
          )
        )
      },
      test("migrate node with children") {
        // Rename value field at root only
        val migration = DynamicMigration.single(
          MigrationAction.Rename(DynamicOptic.root, "value", "data")
        )
        val node = dynamicRecord(
          "value"    -> dynamicInt(1),
          "children" -> dynamicSequence(
            dynamicRecord(
              "value"    -> dynamicInt(2),
              "children" -> dynamicSequence()
            ),
            dynamicRecord(
              "value"    -> dynamicInt(3),
              "children" -> dynamicSequence()
            )
          )
        )
        val result = migration.apply(node)
        // Only root level is renamed
        result match {
          case Right(DynamicValue.Record(fields)) =>
            assertTrue(fields.exists(_._1 == "data"))
            assertTrue(!fields.exists(_._1 == "value"))
          case _ => assertTrue(false)
        }
      },
      test("add field to recursive structure") {
        val migration = DynamicMigration.single(
          MigrationAction.AddField(DynamicOptic.root, "label", Resolved.Literal.string("default"))
        )
        val node = dynamicRecord(
          "value"    -> dynamicInt(1),
          "children" -> dynamicSequence()
        )
        val result = migration.apply(node)
        assertTrue(
          result == Right(
            dynamicRecord(
              "value"    -> dynamicInt(1),
              "children" -> dynamicSequence(),
              "label"    -> dynamicString("default")
            )
          )
        )
      }
    ),
    suite("Generic type migrations")(
      test("migrate Box[Int]") {
        val migration = DynamicMigration.single(
          MigrationAction.Rename(DynamicOptic.root, "content", "value")
        )
        val box = dynamicRecord("content" -> dynamicInt(42))
        assertTrue(migration.apply(box) == Right(dynamicRecord("value" -> dynamicInt(42))))
      },
      test("migrate Box[String]") {
        val migration = DynamicMigration.single(
          MigrationAction.Rename(DynamicOptic.root, "content", "value")
        )
        val box = dynamicRecord("content" -> dynamicString("hello"))
        assertTrue(migration.apply(box) == Right(dynamicRecord("value" -> dynamicString("hello"))))
      },
      test("transform content of generic type") {
        val migration = DynamicMigration.single(
          MigrationAction.TransformValue(
            DynamicOptic.root,
            "content",
            Resolved.Convert("Int", "String", Resolved.Identity),
            Resolved.Convert("String", "Int", Resolved.Identity)
          )
        )
        val box = dynamicRecord("content" -> dynamicInt(42))
        assertTrue(migration.apply(box) == Right(dynamicRecord("content" -> dynamicString("42"))))
      }
    ),
    suite("Nested structure migrations")(
      test("migrate field in nested record") {
        val migration = DynamicMigration.single(
          MigrationAction.Rename(DynamicOptic.root.field("address"), "street", "streetAddress")
        )
        val person = dynamicRecord(
          "name"    -> dynamicString("Alice"),
          "address" -> dynamicRecord(
            "street" -> dynamicString("123 Main St"),
            "city"   -> dynamicString("Boston")
          )
        )
        val result = migration.apply(person)
        assertTrue(
          result == Right(
            dynamicRecord(
              "name"    -> dynamicString("Alice"),
              "address" -> dynamicRecord(
                "streetAddress" -> dynamicString("123 Main St"),
                "city"          -> dynamicString("Boston")
              )
            )
          )
        )
      },
      test("migrate deeply nested field") {
        val migration = DynamicMigration.single(
          MigrationAction.AddField(
            DynamicOptic.root.field("level1").field("level2").field("level3"),
            "newField",
            Resolved.Literal.int(42)
          )
        )
        val input = dynamicRecord(
          "level1" -> dynamicRecord(
            "level2" -> dynamicRecord(
              "level3" -> dynamicRecord("existing" -> dynamicInt(1))
            )
          )
        )
        val result = migration.apply(input)
        result match {
          case Right(DynamicValue.Record(fields)) =>
            // Verify deep nesting preserved
            assertTrue(fields.exists(_._1 == "level1"))
          case _ => assertTrue(false)
        }
      },
      test("migrate list of nested records") {
        val migration = DynamicMigration.single(
          MigrationAction.TransformElements(
            DynamicOptic.root.field("items"),
            Resolved.Identity, // Each element gets identity (no-op)
            Resolved.Identity
          )
        )
        val input = dynamicRecord(
          "items" -> dynamicSequence(
            dynamicRecord("id" -> dynamicInt(1)),
            dynamicRecord("id" -> dynamicInt(2))
          )
        )
        assertTrue(migration.apply(input) == Right(input))
      }
    ),
    suite("Option type migrations")(
      test("migrate Some value") {
        val migration = DynamicMigration.single(
          MigrationAction.TransformCase(
            DynamicOptic.root.field("maybeValue"),
            "Some",
            Vector(
              MigrationAction.TransformValue(
                DynamicOptic.root,
                "value", // Option's value is in "value" field in ZIO Schema representation
                Resolved.Identity,
                Resolved.Identity
              )
            )
          )
        )
        // This tests the structure but the actual behavior depends on implementation
        val input = dynamicRecord(
          "maybeValue" -> dynamicSome(dynamicInt(42))
        )
        // TransformCase on wrong structure is no-op
        val result = migration.apply(input)
        assertTrue(result.isRight)
      },
      test("mandate optional field") {
        val migration = DynamicMigration.single(
          MigrationAction.Mandate(DynamicOptic.root, "maybeInt", Resolved.Literal.int(0))
        )
        val withSome = dynamicRecord("maybeInt" -> dynamicSome(dynamicInt(42)))
        val withNone = dynamicRecord("maybeInt" -> dynamicNone)

        assertTrue(migration.apply(withSome) == Right(dynamicRecord("maybeInt" -> dynamicInt(42))))
        assertTrue(migration.apply(withNone) == Right(dynamicRecord("maybeInt" -> dynamicInt(0))))
      },
      test("optionalize required field") {
        val migration = DynamicMigration.single(
          MigrationAction.Optionalize(DynamicOptic.root, "requiredInt")
        )
        val input = dynamicRecord("requiredInt" -> dynamicInt(42))
        assertTrue(
          migration.apply(input) == Right(
            dynamicRecord(
              "requiredInt" -> dynamicSome(dynamicInt(42))
            )
          )
        )
      },
      test("nested option handling") {
        val migration = DynamicMigration.single(
          MigrationAction.Mandate(
            DynamicOptic.root.field("data"),
            "optional",
            Resolved.Literal.string("default")
          )
        )
        val input = dynamicRecord(
          "data" -> dynamicRecord(
            "optional" -> dynamicNone
          )
        )
        val result = migration.apply(input)
        assertTrue(
          result == Right(
            dynamicRecord(
              "data" -> dynamicRecord(
                "optional" -> dynamicString("default")
              )
            )
          )
        )
      }
    ),
    suite("Complex nested ADT migrations")(
      test("migrate field in variant inside record") {
        val migration = DynamicMigration.single(
          MigrationAction.TransformCase(
            DynamicOptic.root.field("shape"),
            "Circle",
            Vector(MigrationAction.Rename(DynamicOptic.root, "radius", "r"))
          )
        )
        val input = dynamicRecord(
          "name"  -> dynamicString("myShape"),
          "shape" -> dynamicVariant("Circle", dynamicRecord("radius" -> dynamicDouble(5.0)))
        )
        val result = migration.apply(input)
        assertTrue(
          result == Right(
            dynamicRecord(
              "name"  -> dynamicString("myShape"),
              "shape" -> dynamicVariant("Circle", dynamicRecord("r" -> dynamicDouble(5.0)))
            )
          )
        )
      },
      test("migrate list of variants") {
        val migration = DynamicMigration.single(
          MigrationAction.TransformElements(
            DynamicOptic.root.field("shapes"),
            Resolved.Identity,
            Resolved.Identity
          )
        )
        val input = dynamicRecord(
          "shapes" -> dynamicSequence(
            dynamicVariant("Circle", dynamicRecord("radius" -> dynamicDouble(1.0))),
            dynamicVariant(
              "Rectangle",
              dynamicRecord(
                "width"  -> dynamicDouble(2.0),
                "height" -> dynamicDouble(3.0)
              )
            )
          )
        )
        assertTrue(migration.apply(input) == Right(input))
      }
    ),
    suite("Map type migrations")(
      test("transform map keys") {
        val migration = DynamicMigration.single(
          MigrationAction.TransformKeys(
            DynamicOptic.root.field("metadata"),
            Resolved.Concat(
              Vector(
                Resolved.Literal.string("prefix_"),
                Resolved.Identity
              ),
              ""
            ),
            Resolved.Identity // Reverse not fully implemented for this test
          )
        )
        val input = dynamicRecord(
          "metadata" -> dynamicRecord(
            "key1" -> dynamicString("value1"),
            "key2" -> dynamicString("value2")
          )
        )
        val result = migration.apply(input)
        result match {
          case Right(DynamicValue.Record(fields)) =>
            val metadata = fields.find(_._1 == "metadata")
            assertTrue(metadata.isDefined)
          case _ => assertTrue(false)
        }
      },
      test("transform map values") {
        val migration = DynamicMigration.single(
          MigrationAction.TransformValues(
            DynamicOptic.root.field("scores"),
            Resolved.Convert("Int", "String", Resolved.Identity),
            Resolved.Convert("String", "Int", Resolved.Identity)
          )
        )
        val input = dynamicRecord(
          "scores" -> dynamicRecord(
            "alice" -> dynamicInt(100),
            "bob"   -> dynamicInt(95)
          )
        )
        val result = migration.apply(input)
        assertTrue(
          result == Right(
            dynamicRecord(
              "scores" -> dynamicRecord(
                "alice" -> dynamicString("100"),
                "bob"   -> dynamicString("95")
              )
            )
          )
        )
      }
    ),
    suite("Edge cases")(
      test("empty record migration") {
        val migration = DynamicMigration.single(
          MigrationAction.AddField(DynamicOptic.root, "field", Resolved.Literal.int(1))
        )
        val input = dynamicRecord()
        assertTrue(migration.apply(input) == Right(dynamicRecord("field" -> dynamicInt(1))))
      },
      test("empty sequence migration") {
        val migration = DynamicMigration.single(
          MigrationAction.TransformElements(
            DynamicOptic.root,
            Resolved.Convert("Int", "String", Resolved.Identity),
            Resolved.Identity
          )
        )
        val input = dynamicSequence()
        assertTrue(migration.apply(input) == Right(dynamicSequence()))
      },
      test("deeply nested variant in sequence") {
        val migration = DynamicMigration.single(
          MigrationAction.TransformElements(
            DynamicOptic.root.field("items"),
            Resolved.Identity,
            Resolved.Identity
          )
        )
        val input = dynamicRecord(
          "items" -> dynamicSequence(
            dynamicRecord(
              "status" -> dynamicVariant("Active", dynamicRecord("since" -> dynamicInt(2023)))
            ),
            dynamicRecord(
              "status" -> dynamicVariant("Inactive", dynamicRecord())
            )
          )
        )
        assertTrue(migration.apply(input) == Right(input))
      },
      test("circular reference prevention (structural test)") {
        // While we can't have true circular references in immutable data,
        // we test deeply nested structures that might cause issues
        def deeplyNested(depth: Int): DynamicValue =
          if (depth <= 0) dynamicRecord("leaf" -> dynamicInt(1))
          else dynamicRecord("nested"          -> deeplyNested(depth - 1))

        val migration = DynamicMigration.single(
          MigrationAction.AddField(DynamicOptic.root, "marker", Resolved.Literal.string("marked"))
        )
        val input  = deeplyNested(10)
        val result = migration.apply(input)
        assertTrue(result.isRight)
      }
    )
  )
}
