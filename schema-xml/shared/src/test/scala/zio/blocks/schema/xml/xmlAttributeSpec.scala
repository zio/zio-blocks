package zio.blocks.schema.xml

import zio.blocks.schema.{Modifier, Schema, SchemaBaseSpec}
import zio.test._

object xmlAttributeSpec extends SchemaBaseSpec {
  def spec: Spec[TestEnvironment, Any] = suite("xmlAttributeSpec")(
    test("simple case class with xml attributes") {
      case class Person(
        @Modifier.config("xml.attribute", "") id: String,
        @Modifier.config("xml.attribute", "") age: Int,
        name: String
      )
      object Person {
        implicit val schema: Schema[Person] = Schema.derived
      }

      val person = Person("123", 30, "John")
      val codec  = Schema[Person].derive(XmlFormat)
      val xml    = codec.encodeToString(person)

      assertTrue(xml.contains("""id="123"""")) &&
      assertTrue(xml.contains("""age="30"""")) &&
      assertTrue(xml.contains("<name>John</name>")) &&
      assertTrue(!xml.contains("<id>")) &&
      assertTrue(!xml.contains("<age>"))
    },
    test("round-trip with xml attributes") {
      case class Book(
        @Modifier.config("xml.attribute", "") id: String,
        @Modifier.config("xml.attribute", "") year: Int,
        title: String,
        author: String
      )
      object Book {
        implicit val schema: Schema[Book] = Schema.derived
      }

      val book   = Book("B123", 2024, "ZIO Blocks", "ZIO Team")
      val codec  = Schema[Book].derive(XmlFormat)
      val result = codec.decode(codec.encode(book))

      assertTrue(result == Right(book))
    },
    test("custom attribute name") {
      case class Product(
        @Modifier.config("xml.attribute", "product-id") id: String,
        @Modifier.config("xml.attribute", "product-code") code: Int,
        name: String
      )
      object Product {
        implicit val schema: Schema[Product] = Schema.derived
      }

      val product = Product("P456", 789, "Widget")
      val codec   = Schema[Product].derive(XmlFormat)
      val xml     = codec.encodeToString(product)

      assertTrue(xml.contains("""product-id="P456"""")) &&
      assertTrue(xml.contains("""product-code="789"""")) &&
      assertTrue(!xml.contains(""" id="P456"""")) &&
      assertTrue(!xml.contains(""" code="789""""))
    },
    test("round-trip with custom attribute names") {
      case class Item(
        @Modifier.config("xml.attribute", "item-id") id: String,
        @Modifier.config("xml.attribute", "stock") quantity: Int,
        description: String
      )
      object Item {
        implicit val schema: Schema[Item] = Schema.derived
      }

      val item   = Item("I999", 50, "Test Item")
      val codec  = Schema[Item].derive(XmlFormat)
      val result = codec.decode(codec.encode(item))

      assertTrue(result == Right(item))
    },
    test("mixed attributes and child elements") {
      case class Document(
        @Modifier.config("xml.attribute", "") version: String,
        @Modifier.config("xml.attribute", "") encoding: String,
        title: String,
        content: String,
        @Modifier.config("xml.attribute", "") author: String
      )
      object Document {
        implicit val schema: Schema[Document] = Schema.derived
      }

      val doc    = Document("1.0", "UTF-8", "My Doc", "Content here", "Author")
      val codec  = Schema[Document].derive(XmlFormat)
      val xml    = codec.encodeToString(doc)
      val result = codec.decode(xml)

      assertTrue(result == Right(doc)) &&
      assertTrue(xml.contains("""version="1.0"""")) &&
      assertTrue(xml.contains("""encoding="UTF-8"""")) &&
      assertTrue(xml.contains("""author="Author"""")) &&
      assertTrue(xml.contains("<title>My Doc</title>")) &&
      assertTrue(xml.contains("<content>Content here</content>"))
    },
    test("all primitive types as attributes") {
      case class AllTypes(
        @Modifier.config("xml.attribute", "") str: String,
        @Modifier.config("xml.attribute", "") num: Int,
        @Modifier.config("xml.attribute", "") lng: Long,
        @Modifier.config("xml.attribute", "") dbl: Double,
        @Modifier.config("xml.attribute", "") bool: Boolean,
        name: String
      )
      object AllTypes {
        implicit val schema: Schema[AllTypes] = Schema.derived
      }

      val value  = AllTypes("test", 42, 9876543210L, 3.14, true, "Name")
      val codec  = Schema[AllTypes].derive(XmlFormat)
      val result = codec.decode(codec.encode(value))

      assertTrue(result == Right(value))
    },
    test("decode from manually created XML with attributes") {
      case class Simple(@Modifier.config("xml.attribute", "") id: String, name: String)
      object Simple {
        implicit val schema: Schema[Simple] = Schema.derived
      }

      val xml    = """<Simple id="S123"><name>Test</name></Simple>"""
      val codec  = Schema[Simple].derive(XmlFormat)
      val result = codec.decode(xml)

      assertTrue(result == Right(Simple("S123", "Test")))
    }
  )
}
