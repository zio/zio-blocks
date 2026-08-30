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

/**
 * Persistence for a single projection entity type `A`.
 *
 * Implementations are per-projection (one SQLite file per spec name or an
 * in-memory map in tests). Stores a watermark (`lastSeq`) and a schema hash
 * alongside the entity rows.
 */
trait ProjectionStore[A] {

  /** Insert a new entity; fails if the id already exists. */
  def insert(a: A): Task[Unit]

  /**
   * Insert or update an entity.
   *
   * On SQLite this uses `INSERT ... ON CONFLICT DO UPDATE` with a fallback to
   * `INSERT OR REPLACE` only for `SQLException`.
   */
  def upsert(a: A): Task[Unit]

  /**
   * Apply field-level updates atomically.
   *
   * Validates each field against the table allowlist and uses quoted
   * identifiers. Runs inside a single transaction (INSERT OR IGNORE for the
   * default row + N updates).
   */
  def updateFields(entityId: String, updates: Chunk[FieldUpdate]): Task[Unit]

  /** Delete the entity with the given id. */
  def delete(entityId: String): Task[Unit]

  /** Remove all entities from the store. */
  def truncate: Task[Unit]

  /** Find an entity by id. */
  def findById(entityId: String): Task[Option[A]]

  /** Watermark of the last successfully processed event seq. */
  def getLastProcessedSeq: Task[Long]

  /**
   * Persist the watermark.
   *
   * Uses `INSERT ... ON CONFLICT DO UPDATE` for atomicity (no
   * UPDATE-then-INSERT race).
   */
  def updateLastProcessedSeq(seq: Long): Task[Unit]

  /** Stored schema hash (for evolution detection). */
  def getSchemaHash: Task[Option[String]]

  /** Persist the schema hash (also ON CONFLICT). */
  def updateSchemaHash(hash: String): Task[Unit]

  /** Drop and recreate the underlying table (default truncates). */
  def recreateTable(): Task[Unit] = truncate

  /**
   * Add a column if it does not already exist.
   *
   * Validates `columnName` against `^[a-zA-Z_][a-zA-Z0-9_]*$` and uses quoted
   * identifiers.
   */
  def addColumn(columnName: String, sqlType: String): Task[Unit] =
    // reference params to satisfy -Wunused
    if (columnName.isEmpty && sqlType.isEmpty) ZIO.unit else ZIO.unit
}
