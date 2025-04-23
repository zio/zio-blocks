package zio.blocks.schema

import zio.Scope
import zio.blocks.schema.Reflect.Primitive
import zio.blocks.schema.binding.RegisterOffset.RegisterOffset
import zio.blocks.schema.binding._
import zio.test.Assertion._
import zio.test._

object SchemaSpec extends ZIOSpecDefault {
  def spec: Spec[TestEnvironment with Scope, Any] = suite("SchemaSpec")(
    suite("Reflect.Primitive")(
      test("has consistent equals and hashCode") {
        assert(Schema[Long])(equalTo(Schema[Long])) &&
        assert(Schema[Long].hashCode)(equalTo(Schema[Long].hashCode)) &&
        assert(Schema[Long].examples(1L, 2L, 3L))(equalTo(Schema[Long])) &&
        assert(Schema[Long].examples(1L, 2L, 3L).hashCode)(equalTo(Schema[Long].hashCode)) &&
        assert(Schema[Long].defaultValue(0L))(equalTo(Schema[Long])) &&
        assert(Schema[Long].defaultValue(0L).hashCode)(equalTo(Schema[Long].hashCode)) &&
        assert(Schema[Long].doc("Long"))(not(equalTo(Schema[Long]))) &&
        assert(Schema[Int]: Any)(not(equalTo(Schema[Long]))) &&
        assert(Schema[Double]: Any)(not(equalTo(Schema[Long])))
      },
      test("updates primitive default value") {
        assert(Schema[Int].reflect.binding.defaultValue)(isNone) &&
        assert(Schema[Int].defaultValue(1).reflect.binding.defaultValue.get.apply())(equalTo(1))
      },
      test("has access to primitive documentation") {
        assert(Schema[Long].doc)(equalTo(Doc.Empty))
      },
      test("updates primitive documentation") {
        assert(Schema[Int].doc("Int (updated)").doc)(equalTo(Doc("Int (updated)")))
      },
      test("has access to primitive examples") {
        val long1 = Primitive(
          primitiveType = PrimitiveType.Long(Validation.Numeric.Positive),
          primitiveBinding = Binding.Primitive[Long](examples = Seq(1L, 2L, 3L)),
          typeName = TypeName.long,
          doc = Doc("Long (positive)"),
          modifiers = Nil
        )
        assert(Schema(long1).examples)(equalTo(Seq(1L, 2L, 3L)))
      },
      test("updates primitive examples") {
        assert(Schema[Int].examples(1, 2, 3).examples)(equalTo(Seq(1, 2, 3)))
      }
    ),
    suite("Reflect.Record")(
      test("has consistent equals and hashCode") {
        assert(Record.schema)(equalTo(Record.schema)) &&
        assert(Record.schema.hashCode)(equalTo(Record.schema.hashCode)) &&
        assert(Schema.none)(equalTo(Schema.none)) &&
        assert(Schema.none.hashCode)(equalTo(Schema.none.hashCode)) &&
        assert(Schema[Some[Short]])(equalTo(Schema[Some[Short]])) &&
        assert(Schema[Some[Short]].hashCode)(equalTo(Schema[Some[Short]].hashCode)) &&
        assert(Schema[Option[Short]])(equalTo(Schema[Option[Short]])) &&
        assert(Schema[Option[Short]].hashCode)(equalTo(Schema[Option[Short]].hashCode)) &&
        assert(Schema[Left[Char, Unit]])(equalTo(Schema[Left[Char, Unit]])) &&
        assert(Schema[Left[Char, Unit]].hashCode)(equalTo(Schema[Left[Char, Unit]].hashCode)) &&
        assert(Schema[Right[Char, Unit]])(equalTo(Schema[Right[Char, Unit]])) &&
        assert(Schema[Right[Char, Unit]].hashCode)(equalTo(Schema[Right[Char, Unit]].hashCode)) &&
        assert(Schema[(Int, Int)])(equalTo(Schema[(Int, Int)])) &&
        assert(Schema[(Int, Int)].hashCode)(equalTo(Schema[(Int, Int)].hashCode)) &&
        assert(Schema[(Int, Int, Int)])(equalTo(Schema[(Int, Int, Int)])) &&
        assert(Schema[(Int, Int, Int)].hashCode)(equalTo(Schema[(Int, Int, Int)].hashCode)) &&
        assert(Schema[(Int, Int, Int, Int)])(equalTo(Schema[(Int, Int, Int, Int)])) &&
        assert(Schema[(Int, Int, Int, Int)].hashCode)(equalTo(Schema[(Int, Int, Int, Int)].hashCode)) &&
        assert(Schema[(Int, Int, Int, Int, Int)])(equalTo(Schema[(Int, Int, Int, Int, Int)])) &&
        assert(Schema[(Int, Int, Int, Int, Int)].hashCode)(equalTo(Schema[(Int, Int, Int, Int, Int)].hashCode)) &&
        assert(Schema[(Int, Int, Int, Int, Int, Int)])(equalTo(Schema[(Int, Int, Int, Int, Int, Int)])) &&
        assert(Schema[(Int, Int, Int, Int, Int, Int)].hashCode)(
          equalTo(Schema[(Int, Int, Int, Int, Int, Int)].hashCode)
        ) &&
        assert(Schema[(Int, Int, Int, Int, Int, Int, Int)])(equalTo(Schema[(Int, Int, Int, Int, Int, Int, Int)])) &&
        assert(Schema[(Int, Int, Int, Int, Int, Int, Int)].hashCode)(
          equalTo(Schema[(Int, Int, Int, Int, Int, Int, Int)].hashCode)
        ) &&
        assert(Schema[(Int, Int, Int, Int, Int, Int, Int, Int)])(
          equalTo(Schema[(Int, Int, Int, Int, Int, Int, Int, Int)])
        ) &&
        assert(Schema[(Int, Int, Int, Int, Int, Int, Int, Int)].hashCode)(
          equalTo(Schema[(Int, Int, Int, Int, Int, Int, Int, Int)].hashCode)
        ) &&
        assert(Schema[(Int, Int, Int, Int, Int, Int, Int, Int, Int)])(
          equalTo(Schema[(Int, Int, Int, Int, Int, Int, Int, Int, Int)])
        ) &&
        assert(Schema[(Int, Int, Int, Int, Int, Int, Int, Int, Int)].hashCode)(
          equalTo(Schema[(Int, Int, Int, Int, Int, Int, Int, Int, Int)].hashCode)
        ) &&
        assert(Schema[(Int, Int, Int, Int, Int, Int, Int, Int, Int, Int)])(
          equalTo(Schema[(Int, Int, Int, Int, Int, Int, Int, Int, Int, Int)])
        ) &&
        assert(Schema[(Int, Int, Int, Int, Int, Int, Int, Int, Int, Int)].hashCode)(
          equalTo(Schema[(Int, Int, Int, Int, Int, Int, Int, Int, Int, Int)].hashCode)
        ) &&
        assert(Schema[(Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int)])(
          equalTo(Schema[(Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int)])
        ) &&
        assert(Schema[(Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int)].hashCode)(
          equalTo(Schema[(Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int)].hashCode)
        ) &&
        assert(Schema[(Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int)])(
          equalTo(Schema[(Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int)])
        ) &&
        assert(Schema[(Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int)].hashCode)(
          equalTo(Schema[(Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int)].hashCode)
        ) &&
        assert(Schema[(Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int)])(
          equalTo(Schema[(Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int)])
        ) &&
        assert(Schema[(Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int)].hashCode)(
          equalTo(Schema[(Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int)].hashCode)
        ) &&
        assert(Schema[(Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int)])(
          equalTo(Schema[(Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int)])
        ) &&
        assert(Schema[(Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int)].hashCode)(
          equalTo(Schema[(Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int)].hashCode)
        ) &&
        assert(Schema[(Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int)])(
          equalTo(Schema[(Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int)])
        ) &&
        assert(Schema[(Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int)].hashCode)(
          equalTo(Schema[(Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int)].hashCode)
        ) &&
        assert(Schema[(Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int)])(
          equalTo(Schema[(Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int)])
        ) &&
        assert(Schema[(Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int)].hashCode)(
          equalTo(Schema[(Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int)].hashCode)
        ) &&
        assert(Schema[(Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int)])(
          equalTo(Schema[(Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int)])
        ) &&
        assert(Schema[(Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int)].hashCode)(
          equalTo(
            Schema[(Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int)].hashCode
          )
        ) &&
        assert(Schema[(Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int)])(
          equalTo(Schema[(Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int)])
        ) &&
        assert(
          Schema[(Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int)].hashCode
        )(
          equalTo(
            Schema[(Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int)].hashCode
          )
        ) &&
        assert(Schema[(Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int)])(
          equalTo(
            Schema[(Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int)]
          )
        ) &&
        assert(
          Schema[
            (Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int)
          ].hashCode
        )(
          equalTo(
            Schema[
              (Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int)
            ].hashCode
          )
        ) &&
        assert(
          Schema[(Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int)]
        )(
          equalTo(
            Schema[(Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int)]
          )
        ) &&
        assert(
          Schema[
            (Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int)
          ].hashCode
        )(
          equalTo(
            Schema[
              (Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int)
            ].hashCode
          )
        ) &&
        assert(
          Schema[
            (Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int)
          ]
        )(
          equalTo(
            Schema[
              (Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int)
            ]
          )
        ) &&
        assert(
          Schema[
            (Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int)
          ].hashCode
        )(
          equalTo(
            Schema[
              (Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int)
            ].hashCode
          )
        ) &&
        assert(
          Schema[
            (
              Int,
              Int,
              Int,
              Int,
              Int,
              Int,
              Int,
              Int,
              Int,
              Int,
              Int,
              Int,
              Int,
              Int,
              Int,
              Int,
              Int,
              Int,
              Int,
              Int,
              Int,
              Int
            )
          ]
        )(
          equalTo(
            Schema[
              (
                Int,
                Int,
                Int,
                Int,
                Int,
                Int,
                Int,
                Int,
                Int,
                Int,
                Int,
                Int,
                Int,
                Int,
                Int,
                Int,
                Int,
                Int,
                Int,
                Int,
                Int,
                Int
              )
            ]
          )
        ) &&
        assert(
          Schema[
            (
              Int,
              Int,
              Int,
              Int,
              Int,
              Int,
              Int,
              Int,
              Int,
              Int,
              Int,
              Int,
              Int,
              Int,
              Int,
              Int,
              Int,
              Int,
              Int,
              Int,
              Int,
              Int
            )
          ].hashCode
        )(
          equalTo(
            Schema[
              (
                Int,
                Int,
                Int,
                Int,
                Int,
                Int,
                Int,
                Int,
                Int,
                Int,
                Int,
                Int,
                Int,
                Int,
                Int,
                Int,
                Int,
                Int,
                Int,
                Int,
                Int,
                Int
              )
            ].hashCode
          )
        ) &&
        assert(Record.schema.defaultValue(Record(0, 0)))(equalTo(Record.schema)) &&
        assert(Record.schema.defaultValue(Record(0, 0)).hashCode)(equalTo(Record.schema.hashCode)) &&
        assert(Record.schema.examples(Record(1, 1000)))(equalTo(Record.schema)) &&
        assert(Record.schema.examples(Record(1, 1000)).hashCode)(equalTo(Record.schema.hashCode)) &&
        assert(Record.schema.doc("Record (updated)"))(not(equalTo(Record.schema)))
      },
      test("updates record default value") {
        assert(Record.schema.reflect.binding.defaultValue)(isNone) &&
        assert(Record.schema.defaultValue(Record(1, 2)).reflect.binding.defaultValue.get.apply())(equalTo(Record(1, 2)))
      },
      test("has access to record documentation") {
        assert(Record.schema.doc)(equalTo(Doc("Record with 2 fields")))
      },
      test("has access to record term documentation using lens focus") {
        val record = Record.schema.reflect.asInstanceOf[Reflect.Record[Binding, Record]]
        assert(Record.schema.doc(Lens(record, record.fields(0))): Doc)(equalTo(record.fields(0).value.doc))
      },
      test("updates record documentation") {
        assert(Record.schema.doc("Record (updated)").doc)(equalTo(Doc("Record (updated)")))
      },
      test("has access to record examples") {
        assert(Record.schema.examples)(equalTo(Record(1, 1000) :: Nil))
      },
      test("updates record examples") {
        assert(Record.schema.examples(Record(2, 2000)).examples)(equalTo(Record(2, 2000) :: Nil))
      },
      test("has access to record term examples using lens focus") {
        val record = Record.schema.reflect.asInstanceOf[Reflect.Record[Binding, Record]]
        assert(Record.schema.examples(Lens(record, record.fields(0))): Seq[_])(
          equalTo(record.fields(0).value.binding.examples)
        )
      },
      test("derives schema for record with default values using a macro call") {
        case class `Record-1`(`b-1`: Byte = 1, `i-2`: Int = 2)

        type Record1 = `Record-1`

        val schema = Schema.derived[Record1]
        val fields = schema.reflect.asInstanceOf[Reflect.Record[Binding, Record1]].fields
        assert(fields(0).value.binding.defaultValue.get.apply(): Any)(equalTo(1)) &&
        assert(fields(1).value.binding.defaultValue.get.apply(): Any)(equalTo(2)) &&
        assert(schema)(
          equalTo(
            new Schema[Record1](
              reflect = Reflect.Record[Binding, Record1](
                fields = Seq(
                  Schema[Byte].reflect.asTerm("b-1"),
                  Schema[Int].reflect.asTerm("i-2")
                ),
                typeName = TypeName(
                  namespace = Namespace(
                    packages = Seq("zio", "blocks", "schema"),
                    values = Seq("SchemaSpec", "spec")
                  ),
                  name = "Record-1"
                ),
                recordBinding = null,
                doc = Doc.Empty,
                modifiers = Nil
              )
            )
          )
        )
      },
      test("derives schema for generic record using a macro call") {
        case class `Record-2`[B, I](b: B, i: I)

        type Record2[B, I] = `Record-2`[B, I]
        type `i-8`         = Byte
        type `i-32`        = Int

        assert(Schema.derived[Record2[`i-8`, `i-32`]])(
          equalTo(
            new Schema[Record2[`i-8`, `i-32`]](
              reflect = Reflect.Record[Binding, Record2[`i-8`, `i-32`]](
                fields = Seq(
                  Schema[Byte].reflect.asTerm("b"),
                  Schema[Int].reflect.asTerm("i")
                ),
                typeName = TypeName(
                  namespace = Namespace(
                    packages = Seq("zio", "blocks", "schema"),
                    values = Seq("SchemaSpec", "spec")
                  ),
                  name = "Record-2"
                ),
                recordBinding = null,
                doc = Doc.Empty,
                modifiers = Nil
              )
            )
          )
        )
      },
      test("derives schema for record with multi list constructor using a macro call") {
        class Record3(val b: Byte)(val i: Int)

        assert(Schema.derived[Record3])(
          equalTo(
            new Schema[Record3](
              reflect = Reflect.Record[Binding, Record3](
                fields = Seq(
                  Schema[Byte].reflect.asTerm("b"),
                  Schema[Int].reflect.asTerm("i")
                ),
                typeName = TypeName(
                  namespace = Namespace(
                    packages = Seq("zio", "blocks", "schema"),
                    values = Seq("SchemaSpec", "spec")
                  ),
                  name = "Record3"
                ),
                recordBinding = null,
                doc = Doc.Empty,
                modifiers = Nil
              )
            )
          )
        )
      }
    ),
    suite("Reflect.Variant")(
      test("has consistent equals and hashCode") {
        assert(Variant.schema)(equalTo(Variant.schema)) &&
        assert(Variant.schema.hashCode)(equalTo(Variant.schema.hashCode)) &&
        assert(Schema[Either[Int, Long]])(equalTo(Schema[Either[Int, Long]])) &&
        assert(Schema[Either[Int, Long]].hashCode)(equalTo(Schema[Either[Int, Long]].hashCode)) &&
        assert(Variant.schema.defaultValue(Case1(0.1)))(equalTo(Variant.schema)) &&
        assert(Variant.schema.defaultValue(Case1(0.1)).hashCode)(equalTo(Variant.schema.hashCode)) &&
        assert(Variant.schema.examples(Case1(0.1)))(equalTo(Variant.schema)) &&
        assert(Variant.schema.examples(Case1(0.1)).hashCode)(equalTo(Variant.schema.hashCode)) &&
        assert(Variant.schema.doc("Variant (updated)"))(not(equalTo(Variant.schema)))
      },
      test("updates variant default value") {
        assert(Variant.schema.reflect.binding.defaultValue)(isNone) &&
        assert(Variant.schema.defaultValue(Case1(1.0)).reflect.binding.defaultValue.get.apply())(equalTo(Case1(1.0)))
      },
      test("has access to variant documentation") {
        assert(Variant.schema.doc)(equalTo(Doc("Variant with 2 cases")))
      },
      test("has access to variant case documentation using prism focus") {
        val variant = Variant.schema.reflect.asInstanceOf[Reflect.Variant[Binding, Variant]]
        assert(Variant.schema.doc(Prism(variant, variant.cases(0))): Doc)(equalTo(variant.cases(0).value.doc))
      },
      test("updates variant documentation") {
        assert(Variant.schema.doc("Variant (updated)").doc)(equalTo(Doc("Variant (updated)")))
      },
      test("has access to variant examples") {
        assert(Variant.schema.examples)(equalTo(Case1(1.0) :: Case2("WWW") :: Nil))
      },
      test("updates variant examples") {
        assert(Variant.schema.examples(Case1(2.0), Case2("VVV")).examples)(equalTo(Case1(2.0) :: Case2("VVV") :: Nil))
      },
      test("has access to variant case examples using prism focus") {
        val variant = Variant.schema.reflect.asInstanceOf[Reflect.Variant[Binding, Variant]]
        assert(Variant.schema.examples(Prism(variant, variant.cases(0))): Seq[_])(
          equalTo(variant.cases(0).value.binding.examples)
        )
      }
    ),
    suite("Reflect.Sequence")(
      test("has consistent equals and hashCode") {
        assert(Schema[List[Double]])(equalTo(Schema[List[Double]])) &&
        assert(Schema[List[Double]].hashCode)(equalTo(Schema[List[Double]].hashCode)) &&
        assert(Schema[List[Double]].defaultValue(Nil))(equalTo(Schema[List[Double]])) &&
        assert(Schema[List[Double]].defaultValue(Nil).hashCode)(equalTo(Schema[List[Double]].hashCode)) &&
        assert(Schema[List[Double]].examples(List(0.1)))(equalTo(Schema[List[Double]])) &&
        assert(Schema[List[Double]].defaultValue(Nil).hashCode)(equalTo(Schema[List[Double]].hashCode)) &&
        assert(Schema[List[Double]].doc("List[Double] (updated)"))(not(equalTo(Schema[List[Double]]))) &&
        assert(Schema[List[Int]]: Any)(not(equalTo(Schema[List[Double]]))) &&
        assert(Schema[Vector[Double]]: Any)(not(equalTo(Schema[List[Double]])))
      },
      test("updates sequence default value") {
        assert(Schema[Vector[Int]].reflect.binding.defaultValue)(isNone) &&
        assert(Schema[Vector[Int]].defaultValue(Vector.empty).reflect.binding.defaultValue.get.apply())(
          equalTo(Vector.empty)
        )
      },
      test("has access to sequence documentation") {
        assert(Schema[List[Double]].doc)(equalTo(Doc.Empty))
      },
      test("has access to sequence value documentation using traversal focus") {
        val long1 = Primitive(
          primitiveType = PrimitiveType.Long(Validation.Numeric.Positive),
          primitiveBinding = null.asInstanceOf[Binding.Primitive[Long]],
          typeName = TypeName.long,
          doc = Doc("Long (positive)"),
          modifiers = Nil
        )
        val sequence1 = Reflect.Sequence[Binding, Long, List](
          element = long1,
          typeName = TypeName.list,
          seqBinding = null.asInstanceOf[Binding.Seq[List, Long]],
          doc = Doc("List of positive longs"),
          modifiers = Nil
        )
        assert(Schema(sequence1).doc(Traversal.listValues(long1)): Doc)(equalTo(Doc("Long (positive)")))
      },
      test("updates sequence documentation") {
        assert(Schema[Array[Int]].doc("Array (updated)").doc)(equalTo(Doc("Array (updated)")))
      },
      test("has access to record examples") {
        assert(Schema[List[Double]].examples)(equalTo(Seq.empty))
      },
      test("updates sequence examples") {
        assert(Schema[Set[Int]].examples(Set(1, 2, 3)).examples)(equalTo(Seq(Set(1, 2, 3))))
      },
      test("has access to sequence value examples using traversal focus") {
        val long1 = Primitive(
          primitiveType = PrimitiveType.Long(Validation.Numeric.Positive),
          primitiveBinding = Binding.Primitive[Long](examples = Seq(1L, 2L, 3L)),
          typeName = TypeName.long,
          doc = Doc("Long (positive)"),
          modifiers = Nil
        )
        val sequence1 = Reflect.Sequence[Binding, Long, List](
          element = long1,
          typeName = TypeName.list,
          seqBinding = null.asInstanceOf[Binding.Seq[List, Long]],
          doc = Doc("List of positive longs"),
          modifiers = Nil
        )
        assert(Schema(sequence1).examples(Traversal.listValues(long1)): Seq[_])(equalTo(Seq(1L, 2L, 3L)))
      }
    ),
    suite("Reflect.Map")(
      test("has consistent equals and hashCode") {
        assert(Schema[Map[Short, Float]])(equalTo(Schema[Map[Short, Float]])) &&
        assert(Schema[Map[Short, Float]].hashCode)(equalTo(Schema[Map[Short, Float]].hashCode)) &&
        assert(Schema[Map[Short, Float]].defaultValue(Map.empty))(equalTo(Schema[Map[Short, Float]])) &&
        assert(Schema[Map[Short, Float]].defaultValue(Map.empty).hashCode)(
          equalTo(Schema[Map[Short, Float]].hashCode)
        ) &&
        assert(Schema[Map[Short, Float]].examples(Map((1: Short) -> 0.1f)))(equalTo(Schema[Map[Short, Float]])) &&
        assert(Schema[Map[Short, Float]].examples(Map((1: Short) -> 0.1f)).hashCode)(
          equalTo(Schema[Map[Short, Float]].hashCode)
        ) &&
        assert(Schema[Map[Short, Float]].doc("Map[Short, Float] (updated)"))(not(equalTo(Schema[Map[Short, Float]]))) &&
        assert(Schema[Map[Short, Boolean]]: Any)(not(equalTo(Schema[Map[Short, Float]]))) &&
        assert(Schema[Map[String, Float]]: Any)(not(equalTo(Schema[Map[Short, Float]])))
      },
      test("updates map default value") {
        assert(Schema[Map[Int, Long]].reflect.binding.defaultValue)(isNone) &&
        assert(Schema[Map[Int, Long]].defaultValue(Map.empty).reflect.binding.defaultValue.get.apply())(
          equalTo(Map.empty[Int, Long])
        )
      },
      test("has access to map documentation") {
        assert(Schema[Map[Int, Long]].doc)(equalTo(Doc.Empty))
      },
      test("has access to map key documentation using traversal focus") {
        val int1 = Primitive(
          primitiveType = PrimitiveType.Int(Validation.Numeric.Positive),
          primitiveBinding = Binding.Primitive[Int](),
          typeName = TypeName.int,
          doc = Doc("Int (positive)"),
          modifiers = Nil
        )
        val map1 = Reflect.Map[Binding, Int, Long, Map](
          key = int1,
          value = Reflect.long,
          typeName = TypeName.map[Int, Long],
          mapBinding = null.asInstanceOf[Binding.Map[Map, Int, Long]],
          doc = Doc.Empty,
          modifiers = Nil
        )
        assert(Schema(map1).doc(Traversal.mapKeys(map1)): Doc)(equalTo(Doc("Int (positive)")))
      },
      test("updates map documentation") {
        assert(Schema[Map[Int, Long]].doc("Map (updated)").doc)(equalTo(Doc("Map (updated)")))
      },
      test("has access to map value documentation using traversal focus") {
        val long1 = Primitive(
          primitiveType = PrimitiveType.Long(Validation.Numeric.Positive),
          primitiveBinding = Binding.Primitive[Long](),
          typeName = TypeName.long,
          doc = Doc("Long (positive)"),
          modifiers = Nil
        )
        val map1 = Reflect.Map[Binding, Int, Long, Map](
          key = Reflect.int,
          value = long1,
          typeName = TypeName.map[Int, Long],
          mapBinding = null.asInstanceOf[Binding.Map[Map, Int, Long]],
          doc = Doc.Empty,
          modifiers = Nil
        )
        assert(Schema(map1).doc(Traversal.mapValues(map1)): Doc)(equalTo(Doc("Long (positive)")))
      },
      test("has access to map examples") {
        val map1 = Reflect.Map[Binding, Int, Long, Map](
          key = Reflect.int,
          value = Reflect.long,
          typeName = TypeName.map[Int, Long],
          mapBinding = Binding.Map[Map, Int, Long](
            constructor = MapConstructor.map,
            deconstructor = MapDeconstructor.map,
            examples = Map(1 -> 1L, 2 -> 2L, 3 -> 3L) :: Nil
          ),
          doc = Doc.Empty,
          modifiers = Nil
        )
        assert(Schema(map1).examples)(equalTo(Map(1 -> 1L, 2 -> 2L, 3 -> 3L) :: Nil))
      },
      test("updates map examples") {
        assert(Schema[Map[Int, Long]].examples(Map(1 -> 1L, 2 -> 2L, 3 -> 3L)).examples)(
          equalTo(Map(1 -> 1L, 2 -> 2L, 3 -> 3L) :: Nil)
        )
      },
      test("has access to sequence map value examples using traversal focus") {
        val long1 = Primitive(
          primitiveType = PrimitiveType.Long(Validation.Numeric.Positive),
          primitiveBinding = Binding.Primitive[Long](examples = Seq(1L, 2L, 3L)),
          typeName = TypeName.long,
          doc = Doc.Empty,
          modifiers = Nil
        )
        val map1 = Reflect.Map[Binding, Int, Long, Map](
          key = Reflect.int,
          value = long1,
          typeName = TypeName.map[Int, Long],
          mapBinding = null.asInstanceOf[Binding.Map[Map, Int, Long]],
          doc = Doc.Empty,
          modifiers = Nil
        )
        assert(Schema(map1).examples(Traversal.mapValues(map1)): Seq[_])(equalTo(Seq(1L, 2L, 3L)))
      },
      test("has access to sequence map value examples using traversal focus") {
        val int1 = Primitive(
          primitiveType = PrimitiveType.Int(Validation.Numeric.Positive),
          primitiveBinding = Binding.Primitive[Int](examples = Seq(1, 2, 3)),
          typeName = TypeName.int,
          doc = Doc.Empty,
          modifiers = Nil
        )
        val map1 = Reflect.Map[Binding, Int, Long, Map](
          key = int1,
          value = Reflect.long,
          typeName = TypeName.map[Int, Long],
          mapBinding = null.asInstanceOf[Binding.Map[Map, Int, Long]],
          doc = Doc.Empty,
          modifiers = Nil
        )
        assert(Schema(map1).examples(Traversal.mapKeys(map1)): Seq[_])(equalTo(Seq(1, 2, 3)))
      }
    ),
    suite("Reflect.Dynamic")(
      test("has consistent equals and hashCode") {
        assert(Schema[DynamicValue])(equalTo(Schema[DynamicValue])) &&
        assert(Schema[DynamicValue].hashCode)(equalTo(Schema[DynamicValue].hashCode))
      },
      test("updates dynamic default value") {
        assert(Schema[DynamicValue].reflect.binding.defaultValue)(isNone) &&
        assert(
          Schema[DynamicValue]
            .defaultValue(DynamicValue.Primitive(PrimitiveValue.Int(0)))
            .reflect
            .binding
            .defaultValue
            .get
            .apply()
        )(equalTo(DynamicValue.Primitive(PrimitiveValue.Int(0))))
      },
      test("has access to dynamic documentation") {
        assert(Schema[DynamicValue].doc)(equalTo(Doc.Empty))
      },
      test("updates dynamic documentation") {
        assert(Schema[DynamicValue].doc("Dynamic (updated)").doc)(equalTo(Doc("Dynamic (updated)")))
      },
      test("has access to dynamic examples") {
        assert(Schema[DynamicValue].examples)(equalTo(Seq.empty))
      },
      test("updates dynamic examples") {
        assert(Schema[DynamicValue].examples(DynamicValue.Primitive(PrimitiveValue.Int(1))).examples)(
          equalTo(DynamicValue.Primitive(PrimitiveValue.Int(1)) :: Nil)
        )
      }
    ),
    suite("Reflect.Deferred")(
      test("has consistent equals and hashCode") {
        val deferred1 = Reflect.Deferred[Binding, Int](() => Reflect.int)
        val deferred2 = Reflect.Deferred[Binding, Int](() => Reflect.int)
        val deferred3 = Reflect.int[Binding]
        val deferred4 =
          Primitive(PrimitiveType.Int(Validation.Numeric.Positive), Binding.Primitive.int, TypeName.int, Doc.Empty, Nil)
        val deferred5 = Reflect.Deferred[Binding, Int](() => deferred4)
        assert(Schema(deferred1))(equalTo(Schema(deferred1))) &&
        assert(Schema(deferred1).hashCode)(equalTo(Schema(deferred1).hashCode)) &&
        assert(Schema(deferred2))(equalTo(Schema(deferred1))) &&
        assert(Schema(deferred2).hashCode)(equalTo(Schema(deferred1).hashCode)) &&
        assert(Schema(deferred3))(equalTo(Schema(deferred1))) &&
        assert(Schema(deferred3).hashCode)(equalTo(Schema(deferred1).hashCode)) &&
        assert(Schema(deferred4))(not(equalTo(Schema(deferred1)))) &&
        assert(Schema(deferred5))(not(equalTo(Schema(deferred1))))
      },
      test("updates deferred default value") {
        val deferred1 = Reflect.Deferred[Binding, Int](() => Reflect.int)
        assert(Schema(deferred1).reflect.binding.defaultValue)(isNone) &&
        assert(Schema(deferred1).defaultValue(1).reflect.binding.defaultValue.get.apply())(equalTo(1))
      },
      test("has access to deferred documentation") {
        val deferred1 = Reflect.Deferred[Binding, Int] { () =>
          Primitive(
            PrimitiveType.Int(Validation.Numeric.Positive),
            Binding.Primitive.int,
            TypeName.int,
            Doc("Int (positive)"),
            Nil
          )
        }
        assert(Schema(deferred1).doc)(equalTo(Doc("Int (positive)")))
      },
      test("updates sequence documentation") {
        val deferred1 = Reflect.Deferred[Binding, Int](() => Reflect.int)
        assert(Schema(deferred1).doc("Deferred (updated)").doc)(equalTo(Doc("Deferred (updated)")))
      },
      test("has access to deferred examples") {
        val deferred1 = Reflect.Deferred[Binding, Int] { () =>
          Primitive(
            PrimitiveType.Int(Validation.Numeric.Positive),
            Binding.Primitive(examples = Seq(1, 2, 3)),
            TypeName.int,
            Doc.Empty,
            Nil
          )
        }
        assert(Schema(deferred1).examples)(equalTo(Seq(1, 2, 3)))
      },
      test("updates deferred examples") {
        val deferred1 = Reflect.Deferred[Binding, Int] { () =>
          Primitive(
            PrimitiveType.Int(Validation.Numeric.Positive),
            Binding.Primitive(examples = Seq(1, 2, 3)),
            TypeName.int,
            Doc.Empty,
            Nil
          )
        }
        assert(Schema(deferred1).examples(1, 2).examples)(equalTo(Seq(1, 2)))
      }
    )
  )

