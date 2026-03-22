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
import zio.blocks.schema.{DynamicOptic, DynamicValue}
import zio.blocks.schema.migration.MigrationAction._

/**
 * An untyped, pure, fully serializable migration engine that transforms a
 * `DynamicValue` from one schema shape to another.
 *
 * `DynamicMigration` represents a sequence of [[MigrationAction]]s, each
 * operating at a `DynamicOptic` path. Because it contains only data (no
 * functions, closures, or reflection), it can be:
 *
 *   - Serialized and stored in registries
 *   - Applied dynamically at runtime
 *   - Inspected and transformed
 *   - Used to generate SQL DDL / DML or offline data transforms
 *
 * Migrations can be composed with `++` and reversed with `reverse`. The
 * identity migration is `DynamicMigration.empty`.
 *
 * @see
 *   [[Migration]] for the typed, user-facing API
 */
final case class DynamicMigration(actions: Vector[MigrationAction]) {

  /**
   * Apply this migration to transform a `DynamicValue`.
   *
   * Actions are applied sequentially. Each action transforms the current value,
   * passing the result to the next action.
   *
   * @return
   *   `Right` with the migrated value, or `Left` with a [[MigrationError]]
   *   that includes the path at which the failure occurred.
   */
  def apply(value: DynamicValue): Either[MigrationError, DynamicValue] = {
    var current: DynamicValue = value
    val len                   = actions.length
    var idx                   = 0
    while (idx < len) {
      DynamicMigration.applyAction(current, actions(idx)) match {
        case Right(updated) => current = updated
        case l              => return l
      }
      idx += 1
    }
    new Right(current)
  }

  /**
   * Compose two migrations sequentially. The result applies `this` first, then
   * `that`.
   */
  def ++(that: DynamicMigration): DynamicMigration =
    new DynamicMigration(actions ++ that.actions)

  /**
   * The structural reverse of this migration. Applying `reverse` is a
   * best-effort semantic inverse: for lossless actions, it will fully invert
   * the migration.
   */
  def reverse: DynamicMigration =
    new DynamicMigration(actions.map(_.reverse).reverse)

  /** Returns true if this migration has no actions (identity). */
  def isEmpty: Boolean = actions.isEmpty

  override def toString: String =
    if (actions.isEmpty) "DynamicMigration {}"
    else {
      val sb = new java.lang.StringBuilder("DynamicMigration {\n")
      actions.foreach { action =>
        sb.append("  ").append(action).append('\n')
      }
      sb.append('}').toString
    }
}

object DynamicMigration {

  /** The empty (identity) migration. */
  val empty: DynamicMigration = new DynamicMigration(Vector.empty)

  /** Create a migration from a single action. */
  def fromAction(action: MigrationAction): DynamicMigration =
    new DynamicMigration(Vector(action))

  // ─────────────────────────────────────────────────────────────────────────
  // Action dispatch
  // ─────────────────────────────────────────────────────────────────────────

  private[migration] def applyAction(
    value: DynamicValue,
    action: MigrationAction
  ): Either[MigrationError, DynamicValue] =
    action match {
      case a: AddField          => applyStructural(value, a.at.nodes, 0, a.at, applyAddField(_, a))
      case a: DropField         => applyStructural(value, a.at.nodes, 0, a.at, applyDropField(_, a))
      case a: Rename            => applyStructural(value, a.at.nodes, 0, a.at, applyRename(_, a))
      case a: TransformValue    => applyAtPath(value, a.at.nodes, 0, a.at, applyTransformValue(_, a))
      case a: Mandate           => applyAtPath(value, a.at.nodes, 0, a.at, applyMandate(_, a))
      case a: Optionalize       => applyAtPath(value, a.at.nodes, 0, a.at, applyOptionalize)
      case a: RenameCase        => applyAtPath(value, a.at.nodes, 0, a.at, applyRenameCase(_, a))
      case a: TransformCase     => applyAtPath(value, a.at.nodes, 0, a.at, applyTransformCase(_, a))
      case a: TransformElements => applyAtPath(value, a.at.nodes, 0, a.at, applyTransformElements(_, a))
      case a: TransformKeys     => applyAtPath(value, a.at.nodes, 0, a.at, applyTransformKeys(_, a))
      case a: TransformValues   => applyAtPath(value, a.at.nodes, 0, a.at, applyTransformValues(_, a))
    }

