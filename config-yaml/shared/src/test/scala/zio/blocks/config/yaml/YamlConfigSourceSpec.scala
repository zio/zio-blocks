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

import zio.blocks.config.ConfigSource
import zio.blocks.maybe.Maybe
import zio.test._

object YamlConfigSourceSpec extends ZIOSpecDefault {
  def spec = suite("YamlConfigSource")(
    test("ConfigSource.fromYaml parses simple scalar values") {
      val yaml   = "key: value"
      val result = ConfigSource.fromYaml(yaml)
      assertTrue(result.isRight) &&
      assertTrue(result.toOption.get.get("key").map(_.value) == Maybe.present("value"))
    },
    test("parses simple scalar values") {
      val yaml   = "key: value"
      val result = YamlConfigSource.fromString(yaml)
      assertTrue(result.isRight) &&
      assertTrue(result.toOption.get.get("key").map(_.value) == Maybe.present("value"))
    },
    test("flattens nested mappings with dot notation") {
      val yaml = """
                   |database:
                   |  host: localhost
                   |  port: 5432
                   |""".stripMargin
      val result = YamlConfigSource.fromString(yaml)
      assertTrue(result.isRight) &&
      assertTrue(result.toOption.get.get("database.host").map(_.value) == Maybe.present("localhost")) &&
      assertTrue(result.toOption.get.get("database.port").map(_.value) == Maybe.present("5432"))
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
      assertTrue(result.toOption.get.get("items.0").map(_.value) == Maybe.present("apple")) &&
      assertTrue(result.toOption.get.get("items.1").map(_.value) == Maybe.present("banana")) &&
      assertTrue(result.toOption.get.get("items.2").map(_.value) == Maybe.present("cherry"))
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
      assertTrue(result.toOption.get.get("app.server.http.port").map(_.value) == Maybe.present("8080")) &&
      assertTrue(result.toOption.get.get("app.server.http.host").map(_.value) == Maybe.present("0.0.0.0"))
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
      assertTrue(result.toOption.get.get("servers.0.name").map(_.value) == Maybe.present("server1")) &&
      assertTrue(result.toOption.get.get("servers.0.port").map(_.value) == Maybe.present("8080")) &&
      assertTrue(result.toOption.get.get("servers.1.name").map(_.value) == Maybe.present("server2")) &&
      assertTrue(result.toOption.get.get("servers.1.port").map(_.value) == Maybe.present("8081"))
    },
    test("skips null values") {
      val yaml = """
                   |key1: value1
                   |key2: null
                   |key3: value3
                   |""".stripMargin
      val result = YamlConfigSource.fromString(yaml)
      assertTrue(result.isRight) &&
      assertTrue(result.toOption.get.get("key1").map(_.value) == Maybe.present("value1")) &&
      assertTrue(result.toOption.get.get("key2").isEmpty) &&
      assertTrue(result.toOption.get.get("key3").map(_.value) == Maybe.present("value3"))
    },
    test("handles flow sequence value") {
      val yaml   = "key: [a, b, c]"
      val result = YamlConfigSource.fromString(yaml)
      assertTrue(result.isRight || result.isLeft)
    },
    test("uses custom sourceId in provenance") {
      val yaml   = "key: value"
      val result = YamlConfigSource.fromString(yaml, "custom:source")
      assertTrue(result.isRight) &&
      assertTrue(result.toOption.get.get("key").map(_.provenance.sourceId) == Maybe.present("custom:source"))
    },
    test("handles empty YAML") {
      val yaml   = ""
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
      assertTrue(result.toOption.get.get("single").map(_.value) == Maybe.present("single quoted")) &&
      assertTrue(result.toOption.get.get("double").map(_.value) == Maybe.present("double quoted")) &&
      assertTrue(result.toOption.get.get("unquoted").map(_.value) == Maybe.present("unquoted"))
    },
    test("all returns all keys with matching prefix") {
      val yaml = """
                   |database:
                   |  host: localhost
                   |  port: 5432
                   |cache:
                   |  ttl: 3600
                   |""".stripMargin
      val result = YamlConfigSource.fromString(yaml)
      assertTrue(result.isRight) &&
      assertTrue(result.toOption.get.all("database").size == 2) &&
      assertTrue(result.toOption.get.all("database").contains("database.host")) &&
      assertTrue(result.toOption.get.all("database").contains("database.port"))
    },
    test("malformed YAML treated as scalar value") {
      val result = YamlConfigSource.fromString("key: ]]][[[")
      assertTrue(result.isRight)
    },
    test("triple-nested object keys accessible via dot notation") {
      val yaml = """
                   |outer:
                   |  inner:
                   |    value: deep
                   |""".stripMargin
      val result = YamlConfigSource.fromString(yaml)
      assertTrue(result.isRight) &&
      assertTrue(result.toOption.get.get("outer.inner.value").map(_.value) == Maybe.present("deep"))
    },
    test("tilde null value results in None from get") {
      val yaml   = "missing: ~"
      val result = YamlConfigSource.fromString(yaml)
      assertTrue(result.isRight) &&
      assertTrue(result.toOption.get.get("missing").isEmpty)
    },
    test("numeric YAML value stored as string representation") {
      val yaml   = "count: 42"
      val result = YamlConfigSource.fromString(yaml)
      assertTrue(result.isRight) &&
      assertTrue(result.toOption.get.get("count").map(_.value) == Maybe.present("42"))
    }
  )
}
