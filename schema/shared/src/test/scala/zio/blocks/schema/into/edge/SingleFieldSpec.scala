package zio.blocks.schema.into.edge

import zio.blocks.schema._
import zio.test._
import zio.test.Assertion._

/**
 * Tests for single-field case class conversions.
 *
 * Covers:
 *   - Single field to single field (same type)
 *   - Single field with type coercion
 *   - Single field to multi-field (with optional/defaults)
 *   - Single field to/from tuple
 */
object SingleFieldSpec extends ZIOSpecDefault {

  // === Single field case classes ===
  case class SingleInt(value: Int)
  case class SingleLong(value: Long)
  case class SingleString(name: String)
  case class SingleStringAlt(label: String)
  case class SingleOption(value: Option[Int])
  case class SingleList(values: List[Int])

  // === Target types ===
  case class WithExtra(value: Int, extra: Option[String])
  case class WithDefault(value: Int, name: String = "default")
  case class TwoFields(a: Int, b: String)

  def spec: Spec[TestEnvironment, Any] = suite("SingleFieldSpec")(
    suite("Single to Single - Same Type")(
      test("converts single-field case class with same type") {
        val source = SingleInt(42)
        val result = Into.derived[SingleInt, SingleInt].into(source)

        assert(result)(isRight(equalTo(SingleInt(42))))
      },
      test("converts single-field with different name but same type") {
        val source = SingleString("hello")
        val result = Into.derived[SingleString, SingleStringAlt].into(source)

        assert(result)(isRight(equalTo(SingleStringAlt("hello"))))
      }
    ),
    suite("Single to Single - Type Coercion")(
      test("widens Int to Long") {
        val source = SingleInt(100)
        val result = Into.derived[SingleInt, SingleLong].into(source)

        assert(result)(isRight(equalTo(SingleLong(100L))))
      },
      test("narrows Long to Int when value fits") {
        val source = SingleLong(50L)
        val result = Into.derived[SingleLong, SingleInt].into(source)

        assert(result)(isRight(equalTo(SingleInt(50))))
      },
      test("fails when narrowing would overflow") {
        val source = SingleLong(Long.MaxValue)
        val result = Into.derived[SingleLong, SingleInt].into(source)

        assert(result)(isLeft)
      }
    ),
    suite("Single to Multi-field with Optional")(
      test("single field to multi-field adds None for optional") {
        val source = SingleInt(10)
        val result = Into.derived[SingleInt, WithExtra].into(source)

        assert(result)(isRight(equalTo(WithExtra(10, None))))
      }
    ),
    suite("Single to Multi-field with Default")(
      test("single field to multi-field uses default") {
        val source = SingleInt(20)
        val result = Into.derived[SingleInt, WithDefault].into(source)

        assert(result)(isRight(equalTo(WithDefault(20, "default"))))
      }
    ),
    suite("Multi-field to Single")(
      test("multi-field to single drops extra fields") {
        val source = WithExtra(30, Some("ignored"))
        val result = Into.derived[WithExtra, SingleInt].into(source)

        assert(result)(isRight(equalTo(SingleInt(30))))
      },
      test("multi-field to single with optional None") {
        val source = WithExtra(40, None)
        val result = Into.derived[WithExtra, SingleInt].into(source)

        assert(result)(isRight(equalTo(SingleInt(40))))
      }
    ),
    suite("Single to Tuple1")(
      test("converts single-field case class to Tuple1") {
        val source = SingleInt(55)
        val result = Into.derived[SingleInt, Tuple1[Int]].into(source)

        assert(result)(isRight(equalTo(Tuple1(55))))
      },
      test("converts single-field to Tuple1 with coercion") {
        val source = SingleInt(66)
        val result = Into.derived[SingleInt, Tuple1[Long]].into(source)

        assert(result)(isRight(equalTo(Tuple1(66L))))
      }
    ),
    suite("Tuple1 to Single")(
      test("converts Tuple1 to single-field case class") {
        val source = Tuple1(77)
        val result = Into.derived[Tuple1[Int], SingleInt].into(source)

        assert(result)(isRight(equalTo(SingleInt(77))))
      },
      test("converts Tuple1 to single-field with coercion") {
        val source = Tuple1(88)
        val result = Into.derived[Tuple1[Int], SingleLong].into(source)

        assert(result)(isRight(equalTo(SingleLong(88L))))
      }
    ),
    suite("Single with Option Field")(
      test("converts single Option field - Some") {
        val source = SingleOption(Some(100))
        val result = Into.derived[SingleOption, SingleOption].into(source)

        assert(result)(isRight(equalTo(SingleOption(Some(100)))))
      },
      test("converts single Option field - None") {
        val source = SingleOption(None)
        val result = Into.derived[SingleOption, SingleOption].into(source)

        assert(result)(isRight(equalTo(SingleOption(None))))
      }
    ),
    suite("Single with Collection Field")(
      test("converts single List field") {
        val source = SingleList(List(1, 2, 3))
        val result = Into.derived[SingleList, SingleList].into(source)

        assert(result)(isRight(equalTo(SingleList(List(1, 2, 3)))))
      },
      test("converts empty List field") {
        val source = SingleList(Nil)
        val result = Into.derived[SingleList, SingleList].into(source)

        assert(result)(isRight(equalTo(SingleList(Nil))))
      }
    ),
    suite("Single with Custom Into")(
      test("uses implicit Into for field conversion") {
        case class Wrapper(inner: Int)
        case class SingleWrapper(value: Wrapper)
        case class SingleUnwrapped(value: Int)

        implicit val wrapperToInt: Into[Wrapper, Int] = (w: Wrapper) => Right(w.inner * 2)

        val source = SingleWrapper(Wrapper(21))
        val result = Into.derived[SingleWrapper, SingleUnwrapped].into(source)

        assert(result)(isRight(equalTo(SingleUnwrapped(42))))
      }
    )
  )
}
