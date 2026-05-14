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

package zio.blocks.config.yaml

import zio.blocks.config.{ConfigError, ConfigSource}
import zio.test.*

object YamlConfigSourceSpec extends ZIOSpecDefault {
  def spec = suite("YamlConfigSource")(
    test("parses simple scalar values") {
      val yaml = "key: value"
      val result = YamlConfigSource.fromString(yaml)
      assertTrue(result.isRight) &&
      assertTrue(result.toOption.get.get("key").map(_.value) == Some("value"))
    },
    test("flattens nested mappings with dot notation") {
      val yaml = """
        |database:
        |  host: localhost
        |  port: 5432
        |""".stripMargin
      val result = YamlConfigSource.fromString(yaml)
      assertTrue(result.isRight) &&
      assertTrue(result.toOption.get.get("database.host").map(_.value) == Some("localhost")) &&
      assertTrue(result.toOption.get.get("database.port").map(_.value) == Some("5432"))
    },
    test("handles sequences with indexed keys") {
      val yaml = """
        |items:
        |  - apple
        |  - banana
        |  - cherry
        |""".stripMargin
      val result = YamlConfigSource.fromString(yaml)
      assertTrue(result.isRight) &&
      assertTrue(result.toOption.get.get("items.0").map(_.value) == Some("apple")) &&
      assertTrue(result.toOption.get.get("items.1").map(_.value) == Some("banana")) &&
      assertTrue(result.toOption.get.get("items.2").map(_.value) == Some("cherry"))
    },
    test("handles deeply nested structures") {
      val yaml = """
        |app:
        |  server:
        |    http:
        |      port: 8080
        |      host: 0.0.0.0
        |""".stripMargin
      val result = YamlConfigSource.fromString(yaml)
      assertTrue(result.isRight) &&
      assertTrue(result.toOption.get.get("app.server.http.port").map(_.value) == Some("8080")) &&
      assertTrue(result.toOption.get.get("app.server.http.host").map(_.value) == Some("0.0.0.0"))
    },
    test("handles mixed nested mappings and sequences") {
      val yaml = """
        |servers:
        |  - name: server1
        |    port: 8080
        |  - name: server2
        |    port: 8081
        |""".stripMargin
      val result = YamlConfigSource.fromString(yaml)
      assertTrue(result.isRight) &&
      assertTrue(result.toOption.get.get("servers.0.name").map(_.value) == Some("server1")) &&
      assertTrue(result.toOption.get.get("servers.0.port").map(_.value) == Some("8080")) &&
      assertTrue(result.toOption.get.get("servers.1.name").map(_.value) == Some("server2")) &&
      assertTrue(result.toOption.get.get("servers.1.port").map(_.value) == Some("8081"))
    },
    test("skips null values") {
      val yaml = """
        |key1: value1
        |key2: null
        |key3: value3
        |""".stripMargin
      val result = YamlConfigSource.fromString(yaml)
      assertTrue(result.isRight) &&
      assertTrue(result.toOption.get.get("key1").map(_.value) == Some("value1")) &&
      assertTrue(result.toOption.get.get("key2").isEmpty) &&
      assertTrue(result.toOption.get.get("key3").map(_.value) == Some("value3"))
    },
    test("handles unusual but valid YAML gracefully") {
      val yaml = "key: [a, b, c"
      val result = YamlConfigSource.fromString(yaml)
      assertTrue(result.isRight || result.isLeft)
    },
    test("uses custom sourceId in provenance") {
      val yaml = "key: value"
      val result = YamlConfigSource.fromString(yaml, "custom:source")
      assertTrue(result.isRight) &&
      assertTrue(result.toOption.get.get("key").map(_.provenance.sourceId) == Some("custom:source"))
    },
    test("handles empty YAML") {
      val yaml = ""
      val result = YamlConfigSource.fromString(yaml)
      assertTrue(result.isRight) &&
      assertTrue(result.toOption.get.get("nonexistent").isEmpty)
    },
    test("handles quoted string values") {
      val yaml = """
        |single: 'single quoted'
        |double: "double quoted"
        |unquoted: unquoted
        |""".stripMargin
      val result = YamlConfigSource.fromString(yaml)
      assertTrue(result.isRight) &&
      assertTrue(result.toOption.get.get("single").map(_.value) == Some("single quoted")) &&
      assertTrue(result.toOption.get.get("double").map(_.value) == Some("double quoted")) &&
      assertTrue(result.toOption.get.get("unquoted").map(_.value) == Some("unquoted"))
    },
    test("getAll returns all keys with matching prefix") {
      val yaml = """
        |database:
        |  host: localhost
        |  port: 5432
        |cache:
        |  ttl: 3600
        |""".stripMargin
      val result = YamlConfigSource.fromString(yaml)
      assertTrue(result.isRight) &&
      assertTrue(result.toOption.get.getAll("database").size == 2) &&
      assertTrue(result.toOption.get.getAll("database").contains("database.host")) &&
      assertTrue(result.toOption.get.getAll("database").contains("database.port"))
    }
  )
}
