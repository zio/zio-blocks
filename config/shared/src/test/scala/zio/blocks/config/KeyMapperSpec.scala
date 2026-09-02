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

object KeyMapperSpec extends ConfigBaseSpec {
  def spec = suite("KeyMapperSpec")(
    suite("toCanonical")(
      test("UPPER_SNAKE_CASE to camelCase") {
        val mapper = KeyMapper.default
        assertTrue(mapper.toCanonical("DATABASE_URL") == "databaseUrl")
      },
      test("kebab-case to camelCase") {
        val mapper = KeyMapper.default
        assertTrue(mapper.toCanonical("api-key") == "apiKey")
      },
      test("camelCase identity") {
        val mapper = KeyMapper.default
        assertTrue(mapper.toCanonical("apiKey") == "apiKey")
      },
      test("single word unchanged") {
        val mapper = KeyMapper.default
        assertTrue(mapper.toCanonical("key") == "key")
      },
      test("multiple underscores") {
        val mapper = KeyMapper.default
        assertTrue(mapper.toCanonical("MY_API_KEY") == "myApiKey")
      },
      test("multiple hyphens") {
        val mapper = KeyMapper.default
        assertTrue(mapper.toCanonical("my-api-key") == "myApiKey")
      }
    ),
    suite("fromCanonical")(
      test("camelCase to CamelCase") {
        val mapper = KeyMapper.default
        assertTrue(mapper.fromCanonical("apiKey", KeyFormat.CamelCase) == "apiKey")
      },
      test("camelCase to snake_case") {
        val mapper = KeyMapper.default
        assertTrue(mapper.fromCanonical("apiKey", KeyFormat.SnakeCase) == "api_key")
      },
      test("camelCase to kebab-case") {
        val mapper = KeyMapper.default
        assertTrue(mapper.fromCanonical("apiKey", KeyFormat.KebabCase) == "api-key")
      },
      test("camelCase to UPPER_SNAKE_CASE") {
        val mapper = KeyMapper.default
        assertTrue(mapper.fromCanonical("apiKey", KeyFormat.UpperSnakeCase) == "API_KEY")
      },
      test("single word to snake_case") {
        val mapper = KeyMapper.default
        assertTrue(mapper.fromCanonical("key", KeyFormat.SnakeCase) == "key")
      },
      test("multiple camelCase words to snake_case") {
        val mapper = KeyMapper.default
        assertTrue(mapper.fromCanonical("myApiKey", KeyFormat.SnakeCase) == "my_api_key")
      },
      test("multiple camelCase words to kebab-case") {
        val mapper = KeyMapper.default
        assertTrue(mapper.fromCanonical("myApiKey", KeyFormat.KebabCase) == "my-api-key")
      },
      test("multiple camelCase words to UPPER_SNAKE_CASE") {
        val mapper = KeyMapper.default
        assertTrue(mapper.fromCanonical("myApiKey", KeyFormat.UpperSnakeCase) == "MY_API_KEY")
      }
    )
  )
}
