package zio.blocks.schema

import zio.blocks.schema.binding.Binding
import zio.blocks.typeid.{Owner, TypeId, TypeRepr}
import zio.prelude.{Newtype, Subtype}
import zio.test._
import zio.test.Assertion._

object ZIOPreludeSupportSpec extends SchemaBaseSpec {
  def spec: Spec[TestEnvironment, Any] = suite("ZIOPreludeSupportSpec")(
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
      assert(Planet.name.focus.typeId)(
        equalTo(
          TypeId.opaque[Name](
            "Name",
            Owner.fromPackagePath("zio.blocks.schema").term("ZIOPreludeSupportSpec"),
            representation = TypeRepr.Ref(TypeId.string)
          )
        )
      ) &&
      assert(Planet.mass.focus.typeId)(
        equalTo(
          TypeId.opaque[Kilogram](
            "Kilogram",
            Owner.fromPackagePath("zio.blocks.schema").term("ZIOPreludeSupportSpec"),
            representation = TypeRepr.Ref(TypeId.double)
          )
        )
      ) &&
      assert(Planet.radius.focus.typeId)(
        equalTo(
          TypeId.opaque[Meter](
            "Meter",
            Owner.fromPackagePath("zio.blocks.schema").term("ZIOPreludeSupportSpec"),
            representation = TypeRepr.Ref(TypeId.double)
          )
        )
      ) &&
      assert(Planet.distanceFromSun.focus.typeId)(
        equalTo(
          TypeId.of[Option[Meter]]
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
    }
  )

  private val zioPreludeOwner: Owner = Owner.fromPackagePath("zio.blocks.schema").term("ZIOPreludeSupportSpec")

  type Name = Name.Type

  object Name extends Newtype[String] {
    override def assertion = assert(!zio.prelude.Assertion.isEmptyString)

    implicit val typeId: TypeId[Name] =
      TypeId.opaque[Name]("Name", zioPreludeOwner, representation = TypeRepr.Ref(TypeId.string))
    implicit val schema: Schema[Name] = Schema[String]
      .transform[Name](
        s =>
          if (s.length > 0) s.asInstanceOf[Name]
          else throw SchemaError.validationFailed("String must not be empty"),
        (n: Name) => n.asInstanceOf[String]
      )
  }

  type Kilogram = Kilogram.Type

  object Kilogram extends Subtype[Double] {
    implicit val typeId: TypeId[Kilogram] =
      TypeId.opaque[Kilogram]("Kilogram", zioPreludeOwner, representation = TypeRepr.Ref(TypeId.double))
    implicit val schema: Schema[Kilogram] = Schema[Double]
      .transform[Kilogram](_.asInstanceOf[Kilogram], _.asInstanceOf[Double])
  }

  type Meter = Meter.Type

  object Meter extends Newtype[Double] {
    implicit val typeId: TypeId[Meter] =
      TypeId.opaque[Meter]("Meter", zioPreludeOwner, representation = TypeRepr.Ref(TypeId.double))
    implicit val schema: Schema[Meter] =
      Schema[Double].transform[Meter](_.asInstanceOf[Meter], _.asInstanceOf[Double])
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
}
