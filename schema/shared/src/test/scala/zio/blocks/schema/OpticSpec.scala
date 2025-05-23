package zio.blocks.schema

import zio.blocks.schema.DynamicOptic.Node.{Case, Elements, Field, MapKeys, MapValues}
import zio.blocks.schema.OpticCheck.{EmptyMap, EmptySequence, UnexpectedCase}
import zio.{Scope, ZIO}
import zio.blocks.schema.binding._
import zio.test.Assertion._
import zio.test._

object OpticSpec extends ZIOSpecDefault {
  import OpticSpecTypes._

  def spec: Spec[TestEnvironment with Scope, Any] = suite("OpticSpec")(
    suite("Lens")(
      test("path") {
        assert(Record1.b.toDynamic)(equalTo(DynamicOptic(Vector(DynamicOptic.Node.Field("b"))))) &&
        assert(Record2.r1_b.toDynamic)(
          equalTo(DynamicOptic(Vector(DynamicOptic.Node.Field("r1"), DynamicOptic.Node.Field("b"))))
        ) &&
        assert(Record3.v1.toDynamic)(equalTo(DynamicOptic(Vector(DynamicOptic.Node.Field("v1"))))) &&
        assert(Record3.v1.toDynamic)(equalTo(DynamicOptic(Vector(DynamicOptic.Node.Field("v1"))))) &&
        assert(Record3.v1.toDynamic)(equalTo(DynamicOptic(Vector(DynamicOptic.Node.Field("v1")))))
      },
      test("checks prerequisites for creation") {
        ZIO.attempt(Lens(null, Case1.d)).flip.map(e => assertTrue(e.isInstanceOf[IllegalArgumentException])) &&
        ZIO.attempt(Lens(Case1.d, null)).flip.map(e => assertTrue(e.isInstanceOf[IllegalArgumentException])) &&
        ZIO.attempt(Lens(Case4.reflect, null)).flip.map(e => assertTrue(e.isInstanceOf[IllegalArgumentException])) &&
        ZIO
          .attempt(Lens(null, Case4.reflect.fields(0).asInstanceOf[Term.Bound[Case4, List[Record3]]]))
          .flip
          .map(e => assertTrue(e.isInstanceOf[IllegalArgumentException]))
      },
      test("has consistent equals and hashCode") {
        assert(Record1.b)(equalTo(Record1.b)) &&
        assert(Record1.b.hashCode)(equalTo(Record1.b.hashCode)) &&
        assert(Record2.r1_b)(equalTo(Record2.r1_b)) &&
        assert(Record2.r1_b.hashCode)(equalTo(Record2.r1_b.hashCode)) &&
        assert(Record3.v1)(equalTo(Record3.v1)) &&
        assert(Record3.v1.hashCode)(equalTo(Record3.v1.hashCode)) &&
        assert(Record1.f: Any)(not(equalTo(Record1.b))) &&
        assert(Record2.l: Any)(not(equalTo(Record1.b))) &&
        assert(Record2.vi: Any)(not(equalTo(Record1.b))) &&
        assert(Record2.r1: Any)(not(equalTo(Record1.b))) &&
        assert(Record2.r1_f: Any)(not(equalTo(Record2.r1_b)))
        assert(Record2.r1_b: Any)(not(equalTo("")))
      },
      test("has associative equals and hashCode") {
        assert(Record3.r2_r1_b_left: Any)(equalTo(Record3.r2_r1_b_right)) &&
        assert(Record3.r2_r1_b_left.hashCode)(equalTo(Record3.r2_r1_b_right.hashCode))
      },
      test("returns a source structure") {
        assert(Record1.b.source)(equalTo(Record1.reflect)) &&
        assert(Record2.r1_b.source)(equalTo(Record2.reflect))
      },
      test("returns a focus structure") {
        assert(Record1.b.focus)(equalTo(Reflect.boolean[Binding])) &&
        assert(Record2.r1_b.focus)(equalTo(Reflect.boolean[Binding]))
      },
      test("passes check if a focus value exists") {
        assert(Record1.b.check(Record1(true, 1)))(isNone) &&
        assert(Record2.r1_b.check(Record2(2L, Vector.empty, Record1(true, 1))))(isNone)
      },
      test("gets a focus value") {
        assert(Record1.b.get(Record1(true, 1)))(equalTo(true)) &&
        assert(Record1.b.get(Record1(false, 1)))(equalTo(false)) &&
        assert(Record2.r1_b.get(Record2(2L, Vector.empty, Record1(true, 1))))(equalTo(true)) &&
        assert(Record2.r1_b.get(Record2(2L, Vector.empty, Record1(false, 1))))(equalTo(false)) &&
        assert(
          Record3.r2_r1_b_left.get(Record3(Record1(false, 3), Record2(2L, Vector.empty, Record1(true, 1)), Case1(0.5)))
        )(
          equalTo(true)
        ) &&
        assert(
          Record3.r2_r1_b_right.get(Record3(Record1(true, 3), Record2(2L, Vector.empty, Record1(false, 1)), Case1(0.5)))
        )(
          equalTo(false)
        )
      },
      test("replaces a focus value") {
        assert(Record1.b.replace(Record1(true, 1), false))(equalTo(Record1(false, 1))) &&
        assert(Record2.r1_b.replace(Record2(2L, Vector.empty, Record1(true, 1)), false))(
          equalTo(Record2(2L, Vector.empty, Record1(false, 1)))
        ) &&
        assert(
          Record3.r2_r1_b_left
            .replace(Record3(Record1(true, 3), Record2(2L, Vector.empty, Record1(true, 1)), Case1(0.5)), false)
        )(
          equalTo(Record3(Record1(true, 3), Record2(2L, Vector.empty, Record1(false, 1)), Case1(0.5)))
        )
      },
      test("modifies a focus value") {
        assert(Record1.b.modify(Record1(true, 1), x => !x))(equalTo(Record1(false, 1))) &&
        assert(Record2.r1_b.modify(Record2(2L, Vector.empty, Record1(true, 1)), x => !x))(
          equalTo(Record2(2L, Vector.empty, Record1(false, 1)))
        )
      },
      test("modifies a focus value optionaly") {
        assert(Record1.b.modifyOption(Record1(true, 1), x => !x))(isSome(equalTo(Record1(false, 1)))) &&
        assert(Record2.r1_b.modifyOption(Record2(2L, Vector.empty, Record1(true, 1)), x => !x))(
          isSome(equalTo(Record2(2L, Vector.empty, Record1(false, 1))))
        )
      },
      test("modifies a focus value or fails with a error") {
        assert(Record1.b.modifyOrFail(Record1(true, 1), x => !x))(isRight(equalTo(Record1(false, 1)))) &&
        assert(Record2.r1_b.modifyOrFail(Record2(2L, Vector.empty, Record1(true, 1)), x => !x))(
          isRight(equalTo(Record2(2L, Vector.empty, Record1(false, 1))))
        )
      }
    ),
    suite("Prism")(
      test("toDynamic") {
        assert(Variant1.c1.toDynamic)(equalTo(DynamicOptic(Vector(DynamicOptic.Node.Case("Case1"))))) &&
        assert(Variant1.c2.toDynamic)(equalTo(DynamicOptic(Vector(DynamicOptic.Node.Case("Case2"))))) &&
        assert(Variant1.v2.toDynamic)(equalTo(DynamicOptic(Vector(DynamicOptic.Node.Case("Variant2"))))) &&
        assert(Variant1.v2_c3.toDynamic)(
          equalTo(DynamicOptic(Vector(DynamicOptic.Node.Case("Variant2"), DynamicOptic.Node.Case("Case3"))))
        ) &&
        assert(Variant1.v2_c4.toDynamic)(
          equalTo(DynamicOptic(Vector(DynamicOptic.Node.Case("Variant2"), DynamicOptic.Node.Case("Case4"))))
        ) &&
        assert(Variant1.v2_v3_c5_left.toDynamic)(
          equalTo(
            DynamicOptic(
              Vector(
                DynamicOptic.Node.Case("Variant2"),
                DynamicOptic.Node.Case("Variant3"),
                DynamicOptic.Node.Case("Case5")
              )
            )
          )
        )
      },
      test("checks prerequisites for creation") {
        ZIO.attempt(Prism(null, Variant1.c1)).flip.map(e => assertTrue(e.isInstanceOf[IllegalArgumentException])) &&
        ZIO.attempt(Prism(Variant1.c1, null)).flip.map(e => assertTrue(e.isInstanceOf[IllegalArgumentException])) &&
        ZIO
          .attempt(Prism(Variant1.reflect, null))
          .flip
          .map(e => assertTrue(e.isInstanceOf[IllegalArgumentException])) &&
        ZIO
          .attempt(Prism(null, Variant1.reflect.cases(0).asInstanceOf[Term.Bound[Variant1, Case1]]))
          .flip
          .map(e => assertTrue(e.isInstanceOf[IllegalArgumentException]))
      },
      test("has consistent equals and hashCode") {
        assert(Variant1.c1)(equalTo(Variant1.c1)) &&
        assert(Variant1.c1.hashCode)(equalTo(Variant1.c1.hashCode)) &&
        assert(Variant1.c2: Any)(not(equalTo(Variant1.c1))) &&
        assert(Variant1.v2: Any)(not(equalTo(Variant1.c1))) &&
        assert(Variant2.c3: Any)(not(equalTo(Variant1.c1))) &&
        assert(Variant2.c4: Any)(not(equalTo(Variant1.c1))) &&
        assert(Variant2.c4: Any)(not(equalTo("")))
      },
      test("has associative equals and hashCode") {
        assert(Variant1.v2_v3_c5_left)(equalTo(Variant1.v2_v3_c5_right)) &&
        assert(Variant1.v2_v3_c5_left.hashCode)(equalTo(Variant1.v2_v3_c5_right.hashCode))
      },
      test("returns a source structure") {
        assert(Variant1.c1.source)(equalTo(Variant1.reflect)) &&
        assert(Variant1.c2.source)(equalTo(Variant1.reflect)) &&
        assert(Variant1.v2.source)(equalTo(Variant1.reflect)) &&
        assert(Variant1.v2_c3.source)(equalTo(Variant1.reflect)) &&
        assert(Variant1.v2_c4.source)(equalTo(Variant1.reflect)) &&
        assert(Variant1.v2_v3_c5_left.source)(equalTo(Variant1.reflect)) &&
        assert(Variant1.v2_v3_c5_right.source)(equalTo(Variant1.reflect))
      },
      test("returns a focus structure") {
        assert(Variant1.c1.focus)(equalTo(Case1.reflect)) &&
        assert(Variant1.c2.focus)(equalTo(Case2.reflect)) &&
        assert(Variant1.v2.focus)(equalTo(Variant2.reflect)) &&
        assert(Variant1.v2_c3.focus)(equalTo(Case3.reflect)) &&
        assert(Variant1.v2_c4.focus)(equalTo(Case4.reflect)) &&
        assert(Variant1.v2_v3_c5_left.focus)(equalTo(Case5.reflect)) &&
        assert(Variant1.v2_v3_c5_right.focus)(equalTo(Case5.reflect))
      },
      test("passes check if a focus value exists") {
        assert(Variant1.c1.check(Case1(0.1)))(isNone) &&
        assert(Variant1.c2.check(Case2(Record3(null, null, null))))(isNone) &&
        assert(Variant1.v2.check(Case3(Case1(0.1))))(isNone) &&
        assert(Variant1.v2_c3.check(Case3(Case1(0.1))))(isNone) &&
        assert(Variant2.c3.check(Case3(Case1(0.1))))(isNone) &&
        assert(Variant2.c4.check(Case4(List(Record3(null, null, null)))))(isNone) &&
        assert(Variant1.v2_v3_c5_left.check(Case5(null, null)))(isNone) &&
        assert(Variant1.v2_v3_c5_right.check(Case5(null, null)))(isNone)
      },
      test("doesn't pass check if a focus value doesn't exist") {
        assert(Variant1.c1.check(Case2(Record3(null, null, null))))(
          isSome(
            hasField[OpticCheck, String](
              "message",
              _.message,
              containsString(
                "During attempted access at .when[Case1], encountered an unexpected case at .when[Case1]: expected Case1, but got Case2"
              )
            )
          )
        ) &&
        assert(Variant1.c2.check(Case1(0.1)))(
          isSome(
            hasField[OpticCheck, String](
              "message",
              _.message,
              containsString(
                "During attempted access at .when[Case2], encountered an unexpected case at .when[Case2]: expected Case2, but got Case1"
              )
            )
          )
        ) &&
        assert(Variant1.v2.check(Case1(0.1)))(
          isSome(
            hasField[OpticCheck, String](
              "message",
              _.message,
              containsString(
                "During attempted access at .when[Variant2], encountered an unexpected case at .when[Variant2]: expected Variant2, but got Case1"
              )
            )
          )
        ) &&
        assert(Variant1.v2_c3.check(Case1(0.1)))(
          isSome(
            hasField[OpticCheck, String](
              "message",
              _.message,
              containsString(
                "During attempted access at .when[Variant2].when[Case3], encountered an unexpected case at .when[Variant2]: expected Variant2, but got Case1"
              )
            )
          )
        ) &&
        assert(Variant2.c3.check(Case4(List(Record3(null, null, null)))))(
          isSome(
            hasField[OpticCheck, String](
              "message",
              _.message,
              containsString(
                "During attempted access at .when[Case3], encountered an unexpected case at .when[Case3]: expected Case3, but got Case4"
              )
            )
          )
        ) &&
        assert(Variant2.c4.check(Case3(Case1(0.1))))(
          isSome(
            hasField[OpticCheck, String](
              "message",
              _.message,
              containsString(
                "During attempted access at .when[Case4], encountered an unexpected case at .when[Case4]: expected Case4, but got Case3"
              )
            )
          )
        ) &&
        assert(Variant1.v2_v3_c5_left.check(Case6(null)))(
          isSome(
            hasField[OpticCheck, String](
              "message",
              _.message,
              containsString(
                "During attempted access at .when[Variant2].when[Variant3].when[Case5], encountered an unexpected case at .when[Variant2].when[Variant3].when[Case5]"
              )
            )
          )
        ) &&
        assert(Variant1.v2_v3_c5_right.check(Case6(null)))(
          isSome(
            hasField[OpticCheck, String](
              "message",
              _.message,
              containsString(
                "During attempted access at .when[Variant2].when[Variant3].when[Case5], encountered an unexpected case at .when[Variant2].when[Variant3].when[Case5]: expected Case5, but got Case6"
              )
            )
          )
        )
      },
      test("gets an optional case class value") {
        assert(Variant1.c1.getOption(Case1(0.1)))(isSome(equalTo(Case1(0.1)))) &&
        assert(Variant1.c2.getOption(Case2(Record3(null, null, null))))(
          isSome(equalTo(Case2(Record3(null, null, null))))
        ) &&
        assert(Variant1.v2.getOption(Case3(Case1(0.1))))(isSome(equalTo(Case3(Case1(0.1)): Variant2))) &&
        assert(Variant1.v2_c3.getOption(Case3(Case1(0.1))))(isSome(equalTo(Case3(Case1(0.1))))) &&
        assert(Variant2.c3.getOption(Case3(Case1(0.1))))(isSome(equalTo(Case3(Case1(0.1))))) &&
        assert(Variant2.c4.getOption(Case4(List(Record3(null, null, null)))))(
          isSome(equalTo(Case4(List(Record3(null, null, null)))))
        ) &&
        assert(Variant1.v2_v3_c5_left.getOption(Case5(null, null)))(isSome(equalTo(Case5(null, null)))) &&
        assert(Variant1.v2_v3_c5_right.getOption(Case5(null, null)))(isSome(equalTo(Case5(null, null))))
      },
      test("doesn't get other case class values") {
        assert(Variant1.c1.getOption(Case2(Record3(null, null, null))))(isNone) &&
        assert(Variant1.c2.getOption(Case1(0.1)))(isNone) &&
        assert(Variant1.v2.getOption(Case1(0.1)))(isNone) &&
        assert(Variant1.v2_c3.getOption(Case1(0.1)))(isNone) &&
        assert(Variant2.c3.getOption(Case4(List(Record3(null, null, null)))))(isNone) &&
        assert(Variant2.c4.getOption(Case3(Case1(0.1))))(isNone) &&
        assert(Variant1.v2_v3_c5_left.getOption(Case6(null)))(isNone) &&
        assert(Variant1.v2_v3_c5_right.getOption(Case6(null)))(isNone)
      },
      test("gets an optional case wrapped to rigth") {
        assert(Variant1.c1.getOrFail(Case1(0.1)))(isRight(equalTo(Case1(0.1)))) &&
        assert(Variant1.c2.getOrFail(Case2(Record3(null, null, null))))(
          isRight(equalTo(Case2(Record3(null, null, null))))
        ) &&
        assert(Variant1.v2.getOrFail(Case3(Case1(0.1))))(isRight(equalTo(Case3(Case1(0.1)): Variant2))) &&
        assert(Variant1.v2_c3.getOrFail(Case3(Case1(0.1))))(isRight(equalTo(Case3(Case1(0.1))))) &&
        assert(Variant2.c3.getOrFail(Case3(Case1(0.1))))(isRight(equalTo(Case3(Case1(0.1))))) &&
        assert(Variant2.c4.getOrFail(Case4(List(Record3(null, null, null)))))(
          isRight(equalTo(Case4(List(Record3(null, null, null)))))
        ) &&
        assert(Variant1.v2_v3_c5_left.getOrFail(Case5(null, null)))(isRight(equalTo(Case5(null, null)))) &&
        assert(Variant1.v2_v3_c5_right.getOrFail(Case5(null, null)))(isRight(equalTo(Case5(null, null))))
      },
      test("doesn't get other case class values") {
        assert(Variant1.c1.getOrFail(Case2(Record3(null, null, null))))(
          isLeft(
            equalTo(
              OpticCheck(
                errors = ::(
                  UnexpectedCase(
                    expectedCase = "Case1",
                    actualCase = "Case2",
                    full = DynamicOptic(Vector(Case("Case1"))),
                    prefix = DynamicOptic(Vector(Case("Case1"))),
                    actualValue = Case2(Record3(null, null, null))
                  ),
                  Nil
                )
              )
            )
          )
        ) &&
        assert(Variant1.c2.getOrFail(Case1(0.1)))(
          isLeft(
            equalTo(
              OpticCheck(
                errors = ::(
                  UnexpectedCase(
                    expectedCase = "Case2",
                    actualCase = "Case1",
                    full = DynamicOptic(Vector(Case("Case2"))),
                    prefix = DynamicOptic(Vector(Case("Case2"))),
                    actualValue = Case1(0.1)
                  ),
                  Nil
                )
              )
            )
          )
        ) &&
        assert(Variant1.v2.getOrFail(Case1(0.1)))(
          isLeft(
            equalTo(
              OpticCheck(
                errors = ::(
                  UnexpectedCase(
                    expectedCase = "Variant2",
                    actualCase = "Case1",
                    full = DynamicOptic(Vector(Case("Variant2"))),
                    prefix = DynamicOptic(Vector(Case("Variant2"))),
                    actualValue = Case1(0.1)
                  ),
                  Nil
                )
              )
            )
          )
        ) &&
        assert(Variant1.v2_c3.getOrFail(Case1(0.1)))(
          isLeft(
            equalTo(
              OpticCheck(
                errors = ::(
                  UnexpectedCase(
                    expectedCase = "Variant2",
                    actualCase = "Case1",
                    full = DynamicOptic(Vector(Case("Variant2"), Case("Case3"))),
                    prefix = DynamicOptic(Vector(Case("Variant2"))),
                    actualValue = Case1(0.1)
                  ),
                  Nil
                )
              )
            )
          )
        ) &&
        assert(Variant2.c3.getOrFail(Case4(List(Record3(null, null, null)))))(
          isLeft(
            equalTo(
              OpticCheck(
                errors = ::(
                  UnexpectedCase(
                    expectedCase = "Case3",
                    actualCase = "Case4",
                    full = DynamicOptic(Vector(Case("Case3"))),
                    prefix = DynamicOptic(Vector(Case("Case3"))),
                    actualValue = Case4(List(Record3(null, null, null)))
                  ),
                  Nil
                )
              )
            )
          )
        ) &&
        assert(Variant2.c4.getOrFail(Case3(Case1(0.1))))(
          isLeft(
            equalTo(
              OpticCheck(
                errors = ::(
                  UnexpectedCase(
                    expectedCase = "Case4",
                    actualCase = "Case3",
                    full = DynamicOptic(Vector(Case("Case4"))),
                    prefix = DynamicOptic(Vector(Case("Case4"))),
                    actualValue = Case3(Case1(0.1))
                  ),
                  Nil
                )
              )
            )
          )
        ) &&
        assert(Variant1.v2_v3_c5_left.getOrFail(Case6(null)))(
          isLeft(
            equalTo(
              OpticCheck(
                errors = ::(
                  UnexpectedCase(
                    expectedCase = "Case5",
                    actualCase = "Case6",
                    full = DynamicOptic(Vector(Case("Variant2"), Case("Variant3"), Case("Case5"))),
                    prefix = DynamicOptic(Vector(Case("Variant2"), Case("Variant3"), Case("Case5"))),
                    actualValue = Case6(null)
                  ),
                  Nil
                )
              )
            )
          )
        ) &&
        assert(Variant1.v2_v3_c5_right.getOrFail(Case6(null)))(
          isLeft(
            equalTo(
              OpticCheck(
                errors = ::(
                  UnexpectedCase(
                    expectedCase = "Case5",
                    actualCase = "Case6",
                    full = DynamicOptic(Vector(Case("Variant2"), Case("Variant3"), Case("Case5"))),
                    prefix = DynamicOptic(Vector(Case("Variant2"), Case("Variant3"), Case("Case5"))),
                    actualValue = Case6(null)
                  ),
                  Nil
                )
              )
            )
          )
        )
      },
      test("reverse gets a base class value") {
        assert(Variant1.c1.reverseGet(Case1(0.1)))(equalTo(Case1(0.1))) &&
        assert(Variant1.c2.reverseGet(Case2(Record3(null, null, null))))(
          equalTo(Case2(Record3(null, null, null)): Variant1)
        ) &&
        assert(Variant1.v2.reverseGet(Case3(Case1(0.1))))(equalTo(Case3(Case1(0.1)): Variant1)) &&
        assert(Variant1.v2_c3.reverseGet(Case3(Case1(0.1))))(equalTo(Case3(Case1(0.1)): Variant1)) &&
        assert(Variant2.c3.reverseGet(Case3(Case1(0.1))))(equalTo(Case3(Case1(0.1)): Variant2)) &&
        assert(Variant2.c4.reverseGet(Case4(List(Record3(null, null, null)))))(
          equalTo(Case4(List(Record3(null, null, null))): Variant2)
        ) &&
        assert(Variant1.v2_v3_c5_left.reverseGet(Case5(null, null)))(equalTo(Case5(null, null): Variant1)) &&
        assert(Variant1.v2_v3_c5_right.reverseGet(Case5(null, null)))(equalTo(Case5(null, null): Variant1))
      },
      test("replaces an optional case class value") {
        assert(Variant1.c1.replace(Case1(0.1), Case1(0.2)))(equalTo(Case1(0.2): Variant1)) &&
        assert(Variant1.c2.replace(Case2(Record3(null, null, null)), Case2(null)))(equalTo(Case2(null): Variant1)) &&
        assert(Variant1.v2.replace(Case3(Case1(0.1)), Case4(Nil)))(equalTo(Case4(Nil): Variant1)) &&
        assert(Variant1.v2_c3.replace(Case3(Case1(0.1)), Case3(Case1(0.2))))(equalTo(Case3(Case1(0.2)): Variant1)) &&
        assert(Variant2.c3.replace(Case3(Case1(0.1)), Case3(Case1(0.2))))(equalTo(Case3(Case1(0.2)): Variant2)) &&
        assert(Variant2.c4.replace(Case4(List(Record3(null, null, null))), Case4(Nil)))(
          equalTo(Case4(Nil): Variant2)
        ) &&
        assert(Variant1.v2_v3_c5_left.replace(Case5(null, null), Case5(Set.empty, null)))(
          equalTo(Case5(Set.empty, null): Variant1)
        ) &&
        assert(Variant1.v2_v3_c5_right.replace(Case5(null, null), Case5(Set.empty, null)))(
          equalTo(Case5(Set.empty, null): Variant1)
        )
      },
      test("doesn't replace other case class values") {
        assert(Variant1.c1.replace(Case2(null), Case1(0.2)))(equalTo(Case2(null): Variant1)) &&
        assert(Variant1.c2.replace(Case1(0.1), Case2(null)))(equalTo(Case1(0.1): Variant1)) &&
        assert(Variant1.v2.replace(Case2(null), Case4(Nil)))(equalTo(Case2(null): Variant1)) &&
        assert(Variant1.v2_c3.replace(Case1(0.1), Case3(Case1(0.2))))(equalTo(Case1(0.1): Variant1)) &&
        assert(Variant2.c3.replace(Case4(List(Record3(null, null, null))), Case3(Case1(0.2))))(
          equalTo(Case4(List(Record3(null, null, null))): Variant2)
        ) &&
        assert(Variant2.c4.replace(Case3(Case1(0.1)), Case4(Nil)))(equalTo(Case3(Case1(0.1)): Variant2)) &&
        assert(Variant1.v2_v3_c5_left.replace(Case4(Nil), Case5(Set.empty, null)))(equalTo(Case4(Nil): Variant1)) &&
        assert(Variant1.v2_v3_c5_right.replace(Case4(Nil), Case5(Set.empty, null)))(equalTo(Case4(Nil): Variant1))
      },
      test("optionally replaces an optional case class value") {
        assert(Variant1.c1.replaceOption(Case1(0.1), Case1(0.2)))(isSome(equalTo(Case1(0.2): Variant1))) &&
        assert(Variant1.c2.replaceOption(Case2(Record3(null, null, null)), Case2(null)))(
          isSome(equalTo(Case2(null): Variant1))
        ) &&
        assert(Variant1.v2.replaceOption(Case3(Case1(0.1)), Case4(Nil)))(isSome(equalTo(Case4(Nil): Variant1))) &&
        assert(Variant1.v2_c3.replaceOption(Case3(Case1(0.1)), Case3(Case1(0.2))))(
          isSome(equalTo(Case3(Case1(0.2)): Variant1))
        ) &&
        assert(Variant2.c3.replaceOption(Case3(Case1(0.1)), Case3(Case1(0.2))))(
          isSome(equalTo(Case3(Case1(0.2)): Variant2))
        ) &&
        assert(Variant2.c4.replaceOption(Case4(List(Record3(null, null, null))), Case4(Nil)))(
          isSome(equalTo(Case4(Nil): Variant2))
        ) &&
        assert(Variant1.v2_v3_c5_left.replaceOption(Case5(null, null), Case5(Set.empty, null)))(
          isSome(equalTo(Case5(Set.empty, null): Variant1))
        ) &&
        assert(Variant1.v2_v3_c5_right.replaceOption(Case5(null, null), Case5(Set.empty, null)))(
          isSome(equalTo(Case5(Set.empty, null): Variant1))
        )
      },
      test("optionally doesn't replace other case class values") {
        assert(Variant1.c1.replaceOption(Case2(null), Case1(0.2)))(isNone) &&
        assert(Variant1.c2.replaceOption(Case1(0.1), Case2(null)))(isNone) &&
        assert(Variant1.v2.replaceOption(Case2(null), Case4(Nil)))(isNone) &&
        assert(Variant1.v2_c3.replaceOption(Case1(0.1), Case3(Case1(0.2))))(isNone) &&
        assert(Variant2.c3.replaceOption(Case4(List(Record3(null, null, null))), Case3(Case1(0.2))))(isNone) &&
        assert(Variant2.c4.replaceOption(Case3(Case1(0.1)), Case4(Nil)))(isNone) &&
        assert(Variant1.v2_v3_c5_left.replaceOption(Case4(Nil), Case5(Set.empty, null)))(isNone) &&
        assert(Variant1.v2_v3_c5_right.replaceOption(Case4(Nil), Case5(Set.empty, null)))(isNone)
      },
      test("optionally replaces a case wrapping the result to right") {
        assert(Variant1.c1.replaceOrFail(Case1(0.1), Case1(0.2)))(isRight(equalTo(Case1(0.2): Variant1))) &&
        assert(Variant1.c2.replaceOrFail(Case2(Record3(null, null, null)), Case2(null)))(
          isRight(equalTo(Case2(null): Variant1))
        ) &&
        assert(Variant1.v2.replaceOrFail(Case3(Case1(0.1)), Case4(Nil)))(isRight(equalTo(Case4(Nil): Variant1))) &&
        assert(Variant1.v2_c3.replaceOrFail(Case3(Case1(0.1)), Case3(Case1(0.2))))(
          isRight(equalTo(Case3(Case1(0.2)): Variant1))
        ) &&
        assert(Variant2.c3.replaceOrFail(Case3(Case1(0.1)), Case3(Case1(0.2))))(
          isRight(equalTo(Case3(Case1(0.2)): Variant2))
        ) &&
        assert(Variant2.c4.replaceOrFail(Case4(List(Record3(null, null, null))), Case4(Nil)))(
          isRight(equalTo(Case4(Nil): Variant2))
        ) &&
        assert(Variant1.v2_v3_c5_left.replaceOrFail(Case5(null, null), Case5(Set.empty, null)))(
          isRight(equalTo(Case5(Set.empty, null): Variant1))
        ) &&
        assert(Variant1.v2_v3_c5_right.replaceOrFail(Case5(null, null), Case5(Set.empty, null)))(
          isRight(equalTo(Case5(Set.empty, null): Variant1))
        )
      },
      test("optionally doesn't replace other case returning an error") {
        assert(Variant1.c1.replaceOrFail(Case2(null), Case1(0.2)))(
          isLeft(
            equalTo(
              OpticCheck(
                errors = ::(
                  UnexpectedCase(
                    expectedCase = "Case1",
                    actualCase = "Case2",
                    full = DynamicOptic(Vector(Case("Case1"))),
                    prefix = DynamicOptic(Vector(Case("Case1"))),
                    actualValue = Case2(null)
                  ),
                  Nil
                )
              )
            )
          )
        ) &&
        assert(Variant1.c2.replaceOrFail(Case1(0.1), Case2(null)))(
          isLeft(
            equalTo(
              OpticCheck(
                errors = ::(
                  UnexpectedCase(
                    expectedCase = "Case2",
                    actualCase = "Case1",
                    full = DynamicOptic(Vector(Case("Case2"))),
                    prefix = DynamicOptic(Vector(Case("Case2"))),
                    actualValue = Case1(0.1)
                  ),
                  Nil
                )
              )
            )
          )
        ) &&
        assert(Variant1.v2.replaceOrFail(Case2(null), Case4(Nil)))(
          isLeft(
            equalTo(
              OpticCheck(
                errors = ::(
                  UnexpectedCase(
                    expectedCase = "Variant2",
                    actualCase = "Case2",
                    full = DynamicOptic(Vector(Case("Variant2"))),
                    prefix = DynamicOptic(Vector(Case("Variant2"))),
                    actualValue = Case2(null)
                  ),
                  Nil
                )
              )
            )
          )
        ) &&
        assert(Variant1.v2_c3.replaceOrFail(Case1(0.1), Case3(Case1(0.2))))(
          isLeft(
            equalTo(
              OpticCheck(
                errors = ::(
                  UnexpectedCase(
                    expectedCase = "Variant2",
                    actualCase = "Case1",
                    full = DynamicOptic(Vector(Case("Variant2"), Case("Case3"))),
                    prefix = DynamicOptic(Vector(Case("Variant2"))),
                    actualValue = Case1(0.1)
                  ),
                  Nil
                )
              )
            )
          )
        ) &&
        assert(Variant2.c3.replaceOrFail(Case4(List(Record3(null, null, null))), Case3(Case1(0.2))))(
          isLeft(
            equalTo(
              OpticCheck(
                errors = ::(
                  UnexpectedCase(
                    expectedCase = "Case3",
                    actualCase = "Case4",
                    full = DynamicOptic(Vector(Case("Case3"))),
                    prefix = DynamicOptic(Vector(Case("Case3"))),
                    actualValue = Case4(List(Record3(null, null, null)))
                  ),
                  Nil
                )
              )
            )
          )
        ) &&
        assert(Variant2.c4.replaceOrFail(Case3(Case1(0.1)), Case4(Nil)))(
          isLeft(
            equalTo(
              OpticCheck(
                errors = ::(
                  UnexpectedCase(
                    expectedCase = "Case4",
                    actualCase = "Case3",
                    full = DynamicOptic(Vector(Case("Case4"))),
                    prefix = DynamicOptic(Vector(Case("Case4"))),
                    actualValue = Case3(Case1(0.1))
                  ),
                  Nil
                )
              )
            )
          )
        ) &&
        assert(Variant1.v2_v3_c5_left.replaceOrFail(Case4(Nil), Case5(Set.empty, null)))(
          isLeft(
            equalTo(
              OpticCheck(
                errors = ::(
                  UnexpectedCase(
                    expectedCase = "Variant3",
                    actualCase = "Case4",
                    full = DynamicOptic(Vector(Case("Variant2"), Case("Variant3"), Case("Case5"))),
                    prefix = DynamicOptic(Vector(Case("Variant2"), Case("Variant3"))),
                    actualValue = Case4(Nil)
                  ),
                  Nil
                )
              )
            )
          )
        ) &&
        assert(Variant1.v2_v3_c5_right.replaceOrFail(Case4(Nil), Case5(Set.empty, null)))(
          isLeft(
            equalTo(
              OpticCheck(
                errors = ::(
                  UnexpectedCase(
                    expectedCase = "Variant3",
                    actualCase = "Case4",
                    full = DynamicOptic(Vector(Case("Variant2"), Case("Variant3"), Case("Case5"))),
                    prefix = DynamicOptic(Vector(Case("Variant2"), Case("Variant3"))),
                    actualValue = Case4(Nil)
                  ),
                  Nil
                )
              )
            )
          )
        )
      },
      test("modify an optional case class value") {
        assert(Variant1.c1.modify(Case1(0.1), _ => Case1(0.2)))(equalTo(Case1(0.2): Variant1)) &&
        assert(Variant1.c2.modify(Case2(Record3(null, null, null)), _ => Case2(null)))(
          equalTo(Case2(null): Variant1)
        ) &&
        assert(Variant1.v2.modify(Case3(Case1(0.1)), _ => Case4(Nil)))(equalTo(Case4(Nil): Variant1)) &&
        assert(Variant1.v2_c3.modify(Case3(Case1(0.1)), _ => Case3(Case1(0.2))))(
          equalTo(Case3(Case1(0.2)): Variant1)
        ) &&
        assert(Variant2.c3.modify(Case3(Case1(0.1)), _ => Case3(Case1(0.2))))(equalTo(Case3(Case1(0.2)): Variant2)) &&
        assert(Variant2.c4.modify(Case4(List(Record3(null, null, null))), _ => Case4(Nil)))(
          equalTo(Case4(Nil): Variant2)
        ) &&
        assert(Variant1.v2_v3_c5_left.modify(Case5(null, null), _ => Case5(Set.empty, null)))(
          equalTo(Case5(Set.empty, null): Variant1)
        ) &&
        assert(Variant1.v2_v3_c5_right.modify(Case5(null, null), _ => Case5(Set.empty, null)))(
          equalTo(Case5(Set.empty, null): Variant1)
        )
      },
      test("doesn't modify other case class values") {
        assert(Variant1.c1.modify(Case2(null), _ => Case1(0.2)))(equalTo(Case2(null): Variant1)) &&
        assert(Variant1.c2.modify(Case1(0.1), _ => Case2(null)))(equalTo(Case1(0.1): Variant1)) &&
        assert(Variant1.v2.modify(Case2(null), _ => Case4(Nil)))(equalTo(Case2(null): Variant1)) &&
        assert(Variant1.v2_c3.modify(Case1(0.1), _ => Case3(Case1(0.2))))(equalTo(Case1(0.1): Variant1)) &&
        assert(Variant2.c3.modify(Case4(List(Record3(null, null, null))), _ => Case3(Case1(0.2))))(
          equalTo(Case4(List(Record3(null, null, null))): Variant2)
        ) &&
        assert(Variant2.c4.modify(Case3(Case1(0.1)), _ => Case4(Nil)))(
          equalTo(Case3(Case1(0.1)): Variant2)
        ) &&
        assert(Variant1.v2_v3_c5_left.modify(Case4(Nil), _ => Case5(Set.empty, null)))(equalTo(Case4(Nil): Variant1)) &&
        assert(Variant1.v2_v3_c5_right.modify(Case4(Nil), _ => Case5(Set.empty, null)))(equalTo(Case4(Nil): Variant1))
      },
      test("modifies an optional case class value wrapped to some") {
        assert(Variant1.c1.modifyOption(Case1(0.1), _ => Case1(0.2)))(isSome(equalTo(Case1(0.2): Variant1))) &&
        assert(Variant1.c2.modifyOption(Case2(Record3(null, null, null)), _ => Case2(null)))(
          isSome(equalTo(Case2(null): Variant1))
        ) &&
        assert(Variant1.v2.modifyOption(Case3(Case1(0.1)), _ => Case4(Nil)))(isSome(equalTo(Case4(Nil): Variant1))) &&
        assert(Variant1.v2_c3.modifyOption(Case3(Case1(0.1)), _ => Case3(Case1(0.2))))(
          isSome(equalTo(Case3(Case1(0.2)): Variant1))
        ) &&
        assert(Variant2.c3.modifyOption(Case3(Case1(0.1)), _ => Case3(Case1(0.2))))(
          isSome(equalTo(Case3(Case1(0.2)): Variant2))
        ) &&
        assert(Variant2.c4.modifyOption(Case4(List(Record3(null, null, null))), _ => Case4(Nil)))(
          isSome(equalTo(Case4(Nil): Variant2))
        ) &&
        assert(Variant1.v2_v3_c5_left.modifyOption(Case5(null, null), _ => Case5(Set.empty, null)))(
          isSome(equalTo(Case5(Set.empty, null): Variant1))
        ) &&
        assert(Variant1.v2_v3_c5_right.modifyOption(Case5(null, null), _ => Case5(Set.empty, null)))(
          isSome(equalTo(Case5(Set.empty, null): Variant1))
        )
      },
      test("doesn't modify other case class values returning none") {
        assert(Variant1.c1.modifyOption(Case2(null), _ => Case1(0.2)))(isNone) &&
        assert(Variant1.c2.modifyOption(Case1(0.1), _ => Case2(null)))(isNone) &&
        assert(Variant1.v2.modifyOption(Case2(null), _ => Case4(Nil)))(isNone) &&
        assert(Variant1.v2_c3.modifyOption(Case1(0.1), _ => Case3(Case1(0.2))))(isNone) &&
        assert(Variant2.c3.modifyOption(Case4(List(Record3(null, null, null))), _ => Case3(Case1(0.2))))(isNone) &&
        assert(Variant2.c4.modifyOption(Case3(Case1(0.1)), _ => Case4(Nil)))(isNone) &&
        assert(Variant1.v2_v3_c5_left.modifyOption(Case4(Nil), _ => Case5(Set.empty, null)))(isNone) &&
        assert(Variant1.v2_v3_c5_right.modifyOption(Case4(Nil), _ => Case5(Set.empty, null)))(isNone)
      },
      test("modifies an optional case class value wrapped to right") {
        assert(Variant1.c1.modifyOrFail(Case1(0.1), _ => Case1(0.2)))(isRight(equalTo(Case1(0.2): Variant1))) &&
        assert(Variant1.c2.modifyOrFail(Case2(Record3(null, null, null)), _ => Case2(null)))(
          isRight(equalTo(Case2(null): Variant1))
        ) &&
        assert(Variant1.v2.modifyOrFail(Case3(Case1(0.1)), _ => Case4(Nil)))(isRight(equalTo(Case4(Nil): Variant1))) &&
        assert(Variant1.v2_c3.modifyOrFail(Case3(Case1(0.1)), _ => Case3(Case1(0.2))))(
          isRight(equalTo(Case3(Case1(0.2)): Variant1))
        ) &&
        assert(Variant2.c3.modifyOrFail(Case3(Case1(0.1)), _ => Case3(Case1(0.2))))(
          isRight(equalTo(Case3(Case1(0.2)): Variant2))
        ) &&
        assert(Variant2.c4.modifyOrFail(Case4(List(Record3(null, null, null))), _ => Case4(Nil)))(
          isRight(equalTo(Case4(Nil): Variant2))
        ) &&
        assert(Variant1.v2_v3_c5_left.modifyOrFail(Case5(null, null), _ => Case5(Set.empty, null)))(
          isRight(equalTo(Case5(Set.empty, null): Variant1))
        ) &&
        assert(Variant1.v2_v3_c5_right.modifyOrFail(Case5(null, null), _ => Case5(Set.empty, null)))(
          isRight(equalTo(Case5(Set.empty, null): Variant1))
        )
      },
      test("doesn't modify other case class values returning none") {
        assert(Variant1.c1.modifyOrFail(Case2(null), _ => Case1(0.2)))(
          isLeft(
            equalTo(
              OpticCheck(
                errors = ::(
                  UnexpectedCase(
                    expectedCase = "Case1",
                    actualCase = "Case2",
                    full = DynamicOptic(Vector(Case("Case1"))),
                    prefix = DynamicOptic(Vector(Case("Case1"))),
                    actualValue = Case2(null)
                  ),
                  Nil
                )
              )
            )
          )
        ) &&
        assert(Variant1.c2.modifyOrFail(Case1(0.1), _ => Case2(null)))(
          isLeft(
            equalTo(
              OpticCheck(
                errors = ::(
                  UnexpectedCase(
                    expectedCase = "Case2",
                    actualCase = "Case1",
                    full = DynamicOptic(Vector(Case("Case2"))),
                    prefix = DynamicOptic(Vector(Case("Case2"))),
                    actualValue = Case1(0.1)
                  ),
                  Nil
                )
              )
            )
          )
        ) &&
        assert(Variant1.v2.modifyOrFail(Case2(null), _ => Case4(Nil)))(
          isLeft(
            equalTo(
              OpticCheck(
                errors = ::(
                  UnexpectedCase(
                    expectedCase = "Variant2",
                    actualCase = "Case2",
                    full = DynamicOptic(Vector(Case("Variant2"))),
                    prefix = DynamicOptic(Vector(Case("Variant2"))),
                    actualValue = Case2(null)
                  ),
                  Nil
                )
              )
            )
          )
        ) &&
        assert(Variant1.v2_c3.modifyOrFail(Case1(0.1), _ => Case3(Case1(0.2))))(
          isLeft(
            equalTo(
              OpticCheck(
                errors = ::(
                  UnexpectedCase(
                    expectedCase = "Variant2",
                    actualCase = "Case1",
                    full = DynamicOptic(Vector(Case("Variant2"), Case("Case3"))),
                    prefix = DynamicOptic(Vector(Case("Variant2"))),
                    actualValue = Case1(0.1)
                  ),
                  Nil
                )
              )
            )
          )
        ) &&
        assert(Variant2.c3.modifyOrFail(Case4(List(Record3(null, null, null))), _ => Case3(Case1(0.2))))(
          isLeft(
            equalTo(
              OpticCheck(
                errors = ::(
                  UnexpectedCase(
                    expectedCase = "Case3",
                    actualCase = "Case4",
                    full = DynamicOptic(Vector(Case("Case3"))),
                    prefix = DynamicOptic(Vector(Case("Case3"))),
                    actualValue = Case4(List(Record3(null, null, null)))
                  ),
                  Nil
                )
              )
            )
          )
        ) &&
        assert(Variant2.c4.modifyOrFail(Case3(Case1(0.1)), _ => Case4(Nil)))(
          isLeft(
            equalTo(
              OpticCheck(
                errors = ::(
                  UnexpectedCase(
                    expectedCase = "Case4",
                    actualCase = "Case3",
                    full = DynamicOptic(Vector(Case("Case4"))),
                    prefix = DynamicOptic(Vector(Case("Case4"))),
                    actualValue = Case3(Case1(0.1))
                  ),
                  Nil
                )
              )
            )
          )
        ) &&
        assert(Variant1.v2_v3_c5_left.modifyOrFail(Case4(Nil), _ => Case5(Set.empty, null)))(
          isLeft(
            equalTo(
              OpticCheck(
                errors = ::(
                  UnexpectedCase(
                    expectedCase = "Variant3",
                    actualCase = "Case4",
                    full = DynamicOptic(Vector(Case("Variant2"), Case("Variant3"), Case("Case5"))),
                    prefix = DynamicOptic(Vector(Case("Variant2"), Case("Variant3"))),
                    actualValue = Case4(Nil)
                  ),
                  Nil
                )
              )
            )
          )
        ) &&
        assert(Variant1.v2_v3_c5_right.modifyOrFail(Case4(Nil), _ => Case5(Set.empty, null)))(
          isLeft(
            equalTo(
              OpticCheck(
                errors = ::(
                  UnexpectedCase(
                    expectedCase = "Variant3",
                    actualCase = "Case4",
                    full = DynamicOptic(Vector(Case("Variant2"), Case("Variant3"), Case("Case5"))),
                    prefix = DynamicOptic(Vector(Case("Variant2"), Case("Variant3"))),
                    actualValue = Case4(Nil)
                  ),
                  Nil
                )
              )
            )
          )
        )
      }
    ),
    suite("Optional")(
      test("path") {
        assert(Variant1.c1_d.toDynamic)(
          equalTo(DynamicOptic(Vector(DynamicOptic.Node.Case("Case1"), DynamicOptic.Node.Field("d"))))
        ) &&
        assert(Variant1.c2_r3.toDynamic)(
          equalTo(DynamicOptic(Vector(DynamicOptic.Node.Case("Case2"), DynamicOptic.Node.Field("r3"))))
        ) &&
        assert(Variant1.c2_r3_r1.toDynamic)(
          equalTo(
            DynamicOptic(
              Vector(DynamicOptic.Node.Case("Case2"), DynamicOptic.Node.Field("r3"), DynamicOptic.Node.Field("r1"))
            )
          )
        )
      },
      test("check") {
        assert(Variant1.c1_d.check(Case2(Record3(null, null, null))))(
          isSome(
            hasField[OpticCheck, String](
              "message",
              _.message,
              containsString("expected Case1, but got Case2")
            )
          )
        ) &&
        assert(Variant1.c2_r3_v1_c1.check(Case2(Record3(null, null, Case2(null)))))(
          isSome(
            hasField[OpticCheck, String](
              "message",
              _.message,
              containsString("expected Case1, but got Case2")
            )
          )
        ) &&
        assert(Variant2.c3_v1_c1_left.check(Case4(Nil)))(
          isSome(
            hasField[OpticCheck, String](
              "message",
              _.message,
              containsString("expected Case3, but got Case4")
            )
          )
        ) &&
        assert(Variant2.c3_v1_c1_right.check(Case3(Case2(null))))(
          isSome(
            hasField[OpticCheck, String](
              "message",
              _.message,
              containsString("expected Case1, but got Case2")
            )
          )
        ) &&
        assert(Case3.v1_c1_d_left.check(Case3(Case4(Nil))))(
          isSome(
            hasField[OpticCheck, String](
              "message",
              _.message,
              containsString(
                "During attempted access at .v1.when[Case1].d, encountered an unexpected case at .v1.when[Case1]: expected Case1, but got Variant2"
              )
            )
          )
        ) &&
        assert(Variant1.c2_r3_r2_r1_b_left.check(Case1(0.1)))(
          isSome(
            hasField[OpticCheck, String](
              "message",
              _.message,
              containsString("expected Case2, but got Case1")
            )
          )
        )
      },
      test("has consistent equals and hashCode") {
        assert(Variant1.c1_d)(equalTo(Variant1.c1_d)) &&
        assert(Variant1.c1_d.hashCode)(equalTo(Variant1.c1_d.hashCode)) &&
        assert(Variant1.c2_r3)(equalTo(Variant1.c2_r3)) &&
        assert(Variant1.c2_r3.hashCode)(equalTo(Variant1.c2_r3.hashCode)) &&
        assert(Variant1.c2_r3_r1)(equalTo(Variant1.c2_r3_r1)) &&
        assert(Variant1.c2_r3_r1.hashCode)(equalTo(Variant1.c2_r3_r1.hashCode)) &&
        assert(Variant1.c2_r3_v1_c1)(equalTo(Variant1.c2_r3_v1_c1)) &&
        assert(Variant1.c2_r3_v1_c1.hashCode)(equalTo(Variant1.c2_r3_v1_c1.hashCode)) &&
        assert(Variant2.c3_v1)(equalTo(Variant2.c3_v1)) &&
        assert(Variant2.c3_v1.hashCode)(equalTo(Variant2.c3_v1.hashCode)) &&
        assert(Variant1.v2_c3_v1)(equalTo(Variant1.v2_c3_v1)) &&
        assert(Variant1.v2_c3_v1.hashCode)(equalTo(Variant1.v2_c3_v1.hashCode)) &&
        assert(Variant2.c3_v1_v2)(equalTo(Variant2.c3_v1_v2)) &&
        assert(Variant2.c3_v1_v2.hashCode)(equalTo(Variant2.c3_v1_v2.hashCode)) &&
        assert(Variant1.c2_r3: Any)(not(equalTo(Variant1.c1_d))) &&
        assert(Variant2.c3_v1_v2_c4: Any)(not(equalTo(Variant2.c3_v1_v2_c4_lr3))) &&
        assert(Case2.r3_v1_c1)(equalTo(Case2.r3_v1_c1)) &&
        assert(Case2.r3_v1_c1.hashCode)(equalTo(Case2.r3_v1_c1.hashCode)) &&
        assert(Case3.v1_v2_c3_v1_v2: Any)(not(equalTo(Case3.v1_c1_d_right))) &&
        assert(Case3.v1_v2_c3_v1_v2: Any)(not(equalTo("")))
      },
      test("has associative equals and hashCode") {
        assert(Variant1.c2_r3_r2_r1_b_left)(equalTo(Variant1.c2_r3_r2_r1_b_right)) &&
        assert(Variant1.c2_r3_r2_r1_b_left.hashCode)(equalTo(Variant1.c2_r3_r2_r1_b_right.hashCode)) &&
        assert(Variant2.c3_v1_c1_left)(equalTo(Variant2.c3_v1_c1_right)) &&
        assert(Variant2.c3_v1_c1_left.hashCode)(equalTo(Variant2.c3_v1_c1_left.hashCode)) &&
        assert(Case3.v1_c1_d_left)(equalTo(Case3.v1_c1_d_right)) &&
        assert(Case3.v1_c1_d_left.hashCode)(equalTo(Case3.v1_c1_d_right.hashCode))
      },
      test("returns a source structure") {
        assert(Variant1.c1_d.source)(equalTo(Variant1.reflect)) &&
        assert(Variant1.c2_r3.source)(equalTo(Variant1.reflect)) &&
        assert(Variant2.c3_v1_c1_left.source)(equalTo(Variant2.reflect)) &&
        assert(Variant2.c3_v1_c1_right.source)(equalTo(Variant2.reflect)) &&
        assert(Variant2.c3_v1_c1_d_left.source)(equalTo(Variant2.reflect)) &&
        assert(Variant2.c3_v1_c1_d_right.source)(equalTo(Variant2.reflect)) &&
        assert(Variant1.c2_r3_r1.source)(equalTo(Variant1.reflect)) &&
        assert(Variant1.v2_c3_v1.source)(equalTo(Variant1.reflect)) &&
        assert(Variant1.c2_r3_v1_c1.source)(equalTo(Variant1.reflect)) &&
        assert(Case2.r3_v1_c1.source)(equalTo(Case2.reflect)) &&
        assert(Case3.v1_c1_d_left.source)(equalTo(Case3.reflect)) &&
        assert(Case3.v1_c1_d_right.source)(equalTo(Case3.reflect)) &&
        assert(Case3.v1_c1.source)(equalTo(Case3.reflect)) &&
        assert(Variant2.c3_v1_v2_c4.source)(equalTo(Variant2.reflect))
      },
      test("returns a focus structure") {
        assert(Variant1.c1_d.focus)(equalTo(Reflect.double[Binding])) &&
        assert(Variant1.c2_r3.focus)(equalTo(Record3.reflect)) &&
        assert(Variant2.c3_v1_c1_left.focus)(equalTo(Case1.reflect)) &&
        assert(Variant2.c3_v1_c1_right.focus)(equalTo(Case1.reflect)) &&
        assert(Variant2.c3_v1_c1_d_left.focus)(equalTo(Reflect.double[Binding])) &&
        assert(Variant2.c3_v1_c1_d_right.focus)(equalTo(Reflect.double[Binding])) &&
        assert(Variant1.c2_r3_r1.focus)(equalTo(Record1.reflect)) &&
        assert(Variant1.v2_c3_v1.focus)(equalTo(Variant1.reflect)) &&
        assert(Variant1.c2_r3_v1_c1.focus)(equalTo(Case1.reflect)) &&
        assert(Case2.r3_v1_c1.focus)(equalTo(Case1.reflect)) &&
        assert(Case3.v1_c1_d_left.focus)(equalTo(Reflect.double[Binding])) &&
        assert(Case3.v1_c1_d_right.focus)(equalTo(Reflect.double[Binding])) &&
        assert(Case3.v1_c1.focus)(equalTo(Case1.reflect)) &&
        assert(Variant2.c3_v1_v2_c4.focus)(equalTo(Case4.reflect))
      },
      test("passes check if a focus value exists") {
        assert(Variant1.c2_r3_r1.check(Case2(Record3(Record1(true, 0.1f), null, null))))(isNone) &&
        assert(Case2.r3_v1_c1.check(Case2(Record3(null, null, Case1(0.1)))))(isNone) &&
        assert(Variant1.c2_r3_v1_c1.check(Case2(Record3(null, null, Case1(0.1)))))(isNone) &&
        assert(Variant2.c3_v1_v2_c4.check(Case3(Case4(Nil))))(isNone) &&
        assert(Variant2.c3_v1_c1_left.check(Case3(Case1(0.1))))(isNone) &&
        assert(Variant2.c3_v1_c1_right.check(Case3(Case1(0.1))))(isNone) &&
        assert(Variant2.c3_v1_c1_d_right.check(Case3(Case1(0.1))))(isNone) &&
        assert(Variant2.c3_v1.check(Case3(Case1(0.1))))(isNone) &&
        assert(Case3.v1_c1_d_left.check(Case3(Case1(0.1))))(isNone) &&
        assert(Case3.v1_c1_d_right.check(Case3(Case1(0.1))))(isNone) &&
        assert(Case3.v1_c1.check(Case3(Case1(0.1))))(isNone)
      },
      test("doesn't pass check if a focus value doesn't exist") {
        assert(Variant1.c2_r3_r1.check(Case3(Case1(0.1))))(
          isSome(
            hasField[OpticCheck, String](
              "message",
              _.message,
              containsString(
                "During attempted access at .when[Case2].r3.r1, encountered an unexpected case at .when[Case2]: expected Case2, but got Variant2"
              )
            )
          )
        ) &&
        assert(Case2.r3_v1_c1.check(Case2(Record3(null, null, Case4(Nil)))))(
          isSome(
            hasField[OpticCheck, String](
              "message",
              _.message,
              containsString("1")
            )
          )
        ) &&
        assert(Variant1.c2_r3_v1_c1.check(Case2(Record3(null, null, Case4(Nil)))))(
          isSome(
            hasField[OpticCheck, String](
              "message",
              _.message,
              containsString("2")
            )
          )
        ) &&
        assert(Variant2.c3_v1_v2_c4.check(Case3(Case1(0.1))))(
          isSome(
            hasField[OpticCheck, String](
              "message",
              _.message,
              containsString("3")
            )
          )
        ) &&
        assert(Variant2.c3_v1_c1_left.check(Case4(Nil)))(
          isSome(
            hasField[OpticCheck, String](
              "message",
              _.message,
              containsString("4")
            )
          )
        ) &&
        assert(Variant2.c3_v1_c1_right.check(Case3(Case2(null))))(
          isSome(
            hasField[OpticCheck, String](
              "message",
              _.message,
              containsString(
                "During attempted access at .when[Case3].v1.when[Case1], encountered an unexpected case at .when[Case3].v1.when[Case1]: expected Case1, but got Case2"
              )
            )
          )
        ) &&
        assert(Variant2.c3_v1_c1_d_right.check(Case4(Nil)))(
          isSome(
            hasField[OpticCheck, String](
              "message",
              _.message,
              containsString(
                "During attempted access at .when[Case3].v1.when[Case1].d, encountered an unexpected case at .when[Case3]: expected Case3, but got Case4"
              )
            )
          )
        ) &&
        assert(Variant2.c3_v1.check(Case4(Nil)))(
          isSome(
            hasField[OpticCheck, String](
              "message",
              _.message,
              containsString(
                "During attempted access at .when[Case3].v1, encountered an unexpected case at .when[Case3]: expected Case3, but got Case4"
              )
            )
          )
        ) &&
        assert(Case3.v1_c1_d_left.check(Case3(Case4(Nil))))(
          isSome(
            hasField[OpticCheck, String](
              "message",
              _.message,
              containsString(
                "During attempted access at .v1.when[Case1].d, encountered an unexpected case at .v1.when[Case1]: expected Case1, but got Variant2"
              )
            )
          )
        ) &&
        assert(Case3.v1_c1_d_right.check(Case3(Case4(Nil))))(
          isSome(
            hasField[OpticCheck, String](
              "message",
              _.message,
              containsString(
                "During attempted access at .v1.when[Case1].d, encountered an unexpected case at .v1.when[Case1]: expected Case1, but got Variant2"
              )
            )
          )
        )
      },
      test("gets an optional focus value") {
        assert(Variant1.c2_r3_r1.getOption(Case2(Record3(Record1(true, 0.1f), null, null))))(
          isSome(equalTo(Record1(true, 0.1f)))
        ) &&
        assert(Case2.r3_v1_c1.getOption(Case2(Record3(null, null, Case1(0.1)))))(isSome(equalTo(Case1(0.1)))) &&
        assert(Variant1.c2_r3_v1_c1.getOption(Case2(Record3(null, null, Case1(0.1)))))(isSome(equalTo(Case1(0.1)))) &&
        assert(Variant2.c3_v1_v2_c4.getOption(Case3(Case4(Nil))))(isSome(equalTo(Case4(Nil)))) &&
        assert(Variant2.c3_v1_c1_left.getOption(Case3(Case1(0.1))))(isSome(equalTo(Case1(0.1)))) &&
        assert(Variant2.c3_v1_c1_right.getOption(Case3(Case1(0.1))))(isSome(equalTo(Case1(0.1)))) &&
        assert(Variant2.c3_v1_c1_d_right.getOption(Case3(Case1(0.1))))(isSome(equalTo(0.1))) &&
        assert(Variant2.c3_v1.getOption(Case3(Case1(0.1))))(isSome(equalTo(Case1(0.1)))) &&
        assert(Case3.v1_c1_d_left.getOption(Case3(Case1(0.1))))(isSome(equalTo(0.1))) &&
        assert(Case3.v1_c1_d_right.getOption(Case3(Case1(0.1))))(isSome(equalTo(0.1))) &&
        assert(Case3.v1_c1.getOption(Case3(Case1(0.1))))(isSome(equalTo(Case1(0.1))))
      },
      test("doesn't get a focus value if it's not possible") {
        assert(Variant1.c2_r3_r1.getOption(Case3(Case1(0.1))))(isNone) &&
        assert(Case2.r3_v1_c1.getOption(Case2(Record3(null, null, Case4(Nil)))))(isNone) &&
        assert(Variant1.c2_r3_v1_c1.getOption(Case2(Record3(null, null, Case4(Nil)))))(isNone) &&
        assert(Variant2.c3_v1_v2_c4.getOption(Case3(Case1(0.1))))(isNone) &&
        assert(Variant2.c3_v1_c1_left.getOption(Case4(Nil)))(isNone) &&
        assert(Variant2.c3_v1_c1_right.getOption(Case3(Case2(null))))(isNone) &&
        assert(Variant2.c3_v1_c1_d_right.getOption(Case4(Nil)))(isNone) &&
        assert(Variant2.c3_v1.getOption(Case4(Nil)))(isNone) &&
        assert(Case3.v1_c1_d_left.getOption(Case3(Case4(Nil))))(isNone) &&
        assert(Case3.v1_c1_d_right.getOption(Case3(Case4(Nil))))(isNone)
      },
      test("gets an optional focus value wrapped to right") {
        assert(Variant1.c2_r3_r1.getOrFail(Case2(Record3(Record1(true, 0.1f), null, null))))(
          isRight(equalTo(Record1(true, 0.1f)))
        ) &&
        assert(Case2.r3_v1_c1.getOrFail(Case2(Record3(null, null, Case1(0.1)))))(isRight(equalTo(Case1(0.1)))) &&
        assert(Variant1.c2_r3_v1_c1.getOrFail(Case2(Record3(null, null, Case1(0.1)))))(isRight(equalTo(Case1(0.1)))) &&
        assert(Variant2.c3_v1_v2_c4.getOrFail(Case3(Case4(Nil))))(isRight(equalTo(Case4(Nil)))) &&
        assert(Variant2.c3_v1_c1_left.getOrFail(Case3(Case1(0.1))))(isRight(equalTo(Case1(0.1)))) &&
        assert(Variant2.c3_v1_c1_right.getOrFail(Case3(Case1(0.1))))(isRight(equalTo(Case1(0.1)))) &&
        assert(Variant2.c3_v1_c1_d_right.getOrFail(Case3(Case1(0.1))))(isRight(equalTo(0.1))) &&
        assert(Variant2.c3_v1.getOrFail(Case3(Case1(0.1))))(isRight(equalTo(Case1(0.1)))) &&
        assert(Case3.v1_c1_d_left.getOrFail(Case3(Case1(0.1))))(isRight(equalTo(0.1))) &&
        assert(Case3.v1_c1_d_right.getOrFail(Case3(Case1(0.1))))(isRight(equalTo(0.1))) &&
        assert(Case3.v1_c1.getOrFail(Case3(Case1(0.1))))(isRight(equalTo(Case1(0.1))))
      },
      test("doesn't get a focus value if it's not possible and returns an error") {
        assert(Variant1.c2_r3_r1.getOrFail(Case3(Case1(0.1))))(
          isLeft(
            equalTo(
              OpticCheck(
                errors = ::(
                  UnexpectedCase(
                    expectedCase = "Case2",
                    actualCase = "Variant2",
                    full = DynamicOptic(Vector(Case("Case2"), Field("r3"), Field("r1"))),
                    prefix = DynamicOptic(Vector(Case("Case2"))),
                    actualValue = Case3(Case1(0.1))
                  ),
                  Nil
                )
              )
            )
          )
        ) &&
        assert(Case2.r3_v1_c1.getOrFail(Case2(Record3(null, null, Case4(Nil)))))(
          isLeft(
            equalTo(
              OpticCheck(
                errors = ::(
                  UnexpectedCase(
                    expectedCase = "Case1",
                    actualCase = "Variant2",
                    full = DynamicOptic(Vector(Field("r3"), Field("v1"), Case("Case1"))),
                    prefix = DynamicOptic(Vector(Field("r3"), Field("v1"), Case("Case1"))),
                    actualValue = Case4(Nil)
                  ),
                  Nil
                )
              )
            )
          )
        ) &&
        assert(Variant1.c2_r3_v1_c1.getOrFail(Case2(Record3(null, null, Case4(Nil)))))(
          isLeft(
            equalTo(
              OpticCheck(
                errors = ::(
                  UnexpectedCase(
                    expectedCase = "Case1",
                    actualCase = "Variant2",
                    full = DynamicOptic(Vector(Case("Case2"), Field("r3"), Field("v1"), Case("Case1"))),
                    prefix = DynamicOptic(Vector(Case("Case2"), Field("r3"), Field("v1"), Case("Case1"))),
                    actualValue = Case4(Nil)
                  ),
                  Nil
                )
              )
            )
          )
        ) &&
        assert(Variant2.c3_v1_v2_c4.getOrFail(Case3(Case1(0.1))))(
          isLeft(
            equalTo(
              OpticCheck(
                errors = ::(
                  UnexpectedCase(
                    expectedCase = "Variant2",
                    actualCase = "Case1",
                    full = DynamicOptic(Vector(Case("Case3"), Field("v1"), Case("Variant2"), Case("Case4"))),
                    prefix = DynamicOptic(Vector(Case("Case3"), Field("v1"), Case("Variant2"))),
                    actualValue = Case1(0.1)
                  ),
                  Nil
                )
              )
            )
          )
        ) &&
        assert(Variant2.c3_v1_c1_left.getOrFail(Case4(Nil)))(
          isLeft(
            equalTo(
              OpticCheck(
                errors = ::(
                  UnexpectedCase(
                    expectedCase = "Case3",
                    actualCase = "Case4",
                    full = DynamicOptic(Vector(Case("Case3"), Field("v1"), Case("Case1"))),
                    prefix = DynamicOptic(Vector(Case("Case3"))),
                    actualValue = Case4(Nil)
                  ),
                  Nil
                )
              )
            )
          )
        ) &&
        assert(Variant2.c3_v1_c1_right.getOrFail(Case3(Case2(null))))(
          isLeft(
            equalTo(
              OpticCheck(
                errors = ::(
                  UnexpectedCase(
                    expectedCase = "Case1",
                    actualCase = "Case2",
                    full = DynamicOptic(Vector(Case("Case3"), Field("v1"), Case("Case1"))),
                    prefix = DynamicOptic(Vector(Case("Case3"), Field("v1"), Case("Case1"))),
                    actualValue = Case2(null)
                  ),
                  Nil
                )
              )
            )
          )
        ) &&
        assert(Variant2.c3_v1_c1_d_right.getOrFail(Case4(Nil)))(
          isLeft(
            equalTo(
              OpticCheck(
                errors = ::(
                  UnexpectedCase(
                    expectedCase = "Case3",
                    actualCase = "Case4",
                    full = DynamicOptic(Vector(Case("Case3"), Field("v1"), Case("Case1"), Field("d"))),
                    prefix = DynamicOptic(Vector(Case("Case3"))),
                    actualValue = Case4(Nil)
                  ),
                  Nil
                )
              )
            )
          )
        ) &&
        assert(Variant2.c3_v1.getOrFail(Case4(Nil)))(
          isLeft(
            equalTo(
              OpticCheck(
                errors = ::(
                  UnexpectedCase(
                    expectedCase = "Case3",
                    actualCase = "Case4",
                    full = DynamicOptic(Vector(Case("Case3"), Field("v1"))),
                    prefix = DynamicOptic(Vector(Case("Case3"))),
                    actualValue = Case4(Nil)
                  ),
                  Nil
                )
              )
            )
          )
        ) &&
        assert(Case3.v1_c1_d_left.getOrFail(Case3(Case4(Nil))))(
          isLeft(
            equalTo(
              OpticCheck(
                errors = ::(
                  UnexpectedCase(
                    expectedCase = "Case1",
                    actualCase = "Variant2",
                    full = DynamicOptic(Vector(Field("v1"), Case("Case1"), Field("d"))),
                    prefix = DynamicOptic(Vector(Field("v1"), Case("Case1"))),
                    actualValue = Case4(Nil)
                  ),
                  Nil
                )
              )
            )
          )
        ) &&
        assert(Case3.v1_c1_d_right.getOrFail(Case3(Case4(Nil))))(
          isLeft(
            equalTo(
              OpticCheck(
                errors = ::(
                  UnexpectedCase(
                    expectedCase = "Case1",
                    actualCase = "Variant2",
                    full = DynamicOptic(Vector(Field("v1"), Case("Case1"), Field("d"))),
                    prefix = DynamicOptic(Vector(Field("v1"), Case("Case1"))),
                    actualValue = Case4(Nil)
                  ),
                  Nil
                )
              )
            )
          )
        )
      },
      test("replaces a focus value") {
        assert(Variant1.c2_r3_r1.replace(Case2(Record3(Record1(true, 0.1f), null, null)), Record1(false, 0.2f)))(
          equalTo(Case2(Record3(Record1(false, 0.2f), null, null)))
        ) &&
        assert(Case2.r3_v1_c1.replace(Case2(Record3(null, null, Case1(0.1))), Case1(0.2)))(
          equalTo(Case2(Record3(null, null, Case1(0.2))))
        ) &&
        assert(Variant1.c2_r3_v1_c1.replace(Case2(Record3(null, null, Case1(0.1))), Case1(0.2)))(
          equalTo(Case2(Record3(null, null, Case1(0.2))))
        ) &&
        assert(Variant2.c3_v1_v2_c4.replace(Case3(Case4(Nil)), Case4(null)))(equalTo(Case3(Case4(null)))) &&
        assert(Variant2.c3_v1_c1_left.replace(Case3(Case1(0.1)), Case1(0.2)))(equalTo(Case3(Case1(0.2)))) &&
        assert(Variant2.c3_v1_c1_right.replace(Case3(Case1(0.1)), Case1(0.2)))(equalTo(Case3(Case1(0.2)))) &&
        assert(Variant2.c3_v1_c1_d_right.replace(Case3(Case1(0.1)), 0.2))(equalTo(Case3(Case1(0.2)))) &&
        assert(Variant2.c3_v1.replace(Case3(Case1(0.1)), Case1(0.2)))(equalTo(Case3(Case1(0.2)))) &&
        assert(Case3.v1_c1_d_left.replace(Case3(Case1(0.1)), 0.2))(equalTo(Case3(Case1(0.2)))) &&
        assert(Case3.v1_c1_d_right.replace(Case3(Case1(0.1)), 0.2))(equalTo(Case3(Case1(0.2)))) &&
        assert(Case3.v1_c1.replace(Case3(Case1(0.1)), Case1(0.2)))(equalTo(Case3(Case1(0.2))))
      },
      test("doesn't replace a focus value if it's not possible") {
        assert(Variant1.c2_r3_r1.replace(Case3(Case1(0.1)), Record1(false, 0.2f)))(equalTo(Case3(Case1(0.1)))) &&
        assert(Case2.r3_v1_c1.replace(Case2(Record3(null, null, Case4(Nil))), Case1(0.2)))(
          equalTo(Case2(Record3(null, null, Case4(Nil))))
        ) &&
        assert(Variant1.c2_r3_v1_c1.replace(Case3(Case1(0.1)), Case1(0.2)))(equalTo(Case3(Case1(0.1)))) &&
        assert(Variant2.c3_v1_v2_c4.replace(Case3(Case1(0.1)), Case4(Nil)))(equalTo(Case3(Case1(0.1)))) &&
        assert(Variant2.c3_v1_c1_left.replace(Case4(Nil), Case1(0.2)))(equalTo(Case4(Nil))) &&
        assert(Variant2.c3_v1_c1_right.replace(Case3(Case2(null)), Case1(0.2)))(equalTo(Case3(Case2(null)))) &&
        assert(Variant2.c3_v1_c1_d_right.replace(Case4(Nil), 0.2))(equalTo(Case4(Nil))) &&
        assert(Variant2.c3_v1.replace(Case4(Nil), Case1(0.2)))(equalTo(Case4(Nil))) &&
        assert(Case3.v1_c1_d_left.replace(Case3(Case4(Nil)), 0.2))(equalTo(Case3(Case4(Nil)))) &&
        assert(Case3.v1_c1_d_right.replace(Case3(Case4(Nil)), 0.2))(equalTo(Case3(Case4(Nil))))
      },
      test("optionally replaces a focus value") {
        assert(Variant1.c2_r3_r1.replaceOption(Case2(Record3(Record1(true, 0.1f), null, null)), Record1(false, 0.2f)))(
          isSome(equalTo(Case2(Record3(Record1(false, 0.2f), null, null))))
        ) &&
        assert(Case2.r3_v1_c1.replaceOption(Case2(Record3(null, null, Case1(0.1))), Case1(0.2)))(
          isSome(equalTo(Case2(Record3(null, null, Case1(0.2)))))
        ) &&
        assert(Variant1.c2_r3_v1_c1.replaceOption(Case2(Record3(null, null, Case1(0.1))), Case1(0.2)))(
          isSome(equalTo(Case2(Record3(null, null, Case1(0.2)))))
        ) &&
        assert(Variant2.c3_v1_v2_c4.replaceOption(Case3(Case4(Nil)), Case4(null)))(
          isSome(equalTo(Case3(Case4(null))))
        ) &&
        assert(Variant2.c3_v1_c1_left.replaceOption(Case3(Case1(0.1)), Case1(0.2)))(
          isSome(equalTo(Case3(Case1(0.2))))
        ) &&
        assert(Variant2.c3_v1_c1_right.replaceOption(Case3(Case1(0.1)), Case1(0.2)))(
          isSome(equalTo(Case3(Case1(0.2))))
        ) &&
        assert(Variant2.c3_v1_c1_d_right.replaceOption(Case3(Case1(0.1)), 0.2))(isSome(equalTo(Case3(Case1(0.2))))) &&
        assert(Variant2.c3_v1.replaceOption(Case3(Case1(0.1)), Case1(0.2)))(isSome(equalTo(Case3(Case1(0.2))))) &&
        assert(Case3.v1_c1_d_left.replaceOption(Case3(Case1(0.1)), 0.2))(isSome(equalTo(Case3(Case1(0.2))))) &&
        assert(Case3.v1_c1_d_right.replaceOption(Case3(Case1(0.1)), 0.2))(isSome(equalTo(Case3(Case1(0.2))))) &&
        assert(Case3.v1_c1.replaceOption(Case3(Case1(0.1)), Case1(0.2)))(isSome(equalTo(Case3(Case1(0.2)))))
      },
      test("optionally doesn't replace a focus value if it's not possible") {
        assert(Variant1.c2_r3_r1.replaceOption(Case3(Case1(0.1)), Record1(false, 0.2f)))(isNone) &&
        assert(Case2.r3_v1_c1.replaceOption(Case2(Record3(null, null, Case4(Nil))), Case1(0.2)))(isNone) &&
        assert(Variant1.c2_r3_v1_c1.replaceOption(Case3(Case1(0.1)), Case1(0.2)))(isNone) &&
        assert(Variant2.c3_v1_v2_c4.replaceOption(Case3(Case1(0.1)), Case4(Nil)))(isNone) &&
        assert(Variant2.c3_v1_c1_left.replaceOption(Case4(Nil), Case1(0.2)))(isNone) &&
        assert(Variant2.c3_v1_c1_right.replaceOption(Case3(Case2(null)), Case1(0.2)))(isNone) &&
        assert(Variant2.c3_v1_c1_d_right.replaceOption(Case4(Nil), 0.2))(isNone) &&
        assert(Variant2.c3_v1.replaceOption(Case4(Nil), Case1(0.2)))(isNone) &&
        assert(Case3.v1_c1_d_left.replaceOption(Case3(Case4(Nil)), 0.2))(isNone) &&
        assert(Case3.v1_c1_d_right.replaceOption(Case3(Case4(Nil)), 0.2))(isNone)
      },
      test("optionally replaces a focus value wrapped by right") {
        assert(Variant1.c2_r3_r1.replaceOrFail(Case2(Record3(Record1(true, 0.1f), null, null)), Record1(false, 0.2f)))(
          isRight(equalTo(Case2(Record3(Record1(false, 0.2f), null, null))))
        ) &&
        assert(Case2.r3_v1_c1.replaceOrFail(Case2(Record3(null, null, Case1(0.1))), Case1(0.2)))(
          isRight(equalTo(Case2(Record3(null, null, Case1(0.2)))))
        ) &&
        assert(Variant1.c2_r3_v1_c1.replaceOrFail(Case2(Record3(null, null, Case1(0.1))), Case1(0.2)))(
          isRight(equalTo(Case2(Record3(null, null, Case1(0.2)))))
        ) &&
        assert(Variant2.c3_v1_v2_c4.replaceOrFail(Case3(Case4(Nil)), Case4(null)))(
          isRight(equalTo(Case3(Case4(null))))
        ) &&
        assert(Variant2.c3_v1_c1_left.replaceOrFail(Case3(Case1(0.1)), Case1(0.2)))(
          isRight(equalTo(Case3(Case1(0.2))))
        ) &&
        assert(Variant2.c3_v1_c1_right.replaceOrFail(Case3(Case1(0.1)), Case1(0.2)))(
          isRight(equalTo(Case3(Case1(0.2))))
        ) &&
        assert(Variant2.c3_v1_c1_d_right.replaceOrFail(Case3(Case1(0.1)), 0.2))(isRight(equalTo(Case3(Case1(0.2))))) &&
        assert(Variant2.c3_v1.replaceOrFail(Case3(Case1(0.1)), Case1(0.2)))(isRight(equalTo(Case3(Case1(0.2))))) &&
        assert(Case3.v1_c1_d_left.replaceOrFail(Case3(Case1(0.1)), 0.2))(isRight(equalTo(Case3(Case1(0.2))))) &&
        assert(Case3.v1_c1_d_right.replaceOrFail(Case3(Case1(0.1)), 0.2))(isRight(equalTo(Case3(Case1(0.2))))) &&
        assert(Case3.v1_c1.replaceOrFail(Case3(Case1(0.1)), Case1(0.2)))(isRight(equalTo(Case3(Case1(0.2)))))
      },
      test("optionally doesn't replace a focus value if it's not possible and returns an error") {
        assert(Variant1.c2_r3_r1.replaceOrFail(Case3(Case1(0.1)), Record1(false, 0.2f)))(
          isLeft(
            equalTo(
              OpticCheck(
                errors = ::(
                  UnexpectedCase(
                    expectedCase = "Case2",
                    actualCase = "Variant2",
                    full = DynamicOptic(Vector(Case("Case2"), Field("r3"), Field("r1"))),
                    prefix = DynamicOptic(Vector(Case("Case2"))),
                    actualValue = Case3(Case1(0.1))
                  ),
                  Nil
                )
              )
            )
          )
        ) &&
        assert(Case2.r3_v1_c1.replaceOrFail(Case2(Record3(null, null, Case4(Nil))), Case1(0.2)))(
          isLeft(
            equalTo(
              OpticCheck(
                errors = ::(
                  UnexpectedCase(
                    expectedCase = "Case1",
                    actualCase = "Variant2",
                    full = DynamicOptic(Vector(Field("r3"), Field("v1"), Case("Case1"))),
                    prefix = DynamicOptic(Vector(Field("r3"), Field("v1"), Case("Case1"))),
                    actualValue = Case4(Nil)
                  ),
                  Nil
                )
              )
            )
          )
        ) &&
        assert(Variant1.c2_r3_v1_c1.replaceOrFail(Case2(Record3(null, null, Case4(Nil))), Case1(0.2)))(
          isLeft(
            equalTo(
              OpticCheck(
                errors = ::(
                  UnexpectedCase(
                    expectedCase = "Case1",
                    actualCase = "Variant2",
                    full = DynamicOptic(Vector(Case("Case2"), Field("r3"), Field("v1"), Case("Case1"))),
                    prefix = DynamicOptic(Vector(Case("Case2"), Field("r3"), Field("v1"), Case("Case1"))),
                    actualValue = Case4(Nil)
                  ),
                  Nil
                )
              )
            )
          )
        ) &&
        assert(Variant2.c3_v1_v2_c4.replaceOrFail(Case3(Case1(0.1)), Case4(Nil)))(
          isLeft(
            equalTo(
              OpticCheck(
                errors = ::(
                  UnexpectedCase(
                    expectedCase = "Variant2",
                    actualCase = "Case1",
                    full = DynamicOptic(Vector(Case("Case3"), Field("v1"), Case("Variant2"), Case("Case4"))),
                    prefix = DynamicOptic(Vector(Case("Case3"), Field("v1"), Case("Variant2"))),
                    actualValue = Case1(0.1)
                  ),
                  Nil
                )
              )
            )
          )
        ) &&
        assert(Variant2.c3_v1_c1_left.replaceOrFail(Case4(Nil), Case1(0.2)))(
          isLeft(
            equalTo(
              OpticCheck(
                errors = ::(
                  UnexpectedCase(
                    expectedCase = "Case3",
                    actualCase = "Case4",
                    full = DynamicOptic(Vector(Case("Case3"), Field("v1"), Case("Case1"))),
                    prefix = DynamicOptic(Vector(Case("Case3"))),
                    actualValue = Case4(Nil)
                  ),
                  Nil
                )
              )
            )
          )
        ) &&
        assert(Variant2.c3_v1_c1_right.replaceOrFail(Case3(Case2(null)), Case1(0.2)))(
          isLeft(
            equalTo(
              OpticCheck(
                errors = ::(
                  UnexpectedCase(
                    expectedCase = "Case1",
                    actualCase = "Case2",
                    full = DynamicOptic(Vector(Case("Case3"), Field("v1"), Case("Case1"))),
                    prefix = DynamicOptic(Vector(Case("Case3"), Field("v1"), Case("Case1"))),
                    actualValue = Case2(null)
                  ),
                  Nil
                )
              )
            )
          )
        ) &&
        assert(Variant2.c3_v1_c1_d_right.replaceOrFail(Case4(Nil), 0.2))(
          isLeft(
            equalTo(
              OpticCheck(
                errors = ::(
                  UnexpectedCase(
                    expectedCase = "Case3",
                    actualCase = "Case4",
                    full = DynamicOptic(Vector(Case("Case3"), Field("v1"), Case("Case1"), Field("d"))),
                    prefix = DynamicOptic(Vector(Case("Case3"))),
                    actualValue = Case4(Nil)
                  ),
                  Nil
                )
              )
            )
          )
        ) &&
        assert(Variant2.c3_v1.replaceOrFail(Case4(Nil), Case1(0.2)))(
          isLeft(
            equalTo(
              OpticCheck(
                errors = ::(
                  UnexpectedCase(
                    expectedCase = "Case3",
                    actualCase = "Case4",
                    full = DynamicOptic(Vector(Case("Case3"), Field("v1"))),
                    prefix = DynamicOptic(Vector(Case("Case3"))),
                    actualValue = Case4(Nil)
                  ),
                  Nil
                )
              )
            )
          )
        ) &&
        assert(Case3.v1_c1_d_left.replaceOrFail(Case3(Case4(Nil)), 0.2))(
          isLeft(
            equalTo(
              OpticCheck(
                errors = ::(
                  UnexpectedCase(
                    expectedCase = "Case1",
                    actualCase = "Variant2",
                    full = DynamicOptic(Vector(Field("v1"), Case("Case1"), Field("d"))),
                    prefix = DynamicOptic(Vector(Field("v1"), Case("Case1"))),
                    actualValue = Case4(Nil)
                  ),
                  Nil
                )
              )
            )
          )
        ) &&
        assert(Case3.v1_c1_d_right.replaceOrFail(Case3(Case4(Nil)), 0.2))(
          isLeft(
            equalTo(
              OpticCheck(
                errors = ::(
                  UnexpectedCase(
                    expectedCase = "Case1",
                    actualCase = "Variant2",
                    full = DynamicOptic(Vector(Field("v1"), Case("Case1"), Field("d"))),
                    prefix = DynamicOptic(Vector(Field("v1"), Case("Case1"))),
                    actualValue = Case4(Nil)
                  ),
                  Nil
                )
              )
            )
          )
        )
      },
      test("modifies a focus value") {
        assert(Variant1.c2_r3_r1.modify(Case2(Record3(Record1(true, 0.1f), null, null)), _ => Record1(false, 0.2f)))(
          equalTo(Case2(Record3(Record1(false, 0.2f), null, null)))
        ) &&
        assert(Case2.r3_v1_c1.modify(Case2(Record3(null, null, Case1(0.1))), _ => Case1(0.2)))(
          equalTo(Case2(Record3(null, null, Case1(0.2))))
        ) &&
        assert(Variant1.c2_r3_v1_c1.modify(Case2(Record3(null, null, Case1(0.1))), _ => Case1(0.2)))(
          equalTo(Case2(Record3(null, null, Case1(0.2))))
        ) &&
        assert(Variant2.c3_v1_v2_c4.modify(Case3(Case4(Nil)), _ => Case4(null)))(equalTo(Case3(Case4(null)))) &&
        assert(Variant2.c3_v1_c1_left.modify(Case3(Case1(0.1)), _ => Case1(0.2)))(equalTo(Case3(Case1(0.2)))) &&
        assert(Variant2.c3_v1_c1_right.modify(Case3(Case1(0.1)), _ => Case1(0.2)))(equalTo(Case3(Case1(0.2)))) &&
        assert(Variant2.c3_v1_c1_d_right.modify(Case3(Case1(0.1)), _ => 0.2))(equalTo(Case3(Case1(0.2)))) &&
        assert(Variant2.c3_v1.modify(Case3(Case1(0.1)), _ => Case1(0.2)))(equalTo(Case3(Case1(0.2)))) &&
        assert(Case3.v1_c1_d_left.modify(Case3(Case1(0.1)), _ => 0.2))(equalTo(Case3(Case1(0.2)))) &&
        assert(Case3.v1_c1_d_right.modify(Case3(Case1(0.1)), _ => 0.2))(equalTo(Case3(Case1(0.2)))) &&
        assert(Case3.v1_c1.modify(Case3(Case1(0.1)), _ => Case1(0.2)))(equalTo(Case3(Case1(0.2))))
      },
      test("doesn't modify a focus value if it's not possible") {
        assert(Variant1.c2_r3_r1.modify(Case3(Case1(0.1)), _ => Record1(false, 0.2f)))(equalTo(Case3(Case1(0.1)))) &&
        assert(Case2.r3_v1_c1.modify(Case2(Record3(null, null, Case4(Nil))), _ => Case1(0.2)))(
          equalTo(Case2(Record3(null, null, Case4(Nil))))
        ) &&
        assert(Variant1.c2_r3_v1_c1.modify(Case3(Case1(0.1)), _ => Case1(0.2)))(equalTo(Case3(Case1(0.1)))) &&
        assert(Variant2.c3_v1_v2_c4.modify(Case3(Case1(0.1)), _ => Case4(Nil)))(equalTo(Case3(Case1(0.1)))) &&
        assert(Variant2.c3_v1_c1_left.modify(Case4(Nil), _ => Case1(0.2)))(equalTo(Case4(Nil))) &&
        assert(Variant2.c3_v1_c1_right.modify(Case4(Nil), _ => Case1(0.2)))(equalTo(Case4(Nil))) &&
        assert(Variant2.c3_v1_c1_d_right.modify(Case4(Nil), _ => 0.2))(equalTo(Case4(Nil))) &&
        assert(Variant2.c3_v1.modify(Case4(Nil), _ => Case1(0.2)))(equalTo(Case4(Nil))) &&
        assert(Case3.v1_c1_d_left.modify(Case3(Case4(Nil)), _ => 0.2))(equalTo(Case3(Case4(Nil)))) &&
        assert(Case3.v1_c1_d_right.modify(Case3(Case4(Nil)), _ => 0.2))(equalTo(Case3(Case4(Nil))))
      },
      test("modifies a focus value wrapped to some") {
        assert(
          Variant1.c2_r3_r1.modifyOption(Case2(Record3(Record1(true, 0.1f), null, null)), _ => Record1(false, 0.2f))
        )(
          isSome(equalTo(Case2(Record3(Record1(false, 0.2f), null, null))))
        ) &&
        assert(Case2.r3_v1_c1.modifyOption(Case2(Record3(null, null, Case1(0.1))), _ => Case1(0.2)))(
          isSome(equalTo(Case2(Record3(null, null, Case1(0.2)))))
        ) &&
        assert(Variant1.c2_r3_v1_c1.modifyOption(Case2(Record3(null, null, Case1(0.1))), _ => Case1(0.2)))(
          isSome(equalTo(Case2(Record3(null, null, Case1(0.2)))))
        ) &&
        assert(Variant2.c3_v1_v2_c4.modifyOption(Case3(Case4(Nil)), _ => Case4(null)))(
          isSome(equalTo(Case3(Case4(null))))
        ) &&
        assert(Variant2.c3_v1_c1_left.modifyOption(Case3(Case1(0.1)), _ => Case1(0.2)))(
          isSome(equalTo(Case3(Case1(0.2))))
        ) &&
        assert(Variant2.c3_v1_c1_right.modifyOption(Case3(Case1(0.1)), _ => Case1(0.2)))(
          isSome(equalTo(Case3(Case1(0.2))))
        ) &&
        assert(Variant2.c3_v1_c1_d_right.modifyOption(Case3(Case1(0.1)), _ => 0.2))(
          isSome(equalTo(Case3(Case1(0.2))))
        ) &&
        assert(Variant2.c3_v1.modifyOption(Case3(Case1(0.1)), _ => Case1(0.2)))(isSome(equalTo(Case3(Case1(0.2))))) &&
        assert(Case3.v1_c1_d_left.modifyOption(Case3(Case1(0.1)), _ => 0.2))(isSome(equalTo(Case3(Case1(0.2))))) &&
        assert(Case3.v1_c1_d_right.modifyOption(Case3(Case1(0.1)), _ => 0.2))(isSome(equalTo(Case3(Case1(0.2))))) &&
        assert(Case3.v1_c1.modifyOption(Case3(Case1(0.1)), _ => Case1(0.2)))(isSome(equalTo(Case3(Case1(0.2)))))
      },
      test("doesn't modify a focus value returning none") {
        assert(Variant1.c2_r3_r1.modifyOption(Case3(Case1(0.1)), _ => Record1(false, 0.2f)))(isNone) &&
        assert(Case2.r3_v1_c1.modifyOption(Case2(Record3(null, null, Case4(Nil))), _ => Case1(0.2)))(isNone) &&
        assert(Variant1.c2_r3_v1_c1.modifyOption(Case3(Case1(0.1)), _ => Case1(0.2)))(isNone) &&
        assert(Variant2.c3_v1_v2_c4.modifyOption(Case3(Case1(0.1)), _ => Case4(Nil)))(isNone) &&
        assert(Variant2.c3_v1_c1_left.modifyOption(Case4(Nil), _ => Case1(0.2)))(isNone) &&
        assert(Variant2.c3_v1_c1_right.modifyOption(Case4(Nil), _ => Case1(0.2)))(isNone) &&
        assert(Variant2.c3_v1_c1_d_right.modifyOption(Case4(Nil), _ => 0.2))(isNone) &&
        assert(Variant2.c3_v1.modifyOption(Case4(Nil), _ => Case1(0.2)))(isNone) &&
        assert(Case3.v1_c1_d_left.modifyOption(Case3(Case4(Nil)), _ => 0.2))(isNone) &&
        assert(Case3.v1_c1_d_right.modifyOption(Case3(Case4(Nil)), _ => 0.2))(isNone)
      },
      test("modifies a focus value wrapped to right") {
        assert(
          Variant1.c2_r3_r1.modifyOrFail(Case2(Record3(Record1(true, 0.1f), null, null)), _ => Record1(false, 0.2f))
        )(
          isRight(equalTo(Case2(Record3(Record1(false, 0.2f), null, null))))
        ) &&
        assert(Case2.r3_v1_c1.modifyOrFail(Case2(Record3(null, null, Case1(0.1))), _ => Case1(0.2)))(
          isRight(equalTo(Case2(Record3(null, null, Case1(0.2)))))
        ) &&
        assert(Variant1.c2_r3_v1_c1.modifyOrFail(Case2(Record3(null, null, Case1(0.1))), _ => Case1(0.2)))(
          isRight(equalTo(Case2(Record3(null, null, Case1(0.2)))))
        ) &&
        assert(Variant2.c3_v1_v2_c4.modifyOrFail(Case3(Case4(Nil)), _ => Case4(null)))(
          isRight(equalTo(Case3(Case4(null))))
        ) &&
        assert(Variant2.c3_v1_c1_left.modifyOrFail(Case3(Case1(0.1)), _ => Case1(0.2)))(
          isRight(equalTo(Case3(Case1(0.2))))
        ) &&
        assert(Variant2.c3_v1_c1_right.modifyOrFail(Case3(Case1(0.1)), _ => Case1(0.2)))(
          isRight(equalTo(Case3(Case1(0.2))))
        ) &&
        assert(Variant2.c3_v1_c1_d_right.modifyOrFail(Case3(Case1(0.1)), _ => 0.2))(
          isRight(equalTo(Case3(Case1(0.2))))
        ) &&
        assert(Variant2.c3_v1.modifyOrFail(Case3(Case1(0.1)), _ => Case1(0.2)))(isRight(equalTo(Case3(Case1(0.2))))) &&
        assert(Case3.v1_c1_d_left.modifyOrFail(Case3(Case1(0.1)), _ => 0.2))(isRight(equalTo(Case3(Case1(0.2))))) &&
        assert(Case3.v1_c1_d_right.modifyOrFail(Case3(Case1(0.1)), _ => 0.2))(isRight(equalTo(Case3(Case1(0.2))))) &&
        assert(Case3.v1_c1.modifyOrFail(Case3(Case1(0.1)), _ => Case1(0.2)))(isRight(equalTo(Case3(Case1(0.2)))))
      },
      test("doesn't modify a focus value returning none") {
        assert(Variant1.c2_r3_r1.modifyOrFail(Case3(Case1(0.1)), _ => Record1(false, 0.2f)))(
          isLeft(
            equalTo(
              OpticCheck(
                errors = ::(
                  UnexpectedCase(
                    expectedCase = "Case2",
                    actualCase = "Variant2",
                    full = DynamicOptic(Vector(Case("Case2"), Field("r3"), Field("r1"))),
                    prefix = DynamicOptic(Vector(Case("Case2"))),
                    actualValue = Case3(Case1(0.1))
                  ),
                  Nil
                )
              )
            )
          )
        ) &&
        assert(Case2.r3_v1_c1.modifyOrFail(Case2(Record3(null, null, Case4(Nil))), _ => Case1(0.2)))(
          isLeft(
            equalTo(
              OpticCheck(
                errors = ::(
                  UnexpectedCase(
                    expectedCase = "Case1",
                    actualCase = "Variant2",
                    full = DynamicOptic(Vector(Field("r3"), Field("v1"), Case("Case1"))),
                    prefix = DynamicOptic(Vector(Field("r3"), Field("v1"), Case("Case1"))),
                    actualValue = Case4(Nil)
                  ),
                  Nil
                )
              )
            )
          )
        ) &&
        assert(Variant1.c2_r3_v1_c1.modifyOrFail(Case3(Case1(0.1)), _ => Case1(0.2)))(
          isLeft(
            equalTo(
              OpticCheck(
                errors = ::(
                  UnexpectedCase(
                    expectedCase = "Case2",
                    actualCase = "Variant2",
                    full = DynamicOptic(Vector(Case("Case2"), Field("r3"), Field("v1"), Case("Case1"))),
                    prefix = DynamicOptic(Vector(Case("Case2"))),
                    actualValue = Case3(Case1(0.1))
                  ),
                  Nil
                )
              )
            )
          )
        ) &&
        assert(Variant2.c3_v1_v2_c4.modifyOrFail(Case3(Case1(0.1)), _ => Case4(Nil)))(
          isLeft(
            equalTo(
              OpticCheck(
                errors = ::(
                  UnexpectedCase(
                    expectedCase = "Variant2",
                    actualCase = "Case1",
                    full = DynamicOptic(Vector(Case("Case3"), Field("v1"), Case("Variant2"), Case("Case4"))),
                    prefix = DynamicOptic(Vector(Case("Case3"), Field("v1"), Case("Variant2"))),
                    actualValue = Case1(0.1)
                  ),
                  Nil
                )
              )
            )
          )
        ) &&
        assert(Variant2.c3_v1_c1_left.modifyOrFail(Case4(Nil), _ => Case1(0.2)))(
          isLeft(
            equalTo(
              OpticCheck(
                errors = ::(
                  UnexpectedCase(
                    expectedCase = "Case3",
                    actualCase = "Case4",
                    full = DynamicOptic(Vector(Case("Case3"), Field("v1"), Case("Case1"))),
                    prefix = DynamicOptic(Vector(Case("Case3"))),
                    actualValue = Case4(Nil)
                  ),
                  Nil
                )
              )
            )
          )
        ) &&
        assert(Variant2.c3_v1_c1_right.modifyOrFail(Case4(Nil), _ => Case1(0.2)))(
          isLeft(
            equalTo(
              OpticCheck(
                errors = ::(
                  UnexpectedCase(
                    expectedCase = "Case3",
                    actualCase = "Case4",
                    full = DynamicOptic(Vector(Case("Case3"), Field("v1"), Case("Case1"))),
                    prefix = DynamicOptic(Vector(Case("Case3"))),
                    actualValue = Case4(Nil)
                  ),
                  Nil
                )
              )
            )
          )
        ) &&
        assert(Variant2.c3_v1_c1_d_right.modifyOrFail(Case4(Nil), _ => 0.2))(
          isLeft(
            equalTo(
              OpticCheck(
                errors = ::(
                  UnexpectedCase(
                    expectedCase = "Case3",
                    actualCase = "Case4",
                    full = DynamicOptic(Vector(Case("Case3"), Field("v1"), Case("Case1"), Field("d"))),
                    prefix = DynamicOptic(Vector(Case("Case3"))),
                    actualValue = Case4(Nil)
                  ),
                  Nil
                )
              )
            )
          )
        ) &&
        assert(Variant2.c3_v1.modifyOrFail(Case4(Nil), _ => Case1(0.2)))(
          isLeft(
            equalTo(
              OpticCheck(
                errors = ::(
                  UnexpectedCase(
                    expectedCase = "Case3",
                    actualCase = "Case4",
                    full = DynamicOptic(Vector(Case("Case3"), Field("v1"))),
                    prefix = DynamicOptic(Vector(Case("Case3"))),
                    actualValue = Case4(Nil)
                  ),
                  Nil
                )
              )
            )
          )
        ) &&
        assert(Case3.v1_c1_d_left.modifyOrFail(Case3(Case4(Nil)), _ => 0.2))(
          isLeft(
            equalTo(
              OpticCheck(
                errors = ::(
                  UnexpectedCase(
                    expectedCase = "Case1",
                    actualCase = "Variant2",
                    full = DynamicOptic(Vector(Field("v1"), Case("Case1"), Field("d"))),
                    prefix = DynamicOptic(Vector(Field("v1"), Case("Case1"))),
                    actualValue = Case4(Nil)
                  ),
                  Nil
                )
              )
            )
          )
        ) &&
        assert(Case3.v1_c1_d_right.modifyOrFail(Case3(Case4(Nil)), _ => 0.2))(
          isLeft(
            equalTo(
              OpticCheck(
                errors = ::(
                  UnexpectedCase(
                    expectedCase = "Case1",
                    actualCase = "Variant2",
                    full = DynamicOptic(Vector(Field("v1"), Case("Case1"), Field("d"))),
                    prefix = DynamicOptic(Vector(Field("v1"), Case("Case1"))),
                    actualValue = Case4(Nil)
                  ),
                  Nil
                )
              )
            )
          )
        )
      }
    ),
    suite("Traversal")(
      test("path") {
        assert(Record2.vi.toDynamic)(
          equalTo(DynamicOptic(Vector(DynamicOptic.Node.Field("vi"), DynamicOptic.Node.Elements)))
        ) &&
        assert(Collections.ai.toDynamic)(equalTo(DynamicOptic(Vector(DynamicOptic.Node.Elements)))) &&
        assert(Collections.mkc.toDynamic)(equalTo(DynamicOptic(Vector(DynamicOptic.Node.MapKeys)))) &&
        assert(Collections.mvs.toDynamic)(equalTo(DynamicOptic(Vector(DynamicOptic.Node.MapValues)))) &&
        assert(Collections.lc1.toDynamic)(
          equalTo(DynamicOptic(Vector(DynamicOptic.Node.Elements, DynamicOptic.Node.Case("Case1"))))
        )
      },
      test("checks prerequisites for creation") {
        ZIO
          .attempt(Traversal.arrayValues(null))
          .flip
          .map(e => assertTrue(e.isInstanceOf[IllegalArgumentException])) &&
        ZIO
          .attempt(Traversal.listValues(null))
          .flip
          .map(e => assertTrue(e.isInstanceOf[IllegalArgumentException])) &&
        ZIO
          .attempt(Traversal.setValues(null))
          .flip
          .map(e => assertTrue(e.isInstanceOf[IllegalArgumentException])) &&
        ZIO
          .attempt(Traversal.vectorValues(null))
          .flip
          .map(e => assertTrue(e.isInstanceOf[IllegalArgumentException]))
      },
      test("has consistent equals and hashCode") {
        assert(Record2.vi)(equalTo(Record2.vi)) &&
        assert(Record2.vi.hashCode)(equalTo(Record2.vi.hashCode)) &&
        assert(Collections.ai)(equalTo(Collections.ai)) &&
        assert(Collections.ai.hashCode)(equalTo(Collections.ai.hashCode)) &&
        assert(Collections.mkc)(equalTo(Collections.mkc)) &&
        assert(Collections.mkc.hashCode)(equalTo(Collections.mkc.hashCode)) &&
        assert(Collections.mvs)(equalTo(Collections.mvs)) &&
        assert(Collections.mvs.hashCode)(equalTo(Collections.mvs.hashCode)) &&
        assert(Variant2.c3_v1_v2_c4_lr3)(equalTo(Variant2.c3_v1_v2_c4_lr3)) &&
        assert(Variant2.c3_v1_v2_c4_lr3.hashCode)(equalTo(Variant2.c3_v1_v2_c4_lr3.hashCode)) &&
        assert(Record2.r1_f: Any)(not(equalTo(Record2.vi))) &&
        assert(Variant2.c4_lr3: Any)(not(equalTo(Case4.lr3))) &&
        assert(Case4.lr3_r2_r1: Any)(not(equalTo(Case4.lr3))) &&
        assert(Traversal.mapKeys(Reflect.map(Reflect.int, Reflect.int)))(
          not(equalTo(Traversal.mapValues(Reflect.map(Reflect.int, Reflect.int))))
        ) &&
        assert(Case4.lr3_r2_r1: Any)(not(equalTo(""))) &&
        assert(Collections.lb: Any)(not(equalTo(""))) &&
        assert(Collections.mkc: Any)(not(equalTo(""))) &&
        assert(Collections.mvs: Any)(not(equalTo("")))
      },
      test("returns a source structure") {
        assert(Collections.ai.source)(equalTo(Reflect.array(Reflect.int[Binding]))) &&
        assert(Collections.mkc.source)(equalTo(Reflect.map(Reflect.char[Binding], Reflect.string[Binding]))) &&
        assert(Collections.mvs.source)(equalTo(Reflect.map(Reflect.char[Binding], Reflect.string[Binding]))) &&
        assert(Collections.lc1.source)(equalTo(Reflect.list(Variant1.reflect))) &&
        assert(Collections.lc1_d.source)(equalTo(Reflect.list(Variant1.reflect))) &&
        assert(Collections.lc4_lr3.source)(equalTo(Reflect.list(Case4.reflect))) &&
        assert(Collections.lr1.source)(equalTo(Reflect.list(Record1.reflect))) &&
        assert(Record2.vi.source)(equalTo(Record2.reflect)) &&
        assert(Case4.lr3.source)(equalTo(Case4.reflect)) &&
        assert(Case4.lr3_r2_r1.source)(equalTo(Case4.reflect)) &&
        assert(Variant2.c4_lr3.source)(equalTo(Variant2.reflect)) &&
        assert(Variant2.c3_v1_v2_c4_lr3.source)(equalTo(Variant2.reflect))
      },
      test("returns a focus structure") {
        assert(Collections.ai.focus)(equalTo(Reflect.int[Binding])) &&
        assert(Collections.mkc.focus)(equalTo(Reflect.char[Binding])) &&
        assert(Collections.mvs.focus)(equalTo(Reflect.string[Binding])) &&
        assert(Collections.lc1.focus)(equalTo(Case1.reflect)) &&
        assert(Collections.lc1_d.focus)(equalTo(Reflect.double[Binding])) &&
        assert(Collections.lc4_lr3.focus)(equalTo(Record3.reflect)) &&
        assert(Collections.lr1.focus)(equalTo(Reflect.boolean[Binding])) &&
        assert(Record2.vi.focus)(equalTo(Reflect.int[Binding])) &&
        assert(Case4.lr3.focus)(equalTo(Record3.reflect)) &&
        assert(Case4.lr3_r2_r1.focus)(equalTo(Record1.reflect)) &&
        assert(Variant2.c4_lr3.focus)(equalTo(Record3.reflect)) &&
        assert(Variant2.c3_v1_v2_c4_lr3.focus)(equalTo(Record3.reflect))
      },
      test("checks collection values and returns none if they will be modified") {
        assert(Collections.mkc.check(Map('a' -> "1", 'b' -> "2", 'c' -> "3")))(isNone) &&
        assert(Collections.mvs.check(Map('a' -> "1", 'b' -> "2", 'c' -> "3")))(isNone) &&
        assert(Collections.lc1.check(List(Case1(0.1))))(isNone) &&
        assert(Collections.lc1_d.check(List(Case1(0.1))))(isNone) &&
        assert(Collections.lc4_lr3.check(List(Case4(List(Record3(null, null, null))))))(isNone) &&
        assert(Collections.lr1.check(List(Record1(true, 0.1f))))(isNone) &&
        assert(Collections.mkv1_c1_d.check(Map(Case1(0.1) -> 1)))(isNone) &&
        assert(Collections.mvv1_c1_d.check(Map(1 -> Case1(0.1))))(isNone) &&
        assert(Record2.vi.check(Record2(2L, Vector(1, 2, 3), null)))(isNone) &&
        assert(Variant2.c4_lr3.check(Case4(List(Record3(null, null, null)))))(isNone) &&
        assert(Variant2.c3_v1_v2_c4_lr3.check(Case3(Case4(List(Record3(null, null, null))))))(isNone)
      },
      test("checks collection values and returns an error if they will not be modified") {
        assert(Collections.mkv1_c1_d.check(Map(Case2(null) -> 1, Case6(null) -> 2)))(
          isSome(
            hasField[OpticCheck, String](
              "message",
              _.message,
              containsString(
                "During attempted access at .eachKey.when[Case1].d, encountered an unexpected case at .eachKey.when[Case1]: expected Case1, but got Case2\nDuring attempted access at .eachKey.when[Case1].d, encountered an unexpected case at .eachKey.when[Case1]: expected Case1, but got Variant2"
              )
            )
          )
        ) &&
        assert(Collections.mkc.check(Map.empty[Char, String]))(
          isSome(
            hasField[OpticCheck, String](
              "message",
              _.message,
              containsString("During attempted access at .eachKey, encountered an empty map at .eachKey")
            )
          )
        ) &&
        assert(Collections.mvs.check(Map.empty[Char, String]))(
          isSome(
            hasField[OpticCheck, String](
              "message",
              _.message,
              containsString("During attempted access at .eachValue, encountered an empty map at .eachValue")
            )
          )
        ) &&
        assert(Variant2.c3_v1_v2_c4_lr3.check(Case3(Case4(Nil))))(
          isSome(
            hasField[OpticCheck, String](
              "message",
              _.message,
              containsString(
                "During attempted access at .when[Case3].v1.when[Variant2].when[Case4].lr3.each, encountered an empty sequence at .when[Case3].v1.when[Variant2].when[Case4].lr3.each"
              )
            )
          )
        ) &&
        assert(Variant2.c3_v1_v2_c4_lr3.check(Case4(Nil)))(
          isSome(
            hasField[OpticCheck, String](
              "message",
              _.message,
              containsString(
                "During attempted access at .when[Case3].v1.when[Variant2].when[Case4].lr3.each, encountered an unexpected case at .when[Case3]: expected Case3, but got Case4"
              )
            )
          )
        ) &&
        assert(Variant2.c4_lr3.check(Case3(Case1(0.1))))(
          isSome(
            hasField[OpticCheck, String](
              "message",
              _.message,
              containsString(
                "During attempted access at .when[Case4].lr3.each, encountered an unexpected case at .when[Case4]: expected Case4, but got Case3"
              )
            )
          )
        )
      },
      test("modifies collection values") {
        assert(Collections.abl.modify(Array(true, false, true), x => !x).toList)(equalTo(List(false, true, false))) &&
        assert(Collections.ab.modify(Array(1: Byte, 2: Byte, 3: Byte), x => (x + 1).toByte).toList)(
          equalTo(List(2: Byte, 3: Byte, 4: Byte))
        ) &&
        assert(Collections.ash.modify(Array(1: Short, 2: Short, 3: Short), x => (x + 1).toShort).toList)(
          equalTo(List(2: Short, 3: Short, 4: Short))
        ) &&
        assert(Collections.ai.modify(Array(1, 2, 3), _ + 1).toList)(equalTo(List(2, 3, 4))) &&
        assert(Collections.al.modify(Array(1L, 2L, 3L), _ + 1L).toList)(equalTo(List(2L, 3L, 4L))) &&
        assert(Collections.ad.modify(Array(1.0, 2.0, 3.0), _ + 1.0).toList)(equalTo(List(2.0, 3.0, 4.0))) &&
        assert(Collections.af.modify(Array(1.0f, 2.0f, 3.0f), _ + 1.0f).toList)(equalTo(List(2.0f, 3.0f, 4.0f))) &&
        assert(Collections.ac.modify(Array('a', 'b', 'c'), _.toUpper).toList)(equalTo(List('A', 'B', 'C'))) &&
        assert(Collections.as.modify(Array("1", "2", "3"), _ + "x").toList)(equalTo(List("1x", "2x", "3x"))) &&
        assert(Collections.mkc.modify(Map('a' -> "1", 'b' -> "2", 'c' -> "3"), _.toUpper))(
          equalTo(Map('A' -> "1", 'B' -> "2", 'C' -> "3"))
        ) &&
        assert(Collections.mvs.modify(Map('a' -> "1", 'b' -> "2", 'c' -> "3"), _ + "x"))(
          equalTo(Map('a' -> "1x", 'b' -> "2x", 'c' -> "3x"))
        ) &&
        assert(Collections.lc1.modify(List(Case1(0.1)), _.copy(d = 0.2)))(equalTo(List(Case1(0.2)))) &&
        assert(Collections.lc1_d.modify(List(Case1(0.1)), _ + 0.4))(equalTo(List(Case1(0.5)))) &&
        assert(Collections.mkv1_c1_d.modify(Map(Case1(0.1) -> 1), _ + 0.4))(
          equalTo(Map[Variant1, Int](Case1(0.5) -> 1))
        ) &&
        assert(Collections.mvv1_c1_d.modify(Map(1 -> Case1(0.1)), _ + 0.4))(equalTo(Map(1 -> Case1(0.5)))) &&
        assert(
          Collections.lc4_lr3.modify(List(Case4(List(Record3(null, null, null)))), _.copy(r1 = Record1(true, 0.1f)))
        )(
          equalTo(List(Case4(List(Record3(Record1(true, 0.1f), null, null)))))
        ) &&
        assert(Collections.lr1.modify(List(Record1(true, 0.1f)), x => !x))(equalTo(List(Record1(false, 0.1f)))) &&
        assert(Record2.vi.modify(Record2(2L, Vector(1, 2, 3), null), _ + 1))(
          equalTo(Record2(2L, Vector(2, 3, 4), null))
        ) &&
        assert(Variant2.c4_lr3.modify(Case4(List(Record3(null, null, null))), _ => null))(equalTo(Case4(List(null)))) &&
        assert(Variant2.c3_v1_v2_c4_lr3.modify(Case3(Case4(List(Record3(null, null, null)))), _ => null))(
          equalTo(Case3(Case4(List(null))))
        )
      },
      test("doesn't modify collection values for non-matching cases") {
        assert(Collections.mkv1_c1_d.modify(Map(Case2(null) -> 1, Case6(null) -> 2), _ + 0.4))(
          equalTo(Map[Variant1, Int](Case2(null) -> 1, Case6(null) -> 2))
        ) &&
        assert(Variant2.c3_v1_v2_c4_lr3.modify(Case4(Nil), _ => null))(equalTo(Case4(Nil))) &&
        assert(Variant2.c4_lr3.modify(Case3(Case1(0.1)), _ => null))(equalTo(Case3(Case1(0.1))))
      },
      test("modifies collection values and wraps result to some") {
        assert(Collections.mkc.modifyOption(Map('a' -> "1", 'b' -> "2", 'c' -> "3"), _.toUpper))(
          isSome(equalTo(Map('A' -> "1", 'B' -> "2", 'C' -> "3")))
        ) &&
        assert(Collections.mvs.modifyOption(Map('a' -> "1", 'b' -> "2", 'c' -> "3"), _ + "x"))(
          isSome(equalTo(Map('a' -> "1x", 'b' -> "2x", 'c' -> "3x")))
        ) &&
        assert(Collections.lc1.modifyOption(List(Case1(0.1)), _.copy(d = 0.2)))(isSome(equalTo(List(Case1(0.2))))) &&
        assert(Collections.lc1_d.modifyOption(List(Case1(0.1)), _ + 0.4))(isSome(equalTo(List(Case1(0.5))))) &&
        assert(Collections.mkv1_c1_d.modifyOption(Map(Case1(0.1) -> 1), _ + 0.4))(
          isSome(equalTo(Map[Variant1, Int](Case1(0.5) -> 1)))
        ) &&
        assert(Collections.mvv1_c1_d.modifyOption(Map(1 -> Case1(0.1)), _ + 0.4))(
          isSome(equalTo(Map(1 -> Case1(0.5))))
        ) &&
        assert(
          Collections.lc4_lr3
            .modifyOption(List(Case4(List(Record3(null, null, null)))), _.copy(r1 = Record1(true, 0.1f)))
        )(
          isSome(equalTo(List(Case4(List(Record3(Record1(true, 0.1f), null, null))))))
        ) &&
        assert(Collections.lr1.modifyOption(List(Record1(true, 0.1f)), x => !x))(
          isSome(equalTo(List(Record1(false, 0.1f))))
        ) &&
        assert(Record2.vi.modifyOption(Record2(2L, Vector(1, 2, 3), null), _ + 1))(
          isSome(equalTo(Record2(2L, Vector(2, 3, 4), null)))
        ) &&
        assert(Variant2.c4_lr3.modifyOption(Case4(List(Record3(null, null, null))), _ => null))(
          isSome(equalTo(Case4(List(null))))
        ) &&
        assert(Variant2.c3_v1_v2_c4_lr3.modifyOption(Case3(Case4(List(Record3(null, null, null)))), _ => null))(
          isSome(equalTo(Case3(Case4(List(null)))))
        )
      },
      test("doesn't modify collection values for non-matching cases and returns none") {
        assert(Collections.mkv1_c1_d.modifyOption(Map(Case2(null) -> 1, Case6(null) -> 2), _ + 0.4))(isNone) &&
        assert(Variant2.c3_v1_v2_c4_lr3.modifyOption(Case4(Nil), _ => null))(isNone) &&
        assert(Variant2.c4_lr3.modifyOption(Case3(Case1(0.1)), _ => null))(isNone)
      },
      test("modifies collection values and wraps result to right") {
        assert(Collections.mkc.modifyOrFail(Map('a' -> "1", 'b' -> "2", 'c' -> "3"), _.toUpper))(
          isRight(equalTo(Map('A' -> "1", 'B' -> "2", 'C' -> "3")))
        ) &&
        assert(Collections.mvs.modifyOrFail(Map('a' -> "1", 'b' -> "2", 'c' -> "3"), _ + "x"))(
          isRight(equalTo(Map('a' -> "1x", 'b' -> "2x", 'c' -> "3x")))
        ) &&
        assert(Collections.lc1.modifyOrFail(List(Case1(0.1)), _.copy(d = 0.2)))(isRight(equalTo(List(Case1(0.2))))) &&
        assert(Collections.lc1_d.modifyOrFail(List(Case1(0.1)), _ + 0.4))(isRight(equalTo(List(Case1(0.5))))) &&
        assert(Collections.mkv1_c1_d.modifyOrFail(Map(Case1(0.1) -> 1), _ + 0.4))(
          isRight(equalTo(Map[Variant1, Int](Case1(0.5) -> 1)))
        ) &&
        assert(Collections.mvv1_c1_d.modifyOrFail(Map(1 -> Case1(0.1)), _ + 0.4))(
          isRight(equalTo(Map(1 -> Case1(0.5))))
        ) &&
        assert(
          Collections.lc4_lr3
            .modifyOrFail(List(Case4(List(Record3(null, null, null)))), _.copy(r1 = Record1(true, 0.1f)))
        )(
          isRight(equalTo(List(Case4(List(Record3(Record1(true, 0.1f), null, null))))))
        ) &&
        assert(Collections.lr1.modifyOrFail(List(Record1(true, 0.1f)), x => !x))(
          isRight(equalTo(List(Record1(false, 0.1f))))
        ) &&
        assert(Record2.vi.modifyOrFail(Record2(2L, Vector(1, 2, 3), null), _ + 1))(
          isRight(equalTo(Record2(2L, Vector(2, 3, 4), null)))
        ) &&
        assert(Variant2.c4_lr3.modifyOrFail(Case4(List(Record3(null, null, null))), _ => null))(
          isRight(equalTo(Case4(List(null))))
        ) &&
        assert(Variant2.c3_v1_v2_c4_lr3.modifyOrFail(Case3(Case4(List(Record3(null, null, null)))), _ => null))(
          isRight(equalTo(Case3(Case4(List(null)))))
        )
      },
      test("doesn't modify collection values for non-matching cases and returns an error") {
        assert(Variant2.c3_v1_v2_c4_lr3.modifyOrFail(Case3(Case4(Nil)), _ => null))(
          isLeft(
            equalTo(
              OpticCheck(
                errors = ::(
                  EmptySequence(
                    full = DynamicOptic(
                      Vector(Case("Case3"), Field("v1"), Case("Variant2"), Case("Case4"), Field("lr3"), Elements)
                    ),
                    prefix = DynamicOptic(
                      Vector(Case("Case3"), Field("v1"), Case("Variant2"), Case("Case4"), Field("lr3"), Elements)
                    ),
                    actualValue = Nil
                  ),
                  Nil
                )
              )
            )
          )
        ) &&
        assert(Variant2.c3_v1_v2_c4_lr3.modifyOrFail(Case4(Nil), _ => null))(
          isLeft(
            equalTo(
              OpticCheck(
                errors = ::(
                  UnexpectedCase(
                    expectedCase = "Case3",
                    actualCase = "Case4",
                    full = DynamicOptic(
                      Vector(Case("Case3"), Field("v1"), Case("Variant2"), Case("Case4"), Field("lr3"), Elements)
                    ),
                    prefix = DynamicOptic(Vector(Case("Case3"))),
                    actualValue = Case4(Nil)
                  ),
                  Nil
                )
              )
            )
          )
        ) &&
        assert(Variant2.c4_lr3.modifyOrFail(Case3(Case1(0.1)), _ => null))(
          isLeft(
            equalTo(
              OpticCheck(
                errors = ::(
                  UnexpectedCase(
                    expectedCase = "Case4",
                    actualCase = "Case3",
                    full = DynamicOptic(Vector(Case("Case4"), Field("lr3"), Elements)),
                    prefix = DynamicOptic(Vector(Case("Case4"))),
                    actualValue = Case3(Case1(0.1))
                  ),
                  Nil
                )
              )
            )
          )
        )
      },
      test("folds collection values") {
        assert(Collections.abl.fold[Int](Array(true, false, true))(0, (z, x) => if (x) z + 1 else z))(equalTo(2)) &&
        assert(Collections.ab.fold[Int](Array(1: Byte, 2: Byte, 3: Byte))(0, _ + _))(equalTo(6)) &&
        assert(Collections.ash.fold[Int](Array(1: Short, 2: Short, 3: Short))(0, _ + _))(equalTo(6)) &&
        assert(Collections.ai.fold[Int](Array(1, 2, 3))(0, _ + _))(equalTo(6)) &&
        assert(Collections.ai.fold[Long](Array(1, 2, 3))(0L, _ + _))(equalTo(6L)) &&
        assert(Collections.ai.fold[Double](Array(1, 2, 3))(0.0, _ + _))(equalTo(6.0)) &&
        assert(Collections.ai.fold[String](Array(1, 2, 3))("", _ + _))(equalTo("123")) &&
        assert(Collections.al.fold[Int](Array(1L, 2L, 3L))(0, _ + _.toInt))(equalTo(6)) &&
        assert(Collections.al.fold[Long](Array(1L, 2L, 3L))(0L, _ + _))(equalTo(6L)) &&
        assert(Collections.al.fold[Double](Array(1L, 2L, 3L))(0.0, _ + _))(equalTo(6.0)) &&
        assert(Collections.al.fold[String](Array(1L, 2L, 3L))("", _ + _))(equalTo("123")) &&
        assert(Collections.ad.fold[Int](Array(1.0, 2.0, 3.0))(0, _ + _.toInt))(equalTo(6)) &&
        assert(Collections.ad.fold[Long](Array(1.0, 2.0, 3.0))(0L, _ + _.toLong))(equalTo(6L)) &&
        assert(Collections.ad.fold[Double](Array(1.0, 2.0, 3.0))(0.0, _ + _))(equalTo(6.0)) &&
        assert(Collections.ad.fold[String](Array(1.0, 2.0, 3.0))("", _ + _.toInt))(equalTo("123")) &&
        assert(Collections.af.fold[Int](Array(1.0f, 2.0f, 3.0f))(0, _ + _.toInt))(equalTo(6)) &&
        assert(Collections.ac.fold[String](Array('a', 'b', 'c'))("", _ + _))(equalTo("abc")) &&
        assert(Collections.as.fold[String](Array("1", "2", "3"))("", _ + _))(equalTo("123")) &&
        assert(Collections.mkc.fold[String](Map('a' -> "1", 'b' -> "2", 'c' -> "3"))("", _ + _.toString))(
          equalTo("abc")
        ) &&
        assert(Collections.mvs.fold[String](Map('a' -> "1", 'b' -> "2", 'c' -> "3"))("", _ + _))(equalTo("123")) &&
        assert(Collections.lc1.fold[String](List(Case1(0.1)))("", _ + _.toString))(equalTo("Case1(0.1)")) &&
        assert(Collections.lc1_d.fold[Double](List(Case1(0.1), Case1(0.4)))(0.0, _ + _))(equalTo(0.5)) &&
        assert(Collections.mkv1_c1_d.fold[Double](Map(Case1(0.1) -> 1))(0.0, _ + _))(equalTo(0.1)) &&
        assert(Collections.mvv1_c1_d.fold[Double](Map(1 -> Case1(0.1)))(0.0, _ + _))(equalTo(0.1)) &&
        assert(Collections.lc4_lr3.fold[String](List(Case4(List(Record3(null, null, null)))))("", _ + _.toString))(
          equalTo("Record3(null,null,null)")
        ) &&
        assert(Collections.lr1.fold[Boolean](List(Record1(true, 0.1f)))(false, _ | _))(equalTo(true)) &&
        assert(Record2.vi.fold[Int](Record2(2L, Vector(1, 2, 3), null))(0, _ + _))(equalTo(6)) &&
        assert(Variant2.c4_lr3.fold[Record3](Case4(List(Record3(null, null, null))))(null, (_, x) => x))(
          equalTo(Record3(null, null, null))
        ) &&
        assert(
          Variant2.c3_v1_v2_c4_lr3.fold[Record3](Case3(Case4(List(Record3(null, null, null)))))(null, (_, x) => x)
        )(equalTo(Record3(null, null, null)))
      },
      test("folds zero values for non-matching cases") {
        assert(Collections.mkv1_c1_d.fold[Double](Map(Case2(null) -> 1, Case6(null) -> 2))(0.0, _ + _))(equalTo(0.0)) &&
        assert(Variant2.c3_v1_v2_c4_lr3.fold[Record3](Case4(Nil))(null, (_, x) => x))(equalTo(null)) &&
        assert(Variant2.c4_lr3.fold[Record3](Case3(Case1(0.1)))(null, (_, x) => x))(equalTo(null))
      },
      test("reduses collection values and wraps the result to right") {
        assert(Collections.abl.reduceOrFail(Array(true, false, true))((x, y) => x | y))(isRight(equalTo(true))) &&
        assert(Collections.ai.reduceOrFail(Array(1))(_ + _))(isRight(equalTo(1))) &&
        assert(Collections.ai.reduceOrFail(Array(1, 2, 3))(_ + _))(isRight(equalTo(6))) &&
        assert(Collections.al.reduceOrFail(Array(1L, 2L, 3L))(_ + _))(isRight(equalTo(6L))) &&
        assert(Collections.ad.reduceOrFail(Array(1.0, 2.0, 3.0))(_ + _))(isRight(equalTo(6.0))) &&
        assert(Collections.as.reduceOrFail(Array("1", "2", "3"))(_ + _))(isRight(equalTo("123"))) &&
        assert(Collections.mvs.reduceOrFail(Map('a' -> "1", 'b' -> "2", 'c' -> "3"))(_ + _))(isRight(equalTo("123"))) &&
        assert(Collections.lc1_d.reduceOrFail(List(Case1(0.1), Case1(0.4)))(_ + _))(isRight(equalTo(0.5)))
      },
      test("doesn't reduce for non-matching cases returnung an error") {
        assert(Variant2.c3_v1_v2_c4_lr3.reduceOrFail(Case4(Nil))((_, x) => x))(
          isLeft(
            equalTo(
              OpticCheck(
                errors = ::(
                  UnexpectedCase(
                    expectedCase = "Case3",
                    actualCase = "Case4",
                    full = DynamicOptic(
                      Vector(Case("Case3"), Field("v1"), Case("Variant2"), Case("Case4"), Field("lr3"), Elements)
                    ),
                    prefix = DynamicOptic(Vector(Case("Case3"))),
                    actualValue = Case4(Nil)
                  ),
                  Nil
                )
              )
            )
          )
        ) &&
        assert(Variant2.c4_lr3.reduceOrFail(Case3(Case1(0.1)))((_, x) => x))(
          isLeft(
            equalTo(
              OpticCheck(
                errors = ::(
                  UnexpectedCase(
                    expectedCase = "Case4",
                    actualCase = "Case3",
                    full = DynamicOptic(Vector(Case("Case4"), Field("lr3"), Elements)),
                    prefix = DynamicOptic(Vector(Case("Case4"))),
                    actualValue = Case3(Case1(0.1))
                  ),
                  Nil
                )
              )
            )
          )
        )
      }
    )
  )
}

