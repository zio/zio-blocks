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

package zio.blocks.sql.query

import zio.blocks.sql.{SqlIdentifier, Table}

import scala.quoted.*

/**
 * A join condition between two tables: `fromTable.fkColumn = toTable.pkColumn`.
 * [[SqlQuery#join]] and the other join methods take one. The constructor checks
 * that both columns exist and throws `IllegalArgumentException` otherwise.
 *
 * `Rel.manyToOne` and `Rel.oneToMany` with field selectors check the field
 * names at compile time:
 *
 * {{{
 * import zio.blocks.schema.Schema
 * import zio.blocks.sql.query._
 *
 * case class Customer(customerId: Int, name: String)
 * object Customer { implicit val schema: Schema[Customer] = Schema.derived }
 * case class Invoice(invoiceId: Int, buyerId: Int, totalCents: Long)
 * object Invoice { implicit val schema: Schema[Invoice] = Schema.derived }
 *
 * val customers = Table.derived[Customer]
 * val invoices  = Table.derived[Invoice]
 *
 * val buyer = Rel.manyToOne(invoices, _.buyerId, customers, _.customerId)
 * val columns = (buyer.fkColumn, buyer.pkColumn) // (buyer_id,customer_id)
 * }}}
 *
 * The selector forms turn the field name into snake case and ignore
 * `@Modifier.rename`, so a `Rel` on a renamed field throws when it is built,
 * and they accept a selector that goes on past the field, such as
 * `_.buyerId + 1`, and join on the field alone; both are known bugs in 0.0.56.
 * For a renamed column, pass the column names as strings:
 * `Rel(invoices, "buyer_id", customers, "customer_id")`.
 *
 * @see
 *   [[https://zioblocks.com/reference/sql/rel/ Rel]]
 */
final case class Rel[From, To](
  fromTable: Table[From],
  fkColumn: String,
  toTable: Table[To],
  pkColumn: String
) {
  private val fkValidated = SqlIdentifier.validate("column", fkColumn)
  private val pkValidated = SqlIdentifier.validate("column", pkColumn)

  if (!fromTable.columns.contains(fkValidated))
    throw new IllegalArgumentException(
      s"FK column ${fromTable.name}.$fkColumn (validated as '$fkValidated') not found in table '${fromTable.name}' columns: ${fromTable.columns.mkString(", ")}"
    )
  if (!toTable.columns.contains(pkValidated))
    throw new IllegalArgumentException(
      s"PK column ${toTable.name}.$pkColumn (validated as '$pkValidated') not found in table '${toTable.name}' columns: ${toTable.columns.mkString(", ")}"
    )
}

/** Constructors for [[Rel]]. */
object Rel {

  /**
   * Returns the `Rel` `fromTable.fkColumn = toTable.pkColumn`, for column names
   * known only as strings.
   */
  def manyToOne[From, To](
    fromTable: Table[From],
    fkColumn: String,
    toTable: Table[To],
    pkColumn: String
  ): Rel[From, To] =
    Rel(fromTable, fkColumn, toTable, pkColumn)

  /**
   * The same as the string form of `manyToOne`: it also returns
   * `fromTable.fkColumn = toTable.pkColumn`.
   */
  def oneToMany[From, To](
    fromTable: Table[From],
    fkColumn: String,
    toTable: Table[To],
    pkColumn: String
  ): Rel[From, To] =
    Rel(fromTable, fkColumn, toTable, pkColumn)

  /**
   * Returns the `Rel` `fromTable.<fk> = toTable.<pk>`, where each column is the
   * selected field's name in snake case. A field that the row type does not
   * have is a compile error.
   */
  transparent inline def manyToOne[From, To](
    inline fromTable: Table[From],
    inline fkSelector: From => Any,
    inline toTable: Table[To],
    inline pkSelector: To => Any
  ): Rel[From, To] =
    ${ RelMacros.manyToOneImpl[From, To]('fromTable, 'fkSelector, 'toTable, 'pkSelector) }

  /**
   * The same as the selector form of `manyToOne`: `fromTable` holds the foreign
   * key and `toTable` the key it refers to.
   */
  transparent inline def oneToMany[From, To](
    inline fromTable: Table[From],
    inline fkSelector: From => Any,
    inline toTable: Table[To],
    inline pkSelector: To => Any
  ): Rel[From, To] =
    ${ RelMacros.oneToManyImpl[From, To]('fromTable, 'fkSelector, 'toTable, 'pkSelector) }
}
