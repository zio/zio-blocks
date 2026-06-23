---
id: getting-started-with-mux
title: "Getting Started with Mux"
description: "Learn how to manage multiplexed bidirectional message streams with capacity limits."
keywords: ["mux", "multiplexing", "message streams", "concurrency", "state machine"]
---

Welcome to Getting Started with Mux! This tutorial is for developers who want to understand how to manage concurrent, request-response style communication where multiple independent conversations happen over a single channel. You don't need any prior experience with multiplexing or the Mux library to follow along — we'll build your understanding step by step.

## Learning Objectives

By the end of this tutorial, you will understand:

- What a `Mux` is and the problem it solves
- How to create a mux and open streams within it
- The relationship between a protocol and your application code
- How `send()` and `receive()` work for application messages
- How `takeOutbound()` and `offerInbound()` work for protocol code
- The stream lifecycle: OPEN → HALF_CLOSED → CLOSED
- How to perform graceful shutdown (halfClose/signalRemoteClose) vs immediate closure (close)
- How to work with multiple independent streams
- When and how to handle errors from capacity limits
- Why thread safety matters and what it means in practice

We'll learn these concepts through:

1. **The Big Picture** — What Mux does and why it's useful
2. **Creating a Mux** — Your first mux and opening a stream
3. **Understanding Streams and Message Queues** — Two-way communication
4. **The Stream Lifecycle** — Open, half-close, and close
5. **Working with Multiple Streams** — Independence and isolation
6. **Managing Capacity** — When limits matter and how to handle them
7. **Thread Safety** — Who can call what, and from where
8. **Putting It Together** — A complete, runnable example
9. **Running the Examples** — Step-by-step commands

We recommend reading from top to bottom — each section builds on the previous one.

:::info[Scala 2 and Scala 3]
The code examples in this tutorial use **Scala 3 syntax**. In Scala 2.13, the API returns `Either[MuxError, T]` instead of union types, and pattern matching uses `case Right(value)` and `case Left(error)` instead of `case value` and `case error`. For complete API signatures with both syntaxes, see the [Mux Reference](../reference/mux.mdx).

**Example: Scala 2 equivalent:**
```scala
// Scala 3
stream.send("Hello") match {
  case () => println("Success")
  case error: MuxError => println(s"Error: $error")
}

// Scala 2
stream.send("Hello") match {
  case Right(()) => println("Success")
  case Left(error) => println(s"Error: $error")
}
```
:::

## The Big Picture

Imagine you're building a communication protocol: a client sends requests to a server over a network connection, and the server sends responses back. Both directions use the same channel, but they're independent of each other. The client doesn't need to wait for one response before sending the next request — it can send five requests in a row, get three responses, send one more request, then get two more responses.

This is multiplexing: packing multiple independent conversations into a single bidirectional channel.

A `Mux` is a registry of these conversations. It manages:

- **Streams**: Each stream is one independent conversation, identified by a numeric ID.
- **Two message queues per stream**: One for messages going out (outbound), one for messages coming in (inbound).
- **Capacity limits**: A per-stream queue capacity and a per-mux overall capacity to prevent memory exhaustion.
- **Lifecycle management**: Each stream transitions through well-defined states (OPEN, HALF_CLOSED, CLOSED) to ensure orderly shutdown.

The key insight: application code calls `send()` and `receive()` to exchange messages with its peer. Protocol code (your networking layer) calls `takeOutbound()` and `offerInbound()` to move messages in and out of the mux. The mux itself never touches the network — it just manages queues and state.

## 1. Creating a Mux

The simplest starting point is to create a mux and open your first stream. A `Mux` is parameterized by three types:

- The **stream ID type** — typically `Int` or `Long`
- The **outbound message type** — what your application sends out
- The **inbound message type** — what your application receives

Let's create a mux that carries `String` messages in both directions, with stream IDs as integers:

```scala mdoc:silent:reset
import zio.blocks.mux._

// Create a mux that carries String messages in both directions
val mux = Mux[Int, String, String](100)
```

