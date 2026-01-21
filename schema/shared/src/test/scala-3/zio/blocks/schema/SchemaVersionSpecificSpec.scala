package zio.blocks.schema

import scala.collection.immutable.ArraySeq
import zio.blocks.schema.SchemaVersionSpecificSpec.{InnerId, InnerValue}
import zio.blocks.schema.binding._
import zio.blocks.typeid.TypeId
import zio.test.Assertion._
import zio.test._

object SchemaVersionSpecificSpec extends SchemaBaseSpec {
  def spec: Spec[TestEnvironment, Any] = suite("SchemaVersionSpecificSpec")(
    suite("Reflect.Record")(
      test("derives schema using 'derives' keyword") {

        /** Record: Record1 */
        case class Record1(c: Char, d: Double) derives Schema

        object Record1 extends CompanionOptics[Record1] {
          val c: Lens[Record1, Char]   = optic(x => x.c)
          val d: Lens[Record1, Double] = optic(_.d)
        }

        val schema = Schema[Record1]
        val record = schema.reflect.asRecord
        assert(record.map(_.constructor.usedRegisters))(isSome(equalTo(RegisterOffset(chars = 1, doubles = 1)))) &&
        assert(record.map(_.deconstructor.usedRegisters))(isSome(equalTo(RegisterOffset(chars = 1, doubles = 1)))) &&
        assert(Record1.c.get(Record1('1', 2.0)))(equalTo('1')) &&
        assert(Record1.d.get(Record1('1', 2.0)))(equalTo(2.0)) &&
        assert(Record1.c.replace(Record1('1', 2.0), '3'))(equalTo(Record1('3', 2.0))) &&
        assert(Record1.d.replace(Record1('1', 2.0), 3.0))(equalTo(Record1('1', 3.0))) &&
        assert(schema.fromDynamicValue(schema.toDynamicValue(Record1('1', 2.0))))(
          isRight(equalTo(Record1('1', 2.0)))
        ) &&
        assert(record.map(_.fields.map(_.name)))(isSome(equalTo(Vector("c", "d")))) &&
        assert(record.map(_.typeId))(
          isSome(
            equalTo(TypeId.derived[Record1])
          )
        ) &&
        assert(record.map(_.doc))(isSome(equalTo(Doc("/** Record: Record1 */"))))
      },
      test("derives schema recursively for options and supported collections using 'derives' keyword") {
        case class Foo(
          as: ArraySeq[Bar],
          l: List[Bar],
          m: Map[Bar, Bar],
          o: Option[Bar],
          v: Vector[Bar],
          s: Set[Bar]
        ) derives Schema

        case class Bar(id: Int)

        val schema = Schema[Foo]
        val record = schema.reflect.asRecord
        val value  = Foo(
          ArraySeq(Bar(1)),
          List(Bar(2)),
          Map(Bar(3) -> Bar(4)),
          Option(Bar(5)),
          Vector(Bar(6)),
          Set(Bar(7))
        )
        assert(record.map(_.constructor.usedRegisters))(isSome(equalTo(RegisterOffset(objects = 6)))) &&
        assert(record.map(_.deconstructor.usedRegisters))(isSome(equalTo(RegisterOffset(objects = 6)))) &&
        assert(schema.fromDynamicValue(schema.toDynamicValue(value)))(isRight(equalTo(value))) &&
        assert(record.map(_.fields.map(_.name)))(isSome(equalTo(Vector("as", "l", "m", "o", "v", "s"))))
      },
      test("derives schema for tuples") {
        type Tuple4 = (Byte, Short, Int, Long)

        object Tuple4 extends CompanionOptics[Tuple4] {
          implicit val schema: Schema[Tuple4] = Schema.derived
          val _1: Lens[Tuple4, Byte]          = $(_(0))
          val _2: Lens[Tuple4, Short]         = $(_.apply(1))
          val _3: Lens[Tuple4, Int]           = $(_._3)
          val _4: Lens[Tuple4, Long]          = $(_._4)
        }

        val record = Tuple4.schema.reflect.asRecord
        val value  = (1: Byte, 2: Short, 3, 4L)
        assert(record.map(_.constructor.usedRegisters))(
          isSome(equalTo(RegisterOffset(bytes = 1, shorts = 1, ints = 1, longs = 1)))
        ) &&
        assert(Tuple4._1.get(value))(equalTo(1: Byte)) &&
        assert(Tuple4._2.get(value))(equalTo(2: Short)) &&
        assert(Tuple4._3.get(value))(equalTo(3)) &&
        assert(Tuple4._4.get(value))(equalTo(4L)) &&
        assert(Tuple4._1.replace(value, 5: Byte))(equalTo((5: Byte, 2: Short, 3, 4L))) &&
        assert(Tuple4._2.replace(value, 5: Short))(equalTo((1: Byte, 5: Short, 3, 4L))) &&
        assert(Tuple4._3.replace(value, 5))(equalTo((1: Byte, 2: Short, 5, 4L))) &&
        assert(Tuple4._4.replace(value, 5L))(equalTo((1: Byte, 2: Short, 3, 5L))) &&
        assert(Tuple4.schema.fromDynamicValue(Tuple4.schema.toDynamicValue(value)))(isRight(equalTo(value))) &&
        assert(Tuple4.schema)(
          equalTo(
            new Schema[Tuple4](
              reflect = Reflect.Record[Binding, Tuple4](
                fields = Vector(
                  Schema[Byte].reflect.asTerm("_1"),
                  Schema[Short].reflect.asTerm("_2"),
                  Schema[Int].reflect.asTerm("_3"),
                  Schema[Long].reflect.asTerm("_4")
                ),
                typeId = TypeId.derived[(Byte, Short, Int, Long)],
                recordBinding = null
              )
            )
          )
        )
      },
      test("derives schema for generic tuples") {
        type GenericTuple4 = Byte *: Short *: Int *: Long *: EmptyTuple

        object GenericTuple4 extends CompanionOptics[GenericTuple4] {
          implicit val schema: Schema[GenericTuple4] = Schema.derived
          val _1: Lens[GenericTuple4, Byte]          = $(_(0))
          val _2: Lens[GenericTuple4, Short]         = $(_.apply(1))
          val _3: Lens[GenericTuple4, Int]           = $(_(2))
          val _4: Lens[GenericTuple4, Long]          = $(_.apply(3))
        }

        val record = GenericTuple4.schema.reflect.asRecord
        val value  = (1: Byte) *: (2: Short) *: 3 *: 4L *: EmptyTuple
        assert(record.map(_.constructor.usedRegisters))(
          isSome(equalTo(RegisterOffset(bytes = 1, shorts = 1, ints = 1, longs = 1)))
        ) &&
        assert(GenericTuple4._1.get(value))(equalTo(1: Byte)) &&
        assert(GenericTuple4._2.get(value))(equalTo(2: Short)) &&
        assert(GenericTuple4._3.get(value))(equalTo(3)) &&
        assert(GenericTuple4._4.get(value))(equalTo(4L)) &&
        assert(GenericTuple4._1.replace(value, 5: Byte))(equalTo((5: Byte) *: (2: Short) *: 3 *: 4L *: EmptyTuple)) &&
        assert(GenericTuple4._2.replace(value, 5: Short))(equalTo((1: Byte) *: (5: Short) *: 3 *: 4L *: EmptyTuple)) &&
        assert(GenericTuple4._3.replace(value, 5))(equalTo((1: Byte) *: (2: Short) *: 5 *: 4L *: EmptyTuple)) &&
        assert(GenericTuple4._4.replace(value, 5L))(equalTo((1: Byte) *: (2: Short) *: 3 *: 5L *: EmptyTuple)) &&
        assert(GenericTuple4.schema.fromDynamicValue(GenericTuple4.schema.toDynamicValue(value)))(
          isRight(equalTo(value))
        ) &&
        assert(GenericTuple4.schema)(
          equalTo(
            new Schema[GenericTuple4](
              reflect = Reflect.Record[Binding, GenericTuple4](
                fields = Vector(
                  Schema[Byte].reflect.asTerm("_1"),
                  Schema[Short].reflect.asTerm("_2"),
                  Schema[Int].reflect.asTerm("_3"),
                  Schema[Long].reflect.asTerm("_4")
                ),
                typeId = TypeId.derived[(Byte, Short, Int, Long)],
                recordBinding = null
              )
            )
          )
        )
      },
      test("derives schema for complex generic tuples") {
        val value1 = (1, "VVV")
        val value2 = ((1, 2L), ("VVV", "WWW"))
        val value3 = (Some(1), Some("VVV"))
        val value4 = EmptyTuple

        val schema1: Schema[Tuple.Tail[(Long, Int, String)]]                         = Schema.derived
        val schema2: Schema[Tuple.Init[(Int, String, Long)]]                         = Schema.derived
        val schema3: Schema[Tuple.Drop[(Long, Int, String), 1]]                      = Schema.derived
        val schema4: Schema[Tuple.Take[(Int, String, Long), 2]]                      = Schema.derived
        val schema5: Schema[Tuple.Concat[Tuple1[Int], Tuple1[String]]]               = Schema.derived
        val schema6: Schema[Tuple.Append[Tuple1[Int], String]]                       = Schema.derived
        val schema7: Schema[Tuple.InverseMap[(Option[Int], Option[String]), Option]] = Schema.derived
        val schema8: Schema[Int *: Tuple1[String]]                                   = Schema.derived
        val schema9: Schema[Tuple.Zip[(Int, String), (Long, String)]]                = Schema.derived
        val schema10: Schema[Tuple.Map[(Int, String), Option]]                       = Schema.derived
        val schema11: Schema[EmptyTuple]                                             = Schema.derived
        val schema12: Schema[Tuple.Drop[(Long, Int, String), 3]]                     = Schema.derived
        assert(schema1)(
          equalTo(
            new Schema[(Int, String)](
              reflect = Reflect.Record[Binding, (Int, String)](
                fields = Vector(
                  Schema[Int].reflect.asTerm("_1"),
                  Schema[String].reflect.asTerm("_2")
                ),
                typeId = TypeId.derived[(Int, String)],
                recordBinding = null
              )
            )
          )
        ) &&
        assert(schema1)(equalTo(schema2)) &&
        assert(schema1)(equalTo(schema3)) &&
        assert(schema1)(equalTo(schema4)) &&
        assert(schema1)(equalTo(schema5)) &&
        assert(schema1)(equalTo(schema6)) &&
        assert(schema1)(equalTo(schema7)) &&
        assert(schema1)(equalTo(schema8)) &&
        assert(schema9)(
          equalTo(
            new Schema[((Int, Long), (String, String))](
              reflect = Reflect.Record[Binding, ((Int, Long), (String, String))](
                fields = Vector(
                  Schema.derived[(Int, Long)].reflect.asTerm("_1"),
                  Schema.derived[(String, String)].reflect.asTerm("_2")
                ),
                typeId = TypeId.derived[((Int, Long), (String, String))],
                recordBinding = null
              )
            )
          )
        ) &&
        assert(schema10)(
          equalTo(
            new Schema[(Int, String)](
              reflect = Reflect.Record[Binding, (Int, String)](
                fields = Vector(
                  Schema[Option[Int]].reflect.asTerm("_1"),
                  Schema[Option[String]].reflect.asTerm("_2")
                ),
                typeId = TypeId.derived[(Int, String)],
                recordBinding = null
              )
            )
          )
        ) &&
        assert(schema11)(
          equalTo(
            new Schema[EmptyTuple](
              reflect = Reflect.Record[Binding, EmptyTuple](
                fields = Vector(),
                typeId = TypeId.derived[EmptyTuple],
                recordBinding = null
              )
            )
          )
        ) &&
        assert(schema12)(equalTo(schema11)) &&
        assert(schema1.fromDynamicValue(schema1.toDynamicValue(value1)))(isRight(equalTo(value1))) &&
        assert(schema2.fromDynamicValue(schema2.toDynamicValue(value1)))(isRight(equalTo(value1))) &&
        assert(schema3.fromDynamicValue(schema3.toDynamicValue(value1)))(isRight(equalTo(value1))) &&
        assert(schema4.fromDynamicValue(schema4.toDynamicValue(value1)))(isRight(equalTo(value1))) &&
        assert(schema5.fromDynamicValue(schema5.toDynamicValue(value1)))(isRight(equalTo(value1))) &&
        assert(schema6.fromDynamicValue(schema6.toDynamicValue(value1)))(isRight(equalTo(value1))) &&
        assert(schema7.fromDynamicValue(schema7.toDynamicValue(value1)))(isRight(equalTo(value1))) &&
        assert(schema9.fromDynamicValue(schema9.toDynamicValue(value2)))(isRight(equalTo(value2))) &&
        assert(schema10.fromDynamicValue(schema10.toDynamicValue(value3)))(isRight(equalTo(value3))) &&
        assert(schema11.fromDynamicValue(schema11.toDynamicValue(value4)))(isRight(equalTo(value4))) &&
        assert(schema12.fromDynamicValue(schema12.toDynamicValue(value4)))(isRight(equalTo(value4)))
      },
      test("derives schema for nested generic records") {
        case class Parent(child: Child[MySealedTrait]) derives Schema

        case class Child[T <: MySealedTrait](test: T) derives Schema

        sealed trait MySealedTrait derives Schema

        object MySealedTrait {
          case class Foo(foo: Int) extends MySealedTrait

          case class Bar(bar: String) extends MySealedTrait
        }

        val value1 = Parent(Child(MySealedTrait.Foo(1)))
        val value2 = Parent(Child(MySealedTrait.Bar("WWW")))
        val schema = Schema[Parent]
        assert(schema.fromDynamicValue(schema.toDynamicValue(value1)))(isRight(equalTo(value1))) &&
        assert(schema.fromDynamicValue(schema.toDynamicValue(value2)))(isRight(equalTo(value2)))
      },
      test("derives schema for case class with opaque subtype fields") {
        import Id.schema

        val value1 = Opaque(Id.applyUnsafe("VVV"), Value(1))
        val value2 = Opaque(Id.applyUnsafe("!!!"), Value(1))
        val schema = Schema[Opaque]
        assert(schema)(
          equalTo(
            new Schema[Opaque](
              reflect = Reflect.Record[Binding, Opaque](
                fields = Vector(
                  Schema[Id].reflect.asTerm("id"),
                  Schema.derived[Value].reflect.asTerm("value")
                ),
                typeId = TypeId.derived[Opaque],
                recordBinding = null
              )
            )
          )
        ) &&
        assert(Opaque.id.get(value1))(equalTo(Id.applyUnsafe("VVV"))) &&
        assert(Opaque.id_wrapped.getOption(value2))(isSome(equalTo("!!!"))) &&
        assert(Opaque.value.get(value1))(equalTo(Value(1))) &&
        assert(Opaque.id.replace(value1, Id.applyUnsafe("!!!")))(equalTo(value2)) &&
        assert(Opaque.id_wrapped.replace(value1, "!!!"))(equalTo(value1)) &&
        assert(schema.fromDynamicValue(schema.toDynamicValue(value1)))(isRight(equalTo(value1))) &&
        assert(schema.fromDynamicValue(schema.toDynamicValue(value2)))(
          isLeft(
            hasField[SchemaError, String](
              "getMessage",
              _.getMessage,
              containsString("Expected a string with letter or digit characters")
            )
          )
        )
      },
      test("derives schema for case class with inner opaque type fields") {
        import InnerId.schema

        val value1 = InnerOpaque(InnerId.applyUnsafe("VVV"), InnerValue(1))
        val value2 = InnerOpaque(InnerId.applyUnsafe("!!!"), InnerValue(1))
        val schema = Schema[InnerOpaque]
        assert(schema)(
          equalTo(
            new Schema[InnerOpaque](
              reflect = Reflect.Record[Binding, InnerOpaque](
                fields = Vector(
                  Schema[InnerId].reflect.asTerm("id"),
                  Schema[Int].reflect
                    .typeId(TypeId.derived[InnerValue])
                    .asTerm("value")
                ),
                typeId = TypeId.derived[InnerOpaque],
                recordBinding = null
              )
            )
          )
        ) &&
        assert(InnerOpaque.id.get(value1))(equalTo(InnerId.applyUnsafe("VVV"))) &&
        assert(InnerOpaque.id_wrapped.getOption(value2))(isSome(equalTo("!!!"))) &&
        assert(InnerOpaque.value.get(value1))(equalTo(InnerValue(1))) &&
        assert(InnerOpaque.id.replace(value1, InnerId.applyUnsafe("!!!")))(equalTo(value2)) &&
        assert(InnerOpaque.id_wrapped.replace(value1, "!!!"))(equalTo(value1)) &&
        assert(schema.fromDynamicValue(schema.toDynamicValue(value1)))(isRight(equalTo(value1))) &&
        assert(schema.fromDynamicValue(schema.toDynamicValue(value2)))(
          isLeft(
            hasField[SchemaError, String](
              "getMessage",
              _.getMessage,
              containsString("Expected a string with letter or digit characters")
            )
          )
        )
      },
      test("derives schema for tuples with more than 22 fields") {
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
          Int,
          Int,
          Int,
          Int,
          Int,
          Int,
          Int,
          InnerValue,
          InnerId,
          Value,
          Id,
          Box1,
          Box2,
          Int,
          String
        )

        object Tuple24 extends CompanionOptics[Tuple24] {
          implicit val schema: Schema[Tuple24] = Schema.derived
          val i17: Lens[Tuple24, InnerValue]   = $(_(16))
          val i18: Lens[Tuple24, InnerId]      = $(_.apply(17))
          val o19: Lens[Tuple24, Value]        = $(_(18))
          val o20: Lens[Tuple24, Id]           = $(_.apply(19))
          val b21: Lens[Tuple24, Box1]         = $(_(20))
          val b22: Lens[Tuple24, Box2]         = $(_.apply(21))
          val i23: Lens[Tuple24, Int]          = $(_(22))
          val s24: Lens[Tuple24, String]       = $(_.apply(23))
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
          10,
          11,
          12,
          13,
          14,
          15,
          16,
          InnerValue(17),
          InnerId.applyUnsafe("18"),
          Value(19),
          Id.applyUnsafe("20"),
          Box1(21L),
          Box2("22"),
          23,
          "24"
        )
        val offset = RegisterOffset(
          ints = 11,
          floats = 1,
          longs = 1,
          doubles = 1,
          booleans = 1,
          bytes = 1,
          chars = 1,
          shorts = 1,
          objects = 5
        )
        assert(record.map(_.constructor.usedRegisters))(isSome(equalTo(offset))) &&
        assert(record.map(_.deconstructor.usedRegisters))(isSome(equalTo(offset))) &&
        assert(Tuple24.i17.get(value))(equalTo(InnerValue(17))) &&
        assert(Tuple24.i18.get(value))(equalTo(InnerId.applyUnsafe("18"))) &&
        assert(Tuple24.o19.get(value))(equalTo(Value(19))) &&
        assert(Tuple24.o20.get(value))(equalTo(Id.applyUnsafe("20"))) &&
        assert(Tuple24.b21.get(value))(equalTo(Box1(21L))) &&
        assert(Tuple24.b22.get(value))(equalTo(Box2("22"))) &&
        assert(Tuple24.i23.get(value))(equalTo(23)) &&
        assert(Tuple24.s24.get(value))(equalTo("24")) &&
        assert(Tuple24.schema.fromDynamicValue(Tuple24.schema.toDynamicValue(value)))(isRight(equalTo(value)))
      }
    ),
    suite("Reflect.Sequence")(
      test("derives schema for IArray") {
        val schema1                                 = Schema.derived[IArray[Int]]
        val schema2                                 = Schema.derived[IArray[Long]]
        val schema3                                 = Schema.derived[IArray[Char]]
        val schema4                                 = Schema.derived[IArray[String]]
        val schema5                                 = Schema.derived[IArray[Boolean]]
        val schema6                                 = Schema.derived[IArray[Byte]]
        val schema7                                 = Schema.derived[IArray[Short]]
        val schema8                                 = Schema.derived[IArray[Float]]
        val schema9                                 = Schema.derived[IArray[Double]]
        val traversal1: Traversal[IArray[Int], Int] =
          Traversal.seqValues(
            schema1.reflect.asSequenceUnknown.get.sequence.asInstanceOf[Reflect.Sequence[Binding, Int, IArray]]
          )
        val traversal2: Traversal[IArray[Long], Long] =
          Traversal.seqValues(
            schema2.reflect.asSequenceUnknown.get.sequence.asInstanceOf[Reflect.Sequence[Binding, Long, IArray]]
          )
        val traversal3: Traversal[IArray[Char], Char] =
          Traversal.seqValues(
            schema3.reflect.asSequenceUnknown.get.sequence.asInstanceOf[Reflect.Sequence[Binding, Char, IArray]]
          )
        val traversal4: Traversal[IArray[String], String] =
          Traversal.seqValues(
            schema4.reflect.asSequenceUnknown.get.sequence.asInstanceOf[Reflect.Sequence[Binding, String, IArray]]
          )
        val traversal5: Traversal[IArray[Boolean], Boolean] =
          Traversal.seqValues(
            schema5.reflect.asSequenceUnknown.get.sequence.asInstanceOf[Reflect.Sequence[Binding, Boolean, IArray]]
          )
        val traversal6: Traversal[IArray[Byte], Byte] =
          Traversal.seqValues(
            schema6.reflect.asSequenceUnknown.get.sequence.asInstanceOf[Reflect.Sequence[Binding, Byte, IArray]]
          )
        val traversal7: Traversal[IArray[Short], Short] =
          Traversal.seqValues(
            schema7.reflect.asSequenceUnknown.get.sequence.asInstanceOf[Reflect.Sequence[Binding, Short, IArray]]
          )
        val traversal8: Traversal[IArray[Float], Float] =
          Traversal.seqValues(
            schema8.reflect.asSequenceUnknown.get.sequence.asInstanceOf[Reflect.Sequence[Binding, Float, IArray]]
          )
        val traversal9: Traversal[IArray[Double], Double] =
          Traversal.seqValues(
            schema9.reflect.asSequenceUnknown.get.sequence.asInstanceOf[Reflect.Sequence[Binding, Double, IArray]]
          )
        assert(schema1.reflect.typeId)(equalTo(TypeId.derived[IArray[Int]])) &&
        assert(schema2.reflect.typeId)(equalTo(TypeId.derived[IArray[Long]])) &&
        assert(schema3.reflect.typeId)(equalTo(TypeId.derived[IArray[Char]])) &&
        assert(schema4.reflect.typeId)(equalTo(TypeId.derived[IArray[String]])) &&
        assert(schema5.reflect.typeId)(equalTo(TypeId.derived[IArray[Boolean]])) &&
        assert(schema6.reflect.typeId)(equalTo(TypeId.derived[IArray[Byte]])) &&
        assert(schema7.reflect.typeId)(equalTo(TypeId.derived[IArray[Short]])) &&
        assert(schema8.reflect.typeId)(equalTo(TypeId.derived[IArray[Float]])) &&
        assert(schema9.reflect.typeId)(equalTo(TypeId.derived[IArray[Double]])) &&
        assert(schema1.fromDynamicValue(schema1.toDynamicValue(IArray(1, 2, 3))).map(_.toSeq))(
          isRight(equalTo(Seq(1, 2, 3)))
        ) &&
        assert(schema2.fromDynamicValue(schema2.toDynamicValue(IArray(1L, 2L, 3L))).map(_.toSeq))(
          isRight(equalTo(Seq(1L, 2L, 3L)))
        ) &&
        assert(schema3.fromDynamicValue(schema3.toDynamicValue(IArray('1', '2', '3'))).map(_.toSeq))(
          isRight(equalTo(Seq('1', '2', '3')))
        ) &&
        assert(schema4.fromDynamicValue(schema4.toDynamicValue(IArray("1", "2", "3"))).map(_.toSeq))(
          isRight(equalTo(Seq("1", "2", "3")))
        ) &&
        assert(schema5.fromDynamicValue(schema5.toDynamicValue(IArray(true, false, true))).map(_.toSeq))(
          isRight(equalTo(Seq(true, false, true)))
        ) &&
        assert(schema6.fromDynamicValue(schema6.toDynamicValue(IArray(1: Byte, 2: Byte, 3: Byte))).map(_.toSeq))(
          isRight(equalTo(Seq(1: Byte, 2: Byte, 3: Byte)))
        ) &&
        assert(schema7.fromDynamicValue(schema7.toDynamicValue(IArray(1: Short, 2: Short, 3: Short))).map(_.toSeq))(
          isRight(equalTo(Seq(1: Short, 2: Short, 3: Short)))
        ) &&
        assert(schema8.fromDynamicValue(schema8.toDynamicValue(IArray(1.0f, 2.0f, 3.0f))).map(_.toSeq))(
          isRight(equalTo(Seq(1.0f, 2.0f, 3.0f)))
        ) &&
        assert(schema9.fromDynamicValue(schema9.toDynamicValue(IArray(1.0, 2.0, 3.0))).map(_.toSeq))(
          isRight(equalTo(Seq(1.0, 2.0, 3.0)))
        ) &&
        assert(traversal1.fold(IArray(1, 2, 3))(0, _ + _))(equalTo(6)) &&
        assert(traversal2.fold(IArray(1L, 2L, 3L))(0L, _ + _))(equalTo(6L)) &&
        assert(traversal3.fold(IArray('1', '2', '3'))("", _ + _))(equalTo("123")) &&
        assert(traversal4.fold(IArray("1", "2", "3"))("", _ + _))(equalTo("123")) &&
        assert(traversal5.fold(IArray(true, false, true))(false, _ | _))(equalTo(true)) &&
        assert(traversal6.fold(IArray(1: Byte, 2: Byte, 3: Byte))(0, _ + _))(equalTo(6)) &&
        assert(traversal7.fold(IArray(1: Short, 2: Short, 3: Short))(0, _ + _))(equalTo(6)) &&
        assert(traversal8.fold(IArray(1.0f, 2.0f, 3.0f))(0.0f, _ + _))(equalTo(6.0f)) &&
        assert(traversal9.fold(IArray(1.0, 2.0, 3.0))(0.0, _ + _))(equalTo(6.0))
      },
      test("has consistent newObjectBuilder, addObject and resultObject") {
        val schema      = Schema.derived[IArray[Int]]
        val constructor = schema.reflect.asSequence.get.seqBinding.asInstanceOf[Binding.Seq[IArray, Int]].constructor
        val xs          = constructor.newObjectBuilder[Int](0)
        constructor.addObject(xs, 1)
        constructor.addObject(xs, 2)
        constructor.addObject(xs, 3)
        assert(constructor.resultObject(xs))(equalTo(Array(1, 2, 3)))
      },
      test("derives schema for array and IArray of opaque sub-types") {
        assert(Schema.derived[Array[StructureId]])(equalTo(Schema.derived[Array[String]])) &&
        assert(Schema.derived[IArray[StructureId]])(equalTo(Schema.derived[IArray[String]]))
      },
      test("doesn't generate schema for unsupported collections") {
        typeCheck {
          "Schema.derived[scala.collection.mutable.CollisionProofHashMap[String, Int]]"
        }.map(
          assert(_)(
            isLeft(
              containsString(
                "Cannot derive schema for 'scala.collection.mutable.CollisionProofHashMap[scala.Predef.String, scala.Int]'."
              )
            )
          )
        )
      }
    ),
    suite("Reflect.Variant")(
      test("derives schema for sealed traits using 'derives' keyword") {

        /** Variant: Variant1 */
        sealed trait Variant1 derives Schema

        /** Case: Case1 */
        case class Case1(d: Double) extends Variant1 derives Schema

        /** Case: Case2 */
        case class Case2() extends Variant1 derives Schema

        /** Case: Case3 */
        case object Case3 extends Variant1 derives Schema

        object Variant1 extends CompanionOptics[Variant1] {
          val case1: Prism[Variant1, Case1]      = optic(_.when[Case1])
          val case2: Prism[Variant1, Case2]      = optic(_.when[Case2])
          val case3: Prism[Variant1, Case3.type] = optic(_.when[Case3.type])
        }

        val schema  = Schema[Variant1]
        val variant = schema.reflect.asVariant
        assert(Variant1.case1.getOption(Case1(0.1)))(isSome(equalTo(Case1(0.1)))) &&
        assert(Variant1.case2.getOption(Case2()))(isSome(equalTo(Case2()))) &&
        assert(Variant1.case3.getOption(Case3))(isSome(equalTo(Case3))) &&
        assert(Variant1.case1.replace(Case1(0.1), Case1(0.2)))(equalTo(Case1(0.2))) &&
        assert(Variant1.case2.replace(Case2(), Case2()))(equalTo(Case2())) &&
        assert(Variant1.case3.replace(Case3, Case3))(equalTo(Case3)) &&
        assert(schema.fromDynamicValue(schema.toDynamicValue(Case1(0.1))))(isRight(equalTo(Case1(0.1)))) &&
        assert(schema.fromDynamicValue(schema.toDynamicValue(Case2())))(isRight(equalTo(Case2()))) &&
        assert(schema.fromDynamicValue(schema.toDynamicValue(Case3)))(isRight(equalTo(Case3))) &&
        assert(variant.map(_.cases.map(_.name)))(isSome(equalTo(Vector("Case1", "Case2", "Case3")))) &&
        assert(variant.map(_.typeId))(
          isSome(equalTo(TypeId.derived[Variant1]))
        ) &&
        assert(variant.map(_.doc))(isSome(equalTo(Doc("/** Variant: Variant1 */"))))
      },
      test("derives schema for Scala 3 enums using 'derives' keyword") {
        val schema  = Schema[Color]
        val variant = schema.reflect.asVariant
        val record1 = variant.flatMap(_.cases(0).value.asRecord)
        val record2 = variant.flatMap(_.cases(1).value.asRecord)
        val record3 = variant.flatMap(_.cases(2).value.asRecord)
        val record4 = variant.flatMap(_.cases(3).value.asRecord)
        assert(record1.map(_.modifiers))(
          isSome(
            equalTo(Seq(Modifier.config("term-key-1", "term-value-1"), Modifier.config("term-key-1", "term-value-2")))
          )
        ) &&
        assert(record2.map(_.modifiers))(
          isSome(
            equalTo(Seq(Modifier.config("term-key-2", "term-value-1"), Modifier.config("term-key-2", "term-value-2")))
          )
        ) &&
        assert(record3.map(_.modifiers))(
          isSome(
            equalTo(Seq(Modifier.config("term-key-3", "term-value-1"), Modifier.config("term-key-3", "term-value-2")))
          )
        ) &&
        assert(record4.map(_.modifiers))(
          isSome(
            equalTo(Seq(Modifier.config("type-key", "type-value-1"), Modifier.config("type-key", "type-value-2")))
          )
        ) &&
        assert(record1.map(_.doc))(isSome(equalTo(Doc("/** Term: Red */")))) &&
        assert(record2.map(_.doc))(isSome(equalTo(Doc("/** Term: Green */")))) &&
        assert(record3.map(_.doc))(isSome(equalTo(Doc("/** Term: Blue */")))) &&
        assert(record4.map(_.doc))(isSome(equalTo(Doc("/** Type: Mix */")))) &&
        assert(Color.red.getOption(Color.Red))(isSome(equalTo(Color.Red))) &&
        assert(Color.mix.getOption(Color.Mix(0xffffff)))(isSome(equalTo(Color.Mix(0xffffff)))) &&
        assert(Color.mix_mix.getOption(Color.Mix(0xffffff)))(isSome(equalTo(0xffffff))) &&
        assert(Color.red.replace(Color.Red, Color.Red))(equalTo(Color.Red)) &&
        assert(Color.mix.replace(Color.Mix(0xffffff), Color.Mix(0x000000)))(equalTo(Color.Mix(0x000000))) &&
        assert(Color.mix_mix.replace(Color.Mix(0xffffff), 0x000000))(equalTo(Color.Mix(0x000000))) &&
        assert(schema.fromDynamicValue(schema.toDynamicValue(Color.Red)))(isRight(equalTo(Color.Red))) &&
        assert(schema.fromDynamicValue(schema.toDynamicValue(Color.Green)))(isRight(equalTo(Color.Green))) &&
        assert(schema.fromDynamicValue(schema.toDynamicValue(Color.Blue)))(isRight(equalTo(Color.Blue))) &&
        assert(schema.fromDynamicValue(schema.toDynamicValue(Color.Mix(0xff7733))))(
          isRight(equalTo(Color.Mix(0xff7733)))
        ) &&
        assert(variant.map(_.cases.map(_.name)))(isSome(equalTo(Vector("Red", "Green", "Blue", "Mix")))) &&
        assert(variant.map(_.typeId))(
          isSome(equalTo(TypeId.derived[Color]))
        ) &&
        assert(variant.map(_.doc))(isSome(equalTo(Doc("/** Variant: Color */"))))
      },
      test("derives schema for one case enums using 'derives' keyword") {
        val schema  = Schema[OneCaseEnum]
        val variant = schema.reflect.asVariant
        assert(variant.map(_.cases(0).name))(isSome(equalTo("Case1")))
      },
      test("derives schema for options of opaque sub-types") {
        val schema = Schema.derived[Option[StructureId]]
        assert(schema.reflect.typeId)(equalTo(TypeId.derived[Option[StructureId]]))
      },
      test("derives schema for type recursive Scala 3 enums") {
        val schema  = Schema.derived[FruitEnum[?]]
        val variant = schema.reflect.asVariant
        assert(schema.fromDynamicValue(schema.toDynamicValue(FruitEnum.Apple("red"))))(
          isRight(equalTo(FruitEnum.Apple("red")))
        ) &&
        assert(schema.fromDynamicValue(schema.toDynamicValue(FruitEnum.Banana(0.5))))(
          isRight(equalTo(FruitEnum.Banana(0.5)))
        ) &&
        assert(variant.map(_.cases.map(_.name)))(isSome(equalTo(Vector("Apple", "Banana")))) &&
        assert(variant.map(_.typeId))(
          isSome(equalTo(TypeId.derived[FruitEnum[?]]))
        )
      },
      test("derives schema for Scala 3 unions") {
        type Value = Int | Boolean | (Int, Boolean) | List[Int] | Map[Int, Long]

        implicit val schema: Schema[Value] = Schema.derived

        object Value extends CompanionOptics[Value] {
          val int: Prism[Value, Int]              = $(_.when[Int])
          val boolean: Prism[Value, Boolean]      = $(_.when[Boolean])
          val tuple: Prism[Value, (Int, Boolean)] = $(_.when[(Int, Boolean)])
          val tuple_1: Optional[Value, Int]       = $(_.when[(Int, Boolean)](0))
          val li: Prism[Value, List[Int]]         = $(_.when[List[Int]])
          val li_1: Optional[Value, Int]          = $(_.when[List[Int]].at(0))
          val mil: Prism[Value, Map[Int, Long]]   = $(_.when[Map[Int, Long]])
          val mil_1: Optional[Value, Long]        = $(_.when[Map[Int, Long]].atKey(1))
        }

        val variant = schema.reflect.asVariant
        assert(Value.int.getOption(123))(isSome(equalTo(123))) &&
        assert(Value.boolean.getOption(true))(isSome(equalTo(true))) &&
        assert(Value.tuple.getOption(true))(isNone) &&
        assert(Value.tuple_1.getOption((1, true)))(isSome(equalTo(1))) &&
        assert(Value.li.getOption(List(1, 2, 3)))(isSome(equalTo(List(1, 2, 3)))) &&
        assert(Value.li_1.getOption(List(1, 2, 3)))(isSome(equalTo(1))) &&
        assert(Value.mil.getOption(Map(1 -> 1L, 2 -> 2L, 3 -> 3L)))(isSome(equalTo(Map(1 -> 1L, 2 -> 2L, 3 -> 3L)))) &&
        assert(Value.mil_1.getOption(Map(1 -> 1L, 2 -> 2L, 3 -> 3L)))(isSome(equalTo(1L))) &&
        assert(Value.int.replace(123, 321))(equalTo(321)) &&
        assert(Value.boolean.replace(true, false))(equalTo(false)) &&
        assert(Value.tuple.replace((1, true), (1, false)))(equalTo((1, false))) &&
        assert(Value.tuple_1.replace((1, true), 2))(equalTo((2, true))) &&
        assert(schema.fromDynamicValue(schema.toDynamicValue(123)))(isRight(equalTo(123))) &&
        assert(schema.fromDynamicValue(schema.toDynamicValue(true)))(isRight(equalTo(true))) &&
        assert(schema)(equalTo(Schema.derived[Int | Boolean | (Int, Boolean) | List[Int] | Map[Int, Long]])) &&
        assert(schema)(not(equalTo(Schema.derived[Boolean | Int]))) &&
        assert(variant.map(_.cases.map(_.name)))(
          isSome(equalTo(Vector("Int", "Boolean", "Tuple2", "collection.immutable.List", "collection.immutable.Map")))
        ) &&
        assert(variant.map(_.typeId.name))(isSome(equalTo("Union")))
      },
      test("derives schema for Scala 3 unions defined as opaque types") {
        val schema  = Schema.derived[Variant]
        val variant = schema.reflect.asVariant
        assert(
          Variant(123) match {
            case _: Int => true
            case _      => false
          }
        )(equalTo(true)) &&
        assert(schema)(not(equalTo(Schema.derived[Int | String | Boolean]))) && // the difference in type names
        assert(schema.fromDynamicValue(schema.toDynamicValue(Variant(123))))(isRight(equalTo(Variant(123)))) &&
        assert(schema.fromDynamicValue(schema.toDynamicValue(Variant(true))))(isRight(equalTo(Variant(true)))) &&
        assert(schema.fromDynamicValue(schema.toDynamicValue(Variant("VVV"))))(isRight(equalTo(Variant("VVV")))) &&
        assert(variant.map(_.cases.map(_.name)))(
          isSome(equalTo(Vector("scala.Int", "java.lang.String", "scala.Boolean")))
        ) &&
        assert(variant.map(_.typeId))(
          isSome(equalTo(TypeId.derived[Variant]))
        )
      },
      test("derives schema for case classes with fields of Scala 3 union types that have duplicated sub-types") {
        type Value1 = Int | Boolean
        type Value2 = Int | String | Int

        case class Unions(v1: Value1, v2: Value2, v3: Value1 | Value2)

        implicit val schema: Schema[Unions] = Schema.derived

        object Unions extends CompanionOptics[Unions] {
          val v1: Lens[Unions, Value1]          = $(_.v1)
          val v2: Lens[Unions, Value2]          = $(_.v2)
          val v3: Lens[Unions, Value1 | Value2] = $(_.v3)
          val v3_s: Optional[Unions, String]    = $(_.v3.when[String])
        }

        val value1 = Unions(123, 321, "VVV")
        val value2 = Unions(true, "VVV", 213)
        val record = schema.reflect.asRecord
        assert(Unions.v1.get(value2))(equalTo(true)) &&
        assert(Unions.v2.get(value2))(equalTo("VVV")) &&
        assert(Unions.v3.get(value2))(equalTo(213)) &&
        assert(Unions.v3_s.getOption(value1))(isSome(equalTo("VVV"))) &&
        assert(record.flatMap(_.fields(1).value.asVariant.map(_.cases.map(_.name))))(
          isSome(equalTo(Seq("scala.Int", "java.lang.String")))
        ) &&
        assert(record.flatMap(_.fields(2).value.asVariant.map(_.cases.map(_.name))))(
          isSome(equalTo(Seq("scala.Int", "scala.Boolean", "java.lang.String")))
        ) &&
        assert(schema.fromDynamicValue(schema.toDynamicValue(value1)))(isRight(equalTo(value1))) &&
        assert(schema.fromDynamicValue(schema.toDynamicValue(value2)))(isRight(equalTo(value2)))
      },
      test("derives schema for recursive generic Scala 3 enums") {
        val schema  = Schema.derived[LinkedList[Int]]
        val variant = schema.reflect.asVariant
        assert(schema.fromDynamicValue(schema.toDynamicValue(LinkedList.Node(2, LinkedList.Node(1, LinkedList.End)))))(
          isRight(equalTo(LinkedList.Node(2, LinkedList.Node(1, LinkedList.End))))
        ) &&
        assert(variant.map(_.cases.map(_.name)))(isSome(equalTo(Vector("End", "Node")))) &&
        assert(variant.flatMap(_.cases(1).value.asRecord.map(_.fields(1).modifiers)))(
          isSome(
            equalTo(Seq(Modifier.config("field-key", "field-value")))
          )
        ) &&
        assert(variant.map(_.typeId))(
          isSome(equalTo(TypeId.derived[LinkedList[Int]]))
        )
      },
      test("derives schema for higher-kinded Scala 3 enums") {
        val schema  = Schema.derived[HKEnum[Option]]
        val variant = schema.reflect.asVariant
        assert(schema.fromDynamicValue(schema.toDynamicValue(HKEnum.Case1(Some(1)))))(
          isRight(equalTo(HKEnum.Case1(Some(1))))
        ) &&
        assert(schema.fromDynamicValue(schema.toDynamicValue(HKEnum.Case2(Some("WWW")))))(
          isRight(equalTo(HKEnum.Case2(Some("WWW"))))
        ) &&
        assert(variant.map(_.cases.map(_.name)))(isSome(equalTo(Vector("Case1", "Case2")))) &&
        assert(variant.map(_.typeId))(
          isSome(equalTo(TypeId.derived[HKEnum[Option]]))
        )
      },
      test("doesn't generate codecs for non-concrete ADTs with at least one free type parameter") {
        typeCheck {
          """sealed trait TypeBase[T]

             object TypeBase {
               given TypeBase[Int] = new TypeBase[Int] {}
               given TypeBase[String] = new TypeBase[String] {}
             }

             sealed trait Base[T: TypeBase] {
               val t: T
             }

             case class A[T: TypeBase](a: T) extends Base[T] {
               override val t: T = a
             }

             case class B[T: TypeBase](b: T) extends Base[T] {
               override val t: T = b
             }

             Schema.derived[Base[_]]"""
        }.map(
          assert(_)(
            isLeft(
              containsString(
                "Cannot resolve free type parameters for ADT cases with base 'Base[_ >: scala.Nothing <: scala.Any]'."
              )
            )
          )
        )
      }
    ),
    suite("withTypeName")(
      test("sets the correct TypeName on transformed schema") {
        case class Age(value: Int)
        val ageSchema: Schema[Age] = Schema[Int].transform(Age(_), _.value).withTypeName[Age]
        val wrapper                = ageSchema.reflect.asWrapperUnknown
        assert(wrapper.map(_.wrapper.typeName.name))(isSome(equalTo("Age")))
      },
      test("preserves transformation behavior after setting TypeName") {
        case class Age(value: Int)
        val ageSchema: Schema[Age] = Schema[Int].transform(Age(_), _.value).withTypeName[Age]
        val dv                     = Schema[Int].toDynamicValue(25)
        assert(ageSchema.fromDynamicValue(dv))(isRight(equalTo(Age(25))))
      }
    ),
    suite("asOpaqueType")(
      test("sets TypeName and primitive type on Wrapper reflect") {
        case class Score(value: Int)
        val scoreSchema: Schema[Score] = Schema[Int].transform(Score(_), _.value).asOpaqueType[Score]
        val wrapper                    = scoreSchema.reflect.asWrapperUnknown
        assert(wrapper.map(_.wrapper.typeName.name))(isSome(equalTo("Score"))) &&
        assert(wrapper.flatMap(_.wrapper.wrapperPrimitiveType))(isSome(equalTo(PrimitiveType.Int(Validation.None))))
      },
      test("round-trips correctly with asOpaqueType") {
        case class Score(value: Int)
        val scoreSchema: Schema[Score] = Schema[Int].transform(Score(_), _.value).asOpaqueType[Score]
        val value                      = Score(100)
        val dv                         = scoreSchema.toDynamicValue(value)
        assert(scoreSchema.fromDynamicValue(dv))(isRight(equalTo(value)))
      },
      test("falls back to withTypeName behavior when called on non-Wrapper reflect") {
        val intSchema: Schema[Int] = Schema[Int].asOpaqueType[Int]
        assert(intSchema.reflect.typeName.name)(equalTo("Int"))
      },
      test("works with Long primitive type") {
        case class LongWrapper(value: Long)
        val schema: Schema[LongWrapper] = Schema[Long].transform(LongWrapper(_), _.value).asOpaqueType[LongWrapper]
        val wrapper                     = schema.reflect.asWrapperUnknown
        assert(wrapper.flatMap(_.wrapper.wrapperPrimitiveType))(isSome(equalTo(PrimitiveType.Long(Validation.None))))
      },
      test("works with String primitive type") {
        case class StringWrapper(value: String)
        val schema: Schema[StringWrapper] =
          Schema[String].transform(StringWrapper(_), _.value).asOpaqueType[StringWrapper]
        val wrapper = schema.reflect.asWrapperUnknown
        assert(wrapper.flatMap(_.wrapper.wrapperPrimitiveType))(isSome(equalTo(PrimitiveType.String(Validation.None))))
      },
      test("works with Double primitive type") {
        case class DoubleWrapper(value: Double)
        val schema: Schema[DoubleWrapper] =
          Schema[Double].transform(DoubleWrapper(_), _.value).asOpaqueType[DoubleWrapper]
        val wrapper = schema.reflect.asWrapperUnknown
        assert(wrapper.flatMap(_.wrapper.wrapperPrimitiveType))(isSome(equalTo(PrimitiveType.Double(Validation.None))))
      }
    )
  )

  /** Variant: Color */
  enum Color(val rgb: Int) derives Schema {

    /** Term: Red */
    @Modifier.config("term-key-1", "term-value-1") @Modifier.config("term-key-1", "term-value-2")
    case Red extends Color(0xff0000)

    /** Term: Green */
    @Modifier.config("term-key-2", "term-value-1") @Modifier.config("term-key-2", "term-value-2")
    case Green extends Color(0x00ff00)

    /** Term: Blue */
    @Modifier.config("term-key-3", "term-value-1") @Modifier.config("term-key-3", "term-value-2")
    case Blue extends Color(0x0000ff)

    /** Type: Mix */
    @Modifier.config("type-key", "type-value-1") @Modifier.config("type-key", "type-value-2")
    case Mix(mix: Int) extends Color(mix)
  }

  object Color extends CompanionOptics[Color] {
    val red: Prism[Color, Color.Red.type]     = $(_.when[Color.Red.type])
    val green: Prism[Color, Color.Green.type] = $(_.when[Color.Green.type])
    val blue: Prism[Color, Color.Blue.type]   = $(_.when[Color.Blue.type])
    val mix: Prism[Color, Color.Mix]          = $(_.when[Color.Mix])
    val mix_mix: Optional[Color, Int]         = $(_.when[Color.Mix].mix)
  }

  enum FruitEnum[T <: FruitEnum[T]] {
    case Apple(color: String) extends FruitEnum[Apple]

    case Banana(curvature: Double) extends FruitEnum[Banana]
  }

  enum LinkedList[+T] {
    case End

    case Node(value: T, @Modifier.config("field-key", "field-value") next: LinkedList[T])
  }

  enum HKEnum[A[_]] {
    case Case1(a: A[Int]) extends HKEnum[A]

    case Case2(a: A[String]) extends HKEnum[A]
  }

  case class Box1(l: Long) extends AnyVal derives Schema

  case class Box2(s: String) extends AnyVal derives Schema

  opaque type InnerId <: String = String

  object InnerId {
    implicit val schema: Schema[InnerId] = Schema(
      Reflect.Wrapper(
        wrapped = Reflect.string[Binding], // Cannot use `Schema[String].reflect` here
        typeId = TypeId.derived[InnerId],
        wrapperPrimitiveType = Some(PrimitiveType.String(Validation.None)),
        wrapperBinding = Binding.Wrapper(s => InnerId(s).left.map(SchemaError.validationFailed), identity)
      )
    )

    def apply(s: String): Either[String, InnerId] =
      if (s.forall(_.isLetterOrDigit)) Right(s)
      else Left("Expected a string with letter or digit characters")

    def applyUnsafe(s: String): InnerId = s
  }

  opaque type OpaqueInt = Int

  opaque type InnerValue = OpaqueInt

  object InnerValue {
    inline def apply(i: Int): InnerValue = i

    extension (x: InnerValue) {
      inline def toInt: Int = x
    }
  }

  enum OneCaseEnum derives Schema { case Case1 }
}

