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

package zio.blocks.htmx.headers

import zio.blocks.html.CssSelector
import zio.blocks.htmx.{HxSwap, HxTarget, HxUrlUpdate}
import zio.blocks.schema.json.Json
import zio.http.{Header, Path, URL}

/**
 * Payload carried by HTMX response-trigger headers.
 *
 * Values can either be plain event names or JSON payloads that HTMX forwards to
 * client-side listeners.
 */
sealed trait HxEventPayload extends Product with Serializable {
  def render: String
}

object HxEventPayload {
  final case class Event(name: String) extends HxEventPayload {
    def render: String = name
  }

  final case class JsonValue(json: Json) extends HxEventPayload {
    def render: String = json.print
  }

  def parse(value: String): Either[String, HxEventPayload] = {
    val trimmed = value.trim
    if (trimmed.startsWith("{") || trimmed.startsWith("[")) Json.parse(trimmed).left.map(_.message).map(JsonValue.apply)
    else if (trimmed.isEmpty) Left("HTMX event name must be non-empty")
    else Right(Event(trimmed))
  }
}

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

  def parseUrlUpdate(value: String): Either[String, HxUrlUpdate] = value.trim.toLowerCase match {
    case "true"  => Right(HxUrlUpdate.Enabled)
    case "false" => Right(HxUrlUpdate.Disabled)
    case _       => parseUrl(value).map(HxUrlUpdate.Url.apply)
  }
}

/** Typed `HX-Request` request header. */
final case class HxRequest(enabled: Boolean) extends Header {
  def headerName: String    = HxRequest.name
  def renderedValue: String = if (enabled) "true" else "false"
}

object HxRequest extends Header.Typed[HxRequest] {
  val name: String                                    = "hx-request"
  def parse(value: String): Either[String, HxRequest] = HtmxHeaderSupport.parseBoolean(value).map(HxRequest.apply)
  def render(h: HxRequest): String                    = h.renderedValue
}

/** Typed `HX-Boosted` request header. */
final case class HxBoosted(enabled: Boolean) extends Header {
  def headerName: String    = HxBoosted.name
  def renderedValue: String = if (enabled) "true" else "false"
}

object HxBoosted extends Header.Typed[HxBoosted] {
  val name: String                                    = "hx-boosted"
  def parse(value: String): Either[String, HxBoosted] = HtmxHeaderSupport.parseBoolean(value).map(HxBoosted.apply)
  def render(h: HxBoosted): String                    = h.renderedValue
}

/** Typed `HX-Current-URL` request header. */
final case class HxCurrentUrl(value: String) extends Header {
  def headerName: String    = HxCurrentUrl.name
  def renderedValue: String = value
}

object HxCurrentUrl extends Header.Typed[HxCurrentUrl] {
  val name: String                                       = "hx-current-url"
  def parse(value: String): Either[String, HxCurrentUrl] = HtmxHeaderSupport.parseUrl(value).map(HxCurrentUrl.apply)
  def render(h: HxCurrentUrl): String                    = h.renderedValue
  def apply(value: URL): HxCurrentUrl                    = new HxCurrentUrl(value.encode)
}

/** Typed `HX-Target` request header carrying the target element id. */
final case class HxTargetId(value: String) extends Header {
  def headerName: String    = HxTargetId.name
  def renderedValue: String = value
}

object HxTargetId extends Header.Typed[HxTargetId] {
  val name: String                                     = "hx-target"
  def parse(value: String): Either[String, HxTargetId] = Right(HxTargetId(value.trim))
  def render(h: HxTargetId): String                    = h.renderedValue
}

/** Typed `HX-Trigger` request header carrying the triggering element id. */
final case class HxTriggerId(value: String) extends Header {
  def headerName: String    = HxTriggerId.name
  def renderedValue: String = value
}

object HxTriggerId extends Header.Typed[HxTriggerId] {
  val name: String                                      = "hx-trigger"
  def parse(value: String): Either[String, HxTriggerId] = Right(HxTriggerId(value.trim))
  def render(h: HxTriggerId): String                    = h.renderedValue
}

/**
 * Typed `HX-Trigger-Name` request header carrying the triggering input name.
 */
