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

final case class ColumnDef(name: String, sqlType: String, nullable: Boolean)

object Ddl {

  def createTable(tableName: String, columns: IndexedSeq[ColumnDef]): Frag = {
    val colDefs = columns.map { col =>
      val nullStr = if (col.nullable) "" else " NOT NULL"
      s"  ${col.name} ${col.sqlType}$nullStr"
    }
    Frag.const(s"CREATE TABLE IF NOT EXISTS $tableName (\n${colDefs.mkString(",\n")}\n)")
  }

  def dropTable(tableName: String): Frag =
    Frag.const(s"DROP TABLE IF EXISTS $tableName")
}
