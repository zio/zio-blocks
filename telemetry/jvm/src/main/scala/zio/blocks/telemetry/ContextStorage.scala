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

/**
 * Holds a value for the code that runs inside `scoped`, and an initial value
 * everywhere else. The global `log` and `trace` keep the current span in one.
 *
 * There is no setter: `scoped(value)(f)` binds `value` while `f` runs and
 * restores the previous value afterwards, even when `f` throws. On the JVM the
 * storage is a `ScopedValue`, so the binding belongs to the current thread, and
 * a thread that `f` starts sees the initial value. On Scala.js it is a plain
 * variable, so a callback that runs after `f` returns sees the previous value.
 *
 * The storage that the global `log` and `trace` share is not public. A program
 * that needs its own span storage must pass the same instance to
 * `LoggerProviderBuilder#setContextStorage` and
 * `TracerProviderBuilder#setContextStorage`: with different storages, log
 * records carry no trace or span ids and nothing warns about it, a known bug.
 *
 * {{{
 * val requestId = ContextStorage.create("none")
 *
 * requestId.scoped("req-42") { requestId.get() } // req-42
 * requestId.get() // none
 * }}}
 */
sealed trait ContextStorage[A] {

  /**
   * Returns the value bound by the innermost enclosing `scoped`, or the initial
   * value outside one.
   */
  def get(): A

  /**
   * Runs `f` with `value` bound, restores the previous value when `f` returns
   * or throws, and returns the result of `f`.
   */
  def scoped[B](value: A)(f: => B): B
}

/** Creates [[ContextStorage]]s. */
object ContextStorage {

  /**
   * Names the mechanism behind the storage: `"ScopedValue"` on the JVM,
   * `"JSGlobal"` on Scala.js.
   */
  val implementationName: String = "ScopedValue"

  /** Returns a new storage whose value is `initial` outside any `scoped` block. */
  def create[A](initial: A): ContextStorage[A] =
    new ScopedValueStorage[A](initial)

  private[telemetry] val defaultSpanContextStorage: ContextStorage[Option[SpanContext]] =
    create[Option[SpanContext]](None)

  /** A storage backed by a `ScopedValue`. */
  private final class ScopedValueStorage[A](initial: A) extends ContextStorage[A] {
    private val sv: ScopedValue[A] = ScopedValue.newInstance()

    def get(): A =
      if (sv.isBound) sv.get() else initial

    def scoped[B](value: A)(f: => B): B = {
      var result: B         = null.asInstanceOf[B]
      var thrown: Throwable = null
      ScopedValue
        .where(sv, value)
        .run(() =>
          try result = f
          catch { case t: Throwable => thrown = t }
        )
      if (thrown != null) throw thrown
      result
    }
  }
}
