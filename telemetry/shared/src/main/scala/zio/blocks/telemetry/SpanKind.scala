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
 * Represents the kind of span - distinguishes the type of span based on its
 * relationship in a trace.
 *
 * Follows OpenTelemetry span kind specification.
 */
sealed trait SpanKind

object SpanKind {

  /**
   * Internal span - represents work performed internally in the application.
   *
   * No remote parent or child, used for internal instrumentation.
   */
  case object Internal extends SpanKind

  /**
   * Server span - represents the server-side of a synchronous RPC
   * communication.
   *
   * The span begins when the server starts processing the RPC and ends when the
   * server sends the response.
   */
  case object Server extends SpanKind

  /**
   * Client span - represents the client-side of a synchronous RPC
   * communication.
   *
   * The span begins when the client sends the RPC request and ends when it
   * receives the response.
   */
  case object Client extends SpanKind

  /**
   * Producer span - represents the producer side of an asynchronous message
   * transmission.
   *
   * The span begins when the producer sends the message and ends when the
   * message is sent.
   */
  case object Producer extends SpanKind

  /**
   * Consumer span - represents the consumer side of an asynchronous message
   * transmission.
   *
   * The span begins when the consumer receives the message and ends when the
   * consumer finishes processing it.
   */
  case object Consumer extends SpanKind
}
