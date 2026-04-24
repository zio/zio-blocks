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

package zio.blocks.html

import zio.test._

object CssSelectorSpec extends ZIOSpecDefault {
  def spec = suite("CssSelector")(
    suite("basic selectors")(
      test("Element selector renders tag name") {
        assertTrue(CssSelector.Element("div").render == "div")
      },
      test("Class selector renders with dot prefix") {
        assertTrue(CssSelector.Class("active").render == ".active")
      },
      test("Id selector renders with hash prefix") {
        assertTrue(CssSelector.Id("main").render == "#main")
      },
      test("Universal selector renders as *") {
        assertTrue(CssSelector.Universal.render == "*")
      },
      test("Raw selector renders verbatim") {
        assertTrue(CssSelector.Raw("div.class#id").render == "div.class#id")
      }
    ),
    suite("factory methods")(
      test("CssSelector.element creates Element") {
        assertTrue(CssSelector.element("span") == CssSelector.Element("span"))
      },
      test("CssSelector.class creates Class") {
        assertTrue(CssSelector.`class`("active") == CssSelector.Class("active"))
      },
      test("CssSelector.id creates Id") {
        assertTrue(CssSelector.id("main") == CssSelector.Id("main"))
      },
      test("CssSelector.universal returns Universal") {
        assertTrue(CssSelector.universal == CssSelector.Universal)
      },
      test("CssSelector.raw creates Raw") {
        assertTrue(CssSelector.raw("custom") == CssSelector.Raw("custom"))
      }
    ),
    suite("combinators")(
      test("child combinator > renders correctly") {
        val result = CssSelector.Element("div").child(CssSelector.Element("p"))
        assertTrue(result.render == "div > p")
      },
      test("descendant combinator >> renders correctly") {
        val parent     = CssSelector.Element("div")
        val descendant = CssSelector.Element("span")
        assertTrue((parent >> descendant).render == "div span")
      },
      test("adjacent sibling combinator + renders correctly") {
        val a = CssSelector.Element("h1")
        val b = CssSelector.Element("p")
        assertTrue((a + b).render == "h1 + p")
      },
      test("general sibling combinator ~ renders correctly") {
        val a = CssSelector.Element("h1")
        val b = CssSelector.Element("p")
        assertTrue((a ~ b).render == "h1 ~ p")
      },
      test("and combinator & renders without space") {
        val el  = CssSelector.Element("div")
        val cls = CssSelector.Class("active")
        assertTrue((el & cls).render == "div.active")
      },
      test("or combinator | renders with comma") {
        val a = CssSelector.Element("div")
        val b = CssSelector.Element("p")
        assertTrue((a | b).render == "div, p")
      }
    ),
    suite("pseudo-classes")(
      test("hover pseudo-class") {
        assertTrue(CssSelector.Element("a").hover.render == "a:hover")
      },
      test("firstChild pseudo-class") {
        assertTrue(CssSelector.Element("li").firstChild.render == "li:first-child")
      },
      test("lastChild pseudo-class") {
        assertTrue(CssSelector.Element("li").lastChild.render == "li:last-child")
      },
      test("focus pseudo-class") {
        assertTrue(CssSelector.Element("input").focus.render == "input:focus")
      },
      test("active pseudo-class") {
        assertTrue(CssSelector.Element("button").active.render == "button:active")
      },
      test("nthChild with Int") {
        assertTrue(CssSelector.Element("li").nthChild(3).render == "li:nth-child(3)")
      },
      test("nthChild with formula") {
        assertTrue(CssSelector.Element("tr").nthChild("2n+1").render == "tr:nth-child(2n+1)")
      },
      test("visited pseudo-class") {
        assertTrue(CssSelector.Element("a").visited.render == "a:visited")
      },
      test("not pseudo-class") {
        val sel = CssSelector.Element("div")
        val neg = CssSelector.Class("hidden")
        assertTrue(sel.not(neg).render == "div:not(.hidden)")
      }
    ),
    suite("pseudo-elements")(
      test("before pseudo-element") {
        assertTrue(CssSelector.Element("p").before.render == "p::before")
      },
      test("after pseudo-element") {
        assertTrue(CssSelector.Element("p").after.render == "p::after")
      },
      test("firstLine pseudo-element") {
        assertTrue(CssSelector.Element("p").firstLine.render == "p::first-line")
      },
      test("firstLetter pseudo-element") {
        assertTrue(CssSelector.Element("p").firstLetter.render == "p::first-letter")
      }
    ),
    suite("attribute selectors")(
      test("withAttribute presence") {
        assertTrue(CssSelector.Element("input").withAttribute("type").render == "input[type]")
      },
      test("withAttribute exact value") {
        assertTrue(CssSelector.Element("input").withAttribute("type", "text").render == """input[type="text"]""")
      },
      test("withAttributeContaining") {
        assertTrue(CssSelector.Element("div").withAttributeContaining("class", "foo").render == """div[class*="foo"]""")
      },
      test("withAttributeStarting") {
        assertTrue(CssSelector.Element("a").withAttributeStarting("href", "https").render == """a[href^="https"]""")
      },
      test("withAttributeEnding") {
        assertTrue(CssSelector.Element("img").withAttributeEnding("src", ".png").render == """img[src$=".png"]""")
      },
      test("withAttributeWord whitespace-separated") {
        assertTrue(CssSelector.Element("div").withAttributeWord("class", "active").render == """div[class~="active"]""")
      }
    ),
    suite("Attribute selector render")(
      test("HyphenPrefix matcher") {
        val sel =
          CssSelector.Attribute(CssSelector.Element("div"), "lang", Some(CssSelector.AttributeMatch.HyphenPrefix("en")))
        assertTrue(sel.render == """div[lang|="en"]""")
      },
      test("empty inner selector") {
        val sel = CssSelector.Attribute(CssSelector.Raw(""), "data-x", None)
        assertTrue(sel.render == "[data-x]")
      }
    ),
    suite("CssSelectable on Dom.Element")(
      test("Element has selector") {
        val el = div
        assertTrue(el.selector == CssSelector.Element("div"))
      },
      test("Element hover") {
        val el = div
        assertTrue(el.hover.render == "div:hover")
      },
      test("Element child combinator") {
        val result = div.child(span)
        assertTrue(result.render == "div > span")
      },
      test("combined or selector on elements") {
        val d = div
        val p = zio.blocks.html.p
        assertTrue((d | p).render == "div, p")
      }
    ),
    suite("toString delegates to render")(
      test("toString matches render") {
        val sel = CssSelector.Element("div")
        assertTrue(sel.toString == sel.render)
      }
    )
  )
}
