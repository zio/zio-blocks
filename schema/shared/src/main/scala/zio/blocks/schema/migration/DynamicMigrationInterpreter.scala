package zio.blocks.schema.migration

import scala.collection.immutable.Vector
import zio.blocks.schema.{
  DynamicOptic,
  DynamicValue,
  SchemaExpr
}
import zio.blocks.schema.migration.MigrationAction.*

object DynamicMigrationInterpreter {

  /** Apply a DynamicMigration to a DynamicValue.
    *
    * We accept schemas so we can:
    *   - resolve MigrationSchemaExpr.DefaultValue using target schema defaults
    *   - do better validation / error messages
    */
  def apply(
      m: DynamicMigration,
      value: DynamicValue
  ): Either[MigrationError, DynamicValue] =
    m.actions.foldLeft[Either[MigrationError, DynamicValue]](Right(value)) {
      (acc, action) => acc.flatMap(v => applyAction(action, v))

    }

  // ─────────────────────────────────────────────
  // Action dispatcher
  // ─────────────────────────────────────────────

  private def applyAction(
      action: MigrationAction,
      root: DynamicValue
  ): Either[MigrationError, DynamicValue] =
    action match {

      // ───────────────
      // Record ops
      // ───────────────

      case AddField(at, defaultExpr) =>
        evalExprToOneDynamic(
          expr = defaultExpr,
          input = DynamicValue.Record(Vector.empty)
        )
          .flatMap(dv => upsertField(at, root, dv))

      case DropField(at, _) =>
        removeField(at, root)

      case Rename(at, to) =>
        renameField(at, root, to)

      case Optionalize(at) =>
        modifyAt(at, root) { dv =>
          Right(DynamicValue.Variant("Some", dv))
        }

      case Mandate(at, defaultExpr) =>
        modifyAt(at, root) {
          case DynamicValue.Variant("Some", dv) =>
            Right(dv)

          case DynamicValue.Variant("None", _) =>
            evalExprToOneDynamic(
              expr = defaultExpr,
              input = DynamicValue.Record(Vector.empty)
            )

          case other =>
            Left(
              MigrationError.TypeMismatch(
                at,
                "Option",
                other.getClass.getSimpleName
              )
            )
        }

      case TransformValue(at, expr) =>
        modifyAt(at, root) { cur =>
          evalExprToOneDynamic(
            expr = expr,
            input = cur
          )
        }

      case ChangeType(at, converter) =>
        modifyAt(at, root) { cur =>
          if (!isPrimitive(cur))
            Left(
              MigrationError.InvalidOp(
                "ChangeType",
                s"Only primitive->primitive supported, but got: $cur at $at"
              )
            )
          else
            evalExprToOneDynamic(
              expr = converter,
              input = cur
            ).flatMap { out =>
              if (!isPrimitive(out))
                Left(
                  MigrationError.InvalidOp(
                    "ChangeType",
                    s"Converter must produce a primitive, but got: $out at $at"
                  )
                )
              else Right(out)
            }
        }

      case Join(at, sourcePaths, combiner) =>
        for {
          inputs <- sourcePaths
            .foldLeft[Either[MigrationError, Vector[DynamicValue]]](
              Right(Vector.empty)
            ) { case (acc, p) =>
              for {
                xs <- acc
                v <- getAt(p, root)
                _ <-
                  if (isPrimitive(v)) Right(())
                  else
                    Left(
                      MigrationError.InvalidOp(
                        "Join",
                        s"Join inputs must be primitive; got $v at $p"
                      )
                    )
              } yield xs :+ v
            }

          // convention: we pass inputs to combiner as a record: _0, _1, ...
          inputRecord = DynamicValue.Record(inputs.zipWithIndex.map {
            case (v, i) => (s"_$i", v)
          })

          out <- evalExprToOneDynamic(
            expr = combiner,
            input = inputRecord
          )
          _ <-
            if (isPrimitive(out)) Right(())
            else
              Left(
                MigrationError.InvalidOp(
                  "Join",
                  s"Join output must be primitive; got $out at $at"
                )
              )

          updated <- upsertField(at, root, out)
        } yield updated

      case Split(at, targetPaths, splitter) =>
        for {
          original <- getAt(at, root)
          _ <-
            if (isPrimitive(original)) Right(())
            else
              Left(
                MigrationError.InvalidOp(
                  "Split",
                  s"Split input must be primitive; got $original at $at"
                )
              )

          produced <- evalExprToOneDynamic(
            expr = splitter,
            input = original
          )

          // convention: splitter must produce a record with fields matching target field names
          producedRecord <- produced match {
            case DynamicValue.Record(fields) => Right(fields.toMap)
            case other =>
              Left(
                MigrationError.InvalidOp(
                  "Split",
                  s"Splitter must return a record, but got: $other"
                )
              )
          }

          // remove original field (split replaces it)
          withoutOriginal <- removeField(at, root)

          withTargets <- targetPaths
            .foldLeft[Either[MigrationError, DynamicValue]](
              Right(withoutOriginal)
            ) { case (acc, targetPath) =>
              for {
                curRoot <- acc
                fieldName <- targetPath.nodes.lastOption match {
                  case Some(DynamicOptic.Node.Field(n)) => Right(n)
                  case other =>
                    Left(
                      MigrationError.InvalidOp(
                        "Split",
                        s"targetPaths must end in .field(...), got: $other"
                      )
                    )
                }
                piece <- producedRecord.get(fieldName) match {
                  case Some(v) => Right(v)
                  case None =>
                    Left(
                      MigrationError.InvalidOp(
                        "Split",
                        s"Splitter did not produce field '$fieldName' required by target path: $targetPath"
                      )
                    )
                }
                _ <-
                  if (isPrimitive(piece)) Right(())
                  else
                    Left(
                      MigrationError.InvalidOp(
                        "Split",
                        s"Split outputs must be primitive; got $piece for $fieldName"
                      )
                    )
                next <- upsertField(targetPath, curRoot, piece)
              } yield next
            }
        } yield withTargets

      // ───────────────
      // Enum ops
      // ───────────────

      case RenameCase(at, from, to) =>
        modifyAt(at, root) {
          case DynamicValue.Variant(`from`, dv) =>
            Right(DynamicValue.Variant(to, dv))
          case DynamicValue.Variant(other, payload) =>
            Right(DynamicValue.Variant(other, payload)) // no-op for other cases
          case other =>
            Left(
              MigrationError.TypeMismatch(
                at,
                "enum/variant",
                other.getClass.getSimpleName
              )
            )
        }

      case TransformCase(at, actions) =>
        modifyAt(at, root) {
          case DynamicValue.Variant(caseName, payload) =>
            val nested = DynamicMigration(actions)
            DynamicMigrationInterpreter(nested, payload)
              .map(newPayload => DynamicValue.Variant(caseName, newPayload))
          case other =>
            Left(
              MigrationError.TypeMismatch(
                at,
                "enum/variant",
                other.getClass.getSimpleName
              )
            )
        }

      // ───────────────
      // Collections / Maps
      // ───────────────

      case TransformElements(at, expr) =>
        modifyAt(at, root) {
          case DynamicValue.Sequence(values) =>
            values
              .foldLeft[Either[MigrationError, Vector[DynamicValue]]](
                Right(Vector.empty)
              ) { case (acc, elem) =>
                for {
                  xs <- acc
                  out <- evalExprToOneDynamic(
                    expr = expr,
                    input = elem
                  )
                } yield xs :+ out
              }
              .map(DynamicValue.Sequence.apply)

          case other =>
            Left(
              MigrationError.TypeMismatch(
                at,
                "sequence",
                other.getClass.getSimpleName
              )
            )
        }

      case TransformKeys(at, expr) =>
        modifyAt(at, root) {
          case DynamicValue.Map(entries) =>

            entries
              .foldLeft[Either[MigrationError, Vector[
                (DynamicValue, DynamicValue)
              ]]](Right(Vector.empty)) { case (acc, (k, v)) =>
                for {
                  xs <- acc
                  newK <- evalExprToOneDynamic(
                    expr = expr,
                    input = k
                  )
                } yield xs :+ (newK -> v)
              }
              .map(DynamicValue.Map.apply)

          case other =>
            Left(
              MigrationError.TypeMismatch(
                at,
                "dictionary/map",
                other.getClass.getSimpleName
              )
            )
        }

      case TransformValues(at, expr) =>
        modifyAt(at, root) {
          case DynamicValue.Map(entries) =>
            entries
              .foldLeft[Either[MigrationError, Vector[
                (DynamicValue, DynamicValue)
              ]]](Right(Vector.empty)) { case (acc, (k, v)) =>
                for {
                  xs <- acc
                  newV <- evalExprToOneDynamic(
                    expr = expr,
                    input = v
                  )
                } yield xs :+ (k -> newV)
              }
              .map(DynamicValue.Map.apply)

          case other =>
            Left(
              MigrationError.TypeMismatch(
                at,
                "dictionary/map",
                other.getClass.getSimpleName
              )
            )
        }
    }

