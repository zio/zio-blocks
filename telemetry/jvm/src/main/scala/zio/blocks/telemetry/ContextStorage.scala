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

package zio.blocks.telemetry

sealed trait ContextStorage[A] {
  def get(): A
  def set(value: A): Unit
  def scoped[B](value: A)(f: => B): B
}

object ContextStorage {

  val hasLoom: Boolean = true

  val implementationName: String = "ScopedValue"

  def create[A](initial: A): ContextStorage[A] =
    new HybridStorage[A](initial)

  /**
   * Hybrid ScopedValue + ThreadLocal storage for JDK 25+.
   *
   * ScopedValue is immutable — bindings only via `where().run()`. Our API
   * requires mutable `set()` for imperative context propagation (e.g.
   * TracerProvider setting trace context). Solution: ScopedValue for scoped
   * reads (fast path, virtual-thread-friendly), ThreadLocal fallback for
   * explicit `set()` calls (rare).
   *
   * Read priority: ScopedValue binding > ThreadLocal > initial value.
   */
  private final class HybridStorage[A](initial: A) extends ContextStorage[A] {
    private val scopedVal: ScopedValue[A]   = ScopedValue.newInstance()
    private val threadLocal: ThreadLocal[A] = new ThreadLocal[A] {
      override def initialValue(): A = initial
    }

    def get(): A =
      if (scopedVal.isBound) scopedVal.get()
      else threadLocal.get()

    def set(value: A): Unit =
      threadLocal.set(value)

    def scoped[B](value: A)(f: => B): B = {
      var result: B         = null.asInstanceOf[B]
      var thrown: Throwable = null
      ScopedValue
        .where(scopedVal, value)
        .run(() =>
          try result = f
          catch { case t: Throwable => thrown = t }
        )
      if (thrown != null) throw thrown
      result
    }
  }
}
