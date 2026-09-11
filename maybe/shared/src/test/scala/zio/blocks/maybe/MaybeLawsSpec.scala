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

object MaybeLawsSpec extends ZIOSpecDefault {
  def spec = suite("MaybeLaws")(
    test("present(absent) is present, not absent") {
      val nested: Maybe[Maybe[String]] = Maybe.present(Maybe.absent[String])
      assertTrue(nested.isPresent, !nested.isAbsent)
    },
    test("present of a Present unwraps to the original through unsafeGet") {
      val inner: Maybe[Maybe[String]]        = Maybe.present(Maybe.absent[String])
      val outer: Maybe[Maybe[Maybe[String]]] = Maybe.present(inner)
      assertTrue(outer.isPresent, Maybe.unsafeGet(outer) == inner)
    },
    test("unsafeGet(absent) is null") {
      assertTrue(Maybe.unsafeGet(Maybe.absent[String]) == null)
    },
    test("unsafeWrap maps null to absent and values to present") {
      val absent: Maybe[String]  = Maybe.unsafeWrap[String](null)
      val present: Maybe[String] = Maybe.unsafeWrap[String]("x")
      assertTrue(absent.isAbsent, present.isPresent, Maybe.unsafeGet(present) == "x")
    },
    test("unsafeIsAbsent distinguishes absent from present") {
      assertTrue(
        Maybe.unsafeIsAbsent(Maybe.absent),
        !Maybe.unsafeIsAbsent(Maybe.present("x")),
        !Maybe.unsafeIsAbsent(Maybe.present(Maybe.absent[String]))
      )
    },
    test("Present hashCode is consistent for null and values") {
      val nullA: Maybe[String]  = Maybe.present(null.asInstanceOf[String])
      val nullB: Maybe[String]  = Maybe.present(null.asInstanceOf[String])
      val valueA: Maybe[String] = Maybe.present("x")
      val valueB: Maybe[String] = Maybe.present("x")
      assertTrue(nullA.hashCode == nullB.hashCode, valueA.hashCode == valueB.hashCode)
    },
    test("withFilter combinators respect the predicate") {
      val value: Maybe[Int] = Maybe.present(2)
      var seen              = 0
      value.withFilter(_ > 1).foreach(x => seen = x)
      assertTrue(
        value.withFilter(_ > 1).map(_ * 10) == Maybe.present(20),
        value.withFilter(_ > 5).map(_ * 10).isAbsent,
        value.withFilter(_ > 1).flatMap(x => Maybe.present(x + 1)) == Maybe.present(3),
        seen == 2
      )
    }
  )
}
