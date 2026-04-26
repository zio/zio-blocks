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

sealed trait IntersectModifier extends Product with Serializable {

  def render: String
}

object IntersectModifier {

  case object Once extends IntersectModifier {
    def render: String = "__once"
  }

  case object Half extends IntersectModifier {
    def render: String = "__half"
  }

  case object Full extends IntersectModifier {
    def render: String = "__full"
  }

  case object Exit extends IntersectModifier {
    def render: String = "__exit"
  }

  final case class Threshold(pct: Double) extends IntersectModifier {
    def render: String = "__threshold." + pct
  }

  final case class Delay(millis: Long) extends IntersectModifier {
    def render: String = "__delay." + millis + "ms"
  }

  final case class Debounce(millis: Long) extends IntersectModifier {
    def render: String = "__debounce." + millis + "ms"
  }

  final case class Throttle(millis: Long) extends IntersectModifier {
    def render: String = "__throttle." + millis + "ms"
  }

  case object ViewTransition extends IntersectModifier {
    def render: String = "__viewTransition"
  }

  final case class And(left: IntersectModifier, right: IntersectModifier) extends IntersectModifier {
    def render: String = left.render + right.render
  }
}
