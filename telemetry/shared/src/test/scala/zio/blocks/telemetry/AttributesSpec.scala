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

package zio.blocks.telemetry

import zio.test._

object AttributesSpec extends ZIOSpecDefault {

  def spec = suite("Attributes")(
    suite("AttributeKey")(
      test("string creates typed key with StringType") {
        val key = AttributeKey.string("service.name")
        assertTrue(
          key.name == "service.name" &&
            key.`type` == AttributeType.StringType
        )
      },
      test("boolean creates typed key with BooleanType") {
        val key = AttributeKey.boolean("debug.enabled")
        assertTrue(
          key.name == "debug.enabled" &&
            key.`type` == AttributeType.BooleanType
        )
      },
      test("long creates typed key with LongType") {
        val key = AttributeKey.long("process.pid")
        assertTrue(
          key.name == "process.pid" &&
            key.`type` == AttributeType.LongType
        )
      },
      test("double creates typed key with DoubleType") {
        val key = AttributeKey.double("http.request.size")
        assertTrue(
          key.name == "http.request.size" &&
            key.`type` == AttributeType.DoubleType
        )
      },
      test("stringSeq creates typed key with StringSeqType") {
        val key = AttributeKey.stringSeq("http.request.header.names")
        assertTrue(
          key.name == "http.request.header.names" &&
            key.`type` == AttributeType.StringSeqType
        )
      },
      test("longSeq creates typed key with LongSeqType") {
        val key = AttributeKey.longSeq("memory.usage.history")
        assertTrue(
          key.name == "memory.usage.history" &&
            key.`type` == AttributeType.LongSeqType
        )
      },
      test("doubleSeq creates typed key with DoubleSeqType") {
        val key = AttributeKey.doubleSeq("latency.percentiles")
        assertTrue(
          key.name == "latency.percentiles" &&
            key.`type` == AttributeType.DoubleSeqType
        )
      },
      test("booleanSeq creates typed key with BooleanSeqType") {
        val key = AttributeKey.booleanSeq("flags.enabled")
        assertTrue(
          key.name == "flags.enabled" &&
            key.`type` == AttributeType.BooleanSeqType
        )
      }
    ),
    suite("AttributeValue")(
      test("StringValue stores string") {
        val value = AttributeValue.StringValue("test")
        assertTrue(value.asInstanceOf[AttributeValue.StringValue].value == "test")
      },
      test("BooleanValue stores boolean") {
        val value = AttributeValue.BooleanValue(true)
        assertTrue(value.asInstanceOf[AttributeValue.BooleanValue].value == true)
      },
      test("LongValue stores long") {
        val value = AttributeValue.LongValue(42L)
        assertTrue(value.asInstanceOf[AttributeValue.LongValue].value == 42L)
      },
      test("DoubleValue stores double") {
        val value = AttributeValue.DoubleValue(3.14)
        assertTrue(value.asInstanceOf[AttributeValue.DoubleValue].value == 3.14)
      },
      test("StringSeqValue stores seq of strings") {
        val value = AttributeValue.StringSeqValue(Seq("a", "b", "c"))
        assertTrue(
          value.asInstanceOf[AttributeValue.StringSeqValue].value == Seq("a", "b", "c")
        )
      },
      test("LongSeqValue stores seq of longs") {
        val value = AttributeValue.LongSeqValue(Seq(1L, 2L, 3L))
        assertTrue(
          value.asInstanceOf[AttributeValue.LongSeqValue].value == Seq(1L, 2L, 3L)
        )
      },
      test("DoubleSeqValue stores seq of doubles") {
        val value = AttributeValue.DoubleSeqValue(Seq(1.0, 2.5, 3.14))
        assertTrue(
          value.asInstanceOf[AttributeValue.DoubleSeqValue].value == Seq(1.0, 2.5, 3.14)
        )
      },
      test("BooleanSeqValue stores seq of booleans") {
        val value = AttributeValue.BooleanSeqValue(Seq(true, false, true))
        assertTrue(
          value.asInstanceOf[AttributeValue.BooleanSeqValue].value == Seq(true, false, true)
        )
      }
    ),
    suite("Attributes.empty")(
      test("is empty") {
        assertTrue(Attributes.empty.isEmpty)
      },
      test("has size 0") {
        assertTrue(Attributes.empty.size == 0)
      },
      test("get returns None") {
        val key = AttributeKey.string("test")
        assertTrue(Attributes.empty.get(key).isEmpty)
      },
      test("toMap returns empty Map") {
        assertTrue(Attributes.empty.toMap == (Map.empty[String, AttributeValue]))
      }
    ),
    suite("Attributes.of")(
      test("creates single-attribute Attributes") {
        val key   = AttributeKey.string("service.name")
        val attrs = Attributes.of(key, "my-service")
        assertTrue(attrs.size == 1)
      },
      test("typed get retrieves value") {
        val key   = AttributeKey.string("service.name")
        val attrs = Attributes.of(key, "my-service")
        assertTrue(attrs.get(key).contains("my-service"))
      },
      test("typed get returns None for different key") {
        val key1  = AttributeKey.string("service.name")
        val key2  = AttributeKey.string("service.version")
        val attrs = Attributes.of(key1, "my-service")
        assertTrue(attrs.get(key2).isEmpty)
      },
      test("works with boolean values") {
        val key   = AttributeKey.boolean("debug.enabled")
        val attrs = Attributes.of(key, true)
        assertTrue(attrs.get(key).contains(true))
      },
      test("works with long values") {
        val key   = AttributeKey.long("process.pid")
        val attrs = Attributes.of(key, 12345L)
        assertTrue(attrs.get(key).contains(12345L))
      },
      test("works with double values") {
        val key   = AttributeKey.double("http.request.size")
        val attrs = Attributes.of(key, 1024.5)
        assertTrue(attrs.get(key).contains(1024.5))
      },
      test("works with string seq values") {
        val key   = AttributeKey.stringSeq("tags")
        val attrs = Attributes.of(key, Seq("tag1", "tag2"))
        assertTrue(attrs.get(key).contains(Seq("tag1", "tag2")))
      }
    ),
    suite("Attributes.builder")(
      test("empty builder creates empty Attributes") {
        val attrs = Attributes.builder.build
        assertTrue(attrs.isEmpty)
      },
      test("put string adds attribute") {
        val attrs = Attributes.builder
          .put("service.name", "my-service")
          .build
        assertTrue(attrs.size == 1)
      },
      test("put long adds attribute") {
        val attrs = Attributes.builder
          .put("process.pid", 12345L)
          .build
        assertTrue(attrs.size == 1)
      },
      test("put double adds attribute") {
        val attrs = Attributes.builder
          .put("latency", 123.45)
          .build
        assertTrue(attrs.size == 1)
      },
      test("put boolean adds attribute") {
        val attrs = Attributes.builder
          .put("debug", true)
          .build
        assertTrue(attrs.size == 1)
      },
      test("put typed adds attribute") {
        val key   = AttributeKey.string("service.name")
        val attrs = Attributes.builder
          .put(key, "my-service")
          .build
        assertTrue(attrs.size == 1 && attrs.get(key).contains("my-service"))
      },
      test("multiple puts accumulate") {
        val attrs = Attributes.builder
          .put("service.name", "my-service")
          .put("service.version", "1.0.0")
          .put("debug", true)
          .build
        assertTrue(attrs.size == 3)
      },
      test("later puts override earlier ones with same key") {
        val attrs = Attributes.builder
          .put("service.name", "old")
          .put("service.name", "new")
          .build
        assertTrue(attrs.size == 1)
      },
      test("typed and untyped puts can coexist") {
        val key   = AttributeKey.string("service.name")
        val attrs = Attributes.builder
          .put(key, "my-service")
          .put("other", "value")
          .build
        assertTrue(
          attrs.size == 2 &&
            attrs.get(key).contains("my-service")
        )
      }
    ),
    suite("Attributes.get")(
      test("retrieves typed value by key") {
        val key   = AttributeKey.string("service.name")
        val attrs = Attributes.of(key, "my-service")
        assertTrue(attrs.get(key).contains("my-service"))
      },
      test("returns None for missing key") {
        val key1  = AttributeKey.string("service.name")
        val key2  = AttributeKey.string("service.version")
        val attrs = Attributes.of(key1, "my-service")
        assertTrue(attrs.get(key2).isEmpty)
      },
      test("returns None for empty Attributes") {
        val key = AttributeKey.string("service.name")
        assertTrue(Attributes.empty.get(key).isEmpty)
      },
      test("handles multiple attributes correctly") {
        val key1  = AttributeKey.string("service.name")
        val key2  = AttributeKey.long("process.pid")
        val attrs = Attributes.builder
          .put(key1, "my-service")
          .put(key2, 12345L)
          .build
        assertTrue(
          attrs.get(key1).contains("my-service") &&
            attrs.get(key2).contains(12345L)
        )
      }
    ),
    suite("Attributes.size and isEmpty")(
      test("empty Attributes has size 0") {
        assertTrue(Attributes.empty.size == 0)
      },
      test("empty Attributes is empty") {
        assertTrue(Attributes.empty.isEmpty)
      },
      test("single attribute has size 1") {
        val attrs = Attributes.of(AttributeKey.string("key"), "value")
        assertTrue(attrs.size == 1 && !attrs.isEmpty)
      },
      test("three attributes have size 3") {
        val attrs = Attributes.builder
          .put("a", "1")
          .put("b", "2")
          .put("c", "3")
          .build
        assertTrue(attrs.size == 3 && !attrs.isEmpty)
      }
    ),
    suite("Attributes.foreach")(
      test("iterates over all attributes") {
        val attrs = Attributes.builder
          .put("key1", "value1")
          .put("key2", "value2")
          .build
        var count = 0
        attrs.foreach { (_, _) =>
          count += 1
        }
        assertTrue(count == 2)
      },
      test("provides correct key-value pairs") {
        val attrs              = Attributes.of(AttributeKey.string("test"), "value")
        var foundKey: String   = null
        var foundValue: String = null
        attrs.foreach { (k, v) =>
          foundKey = k
          foundValue = v.asInstanceOf[AttributeValue.StringValue].value
        }
        assertTrue(foundKey == "test" && foundValue == "value")
      },
      test("iteration works for empty Attributes") {
        var count = 0
        Attributes.empty.foreach { (_, _) =>
          count += 1
        }
        assertTrue(count == 0)
      }
    ),
    suite("Attributes.++")(
      test("merges two Attributes") {
        val attrs1 = Attributes.of(AttributeKey.string("key1"), "value1")
        val attrs2 = Attributes.of(AttributeKey.string("key2"), "value2")
        val merged = attrs1 ++ attrs2
        assertTrue(merged.size == 2)
      },
      test("right side wins on key conflict") {
        val attrs1 = Attributes.of(AttributeKey.string("key"), "old")
        val attrs2 = Attributes.of(AttributeKey.string("key"), "new")
        val merged = attrs1 ++ attrs2
        assertTrue(merged.size == 2)
        val key = AttributeKey.string("key")
        assertTrue(merged.get(key).contains("new"))
      },
      test("merging with empty left preserves right") {
        val attrs  = Attributes.of(AttributeKey.string("key"), "value")
        val merged = Attributes.empty ++ attrs
        assertTrue(merged.size == 1)
      },
      test("merging with empty right preserves left") {
        val attrs  = Attributes.of(AttributeKey.string("key"), "value")
        val merged = attrs ++ Attributes.empty
        assertTrue(merged.size == 1)
      }
    ),
    suite("Attributes.toMap")(
      test("converts to Map with string keys and AttributeValue values") {
        val attrs = Attributes.of(AttributeKey.string("test"), "value")
        val map   = attrs.toMap
        assertTrue(map.size == 1 && map.contains("test"))
      },
      test("empty Attributes converts to empty Map") {
        assertTrue(Attributes.empty.toMap == (Map.empty[String, AttributeValue]))
      },
      test("preserves all key-value pairs") {
        val attrs = Attributes.builder
          .put("a", "1")
          .put("b", "2")
          .build
        val map = attrs.toMap
        assertTrue(map.size == 2 && map.contains("a") && map.contains("b"))
      }
    ),
    suite("Attributes.predefined keys")(
      test("ServiceName is string AttributeKey") {
        val key = Attributes.ServiceName
        assertTrue(
          key.name == "service.name" &&
            key.`type` == AttributeType.StringType
        )
      },
      test("ServiceVersion is string AttributeKey") {
        val key = Attributes.ServiceVersion
        assertTrue(
          key.name == "service.version" &&
            key.`type` == AttributeType.StringType
        )
      }
    )
  )
}
