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
import zio.test.Assertion._

import MaybeAssertions._

object MaybeAssertionsSpec extends ZIOSpecDefault {
  def spec = suite("MaybeAssertions")(
    test("isAbsent accepts absent") {
      assert(Maybe.absent[Int])(isAbsent)
    },
    test("isAbsent rejects present raw") {
      assertTrue(!isAbsent.test(Maybe.present(1)))
    },
    test("isAbsent rejects present-of-absent") {
      assertTrue(!isAbsent.test(Maybe.present(Maybe.absent[Int])))
    },
    test("isPresent (no-arg) accepts present raw") {
      assert(Maybe.present(1))(isPresent)
    },
    test("isPresent (no-arg) accepts present-of-absent") {
      assert(Maybe.present(Maybe.absent[Int]))(isPresent)
    },
    test("isPresent (no-arg) accepts present(null)") {
      assert(Maybe.present(null: String))(isPresent)
    },
    test("isPresent (no-arg) rejects absent") {
      assertTrue(!isPresent.test(Maybe.absent[Int]))
    },
    test("isPresent(assertion) accepts present raw satisfying the inner assertion") {
      assert(Maybe.present(1))(isPresent(equalTo(1)))
    },
    test("isPresent(assertion) rejects present raw failing the inner assertion") {
      assertTrue(!isPresent(equalTo(2)).test(Maybe.present(1)))
    },
    test("isPresent(assertion) rejects absent") {
      assertTrue(!isPresent(equalTo(1)).test(Maybe.absent[Int]))
    }
  )
}
