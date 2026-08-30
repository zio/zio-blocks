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
 * Escape hatch for unchecked raw SQL.
 *
 * `SqlLiteral` holds a SQL string that is spliced verbatim into a `sql"..."`
 * fragment without parameter binding or identifier checking. Use it for
 * dialect-specific functions or dynamic SQL that the lint-grade checked `sql`
 * (`StringContext(...).sql(table)(...)`) cannot model. Prefer the checked
 * `StringContext(...).sql(table)(...)` form for all hand-written queries;
 * reserve `SqlLiteral` for genuinely dynamic or dialect-specific cases.
 *
 * Standalone usage (unchecked, no validation):
 * {{{
 *   val frag: Frag = SqlLiteral("SELECT * FROM legacy WHERE id = 1").toFrag
 * }}}
 *
 * Splicing into `sql` interpolator as a raw fragment (not a parameter):
 * {{{
 *   sql"SELECT ${SqlLiteral("MY_FUNC(id)")} FROM users"
 * }}}
 */
final case class SqlLiteral(sql: String) {

  /** Wraps this raw SQL string as a [[Frag]] with no parameters. */
  def toFrag: Frag = Frag(IndexedSeq(sql), IndexedSeq.empty)
}

object SqlLiteral {

  /** Creates a parameter-free [[Frag]] from a raw SQL string. */
  def frag(sql: String): Frag = Frag(IndexedSeq(sql), IndexedSeq.empty)
}
