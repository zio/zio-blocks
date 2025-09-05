package zio.blocks.schema

import neotype._
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
        equalTo(
          TypeName[Name](Namespace(Seq("zio", "blocks", "schema"), Seq("NeotypeSupportSpec")), "Name")
        )
      ) &&
      assert(Planet.mass.focus.typeName)(
        equalTo(
          TypeName[Kilogram](Namespace(Seq("zio", "blocks", "schema"), Seq("NeotypeSupportSpec")), "Kilogram")
        )
      ) &&
      assert(Planet.radius.focus.typeName)(
        equalTo(
          TypeName[Meter](Namespace(Seq("zio", "blocks", "schema"), Seq("NeotypeSupportSpec")), "Meter")
        )
      ) &&
      assert(Planet.distanceFromSun.focus.typeName)(
        equalTo(
          TypeName.option(
            TypeName[Meter](Namespace(Seq("zio", "blocks", "schema"), Seq("NeotypeSupportSpec")), "Meter")
          )
        )
      )
    }
  )

  type Name = Name.Type

  object Name extends Newtype[String] {
    override inline def validate(string: String): Boolean = string.length > 0

    implicit val schema: Schema[Name] = Schema.derived.wrap(Name.make, _.unwrap)
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
}
