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

object StaticFlagSpec extends ConfigBaseSpec {

  object DefaultIntFlag extends StaticFlag[Int](42)

  object DefaultStringFlag extends StaticFlag[String]("hello")

  object DefaultBoolFlag extends StaticFlag[Boolean](true)

  def spec = suite("StaticFlagSpec")(
    suite("name derivation")(
      test("derives dot-separated name from object class") {
        assertTrue(DefaultIntFlag.name == "zio.blocks.config.StaticFlagSpec.DefaultIntFlag")
      },
      test("string flag also derives name correctly") {
        assertTrue(DefaultStringFlag.name == "zio.blocks.config.StaticFlagSpec.DefaultStringFlag")
      }
    ),
    suite("default resolution")(
      test("uses default value when no other source provides a value") {
        assertTrue(DefaultIntFlag.value == 42)
      },
      test("apply() returns same as value") {
        assertTrue(DefaultIntFlag() == 42)
      },
      test("source is Default") {
        assertTrue(DefaultIntFlag.source == Flag.Source.Default)
      },
      test("provenance is Default") {
        assertTrue(DefaultIntFlag.provenance == Provenance.Default)
      },
      test("string flag default resolution") {
        assertTrue(DefaultStringFlag.value == "hello")
      },
      test("boolean flag default resolution") {
        assertTrue(DefaultBoolFlag.value == true)
      }
    ),
    suite("system property resolution")(
      test("resolves from system property when set") {
        val flagName = "test.sysprop.flag"
        val envName  = "TEST_SYSPROP_FLAG"
        System.setProperty(flagName, "99")
        try {
          val (value, source, prov) =
            StaticFlag.resolve[Int](flagName, envName, 0, Flag.Reader.intReader)
          assertTrue(value == 99) &&
          assertTrue(source == Flag.Source.SystemProperty) &&
          assertTrue(prov match {
            case Provenance.Resolved("sysprop", _, Some("99")) => true
            case _                                             => false
          })
        } finally {
          System.clearProperty(flagName)
        }
      }
    ),
    suite("FlagProvider resolution")(
      test("FlagProvider takes priority over system property") {
        val flagName = "test.provider.flag"
        val envName  = "TEST_PROVIDER_FLAG"
        System.setProperty(flagName, "from-sysprop")
        FlagProvider.Registry.clear()
        FlagProvider.Registry.register(FlagProvider.fromMap(Map(flagName -> "from-provider"), id = "test"))
        try {
          val (value, source, _) =
            StaticFlag.resolve[String](flagName, envName, "default", Flag.Reader.stringReader)
          assertTrue(value == "from-provider") &&
          assertTrue(source == Flag.Source.FlagProviderSource("test"))
        } finally {
          System.clearProperty(flagName)
          FlagProvider.Registry.clear()
        }
      }
    ),
    suite("self-registration")(
      test("flag is registered in Flag.registry") {
        assertTrue(Flag.registry.containsKey(DefaultIntFlag.name))
      },
      test("registered flag is the same instance") {
        assertTrue(Flag.registry.get(DefaultIntFlag.name).asInstanceOf[AnyRef] eq DefaultIntFlag.asInstanceOf[AnyRef])
      }
    ),
    suite("fail-fast")(
      test("throws on unparseable system property") {
        val flagName = "test.badparse.flag"
        val envName  = "TEST_BADPARSE_FLAG"
        System.setProperty(flagName, "not-a-number")
        try {
          val threw = try {
            StaticFlag.resolve[Int](flagName, envName, 0, Flag.Reader.intReader)
            false
          } catch {
            case _: Throwable => true
          }
          assertTrue(threw)
        } finally {
          System.clearProperty(flagName)
        }
      }
    ),
    suite("validation")(
      test("rejects non-object usage via class name check") {
        val result = scala.util.Try {
          StaticFlag.deriveName(classOf[String])
        }
        assertTrue(result.isFailure) &&
        assertTrue(result.failed.get.getMessage.contains("Scala object"))
      }
    )
  )
}
