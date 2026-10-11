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

import scala.compiletime.testing.{Error, ErrorKind, typeCheckErrors}
import zio.blocks.chunk.Chunk
import zio.test._

/**
 * Tests for the literal-checked `element` / `voidElement` factories, the
 * dynamic `Either`-based factories, and sealed generic construction.
 *
 * Note: `element`, `voidElement`, and `typeCheckErrors` are all `inline`, and
 * `assertTrue` cannot inspect an `inline` call tree used directly as a select
 * receiver (e.g. `element("div").render` inside `assertTrue`). Every inline
 * call below is therefore bound to a `val` first; the assertions only inspect
 * the bound values. `typeCheckErrors` (rather than `typeChecks`) is used
 * throughout because it reports errors faithfully in the caller's context;
 * every rejection is asserted through its specific message, and every
 * acceptance is paired with a direct call proving the code compiles.
 */
object ElementTagSpec extends ZIOSpecDefault {
  // Public so typeCheckErrors snippets (checked without access to private
  // members) can reference them.
  inline val inlineWidgetTag              = "my-widget"
  final val constSectionTag               = "section"
  private val typedWidgetTag: "my-widget" = "my-widget"

  inline def forwardElement(inline t: String): Dom.Element = element(t)
  private def literalWidget(tag: "my-widget"): Dom.Element = element(tag)

  private val rendersDiv          = element("div").render
  private val rendersCustom       = element("custom-tag")("content").render
  private val rendersBr           = voidElement("br").render
  private val rendersUpperBr      = voidElement("BR").render
  private val rendersInlineTag    = element(inlineWidgetTag).render
  private val rendersConstTag     = element(constSectionTag).render
  private val rendersTypedTag     = element(typedWidgetTag).render
  private val rendersUpperDiv     = element("DIV").render
  private val rendersUpperSample  = element("SECTION")("x").render
  private val rendersForwarded    = forwardElement("div").render
  private val rendersLiteralParam = literalWidget("my-widget").render

  private val checksDivLiteral: List[String]    = typeCheckErrors("""zio.blocks.html.element("div")""").map(_.message)
  private val checksWidgetLiteral: List[String] =
    typeCheckErrors("""zio.blocks.html.element("my-widget")""").map(_.message)
  private val checksBrVoid: List[String]      = typeCheckErrors("""zio.blocks.html.voidElement("br")""").map(_.message)
  private val checksUpperBrVoid: List[String] = typeCheckErrors("""zio.blocks.html.voidElement("BR")""").map(_.message)
  private val checksInlineTag: List[String]   =
    typeCheckErrors("""zio.blocks.html.element(ElementTagSpec.inlineWidgetTag)""").map(_.message)
  private val checksConstTag: List[String] =
    typeCheckErrors("""zio.blocks.html.element(ElementTagSpec.constSectionTag)""").map(_.message)

  // Independently authored expectations for the macro diagnostics below are
  // identical on 3.9.0 and 3.3.7.

  private val elementRuntimeMessage =
    """element(tag) failed: tag is not a compile-time string literal, so the void check cannot run at compile time.
To fix:
- for a runtime tag, use elementDynamic(tag) and handle both outcomes explicitly: elementDynamic(tag) match { case Right(element) => ...; case Left(error) => ... };
- for a fixed tag, pass a literal instead, e.g. inline val tag = "div" or val tag: "div" = "div", or forward an inline parameter, e.g. inline def build(inline tag: String) = element(tag), called with a literal."""

  private val voidRuntimeMessage =
    """voidElement(tag) failed: tag is not a compile-time string literal, so the void check cannot run at compile time.
To fix:
- for a runtime tag, use voidElementDynamic(tag) and handle both outcomes explicitly: voidElementDynamic(tag) match { case Right(element) => ...; case Left(error) => ... };
- for a fixed tag, pass a literal instead, e.g. inline val tag = "br" or val tag: "br" = "br", or forward an inline parameter, e.g. inline def build(inline tag: String) = voidElement(tag), called with a literal."""

