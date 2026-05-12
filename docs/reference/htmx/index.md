---
id: index
title: HTMX
---

`zio.http.htmx` is a **typed HTMX DSL** for building safe, compile-time HTMX attribute declarations within `zio.blocks.html`. It provides immutable types representing HTMX events, swap strategies, target selectors, and request modifiers, eliminating stringly-typed misuse through rich domain types while maintaining explicit string surfaces for URLs and raw JavaScript where needed.

Core types: `HxTrigger`, `HxSwap`, `HxTarget`, `HxParams`, `HxUrlUpdate`, `HxEncoding`, `HxSync`, `HtmxAttrKey`, `ToHtmxValue`.

Here are the core patterns of typed HTMX construction:

```scala mdoc:compile-only
import zio.blocks.html._
import zio.http.htmx._
import scala.concurrent.duration._

// Compile-safe event triggers with modifiers
div(hxPost := "/search", hxTrigger := HxTrigger.input.delay(500.millis))

// Typed swap strategies with multiple modifiers
div(hxSwap := HxSwap.InnerHTML.swap(1.second).transition)

// Type-safe target selection
button(hxTarget := HxTarget.closest("form"))

// Request control through domain types
form(hxParams := HxParams.only("query", "page"))
```

## Introduction

The `htmx` module eliminates accidental stringly mistakes in HTMX attribute construction. Rather than writing raw strings like `"innerHTML swap:1s settle:500ms transition:true"`, you compose domain values through a type-safe DSL that guides you toward correct HTMX syntax at compile time.

Most attributes are narrower than plain HTML strings. `hxSwap` accepts `HxSwap`, `hxTarget` accepts `HxTarget`, and selector-only attributes accept `CssSelector`. When a string surface is genuinely necessary—for URLs, custom JavaScript filters, or dynamic values—the DSL provides explicit entry points.

## Motivation

HTMX introduces many new attributes and modifier combinations. Writing these as raw strings is error-prone:
- Typos in strategy names (`"innterHTML"`, `"delya:1s"`) silently fail at runtime
- Mixing unrelated modifiers in the same attribute (`"innerHTML queue:first threshold:0.5"`) compiles but confuses intent
- URLs and JavaScript filters have no type guidance, leading to unsafe assumptions

The typed HTMX DSL catches these mistakes at compile time:
- Strategy names are exhaustive case objects: `HxSwap.InnerHTML`, `HxSwap.AfterBegin`, etc.
- Modifier methods enforce correct grouping: repeated calls within the same modifier group (`swap()` or `settle()`) replace earlier values, while different groups (`swap`, `settle`, `transition`) can be combined
- Type-safe attributes like `hxTarget` prevent passing raw strings where structured selectors belong
- Extensible type class `ToHtmxValue` lets custom domain values render themselves to HTMX syntax

## Installation

Add the HTMX module to your project dependencies:

```scala
// JVM
libraryDependencies += "dev.zio" %% "zio-blocks-http-htmx" % "@VERSION@"

// Scala.js
libraryDependencies += "dev.zio" %%% "zio-blocks-http-htmx" % "@VERSION@"
```

Supported Scala versions: 2.13.x and 3.x. The module is cross-compiled for JVM and Scala.js.

## Overview

Each type in the module addresses a specific HTMX concern:

**Event Triggering:** `HxTrigger` declares which event fires a request (click, input, load, custom). Modifiers add timing (`HxTrigger#delay`, `HxTrigger#throttle`), use `HxTrigger#filter` for filtering, source control (`from`), and queue strategy (`queue`). `HxTriggerSet` composes multiple triggers in a comma-separated list for more complex interactions.

**Swap Strategies:** `HxSwap` selects how the response content replaces the DOM (innerHTML, outerHTML, beforeBegin, etc.). Modifiers control timing with `HxSwap#swap` and `HxSwap#settle`, animation (`transition`), scrolling (`scroll`, `show`), and focus behavior (`focusScroll`, `ignoreTitle`).

**Target Selection:** `HxTarget` selects where swap happens (current element, closest ancestor, next sibling, custom CSS selector). Variants support common patterns like `HxTarget.This`, `HxTarget.closest(selector)`, and `HxTarget.next(selector)`.

**Request Control:** `HxParams` filters which form fields are submitted (all, none, only listed, all except listed). `HxUrlUpdate` controls whether the URL bar updates after the request. `HxEncoding` and `HxSync` handle multi-step request concerns.

**Infrastructure:** `HtmxAttrKey` is the typed attribute key binding a name to a value type. `ToHtmxValue` is the type class rendering domain values to HTMX attribute strings, enabling custom types to participate in the DSL.

## How They Work Together

