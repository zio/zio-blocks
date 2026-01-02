package zio.blocks.schema

import neotype._
import zio.blocks.schema.SchemaError.ExpectationMismatch
import zio.blocks.schema.binding.Binding
import zio.test.Assertion._
import zio.test._

object NeotypeSupportSpec extends ZIOSpecDefault {
  def spec: Spec[TestEnvironment, Any] = suite("NeotypeSupportSpec")(
    test("derive schemas for cases classes with subtype and newtype fields") {
      val value = new Planet(Name("Earth"), Kilogram(5.97e24), Meter(6378000.0), Some(Meter(1.5e15)))
      assert(Planet.name.get(value))(equalTo(Name("Earth"))) &&
      assert(Planet.name_wrapped.getOption(value))(isSome(equalTo("Earth"))) &&
      assert(Planet.mass.get(value))(equalTo(Kilogram(5.97e24))) &&
      assert(Planet.radius.get(value))(equalTo(Meter(6378000.0))) &&
      assert(Planet.name_wrapped.replace(value, ""))(equalTo(value)) &&
      assert(Planet.name_wrapped.replace(value, "Cradle"))(
        equalTo(new Planet(Name("Cradle"), Kilogram(5.97e24), Meter(6378000.0), Some(Meter(1.5e15))))
      ) &&
      assert(Planet.mass.replace(value, Kilogram(5.970001e24)))(
        equalTo(new Planet(Name("Earth"), Kilogram(5.970001e24), Meter(6378000.0), Some(Meter(1.5e15))))
      ) &&
      assert(Planet.schema.fromDynamicValue(Planet.schema.toDynamicValue(value)))(isRight(equalTo(value))) &&
      assert(Planet.name.focus.typeName)(
        equalTo(TypeName[Name](Namespace(Seq("zio", "blocks", "schema"), Seq("NeotypeSupportSpec")), "Name"))
      ) &&
      assert(Planet.mass.focus.typeName)(
        equalTo(TypeName[Kilogram](Namespace(Seq("zio", "blocks", "schema"), Seq("NeotypeSupportSpec")), "Kilogram"))
      ) &&
      assert(Planet.radius.focus.typeName)(
        equalTo(TypeName[Meter](Namespace(Seq("zio", "blocks", "schema"), Seq("NeotypeSupportSpec")), "Meter"))
      ) &&
      assert(Planet.distanceFromSun.focus.typeName)(
        equalTo(
          TypeName.option(
            TypeName[Meter](Namespace(Seq("zio", "blocks", "schema"), Seq("NeotypeSupportSpec")), "Meter")
          )
        )
      )
    },
    test("derive schemas for cases classes and generic tuples with newtypes") {
      val value = new NRecord(
        NInt(1),
        NFloat(2.0f),
        NLong(3L),
        NDouble(4.0),
        NBoolean(true),
        NByte(6: Byte),
        NChar('7'),
        NShort(8: Short),
        NUnit(()),
        NString("VVV")
      )
      val schema = Schema.derived[NRecord]
      assert(schema.fromDynamicValue(schema.toDynamicValue(value)))(isRight(equalTo(value)))
    },
    test("derive schemas for options with newtypes and subtypes") {
      val schema1 = Schema.derived[Option[Name]]
      val schema2 = Schema.derived[Option[Kilogram]]
      val schema3 = Schema.derived[Option[Meter]]
      val schema4 = Schema.derived[Option[EmojiDataId]]
      val value1  = Option(Name("Earth"))
      val value2  = Option(Kilogram(5.97e24))
      val value3  = Option(Meter(6378000.0))
      val value4  = Option(EmojiDataId(123))
      assert(schema1.fromDynamicValue(schema1.toDynamicValue(value1)))(isRight(equalTo(value1))) &&
      assert(schema2.fromDynamicValue(schema2.toDynamicValue(value2)))(isRight(equalTo(value2))) &&
      assert(schema3.fromDynamicValue(schema3.toDynamicValue(value3)))(isRight(equalTo(value3))) &&
      assert(schema4.fromDynamicValue(schema4.toDynamicValue(value4)))(isRight(equalTo(value4))) &&
      assert(schema1.reflect.typeName)(
        equalTo(
          TypeName.option(
            TypeName[Name](Namespace(Seq("zio", "blocks", "schema"), Seq("NeotypeSupportSpec")), "Name")
          )
        )
      ) &&
      assert(schema2.reflect.typeName)(
        equalTo(
          TypeName.option(
            TypeName[Kilogram](Namespace(Seq("zio", "blocks", "schema"), Seq("NeotypeSupportSpec")), "Kilogram")
          )
        )
      ) &&
      assert(schema3.reflect.typeName)(
        equalTo(
          TypeName.option(
            TypeName[Meter](Namespace(Seq("zio", "blocks", "schema"), Seq("NeotypeSupportSpec")), "Meter")
          )
        )
      ) &&
      assert(schema4.reflect.typeName)(
        equalTo(
          TypeName.option(
            TypeName[EmojiDataId](Namespace(Seq("zio", "blocks", "schema"), Seq("NeotypeSupportSpec")), "EmojiDataId")
          )
        )
      )
    },
    test("derive schemas for collections with newtypes and subtypes") {
      val schema1 = Schema.derived[List[Name]]
      val schema2 = Schema.derived[Vector[Kilogram]]
      val schema3 = Schema.derived[Set[Meter]]
      val schema4 = Schema.derived[Map[EmojiDataId, Name]]
      val value1  = List(Name("Earth"), Name("Mars"))
      val value2  = Vector(Kilogram(5.97e24), Kilogram(5.970001e24))
      val value3  = Set(Meter(6378000.0))
      val value4  = Map(EmojiDataId(123) -> Name("Batmen"))
      assert(schema1.fromDynamicValue(schema1.toDynamicValue(value1)))(isRight(equalTo(value1))) &&
      assert(schema2.fromDynamicValue(schema2.toDynamicValue(value2)))(isRight(equalTo(value2))) &&
      assert(schema3.fromDynamicValue(schema3.toDynamicValue(value3)))(isRight(equalTo(value3))) &&
      assert(schema4.fromDynamicValue(schema4.toDynamicValue(value4)))(isRight(equalTo(value4))) &&
      assert(schema1.reflect.typeName)(
        equalTo(
          TypeName.list(
            TypeName[Name](Namespace(Seq("zio", "blocks", "schema"), Seq("NeotypeSupportSpec")), "Name")
          )
        )
      ) &&
      assert(schema2.reflect.typeName)(
        equalTo(
          TypeName.vector(
            TypeName[Kilogram](Namespace(Seq("zio", "blocks", "schema"), Seq("NeotypeSupportSpec")), "Kilogram")
          )
        )
      ) &&
      assert(schema3.reflect.typeName)(
        equalTo(
          TypeName.set(
            TypeName[Meter](Namespace(Seq("zio", "blocks", "schema"), Seq("NeotypeSupportSpec")), "Meter")
          )
        )
      ) &&
      assert(schema4.reflect.typeName)(
        equalTo(
          TypeName.map(
            TypeName[EmojiDataId](Namespace(Seq("zio", "blocks", "schema"), Seq("NeotypeSupportSpec")), "EmojiDataId"),
            TypeName[Name](Namespace(Seq("zio", "blocks", "schema"), Seq("NeotypeSupportSpec")), "Name")
          )
        )
      )
    },
    test("derive schemas for cases classes and collections with newtypes for primitives") {
      val value         = Stats(Some(Id(123)), DropRate(0.5), Array(ResponseTime(0.1), ResponseTime(0.23)))
      val invalidValue1 = Stats(None, DropRate.unsafeMake(2), Array.empty[ResponseTime])
      val invalidValue2 = Stats(None, DropRate(0.5), Array(ResponseTime.unsafeMake(-1.0)))
      val schema        = Schema[Stats]
      assert(Stats.id_some_value.getOption(value))(isSome(equalTo(123))) &&
      assert(Stats.id_some_value.replace(value, Id(1)))(
        equalTo(Stats(Some(Id(1)), DropRate(0.5), Array(ResponseTime(0.1), ResponseTime(0.23))))
      ) &&
      assert(Stats.dropRate_wrapped.getOption(value))(isSome(equalTo(0.5))) &&
      assert(Stats.dropRate_wrapped.replace(value, -0.1))(equalTo(value)) &&
      assert(Stats.responseTimes_wrapped.fold(value)(0.0, _ + _))(equalTo(0.33)) &&
      assert(Stats.responseTimes_wrapped.modify(value, _ - 1))(equalTo(value)) &&
      assert(schema.fromDynamicValue(schema.toDynamicValue(value)))(isRight(equalTo(value))) &&
      assert(schema.fromDynamicValue(schema.toDynamicValue(invalidValue1)))(
        isLeft(
          equalTo(
            SchemaError(
              errors = ::(
                ExpectationMismatch(
                  source = DynamicOptic(nodes = Vector(DynamicOptic.Node.Field("dropRate"))),
                  expectation = "Expected DropRate: Validation Failed"
                ),
                Nil
              )
            )
          )
        )
      ) &&
      assert(schema.fromDynamicValue(schema.toDynamicValue(invalidValue2)))(
        isLeft(
          equalTo(
            SchemaError(
              errors = ::(
                ExpectationMismatch(
                  source = DynamicOptic(
                    nodes = Vector(
                      DynamicOptic.Node.Field("responseTimes"),
                      DynamicOptic.Node.Elements,
                      DynamicOptic.Node.AtIndex(0)
                    )
                  ),
                  expectation = "Expected ResponseTime: Validation Failed"
                ),
                Nil
              )
            )
          )
        )
      )
    }
  )