  private def widenedMessage(factory: String, valName: String): String = {
    val dynamic = s"${factory}Dynamic"
    val example = if (factory == "element") "div" else "br"
    s"""$factory(tag) failed: tag is not a compile-time string literal: it refers to val `$valName`, whose declared type is not a literal type.
To fix:
- if the value is a fixed literal, give it a literal type, e.g. val tag: "$example" = "$example", or use an inline val;
- otherwise, for a runtime tag, use $dynamic(tag) and handle both outcomes explicitly: $dynamic(tag) match { case Right(element) => ...; case Left(error) => ... }."""
  }

  // Verified empirically: the macro-authored diagnostics below are identical
  // on 3.9.0 and 3.3.7, as is the remaining compiler-owned diagnostic (the
  // `Generic.copy` absence reports the bare text on both versions). The
  // `VoidGeneric.copy` and `Void.withChildren` absences diverge by version
  // (3.3.7 appends an extension-method hint), so those two snippets are
  // checked cross-package in CheckedElementFactorySpec, where the bare
  // diagnostics are stable and asserted in full with their kinds.

  private val rejectsBr: List[String]            = typeCheckErrors("""zio.blocks.html.element("br")""").map(_.message)
  private val rejectsUpper: List[String]         = typeCheckErrors("""zio.blocks.html.element("BR")""").map(_.message)
  private val rejectsMixed: List[String]         = typeCheckErrors("""zio.blocks.html.element("Br")""").map(_.message)
  private val rejectsImg: List[String]           = typeCheckErrors("""zio.blocks.html.element("img")""").map(_.message)
  private val rejectsObsoleteParam: List[String] =
    typeCheckErrors("""zio.blocks.html.element("param")""").map(_.message)
  private val rejectsDiv: List[String]    = typeCheckErrors("""zio.blocks.html.voidElement("div")""").map(_.message)
  private val rejectsSpan: List[String]   = typeCheckErrors("""zio.blocks.html.voidElement("span")""").map(_.message)
  private val rejectsWidget: List[String] =
    typeCheckErrors("""zio.blocks.html.voidElement("my-widget")""").map(_.message)
  private val rejectsObjectAlias: List[String] =
    typeCheckErrors("""zio.blocks.html.voidElement("object")""").map(_.message)
  private val rejectsUlDef: List[String] =
    typeCheckErrors("""zio.blocks.html.voidElement("ul")""").map(_.message)

  private val rejectsPlainVal: List[String] =
    typeCheckErrors("""{ val tag = "div"; zio.blocks.html.element(tag) }""").map(_.message)
  private val rejectsStringVal: List[String] =
    typeCheckErrors("""{ val tag: String = "div"; zio.blocks.html.element(tag) }""").map(_.message)
  private val rejectsDef: List[String] =
    typeCheckErrors("""{ def tag: String = "div"; zio.blocks.html.element(tag) }""").map(_.message)
  private val rejectsVar: List[String] =
    typeCheckErrors("""{ var tag = "div"; zio.blocks.html.element(tag) }""").map(_.message)
  private val rejectsParam: List[String] =
    typeCheckErrors("""{ def build(tag: String) = zio.blocks.html.element(tag); build("div") }""").map(_.message)
  private val rejectsVoidString: List[String] =
    typeCheckErrors("""{ val tag = "br"; zio.blocks.html.voidElement(tag) }""").map(_.message)
  private val rejectsFinalWidened: List[String] =
    typeCheckErrors("""{ object Tags { final val tag: String = "div" }; zio.blocks.html.element(Tags.tag) }""").map(
      _.message
    )

  private val rejectsElementSingleton: List[String] =
    typeCheckErrors("""{ def build[T <: String](tag: T) = zio.blocks.html.element(tag); build("div") }""").map(
      _.message
    )
  private val rejectsVoidSingleton: List[String] =
    typeCheckErrors("""{ def build[T <: String](tag: T) = zio.blocks.html.voidElement(tag); build("br") }""").map(
      _.message
    )
  private val rejectsStableSingleton: List[String] =
    typeCheckErrors(
      """{ val tag = "div"; def build(t: tag.type) = zio.blocks.html.element(t); build(tag) }"""
    ).map(_.message)
  private val rejectsSingletonBound: List[String] =
    typeCheckErrors(
      """{ def build[T <: String & Singleton](tag: T) = zio.blocks.html.element(tag); build("div") }"""
    ).map(_.message)
  private val rejectsVoidSingletonBound: List[String] =
    typeCheckErrors(
      """{ def build[T <: String & Singleton](tag: T) = zio.blocks.html.voidElement(tag); build("br") }"""
    ).map(_.message)
  private val rejectsGenericCopy: List[Error] =
    typeCheckErrors(
      """zio.blocks.html.element("div").asInstanceOf[zio.blocks.html.Dom.Element.Generic].copy(tag = "br")"""
    )

