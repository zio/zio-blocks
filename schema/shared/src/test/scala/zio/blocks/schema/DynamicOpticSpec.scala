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
    },
    suite("toScalaString method")(
      test("toScalaString uses Scala-style syntax for fields") {
        val optic = DynamicOptic.root.field("name").field("value")
        assertTrue(optic.toScalaString == ".name.value")
      },
      test("toScalaString uses when[Case] syntax for cases") {
        val optic = DynamicOptic.root.caseOf("Person").field("name")
        assertTrue(optic.toScalaString == ".when[Person].name")
      },
      test("toScalaString uses at(index) syntax for indices") {
        val optic = DynamicOptic.root.at(0).at(5)
        assertTrue(optic.toScalaString == ".at(0).at(5)")
      },
      test("toScalaString uses atIndices syntax for multiple indices") {
        val optic = DynamicOptic.root.atIndices(0, 1, 2)
        assertTrue(optic.toScalaString == ".atIndices(0, 1, 2)")
      },
      test("toScalaString uses atKey syntax for map keys") {
        val optic = DynamicOptic.root.atKey("myKey")
        assertTrue(optic.toScalaString.contains(".atKey("))
      },
      test("toScalaString uses atKeys syntax for multiple map keys") {
        val optic = DynamicOptic.root.atKeys("a", "b", "c")
        assertTrue(optic.toScalaString.contains(".atKeys("))
      },
      test("toScalaString uses .each for elements") {
        assertTrue(DynamicOptic.elements.toScalaString == ".each")
      },
      test("toScalaString uses .eachKey for mapKeys") {
        assertTrue(DynamicOptic.mapKeys.toScalaString == ".eachKey")
      },
      test("toScalaString uses .eachValue for mapValues") {
        assertTrue(DynamicOptic.mapValues.toScalaString == ".eachValue")
      },
      test("toScalaString uses .wrapped for wrapped") {
        assertTrue(DynamicOptic.wrapped.toScalaString == ".wrapped")
      },
      test("toScalaString returns . for root") {
        assertTrue(DynamicOptic.root.toScalaString == ".")
      },
      test("complex path toScalaString") {
        val optic = DynamicOptic.root
          .caseOf("Person")
          .field("addresses")
          .elements
          .field("city")
        assertTrue(optic.toScalaString == ".when[Person].addresses.each.city")
      }
    ),
    suite("DynamicOptic schema serialization")(
      test("DynamicOptic can be serialized to DynamicValue and back") {
        val optic    = DynamicOptic.root.field("name").at(0).caseOf("Test")
        val dynamic  = Schema[DynamicOptic].toDynamicValue(optic)
        val restored = Schema[DynamicOptic].fromDynamicValue(dynamic)
        assertTrue(restored == Right(optic))
      },
      test("DynamicOptic.Node.Field serialization round-trip") {
        val node     = DynamicOptic.Node.Field("testField")
        val dynamic  = Schema[DynamicOptic.Node].toDynamicValue(node)
        val restored = Schema[DynamicOptic.Node].fromDynamicValue(dynamic)
        assertTrue(restored == Right(node))
      },
      test("DynamicOptic.Node.Case serialization round-trip") {
        val node     = DynamicOptic.Node.Case("TestCase")
        val dynamic  = Schema[DynamicOptic.Node].toDynamicValue(node)
        val restored = Schema[DynamicOptic.Node].fromDynamicValue(dynamic)
        assertTrue(restored == Right(node))
      },
      test("DynamicOptic.Node.AtIndex serialization round-trip") {
        val node     = DynamicOptic.Node.AtIndex(42)
        val dynamic  = Schema[DynamicOptic.Node].toDynamicValue(node)
        val restored = Schema[DynamicOptic.Node].fromDynamicValue(dynamic)
        assertTrue(restored == Right(node))
      },
      test("DynamicOptic.Node.AtMapKey serialization round-trip") {
        val key      = Schema[String].toDynamicValue("mapKey")
        val node     = DynamicOptic.Node.AtMapKey(key)
        val dynamic  = Schema[DynamicOptic.Node].toDynamicValue(node)
        val restored = Schema[DynamicOptic.Node].fromDynamicValue(dynamic)
        assertTrue(restored == Right(node))
      },
      test("DynamicOptic.Node.AtIndices serialization round-trip") {
        val node     = DynamicOptic.Node.AtIndices(Seq(1, 2, 3))
        val dynamic  = Schema[DynamicOptic.Node].toDynamicValue(node)
        val restored = Schema[DynamicOptic.Node].fromDynamicValue(dynamic)
        assertTrue(restored == Right(node))
      },
      test("DynamicOptic.Node.AtMapKeys serialization round-trip") {
        val keys     = Seq("x", "y").map(Schema[String].toDynamicValue)
        val node     = DynamicOptic.Node.AtMapKeys(keys)
        val dynamic  = Schema[DynamicOptic.Node].toDynamicValue(node)
        val restored = Schema[DynamicOptic.Node].fromDynamicValue(dynamic)
        assertTrue(restored == Right(node))
      },
      test("DynamicOptic.Node.Elements serialization round-trip") {
        val node     = DynamicOptic.Node.Elements
        val dynamic  = Schema[DynamicOptic.Node].toDynamicValue(node)
        val restored = Schema[DynamicOptic.Node].fromDynamicValue(dynamic)
        assertTrue(restored == Right(node))
      },
      test("DynamicOptic.Node.MapKeys serialization round-trip") {
        val node     = DynamicOptic.Node.MapKeys
        val dynamic  = Schema[DynamicOptic.Node].toDynamicValue(node)
        val restored = Schema[DynamicOptic.Node].fromDynamicValue(dynamic)
        assertTrue(restored == Right(node))
      },
      test("DynamicOptic.Node.MapValues serialization round-trip") {
        val node     = DynamicOptic.Node.MapValues
        val dynamic  = Schema[DynamicOptic.Node].toDynamicValue(node)
        val restored = Schema[DynamicOptic.Node].fromDynamicValue(dynamic)
        assertTrue(restored == Right(node))
      },
      test("DynamicOptic.Node.Wrapped serialization round-trip") {
        val node     = DynamicOptic.Node.Wrapped
        val dynamic  = Schema[DynamicOptic.Node].toDynamicValue(node)
        val restored = Schema[DynamicOptic.Node].fromDynamicValue(dynamic)
        assertTrue(restored == Right(node))
      }
    ),
    suite("special character rendering in keys")(
      test("renders string keys with special characters correctly") {
        val optic = DynamicOptic.root.atKey("key\nwith\nnewlines")
        assertTrue(optic.toString.contains("\\n"))
      },
      test("renders string keys with tabs correctly") {
        val optic = DynamicOptic.root.atKey("key\twith\ttabs")
        assertTrue(optic.toString.contains("\\t"))
      },
      test("renders string keys with backslashes correctly") {
        val optic = DynamicOptic.root.atKey("key\\with\\backslash")
        assertTrue(optic.toString.contains("\\\\"))
      },
      test("renders string keys with quotes correctly") {
        val optic = DynamicOptic.root.atKey("key\"with\"quotes")
        assertTrue(optic.toString.contains("\\\""))
      },
      test("renders integer keys") {
        val intKey = Schema[Int].toDynamicValue(42)
        val optic  = DynamicOptic(Vector(DynamicOptic.Node.AtMapKey(intKey)))
        assertTrue(optic.toString.contains("42"))
      },
      test("renders long keys") {
        val longKey = Schema[Long].toDynamicValue(123456789L)
        val optic   = DynamicOptic(Vector(DynamicOptic.Node.AtMapKey(longKey)))
        assertTrue(optic.toString.contains("123456789"))
      },
      test("renders float keys") {
        val floatKey = Schema[Float].toDynamicValue(3.14f)
        val optic    = DynamicOptic(Vector(DynamicOptic.Node.AtMapKey(floatKey)))
        assertTrue(optic.toString.contains("3.14"))
      },
      test("renders double keys") {
        val doubleKey = Schema[Double].toDynamicValue(2.718)
        val optic     = DynamicOptic(Vector(DynamicOptic.Node.AtMapKey(doubleKey)))
        assertTrue(optic.toString.contains("2.718"))
      },
      test("renders boolean keys") {
        val boolKey = Schema[Boolean].toDynamicValue(true)
        val optic   = DynamicOptic(Vector(DynamicOptic.Node.AtMapKey(boolKey)))
        assertTrue(optic.toString.contains("true"))
      }
    ),
    suite("Node schema comprehensive tests")(
      test("Field roundtrip with various names") {
        val names   = List("simple", "with_underscore", "CamelCase", "a", "x123", "_private")
        val results = names.map { name =>
          val node     = DynamicOptic.Node.Field(name)
          val dv       = Schema[DynamicOptic.Node].toDynamicValue(node)
          val restored = Schema[DynamicOptic.Node].fromDynamicValue(dv)
          restored == Right(node)
        }
        assertTrue(results.forall(identity))
      },
      test("Case roundtrip with various names") {
        val names   = List("Some", "None", "Left", "Right", "Person", "A")
        val results = names.map { name =>
          val node     = DynamicOptic.Node.Case(name)
          val dv       = Schema[DynamicOptic.Node].toDynamicValue(node)
          val restored = Schema[DynamicOptic.Node].fromDynamicValue(dv)
          restored == Right(node)
        }
        assertTrue(results.forall(identity))
      },
      test("AtIndex roundtrip with edge values") {
        val indices = List(0, 1, 10, 100, Int.MaxValue)
        val results = indices.map { idx =>
          val node     = DynamicOptic.Node.AtIndex(idx)
          val dv       = Schema[DynamicOptic.Node].toDynamicValue(node)
          val restored = Schema[DynamicOptic.Node].fromDynamicValue(dv)
          restored == Right(node)
        }
        assertTrue(results.forall(identity))
      },
      test("AtIndices roundtrip with various sequences") {
        val cases = List(
          Seq(0),
          Seq(0, 1),
          Seq(0, 1, 2, 3, 4),
          Seq(10, 20, 30)
        )
        val results = cases.map { indices =>
          val node     = DynamicOptic.Node.AtIndices(indices)
          val dv       = Schema[DynamicOptic.Node].toDynamicValue(node)
          val restored = Schema[DynamicOptic.Node].fromDynamicValue(dv)
          restored == Right(node)
        }
        assertTrue(results.forall(identity))
      },
      test("AtMapKey roundtrip with various primitives") {
        val keys = List(
          DynamicValue.Primitive(PrimitiveValue.String("key")),
          DynamicValue.Primitive(PrimitiveValue.Int(42)),
          DynamicValue.Primitive(PrimitiveValue.Long(123L)),
          DynamicValue.Primitive(PrimitiveValue.Boolean(true)),
          DynamicValue.Primitive(PrimitiveValue.Char('x'))
        )
        val results = keys.map { key =>
          val node     = DynamicOptic.Node.AtMapKey(key)
          val dv       = Schema[DynamicOptic.Node].toDynamicValue(node)
          val restored = Schema[DynamicOptic.Node].fromDynamicValue(dv)
          restored == Right(node)
        }
        assertTrue(results.forall(identity))
      },
      test("AtMapKeys roundtrip with multiple keys") {
        val keyVectors = List(
          Vector(DynamicValue.Primitive(PrimitiveValue.String("a"))),
          Vector(
            DynamicValue.Primitive(PrimitiveValue.String("a")),
            DynamicValue.Primitive(PrimitiveValue.String("b"))
          ),
          Vector(
            DynamicValue.Primitive(PrimitiveValue.Int(1)),
            DynamicValue.Primitive(PrimitiveValue.Int(2)),
            DynamicValue.Primitive(PrimitiveValue.Int(3))
          )
        )
        val results = keyVectors.map { keys =>
          val node     = DynamicOptic.Node.AtMapKeys(keys)
          val dv       = Schema[DynamicOptic.Node].toDynamicValue(node)
          val restored = Schema[DynamicOptic.Node].fromDynamicValue(dv)
          restored == Right(node)
        }
        assertTrue(results.forall(identity))
      },
      test("Singleton nodes roundtrip") {
        val nodes = List(
          DynamicOptic.Node.Elements,
          DynamicOptic.Node.MapKeys,
          DynamicOptic.Node.MapValues,
          DynamicOptic.Node.Wrapped
        )
        val results = nodes.map { node =>
          val dv       = Schema[DynamicOptic.Node].toDynamicValue(node)
          val restored = Schema[DynamicOptic.Node].fromDynamicValue(dv)
          restored == Right(node)
        }
        assertTrue(results.forall(identity))
      },
      test("DynamicOptic roundtrip with complex path") {
        val optic = DynamicOptic.root
          .field("user")
          .caseOf("Admin")
          .field("permissions")
          .at(0)
          .field("name")
        val dv       = Schema[DynamicOptic].toDynamicValue(optic)
        val restored = Schema[DynamicOptic].fromDynamicValue(dv)
        assertTrue(restored == Right(optic))
      },
      test("DynamicOptic roundtrip with all node types") {
        val optic = DynamicOptic.root
          .field("data")
          .caseOf("Container")
          .at(0)
          .atKey("item")
          .elements
          .mapKeys
          .mapValues
          .wrapped
        val dv       = Schema[DynamicOptic].toDynamicValue(optic)
        val restored = Schema[DynamicOptic].fromDynamicValue(dv)
        assertTrue(restored == Right(optic))
      },
      test("DynamicOptic roundtrip with atIndices and atKeys") {
        val optic = DynamicOptic.root
          .atIndices(0, 1, 2)
          .atKeys("a", "b", "c")
        val dv       = Schema[DynamicOptic].toDynamicValue(optic)
        val restored = Schema[DynamicOptic].fromDynamicValue(dv)
        assertTrue(restored == Right(optic))
      },
      test("Node equality across serialization") {
        val node1 = DynamicOptic.Node.Field("test")
        val node2 = DynamicOptic.Node.Field("test")
        val dv1   = Schema[DynamicOptic.Node].toDynamicValue(node1)
        val dv2   = Schema[DynamicOptic.Node].toDynamicValue(node2)
        assertTrue(dv1 == dv2)
      },
      test("Different nodes produce different DynamicValues") {
        val nodes = List(
          DynamicOptic.Node.Field("a"),
          DynamicOptic.Node.Case("a"),
          DynamicOptic.Node.AtIndex(0),
          DynamicOptic.Node.Elements
        )
        val dvs          = nodes.map(Schema[DynamicOptic.Node].toDynamicValue)
        val allDifferent = dvs.combinations(2).forall { case a :: b :: Nil => a != b; case _ => true }
        assertTrue(allDifferent)
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
