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

package zio.blocks.combinators

import zio.test._

object ChoicesSpec extends ZIOSpecDefault {

  def spec = suite("Choices")(
    test("constructs and separates a two-way choice") {
      val leftValue: Int | String  = Choices.left[Int, String](42)
      val rightValue: Int | String = Choices.right[Int, String]("hello")

      assertTrue(
        Choices.separate[Int, String](leftValue) == Left(42),
        Choices.separate[Int, String](rightValue) == Right("hello")
      )
    },
    test("constructs and separates a three-way choice left-nest") {
      val intValue: Int | String | Boolean     = Choices.left[Int | String, Boolean](Choices.left[Int, String](42))
      val stringValue: Int | String | Boolean  = Choices.left[Int | String, Boolean](Choices.right[Int, String]("zio"))
      val booleanValue: Int | String | Boolean = Choices.right[Int | String, Boolean](true)

      val intSeparated     = Choices.separate[Int | String, Boolean](intValue)
      val stringSeparated  = Choices.separate[Int | String, Boolean](stringValue)
      val booleanSeparated = Choices.separate[Int | String, Boolean](booleanValue)

      assertTrue(
        intSeparated == Left(Choices.left[Int, String](42)),
        stringSeparated == Left(Choices.right[Int, String]("zio")),
        booleanSeparated == Right(true)
      )
    }
  )
}
