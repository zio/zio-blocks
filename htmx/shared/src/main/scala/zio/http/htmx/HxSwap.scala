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
 * Typed representation of an `hx-swap` value.
 *
 * Start from one of the predefined strategies such as [[HxSwap.InnerHTML]] or
 * [[HxSwap.AfterEnd]], then add modifiers like [[swap]], [[settle]],
 * [[transition]], [[scroll]], [[show]], [[ignoreTitle]], and [[focusScroll]].
 */
sealed trait HxSwap extends Product with Serializable {
  protected def value: HxSwap.Value

  /** Renders this swap strategy into the literal `hx-swap` attribute value. */
  def render: String = value.render

  /** Adds a `swap:` delay modifier. */
  def swap(duration: FiniteDuration): HxSwap =
    value.copy(swapDelay = Some(HtmxSupport.requireNonNegativeDuration(duration, "swap delay")))

  /** Adds a `settle:` delay modifier. */
  def settle(duration: FiniteDuration): HxSwap =
    value.copy(settleDelay = Some(HtmxSupport.requireNonNegativeDuration(duration, "settle delay")))

  /** Enables `transition:true`. */
  def transition: HxSwap = value.copy(transitionEnabled = true)

  /** Adds a `scroll:` modifier. */
  def scroll(position: HxSwap.ScrollPosition): HxSwap = value.copy(scroll = Some(position))

  /** Adds a `show:` modifier. */
  def show(position: HxSwap.ShowPosition): HxSwap = value.copy(show = Some(position))

  /** Enables `ignoreTitle:true`. */
  def ignoreTitle: HxSwap = value.copy(ignoreTitleEnabled = true)

  /** Sets `focusScroll:true` or `focusScroll:false`. */
  def focusScroll(enabled: Boolean): HxSwap = value.copy(focusScroll = Some(enabled))
}

object HxSwap {

  /** Core HTMX swap strategies supported by `hx-swap`. */
  sealed trait Strategy extends Product with Serializable {
    def render: String
  }

  object Strategy {
    case object InnerHTML   extends Strategy { def render: String = "innerHTML"   }
    case object OuterHTML   extends Strategy { def render: String = "outerHTML"   }
    case object TextContent extends Strategy { def render: String = "textContent" }
    case object BeforeBegin extends Strategy { def render: String = "beforebegin" }
    case object AfterBegin  extends Strategy { def render: String = "afterbegin"  }
    case object BeforeEnd   extends Strategy { def render: String = "beforeend"   }
    case object AfterEnd    extends Strategy { def render: String = "afterend"    }
    case object Delete      extends Strategy { def render: String = "delete"      }
    case object None_       extends Strategy { def render: String = "none"        }

    val all: List[Strategy] =
      List(InnerHTML, OuterHTML, TextContent, BeforeBegin, AfterBegin, BeforeEnd, AfterEnd, Delete, None_)

    def parse(value: String): Either[String, Strategy] =
      all.find(_.render == value).toRight("Unknown HTMX swap strategy: " + value)
  }

  /** Valid positions for the `scroll:` modifier. */
  sealed trait ScrollPosition extends Product with Serializable {
    def render: String
  }

  object ScrollPosition {
    case object Top    extends ScrollPosition { def render: String = "top"    }
    case object Bottom extends ScrollPosition { def render: String = "bottom" }
  }

  /** Valid positions for the `show:` modifier. */
  sealed trait ShowPosition extends Product with Serializable {
    def render: String
  }

  object ShowPosition {
    case object Top    extends ShowPosition { def render: String = "top"    }
    case object Bottom extends ShowPosition { def render: String = "bottom" }
  }

  /** Concrete swap value with a base strategy and zero or more modifiers. */
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

  val InnerHTML: HxSwap   = Value(Strategy.InnerHTML)
  val OuterHTML: HxSwap   = Value(Strategy.OuterHTML)
  val TextContent: HxSwap = Value(Strategy.TextContent)
  val BeforeBegin: HxSwap = Value(Strategy.BeforeBegin)
  val AfterBegin: HxSwap  = Value(Strategy.AfterBegin)
  val BeforeEnd: HxSwap   = Value(Strategy.BeforeEnd)
  val AfterEnd: HxSwap    = Value(Strategy.AfterEnd)
  val Delete: HxSwap      = Value(Strategy.Delete)
  val NoneSwap: HxSwap    = Value(Strategy.None_)

  /**
   * Parses a rendered HTMX swap string back into a typed [[HxSwap]] value.
   *
   * The parser accepts the same rendering format produced by [[render]] and
   * returns a descriptive error for unsupported strategies or modifiers.
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

  implicit val toHtmxValue: ToHtmxValue[HxSwap] = new ToHtmxValue[HxSwap] {
    def toHtmxValue(value: HxSwap): String = value.render
  }
}
