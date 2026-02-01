package zio.blocks.combinators

import zio.test._

object EitherAlternatorSpec extends ZIOSpecDefault {

  def spec = suite("EitherAlternator")(
    suite("different types - left and right injection")(
      test("Either[Int, String]: left injects Int") {
        val alt                       = implicitly[EitherAlternator.WithOut[Int, String, Either[Int, String]]]
        val left: Either[Int, String] = alt.left(42)
        assertTrue(left == Left(42))
      },
      test("Either[Int, String]: right injects String") {
        val alt                        = implicitly[EitherAlternator.WithOut[Int, String, Either[Int, String]]]
        val right: Either[Int, String] = alt.right("hello")
        assertTrue(right == Right("hello"))
      },
      test("Either[Boolean, Double]: left and right") {
        val alt                            = implicitly[EitherAlternator.WithOut[Boolean, Double, Either[Boolean, Double]]]
        val left: Either[Boolean, Double]  = alt.left(true)
        val right: Either[Boolean, Double] = alt.right(3.14)
        assertTrue(left == Left(true) && right == Right(3.14))
      }
    ),
    suite("different types - unleft extraction")(
      test("unleft extracts left Int value") {
        val alt  = implicitly[EitherAlternator.WithOut[Int, String, Either[Int, String]]]
        val left = alt.left(42)
        assertTrue(alt.unleft(left) == Some(42))
      },
      test("unleft returns None for right value") {
        val alt   = implicitly[EitherAlternator.WithOut[Int, String, Either[Int, String]]]
        val right = alt.right("hello")
        assertTrue(alt.unleft(right) == None)
      },
      test("unleft with complex types") {
        val alt   = implicitly[EitherAlternator.WithOut[List[Int], Map[String, Int], Either[List[Int], Map[String, Int]]]]
        val left  = alt.left(List(1, 2, 3))
        val right = alt.right(Map("a" -> 1))
        assertTrue(
          alt.unleft(left) == Some(List(1, 2, 3)) &&
            alt.unleft(right) == None
        )
      }
    ),
    suite("different types - unright extraction")(
      test("unright extracts right String value") {
        val alt   = implicitly[EitherAlternator.WithOut[Int, String, Either[Int, String]]]
        val right = alt.right("hello")
        assertTrue(alt.unright(right) == Some("hello"))
      },
      test("unright returns None for left value") {
        val alt  = implicitly[EitherAlternator.WithOut[Int, String, Either[Int, String]]]
        val left = alt.left(42)
        assertTrue(alt.unright(left) == None)
      },
      test("unright with complex types") {
        val alt   = implicitly[EitherAlternator.WithOut[List[Int], Map[String, Int], Either[List[Int], Map[String, Int]]]]
        val left  = alt.left(List(1, 2, 3))
        val right = alt.right(Map("a" -> 1))
        assertTrue(
          alt.unright(right) == Some(Map("a" -> 1)) &&
            alt.unright(left) == None
        )
      }
    ),
    suite("round-trip property")(
      test("left value round-trips through unleft") {
        val alt      = implicitly[EitherAlternator.WithOut[Int, String, Either[Int, String]]]
        val original = 42
        val result   = alt.left(original)
        assertTrue(alt.unleft(result).contains(original))
      },
      test("right value round-trips through unright") {
        val alt      = implicitly[EitherAlternator.WithOut[Int, String, Either[Int, String]]]
        val original = "hello"
        val result   = alt.right(original)
        assertTrue(alt.unright(result).contains(original))
      }
    ),
    suite("type inference")(
      test("alternator instance exists for Int and String") {
        val alt = implicitly[EitherAlternator[Int, String]]
        assertTrue(alt != null)
      },
      test("alternator Out type is correctly inferred as Either") {
        val alt = implicitly[EitherAlternator.WithOut[Int, String, Either[Int, String]]]
        assertTrue(alt != null)
      },
      test("alternator works with different type combinations") {
        val alt1 = implicitly[EitherAlternator[String, Int]]
        val alt2 = implicitly[EitherAlternator[Boolean, List[String]]]
        val alt3 = implicitly[EitherAlternator[Option[Int], Either[String, Boolean]]]
        assertTrue(alt1 != null && alt2 != null && alt3 != null)
      }
    ),
    suite("same types - Either[A, A]")(
      test("EitherAlternator[Int, Int] instance resolves") {
        val alt = implicitly[EitherAlternator.WithOut[Int, Int, Either[Int, Int]]]
        assertTrue(alt != null)
      },
      test("left(1) produces Left(1)") {
        val alt    = implicitly[EitherAlternator.WithOut[Int, Int, Either[Int, Int]]]
        val result = alt.left(1)
        assertTrue(result == Left(1))
      },
      test("right(2) produces Right(2)") {
        val alt    = implicitly[EitherAlternator.WithOut[Int, Int, Either[Int, Int]]]
        val result = alt.right(2)
        assertTrue(result == Right(2))
      },
      test("unleft extracts from Left") {
        val alt  = implicitly[EitherAlternator.WithOut[Int, Int, Either[Int, Int]]]
        val left = alt.left(42)
        assertTrue(alt.unleft(left) == Some(42))
      },
      test("unright extracts from Right") {
        val alt   = implicitly[EitherAlternator.WithOut[Int, Int, Either[Int, Int]]]
        val right = alt.right(99)
        assertTrue(alt.unright(right) == Some(99))
      },
      test("unleft returns None for Right") {
        val alt   = implicitly[EitherAlternator.WithOut[Int, Int, Either[Int, Int]]]
        val right = alt.right(1)
        assertTrue(alt.unleft(right) == None)
      },
      test("unright returns None for Left") {
        val alt  = implicitly[EitherAlternator.WithOut[Int, Int, Either[Int, Int]]]
        val left = alt.left(1)
        assertTrue(alt.unright(left) == None)
      }
    )
  )
}