object OpticSpecTypes {

  case class Record1(b: Boolean, f: Float)

  object Record1 extends CompanionOptics[Record1] {
    implicit val schema: Schema[Record1]       = Schema.derived
    val reflect: Reflect.Record.Bound[Record1] = schema.reflect.asInstanceOf[Reflect.Record.Bound[Record1]]
    val b: Lens[Record1, Boolean]              = field(_.b)
    val f: Lens[Record1, Float]                = field(_.f)
  }

  case class Record2(l: Long, vi: Vector[Int], r1: Record1)

  object Record2 extends CompanionOptics[Record2] {
    implicit val schema: Schema[Record2]       = Schema.derived
    val reflect: Reflect.Record.Bound[Record2] = schema.reflect.asInstanceOf[Reflect.Record.Bound[Record2]]
    val l: Lens[Record2, Long]                 = field(_.l)
    val vi: Traversal[Record2, Int]            = field(_.vi).vectorValues
    val r1: Lens[Record2, Record1]             = field(_.r1)
    lazy val r1_b: Lens[Record2, Boolean]      = r1(Record1.b)
    lazy val r1_f: Lens[Record2, Float]        = r1(Record1.f)
  }

  case class Record3(r1: Record1, r2: Record2, @Modifier.deferred v1: Variant1)

