package zio.blocks.schema

import zio.test._

object StructuralTypedSpec extends ZIOSpecDefault {
  private case class Person(name: String, age: Int)

  // Compile-time check (permissive): ensure a `ToStructural` instance is available
  // for `Person`. This guarantees the `derivedTyped` inline given materializes (typed
  // or fallback). Strict member-access tests will be added once the macro stabilizes.
  private val _ = summon[ToStructural[Person]]

  def spec = suite("StructuralTypedSpec")(
    test("placeholder - compile time typed checks (skeleton)") {
      // TODO: replace with real compile-time checks when the macro produces
      // a concrete structural type. Example (future):
      //
      // // after macro: Schema.derived[Person].StructuralType == generated trait
      // summon[ToStructural.Aux[Person, GeneratedTrait]]
      // val s: Schema[GeneratedTrait] = Schema.derived[Person].structural
      // then use generated members with static typing

      assertTrue(true)
    }
  )
}
