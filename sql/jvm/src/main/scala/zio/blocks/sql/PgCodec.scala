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

import zio.blocks.schema.json.Json

/**
 * PostgreSQL instances to import with `import zio.blocks.sql.PgCodec.given`:
 * [[DbParam]]s that bind a Scala collection as one SQL array, so that
 * `= ANY(\$values)` matches any of the values, [[DbCodec]]s that read a
 * `text[]` column into a `List[String]` or `IArray[String]`, and a `DbCodec`
 * for the `Json` type.
 *
 * {{{
 * import zio.blocks.sql.PgCodec.given
 *
 * val skus = List("KB-01", "MS-02")
 * val frag = sql"SELECT name FROM product WHERE sku = ANY(\$skus)"
 *
 * frag.sql(SqlDialect.PostgreSQL) // SELECT name FROM product WHERE sku = ANY(?)
 * frag.params                     // Vector(DbArray(varchar,Vector(KB-01, MS-02)))
 * }}}
 *
 * Each parameter binds through `Connection#createArrayOf` with a fixed element
 * type: `varchar` for `Seq`, `List`, `IndexedSeq` and `IArray` of `String`;
 * `integer` for `Seq`, `List` and `IndexedSeq` of `Int`; `bigint` for the same
 * three of `Long`; `double precision` for `Seq` and `List` of `Double`;
 * `boolean` for `Seq` and `List` of `Boolean`; and `uuid` for `Seq` and `List`
 * of `UUID`. SQLite has no arrays, so these work on PostgreSQL only.
 *
 * @see
 *   [[https://zioblocks.com/reference/sql/db-param/ DbParam]]
 */
object PgCodec {

  /**
   * Stores a [[zio.blocks.schema.json.Json]] value as JSON text, written with
   * `Json#print` and read with `Json.parseUnsafe`, for a `jsonb` column. The
   * text binds as a string, which PostgreSQL won't store in a `jsonb` column
   * unless the JDBC URL has `stringtype=unspecified`: a known bug in 0.0.56.
   */
  given jsonCodec: DbCodec[Json] = DbCodec.jsonb[Json](_.print, Json.parseUnsafe)

  /** The one column name of the array codecs. */
  private val ValueColumn = IndexedSeq("value")

  /** Returns the array as read, `null` included. */
  private def readStringArray(values: Array[String]): Array[String] =
    if (values == null) null else values

  private val listStringArrayCodec: DbCodec[List[String]] = new DbCodec[List[String]] {
    val columns: IndexedSeq[String] = ValueColumn

    def readValue(reader: DbResultReader, columnLabels: IndexedSeq[String]): List[String] = {
      val values = readStringArray(reader.getArray(columnLabels.head))
      if (values == null) null else List.from(values)
    }

    override def readValue(reader: DbResultReader, startIndex: Int): List[String] = {
      val values = readStringArray(reader.getArray(startIndex))
      if (values == null) null else List.from(values)
    }

    def writeValue(writer: DbParamWriter, startIndex: Int, value: List[String]): Unit =
      writer.setArray(startIndex, "varchar", value.toIndexedSeq)

    def toDbValues(value: List[String]): IndexedSeq[DbValue] =
      IndexedSeq(DbValue.DbArray("varchar", value.toIndexedSeq))
  }

  private val iArrayStringArrayCodec: DbCodec[IArray[String]] = new DbCodec[IArray[String]] {
    val columns: IndexedSeq[String] = ValueColumn

    def readValue(reader: DbResultReader, columnLabels: IndexedSeq[String]): IArray[String] = {
      val values = readStringArray(reader.getArray(columnLabels.head))
      if (values == null) null.asInstanceOf[IArray[String]] else IArray.from(values)
    }

    override def readValue(reader: DbResultReader, startIndex: Int): IArray[String] = {
      val values = readStringArray(reader.getArray(startIndex))
      if (values == null) null.asInstanceOf[IArray[String]] else IArray.from(values)
    }

    def writeValue(writer: DbParamWriter, startIndex: Int, value: IArray[String]): Unit =
      writer.setArray(startIndex, "varchar", value.toIndexedSeq)

    def toDbValues(value: IArray[String]): IndexedSeq[DbValue] =
      IndexedSeq(DbValue.DbArray("varchar", value.toIndexedSeq))
  }

  /**
   * Reads a SQL array column into a `List[String]` and writes one as a
   * `varchar` array. A SQL `NULL` array reads as `null`, not an empty list: a
   * known bug in 0.0.56.
   */
  given listStringCodec: DbCodec[List[String]] = listStringArrayCodec