  // ─────────────────────────────────────────────────────────────────────────
  // Navigation helpers
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Navigate to the *parent container* of the last path node and apply a
   * structural operation (AddField / DropField / Rename) there.
   *
   * If the path has only one node, the operation is applied at the root level.
   * If the path has more nodes, we navigate to the container first.
   */
  private[this] def applyStructural(
    value: DynamicValue,
    path: IndexedSeq[DynamicOptic.Node],
    pathIdx: Int,
    fullPath: DynamicOptic,
    op: DynamicValue => Either[MigrationError, DynamicValue]
  ): Either[MigrationError, DynamicValue] = {
    val remaining = path.length - pathIdx
    if (remaining <= 1) {
      // We're at the container; the last node tells us the field name
      op(value)
    } else {
      // Navigate one more level into the container
      navigateOneLevel(value, path(pathIdx), path, pathIdx, fullPath) { inner =>
        applyStructural(inner, path, pathIdx + 1, fullPath, op)
      }
    }
  }

  /**
   * Navigate to the value *at* the full path and apply an operation there.
   */
  private[this] def applyAtPath(
    value: DynamicValue,
    path: IndexedSeq[DynamicOptic.Node],
    pathIdx: Int,
    fullPath: DynamicOptic,
    op: DynamicValue => Either[MigrationError, DynamicValue]
  ): Either[MigrationError, DynamicValue] =
    if (pathIdx == path.length) {
      op(value)
    } else {
      navigateOneLevel(value, path(pathIdx), path, pathIdx, fullPath) { inner =>
        applyAtPath(inner, path, pathIdx + 1, fullPath, op)
      }
    }

