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

import zio.blocks.schema._

/**
 * Binds a row type `A` to a database table: the table's name, the [[DbCodec]]
 * that reads and writes its rows, and the name, type and nullability of each
 * column. [[Repo]] and `SqlQuery` render their SQL from it, and
 * [[Table#createTable]] renders its `CREATE TABLE` statement.
 *
 * `Table.derived` takes the name from the type and the columns from its
 * `Schema`. The constructor checks that the table and column names are SQL
 * identifiers (`[A-Za-z_][A-Za-z0-9_]*`) and that no column repeats, and throws
 * `IllegalArgumentException` otherwise.
 *
 * {{{
 * import zio.blocks.schema.Schema
 *
 * case class Customer(id: Int, fullName: String, email: Option[String])
 * object Customer { implicit val schema: Schema[Customer] = Schema.derived }
 *
 * val customers = Table.derived[Customer]
 * val name      = customers.name    // customer
 * val columns   = customers.columns // Vector(id, full_name, email)
 *
 * val xa = JdbcTransactor.fromUrl("jdbc:sqlite::memory:", SqlDialect.SQLite)
 * val rows = xa.connect {
 *   customers.createTable(SqlDialect.SQLite).update
 *   sql"INSERT INTO customer VALUES (1, 'Ada Lovelace', NULL)".update
 *   sql"SELECT * FROM customer".query[Customer]
 * }
 * // List(Customer(1,Ada Lovelace,None))
 * }}}
 *
 * The columns come from the `Schema`, not from `codec`, and in 0.0.56 the two
 * disagree for some fields, a known bug: a nested case class field becomes
 * `<field>_<subfield>` columns while the codec stores it as one JSON column,
 * and a collection field makes `Table.derived` throw
 * `UnsupportedOperationException`.
 *
 * @param name
 *   the table name, used as is in generated SQL
 * @param codec
 *   the [[DbCodec]] that reads and writes rows of type `A`
 * @param columnsMeta
 *   the name, representative [[DbValue]] and nullability of each column, in
 *   codec order; [[createTable]] renders them
 * @see
 *   [[https://zioblocks.com/reference/sql/table/ Table]]
 */
final case class Table[A](name: String, codec: DbCodec[A], columnsMeta: IndexedSeq[ColumnMeta]) {

  private val validatedName        = SqlIdentifier.validate("table", name)
  private val validatedColumnsMeta = columnsMeta.map { column =>
    column.copy(name = SqlIdentifier.validate("column", column.name))
  }

  // Fail fast on duplicate columns: downstream builders (e.g.
  // `Upsert.insertDoUpdate`) resolve column values by position, and a silently
  // duplicated column would bind the wrong value to the wrong placeholder.
  require(
    validatedColumnsMeta.map(_.name).distinct.size == validatedColumnsMeta.size,
    s"Table '$name' has duplicate columns: ${validatedColumnsMeta.map(_.name).mkString(", ")}"
  )

  /**
   * Returns the column names, taken from `columnsMeta` (not from
   * `codec.columns`).
   */
  def columns: IndexedSeq[String] = validatedColumnsMeta.map(_.name)

  /**
   * Returns a `CREATE TABLE IF NOT EXISTS` statement, one line per column with
   * the type `dialect` gives its representative [[DbValue]] and `NOT NULL`
   * unless the column is nullable.
   *
   * {{{
   * import zio.blocks.schema.Schema
   *
   * case class Product(sku: String, priceCents: Long, stock: Option[Int])
   * object Product { implicit val schema: Schema[Product] = Schema.derived }
   *
   * val ddl = Table.derived[Product].createTable(SqlDialect.PostgreSQL).sql(SqlDialect.PostgreSQL)
   * // CREATE TABLE IF NOT EXISTS product (
   * //   sku TEXT NOT NULL,
   * //   price_cents BIGINT NOT NULL,
   * //   stock INTEGER
   * // )
   * }}}
   *
   * Three known bugs in 0.0.56: the statement declares no `PRIMARY KEY`, so
   * [[Repo#insertOrUpdate]] fails on the table; the names are not quoted, so a
   * table or column named by an SQL keyword, such as `order`, gives invalid
   * SQL; and on SQLite an `Instant` column is declared `TEXT`, which the codec
   * can't read back.
   */
  def createTable(dialect: SqlDialect): Frag = {
    val columnDefs = validatedColumnsMeta.map { column =>
      ColumnDef(column.name, dialect.typeName(column.dbValue), nullable = column.nullable)
    }
    Ddl.createTable(validatedName, columnDefs)
  }

  /**
   * Returns a `DROP TABLE IF EXISTS` statement for this table, the same for
   * every dialect. The name is not quoted, a known bug in 0.0.56.
   */
  def dropTable: Frag = Ddl.dropTable(validatedName)
}

