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
import zio.blocks.schema.SchemaBaseSpec
import zio.test._

object XmlWriterSpec extends SchemaBaseSpec {

  def spec = suite("XmlWriterSpec")(
    suite("write")(
      test("writes simple text node") {
        val text   = Xml.Text("Hello World")
        val result = XmlWriter.write(text)
        assertTrue(result == "Hello World")
      },
      test("writes CDATA node") {
        val cdata  = Xml.CData("some <data> with special chars")
        val result = XmlWriter.write(cdata)
        assertTrue(result == "<![CDATA[some <data> with special chars]]>")
      },
      test("writes comment node") {
        val comment = Xml.Comment("This is a comment")
        val result  = XmlWriter.write(comment)
        assertTrue(result == "<!--This is a comment-->")
      },
      test("writes processing instruction") {
        val pi     = Xml.ProcessingInstruction("xml-stylesheet", "type=\"text/xsl\" href=\"style.xsl\"")
        val result = XmlWriter.write(pi)
        assertTrue(result == "<?xml-stylesheet type=\"text/xsl\" href=\"style.xsl\"?>")
      },
      test("writes simple element with no attributes or children") {
        val elem   = Xml.Element(XmlName("root"), Chunk.empty, Chunk.empty)
        val result = XmlWriter.write(elem)
        assertTrue(result == "<root/>")
      },
      test("writes element with text child") {
        val elem = Xml.Element(
          XmlName("root"),
          Chunk.empty,
          Chunk(Xml.Text("content"))
        )
        val result = XmlWriter.write(elem)
        assertTrue(result == "<root>content</root>")
      },
      test("writes element with attributes") {
        val elem = Xml.Element(
          XmlName("root"),
          Chunk((XmlName("id"), "123"), (XmlName("name"), "test")),
          Chunk.empty
        )
        val result = XmlWriter.write(elem)
        assertTrue(result == "<root id=\"123\" name=\"test\"/>")
      },
      test("writes element with child elements") {
        val elem = Xml.Element(
          XmlName("root"),
          Chunk.empty,
          Chunk(
            Xml.Element(XmlName("child1"), Chunk.empty, Chunk.empty),
            Xml.Element(XmlName("child2"), Chunk.empty, Chunk.empty)
          )
        )
        val result = XmlWriter.write(elem)
        assertTrue(result == "<root><child1/><child2/></root>")
      },
      test("writes element with namespace") {
        val elem   = Xml.Element(XmlName("root", "http://example.com"), Chunk.empty, Chunk.empty)
        val result = XmlWriter.write(elem)
        assertTrue(result.contains("root") && result.contains("http://example.com"))
      }
    ),
    suite("escaping")(
      test("escapes & in text content") {
        val text   = Xml.Text("A & B")
        val result = XmlWriter.write(text)
        assertTrue(result == "A &amp; B")
      },
      test("escapes < in text content") {
        val text   = Xml.Text("A < B")
        val result = XmlWriter.write(text)
        assertTrue(result == "A &lt; B")
      },
      test("escapes > in text content") {
        val text   = Xml.Text("A > B")
        val result = XmlWriter.write(text)
        assertTrue(result == "A &gt; B")
      },
      test("escapes multiple special chars in text") {
        val text   = Xml.Text("<tag> & </tag>")
        val result = XmlWriter.write(text)
        assertTrue(result == "&lt;tag&gt; &amp; &lt;/tag&gt;")
      },
      test("escapes & in attribute values") {
        val elem = Xml.Element(
          XmlName("root"),
          Chunk((XmlName("attr"), "A & B")),
          Chunk.empty
        )
        val result = XmlWriter.write(elem)
        assertTrue(result == "<root attr=\"A &amp; B\"/>")
      },
      test("escapes < in attribute values") {
        val elem = Xml.Element(
          XmlName("root"),
          Chunk((XmlName("attr"), "A < B")),
          Chunk.empty
        )
        val result = XmlWriter.write(elem)
        assertTrue(result == "<root attr=\"A &lt; B\"/>")
      },
      test("escapes \" in attribute values") {
        val elem = Xml.Element(
          XmlName("root"),
          Chunk((XmlName("attr"), "Say \"Hello\"")),
          Chunk.empty
        )
        val result = XmlWriter.write(elem)
        assertTrue(result == "<root attr=\"Say &quot;Hello&quot;\"/>")
      },
      test("escapes ' in attribute values") {
        val elem = Xml.Element(
          XmlName("root"),
          Chunk((XmlName("attr"), "It's here")),
          Chunk.empty
        )
        val result = XmlWriter.write(elem)
        assertTrue(result == "<root attr=\"It&apos;s here\"/>")
      }
    ),
    suite("indentation")(
      test("writes with indentation when configured") {
        val elem = Xml.Element(
          XmlName("root"),
          Chunk.empty,
          Chunk(
            Xml.Element(XmlName("child"), Chunk.empty, Chunk(Xml.Text("text")))
          )
        )
        val config = WriterConfig(indentStep = 2)
        val result = XmlWriter.write(elem, config)
        assertTrue(result == "<root>\n  <child>text</child>\n</root>")
      },
      test("writes without indentation by default") {
        val elem = Xml.Element(
          XmlName("root"),
          Chunk.empty,
          Chunk(
            Xml.Element(XmlName("child"), Chunk.empty, Chunk.empty)
          )
        )
        val result = XmlWriter.write(elem)
        assertTrue(result == "<root><child/></root>")
      }
    ),
    suite("XML declaration")(
      test("includes XML declaration when configured") {
        val elem   = Xml.Element(XmlName("root"), Chunk.empty, Chunk.empty)
        val config = WriterConfig(includeDeclaration = true)
        val result = XmlWriter.write(elem, config)
        assertTrue(result.startsWith("<?xml version=\"1.0\""))
      },
      test("does not include declaration by default") {
        val elem   = Xml.Element(XmlName("root"), Chunk.empty, Chunk.empty)
        val result = XmlWriter.write(elem)
        assertTrue(!result.startsWith("<?xml"))
      },
      test("includes encoding in declaration when specified") {
        val elem   = Xml.Element(XmlName("root"), Chunk.empty, Chunk.empty)
        val config = WriterConfig(includeDeclaration = true, encoding = "UTF-16")
        val result = XmlWriter.write(elem, config)
        assertTrue(result == "<?xml version=\"1.0\" encoding=\"UTF-16\"?><root/>")
      }
    ),
    suite("writeToBytes")(
      test("writes to byte array") {
        val text   = Xml.Text("Hello")
        val result = XmlWriter.writeToBytes(text)
        assertTrue(new String(result, "UTF-8") == "Hello")
      },
      test("writes with specified encoding") {
        val text   = Xml.Text("Hello")
        val config = WriterConfig(encoding = "UTF-8")
        val result = XmlWriter.writeToBytes(text, config)
        assertTrue(new String(result, "UTF-8") == "Hello")
      }
    )
  )
}