  case class Record(b: Byte, i: Int)

  object Record {
    val schema: Schema[Record] = Schema(
      reflect = Reflect.Record[Binding, Record](
        fields = Seq(
          Reflect.byte[Binding].asTerm("b"),
          Reflect.int[Binding].asTerm("i")
        ),
        typeName = TypeName(Namespace(Seq("zio", "blocks", "schema"), Seq("SchemaSpec")), "Record"),
        recordBinding = Binding.Record(
          constructor = new Constructor[Record] {
            def usedRegisters: RegisterOffset = RegisterOffset(bytes = 1, ints = 1)

            def construct(in: Registers, baseOffset: RegisterOffset): Record =
              Record(in.getByte(baseOffset, 0), in.getInt(baseOffset, 0))
          },
          deconstructor = new Deconstructor[Record] {
            def usedRegisters: RegisterOffset = RegisterOffset(bytes = 1, ints = 1)

            def deconstruct(out: Registers, baseOffset: RegisterOffset, in: Record): Unit = {
              out.setByte(baseOffset, 0, in.b)
              out.setInt(baseOffset, 1, in.i)
            }
          },
          examples = Record(1, 1000) :: Nil
        ),
        doc = Doc("Record with 2 fields"),
        modifiers = Nil
      )
    )
  }