  object Record3 extends CompanionOptics[Record3] {
    implicit val schema: Schema[Record3]           = Schema.derived
    val reflect: Reflect.Record.Bound[Record3]     = schema.reflect.asInstanceOf[Reflect.Record.Bound[Record3]]
    val r1: Lens[Record3, Record1]                 = field(_.r1)
    val r2: Lens[Record3, Record2]                 = field(_.r2)
    val v1: Lens[Record3, Variant1]                = field(_.v1)
    lazy val r2_r1_b_left: Lens[Record3, Boolean]  = r2(Record2.r1)(Record1.b)
    lazy val r2_r1_b_right: Lens[Record3, Boolean] = r2(Record2.r1(Record1.b))
    lazy val v1_c1: Optional[Record3, Case1]       = v1(Variant1.c1)
  }

  sealed trait Variant1

  object Variant1 extends CompanionOptics[Variant1] {
    implicit val schema: Schema[Variant1]                     = Schema.derived
    val reflect: Reflect.Variant.Bound[Variant1]              = schema.reflect.asInstanceOf[Reflect.Variant.Bound[Variant1]]
    val c1: Prism[Variant1, Case1]                            = caseOf
    val c2: Prism[Variant1, Case2]                            = caseOf
    val v2: Prism[Variant1, Variant2]                         = caseOf
    lazy val v2_c3: Prism[Variant1, Case3]                    = v2(Variant2.c3)
    lazy val v2_c4: Prism[Variant1, Case4]                    = v2(Variant2.c4)
    lazy val v2_v3_c5_left: Prism[Variant1, Case5]            = v2(Variant2.v3)(Variant3.c5)
    lazy val v2_v3_c5_right: Prism[Variant1, Case5]           = v2(Variant2.v3(Variant3.c5))
    lazy val v2_c4_lr3: Traversal[Variant1, Record3]          = v2(Variant2.c4(Case4.lr3))
    lazy val v2_c3_v1: Optional[Variant1, Variant1]           = v2(Variant2.c3_v1)
    lazy val c1_d: Optional[Variant1, Double]                 = c1(Case1.d)
    lazy val c2_r3: Optional[Variant1, Record3]               = c2(Case2.r3)
    lazy val c2_r3_r1: Optional[Variant1, Record1]            = c2_r3(Record3.r1)
    lazy val c2_r3_r2_r1_b_left: Optional[Variant1, Boolean]  = c2_r3(Record3.r2_r1_b_left)
    lazy val c2_r3_r2_r1_b_right: Optional[Variant1, Boolean] = c2_r3(Record3.r2_r1_b_right)
    lazy val c2_r3_v1_c1: Optional[Variant1, Case1]           = c2_r3(Record3.v1_c1)
  }

