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

  def spec = suite("TableSpec")(
    suite("Table.derived")(
      test("derives simple_record from SimpleRecord") {
        val table = Table.derived[SimpleRecord](SqlDialect.PostgreSQL)
        assertTrue(table.name == "simple_record")
      },
      test("derives user_profile from UserProfile") {
        val table = Table.derived[UserProfile](SqlDialect.PostgreSQL)
        assertTrue(table.name == "user_profile")
      },
      test("derives category from Category") {
        val table = Table.derived[Category](SqlDialect.SQLite)
        assertTrue(table.name == "category")
      },
      test("derives address from Address") {
        val table = Table.derived[Address](SqlDialect.PostgreSQL)
        assertTrue(table.name == "address")
      },
      test("derives box from Box") {
        val table = Table.derived[Box](SqlDialect.PostgreSQL)
        assertTrue(table.name == "box")
      },
      test("table.columns matches codec.columns") {
        val table = Table.derived[SimpleRecord](SqlDialect.PostgreSQL)
        assertTrue(
          table.columns == IndexedSeq("name", "age"),
          table.columns.size == 2
        )
      },
      test("derived with explicit table name uses it directly") {
        val table = Table.derived[SimpleRecord]("my_custom_table", SqlDialect.PostgreSQL)
        assertTrue(table.name == "my_custom_table")
      },
      test("derived with explicit name ignores type name") {
        val table = Table.derived[UserProfile]("profiles", SqlDialect.SQLite)
        assertTrue(table.name == "profiles")
      }
    ),
    suite("pluralize")(
      test("pluralizes user to users") {
        assertTrue(Table.pluralize("user") == "users")
      },
      test("pluralizes address to addresses") {
        assertTrue(Table.pluralize("address") == "addresses")
      },
      test("pluralizes category to categories") {
        assertTrue(Table.pluralize("category") == "categories")
      },
      test("pluralizes box to boxes") {
        assertTrue(Table.pluralize("box") == "boxes")
      },
      test("pluralizes bus to buses") {
        assertTrue(Table.pluralize("bus") == "buses")
      },
      test("pluralizes fox to foxes") {
        assertTrue(Table.pluralize("fox") == "foxes")
      },
      test("pluralizes church to churches") {
        assertTrue(Table.pluralize("church") == "churches")
      },
      test("pluralizes dish to dishes") {
        assertTrue(Table.pluralize("dish") == "dishes")
      },
      test("pluralizes quiz to quizzes") {
        assertTrue(Table.pluralize("quiz") == "quizzes")
      },
      test("pluralizes buzz to buzzes") {
        assertTrue(Table.pluralize("buzz") == "buzzes")
      },
      test("pluralizes fuzz to fuzzes") {
        assertTrue(Table.pluralize("fuzz") == "fuzzes")
      },
      test("empty string pluralizes to empty string") {
        assertTrue(Table.pluralize("") == "")
      }
    ),
    suite("Table.dropTable")(
      test("generates DROP TABLE IF EXISTS") {
        val table = Table.derived[SimpleRecord](SqlDialect.PostgreSQL)
        val frag  = table.dropTable
        assertTrue(frag.sql(SqlDialect.PostgreSQL) == "DROP TABLE IF EXISTS simple_record")
      },
      test("works with SQLite dialect") {
        val table = Table.derived[Category](SqlDialect.SQLite)
        val frag  = table.dropTable
        assertTrue(frag.sql(SqlDialect.SQLite) == "DROP TABLE IF EXISTS category")
      }
    ),
    suite("Table.createTable")(
      test("generates CREATE TABLE IF NOT EXISTS") {
        val table = Table.derived[SimpleRecord](SqlDialect.PostgreSQL)
        val frag  = table.createTable
        val sql   = frag.sql(SqlDialect.PostgreSQL)
        assertTrue(
          sql.contains("CREATE TABLE IF NOT EXISTS simple_record"),
          sql.contains("name"),
          sql.contains("age")
        )
      },
      test("works with PostgreSQL dialect") {
        val table = Table.derived[Category](SqlDialect.PostgreSQL)
        val frag  = table.createTable
        val sql   = frag.sql(SqlDialect.PostgreSQL)
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS category"))
      },
      test("works with SQLite dialect") {
        val table = Table.derived[Category](SqlDialect.SQLite)
        val frag  = table.createTable
        val sql   = frag.sql(SqlDialect.SQLite)
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS category"))
      }
    )
  )
}
