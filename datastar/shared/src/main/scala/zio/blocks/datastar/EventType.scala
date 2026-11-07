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

package zio.http.datastar

/**
 * The two Datastar event names written in the SSE `event:` field. The
 * [[DatastarEvent]] constructors pick the right one: element patches, script
 * runs and removals use `PatchElements`, and signal patches use `PatchSignals`.
 */
sealed trait EventType extends Product with Serializable {

  /** Returns the exact SSE `event:` value emitted for this event type. */
  def render: String
}

/** The Datastar SSE event names. */
object EventType {

  /** The event that patches elements. */
  case object PatchElements extends EventType {

    /** Returns `datastar-patch-elements`. */
    def render: String = "datastar-patch-elements"
  }

  /** The event that patches signals. */
  case object PatchSignals extends EventType {

    /** Returns `datastar-patch-signals`. */
    def render: String = "datastar-patch-signals"
  }
}
