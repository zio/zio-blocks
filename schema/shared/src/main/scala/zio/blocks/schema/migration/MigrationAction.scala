package zio.blocks.schema.migration

import zio.blocks.schema.{DynamicOptic, DynamicValue}

sealed trait MigrationAction {
  def at: DynamicOptic
  def reverse: MigrationAction
  def apply(value: DynamicValue): Either[MigrationError, DynamicValue]
}

object MigrationAction {

  final case class AddField(
    at: DynamicOptic,
    fieldName: String,
    defaultValue: DynamicValue
  ) extends MigrationAction {
    override def reverse: MigrationAction = DropField(at, fieldName, Some(defaultValue))

    override def apply(value: DynamicValue): Either[MigrationError, DynamicValue] =
      navigateAndTransform(value, at.nodes, 0) {
        case DynamicValue.Record(fields) =>
          if (fields.exists(_._1 == fieldName))
            Left(MigrationError.actionFailed(at, "AddField", s"Field '$fieldName' already exists"))
          else
            Right(DynamicValue.Record(fields :+ (fieldName, defaultValue)))
        case other =>
          Left(MigrationError.typeMismatch(at, "Record", other.getClass.getSimpleName))
      }
  }

  final case class DropField(
    at: DynamicOptic,
    fieldName: String,
    defaultForReverse: Option[DynamicValue]
  ) extends MigrationAction {
    override def reverse: MigrationAction = defaultForReverse match {
      case Some(default) => AddField(at, fieldName, default)
      case None => throw new UnsupportedOperationException(s"DropField('$fieldName') not reversible without default")
    }

    override def apply(value: DynamicValue): Either[MigrationError, DynamicValue] =
      navigateAndTransform(value, at.nodes, 0) {
        case DynamicValue.Record(fields) =>
          val newFields = fields.filterNot(_._1 == fieldName)
          if (newFields.length == fields.length) Left(MigrationError.missingField(at, fieldName))
          else Right(DynamicValue.Record(newFields))
        case other =>
          Left(MigrationError.typeMismatch(at, "Record", other.getClass.getSimpleName))
      }
  }

  final case class Rename(
    at: DynamicOptic,
    from: String,
    to: String
  ) extends MigrationAction {
    override def reverse: MigrationAction = Rename(at, to, from)

    override def apply(value: DynamicValue): Either[MigrationError, DynamicValue] =
      navigateAndTransform(value, at.nodes, 0) {
        case DynamicValue.Record(fields) =>
          val idx = fields.indexWhere(_._1 == from)
          if (idx < 0)
            Left(MigrationError.missingField(at, from))
          else if (fields.exists(_._1 == to))
            Left(MigrationError.actionFailed(at, "Rename", s"Field '$to' already exists"))
          else {
            val (_, fieldValue) = fields(idx)
            Right(DynamicValue.Record(fields.updated(idx, (to, fieldValue))))
          }
        case other =>
          Left(MigrationError.typeMismatch(at, "Record", other.getClass.getSimpleName))
      }
  }

  final case class TransformValue(
    at: DynamicOptic,
    transform: SchemaExpr
  ) extends MigrationAction {
    override def reverse: MigrationAction = transform.reverse match {
      case Some(rev) => TransformValue(at, rev)
      case None => throw new UnsupportedOperationException(s"TransformValue at $at not reversible")
    }

    override def apply(value: DynamicValue): Either[MigrationError, DynamicValue] =
      navigateAndTransform(value, at.nodes, 0)(transform(_))
  }

