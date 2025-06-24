package zio.blocks.schema

import zio.Scope
import zio.test._
import zio.test.Assertion._

object SchemaPlatformSpecificSpec extends ZIOSpecDefault {
  def spec: Spec[TestEnvironment with Scope, Any] = suite("SchemaPlatformSpecificSpec")(
    suite("zio-prelude")(
      test("fail to derive schemas for newtypes") {
        typeCheck {
          """import zio.prelude._
             object Name extends Newtype[String] {
               implicit val schema: Schema[Name.Type] = Schema.derived
             }"""
        }.map(assert(_)(isLeft(equalTo("Cannot derive schema for 'Name.Type'."))))
      },
      test("fail to derive schemas for cases classes with newtype fields") {
        typeCheck {
          """import zio.prelude._
             object Name extends Subtype[String] {
               implicit def schema: Schema[Name.Type] = derive(Schema[String])
             }
             object Kilogram extends Newtype[Double] {
               implicit def schema: Schema[Kilogram.Type] = derive(Schema[Double])
             }
             object Meter extends Newtype[Double] {
               implicit def schema: Schema[Meter.Type] = derive(Schema[Double])
             }
             case class Planet(name: Name.Type, mass: Kilogram.Type, radius: Meter.Type)
             object Planet extends CompanionOptics[Planet] {
               implicit val schema: Schema[Planet]   = Schema.derived
               val name: Lens[Planet, Name.Type]     = optic(_.name)
               val mass: Lens[Planet, Kilogram.Type] = optic(_.mass)
               val radius: Lens[Planet, Meter.Type]  = optic(_.radius)
             }"""
        }.map(assert(_)(isLeft(equalTo("Unsupported field type 'Kilogram.Type'."))))
      }
    )
  )
}
