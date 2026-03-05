package zio.blocks.codegen.ir

import zio.test._
import zio.test.Assertion._

object TypeParamSpec extends ZIOSpecDefault {
  def spec =
    suite("TypeParam")(
      suite("construction")(
        test("creates simple invariant type param") {
          val tp = TypeParam("A")
          assert(tp.name)(equalTo("A")) &&
          assert(tp.variance)(equalTo(Variance.Invariant)) &&
          assert(tp.upperBound)(isNone) &&
          assert(tp.lowerBound)(isNone)
        },
        test("creates covariant type param") {
          val tp = TypeParam("A", Variance.Covariant)
          assert(tp.variance)(equalTo(Variance.Covariant))
        },
        test("creates contravariant type param") {
          val tp = TypeParam("A", Variance.Contravariant)
          assert(tp.variance)(equalTo(Variance.Contravariant))
        },
        test("creates type param with upper bound") {
          val tp = TypeParam("A", upperBound = Some(TypeRef("Serializable")))
          assert(tp.upperBound)(isSome(equalTo(TypeRef("Serializable"))))
        },
        test("creates type param with lower bound") {
          val tp = TypeParam("A", lowerBound = Some(TypeRef.Nothing))
          assert(tp.lowerBound)(isSome(equalTo(TypeRef.Nothing)))
        },
        test("creates type param with both bounds") {
          val tp = TypeParam(
            "A",
            lowerBound = Some(TypeRef.Nothing),
            upperBound = Some(TypeRef("AnyRef"))
          )
          assert(tp.lowerBound)(isSome) &&
          assert(tp.upperBound)(isSome)
        }
      ),
      suite("equality")(
        test("equal type params") {
          val tp1 = TypeParam("A", Variance.Covariant)
          val tp2 = TypeParam("A", Variance.Covariant)
          assert(tp1)(equalTo(tp2))
        },
        test("unequal when name differs") {
          val tp1 = TypeParam("A")
          val tp2 = TypeParam("B")
          assert(tp1)(not(equalTo(tp2)))
        },
        test("unequal when variance differs") {
          val tp1 = TypeParam("A", Variance.Covariant)
          val tp2 = TypeParam("A", Variance.Contravariant)
          assert(tp1)(not(equalTo(tp2)))
        }
      )
    )
}
