package zio.blocks.schema.migration

import zio.blocks.schema.{DynamicValue, PrimitiveValue, Schema}
import zio.blocks.schema.SchemaBaseSpec
import zio.blocks.chunk.Chunk
import zio.test.*

/**
 * Tests for ValidationProof, PathExtractor, TypeLevel, and
 * TrackedMigrationBuilder.
 */
object ValidationProofSpec extends SchemaBaseSpec {

  // Test schema types
  case class PersonV1(name: String, age: Int, email: String)
  object PersonV1 {
    given Schema[PersonV1] = Schema.derived
  }

  case class PersonV2(fullName: String, age: Int)
  object PersonV2 {
    given Schema[PersonV2] = Schema.derived
  }

  case class AddressV1(street: String, city: String, zip: String)
  object AddressV1 {
    given Schema[AddressV1] = Schema.derived
  }

  case class AddressV2(street: String, city: String, zip: String, country: String)
  object AddressV2 {
    given Schema[AddressV2] = Schema.derived
  }

  sealed trait StatusV1
  object StatusV1 {
    case object Active   extends StatusV1
    case object Inactive extends StatusV1
    given Schema[StatusV1] = Schema.derived
  }

  sealed trait StatusV2
  object StatusV2 {
    case object Active   extends StatusV2
    case object Inactive extends StatusV2
    case object Pending  extends StatusV2
    given Schema[StatusV2] = Schema.derived
  }

  def spec: Spec[TestEnvironment, Any] = suite("ValidationProofSpec")(
    suite("PathExtractor.extractAllPaths")(
      test("extracts field paths from simple case class") {
        val paths = PathExtractor.extractAllPaths[PersonV1]
        assertTrue(paths.contains("name")) &&
        assertTrue(paths.contains("age")) &&
        assertTrue(paths.contains("email"))
      },
      test("extracts correct number of paths") {
        val paths = PathExtractor.extractAllPaths[PersonV1]
        assertTrue(paths.size == 3)
      },
      test("extracts field paths from address type") {
        val paths = PathExtractor.extractAllPaths[AddressV1]
        assertTrue(paths.contains("street")) &&
        assertTrue(paths.contains("city")) &&
        assertTrue(paths.contains("zip"))
      },
      test("extractPathsAndCases combines field and case extraction") {
        val combined = PathExtractor.extractPathsAndCases[StatusV1]
        assertTrue(combined.exists(_.startsWith("case:")))
      }
    ),
    suite("PathExtractor.extractCases")(
      test("extracts case names from sealed trait") {
        val cases = PathExtractor.extractCases[StatusV1]
        assertTrue(cases.contains("case:Active")) &&
        assertTrue(cases.contains("case:Inactive"))
      },
      test("extracts correct case count") {
        val cases = PathExtractor.extractCases[StatusV1]
        assertTrue(cases.size == 2)
      },
      test("extracts more cases from extended enum") {
        val cases = PathExtractor.extractCases[StatusV2]
        assertTrue(cases.size == 3) &&
        assertTrue(cases.contains("case:Pending"))
      }
    ),
    suite("PathExtractor.computeRequirements")(
      test("computes required operations between types") {
        val reqs = PathExtractor.computeRequirements[AddressV1, AddressV2]
        // AddressV2 has country not in AddressV1
        assertTrue(reqs.needsProviding.contains("country"))
      },
      test("handles identical types with empty requirements") {
        val reqs = PathExtractor.computeRequirements[AddressV1, AddressV1]
        assertTrue(reqs.needsHandling.isEmpty && reqs.needsProviding.isEmpty)
      },
      test("identifies paths needing handling when fields are removed") {
        val reqs = PathExtractor.computeRequirements[PersonV1, PersonV2]
        // PersonV1 has name and email that are not in PersonV2
        assertTrue(reqs.needsHandling.contains("name")) &&
        assertTrue(reqs.needsHandling.contains("email"))
      },
      test("identifies paths needing providing when fields are added") {
        val reqs = PathExtractor.computeRequirements[PersonV1, PersonV2]
        // PersonV2 has fullName not in PersonV1
        assertTrue(reqs.needsProviding.contains("fullName"))
      }
    ),
    suite("TrackedMigrationBuilder")(
      test("tracked factory creates builder") {
        import MigrationBuilderSyntax.*
        val builder = tracked[AddressV1, AddressV2]
        assertTrue(builder.underlying != null)
      },
      test("buildPartial returns valid migration") {
        import MigrationBuilderSyntax.*
        val builder   = tracked[AddressV1, AddressV2]
        val migration = builder.underlying
          .addFieldLiteral("country", "USA")
          .buildPartial
        assertTrue(migration != null)
      },
      test("buildRuntimeStrict validates migration") {
        val tracked   = MigrationBuilderSyntax.tracked[AddressV1, AddressV2]
        val complete  = tracked.underlying.addFieldLiteral("country", "USA")
        val migration = complete.buildStrict
        assertTrue(migration != null)
      }
    ),
    suite("DynamicMigrationInterpreter")(
      test("can apply migration to dynamic values") {
        val migration = DynamicMigration.single(
          MigrationAction.AddField(
            zio.blocks.schema.DynamicOptic.root,
            "country",
            Resolved.Literal.string("USA")
          )
        )

        val input = DynamicValue.Record(
          Chunk(
            ("street", DynamicValue.Primitive(PrimitiveValue.String("123 Main St"))),
            ("city", DynamicValue.Primitive(PrimitiveValue.String("Test City"))),
            ("zip", DynamicValue.Primitive(PrimitiveValue.String("12345")))
          )
        )

        val result = DynamicMigrationInterpreter(migration, input)
        assertTrue(result.isRight)
      },
      test("adds field with correct value") {
        val migration = DynamicMigration.single(
          MigrationAction.AddField(
            zio.blocks.schema.DynamicOptic.root,
            "extra",
            Resolved.Literal.int(42)
          )
        )

        val input = DynamicValue.Record(Chunk(("name", DynamicValue.Primitive(PrimitiveValue.String("test")))))

        DynamicMigrationInterpreter(migration, input) match {
          case Right(rec @ DynamicValue.Record(flds)) =>
            assertTrue(flds.exists(_._1 == "extra"))
          case _ =>
            assertTrue(false)
        }
      }
    ),
    suite("TypeLevel operations")(
      test("Union type is defined") {
        import TypeLevel.*
        // Type verification - if it compiles, it works
        type Combined = Union[("a", "b"), ("c", "d")]
        assertTrue(true)
      },
      test("Difference type is defined") {
        import TypeLevel.*
        type Diff = Difference[("a", "b", "c"), ("a", "b")]
        assertTrue(true)
      }
    )
  )
}
