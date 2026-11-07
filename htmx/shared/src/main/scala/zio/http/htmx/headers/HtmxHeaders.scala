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

package zio.http.htmx.headers

import _root_.zio.blocks.html.CssSelector
import _root_.zio.blocks.schema.json.Json
import _root_.zio.http.htmx.{HxSwap, HxTarget, HxUrlUpdate}
import _root_.zio.http.{Header, Path, URL}

/**
 * The value of an `HX-Trigger`, `HX-Trigger-After-Swap` or
 * `HX-Trigger-After-Settle` response header: the events htmx fires in the
 * browser.
 *
 * An [[HxEventPayload.Event]] holds event names; htmx 2 splits the value at
 * commas, so `Event("cart-updated, toast")` fires two events. An
 * [[HxEventPayload.JsonValue]] holds a JSON object whose keys are event names
 * and whose values become the events' `detail`.
 *
 * {{{
 * import zio.blocks.schema.json.Json
 * import zio.http.Response
 * import zio.http.htmx.headers._
 *
 * val saved = Json.Object("item-saved" -> Json.Object("sku" -> Json.String("TEA-042")))
 * val response = Response.ok.addHeader(HxTriggerHeader(HxEventPayload.JsonValue(saved)))
 * val value = response.headers.rawGet("hx-trigger") // Some({"item-saved":{"sku":"TEA-042"}})
 * }}}
 *
 * @see
 *   [[https://zioblocks.com/reference/htmx/response-headers/ Request and Response Headers]]
 */
sealed trait HxEventPayload extends Product with Serializable {

  /** Renders the header value. */
  def render: String
}

/** The [[HxEventPayload]] cases and their parser. */
object HxEventPayload {

  /**
   * One or more event names, rendered as given; htmx 2 splits the value at
   * commas.
   */
  final case class Event(name: String) extends HxEventPayload {

    /** Renders the name unchanged. */
    def render: String = name
  }

  /**
   * Events with data, as JSON, rendered in compact form.
   *
   * htmx 2 reads the value as JSON only when it starts with `{`, but any
   * [[zio.blocks.schema.json.Json Json]] is accepted, so an array renders and
   * then fires no useful event (a known bug). Non-ASCII characters are written
   * unescaped into the header value, which some servers reject (a known bug).
   */
  final case class JsonValue(json: Json) extends HxEventPayload {

    /** Renders the JSON with `Json#print`. */
    def render: String = json.print
  }

  /**
   * Parses a header value, after trimming it. A value that starts with `{` or
   * `[` is parsed as JSON into a [[JsonValue]] (a `Left` with the parser's
   * message if it is malformed); any other non-blank value becomes one
   * [[Event]], commas included. A blank value gives
   * `Left("HTMX event name must be non-empty")`.
   */
  def parse(value: String): Either[String, HxEventPayload] = {
    val trimmed = value.trim
    if (trimmed.startsWith("{") || trimmed.startsWith("[")) Json.parse(trimmed).left.map(_.message).map(JsonValue.apply)
    else if (trimmed.isEmpty) Left("HTMX event name must be non-empty")
    else Right(Event(trimmed))
  }
}

/** Parsing helpers shared by the header codecs. */
private[headers] object HtmxHeaderSupport {
  def parseBoolean(value: String): Either[String, Boolean] = value.trim.toLowerCase match {
    case "true"  => Right(true)
    case "false" => Right(false)
    case other   => Left("Invalid HTMX boolean header value: " + other)
  }

  def parseUrl(value: String): Either[String, String] = {
    val trimmed = value.trim
    if (trimmed.isEmpty) Left("Empty URL value")
    else Right(trimmed)
  }

  def parseUrlUpdate(value: String): Either[String, HxUrlUpdate] = HxUrlUpdate.parse(value)

  def parseNonEmpty(value: String, label: String): Either[String, String] = {
    val trimmed = value.trim
    if (trimmed.isEmpty) Left(label + " must be non-empty")
    else Right(trimmed)
  }
}

