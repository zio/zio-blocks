package zio.blocks.schema.migration

import zio.blocks.chunk.Chunk
import zio.blocks.schema.{DynamicOptic, DynamicValue}

/**
 * A MigrationAction represents a single atomic transformation in a migration.
 *
 * All actions are path-based via DynamicOptic and use Resolved expressions for
 * full serializability. This enables migrations to be stored in registries,
 * transmitted over networks, and applied without reflection.
 */
sealed trait MigrationAction {
  def at: DynamicOptic
  def reverse: MigrationAction

  /**
   * Execute this action on a DynamicValue.
   *
   * This method transforms a DynamicValue according to this action's semantics.
   * Execution is delegated to the DynamicMigrationInterpreter which handles all
   * action types uniformly.
   *
   * @param value
   *   the input DynamicValue to transform
   * @return
   *   Either a MigrationError on failure, or the transformed DynamicValue
   */
  def execute(value: DynamicValue): Either[MigrationError, DynamicValue] =
    DynamicMigrationInterpreter.applyAction(this, value)

  /**
   * Create a copy of this action with the path prefixed by the given prefix.
   * Used for nested migrations where actions need to be scoped to a sub-path.
   *
   * @param prefix
   *   the DynamicOptic prefix to prepend to this action's path
   * @return
   *   a new action with the prefixed path
   */
  def prefixPath(prefix: DynamicOptic): MigrationAction
}

object MigrationAction {

  // Record actions

  /**
   * Add a new field with a default value.
   *
   * @param at
   *   path to the record containing the new field
   * @param fieldName
   *   name of the field to add
   * @param default
   *   expression providing the default value
   */
  final case class AddField(
    at: DynamicOptic,
    fieldName: String,
    default: Resolved
  ) extends MigrationAction {
    def reverse: MigrationAction                          = DropField(at, fieldName, default)
    def prefixPath(prefix: DynamicOptic): MigrationAction = copy(at = prefix(at))
  }

  /**
   * Drop a field from a record.
   *
   * @param at
   *   path to the record
   * @param fieldName
   *   name of the field to drop
   * @param defaultForReverse
   *   default value for reverse migration
   */
  final case class DropField(
    at: DynamicOptic,
    fieldName: String,
    defaultForReverse: Resolved
  ) extends MigrationAction {
    def reverse: MigrationAction                          = AddField(at, fieldName, defaultForReverse)
    def prefixPath(prefix: DynamicOptic): MigrationAction = copy(at = prefix(at))
  }

  /**
   * Rename a field in a record.
   *
   * @param at
   *   path to the record
   * @param from
   *   original field name
   * @param to
   *   new field name
   */
  final case class Rename(
    at: DynamicOptic,
    from: String,
    to: String
  ) extends MigrationAction {
    def reverse: MigrationAction                          = Rename(at, to, from)
    def prefixPath(prefix: DynamicOptic): MigrationAction = copy(at = prefix(at))
  }

  /**
   * Transform a field's value using forward and reverse expressions.
   *
   * @param at
   *   path to the record
   * @param fieldName
   *   name of the field to transform
   * @param transform
   *   forward transformation
   * @param reverseTransform
   *   reverse transformation
   */
  final case class TransformValue(
    at: DynamicOptic,
    fieldName: String,
    transform: Resolved,
    reverseTransform: Resolved
  ) extends MigrationAction {
    def reverse: MigrationAction                          = TransformValue(at, fieldName, reverseTransform, transform)
    def prefixPath(prefix: DynamicOptic): MigrationAction = copy(at = prefix(at))
  }

  /**
   * Make an optional field mandatory.
   *
   * @param at
   *   path to the record
   * @param fieldName
   *   name of the optional field
   * @param default
   *   value to use when field is None
   */
  final case class Mandate(
    at: DynamicOptic,
    fieldName: String,
    default: Resolved
  ) extends MigrationAction {
    def reverse: MigrationAction                          = Optionalize(at, fieldName)
    def prefixPath(prefix: DynamicOptic): MigrationAction = copy(at = prefix(at))
  }