final case class HxTriggerName(value: String) extends Header {
  def headerName: String    = HxTriggerName.name
  def renderedValue: String = value
}

object HxTriggerName extends Header.Typed[HxTriggerName] {
  val name: String                                        = "hx-trigger-name"
  def parse(value: String): Either[String, HxTriggerName] = Right(HxTriggerName(value.trim))
  def render(h: HxTriggerName): String                    = h.renderedValue
}

/** Typed `HX-History-Restore-Request` request header. */
final case class HxHistoryRestoreRequest(enabled: Boolean) extends Header {
  def headerName: String    = HxHistoryRestoreRequest.name
  def renderedValue: String = if (enabled) "true" else "false"
}

object HxHistoryRestoreRequest extends Header.Typed[HxHistoryRestoreRequest] {
  val name: String                                                  = "hx-history-restore-request"
  def parse(value: String): Either[String, HxHistoryRestoreRequest] =
    HtmxHeaderSupport.parseBoolean(value).map(HxHistoryRestoreRequest.apply)
  def render(h: HxHistoryRestoreRequest): String = h.renderedValue
}

/** Typed `HX-Prompt` request header. */
final case class HxPrompt(value: String) extends Header {
  def headerName: String    = HxPrompt.name
  def renderedValue: String = value
}

object HxPrompt extends Header.Typed[HxPrompt] {
  val name: String                                   = "hx-prompt"
  def parse(value: String): Either[String, HxPrompt] = Right(HxPrompt(value.trim))
  def render(h: HxPrompt): String                    = h.renderedValue
}

/** Typed `HX-Location` response header. */
final case class HxLocation(value: Json.Object) extends Header {
  def headerName: String    = HxLocation.name
  def renderedValue: String = value.print
}

object HxLocation extends Header.Typed[HxLocation] {
  val name: String = "hx-location"

  def parse(value: String): Either[String, HxLocation] =
    Json.parse(value).left.map(_.message).flatMap {
      case obj: Json.Object => Right(HxLocation(obj))
      case _                => Left("HX-Location must be a JSON object")
    }

  def render(h: HxLocation): String = h.renderedValue

  def apply(path: String, target: Option[HxTarget] = None, swap: Option[HxSwap] = None): HxLocation = {
    val fields = scala.collection.mutable.ListBuffer[(String, Json)]("path" -> Json.String(path))
    target.foreach(v => fields += (("target", Json.String(v.render))))
    swap.foreach(v => fields += (("swap", Json.String(v.render))))
    new HxLocation(Json.Object(fields.toList: _*))
  }

  def apply(path: Path, target: Option[HxTarget], swap: Option[HxSwap]): HxLocation =
    apply(path.encode, target, swap)

  def apply(path: URL, target: Option[HxTarget], swap: Option[HxSwap]): HxLocation =
    apply(path.encode, target, swap)
}

/** Typed `HX-Push-Url` response header. */
final case class HxPushUrl(value: HxUrlUpdate) extends Header {
  def headerName: String    = HxPushUrl.name
  def renderedValue: String = value.render
}

object HxPushUrl extends Header.Typed[HxPushUrl] {
  val name: String                                    = "hx-push-url"
  def parse(value: String): Either[String, HxPushUrl] = HtmxHeaderSupport.parseUrlUpdate(value).map(HxPushUrl.apply)
  def render(h: HxPushUrl): String                    = h.renderedValue
}

/** Typed `HX-Replace-Url` response header. */
final case class HxReplaceUrl(value: HxUrlUpdate) extends Header {
  def headerName: String    = HxReplaceUrl.name
  def renderedValue: String = value.render
}

object HxReplaceUrl extends Header.Typed[HxReplaceUrl] {
  val name: String                                       = "hx-replace-url"
  def parse(value: String): Either[String, HxReplaceUrl] =
    HtmxHeaderSupport.parseUrlUpdate(value).map(HxReplaceUrl.apply)
  def render(h: HxReplaceUrl): String = h.renderedValue
}

/** Typed `HX-Redirect` response header. */
final case class HxRedirect(value: String) extends Header {
  def headerName: String    = HxRedirect.name
  def renderedValue: String = value
}