/**
 * The `HX-Request` request header, which htmx sends as `true` with each of its
 * requests.
 *
 * A server often answers an htmx request with a fragment and other requests
 * with the full page. htmx 2 also sends `HX-Request: true` when it fetches a
 * page after a history cache miss ([[HxHistoryRestoreRequest]]) and for a
 * boosted link or form ([[HxBoosted]]), and both need the full page, so check
 * all three. Answering every `HX-Request` with a fragment breaks the Back
 * button and boosted links (a known bug of that pattern).
 *
 * {{{
 * import zio.http.Request
 * import zio.http.htmx.headers._
 *
 * def wantsFragment(request: Request): Boolean =
 *   request.header(HxRequest).exists(_.enabled) &&
 *     !request.header(HxHistoryRestoreRequest).exists(_.enabled) &&
 *     !request.header(HxBoosted).exists(_.enabled)
 * }}}
 *
 * @see
 *   [[https://zioblocks.com/reference/htmx/response-headers/ Request and Response Headers]]
 */
final case class HxRequest(enabled: Boolean) extends Header {

  /** Returns [[HxRequest.name]]. */
  def headerName: String = HxRequest.name

  /** Returns `true` or `false`. */
  def renderedValue: String = if (enabled) "true" else "false"
}

/**
 * The codec for the `HX-Request` header: its [[name]], [[parse]] and
 * [[render]]. Pass it to `Request#header` or `Response#header` to read the
 * header.
 */
object HxRequest extends Header.Typed[HxRequest] {

  /** The header name, in lower case: `hx-request`. */
  val name: String = "hx-request"

  /**
   * Parses `true` or `false` in any letter case, after trimming; any other
   * value gives a `Left` starting with `Invalid HTMX boolean header value:`.
   */
  def parse(value: String): Either[String, HxRequest] = HtmxHeaderSupport.parseBoolean(value).map(HxRequest.apply)

  /** Renders `true` or `false`. */
  def render(h: HxRequest): String = h.renderedValue
}

/**
 * The `HX-Boosted` request header, which htmx sends as `true` for a request
 * from a link or form under `hx-boost`. htmx swaps the answer into the page
 * body, so it needs the full page.
 */
final case class HxBoosted(enabled: Boolean) extends Header {

  /** Returns [[HxBoosted.name]]. */
  def headerName: String = HxBoosted.name

  /** Returns `true` or `false`. */
  def renderedValue: String = if (enabled) "true" else "false"
}

/**
 * The codec for the `HX-Boosted` header: its [[name]], [[parse]] and
 * [[render]]. Pass it to `Request#header` or `Response#header` to read the
 * header.
 */
object HxBoosted extends Header.Typed[HxBoosted] {

  /** The header name, in lower case: `hx-boosted`. */
  val name: String = "hx-boosted"

  /**
   * Parses `true` or `false` in any letter case, after trimming; any other
   * value gives a `Left` starting with `Invalid HTMX boolean header value:`.
   */
  def parse(value: String): Either[String, HxBoosted] = HtmxHeaderSupport.parseBoolean(value).map(HxBoosted.apply)

  /** Renders `true` or `false`. */
  def render(h: HxBoosted): String = h.renderedValue
}

/** The `HX-Current-URL` request header: the browser's current URL. */
final case class HxCurrentUrl(value: String) extends Header {

  /** Returns [[HxCurrentUrl.name]]. */
  def headerName: String = HxCurrentUrl.name

  /** Returns the stored value unchanged. */
  def renderedValue: String = value
}

/**
 * The codec for the `HX-Current-URL` header: its [[name]], [[parse]] and
 * [[render]]. Pass it to `Request#header` or `Response#header` to read the
 * header.
 */
object HxCurrentUrl extends Header.Typed[HxCurrentUrl] {

  /** The header name, in lower case: `hx-current-url`. */
  val name: String = "hx-current-url"

  /**
   * Parses any non-blank value, trimmed and not checked as a URL; a blank one
   * gives `Left("Empty URL value")`.
   */
  def parse(value: String): Either[String, HxCurrentUrl] = HtmxHeaderSupport.parseUrl(value).map(HxCurrentUrl.apply)

  /** Renders the stored value unchanged. */
  def render(h: HxCurrentUrl): String = h.renderedValue

  /** Creates the header from a URL, percent-encoded with `URL#encode`. */
  def apply(value: URL): HxCurrentUrl = new HxCurrentUrl(value.encode)
}

