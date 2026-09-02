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

package zio.blocks.config

trait Displayable[A] {
  def display(value: A): String
}

trait DisplayableLowPriority {
  implicit def fallback[A]: Displayable[A] = Displayable._fallback.asInstanceOf[Displayable[A]]
}

object Displayable extends DisplayableLowPriority {
  def apply[A](implicit displayable: Displayable[A]): Displayable[A] = displayable

  def instance[A](render: A => String): Displayable[A] = new Displayable[A] {
    def display(value: A): String = render(value)
  }

  implicit val stringDisplayable: Displayable[String]   = instance(identity)
  implicit val intDisplayable: Displayable[Int]         = instance(_.toString)
  implicit val longDisplayable: Displayable[Long]       = instance(_.toString)
  implicit val doubleDisplayable: Displayable[Double]   = instance(_.toString)
  implicit val booleanDisplayable: Displayable[Boolean] = instance(_.toString)

  private[config] val _fallback: Displayable[Any] = instance(_.toString)
}
