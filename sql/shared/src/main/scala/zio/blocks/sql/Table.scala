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
 * Metadata binding a Scala type `A` to a specific database table.
 *
 * @param name
 *   The SQL table name used in generated queries.
 * @param codec
 *   The [[DbCodec]] used to read and write rows of type `A`.
 * @param columnsMeta
 *   Per-column metadata (name, SQL type, nullability) derived from the schema.
 *   Used by [[createTable]] to emit DDL.
 */
final case class Table[A](name: String, codec: DbCodec[A], columnsMeta: IndexedSeq[ColumnMeta]) {

  private val validatedName        = SqlIdentifier.validate("table", name)
  private val validatedColumnsMeta = columnsMeta.map { column =>
    column.copy(name = SqlIdentifier.validate("column", column.name))
  }

  /** Column names in codec order (delegates to `codec.columns`). */
  def columns: IndexedSeq[String] = validatedColumnsMeta.map(_.name)

  /**
   * Generates a `CREATE TABLE IF NOT EXISTS` fragment using the column types
   * resolved by `dialect`.
   */
  def createTable(dialect: SqlDialect): Frag = {
    val columnDefs = validatedColumnsMeta.map { column =>
      ColumnDef(column.name, dialect.typeName(column.dbValue), nullable = column.nullable)
    }
    Ddl.createTable(validatedName, columnDefs)
  }

  /** Generates a `DROP TABLE IF EXISTS` fragment for this table. */
  def dropTable: Frag = Ddl.dropTable(validatedName)
}

object Table {

  private val defaultNamingPolicy: TableNamingPolicy = TableNamingPolicy.Singular

  /**
   * Derives a [[Table]] from `A`'s schema using the default naming policy
   * ([[TableNamingPolicy.Singular]], i.e. `CamelCase` → `snake_case` singular).
   *
   * The table name can be overridden by annotating `A` with
   * `@Modifier.config("sql.table_name", "my_table")`.
   */
  def derived[A](implicit schema: Schema[A]): Table[A] =
    derived[A](defaultNamingPolicy)

  /**
   * Derives a [[Table]] from `A`'s schema with an explicit table name,
   * bypassing both the schema annotation and the naming policy.
   */
  def derived[A](tableName: String)(implicit schema: Schema[A]): Table[A] = {
    val codec   = schema.deriving(DbCodecDeriver).derive
    val columns = TableMetadata.columnsFor(schema)
    Table(tableName, codec, columns)
  }

  /**
   * Derives a [[Table]] from `A`'s schema using the supplied naming policy to
   * compute the table name (unless overridden by a `@Modifier.config`
   * annotation).
   */
  def derived[A](namingPolicy: TableNamingPolicy)(implicit schema: Schema[A]): Table[A] = {
    val codec     = schema.deriving(DbCodecDeriver).derive
    val tableName = deriveTableName(schema, namingPolicy)
    val columns   = TableMetadata.columnsFor(schema)
    Table(tableName, codec, columns)
  }

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
