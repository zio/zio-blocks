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

package zio.blocks.telemetry.otel

import zio.blocks.telemetry._

/**
 * Reads and writes a `SpanContext` in the headers of a request or message, so
 * that a trace continues across a process boundary. The caller injects the
 * current span's context into an outgoing request; the receiver extracts it and
 * opens its spans as children of it.
 *
 * A propagator works on any carrier: a `Map`, a framework's request type, the
 * metadata of a queue message. You pass a getter that reads one header by name,
 * and a setter that returns the carrier with one header added. The propagator
 * calls them with the exact names in `fields`, so a getter over HTTP headers,
 * whose names are case-insensitive, should ignore case. The module provides
 * [[W3CTraceContextPropagator]] and the two formats of [[B3Propagator]].
 *
 * {{{
 * val received = List(
 *   "content-type" -> "application/json",
 *   "traceparent"  -> "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01"
 * )
 * val parent = W3CTraceContextPropagator.extract[List[(String, String)]](
 *   received,
 *   (headers, name) => headers.collectFirst { case (k, v) if k.equalsIgnoreCase(name) => v }
 * )
 * parent.map(_.spanId.toHex) // Some(00f067aa0ba902b7)
 *
 * val forwarded = parent.map { ctx =>
 *   B3Propagator.multi.inject[List[(String, String)]](ctx, Nil, (headers, name, value) => (name -> value) :: headers)
 * }
 * // Some(List((X-B3-Sampled,1), (X-B3-SpanId,00f067aa0ba902b7), (X-B3-TraceId,4bf92f3577b34da6a3ce929d0e0e4736)))
 * }}}
 *
 * @see
 *   [[https://zioblocks.com/reference/telemetry/otel/ OTLP Export]]
 */
trait Propagator {

  /**
   * Returns the context that `carrier` holds, read through `getter`, or `None`
   * when its headers are missing or not in this propagator's format. The
   * built-in propagators mark the context `isRemote`. Check
   * `SpanContext#isValid` before you use the result as a parent:
   * `B3Propagator.single` returns `Some` of an invalid context for a header
   * that holds only a sampling decision, a known bug.
   */
  def extract[C](carrier: C, getter: (C, String) => Option[String]): Option[SpanContext]

  /**
   * Returns `carrier` with this propagator's headers set from `spanContext`,
   * calling `setter` once per header. The built-in propagators return `carrier`
   * unchanged when `spanContext` is invalid.
   */
  def inject[C](spanContext: SpanContext, carrier: C, setter: (C, String, String) => C): C

  /**
   * Returns the names of the headers this propagator reads and writes, in the
   * case it passes them to the getter and setter. Give them to a CORS or header
   * allow-list.
   */
  def fields: Seq[String]
}
