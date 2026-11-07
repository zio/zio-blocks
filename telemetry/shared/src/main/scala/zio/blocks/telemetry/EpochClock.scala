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
 * Unix epoch time in nanoseconds: the wall-clock time read once when the object
 * loads, plus the `System.nanoTime` elapsed since. It does not follow later
 * changes to the system clock.
 */
private[telemetry] object EpochClock {
  private val epochMillisBase: Long = System.currentTimeMillis()
  private val nanoTimeBase: Long    = System.nanoTime()

  /** Returns the current Unix epoch time in nanoseconds. */
  def epochNanos(): Long = {
    val delta = System.nanoTime() - nanoTimeBase
    (epochMillisBase * 1000000L) + delta
  }
}
