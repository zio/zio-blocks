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
import zio.blocks.schema._
import java.sql.DriverManager

object RepoInsertManySpec extends ZIOSpecDefault {
  private val _ = Class.forName("org.sqlite.JDBC")

  case class Item(id: Long, name: String)
  object Item {
    implicit val schema: Schema[Item] = Schema.derived
  }

  private given DbCodec[Item] = Item.schema.deriving(DbCodecDeriver).derive

  private val itemTable = Table.derived[Item]
  private val itemRepo  = Repo(itemTable, "id", DbCodec.longCodec, (_: Item).id)

  private def withFreshDb[A](f: JdbcTransactor => A): A = {
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
      Frag
        .literal("CREATE TABLE IF NOT EXISTS item (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL)")
        .update
    }
    f(tx)
  }

  def spec: Spec[TestEnvironment, Any] = suite("RepoInsertManySpec")(
    test("insertAll returns Seq of length 3") {
      withFreshDb { tx =>
        tx.connect {
          val ids = itemRepo.insertAll(
            Seq(
              Item(1L, "Alpha"),
              Item(2L, "Beta"),
              Item(3L, "Gamma")
            )
          )
          assertTrue(ids.length == 3)
        }
      }
    },
    test("IDs are in ascending order") {
      withFreshDb { tx =>
        tx.connect {
          val ids = itemRepo.insertAll(
            Seq(
              Item(1L, "Alpha"),
              Item(2L, "Beta"),
              Item(3L, "Gamma")
            )
          )
          assertTrue(ids(0) < ids(1), ids(1) < ids(2))
        }
      }
    },
    test("findAll returns all 3 entities after insertAll") {
      withFreshDb { tx =>
        tx.connect {
          itemRepo.insertAll(
            Seq(
              Item(1L, "Alpha"),
              Item(2L, "Beta"),
              Item(3L, "Gamma")
            )
          )
          val all = itemRepo.findAll
          assertTrue(
            all.size == 3,
            all.map(_.name).toSet == Set("Alpha", "Beta", "Gamma")
          )
        }
      }
    },
    test("insertAll with empty Seq throws IllegalArgumentException") {
      withFreshDb { tx =>
        tx.connect {
          val result = scala.util.Try(itemRepo.insertAll(Seq.empty))
          assertTrue(result.failed.toOption.exists(_.isInstanceOf[IllegalArgumentException]))
        }
      }
    }
  )
}
