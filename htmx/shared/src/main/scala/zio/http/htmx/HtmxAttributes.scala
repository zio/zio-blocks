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

import _root_.zio.blocks.html.{CssSelector, Dom, Js}

/**
 * Mixin providing the typed HTMX `hx-*` attribute DSL for `zio.blocks.html`.
 *
 * Typical usage is through the package object via `import zio.http.htmx._`.
 * Attribute keys in this trait delegate value rendering to [[ToHtmxValue]], so
 * custom domain values can participate in the DSL by defining their own
 * instances.
 *
 * Event-handler attributes are exposed through [[hxOn]], which writes literal
 * JavaScript expressions to `hx-on:*` attributes instead of generic string
 * values.
 *
 * Most attributes are intentionally narrower than plain HTML strings. For
 * example, `hxSwap` accepts [[HxSwap]], `hxTarget` accepts [[HxTarget]], and
 * selector-only attributes accept [[CssSelector]]. This makes accidental
 * stringly misuse fail at compile time while keeping explicit URL/string
 * surfaces for request attributes.
 */
trait HtmxAttributes {
  import ToHtmxValue.UrlLike

  val hxGet: HtmxAttrKey[UrlLike]    = HtmxAttrKey.stringValue[UrlLike]("hx-get")
  val hxPost: HtmxAttrKey[UrlLike]   = HtmxAttrKey.stringValue[UrlLike]("hx-post")
  val hxPut: HtmxAttrKey[UrlLike]    = HtmxAttrKey.stringValue[UrlLike]("hx-put")
  val hxPatch: HtmxAttrKey[UrlLike]  = HtmxAttrKey.stringValue[UrlLike]("hx-patch")
  val hxDelete: HtmxAttrKey[UrlLike] = HtmxAttrKey.stringValue[UrlLike]("hx-delete")

  val hxSwap: HtmxAttrKey[HxSwap]             = HtmxAttrKey.stringValue[HxSwap]("hx-swap")
  val hxTrigger: HtmxAttrKey[HxTriggerValue]  = HtmxAttrKey.stringValue[HxTriggerValue]("hx-trigger")
  val hxTarget: HtmxAttrKey[HxTarget]         = HtmxAttrKey.stringValue[HxTarget]("hx-target")
  val hxBoost: HtmxAttrKey[Boolean]           = HtmxAttrKey.stringValue[Boolean]("hx-boost")
  val hxPushUrl: HtmxAttrKey[HxUrlUpdate]     = HtmxAttrKey.stringValue[HxUrlUpdate]("hx-push-url")
  val hxReplaceUrl: HtmxAttrKey[HxUrlUpdate]  = HtmxAttrKey.stringValue[HxUrlUpdate]("hx-replace-url")
  val hxSelect: HtmxAttrKey[CssSelector]      = HtmxAttrKey.stringValue[CssSelector]("hx-select")
  val hxSelectOob: HtmxAttrKey[CssSelector]   = HtmxAttrKey.stringValue[CssSelector]("hx-select-oob")
  val hxSwapOob: HtmxAttrKey[HxSwapOob]       = HtmxAttrKey.stringValue[HxSwapOob]("hx-swap-oob")
  val hxConfirm: HtmxAttrKey[String]          = HtmxAttrKey.stringValue[String]("hx-confirm")
  val hxPrompt: HtmxAttrKey[String]           = HtmxAttrKey.stringValue[String]("hx-prompt")
  val hxDisable: HtmxAttrKey[Boolean]         = HtmxAttrKey.stringValue[Boolean]("hx-disable")
  val hxDisabledElt: HtmxAttrKey[CssSelector] = HtmxAttrKey.stringValue[CssSelector]("hx-disabled-elt")
  val hxIndicator: HtmxAttrKey[CssSelector]   = HtmxAttrKey.stringValue[CssSelector]("hx-indicator")
  val hxInclude: HtmxAttrKey[HxTarget]        = HtmxAttrKey.stringValue[HxTarget]("hx-include")
  val hxParams: HtmxAttrKey[HxParams]         = HtmxAttrKey.stringValue[HxParams]("hx-params")
  val hxSync: HtmxAttrKey[HxSync]             = HtmxAttrKey.stringValue[HxSync]("hx-sync")
  val hxValidate: HtmxAttrKey[Boolean]        = HtmxAttrKey.stringValue[Boolean]("hx-validate")
  val hxPreserve: HtmxAttrKey[Boolean]        = HtmxAttrKey.stringValue[Boolean]("hx-preserve")
  val hxEncoding: HtmxAttrKey[HxEncoding]     = HtmxAttrKey.stringValue[HxEncoding]("hx-encoding")
  val hxVals: HtmxAttrKey[HxVals]             = HtmxAttrKey.stringValue[HxVals]("hx-vals")
  val hxHeaders: HtmxAttrKey[HxHeadersValue]  = HtmxAttrKey.stringValue[HxHeadersValue]("hx-headers")
  val hxHistory: HtmxAttrKey[Boolean]         = HtmxAttrKey.stringValue[Boolean]("hx-history")

  def hxExt(first: String, rest: String*): Dom.Attribute = hxExt := HxExtensions(first, rest: _*)
  val hxExt: HtmxAttrKey[HxExtensions]                   = HtmxAttrKey.stringValue[HxExtensions]("hx-ext")

  def hxDisinherit(first: String, rest: String*): Dom.Attribute =
    hxDisinherit := HxAttributeNames(first, rest: _*)

  val hxDisinherit: HtmxAttrKey[HxAttributeNames] = HtmxAttrKey.stringValue[HxAttributeNames]("hx-disinherit")

  /**
   * Builder for event-specific `hx-on:*` attributes.
   *
   * Eagerly constructed: the keys in this trait are cheap wrappers, so plain
   * `val`s avoid the bitmap plus synchronized initialization that `lazy val`
   * would add to every importer scope.
   */
  val hxOn: PartialHxOn = new PartialHxOn
}

/** Partial builder for event-specific `hx-on:*` attributes. */
final class PartialHxOn {
  def click: HxOnKey               = HxOnKey("click")
  def submit: HxOnKey              = HxOnKey("submit")
  def change: HxOnKey              = HxOnKey("change")
  def input: HxOnKey               = HxOnKey("input")
  def apply(name: String): HxOnKey = HxOnKey(HtmxSupport.requireNonBlank(name, "HTMX event name"))
}

/**
 * Typed key for an `hx-on:*` attribute bound to a specific event name.
 */
final class HxOnKey private (eventName: String) {

  /**
   * Assigns a JavaScript expression to the selected `hx-on:*` attribute.
   *
   * `Js` is treated as an intentionally raw JavaScript surface. Do not build it
   * from unsanitized user input.
   */
  def :=(value: Js): Dom.Attribute =
    Dom.Attribute.KeyValue("hx-on:" + eventName, Dom.AttributeValue.JsValue(value))
}

object HxOnKey {
  private[htmx] def apply(eventName: String): HxOnKey = new HxOnKey(eventName)
}
