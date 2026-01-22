package zio.blocks.schema.migration

import zio.blocks.schema.{DynamicOptic, DynamicValue, SchemaExpr}

/**
 * Represents an atomic migration operation. All actions operate at a specific
 * path and are reversible.
 */
sealed trait MigrationAction {

  /**
   * The path where this action operates.
   */
  def at: DynamicOptic

  /**
   * Execute this action on a DynamicValue.
   */
  def execute(value: DynamicValue): Either[MigrationError, DynamicValue]

  /**
   * Returns the structural inverse of this action.
   */
  def reverse: MigrationAction
}

object MigrationAction {

  // Private helpers for navigating and updating nested DynamicValue structures.
  private object NavigationHelpers {

    /**
     * Navigate to a nested field and return the parent record, field index, and
     * field value. Example: for path `.address.street`, this navigates to the
     * `address` record and returns (addressRecord, streetIndex, streetValue)
     */
    def navigateToField(
      value: DynamicValue,
      path: DynamicOptic
    ): Either[MigrationError, (DynamicValue.Record, Int, String, DynamicValue)] = {
      if (path.nodes.isEmpty) {
        return Left(MigrationError.InvalidStructure(path, "non-empty path", "empty path"))
      }

      // Extract all field nodes
      val fieldNodes = path.nodes.collect { case node: DynamicOptic.Node.Field => node.name }

      if (fieldNodes.length != path.nodes.length) {
        return Left(
          MigrationError.InvalidStructure(path, "only Field nodes", s"path contains non-Field nodes")
        )
      }

      // Navigate through all intermediate fields
      def navigateIntermediate(currentValue: DynamicValue, depth: Int): Either[MigrationError, DynamicValue] =
        if (depth >= fieldNodes.length - 1) {
          // Reached the parent of the target field
          Right(currentValue)
        } else {
          val fieldName = fieldNodes(depth)
          currentValue match {
            case record: DynamicValue.Record =>
              val fieldIndex = record.fields.indexWhere(_._1 == fieldName)
              if (fieldIndex < 0) {
                Left(MigrationError.IntermediateFieldNotFound(path, fieldName, depth))
              } else {
                val nextValue = record.fields(fieldIndex)._2
                navigateIntermediate(nextValue, depth + 1)
              }
            case _ =>
              Left(
                MigrationError.IntermediateFieldNotRecord(path, fieldName, depth, currentValue.getClass.getSimpleName)
              )
          }
        }

      // Navigate to parent record
      navigateIntermediate(value, 0) match {
        case Right(parentValue) =>
          parentValue match {
            case parentRecord: DynamicValue.Record =>
              val targetFieldName = fieldNodes.last
              val targetIndex     = parentRecord.fields.indexWhere(_._1 == targetFieldName)

              if (targetIndex < 0) {
                Left(MigrationError.FieldNotFound(path, targetFieldName))
              } else {
                val targetValue = parentRecord.fields(targetIndex)._2
                Right((parentRecord, targetIndex, targetFieldName, targetValue))
              }

            case _ =>
              Left(
                MigrationError.InvalidStructure(
                  path,
                  "Record",
                  s"${parentValue.getClass.getSimpleName} (at depth ${fieldNodes.length - 1})"
                )
              )
          }
        case Left(err) => Left(err)
      }
    }

    /**
     * Update a nested field value, rebuilding the entire structure immutably.
     * Example: for path `.address.street`, this updates the `street` field
     * inside the `address` record and rebuilds the entire structure.
     */
    def updateNestedField(
      value: DynamicValue,
      path: DynamicOptic,
      newValue: DynamicValue
    ): Either[MigrationError, DynamicValue] = {
      if (path.nodes.isEmpty) {
        return Left(MigrationError.InvalidStructure(path, "non-empty path", "empty path"))
      }

      val fieldNodes = path.nodes.collect { case node: DynamicOptic.Node.Field => node.name }

      if (fieldNodes.length != path.nodes.length) {
        return Left(MigrationError.InvalidStructure(path, "only Field nodes", "path contains non-Field nodes"))
      }

      // Recursive helper to update at a specific depth
      def updateAtDepth(currentValue: DynamicValue, depth: Int): Either[MigrationError, DynamicValue] =
        if (depth == fieldNodes.length - 1) {
          // We're at the target depth - update the field
          currentValue match {
            case record: DynamicValue.Record =>
              val fieldName  = fieldNodes(depth)
              val fieldIndex = record.fields.indexWhere(_._1 == fieldName)
              if (fieldIndex < 0) {
                Left(MigrationError.FieldNotFound(path, fieldName))
              } else {
                val updatedFields = record.fields.updated(fieldIndex, (fieldName, newValue))
                Right(DynamicValue.Record(updatedFields))
              }
            case _ =>
              Left(MigrationError.InvalidStructure(path, "Record", currentValue.getClass.getSimpleName))
          }
        } else {
          // Navigate deeper and rebuild on the way back up
          currentValue match {
            case record: DynamicValue.Record =>
              val fieldName  = fieldNodes(depth)
              val fieldIndex = record.fields.indexWhere(_._1 == fieldName)
              if (fieldIndex < 0) {
                Left(MigrationError.IntermediateFieldNotFound(path, fieldName, depth))
              } else {
                val childValue = record.fields(fieldIndex)._2
                // Recursively update the child
                updateAtDepth(childValue, depth + 1) match {
                  case Right(updatedChild) =>
                    val updatedFields = record.fields.updated(fieldIndex, (fieldName, updatedChild))
                    Right(DynamicValue.Record(updatedFields))
                  case Left(err) => Left(err)
                }
              }
            case _ =>
              Left(
                MigrationError.IntermediateFieldNotRecord(
                  path,
                  fieldNodes(depth),
                  depth,
                  currentValue.getClass.getSimpleName
                )
              )
          }
        }

      updateAtDepth(value, 0)
    }

    /**
     * Add a nested field, creating intermediate Records.
     */
    def addNestedField(
      value: DynamicValue,
      path: DynamicOptic,
      fieldValue: DynamicValue
    ): Either[MigrationError, DynamicValue] = {
      if (path.nodes.isEmpty) {
        return Left(MigrationError.InvalidStructure(path, "non-empty path", "empty path"))
      }

      val fieldNodes = path.nodes.collect { case node: DynamicOptic.Node.Field => node.name }

      if (fieldNodes.length != path.nodes.length) {
        return Left(MigrationError.InvalidStructure(path, "only Field nodes", "path contains non-Field nodes"))
      }

      // Recursive helper to add at a specific depth
      def addAtDepth(currentValue: DynamicValue, depth: Int): Either[MigrationError, DynamicValue] =
        if (depth == fieldNodes.length - 1) {
          // We're at the target depth - add the field
          currentValue match {
            case record: DynamicValue.Record =>
              val fieldName = fieldNodes(depth)
              // Check if field already exists
              if (record.fields.exists(_._1 == fieldName)) {
                Left(MigrationError.FieldAlreadyExists(path, fieldName))
              } else {
                val updatedFields = record.fields :+ (fieldName -> fieldValue)
                Right(DynamicValue.Record(updatedFields))
              }
            case _ =>
              Left(MigrationError.InvalidStructure(path, "Record", currentValue.getClass.getSimpleName))
          }
        } else {
          // Navigate deeper and rebuild on the way back up
          currentValue match {
            case record: DynamicValue.Record =>
              val fieldName  = fieldNodes(depth)
              val fieldIndex = record.fields.indexWhere(_._1 == fieldName)
              if (fieldIndex < 0) {
                Left(MigrationError.IntermediateFieldNotFound(path, fieldName, depth))
              } else {
                val childValue = record.fields(fieldIndex)._2
                // Recursively add to the child
                addAtDepth(childValue, depth + 1) match {
                  case Right(updatedChild) =>
                    val updatedFields = record.fields.updated(fieldIndex, (fieldName, updatedChild))
                    Right(DynamicValue.Record(updatedFields))
                  case Left(err) => Left(err)
                }
              }
            case _ =>
              Left(
                MigrationError.IntermediateFieldNotRecord(
                  path,
                  fieldNodes(depth),
                  depth,
                  currentValue.getClass.getSimpleName
                )
              )
          }
        }

      addAtDepth(value, 0)
    }

