package zio.blocks.schema.structural

import zio.test._
import zio.blocks.schema._

object AsIntegrationSpec extends ZIOSpecDefault {
  def spec = suite("AsIntegrationSpec")(
    test("nominal to structural and back via As") {
      case class Person(name: String, age: Int)
      type PersonStructure = { def name: String; def age: Int }

      implicit val personSchema: Schema[Person]              = Schema.derived[Person]
      implicit val structuralSchema: Schema[PersonStructure] = Schema.derived[PersonStructure]

      val as       = As.derived[Person, PersonStructure]
      val original = Person("Alice", 30)

      val result = as.into(original).flatMap(as.from)

      assertTrue(result == Right(original))
    }
  )
}
