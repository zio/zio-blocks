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

package zio.blocks.streams

import scala.annotation.unchecked.uncheckedVariance

import zio.blocks.chunk.Chunk

/**
 * A push-based sink that a producer uses to emit elements into a stream.
 *
 * '''SPSC contract:''' Only one thread may call [[emit]] at a time. The
 * implementation uses a single-producer single-consumer ring buffer. Concurrent
 * [[emit]] calls from multiple threads will corrupt the buffer.
 *
 * '''Contract:''' Producers MUST call either [[end]] or [[fail]] when done.
 * Failing to do so (e.g. due to an uncaught exception on the producer thread)
 * will cause the consumer to block indefinitely. Use
 * [[Stream.fromProducerSimple]] for automatic lifecycle management.
 *
 * [[end]] and [[fail]] are idempotent — the first call wins; subsequent calls
 * are no-ops.
 *
 * @note
 *   [[emit]] does not accept `null` values; passing `null` throws
 *   [[NullPointerException]].
 */
trait ProducerSink[-A, -E] {

  /**
   * Emit a single element. Returns `false` if the stream is closed/cancelled.
   *
   * @throws NullPointerException
   *   if `a` is `null`
   */
  def emit(a: A): Boolean

  /**
   * Emit a chunk of elements. Returns `false` if the stream is closed/cancelled
   * before all elements have been emitted.
   *
   * The default implementation iterates the chunk and calls [[emit(a:A)*]] for
   * each element, stopping on the first failure. Subclasses may override for
   * more efficient bulk transfer (e.g. putting the whole chunk into a ring
   * buffer as a single reference).
   */
  def emit(chunk: Chunk[A @uncheckedVariance]): Boolean = {
    var i = 0
    while (i < chunk.length) {
      if (!emit(chunk(i))) return false
      i += 1
    }
    true
  }

  /** Signal normal completion. Idempotent — second call is a no-op. */
  def end(): Unit

  /** Signal a typed error. Idempotent — second call is a no-op. */
  def fail(error: E): Unit
}
