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

import scala.concurrent.duration.DurationInt

import zio.blocks.html._
import zio.blocks.schema.Schema
import zio.test._
import zio.http.{Path, URL}

object HtmxAttributesSpec extends ZIOSpecDefault {

  final case class SearchInput(query: String, page: Int)
  object SearchInput {
    implicit val schema: Schema[SearchInput] = Schema.derived
  }

  def spec = suite("HtmxAttributes")(
    test("hxGet accepts relative string paths") {
      val result = div(hxGet := "/search").render
      assertTrue(result == """<div hx-get="/search"></div>""")
    },
    test("htmx URL attributes reuse HTML URL sanitization") {
      val result = div(hxGet := "javascript:alert(1)", hxReplaceUrl := HxUrlUpdate("javascript:evil()")).render
      assertTrue(
        result == """<div hx-get="unsafe:javascript:alert(1)" hx-replace-url="unsafe:javascript:evil()"></div>"""
      )
    },
    test("hxPost accepts Path values") {
      val result = form(hxPost := Path("/search")).render
      assertTrue(result == """<form hx-post="/search"></form>""")
    },
    test("hxDelete accepts URL values") {
      val result = button(hxDelete := URL.fromPath(Path("/items/1"))).render
      assertTrue(result == """<button hx-delete="/items/1"></button>""")
    },
    test("hxSwap renders typed swap modifiers") {
      val result = div(hxSwap := HxSwap.InnerHTML.swap(1.second).settle(500.millis).transition).render
      assertTrue(result == """<div hx-swap="innerHTML swap:1s settle:500ms transition:true"></div>""")
    },
    test("hxSwap parses and renders all modifier variants") {
      val parsed = HxSwap.parse(
        "outerHTML swap:2s settle:250ms transition:true scroll:bottom show:top ignoreTitle:true focusScroll:false"
      )
      assertTrue(
        parsed == Right(
          HxSwap.OuterHTML
            .swap(2.seconds)
            .settle(250.millis)
            .transition
            .scroll(HxSwap.ScrollPosition.Bottom)
            .show(HxSwap.ShowPosition.Top)
            .ignoreTitle
            .focusScroll(false)
        )
      )
    },
    test("hxSwap parses remaining valid modifier branches") {
      val parsed = HxSwap.parse("beforeend scroll:top show:bottom focusScroll:true")
      assertTrue(
        parsed == Right(
          HxSwap.BeforeEnd
            .scroll(HxSwap.ScrollPosition.Top)
            .show(HxSwap.ShowPosition.Bottom)
            .focusScroll(true)
        )
      )
    },
    test("hxSwap parse failures are descriptive") {
      assertTrue(HxSwap.parse("") == Left("Empty HTMX swap value")) &&
      assertTrue(HxSwap.parse("bogus") == Left("Unknown HTMX swap strategy: bogus")) &&
      assertTrue(HxSwap.parse("innerHTML swap:later") == Left("Unsupported HTMX duration: later")) &&
      assertTrue(HxSwap.parse("innerHTML swap:abcms") == Left("Invalid number: abc")) &&
      assertTrue(HxSwap.parse("innerHTML swap:-1s") == Left("HTMX duration must be non-negative: -1")) &&
      assertTrue(HxSwap.parse("innerHTML settle:10m") == Left("Unsupported HTMX duration: 10m")) &&
      assertTrue(HxSwap.parse("innerHTML scroll:middle") == Left("Invalid HTMX scroll position: middle")) &&
      assertTrue(HxSwap.parse("innerHTML show:center") == Left("Invalid HTMX show position: center")) &&
      assertTrue(HxSwap.parse("innerHTML focusScroll:maybe") == Left("Invalid boolean value: maybe")) &&
      assertTrue(HxSwap.parse("innerHTML morph:true") == Left("Unsupported HTMX swap modifier: morph:true"))
    },
    test("hxTrigger renders chained modifiers") {
      val result = div(hxTrigger := HxTrigger.click.delay(500.millis).once.changed).render
      assertTrue(result == """<div hx-trigger="click delay:500ms once changed"></div>""")
    },
    test("hxTrigger renders filters and queue strategies") {
      val result = div(hxTrigger := HxTrigger.click.filter(js"ctrlKey").queue(HxTrigger.QueueStrategy.Last)).render
      assertTrue(result == """<div hx-trigger="click[ctrlKey] queue:last"></div>""")
    },
    test("hxTrigger renders multiple triggers") {
      val result = div(hxTrigger := HxTrigger(HxTrigger.load, HxTrigger.click.delay(1.second))).render
      assertTrue(result == """<div hx-trigger="load, click delay:1s"></div>""")
    },
    test("hxTrigger renders all supported modifiers and every") {
      val result =
        div(
          hxTrigger := HxTrigger
            .every(2.seconds)
            .throttle(1.second)
            .from(HxTarget.find("button"))
            .target("#save")
            .consume
            .queue(HxTrigger.QueueStrategy.First)
            .threshold(0.5)
            .root("#viewport")
        ).render
      assertTrue(
        result == """<div hx-trigger="every 2s throttle:1s from:find button target:#save consume queue:first threshold:0.5 root:#viewport"></div>"""
      )
    },
    test("hxTrigger accepts zero-length finite durations") {
      val result = div(hxTrigger := HxTrigger.every(0.seconds).delay(0.millis).throttle(0.millis)).render
      assertTrue(result == """<div hx-trigger="every 0s delay:0s throttle:0s"></div>""")
    },
    test("hxTrigger replaces modifiers from the same group") {
      val result =
        div(
          hxTrigger := HxTrigger.click
            .delay(1.second)
            .delay(250.millis)
            .queue(HxTrigger.QueueStrategy.First)
            .queue(HxTrigger.QueueStrategy.All)
            .from("body")
            .from(HxTarget.closest("form"))
        ).render
      assertTrue(result == """<div hx-trigger="click delay:250ms queue:all from:closest form"></div>""")
    },
    test("hxTrigger replaces every remaining modifier group") {
      val result =
        div(
          hxTrigger := HxTrigger.click
            .throttle(1.second)
            .throttle(2.seconds)
            .target("#first")
            .target("#second")
            .threshold(0.25)
            .threshold(0.75)
            .root("#one")
            .root("#two")
            .once
            .once
            .changed
            .changed
            .consume
            .consume
        ).render
      assertTrue(
        result == """<div hx-trigger="click throttle:2s target:#second threshold:0.75 root:#two once changed consume"></div>"""
      )
    },
    test("hxTarget renders extended selectors") {
      val result = div(hxTarget := HxTarget.closest("div")).render
      assertTrue(result == """<div hx-target="closest div"></div>""")
    },
    test("hxTarget renders all selector variants") {
      assertTrue(div(hxTarget := HxTarget.this_).render == """<div hx-target="this"></div>""") &&
      assertTrue(div(hxTarget := HxTarget.This).render == """<div hx-target="this"></div>""") &&
      assertTrue(div(hxTarget := HxTarget.next).render == """<div hx-target="next"></div>""") &&
      assertTrue(div(hxTarget := HxTarget.next(".item")).render == """<div hx-target="next .item"></div>""") &&
      assertTrue(div(hxTarget := HxTarget.previous).render == """<div hx-target="previous"></div>""") &&
      assertTrue(div(hxTarget := HxTarget.previous(".item")).render == """<div hx-target="previous .item"></div>""") &&
      assertTrue(div(hxTarget := HxTarget.css(selector"#result")).render == """<div hx-target="#result"></div>""")
    },
    test("hxOn renders event-specific JavaScript handlers") {
      val result = button(hxOn.click := js"doSomething()").render
      assertTrue(result == """<button hx-on:click="doSomething()"></button>""")
    },
    test("hxOn supports custom event names") {
      val result = button(hxOn("htmx:afterSwap") := js"refreshUi()").render
      assertTrue(result == """<button hx-on:htmx:afterSwap="refreshUi()"></button>""")
    },
    test("hxPushUrl accepts boolean or URL updates") {
      val result = div(hxPushUrl := HxUrlUpdate(true), hxReplaceUrl := HxUrlUpdate("/other")).render
      assertTrue(result == """<div hx-push-url="true" hx-replace-url="/other"></div>""")
    },
    test("hxPushUrl covers disabled, path, and URL values") {
      val result =
        div(
          hxPushUrl    := HxUrlUpdate(false),
          hxReplaceUrl := HxUrlUpdate(Path("/pages/2")),
          hxGet        := URL.parse("https://zio.dev/blocks?page=2").toOption.get
        ).render
      assertTrue(
        result == """<div hx-push-url="false" hx-replace-url="/pages/2" hx-get="https://zio.dev/blocks?page=2"></div>"""
      )
    },
    test("hxSelect and hxIndicator accept CSS selectors") {
      val result = div(hxSelect := selector"#result", hxIndicator := selector".spinner").render
      assertTrue(result == """<div hx-select="#result" hx-indicator=".spinner"></div>""")
    },
    test("hxInclude reuses extended selectors") {
      val result = div(hxInclude := HxTarget.find(".extra-fields")).render
      assertTrue(result == """<div hx-include="find .extra-fields"></div>""")
    },
    test("hxParams renders typed parameter strategies") {
      val result = div(hxParams := HxParams.not("page", "sort")).render
      assertTrue(result == """<div hx-params="not page,sort"></div>""")
    },
    test("hxParams renders all parameter strategies") {
      assertTrue(div(hxParams := HxParams.All).render == """<div hx-params="*"></div>""") &&
      assertTrue(div(hxParams := HxParams.None).render == """<div hx-params="none"></div>""") &&
      assertTrue(div(hxParams := HxParams.only("page", "sort")).render == """<div hx-params="page,sort"></div>""")
    },
    test("hxSync renders selector plus strategy") {
      val result = div(hxSync := HxSync(HxTarget.closest("form"), HxSyncStrategy.Abort)).render
      assertTrue(result == """<div hx-sync="closest form:abort"></div>""")
    },
    test("hxSync renders every strategy") {
      assertTrue(
        div(hxSync := HxSync(HxTarget.This, HxSyncStrategy.Replace)).render == """<div hx-sync="this:replace"></div>"""
      ) &&
      assertTrue(
        div(hxSync := HxSync(HxTarget.next, HxSyncStrategy.Drop)).render == """<div hx-sync="next:drop"></div>"""
      ) &&
      assertTrue(
        div(
          hxSync := HxSync(HxTarget.previous("form"), HxSyncStrategy.Queue)
        ).render == """<div hx-sync="previous form:queue"></div>"""
      )
    },
    test("hxEncoding renders multipart form data") {
      val result = form(hxEncoding := HxEncoding.Multipart).render
      assertTrue(result == """<form hx-encoding="multipart/form-data"></form>""")
    },
    test("hxVals renders schema-backed JSON") {
      val result = div(hxVals := HxVals.from(SearchInput("zio", 2))).render
      assertTrue(result == """<div hx-vals="{&quot;query&quot;:&quot;zio&quot;,&quot;page&quot;:2}"></div>""")
    },
    test("hxHeaders renders schema-backed JSON") {
      val result = div(hxHeaders := HxHeadersValue.from(SearchInput("zio", 2))).render
      assertTrue(result == """<div hx-headers="{&quot;query&quot;:&quot;zio&quot;,&quot;page&quot;:2}"></div>""")
    },
    test("hxSwapOob renders swap strategy and selector") {
      val result = div(hxSwapOob := HxSwapOob.using(HxSwap.AfterEnd, HxTarget.css("#messages"))).render
      assertTrue(result == """<div hx-swap-oob="afterend:#messages"></div>""")
    },
    test("hxSwapOob renders boolean and selector-free forms") {
      assertTrue(div(hxSwapOob := HxSwapOob(true)).render == """<div hx-swap-oob="true"></div>""") &&
      assertTrue(div(hxSwapOob := HxSwapOob(false)).render == """<div hx-swap-oob="false"></div>""") &&
      assertTrue(div(hxSwapOob := HxSwapOob.using(HxSwap.Delete)).render == """<div hx-swap-oob="delete"></div>""")
    },
    test("hxExt and hxDisinherit render name lists") {
      val result = div(hxExt("sse", "morph"), hxDisinherit("hx-target", "hx-swap")).render
      assertTrue(result == """<div hx-ext="sse,morph" hx-disinherit="hx-target hx-swap"></div>""")
    },
    test("support helpers cover quoting and primitive conversions") {
      assertTrue(HtmxSupport.quoteJson("he said \"hi\"") == "\"he said \\\"hi\\\"\"") &&
      assertTrue(ToHtmxValue[Int].toHtmxValue(2) == "2") &&
      assertTrue(ToHtmxValue[Long].toHtmxValue(3L) == "3") &&
      assertTrue(ToHtmxValue[Double].toHtmxValue(0.5) == "0.5")
    },
    test("boolean-style helpers render explicit HTMX booleans") {
      val result =
        div(hxBoost := true, hxValidate := false, hxPreserve := true, hxHistory := false, hxDisable := true).render
      assertTrue(
        result == """<div hx-boost="true" hx-validate="false" hx-preserve="true" hx-history="false" hx-disable="true"></div>"""
      )
    }
  )
}
