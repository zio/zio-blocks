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

import scala.concurrent.duration.FiniteDuration

import _root_.zio.blocks.chunk.Chunk

/**
 * An `hx-swap` value: how htmx puts a response into its target element.
 *
 * Start from a predefined strategy such as [[HxSwap.InnerHTML]] or
 * [[HxSwap.OuterHTML]], add modifiers with [[swap]], [[settle]],
 * [[transition]], [[scroll]], [[show]], [[ignoreTitle]] and [[focusScroll]],
 * and assign the result with `hxSwap :=`. Each modifier returns a new value;
 * calling the same modifier again replaces its earlier setting. [[render]]
 * writes the strategy first and then the modifiers in a fixed order (swap,
 * settle, transition, scroll, show, ignoreTitle, focusScroll), whatever order
 * they were added in.
 *
 * {{{
 * import scala.concurrent.duration.DurationInt
 * import zio.blocks.html._
 *
 * val swap = HxSwap.OuterHTML.transition.settle(250.millis).swap(1.second)
 * val rendered = swap.render // outerHTML swap:1s settle:250ms transition:true
 *
 * val card = div(hxGet := "/inventory/42", hxSwap := swap)("Loading...").render
 * // <div hx-get="/inventory/42" hx-swap="outerHTML swap:1s settle:250ms transition:true">Loading...</div>
 * }}}
 *
 * @see
 *   [[https://zioblocks.com/reference/htmx/hx-swap/ HxSwap]]
 */
sealed trait HxSwap extends Product with Serializable {

  /** The case class that holds this swap's strategy and modifiers. */
  protected def value: HxSwap.Value

  /**
   * Renders this swap as the literal `hx-swap` attribute value, such as
   * `innerHTML swap:1s`.
   */
  def render: String = value.render

  /**
   * Sets the `swap:` delay, the time htmx waits between receiving the response
   * and swapping it in. A delay that is a whole number of seconds renders in
   * seconds (`1s`); any other delay renders in milliseconds (`1500ms`), and a
   * part below one millisecond is dropped.
   *
   * @throws java.lang.IllegalArgumentException
   *   if `duration` is negative
   */
  def swap(duration: FiniteDuration): HxSwap =
    value.copy(swapDelay = Some(HtmxSupport.requireNonNegativeDuration(duration, "swap delay")))

  /**
   * Sets the `settle:` delay, the time htmx waits between the swap and the
   * settle step. The delay renders as in [[swap]].
   *
   * @throws java.lang.IllegalArgumentException
   *   if `duration` is negative
   */
  def settle(duration: FiniteDuration): HxSwap =
    value.copy(settleDelay = Some(HtmxSupport.requireNonNegativeDuration(duration, "settle delay")))

  /**
   * Adds `transition:true`, which asks htmx to run the swap through the
   * browser's View Transitions API.
   */
  def transition: HxSwap = value.copy(transitionEnabled = true)

  /**
   * Adds `scroll:top` or `scroll:bottom`, which tells htmx to scroll to the top
   * or bottom of the swapped content after the swap. The
   * `scroll:<selector>:top` form of htmx is not available.
   */
  def scroll(position: HxSwap.ScrollPosition): HxSwap = value.copy(scroll = Some(position))

  /**
   * Adds `show:top` or `show:bottom`, which tells htmx to scroll the swapped
   * content into view, aligned at its top or bottom. The `show:<selector>:top`
   * form of htmx is not available.
   */
  def show(position: HxSwap.ShowPosition): HxSwap = value.copy(show = Some(position))

  /**
   * Adds `ignoreTitle:true`, so htmx does not update the page title from a
   * `title` element in the response.
   */
  def ignoreTitle: HxSwap = value.copy(ignoreTitleEnabled = true)

  /**
   * Adds `focusScroll:true` or `focusScroll:false`.
   *
   * htmx 2 spells this modifier `focus-scroll:`, so it ignores the rendered
   * `focusScroll:` token and logs `Unknown modifier in hx-swap`; this is a
   * known bug.
   */
  def focusScroll(enabled: Boolean): HxSwap = value.copy(focusScroll = Some(enabled))
}

/** The predefined [[HxSwap]] strategies, the modifier types and the parser. */
object HxSwap {

  /** The base swap strategies that start an `hx-swap` value. */
  sealed trait Strategy extends Product with Serializable {

    /** The strategy name as htmx spells it, such as `innerHTML`. */
    def render: String
  }

  /** The nine [[Strategy]] values and their parser. */
  object Strategy {

    /** `innerHTML`: replaces the content of the target element. */
    case object InnerHTML extends Strategy { def render: String = "innerHTML" }

