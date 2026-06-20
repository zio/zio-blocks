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

import zio.blocks.maybe.Maybe
import zio.test._

object FlagSourceSpec extends ConfigBaseSpec {

  def spec = suite("FlagSourceSpec")(
    suite("fromMap")(
      test("exposes sourceId and get") {
        val source = FlagSource.fromMap(Map("my.flag" -> "hello"), id = "source")
        assertTrue(
          source.sourceId == "source",
          source.get("my.flag") == Maybe.present(
            SourceValue("hello", Provenance.Resolved("source", "my.flag", Maybe.present("hello")))
          )
        )
      },
      test("returns Maybe.absent for missing key") {
        val source = FlagSource.fromMap(Map("a" -> "1"))
        assertTrue(source.get("b") == Maybe.absent)
      }
    ),
    suite("orElse composition")(
      test("primary wins when both have value") {
        val p1       = FlagSource.fromMap(Map("k" -> "primary"), id = "p1")
        val p2       = FlagSource.fromMap(Map("k" -> "fallback"), id = "p2")
        val composed = p1.orElse(p2)
        assertTrue(composed.get("k").map(_.value) == Maybe.present("primary"))
      },
      test("falls back when primary returns Maybe.absent") {
        val p1       = FlagSource.fromMap(Map.empty, id = "p1")
        val p2       = FlagSource.fromMap(Map("k" -> "fallback"), id = "p2")
        val composed = p1.orElse(p2)
        assertTrue(composed.get("k").map(_.value) == Maybe.present("fallback"))
      },
      test("composed sourceId reflects both") {
        val p1       = FlagSource.fromMap(Map.empty, id = "p1")
        val p2       = FlagSource.fromMap(Map.empty, id = "p2")
        val composed = p1.orElse(p2)
        assertTrue(composed.sourceId == "p1|p2")
      }
    ),
    suite("Registry")(
      test("register and resolve") {
        FlagSource.Registry.clear()
        val source = FlagSource.fromMap(Map("flag" -> "value"), id = "reg-test")
        FlagSource.Registry.register(source)
        val result = FlagSource.Registry.resolve("flag")
        FlagSource.Registry.clear()
        assertTrue(
          result == Maybe.present(SourceValue("value", Provenance.Resolved("reg-test", "flag", Maybe.present("value"))))
        )
      },
      test("unregister removes source") {
        FlagSource.Registry.clear()
        val source = FlagSource.fromMap(Map("flag" -> "value"), id = "reg-test-2")
        FlagSource.Registry.register(source)
        FlagSource.Registry.unregister("reg-test-2")
        val result = FlagSource.Registry.resolve("flag")
        FlagSource.Registry.clear()
        assertTrue(result == Maybe.absent)
      },
      test("all returns registered sources") {
        FlagSource.Registry.clear()
        val p1 = FlagSource.fromMap(Map.empty, id = "all-1")
        val p2 = FlagSource.fromMap(Map.empty, id = "all-2")
        FlagSource.Registry.register(p1)
        FlagSource.Registry.register(p2)
        val ids = FlagSource.Registry.all.map(_.sourceId).toSet
        FlagSource.Registry.clear()
        assertTrue(ids.contains("all-1"), ids.contains("all-2"))
      },
      test("resolve returns Maybe.absent when no source has the flag") {
        FlagSource.Registry.clear()
        val source = FlagSource.fromMap(Map("other" -> "value"), id = "miss")
        FlagSource.Registry.register(source)
        val result = FlagSource.Registry.resolve("nonexistent")
        FlagSource.Registry.clear()
        assertTrue(result == Maybe.absent)
      }
    )
  ) @@ TestAspect.sequential
}
