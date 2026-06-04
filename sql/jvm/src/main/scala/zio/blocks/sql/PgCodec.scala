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
 * PostgreSQL-specific [[DbParam]] instances for collection types.
 *
 * Import `PgCodec.given` to enable passing `Seq[A]`, `List[A]`, and
 * `IndexedSeq[A]` as SQL array parameters in the `sql"..."` interpolator.
 *
 * {{{
 * import zio.blocks.sql.*
 * import zio.blocks.sql.PgCodec.given
 *
 * val ids: List[String] = List("a", "b", "c")
 * val frag = sql"SELECT * FROM products WHERE id = ANY(\$ids)"
 * }}}
 *
 * These instances produce `DbValue.DbArray` values that bind as JDBC arrays via
 * `Connection.createArrayOf`. The SQL element type is inferred from the Scala
 * element type.
 */
object PgCodec {

  given seqStringParam: DbParam[Seq[String]] with {
    def toDbValue(v: Seq[String]): DbValue = DbValue.DbArray("varchar", v.toIndexedSeq)
  }

  given listStringParam: DbParam[List[String]] with {
    def toDbValue(v: List[String]): DbValue = DbValue.DbArray("varchar", v.toIndexedSeq)
  }

  given indexedSeqStringParam: DbParam[IndexedSeq[String]] with {
    def toDbValue(v: IndexedSeq[String]): DbValue = DbValue.DbArray("varchar", v)
  }

  given seqIntParam: DbParam[Seq[Int]] with {
    def toDbValue(v: Seq[Int]): DbValue = DbValue.DbArray("integer", v.map(Int.box).toIndexedSeq)
  }

  given listIntParam: DbParam[List[Int]] with {
    def toDbValue(v: List[Int]): DbValue = DbValue.DbArray("integer", v.map(Int.box).toIndexedSeq)
  }

  given indexedSeqIntParam: DbParam[IndexedSeq[Int]] with {
    def toDbValue(v: IndexedSeq[Int]): DbValue = DbValue.DbArray("integer", v.map(Int.box))
  }

  given seqLongParam: DbParam[Seq[Long]] with {
    def toDbValue(v: Seq[Long]): DbValue = DbValue.DbArray("bigint", v.map(Long.box).toIndexedSeq)
  }

  given listLongParam: DbParam[List[Long]] with {
    def toDbValue(v: List[Long]): DbValue = DbValue.DbArray("bigint", v.map(Long.box).toIndexedSeq)
  }

  given indexedSeqLongParam: DbParam[IndexedSeq[Long]] with {
    def toDbValue(v: IndexedSeq[Long]): DbValue = DbValue.DbArray("bigint", v.map(Long.box))
  }

  given seqDoubleParam: DbParam[Seq[Double]] with {
    def toDbValue(v: Seq[Double]): DbValue =
      DbValue.DbArray("double precision", v.map(Double.box).toIndexedSeq)
  }

  given listDoubleParam: DbParam[List[Double]] with {
    def toDbValue(v: List[Double]): DbValue =
      DbValue.DbArray("double precision", v.map(Double.box).toIndexedSeq)
  }

  given seqBooleanParam: DbParam[Seq[Boolean]] with {
    def toDbValue(v: Seq[Boolean]): DbValue =
      DbValue.DbArray("boolean", v.map(Boolean.box).toIndexedSeq)
  }

  given listBooleanParam: DbParam[List[Boolean]] with {
    def toDbValue(v: List[Boolean]): DbValue =
      DbValue.DbArray("boolean", v.map(Boolean.box).toIndexedSeq)
  }

  given seqUuidParam: DbParam[Seq[java.util.UUID]] with {
    def toDbValue(v: Seq[java.util.UUID]): DbValue = DbValue.DbArray("uuid", v.toIndexedSeq)
  }

  given listUuidParam: DbParam[List[java.util.UUID]] with {
    def toDbValue(v: List[java.util.UUID]): DbValue = DbValue.DbArray("uuid", v.toIndexedSeq)
  }
}
