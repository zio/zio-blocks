package zio.blocks.schema

import zio.test._

object StructuralConversionsSpec extends ZIOSpecDefault {
  private case class Person(name: String, age: Int)

  def spec = suite("StructuralConversionsSpec")(
    test("nominal -> structural -> nominal roundtrip") {
      val p                = Person("Bob", 42)
      given Schema[Person] = Schema.derived[Person]
      val dv               = StructuralConversions.fromNominal(p)
      val recovered        = StructuralConversions.toNominal[Person](dv)
      assertTrue(recovered == Right(p))
    }
  )
}
