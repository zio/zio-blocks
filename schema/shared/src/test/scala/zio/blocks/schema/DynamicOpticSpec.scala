package zio.blocks.schema

import zio.blocks.schema.binding.Binding
import zio.test.Assertion.{equalTo, isNone, isSome}
import zio.test.{Spec, TestEnvironment, assert, assertTrue}

object DynamicOpticSpec extends SchemaBaseSpec {
  def spec: Spec[TestEnvironment, Any] = suite("DynamicOpticSpec")(
    suite("Node types")(
      test("Field node construction and properties") {
        val field = DynamicOptic.Node.Field("name")
        assertTrue(
          field.name == "name",
          field.isInstanceOf[DynamicOptic.Node],
          DynamicOptic.root.field("name").nodes.head == field
        )
      },
      test("Case node construction and properties") {
        val caseNode = DynamicOptic.Node.Case("SomeCase")
        assertTrue(
          caseNode.name == "SomeCase",
          caseNode.isInstanceOf[DynamicOptic.Node],
          DynamicOptic.root.caseOf("SomeCase").nodes.head == caseNode
        )
      },
      test("AtIndex node construction and properties") {
        val atIndex = DynamicOptic.Node.AtIndex(5)
        assertTrue(
          atIndex.index == 5,
          atIndex.isInstanceOf[DynamicOptic.Node],
          DynamicOptic.root.at(5).nodes.head == atIndex
        )
      },
      test("AtMapKey node construction and properties") {
        val key      = Schema[String].toDynamicValue("testKey")
        val atMapKey = DynamicOptic.Node.AtMapKey(key)
        assertTrue(
          atMapKey.key == key,
          atMapKey.isInstanceOf[DynamicOptic.Node],
          DynamicOptic.root.atKey("testKey").nodes.head == atMapKey
        )
      },
      test("Elements node is singleton") {
        assertTrue(
          DynamicOptic.Node.Elements == DynamicOptic.Node.Elements,
          DynamicOptic.elements.nodes.head == DynamicOptic.Node.Elements
        )
      },
      test("MapKeys node is singleton") {
        assertTrue(
          DynamicOptic.Node.MapKeys == DynamicOptic.Node.MapKeys,
          DynamicOptic.mapKeys.nodes.head == DynamicOptic.Node.MapKeys
        )
      },
      test("MapValues node is singleton") {
        assertTrue(
          DynamicOptic.Node.MapValues == DynamicOptic.Node.MapValues,
          DynamicOptic.mapValues.nodes.head == DynamicOptic.Node.MapValues
        )
      },
      test("Wrapped node is singleton") {
        assertTrue(
          DynamicOptic.Node.Wrapped == DynamicOptic.Node.Wrapped,
          DynamicOptic.wrapped.nodes.head == DynamicOptic.Node.Wrapped
        )
      },
      test("AtIndices node construction") {
        val atIndices = DynamicOptic.Node.AtIndices(Seq(0, 1, 2))
        assertTrue(
          atIndices.index == Seq(0, 1, 2),
          DynamicOptic.root.atIndices(0, 1, 2).nodes.head == atIndices
        )
      },
      test("AtMapKeys node construction") {
        val keys      = Seq("a", "b", "c").map(Schema[String].toDynamicValue)
        val atMapKeys = DynamicOptic.Node.AtMapKeys(keys)
        assertTrue(
          atMapKeys.keys == keys,
          DynamicOptic.root.atKeys("a", "b", "c").nodes.head == atMapKeys
        )
      }
    ),
    suite("DynamicOptic properties")(
      test("root has empty nodes") {
        assertTrue(DynamicOptic.root.nodes.isEmpty)
      },
      test("nodes are immutable") {
        val optic1 = DynamicOptic.root.field("a")
        val optic2 = optic1.field("b")
        assertTrue(
          optic1.nodes.length == 1,
          optic2.nodes.length == 2,
          optic1.nodes.head == DynamicOptic.Node.Field("a"),
          optic2.nodes.last == DynamicOptic.Node.Field("b")
        )
      },
      test("multiple compositions") {
        val optic = DynamicOptic.root
          .field("users")
          .at(0)
          .field("address")
          .field("city")
        assertTrue(
          optic.nodes.length == 4,
          optic.toString == ".users[0].address.city"
        )
      },
      test("mixed traversal paths") {
        val optic = DynamicOptic.root
          .caseOf("Person")
          .field("contacts")
          .elements
          .field("email")
        assertTrue(
          optic.nodes.length == 4,
          optic.toString.contains("<Person>")
        )
      }
    ),
    suite("DynamicOptic equality")(
      test("equal optics have same nodes") {
        val optic1 = DynamicOptic.root.field("a").field("b")
        val optic2 = DynamicOptic.root.field("a").field("b")
        assertTrue(optic1 == optic2, optic1.hashCode == optic2.hashCode)
      },
      test("different optics are not equal") {
        val optic1 = DynamicOptic.root.field("a")
        val optic2 = DynamicOptic.root.field("b")
        assertTrue(optic1 != optic2)
      },
      test("optics with different node types are not equal") {
        val optic1 = DynamicOptic.root.field("a")
        val optic2 = DynamicOptic.root.caseOf("a")
        assertTrue(optic1 != optic2)
      }
    ),
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

    implicit val schema: Schema[PosInt] =
      Schema[Int].transformOrFail[PosInt](PosInt.apply, _.value).asOpaqueType[PosInt]
  }
}
