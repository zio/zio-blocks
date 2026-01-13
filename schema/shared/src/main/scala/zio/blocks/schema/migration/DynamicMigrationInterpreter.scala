
package zio.blocks.schema.migration

import scala.collection.immutable.Vector
import zio.blocks.schema.{DynamicOptic, DynamicValue, SchemaExpr, OpticCheck}

import zio.blocks.schema.migration.MigrationAction._
import zio.blocks.schema.migration.MigrationAction.*

object DynamicMigrationInterpreter {

  /** Apply a DynamicMigration (vector of actions) to a DynamicValue.
    *
    * NOTE:
    *   - This interpreter is intentionally incremental.
    *   - It fully supports AddField / DropField / RenameField.
    *   - Other actions return a clear "Not implemented yet" MigrationError.
    */
 def apply(m: DynamicMigration, value: DynamicValue): Either[MigrationError, DynamicValue] =
  m.actions.foldLeft[Either[MigrationError, DynamicValue]](Right(value)) {
    (acc, action) => acc.flatMap(v => applyAction(action, v))
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

      case MigrationAction.AddField(at, defaultExpr) =>
  evalExprToOneDynamic(expr = defaultExpr, input = DynamicValue.Unit).flatMap { dv =>
    upsertField(at, value, dv)
  }


      case MigrationAction.DropField(at, defaultForReverse) =>
        // if you want: you could store removed value somewhere for reverse; spec uses a default for reverse
        removeField(at, value)

      case MigrationAction.Rename(at, to) =>
        renameField(at, value, to)


     case Optionalize(at) =>
  modifyAt(at, value) { dv =>
    Right(DynamicValue.Variant("Some", dv))
  }


      case Mandate(at, defaultExpr) =>
  modifyAt(at, value) {
    case DynamicValue.Variant("Some", dv) =>
      Right(dv)

    case DynamicValue.Variant("None", _) =>
      evalExprToOneDynamic(expr = defaultExpr, input = DynamicValue.Unit)

    case other =>
      Left(MigrationError.TypeMismatch(at, "Option", other.getClass.getSimpleName))
  }


      case TransformValue(at, expr) =>
        modifyAt(at, value) { cur =>
          // evaluate expression using current value as input (only works when you add dynamic expr eval later)
           evalExprToOneDynamic(
            expr = expr,
            input = cur // IMPORTANT: expression sees the current focused value
            
          )
        }

      case RenameCase(at, from, to) =>
        modifyAt(at, value) {
          case DynamicValue.Variant(`from`, dv) => Right(DynamicValue.Variant(to, dv))
          case DynamicValue.Variant(other, _)   => Right(DynamicValue.Variant(other, value)) // no-op if different case
          case other =>
            Left(MigrationError.TypeMismatch(at, "enum/variant", other.getClass.getSimpleName))
        }

      case TransformCase(at, actions) =>
  modifyAt(at, value) {
    case DynamicValue.Variant(caseName, payload) =>
      val nested = DynamicMigration(actions)
      DynamicMigrationInterpreter(nested, payload)
        .map(newPayload => DynamicValue.Variant(caseName, newPayload))

    case other =>
      Left(MigrationError.TypeMismatch(at, "enum/variant", other.getClass.getSimpleName))
  }


      case TransformElements(at, expr) =>
  modifyAt(at, value) {
    case DynamicValue.Sequence(values) =>
      values.foldLeft[Either[MigrationError, Vector[DynamicValue]]](Right(Vector.empty)) {
        case (acc, elem) =>
          for {
            xs  <- acc
            out <- evalExprToOneDynamic(expr = expr, input = elem)
          } yield xs :+ out
      }.map(DynamicValue.Sequence.apply)

    case other =>
      Left(MigrationError.TypeMismatch(at, "sequence", other.getClass.getSimpleName))
  }


      case TransformKeys(at, expr) =>
        modifyAt(at, value) {
          case DynamicValue.Dictionary(entries) =>
            // keys are DynamicValue; interpret expr as key->key
            entries.foldLeft[Either[MigrationError, Vector[(DynamicValue, DynamicValue)]]](Right(Vector.empty)) {
              case (acc, (k, v)) =>
                for {
                  xs <- acc
                  newK <- evalExprToOneDynamic(
                    expr = expr,
                    input = k // IMPORTANT: expression sees the key
                    
                  )               
                } yield xs :+ (newK -> v)
            }.map(DynamicValue.Dictionary.apply)

          case other =>
            Left(MigrationError.TypeMismatch(at, "dictionary/map", other.getClass.getSimpleName))
        }

      case TransformValues(at, expr) =>
        modifyAt(at, value) {
          case DynamicValue.Dictionary(entries) =>
            entries.foldLeft[Either[MigrationError, Vector[(DynamicValue, DynamicValue)]]](Right(Vector.empty)) {
              case (acc, (k, v)) =>
                for {
                  xs <- acc
                  newV <- evalExprToOneDynamic(
                    expr = expr,
                    input = v, // IMPORTANT: expression sees the value
                    defaultProvider = () =>
                      Left(MigrationError.InvalidOp("DefaultValue", "no default for value transform"))
                  )
                } yield xs :+ (k -> newV)
            }.map(DynamicValue.Dictionary.apply)

          case other =>
            Left(MigrationError.TypeMismatch(at, "dictionary/map", other.getClass.getSimpleName))
        }
  
    }

