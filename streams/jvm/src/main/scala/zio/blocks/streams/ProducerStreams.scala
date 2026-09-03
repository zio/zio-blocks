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

import zio.blocks.chunk.Chunk
import zio.blocks.streams.internal.{ByteProducerReader, ProducerReader}

/**
 * JVM-only stream constructor that bridges a push-based producer into a
 * pull-based [[Stream]]. Uses the shared blocking queue infrastructure that the
 * other concurrent JVM stream readers use.
 *
 * @note
 *   Prefer [[Stream.fromProducer]] / [[Stream.fromProducerSimple]] (via
 *   [[StreamCompanionPlatformSpecific]]). This object is retained as an alias
 *   for source compatibility.
 */
object ProducerStreams {

  /**
   * Creates a stream from a push-based producer. The `register` callback
   * receives a [[ProducerSink]] and returns a cancel callback (`() => Unit`)
   * that is invoked when the consumer closes the stream.
   *
   * If `register` throws synchronously, the exception is delivered as a defect
   * (rethrown on the consumer thread), not as a typed `Left[E]`. Use
   * `sink.fail(E)` for the typed `Left[E]` path.
   *
   * @param register
   *   Called during stream compilation. Receives a sink; returns a cancel
   *   callback.
   * @param knownLength
   *   Optional metadata hint for the number of elements.
   * @param bufferSize
   *   Internal queue capacity (default 256). Must be positive.
   */
  def fromProducer[E, A](
    register: ProducerSink[A, E] => () => Unit,
    knownLength: Option[Long] = None,
    bufferSize: Int = 256
  ): Stream[E, A] = {
    require(bufferSize > 0, s"bufferSize must be positive, got: $bufferSize")
    val theKnownLength = knownLength
    new Stream.FromReader[E, A](
      () => {
        val reader = new ProducerReader[A](bufferSize)
        try {
          val cancel = register(reader.sink)
          reader.setCancelCallback(cancel)
        } catch {
          case e: Throwable =>
            reader.offerDefect(e)
        }
        reader
      },
      "ProducerStreams.fromProducer(...)"
    ) {
      override def knownLength: Option[Long] = theKnownLength
    }
  }

  /**
   * Convenience wrapper around [[fromProducer]] that manages the producer
   * lifecycle automatically: runs `produce` on a new virtual thread, calls
   * [[ProducerSink.end]] on normal return, and surfaces thrown exceptions as
   * defects (rethrown). Call `sink.fail(E)` inside `produce` for the typed
   * `Left[E]` path. Cancellation interrupts the producer thread; cancellation-
   * originated interrupts are not surfaced as defects.
   *
   * Use this instead of [[fromProducer]] when custom thread management is not
   * needed.
   */
  def fromProducerSimple[E, A](
    produce: ProducerSink[A, E] => Unit,
    knownLength: Option[Long] = None,
    bufferSize: Int = 256
  ): Stream[E, A] = {
    require(bufferSize > 0, s"bufferSize must be positive, got: $bufferSize")
    val theKnownLength = knownLength
    new Stream.FromReader[E, A](
      () => {
        val reader = new ProducerReader[A](bufferSize)
        val thread = Platform.startVirtualThread(
          "producer",
          new Runnable {
            def run(): Unit =
              try {
                produce(reader.sink)
                reader.sink.end()
              } catch {
                case ie: InterruptedException =>
                  Thread.currentThread().interrupt()
                  if (!reader.isCancelled) reader.offerDefect(ie)
                case e: Throwable =>
                  reader.offerDefect(e)
              }
          }
        )
        reader.setCancelCallback(() => thread.interrupt())
        reader
      },
      "ProducerStreams.fromProducerSimple(...)"
    ) {
      override def knownLength: Option[Long] = theKnownLength
    }
  }

  /**
   * Creates a byte stream from a push-based producer. Optimized for
   * [[Chunk[Byte]]] emission — each chunk is transferred as a single reference
   * through the internal ring buffer, and bytes are pulled without boxing via
   * [[zio.blocks.streams.io.Reader.readByte()]].
   *
   * Thrown exceptions from `register` are defects (rethrown); `sink.fail(E)` is
   * the typed `Left[E]` path.
   *
   * '''SPSC contract:''' The caller must ensure that only one thread calls
   * [[ProducerSink.emit]] at a time.
   */
  def fromProducerBytes[E](
    register: ProducerSink[Byte, E] => () => Unit,
    knownLength: Option[Long] = None,
    bufferSize: Int = 256
  ): Stream[E, Byte] = {
    require(bufferSize > 0, s"bufferSize must be positive, got: $bufferSize")
    val theKnownLength = knownLength
    new Stream.FromReader[E, Byte](
      () => {
        val reader = new ByteProducerReader(bufferSize)
        try {
          val cancel = register(reader.sink)
          reader.setCancelCallback(cancel)
        } catch {
          case e: Throwable =>
            reader.offerDefect(e)
        }
        reader
      },
      "ProducerStreams.fromProducerBytes(...)"
    ) {
      override def knownLength: Option[Long] = theKnownLength
    }
  }

  /**
   * Convenience wrapper around [[fromProducerBytes]] that manages the producer
   * lifecycle automatically: runs `produce` on a virtual thread, calls
   * [[ProducerSink.end]] on normal return, and surfaces thrown exceptions as
   * defects (rethrown). Call `sink.fail(E)` inside `produce` for the typed
   * `Left[E]` path. Cancellation interrupts are not surfaced as defects.
   */
  def fromProducerBytesSimple[E](
    produce: ProducerSink[Byte, E] => Unit,
    knownLength: Option[Long] = None,
    bufferSize: Int = 256
  ): Stream[E, Byte] = {
    require(bufferSize > 0, s"bufferSize must be positive, got: $bufferSize")
    val theKnownLength = knownLength
    new Stream.FromReader[E, Byte](
      () => {
        val reader = new ByteProducerReader(bufferSize)
        val thread = Platform.startVirtualThread(
          "producer",
          new Runnable {
            def run(): Unit =
              try {
                produce(reader.sink)
                reader.sink.end()
              } catch {
                case ie: InterruptedException =>
                  Thread.currentThread().interrupt()
                  if (!reader.isCancelled) reader.offerDefect(ie)
                case e: Throwable =>
                  reader.offerDefect(e)
              }
          }
        )
        reader.setCancelCallback(() => thread.interrupt())
        reader
      },
      "ProducerStreams.fromProducerBytesSimple(...)"
    ) {
      override def knownLength: Option[Long] = theKnownLength
    }
  }

  // -- Internal sentinels --

  private[streams] val EndMarker: AnyRef = new AnyRef {
    override def toString: String = "EndMarker"
  }

  private[streams] final class FailMarker(val error: Any) {
    override def toString: String = s"FailMarker($error)"
  }

  private[streams] final class DefectMarker(val cause: Throwable) {
    override def toString: String =
      s"DefectMarker(${cause.getClass.getSimpleName}: ${cause.getMessage})"
  }

  private[streams] final class ChunkEnvelope(val chunk: Chunk[_]) extends AnyRef {
    override def toString: String = s"ChunkEnvelope(${chunk.length} elements)"
  }
}
