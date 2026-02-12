package zio.blocks.schema.migration

import zio.test._
import zio.test.Assertion
import zio.blocks.schema._
import zio.blocks.schema.CompanionOptics

/**
 * Scala 3-specific CompileTimeValidationSpec tests.
 *
 * Tests Scala 3 `enum` support and `given` syntax which are not available in
 * Scala 2.
 *
 * All shared tests (direct method chaining, typeCheck, ShapeTree, etc.) are in
 * `src/test/scala/zio/blocks/schema/migration/CompileTimeValidationSpec.scala`.
 */
object CompileTimeValidationSpecScala3 extends ZIOSpecDefault {

  def spec = suite("CompileTimeValidationSpec - Scala 3 specific")(
    suite("enum support")(
      test("enum case rename works") {
        enum ColorV1 {
          case Red, Green, Blue
        }
        object ColorV1Optics extends CompanionOptics[ColorV1]

        enum ColorV2 {
          case Crimson, Green, Blue // Red -> Crimson
        }

        given Schema[ColorV1] = Schema.derived
        given Schema[ColorV2] = Schema.derived

        import ColorV1Optics.when

        val migration = MigrationBuilder
          .newBuilder[ColorV1, ColorV2]
          .renameCase((c: ColorV1) => c.when[ColorV1.Red.type], "Crimson")
          .build

        // Verify build succeeds and the rename works at DynamicValue level
        val sourceSchema = summon[Schema[ColorV1]]
        val dynamicRed   = sourceSchema.toDynamicValue(ColorV1.Red)
        val result       = migration.dynamicMigration.apply(dynamicRed)

        assertTrue(
          result.isRight,
          result.exists(_.caseName == Some("Crimson"))
        )
      }
    ),
    suite("given syntax in typeCheck")(
      test("build fails with given syntax when drop is missing") {
        assertZIO(typeCheck("""
          import zio.blocks.schema.migration._
          import zio.blocks.schema._

          case class Src(shared: String, removed: Int)
          case class Tgt(shared: String, added: Boolean)
          given Schema[Src] = Schema.derived
          given Schema[Tgt] = Schema.derived

          MigrationBuilder.newBuilder[Src, Tgt].build
        """))(Assertion.isLeft(Assertion.containsString("Unhandled paths from source")))
      }
    )
  )
}
