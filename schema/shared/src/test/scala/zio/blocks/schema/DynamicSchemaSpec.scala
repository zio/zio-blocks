package zio.blocks.schema

import zio.blocks.chunk.Chunk
import zio.test._

object DynamicSchemaSpec extends SchemaBaseSpec {
  private def getTypeName(ds: DynamicSchema): String = ds.typeId.name
  case class Person(name: String, age: Int)
  object Person {
    implicit val schema: Schema[Person] = Schema.derived
  }

  case class Address(street: String, city: String, zip: Option[String])
  object Address {
    implicit val schema: Schema[Address] = Schema.derived
  }

  sealed trait Color
  object Color {
    case object Red   extends Color
    case object Green extends Color
    case object Blue  extends Color

    implicit val schema: Schema[Color] = Schema.derived
  }

  sealed trait Tree
  object Tree {
    case class Leaf(value: Int)              extends Tree
    case class Node(left: Tree, right: Tree) extends Tree

    implicit val leafSchema: Schema[Leaf] = Schema.derived
    implicit val nodeSchema: Schema[Node] = Schema.derived
    implicit val schema: Schema[Tree]     = Schema.derived
  }

  def spec: Spec[TestEnvironment, Any] = suite("DynamicSchemaSpec")(
    suite("toDynamicSchema")(
      test("primitive schema converts to DynamicSchema") {
        val ds   = Schema[Int].toDynamicSchema
        val name = ds.typeId.name
        assertTrue(name == "Int")
      },
      test("record schema converts to DynamicSchema") {
        val ds   = Schema[Person].toDynamicSchema
        val name = ds.typeId.name
        assertTrue(name == "Person")
      },
      test("variant schema converts to DynamicSchema") {
        val ds   = Schema[Color].toDynamicSchema
        val name = ds.typeId.name
        assertTrue(name == "Color")
      },
      test("sequence schema converts to DynamicSchema") {
        val ds   = Schema[List[Int]].toDynamicSchema
        val name = ds.typeId.name
        assertTrue(name == "List")
      },
      test("map schema converts to DynamicSchema") {
        val ds   = Schema[Map[String, Int]].toDynamicSchema
        val name = ds.typeId.name
        assertTrue(name == "Map")
      },
      test("option schema converts to DynamicSchema") {
        val ds   = Schema[Option[Int]].toDynamicSchema
        val name = ds.typeId.name
        assertTrue(name == "Option")
      }
    ),
    suite("check - Record validation")(
      test("record with matching fields passes") {
        val ds = Schema[Person].toDynamicSchema
        val dv = DynamicValue.Record(
          Chunk(
            "name" -> DynamicValue.Primitive(PrimitiveValue.String("Alice")),
            "age"  -> DynamicValue.Primitive(PrimitiveValue.Int(30))
          )
        )
        assertTrue(ds.check(dv).isEmpty)
      },
      test("record with missing field fails") {
        val ds = Schema[Person].toDynamicSchema
        val dv = DynamicValue.Record(
          Chunk("name" -> DynamicValue.Primitive(PrimitiveValue.String("Alice")))
        )
        assertTrue(ds.check(dv).isDefined)
      },
      test("record with extra field fails") {
        val ds = Schema[Person].toDynamicSchema
        val dv = DynamicValue.Record(
          Chunk(
            "name"  -> DynamicValue.Primitive(PrimitiveValue.String("Alice")),
            "age"   -> DynamicValue.Primitive(PrimitiveValue.Int(30)),
            "extra" -> DynamicValue.Primitive(PrimitiveValue.String("unexpected"))
          )
        )
        assertTrue(ds.check(dv).isDefined)
      },
      test("record with wrong field type fails") {
        val ds = Schema[Person].toDynamicSchema
        val dv = DynamicValue.Record(
          Chunk(
            "name" -> DynamicValue.Primitive(PrimitiveValue.String("Alice")),
            "age"  -> DynamicValue.Primitive(PrimitiveValue.String("thirty"))
          )
        )
        assertTrue(ds.check(dv).isDefined)
      },
      test("nested record validates correctly") {
        val ds = Schema[Address].toDynamicSchema
        val dv = DynamicValue.Record(
          Chunk(
            "street" -> DynamicValue.Primitive(PrimitiveValue.String("123 Main")),
            "city"   -> DynamicValue.Primitive(PrimitiveValue.String("Springfield")),
            "zip"    -> DynamicValue.Variant(
              "Some",
              DynamicValue.Record(Chunk("value" -> DynamicValue.Primitive(PrimitiveValue.String("12345"))))
            )
          )
        )
        assertTrue(ds.check(dv).isEmpty)
      }
    ),
    suite("check - Variant validation")(
      test("variant with valid case passes") {
        val ds = Schema[Color].toDynamicSchema
        val dv = DynamicValue.Variant("Red", DynamicValue.Record(Chunk.empty))
        assertTrue(ds.check(dv).isEmpty)
      },
      test("variant with unknown case fails") {
        val ds = Schema[Color].toDynamicSchema
        val dv = DynamicValue.Variant("Yellow", DynamicValue.Record(Chunk.empty))
        assertTrue(ds.check(dv).isDefined)
      },
      test("variant with wrong case value type fails") {
        val ds = Schema[Tree].toDynamicSchema
        val dv = DynamicValue.Variant(
          "Leaf",
          DynamicValue.Record(Chunk("value" -> DynamicValue.Primitive(PrimitiveValue.String("not an int"))))
        )
        assertTrue(ds.check(dv).isDefined)
      }
    ),
    suite("check - Sequence validation")(
      test("sequence with valid elements passes") {
        val ds = Schema[List[Int]].toDynamicSchema
        val dv = DynamicValue.Sequence(
          Chunk(
            DynamicValue.Primitive(PrimitiveValue.Int(1)),
            DynamicValue.Primitive(PrimitiveValue.Int(2)),
            DynamicValue.Primitive(PrimitiveValue.Int(3))
          )
        )
        assertTrue(ds.check(dv).isEmpty)
      },
      test("sequence with invalid element fails") {
        val ds = Schema[List[Int]].toDynamicSchema
        val dv = DynamicValue.Sequence(
          Chunk(
            DynamicValue.Primitive(PrimitiveValue.Int(1)),
            DynamicValue.Primitive(PrimitiveValue.String("two")),
            DynamicValue.Primitive(PrimitiveValue.Int(3))
          )
        )
        assertTrue(ds.check(dv).isDefined)
      },
      test("empty sequence passes") {
        val ds = Schema[List[Int]].toDynamicSchema
        val dv = DynamicValue.Sequence(Chunk.empty)
        assertTrue(ds.check(dv).isEmpty)
      }
    ),
    suite("check - Map validation")(
      test("map with valid entries passes") {
        val ds = Schema[Map[String, Int]].toDynamicSchema
        val dv = DynamicValue.Map(
          Chunk(
            DynamicValue.Primitive(PrimitiveValue.String("a")) -> DynamicValue.Primitive(PrimitiveValue.Int(1)),
            DynamicValue.Primitive(PrimitiveValue.String("b")) -> DynamicValue.Primitive(PrimitiveValue.Int(2))
          )
        )
        assertTrue(ds.check(dv).isEmpty)
      },
      test("map with invalid key fails") {
        val ds = Schema[Map[String, Int]].toDynamicSchema
        val dv = DynamicValue.Map(
          Chunk(
            DynamicValue.Primitive(PrimitiveValue.Int(1)) -> DynamicValue.Primitive(PrimitiveValue.Int(1))
          )
        )
        assertTrue(ds.check(dv).isDefined)
      },
      test("map with invalid value fails") {
        val ds = Schema[Map[String, Int]].toDynamicSchema
        val dv = DynamicValue.Map(
          Chunk(
            DynamicValue.Primitive(PrimitiveValue.String("a")) -> DynamicValue.Primitive(
              PrimitiveValue.String("not int")
            )
          )
        )
        assertTrue(ds.check(dv).isDefined)
      }
    ),
    suite("check - Primitive validation")(
      test("primitive with no validation passes") {
        val ds = Schema[Int].toDynamicSchema
        val dv = DynamicValue.Primitive(PrimitiveValue.Int(42))
        assertTrue(ds.check(dv).isEmpty)
      },
      test("primitive type mismatch fails") {
        val ds = Schema[Int].toDynamicSchema
        val dv = DynamicValue.Primitive(PrimitiveValue.String("not an int"))
        assertTrue(ds.check(dv).isDefined)
      },
      test("non-Primitive value against primitive schema fails") {
        val ds = Schema[Int].toDynamicSchema
        val dv = DynamicValue.Record(Chunk.empty)
        assertTrue(ds.check(dv).isDefined)
      }
    ),
    suite("check - Numeric validation")(
      test("Numeric.Positive passes for positive value") {
        val schema = Schema[Int].reflect.asPrimitive
          .map(p =>
            new Reflect.Primitive[binding.Binding, Int](
              PrimitiveType.Int(Validation.Numeric.Positive),
              p.typeId,
              p.primitiveBinding
            )
          )
          .map(r => new Schema(r))
          .getOrElse(Schema[Int])
        val ds = schema.toDynamicSchema
        val dv = DynamicValue.Primitive(PrimitiveValue.Int(5))
        assertTrue(ds.check(dv).isEmpty)
      },
      test("Numeric.Positive fails for zero") {
        val schema = Schema[Int].reflect.asPrimitive
          .map(p =>
            new Reflect.Primitive[binding.Binding, Int](
              PrimitiveType.Int(Validation.Numeric.Positive),
              p.typeId,
              p.primitiveBinding
            )
          )
          .map(r => new Schema(r))
          .getOrElse(Schema[Int])
        val ds = schema.toDynamicSchema
        val dv = DynamicValue.Primitive(PrimitiveValue.Int(0))
        assertTrue(ds.check(dv).isDefined)
      },
      test("Numeric.Positive fails for negative") {
        val schema = Schema[Int].reflect.asPrimitive
          .map(p =>
            new Reflect.Primitive[binding.Binding, Int](
              PrimitiveType.Int(Validation.Numeric.Positive),
              p.typeId,
              p.primitiveBinding
            )
          )
          .map(r => new Schema(r))
          .getOrElse(Schema[Int])
        val ds = schema.toDynamicSchema
        val dv = DynamicValue.Primitive(PrimitiveValue.Int(-5))
        assertTrue(ds.check(dv).isDefined)
      },
      test("Numeric.Negative passes for negative") {
        val schema = Schema[Int].reflect.asPrimitive
          .map(p =>
            new Reflect.Primitive[binding.Binding, Int](
              PrimitiveType.Int(Validation.Numeric.Negative),
              p.typeId,
              p.primitiveBinding
            )
          )
          .map(r => new Schema(r))
          .getOrElse(Schema[Int])
        val ds = schema.toDynamicSchema
        val dv = DynamicValue.Primitive(PrimitiveValue.Int(-5))
        assertTrue(ds.check(dv).isEmpty)
      },
      test("Numeric.Negative fails for positive") {
        val schema = Schema[Int].reflect.asPrimitive
          .map(p =>
            new Reflect.Primitive[binding.Binding, Int](
              PrimitiveType.Int(Validation.Numeric.Negative),
              p.typeId,
              p.primitiveBinding
            )
          )
          .map(r => new Schema(r))
          .getOrElse(Schema[Int])
        val ds = schema.toDynamicSchema
        val dv = DynamicValue.Primitive(PrimitiveValue.Int(5))
        assertTrue(ds.check(dv).isDefined)
      },
      test("Numeric.NonPositive passes for zero") {
        val schema = Schema[Int].reflect.asPrimitive
          .map(p =>
            new Reflect.Primitive[binding.Binding, Int](
              PrimitiveType.Int(Validation.Numeric.NonPositive),
              p.typeId,
              p.primitiveBinding
            )
          )
          .map(r => new Schema(r))
          .getOrElse(Schema[Int])
        val ds = schema.toDynamicSchema
        val dv = DynamicValue.Primitive(PrimitiveValue.Int(0))
        assertTrue(ds.check(dv).isEmpty)
      },
      test("Numeric.NonPositive passes for negative") {
        val schema = Schema[Int].reflect.asPrimitive
          .map(p =>
            new Reflect.Primitive[binding.Binding, Int](
              PrimitiveType.Int(Validation.Numeric.NonPositive),
              p.typeId,
              p.primitiveBinding
            )
          )
          .map(r => new Schema(r))
          .getOrElse(Schema[Int])
        val ds = schema.toDynamicSchema
        val dv = DynamicValue.Primitive(PrimitiveValue.Int(-5))
        assertTrue(ds.check(dv).isEmpty)
      },
      test("Numeric.NonNegative passes for zero") {
        val schema = Schema[Int].reflect.asPrimitive
          .map(p =>
            new Reflect.Primitive[binding.Binding, Int](
              PrimitiveType.Int(Validation.Numeric.NonNegative),
              p.typeId,
              p.primitiveBinding
            )
          )
          .map(r => new Schema(r))
          .getOrElse(Schema[Int])
        val ds = schema.toDynamicSchema
        val dv = DynamicValue.Primitive(PrimitiveValue.Int(0))
        assertTrue(ds.check(dv).isEmpty)
      },
      test("Numeric.NonNegative passes for positive") {
        val schema = Schema[Int].reflect.asPrimitive
          .map(p =>
            new Reflect.Primitive[binding.Binding, Int](
              PrimitiveType.Int(Validation.Numeric.NonNegative),
              p.typeId,
              p.primitiveBinding
            )
          )
          .map(r => new Schema(r))
          .getOrElse(Schema[Int])
        val ds = schema.toDynamicSchema
        val dv = DynamicValue.Primitive(PrimitiveValue.Int(5))
        assertTrue(ds.check(dv).isEmpty)
      },
      test("Numeric.Range passes for value in range") {
        val schema = Schema[Int].reflect.asPrimitive
          .map(p =>
            new Reflect.Primitive[binding.Binding, Int](
              PrimitiveType.Int(Validation.Numeric.Range(Some(1), Some(10))),
              p.typeId,
              p.primitiveBinding
            )
          )
          .map(r => new Schema(r))
          .getOrElse(Schema[Int])
        val ds = schema.toDynamicSchema
        val dv = DynamicValue.Primitive(PrimitiveValue.Int(5))
        assertTrue(ds.check(dv).isEmpty)
      },
      test("Numeric.Range fails for value below min") {
        val schema = Schema[Int].reflect.asPrimitive
          .map(p =>
            new Reflect.Primitive[binding.Binding, Int](
              PrimitiveType.Int(Validation.Numeric.Range(Some(1), Some(10))),
              p.typeId,
              p.primitiveBinding
            )
          )
          .map(r => new Schema(r))
          .getOrElse(Schema[Int])
        val ds = schema.toDynamicSchema
        val dv = DynamicValue.Primitive(PrimitiveValue.Int(0))
        assertTrue(ds.check(dv).isDefined)
      },
      test("Numeric.Range fails for value above max") {
        val schema = Schema[Int].reflect.asPrimitive
          .map(p =>
            new Reflect.Primitive[binding.Binding, Int](
              PrimitiveType.Int(Validation.Numeric.Range(Some(1), Some(10))),
              p.typeId,
              p.primitiveBinding
            )
          )
          .map(r => new Schema(r))
          .getOrElse(Schema[Int])
        val ds = schema.toDynamicSchema
        val dv = DynamicValue.Primitive(PrimitiveValue.Int(20))
        assertTrue(ds.check(dv).isDefined)
      },
      test("Numeric.Set passes for value in set") {
        val schema = Schema[Int].reflect.asPrimitive
          .map(p =>
            new Reflect.Primitive[binding.Binding, Int](
              PrimitiveType.Int(Validation.Numeric.Set(Set(1, 2, 3))),
              p.typeId,
              p.primitiveBinding
            )
          )
          .map(r => new Schema(r))
          .getOrElse(Schema[Int])
        val ds = schema.toDynamicSchema
        val dv = DynamicValue.Primitive(PrimitiveValue.Int(2))
        assertTrue(ds.check(dv).isEmpty)
      },
      test("Numeric.Set fails for value not in set") {
        val schema = Schema[Int].reflect.asPrimitive
          .map(p =>
            new Reflect.Primitive[binding.Binding, Int](
              PrimitiveType.Int(Validation.Numeric.Set(Set(1, 2, 3))),
              p.typeId,
              p.primitiveBinding
            )
          )
          .map(r => new Schema(r))
          .getOrElse(Schema[Int])
        val ds = schema.toDynamicSchema
        val dv = DynamicValue.Primitive(PrimitiveValue.Int(5))
        assertTrue(ds.check(dv).isDefined)
      }
    ),
    suite("check - String validation")(
      test("String.NonEmpty passes for non-empty") {
        val schema = Schema[String].reflect.asPrimitive
          .map(p =>
            new Reflect.Primitive[binding.Binding, String](
              PrimitiveType.String(Validation.String.NonEmpty),
              p.typeId,
              p.primitiveBinding
            )
          )
          .map(r => new Schema(r))
          .getOrElse(Schema[String])
        val ds = schema.toDynamicSchema
        val dv = DynamicValue.Primitive(PrimitiveValue.String("hello"))
        assertTrue(ds.check(dv).isEmpty)
      },
      test("String.NonEmpty fails for empty") {
        val schema = Schema[String].reflect.asPrimitive
          .map(p =>
            new Reflect.Primitive[binding.Binding, String](
              PrimitiveType.String(Validation.String.NonEmpty),
              p.typeId,
              p.primitiveBinding
            )
          )
          .map(r => new Schema(r))
          .getOrElse(Schema[String])
        val ds = schema.toDynamicSchema
        val dv = DynamicValue.Primitive(PrimitiveValue.String(""))
        assertTrue(ds.check(dv).isDefined)
      },
      test("String.Empty passes for empty") {
        val schema = Schema[String].reflect.asPrimitive
          .map(p =>
            new Reflect.Primitive[binding.Binding, String](
              PrimitiveType.String(Validation.String.Empty),
              p.typeId,
              p.primitiveBinding
            )
          )
          .map(r => new Schema(r))
          .getOrElse(Schema[String])
        val ds = schema.toDynamicSchema
        val dv = DynamicValue.Primitive(PrimitiveValue.String(""))
        assertTrue(ds.check(dv).isEmpty)
      },
      test("String.Empty fails for non-empty") {
        val schema = Schema[String].reflect.asPrimitive
          .map(p =>
            new Reflect.Primitive[binding.Binding, String](
              PrimitiveType.String(Validation.String.Empty),
              p.typeId,
              p.primitiveBinding
            )
          )
          .map(r => new Schema(r))
          .getOrElse(Schema[String])
        val ds = schema.toDynamicSchema
        val dv = DynamicValue.Primitive(PrimitiveValue.String("hello"))
        assertTrue(ds.check(dv).isDefined)
      },
      test("String.Blank passes for blank string") {
        val schema = Schema[String].reflect.asPrimitive
          .map(p =>
            new Reflect.Primitive[binding.Binding, String](
              PrimitiveType.String(Validation.String.Blank),
              p.typeId,
              p.primitiveBinding
            )
          )
          .map(r => new Schema(r))
          .getOrElse(Schema[String])
        val ds = schema.toDynamicSchema
        val dv = DynamicValue.Primitive(PrimitiveValue.String("   "))
        assertTrue(ds.check(dv).isEmpty)
      },
      test("String.NonBlank passes for non-blank") {
        val schema = Schema[String].reflect.asPrimitive
          .map(p =>
            new Reflect.Primitive[binding.Binding, String](
              PrimitiveType.String(Validation.String.NonBlank),
              p.typeId,
              p.primitiveBinding
            )
          )
          .map(r => new Schema(r))
          .getOrElse(Schema[String])
        val ds = schema.toDynamicSchema
        val dv = DynamicValue.Primitive(PrimitiveValue.String("hello"))
        assertTrue(ds.check(dv).isEmpty)
      },
      test("String.Length passes for valid length") {
        val schema = Schema[String].reflect.asPrimitive
          .map(p =>
            new Reflect.Primitive[binding.Binding, String](
              PrimitiveType.String(Validation.String.Length(Some(2), Some(10))),
              p.typeId,
              p.primitiveBinding
            )
          )
          .map(r => new Schema(r))
          .getOrElse(Schema[String])
        val ds = schema.toDynamicSchema
        val dv = DynamicValue.Primitive(PrimitiveValue.String("hello"))
        assertTrue(ds.check(dv).isEmpty)
      },
      test("String.Length fails for too short") {
        val schema = Schema[String].reflect.asPrimitive
          .map(p =>
            new Reflect.Primitive[binding.Binding, String](
              PrimitiveType.String(Validation.String.Length(Some(5), Some(10))),
              p.typeId,
              p.primitiveBinding
            )
          )
          .map(r => new Schema(r))
          .getOrElse(Schema[String])
        val ds = schema.toDynamicSchema
        val dv = DynamicValue.Primitive(PrimitiveValue.String("hi"))
        assertTrue(ds.check(dv).isDefined)
      },
      test("String.Length fails for too long") {
        val schema = Schema[String].reflect.asPrimitive
          .map(p =>
            new Reflect.Primitive[binding.Binding, String](
              PrimitiveType.String(Validation.String.Length(Some(1), Some(3))),
              p.typeId,
              p.primitiveBinding
            )
          )
          .map(r => new Schema(r))
          .getOrElse(Schema[String])
        val ds = schema.toDynamicSchema
        val dv = DynamicValue.Primitive(PrimitiveValue.String("hello"))
        assertTrue(ds.check(dv).isDefined)
      },
      test("String.Pattern passes for matching") {
        val schema = Schema[String].reflect.asPrimitive
          .map(p =>
            new Reflect.Primitive[binding.Binding, String](
              PrimitiveType.String(Validation.String.Pattern("^[a-z]+$")),
              p.typeId,
              p.primitiveBinding
            )
          )
          .map(r => new Schema(r))
          .getOrElse(Schema[String])
        val ds = schema.toDynamicSchema
        val dv = DynamicValue.Primitive(PrimitiveValue.String("hello"))
        assertTrue(ds.check(dv).isEmpty)
      },
      test("String.Pattern fails for non-matching") {
        val schema = Schema[String].reflect.asPrimitive
          .map(p =>
            new Reflect.Primitive[binding.Binding, String](
              PrimitiveType.String(Validation.String.Pattern("^[a-z]+$")),
              p.typeId,
              p.primitiveBinding
            )
          )
          .map(r => new Schema(r))
          .getOrElse(Schema[String])
        val ds = schema.toDynamicSchema
        val dv = DynamicValue.Primitive(PrimitiveValue.String("Hello123"))
        assertTrue(ds.check(dv).isDefined)
      }
    ),
    suite("check - Dynamic validation")(
      test("dynamic schema accepts any value") {
        val ds = Schema[DynamicValue].toDynamicSchema
        assertTrue(ds.check(DynamicValue.Primitive(PrimitiveValue.Int(42))).isEmpty) &&
        assertTrue(ds.check(DynamicValue.Record(Chunk.empty)).isEmpty) &&
        assertTrue(ds.check(DynamicValue.Sequence(Chunk.empty)).isEmpty) &&
        assertTrue(ds.check(DynamicValue.Null).isEmpty)
      }
    ),
    suite("conforms")(
      test("conforms returns true when check passes") {
        val ds = Schema[Int].toDynamicSchema
        val dv = DynamicValue.Primitive(PrimitiveValue.Int(42))
        assertTrue(ds.conforms(dv))
      },
      test("conforms returns false when check fails") {
        val ds = Schema[Int].toDynamicSchema
        val dv = DynamicValue.Primitive(PrimitiveValue.String("not int"))
        assertTrue(!ds.conforms(dv))
      }
    ),
    suite("DynamicValue.check/conforms")(
      test("DynamicValue.check delegates to DynamicSchema.check") {
        val ds = Schema[Int].toDynamicSchema
        val dv = DynamicValue.Primitive(PrimitiveValue.Int(42))
        assertTrue(dv.check(ds).isEmpty)
      },
      test("DynamicValue.conforms delegates to DynamicSchema.conforms") {
        val ds = Schema[Int].toDynamicSchema
        val dv = DynamicValue.Primitive(PrimitiveValue.Int(42))
        assertTrue(dv.conforms(ds))
      }
    ),
    suite("toSchema")(
      test("toSchema creates schema that validates conforming values") {
        val ds      = Schema[Person].toDynamicSchema
        val validDv = DynamicValue.Record(
          Chunk(
            "name" -> DynamicValue.Primitive(PrimitiveValue.String("Alice")),
            "age"  -> DynamicValue.Primitive(PrimitiveValue.Int(30))
          )
        )
        val schema = ds.toSchema
        assertTrue(schema.fromDynamicValue(validDv).isRight)
      },
      test("toSchema creates schema that rejects non-conforming values") {
        val ds        = Schema[Person].toDynamicSchema
        val invalidDv = DynamicValue.Record(
          Chunk("name" -> DynamicValue.Primitive(PrimitiveValue.String("Alice")))
        )
        val schema = ds.toSchema
        assertTrue(schema.fromDynamicValue(invalidDv).isLeft)
      }
    ),
    suite("delegated methods")(
      test("doc returns reflect.doc") {
        val ds = Schema[Person].doc("A person").toDynamicSchema
        assertTrue(ds.doc == Doc.Text("A person"))
      },
      test("doc(value) updates reflect") {
        val ds  = Schema[Person].toDynamicSchema
        val ds2 = ds.doc("Updated doc")
        assertTrue(ds2.doc == Doc.Text("Updated doc"))
      },
      test("typeId returns reflect.typeId") {
        val ds   = Schema[Person].toDynamicSchema
        val name = ds.typeId.name
        assertTrue(name == "Person")
      },
      test("modifiers returns reflect.modifiers") {
        val ds = Schema[Person].modifier(Modifier.config("key", "value")).toDynamicSchema
        assertTrue(ds.modifiers.nonEmpty)
      },
      test("modifier adds to reflect") {
        val ds  = Schema[Person].toDynamicSchema
        val ds2 = ds.modifier(Modifier.config("key", "value"))
        assertTrue(ds2.modifiers.nonEmpty)
      },
      test("get(optic) navigates structure") {
        val ds    = Schema[Person].toDynamicSchema
        val optic = DynamicOptic.root.field("name")
        assertTrue(ds.get(optic).isDefined)
      }
    ),
    suite("defaultValue and examples")(
      test("getDefaultValue returns None when not set") {
        val ds = Schema[Int].toDynamicSchema
        assertTrue(ds.getDefaultValue.isEmpty)
      },
      test("defaultValue sets and getDefaultValue retrieves") {
        val ds  = Schema[Int].toDynamicSchema
        val dv  = DynamicValue.Primitive(PrimitiveValue.Int(42))
        val ds2 = ds.defaultValue(dv)
        assertTrue(ds2.getDefaultValue.contains(dv))
      },
      test("examples returns empty when not set") {
        val ds = Schema[Int].toDynamicSchema
        assertTrue(ds.examples.isEmpty)
      },
      test("examples sets and retrieves") {
        val ds  = Schema[Int].toDynamicSchema
        val dv1 = DynamicValue.Primitive(PrimitiveValue.Int(1))
        val dv2 = DynamicValue.Primitive(PrimitiveValue.Int(2))
        val ds2 = ds.examples(dv1, dv2)
        assertTrue(ds2.examples.length == 2)
      },
      test("defaultValue works through Deferred (recursive schema)") {
        val ds = Schema[Tree].toDynamicSchema
        val dv = DynamicValue.Variant(
          "Leaf",
          DynamicValue.Record(Chunk("value" -> DynamicValue.Primitive(PrimitiveValue.Int(42))))
        )
        val ds2 = ds.defaultValue(dv)
        assertTrue(ds2.getDefaultValue.contains(dv))
      },
      test("examples works through Deferred (recursive schema)") {
        val ds  = Schema[Tree].toDynamicSchema
        val dv1 = DynamicValue.Variant(
          "Leaf",
          DynamicValue.Record(Chunk("value" -> DynamicValue.Primitive(PrimitiveValue.Int(1))))
        )
        val dv2 = DynamicValue.Variant(
          "Leaf",
          DynamicValue.Record(Chunk("value" -> DynamicValue.Primitive(PrimitiveValue.Int(2))))
        )
        val ds2 = ds.examples(dv1, dv2)
        assertTrue(ds2.examples.length == 2) &&
        assertTrue(ds2.examples.contains(dv1)) &&
        assertTrue(ds2.examples.contains(dv2))
      }
    ),
    suite("type mismatch errors")(
      test("string value against int schema fails") {
        val ds = Schema[Int].toDynamicSchema
        val dv = DynamicValue.Primitive(PrimitiveValue.String("not int"))
        assertTrue(ds.check(dv).isDefined)
      },
      test("record value against sequence schema fails") {
        val ds = Schema[List[Int]].toDynamicSchema
        val dv = DynamicValue.Record(Chunk.empty)
        assertTrue(ds.check(dv).isDefined)
      },
      test("sequence value against record schema fails") {
        val ds = Schema[Person].toDynamicSchema
        val dv = DynamicValue.Sequence(Chunk.empty)
        assertTrue(ds.check(dv).isDefined)
      },
      test("variant value against primitive schema fails") {
        val ds = Schema[Int].toDynamicSchema
        val dv = DynamicValue.Variant("SomeCase", DynamicValue.Null)
        assertTrue(ds.check(dv).isDefined)
      }
    ),
    suite("recursive schemas")(
      test("recursive schema validates correctly - Leaf case") {
        val ds = Schema[Tree].toDynamicSchema
        val dv = DynamicValue.Variant(
          "Leaf",
          DynamicValue.Record(Chunk("value" -> DynamicValue.Primitive(PrimitiveValue.Int(42))))
        )
        assertTrue(ds.check(dv).isEmpty)
      },
      test("recursive schema validates correctly - Node case") {
        val ds       = Schema[Tree].toDynamicSchema
        val leftLeaf = DynamicValue.Variant(
          "Leaf",
          DynamicValue.Record(Chunk("value" -> DynamicValue.Primitive(PrimitiveValue.Int(1))))
        )
        val rightLeaf = DynamicValue.Variant(
          "Leaf",
          DynamicValue.Record(Chunk("value" -> DynamicValue.Primitive(PrimitiveValue.Int(2))))
        )
        val dv = DynamicValue.Variant(
          "Node",
          DynamicValue.Record(Chunk("left" -> leftLeaf, "right" -> rightLeaf))
        )
        assertTrue(ds.check(dv).isEmpty)
      }
    ),
    suite("check - Wrapper validation")(
      test("wrapper schema validates wrapped value") {
        case class PositiveInt(value: Int)
        object PositiveInt {
          implicit val schema: Schema[PositiveInt] = Schema[Int].transform(to = PositiveInt(_), from = _.value)
        }
        val ds = PositiveInt.schema.toDynamicSchema
        val dv = DynamicValue.Primitive(PrimitiveValue.Int(42))
        assertTrue(ds.check(dv).isEmpty)
      },
      test("wrapper schema rejects wrong type") {
        case class PositiveInt(value: Int)
        object PositiveInt {
          implicit val schema: Schema[PositiveInt] = Schema[Int].transform(to = PositiveInt(_), from = _.value)
        }
        val ds = PositiveInt.schema.toDynamicSchema
        val dv = DynamicValue.Primitive(PrimitiveValue.String("not an int"))
        assertTrue(ds.check(dv).isDefined)
      }
    ),
    suite("toJsonSchema")(
      test("toJsonSchema returns a valid JsonSchema") {
        val ds         = Schema[Person].toDynamicSchema
        val jsonSchema = ds.toJsonSchema
        assertTrue(jsonSchema != null)
      }
    ),
    suite("toSchema encoding")(
      test("toSchema.toDynamicValue uses identity for encoding") {
        val ds     = Schema[Person].toDynamicSchema
        val schema = ds.toSchema
        val dv     = DynamicValue.Record(
          Chunk(
            "name" -> DynamicValue.Primitive(PrimitiveValue.String("Alice")),
            "age"  -> DynamicValue.Primitive(PrimitiveValue.Int(30))
          )
        )
        val encoded = schema.toDynamicValue(dv)
        assertTrue(encoded == dv)
      }
    ),
    suite("type mismatch - variant and map")(
      test("record value against variant schema fails") {
        val ds = Schema[Color].toDynamicSchema
        val dv = DynamicValue.Record(Chunk.empty)
        assertTrue(ds.check(dv).isDefined)
      },
      test("sequence value against map schema fails") {
        val ds = Schema[Map[String, Int]].toDynamicSchema
        val dv = DynamicValue.Sequence(Chunk.empty)
        assertTrue(ds.check(dv).isDefined)
      }
    ),
    suite("defaultValue and examples for all Reflect types")(
      test("Record defaultValue and getDefaultValue") {
        val ds = Schema[Person].toDynamicSchema
        val dv = DynamicValue.Record(
          Chunk(
            "name" -> DynamicValue.Primitive(PrimitiveValue.String("Bob")),
            "age"  -> DynamicValue.Primitive(PrimitiveValue.Int(25))
          )
        )
        val ds2 = ds.defaultValue(dv)
        assertTrue(ds2.getDefaultValue.contains(dv))
      },
      test("Record examples sets and retrieves") {
        val ds  = Schema[Person].toDynamicSchema
        val dv1 = DynamicValue.Record(
          Chunk(
            "name" -> DynamicValue.Primitive(PrimitiveValue.String("Bob")),
            "age"  -> DynamicValue.Primitive(PrimitiveValue.Int(25))
          )
        )
        val ds2 = ds.examples(dv1)
        assertTrue(ds2.examples.length == 1)
      },
      test("Variant defaultValue and getDefaultValue") {
        val ds  = Schema[Color].toDynamicSchema
        val dv  = DynamicValue.Variant("Red", DynamicValue.Record(Chunk.empty))
        val ds2 = ds.defaultValue(dv)
        assertTrue(ds2.getDefaultValue.contains(dv))
      },
      test("Variant examples sets and retrieves") {
        val ds  = Schema[Color].toDynamicSchema
        val dv1 = DynamicValue.Variant("Red", DynamicValue.Record(Chunk.empty))
        val ds2 = ds.examples(dv1)
        assertTrue(ds2.examples.length == 1)
      },
      test("Sequence defaultValue and getDefaultValue") {
        val ds  = Schema[List[Int]].toDynamicSchema
        val dv  = DynamicValue.Sequence(Chunk(DynamicValue.Primitive(PrimitiveValue.Int(1))))
        val ds2 = ds.defaultValue(dv)
        assertTrue(ds2.getDefaultValue.contains(dv))
      },
      test("Sequence examples sets and retrieves") {
        val ds  = Schema[List[Int]].toDynamicSchema
        val dv1 = DynamicValue.Sequence(Chunk(DynamicValue.Primitive(PrimitiveValue.Int(1))))
        val ds2 = ds.examples(dv1)
        assertTrue(ds2.examples.length == 1)
      },
      test("Map defaultValue and getDefaultValue") {
        val ds = Schema[Map[String, Int]].toDynamicSchema
        val dv = DynamicValue.Map(
          Chunk(DynamicValue.Primitive(PrimitiveValue.String("a")) -> DynamicValue.Primitive(PrimitiveValue.Int(1)))
        )
        val ds2 = ds.defaultValue(dv)
        assertTrue(ds2.getDefaultValue.contains(dv))
      },
      test("Map examples sets and retrieves") {
        val ds  = Schema[Map[String, Int]].toDynamicSchema
        val dv1 = DynamicValue.Map(
          Chunk(DynamicValue.Primitive(PrimitiveValue.String("a")) -> DynamicValue.Primitive(PrimitiveValue.Int(1)))
        )
        val ds2 = ds.examples(dv1)
        assertTrue(ds2.examples.length == 1)
      },
      test("Wrapper defaultValue and getDefaultValue") {
        case class WrappedInt(value: Int)
        object WrappedInt {
          implicit val schema: Schema[WrappedInt] = Schema[Int].transform(to = WrappedInt(_), from = _.value)
        }
        val ds  = WrappedInt.schema.toDynamicSchema
        val dv  = DynamicValue.Primitive(PrimitiveValue.Int(42))
        val ds2 = ds.defaultValue(dv)
        assertTrue(ds2.getDefaultValue.contains(dv))
      },
      test("Wrapper examples sets and retrieves") {
        case class WrappedInt(value: Int)
        object WrappedInt {
          implicit val schema: Schema[WrappedInt] = Schema[Int].transform(to = WrappedInt(_), from = _.value)
        }
        val ds  = WrappedInt.schema.toDynamicSchema
        val dv1 = DynamicValue.Primitive(PrimitiveValue.Int(42))
        val ds2 = ds.examples(dv1)
        assertTrue(ds2.examples.length == 1)
      },
      test("Dynamic getDefaultValue returns None") {
        val ds = Schema[DynamicValue].toDynamicSchema
        assertTrue(ds.getDefaultValue.isEmpty)
      },
      test("Dynamic examples returns empty") {
        val ds = Schema[DynamicValue].toDynamicSchema
        assertTrue(ds.examples.isEmpty)
      },
      test("Dynamic defaultValue is no-op") {
        val ds  = Schema[DynamicValue].toDynamicSchema
        val dv  = DynamicValue.Primitive(PrimitiveValue.Int(1))
        val ds2 = ds.defaultValue(dv)
        assertTrue(ds2.getDefaultValue.isEmpty)
      },
      test("Dynamic examples is no-op") {
        val ds  = Schema[DynamicValue].toDynamicSchema
        val dv  = DynamicValue.Primitive(PrimitiveValue.Int(1))
        val ds2 = ds.examples(dv)
        assertTrue(ds2.examples.isEmpty)
      }
    ),
    suite("check - all primitive types")(
      test("Unit primitive validates correctly") {
        val ds = Schema[Unit].toDynamicSchema
        assertTrue(ds.check(DynamicValue.Primitive(PrimitiveValue.Unit)).isEmpty) &&
        assertTrue(ds.check(DynamicValue.Null).isEmpty) &&
        assertTrue(ds.check(DynamicValue.Primitive(PrimitiveValue.Int(1))).isDefined)
      },
      test("Boolean primitive validates correctly") {
        val ds = Schema[Boolean].toDynamicSchema
        assertTrue(ds.check(DynamicValue.Primitive(PrimitiveValue.Boolean(true))).isEmpty) &&
        assertTrue(ds.check(DynamicValue.Primitive(PrimitiveValue.Int(1))).isDefined)
      },
      test("Byte primitive validates correctly") {
        val ds = Schema[Byte].toDynamicSchema
        assertTrue(ds.check(DynamicValue.Primitive(PrimitiveValue.Byte(1))).isEmpty) &&
        assertTrue(ds.check(DynamicValue.Primitive(PrimitiveValue.Int(1))).isDefined)
      },
      test("Short primitive validates correctly") {
        val ds = Schema[Short].toDynamicSchema
        assertTrue(ds.check(DynamicValue.Primitive(PrimitiveValue.Short(1))).isEmpty) &&
        assertTrue(ds.check(DynamicValue.Primitive(PrimitiveValue.Int(1))).isDefined)
      },
      test("Long primitive validates correctly") {
        val ds = Schema[Long].toDynamicSchema
        assertTrue(ds.check(DynamicValue.Primitive(PrimitiveValue.Long(1L))).isEmpty) &&
        assertTrue(ds.check(DynamicValue.Primitive(PrimitiveValue.Int(1))).isDefined)
      },
      test("Float primitive validates correctly") {
        val ds = Schema[Float].toDynamicSchema
        assertTrue(ds.check(DynamicValue.Primitive(PrimitiveValue.Float(1.0f))).isEmpty) &&
        assertTrue(ds.check(DynamicValue.Primitive(PrimitiveValue.Int(1))).isDefined)
      },
      test("Double primitive validates correctly") {
        val ds = Schema[Double].toDynamicSchema
        assertTrue(ds.check(DynamicValue.Primitive(PrimitiveValue.Double(1.0))).isEmpty) &&
        assertTrue(ds.check(DynamicValue.Primitive(PrimitiveValue.Int(1))).isDefined)
      },
      test("Char primitive validates correctly") {
        val ds = Schema[Char].toDynamicSchema
        assertTrue(ds.check(DynamicValue.Primitive(PrimitiveValue.Char('a'))).isEmpty) &&
        assertTrue(ds.check(DynamicValue.Primitive(PrimitiveValue.Int(1))).isDefined)
      },
      test("BigInt primitive validates correctly") {
        val ds = Schema[BigInt].toDynamicSchema
        assertTrue(ds.check(DynamicValue.Primitive(PrimitiveValue.BigInt(BigInt(1)))).isEmpty) &&
        assertTrue(ds.check(DynamicValue.Primitive(PrimitiveValue.Int(1))).isDefined)
      },
      test("BigDecimal primitive validates correctly") {
        val ds = Schema[BigDecimal].toDynamicSchema
        assertTrue(ds.check(DynamicValue.Primitive(PrimitiveValue.BigDecimal(BigDecimal(1)))).isEmpty) &&
        assertTrue(ds.check(DynamicValue.Primitive(PrimitiveValue.Int(1))).isDefined)
      },
      test("DayOfWeek primitive validates correctly") {
        val ds = Schema[java.time.DayOfWeek].toDynamicSchema
        assertTrue(ds.check(DynamicValue.Primitive(PrimitiveValue.DayOfWeek(java.time.DayOfWeek.MONDAY))).isEmpty) &&
        assertTrue(ds.check(DynamicValue.Primitive(PrimitiveValue.Int(1))).isDefined)
      },
      test("Duration primitive validates correctly") {
        val ds = Schema[java.time.Duration].toDynamicSchema
        assertTrue(
          ds.check(DynamicValue.Primitive(PrimitiveValue.Duration(java.time.Duration.ofSeconds(1)))).isEmpty
        ) &&
        assertTrue(ds.check(DynamicValue.Primitive(PrimitiveValue.Int(1))).isDefined)
      },
      test("Instant primitive validates correctly") {
        val ds = Schema[java.time.Instant].toDynamicSchema
        assertTrue(ds.check(DynamicValue.Primitive(PrimitiveValue.Instant(java.time.Instant.now()))).isEmpty) &&
        assertTrue(ds.check(DynamicValue.Primitive(PrimitiveValue.Int(1))).isDefined)
      },
      test("LocalDate primitive validates correctly") {
        val ds = Schema[java.time.LocalDate].toDynamicSchema
        assertTrue(ds.check(DynamicValue.Primitive(PrimitiveValue.LocalDate(java.time.LocalDate.now()))).isEmpty) &&
        assertTrue(ds.check(DynamicValue.Primitive(PrimitiveValue.Int(1))).isDefined)
      },
      test("LocalDateTime primitive validates correctly") {
        val ds = Schema[java.time.LocalDateTime].toDynamicSchema
        assertTrue(
          ds.check(DynamicValue.Primitive(PrimitiveValue.LocalDateTime(java.time.LocalDateTime.now()))).isEmpty
        ) &&
        assertTrue(ds.check(DynamicValue.Primitive(PrimitiveValue.Int(1))).isDefined)
      },
      test("LocalTime primitive validates correctly") {
        val ds = Schema[java.time.LocalTime].toDynamicSchema
        assertTrue(ds.check(DynamicValue.Primitive(PrimitiveValue.LocalTime(java.time.LocalTime.now()))).isEmpty) &&
        assertTrue(ds.check(DynamicValue.Primitive(PrimitiveValue.Int(1))).isDefined)
      },
      test("Month primitive validates correctly") {
        val ds = Schema[java.time.Month].toDynamicSchema
        assertTrue(ds.check(DynamicValue.Primitive(PrimitiveValue.Month(java.time.Month.JANUARY))).isEmpty) &&
        assertTrue(ds.check(DynamicValue.Primitive(PrimitiveValue.Int(1))).isDefined)
      },
      test("MonthDay primitive validates correctly") {
        val ds = Schema[java.time.MonthDay].toDynamicSchema
        assertTrue(ds.check(DynamicValue.Primitive(PrimitiveValue.MonthDay(java.time.MonthDay.of(1, 1)))).isEmpty) &&
        assertTrue(ds.check(DynamicValue.Primitive(PrimitiveValue.Int(1))).isDefined)
      },
      test("OffsetDateTime primitive validates correctly") {
        val ds = Schema[java.time.OffsetDateTime].toDynamicSchema
        assertTrue(
          ds.check(DynamicValue.Primitive(PrimitiveValue.OffsetDateTime(java.time.OffsetDateTime.now()))).isEmpty
        ) &&
        assertTrue(ds.check(DynamicValue.Primitive(PrimitiveValue.Int(1))).isDefined)
      },
      test("OffsetTime primitive validates correctly") {
        val ds = Schema[java.time.OffsetTime].toDynamicSchema
        assertTrue(ds.check(DynamicValue.Primitive(PrimitiveValue.OffsetTime(java.time.OffsetTime.now()))).isEmpty) &&
        assertTrue(ds.check(DynamicValue.Primitive(PrimitiveValue.Int(1))).isDefined)
      },
      test("Period primitive validates correctly") {
        val ds = Schema[java.time.Period].toDynamicSchema
        assertTrue(ds.check(DynamicValue.Primitive(PrimitiveValue.Period(java.time.Period.ofDays(1)))).isEmpty) &&
        assertTrue(ds.check(DynamicValue.Primitive(PrimitiveValue.Int(1))).isDefined)
      },
      test("Year primitive validates correctly") {
        val ds = Schema[java.time.Year].toDynamicSchema
        assertTrue(ds.check(DynamicValue.Primitive(PrimitiveValue.Year(java.time.Year.of(2024)))).isEmpty) &&
        assertTrue(ds.check(DynamicValue.Primitive(PrimitiveValue.Int(1))).isDefined)
      },
      test("YearMonth primitive validates correctly") {
        val ds = Schema[java.time.YearMonth].toDynamicSchema
        assertTrue(
          ds.check(DynamicValue.Primitive(PrimitiveValue.YearMonth(java.time.YearMonth.of(2024, 1)))).isEmpty
        ) &&
        assertTrue(ds.check(DynamicValue.Primitive(PrimitiveValue.Int(1))).isDefined)
      },
      test("ZoneId primitive validates correctly") {
        val ds = Schema[java.time.ZoneId].toDynamicSchema
        assertTrue(ds.check(DynamicValue.Primitive(PrimitiveValue.ZoneId(java.time.ZoneId.of("UTC")))).isEmpty) &&
        assertTrue(ds.check(DynamicValue.Primitive(PrimitiveValue.Int(1))).isDefined)
      },
      test("ZoneOffset primitive validates correctly") {
        val ds = Schema[java.time.ZoneOffset].toDynamicSchema
        assertTrue(ds.check(DynamicValue.Primitive(PrimitiveValue.ZoneOffset(java.time.ZoneOffset.UTC))).isEmpty) &&
        assertTrue(ds.check(DynamicValue.Primitive(PrimitiveValue.Int(1))).isDefined)
      },
      test("ZonedDateTime primitive validates correctly") {
        val ds = Schema[java.time.ZonedDateTime].toDynamicSchema
        assertTrue(
          ds.check(DynamicValue.Primitive(PrimitiveValue.ZonedDateTime(java.time.ZonedDateTime.now()))).isEmpty
        ) &&
        assertTrue(ds.check(DynamicValue.Primitive(PrimitiveValue.Int(1))).isDefined)
      },
      test("Currency primitive validates correctly") {
        val ds = Schema[java.util.Currency].toDynamicSchema
        assertTrue(
          ds.check(DynamicValue.Primitive(PrimitiveValue.Currency(java.util.Currency.getInstance("USD")))).isEmpty
        ) &&
        assertTrue(ds.check(DynamicValue.Primitive(PrimitiveValue.Int(1))).isDefined)
      },
      test("UUID primitive validates correctly") {
        val ds        = Schema[java.util.UUID].toDynamicSchema
        val fixedUUID = java.util.UUID.fromString("123e4567-e89b-12d3-a456-426614174000")
        assertTrue(ds.check(DynamicValue.Primitive(PrimitiveValue.UUID(fixedUUID))).isEmpty) &&
        assertTrue(ds.check(DynamicValue.Primitive(PrimitiveValue.Int(1))).isDefined)
      }
    ),
    suite("check - Numeric validation for all types")(
      test("Numeric.Positive for Byte passes for positive") {
        val ds = Schema[Byte].reflect.asPrimitive
          .map(p =>
            new Schema(
              new Reflect.Primitive(PrimitiveType.Byte(Validation.Numeric.Positive), p.typeId, p.primitiveBinding)
            )
          )
          .get
          .toDynamicSchema
        assertTrue(ds.check(DynamicValue.Primitive(PrimitiveValue.Byte(5))).isEmpty) &&
        assertTrue(ds.check(DynamicValue.Primitive(PrimitiveValue.Byte(0))).isDefined)
      },
      test("Numeric.Positive for Short passes for positive") {
        val ds = Schema[Short].reflect.asPrimitive
          .map(p =>
            new Schema(
              new Reflect.Primitive(PrimitiveType.Short(Validation.Numeric.Positive), p.typeId, p.primitiveBinding)
            )
          )
          .get
          .toDynamicSchema
        assertTrue(ds.check(DynamicValue.Primitive(PrimitiveValue.Short(5))).isEmpty) &&
        assertTrue(ds.check(DynamicValue.Primitive(PrimitiveValue.Short(0))).isDefined)
      },
      test("Numeric.Positive for Long passes for positive") {
        val ds = Schema[Long].reflect.asPrimitive
          .map(p =>
            new Schema(
              new Reflect.Primitive(PrimitiveType.Long(Validation.Numeric.Positive), p.typeId, p.primitiveBinding)
            )
          )
          .get
          .toDynamicSchema
        assertTrue(ds.check(DynamicValue.Primitive(PrimitiveValue.Long(5L))).isEmpty) &&
        assertTrue(ds.check(DynamicValue.Primitive(PrimitiveValue.Long(0L))).isDefined)
      },
      test("Numeric.Positive for Float passes for positive") {
        val ds = Schema[Float].reflect.asPrimitive
          .map(p =>
            new Schema(
              new Reflect.Primitive(PrimitiveType.Float(Validation.Numeric.Positive), p.typeId, p.primitiveBinding)
            )
          )
          .get
          .toDynamicSchema
        assertTrue(ds.check(DynamicValue.Primitive(PrimitiveValue.Float(5.0f))).isEmpty) &&
        assertTrue(ds.check(DynamicValue.Primitive(PrimitiveValue.Float(0.0f))).isDefined)
      },
      test("Numeric.Positive for Double passes for positive") {
        val ds = Schema[Double].reflect.asPrimitive
          .map(p =>
            new Schema(
              new Reflect.Primitive(PrimitiveType.Double(Validation.Numeric.Positive), p.typeId, p.primitiveBinding)
            )
          )
          .get
          .toDynamicSchema
        assertTrue(ds.check(DynamicValue.Primitive(PrimitiveValue.Double(5.0))).isEmpty) &&
        assertTrue(ds.check(DynamicValue.Primitive(PrimitiveValue.Double(0.0))).isDefined)
      },
      test("Numeric.Positive for BigInt passes for positive") {
        val ds = Schema[BigInt].reflect.asPrimitive
          .map(p =>
            new Schema(
              new Reflect.Primitive(PrimitiveType.BigInt(Validation.Numeric.Positive), p.typeId, p.primitiveBinding)
            )
          )
          .get
          .toDynamicSchema
        assertTrue(ds.check(DynamicValue.Primitive(PrimitiveValue.BigInt(BigInt(5)))).isEmpty) &&
        assertTrue(ds.check(DynamicValue.Primitive(PrimitiveValue.BigInt(BigInt(0)))).isDefined)
      },
      test("Numeric.Positive for BigDecimal passes for positive") {
        val ds = Schema[BigDecimal].reflect.asPrimitive
          .map(p =>
            new Schema(
              new Reflect.Primitive(
                PrimitiveType.BigDecimal(Validation.Numeric.Positive),
                p.typeId,
                p.primitiveBinding
              )
            )
          )
          .get
          .toDynamicSchema
        assertTrue(ds.check(DynamicValue.Primitive(PrimitiveValue.BigDecimal(BigDecimal(5)))).isEmpty) &&
        assertTrue(ds.check(DynamicValue.Primitive(PrimitiveValue.BigDecimal(BigDecimal(0)))).isDefined)
      },
      test("Numeric.Negative for Byte passes for negative") {
        val ds = Schema[Byte].reflect.asPrimitive
          .map(p =>
            new Schema(
              new Reflect.Primitive(PrimitiveType.Byte(Validation.Numeric.Negative), p.typeId, p.primitiveBinding)
            )
          )
          .get
          .toDynamicSchema
        assertTrue(ds.check(DynamicValue.Primitive(PrimitiveValue.Byte(-1))).isEmpty) &&
        assertTrue(ds.check(DynamicValue.Primitive(PrimitiveValue.Byte(0))).isDefined)
      },
      test("Numeric.Negative for Short passes for negative") {
        val ds = Schema[Short].reflect.asPrimitive
          .map(p =>
            new Schema(
              new Reflect.Primitive(PrimitiveType.Short(Validation.Numeric.Negative), p.typeId, p.primitiveBinding)
            )
          )
          .get
          .toDynamicSchema
        assertTrue(ds.check(DynamicValue.Primitive(PrimitiveValue.Short(-1))).isEmpty) &&
        assertTrue(ds.check(DynamicValue.Primitive(PrimitiveValue.Short(0))).isDefined)
      },
      test("Numeric.Negative for Long passes for negative") {
        val ds = Schema[Long].reflect.asPrimitive
          .map(p =>
            new Schema(
              new Reflect.Primitive(PrimitiveType.Long(Validation.Numeric.Negative), p.typeId, p.primitiveBinding)
            )
          )
          .get
          .toDynamicSchema
        assertTrue(ds.check(DynamicValue.Primitive(PrimitiveValue.Long(-1L))).isEmpty) &&
        assertTrue(ds.check(DynamicValue.Primitive(PrimitiveValue.Long(0L))).isDefined)
      },
      test("Numeric.Negative for Float passes for negative") {
        val ds = Schema[Float].reflect.asPrimitive
          .map(p =>
            new Schema(
              new Reflect.Primitive(PrimitiveType.Float(Validation.Numeric.Negative), p.typeId, p.primitiveBinding)
            )
          )
          .get
          .toDynamicSchema
        assertTrue(ds.check(DynamicValue.Primitive(PrimitiveValue.Float(-1.0f))).isEmpty) &&
        assertTrue(ds.check(DynamicValue.Primitive(PrimitiveValue.Float(0.0f))).isDefined)
      },
      test("Numeric.Negative for Double passes for negative") {
        val ds = Schema[Double].reflect.asPrimitive
          .map(p =>
            new Schema(
              new Reflect.Primitive(PrimitiveType.Double(Validation.Numeric.Negative), p.typeId, p.primitiveBinding)
            )
          )
          .get
          .toDynamicSchema
        assertTrue(ds.check(DynamicValue.Primitive(PrimitiveValue.Double(-1.0))).isEmpty) &&
        assertTrue(ds.check(DynamicValue.Primitive(PrimitiveValue.Double(0.0))).isDefined)
      },
      test("Numeric.Negative for BigInt passes for negative") {
        val ds = Schema[BigInt].reflect.asPrimitive
          .map(p =>
            new Schema(
              new Reflect.Primitive(PrimitiveType.BigInt(Validation.Numeric.Negative), p.typeId, p.primitiveBinding)
            )
          )
          .get
          .toDynamicSchema
        assertTrue(ds.check(DynamicValue.Primitive(PrimitiveValue.BigInt(BigInt(-1)))).isEmpty) &&
        assertTrue(ds.check(DynamicValue.Primitive(PrimitiveValue.BigInt(BigInt(0)))).isDefined)
      },
      test("Numeric.Negative for BigDecimal passes for negative") {
        val ds = Schema[BigDecimal].reflect.asPrimitive
          .map(p =>
            new Schema(
              new Reflect.Primitive(
                PrimitiveType.BigDecimal(Validation.Numeric.Negative),
                p.typeId,
                p.primitiveBinding
              )
            )
          )
          .get
          .toDynamicSchema
        assertTrue(ds.check(DynamicValue.Primitive(PrimitiveValue.BigDecimal(BigDecimal(-1)))).isEmpty) &&
        assertTrue(ds.check(DynamicValue.Primitive(PrimitiveValue.BigDecimal(BigDecimal(0)))).isDefined)
      },
      test("Numeric.NonPositive for Byte passes for non-positive") {
        val ds = Schema[Byte].reflect.asPrimitive
          .map(p =>
            new Schema(
              new Reflect.Primitive(PrimitiveType.Byte(Validation.Numeric.NonPositive), p.typeId, p.primitiveBinding)
            )
          )
          .get
          .toDynamicSchema
        assertTrue(ds.check(DynamicValue.Primitive(PrimitiveValue.Byte(0))).isEmpty) &&
        assertTrue(ds.check(DynamicValue.Primitive(PrimitiveValue.Byte(1))).isDefined)
      },
      test("Numeric.NonPositive for Short passes for non-positive") {
        val ds = Schema[Short].reflect.asPrimitive
          .map(p =>
            new Schema(
              new Reflect.Primitive(PrimitiveType.Short(Validation.Numeric.NonPositive), p.typeId, p.primitiveBinding)
            )
          )
          .get
          .toDynamicSchema
        assertTrue(ds.check(DynamicValue.Primitive(PrimitiveValue.Short(0))).isEmpty) &&
        assertTrue(ds.check(DynamicValue.Primitive(PrimitiveValue.Short(1))).isDefined)
      },
      test("Numeric.NonPositive for Long passes for non-positive") {
        val ds = Schema[Long].reflect.asPrimitive
          .map(p =>
            new Schema(
              new Reflect.Primitive(PrimitiveType.Long(Validation.Numeric.NonPositive), p.typeId, p.primitiveBinding)
            )
          )
          .get
          .toDynamicSchema
        assertTrue(ds.check(DynamicValue.Primitive(PrimitiveValue.Long(0L))).isEmpty) &&
        assertTrue(ds.check(DynamicValue.Primitive(PrimitiveValue.Long(1L))).isDefined)
      },
      test("Numeric.NonPositive for Float passes for non-positive") {
        val ds = Schema[Float].reflect.asPrimitive
          .map(p =>
            new Schema(
              new Reflect.Primitive(PrimitiveType.Float(Validation.Numeric.NonPositive), p.typeId, p.primitiveBinding)
            )
          )
          .get
          .toDynamicSchema
        assertTrue(ds.check(DynamicValue.Primitive(PrimitiveValue.Float(0.0f))).isEmpty) &&
        assertTrue(ds.check(DynamicValue.Primitive(PrimitiveValue.Float(1.0f))).isDefined)
      },
      test("Numeric.NonPositive for Double passes for non-positive") {
        val ds = Schema[Double].reflect.asPrimitive
          .map(p =>
            new Schema(
              new Reflect.Primitive(
                PrimitiveType.Double(Validation.Numeric.NonPositive),
                p.typeId,
                p.primitiveBinding
              )
            )
          )
          .get
          .toDynamicSchema
        assertTrue(ds.check(DynamicValue.Primitive(PrimitiveValue.Double(0.0))).isEmpty) &&
        assertTrue(ds.check(DynamicValue.Primitive(PrimitiveValue.Double(1.0))).isDefined)
      },
      test("Numeric.NonPositive for BigInt passes for non-positive") {
        val ds = Schema[BigInt].reflect.asPrimitive
          .map(p =>
            new Schema(
              new Reflect.Primitive(
                PrimitiveType.BigInt(Validation.Numeric.NonPositive),
                p.typeId,
                p.primitiveBinding
              )
            )
          )
          .get
          .toDynamicSchema
        assertTrue(ds.check(DynamicValue.Primitive(PrimitiveValue.BigInt(BigInt(0)))).isEmpty) &&
        assertTrue(ds.check(DynamicValue.Primitive(PrimitiveValue.BigInt(BigInt(1)))).isDefined)
      },
      test("Numeric.NonPositive for BigDecimal passes for non-positive") {
        val ds = Schema[BigDecimal].reflect.asPrimitive
          .map(p =>
            new Schema(
              new Reflect.Primitive(
                PrimitiveType.BigDecimal(Validation.Numeric.NonPositive),
                p.typeId,
                p.primitiveBinding
              )
            )
          )
          .get
          .toDynamicSchema
        assertTrue(ds.check(DynamicValue.Primitive(PrimitiveValue.BigDecimal(BigDecimal(0)))).isEmpty) &&
        assertTrue(ds.check(DynamicValue.Primitive(PrimitiveValue.BigDecimal(BigDecimal(1)))).isDefined)
      },
      test("Numeric.NonNegative for Byte passes for non-negative") {
        val ds = Schema[Byte].reflect.asPrimitive
          .map(p =>
            new Schema(
              new Reflect.Primitive(PrimitiveType.Byte(Validation.Numeric.NonNegative), p.typeId, p.primitiveBinding)
            )
          )
          .get
          .toDynamicSchema
        assertTrue(ds.check(DynamicValue.Primitive(PrimitiveValue.Byte(0))).isEmpty) &&
        assertTrue(ds.check(DynamicValue.Primitive(PrimitiveValue.Byte(-1))).isDefined)
      },
      test("Numeric.NonNegative for Short passes for non-negative") {
        val ds = Schema[Short].reflect.asPrimitive
          .map(p =>
            new Schema(
              new Reflect.Primitive(PrimitiveType.Short(Validation.Numeric.NonNegative), p.typeId, p.primitiveBinding)
            )
          )
          .get
          .toDynamicSchema
        assertTrue(ds.check(DynamicValue.Primitive(PrimitiveValue.Short(0))).isEmpty) &&
        assertTrue(ds.check(DynamicValue.Primitive(PrimitiveValue.Short(-1))).isDefined)
      },
      test("Numeric.NonNegative for Long passes for non-negative") {
        val ds = Schema[Long].reflect.asPrimitive
          .map(p =>
            new Schema(
              new Reflect.Primitive(PrimitiveType.Long(Validation.Numeric.NonNegative), p.typeId, p.primitiveBinding)
            )
          )
          .get
          .toDynamicSchema
        assertTrue(ds.check(DynamicValue.Primitive(PrimitiveValue.Long(0L))).isEmpty) &&
        assertTrue(ds.check(DynamicValue.Primitive(PrimitiveValue.Long(-1L))).isDefined)
      },
      test("Numeric.NonNegative for Float passes for non-negative") {
        val ds = Schema[Float].reflect.asPrimitive
          .map(p =>
            new Schema(
              new Reflect.Primitive(PrimitiveType.Float(Validation.Numeric.NonNegative), p.typeId, p.primitiveBinding)
            )
          )
          .get
          .toDynamicSchema
        assertTrue(ds.check(DynamicValue.Primitive(PrimitiveValue.Float(0.0f))).isEmpty) &&
        assertTrue(ds.check(DynamicValue.Primitive(PrimitiveValue.Float(-1.0f))).isDefined)
      },
      test("Numeric.NonNegative for Double passes for non-negative") {
        val ds = Schema[Double].reflect.asPrimitive
          .map(p =>
            new Schema(
              new Reflect.Primitive(
                PrimitiveType.Double(Validation.Numeric.NonNegative),
                p.typeId,
                p.primitiveBinding
              )
            )
          )
          .get
          .toDynamicSchema
        assertTrue(ds.check(DynamicValue.Primitive(PrimitiveValue.Double(0.0))).isEmpty) &&
        assertTrue(ds.check(DynamicValue.Primitive(PrimitiveValue.Double(-1.0))).isDefined)
      },
      test("Numeric.NonNegative for BigInt passes for non-negative") {
        val ds = Schema[BigInt].reflect.asPrimitive
          .map(p =>
            new Schema(
              new Reflect.Primitive(
                PrimitiveType.BigInt(Validation.Numeric.NonNegative),
                p.typeId,
                p.primitiveBinding
              )
            )
          )
          .get
          .toDynamicSchema
        assertTrue(ds.check(DynamicValue.Primitive(PrimitiveValue.BigInt(BigInt(0)))).isEmpty) &&
        assertTrue(ds.check(DynamicValue.Primitive(PrimitiveValue.BigInt(BigInt(-1)))).isDefined)
      },
      test("Numeric.NonNegative for BigDecimal passes for non-negative") {
        val ds = Schema[BigDecimal].reflect.asPrimitive
          .map(p =>
            new Schema(
              new Reflect.Primitive(
                PrimitiveType.BigDecimal(Validation.Numeric.NonNegative),
                p.typeId,
                p.primitiveBinding
              )
            )
          )
          .get
          .toDynamicSchema
        assertTrue(ds.check(DynamicValue.Primitive(PrimitiveValue.BigDecimal(BigDecimal(0)))).isEmpty) &&
        assertTrue(ds.check(DynamicValue.Primitive(PrimitiveValue.BigDecimal(BigDecimal(-1)))).isDefined)
      },
      test("Numeric.Range for Byte") {
        val ds = Schema[Byte].reflect.asPrimitive
          .map(p =>
            new Schema(
              new Reflect.Primitive(
                PrimitiveType.Byte(Validation.Numeric.Range(Some(1: Byte), Some(10: Byte))),
                p.typeId,
                p.primitiveBinding
              )
            )
          )
          .get
          .toDynamicSchema
        assertTrue(ds.check(DynamicValue.Primitive(PrimitiveValue.Byte(5))).isEmpty) &&
        assertTrue(ds.check(DynamicValue.Primitive(PrimitiveValue.Byte(0))).isDefined)
      },
      test("Numeric.Range for Short") {
        val ds = Schema[Short].reflect.asPrimitive
          .map(p =>
            new Schema(
              new Reflect.Primitive(
                PrimitiveType.Short(Validation.Numeric.Range(Some(1: Short), Some(10: Short))),
                p.typeId,
                p.primitiveBinding
              )
            )
          )
          .get
          .toDynamicSchema
        assertTrue(ds.check(DynamicValue.Primitive(PrimitiveValue.Short(5))).isEmpty) &&
        assertTrue(ds.check(DynamicValue.Primitive(PrimitiveValue.Short(0))).isDefined)
      },
      test("Numeric.Range for Long") {
        val ds = Schema[Long].reflect.asPrimitive
          .map(p =>
            new Schema(
              new Reflect.Primitive(
                PrimitiveType.Long(Validation.Numeric.Range(Some(1L), Some(10L))),
                p.typeId,
                p.primitiveBinding
              )
            )
          )
          .get
          .toDynamicSchema
        assertTrue(ds.check(DynamicValue.Primitive(PrimitiveValue.Long(5L))).isEmpty) &&
        assertTrue(ds.check(DynamicValue.Primitive(PrimitiveValue.Long(0L))).isDefined)
      },
      test("Numeric.Range for Float") {
        val ds = Schema[Float].reflect.asPrimitive
          .map(p =>
            new Schema(
              new Reflect.Primitive(
                PrimitiveType.Float(Validation.Numeric.Range(Some(1.0f), Some(10.0f))),
                p.typeId,
                p.primitiveBinding
              )
            )
          )
          .get
          .toDynamicSchema
        assertTrue(ds.check(DynamicValue.Primitive(PrimitiveValue.Float(5.0f))).isEmpty) &&
        assertTrue(ds.check(DynamicValue.Primitive(PrimitiveValue.Float(0.0f))).isDefined)
      },
      test("Numeric.Range for Double") {
        val ds = Schema[Double].reflect.asPrimitive
          .map(p =>
            new Schema(
              new Reflect.Primitive(
                PrimitiveType.Double(Validation.Numeric.Range(Some(1.0), Some(10.0))),
                p.typeId,
                p.primitiveBinding
              )
            )
          )
          .get
          .toDynamicSchema
        assertTrue(ds.check(DynamicValue.Primitive(PrimitiveValue.Double(5.0))).isEmpty) &&
        assertTrue(ds.check(DynamicValue.Primitive(PrimitiveValue.Double(0.0))).isDefined)
      },
      test("Numeric.Range for BigInt") {
        val ds = Schema[BigInt].reflect.asPrimitive
          .map(p =>
            new Schema(
              new Reflect.Primitive(
                PrimitiveType.BigInt(Validation.Numeric.Range(Some(BigInt(1)), Some(BigInt(10)))),
                p.typeId,
                p.primitiveBinding
              )
            )
          )
          .get
          .toDynamicSchema
        assertTrue(ds.check(DynamicValue.Primitive(PrimitiveValue.BigInt(BigInt(5)))).isEmpty) &&
        assertTrue(ds.check(DynamicValue.Primitive(PrimitiveValue.BigInt(BigInt(0)))).isDefined)
      },
      test("Numeric.Range for BigDecimal") {
        val ds = Schema[BigDecimal].reflect.asPrimitive
          .map(p =>
            new Schema(
              new Reflect.Primitive(
                PrimitiveType.BigDecimal(Validation.Numeric.Range(Some(BigDecimal(1)), Some(BigDecimal(10)))),
                p.typeId,
                p.primitiveBinding
              )
            )
          )
          .get
          .toDynamicSchema
        assertTrue(ds.check(DynamicValue.Primitive(PrimitiveValue.BigDecimal(BigDecimal(5)))).isEmpty) &&
        assertTrue(ds.check(DynamicValue.Primitive(PrimitiveValue.BigDecimal(BigDecimal(0)))).isDefined)
      },
      test("Numeric.Set for Byte") {
        val ds = Schema[Byte].reflect.asPrimitive
          .map(p =>
            new Schema(
              new Reflect.Primitive(
                PrimitiveType.Byte(Validation.Numeric.Set(Set(1: Byte, 2: Byte))),
                p.typeId,
                p.primitiveBinding
              )
            )
          )
          .get
          .toDynamicSchema
        assertTrue(ds.check(DynamicValue.Primitive(PrimitiveValue.Byte(1))).isEmpty) &&
        assertTrue(ds.check(DynamicValue.Primitive(PrimitiveValue.Byte(3))).isDefined)
      },
      test("Numeric.Set for Short") {
        val ds = Schema[Short].reflect.asPrimitive
          .map(p =>
            new Schema(
              new Reflect.Primitive(
                PrimitiveType.Short(Validation.Numeric.Set(Set(1: Short, 2: Short))),
                p.typeId,
                p.primitiveBinding
              )
            )
          )
          .get
          .toDynamicSchema
        assertTrue(ds.check(DynamicValue.Primitive(PrimitiveValue.Short(1))).isEmpty) &&
        assertTrue(ds.check(DynamicValue.Primitive(PrimitiveValue.Short(3))).isDefined)
      },
      test("Numeric.Set for Long") {
        val ds = Schema[Long].reflect.asPrimitive
          .map(p =>
            new Schema(
              new Reflect.Primitive(
                PrimitiveType.Long(Validation.Numeric.Set(Set(1L, 2L))),
                p.typeId,
                p.primitiveBinding
              )
            )
          )
          .get
          .toDynamicSchema
        assertTrue(ds.check(DynamicValue.Primitive(PrimitiveValue.Long(1L))).isEmpty) &&
        assertTrue(ds.check(DynamicValue.Primitive(PrimitiveValue.Long(3L))).isDefined)
      },
      test("Numeric.Set for Float") {
        val ds = Schema[Float].reflect.asPrimitive
          .map(p =>
            new Schema(
              new Reflect.Primitive(
                PrimitiveType.Float(Validation.Numeric.Set(Set(1.0f, 2.0f))),
                p.typeId,
                p.primitiveBinding
              )
            )
          )
          .get
          .toDynamicSchema
        assertTrue(ds.check(DynamicValue.Primitive(PrimitiveValue.Float(1.0f))).isEmpty) &&
        assertTrue(ds.check(DynamicValue.Primitive(PrimitiveValue.Float(3.0f))).isDefined)
      },
      test("Numeric.Set for Double") {
        val ds = Schema[Double].reflect.asPrimitive
          .map(p =>
            new Schema(
              new Reflect.Primitive(
                PrimitiveType.Double(Validation.Numeric.Set(Set(1.0, 2.0))),
                p.typeId,
                p.primitiveBinding
              )
            )
          )
          .get
          .toDynamicSchema
        assertTrue(ds.check(DynamicValue.Primitive(PrimitiveValue.Double(1.0))).isEmpty) &&
        assertTrue(ds.check(DynamicValue.Primitive(PrimitiveValue.Double(3.0))).isDefined)
      },
      test("Numeric.Set for BigInt") {
        val ds = Schema[BigInt].reflect.asPrimitive
          .map(p =>
            new Schema(
              new Reflect.Primitive(
                PrimitiveType.BigInt(Validation.Numeric.Set(Set(BigInt(1), BigInt(2)))),
                p.typeId,
                p.primitiveBinding
              )
            )
          )
          .get
          .toDynamicSchema
        assertTrue(ds.check(DynamicValue.Primitive(PrimitiveValue.BigInt(BigInt(1)))).isEmpty) &&
        assertTrue(ds.check(DynamicValue.Primitive(PrimitiveValue.BigInt(BigInt(3)))).isDefined)
      },
      test("Numeric.Set for BigDecimal") {
        val ds = Schema[BigDecimal].reflect.asPrimitive
          .map(p =>
            new Schema(
              new Reflect.Primitive(
                PrimitiveType.BigDecimal(Validation.Numeric.Set(Set(BigDecimal(1), BigDecimal(2)))),
                p.typeId,
                p.primitiveBinding
              )
            )
          )
          .get
          .toDynamicSchema
        assertTrue(ds.check(DynamicValue.Primitive(PrimitiveValue.BigDecimal(BigDecimal(1)))).isEmpty) &&
        assertTrue(ds.check(DynamicValue.Primitive(PrimitiveValue.BigDecimal(BigDecimal(3)))).isDefined)
      },
      test("Range validation fails for wrong type") {
        val ds = Schema[Int].reflect.asPrimitive
          .map(p =>
            new Schema(
              new Reflect.Primitive(
                PrimitiveType.Int(Validation.Numeric.Range(Some(1), Some(10))),
                p.typeId,
                p.primitiveBinding
              )
            )
          )
          .get
          .toDynamicSchema
        assertTrue(ds.check(DynamicValue.Primitive(PrimitiveValue.String("not a number"))).isDefined)
      },
      test("Set validation fails for wrong type") {
        val ds = Schema[Int].reflect.asPrimitive
          .map(p =>
            new Schema(
              new Reflect.Primitive(
                PrimitiveType.Int(Validation.Numeric.Set(Set(1, 2))),
                p.typeId,
                p.primitiveBinding
              )
            )
          )
          .get
          .toDynamicSchema
        assertTrue(ds.check(DynamicValue.Primitive(PrimitiveValue.String("not a number"))).isDefined)
      }
    ),
    suite("check - String validation edge cases")(
      test("String.Blank fails for non-string") {
        val ds = Schema[String].reflect.asPrimitive
          .map(p =>
            new Schema(
              new Reflect.Primitive(PrimitiveType.String(Validation.String.Blank), p.typeId, p.primitiveBinding)
            )
          )
          .get
          .toDynamicSchema
        assertTrue(ds.check(DynamicValue.Primitive(PrimitiveValue.Int(1))).isDefined) &&
        assertTrue(ds.check(DynamicValue.Primitive(PrimitiveValue.String("  "))).isEmpty) &&
        assertTrue(ds.check(DynamicValue.Primitive(PrimitiveValue.String("abc"))).isDefined)
      },
      test("String.NonBlank fails for non-string") {
        val ds = Schema[String].reflect.asPrimitive
          .map(p =>
            new Schema(
              new Reflect.Primitive(PrimitiveType.String(Validation.String.NonBlank), p.typeId, p.primitiveBinding)
            )
          )
          .get
          .toDynamicSchema
        assertTrue(ds.check(DynamicValue.Primitive(PrimitiveValue.Int(1))).isDefined) &&
        assertTrue(ds.check(DynamicValue.Primitive(PrimitiveValue.String("abc"))).isEmpty) &&
        assertTrue(ds.check(DynamicValue.Primitive(PrimitiveValue.String("  "))).isDefined)
      },
      test("String.Length fails for non-string") {
        val ds = Schema[String].reflect.asPrimitive
          .map(p =>
            new Schema(
              new Reflect.Primitive(
                PrimitiveType.String(Validation.String.Length(Some(1), Some(5))),
                p.typeId,
                p.primitiveBinding
              )
            )
          )
          .get
          .toDynamicSchema
        assertTrue(ds.check(DynamicValue.Primitive(PrimitiveValue.Int(1))).isDefined)
      },
      test("String.Pattern fails for non-string") {
        val ds = Schema[String].reflect.asPrimitive
          .map(p =>
            new Schema(
              new Reflect.Primitive(
                PrimitiveType.String(Validation.String.Pattern("^[a-z]+$")),
                p.typeId,
                p.primitiveBinding
              )
            )
          )
          .get
          .toDynamicSchema
        assertTrue(ds.check(DynamicValue.Primitive(PrimitiveValue.Int(1))).isDefined)
      }
    ),
    suite("Schema[DynamicSchema] round-trip")(
      test("primitive schema round-trips through DynamicValue") {
        val original  = Schema[Int].toDynamicSchema
        val dv        = DynamicSchema.toDynamicValue(original)
        val roundTrip = DynamicSchema.fromDynamicValue(dv)
        val name      = roundTrip.typeId.name
        assertTrue(name == "Int")
      },
      test("record schema round-trips through DynamicValue") {
        val original  = Schema[Person].toDynamicSchema
        val dv        = DynamicSchema.toDynamicValue(original)
        val roundTrip = DynamicSchema.fromDynamicValue(dv)
        val name      = roundTrip.typeId.name
        val hasName   = roundTrip.get(DynamicOptic.root.field("name")).isDefined
        val hasAge    = roundTrip.get(DynamicOptic.root.field("age")).isDefined
        assertTrue(name == "Person") &&
        assertTrue(hasName) &&
        assertTrue(hasAge)
      },
      test("variant schema round-trips through DynamicValue") {
        val original  = Schema[Color].toDynamicSchema
        val dv        = DynamicSchema.toDynamicValue(original)
        val roundTrip = DynamicSchema.fromDynamicValue(dv)
        val name      = roundTrip.typeId.name
        assertTrue(name == "Color")
      },
      test("sequence schema round-trips through DynamicValue") {
        val original  = Schema[List[Int]].toDynamicSchema
        val dv        = DynamicSchema.toDynamicValue(original)
        val roundTrip = DynamicSchema.fromDynamicValue(dv)
        val name      = roundTrip.typeId.name
        assertTrue(name == "List")
      },
      test("map schema round-trips through DynamicValue") {
        val original  = Schema[Map[String, Int]].toDynamicSchema
        val dv        = DynamicSchema.toDynamicValue(original)
        val roundTrip = DynamicSchema.fromDynamicValue(dv)
        val name      = roundTrip.typeId.name
        assertTrue(name == "Map")
      },
      test("round-tripped schema validates same values as original") {
        val original   = Schema[Person].toDynamicSchema
        val dv         = DynamicSchema.toDynamicValue(original)
        val roundTrip  = DynamicSchema.fromDynamicValue(dv)
        val validValue = DynamicValue.Record(
          Chunk(
            "name" -> DynamicValue.Primitive(PrimitiveValue.String("Alice")),
            "age"  -> DynamicValue.Primitive(PrimitiveValue.Int(30))
          )
        )
        val conformsOriginal  = original.conforms(validValue)
        val conformsRoundTrip = roundTrip.conforms(validValue)
        assertTrue(conformsOriginal == conformsRoundTrip)
      },
      test("Schema[DynamicSchema] implicit is available") {
        val schemaForDynamicSchema = implicitly[Schema[DynamicSchema]]
        assertTrue(schemaForDynamicSchema != null)
      },
      test("DynamicSchema serializes via Schema[DynamicSchema]") {
        val original  = Schema[Person].toDynamicSchema
        val schema    = Schema[DynamicSchema]
        val dv        = schema.toDynamicValue(original)
        val roundTrip = schema.fromDynamicValue(dv)
        val isRight   = roundTrip.isRight
        val name      = roundTrip.toOption.get.typeId.name
        assertTrue(isRight) &&
        assertTrue(name == "Person")
      },
      test("validation is preserved through DynamicSchema round-trip") {
        val schemaWithValidation = Schema[Int].reflect.asPrimitive
          .map(p =>
            new Schema(
              new Reflect.Primitive(PrimitiveType.Int(Validation.Numeric.Positive), p.typeId, p.primitiveBinding)
            )
          )
          .get
        val original  = schemaWithValidation.toDynamicSchema
        val dv        = DynamicSchema.toDynamicValue(original)
        val roundTrip = DynamicSchema.fromDynamicValue(dv)
        assertTrue(roundTrip.check(DynamicValue.Primitive(PrimitiveValue.Int(5))).isEmpty) &&
        assertTrue(roundTrip.check(DynamicValue.Primitive(PrimitiveValue.Int(-5))).isDefined)
      },
      test("String.NonEmpty validation is preserved through round-trip") {
        val schemaWithValidation = Schema[String].reflect.asPrimitive
          .map(p =>
            new Schema(
              new Reflect.Primitive(
                PrimitiveType.String(Validation.String.NonEmpty),
                p.typeId,
                p.primitiveBinding
              )
            )
          )
          .get
        val original  = schemaWithValidation.toDynamicSchema
        val dv        = DynamicSchema.toDynamicValue(original)
        val roundTrip = DynamicSchema.fromDynamicValue(dv)
        assertTrue(roundTrip.check(DynamicValue.Primitive(PrimitiveValue.String("hello"))).isEmpty) &&
        assertTrue(roundTrip.check(DynamicValue.Primitive(PrimitiveValue.String(""))).isDefined)
      },
      test("Doc.Concat is preserved through round-trip") {
        val schemaWithDoc = Schema[Int].doc("First line").doc("Second line")
        val original      = schemaWithDoc.toDynamicSchema
        val dv            = DynamicSchema.toDynamicValue(original)
        val roundTrip     = DynamicSchema.fromDynamicValue(dv)
        assertTrue(roundTrip.doc != Doc.Empty)
      }
    ),
    suite("Serialization regression tests")(
      test("regression: PrimitiveType serializes as Variant not Record") {
        val ds = Schema[Int].toDynamicSchema
        val dv = DynamicSchema.toDynamicValue(ds)
        dv match {
          case DynamicValue.Variant("Primitive", DynamicValue.Record(fields)) =>
            val fieldMap = fields.toMap
            fieldMap.get("primitiveType") match {
              case Some(DynamicValue.Variant(caseName, _)) =>
                assertTrue(caseName == "Int")
              case _ =>
                assertTrue(false)
            }
          case _ =>
            assertTrue(false)
        }
      },
      test("regression: all String validations round-trip correctly") {
        def testStringValidation(v: Validation.String): zio.test.TestResult = {
          val schema = Schema[String].reflect.asPrimitive
            .map(p => new Schema(new Reflect.Primitive(PrimitiveType.String(v), p.typeId, p.primitiveBinding)))
            .get
          val ds        = schema.toDynamicSchema
          val dv        = DynamicSchema.toDynamicValue(ds)
          val roundTrip = DynamicSchema.fromDynamicValue(dv)
          roundTrip.reflect.asPrimitive match {
            case Some(p) =>
              p.primitiveType match {
                case PrimitiveType.String(validation) =>
                  assertTrue(validation == v)
                case _ =>
                  assertTrue(false)
              }
            case _ =>
              assertTrue(false)
          }
        }
        testStringValidation(Validation.String.NonEmpty) &&
        testStringValidation(Validation.String.Empty) &&
        testStringValidation(Validation.String.Blank) &&
        testStringValidation(Validation.String.NonBlank) &&
        testStringValidation(Validation.String.Length(Some(1), Some(10))) &&
        testStringValidation(Validation.String.Pattern("^[a-z]+$"))
      },
      test("regression: all Numeric validations round-trip correctly") {
        def testNumericValidation(v: Validation[Int]): zio.test.TestResult = {
          val schema = Schema[Int].reflect.asPrimitive
            .map(p => new Schema(new Reflect.Primitive(PrimitiveType.Int(v), p.typeId, p.primitiveBinding)))
            .get
          val ds        = schema.toDynamicSchema
          val dv        = DynamicSchema.toDynamicValue(ds)
          val roundTrip = DynamicSchema.fromDynamicValue(dv)
          roundTrip.reflect.asPrimitive match {
            case Some(p) =>
              p.primitiveType match {
                case PrimitiveType.Int(validation) =>
                  assertTrue(validation == v)
                case _ =>
                  assertTrue(false)
              }
            case _ =>
              assertTrue(false)
          }
        }
        testNumericValidation(Validation.None) &&
        testNumericValidation(Validation.Numeric.Positive) &&
        testNumericValidation(Validation.Numeric.Negative) &&
        testNumericValidation(Validation.Numeric.NonPositive) &&
        testNumericValidation(Validation.Numeric.NonNegative)
      },
      test("regression: Doc.Concat 'flatten' field name consistency") {
        val doc       = Doc.Concat(IndexedSeq(Doc.Text("a"), Doc.Text("b")))
        val schema    = Schema[Int].reflect.asPrimitive.map(p => new Schema(p.doc(doc))).get
        val ds        = schema.toDynamicSchema
        val dv        = DynamicSchema.toDynamicValue(ds)
        val roundTrip = DynamicSchema.fromDynamicValue(dv)
        roundTrip.doc match {
          case Doc.Concat(flatten) =>
            assertTrue(flatten.length == 2)
          case _ =>
            assertTrue(false)
        }
      },
      test("regression: validation is enforced after round-trip") {
        val schema = Schema[Int].reflect.asPrimitive
          .map(p =>
            new Schema(
              new Reflect.Primitive(PrimitiveType.Int(Validation.Numeric.Positive), p.typeId, p.primitiveBinding)
            )
          )
          .get
        val ds        = schema.toDynamicSchema
        val dv        = DynamicSchema.toDynamicValue(ds)
        val roundTrip = DynamicSchema.fromDynamicValue(dv)
        assertTrue(roundTrip.check(DynamicValue.Primitive(PrimitiveValue.Int(5))).isEmpty) &&
        assertTrue(roundTrip.check(DynamicValue.Primitive(PrimitiveValue.Int(0))).isDefined) &&
        assertTrue(roundTrip.check(DynamicValue.Primitive(PrimitiveValue.Int(-5))).isDefined)
      }
    ),
    suite("coverage - Deferred handling")(
      test("getDefaultValue through Deferred returns underlying default") {
        val ds = Schema[Tree].toDynamicSchema
        assertTrue(ds.getDefaultValue.isEmpty)
      },
      test("examples through Deferred returns underlying examples") {
        val ds = Schema[Tree].toDynamicSchema
        assertTrue(ds.examples.isEmpty)
      },
      test("checkValue handles Deferred reflect correctly") {
        val ds        = Schema[Tree].toDynamicSchema
        val validLeaf = DynamicValue.Variant(
          "Leaf",
          DynamicValue.Record(Chunk("value" -> DynamicValue.Primitive(PrimitiveValue.Int(42))))
        )
        assertTrue(ds.check(validLeaf).isEmpty)
      }
    ),
    suite("coverage - String validation error branches")(
      test("String.Length fails for non-string value") {
        val schema = Schema[String].reflect.asPrimitive
          .map(p =>
            new Reflect.Primitive[binding.Binding, String](
              PrimitiveType.String(Validation.String.Length(Some(1), Some(10))),
              p.typeId,
              p.primitiveBinding
            )
          )
          .map(r => new Schema(r))
          .getOrElse(Schema[String])
        val ds = schema.toDynamicSchema
        val dv = DynamicValue.Primitive(PrimitiveValue.Int(42))
        assertTrue(ds.check(dv).isDefined)
      },
      test("String.Pattern fails for non-string value") {
        val schema = Schema[String].reflect.asPrimitive
          .map(p =>
            new Reflect.Primitive[binding.Binding, String](
              PrimitiveType.String(Validation.String.Pattern("^[a-z]+$")),
              p.typeId,
              p.primitiveBinding
            )
          )
          .map(r => new Schema(r))
          .getOrElse(Schema[String])
        val ds = schema.toDynamicSchema
        val dv = DynamicValue.Primitive(PrimitiveValue.Int(42))
        assertTrue(ds.check(dv).isDefined)
      }
    ),
    suite("coverage - Numeric validation error branches")(
      test("Numeric.Range fails for non-numeric value") {
        val schema = Schema[Int].reflect.asPrimitive
          .map(p =>
            new Reflect.Primitive[binding.Binding, Int](
              PrimitiveType.Int(Validation.Numeric.Range(Some(0), Some(100))),
              p.typeId,
              p.primitiveBinding
            )
          )
          .map(r => new Schema(r))
          .getOrElse(Schema[Int])
        val ds = schema.toDynamicSchema
        val dv = DynamicValue.Primitive(PrimitiveValue.String("not a number"))
        assertTrue(ds.check(dv).isDefined)
      },
      test("Numeric.Set fails for non-numeric value") {
        val schema = Schema[Int].reflect.asPrimitive
          .map(p =>
            new Reflect.Primitive[binding.Binding, Int](
              PrimitiveType.Int(Validation.Numeric.Set(Set(1, 2, 3))),
              p.typeId,
              p.primitiveBinding
            )
          )
          .map(r => new Schema(r))
          .getOrElse(Schema[Int])
        val ds = schema.toDynamicSchema
        val dv = DynamicValue.Primitive(PrimitiveValue.String("not a number"))
        assertTrue(ds.check(dv).isDefined)
      }
    ),
    suite("coverage - Modifier.config serialization")(
      test("Modifier.config on Reflect round-trips correctly") {
        val schema    = Schema[Int].modifier(Modifier.config("testKey", "testValue"))
        val ds        = schema.toDynamicSchema
        val dv        = DynamicSchema.toDynamicValue(ds)
        val roundTrip = DynamicSchema.fromDynamicValue(dv)
        val hasConfig = roundTrip.modifiers.exists { case Modifier.config(k, v) =>
          k == "testKey" && v == "testValue"
        }
        assertTrue(hasConfig)
      }
    ),
    suite("coverage - Validation.Numeric.Range and Set serialization")(
      test("Validation.Numeric.Range structure is serialized correctly") {
        val schema = Schema[Int].reflect.asPrimitive
          .map(p =>
            new Schema(
              new Reflect.Primitive(
                PrimitiveType.Int(Validation.Numeric.Range(Some(0), Some(100))),
                p.typeId,
                p.primitiveBinding
              )
            )
          )
          .get
        val ds = schema.toDynamicSchema
        val dv = DynamicSchema.toDynamicValue(ds)
        dv match {
          case DynamicValue.Variant("Primitive", DynamicValue.Record(fields)) =>
            val fieldMap = fields.toMap
            fieldMap.get("primitiveType") match {
              case Some(DynamicValue.Variant("Int", DynamicValue.Record(innerFields))) =>
                val innerMap = innerFields.toMap
                innerMap.get("validation") match {
                  case Some(DynamicValue.Variant("Range", _)) => assertTrue(true)
                  case _                                      => assertTrue(false)
                }
              case _ => assertTrue(false)
            }
          case _ => assertTrue(false)
        }
      },
      test("Validation.Numeric.Set structure is serialized correctly") {
        val schema = Schema[Int].reflect.asPrimitive
          .map(p =>
            new Schema(
              new Reflect.Primitive(
                PrimitiveType.Int(Validation.Numeric.Set(Set(1, 2, 3))),
                p.typeId,
                p.primitiveBinding
              )
            )
          )
          .get
        val ds = schema.toDynamicSchema
        val dv = DynamicSchema.toDynamicValue(ds)
        dv match {
          case DynamicValue.Variant("Primitive", DynamicValue.Record(fields)) =>
            val fieldMap = fields.toMap
            fieldMap.get("primitiveType") match {
              case Some(DynamicValue.Variant("Int", DynamicValue.Record(innerFields))) =>
                val innerMap = innerFields.toMap
                innerMap.get("validation") match {
                  case Some(DynamicValue.Variant("Set", _)) => assertTrue(true)
                  case _                                    => assertTrue(false)
                }
              case _ => assertTrue(false)
            }
          case _ => assertTrue(false)
        }
      }
    ),
    suite("coverage - PrimitiveType.Unit serialization")(
      test("Schema[Unit] round-trips correctly") {
        val schema       = Schema[Unit]
        val ds           = schema.toDynamicSchema
        val dv           = DynamicSchema.toDynamicValue(ds)
        val roundTrip    = DynamicSchema.fromDynamicValue(dv)
        val name: String = roundTrip.typeId.name
        assertTrue(name == "Unit")
      }
    ),
    suite("coverage - Term modifier serialization")(
      test("transient modifier round-trips correctly") {
        case class WithTransient(name: String, @Modifier.transient() temp: Int = 0)
        object WithTransient {
          implicit val schema: Schema[WithTransient] = Schema.derived
        }
        val _            = WithTransient("test", 0).name + WithTransient("test", 0).temp
        val ds           = WithTransient.schema.toDynamicSchema
        val dv           = DynamicSchema.toDynamicValue(ds)
        val roundTrip    = DynamicSchema.fromDynamicValue(dv)
        val hasTransient = roundTrip.reflect.asRecord.exists { r =>
          val tempField = r.fields.find(_.name == "temp")
          tempField.exists(_.modifiers.exists {
            case Modifier.transient() => true
            case _                    => false
          })
        }
        assertTrue(hasTransient)
      },
      test("rename modifier round-trips correctly") {
        case class WithRename(@Modifier.rename("full_name") val name: String)
        object WithRename {
          implicit val schema: Schema[WithRename] = Schema.derived
        }
        val _         = WithRename("test")
        val ds        = WithRename.schema.toDynamicSchema
        val dv        = DynamicSchema.toDynamicValue(ds)
        val roundTrip = DynamicSchema.fromDynamicValue(dv)
        val hasRename = roundTrip.reflect.asRecord.exists { r =>
          val nameField = r.fields.find(_.name == "name")
          nameField.exists(_.modifiers.exists {
            case Modifier.rename(n) => n == "full_name"
            case _                  => false
          })
        }
        assertTrue(hasRename)
      },
      test("alias modifier round-trips correctly") {
        case class WithAlias(@Modifier.alias("nm") name: String) {
          locally(name)
        }
        object WithAlias {
          implicit val schema: Schema[WithAlias] = Schema.derived
        }
        val _         = WithAlias("test")
        val ds        = WithAlias.schema.toDynamicSchema
        val dv        = DynamicSchema.toDynamicValue(ds)
        val roundTrip = DynamicSchema.fromDynamicValue(dv)
        val hasAlias  = roundTrip.reflect.asRecord.exists { r =>
          val nameField = r.fields.find(_.name == "name")
          nameField.exists(_.modifiers.exists {
            case Modifier.alias(n) => n == "nm"
            case _                 => false
          })
        }
        assertTrue(hasAlias)
      }
    ),
    suite("coverage - Wrapper serialization")(
      test("Wrapper schema round-trips correctly") {
        case class PositiveInt(value: Int) {
          locally(value)
        }
        object PositiveInt {
          implicit val schema: Schema[PositiveInt] = Schema[Int].transform(to = PositiveInt(_), from = _.value)
        }
        val _         = PositiveInt(1)
        val ds        = PositiveInt.schema.toDynamicSchema
        val dv        = DynamicSchema.toDynamicValue(ds)
        val roundTrip = DynamicSchema.fromDynamicValue(dv)
        val isWrapper = roundTrip.reflect.isWrapper
        assertTrue(isWrapper)
      }
    ),
    suite("coverage - Dynamic serialization")(
      test("Dynamic schema round-trips correctly") {
        val ds        = Schema[DynamicValue].toDynamicSchema
        val dv        = DynamicSchema.toDynamicValue(ds)
        val roundTrip = DynamicSchema.fromDynamicValue(dv)
        val isDynamic = roundTrip.reflect.isDynamic
        assertTrue(isDynamic)
      }
    ),
    suite("coverage - Sequence serialization")(
      test("Sequence schema round-trips correctly") {
        val ds           = Schema[List[Int]].toDynamicSchema
        val dv           = DynamicSchema.toDynamicValue(ds)
        val roundTrip    = DynamicSchema.fromDynamicValue(dv)
        val name: String = roundTrip.typeId.name
        assertTrue(name == "List")
      }
    ),
    suite("coverage - Map serialization")(
      test("Map schema round-trips correctly") {
        val ds           = Schema[Map[String, Int]].toDynamicSchema
        val dv           = DynamicSchema.toDynamicValue(ds)
        val roundTrip    = DynamicSchema.fromDynamicValue(dv)
        val name: String = roundTrip.typeId.name
        assertTrue(name == "Map")
      }
    ),
    suite("coverage - All PrimitiveType variants serialization")(
      test("PrimitiveType.Boolean round-trips") {
        val ds        = Schema[Boolean].toDynamicSchema
        val dv        = DynamicSchema.toDynamicValue(ds)
        val roundTrip = DynamicSchema.fromDynamicValue(dv)
        assertTrue(getTypeName(roundTrip) == "Boolean")
      },
      test("PrimitiveType.Byte round-trips") {
        val ds        = Schema[Byte].toDynamicSchema
        val dv        = DynamicSchema.toDynamicValue(ds)
        val roundTrip = DynamicSchema.fromDynamicValue(dv)
        assertTrue(getTypeName(roundTrip) == "Byte")
      },
      test("PrimitiveType.Short round-trips") {
        val ds        = Schema[Short].toDynamicSchema
        val dv        = DynamicSchema.toDynamicValue(ds)
        val roundTrip = DynamicSchema.fromDynamicValue(dv)
        assertTrue(getTypeName(roundTrip) == "Short")
      },
      test("PrimitiveType.Long round-trips") {
        val ds        = Schema[Long].toDynamicSchema
        val dv        = DynamicSchema.toDynamicValue(ds)
        val roundTrip = DynamicSchema.fromDynamicValue(dv)
        assertTrue(getTypeName(roundTrip) == "Long")
      },
      test("PrimitiveType.Float round-trips") {
        val ds        = Schema[Float].toDynamicSchema
        val dv        = DynamicSchema.toDynamicValue(ds)
        val roundTrip = DynamicSchema.fromDynamicValue(dv)
        assertTrue(getTypeName(roundTrip) == "Float")
      },
      test("PrimitiveType.Double round-trips") {
        val ds        = Schema[Double].toDynamicSchema
        val dv        = DynamicSchema.toDynamicValue(ds)
        val roundTrip = DynamicSchema.fromDynamicValue(dv)
        assertTrue(getTypeName(roundTrip) == "Double")
      },
      test("PrimitiveType.Char round-trips") {
        val ds        = Schema[Char].toDynamicSchema
        val dv        = DynamicSchema.toDynamicValue(ds)
        val roundTrip = DynamicSchema.fromDynamicValue(dv)
        assertTrue(getTypeName(roundTrip) == "Char")
      },
      test("PrimitiveType.BigInt round-trips") {
        val ds        = Schema[BigInt].toDynamicSchema
        val dv        = DynamicSchema.toDynamicValue(ds)
        val roundTrip = DynamicSchema.fromDynamicValue(dv)
        assertTrue(getTypeName(roundTrip) == "BigInt")
      },
      test("PrimitiveType.BigDecimal round-trips") {
        val ds        = Schema[BigDecimal].toDynamicSchema
        val dv        = DynamicSchema.toDynamicValue(ds)
        val roundTrip = DynamicSchema.fromDynamicValue(dv)
        assertTrue(getTypeName(roundTrip) == "BigDecimal")
      },
      test("PrimitiveType.UUID round-trips") {
        val ds        = Schema[java.util.UUID].toDynamicSchema
        val dv        = DynamicSchema.toDynamicValue(ds)
        val roundTrip = DynamicSchema.fromDynamicValue(dv)
        assertTrue(getTypeName(roundTrip) == "UUID")
      },
      test("PrimitiveType.Instant round-trips") {
        val ds        = Schema[java.time.Instant].toDynamicSchema
        val dv        = DynamicSchema.toDynamicValue(ds)
        val roundTrip = DynamicSchema.fromDynamicValue(dv)
        assertTrue(getTypeName(roundTrip) == "Instant")
      },
      test("PrimitiveType.LocalDate round-trips") {
        val ds        = Schema[java.time.LocalDate].toDynamicSchema
        val dv        = DynamicSchema.toDynamicValue(ds)
        val roundTrip = DynamicSchema.fromDynamicValue(dv)
        assertTrue(getTypeName(roundTrip) == "LocalDate")
      },
      test("PrimitiveType.LocalDateTime round-trips") {
        val ds        = Schema[java.time.LocalDateTime].toDynamicSchema
        val dv        = DynamicSchema.toDynamicValue(ds)
        val roundTrip = DynamicSchema.fromDynamicValue(dv)
        assertTrue(getTypeName(roundTrip) == "LocalDateTime")
      },
      test("PrimitiveType.LocalTime round-trips") {
        val ds        = Schema[java.time.LocalTime].toDynamicSchema
        val dv        = DynamicSchema.toDynamicValue(ds)
        val roundTrip = DynamicSchema.fromDynamicValue(dv)
        assertTrue(getTypeName(roundTrip) == "LocalTime")
      },
      test("PrimitiveType.Duration round-trips") {
        val ds        = Schema[java.time.Duration].toDynamicSchema
        val dv        = DynamicSchema.toDynamicValue(ds)
        val roundTrip = DynamicSchema.fromDynamicValue(dv)
        assertTrue(getTypeName(roundTrip) == "Duration")
      },
      test("PrimitiveType.DayOfWeek round-trips") {
        val ds        = Schema[java.time.DayOfWeek].toDynamicSchema
        val dv        = DynamicSchema.toDynamicValue(ds)
        val roundTrip = DynamicSchema.fromDynamicValue(dv)
        assertTrue(getTypeName(roundTrip) == "DayOfWeek")
      },
      test("PrimitiveType.Month round-trips") {
        val ds        = Schema[java.time.Month].toDynamicSchema
        val dv        = DynamicSchema.toDynamicValue(ds)
        val roundTrip = DynamicSchema.fromDynamicValue(dv)
        assertTrue(getTypeName(roundTrip) == "Month")
      },
      test("PrimitiveType.MonthDay round-trips") {
        val ds        = Schema[java.time.MonthDay].toDynamicSchema
        val dv        = DynamicSchema.toDynamicValue(ds)
        val roundTrip = DynamicSchema.fromDynamicValue(dv)
        assertTrue(getTypeName(roundTrip) == "MonthDay")
      },
      test("PrimitiveType.OffsetDateTime round-trips") {
        val ds        = Schema[java.time.OffsetDateTime].toDynamicSchema
        val dv        = DynamicSchema.toDynamicValue(ds)
        val roundTrip = DynamicSchema.fromDynamicValue(dv)
        assertTrue(getTypeName(roundTrip) == "OffsetDateTime")
      },
      test("PrimitiveType.OffsetTime round-trips") {
        val ds        = Schema[java.time.OffsetTime].toDynamicSchema
        val dv        = DynamicSchema.toDynamicValue(ds)
        val roundTrip = DynamicSchema.fromDynamicValue(dv)
        assertTrue(getTypeName(roundTrip) == "OffsetTime")
      },
      test("PrimitiveType.Period round-trips") {
        val ds        = Schema[java.time.Period].toDynamicSchema
        val dv        = DynamicSchema.toDynamicValue(ds)
        val roundTrip = DynamicSchema.fromDynamicValue(dv)
        assertTrue(getTypeName(roundTrip) == "Period")
      },
      test("PrimitiveType.Year round-trips") {
        val ds        = Schema[java.time.Year].toDynamicSchema
        val dv        = DynamicSchema.toDynamicValue(ds)
        val roundTrip = DynamicSchema.fromDynamicValue(dv)
        assertTrue(getTypeName(roundTrip) == "Year")
      },
      test("PrimitiveType.YearMonth round-trips") {
        val ds        = Schema[java.time.YearMonth].toDynamicSchema
        val dv        = DynamicSchema.toDynamicValue(ds)
        val roundTrip = DynamicSchema.fromDynamicValue(dv)
        assertTrue(getTypeName(roundTrip) == "YearMonth")
      },
      test("PrimitiveType.ZoneId round-trips") {
        val ds        = Schema[java.time.ZoneId].toDynamicSchema
        val dv        = DynamicSchema.toDynamicValue(ds)
        val roundTrip = DynamicSchema.fromDynamicValue(dv)
        assertTrue(getTypeName(roundTrip) == "ZoneId")
      },
      test("PrimitiveType.ZoneOffset round-trips") {
        val ds        = Schema[java.time.ZoneOffset].toDynamicSchema
        val dv        = DynamicSchema.toDynamicValue(ds)
        val roundTrip = DynamicSchema.fromDynamicValue(dv)
        assertTrue(getTypeName(roundTrip) == "ZoneOffset")
      },
      test("PrimitiveType.ZonedDateTime round-trips") {
        val ds        = Schema[java.time.ZonedDateTime].toDynamicSchema
        val dv        = DynamicSchema.toDynamicValue(ds)
        val roundTrip = DynamicSchema.fromDynamicValue(dv)
        assertTrue(getTypeName(roundTrip) == "ZonedDateTime")
      },
      test("PrimitiveType.Currency round-trips") {
        val ds        = Schema[java.util.Currency].toDynamicSchema
        val dv        = DynamicSchema.toDynamicValue(ds)
        val roundTrip = DynamicSchema.fromDynamicValue(dv)
        assertTrue(getTypeName(roundTrip) == "Currency")
      }
    ),
    suite("coverage - Variant serialization")(
      test("Variant schema round-trips correctly") {
        val ds        = Schema[Color].toDynamicSchema
        val dv        = DynamicSchema.toDynamicValue(ds)
        val roundTrip = DynamicSchema.fromDynamicValue(dv)
        assertTrue(getTypeName(roundTrip) == "Color")
      }
    ),
    suite("coverage - Deferred serialization")(
      test("Deferred schema check handles recursive types") {
        val ds        = Schema[Tree].toDynamicSchema
        val validLeaf = DynamicValue.Variant(
          "Leaf",
          DynamicValue.Record(Chunk("value" -> DynamicValue.Primitive(PrimitiveValue.Int(42))))
        )
        assertTrue(ds.check(validLeaf).isEmpty) &&
        assertTrue(getTypeName(ds) == "Tree")
      }
    ),
    suite("coverage - defaultValue and examples serialization")(
      test("defaultValue round-trips for Record") {
        val defaultPerson = DynamicValue.Record(
          Chunk(
            "name" -> DynamicValue.Primitive(PrimitiveValue.String("Default")),
            "age"  -> DynamicValue.Primitive(PrimitiveValue.Int(0))
          )
        )
        val ds        = Schema[Person].toDynamicSchema.defaultValue(defaultPerson)
        val dv        = DynamicSchema.toDynamicValue(ds)
        val roundTrip = DynamicSchema.fromDynamicValue(dv)
        assertTrue(roundTrip.getDefaultValue.isDefined)
      },
      test("examples round-trips for Record") {
        val example1 = DynamicValue.Record(
          Chunk(
            "name" -> DynamicValue.Primitive(PrimitiveValue.String("Alice")),
            "age"  -> DynamicValue.Primitive(PrimitiveValue.Int(30))
          )
        )
        val example2 = DynamicValue.Record(
          Chunk(
            "name" -> DynamicValue.Primitive(PrimitiveValue.String("Bob")),
            "age"  -> DynamicValue.Primitive(PrimitiveValue.Int(25))
          )
        )
        val ds        = Schema[Person].toDynamicSchema.examples(example1, example2)
        val dv        = DynamicSchema.toDynamicValue(ds)
        val roundTrip = DynamicSchema.fromDynamicValue(dv)
        assertTrue(roundTrip.examples.length == 2)
      }
    ),
    suite("coverage - Numeric Range validation with various numeric types")(
      test("Range validation for Byte") {
        val schema = Schema[Byte].reflect.asPrimitive
          .map(p =>
            new Schema(
              new Reflect.Primitive(
                PrimitiveType.Byte(Validation.Numeric.Range(Some(0.toByte), Some(100.toByte))),
                p.typeId,
                p.primitiveBinding
              )
            )
          )
          .get
        val ds = schema.toDynamicSchema
        assertTrue(ds.check(DynamicValue.Primitive(PrimitiveValue.Byte(50))).isEmpty) &&
        assertTrue(ds.check(DynamicValue.Primitive(PrimitiveValue.Byte(-1))).isDefined)
      },
      test("Range validation for Short") {
        val schema = Schema[Short].reflect.asPrimitive
          .map(p =>
            new Schema(
              new Reflect.Primitive(
                PrimitiveType.Short(Validation.Numeric.Range(Some(0.toShort), Some(100.toShort))),
                p.typeId,
                p.primitiveBinding
              )
            )
          )
          .get
        val ds = schema.toDynamicSchema
        assertTrue(ds.check(DynamicValue.Primitive(PrimitiveValue.Short(50))).isEmpty) &&
        assertTrue(ds.check(DynamicValue.Primitive(PrimitiveValue.Short(-1))).isDefined)
      },
      test("Range validation for Long") {
        val schema = Schema[Long].reflect.asPrimitive
          .map(p =>
            new Schema(
              new Reflect.Primitive(
                PrimitiveType.Long(Validation.Numeric.Range(Some(0L), Some(100L))),
                p.typeId,
                p.primitiveBinding
              )
            )
          )
          .get
        val ds = schema.toDynamicSchema
        assertTrue(ds.check(DynamicValue.Primitive(PrimitiveValue.Long(50L))).isEmpty) &&
        assertTrue(ds.check(DynamicValue.Primitive(PrimitiveValue.Long(-1L))).isDefined)
      },
      test("Range validation for Float") {
        val schema = Schema[Float].reflect.asPrimitive
          .map(p =>
            new Schema(
              new Reflect.Primitive(
                PrimitiveType.Float(Validation.Numeric.Range(Some(0.0f), Some(100.0f))),
                p.typeId,
                p.primitiveBinding
              )
            )
          )
          .get
        val ds = schema.toDynamicSchema
        assertTrue(ds.check(DynamicValue.Primitive(PrimitiveValue.Float(50.0f))).isEmpty) &&
        assertTrue(ds.check(DynamicValue.Primitive(PrimitiveValue.Float(-1.0f))).isDefined)
      },
      test("Range validation for Double") {
        val schema = Schema[Double].reflect.asPrimitive
          .map(p =>
            new Schema(
              new Reflect.Primitive(
                PrimitiveType.Double(Validation.Numeric.Range(Some(0.0), Some(100.0))),
                p.typeId,
                p.primitiveBinding
              )
            )
          )
          .get
        val ds = schema.toDynamicSchema
        assertTrue(ds.check(DynamicValue.Primitive(PrimitiveValue.Double(50.0))).isEmpty) &&
        assertTrue(ds.check(DynamicValue.Primitive(PrimitiveValue.Double(-1.0))).isDefined)
      },
      test("Range validation for BigInt") {
        val schema = Schema[BigInt].reflect.asPrimitive
          .map(p =>
            new Schema(
              new Reflect.Primitive(
                PrimitiveType.BigInt(Validation.Numeric.Range(Some(BigInt(0)), Some(BigInt(100)))),
                p.typeId,
                p.primitiveBinding
              )
            )
          )
          .get
        val ds = schema.toDynamicSchema
        assertTrue(ds.check(DynamicValue.Primitive(PrimitiveValue.BigInt(BigInt(50)))).isEmpty) &&
        assertTrue(ds.check(DynamicValue.Primitive(PrimitiveValue.BigInt(BigInt(-1)))).isDefined)
      },
      test("Range validation for BigDecimal") {
        val schema = Schema[BigDecimal].reflect.asPrimitive
          .map(p =>
            new Schema(
              new Reflect.Primitive(
                PrimitiveType.BigDecimal(Validation.Numeric.Range(Some(BigDecimal(0)), Some(BigDecimal(100)))),
                p.typeId,
                p.primitiveBinding
              )
            )
          )
          .get
        val ds = schema.toDynamicSchema
        assertTrue(ds.check(DynamicValue.Primitive(PrimitiveValue.BigDecimal(BigDecimal(50)))).isEmpty) &&
        assertTrue(ds.check(DynamicValue.Primitive(PrimitiveValue.BigDecimal(BigDecimal(-1)))).isDefined)
      }
    ),
    suite("coverage - Numeric Set validation with various numeric types")(
      test("Set validation for Byte") {
        val schema = Schema[Byte].reflect.asPrimitive
          .map(p =>
            new Schema(
              new Reflect.Primitive(
                PrimitiveType.Byte(Validation.Numeric.Set(Set(1.toByte, 2.toByte, 3.toByte))),
                p.typeId,
                p.primitiveBinding
              )
            )
          )
          .get
        val ds = schema.toDynamicSchema
        assertTrue(ds.check(DynamicValue.Primitive(PrimitiveValue.Byte(2))).isEmpty) &&
        assertTrue(ds.check(DynamicValue.Primitive(PrimitiveValue.Byte(5))).isDefined)
      },
      test("Set validation for Short") {
        val schema = Schema[Short].reflect.asPrimitive
          .map(p =>
            new Schema(
              new Reflect.Primitive(
                PrimitiveType.Short(Validation.Numeric.Set(Set(1.toShort, 2.toShort, 3.toShort))),
                p.typeId,
                p.primitiveBinding
              )
            )
          )
          .get
        val ds = schema.toDynamicSchema
        assertTrue(ds.check(DynamicValue.Primitive(PrimitiveValue.Short(2))).isEmpty) &&
        assertTrue(ds.check(DynamicValue.Primitive(PrimitiveValue.Short(5))).isDefined)
      },
      test("Set validation for Long") {
        val schema = Schema[Long].reflect.asPrimitive
          .map(p =>
            new Schema(
              new Reflect.Primitive(
                PrimitiveType.Long(Validation.Numeric.Set(Set(1L, 2L, 3L))),
                p.typeId,
                p.primitiveBinding
              )
            )
          )
          .get
        val ds = schema.toDynamicSchema
        assertTrue(ds.check(DynamicValue.Primitive(PrimitiveValue.Long(2L))).isEmpty) &&
        assertTrue(ds.check(DynamicValue.Primitive(PrimitiveValue.Long(5L))).isDefined)
      },
      test("Set validation for Float") {
        val schema = Schema[Float].reflect.asPrimitive
          .map(p =>
            new Schema(
              new Reflect.Primitive(
                PrimitiveType.Float(Validation.Numeric.Set(Set(1.0f, 2.0f, 3.0f))),
                p.typeId,
                p.primitiveBinding
              )
            )
          )
          .get
        val ds = schema.toDynamicSchema
        assertTrue(ds.check(DynamicValue.Primitive(PrimitiveValue.Float(2.0f))).isEmpty) &&
        assertTrue(ds.check(DynamicValue.Primitive(PrimitiveValue.Float(5.0f))).isDefined)
      },
      test("Set validation for Double") {
        val schema = Schema[Double].reflect.asPrimitive
          .map(p =>
            new Schema(
              new Reflect.Primitive(
                PrimitiveType.Double(Validation.Numeric.Set(Set(1.0, 2.0, 3.0))),
                p.typeId,
                p.primitiveBinding
              )
            )
          )
          .get
        val ds = schema.toDynamicSchema
        assertTrue(ds.check(DynamicValue.Primitive(PrimitiveValue.Double(2.0))).isEmpty) &&
        assertTrue(ds.check(DynamicValue.Primitive(PrimitiveValue.Double(5.0))).isDefined)
      },
      test("Set validation for BigInt") {
        val schema = Schema[BigInt].reflect.asPrimitive
          .map(p =>
            new Schema(
              new Reflect.Primitive(
                PrimitiveType.BigInt(Validation.Numeric.Set(Set(BigInt(1), BigInt(2), BigInt(3)))),
                p.typeId,
                p.primitiveBinding
              )
            )
          )
          .get
        val ds = schema.toDynamicSchema
        assertTrue(ds.check(DynamicValue.Primitive(PrimitiveValue.BigInt(BigInt(2)))).isEmpty) &&
        assertTrue(ds.check(DynamicValue.Primitive(PrimitiveValue.BigInt(BigInt(5)))).isDefined)
      },
      test("Set validation for BigDecimal") {
        val schema = Schema[BigDecimal].reflect.asPrimitive
          .map(p =>
            new Schema(
              new Reflect.Primitive(
                PrimitiveType.BigDecimal(Validation.Numeric.Set(Set(BigDecimal(1), BigDecimal(2), BigDecimal(3)))),
                p.typeId,
                p.primitiveBinding
              )
            )
          )
          .get
        val ds = schema.toDynamicSchema
        assertTrue(ds.check(DynamicValue.Primitive(PrimitiveValue.BigDecimal(BigDecimal(2)))).isEmpty) &&
        assertTrue(ds.check(DynamicValue.Primitive(PrimitiveValue.BigDecimal(BigDecimal(5)))).isDefined)
      }
    ),
    suite("Hand-written schema round-trips")(
      test("TypeId schema round-trips") {
        import zio.blocks.typeid._
        val tid       = TypeId.nominal[Int]("Int", Owner.fromPackagePath("scala"))
        val schema    = DynamicSchema.typeIdSchema
        val dv        = schema.toDynamicValue(tid)
        val roundTrip = schema.fromDynamicValue(dv)
        val isRight   = roundTrip.isRight
        val name      = roundTrip.toOption.map(_.name).getOrElse("")
        assertTrue(isRight) && assertTrue(name == "Int")
      },
      test("TypeId with type params round-trips") {
        import zio.blocks.typeid._
        val listTypeId = TypeId.nominal[List[Int]](
          "List",
          Owner.fromPackagePath("scala.collection.immutable"),
          List(TypeParam("A", 0, Variance.Covariant))
        )
        val schema    = DynamicSchema.typeIdSchema
        val dv        = schema.toDynamicValue(listTypeId)
        val roundTrip = schema.fromDynamicValue(dv)
        val isRight   = roundTrip.isRight
        val name      = roundTrip.toOption.map(_.name).getOrElse("")
        val hasParams = roundTrip.toOption.exists(_.typeParams.nonEmpty)
        assertTrue(isRight) && assertTrue(name == "List") && assertTrue(hasParams)
      },
      test("Doc.Empty round-trips via docSchema") {
        val doc: Doc  = Doc.Empty
        val schema    = DynamicSchema.docSchema
        val dv        = schema.toDynamicValue(doc)
        val roundTrip = schema.fromDynamicValue(dv)
        assertTrue(roundTrip == Right(Doc.Empty))
      },
      test("Doc.Text round-trips via docSchema") {
        val doc: Doc  = Doc.Text("hello")
        val schema    = DynamicSchema.docSchema
        val dv        = schema.toDynamicValue(doc)
        val roundTrip = schema.fromDynamicValue(dv)
        assertTrue(roundTrip == Right(Doc.Text("hello")))
      },
      test("Doc.Concat round-trips via docSchema") {
        val doc: Doc  = Doc.Concat(IndexedSeq(Doc.Text("hello"), Doc.Text("world")))
        val schema    = DynamicSchema.docSchema
        val dv        = schema.toDynamicValue(doc)
        val roundTrip = schema.fromDynamicValue(dv)
        val isRight   = roundTrip.isRight
        assertTrue(isRight)
      },
      test("Doc.Leaf round-trips via docLeafSchema") {
        val leaf: Doc.Leaf = Doc.Text("test")
        val schema         = DynamicSchema.docLeafSchema
        val dv             = schema.toDynamicValue(leaf)
        val roundTrip      = schema.fromDynamicValue(dv)
        assertTrue(roundTrip == Right(Doc.Text("test")))
      },
      test("Validation.None round-trips via validationSchema") {
        val v: Validation[_] = Validation.None
        val schema           = DynamicSchema.validationSchema
        val dv               = schema.toDynamicValue(v)
        val roundTrip        = schema.fromDynamicValue(dv)
        assertTrue(roundTrip == Right(Validation.None))
      },
      test("Validation.Numeric.Positive round-trips via validationSchema") {
        val v: Validation[_] = Validation.Numeric.Positive
        val schema           = DynamicSchema.validationSchema
        val dv               = schema.toDynamicValue(v)
        val roundTrip        = schema.fromDynamicValue(dv)
        assertTrue(roundTrip == Right(Validation.Numeric.Positive))
      },
      test("Validation.String.NonEmpty round-trips via validationSchema") {
        val v: Validation[_] = Validation.String.NonEmpty
        val schema           = DynamicSchema.validationSchema
        val dv               = schema.toDynamicValue(v)
        val roundTrip        = schema.fromDynamicValue(dv)
        assertTrue(roundTrip == Right(Validation.String.NonEmpty))
      },
      test("PrimitiveType.Int round-trips via primitiveTypeSchema") {
        val pt: PrimitiveType[_] = PrimitiveType.Int(Validation.None)
        val schema               = DynamicSchema.primitiveTypeSchema
        val dv                   = schema.toDynamicValue(pt)
        val roundTrip            = schema.fromDynamicValue(dv)
        val isRight              = roundTrip.isRight
        assertTrue(isRight)
      },
      test("PrimitiveType.String with validation round-trips via primitiveTypeSchema") {
        val pt: PrimitiveType[_] = PrimitiveType.String(Validation.String.NonEmpty)
        val schema               = DynamicSchema.primitiveTypeSchema
        val dv                   = schema.toDynamicValue(pt)
        val roundTrip            = schema.fromDynamicValue(dv)
        val isRight              = roundTrip.isRight
        assertTrue(isRight)
      },
      test("PrimitiveType.Unit round-trips via primitiveTypeSchema") {
        val pt: PrimitiveType[_] = PrimitiveType.Unit
        val schema               = DynamicSchema.primitiveTypeSchema
        val dv                   = schema.toDynamicValue(pt)
        val roundTrip            = schema.fromDynamicValue(dv)
        val isRight              = roundTrip.isRight
        assertTrue(isRight)
      },
      test("PrimitiveType.Boolean round-trips via primitiveTypeSchema") {
        val pt: PrimitiveType[_] = PrimitiveType.Boolean(Validation.None)
        val schema               = DynamicSchema.primitiveTypeSchema
        val dv                   = schema.toDynamicValue(pt)
        val roundTrip            = schema.fromDynamicValue(dv)
        val isRight              = roundTrip.isRight
        assertTrue(isRight)
      },
      test("PrimitiveType.Long round-trips via primitiveTypeSchema") {
        val pt: PrimitiveType[_] = PrimitiveType.Long(Validation.Numeric.Positive)
        val schema               = DynamicSchema.primitiveTypeSchema
        val dv                   = schema.toDynamicValue(pt)
        val roundTrip            = schema.fromDynamicValue(dv)
        val isRight              = roundTrip.isRight
        assertTrue(isRight)
      },
      test("PrimitiveType.Float round-trips via primitiveTypeSchema") {
        val pt: PrimitiveType[_] = PrimitiveType.Float(Validation.None)
        val schema               = DynamicSchema.primitiveTypeSchema
        val dv                   = schema.toDynamicValue(pt)
        val roundTrip            = schema.fromDynamicValue(dv)
        val isRight              = roundTrip.isRight
        assertTrue(isRight)
      },
      test("PrimitiveType.Double round-trips via primitiveTypeSchema") {
        val pt: PrimitiveType[_] = PrimitiveType.Double(Validation.Numeric.NonNegative)
        val schema               = DynamicSchema.primitiveTypeSchema
        val dv                   = schema.toDynamicValue(pt)
        val roundTrip            = schema.fromDynamicValue(dv)
        val isRight              = roundTrip.isRight
        assertTrue(isRight)
      },
      test("PrimitiveType.Char round-trips via primitiveTypeSchema") {
        val pt: PrimitiveType[_] = PrimitiveType.Char(Validation.None)
        val schema               = DynamicSchema.primitiveTypeSchema
        val dv                   = schema.toDynamicValue(pt)
        val roundTrip            = schema.fromDynamicValue(dv)
        val isRight              = roundTrip.isRight
        assertTrue(isRight)
      },
      test("PrimitiveType.Byte round-trips via primitiveTypeSchema") {
        val pt: PrimitiveType[_] = PrimitiveType.Byte(Validation.None)
        val schema               = DynamicSchema.primitiveTypeSchema
        val dv                   = schema.toDynamicValue(pt)
        val roundTrip            = schema.fromDynamicValue(dv)
        val isRight              = roundTrip.isRight
        assertTrue(isRight)
      },
      test("PrimitiveType.Short round-trips via primitiveTypeSchema") {
        val pt: PrimitiveType[_] = PrimitiveType.Short(Validation.None)
        val schema               = DynamicSchema.primitiveTypeSchema
        val dv                   = schema.toDynamicValue(pt)
        val roundTrip            = schema.fromDynamicValue(dv)
        val isRight              = roundTrip.isRight
        assertTrue(isRight)
      },
      test("PrimitiveType.BigInt round-trips via primitiveTypeSchema") {
        val pt: PrimitiveType[_] = PrimitiveType.BigInt(Validation.None)
        val schema               = DynamicSchema.primitiveTypeSchema
        val dv                   = schema.toDynamicValue(pt)
        val roundTrip            = schema.fromDynamicValue(dv)
        val isRight              = roundTrip.isRight
        assertTrue(isRight)
      },
      test("PrimitiveType.BigDecimal round-trips via primitiveTypeSchema") {
        val pt: PrimitiveType[_] = PrimitiveType.BigDecimal(Validation.None)
        val schema               = DynamicSchema.primitiveTypeSchema
        val dv                   = schema.toDynamicValue(pt)
        val roundTrip            = schema.fromDynamicValue(dv)
        val isRight              = roundTrip.isRight
        assertTrue(isRight)
      },
      test("PrimitiveType.UUID round-trips via primitiveTypeSchema") {
        val pt: PrimitiveType[_] = PrimitiveType.UUID(Validation.None)
        val schema               = DynamicSchema.primitiveTypeSchema
        val dv                   = schema.toDynamicValue(pt)
        val roundTrip            = schema.fromDynamicValue(dv)
        val isRight              = roundTrip.isRight
        assertTrue(isRight)
      },
      test("PrimitiveType.LocalDate round-trips via primitiveTypeSchema") {
        val pt: PrimitiveType[_] = PrimitiveType.LocalDate(Validation.None)
        val schema               = DynamicSchema.primitiveTypeSchema
        val dv                   = schema.toDynamicValue(pt)
        val roundTrip            = schema.fromDynamicValue(dv)
        val isRight              = roundTrip.isRight
        assertTrue(isRight)
      },
      test("PrimitiveType.LocalTime round-trips via primitiveTypeSchema") {
        val pt: PrimitiveType[_] = PrimitiveType.LocalTime(Validation.None)
        val schema               = DynamicSchema.primitiveTypeSchema
        val dv                   = schema.toDynamicValue(pt)
        val roundTrip            = schema.fromDynamicValue(dv)
        val isRight              = roundTrip.isRight
        assertTrue(isRight)
      },
      test("PrimitiveType.LocalDateTime round-trips via primitiveTypeSchema") {
        val pt: PrimitiveType[_] = PrimitiveType.LocalDateTime(Validation.None)
        val schema               = DynamicSchema.primitiveTypeSchema
        val dv                   = schema.toDynamicValue(pt)
        val roundTrip            = schema.fromDynamicValue(dv)
        val isRight              = roundTrip.isRight
        assertTrue(isRight)
      },
      test("PrimitiveType.Instant round-trips via primitiveTypeSchema") {
        val pt: PrimitiveType[_] = PrimitiveType.Instant(Validation.None)
        val schema               = DynamicSchema.primitiveTypeSchema
        val dv                   = schema.toDynamicValue(pt)
        val roundTrip            = schema.fromDynamicValue(dv)
        val isRight              = roundTrip.isRight
        assertTrue(isRight)
      },
      test("PrimitiveType.Duration round-trips via primitiveTypeSchema") {
        val pt: PrimitiveType[_] = PrimitiveType.Duration(Validation.None)
        val schema               = DynamicSchema.primitiveTypeSchema
        val dv                   = schema.toDynamicValue(pt)
        val roundTrip            = schema.fromDynamicValue(dv)
        val isRight              = roundTrip.isRight
        assertTrue(isRight)
      },
      test("PrimitiveType.DayOfWeek round-trips via primitiveTypeSchema") {
        val pt: PrimitiveType[_] = PrimitiveType.DayOfWeek(Validation.None)
        val schema               = DynamicSchema.primitiveTypeSchema
        val dv                   = schema.toDynamicValue(pt)
        val roundTrip            = schema.fromDynamicValue(dv)
        val isRight              = roundTrip.isRight
        assertTrue(isRight)
      },
      test("PrimitiveType.Month round-trips via primitiveTypeSchema") {
        val pt: PrimitiveType[_] = PrimitiveType.Month(Validation.None)
        val schema               = DynamicSchema.primitiveTypeSchema
        val dv                   = schema.toDynamicValue(pt)
        val roundTrip            = schema.fromDynamicValue(dv)
        val isRight              = roundTrip.isRight
        assertTrue(isRight)
      },
      test("PrimitiveType.MonthDay round-trips via primitiveTypeSchema") {
        val pt: PrimitiveType[_] = PrimitiveType.MonthDay(Validation.None)
        val schema               = DynamicSchema.primitiveTypeSchema
        val dv                   = schema.toDynamicValue(pt)
        val roundTrip            = schema.fromDynamicValue(dv)
        val isRight              = roundTrip.isRight
        assertTrue(isRight)
      },
      test("PrimitiveType.Year round-trips via primitiveTypeSchema") {
        val pt: PrimitiveType[_] = PrimitiveType.Year(Validation.None)
        val schema               = DynamicSchema.primitiveTypeSchema
        val dv                   = schema.toDynamicValue(pt)
        val roundTrip            = schema.fromDynamicValue(dv)
        val isRight              = roundTrip.isRight
        assertTrue(isRight)
      },
      test("PrimitiveType.YearMonth round-trips via primitiveTypeSchema") {
        val pt: PrimitiveType[_] = PrimitiveType.YearMonth(Validation.None)
        val schema               = DynamicSchema.primitiveTypeSchema
        val dv                   = schema.toDynamicValue(pt)
        val roundTrip            = schema.fromDynamicValue(dv)
        val isRight              = roundTrip.isRight
        assertTrue(isRight)
      },
      test("PrimitiveType.ZoneId round-trips via primitiveTypeSchema") {
        val pt: PrimitiveType[_] = PrimitiveType.ZoneId(Validation.None)
        val schema               = DynamicSchema.primitiveTypeSchema
        val dv                   = schema.toDynamicValue(pt)
        val roundTrip            = schema.fromDynamicValue(dv)
        val isRight              = roundTrip.isRight
        assertTrue(isRight)
      },
      test("PrimitiveType.ZoneOffset round-trips via primitiveTypeSchema") {
        val pt: PrimitiveType[_] = PrimitiveType.ZoneOffset(Validation.None)
        val schema               = DynamicSchema.primitiveTypeSchema
        val dv                   = schema.toDynamicValue(pt)
        val roundTrip            = schema.fromDynamicValue(dv)
        val isRight              = roundTrip.isRight
        assertTrue(isRight)
      },
      test("PrimitiveType.ZonedDateTime round-trips via primitiveTypeSchema") {
        val pt: PrimitiveType[_] = PrimitiveType.ZonedDateTime(Validation.None)
        val schema               = DynamicSchema.primitiveTypeSchema
        val dv                   = schema.toDynamicValue(pt)
        val roundTrip            = schema.fromDynamicValue(dv)
        val isRight              = roundTrip.isRight
        assertTrue(isRight)
      },
      test("PrimitiveType.OffsetTime round-trips via primitiveTypeSchema") {
        val pt: PrimitiveType[_] = PrimitiveType.OffsetTime(Validation.None)
        val schema               = DynamicSchema.primitiveTypeSchema
        val dv                   = schema.toDynamicValue(pt)
        val roundTrip            = schema.fromDynamicValue(dv)
        val isRight              = roundTrip.isRight
        assertTrue(isRight)
      },
      test("PrimitiveType.OffsetDateTime round-trips via primitiveTypeSchema") {
        val pt: PrimitiveType[_] = PrimitiveType.OffsetDateTime(Validation.None)
        val schema               = DynamicSchema.primitiveTypeSchema
        val dv                   = schema.toDynamicValue(pt)
        val roundTrip            = schema.fromDynamicValue(dv)
        val isRight              = roundTrip.isRight
        assertTrue(isRight)
      },
      test("PrimitiveType.Period round-trips via primitiveTypeSchema") {
        val pt: PrimitiveType[_] = PrimitiveType.Period(Validation.None)
        val schema               = DynamicSchema.primitiveTypeSchema
        val dv                   = schema.toDynamicValue(pt)
        val roundTrip            = schema.fromDynamicValue(dv)
        val isRight              = roundTrip.isRight
        assertTrue(isRight)
      },
      test("PrimitiveType.Currency round-trips via primitiveTypeSchema") {
        val pt: PrimitiveType[_] = PrimitiveType.Currency(Validation.None)
        val schema               = DynamicSchema.primitiveTypeSchema
        val dv                   = schema.toDynamicValue(pt)
        val roundTrip            = schema.fromDynamicValue(dv)
        val isRight              = roundTrip.isRight
        assertTrue(isRight)
      }
    ),
    suite("Coverage - edge cases for validation on wrong types")(
      test("String.Length validation fails for non-string") {
        val schema = Schema[String].reflect.asPrimitive
          .map(p =>
            new Reflect.Primitive[binding.Binding, String](
              PrimitiveType.String(Validation.String.Length(Some(1), Some(10))),
              p.typeId,
              p.primitiveBinding
            )
          )
          .map(r => new Schema(r))
          .getOrElse(Schema[String])
        val ds = schema.toDynamicSchema
        assertTrue(ds.check(DynamicValue.Primitive(PrimitiveValue.Int(42))).isDefined)
      },
      test("String.Pattern validation fails for non-string") {
        val schema = Schema[String].reflect.asPrimitive
          .map(p =>
            new Reflect.Primitive[binding.Binding, String](
              PrimitiveType.String(Validation.String.Pattern(".*")),
              p.typeId,
              p.primitiveBinding
            )
          )
          .map(r => new Schema(r))
          .getOrElse(Schema[String])
        val ds = schema.toDynamicSchema
        assertTrue(ds.check(DynamicValue.Primitive(PrimitiveValue.Int(42))).isDefined)
      },
      test("Numeric.Range validation fails for non-numeric") {
        val schema = Schema[Int].reflect.asPrimitive
          .map(p =>
            new Schema(
              new Reflect.Primitive(
                PrimitiveType.Int(Validation.Numeric.Range(Some(0), Some(100))),
                p.typeId,
                p.primitiveBinding
              )
            )
          )
          .get
        val ds = schema.toDynamicSchema
        assertTrue(ds.check(DynamicValue.Primitive(PrimitiveValue.String("not a number"))).isDefined)
      },
      test("Numeric.Set validation fails for non-numeric") {
        val schema = Schema[Int].reflect.asPrimitive
          .map(p =>
            new Schema(
              new Reflect.Primitive(
                PrimitiveType.Int(Validation.Numeric.Set(Set(1, 2, 3))),
                p.typeId,
                p.primitiveBinding
              )
            )
          )
          .get
        val ds = schema.toDynamicSchema
        assertTrue(ds.check(DynamicValue.Primitive(PrimitiveValue.String("not a number"))).isDefined)
      }
    ),
    suite("Coverage - Deferred handling in getDefaultValue and examples")(
      test("getDefaultValue retrieves value through Deferred") {
        val ds = Schema[Tree].toDynamicSchema
        val dv = DynamicValue.Variant(
          "Leaf",
          DynamicValue.Record(Chunk("value" -> DynamicValue.Primitive(PrimitiveValue.Int(42))))
        )
        val dsWithDefault = ds.defaultValue(dv)
        assertTrue(dsWithDefault.getDefaultValue.contains(dv))
      },
      test("examples retrieves values through Deferred") {
        val ds  = Schema[Tree].toDynamicSchema
        val dv1 = DynamicValue.Variant(
          "Leaf",
          DynamicValue.Record(Chunk("value" -> DynamicValue.Primitive(PrimitiveValue.Int(1))))
        )
        val dv2 = DynamicValue.Variant(
          "Leaf",
          DynamicValue.Record(Chunk("value" -> DynamicValue.Primitive(PrimitiveValue.Int(2))))
        )
        val dsWithExamples = ds.examples(dv1, dv2)
        assertTrue(dsWithExamples.examples.length == 2)
      }
    ),
    suite("Coverage - Wrapper serialization in reflectToDynamicValue")(
      test("Wrapper schema serializes and deserializes") {
        case class WrappedInt(value: Int) {
          locally(value)
        }
        object WrappedInt {
          implicit val schema: Schema[WrappedInt] = Schema[Int].transform(to = WrappedInt(_), from = _.value)
        }
        val _         = WrappedInt(1)
        val original  = WrappedInt.schema.toDynamicSchema
        val dv        = DynamicSchema.toDynamicValue(original)
        val roundTrip = DynamicSchema.fromDynamicValue(dv)
        assertTrue(roundTrip.check(DynamicValue.Primitive(PrimitiveValue.Int(42))).isEmpty)
      }
    ),
    suite("Coverage - Dynamic serialization in reflectToDynamicValue")(
      test("Dynamic schema serializes and deserializes") {
        val original  = Schema[DynamicValue].toDynamicSchema
        val dv        = DynamicSchema.toDynamicValue(original)
        val roundTrip = DynamicSchema.fromDynamicValue(dv)
        assertTrue(roundTrip.check(DynamicValue.Primitive(PrimitiveValue.Int(42))).isEmpty)
      }
    ),
    suite("Coverage - modifier serialization")(
      test("config modifier on schema serializes correctly") {
        val schemaWithMod = Schema[Int].modifier(Modifier.config("key", "value"))
        val ds            = schemaWithMod.toDynamicSchema
        val dv            = DynamicSchema.toDynamicValue(ds)
        val roundTrip     = DynamicSchema.fromDynamicValue(dv)
        assertTrue(roundTrip.modifiers.nonEmpty)
      }
    ),
    suite("Coverage - deserialization fallback branches")(
      test("unknown variant returns Dynamic") {
        val unknownDv = DynamicValue.Variant("UnknownType", DynamicValue.Record(Chunk.empty))
        val roundTrip = DynamicSchema.fromDynamicValue(unknownDv)
        val isDynamic = roundTrip.reflect.asDynamic.isDefined
        assertTrue(isDynamic)
      },
      test("non-variant DynamicValue returns Dynamic") {
        val primitiveDv = DynamicValue.Primitive(PrimitiveValue.Int(42))
        val roundTrip   = DynamicSchema.fromDynamicValue(primitiveDv)
        val isDynamic   = roundTrip.reflect.asDynamic.isDefined
        assertTrue(isDynamic)
      },
      test("malformed TypeId with non-Record owner falls back gracefully") {
        val malformedTypeId = DynamicValue.Record(
          Chunk(
            "namespace" -> DynamicValue.Primitive(PrimitiveValue.String("not a record")),
            "name"      -> DynamicValue.Primitive(PrimitiveValue.String("Test")),
            "params"    -> DynamicValue.Sequence(Chunk.empty)
          )
        )
        val primitiveWithMalformedTypeId = DynamicValue.Variant(
          "Primitive",
          DynamicValue.Record(
            Chunk(
              "typeId"        -> malformedTypeId,
              "doc"           -> DynamicValue.Variant("Empty", DynamicValue.Record(Chunk.empty)),
              "modifiers"     -> DynamicValue.Sequence(Chunk.empty),
              "primitiveType" -> DynamicValue.Variant("Int", DynamicValue.Record(Chunk.empty)),
              "defaultValue"  -> DynamicValue.Null,
              "examples"      -> DynamicValue.Sequence(Chunk.empty)
            )
          )
        )
        val roundTrip  = DynamicSchema.fromDynamicValue(primitiveWithMalformedTypeId)
        val typeIdName = roundTrip.typeId.name
        assertTrue(typeIdName == "Unknown")
      },
      test("malformed TypeId with non-String name falls back to Unknown") {
        val malformedTypeId = DynamicValue.Record(
          Chunk(
            "namespace" -> DynamicValue.Record(
              Chunk(
                "packages" -> DynamicValue.Sequence(Chunk.empty),
                "values"   -> DynamicValue.Sequence(Chunk.empty)
              )
            ),
            "name"   -> DynamicValue.Primitive(PrimitiveValue.Int(42)),
            "params" -> DynamicValue.Sequence(Chunk.empty)
          )
        )
        val primitiveWithMalformedTypeId = DynamicValue.Variant(
          "Primitive",
          DynamicValue.Record(
            Chunk(
              "typeId"        -> malformedTypeId,
              "doc"           -> DynamicValue.Variant("Empty", DynamicValue.Record(Chunk.empty)),
              "modifiers"     -> DynamicValue.Sequence(Chunk.empty),
              "primitiveType" -> DynamicValue.Variant("Int", DynamicValue.Record(Chunk.empty)),
              "defaultValue"  -> DynamicValue.Null,
              "examples"      -> DynamicValue.Sequence(Chunk.empty)
            )
          )
        )
        val roundTrip  = DynamicSchema.fromDynamicValue(primitiveWithMalformedTypeId)
        val typeIdName = roundTrip.typeId.name
        assertTrue(typeIdName == "Unknown")
      },
      test("TypeId that's not a Record falls back to Unknown") {
        val primitiveWithMalformedTypeId = DynamicValue.Variant(
          "Primitive",
          DynamicValue.Record(
            Chunk(
              "typeId"        -> DynamicValue.Primitive(PrimitiveValue.String("not a record")),
              "doc"           -> DynamicValue.Variant("Empty", DynamicValue.Record(Chunk.empty)),
              "modifiers"     -> DynamicValue.Sequence(Chunk.empty),
              "primitiveType" -> DynamicValue.Variant("Int", DynamicValue.Record(Chunk.empty)),
              "defaultValue"  -> DynamicValue.Null,
              "examples"      -> DynamicValue.Sequence(Chunk.empty)
            )
          )
        )
        val roundTrip  = DynamicSchema.fromDynamicValue(primitiveWithMalformedTypeId)
        val typeIdName = roundTrip.typeId.name
        assertTrue(typeIdName == "Unknown")
      },
      test("malformed doc falls back to Empty") {
        val primitiveWithMalformedDoc = DynamicValue.Variant(
          "Primitive",
          DynamicValue.Record(
            Chunk(
              "typeId" -> DynamicValue.Record(
                Chunk(
                  "namespace" -> DynamicValue.Record(
                    Chunk(
                      "packages" -> DynamicValue.Sequence(Chunk.empty),
                      "values"   -> DynamicValue.Sequence(Chunk.empty)
                    )
                  ),
                  "name"   -> DynamicValue.Primitive(PrimitiveValue.String("Test")),
                  "params" -> DynamicValue.Sequence(Chunk.empty)
                )
              ),
              "doc"           -> DynamicValue.Primitive(PrimitiveValue.String("not a variant")),
              "modifiers"     -> DynamicValue.Sequence(Chunk.empty),
              "primitiveType" -> DynamicValue.Variant("Int", DynamicValue.Record(Chunk.empty)),
              "defaultValue"  -> DynamicValue.Null,
              "examples"      -> DynamicValue.Sequence(Chunk.empty)
            )
          )
        )
        val roundTrip = DynamicSchema.fromDynamicValue(primitiveWithMalformedDoc)
        assertTrue(roundTrip.doc == Doc.Empty)
      },
      test("malformed typeArgs in TypeId falls back to empty") {
        val malformedTypeId = DynamicValue.Record(
          Chunk(
            "namespace" -> DynamicValue.Record(
              Chunk(
                "packages" -> DynamicValue.Sequence(Chunk.empty),
                "values"   -> DynamicValue.Sequence(Chunk.empty)
              )
            ),
            "name"   -> DynamicValue.Primitive(PrimitiveValue.String("Test")),
            "params" -> DynamicValue.Primitive(PrimitiveValue.String("not a sequence"))
          )
        )
        val primitiveWithMalformedTypeId = DynamicValue.Variant(
          "Primitive",
          DynamicValue.Record(
            Chunk(
              "typeId"        -> malformedTypeId,
              "doc"           -> DynamicValue.Variant("Empty", DynamicValue.Record(Chunk.empty)),
              "modifiers"     -> DynamicValue.Sequence(Chunk.empty),
              "primitiveType" -> DynamicValue.Variant("Int", DynamicValue.Record(Chunk.empty)),
              "defaultValue"  -> DynamicValue.Null,
              "examples"      -> DynamicValue.Sequence(Chunk.empty)
            )
          )
        )
        val roundTrip   = DynamicSchema.fromDynamicValue(primitiveWithMalformedTypeId)
        val paramsEmpty = roundTrip.typeId.typeParams.isEmpty
        assertTrue(paramsEmpty)
      },
      test("malformed modifiers (non-Sequence) falls back to empty") {
        val primitiveDv = DynamicValue.Variant(
          "Primitive",
          DynamicValue.Record(
            Chunk(
              "typeId" -> DynamicValue.Record(
                Chunk(
                  "namespace" -> DynamicValue.Record(
                    Chunk(
                      "packages" -> DynamicValue.Sequence(Chunk.empty),
                      "values"   -> DynamicValue.Sequence(Chunk.empty)
                    )
                  ),
                  "name"   -> DynamicValue.Primitive(PrimitiveValue.String("Test")),
                  "params" -> DynamicValue.Sequence(Chunk.empty)
                )
              ),
              "doc"           -> DynamicValue.Variant("Empty", DynamicValue.Record(Chunk.empty)),
              "modifiers"     -> DynamicValue.Primitive(PrimitiveValue.String("not a sequence")),
              "primitiveType" -> DynamicValue.Variant("Int", DynamicValue.Record(Chunk.empty)),
              "defaultValue"  -> DynamicValue.Null,
              "examples"      -> DynamicValue.Sequence(Chunk.empty)
            )
          )
        )
        val roundTrip = DynamicSchema.fromDynamicValue(primitiveDv)
        assertTrue(roundTrip.modifiers.isEmpty)
      },
      test("malformed examples (non-Sequence) falls back to empty") {
        val primitiveDv = DynamicValue.Variant(
          "Primitive",
          DynamicValue.Record(
            Chunk(
              "typeId" -> DynamicValue.Record(
                Chunk(
                  "namespace" -> DynamicValue.Record(
                    Chunk(
                      "packages" -> DynamicValue.Sequence(Chunk.empty),
                      "values"   -> DynamicValue.Sequence(Chunk.empty)
                    )
                  ),
                  "name"   -> DynamicValue.Primitive(PrimitiveValue.String("Test")),
                  "params" -> DynamicValue.Sequence(Chunk.empty)
                )
              ),
              "doc"           -> DynamicValue.Variant("Empty", DynamicValue.Record(Chunk.empty)),
              "modifiers"     -> DynamicValue.Sequence(Chunk.empty),
              "primitiveType" -> DynamicValue.Variant("Int", DynamicValue.Record(Chunk.empty)),
              "defaultValue"  -> DynamicValue.Null,
              "examples"      -> DynamicValue.Primitive(PrimitiveValue.String("not a sequence"))
            )
          )
        )
        val roundTrip       = DynamicSchema.fromDynamicValue(primitiveDv)
        val hasEmptyExample = roundTrip.reflect.asPrimitive.exists(_.storedExamples.isEmpty)
        assertTrue(hasEmptyExample)
      },
      test("unknown PrimitiveType falls back to String") {
        val primitiveDv = DynamicValue.Variant(
          "Primitive",
          DynamicValue.Record(
            Chunk(
              "typeId" -> DynamicValue.Record(
                Chunk(
                  "namespace" -> DynamicValue.Record(
                    Chunk(
                      "packages" -> DynamicValue.Sequence(Chunk.empty),
                      "values"   -> DynamicValue.Sequence(Chunk.empty)
                    )
                  ),
                  "name"   -> DynamicValue.Primitive(PrimitiveValue.String("Test")),
                  "params" -> DynamicValue.Sequence(Chunk.empty)
                )
              ),
              "doc"           -> DynamicValue.Variant("Empty", DynamicValue.Record(Chunk.empty)),
              "modifiers"     -> DynamicValue.Sequence(Chunk.empty),
              "primitiveType" -> DynamicValue.Variant("UnknownType", DynamicValue.Record(Chunk.empty)),
              "defaultValue"  -> DynamicValue.Null,
              "examples"      -> DynamicValue.Sequence(Chunk.empty)
            )
          )
        )
        val roundTrip  = DynamicSchema.fromDynamicValue(primitiveDv)
        val isStringPT = roundTrip.reflect.asPrimitive.exists(p => p.primitiveType.isInstanceOf[PrimitiveType.String])
        assertTrue(isStringPT)
      },
      test("non-Variant primitiveType falls back to String") {
        val primitiveDv = DynamicValue.Variant(
          "Primitive",
          DynamicValue.Record(
            Chunk(
              "typeId" -> DynamicValue.Record(
                Chunk(
                  "namespace" -> DynamicValue.Record(
                    Chunk(
                      "packages" -> DynamicValue.Sequence(Chunk.empty),
                      "values"   -> DynamicValue.Sequence(Chunk.empty)
                    )
                  ),
                  "name"   -> DynamicValue.Primitive(PrimitiveValue.String("Test")),
                  "params" -> DynamicValue.Sequence(Chunk.empty)
                )
              ),
              "doc"           -> DynamicValue.Variant("Empty", DynamicValue.Record(Chunk.empty)),
              "modifiers"     -> DynamicValue.Sequence(Chunk.empty),
              "primitiveType" -> DynamicValue.Primitive(PrimitiveValue.String("not a variant")),
              "defaultValue"  -> DynamicValue.Null,
              "examples"      -> DynamicValue.Sequence(Chunk.empty)
            )
          )
        )
        val roundTrip  = DynamicSchema.fromDynamicValue(primitiveDv)
        val isStringPT = roundTrip.reflect.asPrimitive.exists(p => p.primitiveType.isInstanceOf[PrimitiveType.String])
        assertTrue(isStringPT)
      },
      test("malformed Term (non-Record) falls back") {
        val recordDv = DynamicValue.Variant(
          "Record",
          DynamicValue.Record(
            Chunk(
              "typeId" -> DynamicValue.Record(
                Chunk(
                  "namespace" -> DynamicValue.Record(
                    Chunk(
                      "packages" -> DynamicValue.Sequence(Chunk.empty),
                      "values"   -> DynamicValue.Sequence(Chunk.empty)
                    )
                  ),
                  "name"   -> DynamicValue.Primitive(PrimitiveValue.String("Test")),
                  "params" -> DynamicValue.Sequence(Chunk.empty)
                )
              ),
              "doc"          -> DynamicValue.Variant("Empty", DynamicValue.Record(Chunk.empty)),
              "modifiers"    -> DynamicValue.Sequence(Chunk.empty),
              "fields"       -> DynamicValue.Sequence(Chunk(DynamicValue.Primitive(PrimitiveValue.String("bad term")))),
              "defaultValue" -> DynamicValue.Null,
              "examples"     -> DynamicValue.Sequence(Chunk.empty)
            )
          )
        )
        val roundTrip   = DynamicSchema.fromDynamicValue(recordDv)
        val hasOneField = roundTrip.reflect.asRecord.exists(_.fields.length == 1)
        assertTrue(hasOneField)
      },
      test("Term with non-String name falls back to unknown") {
        val termDv = DynamicValue.Record(
          Chunk(
            "name"      -> DynamicValue.Primitive(PrimitiveValue.Int(42)),
            "value"     -> DynamicValue.Variant("Dynamic", DynamicValue.Record(Chunk.empty)),
            "doc"       -> DynamicValue.Variant("Empty", DynamicValue.Record(Chunk.empty)),
            "modifiers" -> DynamicValue.Sequence(Chunk.empty)
          )
        )
        val recordDv = DynamicValue.Variant(
          "Record",
          DynamicValue.Record(
            Chunk(
              "typeId" -> DynamicValue.Record(
                Chunk(
                  "namespace" -> DynamicValue.Record(
                    Chunk(
                      "packages" -> DynamicValue.Sequence(Chunk.empty),
                      "values"   -> DynamicValue.Sequence(Chunk.empty)
                    )
                  ),
                  "name"   -> DynamicValue.Primitive(PrimitiveValue.String("Test")),
                  "params" -> DynamicValue.Sequence(Chunk.empty)
                )
              ),
              "doc"          -> DynamicValue.Variant("Empty", DynamicValue.Record(Chunk.empty)),
              "modifiers"    -> DynamicValue.Sequence(Chunk.empty),
              "fields"       -> DynamicValue.Sequence(Chunk(termDv)),
              "defaultValue" -> DynamicValue.Null,
              "examples"     -> DynamicValue.Sequence(Chunk.empty)
            )
          )
        )
        val roundTrip    = DynamicSchema.fromDynamicValue(recordDv)
        val hasUnknownNm = roundTrip.reflect.asRecord.exists(_.fields.head.name == "unknown")
        assertTrue(hasUnknownNm)
      },
      test("unknown Term modifier is skipped") {
        val termDv = DynamicValue.Record(
          Chunk(
            "name"      -> DynamicValue.Primitive(PrimitiveValue.String("field")),
            "value"     -> DynamicValue.Variant("Dynamic", DynamicValue.Record(Chunk.empty)),
            "doc"       -> DynamicValue.Variant("Empty", DynamicValue.Record(Chunk.empty)),
            "modifiers" -> DynamicValue.Sequence(
              Chunk(DynamicValue.Variant("unknownModifier", DynamicValue.Record(Chunk.empty)))
            )
          )
        )
        val recordDv = DynamicValue.Variant(
          "Record",
          DynamicValue.Record(
            Chunk(
              "typeId" -> DynamicValue.Record(
                Chunk(
                  "namespace" -> DynamicValue.Record(
                    Chunk(
                      "packages" -> DynamicValue.Sequence(Chunk.empty),
                      "values"   -> DynamicValue.Sequence(Chunk.empty)
                    )
                  ),
                  "name"   -> DynamicValue.Primitive(PrimitiveValue.String("Test")),
                  "params" -> DynamicValue.Sequence(Chunk.empty)
                )
              ),
              "doc"          -> DynamicValue.Variant("Empty", DynamicValue.Record(Chunk.empty)),
              "modifiers"    -> DynamicValue.Sequence(Chunk.empty),
              "fields"       -> DynamicValue.Sequence(Chunk(termDv)),
              "defaultValue" -> DynamicValue.Null,
              "examples"     -> DynamicValue.Sequence(Chunk.empty)
            )
          )
        )
        val roundTrip   = DynamicSchema.fromDynamicValue(recordDv)
        val hasNoModifs = roundTrip.reflect.asRecord.exists(_.fields.head.modifiers.isEmpty)
        assertTrue(hasNoModifs)
      },
      test("unknown Reflect modifier is skipped") {
        val primitiveDv = DynamicValue.Variant(
          "Primitive",
          DynamicValue.Record(
            Chunk(
              "typeId" -> DynamicValue.Record(
                Chunk(
                  "namespace" -> DynamicValue.Record(
                    Chunk(
                      "packages" -> DynamicValue.Sequence(Chunk.empty),
                      "values"   -> DynamicValue.Sequence(Chunk.empty)
                    )
                  ),
                  "name"   -> DynamicValue.Primitive(PrimitiveValue.String("Test")),
                  "params" -> DynamicValue.Sequence(Chunk.empty)
                )
              ),
              "doc"       -> DynamicValue.Variant("Empty", DynamicValue.Record(Chunk.empty)),
              "modifiers" -> DynamicValue.Sequence(
                Chunk(DynamicValue.Variant("unknownModifier", DynamicValue.Record(Chunk.empty)))
              ),
              "primitiveType" -> DynamicValue.Variant("Int", DynamicValue.Record(Chunk.empty)),
              "defaultValue"  -> DynamicValue.Null,
              "examples"      -> DynamicValue.Sequence(Chunk.empty)
            )
          )
        )
        val roundTrip = DynamicSchema.fromDynamicValue(primitiveDv)
        assertTrue(roundTrip.modifiers.isEmpty)
      },
      test("malformed TypeId in old format falls back to Unknown with empty owner") {
        val malformedTypeId = DynamicValue.Record(
          Chunk(
            "namespace" -> DynamicValue.Record(
              Chunk(
                "packages" -> DynamicValue.Sequence(
                  Chunk(
                    DynamicValue.Primitive(PrimitiveValue.String("valid")),
                    DynamicValue.Primitive(PrimitiveValue.Int(42))
                  )
                ),
                "values" -> DynamicValue.Sequence(Chunk.empty)
              )
            ),
            "name"   -> DynamicValue.Primitive(PrimitiveValue.String("Test")),
            "params" -> DynamicValue.Sequence(Chunk.empty)
          )
        )
        val primitiveDv = DynamicValue.Variant(
          "Primitive",
          DynamicValue.Record(
            Chunk(
              "typeId"        -> malformedTypeId,
              "doc"           -> DynamicValue.Variant("Empty", DynamicValue.Record(Chunk.empty)),
              "modifiers"     -> DynamicValue.Sequence(Chunk.empty),
              "primitiveType" -> DynamicValue.Variant("Int", DynamicValue.Record(Chunk.empty)),
              "defaultValue"  -> DynamicValue.Null,
              "examples"      -> DynamicValue.Sequence(Chunk.empty)
            )
          )
        )
        val roundTrip   = DynamicSchema.fromDynamicValue(primitiveDv)
        val typeIdName  = roundTrip.typeId.name
        val ownerIsRoot = roundTrip.typeId.owner.isRoot
        assertTrue(typeIdName == "Unknown") && assertTrue(ownerIsRoot)
      },
      test("malformed TypeId without proper fields falls back to Unknown") {
        val malformedTypeId = DynamicValue.Record(
          Chunk(
            "namespace" -> DynamicValue.Record(
              Chunk(
                "packages" -> DynamicValue.Primitive(PrimitiveValue.String("not a sequence")),
                "values"   -> DynamicValue.Sequence(Chunk.empty)
              )
            ),
            "name"   -> DynamicValue.Primitive(PrimitiveValue.String("Test")),
            "params" -> DynamicValue.Sequence(Chunk.empty)
          )
        )
        val primitiveDv = DynamicValue.Variant(
          "Primitive",
          DynamicValue.Record(
            Chunk(
              "typeId"        -> malformedTypeId,
              "doc"           -> DynamicValue.Variant("Empty", DynamicValue.Record(Chunk.empty)),
              "modifiers"     -> DynamicValue.Sequence(Chunk.empty),
              "primitiveType" -> DynamicValue.Variant("Int", DynamicValue.Record(Chunk.empty)),
              "defaultValue"  -> DynamicValue.Null,
              "examples"      -> DynamicValue.Sequence(Chunk.empty)
            )
          )
        )
        val roundTrip   = DynamicSchema.fromDynamicValue(primitiveDv)
        val typeIdName  = roundTrip.typeId.name
        val ownerIsRoot = roundTrip.typeId.owner.isRoot
        assertTrue(typeIdName == "Unknown") && assertTrue(ownerIsRoot)
      },
      test("Doc.Text with missing value falls back to Empty") {
        val primitiveDv = DynamicValue.Variant(
          "Primitive",
          DynamicValue.Record(
            Chunk(
              "typeId" -> DynamicValue.Record(
                Chunk(
                  "namespace" -> DynamicValue.Record(
                    Chunk(
                      "packages" -> DynamicValue.Sequence(Chunk.empty),
                      "values"   -> DynamicValue.Sequence(Chunk.empty)
                    )
                  ),
                  "name"   -> DynamicValue.Primitive(PrimitiveValue.String("Test")),
                  "params" -> DynamicValue.Sequence(Chunk.empty)
                )
              ),
              "doc"           -> DynamicValue.Variant("Text", DynamicValue.Record(Chunk.empty)),
              "modifiers"     -> DynamicValue.Sequence(Chunk.empty),
              "primitiveType" -> DynamicValue.Variant("Int", DynamicValue.Record(Chunk.empty)),
              "defaultValue"  -> DynamicValue.Null,
              "examples"      -> DynamicValue.Sequence(Chunk.empty)
            )
          )
        )
        val roundTrip = DynamicSchema.fromDynamicValue(primitiveDv)
        assertTrue(roundTrip.doc == Doc.Empty)
      },
      test("Doc.Concat with missing flatten falls back to Empty") {
        val primitiveDv = DynamicValue.Variant(
          "Primitive",
          DynamicValue.Record(
            Chunk(
              "typeId" -> DynamicValue.Record(
                Chunk(
                  "namespace" -> DynamicValue.Record(
                    Chunk(
                      "packages" -> DynamicValue.Sequence(Chunk.empty),
                      "values"   -> DynamicValue.Sequence(Chunk.empty)
                    )
                  ),
                  "name"   -> DynamicValue.Primitive(PrimitiveValue.String("Test")),
                  "params" -> DynamicValue.Sequence(Chunk.empty)
                )
              ),
              "doc"           -> DynamicValue.Variant("Concat", DynamicValue.Record(Chunk.empty)),
              "modifiers"     -> DynamicValue.Sequence(Chunk.empty),
              "primitiveType" -> DynamicValue.Variant("Int", DynamicValue.Record(Chunk.empty)),
              "defaultValue"  -> DynamicValue.Null,
              "examples"      -> DynamicValue.Sequence(Chunk.empty)
            )
          )
        )
        val roundTrip = DynamicSchema.fromDynamicValue(primitiveDv)
        assertTrue(roundTrip.doc == Doc.Empty)
      },
      test("Term modifiers with non-Sequence falls back to empty") {
        val termDv = DynamicValue.Record(
          Chunk(
            "name"      -> DynamicValue.Primitive(PrimitiveValue.String("field")),
            "value"     -> DynamicValue.Variant("Dynamic", DynamicValue.Record(Chunk.empty)),
            "doc"       -> DynamicValue.Variant("Empty", DynamicValue.Record(Chunk.empty)),
            "modifiers" -> DynamicValue.Primitive(PrimitiveValue.String("not a sequence"))
          )
        )
        val recordDv = DynamicValue.Variant(
          "Record",
          DynamicValue.Record(
            Chunk(
              "typeId" -> DynamicValue.Record(
                Chunk(
                  "namespace" -> DynamicValue.Record(
                    Chunk(
                      "packages" -> DynamicValue.Sequence(Chunk.empty),
                      "values"   -> DynamicValue.Sequence(Chunk.empty)
                    )
                  ),
                  "name"   -> DynamicValue.Primitive(PrimitiveValue.String("Test")),
                  "params" -> DynamicValue.Sequence(Chunk.empty)
                )
              ),
              "doc"          -> DynamicValue.Variant("Empty", DynamicValue.Record(Chunk.empty)),
              "modifiers"    -> DynamicValue.Sequence(Chunk.empty),
              "fields"       -> DynamicValue.Sequence(Chunk(termDv)),
              "defaultValue" -> DynamicValue.Null,
              "examples"     -> DynamicValue.Sequence(Chunk.empty)
            )
          )
        )
        val roundTrip   = DynamicSchema.fromDynamicValue(recordDv)
        val hasNoModifs = roundTrip.reflect.asRecord.exists(_.fields.head.modifiers.isEmpty)
        assertTrue(hasNoModifs)
      },
      test("unknown Validation falls back to None") {
        val primitiveDv = DynamicValue.Variant(
          "Primitive",
          DynamicValue.Record(
            Chunk(
              "typeId" -> DynamicValue.Record(
                Chunk(
                  "namespace" -> DynamicValue.Record(
                    Chunk(
                      "packages" -> DynamicValue.Sequence(Chunk.empty),
                      "values"   -> DynamicValue.Sequence(Chunk.empty)
                    )
                  ),
                  "name"   -> DynamicValue.Primitive(PrimitiveValue.String("Test")),
                  "params" -> DynamicValue.Sequence(Chunk.empty)
                )
              ),
              "doc"           -> DynamicValue.Variant("Empty", DynamicValue.Record(Chunk.empty)),
              "modifiers"     -> DynamicValue.Sequence(Chunk.empty),
              "primitiveType" -> DynamicValue.Variant(
                "Int",
                DynamicValue.Record(
                  Chunk(
                    "validation" -> DynamicValue.Variant("UnknownValidation", DynamicValue.Record(Chunk.empty))
                  )
                )
              ),
              "defaultValue" -> DynamicValue.Null,
              "examples"     -> DynamicValue.Sequence(Chunk.empty)
            )
          )
        )
        val roundTrip     = DynamicSchema.fromDynamicValue(primitiveDv)
        val hasNoneValida = roundTrip.reflect.asPrimitive.exists(p =>
          p.primitiveType.asInstanceOf[PrimitiveType.Int].validation == Validation.None
        )
        assertTrue(hasNoneValida)
      },
      test("Record with non-Sequence fields falls back to empty fields") {
        val recordDv = DynamicValue.Variant(
          "Record",
          DynamicValue.Record(
            Chunk(
              "typeId" -> DynamicValue.Record(
                Chunk(
                  "namespace" -> DynamicValue.Record(
                    Chunk(
                      "packages" -> DynamicValue.Sequence(Chunk.empty),
                      "values"   -> DynamicValue.Sequence(Chunk.empty)
                    )
                  ),
                  "name"   -> DynamicValue.Primitive(PrimitiveValue.String("Test")),
                  "params" -> DynamicValue.Sequence(Chunk.empty)
                )
              ),
              "doc"          -> DynamicValue.Variant("Empty", DynamicValue.Record(Chunk.empty)),
              "modifiers"    -> DynamicValue.Sequence(Chunk.empty),
              "fields"       -> DynamicValue.Primitive(PrimitiveValue.String("not a sequence")),
              "defaultValue" -> DynamicValue.Null,
              "examples"     -> DynamicValue.Sequence(Chunk.empty)
            )
          )
        )
        val roundTrip   = DynamicSchema.fromDynamicValue(recordDv)
        val hasNoFields = roundTrip.reflect.asRecord.exists(_.fields.isEmpty)
        assertTrue(hasNoFields)
      },
      test("Variant with non-Sequence cases falls back to empty cases") {
        val variantDv = DynamicValue.Variant(
          "Variant",
          DynamicValue.Record(
            Chunk(
              "typeId" -> DynamicValue.Record(
                Chunk(
                  "namespace" -> DynamicValue.Record(
                    Chunk(
                      "packages" -> DynamicValue.Sequence(Chunk.empty),
                      "values"   -> DynamicValue.Sequence(Chunk.empty)
                    )
                  ),
                  "name"   -> DynamicValue.Primitive(PrimitiveValue.String("Test")),
                  "params" -> DynamicValue.Sequence(Chunk.empty)
                )
              ),
              "doc"          -> DynamicValue.Variant("Empty", DynamicValue.Record(Chunk.empty)),
              "modifiers"    -> DynamicValue.Sequence(Chunk.empty),
              "cases"        -> DynamicValue.Primitive(PrimitiveValue.String("not a sequence")),
              "defaultValue" -> DynamicValue.Null,
              "examples"     -> DynamicValue.Sequence(Chunk.empty)
            )
          )
        )
        val roundTrip  = DynamicSchema.fromDynamicValue(variantDv)
        val hasNoCases = roundTrip.reflect.asVariant.exists(_.cases.isEmpty)
        assertTrue(hasNoCases)
      }
    ),
    suite("Coverage - Validation String.Length and Pattern deserialization")(
      test("String.Length validation deserializes correctly") {
        val schemaWithValidation = Schema[String].reflect.asPrimitive
          .map(p =>
            new Schema(
              new Reflect.Primitive(
                PrimitiveType.String(Validation.String.Length(Some(5), Some(10))),
                p.typeId,
                p.primitiveBinding
              )
            )
          )
          .get
        val original  = schemaWithValidation.toDynamicSchema
        val dv        = DynamicSchema.toDynamicValue(original)
        val roundTrip = DynamicSchema.fromDynamicValue(dv)
        assertTrue(roundTrip.check(DynamicValue.Primitive(PrimitiveValue.String("hello"))).isEmpty) &&
        assertTrue(roundTrip.check(DynamicValue.Primitive(PrimitiveValue.String("hi"))).isDefined)
      },
      test("String.Pattern validation deserializes correctly") {
        val schemaWithValidation = Schema[String].reflect.asPrimitive
          .map(p =>
            new Schema(
              new Reflect.Primitive(
                PrimitiveType.String(Validation.String.Pattern("^[a-z]+$")),
                p.typeId,
                p.primitiveBinding
              )
            )
          )
          .get
        val original  = schemaWithValidation.toDynamicSchema
        val dv        = DynamicSchema.toDynamicValue(original)
        val roundTrip = DynamicSchema.fromDynamicValue(dv)
        assertTrue(roundTrip.check(DynamicValue.Primitive(PrimitiveValue.String("hello"))).isEmpty) &&
        assertTrue(roundTrip.check(DynamicValue.Primitive(PrimitiveValue.String("Hello123"))).isDefined)
      }
    ),
    suite("Coverage - dvToTermModifiers branches")(
      test("transient modifier on term deserializes correctly") {
        case class WithTransient(@Modifier.transient() ignored: String = "", name: String) {
          locally(ignored + name)
        }
        object WithTransient {
          implicit val schema: Schema[WithTransient] = Schema.derived
        }
        val _           = WithTransient("", "test")
        val original    = WithTransient.schema.toDynamicSchema
        val dv          = DynamicSchema.toDynamicValue(original)
        val roundTrip   = DynamicSchema.fromDynamicValue(dv)
        val typeIdMatch = roundTrip.typeId.name == "WithTransient"
        assertTrue(typeIdMatch)
      },
      test("rename modifier on term deserializes correctly") {
        case class WithRename(@Modifier.rename("full_name") name: String) {
          locally(name)
        }
        object WithRename {
          implicit val schema: Schema[WithRename] = Schema.derived
        }
        val _           = WithRename("test")
        val original    = WithRename.schema.toDynamicSchema
        val dv          = DynamicSchema.toDynamicValue(original)
        val roundTrip   = DynamicSchema.fromDynamicValue(dv)
        val typeIdMatch = roundTrip.typeId.name == "WithRename"
        assertTrue(typeIdMatch)
      },
      test("alias modifier on term deserializes correctly") {
        case class WithAlias(@Modifier.alias("n") name: String) {
          locally(name)
        }
        object WithAlias {
          implicit val schema: Schema[WithAlias] = Schema.derived
        }
        val _           = WithAlias("test")
        val original    = WithAlias.schema.toDynamicSchema
        val dv          = DynamicSchema.toDynamicValue(original)
        val roundTrip   = DynamicSchema.fromDynamicValue(dv)
        val typeIdMatch = roundTrip.typeId.name == "WithAlias"
        assertTrue(typeIdMatch)
      },
      test("config modifier on term deserializes correctly") {
        case class WithConfig(@Modifier.config("key", "value") name: String) {
          locally(name)
        }
        object WithConfig {
          implicit val schema: Schema[WithConfig] = Schema.derived
        }
        val _           = WithConfig("test")
        val original    = WithConfig.schema.toDynamicSchema
        val dv          = DynamicSchema.toDynamicValue(original)
        val roundTrip   = DynamicSchema.fromDynamicValue(dv)
        val typeIdMatch = roundTrip.typeId.name == "WithConfig"
        assertTrue(typeIdMatch)
      }
    )
  )
}