/**
 * The `HX-Target` request header: the `id` of the request's target element,
 * which htmx sends when the target has one. Not to be confused with the
 * [[zio.http.htmx.HxTarget]] attribute value.
 */
final case class HxTargetId(value: String) extends Header {

  /** Returns [[HxTargetId.name]]. */
  def headerName: String = HxTargetId.name

  /** Returns the stored value unchanged. */
  def renderedValue: String = value
}

/**
 * The codec for the `HX-Target` header: its [[name]], [[parse]] and [[render]].
 * Pass it to `Request#header` or `Response#header` to read the header.
 */
object HxTargetId extends Header.Typed[HxTargetId] {

  /** The header name, in lower case: `hx-target`. */
  val name: String = "hx-target"

  /**
   * Parses any non-blank value, trimmed; a blank one gives
   * `Left("HX-Target must be non-empty")`.
   */
  def parse(value: String): Either[String, HxTargetId] =
    HtmxHeaderSupport.parseNonEmpty(value, "HX-Target").map(HxTargetId.apply)

  /** Renders the stored value unchanged. */
  def render(h: HxTargetId): String = h.renderedValue
}

/**
 * The `HX-Trigger` request header: the `id` of the element that triggered the
 * request, which htmx sends when the element has one. The response header of
 * the same name is [[HxTriggerHeader]].
 */
final case class HxTriggerId(value: String) extends Header {

  /** Returns [[HxTriggerId.name]]. */
  def headerName: String = HxTriggerId.name

  /** Returns the stored value unchanged. */
  def renderedValue: String = value
}

/**
 * The codec for the `HX-Trigger` header: its [[name]], [[parse]] and
 * [[render]]. Pass it to `Request#header` or `Response#header` to read the
 * header.
 */
object HxTriggerId extends Header.Typed[HxTriggerId] {

  /** The header name, in lower case: `hx-trigger`. */
  val name: String = "hx-trigger"

  /**
   * Parses any non-blank value, trimmed; a blank one gives
   * `Left("HX-Trigger must be non-empty")`.
   */
  def parse(value: String): Either[String, HxTriggerId] =
    HtmxHeaderSupport.parseNonEmpty(value, "HX-Trigger").map(HxTriggerId.apply)

  /** Renders the stored value unchanged. */
  def render(h: HxTriggerId): String = h.renderedValue
}

/**
 * The `HX-Trigger-Name` request header: the `name` of the element that
 * triggered the request, which htmx sends when the element has one.
 */
final case class HxTriggerName(value: String) extends Header {

  /** Returns [[HxTriggerName.name]]. */
  def headerName: String = HxTriggerName.name

  /** Returns the stored value unchanged. */
  def renderedValue: String = value
}

/**
 * The codec for the `HX-Trigger-Name` header: its [[name]], [[parse]] and
 * [[render]]. Pass it to `Request#header` or `Response#header` to read the
 * header.
 */
object HxTriggerName extends Header.Typed[HxTriggerName] {

  /** The header name, in lower case: `hx-trigger-name`. */
  val name: String = "hx-trigger-name"

  /**
   * Parses any non-blank value, trimmed; a blank one gives
   * `Left("HX-Trigger-Name must be non-empty")`.
   */
  def parse(value: String): Either[String, HxTriggerName] =
    HtmxHeaderSupport.parseNonEmpty(value, "HX-Trigger-Name").map(HxTriggerName.apply)

  /** Renders the stored value unchanged. */
  def render(h: HxTriggerName): String = h.renderedValue
}

/**
 * The `HX-History-Restore-Request` request header, which htmx 2 sends as `true`
 * when it fetches a page after a miss in its history cache, such as on the Back
 * button. htmx swaps the answer into the page body, so it needs the full page;
 * see [[HxRequest]].
 */
final case class HxHistoryRestoreRequest(enabled: Boolean) extends Header {

  /** Returns [[HxHistoryRestoreRequest.name]]. */
  def headerName: String = HxHistoryRestoreRequest.name

  /** Returns `true` or `false`. */
  def renderedValue: String = if (enabled) "true" else "false"
}

/**
 * The codec for the `HX-History-Restore-Request` header: its [[name]],
 * [[parse]] and [[render]]. Pass it to `Request#header` or `Response#header` to
 * read the header.
 */