  /**
   * Make a mandatory field optional.
   *
   * @param at
   *   path to the record
   * @param fieldName
   *   name of the field to optionalize
   */
  final case class Optionalize(
    at: DynamicOptic,
    fieldName: String
  ) extends MigrationAction {
    def reverse: MigrationAction                          = Mandate(at, fieldName, Resolved.Fail("Cannot reverse optionalize without default"))
    def prefixPath(prefix: DynamicOptic): MigrationAction = copy(at = prefix(at))
  }

  /**
   * Change a field's primitive type.
   *
   * @param at
   *   path to the record
   * @param fieldName
   *   name of the field
   * @param converter
   *   forward type conversion
   * @param reverseConverter
   *   reverse type conversion
   */
  final case class ChangeType(
    at: DynamicOptic,
    fieldName: String,
    converter: Resolved,
    reverseConverter: Resolved
  ) extends MigrationAction {
    def reverse: MigrationAction                          = ChangeType(at, fieldName, reverseConverter, converter)
    def prefixPath(prefix: DynamicOptic): MigrationAction = copy(at = prefix(at))
  }

  // Enum actions

  /**
   * Rename a case in an enum/variant type.
   *
   * @param at
   *   path to the enum
   * @param from
   *   original case name
   * @param to
   *   new case name
   */
  final case class RenameCase(
    at: DynamicOptic,
    from: String,
    to: String
  ) extends MigrationAction {
    def reverse: MigrationAction                          = RenameCase(at, to, from)
    def prefixPath(prefix: DynamicOptic): MigrationAction = copy(at = prefix(at))
  }

  /**
   * Transform the contents of a specific enum case.
   *
   * @param at
   *   path to the enum
   * @param caseName
   *   name of the case to transform
   * @param caseActions
   *   actions to apply to the case value
   */
  final case class TransformCase(
    at: DynamicOptic,
    caseName: String,
    caseActions: Chunk[MigrationAction]
  ) extends MigrationAction {
    def reverse: MigrationAction                          = TransformCase(at, caseName, caseActions.map(_.reverse).reverse)
    def prefixPath(prefix: DynamicOptic): MigrationAction =
      copy(at = prefix(at), caseActions = caseActions.map(_.prefixPath(prefix)))
  }

  // Collection actions

  /**
   * Transform each element in a sequence.
   *
   * @param at
   *   path to the sequence
   * @param elementTransform
   *   forward transformation
   * @param reverseTransform
   *   reverse transformation
   */
  final case class TransformElements(
    at: DynamicOptic,
    elementTransform: Resolved,
    reverseTransform: Resolved
  ) extends MigrationAction {
    def reverse: MigrationAction                          = TransformElements(at, reverseTransform, elementTransform)
    def prefixPath(prefix: DynamicOptic): MigrationAction = copy(at = prefix(at))
  }

  /**
   * Transform map keys.
   *
   * @param at
   *   path to the map
   * @param keyTransform
   *   forward key transformation
   * @param reverseTransform
   *   reverse key transformation
   */
  final case class TransformKeys(
    at: DynamicOptic,
    keyTransform: Resolved,
    reverseTransform: Resolved
  ) extends MigrationAction {
    def reverse: MigrationAction                          = TransformKeys(at, reverseTransform, keyTransform)
    def prefixPath(prefix: DynamicOptic): MigrationAction = copy(at = prefix(at))
  }

  /**
   * Transform map values.
   *
   * @param at
   *   path to the map
   * @param valueTransform
   *   forward value transformation
   * @param reverseTransform
   *   reverse value transformation
   */
  final case class TransformValues(
    at: DynamicOptic,
    valueTransform: Resolved,
    reverseTransform: Resolved
  ) extends MigrationAction {
    def reverse: MigrationAction                          = TransformValues(at, reverseTransform, valueTransform)
    def prefixPath(prefix: DynamicOptic): MigrationAction = copy(at = prefix(at))
  }