    /** `outerHTML`: replaces the whole target element. */
    case object OuterHTML extends Strategy { def render: String = "outerHTML" }

    /**
     * `textContent`: replaces the text of the target element without parsing
     * the response as HTML.
     */
    case object TextContent extends Strategy { def render: String = "textContent" }

    /** `beforebegin`: inserts the response before the target element. */
    case object BeforeBegin extends Strategy { def render: String = "beforebegin" }

    /**
     * `afterbegin`: inserts the response inside the target element, before its
     * first child.
     */
    case object AfterBegin extends Strategy { def render: String = "afterbegin" }

    /**
     * `beforeend`: inserts the response inside the target element, after its
     * last child.
     */
    case object BeforeEnd extends Strategy { def render: String = "beforeend" }

    /** `afterend`: inserts the response after the target element. */
    case object AfterEnd extends Strategy { def render: String = "afterend" }

    /** `delete`: removes the target element, whatever the response holds. */
    case object Delete extends Strategy { def render: String = "delete" }

    /**
     * `none`: leaves the target element alone; out-of-band elements in the
     * response are still swapped.
     */
    case object None_ extends Strategy { def render: String = "none" }

    /** Every strategy, in declaration order. */
    val all: List[Strategy] =
      List(InnerHTML, OuterHTML, TextContent, BeforeBegin, AfterBegin, BeforeEnd, AfterEnd, Delete, None_)

    /**
     * Finds the strategy whose name equals `value` exactly. The match is
     * case-sensitive: `innerhtml` gives
     * `Left("Unknown HTMX swap strategy: innerhtml")`.
     */
    def parse(value: String): Either[String, Strategy] =
      all.find(_.render == value).toRight("Unknown HTMX swap strategy: " + value)
  }

  /** Positions for the `scroll:` modifier. */
  sealed trait ScrollPosition extends Product with Serializable {

    /** The position as written in `hx-swap`: `top` or `bottom`. */
    def render: String
  }

  /** The two [[ScrollPosition]] values. */
  object ScrollPosition {

    /** Renders `top`. */
    case object Top extends ScrollPosition { def render: String = "top" }

    /** Renders `bottom`. */
    case object Bottom extends ScrollPosition { def render: String = "bottom" }
  }

  /** Positions for the `show:` modifier. */
  sealed trait ShowPosition extends Product with Serializable {

    /** The position as written in `hx-swap`: `top` or `bottom`. */
    def render: String
  }

  /** The two [[ShowPosition]] values. */
  object ShowPosition {

    /** Renders `top`. */
    case object Top extends ShowPosition { def render: String = "top" }

    /** Renders `bottom`. */
    case object Bottom extends ShowPosition { def render: String = "bottom" }
  }

  /**
   * The case class behind every [[HxSwap]]: a base strategy and its optional
   * modifiers. `HxSwap.InnerHTML` equals `Value(Strategy.InnerHTML)`. The
   * constructor does not check the delays; the [[HxSwap#swap]] and
   * [[HxSwap#settle]] methods reject negative ones.
   */
  final case class Value(
    strategy: Strategy,
    swapDelay: Option[FiniteDuration] = scala.None,
    settleDelay: Option[FiniteDuration] = scala.None,
    transitionEnabled: Boolean = false,
    scroll: Option[ScrollPosition] = scala.None,
    show: Option[ShowPosition] = scala.None,
    ignoreTitleEnabled: Boolean = false,
    focusScroll: Option[Boolean] = scala.None
  ) extends HxSwap {
    protected def value: Value = this

    /**
     * Renders the strategy followed by the set modifiers, separated by single
     * spaces, in the order swap, settle, transition, scroll, show, ignoreTitle,
     * focusScroll.
     */
    override def render: String = {
      val parts = Chunk.newBuilder[String]
      parts += strategy.render
      swapDelay.foreach(d => parts += ("swap:" + HtmxSupport.renderDuration(d)))
      settleDelay.foreach(d => parts += ("settle:" + HtmxSupport.renderDuration(d)))
      if (transitionEnabled) parts += "transition:true"
      scroll.foreach(v => parts += ("scroll:" + v.render))
      show.foreach(v => parts += ("show:" + v.render))
      if (ignoreTitleEnabled) parts += "ignoreTitle:true"
      focusScroll.foreach(v => parts += ("focusScroll:" + (if (v) "true" else "false")))
      parts.result().mkString(" ")
    }
  }

  /** `innerHTML` with no modifiers: replaces the content of the target. */
  val InnerHTML: HxSwap = Value(Strategy.InnerHTML)

