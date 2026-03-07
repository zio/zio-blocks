package zio.blocks.schema.migration

import zio.blocks.chunk.Chunk
import zio.blocks.schema.{DynamicOptic, DynamicValue, PrimitiveValue, SchemaError}

/**
 * Pure, serializable migration that transforms DynamicValue to DynamicValue.
 * Composable (++) and reversible (reverse). All actions are applied in order.
 */
final case class DynamicMigration(actions: Chunk[MigrationAction]) {

  def apply(value: DynamicValue): Either[SchemaError, DynamicValue] =
    actions.foldLeft[Either[SchemaError, DynamicValue]](Right(value)) { (acc, action) =>
      acc.flatMap(DynamicMigration.applyAction(_, action))
    }

  def ++(that: DynamicMigration): DynamicMigration =
    DynamicMigration(actions ++ that.actions)

  def reverse: DynamicMigration =
    DynamicMigration(actions.reverse.map(_.reverse))
}

object DynamicMigration {

  val empty: DynamicMigration = DynamicMigration(Chunk.empty)

  private def applyAction(value: DynamicValue, action: MigrationAction): Either[SchemaError, DynamicValue] =
    action match {
      case a: MigrationAction.AddField       => applyAddField(value, a)
      case a: MigrationAction.DropField     => applyDropField(value, a)
      case a: MigrationAction.Rename        => applyRename(value, a)
      case a: MigrationAction.TransformValue  => applyTransformValue(value, a)
      case a: MigrationAction.Mandate       => applyMandate(value, a)
      case a: MigrationAction.Optionalize   => Right(value)
      case a: MigrationAction.ChangeType    => applyChangeType(value, a)
      case a: MigrationAction.RenameCase    => applyRenameCase(value, a)
      case a: MigrationAction.TransformCase  => applyTransformCase(value, a)
      case a: MigrationAction.TransformElements => applyTransformElements(value, a)
      case a: MigrationAction.TransformKeys     => applyTransformKeys(value, a)
      case a: MigrationAction.TransformValues   => applyTransformValues(value, a)
      case a: MigrationAction.Join   => applyJoin(value, a)
      case a: MigrationAction.Split  => applySplit(value, a)
    }

  private def applyAddField(value: DynamicValue, a: MigrationAction.AddField): Either[SchemaError, DynamicValue] =
    for {
      defaultVal <- a.default.eval(value)
      result     <- value.insertOrFail(a.at, defaultVal)
    } yield result

  private def applyDropField(value: DynamicValue, a: MigrationAction.DropField): Either[SchemaError, DynamicValue] =
    value.deleteOrFail(a.at)

  private def applyRename(value: DynamicValue, a: MigrationAction.Rename): Either[SchemaError, DynamicValue] = {
    val parentNodes = a.at.nodes.dropRight(1)
    val toPath     = new DynamicOptic(parentNodes :+ DynamicOptic.Node.Field(a.to))
    for {
      v      <- value.get(a.at).one
      without <- value.deleteOrFail(a.at)
      result <- without.insertOrFail(toPath, v)
    } yield result
  }

  private def applyTransformValue(value: DynamicValue, a: MigrationAction.TransformValue): Either[SchemaError, DynamicValue] =
    for {
      newVal <- a.transform.eval(value)
      result <- value.setOrFail(a.at, newVal)
    } yield result

  private def applyChangeType(value: DynamicValue, a: MigrationAction.ChangeType): Either[SchemaError, DynamicValue] =
    for {
      current <- value.get(a.at).one
      newVal  <- a.converter.eval(current)
      result  <- value.setOrFail(a.at, newVal)
    } yield result

  private def applyJoin(value: DynamicValue, a: MigrationAction.Join): Either[SchemaError, DynamicValue] =
    a.combineOp.eval(value, a.sourcePaths).flatMap { combined =>
      value.insertOrFail(a.at, combined)
    }

  private def applySplit(value: DynamicValue, a: MigrationAction.Split): Either[SchemaError, DynamicValue] =
    for {
      sourceVal <- value.get(a.at).one
      parts     <- a.splitOp.evalSplit(sourceVal)
      _         <- if (parts.length == a.targetPaths.length) Right(())
                   else Left(SchemaError.message(
                     s"Split produced ${parts.length} parts but ${a.targetPaths.length} target paths",
                     a.at
                   ))
      without   <- value.deleteOrFail(a.at)
      result    <- parts.zip(a.targetPaths).foldLeft[Either[SchemaError, DynamicValue]](Right(without)) {
                     case (acc, (part, path)) =>
                       acc.flatMap(_.insertOrFail(path, part))
                   }
    } yield result

