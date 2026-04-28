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

sealed trait OnSignalPatchModifier extends Product with Serializable {

  def render: String
}

object OnSignalPatchModifier {

  final case class Delay(millis: Long) extends OnSignalPatchModifier {
    def render: String = "__delay." + millis + "ms"
  }

  final case class Debounce(millis: Long) extends OnSignalPatchModifier {
    def render: String = "__debounce." + millis + "ms"
  }

  final case class Throttle(millis: Long) extends OnSignalPatchModifier {
    def render: String = "__throttle." + millis + "ms"
  }

  final case class And(left: OnSignalPatchModifier, right: OnSignalPatchModifier) extends OnSignalPatchModifier {
    def render: String = left.render + right.render
  }

  def normalize(existing: Option[OnSignalPatchModifier], next: OnSignalPatchModifier): Option[OnSignalPatchModifier] = {
    val normalized = flatten(existing.toList :+ next).foldLeft(List.empty[OnSignalPatchModifier]) { (acc, modifier) =>
      modifier match {
        case d: Delay =>
          acc.filter {
            case _: Delay => false
            case _        => true
          } :+ d
        case d: Debounce =>
          acc.filter {
            case _: Debounce => false
            case _           => true
          } :+ d
        case t: Throttle =>
          acc.filter {
            case _: Throttle => false
            case _           => true
          } :+ t
        case other =>
          if (acc.exists(_ == other)) acc else acc :+ other
      }
    }

    normalized match {
      case Nil          => None
      case head :: Nil  => Some(head)
      case head :: tail => Some(tail.foldLeft(head)(And.apply))
    }
  }

  private def flatten(modifiers: List[OnSignalPatchModifier]): List[OnSignalPatchModifier] =
    modifiers.flatMap {
      case And(left, right) => flatten(left :: right :: Nil)
      case other            => other :: Nil
    }
}