    /** Evaluate a SchemaExpr to a single DynamicValue.
    *
    * - Uses SchemaExpr.evalDynamic (already implemented in zio-blocks) :contentReference[oaicite:1]{index=1}
    * - Special-cases DefaultValueExpr (migration-only marker)
    * - If the expression returns multiple results (Traversal, etc.), we take the first
    *   (migration actions should normally target a single primitive result).
    */
  private def evalExprToOneDynamic(
    expr: SchemaExpr[Any, Any],
    input: DynamicValue
): Either[MigrationError, DynamicValue] =
  expr.evalDynamic(input) match {
    case Right(values) if values.nonEmpty =>
      Right(values.head)

    case Right(_) =>
      Left(MigrationError.InvalidOp("SchemaExpr", "Expression produced no results"))

    case Left(check) =>
      Left(MigrationError.OpticCheckFailed(check))
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


            // .each / Elements: walk every element in a Sequence
case DynamicOptic.Node.Elements =>
  cur match {
    case DynamicValue.Sequence(values) =>
      values
        .foldLeft[Either[MigrationError, Vector[DynamicValue]]](Right(Vector.empty)) {
          case (acc2, elem) =>
            for {
              xs  <- acc2
              out <- loop(i + 1, elem) // continue walking the rest of the optic on this element
            } yield xs :+ out
        }
        .map(DynamicValue.Sequence.apply)

    case other =>
      Left(MigrationError.TypeMismatch(at, "sequence", other.getClass.getSimpleName))
  }

// .mapKeys: focus each key (dictionary)
case DynamicOptic.Node.MapKeys =>
  cur match {
    case DynamicValue.Dictionary(entries) =>
      entries
        .foldLeft[Either[MigrationError, Vector[(DynamicValue, DynamicValue)]]](Right(Vector.empty)) {
          case (acc2, (k, v)) =>
            for {
              xs  <- acc2
              k2  <- loop(i + 1, k) // continue walking on the key
            } yield xs :+ (k2 -> v)
        }
        .map(DynamicValue.Dictionary.apply)

    case other =>
      Left(MigrationError.TypeMismatch(at, "dictionary/map", other.getClass.getSimpleName))
  }

// .mapValues: focus each value (dictionary)
case DynamicOptic.Node.MapValues =>
  cur match {
    case DynamicValue.Dictionary(entries) =>
      entries
        .foldLeft[Either[MigrationError, Vector[(DynamicValue, DynamicValue)]]](Right(Vector.empty)) {
          case (acc2, (k, v)) =>
            for {
              xs  <- acc2
              v2  <- loop(i + 1, v) // continue walking on the value
            } yield xs :+ (k -> v2)
        }
        .map(DynamicValue.Dictionary.apply)

    case other =>
      Left(MigrationError.TypeMismatch(at, "dictionary/map", other.getClass.getSimpleName))
  }

// .when[Case] / Case("X"): only focus if current Variant matches the case
case DynamicOptic.Node.Case(expected) =>
  cur match {
    case DynamicValue.Variant(actual, payload) if actual == expected =>
      loop(i + 1, payload).map(p2 => DynamicValue.Variant(actual, p2))

    case DynamicValue.Variant(_, _) =>
      // different case: selector focus doesn't exist → no-op
      Right(cur)

    case other =>
      Left(MigrationError.TypeMismatch(at, "enum/variant", other.getClass.getSimpleName))
  }


          
        }

    loop(0, root)
  }

    private def splitParentAndField(at: DynamicOptic): Either[MigrationError, (DynamicOptic, String)] = {
    at.nodes.lastOption match {
      case Some(DynamicOptic.Node.Field(name)) =>
        val parentNodes = at.nodes.toVector.dropRight(1)
        val parent = MigrationDsl.RuntimeOptic.rebuildFromNodes(parentNodes)
        Right((parent, name))

      case other =>
        Left(MigrationError.InvalidOp("DynamicOptic", s"Expected field path, got: $other"))
    }
  }

  private def upsertField(at: DynamicOptic, root: DynamicValue, value: DynamicValue): Either[MigrationError, DynamicValue] =
    splitParentAndField(at).flatMap { case (parent, field) =>
      modifyAt(parent, root) {
        case DynamicValue.Record(fields) =>
          val idx = fields.indexWhere(_._1 == field)
          if (idx >= 0) Right(DynamicValue.Record(fields.updated(idx, field -> value)))
          else Right(DynamicValue.Record(fields :+ (field -> value)))

        case other =>
          Left(MigrationError.ExpectedRecord(parent, other))
      }
    }

  private def removeField(at: DynamicOptic, root: DynamicValue): Either[MigrationError, DynamicValue] =
    splitParentAndField(at).flatMap { case (parent, field) =>
      modifyAt(parent, root) {
        case DynamicValue.Record(fields) =>
          Right(DynamicValue.Record(fields.filterNot(_._1 == field)))
        case other =>
          Left(MigrationError.ExpectedRecord(parent, other))
      }
    }

  private def renameField(at: DynamicOptic, root: DynamicValue, to: String): Either[MigrationError, DynamicValue] =
    splitParentAndField(at).flatMap { case (parent, from) =>
      modifyAt(parent, root) {
        case DynamicValue.Record(fields) =>
          val idx = fields.indexWhere(_._1 == from)
          if (idx < 0) Right(DynamicValue.Record(fields)) // or error if you want strict
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
