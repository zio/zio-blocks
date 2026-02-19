package zio.blocks.schema.xml

import zio.blocks.chunk.Chunk
import zio.test._

object XmlReaderSpec extends ZIOSpecDefault {

  def spec = suite("XmlReader")(
    suite("Basic parsing")(
      test("parse simple element") {
        val input  = "<root/>"
        val result = XmlReader.read(input)
        assertTrue(
          result == Right(Xml.Element(XmlName("root"), Chunk.empty, Chunk.empty))
        )
      },
      test("parse element with text content") {
        val input  = "<root>hello</root>"
        val result = XmlReader.read(input)
        assertTrue(
          result == Right(
            Xml.Element(
              XmlName("root"),
              Chunk.empty,
              Chunk(Xml.Text("hello"))
            )
          )
        )
      },
      test("parse element with single attribute") {
        val input  = "<root attr=\"value\"/>"
        val result = XmlReader.read(input)
        assertTrue(
          result == Right(
            Xml.Element(
              XmlName("root"),
              Chunk((XmlName("attr"), "value")),
              Chunk.empty
            )
          )
        )
      },
      test("parse element with multiple attributes") {
        val input  = "<root a=\"1\" b=\"2\"/>"
        val result = XmlReader.read(input)
        assertTrue(
          result == Right(
            Xml.Element(
              XmlName("root"),
              Chunk((XmlName("a"), "1"), (XmlName("b"), "2")),
              Chunk.empty
            )
          )
        )
      },
      test("parse nested elements") {
        val input  = "<root><child>text</child></root>"
        val result = XmlReader.read(input)
        assertTrue(
          result == Right(
            Xml.Element(
              XmlName("root"),
              Chunk.empty,
              Chunk(
                Xml.Element(
                  XmlName("child"),
                  Chunk.empty,
                  Chunk(Xml.Text("text"))
                )
              )
            )
          )
        )
      }
    ),
    suite("Entity references")(
      test("decode &amp; entity") {
        val input  = "<root>&amp;</root>"
        val result = XmlReader.read(input)
        assertTrue(
          result == Right(
            Xml.Element(XmlName("root"), Chunk.empty, Chunk(Xml.Text("&")))
          )
        )
      },
      test("decode &lt; entity") {
        val input  = "<root>&lt;</root>"
        val result = XmlReader.read(input)
        assertTrue(
          result == Right(
            Xml.Element(XmlName("root"), Chunk.empty, Chunk(Xml.Text("<")))
          )
        )
      },
      test("decode &gt; entity") {
        val input  = "<root>&gt;</root>"
        val result = XmlReader.read(input)
        assertTrue(
          result == Right(
            Xml.Element(XmlName("root"), Chunk.empty, Chunk(Xml.Text(">")))
          )
        )
      },
      test("decode &quot; entity") {
        val input  = "<root attr=\"&quot;\"/>"
        val result = XmlReader.read(input)
        assertTrue(
          result == Right(
            Xml.Element(XmlName("root"), Chunk((XmlName("attr"), "\"")), Chunk.empty)
          )
        )
      },
      test("decode &apos; entity") {
        val input  = "<root attr=\"&apos;\"/>"
        val result = XmlReader.read(input)
        assertTrue(
          result == Right(
            Xml.Element(XmlName("root"), Chunk((XmlName("attr"), "'")), Chunk.empty)
          )
        )
      },
      test("decode multiple entities") {
        val input  = "<root>&lt;&amp;&gt;</root>"
        val result = XmlReader.read(input)
        assertTrue(
          result == Right(
            Xml.Element(XmlName("root"), Chunk.empty, Chunk(Xml.Text("<&>")))
          )
        )
      }
    ),
    suite("Special node types")(
      test("parse CDATA section") {
        val input  = "<root><![CDATA[<>&\"]]></root>"
        val result = XmlReader.read(input)
        assertTrue(
          result == Right(
            Xml.Element(
              XmlName("root"),
              Chunk.empty,
              Chunk(Xml.CData("<>&\""))
            )
          )
        )
      },
      test("parse comment") {
        val input  = "<root><!-- comment --></root>"
        val result = XmlReader.read(input)
        assertTrue(
          result == Right(
            Xml.Element(
              XmlName("root"),
              Chunk.empty,
              Chunk(Xml.Comment(" comment "))
            )
          )
        )
      },
      test("parse processing instruction") {
        val input  = "<root><?target data?></root>"
        val result = XmlReader.read(input)
        assertTrue(
          result == Right(
            Xml.Element(
              XmlName("root"),
              Chunk.empty,
              Chunk(Xml.ProcessingInstruction("target", "data"))
            )
          )
        )
      },
      test("parse processing instruction with empty data") {
        val input  = "<root><?target?></root>"
        val result = XmlReader.read(input)
        assertTrue(
          result == Right(
            Xml.Element(
              XmlName("root"),
              Chunk.empty,
              Chunk(Xml.ProcessingInstruction("target", ""))
            )
          )
        )
      }
    ),
    suite("Whitespace handling")(
      test("trim whitespace by default") {
        val input  = "<root>  text  </root>"
        val result = XmlReader.read(input)
        assertTrue(
          result == Right(
            Xml.Element(XmlName("root"), Chunk.empty, Chunk(Xml.Text("text")))
          )
        )
      },
      test("preserve whitespace when configured") {
        val input  = "<root>  text  </root>"
        val config = ReaderConfig(preserveWhitespace = true)
        val result = XmlReader.read(input, config)
        assertTrue(
          result == Right(
            Xml.Element(XmlName("root"), Chunk.empty, Chunk(Xml.Text("  text  ")))
          )
        )
      },
      test("ignore whitespace-only text nodes by default") {
        val input  = "<root>  <child/>  </root>"
        val result = XmlReader.read(input)
        assertTrue(
          result == Right(
            Xml.Element(
              XmlName("root"),
              Chunk.empty,
              Chunk(Xml.Element(XmlName("child"), Chunk.empty, Chunk.empty))
            )
          )
        )
      }
    ),
    suite("Error handling")(
      test("reject unclosed element") {
        val input  = "<root>"
        val result = XmlReader.read(input)
        assertTrue(result.isLeft)
      },
      test("reject mismatched tags") {
        val input  = "<root></other>"
        val result = XmlReader.read(input)
        assertTrue(result.isLeft)
      },
      test("reject invalid element name") {
        val input  = "<123/>"
        val result = XmlReader.read(input)
        assertTrue(result.isLeft)
      },
      test("reject element exceeding max depth") {
        val depth  = 10
        val config = ReaderConfig(maxDepth = depth)
        val input  = "<a>" * (depth + 1) + "</a>" * (depth + 1)
        val result = XmlReader.read(input, config)
        assertTrue(result.isLeft)
      },
      test("reject element exceeding max attributes") {
        val config = ReaderConfig(maxAttributes = 2)
        val input  = "<root a=\"1\" b=\"2\" c=\"3\"/>"
        val result = XmlReader.read(input, config)
        assertTrue(result.isLeft)
      },
      test("reject text exceeding max length") {
        val config = ReaderConfig(maxTextLength = 5)
        val input  = "<root>123456</root>"
        val result = XmlReader.read(input, config)
        assertTrue(result.isLeft)
      },
      test("provide line and column in error") {
        val input  = "<root>\n  <invalid>"
        val result = XmlReader.read(input)
        assertTrue(
          result match {
            case Left(error) => error.getMessage.contains("line") && error.getMessage.contains("column")
            case _           => false
          }
        )
      }
    ),
    suite("readFromBytes")(
      test("parse from UTF-8 bytes") {
        val input  = "<root>hello</root>".getBytes("UTF-8")
        val result = XmlReader.readFromBytes(input)
        assertTrue(
          result == Right(
            Xml.Element(
              XmlName("root"),
              Chunk.empty,
              Chunk(Xml.Text("hello"))
            )
          )
        )
      }
    ),
    suite("Complex structures")(
      test("parse mixed content") {
        val input  = "<root>text1<child/>text2</root>"
        val result = XmlReader.read(input)
        assertTrue(
          result == Right(
            Xml.Element(
              XmlName("root"),
              Chunk.empty,
              Chunk(
                Xml.Text("text1"),
                Xml.Element(XmlName("child"), Chunk.empty, Chunk.empty),
                Xml.Text("text2")
              )
            )
          )
        )
      },
      test("parse element with namespace prefix") {
        val input  = "<ns:root xmlns:ns=\"http://example.com\"/>"
        val result = XmlReader.read(input)
        assertTrue(
          result.isRight && result.toOption.exists {
            case Xml.Element(name, attrs, _) =>
              name.localName == "root" && attrs.exists { case (attrName, attrValue) =>
                attrName.localName == "ns" && attrValue == "http://example.com"
              }
            case _ => false
          }
        )
      }
    )
  )
}
