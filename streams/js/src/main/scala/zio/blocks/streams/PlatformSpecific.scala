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

import zio.blocks.streams.io.Reader

/**
 * JavaScript-specific platform implementation.
 *
 * Scala.js is single-threaded; true concurrency is not supported. Virtual
 * threads are not available. [[createBufferedReader]] uses
 * [[internal.SyncBufferedReader]] to synchronously prefetch elements.
 *
 * [[createMergeReader]] and [[createMapParReader]] degrade to sequential
 * implementations (flatMap and map, respectively).
 */
trait PlatformSpecific extends Platform {
  override val supportsConcurrency: Boolean = false

  override def startVirtualThread(name: String, task: Runnable): Thread =
    throw new UnsupportedOperationException(
      "Virtual threads are not supported on Scala.js. Use supportsConcurrency to guard calls."
    )

  override def createBufferedReaderFromReader[A](upstream: Reader[A], bufferSize: Int): Reader[A] = upstream match {
    case reader: Reader.SyncReader[A @unchecked]  => new internal.SyncBufferedReader(reader, bufferSize)
    case reader: Reader.AsyncReader[A @unchecked] => internal.AsyncStatefulReader.buffered(reader, bufferSize)
  }

  override def createMergeReaderFromReader[A](
    outerReader: Reader[?],
    maxOpen: Int,
    bufferSize: Int,
    elemType: JvmType
  ): Reader[A] = internal.AsyncConcurrentReaders.merge[A](outerReader, maxOpen, bufferSize, elemType)

  override private[streams] def createMergeReader[A](
    outerReader: Reader.SyncReader[?],
    maxOpen: Int,
    bufferSize: Int,
    elemType: JvmType
  ): Reader.SyncReader[A] = {
    val identity     = ((stream: AnyRef) => stream).asInstanceOf[AnyRef]
    val compileInner = (stream: AnyRef) =>
      stream.asInstanceOf[Stream[Any, A]].compile(0, bufferSize) match {
        case reader: Reader.SyncReader[A @unchecked] => reader.asInstanceOf[Reader.SyncReader[Any]]
        case _: Reader.AsyncReader[_]                =>
          throw new UnsupportedOperationException(
            "Native asynchronous inner streams are not supported by synchronous merge"
          )
      }
    new Reader.FlatMappedRef(outerReader, identity, compileInner, elemType).asInstanceOf[Reader.SyncReader[A]]
  }

  override def createMapParReaderFromReader[A, B](
    upstream: Reader[A],
    n: Int,
    f: A => B,
    bufferSize: Int,
    inType: JvmType,
    outType: JvmType
  ): Reader[B] = upstream match {
    case reader: Reader.AsyncReader[A @unchecked] =>
      internal.AsyncConcurrentReaders.mapPar(reader, n, (a: A) => zio.blocks.async.Async.succeed(f(a)), outType)
    case reader: Reader.SyncReader[A @unchecked] =>
      val adapted = internal.SyncInterpreter.adaptMap(inType, outType, f)
      val mapped  = reader match {
        case thin: Reader.WrappedReader if (inType ne JvmType.Boolean) && (inType ne JvmType.Byte) =>
          val interpreter = thin.toInterpreter
          interpreter.addAdaptedMap(inType, outType, adapted)
          interpreter.seal()
          interpreter
        case _ =>
          (internal.SyncInterpreter.laneOf(inType): @scala.annotation.switch) match {
            case 0 =>
              if ((inType eq JvmType.Int) && (outType eq JvmType.Int))
                new Reader.MappedIntInt(reader.asInstanceOf[Reader.SyncReader[Int]], adapted)
              else new Reader.MappedInt(reader, adapted, outType, inType)
            case 1 => new Reader.MappedLong(reader, adapted, outType)
            case 2 => new Reader.MappedFloat(reader, adapted, outType)
            case 3 => new Reader.MappedDouble(reader, adapted, outType)
            case _ => new Reader.MappedRef(reader, adapted, outType)
          }
      }
      mapped.asInstanceOf[Reader[B]]
  }
}
