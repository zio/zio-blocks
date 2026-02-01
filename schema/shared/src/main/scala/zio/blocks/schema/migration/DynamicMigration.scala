package zio.blocks.schema.migration

import scala.collection.immutable.{Map => IMap}

import zio.blocks.chunk.Chunk
import zio.blocks.schema.{DynamicOptic, DynamicValue, Reflect, Schema}
import zio.blocks.schema.binding._
import zio.blocks.schema.binding.RegisterOffset.RegisterOffset
import zio.blocks.typeid.TypeId

final case class DynamicMigration(step: MigrationStep) {

  def apply(value: DynamicValue): Either[MigrationError, DynamicValue] =
    applyStep(value, step, DynamicOptic.root)

  def reverse: DynamicMigration = DynamicMigration(step.reverse)

  def andThen(that: DynamicMigration): DynamicMigration =
    DynamicMigration(composeMigrationSteps(this.step, that.step))

  private def composeMigrationSteps(first: MigrationStep, second: MigrationStep): MigrationStep =
    (first, second) match {
      case (MigrationStep.NoOp, s) => s
      case (s, MigrationStep.NoOp) => s

      case (r1: MigrationStep.Record, r2: MigrationStep.Record) =>
        MigrationStep.Record(
          r1.fieldActions ++ r2.fieldActions,
          mergeNestedMaps(r1.nestedFields, r2.nestedFields)
        )

      case (v1: MigrationStep.Variant, v2: MigrationStep.Variant) =>
        MigrationStep.Variant(
          v1.renames ++ v2.renames,
          mergeNestedMaps(v1.nestedCases, v2.nestedCases)
        )

      case (s1: MigrationStep.Sequence, s2: MigrationStep.Sequence) =>
        MigrationStep.Sequence(composeMigrationSteps(s1.elementStep, s2.elementStep))

      case (m1: MigrationStep.MapEntries, m2: MigrationStep.MapEntries) =>
        MigrationStep.MapEntries(
          composeMigrationSteps(m1.keyStep, m2.keyStep),
          composeMigrationSteps(m1.valueStep, m2.valueStep)
        )

      case (first, second) =>
        throw new IllegalArgumentException(
          s"Cannot compose incompatible migration step types: ${first.getClass.getSimpleName} and ${second.getClass.getSimpleName}"
        )
    }

  private def mergeNestedMaps(
    first: IMap[String, MigrationStep],
    second: IMap[String, MigrationStep]
  ): IMap[String, MigrationStep] = {
    val allKeys = first.keySet ++ second.keySet
    allKeys.map { key =>
      val merged = (first.get(key), second.get(key)) match {
        case (Some(s1), Some(s2)) => composeMigrationSteps(s1, s2)
        case (Some(s1), None)     => s1
        case (None, Some(s2))     => s2
        case (None, None)         => MigrationStep.NoOp
      }
      key -> merged
    }.toMap
  }

  private def applyStep(
    value: DynamicValue,
    step: MigrationStep,
    path: DynamicOptic
  ): Either[MigrationError, DynamicValue] = step match {
    case MigrationStep.NoOp =>
      Right(value)

    case r: MigrationStep.Record =>
      applyRecordStep(value, r, path)

    case v: MigrationStep.Variant =>
      applyVariantStep(value, v, path)

    case s: MigrationStep.Sequence =>
      applySequenceStep(value, s, path)

    case m: MigrationStep.MapEntries =>
      applyMapStep(value, m, path)
  }

  private def applyRecordStep(
    value: DynamicValue,
    step: MigrationStep.Record,
    path: DynamicOptic
  ): Either[MigrationError, DynamicValue] = value match {
    case DynamicValue.Record(fields) =>
      val fieldsAfterActions = step.fieldActions.foldLeft[Either[MigrationError, Vector[(String, DynamicValue)]]](
        Right(fields.toVector)
      ) {
        case (Right(currentFields), action) => applyFieldAction(currentFields, action, path)
        case (left @ Left(_), _)            => left
      }

      fieldsAfterActions.flatMap { currentFields =>
        step.nestedFields.foldLeft[Either[MigrationError, Vector[(String, DynamicValue)]]](Right(currentFields)) {
          case (Right(flds), (fieldName, nestedStep)) =>
            val fieldIdx = flds.indexWhere(_._1 == fieldName)
            if (fieldIdx < 0) {
              if (nestedStep.isEmpty) Right(flds)
              else Left(MigrationError.fieldNotFound(path, fieldName))
            } else {
              val (name, fieldValue) = flds(fieldIdx)
              applyStep(fieldValue, nestedStep, path.field(fieldName)).map { newValue =>
                flds.updated(fieldIdx, (name, newValue))
              }
            }
          case (left @ Left(_), _) => left
        }
      }.map(v => DynamicValue.Record(Chunk.from(v)))

    case _ =>
      Left(MigrationError.typeMismatch(path, "Record", value.valueType.toString))
  }

  private def applyFieldAction(
    fields: Vector[(String, DynamicValue)],
    action: FieldAction,
    path: DynamicOptic
  ): Either[MigrationError, Vector[(String, DynamicValue)]] = action match {
    case FieldAction.Add(name, defaultValue) =>
      if (fields.exists(_._1 == name)) {
        Left(MigrationError.fieldAlreadyExists(path, name))
      } else {
        Right(fields :+ (name -> defaultValue))
      }

    case FieldAction.Remove(name, _) =>
      val idx = fields.indexWhere(_._1 == name)
      if (idx < 0) {
        Left(MigrationError.fieldNotFound(path, name))
      } else {
        Right(fields.patch(idx, Vector.empty, 1))
      }

    case FieldAction.Rename(from, to) =>
      val idx = fields.indexWhere(_._1 == from)
      if (idx < 0) {
        Left(MigrationError.fieldNotFound(path, from))
      } else if (fields.exists(_._1 == to)) {
        Left(MigrationError.fieldAlreadyExists(path, to))
      } else {
        val (_, value) = fields(idx)
        Right(fields.updated(idx, (to, value)))
      }

    case FieldAction.Transform(name, forward, _) =>
      val idx = fields.indexWhere(_._1 == name)
      if (idx < 0) {
        Left(MigrationError.fieldNotFound(path, name))
      } else {
        val (_, value) = fields(idx)
        forward(value) match {
          case Right(newValue) => Right(fields.updated(idx, (name, newValue)))
          case Left(err)       => Left(MigrationError.transformFailed(path.field(name), err))
        }
      }

    case FieldAction.MakeOptional(name, _) =>
      val idx = fields.indexWhere(_._1 == name)
      if (idx < 0) {
        Left(MigrationError.fieldNotFound(path, name))
      } else {
        val (_, value) = fields(idx)
        val wrapped    = DynamicValue.Variant("Some", DynamicValue.Record("value" -> value))
        Right(fields.updated(idx, (name, wrapped)))
      }

    case FieldAction.MakeRequired(name, defaultForNone) =>
      val idx = fields.indexWhere(_._1 == name)
      if (idx < 0) {
        Left(MigrationError.fieldNotFound(path, name))
      } else {
        val (_, value) = fields(idx)
        value match {
          case DynamicValue.Variant("Some", inner) =>
            val unwrapped = inner.get("value").one.getOrElse(inner)
            Right(fields.updated(idx, (name, unwrapped)))
          case DynamicValue.Variant("None", _) =>
            Right(fields.updated(idx, (name, defaultForNone)))
          case DynamicValue.Null =>
            Right(fields.updated(idx, (name, defaultForNone)))
          case other =>
            Left(
              MigrationError.transformFailed(
                path.field(name),
                s"MakeRequired expects an optional value (Some/None/Null), got ${other.valueType}"
              )
            )
        }
      }

    case FieldAction.ChangeType(name, forward, _) =>
      val idx = fields.indexWhere(_._1 == name)
      if (idx < 0) {
        Left(MigrationError.fieldNotFound(path, name))
      } else {
        val (_, value) = fields(idx)
        forward(value) match {
          case Right(newValue) => Right(fields.updated(idx, (name, newValue)))
          case Left(err)       => Left(MigrationError.transformFailed(path.field(name), err))
        }
      }

    case FieldAction.JoinFields(targetName, sourceNames, combiner, _) =>
      if (fields.exists(_._1 == targetName)) {
        Left(MigrationError.fieldAlreadyExists(path, targetName))
      } else {
        val sourceValues = sourceNames.flatMap { name =>
          fields.find(_._1 == name).map(_._2)
        }
        if (sourceValues.length != sourceNames.length) {
          sourceNames.find(name => !fields.exists(_._1 == name)) match {
            case Some(missingField) => Left(MigrationError.fieldNotFound(path, missingField))
            case None               => Left(MigrationError.transformFailed(path, "Unknown missing field"))
          }
        } else {
          val sourceRecord = DynamicValue.Record(
            Chunk.from(sourceNames.zip(sourceValues))
          )
          combiner(sourceRecord) match {
            case Right(combinedValue) =>
              val fieldsWithoutSources = fields.filterNot(f => sourceNames.contains(f._1))
              Right(fieldsWithoutSources :+ (targetName -> combinedValue))
            case Left(err) =>
              Left(MigrationError.transformFailed(path.field(targetName), err))
          }
        }
      }

    case FieldAction.SplitField(sourceName, targetNames, splitter, _) =>
      val idx = fields.indexWhere(_._1 == sourceName)
      if (idx < 0) {
        Left(MigrationError.fieldNotFound(path, sourceName))
      } else {
        targetNames.find(name => fields.exists(_._1 == name)) match {
          case Some(existingField) =>
            Left(MigrationError.fieldAlreadyExists(path, existingField))
          case None =>
            val (_, sourceValue) = fields(idx)
            splitter(sourceValue) match {
              case Right(DynamicValue.Record(splitFields)) =>
                val newFieldsMap      = splitFields.toMap
                val targetFieldValues = targetNames.flatMap { name =>
                  newFieldsMap.get(name).map(name -> _)
                }
                if (targetFieldValues.length != targetNames.length) {
                  Left(
                    MigrationError.transformFailed(
                      path.field(sourceName),
                      s"Splitter did not produce all target fields: ${targetNames.mkString(", ")}"
                    )
                  )
                } else {
                  val fieldsWithoutSource = fields.patch(idx, Vector.empty, 1)
                  Right(fieldsWithoutSource ++ targetFieldValues)
                }
              case Right(_) =>
                Left(
                  MigrationError.transformFailed(
                    path.field(sourceName),
                    "Splitter must return a Record with target field names"
                  )
                )
              case Left(err) =>
                Left(MigrationError.transformFailed(path.field(sourceName), err))
            }
        }
      }
  }

  private def applyVariantStep(
    value: DynamicValue,
    step: MigrationStep.Variant,
    path: DynamicOptic
  ): Either[MigrationError, DynamicValue] = value match {
    case DynamicValue.Variant(caseName, caseValue) =>
      val renamedCaseName = step.renames.getOrElse(caseName, caseName)

      step.nestedCases.get(renamedCaseName) match {
        case Some(nestedStep) =>
          applyStep(caseValue, nestedStep, path.caseOf(renamedCaseName)).map { newValue =>
            DynamicValue.Variant(renamedCaseName, newValue)
          }
        case None =>
          Right(DynamicValue.Variant(renamedCaseName, caseValue))
      }

    case _ =>
      Left(MigrationError.typeMismatch(path, "Variant", value.valueType.toString))
  }

  private def applySequenceStep(
    value: DynamicValue,
    step: MigrationStep.Sequence,
    path: DynamicOptic
  ): Either[MigrationError, DynamicValue] = value match {
    case DynamicValue.Sequence(elements) =>
      elements.zipWithIndex
        .foldLeft[Either[MigrationError, Vector[DynamicValue]]](Right(Vector.empty)) {
          case (Right(acc), (elem, idx)) =>
            applyStep(elem, step.elementStep, path.at(idx)).map(acc :+ _)
          case (left @ Left(_), _) => left
        }
        .map(v => DynamicValue.Sequence(Chunk.from(v)))

    case _ =>
      Left(MigrationError.typeMismatch(path, "Sequence", value.valueType.toString))
  }

  private def applyMapStep(
    value: DynamicValue,
    step: MigrationStep.MapEntries,
    path: DynamicOptic
  ): Either[MigrationError, DynamicValue] = value match {
    case DynamicValue.Map(entries) =>
      entries
        .foldLeft[Either[MigrationError, Vector[(DynamicValue, DynamicValue)]]](Right(Vector.empty)) {
          case (Right(acc), (key, entryValue)) =>
            for {
              newKey   <- applyStep(key, step.keyStep, path.mapKeys)
              newValue <- applyStep(entryValue, step.valueStep, path.mapValues)
            } yield acc :+ ((newKey, newValue))
          case (left @ Left(_), _) => left
        }
        .flatMap { migratedEntries =>
          val keys = migratedEntries.map(_._1)
          if (keys.distinct.size != keys.size) Left(MigrationError.duplicateMapKey(path))
          else Right(DynamicValue.Map(Chunk.from(migratedEntries)))
        }

    case _ =>
      Left(MigrationError.typeMismatch(path, "Map", value.valueType.toString))
  }
}

