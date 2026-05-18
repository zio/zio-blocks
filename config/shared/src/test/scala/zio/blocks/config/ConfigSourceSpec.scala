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

object ConfigSourceSpec extends ConfigBaseSpec {
  def spec = suite("ConfigSourceSpec")(
    sourceSuite,
    mapSourceSuite,
    orElseSuite,
    withPrefixSuite,
    withKeyMapperSuite,
    envSourceSuite,
    sysPropSourceSuite
  )

  private val sourceSuite = suite("Source")(
    test("ConfigSource exposes Source id and get") {
      val source: FlagSource = ConfigSource.fromMap(Map("db.host" -> "localhost"), "cfg")
      val result             = source.get("db.host")
      assertTrue(
        source.sourceId == "cfg",
        result.map(_.value) == Some("localhost")
      )
    }
  )

  private val mapSourceSuite = suite("MapSource")(
    test("get returns value with Resolved provenance for existing key") {
      val source = ConfigSource.fromMap(Map("db.host" -> "localhost"))
      val result = source.get("db.host")
      assertTrue(
        result.isDefined,
        result.get.value == "localhost",
        result.get.provenance == Provenance.Resolved("map", "db.host", Some("localhost"))
      )
    },
    test("get returns None for missing key") {
      val source = ConfigSource.fromMap(Map("db.host" -> "localhost"))
      assertTrue(source.get("db.port").isEmpty)
    },
    test("get returns empty string value when present") {
      val source = ConfigSource.fromMap(Map("key" -> ""))
      val result = source.get("key")
      assertTrue(
        result.isDefined,
        result.get.value == "",
        result.get.provenance == Provenance.Resolved("map", "key", Some(""))
      )
    },
    test("custom sourceId is used in provenance") {
      val source = ConfigSource.MapSource(Map("k" -> "v"), sourceId = "custom-map")
      val result = source.get("k")
      assertTrue(result.get.provenance == Provenance.Resolved("custom-map", "k", Some("v")))
    },
    test("getAll returns entries matching prefix") {
      val source = ConfigSource.fromMap(
        Map("db.host" -> "localhost", "db.port" -> "5432", "api.key" -> "secret")
      )
      val result = source.getAll("db")
      assertTrue(
        result.size == 2,
        result.contains("db.host"),
        result.contains("db.port"),
        !result.contains("api.key")
      )
    },
    test("getAll includes exact prefix match") {
      val source = ConfigSource.fromMap(Map("db" -> "value", "db.host" -> "localhost"))
      val result = source.getAll("db")
      assertTrue(
        result.size == 2,
        result.contains("db"),
        result.contains("db.host")
      )
    },
    test("getAll with empty prefix returns all entries") {
      val source = ConfigSource.fromMap(Map("a" -> "1", "b" -> "2"))
      val result = source.getAll("")
      assertTrue(result.size == 2)
    }
  )

  private val orElseSuite = suite("orElse")(
    test("returns value from primary source when present") {
      val primary  = ConfigSource.fromMap(Map("key" -> "primary"), "primary")
      val fallback = ConfigSource.fromMap(Map("key" -> "fallback"), "fallback")
      val composed = primary.orElse(fallback)
      val result   = composed.get("key")
      assertTrue(
        result.get.value == "primary",
        result.get.provenance == Provenance.Resolved("primary", "key", Some("primary"))
      )
    },
    test("returns value from fallback when primary is missing") {
      val primary  = ConfigSource.fromMap(Map.empty, "primary")
      val fallback = ConfigSource.fromMap(Map("key" -> "fallback"), "fallback")
      val composed = primary.orElse(fallback)
      val result   = composed.get("key")
      assertTrue(
        result.get.value == "fallback",
        result.get.provenance == Provenance.Resolved("fallback", "key", Some("fallback"))
      )
    },
    test("returns None when both sources are missing") {
      val primary  = ConfigSource.fromMap(Map.empty, "primary")
      val fallback = ConfigSource.fromMap(Map.empty, "fallback")
      val composed = primary.orElse(fallback)
      assertTrue(composed.get("key").isEmpty)
    },
    test("composed sourceId combines both source ids") {
      val primary  = ConfigSource.fromMap(Map.empty, "env")
      val fallback = ConfigSource.fromMap(Map.empty, "sysprop")
      val composed = primary.orElse(fallback)
      assertTrue(composed.sourceId == "env|sysprop")
    },
    test("getAll merges entries with primary taking precedence") {
      val primary  = ConfigSource.fromMap(Map("db.host" -> "primary-host"), "primary")
      val fallback = ConfigSource.fromMap(Map("db.host" -> "fallback-host", "db.port" -> "5432"), "fallback")
      val composed = primary.orElse(fallback)
      val result   = composed.getAll("db")
      assertTrue(
        result.size == 2,
        result("db.host").value == "primary-host",
        result("db.host").provenance == Provenance.Resolved("primary", "db.host", Some("primary-host")),
        result("db.port").value == "5432",
        result("db.port").provenance == Provenance.Resolved("fallback", "db.port", Some("5432"))
      )
    }
  )