    /**
     * Remove a nested field, rebuilding the entire structure immutably.
     */
    def removeNestedField(
      value: DynamicValue,
      path: DynamicOptic
    ): Either[MigrationError, DynamicValue] = {
      if (path.nodes.isEmpty) {
        return Left(MigrationError.InvalidStructure(path, "non-empty path", "empty path"))
      }

      val fieldNodes = path.nodes.collect { case node: DynamicOptic.Node.Field => node.name }

      if (fieldNodes.length != path.nodes.length) {
        return Left(MigrationError.InvalidStructure(path, "only Field nodes", "path contains non-Field nodes"))
      }

      // Recursive helper to remove at a specific depth
      def removeAtDepth(currentValue: DynamicValue, depth: Int): Either[MigrationError, DynamicValue] =
        if (depth == fieldNodes.length - 1) {
          // We're at the target depth - remove the field
          currentValue match {
            case record: DynamicValue.Record =>
              val fieldName  = fieldNodes(depth)
              val fieldIndex = record.fields.indexWhere(_._1 == fieldName)
              if (fieldIndex < 0) {
                Left(MigrationError.FieldNotFound(path, fieldName))
              } else {
                val updatedFields = record.fields.patch(fieldIndex, Nil, 1)
                Right(DynamicValue.Record(updatedFields))
              }
            case _ =>
              Left(MigrationError.InvalidStructure(path, "Record", currentValue.getClass.getSimpleName))
          }
        } else {
          // Navigate deeper and rebuild on the way back up
          currentValue match {
            case record: DynamicValue.Record =>
              val fieldName  = fieldNodes(depth)
              val fieldIndex = record.fields.indexWhere(_._1 == fieldName)
              if (fieldIndex < 0) {
                Left(MigrationError.IntermediateFieldNotFound(path, fieldName, depth))
              } else {
                val childValue = record.fields(fieldIndex)._2
                // Recursively remove from the child
                removeAtDepth(childValue, depth + 1) match {
                  case Right(updatedChild) =>
                    val updatedFields = record.fields.updated(fieldIndex, (fieldName, updatedChild))
                    Right(DynamicValue.Record(updatedFields))
                  case Left(err) => Left(err)
                }
              }
            case _ =>
              Left(
                MigrationError.IntermediateFieldNotRecord(
                  path,
                  fieldNodes(depth),
                  depth,
                  currentValue.getClass.getSimpleName
                )
              )
          }
        }

      removeAtDepth(value, 0)
    }
  }

  /**
   * Adds a field to a record with a default value. Reverse: DropField
   */
  final case class AddField(
    at: DynamicOptic,
    default: SchemaExpr[DynamicValue, ?]
  ) extends MigrationAction {
    def execute(value: DynamicValue): Either[MigrationError, DynamicValue] = {
      if (at.nodes.isEmpty) {
        return Left(MigrationError.InvalidStructure(at, "non-empty path", "empty path"))
      }

      // Evaluate default expression to get DynamicValue
      val defaultValueResult = default.evalDynamic(value) match {
        case Right(seq) =>
          if (seq.isEmpty) {
            return Left(MigrationError.EvaluationError(at, "Default expression returned empty sequence"))
          }
          Right(seq.head)
        case Left(err) =>
          Left(MigrationError.EvaluationError(at, s"Failed to evaluate default: ${err.getMessage}"))
      }

      val defaultValue = defaultValueResult match {
        case Right(v)  => v
        case Left(err) => return Left(err)
      }

      if (at.nodes.length == 1) {
        // Optimized path: top-level field
        value match {
          case record: DynamicValue.Record =>
            at.nodes.last match {
              case DynamicOptic.Node.Field(fieldName) =>
                // Check if field already exists
                if (record.fields.exists(_._1 == fieldName)) {
                  Left(MigrationError.FieldAlreadyExists(at, fieldName))
                } else {
                  Right(DynamicValue.Record(record.fields :+ (fieldName -> defaultValue)))
                }
              case _ =>
                Left(MigrationError.InvalidStructure(at, "Field node", s"${at.nodes.last}"))
            }
          case _ =>
            Left(MigrationError.InvalidStructure(at, "Record", value.getClass.getSimpleName))
        }
      } else {
        // Nested field - use navigation helpers
        NavigationHelpers.addNestedField(value, at, defaultValue)
      }
    }

    def reverse: MigrationAction = DropField(at, default)
  }

  /**
   * Removes a field from a record. Stores a default value for reverse
   * migration. Reverse: AddField
   */
  final case class DropField(
    at: DynamicOptic,
    defaultForReverse: SchemaExpr[DynamicValue, ?]
  ) extends MigrationAction {
    def execute(value: DynamicValue): Either[MigrationError, DynamicValue] = {
      if (at.nodes.isEmpty) {
        return Left(MigrationError.InvalidStructure(at, "non-empty path", "empty path"))
      }

      if (at.nodes.length == 1) {
        // Optimized path: top-level field
        value match {
          case record: DynamicValue.Record =>
            at.nodes.last match {
              case DynamicOptic.Node.Field(fieldName) =>
                // Check if field exists
                val fieldIndex = record.fields.indexWhere(_._1 == fieldName)
                if (fieldIndex < 0) {
                  Left(MigrationError.FieldNotFound(at, fieldName))
                } else {
                  val newFields = record.fields.patch(fieldIndex, Nil, 1)
                  Right(DynamicValue.Record(newFields))
                }
              case _ =>
                Left(MigrationError.InvalidStructure(at, "Field node", s"${at.nodes.last}"))
            }
          case _ =>
            Left(MigrationError.InvalidStructure(at, "Record", value.getClass.getSimpleName))
        }
      } else {
        // Nested field - use navigation helpers
        NavigationHelpers.removeNestedField(value, at)
      }
    }

    def reverse: MigrationAction = AddField(at, defaultForReverse)
  }

  /**
   * Renames a field in a record. Reverse: Rename with flipped to/from
   */
  final case class Rename(
    at: DynamicOptic,
    to: String
  ) extends MigrationAction {
    def execute(value: DynamicValue): Either[MigrationError, DynamicValue] = {
      if (at.nodes.isEmpty) {
        return Left(MigrationError.InvalidStructure(at, "non-empty path", "empty path"))
      }

      if (at.nodes.length == 1) {
        // Optimized path: top-level field
        value match {
          case record: DynamicValue.Record =>
            at.nodes.last match {
              case DynamicOptic.Node.Field(fromName) =>
                // Check if from field exists
                val fromIndex = record.fields.indexWhere(_._1 == fromName)
                if (fromIndex < 0) {
                  return Left(MigrationError.FieldNotFound(at, fromName))
                }

                // Check if to field already exists
                if (record.fields.exists(_._1 == to)) {
                  return Left(MigrationError.FieldAlreadyExists(at, to))
                }

                // Rename field
                val (_, fieldValue) = record.fields(fromIndex)
                val newFields       = record.fields.updated(fromIndex, (to, fieldValue))
                Right(DynamicValue.Record(newFields))

              case _ =>
                Left(MigrationError.InvalidStructure(at, "Field node", s"${at.nodes.last}"))
            }
          case _ =>
            Left(MigrationError.InvalidStructure(at, "Record", value.getClass.getSimpleName))
        }
      } else {
        // Nested field - navigate and rename
        NavigationHelpers.navigateToField(value, at) match {
          case Right((parentRecord, fieldIndex, _, fieldValue)) =>
            // Check if 'to' field already exists in parent
            if (parentRecord.fields.exists(_._1 == to)) {
              return Left(MigrationError.FieldAlreadyExists(at, to))
            }

            // Rename the field in parent record
            val updatedParentFields = parentRecord.fields.updated(fieldIndex, (to, fieldValue))
            val updatedParent       = DynamicValue.Record(updatedParentFields)

            // Rebuild the structure with updated parent
            // Create a path to the parent (all nodes except last)
            val parentPath = new DynamicOptic(at.nodes.dropRight(1))
            if (parentPath.nodes.isEmpty) {
              // Parent is root, return updated parent directly
              Right(updatedParent)
            } else {
              // Update the parent in the original structure
              NavigationHelpers.updateNestedField(value, parentPath, updatedParent)
            }

          case Left(err) => Left(err)
        }
      }
    }

    def reverse: MigrationAction =
      // Extract the from field name and create reverse rename
      at.nodes.last match {
        case DynamicOptic.Node.Field(fromName) =>
          // Create a new optic pointing to the "to" field
          val reversePath = if (at.nodes.length == 1) {
            DynamicOptic.root.field(to)
          } else {
            val parentNodes = at.nodes.dropRight(1)
            new DynamicOptic(parentNodes :+ DynamicOptic.Node.Field(to))
          }
          Rename(reversePath, fromName)
        case _ =>
          // This shouldn't happen if validation is done properly
          this
      }
  }