  inline given newTypeSchema[A, B](using newType: Newtype.WithType[A, B], schema: Schema[A]): Schema[B] =
    Schema.derived[B].wrap[A](newType.make, newType.unwrap)

  type Name = Name.Type

  object Name extends Newtype[String] {
    override inline def validate(string: String): Boolean = string.length > 0
  }

  type Kilogram = Kilogram.Type

  object Kilogram extends Subtype[Double]

  type Meter = Meter.Type

  object Meter extends Newtype[Double]

  case class Planet(name: Name, mass: Kilogram, radius: Meter, distanceFromSun: Option[Meter])

  object Planet extends CompanionOptics[Planet] {
    implicit val schema: Schema[Planet]              = Schema.derived
    val name: Lens[Planet, Name]                     = $(_.name)
    val mass: Lens[Planet, Kilogram]                 = $(_.mass)
    val radius: Lens[Planet, Meter]                  = $(_.radius)
    val distanceFromSun: Lens[Planet, Option[Meter]] = $(_.distanceFromSun)
    val name_wrapped: Optional[Planet, String]       = $(_.name.wrapped[String])
  }

  object NInt extends Newtype[Int]

  object NFloat extends Newtype[Float]

  object NLong extends Newtype[Long]

  object NDouble extends Newtype[Double]

