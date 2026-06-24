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

package zio.blocks.config.json

import zio.blocks.config.ConfigSource
import zio.test._

object JsonConfigSourceSpec extends ZIOSpecDefault {
  def spec = suite("JsonConfigSource")(
    test("ConfigSource.fromJson parses simple flat JSON object") {
      val json   = """{"key": "value", "number": 42}"""
      val result = ConfigSource.fromJson(json)
      assertTrue(result.isRight) &&
      assertTrue(result.toOption.get.get("key").isDefined) &&
      assertTrue(result.toOption.get.get("key").get.value == "value")
    },
    test("parses simple flat JSON object") {
      val json   = """{"key": "value", "number": 42}"""
      val result = JsonConfigSource.fromString(json)
      assertTrue(result.isRight) &&
      assertTrue(result.toOption.get.get("key").isDefined) &&
      assertTrue(result.toOption.get.get("key").get.value == "value") &&
      assertTrue(result.toOption.get.get("number").isDefined) &&
      assertTrue(result.toOption.get.get("number").get.value == "42")
    },
    test("flattens nested JSON objects with dot-separated keys") {
      val json   = """{"db": {"host": "localhost", "port": 5432}}"""
      val result = JsonConfigSource.fromString(json)
      assertTrue(result.isRight) &&
      assertTrue(result.toOption.get.get("db.host").isDefined) &&
      assertTrue(result.toOption.get.get("db.host").get.value == "localhost") &&
      assertTrue(result.toOption.get.get("db.port").isDefined) &&
      assertTrue(result.toOption.get.get("db.port").get.value == "5432")
    },
    test("handles arrays with indexed keys") {
      val json   = """{"servers": ["server1", "server2", "server3"]}"""
      val result = JsonConfigSource.fromString(json)
      assertTrue(result.isRight) &&
      assertTrue(result.toOption.get.get("servers.0").isDefined) &&
      assertTrue(result.toOption.get.get("servers.0").get.value == "server1") &&
      assertTrue(result.toOption.get.get("servers.1").isDefined) &&
      assertTrue(result.toOption.get.get("servers.1").get.value == "server2") &&
      assertTrue(result.toOption.get.get("servers.2").isDefined) &&
      assertTrue(result.toOption.get.get("servers.2").get.value == "server3")
    },
    test("handles nested objects within arrays") {
      val json   = """{"items": [{"id": 1, "name": "item1"}, {"id": 2, "name": "item2"}]}"""
      val result = JsonConfigSource.fromString(json)
      assertTrue(result.isRight) &&
      assertTrue(result.toOption.get.get("items.0.id").isDefined) &&
      assertTrue(result.toOption.get.get("items.0.id").get.value == "1") &&
      assertTrue(result.toOption.get.get("items.0.name").isDefined) &&
      assertTrue(result.toOption.get.get("items.0.name").get.value == "item1") &&
      assertTrue(result.toOption.get.get("items.1.id").isDefined) &&
      assertTrue(result.toOption.get.get("items.1.id").get.value == "2") &&
      assertTrue(result.toOption.get.get("items.1.name").isDefined) &&
      assertTrue(result.toOption.get.get("items.1.name").get.value == "item2")
    },
    test("handles boolean values") {
      val json   = """{"enabled": true, "disabled": false}"""
      val result = JsonConfigSource.fromString(json)
      assertTrue(result.isRight) &&
      assertTrue(result.toOption.get.get("enabled").isDefined) &&
      assertTrue(result.toOption.get.get("enabled").get.value == "true") &&
      assertTrue(result.toOption.get.get("disabled").isDefined) &&
      assertTrue(result.toOption.get.get("disabled").get.value == "false")
    },
    test("skips null values") {
      val json   = """{"key": "value", "nullKey": null}"""
      val result = JsonConfigSource.fromString(json)
      assertTrue(result.isRight) &&
      assertTrue(result.toOption.get.get("key").isDefined) &&
      assertTrue(result.toOption.get.get("nullKey").isEmpty)
    },
    test("handles numeric values as strings") {
      val json   = """{"int": 42, "float": 3.14, "negative": -10}"""
      val result = JsonConfigSource.fromString(json)
      assertTrue(result.isRight) &&
      assertTrue(result.toOption.get.get("int").isDefined) &&
      assertTrue(result.toOption.get.get("int").get.value == "42") &&
      assertTrue(result.toOption.get.get("float").isDefined) &&
      assertTrue(result.toOption.get.get("float").get.value == "3.14") &&
      assertTrue(result.toOption.get.get("negative").isDefined) &&
      assertTrue(result.toOption.get.get("negative").get.value == "-10")
    },
    test("returns error on invalid JSON") {
      val json   = """{"invalid": json}"""
      val result = JsonConfigSource.fromString(json)
      assertTrue(result.isLeft)
    },
    test("tracks provenance with sourceId") {
      val json     = """{"key": "value"}"""
      val sourceId = "custom:source"
      val result   = JsonConfigSource.fromString(json, sourceId)
      assertTrue(result.isRight) &&
      assertTrue(result.toOption.get.get("key").isDefined) &&
      assertTrue(result.toOption.get.get("key").get.provenance.sourceId == sourceId)
    },
    test("uses default sourceId when not provided") {
      val json   = """{"key": "value"}"""
      val result = JsonConfigSource.fromString(json)
      assertTrue(result.isRight) &&
      assertTrue(result.toOption.get.get("key").isDefined) &&
      assertTrue(result.toOption.get.get("key").get.provenance.sourceId == "json:string")
    },
    test("handles deeply nested structures") {
      val json   = """{"a": {"b": {"c": {"d": "value"}}}}"""
      val result = JsonConfigSource.fromString(json)
      assertTrue(result.isRight) &&
      assertTrue(result.toOption.get.get("a.b.c.d").isDefined) &&
      assertTrue(result.toOption.get.get("a.b.c.d").get.value == "value")
    },
    test("handles empty objects and arrays") {
      val json   = """{"empty_obj": {}, "empty_arr": []}"""
      val result = JsonConfigSource.fromString(json)
      assertTrue(result.isRight) &&
      assertTrue(result.toOption.get.get("empty_obj").isEmpty) &&
      assertTrue(result.toOption.get.get("empty_arr").isEmpty)
    },
    test("all returns all keys with matching prefix") {
      val json   = """{"db": {"host": "localhost", "port": 5432}, "app": {"name": "myapp"}}"""
      val result = JsonConfigSource.fromString(json)
      assertTrue(result.isRight) &&
      assertTrue(result.toOption.get.all("db").size == 2) &&
      assertTrue(result.toOption.get.all("db").contains("db.host")) &&
      assertTrue(result.toOption.get.all("db").contains("db.port"))
    },
    test("handles string values with special characters") {
      val json   = """{"path": "/home/user/file.txt", "url": "https://example.com?key=value"}"""
      val result = JsonConfigSource.fromString(json)
      assertTrue(result.isRight) &&
      assertTrue(result.toOption.get.get("path").isDefined) &&
      assertTrue(result.toOption.get.get("path").get.value == "/home/user/file.txt") &&
      assertTrue(result.toOption.get.get("url").isDefined) &&
      assertTrue(result.toOption.get.get("url").get.value == "https://example.com?key=value")
    }
  )
}
