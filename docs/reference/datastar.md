---
id: datastar
title: "Datastar"
---

`zio-blocks-datastar` provides a type-safe Scala SDK for [Datastar](https://data-star.dev/), the hypermedia framework that brings reactive UIs via server-sent events (SSE). It builds on `zio-blocks-html` for DOM construction and `zio-blocks-schema` for JSON serialization.

## Installation

```scala
// JVM
libraryDependencies += "dev.zio" %% "zio-blocks-datastar" % "@VERSION@"

// Scala.js
libraryDependencies += "dev.zio" %%% "zio-blocks-datastar" % "@VERSION@"
```

## Signals

A `Signal[A]` represents a named reactive signal on the client. Use `:=` to pair it with a value, producing a `SignalUpdate` ready for SSE transmission:

```scala
import zio.blocks.datastar._
import zio.blocks.schema.Schema

case class User(name: String, age: Int)
object User {
  implicit val schema: Schema[User] = Schema.derived
}

val count    = Signal[Int]("count")
val username = Signal[String]("username")

// Create signal updates (serializes via Schema's cached JSON codec)
val update1 = count := 0
val update2 = username := "Alice"
```

## SSE Events

`DatastarEvent` is the sealed trait representing events sent to the browser. Use the companion object's builder methods:

### patchSignals

Send signal updates to the client:

```scala
val sse = DatastarEvent
  .patchSignals(count := 42, username := "Bob")
  .renderSSE

// With options:
val sseWithOptions = DatastarEvent
  .patchSignals(count := 1)
  .withOnlyIfMissing
  .withEventId("evt-1")
  .withRetry(5000)
  .renderSSE
```

### patchElements

Send DOM fragments to the client:

```scala
import zio.blocks.html._

val sse = DatastarEvent
  .patchElements(div(id := "status")("Online"))
  .withSelector(CssSelector.id("status"))
  .withMode(ElementPatchMode.Inner)
  .withViewTransition
  .renderSSE
```

### executeScript

Run JavaScript on the client:

```scala
val sse = DatastarEvent
  .executeScript(js"console.log('hello')")
  .renderSSE
```

### removeElements

Remove elements matching a CSS selector:

```scala
val sse = DatastarEvent
  .removeElements(CssSelector.id("old-item"))
  .renderSSE
```

## Attribute DSL

Import `zio.blocks.datastar._` to get all `data-*` attribute constructors. These integrate with the `zio-blocks-html` DSL:

```scala
import zio.blocks.html._
import zio.blocks.datastar._

val count    = Signal[Int]("count")
val username = Signal[String]("username")

div(
  dataSignals(count := 0),
  dataText := count
)(
  span()("Count: "),
  button(dataOn.click := js"$count++")("Increment")
)
```

Datastar expression positions are intentionally stricter than generic HTML/JS templating.
Raw `String` values are rejected in Datastar expression attributes; use `js"..."`
for expressions, or typed values like `Signal`, `SignalUpdate`, and `DatastarRef`.

```scala
dataText := count
dataText := count.ref
dataOn.click := js"$count++"

// does not compile:
// dataOn.click := "$count++"
```

### Available Attributes

| Method | Datastar Attribute | Description |
|--------|-------------------|-------------|
| `dataSignals(signal)` | `data-signals:name` | Declare one keyed signal with an initial value |
| `dataSignals(update, updates...)` | `data-signals` | Patch multiple signals with one object expression |
| `dataBind(signal)` | `data-bind:name` | Two-way bind an input to a signal |
| `dataText` | `data-text` | Set element text content |
| `dataShow` | `data-show` | Conditionally show/hide element |
| `dataClass("name")` | `data-class:name` | Toggle CSS class |
| `dataOn.click` | `data-on:click` | Event handler |
| `dataComputed(signal)` | `data-computed:name` | Computed signal |
| `dataEffect` | `data-effect` | Side-effect expression |
| `dataIndicator(signal)` | `data-indicator:name` | Loading indicator signal |
| `dataRef("name")` | `data-ref:name` | Element reference |
| `dataInit` | `data-init` | Initialization expression |

### Event Modifiers

Chain modifiers on `dataOn` before assigning a handler:

```scala
// Debounce input by 300ms
dataOn.input.debounce(300) := username

// Click with prevent default, only once
dataOn.click.prevent.once := js"handleSubmit()"

// Throttle scroll, listen on window
dataOn.scroll.throttle(100).window := js"onScroll()"
```

Available modifiers: `debounce`, `debounceLeading`, `throttle`, `throttleLeading`, `delay`, `once`, `passive`, `capture`, `stop`, `prevent`, `outside`, `window`, `document`, `viewTransition`.
