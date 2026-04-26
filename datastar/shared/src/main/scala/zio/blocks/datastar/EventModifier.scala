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

sealed trait EventModifier extends Product with Serializable {

  def render: String
}

object EventModifier {

  final case class Debounce(millis: Long, leading: Boolean) extends EventModifier {

    def render: String =
      if (leading) "__debounce." + millis + "ms.leading"
      else "__debounce." + millis + "ms"
  }

  final case class Throttle(millis: Long, leading: Boolean) extends EventModifier {

    def render: String =
      if (leading) "__throttle." + millis + "ms.leading"
      else "__throttle." + millis + "ms"
  }

  final case class Delay(millis: Long) extends EventModifier {
    def render: String = "__delay." + millis + "ms"
  }

  case object Once extends EventModifier {
    def render: String = "__once"
  }

  case object Passive extends EventModifier {
    def render: String = "__passive"
  }

  case object Capture extends EventModifier {
    def render: String = "__capture"
  }

  case object Stop extends EventModifier {
    def render: String = "__stop"
  }

  case object Prevent extends EventModifier {
    def render: String = "__prevent"
  }

  case object Outside extends EventModifier {
    def render: String = "__outside"
  }

  case object Window extends EventModifier {
    def render: String = "__window"
  }

  case object Document extends EventModifier {
    def render: String = "__document"
  }

  case object ViewTransition extends EventModifier {
    def render: String = "__viewTransition"
  }

  final case class And(left: EventModifier, right: EventModifier) extends EventModifier {
    def render: String = left.render + right.render
  }
}
