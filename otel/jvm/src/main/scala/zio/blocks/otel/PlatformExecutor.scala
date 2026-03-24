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

package zio.blocks.otel

import java.lang.invoke.MethodHandles
import java.util.concurrent._
import java.util.concurrent.atomic.AtomicLong

final class PlatformExecutor private (val executor: ScheduledExecutorService) {

  def schedule(initialDelay: Long, period: Long, unit: TimeUnit)(task: Runnable): ScheduledFuture[_] =
    executor.scheduleAtFixedRate(task, initialDelay, period, unit)

  def shutdown(): Unit =
    executor.shutdown()
}

object PlatformExecutor {

  val hasLoom: Boolean = ContextStorage.hasLoom

  def create(): PlatformExecutor =
    new PlatformExecutor(createExecutor())

  private def createExecutor(): ScheduledExecutorService =
    if (hasLoom) createVirtualThreadExecutor()
    else createDaemonThreadExecutor()

  private def createVirtualThreadExecutor(): ScheduledExecutorService =
    try {
      val lookup      = MethodHandles.lookup()
      val threadClass = classOf[Thread]

      // Thread.ofVirtual() — static method returning Thread.Builder.OfVirtual
      val ofVirtualMethod = threadClass.getMethod("ofVirtual")
      val ofVirtualHandle = lookup.unreflect(ofVirtualMethod)
      val builder         = ofVirtualHandle.invoke()

      // builder.name("otel-", 0) — returns Thread.Builder.OfVirtual
      // The name(String, long) method is on Thread.Builder.OfVirtual interface
      val ofVirtualClass = Class.forName("java.lang.Thread$Builder$OfVirtual")
      val nameMethod     = ofVirtualClass.getMethod("name", classOf[String], java.lang.Long.TYPE)
      val nameHandle     = lookup.unreflect(nameMethod)
      val namedBuilder   = nameHandle.invoke(builder, "otel-", java.lang.Long.valueOf(0L))

      // builder.factory() — returns ThreadFactory (on Thread.Builder interface)
      val builderClass  = Class.forName("java.lang.Thread$Builder")
      val factoryMethod = builderClass.getMethod("factory")
      val factoryHandle = lookup.unreflect(factoryMethod)
      val factory       = factoryHandle.invoke(namedBuilder).asInstanceOf[ThreadFactory]

      Executors.newScheduledThreadPool(1, factory)
    } catch {
      case _: Exception =>
        createDaemonThreadExecutor()
    }

  private def createDaemonThreadExecutor(): ScheduledExecutorService = {
    val counter                = new AtomicLong(0L)
    val factory: ThreadFactory = new ThreadFactory {
      def newThread(r: Runnable): Thread = {
        val t = new Thread(r, "otel-daemon-" + counter.getAndIncrement())
        t.setDaemon(true)
        t
      }
    }
    Executors.newSingleThreadScheduledExecutor(factory)
  }
}