A typical HTMX request flow combines multiple types: trigger defines when, swap defines what, target defines where, and request control refines how. Here is the overall flow:

```
User Action
    ↓
HxTrigger (when: click, input, load, every N seconds, etc.)
    ├─ Modifiers: delay, throttle, filter, from, queue
    ↓
Request Sent
    ├─ URL: hxPost, hxGet, hxPut, hxPatch, hxDelete
    ├─ Parameters: HxParams (all, none, only, not)
    ├─ Encoding: HxEncoding (multipart override; default is URL-encoded)
    └─ Synchronization: HxSync (queue strategy, abort behavior)
    ↓
Response Received
    ↓
HxTarget (where: this, closest, find, next, previous, css selector)
    ↓
HxSwap (how: innerHTML, outerHTML, beforeBegin, etc.)
    ├─ Modifiers: swap delay, settle delay, transition, scroll, show
    └─ Focus: ignoreTitle, focusScroll
    ↓
DOM Updated & Settled
```

**Example workflow:** When a user types in a search input, fire a POST request to `/api/search` after a 500ms delay. Send only the query and page parameters. Replace the results section (the closest parent with CSS class `results`) with innerHTML strategy, wait 250ms for CSS transitions, then scroll the results into view:

```scala mdoc:compile-only
import zio.blocks.html._
import zio.http.htmx._
import scala.concurrent.duration._

input(
  placeholder := "Search...",
  hxPost := "/api/search",
  hxTrigger := HxTrigger.input.delay(500.millis),
  hxParams := HxParams.only("query", "page"),
  hxTarget := HxTarget.closest(".results"),
  hxSwap := HxSwap.InnerHTML.settle(250.millis).scroll(HxSwap.ScrollPosition.Top)
)
```

This example shows how types guide each concern: `HxTrigger.input` is compile-checked (not a typo), `HxTrigger#delay` is a method not a string, `HxParams.only()` is exhaustively typed, `HxTarget.closest()` takes a string but validates non-emptiness, and `HxSwap` chains modifiers with type safety.

## Common Patterns

The HTMX DSL supports several common interaction patterns. Here are representative examples:

### Pattern 1: Progressive Enhancement with Boost

Use `hxBoost := true` to transform regular links and form submissions into HTMX requests, maintaining server-side rendering and graceful degradation:

```scala mdoc:compile-only
import zio.blocks.html._
import zio.http.htmx._

// Entire nav boosted—clicks are HTMX requests, but work without JS
nav(
  hxBoost := true,
  ul(
    li(a(href := "/", "Home")),
    li(a(href := "/products", "Products")),
    li(a(href := "/contact", "Contact"))
  )
)
```

### Pattern 2: Polling and Periodic Updates

Use `HxTrigger.every()` with a duration to poll an endpoint at regular intervals:

```scala mdoc:compile-only
import zio.blocks.html._
import zio.http.htmx._
import scala.concurrent.duration._

// Poll every 2 seconds for new notifications
div(
  id := "notifications",
  hxGet := "/api/notifications",
  hxTrigger := HxTrigger.every(2.seconds),
  hxSwap := HxSwap.InnerHTML
)
```

### Pattern 3: Chained Modifiers for Complex Interactions

Combine multiple trigger modifiers to refine when and how a request fires:

```scala mdoc:compile-only
import zio.blocks.html._
import zio.http.htmx._
import scala.concurrent.duration._

// Fire on input, throttle to once per second, only if value changed
input(
  hxPost := "/search",
  hxTrigger := HxTrigger.input.throttle(1.second).changed,
  hxSwap := HxSwap.InnerHTML
)
```

Modifier methods return updated `HxTrigger` instances, so you can chain: `HxTrigger.click.delay(100.millis).once` creates a single-fire click handler with a delay.

### Pattern 4: Out-of-Band Swaps

Update multiple DOM regions with a single response using `hxSwapOob` (out-of-bounds):

```scala mdoc:compile-only
import zio.blocks.html._
import zio.http.htmx._

// Main content updates inline; status badge updates separately
div(
  hxPost := "/api/action",
  hxSwap := HxSwap.InnerHTML,
  button("Submit"),
  div(id := "status", hxSwapOob := HxSwapOob.using(HxSwap.InnerHTML), "Ready")
)
```

The response contains both the new main content and a separate element targeted by `hxSwapOob`, allowing one request to update multiple areas.

### Pattern 5: Conditional Rendering with JavaScript Filters

Use `HxTrigger#filter` with the `Js` type to add a JavaScript condition that gates the request:

```scala mdoc:compile-only
import zio.blocks.html._
import zio.http.htmx._

input(
  hxPost := "/search",
  hxTrigger := HxTrigger.input.filter(Js("event.target.value.length > 2")),
  "Only POST if search has 3+ characters"
)
```

