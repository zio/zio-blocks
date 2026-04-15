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

/**
 * An untyped, serializable migration that transforms a [[DynamicValue]] from
 * one schema shape to another. Migrations are composed of a sequence of
 * [[MigrationAction]]s applied in order.
 *
 * {{{
 * val migration = DynamicMigration(Chunk(
 *   MigrationAction.RenameField(DynamicOptic.root, "name", "fullName"),
 *   MigrationAction.AddField(DynamicOptic.root, "email", DynamicValue.Primitive(PrimitiveValue.String(""))),
 *   MigrationAction.DropField(DynamicOptic.root, "age")
 * ))
 *
 * migration(oldValue) // Right(migratedValue)
 * }}}
 */
final case class DynamicMigration(actions: Chunk[MigrationAction]) {

  /** Apply this migration to a DynamicValue. */
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
   * Compose two migrations. The result applies this migration first, then that.
   */
  def andThen(that: DynamicMigration): DynamicMigration =
    new DynamicMigration(actions ++ that.actions)

  /** Check if this migration is empty (no actions). */
  def isEmpty: Boolean = actions.isEmpty
}

object DynamicMigration {

  /** Empty migration -- identity element for composition. */
  val identity: DynamicMigration = DynamicMigration(Chunk.empty)

  /** Create a migration with a single action. */
  def single(action: MigrationAction): DynamicMigration =
    new DynamicMigration(Chunk.single(action))

  // ---------------------------------------------------------------------------
  // Action dispatch
  // ---------------------------------------------------------------------------

  private def applyAction(
    value: DynamicValue,
    action: MigrationAction
  ): Either[MigrationError, DynamicValue] = {
    val path = action.path.nodes
    if (path.isEmpty) applyAtTarget(value, action)
    else navigateAndApply(value, path, 0, action)
  }

  // ---------------------------------------------------------------------------
  // Path navigation -- follows the same recursive pattern as DynamicPatch
  // ---------------------------------------------------------------------------

  private def navigateAndApply(
    value: DynamicValue,
    path: IndexedSeq[DynamicOptic.Node],
    pathIdx: Int,
    action: MigrationAction
  ): Either[MigrationError, DynamicValue] = {
    val node   = path(pathIdx)
    val isLast = pathIdx == path.length - 1
    node match {
      case f: DynamicOptic.Node.Field =>
        val name = f.name
        value match {
          case r: DynamicValue.Record =>
            val fields   = r.fields
            val fieldIdx = fields.indexWhere(_._1 == name)
            if (fieldIdx < 0)
              new Left(MigrationError.InvalidPath(action.path, s"Field '$name' not found during navigation"))
            else {
              val (fieldName, fieldValue) = fields(fieldIdx)
              (if (isLast) applyAtTarget(fieldValue, action)
               else navigateAndApply(fieldValue, path, pathIdx + 1, action)) match {
                case Right(v) => new Right(new DynamicValue.Record(fields.updated(fieldIdx, (fieldName, v))))
                case l        => l
              }
            }
          case _ =>
            new Left(MigrationError.TypeMismatch(action.path, "Record", value.getClass.getSimpleName))
        }

      case c: DynamicOptic.Node.Case =>
        value match {
          case DynamicValue.Variant(caseName, innerValue) =>
            if (caseName != c.name)
              new Left(MigrationError.TypeMismatch(action.path, s"Case(${c.name})", s"Case($caseName)"))
            else {
              (if (isLast) applyAtTarget(innerValue, action)
               else navigateAndApply(innerValue, path, pathIdx + 1, action)) match {
                case Right(v) => new Right(new DynamicValue.Variant(caseName, v))
                case l        => l
              }
            }
          case _ =>
            new Left(MigrationError.TypeMismatch(action.path, "Variant", value.getClass.getSimpleName))
        }

      case _: DynamicOptic.Node.Elements.type =>
        value match {
          case DynamicValue.Sequence(elements) =>
            if (isLast) applyToAllElements(elements, action)
            else navigateAllElements(elements, path, pathIdx + 1, action)
          case _ =>
            new Left(MigrationError.TypeMismatch(action.path, "Sequence", value.getClass.getSimpleName))
        }

      case DynamicOptic.Node.MapKeys =>
        value match {
          case DynamicValue.Map(entries) =>
            if (isLast) applyToAllMapKeys(entries, action)
            else new Left(MigrationError.InvalidPath(action.path, "MapKeys must be the last path segment"))
          case _ =>
            new Left(MigrationError.TypeMismatch(action.path, "Map", value.getClass.getSimpleName))
        }

      case DynamicOptic.Node.MapValues =>
        value match {
          case DynamicValue.Map(entries) =>
            if (isLast) applyToAllMapValues(entries, action)
            else navigateAllMapValues(entries, path, pathIdx + 1, action)
          case _ =>
            new Left(MigrationError.TypeMismatch(action.path, "Map", value.getClass.getSimpleName))
        }

      case _ =>
        new Left(MigrationError.InvalidPath(action.path, s"Unsupported path node: ${node.getClass.getSimpleName}"))
    }
  }

