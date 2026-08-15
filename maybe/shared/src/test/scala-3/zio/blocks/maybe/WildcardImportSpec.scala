/*
 * Copyright 2024-2026 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless otherwise indicated, this file is licensed under the
 * terms of the Apache License, Version 2.0 (the "License");
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

package zio.blocks.maybe.userview

import zio.blocks.maybe.*
import zio.test.*

object WildcardImportSpec extends ZIOSpecDefault {
  def spec = suite("Maybe wildcard import (bare Present/Absent)")(
    test("case Present(v) / case Absent work via import zio.blocks.maybe.*") {
      val rawPresent: Maybe[Int]             = Maybe.present(1)
      val presentOfAbsent: Maybe[Maybe[Int]] = Maybe.present(Maybe.absent[Int])
      val absentVal: Maybe[Int]              = Maybe.absent[Int]

      assertTrue(
        // raw present extracts via bare Present
        (rawPresent match { case Present(v) => v == 1; case Absent => false }),
        // present-of-absent extracts via bare Present
        (presentOfAbsent match { case Present(v) => v.isAbsent; case Absent => false }),
        // absent matches bare Absent
        (absentVal match { case Present(_) => false; case Absent => true }),
        // two-case bare idiom on present
        (rawPresent match { case Present(v) => v; case Absent => -1 }) == 1,
        // two-case bare idiom on absent
        (absentVal match { case Present(_) => -1; case Absent => 0 }) == 0,
        // construction and predicates still work
        Maybe.present(42).isPresent,
        Maybe.absent[Int].isAbsent
      )
    }
  )
}
