# Mux Examples

This directory contains runnable Scala examples demonstrating the key concepts from the [Getting Started with Mux](../docs/guides/getting-started-with-mux.md) tutorial.

## Overview

The Mux library provides a thread-safe way to manage multiplexed bidirectional message streams. Each example focuses on a specific concept:

### Example Files

1. **Example1CreatingAMux.scala** — Creating a Mux
   - How to instantiate a Mux with capacity limits
   - Opening streams and handling capacity errors
   - Checking stream state (isClosed, isHalfClosed)

2. **Example2UnderstandingStreamsAndMessageQueues.scala** — Streams and Message Queues
   - Bidirectional messaging: send/receive vs takeOutbound/offerInbound
   - Two perspectives: application code vs protocol code
   - Messages stay in their FIFO queues per stream

3. **Example3StreamLifecycle.scala** — The Stream Lifecycle
   - Stream lifecycle states: OPEN → HALF_CLOSED → CLOSED
   - Graceful shutdown (halfClose + signalRemoteClose)
   - Immediate closure (close)
   - External thread-safe cancellation (mux.cancel)

4. **Example4WorkingWithMultipleStreams.scala** — Working with Multiple Streams
   - Opening and managing multiple independent streams
   - Demonstrating complete stream isolation (no crosstalk)
   - Showing that closing one stream doesn't affect others

5. **Example5ManagingCapacity.scala** — Managing Capacity
   - Mux-level capacity (concurrent stream limits)
   - Per-stream capacity (message queue limits, fixed at 256)
   - Recovery pattern: draining outbound queue to free space

6. **Example6ThreadSafety.scala** — Thread Safety
   - Thread-safe vs single-threaded operations
   - Correct usage patterns per operation type
   - Using mux.cancel for thread-safe external cancellation

7. **CompleteExample.scala** — Putting It All Together
   - Comprehensive end-to-end example combining all concepts
   - Demonstrates a real-world request-response pattern
   - Shows graceful shutdown and stream independence verification

## Running the Examples

Each example can be run independently using sbt:

```bash
# Example 1: Creating a Mux
sbt "mux-examples/runMain mux.example1CreatingAMux"

# Example 2: Understanding Streams and Message Queues
sbt "mux-examples/runMain mux.example2UnderstandingStreamsAndMessageQueues"

# Example 3: The Stream Lifecycle
sbt "mux-examples/runMain mux.example3StreamLifecycle"

# Example 4: Working with Multiple Streams
sbt "mux-examples/runMain mux.example4WorkingWithMultipleStreams"

# Example 5: Managing Capacity
sbt "mux-examples/runMain mux.example5ManagingCapacity"

# Example 6: Thread Safety
sbt "mux-examples/runMain mux.example6ThreadSafety"

# Complete Example
sbt "mux-examples/runMain mux.completeExample"
```

## Key Concepts

### Two Roles: Application vs Protocol

- **Application code** calls `send()` to send messages and `receive()` to get responses
- **Protocol code** calls `takeOutbound()` to extract messages for sending and `offerInbound()` to deliver received messages

### Stream Lifecycle

Streams transition through states to ensure orderly shutdown:
- `OPEN` → Application can send/receive normally
- `HALF_CLOSED` → One side declared done sending, but the other can still send
- `CLOSED` → Both sides done, stream is fully closed

### Capacity Limits

- **Mux-level**: Total number of concurrent streams (set at creation)
- **Per-stream**: Maximum messages per direction (fixed at 256)

### Thread Safety

- `send()` and `offerInbound()` are thread-safe (multiple producers)
- `receive()` and `takeOutbound()` must be called from a single dedicated thread
- `mux.cancel(id, reason)` is the thread-safe way to externally cancel a stream

## Learning Path

Follow the examples in order:
1. Start with **Example1CreatingAMux** to understand basic setup
2. Move to **Example2UnderstandingStreamsAndMessageQueues** to learn message exchange
3. Explore **Example3StreamLifecycle** to understand state transitions
4. Study **Example4WorkingWithMultipleStreams** for stream independence
5. Review **Example5ManagingCapacity** for handling capacity limits
6. Read **Example6ThreadSafety** to understand threading constraints
7. Run **CompleteExample** for a full integrated scenario

For detailed explanations, refer to the [Getting Started with Mux](../docs/guides/getting-started-with-mux.md) tutorial.
