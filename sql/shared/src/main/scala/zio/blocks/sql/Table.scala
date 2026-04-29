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

final case class Table[A](name: String, codec: DbCodec[A], columnsMeta: IndexedSeq[ColumnMeta]) {
  def columns: IndexedSeq[String] = codec.columns

  def createTable(dialect: SqlDialect): Frag = {
    val columnDefs = columnsMeta.map { column =>
      ColumnDef(column.name, dialect.typeName(column.dbValue), nullable = column.nullable)
    }
    Ddl.createTable(name, columnDefs)
  }

  def dropTable: Frag = Ddl.dropTable(name)
}

object Table {

  private val defaultNamingPolicy: TableNamingPolicy = TableNamingPolicy.Singular

  def derived[A](implicit schema: Schema[A]): Table[A] = {
    derived[A](defaultNamingPolicy)
  }

  def derived[A](tableName: String)(implicit schema: Schema[A]): Table[A] = {
    val codec   = schema.deriving(DbCodecDeriver).derive
    val columns = TableMetadata.columnsFor(schema)
    Table(tableName, codec, columns)
  }

  def derived[A](namingPolicy: TableNamingPolicy)(implicit schema: Schema[A]): Table[A] = {
    val codec     = schema.deriving(DbCodecDeriver).derive
    val tableName = deriveTableName(schema, namingPolicy)
    val columns   = TableMetadata.columnsFor(schema)
    Table(tableName, codec, columns)
  }

  private[sql] def deriveTableName[A](schema: Schema[A], namingPolicy: TableNamingPolicy = defaultNamingPolicy): String = {
    val configured = schema.reflect.modifiers.collectFirst { case Modifier.config("sql.table_name", value) =>
      value
    }
    configured.getOrElse {
      val typeName = schema.reflect.typeId.name
      namingPolicy.defaultName(typeName)
    }
  }
}