/** Constructors that derive a [[Table]] from a `Schema`. */
object Table {

  private val defaultNamingPolicy: TableNamingPolicy = TableNamingPolicy.Singular

  /**
   * Derives a [[Table]] from `A`'s schema, named by
   * [[TableNamingPolicy.Singular]] (`OrderLine` becomes `order_line`) unless
   * `A` has `@Modifier.config("sql.table_name", "<name>")`. Columns are the
   * fields in snake case, or the name from `@Modifier.rename`.
   *
   * {{{
   * import zio.blocks.schema.{Modifier, Schema}
   *
   * case class User(id: Int, name: String)
   * object User { implicit val schema: Schema[User] = Schema.derived }
   *
   * @Modifier.config("sql.table_name", "accounts")
   * case class Account(id: Int, ownerId: Int)
   * object Account { implicit val schema: Schema[Account] = Schema.derived }
   *
   * val userTable    = Table.derived[User].name    // user
   * val accountTable = Table.derived[Account].name // accounts
   * }}}
   *
   * To write the table's DDL to a file at compile time, pass it to
   * [[Dump.dumpTable]].
   *
   * @see
   *   [[https://zioblocks.com/reference/sql/table/ Table]]
   */
  def derived[A](implicit schema: Schema[A]): Table[A] =
    derived[A](defaultNamingPolicy)

  /**
   * Derives a [[Table]] from `A`'s schema with the name `tableName`, ignoring
   * the naming policy and any `sql.table_name` annotation.
   */
  def derived[A](tableName: String)(implicit schema: Schema[A]): Table[A] = {
    val codec   = schema.deriving(DbCodecDeriver).derive
    val columns = TableMetadata.columnsFor(schema)
    Table(tableName, codec, columns)
  }

  /**
   * Derives a [[Table]] from `A`'s schema, named by `namingPolicy` unless `A`
   * has `@Modifier.config("sql.table_name", "<name>")`.
   *
   * {{{
   * import zio.blocks.schema.Schema
   *
   * case class Category(id: Int, title: String)
   * object Category { implicit val schema: Schema[Category] = Schema.derived }
   *
   * val name = Table.derived[Category](TableNamingPolicy.Plural).name // categories
   * }}}
   */
  def derived[A](namingPolicy: TableNamingPolicy)(implicit schema: Schema[A]): Table[A] = {
    val codec     = schema.deriving(DbCodecDeriver).derive
    val tableName = deriveTableName(schema, namingPolicy)
    val columns   = TableMetadata.columnsFor(schema)
    Table(tableName, codec, columns)
  }

  /**
   * Returns the `sql.table_name` annotation, or the name `namingPolicy` gives.
   */
  private[sql] def deriveTableName[A](
    schema: Schema[A],
    namingPolicy: TableNamingPolicy = defaultNamingPolicy
  ): String = {
    val configured = schema.reflect.modifiers.collectFirst { case Modifier.config("sql.table_name", value) =>
      value
    }
    configured.getOrElse {
      val typeName = schema.reflect.typeId.name
      namingPolicy.defaultName(typeName)
    }
  }
}