  // Field combination actions

  /**
   * Join multiple source fields into a single target field.
   *
   * This action combines multiple primitive fields into a single field using a
   * combiner expression. The result must be a primitive.
   *
   * @param at
   *   path to the record containing the target field
   * @param targetFieldName
   *   name of the new combined field
   * @param sourcePaths
   *   paths to the source fields to combine (relative to 'at')
   * @param combiner
   *   expression that combines the source values
   * @param splitter
   *   expression for reverse (to recreate source fields)
   */
  final case class Join(
    at: DynamicOptic,
    targetFieldName: String,
    sourcePaths: Chunk[DynamicOptic],
    combiner: Resolved,
    splitter: Resolved
  ) extends MigrationAction {
    def reverse: MigrationAction                          = Split(at, targetFieldName, sourcePaths, splitter, combiner)
    def prefixPath(prefix: DynamicOptic): MigrationAction =
      copy(at = prefix(at), sourcePaths = sourcePaths.map(prefix(_)))
  }

  /**
   * Split a single source field into multiple target fields.
   *
   * This action splits a primitive field into multiple fields using a splitter
   * expression. Source and results must be primitives.
   *
   * @param at
   *   path to the record containing the source field
   * @param sourceFieldName
   *   name of the field to split
   * @param targetPaths
   *   names of the new target fields
   * @param splitter
   *   expression that splits the source value
   * @param combiner
   *   expression for reverse (to recreate source field)
   */
  final case class Split(
    at: DynamicOptic,
    sourceFieldName: String,
    targetPaths: Chunk[DynamicOptic],
    splitter: Resolved,
    combiner: Resolved
  ) extends MigrationAction {
    def reverse: MigrationAction                          = Join(at, sourceFieldName, targetPaths, combiner, splitter)
    def prefixPath(prefix: DynamicOptic): MigrationAction =
      copy(at = prefix(at), targetPaths = targetPaths.map(prefix(_)))
  }

  // Schema instance for MigrationAction serialization
  // Uses DynamicValue representation for flexibility across all cases
  implicit lazy val schema: zio.blocks.schema.Schema[MigrationAction] = {
    import zio.blocks.schema.{DynamicValue, Schema, SchemaError}

    Schema[DynamicValue].transformOrFail(
      (dv: DynamicValue) => decodeMigrationAction(dv).left.map(s => SchemaError.validationFailed(s)),
      (ma: MigrationAction) => encodeMigrationAction(ma)
    )
  }

