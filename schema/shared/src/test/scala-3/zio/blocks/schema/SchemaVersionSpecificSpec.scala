package zio.blocks.schema

import zio.Scope
import zio.blocks.schema.Reflect.Primitive
import zio.blocks.schema.binding.RegisterOffset.RegisterOffset
import zio.blocks.schema.binding._
import zio.test.Assertion._
import zio.test._

object SchemaVersionSpecificSpec extends ZIOSpecDefault {
  def spec: Spec[TestEnvironment with Scope, Any] = suite("SchemaVersionSpecificSpec")(
    suite("Reflect.Record")(
      test("derives schema using 'derives' keyword") {
        case class Record1(c: Char, d: Double) derives Schema

        object Record1 extends CompanionOptics[Record1] {
          val c = optic(x => x.c)
          val d = optic(_.d)
        }

        val schema = Schema[Record1]
        val record = schema.reflect.asInstanceOf[Reflect.Record[Binding, Record1]]
        val field1 = record.fields(0).asInstanceOf[Term.Bound[Record1, Char]]
        val field2 = record.fields(1).asInstanceOf[Term.Bound[Record1, Double]]
        assert(field1.value.binding.defaultValue)(isNone) &&
        assert(field2.value.binding.defaultValue)(isNone) &&
        assert(record.constructor.usedRegisters)(equalTo(RegisterOffset(chars = 1, doubles = 1))) &&
        assert(record.deconstructor.usedRegisters)(equalTo(RegisterOffset(chars = 1, doubles = 1))) &&
        assert(Record1.c.get(Record1('1', 2.0)))(equalTo('1')) &&
        assert(Record1.d.get(Record1('1', 2.0)))(equalTo(2.0)) &&
        assert(Record1.c.replace(Record1('1', 2.0), '3'))(equalTo(Record1('3', 2.0))) &&
        assert(Record1.d.replace(Record1('1', 2.0), 3.0))(equalTo(Record1('1', 3.0))) &&
        assert(schema.fromDynamicValue(schema.toDynamicValue(Record1('1', 2.0))))(
          isRight(equalTo(Record1('1', 2.0)))
        ) &&
        assert(schema)(
          equalTo(
            new Schema[Record1](
              reflect = Reflect.Record[Binding, Record1](
                fields = Vector(
                  Schema[Char].reflect.asTerm("c"),
                  Schema[Double].reflect.asTerm("d")
                ),
                typeName = TypeName(
                  namespace = Namespace(
                    packages = Seq("zio", "blocks", "schema"),
                    values = Seq("SchemaVersionSpecificSpec", "spec")
                  ),
                  name = "Record1"
                ),
                recordBinding = null
              )
            )
          )
        )
      }
    ),
    suite("Reflect.Variant")(
      test("derives schema for sealed traits using 'derives' keyword") {
        sealed trait Variant1 derives Schema

        case class Case1(d: Double) extends Variant1 derives Schema

        case class Case2() extends Variant1 derives Schema

        case object Case3 extends Variant1 derives Schema

        object Variant1 extends CompanionOptics[Variant1] {
          val case1 = optic(_.when[Case1])
          val case2 = optic(_.when[Case2])
          val case3 = optic(_.when[Case3.type])
        }

        val schema = Schema[Variant1]
        assert(Variant1.case1.getOption(Case1(0.1)))(isSome(equalTo(Case1(0.1)))) &&
        assert(Variant1.case2.getOption(Case2()))(isSome(equalTo(Case2()))) &&
        assert(Variant1.case3.getOption(Case3))(isSome(equalTo(Case3))) &&
        assert(Variant1.case1.replace(Case1(0.1), Case1(0.2)))(equalTo(Case1(0.2))) &&
        assert(Variant1.case2.replace(Case2(), Case2()))(equalTo(Case2())) &&
        assert(Variant1.case3.replace(Case3, Case3))(equalTo(Case3)) &&
        assert(schema.fromDynamicValue(schema.toDynamicValue(Case1(0.1))))(isRight(equalTo(Case1(0.1)))) &&
        assert(schema.fromDynamicValue(schema.toDynamicValue(Case2())))(isRight(equalTo(Case2()))) &&
        assert(schema.fromDynamicValue(schema.toDynamicValue(Case3)))(isRight(equalTo(Case3))) &&
        assert(schema)(
          equalTo(
            new Schema[Variant1](
              reflect = Reflect.Variant[Binding, Variant1](
                cases = Vector(
                  Schema[Case1].reflect.asTerm("Case1"),
                  Schema[Case2].reflect.asTerm("Case2"),
                  Schema[Case3.type].reflect.asTerm("Case3")
                ),
                typeName = TypeName(
                  namespace = Namespace(
                    packages = Seq("zio", "blocks", "schema"),
                    values = Seq("SchemaVersionSpecificSpec", "spec")
                  ),
                  name = "Variant1"
                ),
                variantBinding = null
              )
            )
          )
        )
      },
      test("derives schema for Scala 3 enums using 'derives' keyword") {
        val schema  = Schema[Color]
        val record1 = Schema[Color.Red.type].reflect.asInstanceOf[Reflect.Record[Binding, Color.Red.type]]
        val record2 = Schema[Color.Green.type].reflect.asInstanceOf[Reflect.Record[Binding, Color.Green.type]]
        val record3 = Schema[Color.Blue.type].reflect.asInstanceOf[Reflect.Record[Binding, Color.Blue.type]]
        val record4 = Schema[Color.Mix].reflect.asInstanceOf[Reflect.Record[Binding, Color.Mix]]
        assert(record1.modifiers)(
          equalTo(
            Seq(
              Modifier.config("term-key-1", "term-value-1"),
              Modifier.config("term-key-1", "term-value-2")
            )
          )
        ) &&
        assert(record2.modifiers)(
          equalTo(
            Seq(
              Modifier.config("term-key-2", "term-value-1"),
              Modifier.config("term-key-2", "term-value-2")
            )
          )
        ) &&
        assert(record3.modifiers)(
          equalTo(
            Seq(
              Modifier.config("term-key-3", "term-value-1"),
              Modifier.config("term-key-3", "term-value-2")
            )
          )
        ) &&
        assert(record4.modifiers)(
          equalTo(
            Seq(
              Modifier.config("type-key", "type-value-1"),
              Modifier.config("type-key", "type-value-2")
            )
          )
        ) &&
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
        assert(schema)(
          equalTo(
            new Schema[Color](
              reflect = Reflect.Variant[Binding, Color](
                cases = Vector(
                  Schema[Color.Blue.type].reflect.asTerm("Blue"),
                  Schema[Color.Green.type].reflect.asTerm("Green"),
                  Schema[Color.Mix].reflect.asTerm("Mix"),
                  Schema[Color.Red.type].reflect.asTerm("Red")
                ),
                typeName = TypeName(
                  namespace = Namespace(
                    packages = Seq("zio", "blocks", "schema"),
                    values = Nil
                  ),
                  name = "Color"
                ),
                variantBinding = null
              )
            )
          )
        )
      },
      test("derives schema for type recursive Scala 3 enums") {
        implicit val appleSchema  = Schema.derived[FruitEnum.Apple]
        implicit val bananaSchema = Schema.derived[FruitEnum.Banana]
        val schema                = Schema.derived[FruitEnum[_]]
        assert(schema.fromDynamicValue(schema.toDynamicValue(FruitEnum.Apple("red"))))(
          isRight(equalTo(FruitEnum.Apple("red")))
        ) &&
        assert(schema.fromDynamicValue(schema.toDynamicValue(FruitEnum.Banana(0.5))))(
          isRight(equalTo(FruitEnum.Banana(0.5)))
        ) &&
        assert(schema)(
          equalTo(
            new Schema[FruitEnum[_]](
              reflect = Reflect.Variant[Binding, FruitEnum[_]](
                cases = Vector(
                  Schema[FruitEnum.Apple].reflect.asTerm("Apple"),
                  Schema[FruitEnum.Banana].reflect.asTerm("Banana")
                ),
                typeName = TypeName(
                  namespace = Namespace(
                    packages = Seq("zio", "blocks", "schema"),
                    values = Nil
                  ),
                  name = "FruitEnum"
                ),
                variantBinding = null
              )
            )
          )
        )
      },
      test("derives schema for Scala 3 unions") {
        type Value = Int | Boolean

        implicit val schema = Schema.derived[Value]

        object Value extends CompanionOptics[Value] {
          val int     = $(_.when[Int])
          val boolean = $(_.when[Boolean])
        }

        assert(Value.int.getOption(123))(isSome(equalTo(123))) &&
        assert(Value.boolean.getOption(true))(isSome(equalTo(true))) &&
        assert(Value.int.replace(123, 321))(equalTo(321)) &&
        assert(Value.boolean.replace(true, false))(equalTo(false)) &&
        assert(schema.fromDynamicValue(schema.toDynamicValue(123)))(isRight(equalTo(123))) &&
        assert(schema.fromDynamicValue(schema.toDynamicValue(true)))(isRight(equalTo(true))) &&
        assert(schema)(equalTo(Schema.derived[Boolean | Int])) &&
        assert(schema)(
          equalTo(
            new Schema[Value](
              reflect = Reflect.Variant[Binding, Value](
                cases = Vector(
                  Schema[Boolean].reflect.asTerm("Boolean"),
                  Schema[Int].reflect.asTerm("Int")
                ),
                typeName = TypeName(
                  namespace = Namespace(
                    packages = Nil,
                    values = Nil
                  ),
                  name = "<none>"
                ),
                variantBinding = null
              )
            )
          )
        )
      },
      test("derives schema for recursive generic Scala 3 enums") {
        implicit val endSchema       = Schema.derived[LinkedList.End.type]
        implicit lazy val nodeSchema = Schema.derived[LinkedList.Node[Int]]
        implicit lazy val schema     = Schema.derived[LinkedList[Int]]
        assert(schema.fromDynamicValue(schema.toDynamicValue(LinkedList.Node(2, LinkedList.Node(1, LinkedList.End)))))(
          isRight(equalTo(LinkedList.Node(2, LinkedList.Node(1, LinkedList.End))))
        ) &&
        assert(schema)(
          equalTo(
            new Schema[LinkedList[Int]](
              reflect = Reflect.Variant[Binding, LinkedList[Int]](
                cases = Vector(
                  Schema[LinkedList.End.type].reflect.asTerm("End"),
                  Schema[LinkedList.Node[Int]].reflect.asTerm("Node")
                ),
                typeName = TypeName(
                  namespace = Namespace(
                    packages = Seq("zio", "blocks", "schema"),
                    values = Nil
                  ),
                  name = "LinkedList"
                ),
                variantBinding = null
              )
            )
          )
        )
      },
      test("derives schema for higher-kinded Scala 3 enums") {
        implicit val case1Schema = Schema.derived[HKEnum.Case1[Option]]
        implicit val case2Schema = Schema.derived[HKEnum.Case2[Option]]
        val schema               = Schema.derived[HKEnum[Option]]
        assert(schema.fromDynamicValue(schema.toDynamicValue(HKEnum.Case1(Some(1)))))(
          isRight(equalTo(HKEnum.Case1(Some(1))))
        ) &&
        assert(schema.fromDynamicValue(schema.toDynamicValue(HKEnum.Case2(Some("WWW")))))(
          isRight(equalTo(HKEnum.Case2(Some("WWW"))))
        ) &&
        assert(schema)(
          equalTo(
            new Schema[HKEnum[Option]](
              reflect = Reflect.Variant[Binding, HKEnum[Option]](
                cases = Vector(
                  Schema[HKEnum.Case1[Option]].reflect.asTerm("Case1"),
                  Schema[HKEnum.Case2[Option]].reflect.asTerm("Case2")
                ),
                typeName = TypeName(
                  namespace = Namespace(
                    packages = Seq("zio", "blocks", "schema"),
                    values = Nil
                  ),
                  name = "HKEnum"
                ),
                variantBinding = null
              )
            )
          )
        )
      }
    )
  )
}

