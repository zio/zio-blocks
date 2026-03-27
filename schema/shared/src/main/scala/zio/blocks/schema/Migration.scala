package zio.blocks.schema

import zio.blocks.chunk.Chunk
import scala.collection.immutable.ListMap

sealed trait MigrationError

object MigrationError {
  case class PathNotFound(at: DynamicOptic)                      extends MigrationError
  case class TransformationFailed(at: DynamicOptic, msg: String) extends MigrationError
}

sealed trait MigrationAction {
  def at: DynamicOptic
  def reverse: MigrationAction
}

object MigrationAction {
  case class AddField(at: DynamicOptic, default: SchemaExpr[_, _]) extends MigrationAction {
    def reverse: MigrationAction = DropField(at, default)
  }

  case class DropField(at: DynamicOptic, defaultForReverse: SchemaExpr[_, _]) extends MigrationAction {
    def reverse: MigrationAction = AddField(at, defaultForReverse)
  }

  case class Rename(at: DynamicOptic, from: String, to: String) extends MigrationAction {
    def reverse: MigrationAction = Rename(at, to, from)
  }

  case class TransformValue(
    at: DynamicOptic,
    transform: SchemaExpr[_, _]
  ) extends MigrationAction {
    def reverse: MigrationAction = this
  }

  case class Mandate(at: DynamicOptic, default: SchemaExpr[_, _]) extends MigrationAction {
    def reverse: MigrationAction = Optionalize(at, default)
  }

  case class Optionalize(at: DynamicOptic, defaultForReverse: SchemaExpr[_, _]) extends MigrationAction {
    def reverse: MigrationAction = Mandate(at, defaultForReverse)
  }

  case class Join(
    at: DynamicOptic,
    sourcePaths: Vector[DynamicOptic],
    combiner: SchemaExpr[_, _],
    splitter: SchemaExpr[_, _]
  ) extends MigrationAction {
    def reverse: MigrationAction = Split(at, sourcePaths, splitter, combiner)
  }

  case class Split(
    at: DynamicOptic,
    targetPaths: Vector[DynamicOptic],
    splitter: SchemaExpr[_, _],
    combiner: SchemaExpr[_, _]
  ) extends MigrationAction {
    def reverse: MigrationAction = Join(at, targetPaths, combiner, splitter)
  }

  case class ChangeType(at: DynamicOptic, converter: SchemaExpr[_, _]) extends MigrationAction {
    def reverse: MigrationAction = this
  }

  case class RenameCase(at: DynamicOptic, from: String, to: String) extends MigrationAction {
    def reverse: MigrationAction = RenameCase(at, to, from)
  }

  case class TransformCase(at: DynamicOptic, actions: Vector[MigrationAction]) extends MigrationAction {
    def reverse: MigrationAction = TransformCase(at, actions.map(_.reverse).reverse)
  }

  case class TransformElements(at: DynamicOptic, transform: SchemaExpr[_, _]) extends MigrationAction {
    def reverse: MigrationAction = this
  }

  case class TransformKeys(at: DynamicOptic, transform: SchemaExpr[_, _]) extends MigrationAction {
    def reverse: MigrationAction = this
  }

  case class TransformValues(at: DynamicOptic, transform: SchemaExpr[_, _]) extends MigrationAction {
    def reverse: MigrationAction = this
  }
}

private[schema] sealed trait Trampoline[+A] {
  @scala.annotation.tailrec
  final def run: A = this match {
    case Trampoline.Done(a)        => a
    case Trampoline.Suspend(thunk) => thunk().run
    case Trampoline.FlatMap(t, f)  =>
      t match {
        case Trampoline.Done(a)        => f(a).run
        case Trampoline.Suspend(thunk) =>
          val next: Trampoline[A] = Trampoline.FlatMap(thunk(), f)
          next.run
        case fm2: Trampoline.FlatMap[_, _] =>
          val t2                  = fm2.t.asInstanceOf[Trampoline[Any]]
          val f2                  = fm2.f.asInstanceOf[Any => Trampoline[Any]]
          val f1                  = f.asInstanceOf[Any => Trampoline[A]]
          val next: Trampoline[A] = Trampoline.FlatMap(t2, (x: Any) => Trampoline.FlatMap(f2(x), f1))
          next.run
      }
  }
  def flatMap[B](f: A => Trampoline[B]): Trampoline[B] = Trampoline.FlatMap(this, f)
  def map[B](f: A => B): Trampoline[B]                 = flatMap(a => Trampoline.Done(f(a)))
}

