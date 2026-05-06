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

package zio.blocks.htmx

import zio.blocks.html.CssSelector

/**
 * Typed representation of the target selector forms accepted by HTMX.
 *
 * These values are used by attributes such as `hx-target`, `hx-include`, and
 * `hx-sync`.
 */
sealed trait HxTarget extends Product with Serializable {
  def render: String
}

object HxTarget {

  /** Targets the current element (`this`). */
  case object This extends HxTarget {
    def render: String = "this"
  }

  /** Identifier-friendly alias for [[This]]. */
  def this_ : HxTarget = This

  /** Targets the closest ancestor matching the selector. */
  final case class Closest(selector: String) extends HxTarget {
    def render: String = "closest " + selector
  }

  /** Targets the first descendant matching the selector. */
  final case class Find(selector: String) extends HxTarget {
    def render: String = "find " + selector
  }

  /** Targets the next sibling, optionally filtered by selector. */
  final case class Next(selector: Option[String]) extends HxTarget {
    def render: String = selector.fold("next")(s => "next " + s)
  }

  /** Targets the previous sibling, optionally filtered by selector. */
  final case class Previous(selector: Option[String]) extends HxTarget {
    def render: String = selector.fold("previous")(s => "previous " + s)
  }

  /** Targets the CSS selector as-is. */
  final case class Css(selector: String) extends HxTarget {
    def render: String = selector
  }

  /** Creates a `closest ...` selector. */
  def closest(selector: String): HxTarget = Closest(selector)

  /** Creates a `find ...` selector. */
  def find(selector: String): HxTarget = Find(selector)

  /** Targets the next sibling. */
  def next: HxTarget = Next(None)

  /** Targets the next sibling matching the selector. */
  def next(selector: String): HxTarget = Next(Some(selector))

  /** Targets the previous sibling. */
  def previous: HxTarget = Previous(None)

  /** Targets the previous sibling matching the selector. */
  def previous(selector: String): HxTarget = Previous(Some(selector))

  /** Uses a raw CSS selector string. */
  def css(selector: String): HxTarget = Css(selector)

  /** Uses a typed CSS selector. */
  def css(selector: CssSelector): HxTarget = Css(selector.render)

  implicit val toHtmxValue: ToHtmxValue[HxTarget] = new ToHtmxValue[HxTarget] {
    def toHtmxValue(value: HxTarget): String = value.render
  }
}
