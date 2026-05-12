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

import zio.blocks.chunk.Chunk
import zio.blocks.schema._

/**
 * A pure, serializable structural transformation between schema versions.
 *
 * `DynamicMigration` represents schema evolution as first-class, introspectable
 * data. Migrations contain no user functions, closures, or reflection — they are
 * pure values that can be serialized, stored in registries, composed, inverted
 * (when possible), and used to generate DDL or data-lake transforms.
 *
 * Migrations operate on [[DynamicValue]] and are the untyped core underlying
 * the typed [[Migration]][A, B] API.
 *
 * ==ADT overview==
 *
 * {{{
 * Identity                           // no-op
 * AndThen(first, second)             // sequential composition
 *
 * // Record field operations
 * RenameField(from, to)              // rename a field (fails if absent)
 * AddField(name, default)            // add a field with a default (idempotent)
 * RemoveField(name)                  // remove a field (idempotent)
 * MigrateField(name, migration)      // apply a sub-migration to a field
 *
 * // Variant case operations
 * RenameCase(from, to)               // rename a case (no-op if unmatched)
 * MigrateCase(name, migration)       // apply a sub-migration to a case value
 *
 * // Collection operations
 * MigrateElements(migration)         // apply to every element of a Sequence
 * MigrateValues(migration)           // apply to every value of a Map
 * }}}
 *
 * @see [[Migration]] for the typed user-facing wrapper
 */
sealed trait DynamicMigration { self =>

  /**
   * Composes this migration with `that`, applying `this` first and then `that`.
   *
   * Optimises away `Identity` on either side.
   */
  def andThen(that: DynamicMigration): DynamicMigration =
    if (self eq DynamicMigration.Identity) that
    else if (that eq DynamicMigration.Identity) self
    else DynamicMigration.AndThen(self, that)

  /**
   * Applies this migration to the given [[DynamicValue]].
   *
   * @return
   *   `Right` with the transformed value, or `Left` with a [[SchemaError]] if
   *   the value does not match the expected structure.
   */
  def apply(value: DynamicValue): Either[SchemaError, DynamicValue] =
    DynamicMigration.applyMigration(self, value)

  /**
   * Returns the inverse of this migration if one exists.
   *
   * Invertible operations:
   *   - `Identity` → `Identity`
   *   - `RenameField(a, b)` → `RenameField(b, a)`
   *   - `RenameCase(a, b)` → `RenameCase(b, a)`
   *   - `AndThen(m1, m2)` → `AndThen(m2.invert, m1.invert)` (if both invertible)
   *   - `MigrateField/Case/Elements/Values(m)` → same wrapper with `m.invert`
   *
   * Not invertible: `AddField` and `RemoveField` (the removed value is not
   * recoverable from the migration description alone).
   */
  def invert: Option[DynamicMigration] = DynamicMigration.invertMigration(self)
}

object DynamicMigration {

  /** No-op migration that returns the value unchanged. */
  case object Identity extends DynamicMigration

  /**
   * Applies `first` and then `second`.
   *
   * Prefer the `andThen` combinator which eliminates redundant `Identity`
   * wrappers.
   */
  final case class AndThen(first: DynamicMigration, second: DynamicMigration) extends DynamicMigration

  /**
   * Renames field `from` to `to` inside a [[DynamicValue.Record]].
   *
   * Fails with [[SchemaError]] if the source field is absent.
   */
  final case class RenameField(from: String, to: String) extends DynamicMigration

  /**
   * Adds field `name` with `value` to a [[DynamicValue.Record]] if absent.
   *
   * Idempotent: if the field already exists, the record is returned unchanged.
   */
  final case class AddField(name: String, value: DynamicValue) extends DynamicMigration

  /**
   * Removes field `name` from a [[DynamicValue.Record]] if present.
   *
   * Idempotent: if the field is absent, the record is returned unchanged.
   */
  final case class RemoveField(name: String) extends DynamicMigration

  /**
   * Applies `migration` to the value of field `name` inside a
   * [[DynamicValue.Record]].
   *
   * Fails with [[SchemaError]] if the field is absent.
   */
  final case class MigrateField(name: String, migration: DynamicMigration) extends DynamicMigration

  /**
   * Renames Variant case `from` to `to` inside a [[DynamicValue.Variant]].
   *
   * Returns the value unchanged if the case name does not match `from`.
   */
  final case class RenameCase(from: String, to: String) extends DynamicMigration

  /**
   * Applies `migration` to the inner value of a [[DynamicValue.Variant]] whose
   * case name equals `name`.
   *
   * Returns the value unchanged if the case name does not match.
   */
  final case class MigrateCase(name: String, migration: DynamicMigration) extends DynamicMigration

  /**
   * Applies `migration` to every element of a [[DynamicValue.Sequence]].
   *
   * Fails on the first element that cannot be migrated.
   */
  final case class MigrateElements(migration: DynamicMigration) extends DynamicMigration

  /**
   * Applies `migration` to every value (not key) of a [[DynamicValue.Map]].
   *
   * Fails on the first entry value that cannot be migrated.
   */
  final case class MigrateValues(migration: DynamicMigration) extends DynamicMigration

  // ─────────────────────────────────────────────────────────────────────────
  // Interpreter
  // ─────────────────────────────────────────────────────────────────────────

