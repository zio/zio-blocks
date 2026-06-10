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

/**
 * Internal parking primitive used by `Async.slowPath.awaitSuspended`. One
 * instance is allocated per `await` call and reused across loop iterations.
 *
 *   - [[reset]] is called before each `poll`, under the same exclusion the
 *     onComplete uses, so a wake that races with the poll cannot be lost.
 *   - [[onComplete]] is the single, stable [[Runnable]] handed to every poll.
 *   - [[park]] blocks until the onComplete fires (JVM) or throws if the
 *     suspension did not complete synchronously (Scala.js — no thread to park).
 */
private[async] abstract class Parker {
  def onComplete: Runnable
  def reset(): Unit
  def park(): Unit
}
