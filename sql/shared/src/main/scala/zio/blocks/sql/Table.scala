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

final case class Table[A](name: String, codec: DbCodec[A], dialect: SqlDialect) {
  def columns: IndexedSeq[String] = codec.columns

  def createTable: Frag = {
    val columnDefs = codec.columns.map { col =>
      ColumnDef(col, dialect.typeName(DbValue.DbString("")), nullable = false)
    }
    Ddl.createTable(name, columnDefs)
  }

  def dropTable: Frag = Ddl.dropTable(name)
}

object Table {

  def derived[A](dialect: SqlDialect)(implicit schema: Schema[A]): Table[A] = {
    val codec     = schema.deriving(DbCodecDeriver).derive
    val tableName = deriveTableName(schema)
    Table(tableName, codec, dialect)
  }

  def derived[A](tableName: String, dialect: SqlDialect)(implicit schema: Schema[A]): Table[A] = {
    val codec = schema.deriving(DbCodecDeriver).derive
    Table(tableName, codec, dialect)
  }

  private def deriveTableName[A](schema: Schema[A]): String = {
    val configured = schema.reflect.modifiers.collectFirst { case Modifier.config("sql.table_name", value) =>
      value
    }
    configured.getOrElse {
      val typeName = schema.reflect.typeId.name
      SqlNameMapper.SnakeCase(typeName)
    }
  }

  def pluralize(s: String): String =
    if (s.isEmpty) s
    else if (s.endsWith("s") || s.endsWith("x") || s.endsWith("ch") || s.endsWith("sh") || s.endsWith("zz"))
      s + "es"
    else if (s.endsWith("z")) s + "zes" // quiz -> quizzes
    else if (s.endsWith("y") && s.length > 1 && !isVowel(s.charAt(s.length - 2))) s.dropRight(1) + "ies"
    else s + "s"

  private def isVowel(c: Char): Boolean = "aeiouAEIOU".indexOf(c) >= 0
}
