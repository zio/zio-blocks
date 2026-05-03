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

package zio.blocks.maybe

import zio.test._

object MaybeSpec extends ZIOSpecDefault {
  def spec = suite("Maybe")(
    test("present value is defined") {
      val value: Maybe[String] = Maybe.present("hello")
      assertTrue(value.isDefined, value.get == "hello", value.toOption == Some("hello"))
    },
    test("absent value is empty") {
      val value: Maybe[String] = Maybe.absent
      assertTrue(value.isEmpty, value.toOption == None, value.getOrElse("fallback") == "fallback")
    },
    test("fromOption converts Option") {
      val present: Maybe[Int] = Maybe.fromOption(Some(42))
      val absent: Maybe[Int]  = Maybe.fromOption(None)
      assertTrue(present.get == 42, absent.isAbsent)
    },
    test("map and flatMap preserve absent/present semantics") {
      val present: Maybe[Int] = Maybe.present(1)
      val absent: Maybe[Int]  = Maybe.absent
      assertTrue(
        present.map(_ + 1).get == 2,
        present.flatMap(n => Maybe.present(n + 2)).get == 3,
        absent.map(_ + 1).isAbsent,
        absent.flatMap(n => Maybe.present(n + 2)).isAbsent
      )
    }
  )
}
