package zio.blocks.schema

import zio.blocks.schema.binding.Binding
import zio.test.Assertion.{equalTo, isNone, isSome}
import zio.test.{Spec, TestEnvironment, ZIOSpecDefault, assert}

object DynamicOpticSpec extends ZIOSpecDefault {
  import DynamicOpticSpecTypes._

  def spec: Spec[TestEnvironment, Any] = suite("DynamicOpticSpec")(
    test("composition using apply, field, caseOf, at, atKey, elements, mapKeys, and mapValues methods") {
      assert(
        DynamicOptic.root
          .apply(DynamicOptic(Vector(DynamicOptic.Node.Case("X"))))
          .apply(DynamicOptic(Vector(DynamicOptic.Node.Field("y"))))
      )(equalTo(A.x(X.y).toDynamic)) &&
      assert(DynamicOptic.root.caseOf("X").field("y"))(equalTo(A.x(X.y).toDynamic)) &&
      assert(
        DynamicOptic.root
          .apply(DynamicOptic(Vector(DynamicOptic.Node.AtIndex(0))))
          .apply(DynamicOptic(Vector(DynamicOptic.Node.AtMapKey("Z"))))
      )(equalTo(DynamicOptic.root.at(0).atKey("Z"))) &&
      assert(DynamicOptic.root.elements.mapKeys.mapValues)(
        equalTo(
          DynamicOptic.root.apply(DynamicOptic.elements).apply(DynamicOptic.mapKeys).apply(DynamicOptic.mapValues)
        )
      )
    },
    test("gets reflect using apply") {
      assert(A.x.toDynamic.apply(Schema[A].reflect): Option[Any])(isSome(equalTo(Schema[X].reflect))) &&
      assert(A.x(X.y).toDynamic.apply(Schema[A].reflect): Option[Any])(isSome(equalTo(Schema[Y].reflect))) &&
      assert(A.x(X.y)(Y.z).toDynamic.apply(Schema[A].reflect): Option[Any])(isSome(equalTo(Reflect.int[Binding]))) &&
      assert(DynamicOptic.root.at(0).atKey("Z").apply(Schema[List[Map[Int, Long]]].reflect): Option[Any])(
        isSome(equalTo(Reflect.long[Binding]))
      ) &&
      assert(DynamicOptic.elements.apply(Schema[List[Int]].reflect): Option[Any])(
        isSome(equalTo(Reflect.int[Binding]))
      ) &&
      assert(DynamicOptic.mapKeys.apply(Schema[Map[Int, Long]].reflect): Option[Any])(
        isSome(equalTo(Reflect.int[Binding]))
      ) &&
      assert(DynamicOptic.mapValues.apply(Schema[Map[Long, Int]].reflect): Option[Any])(
        isSome(equalTo(Reflect.int[Binding]))
      )
    },
    test("doesn't get reflect using apply for wrong dynamic options") {
      assert(DynamicOptic.root.field("z").apply(Schema[A].reflect): Option[Any])(isNone) &&
      assert(DynamicOptic.root.caseOf("z").apply(Schema[X].reflect): Option[Any])(isNone) &&
      assert(DynamicOptic.root.caseOf("Z").apply(Schema[A].reflect): Option[Any])(isNone) &&
      assert(DynamicOptic.root.caseOf("X").field("x").apply(Schema[A].reflect): Option[Any])(isNone) &&
      assert(DynamicOptic.root.at(0).apply(Schema[A].reflect): Option[Any])(isNone) &&
      assert(DynamicOptic.root.atKey("Z").apply(Schema[A].reflect): Option[Any])(isNone) &&
      assert(DynamicOptic.elements.apply(Schema[A].reflect): Option[Any])(isNone) &&
      assert(DynamicOptic.mapKeys.apply(Schema[A].reflect): Option[Any])(isNone) &&
      assert(DynamicOptic.mapValues.apply(Schema[A].reflect): Option[Any])(isNone)
    },
    test("toString returns a path") {
      assert(A.x.toDynamic.toString)(equalTo(".when[X]")) &&
      assert(A.x(X.y).toDynamic.toString)(equalTo(".when[X].y")) &&
      assert(A.x(X.y)(Y.z).toDynamic.toString)(equalTo(".when[X].y.z")) &&
      assert(DynamicOptic.root.at(0).atKey("Z").toString)(equalTo(".at(0).atKey(<key>)")) &&
      assert(DynamicOptic.root.elements.mapKeys.mapValues.toString)(equalTo(".each.eachKey.eachValue"))
    }
  )
}

object DynamicOpticSpecTypes {
  sealed trait A

  case class X(y: Y) extends A

  object X extends CompanionOptics[X] {
    implicit val schema: Schema[X] = Schema.derived
    val y: Lens[X, Y]              = optic(_.y)
  }

  case class Y(z: Int) extends A

  object Y extends CompanionOptics[Y] {
    implicit val schema: Schema[Y] = Schema.derived
    val z: Lens[Y, Int]            = optic(_.z)
  }

  object A extends CompanionOptics[A] {
    implicit val schema: Schema[A] = Schema.derived
    val x: Prism[A, X]             = optic(_.when[X])
    val y: Prism[A, Y]             = optic(_.when[Y])
  }
}
