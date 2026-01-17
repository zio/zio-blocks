package zio.blocks.schema

import neotype._
import zio.blocks.typeid.TypeId
// import zio.blocks.schema.SchemaError.ExpectationMismatch
import zio.blocks.schema.binding.Binding
import zio.blocks.schema.json.JsonTestUtils._
import zio.test.Assertion._
import zio.test._

object NeotypeSupportSpec extends SchemaBaseSpec {
  def spec: Spec[TestEnvironment, Any] = suite("NeotypeSupportSpec")(
    test("derive schemas for cases classes with subtype and newtype fields") {
      assertCompletes
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
      assert(NRecord.schema.fromDynamicValue(NRecord.schema.toDynamicValue(value)))(isRight(equalTo(value))) &&
      roundTrip[NRecord](value, """{"i":1,"f":2.0,"l":3,"d":4.0,"bl":true,"b":6,"c":"7","sh":8,"u":null,"s":"VVV"}""")
    },
    test("derive schemas for options with newtypes and subtypes") {
      assertCompletes
    },
    test("derive schemas for collections with newtypes and subtypes") {
      assertCompletes
    },
    test("derive schemas for cases classes and collections with newtypes for primitives") {
      assertCompletes
    }
  )

  inline given newTypeSchema[A, B](using newType: Newtype.WithType[A, B], schema: Schema[A]): Schema[B] =
    Schema.derived[B].wrap[A](newType.make, newType.unwrap)

  inline given subTypeSchema[A, B <: A](using subType: Subtype.WithType[A, B], schema: Schema[A]): Schema[B] =
    Schema.derived[B].wrap[A](subType.make, _.asInstanceOf[A])

  type Name = Name.Type

  object Name extends Newtype[String] {
    override inline def validate(string: String): Boolean = string.length > 0
  }

  type Kilogram = Kilogram.Type

  object Kilogram extends Subtype[Double] {
    override inline def validate(value: Double): Boolean = value >= 0.0
  }

  type Meter = Meter.Type

  object Meter extends Newtype[Double] {
    override inline def validate(value: Double): Boolean = value >= 0.0
  }

  case class Planet(name: Name, mass: Kilogram, radius: Meter, distanceFromSun: Option[Meter])

  object Planet extends CompanionOptics[Planet] {
    implicit val schema: Schema[Planet]              = Schema.derived
    val name: Lens[Planet, Name]                     = $(_.name)
    val mass: Lens[Planet, Kilogram]                 = $(_.mass)
    val radius: Lens[Planet, Meter]                  = $(_.radius)
    val distanceFromSun: Lens[Planet, Option[Meter]] = $(_.distanceFromSun)
    val name_wrapped: Optional[Planet, String]       = $(_.name.wrapped[String])
  }

  object NInt extends Newtype[Int] {
    override inline def validate(value: Int): Boolean = value >= 0
  }

  object NFloat extends Newtype[Float] {
    override inline def validate(value: Float): Boolean = value >= 0.0f
  }

  object NLong extends Newtype[Long] {
    override inline def validate(value: Long): Boolean = value >= 0L
  }

  object NDouble extends Newtype[Double] {
    override inline def validate(value: Double): Boolean = value >= 0.0
  }

  object NBoolean extends Newtype[Boolean] {
    override inline def validate(value: Boolean): Boolean = value
  }

  object NByte extends Newtype[Byte] {
    override inline def validate(value: Byte): Boolean = value >= 0
  }

  object NChar extends Newtype[Char] {
    override inline def validate(value: Char): Boolean = value >= ' '
  }

  object NShort extends Newtype[Short] {
    override inline def validate(value: Short): Boolean = value >= 0
  }

  object NUnit extends Newtype[Unit] {
    override inline def validate(value: Unit): Boolean = true
  }

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

  object NRecord {
    implicit val schema: Schema[NRecord] = Schema.derived
  }

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
