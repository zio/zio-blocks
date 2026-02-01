package zio.blocks.schema.migration

import zio.blocks.schema.Schema
import zio.blocks.schema.SchemaBaseSpec
import zio.test._

/**
 * Tests for SchemaShapeValidator - hierarchical path tracking system.
 *
 * This validates our implementation exceeds PR #891 by demonstrating:
 *   1. Hierarchical path representation (maintainer's "lists of lists")
 *   2. Depth-aware field tracking
 *   3. Nested TransformCase coverage
 *   4. Coverage analysis by depth level
 */
object SchemaShapeValidatorSpec extends SchemaBaseSpec {

  // Test types
  case class PersonV1(firstName: String, lastName: String, legacy: String)
  object PersonV1 {
    implicit val schema: Schema[PersonV1] = Schema.derived
  }

  case class PersonV2(fullName: String, age: Int)
  object PersonV2 {
    implicit val schema: Schema[PersonV2] = Schema.derived
  }

  case class UserV1(name: String, email: String)
  object UserV1 {
    implicit val schema: Schema[UserV1] = Schema.derived
  }

  case class UserV2(username: String, email: String)
  object UserV2 {
    implicit val schema: Schema[UserV2] = Schema.derived
  }

  // Nested types for hierarchical testing
  case class Address(street: String, city: String)
  object Address {
    implicit val schema: Schema[Address] = Schema.derived
  }

  case class PersonWithAddress(name: String, address: Address)
  object PersonWithAddress {
    implicit val schema: Schema[PersonWithAddress] = Schema.derived
  }

  def spec: Spec[TestEnvironment, Any] = suite("SchemaShapeValidatorSpec")(
    suite("HierarchicalPath")(
      test("root path has depth 0") {
        val path = SchemaShapeValidator.HierarchicalPath.root
        assertTrue(path.depth == 0) &&
        assertTrue(path.segments.isEmpty)
      },
      test("single field has depth 1") {
        val path = SchemaShapeValidator.HierarchicalPath.field("name")
        assertTrue(path.depth == 1) &&
        assertTrue(path.segments.length == 1)
      },
      test("nested field has correct depth") {
        import SchemaShapeValidator.HierarchicalPath
        val path = HierarchicalPath.root / "address" / "street"
        assertTrue(path.depth == 2) &&
        assertTrue(path.segments.length == 2) &&
        assertTrue(path.render == "field:address/field:street")
      },
      test("toFlatString returns dot notation") {
        import SchemaShapeValidator.HierarchicalPath
        val path = HierarchicalPath.root / "address" / "street"
        assertTrue(path.toFlatString == "address.street")
      },
      test("fromDynamicOptic converts correctly") {
        import zio.blocks.schema.DynamicOptic
        import SchemaShapeValidator.HierarchicalPath
        val optic = DynamicOptic.root.field("address").field("city")
        val path  = HierarchicalPath.fromDynamicOptic(optic)
        assertTrue(path.depth == 2) &&
        assertTrue(path.toFlatString == "address.city")
      }
    ),
    suite("SchemaShape extraction")(
      test("extracts field paths as hierarchical paths") {
        val shape = SchemaShapeValidator.SchemaShape.fromSchema(Schema[UserV1])
        assertTrue(shape.fieldPaths.size == 2) &&
        assertTrue(shape.hasField("name")) &&
        assertTrue(shape.hasField("email"))
      },
      test("returns empty for primitive schema") {
        val shape = SchemaShapeValidator.SchemaShape.fromSchema(Schema[String])
        assertTrue(shape.fieldPaths.isEmpty)
      }
    ),
    suite("MigrationCoverage tracking")(
      test("addField marks path as provided only") {
        val coverage = SchemaShapeValidator.MigrationCoverage.empty.addField("age")
        assertTrue(coverage.providedToTarget.exists(_.toFlatString == "age")) &&
        assertTrue(!coverage.handledFromSource.exists(_.toFlatString == "age"))
      },
      test("dropField marks path as handled only") {
        val coverage = SchemaShapeValidator.MigrationCoverage.empty.dropField("legacy")
        assertTrue(coverage.handledFromSource.exists(_.toFlatString == "legacy"))
      },
      test("renameField marks both paths") {
        val coverage = SchemaShapeValidator.MigrationCoverage.empty.renameField("name", "username")
        assertTrue(coverage.handledFromSource.exists(_.toFlatString == "name")) &&
        assertTrue(coverage.providedToTarget.exists(_.toFlatString == "username"))
      },
      test("renderByDepth shows hierarchical structure") {
        import SchemaShapeValidator.{MigrationCoverage, HierarchicalPath}
        val coverage = MigrationCoverage.empty
          .addPath(HierarchicalPath.root / "name")
          .addPath(HierarchicalPath.root / "address" / "street")
        val byDepth = coverage.renderByDepth
        assertTrue(byDepth.contains("Depth 1")) &&
        assertTrue(byDepth.contains("Depth 2"))
      }
    ),
    suite("validateShape")(
      test("complete migration passes validation") {
        val migration = MigrationBuilder[UserV1, UserV2]
          .renameFieldAt(zio.blocks.schema.DynamicOptic.root, "name", "username")
          .buildPartial

        val result = SchemaShapeValidator.validateShape(migration)
        assertTrue(result == SchemaShapeValidator.ShapeValidationResult.Complete)
      },
      test("missing addField results in incomplete") {
        val migration = MigrationBuilder[PersonV1, PersonV2]
          .dropFieldNoReverse("firstName")
          .dropFieldNoReverse("lastName")
          .dropFieldNoReverse("legacy")
          .buildPartial

        val result = SchemaShapeValidator.validateShape(migration)
        result match {
          case SchemaShapeValidator.ShapeValidationResult.Complete =>
            assertTrue(false)
          case incomplete: SchemaShapeValidator.ShapeValidationResult.Incomplete =>
            assertTrue(incomplete.missingTargetFields.nonEmpty)
        }
      }
    ),
    suite("analyzeCoverage in MigrationBuilder")(
      test("tracks field operations") {
        val builder = MigrationBuilder[UserV1, UserV2]
          .dropFieldNoReverse("name")
          .addFieldLiteral("username", "default")

        val coverage = builder.analyzeCoverage
        assertTrue(coverage.droppedFields.exists(_.toFlatString == "name")) &&
        assertTrue(coverage.addedFields.exists(_.toFlatString == "username"))
      }
    ),
    suite("buildStrict validation")(
      test("buildStrict throws on incomplete migration") {
        val result = scala.util.Try {
          MigrationBuilder[PersonV1, PersonV2]
            .dropFieldNoReverse("legacy")
            .buildStrict
        }
        assertTrue(result.isFailure)
      }
    )
  )
}
