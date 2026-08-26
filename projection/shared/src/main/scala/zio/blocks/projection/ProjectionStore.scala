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

package zio.blocks.projection

import zio.*
import zio.blocks.chunk.Chunk

trait ProjectionStore[A] {

  def insert(a: A): Task[Unit]

  def upsert(a: A): Task[Unit]

  def updateFields(entityId: String, updates: Chunk[FieldUpdate]): Task[Unit]

  def delete(entityId: String): Task[Unit]

  def truncate: Task[Unit]

  def findById(entityId: String): Task[Option[A]]

  def getLastProcessedSeq: Task[Long]

  def updateLastProcessedSeq(seq: Long): Task[Unit]

  def getSchemaHash: Task[Option[String]]

  def updateSchemaHash(hash: String): Task[Unit]

  def recreateTable(): Task[Unit] = truncate

  def addColumn(columnName: String, sqlType: String): Task[Unit] = {
    val _ = (columnName, sqlType)
    ZIO.unit
  }
}