  // ─────────────────────────────────────────────
  // SchemaExpr evaluation + DefaultValue resolution
  // ─────────────────────────────────────────────

  private def evalExprToOneDynamic(
    expr: SchemaExpr[Any, Any],
    input: DynamicValue
): Either[MigrationError, DynamicValue] =
  expr.evalDynamic(input) match {
    case Right(values) if values.nonEmpty =>
      Right(values.head)

    case Right(_) =>
      Left(
        MigrationError.InvalidOp(
          "SchemaExpr",
          "Expression produced no results"
        )
      )

    case Left(check) =>
      Left(MigrationError.OpticCheckFailed(check))
  }



  // ─────────────────────────────────────────────
  // Optic helpers
  // ─────────────────────────────────────────────

  private def isPrimitive(dv: DynamicValue): Boolean =
    dv match {
      case DynamicValue.Record(_)     => false
      case DynamicValue.Variant(_, _) => false
      case DynamicValue.Sequence(_)   => false
      case DynamicValue.Map(_)        => false
      case _                          => true
    }

  /** Read-only focus. Supports the same node types modifyAt supports. */
  private def getAt(
      at: DynamicOptic,
      root: DynamicValue
  ): Either[MigrationError, DynamicValue] = {
    val nodes = at.nodes.toVector

    def loop(i: Int, cur: DynamicValue): Either[MigrationError, DynamicValue] =
      if (i >= nodes.length) Right(cur)
      else
        nodes(i) match {

          case DynamicOptic.Node.Field(name) =>
            cur match {
              case DynamicValue.Record(fields) =>
                fields.find(_._1 == name) match {
                  case Some((_, v)) => loop(i + 1, v)
                  case None         => Left(MigrationError.MissingPath(at))
                }
              case other =>
                Left(
                  MigrationError
                    .TypeMismatch(at, "record", other.getClass.getSimpleName)
                )
            }

          case DynamicOptic.Node.Elements =>
            // For defaults we only support focusing a single value, not traversals.
            Left(
              MigrationError.InvalidOp(
                "DynamicOptic",
                s"Traversal (.each) not supported for DefaultValue focus: $at"
              )
            )

          case DynamicOptic.Node.MapKeys | DynamicOptic.Node.MapValues =>
            Left(
              MigrationError.InvalidOp(
                "DynamicOptic",
                s"Map traversal not supported for DefaultValue focus: $at"
              )
            )

          case DynamicOptic.Node.Case(expected) =>
            cur match {
              case DynamicValue.Variant(actual, payload)
                  if actual == expected =>
                loop(i + 1, payload)
              case DynamicValue.Variant(_, _) =>
                // different case: focus doesn't exist
                Left(MigrationError.MissingPath(at))
              case other =>
                Left(
                  MigrationError.TypeMismatch(
                    at,
                    "enum/variant",
                    other.getClass.getSimpleName
                  )
                )
            }

          case other =>
            Left(
              MigrationError.InvalidOp(
                "DynamicOptic",
                s"Unsupported optic node in getAt: $other"
              )
            )
        }

    loop(0, root)
  }

