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
 * Receives the values that the callback of an observable instrument reports:
 * the instrument passes a fresh one to its callback on each collection.
 */
trait ObservableCallback {

  /**
   * Reports `value` for the attribute set `attributes`. Each call adds one data
   * point, even for a set reported before, so report each set once per
   * collection.
   */
  def record(value: Double, attributes: Attributes): Unit
}
