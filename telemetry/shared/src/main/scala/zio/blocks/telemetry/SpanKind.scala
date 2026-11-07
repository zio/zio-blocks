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

package zio.blocks.telemetry

/**
 * The role of a span in a trace, as the OpenTelemetry specification defines it:
 * internal work, one side of a synchronous call, or one side of an asynchronous
 * message. It lets a backend pair a `Client` span with the `Server` span it
 * caused in another service. Pass it to `trace.span` or `Tracer#span`; the
 * default is `Internal`.
 *
 * @see
 *   [[https://zioblocks.com/reference/telemetry/tracing/span-kind/ SpanKind]]
 */
sealed trait SpanKind

/** The five span kinds. */
object SpanKind {

  /**
   * Work inside the application, with no remote parent or child. The default
   * kind of `trace.span`, `Tracer#span` and [[SpanBuilder]].
   */
  case object Internal extends SpanKind

  /**
   * The server side of a synchronous call, from the moment the server starts
   * handling the request until it sends the response.
   */
  case object Server extends SpanKind

  /**
   * The client side of a synchronous call, from sending the request until the
   * response arrives.
   */
  case object Client extends SpanKind

  /**
   * The sending side of an asynchronous message; it often ends before the
   * matching `Consumer` span starts.
   */
  case object Producer extends SpanKind

  /**
   * The receiving side of an asynchronous message, from receiving the message
   * until it is processed.
   */
  case object Consumer extends SpanKind
}
