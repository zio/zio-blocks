package zio.blocks.schema

import zio.test._
import zio._

object IntoSpec extends ZIOSpecDefault {

  def spec = suite("Into Support")(
    suite("Product Types")(
      test("Should convert case class to case class") {
        case class PersonV1(name: String, age: Int)
        case class PersonV2(name: String, age: Int)

        val derivation = Into.derived[PersonV1, PersonV2]
        val input      = PersonV1("Alice", 30)
        val result     = derivation.into(input)

        assertTrue(result == Right(PersonV2("Alice", 30)))
      }
    ),
    /* Structural types tests commented out due to SIP-44 limitation
    suite("Structural types")(
      test("Should convert structural types") {
        // Test implementation would go here
        assertTrue(true)
      }
    )
     */
    suite("Other Tests")(
      test("Placeholder test") {
        assertTrue(true)
      }
    )
  )
}
