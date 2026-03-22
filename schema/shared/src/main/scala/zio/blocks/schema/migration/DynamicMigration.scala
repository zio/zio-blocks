package zio.blocks.schema.migration

import zio.blocks.chunk.Chunk
import zio.blocks.schema.migration.MigrationAction._
import zio.blocks.schema._

/**
 * A pure, schema-less migration that operates on DynamicValue.
 * 
 * DynamicMigration is the core of the migration system - it contains a list of
 * migration actions that can be applied to any DynamicValue. Because migrations
 * are pure data (no functions, closures, or reflection), they are:
 * 
 *   - Fully serializable (to JSON, MessagePack, etc.)
 *   - Inspectable and debuggable
 *   - Reversible (bidirectional migrations)
 *   - Composable (chain multiple migrations)
 * 
 * ==Example Usage==
 * 
 * {{{
 * val migration = DynamicMigration(
 *   Vector(
 *     MigrationAction.renameField(DynamicOptic.root, "oldName", "newName"),
 *     MigrationAction.addField(DynamicOptic.root, "createdAt", SchemaExpr.Literal(now, Schema[Instant]))
 *   )
 * )
 * 
 * val result = migration.apply(oldValue)
 * }}}
 * 
 * ==Laws==
 * 
 *   - Identity: DynamicMigration.empty.apply(a) == Right(a)
 *   - Associativity: (m1 ++ m2) ++ m3 == m1 ++ (m2 ++ m3)
 *   - Reverse Inverse: m.reverse.reverse == m
 */