  // ---------------------------------------------------------------------------
  // Apply action at the target (path fully resolved)
  // ---------------------------------------------------------------------------

  private def applyAtTarget(
    value: DynamicValue,
    action: MigrationAction
  ): Either[MigrationError, DynamicValue] =
    action match {
      case a: MigrationAction.AddField =>
        value match {
          case r: DynamicValue.Record =>
            val fields = r.fields
            if (fields.exists(_._1 == a.fieldName))
              new Left(MigrationError.FieldAlreadyExists(a.path, a.fieldName))
            else
              new Right(new DynamicValue.Record(fields :+ ((a.fieldName, a.defaultValue))))
          case _ =>
            new Left(MigrationError.TypeMismatch(a.path, "Record", value.getClass.getSimpleName))
        }

      case d: MigrationAction.DropField =>
        value match {
          case r: DynamicValue.Record =>
            val fields   = r.fields
            val fieldIdx = fields.indexWhere(_._1 == d.fieldName)
            if (fieldIdx < 0)
              new Left(MigrationError.FieldNotFound(d.path, d.fieldName))
            else {
              val newFields = fields.take(fieldIdx) ++ fields.drop(fieldIdx + 1)
              new Right(new DynamicValue.Record(newFields))
            }
          case _ =>
            new Left(MigrationError.TypeMismatch(d.path, "Record", value.getClass.getSimpleName))
        }

      case r: MigrationAction.RenameField =>
        value match {
          case rec: DynamicValue.Record =>
            val fields   = rec.fields
            val fieldIdx = fields.indexWhere(_._1 == r.oldName)
            if (fieldIdx < 0)
              new Left(MigrationError.FieldNotFound(r.path, r.oldName))
            else if (fields.exists(_._1 == r.newName))
              new Left(MigrationError.FieldAlreadyExists(r.path, r.newName))
            else {
              val (_, fieldValue) = fields(fieldIdx)
              new Right(new DynamicValue.Record(fields.updated(fieldIdx, (r.newName, fieldValue))))
            }
          case _ =>
            new Left(MigrationError.TypeMismatch(r.path, "Record", value.getClass.getSimpleName))
        }

      case s: MigrationAction.SetValue =>
        new Right(s.value)

      case t: MigrationAction.TransformValue =>
        t.migration(value)

      case rc: MigrationAction.RenameCase =>
        value match {
          case DynamicValue.Variant(caseName, innerValue) =>
            if (caseName == rc.oldName)
              new Right(new DynamicValue.Variant(rc.newName, innerValue))
            else
              new Right(value) // Case doesn't match, leave unchanged
          case _ =>
            new Left(MigrationError.TypeMismatch(rc.path, "Variant", value.getClass.getSimpleName))
        }

      case tc: MigrationAction.TransformCase =>
        value match {
          case DynamicValue.Variant(caseName, innerValue) =>
            if (caseName == tc.caseName)
              tc.migration(innerValue).map(v => new DynamicValue.Variant(caseName, v))
            else
              new Right(value) // Case doesn't match, leave unchanged
          case _ =>
            new Left(MigrationError.TypeMismatch(tc.path, "Variant", value.getClass.getSimpleName))
        }

      case te: MigrationAction.TransformElements =>
        value match {
          case DynamicValue.Sequence(elements) =>
            applyMigrationToAll(elements, te.migration).map(DynamicValue.Sequence(_))
          case _ =>
            new Left(MigrationError.TypeMismatch(te.path, "Sequence", value.getClass.getSimpleName))
        }

      case tk: MigrationAction.TransformKeys =>
        value match {
          case DynamicValue.Map(entries) =>
            applyMigrationToMapKeys(entries, tk.migration).map(DynamicValue.Map(_))
          case _ =>
            new Left(MigrationError.TypeMismatch(tk.path, "Map", value.getClass.getSimpleName))
        }

      case tv: MigrationAction.TransformValues =>
        value match {
          case DynamicValue.Map(entries) =>
            applyMigrationToMapValues(entries, tv.migration).map(DynamicValue.Map(_))
          case _ =>
            new Left(MigrationError.TypeMismatch(tv.path, "Map", value.getClass.getSimpleName))
        }

      case ro: MigrationAction.ReorderFields =>
        value match {
          case r: DynamicValue.Record =>
            val fields     = r.fields
            val fieldMap   = fields.toMap
            val order      = ro.fieldOrder
            val ordered    = new Array[(String, DynamicValue)](fields.length)
            var orderedIdx = 0
            // First: place fields in specified order
            var i = 0
            while (i < order.length) {
              fieldMap.get(order(i)) match {
                case Some(v) =>
                  ordered(orderedIdx) = (order(i), v)
                  orderedIdx += 1
                case None => // skip fields not present
              }
              i += 1
            }
            // Then: append any remaining fields not in the order spec
            val orderSet = order.toSet
            fields.foreach { case (name, v) =>
              if (!orderSet.contains(name)) {
                ordered(orderedIdx) = (name, v)
                orderedIdx += 1
              }
            }
            new Right(new DynamicValue.Record(Chunk.fromArray(ordered).take(orderedIdx)))
          case _ =>
            new Left(MigrationError.TypeMismatch(ro.path, "Record", value.getClass.getSimpleName))
        }
    }

