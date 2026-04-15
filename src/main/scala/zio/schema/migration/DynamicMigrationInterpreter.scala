package zio.schema.migration

import zio.schema.{DynamicValue, Schema}

private object DynamicMigrationInterpreter {

  /** Replaces the value at `optic` with the result of `expr.eval`.  
    * If the optic points to a collection element, the transformation is applied
    * to each element. */
  def transformAt(root: DynamicValue, optic: DynamicOptic, expr: SchemaExpr[?, ?]): Either[MigrationError, DynamicValue] =
    navigate(root, optic.steps).flatMap {
      case (parent, field) =>
        // `field` is the last step (a Field, Each, etc.) that we need to replace.
        // For a field we simply run the expr on the current value.
        expr.eval(???, ???) // The macro‑generated builder already pre‑filled the SchemaExpr
          .map(dv => replace(parent, field, dv))
    }

  def mandateAt(root: DynamicValue, optic: DynamicOptic, default: SchemaExpr[?, ?]): Either[MigrationError, DynamicValue] =
    navigate(root, optic.steps).flatMap {
      case (parent, field) =>
        // If the field is already present and non‑null we keep it.
        // Otherwise we evaluate the default expr and insert it.
        get(parent, field) match {
          case Some(v) => Right(root) // unchanged
          case None    =>
            default.eval(???, ???).map(dv => replace(parent, field, dv))
        }
    }

  def optionalizeAt(root: DynamicValue, optic: DynamicOptic): Either[MigrationError, DynamicValue] = {
    // Turn a required field into Option – i.e. wrap the current value in DynamicValue.Some
    navigate(root, optic.steps).flatMap {
      case (parent, field) =>
        get(parent, field) match {
          case Some(v) => Right(replace(parent, field, DynamicValue.Some(v)))
          case None    => Right(root) // already missing → stays missing (treated as None)
        }
    }
  }

  def changeTypeAt(root: DynamicValue, optic: DynamicOptic, converter: SchemaExpr[?, ?]): Either[MigrationError, DynamicValue] =
    // similar to transformAt – but the expr will be a PrimitiveConversion
    transformAt(root, optic, converter)

  def renameCase(root: DynamicValue, optic: DynamicOptic, from: String, to: String): Either[MigrationError, DynamicValue] = {
    // union is represented as a Record with a `Tag` field (type alias stored via Schema)
    // We locate the record at `optic`, replace the Tag field value.
    navigate(root, optic.steps).flatMap {
      case (parent, field) =>
        get(parent, field) match {
          case Some(DynamicValue.Record(tags)) if tags.get("Tag") == Some(DynamicValue.Primitive(from)) =>
            val updated = tags + ("Tag" -> DynamicValue.Primitive(to))
            Right(replace(parent, field, DynamicValue.Record(updated)))
          case _ => Left(MigrationError(s"RenameCase: expected case $from", optic))
        }
    }
  }

  def transformCase(root: DynamicValue, optic: DynamicOptic, actions: Vector[MigrationAction]): Either[MigrationError, DynamicValue] = {
    // First we locate the union value, then we feed the inner record to each action.
    navigate(root, optic.steps).flatMap {
      case (parent, field) =>
        get(parent, field) match {
          case Some(v) =>
            actions.foldLeft[Either[MigrationError, DynamicValue]](Right(v)) { (acc, act) =>
              acc.flatMap(act.apply)
            }.map(transformed => replace(parent, field, transformed))
          case None => Left(MigrationError(s"TransformCase: missing case record", optic))
        }
    }
  }

  def transformElements(root: DynamicValue, optic: DynamicOptic, expr: SchemaExpr[?, ?]): Either[MigrationError, DynamicValue] = {
    navigate(root, optic.steps).flatMap {
      case (parent, OpticStep.Each()) =>
        get(parent, OpticStep.Each()) match {
          case Some(DynamicValue.Sequence(elems)) =>
            elems.foldLeft[Either[MigrationError, Chunk[DynamicValue]]](Right(ChunkBuilder.make[DynamicValue]())) {
              case (acc, elem) =>
                acc.flatMap { ch =>
                  expr.eval(???, ???).map(dv => ch :+ dv) // the expr sees the element as `In`
                }
            }.map(seq => replace(parent, OpticStep.Each(), DynamicValue.Sequence(seq.toList)))
          case _ => Left(MigrationError("TransformElements: not a collection", optic))
        }
      case _ => Left(MigrationError("TransformElements: optic does not end with .each", optic))
    }
  }

