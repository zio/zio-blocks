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

import zio.blocks.streams.JvmType
import zio.blocks.streams.io.Reader

import java.lang.invoke.MethodHandles
import scala.util.control.NonFatal

/**
 * The platform-specific half of `Platform`. The `Platform` object mixes it in,
 * so callers use the same `Platform` members on every platform.
 *
 * On the JVM, `supportsConcurrency` is `true` and `startVirtualThread` starts a
 * virtual thread (JDK 21 and later, found by reflection) or a daemon platform
 * thread. The buffer, merge and `mapPar` readers for a synchronous upstream run
 * their work on such threads; the merge and `mapPar` readers have a specialized
 * class for the `Int` lane (which also carries `Boolean`, `Byte`, `Char` and
 * `Short`) and the `Long`, `Float` and `Double` lanes, and the buffer reader
 * keeps primitive lanes in unboxed queues.
 *
 * On Scala.js, `supportsConcurrency` is `false` and `startVirtualThread` throws
 * `UnsupportedOperationException`. The buffer, merge and `mapPar` readers for a
 * synchronous upstream run on the reading thread: the buffer refills when it
 * runs empty, the merge reads its inner streams one after another, and `mapPar`
 * maps one element at a time.
 *
 * On both platforms, `createMergeReaderFromReader`, and the buffer and `mapPar`
 * readers for an asynchronous upstream, return the same asynchronous readers.
 */
trait PlatformSpecific extends Platform {
  override val supportsConcurrency: Boolean = true

  override def startVirtualThread(name: String, task: Runnable): Thread =
    VirtualThreadSupport.startVirtual match {
      case Some(startVirtual) =>
        try startVirtual(name, task)
        catch {
          case err if NonFatal(err) => fallbackThread(name, task)
        }
      case None => fallbackThread(name, task)
    }

  override def createBufferedReaderFromReader[A](upstream: Reader[A], bufferSize: Int): Reader[A] = upstream match {
    case reader: Reader.SyncReader[A @unchecked]  => new internal.ConcurrentBufferedReader(reader, bufferSize)
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
  ): Reader.SyncReader[A] = elemType match {
    case laneType @ (JvmType.Boolean | JvmType.Byte | JvmType.Char | JvmType.Short | JvmType.Int) =>
      new internal.IntConcurrentMergeReader[A](outerReader, maxOpen, bufferSize, laneType)
    case JvmType.Long =>
      new internal.LongConcurrentMergeReader(outerReader, maxOpen, bufferSize).asInstanceOf[Reader.SyncReader[A]]
    case JvmType.Double =>
      new internal.DoubleConcurrentMergeReader(outerReader, maxOpen, bufferSize).asInstanceOf[Reader.SyncReader[A]]
    case JvmType.Float =>
      new internal.FloatConcurrentMergeReader(outerReader, maxOpen, bufferSize).asInstanceOf[Reader.SyncReader[A]]
    case _ =>
      new internal.ConcurrentMergeReader[A](outerReader, maxOpen, bufferSize)
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
      inType match {
        case inputType @ (JvmType.Boolean | JvmType.Byte | JvmType.Char | JvmType.Short | JvmType.Int) =>
          val adapted: Int => B = inputType match {
            case JvmType.Boolean =>
              val original = f.asInstanceOf[Boolean => B]
              (value: Int) => original(value != 0)
            case JvmType.Byte =>
              val original = f.asInstanceOf[Byte => B]
              (value: Int) => original(value.toByte)
            case JvmType.Char =>
              val original = f.asInstanceOf[Char => B]
              (value: Int) => original(value.toChar)
            case JvmType.Short =>
              val original = f.asInstanceOf[Short => B]
              (value: Int) => original(value.toShort)
            case _ => f.asInstanceOf[Int => B]
          }
          new internal.IntConcurrentMapParReader[B](
            reader,
            n,
            adapted,
            bufferSize,
            outType,
            inputType
          ).asInstanceOf[Reader.SyncReader[B]]
        case JvmType.Long =>
          new internal.LongConcurrentMapParReader[B](
            reader.asInstanceOf[Reader.SyncReader[Long]],
            n,
            f.asInstanceOf[Long => B],
            bufferSize,
            outType
          ).asInstanceOf[Reader.SyncReader[B]]
        case JvmType.Double =>
          new internal.DoubleConcurrentMapParReader[B](
            reader.asInstanceOf[Reader.SyncReader[Double]],
            n,
            f.asInstanceOf[Double => B],
            bufferSize,
            outType
          ).asInstanceOf[Reader.SyncReader[B]]
        case JvmType.Float =>
          new internal.FloatConcurrentMapParReader[B](
            reader.asInstanceOf[Reader.SyncReader[Float]],
            n,
            f.asInstanceOf[Float => B],
            bufferSize,
            outType
          ).asInstanceOf[Reader.SyncReader[B]]
        case _ =>
          new internal.ConcurrentMapParReader[A, B](reader, n, f, bufferSize, outType)
      }
  }

  private def fallbackThread(name: String, task: Runnable): Thread = {
    val thread = new Thread(task)
    thread.setName(name)
    thread.setDaemon(true)
    thread.start()
    thread
  }
}

/**
 * Looks up `Thread.ofVirtual` by reflection, once; `None` when the JDK has no
 * virtual threads or the lookup fails.
 */
private object VirtualThreadSupport {
  val startVirtual: Option[(String, Runnable) => Thread] =
    try {
      val lookup          = MethodHandles.lookup()
      val threadClass     = Class.forName("java.lang.Thread").asInstanceOf[Class[Thread]]
      val ofVirtualMethod = threadClass.getMethod("ofVirtual")
      val ofVirtualHandle = lookup.unreflect(ofVirtualMethod)
      val builderClass    = ofVirtualMethod.getReturnType
      val nameHandle      = lookup.unreflect(builderClass.getMethod("name", classOf[String], java.lang.Long.TYPE))
      val startHandle     = lookup.unreflect(builderClass.getMethod("start", classOf[Runnable]))

      Some { (name: String, task: Runnable) =>
        val builder      = ofVirtualHandle.invoke().asInstanceOf[AnyRef]
        val namedBuilder = nameHandle.invoke(builder, name, Long.box(0L)).asInstanceOf[AnyRef]
        startHandle.invoke(namedBuilder, task).asInstanceOf[Thread]
      }
    } catch {
      // Any reflective/initialization failure (e.g. NoSuchMethodException,
      // ClassNotFoundException, SecurityException, IllegalAccessException,
      // InvocationTargetException, InaccessibleObjectException, LinkageError)
      // must fall back to a platform thread rather than crash class init.
      case scala.util.control.NonFatal(_) => None
    }
}
