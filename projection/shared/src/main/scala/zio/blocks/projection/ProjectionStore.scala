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
 * Where the rows of one read model `A` are kept.
 *
 * A store holds the rows, each under the entity ID read from its ID field (see
 * [[EntityPath]]), and two values the engine keeps next to them: the watermark,
 * which is the sequence number of the last event applied to the rows, and the
 * schema hash, a fingerprint of the shape of `A` that tells the engine when the
 * rows are out of date.
 *
 * The engine applies the [[ProjectionAction]] that a handler returns with one
 * of these methods, and reads a row back with `findById`.
 * [[SQLiteProjectionStore]] keeps the rows in a SQLite file, and
 * `testing.InMemoryProjectionStore` keeps them in memory, for tests.
 *
 * @tparam A
 *   the type of a row: a case class with an ID field
 */
trait ProjectionStore[A] {

  /**
   * Adds `a` as a new row, under the ID read from its ID field.
   *
   * Fails if a row with that ID already exists.
   *
   * @param a
   *   the row to add
   */
  def insert(a: A): Task[Unit]

  /**
   * Adds `a` as a new row, or replaces the row that has the same ID.
   *
   * @param a
   *   the row to add or replace
   */
  def upsert(a: A): Task[Unit]

  /**
   * Applies `updates` to the row with the ID `entityId`, as one change.
   *
   * If there is no such row yet, it is first created with a default value in
   * each column, so that, for example, the first increment of a counter gives
   * `1`. The updates are applied in order.
   *
   * @param entityId
   *   the ID of the row to change
   * @param updates
   *   the changes to apply; their field names are column names, in snake_case,
   *   such as `user_count`. An empty chunk does nothing.
   */
  def updateFields(entityId: String, updates: Chunk[FieldUpdate]): Task[Unit]

  /**
   * Removes the row with the ID `entityId`, if there is one.
   *
   * @param entityId
   *   the ID of the row to remove
   */
  def delete(entityId: String): Task[Unit]

  /**
   * Removes every row. The watermark and the schema hash are kept.
   */
  def truncate: Task[Unit]

  /**
   * Reads the row with the ID `entityId`.
   *
   * @param entityId
   *   the ID of the row to read
   * @return
   *   the row, or `None` if there is none
   */
  def findById(entityId: String): Task[Option[A]]

  /**
   * The watermark: the sequence number of the last event applied to the rows,
   * or `0` if none has been applied yet. The engine reads it at startup, to
   * continue after that event.
   */
  def getLastProcessedSeq: Task[Long]

  /**
   * Saves `seq` as the watermark. The engine calls it after it applies an
   * event.
   *
   * @param seq
   *   the sequence number of the last event applied to the rows
   */
  def updateLastProcessedSeq(seq: Long): Task[Unit]

  /**
   * The schema hash saved with the rows, or `None` if none has been saved yet,
   * as in a new store.
   */
  def getSchemaHash: Task[Option[String]]

  /**
   * Saves `hash` as the schema hash of the rows. The engine calls it once the
   * rows match the current shape of `A`.
   *
   * @param hash
   *   the fingerprint of the shape of `A`, from [[SchemaHash]]
   */
  def updateSchemaHash(hash: String): Task[Unit]

  /**
   * Starts the rows over for a new shape of `A`. A full rebuild calls it before
   * it replays the events.
   *
   * The default only removes the rows with `truncate`.
   * [[SQLiteProjectionStore]] drops the table and creates it again for the
   * current shape of `A`.
   */
  def recreateTable(): Task[Unit] = truncate

  /**
   * Adds the column `columnName`, if the rows do not have it yet. The migration
   * shortcut calls it for each field that a migration adds.
   *
   * The default does nothing. [[SQLiteProjectionStore]] checks the name against
   * `^[a-zA-Z_][a-zA-Z0-9_]*$` and runs `ALTER TABLE ... ADD COLUMN`.
   *
   * @param columnName
   *   the name of the column, in snake_case
   * @param sqlType
   *   the SQL type of the column, such as `TEXT`
   */
  def addColumn(columnName: String, sqlType: String): Task[Unit] =
    // reference params to satisfy -Wunused
    if (columnName.isEmpty && sqlType.isEmpty) ZIO.unit else ZIO.unit
}
