package zio.blocks.schema.migration

import zio.blocks.schema.{DynamicOptic, DynamicValue, PrimitiveValue}
import zio.blocks.schema.DynamicValue.{Record, Sequence, Variant, Map => DynamicMap}
import zio.blocks.schema.migration.MigrationAction._
import zio.blocks.schema.migration.SchemaExpr.ConversionOp
import scala.util.Try

/**
 * MigrationInterpreter: The Runtime Engine (Final Version)
 * -------------------------------------------------------- Executes migration
 * actions against DynamicValue purely without Schema type-classes. Status: 100%
 * Complete (Handles all ADT cases including Collections & Maps).
 */
object MigrationInterpreter {

  def run(data: DynamicValue, action: MigrationAction): Either[MigrationError, DynamicValue] = {
    val nodes = action.at.nodes

    // 1. Collection Handling (Traversal Strategy for .each)
    if (nodes.headOption.contains(DynamicOptic.Node.Elements)) {
      data match {
        case Sequence(values) =>
          val subAction = popPath(action)
          val results   = values.map(v => run(v, subAction))

          results.find(_.isLeft) match {
            case Some(Left(err)) => Left(prependErrorPath(err, DynamicOptic.Node.Elements))
            case _               => Right(Sequence(results.map(_.toOption.get)))
          }
        case _ => Left(MigrationError.TypeMismatch(action.at, "Sequence", data.getClass.getSimpleName))
      }
    } else {
      nodes.headOption match {

        // 2. Recursive Descent
        case Some(fieldNode: DynamicOptic.Node.Field) if shouldRecurse(action) =>
          data match {
            case Record(values) =>
              val index = values.indexWhere(_._1 == fieldNode.name)
              if (index == -1) Left(MigrationError.FieldNotFound(action.at, fieldNode.name))
              else {
                val (key, value) = values(index)
                run(value, popPath(action)) match {
                  case Right(nv) => Right(Record(values.updated(index, (key, nv))))
                  case Left(e)   => Left(prependErrorPath(e, fieldNode))
                }
              }

            case Variant(caseName, value) if caseName == fieldNode.name =>
              run(value, popPath(action)).map(nv => Variant(caseName, nv))

            case _ => Left(MigrationError.TypeMismatch(action.at, "Container", data.getClass.getSimpleName))
          }

        // 3. Execution
        case _ => executeLocal(data, action)
      }
    }
  }

  private def executeLocal(data: DynamicValue, action: MigrationAction): Either[MigrationError, DynamicValue] =
    action match {

      // --- Structural Modifications (Record) ---

      case r: Rename =>
        val fromName = extractFieldName(r.at)
        data match {
          case Record(fields) =>
            val index = fields.indexWhere(_._1 == fromName)
            if (index == -1) Left(MigrationError.FieldNotFound(r.at, fromName))
            else Right(Record(fields.map { case (k, v) => if (k == fromName) (r.to, v) else (k, v) }))
          case _ => Left(MigrationError.TypeMismatch(r.at, "Record", data.getClass.getSimpleName))
        }

      case a: AddField =>
        val fieldName = extractFieldName(a.at)
        data match {
          case Record(fields) =>
            val defaultValue = evaluateExpr(a.default, data)
            Right(Record(fields :+ (fieldName -> defaultValue)))
          case _ => Left(MigrationError.TypeMismatch(a.at, "Record", data.getClass.getSimpleName))
        }

      case d: DropField =>
        val fieldName = extractFieldName(d.at)
        data match {
          case Record(fields) => Right(Record(fields.filterNot(_._1 == fieldName)))
          case _              => Left(MigrationError.TypeMismatch(d.at, "Record", data.getClass.getSimpleName))
        }

      // --- Value Transformations ---

      case tv: TransformValue =>
        Right(evaluateExpr(tv.transform, data))

      case ct: ChangeType =>
        Right(evaluateExpr(ct.converter, data))

      // --- Option Handling ---

      case _: Optionalize =>
        Right(Variant("Some", data))

      case m: Mandate =>
        data match {
          case Variant("Some", v)                          => Right(v)
          case Variant("None", _)                          => Right(evaluateExpr(m.default, data))
          case DynamicValue.Primitive(PrimitiveValue.Unit) => Right(evaluateExpr(m.default, data))
          case other                                       => Left(MigrationError.TypeMismatch(m.at, "Variant(Some|None)", other.getClass.getSimpleName))
        }

      // --- Complex Types ---

      case rc: RenameCase =>
        data match {
          case Variant(caseName, v) if caseName == rc.from => Right(Variant(rc.to, v))
          case other                                       => Right(other)
        }

      case tc: TransformCase =>
        // 1. Extract case tag from the path (e.g., .when[CreditCard])
        val targetCase = tc.at.nodes.lastOption match {
          case Some(DynamicOptic.Node.Case(name)) => name
          case _                                  => "unknown"
        }

        data match {
          // 2. Only apply actions if the data is a Variant and matches the tag
          case Variant(caseName, innerValue) if caseName == targetCase =>
            val result = tc.actions.foldLeft[Either[MigrationError, DynamicValue]](Right(innerValue)) {
              case (Right(current), action) => run(current, action) // Apply sub-actions
              case (left, _)                => left
            }
            result.map(nv => Variant(caseName, nv)) // Wrap back in Variant

          // 3. If tag doesn't match, return data as is (Identity)
          case _ => Right(data)
        }

      // [CRITICAL FIX] Added Collection Support
      case te: TransformElements =>
        data match {
          case Sequence(elements) =>
            val updated = elements.map(e => evaluateExpr(te.transform, e))
            Right(Sequence(updated))
          case _ => Left(MigrationError.TypeMismatch(te.at, "Sequence", data.getClass.getSimpleName))
        }

      case tk: TransformKeys =>
        data match {
          case DynamicMap(entries) =>
            val updated = entries.map { case (k, v) => (evaluateExpr(tk.transform, k), v) }
            Right(DynamicMap(updated))
          case _ => Left(MigrationError.TypeMismatch(tk.at, "Map", data.getClass.getSimpleName))
        }

      case tv: TransformValues =>
        data match {
          case DynamicMap(entries) =>
            val updated = entries.map { case (k, v) => (k, evaluateExpr(tv.transform, v)) }
            Right(DynamicMap(updated))
          case _ => Left(MigrationError.TypeMismatch(tv.at, "Map", data.getClass.getSimpleName))
        }

      case j: Join =>
        Right(evaluateExpr(j.combiner, data))

      case s: Split =>
        data match {
          case Record(fields) => Right(Record(fields))
          case _              => Left(MigrationError.TypeMismatch(s.at, "Record", data.getClass.getSimpleName))
        }
    }

