package zio.blocks.schema.xml

import zio.blocks.schema.{Schema, SchemaBaseSpec}
import zio.test._
import zio.blocks.schema.xml.syntax._

object XmlSyntaxSpec extends SchemaBaseSpec {
  def spec: Spec[TestEnvironment, Any] = suite("XmlSyntaxSpec")(
    suite("toXml extension")(
      test("converts case class to Xml") {
        case class Person(name: String, age: Int)
        object Person {
          implicit val schema: Schema[Person] = Schema.derived
        }

        val person   = Person("Alice", 30)
        val xml      = person.toXml
        val isResult = xml.is(XmlType.Element)

        assertTrue(isResult)
      }
    ),
    suite("toXmlString extension")(
      test("converts case class to XML string with default config") {
        case class Simple(value: String)
        object Simple {
          implicit val schema: Schema[Simple] = Schema.derived
        }

        val simple = Simple("test")
        val xml    = simple.toXmlString

        assertTrue(
          xml.contains("<Simple>"),
          xml.contains("<value>test</value>"),
          xml.contains("</Simple>")
        )
      },
      test("converts case class to XML string with custom config") {
        case class Item(id: Int)
        object Item {
          implicit val schema: Schema[Item] = Schema.derived
        }

        val item = Item(42)
        val xml  = item.toXmlString(WriterConfig.pretty)

        assertTrue(
          xml.contains("<Item>"),
          xml.contains("<id>42</id>")
        )
      }
    ),
    suite("toXmlBytes extension")(
      test("converts case class to bytes") {
        case class Data(value: String)
        object Data {
          implicit val schema: Schema[Data] = Schema.derived
        }

        val data  = Data("hello")
        val bytes = data.toXmlBytes

        assertTrue(bytes.nonEmpty)
      }
    ),
    suite("fromXml String extension")(
      test("decodes XML string to case class") {
        case class Person(name: String, age: Int)
        object Person {
          implicit val schema: Schema[Person] = Schema.derived
        }

        val person = Person("Bob", 25)
        val bytes  = person.toXmlBytes
        val xml    = new String(bytes, "UTF-8")
        val result = xml.fromXml[Person]

        assertTrue(result == Right(person))
      },
      test("returns error for invalid XML") {
        case class Person(name: String, age: Int)
        object Person {
          implicit val schema: Schema[Person] = Schema.derived
        }

        val result = "invalid xml".fromXml[Person]

        assertTrue(result.isLeft)
      }
    ),
    suite("fromXml Array[Byte] extension")(
      test("decodes bytes to case class") {
        case class Book(title: String, year: Int)
        object Book {
          implicit val schema: Schema[Book] = Schema.derived
        }

        val book   = Book("ZIO Guide", 2024)
        val bytes  = book.toXmlBytes
        val result = bytes.fromXml[Book]

        assertTrue(result == Right(book))
      },
      test("round-trip with nested case classes") {
        case class Address(street: String, city: String)
        case class Person(name: String, address: Address)
        object Address {
          implicit val schema: Schema[Address] = Schema.derived
        }
        object Person {
          implicit val schema: Schema[Person] = Schema.derived
        }

        val person = Person("Charlie", Address("123 Main St", "Springfield"))
        val bytes  = person.toXmlBytes
        val result = bytes.fromXml[Person]

        assertTrue(result == Right(person))
      }
    )
  )
}