  // Explicit result types: typeCheckErrors is transparent inline, so its
  // call sites need ascribed types to avoid signature-inference cycles.
  def spec = suite("ElementTag")(
    suite("static factories accept compile-time tags")(
      test("string literals") {
        assertTrue(
          rendersDiv == "<div></div>",
          rendersCustom == "<custom-tag>content</custom-tag>",
          rendersBr == "<br/>",
          rendersUpperBr == "<BR/>",
          checksDivLiteral.isEmpty,
          checksWidgetLiteral.isEmpty,
          checksBrVoid.isEmpty,
          checksUpperBrVoid.isEmpty
        )
      },
      test("inline vals, constants, and literal types") {
        assertTrue(
          rendersInlineTag == "<my-widget></my-widget>",
          rendersConstTag == "<section></section>",
          rendersTypedTag == "<my-widget></my-widget>",
          checksInlineTag.isEmpty,
          checksConstTag.isEmpty
        )
      },
      test("inline parameter forwarding") {
        assertTrue(rendersForwarded == "<div></div>")
      },
      test("literal-typed parameters") {
        assertTrue(rendersLiteralParam == "<my-widget></my-widget>")
      },
      test("uppercase non-void tags stay non-void") {
        assertTrue(
          rendersUpperDiv == "<DIV></DIV>",
          rendersUpperSample == "<SECTION>x</SECTION>"
        )
      },
      test("guidance examples render as documented") {
        assertTrue(
          br(id := "gap").render == """<br id="gap"/>""",
          div(br, "text").render == "<div><br/>text</div>",
          div(id := "gap", "text").render == """<div id="gap">text</div>""",
          ul(id := "gap", li("a")).render == """<ul id="gap"><li>a</li></ul>""",
          ol(id := "gap", li("a")).render == """<ol id="gap"><li>a</li></ol>""",
          table(id := "gap", tr(td("a"))).render == """<table id="gap"><tr><td>a</td></tr></table>""",
          tr(id := "gap", td("a")).render == """<tr id="gap"><td>a</td></tr>""",
          select(id := "gap", option("a")).render == """<select id="gap"><option>a</option></select>""",
          optgroup(id := "gap", option("a")).render == """<optgroup id="gap"><option>a</option></optgroup>"""
        )
      }
    ),
    suite("static factories reject invalid tags at compile time")(
      test("element rejects void tags") {
        assertTrue(
          rejectsBr == List(
            """Cannot create <br> with element: <br> is a void HTML tag.

Use the built-in br factory:
  element("br")              → br
  element("br")(id := "gap") → br(id := "gap")

br accepts attributes only. Move any child content outside br,
for example: div(br, "text")."""
          ),
          rejectsUpper == List(
            """Cannot create <BR> with element: <BR> is a void HTML tag.

Use the built-in br factory:
  element("BR")              → br
  element("BR")(id := "gap") → br(id := "gap")

br accepts attributes only. Move any child content outside br,
for example: div(br, "text")."""
          ),
          rejectsMixed == List(
            """Cannot create <Br> with element: <Br> is a void HTML tag.

Use the built-in br factory:
  element("Br")              → br
  element("Br")(id := "gap") → br(id := "gap")

br accepts attributes only. Move any child content outside br,
for example: div(br, "text")."""
          ),
          rejectsImg == List(
            """Cannot create <img> with element: <img> is a void HTML tag.

Use the built-in img factory:
  element("img")              → img
  element("img")(id := "gap") → img(id := "gap")

img accepts attributes only. Move any child content outside img,
for example: div(img, "text")."""
          ),
          rejectsObsoleteParam == List(
            """Cannot create <param> with element: <param> is a void HTML tag.

Use the built-in param factory:
  element("param")              → param
  element("param")(id := "gap") → param(id := "gap")

param accepts attributes only. Move any child content outside param,
for example: div(param, "text")."""
          )
        )
      },
      test("voidElement rejects non-void tags") {
        assertTrue(
          rejectsDiv == List(
            """Cannot create <div> with voidElement: <div> is not a void HTML tag.

Use the built-in div factory:
  voidElement("div")              → div
  voidElement("div")(id := "gap") → div(id := "gap")

div accepts children and attributes, for example: div(id := "gap", "text")."""
          ),
          rejectsSpan == List(
            """Cannot create <span> with voidElement: <span> is not a void HTML tag.

Use the built-in span factory:
  voidElement("span")              → span
  voidElement("span")(id := "gap") → span(id := "gap")

span accepts children and attributes, for example: span(id := "gap", "text")."""
          ),
          rejectsWidget == List(
            """Cannot create <my-widget> with voidElement: <my-widget> is not a void HTML tag.

Use the generic element factory:
  voidElement("my-widget")              → element("my-widget")
  voidElement("my-widget")(id := "gap") → element("my-widget")(id := "gap")

element("my-widget") accepts children and attributes, for example: element("my-widget")(id := "gap", "text")."""
          ),
          rejectsObjectAlias == List(
            """Cannot create <object> with voidElement: <object> is not a void HTML tag.

Use the built-in `object` factory:
  voidElement("object")              → `object`
  voidElement("object")(id := "gap") → `object`(id := "gap")

`object` accepts children and attributes, for example: `object`(id := "gap", "text")."""
          ),
          rejectsUlDef == List(
            """Cannot create <ul> with voidElement: <ul> is not a void HTML tag.

Use the built-in ul() factory:
  voidElement("ul")              → ul()
  voidElement("ul")(id := "gap") → ul(id := "gap")

ul() accepts children and attributes, for example: ul(id := "gap", li("a"))."""
          )
        )
      },
      test("static factories reject runtime tags") {
        assertTrue(
          rejectsDef == List(elementRuntimeMessage),
          rejectsVar == List(elementRuntimeMessage),
          rejectsParam == List(elementRuntimeMessage)
        )
      },
      test("static factories name widened stable vals") {
        assertTrue(
          rejectsPlainVal == List(widenedMessage("element", "tag")),
          rejectsStringVal == List(widenedMessage("element", "tag")),
          rejectsFinalWidened == List(widenedMessage("element", "tag")),
          rejectsVoidString == List(widenedMessage("voidElement", "tag"))
        )
      },
      test("static factories reject unresolved singleton types") {
        assertTrue(
          rejectsElementSingleton == List(elementRuntimeMessage),
          rejectsVoidSingleton == List(voidRuntimeMessage)
        )
      },
      test("static factories reject stable singleton types") {
        assertTrue(rejectsStableSingleton == List(elementRuntimeMessage))
      },
      test("static factories reject abstract Singleton-bounded types") {
        assertTrue(
          rejectsSingletonBound == List(elementRuntimeMessage),
          rejectsVoidSingletonBound == List(voidRuntimeMessage)
        )
      }
    ),
    suite("dynamic factories")(
      test("elementDynamic builds non-void elements") {
        assertTrue(
          elementDynamic("div").map(_.render) == Right("<div></div>"),
          elementDynamic("custom-element").map(_.render) == Right("<custom-element></custom-element>"),
          elementDynamic("DIV").map(_.render) == Right("<DIV></DIV>")
        )
      },
      test("elementDynamic rejects void tags with a typed error") {
        assertTrue(
          elementDynamic("br") == Left(TagError.VoidTag("br")),
          elementDynamic("BR") == Left(TagError.VoidTag("BR")),
          elementDynamic("Br") == Left(TagError.VoidTag("Br")),
          elementDynamic("br").left.map(_.message) ==
            Left(
              """Cannot create <br> with elementDynamic: <br> is a void HTML tag.

Use the built-in br factory instead: br accepts attributes only. Move any child content outside br,
for example: div(br, "text").
For tag-generic code handling any runtime tag, use voidElementDynamic for this tag and handle Left(error) / Right(element) explicitly."""
            ),
          elementDynamic("BR").left.map(_.message) ==
            Left(
              """Cannot create <BR> with elementDynamic: <BR> is a void HTML tag.

Use the built-in br factory instead: br accepts attributes only. Move any child content outside br,
for example: div(br, "text").
For tag-generic code handling any runtime tag, use voidElementDynamic for this tag and handle Left(error) / Right(element) explicitly."""
            )
        )
      },
      test("voidElementDynamic builds void elements") {
        assertTrue(
          voidElementDynamic("br").map(_.render) == Right("<br/>"),
          voidElementDynamic("BR").map(_.render) == Right("<BR/>"),
          voidElementDynamic("img").map(_.render) == Right("<img/>")
        )
      },
      test("voidElementDynamic rejects non-void tags with a typed error") {
        assertTrue(
          voidElementDynamic("div") == Left(TagError.NonVoidTag("div")),
          voidElementDynamic("my-widget") == Left(TagError.NonVoidTag("my-widget")),
          voidElementDynamic("object") == Left(TagError.NonVoidTag("object")),
          voidElementDynamic("div").left.map(_.message) ==
            Left(
              """Cannot create <div> with voidElementDynamic: <div> is not a void HTML tag.

Use the built-in div factory instead: div accepts children and attributes, for example: div(id := "gap", "text").
For tag-generic code handling any runtime tag, use elementDynamic for this tag and handle Left(error) / Right(element) explicitly."""
            ),
          voidElementDynamic("object").left.map(_.message) ==
            Left(
              """Cannot create <object> with voidElementDynamic: <object> is not a void HTML tag.

Use the built-in `object` factory instead: `object` accepts children and attributes, for example: `object`(id := "gap", "text").
For tag-generic code handling any runtime tag, use elementDynamic for this tag and handle Left(error) / Right(element) explicitly."""
            ),
          voidElementDynamic("my-widget").left.map(_.message) ==
            Left(
              """Cannot create <my-widget> with voidElementDynamic: <my-widget> is not a void HTML tag.

Use elementDynamic for this tag and handle Left(error) / Right(element) explicitly."""
            )
        )
      },
      test("dynamic results support the modifier model") {
        assertTrue(
          elementDynamic("div").map(_("Hello").render) == Right("<div>Hello</div>"),
          elementDynamic("div").map(_(id := "main", "x").render) == Right("""<div id="main">x</div>"""),
          voidElementDynamic("img").map(_(src := "a.png").render) == Right("""<img src="a.png"/>""")
        )
      }
    ),
    suite("sealed construction")(
      test("elements deconstruct through extractors") {
        val generic    = element("div")(id := "a", "x")
        val void       = voidElement("img")(src := "a.png")
        val genericTag = generic match {
          case Dom.Element.Generic(tag, _, _) => tag
          case _                              => "<no-match>"
        }
        val voidTag = void match {
          case Dom.Element.VoidGeneric(tag, _) => tag
          case _                               => "<no-match>"
        }
        assertTrue(genericTag == "div", voidTag == "img")
      },
      test("Generic nodes are never void") {
        val generic = element("div")(id := "a", "x").asInstanceOf[Dom.Element.Generic]
        val updated = generic.withChildren(Chunk(Dom.Text("y")))
        assertTrue(!generic.isVoid, updated.render == """<div id="a">y</div>""")
      },
      test("copy with a retargeted tag does not compile") {
        assertTrue(
          rejectsGenericCopy.map(_.message) == List(
            "value copy is not a member of zio.blocks.html.Dom.Element.Generic"
          ),
          rejectsGenericCopy.map(_.kind) == List(ErrorKind.Typer)
        )
      },
      test("deriving modified elements preserves tags") {
        val renamed       = element("h2").withAttributes(Chunk.empty).withChildren(Chunk(Dom.Text("Hi")))
        val voided        = voidElement("img").withAttributes(Chunk(src := "a.png"))
        val renamedRender = renamed.render
        val voidedRender  = voided.render
        assertTrue(
          renamedRender == "<h2>Hi</h2>",
          voidedRender == """<img src="a.png"/>"""
        )
      }
    )
  )
}