  /**
   * Transforms a field value using a SchemaExpr. Can use any existing
   * SchemaExpr (Arithmetic, StringConcat, etc.) Reverse: TransformValue with
   * reverse SchemaExpr (best-effort)
   */
  final case class TransformValue(
    at: DynamicOptic,
    transform: SchemaExpr[DynamicValue, ?]
  ) extends MigrationAction {
    def execute(value: DynamicValue): Either[MigrationError, DynamicValue] = {
      if (at.nodes.isEmpty) {
        return Left(MigrationError.InvalidStructure(at, "non-empty path", "empty path"))
      }

      if (at.nodes.length == 1) {
        // Optimized path: top-level field
        value match {
          case record: DynamicValue.Record =>
            at.nodes.last match {
              case DynamicOptic.Node.Field(fieldName) =>
                // Check if field exists
                val fieldIndex = record.fields.indexWhere(_._1 == fieldName)
                if (fieldIndex < 0) {
                  return Left(MigrationError.FieldNotFound(at, fieldName))
                }

                // Get current field value
                val (_, fieldValue) = record.fields(fieldIndex)

                // Apply transformation to field value
                transform.evalDynamic(fieldValue) match {
                  case Right(seq) =>
                    if (seq.isEmpty) {
                      return Left(MigrationError.EvaluationError(at, "Transform expression returned empty sequence"))
                    }
                    val transformedValue = seq.head
                    // Update field with transformed value
                    val newFields = record.fields.updated(fieldIndex, (fieldName, transformedValue))
                    Right(DynamicValue.Record(newFields))
                  case Left(err) =>
                    Left(MigrationError.EvaluationError(at, s"Failed to evaluate transform: ${err.getMessage}"))
                }

              case _ =>
                Left(MigrationError.InvalidStructure(at, "Field node", s"${at.nodes.last}"))
            }
          case _ =>
            Left(MigrationError.InvalidStructure(at, "Record", value.getClass.getSimpleName))
        }
      } else {
        // Nested field - navigate, transform, update
        NavigationHelpers.navigateToField(value, at) match {
          case Right((_, _, _, fieldValue)) =>
            // Apply transformation to field value
            transform.evalDynamic(fieldValue) match {
              case Right(seq) =>
                if (seq.isEmpty) {
                  Left(MigrationError.EvaluationError(at, "Transform expression returned empty sequence"))
                } else {
                  val transformedValue = seq.head
                  // Update the nested field with transformed value
                  NavigationHelpers.updateNestedField(value, at, transformedValue)
                }
              case Left(err) =>
                Left(MigrationError.EvaluationError(at, s"Failed to evaluate transform: ${err.getMessage}"))
            }
          case Left(err) => Left(err)
        }
      }
    }

    def reverse: MigrationAction = {
      // Pattern match on transform to find reversible operations
      val inverseTransform = transform match {
        // ✅ LOSSLESS: Arithmetic Add/Subtract
        case SchemaExpr.Arithmetic(left, right, SchemaExpr.ArithmeticOperator.Add, num) =>
          SchemaExpr.Arithmetic(left, right, SchemaExpr.ArithmeticOperator.Subtract, num)

        case SchemaExpr.Arithmetic(left, right, SchemaExpr.ArithmeticOperator.Subtract, num) =>
          SchemaExpr.Arithmetic(left, right, SchemaExpr.ArithmeticOperator.Add, num)

        // ✅ LOSSLESS: Arithmetic Multiply/Divide
        case SchemaExpr.Arithmetic(left, right, SchemaExpr.ArithmeticOperator.Multiply, num) =>
          SchemaExpr.Arithmetic(left, right, SchemaExpr.ArithmeticOperator.Divide, num)

        case SchemaExpr.Arithmetic(left, right, SchemaExpr.ArithmeticOperator.Divide, num) =>
          SchemaExpr.Arithmetic(left, right, SchemaExpr.ArithmeticOperator.Multiply, num)

        // ✅ LOSSLESS: Boolean Not (self-inverse)
        case SchemaExpr.Not(expr) =>
          SchemaExpr.Not(expr)

        // ⚠️ LOSSY: String case conversions (loses original casing)
        case SchemaExpr.StringUppercase(expr) =>
          SchemaExpr.StringLowercase(expr)

        case SchemaExpr.StringLowercase(expr) =>
          SchemaExpr.StringUppercase(expr)

        // ❌ IRREVERSIBLE: Default to identity for operations that cannot be reversed
        // (StringLength, Relational, Logical, StringRegexMatch, etc.)
        case _ => transform
      }

      TransformValue(at, inverseTransform)
    }
  }

  /**
   * Changes the type of a field using a PrimitiveConverter. Reverse: ChangeType
   * with reverse converter
   */
  final case class ChangeType(
    at: DynamicOptic,
    converter: zio.blocks.schema.PrimitiveConverter
  ) extends MigrationAction {
    def execute(value: DynamicValue): Either[MigrationError, DynamicValue] = {
      if (at.nodes.isEmpty) {
        return Left(MigrationError.InvalidStructure(at, "non-empty path", "empty path"))
      }

      if (at.nodes.length == 1) {
        // Optimized path: top-level field
        value match {
          case record: DynamicValue.Record =>
            at.nodes.last match {
              case DynamicOptic.Node.Field(fieldName) =>
                // Check if field exists
                val fieldIndex = record.fields.indexWhere(_._1 == fieldName)
                if (fieldIndex < 0) {
                  return Left(MigrationError.FieldNotFound(at, fieldName))
                }

                // Get current field value
                val (_, fieldValue) = record.fields(fieldIndex)

                // Apply converter to field value
                converter.convert(fieldValue) match {
                  case Right(convertedValue) =>
                    // Update field with converted value
                    val newFields = record.fields.updated(fieldIndex, (fieldName, convertedValue))
                    Right(DynamicValue.Record(newFields))
                  case Left(err) =>
                    Left(MigrationError.EvaluationError(at, s"Type conversion failed: $err"))
                }

              case _ =>
                Left(MigrationError.InvalidStructure(at, "Field node", s"${at.nodes.last}"))
            }
          case _ =>
            Left(MigrationError.InvalidStructure(at, "Record", value.getClass.getSimpleName))
        }
      } else {
        // Nested field - navigate, convert, update
        NavigationHelpers.navigateToField(value, at) match {
          case Right((_, _, _, fieldValue)) =>
            // Apply converter to field value
            converter.convert(fieldValue) match {
              case Right(convertedValue) =>
                // Update the nested field with converted value
                NavigationHelpers.updateNestedField(value, at, convertedValue)
              case Left(err) =>
                Left(MigrationError.EvaluationError(at, s"Type conversion failed: $err"))
            }
          case Left(err) => Left(err)
        }
      }
    }

    def reverse: MigrationAction = ChangeType(at, converter.reverse)
  }

