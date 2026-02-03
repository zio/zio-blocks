package zio.blocks.schema.migration

import zio.blocks.schema.{DynamicOptic, DynamicValue, Schema}

final case class MigrationBuilder[A, B, Handled, Provided](
  sourceSchema: Schema[A],
  targetSchema: Schema[B],
  private[migration] val step: MigrationStep.Record,
  private[migration] val variantStep: MigrationStep.Variant
) {

  private[migration] def addField(path: String, defaultValue: DynamicValue): MigrationBuilder[A, B, Handled, Provided] =
    parsePath(path) match {
      case (Nil, fieldName)          => copy(step = step.addField(fieldName, defaultValue))
      case (head :: tail, fieldName) =>
        copy(step = step.nested(head) { nested =>
          applyNestedFieldAction(nested, tail, FieldAction.Add(fieldName, defaultValue))
        })
    }

  private[migration] def dropField(
    path: String,
    defaultForReverse: DynamicValue
  ): MigrationBuilder[A, B, Handled, Provided] =
    parsePath(path) match {
      case (Nil, fieldName)          => copy(step = step.removeField(fieldName, defaultForReverse))
      case (head :: tail, fieldName) =>
        copy(step = step.nested(head) { nested =>
          applyNestedFieldAction(nested, tail, FieldAction.Remove(fieldName, defaultForReverse))
        })
    }

  private[migration] def renameField(fromPath: String, toPath: String): MigrationBuilder[A, B, Handled, Provided] = {
    val (fromParent, fromName) = parsePath(fromPath)
    val (toParent, toName)     = parsePath(toPath)
    if (fromParent != toParent) {
      throw new IllegalArgumentException(
        s"Cannot rename across different parent paths: '$fromPath' -> '$toPath'"
      )
    }
    fromParent match {
      case Nil          => copy(step = step.renameField(fromName, toName))
      case head :: tail =>
        copy(step = step.nested(head) { nested =>
          applyNestedFieldAction(nested, tail, FieldAction.Rename(fromName, toName))
        })
    }
  }

  private[migration] def transformFieldValue(
    path: String,
    forward: DynamicValueTransform,
    backward: DynamicValueTransform
  ): MigrationBuilder[A, B, Handled, Provided] =
    parsePath(path) match {
      case (Nil, fieldName)          => copy(step = step.transformField(fieldName, forward, backward))
      case (head :: tail, fieldName) =>
        copy(step = step.nested(head) { nested =>
          applyNestedFieldAction(nested, tail, FieldAction.Transform(fieldName, forward, backward))
        })
    }

  private[migration] def optionalizeField(
    path: String,
    defaultForReverse: DynamicValue
  ): MigrationBuilder[A, B, Handled, Provided] =
    parsePath(path) match {
      case (Nil, fieldName)          => copy(step = step.makeFieldOptional(fieldName, defaultForReverse))
      case (head :: tail, fieldName) =>
        copy(step = step.nested(head) { nested =>
          applyNestedFieldAction(nested, tail, FieldAction.MakeOptional(fieldName, defaultForReverse))
        })
    }

  private[migration] def mandateField(
    path: String,
    defaultForNone: DynamicValue
  ): MigrationBuilder[A, B, Handled, Provided] =
    parsePath(path) match {
      case (Nil, fieldName)          => copy(step = step.makeFieldRequired(fieldName, defaultForNone))
      case (head :: tail, fieldName) =>
        copy(step = step.nested(head) { nested =>
          applyNestedFieldAction(nested, tail, FieldAction.MakeRequired(fieldName, defaultForNone))
        })
    }

  private[migration] def changeFieldType(
    path: String,
    forward: PrimitiveConversion,
    backward: PrimitiveConversion
  ): MigrationBuilder[A, B, Handled, Provided] =
    parsePath(path) match {
      case (Nil, fieldName)          => copy(step = step.changeFieldType(fieldName, forward, backward))
      case (head :: tail, fieldName) =>
        copy(step = step.nested(head) { nested =>
          applyNestedFieldAction(nested, tail, FieldAction.ChangeType(fieldName, forward, backward))
        })
    }

  private[migration] def joinFields(
    targetPath: String,
    sourceNames: Vector[String],
    combiner: DynamicValueTransform,
    splitter: DynamicValueTransform
  ): MigrationBuilder[A, B, Handled, Provided] =
    parsePath(targetPath) match {
      case (Nil, targetName) =>
        copy(step = step.joinFields(targetName, sourceNames, combiner, splitter))
      case (head :: tail, targetName) =>
        copy(step = step.nested(head) { nested =>
          applyNestedFieldAction(
            nested,
            tail,
            FieldAction.JoinFields(targetName, sourceNames, combiner, splitter)
          )
        })
    }

  private[migration] def splitField(
    sourcePath: String,
    targetNames: Vector[String],
    splitter: DynamicValueTransform,
    combiner: DynamicValueTransform
  ): MigrationBuilder[A, B, Handled, Provided] =
    parsePath(sourcePath) match {
      case (Nil, sourceName) =>
        copy(step = step.splitField(sourceName, targetNames, splitter, combiner))
      case (head :: tail, sourceName) =>
        copy(step = step.nested(head) { nested =>
          applyNestedFieldAction(
            nested,
            tail,
            FieldAction.SplitField(sourceName, targetNames, splitter, combiner)
          )
        })
    }

  private[migration] def renameCase(from: String, to: String): MigrationBuilder[A, B, Handled, Provided] =
    copy(variantStep = variantStep.renameCase(from, to))

  private[migration] def transformCase(
    caseName: String
  )(buildNested: MigrationStep.Record => MigrationStep.Record): MigrationBuilder[A, B, Handled, Provided] =
    copy(variantStep = variantStep.transformCase(caseName)(buildNested))

  private[migration] def transformElements(
    fieldPath: String
  )(buildNested: MigrationStep.Record => MigrationStep.Record): MigrationBuilder[A, B, Handled, Provided] = {
    val elementStep = buildNested(MigrationStep.Record.empty)
    val seqStep     = MigrationStep.Sequence(elementStep)
    addNestedStepAtPath(fieldPath, seqStep)
  }

  private[migration] def transformKeys(
    fieldPath: String
  )(buildNested: MigrationStep.Record => MigrationStep.Record): MigrationBuilder[A, B, Handled, Provided] = {
    val keyStep = buildNested(MigrationStep.Record.empty)
    val mapStep = MigrationStep.MapEntries(keyStep, MigrationStep.NoOp)
    addNestedStepAtPath(fieldPath, mapStep)
  }

  private[migration] def transformValues(
    fieldPath: String
  )(buildNested: MigrationStep.Record => MigrationStep.Record): MigrationBuilder[A, B, Handled, Provided] = {
    val valueStep = buildNested(MigrationStep.Record.empty)
    val mapStep   = MigrationStep.MapEntries(MigrationStep.NoOp, valueStep)
    addNestedStepAtPath(fieldPath, mapStep)
  }

  private def addNestedStepAtPath(path: String, nestedStep: MigrationStep): MigrationBuilder[A, B, Handled, Provided] =
    parsePath(path) match {
      case (Nil, fieldName) =>
        copy(step = step.copy(nestedFields = step.nestedFields + (fieldName -> nestedStep)))
      case (head :: tail, fieldName) =>
        copy(step = step.nested(head) { nested =>
          addNestedStepToRecord(nested, tail, fieldName, nestedStep)
        })
    }

  private def addNestedStepToRecord(
    record: MigrationStep.Record,
    path: List[String],
    fieldName: String,
    nestedStep: MigrationStep
  ): MigrationStep.Record =
    path match {
      case Nil =>
        record.copy(nestedFields = record.nestedFields + (fieldName -> nestedStep))
      case head :: tail =>
        record.nested(head)(nested => addNestedStepToRecord(nested, tail, fieldName, nestedStep))
    }

  private[migration] def addFieldExpr[T](
    optic: DynamicOptic,
    default: MigrationExpr[A, T]
  ): MigrationBuilder[A, B, Handled, Provided] =
    addField(extractFieldNameFromOptic(optic), exprToDynamicValue(default))

  private[migration] def dropFieldExpr[T](
    optic: DynamicOptic,
    defaultForReverse: MigrationExpr[B, T]
  ): MigrationBuilder[A, B, Handled, Provided] =
    dropField(extractFieldNameFromOptic(optic), exprToDynamicValue(defaultForReverse))

  private[migration] def optionalizeFieldExpr[T](
    optic: DynamicOptic,
    defaultForReverse: MigrationExpr[B, T]
  ): MigrationBuilder[A, B, Handled, Provided] =
    optionalizeField(extractFieldNameFromOptic(optic), exprToDynamicValue(defaultForReverse))

  private[migration] def mandateFieldExpr[T](
    optic: DynamicOptic,
    defaultForNone: MigrationExpr[A, T]
  ): MigrationBuilder[A, B, Handled, Provided] =
    mandateField(extractFieldNameFromOptic(optic), exprToDynamicValue(defaultForNone))

  private def extractFieldNameFromOptic(optic: DynamicOptic): String =
    optic.nodes.lastOption match {
      case Some(DynamicOptic.Node.Field(name)) => name
      case _                                   => throw new IllegalArgumentException(s"Expected field optic, got: $optic")
    }

  private def exprToDynamicValue[S, T](expr: MigrationExpr[S, T]): DynamicValue =
    expr match {
      case MigrationExpr.Literal(value, schema) => schema.asInstanceOf[Schema[Any]].toDynamicValue(value)
      case other                                =>
        throw new UnsupportedOperationException(
          s"Only MigrationExpr.literal() is supported for field defaults at build time. " +
            s"Got: ${other.getClass.getSimpleName}. " +
            "For computed values that depend on source data, use the non-Expr builder methods " +
            "(addField, dropField, etc.) with DynamicValueTransform instead."
        )
    }

  def buildPartial: Migration[A, B] = {
    val finalStep = combineSteps
    Migration(
      DynamicMigration(finalStep),
      sourceSchema,
      targetSchema
    )
  }

  // Note: `build` is defined as a macro in version-specific files
  // (MigrationBuilderSyntax.scala for Scala 2, MigrationBuilderMacros.scala for Scala 3)
  // to provide compile-time validation

  def toDynamicMigration: DynamicMigration =
    DynamicMigration(combineSteps)

  def addedFieldNames: Set[String] = extractAddedFieldPaths(step, "")

  def removedFieldNames: Set[String] = extractRemovedFieldPaths(step, "")

  def renamedFromNames: Set[String] = extractRenamedFromPaths(step, "")

  def renamedToNames: Set[String] = extractRenamedToPaths(step, "")

  private def extractAddedFieldPaths(record: MigrationStep.Record, prefix: String): Set[String] = {
    val directAdds = record.fieldActions.collect {
      case FieldAction.Add(name, _)                => prefix + name
      case FieldAction.JoinFields(target, _, _, _) => prefix + target
    }.toSet

    val splitAdds = record.fieldActions.collect { case FieldAction.SplitField(_, targets, _, _) =>
      targets.map(prefix + _)
    }.flatten.toSet

    val nestedAdds = record.nestedFields.flatMap { case (fieldName, nested) =>
      nested match {
        case r: MigrationStep.Record => extractAddedFieldPaths(r, prefix + fieldName + ".")
        case _                       => Set.empty[String]
      }
    }.toSet

    directAdds ++ splitAdds ++ nestedAdds
  }

  private def extractRemovedFieldPaths(record: MigrationStep.Record, prefix: String): Set[String] = {
    val directRemoves = record.fieldActions.collect {
      case FieldAction.Remove(name, _)             => prefix + name
      case FieldAction.SplitField(source, _, _, _) => prefix + source
    }.toSet

    val joinRemoves = record.fieldActions.collect { case FieldAction.JoinFields(_, sources, _, _) =>
      sources.map(prefix + _)
    }.flatten.toSet

    val nestedRemoves = record.nestedFields.flatMap { case (fieldName, nested) =>
      nested match {
        case r: MigrationStep.Record => extractRemovedFieldPaths(r, prefix + fieldName + ".")
        case _                       => Set.empty[String]
      }
    }.toSet

    directRemoves ++ joinRemoves ++ nestedRemoves
  }

  private def extractRenamedFromPaths(record: MigrationStep.Record, prefix: String): Set[String] = {
    val directRenames = record.fieldActions.collect { case FieldAction.Rename(from, _) =>
      prefix + from
    }.toSet

    val nestedRenames = record.nestedFields.flatMap { case (fieldName, nested) =>
      nested match {
        case r: MigrationStep.Record => extractRenamedFromPaths(r, prefix + fieldName + ".")
        case _                       => Set.empty[String]
      }
    }.toSet

    directRenames ++ nestedRenames
  }

  private def extractRenamedToPaths(record: MigrationStep.Record, prefix: String): Set[String] = {
    val directRenames = record.fieldActions.collect { case FieldAction.Rename(_, to) =>
      prefix + to
    }.toSet

    val nestedRenames = record.nestedFields.flatMap { case (fieldName, nested) =>
      nested match {
        case r: MigrationStep.Record => extractRenamedToPaths(r, prefix + fieldName + ".")
        case _                       => Set.empty[String]
      }
    }.toSet

    directRenames ++ nestedRenames
  }

  private def combineSteps: MigrationStep =
    if (variantStep.isEmpty) step
    else if (step.isEmpty) variantStep
    else
      throw new IllegalStateException(
        "Invalid migration: both record field actions and variant case actions are defined. " +
          "A single migration should operate on either a record type OR a variant type, not both."
      )

  private def parsePath(path: String): (List[String], String) = {
    val parts = path.split('.').toList.filterNot(_.isEmpty)
    if (parts.isEmpty) throw new IllegalArgumentException(s"Invalid empty path: '$path'")
    else (parts.init, parts.last)
  }

  private def applyNestedFieldAction(
    record: MigrationStep.Record,
    path: List[String],
    action: FieldAction
  ): MigrationStep.Record =
    path match {
      case Nil          => record.withFieldAction(action)
      case head :: tail => record.nested(head)(nested => applyNestedFieldAction(nested, tail, action))
    }
}

object MigrationBuilder extends MigrationBuilderCompanionMacro