object HxHistoryRestoreRequest extends Header.Typed[HxHistoryRestoreRequest] {

  /** The header name, in lower case: `hx-history-restore-request`. */
  val name: String = "hx-history-restore-request"

  /**
   * Parses `true` or `false` in any letter case, after trimming; any other
   * value gives a `Left` starting with `Invalid HTMX boolean header value:`.
   */
  def parse(value: String): Either[String, HxHistoryRestoreRequest] =
    HtmxHeaderSupport.parseBoolean(value).map(HxHistoryRestoreRequest.apply)

  /** Renders `true` or `false`. */
  def render(h: HxHistoryRestoreRequest): String = h.renderedValue
}

/**
 * The `HX-Prompt` request header: the user's answer to the `hx-prompt`
 * question.
 */
final case class HxPrompt(value: String) extends Header {

  /** Returns [[HxPrompt.name]]. */
  def headerName: String = HxPrompt.name

  /** Returns the stored value unchanged. */
  def renderedValue: String = value
}

/**
 * The codec for the `HX-Prompt` header: its [[name]], [[parse]] and [[render]].
 * Pass it to `Request#header` or `Response#header` to read the header.
 */
object HxPrompt extends Header.Typed[HxPrompt] {

  /** The header name, in lower case: `hx-prompt`. */
  val name: String = "hx-prompt"

  /** Parses any value, trimmed; an empty answer is accepted. */
  def parse(value: String): Either[String, HxPrompt] = Right(HxPrompt(value.trim))

  /** Renders the stored value unchanged. */
  def render(h: HxPrompt): String = h.renderedValue
}

/**
 * The `HX-Location` response header: htmx loads another URL with an htmx
 * request, without a full page reload, and pushes it into the history.
 *
 * The value is a JSON object with a `path` and optional fields such as `target`
 * and `swap`. `HxLocation(path, target, swap)` builds the common case; the case
 * class constructor takes any [[zio.blocks.schema.json.Json]] object. htmx 2
 * resolves `target` from the document, so pass a CSS target: a relative one
 * such as `HxTarget.closest("main")` makes htmx fire `htmx:targetError` and do
 * nothing (a known bug). Non-ASCII characters are written unescaped into the
 * header value, which some servers reject (a known bug).
 *
 * {{{
 * import zio.http.Response
 * import zio.http.htmx.headers._
 *
 * val response = Response.ok.addHeader(
 *   HxLocation("/orders/1001", target = Some(HxTarget.css("#main")), swap = Some(HxSwap.OuterHTML))
 * )
 * val value = response.headers.rawGet("hx-location")
 * // Some({"path":"/orders/1001","target":"#main","swap":"outerHTML"})
 * }}}
 *
 * @see
 *   [[https://zioblocks.com/reference/htmx/response-headers/ Request and Response Headers]]
 */
final case class HxLocation(value: Json.Object) extends Header {

  /** Returns [[HxLocation.name]]. */
  def headerName: String = HxLocation.name

  /** Returns the JSON object in compact form. */
  def renderedValue: String = value.print
}

/**
 * The codec for the `HX-Location` header: its [[name]], [[parse]] and
 * [[render]]. Pass it to `Request#header` or `Response#header` to read the
 * header.
 */
object HxLocation extends Header.Typed[HxLocation] {

  /** The header name, in lower case: `hx-location`. */
  val name: String = "hx-location"

  /**
   * Parses a JSON object. Malformed JSON gives a `Left` with the parser's
   * message, and other JSON, such as an array or a string, gives
   * `Left("HX-Location must be a JSON object")`.
   */
  def parse(value: String): Either[String, HxLocation] =
    Json.parse(value).left.map(_.message).flatMap {
      case obj: Json.Object => Right(HxLocation(obj))
      case _                => Left("HX-Location must be a JSON object")
    }

  /** Renders the JSON object in compact form. */
  def render(h: HxLocation): String = h.renderedValue

