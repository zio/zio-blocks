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

/**
 * SQL generation for the data-migration queue machinery.
 *
 * All identifier parameters (`table`, `col`, `shadowName`, `sourceTable`,
 * `sourceIdColumn`, `queueKeyColumn`) must be bare, pre-validated SQL
 * identifiers — never user input, never schema-qualified, never quoted. Valid
 * identifiers match `[A-Za-z_][A-Za-z0-9_]*`; callers must enforce this
 * whitelist before passing values in. Implementations interpolate them into
 * DDL/DML as-is.
 */
trait SqlMigration {

  /** Whether `dequeueSQL` uses `FOR UPDATE SKIP LOCKED` (PostgreSQL only). */
  val supportsSkipLocked: Boolean

  /**
   * Idempotent `CREATE TABLE IF NOT EXISTS` DDL for the dirty-key queue table.
   */
  def createQueueTableDDL(table: String, col: String): String

  /**
   * SELECT claiming up to `batchSize` queue keys, ordered by key; on PostgreSQL
   * the claim locks rows with `FOR UPDATE SKIP LOCKED`.
   */
  def dequeueSQL(table: String, col: String, batchSize: Int): String

  /**
   * Idempotent shadow-table DDL copying `sourceTable` structure; unsupported on
   * SQLite (raises UnsupportedOperationException).
   */
  def createShadowTableDDL(shadowName: String, sourceTable: String): String

  /**
   * Statements installing capture triggers that upsert source-row keys into the
   * queue table. Must be idempotent (safe to re-run on restart).
   */
  def createTriggerDDL(
    queueTable: String,
    sourceTable: String,
    sourceIdColumn: String,
    queueKeyColumn: String
  ): List[String]
}

/** Database-specific SQL generation for migration support structures. */
trait Dialect extends SqlMigration

object Dialect {

  object Postgres extends Dialect {
    val supportsSkipLocked = true

    def createQueueTableDDL(table: String, col: String): String =
      s"CREATE TABLE IF NOT EXISTS $table (\n  $col TEXT NOT NULL PRIMARY KEY,\n  op TEXT NOT NULL DEFAULT 'I',\n  payload TEXT\n)"

    def dequeueSQL(table: String, col: String, batchSize: Int): String =
      s"SELECT $col FROM $table ORDER BY $col LIMIT $batchSize FOR UPDATE SKIP LOCKED"

    def createShadowTableDDL(shadowName: String, sourceTable: String): String =
      s"CREATE TABLE IF NOT EXISTS $shadowName (LIKE $sourceTable INCLUDING ALL)"

    def createTriggerDDL(
      queueTable: String,
      sourceTable: String,
      sourceIdColumn: String,
      queueKeyColumn: String
    ): List[String] = {
      val funcName = s"${queueTable}_notify"
      // s-interpolator renders $$ as one $, so build plpgsql dollar-quotes from a named value.
      val dollarQuote = "$$"
      val func        =
        s"""CREATE OR REPLACE FUNCTION $funcName()
           |RETURNS TRIGGER AS $dollarQuote
           |BEGIN
           |  IF TG_OP = 'DELETE' THEN
           |    INSERT INTO $queueTable ($queueKeyColumn, op, payload) VALUES (OLD.$sourceIdColumn, 'D', row_to_json(OLD)::text) ON CONFLICT ($queueKeyColumn) DO UPDATE SET op = 'D', payload = EXCLUDED.payload;
           |  ELSIF TG_OP = 'INSERT' THEN
           |    INSERT INTO $queueTable ($queueKeyColumn, op) VALUES (NEW.$sourceIdColumn, 'I') ON CONFLICT ($queueKeyColumn) DO NOTHING;
           |  ELSIF TG_OP = 'UPDATE' THEN
           |    INSERT INTO $queueTable ($queueKeyColumn, op) VALUES (NEW.$sourceIdColumn, 'U') ON CONFLICT ($queueKeyColumn) DO NOTHING;
           |  END IF;
           |  RETURN NULL;
           |END;
           |$dollarQuote LANGUAGE plpgsql;""".stripMargin
      // CREATE OR REPLACE TRIGGER keeps re-running installTriggers idempotent (PG 14+).
      val trigger =
        s"CREATE OR REPLACE TRIGGER trg_${queueTable}_mod AFTER INSERT OR UPDATE OR DELETE ON $sourceTable FOR EACH ROW EXECUTE FUNCTION $funcName();"
      List(func, trigger)
    }
  }

  object SQLite extends Dialect {
    val supportsSkipLocked = false

    def createQueueTableDDL(table: String, col: String): String =
      s"CREATE TABLE IF NOT EXISTS $table (\n  $col TEXT NOT NULL PRIMARY KEY,\n  op TEXT NOT NULL DEFAULT 'I',\n  payload TEXT\n)"

    def dequeueSQL(table: String, col: String, batchSize: Int): String =
      s"SELECT $col FROM $table ORDER BY $col LIMIT $batchSize"

    def createShadowTableDDL(shadowName: String, sourceTable: String): String =
      throw new UnsupportedOperationException(
        s"SQLite does not support CREATE TABLE ... LIKE. " +
          s"Create shadow table '$shadowName' manually with matching column definitions."
      )

    def createTriggerDDL(
      queueTable: String,
      sourceTable: String,
      sourceIdColumn: String,
      queueKeyColumn: String
    ): List[String] =
      List(
        s"CREATE TRIGGER IF NOT EXISTS trg_${queueTable}_insert AFTER INSERT ON $sourceTable BEGIN INSERT OR IGNORE INTO $queueTable ($queueKeyColumn, op) VALUES (NEW.$sourceIdColumn, 'I'); END;",
        s"CREATE TRIGGER IF NOT EXISTS trg_${queueTable}_update AFTER UPDATE ON $sourceTable BEGIN INSERT OR IGNORE INTO $queueTable ($queueKeyColumn, op) VALUES (NEW.$sourceIdColumn, 'U'); END;",
        s"CREATE TRIGGER IF NOT EXISTS trg_${queueTable}_delete AFTER DELETE ON $sourceTable BEGIN INSERT OR REPLACE INTO $queueTable ($queueKeyColumn, op) VALUES (OLD.$sourceIdColumn, 'D'); END;"
      )
  }
}
