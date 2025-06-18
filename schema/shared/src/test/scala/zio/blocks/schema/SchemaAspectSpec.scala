package zio.blocks.schema

import zio.Scope
import zio.test.Assertion._
import zio.test._

object SchemaAspectSpec extends ZIOSpecDefault {
  case class Person(name: String, age: Int)

  object Person extends CompanionOptics[Person] {
    implicit val schema: Schema[Person] = Schema.derived

    val nameLens: Lens[Person, String] = optic(_.name)
    val ageLens: Lens[Person, Int]     = optic(_.age)
  }

  def spec: Spec[TestEnvironment with Scope, Any] =
    suite("SchemaAspectSpec")(
      test("identity aspect") {
        val updatedSchema = Person.schema @@ SchemaAspect.identity
        assert(updatedSchema)(equalTo(Person.schema))
      },
      test("doc aspect") {
        val doc           = "Person data type"
        val updatedSchema = Person.schema @@ SchemaAspect.doc(doc)
        assert(updatedSchema.doc)(equalTo(Doc.Text(doc)))
      },
      test("example aspect") {
        val p             = Person("Jaro", 34)
        val updatedSchema = Person.schema @@ SchemaAspect.examples(p)
        assert(updatedSchema.examples)(equalTo(Seq(p)))
      },
      test("update doc of a field") {
        val doc           = "name of the person"
        val updatedSchema = Person.schema @@ (Person.nameLens, SchemaAspect.doc(doc))
        assert(updatedSchema.get(Person.nameLens).get.doc)(equalTo(Doc.Text(doc)))
      }
    )
}