  /**
   * Unwraps an Option field, extracting the value from Some or using a default
   * for None. Transforms Option[T] → T Reverse: Optionalize
   */
  final case class Mandate(
    at: DynamicOptic,
    default: SchemaExpr[DynamicValue, ?]
  ) extends MigrationAction {
    def execute(value: DynamicValue): Either[MigrationError, DynamicValue] = {
      // Helper to unwrap Option value
      def unwrapOption(optionValue: DynamicValue): Either[MigrationError, DynamicValue] = optionValue match {
        case DynamicValue.Variant(caseName, innerValue) =>
          caseName match {
            case "Some" =>
              // Extract value from Some's Record
              innerValue match {
                case DynamicValue.Record(fields) =>
                  // Look for "value" field
                  fields.find(_._1 == "value") match {
                    case Some((_, extractedValue)) => Right(extractedValue)
                    case None                      =>
                      Left(
                        MigrationError.InvalidStructure(at, "Record with 'value' field", "Record without 'value' field")
                      )
                  }
                case _ =>
                  Left(MigrationError.InvalidStructure(at, "Record inside Some", innerValue.getClass.getSimpleName))
              }
            case "None" =>
              // Use default value
              default.evalDynamic(value) match {
                case Right(seq) =>
                  if (seq.isEmpty) {
                    Left(MigrationError.EvaluationError(at, "Default expression returned empty sequence"))
                  } else {
                    Right(seq.head)
                  }
                case Left(err) =>
                  Left(MigrationError.EvaluationError(at, s"Failed to evaluate default: ${err.getMessage}"))
              }
            case other =>
              Left(MigrationError.InvalidStructure(at, "Variant with 'Some' or 'None'", s"Variant with case '$other'"))
          }
        case _ =>
          Left(MigrationError.InvalidStructure(at, "Variant (Option)", optionValue.getClass.getSimpleName))
      }

      // Handle root-level operation vs field operation
      if (at.nodes.isEmpty) {
        // Root-level operation - unwrap the value directly
        unwrapOption(value)
      } else if (at.nodes.length == 1) {
        // Optimized path: top-level field
        value match {
          case record: DynamicValue.Record =>
            at.nodes.last match {
              case DynamicOptic.Node.Field(fieldName) =>
                // Find the field
                val fieldIndex = record.fields.indexWhere(_._1 == fieldName)
                if (fieldIndex < 0) {
                  return Left(MigrationError.FieldNotFound(at, fieldName))
                }

                val (_, fieldValue) = record.fields(fieldIndex)

                // Unwrap the Option value
                unwrapOption(fieldValue) match {
                  case Right(unwrappedValue) =>
                    // Replace field with unwrapped value
                    val newFields = record.fields.updated(fieldIndex, (fieldName, unwrappedValue))
                    Right(DynamicValue.Record(newFields))
                  case Left(err) => Left(err)
                }

              case _ =>
                Left(MigrationError.InvalidStructure(at, "Field node", s"${at.nodes.last}"))
            }
          case _ =>
            Left(MigrationError.InvalidStructure(at, "Record", value.getClass.getSimpleName))
        }
      } else {
        // Nested field - navigate, unwrap, update
        NavigationHelpers.navigateToField(value, at) match {
          case Right((_, _, _, fieldValue)) =>
            // Unwrap the Option value
            unwrapOption(fieldValue) match {
              case Right(unwrappedValue) =>
                // Update the nested field with unwrapped value
                NavigationHelpers.updateNestedField(value, at, unwrappedValue)
              case Left(err) => Left(err)
            }
          case Left(err) => Left(err)
        }
      }
    }

    def reverse: MigrationAction = Optionalize(at, default)
  }

  /**
   * Wraps a field value in Some, making it optional. Transforms T → Option[T]
   * Reverse: Mandate
   */
  final case class Optionalize(
    at: DynamicOptic,
    defaultForReverse: SchemaExpr[DynamicValue, ?]
  ) extends MigrationAction {
    def execute(value: DynamicValue): Either[MigrationError, DynamicValue] = {
      // Helper to wrap value in Some
      def wrapInSome(originalValue: DynamicValue): DynamicValue =
        DynamicValue.Variant(
          "Some",
          DynamicValue.Record(Vector("value" -> originalValue))
        )

      // Handle root-level operation vs field operation
      if (at.nodes.isEmpty) {
        // Root-level operation - wrap the value directly
        Right(wrapInSome(value))
      } else if (at.nodes.length == 1) {
        // Optimized path: top-level field
        value match {
          case record: DynamicValue.Record =>
            at.nodes.last match {
              case DynamicOptic.Node.Field(fieldName) =>
                // Find the field
                val fieldIndex = record.fields.indexWhere(_._1 == fieldName)
                if (fieldIndex < 0) {
                  return Left(MigrationError.FieldNotFound(at, fieldName))
                }

                val (_, fieldValue) = record.fields(fieldIndex)

                // Wrap the field value in Some
                val wrappedValue = wrapInSome(fieldValue)

                // Replace field with wrapped value
                val newFields = record.fields.updated(fieldIndex, (fieldName, wrappedValue))
                Right(DynamicValue.Record(newFields))

              case _ =>
                Left(MigrationError.InvalidStructure(at, "Field node", s"${at.nodes.last}"))
            }
          case _ =>
            Left(MigrationError.InvalidStructure(at, "Record", value.getClass.getSimpleName))
        }
      } else {
        // Nested field - navigate, wrap, update
        NavigationHelpers.navigateToField(value, at) match {
          case Right((_, _, _, fieldValue)) =>
            // Wrap the field value in Some
            val wrappedValue = wrapInSome(fieldValue)
            // Update the nested field with wrapped value
            NavigationHelpers.updateNestedField(value, at, wrappedValue)
          case Left(err) => Left(err)
        }
      }
    }

    def reverse: MigrationAction = Mandate(at, defaultForReverse)
  }

