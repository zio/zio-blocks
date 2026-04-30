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
import zio.blocks.schema.binding.Binding

/**
 * A type-safe repository that provides standard CRUD operations for entities of
 * type `E` identified by a primary key of type `ID`.
 *
 * `Repo` wraps a [[Table]] and a description of its identity column, and
 * generates all SQL at construction time so query strings are not re-built on
 * every call.
 *
 * ==Construction==
 * Prefer the smart constructors in the companion object:
 *   - [[Repo.apply]] — fully explicit (table, id column, codec, getter)
 *   - [[Repo.derived]] — macro-derived from a [[zio.blocks.schema.Schema]]
 *
 * ==Thread safety==
 * `Repo` instances are immutable and safe for concurrent use. Individual
 * operations require a `DbCon` or `DbTx` given context which is not shared
 * across threads unless the caller arranges it.
 *
 * @param table
 *   The [[Table]] this repository operates on.
 * @param idColumn
 *   The name of the primary-key column as it appears in SQL.
 * @param idCodec
 *   The [[DbCodec]] used to read and write the ID type.
 * @param getId
 *   Extracts the primary key from an entity value.
 */
class Repo[E, ID](
  val table: Table[E],
  val idColumn: String,
  val idCodec: DbCodec[ID],
  val getId: E => ID
) {
  require(
    idCodec.columnCount == 1,
    s"Repo requires a single-column ID, but '$idColumn' has ${idCodec.columnCount} columns"
  )

  private val allCols: String   = table.columns.mkString(", ")
  private val tbl: String       = table.name
  private val codec: DbCodec[E] = table.codec

  // === Read Operations ===

  /** Returns all rows in the table. */
  def findAll(using con: DbCon): List[E] = {
    val frag = Frag.literal(s"SELECT $allCols FROM $tbl")
    SqlOps.query[E](frag)(using con, codec)
  }

  /** Finds the row with the given primary key, or `None` if absent. */
  def findById(id: ID)(using con: DbCon): Option[E] = {
    val frag = Frag(
      IndexedSeq(s"SELECT $allCols FROM $tbl WHERE $idColumn = ", ""),
      idCodec.toDbValues(id)
    )
    SqlOps.queryOne[E](frag)(using con, codec)
  }

  /** Returns `true` if a row with `id` exists. */
  def existsById(id: ID)(using con: DbCon): Boolean =
    findById(id).isDefined

  /** Returns the total number of rows in the table. */
  def count(using con: DbCon): Long = {
    val frag = Frag.literal(s"SELECT COUNT(*) FROM $tbl")
    SqlOps.queryOne[Long](frag)(using con, DbCodec.longCodec).getOrElse(0L)
  }

  // === Write Operations ===

  /** Inserts `entity` and returns the affected row count (normally 1). */
  def insert(entity: E)(using con: DbCon): Int = {
    val values = codec.toDbValues(entity)
    val frag   = Repo.buildInsertFrag(tbl, allCols, values)
    SqlOps.update(frag)(using con)
  }

  /**
   * Inserts `entity` and returns the inserted row by re-querying it via the
   * generated or supplied primary key.
   *
   * @throws NoSuchElementException
   *   if the row cannot be found after insert.
   */
  def insertReturning(entity: E)(using con: DbCon): E = {
    val frag   = Repo.buildInsertFrag(tbl, allCols, codec.toDbValues(entity))
    val keys   = SqlOps.updateReturningKeys[ID](frag)(using con, idCodec)
    val result = keys.headOption.flatMap(findById(_)).orElse(findById(getId(entity)))
    result.getOrElse(
      throw new NoSuchElementException(s"Entity not found after insert in table $tbl")
    )
  }

  /**
   * Inserts all `entities` using a JDBC batch, which is significantly faster
   * than individual inserts for large collections. Returns the total affected
   * row count.
   */
  def insertAll(entities: Iterable[E])(using con: DbCon): Int = {
    if (entities.isEmpty) return 0
    val first  = entities.head
    val values = codec.toDbValues(first)
    val sqlStr = Repo.buildInsertFrag(tbl, allCols, values).sql(con.dialect)
    val start  = System.nanoTime()
    try {
      val ps = con.connection.prepareStatement(sqlStr)
      try {
        entities.foreach { entity =>
          val vals = codec.toDbValues(entity)
          SqlOps.writeParams(ps.paramWriter, vals)
          ps.addBatch()
        }
        val counts   = ps.executeBatch()
        val total    = counts.sum
        val duration = java.time.Duration.ofNanos(System.nanoTime() - start)
        con.logger.onSuccess(SqlLogger.SuccessEvent(sqlStr, IndexedSeq.empty, duration, total))
        total
      } finally ps.close()
    } catch {
      case e: Throwable =>
        val duration = java.time.Duration.ofNanos(System.nanoTime() - start)
        con.logger.onError(SqlLogger.ErrorEvent(sqlStr, IndexedSeq.empty, duration, e))
        throw e
    }
  }

  /**
   * Updates all non-ID columns for the row identified by `entity`'s primary
   * key. Returns the affected row count (0 if no row with that ID exists, 0 if
   * the entity has only an ID column).
   */
  def update(entity: E)(using con: DbCon): Int = {
    val entityValues  = codec.toDbValues(entity)
    val idValues      = idCodec.toDbValues(getId(entity))
    val updatePairs   = table.columns.zip(entityValues).filter(_._1 != idColumn)
    val updateColumns = updatePairs.map(_._1)
    val updateValues  = updatePairs.map(_._2)
    if (updateColumns.isEmpty) 0
    else {
      val frag = Repo.buildUpdateFrag(tbl, updateColumns, updateValues, idColumn, idValues)
      SqlOps.update(frag)(using con)
    }
  }

  /**
   * Deletes the row with the given primary key. Returns the affected row count.
   */
  def deleteById(id: ID)(using con: DbCon): Int = {
    val frag = Frag(
      IndexedSeq(s"DELETE FROM $tbl WHERE $idColumn = ", ""),
      idCodec.toDbValues(id)
    )
    SqlOps.update(frag)(using con)
  }

  /** Deletes the row corresponding to `entity`'s primary key. */
  def delete(entity: E)(using con: DbCon): Int =
    deleteById(getId(entity))

  /**
   * Deletes all rows in the table using `DELETE FROM <table>` (no `TRUNCATE`).
   */
  def truncate()(using con: DbCon): Int =
    SqlOps.update(Frag.literal(s"DELETE FROM $tbl"))(using con)
}

