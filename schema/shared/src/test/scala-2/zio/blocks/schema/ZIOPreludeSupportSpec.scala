package zio.blocks.schema

import zio.blocks.schema.binding.Binding
import zio.blocks.typeid.TypeId
import zio.prelude.{Newtype, Subtype}
import zio.test._
import zio.test.Assertion._

import zio.blocks.typeid.{Owner, TypeDefKind, DynamicTypeId}

object ZIOPreludeSupportSpec extends SchemaBaseSpec {
  def spec: Spec[TestEnvironment, Any] = suite("ZIOPreludeSupportSpec")(
    test("derive schemas for cases classes with subtype and newtype fields") {
      val value = new Planet(Name("Earth"), Kilogram(5.97e24), Meter(6378000.0), Some(Meter(1.5e15)))

      val expectedOwner = Owner(
        List(
          Owner.Package("zio"),
          Owner.Package("blocks"),
          Owner.Package("schema"),
          Owner.Term("ZIOPreludeSupportSpec")
        )
      )
      def expectedTypeId(name: String): TypeId[Any] = TypeId(DynamicTypeId(
        expectedOwner,
        name,
        Nil,
        TypeDefKind.Class(isFinal = false, isAbstract = false, isCase = false, isValue = false),
        Nil,
        Nil
      )).asInstanceOf[TypeId[Any]]

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
      assert(Planet.name.focus.typeId.asInstanceOf[TypeId[Any]])(equalTo(expectedTypeId("Name"))) &&
      assert(Planet.mass.focus.typeId.asInstanceOf[TypeId[Any]])(equalTo(expectedTypeId("Kilogram"))) &&
      assert(Planet.radius.focus.typeId.asInstanceOf[TypeId[Any]])(equalTo(expectedTypeId("Meter"))) &&
      assert(TypeId(stripMetadata(Planet.distanceFromSun.focus.typeId).dynamic.copy(args = Nil)).asInstanceOf[TypeId[Any]])(
        equalTo(TypeId(stripMetadata(TypeId.from[Option[Meter]]).dynamic.copy(args = Nil)).asInstanceOf[TypeId[Any]])
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

  type Name = Name.Type

  object Name extends Newtype[String] {
    override def assertion = assert(!zio.prelude.Assertion.isEmptyString)

    implicit val schema: Schema[Name] = Schema[String]
      .transformOrFail[Name](
        s =>
          if (s.length > 0) Right(s.asInstanceOf[Name])
          else Left(SchemaError.validationFailed("String must not be empty")),
        (n: Name) => n.asInstanceOf[String]
      )
      .asOpaqueType[Name]
  }

  type Kilogram = Kilogram.Type

  object Kilogram extends Subtype[Double] {
    implicit val schema: Schema[Kilogram] = Schema.derived
  }

  type Meter = Meter.Type

  object Meter extends Newtype[Double] {
    implicit val schema: Schema[Meter] = Schema.derived
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

  object NInt extends Newtype[Int] { implicit val schema: Schema[NInt.Type] = Schema.derived }

  object NFloat extends Newtype[Float] { implicit val schema: Schema[NFloat.Type] = Schema.derived }

  object NLong extends Newtype[Long] { implicit val schema: Schema[NLong.Type] = Schema.derived }

  object NDouble extends Newtype[Double] { implicit val schema: Schema[NDouble.Type] = Schema.derived }

  object NBoolean extends Newtype[Boolean] { implicit val schema: Schema[NBoolean.Type] = Schema.derived }

  object NByte extends Newtype[Byte] { implicit val schema: Schema[NByte.Type] = Schema.derived }

  object NChar extends Newtype[Char] { implicit val schema: Schema[NChar.Type] = Schema.derived }

  object NShort extends Newtype[Short] { implicit val schema: Schema[NShort.Type] = Schema.derived }

  object NUnit extends Newtype[Unit] { implicit val schema: Schema[NUnit.Type] = Schema.derived }

  object NString extends Newtype[String] { implicit val schema: Schema[NString.Type] = Schema.derived }

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