  /**
   * Builds the JSON object `{"path": ...}`, with `target` and `swap` fields
   * when they are given, rendered with their `render` methods. `path` is
   * written as given, without percent-encoding or a check that it is non-empty.
   */
  def apply(path: String, target: Option[HxTarget] = None, swap: Option[HxSwap] = None): HxLocation = {
    val fields = scala.collection.mutable.ListBuffer[(String, Json)]("path" -> Json.String(path))
    target.foreach(v => fields += (("target", Json.String(v.render))))
    swap.foreach(v => fields += (("swap", Json.String(v.render))))
    new HxLocation(Json.Object(fields.toList: _*))
  }

  /**
   * Builds the header from a path, percent-encoded with `Path#encode`; see the
   * `String` overload.
   */
  def apply(path: Path, target: Option[HxTarget], swap: Option[HxSwap]): HxLocation =
    apply(path.encode, target, swap)

  /**
   * Builds the header from a URL, percent-encoded with `URL#encode`; see the
   * `String` overload.
   */
  def apply(path: URL, target: Option[HxTarget], swap: Option[HxSwap]): HxLocation =
    apply(path.encode, target, swap)
}

/**
 * The `HX-Push-Url` response header: the URL htmx pushes into the browser
 * history, `true` for the request URL, or `false` to push nothing.
 */
final case class HxPushUrl(value: HxUrlUpdate) extends Header {

  /** Returns [[HxPushUrl.name]]. */
  def headerName: String = HxPushUrl.name

  /** Returns the rendered [[zio.http.htmx.HxUrlUpdate]]. */
  def renderedValue: String = value.render
}

/**
 * The codec for the `HX-Push-Url` header: its [[name]], [[parse]] and
 * [[render]]. Pass it to `Request#header` or `Response#header` to read the
 * header.
 */
object HxPushUrl extends Header.Typed[HxPushUrl] {

  /** The header name, in lower case: `hx-push-url`. */
  val name: String = "hx-push-url"

  /**
   * Parses the value with [[zio.http.htmx.HxUrlUpdate.parse]]: `true` or
   * `false` in any letter case, or else a non-blank URL.
   */
  def parse(value: String): Either[String, HxPushUrl] = HtmxHeaderSupport.parseUrlUpdate(value).map(HxPushUrl.apply)

  /** Renders the [[zio.http.htmx.HxUrlUpdate]]. */
  def render(h: HxPushUrl): String = h.renderedValue
}

/**
 * The `HX-Replace-Url` response header: the URL htmx puts in place of the
 * current history entry, `true` for the request URL, or `false` to change
 * nothing.
 */
final case class HxReplaceUrl(value: HxUrlUpdate) extends Header {

  /** Returns [[HxReplaceUrl.name]]. */
  def headerName: String = HxReplaceUrl.name

  /** Returns the rendered [[zio.http.htmx.HxUrlUpdate]]. */
  def renderedValue: String = value.render
}

/**
 * The codec for the `HX-Replace-Url` header: its [[name]], [[parse]] and
 * [[render]]. Pass it to `Request#header` or `Response#header` to read the
 * header.
 */
object HxReplaceUrl extends Header.Typed[HxReplaceUrl] {

  /** The header name, in lower case: `hx-replace-url`. */
  val name: String = "hx-replace-url"

  /**
   * Parses the value with [[zio.http.htmx.HxUrlUpdate.parse]]: `true` or
   * `false` in any letter case, or else a non-blank URL.
   */
  def parse(value: String): Either[String, HxReplaceUrl] =
    HtmxHeaderSupport.parseUrlUpdate(value).map(HxReplaceUrl.apply)

  /** Renders the [[zio.http.htmx.HxUrlUpdate]]. */
  def render(h: HxReplaceUrl): String = h.renderedValue
}

/**
 * The `HX-Redirect` response header: htmx navigates the browser to this URL
 * with a full page load.
 */
final case class HxRedirect(value: String) extends Header {

  /** Returns [[HxRedirect.name]]. */
  def headerName: String = HxRedirect.name

  /** Returns the stored value unchanged. */
  def renderedValue: String = value
}

/**
 * The codec for the `HX-Redirect` header: its [[name]], [[parse]] and
 * [[render]]. Pass it to `Request#header` or `Response#header` to read the
 * header.
 */
object HxRedirect extends Header.Typed[HxRedirect] {

  /** The header name, in lower case: `hx-redirect`. */
  val name: String = "hx-redirect"

