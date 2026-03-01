package zio.blocks.schema.migration

import zio.blocks.chunk.ChunkBuilder
import zio.blocks.schema.{DynamicOptic, DynamicValue, PrimitiveValue}

/**
 * A pure, serializable migration that transforms [[DynamicValue]] instances.
 *
 * `DynamicMigration` operates entirely on untyped [[DynamicValue]]
 * representations, making it fully serializable and suitable for storage in
 * registries, offline application, or dynamic evaluation.
 *
 * Migrations are composed of [[MigrationAction]]s that are applied sequentially
 * to transform data from one schema version to another.
 *
 * {{{
 * val migration = DynamicMigration(Vector(
 *   MigrationAction.Rename(DynamicOptic.root.field("firstName"), "fullName"),
 *   MigrationAction.AddField(DynamicOptic.root.field("age"),
 *     DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(0))))
 * ))
 *
 * migration(oldRecord) // Right(newRecord)
 * }}}
 */
final case class DynamicMigration(actions: Vector[MigrationAction]) {

  /**
   * Apply this migration to a [[DynamicValue]].
   *
   * Actions are applied sequentially in order. If any action fails, the entire
   * migration fails with a [[MigrationError]] indicating the path and cause.
   */
  def apply(value: DynamicValue): Either[MigrationError, DynamicValue] = {
    var current: DynamicValue = value
    val len                   = actions.length
    var idx                   = 0
    while (idx < len) {
      DynamicMigration.applyAction(current, actions(idx)) match {
        case Right(next) => current = next
        case left        => return left
      }
      idx += 1
    }
    Right(current)
  }

  /** Compose two migrations sequentially. */
  def ++(that: DynamicMigration): DynamicMigration =
    new DynamicMigration(this.actions ++ that.actions)

  /** Reverse this migration (structural inverse). */
  def reverse: DynamicMigration =
    new DynamicMigration(actions.reverseIterator.map(_.reverse).toVector)

  /** Check if this migration has no actions (identity). */
  def isEmpty: Boolean = actions.isEmpty
}

object DynamicMigration {

  /** The identity migration — applies no transformations. */
  val empty: DynamicMigration = new DynamicMigration(Vector.empty)

  /** Create a migration from a single action. */
  def single(action: MigrationAction): DynamicMigration =
    new DynamicMigration(Vector(action))

