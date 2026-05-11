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
 * HTMX DSL — Advanced Patterns
 *
 * Demonstrates complex HTMX interactions: combining multiple triggers, chaining
 * modifiers, controlling request queuing, animation with transitions, and
 * JavaScript-based filtering. Shows how modifiers compose to create
 * sophisticated client-side behaviors.
 *
 * Run with: sbt "zio-blocks-htmx-examples/runMain
 * zioBlocksHtmx.AdvancedPatterns"
 */
@main def AdvancedPatterns(): Unit = {

  println("=== HTMX Advanced Patterns ===\n")

  // Example 1: Debounced search with state modifier
  println("1. Debounced search with value-changed detection:")
  val debouncedSearch = input(
    `type`      := "text",
    name        := "query",
    placeholder := "Type to search...",
    hxPost      := "/api/search",
    hxTrigger   := HxTrigger.input.delay(500.millis).changed,
    hxParams    := HxParams.only("query"),
    hxTarget    := HxTarget.next("div"),
    hxSwap      := HxSwap.InnerHTML
  )
  println(s"  Trigger: ${HxTrigger.input.delay(500.millis).changed.render}")
  println(s"  Element: $debouncedSearch\n")

  // Example 2: Rate-limited polling with throttle
  println("2. Rate-limited polling (at most 1 request per second):")
  val throttledStatus = div(
    id        := "status",
    hxPost    := "/api/status",
    hxTrigger := HxTrigger.input.throttle(1.second),
    hxSwap    := HxSwap.InnerHTML,
    "Typing status..."
  )
  println(s"  Trigger: ${HxTrigger.input.throttle(1.second).render}")
  println(s"  Element: $throttledStatus\n")

  // Example 3: Multiple triggers with polling and user action
  println("3. Dual triggers: user click OR automatic polling:")
  val autoRefresh = div(
    id        := "data",
    hxGet     := "/api/data",
    hxTrigger := HxTrigger(
      HxTrigger.click,
      HxTrigger.every(30.seconds)
    ),
    hxSwap := HxSwap.InnerHTML.transition.settle(300.millis),
    "Data refreshes on click or every 30s"
  )
  println(s"  Triggers: ${HxTrigger(HxTrigger.click, HxTrigger.every(30.seconds)).render}")
  println(s"  Element: $autoRefresh\n")

  // Example 4: Request queuing strategy
  println("4. Queue strategy: keep only most recent request:")
  val queuedInput = input(
    `type`      := "text",
    placeholder := "Fast typing...",
    hxPost      := "/api/validate",
    hxTrigger   := HxTrigger.input.delay(300.millis).queue(HxTrigger.QueueStrategy.Last),
    hxTarget    := HxTarget.next("span"),
    hxSwap      := HxSwap.InnerHTML
  )
  println(s"  Trigger: ${HxTrigger.input.delay(300.millis).queue(HxTrigger.QueueStrategy.Last).render}")
  println(s"  Element: $queuedInput\n")

  // Example 5: Animation with timing modifiers
  println("5. CSS transition with settle delay:")
  val animatedSwap = button(
    hxPost    := "/api/action",
    hxTrigger := HxTrigger.click,
    hxSwap    := HxSwap.InnerHTML.transition.settle(400.millis),
    hxTarget  := HxTarget.This,
    "Click for animated response"
  )
  println(s"  Swap: ${HxSwap.InnerHTML.transition.settle(400.millis).render}")
  println(s"  Element: $animatedSwap\n")

  // Example 6: Event delegation with target modifier
  println("6. Event delegation: parent listens to child clicks:")
  val eventDelegation = ul(
    id        := "items",
    hxPost    := "/api/item-clicked",
    hxTrigger := HxTrigger.click.target("li"),
    hxSwap    := HxSwap.InnerHTML,
    li("Item 1"),
    li("Item 2"),
    li("Item 3")
  )
  println(s"  Trigger: ${HxTrigger.click.target("li").render}")
  println(s"  Element: $eventDelegation\n")

  // Example 7: JavaScript filtering
  println("7. JavaScript filter: only trigger if condition met:")
  val filteredTrigger = input(
    `type`      := "text",
    placeholder := "Min 3 characters...",
    hxPost      := "/api/search",
    hxTrigger   := HxTrigger.input.filter(Js("event.target.value.length > 2")),
    hxTarget    := HxTarget.next("div"),
    hxSwap      := HxSwap.InnerHTML
  )
  println(s"  Trigger: ${HxTrigger.input.filter(Js("event.target.value.length > 2")).render}")
  println(s"  Element: $filteredTrigger\n")

  // Example 8: Source control with from modifier
  println("8. Source control: button listens to input events:")
  val sourceControl = div(
    input(
      id          := "query-input",
      `type`      := "text",
      placeholder := "Type here..."
    ),
    button(
      id        := "search-btn",
      hxPost    := "/api/search",
      hxTrigger := HxTrigger.click.from("#query-input"),
      hxTarget  := HxTarget.next("div"),
      "Search"
    )
  )
  println(s"  Button trigger: ${HxTrigger.click.from("#query-input").render}")
  println(s"  Element: $sourceControl\n")

  // Example 9: Intersection observer with threshold
  println("9. Lazy loading with intersection observer:")
  val lazyLoad = img(
    src       := "/placeholder.jpg",
    hxGet     := "/api/lazy-image",
    hxTrigger := HxTrigger.intersect.threshold(0.5),
    hxSwap    := HxSwap.OuterHTML,
    alt       := "Lazy loaded image"
  )
  println(s"  Trigger: ${HxTrigger.intersect.threshold(0.5).render}")
  println(s"  Element: $lazyLoad\n")

  // Example 10: Complex modifier chain
  println("10. Complex modifier chain - multiple modifiers:")
  val complexChain = input(
    `type`      := "text",
    name        := "search",
    placeholder := "Advanced search",
    hxPost      := "/api/search",
    hxTrigger   := HxTrigger.input
      .delay(500.millis)
      .throttle(1.second)
      .changed
      .filter(Js("event.target.value.trim().length > 0")),
    hxParams := HxParams.only("search"),
    hxTarget := HxTarget.closest(".search-results"),
    hxSwap   := HxSwap.InnerHTML.transition.settle(250.millis)
  )
  println(
    s"  Complex trigger: ${HxTrigger.input.delay(500.millis).throttle(1.second).changed.filter(Js("event.target.value.trim().length > 0")).render}"
  )
  println(s"  Swap: ${HxSwap.InnerHTML.transition.settle(250.millis).render}")
  println(s"  Element: $complexChain\n")

  // Example 11: Scroll positioning after swap
  println("11. Scroll to bottom after appending messages:")
  val messageList = div(
    id        := "messages",
    hxGet     := "/api/messages",
    hxTrigger := HxTrigger.every(2.seconds),
    hxSwap    := HxSwap.BeforeEnd.scroll(HxSwap.ScrollPosition.Bottom).show(HxSwap.ShowPosition.Bottom),
    "Messages will be appended and scrolled into view"
  )
  println(s"  Swap: ${HxSwap.BeforeEnd.scroll(HxSwap.ScrollPosition.Bottom).show(HxSwap.ShowPosition.Bottom).render}")
  println(s"  Element: $messageList\n")

  println("✓ Advanced pattern examples complete")
}
