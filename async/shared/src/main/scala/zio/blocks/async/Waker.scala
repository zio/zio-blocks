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

package zio.blocks.async

/**
 * Single-method callback handed to a [[zio.blocks.async.Pollable]] when it
 * suspends. The leaf (timer, socket, completer, ...) stashes the waker and
 * invokes [[wake]] when its value becomes available, asking the scheduler to
 * re-poll the pending computation.
 */
trait Waker {
  def wake(): Unit
}
