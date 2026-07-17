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
 * Helpers for the optional `data_migrations` metadata table.
 *
 * Stores provenance for migrated aggregates when resume/re-processing
 * or audit trails are required. Not required for simple in-place migrations.
 */
object MigrationMetadata {

  private val TableName = "data_migrations"

  /**
   * Creates the data_migrations table if it does not exist.
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
    transactor.connect { (con: DbCon) ?=> ddl.update(using con) }
  }

  /**
   * Marks an aggregate as successfully migrated to a given version.
   */
  def markDone(aggregateType: String, aggregateId: String, version: DataVersion)(using con: DbCon): Unit = {
    val vstr  = s"${version.epoch}.${version.major}.${version.minor}"
    val now   = System.currentTimeMillis()
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
      DbValue.DbString(aggregateType), DbValue.DbString(aggregateId), DbValue.DbString(vstr), DbValue.DbLong(now),
      DbValue.DbString(vstr), DbValue.DbLong(now)
    )
    Frag(parts, params).update
  }

  /**
   * Returns the current data version for an aggregate, if recorded.
   */
  def getVersion(aggregateType: String, aggregateId: String)(using con: DbCon): Option[DataVersion] = {
    val sql = s"SELECT data_version FROM $TableName WHERE aggregate_type = ? AND aggregate_id = ?"
    Frag(IndexedSeq(sql), IndexedSeq(DbValue.DbString(aggregateType), DbValue.DbString(aggregateId))).queryOne[String].toOption.flatMap(parseVersion)
  }

  /**
    * Returns count of migrated aggregates recorded in the metadata table.
    */
  def migratedCount(aggregateType: String)(using con: DbCon): Long = {
    val sql = s"SELECT COUNT(*) FROM $TableName WHERE aggregate_type = ?"
    Frag(IndexedSeq(sql), IndexedSeq(DbValue.DbString(aggregateType))).queryOne[Long].getOrElse(0L)
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