  private def encodeMigrationAction(action: MigrationAction): zio.blocks.schema.DynamicValue = {
    import zio.blocks.schema.{DynamicValue, PrimitiveValue}

    def opticDV(o: DynamicOptic): DynamicValue =
      DynamicValue.Primitive(PrimitiveValue.String(o.toScalaString))

    def resolvedDV(r: Resolved): DynamicValue =
      Resolved.schema.toDynamicValue(r)

    def actionsDV(actions: Chunk[MigrationAction]): DynamicValue =
      DynamicValue.Sequence(actions.map(encodeMigrationAction))

    action match {
      case AddField(at, fieldName, default) =>
        DynamicValue.Variant(
          "AddField",
          DynamicValue.Record(
            Chunk(
              "at"        -> opticDV(at),
              "fieldName" -> DynamicValue.Primitive(PrimitiveValue.String(fieldName)),
              "default"   -> resolvedDV(default)
            )
          )
        )

      case DropField(at, fieldName, defaultForReverse) =>
        DynamicValue.Variant(
          "DropField",
          DynamicValue.Record(
            Chunk(
              "at"                -> opticDV(at),
              "fieldName"         -> DynamicValue.Primitive(PrimitiveValue.String(fieldName)),
              "defaultForReverse" -> resolvedDV(defaultForReverse)
            )
          )
        )

      case Rename(at, from, to) =>
        DynamicValue.Variant(
          "Rename",
          DynamicValue.Record(
            Chunk(
              "at"   -> opticDV(at),
              "from" -> DynamicValue.Primitive(PrimitiveValue.String(from)),
              "to"   -> DynamicValue.Primitive(PrimitiveValue.String(to))
            )
          )
        )

      case TransformValue(at, fieldName, transform, reverseTransform) =>
        DynamicValue.Variant(
          "TransformValue",
          DynamicValue.Record(
            Chunk(
              "at"               -> opticDV(at),
              "fieldName"        -> DynamicValue.Primitive(PrimitiveValue.String(fieldName)),
              "transform"        -> resolvedDV(transform),
              "reverseTransform" -> resolvedDV(reverseTransform)
            )
          )
        )

      case Mandate(at, fieldName, default) =>
        DynamicValue.Variant(
          "Mandate",
          DynamicValue.Record(
            Chunk(
              "at"        -> opticDV(at),
              "fieldName" -> DynamicValue.Primitive(PrimitiveValue.String(fieldName)),
              "default"   -> resolvedDV(default)
            )
          )
        )

      case Optionalize(at, fieldName) =>
        DynamicValue.Variant(
          "Optionalize",
          DynamicValue.Record(
            Chunk(
              "at"        -> opticDV(at),
              "fieldName" -> DynamicValue.Primitive(PrimitiveValue.String(fieldName))
            )
          )
        )

      case ChangeType(at, fieldName, converter, reverseConverter) =>
        DynamicValue.Variant(
          "ChangeType",
          DynamicValue.Record(
            Chunk(
              "at"               -> opticDV(at),
              "fieldName"        -> DynamicValue.Primitive(PrimitiveValue.String(fieldName)),
              "converter"        -> resolvedDV(converter),
              "reverseConverter" -> resolvedDV(reverseConverter)
            )
          )
        )

      case RenameCase(at, from, to) =>
        DynamicValue.Variant(
          "RenameCase",
          DynamicValue.Record(
            Chunk(
              "at"   -> opticDV(at),
              "from" -> DynamicValue.Primitive(PrimitiveValue.String(from)),
              "to"   -> DynamicValue.Primitive(PrimitiveValue.String(to))
            )
          )
        )

      case TransformCase(at, caseName, caseActions) =>
        DynamicValue.Variant(
          "TransformCase",
          DynamicValue.Record(
            Chunk(
              "at"          -> opticDV(at),
              "caseName"    -> DynamicValue.Primitive(PrimitiveValue.String(caseName)),
              "caseActions" -> actionsDV(caseActions)
            )
          )
        )

      case TransformElements(at, elementTransform, reverseTransform) =>
        DynamicValue.Variant(
          "TransformElements",
          DynamicValue.Record(
            Chunk(
              "at"               -> opticDV(at),
              "elementTransform" -> resolvedDV(elementTransform),
              "reverseTransform" -> resolvedDV(reverseTransform)
            )
          )
        )

      case TransformKeys(at, keyTransform, reverseTransform) =>
        DynamicValue.Variant(
          "TransformKeys",
          DynamicValue.Record(
            Chunk(
              "at"               -> opticDV(at),
              "keyTransform"     -> resolvedDV(keyTransform),
              "reverseTransform" -> resolvedDV(reverseTransform)
            )
          )
        )

      case TransformValues(at, valueTransform, reverseTransform) =>
        DynamicValue.Variant(
          "TransformValues",
          DynamicValue.Record(
            Chunk(
              "at"               -> opticDV(at),
              "valueTransform"   -> resolvedDV(valueTransform),
              "reverseTransform" -> resolvedDV(reverseTransform)
            )
          )
        )

      case Join(at, targetFieldName, sourcePaths, combiner, splitter) =>
        DynamicValue.Variant(
          "Join",
          DynamicValue.Record(
            Chunk(
              "at"              -> opticDV(at),
              "targetFieldName" -> DynamicValue.Primitive(PrimitiveValue.String(targetFieldName)),
              "sourcePaths"     -> DynamicValue.Sequence(
                sourcePaths.map(opticDV)
              ),
              "combiner" -> resolvedDV(combiner),
              "splitter" -> resolvedDV(splitter)
            )
          )
        )

      case Split(at, sourceFieldName, targetPaths, splitter, combiner) =>
        DynamicValue.Variant(
          "Split",
          DynamicValue.Record(
            Chunk(
              "at"              -> opticDV(at),
              "sourceFieldName" -> DynamicValue.Primitive(PrimitiveValue.String(sourceFieldName)),
              "targetPaths"     -> DynamicValue.Sequence(
                targetPaths.map(opticDV)
              ),
              "splitter" -> resolvedDV(splitter),
              "combiner" -> resolvedDV(combiner)
            )
          )
        )
    }
  }

