package zio.blocks.schema

import zio.Scope
import zio.test.Assertion._
import zio.test._

object SchemaAspectSpec extends ZIOSpecDefault {

  def spec: Spec[TestEnvironment with Scope, Any] =
    suite("SchemaAspectSpec")(
      test("identity aspect") {
        final case class Person(name: String, age: Int)
        implicit val schema: Schema[Person] = Schema.derived

        val updatedSchema = schema @@ SchemaAspect.identity

        assert(updatedSchema)(equalTo(schema))
      },
      test("doc aspect") {
        final case class Person(name: String, age: Int)
        implicit val schema: Schema[Person] = Schema.derived

        val doc = "Person data`type"

        val updatedSchema = schema @@ SchemaAspect.doc(doc)

        assert(updatedSchema.doc)(equalTo(Doc.Text(doc)))
      }
    //   test("example aspect") {
    //     final case class Person(name: String, age: Int)
    //     implicit val schema: Schema[Person] = Schema.derived

    //     val p = Person("Jaro", 34)

    //     case class Hey(name: String)

    //     val h = Hey("aaa")

    //     val updatedSchema = schema @@ SchemaAspect.examples(h)

    //     assert(updatedSchema.examples)(equalTo(Seq(p)))
    //   }
    )
}
