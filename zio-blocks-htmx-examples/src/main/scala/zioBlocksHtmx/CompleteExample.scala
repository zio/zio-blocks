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
import scala.concurrent.duration.DurationInt

/**
 * HTMX DSL — Complete Realistic Example
 *
 * A real-world e-commerce search and filtering interface combining multiple
 * HTMX attributes: debounced search input, live category filtering, paginated
 * results, out-of-band notifications, and dynamic UI updates. Demonstrates how
 * types compose to create a type-safe, interactive UI.
 *
 * Run with: sbt "zio-blocks-htmx-examples/runMain
 * zioBlocksHtmx.CompleteExample"
 */
object CompleteExample {
  def main(args: Array[String]): Unit = {
    println("=== HTMX Complete Realistic Example ===\n")

    println("Building a type-safe e-commerce search interface...\n")

    // Part 1: Search input with debounce and parameter filtering
    val searchInput = input(
      `type`      := "text",
      name        := "query",
      id          := "search-query",
      placeholder := "Search products...",
      hxPost      := "/api/search",
      hxTrigger   := HxTrigger.input
        .delay(500.millis)
        .changed,
      hxParams := HxParams.only("query", "category", "page"),
      hxTarget := HxTarget.find(".results-container"),
      hxSwap   := HxSwap.InnerHTML.transition.settle(250.millis)
    )

    println("1. Search Input:")
    println(s"   Placeholder: Search products")
    println(s"   Trigger: input with 500ms delay + changed")
    println(s"   Params: query, category, page (others excluded)")
    println(s"   Swap: InnerHTML with transition and 250ms settle")
    println(s"   Rendered: $searchInput\n")

    // Part 2: Category filter with instant response
    val categoryFilter = select(
      name      := "category",
      id        := "category-filter",
      hxPost    := "/api/search",
      hxTrigger := HxTrigger.change,
      hxTarget  := HxTarget.find(".results-container"),
      hxSwap    := HxSwap.InnerHTML.transition,
      option(value := "all", selected, "All Categories"),
      option(value := "electronics", "Electronics"),
      option(value := "books", "Books"),
      option(value := "clothing", "Clothing")
    )

    println("2. Category Filter:")
    println(s"   Trigger: change event (no delay)")
    println(s"   Target: .results-container")
    println(s"   Rendered: $categoryFilter\n")

    // Part 3: Results container with dynamic content
    val resultsContainer = div(
      id        := "results",
      `class`   := "results-container",
      hxGet     := "/api/search",
      hxTrigger := HxTrigger.load,
      hxTarget  := HxTarget.This,
      hxSwap    := HxSwap.InnerHTML,
      div("Loading results...")
    )

    println("3. Results Container:")
    println(s"   Trigger: load (fetches initial results)")
    println(s"   Swap: InnerHTML")
    println(s"   Rendered: $resultsContainer\n")

    // Part 4: Pagination buttons
    val prevButton = button(
      id        := "btn-prev",
      hxPost    := "/api/search",
      hxTrigger := HxTrigger.click,
      hxParams  := HxParams.only("query", "category", "page"),
      hxTarget  := HxTarget.find(".results-container"),
      hxSwap    := HxSwap.InnerHTML.scroll(HxSwap.ScrollPosition.Top),
      "← Previous"
    )

    val nextButton = button(
      id        := "btn-next",
      hxPost    := "/api/search",
      hxTrigger := HxTrigger.click,
      hxParams  := HxParams.only("query", "category", "page"),
      hxTarget  := HxTarget.find(".results-container"),
      hxSwap    := HxSwap.InnerHTML.scroll(HxSwap.ScrollPosition.Top),
      "Next →"
    )

    println("4. Pagination Buttons:")
    println(s"   Behavior: POST request, replace results, scroll to top")
    println(s"   Prev: $prevButton")
    println(s"   Next: $nextButton\n")

    // Part 5: Status badge with out-of-band updates
    val statusBadge = div(
      id        := "search-status",
      hxSwapOob := HxSwapOob.using(HxSwap.InnerHTML),
      "Ready"
    )

    println("5. Status Badge (Out-of-Band):")
    println(s"   Updates separately from main results")
    println(s"   Server response can include status updates")
    println(s"   Rendered: $statusBadge\n")

    // Part 6: Complete search form integrating all parts
    val wholeForm = div(
      id := "search-interface",
      h2("Product Search"),
      div(
        `class` := "search-controls",
        div(
          label(`for` := "search-query", "Search:"),
          searchInput
        ),
        div(
          label(`for` := "category-filter", "Category:"),
          categoryFilter
        )
      ),
      resultsContainer,
      div(
        `class` := "pagination-controls",
        prevButton,
        span(id := "page-info", "Page 1"),
        nextButton
      ),
      statusBadge
    )

    println("6. Complete Search Form:")
    println(s"   Integrates search input, category filter, results, pagination")
    println(s"   Form structure: $wholeForm\n")

    // Part 7: Advanced example - filtering based on price range
    val priceRangeInput = input(
      `type`    := "range",
      name      := "max-price",
      id        := "price-slider",
      min       := "0",
      max       := "1000",
      value     := "1000",
      hxPost    := "/api/search",
      hxTrigger := HxTrigger.change.throttle(500.millis),
      hxParams  := HxParams.only("query", "category", "max-price"),
      hxTarget  := HxTarget.find(".results-container"),
      hxSwap    := HxSwap.InnerHTML.transition
    )

    println("7. Advanced - Price Range Filter:")
    println(s"   Trigger: change with 500ms throttle")
    println(s"   Prevents excessive requests while dragging slider")
    println(s"   Rendered: $priceRangeInput\n")

    // Part 8: Bonus - polling for real-time updates
    val liveUpdatesDiv = div(
      id        := "live-updates",
      hxGet     := "/api/trending",
      hxTrigger := HxTrigger.every(10.seconds),
      hxSwap    := HxSwap.InnerHTML,
      "Trending products..."
    )

    println("8. Bonus - Live Updates Panel:")
    println(s"   Polls /api/trending every 10 seconds")
    println(s"   Keeps trending products fresh without user action")
    println(s"   Rendered: $liveUpdatesDiv\n")

    println("=== Type Safety in Action ===")
    println("All HTMX attributes are compile-time checked:")
    println("✓ HxTrigger validates event names and modifiers")
    println("✓ HxSwap ensures correct strategy and modifier combinations")
    println("✓ HxParams prevents typos in parameter names")
    println("✓ HxTarget validates selector syntax")
    println("✓ No raw strings = no HTMX syntax errors at runtime")
    println("\n✓ Complete realistic example built successfully")
  }
}
