package zio.blocks.combinators

import zio.test._
import scala.compiletime.testing.{typeCheckErrors, Error as CompileError}

object UnionAlternatorSpec extends ZIOSpecDefault {

  def spec = suite("UnionAlternator")(
    suite("same-type rejection")(
      test("same-type alternation should not compile") {
        val errors = typeCheckErrors("summon[UnionAlternator.WithOut[Int, Int, ?]]")
        assertTrue(errors.nonEmpty)
      },
      test("String same-type alternation should not compile") {
        val errors = typeCheckErrors("summon[UnionAlternator.WithOut[String, String, ?]]")
        assertTrue(errors.nonEmpty)
      }
    ),
    suite("different types - left and right injection")(
      test("Int | String: left injects Int") {
        val alt                = summon[UnionAlternator.WithOut[Int, String, Int | String]]
        val left: Int | String = alt.left(42)
        assertTrue(left == 42)
      },
      test("Int | String: right injects String") {
        val alt                 = summon[UnionAlternator.WithOut[Int, String, Int | String]]
        val right: Int | String = alt.right("hello")
        assertTrue(right == "hello")
      },
      test("Boolean | Double: left and right") {
        val alt                     = summon[UnionAlternator.WithOut[Boolean, Double, Boolean | Double]]
        val left: Boolean | Double  = alt.left(true)
        val right: Boolean | Double = alt.right(3.14)
        assertTrue(left == true && right == 3.14)
      }
    ),
    suite("different types - unleft extraction")(
      test("unleft extracts Int from union containing Int") {
        val alt                = summon[UnionAlternator.WithOut[Int, String, Int | String]]
        val left: Int | String = alt.left(42)
        assertTrue(alt.unleft(left).contains(42))
      }
    ),
    suite("different types - unright extraction")(
      test("unright extracts String from union containing String") {
        val alt                 = summon[UnionAlternator.WithOut[Int, String, Int | String]]
        val right: Int | String = alt.right("hello")
        assertTrue(alt.unright(right).contains("hello"))
      }
    ),

    suite("type inference")(
      test("alternator instance exists for Int and String") {
        val alt = summon[UnionAlternator[Int, String]]
        assertTrue(alt != null)
      },
      test("alternator Out type is correctly inferred as union") {
        val alt = summon[UnionAlternator.WithOut[Int, String, Int | String]]
        assertTrue(alt != null)
      }
    )
  )
}
