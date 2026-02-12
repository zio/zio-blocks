package zio.blocks.schema

import zio.blocks.chunk.Chunk
import zio.blocks.docs.{Doc, Paragraph, Inline}
import zio.blocks.schema.binding.Binding
import zio.test.Assertion._
import zio.test._

object SchemaAspectSpec extends SchemaBaseSpec {

  private def textDoc(s: String): Doc =
    Doc(Chunk.single(Paragraph(Chunk.single(Inline.Text(s)))))
  case class Person(name: String, age: Int)

  object Person extends CompanionOptics[Person] {
    implicit val schema: Schema[Person] = Schema.derived
    val name: Lens[Person, String]      = optic(_.name)
    val age: Lens[Person, Int]          = optic(_.age)
    val x: Lens[Person, Boolean]        = // invalid lens
      Lens(schema.reflect.asRecord.get, Reflect.boolean[Binding].asTerm("x").asInstanceOf[Term.Bound[Person, Boolean]])
  }

  def spec: Spec[TestEnvironment, Any] =
    suite("SchemaAspectSpec")(
      test("identity aspect") {
        val updatedSchema = Person.schema @@ SchemaAspect.identity
        assert(updatedSchema)(equalTo(Person.schema))
      },
      test("doc aspect") {
        val doc           = "Person data type"
        val updatedSchema = Person.schema @@ SchemaAspect.doc(doc)
        assert(updatedSchema.doc)(equalTo(textDoc(doc)))
      },
      test("example aspect") {
        val p             = Person("Jaro", 34)
        val updatedSchema = Person.schema @@ SchemaAspect.examples(p)
        assert(updatedSchema.examples)(equalTo(Seq(p)))
      },
      test("update doc of a field") {
        val doc           = "name of the person"
        val updatedSchema = Person.schema @@ (Person.name, SchemaAspect.doc(doc))
        assert(updatedSchema.get(Person.name).get.doc)(equalTo(textDoc(doc))) &&
        assert(updatedSchema)(not(equalTo(Person.schema))) &&
        assert(Person.schema @@ (Person.x, SchemaAspect.doc(doc)))(equalTo(Person.schema)) // invalid lens
      }
    )
}
