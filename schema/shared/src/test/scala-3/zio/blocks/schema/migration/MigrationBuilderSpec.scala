package zio.blocks.schema.migration

import zio.blocks.schema.*
import zio.blocks.schema.migration.MigrationAction.*
import zio.test.*

object MigrationBuilderSpec extends ZIOSpecDefault {

  // ----------------------------
  // Test models
  // ----------------------------

  final case class PersonV1(name: String, age: Int)
  object PersonV1 {
    given Schema[PersonV1] = Schema.derived[PersonV1]
  }

  // age is dropped, fullName is rename(name)
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
      test("renameField + dropField + addField(default marker) builds and runs") {
  val mig: Migration[PersonV1, PersonV2] =
    builder[PersonV1, PersonV2]
      .renameField(_.name, _.fullName)
      .dropField(_.age)
      .addField(_.country, SchemaExpr.Literal("DZ", Schema[String]))
      .build

  val out =
    for {
      dynA <- Right(
        summon[zio.blocks.schema.Schema[PersonV1]]
          .toDynamicValue(PersonV1("Ada", 42))
      )
      dynB <- zio.blocks.schema.migration.DynamicMigrationInterpreter(
        mig.program,
        dynA
      )
      outB <- summon[zio.blocks.schema.Schema[PersonV2]]
        .fromDynamicValue(dynB)
        .left
        .map(err =>
          zio.blocks.schema.migration.MigrationError.InvalidOp(
            "Decode",
            err.toString
          )
        )
    } yield outB

  assertTrue(out == Right(PersonV2(fullName = "Ada", country = "DZ")))
}
,
      test(
        "#519 addField(MigrationSchemaExpr.default) captures marker (not left as DefaultValueMarker)"
      ) {
        val mig =
          builder[PersonV1, PersonV2]
            .addField(_.country, SchemaExpr.Literal("DZ", Schema[String]))
            .buildPartial

        mig.program.actions.headOption match {
          case Some(AddField(_, expr)) =>
            assertTrue(expr != SchemaExpr.DefaultValueMarker)
          case _ =>
            assertTrue(false)
        }
      },
      test(
        "#519 dropField defaultForReverse omitted captures marker (not left as DefaultValueMarker)"
      ) {
        val mig =
          builder[PersonV1, PersonV2]
            .dropField(_.age)
            .buildPartial

        mig.program.actions.headOption match {
          case Some(DropField(_, expr)) =>
            assertTrue(expr != SchemaExpr.DefaultValueMarker)
          case _ =>
            assertTrue(false)
        }
      },
      test("#519 mandateField(MigrationSchemaExpr.default) captures marker") {
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

        mig.program.actions(1) match {
          case Mandate(_, expr) =>
            assertTrue(expr != SchemaExpr.DefaultValueMarker)
          case _ =>
            assertTrue(false)
        }
      }
    )
}
