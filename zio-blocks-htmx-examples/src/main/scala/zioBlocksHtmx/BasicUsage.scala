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

package zioBlocksHtmx

import zio.blocks.html._
import zio.http.htmx._
import scala.concurrent.duration._

/**
 * HTMX DSL — Basic Usage
 *
 * Demonstrates fundamental HTMX attribute construction: triggering requests
 * (hxPost, hxGet), swapping strategies (InnerHTML, OuterHTML), and target
 * selection (This, closest, find). Shows how the typed DSL ensures correct HTMX
 * syntax at compile time.
 *
 * Run with: sbt "zio-blocks-htmx-examples/runMain zioBlocksHtmx.BasicUsage"
 */
@main def BasicUsage(): Unit = {

  println("=== HTMX Basic Usage Examples ===\n")

  // Example 1: Simple click trigger with POST
  println("1. Click trigger with POST request:")
  val clickButton = button(
    hxPost    := "/api/action",
    hxTrigger := HxTrigger.click,
    "Click me"
  )
  println(s"  Rendered: $clickButton\n")

  // Example 2: Input with GET request
  println("2. Input with debounced GET request:")
  val searchInput = input(
    `type`      := "text",
    placeholder := "Search...",
    hxGet       := "/api/search",
    hxTrigger   := HxTrigger.input.delay(500.millis),
    hxTarget    := HxTarget.next("div")
  )
  println(s"  Rendered: $searchInput\n")

  // Example 3: Different swap strategies
  println("3. Swap strategies:")
  val innerHTMLDiv = div(
    id     := "content",
    hxGet  := "/api/content",
    hxSwap := HxSwap.InnerHTML,
    "Click to load inner content"
  )
  val outerHTMLDiv = div(
    id     := "card",
    hxPost := "/api/replace",
    hxSwap := HxSwap.OuterHTML,
    "This entire div will be replaced"
  )
  val appendDiv = div(
    id     := "messages",
    hxGet  := "/api/new-message",
    hxSwap := HxSwap.BeforeEnd.scroll(HxSwap.ScrollPosition.Bottom),
    "New messages will be appended"
  )
  println(s"  InnerHTML: ${innerHTMLDiv}")
  println(s"  OuterHTML: ${outerHTMLDiv}")
  println(s"  Append with scroll: ${appendDiv}\n")

  // Example 4: Target selection patterns
  println("4. Target selection:")
  val targetThis = button(
    hxPost   := "/api/toggle",
    hxTarget := HxTarget.This,
    "Toggle this button"
  )
  val targetClosest = button(
    hxPost   := "/api/validate",
    hxTarget := HxTarget.closest("form"),
    "Validate form"
  )
  val targetFind = div(
    hxGet    := "/api/update",
    hxTarget := HxTarget.find(".result"),
    "Content with .result child"
  )
  println(s"  This: $targetThis")
  println(s"  Closest: $targetClosest")
  println(s"  Find: $targetFind\n")

  // Example 5: Form submission with parameters
  println("5. Form submission with selective parameters:")
  val searchForm = form(
    hxPost    := "/api/search",
    hxTrigger := HxTrigger.submit,
    hxParams  := HxParams.only("query", "page"),
    hxSwap    := HxSwap.InnerHTML,
    input(`type`  := "text", name   := "query", placeholder := "Query"),
    input(`type`  := "hidden", name := "page", value        := "1"),
    input(`type`  := "hidden", name := "unused", value      := "ignored"),
    button(`type` := "submit", "Search")
  )
  println(s"  Form with selective params: $searchForm\n")

  // Example 6: Load event with once modifier
  println("6. Load event with once modifier:")
  val initialLoadDiv = div(
    id        := "initial",
    hxGet     := "/api/bootstrap-data",
    hxTrigger := HxTrigger.load.once,
    hxSwap    := HxSwap.InnerHTML,
    "Loading initial data..."
  )
  println(s"  Load once: $initialLoadDiv\n")

  // Example 7: Change event on select
  println("7. Change event on select element:")
  val selectDropdown = select(
    name      := "category",
    hxPost    := "/api/category-changed",
    hxTrigger := HxTrigger.change,
    hxTarget  := HxTarget.next("div"),
    option(value := "all", "All Categories"),
    option(value := "news", "News"),
    option(value := "updates", "Updates")
  )
  println(s"  Select with change: $selectDropdown\n")

  println("✓ Basic usage examples complete")
}
