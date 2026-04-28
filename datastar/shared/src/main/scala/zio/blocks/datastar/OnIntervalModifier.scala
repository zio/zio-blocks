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

sealed trait OnIntervalModifier extends Product with Serializable {

  def render: String
}

object OnIntervalModifier {

  final case class Duration(millis: Long, leading: Boolean) extends OnIntervalModifier {

    def render: String =
      if (leading) "__duration." + millis + "ms.leading"
      else "__duration." + millis + "ms"
  }

  case object ViewTransition extends OnIntervalModifier {
    def render: String = "__viewTransition"
  }

  final case class And(left: OnIntervalModifier, right: OnIntervalModifier) extends OnIntervalModifier {
    def render: String = left.render + right.render
  }

  def normalize(existing: Option[OnIntervalModifier], next: OnIntervalModifier): Option[OnIntervalModifier] = {
    val normalized = flatten(existing.toList :+ next).foldLeft(List.empty[OnIntervalModifier]) { (acc, modifier) =>
      modifier match {
        case d: Duration =>
          acc.filter {
            case _: Duration => false
            case _           => true
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

  private def flatten(modifiers: List[OnIntervalModifier]): List[OnIntervalModifier] =
    modifiers.flatMap {
      case And(left, right) => flatten(left :: right :: Nil)
      case other            => other :: Nil
    }
}
