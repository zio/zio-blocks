package zio.blocks.schema

import zio.test._

object StructuralTypedStrictSpec extends ZIOSpecDefault {
  private case class PersonStrict(name: String, age: Int)

  // Use macro implemented in main sources to perform compile-time checks.
  // Macro: `RequireToStructuralMacro.requireToStructural[A]`.

  def spec = suite("StructuralTypedStrictSpec")(
    test("opt-in compile-time ToStructural presence check") {
      // This check is only active when the feature flag is enabled. It will
      // error at compile time if no `ToStructural` implicit is available.
      RequireToStructuralMacro.requireToStructural[PersonStrict]
      assertTrue(true)
    }
  )
}