  final case class Mandate(
    at: DynamicOptic,
    fieldName: String,
    defaultValue: DynamicValue
  ) extends MigrationAction {
    override def reverse: MigrationAction = Optionalize(at, fieldName)

    override def apply(value: DynamicValue): Either[MigrationError, DynamicValue] =
      navigateAndTransform(value, at.nodes, 0) {
        case DynamicValue.Record(fields) =>
          val idx = fields.indexWhere(_._1 == fieldName)
          if (idx < 0) Left(MigrationError.missingField(at, fieldName))
          else {
            val (name, fieldValue) = fields(idx)
            fieldValue match {
              case DynamicValue.Variant("Some", inner) =>
                Right(DynamicValue.Record(fields.updated(idx, (name, inner))))
              case DynamicValue.Variant("None", _) =>
                Right(DynamicValue.Record(fields.updated(idx, (name, defaultValue))))
              case _ =>
                Right(DynamicValue.Record(fields))
            }
          }
        case other =>
          Left(MigrationError.typeMismatch(at, "Record", other.getClass.getSimpleName))
      }
  }

  final case class Optionalize(
    at: DynamicOptic,
    fieldName: String
  ) extends MigrationAction {
    override def reverse: MigrationAction =
      throw new UnsupportedOperationException(s"Optionalize('$fieldName') not reversible")

    override def apply(value: DynamicValue): Either[MigrationError, DynamicValue] =
      navigateAndTransform(value, at.nodes, 0) {
        case DynamicValue.Record(fields) =>
          val idx = fields.indexWhere(_._1 == fieldName)
          if (idx < 0) Left(MigrationError.missingField(at, fieldName))
          else {
            val (name, fieldValue) = fields(idx)
            fieldValue match {
              case DynamicValue.Variant("Some", _) | DynamicValue.Variant("None", _) =>
                Right(DynamicValue.Record(fields))
              case other =>
                Right(DynamicValue.Record(fields.updated(idx, (name, DynamicValue.Variant("Some", other)))))
            }
          }
        case other =>
          Left(MigrationError.typeMismatch(at, "Record", other.getClass.getSimpleName))
      }
  }

  final case class Join(
    at: DynamicOptic,
    sources: Vector[String],
    target: String,
    joinExpr: SchemaExpr
  ) extends MigrationAction {
    override def reverse: MigrationAction = joinExpr.reverse match {
      case Some(revExpr) => Split(at, target, sources, revExpr)
      case None => throw new UnsupportedOperationException(s"Join at $at not reversible")
    }

    override def apply(value: DynamicValue): Either[MigrationError, DynamicValue] =
      navigateAndTransform(value, at.nodes, 0) {
        case DynamicValue.Record(fields) =>
          val sourceFields = sources.flatMap(name => fields.find(_._1 == name))
          if (sourceFields.length != sources.length) {
            val missing = sources.filterNot(s => fields.exists(_._1 == s))
            Left(MigrationError.actionFailed(at, "Join", s"Missing source fields: ${missing.mkString(", ")}"))
          } else {
            val sourceRecord = DynamicValue.Record(sourceFields)
            joinExpr(sourceRecord).map { joined =>
              val remainingFields = fields.filterNot(f => sources.contains(f._1))
              DynamicValue.Record(remainingFields :+ (target, joined))
            }
          }
        case other =>
          Left(MigrationError.typeMismatch(at, "Record", other.getClass.getSimpleName))
      }
  }

  final case class Split(
    at: DynamicOptic,
    source: String,
    targets: Vector[String],
    splitExpr: SchemaExpr
  ) extends MigrationAction {
    override def reverse: MigrationAction = splitExpr.reverse match {
      case Some(revExpr) => Join(at, targets, source, revExpr)
      case None => throw new UnsupportedOperationException(s"Split at $at not reversible")
    }

    override def apply(value: DynamicValue): Either[MigrationError, DynamicValue] =
      navigateAndTransform(value, at.nodes, 0) {
        case DynamicValue.Record(fields) =>
          fields.find(_._1 == source) match {
            case Some((_, sourceValue)) =>
              splitExpr(sourceValue).flatMap {
                case DynamicValue.Record(splitFields) =>
                  if (splitFields.length != targets.length)
                    Left(MigrationError.actionFailed(at, "Split",
                      s"Split produced ${splitFields.length} fields but expected ${targets.length}"))
                  else {
                    val remainingFields = fields.filterNot(_._1 == source)
                    val newFields = targets.zip(splitFields.map(_._2)).toVector
                    Right(DynamicValue.Record(remainingFields ++ newFields))
                  }
                case other =>
                  Left(MigrationError.typeMismatch(at, "Record (from split)", other.getClass.getSimpleName))
              }
            case None =>
              Left(MigrationError.missingField(at, source))
          }
        case other =>
          Left(MigrationError.typeMismatch(at, "Record", other.getClass.getSimpleName))
      }
  }

