package zio.blocks.schema

import zio.test._
import zio.test.Assertion._
import zio.{Chunk, NonEmptyChunk}

// Define data structures
case class PersonV1(name: String, age: Int)
case class PersonV2(name: String, age: Long)
case class PersonV3(fullName: String, yearsOld: Int)
case class PersonV4(name: String, age: Int, email: Option[String])
case class UserV1(id: Int, details: PersonV1)
case class UserV2(id: Long, details: PersonV2)
case class Coord(x: Int, y: Int)
case class Point(y: Int, x: Int)
case class Wrapper(value: Option[Int])
case class WrapperLong(value: Option[Long])
case class Unwrapped(value: Int)
case class Source(a: Long, b: Long)
case class Target(a: Int, b: Int)

// Helper object to isolate macro derivation from implicit usage
object TestInstances {
  // Deriving here is safe because no implicit Into[PersonV1, PersonV2] exists in this scope
  val personV1ToV2: Into[PersonV1, PersonV2] = Derive.into[PersonV1, PersonV2]
}

object IntoSpec extends ZIOSpecDefault {

  def spec = suite("Into & As Macro Derivation (Cross-Version)")(
    suite("Into[A, B] (One-Way)")(
      test("Exact field match") {
        val p1     = PersonV1("Alice", 30)
        val result = Derive.into[PersonV1, PersonV1].into(p1)
        assert(result)(isRight(equalTo(p1)))
      },
      test("Field reordering (by name)") {
        val c      = Coord(10, 20)
        val result = Derive.into[Coord, Point].into(c)
        assert(result)(isRight(equalTo(Point(20, 10))))
      },
      test("Numeric Widening (Int -> Long)") {
        val p1     = PersonV1("Alice", 30)
        val result = Derive.into[PersonV1, PersonV2].into(p1)
        assert(result)(isRight(equalTo(PersonV2("Alice", 30L))))
      },
      test("Numeric Narrowing (Long -> Int) - Success") {
        val p2     = PersonV2("Alice", 30L)
        val result = Derive.into[PersonV2, PersonV1].into(p2)
        assert(result)(isRight(equalTo(PersonV1("Alice", 30))))
      },
      test("Numeric Narrowing (Long -> Int) - Failure (Overflow)") {
        val p2     = PersonV2("Alice", Long.MaxValue)
        val result = Derive.into[PersonV2, PersonV1].into(p2)

        assert(result.isLeft)(isTrue) &&
        assert(result.left.getOrElse(throw new Exception("fail")).message)(containsString("Numeric overflow"))
      },
      test("Unique Type Disambiguation") {
        val p1     = PersonV1("Alice", 30)
        val result = Derive.into[PersonV1, PersonV3].into(p1)
        assert(result)(isRight(equalTo(PersonV3("Alice", 30))))
      },
      test("Add Optional Field (Missing in Source)") {
        val p1     = PersonV1("Alice", 30)
        val result = Derive.into[PersonV1, PersonV4].into(p1)
        assert(result)(isRight(equalTo(PersonV4("Alice", 30, None))))
      },
      test("Recursive/Nested Derivation") {
        val u1     = UserV1(100, PersonV1("Alice", 30))
        val result = Derive.into[UserV1, UserV2].into(u1)
        assert(result)(isRight(equalTo(UserV2(100L, PersonV2("Alice", 30L)))))
      },
      test("Tuple to Case Class") {
        val tuple  = ("Alice", 30)
        val result = Derive.into[(String, Int), PersonV1].into(tuple)
        assert(result)(isRight(equalTo(PersonV1("Alice", 30))))
      },
      suite("Collections")(
        test("List -> List (with element conversion)") {
          // Bring the pre-derived instance into implicit scope for this test
          implicit val p1ToP2: Into[PersonV1, PersonV2] = TestInstances.personV1ToV2

          val src = List(PersonV1("A", 1), PersonV1("B", 2))
          // Into.listInto will pick up p1ToP2 automatically
          val res = Into[List[PersonV1], List[PersonV2]].into(src)
          assert(res)(isRight(equalTo(List(PersonV2("A", 1L), PersonV2("B", 2L)))))
        },
        test("Option[A] -> Option[B] (Some)") {
          val src = Wrapper(Some(10))
          val res = Derive.into[Wrapper, WrapperLong].into(src)
          assert(res)(isRight(equalTo(WrapperLong(Some(10L)))))
        },
        test("Option[A] -> Option[B] (None)") {
          val src = Wrapper(None)
          val res = Derive.into[Wrapper, WrapperLong].into(src)
          assert(res)(isRight(equalTo(WrapperLong(None))))
        },
        test("Option[A] -> A (Unwrap Some)") {
          implicit val optToInt: Into[Option[Int], Int] = Into.optionToRequired("value")
          val res                                       = Derive.into[Wrapper, Unwrapped].into(Wrapper(Some(42)))
          assert(res)(isRight(equalTo(Unwrapped(42))))
        },
        test("Option[A] -> A (Fail None)") {
          implicit val optToInt: Into[Option[Int], Int] = Into.optionToRequired("value")
          val res                                       = Derive.into[Wrapper, Unwrapped].into(Wrapper(None))
          assert(res.isLeft)(isTrue)
        }
      )
    ),
    suite("As[A, B] (Bidirectional)")(
      test("Round Trip (Identity-like)") {
        val p1 = PersonV1("Alice", 30)
        val as = Derive.as[PersonV1, PersonV1]

        assert(as.to(p1))(isRight(equalTo(p1))) &&
        assert(as.from(p1))(isRight(equalTo(p1)))
      },
      test("Round Trip (Renaming)") {
        val p1 = PersonV1("Alice", 30)
        val p3 = PersonV3("Alice", 30)
        val as = Derive.as[PersonV1, PersonV3]

        assert(as.to(p1))(isRight(equalTo(p3))) &&
        assert(as.from(p3))(isRight(equalTo(p1)))
      },
      test("Round Trip with Numeric Narrowing (Safe)") {
        val p1 = PersonV1("Alice", 30)
        val p2 = PersonV2("Alice", 30L)
        val as = Derive.as[PersonV1, PersonV2]

        assert(as.to(p1))(isRight(equalTo(p2))) &&
        assert(as.from(p2))(isRight(equalTo(p1)))
      },
      test("Round Trip Failure (Overflow)") {
        val p2 = PersonV2("Alice", Long.MaxValue)
        val as = Derive.as[PersonV1, PersonV2]
        assert(as.from(p2).isLeft)(isTrue)
      }
    ),
    suite("Error Accumulation")(
      test("Collects multiple errors") {
        val src = Source(Long.MaxValue, Long.MaxValue)
        val res = Derive.into[Source, Target].into(src)

        assert(res.isLeft)(isTrue) &&
        assert(res.left.getOrElse(throw new Exception("fail")).flatten.size)(equalTo(2))
      }
    )
  )
}


