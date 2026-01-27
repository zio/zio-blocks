package zio.blocks.schema.binding

import zio.blocks.schema._
import zio.test._

object HasBindingSpec extends SchemaBaseSpec {

  // Helper types for testing
  case class Person(name: String, age: Int)
  object Person {
    implicit val schema: Schema[Person] = Schema.derived
  }

  sealed trait Animal
  case class Dog(name: String) extends Animal
  case class Cat(name: String) extends Animal
  object Animal {
    implicit val schema: Schema[Animal] = Schema.derived
  }

  case class PositiveInt(value: Int)
  object PositiveInt {
    implicit val schema: Schema[PositiveInt] =
      Schema[Int]
        .transformOrFail[PositiveInt](
          i => if (i > 0) Right(PositiveInt(i)) else Left(SchemaError.validationFailed("Expected positive value")),
          _.value
        )
        .withTypeName[PositiveInt]
  }

  def spec: Spec[Any, Any] = suite("HasBindingSpec")(
    suite("Reflect type predicates")(
      test("isPrimitive correctly identifies primitives") {
        assertTrue(
          Schema[Int].reflect.isPrimitive,
          Schema[String].reflect.isPrimitive,
          Schema[Boolean].reflect.isPrimitive,
          Schema[Double].reflect.isPrimitive,
          Schema[Long].reflect.isPrimitive,
          Schema[Float].reflect.isPrimitive,
          Schema[Char].reflect.isPrimitive,
          Schema[Byte].reflect.isPrimitive,
          Schema[Short].reflect.isPrimitive,
          Schema[BigInt].reflect.isPrimitive,
          Schema[BigDecimal].reflect.isPrimitive,
          Schema[Unit].reflect.isPrimitive
        )
      },
      test("isRecord correctly identifies records") {
        assertTrue(
          Schema[Person].reflect.isRecord,
          !Schema[Int].reflect.isRecord,
          !Schema[Animal].reflect.isRecord
        )
      },
      test("isVariant correctly identifies variants") {
        assertTrue(
          Schema[Animal].reflect.isVariant,
          !Schema[Int].reflect.isVariant,
          !Schema[Person].reflect.isVariant
        )
      },
      test("isSequence correctly identifies sequences") {
        assertTrue(
          Schema[List[Int]].reflect.isSequence,
          Schema[Vector[String]].reflect.isSequence,
          Schema[Set[Double]].reflect.isSequence,
          !Schema[Int].reflect.isSequence,
          !Schema[Map[String, Int]].reflect.isSequence
        )
      },
      test("isMap correctly identifies maps") {
        assertTrue(
          Schema[Map[String, Int]].reflect.isMap,
          !Schema[Int].reflect.isMap,
          !Schema[List[Int]].reflect.isMap
        )
      },
      test("isWrapper correctly identifies wrappers") {
        assertTrue(
          Schema[PositiveInt].reflect.isWrapper,
          !Schema[Int].reflect.isWrapper,
          !Schema[Person].reflect.isWrapper
        )
      }
    ),
    suite("Reflect converters return correct types")(
      test("asRecord returns Some for record types") {
        assertTrue(
          Schema[Person].reflect.asRecord.isDefined,
          Schema[Int].reflect.asRecord.isEmpty
        )
      },
      test("asVariant returns Some for variant types") {
        assertTrue(
          Schema[Animal].reflect.asVariant.isDefined,
          Schema[Int].reflect.asVariant.isEmpty
        )
      },
      test("asSequenceUnknown returns Some for sequence types") {
        assertTrue(
          Schema[List[Int]].reflect.asSequenceUnknown.isDefined,
          Schema[Vector[String]].reflect.asSequenceUnknown.isDefined,
          Schema[Int].reflect.asSequenceUnknown.isEmpty
        )
      },
      test("asMapUnknown returns Some for map types") {
        assertTrue(
          Schema[Map[String, Int]].reflect.asMapUnknown.isDefined,
          Schema[Int].reflect.asMapUnknown.isEmpty
        )
      },
      test("asWrapperUnknown returns Some for wrapper types") {
        assertTrue(
          Schema[PositiveInt].reflect.asWrapperUnknown.isDefined,
          Schema[Int].reflect.asWrapperUnknown.isEmpty
        )
      },
      test("asPrimitive returns Some for primitive types") {
        assertTrue(
          Schema[Int].reflect.asPrimitive.isDefined,
          Schema[String].reflect.asPrimitive.isDefined,
          Schema[Boolean].reflect.asPrimitive.isDefined,
          Schema[Person].reflect.asPrimitive.isEmpty
        )
      }
    ),
    suite("Binding accessors return non-null bindings")(
      test("recordBinding returns Binding.Record for record types") {
        val binding = Schema[Person].reflect.asRecord.get.recordBinding
        assertTrue(binding.isInstanceOf[Binding.Record[_]])
      },
      test("variantBinding returns Binding.Variant for variant types") {
        val binding = Schema[Animal].reflect.asVariant.get.variantBinding
        assertTrue(binding.isInstanceOf[Binding.Variant[_]])
      },
      test("seqBinding returns Binding.Seq for sequence types") {
        val binding = Schema[List[Int]].reflect.asSequenceUnknown.get.sequence.seqBinding
        assertTrue(binding.isInstanceOf[Binding.Seq[_, _]])
      },
      test("mapBinding returns Binding.Map for map types") {
        val binding = Schema[Map[String, Int]].reflect.asMapUnknown.get.map.mapBinding
        assertTrue(binding.isInstanceOf[Binding.Map[_, _, _]])
      },
      test("wrapperBinding returns Binding.Wrapper for wrapper types") {
        val binding = Schema[PositiveInt].reflect.asWrapperUnknown.get.wrapper.wrapperBinding
        assertTrue(binding.isInstanceOf[Binding.Wrapper[_, _]])
      }
    ),
    suite("Option and Either special cases")(
      test("Option[T] is a variant") {
        assertTrue(Schema[Option[Int]].reflect.isVariant)
      },
      test("Either[A, B] is a variant") {
        assertTrue(Schema[Either[String, Int]].reflect.isVariant)
      }
    ),
    suite("Primitive Binding static instances")(
      test("Binding.Primitive has unit instance") {
        assertTrue(Binding.Primitive.unit != null)
      },
      test("Binding.Primitive has boolean instance") {
        assertTrue(Binding.Primitive.boolean != null)
      },
      test("Binding.Primitive has byte instance") {
        assertTrue(Binding.Primitive.byte != null)
      },
      test("Binding.Primitive has short instance") {
        assertTrue(Binding.Primitive.short != null)
      },
      test("Binding.Primitive has int instance") {
        assertTrue(Binding.Primitive.int != null)
      },
      test("Binding.Primitive has long instance") {
        assertTrue(Binding.Primitive.long != null)
      },
      test("Binding.Primitive has float instance") {
        assertTrue(Binding.Primitive.float != null)
      },
      test("Binding.Primitive has double instance") {
        assertTrue(Binding.Primitive.double != null)
      },
      test("Binding.Primitive has char instance") {
        assertTrue(Binding.Primitive.char != null)
      },
      test("Binding.Primitive has string instance") {
        assertTrue(Binding.Primitive.string != null)
      },
      test("Binding.Primitive has bigInt instance") {
        assertTrue(Binding.Primitive.bigInt != null)
      },
      test("Binding.Primitive has bigDecimal instance") {
        assertTrue(Binding.Primitive.bigDecimal != null)
      }
    ),
    suite("Seq Binding static instances")(
      test("Binding.Seq has set constructor") {
        assertTrue(Binding.Seq.set[Int] != null)
      },
      test("Binding.Seq has list constructor") {
        assertTrue(Binding.Seq.list[Int] != null)
      },
      test("Binding.Seq has vector constructor") {
        assertTrue(Binding.Seq.vector[Int] != null)
      },
      test("Binding.Seq has indexedSeq constructor") {
        assertTrue(Binding.Seq.indexedSeq[Int] != null)
      },
      test("Binding.Seq has seq constructor") {
        assertTrue(Binding.Seq.seq[Int] != null)
      },
      test("Binding.Seq has chunk constructor") {
        assertTrue(Binding.Seq.chunk[Int] != null)
      }
    ),
    suite("Record Binding static instances")(
      test("Binding.Record has none instance") {
        assertTrue(Binding.Record.none != null)
      },
      test("Binding.Record has some instance") {
        assertTrue(Binding.Record.some[String] != null)
      },
      test("Binding.Record has left instance") {
        assertTrue(Binding.Record.left[String, Int] != null)
      },
      test("Binding.Record has right instance") {
        assertTrue(Binding.Record.right[String, Int] != null)
      }
    ),
    suite("Variant Binding static instances")(
      test("Binding.Variant has option instance") {
        assertTrue(Binding.Variant.option[Int] != null)
      },
      test("Binding.Variant has either instance") {
        assertTrue(Binding.Variant.either[String, Int] != null)
      }
    ),
    suite("Map Binding static instances")(
      test("Binding.Map has map instance") {
        assertTrue(Binding.Map.map[String, Int] != null)
      }
    )
  )
}
