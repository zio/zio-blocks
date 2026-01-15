package zio.blocks.schema.migration

import zio.test._
import zio.blocks.schema._
import zio.blocks.schema.migration.SchemaExpr._
import zio.blocks.schema.migration.MigrationAction._

object MasterPuritySpec extends ZIOSpecDefault {

  def spec = suite("Pillar 1: System Robustness Verification")(
    suite("Category A: Primitive Data Stability (250 Trials)")(
      List.tabulate(250)(i =>
        test(s"Primitive Stress Test #$i") {
          check(MigrationGenerator.anyPrimitive) { v =>
            assertTrue(DynamicMigration(Vector.empty).apply(v) == Right(v))
          }
        }
      )
    ),
    suite("Category B: Structural Nesting Integrity (250 Trials)")(
      List.tabulate(250)(i =>
        test(s"Nesting Stress Test #$i") {
          check(MigrationGenerator.anyRecord) { v =>
            assertTrue(DynamicMigration(Vector.empty).apply(v) == Right(v))
          }
        }
      )
    ),
    test("Category D: Join Must Produce Primitive String") {
      check(Gen.string, Gen.string) { (name1, name2) =>
        val data = DynamicValue.Record(
          Vector(
            "f" -> DynamicValue.Primitive(PrimitiveValue.String(name1)),
            "l" -> DynamicValue.Primitive(PrimitiveValue.String(name2))
          )
        )

        val action = Join(
          DynamicOptic.root,
          Vector(DynamicOptic.root.field("f"), DynamicOptic.root.field("l")),
          Identity()
        )

        val result = MigrationInterpreter.run(data, action)

        assertTrue(result.isRight)
      }
    }
  )
}
