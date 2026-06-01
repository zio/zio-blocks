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
import zio.blocks.schema.Schema
import java.sql.DriverManager

object FragValuesSpec extends ZIOSpecDefault {

  private val _ = Class.forName("org.sqlite.JDBC")

  case class Person(name: String, age: Int)
  object Person {
    implicit val schema: Schema[Person] = Schema.derived
  }

  private given DbCodec[Person] = Person.schema.deriving(DbCodecDeriver).derive

  def spec: Spec[TestEnvironment, Any] = suite("FragValuesSpec")(
    test("single row renders (?, ?)") {
      val frag = Frag.values(Seq(Person("a", 1)))
      assertTrue(frag.sql(SqlDialect.SQLite) == "(?, ?)")
    },
    test("three rows renders (?, ?), (?, ?), (?, ?)") {
      val frag = Frag.values(Seq(Person("a", 1), Person("b", 2), Person("c", 3)))
      assertTrue(frag.sql(SqlDialect.SQLite) == "(?, ?), (?, ?), (?, ?)")
    },
    test("param count: 3 rows x 2 columns = 6 DbValues") {
      val frag = Frag.values(Seq(Person("a", 1), Person("b", 2), Person("c", 3)))
      assertTrue(frag.queryParams.size == 6)
    },
    test("empty seq throws IllegalArgumentException") {
      assertTrue(
        scala.util.Try(Frag.values(Seq.empty[Person])).failed.toOption.exists(_.isInstanceOf[IllegalArgumentException])
      )
    },
    test("integration: insert 3 rows into SQLite and verify count") {
      val conn = DriverManager.getConnection("jdbc:sqlite::memory:")
      val tx   = new JdbcTransactor(() => conn, SqlDialect.SQLite) {
        override def connect[B](f: DbCon ?=> B): B = {
          val dbConn       = new JdbcConnection(conn)
          given con: DbCon = new DbCon {
            val connection: DbConnection = dbConn
            val dialect: SqlDialect      = SqlDialect.SQLite
            val logger: SqlLogger        = SqlLogger.noop
          }
          f
        }
      }
      tx.connect {
        Frag.literal("CREATE TABLE IF NOT EXISTS person (name TEXT NOT NULL, age INTEGER NOT NULL)").update
        val insertFrag = Frag.literal("INSERT INTO person (name, age) VALUES ") ++
          Frag.values(Seq(Person("Alice", 30), Person("Bob", 25), Person("Carol", 35)))
        insertFrag.update
        val count = Frag.literal("SELECT COUNT(*) FROM person").queryOne[Int].getOrElse(0)
        assertTrue(count == 3)
      }
    }
  )
}