  /**
   * Reads a SQL array column into an `IArray[String]` and writes one as a
   * `varchar` array; a SQL `NULL` array reads as `null`.
   */
  given iArrayStringCodec: DbCodec[IArray[String]] = iArrayStringArrayCodec

  /** Binds a `Seq[String]` as a `varchar` array. */
  given seqStringParam: DbParam[Seq[String]] with {
    def toDbValue(v: Seq[String]): DbValue = DbValue.DbArray("varchar", v.toIndexedSeq)
  }

  /** Binds a `List[String]` as a `varchar` array. */
  given listStringParam: DbParam[List[String]] with {
    def toDbValue(v: List[String]): DbValue = DbValue.DbArray("varchar", v.toIndexedSeq)
  }

  /** Binds a `IArray[String]` as a `varchar` array. */
  given iArrayStringParam: DbParam[IArray[String]] with {
    def toDbValue(v: IArray[String]): DbValue = DbValue.DbArray("varchar", v.toIndexedSeq)
  }

  /** Binds a `IndexedSeq[String]` as a `varchar` array. */
  given indexedSeqStringParam: DbParam[IndexedSeq[String]] with {
    def toDbValue(v: IndexedSeq[String]): DbValue = DbValue.DbArray("varchar", v)
  }

  /** Binds a `Seq[Int]` as an `integer` array. */
  given seqIntParam: DbParam[Seq[Int]] with {
    def toDbValue(v: Seq[Int]): DbValue = DbValue.DbArray("integer", v.map(Int.box).toIndexedSeq)
  }

  /** Binds a `List[Int]` as an `integer` array. */
  given listIntParam: DbParam[List[Int]] with {
    def toDbValue(v: List[Int]): DbValue = DbValue.DbArray("integer", v.map(Int.box).toIndexedSeq)
  }

  /** Binds a `IndexedSeq[Int]` as an `integer` array. */
  given indexedSeqIntParam: DbParam[IndexedSeq[Int]] with {
    def toDbValue(v: IndexedSeq[Int]): DbValue = DbValue.DbArray("integer", v.map(Int.box))
  }

  /** Binds a `Seq[Long]` as a `bigint` array. */
  given seqLongParam: DbParam[Seq[Long]] with {
    def toDbValue(v: Seq[Long]): DbValue = DbValue.DbArray("bigint", v.map(Long.box).toIndexedSeq)
  }

  /** Binds a `List[Long]` as a `bigint` array. */
  given listLongParam: DbParam[List[Long]] with {
    def toDbValue(v: List[Long]): DbValue = DbValue.DbArray("bigint", v.map(Long.box).toIndexedSeq)
  }

  /** Binds a `IndexedSeq[Long]` as a `bigint` array. */
  given indexedSeqLongParam: DbParam[IndexedSeq[Long]] with {
    def toDbValue(v: IndexedSeq[Long]): DbValue = DbValue.DbArray("bigint", v.map(Long.box))
  }

  /** Binds a `Seq[Double]` as a `double precision` array. */
  given seqDoubleParam: DbParam[Seq[Double]] with {
    def toDbValue(v: Seq[Double]): DbValue =
      DbValue.DbArray("double precision", v.map(Double.box).toIndexedSeq)
  }

  /** Binds a `List[Double]` as a `double precision` array. */
  given listDoubleParam: DbParam[List[Double]] with {
    def toDbValue(v: List[Double]): DbValue =
      DbValue.DbArray("double precision", v.map(Double.box).toIndexedSeq)
  }

  /** Binds a `Seq[Boolean]` as a `boolean` array. */
  given seqBooleanParam: DbParam[Seq[Boolean]] with {
    def toDbValue(v: Seq[Boolean]): DbValue =
      DbValue.DbArray("boolean", v.map(Boolean.box).toIndexedSeq)
  }

  /** Binds a `List[Boolean]` as a `boolean` array. */
  given listBooleanParam: DbParam[List[Boolean]] with {
    def toDbValue(v: List[Boolean]): DbValue =
      DbValue.DbArray("boolean", v.map(Boolean.box).toIndexedSeq)
  }

  /** Binds a `Seq[UUID]` as a `uuid` array. */
  given seqUuidParam: DbParam[Seq[java.util.UUID]] with {
    def toDbValue(v: Seq[java.util.UUID]): DbValue = DbValue.DbArray("uuid", v.toIndexedSeq)
  }

  /** Binds a `List[UUID]` as a `uuid` array. */
  given listUuidParam: DbParam[List[java.util.UUID]] with {
    def toDbValue(v: List[java.util.UUID]): DbValue = DbValue.DbArray("uuid", v.toIndexedSeq)
  }
}
