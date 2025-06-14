package zio.blocks.schema

import zio.Scope
import zio.test.Assertion._
import zio.test._

object SchemaAspectSpec extends ZIOSpecDefault {

  final case class Person(name: String, age: Int)

  object Person extends CompanionOptics[Person] {
    implicit val schema: Schema[Person] = Schema.derived

    val nameLens: Lens[Person, String] = optic(_.name)
    val ageLens: Lens[Person, Int]     = optic(_.age)
  }
  import Person._

  def spec: Spec[TestEnvironment with Scope, Any] =
    suite("SchemaAspectSpec")(
      test("identity aspect") {
        val updatedSchema = schema @@ SchemaAspect.identity

        assert(updatedSchema)(equalTo(schema))
      },
      test("doc aspect") {
        val doc = "Person data type"

        val updatedSchema = schema @@ SchemaAspect.doc(doc)

        assert(updatedSchema.doc)(equalTo(Doc.Text(doc)))
      },
      test("example aspect") {
        val p = Person("Jaro", 34)

        val updatedSchema = schema @@ SchemaAspect.examples(p)

        assert(updatedSchema.examples)(equalTo(Seq(p)))
      },
      test("update doc of a field") {
        val doc = "name of the person"

        val updatedSchema = schema @@ (Person.nameLens, SchemaAspect.doc(doc))

        assert(updatedSchema.get(Person.nameLens).get.doc)(equalTo(Doc.Text(doc)))
      }
    )
}
