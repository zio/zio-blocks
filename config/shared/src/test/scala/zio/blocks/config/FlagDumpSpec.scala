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

import zio.test._

object FlagDumpSpec extends ConfigBaseSpec {

  def spec = suite("FlagDumpSpec")(
    suite("Flag.all and Flag.get")(
      test("returns registered flags") {
        val allBefore = Flag.all
        assertTrue(allBefore.isInstanceOf[List[_]])
      },
      test("get returns None for unknown flag") {
        assertTrue(Flag.get("nonexistent.flag.xyz") == None)
      }
    ),
    suite("Flag.dump")(
      test("returns table or empty message") {
        val dumped = Flag.dump()
        assertTrue(dumped.nonEmpty)
      }
    ),
    suite("Flag.nearMissWarnings")(
      test("detects case-insensitive match in system properties") {
        val propName = "zio.blocks.config.test.NearMissFlag"
        System.setProperty(propName, "42")
        val warnings = Flag.nearMissWarnings("zio.blocks.config.test.nearmissflag")
        System.clearProperty(propName)
        assertTrue(
          warnings.length == 1,
          warnings.head.contains("Near-miss")
        )
      },
      test("returns empty when no near miss") {
        val warnings = Flag.nearMissWarnings("definitely.unique.flag.name.xyz123")
        assertTrue(warnings.isEmpty)
      },
      test("does not trigger for exact match") {
        val propName = "zio.blocks.config.test.exactFlag"
        System.setProperty(propName, "hello")
        val warnings = Flag.nearMissWarnings(propName)
        System.clearProperty(propName)
        assertTrue(warnings.isEmpty)
      }
    )
  )
}