  sealed trait Variant

  object Variant {
    val schema: Schema[Variant] = Schema(
      reflect = Reflect.Variant[Binding, Variant](
        cases = Seq(
          Case1.schema.reflect.asTerm("case1"),
          Case2.schema.reflect.asTerm("case2")
        ),
        typeName = TypeName(Namespace(Seq("zio", "blocks", "schema"), Seq("SchemaSpec")), "Variant"),
        variantBinding = Binding.Variant(
          discriminator = new Discriminator[Variant] {
            def discriminate(a: Variant): Int = a match {
              case _: Case1 => 0
              case _: Case2 => 1
            }
          },
          matchers = Matchers(
            new Matcher[Case1] {
              def downcastOrNull(a: Any): Case1 = a match {
                case x: Case1 => x
                case _        => null
              }
            },
            new Matcher[Case2] {
              def downcastOrNull(a: Any): Case2 = a match {
                case x: Case2 => x
                case _        => null
              }
            }
          ),
          examples = Case1(1.0) :: Case2("WWW") :: Nil
        ),
        doc = Doc("Variant with 2 cases"),
        modifiers = Nil
      )
    )
  }

  case class Case1(d: Double) extends Variant

  object Case1 {
    val schema: Schema[Case1] = Schema(
      reflect = Reflect.Record[Binding, Case1](
        fields = Seq(
          Reflect.double[Binding].asTerm("d")
        ),
        typeName = TypeName(Namespace(Seq("zio", "blocks", "schema"), Seq("SchemaSpec")), "Case1"),
        recordBinding = Binding.Record(
          constructor = new Constructor[Case1] {
            def usedRegisters: RegisterOffset = RegisterOffset(doubles = 1)

            def construct(in: Registers, baseOffset: RegisterOffset): Case1 =
              Case1(in.getDouble(baseOffset, 0))
          },
          deconstructor = new Deconstructor[Case1] {
            def usedRegisters: RegisterOffset = RegisterOffset(doubles = 1)

            def deconstruct(out: Registers, baseOffset: RegisterOffset, in: Case1): Unit =
              out.setDouble(baseOffset, 0, in.d)
          },
          examples = Case1(1.0) :: Nil
        ),
        doc = Doc.Empty,
        modifiers = Nil
      )
    )
  }

  case class Case2(s: String) extends Variant

  object Case2 {
    val schema: Schema[Case2] = Schema(
      reflect = Reflect.Record[Binding, Case2](
        fields = List(
          Reflect.string[Binding].asTerm("s")
        ),
        typeName = TypeName(Namespace(List("zio", "blocks", "schema"), Seq("SchemaSpec")), "Case2"),
        recordBinding = Binding.Record(
          constructor = new Constructor[Case2] {
            def usedRegisters: RegisterOffset = RegisterOffset(objects = 1)

            def construct(in: Registers, baseOffset: RegisterOffset): Case2 =
              Case2(in.getObject(baseOffset, 0).asInstanceOf[String])
          },
          deconstructor = new Deconstructor[Case2] {
            def usedRegisters: RegisterOffset = RegisterOffset(objects = 1)

            def deconstruct(out: Registers, baseOffset: RegisterOffset, in: Case2): Unit =
              out.setObject(baseOffset, 0, in.s)
          },
          examples = Case2("WWW") :: Nil
        ),
        doc = Doc.Empty,
        modifiers = Nil
      )
    )
  }
}