object Repo {

  /** Constructs a `Repo` from explicit components. */
  def apply[E, ID](
    table: Table[E],
    idColumn: String,
    idCodec: DbCodec[ID],
    getId: E => ID
  ): Repo[E, ID] = new Repo(table, idColumn, idCodec, getId)

  /**
   * Derives a `Repo` from `E`'s schema with a caller-supplied ID column name
   * and getter. The table name is derived from the schema type name using the
   * default singular snake_case policy.
   */
  def derived[E, ID](
    idColumn: String,
    getId: E => ID
  )(using schema: Schema[E], idCodec: DbCodec[ID]): Repo[E, ID] = {
    val codec = schema.deriving(DbCodecDeriver).derive
    new Repo(Table(Table.deriveTableName(schema), codec, TableMetadata.columnsFor(schema)), idColumn, idCodec, getId)
  }

  /**
   * Derives a `Repo` from `E`'s schema with an explicit table name, caller-
   * supplied ID column name, and getter.
   */
  def derived[E, ID](
    tableName: String,
    idColumn: String,
    getId: E => ID
  )(using schema: Schema[E], idCodec: DbCodec[ID]): Repo[E, ID] = {
    val codec = schema.deriving(DbCodecDeriver).derive
    new Repo(Table(tableName, codec, TableMetadata.columnsFor(schema)), idColumn, idCodec, getId)
  }

  /**
   * Fully auto-derives a `Repo` by inspecting `E`'s schema to locate the unique
   * field whose type matches `ID`. The ID column name respects
   * `@Modifier.rename` annotations; the table name uses the default naming
   * policy.
   *
   * Fails at runtime with [[IllegalArgumentException]] if:
   *   - `E` is not a record (case class)
   *   - No field of type `ID` exists
   *   - Multiple fields of type `ID` exist (use the explicit overload instead)
   */
  def derived[E, ID](using schema: Schema[E], idSchema: Schema[ID], idCodec: DbCodec[ID]): Repo[E, ID] = {
    val record = schema.reflect match {
      case r: Reflect.Record[_, _] => r.asInstanceOf[Reflect.Record[Binding, E]]
      case _                       =>
        throw new IllegalArgumentException(
          s"Repo.derived requires a record (case class) Schema, got ${schema.reflect}"
        )
    }

    val targetTypeId   = idSchema.reflect.typeId
    val matchingFields = record.fields.zipWithIndex.filter { case (field, _) =>
      field.value.typeId == targetTypeId
    }

    matchingFields match {
      case IndexedSeq((field, idx)) =>
        val idColumn = field.modifiers.collectFirst { case rename: Modifier.rename => rename.name }
          .getOrElse(SqlNameMapper.SnakeCase(field.name))
        val getId: E => ID = entity => entity.asInstanceOf[Product].productElement(idx).asInstanceOf[ID]
        val codec          = schema.deriving(DbCodecDeriver).derive
        new Repo(
          Table(Table.deriveTableName(schema), codec, TableMetadata.columnsFor(schema)),
          idColumn,
          idCodec,
          getId
        )

      case empty if empty.isEmpty =>
        throw new IllegalArgumentException(
          s"No field of type ${targetTypeId} found in ${schema.reflect.typeId}. " +
            "Use Repo.derived(idColumn, getId) to specify the ID field explicitly."
        )

      case multiple =>
        val names = multiple.map(_._1.name).mkString(", ")
        throw new IllegalArgumentException(
          s"Multiple fields of type ${targetTypeId} found in ${schema.reflect.typeId}: $names. " +
            "Use Repo.derived(idColumn, getId) to specify the ID field explicitly."
        )
    }
  }

  private[sql] def buildInsertFrag(
    tableName: String,
    allColumns: String,
    values: IndexedSeq[DbValue]
  ): Frag =
    if (values.isEmpty) Frag.literal(s"INSERT INTO $tableName DEFAULT VALUES")
    else {
      val parts =
        IndexedSeq(s"INSERT INTO $tableName ($allColumns) VALUES (") ++
          IndexedSeq.fill(values.size - 1)(", ") :+
          ")"
      Frag(parts, values)
    }

  private[sql] def buildUpdateFrag(
    tableName: String,
    columns: IndexedSeq[String],
    entityValues: IndexedSeq[DbValue],
    idColumn: String,
    idValues: IndexedSeq[DbValue]
  ): Frag = {
    require(columns.nonEmpty, "Cannot build UPDATE with no columns to set")
    require(columns.size == entityValues.size, "UPDATE column/value count mismatch")
    val allValues = entityValues ++ idValues
    val partsB    = IndexedSeq.newBuilder[String]

    partsB += s"UPDATE $tableName SET ${columns(0)} = "

    var i = 1
    while (i < columns.size) {
      partsB += s", ${columns(i)} = "
      i += 1
    }

    partsB += s" WHERE $idColumn = "
    partsB += ""

    Frag(partsB.result(), allValues)
  }
}