  final case class ChangeType(
    at: DynamicOptic,
    fieldName: String,
    converter: SchemaExpr
  ) extends MigrationAction {
    override def reverse: MigrationAction = converter.reverse match {
      case Some(rev) => ChangeType(at, fieldName, rev)
      case None => throw new UnsupportedOperationException(s"ChangeType('$fieldName') not reversible")
    }

    override def apply(value: DynamicValue): Either[MigrationError, DynamicValue] =
      navigateAndTransform(value, at.nodes, 0) {
        case DynamicValue.Record(fields) =>
          val idx = fields.indexWhere(_._1 == fieldName)
          if (idx < 0) Left(MigrationError.missingField(at, fieldName))
          else {
            val (name, fieldValue) = fields(idx)
            converter(fieldValue).map(converted => DynamicValue.Record(fields.updated(idx, (name, converted))))
          }
        case other =>
          Left(MigrationError.typeMismatch(at, "Record", other.getClass.getSimpleName))
      }
  }

  final case class RenameCase(
    at: DynamicOptic,
    from: String,
    to: String
  ) extends MigrationAction {
    override def reverse: MigrationAction = RenameCase(at, to, from)

    override def apply(value: DynamicValue): Either[MigrationError, DynamicValue] =
      navigateAndTransform(value, at.nodes, 0) {
        case DynamicValue.Variant(caseName, caseValue) =>
          if (caseName == from) Right(DynamicValue.Variant(to, caseValue))
          else Right(DynamicValue.Variant(caseName, caseValue))
        case other =>
          Left(MigrationError.typeMismatch(at, "Variant", other.getClass.getSimpleName))
      }
  }

  final case class TransformCase(
    at: DynamicOptic,
    caseName: String,
    caseActions: Vector[MigrationAction]
  ) extends MigrationAction {
    override def reverse: MigrationAction =
      TransformCase(at, caseName, caseActions.reverse.map(_.reverse))

    override def apply(value: DynamicValue): Either[MigrationError, DynamicValue] =
      navigateAndTransform(value, at.nodes, 0) {
        case DynamicValue.Variant(name, caseValue) if name == caseName =>
          caseActions.foldLeft[Either[MigrationError, DynamicValue]](Right(caseValue)) {
            case (Right(v), action) => action(v)
            case (left, _)         => left
          }.map(DynamicValue.Variant(name, _))
        case other @ DynamicValue.Variant(_, _) =>
          Right(other)
        case other =>
          Left(MigrationError.typeMismatch(at, "Variant", other.getClass.getSimpleName))
      }
  }

  final case class TransformElements(
    at: DynamicOptic,
    elementTransform: SchemaExpr
  ) extends MigrationAction {
    override def reverse: MigrationAction = elementTransform.reverse match {
      case Some(rev) => TransformElements(at, rev)
      case None => throw new UnsupportedOperationException(s"TransformElements at $at not reversible")
    }

    override def apply(value: DynamicValue): Either[MigrationError, DynamicValue] =
      navigateAndTransform(value, at.nodes, 0) {
        case DynamicValue.Sequence(elements) =>
          elements.zipWithIndex.foldLeft[Either[MigrationError, Vector[DynamicValue]]](Right(Vector.empty)) {
            case (Right(acc), (elem, idx)) =>
              elementTransform(elem) match {
                case Right(transformed) => Right(acc :+ transformed)
                case Left(err) =>
                  Left(MigrationError.actionFailed(at.at(idx), "TransformElements", s"Failed at index $idx: ${err.message}"))
              }
            case (left, _) => left
          }.map(DynamicValue.Sequence.apply)
        case other =>
          Left(MigrationError.typeMismatch(at, "Sequence", other.getClass.getSimpleName))
      }
  }

