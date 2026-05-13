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
 * Scala.js platform sliver of the [[Stream]] companion: producer-backed streams
 * are JVM-only. All methods throw [[UnsupportedOperationException]].
 */
private[streams] trait StreamCompanionPlatformSpecific { self: Stream.type =>

  def fromProducer[E, A](
    register: ProducerSink[A, E] => () => Unit,
    knownLength: Option[Long] = None,
    bufferSize: Int = 256
  ): Stream[E, A] =
    throw new UnsupportedOperationException("Stream.fromProducer is JVM-only")

  def fromProducerSimple[E, A](
    produce: ProducerSink[A, E] => Unit,
    knownLength: Option[Long] = None,
    bufferSize: Int = 256
  ): Stream[E, A] =
    throw new UnsupportedOperationException("Stream.fromProducerSimple is JVM-only")

  def fromProducerBytes[E](
    register: ProducerSink[Byte, E] => () => Unit,
    knownLength: Option[Long] = None,
    bufferSize: Int = 256
  ): Stream[E, Byte] =
    throw new UnsupportedOperationException("Stream.fromProducerBytes is JVM-only")

  def fromProducerBytesSimple[E](
    produce: ProducerSink[Byte, E] => Unit,
    knownLength: Option[Long] = None,
    bufferSize: Int = 256
  ): Stream[E, Byte] =
    throw new UnsupportedOperationException("Stream.fromProducerBytesSimple is JVM-only")
}
