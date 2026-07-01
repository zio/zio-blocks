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

package zio.blocks.async.internal

import zio.blocks.async.Async

/**
 * [[Async.Running]] that has already settled to a terminal [[Async]] encoding.
 */
private[async] final class CompletedRunning[A](private val terminal: Any) extends Async.Running[A] {

  def cancel(): Unit = ()

  def poll(onComplete: Runnable): Async[A] = terminal.asInstanceOf[Async[A]]
}
