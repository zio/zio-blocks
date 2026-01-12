package zio.blocks.schema.migration

import scala.collection.immutable.Vector
import zio.blocks.schema.{DynamicOptic, DynamicValue, SchemaExpr}

import zio.blocks.schema.migration.MigrationAction._

object DynamicMigrationInterpreter {

  /** Apply a DynamicMigration (vector of actions) to a DynamicValue.
    *
    * NOTE:
    *   - This interpreter is intentionally incremental.
    *   - It fully supports AddField / DropField / RenameField.
    *   - Other actions return a clear "Not implemented yet" MigrationError.
    */
  def apply(
      m: DynamicMigration,
      value: DynamicValue,
      sourceSchema: zio.blocks.schema.Schema[_],
      targetSchema: zio.blocks.schema.Schema[_]
  ): Either[MigrationError, DynamicValue] =
    m.actions.foldLeft[Either[MigrationError, DynamicValue]](Right(value)) {
      (acc, action) =>
        acc.flatMap(v => applyAction(action, v))
    }

  private def defaultForField(
      targetSchema: zio.blocks.schema.Schema[_],
      fieldName: String
  ): Either[MigrationError, DynamicValue] =
    targetSchema match {
      case zio.blocks.schema.Schema.Record(fields, _) =>
        fields.find(_._1 == fieldName) match {
          case Some((_, fieldSchema)) =>
            fieldSchema.defaultValue match {
              case Some(dv) => Right(dv)
              case None =>
                Left(
                  MigrationError.InvalidOp(
                    "DefaultValue",
                    s"No default defined for field '$fieldName'"
                  )
                )
            }
          case None =>
            Left(MigrationError.MissingPath(DynamicOptic.root.field(fieldName)))
        }

      case _ =>
        Left(
          MigrationError.InvalidOp("Schema", "Target is not a record schema")
        )
    }

  // ─────────────────────────────────────────────
  // Action dispatcher
  // ─────────────────────────────────────────────

  private def applyAction(
      action: MigrationAction,
      value: DynamicValue
  ): Either[MigrationError, DynamicValue] =
    action match {

      // ───────────────
      // Record ops
      // ───────────────

      case AddField(at, fieldName, defaultExpr) =>
        val provider = () => defaultForField(targetSchema, fieldName)

        for {
          dv <- evalExprToDynamic(defaultExpr, provider)
          out <- modifyRecord(at, value) { fields =>
            if (fields.exists(_._1 == fieldName)) fields
            else fields :+ (fieldName -> dv)
          }
        } yield out

      case DropField(at, fieldName, _) =>
        modifyRecord(at, value)(_.filterNot(_._1 == fieldName))

      case RenameField(at, from, to) =>
        modifyRecord(at, value) { fields =>
          fields.map {
            case (`from`, v) => (to, v)
            case other       => other
          }
        }

      case OptionalizeField(source, _) =>
        modifyAt(source, value) { case dv =>
          Right(DynamicValue.Variant("Some", dv))
        }

      case MandateField(sourceOpt, target, defaultExpr) =>
        modifyAt(sourceOpt, value) {
          case DynamicValue.Variant("Some", dv) => Right(dv)
          case DynamicValue.Variant("None", _) =>
            evalExprToDynamic(
              defaultExpr,
              () => defaultForField(targetSchema, "<unknown>")
            )
          case other =>
            Left(
              MigrationError.TypeMismatch(sourceOpt, "Option", other.toString)
            )
        }

      // ───────────────
      // Everything else: clear error for now
      // ───────────────

      case other =>
        Left(
          MigrationError.InvalidOp(
            "MigrationAction",
            s"Not implemented yet: ${other.getClass.getSimpleName}"
          )
        )
    }

  // ─────────────────────────────────────────────
  // SchemaExpr handling (minimal for now)
  // ─────────────────────────────────────────────
  //
  // IMPORTANT:
  // Your current Migration.apply converts values to DynamicValue first.
  // That means SchemaExpr must either:
  //  1) be evaluatable without input (Literal), OR
  //  2) be a migration marker (DefaultValueExpr), OR
  //  3) be interpreted by a dedicated dynamic-expression evaluator later.
  //
  // For now we support only Literal + DefaultValueExpr.
  //

