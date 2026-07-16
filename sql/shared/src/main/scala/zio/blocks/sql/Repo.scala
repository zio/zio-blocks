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
import zio.blocks.maybe.Maybe

/**
 * A type-safe repository that provides standard CRUD operations for entities of
 * type `E` identified by a primary key of type `ID`.
 *
 * `Repo` wraps a [[Table]] and a description of its identity column, and
 * generates all SQL at construction time so query strings are not re-built on
 * every call.
 *
 * ==Construction==
 * Use the companion constructors, or subclass `Repo` directly:
 *   - [[Repo.apply]] — fully explicit (table, id column, codec, getter)
 *   - [[Repo.derived]] — derived from a [[zio.blocks.schema.Schema]]
 *   - `class UserRepo extends Repo[User, UserId]` — derives from the contextual
 *     `Schema` and `DbCodec` and inherits all default CRUD operations.
 *
 * ==Thread safety==
 * `Repo` is immutable and safe for concurrent use. Individual operations
 * require a `DbCon` or `DbTx` given context which is not shared across threads
 * unless the caller arranges it.
 */
abstract class Repo[E, ID] protected (metadata: Repo.Metadata[E, ID]) {

  /**
   * Derives a repository directly from the contextual schema and ID codec. This
   * is the constructor invoked when subclassing `Repo`:
   *
   * {{{
   * final class UserRepo extends Repo[User, UserId]
   * }}}
   */
  protected def this()(using schema: Schema[E], idSchema: Schema[ID], idCodec: DbCodec[ID]) =
    this(Repo.derivedMetadata[E, ID])

  /** The table this repository operates on. */
  val table: Table[E] = metadata.table

  /** The name of the primary-key column as it appears in SQL. */
  val idColumn: String = metadata.idColumn

  /** The codec used to read and write the ID type. */
  val idCodec: DbCodec[ID] = metadata.idCodec

  /** Extracts the primary key from an entity value. */
  val getId: E => ID = metadata.getId

  require(
    idCodec.columnCount == 1,
    s"Repo requires a single-column ID, but '$idColumn' has ${idCodec.columnCount} columns"
  )

  private val validatedIdColumn = SqlIdentifier.validate("column", idColumn)

  require(
    table.columns.contains(validatedIdColumn),
    s"idColumn '$idColumn' (validated as '$validatedIdColumn') not found in table '${table.name}' columns: ${table.columns.mkString(", ")}"
  )

  private val allCols: String = table.columns.mkString(", ")
  private val tbl: String     = table.name

  /**
   * The entity codec, exposed for internal Frag operations.
   *
   * Note: This is a public `given` to be available for implicit search in
   * `Frag.values` and `frag.query` calls within `Repo` methods. It is not
   * intended for direct external use; the public API is the CRUD methods.
   */
  given codec: DbCodec[E] = table.codec

  // === Read Operations ===

  /** Returns all rows in the table. */
  final def all(using con: DbCon): List[E] = {
    val frag = Frag.literal(s"SELECT $allCols FROM $tbl")
    frag.query[E]
  }

  /** Returns the rows whose primary keys match the given IDs. */
  final def findAll(ids: Iterable[ID])(using con: DbCon): List[E] = {
    val idList = ids.toList
    if (idList.isEmpty) List.empty
    else {
      val allValues = idList.flatMap(id => idCodec.toDbValues(id)).toIndexedSeq
      val parts     = IndexedSeq(s"SELECT $allCols FROM $tbl WHERE ($validatedIdColumn) IN (") ++
        IndexedSeq.fill(allValues.size - 1)(", ") :+ ")"
      Frag(parts, allValues).query[E]
    }
  }

  /** Finds the row with the given primary key. */
  final def find(id: ID)(using con: DbCon): Maybe[E] = {
    val frag = Frag(
      IndexedSeq(s"SELECT $allCols FROM $tbl WHERE $validatedIdColumn = ", ""),
      idCodec.toDbValues(id)
    )
    frag.queryOne[E]
  }

  /** Returns `true` if a row with `id` exists. */
  final def exists(id: ID)(using con: DbCon): Boolean =
    find(id).isDefined

  /** Returns the total number of rows in the table. */
  final def count(using con: DbCon): Long = {
    val frag = Frag.literal(s"SELECT COUNT(*) FROM $tbl")
    frag.queryOne[Long](using con, DbCodec.longCodec).getOrElse(0L)
  }

  // === Write Operations ===

  /** Inserts `entity` and returns the affected row count (normally 1). */
  final def insert(entity: E)(using con: DbCon): Int = {
    val values = codec.toDbValues(entity)
    val frag   = Repo.buildInsertFrag(tbl, allCols, values)
    frag.update
  }