  def transformMapKeys(root: DynamicValue, optic: DynamicOptic, expr: SchemaExpr[?, ?]): Either[MigrationError, DynamicValue] =
    // analogous to transformElements but applied to map keys
    ???

  def transformMapValues(root: DynamicValue, optic: DynamicOptic, expr: SchemaExpr[?, ?]): Either[MigrationError, DynamicValue] =
    // analogous to transformElements but applied to map values
    ???

  /** Utility: navigate to the *parent* of the target location.
    * Returns (parentValue, lastStep) where `lastStep` is the step that points to the field we want
    * to read/modify.
    */
  private def navigate(root: DynamicValue, steps: Vector[OpticStep]): Either[MigrationError, (DynamicValue, OpticStep)] = {
    steps match {
      case Nil => Left(MigrationError("Empty optic", DynamicOptic.root))
      case allButLast :+ last =>
        // Walk all-but-last
        allButLast.foldLeft[Either[MigrationError, DynamicValue]](Right(root)) {
          case (acc, step) => acc.flatMap(go(step))
        }.map(parent => (parent, last))
    }
  }

  private def go(step: OpticStep)(curr: DynamicValue): Either[MigrationError, DynamicValue] = step match {
    case OpticStep.Field(name) =>
      curr match {
        case DynamicValue.Record(fields) =>
          fields.get(name) match {
            case Some(v) => Right(v)
            case None    => Left(MigrationError(s"Field $name not found", DynamicOptic.root / step))
          }
        case other => Left(MigrationError(s"Expected a Record at $step, got $other", DynamicOptic.root / step))
      }

    case OpticStep.Each() =>
      curr match {
        case DynamicValue.Sequence(seq) => Right(DynamicValue.Sequence(seq))
        case other                    => Left(MigrationError(s".each expected a collection", DynamicOptic.root / step))
      }

    case OpticStep.When(tag) =>
      // unions: treat as a record with a Tag field
      curr match {
        case DynamicValue.Record(fields) =>
          fields.get("Tag") match {
            case Some(DynamicValue.Primitive(t)) if t == tag => Right(curr)
            case _ => Left(MigrationError(s".when[$tag] not matching", DynamicOptic.root / step))
          }
        case other => Left(MigrationError(s".when expects a union record", DynamicOptic.root / step))
      }

    case OpticStep.Key()   => ??? // future‑proof placeholder
    case OpticStep.Value() => ??? // future‑proof placeholder
  }

  private def get(parent: DynamicValue, step: OpticStep): Option[DynamicValue] = step match {
    case OpticStep.Field(name) => parent match {
      case DynamicValue.Record(fields) => fields.get(name)
      case _                           => None
    }
    case OpticStep.Each() => parent match {
      case DynamicValue.Sequence(seq) => Some(DynamicValue.Sequence(seq))
      case _                         => None
    }
    case _ => None
  }

  private def replace(parent: DynamicValue, step: OpticStep, newValue: DynamicValue): DynamicValue = step match {
    case OpticStep.Field(name) => parent match {
      case DynamicValue.Record(fields) => DynamicValue.Record(fields + (name -> newValue))
      case _ => parent // unreachable – guarded before
    }
    case OpticStep.Each() => DynamicValue.Sequence(newValue.asInstanceOf[DynamicValue.Sequence].values)
    case _ => parent
  }

  private def fieldName(optic: DynamicOptic): String = optic.steps.last match {
    case OpticStep.Field(name) => name
    case other                 => throw new IllegalArgumentException(s"Expected field step, got $other")
  }
}