object HxRedirect extends Header.Typed[HxRedirect] {
  val name: String                                     = "hx-redirect"
  def parse(value: String): Either[String, HxRedirect] = HtmxHeaderSupport.parseUrl(value).map(HxRedirect.apply)
  def render(h: HxRedirect): String                    = h.renderedValue
  def apply(value: URL): HxRedirect                    = new HxRedirect(value.encode)
}

/** Typed `HX-Refresh` response header. */
final case class HxRefresh(enabled: Boolean) extends Header {
  def headerName: String    = HxRefresh.name
  def renderedValue: String = if (enabled) "true" else "false"
}

object HxRefresh extends Header.Typed[HxRefresh] {
  val name: String                                    = "hx-refresh"
  def parse(value: String): Either[String, HxRefresh] = HtmxHeaderSupport.parseBoolean(value).map(HxRefresh.apply)
  def render(h: HxRefresh): String                    = h.renderedValue
}

/** Typed `HX-Reswap` response header. */
final case class HxReswap(value: HxSwap) extends Header {
  def headerName: String    = HxReswap.name
  def renderedValue: String = value.render
}

object HxReswap extends Header.Typed[HxReswap] {
  val name: String                                   = "hx-reswap"
  def parse(value: String): Either[String, HxReswap] = HxSwap.parse(value).map(HxReswap.apply)
  def render(h: HxReswap): String                    = h.renderedValue
}

/** Typed `HX-Retarget` response header. */
final case class HxRetarget(value: CssSelector) extends Header {
  def headerName: String    = HxRetarget.name
  def renderedValue: String = value.render
}

object HxRetarget extends Header.Typed[HxRetarget] {
  val name: String                                     = "hx-retarget"
  def parse(value: String): Either[String, HxRetarget] = Right(HxRetarget(CssSelector.raw(value.trim)))
  def render(h: HxRetarget): String                    = h.renderedValue
}

/** Typed `HX-Reselect` response header. */
final case class HxReselect(value: CssSelector) extends Header {
  def headerName: String    = HxReselect.name
  def renderedValue: String = value.render
}

object HxReselect extends Header.Typed[HxReselect] {
  val name: String                                     = "hx-reselect"
  def parse(value: String): Either[String, HxReselect] = Right(HxReselect(CssSelector.raw(value.trim)))
  def render(h: HxReselect): String                    = h.renderedValue
}

/**
 * Typed `HX-Trigger` response header.
 *
 * This type is named `HxTriggerHeader` to avoid colliding with the
 * attribute-DSL type `zio.blocks.htmx.HxTrigger` under wildcard imports.
 */
final case class HxTriggerHeader(value: HxEventPayload) extends Header {
  def headerName: String    = HxTriggerHeader.name
  def renderedValue: String = value.render
}

object HxTriggerHeader extends Header.Typed[HxTriggerHeader] {
  val name: String                                          = "hx-trigger"
  def parse(value: String): Either[String, HxTriggerHeader] = HxEventPayload.parse(value).map(HxTriggerHeader.apply)
  def render(h: HxTriggerHeader): String                    = h.renderedValue
}

/** Typed `HX-Trigger-After-Settle` response header. */
final case class HxTriggerAfterSettle(value: HxEventPayload) extends Header {
  def headerName: String    = HxTriggerAfterSettle.name
  def renderedValue: String = value.render
}

object HxTriggerAfterSettle extends Header.Typed[HxTriggerAfterSettle] {
  val name: String                                               = "hx-trigger-after-settle"
  def parse(value: String): Either[String, HxTriggerAfterSettle] =
    HxEventPayload.parse(value).map(HxTriggerAfterSettle.apply)
  def render(h: HxTriggerAfterSettle): String = h.renderedValue
}

/** Typed `HX-Trigger-After-Swap` response header. */
final case class HxTriggerAfterSwap(value: HxEventPayload) extends Header {
  def headerName: String    = HxTriggerAfterSwap.name
  def renderedValue: String = value.render
}

object HxTriggerAfterSwap extends Header.Typed[HxTriggerAfterSwap] {
  val name: String                                             = "hx-trigger-after-swap"
  def parse(value: String): Either[String, HxTriggerAfterSwap] =
    HxEventPayload.parse(value).map(HxTriggerAfterSwap.apply)
  def render(h: HxTriggerAfterSwap): String = h.renderedValue
}