  /**
   * Joins multiple source fields into a single target field using a combiner
   * expression. Example: firstName="John" + lastName="Doe" -> fullName="John
   * Doe"
   *
   * The combiner expression receives a temporary Record with fields named
   * "field0", "field1", etc. corresponding to the sourcePaths.
   *
   * Nested field support: SIMPLIFIED - all paths (target + sources) must share
   * the same parent. Example: _.address.street + _.address.city ->
   * _.address.fullAddress (all share parent _.address)
   *
   * Reverse: Split
   */
  final case class Join(
    at: DynamicOptic,
    sourcePaths: Vector[DynamicOptic],
    combiner: SchemaExpr[DynamicValue, ?]
  ) extends MigrationAction {
    def execute(value: DynamicValue): Either[MigrationError, DynamicValue] = {
      // Validate target path
      if (at.nodes.isEmpty) {
        return Left(MigrationError.InvalidStructure(at, "non-empty path", "empty path"))
      }

      // Validate all paths share the same parent (sibling constraint)
      if (at.nodes.length > 1) {
        val targetParent = at.nodes.dropRight(1)
        val invalidPaths = sourcePaths.filterNot { sourcePath =>
          sourcePath.nodes.length == at.nodes.length &&
          sourcePath.nodes.dropRight(1) == targetParent
        }

        if (invalidPaths.nonEmpty) {
          return Left(MigrationError.CrossPathJoinNotSupported(at, at, sourcePaths))
        }
      }

      // Helper to perform join on a record
      def performJoin(record: DynamicValue.Record): Either[MigrationError, DynamicValue.Record] = {
        // Extract target field name
        val targetFieldName = at.nodes.last match {
          case DynamicOptic.Node.Field(name) => name
          case _                             => return Left(MigrationError.InvalidStructure(at, "Field node", s"${at.nodes.last}"))
        }

        // Check if target field already exists
        if (record.fields.exists(_._1 == targetFieldName)) {
          return Left(MigrationError.FieldAlreadyExists(at, targetFieldName))
        }

        // Extract values from all source paths (assume they're in the same record)
        val sourceValuesResult: Either[MigrationError, Vector[DynamicValue]] = {
          val results = sourcePaths.map { sourcePath =>
            if (sourcePath.nodes.isEmpty) {
              Left(MigrationError.InvalidStructure(sourcePath, "non-empty path", "empty path"))
            } else {
              sourcePath.nodes.last match {
                case DynamicOptic.Node.Field(fieldName) =>
                  record.fields.find(_._1 == fieldName) match {
                    case Some((_, fieldValue)) => Right(fieldValue)
                    case None                  => Left(MigrationError.FieldNotFound(sourcePath, fieldName))
                  }
                case _ =>
                  Left(MigrationError.InvalidStructure(sourcePath, "Field node", s"${sourcePath.nodes.last}"))
              }
            }
          }

          val errors = results.collect { case Left(err) => err }
          if (errors.nonEmpty) {
            Left(errors.head)
          } else {
            Right(results.collect { case Right(v) => v })
          }
        }

        val sourceValues = sourceValuesResult match {
          case Right(values) => values
          case Left(err)     => return Left(err)
        }

        // Build temporary Record with field0, field1, etc.
        val tempFields = sourceValues.zipWithIndex.map { case (v, idx) => s"field$idx" -> v }
        val tempRecord = DynamicValue.Record(tempFields)

        // Evaluate combiner on temporary Record
        val combinedValue = combiner.evalDynamic(tempRecord) match {
          case Right(seq) =>
            if (seq.isEmpty) {
              return Left(MigrationError.EvaluationError(at, "Combiner expression returned empty sequence"))
            }
            seq.head
          case Left(err) =>
            return Left(MigrationError.EvaluationError(at, s"Failed to evaluate combiner: ${err.getMessage}"))
        }

        // Add the combined value to target field
        var resultFields = record.fields :+ (targetFieldName -> combinedValue)

        // Remove source fields from record
        val sourceFieldNames = sourcePaths.flatMap { sourcePath =>
          sourcePath.nodes.last match {
            case DynamicOptic.Node.Field(name) => Some(name)
            case _                             => None
          }
        }.toSet

        resultFields = resultFields.filterNot { case (fieldName, _) => sourceFieldNames.contains(fieldName) }

        Right(DynamicValue.Record(resultFields))
      }

      if (at.nodes.length == 1) {
        // Top-level join
        value match {
          case record: DynamicValue.Record => performJoin(record)
          case _                           => Left(MigrationError.InvalidStructure(at, "Record", value.getClass.getSimpleName))
        }
      } else {
        // Nested join - navigate to parent record, perform join, rebuild
        // All paths MUST share the same parent
        val parentPath = new DynamicOptic(at.nodes.dropRight(1))
        NavigationHelpers.navigateToField(value, parentPath) match {
          case Right((_, _, _, parentValue)) =>
            parentValue match {
              case parentRecord: DynamicValue.Record =>
                performJoin(parentRecord) match {
                  case Right(updatedParent) =>
                    NavigationHelpers.updateNestedField(value, parentPath, updatedParent)
                  case Left(err) => Left(err)
                }
              case _ =>
                Left(MigrationError.InvalidStructure(parentPath, "Record", parentValue.getClass.getSimpleName))
            }
          case Left(err) => Left(err)
        }
      }
    }

    def reverse: MigrationAction = {
      // Try to create an appropriate splitter from the combiner
      // For StringConcat with a literal delimiter, use StringSplit
      val splitter = combiner match {
        // Pattern: field0 + delimiter + field1 (simple 2-field join)
        case SchemaExpr.StringConcat(_, SchemaExpr.StringConcat(SchemaExpr.Literal(delimiter: String, _), _)) =>
          // Extract the delimiter and create a StringSplit
          SchemaExpr.StringSplit(SchemaExpr.Dynamic[DynamicValue, String](DynamicOptic.root), delimiter)

        // For other combiners, use the combiner as-is (may fail at runtime)
        case _ =>
          combiner.asInstanceOf[SchemaExpr[DynamicValue, Any]]
      }

      Split(at, sourcePaths, splitter)
    }
  }

  /**
   * Splits a single source field into multiple target fields using a splitter
   * expression. Example: fullName="John Doe" -> firstName="John",
   * lastName="Doe"
   *
   * The splitter expression should return a Seq[DynamicValue] where each
   * element corresponds to a target path by index (result[0] -> targetPaths[0],
   * etc.)
   *
   * Nested field support: SIMPLIFIED - all paths (source + targets) must share
   * the same parent. Example: _.address.fullAddress -> _.address.street +
   * _.address.city (all share parent _.address)
   *
   * Reverse: Join
   */
  final case class Split(
    at: DynamicOptic,
    targetPaths: Vector[DynamicOptic],
    splitter: SchemaExpr[DynamicValue, ?]
  ) extends MigrationAction {
    def execute(value: DynamicValue): Either[MigrationError, DynamicValue] = {
      // Validate source path
      if (at.nodes.isEmpty) {
        return Left(MigrationError.InvalidStructure(at, "non-empty path", "empty path"))
      }

      // Validate all paths share the same parent (sibling constraint)
      if (at.nodes.length > 1) {
        val sourceParent = at.nodes.dropRight(1)
        val invalidPaths = targetPaths.filterNot { targetPath =>
          targetPath.nodes.length == at.nodes.length &&
          targetPath.nodes.dropRight(1) == sourceParent
        }

        if (invalidPaths.nonEmpty) {
          return Left(MigrationError.CrossPathSplitNotSupported(at, at, targetPaths))
        }
      }

      // Helper to perform split on a record
      def performSplit(record: DynamicValue.Record): Either[MigrationError, DynamicValue.Record] = {
        // Extract source field name
        val sourceFieldName = at.nodes.last match {
          case DynamicOptic.Node.Field(name) => name
          case _                             => return Left(MigrationError.InvalidStructure(at, "Field node", s"${at.nodes.last}"))
        }

        // Extract value from source field
        val sourceFieldIndex = record.fields.indexWhere(_._1 == sourceFieldName)
        if (sourceFieldIndex < 0) {
          return Left(MigrationError.FieldNotFound(at, sourceFieldName))
        }

        val (_, sourceValue) = record.fields(sourceFieldIndex)

        // Evaluate splitter on source value
        val splitResults = splitter.evalDynamic(sourceValue) match {
          case Right(seq) => seq
          case Left(err)  =>
            return Left(MigrationError.EvaluationError(at, s"Failed to evaluate splitter: ${err.getMessage}"))
        }

        // Check result count matches targetPaths.length
        if (splitResults.length != targetPaths.length) {
          return Left(
            MigrationError.EvaluationError(
              at,
              s"Splitter returned ${splitResults.length} results, but expected ${targetPaths.length}"
            )
          )
        }

        // Validate target paths and extract field names
        val targetFieldNamesResult: Either[MigrationError, Vector[String]] = {
          val results = targetPaths.map { targetPath =>
            if (targetPath.nodes.isEmpty) {
              Left(MigrationError.InvalidStructure(targetPath, "non-empty path", "empty path"))
            } else {
              targetPath.nodes.last match {
                case DynamicOptic.Node.Field(name) =>
                  if (record.fields.exists(_._1 == name)) {
                    Left(MigrationError.FieldAlreadyExists(targetPath, name))
                  } else {
                    Right(name)
                  }
                case _ =>
                  Left(MigrationError.InvalidStructure(targetPath, "Field node", s"${targetPath.nodes.last}"))
              }
            }
          }

          val errors = results.collect { case Left(err) => err }
          if (errors.nonEmpty) {
            Left(errors.head)
          } else {
            Right(results.collect { case Right(v) => v })
          }
        }

        val targetFieldNames = targetFieldNamesResult match {
          case Right(names) => names
          case Left(err)    => return Left(err)
        }

        // Remove source field from record
        var resultFields = record.fields.patch(sourceFieldIndex, Nil, 1)

        // Add each split result to corresponding target field
        targetFieldNames.zip(splitResults).foreach { case (fieldName, fieldValue) =>
          resultFields = resultFields :+ (fieldName -> fieldValue)
        }

        Right(DynamicValue.Record(resultFields))
      }

      if (at.nodes.length == 1) {
        // Top-level split
        value match {
          case record: DynamicValue.Record => performSplit(record)
          case _                           => Left(MigrationError.InvalidStructure(at, "Record", value.getClass.getSimpleName))
        }
      } else {
        // Nested split - navigate to parent record, perform split, rebuild
        // All paths MUST share the same parent
        val parentPath = new DynamicOptic(at.nodes.dropRight(1))
        NavigationHelpers.navigateToField(value, parentPath) match {
          case Right((_, _, _, parentValue)) =>
            parentValue match {
              case parentRecord: DynamicValue.Record =>
                performSplit(parentRecord) match {
                  case Right(updatedParent) =>
                    NavigationHelpers.updateNestedField(value, parentPath, updatedParent)
                  case Left(err) => Left(err)
                }
              case _ =>
                Left(MigrationError.InvalidStructure(parentPath, "Record", parentValue.getClass.getSimpleName))
            }
          case Left(err) => Left(err)
        }
      }
    }

    def reverse: MigrationAction =
      // Structural reverse: Join with the same expression
      Join(at, targetPaths, splitter)
  }

