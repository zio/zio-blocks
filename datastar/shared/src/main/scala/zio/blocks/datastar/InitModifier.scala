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

sealed trait InitModifier extends Product with Serializable {

  def render: String
}

object InitModifier {

  final case class Delay(millis: Long) extends InitModifier {
    def render: String = "__delay." + millis + "ms"
  }

  case object ViewTransition extends InitModifier {
    def render: String = "__viewTransition"
  }

  final case class And(left: InitModifier, right: InitModifier) extends InitModifier {
    def render: String = left.render + right.render
  }

  def normalize(existing: Option[InitModifier], next: InitModifier): Option[InitModifier] = {
    val normalized = flatten(existing.toList :+ next).foldLeft(List.empty[InitModifier]) { (acc, modifier) =>
      modifier match {
        case d: Delay =>
          acc.filter {
            case _: Delay => false
            case _        => true
          } :+ d
        case flag =>
          if (acc.exists(_ == flag)) acc else acc :+ flag
      }
    }

    normalized match {
      case Nil          => None
      case head :: Nil  => Some(head)
      case head :: tail => Some(tail.foldLeft(head)(And.apply))
    }
  }

  private def flatten(modifiers: List[InitModifier]): List[InitModifier] =
    modifiers.flatMap {
      case And(left, right) => flatten(left :: right :: Nil)
      case other            => other :: Nil
    }
}
