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

object RepoKeysetSpec extends ZIOSpecDefault {
  private val _ = Class.forName("org.sqlite.JDBC")

  case class User(id: Int, name: String, email: String)
  object User {
    implicit val schema: Schema[User] = Schema.derived
  }

  private val userTable              = Table.derived[User]
  private given DbCodec[User]        = User.schema.deriving(DbCodecDeriver).derive
  private given DbCodec[Int]         = implicitly[Schema[Int]].deriving(DbCodecDeriver).derive
  private val intCodec: DbCodec[Int] = summon[DbCodec[Int]]
  private val userRepo               = Repo(userTable, "id", intCodec, (_: User).id)

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
        .literal("CREATE TABLE IF NOT EXISTS user (id INTEGER NOT NULL, name TEXT NOT NULL, email TEXT NOT NULL)")
        .update
    }
    try f(tx)
    finally conn.close()
  }

  def spec: Spec[TestEnvironment, Any] = suite("RepoKeysetSpec")(
    suite("Repo.pageAfter")(
      test("boundary: cursor=last id gives empty page") {
        withFreshDb { tx =>
          tx.connect {
            (1 to 5).foreach(i => userRepo.insert(User(i, s"u$i", s"u$i@test.com")))
            val lastId = 5
            val page   = userRepo.pageAfter(lastId, 10)
            assertTrue(page.isEmpty)
          }
        }
      },
      test("stable cross-page ordering (ORDER BY id ASC, cursor excluded)") {
        withFreshDb { tx =>
          tx.connect {
            // insert out of order to ensure ordering is by SQL, not insertion order
            Seq(3, 1, 5, 2, 4).foreach(i => userRepo.insert(User(i, s"u$i", s"u$i@test.com")))
            val p1 = userRepo.pageAfter(0, 2)
            val p2 = userRepo.pageAfter(p1.last.id, 2)
            val p3 = userRepo.pageAfter(p2.last.id, 10)
            assertTrue(
              p1.map(_.id) == List(1, 2),
              p2.map(_.id) == List(3, 4),
              p3.map(_.id) == List(5)
            )
          }
        }
      },
      test("walk 100 rows in pages of 7 - each row seen exactly once") {
        withFreshDb { tx =>
          tx.connect {
            (1 to 100).foreach(i => userRepo.insert(User(i, s"user$i", s"user$i@example.com")))
            var cursor   = 0
            var seen     = List.empty[Int]
            var pages    = 0
            var continue = true
            while (continue) {
              val page = userRepo.pageAfter(cursor, 7)
              if (page.isEmpty) continue = false
              else {
                assertTrue(page.map(_.id) == page.map(_.id).sorted)
                assertTrue(!page.exists(u => seen.contains(u.id)))
                seen = seen ++ page.map(_.id)
                cursor = page.last.id
                pages += 1
                if (pages > 100) continue = false
              }
            }
            assertTrue(
              seen.size == 100,
              seen.sorted == (1 to 100).toList,
              seen.distinct.size == 100,
              pages == 15
            )
          }
        }
      },
      test("limit validation rejects <=0") {
        withFreshDb { tx =>
          tx.connect {
            userRepo.insert(User(1, "a", "a@test.com"))
            val r1 = scala.util.Try(userRepo.pageAfter(0, 0))
            val r2 = scala.util.Try(userRepo.pageAfter(0, -1))
            assertTrue(
              r1.isFailure && r1.failed.get.isInstanceOf[IllegalArgumentException],
              r2.isFailure && r2.failed.get.isInstanceOf[IllegalArgumentException]
            )
          }
        }
      },
      test("uses > not >= (cursor row not repeated)") {
        withFreshDb { tx =>
          tx.connect {
            (1 to 3).foreach(i => userRepo.insert(User(i, s"u$i", s"u$i@test.com")))
            val p1 = userRepo.pageAfter(0, 2)
            val p2 = userRepo.pageAfter(p1.last.id, 2)
            assertTrue(
              p1.map(_.id) == List(1, 2),
              p2.map(_.id) == List(3),
              !p2.exists(_.id == p1.last.id)
            )
          }
        }
      }
    ),
    suite("Frag.keysetAfter integration")(
      test("unknown order column rejected via Frag.keysetAfter with table") {
        withFreshDb { tx =>
          tx.connect {
            val result = scala.util.Try(Frag.keysetAfter(userTable, "bad_col", DbValue.DbInt(1), 10))
            assertTrue(result.isFailure && result.failed.get.isInstanceOf[IllegalArgumentException])
          }
        }
      },
      test("Frag.keysetAfter composes into SELECT and paginates") {
        withFreshDb { tx =>
          tx.connect {
            (1 to 10).foreach(i => userRepo.insert(User(i, s"u$i", s"u$i@test.com")))
            val base = Frag.literal("SELECT id, name, email FROM user")
            val frag = base ++ Frag.keysetAfter(userTable, "id", DbValue.DbInt(5), 3)
            val rows = frag.query[User]
            assertTrue(rows.map(_.id) == List(6, 7, 8))
          }
        }
      }
    )
  )
}
