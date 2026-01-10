package zio.blocks.schema.migration

import zio.blocks.schema._
import zio.test._

object MigrationDslStructuralSpec extends ZIOSpecDefault {

  // Current runtime type (kept)
  final case class Person(fullName: String, age: Int, country: String)
  object Person { given Schema[Person] = Schema.derived[Person] }

  // Old version as STRUCTURAL TYPE (no old case class)
  type PersonV1 = { def name: String; def age: Int }

  // Requires #517 structural schema derivation support
  given Schema[PersonV1] = Schema.derived[PersonV1]

  override def spec =
    suite("MigrationDsl (structural old version)")(
      test("build migration from structural old type using migrationS") {

        val mig =
          MigrationDsl.migrationS[PersonV1, Person] {
            given MigrationDsl.MigrationBuilder[PersonV1] =
              MigrationDsl.MigrationBuilder.empty[PersonV1]

            import MigrationDsl.ops.*

            rename(_.name, "name", "fullName")
            addField(_.country, "country", "DZ")
          }

        // We need a runtime value of the structural type.
        // Easiest in Scala 3 is an anonymous class implementing the structural members.
        val old: PersonV1 = new {
          val name: String = "Ada"
          val age: Int     = 42
        }

        val out = mig(old)
        assertTrue(out == Right(Person("Ada", 42, "DZ")))
      }
    )
}
