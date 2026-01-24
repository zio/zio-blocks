package zio.blocks.schema

import zio.test._
import zio.test.Assertion._

object TypeNameVersionSpecificSpec extends SchemaBaseSpec {
  def spec: Spec[TestEnvironment, Any] = suite("TypeNameVersionSpecificSpec")(
    suite("TypeName.derived Scala 2 specific")(
      test("derives TypeName for case object singleton type") {
        val typeName = TypeName.derived[CaseObjectTest.type]
        assert(typeName.name)(equalTo("CaseObjectTest"))
      },
      test("derives TypeName for nested class inside object") {
        val typeName = TypeName.derived[OuterObject.InnerClass]
        assert(typeName.name)(equalTo("InnerClass")) &&
        assert(typeName.namespace.values)(equalTo(List("TypeNameVersionSpecificSpec", "OuterObject")))
      },
      test("derives TypeName for nested object inside object") {
        val typeName = TypeName.derived[OuterObject.InnerObject.type]
        assert(typeName.name)(equalTo("InnerObject")) &&
        assert(typeName.namespace.values)(equalTo(List("TypeNameVersionSpecificSpec", "OuterObject")))
      },
      test("derives TypeName for doubly nested class") {
        val typeName = TypeName.derived[OuterObject.InnerObject.DeepClass]
        assert(typeName.name)(equalTo("DeepClass")) &&
        assert(typeName.namespace.values)(equalTo(List("TypeNameVersionSpecificSpec", "OuterObject", "InnerObject")))
      },
      test("derives TypeName for generic class with multiple parameters") {
        val typeName = TypeName.derived[MultiParam[Int, String, Boolean]]
        assert(typeName.name)(equalTo("MultiParam")) &&
        assert(typeName.params.map(_.name))(equalTo(Seq("Int", "String", "Boolean")))
      },
      test("derives TypeName for class with encoded special characters in name") {
        val typeName = TypeName.derived[`Special$Name`]
        assert(typeName.name)(equalTo("Special$Name"))
      },
      test("derives TypeName for type alias pointing to sealed trait") {
        type MySealedAlias = SealedTraitForAlias
        val typeName = TypeName.derived[MySealedAlias]
        assert(typeName.name)(equalTo("SealedTraitForAlias"))
      },
      test("derives TypeName for higher-kinded type applied to Option") {
        val typeName = TypeName.derived[HigherKindedContainer[Option]]
        assert(typeName.name)(equalTo("HigherKindedContainer")) &&
        assert(typeName.params.map(_.name))(equalTo(Seq("Option")))
      },
      test("derives TypeName for type with type bounds") {
        val typeName = TypeName.derived[BoundedType[String]]
        assert(typeName.name)(equalTo("BoundedType")) &&
        assert(typeName.params.map(_.name))(equalTo(Seq("String")))
      },
      test("derives TypeName for path-dependent type") {
        val outer    = new PathDependentOuter
        val typeName = TypeName.derived[outer.Inner]
        assert(typeName.name)(equalTo("Inner"))
      }
    )
  )

  case object CaseObjectTest

  object OuterObject {
    case class InnerClass(x: Int)
    object InnerObject {
      case class DeepClass(y: String)
    }
  }

  case class MultiParam[A, B, C](a: A, b: B, c: C)

  case class `Special$Name`(value: Int)

  sealed trait SealedTraitForAlias
  case class AliasCase1(i: Int) extends SealedTraitForAlias
  case class AliasCase2(s: String) extends SealedTraitForAlias

  trait HigherKindedContainer[F[_]]

  trait BoundedType[A <: AnyRef]

  class PathDependentOuter {
    case class Inner(x: Int)
  }
}