case class InnerOpaque(id: InnerId, value: InnerValue) derives Schema

object InnerOpaque extends CompanionOptics[InnerOpaque] {
  val id: Lens[InnerOpaque, InnerId]            = $(_.id)
  val value: Lens[InnerOpaque, InnerValue]      = $(_.value)
  val id_wrapped: Optional[InnerOpaque, String] = $(_.id.wrapped[String])
}

opaque type UniqueId[+A] = String

trait SomeType

type Id = UniqueId[SomeType]

object Id {
  implicit val schema: Schema[Id] = Schema(
    Reflect.Wrapper(
      wrapped = Reflect.string[Binding], // Cannot use `Schema[String].reflect` here
      typeId = TypeId.derived[Id],
      wrapperPrimitiveType = Some(PrimitiveType.String(Validation.None)),
      wrapperBinding = Binding.Wrapper(s => Id(s).left.map(SchemaError.validationFailed), identity)
    )
  )

  def apply(s: String): Either[String, Id] =
    if (s.forall(_.isLetterOrDigit)) Right(s)
    else Left("Expected a string with letter or digit characters")

  def applyUnsafe(s: String): Id = s
}

opaque type Value <: Int = Int

object Value {
  inline def apply(i: Int): Value = i
}

import Id.schema

case class Opaque(id: Id, value: Value) derives Schema

object Opaque extends CompanionOptics[Opaque] {
  val id: Lens[Opaque, Id]                 = $(_.id)
  val value: Lens[Opaque, Value]           = $(_.value)
  val id_wrapped: Optional[Opaque, String] = $(_.id.wrapped[String])
}

@deprecated("reasons") case class C() derives Schema
