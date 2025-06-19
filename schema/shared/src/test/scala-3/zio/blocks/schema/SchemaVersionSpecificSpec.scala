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

        /** Record: Record1 */
        case class Record1(c: Char, d: Double) derives Schema

        object Record1 extends CompanionOptics[Record1] {
          val c = optic(x => x.c)
          val d = optic(_.d)
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
          val case1 = optic(_.when[Case1])
          val case2 = optic(_.when[Case2])
          val case3 = optic(_.when[Case3.type])
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
            equalTo(Seq(Modifier.config("term-key-3", "term-value-1"), Modifier.config("term-key-3", "term-value-2")))
          )
        ) &&
        assert(record2.map(_.modifiers))(
          isSome(
            equalTo(Seq(Modifier.config("term-key-2", "term-value-1"), Modifier.config("term-key-2", "term-value-2")))
          )
        ) &&
        assert(record3.map(_.modifiers))(
          isSome(
            equalTo(Seq(Modifier.config("type-key", "type-value-1"), Modifier.config("type-key", "type-value-2")))
          )
        ) &&
        assert(record4.map(_.modifiers))(
          isSome(
            equalTo(Seq(Modifier.config("term-key-1", "term-value-1"), Modifier.config("term-key-1", "term-value-2")))
          )
        ) &&
        assert(record1.map(_.doc))(isSome(equalTo(Doc("/** Term: Blue */")))) &&
        assert(record2.map(_.doc))(isSome(equalTo(Doc("/** Term: Green */")))) &&
        assert(record3.map(_.doc))(isSome(equalTo(Doc("/** Type: Mix */")))) &&
        assert(record4.map(_.doc))(isSome(equalTo(Doc("/** Term: Red */")))) &&
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
        assert(variant.map(_.cases.map(_.name)))(isSome(equalTo(Vector("Blue", "Green", "Mix", "Red")))) &&
        assert(variant.map(_.typeName))(
          isSome(
            equalTo(
              TypeName(
                namespace = Namespace(
                  packages = Seq("zio", "blocks", "schema"),
                  values = Nil
                ),
                name = "Color"
              )
            )
          )
        ) &&
        assert(variant.map(_.doc))(isSome(equalTo(Doc("/** Variant: Color */"))))
      },
      test("derives schema for type recursive Scala 3 enums") {
        val schema  = Schema.derived[FruitEnum[_]]
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
                namespace = Namespace(
                  packages = Seq("zio", "blocks", "schema"),
                  values = Nil
                ),
                name = "FruitEnum"
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

        val variant = schema.reflect.asVariant
        assert(Value.int.getOption(123))(isSome(equalTo(123))) &&
        assert(Value.boolean.getOption(true))(isSome(equalTo(true))) &&
        assert(Value.int.replace(123, 321))(equalTo(321)) &&
        assert(Value.boolean.replace(true, false))(equalTo(false)) &&
        assert(schema.fromDynamicValue(schema.toDynamicValue(123)))(isRight(equalTo(123))) &&
        assert(schema.fromDynamicValue(schema.toDynamicValue(true)))(isRight(equalTo(true))) &&
        assert(schema)(equalTo(Schema.derived[Boolean | Int])) &&
        assert(variant.map(_.cases.map(_.name)))(isSome(equalTo(Vector("Boolean", "Int")))) &&
        assert(variant.map(_.typeName))(
          isSome(
            equalTo(
              TypeName(
                namespace = Namespace(
                  packages = Nil,
                  values = Nil
                ),
                name = "<none>"
              )
            )
          )
        )
      },
      test("derives schema for recursive generic Scala 3 enums") {
        implicit lazy val schema = Schema.derived[LinkedList[Int]]
        val variant              = schema.reflect.asVariant
        assert(schema.fromDynamicValue(schema.toDynamicValue(LinkedList.Node(2, LinkedList.Node(1, LinkedList.End)))))(
          isRight(equalTo(LinkedList.Node(2, LinkedList.Node(1, LinkedList.End))))
        ) &&
        assert(variant.map(_.cases.map(_.name)))(isSome(equalTo(Vector("End", "Node")))) &&
        assert(variant.map(_.typeName))(
          isSome(
            equalTo(
              TypeName(
                namespace = Namespace(
                  packages = Seq("zio", "blocks", "schema"),
                  values = Nil
                ),
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
                namespace = Namespace(
                  packages = Seq("zio", "blocks", "schema"),
                  values = Nil
                ),
                name = "HKEnum"
              )
            )
          )
        )
      }
    )
  )
}

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
  case Case1[A[_]](a: A[Int])    extends HKEnum[A]
  case Case2[A[_]](a: A[String]) extends HKEnum[A]
