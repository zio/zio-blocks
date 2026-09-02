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

package zio.blocks.sql

import zio.test._

object SqlValidatorSpec extends ZIOSpecDefault {
  def spec: Spec[TestEnvironment, Any] = suite("SqlValidatorSpec")(
    suite("validate")(
      suite("valid SQL")(
        test("simple SELECT") {
          assertTrue(SqlValidator.validate(Seq("SELECT 1")).isEmpty)
        },
        test("SELECT with parameter hole") {
          assertTrue(SqlValidator.validate(Seq("SELECT * FROM t WHERE id = ", "")).isEmpty)
        },
        test("INSERT with multiple parameter holes") {
          assertTrue(SqlValidator.validate(Seq("INSERT INTO t (a, b) VALUES (", ", ", ")")).isEmpty)
        },
        test("balanced parentheses") {
          assertTrue(SqlValidator.validate(Seq("SELECT (name) FROM users")).isEmpty)
        },
        test("nested parentheses") {
          assertTrue(SqlValidator.validate(Seq("SELECT ((a + b)) FROM t")).isEmpty)
        },
        test("balanced single quotes") {
          assertTrue(SqlValidator.validate(Seq("SELECT 'name' FROM users")).isEmpty)
        },
        test("balanced double quotes") {
          assertTrue(SqlValidator.validate(Seq("SELECT \"col\" FROM users")).isEmpty)
        },
        test("SQL-style escaped single quotes") {
          assertTrue(SqlValidator.validate(Seq("SELECT 'it''s fine' FROM users")).isEmpty)
        },
        test("parentheses inside quotes are ignored") {
          assertTrue(SqlValidator.validate(Seq("SELECT '(not a paren' FROM t")).isEmpty)
        },
        test("fragment without keyword is valid") {
          assertTrue(SqlValidator.validate(Seq(" WHERE id = ", "")).isEmpty)
        },
        test("single parameter hole only") {
          assertTrue(SqlValidator.validate(Seq("", "")).isEmpty)
        }
      ),
      suite("invalid SQL")(
        test("empty SQL") {
          assertTrue(SqlValidator.validate(Seq("")).isDefined)
        },
        test("whitespace-only SQL") {
          assertTrue(SqlValidator.validate(Seq("  ")).isDefined)
        },
        test("unclosed single quote") {
          val result = SqlValidator.validate(Seq("SELECT 'name FROM users"))
          assertTrue(result.exists(_.contains("single quote")))
        },
        test("unclosed double quote") {
          val result = SqlValidator.validate(Seq("SELECT \"name FROM users"))
          assertTrue(result.exists(_.contains("double quote")))
        },
        test("unbalanced opening parenthesis") {
          val result = SqlValidator.validate(Seq("SELECT (name FROM users"))
          assertTrue(result.exists(_.contains("parenthes")))
        },
        test("unbalanced closing parenthesis") {
          val result = SqlValidator.validate(Seq("SELECT name) FROM users"))
          assertTrue(result.exists(_.contains("parenthes")))
        },
        test("unbalanced parens across parts") {
          val result = SqlValidator.validate(Seq("SELECT (name, ", " FROM users"))
          assertTrue(result.exists(_.contains("parenthes")))
        },
        test("unclosed quote across parts") {
          val result = SqlValidator.validate(Seq("SELECT 'abc", " FROM users"))
          assertTrue(result.exists(_.contains("quote")))
        }
      )
    )
  )
}
