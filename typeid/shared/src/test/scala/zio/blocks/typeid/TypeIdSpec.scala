package zio.blocks.typeid

import zio.test.*

object TypeIdSpec extends ZIOSpecDefault {
  def spec = suite("TypeIdSpec")(
    suite("derive")(
      test("derive primitive types") {
        val intId = TypeId.derive[Int]
        assertTrue(intId.name == "Int") &&
        assertTrue(intId.owner.segments.last.name == "scala")
      },
      test("derive case class") {
        case class Person(name: String, age: Int)
        val personId = TypeId.derive[Person]
        assertTrue(personId.name == "Person")
      },
      test("derive nested case class") {
        case class Address(city: String)
        case class User(name: String, address: Address)
        val userId = TypeId.derive[User]
        assertTrue(userId.name == "User")
      }
    )
  )
}
