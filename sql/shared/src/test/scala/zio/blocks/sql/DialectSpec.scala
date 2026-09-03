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

object DialectSpec extends ZIOSpecDefault {
  def spec = suite("DialectSpec")(
    suite("valid identifiers render unchanged (F9)")(
      test("Postgres queue DDL golden strings") {
        assertTrue(
          Dialect.Postgres.createQueueTableDDL("queue", "id") ==
            "CREATE TABLE IF NOT EXISTS queue (\n  id TEXT NOT NULL PRIMARY KEY,\n  op TEXT NOT NULL DEFAULT 'I',\n  payload TEXT\n)",
          Dialect.Postgres.dequeueSQL("queue", "id", 10) ==
            "SELECT id FROM queue ORDER BY id LIMIT 10 FOR UPDATE SKIP LOCKED",
          Dialect.Postgres.createShadowTableDDL("shadow_q", "users") ==
            "CREATE TABLE IF NOT EXISTS shadow_q (LIKE users INCLUDING ALL)"
        )
      },
      test("SQLite queue DDL golden strings") {
        assertTrue(
          Dialect.SQLite.createQueueTableDDL("queue", "id") ==
            "CREATE TABLE IF NOT EXISTS queue (\n  id TEXT NOT NULL PRIMARY KEY,\n  op TEXT NOT NULL DEFAULT 'I',\n  payload TEXT\n)",
          Dialect.SQLite.dequeueSQL("queue", "id", 10) ==
            "SELECT id FROM queue ORDER BY id LIMIT 10"
        )
      },
      test("trigger DDL interpolates validated identifiers") {
        val pg     = Dialect.Postgres.createTriggerDDL("queue", "users", "id", "key")
        val sqlite = Dialect.SQLite.createTriggerDDL("queue", "users", "id", "key")
        assertTrue(
          pg.size == 2,
          pg.head.contains("INSERT INTO queue (key, op, payload) VALUES (OLD.id, 'D'"),
          pg(
            1
          ) == "CREATE OR REPLACE TRIGGER trg_queue_mod AFTER INSERT OR UPDATE OR DELETE ON users FOR EACH ROW EXECUTE FUNCTION queue_notify();",
          sqlite.size == 3,
          sqlite.head ==
            "CREATE TRIGGER IF NOT EXISTS trg_queue_insert AFTER INSERT ON users BEGIN INSERT OR IGNORE INTO queue (key, op) VALUES (NEW.id, 'I'); END;"
        )
      }
    ),
    suite("invalid identifiers throw (F9)")(
      test("Postgres rejects injection in every method") {
        val results = List(
          scala.util.Try(Dialect.Postgres.createQueueTableDDL("q; DROP TABLE q", "id")),
          scala.util.Try(Dialect.Postgres.dequeueSQL("queue", "id desc", 10)),
          scala.util.Try(Dialect.Postgres.createShadowTableDDL("shadow q", "users")),
          scala.util.Try(Dialect.Postgres.createTriggerDDL("queue", "users", "id;--", "key"))
        )
        assertTrue(
          results.forall(_.isFailure),
          results.forall(_.failed.get.isInstanceOf[IllegalArgumentException])
        )
      },
      test("SQLite rejects injection in every method") {
        val results = List(
          scala.util.Try(Dialect.SQLite.createQueueTableDDL("queue", "i\"d")),
          scala.util.Try(Dialect.SQLite.dequeueSQL("que ue", "id", 10)),
          scala.util.Try(Dialect.SQLite.createTriggerDDL("queue", "users", "id", "ke'y"))
        )
        assertTrue(
          results.forall(_.isFailure),
          results.forall(_.failed.get.isInstanceOf[IllegalArgumentException])
        )
      }
    )
  )
}
