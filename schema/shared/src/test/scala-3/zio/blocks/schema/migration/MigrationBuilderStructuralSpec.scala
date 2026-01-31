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

        val b =
          builder[PersonV1, Person]
            .renameField(_.name, _.fullName)
            .addField(_.country, MigrationSchemaExpr.default)

        val prog = DynamicMigration(b.actions)
        MigrationValidator.validateOrThrow(prog, Schema[PersonV1], Schema[Person])

        val reg = MigrationRegistry(
          StoredMigration(v1, v2, prog)
        )

        val planned = reg.plan(v1, v2)

        val out: Either[MigrationError, Person] =
          for {
            dynA <- Right(Schema[PersonV1].toDynamicValue(PersonV1("Ada", 42)))
            plan <- planned.toRight(
              MigrationError.InvalidOp("Plan", s"No migration found from $v1 to $v2")
            )
            dynB <- DynamicMigrationInterpreter(plan, dynA)
            outB <- Schema[Person]
              .fromDynamicValue(dynB)
              .left
              .map(err => MigrationError.InvalidOp("Decode", err.toString))
          } yield outB

        assertTrue(planned.isDefined) &&
        assertTrue(planned.get.actions.nonEmpty) &&
        assertTrue(out == Right(Person("Ada", 42, "DZ")))
      }
    )
}
