package zio.blocks.schema

import zio.blocks.schema.binding.Binding
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
    test("toString returns a path matching p\"...\" interpolator syntax") {
      assert(A.x.toDynamic.toString)(equalTo("<X>")) &&
      assert(A.x(X.y).toDynamic.toString)(equalTo("<X>.y")) &&
      assert(A.x(X.y)(Y.z).toDynamic.toString)(equalTo("<X>.y.z")) &&
      assert(DynamicOptic.root.at(0).atKey("Z").toString)(equalTo("[0]{\"Z\"}")) &&
      assert(DynamicOptic.root.atIndices(0, 1, 2).atKeys("X", "Y", "Z").toString)(
        equalTo("[0,1,2]{\"X\",\"Y\",\"Z\"}")
      ) &&
      assert(DynamicOptic.root.elements.mapKeys.mapValues.wrapped.toString)(equalTo("[*]{*:}{*}.~"))
    },
    suite("DynamicOptic.toString (path interpolator syntax)")(
      test("renders root as dot") {
        assert(DynamicOptic.root.toString)(equalTo("."))
      },
      test("renders field access") {
        val optic = DynamicOptic.root.field("name")
        assert(optic.toString)(equalTo(".name"))
      },
      test("renders multiple field accesses") {
        val optic = DynamicOptic.root.field("address").field("street")
        assert(optic.toString)(equalTo(".address.street"))
      },
      test("renders case selection with angle brackets") {
        val optic = DynamicOptic.root.caseOf("Some")
        assert(optic.toString)(equalTo("<Some>"))
      },
      test("renders case followed by field") {
        val optic = DynamicOptic.root.caseOf("Some").field("value")
        assert(optic.toString)(equalTo("<Some>.value"))
      },
      test("renders index access with brackets") {
        val optic = DynamicOptic.root.at(0)
        assert(optic.toString)(equalTo("[0]"))
      },
      test("renders negative index") {
        val optic = DynamicOptic.root.at(-1)
        assert(optic.toString)(equalTo("[-1]"))
      },
      test("renders large index") {
        val optic = DynamicOptic.root.at(999)
        assert(optic.toString)(equalTo("[999]"))
      },
      test("renders atKey with braces and quoted key") {
        val optic = DynamicOptic.root.atKey("myKey")
        assert(optic.toString)(equalTo("{\"myKey\"}"))
      },
      test("renders atIndices with comma-separated indices") {
        val optic = DynamicOptic.root.atIndices(0, 1, 2)
        assert(optic.toString)(equalTo("[0,1,2]"))
      },
      test("renders atIndices with single index") {
        val optic = DynamicOptic.root.atIndices(5)
        assert(optic.toString)(equalTo("[5]"))
      },
      test("renders atKeys with comma-separated quoted keys") {
        val optic = DynamicOptic.root.atKeys("a", "b", "c")
        assert(optic.toString)(equalTo("{\"a\",\"b\",\"c\"}"))
      },
      test("renders atKeys with single key") {
        val optic = DynamicOptic.root.atKeys("only")
        assert(optic.toString)(equalTo("{\"only\"}"))
      },
      test("renders elements traversal as [*]") {
        val optic = DynamicOptic.elements
        assert(optic.toString)(equalTo("[*]"))
      },
      test("renders mapKeys traversal as {*:}") {
        val optic = DynamicOptic.mapKeys
        assert(optic.toString)(equalTo("{*:}"))
      },
      test("renders mapValues traversal as {*}") {
        val optic = DynamicOptic.mapValues
        assert(optic.toString)(equalTo("{*}"))
      },
      test("renders wrapped as .~") {
        val optic = DynamicOptic.wrapped
        assert(optic.toString)(equalTo(".~"))
      },
      test("renders chained operations") {
        val optic = DynamicOptic.root.field("users").at(0).field("name")
        assert(optic.toString)(equalTo(".users[0].name"))
      },
      test("renders complex path with case and key") {
        val optic = DynamicOptic.root.caseOf("Some").field("value").atKey("key1")
        assert(optic.toString)(equalTo("<Some>.value{\"key1\"}"))
      },
      test("renders atKey with integer key") {
        val optic = DynamicOptic.root.atKey(42)
        assert(optic.toString)(equalTo("{42}"))
      },
      test("renders atKey with long key") {
        val optic = DynamicOptic.root.atKey(9876543210L)
        assert(optic.toString)(equalTo("{9876543210}"))
      },
      test("renders field followed by elements traversal") {
        val optic = DynamicOptic.root.field("items").elements
        assert(optic.toString)(equalTo(".items[*]"))
      },
      test("renders field followed by mapValues") {
        val optic = DynamicOptic.root.field("config").mapValues
        assert(optic.toString)(equalTo(".config{*}"))
      },
      test("renders deeply nested path") {
        val optic = DynamicOptic.root
          .field("level1")
          .field("level2")
          .field("level3")
          .field("level4")
        assert(optic.toString)(equalTo(".level1.level2.level3.level4"))
      },
      test("renders elements followed by field") {
        val optic = DynamicOptic.root.field("users").elements.field("email")
        assert(optic.toString)(equalTo(".users[*].email"))
      },
      test("renders mapValues followed by field") {
        val optic = DynamicOptic.root.field("data").mapValues.field("count")
        assert(optic.toString)(equalTo(".data{*}.count"))
      },
      test("renders wrapped in chain") {
        val optic = DynamicOptic.root.field("wrapper").wrapped.field("inner")
        assert(optic.toString)(equalTo(".wrapper.~.inner"))
      },
      test("renders case after index") {
        val optic = DynamicOptic.root.at(0).caseOf("Left")
        assert(optic.toString)(equalTo("[0]<Left>"))
      },
      test("renders atKeys followed by field") {
        val optic = DynamicOptic.root.field("map").atKeys("a", "b").field("value")
        assert(optic.toString)(equalTo(".map{\"a\",\"b\"}.value"))
      },
      test("renders atIndices followed by case") {
        val optic = DynamicOptic.root.field("list").atIndices(0, 1).caseOf("Some")
        assert(optic.toString)(equalTo(".list[0,1]<Some>"))
      },
      test("renders atKey with empty string key") {
        val optic = DynamicOptic.root.atKey("")
        assert(optic.toString)(equalTo("{\"\"}"))
      },
      test("renders atKey with key containing special chars") {
        val optic = DynamicOptic.root.atKey("key.with.dots")
        assert(optic.toString)(equalTo("{\"key.with.dots\"}"))
      },
      test("renders composition of two optics") {
        val optic1   = DynamicOptic.root.field("a")
        val optic2   = DynamicOptic.root.field("b")
        val composed = optic1(optic2)
        assert(composed.toString)(equalTo(".a.b"))
      }
    )
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

    implicit val schema: Schema[PosInt] =
      Schema[Int].transformOrFail[PosInt](PosInt.apply, _.value).asOpaqueType[PosInt]
  }
}