The code above:
- `Mux[Int, String, String]` — creates a mux where streams are identified by `Int`, outbound messages are `String`, and inbound messages are also `String`
- `(100)` — the mux can have up to 100 concurrent streams. Each stream has its own message queues with a fixed capacity of 256 messages per direction (inbound and outbound)

Now let's open a stream and pattern-match on the result (because opening can fail if capacity is exceeded):

```scala mdoc:silent:reset
import zio.blocks.mux._

val mux = Mux[Int, String, String](100)

val streamOrError = mux.open(1)  // Try to open stream 1
streamOrError match {
  case stream: MuxStream[Int, String, String] =>
    println(s"Opened stream with ID 1: $stream")
  case error: MuxError =>
    println(s"Failed to open stream: $error")
}
```

The code above:
- `mux.open(1)` — attempts to open a new stream with ID `1`
- The result is either a `MuxStream` (success) or a `MuxError` (failure — e.g., capacity exceeded)
- We pattern-match to handle both cases

:::note[Why Pattern-Matching?]
Opening a stream can fail. If the mux has reached its capacity limit, `open()` returns a `CapacityExceeded` error instead of a stream. By pattern-matching, we ensure our code handles both outcomes.
:::

## 2. Understanding Streams and Message Queues

Once you've opened a stream, you have access to a two-way communication channel. The key insight is that there are two different perspectives:

- **Application perspective**: You call `send()` to send outbound messages and `receive()` to get inbound messages.
- **Protocol perspective**: The protocol calls `takeOutbound()` to extract messages your application sent, and `offerInbound()` to deliver messages it received from the peer.

Let's see how this works:

```scala mdoc:silent:reset
import zio.blocks.mux._

val mux = Mux[Int, String, String](100)
val stream = mux.open(1) match {
  case s: MuxStream[Int, String, String] => s
  case _ => ???
}

// Application code: send a message
stream.send("Hello from app") match {
  case () => println("✓ Message sent")
  case error: MuxError => println(s"✗ Send failed: $error")
}

// Protocol code: extract the message to send over network
val outbound = stream.takeOutbound() match {
  case Some(msg) => msg
  case None => "no message available"
  case error: MuxError => s"error: $error"
}
println(s"Protocol will send: $outbound")

// Protocol code: deliver a response from the peer
stream.offerInbound("Hello from peer") match {
  case () => println("✓ Response delivered")
  case error: MuxError => println(s"✗ Offer failed: $error")
}

// Application code: receive the response
val inbound = stream.receive() match {
  case Some(msg) => msg
  case None => "no message available"
  case error: MuxError => s"error: $error"
}
println(s"Application received: $inbound")
```

The code above demonstrates the `MuxStream` API:
- `MuxStream#send(msg)` returns `Unit | MuxError` (success or error); we pattern-match to handle both cases
- `MuxStream#takeOutbound()` returns `Option[String] | MuxError` (Some/None/error); we match all three
- `MuxStream#offerInbound(msg)` returns `Unit | MuxError` (success or error); we pattern-match to handle both
- `MuxStream#receive()` returns `Option[String] | MuxError` (Some/None/error); we match all three cases

:::tip[Two Queues, Two Roles]
Think of it as a pipeline: your application uses `send()` and `receive()`, the protocol uses `takeOutbound()` and `offerInbound()`. The mux sits in the middle, managing two FIFO queues per stream. This separation is intentional — it lets you reason about sending independently from receiving.
:::

## 3. The Stream Lifecycle

Every stream has a well-defined lifecycle with three states: OPEN, HALF_CLOSED, and CLOSED. This ensures graceful shutdown when both sides need to agree that communication is complete.

The state transitions look like this:

```
OPEN
├─ (local side calls halfClose()) ──→ HALF_CLOSED_LOCAL
│                                       └─ (remote side calls signalRemoteClose()) ──→ CLOSED
│
└─ (remote side calls signalRemoteClose()) ──→ HALF_CLOSED_REMOTE
                                                └─ (local side calls halfClose()) ──→ CLOSED
```

Let's walk through a complete lifecycle:

