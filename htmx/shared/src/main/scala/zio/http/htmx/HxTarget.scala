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

package zio.http.htmx

import _root_.zio.blocks.html.CssSelector

/**
 * An element reference in htmx's extended selector syntax: `this`, a relative
 * form (`closest`, `find`, `next`, `previous`) or a plain CSS selector.
 *
 * `hxTarget` and `hxInclude` take an `HxTarget`, and it also names the element
 * in [[HxSync]], `HxTrigger#from`, `HxSwapOob.using` and the `target` of
 * [[zio.http.htmx.headers.HxLocation]]. htmx resolves the relative forms from
 * the element that carries the attribute. The `hx-swap-oob` selector and the
 * `HX-Location` target have no such element, so pass an `HxTarget.css` target
 * there: a relative form such as `closest` renders without complaint and then
 * fails in the browser (a known bug).
 *
 * {{{
 * import zio.blocks.html._
 *
 * val row = tr(
 *   hxGet := "/orders/1001/edit",
 *   hxTarget := HxTarget.closest("tr"),
 *   hxSwap := HxSwap.OuterHTML
 * )(td("Order 1001")).render
 * // <tr hx-get="/orders/1001/edit" hx-target="closest tr" hx-swap="outerHTML"><td>Order 1001</td></tr>
 * }}}
 *
 * @see
 *   [[https://zioblocks.com/reference/htmx/hx-target/ HxTarget]]
 */
sealed trait HxTarget extends Product with Serializable {

  /** Renders this target in htmx syntax, such as `closest tr` or `#results`. */
  def render: String
}

/** The [[HxTarget]] forms, factories and parser. */
object HxTarget {

  /** `this`: the element that carries the attribute. */
  case object This extends HxTarget {

    /** Renders `this`. */
    def render: String = "this"
  }

  /** Returns [[This]]; the backticked name matches htmx's `this`. */
  def `this`: HxTarget = This

  /** Returns [[This]], under a name that needs no backticks. */
  def this_ : HxTarget = This

  /**
   * `closest <selector>`: the nearest ancestor (or the element itself) that
   * matches the selector.
   */
  final case class Closest private[htmx] (selector: String) extends HxTarget {

    /** Renders `closest` followed by the selector. */
    def render: String = "closest " + selector
  }

  /** `find <selector>`: the first descendant that matches the selector. */
  final case class Find private[htmx] (selector: String) extends HxTarget {

    /** Renders `find` followed by the selector. */
    def render: String = "find " + selector
  }

  /**
   * `next` or `next <selector>`: without a selector, the next sibling element;
   * with one, the first element after this one in document order, not counting
   * its descendants, that matches it.
   */
  final case class Next private[htmx] (selector: Option[String]) extends HxTarget {

    /** Renders `next`, followed by the selector when there is one. */
    def render: String = selector.fold("next")(s => "next " + s)
  }

  /**
   * `previous` or `previous <selector>`: without a selector, the previous
   * sibling element; with one, the nearest element before this one in document
   * order, not counting its ancestors, that matches it.
   */
  final case class Previous private[htmx] (selector: Option[String]) extends HxTarget {

    /** Renders `previous`, followed by the selector when there is one. */
    def render: String = selector.fold("previous")(s => "previous " + s)
  }

  /** A plain CSS selector, rendered as-is. */
  final case class Css private[htmx] (selector: String) extends HxTarget {

    /** Renders the selector unchanged. */
    def render: String = selector
  }

  /**
   * Creates a `closest <selector>` target. The selector is trimmed.
   *
   * @throws java.lang.IllegalArgumentException
   *   if `selector` is blank
   */
  def closest(selector: String): HxTarget = new Closest(HtmxSupport.requireNonBlank(selector, "HTMX closest selector"))

  /**
   * Creates a `find <selector>` target. The selector is trimmed.
   *
   * @throws java.lang.IllegalArgumentException
   *   if `selector` is blank
   */
  def find(selector: String): HxTarget = new Find(HtmxSupport.requireNonBlank(selector, "HTMX find selector"))

  /** Creates a bare `next` target: the next sibling element. */
  def next: HxTarget = new Next(None)

  /**
   * Creates a `next <selector>` target: the first element after this one in
   * document order, not counting its descendants, that matches the selector,
   * which is trimmed.
   *
   * @throws java.lang.IllegalArgumentException
   *   if `selector` is blank
   */
  def next(selector: String): HxTarget = new Next(Some(HtmxSupport.requireNonBlank(selector, "HTMX next selector")))

  /** Creates a bare `previous` target: the previous sibling element. */
  def previous: HxTarget = new Previous(None)

  /**
   * Creates a `previous <selector>` target: the nearest element before this one
   * in document order, not counting its ancestors, that matches the selector,
   * which is trimmed.
   *
   * @throws java.lang.IllegalArgumentException
   *   if `selector` is blank
   */
  def previous(selector: String): HxTarget =
    new Previous(Some(HtmxSupport.requireNonBlank(selector, "HTMX previous selector")))

  /**
   * Creates a CSS selector target from a string, which is trimmed and otherwise
   * written as given.
   *
   * @throws java.lang.IllegalArgumentException
   *   if `selector` is blank
   */
  def css(selector: String): HxTarget = new Css(HtmxSupport.requireNonBlank(selector, "HTMX CSS selector"))

  /**
   * Creates a CSS selector target from a
   * [[zio.blocks.html.CssSelector CssSelector]]'s rendered form. An empty
   * rendered selector is not rejected.
   */
  def css(selector: CssSelector): HxTarget = new Css(selector.render)

  /**
   * Parses a rendered target, after trimming it: `this`, `next`, `previous`, or
   * a `closest `, `find `, `next ` or `previous ` prefix followed by a
   * selector. Anything else becomes a [[Css]] target, including a bare
   * `closest` with no selector. A blank input gives
   * `Left("HTMX target selector must be non-empty")`.
   */
  def parse(value: String): Either[String, HxTarget] = {
    val trimmed = value.trim
    if (trimmed.isEmpty) Left("HTMX target selector must be non-empty")
    else if (trimmed == "this") Right(This)
    else if (trimmed == "next") Right(next)
    else if (trimmed == "previous") Right(previous)
    else if (trimmed.startsWith("closest ")) parseValidated(() => closest(trimmed.substring(8)))
    else if (trimmed.startsWith("find ")) parseValidated(() => find(trimmed.substring(5)))
    else if (trimmed.startsWith("next ")) parseValidated(() => next(trimmed.substring(5)))
    else if (trimmed.startsWith("previous ")) parseValidated(() => previous(trimmed.substring(9)))
    else parseValidated(() => css(trimmed))
  }

  private def parseValidated(target: () => HxTarget): Either[String, HxTarget] =
    try Right(target())
    catch {
      case error: IllegalArgumentException => Left(error.getMessage)
    }

  /** Renders an [[HxTarget]] with [[HxTarget#render]]. */
  implicit val toHtmxValue: ToHtmxValue[HxTarget] = new ToHtmxValue[HxTarget] {
    def toHtmxValue(value: HxTarget): String = value.render
  }
}
