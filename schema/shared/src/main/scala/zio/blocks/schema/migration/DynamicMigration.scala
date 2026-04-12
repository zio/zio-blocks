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

import zio.blocks.chunk.ChunkBuilder
import zio.blocks.schema.{DynamicOptic, DynamicValue, OpticCheck, SchemaExpr}

/**
 * A migration program that operates purely on [[zio.blocks.schema.DynamicValue]]
 * using [[zio.blocks.schema.DynamicOptic]] paths.
 *
 * Actions are applied sequentially in the order they appear in `actions`.
 */
final case class DynamicMigration(actions: Vector[MigrationAction]) {
  /**
   * Applies this migration to `value`, executing actions sequentially and
   * returning the first error encountered (if any).
   */
  def apply(value: DynamicValue): Either[MigrationError, DynamicValue] = {
    val len = actions.length
    var idx = 0
    var cur = value
    while (idx < len) {
      DynamicMigration.applyAction(actions(idx), cur) match {
        case Right(next) => cur = next
        case left        => return left
      }
      idx += 1
    }
    new Right(cur)
  }

  /** Concatenates migrations by appending `that` migration's actions. */
  def ++(that: DynamicMigration): DynamicMigration = DynamicMigration(actions ++ that.actions)

  /**
   * Reverses this migration by reversing the action order and reversing each
   * individual action.
   */
  def reverse: DynamicMigration = DynamicMigration(actions.reverse.map(_.reverse))
}

/** Interpreter implementation and internal helpers for [[DynamicMigration]]. */
object DynamicMigration {
  private def applyAction(action: MigrationAction, value: DynamicValue): Either[MigrationError, DynamicValue] =
    action match {
      case a: AddField =>
        splitFieldPath(a.at) match {
          case Right((parentPath, fieldName)) =>
            for {
              defaultValue <- evalOne(a.default, (), a.at)
              updated <- updateAtPath(value, parentPath, a.at, {
                case r: DynamicValue.Record =>
                  val fields = r.fields
                  val len    = fields.length
                  var idx    = 0
                  var exists = false
                  while (idx < len && !exists) {
                    if (fields(idx)._1 == fieldName) exists = true
                    idx += 1
                  }
                  if (exists) new Right(r) // already exists: leave as-is
                  else new Right(new DynamicValue.Record(fields.appended((fieldName, defaultValue))))
                case other =>
                  new Left(TypeMismatch(a.at, expected = "Record", got = other.valueType.toString))
              })
            } yield updated
          case Left(error) => new Left(MigrationFailed(a.at, error))
        }

      case d: DropField =>
        splitFieldPath(d.at) match {
          case Right((parentPath, fieldName)) =>
            updateAtPath(value, parentPath, d.at, {
              case r: DynamicValue.Record =>
                val fields    = r.fields
                val newFields = fields.filterNot(_._1 == fieldName)
                if (newFields.length == fields.length) new Left(FieldNotFound(d.at, fieldName))
                else new Right(new DynamicValue.Record(newFields))
              case other =>
                new Left(TypeMismatch(d.at, expected = "Record", got = other.valueType.toString))
            })
          case Left(error) => new Left(MigrationFailed(d.at, error))
        }

      case r: Rename =>
        splitFieldPath(r.at) match {
          case Right((parentPath, from)) =>
            updateAtPath(value, parentPath, r.at, {
              case rec: DynamicValue.Record =>
                val fields = rec.fields
                val len    = fields.length
                val b      = ChunkBuilder.make[(String, DynamicValue)](len)
                var idx    = 0
                var found  = false
                while (idx < len) {
                  val kv = fields(idx)
                  if (kv._1 == from) {
                    found = true
                    b.addOne((r.to, kv._2))
                  } else b.addOne(kv)
                  idx += 1
                }
                if (!found) new Left(FieldNotFound(r.at, from))
                else new Right(new DynamicValue.Record(b.result()))
              case other =>
                new Left(TypeMismatch(r.at, expected = "Record", got = other.valueType.toString))
            })
          case Left(error) => new Left(MigrationFailed(r.at, error))
        }

      case t: TransformValue =>
        updateAtPath(value, t.at, t.at, dv => evalOne(t.transform, dv, t.at))

      case r: RenameCase =>
        updateAtPath(value, r.at, r.at, {
          case v: DynamicValue.Variant =>
            if (v.caseNameValue == r.from) new Right(new DynamicValue.Variant(r.to, v.value))
            else new Left(MigrationFailed(r.at, s"Variant case '${v.caseNameValue}' does not match '${r.from}'"))
          case other =>
            new Left(TypeMismatch(r.at, expected = "Variant", got = other.valueType.toString))
        })

      case t: TransformCase =>
        val nested = DynamicMigration(t.actions)
        updateAtPath(value, t.at, t.at, dv => nested(dv))

      case t: TransformElements =>
        updateAtPath(value, t.at, t.at, {
          case s: DynamicValue.Sequence =>
            val elements = s.elements
            val len      = elements.length
            val b        = ChunkBuilder.make[DynamicValue](len)
            var idx      = 0
            var error: Option[MigrationError] = None
            while (idx < len && error.isEmpty) {
              evalOne(t.transform, elements(idx), t.at) match {
                case Right(nv) => b.addOne(nv)
                case Left(err) => error = Some(err)
              }
              idx += 1
            }
            error match {
              case Some(err) => new Left(err)
              case None      => new Right(new DynamicValue.Sequence(b.result()))
            }
          case other =>
            new Left(TypeMismatch(t.at, expected = "Sequence", got = other.valueType.toString))
        })

      case t: TransformValues =>
        updateAtPath(value, t.at, t.at, {
          case m: DynamicValue.Map =>
            val entries = m.entries
            val len     = entries.length
            val b       = ChunkBuilder.make[(DynamicValue, DynamicValue)](len)
            var idx     = 0
            var error: Option[MigrationError] = None
            while (idx < len && error.isEmpty) {
              val kv = entries(idx)
              evalOne(t.transform, kv._2, t.at) match {
                case Right(nv) => b.addOne((kv._1, nv))
                case Left(err) => error = Some(err)
              }
              idx += 1
            }
            error match {
              case Some(err) => new Left(err)
              case None      => new Right(new DynamicValue.Map(b.result()))
            }
          case other =>
            new Left(TypeMismatch(t.at, expected = "Map", got = other.valueType.toString))
        })

      case other =>
        new Left(MigrationFailed(other.at, s"Unsupported migration action: ${other.getClass.getSimpleName}"))
    }

