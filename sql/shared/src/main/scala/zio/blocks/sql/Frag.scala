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

final case class Frag(parts: IndexedSeq[String], params: IndexedSeq[DbValue]) {

  def ++(other: Frag): Frag =
    if (parts.isEmpty) other
    else if (other.parts.isEmpty) this
    else {
      val mergedParts = parts.init ++ IndexedSeq(parts.last + other.parts.head) ++ other.parts.tail
      Frag(mergedParts, params ++ other.params)
    }

  def sql(dialect: SqlDialect): String = {
    val sb       = new StringBuilder
    var paramIdx = 1
    var i        = 0
    while (i < parts.length) {
      sb.append(parts(i))
      if (i < params.length) {
        sb.append(dialect.paramPlaceholder(paramIdx))
        paramIdx += 1
      }
      i += 1
    }
    sb.toString()
  }

  def queryParams: IndexedSeq[DbValue] = params

  def isEmpty: Boolean = parts.forall(_.isEmpty) && params.isEmpty
}

object Frag {
  val empty: Frag = Frag(IndexedSeq(""), IndexedSeq.empty)

  def const(sqlStr: String): Frag = Frag(IndexedSeq(sqlStr), IndexedSeq.empty)

  extension (frag: Frag) {

    def query[A](using DbCon, DbCodec[A]): List[A] =
      SqlOps.query[A](frag)

    def queryOne[A](using DbCon, DbCodec[A]): Option[A] =
      SqlOps.queryOne[A](frag)

    def queryLimit[A](limit: Int)(using DbCon, DbCodec[A]): List[A] =
      SqlOps.queryLimit[A](frag, limit)

    def update(using DbCon): Int =
      SqlOps.update(frag)
  }
}
