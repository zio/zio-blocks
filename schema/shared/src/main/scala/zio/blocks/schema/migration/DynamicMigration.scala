package zio.blocks.schema.migration

import zio.blocks.schema.{DynamicOptic, DynamicValue, PrimitiveValue, SchemaExpr}
import zio.blocks.schema.DynamicOptic.Node
import zio.blocks.schema.migration.MigrationError._

final case class DynamicMigration(actions: Vector[MigrationAction]) { self =>

  def apply(value: DynamicValue): Either[MigrationError, DynamicValue] =
    actions.foldLeft[Either[MigrationError, DynamicValue]](Right(value)) {
      case (Right(curr), action) => applyAction(curr, action)
      case (err, _)              => err
    }

  def ++(that: DynamicMigration): DynamicMigration =
    DynamicMigration(self.actions ++ that.actions)

  private def applyAction(root: DynamicValue, action: MigrationAction): Either[MigrationError, DynamicValue] =
    recurse(root, action.at.nodes.toList, action, action.at)

  private def recurse(
    current: DynamicValue,
    path: List[Node],
    action: MigrationAction,
    fullPath: DynamicOptic
  ): Either[MigrationError, DynamicValue] = {
    (current, path) match {
      // -----------------------------------------------------------------------
      // CASE 1: Structural Modifications on Record Fields
      // We are at the Record, and the next node is the Field target.
      // -----------------------------------------------------------------------
      case (DynamicValue.Record(fields), Node.Field(name) :: Nil) =>
        action match {
          case MigrationAction.RenameField(_, newName) =>
            val idx = fields.indexWhere(_._1 == name)
            if (idx == -1) Left(PathNotFound(fullPath, s"Field '$name' not found for rename"))
            else {
              // Fix: Use wildcard `_` for the unused key variable
              val (_, v) = fields(idx)
              Right(DynamicValue.Record(fields.updated(idx, (newName, v))))
            }

          case MigrationAction.DropField(_) =>
            if (!fields.exists(_._1 == name)) Left(PathNotFound(fullPath, s"Field '$name' not found for drop"))
            else Right(DynamicValue.Record(fields.filterNot(_._1 == name)))

          case MigrationAction.AddField(_, defaultExpr) =>
            if (fields.exists(_._1 == name)) Left(InvalidOperation(fullPath, s"Field '$name' already exists"))
            else {
              // Evaluate default using the PARENT record as input context
              evalExpr(defaultExpr, current, fullPath).map { defaultVal =>
                DynamicValue.Record(fields :+ (name -> defaultVal))
              }
            }

          // For Value Actions (Transform, etc.) pointing to a field, we descend.
          case _ =>
            val idx = fields.indexWhere(_._1 == name)
            if (idx == -1) Left(PathNotFound(fullPath, s"Field '$name' not found"))
            else {
              val (k, v) = fields(idx)
              recurse(v, Nil, action, fullPath).map { newVal =>
                DynamicValue.Record(fields.updated(idx, (k, newVal)))
              }
            }
        }

      // -----------------------------------------------------------------------
      // CASE 2: Structural Modifications on Enum Cases
      // -----------------------------------------------------------------------
      case (DynamicValue.Variant(caseName, inner), Node.Case(targetCase) :: Nil) =>
        action match {
          case MigrationAction.RenameCase(_, from, to) =>
            if (caseName == from && caseName == targetCase) Right(DynamicValue.Variant(to, inner))
            else if (caseName != targetCase) {
              // Usually strict, but for renames usually applied only if matching
              Right(current)
            } else Right(current)

          case _ =>
            if (caseName == targetCase) {
              recurse(inner, Nil, action, fullPath).map(newInner => DynamicValue.Variant(caseName, newInner))
            } else {
              Right(current) // Mismatch case, no-op
            }
        }

      // -----------------------------------------------------------------------
      // CASE 3: Traversal (Drilling down)
      // -----------------------------------------------------------------------
      case (DynamicValue.Record(fields), Node.Field(name) :: tail) =>
        val idx = fields.indexWhere(_._1 == name)
        if (idx == -1) Left(PathNotFound(fullPath, s"Field '$name' not found"))
        else {
          val (k, v) = fields(idx)
          recurse(v, tail, action, fullPath).map { newVal =>
            DynamicValue.Record(fields.updated(idx, (k, newVal)))
          }
        }

      case (DynamicValue.Sequence(elems), Node.AtIndex(i) :: tail) =>
        if (i < 0 || i >= elems.length) Left(PathNotFound(fullPath, s"Index $i out of bounds"))
        else {
          recurse(elems(i), tail, action, fullPath).map { newVal =>
            DynamicValue.Sequence(elems.updated(i, newVal))
          }
        }

      case (DynamicValue.Map(entries), Node.AtMapKey(key) :: tail) =>
        // Assuming key in Node is compatible; simplified lookup
        val idx = entries.indexWhere { case (k, _) => k == key.asInstanceOf[DynamicValue] }
        if (idx == -1) Left(PathNotFound(fullPath, s"Key $key not found"))
        else {
          val (k, v) = entries(idx)
          recurse(v, tail, action, fullPath).map { newVal =>
            DynamicValue.Map(entries.updated(idx, (k, newVal)))
          }
        }

      case (DynamicValue.Variant(name, inner), Node.Case(target) :: tail) =>
        if (name == target) {
          recurse(inner, tail, action, fullPath).map(newInner => DynamicValue.Variant(name, newInner))
        } else {
          Right(current) // No-op on mismatch
        }

      // -----------------------------------------------------------------------
      // CASE 4: Target Reached (Leaf Actions)
      // -----------------------------------------------------------------------
      case (value, Nil) =>
        action match {
          case MigrationAction.TransformValue(_, expr) =>
            evalExpr(expr, value, fullPath)

          case MigrationAction.ChangeType(_, expr) =>
            evalExpr(expr, value, fullPath)

          case MigrationAction.Optionalize(_) =>
            // Wrap in "Some" variant. ZIO Schema generic representation for Option.
            Right(DynamicValue.Variant("Some", value))

          case MigrationAction.Mandate(_, default) =>
            unwrapOption(value, default, fullPath)

          case MigrationAction.TransformElements(_, innerAction) =>
            value match {
              case DynamicValue.Sequence(elems) =>
                val results = elems.map(e => recurse(e, innerAction.at.nodes.toList, innerAction, innerAction.at))
                results.find(_.isLeft) match {
                  case Some(Left(e)) => Left(e)
                  case _             => Right(DynamicValue.Sequence(results.map(_.toOption.get)))
                }
              case _ => Left(TypeMismatch(fullPath, "Sequence", value))
            }

          case MigrationAction.TransformValues(_, innerAction) =>
            value match {
              case DynamicValue.Map(entries) =>
                val results = entries.map { case (k, v) =>
                  recurse(v, innerAction.at.nodes.toList, innerAction, innerAction.at).map(nv => (k, nv))
                }
                results.find(_.isLeft) match {
                  case Some(Left(e)) => Left(e)
                  case _             => Right(DynamicValue.Map(results.map(_.toOption.get)))
                }
              case _ => Left(TypeMismatch(fullPath, "Map", value))
            }

          case _ => Left(InvalidOperation(fullPath, s"Unexpected action at leaf: $action"))
        }

      // -----------------------------------------------------------------------
      // CASE 5: Traversal Mismatches
      // -----------------------------------------------------------------------
      case _ =>
        Left(PathNotFound(fullPath, s"Cannot traverse path $path on $current"))
    }
  }

  private def evalExpr(
    expr: SchemaExpr[?, ?],
    input: DynamicValue,
    path: DynamicOptic
  ): Either[MigrationError, DynamicValue] =
    // Unsafe cast to evaluate dynamic expression
    expr.asInstanceOf[SchemaExpr[Any, Any]].evalDynamic(input.asInstanceOf[Any]) match {
      case Right(seq) =>
        seq.headOption.toRight(CalculationError(path, "Expression produced no values"))
      case Left(check) =>
        Left(CalculationError(path, check.toString))
    }

  private def unwrapOption(
    value: DynamicValue,
    default: SchemaExpr[?, ?],
    path: DynamicOptic
  ): Either[MigrationError, DynamicValue] =
    value match {
      case DynamicValue.Variant("Some", inner)                   => Right(inner)
      case DynamicValue.Variant("None", _)                       => evalExpr(default, value, path)
      case DynamicValue.Primitive(p) if p == PrimitiveValue.Unit => evalExpr(default, value, path)
      case _                                                     => Left(TypeMismatch(path, "Option (Variant 'Some'|'None')", value))
    }
}

object DynamicMigration {
  val empty: DynamicMigration = DynamicMigration(Vector.empty)
}