  /**
   * Inserts `entity` and returns the inserted row by re-querying it via the
   * generated or supplied primary key.
   *
   * @throws NoSuchElementException
   *   if the row cannot be found after insert.
   */
  final def insertReturning(entity: E)(using con: DbCon): E = {
    val frag   = Repo.buildInsertFrag(tbl, allCols, codec.toDbValues(entity))
    val keys   = frag.updateReturningKeys[ID](using con, idCodec)
    val result = Maybe.fromOption(keys.headOption).flatMap(find(_)).orElse(find(getId(entity)))
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
  final def insertBatch(entities: Iterable[E])(using con: DbCon): Int = {
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
          Frag.writeParams(ps.paramWriter, vals)
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
  final def insertAll(rows: Seq[E])(using con: DbCon): Seq[ID] = {
    require(rows.nonEmpty, "Repo.insertAll: rows must be non-empty")
    val valuesFrag = Frag.values(rows)
    val frag       = Frag.literal(s"INSERT INTO $tbl ($allCols) VALUES ") ++ valuesFrag
    frag.update
    rows.map(getId)
  }

  /**
   * Updates all non-ID columns for the row identified by `entity`'s primary
   * key. Returns the affected row count (0 if no row with that ID exists, 0 if
   * the entity has only an ID column).
   */
  final def update(entity: E)(using con: DbCon): Int = {
    val entityValues  = codec.toDbValues(entity)
    val idValues      = idCodec.toDbValues(getId(entity))
    val updatePairs   = table.columns.zip(entityValues).filter(_._1 != validatedIdColumn)
    val updateColumns = updatePairs.map(_._1)
    val updateValues  = updatePairs.map(_._2)
    if (updateColumns.isEmpty) 0
    else {
      val frag = Repo.buildUpdateFrag(tbl, updateColumns, updateValues, validatedIdColumn, idValues)
      frag.update
    }
  }

  /**
   * Deletes the row with the given primary key. Returns the affected row count.
   */
  final def delete(id: ID)(using con: DbCon): Int = {
    val frag = Frag(
      IndexedSeq(s"DELETE FROM $tbl WHERE $validatedIdColumn = ", ""),
      idCodec.toDbValues(id)
    )
    frag.update
  }

  /**
   * Deletes an entity by extracting its ID.
   *
   * Note: This method is not provided because it would clash with
   * `delete(id: ID)` after JVM type erasure when `ID` =:= `E`. Users should
   * call `repo.delete(repo.getId(entity))` explicitly.
   */

  /**
   * Deletes the rows with the given primary keys. Returns the total affected
   * row count.
   */
  final def deleteAll(ids: Iterable[ID])(using con: DbCon): Int = {
    val idList = ids.toList
    if (idList.isEmpty) 0
    else {
      val allValues = idList.flatMap(id => idCodec.toDbValues(id)).toIndexedSeq
      val parts     = IndexedSeq(s"DELETE FROM $tbl WHERE ($validatedIdColumn) IN (") ++
        IndexedSeq.fill(allValues.size - 1)(", ") :+ ")"
      Frag(parts, allValues).update
    }
  }

  /** Deletes all rows in the table using `DELETE FROM <table>`. */
  final def clear()(using con: DbCon): Int =
    Frag.literal(s"DELETE FROM $tbl").update
}

object Repo {

  private[sql] final case class Metadata[E, ID](
    table: Table[E],
    idColumn: String,
    idCodec: DbCodec[ID],
    getId: E => ID
  )

  /** The default concrete [[Repo]] backed by resolved [[Metadata]]. */
  private final class DerivedRepo[E, ID](metadata: Metadata[E, ID]) extends Repo[E, ID](metadata)

  private def fromMetadata[E, ID](metadata: Metadata[E, ID]): Repo[E, ID] =
    new DerivedRepo[E, ID](metadata)

  /** Constructs a `Repo` from explicit components. */
  def apply[E, ID](
    table: Table[E],
    idColumn: String,
    idCodec: DbCodec[ID],
    getId: E => ID
  ): Repo[E, ID] = fromMetadata(Metadata(table, idColumn, idCodec, getId))

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
    fromMetadata(
      Metadata(Table(Table.deriveTableName(schema), codec, TableMetadata.columnsFor(schema)), idColumn, idCodec, getId)
    )
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
    fromMetadata(Metadata(Table(tableName, codec, TableMetadata.columnsFor(schema)), idColumn, idCodec, getId))
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
  def derived[E, ID](using schema: Schema[E], idSchema: Schema[ID], idCodec: DbCodec[ID]): Repo[E, ID] =
    fromMetadata(derivedMetadata[E, ID])

  private[sql] def derivedMetadata[E, ID](using
    schema: Schema[E],
    idSchema: Schema[ID],
    idCodec: DbCodec[ID]
  ): Metadata[E, ID] = {
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

    def buildMetadata(field: Term[Binding, ?, ?], idx: Int): Metadata[E, ID] = {
      val idColumn = field.modifiers.collectFirst { case r: Modifier.rename => r.name }
        .getOrElse(SqlNameMapper.SnakeCase(field.name))
      val getId: E => ID = entity => entity.asInstanceOf[Product].productElement(idx).asInstanceOf[ID]
      val codec          = schema.deriving(DbCodecDeriver).derive
      Metadata(
        Table(Table.deriveTableName(schema), codec, TableMetadata.columnsFor(schema)),
        idColumn,
        idCodec,
        getId
      )
    }

    idAnnotatedMatching match {
      case IndexedSeq((field, idx)) =>
        return buildMetadata(field, idx)

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
        return buildMetadata(field, idx)

      case empty if empty.isEmpty =>
      case _                      =>
    }

    allFields.find(_._1.name == "id").filter(_._1.value.typeId == targetTypeId) match {
      case Some((field, idx)) =>
        return buildMetadata(field, idx)
      case None =>
    }

    val simpleName     = schema.reflect.typeId.name.split('.').last
    val decapitalized  = simpleName.head.toLower.toString + simpleName.tail
    val conventionName = decapitalized + "Id"
    allFields.find(_._1.name == conventionName).filter(_._1.value.typeId == targetTypeId) match {
      case Some((field, idx)) =>
        return buildMetadata(field, idx)
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
