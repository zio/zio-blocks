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

import zio.blocks.schema.migration.Migration

/**
 * Semantic version for data schemas.
 *
 * @param epoch
 *   Major breaking epoch (incompatible with prior epochs)
 * @param major
 *   Major version within epoch
 * @param minor
 *   Minor/patch version
 * @param timestampMillis
 *   Optional creation timestamp (epoch millis)
 */
final case class DataVersion(
  epoch: Int,
  major: Int,
  minor: Int,
  timestampMillis: Option[Long] = None
) {
  require(epoch >= 0, "epoch must be non-negative")
  require(major >= 0, "major must be non-negative")
  require(minor >= 0, "minor must be non-negative")

  /** Compare for ordering (epoch > major > minor). */
  def compare(that: DataVersion): Int =
    if (epoch != that.epoch) epoch.compare(that.epoch)
    else if (major != that.major) major.compare(that.major)
    else minor.compare(that.minor)

  def <(that: DataVersion): Boolean  = compare(that) < 0
  def <=(that: DataVersion): Boolean = compare(that) <= 0
  def >(that: DataVersion): Boolean  = compare(that) > 0
  def >=(that: DataVersion): Boolean = compare(that) >= 0
}

/**
 * Typed migration path from version A to version B.
 *
 * @param from
 *   Source data version
 * @param to
 *   Target data version
 * @param migration
 *   The typed Migration[A, B] transformation
 */
final case class MigrationPath[A, B](
  from: DataVersion,
  to: DataVersion,
  migration: Migration[A, B]
) {
  require(from < to, "MigrationPath requires from < to (no downgrades)")
}

/**
 * Execution model for migration strategies.
 *
 * - Tiny: in-memory, single-transaction, minimal overhead
 * - Small: batch-oriented, moderate resource use
 * - Large: streaming/out-of-core, resumable, high resource tolerance
 */
sealed trait ExecutionModel
object ExecutionModel {
  case object Tiny  extends ExecutionModel
  case object Small extends ExecutionModel
  case object Large extends ExecutionModel
}

/**
 * Target write strategy (orthogonal to ExecutionModel).
 *
 * - InPlace: mutate existing table/storage
 * - ShadowTable: write to a new table with given name, then swap/rename
 */
sealed trait TargetStrategy
object TargetStrategy {
  case object InPlace                           extends TargetStrategy
  final case class ShadowTable(name: String)    extends TargetStrategy
}
