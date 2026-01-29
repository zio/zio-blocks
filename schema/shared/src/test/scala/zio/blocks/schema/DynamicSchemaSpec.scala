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
    )
  )
}