  final case class TransformKeys(
    at: DynamicOptic,
    keyTransform: SchemaExpr
  ) extends MigrationAction {
    override def reverse: MigrationAction = keyTransform.reverse match {
      case Some(rev) => TransformKeys(at, rev)
      case None => throw new UnsupportedOperationException(s"TransformKeys at $at not reversible")
    }

    override def apply(value: DynamicValue): Either[MigrationError, DynamicValue] =
      navigateAndTransform(value, at.nodes, 0) {
        case DynamicValue.Map(entries) =>
          entries.foldLeft[Either[MigrationError, Vector[(DynamicValue, DynamicValue)]]](Right(Vector.empty)) {
            case (Right(acc), (k, v)) => keyTransform(k).map(newKey => acc :+ (newKey, v))
            case (left, _)           => left
          }.map(DynamicValue.Map.apply)
        case other =>
          Left(MigrationError.typeMismatch(at, "Map", other.getClass.getSimpleName))
      }
  }

  final case class TransformValues(
    at: DynamicOptic,
    valueTransform: SchemaExpr
  ) extends MigrationAction {
    override def reverse: MigrationAction = valueTransform.reverse match {
      case Some(rev) => TransformValues(at, rev)
      case None => throw new UnsupportedOperationException(s"TransformValues at $at not reversible")
    }

    override def apply(value: DynamicValue): Either[MigrationError, DynamicValue] =
      navigateAndTransform(value, at.nodes, 0) {
        case DynamicValue.Map(entries) =>
          entries.foldLeft[Either[MigrationError, Vector[(DynamicValue, DynamicValue)]]](Right(Vector.empty)) {
            case (Right(acc), (k, v)) => valueTransform(v).map(newValue => acc :+ (k, newValue))
            case (left, _)           => left
          }.map(DynamicValue.Map.apply)
        case other =>
          Left(MigrationError.typeMismatch(at, "Map", other.getClass.getSimpleName))
      }
  }

