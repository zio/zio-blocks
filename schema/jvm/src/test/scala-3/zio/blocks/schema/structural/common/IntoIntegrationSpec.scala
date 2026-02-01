package zio.blocks.schema.structural.common
import zio.blocks.schema.SchemaBaseSpec

import zio.blocks.schema._
import zio.test._

/** Tests for Into integration with structural types. */
object IntoIntegrationSpec extends SchemaBaseSpec {

  case class Person(name: String, age: Int)
  type PersonStructure = { def name: String; def age: Int }

  def spec: Spec[Any, Nothing] = suite("IntoIntegrationSpec")(
    test("nominal to structural via Into") {
      val person = Person("Alice", 30)
      val into   = Into.derived[Person, PersonStructure]
      val result = into.into(person)
      assertTrue(result.isRight)
    },
    test("nominal to structural preserves data") {
      val person = Person("Bob", 25)
      val into   = Into.derived[Person, PersonStructure]
      val result = into.into(person)

      result match {
        case Right(r) =>
          val nameMethod = r.getClass.getMethod("name")
          val ageMethod  = r.getClass.getMethod("age")
          assertTrue(nameMethod.invoke(r) == "Bob", ageMethod.invoke(r) == 25)
        case Left(err) =>
          assertTrue(false) ?? s"Conversion failed: $err"
      }
    }
  )
}
