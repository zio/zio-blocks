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

/**
 * Builds `INSERT ... ON CONFLICT` statements (upserts) as [[Frag]]s, in the
 * syntax PostgreSQL and SQLite share. `insertDoNothing` and `insertDoUpdate`
 * take a [[Table]] and a row; the lower-level builders take column names and
 * [[DbValue]]s. Every name is checked as an SQL identifier
 * (`IllegalArgumentException` otherwise) and written in double quotes, and
 * every value binds as a `?` parameter.
 *
 * {{{
 * import zio.blocks.schema.Schema
 *
 * case class Product(sku: String, name: String, priceCents: Long)
 * object Product { implicit val schema: Schema[Product] = Schema.derived }
 *
 * val products = Table.derived[Product]
 * val upsert   = Upsert.insertDoUpdate(products, Product("MUG-1", "Mug", 1200L), "sku")
 * val sqlText  = upsert.sql(SqlDialect.PostgreSQL)
 * // INSERT INTO "product" ("sku", "name", "price_cents") VALUES (?, ?, ?)
 * //   ON CONFLICT ("sku") DO UPDATE SET "name" = ?, "price_cents" = ?
 * }}}
 *
 * The conflict column needs a `PRIMARY KEY` or `UNIQUE` constraint, which
 * `Table#createTable` does not declare, a known bug in 0.0.56.
 */
object Upsert {

  // -- suffix builders -----------------------------------------------------------------

  /**
   * Returns the clause ` ON CONFLICT ("<conflictColumn>") DO NOTHING`, to
   * append to an `INSERT`.
   */
  def doNothingSuffix(conflictColumn: String): Frag = {
    val c = SqlIdentifier.validate("column", conflictColumn)
    Frag(IndexedSeq(s""" ON CONFLICT ("$c") DO NOTHING"""), IndexedSeq.empty)
  }

  /** The same as [[doNothingSuffix]]. */
  def buildDoNothingSuffix(conflictColumn: String): Frag =
    doNothingSuffix(conflictColumn)

  /**
   * Returns the clause
   * ` ON CONFLICT ("<conflictColumn>") DO UPDATE SET "<column>" = ?, ...`, one
   * assignment per entry of `assignments`, whose values bind in order.
   *
   * @throws java.lang.IllegalArgumentException
   *   if `assignments` is empty
   */
  def doUpdateSuffix(
    conflictColumn: String,
    assignments: IndexedSeq[(String, DbValue)]
  ): Frag = {
    val c = SqlIdentifier.validate("column", conflictColumn)
    if (assignments.isEmpty)
      throw new IllegalArgumentException("Upsert DO UPDATE requires at least one assignment column")
    val validatedAssignments = assignments.map { case (col, _) =>
      SqlIdentifier.validate("column", col)
    }
    val params = assignments.map(_._2)
    val parts  = IndexedSeq.newBuilder[String]
    parts += s""" ON CONFLICT ("$c") DO UPDATE SET "${validatedAssignments.head}" = """
    var i = 1
    while (i < validatedAssignments.size) {
      parts += s""", "${validatedAssignments(i)}" = """
      i += 1
    }
    parts += ""
    Frag(parts.result(), params)
  }

  /** The same as [[doUpdateSuffix]]. */
  def buildDoUpdateSuffix(
    conflictColumn: String,
    assignments: IndexedSeq[(String, DbValue)]
  ): Frag =
    doUpdateSuffix(conflictColumn, assignments)

  // -- low-level full INSERT builders ---------------------------------------------------