  /**
   * Navigate one level into `value` using `node`, recursively apply `f` to the
   * inner value, then rebuild the outer structure.
   */
  private[this] def navigateOneLevel(
    value: DynamicValue,
    node: DynamicOptic.Node,
    path: IndexedSeq[DynamicOptic.Node],
    pathIdx: Int,
    fullPath: DynamicOptic
  )(f: DynamicValue => Either[MigrationError, DynamicValue]): Either[MigrationError, DynamicValue] = {
    val nodePath = new DynamicOptic(path.take(pathIdx + 1))
    node match {
      case field: DynamicOptic.Node.Field =>
        value match {
          case rec: DynamicValue.Record =>
            val fields   = rec.fields
            val fieldIdx = fields.indexWhere(_._1 == field.name)
            if (fieldIdx < 0) new Left(MigrationError.missingField(nodePath, field.name))
            else {
              val (fn, fv) = fields(fieldIdx)
              f(fv) match {
                case Right(updated) => new Right(new DynamicValue.Record(fields.updated(fieldIdx, (fn, updated))))
                case l              => l
              }
            }
          case _ =>
            new Left(MigrationError.typeMismatch(nodePath, "Record", value.getClass.getSimpleName))
        }

      case c: DynamicOptic.Node.Case =>
        value match {
          case v: DynamicValue.Variant if v.caseNameValue == c.name =>
            f(v.value) match {
              case Right(updated) => new Right(new DynamicValue.Variant(v.caseNameValue, updated))
              case l              => l
            }
          case _: DynamicValue.Variant => new Right(value) // case doesn't match, skip
          case _ =>
            new Left(MigrationError.typeMismatch(nodePath, "Variant", value.getClass.getSimpleName))
        }

      case ai: DynamicOptic.Node.AtIndex =>
        value match {
          case seq: DynamicValue.Sequence =>
            val elements = seq.elements
            val index    = ai.index
            if (index < 0 || index >= elements.length)
              new Left(MigrationError.atPath(nodePath, s"Index $index out of bounds (length ${elements.length})"))
            else
              f(elements(index)) match {
                case Right(updated) => new Right(new DynamicValue.Sequence(elements.updated(index, updated)))
                case l              => l
              }
          case _ =>
            new Left(MigrationError.typeMismatch(nodePath, "Sequence", value.getClass.getSimpleName))
        }

      case _: DynamicOptic.Node.Elements.type =>
        value match {
          case seq: DynamicValue.Sequence =>
            val elements = seq.elements
            val len      = elements.length
            val results  = new Array[DynamicValue](len)
            var i        = 0
            while (i < len) {
              f(elements(i)) match {
                case Right(updated) => results(i) = updated
                case l              => return l
              }
              i += 1
            }
            new Right(new DynamicValue.Sequence(Chunk.fromArray(results)))
          case _ =>
            new Left(MigrationError.typeMismatch(nodePath, "Sequence", value.getClass.getSimpleName))
        }

      case amk: DynamicOptic.Node.AtMapKey =>
        value match {
          case m: DynamicValue.Map =>
            val entries  = m.entries
            val entryIdx = entries.indexWhere(_._1 == amk.key)
            if (entryIdx < 0)
              new Left(MigrationError.atPath(nodePath, s"Key not found in map: ${amk.key}"))
            else {
              val (k, v) = entries(entryIdx)
              f(v) match {
                case Right(updated) => new Right(new DynamicValue.Map(entries.updated(entryIdx, (k, updated))))
                case l              => l
              }
            }
          case _ =>
            new Left(MigrationError.typeMismatch(nodePath, "Map", value.getClass.getSimpleName))
        }

      case _: DynamicOptic.Node.MapKeys.type =>
        value match {
          case m: DynamicValue.Map =>
            val entries = m.entries
            val len     = entries.length
            val results = new Array[(DynamicValue, DynamicValue)](len)
            var i       = 0
            while (i < len) {
              val (k, v) = entries(i)
              f(k) match {
                case Right(newKey) => results(i) = (newKey, v)
                case l             => return l
              }
              i += 1
            }
            new Right(new DynamicValue.Map(Chunk.fromArray(results)))
          case _ =>
            new Left(MigrationError.typeMismatch(nodePath, "Map", value.getClass.getSimpleName))
        }

      case _: DynamicOptic.Node.MapValues.type =>
        value match {
          case m: DynamicValue.Map =>
            val entries = m.entries
            val len     = entries.length
            val results = new Array[(DynamicValue, DynamicValue)](len)
            var i       = 0
            while (i < len) {
              val (k, v) = entries(i)
              f(v) match {
                case Right(newVal) => results(i) = (k, newVal)
                case l             => return l
              }
              i += 1
            }
            new Right(new DynamicValue.Map(Chunk.fromArray(results)))
          case _ =>
            new Left(MigrationError.typeMismatch(nodePath, "Map", value.getClass.getSimpleName))
        }

      case _: DynamicOptic.Node.Wrapped.type =>
        // Wrapped values are passed through transparently
        f(value)

      case _ =>
        new Left(MigrationError.atPath(nodePath, s"Unsupported navigation node in migration: ${node.getClass.getSimpleName}"))
    }
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Structural operations (operate on the container at path.init)
  // ─────────────────────────────────────────────────────────────────────────

  private[this] def applyAddField(
    container: DynamicValue,
    action: AddField
  ): Either[MigrationError, DynamicValue] = {
    val fieldName = action.at.nodes.lastOption match {
      case Some(f: DynamicOptic.Node.Field) => f.name
      case _ =>
        return new Left(MigrationError.atPath(action.at, "AddField: last path node must be a Field node"))
    }
    container match {
      case rec: DynamicValue.Record =>
        if (rec.fields.exists(_._1 == fieldName))
          new Right(container) // Field already exists; idempotent
        else
          new Right(new DynamicValue.Record(rec.fields :+ (fieldName -> action.default)))
      case _ =>
        new Left(MigrationError.typeMismatch(action.at, "Record", container.getClass.getSimpleName))
    }
  }

  private[this] def applyDropField(
    container: DynamicValue,
    action: DropField
  ): Either[MigrationError, DynamicValue] = {
    val fieldName = action.at.nodes.lastOption match {
      case Some(f: DynamicOptic.Node.Field) => f.name
      case _ =>
        return new Left(MigrationError.atPath(action.at, "DropField: last path node must be a Field node"))
    }
    container match {
      case rec: DynamicValue.Record =>
        val idx = rec.fields.indexWhere(_._1 == fieldName)
        if (idx < 0) new Right(container) // Field doesn't exist; idempotent
        else new Right(new DynamicValue.Record(rec.fields.take(idx) ++ rec.fields.drop(idx + 1)))
      case _ =>
        new Left(MigrationError.typeMismatch(action.at, "Record", container.getClass.getSimpleName))
    }
  }

  private[this] def applyRename(
    container: DynamicValue,
    action: Rename
  ): Either[MigrationError, DynamicValue] = {
    val fromName = action.at.nodes.lastOption match {
      case Some(f: DynamicOptic.Node.Field) => f.name
      case _ =>
        return new Left(MigrationError.atPath(action.at, "Rename: last path node must be a Field node"))
    }
    container match {
      case rec: DynamicValue.Record =>
        val idx = rec.fields.indexWhere(_._1 == fromName)
        if (idx < 0) new Left(MigrationError.missingField(action.at, fromName))
        else {
          val (_, value) = rec.fields(idx)
          new Right(new DynamicValue.Record(rec.fields.updated(idx, (action.to, value))))
        }
      case _ =>
        new Left(MigrationError.typeMismatch(action.at, "Record", container.getClass.getSimpleName))
    }
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Value operations (operate on the value AT the path)
  // ─────────────────────────────────────────────────────────────────────────

  private[this] def applyTransformValue(
    value: DynamicValue,
    action: TransformValue
  ): Either[MigrationError, DynamicValue] =
    action.transform(value) match {
      case Right(updated) => new Right(updated)
      case Left(err) =>
        val path = if (err.path.nodes.isEmpty) action.at else err.path
        new Left(new MigrationError(err.message, path))
    }

  private[this] def applyMandate(
    value: DynamicValue,
    action: Mandate
  ): Either[MigrationError, DynamicValue] =
    value match {
      case v: DynamicValue.Variant =>
        if (v.caseNameValue == "Some") new Right(v.value)
        else if (v.caseNameValue == "None") new Right(action.default)
        else new Left(MigrationError.atPath(action.at, s"Mandate: expected Some/None Variant but got case '${v.caseNameValue}'"))
      case DynamicValue.Null =>
        new Right(action.default)
      case _ =>
        // Already a non-optional value; pass through
        new Right(value)
    }

  private[this] def applyOptionalize(
    value: DynamicValue
  ): Either[MigrationError, DynamicValue] =
    value match {
      case v: DynamicValue.Variant if v.caseNameValue == "Some" || v.caseNameValue == "None" =>
        new Right(value) // Already optional; pass through
      case DynamicValue.Null =>
        new Right(new DynamicValue.Variant("None", DynamicValue.Null))
      case _ =>
        new Right(new DynamicValue.Variant("Some", value))
    }

  private[this] def applyRenameCase(
    value: DynamicValue,
    action: RenameCase
  ): Either[MigrationError, DynamicValue] =
    value match {
      case v: DynamicValue.Variant if v.caseNameValue == action.from =>
        new Right(new DynamicValue.Variant(action.to, v.value))
      case _ =>
        new Right(value) // Case doesn't match; pass through (other cases are unchanged)
    }

  private[this] def applyTransformCase(
    value: DynamicValue,
    action: TransformCase
  ): Either[MigrationError, DynamicValue] =
    value match {
      case v: DynamicValue.Variant if v.caseNameValue == action.caseName =>
        // Apply the nested migration to the inner value
        val nestedMigration = new DynamicMigration(action.actions)
        nestedMigration(v.value) match {
          case Right(updated) => new Right(new DynamicValue.Variant(v.caseNameValue, updated))
          case l              => l
        }
      case _ =>
        new Right(value) // Case doesn't match; pass through
    }

  private[this] def applyTransformElements(
    value: DynamicValue,
    action: TransformElements
  ): Either[MigrationError, DynamicValue] =
    value match {
      case seq: DynamicValue.Sequence =>
        val elements = seq.elements
        val len      = elements.length
        val results  = new Array[DynamicValue](len)
        var i        = 0
        while (i < len) {
          action.transform(elements(i)) match {
            case Right(updated) => results(i) = updated
            case l              => return l
          }
          i += 1
        }
        new Right(new DynamicValue.Sequence(Chunk.fromArray(results)))
      case _ =>
        new Left(MigrationError.typeMismatch(action.at, "Sequence", value.getClass.getSimpleName))
    }

  private[this] def applyTransformKeys(
    value: DynamicValue,
    action: TransformKeys
  ): Either[MigrationError, DynamicValue] =
    value match {
      case m: DynamicValue.Map =>
        val entries = m.entries
        val len     = entries.length
        val results = new Array[(DynamicValue, DynamicValue)](len)
        var i       = 0
        while (i < len) {
          val (k, v) = entries(i)
          action.transform(k) match {
            case Right(newKey) => results(i) = (newKey, v)
            case l             => return l
          }
          i += 1
        }
        new Right(new DynamicValue.Map(Chunk.fromArray(results)))
      case _ =>
        new Left(MigrationError.typeMismatch(action.at, "Map", value.getClass.getSimpleName))
    }

  private[this] def applyTransformValues(
    value: DynamicValue,
    action: TransformValues
  ): Either[MigrationError, DynamicValue] =
    value match {
      case m: DynamicValue.Map =>
        val entries = m.entries
        val len     = entries.length
        val results = new Array[(DynamicValue, DynamicValue)](len)
        var i       = 0
        while (i < len) {
          val (k, v) = entries(i)
          action.transform(v) match {
            case Right(newVal) => results(i) = (k, newVal)
            case l             => return l
          }
          i += 1
        }
        new Right(new DynamicValue.Map(Chunk.fromArray(results)))
      case _ =>
        new Left(MigrationError.typeMismatch(action.at, "Map", value.getClass.getSimpleName))
    }
}
