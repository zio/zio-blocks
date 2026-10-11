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

package zio.http.datastar

import scala.compiletime.testing.{Error, ErrorKind, typeCheckErrors}
import zio.blocks.html._
import zio.test._

/**
 * Locks in that generic element construction from outside the `html` package
 * goes through the checked factories: the `Generic` / `VoidGeneric`
 * constructors are not directly accessible, and there is no `copy` that could
 * retarget a tag without validation. `Void` likewise offers no `withChildren`.
 *
 * Note: `typeCheckErrors` (rather than `typeChecks`) is used because it reports
 * errors faithfully in the caller's context. The accepted snippets below double
 * as controls proving the machinery can succeed cross-package, so the
 * rejections are meaningful rather than vacuous. All snippets are fully
 * qualified without wildcard imports, which keeps the missing-member
 * diagnostics bare and identical on every supported Scala version.
 */
object CheckedElementFactorySpec extends ZIOSpecDefault {
  // Exact per-version expectations below: 3.3.7 appends an extension-method
  // hint (naming a transitive izumi test-classpath extension) to the
  // `VoidGeneric.copy` and `Void.withChildren` diagnostics, while 3.9.0
  // reports the bare text. The branch below keeps both full texts explicit so
  // drift on either version fails loudly.
  private val isScala33: Boolean = zio.blocks.html.BuildInfo.scalaVersion == "3.3.7"

  private val extensionHint: String =
    ", but could be made available as an extension method.\n\nThe following import might make progress towards fixing the problem:\n\n  import izumi.reflect.dottyreflection.ReflectionUtil.\n  reflectiveUncheckedNonOverloadedSelectable\n\n"

  private def versionedMessage(base: String): String =
    if (isScala33) base + extensionHint else base

  private val checkedDiv: List[String] =
    typeCheckErrors("""{ import zio.blocks.html._; element("div")(id := "a", "x").render }""").map(_.message)
  private val rejectedGeneric: List[Error] =
    typeCheckErrors(
      """{ import zio.blocks.chunk.Chunk; zio.blocks.html.Dom.Element.Generic("div", Chunk.empty, Chunk.empty) }"""
    )
  private val rejectedVoidGeneric: List[Error] =
    typeCheckErrors(
      """{ import zio.blocks.chunk.Chunk; zio.blocks.html.Dom.Element.VoidGeneric("br", Chunk.empty) }"""
    )
  private val rejectedCopy: List[Error] =
    typeCheckErrors(
      """{ import zio.blocks.html._; element("div").asInstanceOf[Dom.Element.Generic].copy(tag = "br") }"""
    )
  private val rejectedVoidCopy: List[Error] =
    typeCheckErrors(
      """zio.blocks.html.voidElement("br").asInstanceOf[zio.blocks.html.Dom.Element.VoidGeneric].copy(tag = "div")"""
    )
  private val rejectedWithChildren: List[Error] =
    typeCheckErrors(
      """zio.blocks.html.voidElement("br").withChildren(zio.blocks.chunk.Chunk.empty)"""
    )

  def spec = suite("CheckedElementFactory")(
    test("checked factories build elements from literals") {
      val divRender = element("div")(id := "a", "x").render
      val imgRender = voidElement("img")(src := "a.png").render
      assertTrue(
        divRender == """<div id="a">x</div>""",
        imgRender == """<img src="a.png"/>""",
        elementDynamic("section").map(_.render) == Right("<section></section>"),
        voidElementDynamic("br").map(_.render) == Right("<br/>"),
        checkedDiv.isEmpty
      )
    },
    test("direct generic construction does not compile") {
      assertTrue(
        rejectedGeneric.map(_.message) == List("object Generic in object Element does not take parameters"),
        rejectedGeneric.map(_.kind) == List(ErrorKind.Typer),
        rejectedVoidGeneric.map(_.message) == List("object VoidGeneric in object Element does not take parameters"),
        rejectedVoidGeneric.map(_.kind) == List(ErrorKind.Typer)
      )
    },
    test("tag-changing copy does not compile") {
      assertTrue(
        rejectedCopy.map(_.message) == List("value copy is not a member of zio.blocks.html.Dom.Element.Generic"),
        rejectedCopy.map(_.kind) == List(ErrorKind.Typer),
        rejectedVoidCopy.map(_.message) == List(
          versionedMessage("value copy is not a member of zio.blocks.html.Dom.Element.VoidGeneric")
        ),
        rejectedVoidCopy.map(_.kind) == List(ErrorKind.Typer)
      )
    },
    test("void rejects children at compile time") {
      val brRender = voidElement("br").render
      assertTrue(
        brRender == "<br/>",
        rejectedWithChildren.map(_.message) == List(
          versionedMessage("value withChildren is not a member of zio.blocks.html.Dom.Element.Void")
        ),
        rejectedWithChildren.map(_.kind) == List(ErrorKind.Typer)
      )
    }
  )
}
