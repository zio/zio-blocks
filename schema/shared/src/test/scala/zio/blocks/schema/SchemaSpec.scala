package zio.blocks.schema

import zio.blocks.chunk.Chunk
import zio.blocks.schema.DynamicOptic.Node.{AtIndex, AtMapKey, Elements, MapValues}
import zio.blocks.schema.Reflect.Primitive
import zio.blocks.schema.SchemaError.{ExpectationMismatch, MissingField}
import zio.blocks.schema.binding._
import zio.blocks.schema.codec.{TextCodec, TextFormat}
import zio.blocks.schema.derive.Deriver
import zio.blocks.typeid.TypeId
import zio.test._
import zio.test.Assertion._
import java.nio.CharBuffer
import scala.collection.immutable.ArraySeq

object SchemaSpec extends SchemaBaseSpec {
  def spec: Spec[TestEnvironment, Any] = suite("SchemaSpec")(
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
      test("gets and updates primitive default value") {
        assert(Schema[Int].getDefaultValue)(isNone) &&
        assert(Schema[Int].defaultValue(1).getDefaultValue)(isSome(equalTo(1)))
      },
      test("gets and updates primitive documentation") {
        assert(Schema[Long].doc)(equalTo(Doc.Empty)) &&
        assert(Schema[Int].doc("Int (updated)").doc)(equalTo(Doc("Int (updated)")))
      },
      test("gets and updates primitive examples") {
        assert(Schema[Int].examples)(equalTo(Nil)) &&
        assert(Schema[Int].examples(1, 2, 3).examples)(equalTo(Seq(1, 2, 3)))
      },
      test("appends primitive modifiers") {
        val schema1 = Schema[Int]
        val schema2 = schema1.modifier(Modifier.config("key1", "value1"))
        assert(schema2.reflect.modifiers: Any)(equalTo(Seq(Modifier.config("key1", "value1")))) &&
        assert(
          schema2.modifiers(Seq(Modifier.config("key2", "value2"))).reflect.modifiers
        )(equalTo(Seq(Modifier.config("key1", "value1"), Modifier.config("key2", "value2"))))
      },
      test("has consistent toDynamicValue and fromDynamicValue") {
        assert(Schema[Byte].fromDynamicValue(Schema[Byte].toDynamicValue(1)))(isRight(equalTo(1: Byte))) &&
        assert(Schema[Byte].fromDynamicValue(DynamicValue.Primitive(PrimitiveValue.Int(1))))(
          isLeft(hasError("Expected Byte at: ."))
        )
      },
      test("encodes values using provided formats and outputs") {
        assert(encodeToString(out => Schema[Byte].encode(ToStringFormat)(out)(1: Byte)))(equalTo("1"))
      },
      test("derives codec from format directly") {
        val codec = Schema[Int].derive(ToStringFormat)
        assert(encodeToString(out => codec.encode(42, out)))(equalTo("42"))
      }
    ),
    suite("Reflect.Record")(
      test("has consistent equals and hashCode") {
        assert(Record.schema)(equalTo(Record.schema)) &&
        assert(Record.schema.hashCode)(equalTo(Record.schema.hashCode)) &&
        assert(Record.schema.defaultValue(Record(0, 0)))(equalTo(Record.schema)) &&
        assert(Record.schema.defaultValue(Record(0, 0)).hashCode)(equalTo(Record.schema.hashCode)) &&
        assert(Record.schema.examples(Record(1, 1000)))(equalTo(Record.schema)) &&
        assert(Record.schema.examples(Record(1, 1000)).hashCode)(equalTo(Record.schema.hashCode)) &&
        assert(Record.schema.doc("Record (updated)"))(not(equalTo(Record.schema)))
      },
      test("gets and updates record default value") {
        assert(Record.schema.getDefaultValue)(isNone) &&
        assert(Record.schema.defaultValue(Record(1, 2)).getDefaultValue)(isSome(equalTo(Record(1, 2))))
      },
      test("gets and updates record documentation") {
        assert(Record.schema.doc)(equalTo(Doc.Empty)) &&
        assert(Record.schema.doc("Record (updated)").doc)(equalTo(Doc("Record (updated)")))
      },
      test("gets and updates record examples") {
        assert(Record.schema.examples)(equalTo(Nil)) &&
        assert(Record.schema.examples(Record(2, 2000)).examples)(equalTo(Record(2, 2000) :: Nil))
      },
      test("gets and updates default values of record fields using optic focus") {
        assert(Record.schema.defaultValue(Record.b, 1: Byte).getDefaultValue(Record.b))(isSome(equalTo(1: Byte))) &&
        assert(Record.schema.defaultValue(Record.i, 1000).getDefaultValue(Record.i))(isSome(equalTo(1000))) &&
        assert(Record.schema.defaultValue(Record.x, true).getDefaultValue(Record.x))(isNone) // invalid lens
      },
      test("gets and updates documentation of record fields using optic focus") {
        assert(Record.schema.doc(Record.b, "b").doc(Record.b))(equalTo(Doc("b"))) &&
        assert(Record.schema.doc(Record.i, "i").doc(Record.i))(equalTo(Doc("i"))) &&
        assert(Record.schema.doc(Record.x, "x").doc(Record.x))(equalTo(Doc.Empty)) // invalid lens
      },
      test("gets and updates examples of record fields using optic focus") {
        assert(Record.schema.examples(Record.b, 2: Byte).examples(Record.b))(equalTo(Seq(2: Byte))) &&
        assert(Record.schema.examples(Record.i, 2000).examples(Record.i))(equalTo(Seq(2000))) &&
        assert(Record.schema.examples(Record.x, true).examples(Record.x))(equalTo(Seq())) // invalid lens
      },
      test("appends record modifiers") {
        val schema1 = Record.schema
        val schema2 = schema1.modifier(Modifier.config("key1", "value1"))
        assert(schema2.reflect.modifiers: Any)(equalTo(Seq(Modifier.config("key1", "value1")))) &&
        assert(
          schema2.modifiers(Seq(Modifier.config("key2", "value2"))).reflect.modifiers
        )(equalTo(Seq(Modifier.config("key1", "value1"), Modifier.config("key2", "value2"))))
      },
      test("has consistent toDynamicValue and fromDynamicValue") {
        assert(Box1.schema.fromDynamicValue(Box1.schema.toDynamicValue(Box1(4L))))(isRight(equalTo(Box1(4L)))) &&
        assert(Box2.schema.fromDynamicValue(Box2.schema.toDynamicValue(Box2("VVV"))))(isRight(equalTo(Box2("VVV")))) &&
        assert(Record.schema.fromDynamicValue(Record.schema.toDynamicValue(Record(1: Byte, 1000))))(
          isRight(equalTo(Record(1: Byte, 1000)))
        ) &&
        assert(Record.schema.fromDynamicValue(DynamicValue.Primitive(PrimitiveValue.Int(1))))(
          isLeft(hasError("Expected a record at: ."))
        ) &&
        assert(
          Record.schema.fromDynamicValue(
            DynamicValue.Record(
              Chunk(
                ("i", DynamicValue.Primitive(PrimitiveValue.Long(1000))),
                ("i", DynamicValue.Primitive(PrimitiveValue.Int(2000)))
              )
            )
          )
        )(isLeft(hasError("Expected Int at: .i\nDuplicated field 'i' at: .\nMissing field 'b' at: .")))
      },
      test("has consistent gets for typed and dynamic optics") {
        assert(Record.schema.get(Record.b.toDynamic))(equalTo(Record.schema.get(Record.b))) &&
        assert(Record.schema.get(Record.i.toDynamic))(equalTo(Record.schema.get(Record.i))) &&
        assert(Record.schema.get(Record.x.toDynamic))(equalTo(Record.schema.get(Record.x))) // invalid lens
      },
      test("derives schema for tuples") {
        type Tuple4 = (Byte, Short, Int, Long)

        object Tuple4 extends CompanionOptics[Tuple4] {
          implicit val schema: Schema[Tuple4] = Schema.derived
          val _1: Lens[Tuple4, Byte]          = optic(_._1)
          val _2: Lens[Tuple4, Short]         = optic(_._2)
          val _3: Lens[Tuple4, Int]           = optic(_._3)
          val _4: Lens[Tuple4, Long]          = optic(_._4)
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
                typeId = zio.blocks.typeid.TypeId.of[(Byte, Short, Int, Long)],
                recordBinding = null
              )
            )
          )
        )
      },
      test("derives schema for record with default values and annotations using a macro call") {
        @Modifier.config("record-key", "record-value-1")
        @Modifier.config("record-key", "record-value-2")
        case class `Record-1`(
          @Modifier.config("field-key", "field-value-1")
          @Modifier.config("field-key", "field-value-2")
          `b-1`: Boolean = false,
          @Modifier.transient()
          `f-2`: Float = 0.0f
        )

        type Record1 = `Record-1`

        object `Record-1` extends CompanionOptics[`Record-1`] {
          implicit val schema: Schema[Record1] = Schema.derived
          val `b-1`: Lens[Record1, Boolean]    = optic(x => x.`b-1`)
          val `f-2`: Lens[Record1, Float]      = optic(_.`f-2`)
        }

        val record = `Record-1`.schema.reflect.asRecord
        assert(record.map(_.constructor.usedRegisters))(isSome(equalTo(RegisterOffset(booleans = 1, floats = 1)))) &&
        assert(record.map(_.deconstructor.usedRegisters))(isSome(equalTo(RegisterOffset(booleans = 1, floats = 1)))) &&
        assert(`Record-1`.`b-1`.focus.getDefaultValue)(isSome(equalTo(false))) &&
        assert(`Record-1`.`f-2`.focus.getDefaultValue)(isSome(equalTo(0.0f))) &&
        assert(`Record-1`.`b-1`.get(`Record-1`()))(equalTo(false)) &&
        assert(`Record-1`.`f-2`.get(`Record-1`()))(equalTo(0.0f)) &&
        assert(`Record-1`.`b-1`.replace(`Record-1`(), true))(equalTo(`Record-1`(`b-1` = true))) &&
        assert(`Record-1`.`f-2`.replace(`Record-1`(), 1.0f))(equalTo(`Record-1`(`b-1` = false, 1.0f))) &&
        assert(`Record-1`.schema.fromDynamicValue(`Record-1`.schema.toDynamicValue(`Record-1`())))(
          isRight(equalTo(`Record-1`()))
        ) &&
        assert(`Record-1`.schema)(
          equalTo(
            new Schema[Record1](
              reflect = Reflect.Record[Binding, Record1](
                fields = Vector(
                  Schema[Boolean].reflect
                    .asTerm("b-1")
                    .copy(modifiers =
                      Seq(
                        Modifier.config("field-key", "field-value-1"),
                        Modifier.config("field-key", "field-value-2")
                      )
                    ),
                  Schema[Float].reflect.asTerm("f-2").copy(modifiers = Seq(Modifier.transient()))
                ),
                typeId = zio.blocks.typeid.TypeId.of[Record1],
                recordBinding = null,
                modifiers = Seq(
                  Modifier.config("record-key", "record-value-1"),
                  Modifier.config("record-key", "record-value-2")
                )
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

        object `Record-2` extends CompanionOptics[Record2[`i-8`, `i-32`]] {
          implicit val schema: Schema[Record2[`i-8`, `i-32`]] = Schema.derived
          val b: Lens[Record2[`i-8`, `i-32`], `i-8`]          = optic(_.b)
          val i: Lens[Record2[`i-8`, `i-32`], `i-32`]         = optic(_.i)
        }

        val record = `Record-2`.schema.reflect.asRecord
        assert(record.map(_.constructor.usedRegisters))(isSome(equalTo(RegisterOffset(bytes = 1, ints = 1)))) &&
        assert(record.map(_.deconstructor.usedRegisters))(isSome(equalTo(RegisterOffset(bytes = 1, ints = 1)))) &&
        assert(`Record-2`.b.focus.getDefaultValue)(isNone) &&
        assert(`Record-2`.i.focus.getDefaultValue)(isNone) &&
        assert(`Record-2`.b.get(`Record-2`[`i-8`, `i-32`](1, 2)))(equalTo(1: Byte)) &&
        assert(`Record-2`.i.get(`Record-2`[`i-8`, `i-32`](1, 2)))(equalTo(2)) &&
        assert(`Record-2`.b.replace(`Record-2`[`i-8`, `i-32`](1, 2), 3: Byte))(
          equalTo(`Record-2`[`i-8`, `i-32`](3, 2))
        ) &&
        assert(`Record-2`.i.replace(`Record-2`[`i-8`, `i-32`](1, 2), 3))(equalTo(`Record-2`[`i-8`, `i-32`](1, 3))) &&
        assert(`Record-2`.schema.fromDynamicValue(`Record-2`.schema.toDynamicValue(`Record-2`[`i-8`, `i-32`](1, 2))))(
          isRight(equalTo(`Record-2`[`i-8`, `i-32`](1, 2)))
        ) &&
        assert(`Record-2`.schema)(
          equalTo(
            new Schema[Record2[`i-8`, `i-32`]](
              reflect = Reflect.Record[Binding, Record2[`i-8`, `i-32`]](
                fields = Vector(
                  Schema[Byte].reflect.asTerm("b"),
                  Schema[Int].reflect.asTerm("i")
                ),
                typeId = TypeId.of[`Record-2`[Byte, Int]],
                recordBinding = null
              )
            )
          )
        )
      },
      test("derives schema for record with multi list constructor using a macro call") {
        case class Record3(s: Short = 0: Short)(val l: Long)

        object Record3 extends CompanionOptics[Record3] {
          implicit val schema: Schema[Record3] = Schema.derived
          val s: Lens[Record3, Short]          = optic(_.s)
          val l: Lens[Record3, Long]           = optic(_.l)
        }

        val record = Record3.schema.reflect.asRecord
        assert(record.map(_.constructor.usedRegisters))(isSome(equalTo(RegisterOffset(shorts = 1, longs = 1)))) &&
        assert(record.map(_.deconstructor.usedRegisters))(isSome(equalTo(RegisterOffset(shorts = 1, longs = 1)))) &&
        assert(Record3.s.focus.getDefaultValue)(isSome(equalTo(0: Short))) &&
        assert(Record3.l.focus.getDefaultValue)(isNone) &&
        assert(Record3.s.modify(new Record3(1)(2L), _ => 3: Short).s)(equalTo(3: Short)) &&
        assert(Record3.l.modify(new Record3(1)(2L), _ => 3L).l)(equalTo(3L)) &&
        assert(Record3.s.get(new Record3(1)(2L)))(equalTo(1: Short)) &&
        assert(Record3.l.get(new Record3(1)(2L)))(equalTo(2L)) &&
        assert(Record3.schema.fromDynamicValue(Record3.schema.toDynamicValue(new Record3(1)(2L))))(
          isRight(equalTo(new Record3(1)(2L)))
        ) &&
        assert(Record3.schema)(
          equalTo(
            new Schema[Record3](
              reflect = Reflect.Record[Binding, Record3](
                fields = Vector(
                  Schema[Short].reflect.asTerm("s"),
                  Schema[Long].reflect.asTerm("l")
                ),
                typeId = zio.blocks.typeid.TypeId.of[Record3],
                recordBinding = null
              )
            )
          )
        )
      },
      test("derives schema for record with nested collections using a macro call") {
        case class Record4(mx: Vector[ArraySeq[Int]], rs: List[Set[Int]])

        object Record4 extends CompanionOptics[Record4] {
          implicit val schema: Schema[Record4]     = Schema.derived
          val v: Traversal[Record4, ArraySeq[Int]] = optic(_.mx).vectorValues
          val mx: Traversal[Record4, Int]          = optic((x: Record4) => x.mx.each.each)
          val rs: Traversal[Record4, Int]          = optic(_.rs).listValues.setValues
        }

        val record = Record4.schema.reflect.asRecord
        assert(record.map(_.constructor.usedRegisters))(isSome(equalTo(RegisterOffset(objects = 2)))) &&
        assert(record.map(_.deconstructor.usedRegisters))(isSome(equalTo(RegisterOffset(objects = 2)))) &&
        assert(Record4.mx.focus.getDefaultValue)(isNone) &&
        assert(Record4.rs.focus.getDefaultValue)(isNone) &&
        assert(Record4.mx.fold[Int](Record4(Vector(ArraySeq(1, 2), ArraySeq(3, 4)), Nil))(0, _ + _))(equalTo(10)) &&
        assert(Record4.rs.fold[Int](Record4(null, List(Set(1, 2), Set(3, 4))))(0, _ + _))(equalTo(10)) &&
        assert(Record4.mx.reduceOrFail(Record4(Vector(ArraySeq(1, 2), ArraySeq(3, 4)), Nil))(_ + _))(
          isRight(equalTo(10))
        ) &&
        assert(Record4.rs.reduceOrFail(Record4(null, List(Set(1, 2), Set(3, 4))))(_ + _))(isRight(equalTo(10))) &&
        assert(
          Record4.schema.fromDynamicValue(
            Record4.schema.toDynamicValue(Record4(Vector(ArraySeq(1, 2), ArraySeq(3, 4)), Nil))
          )
        )(isRight(equalTo(Record4(Vector(ArraySeq(1, 2), ArraySeq(3, 4)), Nil)))) &&
        assert(
          Record4.schema.fromDynamicValue(
            Record4.schema.toDynamicValue(Record4(Vector(ArraySeq()), List(Set(1, 2), Set(3, 4))))
          )
        )(isRight(equalTo(Record4(Vector(ArraySeq()), List(Set(1, 2), Set(3, 4)))))) &&
        assert(Record4.schema)(
          equalTo(
            new Schema[Record4](
              reflect = Reflect.Record[Binding, Record4](
                fields = Vector(
                  Schema.derived[Vector[ArraySeq[Int]]].reflect.asTerm("mx"),
                  Schema[List[Set[Int]]].reflect.asTerm("rs")
                ),
                typeId = zio.blocks.typeid.TypeId.of[Record4],
                recordBinding = null
              )
            )
          )
        )
      },
      test("derives schema recursively for options and supported collections using a macro call") {
        case class Foo(
          as: ArraySeq[Bar],
          l: List[Bar],
          m: Map[Bar, Bar],
          o: Option[Bar],
          v: Vector[Bar],
          s: Set[Bar]
        )

        case class Bar(id: Int)

        val schema = Schema.derived[Foo]
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
      test("derives schema for record with unit types") {
        case class Record5(u: Unit, su: Seq[Unit])

        object Record5 extends CompanionOptics[Record5] {
          implicit val schema: Schema[Record5] = Schema.derived
          val u: Lens[Record5, Unit]           = optic(_.u)
          val su: Traversal[Record5, Unit]     = optic(_.su.each)
        }

        val record = Record5.schema.reflect.asRecord
        assert(record.map(_.constructor.usedRegisters))(isSome(equalTo(RegisterOffset(objects = 1)))) &&
        assert(record.map(_.deconstructor.usedRegisters))(isSome(equalTo(RegisterOffset(objects = 1)))) &&
        assert(Record5.u.focus.getDefaultValue)(isNone) &&
        assert(Record5.su.focus.getDefaultValue)(isNone) &&
        assert(Record5.u.get(Record5((), Nil)))(equalTo(())) &&
        assert(Record5.su.fold[Int](Record5((), Seq((), (), ())))(0, (z, _) => z + 1))(equalTo(3)) &&
        assert(Record5.schema.fromDynamicValue(Record5.schema.toDynamicValue(Record5((), Seq((), (), ())))))(
          isRight(equalTo(Record5((), Seq((), (), ()))))
        ) &&
        assert(Record5.schema)(
          equalTo(
            new Schema[Record5](
              reflect = Reflect.Record[Binding, Record5](
                fields = Vector(
                  Schema[Unit].reflect.asTerm("u"),
                  Schema[Seq[Unit]].reflect.asTerm("su")
                ),
                typeId = zio.blocks.typeid.TypeId.of[Record5],
                recordBinding = null
              )
            )
          )
        )
      },
      test("derives schema for record with option types") {
        case class Record6(
          u: Option[Unit] = Some(()),
          bl: Option[Boolean] = Some(false),
          b: Option[Byte] = Some(0: Byte),
          c: Option[Char] = Some(' '),
          s: Option[Short] = Some(0: Short),
          f: Option[Float] = Some(0.0f),
          i: Option[Int] = Some(0),
          d: Option[Double] = Some(0.0),
          l: Option[Long] = Some(0L),
          r: Option[Record6] = None
        )

        object Record6 extends CompanionOptics[Record6] {
          implicit val schema: Schema[Record6] = Schema.derived
          val u: Optional[Record6, Unit]       = optic(_.u.when[Some[Unit]].value)
          val bl: Optional[Record6, Boolean]   = optic(_.bl.when[Some[Boolean]].value)
          val b: Optional[Record6, Byte]       = optic(_.b.when[Some[Byte]].value)
          val c: Optional[Record6, Char]       = optic(_.c.when[Some[Char]].value)
          val s: Optional[Record6, Short]      = optic(_.s.when[Some[Short]].value)
          val f: Optional[Record6, Float]      = optic(_.f.when[Some[Float]].value)
          val i: Optional[Record6, Int]        = optic(_.i.when[Some[Int]].value)
          val d: Optional[Record6, Double]     = optic(_.d.when[Some[Double]].value)
          val l: Optional[Record6, Long]       = optic(_.l.when[Some[Long]].value)
          val r: Optional[Record6, Record6]    = optic(_.r.when[Some[Record6]].value)
          val rn: Optional[Record6, None.type] = optic(_.r.when[None.type])
        }

        val record = Record6.schema.reflect.asRecord
        assert(record.map(_.constructor.usedRegisters))(isSome(equalTo(RegisterOffset(objects = 10)))) &&
        assert(record.map(_.deconstructor.usedRegisters))(isSome(equalTo(RegisterOffset(objects = 10)))) &&
        assert(Record6.u.modify(Record6(), _ => ()))(equalTo(Record6())) &&
        assert(Record6.u.getOption(Record6()))(isSome(equalTo(()))) &&
        assert(Record6.bl.modify(Record6(bl = Some(true)), _ => false))(equalTo(Record6(bl = Some(false)))) &&
        assert(Record6.bl.getOption(Record6(bl = Some(true))))(isSome(equalTo(true))) &&
        assert(Record6.b.modify(Record6(b = Some(123: Byte)), _ => 0: Byte))(equalTo(Record6(b = Some(0: Byte)))) &&
        assert(Record6.b.getOption(Record6(b = Some(123: Byte))))(isSome(equalTo(123: Byte))) &&
        assert(Record6.c.modify(Record6(c = Some('a')), _ => ' '))(equalTo(Record6(c = Some(' ')))) &&
        assert(Record6.c.getOption(Record6(c = Some('a'))))(isSome(equalTo('a'))) &&
        assert(Record6.s.modify(Record6(s = Some(123: Short)), _ => 0: Short))(equalTo(Record6(s = Some(0: Short)))) &&
        assert(Record6.s.getOption(Record6(s = Some(123: Short))))(isSome(equalTo(123: Short))) &&
        assert(Record6.f.replaceOption(Record6(f = Some(123.0f)), 0.0f))(isSome(equalTo(Record6(f = Some(0.0f))))) &&
        assert(Record6.f.getOption(Record6(f = Some(123.0f))))(isSome(equalTo(123.0f))) &&
        assert(Record6.i.replaceOption(Record6(i = Some(123)), 0))(isSome(equalTo(Record6(i = Some(0))))) &&
        assert(Record6.i.getOption(Record6(i = Some(123))))(isSome(equalTo(123))) &&
        assert(Record6.d.replaceOption(Record6(d = Some(123.0)), 0.0))(isSome(equalTo(Record6(d = Some(0.0))))) &&
        assert(Record6.d.getOption(Record6(d = Some(123.0))))(isSome(equalTo(123.0))) &&
        assert(Record6.l.getOption(Record6(l = Some(123L))))(isSome(equalTo(123L))) &&
        assert(Record6.l.replaceOption(Record6(l = Some(123L)), 0L))(isSome(equalTo(Record6(l = Some(0L))))) &&
        assert(Record6.r.getOption(Record6()))(isNone) &&
        assert(Record6.r.getOption(Record6(r = Some(Record6()))))(isSome(equalTo(Record6()))) &&
        assert(Record6.r.replaceOption(Record6(r = Some(Record6())), null))(isSome(equalTo(Record6(r = Some(null))))) &&
        assert(Record6.rn.getOption(Record6(r = Some(Record6()))))(isNone) &&
        assert(Record6.rn.getOption(Record6(r = None)))(isSome(equalTo(None))) &&
        assert(Record6.schema.fromDynamicValue(Record6.schema.toDynamicValue(Record6())))(
          isRight(equalTo(Record6()))
        ) &&
        assert(Record6.schema.fromDynamicValue(Record6.schema.toDynamicValue(Record6(r = Some(Record6())))))(
          isRight(equalTo(Record6(r = Some(Record6()))))
        )
      },
      test("derives schema for record with dynamic values") {
        case class Record7(d: DynamicValue, od: Option[DynamicValue], isd: IndexedSeq[DynamicValue])

        object Record7 extends CompanionOptics[Record7] {
          implicit val schema: Schema[Record7]      = Schema.derived
          val d: Lens[Record7, DynamicValue]        = optic(_.d)
          val od: Optional[Record7, DynamicValue]   = optic(_.od.when[Some[DynamicValue]].value)
          val isd: Traversal[Record7, DynamicValue] = optic(_.isd.each)
        }

        val record = Record7.schema.reflect.asRecord
        assert(Record7.d.focus.isDynamic)(equalTo(true)) &&
        assert(Record7.od.focus.isDynamic)(equalTo(true)) &&
        assert(Record7.isd.focus.isDynamic)(equalTo(true)) &&
        assert(record.map(_.constructor.usedRegisters))(isSome(equalTo(RegisterOffset(objects = 3)))) &&
        assert(record.map(_.deconstructor.usedRegisters))(isSome(equalTo(RegisterOffset(objects = 3)))) &&
        assert(Record7.d.get(Record7(DynamicValue.Primitive(PrimitiveValue.Int(1)), None, IndexedSeq.empty)))(
          equalTo(DynamicValue.Primitive(PrimitiveValue.Int(1)))
        ) &&
        assert(
          Record7.od.getOption(
            Record7(
              DynamicValue.Primitive(PrimitiveValue.Int(1)),
              Some(DynamicValue.Primitive(PrimitiveValue.Int(2))),
              IndexedSeq.empty
            )
          )
        )(isSome(equalTo(DynamicValue.Primitive(PrimitiveValue.Int(2))))) &&
        assert(
          Record7.isd.fold[Int](
            Record7(
              DynamicValue.Primitive(PrimitiveValue.Int(1)),
              Some(DynamicValue.Primitive(PrimitiveValue.Int(2))),
              IndexedSeq(DynamicValue.Primitive(PrimitiveValue.Int(3)))
            )
          )(0, (z, _) => z + 1)
        )(equalTo(1)) &&
        assert(
          Record7.schema.fromDynamicValue(
            Record7.schema.toDynamicValue(
              Record7(
                DynamicValue.Primitive(PrimitiveValue.Int(1)),
                Some(DynamicValue.Primitive(PrimitiveValue.Int(2))),
                IndexedSeq(DynamicValue.Primitive(PrimitiveValue.Int(3)))
              )
            )
          )
        )(
          isRight(
            equalTo(
              Record7(
                DynamicValue.Primitive(PrimitiveValue.Int(1)),
                Some(DynamicValue.Primitive(PrimitiveValue.Int(2))),
                IndexedSeq(DynamicValue.Primitive(PrimitiveValue.Int(3)))
              )
            )
          )
        ) &&
        assert(Record7.schema)(
          equalTo(
            new Schema[Record7](
              reflect = Reflect.Record[Binding, Record7](
                fields = Vector(
                  Schema[DynamicValue].reflect.asTerm("d"),
                  Schema[Option[DynamicValue]].reflect.asTerm("od"),
                  Schema[IndexedSeq[DynamicValue]].reflect.asTerm("isd")
                ),
                typeId = zio.blocks.typeid.TypeId.of[Record7],
                recordBinding = null
              )
            )
          )
        )
      },
      test("derives schema for recursive higher-kinded records using a macro call") {
        case class Record8[F[_]](f: F[Int], fs: F[Record8[F]])

        val schema = Schema.derived[Record8[Option]]
        val record = schema.reflect.asRecord
        val value  = Record8[Option](Some(1), Some(Record8[Option](Some(2), None)))
        assert(record.map(_.constructor.usedRegisters))(isSome(equalTo(RegisterOffset(objects = 2)))) &&
        assert(record.map(_.deconstructor.usedRegisters))(isSome(equalTo(RegisterOffset(objects = 2)))) &&
        assert(record.map(_.typeId))(
          isSome(
            equalTo(zio.blocks.typeid.TypeId.of[Record8[Option]])
          )
        ) &&
        assert(schema.fromDynamicValue(schema.toDynamicValue(value)))(isRight(equalTo(value)))
      },
      test("derives schema for nested generic records") {
        sealed trait MySealedTrait

        object MySealedTrait {
          case class Foo(foo: Int) extends MySealedTrait

          case class Bar(bar: String) extends MySealedTrait

          implicit val schema: Schema[MySealedTrait] = Schema.derived
        }

        case class Child[T <: MySealedTrait](test: T)

        object Child {
          implicit val schema: Schema[Child[MySealedTrait]] = Schema.derived
        }

        case class Parent(child: Child[MySealedTrait])

        object Parent {
          implicit val schema: Schema[Parent] = Schema.derived
        }

        val schema = Schema[Parent]
        val value1 = Parent(Child(MySealedTrait.Foo(1)))
        val value2 = Parent(Child(MySealedTrait.Bar("WWW")))
        assert(schema.fromDynamicValue(schema.toDynamicValue(value1)))(isRight(equalTo(value1))) &&
        assert(schema.fromDynamicValue(schema.toDynamicValue(value2)))(isRight(equalTo(value2)))
      },
      test("derives schema for case class with value class fields using a macro call") {
        case class Record9(b1: Box1, b2: Box2)

        object Record9 extends CompanionOptics[Record9] {
          implicit val schema: Schema[Record9] = Schema.derived
          val b1: Lens[Record9, Box1]          = optic(_.b1)
          val b2: Lens[Record9, Box2]          = optic(_.b2)
          val b1_l: Lens[Record9, Long]        = optic(_.b1.l)
          val b2_s: Lens[Record9, String]      = optic(_.b2.s)
        }

        val record = Record9.schema.reflect.asRecord
        val value  = Record9(Box1(1L), Box2("VVV"))
        assert(record.map(_.constructor.usedRegisters))(isSome(equalTo(RegisterOffset(objects = 2)))) &&
        assert(record.map(_.deconstructor.usedRegisters))(isSome(equalTo(RegisterOffset(objects = 2)))) &&
        assert(Record9.b1.get(value))(equalTo(Box1(1L))) &&
        assert(Record9.b2.get(value))(equalTo(Box2("VVV"))) &&
        assert(Record9.b1_l.get(value))(equalTo(1L)) &&
        assert(Record9.b2_s.get(value))(equalTo("VVV")) &&
        assert(Record9.b1.replace(value, Box1(2L)))(equalTo(Record9(Box1(2L), Box2("VVV")))) &&
        assert(Record9.b2.replace(value, Box2("WWW")))(equalTo(Record9(Box1(1L), Box2("WWW")))) &&
        assert(Record9.b1_l.replace(value, 2L))(equalTo(Record9(Box1(2L), Box2("VVV")))) &&
        assert(Record9.b2_s.replace(value, "WWW"))(equalTo(Record9(Box1(1L), Box2("WWW")))) &&
        assert(Record9.schema.fromDynamicValue(Record9.schema.toDynamicValue(value)))(isRight(equalTo(value)))
      },
      test("derives schema for case classes with more than 22 fields using a macro call") {
        case class Record24(
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
          i21: Int,
          i22: Int,
          i23: Int,
          s24: String
        )

        object Record24 extends CompanionOptics[Record24] {
          implicit val schema: Schema[Record24] = Schema.derived
          val i23: Lens[Record24, Int]          = $(_.i23)
          val s24: Lens[Record24, String]       = $(_.s24)
        }

        val record = Record24.schema.reflect.asRecord
        val value  = Record24(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, "24")
        assert(record.map(_.constructor.usedRegisters))(isSome(equalTo(RegisterOffset(ints = 23, objects = 1)))) &&
        assert(record.map(_.deconstructor.usedRegisters))(isSome(equalTo(RegisterOffset(ints = 23, objects = 1)))) &&
        assert(Record24.i23.get(value))(equalTo(23)) &&
        assert(Record24.s24.get(value))(equalTo("24")) &&
        assert(Record24.schema.fromDynamicValue(Record24.schema.toDynamicValue(value)))(isRight(equalTo(value)))
      },
      test("derives schema for record with fields that have 3rd party collection type") {
        implicit def chunkSchema[V](implicit ev: Schema[V]): Schema[Chunk[V]] =
          new Schema(
            new Reflect.Wrapper[Binding, Chunk[V], List[V]](
              Schema.list[V].reflect,
              zio.blocks.typeid.TypeId.of[Chunk[V]],
              new Binding.Wrapper(x => Chunk.fromIterable(x), x => x.toList)
            )
          )

        case class Test(chunk: Chunk[Int])

        val schema = Schema.derived[Test]
        val value  = Test(Chunk(1, 2, 3))
        assert(schema.fromDynamicValue(schema.toDynamicValue(value)))(isRight(equalTo(value)))
      },
      test("encodes values using provided formats and outputs") {
        assert(encodeToString { out =>
          Schema[Record].encode(ToStringFormat)(out)(Record(1: Byte, 2))
        })(equalTo("Record(1,2)"))
      },
      test("doesn't generate schema for classes without a primary constructor") {
        typeCheck {
          "Schema.derived[scala.concurrent.duration.Duration]"
        }.map(
          assert(_)(
            isLeft(
              containsString("Cannot find a primary constructor for 'Infinite.this.<local child>'") || // Scala 2
                containsString(
                  "Cannot find 'length' parameter of 'scala.concurrent.duration.FiniteDuration' in the primary constructor."
                ) || // Scala 3.3
                containsString(
                  "Cannot derive schema for 'java.util.concurrent.TimeUnit'."
                ) || // Scala 3.7
                containsString(
                  "Exception occurred while executing macro expansion.\njava.lang.StackOverflowError"
                ) // Scala 3.8, see https://github.com/scala/scala3/issues/24598
            )
          )
        )
      },
      test("doesn't generate schema for non resolved generic field types with a missing implicitly provided schema") {
        typeCheck {
          """case class GenDoc[A, B, C](a: A, opt: Option[B], list: List[C])

             object GenDoc {
               implicit def schema[A, B : Schema, C : Schema]: Schema[GenDoc[A, B, C]] = Schema.derived
             }"""
        }.map(assert(_)(isLeft(containsString("Cannot derive schema for 'A'."))))
      },
      test("doesn't generate schema for multi list constructor with default values in non-first list of arguments") {
        typeCheck {
          """case class MultiListWithDefaults(val s: Short = 0: Short)(val l: Long = 1L)

             Schema.derived[MultiListWithDefaults]"""
        }.map(
          assert(_)(
            isLeft(
              containsString(
                "missing argument list for method <init>$default$2 in object MultiListWithDefaults"
              ) || // Scala 2
                containsString(
                  "Default values of non-first parameter lists are not supported for 'val l' in class 'MultiListWithDefaults'."
                ) // Scala 3
            )
          )
        )
      },
      test("doesn't generate schema for generic constructor with default values of wrong type") {
        typeCheck {
          """case class PolymorphicDefaults[A, B](i: A = 1, cs: List[B] = Nil)

             Schema.derived[PolymorphicDefaults[String, Int]]"""
        }.map(
          assert(_)(
            isLeft(
              containsString(
                "polymorphic expression cannot be instantiated to expected type"
              ) || // Scala 2
                containsString(
                  "Type Mismatch"
                ) ||                                                    // Scala 3.3
                (containsString("Found") && containsString("Required")) // Scala 3.5+
            )
          )
        )
      }
    ),
    suite("Reflect.Variant")(
      test("has consistent equals and hashCode") {
        assert(Variant.schema)(equalTo(Variant.schema)) &&
        assert(Variant.schema.hashCode)(equalTo(Variant.schema.hashCode)) &&
        assert(Schema[Option[String]])(equalTo(Schema[Option[String]])) &&
        assert(Schema[Option[String]].hashCode)(equalTo(Schema[Option[String]].hashCode)) &&
        assert(Schema[Either[Int, Long]])(equalTo(Schema[Either[Int, Long]])) &&
        assert(Schema[Either[Int, Long]].hashCode)(equalTo(Schema[Either[Int, Long]].hashCode)) &&
        assert(Variant.schema.defaultValue(Case1('1')))(equalTo(Variant.schema)) &&
        assert(Variant.schema.defaultValue(Case1('1')).hashCode)(equalTo(Variant.schema.hashCode)) &&
        assert(Variant.schema.examples(Case1('1')))(equalTo(Variant.schema)) &&
        assert(Variant.schema.examples(Case1('1')).hashCode)(equalTo(Variant.schema.hashCode)) &&
        assert(Variant.schema.doc("Variant (updated)"))(not(equalTo(Variant.schema)))
      },
      test("gets and updates variant default value") {
        assert(Variant.schema.getDefaultValue)(isNone) &&
        assert(Variant.schema.defaultValue(Case1('1')).getDefaultValue)(isSome(equalTo(Case1('1'))))
      },
      test("gets and updates variant documentation") {
        assert(Variant.schema.doc)(equalTo(Doc.Empty)) &&
        assert(Variant.schema.doc("Variant (updated)").doc)(equalTo(Doc("Variant (updated)")))
      },
      test("gets and updates variant examples") {
        assert(Variant.schema.examples)(equalTo(Nil)) &&
        assert(Variant.schema.examples(Case1('1'), Case2("VVV")).examples)(equalTo(Case1('1') :: Case2("VVV") :: Nil))
      },
      test("gets and updates default values of variant cases using optic focus") {
        assert(Variant.schema.defaultValue(Variant.case1, Case1('1')).getDefaultValue(Variant.case1))(
          isSome(equalTo(Case1('1')))
        ) &&
        assert(Variant.schema.defaultValue(Variant.case2, Case2("VVV")).getDefaultValue(Variant.case2))(
          isSome(equalTo(Case2("VVV")))
        )
      },
      test("gets and updates documentation of variant cases using optic focus") {
        assert(Variant.schema.doc(Variant.case1, "Case1").doc(Variant.case1))(equalTo(Doc("Case1"))) &&
        assert(Variant.schema.doc(Variant.case2, "Case2").doc(Variant.case2))(equalTo(Doc("Case2")))
      },
      test("gets and updates examples of variant cases using optic focus") {
        assert(Variant.schema.examples(Variant.case1, Case1('1')).examples(Variant.case1))(equalTo(Seq(Case1('1')))) &&
        assert(Variant.schema.examples(Variant.case2, Case2("VVV")).examples(Variant.case2))(equalTo(Seq(Case2("VVV"))))
      },
      test("appends variant modifiers") {
        val schema1 = Variant.schema
        val schema2 = schema1.modifier(Modifier.config("key1", "value1"))
        assert(schema2.reflect.modifiers: Any)(equalTo(Seq(Modifier.config("key1", "value1")))) &&
        assert(
          schema2.modifiers(Seq(Modifier.config("key2", "value2"))).reflect.modifiers
        )(equalTo(Seq(Modifier.config("key1", "value1"), Modifier.config("key2", "value2"))))
      },
      test("has consistent toDynamicValue and fromDynamicValue") {
        assert(Variant.schema.fromDynamicValue(Variant.schema.toDynamicValue(Case1('1'))))(
          isRight(equalTo(Case1('1')))
        ) &&
        assert(Variant.schema.fromDynamicValue(Variant.schema.toDynamicValue(Case2("VVV"))))(
          isRight(equalTo(Case2("VVV")))
        ) &&
        assert(Variant.schema.fromDynamicValue(DynamicValue.Primitive(PrimitiveValue.Int(1))))(
          isLeft(hasError("Expected a variant at: ."))
        ) &&
        assert(
          Variant.schema.fromDynamicValue(
            DynamicValue.Variant("Unknown", DynamicValue.Primitive(PrimitiveValue.Long(1000)))
          )
        )(isLeft(hasError("Unknown case 'Unknown' at: ."))) &&
        assert(
          Variant.schema.fromDynamicValue(
            DynamicValue
              .Variant("Case2", DynamicValue.Record(Chunk(("s", DynamicValue.Primitive(PrimitiveValue.Int(1))))))
          )
        )(
          isLeft(hasError("Expected String at: .when[Case2].s"))
        )
      },
      test("has consistent gets for typed and dynamic optics") {
        assert(Variant.schema.get(Variant.case1.toDynamic))(equalTo(Variant.schema.get(Variant.case1))) &&
        assert(Variant.schema.get(Variant.case2.toDynamic))(equalTo(Variant.schema.get(Variant.case2)))
      },
      test("derives schema for variant using a macro call") {
        @Modifier.config("variant-key", "variant-value-1")
        @Modifier.config("variant-key", "variant-value-2")
        sealed trait `Variant-1`

        type Variant1 = `Variant-1`

        @Modifier.config("case-key-1", "case-value-1")
        @Modifier.config("case-key-1", "case-value-2")
        case class `Case-1`(@Modifier.config("field-key-1", "field-value-1") d: Double) extends Variant1

        @Modifier.config("case-key-2", "case-value-1")
        @Modifier.config("case-key-2", "case-value-2")
        case class `Case-2`(@Modifier.config("field-key-2", "field-value-2") f: Float) extends Variant1

        @Modifier.config("case-key-3", "case-value-1")
        @Modifier.config("case-key-3", "case-value-2")
        case object `Case-3` extends Variant1

        object `Variant-1` extends CompanionOptics[Variant1] {
          implicit val schema: Schema[Variant1]     = Schema.derived
          val case1: Prism[Variant1, `Case-1`]      = optic(_.when[`Case-1`])
          val case2: Prism[Variant1, `Case-2`]      = optic(_.when[`Case-2`])
          val case3: Prism[Variant1, `Case-3`.type] = optic(_.when[`Case-3`.type])
        }

        val variant = `Variant-1`.schema.reflect.asVariant
        val record1 = variant.flatMap(_.cases(0).value.asRecord)
        val record2 = variant.flatMap(_.cases(1).value.asRecord)
        val record3 = variant.flatMap(_.cases(2).value.asRecord)
        assert(variant.map(_.cases(0).modifiers))(
          isSome(
            equalTo(Seq(Modifier.config("case-key-1", "case-value-1"), Modifier.config("case-key-1", "case-value-2")))
          )
        ) &&
        assert(record1.map(_.modifiers))(
          isSome(
            equalTo(Seq(Modifier.config("case-key-1", "case-value-1"), Modifier.config("case-key-1", "case-value-2")))
          )
        ) &&
        assert(record1.map(_.fields.flatMap(_.modifiers)): Option[Any])(
          isSome(
            equalTo(Seq(Modifier.config("field-key-1", "field-value-1")))
          )
        ) &&
        assert(variant.map(_.cases(1).modifiers))(
          isSome(
            equalTo(Seq(Modifier.config("case-key-2", "case-value-1"), Modifier.config("case-key-2", "case-value-2")))
          )
        ) &&
        assert(record2.map(_.modifiers))(
          isSome(
            equalTo(Seq(Modifier.config("case-key-2", "case-value-1"), Modifier.config("case-key-2", "case-value-2")))
          )
        ) &&
        assert(record2.map(_.fields.flatMap(_.modifiers)): Option[Any])(
          isSome(
            equalTo(Seq(Modifier.config("field-key-2", "field-value-2")))
          )
        ) &&
        assert(variant.map(_.cases(2).modifiers))(
          isSome(
            equalTo(Seq(Modifier.config("case-key-3", "case-value-1"), Modifier.config("case-key-3", "case-value-2")))
          )
        ) &&
        assert(record3.map(_.modifiers))(
          isSome(
            equalTo(Seq(Modifier.config("case-key-3", "case-value-1"), Modifier.config("case-key-3", "case-value-2")))
          )
        ) &&
        assert(`Variant-1`.case1.getOption(`Case-1`(0.1)))(isSome(equalTo(`Case-1`(0.1)))) &&
        assert(`Variant-1`.case2.getOption(`Case-2`(0.2f)))(isSome(equalTo(`Case-2`(0.2f)))) &&
        assert(`Variant-1`.case3.getOption(`Case-3`))(isSome(equalTo(`Case-3`))) &&
        assert(`Variant-1`.case1.replace(`Case-1`(0.1), `Case-1`(0.2)))(equalTo(`Case-1`(0.2))) &&
        assert(`Variant-1`.case2.replace(`Case-2`(0.2f), `Case-2`(0.3f)))(equalTo(`Case-2`(0.3f))) &&
        assert(`Variant-1`.case3.replace(`Case-3`, `Case-3`))(equalTo(`Case-3`)) &&
        assert(`Variant-1`.schema.fromDynamicValue(`Variant-1`.schema.toDynamicValue(`Case-1`(0.1))))(
          isRight(equalTo(`Case-1`(0.1)))
        ) &&
        assert(`Variant-1`.schema.fromDynamicValue(`Variant-1`.schema.toDynamicValue(`Case-2`(0.2f))))(
          isRight(equalTo(`Case-2`(0.2f)))
        ) &&
        assert(`Variant-1`.schema.fromDynamicValue(`Variant-1`.schema.toDynamicValue(`Case-3`)))(
          isRight(equalTo(`Case-3`))
        ) &&
        assert(variant.map(_.cases.map(_.name)))(isSome(equalTo(Vector("Case-1", "Case-2", "Case-3")))) &&
        assert(variant.map(_.typeId))(
          isSome(equalTo(zio.blocks.typeid.TypeId.of[`Variant-1`]))
        ) &&
        assert(variant.map(_.modifiers))(
          isSome(
            equalTo(
              Seq(
                Modifier.config("variant-key", "variant-value-1"),
                Modifier.config("variant-key", "variant-value-2")
              )
            )
          )
        )
      },
      test("derives schema for genetic variant using a macro call") {
        sealed abstract class `Variant-2`[+A]

        type Variant2[A] = `Variant-2`[A]

        case object MissingValue extends Variant2[Nothing]

        case object NullValue extends Variant2[Null]

        case class Value[A](a: A) extends Variant2[A]

        object Variant2OfString extends CompanionOptics[Variant2[String]] {
          implicit val schema: Schema[Variant2[String]]                = Schema.derived
          val missingValue: Prism[Variant2[String], MissingValue.type] = optic(_.when[MissingValue.type])
          val nullValue: Prism[Variant2[String], NullValue.type]       = optic(_.when[NullValue.type])
          val value: Prism[Variant2[String], Value[String]]            = optic(_.when[Value[String]])
        }

        val variant = Variant2OfString.schema.reflect.asVariant
        assert(Variant2OfString.missingValue.getOption(MissingValue))(isSome(equalTo(MissingValue))) &&
        assert(Variant2OfString.nullValue.getOption(NullValue))(isSome(equalTo(NullValue))) &&
        assert(Variant2OfString.value.getOption(Value[String]("WWW")))(isSome(equalTo(Value[String]("WWW")))) &&
        assert(Variant2OfString.value.replace(Value[String]("WWW"), Value[String]("VVV")))(
          equalTo(Value[String]("VVV"))
        ) &&
        assert(Variant2OfString.schema.fromDynamicValue(Variant2OfString.schema.toDynamicValue(MissingValue)))(
          isRight(equalTo(MissingValue))
        ) &&
        assert(Variant2OfString.schema.fromDynamicValue(Variant2OfString.schema.toDynamicValue(NullValue)))(
          isRight(equalTo(NullValue))
        ) &&
        assert(Variant2OfString.schema.fromDynamicValue(Variant2OfString.schema.toDynamicValue(Value[String]("WWW"))))(
          isRight(equalTo(Value[String]("WWW")))
        ) &&
        assert(variant.map(_.cases.map(_.name)))(isSome(equalTo(Vector("MissingValue", "NullValue", "Value")))) &&
        assert(variant.map(_.typeId))(
          isSome(equalTo(zio.blocks.typeid.TypeId.of[`Variant-2`[String]]))
        )
      },
      test("derives schema for options") {
        val schema1  = Schema.derived[Option[String]]
        val schema2  = Schema.derived[Option[Unit]]
        val schema3  = Schema.derived[Option[Boolean]]
        val schema4  = Schema.derived[Option[Byte]]
        val schema5  = Schema.derived[Option[Char]]
        val schema6  = Schema.derived[Option[Short]]
        val schema7  = Schema.derived[Option[Float]]
        val schema8  = Schema.derived[Option[Int]]
        val schema9  = Schema.derived[Option[Double]]
        val schema10 = Schema.derived[Option[Long]]
        assert(schema1.fromDynamicValue(schema1.toDynamicValue(Option("VVV"))))(isRight(equalTo(Option("VVV")))) &&
        assert(schema2.fromDynamicValue(schema2.toDynamicValue(Option(()))))(isRight(equalTo(Option(())))) &&
        assert(schema3.fromDynamicValue(schema3.toDynamicValue(Option(true))))(isRight(equalTo(Option(true)))) &&
        assert(schema4.fromDynamicValue(schema4.toDynamicValue(Option(1.toByte))))(
          isRight(equalTo(Option(1.toByte)))
        ) &&
        assert(schema5.fromDynamicValue(schema5.toDynamicValue(Option('V'))))(isRight(equalTo(Option('V')))) &&
        assert(schema6.fromDynamicValue(schema6.toDynamicValue(Option(1.toShort))))(
          isRight(equalTo(Option(1.toShort)))
        ) &&
        assert(schema7.fromDynamicValue(schema7.toDynamicValue(Option(1.0f))))(isRight(equalTo(Option(1.0f)))) &&
        assert(schema8.fromDynamicValue(schema8.toDynamicValue(Option(1))))(isRight(equalTo(Option(1)))) &&
        assert(schema9.fromDynamicValue(schema9.toDynamicValue(Option(1.0))))(isRight(equalTo(Option(1.0)))) &&
        assert(schema10.fromDynamicValue(schema10.toDynamicValue(Option(1L))))(isRight(equalTo(Option(1L))))
      },
      test("derives schema for a variant with cases on different levels using a macro call") {
        object Level1_MultiLevel extends CompanionOptics[Level1.MultiLevel] {
          implicit val schema: Schema[Level1.MultiLevel]        = Schema.derived
          val c: Prism[Level1.MultiLevel, SchemaSpec.Case.type] = $(_.when[SchemaSpec.Case.type])
          val l1_c: Prism[Level1.MultiLevel, Level1.Case.type]  = $(_.when[Level1.Case.type])
        }

        val schema1  = A.schema
        val schema2  = Level1_MultiLevel.schema
        val variant1 = schema1.reflect.asVariant
        val variant2 = schema2.reflect.asVariant
        assert(A.a1.getOption(B.A1))(isSome(equalTo(B.A1))) &&
        assert(A.a2.getOption(B.A2))(isSome(equalTo(B.A2))) &&
        assert(schema1.fromDynamicValue(schema1.toDynamicValue(B.A1)))(isRight(equalTo(B.A1))) &&
        assert(schema1.fromDynamicValue(schema1.toDynamicValue(B.A2)))(isRight(equalTo(B.A2))) &&
        assert(variant1.map(_.cases.map(_.name)))(isSome(equalTo(Vector("A1", "A2")))) &&
        assert(variant1.map(_.typeId))(
          isSome(equalTo(zio.blocks.typeid.TypeId.of[A]))
        ) &&
        assert(Level1_MultiLevel.c.getOption(Case))(isSome(equalTo(Case))) &&
        assert(Level1_MultiLevel.l1_c.getOption(Level1.Case))(isSome(equalTo(Level1.Case))) &&
        assert(schema2.fromDynamicValue(schema2.toDynamicValue(Case)))(isRight(equalTo(Case))) &&
        assert(schema2.fromDynamicValue(schema2.toDynamicValue(Level1.Case)))(isRight(equalTo(Level1.Case))) &&
        assert(variant2.map(_.cases.map(_.name)))(isSome(equalTo(Vector("Level1.Case", "Case")))) &&
        assert(variant2.map(_.typeId))(
          isSome(equalTo(zio.blocks.typeid.TypeId.of[Level1.MultiLevel]))
        )
      },
      test("derives schema for higher-kinded variant using a macro call") {
        sealed trait `Variant-3`[F[_]]

        case class `Case-1`[F[_]](a: F[Double]) extends `Variant-3`[F]

        case class `Case-2`[F[_]](a: F[Float]) extends `Variant-3`[F]

        object Variant3OfOption extends CompanionOptics[`Variant-3`[Option]] {
          implicit val schema: Schema[`Variant-3`[Option]]        = Schema.derived
          val case1: Prism[`Variant-3`[Option], `Case-1`[Option]] = optic(_.when[`Case-1`[Option]])
          val case2: Prism[`Variant-3`[Option], `Case-2`[Option]] = optic(_.when[`Case-2`[Option]])
        }

        import Variant3OfOption._

        val variant = schema.reflect.asVariant
        assert(case1.getOption(`Case-1`[Option](Some(0.1))))(isSome(equalTo(`Case-1`[Option](Some(0.1))))) &&
        assert(case2.getOption(`Case-2`[Option](Some(0.2f))))(isSome(equalTo(`Case-2`[Option](Some(0.2f))))) &&
        assert(case1.replace(`Case-1`[Option](Some(0.1)), `Case-1`[Option](None)))(equalTo(`Case-1`[Option](None))) &&
        assert(case2.replace(`Case-2`[Option](Some(0.2f)), `Case-2`[Option](None)))(equalTo(`Case-2`[Option](None))) &&
        assert(schema.fromDynamicValue(schema.toDynamicValue(`Case-1`[Option](Some(0.1)))))(
          isRight(equalTo(`Case-1`[Option](Some(0.1))))
        ) &&
        assert(schema.fromDynamicValue(schema.toDynamicValue(`Case-2`[Option](None))))(
          isRight(equalTo(`Case-2`[Option](None)))
        ) &&
        assert(variant.map(_.cases.map(_.name)))(isSome(equalTo(Vector("Case-1", "Case-2")))) &&
        assert(variant.map(_.typeId))(
          isSome(equalTo(zio.blocks.typeid.TypeId.of[`Variant-3`[Option]]))
        )
      },
      test("derives schema for genetic variant with 'Nothing' type parameter using a macro call") {
        sealed trait Variant4[+E, +A]

        case class Error[E](error: E) extends Variant4[E, Nothing]

        case class Fatal(reason: String) extends Variant4[Nothing, Nothing]

        case class Success[A](a: A) extends Variant4[Nothing, A]

        case class Timeout() extends Variant4[Nothing, Nothing]

        val schema  = Schema.derived[Variant4[String, Int]]
        val variant = schema.reflect.asVariant
        assert(variant.map(_.cases.map(_.name)))(isSome(equalTo(Vector("Error", "Fatal", "Success", "Timeout")))) &&
        assert(variant.map(_.typeId))(
          isSome(equalTo(zio.blocks.typeid.TypeId.of[Variant4[String, Int]]))
        ) &&
        assert(schema.fromDynamicValue(schema.toDynamicValue(Error[String]("error"))))(
          isRight(equalTo(Error[String]("error")))
        ) &&
        assert(schema.fromDynamicValue(schema.toDynamicValue(Fatal("fatal"))))(isRight(equalTo(Fatal("fatal")))) &&
        assert(schema.fromDynamicValue(schema.toDynamicValue(Success[Int](1))))(isRight(equalTo(Success[Int](1)))) &&
        assert(schema.fromDynamicValue(schema.toDynamicValue(Timeout())))(isRight(equalTo(Timeout())))
      },
      test("encodes values using provided formats and outputs") {
        assert(encodeToString { out =>
          Schema[Variant].encode(ToStringFormat)(out)(Case1('a'))
        })(equalTo("Case1(a)"))
      },
      test("doesn't generate schema when all generic type parameters cannot be resolved") {
        typeCheck {
          """sealed trait Foo[F[_]] extends Product with Serializable

             case class FooImpl[F[_], A](fa: F[A], as: Vector[A]) extends Foo[F]

             sealed trait Bar[A] extends Product with Serializable

             case object Baz extends Bar[Int]

             case object Qux extends Bar[String]

             Schema.derived[Foo[Bar]]"""
        }.map(
          assert(_)(
            isLeft(
              containsString(
                "Type parameter 'A' of 'class FooImpl' can't be deduced from type arguments of 'Foo[Bar]'."
              ) || // Scala 2
                containsString(
                  "Type parameter 'A' of 'class FooImpl' can't be deduced from type arguments of 'Foo[[A >: scala.Nothing <: scala.Any] => Bar[A]]'."
                ) || // Scala 3.3
                containsString(
                  "Type parameter 'A' of 'class FooImpl' can't be deduced from type arguments of 'Foo[[A >: scala.Nothing <: scala.Any] =>> Bar[A]]'."
                ) // Scala 3.7
            )
          )
        )
      },
      test("doesn't generate schema for ADT-base without non-abstract subtypes") {
        typeCheck {
          """sealed trait X

             Schema.derived[X]"""
        }.map(assert(_)(isLeft(containsString("Cannot find sub-types for ADT base 'X'."))))
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
      test("gets and updates sequence default value") {
        assert(Schema[Vector[Int]].getDefaultValue)(isNone) &&
        assert(Schema[Vector[Int]].defaultValue(Vector.empty).getDefaultValue)(isSome(equalTo(Vector.empty)))
      },
      test("gets and updates sequence documentation") {
        assert(Schema[List[Double]].doc)(equalTo(Doc.Empty)) &&
        assert(Schema.derived[Seq[Int]].doc("Seq (updated)").doc)(equalTo(Doc("Seq (updated)")))
      },
      test("gets and updates sequence examples") {
        assert(Schema[List[Double]].examples)(equalTo(Seq.empty)) &&
        assert(Schema[Set[Int]].examples(Set(1, 2, 3)).examples)(equalTo(Seq(Set(1, 2, 3))))
      },
      test("gets and updates default values of sequence elements using optic focus") {
        val elements1 = Traversal.listValues(Reflect.int[Binding])
        val elements2 = Traversal.setValues(Reflect.long[Binding])
        assert(Schema[List[Int]].defaultValue(elements1, 1).getDefaultValue(elements1))(isSome(equalTo(1))) &&
        assert(Schema[Set[Long]].defaultValue(elements2, 1L).getDefaultValue(elements2))(isSome(equalTo(1L)))
      },
      test("gets and updates documentation of sequence elements using optic focus") {
        val elements1 = Traversal.listValues(Reflect.int[Binding])
        val elements2 = Traversal.setValues(Reflect.long[Binding])
        assert(Schema[List[Int]].doc(elements1, "Int").doc(elements1))(equalTo(Doc("Int"))) &&
        assert(Schema[Set[Long]].doc(elements2, "Long").doc(elements2))(equalTo(Doc("Long")))
      },
      test("gets and updates examples of sequence elements using optic focus") {
        val elements1 = Traversal.listValues(Reflect.int[Binding])
        val elements2 = Traversal.setValues(Reflect.long[Binding])
        assert(Schema[List[Int]].examples(elements1, 2).examples(elements1))(equalTo(Seq(2))) &&
        assert(Schema[Set[Long]].examples(elements2, 2L).examples(elements2))(equalTo(Seq(2L)))
      },
      test("appends sequence modifiers") {
        val schema1 = Schema[List[Int]]
        val schema2 = schema1.modifier(Modifier.config("key1", "value1"))
        assert(schema2.reflect.modifiers: Any)(equalTo(Seq(Modifier.config("key1", "value1")))) &&
        assert(
          schema2.modifiers(Seq(Modifier.config("key2", "value2"))).reflect.modifiers
        )(equalTo(Seq(Modifier.config("key1", "value1"), Modifier.config("key2", "value2"))))
      },
      test("has consistent newBuilder, add and result") {
        val schema1      = Schema.derived[Array[Int]]
        val schema2      = Schema.derived[ArraySeq[Int]]
        val constructor1 = schema1.reflect.asSequence.get.seqBinding.asInstanceOf[Binding.Seq[Array, Int]].constructor
        val constructor2 =
          schema2.reflect.asSequence.get.seqBinding.asInstanceOf[Binding.Seq[ArraySeq, Int]].constructor
        val xs1 = constructor1.newBuilder[Int](0)
        val xs2 = constructor2.newBuilder[Int](0)
        constructor1.add(xs1, 1)
        constructor2.add(xs2, 1)
        constructor1.add(xs1, 2)
        constructor2.add(xs2, 2)
        constructor1.add(xs1, 3)
        constructor2.add(xs2, 3)
        assert(constructor1.result(xs1))(equalTo(Array(1, 2, 3))) &&
        assert(constructor2.result(xs2))(equalTo(ArraySeq(1, 2, 3)))
      },
      test("has consistent toDynamicValue and fromDynamicValue") {
        assert(Schema[Seq[Int]].fromDynamicValue(Schema[Seq[Int]].toDynamicValue(Seq(1, 2, 3))))(
          isRight(equalTo(Seq(1, 2, 3)))
        ) &&
        assert(
          Schema
            .derived[Array[Array[Int]]]
            .fromDynamicValue(Schema.derived[Array[Array[Int]]].toDynamicValue(Array(Array(1, 2), Array(3, 4))))
            .map(_.map(_.toSeq).toSeq)
        )(isRight(equalTo(Seq(Seq(1, 2), Seq(3, 4))))) &&
        assert(Schema[List[Boolean]].fromDynamicValue(Schema[List[Boolean]].toDynamicValue(List(true, false))))(
          isRight(equalTo(List(true, false)))
        ) &&
        assert(Schema[List[Byte]].fromDynamicValue(Schema[List[Byte]].toDynamicValue(List(1: Byte, 2: Byte, 3: Byte))))(
          isRight(equalTo(List(1: Byte, 2: Byte, 3: Byte)))
        ) &&
        assert(Schema[List[Char]].fromDynamicValue(Schema[List[Char]].toDynamicValue(List('1', '2', '3'))))(
          isRight(equalTo(List('1', '2', '3')))
        ) &&
        assert(
          Schema[List[Short]].fromDynamicValue(Schema[List[Short]].toDynamicValue(List(1: Short, 2: Short, 3: Short)))
        )(isRight(equalTo(List(1: Short, 2: Short, 3: Short)))) &&
        assert(Schema[List[Float]].fromDynamicValue(Schema[List[Float]].toDynamicValue(List(1.0f, 2.0f, 3.0f))))(
          isRight(equalTo(List(1.0f, 2.0f, 3.0f)))
        ) &&
        assert(Schema[List[Int]].fromDynamicValue(Schema[List[Int]].toDynamicValue(List(1, 2, 3))))(
          isRight(equalTo(List(1, 2, 3)))
        ) &&
        assert(Schema[List[Double]].fromDynamicValue(Schema[List[Double]].toDynamicValue(List(1.0, 2.0, 3.0))))(
          isRight(equalTo(List(1.0, 2.0, 3.0)))
        ) &&
        assert(Schema[List[Long]].fromDynamicValue(Schema[List[Long]].toDynamicValue(List(1L, 2L, 3L))))(
          isRight(equalTo(List(1L, 2L, 3L)))
        ) &&
        assert(Schema[List[String]].fromDynamicValue(Schema[List[String]].toDynamicValue(List("VVV", "WWW"))))(
          isRight(equalTo(List("VVV", "WWW")))
        ) &&
        assert(Schema[List[Int]].fromDynamicValue(DynamicValue.Primitive(PrimitiveValue.Int(1))))(
          isLeft(hasError("Expected a sequence at: ."))
        ) &&
        assert(
          Schema[List[Boolean]].fromDynamicValue(
            DynamicValue.Sequence(
              Chunk(
                DynamicValue.Primitive(PrimitiveValue.Int(1)),
                DynamicValue.Primitive(PrimitiveValue.Int(1))
              )
            )
          )
        )(isLeft(hasError("Expected Boolean at: .each.at(0)\nExpected Boolean at: .each.at(1)"))) &&
        assert(
          Schema[List[Byte]].fromDynamicValue(
            DynamicValue.Sequence(Chunk(DynamicValue.Primitive(PrimitiveValue.Int(1))))
          )
        )(isLeft(hasError("Expected Byte at: .each.at(0)"))) &&
        assert(
          Schema[List[Char]].fromDynamicValue(
            DynamicValue.Sequence(Chunk(DynamicValue.Primitive(PrimitiveValue.Int(1))))
          )
        )(isLeft(hasError("Expected Char at: .each.at(0)"))) &&
        assert(
          Schema[List[Short]].fromDynamicValue(
            DynamicValue.Sequence(Chunk(DynamicValue.Primitive(PrimitiveValue.Int(1))))
          )
        )(isLeft(hasError("Expected Short at: .each.at(0)"))) &&
        assert(
          Schema[List[Int]].fromDynamicValue(
            DynamicValue.Sequence(Chunk(DynamicValue.Primitive(PrimitiveValue.Long(1))))
          )
        )(isLeft(hasError("Expected Int at: .each.at(0)"))) &&
        assert(
          Schema[List[Float]].fromDynamicValue(
            DynamicValue.Sequence(Chunk(DynamicValue.Primitive(PrimitiveValue.Int(1))))
          )
        )(isLeft(hasError("Expected Float at: .each.at(0)"))) &&
        assert(
          Schema[List[Long]].fromDynamicValue(
            DynamicValue.Sequence(Chunk(DynamicValue.Primitive(PrimitiveValue.Int(1))))
          )
        )(isLeft(hasError("Expected Long at: .each.at(0)"))) &&
        assert(
          Schema[List[Double]].fromDynamicValue(
            DynamicValue.Sequence(Chunk(DynamicValue.Primitive(PrimitiveValue.Int(1))))
          )
        )(isLeft(hasError("Expected Double at: .each.at(0)"))) &&
        assert(
          Schema[List[String]].fromDynamicValue(
            DynamicValue.Sequence(Chunk(DynamicValue.Primitive(PrimitiveValue.Int(1))))
          )
        )(isLeft(hasError("Expected String at: .each.at(0)"))) &&
        assert(
          Schema[List[Record]].fromDynamicValue(DynamicValue.Sequence(Chunk(DynamicValue.Record(Chunk.empty))))
        )(
          isLeft(
            equalTo(
              SchemaError(
                errors = ::(
                  MissingField(
                    source = DynamicOptic(nodes = Vector(Elements, AtIndex(0))),
                    fieldName = "b"
                  ),
                  ::(
                    MissingField(
                      source = DynamicOptic(nodes = Vector(Elements, AtIndex(0))),
                      fieldName = "i"
                    ),
                    Nil
                  )
                )
              )
            )
          )
        )
      },
      test("has consistent gets for typed and dynamic optics") {
        val elements1 = Traversal.listValues(Reflect.int[Binding])
        val elements2 = Traversal.setValues(Reflect.long[Binding])
        assert(Schema[List[Int]].get(elements1.toDynamic))(equalTo(Schema[List[Int]].get(elements1))) &&
        assert(Schema[Set[Long]].get(elements2.toDynamic))(equalTo(Schema[Set[Long]].get(elements2)))
      },
      test("encodes values using provided formats and outputs") {
        assert(encodeToString { out =>
          Schema[List[Int]].encode(ToStringFormat)(out)(List(1, 2, 3))
        })(equalTo("List(1, 2, 3)"))
      },
      test("Chunk roundtrips through DynamicValue") {
        val intChunk    = Chunk(1, 2, 3)
        val stringChunk = Chunk("a", "b", "c")
        val boolChunk   = Chunk(true, false, true)
        val doubleChunk = Chunk(1.0, 2.0, 3.0)
        val longChunk   = Chunk(1L, 2L, 3L)
        val emptyChunk  = Chunk.empty[Int]
        assert(Schema[Chunk[Int]].fromDynamicValue(Schema[Chunk[Int]].toDynamicValue(intChunk)))(
          isRight(equalTo(intChunk))
        ) &&
        assert(Schema[Chunk[String]].fromDynamicValue(Schema[Chunk[String]].toDynamicValue(stringChunk)))(
          isRight(equalTo(stringChunk))
        ) &&
        assert(Schema[Chunk[Boolean]].fromDynamicValue(Schema[Chunk[Boolean]].toDynamicValue(boolChunk)))(
          isRight(equalTo(boolChunk))
        ) &&
        assert(Schema[Chunk[Double]].fromDynamicValue(Schema[Chunk[Double]].toDynamicValue(doubleChunk)))(
          isRight(equalTo(doubleChunk))
        ) &&
        assert(Schema[Chunk[Long]].fromDynamicValue(Schema[Chunk[Long]].toDynamicValue(longChunk)))(
          isRight(equalTo(longChunk))
        ) &&
        assert(Schema[Chunk[Int]].fromDynamicValue(Schema[Chunk[Int]].toDynamicValue(emptyChunk)))(
          isRight(equalTo(emptyChunk))
        )
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
      test("gets and updates map default value") {
        assert(Schema[Map[Int, Long]].getDefaultValue)(isNone) &&
        assert(Schema[Map[Int, Long]].defaultValue(Map.empty).getDefaultValue)(isSome(equalTo(Map.empty[Int, Long])))
      },
      test("gets and updates map documentation") {
        assert(Schema[Map[Int, Long]].doc)(equalTo(Doc.Empty)) &&
        assert(Schema[Map[Int, Long]].doc("Map (updated)").doc)(equalTo(Doc("Map (updated)")))
      },
      test("gets and updates map examples") {
        val map1 = Reflect.Map[Binding, Int, Long, Map](
          key = Reflect.int,
          value = Reflect.long,
          typeId = zio.blocks.typeid.TypeId.of[Map[Int, Long]],
          mapBinding = Binding.Map[Map, Int, Long](
            constructor = MapConstructor.map,
            deconstructor = MapDeconstructor.map
          ),
          storedExamples = Seq(
            DynamicValue.Map(
              zio.blocks.chunk.Chunk(
                (DynamicValue.Primitive(PrimitiveValue.Int(1)), DynamicValue.Primitive(PrimitiveValue.Long(1L))),
                (DynamicValue.Primitive(PrimitiveValue.Int(2)), DynamicValue.Primitive(PrimitiveValue.Long(2L))),
                (DynamicValue.Primitive(PrimitiveValue.Int(3)), DynamicValue.Primitive(PrimitiveValue.Long(3L)))
              )
            )
          )
        )
        assert(Schema(map1).examples)(equalTo(Map(1 -> 1L, 2 -> 2L, 3 -> 3L) :: Nil)) &&
        assert(Schema[Map[Int, Long]].examples(Map(1 -> 2L, 2 -> 3L, 3 -> 4L)).examples)(
          equalTo(Map(1 -> 2L, 2 -> 3L, 3 -> 4L) :: Nil)
        )
      },
      test("gets and updates default values of map keys and values using optic focus") {
        val mapKeys   = Traversal.mapKeys(Reflect.map(Reflect.int[Binding], Reflect.long[Binding]))
        val mapValues = Traversal.mapValues(Reflect.map(Reflect.int[Binding], Reflect.long[Binding]))
        assert(Schema[Map[Int, Long]].defaultValue(mapKeys, 1).getDefaultValue(mapKeys))(isSome(equalTo(1))) &&
        assert(Schema[Map[Int, Long]].defaultValue(mapValues, 1L).getDefaultValue(mapValues))(isSome(equalTo(1L)))
      },
      test("gets and updates documentation of map keys and values using optic focus") {
        val mapKeys   = Traversal.mapKeys(Reflect.map(Reflect.int[Binding], Reflect.long[Binding]))
        val mapValues = Traversal.mapValues(Reflect.map(Reflect.int[Binding], Reflect.long[Binding]))
        assert(Schema[Map[Int, Long]].doc(mapKeys, "Int").doc(mapKeys))(equalTo(Doc("Int"))) &&
        assert(Schema[Map[Int, Long]].doc(mapValues, "Long").doc(mapValues))(equalTo(Doc("Long")))
      },
      test("gets and updates examples of map keys and values using optic focus") {
        val mapKeys   = Traversal.mapKeys(Reflect.map(Reflect.int[Binding], Reflect.long[Binding]))
        val mapValues = Traversal.mapValues(Reflect.map(Reflect.int[Binding], Reflect.long[Binding]))
        assert(Schema[Map[Int, Long]].examples(mapKeys, 2).examples(mapKeys))(equalTo(Seq(2))) &&
        assert(Schema[Map[Int, Long]].examples(mapValues, 2L).examples(mapValues))(equalTo(Seq(2L)))
      },
      test("appends map modifiers") {
        val schema1 = Schema[Map[Int, Long]]
        val schema2 = schema1.modifier(Modifier.config("key1", "value1"))
        assert(schema2.reflect.modifiers: Any)(equalTo(Seq(Modifier.config("key1", "value1")))) &&
        assert(
          schema2.modifiers(Seq(Modifier.config("key2", "value2"))).reflect.modifiers
        )(equalTo(Seq(Modifier.config("key1", "value1"), Modifier.config("key2", "value2"))))
      },
      test("has consistent toDynamicValue and fromDynamicValue") {
        assert(
          Schema[Map[Int, Long]].fromDynamicValue(Schema[Map[Int, Long]].toDynamicValue(Map(1 -> 1L, 2 -> 2L, 3 -> 3L)))
        )(isRight(equalTo(Map(1 -> 1L, 2 -> 2L, 3 -> 3L)))) &&
        assert(Schema[Map[Int, Long]].fromDynamicValue(DynamicValue.Primitive(PrimitiveValue.Int(1))))(
          isLeft(hasError("Expected a map at: ."))
        ) &&
        assert(
          Schema[Map[Int, Long]].fromDynamicValue(
            DynamicValue.Map(
              Chunk(
                (DynamicValue.Primitive(PrimitiveValue.Long(1)), DynamicValue.Primitive(PrimitiveValue.Int(1)))
              )
            )
          )
        )(isLeft(hasError("Expected Int at: .eachKey"))) &&
        assert(
          Schema[Map[Int, Long]].fromDynamicValue(
            DynamicValue.Map(
              Chunk(
                (DynamicValue.Primitive(PrimitiveValue.Int(1)), DynamicValue.Primitive(PrimitiveValue.Int(1))),
                (DynamicValue.Primitive(PrimitiveValue.Int(1)), DynamicValue.Primitive(PrimitiveValue.Int(1)))
              )
            )
          )
        )(
          isLeft(
            equalTo(
              SchemaError(
                errors = ::(
                  ExpectationMismatch(
                    source = DynamicOptic(nodes = Vector(MapValues, AtMapKey(Schema[Int].toDynamicValue(1)))),
                    expectation = "Expected Long"
                  ),
                  ::(
                    ExpectationMismatch(
                      source = DynamicOptic(nodes = Vector(MapValues, AtMapKey(Schema[Int].toDynamicValue(1)))),
                      expectation = "Expected Long"
                    ),
                    Nil
                  )
                )
              )
            )
          )
        )
      },
      test("has consistent gets for typed and dynamic optics") {
        val mapKeys   = Traversal.mapKeys(Reflect.map(Reflect.int[Binding], Reflect.long[Binding]))
        val mapValues = Traversal.mapValues(Reflect.map(Reflect.int[Binding], Reflect.long[Binding]))
        assert(Schema[Map[Int, Long]].get(mapKeys.toDynamic))(equalTo(Schema[Map[Int, Long]].get(mapKeys))) &&
        assert(Schema[Map[Int, Long]].get(mapValues.toDynamic))(equalTo(Schema[Map[Int, Long]].get(mapValues)))
      },
      test("encodes values using provided formats and outputs") {
        assert(encodeToString { out =>
          Schema[Map[Int, Char]].encode(ToStringFormat)(out)(Map(1 -> 'a', 2 -> 'b', 3 -> 'c'))
        })(equalTo("Map(1 -> a, 2 -> b, 3 -> c)"))
      }
    ),
    suite("Reflect.Dynamic")(
      test("has consistent equals and hashCode") {
        assert(Schema[DynamicValue])(equalTo(Schema[DynamicValue])) &&
        assert(Schema[DynamicValue].hashCode)(equalTo(Schema[DynamicValue].hashCode))
      },
      test("gets and updates dynamic default value") {
        val value = DynamicValue.Primitive(PrimitiveValue.Int(0))
        assert(Schema[DynamicValue].getDefaultValue)(isNone) &&
        assert(Schema[DynamicValue].defaultValue(value).getDefaultValue)(isSome(equalTo(value)))
      },
      test("gets and updates dynamic documentation") {
        assert(Schema[DynamicValue].doc)(equalTo(Doc.Empty)) &&
        assert(Schema[DynamicValue].doc("Dynamic (updated)").doc)(equalTo(Doc("Dynamic (updated)")))
      },
      test("gets and updates dynamic examples") {
        val value = DynamicValue.Primitive(PrimitiveValue.Int(1))
        assert(Schema[DynamicValue].examples)(equalTo(Seq.empty)) &&
        assert(Schema[DynamicValue].examples(value).examples)(equalTo(value :: Nil))
      },
      test("appends dynamic modifiers") {
        val schema1 = Schema[DynamicValue]
        val schema2 = schema1.modifier(Modifier.config("key1", "value1"))
        assert(schema2.reflect.modifiers: Any)(equalTo(Seq(Modifier.config("key1", "value1")))) &&
        assert(
          schema2.modifiers(Seq(Modifier.config("key2", "value2"))).reflect.modifiers
        )(equalTo(Seq(Modifier.config("key1", "value1"), Modifier.config("key2", "value2"))))
      },
      test("has consistent toDynamicValue and fromDynamicValue") {
        val value = DynamicValue.Primitive(PrimitiveValue.Int(1))
        assert(Schema[DynamicValue].fromDynamicValue(Schema[DynamicValue].toDynamicValue(value)))(
          isRight(equalTo(value))
        )
      },
      test("encodes values using provided formats and outputs") {
        assert(encodeToString { out =>
          Schema[DynamicValue].encode(ToStringFormat)(out)(DynamicValue.Primitive(PrimitiveValue.Int(1)))
        })(equalTo("1"))
      }
    ),
    suite("Reflect.Deferred")(
      test("has consistent equals and hashCode") {
        val deferred1 = Reflect.Deferred[Binding, Int](() => Reflect.int)
        val deferred2 = Reflect.Deferred[Binding, Int](() => Reflect.int)
        val deferred3 = Reflect.int[Binding]
        val deferred4 = Primitive(PrimitiveType.Int(Validation.Numeric.Positive), TypeId.int, Binding.Primitive.int)
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
      test("gets and updates deferred default value") {
        val deferred1 = Reflect.Deferred[Binding, Int](() => Reflect.int)
        assert(Schema(deferred1).getDefaultValue)(isNone) &&
        assert(Schema(deferred1).defaultValue(1).getDefaultValue)(isSome(equalTo(1)))
      },
      test("gets and updates sequence documentation") {
        val deferred1 = Reflect.Deferred[Binding, Int](() => Reflect.int)
        assert(Schema(deferred1).doc)(equalTo(Doc.Empty)) &&
        assert(Schema(deferred1).doc("Deferred (updated)").doc)(equalTo(Doc("Deferred (updated)")))
      },
      test("gets and updates deferred examples") {
        val deferred1 = Reflect.Deferred[Binding, Int] { () =>
          Primitive(
            PrimitiveType.Int(Validation.Numeric.Positive),
            TypeId.int,
            Binding.Primitive(),
            storedExamples = Seq(1, 2, 3).map(i => DynamicValue.Primitive(PrimitiveValue.Int(i)))
          )
        }
        assert(Schema(deferred1).examples)(equalTo(Seq(1, 2, 3))) &&
        assert(Schema(deferred1).examples(1, 2).examples)(equalTo(Seq(1, 2)))
      },
      test("gets and updates default values of deferred value using optic focus") {
        val deferred1 = Reflect.Deferred[Binding, Record](() => Record.schema.reflect)
        val deferred2 = Reflect.Deferred[Binding, Variant](() => Variant.schema.reflect)
        val deferred3 = Reflect.Deferred[Binding, List[Int]](() => Schema[List[Int]].reflect)
        val deferred4 = Reflect.Deferred[Binding, Map[Int, Long]](() => Schema[Map[Int, Long]].reflect)
        val elements  = Traversal.listValues(Reflect.int[Binding])
        val mapKeys   = Traversal.mapKeys(Reflect.map(Reflect.int[Binding], Reflect.long[Binding]))
        val mapValues = Traversal.mapValues(Reflect.map(Reflect.int[Binding], Reflect.long[Binding]))
        assert(Schema(deferred1).defaultValue(Record.b, 1: Byte).getDefaultValue(Record.b))(isSome(equalTo(1: Byte))) &&
        assert(Schema(deferred2).defaultValue(Variant.case1, Case1('1')).getDefaultValue(Variant.case1))(
          isSome(equalTo(Case1('1')))
        ) &&
        assert(Schema(deferred3).defaultValue(elements, 1).getDefaultValue(elements))(isSome(equalTo(1))) &&
        assert(Schema(deferred4).defaultValue(mapKeys, 1).getDefaultValue(mapKeys))(isSome(equalTo(1))) &&
        assert(Schema(deferred4).defaultValue(mapValues, 1L).getDefaultValue(mapValues))(isSome(equalTo(1L)))
      },
      test("gets and updates documentation of deferred value using optic focus") {
        val deferred1 = Reflect.Deferred[Binding, Record](() => Record.schema.reflect)
        val deferred2 = Reflect.Deferred[Binding, Variant](() => Variant.schema.reflect)
        val deferred3 = Reflect.Deferred[Binding, List[Int]](() => Schema[List[Int]].reflect)
        val deferred4 = Reflect.Deferred[Binding, Map[Int, Long]](() => Schema[Map[Int, Long]].reflect)
        val elements  = Traversal.listValues(Reflect.int[Binding])
        val mapKeys   = Traversal.mapKeys(Reflect.map(Reflect.int[Binding], Reflect.long[Binding]))
        val mapValues = Traversal.mapValues(Reflect.map(Reflect.int[Binding], Reflect.long[Binding]))
        assert(Schema(deferred1).doc(Record.b, "b").doc(Record.b))(equalTo(Doc("b"))) &&
        assert(Schema(deferred2).doc(Variant.case1, "Case1").doc(Variant.case1))(equalTo(Doc("Case1")))
        assert(Schema(deferred3).doc(elements, "Int").doc(elements))(equalTo(Doc("Int"))) &&
        assert(Schema(deferred4).doc(mapKeys, "Int").doc(mapKeys))(equalTo(Doc("Int"))) &&
        assert(Schema(deferred4).doc(mapValues, "Long").doc(mapValues))(equalTo(Doc("Long")))
      },
      test("gets and updates examples of deferred value using optic focus") {
        val deferred1 = Reflect.Deferred[Binding, Record](() => Record.schema.reflect)
        val deferred2 = Reflect.Deferred[Binding, Variant](() => Variant.schema.reflect)
        val deferred3 = Reflect.Deferred[Binding, List[Int]](() => Schema[List[Int]].reflect)
        val deferred4 = Reflect.Deferred[Binding, Map[Int, Long]](() => Schema[Map[Int, Long]].reflect)
        val elements  = Traversal.listValues(Reflect.int[Binding])
        val mapKeys   = Traversal.mapKeys(Reflect.map(Reflect.int[Binding], Reflect.long[Binding]))
        val mapValues = Traversal.mapValues(Reflect.map(Reflect.int[Binding], Reflect.long[Binding]))
        assert(Schema(deferred1).examples(Record.b, 2: Byte).examples(Record.b))(equalTo(Seq(2: Byte))) &&
        assert(Schema(deferred2).examples(Variant.case1, Case1('1')).examples(Variant.case1))(equalTo(Seq(Case1('1'))))
        assert(Schema(deferred3).examples(elements, 2).examples(elements))(equalTo(Seq(2))) &&
        assert(Schema(deferred4).examples(mapKeys, 2).examples(mapKeys))(equalTo(Seq(2))) &&
        assert(Schema(deferred4).examples(mapValues, 2L).examples(mapValues))(equalTo(Seq(2L)))
      },
      test("appends deferred modifiers") {
        val schema1 = Schema(Reflect.Deferred[Binding, Record](() => Record.schema.reflect))
        val schema2 = schema1.modifier(Modifier.config("key1", "value1"))
        assert(schema2.reflect.modifiers: Any)(equalTo(Seq(Modifier.config("key1", "value1")))) &&
        assert(
          schema2.modifiers(Seq(Modifier.config("key2", "value2"))).reflect.modifiers
        )(equalTo(Seq(Modifier.config("key1", "value1"), Modifier.config("key2", "value2"))))
      },
      test("has consistent toDynamicValue and fromDynamicValue") {
        val deferred1 = Reflect.Deferred[Binding, Record](() => Record.schema.reflect)
        val deferred2 = Reflect.Deferred[Binding, Variant](() => Variant.schema.reflect)
        assert(Schema(deferred1).fromDynamicValue(Schema(deferred1).toDynamicValue(Record(1: Byte, 1000))))(
          isRight(equalTo(Record(1: Byte, 1000)))
        ) &&
        assert(Schema(deferred2).fromDynamicValue(Schema(deferred2).toDynamicValue(Case1('1'))))(
          isRight(equalTo(Case1('1')))
        )
      },
      test("has consistent gets for typed and dynamic optics") {
        val deferred1 = Reflect.Deferred[Binding, Record](() => Record.schema.reflect)
        val deferred2 = Reflect.Deferred[Binding, Variant](() => Variant.schema.reflect)
        assert(Schema(deferred1).get(Record.b.toDynamic))(equalTo(Schema(deferred1).get(Record.b))) &&
        assert(Schema(deferred2).get(Variant.case1.toDynamic))(equalTo(Schema(deferred2).get(Variant.case1)))
      },
      test("encodes values using provided formats and outputs") {
        val deferred1 = Reflect.Deferred[Binding, Int](() => Reflect.int)
        assert(encodeToString(out => Schema(deferred1).encode(ToStringFormat)(out)(1)))(equalTo("1"))
      },
      test("helps to avoid stack overflow for schemas of recursive data structures") {
        case class Recursive(a: Int, @Modifier.config("field-key", "field-value") b: Option[Recursive] = None)

        def recursiveSchema: Schema[Recursive] = {
          implicit lazy val schema: Schema[Recursive] = Schema.derived[Recursive]
          schema
        }

        val recursive   = Recursive(1, Some(Recursive(2, Some(Recursive(3)))))
        val schema1     = recursiveSchema
        val schema2     = recursiveSchema
        val fieldValue1 = schema1.reflect.asRecord.get.fields(0).value
        val fieldValue2 = schema1.reflect.asRecord.get.fields(1).value
        val caseValue1  = fieldValue2.asVariant.get.cases(0).value
        val caseValue2  = fieldValue2.asVariant.get.cases(1).value
        val fieldValue3 = caseValue2.asRecord.get.fields(0).value
        assert(schema1.fromDynamicValue(schema1.toDynamicValue(recursive)))(isRight(equalTo(recursive))) &&
        assert(schema1 eq schema2)(equalTo(false)) &&
        assert(schema1.reflect)(equalTo(schema2.reflect)) &&
        assert(schema1.reflect.hashCode)(equalTo(schema2.reflect.hashCode)) &&
        assert(schema1.reflect: Any)(equalTo(fieldValue3)) &&
        assert(schema1.reflect.hashCode)(equalTo(fieldValue3.hashCode)) &&
        assert(schema1.reflect.noBinding: Any)(equalTo(schema1.reflect)) &&
        assert(fieldValue1.isInstanceOf[Reflect.Deferred[Binding, ?]])(equalTo(false)) &&
        assert(fieldValue2.isInstanceOf[Reflect.Deferred[Binding, ?]])(equalTo(true)) &&
        assert(fieldValue2.getDefaultValue: Option[Any])(isSome(equalTo(None))) &&
        assert(schema1.reflect.asRecord.get.fields(1).modifiers: Any)(
          equalTo(
            Seq(Modifier.config("field-key", "field-value"))
          )
        ) &&
        assert(caseValue1.isInstanceOf[Reflect.Deferred[Binding, ?]])(equalTo(false)) &&
        assert(caseValue2.isInstanceOf[Reflect.Deferred[Binding, ?]])(equalTo(false)) &&
        assert(fieldValue3.isInstanceOf[Reflect.Deferred[Binding, ?]])(equalTo(false)) &&
        assert(fieldValue2.isVariant)(equalTo(true)) &&
        assert(fieldValue2.isRecord)(equalTo(false)) &&
        assert(fieldValue2.isPrimitive)(equalTo(false)) &&
        assert(fieldValue2.isDynamic)(equalTo(false)) &&
        assert(fieldValue2.isSequence)(equalTo(false)) &&
        assert(fieldValue2.isMap)(equalTo(false)) &&
        assert(fieldValue2.asRecord)(isNone) &&
        assert(fieldValue2.asPrimitive)(isNone) &&
        assert(fieldValue2.asDynamic)(isNone) &&
        assert(fieldValue2.asSequenceUnknown)(isNone) &&
        assert(fieldValue2.asMapUnknown)(isNone)
      }
    ),
    suite("Reflect.Wrapper")(
      test("has consistent equals and hashCode") {
        assert(Schema[PosInt])(equalTo(Schema[PosInt])) &&
        assert(Schema[PosInt].hashCode)(equalTo(Schema[PosInt].hashCode)) &&
        assert(Schema[Email])(equalTo(Schema[Email])) &&
        assert(Schema[Email].hashCode)(equalTo(Schema[Email].hashCode)) &&
        assert(Schema[PosInt]: Any)(not(equalTo(Schema[Email])))
      },
      test("gets and updates wrapper default value") {
        val value = PosInt.applyUnsafe(1)
        assert(Schema[PosInt].getDefaultValue)(isNone) &&
        assert(Schema[PosInt].defaultValue(value).getDefaultValue)(isSome(equalTo(value)))
      },
      test("gets and updates wrapper documentation") {
        assert(Schema[PosInt].doc)(equalTo(Doc.Empty)) &&
        assert(Schema[PosInt].doc("Dynamic (updated)").doc)(equalTo(Doc("Dynamic (updated)")))
      },
      test("gets and updates wrapper examples") {
        val value = PosInt.applyUnsafe(1)
        assert(Schema[PosInt].examples)(equalTo(Seq.empty)) &&
        assert(Schema[PosInt].examples(value).examples)(equalTo(value :: Nil))
      },
      test("gets and updates default values of wrapped schema using optic focus") {
        assert(PosInt.schema.defaultValue(PosInt.wrapped, 1).getDefaultValue(PosInt.wrapped))(isSome(equalTo(1))) &&
        assert(Email.schema.defaultValue(Email.wrapped, "test@gmail.com").getDefaultValue(Email.wrapped))(
          isSome(equalTo("test@gmail.com"))
        )
      },
      test("gets and updates documentation of wrapped schema using optic focus") {
        assert(PosInt.schema.doc(PosInt.wrapped, "Int").doc(PosInt.wrapped))(equalTo(Doc("Int"))) &&
        assert(Email.schema.doc(Email.wrapped, "String").doc(Email.wrapped))(equalTo(Doc("String")))
      },
      test("gets and updates examples of wrapped schema using optic focus") {
        assert(PosInt.schema.examples(PosInt.wrapped, 2).examples(PosInt.wrapped))(equalTo(Seq(2))) &&
        assert(Email.schema.examples(Email.wrapped, "abc@gmail.com").examples(Email.wrapped))(
          equalTo(Seq("abc@gmail.com"))
        )
      },
      test("appends wrapped modifiers") {
        val schema1 = PosInt.schema
        val schema2 = schema1.modifier(Modifier.config("key1", "value1"))
        assert(schema2.reflect.modifiers: Any)(equalTo(Seq(Modifier.config("key1", "value1")))) &&
        assert(
          schema2.modifiers(Seq(Modifier.config("key2", "value2"))).reflect.modifiers
        )(equalTo(Seq(Modifier.config("key1", "value1"), Modifier.config("key2", "value2"))))
      },
      test("has consistent toDynamicValue and fromDynamicValue") {
        val value = PosInt.applyUnsafe(1)
        assert(Schema[PosInt].fromDynamicValue(Schema[PosInt].toDynamicValue(value)))(isRight(equalTo(value))) &&
        assert(Schema[PosInt].fromDynamicValue(DynamicValue.Primitive(PrimitiveValue.Int(-1))))(
          isLeft(hasError("Expected positive value"))
        ) &&
        assert(Schema[PosInt].fromDynamicValue(DynamicValue.Primitive(PrimitiveValue.String("WWW"))))(
          isLeft(hasError("Expected Int at: ."))
        )
      },
      test("has consistent toDynamicValue and fromDynamicValue with wrapper in a case class") {
        case class Test(a: PosInt)

        val value  = Test(PosInt.applyUnsafe(1))
        val schema = Schema.derived[Test]
        assert(schema.fromDynamicValue(schema.toDynamicValue(value)))(isRight(equalTo(value)))
      },
      test("encodes values using provided formats and outputs") {
        assert(encodeToString { out =>
          Schema[PosInt].encode(ToStringFormat)(out)(PosInt.applyUnsafe(1))
        })(equalTo("PosInt(1)"))
      }
    ),
    suite("transform (failable in one direction)")(
      test("succeeds when transformation succeeds") {
        case class Positive(value: Int)
        val positiveSchema: Schema[Positive] = Schema[Int].transform(
          to = n =>
            if (n > 0) Positive(n)
            else throw SchemaError.validationFailed(s"Expected positive, got $n"),
          from = _.value
        )
        val dv = Schema[Int].toDynamicValue(42)
        assert(positiveSchema.fromDynamicValue(dv))(isRight(equalTo(Positive(42))))
      },
      test("fails with SchemaError when transformation fails") {
        case class Positive(value: Int)
        val positiveSchema: Schema[Positive] = Schema[Int].transform(
          to = n =>
            if (n > 0) Positive(n)
            else throw SchemaError.validationFailed(s"Expected positive, got $n"),
          from = _.value
        )
        val dv = Schema[Int].toDynamicValue(-1)
        assert(positiveSchema.fromDynamicValue(dv))(isLeft(hasError("Expected positive, got -1")))
      },
      test("round-trips correctly") {
        case class Positive(value: Int)
        val positiveSchema: Schema[Positive] = Schema[Int].transform(
          to = n =>
            if (n > 0) Positive(n)
            else throw SchemaError.validationFailed(s"Expected positive, got $n"),
          from = _.value
        )
        val value = Positive(42)
        val dv    = positiveSchema.toDynamicValue(value)
        assert(positiveSchema.fromDynamicValue(dv))(isRight(equalTo(value)))
      },
      test("preserves SchemaError details") {
        case class Positive(value: Int)
        val customError                      = SchemaError.conversionFailed(Nil, "Custom validation error with details")
        val positiveSchema: Schema[Positive] = Schema[Int].transform(
          to = n =>
            if (n > 0) Positive(n)
            else throw customError,
          from = _.value
        )
        val dv     = Schema[Int].toDynamicValue(-1)
        val result = positiveSchema.fromDynamicValue(dv)
        assert(result)(isLeft(equalTo(customError)))
      },
      test("creates Wrapper reflect") {
        assertTrue(PosInt.schema.reflect.isWrapper)
      },
      test("works with existing PosInt wrapper schema") {
        val dv = Schema[Int].toDynamicValue(42)
        assert(PosInt.schema.fromDynamicValue(dv))(isRight(equalTo(PosInt.applyUnsafe(42))))
      }
    ),
    suite("transform (total)")(
      test("transforms schema with total functions") {
        case class UserId(value: Long)
        val userIdSchema: Schema[UserId] = Schema[Long].transform(to = UserId(_), from = _.value)
        val dv                           = Schema[Long].toDynamicValue(42L)
        assert(userIdSchema.fromDynamicValue(dv))(isRight(equalTo(UserId(42L))))
      },
      test("round-trips correctly") {
        case class UserId(value: Long)
        val userIdSchema: Schema[UserId] = Schema[Long].transform(to = UserId(_), from = _.value)
        val value                        = UserId(123L)
        val dv                           = userIdSchema.toDynamicValue(value)
        assert(userIdSchema.fromDynamicValue(dv))(isRight(equalTo(value)))
      },
      test("creates Wrapper reflect") {
        case class UserId(value: Long)
        val userIdSchema: Schema[UserId] = Schema[Long].transform(to = UserId(_), from = _.value)
        assertTrue(userIdSchema.reflect.isWrapper)
      },
      test("works with String-based wrappers") {
        assertTrue(Email.schema.reflect.isWrapper) &&
        assert(Email.schema.fromDynamicValue(Schema[String].toDynamicValue("test@example.com")))(
          isRight(equalTo(Email("test@example.com")))
        )
      }
    ),
    suite("transform (failable in both directions)")(
      test("succeeds when both wrap and unwrap succeed") {
        case class ValidatedInt(value: Int)
        val schema: Schema[ValidatedInt] = Schema[Int].transform[ValidatedInt](
          to = (n: Int) =>
            if (n > 0) ValidatedInt(n)
            else throw SchemaError.validationFailed(s"Expected positive, got $n"),
          from = (v: ValidatedInt) =>
            if (v.value < 100) v.value
            else throw SchemaError.validationFailed(s"Value too large: ${v.value}")
        )
        val dv = Schema[Int].toDynamicValue(42)
        assert(schema.fromDynamicValue(dv))(isRight(equalTo(ValidatedInt(42))))
      },
      test("fails when wrap fails") {
        case class ValidatedInt(value: Int)
        val schema: Schema[ValidatedInt] = Schema[Int].transform[ValidatedInt](
          to = (n: Int) =>
            if (n > 0) ValidatedInt(n)
            else throw SchemaError.validationFailed(s"Expected positive, got $n"),
          from = (v: ValidatedInt) => v.value
        )
        val dv = Schema[Int].toDynamicValue(-1)
        assert(schema.fromDynamicValue(dv))(isLeft(hasError("Expected positive, got -1")))
      },
      test("fails when unwrap fails during toDynamicValue") {
        case class ValidatedInt(value: Int)
        val schema: Schema[ValidatedInt] = Schema[Int].transform[ValidatedInt](
          to = (n: Int) => ValidatedInt(n),
          from = (v: ValidatedInt) =>
            if (v.value < 100) v.value
            else throw SchemaError.validationFailed(s"Value too large: ${v.value}")
        )
        val result = scala.util.Try(schema.toDynamicValue(ValidatedInt(200)))
        assertTrue(result.isFailure) &&
        assertTrue(result.failed.get.getMessage.contains("Value too large: 200"))
      },
      test("round-trips correctly when both directions succeed") {
        case class ValidatedInt(value: Int)
        val schema: Schema[ValidatedInt] = Schema[Int].transform[ValidatedInt](
          to = (n: Int) =>
            if (n > 0) ValidatedInt(n)
            else throw SchemaError.validationFailed(s"Expected positive, got $n"),
          from = (v: ValidatedInt) =>
            if (v.value < 100) v.value
            else throw SchemaError.validationFailed(s"Value too large: ${v.value}")
        )
        val value = ValidatedInt(42)
        val dv    = schema.toDynamicValue(value)
        assert(schema.fromDynamicValue(dv))(isRight(equalTo(value)))
      },
      test("creates Wrapper reflect") {
        case class ValidatedInt(value: Int)
        val schema: Schema[ValidatedInt] = Schema[Int].transform[ValidatedInt](
          to = (n: Int) => ValidatedInt(n),
          from = (v: ValidatedInt) => v.value
        )
        assertTrue(schema.reflect.isWrapper)
      }
    ),
    test("doesn't generate schema for unsupported classes") {
      typeCheck {
        "Schema.derived[java.util.Date]"
      }.map(assert(_)(isLeft(containsString("Cannot derive schema for 'java.util.Date'."))))
    },
    test("doesn't generate schema for case classes with non public parameters of the primary constructor") {
      typeCheck {
        """case class MultiListOfArgsWithNonPublicParam(i: Int)(l: Long)

           Schema.derived[MultiListOfArgsWithNonPublicParam]"""
      }.map(
        assert(_)(
          isLeft(
            containsString(
              "Field or getter 'l' of 'MultiListOfArgsWithNonPublicParam' should be defined as 'val' or 'var' in the primary constructor."
            )
          )
        )
      )
    },
    test("doesn't generate schema for classes with parameters in a primary constructor that have no accessor for read") {
      typeCheck {
        """class ParamHasNoAccessor(val i: Int, a: String)

           Schema.derived[ParamHasNoAccessor]"""
      }.map(
        assert(_)(
          isLeft(
            containsString(
              "Field or getter 'a' of 'ParamHasNoAccessor' should be defined as 'val' or 'var' in the primary constructor."
            )
          )
        )
      )
    },
    test(
      "doesn't generate schema for classes with transient fields that are neither optional nor collection nor have default value"
    ) {
      typeCheck {
        """case class WrongTransientField(i: Int, @Modifier.transient() a: String)

           Schema.derived[WrongTransientField]"""
      }.map(
        assert(_)(
          isLeft(
            containsString(
              "Missing default value for transient field 'a' in 'WrongTransientField'"
            )
          )
        )
      )
    },
    typeIdConsistencySuite
  )

  private[this] def hasError(message: String): Assertion[SchemaError] =
    hasField[SchemaError, String]("getMessage", _.getMessage, containsString(message))

  implicit val eitherSchema: Schema[Either[Int, Long]] = Schema.derived

  case class Record(b: Byte, i: Int)

  object Record extends CompanionOptics[Record] {
    implicit val schema: Schema[Record] = Schema.derived
    val b: Lens[Record, Byte]           = $(_.b)
    val i: Lens[Record, Int]            = $(_.i)
    val x: Lens[Record, Boolean]        = // invalid lens
      Lens(schema.reflect.asRecord.get, Reflect.boolean[Binding].asTerm("x").asInstanceOf[Term.Bound[Record, Boolean]])
  }

  sealed trait Variant

  object Variant extends CompanionOptics[Variant] {
    implicit val schema: Schema[Variant] = Schema.derived
    val case1: Prism[Variant, Case1]     = $(_.when[Case1])
    val case2: Prism[Variant, Case2]     = $(_.when[Case2])
  }

  case class Case1(c: Char) extends Variant

  case class Case2(s: String) extends Variant

  sealed trait A

  object B {
    case object A1 extends A

    case object A2 extends A
  }

  object A extends CompanionOptics[A] {
    implicit val schema: Schema[A] = Schema.derived
    val a1: Prism[A, B.A1.type]    = $(_.when[B.A1.type])
    val a2: Prism[A, B.A2.type]    = $(_.when[B.A2.type])
  }

  object Level1 {
    sealed trait MultiLevel

    case object Case extends MultiLevel
  }

  case object Case extends Level1.MultiLevel

  case class Box1(l: Long) extends AnyVal

  object Box1 extends CompanionOptics[Box1] {
    implicit val schema: Schema[Box1] = Schema.derived
  }

  case class Box2(s: String) extends AnyVal

  object Box2 extends CompanionOptics[Box2] {
    implicit val schema: Schema[Box2] = Schema.derived
  }

  case class PosInt private (value: Int) extends AnyVal

  object PosInt extends CompanionOptics[PosInt] {
    def apply(value: Int): Either[SchemaError, PosInt] =
      if (value >= 0) new Right(new PosInt(value))
      else new Left(SchemaError.validationFailed("Expected positive value"))

    def applyUnsafe(value: Int): PosInt =
      if (value >= 0) new PosInt(value)
      else throw new IllegalArgumentException("Expected positive value")

    implicit lazy val typeId: TypeId[PosInt] = TypeId.of[PosInt]
    implicit lazy val schema: Schema[PosInt] =
      Schema[Int].transform[PosInt](PosInt.applyUnsafe, _.value)
    val wrapped: Optional[PosInt, Int] = $(_.wrapped[Int])
  }

  case class Email(value: String)

  object Email extends CompanionOptics[Email] {
    implicit lazy val typeId: TypeId[Email] = TypeId.of[Email]
    implicit lazy val schema: Schema[Email] =
      Schema[String].transform[Email](x => new Email(x), _.value)
    val wrapped: Optional[Email, String] = $(_.wrapped[String])
  }

  def encodeToString(f: CharBuffer => Unit): String = {
    val out = CharBuffer.allocate(1024)
    f(out)
    out.limit(out.position()).position(0).toString
  }

  object ToStringFormat
      extends TextFormat(
        "text/plain",
        new Deriver[TextCodec] {
          override def derivePrimitive[A](
            primitiveType: PrimitiveType[A],
            typeId: zio.blocks.typeid.TypeId[A],
            binding: Binding[BindingType.Primitive, A],
            doc: Doc,
            modifiers: Seq[Modifier.Reflect],
            defaultValue: Option[A],
            examples: Seq[A]
          ): Lazy[TextCodec[A]] =
            Lazy(new TextCodec[A] {
              override def encode(value: A, output: CharBuffer): Unit = output.append(value.toString)

              override def decode(input: CharBuffer): Either[SchemaError, A] = ???
            })

          override def deriveRecord[F[_, _], A](
            fields: IndexedSeq[Term[F, A, ?]],
            typeId: zio.blocks.typeid.TypeId[A],
            binding: Binding[BindingType.Record, A],
            doc: Doc,
            modifiers: Seq[Modifier.Reflect],
            defaultValue: Option[A],
            examples: Seq[A]
          )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[TextCodec[A]] =
            Lazy(new TextCodec[A] {
              override def encode(value: A, output: CharBuffer): Unit = output.append(value.toString)

              override def decode(input: CharBuffer): Either[SchemaError, A] = ???
            })

          override def deriveVariant[F[_, _], A](
            cases: IndexedSeq[Term[F, A, ?]],
            typeId: zio.blocks.typeid.TypeId[A],
            binding: Binding[BindingType.Variant, A],
            doc: Doc,
            modifiers: Seq[Modifier.Reflect],
            defaultValue: Option[A],
            examples: Seq[A]
          )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[TextCodec[A]] =
            Lazy(new TextCodec[A] {
              override def encode(value: A, output: CharBuffer): Unit = output.append(value.toString)

              override def decode(input: CharBuffer): Either[SchemaError, A] = ???
            })

          override def deriveSequence[F[_, _], C[_], A](
            element: Reflect[F, A],
            typeId: zio.blocks.typeid.TypeId[C[A]],
            binding: Binding[BindingType.Seq[C], C[A]],
            doc: Doc,
            modifiers: Seq[Modifier.Reflect],
            defaultValue: Option[C[A]],
            examples: Seq[C[A]]
          )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[TextCodec[C[A]]] =
            Lazy(new TextCodec[C[A]] {
              override def encode(value: C[A], output: CharBuffer): Unit = output.append(value.toString)

              override def decode(input: CharBuffer): Either[SchemaError, C[A]] = ???
            })

          override def deriveMap[F[_, _], M[_, _], K, V](
            key: Reflect[F, K],
            value: Reflect[F, V],
            typeId: zio.blocks.typeid.TypeId[M[K, V]],
            binding: Binding[BindingType.Map[M], M[K, V]],
            doc: Doc,
            modifiers: Seq[Modifier.Reflect],
            defaultValue: Option[M[K, V]],
            examples: Seq[M[K, V]]
          )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[TextCodec[M[K, V]]] =
            Lazy(new TextCodec[M[K, V]] {
              override def encode(value: M[K, V], output: CharBuffer): Unit = output.append(value.toString)

              override def decode(input: CharBuffer): Either[SchemaError, M[K, V]] = ???
            })

          override def deriveDynamic[F[_, _]](
            binding: Binding[BindingType.Dynamic, DynamicValue],
            doc: Doc,
            modifiers: Seq[Modifier.Reflect],
            defaultValue: Option[DynamicValue],
            examples: Seq[DynamicValue]
          )(implicit
            F: HasBinding[F],
            D: HasInstance[F]
          ): Lazy[TextCodec[DynamicValue]] =
            Lazy(new TextCodec[DynamicValue] {
              override def encode(value: DynamicValue, output: CharBuffer): Unit = output.append(value.toString)

              override def decode(input: CharBuffer): Either[SchemaError, DynamicValue] = ???
            })

          override def deriveWrapper[F[_, _], A, B](
            wrapped: Reflect[F, B],
            typeId: zio.blocks.typeid.TypeId[A],
            binding: Binding[BindingType.Wrapper[A, B], A],
            doc: Doc,
            modifiers: Seq[Modifier.Reflect],
            defaultValue: Option[A],
            examples: Seq[A]
          )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[TextCodec[A]] =
            Lazy(new TextCodec[A] {
              override def encode(value: A, output: CharBuffer): Unit = output.append(value.toString)

              override def decode(input: CharBuffer): Either[SchemaError, A] = ???
            })
        }
      )

  case class ProductId(value: Long)

  object ProductId {
    implicit val customTypeId: TypeId[ProductId] =
      TypeId.nominal[ProductId]("ProductId", zio.blocks.typeid.Owner.fromPackagePath("com.acme.catalog"))

    implicit val schema: Schema[ProductId] =
      Schema[Long].transform[ProductId](ProductId.apply, _.value)
  }

  case class CategoryId(value: String)

  object CategoryId {
    implicit val customTypeId: TypeId[CategoryId] =
      TypeId.nominal[CategoryId]("CategoryId", zio.blocks.typeid.Owner.fromPackagePath("com.acme.catalog"))

    implicit val schema: Schema[CategoryId] =
      Schema[String].transform[CategoryId](CategoryId.apply, _.value)
  }

  case class ListContainer(items: List[ProductId])
  object ListContainer extends CompanionOptics[ListContainer] {
    implicit val schema: Schema[ListContainer] = Schema.derived
  }

  case class SetContainer(items: Set[ProductId])
  object SetContainer extends CompanionOptics[SetContainer] {
    implicit val schema: Schema[SetContainer] = Schema.derived
  }

  case class VectorContainer(items: Vector[ProductId])
  object VectorContainer extends CompanionOptics[VectorContainer] {
    implicit val schema: Schema[VectorContainer] = Schema.derived
  }

  case class MapContainer(items: scala.collection.immutable.Map[CategoryId, ProductId])
  object MapContainer extends CompanionOptics[MapContainer] {
    implicit val schema: Schema[MapContainer] = Schema.derived
  }

  case class OptionContainer(item: Option[ProductId])
  object OptionContainer extends CompanionOptics[OptionContainer] {
    implicit val schema: Schema[OptionContainer] = Schema.derived
  }

  case class EitherContainer(item: Either[CategoryId, ProductId])
  object EitherContainer extends CompanionOptics[EitherContainer] {
    implicit val schema: Schema[EitherContainer] = Schema.derived
  }

  case class ArrayContainer(items: Array[ProductId])
  object ArrayContainer extends CompanionOptics[ArrayContainer] {
    implicit val schema: Schema[ArrayContainer] = Schema.derived
  }

  case class ArraySeqContainer(items: ArraySeq[ProductId])
  object ArraySeqContainer extends CompanionOptics[ArraySeqContainer] {
    implicit val schema: Schema[ArraySeqContainer] = Schema.derived
  }

  def typeIdConsistencySuite: Spec[Any, Nothing] = suite("TypeId consistency between Schema and container typeArgs")(
    test("List element uses custom TypeId") {
      val schema       = Schema[ListContainer]
      val record       = schema.reflect.asRecord.get
      val field        = record.fields.find(_.name == "items").get
      val seqReflect   = field.value.asSequenceUnknown.get.sequence
      val elementTid   = seqReflect.element.typeId
      val containerTid = seqReflect.typeId

      assertTrue(elementTid.asInstanceOf[TypeId[Any]] == ProductId.customTypeId.asInstanceOf[TypeId[Any]]) &&
      assertTypeArgMatches(containerTid, 0, ProductId.customTypeId)
    },
    test("Set element uses custom TypeId") {
      val schema       = Schema[SetContainer]
      val record       = schema.reflect.asRecord.get
      val field        = record.fields.find(_.name == "items").get
      val seqReflect   = field.value.asSequenceUnknown.get.sequence
      val elementTid   = seqReflect.element.typeId
      val containerTid = seqReflect.typeId

      assertTrue(elementTid.asInstanceOf[TypeId[Any]] == ProductId.customTypeId.asInstanceOf[TypeId[Any]]) &&
      assertTypeArgMatches(containerTid, 0, ProductId.customTypeId)
    },
    test("Vector element uses custom TypeId") {
      val schema       = Schema[VectorContainer]
      val record       = schema.reflect.asRecord.get
      val field        = record.fields.find(_.name == "items").get
      val seqReflect   = field.value.asSequenceUnknown.get.sequence
      val elementTid   = seqReflect.element.typeId
      val containerTid = seqReflect.typeId

      assertTrue(elementTid.asInstanceOf[TypeId[Any]] == ProductId.customTypeId.asInstanceOf[TypeId[Any]]) &&
      assertTypeArgMatches(containerTid, 0, ProductId.customTypeId)
    },
    test("Map key and value use custom TypeIds") {
      val schema       = Schema[MapContainer]
      val record       = schema.reflect.asRecord.get
      val field        = record.fields.find(_.name == "items").get
      val mapReflect   = field.value.asMapUnknown.get.map
      val keyTid       = mapReflect.key.typeId
      val valueTid     = mapReflect.value.typeId
      val containerTid = mapReflect.typeId

      assertTrue(
        keyTid.asInstanceOf[TypeId[Any]] == CategoryId.customTypeId.asInstanceOf[TypeId[Any]],
        valueTid.asInstanceOf[TypeId[Any]] == ProductId.customTypeId.asInstanceOf[TypeId[Any]]
      ) &&
      assertTypeArgMatches(containerTid, 0, CategoryId.customTypeId) &&
      assertTypeArgMatches(containerTid, 1, ProductId.customTypeId)
    },
    test("Option element uses custom TypeId") {
      val schema       = Schema[OptionContainer]
      val record       = schema.reflect.asRecord.get
      val field        = record.fields.find(_.name == "item").get
      val optReflect   = field.value.asVariant.get
      val someCase     = optReflect.cases.find(_.name == "Some").get
      val someRecord   = someCase.value.asRecord.get
      val valueField   = someRecord.fields.find(_.name == "value").get
      val elementTid   = valueField.value.typeId
      val containerTid = optReflect.typeId

      assertTrue(elementTid.asInstanceOf[TypeId[Any]] == ProductId.customTypeId.asInstanceOf[TypeId[Any]]) &&
      assertTypeArgMatches(containerTid, 0, ProductId.customTypeId)
    },
    test("Either left and right use custom TypeIds") {
      val schema        = Schema[EitherContainer]
      val record        = schema.reflect.asRecord.get
      val field         = record.fields.find(_.name == "item").get
      val eitherReflect = field.value.asVariant.get
      val leftCase      = eitherReflect.cases.find(_.name == "Left").get
      val rightCase     = eitherReflect.cases.find(_.name == "Right").get
      val leftRecord    = leftCase.value.asRecord.get
      val rightRecord   = rightCase.value.asRecord.get
      val leftTid       = leftRecord.fields.find(_.name == "value").get.value.typeId
      val rightTid      = rightRecord.fields.find(_.name == "value").get.value.typeId
      val containerTid  = eitherReflect.typeId

      assertTrue(
        leftTid.asInstanceOf[TypeId[Any]] == CategoryId.customTypeId.asInstanceOf[TypeId[Any]],
        rightTid.asInstanceOf[TypeId[Any]] == ProductId.customTypeId.asInstanceOf[TypeId[Any]]
      ) &&
      assertTypeArgMatches(containerTid, 0, CategoryId.customTypeId) &&
      assertTypeArgMatches(containerTid, 1, ProductId.customTypeId)
    },
    test("Array element uses custom TypeId") {
      val schema       = Schema[ArrayContainer]
      val record       = schema.reflect.asRecord.get
      val field        = record.fields.find(_.name == "items").get
      val seqReflect   = field.value.asSequenceUnknown.get.sequence
      val elementTid   = seqReflect.element.typeId
      val containerTid = seqReflect.typeId

      assertTrue(elementTid.asInstanceOf[TypeId[Any]] == ProductId.customTypeId.asInstanceOf[TypeId[Any]]) &&
      assertTypeArgMatches(containerTid, 0, ProductId.customTypeId)
    },
    test("ArraySeq element uses custom TypeId") {
      val schema       = Schema[ArraySeqContainer]
      val record       = schema.reflect.asRecord.get
      val field        = record.fields.find(_.name == "items").get
      val seqReflect   = field.value.asSequenceUnknown.get.sequence
      val elementTid   = seqReflect.element.typeId
      val containerTid = seqReflect.typeId

      assertTrue(elementTid.asInstanceOf[TypeId[Any]] == ProductId.customTypeId.asInstanceOf[TypeId[Any]]) &&
      assertTypeArgMatches(containerTid, 0, ProductId.customTypeId)
    }
  )

  private def assertTypeArgMatches(
    containerTypeId: TypeId[_],
    index: Int,
    expectedTypeId: TypeId[_]
  ): TestResult = {
    val typeArgs = containerTypeId.typeArgs
    if (typeArgs.size <= index) {
      assertTrue(false)
    } else {
      typeArgs(index) match {
        case zio.blocks.typeid.TypeRepr.Ref(argTypeId) =>
          assertTrue(argTypeId == expectedTypeId.asInstanceOf[TypeId[Any]])
        case _ =>
          assertTrue(false)
      }
    }
  }
}
