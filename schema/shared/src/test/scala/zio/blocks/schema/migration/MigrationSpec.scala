package zio.blocks.schema.migration

import zio.blocks.schema.*
import zio.blocks.schema.migration.MigrationAction.*
import zio.test.*

object MigrationSpec extends ZIOSpecDefault {

  final case class V1(name: String)
  object V1 {
    given Schema[V1] = Schema.derived[V1]
  }

  final case class V2(fullName: String)
  object V2 {
    given Schema[V2] = Schema.derived[V2]
  }

  final case class V3(fullName: String, age: Int = 42)
  object V3 {
    given Schema[V3] = Schema.derived[V3]
  }

  private def builder[A: Schema, B: Schema] =
    new MigrationBuilder[A, B](
      sourceSchema = Schema[A],
      targetSchema = Schema[B],
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
      test(
        "#519 mandateField(default marker) is captured (not left as marker)"
      ) {
        final case class S1(age: Option[Int])
        object S1 { given Schema[S1] = Schema.derived[S1] }

        final case class S2(age: Int = 7)
        object S2 { given Schema[S2] = Schema.derived[S2] }

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
      test(
        "#519 dropField(defaultForReverse omitted) captures default (not left as marker)"
      ) {
        final case class S2(age: Int = 7)
        object S2 { given Schema[S2] = Schema.derived[S2] }

        final case class S3()
        object S3 { given Schema[S3] = Schema.derived[S3] }

        val b =
          builder[S2, S3]
            .dropField(_.age)

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
