package zio.blocks.combinators

import scala.compiletime.testing.typeCheckErrors
import zio.test._

object UnionsSpec extends ZIOSpecDefault {

  def spec = suite("Unions")(
    suite("Combiner")(
      suite("Combine Either to union")(
        test("combine Left returns left value as union") {
          val combiner                   = summon[Unions.Combiner.WithOut[Int, String, Int | String]]
          val input: Either[Int, String] = Left(42)
          val result: Int | String       = combiner.combine(input)
          assertTrue(result == 42)
        },
        test("combine Right returns right value as union") {
          val combiner                   = summon[Unions.Combiner.WithOut[Int, String, Int | String]]
          val input: Either[Int, String] = Right("hello")
          val result: Int | String       = combiner.combine(input)
          assertTrue(result == "hello")
        },
        test("combine with Boolean types") {
          val combiner                            = summon[Unions.Combiner.WithOut[Boolean, Double, Boolean | Double]]
          val inputLeft: Either[Boolean, Double]  = Left(true)
          val inputRight: Either[Boolean, Double] = Right(3.14)
          val resultLeft: Boolean | Double        = combiner.combine(inputLeft)
          val resultRight: Boolean | Double       = combiner.combine(inputRight)
          assertTrue(resultLeft == true && resultRight == 3.14)
        }
      )
    ),
    suite("Separator")(
      suite("Separate union to Either")(
        test("separate discriminates Int from String union") {
          val separator              = summon[Unions.Separator.WithTypes[Int | String, Int, String]]
          val intValue: Int | String = 42
          val strValue: Int | String = "hello"
          val resultInt              = separator.separate(intValue)
          val resultStr              = separator.separate(strValue)
          assertTrue(resultInt == Left(42) && resultStr == Right("hello"))
        },
        test("separate discriminates rightmost type in 3-way union") {
          val separator                         = summon[Unions.Separator.WithTypes[Int | String | Boolean, Int | String, Boolean]]
          val boolValue: Int | String | Boolean = true
          val intValue: Int | String | Boolean  = 42
          val strValue: Int | String | Boolean  = "hello"
          val resultBool                        = separator.separate(boolValue)
          val resultInt                         = separator.separate(intValue)
          val resultStr                         = separator.separate(strValue)
          assertTrue(
            resultBool == Right(true) &&
              resultInt == Left(42) &&
              resultStr == Left("hello")
          )
        }
      ),
      suite("Type discrimination")(
        test("discriminates case class types") {
          case class Foo(x: Int)
          case class Bar(y: String)
          val separator           = summon[Unions.Separator.WithTypes[Foo | Bar, Foo, Bar]]
          val fooValue: Foo | Bar = Foo(1)
          val barValue: Foo | Bar = Bar("test")
          val resultFoo           = separator.separate(fooValue)
          val resultBar           = separator.separate(barValue)
          assertTrue(resultFoo == Left(Foo(1)) && resultBar == Right(Bar("test")))
        }
      )
    ),
    suite("Roundtrip")(
      test("separate(combine(Left(x))) == Left(x)") {
        val combiner                   = summon[Unions.Combiner.WithOut[Int, String, Int | String]]
        val separator                  = summon[Unions.Separator.WithTypes[Int | String, Int, String]]
        val input: Either[Int, String] = Left(42)
        val combined                   = combiner.combine(input)
        val separated                  = separator.separate(combined)
        assertTrue(separated == Left(42))
      },
      test("separate(combine(Right(x))) == Right(x)") {
        val combiner                   = summon[Unions.Combiner.WithOut[Int, String, Int | String]]
        val separator                  = summon[Unions.Separator.WithTypes[Int | String, Int, String]]
        val input: Either[Int, String] = Right("hello")
        val combined                   = combiner.combine(input)
        val separated                  = separator.separate(combined)
        assertTrue(separated == Right("hello"))
      },
      test("combine(separate(union)) == union for left value") {
        val combiner               = summon[Unions.Combiner.WithOut[Int, String, Int | String]]
        val separator              = summon[Unions.Separator.WithTypes[Int | String, Int, String]]
        val original: Int | String = 42
        val separated              = separator.separate(original)
        val recombined             = combiner.combine(separated)
        assertTrue(recombined == 42)
      },
      test("combine(separate(union)) == union for right value") {
        val combiner               = summon[Unions.Combiner.WithOut[Int, String, Int | String]]
        val separator              = summon[Unions.Separator.WithTypes[Int | String, Int, String]]
        val original: Int | String = "hello"
        val separated              = separator.separate(original)
        val recombined             = combiner.combine(separated)
        assertTrue(recombined == "hello")
      }
    ),
    suite("Top-level convenience methods")(
      test("Unions.combine works without explicit combiner") {
        val input: Either[Int, String] = Left(42)
        val result: Int | String       = Unions.combine(input)
        assertTrue(result == 42)
      },
      test("Unions.combine Right works without explicit combiner") {
        val input: Either[Int, String] = Right("hello")
        val result: Int | String       = Unions.combine(input)
        assertTrue(result == "hello")
      },
      test("Unions.separate works with explicit separator") {
        val separator          = summon[Unions.Separator.WithTypes[Int | String, Int, String]]
        val input: Int | String = "hello"
        val result              = separator.separate(input)
        assertTrue(result == Right("hello"))
      }
    ),
    suite("Compile-time uniqueness check")(
      test("Separator[Int | Int] fails with uniqueness error") {
        val errors   = typeCheckErrors("summon[Unions.Separator.WithTypes[Int, Int, Int]]")
        val expected = "Union types must contain unique types. Found duplicate types in the union. Use Either, a wrapper type, opaque type, or newtype to distinguish values of the same underlying type."
        assertTrue(
          errors.length == 1,
          errors.head.message == expected
        )
      },
      test("Separator[String | String] with duplicate types fails with uniqueness error") {
        val errors   = typeCheckErrors("summon[Unions.Separator.WithTypes[String | Int, String | Int, String]]")
        val expected = "Union types must contain unique types. Found duplicate types in the union. Use Either, a wrapper type, opaque type, or newtype to distinguish values of the same underlying type."
        assertTrue(
          errors.length == 1,
          errors.head.message == expected
        )
      },
      test("Separator with three unique types compiles successfully") {
        val errors = typeCheckErrors("summon[Unions.Separator.WithTypes[Int | String | Boolean, Int | String, Boolean]]")
        assertTrue(errors.isEmpty)
      },
      test("Separator[String | Int, String | Int, String] fails when R is contained in L") {
        val errors   = typeCheckErrors("summon[Unions.Separator.WithTypes[String | Int, String | Int, String]]")
        val expected = "Union types must contain unique types. Found duplicate types in the union. Use Either, a wrapper type, opaque type, or newtype to distinguish values of the same underlying type."
        assertTrue(
          errors.length == 1,
          errors.head.message == expected
        )
      }
    )
  )
}
