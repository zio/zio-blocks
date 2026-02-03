package zio.blocks.schema

import zio.blocks.schema.binding.Binding
import zio.blocks.typeid.TypeId
import zio.test.Assertion.{equalTo, isNone, isSome}
import zio.test.{Spec, TestEnvironment, assert}

object DynamicOpticSpec extends SchemaBaseSpec {
  def spec: Spec[TestEnvironment, Any] = suite("DynamicOpticSpec")(
    test("path interpolator matches manual DynamicOptic construction") {
      assert(p"<X>.y")(equalTo(A.x(X.y).toDynamic)) &&
      assert(p"<X>.y.z")(equalTo(A.x(X.y)(Y.z).toDynamic)) &&
      assert(p"[0]")(equalTo(DynamicOptic.root.at(0))) &&
      assert(p"[0,1,2]")(equalTo(DynamicOptic.root.atIndices(0, 1, 2))) &&
      assert(p"[*]")(equalTo(DynamicOptic.elements)) &&
      assert(p"{*:}")(equalTo(DynamicOptic.mapKeys)) &&
      assert(p"{*}")(equalTo(DynamicOptic.mapValues))
    },
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
          .apply(DynamicOptic(Vector(DynamicOptic.Node.AtMapKey(Schema[String].toDynamicValue("Z")))))
      )(equalTo(DynamicOptic.root.at(0).atKey("Z"))) &&
      assert(
        DynamicOptic.root
          .apply(DynamicOptic(Vector(DynamicOptic.Node.AtIndices(Seq(0, 1, 2)))))
          .apply(
            DynamicOptic(Vector(DynamicOptic.Node.AtMapKeys(Seq("X", "Y", "Z").map(Schema[String].toDynamicValue))))
          )
      )(equalTo(DynamicOptic.root.atIndices(0, 1, 2).atKeys("X", "Y", "Z"))) &&
      assert(DynamicOptic.root.elements.mapKeys.mapValues.wrapped)(
        equalTo(
          DynamicOptic.root(DynamicOptic.elements)(DynamicOptic.mapKeys)(DynamicOptic.mapValues)(DynamicOptic.wrapped)
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
      assert(
        DynamicOptic.root
          .atIndices(0, 1, 2)
          .atKeys("X", "Y", "Z")
          .apply(Schema[List[Map[Int, Long]]].reflect): Option[Any]
      )(isSome(equalTo(Reflect.long[Binding]))) &&
      assert(DynamicOptic.elements.apply(Schema[List[Int]].reflect): Option[Any])(
        isSome(equalTo(Reflect.int[Binding]))
      ) &&
      assert(DynamicOptic.mapKeys.apply(Schema[Map[Int, Long]].reflect): Option[Any])(
        isSome(equalTo(Reflect.int[Binding]))
      ) &&
      assert(DynamicOptic.mapValues.apply(Schema[Map[Long, Int]].reflect): Option[Any])(
        isSome(equalTo(Reflect.int[Binding]))
      ) &&
      assert(DynamicOptic.wrapped.apply(Schema[PosInt].reflect): Option[Any])(
        isSome(equalTo(Reflect.int[Binding]))
      )
    },
    test("doesn't get reflect using apply for wrong dynamic options") {
      assert(DynamicOptic.root.field("z").apply(Schema[A].reflect): Option[Any])(isNone) &&
      assert(DynamicOptic.root.caseOf("z").apply(Schema[X].reflect): Option[Any])(isNone) &&
      assert(DynamicOptic.root.caseOf("Z").apply(Schema[A].reflect): Option[Any])(isNone) &&
      assert(DynamicOptic.root.caseOf("X").field("x").apply(Schema[A].reflect): Option[Any])(isNone) &&
      assert(DynamicOptic.root.at(0).apply(Schema[A].reflect): Option[Any])(isNone) &&
      assert(DynamicOptic.root.atIndices(0, 1, 2).apply(Schema[A].reflect): Option[Any])(isNone) &&
      assert(DynamicOptic.root.atKey("Z").apply(Schema[A].reflect): Option[Any])(isNone) &&
      assert(DynamicOptic.root.atKeys("X", "Y", "Z").apply(Schema[A].reflect): Option[Any])(isNone) &&
      assert(DynamicOptic.elements.apply(Schema[A].reflect): Option[Any])(isNone) &&
      assert(DynamicOptic.mapKeys.apply(Schema[A].reflect): Option[Any])(isNone) &&
      assert(DynamicOptic.mapValues.apply(Schema[A].reflect): Option[Any])(isNone) &&
      assert(DynamicOptic.wrapped.apply(Schema[A].reflect): Option[Any])(isNone)
    },
    test("toString returns a path") {
      assert(A.x.toDynamic.toString)(equalTo("<X>")) &&
      assert(A.x(X.y).toDynamic.toString)(equalTo("<X>.y")) &&
      assert(A.x(X.y)(Y.z).toDynamic.toString)(equalTo("<X>.y.z")) &&
      assert(DynamicOptic.root.at(0).atKey("Z").toString)(equalTo("[0]{\"Z\"}")) &&
      assert(DynamicOptic.root.atIndices(0, 1, 2).atKeys("X", "Y", "Z").toString)(
        equalTo("[0,1,2]{\"X\", \"Y\", \"Z\"}")
      ) &&
      assert(DynamicOptic.root.elements.mapKeys.mapValues.wrapped.toString)(equalTo("[*]{*:}{*}.~"))
    },
    test("toString returns dot for empty optic") {
      assert(DynamicOptic.root.toString)(equalTo("."))
    },
    test("toScalaString returns Scala method syntax") {
      assert(A.x.toDynamic.toScalaString)(equalTo(".when[X]")) &&
      assert(A.x(X.y).toDynamic.toScalaString)(equalTo(".when[X].y")) &&
      assert(DynamicOptic.root.at(0).toScalaString)(equalTo(".at(0)")) &&
      assert(DynamicOptic.root.atIndices(0, 1, 2).toScalaString)(equalTo(".atIndices(0, 1, 2)")) &&
      assert(DynamicOptic.root.atKey("Z").toScalaString)(equalTo(".atKey(\"Z\")")) &&
      assert(DynamicOptic.root.atKeys("X", "Y").toScalaString)(equalTo(".atKeys(\"X\", \"Y\")")) &&
      assert(DynamicOptic.elements.toScalaString)(equalTo(".each")) &&
      assert(DynamicOptic.mapKeys.toScalaString)(equalTo(".eachKey")) &&
      assert(DynamicOptic.mapValues.toScalaString)(equalTo(".eachValue")) &&
      assert(DynamicOptic.wrapped.toScalaString)(equalTo(".wrapped"))
    },
    test("toScalaString returns dot for empty optic") {
      assert(DynamicOptic.root.toScalaString)(equalTo("."))
    },
    test("toString handles special characters in string keys") {
      assert(DynamicOptic.root.atKey("hello\nworld").toString)(equalTo("{\"hello\\nworld\"}")) &&
      assert(DynamicOptic.root.atKey("tab\there").toString)(equalTo("{\"tab\\there\"}")) &&
      assert(DynamicOptic.root.atKey("quote\"test").toString)(equalTo("{\"quote\\\"test\"}"))
    },
    test("toString handles numeric primitive keys") {
      assert(DynamicOptic.root.atKey(42).toString)(equalTo("{42}")) &&
      assert(DynamicOptic.root.atKey(123L).toString)(equalTo("{123}")) &&
      assert(DynamicOptic.root.atKey(true).toString)(equalTo("{true}"))
    }
  )

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

  case class PosInt private (value: Int) extends AnyVal

  object PosInt extends CompanionOptics[PosInt] {
    def apply(value: Int): Either[SchemaError, PosInt] =
      if (value >= 0) new Right(new PosInt(value))
      else new Left(SchemaError.validationFailed("Expected positive value"))

    def applyUnsafe(value: Int): PosInt =
      if (value >= 0) new PosInt(value)
      else throw new IllegalArgumentException("Expected positive value")

    implicit lazy val typeId: TypeId[PosInt] = TypeId.of[PosInt]
    implicit lazy val schema: Schema[PosInt] =
      Schema[Int].transform[PosInt](PosInt.applyUnsafe, _.value)
  }
}
