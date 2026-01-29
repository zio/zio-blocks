package zio.blocks.schema

import zio.blocks.chunk.Chunk
import zio.test._

object DynamicSchemaSpec extends SchemaBaseSpec {
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
        val name = ds.typeName.name
        assertTrue(name == "Int")
      },
      test("record schema converts to DynamicSchema") {
        val ds   = Schema[Person].toDynamicSchema
        val name = ds.typeName.name
        assertTrue(name == "Person")
      },
      test("variant schema converts to DynamicSchema") {
        val ds   = Schema[Color].toDynamicSchema
        val name = ds.typeName.name
        assertTrue(name == "Color")
      },
      test("sequence schema converts to DynamicSchema") {
        val ds   = Schema[List[Int]].toDynamicSchema
        val name = ds.typeName.name
        assertTrue(name == "List")
      },
      test("map schema converts to DynamicSchema") {
        val ds   = Schema[Map[String, Int]].toDynamicSchema
        val name = ds.typeName.name
        assertTrue(name == "Map")
      },
      test("option schema converts to DynamicSchema") {
        val ds   = Schema[Option[Int]].toDynamicSchema
        val name = ds.typeName.name
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
              p.typeName,
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
              p.typeName,
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
              p.typeName,
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
              p.typeName,
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
              p.typeName,
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
              p.typeName,
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
              p.typeName,
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
              p.typeName,
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
              p.typeName,
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
              p.typeName,
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
              p.typeName,
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
              p.typeName,
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
              p.typeName,
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
              p.typeName,
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
              p.typeName,
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
              p.typeName,
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
              p.typeName,
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
              p.typeName,
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
              p.typeName,
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
              p.typeName,
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
              p.typeName,
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
              p.typeName,
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
              p.typeName,
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
              p.typeName,
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
              p.typeName,
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
      test("typeName returns reflect.typeName") {
        val ds   = Schema[Person].toDynamicSchema
        val name = ds.typeName.name
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
          implicit val schema: Schema[PositiveInt] = Schema[Int].transform(PositiveInt(_), _.value)
        }
        val ds = Schema[PositiveInt].toDynamicSchema
        val dv = DynamicValue.Primitive(PrimitiveValue.Int(42))
        assertTrue(ds.check(dv).isEmpty)
      },
      test("wrapper schema rejects wrong type") {
        case class PositiveInt(value: Int)
        object PositiveInt {
          implicit val schema: Schema[PositiveInt] = Schema[Int].transform(PositiveInt(_), _.value)
        }
        val ds = Schema[PositiveInt].toDynamicSchema
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
          implicit val schema: Schema[WrappedInt] = Schema[Int].transform(WrappedInt(_), _.value)
        }
        val ds  = Schema[WrappedInt].toDynamicSchema
        val dv  = DynamicValue.Primitive(PrimitiveValue.Int(42))
        val ds2 = ds.defaultValue(dv)
        assertTrue(ds2.getDefaultValue.contains(dv))
      },
      test("Wrapper examples sets and retrieves") {
        case class WrappedInt(value: Int)
        object WrappedInt {
          implicit val schema: Schema[WrappedInt] = Schema[Int].transform(WrappedInt(_), _.value)
        }
        val ds  = Schema[WrappedInt].toDynamicSchema
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
        val ds = Schema[java.util.UUID].toDynamicSchema
        assertTrue(ds.check(DynamicValue.Primitive(PrimitiveValue.UUID(java.util.UUID.randomUUID()))).isEmpty) &&
        assertTrue(ds.check(DynamicValue.Primitive(PrimitiveValue.Int(1))).isDefined)
      }
    ),
    suite("check - Numeric validation for all types")(
      test("Numeric.Positive for Byte passes for positive") {
        val ds = Schema[Byte].reflect.asPrimitive
          .map(p =>
            new Schema(
              new Reflect.Primitive(PrimitiveType.Byte(Validation.Numeric.Positive), p.typeName, p.primitiveBinding)
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
              new Reflect.Primitive(PrimitiveType.Short(Validation.Numeric.Positive), p.typeName, p.primitiveBinding)
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
              new Reflect.Primitive(PrimitiveType.Long(Validation.Numeric.Positive), p.typeName, p.primitiveBinding)
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
              new Reflect.Primitive(PrimitiveType.Float(Validation.Numeric.Positive), p.typeName, p.primitiveBinding)
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
              new Reflect.Primitive(PrimitiveType.Double(Validation.Numeric.Positive), p.typeName, p.primitiveBinding)
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
              new Reflect.Primitive(PrimitiveType.BigInt(Validation.Numeric.Positive), p.typeName, p.primitiveBinding)
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
                p.typeName,
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
              new Reflect.Primitive(PrimitiveType.Byte(Validation.Numeric.Negative), p.typeName, p.primitiveBinding)
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
              new Reflect.Primitive(PrimitiveType.Short(Validation.Numeric.Negative), p.typeName, p.primitiveBinding)
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
              new Reflect.Primitive(PrimitiveType.Long(Validation.Numeric.Negative), p.typeName, p.primitiveBinding)
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
              new Reflect.Primitive(PrimitiveType.Float(Validation.Numeric.Negative), p.typeName, p.primitiveBinding)
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
              new Reflect.Primitive(PrimitiveType.Double(Validation.Numeric.Negative), p.typeName, p.primitiveBinding)
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
              new Reflect.Primitive(PrimitiveType.BigInt(Validation.Numeric.Negative), p.typeName, p.primitiveBinding)
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
                p.typeName,
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
              new Reflect.Primitive(PrimitiveType.Byte(Validation.Numeric.NonPositive), p.typeName, p.primitiveBinding)
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
              new Reflect.Primitive(PrimitiveType.Short(Validation.Numeric.NonPositive), p.typeName, p.primitiveBinding)
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
              new Reflect.Primitive(PrimitiveType.Long(Validation.Numeric.NonPositive), p.typeName, p.primitiveBinding)
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
              new Reflect.Primitive(PrimitiveType.Float(Validation.Numeric.NonPositive), p.typeName, p.primitiveBinding)
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
                p.typeName,
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
                p.typeName,
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
                p.typeName,
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
              new Reflect.Primitive(PrimitiveType.Byte(Validation.Numeric.NonNegative), p.typeName, p.primitiveBinding)
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
              new Reflect.Primitive(PrimitiveType.Short(Validation.Numeric.NonNegative), p.typeName, p.primitiveBinding)
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
              new Reflect.Primitive(PrimitiveType.Long(Validation.Numeric.NonNegative), p.typeName, p.primitiveBinding)
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
              new Reflect.Primitive(PrimitiveType.Float(Validation.Numeric.NonNegative), p.typeName, p.primitiveBinding)
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
                p.typeName,
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
                p.typeName,
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
                p.typeName,
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
                p.typeName,
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
                p.typeName,
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
                p.typeName,
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
                p.typeName,
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
                p.typeName,
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
                p.typeName,
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
                p.typeName,
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
                p.typeName,
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
                p.typeName,
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
                p.typeName,
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
                p.typeName,
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
                p.typeName,
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
                p.typeName,
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
                p.typeName,
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
                p.typeName,
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
                p.typeName,
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
              new Reflect.Primitive(PrimitiveType.String(Validation.String.Blank), p.typeName, p.primitiveBinding)
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
              new Reflect.Primitive(PrimitiveType.String(Validation.String.NonBlank), p.typeName, p.primitiveBinding)
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
                p.typeName,
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
                p.typeName,
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
        val name      = roundTrip.typeName.name
        assertTrue(name == "Int")
      },
      test("record schema round-trips through DynamicValue") {
        val original  = Schema[Person].toDynamicSchema
        val dv        = DynamicSchema.toDynamicValue(original)
        val roundTrip = DynamicSchema.fromDynamicValue(dv)
        val name      = roundTrip.typeName.name
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
        val name      = roundTrip.typeName.name
        assertTrue(name == "Color")
      },
      test("sequence schema round-trips through DynamicValue") {
        val original  = Schema[List[Int]].toDynamicSchema
        val dv        = DynamicSchema.toDynamicValue(original)
        val roundTrip = DynamicSchema.fromDynamicValue(dv)
        val name      = roundTrip.typeName.name
        assertTrue(name == "List")
      },
      test("map schema round-trips through DynamicValue") {
        val original  = Schema[Map[String, Int]].toDynamicSchema
        val dv        = DynamicSchema.toDynamicValue(original)
        val roundTrip = DynamicSchema.fromDynamicValue(dv)
        val name      = roundTrip.typeName.name
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
        val name      = roundTrip.toOption.get.typeName.name
        assertTrue(isRight) &&
        assertTrue(name == "Person")
      }
    )
  )
}
