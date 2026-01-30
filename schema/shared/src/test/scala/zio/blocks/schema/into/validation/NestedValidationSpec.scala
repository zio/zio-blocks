package zio.blocks.schema.into.validation

import zio.blocks.schema._
import zio.test._
import zio.test.Assertion._

/**
 * Tests for nested validation scenarios in Into derivation.
 *
 * Validates that errors propagate correctly through nested structures.
 */
object NestedValidationSpec extends ZIOSpecDefault {

  // === Nested Case Classes ===
  case class Inner(value: Long)
  case class InnerInt(value: Int)

  case class Middle(inner: Inner, name: String)
  case class MiddleInt(inner: InnerInt, name: String)

  case class Outer(middle: Middle, id: Long)
  case class OuterInt(middle: MiddleInt, id: Int)

  // === Lists with Nested Types ===
  case class ContainsListLong(items: List[Long])
  case class ContainsListInt(items: List[Int])

  case class ContainsNestedList(items: List[Inner])
  case class ContainsNestedListInt(items: List[InnerInt])

  // === Options with Nested Types ===
  case class ContainsOptionLong(value: Option[Long])
  case class ContainsOptionInt(value: Option[Int])

  case class ContainsOptionNested(value: Option[Inner])
  case class ContainsOptionNestedInt(value: Option[InnerInt])

  // === Deep Nesting ===
  case class Level4(value: Long)
  case class Level4Int(value: Int)
  case class Level3(level4: Level4)
  case class Level3Int(level4: Level4Int)
  case class Level2(level3: Level3)
  case class Level2Int(level3: Level3Int)
  case class Level1(level2: Level2)
  case class Level1Int(level2: Level2Int)

