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
 * Create, read, update and delete operations for the rows of one [[Table]],
 * keyed by a single primary-key column of type `ID`. Every operation runs in a
 * connection or transaction from a [[Transactor]] (`using DbCon`).
 *
 * `Repo.derived[E, ID]` finds the ID field in `E`'s schema;
 * `Repo.derived(idColumn, getId)` and `Repo.apply` take it explicitly, and a
 * subclass `class CustomerRepo extends Repo[Customer, Int]` derives it the same
 * way as `Repo.derived`.
 *
 * {{{
 * import zio.blocks.schema.Schema
 *
 * case class Product(id: Int, name: String, priceCents: Long)
 * object Product { implicit val schema: Schema[Product] = Schema.derived }
 *
 * val products = Repo.derived[Product, Int]
 *
 * val xa = JdbcTransactor.fromUrl("jdbc:sqlite::memory:", SqlDialect.SQLite)
 * val rows = xa.connect {
 *   products.table.createTable(SqlDialect.SQLite).update
 *   products.insert(Product(1, "Mug", 1200L))
 *   products.insert(Product(2, "Teapot", 3500L))
 *   products.update(Product(1, "Mug", 1500L))
 *   products.delete(2)
 *   products.all
 * }
 * // List(Product(1,Mug,1500))
 * }}}
 *
 * The column lists and quoted names are built once, when the `Repo` is created;
 * each call builds its statement from them. Every table and column name is
 * checked as an SQL identifier and written in double quotes, which PostgreSQL
 * and SQLite both accept.
 *
 * On empty input, [[insertAll]] throws `IllegalArgumentException` (an empty
 * `VALUES` list is invalid SQL), while [[insertBatch]],
 * [[insertOrUpdateBatch]], [[deleteAll]] and [[findAll]] return `0` or `Nil`
 * without touching the database. [[update]] returns `0` both when no row has
 * the ID and when the table has no column besides the ID.
 *
 * A `Repo` holds no mutable state, so one instance can serve several threads;
 * each thread needs its own `DbCon`.
 *
 * @see
 *   [[https://zioblocks.com/reference/sql/repo/ Repo]]
 */
abstract class Repo[E, ID] protected (metadata: Repo.Metadata[E, ID]) {

  /**
   * Derives the repository from the `Schema` of `E` and `ID` in scope, finding
   * the ID field as `Repo.derived[E, ID]` does. A subclass that extends
   * `Repo[E, ID]` without arguments calls it:
   *
   * {{{
   * import zio.blocks.schema.Schema
   *
   * case class User(id: Long, name: String)
   * object User { implicit val schema: Schema[User] = Schema.derived }
   *
   * final class UserRepo extends Repo[User, Long]
   *
   * val users    = new UserRepo
   * val table    = users.table.name // user
   * val idColumn = users.idColumn   // id
   * }}}
   */
  protected def this()(using
    schema: Schema[E],
    idSchema: Schema[ID],
    idCodec: DbCodec[ID]
  ) = this(Repo.derivedMetadata[E, ID])

  /** The table this repository operates on. */
  final val table: Table[E] = metadata.table

  /** The name of the primary-key column, as given (before quoting). */
  final val idColumn: String = metadata.idColumn

  /** The codec used to read and write the ID type. */
  final val idCodec: DbCodec[ID] = metadata.idCodec

  /** Extracts the primary key from an entity value. */
  final val getId: E => ID = metadata.getId

  require(
    idCodec.columnCount == 1,
    s"Repo requires a single-column ID, but '$idColumn' has ${idCodec.columnCount} columns"
  )

  private val validatedIdColumn = SqlIdentifier.validate("column", idColumn)

  require(
    table.columns.contains(validatedIdColumn),
    s"idColumn '$idColumn' (validated as '$validatedIdColumn') not found in table '${table.name}' columns: ${table.columns.mkString(", ")}"
  )

  // Quoted once at construction: every inline SQL string below reuses these,
  // so SELECT/INSERT/UPDATE/DELETE/IN prefixes share one quoting style.
  // `table.columns` and the table name arrive pre-validated from `Table`.
  private val allCols: String = table.columns.map(c => s""""$c"""").mkString(", ")
  private val tbl: String     = s""""${SqlIdentifier.validate("table", table.name)}""""
  // Raw (unquoted) ID column for value-level comparisons; SQL strings use the
  // quoted twin so `WHERE`/`ORDER BY`/conflict targets match the rest.
  private val quotedIdColumn: String = s""""$validatedIdColumn""""

  /**
   * The row codec of [[table]], the given that every statement of this `Repo`
   * decodes rows with.
   */
  private given codec: DbCodec[E] = table.codec

  // === Read Operations ===

  /** Returns every row of the table, in the order the database returns them. */
  final def all(using con: DbCon): List[E] = {
    val frag = Frag.literal(s"SELECT $allCols FROM $tbl")
    frag.query[E]
  }

  /**
   * Returns the rows whose primary key is one of `ids`, in the order the
   * database returns them; an ID with no row is skipped. Returns `Nil` for
   * empty `ids` without a query.
   *
   * The statement has one `?` per ID, and rendered SQL is cached per shape, so
   * many different ID counts fill that cache; [[pageAfter]] avoids this for
   * paging.
   */
  final def findAll(ids: Iterable[ID])(using con: DbCon): List[E] = {
    val idList = ids.toList
    if (idList.isEmpty) List.empty
    else {
      val allValues = idList.flatMap(id => idCodec.toDbValues(id)).toIndexedSeq
      val parts     = Repo.inListParts(s"SELECT $allCols FROM $tbl WHERE ($quotedIdColumn) IN (", allValues.size)
      Frag(parts, allValues).query[E]
    }
  }

  /**
   * Returns the row whose primary key is `id`, or `Maybe.absent` when there is
   * none.
   *
   * {{{
   * import zio.blocks.schema.Schema
   *
   * case class Customer(id: Int, name: String)
   * object Customer { implicit val schema: Schema[Customer] = Schema.derived }
   *
   * val customers = Repo.derived[Customer, Int]
   *
   * val xa = JdbcTransactor.fromUrl("jdbc:sqlite::memory:", SqlDialect.SQLite)
   * val (found, missing) = xa.connect {
   *   customers.table.createTable(SqlDialect.SQLite).update
   *   customers.insert(Customer(7, "Grace Hopper"))
   *   (customers.find(7).toOption, customers.find(8).toOption)
   * }
   * // (Some(Customer(7,Grace Hopper)),None)
   * }}}
   */
  final def find(id: ID)(using con: DbCon): Maybe[E] = {
    val frag = Frag(
      IndexedSeq(s"SELECT $allCols FROM $tbl WHERE $quotedIdColumn = ", ""),
      idCodec.toDbValues(id)
    )
    frag.queryOne[E]
  }

  /** Returns `true` if a row with primary key `id` exists. */
  final def exists(id: ID)(using con: DbCon): Boolean =
    find(id).isDefined

  /** Returns the number of rows in the table (`SELECT COUNT(*)`). */
  final def count(using con: DbCon): Long = {
    val frag = Frag.literal(s"SELECT COUNT(*) FROM $tbl")
    frag.queryOne[Long](using con, DbCodec.longCodec).getOrElse(0L)
  }

  // === Pagination ===

  /**
   * Returns up to `limit` rows whose primary key is greater than `cursorId`, in
   * ascending key order: `WHERE "id" > ? ORDER BY "id" ASC LIMIT n`. Pass the
   * last key of one page to get the next; the cursor row is not repeated.
   *
   * @throws java.lang.IllegalArgumentException
   *   if `limit <= 0`
   */
  final def pageAfter(cursorId: ID, limit: Int)(using con: DbCon): List[E] = {
    require(limit > 0, s"Repo.pageAfter: limit must be > 0, got $limit")
    val frag = Frag(
      IndexedSeq(
        s"SELECT $allCols FROM $tbl WHERE $quotedIdColumn > ",
        s" ORDER BY $quotedIdColumn ASC LIMIT $limit"
      ),
      idCodec.toDbValues(cursorId)
    )
    frag.query[E]
  }

  // === Write Operations ===

  /** Inserts `entity` and returns the affected row count, `1` on success. */
  final def insert(entity: E)(using con: DbCon): Int = {
    val values = codec.toDbValues(entity)
    val frag   = Repo.buildInsertFrag(table.name, table.columns, values)
    frag.update
  }

  /**
   * Inserts `entity`, then reads the row back by the first column of the
   * driver's generated keys, falling back to `entity`'s own ID when there are
   * none.
   *
   * The first generated-key column is not always the ID, a known bug in 0.0.56.
   * On SQLite it is the `rowid`, which equals the ID only when the ID column is
   * declared `INTEGER PRIMARY KEY`; on any other table, including one from
   * [[Table#createTable]], this returns whichever row has that `rowid` as its
   * ID. On PostgreSQL it is the table's first column, so when the ID is not the
   * first column the key is read from the wrong column: with a text first
   * column, the call throws `PSQLException: Bad value for type int`.
   *
   * @throws java.util.NoSuchElementException
   *   if no row is found after the insert
   */
  final def insertReturning(entity: E)(using con: DbCon): E = {
    val frag   = Repo.buildInsertFrag(table.name, table.columns, codec.toDbValues(entity))
    val keys   = frag.updateReturningKeys[ID](using con, idCodec)
    val result = Maybe.fromOption(keys.headOption).flatMap(find(_)).orElse(find(getId(entity)))
    result.getOrElse(
      throw new NoSuchElementException(s"Entity not found after insert in table $tbl")
    )
  }

  /**
   * Inserts `entities` with one prepared statement run as a JDBC batch, and
   * returns the total affected row count (a driver's "success, count unknown"
   * counts as 1). Returns `0` for empty `entities` without touching the
   * database.
   *
   * The logger receives one event with the SQL, the duration and the total
   * count, and no parameters.
   */
  final def insertBatch(entities: Iterable[E])(using con: DbCon): Int = {
    if (entities.isEmpty) return 0
    val first  = entities.head
    val values = codec.toDbValues(first)
    val sqlStr = Repo.buildInsertFrag(table.name, table.columns, values).sql(con.dialect)
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
          else if (count == Repo.SuccessNoInfo) 1
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
   * Inserts `entity`, or overwrites every other column of the row with the same
   * primary key, with `INSERT ... ON CONFLICT ("id") DO UPDATE SET ...`.
   *
   * The conflict target needs a `PRIMARY KEY` or `UNIQUE` constraint on the ID
   * column, which [[Table#createTable]] does not declare, a known bug in
   * 0.0.56; on such a table the database rejects the statement. Create the
   * table yourself:
   *
   * {{{
   * import zio.blocks.schema.Schema
   *
   * case class Product(id: Int, name: String, priceCents: Long)
   * object Product { implicit val schema: Schema[Product] = Schema.derived }
   *
   * val products = Repo.derived[Product, Int]
   *
   * val xa = JdbcTransactor.fromUrl("jdbc:sqlite::memory:", SqlDialect.SQLite)
   * val rows = xa.connect {
   *   sql"CREATE TABLE product (id INTEGER PRIMARY KEY, name TEXT NOT NULL, price_cents INTEGER NOT NULL)".update
   *   products.insertOrUpdate(Product(1, "Mug", 1200L))
   *   products.insertOrUpdate(Product(1, "Mug", 1500L))
   *   products.all
   * }
   * // List(Product(1,Mug,1500))
   * }}}
   *
   * @return
   *   the affected row count, `1` for an insert and for an update
   * @throws java.lang.IllegalArgumentException
   *   if the table has no column besides the ID
   */
  final def insertOrUpdate(entity: E)(using con: DbCon): Int = {
    val frag = Upsert.insertDoUpdate(table, entity, validatedIdColumn)
    frag.update
  }

  /**
   * Runs [[insertOrUpdate]] for each of `entities` as one JDBC batch and
   * returns the total affected row count, counted as in [[insertBatch]].
   * Returns `0` for empty `entities` without touching the database. Needs the
   * same `PRIMARY KEY` or `UNIQUE` constraint as [[insertOrUpdate]].
   *
   * @throws java.lang.IllegalArgumentException
   *   if the table has no column besides the ID
   */
  final def insertOrUpdateBatch(entities: Iterable[E])(using con: DbCon): Int = {
    if (entities.isEmpty) return 0
    val sqlStr = Upsert.insertDoUpdate(table, entities.head, validatedIdColumn).sql(con.dialect)
    // Hoisted once per batch: the upsert shape (columns, conflict target,
    // assignments) is identical for every row, so per-row work is only
    // `toDbValues` plus positional assignment projection. Param order matches
    // `Upsert.insertDoUpdate`: all insert values, then the DO UPDATE SET
    // assignment values in update-column order.
    val updateIdx: Array[Int] = table.columns.filter(_ != validatedIdColumn).map(table.columns.indexOf).toArray
    val start                 = System.nanoTime()
    try {
      val ps = con.connection.prepareStatement(sqlStr)
      try {
        entities.foreach { entity =>
          val vals       = table.codec.toDbValues(entity)
          val assignVals = updateIdx.map(i => vals(i)).toIndexedSeq
          Frag.writeParams(ps.paramWriter, vals)
          Frag.writeParams(ps.paramWriter, assignVals, vals.length + 1)
          ps.addBatch()
        }
        val counts = ps.executeBatch()
        val total  = counts.map { count =>
          if (count >= 0) count
          else if (count == Repo.SuccessNoInfo) 1
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
   * Inserts `rows` with one multi-row `INSERT ... VALUES (?, ?), (?, ?)`
   * statement and returns their IDs, taken from the rows with [[getId]], in
   * input order. Database-generated keys are not read.
   *
   * @throws java.lang.IllegalArgumentException
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
   * Sets every column except the ID to `entity`'s values in the row with
   * `entity`'s primary key. Returns the affected row count: `0` when no row has
   * that key, and `0` without a statement when the table has no column besides
   * the ID.
   */
  final def update(entity: E)(using con: DbCon): Int = {
    val entityValues  = codec.toDbValues(entity)
    val idValues      = idCodec.toDbValues(getId(entity))
    val updatePairs   = table.columns.zip(entityValues).filter(_._1 != validatedIdColumn)
    val updateColumns = updatePairs.map(_._1)
    val updateValues  = updatePairs.map(_._2)
    if (updateColumns.isEmpty) 0
    else {
      val frag = Repo.buildUpdateFrag(table.name, updateColumns, updateValues, validatedIdColumn, idValues)
      frag.update
    }
  }

  /**
   * Deletes the row with primary key `id` and returns the affected row count.
   */
  final def delete(id: ID)(using con: DbCon): Int = {
    val frag = Frag(
      IndexedSeq(s"DELETE FROM $tbl WHERE $quotedIdColumn = ", ""),
      idCodec.toDbValues(id)
    )
    frag.update
  }

  /**
   * Deletes the rows whose primary key is one of `ids` and returns the affected
   * row count. Returns `0` for empty `ids` without a statement. Like
   * [[findAll]], it renders one `?` per ID.
   */
  final def deleteAll(ids: Iterable[ID])(using con: DbCon): Int = {
    val idList = ids.toList
    if (idList.isEmpty) 0
    else {
      val allValues = idList.flatMap(id => idCodec.toDbValues(id)).toIndexedSeq
      val parts     = Repo.inListParts(s"DELETE FROM $tbl WHERE ($quotedIdColumn) IN (", allValues.size)
      Frag(parts, allValues).update
    }
  }

  /** Deletes every row of the table and returns the affected row count. */
  final def clear()(using con: DbCon): Int =
    Frag.literal(s"DELETE FROM $tbl").update
}

/** Constructors for [[Repo]]. */
object Repo {

  /**
   * Shared constant matching `java.sql.Statement.SUCCESS_NO_INFO` (-2). Defined
   * here to avoid `java.sql` references in the shared source set (breaks
   * Scala.js).
   */
  private val SuccessNoInfo: Int = -2

  /** What a [[Repo]] needs: its table, ID column, ID codec and ID getter. */
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

  /**
   * Returns a `Repo` over `table` whose primary key is the column `idColumn`,
   * read and written with `idCodec` and taken from a row with `getId`.
   *
   * @throws java.lang.IllegalArgumentException
   *   if `idCodec` has more than one column or `idColumn` is not a column of
   *   `table`
   */
  def apply[E, ID](
    table: Table[E],
    idColumn: String,
    idCodec: DbCodec[ID],
    getId: E => ID
  ): Repo[E, ID] = fromMetadata(Metadata(table, idColumn, idCodec, getId))

  /**
   * Derives the [[Table]] from `E`'s schema as `Table.derived[E]` does, with
   * `idColumn` as the primary key and `getId` to read it from a row.
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
   * Derives the [[Table]] from `E`'s schema with the name `tableName`, with
   * `idColumn` as the primary key and `getId` to read it from a row.
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
   * Derives a `Repo` from `E`'s schema, choosing as the primary key the first
   * rule that matches among the fields whose type is `ID`:
   *
   *   1. the one field with `@Modifier.id`;
   *   1. the only field of type `ID`;
   *   1. the field named `id`;
   *   1. the field named after the type, such as `customerId` for `Customer`.
   *
   * The ID column is the field's name in snake case, or its `@Modifier.rename`
   * name; the table is named as `Table.derived[E]` names it.
   *
   * {{{
   * import zio.blocks.schema.Schema
   *
   * case class Customer(customerId: Int, referrerId: Int, name: String)
   * object Customer { implicit val schema: Schema[Customer] = Schema.derived }
   *
   * val customers = Repo.derived[Customer, Int]
   * val idColumn  = customers.idColumn // customer_id
   * }}}
   *
   * @throws java.lang.IllegalArgumentException
   *   if `E` is not a case class, if no field matches, or if several fields
   *   have `@Modifier.id`
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

  /**
   * Builds the literal parts for `prefix (?, ..., ?)` with `size` bound
   * parameters: `prefix` followed by `size - 1` `", "` separators and a closing
   * `")"`. Shared by the `IN`-list executors (`findAll`, `deleteAll`) so every
   * call site mints the same shape for the same arity.
   */
  private[sql] def inListParts(prefix: String, size: Int): IndexedSeq[String] = {
    require(size >= 1, s"Repo.inListParts: size must be >= 1, got $size")
    IndexedSeq(prefix) ++ IndexedSeq.fill(size - 1)(", ") :+ ")"
  }

  /**
   * Builds `INSERT INTO "table" ("a", "b", ...) VALUES (?, ...)`, or
   * `INSERT INTO "table" DEFAULT VALUES` when `values` is empty.
   *
   * Identifiers are validated and double-quoted here, so every call site
   * (`Repo` executors, `Upsert` builders) renders the same quoted style both
   * dialects accept. Callers pass raw (unquoted) names with values aligned to
   * `columns` by position.
   */
  private[sql] def buildInsertFrag(
    tableName: String,
    columns: IndexedSeq[String],
    values: IndexedSeq[DbValue]
  ): Frag = {
    val t    = SqlIdentifier.validate("table", tableName)
    val cols = columns.map(c => SqlIdentifier.validate("column", c))
    if (values.isEmpty) Frag.literal(s"""INSERT INTO "$t" DEFAULT VALUES""")
    else {
      require(cols.size == values.size, "Repo.buildInsertFrag: columns/value count mismatch")
      val parts =
        IndexedSeq(s"""INSERT INTO "$t" (${cols.map(c => s""""$c"""").mkString(", ")}) VALUES (""") ++
          IndexedSeq.fill(values.size - 1)(", ") :+
          ")"
      Frag(parts, values)
    }
  }

  /**
   * Builds `UPDATE "table" SET "a" = ?, ... WHERE "id" = ?`.
   *
   * Identifiers are validated and double-quoted here (same contract as
   * [[buildInsertFrag]]).
   */
  private[sql] def buildUpdateFrag(
    tableName: String,
    columns: IndexedSeq[String],
    entityValues: IndexedSeq[DbValue],
    idColumn: String,
    idValues: IndexedSeq[DbValue]
  ): Frag = {
    require(columns.nonEmpty, "Cannot build UPDATE with no columns to set")
    require(columns.size == entityValues.size, "UPDATE column/value count mismatch")
    val t         = SqlIdentifier.validate("table", tableName)
    val cols      = columns.map(c => SqlIdentifier.validate("column", c))
    val idCol     = SqlIdentifier.validate("column", idColumn)
    val allValues = entityValues ++ idValues
    val partsB    = IndexedSeq.newBuilder[String]

    partsB += s"""UPDATE "$t" SET "${cols(0)}" = """

    var i = 1
    while (i < cols.size) {
      partsB += s""", "${cols(i)}" = """
      i += 1
    }

    partsB += s""" WHERE "$idCol" = """
    partsB += ""

    Frag(partsB.result(), allValues)
  }
}