  private[migration] def applyMigration(
    migration: DynamicMigration,
    value: DynamicValue
  ): Either[SchemaError, DynamicValue] =
    migration match {

      case Identity => new Right(value)

      case AndThen(first, second) =>
        applyMigration(first, value) match {
          case Right(v) => applyMigration(second, v)
          case l        => l
        }

      case RenameField(from, to) =>
        value match {
          case r: DynamicValue.Record =>
            val fields = r.fields
            val idx    = fields.indexWhere(_._1 == from)
            if (idx < 0) new Left(SchemaError.missingField(Nil, from))
            else new Right(new DynamicValue.Record(fields.updated(idx, (to, fields(idx)._2))))
          case _ =>
            new Left(
              SchemaError.expectationMismatch(
                Nil,
                s"RenameField('$from', '$to') requires a Record but got ${value.getClass.getSimpleName}"
              )
            )
        }

      case AddField(name, defaultValue) =>
        value match {
          case r: DynamicValue.Record =>
            val fields = r.fields
            if (fields.exists(_._1 == name)) new Right(value)
            else new Right(new DynamicValue.Record(fields :+ (name, defaultValue)))
          case _ =>
            new Left(
              SchemaError.expectationMismatch(
                Nil,
                s"AddField('$name') requires a Record but got ${value.getClass.getSimpleName}"
              )
            )
        }

      case RemoveField(name) =>
        value match {
          case r: DynamicValue.Record =>
            val fields = r.fields
            val idx    = fields.indexWhere(_._1 == name)
            if (idx < 0) new Right(value)
            else new Right(new DynamicValue.Record(fields.take(idx) ++ fields.drop(idx + 1)))
          case _ =>
            new Left(
              SchemaError.expectationMismatch(
                Nil,
                s"RemoveField('$name') requires a Record but got ${value.getClass.getSimpleName}"
              )
            )
        }

      case MigrateField(name, fieldMigration) =>
        value match {
          case r: DynamicValue.Record =>
            val fields = r.fields
            val idx    = fields.indexWhere(_._1 == name)
            if (idx < 0) new Left(SchemaError.missingField(Nil, name))
            else {
              val (fieldName, fieldValue) = fields(idx)
              applyMigration(fieldMigration, fieldValue) match {
                case Right(newValue) =>
                  new Right(new DynamicValue.Record(fields.updated(idx, (fieldName, newValue))))
                case l => l
              }
            }
          case _ =>
            new Left(
              SchemaError.expectationMismatch(
                Nil,
                s"MigrateField('$name') requires a Record but got ${value.getClass.getSimpleName}"
              )
            )
        }

      case RenameCase(from, to) =>
        value match {
          case v: DynamicValue.Variant =>
            if (v.caseNameValue == from) new Right(new DynamicValue.Variant(to, v.value))
            else new Right(value)
          case _ =>
            new Left(
              SchemaError.expectationMismatch(
                Nil,
                s"RenameCase('$from', '$to') requires a Variant but got ${value.getClass.getSimpleName}"
              )
            )
        }

      case MigrateCase(name, caseMigration) =>
        value match {
          case v: DynamicValue.Variant =>
            if (v.caseNameValue != name) new Right(value)
            else
              applyMigration(caseMigration, v.value) match {
                case Right(newValue) => new Right(new DynamicValue.Variant(name, newValue))
                case l               => l
              }
          case _ =>
            new Left(
              SchemaError.expectationMismatch(
                Nil,
                s"MigrateCase('$name') requires a Variant but got ${value.getClass.getSimpleName}"
              )
            )
        }

      case MigrateElements(elementMigration) =>
        value match {
          case s: DynamicValue.Sequence =>
            val elements = s.elements
            val len      = elements.length
            val results  = new Array[DynamicValue](len)
            var idx      = 0
            while (idx < len) {
              applyMigration(elementMigration, elements(idx)) match {
                case Right(v) => results(idx) = v
                case l        => return l
              }
              idx += 1
            }
            new Right(new DynamicValue.Sequence(Chunk.fromArray(results)))
          case _ =>
            new Left(
              SchemaError.expectationMismatch(
                Nil,
                s"MigrateElements requires a Sequence but got ${value.getClass.getSimpleName}"
              )
            )
        }

      case MigrateValues(valueMigration) =>
        value match {
          case m: DynamicValue.Map =>
            val entries = m.entries
            val len     = entries.length
            val results = new Array[(DynamicValue, DynamicValue)](len)
            var idx     = 0
            while (idx < len) {
              val (k, v) = entries(idx)
              applyMigration(valueMigration, v) match {
                case Right(newV) => results(idx) = (k, newV)
                case l           => return l
              }
              idx += 1
            }
            new Right(new DynamicValue.Map(Chunk.fromArray(results)))
          case _ =>
            new Left(
              SchemaError.expectationMismatch(
                Nil,
                s"MigrateValues requires a Map but got ${value.getClass.getSimpleName}"
              )
            )
        }
    }

  // ─────────────────────────────────────────────────────────────────────────
  // Inversion
  // ─────────────────────────────────────────────────────────────────────────

  private def invertMigration(migration: DynamicMigration): Option[DynamicMigration] =
    migration match {
      case Identity              => new Some(Identity)
      case RenameField(from, to) => new Some(RenameField(to, from))
      case RenameCase(from, to)  => new Some(RenameCase(to, from))
      case RemoveField(_)        => None
      case AddField(_, _)        => None
      case AndThen(first, second) =>
        invertMigration(second) match {
          case Some(invSecond) =>
            invertMigration(first) match {
              case Some(invFirst) => new Some(AndThen(invSecond, invFirst))
              case None           => None
            }
          case None => None
        }
      case MigrateField(name, m)    => invertMigration(m).map(MigrateField(name, _))
      case MigrateCase(name, m)     => invertMigration(m).map(MigrateCase(name, _))
      case MigrateElements(m)       => invertMigration(m).map(MigrateElements(_))
      case MigrateValues(m)         => invertMigration(m).map(MigrateValues(_))
    }
}
