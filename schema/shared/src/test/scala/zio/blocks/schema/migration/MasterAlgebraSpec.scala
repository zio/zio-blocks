package zio.blocks.schema.migration

import zio.test._
import zio.blocks.schema._
import zio.blocks.schema.migration.MigrationAction._
import zio.blocks.schema.migration.SchemaExpr

object MasterAlgebraModels {
  case class V1(name: String)
  case class V2(name: String, age: Int)
  case class V3(fullName: String, age: Int)

  case class PersonSource(firstName: String, lastName: String)
  case class PersonTarget(fullName: String, age: Int)

  implicit val v1Schema: Schema[V1]                     = Schema.derived
  implicit val v2Schema: Schema[V2]                     = Schema.derived
  implicit val v3Schema: Schema[V3]                     = Schema.derived
  implicit val personSourceSchema: Schema[PersonSource] = Schema.derived
  implicit val personTargetSchema: Schema[PersonTarget] = Schema.derived
}

import MasterAlgebraModels._

object MasterAlgebraSpec extends ZIOSpecDefault {

  lazy val m1: Migration[V1, V2] = MigrationBuilder
    .make[V1, V2]
    .addField((v: V2) => v.age, 0)
    .build

  lazy val m2: Migration[V2, V3] = MigrationBuilder
    .make[V2, V3]
    .renameField((v: V2) => v.name, (v: V3) => v.fullName)
    .build

  lazy val m3: Migration[V3, V3] = MigrationBuilder.make[V3, V3].build

  def spec = suite("Pillar 1: Master Algebra & Laws Verification")(
    test("Law: Identity - apply(a) == Right(a)") {
      val identityMigration = MigrationBuilder.make[V1, V1].build

      assertTrue(identityMigration.dynamicMigration.actions.isEmpty)
    },
    test("Law: Associativity - Structural Equality check") {
      val leftSide  = (m1 ++ m2) ++ m3
      val rightSide = m1 ++ (m2 ++ m3)

      assertTrue(
        leftSide.dynamicMigration.actions == rightSide.dynamicMigration.actions
      )
    },
    test("Law: Structural Reverse - m.reverse.reverse == m") {
      val reversedTwice = m1.reverse.reverse
      assertTrue(reversedTwice.dynamicMigration.actions == m1.dynamicMigration.actions)
    },
    test("Law: Semantic Inverse - Data restoration") {
      val data = DynamicValue.Record(Vector("name" -> DynamicValue.Primitive(PrimitiveValue.String("John"))))

      val optic        = DynamicOptic(Vector(DynamicOptic.Node.Field("name")))
      val renameAction = Rename(optic, "fullName")

      val result = for {
        migrated <- MigrationInterpreter.run(data, renameAction)
        restored <- MigrationInterpreter.run(migrated, renameAction.reverse)
      } yield restored

      assertTrue(result == Right(data))
    },
    test("Example: PersonSource to PersonTarget (Add Field Check)") {
      val builder = MigrationBuilder
        .make[PersonSource, PersonTarget]
        .addField((p: PersonTarget) => p.age, 0)

      val actions = builder.build.dynamicMigration.actions

      val hasAgeAdd = actions.exists {
        case AddField(at, SchemaExpr.Constant(DynamicValue.Primitive(PrimitiveValue.Int(0)))) =>
          at.nodes.lastOption.contains(DynamicOptic.Node.Field("age"))
        case _ => false
      }

      assertTrue(hasAgeAdd)
    },
    test("Error Handling: Errors must capture path information") {
      val data      = DynamicValue.Primitive(PrimitiveValue.Int(10))
      val errorPath =
        DynamicOptic(Vector(DynamicOptic.Node.Field("missing_address"), DynamicOptic.Node.Field("street")))
      val action = Rename(errorPath, "new")

      val result = MigrationInterpreter.run(data, action)

      val pathCaptured = result match {
        case Left(e: MigrationError.FieldNotFound) => e.path == errorPath
        case Left(e: MigrationError.TypeMismatch)  => e.path == errorPath
        case _                                     => false
      }

      assertTrue(pathCaptured)
    }
  )
}
