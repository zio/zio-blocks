package zio.schema.migration

import zio.blocks.schema._
import zio.blocks.schema.DynamicOptic.Node
import zio.schema.migration.MigrationError._

final case class DynamicMigration(actions: Vector[MigrationAction]) { self =>

  def apply(value: DynamicValue): Either[MigrationError, DynamicValue] =
    actions.foldLeft[Either[MigrationError, DynamicValue]](Right(value)) { (acc, action) =>
      acc.flatMap(v => DynamicMigration.applyAction(action, v))
    }

  def ++(that: DynamicMigration): DynamicMigration =
    DynamicMigration(self.actions ++ that.actions)

  def reverse: DynamicMigration =
    DynamicMigration(actions.reverse.map(_.reverse))
}

object DynamicMigration {

  def applyAction(action: MigrationAction, value: DynamicValue): Either[MigrationError, DynamicValue] = {
    action match {
      case MigrationAction.AddField(at, default) =>
        evalExpr(default, value).flatMap { defaultValue =>
          insertAt(value, at, defaultValue)
        }

      case MigrationAction.DropField(at, _) =>
        deleteAt(value, at)

      case MigrationAction.Rename(at, to) =>
        renameAt(value, at, to)

      case MigrationAction.TransformValue(at, transform) =>
        transformAt(value, at, transform)

      case MigrationAction.Mandate(at, _) =>
        transformAtSimple(
          value,
          at,
          {
            case DynamicValue.Variant("Some", v) => Right(v)
            case DynamicValue.Variant("None", _) => Left(EvaluationError(at, "Mandatory field is missing (None)"))
            case other                           => Left(EvaluationError(at, s"Expected Option (Variant Some/None), got $other"))
          }
        )

      case MigrationAction.Optionalize(at) =>
        transformAtSimple(value, at, v => Right(DynamicValue.Variant("Some", v)))

      case MigrationAction.Join(at, sourcePaths, combiner) =>
        val sourceValues = sourcePaths.map(p => getAt(value, p.nodes))
        sourceValues.find(_.isLeft) match {
          case Some(Left(e)) => Left(e)
          case _             =>
            val values = sourceValues.map(_.toOption.flatten)
            if (values.contains(None)) Left(EvaluationError(at, "Join failed: One or more source paths missing"))
            else {
              val combinedInput =
                DynamicValue.Record(values.map(_.get).zipWithIndex.map { case (v, i) => s"_${i + 1}" -> v })
              evalExpr(combiner, combinedInput).flatMap(res => insertAt(value, at, res))
            }
        }

      case MigrationAction.Split(at, targetPaths, splitter) =>
        getAt(value, at.nodes).flatMap {
          case None        => Left(EvaluationError(at, "Split source missing"))
          case Some(input) =>
            evalExpr(splitter, input).flatMap {
              case DynamicValue.Record(fields) =>
                // Distribute fields to targetPaths (assuming positional matching _1, _2...)
                targetPaths.zipWithIndex.foldLeft[Either[MigrationError, DynamicValue]](Right(value)) {
                  case (acc, (path, idx)) =>
                    acc.flatMap { curr =>
                      fields.find(_._1 == s"_${idx + 1}") match {
                        case Some((_, v)) => insertAt(curr, path, v)
                        case None         =>
                          Left(EvaluationError(at, s"Splitter result missing field _${idx + 1} for target $path"))
                      }
                    }
                }
              case other =>
                Left(
                  EvaluationError(at, s"Splitter must return a Record with positional fields (_1, _2...), got $other")
                )
            }
        }

      case MigrationAction.ChangeType(at, converter) =>
        transformAtSimple(value, at, v => evalExpr(converter, v))

      case MigrationAction.RenameCase(at, from, to) =>
        transformAtSimple(
          value,
          at,
          {
            case DynamicValue.Variant(`from`, inner) => Right(DynamicValue.Variant(to, inner))
            case other                               => Right(other)
          }
        )

      case MigrationAction.TransformCase(at, actions) =>
        updateAt(value, at.nodes, v => actions.apply(v), insert = false)

      case MigrationAction.TransformElements(at, migration) =>
        updateAt(
          value,
          at.nodes,
          {
            case DynamicValue.Sequence(elems) =>
              val res = elems.map(e => migration.apply(e))
              res.find(_.isLeft) match {
                case Some(Left(e)) => Left(e)
                case _             => Right(DynamicValue.Sequence(res.map(_.getOrElse(throw new Exception("Unexpected")))))
              }
            case other => Left(EvaluationError(at, s"Expected Sequence, got $other"))
          },
          insert = false
        )

      case MigrationAction.TransformKeys(at, migration) =>
        updateAt(
          value,
          at.nodes,
          {
            case DynamicValue.Map(entries) =>
              val res = entries.map { case (k, v) =>
                migration.apply(k).map(_ -> v)
              }
              res.find(_.isLeft) match {
                case Some(Left(e)) => Left(e)
                case _             =>
                  val newEntries = res.map(_.getOrElse(throw new Exception("Unexpected")))
                  Right(DynamicValue.Map(newEntries))
              }
            case other => Left(EvaluationError(at, s"Expected Map, got $other"))
          },
          insert = false
        )

      case MigrationAction.TransformValues(at, migration) =>
        updateAt(
          value,
          at.nodes,
          {
            case DynamicValue.Map(entries) =>
              val res = entries.map { case (k, v) =>
                migration.apply(v).map(k -> _)
              }
              res.find(_.isLeft) match {
                case Some(Left(e)) => Left(e)
                case _             =>
                  val newEntries = res.map(_.getOrElse(throw new Exception("Unexpected")))
                  Right(DynamicValue.Map(newEntries))
              }
            case other => Left(EvaluationError(at, s"Expected Map, got $other"))
          },
          insert = false
        )

    }
  }

