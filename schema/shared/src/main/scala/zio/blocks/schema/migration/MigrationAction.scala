package zio.blocks.schema.migration

import zio.blocks.chunk.Chunk
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

  // Helper to compute the inverse of a SchemaExpr for reversible transformations.
  // Returns the original expression for irreversible operations.
  private def inverseTransform(transform: SchemaExpr[DynamicValue, ?]): SchemaExpr[DynamicValue, ?] =
    transform match {
      // Lossless: Arithmetic Add/Subtract
      case SchemaExpr.Arithmetic(left, right, SchemaExpr.ArithmeticOperator.Add, num) =>
        SchemaExpr.Arithmetic(left, right, SchemaExpr.ArithmeticOperator.Subtract, num)

      case SchemaExpr.Arithmetic(left, right, SchemaExpr.ArithmeticOperator.Subtract, num) =>
        SchemaExpr.Arithmetic(left, right, SchemaExpr.ArithmeticOperator.Add, num)

      // Lossless: Arithmetic Multiply/Divide
      case SchemaExpr.Arithmetic(left, right, SchemaExpr.ArithmeticOperator.Multiply, num) =>
        SchemaExpr.Arithmetic(left, right, SchemaExpr.ArithmeticOperator.Divide, num)

      case SchemaExpr.Arithmetic(left, right, SchemaExpr.ArithmeticOperator.Divide, num) =>
        SchemaExpr.Arithmetic(left, right, SchemaExpr.ArithmeticOperator.Multiply, num)

      // Lossless: Boolean Not (self-inverse)
      case SchemaExpr.Not(expr) =>
        SchemaExpr.Not(expr)

      // Lossy: String case conversions (loses original casing)
      case SchemaExpr.StringUppercase(expr) =>
        SchemaExpr.StringLowercase(expr)

      case SchemaExpr.StringLowercase(expr) =>
        SchemaExpr.StringUppercase(expr)

      // Irreversible: Default to identity for operations that cannot be reversed
      // (StringLength, Relational, Logical, StringRegexMatch, etc.)
      case _ => transform
    }

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
     * Transform a value at the given path. If path is empty, transforms the
     * root value directly. Otherwise, navigates to the field and transforms it.
     */
    def transformAt(
      value: DynamicValue,
      path: DynamicOptic,
      transform: DynamicValue => Either[MigrationError, DynamicValue]
    ): Either[MigrationError, DynamicValue] =
      if (path.nodes.isEmpty) {
        transform(value)
      } else {
        navigateToField(value, path) match {
          case Right((_, _, _, fieldValue)) =>
            transform(fieldValue) match {
              case Right(transformedValue) => updateNestedField(value, path, transformedValue)
              case Left(err)               => Left(err)
            }
          case Left(err) => Left(err)
        }
      }

    /**
     * Modify the record containing the field at the given path. Path must be
     * non-empty and consist only of Field nodes. The modify function receives
     * the parent record and target field name.
     */
    def modifyRecordAt(
      value: DynamicValue,
      path: DynamicOptic,
      modify: (DynamicValue.Record, String) => Either[MigrationError, DynamicValue.Record]
    ): Either[MigrationError, DynamicValue] = {
      if (path.nodes.isEmpty) {
        return Left(MigrationError.InvalidStructure(path, "non-empty path", "empty path"))
      }

      val targetFieldName = path.nodes.last match {
        case DynamicOptic.Node.Field(name) => name
        case _                             => return Left(MigrationError.InvalidStructure(path, "Field node", s"${path.nodes.last}"))
      }

      if (path.nodes.length == 1) {
        value match {
          case record: DynamicValue.Record => modify(record, targetFieldName)
          case _                           => Left(MigrationError.InvalidStructure(path, "Record", value.getClass.getSimpleName))
        }
      } else {
        val parentPath = new DynamicOptic(path.nodes.dropRight(1))
        navigateToField(value, parentPath) match {
          case Right((_, _, _, parentValue)) =>
            parentValue match {
              case parentRecord: DynamicValue.Record =>
                modify(parentRecord, targetFieldName) match {
                  case Right(modifiedRecord) => updateNestedField(value, parentPath, modifiedRecord)
                  case Left(err)             => Left(err)
                }
              case _ =>
                Left(MigrationError.InvalidStructure(parentPath, "Record", parentValue.getClass.getSimpleName))
            }
          case Left(err) => Left(err)
        }
      }
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
      // Evaluate default expression first
      val defaultValue = default.evalDynamic(value) match {
        case Right(seq) if seq.nonEmpty => seq.head
        case Right(_)                   => return Left(MigrationError.EvaluationError(at, "Default expression returned empty sequence"))
        case Left(err)                  =>
          return Left(MigrationError.EvaluationError(at, s"Failed to evaluate default: ${err.getMessage}"))
      }

      NavigationHelpers.modifyRecordAt(
        value,
        at,
        (record, fieldName) =>
          if (record.fields.exists(_._1 == fieldName)) Left(MigrationError.FieldAlreadyExists(at, fieldName))
          else Right(DynamicValue.Record(record.fields :+ (fieldName -> defaultValue)))
      )
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
    def execute(value: DynamicValue): Either[MigrationError, DynamicValue] =
      NavigationHelpers.modifyRecordAt(
        value,
        at,
        (record, fieldName) => {
          val fieldIndex = record.fields.indexWhere(_._1 == fieldName)
          if (fieldIndex < 0) Left(MigrationError.FieldNotFound(at, fieldName))
          else Right(DynamicValue.Record(record.fields.patch(fieldIndex, Nil, 1)))
        }
      )

    def reverse: MigrationAction = AddField(at, defaultForReverse)
  }

  /**
   * Renames a field in a record. Reverse: Rename with flipped to/from
   */
  final case class Rename(
    at: DynamicOptic,
    to: String
  ) extends MigrationAction {
    def execute(value: DynamicValue): Either[MigrationError, DynamicValue] =
      NavigationHelpers.modifyRecordAt(
        value,
        at,
        (record, fromName) => {
          val fromIndex = record.fields.indexWhere(_._1 == fromName)
          if (fromIndex < 0) Left(MigrationError.FieldNotFound(at, fromName))
          else if (record.fields.exists(_._1 == to)) Left(MigrationError.FieldAlreadyExists(at, to))
          else {
            val (_, fieldValue) = record.fields(fromIndex)
            Right(DynamicValue.Record(record.fields.updated(fromIndex, (to, fieldValue))))
          }
        }
      )

    def reverse: MigrationAction =
      at.nodes.last match {
        case DynamicOptic.Node.Field(fromName) =>
          val reversePath =
            if (at.nodes.length == 1) DynamicOptic.root.field(to)
            else new DynamicOptic(at.nodes.dropRight(1) :+ DynamicOptic.Node.Field(to))
          Rename(reversePath, fromName)
        case other =>
          throw new IllegalStateException(s"Rename.reverse requires a Field node at path end, but found: $other")
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

      NavigationHelpers.transformAt(
        value,
        at,
        fieldValue =>
          transform.evalDynamic(fieldValue) match {
            case Right(seq) if seq.nonEmpty => Right(seq.head)
            case Right(_)                   => Left(MigrationError.EvaluationError(at, "Transform expression returned empty sequence"))
            case Left(err)                  =>
              Left(MigrationError.EvaluationError(at, s"Failed to evaluate transform: ${err.getMessage}"))
          }
      )
    }

    def reverse: MigrationAction = TransformValue(at, inverseTransform(transform))
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

      NavigationHelpers.transformAt(
        value,
        at,
        fieldValue =>
          converter
            .convert(fieldValue)
            .left
            .map(err => MigrationError.EvaluationError(at, s"Type conversion failed: $err"))
      )
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
    def execute(rootValue: DynamicValue): Either[MigrationError, DynamicValue] = {
      def unwrapOption(optionValue: DynamicValue): Either[MigrationError, DynamicValue] = optionValue match {
        case DynamicValue.Variant(caseName, innerValue) =>
          caseName match {
            case "Some" =>
              innerValue match {
                case DynamicValue.Record(fields) =>
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
              default.evalDynamic(rootValue) match {
                case Right(seq) if seq.nonEmpty => Right(seq.head)
                case Right(_)                   => Left(MigrationError.EvaluationError(at, "Default expression returned empty sequence"))
                case Left(err)                  =>
                  Left(MigrationError.EvaluationError(at, s"Failed to evaluate default: ${err.getMessage}"))
              }
            case other =>
              Left(MigrationError.InvalidStructure(at, "Variant with 'Some' or 'None'", s"Variant with case '$other'"))
          }
        case _ => Left(MigrationError.InvalidStructure(at, "Variant (Option)", optionValue.getClass.getSimpleName))
      }

      NavigationHelpers.transformAt(rootValue, at, unwrapOption)
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
      def wrapInSome(originalValue: DynamicValue): DynamicValue =
        DynamicValue.Variant("Some", DynamicValue.Record(Chunk("value" -> originalValue)))

      NavigationHelpers.transformAt(value, at, v => Right(wrapInSome(v)))
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
      // Validate sibling constraint for nested paths
      if (at.nodes.length > 1) {
        val targetParent = at.nodes.dropRight(1)
        val invalidPaths = sourcePaths.filterNot { sourcePath =>
          sourcePath.nodes.length == at.nodes.length && sourcePath.nodes.dropRight(1) == targetParent
        }
        if (invalidPaths.nonEmpty) {
          return Left(MigrationError.CrossPathJoinNotSupported(at, at, sourcePaths))
        }
      }

      NavigationHelpers.modifyRecordAt(
        value,
        at,
        (record, targetFieldName) =>
          if (record.fields.exists(_._1 == targetFieldName)) {
            Left(MigrationError.FieldAlreadyExists(at, targetFieldName))
          } else {
            // Extract values from all source paths
            val sourceValuesResult: Either[MigrationError, Vector[DynamicValue]] = {
              val results = sourcePaths.map { sourcePath =>
                if (sourcePath.nodes.isEmpty)
                  Left(MigrationError.InvalidStructure(sourcePath, "non-empty path", "empty path"))
                else
                  sourcePath.nodes.last match {
                    case DynamicOptic.Node.Field(fieldName) =>
                      record.fields.find(_._1 == fieldName) match {
                        case Some((_, fv)) => Right(fv)
                        case None          => Left(MigrationError.FieldNotFound(sourcePath, fieldName))
                      }
                    case _ =>
                      Left(MigrationError.InvalidStructure(sourcePath, "Field node", s"${sourcePath.nodes.last}"))
                  }
              }
              results.collectFirst { case Left(err) => err }.toLeft(results.collect { case Right(v) => v })
            }

            sourceValuesResult.flatMap { sourceValues =>
              // Build temporary Record with field0, field1, etc. and evaluate combiner
              val tempFields = Chunk.fromIterable(sourceValues.zipWithIndex.map { case (v, idx) => s"field$idx" -> v })
              combiner.evalDynamic(DynamicValue.Record(tempFields)) match {
                case Right(seq) if seq.nonEmpty =>
                  val combinedValue = seq.head
                  // Add combined value and remove source fields
                  val sourceFieldNames = sourcePaths
                    .flatMap(_.nodes.last match {
                      case DynamicOptic.Node.Field(name) => Some(name)
                      case _                             => None
                    })
                    .toSet
                  val resultFields = (record.fields :+ (targetFieldName -> combinedValue)).filterNot { case (fn, _) =>
                    sourceFieldNames.contains(fn)
                  }
                  Right(DynamicValue.Record(resultFields))
                case Right(_)  => Left(MigrationError.EvaluationError(at, "Combiner expression returned empty sequence"))
                case Left(err) =>
                  Left(MigrationError.EvaluationError(at, s"Failed to evaluate combiner: ${err.getMessage}"))
              }
            }
          }
      )
    }

    def reverse: MigrationAction = {
      val splitter = combiner match {
        case SchemaExpr.StringConcat(_, SchemaExpr.StringConcat(SchemaExpr.Literal(delimiter: String, _), _)) =>
          SchemaExpr.StringSplit(SchemaExpr.Dynamic[DynamicValue, String](DynamicOptic.root), delimiter)
        case _ => combiner
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
      // Validate sibling constraint for nested paths
      if (at.nodes.length > 1) {
        val sourceParent = at.nodes.dropRight(1)
        val invalidPaths = targetPaths.filterNot { targetPath =>
          targetPath.nodes.length == at.nodes.length && targetPath.nodes.dropRight(1) == sourceParent
        }
        if (invalidPaths.nonEmpty) {
          return Left(MigrationError.CrossPathSplitNotSupported(at, at, targetPaths))
        }
      }

      NavigationHelpers.modifyRecordAt(
        value,
        at,
        (record, sourceFieldName) => {
          val sourceFieldIndex = record.fields.indexWhere(_._1 == sourceFieldName)
          if (sourceFieldIndex < 0) {
            Left(MigrationError.FieldNotFound(at, sourceFieldName))
          } else {
            val (_, sourceValue) = record.fields(sourceFieldIndex)

            // Evaluate splitter on source value
            splitter.evalDynamic(sourceValue) match {
              case Left(err) =>
                Left(MigrationError.EvaluationError(at, s"Failed to evaluate splitter: ${err.getMessage}"))
              case Right(splitResults) =>
                if (splitResults.length != targetPaths.length) {
                  Left(
                    MigrationError.EvaluationError(
                      at,
                      s"Splitter returned ${splitResults.length} results, but expected ${targetPaths.length}"
                    )
                  )
                } else {
                  // Validate target paths and extract field names
                  val targetFieldNamesResult: Either[MigrationError, Vector[String]] = {
                    val results = targetPaths.map { targetPath =>
                      if (targetPath.nodes.isEmpty)
                        Left(MigrationError.InvalidStructure(targetPath, "non-empty path", "empty path"))
                      else
                        targetPath.nodes.last match {
                          case DynamicOptic.Node.Field(name) =>
                            if (record.fields.exists(_._1 == name))
                              Left(MigrationError.FieldAlreadyExists(targetPath, name))
                            else Right(name)
                          case _ =>
                            Left(MigrationError.InvalidStructure(targetPath, "Field node", s"${targetPath.nodes.last}"))
                        }
                    }
                    results.collectFirst { case Left(err) => err }.toLeft(results.collect { case Right(v) => v })
                  }

                  targetFieldNamesResult.map { targetFieldNames =>
                    // Remove source field and add split results
                    val withoutSource = record.fields.patch(sourceFieldIndex, Nil, 1)
                    val resultFields  =
                      targetFieldNames.zip(splitResults).foldLeft(withoutSource) { case (fields, (fn, fv)) =>
                        fields :+ (fn -> fv)
                      }
                    DynamicValue.Record(resultFields)
                  }
                }
            }
          }
        }
      )
    }

    def reverse: MigrationAction = {
      val combiner: SchemaExpr[DynamicValue, ?] = splitter match {
        case SchemaExpr.StringSplit(_, delimiter) if targetPaths.length == 2 =>
          SchemaExpr.StringConcat(
            SchemaExpr.Dynamic[DynamicValue, String](DynamicOptic.root.field("field0")),
            SchemaExpr.StringConcat(
              SchemaExpr.Literal(delimiter, zio.blocks.schema.Schema.string),
              SchemaExpr.Dynamic[DynamicValue, String](DynamicOptic.root.field("field1"))
            )
          )
        case _ => SchemaExpr.Dynamic[DynamicValue, DynamicValue](DynamicOptic.root.field("field0"))
      }
      Join(at, targetPaths, combiner)
    }
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
      def transformSequence(seq: DynamicValue.Sequence): Either[MigrationError, DynamicValue] = {
        val transformedResults = seq.elements.map { element =>
          transform.evalDynamic(element) match {
            case Right(results) if results.nonEmpty => Right(results.head)
            case Right(_)                           => Left(MigrationError.EvaluationError(at, "Transform expression returned empty sequence"))
            case Left(err)                          =>
              Left(MigrationError.EvaluationError(at, s"Failed to transform element: ${err.getMessage}"))
          }
        }
        transformedResults.collectFirst { case Left(err) => err }
          .toLeft(DynamicValue.Sequence(transformedResults.collect { case Right(v) => v }))
      }

      NavigationHelpers.transformAt(
        value,
        at,
        {
          case seq: DynamicValue.Sequence => transformSequence(seq)
          case other                      => Left(MigrationError.InvalidStructure(at, "Sequence", other.getClass.getSimpleName))
        }
      )
    }

    def reverse: MigrationAction = TransformElements(at, inverseTransform(transform))
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
      def transformMapKeys(map: DynamicValue.Map): Either[MigrationError, DynamicValue] = {
        val transformedResults = map.entries.map { case (key, v) =>
          transform.evalDynamic(key) match {
            case Right(results) if results.nonEmpty => Right((results.head, v))
            case Right(_)                           => Left(MigrationError.EvaluationError(at, "Transform expression returned empty sequence"))
            case Left(err)                          => Left(MigrationError.EvaluationError(at, s"Failed to transform key: ${err.getMessage}"))
          }
        }
        transformedResults.collectFirst { case Left(err) => err }
          .toLeft(DynamicValue.Map(transformedResults.collect { case Right(entry) => entry }))
      }

      NavigationHelpers.transformAt(
        value,
        at,
        {
          case map: DynamicValue.Map => transformMapKeys(map)
          case other                 => Left(MigrationError.InvalidStructure(at, "Map", other.getClass.getSimpleName))
        }
      )
    }

    def reverse: MigrationAction = TransformKeys(at, inverseTransform(transform))
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
      def transformMapValues(map: DynamicValue.Map): Either[MigrationError, DynamicValue] = {
        val transformedResults = map.entries.map { case (key, v) =>
          transform.evalDynamic(v) match {
            case Right(results) if results.nonEmpty => Right((key, results.head))
            case Right(_)                           => Left(MigrationError.EvaluationError(at, "Transform expression returned empty sequence"))
            case Left(err)                          => Left(MigrationError.EvaluationError(at, s"Failed to transform value: ${err.getMessage}"))
          }
        }
        transformedResults.collectFirst { case Left(err) => err }
          .toLeft(DynamicValue.Map(transformedResults.collect { case Right(entry) => entry }))
      }

      NavigationHelpers.transformAt(
        value,
        at,
        {
          case map: DynamicValue.Map => transformMapValues(map)
          case other                 => Left(MigrationError.InvalidStructure(at, "Map", other.getClass.getSimpleName))
        }
      )
    }

    def reverse: MigrationAction = TransformValues(at, inverseTransform(transform))
  }

  /**
   * Renames a variant case. Changes the case name in a DynamicValue.Variant.
   * Example: Variant("PayPal", ...) with from="PayPal", to="PaypalPayment" ->
   * Variant("PaypalPayment", ...)
   *
   * Reverse: RenameCase with flipped from/to
   */
  final case class RenameCase(
    at: DynamicOptic,
    from: String,
    to: String
  ) extends MigrationAction {
    def execute(value: DynamicValue): Either[MigrationError, DynamicValue] =
      NavigationHelpers.transformAt(
        value,
        at,
        {
          case variant: DynamicValue.Variant =>
            if (variant.caseNameValue == from) Right(DynamicValue.Variant(to, variant.value))
            else Right(variant)
          case other => Left(MigrationError.InvalidStructure(at, "Variant", other.getClass.getSimpleName))
        }
      )

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
    def execute(value: DynamicValue): Either[MigrationError, DynamicValue] =
      NavigationHelpers.transformAt(
        value,
        at,
        {
          case variant: DynamicValue.Variant =>
            if (variant.caseNameValue == caseName) {
              DynamicMigration(actions).apply(variant.value).map(DynamicValue.Variant(caseName, _))
            } else Right(variant)
          case other => Left(MigrationError.InvalidStructure(at, "Variant", other.getClass.getSimpleName))
        }
      )

    def reverse: MigrationAction = TransformCase(at, caseName, actions.reverse.map(_.reverse))
  }
}
