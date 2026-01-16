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
        val mig =
          builder[V1, V2]
            .renameField(_.name, _.fullName)
            .buildPartial

        assertTrue(
          mig.program.actions == Vector(
            Rename(at = DynamicOptic.root.field("name"), to = "fullName")
          )
        )
      },

      test("#519 addField(default marker) is captured into DefaultValueFromSchema (not left as marker)") {
        val mig =
          builder[V2, V3]
            .addField(_.age, MigrationSchemaExpr.default)
            .buildPartial

        val action = mig.program.actions.headOption

        assertTrue(action.isDefined) &&
        assertTrue {
          action.get match {
            case AddField(_, expr) =>
              expr != SchemaExpr.DefaultValueMarker
            case _ => false
          }
        }
      },

      test("#519 mandateField(default marker) is captured (not left as marker)") {
        final case class S1(age: Option[Int])
        object S1 {
          given Schema[S1] = Schema.derived[S1]
        }

        final case class S2(age: Int = 7)
        object S2 {
          given Schema[S2] = Schema.derived[S2]
        }

        val mig =
          builder[S1, S2]
            .mandateField(_.age, _.age, MigrationSchemaExpr.default)
            .buildPartial

        assertTrue(mig.program.actions.length == 2) &&
        assertTrue {
          mig.program.actions(1) match {
            case Mandate(_, expr) => expr != SchemaExpr.DefaultValueMarker
            case _                => false
          }
        }
      },

      test("#519 dropField(defaultForReverse omitted) captures default (not left as marker)") {
        final case class S2(age: Int = 7)
        object S2 {
          given Schema[S2] = Schema.derived[S2]
        }
        final case class S3()
        object S3 {
          given Schema[S3] = Schema.derived[S3]
        }

        val mig =
          builder[S2, S3]
            .dropField(_.age)
            .buildPartial

        assertTrue {
          mig.program.actions.headOption match {
            case Some(DropField(_, expr)) => expr != SchemaExpr.DefaultValueMarker
            case _                        => false
          }
        }
      }
    )
}
