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

package zio.blocks.scope

import zio.blocks.chunk.Chunk

/**
 * The result of running finalizers, collecting any errors that occurred.
 *
 * When a scope closes, each registered finalizer runs in LIFO order. Finalizers
 * that throw are caught and their exceptions are collected into a
 * `Finalization`. This type provides convenient methods for inspecting and
 * re-throwing those errors.
 *
 * @param errors
 *   the errors collected from running finalizers
 */
final class Finalization(val errors: Chunk[Throwable]) {

  /**
   * Returns `true` if no finalizer errors were collected.
   */
  def isEmpty: Boolean = errors.isEmpty

  /**
   * Returns `true` if at least one finalizer error was collected.
   */
  def nonEmpty: Boolean = errors.nonEmpty

  /**
   * Throws the first collected error with all remaining errors added as
   * suppressed. Does nothing if there are no errors.
   *
   * The first error corresponds to the head of the chunk (the first finalizer
   * that failed in LIFO execution order).
   */
  def orThrow(): Unit =
    if (errors.nonEmpty) {
      val first = errors.head
      errors.tail.foreach(first.addSuppressed)
      throw first
    }

  /**
   * Adds all collected errors as suppressed exceptions to `initial` and returns
   * it. If there are no errors, `initial` is returned unchanged.
   *
   * @param initial
   *   the primary throwable to attach suppressed exceptions to
   * @return
   *   `initial`, with any finalizer errors added as suppressed
   */
  def suppress(initial: Throwable): Throwable = {
    errors.foreach(initial.addSuppressed)
    initial
  }
}

object Finalization {

  /**
   * A singleton `Finalization` with no errors.
   */
  val empty: Finalization = new Finalization(Chunk.empty)

  /**
   * Creates a `Finalization` from the given chunk of errors.
   *
   * @param errors
   *   the errors collected from running finalizers
   */
  def apply(errors: Chunk[Throwable]): Finalization = new Finalization(errors)
}