  case class Case1(d: Double) extends Variant1

  object Case1 extends CompanionOptics[Case1] {
    implicit val schema: Schema[Case1]       = Schema.derived
    val reflect: Reflect.Record.Bound[Case1] = schema.reflect.asInstanceOf[Reflect.Record.Bound[Case1]]
    val d: Lens[Case1, Double]               = field(_.d)
  }

  case class Case2(r3: Record3) extends Variant1

  object Case2 extends CompanionOptics[Case2] {
    implicit val schema: Schema[Case2]        = Schema.derived
    val reflect: Reflect.Record.Bound[Case2]  = schema.reflect.asInstanceOf[Reflect.Record.Bound[Case2]]
    val r3: Lens[Case2, Record3]              = field(_.r3)
    lazy val r3_v1_c1: Optional[Case2, Case1] = r3(Record3.v1_c1)
  }

  sealed trait Variant2 extends Variant1

  object Variant2 extends CompanionOptics[Variant2] {
    implicit val schema: Schema[Variant2]                  = Schema.derived
    val reflect: Reflect.Variant.Bound[Variant2]           = schema.reflect.asInstanceOf[Reflect.Variant.Bound[Variant2]]
    val c3: Prism[Variant2, Case3]                         = caseOf
    val c4: Prism[Variant2, Case4]                         = caseOf
    val v3: Prism[Variant2, Variant3]                      = caseOf
    lazy val c3_v1: Optional[Variant2, Variant1]           = c3(Case3.v1)
    lazy val c3_v1_c1_left: Optional[Variant2, Case1]      = c3(Case3.v1)(Variant1.c1)
    lazy val c3_v1_c1_right: Optional[Variant2, Case1]     = c3(Case3.v1_c1)
    lazy val c3_v1_c1_d_left: Optional[Variant2, Double]   = c3(Case3.v1)(Variant1.c1_d)
    lazy val c3_v1_c1_d_right: Optional[Variant2, Double]  = c3_v1(Variant1.c1_d)
    lazy val c3_v1_v2: Optional[Variant2, Variant2]        = c3(Case3.v1)(Variant1.v2)
    lazy val c4_lr3: Traversal[Variant2, Record3]          = c4(Case4.lr3)
    lazy val c3_v1_v2_c4_lr3: Traversal[Variant2, Record3] = c3_v1_v2(c4_lr3)
    lazy val c3_v1_v2_c4: Optional[Variant2, Case4]        = c3_v1_v2(c4)
  }

