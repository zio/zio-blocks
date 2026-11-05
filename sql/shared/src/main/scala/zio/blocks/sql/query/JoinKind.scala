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

/** The kind of a join, for [[SqlQuery#join]]. */
sealed trait JoinKind

/** The join kinds. */
object JoinKind {

  /** `INNER JOIN`: keeps only rows with a match in both tables. */
  case object Inner extends JoinKind

  /** `LEFT JOIN`: keeps every row of the query so far. */
  case object Left extends JoinKind
}