  // --- Traversal Logic ---

  private def insertAt(
    root: DynamicValue,
    path: DynamicOptic,
    valueToInsert: DynamicValue
  ): Either[MigrationError, DynamicValue] =
    updateAt(root, path.nodes, _ => Right(valueToInsert), insert = true)

  private def deleteAt(root: DynamicValue, path: DynamicOptic): Either[MigrationError, DynamicValue] =
    deleteAt(root, path.nodes)

  private def renameAt(root: DynamicValue, path: DynamicOptic, newName: String): Either[MigrationError, DynamicValue] =
    updateParent(
      root,
      path.nodes,
      {
        case (DynamicValue.Record(fields), name) =>
          val idx = fields.indexWhere(_._1 == name)
          if (idx == -1) Left(EvaluationError(path, s"Field $name not found for rename"))
          else Right(DynamicValue.Record(fields.updated(idx, newName -> fields(idx)._2)))
        case _ => Left(EvaluationError(path, "Rename only valid for Record fields"))
      }
    )

  private def transformAt(
    root: DynamicValue,
    path: DynamicOptic,
    expr: SchemaExpr[_, _]
  ): Either[MigrationError, DynamicValue] =
    updateAt(root, path.nodes, v => evalExpr(expr, v), insert = false)

  private def transformAtSimple(
    root: DynamicValue,
    path: DynamicOptic,
    f: DynamicValue => Either[MigrationError, DynamicValue]
  ): Either[MigrationError, DynamicValue] =
    updateAt(root, path.nodes, f, insert = false)

