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
        assertTrue(error.message == "Invalid value 'abc' for key 'port' (expected Int) in source 'config.json': number format error")
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
      }
    ),
    suite("Composite")(
      test("formats multiple errors with newlines") {
        val errors = new ::(
          ConfigError.MissingKey("key1", "source1"),
          List(ConfigError.MissingKey("key2", "source2"))
        )
        val composite = ConfigError.Composite(errors)
        val expected = "Missing required key 'key1' in source 'source1'\nMissing required key 'key2' in source 'source2'"
        assertTrue(composite.message == expected)
      }
    )
  )
}
