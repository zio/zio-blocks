---
id: sse
title: "Server-Sent Events"
sidebar_label: "Server-Sent Events"
---

`DatastarEvent` builds the events a server sends to patch a live page: replace elements, patch signals, execute a script, or remove elements. Each constructor returns a builder for its event kind, and `DatastarEvent#renderSSE` produces the wire format. `DatastarEvent.executeScript` is the exception noted below: it reuses the element-patch builder, so it offers two options that do not apply to it. Supporting types: `PatchElementsBuilder`, `PatchSignalsBuilder`, `RemoveElementsBuilder`, `ElementPatchMode`, `EventType`. The constructors and the one method they all end in:

```scala
sealed trait DatastarEvent {
  def renderSSE: String
  def renderSSEBytes: Array[Byte]
}

object DatastarEvent {
  def patchElements(elements: Dom): PatchElementsBuilder
  def patchSignals(first: SignalUpdate[_], rest: SignalUpdate[_]*): PatchSignalsBuilder
  def patchSignalsRaw(json: String): PatchSignalsBuilder
  def executeScript(code: Js): PatchElementsBuilder
  def removeElements(selector: CssSelector): RemoveElementsBuilder
}
```

## Motivation

The Datastar response protocol is SSE with structure inside the `data:` field. A patch looks like this on the wire:

```
event: datastar-patch-elements
data: selector #results
data: mode append
data: elements <li>Widget</li>

```

Three things make that easy to get wrong by hand. The event name must match the protocol exactly. The `data:` lines are a small keyed format, not free text, and the keys differ per event kind. And the terminating blank line is required — omit it and the browser waits indefinitely for an event it already has.

`DatastarEvent` removes all three concerns. Each constructor knows its event name, each builder method maps to one protocol field, and `DatastarEvent#renderSSE` delegates the SSE envelope to `ServerSentEvent`, which supplies the terminator.

## Quick Showcase

Build an event and render it:

```scala mdoc:silent
import zio.blocks.html._
import zio.http.datastar._

val count = Signal[Int]("count")
```

A signal patch is one line of data:

```scala mdoc
DatastarEvent.patchSignals(count := 42).renderSSE
```

An element patch carries its target and mode:

```scala mdoc
DatastarEvent
  .patchElements(li("Widget"))
  .selector(CssSelector.id("results"))
  .mode(ElementPatchMode.Append)
  .renderSSE
```

## Patching Elements

`DatastarEvent.patchElements` takes a `Dom` and returns a `PatchElementsBuilder`. Its options map one-to-one onto protocol fields:

| Method | Field | Default |
| ------ | ----- | ------- |
| `selector(CssSelector)` | `selector` | omitted — patch by element id |
| `mode(ElementPatchMode)` | `mode` | `Outer`, which is omitted |
| `viewTransition` | `useViewTransition true` | omitted |
| `namespace(String)` | `namespace` | omitted |
| `eventId(String)` | SSE `id:` | omitted |
| `retry(Long)` | SSE `retry:` | omitted |

Every option is omitted when unset, so the minimal event is just the elements:

```scala mdoc:silent:reset
import zio.blocks.html._
import zio.http.datastar._

val row = li("Widget")
```

With no selector, Datastar matches on the element's own id, and `mode` is absent because `Outer` is the default:

```scala mdoc
DatastarEvent.patchElements(row).renderSSE
```

Adding options adds exactly the corresponding lines:

```scala mdoc
DatastarEvent.patchElements(row).selector(CssSelector.id("list")).viewTransition.eventId("evt-1").renderSSE
```

:::note[`mode` is omitted when it is `Outer`]
`ElementPatchMode.Outer` is the protocol default, so setting it explicitly produces no `mode` line. That is intentional and keeps the payload minimal; do not read the absence of a `mode` line as "no mode".
:::

### ElementPatchMode

Eight modes decide where the content lands relative to the target. Each renders as its lowercase name:

