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

package zio.blocks.schema.migration

import zio.blocks.schema._

/**
 * Serializable, closure-free expressions used inside [[MigrationAction]].
 * These evaluate against a root [[DynamicValue]] and optional [[Schema]]
 * metadata (for defaults). They intentionally exclude arbitrary user functions
 * so [[DynamicMigration]] stays serializable.
 *
 * Issue #519 refers to this role as `SchemaExpr` for migrations; that name would
 * collide with [[zio.blocks.schema.SchemaExpr]] (validation / persistence DSL),
 * so this type is named `MigrationExpr`. Use [[MigrationSchemaExpr]] if you
 * need ticket-aligned wording.
 */
sealed trait MigrationExpr { self =>

  /** Structural inverse used when reversing a [[DynamicMigration]]. */
  def reverse: MigrationExpr

  def mapReverse(f: MigrationExpr => MigrationExpr): MigrationExpr = f(self)
}

object MigrationExpr {

  /**
   * Name used in zio-blocks#519 for serializable migration value expressions.
   * Prefer [[MigrationExpr]] in code to avoid confusion with
   * [[zio.blocks.schema.SchemaExpr]].
   */
  type MigrationSchemaExpr = MigrationExpr

  /** A constant [[DynamicValue]] (including primitives). */
  final case class Literal(value: DynamicValue) extends MigrationExpr {
    def reverse: MigrationExpr = this
  }

  /**
   * Read a value relative to the migration root using a [[DynamicOptic]]
   * path.
   */
  final case class RootPath(relative: DynamicOptic) extends MigrationExpr {
    def reverse: MigrationExpr = this
  }

  /** String concatenation (typically for join-style transforms). */
  final case class ConcatStrings(left: MigrationExpr, right: MigrationExpr) extends MigrationExpr {
    def reverse: MigrationExpr = ConcatStrings(left.reverse, right.reverse)
  }

  /** Integer addition. */
  final case class IntPlus(left: MigrationExpr, right: MigrationExpr) extends MigrationExpr {
    def reverse: MigrationExpr = IntPlus(left.reverse, right.reverse)
  }

  /**
   * Resolve `schema.defaultValue` for the whole record (used for optional
   * placeholders in reverse direction).
   */
  final case class SchemaRootDefault(schemaSlot: MigrationSchemaSlot) extends MigrationExpr {
    def reverse: MigrationExpr = this
  }

  /** Schema root default for the source side of a migration (issue #519 default slot). */
  val DefaultValueSource: MigrationExpr = SchemaRootDefault(MigrationSchemaSlot.Source)

  /** Schema root default for the target side of a migration. */
  val DefaultValueTarget: MigrationExpr = SchemaRootDefault(MigrationSchemaSlot.Target)

  /**
   * Resolve the default for a top-level record field by name using the
   * selected schema (source vs target).
   */
  final case class FieldDefault(fieldName: String, schemaSlot: MigrationSchemaSlot) extends MigrationExpr {
    def reverse: MigrationExpr = FieldDefault(fieldName, schemaSlot.flip)
  }

  /**
   * Coerce a primitive [[DynamicValue]] to another primitive representation
   * (narrow widening / string parsing as supported by [[PrimitiveValue]]).
   */
  final case class CoercePrimitive(to: MigrationPrimitiveTarget) extends MigrationExpr {
    def reverse: MigrationExpr = this
  }
}
