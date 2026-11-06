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

import _root_.zio.blocks.schema.Schema
import _root_.zio.blocks.schema.json.Json
import _root_.zio.http.{Path, URL}

/**
 * A value for `hx-push-url` and `hx-replace-url`, and for the `HX-Push-Url` and
 * `HX-Replace-Url` response headers: whether htmx updates the browser's address
 * bar after a request, and with which URL.
 *
 * `HxUrlUpdate(true)` uses the request URL, `HxUrlUpdate(false)` turns the
 * update off, and a `String`, [[zio.http.Path Path]] or [[zio.http.URL URL]]
 * sets the URL to show. A `Path` or `URL` is percent-encoded; a `String` is
 * trimmed and otherwise written as given.
 *
 * {{{
 * import zio.blocks.html._
 * import zio.http.Path
 *
 * val next = a(
 *   hxGet := "/articles?page=2",
 *   hxPushUrl := HxUrlUpdate(Path("/articles/page 2"))
 * )("Next").render
 * // <a hx-get="/articles?page=2" hx-push-url="/articles/page%202">Next</a>
 * }}}
 *
 * @see
 *   [[https://zioblocks.com/reference/htmx/hx-url-update/ HxUrlUpdate]]
 */
sealed trait HxUrlUpdate extends Product with Serializable {

  /** Renders this value: `true`, `false` or the URL. */
  def render: String
}

/** The [[HxUrlUpdate]] values, factories and parser. */
object HxUrlUpdate {

  /** `true`: htmx shows the request URL. */
  case object Enabled extends HxUrlUpdate {

    /** Renders `true`. */
    def render: String = "true"
  }

  /** `false`: htmx leaves the address bar unchanged. */
  case object Disabled extends HxUrlUpdate {

    /** Renders `false`. */
    def render: String = "false"
  }

  /** A URL for htmx to show, rendered as stored. */
  final case class Url private[htmx] (value: String) extends HxUrlUpdate {

    /** Renders the stored URL. */
    def render: String = value
  }

  /** Returns [[Enabled]] for `true` and [[Disabled]] for `false`. */
  def apply(value: Boolean): HxUrlUpdate = if (value) Enabled else Disabled

  /**
   * Creates a [[Url]] from a string, which is trimmed and otherwise written as
   * given, without percent-encoding.
   *
   * @throws java.lang.IllegalArgumentException
   *   if `value` is blank
   */
  def apply(value: String): HxUrlUpdate =
    new Url(HtmxSupport.requireNonBlank(value, "HTMX URL update"))

  /** Creates a [[Url]] from a path, percent-encoded with `Path#encode`. */
  def apply(value: Path): HxUrlUpdate = new Url(value.encode)

  /** Creates a [[Url]] from a URL, percent-encoded with `URL#encode`. */
  def apply(value: URL): HxUrlUpdate = new Url(value.encode)

  /**
   * Parses a value: `true` and `false` in any letter case, after trimming, give
   * [[Enabled]] and [[Disabled]]; anything else becomes a trimmed [[Url]]. A
   * blank input gives `Left("HTMX URL update must be non-empty")`.
   */
  def parse(value: String): Either[String, HxUrlUpdate] = value.trim.toLowerCase match {
    case "true"  => Right(Enabled)
    case "false" => Right(Disabled)
    case _       => parseValidated(() => apply(value))
  }

  private def parseValidated(value: () => HxUrlUpdate): Either[String, HxUrlUpdate] =
    try Right(value())
    catch {
      case error: IllegalArgumentException => Left(error.getMessage)
    }

  /** Renders an [[HxUrlUpdate]] with [[HxUrlUpdate#render]]. */
  implicit val toHtmxValue: ToHtmxValue[HxUrlUpdate] = new ToHtmxValue[HxUrlUpdate] {
    def toHtmxValue(value: HxUrlUpdate): String = value.render
  }
}

