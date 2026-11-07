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

/**
 * A version of a data schema, written `epoch.major.minor`, as
 * [[MigrationMetadata]] records it. Versions are ordered by epoch, then major,
 * then minor; `compare` and the comparison operators ignore `timestampMillis`,
 * but `==` does not, so two versions can compare as equal and still be unequal.
 *
 * {{{
 * DataVersion(1, 2, 0) < DataVersion(1, 10, 0)                      // true
 * DataVersion(1, 2, 0, Some(1700000000000L)) == DataVersion(1, 2, 0) // false
 * }}}
 *
 * @param epoch
 *   the outermost part, for changes incompatible with earlier epochs
 * @param major
 *   the major version within the epoch
 * @param minor
 *   the minor version
 * @param timestampMillis
 *   an optional creation time, in milliseconds since the epoch;
 *   [[MigrationMetadata.markDone]] does not store it
 * @throws java.lang.IllegalArgumentException
 *   if `epoch`, `major` or `minor` is negative
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

  /**
   * Compares by epoch, then major, then minor, ignoring `timestampMillis`;
   * returns a negative number, zero or a positive number.
   */
  def compare(that: DataVersion): Int =
    if (epoch != that.epoch) epoch.compare(that.epoch)
    else if (major != that.major) major.compare(that.major)
    else minor.compare(that.minor)

  /** Whether this version is older than `that`, by [[compare]]. */
  def <(that: DataVersion): Boolean = compare(that) < 0

  /** Whether this version is older than or as old as `that`, by [[compare]]. */
  def <=(that: DataVersion): Boolean = compare(that) <= 0

  /** Whether this version is newer than `that`, by [[compare]]. */
  def >(that: DataVersion): Boolean = compare(that) > 0

  /** Whether this version is newer than or as new as `that`, by [[compare]]. */
  def >=(that: DataVersion): Boolean = compare(that) >= 0
}

/**
 * Names the three migration tiers. No method of the module takes or returns an
 * `ExecutionModel`; each tier is its own class.
 *
 *   - `Tiny`: [[TinyMigrator]], schema statements in one transaction at
 *     startup.
 *   - `Small`: [[SmallMigrator]], queued keys converted one batch per call.
 *   - `Large`: [[LargeMigrator]], a worker loop with pause, resume and a
 *     cutover protocol.
 */
sealed trait ExecutionModel

/** The three tiers. */
object ExecutionModel {

  /** The [[TinyMigrator]] tier. */
  case object Tiny extends ExecutionModel

  /** The [[SmallMigrator]] tier. */
  case object Small extends ExecutionModel

  /** The [[LargeMigrator]] tier. */
  case object Large extends ExecutionModel
}

/**
 * Where [[SmallMigrator]] and [[LargeMigrator]] write the converted rows: into
 * the table itself, or into a shadow table that replaces it at completion.
 *
 *   - `InPlace` updates the rows of the V2 repository's table, which needs the
 *     new columns before the first batch.
 *   - `ShadowTable(suffix)` writes into `<table>_<suffix>`, where `<table>` is
 *     the V2 repository's table, and at completion renames `<table>` to
 *     `<table>_old_<suffix>` and the shadow table to `<table>`. It needs
 *     PostgreSQL.
 *
 * {{{
 * TargetStrategyApplier.finalizePreview("customers", TargetStrategy.ShadowTable("v2"))
 * // List(ALTER TABLE customers RENAME TO customers_old_v2, ALTER TABLE customers_v2 RENAME TO customers)
 * }}}
 *
 * @see
 *   [[https://zioblocks.com/reference/data-migration/ Data Migration]]
 */
sealed trait TargetStrategy

/** The two target strategies. */
object TargetStrategy {

  /** Writes the converted rows into the V2 repository's own table. */
  case object InPlace extends TargetStrategy

  /**
   * Writes the converted rows into the shadow table `<table>_<suffix>` and
   * swaps it in at completion. `suffix` must be ASCII letters, digits and
   * underscores, not starting with a digit; the migrators throw
   * `IllegalArgumentException` otherwise. The migrator's `init()` creates the
   * shadow table with the target table's current columns, not the V2 columns (a
   * known bug), so when the migration changes columns, create the shadow table
   * with the new columns before `init()`.
   */
  final case class ShadowTable(suffix: String) extends TargetStrategy
}
