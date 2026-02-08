package zio.blocks.schema.xml

import zio.blocks.chunk.Chunk
import zio.blocks.schema.SchemaBaseSpec
import zio.test._

object XmlSpec extends SchemaBaseSpec {
  def spec: Spec[TestEnvironment, Any] = suite("XmlSpec")(
    suite("XmlName")(
      test("creates simple name without namespace") {
        val name = XmlName("element")
        assertTrue(
          name.localName == "element",
          name.namespace.isEmpty,
          !name.hasNamespace,
          name.namespaceOrEmpty == "",
          name.toString == "element"
        )
      },
      test("creates name with namespace") {
        val name = XmlName("element", "http://example.com")
        assertTrue(
          name.localName == "element",
          name.namespace.contains("http://example.com"),
          name.hasNamespace,
          name.namespaceOrEmpty == "http://example.com",
          name.toString == "{http://example.com}element"
        )
      },
      test("withNamespace adds namespace") {
        val name   = XmlName("element")
        val withNs = name.withNamespace("http://example.com")
        assertTrue(
          withNs.namespace.contains("http://example.com"),
          withNs.toString == "{http://example.com}element"
        )
      },
      test("withoutNamespace removes namespace") {
        val name      = XmlName("element", "http://example.com")
        val withoutNs = name.withoutNamespace
        assertTrue(
          withoutNs.namespace.isEmpty,
          withoutNs.toString == "element"
        )
      },
      test("parse handles simple name") {
        val name = XmlName.parse("element")
        assertTrue(
          name.localName == "element",
          name.namespace.isEmpty
        )
      },
      test("parse handles namespaced name") {
        val name = XmlName.parse("{http://example.com}element")
        assertTrue(
          name.localName == "element",
          name.namespace.contains("http://example.com")
        )
      }
    ),
    suite("XmlType")(
      test("typeIndex values are unique") {
        val indices = Set(
          XmlType.Element.typeIndex,
          XmlType.Text.typeIndex,
          XmlType.CData.typeIndex,
          XmlType.Comment.typeIndex,
          XmlType.ProcessingInstruction.typeIndex
        )
        assertTrue(indices.size == 5)
      },
      test("apply works as predicate") {
        val elem = Xml.Element("test")
        val text = Xml.Text("content")
        assertTrue(
          XmlType.Element(elem),
          !XmlType.Text(elem),
          XmlType.Text(text),
          !XmlType.Element(text)
        )
      }
    ),
    suite("Xml ADT")(
      suite("Element")(
        test("creates element with name only") {
          val elem = Xml.Element("root")
          assertTrue(
            elem.name.localName == "root",
            elem.attributes.isEmpty,
            elem.children.isEmpty
          )
        },
        test("creates element with children") {
          val elem = Xml.Element(
            "parent",
            Xml.Text("text1"),
            Xml.Element("child")
          )
          val isText    = elem.children(0).is(XmlType.Text)
          val isElement = elem.children(1).is(XmlType.Element)
          assertTrue(
            elem.children.length == 2,
            isText,
            isElement
          )
        },
        test("creates element with namespace") {
          val elem = Xml.Element("elem", "http://example.com")
          assertTrue(
            elem.name.localName == "elem",
            elem.name.namespace.contains("http://example.com")
          )
        },
        test("creates element with full constructor") {
          val elem = Xml.Element(
            XmlName("elem"),
            Chunk((XmlName("attr"), "value")),
            Chunk(Xml.Text("content"))
          )
          assertTrue(
            elem.attributes.length == 1,
            elem.attributes(0)._1.localName == "attr",
            elem.attributes(0)._2 == "value",
            elem.children.length == 1
          )
        }
      ),
      suite("unified type operations")(
        test("is returns true when type matches") {
          val elemCheck    = Xml.Element("test").is(XmlType.Element)
          val textCheck    = Xml.Text("test").is(XmlType.Text)
          val cdataCheck   = Xml.CData("test").is(XmlType.CData)
          val commentCheck = Xml.Comment("test").is(XmlType.Comment)
          val piCheck      = Xml.ProcessingInstruction("target", "data").is(XmlType.ProcessingInstruction)
          assertTrue(
            elemCheck,
            textCheck,
            cdataCheck,
            commentCheck,
            piCheck
          )
        },
        test("is returns false when type does not match") {
          val elem       = Xml.Element("test")
          val notText    = !elem.is(XmlType.Text)
          val notCData   = !elem.is(XmlType.CData)
          val notComment = !elem.is(XmlType.Comment)
          val notPI      = !elem.is(XmlType.ProcessingInstruction)
          assertTrue(
            notText,
            notCData,
            notComment,
            notPI
          )
        },
        test("as returns Some when type matches") {
          val elem: Xml = Xml.Element("test")
          val text: Xml = Xml.Text("content")

          val elemResult = elem.as(XmlType.Element)
          val textResult = text.as(XmlType.Text)

          val _: Option[Xml.Element] = elemResult
          val _: Option[Xml.Text]    = textResult

          assertTrue(
            elemResult.isDefined,
            textResult.isDefined
          )
        },
        test("as returns None when type does not match") {
          val elem: Xml = Xml.Element("test")
          assertTrue(
            elem.as(XmlType.Text).isEmpty,
            elem.as(XmlType.CData).isEmpty,
            elem.as(XmlType.Comment).isEmpty
          )
        },
        test("unwrap returns Some with correct value when type matches") {
          val elem: Xml = Xml.Element(
            XmlName("test"),
            Chunk((XmlName("attr"), "value")),
            Chunk(Xml.Text("child"))
          )
          val text: Xml    = Xml.Text("content")
          val cdata: Xml   = Xml.CData("data")
          val comment: Xml = Xml.Comment("comment")
          val pi: Xml      = Xml.ProcessingInstruction("target", "data")

          val elemUnwrap    = elem.unwrap(XmlType.Element)
          val textUnwrap    = text.unwrap(XmlType.Text)
          val cdataUnwrap   = cdata.unwrap(XmlType.CData)
          val commentUnwrap = comment.unwrap(XmlType.Comment)
          val piUnwrap      = pi.unwrap(XmlType.ProcessingInstruction)

          val _: Option[(XmlName, Chunk[(XmlName, String)], Chunk[Xml])] = elemUnwrap
          val _: Option[String]                                          = textUnwrap
          val _: Option[String]                                          = cdataUnwrap
          val _: Option[String]                                          = commentUnwrap
          val _: Option[(String, String)]                                = piUnwrap

          assertTrue(
            elemUnwrap.isDefined,
            textUnwrap.contains("content"),
            cdataUnwrap.contains("data"),
            commentUnwrap.contains("comment"),
            piUnwrap.contains(("target", "data"))
          )
        },
        test("unwrap returns None when type does not match") {
          val elem: Xml = Xml.Element("test")
          assertTrue(
            elem.unwrap(XmlType.Text).isEmpty,
            elem.unwrap(XmlType.CData).isEmpty,
            elem.unwrap(XmlType.Comment).isEmpty,
            elem.unwrap(XmlType.ProcessingInstruction).isEmpty
          )
        }
      ),
      suite("Text node")(
        test("stores text content") {
          val text = Xml.Text("Hello, World!")
          assertTrue(
            text.value == "Hello, World!",
            text.xmlType == XmlType.Text
          )
        }
      ),
      suite("CData node")(
        test("stores CDATA content") {
          val cdata = Xml.CData("<unescaped>")
          assertTrue(
            cdata.value == "<unescaped>",
            cdata.xmlType == XmlType.CData
          )
        }
      ),
      suite("Comment node")(
        test("stores comment content") {
          val comment = Xml.Comment("This is a comment")
          assertTrue(
            comment.value == "This is a comment",
            comment.xmlType == XmlType.Comment
          )
        }
      ),
      suite("ProcessingInstruction node")(
        test("stores target and data") {
          val pi = Xml.ProcessingInstruction("xml-stylesheet", "type=\"text/xsl\"")
          assertTrue(
            pi.target == "xml-stylesheet",
            pi.data == "type=\"text/xsl\"",
            pi.xmlType == XmlType.ProcessingInstruction
          )
        }
      )
    )
  )
}
