package zio.blocks.schema

import zio.blocks.schema.binding.*
import zio.blocks.typeid.{Owner, TypeBounds, TypeId, TypeRepr}
import zio.test.*
import zio.test.Assertion.*

object SchemaVersionSpecificSpec extends SchemaBaseSpec {

  private def expectedNamedTupleTypeId[A]: TypeId[A] =
    TypeId.opaque[A](
      "NamedTuple",
      Owner(List(Owner.Package("scala"), Owner.Term("NamedTuple"))),
      Nil,
      TypeRepr.Ref(TypeId.string),
      TypeBounds.Unbounded
    )

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
        assertTrue(
          record.map(_.constructor.usedRegisters).get == RegisterOffset(bytes = 1, shorts = 1, ints = 1, longs = 1),
          NamedTuple4.b.get(value) == (1: Byte),
          NamedTuple4.sh.get(value) == (2: Short),
          NamedTuple4.i.get(value) == 3,
          NamedTuple4.l.get(value) == 4L,
          NamedTuple4.b.replace(value, 5: Byte) == (b = 5: Byte, sh = 2: Short, i = 3, l = 4L),
          NamedTuple4.sh.replace(value, 5: Short) == (b = 1: Byte, sh = 5: Short, i = 3, l = 4L),
          NamedTuple4.i.replace(value, 5) == (b = 1: Byte, sh = 2: Short, i = 5, l = 4L),
          NamedTuple4.l.replace(value, 5L) == (b = 1: Byte, sh = 2: Short, i = 3, l = 5L)
        ) &&
        assert(NamedTuple4.schema.fromDynamicValue(NamedTuple4.schema.toDynamicValue(value)))(
          isRight(equalTo(value))
        ) &&
        assertTrue(
          NamedTuple4.schema == new Schema[NamedTuple4](
            reflect = Reflect.Record[Binding, NamedTuple4](
              fields = Vector(
                Schema[Byte].reflect.asTerm("b"),
                Schema[Short].reflect.asTerm("sh"),
                Schema[Int].reflect.asTerm("i"),
                Schema[Long].reflect.asTerm("l")
              ),
              typeId = expectedNamedTupleTypeId[NamedTuple4],
              recordBinding = null
            )
          )
        )
      },
      test("derives schema for case classes with named tuple fields") {
        case class NamedTuples(v1: (b: Byte, sh: Short), v2: (i: Int, l: Long))

        implicit val schema: Schema[NamedTuples] = Schema.derived

        object NamedTuples extends CompanionOptics[NamedTuples] {
          val v1: Lens[NamedTuples, (b: Byte, sh: Short)] = $(_.v1)
          val v2: Lens[NamedTuples, (i: Int, l: Long)]    = $(_.v2)
        }

        val value = NamedTuples((b = 1: Byte, sh = 2: Short), (i = 3, l = 4L))
        assert(schema.fromDynamicValue(schema.toDynamicValue(value)))(isRight(equalTo(value)))
      },
      test("derives schema for complex named tuples") {
        case class Product(i: Int, s: String)

        val value1 = (i = 1, s = "VVV")
        val value2 = (i = (1, 2L), s = ("VVV", "WWW"))
        val value3 = (i = Some(1), s = Some("VVV"))
        val value4 = (1, "VVV")
        val value5 = NamedTuple.Empty

        val schema1: Schema[NamedTuple.NamedTuple[("i", "s"), Int *: String *: EmptyTuple]] = Schema.derived
        val schema2: Schema[NamedTuple.NamedTuple["i" *: "s" *: EmptyTuple, (Int, String)]] = Schema.derived
        val schema3: Schema[NamedTuple.Reverse[(s: String, i: Int)]]                        = Schema.derived
        val schema4: Schema[NamedTuple.Tail[(l: Long, i: Int, s: String)]]                  = Schema.derived
        val schema5: Schema[NamedTuple.Init[(i: Int, s: String, l: Long)]]                  = Schema.derived
        val schema6: Schema[NamedTuple.Drop[(l: Long, i: Int, s: String), 1]]               = Schema.derived
        val schema7: Schema[NamedTuple.Take[(i: Int, s: String, l: Long), 2]]               = Schema.derived
        val schema8: Schema[NamedTuple.Concat[(i: Int), (s: String)]]                       = Schema.derived
        val schema9: Schema[NamedTuple.NamedTuple[("i", "s"), Int *: Tuple1[String]]]       = Schema.derived
        val schema10: Schema[NamedTuple.NamedTuple["i" *: Tuple1["s"], (Int, String)]]      = Schema.derived
        val schema11: Schema[NamedTuple.Zip[(i: Int, s: String), (i: Long, s: String)]]     = Schema.derived
        val schema12: Schema[NamedTuple.Map[(i: Int, s: String), Option]]                   = Schema.derived
        val schema13: Schema[NamedTuple.From[Product]]                                      = Schema.derived
        val schema14: Schema[NamedTuple.Empty]                                              = Schema.derived
        val schema15: Schema[NamedTuple.Drop[(l: Long, i: Int, s: String), 3]]              = Schema.derived
        assertTrue(
          schema1 == new Schema[(i: Int, s: String)](
            reflect = Reflect.Record[Binding, (i: Int, s: String)](
              fields = Vector(
                Schema[Int].reflect.asTerm("i"),
                Schema[String].reflect.asTerm("s")
              ),
              typeId = expectedNamedTupleTypeId[(i: Int, s: String)],
              recordBinding = null
            )
          ),
          schema1 == schema2,
          schema1 == schema3,
          schema1 == schema4,
          schema1 == schema5,
          schema1 == schema6,
          schema1 == schema7,
          schema1 == schema8,
          schema1 == schema9,
          schema1 == schema10,
          schema1 == schema13,
          schema11 == new Schema[(i: (Int, Long), s: (String, String))](
            reflect = Reflect.Record[Binding, (i: (Int, Long), s: (String, String))](
              fields = Vector(
                Schema.derived[(Int, Long)].reflect.asTerm("i"),
                Schema.derived[(String, String)].reflect.asTerm("s")
              ),
              typeId = expectedNamedTupleTypeId[(i: (Int, Long), s: (String, String))],
              recordBinding = null
            )
          ),
          schema12 == new Schema[(i: Option[Int], s: Option[String])](
            reflect = Reflect.Record[Binding, (i: Option[Int], s: Option[String])](
              fields = Vector(
                Schema[Option[Int]].reflect.asTerm("i"),
                Schema[Option[String]].reflect.asTerm("s")
              ),
              typeId = expectedNamedTupleTypeId[(i: Option[Int], s: Option[String])],
              recordBinding = null
            )
          ),
          schema14 == new Schema[NamedTuple.Empty](
            reflect = Reflect.Record[Binding, NamedTuple.Empty](
              fields = Vector(),
              typeId = expectedNamedTupleTypeId[NamedTuple.Empty],
              recordBinding = null
            )
          ),
          schema15 == schema14
        ) &&
        assert(schema1.fromDynamicValue(schema1.toDynamicValue(value1)))(isRight(equalTo(value1))) &&
        assert(schema1.fromDynamicValue(schema1.toDynamicValue(value4)))(isRight(equalTo(value1))) &&
        assert(schema2.fromDynamicValue(schema2.toDynamicValue(value1)))(isRight(equalTo(value1))) &&
        assert(schema2.fromDynamicValue(schema2.toDynamicValue(value4)))(isRight(equalTo(value1))) &&
        assert(schema3.fromDynamicValue(schema3.toDynamicValue(value1)))(isRight(equalTo(value1))) &&
        assert(schema3.fromDynamicValue(schema3.toDynamicValue(value4)))(isRight(equalTo(value1))) &&
        assert(schema4.fromDynamicValue(schema4.toDynamicValue(value1)))(isRight(equalTo(value1))) &&
        assert(schema4.fromDynamicValue(schema4.toDynamicValue(value4)))(isRight(equalTo(value1))) &&
        assert(schema5.fromDynamicValue(schema5.toDynamicValue(value1)))(isRight(equalTo(value1))) &&
        assert(schema5.fromDynamicValue(schema5.toDynamicValue(value4)))(isRight(equalTo(value1))) &&
        assert(schema6.fromDynamicValue(schema6.toDynamicValue(value1)))(isRight(equalTo(value1))) &&
        assert(schema6.fromDynamicValue(schema6.toDynamicValue(value4)))(isRight(equalTo(value1))) &&
        assert(schema7.fromDynamicValue(schema7.toDynamicValue(value1)))(isRight(equalTo(value1))) &&
        assert(schema7.fromDynamicValue(schema7.toDynamicValue(value4)))(isRight(equalTo(value1))) &&
        assert(schema8.fromDynamicValue(schema8.toDynamicValue(value1)))(isRight(equalTo(value1))) &&
        assert(schema8.fromDynamicValue(schema8.toDynamicValue(value4)))(isRight(equalTo(value1))) &&
        assert(schema9.fromDynamicValue(schema9.toDynamicValue(value1)))(isRight(equalTo(value1))) &&
        assert(schema9.fromDynamicValue(schema9.toDynamicValue(value4)))(isRight(equalTo(value1))) &&
        assert(schema10.fromDynamicValue(schema10.toDynamicValue(value1)))(isRight(equalTo(value1))) &&
        assert(schema10.fromDynamicValue(schema10.toDynamicValue(value4)))(isRight(equalTo(value1))) &&
        assert(schema11.fromDynamicValue(schema11.toDynamicValue(value2)))(isRight(equalTo(value2))) &&
        assert(schema12.fromDynamicValue(schema12.toDynamicValue(value3)))(isRight(equalTo(value3))) &&
        assert(schema13.fromDynamicValue(schema13.toDynamicValue(value1)))(isRight(equalTo(value1))) &&
        assert(schema14.fromDynamicValue(schema14.toDynamicValue(value5)))(isRight(equalTo(value5))) &&
        assert(schema15.fromDynamicValue(schema15.toDynamicValue(value5)))(isRight(equalTo(value5)))
      },
      test("derives schema for complex generic tuples") {
        val value1 = (1, "VVV")
        val value2 = (i = 1, s = "VVV")

        val schema1: Schema[Tuple.Reverse[(String, Int)]]              = Schema.derived
        val schema2: Schema[NamedTuple.DropNames[(i: Int, s: String)]] = Schema.derived
        val record1                                                    = schema1.reflect.asRecord
        assertTrue(
          record1.isDefined,
          record1.get.fields.map(_.name) == Vector("_1", "_2"),
          schema1.reflect.typeId.name == "Tuple2",
          schema1.reflect.typeId.owner == Owner.fromPackagePath("scala"),
          schema1 == schema2
        ) &&
        assert(schema1.fromDynamicValue(schema1.toDynamicValue(value1)))(isRight(equalTo(value1))) &&
        assert(schema1.fromDynamicValue(schema1.toDynamicValue(value2)))(isRight(equalTo(value1))) &&
        assert(schema2.fromDynamicValue(schema2.toDynamicValue(value1)))(isRight(equalTo(value1))) &&
        assert(schema2.fromDynamicValue(schema2.toDynamicValue(value2)))(isRight(equalTo(value1)))
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
        assertTrue(
          record.map(_.constructor.usedRegisters).get == RegisterOffset(ints = 1, objects = 1),
          NamedTupleOfIntAndString.a.get(value) == 1,
          NamedTupleOfIntAndString.b.get(value) == "VVV",
          NamedTupleOfIntAndString.a.replace(value, 2) == (a = 2, b = "VVV"),
          NamedTupleOfIntAndString.b.replace(value, "WWW") == (a = 1, b = "WWW")
        ) &&
        assert(NamedTupleOfIntAndString.schema.fromDynamicValue(NamedTupleOfIntAndString.schema.toDynamicValue(value)))(
          isRight(equalTo(value))
        ) &&
        assertTrue(
          NamedTupleOfIntAndString.schema == new Schema[GenericNamedTuple2[Int, String]](
            reflect = Reflect.Record[Binding, GenericNamedTuple2[Int, String]](
              fields = Vector(
                Schema[Int].reflect.asTerm("a"),
                Schema[String].reflect.asTerm("b")
              ),
              typeId = expectedNamedTupleTypeId[GenericNamedTuple2[Int, String]],
              recordBinding = null
            )
          )
        )
      },
      test("derives schema for higher-kind named tuples") {
        type HKNamedTuple2[F[_], G[_]] = (a: F[Int], b: G[String])

        object NamedTupleOfIntAndStringLists extends CompanionOptics[HKNamedTuple2[List, Set]] {
          implicit val schema: Schema[HKNamedTuple2[List, Set]] = Schema.derived
          val a: Traversal[HKNamedTuple2[List, Set], Int]       = $(_.a.each)
          val b: Traversal[HKNamedTuple2[List, Set], String]    = $(_.b.each)
        }

        val record = NamedTupleOfIntAndStringLists.schema.reflect.asRecord
        val value  = (a = List(1, 2, 3), b = Set("VVV"))
        assertTrue(
          record.map(_.constructor.usedRegisters).get == RegisterOffset(objects = 2),
          NamedTupleOfIntAndStringLists.a.fold(value)(0, _ + _) == 6,
          NamedTupleOfIntAndStringLists.b.fold(value)("", _ + _) == "VVV"
        ) &&
        assert(
          NamedTupleOfIntAndStringLists.schema.fromDynamicValue(
            NamedTupleOfIntAndStringLists.schema.toDynamicValue(value)
          )
        )(isRight(equalTo(value))) &&
        assertTrue(
          NamedTupleOfIntAndStringLists.schema == new Schema[HKNamedTuple2[List, Set]](
            reflect = Reflect.Record[Binding, HKNamedTuple2[List, Set]](
              fields = Vector(
                Schema[List[Int]].reflect.asTerm("a"),
                Schema[Set[String]].reflect.asTerm("b")
              ),
              typeId = expectedNamedTupleTypeId[HKNamedTuple2[List, Set]],
              recordBinding = null
            )
          )
        )
      },
      test("derives schema for recursive named tuples") {
        type NamedTuple9 = (
          i1: Int,
          i2: Int,
          i3: Int,
          i4: Int,
          i5: Int,
          i6: Int,
          i7: Int,
          t8: (Int, Int, Int),
          o9: Option[NamedTuple9]
        )

        object NamedTuple9 extends CompanionOptics[NamedTuple9] {
          @annotation.nowarn("msg=Infinite loop")
          implicit lazy val schema: Schema[NamedTuple9]  = Schema.derived
          val o9: Lens[NamedTuple9, Option[NamedTuple9]] = $(_.o9)
          val t8_i1: Lens[NamedTuple9, Int]              = $(_.t8(0))
        }

        type NamedTuple24 = (
          i1: Int,
          i2: Int,
          i3: Int,
          i4: Int,
          i5: Int,
          i6: Int,
          i7: Int,
          i8: Int,
          o9: Option[NamedTuple24],
          l10: List[NamedTuple9],
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
          @annotation.nowarn("msg=Infinite loop")
          implicit lazy val schema: Schema[NamedTuple24]   = Schema.derived
          val o9: Lens[NamedTuple24, Option[NamedTuple24]] = $(_.o9)
          val l10: Lens[NamedTuple24, List[NamedTuple9]]   = $(_.l10)
          val b21: Lens[NamedTuple24, Box1]                = $(_(20))
          val b22: Lens[NamedTuple24, Box2]                = $(_.apply(21))
          val i23: Lens[NamedTuple24, Int]                 = $(_.i23)
          val s24: Lens[NamedTuple24, String]              = $(_.s24)
          val l10_i1s: Traversal[NamedTuple24, Int]        = $(_(9).each(0))
        }

        val record2 = NamedTuple24.schema.reflect.asRecord
        val value1  = (
          i1 = 1,
          i2 = 2,
          i3 = 3,
          i4 = 4,
          i5 = 5,
          i6 = 6,
          i7 = 7,
          t8 = (8, 9, 10),
          o9 = Some(
            (
              i1 = 11,
              i2 = 12,
              i3 = 13,
              i4 = 14,
              i5 = 15,
              i6 = 16,
              i7 = 17,
              t8 = (18, 19, 20),
              o9 = None
            )
          )
        )
        val value2 = (
          i1 = 1,
          i2 = 2,
          i3 = 3,
          i4 = 4,
          i5 = 5,
          i6 = 6,
          i7 = 7,
          i8 = 8,
          o9 = None,
          l10 = List(
            (
              i1 = 11,
              i2 = 12,
              i3 = 13,
              i4 = 14,
              i5 = 15,
              i6 = 16,
              i7 = 17,
              t8 = (18, 19, 20),
              o9 = None
            )
          ),
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
        assertTrue(
          record2.map(_.constructor.usedRegisters).get == RegisterOffset(ints = 19, objects = 5),
          record2.map(_.deconstructor.usedRegisters).get == RegisterOffset(ints = 19, objects = 5),
          NamedTuple9.t8_i1.get(value1) == 8
        ) &&
        assert(NamedTuple24.o9.get(value2))(isNone) && assertTrue(
          NamedTuple24.b21.get(value2) == Box1(21L),
          NamedTuple24.b22.get(value2) == Box2("22"),
          NamedTuple24.i23.get(value2) == 23,
          NamedTuple24.s24.get(value2) == "24",
          NamedTuple24.l10_i1s.fold(value2)(0, _ + _) == 11
        ) &&
        assert(NamedTuple9.schema.fromDynamicValue(NamedTuple9.schema.toDynamicValue(value1)))(
          isRight(equalTo(value1))
        ) &&
        assert(NamedTuple24.schema.fromDynamicValue(NamedTuple24.schema.toDynamicValue(value2)))(
          isRight(equalTo(value2))
        )
      } @@ TestAspect.ignore,
      test("derives schema for recursive generic tuples with more than 22 fields") {
        type Tuple24 = (
          Int,
          Float,
          Long,
          Double,
          Boolean,
          Byte,
          Char,
          Short,
          Unit,
          Option[Tuple24],
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

        object Tuple24 extends CompanionOptics[Tuple24] {
          implicit val schema: Schema[Tuple24] = Schema.derived
          val i21: Lens[Tuple24, Int]          = $(_(20))
          val i22: Lens[Tuple24, Int]          = $(_.apply(21))
          val i23: Lens[Tuple24, Int]          = $(_(22))
          val i24: Lens[Tuple24, Int]          = $(_.apply(23))
        }

        val record = Tuple24.schema.reflect.asRecord
        val value  = (
          1,
          2.0f,
          3L,
          4.0,
          true,
          6: Byte,
          '7',
          8: Short,
          (),
          None,
          11,
          12,
          13,
          14,
          15,
          16,
          17,
          18,
          19,
          20,
          21,
          22,
          23,
          24
        )
        val offset = RegisterOffset(
          ints = 15,
          floats = 1,
          longs = 1,
          doubles = 1,
          booleans = 1,
          bytes = 1,
          chars = 1,
          shorts = 1,
          objects = 1
        )
        assertTrue(
          record.map(_.constructor.usedRegisters).get == offset,
          record.map(_.deconstructor.usedRegisters).get == offset,
          Tuple24.i21.get(value) == 21,
          Tuple24.i22.get(value) == 22,
          Tuple24.i23.get(value) == 23,
          Tuple24.i24.get(value) == 24
        ) &&
        assert(Tuple24.schema.fromDynamicValue(Tuple24.schema.toDynamicValue(value)))(isRight(equalTo(value)))
      }
    )
  )

  case class Box1(l: Long) extends AnyVal derives Schema

  case class Box2(s: String) extends AnyVal derives Schema
}