  /**
   * Transforms all elements in a Sequence by applying a SchemaExpr to each
   * element independently. Example: [1, 2, 3] with (+1) -> [2, 3, 4]
   *
   * Reverse: TransformElements (best-effort, needs inverse expression)
   */
  final case class TransformElements(
    at: DynamicOptic,
    transform: SchemaExpr[DynamicValue, ?]
  ) extends MigrationAction {
    def execute(value: DynamicValue): Either[MigrationError, DynamicValue] = {
      // Helper to transform a sequence
      def transformSequence(seq: DynamicValue.Sequence): Either[MigrationError, DynamicValue.Sequence] = {
        // Transform each element independently
        val transformedResults = seq.elements.map { element =>
          transform.evalDynamic(element) match {
            case Right(results) =>
              if (results.isEmpty) {
                Left(MigrationError.EvaluationError(at, "Transform expression returned empty sequence"))
              } else {
                Right(results.head)
              }
            case Left(err) =>
              Left(MigrationError.EvaluationError(at, s"Failed to transform element: ${err.getMessage}"))
          }
        }

        // Check if any transformations failed
        val errors = transformedResults.collect { case Left(err) => err }
        if (errors.nonEmpty) {
          Left(errors.head)
        } else {
          val transformedElements = transformedResults.collect { case Right(v) => v }
          Right(DynamicValue.Sequence(transformedElements))
        }
      }

      // Handle root-level operation vs field operation
      if (at.nodes.isEmpty) {
        // Root-level operation - transform the sequence directly
        value match {
          case seq: DynamicValue.Sequence =>
            transformSequence(seq)
          case _ =>
            Left(MigrationError.InvalidStructure(at, "Sequence", value.getClass.getSimpleName))
        }
      } else if (at.nodes.length == 1) {
        // Optimized path: top-level field
        value match {
          case record: DynamicValue.Record =>
            at.nodes.last match {
              case DynamicOptic.Node.Field(fieldName) =>
                // Find the field
                val fieldIndex = record.fields.indexWhere(_._1 == fieldName)
                if (fieldIndex < 0) {
                  return Left(MigrationError.FieldNotFound(at, fieldName))
                }

                val (_, fieldValue) = record.fields(fieldIndex)

                // Transform the sequence
                fieldValue match {
                  case seq: DynamicValue.Sequence =>
                    transformSequence(seq) match {
                      case Right(transformedSeq) =>
                        // Replace field with transformed sequence
                        val newFields = record.fields.updated(fieldIndex, (fieldName, transformedSeq))
                        Right(DynamicValue.Record(newFields))
                      case Left(err) => Left(err)
                    }
                  case _ =>
                    Left(MigrationError.InvalidStructure(at, "Sequence", fieldValue.getClass.getSimpleName))
                }

              case _ =>
                Left(MigrationError.InvalidStructure(at, "Field node", s"${at.nodes.last}"))
            }
          case _ =>
            Left(MigrationError.InvalidStructure(at, "Record", value.getClass.getSimpleName))
        }
      } else {
        // Nested field - navigate, transform, update
        NavigationHelpers.navigateToField(value, at) match {
          case Right((_, _, _, fieldValue)) =>
            fieldValue match {
              case seq: DynamicValue.Sequence =>
                transformSequence(seq) match {
                  case Right(transformedSeq) =>
                    NavigationHelpers.updateNestedField(value, at, transformedSeq)
                  case Left(err) => Left(err)
                }
              case _ =>
                Left(MigrationError.InvalidStructure(at, "Sequence", fieldValue.getClass.getSimpleName))
            }
          case Left(err) => Left(err)
        }
      }
    }

    def reverse: MigrationAction = {
      // Use same pattern matching as TransformValue
      val inverseTransform = transform match {
        // ✅ LOSSLESS: Arithmetic Add/Subtract
        case SchemaExpr.Arithmetic(left, right, SchemaExpr.ArithmeticOperator.Add, num) =>
          SchemaExpr.Arithmetic(left, right, SchemaExpr.ArithmeticOperator.Subtract, num)

        case SchemaExpr.Arithmetic(left, right, SchemaExpr.ArithmeticOperator.Subtract, num) =>
          SchemaExpr.Arithmetic(left, right, SchemaExpr.ArithmeticOperator.Add, num)

        // ✅ LOSSLESS: Arithmetic Multiply/Divide
        case SchemaExpr.Arithmetic(left, right, SchemaExpr.ArithmeticOperator.Multiply, num) =>
          SchemaExpr.Arithmetic(left, right, SchemaExpr.ArithmeticOperator.Divide, num)

        case SchemaExpr.Arithmetic(left, right, SchemaExpr.ArithmeticOperator.Divide, num) =>
          SchemaExpr.Arithmetic(left, right, SchemaExpr.ArithmeticOperator.Multiply, num)

        // ✅ LOSSLESS: Boolean Not (self-inverse)
        case SchemaExpr.Not(expr) =>
          SchemaExpr.Not(expr)

        // ⚠️ LOSSY: String case conversions
        case SchemaExpr.StringUppercase(expr) =>
          SchemaExpr.StringLowercase(expr)

        case SchemaExpr.StringLowercase(expr) =>
          SchemaExpr.StringUppercase(expr)

        // ❌ IRREVERSIBLE: Default to identity
        case _ => transform
      }

      TransformElements(at, inverseTransform)
    }
  }

