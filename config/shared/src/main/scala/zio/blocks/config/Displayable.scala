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

/**
 * Renders a value as text for display, as `Flag.dump` and
 * `StaticFlag#displayValue` do. A [[StaticFlag]] needs an implicit one for its
 * type; `Displayable.instance` builds one, and a type without an instance is
 * shown with `toString`.
 */
trait Displayable[A] {

  /**
   * Returns the text that stands for `value`.
   */
  def display(value: A): String
}

/**
 * Holds the `toString` fallback, so that any more specific [[Displayable]]
 * instance is chosen before it.
 */
trait DisplayableLowPriority {

  /**
   * A [[Displayable]] for any type that shows a value with `toString`.
   */
  implicit def fallback[A]: Displayable[A] = Displayable._fallback.asInstanceOf[Displayable[A]]
}

/**
 * Constructors and instances for [[Displayable]].
 */
object Displayable extends DisplayableLowPriority {

  /**
   * Returns the implicit `Displayable[A]` in scope.
   */
  def apply[A](implicit displayable: Displayable[A]): Displayable[A] = displayable

  /**
   * Returns a [[Displayable]] that renders a value with `render`.
   */
  def instance[A](render: A => String): Displayable[A] = new Displayable[A] {
    def display(value: A): String = render(value)
  }

  /**
   * Shows a `String` as it is.
   */
  implicit val stringDisplayable: Displayable[String] = instance(identity)

  /**
   * Shows an `Int` with `toString`.
   */
  implicit val intDisplayable: Displayable[Int] = instance(_.toString)

  /**
   * Shows a `Long` with `toString`.
   */
  implicit val longDisplayable: Displayable[Long] = instance(_.toString)

  /**
   * Shows a `Double` with `toString`.
   */
  implicit val doubleDisplayable: Displayable[Double] = instance(_.toString)

  /**
   * Shows a `Boolean` with `toString`.
   */
  implicit val booleanDisplayable: Displayable[Boolean] = instance(_.toString)

  private[config] val _fallback: Displayable[Any] = instance(_.toString)
}