```scala mdoc:silent:reset
import zio.blocks.mux._

val mux = Mux[Int, String, String](100)
val stream = mux.open(1) match {
  case s: MuxStream[Int, String, String] => s
  case _ => ???
}

// Stream starts in OPEN state
println(s"Is stream closed? ${stream.isClosed}")
println(s"Is stream half-closed? ${stream.isHalfClosed}")

// Application is done sending, signals half-close
stream.halfClose()
println(s"After halfClose(), is half-closed? ${stream.isHalfClosed}")

// Protocol receives a close signal from the peer
stream.signalRemoteClose()
println(s"After signalRemoteClose(), is closed? ${stream.isClosed}")
```

The code above:
- `stream.isClosed` — checks if the stream is in CLOSED state
- `stream.isHalfClosed` — checks if the stream is in HALF_CLOSED state (one side done)
- `stream.halfClose()` — signals that this side is done sending (but can still receive)
- `stream.signalRemoteClose()` — signals that the peer is done sending
- Once you call both `halfClose()` and `signalRemoteClose()`, the stream becomes fully closed

However, sometimes you don't need a graceful handshake—you just want to close the stream immediately. The `close()` method transitions directly from any state to CLOSED without the half-close protocol:

```scala mdoc:silent:reset
import zio.blocks.mux._

val mux = Mux[Int, String, String](100)
val stream = mux.open(1) match {
  case s: MuxStream[Int, String, String] => s
  case _ => ???
}

// Application decides to close immediately (e.g., due to an error)
println(s"Before close: isClosed = ${stream.isClosed}")

stream.close()
println(s"After close: isClosed = ${stream.isClosed}")

// The close was synchronous and immediate
// Capacity is released, no handshake needed
```

The code above:
- `stream.close()` — transitions the stream from OPEN (or any state) immediately to CLOSED
- The `close()` method also enqueues a terminal error (`StreamClosed`) that `receive()` returns after all buffered messages are drained, allowing applications to detect closure
- This bypasses the graceful half-close handshake and releases capacity immediately
- Use `close()` when you need abrupt termination (error conditions, cleanup)
- Use `halfClose()` + `signalRemoteClose()` for orderly shutdown where both sides need to agree

:::tip[When to Use Each Closure Method]
- **Graceful (halfClose + signalRemoteClose):** Normal shutdown where both sides need to drain pending messages and agree to close
- **Immediate (close):** When the stream should terminate right now (error recovery, cleanup, timeout)
- **External (mux.cancel(id, reason)):** When you need to close a stream from outside its context (thread-safe operation)
:::

### External Stream Cancellation: `mux.cancel(id, reason)`

While `halfClose()` and `close()` are stream-level operations that modify the stream's state directly, **`mux.cancel(id, reason)` is a thread-safe mux-level operation** that cancels a stream from any thread without needing to access the stream object itself. This is useful for cleanup handlers, timeouts, and other scenarios where you can't guarantee single-threaded access.

Here's how to use `mux.cancel()` to externally cancel a stream:

```scala mdoc:silent:reset
import zio.blocks.mux._

val mux = Mux[Int, String, String](100)
val stream = mux.open(1) match { case s: MuxStream[Int, String, String] => s; case _ => ??? }

// From any thread, you can cancel a stream by ID
// This is thread-safe and doesn't require access to the stream object
mux.cancel(1, MuxError.Cancelled(1, "Cleanup: application shutting down"))
println("✓ Stream 1 cancelled successfully")

// After cancellation, the stream is closed
println(s"Stream isClosed: ${stream.isClosed}")
```

The code above:
- `mux.cancel(streamId, reason)` — cancels the stream with the given ID and enqueues a terminal error on its inbound queue
- This operation is **thread-safe** and you can call it from any thread
- Unlike `close()`, which requires direct access to the stream, `cancel()` works with just the stream ID
- The `reason` parameter becomes the terminal error that `receive()` returns

:::note[Important: `offerInbound()` After `signalRemoteClose()`]
When you call `signalRemoteClose()`, the stream transitions to `HALF_CLOSED_REMOTE` state. In this state, subsequent `offerInbound()` calls fail with `StreamClosed` error because the remote side signals it will not send any more messages. This prevents the protocol layer from enqueuing messages after the peer declares it's done sending.
:::

