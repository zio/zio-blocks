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

package zio.blocks.data.migration

import zio.test.*
import zio.blocks.schema.migration.Migration
import zio.blocks.sql.*

object MigrationPreviewSpec extends ZIOSpecDefault {

  private val idColumns = IndexedSeq(ColumnMeta("id", DbValue.DbInt(0), nullable = false))
  private val v1Repo    = Repo(Table[Int]("users", DbCodec.intCodec, idColumns), "id", DbCodec.intCodec, identity)
  private val v2Repo    = Repo(Table[Int]("users_v2", DbCodec.intCodec, idColumns), "id", DbCodec.intCodec, identity)

  private val throwingTransactor: Transactor = new Transactor {
    def connect[A](f: DbCon ?=> A): A = throw new AssertionError("Transactor.connect must not be called by previewSql")
    def transact[A](f: DbTx ?=> A): A = throw new AssertionError("Transactor.transact must not be called by previewSql")
  }

  private val unusedMigration = null.asInstanceOf[Migration[Int, Int]]

  def spec = suite("MigrationPreviewSpec")(
    test("Dialect.ddl returns create and drop frags") {
      given Dialect = Dialect.Postgres
      val table     = Table[Int]("users", DbCodec.intCodec, idColumns)
      val frags     = Dialect.ddl(table)
      assertTrue(frags.size == 2) &&
      assertTrue(frags.head.sql(SqlDialect.PostgreSQL).contains("CREATE TABLE IF NOT EXISTS users")) &&
      assertTrue(frags(1).sql(SqlDialect.PostgreSQL) == "DROP TABLE IF EXISTS users")
    },
    test("Dialect.ddl SQLite renders INTEGER for Int") {
      given Dialect = Dialect.SQLite
      val table     = Table[Int]("t", DbCodec.intCodec, idColumns)
      val frags     = Dialect.SQLite.ddl(table)
      assertTrue(frags.head.sql(SqlDialect.SQLite).contains("INTEGER"))
    },
    test("SmallMigrator.previewSql SQLite InPlace without triggers contains queue DDL and dequeue") {
      given Dialect      = Dialect.SQLite
      given Transactor   = throwingTransactor
      given DbCodec[Int] = DbCodec.intCodec
      val m              = SmallMigrator[Int, Int, Int, Int](
        repoV1 = v1Repo,
        repoV2 = v2Repo,
        migration = unusedMigration,
        queueTable = "q",
        batchSize = 10,
        target = TargetStrategy.InPlace,
        captureTriggers = false
      )
      val sql = m.previewSql()
      assertTrue(sql.exists(_.contains("CREATE TABLE IF NOT EXISTS q"))) &&
      assertTrue(sql.exists(_.contains("SELECT id FROM q ORDER BY id LIMIT 10"))) &&
      assertTrue(!sql.exists(_.contains("FOR UPDATE SKIP LOCKED"))) &&
      assertTrue(!sql.exists(_.contains("CREATE TRIGGER"))) &&
      assertTrue(sql.size == 2)
    },
    test("SmallMigrator.previewSql SQLite with triggers and shadow") {
      given Dialect      = Dialect.SQLite
      given Transactor   = throwingTransactor
      given DbCodec[Int] = DbCodec.intCodec
      val m              = SmallMigrator[Int, Int, Int, Int](
        repoV1 = v1Repo,
        repoV2 = v2Repo,
        migration = unusedMigration,
        queueTable = "my_q",
        batchSize = 5,
        target = TargetStrategy.ShadowTable("tmp"),
        captureTriggers = true
      )
      val sql = m.previewSql()
      assertTrue(sql.exists(_.contains("CREATE TABLE IF NOT EXISTS my_q"))) &&
      assertTrue(sql.exists(_.contains("-- SQLite does not support CREATE TABLE ... LIKE"))) &&
      assertTrue(sql.count(_.contains("CREATE TRIGGER")) == 3) &&
      assertTrue(sql.exists(_.contains("SELECT id FROM my_q ORDER BY id LIMIT 5"))) &&
      assertTrue(sql.exists(_.contains("ALTER TABLE users_v2 RENAME TO users_v2_old_tmp"))) &&
      assertTrue(sql.exists(_.contains("ALTER TABLE users_v2_tmp RENAME TO users_v2")))
    },
    test("SmallMigrator.previewSql Postgres InPlace with triggers") {
      given Dialect      = Dialect.Postgres
      given Transactor   = throwingTransactor
      given DbCodec[Int] = DbCodec.intCodec
      val m              = SmallMigrator[Int, Int, Int, Int](
        repoV1 = v1Repo,
        repoV2 = v2Repo,
        migration = unusedMigration,
        queueTable = "q",
        batchSize = 20,
        target = TargetStrategy.InPlace,
        captureTriggers = true
      )
      val sql = m.previewSql()
      assertTrue(sql.exists(_.contains("CREATE TABLE IF NOT EXISTS q"))) &&
      assertTrue(sql.exists(_.contains("CREATE OR REPLACE FUNCTION q_notify"))) &&
      assertTrue(sql.exists(_.contains("CREATE OR REPLACE TRIGGER trg_q_mod"))) &&
      assertTrue(sql.exists(_.contains("SELECT id FROM q ORDER BY id LIMIT 20 FOR UPDATE SKIP LOCKED")))
    },
    test("SmallMigrator.previewSql Postgres ShadowTable") {
      given Dialect      = Dialect.Postgres
      given Transactor   = throwingTransactor
      given DbCodec[Int] = DbCodec.intCodec
      val m              = SmallMigrator[Int, Int, Int, Int](
        repoV1 = v1Repo,
        repoV2 = v2Repo,
        migration = unusedMigration,
        queueTable = "q",
        batchSize = 10,
        target = TargetStrategy.ShadowTable("v2"),
        captureTriggers = false
      )
      val sql = m.previewSql()
      assertTrue(sql.exists(_.contains("CREATE TABLE IF NOT EXISTS users_v2_v2 (LIKE users_v2 INCLUDING ALL)"))) &&
      assertTrue(sql.exists(_.contains("ALTER TABLE users_v2 RENAME TO users_v2_old_v2")))
    },
    test("LargeMigrator.previewSql without connection for both dialects") {
      given DbCodec[Int] = DbCodec.intCodec
      val pgSql          = {
        given Dialect    = Dialect.Postgres
        given Transactor = throwingTransactor
        val m            = LargeMigrator[Int, Int, Int, Int](
          repoV1 = v1Repo,
          repoV2 = v2Repo,
          migration = unusedMigration,
          queueTable = "q_large",
          batchSize = 100,
          target = TargetStrategy.ShadowTable("next"),
          captureTriggers = true
        )
        m.previewSql()
      }
      val sqliteSql = {
        given Dialect    = Dialect.SQLite
        given Transactor = throwingTransactor
        val m            = LargeMigrator[Int, Int, Int, Int](
          repoV1 = v1Repo,
          repoV2 = v2Repo,
          migration = unusedMigration,
          queueTable = "q_large",
          batchSize = 100,
          target = TargetStrategy.InPlace,
          captureTriggers = true
        )
        m.previewSql()
      }
      assertTrue(pgSql.exists(_.contains("FOR UPDATE SKIP LOCKED"))) &&
      assertTrue(!sqliteSql.exists(_.contains("FOR UPDATE SKIP LOCKED"))) &&
      assertTrue(pgSql.exists(_.contains("CREATE OR REPLACE FUNCTION"))) &&
      assertTrue(sqliteSql.exists(_.contains("CREATE TRIGGER IF NOT EXISTS")))
    },
    test("previewSql does not open connection - throwing transactor not invoked") {
      given Dialect      = Dialect.Postgres
      given Transactor   = throwingTransactor
      given DbCodec[Int] = DbCodec.intCodec
      val mSmall         = SmallMigrator[Int, Int, Int, Int](v1Repo, v2Repo, unusedMigration, "q", 10, TargetStrategy.InPlace)
      val mLarge         = LargeMigrator[Int, Int, Int, Int](v1Repo, v2Repo, unusedMigration, "q", 10, TargetStrategy.InPlace)
      val s1             = mSmall.previewSql()
      val s2             = mLarge.previewSql()
      assertTrue(s1.nonEmpty && s2.nonEmpty)
    },
    test("QueueTable pure helpers match dialect builders") {
      given Dialect = Dialect.Postgres
      val ddl       = QueueTable.queueTableDDL("my_q")
      val triggers  = QueueTable.triggerDDLs("my_q", "users", "id")
      val dequeue   = QueueTable.dequeueSQLTemplate("my_q", 10)
      assertTrue(ddl == Dialect.Postgres.createQueueTableDDL("my_q", "id")) &&
      assertTrue(triggers == Dialect.Postgres.createTriggerDDL("my_q", "users", "id", "id")) &&
      assertTrue(dequeue == Dialect.Postgres.dequeueSQL("my_q", "id", 10))
    },
    test("TargetStrategyApplier preview matches expected strings") {
      given Dialect = Dialect.Postgres
      val table     = Table[Int]("users_v2", DbCodec.intCodec, idColumns)
      val prepare   = TargetStrategyApplier.preparePreview(table, TargetStrategy.ShadowTable("tmp"))
      val finalize  = TargetStrategyApplier.finalizePreview("users_v2", TargetStrategy.ShadowTable("tmp"))
      assertTrue(prepare.contains("CREATE TABLE IF NOT EXISTS users_v2_tmp (LIKE users_v2 INCLUDING ALL)")) &&
      assertTrue(
        finalize == List(
          "ALTER TABLE users_v2 RENAME TO users_v2_old_tmp",
          "ALTER TABLE users_v2_tmp RENAME TO users_v2"
        )
      )
    }
  )
}
