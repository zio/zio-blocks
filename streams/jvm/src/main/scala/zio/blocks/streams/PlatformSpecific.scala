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
 * JVM implementation of the cross-platform [[Platform]] capabilities.
 *
 * Mixed into the package-level `Platform` object on JVM, so call sites use the
 * same `Platform.*` surface regardless of platform.
 *
 *   - `supportsConcurrency` is `true`.
 *   - `startVirtualThread` uses `Thread.ofVirtual().start(...)` reflectively
 *     when running on JDK 21+, falling back to a daemon `Thread` otherwise.
 *   - `createBufferedReader`, `createMergeReader`, and `createMapParReader`
 *     return primitive-specialized readers (Int / Long / Float / Double) when
 *     the upstream's `JvmType` allows it, and the generic AnyRef variants
 *     otherwise.
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

  override def createBufferedReader[A](upstream: Reader[A], bufferSize: Int): Reader[A] =
    new internal.ConcurrentBufferedReader(upstream, bufferSize)

  override def createMergeReader[A](
    outerReader: Reader[?],
    maxOpen: Int,
    bufferSize: Int,
    elemType: JvmType
  ): Reader[A] =
    elemType match {
      case JvmType.Int =>
        new internal.IntConcurrentMergeReader(outerReader, maxOpen, bufferSize).asInstanceOf[Reader[A]]
      case JvmType.Long =>
        new internal.LongConcurrentMergeReader(outerReader, maxOpen, bufferSize).asInstanceOf[Reader[A]]
      case JvmType.Double =>
        new internal.DoubleConcurrentMergeReader(outerReader, maxOpen, bufferSize).asInstanceOf[Reader[A]]
      case JvmType.Float =>
        new internal.FloatConcurrentMergeReader(outerReader, maxOpen, bufferSize).asInstanceOf[Reader[A]]
      case _ =>
        new internal.ConcurrentMergeReader[A](outerReader, maxOpen, bufferSize)
    }

  override def createMapParReader[A, B](
    upstream: Reader[A],
    n: Int,
    f: A => B,
    bufferSize: Int,
    inType: JvmType,
    outType: JvmType
  ): Reader[B] =
    inType match {
      case JvmType.Int =>
        new internal.IntConcurrentMapParReader[B](
          upstream.asInstanceOf[Reader[Int]],
          n,
          f.asInstanceOf[Int => B],
          bufferSize,
          outType
        ).asInstanceOf[Reader[B]]
      case JvmType.Long =>
        new internal.LongConcurrentMapParReader[B](
          upstream.asInstanceOf[Reader[Long]],
          n,
          f.asInstanceOf[Long => B],
          bufferSize,
          outType
        ).asInstanceOf[Reader[B]]
      case JvmType.Double =>
        new internal.DoubleConcurrentMapParReader[B](
          upstream.asInstanceOf[Reader[Double]],
          n,
          f.asInstanceOf[Double => B],
          bufferSize,
          outType
        ).asInstanceOf[Reader[B]]
      case JvmType.Float =>
        new internal.FloatConcurrentMapParReader[B](
          upstream.asInstanceOf[Reader[Float]],
          n,
          f.asInstanceOf[Float => B],
          bufferSize,
          outType
        ).asInstanceOf[Reader[B]]
      case _ =>
        new internal.ConcurrentMapParReader[A, B](upstream, n, f, bufferSize)
    }

  private def fallbackThread(name: String, task: Runnable): Thread = {
    val thread = new Thread(task)
    thread.setName(name)
    thread.setDaemon(true)
    thread.start()
    thread
  }
}

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