:::caution[Closing Is Coordinated]
Calling `halfClose()` when the stream is already in HALF_CLOSED_LOCAL or beyond is idempotent—the second call has no effect. Similarly, calling `signalRemoteClose()` when the stream is already in HALF_CLOSED_REMOTE or CLOSED has no effect. Calling `close()` at any time immediately transitions to CLOSED, bypassing any handshake.
:::

## 4. Working with Multiple Streams

The real power of mux comes from managing multiple independent streams simultaneously. Each stream is isolated: messages in stream 1 never appear in stream 2, and capacity is tracked separately.

Let's open three streams and exchange messages on each one:

```scala mdoc:silent:reset
import zio.blocks.mux._

val mux = Mux[Int, String, String](100)

// Open three streams
val stream1 = mux.open(1) match { case s: MuxStream[Int, String, String] => s; case _ => ??? }
val stream2 = mux.open(2) match { case s: MuxStream[Int, String, String] => s; case _ => ??? }
val stream3 = mux.open(3) match { case s: MuxStream[Int, String, String] => s; case _ => ??? }

// Send different messages on each stream
stream1.send("Message for stream 1") match { case () => (); case e: MuxError => println(s"Stream 1 send error: $e") }
stream2.send("Message for stream 2") match { case () => (); case e: MuxError => println(s"Stream 2 send error: $e") }
stream3.send("Message for stream 3") match { case () => (); case e: MuxError => println(s"Stream 3 send error: $e") }

// Extract messages — they stay on their own stream
val out1 = stream1.takeOutbound() match { case Some(msg) => msg; case None => "(empty)"; case e: MuxError => s"error: $e" }
val out2 = stream2.takeOutbound() match { case Some(msg) => msg; case None => "(empty)"; case e: MuxError => s"error: $e" }
val out3 = stream3.takeOutbound() match { case Some(msg) => msg; case None => "(empty)"; case e: MuxError => s"error: $e" }
println(s"Stream 1 outbound: $out1")
println(s"Stream 2 outbound: $out2")
println(s"Stream 3 outbound: $out3")

// Deliver responses to different streams
stream1.offerInbound("Response for stream 1") match { case () => (); case e: MuxError => println(s"Stream 1 offer error: $e") }
stream2.offerInbound("Response for stream 2") match { case () => (); case e: MuxError => println(s"Stream 2 offer error: $e") }
stream3.offerInbound("Response for stream 3") match { case () => (); case e: MuxError => println(s"Stream 3 offer error: $e") }

// Receive on each stream — no crosstalk
val in1 = stream1.receive() match { case Some(msg) => msg; case None => "(empty)"; case e: MuxError => s"error: $e" }
val in2 = stream2.receive() match { case Some(msg) => msg; case None => "(empty)"; case e: MuxError => s"error: $e" }
val in3 = stream3.receive() match { case Some(msg) => msg; case None => "(empty)"; case e: MuxError => s"error: $e" }
println(s"Stream 1 inbound: $in1")
println(s"Stream 2 inbound: $in2")
println(s"Stream 3 inbound: $in3")
```

The code above:
- We open three streams with IDs 1, 2, and 3
- Each stream has its own outbound and inbound queues
- Sending on stream 1 doesn't affect stream 2 or stream 3
- Messages stay on their originating stream — there is no crosstalk

:::tip[Streams Are Independent]
Each stream is a completely independent message channel. If stream 1 fills up its queue, stream 2 is unaffected. If you close stream 1, streams 2 and 3 continue operating normally. This isolation is the core benefit of multiplexing.
:::

## 5. Managing Capacity

