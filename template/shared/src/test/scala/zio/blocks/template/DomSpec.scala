package zio.blocks.template

import zio.test._

object DomSpec extends ZIOSpecDefault {
  def spec = suite("Dom")(
    suite("Text")(
      test("renders plain text") {
        assertTrue(Dom.Text("hello").render == "hello")
      },
      test("escapes HTML in text") {
        assertTrue(Dom.Text("<script>").render == "&lt;script&gt;")
      },
      test("escapes ampersands") {
        assertTrue(Dom.Text("a&b").render == "a&amp;b")
      },
      test("escapes quotes") {
        assertTrue(Dom.Text("\"quoted\"").render == "&quot;quoted&quot;")
      }
    ),
    suite("RawHtml")(
      test("renders without escaping") {
        assertTrue(Dom.RawHtml("<b>bold</b>").render == "<b>bold</b>")
      }
    ),
    suite("Empty")(
      test("renders empty string") {
        assertTrue(Dom.Empty.render == "")
      }
    ),
    suite("Element")(
      test("renders empty element") {
        assertTrue(Dom.Element("div", Vector.empty, Vector.empty).render == "<div></div>")
      },
      test("renders element with string attribute") {
        val attr = Dom.Attribute.KeyValue("id", Dom.AttributeValue.StringValue("main"))
        val el   = Dom.Element("div", Vector(attr), Vector.empty)
        assertTrue(el.render == "<div id=\"main\"></div>")
      },
      test("renders element with text child") {
        val el = Dom.Element("div", Vector.empty, Vector(Dom.Text("hello")))
        assertTrue(el.render == "<div>hello</div>")
      },
      test("renders nested elements") {
        val inner = Dom.Element("span", Vector.empty, Vector(Dom.Text("inner")))
        val outer = Dom.Element("div", Vector.empty, Vector(inner))
        assertTrue(outer.render == "<div><span>inner</span></div>")
      },
      test("renders boolean attribute") {
        val attr = Dom.Attribute.BooleanAttribute("required")
        val el   = Dom.Element("input", Vector(attr), Vector.empty)
        assertTrue(el.render == "<input required>")
      },
      test("renders multi-value attribute") {
        val attr = Dom.Attribute.KeyValue("class", Dom.AttributeValue.MultiValue(Vector("a", "b"), ' '))
        val el   = Dom.Element("div", Vector(attr), Vector.empty)
        assertTrue(el.render == "<div class=\"a b\"></div>")
      },
      test("omits empty multi-value attribute") {
        val attr = Dom.Attribute.KeyValue("class", Dom.AttributeValue.MultiValue(Vector.empty, ' '))
        val el   = Dom.Element("div", Vector(attr), Vector.empty)
        assertTrue(el.render == "<div></div>")
      },
      test("renders JsValue attribute unescaped") {
        val attr = Dom.Attribute.KeyValue("onclick", Dom.AttributeValue.JsValue(Js("alert('hi')")))
        val el   = Dom.Element("button", Vector(attr), Vector.empty)
        assertTrue(el.render == "<button onclick=\"alert('hi')\"></button>")
      },
      test("BooleanValue(false) omits attribute") {
        val attr = Dom.Attribute.KeyValue("disabled", Dom.AttributeValue.BooleanValue(false))
        val el   = Dom.Element("input", Vector(attr), Vector.empty)
        assertTrue(el.render == "<input>")
      },
      test("BooleanValue(true) renders as boolean attribute") {
        val attr = Dom.Attribute.KeyValue("disabled", Dom.AttributeValue.BooleanValue(true))
        val el   = Dom.Element("input", Vector(attr), Vector.empty)
        assertTrue(el.render == "<input disabled>")
      }
    ),
    suite("Void elements")(
      test("br has no closing tag") {
        assertTrue(Dom.Element("br", Vector.empty, Vector.empty).render == "<br>")
      },
      test("img has no closing tag") {
        val attr = Dom.Attribute.KeyValue("src", Dom.AttributeValue.StringValue("a.png"))
        assertTrue(Dom.Element("img", Vector(attr), Vector.empty).render == "<img src=\"a.png\">")
      },
      test("hr has no closing tag") {
        assertTrue(Dom.Element("hr", Vector.empty, Vector.empty).render == "<hr>")
      },
      test("input has no closing tag") {
        assertTrue(Dom.Element("input", Vector.empty, Vector.empty).render == "<input>")
      }
    ),
    suite("Fragment")(
      test("renders children in sequence") {
        val f = Dom.Fragment(Vector(Dom.Text("a"), Dom.Text("b")))
        assertTrue(f.render == "ab")
      },
      test("fragment factory: empty vector yields Empty") {
        assertTrue(Dom.fragment(Vector.empty) == Dom.Empty)
      },
      test("fragment factory: single element unwraps") {
        val t = Dom.Text("x")
        assertTrue(Dom.fragment(Vector(t)) == t)
      },
      test("fragment factory: multiple elements yields Fragment") {
        val children = Vector(Dom.Text("a"), Dom.Text("b"))
        assertTrue(Dom.fragment(children) == Dom.Fragment(children))
      }
    ),
    suite("renderMinified")(
      test("renderMinified produces same output as render for basic elements") {
        val el = Dom.Element("div", Vector.empty, Vector(Dom.Text("hi")))
        assertTrue(el.renderMinified == "<div>hi</div>")
      }
    )
  )
}