  private def applyMandate(value: DynamicValue, a: MigrationAction.Mandate): Either[SchemaError, DynamicValue] = {
    value.get(a.at).one match {
      case Right(_) => Right(value)
      case Left(_)  =>
        for {
          defaultVal <- a.default.eval(value)
          result     <- value.insertOrFail(a.at, defaultVal)
        } yield result
    }
  }

  private def applyRenameCase(value: DynamicValue, a: MigrationAction.RenameCase): Either[SchemaError, DynamicValue] = {
    val pathNodes = a.at.nodes
    if (pathNodes.isEmpty) {
      value match {
        case v: DynamicValue.Variant if v.caseNameValue == a.from =>
          Right(new DynamicValue.Variant(a.to, v.value))
        case _ => Left(SchemaError.message(s"Expected variant case '${a.from}' at root", a.at))
      }
    } else {
      value.modifyOrFail(a.at) {
        case v: DynamicValue.Variant if v.caseNameValue == a.from =>
          new DynamicValue.Variant(a.to, v.value)
      }
    }
  }

  private def applyTransformCase(value: DynamicValue, a: MigrationAction.TransformCase): Either[SchemaError, DynamicValue] = {
    val subMigration = DynamicMigration(a.actions)
    for {
      variant <- value.get(a.at).one
      v       <- variant match {
                   case vv: DynamicValue.Variant => Right(vv)
                   case _                        => Left(SchemaError.message("TransformCase path must point to a Variant", a.at))
                 }
      newCaseVal <- subMigration(v.value)
      modifiedCase = new DynamicValue.Variant(v.caseNameValue, newCaseVal)
      result      <- value.setOrFail(a.at, modifiedCase)
    } yield result
  }

  private def applyTransformElements(value: DynamicValue, a: MigrationAction.TransformElements): Either[SchemaError, DynamicValue] =
    value.get(a.at).one match {
      case Right(seq: DynamicValue.Sequence) =>
        val builder = Chunk.newBuilder[DynamicValue]
        var err: Option[SchemaError] = None
        seq.elements.foreach { elem =>
          if (err.isEmpty) a.transform.eval(elem) match {
            case Right(dv) => builder.addOne(dv)
            case Left(e)   => err = Some(e)
          }
        }
        err match {
          case Some(e) => Left(e)
          case None    => value.setOrFail(a.at, new DynamicValue.Sequence(builder.result()))
        }
      case Right(_) => Left(SchemaError.message("TransformElements path must point to a Sequence", a.at))
      case Left(e)  => Left(e)
    }

  private def applyTransformKeys(value: DynamicValue, a: MigrationAction.TransformKeys): Either[SchemaError, DynamicValue] =
    value.get(a.at).one match {
      case Right(m: DynamicValue.Map) =>
        val builder = Chunk.newBuilder[(DynamicValue, DynamicValue)]
        var err: Option[SchemaError] = None
        m.entries.foreach { case (k, v) =>
          if (err.isEmpty) a.transform.eval(k) match {
            case Right(newK) => builder.addOne((newK, v))
            case Left(e)    => err = Some(e)
          }
        }
        err match {
          case Some(e) => Left(e)
          case None    => value.setOrFail(a.at, new DynamicValue.Map(builder.result()))
        }
      case Right(_) => Left(SchemaError.message("TransformKeys path must point to a Map", a.at))
      case Left(e)  => Left(e)
    }

  private def applyTransformValues(value: DynamicValue, a: MigrationAction.TransformValues): Either[SchemaError, DynamicValue] =
    value.get(a.at).one match {
      case Right(m: DynamicValue.Map) =>
        val builder = Chunk.newBuilder[(DynamicValue, DynamicValue)]
        var err: Option[SchemaError] = None
        m.entries.foreach { case (k, v) =>
          if (err.isEmpty) a.transform.eval(v) match {
            case Right(newV) => builder.addOne((k, newV))
            case Left(e)     => err = Some(e)
          }
        }
        err match {
          case Some(e) => Left(e)
          case None    => value.setOrFail(a.at, new DynamicValue.Map(builder.result()))
        }
      case Right(_) => Left(SchemaError.message("TransformValues path must point to a Map", a.at))
      case Left(e)  => Left(e)
    }
}