  @SuppressWarnings(Array("org.wartremover.warts.AsInstanceOf"))
  private def decodeMigrationAction(dv: zio.blocks.schema.DynamicValue): Either[String, MigrationAction] = {
    import zio.blocks.schema.{DynamicValue, PrimitiveValue}

    def parseOptic(dv: DynamicValue): Either[String, DynamicOptic] = dv match {
      case DynamicValue.Primitive(PrimitiveValue.String(s)) =>
        parsePath(s)
      case _ => Left("Expected string for optic path")
    }

    // Parse a DynamicOptic path string back to DynamicOptic
    // Handles format from toScalaString: .field, .when[Case], .each, .eachKey, .eachValue, .wrapped, (N)
    def parsePath(s: String): Either[String, DynamicOptic] = {
      import DynamicOptic.Node

      if (s == "." || s.isEmpty) {
        Right(DynamicOptic.root)
      } else {
        var nodes                 = Vector.empty[Node]
        var remaining             = s
        var error: Option[String] = None

        while (remaining.nonEmpty && error.isEmpty) {
          if (remaining.startsWith(".when[")) {
            // Case selection: .when[CaseName]
            val endIdx = remaining.indexOf(']')
            if (endIdx < 0) {
              error = Some(s"Malformed when clause in: $remaining")
            } else {
              val caseName = remaining.substring(6, endIdx)
              nodes = nodes :+ Node.Case(caseName)
              remaining = remaining.substring(endIdx + 1)
            }
          } else if (remaining.startsWith(".each")) {
            nodes = nodes :+ Node.Elements
            remaining = remaining.substring(5)
          } else if (remaining.startsWith(".eachKey")) {
            nodes = nodes :+ Node.MapKeys
            remaining = remaining.substring(8)
          } else if (remaining.startsWith(".eachValue")) {
            nodes = nodes :+ Node.MapValues
            remaining = remaining.substring(10)
          } else if (remaining.startsWith(".wrapped")) {
            nodes = nodes :+ Node.Wrapped
            remaining = remaining.substring(8)
          } else if (remaining.startsWith("(") && remaining.contains(")")) {
            // Index access: (N)
            val endIdx = remaining.indexOf(')')
            val idxStr = remaining.substring(1, endIdx)
            try {
              nodes = nodes :+ Node.AtIndex(idxStr.toInt)
              remaining = remaining.substring(endIdx + 1)
            } catch {
              case _: NumberFormatException =>
                error = Some(s"Invalid index: $idxStr")
            }
          } else if (remaining.startsWith(".")) {
            // Field access: .fieldName
            remaining = remaining.substring(1)
            // Find end of field name (next . or [ or ( or end)
            val endIdx   = remaining.indexWhere(c => c == '.' || c == '[' || c == '(')
            val fieldEnd = if (endIdx < 0) remaining.length else endIdx
            if (fieldEnd == 0) {
              error = Some(s"Empty field name in: $s")
            } else {
              val fieldName = remaining.substring(0, fieldEnd)
              nodes = nodes :+ Node.Field(fieldName)
              remaining = remaining.substring(fieldEnd)
            }
          } else {
            error = Some(s"Unexpected character in path: $remaining")
          }
        }

        error match {
          case Some(e) => Left(e)
          case None    => Right(new DynamicOptic(nodes.toIndexedSeq))
        }
      }
    }

    def parseString(dv: DynamicValue): Either[String, String] = dv match {
      case DynamicValue.Primitive(PrimitiveValue.String(s)) => Right(s)
      case _                                                => Left("Expected string")
    }

    def parseResolved(dv: DynamicValue): Either[String, Resolved] =
      Resolved.schema.fromDynamicValue(dv).left.map(_.message)

    @scala.annotation.unused
    def parseStringChunk(dv: DynamicValue): Either[String, Chunk[String]] = dv match {
      case DynamicValue.Sequence(elements) =>
        var result                = Chunk.empty[String]
        var error: Option[String] = None
        elements.foreach { elem =>
          if (error.isEmpty) {
            parseString(elem) match {
              case Right(s)  => result = result :+ s
              case Left(err) => error = Some(err)
            }
          }
        }
        error match {
          case Some(err) => Left(err)
          case None      => Right(result)
        }
      case _ => Left("Expected Sequence for string chunk")
    }

    def parseOpticChunk(dv: DynamicValue): Either[String, Chunk[DynamicOptic]] = dv match {
      case DynamicValue.Sequence(elements) =>
        var result                = Chunk.empty[DynamicOptic]
        var error: Option[String] = None
        elements.foreach { elem =>
          if (error.isEmpty) {
            parseOptic(elem) match {
              case Right(o)  => result = result :+ o
              case Left(err) => error = Some(err)
            }
          }
        }
        error match {
          case Some(err) => Left(err)
          case None      => Right(result)
        }
      case _ => Left("Expected Sequence for optic chunk")
    }

    def parseActions(dv: DynamicValue): Either[String, Chunk[MigrationAction]] = dv match {
      case DynamicValue.Sequence(elements) =>
        var result                = Chunk.empty[MigrationAction]
        var error: Option[String] = None
        elements.foreach { elem =>
          if (error.isEmpty) {
            decodeMigrationAction(elem) match {
              case Right(action) => result = result :+ action
              case Left(err)     => error = Some(err)
            }
          }
        }
        error match {
          case Some(e) => Left(e)
          case None    => Right(result)
        }
      case _ => Left("Expected sequence for actions")
    }

    def getField(fields: Chunk[(String, DynamicValue)], name: String): Either[String, DynamicValue] =
      fields.find(_._1 == name).map(_._2).toRight(s"Missing field: $name")

    dv match {
      case DynamicValue.Variant(caseName, DynamicValue.Record(fields)) =>
        caseName match {
          case "AddField" =>
            for {
              at        <- getField(fields, "at").flatMap(parseOptic)
              fieldName <- getField(fields, "fieldName").flatMap(parseString)
              default   <- getField(fields, "default").flatMap(parseResolved)
            } yield AddField(at, fieldName, default)

          case "DropField" =>
            for {
              at                <- getField(fields, "at").flatMap(parseOptic)
              fieldName         <- getField(fields, "fieldName").flatMap(parseString)
              defaultForReverse <- getField(fields, "defaultForReverse").flatMap(parseResolved)
            } yield DropField(at, fieldName, defaultForReverse)

          case "Rename" =>
            for {
              at   <- getField(fields, "at").flatMap(parseOptic)
              from <- getField(fields, "from").flatMap(parseString)
              to   <- getField(fields, "to").flatMap(parseString)
            } yield Rename(at, from, to)

          case "TransformValue" =>
            for {
              at               <- getField(fields, "at").flatMap(parseOptic)
              fieldName        <- getField(fields, "fieldName").flatMap(parseString)
              transform        <- getField(fields, "transform").flatMap(parseResolved)
              reverseTransform <- getField(fields, "reverseTransform").flatMap(parseResolved)
            } yield TransformValue(at, fieldName, transform, reverseTransform)

          case "Mandate" =>
            for {
              at        <- getField(fields, "at").flatMap(parseOptic)
              fieldName <- getField(fields, "fieldName").flatMap(parseString)
              default   <- getField(fields, "default").flatMap(parseResolved)
            } yield Mandate(at, fieldName, default)

          case "Optionalize" =>
            for {
              at        <- getField(fields, "at").flatMap(parseOptic)
              fieldName <- getField(fields, "fieldName").flatMap(parseString)
            } yield Optionalize(at, fieldName)

          case "ChangeType" =>
            for {
              at               <- getField(fields, "at").flatMap(parseOptic)
              fieldName        <- getField(fields, "fieldName").flatMap(parseString)
              converter        <- getField(fields, "converter").flatMap(parseResolved)
              reverseConverter <- getField(fields, "reverseConverter").flatMap(parseResolved)
            } yield ChangeType(at, fieldName, converter, reverseConverter)

          case "RenameCase" =>
            for {
              at   <- getField(fields, "at").flatMap(parseOptic)
              from <- getField(fields, "from").flatMap(parseString)
              to   <- getField(fields, "to").flatMap(parseString)
            } yield RenameCase(at, from, to)

          case "TransformCase" =>
            for {
              at          <- getField(fields, "at").flatMap(parseOptic)
              caseName    <- getField(fields, "caseName").flatMap(parseString)
              caseActions <- getField(fields, "caseActions").flatMap(parseActions)
            } yield TransformCase(at, caseName, caseActions)

          case "TransformElements" =>
            for {
              at               <- getField(fields, "at").flatMap(parseOptic)
              elementTransform <- getField(fields, "elementTransform").flatMap(parseResolved)
              reverseTransform <- getField(fields, "reverseTransform").flatMap(parseResolved)
            } yield TransformElements(at, elementTransform, reverseTransform)

          case "TransformKeys" =>
            for {
              at               <- getField(fields, "at").flatMap(parseOptic)
              keyTransform     <- getField(fields, "keyTransform").flatMap(parseResolved)
              reverseTransform <- getField(fields, "reverseTransform").flatMap(parseResolved)
            } yield TransformKeys(at, keyTransform, reverseTransform)

          case "TransformValues" =>
            for {
              at               <- getField(fields, "at").flatMap(parseOptic)
              valueTransform   <- getField(fields, "valueTransform").flatMap(parseResolved)
              reverseTransform <- getField(fields, "reverseTransform").flatMap(parseResolved)
            } yield TransformValues(at, valueTransform, reverseTransform)

          case "Join" =>
            for {
              at              <- getField(fields, "at").flatMap(parseOptic)
              targetFieldName <- getField(fields, "targetFieldName").flatMap(parseString)
              sourcePaths     <- getField(fields, "sourcePaths").flatMap(parseOpticChunk)
              combiner        <- getField(fields, "combiner").flatMap(parseResolved)
              splitter        <- getField(fields, "splitter").flatMap(parseResolved)
            } yield Join(at, targetFieldName, sourcePaths, combiner, splitter)

          case "Split" =>
            for {
              at              <- getField(fields, "at").flatMap(parseOptic)
              sourceFieldName <- getField(fields, "sourceFieldName").flatMap(parseString)
              targetPaths     <- getField(fields, "targetPaths").flatMap(parseOpticChunk)
              splitter        <- getField(fields, "splitter").flatMap(parseResolved)
              combiner        <- getField(fields, "combiner").flatMap(parseResolved)
            } yield Split(at, sourceFieldName, targetPaths, splitter, combiner)

          case unknown => Left(s"Unknown MigrationAction case: $unknown")
        }
      case _ => Left("Expected Variant for MigrationAction")
    }
  }
}
