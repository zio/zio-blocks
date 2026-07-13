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
 */
abstract class Repo[E, ID] {

  /** The table this repository operates on. */
  def table: Table[E]

  /** The name of the primary-key column as it appears in SQL. */
  def idColumn: String

  /** The codec used to read and write the ID type. */
  def idCodec: DbCodec[ID]

  /** Extracts the primary key from an entity value. */
  def getId: E => ID

  // === Read Operations ===

  /** Returns all rows in the table. */
  def findAll(using con: DbCon): List[E]

  /** Finds a row by its primary key, or `None` if absent. */
  def findById(id: ID)(using con: DbCon): Option[E]

  /** Returns `true` if a row with the given primary key exists. */
  def existsById(id: ID)(using con: DbCon): Boolean

  /** Returns the total number of rows in the table. */
  def count(using con: DbCon): Long

  // === Write Operations ===

  /** Inserts an entity and returns the affected row count (normally 1). */
  def insert(entity: E)(using con: DbCon): Int

  /** Inserts an entity and returns the inserted row. */
  def insertReturning(entity: E)(using con: DbCon): E

  /**
   * Inserts multiple entities using a JDBC batch and returns the total affected
   * row count.
   */
  def insertBatch(entities: Iterable[E])(using con: DbCon): Int

  /**
   * Inserts multiple entities using a multi-row INSERT and returns their
   * primary keys in input order.
   */
  def insertAll(rows: Seq[E])(using con: DbCon): Seq[ID]

  /**
   * Updates all non-ID columns for the row identified by the entity's primary
   * key. Returns the affected row count.
   */
  def update(entity: E)(using con: DbCon): Int

  /** Deletes a row by its primary key. Returns the affected row count. */
  def deleteById(id: ID)(using con: DbCon): Int

  /** Deletes the row corresponding to the entity's primary key. */
  def delete(entity: E)(using con: DbCon): Int

  /** Deletes all rows in the table. */
  def truncate()(using con: DbCon): Int
}

