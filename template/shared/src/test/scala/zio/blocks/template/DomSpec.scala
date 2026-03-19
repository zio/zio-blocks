package zio.blocks.template

import zio.blocks.chunk.Chunk
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
    suite("Empty")(
      test("renders empty string") {
        assertTrue(Dom.Empty.render == "")
      }
    ),
    suite("PreRendered")(
      test("renders raw HTML without escaping") {
        assertTrue(Dom.PreRendered("<b>bold</b>").render == "<b>bold</b>")
      },
      test("renders empty PreRendered") {
        assertTrue(Dom.PreRendered("").render == "")
      },
      test("renderMinified for PreRendered") {
        assertTrue(Dom.PreRendered("<i>italic</i>").renderMinified == "<i>italic</i>")
      }
    ),
    suite("Element")(
      test("renders empty element") {
        assertTrue(Dom.Element.Generic("div", Chunk.empty, Chunk.empty).render == "<div></div>")
      },
      test("renders element with string attribute") {
        val attr = Dom.Attribute.KeyValue("id", Dom.AttributeValue.StringValue("main"))
        val el   = Dom.Element.Generic("div", Chunk(attr), Chunk.empty)
        assertTrue(el.render == "<div id=\"main\"></div>")
      },
      test("renders element with text child") {
        val el = Dom.Element.Generic("div", Chunk.empty, Chunk(Dom.Text("hello")))
        assertTrue(el.render == "<div>hello</div>")
      },
      test("renders nested elements") {
        val inner = Dom.Element.Generic("span", Chunk.empty, Chunk(Dom.Text("inner")))
        val outer = Dom.Element.Generic("div", Chunk.empty, Chunk(inner))
        assertTrue(outer.render == "<div><span>inner</span></div>")
      },
      test("renders boolean attribute") {
        val attr = Dom.Attribute.BooleanAttribute("required")
        val el   = Dom.Element.Generic("input", Chunk(attr), Chunk.empty)
        assertTrue(el.render == "<input required/>")
      },
      test("renders multi-value attribute") {
        val attr =
          Dom.Attribute.KeyValue("class", Dom.AttributeValue.MultiValue(Chunk("a", "b"), Dom.AttributeSeparator.Space))
        val el = Dom.Element.Generic("div", Chunk(attr), Chunk.empty)
        assertTrue(el.render == "<div class=\"a b\"></div>")
      },
      test("omits empty multi-value attribute") {
        val attr = Dom.Attribute.KeyValue(
          "class",
          Dom.AttributeValue.MultiValue(Chunk.empty, Dom.AttributeSeparator.Space)
        )
        val el = Dom.Element.Generic("div", Chunk(attr), Chunk.empty)
        assertTrue(el.render == "<div></div>")
      },
      test("renders JsValue attribute HTML-escaped") {
        val attr = Dom.Attribute.KeyValue("onclick", Dom.AttributeValue.JsValue(Js("alert('hi')")))
        val el   = Dom.Element.Generic("button", Chunk(attr), Chunk.empty)
        assertTrue(el.render == "<button onclick=\"alert(&#x27;hi&#x27;)\"></button>")
      },
      test("BooleanValue(false) omits attribute") {
        val attr = Dom.Attribute.KeyValue("disabled", Dom.AttributeValue.BooleanValue(false))
        val el   = Dom.Element.Generic("input", Chunk(attr), Chunk.empty)
        assertTrue(el.render == "<input/>")
      },
      test("BooleanValue(true) renders as boolean attribute") {
        val attr = Dom.Attribute.KeyValue("disabled", Dom.AttributeValue.BooleanValue(true))
        val el   = Dom.Element.Generic("input", Chunk(attr), Chunk.empty)
        assertTrue(el.render == "<input disabled/>")
      },
      test("Generic element escapes text content") {
        val el = Dom.Element.Generic("div", Chunk.empty, Chunk(Dom.Text("1 < 2")))
        assertTrue(el.render == "<div>1 &lt; 2</div>")
      }
    ),
    suite("class attribute merging")(
      test("merges duplicate class attributes") {
        val a1 = Dom.Attribute.KeyValue("class", Dom.AttributeValue.StringValue("a"))
        val a2 = Dom.Attribute.KeyValue("class", Dom.AttributeValue.StringValue("b"))
        val el = Dom.Element.Generic("div", Chunk(a1, a2), Chunk.empty)
        assertTrue(el.render == "<div class=\"a b\"></div>")
      },
      test("single class attribute is unchanged") {
        val a1 = Dom.Attribute.KeyValue("class", Dom.AttributeValue.StringValue("only"))
        val el = Dom.Element.Generic("div", Chunk(a1), Chunk.empty)
        assertTrue(el.render == "<div class=\"only\"></div>")
      },
      test("no class attribute is unchanged") {
        val a1 = Dom.Attribute.KeyValue("id", Dom.AttributeValue.StringValue("x"))
        val el = Dom.Element.Generic("div", Chunk(a1), Chunk.empty)
        assertTrue(el.render == "<div id=\"x\"></div>")
      },
      test("merges class with other attributes preserved") {
        val a1 = Dom.Attribute.KeyValue("id", Dom.AttributeValue.StringValue("x"))
        val a2 = Dom.Attribute.KeyValue("class", Dom.AttributeValue.StringValue("a"))
        val a3 = Dom.Attribute.KeyValue("class", Dom.AttributeValue.StringValue("b"))
        val el = Dom.Element.Generic("div", Chunk(a1, a2, a3), Chunk.empty)
        assertTrue(el.render == "<div id=\"x\" class=\"a b\"></div>")
      },
      test("merges MultiValue class attributes") {
        val a1 =
          Dom.Attribute.KeyValue("class", Dom.AttributeValue.MultiValue(Chunk("a", "b"), Dom.AttributeSeparator.Space))
        val a2 = Dom.Attribute.KeyValue("class", Dom.AttributeValue.StringValue("c"))
        val el = Dom.Element.Generic("div", Chunk(a1, a2), Chunk.empty)
        assertTrue(el.render == "<div class=\"a b c\"></div>")
      },
      test("class merging works with indented rendering") {
        val a1 = Dom.Attribute.KeyValue("class", Dom.AttributeValue.StringValue("a"))
        val a2 = Dom.Attribute.KeyValue("class", Dom.AttributeValue.StringValue("b"))
        val el = Dom.Element.Generic("div", Chunk(a1, a2), Chunk(Dom.Text("text")))
        assertTrue(el.render(indent = 2) == "<div class=\"a b\">text</div>")
      },
      test("class merging works with renderMinified") {
        val a1 = Dom.Attribute.KeyValue("class", Dom.AttributeValue.StringValue("a"))
        val a2 = Dom.Attribute.KeyValue("class", Dom.AttributeValue.StringValue("b"))
        val el = Dom.Element.Generic("div", Chunk(a1, a2), Chunk.empty)
        assertTrue(el.renderMinified == "<div class=\"a b\"></div>")
      }
    ),
    suite("Script element")(
      test("renders JS content without escaping") {
        val s = Dom.Element.Script(Chunk.empty, Chunk(Dom.Text("var x = 1 < 2;")))
        assertTrue(s.render == "<script>var x = 1 < 2;</script>")
      },
      test("inlineJs convenience method with String") {
        val s = Dom.Element.Script(Chunk.empty, Chunk.empty).inlineJs("alert('hello')")
        assertTrue(s.render == "<script>alert('hello')</script>")
      },
      test("inlineJs convenience method with Js") {
        val s = Dom.Element.Script(Chunk.empty, Chunk.empty).inlineJs(Js("console.log(1)"))
        assertTrue(s.render == "<script>console.log(1)</script>")
      },
      test("externalJs convenience method") {
        val s = Dom.Element.Script(Chunk.empty, Chunk.empty).externalJs("app.js")
        assertTrue(s.render == """<script src="app.js"></script>""")
      },
      test("tag is script") {
        assertTrue(Dom.Element.Script(Chunk.empty, Chunk.empty).tag == "script")
      },
      test("withAttributes and withChildren") {
        val attr = Dom.Attribute.KeyValue("type", Dom.AttributeValue.StringValue("module"))
        val s    = Dom.Element.Script(Chunk.empty, Chunk.empty).withAttributes(Chunk(attr))
        assertTrue(s.render == """<script type="module"></script>""")
      }
    ),
    suite("Style element")(
      test("renders CSS content without escaping") {
        val s = Dom.Element.Style(Chunk.empty, Chunk(Dom.Text("div > p { color: red; }")))
        assertTrue(s.render == "<style>div > p { color: red; }</style>")
      },
      test("inlineCss convenience method with String") {
        val s = Dom.Element.Style(Chunk.empty, Chunk.empty).inlineCss("body { margin: 0; }")
        assertTrue(s.render == "<style>body { margin: 0; }</style>")
      },
      test("inlineCss convenience method with Css") {
        val s = Dom.Element.Style(Chunk.empty, Chunk.empty).inlineCss(Css("a > b { color: blue; }"))
        assertTrue(s.render == "<style>a > b { color: blue; }</style>")
      },
      test("tag is style") {
        assertTrue(Dom.Element.Style(Chunk.empty, Chunk.empty).tag == "style")
      }
    ),
    suite("Void elements")(
      test("br self-closes") {
        assertTrue(Dom.Element.Generic("br", Chunk.empty, Chunk.empty).render == "<br/>")
      },
      test("img self-closes with attributes") {
        val attr = Dom.Attribute.KeyValue("src", Dom.AttributeValue.StringValue("a.png"))
        assertTrue(Dom.Element.Generic("img", Chunk(attr), Chunk.empty).render == "<img src=\"a.png\"/>")
      },
      test("hr self-closes") {
        assertTrue(Dom.Element.Generic("hr", Chunk.empty, Chunk.empty).render == "<hr/>")
      },
      test("input self-closes") {
        assertTrue(Dom.Element.Generic("input", Chunk.empty, Chunk.empty).render == "<input/>")
      }
    ),
    suite("renderMinified")(
      test("renderMinified produces same output as render for basic elements") {
        val el = Dom.Element.Generic("div", Chunk.empty, Chunk(Dom.Text("hi")))
        assertTrue(el.renderMinified == "<div>hi</div>")
      },
      test("renderMinified for script element") {
        val s = Dom.Element.Script(Chunk.empty, Chunk(Dom.Text("var x = 1 < 2;")))
        assertTrue(s.renderMinified == "<script>var x = 1 < 2;</script>")
      },
      test("renderMinified for style element") {
        val s = Dom.Element.Style(Chunk.empty, Chunk(Dom.Text("div > p {}")))
        assertTrue(s.renderMinified == "<style>div > p {}</style>")
      },
      test("renderMinified for empty") {
        assertTrue(Dom.Empty.renderMinified == "")
      },
      test("renderMinified for void element") {
        assertTrue(Dom.Element.Generic("br", Chunk.empty, Chunk.empty).renderMinified == "<br/>")
      }
    ),
    suite("indentation rendering")(
      test("single text child stays inline") {
        val el = Dom.Element.Generic("p", Chunk.empty, Chunk(Dom.Text("Simple text")))
        assertTrue(el.render(indent = 2) == "<p>Simple text</p>")
      },
      test("multiple children get newlines and indentation") {
        val el = Dom.Element.Generic(
          "div",
          Chunk.empty,
          Chunk(
            Dom.Element.Generic("h1", Chunk.empty, Chunk(Dom.Text("Title"))),
            Dom.Element.Generic("p", Chunk.empty, Chunk(Dom.Text("Content")))
          )
        )
        val expected = "<div>\n  <h1>Title</h1>\n  <p>Content</p>\n</div>"
        assertTrue(el.render(indent = 2) == expected)
      },
      test("nested indentation") {
        val el = Dom.Element.Generic(
          "div",
          Chunk.empty,
          Chunk(
            Dom.Element.Generic(
              "ul",
              Chunk.empty,
              Chunk(
                Dom.Element.Generic("li", Chunk.empty, Chunk(Dom.Text("a"))),
                Dom.Element.Generic("li", Chunk.empty, Chunk(Dom.Text("b")))
              )
            )
          )
        )
        val expected = "<div>\n  <ul>\n    <li>a</li>\n    <li>b</li>\n  </ul>\n</div>"
        assertTrue(el.render(indent = 2) == expected)
      },
      test("indent=0 behaves like render") {
        val el = Dom.Element.Generic(
          "div",
          Chunk.empty,
          Chunk(
            Dom.Element.Generic("p", Chunk.empty, Chunk(Dom.Text("hi")))
          )
        )
        assertTrue(el.render(indent = 0) == el.render)
      },
      test("indented rendering of empty element") {
        val el = Dom.Element.Generic("div", Chunk.empty, Chunk.empty)
        assertTrue(el.render(indent = 2) == "<div></div>")
      },
      test("indented rendering with attributes") {
        val attr = Dom.Attribute.KeyValue("id", Dom.AttributeValue.StringValue("main"))
        val el   = Dom.Element.Generic(
          "div",
          Chunk(attr),
          Chunk(
            Dom.Element.Generic("p", Chunk.empty, Chunk(Dom.Text("hi"))),
            Dom.Element.Generic("p", Chunk.empty, Chunk(Dom.Text("there")))
          )
        )
        val result = el.render(indent = 2)
        assertTrue(
          result == "<div id=\"main\">\n  <p>hi</p>\n  <p>there</p>\n</div>"
        )
      },
      test("indented rendering of void elements") {
        val el = Dom.Element.Generic(
          "div",
          Chunk.empty,
          Chunk(
            Dom.Element.Generic("br", Chunk.empty, Chunk.empty),
            Dom.Element.Generic("hr", Chunk.empty, Chunk.empty)
          )
        )
        val result = el.render(indent = 2)
        assertTrue(result == "<div>\n  <br/>\n  <hr/>\n</div>")
      },
      test("indented rendering of script element") {
        val s = Dom.Element.Script(Chunk.empty, Chunk(Dom.Text("var x = 1 < 2;")))
        assertTrue(s.render(indent = 2) == "<script>var x = 1 < 2;</script>")
      },
      test("indented rendering of style element") {
        val s = Dom.Element.Style(Chunk.empty, Chunk(Dom.Text("p { color: red; }")))
        assertTrue(s.render(indent = 2) == "<style>p { color: red; }</style>")
      },
      test("indented rendering of Empty") {
        assertTrue(Dom.Empty.render(indent = 2) == "")
      },
      test("indented rendering of Text") {
        assertTrue(Dom.Text("hello").render(indent = 2) == "hello")
      },
      test("indented script with multiple children does not escape") {
        val s      = Dom.Element.Script(Chunk.empty, Chunk(Dom.Text("a < b;"), Dom.Text("c > d;")))
        val result = s.render(indent = 2)
        assertTrue(result == "<script>\n  a < b;\n  c > d;\n</script>")
      },
      test("indented style with multiple children does not escape") {
        val s      = Dom.Element.Style(Chunk.empty, Chunk(Dom.Text("a > b {}"), Dom.Text("c > d {}")))
        val result = s.render(indent = 2)
        assertTrue(result == "<style>\n  a > b {}\n  c > d {}\n</style>")
      },
      test("indented rendering of PreRendered") {
        assertTrue(Dom.PreRendered("<b>raw</b>").render(indent = 2) == "<b>raw</b>")
      }
    ),
    suite("boolAttr")(
      test("enabled=true renders attribute") {
        val attr = Dom.boolAttr("required")
        val el   = Dom.Element.Generic("input", Chunk(attr), Chunk.empty)
        assertTrue(el.render == "<input required/>")
      },
      test("enabled=false omits attribute") {
        val attr = Dom.boolAttr("required", enabled = false)
        val el   = Dom.Element.Generic("input", Chunk(attr), Chunk.empty)
        assertTrue(el.render == "<input/>")
      }
    ),
    suite("when/whenSome")(
      test("when true applies modifiers") {
        val el     = Dom.Element.Generic("div", Chunk.empty, Chunk.empty)
        val result = el.when(true)(
          Modifier.attributeToModifier(Dom.Attribute.KeyValue("id", Dom.AttributeValue.StringValue("main")))
        )
        assertTrue(result.render == "<div id=\"main\"></div>")
      },
      test("when false returns unchanged") {
        val el     = Dom.Element.Generic("div", Chunk.empty, Chunk.empty)
        val result = el.when(false)(
          Modifier.attributeToModifier(Dom.Attribute.KeyValue("id", Dom.AttributeValue.StringValue("main")))
        )
        assertTrue(result.render == "<div></div>")
      },
      test("whenSome with Some applies modifiers") {
        val el     = Dom.Element.Generic("div", Chunk.empty, Chunk.empty)
        val result = el.whenSome(Some("highlight")) { cls =>
          Seq(
            Modifier.attributeToModifier(Dom.Attribute.KeyValue("class", Dom.AttributeValue.StringValue(cls)))
          )
        }
        assertTrue(result.render == "<div class=\"highlight\"></div>")
      },
      test("whenSome with None returns unchanged") {
        val el     = Dom.Element.Generic("div", Chunk.empty, Chunk.empty)
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
          Chunk.empty,
          Chunk(
            Dom.Element.Generic("p", Chunk.empty, Chunk(Dom.Text("a"))),
            Dom.Element.Generic("span", Chunk.empty, Chunk(Dom.Text("b")))
          )
        )
        val found = tree.collect { case el: Dom.Element if el.tag == "p" => el }
        assertTrue(found.length == 1)
      },
      test("find locates first matching node") {
        val tree = Dom.Element.Generic(
          "div",
          Chunk.empty,
          Chunk(
            Dom.Element.Generic("p", Chunk.empty, Chunk(Dom.Text("first"))),
            Dom.Element.Generic("p", Chunk.empty, Chunk(Dom.Text("second")))
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
          Chunk.empty,
          Chunk(
            Dom.Element.Generic("p", Chunk.empty, Chunk(Dom.Text("keep"))),
            Dom.Element.Generic("span", Chunk.empty, Chunk(Dom.Text("remove")))
          )
        )
        val filtered = tree.filter {
          case el: Dom.Element => el.tag != "span"
          case _               => true
        }
        assertTrue(filtered.render == "<div><p>keep</p></div>")
      },
      test("filter returns Empty when root fails predicate") {
        val tree     = Dom.Element.Generic("div", Chunk.empty, Chunk(Dom.Text("hello")))
        val filtered = tree.filter(_ => false)
        assertTrue(filtered == Dom.Empty)
      },
      test("transform modifies nodes") {
        val tree        = Dom.Element.Generic("div", Chunk.empty, Chunk(Dom.Text("hello")))
        val transformed = tree.transform {
          case Dom.Text("hello") => Dom.Text("world")
          case other             => other
        }
        assertTrue(transformed.render == "<div>world</div>")
      },
      test("find returning None") {
        val tree  = Dom.Element.Generic("div", Chunk.empty, Chunk(Dom.Text("hello")))
        val found = tree.find {
          case el: Dom.Element => el.tag == "nonexistent"
          case _               => false
        }
        assertTrue(found.isEmpty)
      },
      test("transform on nested structures") {
        val tree = Dom.Element.Generic(
          "div",
          Chunk.empty,
          Chunk(
            Dom.Element.Generic("p", Chunk.empty, Chunk(Dom.Text("old"))),
            Dom.Element.Generic("span", Chunk.empty, Chunk(Dom.Text("old")))
          )
        )
        val transformed = tree.transform {
          case Dom.Text("old") => Dom.Text("new")
          case other           => other
        }
        assertTrue(transformed.render == "<div><p>new</p><span>new</span></div>")
      },
      test("isEmpty") {
        assertTrue(
          Dom.Empty.isEmpty,
          Dom.Text("").isEmpty,
          !Dom.Text("hi").isEmpty,
          !Dom.Element.Generic("div", Chunk.empty, Chunk.empty).isEmpty
        )
      },
      test("isEmpty for PreRendered") {
        assertTrue(
          Dom.PreRendered("").isEmpty,
          !Dom.PreRendered("<b>hi</b>").isEmpty
        )
      }
    ),
    suite("Dom.text and Dom.empty factories")(
      test("Dom.text creates Text node") {
        assertTrue(Dom.text("hello").render == "hello")
      },
      test("Dom.empty is Empty") {
        assertTrue(Dom.empty == Dom.Empty)
      }
    ),
    suite("Element apply for curried modifiers")(
      test("apply adds modifiers to existing element") {
        val el     = Dom.Element.Generic("div", Chunk.empty, Chunk.empty)
        val result = el(Modifier.stringToModifier("hello"))
        assertTrue(result.render == "<div>hello</div>")
      }
    ),
    suite("AttributeSeparator")(
      test("Space separator") {
        val attr = Dom.Attribute.KeyValue(
          "class",
          Dom.AttributeValue.MultiValue(Chunk("a", "b"), Dom.AttributeSeparator.Space)
        )
        val el = Dom.Element.Generic("div", Chunk(attr), Chunk.empty)
        assertTrue(el.render == "<div class=\"a b\"></div>")
      },
      test("Comma separator") {
        val attr = Dom.Attribute.KeyValue(
          "accept",
          Dom.AttributeValue.MultiValue(Chunk("text/html", "application/json"), Dom.AttributeSeparator.Comma)
        )
        val el = Dom.Element.Generic("input", Chunk(attr), Chunk.empty)
        assertTrue(el.render == "<input accept=\"text/html,application/json\"/>")
      },
      test("Semicolon separator") {
        val attr = Dom.Attribute.KeyValue(
          "style",
          Dom.AttributeValue.MultiValue(Chunk("color: red", "font-size: 14px"), Dom.AttributeSeparator.Semicolon)
        )
        val el = Dom.Element.Generic("div", Chunk(attr), Chunk.empty)
        assertTrue(el.render == "<div style=\"color: red;font-size: 14px\"></div>")
      },
      test("Custom separator") {
        val attr = Dom.Attribute.KeyValue(
          "data-tags",
          Dom.AttributeValue.MultiValue(Chunk("a", "b"), Dom.AttributeSeparator.Custom(" | "))
        )
        val el = Dom.Element.Generic("div", Chunk(attr), Chunk.empty)
        assertTrue(el.render == "<div data-tags=\"a | b\"></div>")
      }
    ),
    suite("multiAttr")(
      test("multiAttr factory creates attribute") {
        val attr = Dom.multiAttr("class", Dom.AttributeSeparator.Space, "foo", "bar")
        val el   = Dom.Element.Generic("div", Chunk(attr), Chunk.empty)
        assertTrue(el.render == "<div class=\"foo bar\"></div>")
      },
      test("multiAttr with Iterable") {
        val attr = Dom.multiAttr("class", List("a", "b", "c"))
        val el   = Dom.Element.Generic("div", Chunk(attr), Chunk.empty)
        assertTrue(el.render == "<div class=\"a b c\"></div>")
      },
      test("PartialMultiAttribute := varargs") {
        val cls  = new PartialMultiAttribute("class", Dom.AttributeSeparator.Space)
        val attr = cls.:=("foo", "bar", "baz")
        val el   = Dom.Element.Generic("div", Chunk(attr), Chunk.empty)
        assertTrue(el.render == "<div class=\"foo bar baz\"></div>")
      },
      test("PartialMultiAttribute apply") {
        val cls  = new PartialMultiAttribute("class", Dom.AttributeSeparator.Space)
        val attr = cls("foo", "bar")
        val el   = Dom.Element.Generic("div", Chunk(attr), Chunk.empty)
        assertTrue(el.render == "<div class=\"foo bar\"></div>")
      },
      test("PartialMultiAttribute := single string") {
        val cls  = new PartialMultiAttribute("class", Dom.AttributeSeparator.Space)
        val attr = cls := "single"
        val el   = Dom.Element.Generic("div", Chunk(attr), Chunk.empty)
        assertTrue(el.render == "<div class=\"single\"></div>")
      },
      test("PartialMultiAttribute := Chunk[String]") {
        val cls  = new PartialMultiAttribute("class", Dom.AttributeSeparator.Space)
        val attr = cls := Chunk("a", "b")
        val el   = Dom.Element.Generic("div", Chunk(attr), Chunk.empty)
        assertTrue(el.render == "<div class=\"a b\"></div>")
      },
      test("PartialMultiAttribute apply with Iterable") {
        val cls  = new PartialMultiAttribute("class", Dom.AttributeSeparator.Space)
        val attr = cls(List("x", "y"))
        val el   = Dom.Element.Generic("div", Chunk(attr), Chunk.empty)
        assertTrue(el.render == "<div class=\"x y\"></div>")
      },
      test("PartialMultiAttribute apply with single value") {
        val cls  = new PartialMultiAttribute("class", Dom.AttributeSeparator.Space)
        val attr = cls("single")
        val el   = Dom.Element.Generic("div", Chunk(attr), Chunk.empty)
        assertTrue(el.render == "<div class=\"single\"></div>")
      }
    ),
    suite("PartialAttribute")(
      test(":= with Long") {
        val pa   = new PartialAttribute("tabindex")
        val attr = pa := 100L
        val el   = Dom.Element.Generic("div", Chunk(attr), Chunk.empty)
        assertTrue(el.render == "<div tabindex=\"100\"></div>")
      },
      test(":= with Double") {
        val pa   = new PartialAttribute("data-ratio")
        val attr = pa := 3.14
        val el   = Dom.Element.Generic("div", Chunk(attr), Chunk.empty)
        assertTrue(el.render == "<div data-ratio=\"3.14\"></div>")
      },
      test(":= with Js") {
        val pa   = new PartialAttribute("onclick")
        val attr = pa := Js("alert('hi')")
        val el   = Dom.Element.Generic("button", Chunk(attr), Chunk.empty)
        assertTrue(el.render == "<button onclick=\"alert(&#x27;hi&#x27;)\"></button>")
      },
      test("withSeparator") {
        val pa   = new PartialAttribute("class")
        val attr = pa.withSeparator(Chunk("a", "b", "c"), Dom.AttributeSeparator.Comma)
        val el   = Dom.Element.Generic("div", Chunk(attr), Chunk.empty)
        assertTrue(el.render == "<div class=\"a,b,c\"></div>")
      }
    ),
    suite("PreRendered")(
      test("PreRendered renders raw HTML") {
        val pr = Dom.preRendered("<b>raw</b>")
        assertTrue(pr.render == "<b>raw</b>")
      },
      test("PreRendered isEmpty when empty string") {
        assertTrue(Dom.preRendered("").isEmpty)
      },
      test("PreRendered is not empty when non-empty") {
        assertTrue(!Dom.preRendered("x").isEmpty)
      },
      test("PreRendered in renderMinified") {
        assertTrue(Dom.preRendered("<i>italic</i>").renderMinified == "<i>italic</i>")
      },
      test("PreRendered in indented render") {
        assertTrue(Dom.preRendered("<i>x</i>").render(indent = 2) == "<i>x</i>")
      }
    ),
    suite("Element withAttributes and withChildren")(
      test("Generic withAttributes replaces attributes") {
        val el      = Dom.Element.Generic("div", Chunk.empty, Chunk.empty)
        val attr    = Dom.Attribute.KeyValue("id", Dom.AttributeValue.StringValue("x"))
        val updated = el.withAttributes(Chunk(attr))
        assertTrue(updated.render == "<div id=\"x\"></div>")
      },
      test("Generic withChildren replaces children") {
        val el      = Dom.Element.Generic("div", Chunk.empty, Chunk.empty)
        val updated = el.withChildren(Chunk(Dom.Text("hello")))
        assertTrue(updated.render == "<div>hello</div>")
      },
      test("Script withAttributes replaces attributes") {
        val s       = Dom.Element.Script(Chunk.empty, Chunk.empty)
        val attr    = Dom.Attribute.KeyValue("type", Dom.AttributeValue.StringValue("module"))
        val updated = s.withAttributes(Chunk(attr))
        assertTrue(updated.render == """<script type="module"></script>""")
      },
      test("Script withChildren replaces children") {
        val s       = Dom.Element.Script(Chunk.empty, Chunk.empty)
        val updated = s.withChildren(Chunk(Dom.Text("code")))
        assertTrue(updated.render == "<script>code</script>")
      },
      test("Style withAttributes replaces attributes") {
        val s       = Dom.Element.Style(Chunk.empty, Chunk.empty)
        val attr    = Dom.Attribute.KeyValue("media", Dom.AttributeValue.StringValue("print"))
        val updated = s.withAttributes(Chunk(attr))
        assertTrue(updated.render == """<style media="print"></style>""")
      },
      test("Style withChildren replaces children") {
        val s       = Dom.Element.Style(Chunk.empty, Chunk.empty)
        val updated = s.withChildren(Chunk(Dom.Text("body{}")))
        assertTrue(updated.render == "<style>body{}</style>")
      }
    ),
    suite("Element apply with multiple modifiers")(
      test("apply with single modifier") {
        val el     = Dom.Element.Generic("div", Chunk.empty, Chunk.empty)
        val result = el(Modifier.stringToModifier("text"))
        assertTrue(result.render == "<div>text</div>")
      },
      test("apply with multiple modifiers") {
        val el     = Dom.Element.Generic("div", Chunk.empty, Chunk.empty)
        val result = el(
          Modifier.attributeToModifier(Dom.Attribute.KeyValue("id", Dom.AttributeValue.StringValue("x"))),
          Modifier.stringToModifier("content")
        )
        assertTrue(result.render == """<div id="x">content</div>""")
      }
    ),
    suite("Attribute rendering additional variants")(
      test("MultiValue with Comma separator") {
        val attr = Dom.Attribute.KeyValue(
          "accept",
          Dom.AttributeValue.MultiValue(Chunk("text/html", "text/css"), Dom.AttributeSeparator.Comma)
        )
        val el = Dom.Element.Generic("input", Chunk(attr), Chunk.empty)
        assertTrue(el.render == "<input accept=\"text/html,text/css\"/>")
      },
      test("MultiValue with Semicolon separator") {
        val attr = Dom.Attribute.KeyValue(
          "style",
          Dom.AttributeValue.MultiValue(Chunk("color: red", "font-size: 12px"), Dom.AttributeSeparator.Semicolon)
        )
        val el = Dom.Element.Generic("div", Chunk(attr), Chunk.empty)
        assertTrue(el.render == "<div style=\"color: red;font-size: 12px\"></div>")
      },
      test("MultiValue with Custom separator") {
        val attr = Dom.Attribute.KeyValue(
          "data-list",
          Dom.AttributeValue.MultiValue(Chunk("x", "y", "z"), Dom.AttributeSeparator.Custom(" | "))
        )
        val el = Dom.Element.Generic("div", Chunk(attr), Chunk.empty)
        assertTrue(el.render == "<div data-list=\"x | y | z\"></div>")
      },
      test("MultiValue empty omits attribute entirely") {
        val attr = Dom.Attribute.KeyValue(
          "class",
          Dom.AttributeValue.MultiValue(Chunk.empty, Dom.AttributeSeparator.Comma)
        )
        val el = Dom.Element.Generic("div", Chunk(attr), Chunk.empty)
        assertTrue(el.render == "<div></div>")
      },
      test("JsValue with HTML special chars is properly escaped") {
        val attr = Dom.Attribute.KeyValue(
          "onclick",
          Dom.AttributeValue.JsValue(Js("if (a < b) alert(\"xss\")"))
        )
        val el = Dom.Element.Generic("button", Chunk(attr), Chunk.empty)
        assertTrue(
          el.render == """<button onclick="if (a &lt; b) alert(&quot;xss&quot;)"></button>"""
        )
      },
      test("BooleanValue(true) renders attribute name only") {
        val attr = Dom.Attribute.KeyValue("checked", Dom.AttributeValue.BooleanValue(true))
        val el   = Dom.Element.Generic("input", Chunk(attr), Chunk.empty)
        assertTrue(el.render == "<input checked/>")
      },
      test("BooleanValue(false) omits attribute") {
        val attr = Dom.Attribute.KeyValue("checked", Dom.AttributeValue.BooleanValue(false))
        val el   = Dom.Element.Generic("input", Chunk(attr), Chunk.empty)
        assertTrue(el.render == "<input/>")
      },
      test("StringValue with special chars is escaped") {
        val attr = Dom.Attribute.KeyValue("title", Dom.AttributeValue.StringValue("a<b&c"))
        val el   = Dom.Element.Generic("div", Chunk(attr), Chunk.empty)
        assertTrue(el.render == "<div title=\"a&lt;b&amp;c\"></div>")
      },
      test("Multiple attributes render in order") {
        val a1 = Dom.Attribute.KeyValue("id", Dom.AttributeValue.StringValue("x"))
        val a2 = Dom.Attribute.BooleanAttribute("disabled")
        val a3 = Dom.Attribute.KeyValue("class", Dom.AttributeValue.StringValue("y"))
        val el = Dom.Element.Generic("input", Chunk(a1, a2, a3), Chunk.empty)
        assertTrue(
          el.render == """<input id="x" disabled class="y"/>"""
        )
      }
    ),
    suite("PartialAttribute := variants")(
      test(":= with Int") {
        val pa   = new PartialAttribute("tabindex")
        val attr = pa := 5
        val el   = Dom.Element.Generic("div", Chunk(attr), Chunk.empty)
        assertTrue(el.render == "<div tabindex=\"5\"></div>")
      },
      test(":= with Boolean true creates BooleanValue") {
        val pa   = new PartialAttribute("disabled")
        val attr = pa := true
        val el   = Dom.Element.Generic("input", Chunk(attr), Chunk.empty)
        assertTrue(el.render == "<input disabled/>")
      },
      test(":= with Boolean false omits") {
        val pa   = new PartialAttribute("disabled")
        val attr = pa := false
        val el   = Dom.Element.Generic("input", Chunk(attr), Chunk.empty)
        assertTrue(el.render == "<input/>")
      },
      test(":= with Chunk[String] creates MultiValue") {
        val pa   = new PartialAttribute("class")
        val attr = pa := Chunk("x", "y")
        val el   = Dom.Element.Generic("div", Chunk(attr), Chunk.empty)
        assertTrue(el.render == "<div class=\"x y\"></div>")
      },
      test(":= with varargs creates MultiValue") {
        val pa   = new PartialAttribute("class")
        val attr = pa.:=("a", "b", "c")
        val el   = Dom.Element.Generic("div", Chunk(attr), Chunk.empty)
        assertTrue(el.render == "<div class=\"a b c\"></div>")
      },
      test("BooleanAttribute as modifier (applyTo)") {
        val ba      = Dom.Attribute.BooleanAttribute("required")
        val el      = Dom.Element.Generic("input", Chunk.empty, Chunk.empty)
        val updated = ba.applyTo(el)
        assertTrue(updated.render == "<input required/>")
      },
      test("BooleanAttribute disabled=false does not add attribute") {
        val ba      = Dom.Attribute.BooleanAttribute("disabled", enabled = false)
        val el      = Dom.Element.Generic("input", Chunk.empty, Chunk.empty)
        val updated = ba.applyTo(el)
        assertTrue(updated.render == "<input/>")
      }
    ),
    suite("indented rendering with PreRendered")(
      test("PreRendered inside element with indentation") {
        val el = Dom.Element.Generic(
          "div",
          Chunk.empty,
          Chunk(Dom.preRendered("<b>raw</b>"), Dom.Text("text"))
        )
        val result = el.render(indent = 2)
        assertTrue(result == "<div>\n  <b>raw</b>\n  text\n</div>")
      }
    ),
    suite("traversal on Empty and Text")(
      test("collect on Text") {
        val t     = Dom.Text("hello")
        val found = t.collect { case Dom.Text(c) => Dom.Text(c) }
        assertTrue(found.length == 1)
      },
      test("collect on Empty") {
        val found = Dom.Empty.collect { case Dom.Text(c) => Dom.Text(c) }
        assertTrue(found.isEmpty)
      },
      test("filter on Text returns itself if matching") {
        val t        = Dom.Text("hello")
        val filtered = t.filter(_ => true)
        assertTrue(filtered == t)
      },
      test("filter on Text returns Empty if not matching") {
        val t        = Dom.Text("hello")
        val filtered = t.filter(_ => false)
        assertTrue(filtered == Dom.Empty)
      },
      test("find on Text") {
        val t     = Dom.Text("hello")
        val found = t.find(_ => true)
        assertTrue(found.contains(t))
      },
      test("transform on Text") {
        val t           = Dom.Text("hello")
        val transformed = t.transform {
          case Dom.Text(_) => Dom.Text("world")
          case other       => other
        }
        assertTrue(transformed == Dom.Text("world"))
      },
      test("transform on Empty") {
        val transformed = Dom.Empty.transform {
          case Dom.Empty => Dom.Text("replaced")
          case other     => other
        }
        assertTrue(transformed == Dom.Text("replaced"))
      }
    )
  )
}
