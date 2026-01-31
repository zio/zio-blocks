package zio.blocks.schema.migration

import zio.blocks.schema.*
import zio.blocks.schema.migration.MigrationAction.*
import zio.test.*

object MigrationBuilderSpec extends ZIOSpecDefault {

  final case class PersonV1(name: String, age: Int)
  object PersonV1 {
    given Schema[PersonV1] = Schema.derived[PersonV1]
  }

  // age is dropped; fullName is rename(name)
  final case class PersonV2(fullName: String, country: String = "DZ")
  object PersonV2 {
    given Schema[PersonV2] = Schema.derived[PersonV2]
  }

  private def builder[A: Schema, B: Schema] =
    new MigrationBuilder[A, B](
      sourceSchema = Schema[A],
      targetSchema = Schema[B],
      actions = Vector.empty
    )

  override def spec =
    suite("MigrationBuilder (Scala 3)")(
      test("renameField + dropField + addField(default) builds and runs") {
        val b =
          builder[PersonV1, PersonV2]
            .renameField(_.name, _.fullName)
            .dropField(_.age)
            .addField(_.country, MigrationSchemaExpr.default)

        val prog = DynamicMigration(b.actions)
        MigrationValidator.validateOrThrow(
          prog,
          Schema[PersonV1],
          Schema[PersonV2]
        )

        val out: Either[MigrationError, PersonV2] =
          for {
            dynA <- Right(Schema[PersonV1].toDynamicValue(PersonV1("Ada", 42)))
            dynB <- DynamicMigrationInterpreter(prog, dynA)
            outB <- Schema[PersonV2]
              .fromDynamicValue(dynB)
              .left
              .map(err => MigrationError.InvalidOp("Decode", err.toString))
          } yield outB

        assertTrue(out == Right(PersonV2(fullName = "Ada", country = "DZ")))
      },
      test(
        "#519 addField(default marker) is captured (not left as DefaultValueMarker)"
      ) {
        val b =
          builder[PersonV1, PersonV2]
            .addField(_.country, MigrationSchemaExpr.default)

        val prog = DynamicMigration(b.actions)

        val ok =
          prog.actions.headOption.exists {
            case AddField(_, schemaExpr) =>
              schemaExpr != SchemaExpr.DefaultValueMarker
            case _ => false
          }

        assertTrue(ok)
      },
      test("#519 dropField(defaultForReverse omitted) captures marker") {
        val b =
          builder[PersonV1, PersonV2]
            .dropField(_.age)

        val prog = DynamicMigration(b.actions)

        val ok =
          prog.actions.headOption.exists {
            case DropField(_, schemaExpr) =>
              schemaExpr != SchemaExpr.DefaultValueMarker
            case _ => false
          }

        assertTrue(ok)
      },
      test("#519 mandateField(default marker) is captured") {
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
      }
    )
}