  private def evalOne(expr: SchemaExpr[Any, _], input: Any, at: DynamicOptic): Either[MigrationError, DynamicValue] =
    expr.evalDynamic(input) match {
      case Right(values) =>
        if (values.length == 1) new Right(values.head)
        else new Left(MigrationFailed(at, s"Expected expression to evaluate to 1 value but got ${values.length}"))
      case Left(check) => new Left(MigrationFailed(at, renderOpticCheck(check)))
    }

  private def renderOpticCheck(check: OpticCheck): String = check.toString

  private def splitFieldPath(at: DynamicOptic): Either[String, (DynamicOptic, String)] = {
    val nodes = at.nodes
    if (nodes.isEmpty) new Left("Expected optic ending in a field, but path was empty")
    else
      nodes.last match {
        case DynamicOptic.Node.Field(name) => new Right((new DynamicOptic(nodes.dropRight(1)), name))
        case _                             => new Left(s"Expected optic ending in a field, but got: $at")
      }
  }

  private def updateAtPath(
    value: DynamicValue,
    path: DynamicOptic,
    actionAt: DynamicOptic,
    f: DynamicValue => Either[MigrationError, DynamicValue]
  ): Either[MigrationError, DynamicValue] =
    updateAtPath(value, path.nodes, 0, actionAt, f)