  object NBoolean extends Newtype[Boolean]

  object NByte extends Newtype[Byte]

  object NChar extends Newtype[Char]

  object NShort extends Newtype[Short]

  object NUnit extends Newtype[Unit]

  object NString extends Newtype[String]

  case class NRecord(
    i: NInt.Type,
    f: NFloat.Type,
    l: NLong.Type,
    d: NDouble.Type,
    bl: NBoolean.Type,
    b: NByte.Type,
    c: NChar.Type,
    sh: NShort.Type,
    u: NUnit.Type,
    s: NString.Type
  )

  type Id = Id.Type

  object Id extends Subtype[Int]

  type DropRate = DropRate.Type

  object DropRate extends Newtype[Double] {
    override inline def validate(input: Double): Boolean = input >= 0 && input <= 1
  }

  type ResponseTime = ResponseTime.Type

  object ResponseTime extends Newtype[Double] {
    override inline def validate(input: Double): Boolean = input > 0
  }

  case class Stats(id: Option[Id], dropRate: DropRate, responseTimes: Array[ResponseTime]) derives Schema {
    override def equals(obj: Any): Boolean = obj match {
      case that: Stats =>
        this.dropRate == that.dropRate && java.util.Arrays.equals(
          this.responseTimes.asInstanceOf[Array[Double]],
          that.responseTimes.asInstanceOf[Array[Double]]
        )
      case _ => false
    }

    override def hashCode(): Int =
      dropRate.hashCode * 31 + java.util.Arrays.hashCode(responseTimes.asInstanceOf[Array[Double]])
  }

  object Stats extends CompanionOptics[Stats] {
    val id_some_value: Optional[Stats, Id]              = $(_.id.when[Some[Id]].value)
    val dropRate_wrapped: Optional[Stats, Double]       = $(_.dropRate.wrapped[Double])
    val responseTimes_wrapped: Traversal[Stats, Double] = $(_.responseTimes.each.wrapped[Double])
  }

  type EmojiDataId = EmojiDataId.Type

  object EmojiDataId extends Subtype[Int]
}
