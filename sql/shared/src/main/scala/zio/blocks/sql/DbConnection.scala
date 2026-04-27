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

trait DbConnection extends AutoCloseable {
  def prepareStatement(sql: String): DbPreparedStatement
  def close(): Unit
  def isClosed: Boolean
  def setAutoCommit(autoCommit: Boolean): Unit
  def getAutoCommit: Boolean
  def commit(): Unit
  def rollback(): Unit
}

trait DbPreparedStatement extends AutoCloseable {
  def executeQuery(): DbResultSet
  def executeUpdate(): Int
  def close(): Unit
  def paramWriter: DbParamWriter
  def addBatch(): Unit
  def executeBatch(): Array[Int]
}

trait DbResultSet extends AutoCloseable {
  def next(): Boolean
  def close(): Unit
  def reader: DbResultReader
}
