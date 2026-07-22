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

import scala.concurrent.duration.DurationInt

import _root_.zio.blocks.html.CssSelector
import _root_.zio.blocks.schema.json.Json
import _root_.zio.http.htmx.{HxSwap, HxTarget, HxUrlUpdate}
import _root_.zio.http.{Headers, Path, Request, Response, URL}
import _root_.zio.test._

object HtmxHeadersSpec extends ZIOSpecDefault {
  def spec = suite("HtmxHeaders")(
    suite("request headers")(
      test("HxRequest parses and renders") {
        val parsed = HxRequest.parse("true")
        assertTrue(parsed == Right(HxRequest(true))) &&
        assertTrue(HxRequest.render(HxRequest(true)) == "true")
      },
      test("request header booleans and urls reject invalid values") {
        assertTrue(HxBoosted.parse("false") == Right(HxBoosted(false))) &&
        assertTrue(HxHistoryRestoreRequest.parse("TRUE") == Right(HxHistoryRestoreRequest(true))) &&
        assertTrue(HxRequest.parse("maybe") == Left("Invalid HTMX boolean header value: maybe")) &&
        assertTrue(HxCurrentUrl.parse("   ") == Left("Empty URL value"))
      },
      test("request boolean headers render both states") {
        assertTrue(HxBoosted.render(HxBoosted(true)) == "true") &&
        assertTrue(HxHistoryRestoreRequest.render(HxHistoryRestoreRequest(false)) == "false")
      },
      test("HxCurrentUrl parses URL-like strings") {
        val parsed = HxCurrentUrl.parse("https://zio.dev/blocks")
        assertTrue(parsed == Right(HxCurrentUrl("https://zio.dev/blocks"))) &&
        assertTrue(HxCurrentUrl(URL.parse("https://zio.dev/htmx").toOption.get).renderedValue == "https://zio.dev/htmx")
      },
      test("simple request headers trim values") {
        assertTrue(HxTargetId.parse(" result ") == Right(HxTargetId("result"))) &&
        assertTrue(HxTriggerId.parse(" trigger ") == Right(HxTriggerId("trigger"))) &&
        assertTrue(HxTriggerName.parse(" search ") == Right(HxTriggerName("search"))) &&
        assertTrue(HxPrompt.parse(" confirm delete ") == Right(HxPrompt("confirm delete")))
      },
      test("typed request access works through http-model") {
        val request =
          Request.get(URL.root).addHeaders(Headers(HxRequest.name -> "true", HxTriggerName.name -> "search"))
        assertTrue(request.header(HxRequest).contains(HxRequest(true))) &&
        assertTrue(request.header(HxTriggerName).contains(HxTriggerName("search")))
      }
    ),
    suite("response headers")(
      test("HxPushUrl parses booleans and URLs") {
        assertTrue(HxPushUrl.parse("true") == Right(HxPushUrl(HxUrlUpdate.Enabled))) &&
        assertTrue(HxPushUrl.parse("/next") == Right(HxPushUrl(HxUrlUpdate("/next")))) &&
        assertTrue(HxReplaceUrl.parse("false") == Right(HxReplaceUrl(HxUrlUpdate.Disabled)))
      },
      test("response boolean headers render both states") {
        assertTrue(HxRefresh.render(HxRefresh(true)) == "true") &&
        assertTrue(HxRefresh.render(HxRefresh(false)) == "false")
      },
      test("HxReswap reuses the typed swap DSL") {
        val header   = HxReswap(HxSwap.InnerHTML.swap(1.second).settle(250.millis))
        val rendered = HxReswap.render(header)
        assertTrue(rendered == "innerHTML swap:1s settle:250ms") &&
        assertTrue(HxReswap.parse(rendered) == Right(header))
      },
      test("HxReswap surfaces HTMX swap parse failures") {
        assertTrue(HxReswap.parse("innerHTML focusScroll:later") == Left("Invalid boolean value: later"))
      },
      test("HxRetarget and HxReselect render CSS selectors") {
        assertTrue(HxRetarget.render(HxRetarget(CssSelector.id("result"))) == "#result") &&
        assertTrue(HxReselect.render(HxReselect(CssSelector.raw(".items > li"))) == ".items > li") &&
        assertTrue(HxRetarget.parse("  .panel ") == Right(HxRetarget(CssSelector.raw(".panel")))) &&
        assertTrue(HxReselect.parse("  #messages > li ") == Right(HxReselect(CssSelector.raw("#messages > li"))))
      },
      test("HxTrigger supports string and JSON payloads") {
        val jsonPayload = HxTriggerAfterSwap(HxEventPayload.JsonValue(Json.Object("message" -> Json.String("done"))))
        val rendered    = HxTriggerAfterSwap.render(jsonPayload)
        assertTrue(rendered == "{\"message\":\"done\"}") &&
        assertTrue(HxTriggerAfterSwap.parse(rendered) == Right(jsonPayload)) &&
        assertTrue(HxTriggerHeader.parse("refresh") == Right(HxTriggerHeader(HxEventPayload.Event("refresh"))))
      },
      test("HxTrigger payload parser handles arrays and invalid json") {
        val parsedArray = HxTriggerAfterSettle.parse("[1,2]")
        assertTrue(parsedArray.map(_.renderedValue) == Right("[1,2]")) &&
        assertTrue(HxTriggerHeader.parse("{invalid").isLeft) &&
        assertTrue(HxTriggerHeader.parse("   ") == Left("HTMX event name must be non-empty"))
      },
      test("HxLocation renders typed JSON") {
        val header = HxLocation("/next", target = Some(HxTarget.closest("section")), swap = Some(HxSwap.AfterEnd))
        assertTrue(
          HxLocation.render(header) == "{\"path\":\"/next\",\"target\":\"closest section\",\"swap\":\"afterend\"}"
        ) && assertTrue(HxLocation.parse(HxLocation.render(header)) == Right(header))
      },
      test("HxLocation supports Path and URL overloads and rejects non-objects") {
        val fromPath = HxLocation(Path("/items/1"), target = Some(HxTarget.This), swap = Some(HxSwap.Delete))
        val fromUrl  = HxLocation(URL.parse("https://zio.dev/items/1").toOption.get, target = None, swap = None)
        assertTrue(fromPath.renderedValue == "{\"path\":\"/items/1\",\"target\":\"this\",\"swap\":\"delete\"}") &&
        assertTrue(fromUrl.renderedValue == "{\"path\":\"https://zio.dev/items/1\"}") &&
        assertTrue(HxLocation.parse("[]") == Left("HX-Location must be a JSON object"))
      },
      test("redirect-like response headers support trimming and URL constructors") {
        assertTrue(HxRedirect.parse(" /login ") == Right(HxRedirect("/login"))) &&
        assertTrue(
          HxRedirect(URL.parse("https://zio.dev/login").toOption.get).renderedValue == "https://zio.dev/login"
        ) &&
        assertTrue(HxRedirect.parse("   ") == Left("Empty URL value"))
      },
      test("typed response access works through http-model") {
        val response = Response.ok.addHeaders(
          Headers(HxRefresh.name -> "true", HxRedirect.name -> "/login", HxTriggerAfterSettle.name -> "done")
        )
        assertTrue(response.header(HxRefresh).contains(HxRefresh(true))) &&
        assertTrue(response.header(HxRedirect).contains(HxRedirect("/login"))) &&
        assertTrue(response.header(HxTriggerAfterSettle).contains(HxTriggerAfterSettle(HxEventPayload.Event("done"))))
      }
    )
  )
}
