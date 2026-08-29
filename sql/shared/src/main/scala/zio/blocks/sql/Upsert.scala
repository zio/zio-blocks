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
 * Builders for SQLite/PostgreSQL upsert Frags.
 *
 * Every produced Frag follows the invariant `parts.length == params.length + 1`
 * and is rendered via `dialect.paramPlaceholder` (both dialects use `?`).
 * Identifiers are validated through [[SqlIdentifier.validate]] and quoted with
 * double quotes in the conflict clause. Assignment columns are additionally
 * validated against `Table.columns`.
 */
object Upsert {

  // -- suffix builders -----------------------------------------------------------------

  /** Suffix ` ON CONFLICT ("col") DO NOTHING`. */
  def doNothingSuffix(conflictColumn: String): Frag = {
    val c = SqlIdentifier.validate("column", conflictColumn)
    Frag(IndexedSeq(s""" ON CONFLICT ("$c") DO NOTHING"""), IndexedSeq.empty)
  }

  /** Alias used by prior docs. */
  def buildDoNothingSuffix(conflictColumn: String): Frag =
    doNothingSuffix(conflictColumn)

  /**
   * Suffix ` ON CONFLICT ("conflict") DO UPDATE SET "col" = ?, ...`.
   * Assignments must be non-empty; each column is validated and quoted.
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

  /** Alias used by prior docs. */
  def buildDoUpdateSuffix(
    conflictColumn: String,
    assignments: IndexedSeq[(String, DbValue)]
  ): Frag =
    doUpdateSuffix(conflictColumn, assignments)

  // -- low-level full INSERT builders ---------------------------------------------------

  /**
   * Low-level builder: full INSERT with DO NOTHING.
   *
   * @param tableName
   *   validated via SqlIdentifier
   * @param columns
   *   column names in order, each validated
   * @param values
   *   values aligned with columns
   * @param conflictColumn
   *   validated and quoted in suffix
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
    val allCols = columns.mkString(", ")
    val base    = Repo.buildInsertFrag(t, allCols, values)
    base ++ doNothingSuffix(conflictColumn)
  }

  /** Overload accepting comma-joined column string (as Repo does). */
  def doNothingRaw(
    tableName: String,
    allColumns: String,
    values: IndexedSeq[DbValue],
    conflictColumn: String
  ): Frag = {
    val t = SqlIdentifier.validate("table", tableName)
    SqlIdentifier.validate("column", conflictColumn)
    val columns = allColumns.split(",").map(_.trim).filter(_.nonEmpty).toIndexedSeq
    if (columns.isEmpty)
      throw new IllegalArgumentException("Upsert.doNothingRaw: allColumns must not be empty")
    columns.foreach(c => SqlIdentifier.validate("column", c))
    require(columns.size == values.size, "Upsert.doNothingRaw: columns/value count mismatch")
    val normalizedCols = columns.mkString(", ")
    val base           = Repo.buildInsertFrag(t, normalizedCols, values)
    base ++ doNothingSuffix(conflictColumn)
  }

  /**
   * Low-level builder: full INSERT with DO UPDATE SET.
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
    val allCols = columns.mkString(", ")
    val base    = Repo.buildInsertFrag(t, allCols, values)
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
   * `INSERT ... ON CONFLICT ("conflict") DO NOTHING` for an entity.
   *
   * Builds
   * `INSERT INTO table (cols) VALUES (?, ...) ON CONFLICT ("conflict") DO NOTHING`.
   * The table name is validated via [[SqlIdentifier.validate]] and the conflict
   * column is validated as an identifier and checked for membership in
   * `table.columns`.
   *
   * @param table
   *   validated via [[Table]]; its name via [[SqlIdentifier.validate]] and its
   *   columns used for conflict validation
   * @param entity
   *   entity whose values are obtained via `table.codec.toDbValues`
   * @param conflictColumn
   *   validated identifier that must be present in `table.columns`
   * @return
   *   a [[Frag]] whose SQL is `INSERT ... ON CONFLICT ("conflict") DO NOTHING`
   * @throws IllegalArgumentException
   *   if `conflictColumn` is not a valid identifier or is not found in
   *   `table.columns`
   */
  def insertDoNothing[A](table: Table[A], entity: A, conflictColumn: String): Frag = {
    val t        = validatedTableName(table)
    val conflict = validatedConflictInTable(table, conflictColumn)
    val values   = table.codec.toDbValues(entity)
    val allCols  = table.columns.mkString(", ")
    val base     = Repo.buildInsertFrag(t, allCols, values)
    base ++ doNothingSuffix(conflict)
  }

  /**
   * `INSERT ... ON CONFLICT ("conflict") DO UPDATE SET ...` for an entity.
   *
   * Updates all columns except the conflict column. The conflict column is
   * validated via [[SqlIdentifier.validate]] and must exist in `table.columns`.
   * The update set is derived as `table.columns.filter(_ != conflict)` and
   * validated against `Table.columns`.
   *
   * @param table
   *   validated via [[Table]]; name validated via [[SqlIdentifier.validate]]
   * @param entity
   *   entity serialized via `table.codec.toDbValues`
   * @param conflictColumn
   *   validated identifier that must be present in `table.columns`
   * @return
   *   a [[Frag]] with `ON CONFLICT ("conflict") DO UPDATE SET "col" = ?, ...`
   *   where cols are all table columns except the conflict column
   * @throws IllegalArgumentException
   *   if `conflictColumn` is invalid or not in `table.columns`, or if the table
   *   has only the conflict column (no assignable columns to update)
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
   * `INSERT ... ON CONFLICT ("conflict") DO UPDATE SET "col" = ?, ...` for an
   * entity with explicit update columns.
   *
   * All identifiers are validated via [[SqlIdentifier.validate]]. The conflict
   * column and each `updateColumns` entry must exist in `table.columns`.
   *
   * @param table
   *   validated via [[Table]]; name validated via [[SqlIdentifier.validate]]
   * @param entity
   *   entity serialized via `table.codec.toDbValues`
   * @param conflictColumn
   *   validated identifier that must be present in `table.columns`
   * @param updateColumns
   *   validated against `table.columns`; each entry validated as an identifier,
   *   must exist in the table, must not contain `conflictColumn`, and must be
   *   non-empty
   * @return
   *   a [[Frag]] with `ON CONFLICT ("conflict") DO UPDATE SET` for the
   *   specified columns
   * @throws IllegalArgumentException
   *   if any identifier is invalid, if any column is not in `table.columns`, if
   *   `updateColumns` contains the conflict column, or if it is empty
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
    val values  = table.codec.toDbValues(entity)
    val allCols = table.columns.mkString(", ")
    val base    = Repo.buildInsertFrag(t, allCols, values)
    // Map column -> value via table.columns zip
    val colValueMap                                = table.columns.zip(values).toMap
    val assignments: IndexedSeq[(String, DbValue)] = validatedUpdateCols.map(col => col -> colValueMap(col))
    base ++ doUpdateSuffix(conflict, assignments)
  }
}