object DynamicMigration {

  val identity: DynamicMigration = DynamicMigration(MigrationStep.NoOp)

  def record(build: MigrationStep.Record => MigrationStep.Record): DynamicMigration =
    DynamicMigration(build(MigrationStep.Record.empty))

  def variant(build: MigrationStep.Variant => MigrationStep.Variant): DynamicMigration =
    DynamicMigration(build(MigrationStep.Variant.empty))

  def sequence(elementMigration: DynamicMigration): DynamicMigration =
    DynamicMigration(MigrationStep.Sequence(elementMigration.step))

  implicit lazy val schema: Schema[DynamicMigration] = new Schema(
    reflect = new Reflect.Record[Binding, DynamicMigration](
      fields = Vector(
        MigrationStep.schema.reflect.asTerm("step")
      ),
      typeId = TypeId.of[DynamicMigration],
      recordBinding = new Binding.Record(
        constructor = new Constructor[DynamicMigration] {
          def usedRegisters: RegisterOffset                                      = 1
          def construct(in: Registers, offset: RegisterOffset): DynamicMigration =
            DynamicMigration(in.getObject(offset + 0).asInstanceOf[MigrationStep])
        },
        deconstructor = new Deconstructor[DynamicMigration] {
          def usedRegisters: RegisterOffset                                                   = 1
          def deconstruct(out: Registers, offset: RegisterOffset, in: DynamicMigration): Unit =
            out.setObject(offset + 0, in.step)
        }
      ),
      modifiers = Vector.empty
    )
  )
}