  private def modifyAt(at: DynamicOptic, root: DynamicValue)(
      f: DynamicValue => Either[MigrationError, DynamicValue]
  ): Either[MigrationError, DynamicValue] = {

    val nodes: Vector[DynamicOptic.Node] = at.nodes.toVector

    def loop(i: Int, cur: DynamicValue): Either[MigrationError, DynamicValue] =
      if (i >= nodes.length) f(cur)
      else
        nodes(i) match {

          case DynamicOptic.Node.Field(name) =>
            cur match {
              case DynamicValue.Record(fields) =>
                val idx = fields.indexWhere(_._1 == name)
                if (idx < 0) Left(MigrationError.MissingPath(at))
                else {
                  val (k, v) = fields(idx)
                  loop(i + 1, v).map(v2 =>
                    DynamicValue.Record(fields.updated(idx, (k, v2)))
                  )
                }

              case other =>
                Left(
                  MigrationError
                    .TypeMismatch(at, "record", other.getClass.getSimpleName)
                )
            }

          case DynamicOptic.Node.Elements =>
            cur match {
              case DynamicValue.Sequence(values) =>
                values
                  .foldLeft[Either[MigrationError, Vector[DynamicValue]]](
                    Right(Vector.empty)
                  ) { case (acc2, elem) =>
                    for {
                      xs <- acc2
                      out <- loop(i + 1, elem)
                    } yield xs :+ out
                  }
                  .map(DynamicValue.Sequence.apply)

              case other =>
                Left(
                  MigrationError
                    .TypeMismatch(at, "sequence", other.getClass.getSimpleName)
                )
            }

          case DynamicOptic.Node.MapKeys =>
            cur match {
              case DynamicValue.Map(entries) =>
                entries
                  .foldLeft[Either[MigrationError, Vector[
                    (DynamicValue, DynamicValue)
                  ]]](Right(Vector.empty)) { case (acc2, (k, v)) =>
                    for {
                      xs <- acc2
                      k2 <- loop(i + 1, k)
                    } yield xs :+ (k2 -> v)
                  }
                  .map(DynamicValue.Map.apply)

              case other =>
                Left(
                  MigrationError.TypeMismatch(
                    at,
                    "dictionary/map",
                    other.getClass.getSimpleName
                  )
                )
            }

          case DynamicOptic.Node.MapValues =>
            cur match {
              case DynamicValue.Map(entries) =>
                entries
                  .foldLeft[Either[MigrationError, Vector[
                    (DynamicValue, DynamicValue)
                  ]]](Right(Vector.empty)) { case (acc2, (k, v)) =>
                    for {
                      xs <- acc2
                      v2 <- loop(i + 1, v)
                    } yield xs :+ (k -> v2)
                  }
                  .map(DynamicValue.Map.apply)

              case other =>
                Left(
                  MigrationError.TypeMismatch(
                    at,
                    "dictionary/map",
                    other.getClass.getSimpleName
                  )
                )
            }

          case DynamicOptic.Node.Case(expected) =>
            cur match {
              case DynamicValue.Variant(actual, payload)
                  if actual == expected =>
                loop(i + 1, payload).map(p2 => DynamicValue.Variant(actual, p2))

              case DynamicValue.Variant(_, _) =>
                // different case: no-op
                Right(cur)

              case other =>
                Left(
                  MigrationError.TypeMismatch(
                    at,
                    "enum/variant",
                    other.getClass.getSimpleName
                  )
                )
            }

          case other =>
            Left(
              MigrationError.InvalidOp(
                "DynamicOptic",
                s"Unsupported optic node in modifyAt: $other"
              )
            )
        }

    loop(0, root)
  }

