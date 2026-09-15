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

/**
 * JVM platform sliver of the [[Stream]] companion: push-based producer
 * constructors. Mixed into `object Stream` so the public entry point is
 * `Stream.fromProducer*` on the JVM.
 *
 * Implementation delegates to [[ProducerStreams]], which is retained as a
 * deprecated alias for source/binary compatibility within this PR.
 */
private[streams] trait StreamCompanionPlatformSpecific { self: Stream.type =>

  /**
   * Creates a stream from a push-based producer. `register` is called once
   * during stream compilation and receives a [[ProducerSink]]; it must return a
   * cancel callback that is invoked when the consumer closes early.
   *
   * Thrown exceptions from `register` are delivered as defects (rethrown on the
   * consumer thread), not as typed `Left[E]`. Use `sink.fail(E)` for the typed
   * `Left[E]` path.
   */
  def fromProducer[E, A](
    register: ProducerSink[A, E] => () => Unit,
    knownLength: Option[Long] = None,
    bufferSize: Int = 256
  ): Stream[E, A] =
    ProducerStreams.fromProducer(register, knownLength, bufferSize)

  /**
   * Managed variant of [[fromProducer]] that runs `produce` on a virtual
   * thread, calling `sink.end()` on normal return and surfacing thrown
   * exceptions as defects (rethrown). Interrupts from consumer cancellation are
   * not surfaced as defects. Call `sink.fail(E)` inside `produce` for typed
   * `Left[E]` failures.
   */
  def fromProducerSimple[E, A](
    produce: ProducerSink[A, E] => Unit,
    knownLength: Option[Long] = None,
    bufferSize: Int = 256
  ): Stream[E, A] =
    ProducerStreams.fromProducerSimple(produce, knownLength, bufferSize)

  /**
   * Byte-optimized variant of [[fromProducer]]; each `Chunk[Byte]` is
   * transferred as a single reference and bytes are pulled without boxing.
   *
   * Thrown exceptions from `register` are defects (rethrown); `sink.fail(E)` is
   * the typed `Left[E]` path.
   */
  def fromProducerBytes[E](
    register: ProducerSink[Byte, E] => () => Unit,
    knownLength: Option[Long] = None,
    bufferSize: Int = 256
  ): Stream[E, Byte] =
    ProducerStreams.fromProducerBytes(register, knownLength, bufferSize)

  /**
   * Managed byte variant of [[fromProducer]] with virtual-thread lifecycle.
   * Thrown exceptions from `produce` are defects (rethrown); `sink.fail(E)` is
   * the typed `Left[E]` path. Cancellation interrupts are not surfaced as
   * defects.
   */
  def fromProducerBytesSimple[E](
    produce: ProducerSink[Byte, E] => Unit,
    knownLength: Option[Long] = None,
    bufferSize: Int = 256
  ): Stream[E, Byte] =
    ProducerStreams.fromProducerBytesSimple(produce, knownLength, bufferSize)
}
