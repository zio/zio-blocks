package zio.blocks.schema

import zio.{Scope, ZIO}
import zio.blocks.schema.binding._
import zio.test.Assertion._
import zio.test._

object OpticSpec extends ZIOSpecDefault {
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
      test("refines a binding") {
        assert(Record1.b.transform(ReflectTransformer.noBinding()).force)(equalTo(Record1.b.noBinding)) &&
        assert(Record2.r1_b.transform(ReflectTransformer.noBinding()).force)(equalTo(Record2.r1_b.noBinding))
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
      test("refines a binding") {
        assert(Variant1.c1.transform(ReflectTransformer.noBinding()).force)(equalTo(Variant1.c1.noBinding)) &&
        assert(Variant1.v2_c3.transform(ReflectTransformer.noBinding()).force)(equalTo(Variant1.v2_c3.noBinding))
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
      test("refines a binding") {
        assert(Variant1.c1_d.transform(ReflectTransformer.noBinding()).force)(equalTo(Variant1.c1_d.noBinding)) &&
        assert(Variant1.c2_r3.transform(ReflectTransformer.noBinding()).force)(equalTo(Variant1.c2_r3.noBinding)) &&
        assert(Variant2.c3_v1_c1_left.transform(ReflectTransformer.noBinding()).force)(
          equalTo(Variant2.c3_v1_c1_left.noBinding)
        ) &&
        assert(Variant2.c3_v1_c1_right.transform(ReflectTransformer.noBinding()).force)(
          equalTo(Variant2.c3_v1_c1_right.noBinding)
        ) &&
        assert(Variant2.c3_v1_c1_d_left.transform(ReflectTransformer.noBinding()).force)(
          equalTo(Variant2.c3_v1_c1_d_left.noBinding)
        ) &&
        assert(Variant2.c3_v1_c1_d_right.transform(ReflectTransformer.noBinding()).force)(
          equalTo(Variant2.c3_v1_c1_d_right.noBinding)
        ) &&
        assert(Variant1.c2_r3_r1.transform(ReflectTransformer.noBinding()).force)(
          equalTo(Variant1.c2_r3_r1.noBinding)
        ) &&
        assert(Variant1.v2_c3_v1.transform(ReflectTransformer.noBinding()).force)(
          equalTo(Variant1.v2_c3_v1.noBinding)
        ) &&
        assert(Variant1.c2_r3_v1_c1.transform(ReflectTransformer.noBinding()).force)(
          equalTo(Variant1.c2_r3_v1_c1.noBinding)
        ) &&
        assert(Case2.r3_v1_c1.transform(ReflectTransformer.noBinding()).force)(equalTo(Case2.r3_v1_c1.noBinding)) &&
        assert(Case3.v1_c1_d_left.transform(ReflectTransformer.noBinding()).force)(
          equalTo(Case3.v1_c1_d_left.noBinding)
        ) &&
        assert(Case3.v1_c1_d_right.transform(ReflectTransformer.noBinding()).force)(
          equalTo(Case3.v1_c1_d_right.noBinding)
        ) &&
        assert(Variant2.c3_v1_v2_c4.transform(ReflectTransformer.noBinding()).force)(
          equalTo(Variant2.c3_v1_v2_c4.noBinding)
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
        assert(Variant1.c2_r3_r1.replaceOption(Case3(Case1(0.1)), Record1(false, 0.2f)))(equalTo(None)) &&
        assert(Case2.r3_v1_c1.replaceOption(Case2(Record3(null, null, Case4(Nil))), Case1(0.2)))(equalTo(None)) &&
        assert(Variant1.c2_r3_v1_c1.replaceOption(Case3(Case1(0.1)), Case1(0.2)))(equalTo(None)) &&
        assert(Variant2.c3_v1_v2_c4.replaceOption(Case3(Case1(0.1)), Case4(Nil)))(equalTo(None)) &&
        assert(Variant2.c3_v1_c1_left.replaceOption(Case4(Nil), Case1(0.2)))(equalTo(None)) &&
        assert(Variant2.c3_v1_c1_right.replaceOption(Case3(Case2(null)), Case1(0.2)))(equalTo(None)) &&
        assert(Variant2.c3_v1_c1_d_right.replaceOption(Case4(Nil), 0.2))(equalTo(None)) &&
        assert(Variant2.c3_v1.replaceOption(Case4(Nil), Case1(0.2)))(equalTo(None)) &&
        assert(Case3.v1_c1_d_left.replaceOption(Case3(Case4(Nil)), 0.2))(equalTo(None)) &&
        assert(Case3.v1_c1_d_right.replaceOption(Case3(Case4(Nil)), 0.2))(equalTo(None))
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
          .attempt(Traversal.arrayValues(null)(Binding.bindingFromBinding))
          .flip
          .map(e => assertTrue(e.isInstanceOf[IllegalArgumentException])) &&
        ZIO
          .attempt(Traversal.listValues(null)(Binding.bindingFromBinding))
          .flip
          .map(e => assertTrue(e.isInstanceOf[IllegalArgumentException])) &&
        ZIO
          .attempt(Traversal.setValues(null)(Binding.bindingFromBinding))
          .flip
          .map(e => assertTrue(e.isInstanceOf[IllegalArgumentException])) &&
        ZIO
          .attempt(Traversal.vectorValues(null)(Binding.bindingFromBinding))
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
      test("refines a binding") {
        assert(Collections.ai.transform(ReflectTransformer.noBinding()).force)(equalTo(Collections.ai.noBinding)) &&
        assert(Collections.mkc.transform(ReflectTransformer.noBinding()).force)(equalTo(Collections.mkc.noBinding)) &&
        assert(Collections.mvs.transform(ReflectTransformer.noBinding()).force)(equalTo(Collections.mvs.noBinding)) &&
        assert(Collections.lc1.transform(ReflectTransformer.noBinding()).force)(equalTo(Collections.lc1.noBinding)) &&
        assert(Collections.lc1_d.transform(ReflectTransformer.noBinding()).force)(
          equalTo(Collections.lc1_d.noBinding)
        ) &&
        assert(Collections.lc4_lr3.transform(ReflectTransformer.noBinding()).force)(
          equalTo(Collections.lc4_lr3.noBinding)
        ) &&
        assert(Collections.lr1.transform(ReflectTransformer.noBinding()).force)(equalTo(Collections.lr1.noBinding)) &&
        assert(Record2.vi.transform(ReflectTransformer.noBinding()).force)(equalTo(Record2.vi.noBinding)) &&
        assert(Case4.lr3.transform(ReflectTransformer.noBinding()).force)(equalTo(Case4.lr3.noBinding)) &&
        assert(Case4.lr3_r2_r1.transform(ReflectTransformer.noBinding()).force)(equalTo(Case4.lr3_r2_r1.noBinding)) &&
        assert(Variant2.c4_lr3.transform(ReflectTransformer.noBinding()).force)(equalTo(Variant2.c4_lr3.noBinding)) &&
        assert(Variant2.c3_v1_v2_c4_lr3.transform(ReflectTransformer.noBinding()).force)(
          equalTo(Variant2.c3_v1_v2_c4_lr3.noBinding)
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
        assert(Variant2.c3_v1_v2_c4_lr3.modify(Case4(Nil), _ => null))(equalTo(Case4(Nil))) &&
        assert(Variant2.c4_lr3.modify(Case3(Case1(0.1)), _ => null))(equalTo(Case3(Case1(0.1))))
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
        assert(Variant2.c3_v1_v2_c4_lr3.fold[Record3](Case4(Nil))(null, (_, x) => x))(equalTo(null)) &&
        assert(Variant2.c4_lr3.fold[Record3](Case3(Case1(0.1)))(null, (_, x) => x))(equalTo(null))
      }
    )
  )

  case class Record1(b: Boolean, f: Float)

  object Record1 {
    implicit val schema: Schema[Record1]       = Schema.derived
    val reflect: Reflect.Record.Bound[Record1] = schema.reflect.asInstanceOf[Reflect.Record.Bound[Record1]]
    val b: Lens.Bound[Record1, Boolean] =
      Lens(reflect, reflect.fields(0).asInstanceOf[Term.Bound[Record1, Boolean]])
    val f: Lens.Bound[Record1, Float] = Lens(reflect, reflect.fields(1).asInstanceOf[Term.Bound[Record1, Float]])
  }

  case class Record2(l: Long, vi: Vector[Int], r1: Record1)

  object Record2 {
    implicit val schema: Schema[Record2]       = Schema.derived
    val reflect: Reflect.Record.Bound[Record2] = schema.reflect.asInstanceOf[Reflect.Record.Bound[Record2]]
    val l: Lens.Bound[Record2, Long]           = Lens(reflect, reflect.fields(0).asInstanceOf[Term.Bound[Record2, Long]])
    val vi: Traversal.Bound[Record2, Int] =
      Lens(reflect, reflect.fields(1).asInstanceOf[Term.Bound[Record2, Vector[Int]]]).vectorValues
    val r1: Lens.Bound[Record2, Record1] =
      Lens(reflect, reflect.fields(2).asInstanceOf[Term.Bound[Record2, Record1]])
    lazy val r1_b: Lens.Bound[Record2, Boolean] = r1(Record1.b)
    lazy val r1_f: Lens.Bound[Record2, Float]   = r1(Record1.f)
  }

  case class Record3(r1: Record1, r2: Record2, @Modifier.deferred v1: Variant1)

  object Record3 {
    implicit val schema: Schema[Record3]       = Schema.derived
    val reflect: Reflect.Record.Bound[Record3] = schema.reflect.asInstanceOf[Reflect.Record.Bound[Record3]]
    val r1: Lens.Bound[Record3, Record1] =
      Lens(reflect, reflect.fields(0).asInstanceOf[Term.Bound[Record3, Record1]])
    val r2: Lens.Bound[Record3, Record2] =
      Lens(reflect, reflect.fields(1).asInstanceOf[Term.Bound[Record3, Record2]])
    val v1: Lens.Bound[Record3, Variant1] =
      Lens(reflect, reflect.fields(2).asInstanceOf[Term.Bound[Record3, Variant1]])
    lazy val r2_r1_b_left: Lens.Bound[Record3, Boolean]  = r2(Record2.r1)(Record1.b)
    lazy val r2_r1_b_right: Lens.Bound[Record3, Boolean] = r2(Record2.r1(Record1.b))
    lazy val v1_c1: Optional.Bound[Record3, Case1]       = v1(Variant1.c1)
  }

  sealed trait Variant1

  object Variant1 {
    implicit val schema: Schema[zio.blocks.schema.OpticSpec.Variant1] = Schema.derived
    val reflect: Reflect.Variant.Bound[Variant1]                      = schema.reflect.asInstanceOf[Reflect.Variant.Bound[Variant1]]
    val c1: Prism.Bound[Variant1, Case1] =
      Prism(reflect, reflect.cases(0).asInstanceOf[Term.Bound[Variant1, Case1]])
    val c2: Prism.Bound[Variant1, Case2] =
      Prism(reflect, reflect.cases(1).asInstanceOf[Term.Bound[Variant1, Case2]])
    val v2: Prism.Bound[Variant1, Variant2] =
      Prism(reflect, reflect.cases(2).asInstanceOf[Term.Bound[Variant1, Variant2]])
    lazy val v2_c3: Prism.Bound[Variant1, Case3]                    = v2(Variant2.c3)
    lazy val v2_c4: Prism.Bound[Variant1, Case4]                    = v2(Variant2.c4)
    lazy val v2_v3_c5_left: Prism.Bound[Variant1, Case5]            = v2(Variant2.v3)(Variant3.c5)
    lazy val v2_v3_c5_right: Prism.Bound[Variant1, Case5]           = v2(Variant2.v3(Variant3.c5))
    lazy val v2_c4_lr3: Traversal.Bound[Variant1, Record3]          = v2(Variant2.c4(Case4.lr3))
    lazy val v2_c3_v1: Optional.Bound[Variant1, Variant1]           = v2(Variant2.c3_v1)
    lazy val c1_d: Optional.Bound[Variant1, Double]                 = c1(Case1.d)
    lazy val c2_r3: Optional.Bound[Variant1, Record3]               = c2(Case2.r3)
    lazy val c2_r3_r1: Optional.Bound[Variant1, Record1]            = c2_r3(Record3.r1)
    lazy val c2_r3_r2_r1_b_left: Optional.Bound[Variant1, Boolean]  = c2_r3(Record3.r2_r1_b_left)
    lazy val c2_r3_r2_r1_b_right: Optional.Bound[Variant1, Boolean] = c2_r3(Record3.r2_r1_b_right)
    lazy val c2_r3_v1_c1: Optional.Bound[Variant1, Case1]           = c2_r3(Record3.v1_c1)
  }

  case class Case1(d: Double) extends Variant1

  object Case1 {
    implicit val schema: Schema[Case1]       = Schema.derived
    val reflect: Reflect.Record.Bound[Case1] = schema.reflect.asInstanceOf[Reflect.Record.Bound[Case1]]
    val d: Lens.Bound[Case1, Double]         = Lens(reflect, reflect.fields(0).asInstanceOf[Term.Bound[Case1, Double]])
  }

  case class Case2(r3: Record3) extends Variant1

  object Case2 {
    implicit val schema: Schema[Case2]              = Schema.derived
    val reflect: Reflect.Record.Bound[Case2]        = schema.reflect.asInstanceOf[Reflect.Record.Bound[Case2]]
    val r3: Lens.Bound[Case2, Record3]              = Lens(reflect, reflect.fields(0).asInstanceOf[Term.Bound[Case2, Record3]])
    lazy val r3_v1_c1: Optional.Bound[Case2, Case1] = r3(Record3.v1_c1)
  }

  sealed trait Variant2 extends Variant1

  object Variant2 {
    implicit val schema: Schema[Variant2]        = Schema.derived
    val reflect: Reflect.Variant.Bound[Variant2] = schema.reflect.asInstanceOf[Reflect.Variant.Bound[Variant2]]
    val c3: Prism.Bound[Variant2, Case3] =
      Prism(reflect, reflect.cases(0).asInstanceOf[Term.Bound[Variant2, Case3]])
    val c4: Prism.Bound[Variant2, Case4] =
      Prism(reflect, reflect.cases(1).asInstanceOf[Term.Bound[Variant2, Case4]])
    val v3: Prism.Bound[Variant2, Variant3] =
      Prism(reflect, reflect.cases(2).asInstanceOf[Term.Bound[Variant2, Variant3]])
    lazy val c3_v1: Optional.Bound[Variant2, Variant1]           = c3(Case3.v1)
    lazy val c3_v1_c1_left: Optional.Bound[Variant2, Case1]      = c3(Case3.v1)(Variant1.c1)
    lazy val c3_v1_c1_right: Optional.Bound[Variant2, Case1]     = c3(Case3.v1_c1)
    lazy val c3_v1_c1_d_left: Optional.Bound[Variant2, Double]   = c3(Case3.v1)(Variant1.c1_d)
    lazy val c3_v1_c1_d_right: Optional.Bound[Variant2, Double]  = c3_v1(Variant1.c1_d)
    lazy val c3_v1_v2: Optional.Bound[Variant2, Variant2]        = c3(Case3.v1)(Variant1.v2)
    lazy val c4_lr3: Traversal.Bound[Variant2, Record3]          = c4(Case4.lr3)
    lazy val c3_v1_v2_c4_lr3: Traversal.Bound[Variant2, Record3] = c3_v1_v2(c4_lr3)
    lazy val c3_v1_v2_c4: Optional.Bound[Variant2, Case4]        = c3_v1_v2(c4)
  }

  case class Case3(@Modifier.deferred v1: Variant1) extends Variant2

  object Case3 {
    implicit val schema: Schema[Case3]       = Schema.derived
    val reflect: Reflect.Record.Bound[Case3] = schema.reflect.asInstanceOf[Reflect.Record.Bound[Case3]]
    val v1: Lens.Bound[Case3, Variant1] =
      Lens(reflect, reflect.fields(0).asInstanceOf[Term.Bound[Case3, Variant1]])
    lazy val v1_c1: Optional.Bound[Case3, Case1]             = v1(Variant1.c1)
    lazy val v1_c1_d_left: Optional.Bound[Case3, Double]     = v1(Variant1.c1)(Case1.d)
    lazy val v1_c1_d_right: Optional.Bound[Case3, Double]    = v1(Variant1.c1_d)
    lazy val v1_v2: Optional.Bound[Case3, Variant2]          = v1(Variant1.v2)
    lazy val v1_v2_c3_v1_v2: Optional.Bound[Case3, Variant2] = v1_v2(Variant2.c3(Case3.v1_v2))
  }

  case class Case4(lr3: List[Record3]) extends Variant2

  object Case4 {
    implicit val schema: Schema[Case4]       = Schema.derived
    val reflect: Reflect.Record.Bound[Case4] = schema.reflect.asInstanceOf[Reflect.Record.Bound[Case4]]
    val lr3: Traversal.Bound[Case4, Record3] =
      Lens(reflect, reflect.fields(0).asInstanceOf[Term.Bound[Case4, List[Record3]]]).listValues
    lazy val lr3_r2: Traversal.Bound[Case4, Record2]    = lr3(Record3.r2)
    lazy val lr3_r2_r1: Traversal.Bound[Case4, Record1] = lr3_r2(Record2.r1)
  }

  sealed trait Variant3 extends Variant2

  object Variant3 {
    implicit val schema: Schema[Variant3]        = Schema.derived
    val reflect: Reflect.Variant.Bound[Variant3] = schema.reflect.asInstanceOf[Reflect.Variant.Bound[Variant3]]
    val c5: Prism.Bound[Variant3, Case5] =
      Prism(reflect, reflect.cases(0).asInstanceOf[Term.Bound[Variant3, Case5]])
  }

  case class Case5(si: Set[Int], as: Array[String]) extends Variant3

  object Case5 {
    implicit val schema: Schema[Case5]       = Schema.derived
    val reflect: Reflect.Record.Bound[Case5] = schema.reflect.asInstanceOf[Reflect.Record.Bound[Case5]]
    val si: Traversal.Bound[Case5, Int] =
      Lens(reflect, reflect.fields(0).asInstanceOf[Term.Bound[Case5, Set[Int]]]).setValues
    val as: Traversal.Bound[Case5, String] =
      Lens(reflect, reflect.fields(1).asInstanceOf[Term.Bound[Case5, Array[String]]]).arrayValues
  }

  case class Case6(@Modifier.deferred v2: Variant2) extends Variant3

  object Case6 {
    implicit val schema: Schema[Case6]       = Schema.derived
    val reflect: Reflect.Record.Bound[Case6] = schema.reflect.asInstanceOf[Reflect.Record.Bound[Case6]]
  }

  object Collections {
    val lb: Traversal.Bound[List[Byte], Byte]         = Reflect.list(Reflect.byte[Binding]).values
    val vs: Traversal.Bound[Vector[Short], Short]     = Traversal.vectorValues(Reflect.short)
    val abl: Traversal.Bound[Array[Boolean], Boolean] = Traversal.arrayValues(Reflect.boolean)
    val ab: Traversal.Bound[Array[Byte], Byte]        = Traversal.arrayValues(Reflect.byte)
    val ash: Traversal.Bound[Array[Short], Short]     = Traversal.arrayValues(Reflect.short)
    val ai: Traversal.Bound[Array[Int], Int]          = Traversal.arrayValues(Reflect.int)
    val al: Traversal.Bound[Array[Long], Long]        = Traversal.arrayValues(Reflect.long)
    val ad: Traversal.Bound[Array[Double], Double]    = Traversal.arrayValues(Reflect.double)
    val af: Traversal.Bound[Array[Float], Float]      = Traversal.arrayValues(Reflect.float)
    val ac: Traversal.Bound[Array[Char], Char]        = Traversal.arrayValues(Reflect.char)
    val as: Traversal.Bound[Array[String], String]    = Traversal.arrayValues(Reflect.string)
    val sf: Traversal.Bound[Set[Float], Float]        = Traversal.setValues(Reflect.float)
    val mkc: Traversal.Bound[Predef.Map[Char, String], Char] =
      Traversal.mapKeys(Reflect.map(Reflect.char, Reflect.string))
    val mvs: Traversal.Bound[Predef.Map[Char, String], String] =
      Traversal.mapValues(Reflect.map(Reflect.char, Reflect.string))
    lazy val lr1: Traversal.Bound[List[Record1], Boolean] = Traversal.listValues(Record1.reflect).apply(Record1.b)
    lazy val lc1: Traversal.Bound[List[Variant1], Case1]  = Traversal.listValues(Variant1.reflect).apply(Variant1.c1)
    lazy val lc1_d: Traversal.Bound[List[Variant1], Double] =
      Traversal.listValues(Variant1.reflect).apply(Variant1.c1_d)
    lazy val lc4_lr3: Traversal.Bound[List[Case4], Record3] = Traversal.listValues(Case4.reflect).apply(Case4.lr3)
  }
}
