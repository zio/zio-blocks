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

package zio.blocks.data.migration

import zio.blocks.sql.{DbCon, DbValue, Frag, Transactor}
import zio.blocks.sql.Frag.*

/**
 * Reads and writes the `data_migrations` table, an optional record of which
 * version each aggregate (a row or entity, named by a type and an id) was
 * migrated to and when. The migrators do not use it; call it yourself when you
 * want an audit trail or to skip aggregates that are already done.
 *
 * {{{
 * import zio.blocks.sql.{zio => _, _}
 *
 * def record(customerId: String)(using con: DbCon): Option[DataVersion] = {
 *   MigrationMetadata.markDone("customer", customerId, DataVersion(1, 2, 0))
 *   MigrationMetadata.getVersion("customer", customerId) // Some(DataVersion(1,2,0,None))
 * }
 * }}}
 */
object MigrationMetadata {

  private val TableName = "data_migrations"

  /**
   * Creates the `data_migrations` table with `CREATE TABLE IF NOT EXISTS`, so a
   * second call does nothing. Its columns are `aggregate_type`, `aggregate_id`,
   * `data_version` (text such as `1.2.0`) and `migrated_at` (milliseconds since
   * the epoch), with the first two as primary key.
   */
  def createTable(transactor: Transactor): Unit = {
    val ddl = Frag.literal(
      s"""CREATE TABLE IF NOT EXISTS $TableName (
         |  aggregate_type TEXT NOT NULL,
         |  aggregate_id   TEXT NOT NULL,
         |  data_version   TEXT NOT NULL,
         |  migrated_at    BIGINT NOT NULL,
         |  PRIMARY KEY (aggregate_type, aggregate_id)
         |)""".stripMargin
    )
    transactor.connect((con: DbCon) ?=> ddl.update(using con))
  }

  /**
   * Records that the aggregate is at `version`, now, replacing an earlier
   * record for it. The version is stored as `epoch.major.minor`, without its
   * `timestampMillis`.
   */
  def markDone(aggregateType: String, aggregateId: String, version: DataVersion)(using con: DbCon): Unit = {
    val vstr = s"${version.epoch}.${version.major}.${version.minor}"
    val now  = System.currentTimeMillis()
    // 7 parts, 6 params = 6 ? placeholders
    val parts = IndexedSeq(
      s"INSERT INTO $TableName (aggregate_type, aggregate_id, data_version, migrated_at) VALUES (",
      ", ",
      ", ",
      ", ",
      ") ON CONFLICT (aggregate_type, aggregate_id) DO UPDATE SET data_version = ",
      ", migrated_at = ",
      ""
    )
    val params = IndexedSeq(
      DbValue.DbString(aggregateType),
      DbValue.DbString(aggregateId),
      DbValue.DbString(vstr),
      DbValue.DbLong(now),
      DbValue.DbString(vstr),
      DbValue.DbLong(now)
    )
    Frag(parts, params).update
  }

  /**
   * Returns the recorded version of the aggregate, with no `timestampMillis`,
   * or `None` when there is no record or the stored text is not three
   * dot-separated integers. A negative part throws `IllegalArgumentException`.
   */
  def getVersion(aggregateType: String, aggregateId: String)(using con: DbCon): Option[DataVersion] = {
    val parts = IndexedSeq(
      s"SELECT data_version FROM $TableName WHERE aggregate_type = ",
      " AND aggregate_id = ",
      ""
    )
    val params = IndexedSeq(DbValue.DbString(aggregateType), DbValue.DbString(aggregateId))
    Frag(parts, params).queryOne[String].toOption.flatMap(parseVersion)
  }

  /** Returns the number of aggregates of `aggregateType` that have a record. */
  def migratedCount(aggregateType: String)(using con: DbCon): Long = {
    val parts  = IndexedSeq(s"SELECT COUNT(*) FROM $TableName WHERE aggregate_type = ", "")
    val params = IndexedSeq(DbValue.DbString(aggregateType))
    Frag(parts, params).queryOne[Long].getOrElse(0L)
  }

  private def parseVersion(s: String): Option[DataVersion] = s.split('.').toList match {
    case e :: m :: n :: Nil =>
      (e.toIntOption, m.toIntOption, n.toIntOption) match {
        case (Some(epoch), Some(major), Some(minor)) => Some(DataVersion(epoch, major, minor))
        case _                                       => None
      }
    case _ => None
  }
}
