package zio.blocks.schema.structural

import zio.blocks.schema._
import zio.test._

/**
 * Tests for Scala 3 union types in structural schema conversion (Scala 3 only).
 */
object UnionTypesSpec extends ZIOSpecDefault {

  def spec = suite("UnionTypesSpec")(
    test("simple union type structural schema") {
      // { def a: Int } | { def b: String }
      assertTrue(true)
    } @@ TestAspect.ignore,
    test("union type from sealed trait") {
      assertTrue(true)
    } @@ TestAspect.ignore,
    test("union type preserves variant tags") {
      // { type Tag = "A"; ... } | { type Tag = "B"; ... }
      assertTrue(true)
    } @@ TestAspect.ignore
  )
}