  // ---------------------------------------------------------------------------
  // Collection helpers
  // ---------------------------------------------------------------------------

  private def applyToAllElements(
    elements: Chunk[DynamicValue],
    action: MigrationAction
  ): Either[MigrationError, DynamicValue] = {
    val len     = elements.length
    val results = new Array[DynamicValue](len)
    var idx     = 0
    while (idx < len) {
      applyAtTarget(elements(idx), action) match {
        case Right(v) => results(idx) = v
        case l        => return l
      }
      idx += 1
    }
    new Right(new DynamicValue.Sequence(Chunk.fromArray(results)))
  }

  private def navigateAllElements(
    elements: Chunk[DynamicValue],
    path: IndexedSeq[DynamicOptic.Node],
    pathIdx: Int,
    action: MigrationAction
  ): Either[MigrationError, DynamicValue] = {
    val len     = elements.length
    val results = new Array[DynamicValue](len)
    var idx     = 0
    while (idx < len) {
      navigateAndApply(elements(idx), path, pathIdx, action) match {
        case Right(v) => results(idx) = v
        case l        => return l
      }
      idx += 1
    }
    new Right(new DynamicValue.Sequence(Chunk.fromArray(results)))
  }

  private def applyToAllMapKeys(
    entries: Chunk[(DynamicValue, DynamicValue)],
    action: MigrationAction
  ): Either[MigrationError, DynamicValue] = {
    val len     = entries.length
    val results = new Array[(DynamicValue, DynamicValue)](len)
    var idx     = 0
    while (idx < len) {
      val (k, v) = entries(idx)
      applyAtTarget(k, action) match {
        case Right(nk) => results(idx) = (nk, v)
        case l         => return l.asInstanceOf[Either[MigrationError, DynamicValue]]
      }
      idx += 1
    }
    new Right(new DynamicValue.Map(Chunk.fromArray(results)))
  }