  /** `outerHTML` with no modifiers: replaces the whole target element. */
  val OuterHTML: HxSwap = Value(Strategy.OuterHTML)

  /** `textContent` with no modifiers: replaces the target's text. */
  val TextContent: HxSwap = Value(Strategy.TextContent)

  /** `beforebegin` with no modifiers: inserts before the target. */
  val BeforeBegin: HxSwap = Value(Strategy.BeforeBegin)

  /**
   * `afterbegin` with no modifiers: inserts before the target's first child.
   */
  val AfterBegin: HxSwap = Value(Strategy.AfterBegin)

  /** `beforeend` with no modifiers: inserts after the target's last child. */
  val BeforeEnd: HxSwap = Value(Strategy.BeforeEnd)

  /** `afterend` with no modifiers: inserts after the target. */
  val AfterEnd: HxSwap = Value(Strategy.AfterEnd)

  /** `delete` with no modifiers: removes the target element. */
  val Delete: HxSwap = Value(Strategy.Delete)

  /** `none` with no modifiers: leaves the target alone. */
  val NoneSwap: HxSwap = Value(Strategy.None_)

  /**
   * Parses an `hx-swap` string, such as one read from an `HX-Reswap` header,
   * into an [[HxSwap]]. It reads every string that [[HxSwap#render]] produces,
   * except a negative delay set through the [[HxSwap.Value]] constructor
   * (`swap:-1s` gives `Left("HTMX duration must be non-negative: -1")`).
   *
   * Tokens are separated by whitespace, and the first one must be an exact
   * strategy name. A delay is a whole number followed by `ms` or `s`, or a bare
   * number of milliseconds (`swap:500`); fractions such as `1.5s` and negative
   * numbers are rejected. `transition:` and `ignoreTitle:` are accepted only
   * with `true`, `scroll:` and `show:` only with `top` or `bottom`, and the
   * focus modifier only as `focusScroll:true` or `focusScroll:false` (htmx's
   * own `focus-scroll:` spelling is rejected). Any other input gives a `Left`
   * with a message such as `Unsupported HTMX swap modifier: transition:false`
   * or `Empty HTMX swap value`.
   */
  def parse(value: String): Either[String, HxSwap] = {
    val tokens = value.split("\\s+").toList.filter(_.nonEmpty)
    tokens match {
      case Nil          => Left("Empty HTMX swap value")
      case head :: tail =>
        Strategy.parse(head).flatMap { strategy =>
          tail.foldLeft[Either[String, Value]](Right(Value(strategy))) { (current, token) =>
            current.flatMap { swap =>
              if (token == "transition:true") Right(swap.copy(transitionEnabled = true))
              else if (token == "ignoreTitle:true") Right(swap.copy(ignoreTitleEnabled = true))
              else if (token.startsWith("swap:"))
                HtmxSupport.parseDuration(token.substring(5)).map(d => swap.copy(swapDelay = Some(d)))
              else if (token.startsWith("settle:"))
                HtmxSupport.parseDuration(token.substring(7)).map(d => swap.copy(settleDelay = Some(d)))
              else if (token.startsWith("scroll:"))
                parseScroll(token.substring(7)).map(v => swap.copy(scroll = Some(v)))
              else if (token.startsWith("show:"))
                parseShow(token.substring(5)).map(v => swap.copy(show = Some(v)))
              else if (token.startsWith("focusScroll:"))
                parseBoolean(token.substring(12)).map(v => swap.copy(focusScroll = Some(v)))
              else Left("Unsupported HTMX swap modifier: " + token)
            }
          }
        }
    }
  }

  private def parseBoolean(value: String): Either[String, Boolean] = value match {
    case "true"  => Right(true)
    case "false" => Right(false)
    case other   => Left("Invalid boolean value: " + other)
  }

  private def parseScroll(value: String): Either[String, ScrollPosition] = value match {
    case "top"    => Right(ScrollPosition.Top)
    case "bottom" => Right(ScrollPosition.Bottom)
    case other    => Left("Invalid HTMX scroll position: " + other)
  }

  private def parseShow(value: String): Either[String, ShowPosition] = value match {
    case "top"    => Right(ShowPosition.Top)
    case "bottom" => Right(ShowPosition.Bottom)
    case other    => Left("Invalid HTMX show position: " + other)
  }

  /** Renders an [[HxSwap]] with [[HxSwap#render]]. */
  implicit val toHtmxValue: ToHtmxValue[HxSwap] = new ToHtmxValue[HxSwap] {
    def toHtmxValue(value: HxSwap): String = value.render
  }
}