  // ─────────────────────────────────────────────────────────────────────────
  // Action Execution
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Apply a single [[MigrationAction]] to a [[DynamicValue]].
   */
  private[migration] def applyAction(
    value: DynamicValue,
    action: MigrationAction
  ): Either[MigrationError, DynamicValue] =
    action match {
      case MigrationAction.AddField(at, default) =>
        val (parentPath, fieldName) = splitPath(at)
        evalExpr(default, DynamicValue.Null) match {
          case Left(err)          => Left(err)
          case Right(defaultValue) =>
            applyAtRecord(
              value,
              parentPath,
              { record =>
                val fields = record.fields
                val len    = fields.length
                var idx    = 0
                var exists = false
                while (idx < len && !exists) {
                  if (fields(idx)._1 == fieldName) exists = true
                  idx += 1
                }
                if (exists) Left(MigrationError.UnexpectedField(at, fieldName))
                else Right(new DynamicValue.Record(fields.appended((fieldName, defaultValue))))
              }
            )
        }

      case MigrationAction.DropField(at, _) =>
        val (parentPath, fieldName) = splitPath(at)
        applyAtRecord(
          value,
          parentPath,
          { record =>
            val fields = record.fields
            val len    = fields.length
            val cb     = ChunkBuilder.make[(String, DynamicValue)]()
            var found  = false
            var idx    = 0
            while (idx < len) {
              val kv = fields(idx)
              if (kv._1 == fieldName) found = true
              else cb += kv
              idx += 1
            }
            if (!found) Left(MigrationError.MissingField(at, fieldName))
            else Right(new DynamicValue.Record(cb.result()))
          }
        )

      case MigrationAction.Rename(at, to) =>
        val (parentPath, from) = splitPath(at)
        applyAtRecord(
          value,
          parentPath,
          { record =>
            val fields = record.fields
            val len    = fields.length
            val cb     = ChunkBuilder.make[(String, DynamicValue)]()
            var found  = false
            var idx    = 0
            while (idx < len) {
              val kv = fields(idx)
              if (kv._1 == from) {
                found = true
                cb += ((to, kv._2))
              } else cb += kv
              idx += 1
            }
            if (!found) Left(MigrationError.MissingField(at, from))
            else Right(new DynamicValue.Record(cb.result()))
          }
        )

      case MigrationAction.TransformValue(at, transform) =>
        val (parentPath, fieldName) = splitPath(at)
        applyAtRecord(
          value,
          parentPath,
          { record =>
            val fields                                      = record.fields
            val len                                         = fields.length
            val cb                                          = ChunkBuilder.make[(String, DynamicValue)]()
            var found                                       = false
            var error: Either[MigrationError, DynamicValue] = null
            var idx                                         = 0
            while (idx < len && error == null) {
              val kv = fields(idx)
              if (kv._1 == fieldName) {
                found = true
                evalExpr(transform, kv._2) match {
                  case Right(transformed) => cb += ((fieldName, transformed))
                  case left               => error = left
                }
              } else cb += kv
              idx += 1
            }
            if (error != null) error
            else if (!found) Left(MigrationError.MissingField(at, fieldName))
            else Right(new DynamicValue.Record(cb.result()))
          }
        )

      case MigrationAction.Mandate(at, default) =>
        val (parentPath, fieldName) = splitPath(at)
        applyAtRecord(
          value,
          parentPath,
          { record =>
            val fields = record.fields
            val len    = fields.length
            val cb     = ChunkBuilder.make[(String, DynamicValue)]()
            var found  = false
            var idx    = 0
            while (idx < len) {
              val kv = fields(idx)
              if (kv._1 == fieldName) {
                found = true
                val mandated = kv._2 match {
                  case DynamicValue.Null =>
                    evalExpr(default, DynamicValue.Null) match {
                      case Right(v) => v
                      case Left(_)  => DynamicValue.Null
                    }
                  // Handle Option-like wrapping: Variant("Some", value) / Variant("None", ...)
                  case v: DynamicValue.Variant =>
                    v.caseNameValue match {
                      case "None" | "none" =>
                        evalExpr(default, DynamicValue.Null) match {
                          case Right(v) => v
                          case Left(_)  => DynamicValue.Null
                        }
                      case "Some" | "some" => v.value
                      case _               => v
                    }
                  case other => other
                }
                cb += ((fieldName, mandated))
              } else cb += kv
              idx += 1
            }
            if (!found) Left(MigrationError.MissingField(at, fieldName))
            else Right(new DynamicValue.Record(cb.result()))
          }
        )

      case MigrationAction.Optionalize(at) =>
        val (parentPath, fieldName) = splitPath(at)
        applyAtRecord(
          value,
          parentPath,
          { record =>
            val fields = record.fields
            val len    = fields.length
            val cb     = ChunkBuilder.make[(String, DynamicValue)]()
            var found  = false
            var idx    = 0
            while (idx < len) {
              val kv = fields(idx)
              if (kv._1 == fieldName) {
                found = true
                val optionalized = kv._2 match {
                  case DynamicValue.Null => DynamicValue.Null
                  case other             => other
                }
                cb += ((fieldName, optionalized))
              } else cb += kv
              idx += 1
            }
            if (!found) Left(MigrationError.MissingField(at, fieldName))
            else Right(new DynamicValue.Record(cb.result()))
          }
        )

      case MigrationAction.ChangeType(at, converter) =>
        val (parentPath, fieldName) = splitPath(at)
        applyAtRecord(
          value,
          parentPath,
          { record =>
            val fields                                      = record.fields
            val len                                         = fields.length
            val cb                                          = ChunkBuilder.make[(String, DynamicValue)]()
            var found                                       = false
            var error: Either[MigrationError, DynamicValue] = null
            var idx                                         = 0
            while (idx < len && error == null) {
              val kv = fields(idx)
              if (kv._1 == fieldName) {
                found = true
                evalExpr(converter, kv._2) match {
                  case Right(converted) => cb += ((fieldName, converted))
                  case left             => error = left
                }
              } else cb += kv
              idx += 1
            }
            if (error != null) error
            else if (!found) Left(MigrationError.MissingField(at, fieldName))
            else Right(new DynamicValue.Record(cb.result()))
          }
        )

      case MigrationAction.RenameCase(at, from, to) =>
        applyAtVariant(
          value,
          at,
          variant =>
            if (variant.caseNameValue == from)
              Right(new DynamicValue.Variant(to, variant.value))
            else
              Right(variant) // case doesn't match, pass through
        )

      case MigrationAction.TransformCase(at, caseName, caseActions) =>
        applyAtVariant(
          value,
          at,
          variant =>
            if (variant.caseNameValue == caseName) {
              val caseMigration = new DynamicMigration(caseActions)
              caseMigration(variant.value).map(new DynamicValue.Variant(caseName, _))
            } else
              Right(variant) // case doesn't match, pass through
        )

      case MigrationAction.TransformElements(at, transform) =>
        applyAtSequence(
          value,
          at,
          { seq =>
            val elems                                       = seq.elements
            val len                                         = elems.length
            val cb                                          = ChunkBuilder.make[DynamicValue]()
            var error: Either[MigrationError, DynamicValue] = null
            var idx                                         = 0
            while (idx < len && error == null) {
              evalExpr(transform, elems(idx)) match {
                case Right(transformed) => cb += transformed
                case left               => error = left
              }
              idx += 1
            }
            if (error != null) error
            else Right(new DynamicValue.Sequence(cb.result()))
          }
        )

      case MigrationAction.TransformKeys(at, transform) =>
        applyAtMap(
          value,
          at,
          { map =>
            val entries                                     = map.entries
            val len                                         = entries.length
            val cb                                          = ChunkBuilder.make[(DynamicValue, DynamicValue)]()
            var error: Either[MigrationError, DynamicValue] = null
            var idx                                         = 0
            while (idx < len && error == null) {
              val kv = entries(idx)
              evalExpr(transform, kv._1) match {
                case Right(newKey) => cb += ((newKey, kv._2))
                case Left(err)     => error = Left(err)
              }
              idx += 1
            }
            if (error != null) error
            else Right(new DynamicValue.Map(cb.result()))
          }
        )

      case MigrationAction.TransformValues(at, transform) =>
        applyAtMap(
          value,
          at,
          { map =>
            val entries                                     = map.entries
            val len                                         = entries.length
            val cb                                          = ChunkBuilder.make[(DynamicValue, DynamicValue)]()
            var error: Either[MigrationError, DynamicValue] = null
            var idx                                         = 0
            while (idx < len && error == null) {
              val kv = entries(idx)
              evalExpr(transform, kv._2) match {
                case Right(newVal) => cb += ((kv._1, newVal))
                case Left(err)     => error = Left(err)
              }
              idx += 1
            }
            if (error != null) error
            else Right(new DynamicValue.Map(cb.result()))
          }
        )

      case _: MigrationAction.Join =>
        Left(MigrationError.failed("Join actions are not yet supported at runtime"))

      case _: MigrationAction.Split =>
        Left(MigrationError.failed("Split actions are not yet supported at runtime"))
    }

