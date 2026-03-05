package zio.blocks.codegen.emit

import zio.test._
import zio.blocks.codegen.ir._

object ScalaEmitterTypeParamSpec extends ZIOSpecDefault {
  def spec =
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
    )
}
