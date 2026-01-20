/*
 * Copyright 2023 ZIO Blocks Maintainers
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

package zio.blocks.schema

sealed trait Validation[+A]

object Validation {
  case object None extends Validation[Nothing]

  sealed trait Numeric[+A] extends Validation[A]

  object Numeric {
    case object Positive extends Numeric[Nothing]

    case object Negative extends Numeric[Nothing]

    case object NonPositive extends Numeric[Nothing]

    case object NonNegative extends Numeric[Nothing]

    case class Range[A](min: Option[A], max: Option[A]) extends Numeric[A]

    case class Set[A](values: Set[A]) extends Numeric[A]
  }

  sealed trait String extends Validation[String]

  object String {
    case object NonEmpty extends String

    case object Empty extends String

    case object Blank extends String

    case object NonBlank extends String

    case class Length(min: Option[scala.Int], max: Option[scala.Int]) extends String

    case class Pattern(regex: String) extends String
  }
}