  def spec: Spec[TestEnvironment, Any] = suite("NestedValidationSpec")(
    suite("Single Level Nesting")(
      test("succeeds when inner value fits") {
        implicit val innerInto: Into[Inner, InnerInt] = Into.derived[Inner, InnerInt]
        val source                                    = Middle(Inner(100L), "test")
        val result                                    = Into.derived[Middle, MiddleInt].into(source)

        assert(result)(isRight(equalTo(MiddleInt(InnerInt(100), "test"))))
      },
      test("fails when inner value overflows") {
        implicit val innerInto: Into[Inner, InnerInt] = Into.derived[Inner, InnerInt]
        val source                                    = Middle(Inner(Long.MaxValue), "test")
        val result                                    = Into.derived[Middle, MiddleInt].into(source)

        assert(result)(isLeft)
      }
    ),
    suite("Two Level Nesting")(
      test("succeeds when all values fit") {
        implicit val innerInto: Into[Inner, InnerInt]    = Into.derived[Inner, InnerInt]
        implicit val middleInto: Into[Middle, MiddleInt] = Into.derived[Middle, MiddleInt]
        val source                                       = Outer(Middle(Inner(100L), "test"), 200L)
        val result                                       = Into.derived[Outer, OuterInt].into(source)

        assert(result)(isRight(equalTo(OuterInt(MiddleInt(InnerInt(100), "test"), 200))))
      },
      test("fails when deeply nested value overflows") {
        implicit val innerInto: Into[Inner, InnerInt]    = Into.derived[Inner, InnerInt]
        implicit val middleInto: Into[Middle, MiddleInt] = Into.derived[Middle, MiddleInt]
        val source                                       = Outer(Middle(Inner(Long.MaxValue), "test"), 200L)
        val result                                       = Into.derived[Outer, OuterInt].into(source)

        assert(result)(isLeft)
      },
      test("fails when outer value overflows") {
        implicit val innerInto: Into[Inner, InnerInt]    = Into.derived[Inner, InnerInt]
        implicit val middleInto: Into[Middle, MiddleInt] = Into.derived[Middle, MiddleInt]
        val source                                       = Outer(Middle(Inner(100L), "test"), Long.MaxValue)
        val result                                       = Into.derived[Outer, OuterInt].into(source)

        assert(result)(isLeft)
      }
    ),
    suite("Collection with Narrowing")(
      test("succeeds when all list elements fit") {
        val source = ContainsListLong(List(1L, 2L, 3L))
        val result = Into.derived[ContainsListLong, ContainsListInt].into(source)

        assert(result)(isRight(equalTo(ContainsListInt(List(1, 2, 3)))))
      },
      test("fails when any list element overflows") {
        val source = ContainsListLong(List(1L, Long.MaxValue, 3L))
        val result = Into.derived[ContainsListLong, ContainsListInt].into(source)

        assert(result)(isLeft)
      },
      test("succeeds with empty list") {
        val source = ContainsListLong(List.empty)
        val result = Into.derived[ContainsListLong, ContainsListInt].into(source)

        assert(result)(isRight(equalTo(ContainsListInt(List.empty))))
      }
    ),
    suite("Nested Types in Collection")(
      test("succeeds when all nested elements fit") {
        implicit val innerInto: Into[Inner, InnerInt] = Into.derived[Inner, InnerInt]
        val source                                    = ContainsNestedList(List(Inner(1L), Inner(2L)))
        val result                                    = Into.derived[ContainsNestedList, ContainsNestedListInt].into(source)

        assert(result)(isRight(equalTo(ContainsNestedListInt(List(InnerInt(1), InnerInt(2))))))
      },
      test("fails when any nested element overflows") {
        implicit val innerInto: Into[Inner, InnerInt] = Into.derived[Inner, InnerInt]
        val source                                    = ContainsNestedList(List(Inner(1L), Inner(Long.MaxValue)))
        val result                                    = Into.derived[ContainsNestedList, ContainsNestedListInt].into(source)

        assert(result)(isLeft)
      }
    ),
    suite("Option with Narrowing")(
      test("succeeds when Some value fits") {
        val source = ContainsOptionLong(Some(100L))
        val result = Into.derived[ContainsOptionLong, ContainsOptionInt].into(source)

        assert(result)(isRight(equalTo(ContainsOptionInt(Some(100)))))
      },
      test("fails when Some value overflows") {
        val source = ContainsOptionLong(Some(Long.MaxValue))
        val result = Into.derived[ContainsOptionLong, ContainsOptionInt].into(source)

        assert(result)(isLeft)
      },
      test("succeeds with None") {
        val source = ContainsOptionLong(None)
        val result = Into.derived[ContainsOptionLong, ContainsOptionInt].into(source)

        assert(result)(isRight(equalTo(ContainsOptionInt(None))))
      }
    ),
    suite("Nested Types in Option")(
      test("succeeds when nested Some value fits") {
        implicit val innerInto: Into[Inner, InnerInt] = Into.derived[Inner, InnerInt]
        val source                                    = ContainsOptionNested(Some(Inner(100L)))
        val result                                    = Into.derived[ContainsOptionNested, ContainsOptionNestedInt].into(source)

        assert(result)(isRight(equalTo(ContainsOptionNestedInt(Some(InnerInt(100))))))
      },
      test("fails when nested Some value overflows") {
        implicit val innerInto: Into[Inner, InnerInt] = Into.derived[Inner, InnerInt]
        val source                                    = ContainsOptionNested(Some(Inner(Long.MaxValue)))
        val result                                    = Into.derived[ContainsOptionNested, ContainsOptionNestedInt].into(source)

        assert(result)(isLeft)
      }
    ),
    suite("Deep Nesting (4 Levels)")(
      test("succeeds when deepest value fits") {
        implicit val level4Into: Into[Level4, Level4Int] = Into.derived[Level4, Level4Int]
        implicit val level3Into: Into[Level3, Level3Int] = Into.derived[Level3, Level3Int]
        implicit val level2Into: Into[Level2, Level2Int] = Into.derived[Level2, Level2Int]
        val source                                       = Level1(Level2(Level3(Level4(100L))))
        val result                                       = Into.derived[Level1, Level1Int].into(source)

        assert(result)(isRight(equalTo(Level1Int(Level2Int(Level3Int(Level4Int(100)))))))
      },
      test("fails when deepest value overflows") {
        implicit val level4Into: Into[Level4, Level4Int] = Into.derived[Level4, Level4Int]
        implicit val level3Into: Into[Level3, Level3Int] = Into.derived[Level3, Level3Int]
        implicit val level2Into: Into[Level2, Level2Int] = Into.derived[Level2, Level2Int]
        val source                                       = Level1(Level2(Level3(Level4(Long.MaxValue))))
        val result                                       = Into.derived[Level1, Level1Int].into(source)

        assert(result)(isLeft)
      }
    )
  )
}
