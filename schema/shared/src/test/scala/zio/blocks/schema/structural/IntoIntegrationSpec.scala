package zio.blocks.schema.structural

import zio.test._
import zio.blocks.schema._

object IntoIntegrationSpec extends ZIOSpecDefault {
  def spec = suite("IntoIntegrationSpec")(
    test("nominal to structural conversion via Into") {
      case class Person(name: String, age: Int)
      type PersonStructure = { def name: String; def age: Int }

      implicit val personSchema: Schema[Person]              = Schema.derived[Person]
      implicit val structuralSchema: Schema[PersonStructure] = Schema.derived[PersonStructure]

      val person = Person("Alice", 30)
      val into   = Into.derived[Person, PersonStructure]

      val result = into.into(person)
      assertTrue(result.isRight)

      // Convert back using another Into to verify content
      val back      = Into.derived[PersonStructure, Person]
      val roundTrip = result.flatMap(back.into)

      assertTrue(roundTrip == Right(person))
    }
  )
}