  /**
   * Returns `INSERT INTO "<tableName>" (<columns>) VALUES (?, ...)` followed by
   * [[doNothingSuffix]]. Unlike [[insertDoNothing]], nothing checks that
   * `conflictColumn` is one of `columns`.
   *
   * @param values
   *   one value per entry of `columns`, in the same order
   * @throws java.lang.IllegalArgumentException
   *   if a name is not an SQL identifier or the counts differ
   */
  def doNothing(
    tableName: String,
    columns: IndexedSeq[String],
    values: IndexedSeq[DbValue],
    conflictColumn: String
  ): Frag = {
    val t = SqlIdentifier.validate("table", tableName)
    columns.foreach(c => SqlIdentifier.validate("column", c))
    SqlIdentifier.validate("column", conflictColumn)
    require(columns.size == values.size, "Upsert.doNothing: columns/value count mismatch")
    val base = Repo.buildInsertFrag(t, columns, values)
    base ++ doNothingSuffix(conflictColumn)
  }

  /**
   * The same as [[doNothing]], with the columns given as one comma-separated
   * string such as `"sku, name"`.
   *
   * @throws java.lang.IllegalArgumentException
   *   if `allColumns` names no column, a name is not an SQL identifier, or the
   *   counts differ
   */
  def doNothingRaw(
    tableName: String,
    allColumns: String,
    values: IndexedSeq[DbValue],
    conflictColumn: String
  ): Frag = {
    val t       = SqlIdentifier.validate("table", tableName)
    val columns = allColumns.split(",").map(_.trim).filter(_.nonEmpty).toIndexedSeq
    if (columns.isEmpty)
      throw new IllegalArgumentException("Upsert.doNothingRaw: allColumns must not be empty")
    columns.foreach(c => SqlIdentifier.validate("column", c))
    SqlIdentifier.validate("column", conflictColumn)
    require(columns.size == values.size, "Upsert.doNothingRaw: columns/value count mismatch")
    val base = Repo.buildInsertFrag(t, columns, values)
    base ++ doNothingSuffix(conflictColumn)
  }

  /**
   * Returns `INSERT INTO "<tableName>" (<columns>) VALUES (?, ...)` followed by
   * [[doUpdateSuffix]]; the `values` bind first, then the assignment values.
   *
   * @throws java.lang.IllegalArgumentException
   *   if a name is not an SQL identifier, the counts differ, or `assignments`
   *   is empty
   */
  def doUpdate(
    tableName: String,
    columns: IndexedSeq[String],
    values: IndexedSeq[DbValue],
    conflictColumn: String,
    assignments: IndexedSeq[(String, DbValue)]
  ): Frag = {
    val t = SqlIdentifier.validate("table", tableName)
    columns.foreach(c => SqlIdentifier.validate("column", c))
    SqlIdentifier.validate("column", conflictColumn)
    assignments.foreach { case (col, _) => SqlIdentifier.validate("column", col) }
    require(columns.size == values.size, "Upsert.doUpdate: columns/value count mismatch")
    val base = Repo.buildInsertFrag(t, columns, values)
    base ++ doUpdateSuffix(conflictColumn, assignments)
  }

  // -- Table-aware builders -------------------------------------------------------------

  private def validatedTableName[A](table: Table[A]): String =
    SqlIdentifier.validate("table", table.name)

  private def validatedConflictInTable[A](table: Table[A], conflictColumn: String): String = {
    val c = SqlIdentifier.validate("column", conflictColumn)
    if (!table.columns.contains(c))
      throw new IllegalArgumentException(
        s"Conflict column '$conflictColumn' (validated as '$c') not found in table '${table.name}' columns: ${table.columns.mkString(", ")}"
      )
    c
  }

  private def validatedAssignmentsInTable[A](
    table: Table[A],
    cols: Seq[String]
  ): IndexedSeq[String] =
    cols.map { col =>
      val v = SqlIdentifier.validate("column", col)
      if (!table.columns.contains(v))
        throw new IllegalArgumentException(
          s"Assignment column '$col' (validated as '$v') not found in table '${table.name}' columns: ${table.columns.mkString(", ")}"
        )
      v
    }.toIndexedSeq

