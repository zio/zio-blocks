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
 * Raw SQL text that `sql"..."` splices into a fragment as written, instead of
 * binding it as a `?` parameter, and that the table-checked
 * `StringContext(...).sql(table)(...)` does not check. Use it for SQL the
 * interpolator can't express as a parameter, such as a function call or a
 * column chosen at run time. Nothing escapes or validates the text, so never
 * build it from user input.
 *
 * Interpolated into `sql"..."`, it becomes part of the SQL text:
 *
 * {{{
 * val column = SqlLiteral("upper(name)")
 * val minId  = 10
 * val frag   = sql"SELECT \$column FROM customer WHERE id > \$minId"
 *
 * frag.sql(SqlDialect.SQLite) // SELECT upper(name) FROM customer WHERE id > ?
 * frag.params                 // Vector(DbInt(10))
 * }}}
 *
 * On its own, `toFrag` makes a fragment with no parameters:
 *
 * {{{
 * val frag: Frag = SqlLiteral("SELECT id FROM customer WHERE id = 1").toFrag
 *
 * frag.sql(SqlDialect.SQLite) // SELECT id FROM customer WHERE id = 1
 * }}}
 *
 * @see
 *   [[https://zioblocks.com/reference/sql/frag/ Frag]]
 */
final case class SqlLiteral(sql: String) {

  /**
   * Returns a [[Frag]] whose text is this SQL and that has no parameters.
   */
  def toFrag: Frag = Frag(IndexedSeq(sql), IndexedSeq.empty)
}

/** Constructors for [[SqlLiteral]]. */
object SqlLiteral {

  /**
   * Returns a [[Frag]] whose text is `sql` and that has no parameters, like
   * `SqlLiteral(sql).toFrag` and [[Frag.literal]].
   */
  def frag(sql: String): Frag = Frag(IndexedSeq(sql), IndexedSeq.empty)
}