  /**
   * Transforms all keys in a Map by applying a SchemaExpr to each key
   * independently. Example: Map("a" -> 1) with uppercase -> Map("A" -> 1)
   *
   * Reverse: TransformKeys (best-effort, needs inverse expression)
   */
  final case class TransformKeys(
    at: DynamicOptic,
    transform: SchemaExpr[DynamicValue, ?]
  ) extends MigrationAction {
    def execute(value: DynamicValue): Either[MigrationError, DynamicValue] = {
      // Helper to transform map keys
      def transformMapKeys(map: DynamicValue.Map): Either[MigrationError, DynamicValue.Map] = {
        // Transform each key independently, preserving values
        val transformedResults = map.entries.map { case (key, value) =>
          transform.evalDynamic(key) match {
            case Right(results) =>
              if (results.isEmpty) {
                Left(MigrationError.EvaluationError(at, "Transform expression returned empty sequence"))
              } else {
                Right((results.head, value))
              }
            case Left(err) =>
              Left(MigrationError.EvaluationError(at, s"Failed to transform key: ${err.getMessage}"))
          }
        }

        // Check if any transformations failed
        val errors = transformedResults.collect { case Left(err) => err }
        if (errors.nonEmpty) {
          Left(errors.head)
        } else {
          val transformedEntries = transformedResults.collect { case Right(entry) => entry }
          Right(DynamicValue.Map(transformedEntries))
        }
      }

      // Handle root-level operation vs field operation
      if (at.nodes.isEmpty) {
        // Root-level operation - transform the map directly
        value match {
          case map: DynamicValue.Map =>
            transformMapKeys(map)
          case _ =>
            Left(MigrationError.InvalidStructure(at, "Map", value.getClass.getSimpleName))
        }
      } else if (at.nodes.length == 1) {
        // Optimized path: top-level field
        value match {
          case record: DynamicValue.Record =>
            at.nodes.last match {
              case DynamicOptic.Node.Field(fieldName) =>
                // Find the field
                val fieldIndex = record.fields.indexWhere(_._1 == fieldName)
                if (fieldIndex < 0) {
                  return Left(MigrationError.FieldNotFound(at, fieldName))
                }

                val (_, fieldValue) = record.fields(fieldIndex)

                // Transform the map
                fieldValue match {
                  case map: DynamicValue.Map =>
                    transformMapKeys(map) match {
                      case Right(transformedMap) =>
                        // Replace field with transformed map
                        val newFields = record.fields.updated(fieldIndex, (fieldName, transformedMap))
                        Right(DynamicValue.Record(newFields))
                      case Left(err) => Left(err)
                    }
                  case _ =>
                    Left(MigrationError.InvalidStructure(at, "Map", fieldValue.getClass.getSimpleName))
                }

              case _ =>
                Left(MigrationError.InvalidStructure(at, "Field node", s"${at.nodes.last}"))
            }
          case _ =>
            Left(MigrationError.InvalidStructure(at, "Record", value.getClass.getSimpleName))
        }
      } else {
        // Nested field - navigate, transform, update
        NavigationHelpers.navigateToField(value, at) match {
          case Right((_, _, _, fieldValue)) =>
            fieldValue match {
              case map: DynamicValue.Map =>
                transformMapKeys(map) match {
                  case Right(transformedMap) =>
                    NavigationHelpers.updateNestedField(value, at, transformedMap)
                  case Left(err) => Left(err)
                }
              case _ =>
                Left(MigrationError.InvalidStructure(at, "Map", fieldValue.getClass.getSimpleName))
            }
          case Left(err) => Left(err)
        }
      }
    }

    def reverse: MigrationAction = {
      // Use same pattern matching as TransformValue
      val inverseTransform = transform match {
        // ✅ LOSSLESS: Arithmetic Add/Subtract
        case SchemaExpr.Arithmetic(left, right, SchemaExpr.ArithmeticOperator.Add, num) =>
          SchemaExpr.Arithmetic(left, right, SchemaExpr.ArithmeticOperator.Subtract, num)

        case SchemaExpr.Arithmetic(left, right, SchemaExpr.ArithmeticOperator.Subtract, num) =>
          SchemaExpr.Arithmetic(left, right, SchemaExpr.ArithmeticOperator.Add, num)

        // ✅ LOSSLESS: Arithmetic Multiply/Divide
        case SchemaExpr.Arithmetic(left, right, SchemaExpr.ArithmeticOperator.Multiply, num) =>
          SchemaExpr.Arithmetic(left, right, SchemaExpr.ArithmeticOperator.Divide, num)

        case SchemaExpr.Arithmetic(left, right, SchemaExpr.ArithmeticOperator.Divide, num) =>
          SchemaExpr.Arithmetic(left, right, SchemaExpr.ArithmeticOperator.Multiply, num)

        // ✅ LOSSLESS: Boolean Not (self-inverse)
        case SchemaExpr.Not(expr) =>
          SchemaExpr.Not(expr)

        // ⚠️ LOSSY: String case conversions
        case SchemaExpr.StringUppercase(expr) =>
          SchemaExpr.StringLowercase(expr)

        case SchemaExpr.StringLowercase(expr) =>
          SchemaExpr.StringUppercase(expr)

        // ❌ IRREVERSIBLE: Default to identity
        case _ => transform
      }

      TransformKeys(at, inverseTransform)
    }
  }

  /**
   * Transforms all values in a Map by applying a SchemaExpr to each value
   * independently. Example: Map("a" -> 1) with (+1) -> Map("a" -> 2)
   *
   * Reverse: TransformValues (best-effort, needs inverse expression)
   */
  final case class TransformValues(
    at: DynamicOptic,
    transform: SchemaExpr[DynamicValue, ?]
  ) extends MigrationAction {
    def execute(value: DynamicValue): Either[MigrationError, DynamicValue] = {
      // Helper to transform map values
      def transformMapValues(map: DynamicValue.Map): Either[MigrationError, DynamicValue.Map] = {
        // Transform each value independently, preserving keys
        val transformedResults = map.entries.map { case (key, value) =>
          transform.evalDynamic(value) match {
            case Right(results) =>
              if (results.isEmpty) {
                Left(MigrationError.EvaluationError(at, "Transform expression returned empty sequence"))
              } else {
                Right((key, results.head))
              }
            case Left(err) =>
              Left(MigrationError.EvaluationError(at, s"Failed to transform value: ${err.getMessage}"))
          }
        }

        // Check if any transformations failed
        val errors = transformedResults.collect { case Left(err) => err }
        if (errors.nonEmpty) {
          Left(errors.head)
        } else {
          val transformedEntries = transformedResults.collect { case Right(entry) => entry }
          Right(DynamicValue.Map(transformedEntries))
        }
      }

      // Handle root-level operation vs field operation
      if (at.nodes.isEmpty) {
        // Root-level operation - transform the map directly
        value match {
          case map: DynamicValue.Map =>
            transformMapValues(map)
          case _ =>
            Left(MigrationError.InvalidStructure(at, "Map", value.getClass.getSimpleName))
        }
      } else if (at.nodes.length == 1) {
        // Optimized path: top-level field
        value match {
          case record: DynamicValue.Record =>
            at.nodes.last match {
              case DynamicOptic.Node.Field(fieldName) =>
                // Find the field
                val fieldIndex = record.fields.indexWhere(_._1 == fieldName)
                if (fieldIndex < 0) {
                  return Left(MigrationError.FieldNotFound(at, fieldName))
                }

                val (_, fieldValue) = record.fields(fieldIndex)

                // Transform the map
                fieldValue match {
                  case map: DynamicValue.Map =>
                    transformMapValues(map) match {
                      case Right(transformedMap) =>
                        // Replace field with transformed map
                        val newFields = record.fields.updated(fieldIndex, (fieldName, transformedMap))
                        Right(DynamicValue.Record(newFields))
                      case Left(err) => Left(err)
                    }
                  case _ =>
                    Left(MigrationError.InvalidStructure(at, "Map", fieldValue.getClass.getSimpleName))
                }

              case _ =>
                Left(MigrationError.InvalidStructure(at, "Field node", s"${at.nodes.last}"))
            }
          case _ =>
            Left(MigrationError.InvalidStructure(at, "Record", value.getClass.getSimpleName))
        }
      } else {
        // Nested field - navigate, transform, update
        NavigationHelpers.navigateToField(value, at) match {
          case Right((_, _, _, fieldValue)) =>
            fieldValue match {
              case map: DynamicValue.Map =>
                transformMapValues(map) match {
                  case Right(transformedMap) =>
                    NavigationHelpers.updateNestedField(value, at, transformedMap)
                  case Left(err) => Left(err)
                }
              case _ =>
                Left(MigrationError.InvalidStructure(at, "Map", fieldValue.getClass.getSimpleName))
            }
          case Left(err) => Left(err)
        }
      }
    }

    def reverse: MigrationAction = {
      // Use same pattern matching as TransformValue
      val inverseTransform = transform match {
        // ✅ LOSSLESS: Arithmetic Add/Subtract
        case SchemaExpr.Arithmetic(left, right, SchemaExpr.ArithmeticOperator.Add, num) =>
          SchemaExpr.Arithmetic(left, right, SchemaExpr.ArithmeticOperator.Subtract, num)

        case SchemaExpr.Arithmetic(left, right, SchemaExpr.ArithmeticOperator.Subtract, num) =>
          SchemaExpr.Arithmetic(left, right, SchemaExpr.ArithmeticOperator.Add, num)

        // ✅ LOSSLESS: Arithmetic Multiply/Divide
        case SchemaExpr.Arithmetic(left, right, SchemaExpr.ArithmeticOperator.Multiply, num) =>
          SchemaExpr.Arithmetic(left, right, SchemaExpr.ArithmeticOperator.Divide, num)

        case SchemaExpr.Arithmetic(left, right, SchemaExpr.ArithmeticOperator.Divide, num) =>
          SchemaExpr.Arithmetic(left, right, SchemaExpr.ArithmeticOperator.Multiply, num)

        // ✅ LOSSLESS: Boolean Not (self-inverse)
        case SchemaExpr.Not(expr) =>
          SchemaExpr.Not(expr)

        // ⚠️ LOSSY: String case conversions
        case SchemaExpr.StringUppercase(expr) =>
          SchemaExpr.StringLowercase(expr)

        case SchemaExpr.StringLowercase(expr) =>
          SchemaExpr.StringUppercase(expr)

        // ❌ IRREVERSIBLE: Default to identity
        case _ => transform
      }

      TransformValues(at, inverseTransform)
    }
  }

