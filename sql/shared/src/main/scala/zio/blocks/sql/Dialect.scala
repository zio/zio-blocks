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
 * The SQL that a data migration's change queue needs: the queue table, the
 * triggers that record changed source rows in it, a shadow copy of a table, and
 * the query that claims queued keys.
 *
 * Every table and column parameter must be a bare SQL identifier
 * (`[A-Za-z_][A-Za-z0-9_]*`): not schema-qualified and not quoted. Each method
 * checks this first and throws `IllegalArgumentException` otherwise, then
 * writes the names into the SQL without quotes.
 *
 * @see
 *   [[https://zioblocks.com/reference/data-migration/ Data Migration]]
 */
trait SqlMigration {

  /**
   * `true` when [[dequeueSQL]] locks the rows it claims with
   * `FOR UPDATE SKIP LOCKED`, as on PostgreSQL.
   */
  val supportsSkipLocked: Boolean

  /**
   * Returns `CREATE TABLE IF NOT EXISTS <table>` for a queue table with the
   * text primary key `col`, an `op` column (default `'I'`) and a `payload`
   * column.
   */
  def createQueueTableDDL(table: String, col: String): String

  /**
   * Returns a `SELECT` of up to `batchSize` keys from the queue table, in key
   * order; on PostgreSQL it ends with `FOR UPDATE SKIP LOCKED`.
   */
  def dequeueSQL(table: String, col: String, batchSize: Int): String

  /**
   * Returns `CREATE TABLE IF NOT EXISTS <shadowName>` with the structure of
   * `sourceTable`. SQLite has no such statement, so [[Dialect.SQLite]] throws
   * `UnsupportedOperationException`.
   */
  def createShadowTableDDL(shadowName: String, sourceTable: String): String

  /**
   * Returns the statements that install triggers on `sourceTable`: each insert,
   * update or delete records the row's `sourceIdColumn` value in the queue
   * table's `queueKeyColumn`, with `op` set to `'I'`, `'U'` or `'D'`. Running
   * them again replaces or keeps the triggers, so they can run on every start.
   */
  def createTriggerDDL(
    queueTable: String,
    sourceTable: String,
    sourceIdColumn: String,
    queueKeyColumn: String
  ): List[String]
}

/**
 * A database's migration SQL ([[SqlMigration]]) together with its
 * [[SqlDialect]].
 *
 * {{{
 * import zio.blocks.schema.Schema
 *
 * case class Product(sku: String, priceCents: Long)
 * object Product { implicit val schema: Schema[Product] = Schema.derived }
 *
 * val dequeue = Dialect.Postgres.dequeueSQL("product_queue", "sku", 50)
 * // SELECT sku FROM product_queue ORDER BY sku LIMIT 50 FOR UPDATE SKIP LOCKED
 * val ddl = Dialect.Postgres.ddl(Table.derived[Product]).map(_.sql(SqlDialect.PostgreSQL))
 * // List(CREATE TABLE IF NOT EXISTS product (
 * //   sku TEXT NOT NULL,
 * //   price_cents BIGINT NOT NULL
 * // ), DROP TABLE IF EXISTS product)
 * }}}
 *
 * @see
 *   [[https://zioblocks.com/reference/data-migration/ Data Migration]]
 */
trait Dialect extends SqlMigration {

  /**
   * The [[SqlDialect]] of this database, to compare with the dialect of a
   * connection.
   */
  def sqlDialect: SqlDialect

  /**
   * Returns `table`'s `CREATE TABLE` statement for [[sqlDialect]] followed by
   * its `DROP TABLE` statement, without opening a connection.
   */
  def ddl[A](table: Table[A]): Seq[Frag] =
    Seq(table.createTable(sqlDialect), Ddl.dropTable(table.name))
}

/** The [[Dialect]]s for PostgreSQL and SQLite. */
object Dialect {

  /** Returns `dialect.ddl(table)` for the `Dialect` in scope. */
  def ddl[A](table: Table[A])(using dialect: Dialect): Seq[Frag] =
    dialect.ddl(table)

  /** PostgreSQL: row locking with `SKIP LOCKED`, and `LIKE` shadow tables. */
  object Postgres extends Dialect {

    /** The matching [[SqlDialect]]. */
    val sqlDialect: SqlDialect = SqlDialect.PostgreSQL

    /** Whether [[dequeueSQL]] uses `FOR UPDATE SKIP LOCKED`. */
    val supportsSkipLocked = true

    /**
     * Returns the queue table DDL, as [[SqlMigration#createQueueTableDDL]]
     * describes.
     */
    def createQueueTableDDL(table: String, col: String): String = {
      val t = SqlIdentifier.validate("table", table)
      val c = SqlIdentifier.validate("column", col)
      s"CREATE TABLE IF NOT EXISTS $t (\n  $c TEXT NOT NULL PRIMARY KEY,\n  op TEXT NOT NULL DEFAULT 'I',\n  payload TEXT\n)"
    }

    /**
     * Returns the claiming `SELECT`, as [[SqlMigration#dequeueSQL]] describes.
     */
    def dequeueSQL(table: String, col: String, batchSize: Int): String = {
      val t = SqlIdentifier.validate("table", table)
      val c = SqlIdentifier.validate("column", col)
      s"SELECT $c FROM $t ORDER BY $c LIMIT $batchSize FOR UPDATE SKIP LOCKED"
    }

    /**
     * Returns the shadow-table DDL, as [[SqlMigration#createShadowTableDDL]]
     * describes.
     */
    def createShadowTableDDL(shadowName: String, sourceTable: String): String = {
      val shadow = SqlIdentifier.validate("table", shadowName)
      val source = SqlIdentifier.validate("table", sourceTable)
      s"CREATE TABLE IF NOT EXISTS $shadow (LIKE $source INCLUDING ALL)"
    }

    /**
     * Returns the trigger statements, as [[SqlMigration#createTriggerDDL]]
     * describes.
     */
    def createTriggerDDL(
      queueTable: String,
      sourceTable: String,
      sourceIdColumn: String,
      queueKeyColumn: String
    ): List[String] = {
      val queue    = SqlIdentifier.validate("table", queueTable)
      val source   = SqlIdentifier.validate("table", sourceTable)
      val idCol    = SqlIdentifier.validate("column", sourceIdColumn)
      val keyCol   = SqlIdentifier.validate("column", queueKeyColumn)
      val funcName = s"${queue}_notify"
      // s-interpolator renders $$ as one $, so build plpgsql dollar-quotes from a named value.
      val dollarQuote = "$$"
      val func        =
        s"""CREATE OR REPLACE FUNCTION $funcName()
           |RETURNS TRIGGER AS $dollarQuote
           |BEGIN
           |  IF TG_OP = 'DELETE' THEN
           |    INSERT INTO $queue ($keyCol, op, payload) VALUES (OLD.$idCol, 'D', row_to_json(OLD)::text) ON CONFLICT ($keyCol) DO UPDATE SET op = 'D', payload = EXCLUDED.payload;
           |  ELSIF TG_OP = 'INSERT' THEN
           |    INSERT INTO $queue ($keyCol, op) VALUES (NEW.$idCol, 'I') ON CONFLICT ($keyCol) DO NOTHING;
           |  ELSIF TG_OP = 'UPDATE' THEN
           |    INSERT INTO $queue ($keyCol, op) VALUES (NEW.$idCol, 'U') ON CONFLICT ($keyCol) DO NOTHING;
           |  END IF;
           |  RETURN NULL;
           |END;
           |$dollarQuote LANGUAGE plpgsql;""".stripMargin
      // CREATE OR REPLACE TRIGGER keeps re-running installTriggers idempotent (PG 14+).
      val trigger =
        s"CREATE OR REPLACE TRIGGER trg_${queue}_mod AFTER INSERT OR UPDATE OR DELETE ON $source FOR EACH ROW EXECUTE FUNCTION $funcName();"
      List(func, trigger)
    }
  }

  /** SQLite: no row locking and no shadow-table statement. */
  object SQLite extends Dialect {

    /** The matching [[SqlDialect]]. */
    val sqlDialect: SqlDialect = SqlDialect.SQLite

    /** Whether [[dequeueSQL]] uses `FOR UPDATE SKIP LOCKED`. */
    val supportsSkipLocked = false

    /**
     * Returns the queue table DDL, as [[SqlMigration#createQueueTableDDL]]
     * describes.
     */
    def createQueueTableDDL(table: String, col: String): String = {
      val t = SqlIdentifier.validate("table", table)
      val c = SqlIdentifier.validate("column", col)
      s"CREATE TABLE IF NOT EXISTS $t (\n  $c TEXT NOT NULL PRIMARY KEY,\n  op TEXT NOT NULL DEFAULT 'I',\n  payload TEXT\n)"
    }

    /**
     * Returns the claiming `SELECT`, as [[SqlMigration#dequeueSQL]] describes.
     */
    def dequeueSQL(table: String, col: String, batchSize: Int): String = {
      val t = SqlIdentifier.validate("table", table)
      val c = SqlIdentifier.validate("column", col)
      s"SELECT $c FROM $t ORDER BY $c LIMIT $batchSize"
    }

    /**
     * Throws `UnsupportedOperationException`: SQLite has no
     * `CREATE TABLE ... LIKE`, so create the shadow table by hand.
     */
    def createShadowTableDDL(shadowName: String, sourceTable: String): String =
      throw new UnsupportedOperationException(
        s"SQLite does not support CREATE TABLE ... LIKE. " +
          s"Create shadow table '$shadowName' manually with matching column definitions."
      )

    /**
     * Returns the trigger statements, as [[SqlMigration#createTriggerDDL]]
     * describes.
     */
    def createTriggerDDL(
      queueTable: String,
      sourceTable: String,
      sourceIdColumn: String,
      queueKeyColumn: String
    ): List[String] = {
      val queue  = SqlIdentifier.validate("table", queueTable)
      val source = SqlIdentifier.validate("table", sourceTable)
      val idCol  = SqlIdentifier.validate("column", sourceIdColumn)
      val keyCol = SqlIdentifier.validate("column", queueKeyColumn)
      List(
        s"CREATE TRIGGER IF NOT EXISTS trg_${queue}_insert AFTER INSERT ON $source BEGIN INSERT OR IGNORE INTO $queue ($keyCol, op) VALUES (NEW.$idCol, 'I'); END;",
        s"CREATE TRIGGER IF NOT EXISTS trg_${queue}_update AFTER UPDATE ON $source BEGIN INSERT OR IGNORE INTO $queue ($keyCol, op) VALUES (NEW.$idCol, 'U'); END;",
        s"CREATE TRIGGER IF NOT EXISTS trg_${queue}_delete AFTER DELETE ON $source BEGIN INSERT OR REPLACE INTO $queue ($keyCol, op) VALUES (OLD.$idCol, 'D'); END;"
      )
    }
  }
}
