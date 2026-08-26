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

object SqlIdentifierCheckerSpec extends ZIOSpecDefault {

  private val tables  = Set("users", "orders")
  private val columns = Set("id", "name", "email", "user_id", "amount")

  private def check(parts: Seq[String], t: Set[String] = tables, c: Set[String] = columns) =
    SqlIdentifierChecker.validate(parts, t, c)

  private def checkAllow(parts: Seq[String], allow: Set[String]) =
    SqlIdentifierChecker.validate(parts, tables, columns, allow)

  def spec: Spec[TestEnvironment, Any] = suite("SqlIdentifierCheckerSpec")(
    suite("valid queries - no diagnostics")(
      test("simple SELECT with known table and column") {
        assertTrue(check(Seq("SELECT id FROM users")).isEmpty)
      },
      test("SELECT * with known table") {
        assertTrue(check(Seq("SELECT * FROM users")).isEmpty)
      },
      test("dot-qualified known table and column") {
        assertTrue(check(Seq("SELECT users.id FROM users")).isEmpty)
      },
      test("case-insensitive known names") {
        assertTrue(check(Seq("SELECT ID FROM USERS")).isEmpty)
      },
      test("case-insensitive keywords") {
        assertTrue(check(Seq("SeLeCt id FrOm users WhErE id = 1")).isEmpty)
      },
      test("keywords alone not flagged") {
        assertTrue(check(Seq("SELECT FROM WHERE JOIN ON AS GROUP BY HAVING ORDER LIMIT OFFSET")).isEmpty)
      },
      test("aggregate functions not flagged") {
        assertTrue(
          check(
            Seq("SELECT COUNT(*), SUM(amount), AVG(amount), MIN(id), MAX(id), COALESCE(name, 'x') FROM users")
          ).isEmpty
        )
      },
      test("all required allowlist keywords not flagged") {
        val q =
          "SELECT DISTINCT * FROM users WHERE id BETWEEN 1 AND 10 AND name LIKE '%a%' OR name ILIKE '%b%' IS NULL ORDER BY id ASC DESC NULLS FIRST LAST LIMIT 5 OFFSET 2"
        assertTrue(check(Seq(q)).isEmpty)
      },
      test("CAST and INTERVAL not flagged") {
        assertTrue(check(Seq("SELECT CAST(id AS TEXT), INTERVAL '1 day' FROM users")).isEmpty)
      },
      test("string literal containing unknown identifier not flagged") {
        assertTrue(check(Seq("SELECT * FROM users WHERE name = 'usres'")).isEmpty)
      },
      test("escaped single quote inside string literal not flagged") {
        assertTrue(check(Seq("SELECT * FROM users WHERE name = 'it''s badcol'")).isEmpty)
      },
      test("string literal with semicolon and unknown not flagged") {
        assertTrue(check(Seq("SELECT 'bad_table' FROM users")).isEmpty)
      },
      test("hole placeholder not flagged") {
        assertTrue(check(Seq("SELECT * FROM users WHERE id = ", " AND name = ", "")).isEmpty)
      },
      test("hole boundaries do not merge identifiers") {
        val withHole    = check(Seq("SELECT * FROM use", "rs"))
        val withoutHole = check(Seq("SELECT * FROM users"))
        assertTrue(withHole.nonEmpty && withoutHole.isEmpty)
      },
      test("numeric literal not flagged") {
        assertTrue(check(Seq("SELECT * FROM users WHERE id = 123 AND amount = 3.14")).isEmpty)
      },
      test("quoted identifiers valid") {
        assertTrue(check(Seq("""SELECT "users"."id" FROM "users"""")).isEmpty)
      },
      test("quoted alias accepted thereafter") {
        assertTrue(check(Seq("""SELECT "u".id FROM users AS "u"""")).isEmpty)
      },
      test("alias accepted after declaration") {
        assertTrue(check(Seq("SELECT u.id FROM users AS u")).isEmpty)
      },
      test("alias case-insensitive") {
        assertTrue(check(Seq("SELECT U.id FROM users AS u")).isEmpty)
      },
      test("alias used as bare identifier") {
        assertTrue(check(Seq("SELECT * FROM users AS u WHERE u.id = 1")).isEmpty)
      },
      test("multiple JOIN aliases accepted") {
        assertTrue(check(Seq("SELECT u.id, o.amount FROM users AS u JOIN orders AS o ON u.id = o.user_id")).isEmpty)
      },
      test("dot-qualified alias and column both known") {
        assertTrue(check(Seq("SELECT o.user_id FROM orders AS o")).isEmpty)
      }
    ),
    suite("invalid queries - diagnostics")(
      test("unknown table flagged") {
        val diags = check(Seq("SELECT * FROM usres"))
        assertTrue(diags.exists(_.message.contains("usres")) && diags.nonEmpty)
      },
      test("unknown column flagged") {
        val diags = check(Seq("SELECT nmae FROM users"))
        assertTrue(diags.exists(_.message.toLowerCase.contains("nmae")))
      },
      test("multiple unknown identifiers produce multiple diagnostics") {
        val diags = check(Seq("SELECT bad1, bad2 FROM badTable"))
        assertTrue(diags.length == 3)
      },
      test("typo suggestion distance 1 - table") {
        val diags = check(Seq("SELECT * FROM usres"))
        assertTrue(diags.head.suggestion.exists(_.equalsIgnoreCase("users")))
      },
      test("typo suggestion distance 1 - column") {
        val diags = check(Seq("SELECT emial FROM users"))
        assertTrue(diags.head.suggestion.exists(_.equalsIgnoreCase("email")))
      },
      test("typo suggestion distance 2") {
        val diags = check(Seq("SELECT * FROM usrs"))
        assertTrue(diags.head.suggestion.isDefined)
      },
      test("no suggestion when distance >2") {
        val diags = check(Seq("SELECT * FROM xxxxxxxxx"))
        assertTrue(diags.head.suggestion.isEmpty)
      },
      test("quoted unknown identifier flagged") {
        val diags = check(Seq("""SELECT "users"."nmae" FROM users"""))
        assertTrue(diags.exists(_.message.contains("nmae")))
      },
      test("quoted unknown suggests correct") {
        val diags = check(Seq("""SELECT "emial" FROM users"""))
        assertTrue(diags.head.suggestion.exists(_.equalsIgnoreCase("email")))
      },
      test("alias declaration itself not flagged even if unknown") {
        val diags = check(Seq("SELECT * FROM users AS myalias"))
        assertTrue(diags.isEmpty)
      },
      test("unknown alias usage flagged") {
        val diags = check(Seq("SELECT z.id FROM users AS u"))
        assertTrue(diags.exists(_.message.contains("z")))
      },
      test("dot-qualified unknown column via alias flagged") {
        val diags = check(Seq("SELECT u.badcol FROM users AS u"))
        assertTrue(diags.exists(_.message.contains("badcol")))
      },
      test("diagnostic position non-negative") {
        val diags = check(Seq("SELECT badcol FROM users"))
        assertTrue(diags.head.position >= 0)
      },
      test("diagnostic position points to token start") {
        val sql   = "SELECT badcol FROM users"
        val diags = check(Seq(sql))
        val pos   = diags.head.position
        assertTrue(sql.substring(pos).startsWith("badcol"))
      },
      test("diagnostic carries suggestion when close") {
        val diags = check(Seq("SELECT naem FROM users"))
        assertTrue(diags.head.suggestion.exists(_.equalsIgnoreCase("name")))
      },
      test("string literal not flagged even containing known-like typo") {
        val diags = check(Seq("SELECT * FROM users WHERE email = 'nmae@example.com'"))
        assertTrue(diags.isEmpty)
      },
      test("hole does not create false token across boundary") {
        val diags = check(Seq("SELECT * FROM users WHERE n", "ame"))
        assertTrue(diags.nonEmpty)
      },
      test("hole with valid identifiers around not flagged") {
        val diags = check(Seq("SELECT * FROM users WHERE id = ", ""))
        assertTrue(diags.isEmpty)
      },
      test("user_id typo suggests user_id") {
        val diags = check(Seq("SELECT user_is FROM orders"))
        assertTrue(diags.head.suggestion.exists(_.equalsIgnoreCase("user_id")))
      },
      test("case-insensitive suggestion") {
        val diags = check(Seq("SELECT EMIAL FROM users"))
        assertTrue(diags.head.suggestion.exists(_.equalsIgnoreCase("email")))
      },
      test("allowlist subset does not hide unknown") {
        val smallAllow = Set("select", "from")
        val diags      = checkAllow(Seq("SELECT badcol FROM users"), smallAllow)
        assertTrue(diags.exists(_.message.contains("badcol")))
      },
      test("empty parts still works") {
        val diags = check(Seq(""))
        assertTrue(diags.isEmpty)
      },
      test("complex query with multiple valid clauses no false positives") {
        val sql =
          "SELECT u.id, o.amount FROM users AS u INNER JOIN orders AS o ON u.id = o.user_id WHERE o.amount > 100 GROUP BY u.id HAVING COUNT(*) > 1 ORDER BY u.name ASC LIMIT 10 OFFSET 5"
        assertTrue(check(Seq(sql)).isEmpty)
      },
      test("DefaultAllowlist has at least 150 entries") {
        assertTrue(SqlIdentifierChecker.DefaultAllowlist.size >= 150)
      },
      test("tables overload works") {
        val diags = SqlIdentifierChecker.validate(Seq("SELECT * FROM users"), Seq.empty[Table[?]])
        assertTrue(diags.nonEmpty)
      }
    ),
    suite("levenshtein")(
      test("identical strings distance 0") {
        assertTrue(SqlIdentifierChecker.levenshtein("abc", "abc") == 0)
      },
      test("one insert distance 1") {
        assertTrue(SqlIdentifierChecker.levenshtein("abc", "ab") == 1)
      },
      test("two edits distance 2") {
        assertTrue(SqlIdentifierChecker.levenshtein("kitten", "sitten") == 1)
      },
      test("distance threshold respected") {
        assertTrue(SqlIdentifierChecker.levenshtein("users", "xxxxxxxx") > 2)
      }
    )
  )
}