  private def applyToAllMapValues(
    entries: Chunk[(DynamicValue, DynamicValue)],
    action: MigrationAction
  ): Either[MigrationError, DynamicValue] = {
    val len     = entries.length
    val results = new Array[(DynamicValue, DynamicValue)](len)
    var idx     = 0
    while (idx < len) {
      val (k, v) = entries(idx)
      applyAtTarget(v, action) match {
        case Right(nv) => results(idx) = (k, nv)
        case l         => return l.asInstanceOf[Either[MigrationError, DynamicValue]]
      }
      idx += 1
    }
    new Right(new DynamicValue.Map(Chunk.fromArray(results)))
  }

  private def navigateAllMapValues(
    entries: Chunk[(DynamicValue, DynamicValue)],
    path: IndexedSeq[DynamicOptic.Node],
    pathIdx: Int,
    action: MigrationAction
  ): Either[MigrationError, DynamicValue] = {
    val len     = entries.length
    val results = new Array[(DynamicValue, DynamicValue)](len)
    var idx     = 0
    while (idx < len) {
      val (k, v) = entries(idx)
      navigateAndApply(v, path, pathIdx, action) match {
        case Right(nv) => results(idx) = (k, nv)
        case l         => return l.asInstanceOf[Either[MigrationError, DynamicValue]]
      }
      idx += 1
    }
    new Right(new DynamicValue.Map(Chunk.fromArray(results)))
  }

  private def applyMigrationToAll(
    elements: Chunk[DynamicValue],
    migration: DynamicMigration
  ): Either[MigrationError, Chunk[DynamicValue]] = {
    val len     = elements.length
    val results = new Array[DynamicValue](len)
    var idx     = 0
    while (idx < len) {
      migration(elements(idx)) match {
        case Right(v) => results(idx) = v
        case l        => return l.asInstanceOf[Either[MigrationError, Chunk[DynamicValue]]]
      }
      idx += 1
    }
    new Right(Chunk.fromArray(results))
  }

  private def applyMigrationToMapKeys(
    entries: Chunk[(DynamicValue, DynamicValue)],
    migration: DynamicMigration
  ): Either[MigrationError, Chunk[(DynamicValue, DynamicValue)]] = {
    val len     = entries.length
    val results = new Array[(DynamicValue, DynamicValue)](len)
    var idx     = 0
    while (idx < len) {
      val (k, v) = entries(idx)
      migration(k) match {
        case Right(nk) => results(idx) = (nk, v)
        case l         => return l.asInstanceOf[Either[MigrationError, Chunk[(DynamicValue, DynamicValue)]]]
      }
      idx += 1
    }
    new Right(Chunk.fromArray(results))
  }

  private def applyMigrationToMapValues(
    entries: Chunk[(DynamicValue, DynamicValue)],
    migration: DynamicMigration
  ): Either[MigrationError, Chunk[(DynamicValue, DynamicValue)]] = {
    val len     = entries.length
    val results = new Array[(DynamicValue, DynamicValue)](len)
    var idx     = 0
    while (idx < len) {
      val (k, v) = entries(idx)
      migration(v) match {
        case Right(nv) => results(idx) = (k, nv)
        case l         => return l.asInstanceOf[Either[MigrationError, Chunk[(DynamicValue, DynamicValue)]]]
      }
      idx += 1
    }
    new Right(Chunk.fromArray(results))
  }
}