  /**
   * Returns an `INSERT` of `entity` into `table` that does nothing when a row
   * with the same `conflictColumn` value exists:
   * `INSERT INTO "<table>" (<columns>) VALUES (?, ...) ON CONFLICT ("<conflictColumn>") DO NOTHING`.
   *
   * @throws java.lang.IllegalArgumentException
   *   if `conflictColumn` is not an SQL identifier or not a column of `table`
   */
  def insertDoNothing[A](table: Table[A], entity: A, conflictColumn: String): Frag = {
    val t        = validatedTableName(table)
    val conflict = validatedConflictInTable(table, conflictColumn)
    val values   = table.codec.toDbValues(entity)
    val base     = Repo.buildInsertFrag(t, table.columns, values)
    base ++ doNothingSuffix(conflict)
  }

  /**
   * Returns an `INSERT` of `entity` into `table` that, when a row with the same
   * `conflictColumn` value exists, sets every other column of that row to
   * `entity`'s values.
   *
   * @throws java.lang.IllegalArgumentException
   *   if `conflictColumn` is not an SQL identifier or not a column of `table`,
   *   or if `table` has no other column
   */
  def insertDoUpdate[A](table: Table[A], entity: A, conflictColumn: String): Frag = {
    val conflict   = validatedConflictInTable(table, conflictColumn)
    val updateCols = table.columns.filter(_ != conflict)
    if (updateCols.isEmpty)
      throw new IllegalArgumentException(
        s"Upsert DO UPDATE requires at least one assignment column; table '${table.name}' only has conflict column '$conflict'"
      )
    insertDoUpdate(table, entity, conflict, updateCols)
  }

  /**
   * Returns an `INSERT` of `entity` into `table` that, when a row with the same
   * `conflictColumn` value exists, sets only the `updateColumns` of that row to
   * `entity`'s values.
   *
   * {{{
   * import zio.blocks.schema.Schema
   *
   * case class Product(sku: String, name: String, priceCents: Long)
   * object Product { implicit val schema: Schema[Product] = Schema.derived }
   *
   * val upsert = Upsert.insertDoUpdate(
   *   Table.derived[Product],
   *   Product("MUG-1", "Mug", 1500L),
   *   "sku",
   *   Seq("price_cents")
   * )
   * val sqlText = upsert.sql(SqlDialect.SQLite)
   * // INSERT INTO "product" ("sku", "name", "price_cents") VALUES (?, ?, ?)
   * //   ON CONFLICT ("sku") DO UPDATE SET "price_cents" = ?
   * val params = upsert.params
   * // Vector(DbString(MUG-1), DbString(Mug), DbLong(1500), DbLong(1500))
   * }}}
   *
   * @throws java.lang.IllegalArgumentException
   *   if a name is not an SQL identifier or not a column of `table`, or if
   *   `updateColumns` is empty, contains `conflictColumn`, or repeats a column
   */
  def insertDoUpdate[A](
    table: Table[A],
    entity: A,
    conflictColumn: String,
    updateColumns: Seq[String]
  ): Frag = {
    val t                   = validatedTableName(table)
    val conflict            = validatedConflictInTable(table, conflictColumn)
    val validatedUpdateCols = validatedAssignmentsInTable(table, updateColumns)
    if (validatedUpdateCols.isEmpty)
      throw new IllegalArgumentException("Upsert DO UPDATE requires at least one assignment column")
    if (validatedUpdateCols.contains(conflict))
      throw new IllegalArgumentException(
        s"Assignment columns must not contain conflict column '$conflict'"
      )
    if (validatedUpdateCols.distinct.size != validatedUpdateCols.size)
      throw new IllegalArgumentException(
        s"Assignment columns must not contain duplicates: ${validatedUpdateCols.mkString(", ")}"
      )
    val values = table.codec.toDbValues(entity)
    val base   = Repo.buildInsertFrag(t, table.columns, values)
    // Index by position in one pass, not via per-column `indexOf`: `Table`
    // rejects duplicate columns at construction, so each name maps to exactly
    // one position.
    val columnPositions: Map[String, Int]          = table.columns.zipWithIndex.toMap
    val assignments: IndexedSeq[(String, DbValue)] =
      validatedUpdateCols.map(col => col -> values(columnPositions(col)))
    base ++ doUpdateSuffix(conflict, assignments)
  }
}
