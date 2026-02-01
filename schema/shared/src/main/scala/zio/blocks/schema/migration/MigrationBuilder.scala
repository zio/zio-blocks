package zio.blocks.schema.migration

import zio.blocks.schema.{DynamicOptic, DynamicValue, Schema}

final class MigrationBuilder[A, B] private (
  val sourceSchema: Schema[A],
  val targetSchema: Schema[B],
  private val step: MigrationStep.Record,
  private val variantStep: MigrationStep.Variant
) {

  def addField(path: String, defaultValue: DynamicValue): MigrationBuilder[A, B] =
    parsePath(path) match {
      case (Nil, fieldName)          => copy(step = step.addField(fieldName, defaultValue))
      case (head :: tail, fieldName) =>
        copy(step = step.nested(head) { nested =>
          applyNestedFieldAction(nested, tail, FieldAction.add(fieldName, defaultValue))
        })
    }

  def addFieldExpr[T](path: DynamicOptic, default: MigrationExpr[A, T]): MigrationBuilder[A, B] = {
    val pathString     = dynamicOpticToPath(path)
    val defaultDynamic = default.evalDynamic(DynamicValue.Null) match {
      case Right(dv) => dv
      case Left(_)   => DynamicValue.Null
    }
    addField(pathString, defaultDynamic)
  }

  def dropField(path: String, defaultForReverse: DynamicValue): MigrationBuilder[A, B] =
    parsePath(path) match {
      case (Nil, fieldName)          => copy(step = step.removeField(fieldName, defaultForReverse))
      case (head :: tail, fieldName) =>
        copy(step = step.nested(head) { nested =>
          applyNestedFieldAction(nested, tail, FieldAction.remove(fieldName, defaultForReverse))
        })
    }

  def dropFieldExpr[T](path: DynamicOptic, defaultForReverse: MigrationExpr[B, T]): MigrationBuilder[A, B] = {
    val pathString     = dynamicOpticToPath(path)
    val defaultDynamic = defaultForReverse.evalDynamic(DynamicValue.Null) match {
      case Right(dv) => dv
      case Left(_)   => DynamicValue.Null
    }
    dropField(pathString, defaultDynamic)
  }

  def renameField(fromPath: String, toPath: String): MigrationBuilder[A, B] = {
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
          applyNestedFieldAction(nested, tail, FieldAction.rename(fromName, toName))
        })
    }
  }

  def transformField(
    path: String,
    forward: DynamicValueTransform,
    backward: DynamicValueTransform
  ): MigrationBuilder[A, B] =
    parsePath(path) match {
      case (Nil, fieldName)          => copy(step = step.transformField(fieldName, forward, backward))
      case (head :: tail, fieldName) =>
        copy(step = step.nested(head) { nested =>
          applyNestedFieldAction(nested, tail, FieldAction.transform(fieldName, forward, backward))
        })
    }

  def optionalizeField(path: String, defaultForReverse: DynamicValue): MigrationBuilder[A, B] =
    parsePath(path) match {
      case (Nil, fieldName)          => copy(step = step.makeFieldOptional(fieldName, defaultForReverse))
      case (head :: tail, fieldName) =>
        copy(step = step.nested(head) { nested =>
          applyNestedFieldAction(nested, tail, FieldAction.makeOptional(fieldName, defaultForReverse))
        })
    }

  def optionalizeFieldExpr[T](path: DynamicOptic, defaultForReverse: MigrationExpr[B, T]): MigrationBuilder[A, B] = {
    val pathString     = dynamicOpticToPath(path)
    val defaultDynamic = defaultForReverse.evalDynamic(DynamicValue.Null) match {
      case Right(dv) => dv
      case Left(_)   => DynamicValue.Null
    }
    optionalizeField(pathString, defaultDynamic)
  }

  def mandateField(path: String, defaultForNone: DynamicValue): MigrationBuilder[A, B] =
    parsePath(path) match {
      case (Nil, fieldName)          => copy(step = step.makeFieldRequired(fieldName, defaultForNone))
      case (head :: tail, fieldName) =>
        copy(step = step.nested(head) { nested =>
          applyNestedFieldAction(nested, tail, FieldAction.makeRequired(fieldName, defaultForNone))
        })
    }

  def mandateFieldExpr[T](path: DynamicOptic, defaultForNone: MigrationExpr[A, T]): MigrationBuilder[A, B] = {
    val pathString     = dynamicOpticToPath(path)
    val defaultDynamic = defaultForNone.evalDynamic(DynamicValue.Null) match {
      case Right(dv) => dv
      case Left(_)   => DynamicValue.Null
    }
    mandateField(pathString, defaultDynamic)
  }

  def changeFieldType(
    path: String,
    forward: PrimitiveConversion,
    backward: PrimitiveConversion
  ): MigrationBuilder[A, B] =
    parsePath(path) match {
      case (Nil, fieldName)          => copy(step = step.changeFieldType(fieldName, forward, backward))
      case (head :: tail, fieldName) =>
        copy(step = step.nested(head) { nested =>
          applyNestedFieldAction(nested, tail, FieldAction.changeType(fieldName, forward, backward))
        })
    }

  def joinFields(
    targetPath: String,
    sourceNames: Vector[String],
    combiner: DynamicValueTransform,
    splitter: DynamicValueTransform
  ): MigrationBuilder[A, B] =
    parsePath(targetPath) match {
      case (Nil, targetName) =>
        copy(step = step.joinFields(targetName, sourceNames, combiner, splitter))
      case (head :: tail, targetName) =>
        copy(step = step.nested(head) { nested =>
          applyNestedFieldAction(
            nested,
            tail,
            FieldAction.joinFields(targetName, sourceNames, combiner, splitter)
          )
        })
    }

  def splitField(
    sourcePath: String,
    targetNames: Vector[String],
    splitter: DynamicValueTransform,
    combiner: DynamicValueTransform
  ): MigrationBuilder[A, B] =
    parsePath(sourcePath) match {
      case (Nil, sourceName) =>
        copy(step = step.splitField(sourceName, targetNames, splitter, combiner))
      case (head :: tail, sourceName) =>
        copy(step = step.nested(head) { nested =>
          applyNestedFieldAction(
            nested,
            tail,
            FieldAction.splitField(sourceName, targetNames, splitter, combiner)
          )
        })
    }

  def addCase(caseName: String, defaultValue: DynamicValue): MigrationBuilder[A, B] =
    copy(variantStep = variantStep.addCase(caseName, defaultValue))

  def addCaseExpr[T](caseName: String, default: MigrationExpr[A, T]): MigrationBuilder[A, B] = {
    val defaultDynamic = default.evalDynamic(DynamicValue.Null) match {
      case Right(dv) => dv
      case Left(_)   => DynamicValue.Null
    }
    addCase(caseName, defaultDynamic)
  }

  private def dropCase(caseName: String, defaultForReverse: DynamicValue): MigrationBuilder[A, B] =
    copy(variantStep = variantStep.removeCase(caseName, defaultForReverse))

  def dropCaseExpr[T](caseName: String, defaultForReverse: MigrationExpr[B, T]): MigrationBuilder[A, B] = {
    val defaultDynamic = defaultForReverse.evalDynamic(DynamicValue.Null) match {
      case Right(dv) => dv
      case Left(_)   => DynamicValue.Null
    }
    dropCase(caseName, defaultDynamic)
  }

  def renameCase(from: String, to: String): MigrationBuilder[A, B] =
    copy(variantStep = variantStep.renameCase(from, to))

  def nestedRecord(fieldName: String)(
    buildNested: MigrationStep.Record => MigrationStep.Record
  ): MigrationBuilder[A, B] =
    copy(step = step.nested(fieldName)(buildNested))

  def nestedVariant(caseName: String)(
    buildNested: MigrationStep.Record => MigrationStep.Record
  ): MigrationBuilder[A, B] =
    copy(variantStep = variantStep.nested(caseName)(buildNested))

  def transformElements(elementStep: MigrationStep): MigrationBuilder[A, B] = {
    val sequenceStep = MigrationStep.sequence(elementStep)
    copy(step =
      MigrationStep.Record(
        step.fieldActions,
        step.nestedFields + ("_elements" -> sequenceStep)
      )
    )
  }

  def transformMapKeys(keyStep: MigrationStep): MigrationBuilder[A, B] = {
    val existingMapStep = step.nestedFields.getOrElse("_map", MigrationStep.MapEntries.empty) match {
      case m: MigrationStep.MapEntries => m
      case _                           => MigrationStep.MapEntries.empty
    }
    copy(step =
      MigrationStep.Record(
        step.fieldActions,
        step.nestedFields + ("_map" -> existingMapStep.copy(keyStep = keyStep))
      )
    )
  }

  def transformMapValues(valueStep: MigrationStep): MigrationBuilder[A, B] = {
    val existingMapStep = step.nestedFields.getOrElse("_map", MigrationStep.MapEntries.empty) match {
      case m: MigrationStep.MapEntries => m
      case _                           => MigrationStep.MapEntries.empty
    }
    copy(step =
      MigrationStep.Record(
        step.fieldActions,
        step.nestedFields + ("_map" -> existingMapStep.copy(valueStep = valueStep))
      )
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

  def build: Migration[A, B] = {
    validateAtRuntime()
    buildPartial
  }

  private def validateAtRuntime(): Unit = {
    val sourceFields = extractSchemaFields(sourceSchema)
    val targetFields = extractSchemaFields(targetSchema)

    val added       = addedFieldNames
    val removed     = removedFieldNames
    val renamedFrom = renamedFromNames
    val renamedTo   = renamedToNames

    val transformedSource = (sourceFields -- removed -- renamedFrom) ++ added ++ renamedTo
    val missing           = targetFields -- transformedSource
    val extra             = transformedSource -- targetFields

    if (missing.nonEmpty || extra.nonEmpty) {
      val missingMsg = if (missing.nonEmpty) s"Missing fields in target: ${missing.mkString(", ")}" else ""
      val extraMsg   = if (extra.nonEmpty) s"Extra fields not in target: ${extra.mkString(", ")}" else ""
      val msgs       = Seq(missingMsg, extraMsg).filter(_.nonEmpty).mkString("; ")
      throw new IllegalStateException(s"Migration validation failed: $msgs")
    }
  }

  private def extractSchemaFields(schema: Schema[_]): Set[String] =
    schema.reflect.asRecord match {
      case Some(record) => record.fields.map(_.name).toSet
      case None         => Set.empty[String]
    }

  def toDynamicMigration: DynamicMigration =
    DynamicMigration(combineSteps)

  def addedFieldNames: Set[String] = extractAddedFields(step)

  def removedFieldNames: Set[String] = extractRemovedFields(step)

  def renamedFromNames: Set[String] = extractRenamedFrom(step)

  def renamedToNames: Set[String] = extractRenamedTo(step)

  private def extractAddedFields(record: MigrationStep.Record): Set[String] = {
    val directAdds = record.fieldActions.collect {
      case FieldAction.Add(name, _)                => name
      case FieldAction.JoinFields(target, _, _, _) => target
    }.toSet

    // Also include fields added by split operations
    val splitAdds = record.fieldActions.collect { case FieldAction.SplitField(_, targets, _, _) =>
      targets
    }.flatten.toSet

    directAdds ++ splitAdds
  }

  private def extractRemovedFields(record: MigrationStep.Record): Set[String] = {
    val directRemoves = record.fieldActions.collect {
      case FieldAction.Remove(name, _)             => name
      case FieldAction.SplitField(source, _, _, _) => source
    }.toSet

    // Also include fields removed by join operations
    val joinRemoves = record.fieldActions.collect { case FieldAction.JoinFields(_, sources, _, _) =>
      sources
    }.flatten.toSet

    directRemoves ++ joinRemoves
  }

  private def extractRenamedFrom(record: MigrationStep.Record): Set[String] =
    record.fieldActions.collect { case FieldAction.Rename(from, _) =>
      from
    }.toSet

  private def extractRenamedTo(record: MigrationStep.Record): Set[String] =
    record.fieldActions.collect { case FieldAction.Rename(_, to) =>
      to
    }.toSet

  private def combineSteps: MigrationStep =
    if (variantStep.isEmpty) step
    else if (step.isEmpty) variantStep
    else step

  private def copy(
    step: MigrationStep.Record = this.step,
    variantStep: MigrationStep.Variant = this.variantStep
  ): MigrationBuilder[A, B] =
    new MigrationBuilder(sourceSchema, targetSchema, step, variantStep)

  private def parsePath(path: String): (List[String], String) = {
    val parts = path.split('.').toList.filterNot(_.isEmpty)
    parts.reverse match {
      case Nil             => throw new IllegalArgumentException(s"Invalid empty path: '$path'")
      case last :: Nil     => (Nil, last)
      case last :: initRev => (initRev.reverse, last)
    }
  }

  private def dynamicOpticToPath(optic: DynamicOptic): String =
    optic.nodes.collect { case DynamicOptic.Node.Field(name) =>
      name
    }.mkString(".")

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

object MigrationBuilder {

  def apply[A, B](implicit sourceSchema: Schema[A], targetSchema: Schema[B]): MigrationBuilder[A, B] =
    new MigrationBuilder(sourceSchema, targetSchema, MigrationStep.Record.empty, MigrationStep.Variant.empty)

  def from[A](implicit sourceSchema: Schema[A]): FromBuilder[A] =
    new FromBuilder[A](sourceSchema)

  final class FromBuilder[A](sourceSchema: Schema[A]) {
    def to[B](implicit targetSchema: Schema[B]): MigrationBuilder[A, B] =
      new MigrationBuilder(sourceSchema, targetSchema, MigrationStep.Record.empty, MigrationStep.Variant.empty)
  }
}
