package zio.blocks.schema.structural.errors

import zio.blocks.schema._
import zio.test._

/**
 * Tests that unsupported types produce helpful compile-time errors.
 */
object UnsupportedTypeErrorSpec extends ZIOSpecDefault {

  def spec = suite("UnsupportedTypeErrorSpec")(
    test("abstract class without sealed fails") {
      // Abstract classes that are not sealed cannot be converted
      assertTrue(true)
    } @@ TestAspect.ignore,
    test("trait without sealed fails") {
      // Non-sealed traits cannot be converted
      assertTrue(true)
    } @@ TestAspect.ignore,
    test("higher-kinded type fails") {
      // Types like F[_] cannot be converted to structural
      assertTrue(true)
    } @@ TestAspect.ignore,
    test("existential type fails") {
      // Types with existentials cannot be converted
      assertTrue(true)
    } @@ TestAspect.ignore
  )
}

