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
    val base = Repo.buildInsertFrag(t, allColumns, values)
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
   * Conflict column defaults to the first column when not supplied.
   */
  def insertDoNothing[A](table: Table[A], entity: A): Frag = {
    val conflict = table.columns.headOption.getOrElse(
      throw new IllegalArgumentException(s"Table '${table.name}' has no columns")
    )
    insertDoNothing(table, entity, conflict)
  }

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
   * When `updateColumns` is empty the builder updates every column except the
   * conflict column. Caller-supplied columns are validated against
   * `Table.columns`.
   */
  def insertDoUpdate[A](table: Table[A], entity: A): Frag = {
    val conflict = table.columns.headOption.getOrElse(
      throw new IllegalArgumentException(s"Table '${table.name}' has no columns")
    )
    insertDoUpdate(table, entity, conflict)
  }

  def insertDoUpdate[A](table: Table[A], entity: A, conflictColumn: String): Frag = {
    val conflict   = validatedConflictInTable(table, conflictColumn)
    val updateCols = table.columns.filter(_ != conflict)
    if (updateCols.isEmpty)
      throw new IllegalArgumentException(
        s"Upsert DO UPDATE requires at least one assignment column; table '${table.name}' only has conflict column '$conflict'"
      )
    insertDoUpdate(table, entity, conflict, updateCols)
  }

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