/**
 * An `hx-vals` value: JSON whose fields htmx adds to the request parameters.
 *
 * Build it with [[HxVals.from]] from any value that has a
 * [[zio.blocks.schema.Schema Schema]], or with [[HxVals.json]] from a
 * [[zio.blocks.schema.json.Json Json]] value. htmx reads the fields of a JSON
 * object; neither method checks that the JSON is an object.
 *
 * {{{
 * import zio.blocks.html._
 * import zio.blocks.schema.Schema
 *
 * final case class Paging(page: Int, size: Int)
 * object Paging {
 *   implicit val schema: Schema[Paging] = Schema.derived
 * }
 *
 * val more = button(hxGet := "/products", hxVals := HxVals.from(Paging(2, 20)))("More").render
 * // <button hx-get="/products" hx-vals="{&quot;page&quot;:2,&quot;size&quot;:20}">More</button>
 * }}}
 *
 * @see
 *   [[https://zioblocks.com/reference/htmx/attribute-values/ Attribute Values and Infrastructure]]
 */
final case class HxVals private (serialized: String)

/** Factories for [[HxVals]]. */
object HxVals {

  /** Encodes `value` to compact JSON with its schema's JSON codec. */
  def from[A](value: A)(implicit schema: Schema[A]): HxVals = HxVals(schema.jsonCodec.encodeToString(value))

  /** Prints `value` as compact JSON. */
  def json(value: Json): HxVals = HxVals(value.print)

  /** Renders the stored JSON text. */
  implicit val toHtmxValue: ToHtmxValue[HxVals] = new ToHtmxValue[HxVals] {
    def toHtmxValue(value: HxVals): String = value.serialized
  }
}

/**
 * An `hx-headers` value: JSON whose fields htmx adds to the request headers.
 *
 * Build it with [[HxHeadersValue.from]] from any value that has a
 * [[zio.blocks.schema.Schema Schema]] (a `Map[String, String]` gives one header
 * per entry), or with [[HxHeadersValue.json]] from a
 * [[zio.blocks.schema.json.Json Json]] value. Neither method checks that the
 * JSON is an object.
 */
final case class HxHeadersValue private (serialized: String)

/** Factories for [[HxHeadersValue]]. */
object HxHeadersValue {

  /** Encodes `value` to compact JSON with its schema's JSON codec. */
  def from[A](value: A)(implicit schema: Schema[A]): HxHeadersValue =
    HxHeadersValue(schema.jsonCodec.encodeToString(value))

  /** Prints `value` as compact JSON. */
  def json(value: Json): HxHeadersValue = HxHeadersValue(value.print)

  /** Renders the stored JSON text. */
  implicit val toHtmxValue: ToHtmxValue[HxHeadersValue] = new ToHtmxValue[HxHeadersValue] {
    def toHtmxValue(value: HxHeadersValue): String = value.serialized
  }
}

/**
 * An `hx-swap-oob` value, set on an element of a response: htmx swaps that
 * element into the page "out of band", outside the request's target.
 *
 * [[HxSwapOob.True]] swaps it into the page element with the same `id`, with
 * `outerHTML`. `HxSwapOob.using(swap)` picks the strategy, and
 * `HxSwapOob.using(swap, selector)` also picks the page element by selector.
 *
 * {{{
 * import zio.blocks.html._
 *
 * val badge = span(id := "cart-count", hxSwapOob := HxSwapOob.True)("3").render
 * // <span id="cart-count" hx-swap-oob="true">3</span>
 *
 * val toast = div(hxSwapOob := HxSwapOob.using(HxSwap.BeforeEnd, HxTarget.css("#toasts")))("Saved").render
 * // <div hx-swap-oob="beforeend:#toasts">Saved</div>
 * }}}
 *
 * @see
 *   [[https://zioblocks.com/reference/htmx/attribute-values/ Attribute Values and Infrastructure]]
 */
final case class HxSwapOob private (rendered: String)

