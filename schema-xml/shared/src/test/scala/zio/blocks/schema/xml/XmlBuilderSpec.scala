package zio.blocks.schema.xml

import zio.blocks.schema.SchemaBaseSpec
import zio.test._

object XmlBuilderSpec extends SchemaBaseSpec {
  def spec: Spec[TestEnvironment, Any] = suite("XmlBuilderSpec")(
    suite("XmlBuilder.element")(
      test("creates empty element") {
        val elem = XmlBuilder.element("root").build
        assertTrue(
          elem.name.localName == "root",
          elem.attributes.isEmpty,
          elem.children.isEmpty
        )
      },
      test("creates element with namespace") {
        val elem = XmlBuilder.element(XmlName("root", "http://example.com")).build
        assertTrue(
          elem.name.localName == "root",
          elem.name.namespace.contains("http://example.com"),
          elem.attributes.isEmpty,
          elem.children.isEmpty
        )
      }
    ),
    suite("attribute building")(
      test("adds single attribute with string name") {
        val elem = XmlBuilder
          .element("root")
          .attr("id", "1")
          .build
        assertTrue(
          elem.attributes.length == 1,
          elem.attributes(0)._1.localName == "id",
          elem.attributes(0)._2 == "1"
        )
      },
      test("adds multiple attributes") {
        val elem = XmlBuilder
          .element("root")
          .attr("id", "1")
          .attr("class", "main")
          .attr("data-value", "test")
          .build
        assertTrue(
          elem.attributes.length == 3,
          elem.attributes(0)._1.localName == "id",
          elem.attributes(0)._2 == "1",
          elem.attributes(1)._1.localName == "class",
          elem.attributes(1)._2 == "main",
          elem.attributes(2)._1.localName == "data-value",
          elem.attributes(2)._2 == "test"
        )
      },
      test("adds attribute with XmlName") {
        val elem = XmlBuilder
          .element("root")
          .attr(XmlName("attr", "http://example.com"), "value")
          .build
        assertTrue(
          elem.attributes.length == 1,
          elem.attributes(0)._1.localName == "attr",
          elem.attributes(0)._1.namespace.contains("http://example.com"),
          elem.attributes(0)._2 == "value"
        )
      }
    ),
    suite("child building")(
      test("adds single child element") {
        val child = XmlBuilder.element("child").build
        val elem  = XmlBuilder
          .element("root")
          .child(child)
          .build
        val isElement = elem.children(0).is(XmlType.Element)
        assertTrue(
          elem.children.length == 1,
          isElement
        )
      },
      test("adds multiple children individually") {
        val child1 = XmlBuilder.element("child1").build
        val child2 = XmlBuilder.element("child2").build
        val elem   = XmlBuilder
          .element("root")
          .child(child1)
          .child(child2)
          .build
        val isElement0 = elem.children(0).is(XmlType.Element)
        val isElement1 = elem.children(1).is(XmlType.Element)
        assertTrue(
          elem.children.length == 2,
          isElement0,
          isElement1
        )
      },
      test("adds multiple children with children method") {
        val child1 = XmlBuilder.element("child1").build
        val child2 = XmlBuilder.element("child2").build
        val elem   = XmlBuilder
          .element("root")
          .children(child1, child2)
          .build
        val isElement0 = elem.children(0).is(XmlType.Element)
        val isElement1 = elem.children(1).is(XmlType.Element)
        assertTrue(
          elem.children.length == 2,
          isElement0,
          isElement1
        )
      }
    ),
    suite("text content")(
      test("adds text child") {
        val elem = XmlBuilder
          .element("root")
          .text("Hello World")
          .build
        val isText = elem.children(0).is(XmlType.Text)
        assertTrue(
          elem.children.length == 1,
          isText,
          elem.children(0).as(XmlType.Text).exists(_.value == "Hello World")
        )
      },
      test("adds multiple text children") {
        val elem = XmlBuilder
          .element("root")
          .text("Hello")
          .text("World")
          .build
        val isText0 = elem.children(0).is(XmlType.Text)
        val isText1 = elem.children(1).is(XmlType.Text)
        assertTrue(
          elem.children.length == 2,
          isText0,
          isText1,
          elem.children(0).as(XmlType.Text).exists(_.value == "Hello"),
          elem.children(1).as(XmlType.Text).exists(_.value == "World")
        )
      }
    ),
    suite("mixed content")(
      test("combines elements and text") {
        val elem = XmlBuilder
          .element("root")
          .text("Start")
          .child(XmlBuilder.element("child").text("nested").build)
          .text("End")
          .build
        val isText0   = elem.children(0).is(XmlType.Text)
        val isElement = elem.children(1).is(XmlType.Element)
        val isText2   = elem.children(2).is(XmlType.Text)
        assertTrue(
          elem.children.length == 3,
          isText0,
          isElement,
          isText2
        )
      },
      test("builds complex element with attributes, text, and nested elements") {
        val nested = XmlBuilder
          .element("child")
          .attr("id", "1")
          .text("content")
          .build
        val elem = XmlBuilder
          .element("root")
          .attr("name", "test")
          .attr("version", "1.0")
          .text("Before")
          .child(nested)
          .text("After")
          .build
        val isText0   = elem.children(0).is(XmlType.Text)
        val isElement = elem.children(1).is(XmlType.Element)
        val isText2   = elem.children(2).is(XmlType.Text)
        assertTrue(
          elem.name.localName == "root",
          elem.attributes.length == 2,
          elem.children.length == 3,
          isText0,
          isElement,
          isText2
        )
      }
    ),
    suite("factory methods")(
      test("XmlBuilder.text creates Text node") {
        val text   = XmlBuilder.text("Hello")
        val isText = text.is(XmlType.Text)
        assertTrue(
          isText,
          text.as(XmlType.Text).exists(_.value == "Hello")
        )
      },
      test("XmlBuilder.cdata creates CData node") {
        val cdata   = XmlBuilder.cdata("<unescaped>")
        val isCData = cdata.is(XmlType.CData)
        assertTrue(
          isCData,
          cdata.as(XmlType.CData).exists(_.value == "<unescaped>")
        )
      },
      test("XmlBuilder.comment creates Comment node") {
        val comment   = XmlBuilder.comment("This is a comment")
        val isComment = comment.is(XmlType.Comment)
        assertTrue(
          isComment,
          comment.as(XmlType.Comment).exists(_.value == "This is a comment")
        )
      },
      test("XmlBuilder.processingInstruction creates ProcessingInstruction node") {
        val pi   = XmlBuilder.processingInstruction("xml-stylesheet", "type=\"text/xsl\"")
        val isPI = pi.is(XmlType.ProcessingInstruction)
        assertTrue(
          isPI,
          pi.unwrap(XmlType.ProcessingInstruction).exists { case (target, data) =>
            target == "xml-stylesheet" && data == "type=\"text/xsl\""
          }
        )
      }
    ),
    suite("fluent API composition")(
      test("builds deeply nested structure") {
        val elem = XmlBuilder
          .element("root")
          .attr("level", "0")
          .child(
            XmlBuilder
              .element("level1")
              .attr("level", "1")
              .child(
                XmlBuilder
                  .element("level2")
                  .attr("level", "2")
                  .text("deep content")
                  .build
              )
              .build
          )
          .build
        val isElement = elem.children(0).is(XmlType.Element)
        assertTrue(
          elem.name.localName == "root",
          elem.children.length == 1,
          isElement
        )
      },
      test("example from spec works") {
        val elem = XmlBuilder
          .element("root")
          .attr("id", "1")
          .attr("class", "main")
          .child(XmlBuilder.element("child").text("content").build)
          .child(XmlBuilder.text("more text"))
          .build
        val isElement = elem.children(0).is(XmlType.Element)
        val isText    = elem.children(1).is(XmlType.Text)
        assertTrue(
          elem.name.localName == "root",
          elem.attributes.length == 2,
          elem.children.length == 2,
          isElement,
          isText
        )
      }
    ),
    suite("namespace support")(
      test("builds namespaced element") {
        val elem = XmlBuilder
          .element(XmlName("root", "http://example.com"))
          .build
        assertTrue(
          elem.name.localName == "root",
          elem.name.namespace.contains("http://example.com")
        )
      },
      test("builds namespaced element with namespaced attributes") {
        val elem = XmlBuilder
          .element(XmlName("root", "http://example.com"))
          .attr(XmlName("attr", "http://attr.example.com"), "value")
          .build
        assertTrue(
          elem.name.namespace.contains("http://example.com"),
          elem.attributes.length == 1,
          elem.attributes(0)._1.namespace.contains("http://attr.example.com")
        )
      }
    )
  )
}
