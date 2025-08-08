package zio.blocks.schema

import zio.test.Assertion._
import zio.test._

object ZIOPreludeSupportSpec extends ZIOSpecDefault {
  def spec: Spec[TestEnvironment, Any] = suite("ZIOPreludeSupportSpec")(
    test("fail to derive schemas for newtypes") {
      typeCheck {
        """import zio.prelude._
           object Name extends Newtype[String] {
             implicit val schema: Schema[Name.Type] = Schema.derived
           }"""
      }.map(assert(_)(isLeft(equalTo("Cannot derive schema for 'Name.Type'."))))
    },
    test("fail to derive schemas for subtypes") {
      typeCheck {
        """import zio.prelude._
           object Name extends Subtype[String] {
             implicit val schema: Schema[Name.Type] = Schema.derived
           }"""
      }.map(assert(_)(isLeft(equalTo("Cannot derive schema for 'Name.Type'."))))
    },
    test("derive schemas for cases classes with subtype fields that have provided schema implicits") {
      import zio.prelude._

      type Name = Name.Type

      object Name extends Subtype[String] {
        implicit val schema: Schema[Name] = derive(Schema[String])
      }

      type Kilogram = Kilogram.Type

      object Kilogram extends Subtype[Double] {
        implicit val schema: Schema[Kilogram] = derive(Schema[Double])
      }

      type Meter = Meter.Type

      object Meter extends Subtype[Double] {
        implicit val schema: Schema[Meter] = derive(Schema[Double])
      }

      case class Planet(name: Name, mass: Kilogram, radius: Meter)

      object Planet extends CompanionOptics[Planet] {
        implicit val schema: Schema[Planet] = Schema.derived
        val name: Lens[Planet, Name]        = $(_.name)
        val mass: Lens[Planet, Kilogram]    = $(_.mass)
        val radius: Lens[Planet, Meter]     = $(_.radius)
      }

      val value = Planet(Name("Earth"), Kilogram(5.97e24), Meter(6378000.0))
      assert(Planet.name.get(value))(equalTo("Earth")) &&
      assert(Planet.mass.get(value))(equalTo(Kilogram(5.97e24))) &&
      assert(Planet.radius.get(value))(equalTo(Meter(6378000.0))) &&
      assert(Planet.mass.replace(value, Kilogram(5.970001e24)))(
        equalTo(Planet(Name("Earth"), Kilogram(5.970001e24), Meter(6378000.0)))
      ) &&
      assert(Planet.schema.fromDynamicValue(Planet.schema.toDynamicValue(value)))(isRight(equalTo(value)))
    }
  )
}