  /**
   * Parses any non-blank value, trimmed and not checked as a URL; a blank one
   * gives `Left("Empty URL value")`.
   */
  def parse(value: String): Either[String, HxRedirect] = HtmxHeaderSupport.parseUrl(value).map(HxRedirect.apply)

  /** Renders the stored value unchanged. */
  def render(h: HxRedirect): String = h.renderedValue

  /** Creates the header from a URL, percent-encoded with `URL#encode`. */
  def apply(value: URL): HxRedirect = new HxRedirect(value.encode)
}

/**
 * The `HX-Refresh` response header: with `true`, htmx reloads the whole page.
 */
final case class HxRefresh(enabled: Boolean) extends Header {

  /** Returns [[HxRefresh.name]]. */
  def headerName: String = HxRefresh.name

  /** Returns `true` or `false`. */
  def renderedValue: String = if (enabled) "true" else "false"
}

/**
 * The codec for the `HX-Refresh` header: its [[name]], [[parse]] and
 * [[render]]. Pass it to `Request#header` or `Response#header` to read the
 * header.
 */
object HxRefresh extends Header.Typed[HxRefresh] {

  /** The header name, in lower case: `hx-refresh`. */
  val name: String = "hx-refresh"

  /**
   * Parses `true` or `false` in any letter case, after trimming; any other
   * value gives a `Left` starting with `Invalid HTMX boolean header value:`.
   */
  def parse(value: String): Either[String, HxRefresh] = HtmxHeaderSupport.parseBoolean(value).map(HxRefresh.apply)

  /** Renders `true` or `false`. */
  def render(h: HxRefresh): String = h.renderedValue
}

/**
 * The `HX-Reswap` response header: an [[zio.http.htmx.HxSwap]] that replaces
 * the `hx-swap` of the element that sent the request.
 */
final case class HxReswap(value: HxSwap) extends Header {

  /** Returns [[HxReswap.name]]. */
  def headerName: String = HxReswap.name

  /** Returns the rendered swap. */
  def renderedValue: String = value.render
}

/**
 * The codec for the `HX-Reswap` header: its [[name]], [[parse]] and [[render]].
 * Pass it to `Request#header` or `Response#header` to read the header.
 */
object HxReswap extends Header.Typed[HxReswap] {

  /** The header name, in lower case: `hx-reswap`. */
  val name: String = "hx-reswap"

  /** Parses the value with [[zio.http.htmx.HxSwap.parse]]. */
  def parse(value: String): Either[String, HxReswap] = HxSwap.parse(value).map(HxReswap.apply)

  /** Renders the swap with `HxSwap#render`. */
  def render(h: HxReswap): String = h.renderedValue
}

/**
 * The `HX-Retarget` response header: a CSS selector for the element the
 * response goes into, in place of the request's target.
 */
final case class HxRetarget(value: CssSelector) extends Header {

  /** Returns [[HxRetarget.name]]. */
  def headerName: String = HxRetarget.name

  /** Returns the rendered selector. */
  def renderedValue: String = value.render
}

/**
 * The codec for the `HX-Retarget` header: its [[name]], [[parse]] and
 * [[render]]. Pass it to `Request#header` or `Response#header` to read the
 * header.
 */
object HxRetarget extends Header.Typed[HxRetarget] {

  /** The header name, in lower case: `hx-retarget`. */
  val name: String = "hx-retarget"

  /**
   * Wraps the trimmed value in `CssSelector.raw` without checking it; an empty
   * value is accepted.
   */
  def parse(value: String): Either[String, HxRetarget] = Right(HxRetarget(CssSelector.raw(value.trim)))

  /** Renders the selector. */
  def render(h: HxRetarget): String = h.renderedValue
}

/**
 * The `HX-Reselect` response header: a CSS selector for the part of the
 * response htmx swaps in, in place of the element's `hx-select`.
 */
final case class HxReselect(value: CssSelector) extends Header {

  /** Returns [[HxReselect.name]]. */
  def headerName: String = HxReselect.name

  /** Returns the rendered selector. */
  def renderedValue: String = value.render
}

/**
 * The codec for the `HX-Reselect` header: its [[name]], [[parse]] and
 * [[render]]. Pass it to `Request#header` or `Response#header` to read the
 * header.
 */