  private def updateAt(
    value: DynamicValue,
    nodes: IndexedSeq[Node],
    update: DynamicValue => Either[MigrationError, DynamicValue],
    insert: Boolean
  ): Either[MigrationError, DynamicValue] =
    if (nodes.isEmpty) update(value)
    else {
      val head = nodes.head
      val tail = nodes.tail

      (value, head) match {
        case (DynamicValue.Record(fields), Node.Field(name)) =>
          val idx = fields.indexWhere(_._1 == name)
          if (idx >= 0) {
            updateAt(fields(idx)._2, tail, update, insert).map { newValue =>
              DynamicValue.Record(fields.updated(idx, name -> newValue))
            }
          } else if (insert && tail.isEmpty) {
            // Insert new field
            update(DynamicValue.Record(Vector.empty)).map { newValue =>
              DynamicValue.Record(fields :+ (name -> newValue))
            }
          } else {
            if (insert) {
              // Path doesn't exist, create it if we are inserting deep?
              // For now assuming parent exists.
              Left(EvaluationError(DynamicOptic(Vector(head)), s"Path segment $name not found"))
            } else {
              Left(EvaluationError(DynamicOptic(Vector(head)), s"Field $name not found"))
            }
          }

        case (DynamicValue.Variant(caseName, inner), Node.Case(expectedName)) =>
          if (caseName == expectedName) {
            updateAt(inner, tail, update, insert).map { newValue =>
              DynamicValue.Variant(caseName, newValue)
            }
          } else {
            // If case doesn't match, we ignore? Or fail?
            // Usually selector _.caseOf implies filtering.
            // If we are transforming, and it's a different case, does the transform apply?
            // "_.country.when[UK]"
            // If current value is US, transformation is skipped?
            Right(value)
          }

        case (DynamicValue.Sequence(elems), Node.AtIndex(index)) =>
          if (index >= 0 && index < elems.length) {
            updateAt(elems(index), tail, update, insert).map { newValue =>
              DynamicValue.Sequence(elems.updated(index, newValue))
            }
          } else {
            Left(EvaluationError(DynamicOptic(Vector(head)), s"Index $index out of bounds"))
          }

        case (DynamicValue.Sequence(values), Node.Elements) =>
          val results         = values.map(v => updateAt(v, tail, update, insert))
          val (lefts, rights) = results.partitionMap(identity)
          if (lefts.nonEmpty) Left(lefts.head) // Fail on first error? Or return all?
          else Right(DynamicValue.Sequence(rights))

        case _ => Left(EvaluationError(DynamicOptic(Vector(head)), s"Unsupported traversal: $value at $head"))
      }
    }

  private def deleteAt(value: DynamicValue, nodes: IndexedSeq[Node]): Either[MigrationError, DynamicValue] =
    if (nodes.isEmpty) Right(value) // Deleting root? Not supported usually return what?
    else {
      val head = nodes.head
      val tail = nodes.tail

      if (tail.isEmpty) {
        // Leaf - delete it
        (value, head) match {
          case (DynamicValue.Record(fields), Node.Field(name)) =>
            Right(DynamicValue.Record(fields.filterNot(_._1 == name)))
          case (DynamicValue.Sequence(elems), Node.AtIndex(index)) =>
            if (index >= 0 && index < elems.length) {
              val (l, r) = elems.splitAt(index)
              Right(DynamicValue.Sequence(l ++ r.tail))
            } else Left(EvaluationError(DynamicOptic(Vector(head)), s"Index $index out of bounds"))
          case _ => Left(EvaluationError(DynamicOptic(Vector(head)), s"Cannot delete $head from $value"))
        }
      } else {
        // Standard traversal
        (value, head) match {
          case (DynamicValue.Record(fields), Node.Field(name)) =>
            val idx = fields.indexWhere(_._1 == name)
            if (idx >= 0) {
              deleteAt(fields(idx)._2, tail).map { newValue =>
                DynamicValue.Record(fields.updated(idx, name -> newValue))
              }
            } else Left(EvaluationError(DynamicOptic(Vector(head)), s"Field $name not found"))

          case (DynamicValue.Variant(n, v), Node.Case(expected)) =>
            if (n == expected) {
              deleteAt(v, tail).map(newValue => DynamicValue.Variant(n, newValue))
            } else Right(value)

          case _ => Left(EvaluationError(DynamicOptic(Vector(head)), s"Traversal failed at $head"))
        }
      }
    }

