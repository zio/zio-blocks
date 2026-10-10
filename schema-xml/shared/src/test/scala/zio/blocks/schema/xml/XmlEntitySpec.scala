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

import zio.blocks.chunk.Chunk
import zio.test._
import scala.util.Try

object XmlEntitySpec extends ZIOSpecDefault {

  private def textOf(input: String, config: ReaderConfig = ReaderConfig.default): Xml =
    XmlReader.read(input, config)

  def spec = suite("XmlEntitySpec")(
    suite("closed entity set")(
      test("rejects custom entities with a positioned error") {
        val failure = Try(textOf("<root>&foo;</root>")).failed.toOption
        assertTrue(
          failure.exists(_.isInstanceOf[XmlCodecError]),
          failure.map(_.getMessage).exists(m => m.contains("Unknown entity") && m.contains("line: 1"))
        )
      },
      test("rejects unknown entities in attribute values") {
        val failure = Try(textOf("<root a=\"x&bar;y\"/>")).failed.toOption
        assertTrue(
          failure.exists(_.isInstanceOf[XmlCodecError]),
          failure.map(_.getMessage).exists(_.contains("Unknown entity"))
        )
      },
      test("rejects parameter-entity style references") {
        val failure = Try(textOf("<root>&%pe;</root>")).failed.toOption
        assertTrue(failure.exists(_.isInstanceOf[XmlCodecError]))
      },
      test("decodes decimal numeric character references") {
        assertTrue(
          textOf("<root>&#65;&#66;&#67;</root>") ==
            Xml.Element(XmlName("root"), Chunk.empty, Chunk(Xml.Text("ABC")))
        )
      },
      test("decodes hexadecimal numeric character references") {
        assertTrue(
          textOf("<root>&#x41;&#x4a;</root>") ==
            Xml.Element(XmlName("root"), Chunk.empty, Chunk(Xml.Text("AJ")))
        )
      },
      test("decodes supplementary-plane numeric references") {
        val expected = new String(Character.toChars(0x1f600))
        assertTrue(
          textOf("<root>&#x1F600;</root>") ==
            Xml.Element(XmlName("root"), Chunk.empty, Chunk(Xml.Text(expected)))
        )
      },
      test("rejects malformed numeric references") {
        val bad = List("<root>&#xZZ;</root>", "<root>&#;</root>", "<root>&#0;</root>", "<root>&#x;</root>")
        assertTrue(bad.forall(input => Try(textOf(input)).isFailure))
      },
      test("resolves entities in a single pass (no recursive expansion)") {
        assertTrue(
          textOf("<root>&amp;lt;</root>") ==
            Xml.Element(XmlName("root"), Chunk.empty, Chunk(Xml.Text("&lt;")))
        )
      },
      test("long entity chains decode to exactly one character per entity in a single pass") {
        // Functional single-pass proof, not a timing claim: `&amp;` must decode
        // to `&` without recursive re-expansion, so N entities yield exactly N
        // characters — bound here against the literal expected size.
        val count    = 500
        val expected = "&" * count
        val result   = textOf("<root>" + ("&amp;" * count) + "</root>")
        result match {
          case Xml.Element(_, _, children) =>
            val text = children.collect { case Xml.Text(t) => t }.mkString
            assertTrue(text == expected, text.length == expected.length, expected.length == 500)
          case _ => assertTrue(false)
        }
      },
      test("text beyond maxTextLength is rejected") {
        val failure = Try(textOf("<root>123456789</root>", ReaderConfig(maxTextLength = 8))).failed.toOption
        assertTrue(
          failure.exists(_.isInstanceOf[XmlCodecError]),
          failure.map(_.getMessage).exists(_.contains("exceeds maximum"))
        )
      },
      test("text at exactly maxTextLength is accepted") {
        assertTrue(
          textOf("<root>12345678</root>", ReaderConfig(maxTextLength = 8)) ==
            Xml.Element(XmlName("root"), Chunk.empty, Chunk(Xml.Text("12345678")))
        )
      },
      test("element nesting beyond maxDepth is rejected") {
        val deep    = "<a>" * 10 + "x" + "</a>" * 10
        val failure = Try(textOf(deep, ReaderConfig(maxDepth = 8))).failed.toOption
        assertTrue(
          failure.exists(_.isInstanceOf[XmlCodecError]),
          failure.map(_.getMessage).exists(_.contains("Maximum depth"))
        )
      },
      test("element nesting within maxDepth is accepted") {
        val deep = "<a>" * 10 + "x" + "</a>" * 10
        assertTrue(Try(textOf(deep, ReaderConfig(maxDepth = 10))).isSuccess)
      }
    ),
    suite("no doctype or external entities")(
      test("rejects top-level DOCTYPE") {
        val failure = Try(textOf("<!DOCTYPE greeting SYSTEM \"hello.dtd\"><root/>")).failed.toOption
        assertTrue(failure.exists(_.isInstanceOf[XmlCodecError]))
      },
      test("rejects nested DOCTYPE") {
        val failure = Try(textOf("<root><!DOCTYPE x></root>")).failed.toOption
        assertTrue(failure.exists(_.isInstanceOf[XmlCodecError]))
      }
    ),
    suite("codec-level positioned errors")(
      test("unknown entity surfaces as a Left carrying line position") {
        val result = XmlCodec.stringCodec.decode("<value>&foo;</value>")
        result match {
          case Left(err) =>
            assertTrue(err.message.contains("Unknown entity") && err.message.contains("line: 1"))
          case _ => assertTrue(false)
        }
      },
      test("numeric references decode through the codec") {
        val result = XmlCodec.stringCodec.decode("<value>&#65;</value>")
        assertTrue(result == Right("A"))
      },
      test("explicit signs on numeric references are rejected (decimal)") {
        val plus     = XmlCodec.stringCodec.decode("<value>&#+65;</value>")
        val minus    = XmlCodec.stringCodec.decode("<value>&#-65;</value>")
        val plusMsg  = plus.fold(_.message, _ => "")
        val minusMsg = minus.fold(_.message, _ => "")
        assertTrue(
          plus.isLeft,
          minus.isLeft,
          plusMsg.contains("Invalid entity"),
          minusMsg.contains("Invalid entity")
        )
      },
      test("explicit signs on numeric references are rejected (hexadecimal)") {
        val plus     = XmlCodec.stringCodec.decode("<value>&#x+41;</value>")
        val minus    = XmlCodec.stringCodec.decode("<value>&#x-41;</value>")
        val plusMsg  = plus.fold(_.message, _ => "")
        val minusMsg = minus.fold(_.message, _ => "")
        assertTrue(
          plus.isLeft,
          minus.isLeft,
          plusMsg.contains("Invalid entity"),
          minusMsg.contains("Invalid entity")
        )
      }
    )
  )
}
