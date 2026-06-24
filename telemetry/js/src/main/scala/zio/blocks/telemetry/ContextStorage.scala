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
 * No set() — context is immutable outside scoped blocks. This prevents context
 * leaks and aligns with the JVM's ScopedValue semantics.
 *
 * @see
 *   docs/adr/0001-scoped-context-no-set.md
 */
sealed trait ContextStorage[A] {
  def get(): A
  def scoped[B](value: A)(f: => B): B
}

object ContextStorage {

  val implementationName: String = "JSGlobal"

  def create[A](initial: A): ContextStorage[A] = new JsStorage[A](initial)

  private final class JsStorage[A](initial: A) extends ContextStorage[A] {
    private var current: A = initial

    def get(): A = current

    def scoped[B](value: A)(f: => B): B = {
      val prev = current
      current = value
      try f
      finally { current = prev }
    }
  }
}