private[schema] object Trampoline {
  case class Done[A](value: A)                                      extends Trampoline[A]
  case class Suspend[A](thunk: () => Trampoline[A])                 extends Trampoline[A]
  case class FlatMap[A, B](t: Trampoline[A], f: A => Trampoline[B]) extends Trampoline[B]

  def done[A](value: A): Trampoline[A]                   = Done(value)
  def suspend[A](thunk: => Trampoline[A]): Trampoline[A] = Suspend(() => thunk)

  def collectAll[A](ts: Iterable[Trampoline[A]]): Trampoline[List[A]] =
    ts.foldLeft(done(List.empty[A])) { (acc, t) =>
      for {
        list <- acc
        a    <- t
      } yield a :: list
    }.map(_.reverse)
}

case class DynamicMigration(actions: Vector[MigrationAction]) {
  def ++(that: DynamicMigration): DynamicMigration = DynamicMigration(actions ++ that.actions)

  def reverse: DynamicMigration = DynamicMigration(actions.map(_.reverse).reverse)

  def apply(value: DynamicValue): Either[MigrationError, DynamicValue] =
    actions.foldLeft[Either[MigrationError, DynamicValue]](Right(value)) { (acc, action) =>
      acc.flatMap(v => applyActionSafe(v, action.at.nodes.toList, action).run)
    }

  private def evalExpr(expr: SchemaExpr[_, _], input: Any, at: DynamicOptic): Either[MigrationError, DynamicValue] =
    scala.util.Try(expr.asInstanceOf[SchemaExpr[Any, Any]].evalDynamic(input)).toEither match {
      case Right(Right(seq)) if seq.nonEmpty => Right(seq.head)
      case Right(Right(_))                   => Left(MigrationError.TransformationFailed(at, "Expression evaluated to empty Sequence"))
      case Right(Left(err))                  => Left(MigrationError.TransformationFailed(at, err.toString))
      case Left(ex)                          => Left(MigrationError.TransformationFailed(at, ex.getMessage))
    }

  private def applyActionSafe(
    current: DynamicValue,
    nodes: List[DynamicOptic.Node],
    action: MigrationAction
  ): Trampoline[Either[MigrationError, DynamicValue]] = Trampoline.suspend {
    nodes match {
      case Nil =>
        action match {
          case MigrationAction.Rename(_, from, to) =>
            current match {
              case DynamicValue.Record(fields) =>
                val lm = ListMap.from(fields)
                if (!lm.contains(from)) Trampoline.done(Left(MigrationError.PathNotFound(action.at)))
                else {
                  val newLm = fields.foldLeft(ListMap.empty[String, DynamicValue]) {
                    case (acc, (k, value)) if k == from => acc + (to -> value)
                    case (acc, (k, value))              => acc + (k  -> value)
                  }
                  Trampoline.done(Right(DynamicValue.Record(Chunk.fromIterable(newLm))))
                }
              case _ => Trampoline.done(Left(MigrationError.TransformationFailed(action.at, "Expected Record")))
            }

          case rename: MigrationAction.RenameCase =>
            current match {
              case DynamicValue.Variant(caseName, caseValue) =>
                if (caseName == rename.from) Trampoline.done(Right(DynamicValue.Variant(rename.to, caseValue)))
                else Trampoline.done(Right(current))
              case _ => Trampoline.done(Left(MigrationError.TransformationFailed(action.at, "Expected Variant")))
            }

          case MigrationAction.TransformCase(_, caseActions) =>
            Trampoline.done(DynamicMigration(caseActions).apply(current))

          case MigrationAction.Mandate(_, default) =>
            current match {
              case DynamicValue.Variant("None", _) =>
                evalExpr(default, null.asInstanceOf[Any], action.at) match {
                  case Right(defaultValue) => Trampoline.done(Right(defaultValue))
                  case Left(_)             =>
                    Trampoline.done(
                      Left(
                        MigrationError.TransformationFailed(
                          action.at,
                          s"Mandated value is missing (encountered None) and default failed to evaluate."
                        )
                      )
                    )
                }
              case DynamicValue.Variant("Some", v) => Trampoline.done(Right(v))
              case dyn                             => Trampoline.done(Right(dyn))
            }

          case MigrationAction.Optionalize(_, _) =>
            current match {
              case DynamicValue.Null => Trampoline.done(Right(DynamicValue.Variant("None", DynamicValue.Null)))
              case _                 => Trampoline.done(Right(DynamicValue.Variant("Some", current)))
            }

          case MigrationAction.TransformValue(_, transform) =>
            evalExpr(transform, current, action.at) match {
              case Right(res) => Trampoline.done(Right(res))
              case Left(err)  => Trampoline.done(Left(err))
            }

          case MigrationAction.ChangeType(_, converter) =>
            evalExpr(converter, current, action.at) match {
              case Right(res) => Trampoline.done(Right(res))
              case Left(err)  => Trampoline.done(Left(err))
            }

          case MigrationAction.TransformElements(_, transform) =>
            current match {
              case DynamicValue.Sequence(values) =>
                Trampoline.collectAll(values.map(v => Trampoline.done(evalExpr(transform, v, action.at)))).map {
                  results =>
                    results.find(_.isLeft) match {
                      case Some(err) =>
                        Left(err.swap.getOrElse(MigrationError.TransformationFailed(action.at, "Unknown error")))
                      case None =>
                        Right(DynamicValue.Sequence(Chunk.fromIterable(results.map(_.getOrElse(DynamicValue.Null)))))
                    }
                }
              case _ =>
                Trampoline.done(
                  Left(MigrationError.TransformationFailed(action.at, "Expected Sequence for TransformElements"))
                )
            }

          case MigrationAction.TransformKeys(_, transform) =>
            current match {
              case DynamicValue.Map(entries) =>
                Trampoline
                  .collectAll(entries.map { case (k, v) =>
                    Trampoline.done(evalExpr(transform, k, action.at).map(res => (res, v)))
                  })
                  .map { results =>
                    results.find(_.isLeft) match {
                      case Some(err) =>
                        Left(err.swap.getOrElse(MigrationError.TransformationFailed(action.at, "Unknown error")))
                      case None =>
                        Right(
                          DynamicValue
                            .Map(Chunk.fromIterable(results.map(_.getOrElse((DynamicValue.Null, DynamicValue.Null)))))
                        )
                    }
                  }
              case _ =>
                Trampoline.done(Left(MigrationError.TransformationFailed(action.at, "Expected Map for TransformKeys")))
            }

          case MigrationAction.TransformValues(_, transform) =>
            current match {
              case DynamicValue.Map(entries) =>
                Trampoline
                  .collectAll(entries.map { case (k, v) =>
                    Trampoline.done(evalExpr(transform, v, action.at).map(res => (k, res)))
                  })
                  .map { results =>
                    results.find(_.isLeft) match {
                      case Some(err) =>
                        Left(err.swap.getOrElse(MigrationError.TransformationFailed(action.at, "Unknown error")))
                      case None =>
                        Right(
                          DynamicValue
                            .Map(Chunk.fromIterable(results.map(_.getOrElse((DynamicValue.Null, DynamicValue.Null)))))
                        )
                    }
                  }
              case _ =>
                Trampoline.done(
                  Left(MigrationError.TransformationFailed(action.at, "Expected Map for TransformValues"))
                )
            }

          case MigrationAction.Join(_, _, combiner, _) =>
            evalExpr(combiner, current, action.at) match {
              case Right(res) => Trampoline.done(Right(res))
              case Left(err)  => Trampoline.done(Left(err))
            }

          case MigrationAction.Split(_, _, splitter, _) =>
            evalExpr(splitter, current, action.at) match {
              case Right(res) => Trampoline.done(Right(res))
              case Left(err)  => Trampoline.done(Left(err))
            }

          case _ => Trampoline.done(Right(current))
        }

      case DynamicOptic.Node.Field(name) :: Nil
          if action.isInstanceOf[MigrationAction.AddField] ||
            action.isInstanceOf[MigrationAction.DropField] =>
        current match {
          case DynamicValue.Record(fields) =>
            val lm = ListMap.from(fields)
            action match {
              case MigrationAction.AddField(_, default) =>
                if (lm.contains(name))
                  Trampoline.done(Left(MigrationError.TransformationFailed(action.at, s"Field $name already exists")))
                else {
                  evalExpr(default, null.asInstanceOf[Any], action.at) match {
                    case Right(defaultValue) =>
                      Trampoline.done(Right(DynamicValue.Record(Chunk.fromIterable(lm + (name -> defaultValue)))))
                    case Left(err) => Trampoline.done(Left(err))
                  }
                }
              case MigrationAction.DropField(_, _) =>
                if (!lm.contains(name)) Trampoline.done(Left(MigrationError.PathNotFound(action.at)))
                else Trampoline.done(Right(DynamicValue.Record(Chunk.fromIterable(lm - name))))
              case _ => Trampoline.done(Left(MigrationError.TransformationFailed(action.at, "Unexpected Action")))
            }
          case _ => Trampoline.done(Left(MigrationError.TransformationFailed(action.at, "Expected Record")))
        }

      case DynamicOptic.Node.Field(name) :: tail =>
        current match {
          case DynamicValue.Record(fields) =>
            val lm = ListMap.from(fields)
            lm.get(name) match {
              case Some(v) =>
                applyActionSafe(v, tail, action).map {
                  case Right(transformed) =>
                    val updatedFields = fields.map {
                      case (`name`, _) => (name, transformed)
                      case other       => other
                    }
                    Right(DynamicValue.Record(updatedFields))
                  case Left(err) => Left(err)
                }
              case None => Trampoline.done(Left(MigrationError.PathNotFound(action.at)))
            }
          case _ => Trampoline.done(Left(MigrationError.TransformationFailed(action.at, "Expected Record")))
        }

      case DynamicOptic.Node.Case(name) :: tail =>
        current match {
          case DynamicValue.Variant(caseName, caseValue) =>
            if (caseName == name) {
              applyActionSafe(caseValue, tail, action).map {
                case Right(res) => Right(DynamicValue.Variant(caseName, res))
                case Left(err)  => Left(err)
              }
            } else Trampoline.done(Right(current))
          case _ => Trampoline.done(Left(MigrationError.TransformationFailed(action.at, "Expected Variant")))
        }

      case DynamicOptic.Node.AtIndex(index) :: tail =>
        current match {
          case DynamicValue.Sequence(values) =>
            if (index < 0 || index >= values.length) Trampoline.done(Left(MigrationError.PathNotFound(action.at)))
            else
              applyActionSafe(values(index), tail, action).map {
                case Right(res) => Right(DynamicValue.Sequence(values.updated(index, res)))
                case Left(err)  => Left(err)
              }
          case _ => Trampoline.done(Left(MigrationError.TransformationFailed(action.at, "Expected Sequence")))
        }

      case DynamicOptic.Node.Elements :: tail =>
        current match {
          case DynamicValue.Sequence(values) =>
            Trampoline.collectAll(values.map(v => applyActionSafe(v, tail, action))).map { results =>
              results.find(_.isLeft) match {
                case Some(err) =>
                  Left(err.swap.getOrElse(MigrationError.TransformationFailed(action.at, "Unknown error")))
                case None =>
                  Right(DynamicValue.Sequence(Chunk.fromIterable(results.map(_.getOrElse(DynamicValue.Null)))))
              }
            }
          case _ => Trampoline.done(Left(MigrationError.TransformationFailed(action.at, "Expected Sequence")))
        }

      case DynamicOptic.Node.MapValues :: tail =>
        current match {
          case DynamicValue.Map(entries) =>
            Trampoline
              .collectAll(entries.map { case (k, v) => applyActionSafe(v, tail, action).map(_.map(res => (k, res))) })
              .map { results =>
                results.find(_.isLeft) match {
                  case Some(err) =>
                    Left(err.swap.getOrElse(MigrationError.TransformationFailed(action.at, "Unknown error")))
                  case None =>
                    Right(
                      DynamicValue
                        .Map(Chunk.fromIterable(results.map(_.getOrElse((DynamicValue.Null, DynamicValue.Null)))))
                    )
                }
              }
          case _ => Trampoline.done(Left(MigrationError.TransformationFailed(action.at, "Expected Map")))
        }

      case DynamicOptic.Node.MapKeys :: tail =>
        current match {
          case DynamicValue.Map(entries) =>
            Trampoline
              .collectAll(entries.map { case (k, v) => applyActionSafe(k, tail, action).map(_.map(res => (res, v))) })
              .map { results =>
                results.find(_.isLeft) match {
                  case Some(err) =>
                    Left(err.swap.getOrElse(MigrationError.TransformationFailed(action.at, "Unknown error")))
                  case None =>
                    Right(
                      DynamicValue
                        .Map(Chunk.fromIterable(results.map(_.getOrElse((DynamicValue.Null, DynamicValue.Null)))))
                    )
                }
              }
          case _ => Trampoline.done(Left(MigrationError.TransformationFailed(action.at, "Expected Map")))
        }

      case DynamicOptic.Node.AtMapKey(kOptic) :: tail =>
        current match {
          case DynamicValue.Map(entries) =>
            entries.find(_._1 == kOptic) match {
              case Some((_, v)) =>
                applyActionSafe(v, tail, action).map {
                  case Right(res) =>
                    val newEntries = entries.map {
                      case (ek, _) if ek == kOptic => (ek, res)
                      case other                   => other
                    }
                    Right(DynamicValue.Map(newEntries))
                  case Left(err) => Left(err)
                }
              case None => Trampoline.done(Left(MigrationError.PathNotFound(action.at)))
            }
          case _ => Trampoline.done(Left(MigrationError.TransformationFailed(action.at, "Expected Map")))
        }

      case _ :: _ =>
        Trampoline.done(Left(MigrationError.TransformationFailed(action.at, "Unsupported optic node in migration")))
    }
  }
}

case class Migration[A, B](
  dynamicMigration: DynamicMigration,
  sourceSchema: Schema[A],
  targetSchema: Schema[B]
) {
  def apply(value: A): Either[MigrationError, B] = {
    val dynamic = sourceSchema.toDynamicValue(value)
    dynamicMigration.apply(dynamic) match {
      case Right(dynB) =>
        targetSchema.fromDynamicValue(dynB) match {
          case Right(b)  => Right(b)
          case Left(err) => Left(MigrationError.TransformationFailed(DynamicOptic.root, err.toString))
        }
      case Left(err) => Left(err)
    }
  }

  def ++[C](that: Migration[B, C]): Migration[A, C] =
    Migration(dynamicMigration ++ that.dynamicMigration, sourceSchema, that.targetSchema)

  def andThen[C](that: Migration[B, C]): Migration[A, C] = this ++ that

  def reverse: Migration[B, A] =
    Migration(dynamicMigration.reverse, targetSchema, sourceSchema)
}

object Migration {
  def identity[A](implicit schema: Schema[A]): Migration[A, A] =
    Migration(DynamicMigration(Vector.empty), schema, schema)
}
