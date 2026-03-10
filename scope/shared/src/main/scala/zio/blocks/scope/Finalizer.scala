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

/**
 * A handle for registering cleanup actions (finalizers).
 *
 * Finalizers run in LIFO order when the scope closes. This trait exposes only
 * the `defer` capability, preventing user code from accessing scope internals
 * like `allocate` or `close`.
 */
trait Finalizer {

  /**
   * Registers a finalizer to run when the scope closes.
   *
   * @param f
   *   a by-name expression to execute during cleanup
   * @return
   *   a [[DeferHandle]] that can be used to cancel the registered finalizer
   *   before the scope closes
   */
  def defer(f: => Unit): DeferHandle
}
