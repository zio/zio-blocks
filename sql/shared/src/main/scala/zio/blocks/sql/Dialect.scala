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

trait Naming {
  def quoteIdentifier(id: String): String
}

trait SqlMigration {
  val supportsSkipLocked: Boolean
  def createQueueTableDDL(table: String, col: String): String
  def dequeueSQL(table: String, col: String, batchSize: Int): String
  def createShadowTableDDL(shadowName: String, sourceTable: String): String
  def createTriggerDDL(queueTable: String, sourceTable: String, idColumn: String): List[String]
}

trait Dialect extends Naming, SqlMigration

object Dialect {
  def forSqlDialect(d: SqlDialect): Dialect = d match {
    case SqlDialect.PostgreSQL => Postgres
    case SqlDialect.SQLite     => SQLite
  }

  object Postgres extends Dialect {
    val supportsSkipLocked = true

    def quoteIdentifier(id: String): String = "\"" + id + "\""

    def createQueueTableDDL(table: String, col: String): String =
      s"CREATE TABLE IF NOT EXISTS $table (\n  $col TEXT NOT NULL PRIMARY KEY,\n  op TEXT NOT NULL DEFAULT 'I',\n  payload TEXT\n)"

    def dequeueSQL(table: String, col: String, batchSize: Int): String =
      s"SELECT $col FROM $table ORDER BY $col LIMIT $batchSize FOR UPDATE SKIP LOCKED"

    def createShadowTableDDL(shadowName: String, sourceTable: String): String =
      s"CREATE TABLE IF NOT EXISTS $shadowName (LIKE $sourceTable INCLUDING ALL)"

    def createTriggerDDL(queueTable: String, sourceTable: String, idColumn: String): List[String] = {
      val funcName = s"${queueTable}_notify"
      val func     =
        s"""CREATE OR REPLACE FUNCTION $funcName()
           |RETURNS TRIGGER AS $$
           |BEGIN
           |  IF TG_OP = 'DELETE' THEN
           |    INSERT INTO $queueTable ($idColumn, op, payload) VALUES (OLD.$idColumn, 'D', row_to_json(OLD.*)::text) ON CONFLICT ($idColumn) DO UPDATE SET op = 'D', payload = EXCLUDED.payload;
           |  ELSIF TG_OP = 'INSERT' THEN
           |    INSERT INTO $queueTable ($idColumn, op) VALUES (NEW.$idColumn, 'I') ON CONFLICT ($idColumn) DO NOTHING;
           |  ELSIF TG_OP = 'UPDATE' THEN
           |    INSERT INTO $queueTable ($idColumn, op) VALUES (NEW.$idColumn, 'U') ON CONFLICT ($idColumn) DO NOTHING;
           |  END IF;
           |  RETURN NULL;
           |END;
           |$$ LANGUAGE plpgsql;""".stripMargin
      val trigger =
        s"CREATE TRIGGER trg_${queueTable}_mod AFTER INSERT OR UPDATE OR DELETE ON $sourceTable FOR EACH ROW EXECUTE FUNCTION $funcName();"
      List(func, trigger)
    }
  }

  object SQLite extends Dialect {
    val supportsSkipLocked = false

    def quoteIdentifier(id: String): String = "\"" + id + "\""

    def createQueueTableDDL(table: String, col: String): String =
      s"CREATE TABLE IF NOT EXISTS $table (\n  $col TEXT NOT NULL PRIMARY KEY,\n  op TEXT NOT NULL DEFAULT 'I',\n  payload TEXT\n)"

    def dequeueSQL(table: String, col: String, batchSize: Int): String =
      s"SELECT $col FROM $table ORDER BY $col LIMIT $batchSize"

    def createShadowTableDDL(shadowName: String, sourceTable: String): String =
      throw UnsupportedOperationException(
        s"SQLite does not support CREATE TABLE ... LIKE. " +
          s"Create shadow table '$shadowName' manually with matching column definitions."
      )

    def createTriggerDDL(queueTable: String, sourceTable: String, idColumn: String): List[String] =
      List(
        s"CREATE TRIGGER IF NOT EXISTS trg_${queueTable}_insert AFTER INSERT ON $sourceTable BEGIN INSERT OR IGNORE INTO $queueTable ($idColumn, op) VALUES (NEW.$idColumn, 'I'); END;",
        s"CREATE TRIGGER IF NOT EXISTS trg_${queueTable}_update AFTER UPDATE ON $sourceTable BEGIN INSERT OR IGNORE INTO $queueTable ($idColumn, op) VALUES (NEW.$idColumn, 'U'); END;",
        s"CREATE TRIGGER IF NOT EXISTS trg_${queueTable}_delete AFTER DELETE ON $sourceTable BEGIN INSERT OR REPLACE INTO $queueTable ($idColumn, op) VALUES (OLD.$idColumn, 'D'); END;"
      )
  }
}