  private def updateAtPath(
    value: DynamicValue,
    nodes: IndexedSeq[DynamicOptic.Node],
    index: Int,
    actionAt: DynamicOptic,
    f: DynamicValue => Either[MigrationError, DynamicValue]
  ): Either[MigrationError, DynamicValue] = {
    if (index >= nodes.length) return f(value)

    nodes(index) match {
      case DynamicOptic.Node.Field(name) =>
        value match {
          case r: DynamicValue.Record =>
            val fields = r.fields
            val len    = fields.length
            val b      = ChunkBuilder.make[(String, DynamicValue)](len)
            var idx    = 0
            var found  = false
            while (idx < len) {
              val kv = fields(idx)
              if (kv._1 == name) {
                found = true
                updateAtPath(kv._2, nodes, index + 1, actionAt, f) match {
                  case Right(nv)  => b.addOne((kv._1, nv))
                  case Left(err)  => return new Left(err)
                }
              } else b.addOne(kv)
              idx += 1
            }
            if (!found) new Left(FieldNotFound(actionAt, name))
            else new Right(new DynamicValue.Record(b.result()))
          case other =>
            new Left(TypeMismatch(actionAt, expected = "Record", got = other.valueType.toString))
        }

      case DynamicOptic.Node.Case(name) =>
        value match {
          case v: DynamicValue.Variant if v.caseNameValue == name =>
            updateAtPath(v.value, nodes, index + 1, actionAt, f) match {
              case Right(nv) => new Right(new DynamicValue.Variant(v.caseNameValue, nv))
              case left      => left
            }
          case v: DynamicValue.Variant =>
            new Left(MigrationFailed(actionAt, s"Variant case '${v.caseNameValue}' does not match '$name'"))
          case other =>
            new Left(TypeMismatch(actionAt, expected = "Variant", got = other.valueType.toString))
        }

      case DynamicOptic.Node.AtIndex(i) =>
        value match {
          case s: DynamicValue.Sequence =>
            if (i < 0 || i >= s.elements.length) new Left(MigrationFailed(actionAt, s"Index $i out of bounds"))
            else
              updateAtPath(s.elements(i), nodes, index + 1, actionAt, f) match {
                case Right(nv) => new Right(new DynamicValue.Sequence(s.elements.updated(i, nv)))
                case left      => left
              }
          case other =>
            new Left(TypeMismatch(actionAt, expected = "Sequence", got = other.valueType.toString))
        }

      case DynamicOptic.Node.AtIndices(indices) =>
        value match {
          case s: DynamicValue.Sequence =>
            val indicesSet = indices.toSet
            val elements   = s.elements
            val len        = elements.length
            if (len == 0) return new Left(MigrationFailed(actionAt, "Path not found"))
            val b     = ChunkBuilder.make[DynamicValue](len)
            var idx   = 0
            var found = false
            while (idx < len) {
              val e = elements(idx)
              if (indicesSet.contains(idx)) {
                updateAtPath(e, nodes, index + 1, actionAt, f) match {
                  case Right(nv) =>
                    found = true
                    b.addOne(nv)
                  case Left(err) => return new Left(err)
                }
              } else b.addOne(e)
              idx += 1
            }
            if (!found) new Left(MigrationFailed(actionAt, "Path not found"))
            else new Right(new DynamicValue.Sequence(b.result()))
          case other =>
            new Left(TypeMismatch(actionAt, expected = "Sequence", got = other.valueType.toString))
        }

      case DynamicOptic.Node.AtMapKey(key) =>
        value match {
          case m: DynamicValue.Map =>
            val entries = m.entries
            val len     = entries.length
            if (len == 0) return new Left(MigrationFailed(actionAt, "Path not found"))
            val b     = ChunkBuilder.make[(DynamicValue, DynamicValue)](len)
            var idx   = 0
            var found = false
            while (idx < len) {
              val kv = entries(idx)
              if (kv._1 == key) {
                updateAtPath(kv._2, nodes, index + 1, actionAt, f) match {
                  case Right(nv) =>
                    found = true
                    b.addOne((kv._1, nv))
                  case Left(err) => return new Left(err)
                }
              } else b.addOne(kv)
              idx += 1
            }
            if (!found) new Left(MigrationFailed(actionAt, "Path not found"))
            else new Right(new DynamicValue.Map(b.result()))
          case other =>
            new Left(TypeMismatch(actionAt, expected = "Map", got = other.valueType.toString))
        }

      case DynamicOptic.Node.AtMapKeys(keys) =>
        value match {
          case m: DynamicValue.Map =>
            val keysSet = keys.toSet
            val entries = m.entries
            val len     = entries.length
            if (len == 0) return new Left(MigrationFailed(actionAt, "Path not found"))
            val b     = ChunkBuilder.make[(DynamicValue, DynamicValue)](len)
            var idx   = 0
            var found = false
            while (idx < len) {
              val kv = entries(idx)
              if (keysSet.contains(kv._1)) {
                updateAtPath(kv._2, nodes, index + 1, actionAt, f) match {
                  case Right(nv) =>
                    found = true
                    b.addOne((kv._1, nv))
                  case Left(err) => return new Left(err)
                }
              } else b.addOne(kv)
              idx += 1
            }
            if (!found) new Left(MigrationFailed(actionAt, "Path not found"))
            else new Right(new DynamicValue.Map(b.result()))
          case other =>
            new Left(TypeMismatch(actionAt, expected = "Map", got = other.valueType.toString))
        }

      case DynamicOptic.Node.Elements =>
        value match {
          case s: DynamicValue.Sequence =>
            val elements = s.elements
            val len      = elements.length
            if (len == 0) return new Left(MigrationFailed(actionAt, "Path not found"))
            val b   = ChunkBuilder.make[DynamicValue](len)
            var idx = 0
            while (idx < len) {
              updateAtPath(elements(idx), nodes, index + 1, actionAt, f) match {
                case Right(nv)  => b.addOne(nv)
                case Left(err)  => return new Left(err)
              }
              idx += 1
            }
            new Right(new DynamicValue.Sequence(b.result()))
          case other =>
            new Left(TypeMismatch(actionAt, expected = "Sequence", got = other.valueType.toString))
        }

      case DynamicOptic.Node.MapKeys =>
        value match {
          case m: DynamicValue.Map =>
            val entries = m.entries
            val len     = entries.length
            if (len == 0) return new Left(MigrationFailed(actionAt, "Path not found"))
            val b   = ChunkBuilder.make[(DynamicValue, DynamicValue)](len)
            var idx = 0
            while (idx < len) {
              val kv = entries(idx)
              updateAtPath(kv._1, nodes, index + 1, actionAt, f) match {
                case Right(nk)  => b.addOne((nk, kv._2))
                case Left(err)  => return new Left(err)
              }
              idx += 1
            }
            new Right(new DynamicValue.Map(b.result()))
          case other =>
            new Left(TypeMismatch(actionAt, expected = "Map", got = other.valueType.toString))
        }

      case DynamicOptic.Node.MapValues =>
        value match {
          case m: DynamicValue.Map =>
            val entries = m.entries
            val len     = entries.length
            if (len == 0) return new Left(MigrationFailed(actionAt, "Path not found"))
            val b   = ChunkBuilder.make[(DynamicValue, DynamicValue)](len)
            var idx = 0
            while (idx < len) {
              val kv = entries(idx)
              updateAtPath(kv._2, nodes, index + 1, actionAt, f) match {
                case Right(nv)  => b.addOne((kv._1, nv))
                case Left(err)  => return new Left(err)
              }
              idx += 1
            }
            new Right(new DynamicValue.Map(b.result()))
          case other =>
            new Left(TypeMismatch(actionAt, expected = "Map", got = other.valueType.toString))
        }

      case DynamicOptic.Node.Wrapped =>
        updateAtPath(value, nodes, index + 1, actionAt, f)

      case _: DynamicOptic.Node.TypeSearch =>
        new Left(MigrationFailed(actionAt, "TypeSearch is not supported by DynamicMigration"))

      case DynamicOptic.Node.SchemaSearch(_) =>
        new Left(MigrationFailed(actionAt, "SchemaSearch is not supported by DynamicMigration"))
    }
  }
}