The mux enforces two separate capacity limits: **mux-level** (controlling how many streams can exist concurrently) and **per-stream** (controlling how many messages each stream's queues can hold).

- **Mux-level capacity** (set at creation): The total number of concurrent streams. When exceeded, `open()` fails with `CapacityExceeded`.
- **Per-stream capacity** (fixed at 256 messages per direction): Each stream's outbound and inbound queues. When exceeded, `send()` and `offerInbound()` fail with `QueueFull`.

:::note[Null Message Rejection]
Both `send()` and `offerInbound()` reject `null` messages with `MuxError.ProtocolError("null message")`. This ensures the protocol layer never enqueues null values, which helps catch bugs early.
:::

Let's see what happens when we hit these limits:

```scala mdoc:silent:reset
import zio.blocks.mux._

// Create a tiny mux with a small capacity
val mux = Mux[Int, String, String](10)
val stream = mux.open(1) match {
  case s: MuxStream[Int, String, String] => s
  case _ => ???
}

// Try to send many messages until the queue fills (per-stream capacity is 256 per direction)
for (i <- 1 to 300) {
  val result = stream.send(s"Message $i")
  result match {
    case () => if (i <= 5 || i > 255) println(s"✓ Sent message $i")
    case error: MuxError => println(s"✗ Message $i failed: $error")
  }
}
```

The code above:
- We create a mux with total capacity 10 (very small)
- Each `send()` either succeeds (returns `Unit`) or fails with a `MuxError`
- When the per-stream queue is full, further sends return `MuxError.QueueFull`
- Each stream has its own per-stream message queues with a fixed capacity of 256 messages per direction

The standard recovery pattern is to drain messages from the outbound queue (by calling `takeOutbound()` repeatedly) until space is available:

```scala mdoc:silent:reset
import zio.blocks.mux._

val mux = Mux[Int, String, String](10)
val stream = mux.open(1) match { case s: MuxStream[Int, String, String] => s; case _ => ??? }

// Fill the queue
for (i <- 1 to 5) { stream.send(s"Message $i") }

// Drain one message from the outbound queue
println(s"Drained: ${stream.takeOutbound()}")

// Now we have space to send again
val result = stream.send("Message 6")
println(s"Sent after draining: $result is Unit")
```

The code above:
- We fill the queue with 5 messages
- We call `takeOutbound()` once to simulate the protocol extracting and sending a message
- This frees space in the queue, and the next `send()` succeeds

:::caution[Handling QueueFull in Real Code]
In a real application, `QueueFull` means the protocol is not keeping up with the application. You should either:
- Wait for the protocol to drain messages (using a poll loop or a callback mechanism)
- Close the stream if you decide recovery is impossible
- Implement a backpressure mechanism that slows the application
Never ignore `QueueFull` — it indicates a broken contract between layers.
:::

## 6. Thread Safety

The mux implementation is **thread-safe** for certain operations and **single-threaded** for others. Understanding this boundary is essential.

**Mux-level thread-safe operations** (call from any thread on the mux object):
- `mux.open(id)` — open a new stream
- `mux.get(id)` — retrieve a stream by ID
- `mux.cancel(id, reason)` — externally cancel a stream (thread-safe alternative to `close()`)
- `mux.closeAll(reason)` — close all streams

**Stream-level thread-safe operations** (call from multiple threads on a stream):
- `stream.send(msg)` — queue a message to send (uses lock-free ring buffer on JVM)
- `stream.offerInbound(msg)` — deliver a received message (uses lock-free ring buffer on JVM)

**Stream-level single-threaded operations** (call from the same dedicated thread only):
- `stream.receive()` — dequeue an inbound message
- `stream.takeOutbound()` — dequeue an outbound message
- `stream.halfClose()` — signal local side is done sending
- `stream.signalRemoteClose()` — signal remote side is done sending
- `stream.close()` — immediately close the stream

**Why?** The outbound and inbound queues are lock-free (on JVM) or single-threaded (on JS), which requires that the consumer threads remain stable. State transitions (`halfClose()`, `signalRemoteClose()`, `close()`) are not locked and should be coordinated from a single thread or with external synchronization.

Here's a correct usage pattern:

```scala mdoc:silent:reset
import zio.blocks.mux._

val mux = Mux[Int, String, String](100)
val stream = mux.open(1) match { case s: MuxStream[Int, String, String] => s; case _ => ??? }

// Thread A: application code (single thread)
// Always call send() from the same thread
stream.send("Message 1") match { case () => println("✓ Sent 1"); case e: MuxError => println(s"✗ Error: $e") }
stream.send("Message 2") match { case () => println("✓ Sent 2"); case e: MuxError => println(s"✗ Error: $e") }

// Thread B: protocol code (single thread)
// Always call takeOutbound() from the same thread
stream.takeOutbound() match { case Some(msg) => println(s"Extracted: $msg"); case None => println("(empty)"); case e: MuxError => println(s"✗ Error: $e") }
stream.takeOutbound() match { case Some(msg) => println(s"Extracted: $msg"); case None => println("(empty)"); case e: MuxError => println(s"✗ Error: $e") }

// Thread C, D, E, ... : multiple threads can safely deliver inbound messages
// This is thread-safe
for (_ <- 1 to 3) {
  stream.offerInbound("Response from peer") match { case () => (); case e: MuxError => println(s"✗ Offer error: $e") }
}

// Thread A: back on the application thread
// Always receive() on the same thread
stream.receive() match { case Some(msg) => println(s"Received: $msg"); case None => println("(empty)"); case e: MuxError => println(s"✗ Error: $e") }
stream.receive() match { case Some(msg) => println(s"Received: $msg"); case None => println("(empty)"); case e: MuxError => println(s"✗ Error: $e") }
stream.receive() match { case Some(msg) => println(s"Received: $msg"); case None => println("(empty)"); case e: MuxError => println(s"✗ Error: $e") }
```

The code above:
- Call `send()` and `receive()` each from a single dedicated thread, but they **can be different threads**
- Call `takeOutbound()` from a single dedicated thread (the protocol/reading thread)
- Multiple threads can safely call `offerInbound()` to deliver inbound messages
- Violating the single-threaded requirement for `receive()` and `takeOutbound()` corrupts the ring buffer

:::tip[Comparing Closure Methods]
There are three ways to close a stream, each with different semantics and thread safety properties:

| Method | Thread-Safe | Semantics | When to Use |
|--------|:---:|-----------|------------|
| **halfClose() + signalRemoteClose()** | ❌ Single-threaded | Graceful shutdown: both sides agree to close, pending messages drained | Normal orderly shutdown |
| **close()** | ❌ Single-threaded | Immediate closure: transitions to CLOSED right now, capacity released | Error recovery, cleanup, timeouts |
| **mux.cancel(id, reason)** | ✅ Thread-safe | External cancellation: close from any thread with an error reason | Cleanup from outside the stream, concurrent scenarios |

For example, use `mux.cancel(streamId, error)` when you're shutting down the entire application and need to close streams from a cleanup handler (which might run on a different thread).
:::

:::danger[Thread Safety Violation]
Do **not** call `receive()` or `takeOutbound()` from multiple threads on the same stream. These methods operate on lock-free ring buffers (JVM) or mutable arrays (JS) that are not thread-safe for concurrent access. Violating this will cause data corruption or panics.
:::

## 7. Putting It Together

Now let's write a complete example that demonstrates all the concepts: creating a mux, opening streams, exchanging messages, handling errors, managing capacity, and closing gracefully:

```scala mdoc:compile-only
import zio.blocks.mux._

object MuxExample {
  def main(args: Array[String]): Unit = {
    // Create a mux: stream IDs are Int, messages are String in both directions
    val mux = Mux[Int, String, String](100)

    println("=== Opening Streams ===")
    // Open three streams, handling potential capacity errors
    val streams = (1 to 3).map { id =>
      mux.open(id) match {
        case stream: MuxStream[Int, String, String] =>
          println(s"Opened stream $id")
          stream
        case error: MuxError =>
          println(s"Failed to open stream $id: $error")
          throw new RuntimeException(s"Cannot proceed without stream $id")
      }
    }.toList

    println("\n=== Application Sends Messages ===")
    // Application sends messages on each stream
    streams.zipWithIndex.foreach { case (stream, idx) =>
      val msg = s"Hello from stream ${idx + 1}"
      stream.send(msg) match {
        case () => println(s"Stream ${idx + 1} sent: $msg")
        case error: MuxError => println(s"Stream ${idx + 1} send failed: $error")
      }
    }

    println("\n=== Protocol Extracts Outbound Messages ===")
    // Protocol extracts messages to send over the network
    streams.zipWithIndex.foreach { case (stream, idx) =>
      stream.takeOutbound() match {
        case Some(msg) => println(s"Protocol will send on stream ${idx + 1}: $msg")
        case None => println(s"Stream ${idx + 1} has no outbound messages")
        case error: MuxError => println(s"Stream ${idx + 1} takeOutbound failed: $error")
      }
    }

    println("\n=== Protocol Delivers Inbound Messages ===")
    // Protocol delivers responses received from the peer
    streams.zipWithIndex.foreach { case (stream, idx) =>
      val response = s"Response from peer on stream ${idx + 1}"
      stream.offerInbound(response) match {
        case () => println(s"Protocol delivered on stream ${idx + 1}: $response")
        case error: MuxError => println(s"Stream ${idx + 1} offerInbound failed: $error")
      }
    }

    println("\n=== Application Receives Messages ===")
    // Application receives the responses
    streams.zipWithIndex.foreach { case (stream, idx) =>
      stream.receive() match {
        case Some(msg) => println(s"Stream ${idx + 1} received: $msg")
        case None => println(s"Stream ${idx + 1} has no inbound messages")
        case error: MuxError => println(s"Stream ${idx + 1} receive failed: $error")
      }
    }

    println("\n=== Graceful Shutdown ===")
    // Streams close gracefully
    val stream1 = streams(0)
    stream1.halfClose()
    println("Stream 1: halfClose() called")

    stream1.signalRemoteClose()
    println(s"Stream 1: signalRemoteClose() called, isClosed = ${stream1.isClosed}")

    // Verify the stream is closed
    assert(stream1.isClosed, "Stream 1 should be closed")

    println("\n=== Verify Independence ===")
    // Streams 2 and 3 are unaffected by stream 1 closing
    println(s"Stream 2 is open: ${!streams(1).isClosed}")
    println(s"Stream 3 is open: ${!streams(2).isClosed}")

    println("\nExample complete!")
  }
}
```

This example demonstrates:
- Creating a mux with a specific capacity
- Opening multiple streams and handling errors
- Application code sending messages and receiving responses
- Protocol code extracting and delivering messages
- Graceful stream closure using halfClose and signalRemoteClose
- Verifying that streams remain independent

## What You've Learned

In this tutorial, you learned:

- What a `Mux` is: a registry that manages multiple independent, multiplexed message streams
- How to create a mux and open streams within it, handling capacity errors
- The two-role pattern: application code uses `send()` and `receive()`, protocol code uses `takeOutbound()` and `offerInbound()`
- How streams transition through a well-defined lifecycle (OPEN → HALF_CLOSED → CLOSED)
- How to perform graceful shutdown (halfClose/signalRemoteClose) vs immediate closure (close)
- Why multiple streams are completely independent with no crosstalk
- How capacity limits work and the standard recovery pattern (drain to free space)
- The thread-safety contract: only `send()`, `offerInbound()`, and `cancel()` are multi-threaded safe; state transitions and message dequeuing are single-threaded
- How to build a complete request-response system using mux

You now have a solid foundation in how mux simplifies multiplexed communication. The next step is to see how to build production-grade protocols using mux as the foundation.

## Running the Examples

All examples in this tutorial have corresponding runnable Scala files in the `mux-examples` module. Run them in order to progressively build your understanding in practice.

### Creating a Mux

This example creates a mux and opens your first stream, demonstrating the basic API for stream initialization and handling both success and failure cases when capacity is exceeded.

<details>
  <summary>mux-examples/src/main/scala/mux/Example1CreatingAMux.scala</summary>

```scala mdoc:embed:mux-examples/src/main/scala/mux/Example1CreatingAMux.scala:show-line-numbers
```

</details>

Observe the output: the stream is successfully created and its initial state (not closed, not half-closed) is displayed.

```bash
sbt "mux-examples/runMain mux.example1CreatingAMux"
```

### Understanding Streams and Message Queues

This example demonstrates the two-perspective communication model where application code uses `send()`/`receive()` while protocol code uses `takeOutbound()`/`offerInbound()`, showing how messages flow through the queue system.

<details>
  <summary>mux-examples/src/main/scala/mux/Example2UnderstandingStreamsAndMessageQueues.scala</summary>

```scala mdoc:embed:mux-examples/src/main/scala/mux/Example2UnderstandingStreamsAndMessageQueues.scala:show-line-numbers
```

</details>

Observe the output: messages sent by the application are extracted by the protocol, and responses delivered by the protocol are received by the application.

```bash
sbt "mux-examples/runMain mux.example2UnderstandingStreamsAndMessageQueues"
```

### The Stream Lifecycle

This example shows all three ways to close a stream: graceful two-phase closure (halfClose + signalRemoteClose), immediate closure (close), and external thread-safe cancellation (mux.cancel).

<details>
  <summary>mux-examples/src/main/scala/mux/Example3StreamLifecycle.scala</summary>

```scala mdoc:embed:mux-examples/src/main/scala/mux/Example3StreamLifecycle.scala:show-line-numbers
```

</details>

Observe the output: state transitions from OPEN → HALF_CLOSED → CLOSED in graceful shutdown, instant transition in immediate closure, and the final closed state after external cancellation.

```bash
sbt "mux-examples/runMain mux.example3StreamLifecycle"
```

### Working with Multiple Streams

This example opens three independent streams and exchanges messages on each one, demonstrating that streams are isolated with no message crosstalk and each has its own inbound and outbound queues.

<details>
  <summary>mux-examples/src/main/scala/mux/Example4WorkingWithMultipleStreams.scala</summary>

```scala mdoc:embed:mux-examples/src/main/scala/mux/Example4WorkingWithMultipleStreams.scala:show-line-numbers
```

</details>

Observe the output: messages sent on stream 1 appear only in stream 1's outbound queue, never in streams 2 or 3, proving complete independence.

```bash
sbt "mux-examples/runMain mux.example4WorkingWithMultipleStreams"
```

### Managing Capacity

This example demonstrates both mux-level capacity (controlling concurrent streams) and per-stream capacity (controlling message queue depth), showing how to handle QueueFull errors and the recovery pattern of draining to free space.

<details>
  <summary>mux-examples/src/main/scala/mux/Example5ManagingCapacity.scala</summary>

```scala mdoc:embed:mux-examples/src/main/scala/mux/Example5ManagingCapacity.scala:show-line-numbers
```

</details>

Observe the output: the mux rejects stream 4 and 5 with CapacityExceeded, messages 257+ fail with QueueFull, and after draining one message, sending resumes successfully.

```bash
sbt "mux-examples/runMain mux.example5ManagingCapacity"
```

### Thread Safety

This example documents which operations are thread-safe (mux-level and send/offerInbound) versus single-threaded (state transitions and message dequeuing), with a correct usage pattern showing how threads interact safely.

<details>
  <summary>mux-examples/src/main/scala/mux/Example6ThreadSafety.scala</summary>

```scala mdoc:embed:mux-examples/src/main/scala/mux/Example6ThreadSafety.scala
```

</details>

Observe the output: mux.open and mux.cancel are declared thread-safe, while receive() and takeOutbound() must use single threads, and the pattern shows multiple threads safely offering inbound messages.

```bash
sbt "mux-examples/runMain mux.example6ThreadSafety"
```

### Putting It All Together

This comprehensive example demonstrates a complete request-response system that ties together all concepts: creating a mux, opening multiple streams, exchanging messages, handling errors, managing lifecycle transitions, and verifying independence.

<details>
  <summary>mux-examples/src/main/scala/mux/CompleteExample.scala</summary>

```scala mdoc:embed:mux-examples/src/main/scala/mux/CompleteExample.scala:show-line-numbers
```

</details>

Observe the output: three streams are opened, messages flow through send/receive and takeOutbound/offerInbound, graceful shutdown completes with halfClose/signalRemoteClose, and other streams remain unaffected.

```bash
sbt "mux-examples/runMain mux.completeExample"
```

## Where to Go Next

- **Want to dive deeper into the API?** Read the reference page for [`Mux`](../reference/mux.mdx) for complete method signatures and advanced patterns.
- **Ready to explore related concepts?** Check out the [Ring Buffer](../reference/ringbuffer/index.mdx) reference for understanding lock-free data structures used internally.
- **Interested in other concurrency and stream management?** Explore other libraries in ZIO Blocks for managing complex async workflows.