  private def navigateAndTransform(
    value: DynamicValue,
    path: IndexedSeq[DynamicOptic.Node],
    pathIdx: Int
  )(f: DynamicValue => Either[MigrationError, DynamicValue]): Either[MigrationError, DynamicValue] = {
    if (pathIdx >= path.length) {
      f(value)
    } else {
      path(pathIdx) match {
        case DynamicOptic.Node.Field(name) =>
          value match {
            case DynamicValue.Record(fields) =>
              val fieldIdx = fields.indexWhere(_._1 == name)
              if (fieldIdx < 0)
                Left(MigrationError.missingField(DynamicOptic(path.take(pathIdx + 1)), name))
              else {
                val (fieldName, fieldValue) = fields(fieldIdx)
                navigateAndTransform(fieldValue, path, pathIdx + 1)(f).map { newValue =>
                  DynamicValue.Record(fields.updated(fieldIdx, (fieldName, newValue)))
                }
              }
            case _ =>
              Left(MigrationError.typeMismatch(DynamicOptic(path.take(pathIdx)), "Record", value.getClass.getSimpleName))
          }

        case DynamicOptic.Node.Case(name) =>
          value match {
            case DynamicValue.Variant(caseName, caseValue) if caseName == name =>
              navigateAndTransform(caseValue, path, pathIdx + 1)(f).map(DynamicValue.Variant(caseName, _))
            case DynamicValue.Variant(caseName, _) =>
              Left(MigrationError.actionFailed(
                DynamicOptic(path.take(pathIdx + 1)), "navigateCase", s"Expected case '$name' but found '$caseName'"
              ))
            case _ =>
              Left(MigrationError.typeMismatch(DynamicOptic(path.take(pathIdx)), "Variant", value.getClass.getSimpleName))
          }

        case DynamicOptic.Node.AtIndex(index) =>
          value match {
            case DynamicValue.Sequence(elements) =>
              if (index < 0 || index >= elements.length)
                Left(MigrationError.invalidPath(
                  DynamicOptic(path.take(pathIdx + 1)), s"Index $index out of bounds (length: ${elements.length})"
                ))
              else
                navigateAndTransform(elements(index), path, pathIdx + 1)(f).map { newValue =>
                  DynamicValue.Sequence(elements.updated(index, newValue))
                }
            case _ =>
              Left(MigrationError.typeMismatch(DynamicOptic(path.take(pathIdx)), "Sequence", value.getClass.getSimpleName))
          }

        case DynamicOptic.Node.Elements =>
          value match {
            case DynamicValue.Sequence(elements) =>
              elements.zipWithIndex.foldLeft[Either[MigrationError, Vector[DynamicValue]]](Right(Vector.empty)) {
                case (Right(acc), (elem, _)) => navigateAndTransform(elem, path, pathIdx + 1)(f).map(acc :+ _)
                case (left, _)              => left
              }.map(DynamicValue.Sequence.apply)
            case _ =>
              Left(MigrationError.typeMismatch(DynamicOptic(path.take(pathIdx)), "Sequence", value.getClass.getSimpleName))
          }

        case DynamicOptic.Node.MapKeys =>
          value match {
            case DynamicValue.Map(entries) =>
              entries.foldLeft[Either[MigrationError, Vector[(DynamicValue, DynamicValue)]]](Right(Vector.empty)) {
                case (Right(acc), (k, v)) => navigateAndTransform(k, path, pathIdx + 1)(f).map(newKey => acc :+ (newKey, v))
                case (left, _)           => left
              }.map(DynamicValue.Map.apply)
            case _ =>
              Left(MigrationError.typeMismatch(DynamicOptic(path.take(pathIdx)), "Map", value.getClass.getSimpleName))
          }

        case DynamicOptic.Node.MapValues =>
          value match {
            case DynamicValue.Map(entries) =>
              entries.foldLeft[Either[MigrationError, Vector[(DynamicValue, DynamicValue)]]](Right(Vector.empty)) {
                case (Right(acc), (k, v)) => navigateAndTransform(v, path, pathIdx + 1)(f).map(newValue => acc :+ (k, newValue))
                case (left, _)           => left
              }.map(DynamicValue.Map.apply)
            case _ =>
              Left(MigrationError.typeMismatch(DynamicOptic(path.take(pathIdx)), "Map", value.getClass.getSimpleName))
          }

        case DynamicOptic.Node.AtMapKey(key) =>
          value match {
            case DynamicValue.Map(entries) =>
              val keyIdx = entries.indexWhere(_._1 == key)
              if (keyIdx < 0)
                Left(MigrationError.invalidPath(DynamicOptic(path.take(pathIdx + 1)), "Key not found in map"))
              else {
                val (k, v) = entries(keyIdx)
                navigateAndTransform(v, path, pathIdx + 1)(f).map(newValue => DynamicValue.Map(entries.updated(keyIdx, (k, newValue))))
              }
            case _ =>
              Left(MigrationError.typeMismatch(DynamicOptic(path.take(pathIdx)), "Map", value.getClass.getSimpleName))
          }

        case DynamicOptic.Node.Wrapped =>
          navigateAndTransform(value, path, pathIdx + 1)(f)

        case other =>
          Left(MigrationError.invalidPath(DynamicOptic(path.take(pathIdx + 1)), s"Unsupported node type: $other"))
      }
    }
  }
}
