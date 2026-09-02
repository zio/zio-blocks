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
 * A propagator extracts and injects SpanContext from/into a carrier using
 * getter/setter functions. This abstraction enables context propagation across
 * process boundaries (e.g., HTTP headers, message queue metadata).
 */
trait Propagator {

  /**
   * Extracts a SpanContext from the carrier. Returns None if no valid context
   * is found or the input is malformed.
   */
  def extract[C](carrier: C, getter: (C, String) => Option[String]): Option[SpanContext]

  /**
   * Injects a SpanContext into the carrier, returning the modified carrier.
   */
  def inject[C](spanContext: SpanContext, carrier: C, setter: (C, String, String) => C): C

  /**
   * The header/field names this propagator reads and writes.
   */
  def fields: Seq[String]
}
