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

package zio.blocks.template

import zio.blocks.chunk.Chunk
import zio.test._

object DomSelectionSpec extends ZIOSpecDefault {
  def spec = suite("DomSelection")(
    suite("select by tag")(
      test("finds elements matching tag name") {
        val dom    = div(p("hello"), p("world"), span("other"))
        val result = dom.select(CssSelector.Element("p"))
        assertTrue(result.length == 2) &&
        assertTrue(result.texts == Chunk("hello", "world"))
      },
      test("finds nested elements by tag") {
        val dom    = div(div(p("deep")), p("shallow"))
        val result = dom.select(CssSelector.Element("p"))
        assertTrue(result.length == 2)
      },
      test("returns empty when no match") {
        val dom    = div(p("hello"))
        val result = dom.select(CssSelector.Element("span"))
        assertTrue(result.isEmpty)
      }
    ),
    suite("select by class")(
      test("finds elements with matching class") {
        val dom    = div(div(className := "active", "one"), div("two"))
        val result = dom.select(CssSelector.Class("active"))
        assertTrue(result.length == 1) &&
        assertTrue(result.texts == Chunk("one"))
      },
      test("finds elements with multiple classes") {
        val dom    = div(div(className := "foo active bar", "match"), div(className := "other", "no"))
        val result = dom.select(CssSelector.Class("active"))
        assertTrue(result.length == 1) &&
        assertTrue(result.texts == Chunk("match"))
      },
      test("returns empty when class not present") {
        val dom    = div(div(className := "other", "no"))
        val result = dom.select(CssSelector.Class("active"))
        assertTrue(result.isEmpty)
      }
    ),
    suite("select by id")(
      test("finds element with matching id") {
        val dom    = div(div(id := "main", "content"), div(id := "other", "not"))
        val result = dom.select(CssSelector.Id("main"))
        assertTrue(result.length == 1) &&
        assertTrue(result.texts == Chunk("content"))
      },
      test("returns empty when id not found") {
        val dom    = div(div(id := "other", "x"))
        val result = dom.select(CssSelector.Id("missing"))
        assertTrue(result.isEmpty)
      }
    ),
    suite("select Universal")(
      test("matches all element descendants") {
        val dom    = div(p("a"), span("b"))
        val result = dom.select(CssSelector.Universal)
        assertTrue(result.length == 2)
      }
    ),
    suite("select And")(
      test("matches elements satisfying both selectors") {
        val dom = div(
          div(className := "active", id := "main", "match"),
          div(className := "active", "nomatch"),
          div(id        := "main", "nomatch2")
        )
        val result = dom.select(CssSelector.And(CssSelector.Class("active"), CssSelector.Id("main")))
        assertTrue(result.length == 1) &&
        assertTrue(result.texts == Chunk("match"))
      }
    ),
    suite("select Or")(
      test("matches elements satisfying either selector") {
        val dom = div(
          p("para"),
          span("span-text"),
          div("div-text")
        )
        val result = dom.select(CssSelector.Or(CssSelector.Element("p"), CssSelector.Element("span")))
        assertTrue(result.length == 2) &&
        assertTrue(result.texts == Chunk("para", "span-text"))
      }
    ),
    suite("select Not")(
      test("matches elements not matching the negated selector") {
        val dom = div(
          p("para"),
          span("span-text"),
          div("div-text")
        )
        val result = dom.select(CssSelector.Not(CssSelector.Universal, CssSelector.Element("p")))
        assertTrue(result.length == 2) &&
        assertTrue(result.texts == Chunk("span-text", "div-text"))
      }
    ),
    suite("select Attribute")(
      test("matches elements with attribute present") {
        val dom = div(
          div(attr("data-value") := "x", "has"),
          div("no")
        )
        val result = dom.select(CssSelector.Attribute(CssSelector.Universal, "data-value", None))
        assertTrue(result.length == 1) &&
        assertTrue(result.texts == Chunk("has"))
      },
      test("matches elements with attribute exact value") {
        val dom = div(
          div(attr("data-value") := "x", "match"),
          div(attr("data-value") := "y", "no")
        )
        val result = dom.select(
          CssSelector.Attribute(CssSelector.Universal, "data-value", Some(CssSelector.AttributeMatch.Exact("x")))
        )
        assertTrue(result.length == 1) &&
        assertTrue(result.texts == Chunk("match"))
      },
      test("matches elements with attribute contains value") {
        val dom = div(
          div(attr("data-info") := "hello-world", "match"),
          div(attr("data-info") := "other", "no")
        )
        val result = dom.select(
          CssSelector.Attribute(CssSelector.Universal, "data-info", Some(CssSelector.AttributeMatch.Contains("world")))
        )
        assertTrue(result.length == 1)
      },
      test("matches elements with attribute starts with") {
        val dom = div(
          div(attr("data-info") := "hello-world", "match"),
          div(attr("data-info") := "other", "no")
        )
        val result = dom.select(
          CssSelector
            .Attribute(CssSelector.Universal, "data-info", Some(CssSelector.AttributeMatch.StartsWith("hello")))
        )
        assertTrue(result.length == 1)
      },
      test("matches elements with attribute ends with") {
        val dom = div(
          div(attr("data-info") := "hello-world", "match"),
          div(attr("data-info") := "other", "no")
        )
        val result = dom.select(
          CssSelector
            .Attribute(CssSelector.Universal, "data-info", Some(CssSelector.AttributeMatch.EndsWith("world")))
        )
        assertTrue(result.length == 1)
      },
      test("matches elements with attribute whitespace contains") {
        val dom = div(
          div(attr("data-tags") := "foo bar baz", "match"),
          div(attr("data-tags") := "foobarbaz", "no")
        )
        val result = dom.select(
          CssSelector.Attribute(
            CssSelector.Universal,
            "data-tags",
            Some(CssSelector.AttributeMatch.WhitespaceContains("bar"))
          )
        )
        assertTrue(result.length == 1)
      },
      test("matches elements with attribute hyphen prefix") {
        val dom = div(
          div(attr("lang") := "en", "exact"),
          div(attr("lang") := "en-US", "prefix"),
          div(attr("lang") := "fr", "no")
        )
        val result = dom.select(
          CssSelector
            .Attribute(CssSelector.Universal, "lang", Some(CssSelector.AttributeMatch.HyphenPrefix("en")))
        )
        assertTrue(result.length == 2)
      }
    ),
    suite("select Child combinator")(
      test("finds direct children matching child selector under parent selector") {
        val dom    = div(ul(li("a"), li("b")), ol(li("c")))
        val result = dom.select(CssSelector.Child(CssSelector.Element("ul"), CssSelector.Element("li")))
        assertTrue(result.length == 2) &&
        assertTrue(result.texts == Chunk("a", "b"))
      }
    ),
    suite("select Descendant combinator")(
      test("finds all descendants matching selector under ancestor selector") {
        val dom = div(
          div(className := "container", p("inside"), div(p("deep")))
        )
        val result =
          dom.select(CssSelector.Descendant(CssSelector.Class("container"), CssSelector.Element("p")))
        assertTrue(result.length == 2) &&
        assertTrue(result.texts == Chunk("inside", "deep"))
      }
    ),
    suite("chained selection")(
      test("selects children then selects within them") {
        val dom    = div(ul(li("a"), li("b")), ol(li("c")))
        val result = dom.select(CssSelector.Element("ul")).children
        assertTrue(result.length == 2) &&
        assertTrue(result.texts == Chunk("a", "b"))
      },
      test("selects then filters by tag") {
        val dom    = div(p("a"), span("b"), p("c"))
        val result = dom.select(CssSelector.Universal).withTag("p")
        assertTrue(result.length == 2)
      }
    ),
    suite("children")(
      test("returns direct children of all elements") {
        val dom = DomSelection.fromChunk(Chunk(div(p("a"), span("b")), div(p("c"))))
        assertTrue(dom.children.length == 3)
      },
      test("skips non-element nodes") {
        val dom = DomSelection.fromChunk(Chunk(Dom.Text("text"), div(p("a"))))
        assertTrue(dom.children.length == 1)
      }
    ),
    suite("descendants")(
      test("returns all descendants recursively") {
        val dom    = div(div(p("deep")))
        val result = DomSelection.single(dom).descendants
        assertTrue(result.length == 3)
      }
    ),
    suite("first and last")(
      test("first returns the first node") {
        val sel = DomSelection.fromChunk(Chunk(Dom.Text("a"), Dom.Text("b"), Dom.Text("c")))
        assertTrue(sel.first.texts == Chunk("a"))
      },
      test("last returns the last node") {
        val sel = DomSelection.fromChunk(Chunk(Dom.Text("a"), Dom.Text("b"), Dom.Text("c")))
        assertTrue(sel.last.texts == Chunk("c"))
      },
      test("first on empty returns empty") {
        assertTrue(DomSelection.empty.first.isEmpty)
      },
      test("last on empty returns empty") {
        assertTrue(DomSelection.empty.last.isEmpty)
      }
    ),
    suite("apply")(
      test("returns node at index") {
        val sel = DomSelection.fromChunk(Chunk(Dom.Text("a"), Dom.Text("b"), Dom.Text("c")))
        assertTrue(sel(1).texts == Chunk("b"))
      },
      test("returns empty for out-of-bounds index") {
        val sel = DomSelection.fromChunk(Chunk(Dom.Text("a")))
        assertTrue(sel(5).isEmpty) &&
        assertTrue(sel(-1).isEmpty)
      }
    ),
    suite("elements")(
      test("filters to element nodes only") {
        val sel = DomSelection.fromChunk(Chunk(div("a"), Dom.Text("b"), span("c")))
        assertTrue(sel.elements.length == 2)
      }
    ),
    suite("texts")(
      test("extracts text content from mixed nodes") {
        val dom    = div(p("hello"), p("world"))
        val result = dom.select(CssSelector.Element("p")).texts
        assertTrue(result == Chunk("hello", "world"))
      },
      test("concatenates nested text content for elements") {
        val dom    = div(p(span("nested"), " text"))
        val result = dom.select(CssSelector.Element("p")).texts
        assertTrue(result == Chunk("nested text"))
      },
      test("handles Text nodes directly") {
        val sel = DomSelection.fromChunk(Chunk(Dom.Text("raw")))
        assertTrue(sel.texts == Chunk("raw"))
      }
    ),
    suite("attrs")(
      test("extracts attribute values by name") {
        val dom    = div(div(id := "a"), div(id := "b"), div("no-id"))
        val result = dom.select(CssSelector.Universal).attrs("id")
        assertTrue(result == Chunk("a", "b"))
      }
    ),
    suite("filter")(
      test("filters by predicate") {
        val sel    = DomSelection.fromChunk(Chunk(div("a"), span("b"), p("c")))
        val result = sel.filter(_.isInstanceOf[Dom.Element])
        assertTrue(result.length == 3)
      }
    ),
    suite("withTag")(
      test("keeps only elements with matching tag") {
        val sel    = DomSelection.fromChunk(Chunk(div("a"), span("b"), div("c")))
        val result = sel.withTag("div")
        assertTrue(result.length == 2)
      }
    ),
    suite("withClass")(
      test("keeps only elements with matching class") {
        val sel = DomSelection.fromChunk(
          Chunk(
            div(className := "active", "a"),
            div(className := "other", "b"),
            div(className := "active extra", "c")
          )
        )
        val result = sel.withClass("active")
        assertTrue(result.length == 2) &&
        assertTrue(result.texts == Chunk("a", "c"))
      }
    ),
    suite("withId")(
      test("keeps only elements with matching id") {
        val sel = DomSelection.fromChunk(
          Chunk(
            div(id := "main", "a"),
            div(id := "other", "b")
          )
        )
        val result = sel.withId("main")
        assertTrue(result.length == 1)
      }
    ),
    suite("withAttribute")(
      test("keeps elements with attribute present") {
        val sel = DomSelection.fromChunk(
          Chunk(
            div(attr("data-x") := "1", "a"),
            div("b")
          )
        )
        assertTrue(sel.withAttribute("data-x").length == 1)
      },
      test("keeps elements with attribute matching value") {
        val sel = DomSelection.fromChunk(
          Chunk(
            div(attr("data-x") := "1", "a"),
            div(attr("data-x") := "2", "b")
          )
        )
        assertTrue(sel.withAttribute("data-x", "1").length == 1)
      }
    ),
    suite("modifyAll")(
      test("transforms matched elements") {
        val dom      = div(p("hello"), p("world"))
        val selected = dom.select(CssSelector.Element("p"))
        val modified = selected.modifyAll(_.withChildren(Chunk(Dom.Text("changed"))))
        assertTrue(modified.texts == Chunk("changed", "changed"))
      },
      test("preserves non-element nodes") {
        val sel      = DomSelection.fromChunk(Chunk(Dom.Text("keep"), p("change")))
        val modified = sel.modifyAll(_.withChildren(Chunk(Dom.Text("new"))))
        assertTrue(modified.length == 2)
      }
    ),
    suite("replaceAll")(
      test("replaces all nodes with the replacement") {
        val sel      = DomSelection.fromChunk(Chunk(div("a"), span("b")))
        val replaced = sel.replaceAll(Dom.Text("replaced"))
        assertTrue(replaced.length == 2) &&
        assertTrue(replaced.texts == Chunk("replaced", "replaced"))
      }
    ),
    suite("removeAll")(
      test("returns empty selection") {
        val sel = DomSelection.fromChunk(Chunk(div("a"), span("b")))
        assertTrue(sel.removeAll.isEmpty)
      }
    ),
    suite("size operations")(
      test("isEmpty on empty selection") {
        assertTrue(DomSelection.empty.isEmpty)
      },
      test("nonEmpty on non-empty selection") {
        assertTrue(DomSelection.single(div("a")).nonEmpty)
      },
      test("length returns count") {
        val sel = DomSelection.fromChunk(Chunk(div("a"), div("b")))
        assertTrue(sel.length == 2)
      }
    ),
    suite("toChunk and headOption")(
      test("toChunk returns underlying nodes") {
        val nodes = Chunk[Dom](div("a"), div("b"))
        assertTrue(DomSelection.fromChunk(nodes).toChunk == nodes)
      },
      test("headOption returns first node") {
        val sel = DomSelection.fromChunk(Chunk(div("a"), div("b")))
        assertTrue(sel.headOption == Some(div("a")))
      },
      test("headOption returns None on empty") {
        assertTrue(DomSelection.empty.headOption.isEmpty)
      }
    ),
    suite("concatenation")(
      test("combines two selections") {
        val a = DomSelection.fromChunk(Chunk(div("a")))
        val b = DomSelection.fromChunk(Chunk(span("b")))
        assertTrue((a ++ b).length == 2)
      }
    ),
    suite("Dom#select integration")(
      test("select on Dom directly") {
        val dom    = div(p("hello"), span("world"), p("again"))
        val result = dom.select(CssSelector.Element("p"))
        assertTrue(result.length == 2) &&
        assertTrue(result.texts == Chunk("hello", "again"))
      }
    ),
    suite("selectorMatches fallthrough branches")(
      test("PseudoElement matches inner selector") {
        val dom    = div(p("a"))
        val result = dom.select(CssSelector.PseudoElement(CssSelector.Element("p"), "before"))
        assertTrue(result.length == 1) &&
        assertTrue(result.texts == Chunk("a"))
      },
      test("Raw always returns false") {
        val dom    = div(p("a"), span("b"))
        val result = dom.select(CssSelector.Raw(".foo"))
        assertTrue(result.isEmpty)
      },
      test("PseudoClass matches inner selector") {
        val dom    = div(p("a"))
        val result = dom.select(CssSelector.PseudoClass(CssSelector.Element("p"), "hover"))
        assertTrue(result.length == 1)
      },
      test("AdjacentSibling returns false from selectorMatches") {
        val dom    = div(p("a"), span("b"))
        val result = dom.select(CssSelector.AdjacentSibling(CssSelector.Element("p"), CssSelector.Element("span")))
        assertTrue(result.isEmpty)
      },
      test("GeneralSibling returns false from selectorMatches") {
        val dom    = div(p("a"), span("b"))
        val result = dom.select(CssSelector.GeneralSibling(CssSelector.Element("p"), CssSelector.Element("span")))
        assertTrue(result.isEmpty)
      },
      test("Child selector returns false from selectorMatches for simple select") {
        val el = Dom.Element.Generic("div", Chunk.empty, Chunk.empty)
        assertTrue(
          !DomSelection.selectorMatches(el, CssSelector.Child(CssSelector.Element("p"), CssSelector.Element("span")))
        )
      },
      test("Descendant selector returns false from selectorMatches") {
        val el = Dom.Element.Generic("div", Chunk.empty, Chunk.empty)
        assertTrue(
          !DomSelection
            .selectorMatches(el, CssSelector.Descendant(CssSelector.Element("p"), CssSelector.Element("span")))
        )
      }
    ),
    suite("collectDescendants with non-element root")(
      test("non-element nodes are skipped in collectDescendants") {
        val sel    = DomSelection.fromChunk(Chunk(Dom.Text("text")))
        val result = sel.select(CssSelector.Element("p"))
        assertTrue(result.isEmpty)
      },
      test("select on Empty node") {
        val sel    = DomSelection.fromChunk(Chunk(Dom.Empty))
        val result = sel.select(CssSelector.Element("p"))
        assertTrue(result.isEmpty)
      }
    ),
    suite("descendants with non-element nodes")(
      test("descendants skips non-element nodes") {
        val sel    = DomSelection.fromChunk(Chunk(Dom.Text("text"), Dom.Empty))
        val result = sel.descendants
        assertTrue(result.isEmpty)
      }
    ),
    suite("texts with Empty nodes")(
      test("texts skips Empty nodes") {
        val sel    = DomSelection.fromChunk(Chunk(Dom.Text("hello"), Dom.Empty, Dom.Text("world")))
        val result = sel.texts
        assertTrue(result == Chunk("hello", "world"))
      }
    ),
    suite("attrs with non-element nodes")(
      test("attrs skips non-element and no-attribute nodes") {
        val sel = DomSelection.fromChunk(
          Chunk(Dom.Text("text"), div(id := "x"), Dom.Empty)
        )
        val result = sel.attrs("id")
        assertTrue(result == Chunk("x"))
      },
      test("attrs skips element without the attribute") {
        val sel = DomSelection.fromChunk(
          Chunk(div(className := "a"), div(id := "b"))
        )
        val result = sel.attrs("id")
        assertTrue(result == Chunk("b"))
      }
    ),
    suite("filter predicates with non-element nodes")(
      test("withTag returns false for Text nodes") {
        val sel    = DomSelection.fromChunk(Chunk(Dom.Text("text"), div("a")))
        val result = sel.withTag("div")
        assertTrue(result.length == 1)
      },
      test("withClass returns false for Text nodes") {
        val sel    = DomSelection.fromChunk(Chunk(Dom.Text("text"), div(className := "active", "a")))
        val result = sel.withClass("active")
        assertTrue(result.length == 1)
      },
      test("withId returns false for Text nodes") {
        val sel    = DomSelection.fromChunk(Chunk(Dom.Text("text"), div(id := "main", "a")))
        val result = sel.withId("main")
        assertTrue(result.length == 1)
      },
      test("withAttribute returns false for Text nodes") {
        val sel    = DomSelection.fromChunk(Chunk(Dom.Text("text"), div(attr("data-x") := "1", "a")))
        val result = sel.withAttribute("data-x")
        assertTrue(result.length == 1)
      },
      test("withAttribute(name, value) returns false for Text nodes") {
        val sel    = DomSelection.fromChunk(Chunk(Dom.Text("text"), div(attr("data-x") := "1", "a")))
        val result = sel.withAttribute("data-x", "1")
        assertTrue(result.length == 1)
      }
    ),
    suite("matchesAttribute when attr not present")(
      test("Attribute selector with Exact match returns false when attr missing") {
        val dom = div(
          div("no-attr"),
          div(attr("data-x") := "y", "has-attr")
        )
        val result = dom.select(
          CssSelector.Attribute(CssSelector.Universal, "data-x", Some(CssSelector.AttributeMatch.Exact("y")))
        )
        assertTrue(result.length == 1)
      }
    ),
    suite("selectChild with non-element nodes")(
      test("Child combinator skips non-element parent nodes") {
        val sel    = DomSelection.fromChunk(Chunk(Dom.Text("ignore"), div(p("child"))))
        val result = sel.select(CssSelector.Child(CssSelector.Element("div"), CssSelector.Element("p")))
        assertTrue(result.length == 1) &&
        assertTrue(result.texts == Chunk("child"))
      },
      test("Child combinator skips non-element children") {
        val dom    = div(Dom.Text("text-child"), p("element-child"))
        val result = dom.select(CssSelector.Child(CssSelector.Element("div"), CssSelector.Element("p")))
        assertTrue(result.length == 1)
      }
    ),
    suite("extractText with Empty and nested")(
      test("extractText skips Empty children") {
        val el     = Dom.Element.Generic("div", Chunk.empty, Chunk(Dom.Text("a"), Dom.Empty, Dom.Text("b")))
        val result = DomSelection.extractText(el)
        assertTrue(result == "ab")
      }
    ),
    suite("hasClass with MultiValue and AppendValue")(
      test("hasClass matches MultiValue class attribute") {
        val el = Dom.Element.Generic(
          "div",
          Chunk(
            Dom.Attribute
              .KeyValue("class", Dom.AttributeValue.MultiValue(Chunk("foo", "bar"), Dom.AttributeSeparator.Space))
          ),
          Chunk.empty
        )
        assertTrue(DomSelection.hasClass(el, "bar"))
      },
      test("hasClass returns false for non-matching MultiValue") {
        val el = Dom.Element.Generic(
          "div",
          Chunk(
            Dom.Attribute
              .KeyValue("class", Dom.AttributeValue.MultiValue(Chunk("foo", "bar"), Dom.AttributeSeparator.Space))
          ),
          Chunk.empty
        )
        assertTrue(!DomSelection.hasClass(el, "baz"))
      },
      test("hasClass matches AppendValue with StringValue") {
        val el = Dom.Element.Generic(
          "div",
          Chunk(
            Dom.Attribute
              .AppendValue("class", Dom.AttributeValue.StringValue("active extra"), Dom.AttributeSeparator.Space)
          ),
          Chunk.empty
        )
        assertTrue(DomSelection.hasClass(el, "active"))
      },
      test("hasClass AppendValue StringValue returns false when not matching") {
        val el = Dom.Element.Generic(
          "div",
          Chunk(
            Dom.Attribute.AppendValue("class", Dom.AttributeValue.StringValue("other"), Dom.AttributeSeparator.Space)
          ),
          Chunk.empty
        )
        assertTrue(!DomSelection.hasClass(el, "active"))
      },
      test("hasClass matches AppendValue with MultiValue") {
        val el = Dom.Element.Generic(
          "div",
          Chunk(
            Dom.Attribute.AppendValue(
              "class",
              Dom.AttributeValue.MultiValue(Chunk("foo", "active"), Dom.AttributeSeparator.Space),
              Dom.AttributeSeparator.Space
            )
          ),
          Chunk.empty
        )
        assertTrue(DomSelection.hasClass(el, "active"))
      },
      test("hasClass AppendValue MultiValue returns false when not matching") {
        val el = Dom.Element.Generic(
          "div",
          Chunk(
            Dom.Attribute.AppendValue(
              "class",
              Dom.AttributeValue.MultiValue(Chunk("foo", "bar"), Dom.AttributeSeparator.Space),
              Dom.AttributeSeparator.Space
            )
          ),
          Chunk.empty
        )
        assertTrue(!DomSelection.hasClass(el, "active"))
      },
      test("hasClass returns false when no class attribute present") {
        val el = Dom.Element.Generic(
          "div",
          Chunk(Dom.Attribute.KeyValue("id", Dom.AttributeValue.StringValue("main"))),
          Chunk.empty
        )
        assertTrue(!DomSelection.hasClass(el, "active"))
      }
    ),
    suite("splitContains edge cases")(
      test("splitContains returns false for empty target") {
        val el = Dom.Element.Generic(
          "div",
          Chunk(Dom.Attribute.KeyValue("class", Dom.AttributeValue.StringValue("active"))),
          Chunk.empty
        )
        assertTrue(!DomSelection.hasClass(el, ""))
      },
      test("splitContains returns false for empty attribute value") {
        val el = Dom.Element.Generic(
          "div",
          Chunk(Dom.Attribute.KeyValue("class", Dom.AttributeValue.StringValue(""))),
          Chunk.empty
        )
        assertTrue(!DomSelection.hasClass(el, "active"))
      }
    ),
    suite("hasAttribute with BooleanAttribute and AppendValue")(
      test("hasAttribute matches BooleanAttribute when enabled") {
        val el = Dom.Element.Generic(
          "div",
          Chunk(Dom.Attribute.BooleanAttribute("disabled", true)),
          Chunk.empty
        )
        assertTrue(DomSelection.hasAttribute(el, "disabled"))
      },
      test("hasAttribute returns false for BooleanAttribute when disabled") {
        val el = Dom.Element.Generic(
          "div",
          Chunk(Dom.Attribute.BooleanAttribute("disabled", false)),
          Chunk.empty
        )
        assertTrue(!DomSelection.hasAttribute(el, "disabled"))
      },
      test("hasAttribute matches AppendValue by name") {
        val el = Dom.Element.Generic(
          "div",
          Chunk(
            Dom.Attribute.AppendValue("class", Dom.AttributeValue.StringValue("active"), Dom.AttributeSeparator.Space)
          ),
          Chunk.empty
        )
        assertTrue(DomSelection.hasAttribute(el, "class"))
      },
      test("hasAttribute returns false when attribute not present") {
        val el = Dom.Element.Generic(
          "div",
          Chunk(Dom.Attribute.KeyValue("id", Dom.AttributeValue.StringValue("main"))),
          Chunk.empty
        )
        assertTrue(!DomSelection.hasAttribute(el, "class"))
      }
    ),
    suite("getAttributeValue with MultiValue, BooleanValue, JsValue")(
      test("getAttributeValue reads MultiValue with separator") {
        val el = Dom.Element.Generic(
          "div",
          Chunk(
            Dom.Attribute
              .KeyValue("class", Dom.AttributeValue.MultiValue(Chunk("a", "b", "c"), Dom.AttributeSeparator.Space))
          ),
          Chunk.empty
        )
        assertTrue(DomSelection.getAttributeValue(el, "class") == Some("a b c"))
      },
      test("getAttributeValue reads MultiValue with comma separator") {
        val el = Dom.Element.Generic(
          "div",
          Chunk(
            Dom.Attribute.KeyValue(
              "accept",
              Dom.AttributeValue.MultiValue(Chunk("text/html", "text/plain"), Dom.AttributeSeparator.Comma)
            )
          ),
          Chunk.empty
        )
        assertTrue(DomSelection.getAttributeValue(el, "accept") == Some("text/html,text/plain"))
      },
      test("getAttributeValue reads BooleanValue") {
        val el = Dom.Element.Generic(
          "div",
          Chunk(Dom.Attribute.KeyValue("data-active", Dom.AttributeValue.BooleanValue(true))),
          Chunk.empty
        )
        assertTrue(DomSelection.getAttributeValue(el, "data-active") == Some("true"))
      },
      test("getAttributeValue reads JsValue") {
        val el = Dom.Element.Generic(
          "div",
          Chunk(Dom.Attribute.KeyValue("onclick", Dom.AttributeValue.JsValue(Js("alert('hi')")))),
          Chunk.empty
        )
        assertTrue(DomSelection.getAttributeValue(el, "onclick") == Some("alert('hi')"))
      },
      test("getAttributeValue returns None when not found") {
        val el = Dom.Element.Generic(
          "div",
          Chunk(Dom.Attribute.KeyValue("id", Dom.AttributeValue.StringValue("x"))),
          Chunk.empty
        )
        assertTrue(DomSelection.getAttributeValue(el, "class") == None)
      },
      test("getAttributeValue skips non-matching KeyValue entries") {
        val el = Dom.Element.Generic(
          "div",
          Chunk(
            Dom.Attribute.KeyValue("id", Dom.AttributeValue.StringValue("x")),
            Dom.Attribute.KeyValue("class", Dom.AttributeValue.StringValue("y"))
          ),
          Chunk.empty
        )
        assertTrue(DomSelection.getAttributeValue(el, "class") == Some("y"))
      },
      test("getAttributeValue skips AppendValue and BooleanAttribute") {
        val el = Dom.Element.Generic(
          "div",
          Chunk(
            Dom.Attribute.AppendValue("class", Dom.AttributeValue.StringValue("a"), Dom.AttributeSeparator.Space),
            Dom.Attribute.BooleanAttribute("disabled", true),
            Dom.Attribute.KeyValue("id", Dom.AttributeValue.StringValue("main"))
          ),
          Chunk.empty
        )
        assertTrue(DomSelection.getAttributeValue(el, "id") == Some("main"))
      }
    ),
    suite("DomSelection.select class method")(
      test("select from root Dom") {
        val root   = div(p("hello"), span("world"))
        val result = DomSelection.select(root, CssSelector.Element("p"))
        assertTrue(result.length == 1) &&
        assertTrue(result.texts == Chunk("hello"))
      }
    ),
    suite("hasAttributeValue")(
      test("hasAttributeValue returns true when value matches") {
        val el = Dom.Element.Generic(
          "div",
          Chunk(Dom.Attribute.KeyValue("id", Dom.AttributeValue.StringValue("main"))),
          Chunk.empty
        )
        assertTrue(DomSelection.hasAttributeValue(el, "id", "main"))
      },
      test("hasAttributeValue returns false when value differs") {
        val el = Dom.Element.Generic(
          "div",
          Chunk(Dom.Attribute.KeyValue("id", Dom.AttributeValue.StringValue("other"))),
          Chunk.empty
        )
        assertTrue(!DomSelection.hasAttributeValue(el, "id", "main"))
      },
      test("hasAttributeValue returns false when attr missing") {
        val el = Dom.Element.Generic("div", Chunk.empty, Chunk.empty)
        assertTrue(!DomSelection.hasAttributeValue(el, "id", "main"))
      }
    ),
    suite("modifyAll with non-element")(
      test("modifyAll preserves Text nodes unchanged") {
        val sel      = DomSelection.fromChunk(Chunk(Dom.Text("keep"), Dom.Empty))
        val modified = sel.modifyAll(_.withChildren(Chunk(Dom.Text("new"))))
        assertTrue(modified.length == 2) &&
        assertTrue(modified.toChunk(0) == Dom.Text("keep")) &&
        assertTrue(modified.toChunk(1) == Dom.Empty)
      }
    )
  )
}