  private val withPrefixSuite = suite("withPrefix")(
    test("prepends prefix to key on get") {
      val source   = ConfigSource.fromMap(Map("db.host" -> "localhost"))
      val prefixed = source.withPrefix("db")
      val result   = prefixed.get("host")
      assertTrue(
        result.isDefined,
        result.get.value == "localhost"
      )
    },
    test("preserves original sourceId") {
      val source   = ConfigSource.fromMap(Map("db.host" -> "localhost"), "my-source")
      val prefixed = source.withPrefix("db")
      assertTrue(prefixed.sourceId == "my-source")
    },
    test("returns None when prefixed key is missing") {
      val source   = ConfigSource.fromMap(Map("api.key" -> "secret"))
      val prefixed = source.withPrefix("db")
      assertTrue(prefixed.get("host").isEmpty)
    },
    test("prepends prefix to getAll") {
      val source   = ConfigSource.fromMap(Map("app.db.host" -> "localhost", "app.db.port" -> "5432", "other" -> "x"))
      val prefixed = source.withPrefix("app")
      val result   = prefixed.getAll("db")
      assertTrue(
        result.size == 2,
        result.contains("app.db.host"),
        result.contains("app.db.port")
      )
    }
  )

  private val withKeyMapperSuite = suite("withKeyMapper")(
    test("maps camelCase key to UPPER_SNAKE_CASE for lookup") {
      val source = ConfigSource.fromMap(Map("DATABASE_URL" -> "jdbc:postgres://localhost"))
      val mapped = source.withKeyMapper(KeyMapper.default, KeyFormat.UpperSnakeCase)
      val result = mapped.get("databaseUrl")
      assertTrue(
        result.isDefined,
        result.get.value == "jdbc:postgres://localhost"
      )
    },
    test("maps camelCase key to kebab-case for lookup") {
      val source = ConfigSource.fromMap(Map("api-key" -> "secret"))
      val mapped = source.withKeyMapper(KeyMapper.default, KeyFormat.KebabCase)
      val result = mapped.get("apiKey")
      assertTrue(
        result.isDefined,
        result.get.value == "secret"
      )
    },
    test("maps camelCase key to snake_case for lookup") {
      val source = ConfigSource.fromMap(Map("max_retries" -> "3"))
      val mapped = source.withKeyMapper(KeyMapper.default, KeyFormat.SnakeCase)
      val result = mapped.get("maxRetries")
      assertTrue(
        result.isDefined,
        result.get.value == "3"
      )
    },
    test("preserves sourceId") {
      val source = ConfigSource.fromMap(Map.empty, "test-source")
      val mapped = source.withKeyMapper(KeyMapper.default, KeyFormat.UpperSnakeCase)
      assertTrue(mapped.sourceId == "test-source")
    }
  )

  private val envSourceSuite = suite("EnvSource")(
    test("sourceId is env") {
      assertTrue(EnvSource.sourceId == "env")
    },
    test("reads PATH environment variable") {
      val result = EnvSource.get("path")
      if (TestPlatform.isJVM) assertTrue(result.isDefined)
      else assertTrue(true)
    },
    test("returns None for non-existent env var") {
      val result = EnvSource.get("zio.blocks.config.test.nonexistent.key.12345")
      assertTrue(result.isEmpty)
    }
  )

  private val sysPropSourceSuite = suite("SysPropSource")(
    test("sourceId is sysprop") {
      assertTrue(SysPropSource.sourceId == "sysprop")
    },
    test("reads java.version system property on JVM") {
      if (TestPlatform.isJVM) {
        val result = SysPropSource.get("java.version")
        assertTrue(result.isDefined, result.get.value.nonEmpty)
      } else assertTrue(SysPropSource.get("java.version").isEmpty)
    },
    test("returns None for non-existent system property") {
      val result = SysPropSource.get("zio.blocks.config.test.nonexistent.prop.12345")
      assertTrue(result.isEmpty)
    }
  )
}
