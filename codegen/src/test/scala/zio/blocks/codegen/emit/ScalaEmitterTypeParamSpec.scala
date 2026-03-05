package zio.blocks.codegen.emit

import zio.test._
import zio.blocks.codegen.ir._

object ScalaEmitterTypeParamSpec extends ZIOSpecDefault {
  def spec = suite("ScalaEmitter type param emission")(
    suite("ScalaEmitter.emitTypeParam")(
      test("invariant type param") {
        val result = ScalaEmitter.emitTypeParam(TypeParam("A"))
        assertTrue(result == "A")
      },
      test("covariant type param") {
        val result = ScalaEmitter.emitTypeParam(TypeParam("A", Variance.Covariant))
        assertTrue(result == "+A")
      },
      test("contravariant type param") {
        val result = ScalaEmitter.emitTypeParam(TypeParam("A", Variance.Contravariant))
        assertTrue(result == "-A")
      },
      test("type param with upper bound") {
        val result = ScalaEmitter.emitTypeParam(
          TypeParam("A", upperBound = Some(TypeRef("Serializable")))
        )
        assertTrue(result == "A <: Serializable")
      },
      test("covariant type param with upper bound") {
        val result = ScalaEmitter.emitTypeParam(
          TypeParam("A", Variance.Covariant, upperBound = Some(TypeRef("AnyRef")))
        )
        assertTrue(result == "+A <: AnyRef")
      },
      test("type param with both bounds") {
        val result = ScalaEmitter.emitTypeParam(
          TypeParam("A", lowerBound = Some(TypeRef.Nothing), upperBound = Some(TypeRef("AnyRef")))
        )
        assertTrue(result == "A >: Nothing <: AnyRef")
      },
      test("case class with covariant type param") {
        val cc = CaseClass(
          "Box",
          fields = List(Field("value", TypeRef("A"))),
          typeParams = List(TypeParam("A", Variance.Covariant))
        )
        val result = ScalaEmitter.emitCaseClass(cc, EmitterConfig.default)
        assertTrue(
          result ==
            """|case class Box[+A](
               |  value: A,
               |)""".stripMargin
        )
      },
      test("sealed trait with contravariant type param") {
        val st = SealedTrait(
          "Consumer",
          typeParams = List(TypeParam("A", Variance.Contravariant))
        )
        val result = ScalaEmitter.emitSealedTrait(st, EmitterConfig.default)
        assertTrue(result == "sealed trait Consumer[-A]")
      },
      test("method with bounded type param") {
        val method = Method(
          "process",
          typeParams = List(TypeParam("A", upperBound = Some(TypeRef("Serializable")))),
          params = List(ParamList(List(MethodParam("value", TypeRef("A"))))),
          returnType = TypeRef("A"),
          body = Some("value")
        )
        val result = ScalaEmitter.emitMethod(method, EmitterConfig.default)
        assertTrue(result == "def process[A <: Serializable](value: A): A = value")
      },
      test("method with multiple type params including variance and bounds") {
        val method = Method(
          "transform",
          typeParams = List(
            TypeParam("A", lowerBound = Some(TypeRef.Nothing)),
            TypeParam("B", upperBound = Some(TypeRef("AnyRef")))
          ),
          params = List(ParamList(List(MethodParam("a", TypeRef("A"))))),
          returnType = TypeRef("B")
        )
        val result = ScalaEmitter.emitMethod(method, EmitterConfig.default)
        assertTrue(result == "def transform[A >: Nothing, B <: AnyRef](a: A): B")
      }
    ) @@ TestAspect.tag("existing"),
    suite("HKT type params")(
      test("F[_] renders correctly") {
        val tp     = TypeParam("F", typeParams = List(TypeParam("_")))
        val result = ScalaEmitter.emitTypeParam(tp)
        assertTrue(result == "F[_]")
      },
      test("+F[_] with nested type params renders correctly") {
        val tp     = TypeParam("F", Variance.Covariant, typeParams = List(TypeParam("_")))
        val result = ScalaEmitter.emitTypeParam(tp)
        assertTrue(result == "+F[_]")
      },
      test("trait with HKT type param renders with brackets on type param") {
        val t = Trait(
          "Functor",
          typeParams = List(TypeParam("F", typeParams = List(TypeParam("_"))))
        )
        val result = ScalaEmitter.emitTrait(t, EmitterConfig.default)
        assertTrue(result == "trait Functor[F[_]]")
      }
    ),
    suite("context bounds")(
      test("context bound on type param") {
        val tp     = TypeParam("A", contextBounds = List(TypeRef("Ordering")))
        val result = ScalaEmitter.emitTypeParam(tp)
        assertTrue(result == "A: Ordering")
      },
      test("multiple context bounds") {
        val tp     = TypeParam("A", contextBounds = List(TypeRef("Ordering"), TypeRef("Show")))
        val result = ScalaEmitter.emitTypeParam(tp)
        assertTrue(result == "A: Ordering: Show")
      },
      test("context bound with variance") {
        val tp     = TypeParam("A", Variance.Covariant, contextBounds = List(TypeRef("Schema")))
        val result = ScalaEmitter.emitTypeParam(tp)
        assertTrue(result == "+A: Schema")
      },
      test("context bound combined with upper bound") {
        val tp = TypeParam(
          "A",
          upperBound = Some(TypeRef("Serializable")),
          contextBounds = List(TypeRef("Schema"))
        )
        val result = ScalaEmitter.emitTypeParam(tp)
        assertTrue(result == "A <: Serializable: Schema")
      }
    ),
    suite("type param edge cases")(
      test("wildcard type param") {
        val tp     = TypeParam("_")
        val result = ScalaEmitter.emitTypeParam(tp)
        assertTrue(result == "_")
      },
      test("type param with lower and upper bounds") {
        val tp = TypeParam(
          "A",
          lowerBound = Some(TypeRef("Nothing")),
          upperBound = Some(TypeRef("Any"))
        )
        val result = ScalaEmitter.emitTypeParam(tp)
        assertTrue(result == "A >: Nothing <: Any")
      },
      test("contravariant with upper bound") {
        val tp     = TypeParam("A", Variance.Contravariant, upperBound = Some(TypeRef("AnyRef")))
        val result = ScalaEmitter.emitTypeParam(tp)
        assertTrue(result == "-A <: AnyRef")
      }
    )
  )
}