  case class Case3(@Modifier.deferred v1: Variant1) extends Variant2

  object Case3 extends CompanionOptics[Case3] {
    implicit val schema: Schema[Case3]                 = Schema.derived
    val reflect: Reflect.Record.Bound[Case3]           = schema.reflect.asInstanceOf[Reflect.Record.Bound[Case3]]
    val v1: Lens[Case3, Variant1]                      = field(_.v1)
    lazy val v1_c1: Optional[Case3, Case1]             = v1(Variant1.c1)
    lazy val v1_c1_d_left: Optional[Case3, Double]     = v1(Variant1.c1)(Case1.d)
    lazy val v1_c1_d_right: Optional[Case3, Double]    = v1(Variant1.c1_d)
    lazy val v1_v2: Optional[Case3, Variant2]          = v1(Variant1.v2)
    lazy val v1_v2_c3_v1_v2: Optional[Case3, Variant2] = v1_v2(Variant2.c3(Case3.v1_v2))
  }

  case class Case4(lr3: List[Record3]) extends Variant2

  object Case4 extends CompanionOptics[Case4] {
    implicit val schema: Schema[Case4]            = Schema.derived
    val reflect: Reflect.Record.Bound[Case4]      = schema.reflect.asInstanceOf[Reflect.Record.Bound[Case4]]
    val lr3: Traversal[Case4, Record3]            = field(_.lr3).listValues
    lazy val lr3_r2: Traversal[Case4, Record2]    = lr3(Record3.r2)
    lazy val lr3_r2_r1: Traversal[Case4, Record1] = lr3_r2(Record2.r1)
  }

