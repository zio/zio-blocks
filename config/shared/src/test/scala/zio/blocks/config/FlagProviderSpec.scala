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

object FlagProviderSpec extends ConfigBaseSpec {

  def spec = suite("FlagProviderSpec")(
    suite("fromMap")(
      test("resolves existing key") {
        val provider = FlagProvider.fromMap(Map("my.flag" -> "hello"))
        assertTrue(provider.resolve("my.flag") == Some("hello"))
      },
      test("returns None for missing key") {
        val provider = FlagProvider.fromMap(Map("a" -> "1"))
        assertTrue(provider.resolve("b") == None)
      },
      test("uses custom provider id") {
        val provider = FlagProvider.fromMap(Map.empty, id = "test-provider")
        assertTrue(provider.providerId == "test-provider")
      }
    ),
    suite("orElse composition")(
      test("primary wins when both have value") {
        val p1       = FlagProvider.fromMap(Map("k" -> "primary"), id = "p1")
        val p2       = FlagProvider.fromMap(Map("k" -> "fallback"), id = "p2")
        val composed = p1.orElse(p2)
        assertTrue(composed.resolve("k") == Some("primary"))
      },
      test("falls back when primary returns None") {
        val p1       = FlagProvider.fromMap(Map.empty, id = "p1")
        val p2       = FlagProvider.fromMap(Map("k" -> "fallback"), id = "p2")
        val composed = p1.orElse(p2)
        assertTrue(composed.resolve("k") == Some("fallback"))
      },
      test("returns None when both return None") {
        val p1       = FlagProvider.fromMap(Map.empty, id = "p1")
        val p2       = FlagProvider.fromMap(Map.empty, id = "p2")
        val composed = p1.orElse(p2)
        assertTrue(composed.resolve("k") == None)
      },
      test("composed provider id reflects both") {
        val p1       = FlagProvider.fromMap(Map.empty, id = "p1")
        val p2       = FlagProvider.fromMap(Map.empty, id = "p2")
        val composed = p1.orElse(p2)
        assertTrue(composed.providerId == "p1|p2")
      }
    ),
    suite("Registry")(
      test("register and resolve") {
        FlagProvider.Registry.clear()
        val provider = FlagProvider.fromMap(Map("flag" -> "value"), id = "reg-test")
        FlagProvider.Registry.register(provider)
        val result = FlagProvider.Registry.resolve("flag")
        FlagProvider.Registry.clear()
        assertTrue(result == Some(("value", "reg-test")))
      },
      test("unregister removes provider") {
        FlagProvider.Registry.clear()
        val provider = FlagProvider.fromMap(Map("flag" -> "value"), id = "reg-test-2")
        FlagProvider.Registry.register(provider)
        FlagProvider.Registry.unregister("reg-test-2")
        val result = FlagProvider.Registry.resolve("flag")
        FlagProvider.Registry.clear()
        assertTrue(result == None)
      },
      test("all returns registered providers") {
        FlagProvider.Registry.clear()
        val p1 = FlagProvider.fromMap(Map.empty, id = "all-1")
        val p2 = FlagProvider.fromMap(Map.empty, id = "all-2")
        FlagProvider.Registry.register(p1)
        FlagProvider.Registry.register(p2)
        val ids = FlagProvider.Registry.all.map(_.providerId).toSet
        FlagProvider.Registry.clear()
        assertTrue(ids == Set("all-1", "all-2"))
      },
      test("resolve returns None when no provider has the flag") {
        FlagProvider.Registry.clear()
        val provider = FlagProvider.fromMap(Map("other" -> "value"), id = "miss")
        FlagProvider.Registry.register(provider)
        val result = FlagProvider.Registry.resolve("nonexistent")
        FlagProvider.Registry.clear()
        assertTrue(result == None)
      }
    ),
    suite("contextual resolve")(
      test("default contextual resolve delegates to simple resolve") {
        val provider = FlagProvider.fromMap(Map("k" -> "v"), id = "ctx-test")
        assertTrue(provider.resolve("k", "path", Map.empty) == Some("v"))
      }
    )
  )
}
