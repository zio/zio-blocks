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

package zio.blocks.datastar

/**
 * Datastar event types emitted via the SSE `event:` field.
 *
 * The rendered string returned by [[render]] is the protocol-level event name
 * sent on the wire.
 */
sealed trait EventType extends Product with Serializable {

  /** Returns the exact SSE `event:` value emitted for this event type. */
  def render: String
}

object EventType {

  /** Emits the `datastar-patch-elements` SSE event type. */
  case object PatchElements extends EventType {
    def render: String = "datastar-patch-elements"
  }

  /** Emits the `datastar-patch-signals` SSE event type. */
  case object PatchSignals extends EventType {
    def render: String = "datastar-patch-signals"
  }
}
