package zio.blocks.schema

import zio.blocks.schema.binding._
import zio.test.Assertion._
import zio.test._

object SchemaVersionSpecificSpec extends ZIOSpecDefault {
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
        assert(record.map(_.typeName))(
          isSome(
            equalTo(
              TypeName(
                namespace = Namespace(
                  packages = Seq("zio", "blocks", "schema"),
                  values = Seq("SchemaVersionSpecificSpec", "spec")
                ),
                name = "Record1"
              )
            )
          )
        ) &&
        assert(record.map(_.doc))(isSome(equalTo(Doc("/** Record: Record1 */"))))
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
                typeName = TypeName(namespace = Namespace(packages = Seq("scala"), values = Nil), name = "Tuple4"),
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
                typeName = TypeName(namespace = Namespace(packages = Seq("scala"), values = Nil), name = "Tuple4"),
                recordBinding = null
              )
            )
          )
        )
      },
      test("derives schema for complex generic tuples") {
        val value1         = (1, "VVV")
        val value2         = ((1, 2L), ("VVV", "WWW"))
        val value3         = (Some(1), Some("VVV"))
        val expectedFields =
          Vector(Schema[Int].reflect.asTerm("_1"), Schema[String].reflect.asTerm("_2"))
        val schema1: Schema[Tuple.Tail[(Long, Int, String)]]                         = Schema.derived
        val schema2: Schema[Tuple.Init[(Int, String, Long)]]                         = Schema.derived
        val schema3: Schema[Tuple.Drop[(Long, Int, String), 1]]                      = Schema.derived
        val schema4: Schema[Tuple.Take[(Int, String, Long), 2]]                      = Schema.derived
        val schema5: Schema[Tuple.Concat[Tuple1[Int], Tuple1[String]]]               = Schema.derived
        val schema6: Schema[Tuple.Append[Tuple1[Int], String]]                       = Schema.derived
        val schema7: Schema[Tuple.Zip[(Int, String), (Long, String)]]                = Schema.derived
        val schema8: Schema[Tuple.InverseMap[(Option[Int], Option[String]), Option]] = Schema.derived
        val schema9: Schema[Tuple.Map[(Int, String), Option]]                        = Schema.derived
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
        assert(schema7.reflect.asRecord.get.fields)(
          equalTo(
            Vector(
              Schema.derived[(Int, Long)].reflect.asTerm("_1"),
              Schema.derived[(String, String)].reflect.asTerm("_2")
            )
          )
        ) &&
        assert(schema7.fromDynamicValue(schema7.toDynamicValue(value2)))(isRight(equalTo(value2))) &&
        assert(schema8.reflect.asRecord.get.fields)(equalTo(expectedFields)) &&
        assert(schema8.fromDynamicValue(schema8.toDynamicValue(value1)))(isRight(equalTo(value1))) &&
        assert(schema9.reflect.asRecord.get.fields)(
          equalTo(
            Vector(Schema[Option[Int]].reflect.asTerm("_1"), Schema[Option[String]].reflect.asTerm("_2"))
          )
        ) &&
        assert(schema9.fromDynamicValue(schema9.toDynamicValue(value3)))(isRight(equalTo(value3)))
      },
      test("derives schema for tuples with more than 22 fields") {
        type Tuple24 = (
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
          Box1,
          Box2,
          Int,
          String
        )

        object Tuple24 extends CompanionOptics[Tuple24] {
          implicit val schema: Schema[Tuple24] = Schema.derived
          val b21: Lens[Tuple24, Box1]         = $(_(20))
          val b22: Lens[Tuple24, Box2]         = $(_.apply(21))
          val i23: Lens[Tuple24, Int]          = $(_(22))
          val s24: Lens[Tuple24, String]       = $(_.apply(23))
        }

        val record = Tuple24.schema.reflect.asRecord
        val value  =
          (1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, Box1(21L), Box2("22"), 23, "24")
        assert(record.map(_.constructor.usedRegisters))(isSome(equalTo(RegisterOffset(ints = 21, objects = 3)))) &&
        assert(record.map(_.deconstructor.usedRegisters))(isSome(equalTo(RegisterOffset(ints = 21, objects = 3)))) &&
        assert(Tuple24.b21.get(value))(equalTo(Box1(21L))) &&
        assert(Tuple24.b22.get(value))(equalTo(Box2("22"))) &&
        assert(Tuple24.i23.get(value))(equalTo(23)) &&
        assert(Tuple24.s24.get(value))(equalTo("24")) &&
        assert(Tuple24.schema.fromDynamicValue(Tuple24.schema.toDynamicValue(value)))(isRight(equalTo(value)))
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
        assert(variant.map(_.typeName))(
          isSome(
            equalTo(
              TypeName(
                namespace = Namespace(
                  packages = Seq("zio", "blocks", "schema"),
                  values = Seq("SchemaVersionSpecificSpec", "spec")
                ),
                name = "Variant1"
              )
            )
          )
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
        assert(variant.map(_.typeName))(
          isSome(
            equalTo(
              TypeName(
                namespace =
                  Namespace(packages = Seq("zio", "blocks", "schema"), values = Seq("SchemaVersionSpecificSpec")),
                name = "Color"
              )
            )
          )
        ) &&
        assert(variant.map(_.doc))(isSome(equalTo(Doc("/** Variant: Color */"))))
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
        assert(variant.map(_.typeName))(
          isSome(
            equalTo(
              TypeName(
                namespace =
                  Namespace(packages = Seq("zio", "blocks", "schema"), values = Seq("SchemaVersionSpecificSpec")),
                name = "FruitEnum"
              )
            )
          )
        )
      },
      test("derives schema for Scala 3 unions") {
        type Value = Int | Boolean

        given schema: Schema[Value] = Schema.derived

        object Value extends CompanionOptics[Value] {
          val int: Prism[Value, Int]         = $(_.when[Int])
          val boolean: Prism[Value, Boolean] = $(_.when[Boolean])
        }

        val variant = schema.reflect.asVariant
        assert(Value.int.getOption(123))(isSome(equalTo(123))) &&
        assert(Value.boolean.getOption(true))(isSome(equalTo(true))) &&
        assert(Value.int.replace(123, 321))(equalTo(321)) &&
        assert(Value.boolean.replace(true, false))(equalTo(false)) &&
        assert(schema.fromDynamicValue(schema.toDynamicValue(123)))(isRight(equalTo(123))) &&
        assert(schema.fromDynamicValue(schema.toDynamicValue(true)))(isRight(equalTo(true))) &&
        assert(schema)(not(equalTo(Schema.derived[Boolean | Int]))) &&
        assert(variant.map(_.cases.map(_.name)))(isSome(equalTo(Vector("Int", "Boolean")))) &&
        assert(variant.map(_.typeName))(
          isSome(equalTo(TypeName(namespace = Namespace(packages = Nil, values = Nil), name = "|")))
        )
      },
      test("derives schema for recursive generic Scala 3 enums") {
        // `given` declaration is lazy that helps to avoid endless loops on recursive data structures,
        // see: https://users.scala-lang.org/t/how-to-deal-with-given-being-always-lazy/10844/5
        given schema: Schema[LinkedList[Int]] = Schema.derived
        val variant                           = schema.reflect.asVariant
        assert(schema.fromDynamicValue(schema.toDynamicValue(LinkedList.Node(2, LinkedList.Node(1, LinkedList.End)))))(
          isRight(equalTo(LinkedList.Node(2, LinkedList.Node(1, LinkedList.End))))
        ) &&
        assert(variant.map(_.cases.map(_.name)))(isSome(equalTo(Vector("End", "Node")))) &&
        assert(variant.map(_.typeName))(
          isSome(
            equalTo(
              TypeName(
                namespace =
                  Namespace(packages = Seq("zio", "blocks", "schema"), values = Seq("SchemaVersionSpecificSpec")),
                name = "LinkedList"
              )
            )
          )
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
        assert(variant.map(_.typeName))(
          isSome(
            equalTo(
              TypeName(
                namespace =
                  Namespace(packages = Seq("zio", "blocks", "schema"), values = Seq("SchemaVersionSpecificSpec")),
                name = "HKEnum"
              )
            )
          )
        )
      }
    )
  )

  /** Variant: Color */
  enum Color(val rgb: Int) derives Schema:
    /** Term: Red */
    @Modifier.config("term-key-1", "term-value-1") @Modifier.config("term-key-1", "term-value-2") case Red
        extends Color(0xff0000)

    /** Term: Green */
    @Modifier.config("term-key-2", "term-value-1") @Modifier.config("term-key-2", "term-value-2") case Green
        extends Color(0x00ff00)

    /** Term: Blue */
    @Modifier.config("term-key-3", "term-value-1") @Modifier.config("term-key-3", "term-value-2") case Blue
        extends Color(0x0000ff)

    /** Type: Mix */
    @Modifier.config("type-key", "type-value-1") @Modifier.config("type-key", "type-value-2") case Mix(mix: Int)
        extends Color(mix)

  object Color extends CompanionOptics[Color] {
    val red: Prism[Color, Color.Red.type]     = $(_.when[Color.Red.type])
    val green: Prism[Color, Color.Green.type] = $(_.when[Color.Green.type])
    val blue: Prism[Color, Color.Blue.type]   = $(_.when[Color.Blue.type])
    val mix: Prism[Color, Color.Mix]          = $(_.when[Color.Mix])
    val mix_mix: Optional[Color, Int]         = $(_.when[Color.Mix].mix)
  }

  enum FruitEnum[T <: FruitEnum[T]]:
    case Apple(color: String)      extends FruitEnum[Apple]
    case Banana(curvature: Double) extends FruitEnum[Banana]

  enum LinkedList[+T]:
    case End
    case Node(value: T, next: LinkedList[T])

  enum HKEnum[A[_]]:
    case Case1(a: A[Int])    extends HKEnum[A]
    case Case2(a: A[String]) extends HKEnum[A]

  case class Box1(l: Long) extends AnyVal derives Schema

  case class Box2(s: String) extends AnyVal derives Schema
}
