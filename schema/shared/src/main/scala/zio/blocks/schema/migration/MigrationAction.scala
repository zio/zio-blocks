package zio.blocks.schema.migration

import zio.blocks.chunk.Chunk
import zio.blocks.schema.{
  DynamicOptic,
  DynamicSchemaExpr,
  DynamicValue,
  PrimitiveConverter,
  PrimitiveValue,
  Reflect,
  Schema
}
import zio.blocks.schema.binding._
import zio.blocks.schema.binding.RegisterOffset.RegisterOffset
import zio.blocks.typeid.TypeId

/**
 * Represents an atomic migration operation. All actions operate at a specific
 * path and are reversible.
 */
private[migration] sealed trait MigrationAction {

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

  // Helper to compute the inverse of a DynamicSchemaExpr for reversible transformations.
  // Delegates to DynamicSchemaExpr.inverse.
  private def inverseTransform(transform: DynamicSchemaExpr): Option[DynamicSchemaExpr] =
    transform.inverse

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
  private[migration] final case class AddField(
    at: DynamicOptic,
    default: DynamicSchemaExpr
  ) extends MigrationAction {
    def execute(value: DynamicValue): Either[MigrationError, DynamicValue] = {
      // Evaluate default expression first
      val defaultValue = default.eval(value) match {
        case Right(seq) if seq.nonEmpty => seq.head
        case Right(_)                   => return Left(MigrationError.EvaluationError(at, "Default expression returned empty sequence"))
        case Left(err)                  =>
          return Left(MigrationError.EvaluationError(at, s"Failed to evaluate default: $err"))
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
  private[migration] final case class DropField(
    at: DynamicOptic,
    defaultForReverse: DynamicSchemaExpr
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
  private[migration] final case class Rename(
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
   * Transforms a field value using a DynamicSchemaExpr. Can use any existing
   * expression (Arithmetic, StringConcat, etc.) Reverse: TransformValue with
   * inverse expression (best-effort)
   */
  private[migration] final case class TransformValue(
    at: DynamicOptic,
    transform: DynamicSchemaExpr
  ) extends MigrationAction {
    def execute(value: DynamicValue): Either[MigrationError, DynamicValue] = {
      if (at.nodes.isEmpty) {
        return Left(MigrationError.InvalidStructure(at, "non-empty path", "empty path"))
      }

      NavigationHelpers.transformAt(
        value,
        at,
        fieldValue =>
          transform.eval(fieldValue) match {
            case Right(seq) if seq.nonEmpty => Right(seq.head)
            case Right(_)                   => Left(MigrationError.EvaluationError(at, "Transform expression returned empty sequence"))
            case Left(err)                  =>
              Left(MigrationError.EvaluationError(at, s"Failed to evaluate transform: $err"))
          }
      )
    }

    def reverse: MigrationAction = inverseTransform(transform) match {
      case Some(inv) => TransformValue(at, inv)
      case None      => Irreversible(at, s"Cannot reverse transform: ${transform.getClass.getSimpleName}")
    }
  }

  /**
   * Changes the type of a field using a PrimitiveConverter. Reverse: ChangeType
   * with reverse converter
   */
  private[migration] final case class ChangeType(
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
  private[migration] final case class Mandate(
    at: DynamicOptic,
    default: DynamicSchemaExpr
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
              default.eval(rootValue) match {
                case Right(seq) if seq.nonEmpty => Right(seq.head)
                case Right(_)                   => Left(MigrationError.EvaluationError(at, "Default expression returned empty sequence"))
                case Left(err)                  =>
                  Left(MigrationError.EvaluationError(at, s"Failed to evaluate default: $err"))
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
  private[migration] final case class Optionalize(
    at: DynamicOptic,
    defaultForReverse: DynamicSchemaExpr
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
  private[migration] final case class Join(
    at: DynamicOptic,
    sourcePaths: Vector[DynamicOptic],
    combiner: DynamicSchemaExpr
  ) extends MigrationAction {
    def execute(value: DynamicValue): Either[MigrationError, DynamicValue] = {
      // Validate sibling constraint for nested paths
      if (at.nodes.length > 1) {
        val targetParent = at.nodes.dropRight(1)
        val invalidPaths = sourcePaths.filterNot { sourcePath =>
          sourcePath.nodes.length == at.nodes.length && sourcePath.nodes.dropRight(1) == targetParent
        }
        if (invalidPaths.nonEmpty) {
          return Left(MigrationError.CrossPathJoinNotSupported(at, invalidPaths))
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
              combiner.eval(DynamicValue.Record(tempFields)) match {
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
                  Left(MigrationError.EvaluationError(at, s"Failed to evaluate combiner: $err"))
              }
            }
          }
      )
    }

    def reverse: MigrationAction = combiner match {
      case DynamicSchemaExpr.StringConcat(
            _,
            DynamicSchemaExpr.StringConcat(DynamicSchemaExpr.Literal(delimiterDv), _)
          ) =>
        val delimiter = delimiterDv match {
          case DynamicValue.Primitive(PrimitiveValue.String(s)) => s
          case _                                                =>
            return Irreversible(at, "Cannot reverse Join: delimiter is not a String literal")
        }
        val splitter = DynamicSchemaExpr.StringSplit(DynamicSchemaExpr.Dynamic(DynamicOptic.root), delimiter)
        Split(at, sourcePaths, splitter)
      case _ =>
        Irreversible(
          at,
          "Cannot reverse Join: unsupported combiner expression type. Only StringConcat with a delimiter is supported."
        )
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
  private[migration] final case class Split(
    at: DynamicOptic,
    targetPaths: Vector[DynamicOptic],
    splitter: DynamicSchemaExpr
  ) extends MigrationAction {
    def execute(value: DynamicValue): Either[MigrationError, DynamicValue] = {
      // Validate sibling constraint for nested paths
      if (at.nodes.length > 1) {
        val sourceParent = at.nodes.dropRight(1)
        val invalidPaths = targetPaths.filterNot { targetPath =>
          targetPath.nodes.length == at.nodes.length && targetPath.nodes.dropRight(1) == sourceParent
        }
        if (invalidPaths.nonEmpty) {
          return Left(MigrationError.CrossPathSplitNotSupported(at, invalidPaths))
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
            splitter.eval(sourceValue) match {
              case Left(err) =>
                Left(MigrationError.EvaluationError(at, s"Failed to evaluate splitter: $err"))
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

    def reverse: MigrationAction = splitter match {
      case DynamicSchemaExpr.StringSplit(_, delimiter) if targetPaths.nonEmpty =>
        // Build StringConcat chain: field0 + delimiter + field1 + delimiter + field2 + ...
        val lastIdx                     = targetPaths.length - 1
        val lastExpr: DynamicSchemaExpr =
          DynamicSchemaExpr.Dynamic(DynamicOptic.root.field(s"field$lastIdx"))
        val delimiterLit = DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String(delimiter)))
        val combiner     = (lastIdx - 1 to 0 by -1).foldLeft(lastExpr) { (acc, idx) =>
          DynamicSchemaExpr.StringConcat(
            DynamicSchemaExpr.Dynamic(DynamicOptic.root.field(s"field$idx")),
            DynamicSchemaExpr.StringConcat(delimiterLit, acc)
          )
        }
        Join(at, targetPaths, combiner)
      case _ =>
        Irreversible(at, "Cannot reverse Split: unsupported splitter expression type. Only StringSplit is supported.")
    }
  }

  /**
   * Transforms all elements in a Sequence by applying a SchemaExpr to each
   * element independently. Example: [1, 2, 3] with (+1) -> [2, 3, 4]
   *
   * Reverse: TransformElements (best-effort, needs inverse expression)
   */
  private[migration] final case class TransformElements(
    at: DynamicOptic,
    transform: DynamicSchemaExpr
  ) extends MigrationAction {
    def execute(value: DynamicValue): Either[MigrationError, DynamicValue] = {
      def transformSequence(seq: DynamicValue.Sequence): Either[MigrationError, DynamicValue] = {
        val transformedResults = seq.elements.map { element =>
          transform.eval(element) match {
            case Right(results) if results.nonEmpty => Right(results.head)
            case Right(_)                           => Left(MigrationError.EvaluationError(at, "Transform expression returned empty sequence"))
            case Left(err)                          =>
              Left(MigrationError.EvaluationError(at, s"Failed to transform element: $err"))
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

    def reverse: MigrationAction = inverseTransform(transform) match {
      case Some(inv) => TransformElements(at, inv)
      case None      => Irreversible(at, s"Cannot reverse element transform: ${transform.getClass.getSimpleName}")
    }
  }

  /**
   * Transforms all keys in a Map by applying a SchemaExpr to each key
   * independently. Example: Map("a" -> 1) with uppercase -> Map("A" -> 1)
   *
   * Reverse: TransformKeys (best-effort, needs inverse expression)
   */
  private[migration] final case class TransformKeys(
    at: DynamicOptic,
    transform: DynamicSchemaExpr
  ) extends MigrationAction {
    def execute(value: DynamicValue): Either[MigrationError, DynamicValue] = {
      def transformMapKeys(map: DynamicValue.Map): Either[MigrationError, DynamicValue] = {
        val transformedResults = map.entries.map { case (key, v) =>
          transform.eval(key) match {
            case Right(results) if results.nonEmpty => Right((results.head, v))
            case Right(_)                           => Left(MigrationError.EvaluationError(at, "Transform expression returned empty sequence"))
            case Left(err)                          => Left(MigrationError.EvaluationError(at, s"Failed to transform key: $err"))
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

    def reverse: MigrationAction = inverseTransform(transform) match {
      case Some(inv) => TransformKeys(at, inv)
      case None      => Irreversible(at, s"Cannot reverse key transform: ${transform.getClass.getSimpleName}")
    }
  }

  /**
   * Transforms all values in a Map by applying a SchemaExpr to each value
   * independently. Example: Map("a" -> 1) with (+1) -> Map("a" -> 2)
   *
   * Reverse: TransformValues (best-effort, needs inverse expression)
   */
  private[migration] final case class TransformValues(
    at: DynamicOptic,
    transform: DynamicSchemaExpr
  ) extends MigrationAction {
    def execute(value: DynamicValue): Either[MigrationError, DynamicValue] = {
      def transformMapValues(map: DynamicValue.Map): Either[MigrationError, DynamicValue] = {
        val transformedResults = map.entries.map { case (key, v) =>
          transform.eval(v) match {
            case Right(results) if results.nonEmpty => Right((key, results.head))
            case Right(_)                           => Left(MigrationError.EvaluationError(at, "Transform expression returned empty sequence"))
            case Left(err)                          => Left(MigrationError.EvaluationError(at, s"Failed to transform value: $err"))
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

    def reverse: MigrationAction = inverseTransform(transform) match {
      case Some(inv) => TransformValues(at, inv)
      case None      => Irreversible(at, s"Cannot reverse value transform: ${transform.getClass.getSimpleName}")
    }
  }

  /**
   * Renames a variant case. Changes the case name in a DynamicValue.Variant.
   * Example: Variant("PayPal", ...) with from="PayPal", to="PaypalPayment" ->
   * Variant("PaypalPayment", ...)
   *
   * Reverse: RenameCase with flipped from/to
   */
  private[migration] final case class RenameCase(
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
  private[migration] final case class TransformCase(
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

  /**
   * A marker action representing an operation that cannot be reversed. Always
   * fails at execution time with a clear error message.
   */
  private[migration] final case class Irreversible(
    at: DynamicOptic,
    reason: String
  ) extends MigrationAction {
    def execute(value: DynamicValue): Either[MigrationError, DynamicValue] =
      Left(MigrationError.IrreversibleOperation(at, reason))

    def reverse: MigrationAction = this
  }

  // --- Manual Schema derivations for Scala 2 ---

  private def obj2Schema[A](id: TypeId[A], f1: String, s1: => Schema[_], f2: String, s2: => Schema[_])(
    mk: (AnyRef, AnyRef) => A,
    get: A => (AnyRef, AnyRef)
  ): Schema[A] = new Schema(
    new Reflect.Record[Binding, A](
      fields = Chunk(
        Reflect.Deferred(() => s1.reflect.asInstanceOf[Reflect.Bound[Any]]).asTerm(f1),
        Reflect.Deferred(() => s2.reflect.asInstanceOf[Reflect.Bound[Any]]).asTerm(f2)
      ),
      typeId = id,
      recordBinding = new Binding.Record(
        constructor = new Constructor[A] {
          def usedRegisters: RegisterOffset                       = RegisterOffset(objects = 2)
          def construct(in: Registers, offset: RegisterOffset): A =
            mk(in.getObject(offset), in.getObject(RegisterOffset.incrementObjects(offset)))
        },
        deconstructor = new Deconstructor[A] {
          def usedRegisters: RegisterOffset                                    = RegisterOffset(objects = 2)
          def deconstruct(out: Registers, offset: RegisterOffset, in: A): Unit = {
            val (a, b) = get(in); out.setObject(offset, a); out.setObject(RegisterOffset.incrementObjects(offset), b)
          }
        }
      ),
      modifiers = Chunk.empty
    )
  )

  private def obj3Schema[A](
    id: TypeId[A],
    f1: String,
    s1: => Schema[_],
    f2: String,
    s2: => Schema[_],
    f3: String,
    s3: => Schema[_]
  )(
    mk: (AnyRef, AnyRef, AnyRef) => A,
    get: A => (AnyRef, AnyRef, AnyRef)
  ): Schema[A] = new Schema(
    new Reflect.Record[Binding, A](
      fields = Chunk(
        Reflect.Deferred(() => s1.reflect.asInstanceOf[Reflect.Bound[Any]]).asTerm(f1),
        Reflect.Deferred(() => s2.reflect.asInstanceOf[Reflect.Bound[Any]]).asTerm(f2),
        Reflect.Deferred(() => s3.reflect.asInstanceOf[Reflect.Bound[Any]]).asTerm(f3)
      ),
      typeId = id,
      recordBinding = new Binding.Record(
        constructor = new Constructor[A] {
          def usedRegisters: RegisterOffset                       = RegisterOffset(objects = 3)
          def construct(in: Registers, offset: RegisterOffset): A = {
            val off1 = RegisterOffset.incrementObjects(offset)
            mk(in.getObject(offset), in.getObject(off1), in.getObject(RegisterOffset.incrementObjects(off1)))
          }
        },
        deconstructor = new Deconstructor[A] {
          def usedRegisters: RegisterOffset                                    = RegisterOffset(objects = 3)
          def deconstruct(out: Registers, offset: RegisterOffset, in: A): Unit = {
            val (a, b, c) = get(in); val off1 = RegisterOffset.incrementObjects(offset)
            out.setObject(offset, a); out.setObject(off1, b); out.setObject(RegisterOffset.incrementObjects(off1), c)
          }
        }
      ),
      modifiers = Chunk.empty
    )
  )

  private[migration] implicit lazy val addFieldSchema: Schema[AddField] =
    obj2Schema(TypeId.of[AddField], "at", Schema[DynamicOptic], "default", DynamicSchemaExpr.schema)(
      (a, b) => AddField(a.asInstanceOf[DynamicOptic], b.asInstanceOf[DynamicSchemaExpr]),
      a => (a.at, a.default)
    )

  private[migration] implicit lazy val dropFieldSchema: Schema[DropField] =
    obj2Schema(TypeId.of[DropField], "at", Schema[DynamicOptic], "defaultForReverse", DynamicSchemaExpr.schema)(
      (a, b) => DropField(a.asInstanceOf[DynamicOptic], b.asInstanceOf[DynamicSchemaExpr]),
      a => (a.at, a.defaultForReverse)
    )

  private[migration] implicit lazy val renameSchema: Schema[Rename] =
    obj2Schema(TypeId.of[Rename], "at", Schema[DynamicOptic], "to", Schema.string)(
      (a, b) => Rename(a.asInstanceOf[DynamicOptic], b.asInstanceOf[String]),
      a => (a.at, a.to)
    )

  private[migration] implicit lazy val transformValueSchema: Schema[TransformValue] =
    obj2Schema(TypeId.of[TransformValue], "at", Schema[DynamicOptic], "transform", DynamicSchemaExpr.schema)(
      (a, b) => TransformValue(a.asInstanceOf[DynamicOptic], b.asInstanceOf[DynamicSchemaExpr]),
      a => (a.at, a.transform)
    )

  private[migration] implicit lazy val changeTypeSchema: Schema[ChangeType] =
    obj2Schema(TypeId.of[ChangeType], "at", Schema[DynamicOptic], "converter", PrimitiveConverter.schema)(
      (a, b) => ChangeType(a.asInstanceOf[DynamicOptic], b.asInstanceOf[PrimitiveConverter]),
      a => (a.at, a.converter)
    )

  private[migration] implicit lazy val mandateSchema: Schema[Mandate] =
    obj2Schema(TypeId.of[Mandate], "at", Schema[DynamicOptic], "default", DynamicSchemaExpr.schema)(
      (a, b) => Mandate(a.asInstanceOf[DynamicOptic], b.asInstanceOf[DynamicSchemaExpr]),
      a => (a.at, a.default)
    )

  private[migration] implicit lazy val optionalizeSchema: Schema[Optionalize] =
    obj2Schema(TypeId.of[Optionalize], "at", Schema[DynamicOptic], "defaultForReverse", DynamicSchemaExpr.schema)(
      (a, b) => Optionalize(a.asInstanceOf[DynamicOptic], b.asInstanceOf[DynamicSchemaExpr]),
      a => (a.at, a.defaultForReverse)
    )

  private[migration] implicit lazy val joinSchema: Schema[Join] =
    obj3Schema(
      TypeId.of[Join],
      "at",
      Schema[DynamicOptic],
      "sourcePaths",
      Schema[Vector[DynamicOptic]],
      "combiner",
      DynamicSchemaExpr.schema
    )(
      (a, b, c) =>
        Join(a.asInstanceOf[DynamicOptic], b.asInstanceOf[Vector[DynamicOptic]], c.asInstanceOf[DynamicSchemaExpr]),
      a => (a.at, a.sourcePaths, a.combiner)
    )

  private[migration] implicit lazy val splitSchema: Schema[Split] =
    obj3Schema(
      TypeId.of[Split],
      "at",
      Schema[DynamicOptic],
      "targetPaths",
      Schema[Vector[DynamicOptic]],
      "splitter",
      DynamicSchemaExpr.schema
    )(
      (a, b, c) =>
        Split(a.asInstanceOf[DynamicOptic], b.asInstanceOf[Vector[DynamicOptic]], c.asInstanceOf[DynamicSchemaExpr]),
      a => (a.at, a.targetPaths, a.splitter)
    )

  private[migration] implicit lazy val transformElementsSchema: Schema[TransformElements] =
    obj2Schema(TypeId.of[TransformElements], "at", Schema[DynamicOptic], "transform", DynamicSchemaExpr.schema)(
      (a, b) => TransformElements(a.asInstanceOf[DynamicOptic], b.asInstanceOf[DynamicSchemaExpr]),
      a => (a.at, a.transform)
    )

  private[migration] implicit lazy val transformKeysSchema: Schema[TransformKeys] =
    obj2Schema(TypeId.of[TransformKeys], "at", Schema[DynamicOptic], "transform", DynamicSchemaExpr.schema)(
      (a, b) => TransformKeys(a.asInstanceOf[DynamicOptic], b.asInstanceOf[DynamicSchemaExpr]),
      a => (a.at, a.transform)
    )

  private[migration] implicit lazy val transformValuesSchema: Schema[TransformValues] =
    obj2Schema(TypeId.of[TransformValues], "at", Schema[DynamicOptic], "transform", DynamicSchemaExpr.schema)(
      (a, b) => TransformValues(a.asInstanceOf[DynamicOptic], b.asInstanceOf[DynamicSchemaExpr]),
      a => (a.at, a.transform)
    )

  private[migration] implicit lazy val renameCaseSchema: Schema[RenameCase] =
    obj3Schema(TypeId.of[RenameCase], "at", Schema[DynamicOptic], "from", Schema.string, "to", Schema.string)(
      (a, b, c) => RenameCase(a.asInstanceOf[DynamicOptic], b.asInstanceOf[String], c.asInstanceOf[String]),
      a => (a.at, a.from, a.to)
    )

  private[migration] implicit lazy val transformCaseSchema: Schema[TransformCase] =
    obj3Schema(
      TypeId.of[TransformCase],
      "at",
      Schema[DynamicOptic],
      "caseName",
      Schema.string,
      "actions",
      Schema[Vector[MigrationAction]]
    )(
      (a, b, c) =>
        TransformCase(a.asInstanceOf[DynamicOptic], b.asInstanceOf[String], c.asInstanceOf[Vector[MigrationAction]]),
      a => (a.at, a.caseName, a.actions)
    )

  private[migration] implicit lazy val irreversibleSchema: Schema[Irreversible] =
    obj2Schema(TypeId.of[Irreversible], "at", Schema[DynamicOptic], "reason", Schema.string)(
      (a, b) => Irreversible(a.asInstanceOf[DynamicOptic], b.asInstanceOf[String]),
      a => (a.at, a.reason)
    )

  private[migration] implicit lazy val schema: Schema[MigrationAction] = new Schema(
    new Reflect.Variant[Binding, MigrationAction](
      cases = Chunk(
        addFieldSchema.reflect.asTerm("AddField"),
        dropFieldSchema.reflect.asTerm("DropField"),
        renameSchema.reflect.asTerm("Rename"),
        transformValueSchema.reflect.asTerm("TransformValue"),
        changeTypeSchema.reflect.asTerm("ChangeType"),
        mandateSchema.reflect.asTerm("Mandate"),
        optionalizeSchema.reflect.asTerm("Optionalize"),
        joinSchema.reflect.asTerm("Join"),
        splitSchema.reflect.asTerm("Split"),
        transformElementsSchema.reflect.asTerm("TransformElements"),
        transformKeysSchema.reflect.asTerm("TransformKeys"),
        transformValuesSchema.reflect.asTerm("TransformValues"),
        renameCaseSchema.reflect.asTerm("RenameCase"),
        transformCaseSchema.reflect.asTerm("TransformCase"),
        irreversibleSchema.reflect.asTerm("Irreversible")
      ),
      typeId = TypeId.of[MigrationAction],
      variantBinding = new Binding.Variant(
        discriminator = new Discriminator[MigrationAction] {
          def discriminate(a: MigrationAction): Int = a match {
            case _: AddField        => 0; case _: DropField         => 1; case _: Rename        => 2; case _: TransformValue => 3
            case _: ChangeType      => 4; case _: Mandate           => 5; case _: Optionalize   => 6; case _: Join           => 7
            case _: Split           => 8; case _: TransformElements => 9; case _: TransformKeys => 10;
            case _: TransformValues => 11
            case _: RenameCase      => 12; case _: TransformCase    => 13; case _: Irreversible => 14
          }
        },
        matchers = Matchers(
          new Matcher[AddField] {
            def downcastOrNull(a: Any): AddField = a match {
              case x: AddField => x; case _ => null.asInstanceOf[AddField]
            }
          },
          new Matcher[DropField] {
            def downcastOrNull(a: Any): DropField = a match {
              case x: DropField => x; case _ => null.asInstanceOf[DropField]
            }
          },
          new Matcher[Rename] {
            def downcastOrNull(a: Any): Rename = a match { case x: Rename => x; case _ => null.asInstanceOf[Rename] }
          },
          new Matcher[TransformValue] {
            def downcastOrNull(a: Any): TransformValue = a match {
              case x: TransformValue => x; case _ => null.asInstanceOf[TransformValue]
            }
          },
          new Matcher[ChangeType] {
            def downcastOrNull(a: Any): ChangeType = a match {
              case x: ChangeType => x; case _ => null.asInstanceOf[ChangeType]
            }
          },
          new Matcher[Mandate] {
            def downcastOrNull(a: Any): Mandate = a match { case x: Mandate => x; case _ => null.asInstanceOf[Mandate] }
          },
          new Matcher[Optionalize] {
            def downcastOrNull(a: Any): Optionalize = a match {
              case x: Optionalize => x; case _ => null.asInstanceOf[Optionalize]
            }
          },
          new Matcher[Join] {
            def downcastOrNull(a: Any): Join = a match { case x: Join => x; case _ => null.asInstanceOf[Join] }
          },
          new Matcher[Split] {
            def downcastOrNull(a: Any): Split = a match { case x: Split => x; case _ => null.asInstanceOf[Split] }
          },
          new Matcher[TransformElements] {
            def downcastOrNull(a: Any): TransformElements = a match {
              case x: TransformElements => x; case _ => null.asInstanceOf[TransformElements]
            }
          },
          new Matcher[TransformKeys] {
            def downcastOrNull(a: Any): TransformKeys = a match {
              case x: TransformKeys => x; case _ => null.asInstanceOf[TransformKeys]
            }
          },
          new Matcher[TransformValues] {
            def downcastOrNull(a: Any): TransformValues = a match {
              case x: TransformValues => x; case _ => null.asInstanceOf[TransformValues]
            }
          },
          new Matcher[RenameCase] {
            def downcastOrNull(a: Any): RenameCase = a match {
              case x: RenameCase => x; case _ => null.asInstanceOf[RenameCase]
            }
          },
          new Matcher[TransformCase] {
            def downcastOrNull(a: Any): TransformCase = a match {
              case x: TransformCase => x; case _ => null.asInstanceOf[TransformCase]
            }
          },
          new Matcher[Irreversible] {
            def downcastOrNull(a: Any): Irreversible = a match {
              case x: Irreversible => x; case _ => null.asInstanceOf[Irreversible]
            }
          }
        )
      ),
      modifiers = Chunk.empty
    )
  )
}
