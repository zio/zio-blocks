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

package zio.blocks

import zio.blocks.combinators.Unions

package object streams {
  def separateStringInt(value: String | Int): Either[String, Int] =
    summon[Unions.Unions.WithOut[String, Int, String | Int]].separate(value)

  def separateStringIntBoolean(value: String | Int | Boolean): Either[Either[String, Int], Boolean] =
    summon[Unions.Unions.WithOut[String | Int, Boolean, String | Int | Boolean]].separate(value) match {
      case Left(left)  => Left(separateStringInt(left))
      case Right(last) => Right(last)
    }

  def separateStringIntBooleanDouble(
    value: String | Int | Boolean | Double
  ): Either[Either[Either[String, Int], Boolean], Double] =
    summon[Unions.Unions.WithOut[String | Int | Boolean, Double, String | Int | Boolean | Double]].separate(value) match {
      case Left(left)  => Left(separateStringIntBoolean(left))
      case Right(last) => Right(last)
    }
}