The `Js` type is intentionally raw—do not build it from unsanitized user input.

## Integration Points

**With `zio.blocks.html`:** All HTMX attributes integrate seamlessly into the HTML DSL. `HtmxAttributes` is mixed into the `zio.http.htmx` package object, making attributes like `hxPost`, `hxTrigger`, and `hxSwap` directly available when you `import zio.http.htmx._`.

**With `zio.blocks.schema`:** URL-bearing attributes accept `URL` and `Path` types from `zio.http`, which are then rendered to valid URL strings. The `ToHtmxValue` type class provides encoding for `URL`, `Path`, and `Json` values. For domain values, use `HxVals.from(...)` and `HxHeadersValue.from(...)` to encode values via their `Schema` to JSON.

**With CSS selectors:** Attributes that accept selectors (hxTarget, hxSelect, hxDisabledElt, hxIndicator) accept `CssSelector` from `zio.blocks.html`, providing type-safe selector construction.

**With JavaScript:** Attributes that accept raw JavaScript expressions (`hxOn:*`, `hxTrigger.filter()`) accept the `Js` type, making it explicit that you're writing unescaped JavaScript code. This prevents accidental XSS while allowing intentional dynamic behavior.

**With headers:** The `zio.http.htmx.headers` submodule provides typed HTMX request/response headers (HX-Request, HX-Trigger, HX-Redirect, etc.), letting you inspect and build headers with the same type safety as attributes.

**Extending with custom types:** Implement `ToHtmxValue[MyType]` to let your domain types render themselves in the DSL. For example, a custom `enum Status { Active, Inactive }` defines `implicit val statusToHtmx: ToHtmxValue[Status] = ...` and renders directly in HTMX attributes.

## Running the Examples

All code from this guide is available as runnable examples in the `zio-blocks-htmx-examples` module.

**1. Clone the repository and navigate to the project:**

Start by cloning the repository and entering the project directory:

```bash
git clone https://github.com/zio/zio-blocks-modern.git
cd zio-blocks-modern
```

**2. Run individual examples with sbt:**

### Basic Usage

Demonstrates fundamental HTMX attribute construction: triggering requests (hxPost, hxGet), swapping strategies (InnerHTML, OuterHTML), and target selection (This, closest, find). Shows how the typed DSL ensures correct HTMX syntax at compile time. Here is the source code:

```scala mdoc:passthrough
import docs.SourceFile

SourceFile.print("zio-blocks-htmx-examples/src/main/scala/zioBlocksHtmx/BasicUsage.scala")
```

([source](https://github.com/zio/zio-blocks-modern/blob/main/zio-blocks-htmx-examples/src/main/scala/zioBlocksHtmx/BasicUsage.scala))

Run this example with the following command:

```bash
sbt "zio-blocks-htmx-examples/runMain zioBlocksHtmx.BasicUsage"
```

### Advanced Patterns

Demonstrates complex HTMX interactions: combining multiple triggers, chaining modifiers, controlling request queuing, animation with transitions, and JavaScript-based filtering. Shows how modifiers compose to create sophisticated client-side behaviors. Here is the source code:

```scala mdoc:passthrough
import docs.SourceFile

SourceFile.print("zio-blocks-htmx-examples/src/main/scala/zioBlocksHtmx/AdvancedPatterns.scala")
```

([source](https://github.com/zio/zio-blocks-modern/blob/main/zio-blocks-htmx-examples/src/main/scala/zioBlocksHtmx/AdvancedPatterns.scala))

Run this example with the following command:

```bash
sbt "zio-blocks-htmx-examples/runMain zioBlocksHtmx.AdvancedPatterns"
```

### Complete Example

A realistic e-commerce search and filtering interface combining multiple HTMX attributes: debounced search input, live category filtering, paginated results, out-of-band notifications, and dynamic UI updates. Demonstrates how types compose to create a type-safe, interactive UI. Here is the source code:

```scala mdoc:passthrough
import docs.SourceFile

SourceFile.print("zio-blocks-htmx-examples/src/main/scala/zioBlocksHtmx/CompleteExample.scala")
```

([source](https://github.com/zio/zio-blocks-modern/blob/main/zio-blocks-htmx-examples/src/main/scala/zioBlocksHtmx/CompleteExample.scala))

Run this example with the following command:

```bash
sbt "zio-blocks-htmx-examples/runMain zioBlocksHtmx.CompleteExample"
```

**3. Or compile all examples at once:**

To compile all example sources without running them, use:

```bash
sbt "zio-blocks-htmx-examples/compile"
```
