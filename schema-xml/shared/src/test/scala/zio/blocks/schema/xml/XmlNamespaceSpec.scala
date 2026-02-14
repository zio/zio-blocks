package zio.blocks.schema.xml

import zio.blocks.schema.{Modifier, Schema, SchemaBaseSpec}
import zio.test._

object XmlNamespaceSpec extends SchemaBaseSpec {
  def spec: Spec[TestEnvironment, Any] = suite("XmlNamespaceSpec")(
    test("default namespace") {
      @Modifier.config("xml.namespace.uri", "http://example.com/ns")
      case class Item(name: String)
      object Item {
        implicit val schema: Schema[Item] = Schema.derived
      }

      val item  = Item("test")
      val codec = Schema[Item].derive(XmlFormat)
      val xml   = codec.encodeToString(item)

      assertTrue(xml.contains("""xmlns="http://example.com/ns""""))
    },
    test("prefixed namespace") {
      @Modifier.config("xml.namespace.uri", "http://example.com/ns")
      @Modifier.config("xml.namespace.prefix", "ex")
      case class Item(name: String)
      object Item {
        implicit val schema: Schema[Item] = Schema.derived
      }

      val item  = Item("test")
      val codec = Schema[Item].derive(XmlFormat)
      val xml   = codec.encodeToString(item)

      assertTrue(
        xml.contains("""xmlns:ex="http://example.com/ns""""),
        xml.contains("<ex:Item")
      )
    },
    test("round-trip with default namespace") {
      @Modifier.config("xml.namespace.uri", "http://example.com/ns")
      case class Data(value: Int)
      object Data {
        implicit val schema: Schema[Data] = Schema.derived
      }

      val data   = Data(42)
      val codec  = Schema[Data].derive(XmlFormat)
      val result = codec.decode(codec.encode(data))

      assertTrue(result == Right(data))
    },
    test("round-trip with prefixed namespace") {
      @Modifier.config("xml.namespace.uri", "http://example.com/ns")
      @Modifier.config("xml.namespace.prefix", "ex")
      case class Data(value: Int)
      object Data {
        implicit val schema: Schema[Data] = Schema.derived
      }

      val data   = Data(42)
      val codec  = Schema[Data].derive(XmlFormat)
      val result = codec.decode(codec.encode(data))

      assertTrue(result == Right(data))
    },
    test("namespace with multiple fields") {
      @Modifier.config("xml.namespace.uri", "http://www.w3.org/2005/Atom")
      case class Feed(title: String, author: String, updated: String)
      object Feed {
        implicit val schema: Schema[Feed] = Schema.derived
      }

      val feed  = Feed("My Feed", "John Doe", "2024-01-01")
      val codec = Schema[Feed].derive(XmlFormat)
      val xml   = codec.encodeToString(feed)

      assertTrue(
        xml.contains("""xmlns="http://www.w3.org/2005/Atom""""),
        xml.contains("<title>My Feed</title>"),
        xml.contains("<author>John Doe</author>")
      )
    },
    test("namespace with attributes") {
      @Modifier.config("xml.namespace.uri", "http://example.com/ns")
      case class Document(
        @Modifier.config("xml.attribute", "") id: String,
        content: String
      )
      object Document {
        implicit val schema: Schema[Document] = Schema.derived
      }

      val doc   = Document("doc-123", "Hello World")
      val codec = Schema[Document].derive(XmlFormat)
      val xml   = codec.encodeToString(doc)

      assertTrue(
        xml.contains("""xmlns="http://example.com/ns""""),
        xml.contains("""id="doc-123""""),
        xml.contains("<content>Hello World</content>")
      )
    },
    test("prefixed namespace with attributes") {
      @Modifier.config("xml.namespace.uri", "http://example.com/ns")
      @Modifier.config("xml.namespace.prefix", "ex")
      case class Document(
        @Modifier.config("xml.attribute", "") id: String,
        content: String
      )
      object Document {
        implicit val schema: Schema[Document] = Schema.derived
      }

      val doc   = Document("doc-456", "Content here")
      val codec = Schema[Document].derive(XmlFormat)
      val xml   = codec.encodeToString(doc)

      assertTrue(
        xml.contains("""xmlns:ex="http://example.com/ns""""),
        xml.contains("<ex:Document"),
        xml.contains("""id="doc-456"""")
      )
    }
  )
}
