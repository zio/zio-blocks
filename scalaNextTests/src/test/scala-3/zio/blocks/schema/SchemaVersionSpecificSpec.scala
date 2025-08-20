package zio.blocks.schema

import zio.blocks.schema.binding._
import zio.test.Assertion._
import zio.test._

object SchemaVersionSpecificSpec extends ZIOSpecDefault {
  def spec: Spec[TestEnvironment, Any] = suite("SchemaVersionSpecificSpec")(
    suite("Reflect.Record")(
      test("derives schema for named tuples") {
        type NamedTuple4 = (b: Byte, sh: Short, i: Int, l: Long)

        object NamedTuple4 extends CompanionOptics[NamedTuple4] {
          implicit val schema: Schema[NamedTuple4] = Schema.derived
          val b: Lens[NamedTuple4, Byte]           = $(_(0))
          val sh: Lens[NamedTuple4, Short]         = $(_.apply(1))
          val i: Lens[NamedTuple4, Int]            = $(_.i)
          val l: Lens[NamedTuple4, Long]           = $(_.l)
        }

        val record = NamedTuple4.schema.reflect.asRecord
        val value  = (b = 1: Byte, sh = 2: Short, i = 3, l = 4L)
        assert(record.map(_.constructor.usedRegisters))(
          isSome(equalTo(RegisterOffset(bytes = 1, shorts = 1, ints = 1, longs = 1)))
        ) &&
        assert(NamedTuple4.b.get(value))(equalTo(1: Byte)) &&
        assert(NamedTuple4.sh.get(value))(equalTo(2: Short)) &&
        assert(NamedTuple4.i.get(value))(equalTo(3)) &&
        assert(NamedTuple4.l.get(value))(equalTo(4L)) &&
        assert(NamedTuple4.b.replace(value, 5: Byte))(equalTo((b = 5: Byte, sh = 2: Short, i = 3, l = 4L))) &&
        assert(NamedTuple4.sh.replace(value, 5: Short))(equalTo((b = 1: Byte, sh = 5: Short, i = 3, l = 4L))) &&
        assert(NamedTuple4.i.replace(value, 5))(equalTo((b = 1: Byte, sh = 2: Short, i = 5, l = 4L))) &&
        assert(NamedTuple4.l.replace(value, 5L))(equalTo((b = 1: Byte, sh = 2: Short, i = 3, l = 5L))) &&
        assert(NamedTuple4.schema.fromDynamicValue(NamedTuple4.schema.toDynamicValue(value)))(
          isRight(equalTo(value))
        ) &&
        assert(NamedTuple4.schema)(
          equalTo(
            new Schema[NamedTuple4](
              reflect = Reflect.Record[Binding, NamedTuple4](
                fields = Vector(
                  Schema[Byte].reflect.asTerm("b"),
                  Schema[Short].reflect.asTerm("sh"),
                  Schema[Int].reflect.asTerm("i"),
                  Schema[Long].reflect.asTerm("l")
                ),
                typeName = TypeName(
                  namespace = Namespace(packages = Seq("scala"), values = Seq("NamedTuple")),
                  name = "NamedTuple",
                  params = Seq(TypeName.byte, TypeName.short, TypeName.int, TypeName.long)
                ),
                recordBinding = null
              )
            )
          )
        )
      },
      test("derives schema for complex named tuples") {
        case class Product(i: Int, s: String)

        val value1         = (i = 1, s = "VVV")
        val value2         = (i = (1, 2L), s = ("VVV", "WWW"))
        val value3         = (i = Some(1), s = Some("VVV"))
        val expectedFields =
          Vector(Schema[Int].reflect.asTerm("i"), Schema[String].reflect.asTerm("s"))
        val schema1: Schema[NamedTuple.NamedTuple[("i", "s"), Int *: String *: EmptyTuple]] = Schema.derived
        val schema2: Schema[NamedTuple.NamedTuple["i" *: "s" *: EmptyTuple, (Int, String)]] = Schema.derived
        val schema3: Schema[NamedTuple.Reverse[(s: String, i: Int)]]                        = Schema.derived
        val schema4: Schema[NamedTuple.Tail[(l: Long, i: Int, s: String)]]                  = Schema.derived
        val schema5: Schema[NamedTuple.Init[(i: Int, s: String, l: Long)]]                  = Schema.derived
        val schema6: Schema[NamedTuple.Drop[(l: Long, i: Int, s: String), 1]]               = Schema.derived
        val schema7: Schema[NamedTuple.Take[(i: Int, s: String, l: Long), 2]]               = Schema.derived
        val schema8: Schema[NamedTuple.Concat[(i: Int), (s: String)]]                       = Schema.derived
        val schema9: Schema[NamedTuple.Zip[(i: Int, s: String), (i: Long, s: String)]]      = Schema.derived
        val schema10: Schema[NamedTuple.Map[(i: Int, s: String), Option]]                   = Schema.derived
        val schema11: Schema[NamedTuple.From[Product]]                                      = Schema.derived
        assert(schema1.reflect.asRecord.get.fields)(equalTo(expectedFields)) &&
        assert(schema1.fromDynamicValue(schema1.toDynamicValue(value1)))(isRight(equalTo(value1))) &&
        assert(schema2.reflect.asRecord.get.fields)(equalTo(expectedFields)) &&
        assert(schema2.fromDynamicValue(schema2.toDynamicValue(value1)))(isRight(equalTo(value1))) &&
        assert(schema3.reflect.asRecord.get.fields)(equalTo(expectedFields)) &&
        assert(schema3.fromDynamicValue(schema3.toDynamicValue(value1)))(isRight(equalTo(value1))) &&
        assert(schema4.reflect.asRecord.get.fields)(equalTo(expectedFields)) &&
        assert(schema4.fromDynamicValue(schema4.toDynamicValue(value1)))(isRight(equalTo(value1))) &&
        assert(schema5.reflect.asRecord.get.fields)(equalTo(expectedFields)) &&
        assert(schema5.fromDynamicValue(schema5.toDynamicValue(value1)))(isRight(equalTo(value1))) &&
        assert(schema6.reflect.asRecord.get.fields)(equalTo(expectedFields)) &&
        assert(schema6.fromDynamicValue(schema6.toDynamicValue(value1)))(isRight(equalTo(value1))) &&
        assert(schema7.reflect.asRecord.get.fields)(equalTo(expectedFields)) &&
        assert(schema7.fromDynamicValue(schema7.toDynamicValue(value1)))(isRight(equalTo(value1))) &&
        assert(schema8.reflect.asRecord.get.fields)(equalTo(expectedFields)) &&
        assert(schema8.fromDynamicValue(schema8.toDynamicValue(value1)))(isRight(equalTo(value1))) &&
        assert(schema9.reflect.asRecord.get.fields)(
          equalTo(
            Vector(
              Schema.derived[(Int, Long)].reflect.asTerm("i"),
              Schema.derived[(String, String)].reflect.asTerm("s")
            )
          )
        ) &&
        assert(schema9.fromDynamicValue(schema9.toDynamicValue(value2)))(isRight(equalTo(value2))) &&
        assert(schema10.reflect.asRecord.get.fields)(
          equalTo(
            Vector(Schema[Option[Int]].reflect.asTerm("i"), Schema[Option[String]].reflect.asTerm("s"))
          )
        ) &&
        assert(schema10.fromDynamicValue(schema10.toDynamicValue(value3)))(isRight(equalTo(value3))) &&
        assert(schema11.reflect.asRecord.get.fields)(equalTo(expectedFields)) &&
        assert(schema11.fromDynamicValue(schema11.toDynamicValue(value1)))(isRight(equalTo(value1)))
      },
      test("derives schema for complex generic and named tuples") {
        val expectedFields =
          Vector(Schema[Int].reflect.asTerm("_1"), Schema[String].reflect.asTerm("_2"))
        val schema1: Schema[Tuple.Reverse[(String, Int)]]              = Schema.derived
        val schema2: Schema[NamedTuple.DropNames[(i: Int, s: String)]] = Schema.derived
        assert(schema1.reflect.asRecord.get.fields)(equalTo(expectedFields)) &&
        assert(schema2.reflect.asRecord.get.fields)(equalTo(expectedFields))
      },
      test("derives schema for generic named tuples") {
        type GenericNamedTuple2[A, B] = (a: A, b: B)

        object NamedTupleOfIntAndString extends CompanionOptics[GenericNamedTuple2[Int, String]] {
          implicit val schema: Schema[GenericNamedTuple2[Int, String]] = Schema.derived
          val a: Lens[GenericNamedTuple2[Int, String], Int]            = $(_.a)
          val b: Lens[GenericNamedTuple2[Int, String], String]         = $(_.b)
        }

        val record = NamedTupleOfIntAndString.schema.reflect.asRecord
        val value  = (a = 1, b = "VVV")
        assert(record.map(_.constructor.usedRegisters))(isSome(equalTo(RegisterOffset(ints = 1, objects = 1)))) &&
        assert(NamedTupleOfIntAndString.a.get(value))(equalTo(1)) &&
        assert(NamedTupleOfIntAndString.b.get(value))(equalTo("VVV")) &&
        assert(NamedTupleOfIntAndString.a.replace(value, 2))(equalTo((a = 2, b = "VVV"))) &&
        assert(NamedTupleOfIntAndString.b.replace(value, "WWW"))(equalTo((a = 1, b = "WWW"))) &&
        assert(NamedTupleOfIntAndString.schema.fromDynamicValue(NamedTupleOfIntAndString.schema.toDynamicValue(value)))(
          isRight(equalTo(value))
        ) &&
        assert(NamedTupleOfIntAndString.schema)(
          equalTo(
            new Schema[GenericNamedTuple2[Int, String]](
              reflect = Reflect.Record[Binding, GenericNamedTuple2[Int, String]](
                fields = Vector(
                  Schema[Int].reflect.asTerm("a"),
                  Schema[String].reflect.asTerm("b")
                ),
                typeName = TypeName(
                  namespace = Namespace(packages = Seq("scala"), values = Seq("NamedTuple")),
                  name = "NamedTuple",
                  params = Seq(TypeName.int, TypeName.string)
                ),
                recordBinding = null
              )
            )
          )
        )
      },
      test("derives schema for higher-kind named tuples") {
        type HKNamedTuple2[F[_]] = (a: F[Int], b: F[String])

        object NamedTupleOfIntAndStringLists extends CompanionOptics[HKNamedTuple2[List]] {
          implicit val schema: Schema[HKNamedTuple2[List]] = Schema.derived
          val a: Traversal[HKNamedTuple2[List], Int]       = $(_.a.each)
          val b: Traversal[HKNamedTuple2[List], String]    = $(_.b.each)
        }

        val record = NamedTupleOfIntAndStringLists.schema.reflect.asRecord
        val value  = (a = List(1, 2, 3), b = List("VVV"))
        assert(record.map(_.constructor.usedRegisters))(isSome(equalTo(RegisterOffset(objects = 2)))) &&
        assert(NamedTupleOfIntAndStringLists.a.fold(value)(0, _ + _))(equalTo(6)) &&
        assert(NamedTupleOfIntAndStringLists.b.fold(value)("", _ + _))(equalTo("VVV")) &&
        assert(
          NamedTupleOfIntAndStringLists.schema.fromDynamicValue(
            NamedTupleOfIntAndStringLists.schema.toDynamicValue(value)
          )
        )(isRight(equalTo(value))) &&
        assert(NamedTupleOfIntAndStringLists.schema)(
          equalTo(
            new Schema[HKNamedTuple2[List]](
              reflect = Reflect.Record[Binding, HKNamedTuple2[List]](
                fields = Vector(
                  Schema[List[Int]].reflect.asTerm("a"),
                  Schema[List[String]].reflect.asTerm("b")
                ),
                typeName = TypeName(
                  namespace = Namespace(packages = Seq("scala"), values = Seq("NamedTuple")),
                  name = "NamedTuple",
                  params = Seq(TypeName.list(TypeName.int), TypeName.list(TypeName.string))
                ),
                recordBinding = null
              )
            )
          )
        )
      },
      test("derives schema for named tuples with more than 22 fields") {
        type NamedTuple24 = (
          i1: Int,
          i2: Int,
          i3: Int,
          i4: Int,
          i5: Int,
          i6: Int,
          i7: Int,
          i8: Int,
          i9: Int,
          i10: Int,
          i11: Int,
          i12: Int,
          i13: Int,
          i14: Int,
          i15: Int,
          i16: Int,
          i17: Int,
          i18: Int,
          i19: Int,
          i20: Int,
          b21: Box1,
          b22: Box2,
          i23: Int,
          s24: String
        )

        object NamedTuple24 extends CompanionOptics[NamedTuple24] {
          implicit val schema: Schema[NamedTuple24] = Schema.derived
          val b21: Lens[NamedTuple24, Box1]         = $(_(20))
          val b22: Lens[NamedTuple24, Box2]         = $(_.apply(21))
          val i23: Lens[NamedTuple24, Int]          = $(_.i23)
          val s24: Lens[NamedTuple24, String]       = $(_.s24)
        }

        val record = NamedTuple24.schema.reflect.asRecord
        val value  = (
          i1 = 1,
          i2 = 2,
          i3 = 3,
          i4 = 4,
          i5 = 5,
          i6 = 6,
          i7 = 7,
          i8 = 8,
          i9 = 9,
          i10 = 10,
          i11 = 11,
          i12 = 12,
          i13 = 13,
          i14 = 14,
          i15 = 15,
          i16 = 16,
          i17 = 17,
          i18 = 18,
          i19 = 19,
          i20 = 20,
          b21 = Box1(21L),
          b22 = Box2("22"),
          i23 = 23,
          s24 = "24"
        )
        assert(record.map(_.constructor.usedRegisters))(isSome(equalTo(RegisterOffset(ints = 21, objects = 3)))) &&
        assert(record.map(_.deconstructor.usedRegisters))(isSome(equalTo(RegisterOffset(ints = 21, objects = 3)))) &&
        assert(NamedTuple24.b21.get(value))(equalTo(Box1(21L))) &&
        assert(NamedTuple24.b22.get(value))(equalTo(Box2("22"))) &&
        assert(NamedTuple24.i23.get(value))(equalTo(23)) &&
        assert(NamedTuple24.s24.get(value))(equalTo("24")) &&
        assert(NamedTuple24.schema.fromDynamicValue(NamedTuple24.schema.toDynamicValue(value)))(isRight(equalTo(value)))
      }
    )
  )

  case class Box1(l: Long) extends AnyVal derives Schema

  case class Box2(s: String) extends AnyVal derives Schema
}