  private def updateParent(
    value: DynamicValue,
    nodes: IndexedSeq[Node],
    f: (DynamicValue, String) => Either[MigrationError, DynamicValue]
  ): Either[MigrationError, DynamicValue] =
    // For renaming, we need to reach the PARENT of the target, and modify the key.
    // If nodes is empty, error.
    // If nodes is length 1: Field(name), we apply f(value, name).
    if (nodes.isEmpty) Left(ValidatingError("Cannot rename root"))
    else {
      val last        = nodes.last
      val parentNodes = nodes.init

      last match {
        case Node.Field(name) =>
          updateAt(value, parentNodes, parent => f(parent, name), insert = false)
        case _ => Left(ValidatingError("Can only rename fields"))
      }
    }

  // --- Expression Evaluation ---

  def evalExpr(expr: SchemaExpr[_, _], input: DynamicValue): Either[MigrationError, DynamicValue] = {
    import SchemaExpr._

    expr match {
      case AccessDynamic(path) =>
        getAt(input, path.nodes).map(_.getOrElse(DynamicValue.Primitive(PrimitiveValue.Unit)))

      case Literal(value, schema) =>
        // l.value is A, l.schema is Schema[A].
        // We can safely cast to Schema[Any] and Any because toDynamicValue is generally safe given the pair is consistent.
        Right(schema.asInstanceOf[Schema[Any]].toDynamicValue(value.asInstanceOf[Any]))

      case DefaultValue(schema) =>
        schema.getDefaultValue match {
          case Some(v) => Right(schema.asInstanceOf[Schema[Any]].toDynamicValue(v.asInstanceOf[Any]))
          case None    =>
            Left(
              EvaluationError(
                zio.blocks.schema.DynamicOptic(Vector.empty),
                "DefaultValue expression failed: No default value in schema"
              )
            )
        }

      case l: SchemaExpr.Logical[_] =>
        // Evaluate left/right recursively
        for {
          lVal <- evalExpr(l.left, input)
          rVal <- evalExpr(l.right, input)
        } yield {
          (lVal, rVal) match {
            case (
                  DynamicValue.Primitive(PrimitiveValue.Boolean(a)),
                  DynamicValue.Primitive(PrimitiveValue.Boolean(b))
                ) =>
              val res = if (l.operator == LogicalOperator.And) a && b else a || b
              DynamicValue.Primitive(PrimitiveValue.Boolean(res))
            case _ => throw new RuntimeException("Type mismatch in Logical expr")
          }
        }

      case _ => Left(ValidatingError(s"Unsupported SchemaExpr type: ${expr.getClass.getName}"))
    }
  }

  private def getAt(value: DynamicValue, nodes: IndexedSeq[Node]): Either[MigrationError, Option[DynamicValue]] =
    if (nodes.isEmpty) Right(Some(value))
    else {
      val head = nodes.head
      val tail = nodes.tail
      (value, head) match {
        case (DynamicValue.Record(fields), Node.Field(name)) =>
          fields.find(_._1 == name).map(_._2) match {
            case Some(v) => getAt(v, tail)
            case None    => Right(None)
          }
        case (DynamicValue.Variant(n, v), Node.Case(expected)) =>
          if (n == expected) getAt(v, tail)
          else Right(None)
        case _ => Left(EvaluationError(DynamicOptic(Vector(head)), s"Cannot get at $head"))
      }
    }
  implicit val schema: zio.blocks.schema.Schema[DynamicMigration] =
    new zio.blocks.schema.Schema(
      new zio.blocks.schema.Reflect.Wrapper(
        zio.blocks.schema.Schema.vector(MigrationAction.schema).reflect,
        zio.blocks.schema
          .TypeName(zio.blocks.schema.Namespace(List("zio", "schema", "migration"), Nil), "DynamicMigration", Nil),
        None,
        new zio.blocks.schema.binding.Binding.Wrapper(
          (v: Vector[MigrationAction]) => Right(DynamicMigration(v)),
          (m: DynamicMigration) => m.actions
        )
      )
    )
}
