package zio.blocks.schema

import zio.Scope
import zio.blocks.schema.Reflect.Primitive
import zio.blocks.schema.binding._
import zio.test._
import zio.test.Assertion._
import zio.test.TestAspect._

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
      test("gets and updates default values of record fields using prism focus") {
        assert(Record.schema.defaultValue(Record.b, 1: Byte).getDefaultValue(Record.b))(isSome(equalTo(1: Byte))) &&
        assert(Record.schema.defaultValue(Record.i, 1000).getDefaultValue(Record.i))(isSome(equalTo(1000)))
      },
      test("gets and updates documentation of record fields using prism focus") {
        assert(Record.schema.doc(Record.b, "b").doc(Record.b))(equalTo(Doc("b"))) &&
        assert(Record.schema.doc(Record.i, "i").doc(Record.i))(equalTo(Doc("i")))
      },
      test("gets and updates examples of record fields using prism focus") {
        assert(Record.schema.examples(Record.b, 2: Byte).examples(Record.b))(equalTo(Seq(2: Byte))) &&
        assert(Record.schema.examples(Record.i, 2000).examples(Record.i))(equalTo(Seq(2000)))
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
          val `b-1`: Lens[Record1, Boolean]    = field(x => x.`b-1`)
          val `f-2`: Lens[Record1, Float]      = field(_.`f-2`)
        }

        val record = `Record-1`.schema.reflect.asInstanceOf[Reflect.Record[Binding, Record1]]
        assert(record.constructor.usedRegisters)(equalTo(RegisterOffset(booleans = 1, floats = 1))) &&
        assert(record.deconstructor.usedRegisters)(equalTo(RegisterOffset(booleans = 1, floats = 1))) &&
        assert(`Record-1`.`b-1`.focus.getDefaultValue)(isSome(equalTo(false))) &&
        assert(`Record-1`.`f-2`.focus.getDefaultValue)(isSome(equalTo(0.0f))) &&
        assert(`Record-1`.`b-1`.get(`Record-1`()))(equalTo(false)) &&
        assert(`Record-1`.`f-2`.get(`Record-1`()))(equalTo(0.0f)) &&
        assert(`Record-1`.`b-1`.replace(`Record-1`(), true))(equalTo(`Record-1`(`b-1` = true))) &&
        assert(`Record-1`.`f-2`.replace(`Record-1`(), 1.0f))(equalTo(`Record-1`(`b-1` = false, 1.0f))) &&
        assert(`Record-1`.schema)(
          equalTo(
            new Schema[Record1](
              reflect = Reflect.Record[Binding, Record1](
                fields = Seq(
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
                typeName = TypeName(
                  namespace = Namespace(
                    packages = Seq("zio", "blocks", "schema"),
                    values = Seq("SchemaSpec", "spec")
                  ),
                  name = "Record-1"
                ),
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
        case class `Record-2`[B, I](b: B, i: I = null.asInstanceOf[I])

        type Record2[B, I] = `Record-2`[B, I]
        type `i-8`         = Byte
        type `i-32`        = Int

        object `Record-2` extends CompanionOptics[Record2[`i-8`, `i-32`]] {
          implicit val schema: Schema[Record2[`i-8`, `i-32`]] = Schema.derived
          val b: Lens[Record2[`i-8`, `i-32`], `i-8`]          = field(_.b)
          val i: Lens[Record2[`i-8`, `i-32`], `i-32`]         = field(_.i)
        }

        val record = `Record-2`.schema.reflect.asInstanceOf[Reflect.Record[Binding, Record2[`i-8`, `i-32`]]]
        assert(record.constructor.usedRegisters)(equalTo(RegisterOffset(bytes = 1, ints = 1))) &&
        assert(record.deconstructor.usedRegisters)(equalTo(RegisterOffset(bytes = 1, ints = 1))) &&
        assert(`Record-2`.b.focus.getDefaultValue)(isNone) &&
        assert(`Record-2`.i.focus.getDefaultValue.isDefined)(equalTo(true)) &&
        assert(`Record-2`.b.get(`Record-2`[`i-8`, `i-32`](1, 2)))(equalTo(1: Byte)) &&
        assert(`Record-2`.i.get(`Record-2`[`i-8`, `i-32`](1, 2)))(equalTo(2)) &&
        assert(`Record-2`.b.replace(`Record-2`[`i-8`, `i-32`](1, 2), 3: Byte))(
          equalTo(`Record-2`[`i-8`, `i-32`](3, 2))
        ) &&
        assert(`Record-2`.i.replace(`Record-2`[`i-8`, `i-32`](1, 2), 3))(equalTo(`Record-2`[`i-8`, `i-32`](1, 3))) &&
        assert(`Record-2`.schema)(
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
                recordBinding = null
              )
            )
          )
        )
      } @@ jvmOnly, // FIXME: ClassCastException and NullPointerException in Scala.js and Scala Native accordingly
      test("derives schema for record with multi list constructor using a macro call") {
        class Record3(val s: Short)(val l: Long)

        object Record3 extends CompanionOptics[Record3] {
          implicit val schema: Schema[Record3] = Schema.derived
          val s: Lens[Record3, Short]          = field(_.s)
          val l: Lens[Record3, Long]           = field(_.l)
        }

        val record = Record3.schema.reflect.asInstanceOf[Reflect.Record[Binding, Record3]]
        assert(record.constructor.usedRegisters)(equalTo(RegisterOffset(shorts = 1, longs = 1))) &&
        assert(record.deconstructor.usedRegisters)(equalTo(RegisterOffset(shorts = 1, longs = 1))) &&
        assert(Record3.s.focus.getDefaultValue)(isNone) &&
        assert(Record3.l.focus.getDefaultValue)(isNone) &&
        assert(Record3.s.get(new Record3(1)(2L)))(equalTo(1: Short)) &&
        assert(Record3.l.get(new Record3(1)(2L)))(equalTo(2L)) &&
        assert(Record3.s.replace(new Record3(1)(2L), 3: Short).s)(equalTo(3: Short)) &&
        assert(Record3.l.replace(new Record3(1)(2L), 3L).l)(equalTo(3L)) &&
        assert(Record3.schema)(
          equalTo(
            new Schema[Record3](
              reflect = Reflect.Record[Binding, Record3](
                fields = Seq(
                  Schema[Short].reflect.asTerm("s"),
                  Schema[Long].reflect.asTerm("l")
                ),
                typeName = TypeName(
                  namespace = Namespace(
                    packages = Seq("zio", "blocks", "schema"),
                    values = Seq("SchemaSpec", "spec")
                  ),
                  name = "Record3"
                ),
                recordBinding = null
              )
            )
          )
        )
      },
      test("derives schema for record with nested collections using a macro call") {
        case class Record4(mx: Array[Array[Int]], rs: List[Set[Int]])

        object Record4 extends CompanionOptics[Record4] {
          implicit val schema: Schema[Record4] = Schema.derived
          val mx: Traversal[Record4, Int]      = field((x: Record4) => x.mx).arrayValues.arrayValues
          val rs: Traversal[Record4, Int]      = field(_.rs).listValues.setValues
        }

        val schema = Schema.derived[Record4]
        val record = schema.reflect.asInstanceOf[Reflect.Record[Binding, Record4]]
        assert(record.constructor.usedRegisters)(equalTo(RegisterOffset(objects = 2))) &&
        assert(record.deconstructor.usedRegisters)(equalTo(RegisterOffset(objects = 2))) &&
        assert(Record4.mx.focus.getDefaultValue)(isNone) &&
        assert(Record4.rs.focus.getDefaultValue)(isNone) &&
        assert(Record4.mx.fold[Int](Record4(Array(Array(1, 2), Array(3, 4)), Nil))(0, _ + _))(equalTo(10)) &&
        assert(Record4.rs.fold[Int](Record4(null, List(Set(1, 2), Set(3, 4))))(0, _ + _))(equalTo(10)) &&
        assert(Record4.mx.reduceOrFail(Record4(Array(Array(1, 2), Array(3, 4)), Nil))(_ + _))(isRight(equalTo(10))) &&
        assert(Record4.rs.reduceOrFail(Record4(null, List(Set(1, 2), Set(3, 4))))(_ + _))(isRight(equalTo(10))) &&
        assert(schema)(
          equalTo(
            new Schema[Record4](
              reflect = Reflect.Record[Binding, Record4](
                fields = Seq(
                  Schema[Array[Array[Int]]].reflect.asTerm("mx"),
                  Schema[List[Set[Int]]].reflect.asTerm("rs")
                ),
                typeName = TypeName(
                  namespace = Namespace(
                    packages = Seq("zio", "blocks", "schema"),
                    values = Seq("SchemaSpec", "spec")
                  ),
                  name = "Record4"
                ),
                recordBinding = null
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
      test("gets and updates variant default value") {
        assert(Variant.schema.getDefaultValue)(isNone) &&
        assert(Variant.schema.defaultValue(Case1(1.0)).getDefaultValue)(isSome(equalTo(Case1(1.0))))
      },
      test("gets and updates variant documentation") {
        assert(Variant.schema.doc)(equalTo(Doc.Empty)) &&
        assert(Variant.schema.doc("Variant (updated)").doc)(equalTo(Doc("Variant (updated)")))
      },
      test("gets and updates variant examples") {
        assert(Variant.schema.examples)(equalTo(Nil)) &&
        assert(Variant.schema.examples(Case1(1.0), Case2("VVV")).examples)(equalTo(Case1(1.0) :: Case2("VVV") :: Nil))
      },
      test("gets and updates default values of variant cases using prism focus") {
        assert(Variant.schema.defaultValue(Variant.case1, Case1(1.0)).getDefaultValue(Variant.case1))(
          isSome(equalTo(Case1(1.0)))
        ) &&
        assert(Variant.schema.defaultValue(Variant.case2, Case2("VVV")).getDefaultValue(Variant.case2))(
          isSome(equalTo(Case2("VVV")))
        )
      },
      test("gets and updates documentation of variant cases using prism focus") {
        assert(Variant.schema.doc(Variant.case1, "Case1").doc(Variant.case1))(equalTo(Doc("Case1"))) &&
        assert(Variant.schema.doc(Variant.case2, "Case2").doc(Variant.case2))(equalTo(Doc("Case2")))
      },
      test("gets and updates examples of variant cases using prism focus") {
        assert(Variant.schema.examples(Variant.case1, Case1(1.0)).examples(Variant.case1))(equalTo(Seq(Case1(1.0)))) &&
        assert(Variant.schema.examples(Variant.case2, Case2("VVV")).examples(Variant.case2))(equalTo(Seq(Case2("VVV"))))
      },
      test("derives schema for variant using a macro call") {
        @Modifier.config("variant-key", "variant-value-1")
        @Modifier.config("variant-key", "variant-value-2")
        sealed trait `Variant-1`

        type Variant1 = `Variant-1`

        case class `Case-1`(d: Double) extends Variant1

        object `Case-1` {
          implicit val schema: Schema[`Case-1`] = Schema.derived
        }

        case class `Case-2`(f: Float) extends Variant1

        object `Case-2` {
          implicit val schema: Schema[`Case-2`] = Schema.derived
        }

        object `Variant-1` extends CompanionOptics[Variant1] {
          implicit val schema: Schema[Variant1] = Schema.derived
          val case1: Prism[Variant1, `Case-1`]  = caseOf
          val case2: Prism[Variant1, `Case-2`]  = caseOf
        }

        assert(`Variant-1`.case1.getOption(`Case-1`(0.1)))(isSome(equalTo(`Case-1`(0.1)))) &&
        assert(`Variant-1`.case2.getOption(`Case-2`(0.2f)))(isSome(equalTo(`Case-2`(0.2f)))) &&
        assert(`Variant-1`.case1.replace(`Case-1`(0.1), `Case-1`(0.2)))(equalTo(`Case-1`(0.2))) &&
        assert(`Variant-1`.case2.replace(`Case-2`(0.2f), `Case-2`(0.3f)))(equalTo(`Case-2`(0.3f))) &&
        assert(`Variant-1`.schema)(
          equalTo(
            new Schema[Variant1](
              reflect = Reflect.Variant[Binding, Variant1](
                cases = Seq(
                  Schema[`Case-1`].reflect.asTerm("Case-1"),
                  Schema[`Case-2`].reflect.asTerm("Case-2")
                ),
                typeName = TypeName(
                  namespace = Namespace(
                    packages = Seq("zio", "blocks", "schema"),
                    values = Seq("SchemaSpec", "spec")
                  ),
                  name = "Variant-1"
                ),
                variantBinding = null,
                modifiers = Seq(
                  Modifier.config("variant-key", "variant-value-1"),
                  Modifier.config("variant-key", "variant-value-2")
                )
              )
            )
          )
        )
      },
      test("derives schema for genetic variant using a macro call") {
        sealed abstract class `Variant-2`[+A]

        type Variant2[A] = `Variant-2`[A]

        @Modifier.config("record-key", "record-value-1")
        @Modifier.config("record-key", "record-value-2")
        case object MissingValue extends Variant2[Nothing] {
          implicit val schema: Schema[MissingValue.type] = Schema.derived
        }

        case object NullValue extends Variant2[Null] {
          implicit val schema: Schema[NullValue.type] = Schema.derived
        }

        case class Value[A](a: A) extends Variant2[A]

        object Value {
          implicit def schema[A <: AnyRef: Schema]: Schema[Value[A]] = Schema.derived
        }

        object Variant2OfString extends CompanionOptics[Variant2[String]] {
          implicit val schema: Schema[Variant2[String]]                = Schema.derived
          val missingValue: Prism[Variant2[String], MissingValue.type] = caseOf
          val nullValue: Prism[Variant2[String], NullValue.type]       = caseOf
          val value: Prism[Variant2[String], Value[String]]            = caseOf
        }

        val record = Schema[MissingValue.type].reflect.asInstanceOf[Reflect.Record[Binding, MissingValue.type]]
        assert(record.modifiers)(
          equalTo(
            Seq(
              Modifier.config("record-key", "record-value-1"),
              Modifier.config("record-key", "record-value-2")
            )
          )
        ) &&
        assert(Variant2OfString.missingValue.getOption(MissingValue))(isSome(equalTo(MissingValue))) &&
        assert(Variant2OfString.nullValue.getOption(NullValue))(isSome(equalTo(NullValue))) &&
        assert(Variant2OfString.value.getOption(Value[String]("WWW")))(isSome(equalTo(Value[String]("WWW")))) &&
        assert(Variant2OfString.value.replace(Value[String]("WWW"), Value[String]("VVV")))(
          equalTo(Value[String]("VVV"))
        ) &&
        assert(Variant2OfString.schema)(
          equalTo(
            new Schema[Variant2[String]](
              reflect = Reflect.Variant[Binding, Variant2[String]](
                cases = Seq(
                  Schema[MissingValue.type].reflect
                    .asTerm("MissingValue")
                    .asInstanceOf[Term[Binding, Variant2[String], ? <: Variant2[String]]],
                  Schema[NullValue.type].reflect
                    .asTerm("NullValue")
                    .asInstanceOf[Term[Binding, Variant2[String], ? <: Variant2[String]]],
                  Schema[Value[String]].reflect
                    .asTerm("Value")
                    .asInstanceOf[Term[Binding, Variant2[String], ? <: Variant2[String]]]
                ),
                typeName = TypeName(
                  namespace = Namespace(
                    packages = Seq("zio", "blocks", "schema"),
                    values = Seq("SchemaSpec", "spec")
                  ),
                  name = "Variant-2"
                ),
                variantBinding = null
              )
            )
          )
        )
      },
      test("derives schema for higher-kinded variant using a macro call") {
        sealed trait `Variant-3`[F[_]]

        case class `Case-1`[F[_]](a: F[Double]) extends `Variant-3`[F]

        case class `Case-2`[F[_]](a: F[Float]) extends `Variant-3`[F]

        object Variant3OfOption extends CompanionOptics[`Variant-3`[Option]] {
          implicit val schemaCase1: Schema[`Case-1`[Option]]      = Schema.derived
          implicit val schemaCase2: Schema[`Case-2`[Option]]      = Schema.derived
          implicit val schema: Schema[`Variant-3`[Option]]        = Schema.derived
          val case1: Prism[`Variant-3`[Option], `Case-1`[Option]] = caseOf
          val case2: Prism[`Variant-3`[Option], `Case-2`[Option]] = caseOf
        }

        import Variant3OfOption._

        assert(case1.getOption(`Case-1`[Option](Some(0.1))))(isSome(equalTo(`Case-1`[Option](Some(0.1))))) &&
        assert(case2.getOption(`Case-2`[Option](Some(0.2f))))(isSome(equalTo(`Case-2`[Option](Some(0.2f))))) &&
        assert(case1.replace(`Case-1`[Option](Some(0.1)), `Case-1`[Option](None)))(equalTo(`Case-1`[Option](None))) &&
        assert(case2.replace(`Case-2`[Option](Some(0.2f)), `Case-2`[Option](None)))(equalTo(`Case-2`[Option](None))) &&
        assert(schema)(
          equalTo(
            new Schema[`Variant-3`[Option]](
              reflect = Reflect.Variant[Binding, `Variant-3`[Option]](
                cases = Seq(
                  Schema[`Case-1`[Option]].reflect.asTerm("Case-1"),
                  Schema[`Case-2`[Option]].reflect.asTerm("Case-2")
                ),
                typeName = TypeName(
                  namespace = Namespace(
                    packages = Seq("zio", "blocks", "schema"),
                    values = Seq("SchemaSpec", "spec")
                  ),
                  name = "Variant-3"
                ),
                variantBinding = null
              )
            )
          )
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
      test("gets and updates sequence default value") {
        assert(Schema[Vector[Int]].getDefaultValue)(isNone) &&
        assert(Schema[Vector[Int]].defaultValue(Vector.empty).getDefaultValue)(isSome(equalTo(Vector.empty)))
      },
      test("gets and updates sequence documentation") {
        assert(Schema[List[Double]].doc)(equalTo(Doc.Empty)) &&
        assert(Schema[Array[Int]].doc("Array (updated)").doc)(equalTo(Doc("Array (updated)")))
      },
      test("gets and updates sequence examples") {
        assert(Schema[List[Double]].examples)(equalTo(Seq.empty)) &&
        assert(Schema[Set[Int]].examples(Set(1, 2, 3)).examples)(equalTo(Seq(Set(1, 2, 3))))
      },
      test("has access to sequence value documentation using traversal focus") {
        val long1 = Primitive(
          primitiveType = PrimitiveType.Long(Validation.Numeric.Positive),
          primitiveBinding = null.asInstanceOf[Binding.Primitive[Long]],
          typeName = TypeName.long,
          doc = Doc("Long (positive)")
        )
        val sequence1 = Reflect.Sequence[Binding, Long, List](
          element = long1,
          typeName = TypeName.list,
          seqBinding = null,
          doc = Doc("List of positive longs")
        )
        assert(Schema(sequence1).doc(Traversal.listValues(long1)): Doc)(equalTo(Doc("Long (positive)")))
      },
      test("has access to sequence value examples using traversal focus") {
        val long1 = Primitive(
          primitiveType = PrimitiveType.Long(Validation.Numeric.Positive),
          primitiveBinding = Binding.Primitive[Long](examples = Seq(1L, 2L, 3L)),
          typeName = TypeName.long,
          doc = Doc("Long (positive)")
        )
        val sequence1 = Reflect.Sequence[Binding, Long, List](
          element = long1,
          typeName = TypeName.list,
          seqBinding = null,
          doc = Doc("List of positive longs")
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
          typeName = TypeName.map[Int, Long],
          mapBinding = Binding.Map[Map, Int, Long](
            constructor = MapConstructor.map,
            deconstructor = MapDeconstructor.map,
            examples = Map(1 -> 1L, 2 -> 2L, 3 -> 3L) :: Nil
          )
        )
        assert(Schema(map1).examples)(equalTo(Map(1 -> 1L, 2 -> 2L, 3 -> 3L) :: Nil)) &&
        assert(Schema[Map[Int, Long]].examples(Map(1 -> 2L, 2 -> 3L, 3 -> 4L)).examples)(
          equalTo(Map(1 -> 2L, 2 -> 3L, 3 -> 4L) :: Nil)
        )
      },
      test("has access to map key documentation using traversal focus") {
        val int1 = Primitive(
          primitiveType = PrimitiveType.Int(Validation.Numeric.Positive),
          primitiveBinding = Binding.Primitive[Int](),
          typeName = TypeName.int,
          doc = Doc("Int (positive)")
        )
        val map1 = Reflect.Map[Binding, Int, Long, Map](
          key = int1,
          value = Reflect.long,
          typeName = TypeName.map[Int, Long],
          mapBinding = null
        )
        assert(Schema(map1).doc(Traversal.mapKeys(Schema[Map[Int, Long]].reflect.asMap.get)): Doc)(
          equalTo(Doc("Int (positive)"))
        )
      },
      test("has access to map value documentation using traversal focus") {
        val long1 = Primitive(
          primitiveType = PrimitiveType.Long(Validation.Numeric.Positive),
          primitiveBinding = Binding.Primitive[Long](),
          typeName = TypeName.long,
          doc = Doc("Long (positive)")
        )
        val map1 = Reflect.Map[Binding, Int, Long, Map](
          key = Reflect.int,
          value = long1,
          typeName = TypeName.map[Int, Long],
          mapBinding = null
        )
        assert(Schema(map1).doc(Traversal.mapValues(Schema[Map[Int, Long]].reflect.asMap.get)): Doc)(
          equalTo(Doc("Long (positive)"))
        )
      },
      test("has access to map key examples using traversal focus") {
        val int1 = Primitive(
          primitiveType = PrimitiveType.Int(Validation.Numeric.Positive),
          primitiveBinding = Binding.Primitive[Int](examples = Seq(1, 2, 3)),
          typeName = TypeName.int
        )
        val map1 = Reflect.Map[Binding, Int, Long, Map](
          key = int1,
          value = Reflect.long,
          typeName = TypeName.map[Int, Long],
          mapBinding = null
        )
        assert(Schema(map1).examples(Traversal.mapKeys(Schema[Map[Int, Long]].reflect.asMap.get)): Seq[_])(
          equalTo(Seq(1, 2, 3))
        )
      },
      test("has access to map value examples using traversal focus") {
        val long1 = Primitive(
          primitiveType = PrimitiveType.Long(Validation.Numeric.Positive),
          primitiveBinding = Binding.Primitive[Long](examples = Seq(1L, 2L, 3L)),
          typeName = TypeName.long
        )
        val map1 = Reflect.Map[Binding, Int, Long, Map](
          key = Reflect.int,
          value = long1,
          typeName = TypeName.map[Int, Long],
          mapBinding = null
        )
        assert(Schema(map1).examples(Traversal.mapValues(Schema[Map[Int, Long]].reflect.asMap.get)): Seq[_])(
          equalTo(Seq(1L, 2L, 3L))
        )
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
            Binding.Primitive(examples = Seq(1, 2, 3)),
            TypeName.int
          )
        }
        assert(Schema(deferred1).examples)(equalTo(Seq(1, 2, 3))) &&
        assert(Schema(deferred1).examples(1, 2).examples)(equalTo(Seq(1, 2)))
      }
    )
  )

  case class Record(b: Byte, i: Int)

  object Record extends CompanionOptics[Record] {
    implicit val schema: Schema[Record] = Schema.derived
    val b: Lens[Record, Byte]           = field(_.b)
    val i: Lens[Record, Int]            = field(_.i)
  }

  sealed trait Variant

  object Variant extends CompanionOptics[Variant] {
    implicit val schema: Schema[Variant] = Schema.derived
    val case1: Prism[Variant, Case1]     = caseOf
    val case2: Prism[Variant, Case2]     = caseOf
  }

  case class Case1(d: Double) extends Variant

  object Case1 {
    implicit val schema: Schema[Case1] = Schema.derived
  }

  case class Case2(s: String) extends Variant

  object Case2 {
    implicit val schema: Schema[Case2] = Schema.derived
  }
}