  sealed trait Variant3 extends Variant2

  object Variant3 extends CompanionOptics[Variant3] {
    implicit val schema: Schema[Variant3]        = Schema.derived
    val reflect: Reflect.Variant.Bound[Variant3] = schema.reflect.asInstanceOf[Reflect.Variant.Bound[Variant3]]
    val c5: Prism[Variant3, Case5]               = caseOf
  }

  case class Case5(si: Set[Int], as: Array[String]) extends Variant3

  object Case5 extends CompanionOptics[Case5] {
    implicit val schema: Schema[Case5]       = Schema.derived
    val reflect: Reflect.Record.Bound[Case5] = schema.reflect.asInstanceOf[Reflect.Record.Bound[Case5]]
    val si: Traversal[Case5, Int]            = field(_.si).setValues
    val as: Traversal[Case5, String]         = field(_.as).arrayValues
  }

  case class Case6(@Modifier.deferred v2: Variant2) extends Variant3

  object Case6 {
    implicit val schema: Schema[Case6]       = Schema.derived
    val reflect: Reflect.Record.Bound[Case6] = schema.reflect.asInstanceOf[Reflect.Record.Bound[Case6]]
  }

  object Collections {
    val lb: Traversal[List[Byte], Byte]         = Traversal.listValues(Reflect.byte)
    val vs: Traversal[Vector[Short], Short]     = Traversal.vectorValues(Reflect.short)
    val abl: Traversal[Array[Boolean], Boolean] = Traversal.arrayValues(Reflect.boolean)
    val ab: Traversal[Array[Byte], Byte]        = Traversal.arrayValues(Reflect.byte)
    val ash: Traversal[Array[Short], Short]     = Traversal.arrayValues(Reflect.short)
    val ai: Traversal[Array[Int], Int]          = Traversal.arrayValues(Reflect.int)
    val al: Traversal[Array[Long], Long]        = Traversal.arrayValues(Reflect.long)
    val ad: Traversal[Array[Double], Double]    = Traversal.arrayValues(Reflect.double)
    val af: Traversal[Array[Float], Float]      = Traversal.arrayValues(Reflect.float)
    val ac: Traversal[Array[Char], Char]        = Traversal.arrayValues(Reflect.char)
    val as: Traversal[Array[String], String]    = Traversal.arrayValues(Reflect.string)
    val sf: Traversal[Set[Float], Float]        = Traversal.setValues(Reflect.float)
    val mkc: Traversal[Predef.Map[Char, String], Char] =
      Traversal.mapKeys(Reflect.map(Reflect.char, Reflect.string))
    val mvs: Traversal[Predef.Map[Char, String], String] =
      Traversal.mapValues(Reflect.map(Reflect.char, Reflect.string))
    lazy val lr1: Traversal[List[Record1], Boolean] = Traversal.listValues(Record1.reflect).apply(Record1.b)
    lazy val lc1: Traversal[List[Variant1], Case1]  = Traversal.listValues(Variant1.reflect).apply(Variant1.c1)
    lazy val lc1_d: Traversal[List[Variant1], Double] =
      Traversal.listValues(Variant1.reflect).apply(Variant1.c1_d)
    lazy val lc4_lr3: Traversal[List[Case4], Record3] = Traversal.listValues(Case4.reflect).apply(Case4.lr3)
    lazy val mkv1_c1_d: Traversal[Map[Variant1, Int], Double] =
      Traversal.mapKeys(Schema[Map[Variant1, Int]].reflect.asInstanceOf[Reflect.Map.Bound[Variant1, Int, Map]])(
        Variant1.c1
      )(Case1.d)
    lazy val mvv1_c1_d: Traversal[Map[Int, Variant1], Double] =
      Traversal.mapValues(Schema[Map[Int, Variant1]].reflect.asInstanceOf[Reflect.Map.Bound[Int, Variant1, Map]])(
        Variant1.c1
      )(Case1.d)
  }
}
