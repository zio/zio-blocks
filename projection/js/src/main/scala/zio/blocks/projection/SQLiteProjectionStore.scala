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
import zio.blocks.schema.Schema

class SQLiteProjectionStore[A: Schema: EntityPath] private (
  path: String,
  cache: TransactorCache
) extends ProjectionStore[A] {

  def insert(a: A): Task[Unit] =
    ZIO.fail(new UnsupportedOperationException("SQLiteProjectionStore is JVM-only"))

  def upsert(a: A): Task[Unit] =
    ZIO.fail(new UnsupportedOperationException("SQLiteProjectionStore is JVM-only"))

  def updateFields(entityId: String, updates: Chunk[FieldUpdate]): Task[Unit] =
    ZIO.fail(new UnsupportedOperationException("SQLiteProjectionStore is JVM-only"))

  def delete(entityId: String): Task[Unit] =
    ZIO.fail(new UnsupportedOperationException("SQLiteProjectionStore is JVM-only"))

  def truncate: Task[Unit] =
    ZIO.fail(new UnsupportedOperationException("SQLiteProjectionStore is JVM-only"))

  def findById(entityId: String): Task[Option[A]] =
    ZIO.fail(new UnsupportedOperationException("SQLiteProjectionStore is JVM-only"))

  def getLastProcessedSeq: Task[Long] =
    ZIO.fail(new UnsupportedOperationException("SQLiteProjectionStore is JVM-only"))

  def updateLastProcessedSeq(seq: Long): Task[Unit] =
    ZIO.fail(new UnsupportedOperationException("SQLiteProjectionStore is JVM-only"))

  def getSchemaHash: Task[Option[String]] =
    ZIO.fail(new UnsupportedOperationException("SQLiteProjectionStore is JVM-only"))

  def updateSchemaHash(hash: String): Task[Unit] =
    ZIO.fail(new UnsupportedOperationException("SQLiteProjectionStore is JVM-only"))
}

object SQLiteProjectionStore {

  def make[A: Schema: EntityPath](path: String, cache: TransactorCache): Task[SQLiteProjectionStore[A]] =
    ZIO.succeed(new SQLiteProjectionStore[A](path, cache))

  def makeSync[A: Schema: EntityPath](path: String, cache: TransactorCache): SQLiteProjectionStore[A] =
    new SQLiteProjectionStore[A](path, cache)
}