/** The default JDBC-backed implementation of [[Repo]]. */
final case class RepoImpl[E, ID](
  val table: Table[E],
  val idColumn: String,
  val idCodec: DbCodec[ID],
  val getId: E => ID
) extends Repo[E, ID] {
  require(
    idCodec.columnCount == 1,
    s"Repo requires a single-column ID, but '$idColumn' has ${idCodec.columnCount} columns"
  )

  private val validatedIdColumn = SqlIdentifier.validate("column", idColumn)

  require(
    table.columns.contains(validatedIdColumn),
    s"idColumn '$idColumn' (validated as '$validatedIdColumn') not found in table '${table.name}' columns: ${table.columns.mkString(", ")}"
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
      IndexedSeq(s"SELECT $allCols FROM $tbl WHERE $validatedIdColumn = ", ""),
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
   *
   * Batch logging records the rendered SQL, duration, and total affected row
   * count. Individual parameter lists are omitted because a batch may contain a
   * large number of rows.
   */
  def insertBatch(entities: Iterable[E])(using con: DbCon): Int = {
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
        val counts = ps.executeBatch()
        val total  = counts.map { count =>
          if (count >= 0) count
          else if (count == java.sql.Statement.SUCCESS_NO_INFO) 1
          else 0
        }.sum
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
   * Inserts multiple entities using a single multi-row INSERT and returns the
   * primary keys of the inserted entities in input order.
   *
   * Distinct from [[insertBatch]] which uses a JDBC batch and returns row
   * counts. `insertAll` uses a single `VALUES (?, ?), (?, ?)` statement for
   * efficiency, then returns the IDs extracted from the entities via `getId`.
   * This is suitable when the caller supplies the primary keys explicitly (as
   * opposed to relying on database auto-generation).
   *
   * @throws IllegalArgumentException
   *   if `rows` is empty
   */
  def insertAll(rows: Seq[E])(using con: DbCon): Seq[ID] = {
    require(rows.nonEmpty, "Repo.insertAll: rows must be non-empty")
    val valuesFrag = Frag.values(rows)(using codec)
    val frag       = Frag.literal(s"INSERT INTO $tbl ($allCols) VALUES ") ++ valuesFrag
    SqlOps.update(frag)(using con)
    rows.map(getId)
  }

  /**
   * Updates all non-ID columns for the row identified by `entity`'s primary
   * key. Returns the affected row count (0 if no row with that ID exists, 0 if
   * the entity has only an ID column).
   */
  def update(entity: E)(using con: DbCon): Int = {
    val entityValues  = codec.toDbValues(entity)
    val idValues      = idCodec.toDbValues(getId(entity))
    val updatePairs   = table.columns.zip(entityValues).filter(_._1 != validatedIdColumn)
    val updateColumns = updatePairs.map(_._1)
    val updateValues  = updatePairs.map(_._2)
    if (updateColumns.isEmpty) 0
    else {
      val frag = Repo.buildUpdateFrag(tbl, updateColumns, updateValues, validatedIdColumn, idValues)
      SqlOps.update(frag)(using con)
    }
  }

  /**
   * Deletes the row with the given primary key. Returns the affected row count.
   */
  def deleteById(id: ID)(using con: DbCon): Int = {
    val frag = Frag(
      IndexedSeq(s"DELETE FROM $tbl WHERE $validatedIdColumn = ", ""),
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
  ): Repo[E, ID] = RepoImpl(table, idColumn, idCodec, getId)

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
    RepoImpl(Table(Table.deriveTableName(schema), codec, TableMetadata.columnsFor(schema)), idColumn, idCodec, getId)
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
    RepoImpl(Table(tableName, codec, TableMetadata.columnsFor(schema)), idColumn, idCodec, getId)
  }

  /**
   * Fully auto-derives a `Repo` by inspecting `E`'s schema to locate the ID
   * field using a 4-priority rule:
   *   1. `@Modifier.id` annotation on a field whose type matches `ID`
   *   2. Unique field whose type matches `ID` (previously the only strategy)
   *   3. Field literally named `"id"` whose type matches `ID`
   *   4. Field named `<entity>Id` (e.g. `userId` for entity `User`) whose type
   *      matches `ID`
   *
   * The ID column name respects `@Modifier.rename` annotations; the table name
   * uses the default naming policy.
   *
   * Fails at runtime with [[IllegalArgumentException]] if:
   *   - `E` is not a record (case class)
   *   - No field can be resolved by any of the four strategies
   *   - Multiple fields match the same priority (ambiguous `@Modifier.id`)
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
    val allFields      = record.fields.zipWithIndex
    val matchingFields = allFields.filter { case (field, _) =>
      field.value.typeId == targetTypeId
    }

    // Priority 1: @Modifier.id annotation (among type-matching fields)
    val idAnnotatedMatching = matchingFields.filter { case (field, _) =>
      field.modifiers.exists(_.isInstanceOf[Modifier.id])
    }

    def buildRepo(field: Term[Binding, ?, ?], idx: Int): Repo[E, ID] = {
      val idColumn = field.modifiers.collectFirst { case r: Modifier.rename => r.name }
        .getOrElse(SqlNameMapper.SnakeCase(field.name))
      val getId: E => ID = entity => entity.asInstanceOf[Product].productElement(idx).asInstanceOf[ID]
      val codec          = schema.deriving(DbCodecDeriver).derive
      RepoImpl(
        Table(Table.deriveTableName(schema), codec, TableMetadata.columnsFor(schema)),
        idColumn,
        idCodec,
        getId
      )
    }

    idAnnotatedMatching match {
      case IndexedSeq((field, idx)) =>
        return buildRepo(field, idx)

      case multiple if multiple.size > 1 =>
        val names = multiple.map(_._1.name).mkString(", ")
        throw new IllegalArgumentException(
          s"Multiple @Modifier.id-annotated fields of type ${targetTypeId} found in ${schema.reflect.typeId}: $names. " +
            "Use Repo.derived(idColumn, getId) to specify the ID field explicitly."
        )

      case _ =>
    }

    // Priority 2: Unique type match
    matchingFields match {
      case IndexedSeq((field, idx)) =>
        return buildRepo(field, idx)

      case empty if empty.isEmpty =>
      case multiple               =>
    }

    allFields.find(_._1.name == "id").filter(_._1.value.typeId == targetTypeId) match {
      case Some((field, idx)) =>
        return buildRepo(field, idx)
      case None =>
    }

    val simpleName     = schema.reflect.typeId.name.split('.').last
    val decapitalized  = simpleName.head.toLower.toString + simpleName.tail
    val conventionName = decapitalized + "Id"
    allFields.find(_._1.name == conventionName).filter(_._1.value.typeId == targetTypeId) match {
      case Some((field, idx)) =>
        return buildRepo(field, idx)
      case None =>
    }

    if (matchingFields.size > 1) {
      val names = matchingFields.map(_._1.name).mkString(", ")
      throw new IllegalArgumentException(
        s"Multiple fields of type ${targetTypeId} found in ${schema.reflect.typeId}: $names. " +
          "None matched the name-based fallbacks (\"id\" or \"<entity>Id\"). " +
          "Use @Modifier.id on the intended field or use Repo.derived(idColumn, getId) to specify explicitly."
      )
    }

    throw new IllegalArgumentException(
      s"No field of type ${targetTypeId} found in ${schema.reflect.typeId}. " +
        "Use Repo.derived(idColumn, getId) to specify the ID field explicitly."
    )
    }

    throw new IllegalArgumentException(
      s"No field of type ${targetTypeId} found in ${schema.reflect.typeId}. " +
        "Use Repo.derived(idColumn, getId) to specify the ID field explicitly."
    )
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
