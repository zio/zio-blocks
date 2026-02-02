package zio.blocks.schema.migration

import zio.test._
import zio.blocks.schema._
import zio.blocks.schema.migration.SharedTestModels._
import zio.blocks.schema.migration.MigrationAction._
// [FIX] Explicitly import the Migration version of SchemaExpr
import zio.blocks.schema.migration.SchemaExpr

object MigrationOfflineSpec extends ZIOSpecDefault {

  def spec = suite("Requirement: Offline Migration & Introspection")(
    test("Capability: Generate SQL DDL from Migration Actions (Offline Mode)") {
      // 1. Define Migration
      val migration = MigrationBuilder
        .make[UserV1, UserV2]
        .renameField((u: UserV1) => u.name, (u: UserV2) => u.fullName)
        .addField(
          (u: UserV2) => u.status,
          SchemaExpr.Constant(DynamicValue.Primitive(PrimitiveValue.String("Pending")))
        )
        .build

      // 2. Offline Interpreter (Simulating a DDL Generator)
      def generateDDL(actions: Vector[MigrationAction]): String =
        actions.map {
          case Rename(path, to) =>
            // Robust extraction of field name from path
            val fromCol = path.nodes.lastOption match {
              case Some(DynamicOptic.Node.Field(name)) => name
              case _                                   => "unknown"
            }
            s"ALTER TABLE users RENAME COLUMN $fromCol TO $to;"

          case AddField(path, _) =>
            val newCol = path.nodes.lastOption match {
              case Some(DynamicOptic.Node.Field(name)) => name
              case _                                   => "unknown"
            }
            s"ALTER TABLE users ADD COLUMN $newCol VARCHAR(255);"

          case _ => "-- Unhandled Action"
        }.mkString("\n")

      val ddl = generateDDL(migration.dynamicMigration.actions)

      // 3. Verification
      assertTrue(ddl.contains("RENAME COLUMN name TO fullName")) &&
      assertTrue(ddl.contains("ADD COLUMN status"))
    },

    test("Capability: Schema-less Data Migration (Data Lake Support)") {
      // Scenario: We have JSON/DynamicValue but NO Case Class (e.g., reading from Kafka/S3)
      // The migration must run purely on DynamicValue without needing Schema[UserV1] at runtime.

      val rawData = DynamicValue.Record(
        Vector(
          "name" -> DynamicValue.Primitive(PrimitiveValue.String("Karim")),
          "age"  -> DynamicValue.Primitive(PrimitiveValue.Int(25))
        )
      )

      // We manually construct the DynamicMigration (simulating loading from a registry/file)
      val offlineMigration = DynamicMigration(
        Vector(
          Rename(DynamicOptic(Vector(DynamicOptic.Node.Field("name"))), "fullName")
        )
      )

      val result = MigrationInterpreter.run(rawData, offlineMigration.actions.head)

      val expected = DynamicValue.Record(
        Vector(
          "fullName" -> DynamicValue.Primitive(PrimitiveValue.String("Karim")),
          "age"      -> DynamicValue.Primitive(PrimitiveValue.Int(25))
        )
      )

      assertTrue(result == Right(expected))
    }
  )
}
