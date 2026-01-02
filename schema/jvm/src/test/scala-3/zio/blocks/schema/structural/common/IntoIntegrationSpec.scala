package zio.blocks.schema.structural.common

import zio.blocks.schema._
import zio.test._
import scala.language.reflectiveCalls

/**
 * Tests for Into integration with structural types.
 * 
 * Note: Structural type as source (structural -> nominal) requires JVM reflection
 * and is tested in the JVM-specific StructuralTypeSourceSpec.
 */
object IntoIntegrationSpec extends ZIOSpecDefault {

  case class Person(name: String, age: Int)
  case class PersonWide(name: String, age: Int, email: String)

  type PersonStructure = { def name: String; def age: Int }

  def spec = suite("IntoIntegrationSpec")(
    test("nominal to structural via Into") {
      val person = Person("Alice", 30)
      val into = Into.derived[Person, PersonStructure]
      val result = into.into(person)

      // Conversion should succeed
      assertTrue(result.isRight)
    },
    test("nominal to structural preserves data") {
      val person = Person("Bob", 25)
      val into = Into.derived[Person, PersonStructure]
      val result = into.into(person)

      result match {
        case Right(r) =>
          // Access fields through reflection (JVM only)
          val nameMethod = r.getClass.getMethod("name")
          val ageMethod = r.getClass.getMethod("age")
          assertTrue(
            nameMethod.invoke(r) == "Bob",
            ageMethod.invoke(r) == 25
          )
        case Left(err) =>
          assertTrue(false) ?? s"Conversion failed: $err"
      }
    },
    test("Into with field subset") {
      // Convert Person with more fields to structural with fewer
      val person = PersonWide("Carol", 28, "carol@example.com")
      val into = Into.derived[PersonWide, PersonStructure]
      val result = into.into(person)

      assertTrue(result.isRight)
    },
    test("Into with field type coercion") {
      case class Source(value: Int)
      type StructuralInt = { def value: Int }

      val source = Source(42)
      val intoStructural = Into.derived[Source, StructuralInt]
      val result = intoStructural.into(source)

      assertTrue(result.isRight)
    }
  )
}
