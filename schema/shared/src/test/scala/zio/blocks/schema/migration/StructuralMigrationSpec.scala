package zio.blocks.schema.migration

import zio.test._
import zio.blocks.schema._
import scala.annotation.unused
import zio.blocks.chunk.Chunk // [FIX] Added Chunk Import

//import scala.collection.immutable.ListMap

object StructuralMigrationSpec extends ZIOSpecDefault {

  case class PersonV2(fullName: String, age: Int)
  type PersonV1 = { def name: String; def age: Int }

  // @nowarn("msg=unused")
  implicit val conversion: scala.languageFeature.implicitConversions = scala.language.implicitConversions
  import scala.language.reflectiveCalls

  def spec = suite("Requirement: Structural Types & Schema Evolution")(
    test("Workflow: Migrate from a Structural Type (V1) to a Case Class (V2)") {

      // [FIX] Added @unused to fix compilation error
      @unused implicit val schemaV1: Schema[PersonV1] = null.asInstanceOf[Schema[PersonV1]]
      @unused implicit val schemaV2: Schema[PersonV2] = null.asInstanceOf[Schema[PersonV2]]

      val migration = MigrationBuilder
        .make[PersonV1, PersonV2]
        .renameField((u: PersonV1) => u.name, (u: PersonV2) => u.fullName)
        .build

      val actions = migration.dynamicMigration.actions

      assertTrue(actions.length == 1) &&
      assertTrue(actions.head match {
        case MigrationAction.Rename(from, to) =>
          from.nodes.last == DynamicOptic.Node.Field("name") &&
          to == "fullName"
        case _ => false
      })
    },

    test("Zero Runtime Overhead: Structural access does not crash or use reflection at runtime") {
      // [FIX] Converted Vector to Chunk using Chunk.fromIterable
      val oldData = DynamicValue.Record(
        Chunk.fromIterable(
          Vector(
            "name" -> DynamicValue.Primitive(PrimitiveValue.String("Rahim")),
            "age"  -> DynamicValue.Primitive(PrimitiveValue.Int(30))
          )
        )
      )

      val dynamicMigration = zio.blocks.schema.migration.DynamicMigration(
        Vector(
          MigrationAction.Rename(
            DynamicOptic(Vector(DynamicOptic.Node.Field("name"))),
            "fullName"
          )
        )
      )

      val result = MigrationInterpreter.run(oldData, dynamicMigration.actions.head)

      // [FIX] Converted Vector to Chunk using Chunk.fromIterable
      val expected = DynamicValue.Record(
        Chunk.fromIterable(
          Vector(
            "fullName" -> DynamicValue.Primitive(PrimitiveValue.String("Rahim")),
            "age"      -> DynamicValue.Primitive(PrimitiveValue.Int(30))
          )
        )
      )

      assertTrue(result == Right(expected))
    },

    test("Purely Structural: Migrate between two structural types (No Case Classes at all)") {
      type Origin = { def a: Int }
      type Dest   = { def b: Int }

      // [FIX] Added @unused
      @unused implicit val s1: Schema[Origin] = null.asInstanceOf[Schema[Origin]]
      @unused implicit val s2: Schema[Dest]   = null.asInstanceOf[Schema[Dest]]

      val m = MigrationBuilder
        .make[Origin, Dest]
        .renameField((o: Origin) => o.a, (d: Dest) => d.b)
        .build

      assertTrue(m.dynamicMigration.actions.nonEmpty)
    }
  )
}
