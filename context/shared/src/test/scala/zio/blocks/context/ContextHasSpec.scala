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

package zio.blocks.context

import zio.test._

object ContextHasSpec extends ZIOSpecDefault {

  trait Logger
  trait Database
  trait Cache

  def spec = suite("ContextHas")(
    test("resolves when A is the exact Ctx type") {
      val ev = implicitly[ContextHas[Logger, Logger]]
      assertTrue(ev != null)
    },
    test("resolves when A is part of an intersection Ctx") {
      val ev = implicitly[ContextHas[Logger & Database, Logger]]
      assertTrue(ev != null)
    },
    test("resolves for each component of a multi-type intersection") {
      val ev1 = implicitly[ContextHas[Logger & Database & Cache, Logger]]
      val ev2 = implicitly[ContextHas[Logger & Database & Cache, Database]]
      val ev3 = implicitly[ContextHas[Logger & Database & Cache, Cache]]
      assertTrue(ev1 != null, ev2 != null, ev3 != null)
    },
    test("all evidence instances share the same runtime object") {
      val ev1 = implicitly[ContextHas[Logger & Database, Logger]]
      val ev2 = implicitly[ContextHas[Logger & Database, Database]]
      assertTrue(ev1.asInstanceOf[AnyRef] eq ev2.asInstanceOf[AnyRef])
    },
    test("does not compile when Ctx is missing a required type") {
      val expected =
        "Context[zio.blocks.context.ContextHasSpec.Logger] does not provide zio.blocks.context.ContextHasSpec.Database." +
          " Compare the two types \u2014 each component of zio.blocks.context.ContextHasSpec.Database" +
          " not in zio.blocks.context.ContextHasSpec.Logger must be added via context.add(value)."
      typeCheck(
        "implicitly[zio.blocks.context.ContextHas[zio.blocks.context.ContextHasSpec.Logger, zio.blocks.context.ContextHasSpec.Database]]"
      ).map { result =>
        val actual = result.left.getOrElse("")
        assertTrue(actual == expected || actual.startsWith(expected))
      }
    }
  )
}