  private def evalExprToDynamic(
      expr: SchemaExpr[Any, Any],
      defaultProvider: () => Either[MigrationError, DynamicValue]
  ): Either[MigrationError, DynamicValue] =
    expr match {
      case DefaultValueExpr =>
        defaultProvider.defaultValueOrFail

      case lit: SchemaExpr.Literal[Any, Any] @unchecked =>
        // Literal already contains a Schema[A] and precomputes toDynamicValue internally. :contentReference[oaicite:1]{index=1}
        lit.evalDynamic(()) match {
          case Right(values) if values.nonEmpty => Right(values.head)
          case Right(_) =>
            Left(
              MigrationError.InvalidOp(
                "SchemaExpr.Literal",
                "Produced no dynamic values"
              )
            )
          case Left(check) =>
            Left(MigrationError.InvalidOp("SchemaExpr.Literal", check.toString))
        }

      case other =>
        Left(
          MigrationError.InvalidOp(
            "SchemaExpr",
            s"Only SchemaExpr.Literal (and DefaultValueExpr marker) are supported right now. Got: ${other.getClass.getSimpleName}"
          )
        )
    }

  // ─────────────────────────────────────────────
  // Optic helpers (reused from your previous interpreter style)
  // ─────────────────────────────────────────────

  private def modifyRecord(at: DynamicOptic, root: DynamicValue)(
      f: Vector[(String, DynamicValue)] => Vector[(String, DynamicValue)]
  ): Either[MigrationError, DynamicValue] =
    modifyAt(at, root) {
      case DynamicValue.Record(fields) => Right(DynamicValue.Record(f(fields)))
      case other =>
        Left(
          MigrationError.TypeMismatch(
            at,
            "record",
            other.getClass.getSimpleName
          )
        )
    }

  /** Walk down a DynamicOptic path and modify the selected sub-value.
    *
    * This is intentionally conservative: many optic node kinds return "not
    * supported yet" until we implement the corresponding actions
    * (TransformElements, map keys/values, etc.)
    */
  private def modifyAt(at: DynamicOptic, root: DynamicValue)(
      f: DynamicValue => Either[MigrationError, DynamicValue]
  ): Either[MigrationError, DynamicValue] = {

    val nodes: Vector[DynamicOptic.Node] = at.nodes.toVector

    def loop(i: Int, cur: DynamicValue): Either[MigrationError, DynamicValue] =
      if (i >= nodes.length) f(cur)
      else
        nodes(i) match {

          // .field("name")
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
                  MigrationError.TypeMismatch(
                    at,
                    "record",
                    other.getClass.getSimpleName
                  )
                )
            }

          // Everything else is not supported until we implement the matching actions.
          case DynamicOptic.Node.Elements =>
            Left(
              MigrationError.InvalidOp(
                "DynamicOptic",
                "Elements not supported yet (use TransformElements action first)"
              )
            )

          case DynamicOptic.Node.MapKeys =>
            Left(
              MigrationError.InvalidOp(
                "DynamicOptic",
                "MapKeys not supported yet (use TransformKeys action first)"
              )
            )

          case DynamicOptic.Node.MapValues =>
            Left(
              MigrationError.InvalidOp(
                "DynamicOptic",
                "MapValues not supported yet (use TransformValues action first)"
              )
            )

          case DynamicOptic.Node.Case(_) =>
            Left(
              MigrationError.InvalidOp(
                "DynamicOptic",
                "Case not supported yet (use TransformCase action first)"
              )
            )

          case DynamicOptic.Node.Wrapped =>
            Left(
              MigrationError
                .InvalidOp("DynamicOptic", "Wrapped not supported yet")
            )

          case DynamicOptic.Node.AtIndex(_) =>
            Left(
              MigrationError
                .InvalidOp("DynamicOptic", "AtIndex not supported yet")
            )

          case DynamicOptic.Node.AtIndices(_) =>
            Left(
              MigrationError
                .InvalidOp("DynamicOptic", "AtIndices not supported yet")
            )

          case DynamicOptic.Node.AtMapKey(_) =>
            Left(
              MigrationError
                .InvalidOp("DynamicOptic", "AtMapKey not supported yet")
            )

          case DynamicOptic.Node.AtMapKeys(_) =>
            Left(
              MigrationError
                .InvalidOp("DynamicOptic", "AtMapKeys not supported yet")
            )
        }

    loop(0, root)
  }
}
