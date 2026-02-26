package zio.blocks.schema.xml

import zio.blocks.schema.{Schema, SchemaBaseSpec}
import zio.test._

object XmlFormatSpec extends SchemaBaseSpec {

  case class Person(name: String, age: Int)
  object Person {
    implicit val schema: Schema[Person] = Schema.derived
  }

  def spec: Spec[TestEnvironment, Any] = suite("XmlFormatSpec")(
    test("mimeType is application/xml") {
      assertTrue(XmlFormat.mimeType == "application/xml")
    },
    test("derive codec for case class") {
      val codec  = Schema[Person].derive(XmlFormat)
      val person = Person("Alice", 30)
      val bytes  = codec.encode(person)
      val result = codec.decode(bytes)
      assertTrue(result == Right(person))
    },
    test("round-trip simple case class") {
      val codec  = Schema[Person].derive(XmlFormat)
      val person = Person("Bob", 25)
      val result = codec.decode(codec.encode(person))
      assertTrue(result == Right(person))
    }
  )
}
