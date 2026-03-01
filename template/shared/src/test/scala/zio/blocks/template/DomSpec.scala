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
        assertTrue(Dom.Element.Generic("div", Vector.empty, Vector.empty).render == "<div></div>")
      },
      test("renders element with string attribute") {
        val attr = Dom.Attribute.KeyValue("id", Dom.AttributeValue.StringValue("main"))
        val el   = Dom.Element.Generic("div", Vector(attr), Vector.empty)
        assertTrue(el.render == "<div id=\"main\"></div>")
      },
      test("renders element with text child") {
        val el = Dom.Element.Generic("div", Vector.empty, Vector(Dom.Text("hello")))
        assertTrue(el.render == "<div>hello</div>")
      },
      test("renders nested elements") {
        val inner = Dom.Element.Generic("span", Vector.empty, Vector(Dom.Text("inner")))
        val outer = Dom.Element.Generic("div", Vector.empty, Vector(inner))
        assertTrue(outer.render == "<div><span>inner</span></div>")
      },
      test("renders boolean attribute") {
        val attr = Dom.Attribute.BooleanAttribute("required")
        val el   = Dom.Element.Generic("input", Vector(attr), Vector.empty)
        assertTrue(el.render == "<input required/>")
      },
      test("renders multi-value attribute") {
        val attr =
          Dom.Attribute.KeyValue("class", Dom.AttributeValue.MultiValue(Vector("a", "b"), Dom.AttributeSeparator.Space))
        val el = Dom.Element.Generic("div", Vector(attr), Vector.empty)
        assertTrue(el.render == "<div class=\"a b\"></div>")
      },
      test("omits empty multi-value attribute") {
        val attr = Dom.Attribute.KeyValue(
          "class",
          Dom.AttributeValue.MultiValue(Vector.empty, Dom.AttributeSeparator.Space)
        )
        val el = Dom.Element.Generic("div", Vector(attr), Vector.empty)
        assertTrue(el.render == "<div></div>")
      },
      test("renders JsValue attribute unescaped") {
        val attr = Dom.Attribute.KeyValue("onclick", Dom.AttributeValue.JsValue(Js("alert('hi')")))
        val el   = Dom.Element.Generic("button", Vector(attr), Vector.empty)
        assertTrue(el.render == "<button onclick=\"alert('hi')\"></button>")
      },
      test("BooleanValue(false) omits attribute") {
        val attr = Dom.Attribute.KeyValue("disabled", Dom.AttributeValue.BooleanValue(false))
        val el   = Dom.Element.Generic("input", Vector(attr), Vector.empty)
        assertTrue(el.render == "<input/>")
      },
      test("BooleanValue(true) renders as boolean attribute") {
        val attr = Dom.Attribute.KeyValue("disabled", Dom.AttributeValue.BooleanValue(true))
        val el   = Dom.Element.Generic("input", Vector(attr), Vector.empty)
        assertTrue(el.render == "<input disabled/>")
      },
      test("Generic element escapes text content") {
        val el = Dom.Element.Generic("div", Vector.empty, Vector(Dom.Text("1 < 2")))
        assertTrue(el.render == "<div>1 &lt; 2</div>")
      }
    ),
    suite("Script element")(
      test("renders JS content without escaping") {
        val s = Dom.Element.Script(Vector.empty, Vector(Dom.Text("var x = 1 < 2;")))
        assertTrue(s.render == "<script>var x = 1 < 2;</script>")
      },
      test("inlineJs convenience method with String") {
        val s = Dom.Element.Script(Vector.empty, Vector.empty).inlineJs("alert('hello')")
        assertTrue(s.render == "<script>alert('hello')</script>")
      },
      test("inlineJs convenience method with Js") {
        val s = Dom.Element.Script(Vector.empty, Vector.empty).inlineJs(Js("console.log(1)"))
        assertTrue(s.render == "<script>console.log(1)</script>")
      },
      test("externalJs convenience method") {
        val s = Dom.Element.Script(Vector.empty, Vector.empty).externalJs("app.js")
        assertTrue(s.render == """<script src="app.js"></script>""")
      },
      test("tag is script") {
        assertTrue(Dom.Element.Script(Vector.empty, Vector.empty).tag == "script")
      },
      test("withAttributes and withChildren") {
        val attr = Dom.Attribute.KeyValue("type", Dom.AttributeValue.StringValue("module"))
        val s    = Dom.Element.Script(Vector.empty, Vector.empty).withAttributes(Vector(attr))
        assertTrue(s.render == """<script type="module"></script>""")
      }
    ),
    suite("Style element")(
      test("renders CSS content without escaping") {
        val s = Dom.Element.Style(Vector.empty, Vector(Dom.Text("div > p { color: red; }")))
        assertTrue(s.render == "<style>div > p { color: red; }</style>")
      },
      test("inlineCss convenience method with String") {
        val s = Dom.Element.Style(Vector.empty, Vector.empty).inlineCss("body { margin: 0; }")
        assertTrue(s.render == "<style>body { margin: 0; }</style>")
      },
      test("inlineCss convenience method with Css") {
        val s = Dom.Element.Style(Vector.empty, Vector.empty).inlineCss(Css("a > b { color: blue; }"))
        assertTrue(s.render == "<style>a > b { color: blue; }</style>")
      },
      test("tag is style") {
        assertTrue(Dom.Element.Style(Vector.empty, Vector.empty).tag == "style")
      }
    ),
    suite("Void elements")(
      test("br self-closes") {
        assertTrue(Dom.Element.Generic("br", Vector.empty, Vector.empty).render == "<br/>")
      },
      test("img self-closes with attributes") {
        val attr = Dom.Attribute.KeyValue("src", Dom.AttributeValue.StringValue("a.png"))
        assertTrue(Dom.Element.Generic("img", Vector(attr), Vector.empty).render == "<img src=\"a.png\"/>")
      },
      test("hr self-closes") {
        assertTrue(Dom.Element.Generic("hr", Vector.empty, Vector.empty).render == "<hr/>")
      },
      test("input self-closes") {
        assertTrue(Dom.Element.Generic("input", Vector.empty, Vector.empty).render == "<input/>")
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
        val el = Dom.Element.Generic("div", Vector.empty, Vector(Dom.Text("hi")))
        assertTrue(el.renderMinified == "<div>hi</div>")
      }
    ),
    suite("indentation rendering")(
      test("single text child stays inline") {
        val el = Dom.Element.Generic("p", Vector.empty, Vector(Dom.Text("Simple text")))
        assertTrue(el.render(indentation = true) == "<p>Simple text</p>")
      },
      test("multiple children get newlines and indentation") {
        val el = Dom.Element.Generic(
          "div",
          Vector.empty,
          Vector(
            Dom.Element.Generic("h1", Vector.empty, Vector(Dom.Text("Title"))),
            Dom.Element.Generic("p", Vector.empty, Vector(Dom.Text("Content")))
          )
        )
        val expected = "<div>\n  <h1>Title</h1>\n  <p>Content</p>\n</div>"
        assertTrue(el.render(indentation = true) == expected)
      },
      test("nested indentation") {
        val el = Dom.Element.Generic(
          "div",
          Vector.empty,
          Vector(
            Dom.Element.Generic(
              "ul",
              Vector.empty,
              Vector(
                Dom.Element.Generic("li", Vector.empty, Vector(Dom.Text("a"))),
                Dom.Element.Generic("li", Vector.empty, Vector(Dom.Text("b")))
              )
            )
          )
        )
        val expected = "<div>\n  <ul>\n    <li>a</li>\n    <li>b</li>\n  </ul>\n</div>"
        assertTrue(el.render(indentation = true) == expected)
      },
      test("indentation=false behaves like render") {
        val el = Dom.Element.Generic(
          "div",
          Vector.empty,
          Vector(
            Dom.Element.Generic("p", Vector.empty, Vector(Dom.Text("hi")))
          )
        )
        assertTrue(el.render(indentation = false) == el.render)
      }
    ),
    suite("boolAttr")(
      test("enabled=true renders attribute") {
        val attr = Dom.boolAttr("required")
        val el   = Dom.Element.Generic("input", Vector(attr), Vector.empty)
        assertTrue(el.render == "<input required/>")
      },
      test("enabled=false omits attribute") {
        val attr = Dom.boolAttr("required", enabled = false)
        val el   = Dom.Element.Generic("input", Vector(attr), Vector.empty)
        assertTrue(el.render == "<input/>")
      }
    ),
    suite("when/whenSome")(
      test("when true applies modifiers") {
        val el     = Dom.Element.Generic("div", Vector.empty, Vector.empty)
        val result = el.when(true)(
          Modifier.attributeToModifier(Dom.Attribute.KeyValue("id", Dom.AttributeValue.StringValue("main")))
        )
        assertTrue(result.render == "<div id=\"main\"></div>")
      },
      test("when false returns unchanged") {
        val el     = Dom.Element.Generic("div", Vector.empty, Vector.empty)
        val result = el.when(false)(
          Modifier.attributeToModifier(Dom.Attribute.KeyValue("id", Dom.AttributeValue.StringValue("main")))
        )
        assertTrue(result.render == "<div></div>")
      },
      test("whenSome with Some applies modifiers") {
        val el     = Dom.Element.Generic("div", Vector.empty, Vector.empty)
        val result = el.whenSome(Some("highlight")) { cls =>
          Seq(
            Modifier.attributeToModifier(Dom.Attribute.KeyValue("class", Dom.AttributeValue.StringValue(cls)))
          )
        }
        assertTrue(result.render == "<div class=\"highlight\"></div>")
      },
      test("whenSome with None returns unchanged") {
        val el     = Dom.Element.Generic("div", Vector.empty, Vector.empty)
        val result = el.whenSome(Option.empty[String]) { cls =>
          Seq(
            Modifier.attributeToModifier(Dom.Attribute.KeyValue("class", Dom.AttributeValue.StringValue(cls)))
          )
        }
        assertTrue(result.render == "<div></div>")
      }
    ),
    suite("DOM traversal")(
      test("collect finds matching nodes") {
        val tree = Dom.Element.Generic(
          "div",
          Vector.empty,
          Vector(
            Dom.Element.Generic("p", Vector.empty, Vector(Dom.Text("a"))),
            Dom.Element.Generic("span", Vector.empty, Vector(Dom.Text("b")))
          )
        )
        val found = tree.collect { case el: Dom.Element if el.tag == "p" => el }
        assertTrue(found.length == 1)
      },
      test("find locates first matching node") {
        val tree = Dom.Element.Generic(
          "div",
          Vector.empty,
          Vector(
            Dom.Element.Generic("p", Vector.empty, Vector(Dom.Text("first"))),
            Dom.Element.Generic("p", Vector.empty, Vector(Dom.Text("second")))
          )
        )
        val found = tree.find {
          case el: Dom.Element => el.tag == "p"
          case _               => false
        }
        assertTrue(found.isDefined)
      },
      test("filter removes non-matching nodes") {
        val tree = Dom.Element.Generic(
          "div",
          Vector.empty,
          Vector(
            Dom.Element.Generic("p", Vector.empty, Vector(Dom.Text("keep"))),
            Dom.Element.Generic("span", Vector.empty, Vector(Dom.Text("remove")))
          )
        )
        val filtered = tree.filter {
          case el: Dom.Element => el.tag != "span"
          case _               => true
        }
        assertTrue(!filtered.render.contains("remove"))
      },
      test("transform modifies nodes") {
        val tree        = Dom.Element.Generic("div", Vector.empty, Vector(Dom.Text("hello")))
        val transformed = tree.transform {
          case Dom.Text("hello") => Dom.Text("world")
          case other             => other
        }
        assertTrue(transformed.render == "<div>world</div>")
      },
      test("isEmpty") {
        assertTrue(
          Dom.Empty.isEmpty,
          Dom.Text("").isEmpty,
          !Dom.Text("hi").isEmpty,
          !Dom.Element.Generic("div", Vector.empty, Vector.empty).isEmpty
        )
      }
    ),
    suite("Element apply for curried modifiers")(
      test("apply adds modifiers to existing element") {
        val el     = Dom.Element.Generic("div", Vector.empty, Vector.empty)
        val result = el(Modifier.stringToModifier("hello"))
        assertTrue(result.render == "<div>hello</div>")
      }
    ),
    suite("AttributeSeparator")(
      test("Space separator") {
        val attr = Dom.Attribute.KeyValue(
          "class",
          Dom.AttributeValue.MultiValue(Vector("a", "b"), Dom.AttributeSeparator.Space)
        )
        val el = Dom.Element.Generic("div", Vector(attr), Vector.empty)
        assertTrue(el.render == "<div class=\"a b\"></div>")
      },
      test("Comma separator") {
        val attr = Dom.Attribute.KeyValue(
          "accept",
          Dom.AttributeValue.MultiValue(Vector("text/html", "application/json"), Dom.AttributeSeparator.Comma)
        )
        val el = Dom.Element.Generic("input", Vector(attr), Vector.empty)
        assertTrue(el.render == "<input accept=\"text/html,application/json\"/>")
      },
      test("Semicolon separator") {
        val attr = Dom.Attribute.KeyValue(
          "style",
          Dom.AttributeValue.MultiValue(Vector("color: red", "font-size: 14px"), Dom.AttributeSeparator.Semicolon)
        )
        val el = Dom.Element.Generic("div", Vector(attr), Vector.empty)
        assertTrue(el.render == "<div style=\"color: red;font-size: 14px\"></div>")
      },
      test("Custom separator") {
        val attr = Dom.Attribute.KeyValue(
          "data-tags",
          Dom.AttributeValue.MultiValue(Vector("a", "b"), Dom.AttributeSeparator.Custom(" | "))
        )
        val el = Dom.Element.Generic("div", Vector(attr), Vector.empty)
        assertTrue(el.render == "<div data-tags=\"a | b\"></div>")
      }
    ),
    suite("multiAttr")(
      test("multiAttr factory creates attribute") {
        val attr = Dom.multiAttr("class", Dom.AttributeSeparator.Space, "foo", "bar")
        val el   = Dom.Element.Generic("div", Vector(attr), Vector.empty)
        assertTrue(el.render == "<div class=\"foo bar\"></div>")
      },
      test("multiAttr with Iterable") {
        val attr = Dom.multiAttr("class", List("a", "b", "c"))
        val el   = Dom.Element.Generic("div", Vector(attr), Vector.empty)
        assertTrue(el.render == "<div class=\"a b c\"></div>")
      },
      test("PartialMultiAttribute := varargs") {
        val cls  = new PartialMultiAttribute("class", Dom.AttributeSeparator.Space)
        val attr = cls.:=("foo", "bar", "baz")
        val el   = Dom.Element.Generic("div", Vector(attr), Vector.empty)
        assertTrue(el.render == "<div class=\"foo bar baz\"></div>")
      },
      test("PartialMultiAttribute apply") {
        val cls  = new PartialMultiAttribute("class", Dom.AttributeSeparator.Space)
        val attr = cls("foo", "bar")
        val el   = Dom.Element.Generic("div", Vector(attr), Vector.empty)
        assertTrue(el.render == "<div class=\"foo bar\"></div>")
      }
    )
  )
}
