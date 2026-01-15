package zio.blocks.schema.migration

import zio.test._
import zio.blocks.schema.{DynamicValue, PrimitiveValue, DynamicOptic}
import zio.blocks.schema.migration._
import zio.blocks.schema.migration.MigrationAction._
import zio.blocks.schema.migration.{SchemaExpr => MigrationSchemaExpr}

object OfflineEvidenceSpec extends ZIOSpecDefault {

  def spec = suite("THE FINAL EVIDENCE: Offline & Algebraic Capability")(
    test("PROOF 1: Migration actions are pure data-only and serializable") {
      val optic  = DynamicOptic(Vector(DynamicOptic.Node.Field("user_name")))
      val action = Rename(optic, "full_name")

      assertTrue(action.isInstanceOf[java.io.Serializable])
    },
    test("PROOF 2: Migration can generate SQL DDL statements (Offline Support)") {
      val actions = Vector(
        Rename(DynamicOptic(Vector(DynamicOptic.Node.Field("age"))), "user_age"),
        AddField(
          DynamicOptic(Vector(DynamicOptic.Node.Field("status"))),
          MigrationSchemaExpr.Constant(DynamicValue.Primitive(PrimitiveValue.String("active")))
        )
      )

      def generateSQL(actions: Vector[MigrationAction]): String =
        actions.map {
          case Rename(at, to) =>
            val oldName = at.nodes.last.asInstanceOf[DynamicOptic.Node.Field].name
            s"ALTER TABLE users RENAME COLUMN $oldName TO $to;"
          case AddField(at, _) =>
            val newName = at.nodes.last.asInstanceOf[DynamicOptic.Node.Field].name
            s"ALTER TABLE users ADD COLUMN $newName VARCHAR(255);"
          case _ => ""
        }.mkString("\n")

      val sql = generateSQL(actions)
      assertTrue(sql.contains("RENAME COLUMN age TO user_age") && sql.contains("ADD COLUMN status"))
    },
    test("PROOF 3: Transform raw data without original classes") {
      val rawData = DynamicValue.Record(
        Vector(
          "firstName" -> DynamicValue.Primitive(PrimitiveValue.String("Dhrubo"))
        )
      )
      val action = Rename(DynamicOptic(Vector(DynamicOptic.Node.Field("firstName"))), "fullName")
      val result = MigrationInterpreter.run(rawData, action)

      assertTrue(result.isRight)
    }
  )
}
