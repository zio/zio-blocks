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

import zio.blocks.html.{Dom, Js}

/**
 * Mixin providing the typed HTMX `hx-*` attribute DSL for `zio.blocks.html`.
 *
 * Typical usage is through the package object via `import zio.blocks.htmx._`.
 * Attribute keys in this trait delegate value rendering to [[ToHtmxValue]], so
 * custom domain values can participate in the DSL by defining their own
 * instances.
 *
 * Event-handler attributes are exposed through [[hxOn]], which writes literal
 * JavaScript expressions to `hx-on:*` attributes instead of generic string
 * values.
 */
trait HtmxAttributes {
  def hxGet: HtmxAttrKey    = HtmxAttrKey("hx-get")
  def hxPost: HtmxAttrKey   = HtmxAttrKey("hx-post")
  def hxPut: HtmxAttrKey    = HtmxAttrKey("hx-put")
  def hxPatch: HtmxAttrKey  = HtmxAttrKey("hx-patch")
  def hxDelete: HtmxAttrKey = HtmxAttrKey("hx-delete")

  def hxSwap: HtmxAttrKey        = HtmxAttrKey("hx-swap")
  def hxTrigger: HtmxAttrKey     = HtmxAttrKey("hx-trigger")
  def hxTarget: HtmxAttrKey      = HtmxAttrKey("hx-target")
  def hxBoost: HtmxAttrKey       = HtmxAttrKey("hx-boost")
  def hxPushUrl: HtmxAttrKey     = HtmxAttrKey("hx-push-url")
  def hxReplaceUrl: HtmxAttrKey  = HtmxAttrKey("hx-replace-url")
  def hxSelect: HtmxAttrKey      = HtmxAttrKey("hx-select")
  def hxSelectOob: HtmxAttrKey   = HtmxAttrKey("hx-select-oob")
  def hxSwapOob: HtmxAttrKey     = HtmxAttrKey("hx-swap-oob")
  def hxConfirm: HtmxAttrKey     = HtmxAttrKey("hx-confirm")
  def hxPrompt: HtmxAttrKey      = HtmxAttrKey("hx-prompt")
  def hxDisable: HtmxAttrKey     = HtmxAttrKey("hx-disable")
  def hxDisabledElt: HtmxAttrKey = HtmxAttrKey("hx-disabled-elt")
  def hxIndicator: HtmxAttrKey   = HtmxAttrKey("hx-indicator")
  def hxInclude: HtmxAttrKey     = HtmxAttrKey("hx-include")
  def hxParams: HtmxAttrKey      = HtmxAttrKey("hx-params")
  def hxSync: HtmxAttrKey        = HtmxAttrKey("hx-sync")
  def hxValidate: HtmxAttrKey    = HtmxAttrKey("hx-validate")
  def hxPreserve: HtmxAttrKey    = HtmxAttrKey("hx-preserve")
  def hxEncoding: HtmxAttrKey    = HtmxAttrKey("hx-encoding")
  def hxVals: HtmxAttrKey        = HtmxAttrKey("hx-vals")
  def hxHeaders: HtmxAttrKey     = HtmxAttrKey("hx-headers")
  def hxHistory: HtmxAttrKey     = HtmxAttrKey("hx-history")

  def hxExt(first: String, rest: String*): Dom.Attribute = hxExt := HxExtensions(first, rest: _*)
  def hxExt: HtmxAttrKey                                 = HtmxAttrKey("hx-ext")

  def hxDisinherit(first: String, rest: String*): Dom.Attribute =
    hxDisinherit := HxAttributeNames(first, rest: _*)

  def hxDisinherit: HtmxAttrKey = HtmxAttrKey("hx-disinherit")

  /** Builder for event-specific `hx-on:*` attributes. */
  def hxOn: PartialHxOn = new PartialHxOn
}

/** Partial builder for event-specific `hx-on:*` attributes. */
final class PartialHxOn {
  def click: HxOnKey               = HxOnKey("click")
  def submit: HxOnKey              = HxOnKey("submit")
  def change: HxOnKey              = HxOnKey("change")
  def input: HxOnKey               = HxOnKey("input")
  def apply(name: String): HxOnKey = HxOnKey(name)
}

/**
 * Typed key for an `hx-on:*` attribute bound to a specific event name.
 */
final class HxOnKey private (eventName: String) {

  /** Assigns a JavaScript expression to the selected `hx-on:*` attribute. */
  def :=(value: Js): Dom.Attribute =
    Dom.Attribute.KeyValue("hx-on:" + eventName, Dom.AttributeValue.JsValue(value))
}

object HxOnKey {
  private[htmx] def apply(eventName: String): HxOnKey = new HxOnKey(eventName)
}
