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

package zio.blocks.sql

import zio.test.*

object FragSequenceSpec extends ZIOSpecDefault {
  private val f1: Frag = Frag.literal("SELECT 1")
  private val f2: Frag = Frag(IndexedSeq("WHERE x = ", ""), IndexedSeq(DbValue.DbInt(42)))
  private val f3: Frag = Frag.literal("ORDER BY x")

  def spec: Spec[TestEnvironment, Any] = suite("FragSequenceSpec")(
    test("sequence with no fragments returns empty") {
      val frag = Frag.sequence()

      assertTrue(frag.isEmpty)
    },
    test("sequence with one fragment renders the same SQL") {
      val frag = Frag.sequence(f1)

      assertTrue(
        frag.sql(SqlDialect.SQLite) == f1.sql(SqlDialect.SQLite),
        frag.queryParams == f1.queryParams
      )
    },
    test("sequence with multiple fragments matches ++ rendering") {
      val sequenceFrag = Frag.sequence(f1, f2, f3)
      val plusFrag     = f1 ++ f2 ++ f3

      assertTrue(
        sequenceFrag.sql(SqlDialect.SQLite) == plusFrag.sql(SqlDialect.SQLite),
        sequenceFrag.queryParams == plusFrag.queryParams,
        sequenceFrag.queryParams == IndexedSeq(DbValue.DbInt(42))
      )
    }
  )
}
