package zio.blocks.schema.migration

import zio.blocks.schema.*
import zio.blocks.schema.migration.MigrationAction.*
import zio.test.*

object MigrationBuilderStructuralSpec extends ZIOSpecDefault {

  // “Current” runtime type
  final case class Person(fullName: String, age: Int, country: String = "DZ")
  object Person {
    given Schema[Person] = Schema.derived[Person]
  }

  // “Old” runtime type
  final case class PersonV1(name: String, age: Int)
  object PersonV1 {
    given Schema[PersonV1] = Schema.derived[PersonV1]
  }

  private def builder[A: Schema, B: Schema] =
    new MigrationBuilder[A, B](
      sourceSchema = Schema[A],
      targetSchema = Schema[B],
      actions = Vector.empty
    )

  override def spec =
    suite("MigrationBuilder (structural-style via SchemaId)")(
  test("plan + run migration without relying on old class identity (SchemaId routing)") {

    val v1 = SchemaId("Person", 1)
    val v2 = SchemaId("Person", 2)

    val mig =
      builder[PersonV1, Person]
        .renameField(_.name, _.fullName)
        .addField(_.country, SchemaExpr.Literal("DZ", Schema[String]))
        .build

    val reg = MigrationRegistry(
      StoredMigration(v1, v2, mig.program)
    )

    val planned = reg.plan(v1, v2)

    // run migration via DynamicValue + interpreter (avoids As[...] requirements)
    val out =
      for {
        dynA <- Right(summon[zio.blocks.schema.Schema[PersonV1]].toDynamicValue(PersonV1("Ada", 42)))
        dynB <- zio.blocks.schema.migration.DynamicMigrationInterpreter(mig.program, dynA)
        outB <- summon[zio.blocks.schema.Schema[Person]]
          .fromDynamicValue(dynB)
          .left
          .map(err => zio.blocks.schema.migration.MigrationError.InvalidOp("Decode", err.toString))
      } yield outB

    assertTrue(planned.isDefined) &&
    assertTrue(planned.get.actions.nonEmpty) &&
    assertTrue(out == Right(Person("Ada", 42, "DZ")))
  }
)

}
