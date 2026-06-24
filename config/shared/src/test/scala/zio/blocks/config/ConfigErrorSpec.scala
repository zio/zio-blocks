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

object ConfigErrorSpec extends ConfigBaseSpec {
  def spec = suite("ConfigErrorSpec")(
    suite("MissingKey")(
      test("formats message with path and source") {
        val error = ConfigError.MissingKey("database.url", "environment")
        assertTrue(error.message == "Missing required key 'database.url' in source 'environment'")
      },
      test("getMessage equals message") {
        val error = ConfigError.MissingKey("database.url", "environment")
        assertTrue(error.getMessage == error.message)
      },
      test("is a ConfigSourceError") {
        val error = ConfigError.MissingKey("database.url", "environment")
        assertTrue(error.isInstanceOf[ConfigSourceError])
      }
    ),
    suite("InvalidValue")(
      test("formats message with path, value, type, and source") {
        val error = ConfigError.InvalidValue("port", "abc", "Int", "config.json")
        assertTrue(error.message == "Invalid value 'abc' for key 'port' (expected Int) in source 'config.json'")
      },
      test("includes cause message when present") {
        val cause = new Exception("number format error")
        val error = ConfigError.InvalidValue("port", "abc", "Int", "config.json", Some(cause))
        assertTrue(
          error.message == "Invalid value 'abc' for key 'port' (expected Int) in source 'config.json': number format error"
        )
      },
      test("getMessage equals message") {
        val error = ConfigError.InvalidValue("port", "abc", "Int", "config.json")
        assertTrue(error.getMessage == error.message)
      },
      test("is a ConfigParseError") {
        val error = ConfigError.InvalidValue("port", "abc", "Int", "config.json")
        assertTrue(error.isInstanceOf[ConfigParseError])
      }
    ),
    suite("DuplicateKey")(
      test("formats message with path and sources") {
        val error = ConfigError.DuplicateKey("api.key", Seq("env", "config.json"))
        assertTrue(error.message == "Duplicate key 'api.key' found in conflicting sources: env, config.json")
      },
      test("handles single source") {
        val error = ConfigError.DuplicateKey("api.key", Seq("env"))
        assertTrue(error.message == "Duplicate key 'api.key' found in conflicting sources: env")
      },
      test("getMessage equals message") {
        val error = ConfigError.DuplicateKey("api.key", Seq("env"))
        assertTrue(error.getMessage == error.message)
      },
      test("is a ConfigSourceError") {
        val error = ConfigError.DuplicateKey("api.key", Seq("env"))
        assertTrue(error.isInstanceOf[ConfigSourceError])
      }
    ),
    suite("Composite")(
      test("formats multiple errors with newlines") {
        val errors = new ::(
          ConfigError.MissingKey("key1", "source1"),
          List(ConfigError.MissingKey("key2", "source2"))
        )
        val composite = ConfigError.Composite(errors)
        val expected  =
          "Missing required key 'key1' in source 'source1'\nMissing required key 'key2' in source 'source2'"
        assertTrue(composite.message == expected)
      },
      test("getMessage equals message") {
        val errors    = new ::(ConfigError.MissingKey("key1", "source1"), Nil)
        val composite = ConfigError.Composite(errors)
        assertTrue(composite.getMessage == composite.message)
      }
    ),
    suite("Unauthorized")(
      test("formats message with path and source") {
        val error = ConfigError.Unauthorized("secrets.api-key", "vault")
        assertTrue(error.message == "Unauthorized access to key 'secrets.api-key' in source 'vault'")
      },
      test("message contains path") {
        val error = ConfigError.Unauthorized("secrets.api-key", "vault")
        assertTrue(error.message.contains("secrets.api-key"))
      },
      test("message contains source") {
        val error = ConfigError.Unauthorized("secrets.api-key", "vault")
        assertTrue(error.message.contains("vault"))
      },
      test("getMessage equals message") {
        val error = ConfigError.Unauthorized("secrets.api-key", "vault")
        assertTrue(error.getMessage == error.message)
      },
      test("is a ConfigSourceError") {
        val error = ConfigError.Unauthorized("secrets.api-key", "vault")
        assertTrue(error.isInstanceOf[ConfigSourceError])
      }
    ),
    suite("ParseError")(
      test("formats message with path, expected type, and source") {
        val error = ConfigError.ParseError("db.config", "config.json", "JSON object")
        assertTrue(error.message == "Parse error for key 'db.config' (expected JSON object) in source 'config.json'")
      },
      test("includes cause message when present") {
        val cause = new Exception("unexpected end of input")
        val error = ConfigError.ParseError("db.config", "config.json", "JSON object", Some(cause))
        assertTrue(
          error.message == "Parse error for key 'db.config' (expected JSON object) in source 'config.json': unexpected end of input"
        )
      },
      test("message contains path") {
        val error = ConfigError.ParseError("db.config", "config.json", "JSON object")
        assertTrue(error.message.contains("db.config"))
      },
      test("message contains source") {
        val error = ConfigError.ParseError("db.config", "config.json", "JSON object")
        assertTrue(error.message.contains("config.json"))
      },
      test("message contains expected type") {
        val error = ConfigError.ParseError("db.config", "config.json", "JSON object")
        assertTrue(error.message.contains("JSON object"))
      },
      test("getMessage equals message") {
        val error = ConfigError.ParseError("db.config", "config.json", "JSON object")
        assertTrue(error.getMessage == error.message)
      },
      test("is a ConfigParseError") {
        val error = ConfigError.ParseError("db.config", "config.json", "JSON object")
        assertTrue(error.isInstanceOf[ConfigParseError])
      }
    ),
    suite("category traits")(
      test("MissingKey is ConfigError and ConfigSourceError") {
        val e: ConfigError = ConfigError.MissingKey("k", "s")
        assertTrue(e.isInstanceOf[ConfigSourceError]) &&
        assertTrue(e.isInstanceOf[ConfigError])
      },
      test("InvalidValue is ConfigError and ConfigParseError") {
        val e: ConfigError = ConfigError.InvalidValue("k", "v", "Int", "s")
        assertTrue(e.isInstanceOf[ConfigParseError]) &&
        assertTrue(e.isInstanceOf[ConfigError])
      },
      test("DuplicateKey is ConfigError and ConfigSourceError") {
        val e: ConfigError = ConfigError.DuplicateKey("k", Seq("s1", "s2"))
        assertTrue(e.isInstanceOf[ConfigSourceError]) &&
        assertTrue(e.isInstanceOf[ConfigError])
      },
      test("Unauthorized is ConfigError and ConfigSourceError") {
        val e: ConfigError = ConfigError.Unauthorized("k", "s")
        assertTrue(e.isInstanceOf[ConfigSourceError]) &&
        assertTrue(e.isInstanceOf[ConfigError])
      },
      test("ParseError is ConfigError and ConfigParseError") {
        val e: ConfigError = ConfigError.ParseError("k", "s", "T")
        assertTrue(e.isInstanceOf[ConfigParseError]) &&
        assertTrue(e.isInstanceOf[ConfigError])
      },
      test("Composite is ConfigError only") {
        val e: ConfigError = ConfigError.Composite(new ::(ConfigError.MissingKey("k", "s"), Nil))
        assertTrue(e.isInstanceOf[ConfigError]) &&
        assertTrue(!e.isInstanceOf[ConfigSourceError]) &&
        assertTrue(!e.isInstanceOf[ConfigParseError])
      }
    ),
    suite("NoStackTrace")(
      test("ConfigError has no stack trace") {
        val error = ConfigError.MissingKey("k", "s")
        assertTrue(error.getStackTrace.length == 0)
      }
    ),
    suite("ConfigError cases")(
      test("Composite message is non-empty for single error") {
        val error     = ConfigError.MissingKey("x", "src")
        val composite = ConfigError.Composite(new ::(error, Nil))
        assertTrue(composite.message.nonEmpty)
      },
      test("ParseError message contains path, source, and expected type") {
        val error = ConfigError.ParseError("db.url", "vault", "JDBC URL")
        assertTrue(
          error.message.contains("db.url") &&
            error.message.contains("vault") &&
            error.message.contains("JDBC URL")
        )
      }
    )
  )
}
