/*
 * Copyright 2024-2026 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
      assertTrue(xml == "<Item xmlns=\"http://example.com/ns\"><name>test</name></Item>")
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
      assertTrue(xml == "<ex:Item xmlns:ex=\"http://example.com/ns\"><name>test</name></ex:Item>")
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
        xml == "<Feed xmlns=\"http://www.w3.org/2005/Atom\"><title>My Feed</title><author>John Doe</author><updated>2024-01-01</updated></Feed>"
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
        xml == "<Document xmlns=\"http://example.com/ns\" id=\"doc-123\"><content>Hello World</content></Document>"
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
        xml == "<ex:Document xmlns:ex=\"http://example.com/ns\" id=\"doc-456\"><content>Content here</content></ex:Document>"
      )
    }
  )
}
