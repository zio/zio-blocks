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

import zio.test.*
import zio.blocks.schema._
import zio.blocks.schema.Maybe

object TableSpec extends ZIOSpecDefault {

  case class SimpleRecord(name: String, age: Int)
  object SimpleRecord {
    implicit val schema: Schema[SimpleRecord] = Schema.derived
  }

  case class UserProfile(firstName: String, lastName: String)
  object UserProfile {
    implicit val schema: Schema[UserProfile] = Schema.derived
  }

  case class Category(name: String)
  object Category {
    implicit val schema: Schema[Category] = Schema.derived
  }

  case class Address(street: String, city: String)
  object Address {
    implicit val schema: Schema[Address] = Schema.derived
  }

  case class Box(width: Int)
  object Box {
    implicit val schema: Schema[Box] = Schema.derived
  }

  case class OptionalRecord(id: Int, nickname: Option[String], alias: Maybe[String])
  object OptionalRecord {
    implicit val schema: Schema[OptionalRecord] = Schema.derived
  }

  def spec = suite("TableSpec")(
    suite("Table.derived")(
      test("derives simple_record from SimpleRecord") {
        val table = Table.derived[SimpleRecord]
        assertTrue(table.name == "simple_record")
      },
      test("derives user_profile from UserProfile") {
        val table = Table.derived[UserProfile]
        assertTrue(table.name == "user_profile")
      },
      test("derives category from Category") {
        val table = Table.derived[Category]
        assertTrue(table.name == "category")
      },
      test("derives address from Address") {
        val table = Table.derived[Address]
        assertTrue(table.name == "address")
      },
      test("derives box from Box") {
        val table = Table.derived[Box]
        assertTrue(table.name == "box")
      },
      test("table.columns matches codec.columns") {
        val table = Table.derived[SimpleRecord]
        assertTrue(
          table.columns == IndexedSeq("name", "age"),
          table.columns.size == 2
        )
      },
      test("derived with explicit table name uses it directly") {
        val table = Table.derived[SimpleRecord]("my_custom_table")
        assertTrue(table.name == "my_custom_table")
      },
      test("derived with explicit name ignores type name") {
        val table = Table.derived[UserProfile]("profiles")
        assertTrue(table.name == "profiles")
      },
      test("plural naming policy pluralizes the derived name") {
        val table = Table.derived[Category](TableNamingPolicy.Plural)
        assertTrue(table.name == "categories")
      }
    ),
    suite("TableNamingPolicy")(
      test("plural naming policy pluralizes user") {
        assertTrue(TableNamingPolicy.Plural.defaultName("User") == "users")
      },
      test("plural naming policy pluralizes category") {
        assertTrue(TableNamingPolicy.Plural.defaultName("Category") == "categories")
      },
      test("plural naming policy pluralizes quiz") {
        assertTrue(TableNamingPolicy.Plural.defaultName("Quiz") == "quizzes")
      },
      test("plural naming policy pluralizes waltz") {
        assertTrue(TableNamingPolicy.Plural.defaultName("Waltz") == "waltzes")
      },
      test("singular naming policy keeps singular snake case") {
        assertTrue(TableNamingPolicy.Singular.defaultName("UserProfile") == "user_profile")
      }
    ),
    suite("Table.dropTable")(
      test("generates DROP TABLE IF EXISTS") {
        val table = Table.derived[SimpleRecord]
        val frag  = table.dropTable
        assertTrue(frag.sql(SqlDialect.PostgreSQL) == "DROP TABLE IF EXISTS simple_record")
      },
      test("works with SQLite dialect") {
        val table = Table.derived[Category]
        val frag  = table.dropTable
        assertTrue(frag.sql(SqlDialect.SQLite) == "DROP TABLE IF EXISTS category")
      }
    ),
    suite("Table.createTable")(
      test("generates CREATE TABLE IF NOT EXISTS") {
        val table = Table.derived[SimpleRecord]
        val frag  = table.createTable(SqlDialect.PostgreSQL)
        assertTrue(
          frag.sql(SqlDialect.PostgreSQL) ==
            "CREATE TABLE IF NOT EXISTS simple_record (\n  name TEXT NOT NULL,\n  age INTEGER NOT NULL\n)"
        )
      },
      test("works with PostgreSQL dialect") {
        val table = Table.derived[Category]
        val frag  = table.createTable(SqlDialect.PostgreSQL)
        assertTrue(
          frag.sql(SqlDialect.PostgreSQL) ==
            "CREATE TABLE IF NOT EXISTS category (\n  name TEXT NOT NULL\n)"
        )
      },
      test("works with SQLite dialect") {
        val table = Table.derived[Category]
        val frag  = table.createTable(SqlDialect.SQLite)
        assertTrue(
          frag.sql(SqlDialect.SQLite) ==
            "CREATE TABLE IF NOT EXISTS category (\n  name TEXT NOT NULL\n)"
        )
      },
      test("Option and Maybe columns are nullable in typed DDL") {
        val table = Table.derived[OptionalRecord]
        assertTrue(
          table.createTable(SqlDialect.PostgreSQL).sql(SqlDialect.PostgreSQL) ==
            "CREATE TABLE IF NOT EXISTS optional_record (\n  id INTEGER NOT NULL,\n  nickname TEXT,\n  alias TEXT\n)"
        )
      }
    )
  )
}