  /**
   * Renames a variant case. Changes the case name in a DynamicValue.Variant.
   * Example: Variant("PayPal", ...) with from="PayPal", to="PaypalPayment"
   * -&gt; Variant("PaypalPayment", ...)
   *
   * Reverse: RenameCase with flipped from/to
   */
  final case class RenameCase(
    at: DynamicOptic,
    from: String,
    to: String
  ) extends MigrationAction {
    def execute(value: DynamicValue): Either[MigrationError, DynamicValue] = {
      // Helper to rename a variant case
      def renameVariant(variant: DynamicValue.Variant): Either[MigrationError, DynamicValue] =
        if (variant.caseName == from) {
          // Rename the case
          Right(DynamicValue.Variant(to, variant.value))
        } else {
          // Case name doesn't match, leave unchanged
          Right(variant)
        }

      // Handle root-level operation vs field operation
      if (at.nodes.isEmpty) {
        // Root-level operation - rename the variant directly
        value match {
          case variant: DynamicValue.Variant =>
            renameVariant(variant)
          case _ =>
            Left(MigrationError.InvalidStructure(at, "Variant", value.getClass.getSimpleName))
        }
      } else if (at.nodes.length == 1) {
        // Optimized path: top-level field
        value match {
          case record: DynamicValue.Record =>
            at.nodes.last match {
              case DynamicOptic.Node.Field(fieldName) =>
                // Find the field
                val fieldIndex = record.fields.indexWhere(_._1 == fieldName)
                if (fieldIndex < 0) {
                  return Left(MigrationError.FieldNotFound(at, fieldName))
                }

                val (_, fieldValue) = record.fields(fieldIndex)

                // Rename the variant
                fieldValue match {
                  case variant: DynamicValue.Variant =>
                    renameVariant(variant) match {
                      case Right(renamedVariant) =>
                        // Replace field with renamed variant
                        val newFields = record.fields.updated(fieldIndex, (fieldName, renamedVariant))
                        Right(DynamicValue.Record(newFields))
                      case Left(err) => Left(err)
                    }
                  case _ =>
                    Left(MigrationError.InvalidStructure(at, "Variant", fieldValue.getClass.getSimpleName))
                }

              case _ =>
                Left(MigrationError.InvalidStructure(at, "Field node", s"${at.nodes.last}"))
            }
          case _ =>
            Left(MigrationError.InvalidStructure(at, "Record", value.getClass.getSimpleName))
        }
      } else {
        // Nested field - navigate, rename, update
        NavigationHelpers.navigateToField(value, at) match {
          case Right((_, _, _, fieldValue)) =>
            fieldValue match {
              case variant: DynamicValue.Variant =>
                renameVariant(variant) match {
                  case Right(renamedVariant) =>
                    NavigationHelpers.updateNestedField(value, at, renamedVariant)
                  case Left(err) => Left(err)
                }
              case _ =>
                Left(MigrationError.InvalidStructure(at, "Variant", fieldValue.getClass.getSimpleName))
            }
          case Left(err) => Left(err)
        }
      }
    }

    def reverse: MigrationAction = RenameCase(at, to, from)
  }

  /**
   * Transforms the inner value of a specific variant case by applying nested
   * migration actions. Only transforms matching cases, leaves others unchanged.
   * Example: Transform CreditCard case fields while leaving PayPal unchanged
   *
   * Reverse: TransformCase with reversed nested actions
   */
  final case class TransformCase(
    at: DynamicOptic,
    caseName: String,
    actions: Vector[MigrationAction]
  ) extends MigrationAction {
    def execute(value: DynamicValue): Either[MigrationError, DynamicValue] = {
      // Helper to transform a variant case if it matches
      def transformVariant(variant: DynamicValue.Variant): Either[MigrationError, DynamicValue] =
        if (variant.caseName == caseName) {
          // Case matches - apply nested actions to the inner value
          // Create a DynamicMigration from the actions and apply it
          val migration = DynamicMigration(actions)
          migration.apply(variant.value).map { transformedValue =>
            DynamicValue.Variant(caseName, transformedValue)
          }
        } else {
          // Case doesn't match, leave unchanged
          Right(variant)
        }

      // Handle root-level operation vs field operation
      if (at.nodes.isEmpty) {
        // Root-level operation - transform the variant directly
        value match {
          case variant: DynamicValue.Variant =>
            transformVariant(variant)
          case _ =>
            Left(MigrationError.InvalidStructure(at, "Variant", value.getClass.getSimpleName))
        }
      } else if (at.nodes.length == 1) {
        // Optimized path: top-level field
        value match {
          case record: DynamicValue.Record =>
            at.nodes.last match {
              case DynamicOptic.Node.Field(fieldName) =>
                // Find the field
                val fieldIndex = record.fields.indexWhere(_._1 == fieldName)
                if (fieldIndex < 0) {
                  return Left(MigrationError.FieldNotFound(at, fieldName))
                }

                val (_, fieldValue) = record.fields(fieldIndex)

                // Transform the variant
                fieldValue match {
                  case variant: DynamicValue.Variant =>
                    transformVariant(variant) match {
                      case Right(transformedVariant) =>
                        // Replace field with transformed variant
                        val newFields = record.fields.updated(fieldIndex, (fieldName, transformedVariant))
                        Right(DynamicValue.Record(newFields))
                      case Left(err) => Left(err)
                    }
                  case _ =>
                    Left(MigrationError.InvalidStructure(at, "Variant", fieldValue.getClass.getSimpleName))
                }

              case _ =>
                Left(MigrationError.InvalidStructure(at, "Field node", s"${at.nodes.last}"))
            }
          case _ =>
            Left(MigrationError.InvalidStructure(at, "Record", value.getClass.getSimpleName))
        }
      } else {
        // Nested field - navigate, transform, update
        NavigationHelpers.navigateToField(value, at) match {
          case Right((_, _, _, fieldValue)) =>
            fieldValue match {
              case variant: DynamicValue.Variant =>
                transformVariant(variant) match {
                  case Right(transformedVariant) =>
                    NavigationHelpers.updateNestedField(value, at, transformedVariant)
                  case Left(err) => Left(err)
                }
              case _ =>
                Left(MigrationError.InvalidStructure(at, "Variant", fieldValue.getClass.getSimpleName))
            }
          case Left(err) => Left(err)
        }
      }
    }

    def reverse: MigrationAction = {
      // Reverse the nested actions
      val reversedActions = actions.reverse.map(_.reverse)
      TransformCase(at, caseName, reversedActions)
    }
  }
}