enum Color(val rgb: Int) derives Schema:
  @Modifier.config("term-key-1", "term-value-1") @Modifier.config("term-key-1", "term-value-2") case Red
      extends Color(0xff0000)
  @Modifier.config("term-key-2", "term-value-1") @Modifier.config("term-key-2", "term-value-2") case Green
      extends Color(0x00ff00)
  @Modifier.config("term-key-3", "term-value-1") @Modifier.config("term-key-3", "term-value-2") case Blue
      extends Color(0x0000ff)
  @Modifier.config("type-key", "type-value-1") @Modifier.config("type-key", "type-value-2") case Mix(mix: Int)
      extends Color(mix)

object Color extends CompanionOptics[Color] {
  implicit val redSchema: Schema[Color.Red.type]     = Schema.derived
  implicit val greenSchema: Schema[Color.Green.type] = Schema.derived
  implicit val blueSchema: Schema[Color.Blue.type]   = Schema.derived

  object Mix extends CompanionOptics[Color.Mix] {
    implicit val schema: Schema[Color.Mix] = Schema.derived
    val mix: Lens[Color.Mix, Int]          = $(_.mix)
  }

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
  case Case1[A[_]](a: A[Int])    extends HKEnum[A]
  case Case2[A[_]](a: A[String]) extends HKEnum[A]
