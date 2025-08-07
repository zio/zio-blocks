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
                  name = "NamedTuple"
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
        val value = (
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
