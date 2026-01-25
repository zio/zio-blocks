package zio.blocks.schema

import zio.blocks.schema.binding.Binding
import zio.blocks.typeid.{Owner, TypeId}
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
          TypeId.nominal[Name]("Name", Owner.fromPackagePath("zio.blocks.schema").term("ZIOPreludeSupportSpec"))
        )
      ) &&
      assert(Planet.mass.focus.typeId)(
        equalTo(
          TypeId.nominal[Kilogram]("Kilogram", Owner.fromPackagePath("zio.blocks.schema").term("ZIOPreludeSupportSpec"))
        )
      ) &&
      assert(Planet.radius.focus.typeId)(
        equalTo(
          TypeId.nominal[Meter]("Meter", Owner.fromPackagePath("zio.blocks.schema").term("ZIOPreludeSupportSpec"))
        )
      ) &&
      assert(Planet.distanceFromSun.focus.typeId)(
        equalTo(
          TypeId.of[Option[Meter]]
        )
      )
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
      assert(schema1.reflect.typeId)(
        equalTo(TypeId.of[Option[Name]])
      ) &&
      assert(schema2.reflect.typeId)(
        equalTo(TypeId.of[Option[Kilogram]])
      ) &&
      assert(schema3.reflect.typeId)(
        equalTo(TypeId.of[Option[Meter]])
      ) &&
      assert(schema4.reflect.typeId)(
        equalTo(TypeId.of[Option[EmojiDataId]])
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
      assert(schema1.reflect.typeId)(
        equalTo(TypeId.of[List[Name]])
      ) &&
      assert(schema2.reflect.typeId)(
        equalTo(TypeId.of[Vector[Kilogram]])
      ) &&
      assert(schema3.reflect.typeId)(
        equalTo(TypeId.of[Set[Meter]])
      ) &&
      assert(schema4.reflect.typeId)(
        equalTo(TypeId.of[Map[EmojiDataId, Name]])
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
    test("TypeId.of uses user-provided given TypeId when available") {
      val schemaTypeId  = Schema[Name].reflect.typeId
      val derivedTypeId = TypeId.of[Name]
      assert(schemaTypeId)(equalTo(derivedTypeId)) &&
      assert(schemaTypeId.name)(equalTo("Name")) &&
      assert(schemaTypeId.owner)(equalTo(zioPreludeOwner))
    },
    test("TypeId.of for collections uses given TypeId for element types") {
      val schemaListTypeId  = Schema.derived[List[Name]].reflect.typeId
      val derivedListTypeId = TypeId.of[List[Name]]
      val schemaMapTypeId   = Schema.derived[Map[EmojiDataId, Name]].reflect.typeId
      val derivedMapTypeId  = TypeId.of[Map[EmojiDataId, Name]]
      assert(schemaListTypeId)(equalTo(derivedListTypeId)) &&
      assert(schemaMapTypeId)(equalTo(derivedMapTypeId))
    },
    test("TypeId.of auto-derives when no given TypeId is available") {
      val derivedTypeId1 = TypeId.of[NInt.Type]
      val derivedTypeId2 = TypeId.of[NInt.Type]
      assert(derivedTypeId1)(equalTo(derivedTypeId2)) &&
      assert(derivedTypeId1.name)(equalTo("Type")) &&
      assert(derivedTypeId1.owner.toString)(containsString("NInt"))
    }
  )

  private val zioPreludeOwner: Owner = Owner.fromPackagePath("zio.blocks.schema").term("ZIOPreludeSupportSpec")

  type Name = Name.Type

  object Name extends Newtype[String] {
    override inline def assertion: zio.prelude.Assertion[String] = !zio.prelude.Assertion.isEmptyString

    given TypeId[Name]                = TypeId.nominal("Name", zioPreludeOwner)
    implicit val schema: Schema[Name] = Schema[String]
      .transformOrFail(
        s =>
          if (s.length > 0) Right(s.asInstanceOf[Name])
          else Left(SchemaError.validationFailed("String must not be empty")),
        _.asInstanceOf[String]
      )
      .asOpaqueType[Name]
  }

  type Kilogram = Kilogram.Type

  object Kilogram extends Newtype[Double] {
    given TypeId[Kilogram]                = TypeId.nominal("Kilogram", zioPreludeOwner)
    implicit val schema: Schema[Kilogram] =
      Schema[Double].transform(_.asInstanceOf[Kilogram], _.asInstanceOf[Double]).asOpaqueType[Kilogram]
  }

  type Meter = Meter.Type

  object Meter extends Subtype[Double] {
    given TypeId[Meter]                = TypeId.nominal("Meter", zioPreludeOwner)
    implicit val schema: Schema[Meter] =
      Schema[Double].transform(_.asInstanceOf[Meter], _.asInstanceOf[Double]).asOpaqueType[Meter]
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

  type EmojiDataId = EmojiDataId.Type

  object EmojiDataId extends Subtype[Int] {
    given TypeId[EmojiDataId]                = TypeId.nominal("EmojiDataId", zioPreludeOwner)
    implicit val schema: Schema[EmojiDataId] =
      Schema[Int].transform(_.asInstanceOf[EmojiDataId], _.asInstanceOf[Int]).asOpaqueType[EmojiDataId]
  }
}