  private def splitParentAndField(
      at: DynamicOptic
  ): Either[MigrationError, (DynamicOptic, String)] =
    at.nodes.lastOption match {
      case Some(DynamicOptic.Node.Field(name)) =>
        val parentNodes = at.nodes.toVector.dropRight(1)
        val parent = DynamicOptic(parentNodes)

        Right((parent, name))

      case other =>
        Left(
          MigrationError.InvalidOp(
            "DynamicOptic",
            s"Expected field path, got: $other"
          )
        )
    }

  private def upsertField(
      at: DynamicOptic,
      root: DynamicValue,
      value: DynamicValue
  ): Either[MigrationError, DynamicValue] =
    splitParentAndField(at).flatMap { case (parent, field) =>
      modifyAt(parent, root) {
        case DynamicValue.Record(fields) =>
          val idx = fields.indexWhere(_._1 == field)
          if (idx >= 0)
            Right(DynamicValue.Record(fields.updated(idx, field -> value)))
          else Right(DynamicValue.Record(fields :+ (field -> value)))

        case other =>
          Left(MigrationError.ExpectedRecord(parent, other))
      }
    }

  private def removeField(
      at: DynamicOptic,
      root: DynamicValue
  ): Either[MigrationError, DynamicValue] =
    splitParentAndField(at).flatMap { case (parent, field) =>
      modifyAt(parent, root) {
        case DynamicValue.Record(fields) =>
          Right(DynamicValue.Record(fields.filterNot(_._1 == field)))
        case other =>
          Left(MigrationError.ExpectedRecord(parent, other))
      }
    }

  private def renameField(
      at: DynamicOptic,
      root: DynamicValue,
      to: String
  ): Either[MigrationError, DynamicValue] =
    splitParentAndField(at).flatMap { case (parent, from) =>
      modifyAt(parent, root) {
        case DynamicValue.Record(fields) =>
          val idx = fields.indexWhere(_._1 == from)
          if (idx < 0) Left(MigrationError.MissingPath(at))
          else {
            val v = fields(idx)._2
            val without = fields.filterNot(_._1 == from)
            Right(DynamicValue.Record(without :+ (to -> v)))
          }

        case other =>
          Left(MigrationError.ExpectedRecord(parent, other))
      }
    }
}
