package zio.blocks.schema.diff

import zio.blocks.schema.{DynamicOptic, DynamicValue, PrimitiveValue, SchemaError}
import zio.blocks.schema.DynamicOptic.Node

final case class DynamicPatchOp(optic: DynamicOptic, operation: Operation)

final case class DynamicPatch(ops: Vector[DynamicPatchOp]) { self =>

  def ++(that: DynamicPatch): DynamicPatch = DynamicPatch(self.ops ++ that.ops)
  def isEmpty: Boolean                     = ops.isEmpty

  def apply(value: DynamicValue)(implicit mode: PatchMode = PatchMode.Strict): Either[SchemaError, DynamicValue] =
    ops.foldLeft[Either[SchemaError, DynamicValue]](Right(value)) {
      case (Left(e), _)      => Left(e)
      case (Right(curr), op) =>
        applyOp(curr, op.optic.nodes.toList, op.operation, mode, Nil)
    }

  // --- Navigation & Application Logic ---

  private def applyOp(
    value: DynamicValue,
    path: List[Node],
    op: Operation,
    mode: PatchMode,
    trace: List[Node]
  ): Either[SchemaError, DynamicValue] = path match {

    // Reached target: Execute Operation
    case Nil => executeOperation(value, op, mode, trace)

    // Navigation: Record Field
    case (head @ Node.Field(name)) :: tail =>
      value match {
        case DynamicValue.Record(fields) =>
          val idx = fields.indexWhere(_._1 == name)
          if (idx == -1) {
            if (mode == PatchMode.Lenient) Right(value)
            else if (mode == PatchMode.Clobber && tail.isEmpty) {
              op match {
                case Operation.Set(v) => Right(DynamicValue.Record(fields :+ (name -> v)))
                case _                => Left(SchemaError.missingField(head :: trace, name))
              }
            } else Left(SchemaError.missingField(trace, name))
          } else {
            val (k, v) = fields(idx)
            applyOp(v, tail, op, mode, head :: trace).map { newVal =>
              DynamicValue.Record(fields.updated(idx, (k, newVal)))
            }
          }
        case _ => Left(SchemaError.expectationMismatch(trace, s"Expected Record, got ${value.getClass.getSimpleName}"))
      }

    // Navigation: Sequence Index
    case (head @ Node.AtIndex(index)) :: tail =>
      value match {
        case DynamicValue.Sequence(elems) =>
          if (index < 0 || index >= elems.length) {
            if (mode == PatchMode.Strict) Left(SchemaError.expectationMismatch(trace, s"Index $index out of bounds"))
            else Right(value)
          } else {
            applyOp(elems(index), tail, op, mode, head :: trace).map { newVal =>
              DynamicValue.Sequence(elems.updated(index, newVal))
            }
          }
        case _ =>
          Left(SchemaError.expectationMismatch(trace, s"Expected Sequence, got ${value.getClass.getSimpleName}"))
      }

    // Navigation: Sequence Elements (Traversal)
    case (head @ Node.Elements) :: tail =>
      value match {
        case DynamicValue.Sequence(elems) =>
          val res = elems.zipWithIndex.foldLeft[Either[SchemaError, Vector[DynamicValue]]](Right(Vector.empty)) {
            case (Left(e), _)         => Left(e)
            case (Right(acc), (v, _)) =>
              applyOp(v, tail, op, mode, head :: trace).map(acc :+ _)
          }
          res.map(DynamicValue.Sequence(_))
        case _ =>
          Left(SchemaError.expectationMismatch(trace, s"Expected Sequence, got ${value.getClass.getSimpleName}"))
      }

    // Navigation: Map Values (Traversal)
    case (head @ Node.MapValues) :: tail =>
      value match {
        case DynamicValue.Map(entries) =>
          val res = entries.foldLeft[Either[SchemaError, Vector[(DynamicValue, DynamicValue)]]](Right(Vector.empty)) {
            case (Left(e), _)         => Left(e)
            case (Right(acc), (k, v)) =>
              applyOp(v, tail, op, mode, head :: trace).map(nv => acc :+ (k -> nv))
          }
          res.map(DynamicValue.Map(_))
        case _ => Left(SchemaError.expectationMismatch(trace, s"Expected Map, got ${value.getClass.getSimpleName}"))
      }

    // Navigation: Map Key (Specific)
    case (head @ Node.AtMapKey(key: DynamicValue)) :: tail =>
      value match {
        case DynamicValue.Map(entries) =>
          val idx = entries.indexWhere(_._1 == key)
          if (idx == -1) {
            if (mode == PatchMode.Strict) Left(SchemaError.missingField(trace, key.toString))
            else Right(value)
          } else {
            val (k, v) = entries(idx)
            applyOp(v, tail, op, mode, head :: trace).map { newVal =>
              DynamicValue.Map(entries.updated(idx, (k, newVal)))
            }
          }
        case _ => Left(SchemaError.expectationMismatch(trace, s"Expected Map, got ${value.getClass.getSimpleName}"))
      }

    // Navigation: Variant Case
    case (head @ Node.Case(caseName)) :: tail =>
      value match {
        case DynamicValue.Variant(name, inner) if name == caseName =>
          applyOp(inner, tail, op, mode, head :: trace).map(newInner => DynamicValue.Variant(name, newInner))
        case DynamicValue.Variant(_, _) =>
          // Prism semantics: Mismatch means we do not apply the patch. Return original.
          Right(value)
        case _ => Left(SchemaError.expectationMismatch(trace, s"Expected Variant, got ${value.getClass.getSimpleName}"))
      }

    // Navigation: Wrapped (Skip)
    case Node.Wrapped :: tail =>
      applyOp(value, tail, op, mode, trace)

    case head :: _ =>
      Left(SchemaError.expectationMismatch(head :: trace, s"Unsupported path node: $head"))
  }

  // --- Operation Execution ---

  private def executeOperation(
    target: DynamicValue,
    op: Operation,
    mode: PatchMode,
    trace: List[Node]
  ): Either[SchemaError, DynamicValue] =
    op match {
      case Operation.Identity          => Right(target)
      case Operation.Set(v)            => Right(v)
      case Operation.PrimitiveDelta(d) => applyPrimitiveDelta(target, d, trace)
      case Operation.StringEdit(ops)   => applyStringEdit(target, ops, trace)
      case Operation.SequenceEdit(ops) => applySeqEdit(target, ops, mode, trace)
      case Operation.MapEdit(ops)      => applyMapEdit(target, ops, mode, trace)
    }

  private def applyPrimitiveDelta(
    target: DynamicValue,
    op: PrimitiveOp,
    trace: List[Node]
  ): Either[SchemaError, DynamicValue] =
    (target, op) match {
      case (DynamicValue.Primitive(PrimitiveValue.Int(v)), PrimitiveOp.IntDelta(d)) =>
        Right(DynamicValue.Primitive(PrimitiveValue.Int(v + d)))
      case (DynamicValue.Primitive(PrimitiveValue.Long(v)), PrimitiveOp.LongDelta(d)) =>
        Right(DynamicValue.Primitive(PrimitiveValue.Long(v + d)))
      case (DynamicValue.Primitive(PrimitiveValue.Double(v)), PrimitiveOp.DoubleDelta(d)) =>
        Right(DynamicValue.Primitive(PrimitiveValue.Double(v + d)))
      case (DynamicValue.Primitive(PrimitiveValue.Float(v)), PrimitiveOp.FloatDelta(d)) =>
        Right(DynamicValue.Primitive(PrimitiveValue.Float(v + d)))
      case (DynamicValue.Primitive(PrimitiveValue.BigDecimal(v)), PrimitiveOp.BigDecimalDelta(d)) =>
        Right(DynamicValue.Primitive(PrimitiveValue.BigDecimal(v + d)))
      case (DynamicValue.Primitive(PrimitiveValue.BigInt(v)), PrimitiveOp.BigIntDelta(d)) =>
        Right(DynamicValue.Primitive(PrimitiveValue.BigInt(v + d)))

      case (DynamicValue.Primitive(PrimitiveValue.Duration(v)), PrimitiveOp.DurationDelta(n)) =>
        Right(DynamicValue.Primitive(PrimitiveValue.Duration(v.plusNanos(n))))
      case (DynamicValue.Primitive(PrimitiveValue.Instant(v)), PrimitiveOp.InstantDelta(s, n)) =>
        Right(DynamicValue.Primitive(PrimitiveValue.Instant(v.plusSeconds(s).plusNanos(n))))
      case (DynamicValue.Primitive(PrimitiveValue.LocalDate(v)), PrimitiveOp.LocalDateDelta(d)) =>
        Right(DynamicValue.Primitive(PrimitiveValue.LocalDate(v.plusDays(d))))
      case (DynamicValue.Primitive(PrimitiveValue.LocalTime(v)), PrimitiveOp.LocalTimeDelta(n)) =>
        Right(DynamicValue.Primitive(PrimitiveValue.LocalTime(v.plusNanos(n))))
      case (DynamicValue.Primitive(PrimitiveValue.LocalDateTime(v)), PrimitiveOp.LocalDateTimeDelta(n)) =>
        Right(DynamicValue.Primitive(PrimitiveValue.LocalDateTime(v.plusNanos(n))))

      case _ => Left(SchemaError.expectationMismatch(trace, s"Cannot apply delta $op to $target"))
    }

  private def applyStringEdit(
    target: DynamicValue,
    ops: Vector[StringOp],
    trace: List[Node]
  ): Either[SchemaError, DynamicValue] =
    target match {
      case DynamicValue.Primitive(PrimitiveValue.String(s)) =>
        val res = ops.foldLeft(s) {
          case (acc, StringOp.Insert(idx, txt)) =>
            if (idx > acc.length) acc + txt else acc.patch(idx, txt, 0)
          case (acc, StringOp.Delete(idx, len)) =>
            if (idx >= acc.length) acc else acc.patch(idx, "", len)
        }
        Right(DynamicValue.Primitive(PrimitiveValue.String(res)))
      case _ => Left(SchemaError.expectationMismatch(trace, "Expected String for StringEdit"))
    }

  private def applySeqEdit(
    target: DynamicValue,
    ops: Vector[SeqOp],
    mode: PatchMode,
    trace: List[Node]
  ): Either[SchemaError, DynamicValue] =
    target match {
      case DynamicValue.Sequence(initial) =>
        ops
          .foldLeft[Either[SchemaError, Vector[DynamicValue]]](Right(initial)) {
            case (Left(e), _)     => Left(e)
            case (Right(acc), op) =>
              op match {
                case SeqOp.Insert(idx, vs) =>
                  if (idx > acc.size && mode == PatchMode.Strict)
                    Left(SchemaError.expectationMismatch(trace, "Index out of bounds"))
                  else Right(acc.patch(idx, vs, 0))
                case SeqOp.Append(vs)       => Right(acc ++ vs)
                case SeqOp.Delete(idx, cnt) =>
                  if ((idx < 0 || idx + cnt > acc.size) && mode == PatchMode.Strict)
                    Left(SchemaError.expectationMismatch(trace, "Index out of bounds"))
                  else Right(acc.patch(idx, Vector.empty, cnt))
                case SeqOp.Modify(idx, patch) =>
                  if (idx >= acc.size && mode == PatchMode.Strict)
                    Left(SchemaError.expectationMismatch(trace, "Index out of bounds"))
                  else if (idx >= acc.size) Right(acc)
                  else patch.apply(acc(idx))(mode).map(v => acc.updated(idx, v))
              }
          }
          .map(DynamicValue.Sequence(_))
      case _ => Left(SchemaError.expectationMismatch(trace, "Expected Sequence"))
    }

  private def applyMapEdit(
    target: DynamicValue,
    ops: Vector[MapOp],
    mode: PatchMode,
    trace: List[Node]
  ): Either[SchemaError, DynamicValue] =
    target match {
      case DynamicValue.Map(initial) =>
        ops
          .foldLeft[Either[SchemaError, Vector[(DynamicValue, DynamicValue)]]](Right(initial)) {
            case (Left(e), _)     => Left(e)
            case (Right(acc), op) =>
              op match {
                case MapOp.Add(k, v) =>
                  val idx = acc.indexWhere(_._1 == k)
                  if (idx >= 0) {
                    if (mode == PatchMode.Strict) Left(SchemaError.duplicatedField(trace, k.toString))
                    else Right(acc.updated(idx, (k, v)))
                  } else Right(acc :+ (k -> v))
                case MapOp.Remove(k)        => Right(acc.filterNot(_._1 == k))
                case MapOp.Modify(k, patch) =>
                  val idx = acc.indexWhere(_._1 == k)
                  if (idx == -1) {
                    if (mode == PatchMode.Strict) Left(SchemaError.missingField(trace, k.toString))
                    else Right(acc)
                  } else {
                    patch.apply(acc(idx)._2)(mode).map(nv => acc.updated(idx, (k, nv)))
                  }
              }
          }
          .map(DynamicValue.Map(_))
      case _ => Left(SchemaError.expectationMismatch(trace, "Expected Map"))
    }
}

object DynamicPatch {
  val empty = DynamicPatch(Vector.empty)
}
