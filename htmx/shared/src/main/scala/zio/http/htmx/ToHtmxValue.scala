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

import scala.annotation.implicitNotFound

import _root_.zio.blocks.html.{CssSelector, Js}
import _root_.zio.blocks.schema.json.Json
import _root_.zio.http.{Path, URL}

/**
 * Renders a value as the string that goes into an htmx attribute.
 *
 * Each key of [[HtmxAttributes]] is built with the instance for its value type,
 * and `HtmxAttrKey#:=` uses that instance; it does not look one up at the call
 * site, so an instance for a new type does not make that type assignable to a
 * key. The module has instances for `String`, `Boolean`, `Int`, `Long`,
 * `Double`, [[zio.blocks.html.Js Js]],
 * [[zio.blocks.html.CssSelector CssSelector]], [[zio.http.Path Path]],
 * [[zio.http.URL URL]], [[zio.blocks.schema.json.Json Json]] and the value
 * types of this package, each in its companion.
 *
 * {{{
 * import zio.blocks.html.CssSelector
 *
 * val swap = ToHtmxValue[HxSwap].toHtmxValue(HxSwap.BeforeEnd) // beforeend
 * val selector = ToHtmxValue[CssSelector].toHtmxValue(CssSelector.id("cart")) // #cart
 * }}}
 */
@implicitNotFound("No ToHtmxValue instance found for type ${A}.")
trait ToHtmxValue[-A] {

  /** Returns the attribute string for `value`. */
  def toHtmxValue(value: A): String
}

/** The built-in [[ToHtmxValue]] instances and the [[UrlLike]] type. */
object ToHtmxValue {

  /**
   * The URL types the request keys (`hxGet`, `hxPost`, ...) accept: a `String`,
   * a [[zio.http.Path Path]] or a [[zio.http.URL URL]].
   */
  type UrlLike = String | Path | URL

  /** Summons the [[ToHtmxValue]] instance for `A`. */
  def apply[A](implicit ev: ToHtmxValue[A]): ToHtmxValue[A] = ev

  /**
   * Renders a `String` unchanged, and a `Path` or `URL` with its `encode`
   * method, which percent-encodes it. A `String` is not percent-encoded, so a
   * user's text in its query can add parameters; this is a known bug.
   */
  implicit val urlLikeValue: ToHtmxValue[UrlLike] = new ToHtmxValue[UrlLike] {
    def toHtmxValue(value: UrlLike): String = value match {
      case value: String => value
      case value: Path   => value.encode
      case value: URL    => value.encode
    }
  }

  /** Renders a string unchanged. */
  implicit val stringValue: ToHtmxValue[String] = new ToHtmxValue[String] {
    def toHtmxValue(value: String): String = value
  }

  /** Renders `true` or `false`. */
  implicit val booleanValue: ToHtmxValue[Boolean] = new ToHtmxValue[Boolean] {
    def toHtmxValue(value: Boolean): String = if (value) "true" else "false"
  }

  /** Renders an `Int` with `toString`. */
  implicit val intValue: ToHtmxValue[Int] = new ToHtmxValue[Int] {
    def toHtmxValue(value: Int): String = value.toString
  }

  /** Renders a `Long` with `toString`. */
  implicit val longValue: ToHtmxValue[Long] = new ToHtmxValue[Long] {
    def toHtmxValue(value: Long): String = value.toString
  }

  /**
   * Renders a `Double` with `toString`, so large and small values use exponent
   * notation (`1.0E10`).
   */
  implicit val doubleValue: ToHtmxValue[Double] = new ToHtmxValue[Double] {
    def toHtmxValue(value: Double): String = value.toString
  }

  /** Renders the JavaScript code unchanged. */
  implicit val jsValue: ToHtmxValue[Js] = new ToHtmxValue[Js] {
    def toHtmxValue(value: Js): String = value.value
  }

  /** Renders a selector with `CssSelector#render`. */
  implicit val cssSelectorValue: ToHtmxValue[CssSelector] = new ToHtmxValue[CssSelector] {
    def toHtmxValue(value: CssSelector): String = value.render
  }

  /** Renders a path percent-encoded, with `Path#encode`. */
  implicit val pathValue: ToHtmxValue[Path] = new ToHtmxValue[Path] {
    def toHtmxValue(value: Path): String = value.encode
  }

  /** Renders a URL percent-encoded, with `URL#encode`. */
  implicit val urlValue: ToHtmxValue[URL] = new ToHtmxValue[URL] {
    def toHtmxValue(value: URL): String = value.encode
  }

  /**
   * Renders JSON in compact form with `Json#print`, which leaves non-ASCII
   * characters unescaped.
   */
  implicit val jsonValue: ToHtmxValue[Json] = new ToHtmxValue[Json] {
    def toHtmxValue(value: Json): String = value.print
  }
}
