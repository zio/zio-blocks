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
 * The typed htmx 2 attribute keys (`hxGet`, `hxSwap`, `hxTrigger`, ...) for the
 * [[zio.blocks.html]] DSL.
 *
 * The `zio.http.htmx` package object mixes this trait in, so
 * `import zio.http.htmx._` brings every key into scope. Each key renders its
 * value with the [[ToHtmxValue]] instance fixed when the key was defined;
 * defining an instance for another type does not let that type be assigned to a
 * key.
 *
 * Most keys take a narrower type than a string: `hxSwap` takes [[HxSwap]],
 * `hxTarget` takes [[HxTarget]], and selector-only keys take a
 * [[zio.blocks.html.CssSelector CssSelector]], so a misspelled value fails at
 * compile time. The request keys (`hxGet`, `hxPost`, ...) take a `String`, a
 * [[zio.http.Path Path]] or a [[zio.http.URL URL]], and the [[HxOnKey]] keys of
 * [[hxOn]] take raw JavaScript as [[zio.blocks.html.Js Js]].
 *
 * {{{
 * import zio.blocks.html._
 *
 * val addToCart = button(
 *   hxPost := "/cart/items",
 *   hxTarget := HxTarget.css("#cart"),
 *   hxSwap := HxSwap.OuterHTML,
 *   hxIndicator := CssSelector.id("cart-spinner")
 * )("Add to cart").render
 * // <button hx-post="/cart/items" hx-target="#cart" hx-swap="outerHTML" hx-indicator="#cart-spinner">Add to cart</button>
 * }}}
 *
 * @see
 *   [[https://zioblocks.com/reference/htmx/ HTMX]]
 */
trait HtmxAttributes {
  import ToHtmxValue.UrlLike

  /**
   * `hx-get`: htmx sends a GET request to this URL. A `String` is written as
   * given, except that the HTML renderer prefixes a `javascript:`, `vbscript:`
   * or `data:text/html` URL with `unsafe:`, while a [[zio.http.Path Path]] or
   * [[zio.http.URL URL]] is percent-encoded. A user's text in the query of a
   * `String` URL can therefore add parameters (a known bug); percent-encode
   * such text before building the string.
   */
  lazy val hxGet: HtmxAttrKey[UrlLike] = HtmxAttrKey.stringValue[UrlLike]("hx-get")

  /** `hx-post`: htmx sends a POST request to this URL; see [[hxGet]]. */
  lazy val hxPost: HtmxAttrKey[UrlLike] = HtmxAttrKey.stringValue[UrlLike]("hx-post")

  /** `hx-put`: htmx sends a PUT request to this URL; see [[hxGet]]. */
  lazy val hxPut: HtmxAttrKey[UrlLike] = HtmxAttrKey.stringValue[UrlLike]("hx-put")

  /** `hx-patch`: htmx sends a PATCH request to this URL; see [[hxGet]]. */
  lazy val hxPatch: HtmxAttrKey[UrlLike] = HtmxAttrKey.stringValue[UrlLike]("hx-patch")

  /** `hx-delete`: htmx sends a DELETE request to this URL; see [[hxGet]]. */
  lazy val hxDelete: HtmxAttrKey[UrlLike] = HtmxAttrKey.stringValue[UrlLike]("hx-delete")

  /** `hx-swap`: how htmx puts the response into the target; see [[HxSwap]]. */
  lazy val hxSwap: HtmxAttrKey[HxSwap] = HtmxAttrKey.stringValue[HxSwap]("hx-swap")

  /**
   * `hx-trigger`: which events send the request; takes an [[HxTrigger]] or an
   * [[HxTriggerSet]].
   */
  lazy val hxTrigger: HtmxAttrKey[HxTriggerValue] = HtmxAttrKey.stringValue[HxTriggerValue]("hx-trigger")

  /** `hx-target`: the element the response goes into; see [[HxTarget]]. */
  lazy val hxTarget: HtmxAttrKey[HxTarget] = HtmxAttrKey.stringValue[HxTarget]("hx-target")

  /**
   * `hx-boost`: with `true`, htmx turns the links and forms on and inside the
   * element into htmx requests that swap the page body.
   */
  lazy val hxBoost: HtmxAttrKey[Boolean] = HtmxAttrKey.stringValue[Boolean]("hx-boost")

  /**
   * `hx-push-url`: whether htmx pushes a URL into the browser history; see
   * [[HxUrlUpdate]].
   */
  lazy val hxPushUrl: HtmxAttrKey[HxUrlUpdate] = HtmxAttrKey.stringValue[HxUrlUpdate]("hx-push-url")

  /**
   * `hx-replace-url`: whether htmx replaces the current URL in the browser
   * history; see [[HxUrlUpdate]].
   */
  lazy val hxReplaceUrl: HtmxAttrKey[HxUrlUpdate] = HtmxAttrKey.stringValue[HxUrlUpdate]("hx-replace-url")

  /** `hx-select`: the part of the response that htmx swaps in. */
  lazy val hxSelect: HtmxAttrKey[CssSelector] = HtmxAttrKey.stringValue[CssSelector]("hx-select")

  /**
   * `hx-select-oob`: parts of the response that htmx swaps in out of band, each
   * into the page element with the same `id`.
   */
  lazy val hxSelectOob: HtmxAttrKey[CssSelector] = HtmxAttrKey.stringValue[CssSelector]("hx-select-oob")

  /**
   * `hx-swap-oob`, set on an element of a response: htmx swaps that element
   * into the page outside the target; see [[HxSwapOob]].
   */
  lazy val hxSwapOob: HtmxAttrKey[HxSwapOob] = HtmxAttrKey.stringValue[HxSwapOob]("hx-swap-oob")

  /**
   * `hx-confirm`: htmx asks the user to confirm this text before the request.
   */
  lazy val hxConfirm: HtmxAttrKey[String] = HtmxAttrKey.stringValue[String]("hx-confirm")

  /**
   * `hx-prompt`: htmx asks the user for a text with this message and sends the
   * answer in the `HX-Prompt` header ([[zio.http.htmx.headers.HxPrompt]]).
   */
  lazy val hxPrompt: HtmxAttrKey[String] = HtmxAttrKey.stringValue[String]("hx-prompt")

  /**
   * `hx-disable`: htmx ignores the element and its children.
   *
   * htmx 2 checks only that the attribute is present, so `hxDisable := false`
   * disables htmx too; this is a known bug. Leave the attribute out to keep
   * htmx on.
   */
  lazy val hxDisable: HtmxAttrKey[Boolean] = HtmxAttrKey.stringValue[Boolean]("hx-disable")

  /**
   * `hx-disabled-elt`: elements that htmx gives the `disabled` attribute while
   * the request is in flight.
   */
  lazy val hxDisabledElt: HtmxAttrKey[CssSelector] = HtmxAttrKey.stringValue[CssSelector]("hx-disabled-elt")

  /**
   * `hx-indicator`: the element that htmx gives the `htmx-request` class while
   * the request is in flight.
   */
  lazy val hxIndicator: HtmxAttrKey[CssSelector] = HtmxAttrKey.stringValue[CssSelector]("hx-indicator")

  /**
   * `hx-include`: more elements whose values htmx adds to the request; see
   * [[HxTarget]].
   */
  lazy val hxInclude: HtmxAttrKey[HxTarget] = HtmxAttrKey.stringValue[HxTarget]("hx-include")

  /** `hx-params`: which parameters htmx sends; see [[HxParams]]. */
  lazy val hxParams: HtmxAttrKey[HxParams] = HtmxAttrKey.stringValue[HxParams]("hx-params")

  /** `hx-sync`: synchronizes requests between elements; see [[HxSync]]. */
  lazy val hxSync: HtmxAttrKey[HxSync] = HtmxAttrKey.stringValue[HxSync]("hx-sync")

  /**
   * `hx-validate`: with `true`, htmx runs the browser's form validation on the
   * element before the request.
   */
  lazy val hxValidate: HtmxAttrKey[Boolean] = HtmxAttrKey.stringValue[Boolean]("hx-validate")

  /**
   * `hx-preserve`: htmx keeps the element, matched by `id`, unchanged when the
   * content around it is swapped.
   *
   * htmx 2 checks only that the attribute is present, so `hxPreserve := false`
   * preserves the element too; this is a known bug. Leave the attribute out
   * instead.
   */
  lazy val hxPreserve: HtmxAttrKey[Boolean] = HtmxAttrKey.stringValue[Boolean]("hx-preserve")

  /** `hx-encoding`: the request body encoding; see [[HxEncoding]]. */
  lazy val hxEncoding: HtmxAttrKey[HxEncoding] = HtmxAttrKey.stringValue[HxEncoding]("hx-encoding")

  /**
   * `hx-vals`: JSON values htmx adds to the request parameters; see [[HxVals]].
   */
  lazy val hxVals: HtmxAttrKey[HxVals] = HtmxAttrKey.stringValue[HxVals]("hx-vals")

  /**
   * `hx-headers`: JSON values htmx adds to the request headers; see
   * [[HxHeadersValue]].
   */
  lazy val hxHeaders: HtmxAttrKey[HxHeadersValue] = HtmxAttrKey.stringValue[HxHeadersValue]("hx-headers")

  /**
   * `hx-history`: with `false`, htmx keeps the page out of its history cache,
   * for pages that show sensitive data.
   */
  lazy val hxHistory: HtmxAttrKey[Boolean] = HtmxAttrKey.stringValue[Boolean]("hx-history")

  /**
   * Creates an `hx-ext` attribute that enables the named htmx extensions, such
   * as `hxExt("json-enc", "preload")` for `hx-ext="json-enc,preload"`.
   *
   * @throws java.lang.IllegalArgumentException
   *   if a name is blank
   */
  def hxExt(first: String, rest: String*): Dom.Attribute = hxExt := HxExtensions(first, rest: _*)

  /** The `hx-ext` key, for an [[HxExtensions]] value. */
  lazy val hxExt: HtmxAttrKey[HxExtensions] = HtmxAttrKey.stringValue[HxExtensions]("hx-ext")

  /**
   * Creates an `hx-disinherit` attribute that stops the element's children from
   * inheriting the named htmx attributes, such as
   * `hxDisinherit("hx-target", "hx-swap")`.
   *
   * @throws java.lang.IllegalArgumentException
   *   if a name is blank
   */
  def hxDisinherit(first: String, rest: String*): Dom.Attribute =
    hxDisinherit := HxAttributeNames(first, rest: _*)

  /** The `hx-disinherit` key, for an [[HxAttributeNames]] value. */
  lazy val hxDisinherit: HtmxAttrKey[HxAttributeNames] = HtmxAttrKey.stringValue[HxAttributeNames]("hx-disinherit")

  /**
   * Builds `hx-on:<event>` attributes, which run JavaScript when the event
   * fires: `hxOn.click := Js("...")` renders `hx-on:click`.
   */
  lazy val hxOn: PartialHxOn = new PartialHxOn
}

/** Picks the event of an `hx-on:<event>` attribute; reached through `hxOn`. */
final class PartialHxOn {

  /** The `hx-on:click` key. */
  def click: HxOnKey = HxOnKey("click")

  /** The `hx-on:submit` key. */
  def submit: HxOnKey = HxOnKey("submit")

  /** The `hx-on:change` key. */
  def change: HxOnKey = HxOnKey("change")

  /** The `hx-on:input` key. */
  def input: HxOnKey = HxOnKey("input")

  /**
   * The key for any event, after trimming its name:
   * `hxOn("htmx:after-request")` gives `hx-on:htmx:after-request`.
   *
   * @throws java.lang.IllegalArgumentException
   *   if `name` is blank
   */
  def apply(name: String): HxOnKey = HxOnKey(HtmxSupport.requireNonBlank(name, "HTMX event name"))
}

/** The key of an `hx-on:<event>` attribute for one event. */
final class HxOnKey private (eventName: String) {

  /**
   * Creates the `hx-on:<event>` attribute with this JavaScript as its value.
   *
   * `Js` is a raw JavaScript surface: the code is written as given (HTML
   * escaped as an attribute value), so never build it from user input.
   */
  def :=(value: Js): Dom.Attribute =
    Dom.Attribute.KeyValue("hx-on:" + eventName, Dom.AttributeValue.JsValue(value))
}

/** Holds the package-private factory for [[HxOnKey]]. */
object HxOnKey {

  /** Creates the key for `eventName`, which callers have already checked. */
  private[htmx] def apply(eventName: String): HxOnKey = new HxOnKey(eventName)
}