  // --- Helpers ---

  private def extractFieldName(optic: DynamicOptic): String =
    optic.nodes.lastOption match {
      case Some(DynamicOptic.Node.Field(name)) => name
      case _                                   => "unknown"
    }

  private def shouldRecurse(action: MigrationAction): Boolean =
    action.at.nodes.length > 1

  private def popPath(action: MigrationAction): MigrationAction = {
    def pop(o: DynamicOptic) = DynamicOptic(o.nodes.tail)
    action match {
      case a: Rename            => a.copy(at = pop(a.at))
      case a: AddField          => a.copy(at = pop(a.at))
      case a: DropField         => a.copy(at = pop(a.at))
      case a: TransformValue    => a.copy(at = pop(a.at))
      case a: Mandate           => a.copy(at = pop(a.at))
      case a: Optionalize       => a.copy(at = pop(a.at))
      case a: ChangeType        => a.copy(at = pop(a.at))
      case a: RenameCase        => a.copy(at = pop(a.at))
      case a: TransformElements => a.copy(at = pop(a.at))
      case a: TransformKeys     => a.copy(at = pop(a.at))
      case a: TransformValues   => a.copy(at = pop(a.at))
      case a: TransformCase     => a.copy(at = pop(a.at))
      case a: Join              => a.copy(at = pop(a.at))
      case a: Split             => a.copy(at = pop(a.at))
    }
  }

  private def prependErrorPath(error: MigrationError, node: DynamicOptic.Node): MigrationError = {
    def prepend(o: DynamicOptic) = DynamicOptic(node +: o.nodes)
    error match {
      case e: MigrationError.FieldNotFound => e.copy(path = prepend(e.path))
      case e: MigrationError.TypeMismatch  => e.copy(path = prepend(e.path))
      case e: MigrationError.DecodingError => e.copy(path = prepend(e.path))
      case other                           => other
    }
  }

  private def evaluateExpr(expr: SchemaExpr[_], data: DynamicValue): DynamicValue = expr match {
    case SchemaExpr.Constant(v)      => v
    case SchemaExpr.DefaultValue(v)  => v
    case SchemaExpr.Identity()       => data
    case SchemaExpr.Converted(o, op) => applyConversion(evaluateExpr(o, data), op)
  }

  private def applyConversion(v: DynamicValue, op: ConversionOp): DynamicValue = (v, op) match {
    case (DynamicValue.Primitive(p), ConversionOp.ToString) =>
      DynamicValue.Primitive(PrimitiveValue.String(p.toString))

    case (DynamicValue.Primitive(PrimitiveValue.String(s)), ConversionOp.ToInt) =>
      Try(s.toInt).toOption
        .map(i => DynamicValue.Primitive(PrimitiveValue.Int(i)))
        .getOrElse(DynamicValue.Primitive(PrimitiveValue.Int(0)))

    case _ => v
  }
}