  // ─────────────────────────────────────────────────────────────────────────
  // Expression Evaluation
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Evaluate a [[DynamicSchemaExpr]] against an input value.
   *
   * This is the sole interpreter for the pure data expressions — the
   * expressions themselves contain no logic.
   */
  private[migration] def evalExpr(
    expr: DynamicSchemaExpr,
    input: DynamicValue
  ): Either[MigrationError, DynamicValue] = {
    val root = DynamicOptic.root
    expr match {
      case DynamicSchemaExpr.Literal(v)    => Right(v)
      case DynamicSchemaExpr.Identity      => Right(input)
      case DynamicSchemaExpr.DefaultValue  => Right(input)

      case DynamicSchemaExpr.IntToString =>
        input match {
          case DynamicValue.Primitive(pv: PrimitiveValue.Int) =>
            Right(new DynamicValue.Primitive(new PrimitiveValue.String(pv.value.toString)))
          case _ =>
            Left(MigrationError.ConversionFailed(root, input.valueType.toString, "String", "Expected Int primitive"))
        }

      case DynamicSchemaExpr.StringToInt =>
        input match {
          case DynamicValue.Primitive(pv: PrimitiveValue.String) =>
            try Right(new DynamicValue.Primitive(new PrimitiveValue.Int(pv.value.toInt)))
            catch {
              case _: NumberFormatException =>
                Left(MigrationError.ConversionFailed(root, "String", "Int", "Cannot parse '" + pv.value + "' as Int"))
            }
          case _ =>
            Left(MigrationError.ConversionFailed(root, input.valueType.toString, "Int", "Expected String primitive"))
        }

      case DynamicSchemaExpr.LongToString =>
        input match {
          case DynamicValue.Primitive(pv: PrimitiveValue.Long) =>
            Right(new DynamicValue.Primitive(new PrimitiveValue.String(pv.value.toString)))
          case _ =>
            Left(MigrationError.ConversionFailed(root, input.valueType.toString, "String", "Expected Long primitive"))
        }

      case DynamicSchemaExpr.StringToLong =>
        input match {
          case DynamicValue.Primitive(pv: PrimitiveValue.String) =>
            try Right(new DynamicValue.Primitive(new PrimitiveValue.Long(pv.value.toLong)))
            catch {
              case _: NumberFormatException =>
                Left(
                  MigrationError.ConversionFailed(root, "String", "Long", "Cannot parse '" + pv.value + "' as Long")
                )
            }
          case _ =>
            Left(MigrationError.ConversionFailed(root, input.valueType.toString, "Long", "Expected String primitive"))
        }

      case DynamicSchemaExpr.IntToLong =>
        input match {
          case DynamicValue.Primitive(pv: PrimitiveValue.Int) =>
            Right(new DynamicValue.Primitive(new PrimitiveValue.Long(pv.value.toLong)))
          case _ =>
            Left(MigrationError.ConversionFailed(root, input.valueType.toString, "Long", "Expected Int primitive"))
        }

      case DynamicSchemaExpr.LongToInt =>
        input match {
          case DynamicValue.Primitive(pv: PrimitiveValue.Long) =>
            Right(new DynamicValue.Primitive(new PrimitiveValue.Int(pv.value.toInt)))
          case _ =>
            Left(MigrationError.ConversionFailed(root, input.valueType.toString, "Int", "Expected Long primitive"))
        }

      case DynamicSchemaExpr.BooleanToString =>
        input match {
          case DynamicValue.Primitive(pv: PrimitiveValue.Boolean) =>
            Right(new DynamicValue.Primitive(new PrimitiveValue.String(pv.value.toString)))
          case _ =>
            Left(
              MigrationError.ConversionFailed(root, input.valueType.toString, "String", "Expected Boolean primitive")
            )
        }

      case DynamicSchemaExpr.StringToBoolean =>
        input match {
          case DynamicValue.Primitive(pv: PrimitiveValue.String) =>
            pv.value.toLowerCase match {
              case "true"  => Right(new DynamicValue.Primitive(new PrimitiveValue.Boolean(true)))
              case "false" => Right(new DynamicValue.Primitive(new PrimitiveValue.Boolean(false)))
              case _ =>
                Left(
                  MigrationError.ConversionFailed(
                    root,
                    "String",
                    "Boolean",
                    "Cannot parse '" + pv.value + "' as Boolean"
                  )
                )
            }
          case _ =>
            Left(
              MigrationError.ConversionFailed(root, input.valueType.toString, "Boolean", "Expected String primitive")
            )
        }
    }
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Path Utilities
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Split a record action path into parent path and field name.
   *
   * Record actions encode the target field as the last node in the path.
   */
  private def splitPath(optic: DynamicOptic): (DynamicOptic, String) = {
    val nodes = optic.nodes
    nodes.last match {
      case f: DynamicOptic.Node.Field =>
        val parent =
          if (nodes.length == 1) DynamicOptic.root
          else new DynamicOptic(nodes.dropRight(1))
        (parent, f.name)
      case other =>
        throw new IllegalStateException(
          "Record action path must terminate in a field node, got: " + other.getClass.getSimpleName
        )
    }
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Path Navigation
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Navigate to the target path and apply a transformation to the record found
   * there.
   */
  private def applyAtRecord(
    value: DynamicValue,
    at: DynamicOptic,
    f: DynamicValue.Record => Either[MigrationError, DynamicValue]
  ): Either[MigrationError, DynamicValue] =
    if (at.nodes.isEmpty) {
      // Operating at root
      value match {
        case r: DynamicValue.Record => f(r)
        case _                      =>
          Left(MigrationError.TypeMismatch(at, "Record", value.valueType.toString))
      }
    } else {
      applyAtPath(
        value,
        at,
        {
          case r: DynamicValue.Record => f(r)
          case other                  =>
            Left(MigrationError.TypeMismatch(at, "Record", other.valueType.toString))
        }
      )
    }

  private def applyAtVariant(
    value: DynamicValue,
    at: DynamicOptic,
    f: DynamicValue.Variant => Either[MigrationError, DynamicValue]
  ): Either[MigrationError, DynamicValue] =
    if (at.nodes.isEmpty) {
      value match {
        case v: DynamicValue.Variant => f(v)
        case _                       =>
          Left(MigrationError.TypeMismatch(at, "Variant", value.valueType.toString))
      }
    } else {
      applyAtPath(
        value,
        at,
        {
          case v: DynamicValue.Variant => f(v)
          case other                   =>
            Left(MigrationError.TypeMismatch(at, "Variant", other.valueType.toString))
        }
      )
    }

  private def applyAtSequence(
    value: DynamicValue,
    at: DynamicOptic,
    f: DynamicValue.Sequence => Either[MigrationError, DynamicValue]
  ): Either[MigrationError, DynamicValue] =
    if (at.nodes.isEmpty) {
      value match {
        case s: DynamicValue.Sequence => f(s)
        case _                        =>
          Left(MigrationError.TypeMismatch(at, "Sequence", value.valueType.toString))
      }
    } else {
      applyAtPath(
        value,
        at,
        {
          case s: DynamicValue.Sequence => f(s)
          case other                    =>
            Left(MigrationError.TypeMismatch(at, "Sequence", other.valueType.toString))
        }
      )
    }

  private def applyAtMap(
    value: DynamicValue,
    at: DynamicOptic,
    f: DynamicValue.Map => Either[MigrationError, DynamicValue]
  ): Either[MigrationError, DynamicValue] =
    if (at.nodes.isEmpty) {
      value match {
        case m: DynamicValue.Map => f(m)
        case _                   =>
          Left(MigrationError.TypeMismatch(at, "Map", value.valueType.toString))
      }
    } else {
      applyAtPath(
        value,
        at,
        {
          case m: DynamicValue.Map => f(m)
          case other               =>
            Left(MigrationError.TypeMismatch(at, "Map", other.valueType.toString))
        }
      )
    }

  /**
   * Navigate to a nested path and apply a transformation to the value found
   * there.
   */
  private def applyAtPath(
    value: DynamicValue,
    path: DynamicOptic,
    f: DynamicValue => Either[MigrationError, DynamicValue]
  ): Either[MigrationError, DynamicValue] = {
    val nodes = path.nodes
    val depth = nodes.length

    def go(current: DynamicValue, idx: Int): Either[MigrationError, DynamicValue] =
      if (idx == depth) f(current)
      else {
        val node = nodes(idx)
        node match {
          case field: DynamicOptic.Node.Field =>
            current match {
              case r: DynamicValue.Record =>
                val fields = r.fields
                val len    = fields.length
                var i      = 0
                while (i < len) {
                  val kv = fields(i)
                  if (kv._1 == field.name) {
                    return go(kv._2, idx + 1).map { newVal =>
                      val cb = ChunkBuilder.make[(String, DynamicValue)]()
                      var j  = 0
                      while (j < len) {
                        if (j == i) cb += ((kv._1, newVal))
                        else cb += fields(j)
                        j += 1
                      }
                      new DynamicValue.Record(cb.result())
                    }
                  }
                  i += 1
                }
                Left(MigrationError.MissingField(path, field.name))
              case _ =>
                Left(MigrationError.TypeMismatch(path, "Record", current.valueType.toString))
            }

          case caseNode: DynamicOptic.Node.Case =>
            current match {
              case v: DynamicValue.Variant if v.caseNameValue == caseNode.name =>
                go(v.value, idx + 1).map(new DynamicValue.Variant(v.caseNameValue, _))
              case _: DynamicValue.Variant =>
                Right(current) // different case, pass through
              case _ =>
                Left(MigrationError.TypeMismatch(path, "Variant", current.valueType.toString))
            }

          case _ =>
            Left(MigrationError.failed(path, s"Unsupported path node type: ${node.getClass.getSimpleName}"))
        }
      }

    go(value, 0)
  }
}