final case class DynamicMigration(actions: Vector[MigrationAction]) {

  /**
   * Apply this migration to a DynamicValue.
   * 
   * @param value The value to migrate
   * @return Either an error or the migrated value
   */
  def apply(value: DynamicValue): Either[MigrationError, DynamicValue] = {
    var current: DynamicValue = value
    for (action <- actions) {
      applyAction(current, action) match {
        case Right(updated) => current = updated
        case Left(error) => return Left(error)
      }
    }
    Right(current)
  }

  /**
   * Compose this migration with another. The resulting migration applies
   * this migration first, then the other migration.
   * 
   * This operation is associative: (m1 ++ m2) ++ m3 == m1 ++ (m2 ++ m3)
   */
  def ++(that: DynamicMigration): DynamicMigration =
    DynamicMigration(actions ++ that.actions)

  /**
   * Returns the reverse of this migration.
   * 
   * The reverse can be used to migrate in the opposite direction.
   * Note: Some actions (like TransformValue) have identity reverses.
   */
  def reverse: DynamicMigration =
    DynamicMigration(actions.map(_.reverse).reverse)

  /**
   * Returns true if this migration has no actions.
   */
  def isEmpty: Boolean = actions.isEmpty

  /**
   * Returns a new migration with an additional action.
   */
  def :+(action: MigrationAction): DynamicMigration =
    DynamicMigration(actions :+ action)

  /**
   * Returns a new migration with an action prepended.
   */
  def +:(action: MigrationAction): DynamicMigration =
    DynamicMigration(action +: actions)

  /**
   * Returns a new migration with all actions from the given sequence prepended.
   */
  def ++:(prefix: Seq[MigrationAction]): DynamicMigration =
    DynamicMigration(prefix ++ actions)

  /**
   * Returns the number of actions in this migration.
   */
  def size: Int = actions.size

  override def toString: String = {
    if (actions.isEmpty) "DynamicMigration.empty"
    else {
      val sb = new java.lang.StringBuilder("DynamicMigration(\n")
      actions.foreach { action =>
        sb.append("  ").append(action).append(",\n")
      }
      sb.append(")")
      sb.toString
    }
  }

  // ============================================================================
  // Private Implementation
  // ============================================================================

  private def applyAction(
    value: DynamicValue,
    action: MigrationAction
  ): Either[MigrationError, DynamicValue] = action match {
    
    case AddField(at, fieldName, default) =>
      applyAtPath(value, at.nodes, 0) { (current, remaining) =>
        if (remaining.nonEmpty) Left(MigrationError.invalidOperation(
          s"AddField: cannot have remaining path components", at
        ))
        else current match {
          case r: DynamicValue.Record =>
            if (r.fields.exists(_._1 == fieldName)) {
              Left(MigrationError.invalidOperation(
                s"AddField: field '$fieldName' already exists", at
              ))
            } else {
              default.eval(()) match {
                case Right(Seq(dynValue)) =>
                  Right(DynamicValue.Record(r.fields :+ (fieldName, dynValue)))
                case _ =>
                  Left(MigrationError.transformFailed(
                    s"AddField: could not evaluate default value", at
                  ))
              }
            }
          case _ =>
            Left(MigrationError.typeMismatch("Record", current.valueType.toString, at))
        }
      }

    case DropField(at, fieldName, _) =>
      applyAtPath(value, at.nodes, 0) { (current, remaining) =>
        if (remaining.nonEmpty) Left(MigrationError.invalidOperation(
          s"DropField: cannot have remaining path components", at
        ))
        else current match {
          case r: DynamicValue.Record =>
            val idx = r.fields.indexWhere(_._1 == fieldName)
            if (idx < 0) {
              Left(MigrationError.fieldNotFound(fieldName, at))
            } else {
              Right(DynamicValue.Record(r.fields.patch(idx, Chunk.empty, 1)))
            }
          case _ =>
            Left(MigrationError.typeMismatch("Record", current.valueType.toString, at))
        }
      }

    case RenameField(at, from, to) =>
      applyAtPath(value, at.nodes, 0) { (current, remaining) =>
        if (remaining.nonEmpty) Left(MigrationError.invalidOperation(
          s"RenameField: cannot have remaining path components", at
        ))
        else current match {
          case r: DynamicValue.Record =>
            val idx = r.fields.indexWhere(_._1 == from)
            if (idx < 0) {
              Left(MigrationError.fieldNotFound(from, at))
            } else if (r.fields.exists(_._1 == to)) {
              Left(MigrationError.invalidOperation(
                s"RenameField: field '$to' already exists", at
              ))
            } else {
              val (name, v) = r.fields(idx)
              Right(DynamicValue.Record(r.fields.updated(idx, (to, v))))
            }
          case _ =>
            Left(MigrationError.typeMismatch("Record", current.valueType.toString, at))
        }
      }

    case TransformValue(at, fieldName, transform) =>
      applyAtPath(value, at.nodes, 0) { (current, remaining) =>
        if (remaining.nonEmpty) Left(MigrationError.invalidOperation(
          s"TransformValue: cannot have remaining path components", at
        ))
        else current match {
          case r: DynamicValue.Record =>
            val idx = r.fields.indexWhere(_._1 == fieldName)
            if (idx < 0) {
              Left(MigrationError.fieldNotFound(fieldName, at))
            } else {
              val (_, fieldValue) = r.fields(idx)
              // Note: SchemaExpr operates on typed values, so we need to work with DynamicValue
              // For now, we return the value unchanged (transform would need more infrastructure)
              // In a full implementation, this would apply the transform
              Right(value) // Placeholder - full implementation would transform
            }
          case _ =>
            Left(MigrationError.typeMismatch("Record", current.valueType.toString, at))
        }
      }

    case MandateField(at, fieldName, default) =>
      applyAtPath(value, at.nodes, 0) { (current, remaining) =>
        if (remaining.nonEmpty) Left(MigrationError.invalidOperation(
          s"MandateField: cannot have remaining path components", at
        ))
        else current match {
          case r: DynamicValue.Record =>
            val idx = r.fields.indexWhere(_._1 == fieldName)
            if (idx >= 0) {
              // Field exists, check if it's null and replace with default
              val (_, fieldValue) = r.fields(idx)
              fieldValue match {
                case DynamicValue.Null =>
                  default.eval(()) match {
                    case Right(Seq(dynValue)) =>
                      Right(DynamicValue.Record(r.fields.updated(idx, (fieldName, dynValue))))
                    case _ =>
                      Left(MigrationError.transformFailed(
                        s"MandateField: could not evaluate default value", at
                      ))
                  }
                case _ => Right(value) // Field has a value, no change needed
              }
            } else {
              // Field doesn't exist, add it with default
              default.eval(()) match {
                case Right(Seq(dynValue)) =>
                  Right(DynamicValue.Record(r.fields :+ (fieldName, dynValue)))
                case _ =>
                  Left(MigrationError.transformFailed(
                    s"MandateField: could not evaluate default value", at
                  ))
              }
            }
          case _ =>
            Left(MigrationError.typeMismatch("Record", current.valueType.toString, at))
        }
      }

    case OptionalizeField(at, fieldName) =>
      applyAtPath(value, at.nodes, 0) { (current, remaining) =>
        if (remaining.nonEmpty) Left(MigrationError.invalidOperation(
          s"OptionalizeField: cannot have remaining path components", at
        ))
        else current match {
          case r: DynamicValue.Record =>
            // Optionalize just means the field can be null - no structural change
            // But we could optionally set null if field is missing
            Right(value)
          case _ =>
            Left(MigrationError.typeMismatch("Record", current.valueType.toString, at))
        }
      }

    case ChangeFieldType(at, fieldName, converter) =>
      applyAtPath(value, at.nodes, 0) { (current, remaining) =>
        if (remaining.nonEmpty) Left(MigrationError.invalidOperation(
          s"ChangeFieldType: cannot have remaining path components", at
        ))
        else current match {
          case r: DynamicValue.Record =>
            val idx = r.fields.indexWhere(_._1 == fieldName)
            if (idx < 0) {
              Left(MigrationError.fieldNotFound(fieldName, at))
            } else {
              // Similar to TransformValue - would need full implementation
              Right(value)
            }
          case _ =>
            Left(MigrationError.typeMismatch("Record", current.valueType.toString, at))
        }
      }

    case JoinFields(at, targetField, sourcePaths, combiner) =>
      applyAtPath(value, at.nodes, 0) { (current, remaining) =>
        if (remaining.nonEmpty) Left(MigrationError.invalidOperation(
          s"JoinFields: cannot have remaining path components", at
        ))
        else current match {
          case r: DynamicValue.Record =>
            // Would need to evaluate sourcePaths and apply combiner
            // Placeholder implementation
            Right(value)
          case _ =>
            Left(MigrationError.typeMismatch("Record", current.valueType.toString, at))
        }
      }

    case SplitField(at, sourceField, targetPaths, splitter) =>
      applyAtPath(value, at.nodes, 0) { (current, remaining) =>
        if (remaining.nonEmpty) Left(MigrationError.invalidOperation(
          s"SplitField: cannot have remaining path components", at
        ))
        else current match {
          case r: DynamicValue.Record =>
            // Would need to evaluate splitter and create target fields
            // Placeholder implementation
            Right(value)
          case _ =>
            Left(MigrationError.typeMismatch("Record", current.valueType.toString, at))
        }
      }

    case RenameCase(at, from, to) =>
      applyAtPath(value, at.nodes, 0) { (current, remaining) =>
        if (remaining.nonEmpty) Left(MigrationError.invalidOperation(
          s"RenameCase: cannot have remaining path components", at
        ))
        else current match {
          case v: DynamicValue.Variant =>
            if (v.caseNameValue == from) {
              Right(DynamicValue.Variant(to, v.value))
            } else {
              Right(value) // Case doesn't match, no change
            }
          case _ =>
            Left(MigrationError.typeMismatch("Variant", current.valueType.toString, at))
        }
      }

    case TransformCase(at, caseName, caseActions) =>
      applyAtPath(value, at.nodes, 0) { (current, remaining) =>
        if (remaining.nonEmpty) Left(MigrationError.invalidOperation(
          s"TransformCase: cannot have remaining path components", at
        ))
        else current match {
          case v: DynamicValue.Variant =>
            if (v.caseNameValue == caseName) {
              val caseMigration = DynamicMigration(caseActions)
              caseMigration.apply(v.value) match {
                case Right(transformedValue) =>
                  Right(DynamicValue.Variant(caseName, transformedValue))
                case Left(error) =>
                  Left(error)
              }
            } else {
              Right(value) // Case doesn't match, no change
            }
          case _ =>
            Left(MigrationError.typeMismatch("Variant", current.valueType.toString, at))
        }
      }

    case TransformElements(at, transform) =>
      applyAtPath(value, at.nodes, 0) { (current, remaining) =>
        if (remaining.nonEmpty) Left(MigrationError.invalidOperation(
          s"TransformElements: cannot have remaining path components", at
        ))
        else current match {
          case s: DynamicValue.Sequence =>
            // Would need to apply transform to each element
            // Placeholder implementation
            Right(value)
          case _ =>
            Left(MigrationError.typeMismatch("Sequence", current.valueType.toString, at))
        }
      }

    case TransformKeys(at, transform) =>
      applyAtPath(value, at.nodes, 0) { (current, remaining) =>
        if (remaining.nonEmpty) Left(MigrationError.invalidOperation(
          s"TransformKeys: cannot have remaining path components", at
        ))
        else current match {
          case m: DynamicValue.Map =>
            // Would need to apply transform to each key
            // Placeholder implementation
            Right(value)
          case _ =>
            Left(MigrationError.typeMismatch("Map", current.valueType.toString, at))
        }
      }

    case TransformValues(at, transform) =>
      applyAtPath(value, at.nodes, 0) { (current, remaining) =>
        if (remaining.nonEmpty) Left(MigrationError.invalidOperation(
          s"TransformValues: cannot have remaining path components", at
        ))
        else current match {
          case m: DynamicValue.Map =>
            // Would need to apply transform to each value
            // Placeholder implementation
            Right(value)
          case _ =>
            Left(MigrationError.typeMismatch("Map", current.valueType.toString, at))
        }
      }
  }

  private def applyAtPath[A](
    value: DynamicValue,
    nodes: IndexedSeq[DynamicOptic.Node],
    idx: Int
  )(f: (DynamicValue, IndexedSeq[DynamicOptic.Node]) => Either[MigrationError, A]
  ): Either[MigrationError, A] = {
    if (idx >= nodes.length) {
      f(value, nodes.slice(idx, nodes.length))
    } else {
      nodes(idx) match {
        case DynamicOptic.Node.Field(name) =>
          value match {
            case r: DynamicValue.Record =>
              r.fields.find(_._1 == name) match {
                case Some((_, fieldValue)) =>
                  applyAtPath(fieldValue, nodes, idx + 1)(f)
                case None =>
                  Left(MigrationError.fieldNotFound(name, new DynamicOptic(nodes.slice(0, idx + 1))))
              }
            case _ =>
              Left(MigrationError.typeMismatch("Record", value.valueType.toString, new DynamicOptic(nodes.slice(0, idx))))
          }
        case DynamicOptic.Node.Case(caseName) =>
          value match {
            case v: DynamicValue.Variant if v.caseNameValue == caseName =>
              applyAtPath(v.value, nodes, idx + 1)(f)
            case v: DynamicValue.Variant =>
              Left(MigrationError.caseNotFound(caseName, new DynamicOptic(nodes.slice(0, idx + 1))))
            case _ =>
              Left(MigrationError.typeMismatch("Variant", value.valueType.toString, new DynamicOptic(nodes.slice(0, idx))))
          }
        case DynamicOptic.Node.AtIndex(i) =>
          value match {
            case s: DynamicValue.Sequence =>
              if (i >= 0 && i < s.elements.length) {
                applyAtPath(s.elements(i), nodes, idx + 1)(f)
              } else {
                Left(MigrationError.invalidOperation(
                  s"Index $i out of bounds (size: ${s.elements.length})", 
                  new DynamicOptic(nodes.slice(0, idx + 1))
                ))
              }
            case _ =>
              Left(MigrationError.typeMismatch("Sequence", value.valueType.toString, new DynamicOptic(nodes.slice(0, idx))))
          }
        case DynamicOptic.Node.Elements =>
          value match {
            case s: DynamicValue.Sequence =>
              // Apply to all elements - for now, we don't support this in applyAtPath
              // This would need special handling
              Left(MigrationError.invalidOperation(
                "Elements traversal not supported in this context",
                new DynamicOptic(nodes.slice(0, idx + 1))
              ))
            case _ =>
              Left(MigrationError.typeMismatch("Sequence", value.valueType.toString, new DynamicOptic(nodes.slice(0, idx))))
          }
        case _ =>
          Left(MigrationError.invalidOperation(
            s"Unsupported optic node at index $idx",
            new DynamicOptic(nodes.slice(0, idx + 1))
          ))
      }
    }
  }
}

object DynamicMigration {

  /**
   * The empty migration - identity element for composition.
   */
  val empty: DynamicMigration = DynamicMigration(Vector.empty)

  /**
   * Create a migration with a single action.
   */
  def single(action: MigrationAction): DynamicMigration =
    DynamicMigration(Vector.single(action))

  /**
   * Create a migration from a sequence of actions.
   */
  def apply(actions: MigrationAction*): DynamicMigration =
    DynamicMigration(actions.toVector)
}
