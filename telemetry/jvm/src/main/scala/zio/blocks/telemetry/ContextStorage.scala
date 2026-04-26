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
 * Scoped context storage for propagating values through call stacks.
 *
 * Only two operations:
 *   - get(): read the current value
 *   - scoped(): run a block with a different value, automatically restored
 *     after
 *
 * No set() — context is immutable outside scoped blocks. This aligns with JDK
 * 25's ScopedValue semantics and prevents context leaks.
 *
 * @see
 *   docs/adr/0001-scoped-context-no-set.md
 */
sealed trait ContextStorage[A] {
  def get(): A
  def scoped[B](value: A)(f: => B): B
}

object ContextStorage {

  val implementationName: String = "ScopedValue"

  def create[A](initial: A): ContextStorage[A] =
    new ScopedValueStorage[A](initial)

  /**
   * Pure ScopedValue storage for JDK 25+.
   *
   * ScopedValue is immutable — bindings only via `where().run()`. This aligns
   * perfectly with our two-method API: `get()` reads, `scoped()` binds.
   */
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