| Mode | Effect |
| ---- | ------ |
| `Outer` | Morph the target element into the new content *(default)* |
| `Inner` | Morph the target's children |
| `Replace` | Replace the target element without morphing |
| `Prepend` | Insert before the target's existing children |
| `Append` | Insert after the target's existing children |
| `Before` | Insert immediately before the target |
| `After` | Insert immediately after the target |
| `Remove` | Remove the target |

`Append` is the one to reach for when adding to a list without re-rendering it:

```scala mdoc
ElementPatchMode.Append.render
ElementPatchMode.Inner.render
```

## Patching Signals

`DatastarEvent.patchSignals` takes one or more `SignalUpdate`s and renders them as a single JSON object under the `signals` key:

```scala mdoc:silent:reset
import zio.blocks.html._
import zio.http.datastar._

val price    = Signal[Double]("price")
val quantity = Signal[Int]("quantity")
```

Multiple updates go in one event rather than one event each:

```scala mdoc
DatastarEvent.patchSignals(price := 19.99, quantity := 3).renderSSE
```

`PatchSignalsBuilder#onlyIfMissing` adds `onlyIfMissing true`, which tells Datastar to set the signal only when it is not already present — the way to supply a default without clobbering client state:

```scala mdoc
DatastarEvent.patchSignals(price := 0.0).onlyIfMissing.renderSSE
```

### `DatastarEvent.patchSignalsRaw` — pre-serialized JSON

When the JSON already exists — from a cache, or a shape no `Schema` describes — `DatastarEvent.patchSignalsRaw` takes it verbatim:

```scala mdoc
DatastarEvent.patchSignalsRaw("""{"count":7}""").renderSSE
```

:::warning[`patchSignalsRaw` does not validate]
The string is inserted into the payload unchanged. Malformed JSON produces an event the browser silently discards, and there is no server-side error. Prefer `DatastarEvent.patchSignals` with typed updates, which cannot produce invalid JSON.
:::

## Executing Scripts

`DatastarEvent.executeScript` takes a `Js` and returns a `PatchElementsBuilder` — because on the wire it *is* an element patch, appending a `<script>` element that the browser executes:

```scala mdoc:silent:reset
import zio.blocks.html._
import zio.http.datastar._
```

The rendered event shows the mechanism rather than hiding it:

```scala mdoc
DatastarEvent.executeScript(js"console.log('done')").renderSSE
```

The script is appended to `body` and carries `data-effect="el.remove()"`, so it deletes itself after running and leaves no residue in the DOM.

Because the return type is `PatchElementsBuilder`, the element-patch options are all available — including `selector` and `mode`, which would override that targeting and rarely make sense here. Treat the builder as offering `PatchElementsBuilder#eventId` and `PatchElementsBuilder#retry`.

## Removing Elements

`DatastarEvent.removeElements` takes a `CssSelector` and returns a `RemoveElementsBuilder` with a deliberately narrow surface — `viewTransition`, `namespace`, `RemoveElementsBuilder#eventId`, and `RemoveElementsBuilder#retry`:

```scala mdoc
DatastarEvent.removeElements(CssSelector.id("banner")).renderSSE
```

There is no `mode` or `selector` method, because the selector is the argument and the mode is fixed to `remove` with an empty element body. That is the builder pattern doing its job: an option that would be meaningless is not offered.

## EventType

`EventType` names the protocol event in the SSE `event:` field. Two values, and you rarely name them directly since each constructor selects the right one:

| Value | Renders |
| ----- | ------- |
| `EventType.PatchElements` | `datastar-patch-elements` |
| `EventType.PatchSignals` | `datastar-patch-signals` |

Both render the protocol string rather than the Scala name:

```scala mdoc
EventType.PatchElements.render
EventType.PatchSignals.render
```

Both element patches and script execution use `PatchElements`, and removal does too — the distinction between them is in the `data:` body, not the event name.

## Streaming Several Events

`DatastarEvent#renderSSE` produces one complete event including its terminating blank line, so a stream is a concatenation. Nothing in the module manages the stream itself; you write the strings to whatever response body your HTTP layer uses:

```scala mdoc:silent:reset
import zio.blocks.html._
import zio.http.datastar._

val progress = Signal[Int]("progress")

val stream: String =
  (1 to 3).map(step => DatastarEvent.patchSignals(progress := step * 33).renderSSE).mkString
```

Each event is self-delimiting, so concatenating what `DatastarEvent#renderSSE` returns is valid SSE:

```scala mdoc
stream
```

Set `PatchElementsBuilder#eventId` when the client should be able to resume with `Last-Event-ID`, and `PatchElementsBuilder#retry` to control the reconnection delay — both are ordinary SSE fields handled by `ServerSentEvent`.

## Bytes Output

Every builder also offers `renderSSEBytes`, which returns the exact UTF-8 bytes of `renderSSE`: `renderSSEBytes.sameElements(renderSSE.getBytes(StandardCharsets.UTF_8))` holds for every event, including malformed input. (`new String(bytes, UTF_8) == renderSSE` additionally holds for valid Unicode, but not for lone surrogates: the bytes encode them as `?` exactly like `String.getBytes`, while the `String` retains the surrogate.) The bytes are encoded directly into a fresh array — the whole-event string is never built and `String#getBytes` is never called — while field ordering, optional-field handling, CR/LF/CRLF splitting, `id`/`retry` validation, and blank-line framing share one implementation, so the two outputs cannot drift apart:

```scala mdoc:silent:reset
import zio.blocks.html._
import zio.http.datastar._

val count = Signal[Int]("count")
```

```scala mdoc
val bytes: Array[Byte] = DatastarEvent.patchSignals(count := 42).renderSSEBytes
```

Two deliberate limits. First, per-event payloads still render through intermediate strings (`Dom#renderMinified` for elements, the signals JSON string); only the SSE framing itself is fused into the byte output, so it removes the whole-event `String` allocation and the subsequent traversal/encoding of that `String` — the direct encoding still performs its own single UTF-8 pass, and no throughput claim is made without a benchmark. Second, malformed UTF-16 encodes exactly like `String.getBytes(StandardCharsets.UTF_8)`: lone surrogates become `?`, and a high surrogate split across an internal append boundary still pairs with the low surrogate that follows it (empty appends never flush the buffered high surrogate).

Each call returns a new array that the caller owns — the implementation never retains or reuses it. Byte-oriented HTTP bodies that wrap without copying are therefore safe as long as the caller does not mutate the array afterwards. On the zio-http 4.x line the source-verified byte contract is `zio.blocks.chunk.Chunk[Byte]`: the server transport tests move chunk payloads (see `H2IntegrationSpec` in a v4 snapshot checkout), and `Chunk.fromArray` wraps without copying under a must-not-mutate contract. Note the scope honestly: the legacy `Body.fromArray`/`Body.fromChunk` constructors were read in the same snapshot but reference model types with no definitions there, so they are not presented as a compile-verified 4.x route, and no end-to-end HTTP integration was compiled in this task — only the `Chunk.fromArray` snippet below, which compiles against zio-blocks:

```scala mdoc:silent
import zio.blocks.chunk.Chunk

val payload: Chunk[Byte] = Chunk.fromArray(bytes) // wraps; do not mutate `bytes` afterwards
```

Set the response `Content-Type` to `text/event-stream` (plus `Cache-Control: no-cache`) in the HTTP layer; the module itself stays HTTP-independent and produces only the event bytes.

## Integration Points

`DatastarEvent#renderSSE` delegates to `ServerSentEvent` from `zio-blocks-http-model`, which supplies the SSE envelope, the field ordering, and the terminating blank line — see [ServerSentEvent](../http-model/server-sent-event.md). The Datastar-specific part is the event name and the keyed `data:` body.

Element patches carry `Dom` values and `CssSelector` targets from [HTML](../html.md), and signal patches carry [SignalUpdate](./signals.md) values whose JSON comes from the type's `Schema`. What the patched page does with the result is determined by the [Attributes](./attributes.md) and [Event Handlers](./events.md) rendered into it.
