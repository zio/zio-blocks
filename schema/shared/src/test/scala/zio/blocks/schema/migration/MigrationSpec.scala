package zio.blocks.schema.migration

import zio.blocks.schema._
import zio.blocks.schema.migration.MigrationAction._
import zio.test._

object MigrationSpec extends ZIOSpecDefault {

  final case class V1(name: String)
  object V1 {
    implicit val schema: Schema[V1] = Schema.derived[V1]
  }

  final case class V2(fullName: String)
  object V2 {
    implicit val schema: Schema[V2] = Schema.derived[V2]
  }

  final case class V3(fullName: String, age: Int = 42)
  object V3 {
    implicit val schema: Schema[V3] = Schema.derived[V3]
  }

  private def builder[A: Schema, B: Schema] =
    new MigrationBuilder[A, B](
      sourceSchema = implicitly[Schema[A]],
      targetSchema = implicitly[Schema[B]],
      actions = Vector.empty
    )

  override def spec =
    suite("migration")(
      test("rename field emits Rename action") {
        val b =
          builder[V1, V2]
            .renameField(_.name, _.fullName)

        val prog = DynamicMigration(b.actions)

        assertTrue(
          prog.actions == Vector(
            Rename(at = DynamicOptic.root.field("name"), to = "fullName")
          )
        )
      },
      test("#519 addField(default marker) is captured (not left as marker)") {
        val b =
          builder[V2, V3]
            .addField(_.age, MigrationSchemaExpr.default)

        val prog = DynamicMigration(b.actions)

        val ok =
          prog.actions.headOption.exists {
            case AddField(_, schemaExpr) =>
              schemaExpr != SchemaExpr.DefaultValueMarker
            case _ => false
          }

        assertTrue(ok)
      },
      test("#519 mandateField(default marker) is captured (not left as marker)") {
        final case class S1(age: Option[Int])
        object S1 { implicit val schema: Schema[S1] = Schema.derived[S1] }

        final case class S2(age: Int = 7)
        object S2 { implicit val schema: Schema[S2] = Schema.derived[S2] }

        val b =
          builder[S1, S2]
            .mandateField(_.age, _.age, MigrationSchemaExpr.default)

        val prog = DynamicMigration(b.actions)

        val ok =
          prog.actions.lift(1).exists {
            case Mandate(_, schemaExpr) =>
              schemaExpr != SchemaExpr.DefaultValueMarker
            case _ => false
          }

        assertTrue(ok)
      },
      test("#519 dropField captures default (not left as marker)") {
        final case class S2(age: Int = 7)
        object S2 { implicit val schema: Schema[S2] = Schema.derived[S2] }

        final case class S3()
        object S3 { implicit val schema: Schema[S3] = Schema.derived[S3] }

        val b =
          builder[S2, S3]
            // Scala 2 macro limitation: don't rely on default arg here
            .dropField(_.age, MigrationSchemaExpr.default)

        val prog = DynamicMigration(b.actions)

        val ok =
          prog.actions.headOption.exists {
            case DropField(_, schemaExpr) =>
              schemaExpr != SchemaExpr.DefaultValueMarker
            case _ => false
          }

        assertTrue(ok)
      }
    )
}
