package zio.blocks.schema

import zio.blocks.chunk.Chunk
import zio.blocks.schema.DynamicOptic.Node._
import zio.blocks.schema.OpticCheck._
import zio.ZIO
import zio.blocks.schema.binding._
import zio.blocks.typeid.{Owner, TypeId}
import zio.test.Assertion._
import zio.test._

import scala.collection.immutable.ArraySeq

object OpticSpec extends SchemaBaseSpec {
  import OpticSpecTypes._

  def spec: Spec[TestEnvironment, Any] = suite("OpticSpec")(
    suite("Lens")(
      test("evaluates schema expressions") {
        assert(((Record1.f != Record1.f) || (Record1.b != Record1.b)).eval(Record1(false, 0)))(
          isRight(equalTo(Seq(false)))
        ) &&
        assert(((Record1.f === Record1.f) || (Record1.b != Record1.b)).eval(Record1(false, 0)))(
          isRight(equalTo(Seq(true)))
        ) &&
        assert(((Record1.f === Record1.f) && (Record1.b != Record1.b)).eval(Record1(false, 0)))(
          isRight(equalTo(Seq(false)))
        ) &&
        assert(((Record1.f === Record1.f) && (Record1.b === Record1.b)).eval(Record1(false, 0)))(
          isRight(equalTo(Seq(true)))
        ) &&
        assert((Record1.b === Record1.b).eval(Record1(false, 0)))(isRight(equalTo(Seq(true)))) &&
        assert((Record1.b === true).eval(Record1(false, 0)))(isRight(equalTo(Seq(false)))) &&
        assert((Record1.b != Record1.b).eval(Record1(false, 0)))(isRight(equalTo(Seq(false)))) &&
        assert((Record1.b != true).eval(Record1(false, 0)))(isRight(equalTo(Seq(true)))) &&
        assert((Record1.f === Record1.f).eval(Record1(false, 0)))(isRight(equalTo(Seq(true)))) &&
        assert((Record1.b && true).eval(Record1(false, 0)))(isRight(equalTo(Seq(false)))) &&
        assert((Record1.b && Record1.b).eval(Record1(false, 0)))(isRight(equalTo(Seq(false)))) &&
        assert((Record1.b || true).eval(Record1(false, 0)))(isRight(equalTo(Seq(true)))) &&
        assert((Record1.b || Record1.b).eval(Record1(false, 0)))(isRight(equalTo(Seq(false)))) &&
        assert((!Record1.b).eval(Record1(false, 0)))(isRight(equalTo(Seq(true)))) &&
        assert((Record1.f === 1.0f).eval(Record1(false, 0)))(isRight(equalTo(Seq(false)))) &&
        assert((Record1.f <= Record1.f).eval(Record1(false, 0)))(isRight(equalTo(Seq(true)))) &&
        assert((Record1.f <= 1.0f).eval(Record1(false, 0)))(isRight(equalTo(Seq(true)))) &&
        assert((Record1.f < 1.0f).eval(Record1(false, 0)))(isRight(equalTo(Seq(true)))) &&
        assert((Record1.f < Record1.f).eval(Record1(false, 0)))(isRight(equalTo(Seq(false)))) &&
        assert((Record1.f >= -1.0f).eval(Record1(false, 0)))(isRight(equalTo(Seq(true)))) &&
        assert((Record1.f >= Record1.f).eval(Record1(false, 0)))(isRight(equalTo(Seq(true)))) &&
        assert((Record1.f > -1.0f).eval(Record1(false, 0)))(isRight(equalTo(Seq(true)))) &&
        assert((Record1.f > Record1.f).eval(Record1(false, 0)))(isRight(equalTo(Seq(false)))) &&
        assert((Record1.f + 1.0f).eval(Record1(false, 0)))(isRight(equalTo(Seq(1.0f)))) &&
        assert((Record1.f - 1.0f).eval(Record1(false, 0)))(isRight(equalTo(Seq(-1.0f)))) &&
        assert((Record1.f * 2).eval(Record1(false, 2)))(isRight(equalTo(Seq(4.0f))))
      },
      test("evaluates schema expressions to dynamic values") {
        assert((Record1.b === Record1.b).evalDynamic(Record1(false, 0)))(
          isRight(equalTo(Seq(DynamicValue.Primitive(PrimitiveValue.Boolean(true)))))
        ) &&
        assert((Record1.b === true).evalDynamic(Record1(false, 0)))(
          isRight(equalTo(Seq(DynamicValue.Primitive(PrimitiveValue.Boolean(false)))))
        ) &&
        assert((Record1.b && true).evalDynamic(Record1(false, 0)))(
          isRight(equalTo(Seq(DynamicValue.Primitive(PrimitiveValue.Boolean(false)))))
        ) &&
        assert((Record1.b || true).evalDynamic(Record1(false, 0)))(
          isRight(equalTo(Seq(DynamicValue.Primitive(PrimitiveValue.Boolean(true)))))
        ) &&
        assert((!Record1.b).evalDynamic(Record1(false, 0)))(
          isRight(equalTo(Seq(DynamicValue.Primitive(PrimitiveValue.Boolean(true)))))
        ) &&
        assert((Record1.f === 1.0f).evalDynamic(Record1(false, 0)))(
          isRight(equalTo(Seq(DynamicValue.Primitive(PrimitiveValue.Boolean(false)))))
        ) &&
        assert((Record1.f >= 1.0f).evalDynamic(Record1(false, 0)))(
          isRight(equalTo(Seq(DynamicValue.Primitive(PrimitiveValue.Boolean(false)))))
        ) &&
        assert((Record1.f > 1.0f).evalDynamic(Record1(false, 0)))(
          isRight(equalTo(Seq(DynamicValue.Primitive(PrimitiveValue.Boolean(false)))))
        ) &&
        assert((Record1.f <= 1.0f).evalDynamic(Record1(false, 0)))(
          isRight(equalTo(Seq(DynamicValue.Primitive(PrimitiveValue.Boolean(true)))))
        ) &&
        assert((Record1.f < 1.0f).evalDynamic(Record1(false, 0)))(
          isRight(equalTo(Seq(DynamicValue.Primitive(PrimitiveValue.Boolean(true)))))
        ) &&
        assert((Record1.f != 1.0f).evalDynamic(Record1(false, 0)))(
          isRight(equalTo(Seq(DynamicValue.Primitive(PrimitiveValue.Boolean(true)))))
        ) &&
        assert((Record1.f + 1.0f).evalDynamic(Record1(false, 0)))(
          isRight(equalTo(Seq(DynamicValue.Primitive(PrimitiveValue.Float(1.0f)))))
        ) &&
        assert((Record1.f - 1.0f).evalDynamic(Record1(false, 0)))(
          isRight(equalTo(Seq(DynamicValue.Primitive(PrimitiveValue.Float(-1.0f)))))
        ) &&
        assert((Record1.f * 2.0f).evalDynamic(Record1(false, 2.0f)))(
          isRight(equalTo(Seq(DynamicValue.Primitive(PrimitiveValue.Float(4.0f)))))
        )
      },
      test("toDynamic") {
        assert(Box1.l.toDynamic)(equalTo(DynamicOptic(Chunk(Field("l"))))) &&
        assert(Box2.r1_b.toDynamic)(equalTo(DynamicOptic(Chunk(Field("r1"), Field("b"))))) &&
        assert(Record1.b.toDynamic)(equalTo(DynamicOptic(Chunk(Field("b"))))) &&
        assert(Record2.r1_b.toDynamic)(equalTo(DynamicOptic(Chunk(Field("r1"), Field("b"))))) &&
        assert(Record3.v1.toDynamic)(equalTo(DynamicOptic(Chunk(Field("v1"))))) &&
        assert(Record3.v1.toDynamic)(equalTo(DynamicOptic(Chunk(Field("v1"))))) &&
        assert(Record3.v1.toDynamic)(equalTo(DynamicOptic(Chunk(Field("v1")))))
      },
      test("checks prerequisites for creation") {
        ZIO.attempt(Lens(null, Case1.d)).flip.map(e => assertTrue(e.isInstanceOf[Throwable])) &&
        ZIO.attempt(Lens(Case1.d, null)).flip.map(e => assertTrue(e.isInstanceOf[Throwable])) &&
        ZIO.attempt(Lens(Case4.reflect, null)).flip.map(e => assertTrue(e.isInstanceOf[Throwable])) &&
        ZIO.attempt(Lens(null, Case4.reflect.fields(0))).flip.map(e => assertTrue(e.isInstanceOf[Throwable]))
      },
      test("optic macro requires record for creation") {
        ZIO.attempt {
          sealed trait Variant {
            def b: String
          }

          case class Case(b: String) extends Variant

          case class Test(a: Variant)

          object Test extends CompanionOptics[Test] {
            implicit val schema: Schema[Test] = Schema.derived
            val lens                          = optic(_.a.b)
          }

          Test.lens.get(Test(Case("VVV")))
        }.flip
          .map(e => assert(e.getMessage)(equalTo("Expected a record")))
      },
      test("fail to generate lens") {
        typeCheck {
          """case class Test(a: Double)

             object Test extends CompanionOptics[Test] {
               implicit val schema: Schema[Test] = Schema.derived
               val lens: Lens[Test, _]           = optic(_.equals(null))
             }"""
        }.map(
          assert(_)(
            isLeft(
              (startsWithString(
                "Expected path elements: .<field>, .when[<T>], .at(<index>), .atIndices(<indices>), .atKey(<key>), .atKeys(<keys>), .each, .eachKey, .eachValue, or .wrapped[<T>], got '"
              ) && endsWithString(".equals(null)'.")) ||
                containsString("Recursive value") // Scala 3.5+
            )
          )
        )
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
        assert(Record2.r1_f: Any)(not(equalTo(Record2.r1_b))) &&
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
        assert(Box1.l.get(Box1(1L)))(equalTo(1L)) &&
        assert(Box2.r1.get(Box2(Record1(true, 1))))(equalTo(Record1(true, 1))) &&
        assert(Box2.r1_b.get(Box2(Record1(true, 1))))(equalTo(true)) &&
        assert(Record1.b.get(Record1(true, 1)))(equalTo(true)) &&
        assert(Record1.b.get(Record1(false, 1)))(equalTo(false)) &&
        assert(Record2.r1_b.get(Record2(2L, Vector.empty, Record1(true, 1))))(equalTo(true)) &&
        assert(Record2.r1_b.get(Record2(2L, Vector.empty, Record1(false, 1))))(equalTo(false)) &&
        assert(
          Record3.r2_r1_b_left.get(Record3(Record1(false, 3), Record2(2L, Vector.empty, Record1(true, 1)), Case1(0.5)))
        )(equalTo(true)) &&
        assert(
          Record3.r2_r1_b_right.get(Record3(Record1(true, 3), Record2(2L, Vector.empty, Record1(false, 1)), Case1(0.5)))
        )(equalTo(false))
      },
      test("replaces a focus value") {
        assert(Box1.l.replace(Box1(1L), 2L))(equalTo(Box1(2L))) &&
        assert(Box2.r1.replace(Box2(Record1(true, 1)), Record1(false, 2)))(equalTo(Box2(Record1(false, 2)))) &&
        assert(Box2.r1_b.replace(Box2(Record1(true, 1)), false))(equalTo(Box2(Record1(false, 1)))) &&
        assert(Record1.b.replace(Record1(true, 1), false))(equalTo(Record1(false, 1))) &&
        assert(Record2.r1_b.replace(Record2(2L, Vector.empty, Record1(true, 1)), false))(
          equalTo(Record2(2L, Vector.empty, Record1(false, 1)))
        ) &&
        assert(
          Record3.r2_r1_b_left
            .replace(Record3(Record1(true, 3), Record2(2L, Vector.empty, Record1(true, 1)), Case1(0.5)), false)
        )(equalTo(Record3(Record1(true, 3), Record2(2L, Vector.empty, Record1(false, 1)), Case1(0.5))))
      },
      test("modifies a focus value") {
        assert(Box1.l.modify(Box1(1L), _ + 1L))(equalTo(Box1(2L))) &&
        assert(Box2.r1.modify(Box2(Record1(true, 1)), _ => null))(equalTo(Box2(null))) &&
        assert(Box2.r1_b.modify(Box2(Record1(true, 1)), x => !x))(equalTo(Box2(Record1(false, 1)))) &&
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
      test("evaluates schema expressions") {
        assert((Variant1.c1 === Variant1.c1).eval(Case1(0.1)))(isRight(equalTo(Seq(true)))) &&
        assert((Variant1.c1 === Variant1.c1).eval(Case4(Nil)))(
          isLeft(
            equalTo(
              OpticCheck(
                errors = ::(
                  UnexpectedCase(
                    expectedCase = "Case1",
                    actualCase = "Variant2",
                    full = DynamicOptic(Chunk(Case("Case1"))),
                    prefix = DynamicOptic(Chunk(Case("Case1"))),
                    actualValue = Case4(Nil)
                  ),
                  Nil
                )
              )
            )
          )
        )
      },
      test("evaluates schema expressions to dynamic values") {
        assert((Variant1.c1 === Variant1.c1).evalDynamic(Case1(0.1)))(
          isRight(equalTo(Seq(DynamicValue.Primitive(PrimitiveValue.Boolean(true)))))
        ) &&
        assert((Variant1.c1 === Variant1.c1).evalDynamic(Case4(Nil)))(
          isLeft(
            equalTo(
              OpticCheck(
                errors = ::(
                  UnexpectedCase(
                    expectedCase = "Case1",
                    actualCase = "Variant2",
                    full = DynamicOptic(Chunk(Case("Case1"))),
                    prefix = DynamicOptic(Chunk(Case("Case1"))),
                    actualValue = Case4(Nil)
                  ),
                  Nil
                )
              )
            )
          )
        )
      },
      test("toDynamic") {
        assert(Variant1.c1.toDynamic)(equalTo(DynamicOptic(Chunk(Case("Case1"))))) &&
        assert(Variant1.c2.toDynamic)(equalTo(DynamicOptic(Chunk(Case("Case2"))))) &&
        assert(Variant1.v2.toDynamic)(equalTo(DynamicOptic(Chunk(Case("Variant2"))))) &&
        assert(Variant1.v2_c3.toDynamic)(equalTo(DynamicOptic(Chunk(Case("Variant2"), Case("Case3"))))) &&
        assert(Variant1.v2_c4.toDynamic)(equalTo(DynamicOptic(Chunk(Case("Variant2"), Case("Case4"))))) &&
        assert(Variant1.v2_v3_c5_left.toDynamic)(
          equalTo(DynamicOptic(Chunk(Case("Variant2"), Case("Variant3"), Case("Case5"))))
        )
      },
      test("checks prerequisites for creation") {
        ZIO.attempt(Prism(null, Variant1.c1)).flip.map(e => assertTrue(e.isInstanceOf[Throwable])) &&
        ZIO.attempt(Prism(Variant1.c1, null)).flip.map(e => assertTrue(e.isInstanceOf[Throwable])) &&
        ZIO
          .attempt(Prism(Variant1.reflect, null))
          .flip
          .map(e => assertTrue(e.isInstanceOf[Throwable])) &&
        ZIO
          .attempt(Prism(null, Variant1.reflect.cases(0)))
          .flip
          .map(e => assertTrue(e.isInstanceOf[Throwable]))
      },
      test("optic macro requires variant for creation") {
        ZIO.attempt {
          case class Test(a: Double)

          object Test extends CompanionOptics[Test] {
            implicit val schema: Schema[Test] = Schema.derived
            val prism                         = optic(_.when[Test])
          }

          Test.prism
        }.flip
          .map(e => assert(e.getMessage)(equalTo("Expected a variant")))
      },
      test("fail to generate prism") {
        typeCheck {
          """case class Test(a: Double)

             object Test extends CompanionOptics[Test] {
               implicit val schema: Schema[Test] = Schema.derived
               val prism: Prism[Test, _]         = optic(null.asInstanceOf[Test => Double])
             }"""
        }.map(
          assert(_)(
            isLeft(
              startsWithString("Expected a lambda expression, got 'null.asInstanceOf[") ||
                containsString("Recursive value") // Scala 3.5+
            )
          )
        )
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
            hasError(
              "During attempted access at .when[Case1], encountered an unexpected case at .when[Case1]: expected Case1, but got Case2"
            )
          )
        ) &&
        assert(Variant1.c2.check(Case1(0.1)))(
          isSome(
            hasError(
              "During attempted access at .when[Case2], encountered an unexpected case at .when[Case2]: expected Case2, but got Case1"
            )
          )
        ) &&
        assert(Variant1.v2.check(Case1(0.1)))(
          isSome(
            hasError(
              "During attempted access at .when[Variant2], encountered an unexpected case at .when[Variant2]: expected Variant2, but got Case1"
            )
          )
        ) &&
        assert(Variant1.v2_c3.check(Case1(0.1)))(
          isSome(
            hasError(
              "During attempted access at .when[Variant2].when[Case3], encountered an unexpected case at .when[Variant2]: expected Variant2, but got Case1"
            )
          )
        ) &&
        assert(Variant2.c3.check(Case4(List(Record3(null, null, null)))))(
          isSome(
            hasError(
              "During attempted access at .when[Case3], encountered an unexpected case at .when[Case3]: expected Case3, but got Case4"
            )
          )
        ) &&
        assert(Variant2.c4.check(Case3(Case1(0.1))))(
          isSome(
            hasError(
              "During attempted access at .when[Case4], encountered an unexpected case at .when[Case4]: expected Case4, but got Case3"
            )
          )
        ) &&
        assert(Variant1.v2_v3_c5_left.check(Case6(null)))(
          isSome(
            hasError(
              "During attempted access at .when[Variant2].when[Variant3].when[Case5], encountered an unexpected case at .when[Variant2].when[Variant3].when[Case5]"
            )
          )
        ) &&
        assert(Variant1.v2_v3_c5_right.check(Case6(null)))(
          isSome(
            hasError(
              "During attempted access at .when[Variant2].when[Variant3].when[Case5], encountered an unexpected case at .when[Variant2].when[Variant3].when[Case5]: expected Case5, but got Case6"
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
                    full = DynamicOptic(Chunk(Case("Case1"))),
                    prefix = DynamicOptic(Chunk(Case("Case1"))),
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
                    full = DynamicOptic(Chunk(Case("Case2"))),
                    prefix = DynamicOptic(Chunk(Case("Case2"))),
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
                    full = DynamicOptic(Chunk(Case("Variant2"))),
                    prefix = DynamicOptic(Chunk(Case("Variant2"))),
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
                    full = DynamicOptic(Chunk(Case("Variant2"), Case("Case3"))),
                    prefix = DynamicOptic(Chunk(Case("Variant2"))),
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
                    full = DynamicOptic(Chunk(Case("Case3"))),
                    prefix = DynamicOptic(Chunk(Case("Case3"))),
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
                    full = DynamicOptic(Chunk(Case("Case4"))),
                    prefix = DynamicOptic(Chunk(Case("Case4"))),
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
                    full = DynamicOptic(Chunk(Case("Variant2"), Case("Variant3"), Case("Case5"))),
                    prefix = DynamicOptic(Chunk(Case("Variant2"), Case("Variant3"), Case("Case5"))),
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
                    full = DynamicOptic(Chunk(Case("Variant2"), Case("Variant3"), Case("Case5"))),
                    prefix = DynamicOptic(Chunk(Case("Variant2"), Case("Variant3"), Case("Case5"))),
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
                    full = DynamicOptic(Chunk(Case("Case1"))),
                    prefix = DynamicOptic(Chunk(Case("Case1"))),
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
                    full = DynamicOptic(Chunk(Case("Case2"))),
                    prefix = DynamicOptic(Chunk(Case("Case2"))),
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
                    full = DynamicOptic(Chunk(Case("Variant2"))),
                    prefix = DynamicOptic(Chunk(Case("Variant2"))),
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
                    full = DynamicOptic(Chunk(Case("Variant2"), Case("Case3"))),
                    prefix = DynamicOptic(Chunk(Case("Variant2"))),
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
                    full = DynamicOptic(Chunk(Case("Case3"))),
                    prefix = DynamicOptic(Chunk(Case("Case3"))),
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
                    full = DynamicOptic(Chunk(Case("Case4"))),
                    prefix = DynamicOptic(Chunk(Case("Case4"))),
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
                    full = DynamicOptic(Chunk(Case("Variant2"), Case("Variant3"), Case("Case5"))),
                    prefix = DynamicOptic(Chunk(Case("Variant2"), Case("Variant3"))),
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
                    full = DynamicOptic(Chunk(Case("Variant2"), Case("Variant3"), Case("Case5"))),
                    prefix = DynamicOptic(Chunk(Case("Variant2"), Case("Variant3"))),
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
                    full = DynamicOptic(Chunk(Case("Case1"))),
                    prefix = DynamicOptic(Chunk(Case("Case1"))),
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
                    full = DynamicOptic(Chunk(Case("Case2"))),
                    prefix = DynamicOptic(Chunk(Case("Case2"))),
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
                    full = DynamicOptic(Chunk(Case("Variant2"))),
                    prefix = DynamicOptic(Chunk(Case("Variant2"))),
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
                    full = DynamicOptic(Chunk(Case("Variant2"), Case("Case3"))),
                    prefix = DynamicOptic(Chunk(Case("Variant2"))),
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
                    full = DynamicOptic(Chunk(Case("Case3"))),
                    prefix = DynamicOptic(Chunk(Case("Case3"))),
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
                    full = DynamicOptic(Chunk(Case("Case4"))),
                    prefix = DynamicOptic(Chunk(Case("Case4"))),
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
                    full = DynamicOptic(Chunk(Case("Variant2"), Case("Variant3"), Case("Case5"))),
                    prefix = DynamicOptic(Chunk(Case("Variant2"), Case("Variant3"))),
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
                    full = DynamicOptic(Chunk(Case("Variant2"), Case("Variant3"), Case("Case5"))),
                    prefix = DynamicOptic(Chunk(Case("Variant2"), Case("Variant3"))),
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
      test("evaluates schema expressions") {
        assert((Variant1.c1_d === Variant1.c1_d).eval(Case1(0.1)))(isRight(equalTo(Seq(true)))) &&
        assert((Variant1.c1_d === 0.1).eval(Case1(0.1)))(isRight(equalTo(Seq(true)))) &&
        assert((Variant1.c1_d != 0.1).eval(Case1(0.1)))(isRight(equalTo(Seq(false)))) &&
        assert((Variant1.c1_d === Variant1.c1_d).eval(Case4(Nil)))(
          isLeft(
            equalTo(
              OpticCheck(
                errors = ::(
                  UnexpectedCase(
                    expectedCase = "Case1",
                    actualCase = "Variant2",
                    full = DynamicOptic(Chunk(Case("Case1"), Field("d"))),
                    prefix = DynamicOptic(Chunk(Case("Case1"))),
                    actualValue = Case4(Nil)
                  ),
                  Nil
                )
              )
            )
          )
        )
      },
      test("evaluates schema expressions to dynamic values") {
        assert((Variant1.c1_d === Variant1.c1_d).evalDynamic(Case1(0.1)))(
          isRight(equalTo(Seq(DynamicValue.Primitive(PrimitiveValue.Boolean(true)))))
        ) &&
        assert((Variant1.c1_d === Variant1.c1_d).evalDynamic(Case4(Nil)))(
          isLeft(
            equalTo(
              OpticCheck(
                errors = ::(
                  UnexpectedCase(
                    expectedCase = "Case1",
                    actualCase = "Variant2",
                    full = DynamicOptic(Chunk(Case("Case1"), Field("d"))),
                    prefix = DynamicOptic(Chunk(Case("Case1"))),
                    actualValue = Case4(Nil)
                  ),
                  Nil
                )
              )
            )
          )
        )
      },
      test("toDynamic") {
        assert(Wrapper.r1.toDynamic)(equalTo(DynamicOptic(Chunk(Wrapped)))) &&
        assert(Wrapper.r1_b.toDynamic)(equalTo(DynamicOptic(Chunk(Wrapped, Field("b"))))) &&
        assert(Case5.aas.toDynamic)(equalTo(DynamicOptic(Chunk(Field("as"), AtIndex(1))))) &&
        assert(Case6.akmil.toDynamic)(
          equalTo(DynamicOptic(Chunk(Field("mil"), AtMapKey(Schema[Int].toDynamicValue(1)))))
        ) &&
        assert(Variant1.c1_d.toDynamic)(equalTo(DynamicOptic(Chunk(Case("Case1"), Field("d"))))) &&
        assert(Variant1.c2_r3.toDynamic)(equalTo(DynamicOptic(Chunk(Case("Case2"), Field("r3"))))) &&
        assert(Variant1.c2_r3_r1.toDynamic)(equalTo(DynamicOptic(Chunk(Case("Case2"), Field("r3"), Field("r1")))))
      },
      test("checks prerequisites for creation") {
        ZIO
          .attempt(Optional.wrapped(null: Reflect.Wrapper.Bound[Box1, Long]))
          .flip
          .map(e => assertTrue(e.isInstanceOf[Throwable])) &&
        ZIO
          .attempt(Optional.at(null: Reflect.Sequence.Bound[Int, Array], 1))
          .flip
          .map(e => assertTrue(e.isInstanceOf[Throwable])) &&
        ZIO
          .attempt(Optional.atKey(null: Reflect.Map.Bound[Int, Long, Map], 1))
          .flip
          .map(e => assertTrue(e.isInstanceOf[Throwable])) &&
        ZIO
          .attempt(Optional.at(null: Reflect.Sequence.Bound[Int, Array], -1))
          .flip
          .map(e => assertTrue(e.isInstanceOf[Throwable])) &&
        ZIO
          .attempt(Optional(null: Prism[Variant1, Case1], Case1.d))
          .flip
          .map(e => assertTrue(e.isInstanceOf[Throwable])) &&
        ZIO
          .attempt(Optional(Variant1.c1, null: Lens[Case1, Int]))
          .flip
          .map(e => assertTrue(e.isInstanceOf[Throwable])) &&
        ZIO
          .attempt(Optional(null: Optional[Variant1, Case1], Case1.d))
          .flip
          .map(e => assertTrue(e.isInstanceOf[Throwable])) &&
        ZIO
          .attempt(Optional(Variant1.c2_r3_v1_c1, null: Lens[Case1, Int]))
          .flip
          .map(e => assertTrue(e.isInstanceOf[Throwable])) &&
        ZIO
          .attempt(Optional(Variant1.c2_r3_v1_c1, null: Optional[Case1, Int]))
          .flip
          .map(e => assertTrue(e.isInstanceOf[Throwable])) &&
        ZIO
          .attempt(Optional(null: Optional[Variant1, Variant1], Variant1.c1_d))
          .flip
          .map(e => assertTrue(e.isInstanceOf[Throwable]))
      },
      test("optic macro requires wrapper for creation") {
        ZIO.attempt {
          case class Test(a: String)

          object Test extends CompanionOptics[Test] {
            implicit val schema: Schema[Test] = Schema.derived
            val optional                      = optic(_.wrapped[String])
          }

          Test.optional
        }.flip
          .map(e => assert(e.getMessage)(equalTo("Expected a wrapper")))
      },
      test("optic macro generates optionals for sequence or map") {
        object Test1 extends CompanionOptics[Vector[String]] {
          val optional: Optional[Vector[String], String] = optic[String](_.at(1))
        }
        object Test2 extends CompanionOptics[Map[Int, Long]] {
          val optional: Optional[Map[Int, Long], Long] = optic[Long](_.atKey(1))
        }

        assert(Test1.optional.getOption(Vector("a", "b", "c")))(isSome(equalTo("b"))) &&
        assert(Test2.optional.getOption(Map(1 -> 1L, 2 -> 2L, 3 -> 3L)))(isSome(equalTo(1L)))
      },
      test("check") {
        assert(Variant1.c1_d.check(Case2(Record3(null, null, null))))(
          isSome(hasError("expected Case1, but got Case2"))
        ) &&
        assert(Variant1.c2_r3_v1_c1.check(Case2(Record3(null, null, Case2(null)))))(
          isSome(hasError("expected Case1, but got Case2"))
        ) &&
        assert(Variant2.c3_v1_c1_left.check(Case4(Nil)))(
          isSome(hasError("expected Case3, but got Case4"))
        ) &&
        assert(Variant2.c3_v1_c1_right.check(Case3(Case2(null))))(
          isSome(hasError("expected Case1, but got Case2"))
        ) &&
        assert(Case3.v1_c1_d_left.check(Case3(Case4(Nil))))(
          isSome(
            hasError(
              "During attempted access at .v1.when[Case1].d, encountered an unexpected case at .v1.when[Case1]: expected Case1, but got Variant2"
            )
          )
        ) &&
        assert(Variant1.c2_r3_r2_r1_b_left.check(Case1(0.1)))(
          isSome(hasError("expected Case2, but got Case1"))
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
        assert(Case3.v1_v2_c3_v1_v2: Any)(not(equalTo(""))) &&
        assert(Case5.aas)(equalTo(Case5.aas)) &&
        assert(Case5.aas.hashCode)(equalTo(Case5.aas.hashCode)) &&
        assert(Case6.akmil)(equalTo(Case6.akmil)) &&
        assert(Case6.akmil.hashCode)(equalTo(Case6.akmil.hashCode)) &&
        assert(Wrapper.r1)(equalTo(Wrapper.r1)) &&
        assert(Wrapper.r1.hashCode)(equalTo(Wrapper.r1.hashCode)) &&
        assert(Wrapper.r1_b)(equalTo(Wrapper.r1_b)) &&
        assert(Wrapper.r1_b.hashCode)(equalTo(Wrapper.r1_b.hashCode))
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
        assert(Variant2.c3_v1_v2_c4.source)(equalTo(Variant2.reflect)) &&
        assert(Case5.aas.source)(equalTo(Case5.reflect)) &&
        assert(Case6.akmil.source)(equalTo(Case6.reflect)) &&
        assert(Wrapper.r1.source)(equalTo(Wrapper.reflect)) &&
        assert(Wrapper.r1_b.source)(equalTo(Wrapper.reflect)) &&
        assert(Wrappers.w_wr1.source)(equalTo(Wrappers.reflect)) &&
        assert(Record4.w_w_wr1.source)(equalTo(Record4.reflect))
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
        assert(Variant2.c3_v1_v2_c4.focus)(equalTo(Case4.reflect)) &&
        assert(Case5.aas.focus)(equalTo(Reflect.string[Binding])) &&
        assert(Case6.akmil.focus)(equalTo(Reflect.long[Binding])) &&
        assert(Wrapper.r1.focus)(equalTo(Record1.reflect)) &&
        assert(Wrapper.r1_b.focus)(equalTo(Reflect.boolean[Binding])) &&
        assert(Wrappers.w_wr1.focus)(equalTo(Record1.reflect)) &&
        assert(Record4.w_w_wr1.focus)(equalTo(Record1.reflect))
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
        assert(Case3.v1_c1.check(Case3(Case1(0.1))))(isNone) &&
        assert(Case5.aas.check(Case5(Set(), Array("a", "b", "c"))))(isNone) &&
        assert(Case6.akmil.check(Case6(Map(1 -> 1L, 2 -> 2L, 3 -> 3L))))(isNone) &&
        assert(Collections.alb.check(List(1: Byte, 2: Byte, 3: Byte)))(isNone) &&
        assert(Collections.aabl.check(Array(false, true, false)))(isNone) &&
        assert(Collections.aab.check(Array(1: Byte, 2: Byte, 3: Byte)))(isNone) &&
        assert(Collections.aash.check(Array(1: Short, 2: Short, 3: Short)))(isNone) &&
        assert(Collections.aai.check(Array(1, 2, 3)))(isNone) &&
        assert(Collections.aal.check(Array(1L, 2L, 3L)))(isNone) &&
        assert(Collections.aad.check(Array(1.0, 2.0, 3.0)))(isNone) &&
        assert(Collections.aaf.check(Array(1.0f, 2.0f, 3.0f)))(isNone) &&
        assert(Collections.aac.check(Array('a', 'b', 'c')))(isNone) &&
        assert(Collections.aas.check(Array("a", "b", "c")))(isNone) &&
        assert(Wrapper.r1.check(Wrapper.applyUnsafe(Record1(true, 1))))(isNone) &&
        assert(Wrapper.r1_b.check(Wrapper.applyUnsafe(Record1(true, 1))))(isNone)
      },
      test("doesn't pass check if a focus value doesn't exist") {
        assert(Variant1.c2_r3_r1.check(Case3(Case1(0.1))))(
          isSome(
            hasError(
              "During attempted access at .when[Case2].r3.r1, encountered an unexpected case at .when[Case2]: expected Case2, but got Variant2"
            )
          )
        ) &&
        assert(Case2.r3_v1_c1.check(Case2(Record3(null, null, Case4(Nil)))))(
          isSome(
            hasError(
              "During attempted access at .r3.v1.when[Case1], encountered an unexpected case at .r3.v1.when[Case1]: expected Case1, but got Variant2"
            )
          )
        ) &&
        assert(Variant1.c2_r3_v1_c1.check(Case2(Record3(null, null, Case4(Nil)))))(
          isSome(
            hasError(
              "During attempted access at .when[Case2].r3.v1.when[Case1], encountered an unexpected case at .when[Case2].r3.v1.when[Case1]: expected Case1, but got Variant2"
            )
          )
        ) &&
        assert(Variant2.c3_v1_v2_c4.check(Case3(Case1(0.1))))(
          isSome(
            hasError(
              "During attempted access at .when[Case3].v1.when[Variant2].when[Case4], encountered an unexpected case at .when[Case3].v1.when[Variant2]: expected Variant2, but got Case1"
            )
          )
        ) &&
        assert(Variant2.c3_v1_c1_left.check(Case4(Nil)))(
          isSome(
            hasError(
              "During attempted access at .when[Case3].v1.when[Case1], encountered an unexpected case at .when[Case3]: expected Case3, but got Case4"
            )
          )
        ) &&
        assert(Variant2.c3_v1_c1_right.check(Case3(Case2(null))))(
          isSome(
            hasError(
              "During attempted access at .when[Case3].v1.when[Case1], encountered an unexpected case at .when[Case3].v1.when[Case1]: expected Case1, but got Case2"
            )
          )
        ) &&
        assert(Variant2.c3_v1_c1_d_right.check(Case4(Nil)))(
          isSome(
            hasError(
              "During attempted access at .when[Case3].v1.when[Case1].d, encountered an unexpected case at .when[Case3]: expected Case3, but got Case4"
            )
          )
        ) &&
        assert(Variant2.c3_v1.check(Case4(Nil)))(
          isSome(
            hasError(
              "During attempted access at .when[Case3].v1, encountered an unexpected case at .when[Case3]: expected Case3, but got Case4"
            )
          )
        ) &&
        assert(Case3.v1_c1_d_left.check(Case3(Case4(Nil))))(
          isSome(
            hasError(
              "During attempted access at .v1.when[Case1].d, encountered an unexpected case at .v1.when[Case1]: expected Case1, but got Variant2"
            )
          )
        ) &&
        assert(Case3.v1_c1_d_right.check(Case3(Case4(Nil))))(
          isSome(
            hasError(
              "During attempted access at .v1.when[Case1].d, encountered an unexpected case at .v1.when[Case1]: expected Case1, but got Variant2"
            )
          )
        ) &&
        assert(Case5.aas.check(Case5(Set(), Array())))(
          isSome(
            hasError(
              "During attempted access at .as.at(1), encountered a sequence out of bounds at .as.at(1): index is 1, but size is 0"
            )
          )
        ) &&
        assert(Collections.alb.check(List()))(
          isSome(
            hasError(
              "During attempted access at .at(1), encountered a sequence out of bounds at .at(1): index is 1, but size is 0"
            )
          )
        ) &&
        assert(Case6.akmil.check(Case6(Map())))(
          isSome(
            hasError(
              "During attempted access at .mil.atKey(1), encountered missing key at .mil.atKey(1)"
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
        assert(Case3.v1_c1.getOption(Case3(Case1(0.1))))(isSome(equalTo(Case1(0.1)))) &&
        assert(Case5.aas.getOption(Case5(Set(), Array("a", "b", "c"))))(isSome(equalTo("b"))) &&
        assert(Case6.akmil.getOption(Case6(Map(1 -> 1L, 2 -> 2L, 3 -> 3L))))(isSome(equalTo(1L))) &&
        assert(Collections.alb.getOption(List(1: Byte, 2: Byte, 3: Byte)))(isSome(equalTo(2: Byte))) &&
        assert(Collections.aabl.getOption(Array(false, true, false)))(isSome(equalTo(true))) &&
        assert(Collections.aab.getOption(Array(1: Byte, 2: Byte, 3: Byte)))(isSome(equalTo(2: Byte))) &&
        assert(Collections.aash.getOption(Array(1: Short, 2: Short, 3: Short)))(isSome(equalTo(2: Short))) &&
        assert(Collections.aai.getOption(Array(1, 2, 3)))(isSome(equalTo(2))) &&
        assert(Collections.aal.getOption(Array(1L, 2L, 3L)))(isSome(equalTo(2L))) &&
        assert(Collections.aad.getOption(Array(1.0, 2.0, 3.0)))(isSome(equalTo(2.0))) &&
        assert(Collections.aaf.getOption(Array(1.0f, 2.0f, 3.0f)))(isSome(equalTo(2.0f))) &&
        assert(Collections.aac.getOption(Array('a', 'b', 'c')))(isSome(equalTo('b'))) &&
        assert(Collections.aas.getOption(Array("a", "b", "c")))(isSome(equalTo("b"))) &&
        assert(Wrapper.r1.getOption(Wrapper.applyUnsafe(Record1(true, 1))))(isSome(equalTo(Record1(true, 1)))) &&
        assert(Wrapper.r1_b.getOption(Wrapper.applyUnsafe(Record1(true, 1))))(isSome(equalTo(true)))
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
        assert(Case3.v1_c1_d_right.getOption(Case3(Case4(Nil))))(isNone) &&
        assert(Case5.aas.getOption(Case5(Set(), Array())))(isNone) &&
        assert(Collections.alb.getOption(List()))(isNone) &&
        assert(Case6.akmil.getOption(Case6(Map())))(isNone)
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
        assert(Case3.v1_c1.getOrFail(Case3(Case1(0.1))))(isRight(equalTo(Case1(0.1)))) &&
        assert(Case5.aas.getOrFail(Case5(Set(), Array("a", "b", "c"))))(isRight(equalTo("b"))) &&
        assert(Case6.akmil.getOrFail(Case6(Map(1 -> 1L, 2 -> 2L, 3 -> 3L))))(isRight(equalTo(1L))) &&
        assert(Wrapper.r1.getOrFail(Wrapper.applyUnsafe(Record1(true, 1))))(isRight(equalTo(Record1(true, 1)))) &&
        assert(Wrapper.r1_b.getOrFail(Wrapper.applyUnsafe(Record1(true, 1))))(isRight(equalTo(true)))
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
                    full = DynamicOptic(Chunk(Case("Case2"), Field("r3"), Field("r1"))),
                    prefix = DynamicOptic(Chunk(Case("Case2"))),
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
                    full = DynamicOptic(Chunk(Field("r3"), Field("v1"), Case("Case1"))),
                    prefix = DynamicOptic(Chunk(Field("r3"), Field("v1"), Case("Case1"))),
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
                    full = DynamicOptic(Chunk(Case("Case2"), Field("r3"), Field("v1"), Case("Case1"))),
                    prefix = DynamicOptic(Chunk(Case("Case2"), Field("r3"), Field("v1"), Case("Case1"))),
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
                    full = DynamicOptic(Chunk(Case("Case3"), Field("v1"), Case("Variant2"), Case("Case4"))),
                    prefix = DynamicOptic(Chunk(Case("Case3"), Field("v1"), Case("Variant2"))),
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
                    full = DynamicOptic(Chunk(Case("Case3"), Field("v1"), Case("Case1"))),
                    prefix = DynamicOptic(Chunk(Case("Case3"))),
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
                    full = DynamicOptic(Chunk(Case("Case3"), Field("v1"), Case("Case1"))),
                    prefix = DynamicOptic(Chunk(Case("Case3"), Field("v1"), Case("Case1"))),
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
                    full = DynamicOptic(Chunk(Case("Case3"), Field("v1"), Case("Case1"), Field("d"))),
                    prefix = DynamicOptic(Chunk(Case("Case3"))),
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
                    full = DynamicOptic(Chunk(Case("Case3"), Field("v1"))),
                    prefix = DynamicOptic(Chunk(Case("Case3"))),
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
                    full = DynamicOptic(Chunk(Field("v1"), Case("Case1"), Field("d"))),
                    prefix = DynamicOptic(Chunk(Field("v1"), Case("Case1"))),
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
                    full = DynamicOptic(Chunk(Field("v1"), Case("Case1"), Field("d"))),
                    prefix = DynamicOptic(Chunk(Field("v1"), Case("Case1"))),
                    actualValue = Case4(Nil)
                  ),
                  Nil
                )
              )
            )
          )
        ) &&
        assert(Case5.aas.getOrFail(Case5(Set(), Array())))(
          isLeft(
            equalTo(
              OpticCheck(
                errors = ::(
                  SequenceIndexOutOfBounds(
                    full = DynamicOptic(Chunk(Field("as"), AtIndex(1))),
                    prefix = DynamicOptic(Chunk(Field("as"), AtIndex(1))),
                    index = 1,
                    size = 0
                  ),
                  Nil
                )
              )
            )
          )
        ) &&
        assert(Case6.akmil.getOrFail(Case6(Map())))(
          isLeft(
            equalTo(
              OpticCheck(
                errors = ::(
                  MissingKey(
                    full = DynamicOptic(Chunk(Field("mil"), AtMapKey(Schema[Int].toDynamicValue(1)))),
                    prefix = DynamicOptic(Chunk(Field("mil"), AtMapKey(Schema[Int].toDynamicValue(1)))),
                    key = 1
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
        assert(Case3.v1_c1.replace(Case3(Case1(0.1)), Case1(0.2)))(equalTo(Case3(Case1(0.2)))) &&
        assert(Wrapper.r1.replace(Wrapper.applyUnsafe(Record1(true, 1)), Record1(false, -2)))(
          equalTo(Wrapper.applyUnsafe(Record1(false, -2)))
        ) &&
        assert(Wrapper.r1_b.replace(Wrapper.applyUnsafe(Record1(true, 0)), false))(
          equalTo(Wrapper.applyUnsafe(Record1(false, 0)))
        )
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
        assert(Case3.v1_c1_d_right.replace(Case3(Case4(Nil)), 0.2))(equalTo(Case3(Case4(Nil)))) &&
        assert(Wrapper.r1.replace(Wrapper.applyUnsafe(Record1(true, 1)), Record1(false, 2)))(
          equalTo(Wrapper.applyUnsafe(Record1(true, 1)))
        ) &&
        assert(Wrapper.r1_b.replace(Wrapper.applyUnsafe(Record1(true, 1)), false))(
          equalTo(Wrapper.applyUnsafe(Record1(true, 1)))
        )
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
        assert(Case3.v1_c1.replaceOption(Case3(Case1(0.1)), Case1(0.2)))(isSome(equalTo(Case3(Case1(0.2))))) &&
        assert(Wrapper.r1.replaceOption(Wrapper.applyUnsafe(Record1(true, 1)), Record1(false, -2)))(
          isSome(equalTo(Wrapper.applyUnsafe(Record1(false, -2))))
        ) &&
        assert(Wrapper.r1_b.replaceOption(Wrapper.applyUnsafe(Record1(true, 0)), false))(
          isSome(equalTo(Wrapper.applyUnsafe(Record1(false, 0))))
        )
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
        assert(Case3.v1_c1_d_right.replaceOption(Case3(Case4(Nil)), 0.2))(isNone) &&
        assert(Wrapper.r1.replaceOption(Wrapper.applyUnsafe(Record1(true, 1)), Record1(false, 2)))(isNone) &&
        assert(Wrapper.r1_b.replaceOption(Wrapper.applyUnsafe(Record1(true, 1)), false))(isNone)
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
        assert(Case3.v1_c1.replaceOrFail(Case3(Case1(0.1)), Case1(0.2)))(isRight(equalTo(Case3(Case1(0.2))))) &&
        assert(Wrapper.r1.replaceOrFail(Wrapper.applyUnsafe(Record1(true, 1)), Record1(false, -2)))(
          isRight(equalTo(Wrapper.applyUnsafe(Record1(false, -2))))
        ) &&
        assert(Wrapper.r1_b.replaceOrFail(Wrapper.applyUnsafe(Record1(true, 0)), false))(
          isRight(equalTo(Wrapper.applyUnsafe(Record1(false, 0))))
        )
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
                    full = DynamicOptic(Chunk(Case("Case2"), Field("r3"), Field("r1"))),
                    prefix = DynamicOptic(Chunk(Case("Case2"))),
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
                    full = DynamicOptic(Chunk(Field("r3"), Field("v1"), Case("Case1"))),
                    prefix = DynamicOptic(Chunk(Field("r3"), Field("v1"), Case("Case1"))),
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
                    full = DynamicOptic(Chunk(Case("Case2"), Field("r3"), Field("v1"), Case("Case1"))),
                    prefix = DynamicOptic(Chunk(Case("Case2"), Field("r3"), Field("v1"), Case("Case1"))),
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
                    full = DynamicOptic(Chunk(Case("Case3"), Field("v1"), Case("Variant2"), Case("Case4"))),
                    prefix = DynamicOptic(Chunk(Case("Case3"), Field("v1"), Case("Variant2"))),
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
                    full = DynamicOptic(Chunk(Case("Case3"), Field("v1"), Case("Case1"))),
                    prefix = DynamicOptic(Chunk(Case("Case3"))),
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
                    full = DynamicOptic(Chunk(Case("Case3"), Field("v1"), Case("Case1"))),
                    prefix = DynamicOptic(Chunk(Case("Case3"), Field("v1"), Case("Case1"))),
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
                    full = DynamicOptic(Chunk(Case("Case3"), Field("v1"), Case("Case1"), Field("d"))),
                    prefix = DynamicOptic(Chunk(Case("Case3"))),
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
                    full = DynamicOptic(Chunk(Case("Case3"), Field("v1"))),
                    prefix = DynamicOptic(Chunk(Case("Case3"))),
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
                    full = DynamicOptic(Chunk(Field("v1"), Case("Case1"), Field("d"))),
                    prefix = DynamicOptic(Chunk(Field("v1"), Case("Case1"))),
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
                    full = DynamicOptic(Chunk(Field("v1"), Case("Case1"), Field("d"))),
                    prefix = DynamicOptic(Chunk(Field("v1"), Case("Case1"))),
                    actualValue = Case4(Nil)
                  ),
                  Nil
                )
              )
            )
          )
        ) &&
        assert(Wrapper.r1.replaceOrFail(Wrapper.applyUnsafe(Record1(true, 1)), Record1(false, 2)))(
          isLeft(
            equalTo(
              OpticCheck(
                errors = ::(
                  WrappingError(
                    full = DynamicOptic(Chunk(Wrapped)),
                    prefix = DynamicOptic(Chunk(Wrapped)),
                    error = SchemaError.validationFailed("Unexpected 'Wrapper' value")
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
        assert(Case3.v1_c1.modify(Case3(Case1(0.1)), _ => Case1(0.2)))(equalTo(Case3(Case1(0.2)))) &&
        assert(Collections.alb.modify(List(1: Byte, 2: Byte, 3: Byte), x => (x + 1).toByte))(
          equalTo(List(1: Byte, 3: Byte, 3: Byte))
        ) &&
        assert(Collections.ailb.modify(List(1: Byte, 2: Byte, 3: Byte), x => (x + 1).toByte))(
          equalTo(List(1: Byte, 3: Byte, 4: Byte))
        ) &&
        assert(Collections.alc1_d.modify(List(Case1(1.0), Case1(2.0), Case1(3.0)), _ + 1.0))(
          equalTo(List(Case1(1.0), Case1(3.0), Case1(3.0)))
        ) &&
        assert(Collections.aabl.modify(Array(false, true, false), x => !x))(equalTo(Array(false, false, false))) &&
        assert(Collections.aab.modify(Array(1: Byte, 2: Byte, 3: Byte), x => (x + 1).toByte))(
          equalTo(Array(1: Byte, 3: Byte, 3: Byte))
        ) &&
        assert(Collections.aash.modify(Array(1: Short, 2: Short, 3: Short), x => (x + 1).toShort))(
          equalTo(Array(1: Short, 3: Short, 3: Short))
        ) &&
        assert(Collections.aai.modify(Array(1, 2, 3), _ + 1))(equalTo(Array(1, 3, 3))) &&
        assert(Collections.aal.modify(Array(1L, 2L, 3L), _ + 1L))(equalTo(Array(1L, 3L, 3L))) &&
        assert(Collections.aad.modify(Array(1.0, 2.0, 3.0), _ + 1.0))(equalTo(Array(1.0, 3.0, 3.0))) &&
        assert(Collections.aaf.modify(Array(1.0f, 2.0f, 3.0f), _ + 1.0f))(equalTo(Array(1.0f, 3.0f, 3.0f))) &&
        assert(Collections.aac.modify(Array('a', 'b', 'c'), _.toUpper))(equalTo(Array('a', 'B', 'c'))) &&
        assert(Collections.aas.modify(Array("a", "b", "c"), _ + "x"))(equalTo(Array("a", "bx", "c"))) &&
        assert(Collections.akms.modify(Map('A' -> "a", 'B' -> "b", 'C' -> "c"), _ + "x"))(
          equalTo(Map('A' -> "ax", 'B' -> "b", 'C' -> "c"))
        ) &&
        assert(Collections.akmc1_d.modify(Map('A' -> Case1(1.0), 'B' -> Case1(2.0), 'C' -> Case1(2.0)), _ + 1.0))(
          equalTo(Map('A' -> Case1(2.0), 'B' -> Case1(2.0), 'C' -> Case1(2.0)))
        ) &&
        assert(Wrapper.r1.modify(Wrapper.applyUnsafe(Record1(true, 1)), _ => Record1(false, -2)))(
          equalTo(Wrapper.applyUnsafe(Record1(false, -2)))
        ) &&
        assert(Wrapper.r1_b.modify(Wrapper.applyUnsafe(Record1(true, 0)), x => !x))(
          equalTo(Wrapper.applyUnsafe(Record1(false, 0)))
        )
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
        assert(Case3.v1_c1_d_right.modify(Case3(Case4(Nil)), _ => 0.2))(equalTo(Case3(Case4(Nil)))) &&
        assert(Collections.akms.modify(Map('B' -> "b", 'C' -> "c"), _ + "x"))(equalTo(Map('B' -> "b", 'C' -> "c"))) &&
        assert(Wrapper.r1.modify(Wrapper.applyUnsafe(Record1(true, 1)), _ => Record1(false, 2)))(
          equalTo(Wrapper.applyUnsafe(Record1(true, 1)))
        ) &&
        assert(Wrapper.r1_b.modify(Wrapper.applyUnsafe(Record1(true, 1)), x => !x))(
          equalTo(Wrapper.applyUnsafe(Record1(true, 1)))
        )
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
        assert(Case3.v1_c1.modifyOption(Case3(Case1(0.1)), _ => Case1(0.2)))(isSome(equalTo(Case3(Case1(0.2))))) &&
        assert(Wrapper.r1.modifyOption(Wrapper.applyUnsafe(Record1(true, 1)), _ => Record1(false, -1)))(
          isSome(equalTo(Wrapper.applyUnsafe(Record1(false, -1))))
        ) &&
        assert(Wrapper.r1_b.modifyOption(Wrapper.applyUnsafe(Record1(true, 0)), x => !x))(
          isSome(equalTo(Wrapper.applyUnsafe(Record1(false, 0))))
        )
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
        assert(Case3.v1_c1_d_right.modifyOption(Case3(Case4(Nil)), _ => 0.2))(isNone) &&
        assert(Wrapper.r1.modifyOption(Wrapper.applyUnsafe(Record1(true, 1)), _ => Record1(false, 2)))(isNone) &&
        assert(Wrapper.r1_b.modifyOption(Wrapper.applyUnsafe(Record1(true, 1)), x => !x))(isNone)
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
        assert(Case3.v1_c1.modifyOrFail(Case3(Case1(0.1)), _ => Case1(0.2)))(isRight(equalTo(Case3(Case1(0.2))))) &&
        assert(Wrapper.r1.modifyOrFail(Wrapper.applyUnsafe(Record1(true, 1)), _ => Record1(false, -1)))(
          isRight(equalTo(Wrapper.applyUnsafe(Record1(false, -1))))
        ) &&
        assert(Wrapper.r1_b.modifyOrFail(Wrapper.applyUnsafe(Record1(true, 0)), x => !x))(
          isRight(equalTo(Wrapper.applyUnsafe(Record1(false, 0))))
        )
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
                    full = DynamicOptic(Chunk(Case("Case2"), Field("r3"), Field("r1"))),
                    prefix = DynamicOptic(Chunk(Case("Case2"))),
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
                    full = DynamicOptic(Chunk(Field("r3"), Field("v1"), Case("Case1"))),
                    prefix = DynamicOptic(Chunk(Field("r3"), Field("v1"), Case("Case1"))),
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
                    full = DynamicOptic(Chunk(Case("Case2"), Field("r3"), Field("v1"), Case("Case1"))),
                    prefix = DynamicOptic(Chunk(Case("Case2"))),
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
                    full = DynamicOptic(Chunk(Case("Case3"), Field("v1"), Case("Variant2"), Case("Case4"))),
                    prefix = DynamicOptic(Chunk(Case("Case3"), Field("v1"), Case("Variant2"))),
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
                    full = DynamicOptic(Chunk(Case("Case3"), Field("v1"), Case("Case1"))),
                    prefix = DynamicOptic(Chunk(Case("Case3"))),
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
                    full = DynamicOptic(Chunk(Case("Case3"), Field("v1"), Case("Case1"))),
                    prefix = DynamicOptic(Chunk(Case("Case3"))),
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
                    full = DynamicOptic(Chunk(Case("Case3"), Field("v1"), Case("Case1"), Field("d"))),
                    prefix = DynamicOptic(Chunk(Case("Case3"))),
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
                    full = DynamicOptic(Chunk(Case("Case3"), Field("v1"))),
                    prefix = DynamicOptic(Chunk(Case("Case3"))),
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
                    full = DynamicOptic(Chunk(Field("v1"), Case("Case1"), Field("d"))),
                    prefix = DynamicOptic(Chunk(Field("v1"), Case("Case1"))),
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
                    full = DynamicOptic(Chunk(Field("v1"), Case("Case1"), Field("d"))),
                    prefix = DynamicOptic(Chunk(Field("v1"), Case("Case1"))),
                    actualValue = Case4(Nil)
                  ),
                  Nil
                )
              )
            )
          )
        ) &&
        assert(Wrapper.r1.modifyOrFail(Wrapper.applyUnsafe(Record1(true, 1)), _ => Record1(false, 1)))(
          isLeft(
            equalTo(
              OpticCheck(
                errors = ::(
                  WrappingError(
                    full = DynamicOptic(Chunk(Wrapped)),
                    prefix = DynamicOptic(Chunk(Wrapped)),
                    error = SchemaError.validationFailed("Unexpected 'Wrapper' value")
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
      test("evaluates schema expressions") {
        val emptyArray = Array.empty[String]
        assert((Collections.ab + 1).eval(Array(1: Byte)))(isRight(equalTo(Seq(2: Byte)))) &&
        assert((Collections.ash + 1).eval(Array(1: Short)))(isRight(equalTo(Seq(2: Short)))) &&
        assert((Collections.ai + 1).eval(Array(1)))(isRight(equalTo(Seq(2)))) &&
        assert((Collections.al + 1L).eval(Array(1L)))(isRight(equalTo(Seq(2L)))) &&
        assert((Collections.ad + 1.0).eval(Array(1.0)))(isRight(equalTo(Seq(2.0)))) &&
        assert((Collections.af + 1.0f).eval(Array(1.0f)))(isRight(equalTo(Seq(2.0f)))) &&
        assert((Collections.abi + BigInt(1)).eval(Array(BigInt(1))))(isRight(equalTo(Seq(BigInt(2))))) &&
        assert((Collections.abd + BigDecimal(1)).eval(Array(BigDecimal(1))))(isRight(equalTo(Seq(BigDecimal(2))))) &&
        assert(Case5.as.matches("a").eval(Case5(Set(), Array("a", "b"))))(isRight(equalTo(Seq(true, false)))) &&
        assert(Case5.as.concat("x").eval(Case5(Set(), Array("a", "b"))))(isRight(equalTo(Seq("ax", "bx")))) &&
        assert(Case5.as.length.eval(Case5(Set(), Array("a", "b"))))(isRight(equalTo(Seq(1, 1)))) &&
        assert(Case5.as.length.eval(Case5(Set(), emptyArray)))(
          isLeft(
            equalTo(
              OpticCheck(
                errors = ::(
                  EmptySequence(
                    full = DynamicOptic(
                      Vector(Field("as"), Elements)
                    ),
                    prefix = DynamicOptic(
                      Vector(Field("as"), Elements)
                    )
                  ),
                  Nil
                )
              )
            )
          )
        )
      },
      test("evaluates schema expressions to dynamic values") {
        val emptyArray = Array.empty[String]
        assert((Collections.abd === Collections.abd).evalDynamic(Array(BigDecimal(1))))(
          isRight(equalTo(Seq(DynamicValue.Primitive(PrimitiveValue.Boolean(true)))))
        ) &&
        assert(Case5.as.matches("a").evalDynamic(Case5(Set(), Array("a", "b"))))(
          isRight(
            equalTo(
              Seq(
                DynamicValue.Primitive(PrimitiveValue.Boolean(true)),
                DynamicValue.Primitive(PrimitiveValue.Boolean(false))
              )
            )
          )
        ) &&
        assert(Case5.as.concat("x").evalDynamic(Case5(Set(), Array("a", "b"))))(
          isRight(
            equalTo(
              Seq(
                DynamicValue.Primitive(PrimitiveValue.String("ax")),
                DynamicValue.Primitive(PrimitiveValue.String("bx"))
              )
            )
          )
        ) &&
        assert(Case5.as.length.evalDynamic(Case5(Set(), Array("a", "b"))))(
          isRight(
            equalTo(Seq(DynamicValue.Primitive(PrimitiveValue.Int(1)), DynamicValue.Primitive(PrimitiveValue.Int(1))))
          )
        ) &&
        assert((Case5.as === Case5.as).evalDynamic(Case5(Set(), emptyArray)))(
          isLeft(
            equalTo(
              OpticCheck(
                errors = ::(
                  EmptySequence(
                    full = DynamicOptic(Chunk(Field("as"), Elements)),
                    prefix = DynamicOptic(Chunk(Field("as"), Elements))
                  ),
                  Nil
                )
              )
            )
          )
        )
      },
      test("toDynamic") {
        assert(Record2.vi.toDynamic)(equalTo(DynamicOptic(Chunk(Field("vi"), Elements)))) &&
        assert(Collections.ai.toDynamic)(equalTo(DynamicOptic(Chunk(Elements)))) &&
        assert(Collections.mkc.toDynamic)(equalTo(DynamicOptic(Chunk(MapKeys)))) &&
        assert(Collections.mvs.toDynamic)(equalTo(DynamicOptic(Chunk(MapValues)))) &&
        assert(Collections.lc1.toDynamic)(equalTo(DynamicOptic(Chunk(Elements, Case("Case1")))))
      },
      test("checks prerequisites for creation") {
        ZIO
          .attempt(Traversal.atIndices(null: Reflect.Sequence.Bound[Int, Array], Seq(1)))
          .flip
          .map(e => assertTrue(e.isInstanceOf[Throwable])) &&
        ZIO
          .attempt(Traversal.atKeys(null: Reflect.Map.Bound[Int, Long, Map], Seq(1)))
          .flip
          .map(e => assertTrue(e.isInstanceOf[Throwable])) &&
        ZIO
          .attempt(Traversal.atKeys(Reflect.map(Reflect.int[Binding], Reflect.long[Binding]), Seq()))
          .flip
          .map(e => assertTrue(e.isInstanceOf[Throwable])) &&
        ZIO
          .attempt(Traversal.listValues(null))
          .flip
          .map(e => assertTrue(e.isInstanceOf[Throwable])) &&
        ZIO
          .attempt(Traversal.setValues(null))
          .flip
          .map(e => assertTrue(e.isInstanceOf[Throwable])) &&
        ZIO
          .attempt(Traversal.seqValues(null))
          .flip
          .map(e => assertTrue(e.isInstanceOf[Throwable])) &&
        ZIO
          .attempt(Traversal.vectorValues(null))
          .flip
          .map(e => assertTrue(e.isInstanceOf[Throwable])) &&
        ZIO
          .attempt(Traversal.mapKeys(null))
          .flip
          .map(e => assertTrue(e.isInstanceOf[Throwable])) &&
        ZIO
          .attempt(Traversal.mapValues(null))
          .flip
          .map(e => assertTrue(e.isInstanceOf[Throwable]))
      },
      test("optic macro requires sequence or map for creation") {
        ZIO.attempt {
          case class Test(a: Array[Map[Int, String]])

          object Test extends CompanionOptics[Test] {
            implicit val schema: Schema[Test] = Schema.derived
            val traversal                     = optic(_.a.each.each)
          }

          Test.traversal
        }.flip
          .map(e => assert(e.getMessage)(equalTo("Expected a sequence"))) &&
        ZIO.attempt {
          case class Test(a: Map[Set[Int], String])

          object Test extends CompanionOptics[Test] {
            implicit val schema: Schema[Test] = Schema.derived
            val traversal                     = optic(_.a.eachKey.eachKey)
          }

          Test.traversal
        }.flip
          .map(e => assert(e.getMessage)(equalTo("Expected a map"))) &&
        ZIO.attempt {
          case class Test(a: Map[Int, Set[String]])

          object Test extends CompanionOptics[Test] {
            implicit val schema: Schema[Test] = Schema.derived
            val traversal                     = optic(_.a.eachValue.eachValue)
          }

          Test.traversal
        }.flip
          .map(e => assert(e.getMessage)(equalTo("Expected a map")))
      },
      test("optic macro generates traversals for sequence or map") {
        object Test1 extends CompanionOptics[Vector[String]] {
          val traversal: Traversal[Vector[String], String] = $(_.each)
        }

        object Test2 extends CompanionOptics[Vector[String]] {
          val traversal: Traversal[Vector[String], String] = $(_.atIndices(1, 2))
        }

        object Test3 extends CompanionOptics[Map[Int, Long]] {
          val traversal: Traversal[Map[Int, Long], Int] = $(_.eachKey)
        }

        object Test4 extends CompanionOptics[Map[Int, Long]] {
          val traversal: Traversal[Map[Int, Long], Long] = $(_.eachValue)
        }

        object Test5 extends CompanionOptics[Map[Int, Long]] {
          val traversal: Traversal[Map[Int, Long], Long] = $(_.atKeys(1, 2))
        }

        object Test6 extends CompanionOptics[Vector[Vector[String]]] {
          val traversal: Traversal[Vector[Vector[String]], String] = $(_.each.at(1))
        }

        object Test7 extends CompanionOptics[Vector[Map[Int, Long]]] {
          val traversal: Traversal[Vector[Map[Int, Long]], Long] = $(_.each.atKey(1))
        }

        object Test8 extends CompanionOptics[Option[Vector[String]]] {
          val traversal: Optional[Option[Vector[String]], String] = $(_.when[Some[Vector[String]]].value.at(1))
        }

        object Test9 extends CompanionOptics[Option[Map[Int, Long]]] {
          val traversal: Optional[Option[Map[Int, Long]], Long] = $(_.when[Some[Map[Int, Long]]].value.atKey(1))
        }

        assert(Test1.traversal.fold[String](Vector("a", "b", "c"))("", _ + _))(equalTo("abc")) &&
        assert(Test2.traversal.fold[String](Vector("a", "b", "c"))("", _ + _))(equalTo("bc")) &&
        assert(Test3.traversal.fold[Int](Map(1 -> 1L, 2 -> 2L, 3 -> 3L))(0, _ + _))(equalTo(6)) &&
        assert(Test4.traversal.fold[Long](Map(1 -> 1L, 2 -> 2L, 3 -> 3L))(0, _ + _))(equalTo(6L)) &&
        assert(Test5.traversal.fold[Long](Map(1 -> 1L, 2 -> 2L, 3 -> 3L))(0, _ + _))(equalTo(3L)) &&
        assert(Test6.traversal.fold[String](Vector(Vector("a", "b")))("", _ + _))(equalTo("b")) &&
        assert(Test7.traversal.fold[Long](Vector(Map(1 -> 1L, 2 -> 2L)))(0, _ + _))(equalTo(1L)) &&
        assert(Test8.traversal.getOption(Option(Vector("a", "b"))))(isSome(equalTo("b"))) &&
        assert(Test9.traversal.getOption(Option(Map(1 -> 1L, 2 -> 2L))))(isSome(equalTo(1L)))
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
        assert(Collections.mvs: Any)(not(equalTo(""))) &&
        assert(
          Optional.at(Reflect.list(Reflect.list(Reflect.int[Binding])), 2)(Traversal.listValues(Reflect.int[Binding]))
        )(not(equalTo(Collections.alli_li)))
      },
      test("returns a source structure") {
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
        assert(Variant2.c3_v1_v2_c4_lr3.source)(equalTo(Variant2.reflect)) &&
        assert(Record4.vw_wr1_b.source)(equalTo(Record4.reflect)) &&
        assert(Case5.aias.source)(equalTo(Case5.reflect)) &&
        assert(Case6.aksmil.source)(equalTo(Case6.reflect))
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
        assert(Variant2.c3_v1_v2_c4_lr3.focus)(equalTo(Record3.reflect)) &&
        assert(Record4.vw_wr1_b.focus)(equalTo(Reflect.boolean[Binding])) &&
        assert(Case5.aias.focus)(equalTo(Reflect.string[Binding])) &&
        assert(Case6.aksmil.focus)(equalTo(Reflect.long[Binding]))
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
        assert(Case6.milk.check(Case6(Map(1 -> 2L))))(isNone) &&
        assert(Case6.milv.check(Case6(Map(1 -> 2L))))(isNone) &&
        assert(Variant2.c4_lr3.check(Case4(List(Record3(null, null, null)))))(isNone) &&
        assert(Variant2.c3_v1_v2_c4_lr3.check(Case3(Case4(List(Record3(null, null, null))))))(isNone) &&
        assert(Collections.aasasi_asi.check(ArraySeq(ArraySeq(1), ArraySeq(2), ArraySeq(3))))(isNone) &&
        assert(Collections.aiasasi_asi.check(ArraySeq(ArraySeq(1), ArraySeq(2), ArraySeq(3))))(isNone) &&
        assert(Collections.asasi_aasi.check(ArraySeq(ArraySeq(1, 2, 3))))(isNone) &&
        assert(Collections.alli_li.check(List(List(1), List(2), List(3))))(isNone) &&
        assert(Collections.ailli_li.check(List(List(1), List(2), List(3))))(isNone) &&
        assert(Collections.lli_ali.check(List(List(1, 2, 3))))(isNone) &&
        assert(Collections.akmill_ll.check(Map(1 -> List(1L, 2L, 3L))))(isNone) &&
        assert(Collections.aksmill_ll.check(Map(1 -> List(1L, 2L, 3L))))(isNone) &&
        assert(Collections.lmil_akmil.check(List(Map(1 -> 1L, 2 -> 2L, 3 -> 3L))))(isNone) &&
        assert(Collections.lmil_aksmil.check(List(Map(1 -> 1L, 2 -> 2L, 3 -> 3L))))(isNone) &&
        assert(Collections.lw_r1.check(List(Wrapper.applyUnsafe(Record1(true, 1)))))(isNone) &&
        assert(Collections.lw_r1_b.check(List(Wrapper.applyUnsafe(Record1(true, 1)))))(isNone)
      },
      test("checks collection values and returns an error if they will not be modified") {
        assert(Collections.mkv1_c1_d.check(Map(Case2(null) -> 1, Case6(null) -> 2)))(
          isSome(
            hasError(
              "During attempted access at .eachKey.when[Case1].d, encountered an unexpected case at .eachKey.when[Case1]: expected Case1, but got Case2\nDuring attempted access at .eachKey.when[Case1].d, encountered an unexpected case at .eachKey.when[Case1]: expected Case1, but got Variant2"
            )
          )
        ) &&
        assert(Collections.mkc.check(Map.empty[Char, String]))(
          isSome(hasError("During attempted access at .eachKey, encountered an empty map at .eachKey"))
        ) &&
        assert(Collections.mvs.check(Map.empty[Char, String]))(
          isSome(hasError("During attempted access at .eachValue, encountered an empty map at .eachValue"))
        ) &&
        assert(Variant2.c3_v1_v2_c4_lr3.check(Case3(Case4(Nil))))(
          isSome(
            hasError(
              "During attempted access at .when[Case3].v1.when[Variant2].when[Case4].lr3.each, encountered an empty sequence at .when[Case3].v1.when[Variant2].when[Case4].lr3.each"
            )
          )
        ) &&
        assert(Variant2.c3_v1_v2_c4_lr3.check(Case4(Nil)))(
          isSome(
            hasError(
              "During attempted access at .when[Case3].v1.when[Variant2].when[Case4].lr3.each, encountered an unexpected case at .when[Case3]: expected Case3, but got Case4"
            )
          )
        ) &&
        assert(Variant2.c4_lr3.check(Case3(Case1(0.1))))(
          isSome(
            hasError(
              "During attempted access at .when[Case4].lr3.each, encountered an unexpected case at .when[Case4]: expected Case4, but got Case3"
            )
          )
        ) &&
        assert(Collections.aasasi_asi.check(ArraySeq(ArraySeq())))(
          isSome(
            hasError(
              "During attempted access at .at(1).each, encountered a sequence out of bounds at .at(1): index is 1, but size is 1"
            )
          )
        ) &&
        assert(Collections.aiasasi_asi.check(ArraySeq(ArraySeq())))(
          isSome(
            hasError(
              "During attempted access at .atIndices(1, 2).each, encountered a sequence out of bounds at .atIndices(1, 2): index is 1, but size is 1"
            )
          )
        ) &&
        assert(Collections.asasi_aasi.check(ArraySeq(ArraySeq())))(
          isSome(
            hasError(
              "During attempted access at .each.at(1), encountered a sequence out of bounds at .each.at(1): index is 1, but size is 0"
            )
          )
        ) &&
        assert(Collections.alli_li.check(List(List())))(
          isSome(
            hasError(
              "During attempted access at .at(1).each, encountered a sequence out of bounds at .at(1): index is 1, but size is 1"
            )
          )
        ) &&
        assert(Collections.ailli_li.check(List(List())))(
          isSome(
            hasError(
              "During attempted access at .atIndices(1).each, encountered a sequence out of bounds at .atIndices(1): index is 1, but size is 1"
            )
          )
        ) &&
        assert(Collections.lli_ali.check(List(List())))(
          isSome(
            hasError(
              "During attempted access at .each.at(1), encountered a sequence out of bounds at .each.at(1): index is 1, but size is 0"
            )
          )
        ) &&
        assert(Collections.akmill_ll.check(Map()))(
          isSome(
            hasError("During attempted access at .atKey(1).each, encountered missing key at .atKey(1)")
          )
        ) &&
        assert(Collections.aksmill_ll.check(Map()))(
          isSome(
            hasError("During attempted access at .atKeys(1).each, encountered missing key at .atKeys(1)")
          )
        ) &&
        assert(Collections.lmil_akmil.check(List(Map())))(
          isSome(
            hasError("During attempted access at .each.atKey(1), encountered missing key at .each.atKey(1)")
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
        assert(Collections.asbl.modify(ArraySeq(true, false, true), x => !x))(equalTo(ArraySeq(false, true, false))) &&
        assert(Collections.asb.modify(ArraySeq(1: Byte, 2: Byte, 3: Byte), x => (x + 1).toByte))(
          equalTo(ArraySeq(2: Byte, 3: Byte, 4: Byte))
        ) &&
        assert(Collections.assh.modify(ArraySeq(1: Short, 2: Short, 3: Short), x => (x + 1).toShort))(
          equalTo(ArraySeq(2: Short, 3: Short, 4: Short))
        ) &&
        assert(Collections.asi.modify(ArraySeq(1, 2, 3), _ + 1))(equalTo(ArraySeq(2, 3, 4))) &&
        assert(Collections.asl.modify(ArraySeq(1L, 2L, 3L), _ + 1L))(equalTo(ArraySeq(2L, 3L, 4L))) &&
        assert(Collections.asd.modify(ArraySeq(1.0, 2.0, 3.0), _ + 1.0))(equalTo(ArraySeq(2.0, 3.0, 4.0))) &&
        assert(Collections.asf.modify(ArraySeq(1.0f, 2.0f, 3.0f), _ + 1.0f))(equalTo(ArraySeq(2.0f, 3.0f, 4.0f))) &&
        assert(Collections.asc.modify(ArraySeq('a', 'b', 'c'), _.toUpper))(equalTo(ArraySeq('A', 'B', 'C'))) &&
        assert(Collections.ass.modify(ArraySeq("1", "2", "3"), _ + "x"))(equalTo(ArraySeq("1x", "2x", "3x"))) &&
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
        assert(Case6.milk.modify(Case6(Map(1 -> 2L)), _ + 1))(equalTo(Case6(Map(2 -> 2L)))) &&
        assert(Case6.milv.modify(Case6(Map(1 -> 2L)), _ + 1L))(equalTo(Case6(Map(1 -> 3L)))) &&
        assert(Variant2.c4_lr3.modify(Case4(List(Record3(null, null, null))), _ => null))(equalTo(Case4(List(null)))) &&
        assert(Variant2.c3_v1_v2_c4_lr3.modify(Case3(Case4(List(Record3(null, null, null)))), _ => null))(
          equalTo(Case3(Case4(List(null))))
        ) &&
        assert(Collections.aasasi_asi.modify(ArraySeq(ArraySeq(1), ArraySeq(2), ArraySeq(3)), _ + 1))(
          equalTo(ArraySeq(ArraySeq(1), ArraySeq(3), ArraySeq(3)))
        ) &&
        assert(Collections.aiasasi_asi.modify(ArraySeq(ArraySeq(1), ArraySeq(2), ArraySeq(3)), _ + 1))(
          equalTo(ArraySeq(ArraySeq(1), ArraySeq(3), ArraySeq(4)))
        ) &&
        assert(Collections.asasb_aasb.modify(ArraySeq(ArraySeq(1: Byte, 2: Byte, 3: Byte)), x => (x + 1).toByte))(
          equalTo(ArraySeq(ArraySeq(1: Byte, 3: Byte, 3: Byte)))
        ) &&
        assert(Collections.asasbl_aasbl.modify(ArraySeq(ArraySeq(true, true, true)), x => !x))(
          equalTo(ArraySeq(ArraySeq(true, false, true)))
        ) &&
        assert(Collections.asassh_aassh.modify(ArraySeq(ArraySeq(1: Short, 2: Short, 3: Short)), x => (x + 1).toShort))(
          equalTo(ArraySeq(ArraySeq(1: Short, 3: Short, 3: Short)))
        ) &&
        assert(Collections.asasc_aasc.modify(ArraySeq(ArraySeq('a', 'b', 'c')), _.toUpper))(
          equalTo(ArraySeq(ArraySeq('a', 'B', 'c')))
        ) &&
        assert(Collections.asasi_aasi.modify(ArraySeq(ArraySeq(1, 2, 3)), _ + 1))(
          equalTo(ArraySeq(ArraySeq(1, 3, 3)))
        ) &&
        assert(Collections.asasf_aasf.modify(ArraySeq(ArraySeq(1.0f, 2.0f, 3.0f)), _ + 1.0f))(
          equalTo(ArraySeq(ArraySeq(1.0f, 3.0f, 3.0f)))
        ) &&
        assert(Collections.asasl_aasl.modify(ArraySeq(ArraySeq(1L, 2L, 3L)), _ + 1L))(
          equalTo(ArraySeq(ArraySeq(1L, 3L, 3L)))
        ) &&
        assert(Collections.asasd_aasd.modify(ArraySeq(ArraySeq(1.0, 2.0, 3.0)), _ + 1.0))(
          equalTo(ArraySeq(ArraySeq(1.0, 3.0, 3.0)))
        ) &&
        assert(Collections.asass_aass.modify(ArraySeq(ArraySeq("a", "b", "c")), _ + "x"))(
          equalTo(ArraySeq(ArraySeq("a", "bx", "c")))
        ) &&
        assert(Collections.asasb_aiasb.modify(ArraySeq(ArraySeq(1: Byte, 2: Byte, 3: Byte)), x => (x + 1).toByte))(
          equalTo(ArraySeq(ArraySeq(1: Byte, 3: Byte, 4: Byte)))
        ) &&
        assert(Collections.asasbl_aiasbl.modify(ArraySeq(ArraySeq(true, true, true)), x => !x))(
          equalTo(ArraySeq(ArraySeq(true, false, false)))
        ) &&
        assert(
          Collections.asassh_aiassh.modify(ArraySeq(ArraySeq(1: Short, 2: Short, 3: Short)), x => (x + 1).toShort)
        )(
          equalTo(ArraySeq(ArraySeq(1: Short, 3: Short, 4: Short)))
        ) &&
        assert(Collections.asasc_aiasc.modify(ArraySeq(ArraySeq('a', 'b', 'c')), _.toUpper))(
          equalTo(ArraySeq(ArraySeq('a', 'B', 'C')))
        ) &&
        assert(Collections.asasi_aiasi.modify(ArraySeq(ArraySeq(1, 2, 3)), _ + 1))(
          equalTo(ArraySeq(ArraySeq(1, 3, 4)))
        ) &&
        assert(Collections.asasf_aiasf.modify(ArraySeq(ArraySeq(1.0f, 2.0f, 3.0f)), _ + 1.0f))(
          equalTo(ArraySeq(ArraySeq(1.0f, 3.0f, 4.0f)))
        ) &&
        assert(Collections.asasl_aiasl.modify(ArraySeq(ArraySeq(1L, 2L, 3L)), _ + 1L))(
          equalTo(ArraySeq(ArraySeq(1L, 3L, 4L)))
        ) &&
        assert(Collections.asasd_aiasd.modify(ArraySeq(ArraySeq(1.0, 2.0, 3.0)), _ + 1.0))(
          equalTo(ArraySeq(ArraySeq(1.0, 3.0, 4.0)))
        ) &&
        assert(Collections.asass_aiass.modify(ArraySeq(ArraySeq("a", "b", "c")), _ + "x"))(
          equalTo(ArraySeq(ArraySeq("a", "bx", "cx")))
        ) &&
        assert(Collections.alli_li.modify(List(List(1), List(2), List(3)), _ + 1))(
          equalTo(List(List(1), List(3), List(3)))
        ) &&
        assert(Collections.ailli_li.modify(List(List(1), List(2), List(3)), _ + 1))(
          equalTo(List(List(1), List(3), List(3)))
        ) &&
        assert(Collections.lli_ali.modify(List(List(1, 2, 3)), _ + 1))(equalTo(List(List(1, 3, 3)))) &&
        assert(Collections.akmill_ll.modify(Map(1 -> List(1L), 2 -> List(2L), 3 -> List(3L)), _ + 1L))(
          equalTo(Map(1 -> List(2L), 2 -> List(2L), 3 -> List(3L)))
        ) &&
        assert(Collections.aksmill_ll.modify(Map(1 -> List(1L), 2 -> List(2L), 3 -> List(3L)), _ + 1L))(
          equalTo(Map(1 -> List(2L), 2 -> List(2L), 3 -> List(3L)))
        ) &&
        assert(Collections.lmil_akmil.modify(List(Map(1 -> 1L), Map(2 -> 2L), Map(3 -> 3L)), _ + 1L))(
          equalTo(List(Map(1 -> 2L), Map(2 -> 2L), Map(3 -> 3L)))
        ) &&
        assert(Collections.lmil_aksmil.modify(List(Map(1 -> 1L), Map(2 -> 2L), Map(3 -> 3L)), _ + 1L))(
          equalTo(List(Map(1 -> 2L), Map(2 -> 3L), Map(3 -> 3L)))
        ) &&
        assert(Collections.lw_r1.modify(List(Wrapper.applyUnsafe(Record1(true, 1))), _ => Record1(false, -1)))(
          equalTo(List(Wrapper.applyUnsafe(Record1(false, -1))))
        ) &&
        assert(Collections.lw_r1_b.modify(List(Wrapper.applyUnsafe(Record1(true, 0))), x => !x))(
          equalTo(List(Wrapper.applyUnsafe(Record1(false, 0))))
        )
      },
      test("doesn't modify collection values for non-matching cases") {
        assert(Collections.mkv1_c1_d.modify(Map(Case2(null) -> 1, Case6(null) -> 2), _ + 0.4))(
          equalTo(Map[Variant1, Int](Case2(null) -> 1, Case6(null) -> 2))
        ) &&
        assert(Variant2.c3_v1_v2_c4_lr3.modify(Case4(Nil), _ => null))(equalTo(Case4(Nil))) &&
        assert(Variant2.c4_lr3.modify(Case3(Case1(0.1)), _ => null))(equalTo(Case3(Case1(0.1)))) &&
        assert(Collections.lw_r1.modify(List(Wrapper.applyUnsafe(Record1(true, 1))), _ => Record1(false, 1)))(
          equalTo(List(Wrapper.applyUnsafe(Record1(true, 1))))
        ) &&
        assert(Collections.lw_r1_b.modify(List(Wrapper.applyUnsafe(Record1(true, 1))), x => !x))(
          equalTo(List(Wrapper.applyUnsafe(Record1(true, 1))))
        )
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
        ) &&
        assert(Collections.lw_r1.modifyOption(List(Wrapper.applyUnsafe(Record1(true, 1))), _ => Record1(false, -1)))(
          isSome(equalTo(List(Wrapper.applyUnsafe(Record1(false, -1)))))
        ) &&
        assert(Collections.lw_r1_b.modifyOption(List(Wrapper.applyUnsafe(Record1(true, 0))), x => !x))(
          isSome(equalTo(List(Wrapper.applyUnsafe(Record1(false, 0)))))
        )
      },
      test("doesn't modify collection values for non-matching cases and returns none") {
        assert(Collections.aasasi_asi.modifyOption(ArraySeq(ArraySeq(1)), _ + 1))(isNone) &&
        assert(Collections.aiasasi_asi.modifyOption(ArraySeq(ArraySeq(1)), _ + 1))(isNone) &&
        assert(Collections.asasi_aasi.modifyOption(ArraySeq(ArraySeq(1)), _ + 1))(isNone) &&
        assert(Collections.asasi_aiasi.modifyOption(ArraySeq(ArraySeq(1)), _ + 1))(isNone) &&
        assert(Collections.alli_li.modifyOption(List(List(1)), _ + 1))(isNone) &&
        assert(Collections.ailli_li.modifyOption(List(List(1)), _ + 1))(isNone) &&
        assert(Collections.akmill_ll.modifyOption(Map(2 -> List(2L)), _ + 1L))(isNone) &&
        assert(Collections.aksmill_ll.modifyOption(Map(2 -> List(2L)), _ + 1L))(isNone) &&
        assert(Collections.mkv1_c1_d.modifyOption(Map(Case2(null) -> 1, Case6(null) -> 2), _ + 0.4))(isNone) &&
        assert(Variant2.c3_v1_v2_c4_lr3.modifyOption(Case4(Nil), _ => null))(isNone) &&
        assert(Variant2.c4_lr3.modifyOption(Case3(Case1(0.1)), _ => null))(isNone) &&
        assert(Collections.lw_r1.modifyOption(List(Wrapper.applyUnsafe(Record1(true, 1))), _ => Record1(false, 1)))(
          isNone
        ) &&
        assert(Collections.lw_r1_b.modifyOption(List(Wrapper.applyUnsafe(Record1(true, 1))), x => !x))(isNone)
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
        ) &&
        assert(Collections.lw_r1.modifyOrFail(List(Wrapper.applyUnsafe(Record1(true, 1))), _ => Record1(false, -1)))(
          isRight(equalTo(List(Wrapper.applyUnsafe(Record1(false, -1)))))
        ) &&
        assert(Collections.lw_r1_b.modifyOrFail(List(Wrapper.applyUnsafe(Record1(true, 0))), x => !x))(
          isRight(equalTo(List(Wrapper.applyUnsafe(Record1(false, 0)))))
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
                      Chunk(Case("Case3"), Field("v1"), Case("Variant2"), Case("Case4"), Field("lr3"), Elements)
                    ),
                    prefix = DynamicOptic(
                      Chunk(Case("Case3"), Field("v1"), Case("Variant2"), Case("Case4"), Field("lr3"), Elements)
                    )
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
                      Chunk(Case("Case3"), Field("v1"), Case("Variant2"), Case("Case4"), Field("lr3"), Elements)
                    ),
                    prefix = DynamicOptic(Chunk(Case("Case3"))),
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
                    full = DynamicOptic(Chunk(Case("Case4"), Field("lr3"), Elements)),
                    prefix = DynamicOptic(Chunk(Case("Case4"))),
                    actualValue = Case3(Case1(0.1))
                  ),
                  Nil
                )
              )
            )
          )
        ) &&
        assert(Collections.lw_r1.modifyOrFail(List(Wrapper.applyUnsafe(Record1(true, 1))), _ => Record1(false, 1)))(
          isLeft(
            hasError(
              "During attempted access at .each.wrapped, encountered an error at .each.wrapped: Unexpected 'Wrapper' value"
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
        )(equalTo(Record3(null, null, null))) &&
        assert(Collections.aasasi_asi.fold[Int](ArraySeq(ArraySeq(1), ArraySeq(2), ArraySeq(3)))(0, _ + _))(
          equalTo(2)
        ) &&
        assert(Collections.aiasasi_asi.fold[Int](ArraySeq(ArraySeq(1), ArraySeq(2), ArraySeq(3)))(0, _ + _))(
          equalTo(5)
        ) &&
        assert(Collections.asasb_aasb.fold[Int](ArraySeq(ArraySeq(1: Byte, 2: Byte, 3: Byte)))(0, _ + _))(equalTo(2)) &&
        assert(Collections.asasbl_aasbl.fold[Boolean](ArraySeq(ArraySeq(true, true, true)))(false, _ || _))(
          equalTo(true)
        ) &&
        assert(Collections.asassh_aassh.fold[Int](ArraySeq(ArraySeq(1: Short, 2: Short, 3: Short)))(0, _ + _))(
          equalTo(2)
        ) &&
        assert(Collections.asasc_aasc.fold[String](ArraySeq(ArraySeq('1', '2', '3')))("0", _ + _.toString))(
          equalTo("02")
        ) &&
        assert(Collections.asasi_aasi.fold[Int](ArraySeq(ArraySeq(1, 2, 3)))(0, _ + _))(equalTo(2)) &&
        assert(Collections.asasf_aasf.fold[Float](ArraySeq(ArraySeq(1.0f, 2.0f, 3.0f)))(0.0f, _ + _))(equalTo(2.0f)) &&
        assert(Collections.asasl_aasl.fold[Long](ArraySeq(ArraySeq(1L, 2L, 3L)))(0L, _ + _))(equalTo(2L)) &&
        assert(Collections.asasd_aasd.fold[Double](ArraySeq(ArraySeq(1.0, 2.0, 3.0)))(0.0, _ + _))(equalTo(2.0)) &&
        assert(Collections.asass_aass.fold[String](ArraySeq(ArraySeq("1", "2", "3")))("0", _ + _))(equalTo("02")) &&
        assert(Collections.asasb_aiasb.fold[Int](ArraySeq(ArraySeq(1: Byte, 2: Byte, 3: Byte)))(0, _ + _))(
          equalTo(5)
        ) &&
        assert(Collections.asasbl_aiasbl.fold[Boolean](ArraySeq(ArraySeq(true, true, true)))(false, _ || _))(
          equalTo(true)
        ) &&
        assert(Collections.asassh_aiassh.fold[Int](ArraySeq(ArraySeq(1: Short, 2: Short, 3: Short)))(0, _ + _))(
          equalTo(5)
        ) &&
        assert(Collections.asasc_aiasc.fold[String](ArraySeq(ArraySeq('1', '2', '3')))("0", _ + _.toString))(
          equalTo("023")
        ) &&
        assert(Collections.asasi_aiasi.fold[Int](ArraySeq(ArraySeq(1, 2, 3)))(0, _ + _))(equalTo(5)) &&
        assert(Collections.asasi_aiasi.fold[Long](ArraySeq(ArraySeq(1, 2, 3)))(0L, _ + _))(equalTo(5L)) &&
        assert(Collections.asasi_aiasi.fold[Double](ArraySeq(ArraySeq(1, 2, 3)))(0.0, _ + _))(equalTo(5.0)) &&
        assert(Collections.asasi_aiasi.fold[String](ArraySeq(ArraySeq(1, 2, 3)))("0", _ + _))(equalTo("023")) &&
        assert(Collections.asasl_aiasl.fold[Long](ArraySeq(ArraySeq(1L, 2L, 3L)))(0L, _ + _))(equalTo(5L)) &&
        assert(Collections.asasl_aiasl.fold[Int](ArraySeq(ArraySeq(1L, 2L, 3L)))(0, _ + _.toInt))(equalTo(5)) &&
        assert(Collections.asasl_aiasl.fold[Double](ArraySeq(ArraySeq(1L, 2L, 3L)))(0.0, _ + _))(equalTo(5.0)) &&
        assert(Collections.asasl_aiasl.fold[String](ArraySeq(ArraySeq(1L, 2L, 3L)))("0", _ + _))(equalTo("023")) &&
        assert(Collections.asasd_aiasd.fold[Double](ArraySeq(ArraySeq(1.0, 2.0, 3.0)))(0.0, _ + _))(equalTo(5.0)) &&
        assert(Collections.asasd_aiasd.fold[Int](ArraySeq(ArraySeq(1.0, 2.0, 3.0)))(0, _ + _.toInt))(equalTo(5)) &&
        assert(Collections.asasd_aiasd.fold[Long](ArraySeq(ArraySeq(1.0, 2.0, 3.0)))(0L, _ + _.toLong))(equalTo(5L)) &&
        assert(Collections.asasd_aiasd.fold[String](ArraySeq(ArraySeq(1.0, 2.0, 3.0)))("0", _ + _.toInt))(
          equalTo("023")
        ) &&
        assert(Collections.asasf_aiasf.fold[Float](ArraySeq(ArraySeq(1.0f, 2.0f, 3.0f)))(0.0f, _ + _))(equalTo(5.0f)) &&
        assert(Collections.asass_aiass.fold[String](ArraySeq(ArraySeq("1", "2", "3")))("0", _ + _))(equalTo("023")) &&
        assert(Collections.alli_li.fold[Int](List(List(1), List(2), List(3)))(0, _ + _))(equalTo(2)) &&
        assert(Collections.ailli_li.fold[Int](List(List(1), List(2), List(3)))(0, _ + _))(equalTo(2)) &&
        assert(Collections.lli_ali.fold[Int](List(List(1, 2, 3)))(0, _ + _))(equalTo(2)) &&
        assert(Collections.akmill_ll.fold[Long](Map(1 -> List(1L), 2 -> List(2L), 3 -> List(3L)))(0L, _ + _))(
          equalTo(1L)
        ) &&
        assert(Collections.aksmill_ll.fold[Long](Map(1 -> List(1L), 2 -> List(2L), 3 -> List(3L)))(0L, _ + _))(
          equalTo(1L)
        ) &&
        assert(Collections.lmil_akmil.fold[Long](List(Map(1 -> 1L), Map(2 -> 2L), Map(3 -> 3L)))(0L, _ + _))(
          equalTo(1L)
        ) &&
        assert(Collections.lmil_aksmil.fold[Long](List(Map(1 -> 1L), Map(2 -> 2L), Map(3 -> 3L)))(0L, _ + _))(
          equalTo(3L)
        ) &&
        assert(Collections.lw_r1.fold[Record1](List(Wrapper.applyUnsafe(Record1(true, 1))))(null, (_, x) => x))(
          equalTo(Record1(true, 1))
        ) &&
        assert(Collections.lw_r1_b.fold[Boolean](List(Wrapper.applyUnsafe(Record1(true, 1))))(false, _ && _))(
          equalTo(false)
        )
      },
      test("folds zero values for non-matching cases") {
        assert(Collections.aasasi_asi.fold[Int](ArraySeq(ArraySeq()))(0, _ + _))(equalTo(0)) &&
        assert(Collections.aiasasi_asi.fold[Int](ArraySeq(ArraySeq()))(0, _ + _))(equalTo(0)) &&
        assert(Collections.asasi_aasi.fold[Int](ArraySeq(ArraySeq()))(0, _ + _))(equalTo(0)) &&
        assert(Collections.asasi_aiasi.fold[Int](ArraySeq(ArraySeq()))(0, _ + _))(equalTo(0)) &&
        assert(Collections.alli_li.fold[Int](List(List()))(0, _ + _))(equalTo(0)) &&
        assert(Collections.ailli_li.fold[Int](List(List()))(0, _ + _))(equalTo(0)) &&
        assert(Collections.akmill_ll.fold[Long](Map())(0L, _ + _))(equalTo(0L)) &&
        assert(Collections.aksmill_ll.fold[Long](Map())(0L, _ + _))(equalTo(0L)) &&
        assert(Collections.lli_ali.fold[Int](List(List()))(0, _ + _))(equalTo(0)) &&
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
      test("doesn't reduce for non-matching cases returning an error") {
        assert(Variant2.c3_v1_v2_c4_lr3.reduceOrFail(Case4(Nil))((_, x) => x))(
          isLeft(
            equalTo(
              OpticCheck(
                errors = ::(
                  UnexpectedCase(
                    expectedCase = "Case3",
                    actualCase = "Case4",
                    full = DynamicOptic(
                      Chunk(Case("Case3"), Field("v1"), Case("Variant2"), Case("Case4"), Field("lr3"), Elements)
                    ),
                    prefix = DynamicOptic(Chunk(Case("Case3"))),
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
                    full = DynamicOptic(Chunk(Case("Case4"), Field("lr3"), Elements)),
                    prefix = DynamicOptic(Chunk(Case("Case4"))),
                    actualValue = Case3(Case1(0.1))
                  ),
                  Nil
                )
              )
            )
          )
        )
      }
    ),
    suite("Primitive Array ClassTag issues")(
      test("modifySeqAt with Array[Int] preserves primitive array type") {
        val record                            = RecordWithPrimitiveArray(Array(1, 2, 3))
        val result                            = RecordWithPrimitiveArray.intAt1.modify(record, _ + 10)
        val componentType                     = result.ints.getClass.getComponentType
        val isPrimitive                       = componentType.isPrimitive
        def sumIntArray(arr: Array[Int]): Int = arr.sum
        val sum                               = sumIntArray(result.ints)
        assertTrue(isPrimitive, componentType == classOf[Int], sum == 16) &&
        assert(result.ints.toList)(equalTo(List(1, 12, 3)))
      },
      test("modifySeqAtIndices with Array[Int] preserves primitive array type") {
        val record        = RecordWithPrimitiveArray(Array(1, 2, 3))
        val result        = RecordWithPrimitiveArray.intsAtIndices.modify(record, _ + 10)
        val componentType = result.ints.getClass.getComponentType
        assertTrue(componentType == classOf[Int]) &&
        assert(result.ints.toList)(equalTo(List(11, 2, 13)))
      },
      test("modifySeq via traversal with Array[Int] preserves primitive array type") {
        val record        = RecordWithPrimitiveArray(Array(1, 2, 3))
        val result        = RecordWithPrimitiveArray.ints.modify(record, _ + 10)
        val componentType = result.ints.getClass.getComponentType
        assertTrue(componentType == classOf[Int]) &&
        assert(result.ints.toList)(equalTo(List(11, 12, 13)))
      }
    )
  )

  private[this] def hasError(message: String) =
    hasField[OpticCheck, String]("message", _.message, containsString(message))
}

object OpticSpecTypes {

  case class Record1(b: Boolean, f: Float)

  object Record1 extends CompanionOptics[Record1] {
    implicit val schema: Schema[Record1]       = Schema.derived
    val reflect: Reflect.Record.Bound[Record1] = schema.reflect.asRecord.get
    val b: Lens[Record1, Boolean]              = optic(_.b)
    val f: Lens[Record1, Float]                = optic(_.f)
  }

  case class Record2(l: Long, vi: Vector[Int], r1: Record1)

  object Record2 extends CompanionOptics[Record2] {
    implicit val schema: Schema[Record2]       = Schema.derived
    val reflect: Reflect.Record.Bound[Record2] = schema.reflect.asRecord.get
    val l: Lens[Record2, Long]                 = optic(_.l)
    val vi: Traversal[Record2, Int]            = optic(_.vi.each)
    val r1: Lens[Record2, Record1]             = optic(_.r1)
    val r1_b: Lens[Record2, Boolean]           = optic(_.r1.b)
    val r1_f: Lens[Record2, Float]             = optic(_.r1.f)
  }

  case class Record3(r1: Record1, r2: Record2, v1: Variant1)

  object Record3 extends CompanionOptics[Record3] {
    implicit val schema: Schema[Record3]       = Schema.derived
    val reflect: Reflect.Record.Bound[Record3] = schema.reflect.asRecord.get
    val r1: Lens[Record3, Record1]             = optic(_.r1)
    val r2: Lens[Record3, Record2]             = optic(_.r2)
    val v1: Lens[Record3, Variant1]            = optic(_.v1)
    val r2_r1_b_left: Lens[Record3, Boolean]   = r2(Record2.r1)(Record1.b)
    val r2_r1_b_right: Lens[Record3, Boolean]  = r2(Record2.r1(Record1.b))
    lazy val v1_c1: Optional[Record3, Case1]   = optic(_.v1.when[Case1])
  }

  sealed trait Variant1

  object Variant1 extends CompanionOptics[Variant1] {
    implicit val schema: Schema[Variant1]                = Schema.derived
    val reflect: Reflect.Variant.Bound[Variant1]         = schema.reflect.asVariant.get
    val c1: Prism[Variant1, Case1]                       = optic(_.when[Case1])
    val c2: Prism[Variant1, Case2]                       = optic(_.when[Case2])
    val v2: Prism[Variant1, Variant2]                    = optic(_.when[Variant2])
    val v2_c3: Prism[Variant1, Case3]                    = optic(_.when[Variant2].when[Case3])
    val v2_c4: Prism[Variant1, Case4]                    = optic(_.when[Variant2].when[Case4])
    val v2_v3_c5_left: Prism[Variant1, Case5]            = v2(Variant2.v3)(Variant3.c5)
    val v2_v3_c5_right: Prism[Variant1, Case5]           = v2(Variant2.v3(Variant3.c5))
    val v2_c4_lr3: Traversal[Variant1, Record3]          = optic(_.when[Variant2].when[Case4].lr3.each)
    val v2_c3_v1: Optional[Variant1, Variant1]           = optic(_.when[Variant2].when[Case3].v1)
    val c1_d: Optional[Variant1, Double]                 = optic(_.when[Case1].d)
    val c2_r3: Optional[Variant1, Record3]               = optic(_.when[Case2].r3)
    val c2_r3_r1: Optional[Variant1, Record1]            = optic(_.when[Case2].r3.r1)
    val c2_r3_r2_r1_b_left: Optional[Variant1, Boolean]  = c2_r3(Record3.r2_r1_b_left)
    val c2_r3_r2_r1_b_right: Optional[Variant1, Boolean] = c2_r3(Record3.r2_r1_b_right)
    lazy val c2_r3_v1_c1: Optional[Variant1, Case1]      = optic(_.when[Case2].r3.v1.when[Case1])
  }

  case class Case1(d: Double) extends Variant1

  object Case1 extends CompanionOptics[Case1] {
    implicit val schema: Schema[Case1]       = Schema.derived
    val reflect: Reflect.Record.Bound[Case1] = schema.reflect.asRecord.get
    val d: Lens[Case1, Double]               = optic(_.d)
  }

  case class Case2(r3: Record3) extends Variant1

  object Case2 extends CompanionOptics[Case2] {
    implicit val schema: Schema[Case2]        = Schema.derived
    val reflect: Reflect.Record.Bound[Case2]  = schema.reflect.asRecord.get
    val r3: Lens[Case2, Record3]              = optic(_.r3)
    lazy val r3_v1_c1: Optional[Case2, Case1] = optic(_.r3.v1.when[Case1])
  }

  sealed trait Variant2 extends Variant1

  object Variant2 extends CompanionOptics[Variant2] {
    implicit val schema: Schema[Variant2]                  = Schema.derived
    val reflect: Reflect.Variant.Bound[Variant2]           = schema.reflect.asVariant.get
    val c3: Prism[Variant2, Case3]                         = optic(_.when[Case3])
    val c4: Prism[Variant2, Case4]                         = optic(_.when[Case4])
    val v3: Prism[Variant2, Variant3]                      = optic(_.when[Variant3])
    val c4_lr3: Traversal[Variant2, Record3]               = c4(Case4.lr3)
    lazy val c3_v1: Optional[Variant2, Variant1]           = optic(_.when[Case3].v1)
    lazy val c3_v1_c1_left: Optional[Variant2, Case1]      = c3(Case3.v1)(Variant1.c1)
    lazy val c3_v1_c1_right: Optional[Variant2, Case1]     = c3(Case3.v1_c1)
    lazy val c3_v1_c1_d_left: Optional[Variant2, Double]   = c3(Case3.v1)(Variant1.c1_d)
    lazy val c3_v1_c1_d_right: Optional[Variant2, Double]  = c3_v1(Variant1.c1_d)
    lazy val c3_v1_v2: Optional[Variant2, Variant2]        = optic(_.when[Case3].v1.when[Variant2])
    lazy val c3_v1_v2_c4_lr3: Traversal[Variant2, Record3] = optic(_.when[Case3].v1.when[Variant2].when[Case4].lr3.each)
    lazy val c3_v1_v2_c4: Optional[Variant2, Case4]        = optic(_.when[Case3].v1.when[Variant2].when[Case4])
  }

  case class Case3(v1: Variant1) extends Variant2

  object Case3 extends CompanionOptics[Case3] {
    implicit val schema: Schema[Case3]                 = Schema.derived
    val reflect: Reflect.Record.Bound[Case3]           = schema.reflect.asRecord.get
    val v1: Lens[Case3, Variant1]                      = optic(_.v1)
    lazy val v1_c1: Optional[Case3, Case1]             = optic(_.v1.when[Case1])
    lazy val v1_c1_d_left: Optional[Case3, Double]     = v1(Variant1.c1)(Case1.d)
    lazy val v1_c1_d_right: Optional[Case3, Double]    = v1(Variant1.c1_d)
    lazy val v1_v2: Optional[Case3, Variant2]          = optic(_.v1.when[Variant2])
    lazy val v1_v2_c3_v1_v2: Optional[Case3, Variant2] = optic(_.v1.when[Variant2].when[Case3].v1.when[Variant2])
  }

  case class Case4(lr3: List[Record3]) extends Variant2

  object Case4 extends CompanionOptics[Case4] {
    implicit val schema: Schema[Case4]       = Schema.derived
    val reflect: Reflect.Record.Bound[Case4] = schema.reflect.asRecord.get
    val lr3: Traversal[Case4, Record3]       = optic(_.lr3.each)
    val lr3_r2: Traversal[Case4, Record2]    = optic(_.lr3.each.r2)
    val lr3_r2_r1: Traversal[Case4, Record1] = optic(_.lr3.each.r2.r1)
  }

  sealed trait Variant3 extends Variant2

  object Variant3 extends CompanionOptics[Variant3] {
    val reflect: Reflect.Deferred.Bound[Variant3] = Reflect.Deferred(() => Schema.derived[Variant3].reflect)
    implicit val schema: Schema[Variant3]         = Schema(reflect) // to test prism derivation for Reflect.Deferred
    val c5: Prism[Variant3, Case5]                = optic(_.when[Case5])
  }

  case class Case5(si: Set[Int], as: Array[String]) extends Variant3

  object Case5 extends CompanionOptics[Case5] {
    val reflect: Reflect.Deferred.Bound[Case5] = Reflect.Deferred(() => Schema.derived[Case5].reflect)
    implicit val schema: Schema[Case5]         = Schema(reflect) // to test lens derivation for Reflect.Deferred
    val si: Traversal[Case5, Int]              = optic(_.si.each)
    val as: Traversal[Case5, String]           = optic(_.as.each)
    val aas: Optional[Case5, String]           = optic(_.as.at(1))
    val aias: Traversal[Case5, String]         = optic(_.as.atIndices(1, 2))
  }

  case class Case6(mil: Map[Int, Long]) extends Variant3

  object Case6 extends CompanionOptics[Case6] {
    implicit val schema: Schema[Case6]       = Schema.derived
    val reflect: Reflect.Record.Bound[Case6] = schema.reflect.asRecord.get
    val milk: Traversal[Case6, Int]          = optic(_.mil.eachKey)
    val milv: Traversal[Case6, Long]         = optic(_.mil.eachValue)
    val akmil: Optional[Case6, Long]         = optic(_.mil.atKey(1))
    val aksmil: Traversal[Case6, Long]       = optic(_.mil.atKeys(1, 2))
  }

  case class Box1(l: Long) extends AnyVal

  object Box1 extends CompanionOptics[Box1] {
    implicit val schema: Schema[Box1] = Schema.derived
    val l: Lens[Box1, Long]           = optic(_.l)
  }

  case class Box2(r1: Record1) extends AnyVal

  object Box2 extends CompanionOptics[Box2] {
    implicit val schema: Schema[Box2] = Schema.derived
    val r1: Lens[Box2, Record1]       = optic(_.r1)
    val r1_b: Lens[Box2, Boolean]     = optic(_.r1.b)
  }

  sealed trait Wrappers

  object Wrappers extends CompanionOptics[Wrappers] {
    implicit val schema: Schema[Wrappers]        = Schema.derived
    val reflect: Reflect.Variant.Bound[Wrappers] = schema.reflect.asVariant.get
    val w_wr1: Optional[Wrappers, Record1]       = optic(_.when[Wrapper].wrapped[Record1])
  }

  case class Wrapper private (value: Record1) extends Wrappers

  object Wrapper extends CompanionOptics[Wrapper] {
    def apply(value: Record1): Either[SchemaError, Wrapper] =
      if (value.b ^ value.f < 0 || value.f == 0) new Right(new Wrapper(value))
      else new Left(SchemaError.validationFailed("Unexpected 'Wrapper' value"))

    def applyUnsafe(value: Record1): Wrapper =
      if (value.b ^ value.f < 0 || value.f == 0) new Wrapper(value)
      else throw SchemaError.validationFailed("Unexpected 'Wrapper' value")

    val reflect: Reflect.Wrapper[Binding, Wrapper, Record1] = new Reflect.Wrapper(
      wrapped = Schema[Record1].reflect,
      typeId = TypeId.nominal[Wrapper]("Wrapper", Owner.fromPackagePath("zio.blocks.schema").term("OpticSpec")),
      wrapperBinding = Binding.Wrapper(
        wrap = Wrapper.applyUnsafe,
        unwrap = (x: Wrapper) => x.value
      )
    )
    implicit val schema: Schema[Wrapper] = new Schema(reflect)
    val r1: Optional[Wrapper, Record1]   = optic(_.wrapped[Record1])
    val r1_b: Optional[Wrapper, Boolean] = optic(_.wrapped[Record1].b)
  }

  case class Record4(vw: Vector[Wrapper], w: Wrappers)

  object Record4 extends CompanionOptics[Record4] {
    implicit val schema: Schema[Record4]       = Schema.derived
    val reflect: Reflect.Record.Bound[Record4] = schema.reflect.asRecord.get
    val vw_wr1_b: Traversal[Record4, Boolean]  = optic(_.vw.each.wrapped[Record1].b)
    val w_w_wr1: Optional[Record4, Record1]    = optic(_.w.when[Wrapper].wrapped[Record1])
  }

  object Collections {
    lazy val alb: Optional[List[Byte], Byte]         = Optional.at(Reflect.list(Reflect.byte), 1)
    lazy val ailb: Traversal[List[Byte], Byte]       = Traversal.atIndices(Reflect.list(Reflect.byte), Seq(1, 2))
    lazy val alc1_d: Optional[List[Case1], Double]   = Optional.at(Reflect.list(Case1.reflect), 1)(Case1.d)
    lazy val aabl: Optional[Array[Boolean], Boolean] =
      Optional.at(
        Schema
          .derived[Array[Boolean]]
          .reflect
          .asSequenceUnknown
          .get
          .sequence
          .asInstanceOf[Reflect.Sequence[Binding, Boolean, Array]],
        1
      )
    lazy val aab: Optional[Array[Byte], Byte] =
      Optional.at(
        Schema
          .derived[Array[Byte]]
          .reflect
          .asSequenceUnknown
          .get
          .sequence
          .asInstanceOf[Reflect.Sequence[Binding, Byte, Array]],
        1
      )
    lazy val aash: Optional[Array[Short], Short] =
      Optional.at(
        Schema
          .derived[Array[Short]]
          .reflect
          .asSequenceUnknown
          .get
          .sequence
          .asInstanceOf[Reflect.Sequence[Binding, Short, Array]],
        1
      )
    lazy val aai: Optional[Array[Int], Int] =
      Optional.at(
        Schema
          .derived[Array[Int]]
          .reflect
          .asSequenceUnknown
          .get
          .sequence
          .asInstanceOf[Reflect.Sequence[Binding, Int, Array]],
        1
      )
    lazy val aal: Optional[Array[Long], Long] =
      Optional.at(
        Schema
          .derived[Array[Long]]
          .reflect
          .asSequenceUnknown
          .get
          .sequence
          .asInstanceOf[Reflect.Sequence[Binding, Long, Array]],
        1
      )
    lazy val aad: Optional[Array[Double], Double] =
      Optional.at(
        Schema
          .derived[Array[Double]]
          .reflect
          .asSequenceUnknown
          .get
          .sequence
          .asInstanceOf[Reflect.Sequence[Binding, Double, Array]],
        1
      )
    lazy val aaf: Optional[Array[Float], Float] =
      Optional.at(
        Schema
          .derived[Array[Float]]
          .reflect
          .asSequenceUnknown
          .get
          .sequence
          .asInstanceOf[Reflect.Sequence[Binding, Float, Array]],
        1
      )
    lazy val aac: Optional[Array[Char], Char] =
      Optional.at(
        Schema
          .derived[Array[Char]]
          .reflect
          .asSequenceUnknown
          .get
          .sequence
          .asInstanceOf[Reflect.Sequence[Binding, Char, Array]],
        1
      )
    lazy val aas: Optional[Array[String], String] =
      Optional.at(
        Schema
          .derived[Array[String]]
          .reflect
          .asSequenceUnknown
          .get
          .sequence
          .asInstanceOf[Reflect.Sequence[Binding, String, Array]],
        1
      )
    lazy val lb: Traversal[List[Byte], Byte]         = Traversal.listValues(Reflect.byte)
    lazy val vs: Traversal[Vector[Short], Short]     = Traversal.vectorValues(Reflect.short)
    lazy val abl: Traversal[Array[Boolean], Boolean] =
      Traversal.seqValues(
        Schema
          .derived[Array[Boolean]]
          .reflect
          .asSequenceUnknown
          .get
          .sequence
          .asInstanceOf[Reflect.Sequence[Binding, Boolean, Array]]
      )
    lazy val ab: Traversal[Array[Byte], Byte] =
      Traversal.seqValues(
        Schema
          .derived[Array[Byte]]
          .reflect
          .asSequenceUnknown
          .get
          .sequence
          .asInstanceOf[Reflect.Sequence[Binding, Byte, Array]]
      )
    lazy val ash: Traversal[Array[Short], Short] =
      Traversal.seqValues(
        Schema
          .derived[Array[Short]]
          .reflect
          .asSequenceUnknown
          .get
          .sequence
          .asInstanceOf[Reflect.Sequence[Binding, Short, Array]]
      )
    lazy val ai: Traversal[Array[Int], Int] =
      Traversal.seqValues(
        Schema
          .derived[Array[Int]]
          .reflect
          .asSequenceUnknown
          .get
          .sequence
          .asInstanceOf[Reflect.Sequence[Binding, Int, Array]]
      )
    lazy val al: Traversal[Array[Long], Long] =
      Traversal.seqValues(
        Schema
          .derived[Array[Long]]
          .reflect
          .asSequenceUnknown
          .get
          .sequence
          .asInstanceOf[Reflect.Sequence[Binding, Long, Array]]
      )
    lazy val ad: Traversal[Array[Double], Double] =
      Traversal.seqValues(
        Schema
          .derived[Array[Double]]
          .reflect
          .asSequenceUnknown
          .get
          .sequence
          .asInstanceOf[Reflect.Sequence[Binding, Double, Array]]
      )
    lazy val af: Traversal[Array[Float], Float] =
      Traversal.seqValues(
        Schema
          .derived[Array[Float]]
          .reflect
          .asSequenceUnknown
          .get
          .sequence
          .asInstanceOf[Reflect.Sequence[Binding, Float, Array]]
      )
    lazy val ac: Traversal[Array[Char], Char] =
      Traversal.seqValues(
        Schema
          .derived[Array[Char]]
          .reflect
          .asSequenceUnknown
          .get
          .sequence
          .asInstanceOf[Reflect.Sequence[Binding, Char, Array]]
      )
    lazy val as: Traversal[Array[String], String] =
      Traversal.seqValues(
        Schema
          .derived[Array[String]]
          .reflect
          .asSequenceUnknown
          .get
          .sequence
          .asInstanceOf[Reflect.Sequence[Binding, String, Array]]
      )
    lazy val abi: Traversal[Array[BigInt], BigInt] =
      Traversal.seqValues(
        Schema
          .derived[Array[BigInt]]
          .reflect
          .asSequenceUnknown
          .get
          .sequence
          .asInstanceOf[Reflect.Sequence[Binding, BigInt, Array]]
      )
    lazy val abd: Traversal[Array[BigDecimal], BigDecimal] =
      Traversal.seqValues(
        Schema
          .derived[Array[BigDecimal]]
          .reflect
          .asSequenceUnknown
          .get
          .sequence
          .asInstanceOf[Reflect.Sequence[Binding, BigDecimal, Array]]
      )
    lazy val asbl: Traversal[ArraySeq[Boolean], Boolean] =
      Traversal.seqValues(
        Schema
          .derived[ArraySeq[Boolean]]
          .reflect
          .asSequenceUnknown
          .get
          .sequence
          .asInstanceOf[Reflect.Sequence[Binding, Boolean, ArraySeq]]
      )
    lazy val asb: Traversal[ArraySeq[Byte], Byte] =
      Traversal.seqValues(
        Schema
          .derived[ArraySeq[Byte]]
          .reflect
          .asSequenceUnknown
          .get
          .sequence
          .asInstanceOf[Reflect.Sequence[Binding, Byte, ArraySeq]]
      )
    lazy val assh: Traversal[ArraySeq[Short], Short] =
      Traversal.seqValues(
        Schema
          .derived[ArraySeq[Short]]
          .reflect
          .asSequenceUnknown
          .get
          .sequence
          .asInstanceOf[Reflect.Sequence[Binding, Short, ArraySeq]]
      )
    lazy val asi: Traversal[ArraySeq[Int], Int] =
      Traversal.seqValues(
        Schema
          .derived[ArraySeq[Int]]
          .reflect
          .asSequenceUnknown
          .get
          .sequence
          .asInstanceOf[Reflect.Sequence[Binding, Int, ArraySeq]]
      )
    lazy val asl: Traversal[ArraySeq[Long], Long] =
      Traversal.seqValues(
        Schema
          .derived[ArraySeq[Long]]
          .reflect
          .asSequenceUnknown
          .get
          .sequence
          .asInstanceOf[Reflect.Sequence[Binding, Long, ArraySeq]]
      )
    lazy val asd: Traversal[ArraySeq[Double], Double] =
      Traversal.seqValues(
        Schema
          .derived[ArraySeq[Double]]
          .reflect
          .asSequenceUnknown
          .get
          .sequence
          .asInstanceOf[Reflect.Sequence[Binding, Double, ArraySeq]]
      )
    lazy val asf: Traversal[ArraySeq[Float], Float] =
      Traversal.seqValues(
        Schema
          .derived[ArraySeq[Float]]
          .reflect
          .asSequenceUnknown
          .get
          .sequence
          .asInstanceOf[Reflect.Sequence[Binding, Float, ArraySeq]]
      )
    lazy val asc: Traversal[ArraySeq[Char], Char] =
      Traversal.seqValues(
        Schema
          .derived[ArraySeq[Char]]
          .reflect
          .asSequenceUnknown
          .get
          .sequence
          .asInstanceOf[Reflect.Sequence[Binding, Char, ArraySeq]]
      )
    lazy val ass: Traversal[ArraySeq[String], String] =
      Traversal.seqValues(
        Schema
          .derived[ArraySeq[String]]
          .reflect
          .asSequenceUnknown
          .get
          .sequence
          .asInstanceOf[Reflect.Sequence[Binding, String, ArraySeq]]
      )
    lazy val sf: Traversal[Set[Float], Float]         = Traversal.setValues(Reflect.float)
    lazy val lr1: Traversal[List[Record1], Boolean]   = Traversal.listValues(Record1.reflect)(Record1.b)
    lazy val lc4_lr3: Traversal[List[Case4], Record3] = Traversal.listValues(Case4.reflect)(Case4.lr3)
    lazy val lc1: Traversal[List[Variant1], Case1]    = Traversal.listValues(Variant1.reflect)(Variant1.c1)
    lazy val lc1_d: Traversal[List[Variant1], Double] = Traversal.listValues(Variant1.reflect)(Variant1.c1_d)
    lazy val mkc: Traversal[Map[Char, String], Char]  =
      Traversal.mapKeys(Reflect.map(Reflect.char, Reflect.string))
    lazy val mvs: Traversal[Map[Char, String], String] =
      Traversal.mapValues(Reflect.map(Reflect.char, Reflect.string))
    lazy val mkv1_c1_d: Traversal[Map[Variant1, Int], Double] =
      Traversal.mapKeys(Reflect.map(Variant1.reflect, Reflect.int[Binding]))(Variant1.c1)(Case1.d)
    lazy val mvv1_c1_d: Traversal[Map[Int, Variant1], Double] =
      Traversal.mapValues(Reflect.map(Reflect.int[Binding], Variant1.reflect))(Variant1.c1)(Case1.d)
    lazy val akms: Optional[Map[Char, String], String] =
      Optional.atKey(Reflect.map(Reflect.char[Binding], Reflect.string[Binding]), 'A')
    lazy val akmc1_d: Optional[Map[Char, Case1], Double] =
      Optional.atKey(Reflect.map(Reflect.char[Binding], Case1.reflect), 'A')(Case1.d)
    lazy val aasasi_asi: Traversal[ArraySeq[ArraySeq[Int]], Int] =
      Optional.at(
        Schema
          .derived[ArraySeq[ArraySeq[Int]]]
          .reflect
          .asSequenceUnknown
          .get
          .sequence
          .asInstanceOf[Reflect.Sequence[Binding, ArraySeq[Int], ArraySeq]],
        1
      )(
        Traversal.seqValues(
          Schema
            .derived[ArraySeq[Int]]
            .reflect
            .asSequenceUnknown
            .get
            .sequence
            .asInstanceOf[Reflect.Sequence[Binding, Int, ArraySeq]]
        )
      )
    lazy val aiasasi_asi: Traversal[ArraySeq[ArraySeq[Int]], Int] =
      Traversal.atIndices(
        Schema
          .derived[ArraySeq[ArraySeq[Int]]]
          .reflect
          .asSequenceUnknown
          .get
          .sequence
          .asInstanceOf[Reflect.Sequence[Binding, ArraySeq[Int], ArraySeq]],
        Seq(1, 2)
      )(
        Traversal.seqValues(
          Schema
            .derived[ArraySeq[Int]]
            .reflect
            .asSequenceUnknown
            .get
            .sequence
            .asInstanceOf[Reflect.Sequence[Binding, Int, ArraySeq]]
        )
      )
    lazy val asasb_aasb: Traversal[ArraySeq[ArraySeq[Byte]], Byte] =
      Traversal.seqValues(
        Schema
          .derived[ArraySeq[ArraySeq[Byte]]]
          .reflect
          .asSequenceUnknown
          .get
          .sequence
          .asInstanceOf[Reflect.Sequence[Binding, ArraySeq[Byte], ArraySeq]]
      )(
        Optional.at(
          Schema
            .derived[ArraySeq[Byte]]
            .reflect
            .asSequenceUnknown
            .get
            .sequence
            .asInstanceOf[Reflect.Sequence[Binding, Byte, ArraySeq]],
          1
        )
      )
    lazy val asasbl_aasbl: Traversal[ArraySeq[ArraySeq[Boolean]], Boolean] =
      Traversal.seqValues(
        Schema
          .derived[ArraySeq[ArraySeq[Boolean]]]
          .reflect
          .asSequenceUnknown
          .get
          .sequence
          .asInstanceOf[Reflect.Sequence[Binding, ArraySeq[Boolean], ArraySeq]]
      )(
        Optional.at(
          Schema
            .derived[ArraySeq[Boolean]]
            .reflect
            .asSequenceUnknown
            .get
            .sequence
            .asInstanceOf[Reflect.Sequence[Binding, Boolean, ArraySeq]],
          1
        )
      )
    lazy val asassh_aassh: Traversal[ArraySeq[ArraySeq[Short]], Short] =
      Traversal.seqValues(
        Schema
          .derived[ArraySeq[ArraySeq[Short]]]
          .reflect
          .asSequenceUnknown
          .get
          .sequence
          .asInstanceOf[Reflect.Sequence[Binding, ArraySeq[Short], ArraySeq]]
      )(
        Optional.at(
          Schema
            .derived[ArraySeq[Short]]
            .reflect
            .asSequenceUnknown
            .get
            .sequence
            .asInstanceOf[Reflect.Sequence[Binding, Short, ArraySeq]],
          1
        )
      )
    lazy val asasc_aasc: Traversal[ArraySeq[ArraySeq[Char]], Char] =
      Traversal.seqValues(
        Schema
          .derived[ArraySeq[ArraySeq[Char]]]
          .reflect
          .asSequenceUnknown
          .get
          .sequence
          .asInstanceOf[Reflect.Sequence[Binding, ArraySeq[Char], ArraySeq]]
      )(
        Optional.at(
          Schema
            .derived[ArraySeq[Char]]
            .reflect
            .asSequenceUnknown
            .get
            .sequence
            .asInstanceOf[Reflect.Sequence[Binding, Char, ArraySeq]],
          1
        )
      )
    lazy val asasi_aasi: Traversal[ArraySeq[ArraySeq[Int]], Int] =
      Traversal.seqValues(
        Schema
          .derived[ArraySeq[ArraySeq[Int]]]
          .reflect
          .asSequenceUnknown
          .get
          .sequence
          .asInstanceOf[Reflect.Sequence[Binding, ArraySeq[Int], ArraySeq]]
      )(
        Optional.at(
          Schema
            .derived[ArraySeq[Int]]
            .reflect
            .asSequenceUnknown
            .get
            .sequence
            .asInstanceOf[Reflect.Sequence[Binding, Int, ArraySeq]],
          1
        )
      )
    lazy val asasf_aasf: Traversal[ArraySeq[ArraySeq[Float]], Float] =
      Traversal.seqValues(
        Schema
          .derived[ArraySeq[ArraySeq[Float]]]
          .reflect
          .asSequenceUnknown
          .get
          .sequence
          .asInstanceOf[Reflect.Sequence[Binding, ArraySeq[Float], ArraySeq]]
      )(
        Optional.at(
          Schema
            .derived[ArraySeq[Float]]
            .reflect
            .asSequenceUnknown
            .get
            .sequence
            .asInstanceOf[Reflect.Sequence[Binding, Float, ArraySeq]],
          1
        )
      )
    lazy val asasl_aasl: Traversal[ArraySeq[ArraySeq[Long]], Long] =
      Traversal.seqValues(
        Schema
          .derived[ArraySeq[ArraySeq[Long]]]
          .reflect
          .asSequenceUnknown
          .get
          .sequence
          .asInstanceOf[Reflect.Sequence[Binding, ArraySeq[Long], ArraySeq]]
      )(
        Optional.at(
          Schema
            .derived[ArraySeq[Long]]
            .reflect
            .asSequenceUnknown
            .get
            .sequence
            .asInstanceOf[Reflect.Sequence[Binding, Long, ArraySeq]],
          1
        )
      )
    lazy val asasd_aasd: Traversal[ArraySeq[ArraySeq[Double]], Double] =
      Traversal.seqValues(
        Schema
          .derived[ArraySeq[ArraySeq[Double]]]
          .reflect
          .asSequenceUnknown
          .get
          .sequence
          .asInstanceOf[Reflect.Sequence[Binding, ArraySeq[Double], ArraySeq]]
      )(
        Optional.at(
          Schema
            .derived[ArraySeq[Double]]
            .reflect
            .asSequenceUnknown
            .get
            .sequence
            .asInstanceOf[Reflect.Sequence[Binding, Double, ArraySeq]],
          1
        )
      )
    lazy val asass_aass: Traversal[ArraySeq[ArraySeq[String]], String] =
      Traversal.seqValues(
        Schema
          .derived[ArraySeq[ArraySeq[String]]]
          .reflect
          .asSequenceUnknown
          .get
          .sequence
          .asInstanceOf[Reflect.Sequence[Binding, ArraySeq[String], ArraySeq]]
      )(
        Optional.at(
          Schema
            .derived[ArraySeq[String]]
            .reflect
            .asSequenceUnknown
            .get
            .sequence
            .asInstanceOf[Reflect.Sequence[Binding, String, ArraySeq]],
          1
        )
      )
    lazy val asasb_aiasb: Traversal[ArraySeq[ArraySeq[Byte]], Byte] =
      Traversal.seqValues(
        Schema
          .derived[ArraySeq[ArraySeq[Byte]]]
          .reflect
          .asSequenceUnknown
          .get
          .sequence
          .asInstanceOf[Reflect.Sequence[Binding, ArraySeq[Byte], ArraySeq]]
      )(
        Traversal.atIndices(
          Schema
            .derived[ArraySeq[Byte]]
            .reflect
            .asSequenceUnknown
            .get
            .sequence
            .asInstanceOf[Reflect.Sequence[Binding, Byte, ArraySeq]],
          Seq(1, 2)
        )
      )
    lazy val asasbl_aiasbl: Traversal[ArraySeq[ArraySeq[Boolean]], Boolean] =
      Traversal.seqValues(
        Schema
          .derived[ArraySeq[ArraySeq[Boolean]]]
          .reflect
          .asSequenceUnknown
          .get
          .sequence
          .asInstanceOf[Reflect.Sequence[Binding, ArraySeq[Boolean], ArraySeq]]
      )(
        Traversal.atIndices(
          Schema
            .derived[ArraySeq[Boolean]]
            .reflect
            .asSequenceUnknown
            .get
            .sequence
            .asInstanceOf[Reflect.Sequence[Binding, Boolean, ArraySeq]],
          Seq(1, 2)
        )
      )
    lazy val asassh_aiassh: Traversal[ArraySeq[ArraySeq[Short]], Short] =
      Traversal.seqValues(
        Schema
          .derived[ArraySeq[ArraySeq[Short]]]
          .reflect
          .asSequenceUnknown
          .get
          .sequence
          .asInstanceOf[Reflect.Sequence[Binding, ArraySeq[Short], ArraySeq]]
      )(
        Traversal.atIndices(
          Schema
            .derived[ArraySeq[Short]]
            .reflect
            .asSequenceUnknown
            .get
            .sequence
            .asInstanceOf[Reflect.Sequence[Binding, Short, ArraySeq]],
          Seq(1, 2)
        )
      )
    lazy val asasc_aiasc: Traversal[ArraySeq[ArraySeq[Char]], Char] =
      Traversal.seqValues(
        Schema
          .derived[ArraySeq[ArraySeq[Char]]]
          .reflect
          .asSequenceUnknown
          .get
          .sequence
          .asInstanceOf[Reflect.Sequence[Binding, ArraySeq[Char], ArraySeq]]
      )(
        Traversal.atIndices(
          Schema
            .derived[ArraySeq[Char]]
            .reflect
            .asSequenceUnknown
            .get
            .sequence
            .asInstanceOf[Reflect.Sequence[Binding, Char, ArraySeq]],
          Seq(1, 2)
        )
      )
    lazy val asasi_aiasi: Traversal[ArraySeq[ArraySeq[Int]], Int] =
      Traversal.seqValues(
        Schema
          .derived[ArraySeq[ArraySeq[Int]]]
          .reflect
          .asSequenceUnknown
          .get
          .sequence
          .asInstanceOf[Reflect.Sequence[Binding, ArraySeq[Int], ArraySeq]]
      )(
        Traversal.atIndices(
          Schema
            .derived[ArraySeq[Int]]
            .reflect
            .asSequenceUnknown
            .get
            .sequence
            .asInstanceOf[Reflect.Sequence[Binding, Int, ArraySeq]],
          Seq(1, 2)
        )
      )
    lazy val asasf_aiasf: Traversal[ArraySeq[ArraySeq[Float]], Float] =
      Traversal.seqValues(
        Schema
          .derived[ArraySeq[ArraySeq[Float]]]
          .reflect
          .asSequenceUnknown
          .get
          .sequence
          .asInstanceOf[Reflect.Sequence[Binding, ArraySeq[Float], ArraySeq]]
      )(
        Traversal.atIndices(
          Schema
            .derived[ArraySeq[Float]]
            .reflect
            .asSequenceUnknown
            .get
            .sequence
            .asInstanceOf[Reflect.Sequence[Binding, Float, ArraySeq]],
          Seq(1, 2)
        )
      )
    lazy val asasl_aiasl: Traversal[ArraySeq[ArraySeq[Long]], Long] =
      Traversal.seqValues(
        Schema
          .derived[ArraySeq[ArraySeq[Long]]]
          .reflect
          .asSequenceUnknown
          .get
          .sequence
          .asInstanceOf[Reflect.Sequence[Binding, ArraySeq[Long], ArraySeq]]
      )(
        Traversal.atIndices(
          Schema
            .derived[ArraySeq[Long]]
            .reflect
            .asSequenceUnknown
            .get
            .sequence
            .asInstanceOf[Reflect.Sequence[Binding, Long, ArraySeq]],
          Seq(1, 2)
        )
      )
    lazy val asasd_aiasd: Traversal[ArraySeq[ArraySeq[Double]], Double] =
      Traversal.seqValues(
        Schema
          .derived[ArraySeq[ArraySeq[Double]]]
          .reflect
          .asSequenceUnknown
          .get
          .sequence
          .asInstanceOf[Reflect.Sequence[Binding, ArraySeq[Double], ArraySeq]]
      )(
        Traversal.atIndices(
          Schema
            .derived[ArraySeq[Double]]
            .reflect
            .asSequenceUnknown
            .get
            .sequence
            .asInstanceOf[Reflect.Sequence[Binding, Double, ArraySeq]],
          Seq(1, 2)
        )
      )
    lazy val asass_aiass: Traversal[ArraySeq[ArraySeq[String]], String] =
      Traversal.seqValues(
        Schema
          .derived[ArraySeq[ArraySeq[String]]]
          .reflect
          .asSequenceUnknown
          .get
          .sequence
          .asInstanceOf[Reflect.Sequence[Binding, ArraySeq[String], ArraySeq]]
      )(
        Traversal.atIndices(
          Schema
            .derived[ArraySeq[String]]
            .reflect
            .asSequenceUnknown
            .get
            .sequence
            .asInstanceOf[Reflect.Sequence[Binding, String, ArraySeq]],
          Seq(1, 2)
        )
      )
    lazy val alli_li: Traversal[List[List[Int]], Int] =
      Optional.at(Reflect.list(Reflect.list(Reflect.int[Binding])), 1)(Traversal.listValues(Reflect.int[Binding]))
    lazy val ailli_li: Traversal[List[List[Int]], Int] =
      Traversal.atIndices(Reflect.list(Reflect.list(Reflect.int[Binding])), Seq(1))(
        Traversal.listValues(Reflect.int[Binding])
      )
    lazy val lli_ali: Traversal[List[List[Int]], Int] =
      Traversal.listValues(Reflect.list(Reflect.int[Binding]))(Optional.at(Reflect.list(Reflect.int[Binding]), 1))
    lazy val akmill_ll: Traversal[Map[Int, List[Long]], Long] =
      Optional.atKey(Reflect.map(Reflect.int, Reflect.list(Reflect.long)), 1)(Traversal.listValues(Reflect.long))
    lazy val aksmill_ll: Traversal[Map[Int, List[Long]], Long] =
      Traversal.atKeys(Reflect.map(Reflect.int, Reflect.list(Reflect.long)), Seq(1))(Traversal.listValues(Reflect.long))
    lazy val lmil_akmil: Traversal[List[Map[Int, Long]], Long] =
      Traversal.listValues(Reflect.map(Reflect.int, Reflect.long))(
        Optional.atKey(Reflect.map(Reflect.int, Reflect.long), 1)
      )
    lazy val lmil_aksmil: Traversal[List[Map[Int, Long]], Long] =
      Traversal.listValues(Reflect.map(Reflect.int, Reflect.long))(
        Traversal.atKeys(Reflect.map(Reflect.int, Reflect.long), Seq(1, 2))
      )
    lazy val lw_r1: Traversal[List[Wrapper], Record1]   = Traversal.listValues(Wrapper.reflect)(Wrapper.r1)
    lazy val lw_r1_b: Traversal[List[Wrapper], Boolean] = Traversal.listValues(Wrapper.reflect)(Wrapper.r1_b)
  }

  case class RecordWithPrimitiveArray(ints: Array[Int])

  object RecordWithPrimitiveArray extends CompanionOptics[RecordWithPrimitiveArray] {
    implicit val schema: Schema[RecordWithPrimitiveArray]       = Schema.derived
    val reflect: Reflect.Record.Bound[RecordWithPrimitiveArray] = schema.reflect.asRecord.get
    val ints: Traversal[RecordWithPrimitiveArray, Int]          = optic(_.ints.each)
    val intAt1: Optional[RecordWithPrimitiveArray, Int]         = optic(_.ints.at(1))
    val intsAtIndices: Traversal[RecordWithPrimitiveArray, Int] = optic(_.ints.atIndices(0, 2))
  }

}
