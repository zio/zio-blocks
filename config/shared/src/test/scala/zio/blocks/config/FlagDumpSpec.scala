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
import zio.test.TestAspect

object FlagDumpSpec extends ConfigBaseSpec {

  object PlainFlag  extends StaticFlag[String]("plain-value")
  object SecretFlag extends StaticFlag[Secret](Secret("hunter2"))

  def spec = suite("FlagDumpSpec")(
    suite("Flag.dump")(
      test("returns table or empty message") {
        val dumped = Flag.dump()
        assertTrue(dumped.nonEmpty)
      },
      test("redacts secret flag values while keeping plain values") {
        val _      = (PlainFlag.value, SecretFlag.value) // force initialization
        val dumped = Flag.dump()
        assertTrue(
          dumped.contains(PlainFlag.name),
          dumped.contains("plain-value"),
          dumped.contains(SecretFlag.name),
          dumped.contains("<secret>"),
          !dumped.contains("hunter2")
        )
      }
    ),
    suite("Flag.nearMissWarnings")(
      test("detects case-insensitive match in system properties") {
        val propName = "zio.blocks.config.test.NearMissFlag"
        System.setProperty(propName, "42")
        try {
          val warnings = Flag.nearMissWarnings("zio.blocks.config.test.nearmissflag")
          assertTrue(
            warnings.length == 1,
            warnings.head.contains("Near-miss")
          )
        } finally {
          System.clearProperty(propName)
        }
      },
      test("detects case-insensitive match in environment variables") {
        val maybeEnvVar =
          System.getenv().keySet().toArray(new Array[String](0)).find(name => name != name.toUpperCase)

        maybeEnvVar match {
          case Some(envVar) =>
            val flagName = envVar.toLowerCase.replace('_', '.')
            val warnings = Flag.nearMissWarnings(flagName)
            assertTrue(
              warnings.exists(
                _.contains(s"environment variable '$envVar' looks similar to flag '$flagName'")
              )
            )
          case None =>
            assertTrue(true)
        }
      },
      test("detects near miss in registered FlagSource") {
        FlagSource.Registry.clear()
        FlagSource.Registry.register(
          FlagSource.fromMap(Map("ZIO_BLOCKS_CONFIG_TEST_SOURCE_FLAG" -> "42"), id = "test-source")
        )
        try {
          val warnings = Flag.nearMissWarnings("zio.blocks.config.test.source.flag")
          assertTrue(
            warnings.exists(
              _.contains(
                "FlagSource 'test-source' contains key 'ZIO_BLOCKS_CONFIG_TEST_SOURCE_FLAG' similar to flag 'zio.blocks.config.test.source.flag'"
              )
            )
          )
        } finally {
          FlagSource.Registry.clear()
        }
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
  ) @@ TestAspect.sequential
}