object HxReselect extends Header.Typed[HxReselect] {

  /** The header name, in lower case: `hx-reselect`. */
  val name: String = "hx-reselect"

  /**
   * Wraps the trimmed value in `CssSelector.raw` without checking it; an empty
   * value is accepted.
   */
  def parse(value: String): Either[String, HxReselect] = Right(HxReselect(CssSelector.raw(value.trim)))

  /** Renders the selector. */
  def render(h: HxReselect): String = h.renderedValue
}

/**
 * The `HX-Trigger` response header: events htmx fires in the browser as soon as
 * the response arrives; see [[HxEventPayload]].
 *
 * It is named `HxTriggerHeader` so that it does not clash with the attribute
 * value [[zio.http.htmx.HxTrigger]] when both packages are imported with `_`.
 * The request header of the same name is [[HxTriggerId]].
 */
final case class HxTriggerHeader(value: HxEventPayload) extends Header {

  /** Returns [[HxTriggerHeader.name]]. */
  def headerName: String = HxTriggerHeader.name

  /** Returns the rendered payload. */
  def renderedValue: String = value.render
}

/**
 * The codec for the `HX-Trigger` header: its [[name]], [[parse]] and
 * [[render]]. Pass it to `Request#header` or `Response#header` to read the
 * header.
 */
object HxTriggerHeader extends Header.Typed[HxTriggerHeader] {

  /** The header name, in lower case: `hx-trigger`. */
  val name: String = "hx-trigger"

  /** Parses the value with [[HxEventPayload.parse]]. */
  def parse(value: String): Either[String, HxTriggerHeader] = HxEventPayload.parse(value).map(HxTriggerHeader.apply)

  /** Renders the payload with `HxEventPayload#render`. */
  def render(h: HxTriggerHeader): String = h.renderedValue
}

/**
 * The `HX-Trigger-After-Settle` response header: events htmx fires after the
 * settle step; see [[HxEventPayload]].
 */
final case class HxTriggerAfterSettle(value: HxEventPayload) extends Header {

  /** Returns [[HxTriggerAfterSettle.name]]. */
  def headerName: String = HxTriggerAfterSettle.name

  /** Returns the rendered payload. */
  def renderedValue: String = value.render
}

/**
 * The codec for the `HX-Trigger-After-Settle` header: its [[name]], [[parse]]
 * and [[render]]. Pass it to `Request#header` or `Response#header` to read the
 * header.
 */
object HxTriggerAfterSettle extends Header.Typed[HxTriggerAfterSettle] {

  /** The header name, in lower case: `hx-trigger-after-settle`. */
  val name: String = "hx-trigger-after-settle"

  /** Parses the value with [[HxEventPayload.parse]]. */
  def parse(value: String): Either[String, HxTriggerAfterSettle] =
    HxEventPayload.parse(value).map(HxTriggerAfterSettle.apply)

  /** Renders the payload with `HxEventPayload#render`. */
  def render(h: HxTriggerAfterSettle): String = h.renderedValue
}

/**
 * The `HX-Trigger-After-Swap` response header: events htmx fires after the
 * swap; see [[HxEventPayload]].
 */
final case class HxTriggerAfterSwap(value: HxEventPayload) extends Header {

  /** Returns [[HxTriggerAfterSwap.name]]. */
  def headerName: String = HxTriggerAfterSwap.name

  /** Returns the rendered payload. */
  def renderedValue: String = value.render
}

/**
 * The codec for the `HX-Trigger-After-Swap` header: its [[name]], [[parse]] and
 * [[render]]. Pass it to `Request#header` or `Response#header` to read the
 * header.
 */
object HxTriggerAfterSwap extends Header.Typed[HxTriggerAfterSwap] {

  /** The header name, in lower case: `hx-trigger-after-swap`. */
  val name: String = "hx-trigger-after-swap"

  /** Parses the value with [[HxEventPayload.parse]]. */
  def parse(value: String): Either[String, HxTriggerAfterSwap] =
    HxEventPayload.parse(value).map(HxTriggerAfterSwap.apply)

  /** Renders the payload with `HxEventPayload#render`. */
  def render(h: HxTriggerAfterSwap): String = h.renderedValue
}