/** The [[HxSwapOob]] values and factories. */
object HxSwapOob {

  /** `true`: swaps the element into the page element with the same `id`. */
  val True: HxSwapOob = HxSwapOob("true")

  /**
   * Renders `false`. htmx 2 has no `false` value: it reads `false` as a swap
   * style, falls back to its default swap and still swaps the element into the
   * page element with the same `id`. This is a known bug; leave the attribute
   * out to keep an element in the main response.
   */
  val False: HxSwapOob = HxSwapOob("false")

  /**
   * Returns [[True]] for `true` and [[False]] for `false`; see [[False]] for
   * how htmx 2 treats `false`.
   */
  def apply(enabled: Boolean): HxSwapOob = if (enabled) True else False

  /**
   * Swaps the element into the page element with the same `id`, using the given
   * strategy, such as `innerHTML`.
   *
   * The whole [[HxSwap]] is rendered, modifiers included. htmx 2 accepts only a
   * bare strategy here, so a swap with a modifier such as `swap(10.millis)`
   * makes htmx drop the whole response; this is a known bug. Pass a swap
   * without modifiers.
   */
  def using(swap: HxSwap): HxSwapOob = HxSwapOob(swap.render)

  /**
   * Swaps the element into the page elements that `selector` matches, using the
   * given strategy, rendered as `<strategy>:<selector>`.
   *
   * htmx 2 looks the selector up from the document, so pass a CSS target such
   * as `HxTarget.css("#toasts")`: a relative one such as
   * `HxTarget.closest("section")` finds nothing and htmx fires
   * `htmx:oobErrorNoTarget`. Modifiers on `swap` break the response as in the
   * one-argument `using`. Both are known bugs.
   */
  def using(swap: HxSwap, selector: HxTarget): HxSwapOob =
    HxSwapOob(swap.render + ":" + selector.render)

  /** Renders the stored value. */
  implicit val toHtmxValue: ToHtmxValue[HxSwapOob] = new ToHtmxValue[HxSwapOob] {
    def toHtmxValue(value: HxSwapOob): String = value.rendered
  }
}

/**
 * An `hx-disinherit` value: htmx attribute names, separated by spaces, that the
 * element's children do not inherit.
 */
final case class HxAttributeNames private (rendered: String)

/** The factory for [[HxAttributeNames]]. */
object HxAttributeNames {

  /**
   * Joins the names with spaces, after trimming each one.
   *
   * @throws java.lang.IllegalArgumentException
   *   if a name is blank
   */
  def apply(first: String, rest: String*): HxAttributeNames =
    HxAttributeNames(validateNames(first +: rest).mkString(" "))

  private def validateNames(names: Seq[String]): Seq[String] =
    names.map(name => HtmxSupport.requireNonBlank(name, "HTMX attribute name"))

  /** Renders the joined names. */
  implicit val toHtmxValue: ToHtmxValue[HxAttributeNames] = new ToHtmxValue[HxAttributeNames] {
    def toHtmxValue(value: HxAttributeNames): String = value.rendered
  }
}

/**
 * An `hx-ext` value: names of htmx extensions to enable on the element,
 * separated by commas.
 */
final case class HxExtensions private (rendered: String)

/** The factory for [[HxExtensions]]. */
object HxExtensions {

  /**
   * Joins the names with commas, after trimming each one.
   *
   * @throws java.lang.IllegalArgumentException
   *   if a name is blank
   */
  def apply(first: String, rest: String*): HxExtensions =
    HxExtensions(validateNames(first +: rest).mkString(","))

  private def validateNames(names: Seq[String]): Seq[String] =
    names.map(name => HtmxSupport.requireNonBlank(name, "HTMX extension name"))

  /** Renders the joined names. */
  implicit val toHtmxValue: ToHtmxValue[HxExtensions] = new ToHtmxValue[HxExtensions] {
    def toHtmxValue(value: HxExtensions): String = value.rendered
  }
}
