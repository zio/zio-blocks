package zio.blocks.schema

import zio.test._
import zio._

object RedemptionSpec extends ZIOSpecDefault {

  // Definiamo due case class semplici
  case class User(name: String, age: Int)
  case class Person(name: String, age: Int)

  def spec = suite("Redemption Arc: Macro Verification")(
    test("Should convert User to Person (Same fields)") {
      // Questa riga invoca la MACRO a compilazione
      val derivation = Into.derived[User, Person]

      val input  = User("Mario", 30)
      val result = derivation.into(input)

      // Verifichiamo che a runtime funzioni
      assertTrue(result == Right(Person("Mario", 30)))
    }
  )
}
