package zio.blocks.schema.patch

import zio.blocks.chunk.Chunk
import zio.blocks.schema._
import zio.blocks.schema.binding._
import zio.blocks.schema.binding.RegisterOffset.RegisterOffset
import zio.blocks.schema.json.JsonBinaryCodec
import zio.blocks.typeid.TypeId
import java.lang

/**
 * An untyped patch that operates on DynamicValue. Patches are serializable and
 * can be composed.
 */
final case class DynamicPatch(ops: Chunk[DynamicPatch.DynamicPatchOp]) {

  /** Apply this patch to a DynamicValue.`value` and `mode`. */
  def apply(value: DynamicValue, mode: PatchMode = PatchMode.Strict): Either[SchemaError, DynamicValue] = {
    var current: DynamicValue = value
    val len                   = ops.length
    var idx                   = 0
    while (idx < len) {
      val op = ops(idx)
      DynamicPatch.applyOp(current, op.path.nodes, op.operation, mode) match {
        case Right(updated) => current = updated
        case l              => if (mode eq PatchMode.Strict) return l
      }
      idx += 1
    }
    new Right(current)
  }

  /**
   * Compose two patches. The result applies this patch first, then that patch.
   */
  def ++(that: DynamicPatch): DynamicPatch = new DynamicPatch(ops ++ that.ops)

  /** Check if this patch is empty (no operations). */
  def isEmpty: Boolean = ops.isEmpty

  override def toString: String =
    if (ops.isEmpty) "DynamicPatch {}"
    else {
      val sb = new java.lang.StringBuilder("DynamicPatch {\n")
      ops.foreach(op => DynamicPatch.renderOp(sb, op, "  "))
      sb.append('}').toString
    }
}

object DynamicPatch {

  /** Empty patch - identity element for composition. */
  val empty: DynamicPatch = DynamicPatch(Chunk.empty)

  /** Create a patch with a single operation at the root. */
  def root(operation: Operation): DynamicPatch =
    new DynamicPatch(Chunk.single(new DynamicPatchOp(DynamicOptic.root, operation)))

  /** Create a patch with a single operation at the given path. */
  def apply(path: DynamicOptic, operation: Operation): DynamicPatch =
    new DynamicPatch(Chunk.single(new DynamicPatchOp(path, operation)))

  private[patch] def renderOp(sb: lang.StringBuilder, op: DynamicPatchOp, indent: String): Unit = {
    val pathStr = op.path.toString
    op.operation match {
      case Operation.Set(value) =>
        sb.append(indent).append(pathStr).append(" = ").append(value).append('\n')
      case Operation.PrimitiveDelta(primitiveOp) =>
        renderPrimitiveDelta(sb, pathStr, primitiveOp, indent)
      case Operation.SequenceEdit(seqOps) =>
        sb.append(indent).append(pathStr).append(":\n")
        seqOps.foreach(so => renderSeqOp(sb, so, indent + "  "))
      case Operation.MapEdit(mapOps) =>
        sb.append(indent).append(pathStr).append(":\n")
        mapOps.foreach(mo => renderMapOp(sb, mo, indent + "  "))
      case Operation.Patch(nestedPatch) =>
        sb.append(indent).append(pathStr).append(":\n")
        nestedPatch.ops.foreach(op => renderOp(sb, op, indent + "  "))
    }
  }

  private[this] def renderPrimitiveDelta(
    sb: lang.StringBuilder,
    pathStr: String,
    op: PrimitiveOp,
    indent: String
  ): Unit = {
    sb.append(indent)
    op match {
      case PrimitiveOp.IntDelta(d) =>
        if (d >= 0) sb.append(pathStr).append(" += ").append(d).append('\n')
        else sb.append(pathStr).append(" -= ").append(-d).append('\n')
      case PrimitiveOp.LongDelta(d) =>
        if (d >= 0) sb.append(pathStr).append(" += ").append(d).append('\n')
        else sb.append(pathStr).append(" -= ").append(-d).append('\n')
      case PrimitiveOp.DoubleDelta(d) =>
        if (d >= 0) sb.append(pathStr).append(" += ").append(d).append('\n')
        else sb.append(pathStr).append(" -= ").append(-d).append('\n')
      case PrimitiveOp.FloatDelta(d) =>
        if (d >= 0) sb.append(pathStr).append(" += ").append(d).append('\n')
        else sb.append(pathStr).append(" -= ").append(-d).append('\n')
      case PrimitiveOp.ShortDelta(d) =>
        if (d >= 0) sb.append(pathStr).append(" += ").append(d).append('\n')
        else sb.append(pathStr).append(" -= ").append(-d).append('\n')
      case PrimitiveOp.ByteDelta(d) =>
        if (d >= 0) sb.append(pathStr).append(" += ").append(d).append('\n')
        else sb.append(pathStr).append(" -= ").append(-d).append('\n')
      case PrimitiveOp.BigIntDelta(d) =>
        if (d >= BigInt(0)) sb.append(pathStr).append(" += ").append(d).append('\n')
        else sb.append(pathStr).append(" -= ").append(-d).append('\n')
      case PrimitiveOp.BigDecimalDelta(d) =>
        if (d >= BigDecimal(0)) sb.append(pathStr).append(" += ").append(d).append('\n')
        else sb.append(pathStr).append(" -= ").append(-d).append('\n')
      case PrimitiveOp.InstantDelta(d) =>
        sb.append(pathStr).append(" += ").append(d).append('\n')
      case PrimitiveOp.DurationDelta(d) =>
        sb.append(pathStr).append(" += ").append(d).append('\n')
      case PrimitiveOp.LocalDateDelta(d) =>
        sb.append(pathStr).append(" += ").append(d).append('\n')
      case PrimitiveOp.LocalDateTimeDelta(p, d) =>
        sb.append(pathStr).append(" += ").append(p).append(", ").append(d).append('\n')
      case PrimitiveOp.PeriodDelta(d) =>
        sb.append(pathStr).append(" += ").append(d).append('\n')
      case PrimitiveOp.StringEdit(ops) =>
        sb.append(pathStr).append(":\n")
        ops.foreach {
          case StringOp.Insert(idx, text) =>
            sb.append(indent).append("  + [").append(idx).append(": ").append(escapeString(text)).append("]\n")
          case StringOp.Delete(idx, len) =>
            sb.append(indent).append("  - [").append(idx).append(", ").append(len).append("]\n")
          case StringOp.Append(text) =>
            sb.append(indent).append("  + ").append(escapeString(text)).append('\n')
          case StringOp.Modify(idx, len, text) =>
            sb.append(indent)
              .append("  ~ [")
              .append(idx)
              .append(", ")
              .append(len)
              .append(": ")
              .append(escapeString(text))
              .append("]\n")
        }
    }
  }

  private[this] def renderSeqOp(sb: lang.StringBuilder, op: SeqOp, indent: String): Unit = op match {
    case SeqOp.Insert(index, values) =>
      values.foreach {
        var idx = -1
        v =>
          idx += 1
          sb.append(indent).append("+ [").append(index + idx).append(": ").append(v).append("]\n")
      }
    case SeqOp.Append(values)       => values.foreach(v => sb.append(indent).append("+ ").append(v).append('\n'))
    case SeqOp.Delete(index, count) =>
      sb.append(indent)
      if (count == 1) sb.append("- [").append(index).append("]\n")
      else {
        sb.append("- [")
        val limit = index + count
        var idx   = index
        while (idx < limit) {
          if (idx > index) sb.append(", ")
          sb.append(idx)
          idx += 1
        }
        sb.append("]\n")
      }
    case SeqOp.Modify(index, nestedOp) =>
      sb.append(indent)
      nestedOp match {
        case Operation.Set(v) => sb.append("~ [").append(index).append(": ").append(v).append("]\n")
        case _                =>
          sb.append("~ [").append(index).append("]:\n")
          renderOp(sb, DynamicPatchOp(DynamicOptic.root, nestedOp), indent + "  ")
      }
  }

  private[this] def renderMapOp(sb: lang.StringBuilder, op: MapOp, indent: String): Unit = {
    sb.append(indent)
    op match {
      case MapOp.Add(k, v)        => sb.append("+ {").append(k).append(": ").append(v).append("}\n")
      case MapOp.Remove(k)        => sb.append("- {").append(k).append("}\n")
      case MapOp.Modify(k, patch) =>
        sb.append("~ {").append(k).append("}:\n")
        patch.ops.foreach(op => renderOp(sb, op, indent + "  "))
    }
  }

  private[this] def escapeString(s: String): String = JsonBinaryCodec.stringCodec.encodeToString(s)

  // Apply a single operation at a path within a value.
  private[schema] def applyOp(
    value: DynamicValue,
    path: IndexedSeq[DynamicOptic.Node],
    operation: Operation,
    mode: PatchMode
  ): Either[SchemaError, DynamicValue] =
    if (path.isEmpty) applyOperation(value, operation, mode, Nil)
    else navigateAndApply(value, path, 0, operation, mode, Nil)

  // Navigate to the target location and apply the operation.
  // Uses a recursive approach that rebuilds the structure on the way back.
  private[this] def navigateAndApply(
    value: DynamicValue,
    path: IndexedSeq[DynamicOptic.Node],
    pathIdx: Int,
    operation: Operation,
    mode: PatchMode,
    trace: List[DynamicOptic.Node]
  ): Either[SchemaError, DynamicValue] = {
    val node   = path(pathIdx)
    val isLast = pathIdx == path.length - 1
    node match {
      case f: DynamicOptic.Node.Field =>
        val name = f.name
        value match {
          case DynamicValue.Record(fields) =>
            val fieldIdx = fields.indexWhere(_._1 == name)
            if (fieldIdx < 0) new Left(SchemaError.missingField(trace, name))
            else {
              val (fieldName, fieldValue) = fields(fieldIdx)
              val newTrace                = f :: trace
              if (isLast) {
                applyOperation(fieldValue, operation, mode, newTrace).map { newFieldValue =>
                  new DynamicValue.Record(fields.updated(fieldIdx, (fieldName, newFieldValue)))
                }
              } else {
                navigateAndApply(fieldValue, path, pathIdx + 1, operation, mode, newTrace).map { newFieldValue =>
                  new DynamicValue.Record(fields.updated(fieldIdx, (fieldName, newFieldValue)))
                }
              }
            }
          case _ =>
            new Left(SchemaError.expectationMismatch(trace, s"Expected Record but got ${value.getClass.getSimpleName}"))
        }
      case ai: DynamicOptic.Node.AtIndex =>
        val index = ai.index
        value match {
          case DynamicValue.Sequence(elements) =>
            val len = elements.length
            if (index < 0 || index >= len) {
              val msg = s"Index $index out of bounds for sequence of length $len"
              new Left(SchemaError.expectationMismatch(trace, msg))
            } else {
              val element  = elements(index)
              val newTrace = ai :: trace
              if (isLast) {
                applyOperation(element, operation, mode, newTrace).map { newElement =>
                  new DynamicValue.Sequence(elements.updated(index, newElement))
                }
              } else {
                navigateAndApply(element, path, pathIdx + 1, operation, mode, newTrace).map { newElement =>
                  new DynamicValue.Sequence(elements.updated(index, newElement))
                }
              }
            }
          case _ =>
            val msg = s"Expected Sequence but got ${value.getClass.getSimpleName}"
            new Left(SchemaError.expectationMismatch(trace, msg))
        }
      case amk: DynamicOptic.Node.AtMapKey =>
        val key = amk.key
        value match {
          case DynamicValue.Map(entries) =>
            val entryIdx = entries.indexWhere(_._1 == key)
            if (entryIdx < 0) new Left(SchemaError.expectationMismatch(trace, s"Key not found in map"))
            else {
              val (k, v)   = entries(entryIdx)
              val newTrace = amk :: trace
              if (isLast) {
                applyOperation(v, operation, mode, newTrace).map { newValue =>
                  new DynamicValue.Map(entries.updated(entryIdx, (k, newValue)))
                }
              } else {
                navigateAndApply(v, path, pathIdx + 1, operation, mode, newTrace).map { newValue =>
                  new DynamicValue.Map(entries.updated(entryIdx, (k, newValue)))
                }
              }
            }
          case _ =>
            new Left(SchemaError.expectationMismatch(trace, s"Expected Map but got ${value.getClass.getSimpleName}"))
        }
      case c: DynamicOptic.Node.Case =>
        val expectedCase = c.name
        value match {
          case DynamicValue.Variant(caseName, innerValue) =>
            val newTrace = c :: trace
            if (caseName != expectedCase) {
              // Case doesn't match - this is an error in Strict mode
              new Left(SchemaError.expectationMismatch(newTrace, s"Expected case $expectedCase but got $caseName"))
            } else if (isLast) {
              applyOperation(innerValue, operation, mode, newTrace).map { newInnerValue =>
                new DynamicValue.Variant(caseName, newInnerValue)
              }
            } else {
              navigateAndApply(innerValue, path, pathIdx + 1, operation, mode, newTrace).map { newInnerValue =>
                new DynamicValue.Variant(caseName, newInnerValue)
              }
            }
          case _ =>
            val msg = s"Expected Variant but got ${value.getClass.getSimpleName}"
            new Left(SchemaError.expectationMismatch(trace, msg))
        }
      case e: DynamicOptic.Node.Elements.type =>
        value match {
          case DynamicValue.Sequence(elements) =>
            val newTrace = e :: trace
            if (elements.isEmpty) {
              // Empty sequence - in Strict mode, this is an error (can't apply patch to empty list)
              // In Lenient/Clobber mode, return unchanged
              if (mode ne PatchMode.Strict) new Right(value)
              else new Left(SchemaError.expectationMismatch(newTrace, "encountered an empty sequence"))
            } else if (isLast) applyToAllElements(elements, operation, mode, newTrace)
            else navigateAllElements(elements, path, pathIdx + 1, operation, mode, newTrace)
          case _ =>
            val msg = s"Expected Sequence but got ${value.getClass.getSimpleName}"
            new Left(SchemaError.expectationMismatch(trace, msg))
        }
      case w: DynamicOptic.Node.Wrapped.type =>
        // Wrappers in DynamicValue are typically represented as the unwrapped value directly
        // or as a Record with a single field. For now, we pass through.
        val newTrace = w :: trace
        if (isLast) applyOperation(value, operation, mode, newTrace)
        else navigateAndApply(value, path, pathIdx + 1, operation, mode, newTrace)
      case _: DynamicOptic.Node.AtIndices =>
        new Left(SchemaError.expectationMismatch(trace, "AtIndices not supported in patches"))
      case _: DynamicOptic.Node.AtMapKeys =>
        new Left(SchemaError.expectationMismatch(trace, "AtMapKeys not supported in patches"))
      case DynamicOptic.Node.MapKeys =>
        new Left(SchemaError.expectationMismatch(trace, "MapKeys not supported in patches"))
      case DynamicOptic.Node.MapValues =>
        new Left(SchemaError.expectationMismatch(trace, "MapValues not supported in patches"))
      case _: DynamicOptic.Node.TypeSearch =>
        new Left(SchemaError.expectationMismatch(trace, "TypeSearch requires Schema context, not supported in patches"))
      case DynamicOptic.Node.SchemaSearch(pattern) =>
        val newTrace = node :: trace
        if (isLast) {
          // SchemaSearch is the last node - apply operation to all matching values
          schemaSearchApplyOperation(value, pattern, operation, mode, newTrace)
        } else {
          // SchemaSearch is not the last node - navigate deeper through matching values
          schemaSearchNavigate(value, pattern, path, pathIdx + 1, operation, mode, newTrace)
        }
    }
  }

  // Apply operation to all elements in a sequence.
  private[this] def applyToAllElements(
    elements: Chunk[DynamicValue],
    operation: Operation,
    mode: PatchMode,
    trace: List[DynamicOptic.Node]
  ): Either[SchemaError, DynamicValue] = {
    val len     = elements.length
    val results = new Array[DynamicValue](len)
    var idx     = 0
    while (idx < len) {
      val elementTrace = new DynamicOptic.Node.AtIndex(idx) :: trace
      applyOperation(elements(idx), operation, mode, elementTrace) match {
        case Right(updated) => results(idx) = updated
        case l @ Left(err)  =>
          // For Case mismatches within traversals, skip the element even in Strict mode
          val shouldSkip = isCaseMismatch(err)
          if (shouldSkip || mode != PatchMode.Strict) results(idx) = elements(idx) // Keep original on error
          else return l
      }
      idx += 1
    }
    new Right(new DynamicValue.Sequence(Chunk.fromArray(results)))
  }

  // Navigate deeper into all elements of a sequence.
  private[this] def navigateAllElements(
    elements: Chunk[DynamicValue],
    path: IndexedSeq[DynamicOptic.Node],
    pathIdx: Int,
    operation: Operation,
    mode: PatchMode,
    trace: List[DynamicOptic.Node]
  ): Either[SchemaError, DynamicValue] = {
    val len     = elements.length
    val results = new Array[DynamicValue](len)
    var idx     = 0
    // Check if the next path node is a Case - if so, we should skip non-matching elements
    // rather than fail, even in Strict mode (this is Traversal semantics)
    val nextIsCase = pathIdx < path.length && path(pathIdx).isInstanceOf[DynamicOptic.Node.Case]
    while (idx < len) {
      val elementTrace = new DynamicOptic.Node.AtIndex(idx) :: trace
      navigateAndApply(elements(idx), path, pathIdx, operation, mode, elementTrace) match {
        case Right(updated) => results(idx) = updated
        case l @ Left(err)  =>
          // For Case mismatches within traversals, skip the element even in Strict mode
          val shouldSkip = nextIsCase && isCaseMismatch(err)
          if (shouldSkip || mode != PatchMode.Strict) results(idx) = elements(idx) // Keep original on error
          else return l
      }
      idx += 1
    }
    new Right(new DynamicValue.Sequence(Chunk.fromArray(results)))
  }

  // ─────────────────────────────────────────────────────────────────────────
  // SchemaSearch Helper Functions
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Applies an operation to all values matching a SchemaRepr pattern. Uses
   * recursive traversal to find matching values and apply the operation.
   * Returns the modified structure with all matching values updated.
   */
  private def schemaSearchApplyOperation(
    value: DynamicValue,
    pattern: SchemaRepr,
    operation: Operation,
    mode: PatchMode,
    trace: List[DynamicOptic.Node]
  ): Either[SchemaError, DynamicValue] = {
    var found       = false
    var globalError = Option.empty[SchemaError]

    val result = DynamicValue.iterativeTransform(value) { dv =>
      // If we've already hit an error in Strict mode, skip further processing
      if (globalError.isDefined && mode == PatchMode.Strict) dv
      else if (SchemaMatch.matches(pattern, dv)) {
        applyOperation(dv, operation, mode, trace) match {
          case Right(modified) =>
            found = true
            modified
          case Left(err) =>
            mode match {
              case PatchMode.Strict =>
                if (globalError.isEmpty) globalError = Some(err)
                dv
              case PatchMode.Lenient | PatchMode.Clobber =>
                dv
            }
        }
      } else {
        dv
      }
    }

    globalError match {
      case Some(err) => Left(err)
      case None      =>
        if (!found && mode == PatchMode.Strict) {
          Left(SchemaError.expectationMismatch(trace, "No values matched the SchemaSearch pattern"))
        } else {
          Right(result)
        }
    }
  }

  /**
   * Navigates through values matching a SchemaRepr pattern and continues with
   * the remaining path. Uses iterative stack-based traversal to avoid stack
   * overflow on deeply nested structures.
   */
  private def schemaSearchNavigate(
    value: DynamicValue,
    pattern: SchemaRepr,
    path: IndexedSeq[DynamicOptic.Node],
    pathIdx: Int,
    operation: Operation,
    mode: PatchMode,
    trace: List[DynamicOptic.Node]
  ): Either[SchemaError, DynamicValue] = {
    var found       = false
    var globalError = Option.empty[SchemaError]

    // Check if the next path node is a Case - if so, we should skip non-matching elements
    // rather than fail, even in Strict mode (this is Traversal semantics)
    val nextIsCase = pathIdx < path.length && path(pathIdx).isInstanceOf[DynamicOptic.Node.Case]

    val result = DynamicValue.iterativeTransform(value) { dv =>
      // If we've already hit an error in Strict mode, skip further processing
      if (globalError.isDefined && mode == PatchMode.Strict) dv
      else if (SchemaMatch.matches(pattern, dv)) {
        navigateAndApply(dv, path, pathIdx, operation, mode, trace) match {
          case Right(modified) =>
            found = true
            modified
          case Left(err) =>
            val shouldSkip = nextIsCase && isCaseMismatch(err)
            if (shouldSkip) {
              dv
            } else {
              mode match {
                case PatchMode.Strict =>
                  if (globalError.isEmpty) globalError = Some(err)
                  dv
                case PatchMode.Lenient | PatchMode.Clobber =>
                  dv
              }
            }
        }
      } else {
        dv
      }
    }

    globalError match {
      case Some(err) => Left(err)
      case None      =>
        if (!found && mode == PatchMode.Strict) {
          Left(SchemaError.expectationMismatch(trace, "No values matched the SchemaSearch pattern"))
        } else {
          Right(result)
        }
    }
  }

  // Check if an error is a Case mismatch (variant case doesn't match expected).
  private[this] def isCaseMismatch(err: SchemaError): Boolean = err.errors.headOption.exists { single =>
    single.message.contains("Expected case") && single.message.contains("but got")
  }

  // Apply an operation to a value (at the current location).
  private[this] def applyOperation(
    value: DynamicValue,
    operation: Operation,
    mode: PatchMode,
    trace: List[DynamicOptic.Node]
  ): Either[SchemaError, DynamicValue] = operation match {
    case Operation.Set(newValue)        => new Right(newValue)
    case Operation.PrimitiveDelta(op)   => applyPrimitiveDelta(value, op, trace)
    case Operation.SequenceEdit(seqOps) => applySequenceEdit(value, seqOps, mode, trace)
    case Operation.MapEdit(mapOps)      => applyMapEdit(value, mapOps, mode, trace)
    case Operation.Patch(nestedPatch)   => nestedPatch.apply(value, mode)
  }

  private[this] def applyPrimitiveDelta(
    value: DynamicValue,
    op: PrimitiveOp,
    trace: List[DynamicOptic.Node]
  ): Either[SchemaError, DynamicValue] =
    value match {
      case DynamicValue.Primitive(pv) => applyPrimitiveOpToValue(pv, op, trace)
      case _                          =>
        new Left(SchemaError.expectationMismatch(trace, s"Expected Primitive but got ${value.getClass.getSimpleName}"))
    }

  private[this] def applyPrimitiveOpToValue(
    pv: PrimitiveValue,
    op: PrimitiveOp,
    trace: List[DynamicOptic.Node]
  ): Either[SchemaError, DynamicValue] =
    (pv, op) match {
      // Numeric deltas
      case (PrimitiveValue.Int(v), PrimitiveOp.IntDelta(delta)) =>
        new Right(new DynamicValue.Primitive(new PrimitiveValue.Int(v + delta)))
      case (PrimitiveValue.Long(v), PrimitiveOp.LongDelta(delta)) =>
        new Right(new DynamicValue.Primitive(new PrimitiveValue.Long(v + delta)))
      case (PrimitiveValue.Double(v), PrimitiveOp.DoubleDelta(delta)) =>
        new Right(new DynamicValue.Primitive(new PrimitiveValue.Double(v + delta)))
      case (PrimitiveValue.Float(v), PrimitiveOp.FloatDelta(delta)) =>
        new Right(new DynamicValue.Primitive(new PrimitiveValue.Float(v + delta)))
      case (PrimitiveValue.Short(v), PrimitiveOp.ShortDelta(delta)) =>
        new Right(new DynamicValue.Primitive(new PrimitiveValue.Short((v + delta).toShort)))
      case (PrimitiveValue.Byte(v), PrimitiveOp.ByteDelta(delta)) =>
        new Right(new DynamicValue.Primitive(new PrimitiveValue.Byte((v + delta).toByte)))
      case (PrimitiveValue.BigInt(v), PrimitiveOp.BigIntDelta(delta)) =>
        new Right(new DynamicValue.Primitive(new PrimitiveValue.BigInt(v + delta)))
      case (PrimitiveValue.BigDecimal(v), PrimitiveOp.BigDecimalDelta(delta)) =>
        new Right(new DynamicValue.Primitive(new PrimitiveValue.BigDecimal(v + delta)))
      // String edits
      case (PrimitiveValue.String(v), PrimitiveOp.StringEdit(ops)) => applyStringEdits(v, ops, trace)
      // Temporal deltas
      case (PrimitiveValue.Instant(v), PrimitiveOp.InstantDelta(delta)) =>
        new Right(new DynamicValue.Primitive(new PrimitiveValue.Instant(v.plus(delta))))
      case (PrimitiveValue.Duration(v), PrimitiveOp.DurationDelta(delta)) =>
        new Right(new DynamicValue.Primitive(new PrimitiveValue.Duration(v.plus(delta))))
      case (PrimitiveValue.LocalDate(v), PrimitiveOp.LocalDateDelta(delta)) =>
        new Right(new DynamicValue.Primitive(new PrimitiveValue.LocalDate(v.plus(delta))))
      case (PrimitiveValue.LocalDateTime(v), PrimitiveOp.LocalDateTimeDelta(periodDelta, durationDelta)) =>
        new Right(new DynamicValue.Primitive(new PrimitiveValue.LocalDateTime(v.plus(periodDelta).plus(durationDelta))))
      case (PrimitiveValue.Period(v), PrimitiveOp.PeriodDelta(delta)) =>
        new Right(new DynamicValue.Primitive(new PrimitiveValue.Period(v.plus(delta))))
      case _ =>
        val msg = s"Type mismatch: cannot apply ${op.getClass.getSimpleName} to ${pv.getClass.getSimpleName}"
        new Left(SchemaError.expectationMismatch(trace, msg))
    }

  private[this] def applyStringEdits(
    str: String,
    ops: Chunk[StringOp],
    trace: List[DynamicOptic.Node]
  ): Either[SchemaError, DynamicValue] = {
    var result = str
    val opsLen = ops.length
    var idx    = 0
    while (idx < opsLen) {
      ops(idx) match {
        case StringOp.Insert(index, text) =>
          val len = result.length
          if (index < 0 || index > len) {
            val msg = s"String insert index $index out of bounds for string of length $len"
            return new Left(SchemaError.expectationMismatch(trace, msg))
          } else result = result.substring(0, index) + text + result.substring(index)
        case StringOp.Delete(index, length) =>
          val len   = result.length
          val limit = index + length
          if (index < 0 || limit > len) {
            val msg = s"String delete range [$index, $limit) out of bounds for string of length $len"
            return new Left(SchemaError.expectationMismatch(trace, msg))
          } else result = result.substring(0, index) + result.substring(index + length)
        case StringOp.Append(text)                => result = result + text
        case StringOp.Modify(index, length, text) =>
          val len   = result.length
          val limit = index + length
          if (index < 0 || limit > len) {
            val msg = s"String modify range [$index, $limit) out of bounds for string of length $len"
            return new Left(SchemaError.expectationMismatch(trace, msg))
          } else result = result.substring(0, index) + text + result.substring(index + length)
      }
      idx += 1
    }
    new Right(new DynamicValue.Primitive(new PrimitiveValue.String(result)))
  }

  private[this] def applySequenceEdit(
    value: DynamicValue,
    ops: Chunk[SeqOp],
    mode: PatchMode,
    trace: List[DynamicOptic.Node]
  ): Either[SchemaError, DynamicValue] =
    value match {
      case DynamicValue.Sequence(elements) => applySeqOps(elements, ops, mode, trace)
      case _                               =>
        new Left(SchemaError.expectationMismatch(trace, s"Expected Sequence but got ${value.getClass.getSimpleName}"))
    }

  private[this] def applySeqOps(
    elements: Chunk[DynamicValue],
    ops: Chunk[SeqOp],
    mode: PatchMode,
    trace: List[DynamicOptic.Node]
  ): Either[SchemaError, DynamicValue] = {
    var result = elements
    val opslen = ops.length
    var idx    = 0
    while (idx < opslen) {
      applySeqOp(result, ops(idx), mode, trace) match {
        case Right(updated) => result = updated
        case l              => if (mode eq PatchMode.Strict) return l.asInstanceOf[Either[SchemaError, DynamicValue]]
      }
      idx += 1
    }
    new Right(new DynamicValue.Sequence(result))
  }

  private[this] def applySeqOp(
    elements: Chunk[DynamicValue],
    op: SeqOp,
    mode: PatchMode,
    trace: List[DynamicOptic.Node]
  ): Either[SchemaError, Chunk[DynamicValue]] = op match {
    case SeqOp.Append(values)        => new Right(elements ++ values)
    case SeqOp.Insert(index, values) =>
      val len = elements.length
      if (index < 0 || index > len) {
        if (mode eq PatchMode.Clobber) { // In clobber mode, clamp the index
          val clampedIndex    = Math.max(0, Math.min(index, len))
          val (before, after) = elements.splitAt(clampedIndex)
          new Right(before ++ values ++ after)
        } else {
          val msg = s"Insert index $index out of bounds for sequence of length $len"
          new Left(SchemaError.expectationMismatch(trace, msg))
        }
      } else {
        val (before, after) = elements.splitAt(index)
        new Right(before ++ values ++ after)
      }
    case SeqOp.Delete(index, count) =>
      val len   = elements.length
      val limit = index + count
      if (index < 0 || limit > len) {
        if (mode eq PatchMode.Clobber) { // In clobber mode, delete what we can
          val clampedIndex = Math.max(0, Math.min(index, len))
          val clampedEnd   = Math.max(0, Math.min(limit, len))
          new Right(elements.take(clampedIndex) ++ elements.drop(clampedEnd))
        } else {
          val msg = s"Delete range [$index, $limit) out of bounds for sequence of length $len"
          new Left(SchemaError.expectationMismatch(trace, msg))
        }
      } else new Right(elements.take(index) ++ elements.drop(limit))
    case SeqOp.Modify(index, nestedOp) =>
      val len = elements.length
      if (index < 0 || index >= len) {
        val msg = s"Modify index $index out of bounds for sequence of length $len"
        new Left(SchemaError.expectationMismatch(trace, msg))
      } else {
        val element  = elements(index)
        val newTrace = new DynamicOptic.Node.AtIndex(index) :: trace
        applyOperation(element, nestedOp, mode, newTrace).map(elements.updated(index, _))
      }
  }

  private[this] def applyMapEdit(
    value: DynamicValue,
    ops: Chunk[MapOp],
    mode: PatchMode,
    trace: List[DynamicOptic.Node]
  ): Either[SchemaError, DynamicValue] = value match {
    case DynamicValue.Map(entries) => applyMapOps(entries, ops, mode, trace)
    case _                         => new Left(SchemaError.expectationMismatch(trace, s"Expected Map but got ${value.getClass.getSimpleName}"))
  }

  private[this] def applyMapOps(
    entries: Chunk[(DynamicValue, DynamicValue)],
    ops: Chunk[MapOp],
    mode: PatchMode,
    trace: List[DynamicOptic.Node]
  ): Either[SchemaError, DynamicValue] = {
    var result = entries
    val opsLen = ops.length
    var idx    = 0
    while (idx < opsLen) {
      applyMapOp(result, ops(idx), mode, trace) match {
        case Right(updated) => result = updated
        case l              => if (mode eq PatchMode.Strict) return l.asInstanceOf[Either[SchemaError, DynamicValue]]
      }
      idx += 1
    }
    new Right(new DynamicValue.Map(result))
  }

  private[this] def applyMapOp(
    entries: Chunk[(DynamicValue, DynamicValue)],
    op: MapOp,
    mode: PatchMode,
    trace: List[DynamicOptic.Node]
  ): Either[SchemaError, Chunk[(DynamicValue, DynamicValue)]] = op match {
    case MapOp.Add(key, value) =>
      val existingIdx = entries.indexWhere(_._1 == key)
      if (existingIdx >= 0) {
        if (mode eq PatchMode.Clobber) new Right(entries.updated(existingIdx, (key, value)))
        else new Left(SchemaError.expectationMismatch(trace, s"Key already exists in map"))
      } else new Right(entries :+ (key, value))
    case MapOp.Remove(key) =>
      val existingIdx = entries.indexWhere(_._1 == key)
      if (existingIdx < 0) {
        if (mode eq PatchMode.Clobber) new Right(entries) // Nothing to remove, return unchanged
        else new Left(SchemaError.expectationMismatch(trace, s"Key not found in map"))
      } else new Right(entries.take(existingIdx) ++ entries.drop(existingIdx + 1))
    case MapOp.Modify(key, nestedPatch) =>
      val existingIdx = entries.indexWhere(_._1 == key)
      if (existingIdx < 0) new Left(SchemaError.expectationMismatch(trace, s"Key not found in map"))
      else {
        val (k, v) = entries(existingIdx)
        nestedPatch.apply(v, mode).map(newValue => entries.updated(existingIdx, (k, newValue)))
      }
  }

  // Internal patch operation types

  /** A single patch operation paired with the path to apply it at. */
  final case class DynamicPatchOp(path: DynamicOptic, operation: Operation)

  // Top-level operation type for patches, each operation describes a change to be applied to a DynamicValue.
  sealed trait Operation

  object Operation {

    /**
     * Set a value directly (clobber semantics). Replaces the target value
     * entirely.
     */
    final case class Set(value: DynamicValue) extends Operation

    /**
     * Apply a primitive delta operation. Used for numeric increments, string
     * edits, temporal adjustments, etc.
     */
    final case class PrimitiveDelta(op: PrimitiveOp) extends Operation

    /**
     * Apply sequence edit operations. Used for inserting, appending, deleting,
     * or modifying sequence elements.
     */
    final case class SequenceEdit(ops: Chunk[SeqOp]) extends Operation

    /**
     * Apply map edit operations. Used for adding, removing, or modifying map
     * entries.
     */
    final case class MapEdit(ops: Chunk[MapOp]) extends Operation

    /**
     * Apply a nested patch. Used to group multiple operations that share a
     * common path prefix, avoiding path repetition in nested structures.
     */
    final case class Patch(patch: DynamicPatch) extends Operation
  }

  // Primitive delta operations for numeric types, strings, and temporal types.
  sealed trait PrimitiveOp

  object PrimitiveOp {

    // Delta for Primitive values. Applied by adding delta to the current value.

    final case class IntDelta(delta: Int) extends PrimitiveOp

    final case class LongDelta(delta: Long) extends PrimitiveOp

    final case class DoubleDelta(delta: Double) extends PrimitiveOp

    final case class FloatDelta(delta: Float) extends PrimitiveOp

    final case class ShortDelta(delta: Short) extends PrimitiveOp

    final case class ByteDelta(delta: Byte) extends PrimitiveOp

    final case class BigIntDelta(delta: BigInt) extends PrimitiveOp

    final case class BigDecimalDelta(delta: BigDecimal) extends PrimitiveOp

    final case class StringEdit(ops: Chunk[StringOp]) extends PrimitiveOp

    final case class InstantDelta(delta: java.time.Duration) extends PrimitiveOp

    final case class DurationDelta(delta: java.time.Duration) extends PrimitiveOp

    final case class LocalDateDelta(delta: java.time.Period) extends PrimitiveOp

    // Delta for LocalDateTime values. Applied by adding period and duration.
    final case class LocalDateTimeDelta(periodDelta: java.time.Period, durationDelta: java.time.Duration)
        extends PrimitiveOp

    final case class PeriodDelta(delta: java.time.Period) extends PrimitiveOp
  }

  /** Sequence edit operations for lists, chunks, and other sequences. */
  sealed trait SeqOp

  object SeqOp {

    /** Insert elements at the given index. */
    final case class Insert(index: Int, values: Chunk[DynamicValue]) extends SeqOp

    /** Append elements to the end of the sequence. */
    final case class Append(values: Chunk[DynamicValue]) extends SeqOp

    /** Delete elements starting at the given index. */
    final case class Delete(index: Int, count: Int) extends SeqOp

    /** Modify the element at the given index with a nested operation. */
    final case class Modify(index: Int, op: Operation) extends SeqOp
  }

  /** String edit operations for insert, delete, append, and modify. */
  sealed trait StringOp

  object StringOp {

    /** Insert text at the given index. */
    final case class Insert(index: Int, text: String) extends StringOp

    /** Delete characters starting at the given index. */
    final case class Delete(index: Int, length: Int) extends StringOp

    /** Append text to the end of the string. */
    final case class Append(text: String) extends StringOp

    /**
     * Modify (replace) characters starting at the given index with new text.
     */
    final case class Modify(index: Int, length: Int, text: String) extends StringOp
  }

  /** Map edit operations for adding, removing, and modifying map entries. */
  sealed trait MapOp

  object MapOp {

    /** Add a key-value pair to the map. */
    final case class Add(key: DynamicValue, value: DynamicValue) extends MapOp

    /** Remove a key from the map. */
    final case class Remove(key: DynamicValue) extends MapOp

    /** Modify the value at a key with a nested patch. */
    final case class Modify(key: DynamicValue, patch: DynamicPatch) extends MapOp
  }

  // Dummy implicit ladder for Duration-related operations.
  // Disambiguates overloads: addDuration for Instant vs Duration.
  sealed abstract class DurationDummy
  object DurationDummy {
    implicit object ForInstant  extends DurationDummy
    implicit object ForDuration extends DurationDummy
  }

  // Dummy implicit ladder for Period-related operations.
  // Disambiguates overloads: addPeriod for LocalDate vs Period.
  sealed abstract class PeriodDummy
  object PeriodDummy {
    implicit object ForLocalDate extends PeriodDummy
    implicit object ForPeriod    extends PeriodDummy
  }

  // Dummy implicit ladder for Collection-related operations.
  // Disambiguates overloads: append/insertAt/deleteAt/modifyAt for different collection types.
  // Note that there are no tests for LazyList, schema.derived on LazyList leads to malformed tree.
  sealed abstract class CollectionDummy
  object CollectionDummy {
    implicit object ForVector     extends CollectionDummy
    implicit object ForList       extends CollectionDummy
    implicit object ForSeq        extends CollectionDummy
    implicit object ForIndexedSeq extends CollectionDummy
    implicit object ForLazyList   extends CollectionDummy
  }

  // Schema instances for DynamicPatch types
  // Manual derivation for Scala 2 compatability
  // In Scala 3, You can derive Schema using `Schema.derive`

  implicit lazy val stringOpInsertSchema: Schema[StringOp.Insert] =
    new Schema(
      reflect = new Reflect.Record[Binding, StringOp.Insert](
        fields = Vector(Schema[Int].reflect.asTerm("index"), Schema[String].reflect.asTerm("text")),
        typeId = TypeId.of[StringOp.Insert],
        recordBinding = new Binding.Record(
          constructor = new Constructor[StringOp.Insert] {
            def usedRegisters: RegisterOffset                                     = RegisterOffset(ints = 1, objects = 1)
            def construct(in: Registers, offset: RegisterOffset): StringOp.Insert =
              StringOp.Insert(in.getInt(offset), in.getObject(offset).asInstanceOf[String])
          },
          deconstructor = new Deconstructor[StringOp.Insert] {
            def usedRegisters: RegisterOffset                                                  = RegisterOffset(ints = 1, objects = 1)
            def deconstruct(out: Registers, offset: RegisterOffset, in: StringOp.Insert): Unit = {
              out.setInt(offset, in.index)
              out.setObject(offset, in.text)
            }
          }
        ),
        modifiers = Vector.empty
      )
    )

  implicit lazy val stringOpDeleteSchema: Schema[StringOp.Delete] =
    new Schema(
      reflect = new Reflect.Record[Binding, StringOp.Delete](
        fields = Vector(Schema[Int].reflect.asTerm("index"), Schema[Int].reflect.asTerm("length")),
        typeId = TypeId.of[StringOp.Delete],
        recordBinding = new Binding.Record(
          constructor = new Constructor[StringOp.Delete] {
            def usedRegisters: RegisterOffset                                     = RegisterOffset(ints = 2)
            def construct(in: Registers, offset: RegisterOffset): StringOp.Delete =
              StringOp.Delete(
                in.getInt(offset),
                in.getInt(RegisterOffset.incrementFloatsAndInts(offset))
              )
          },
          deconstructor = new Deconstructor[StringOp.Delete] {
            def usedRegisters: RegisterOffset                                                  = RegisterOffset(ints = 2)
            def deconstruct(out: Registers, offset: RegisterOffset, in: StringOp.Delete): Unit = {
              out.setInt(offset, in.index)
              out.setInt(RegisterOffset.incrementFloatsAndInts(offset), in.length)
            }
          }
        ),
        modifiers = Vector.empty
      )
    )

  implicit lazy val stringOpAppendSchema: Schema[StringOp.Append] =
    new Schema(
      reflect = new Reflect.Record[Binding, StringOp.Append](
        fields = Vector(Schema[String].reflect.asTerm("text")),
        typeId = TypeId.of[StringOp.Append],
        recordBinding = new Binding.Record(
          constructor = new Constructor[StringOp.Append] {
            def usedRegisters: RegisterOffset                                     = RegisterOffset(objects = 1)
            def construct(in: Registers, offset: RegisterOffset): StringOp.Append =
              StringOp.Append(in.getObject(offset).asInstanceOf[String])
          },
          deconstructor = new Deconstructor[StringOp.Append] {
            def usedRegisters: RegisterOffset                                                  = RegisterOffset(objects = 1)
            def deconstruct(out: Registers, offset: RegisterOffset, in: StringOp.Append): Unit =
              out.setObject(offset, in.text)
          }
        ),
        modifiers = Vector.empty
      )
    )

  implicit lazy val stringOpModifySchema: Schema[StringOp.Modify] =
    new Schema(
      reflect = new Reflect.Record[Binding, StringOp.Modify](
        fields = Vector(
          Schema[Int].reflect.asTerm("index"),
          Schema[Int].reflect.asTerm("length"),
          Schema[String].reflect.asTerm("text")
        ),
        typeId = TypeId.of[StringOp.Modify],
        recordBinding = new Binding.Record(
          constructor = new Constructor[StringOp.Modify] {
            def usedRegisters: RegisterOffset                                     = RegisterOffset(ints = 2, objects = 1)
            def construct(in: Registers, offset: RegisterOffset): StringOp.Modify =
              StringOp.Modify(
                in.getInt(offset),
                in.getInt(RegisterOffset.incrementFloatsAndInts(offset)),
                in.getObject(offset).asInstanceOf[String]
              )
          },
          deconstructor = new Deconstructor[StringOp.Modify] {
            def usedRegisters: RegisterOffset                                                  = RegisterOffset(ints = 2, objects = 1)
            def deconstruct(out: Registers, offset: RegisterOffset, in: StringOp.Modify): Unit = {
              out.setInt(offset, in.index)
              out.setInt(RegisterOffset.incrementFloatsAndInts(offset), in.length)
              out.setObject(offset, in.text)
            }
          }
        ),
        modifiers = Vector.empty
      )
    )

  implicit lazy val stringOpSchema: Schema[StringOp] = new Schema(
    reflect = new Reflect.Variant[Binding, StringOp](
      cases = Vector(
        stringOpInsertSchema.reflect.asTerm("Insert"),
        stringOpDeleteSchema.reflect.asTerm("Delete"),
        stringOpAppendSchema.reflect.asTerm("Append"),
        stringOpModifySchema.reflect.asTerm("Modify")
      ),
      typeId = TypeId.of[StringOp],
      variantBinding = new Binding.Variant(
        discriminator = new Discriminator[StringOp] {
          def discriminate(a: StringOp): Int = a match {
            case _: StringOp.Insert => 0
            case _: StringOp.Delete => 1
            case _: StringOp.Append => 2
            case _: StringOp.Modify => 3
          }
        },
        matchers = Matchers(
          new Matcher[StringOp.Insert] {
            def downcastOrNull(a: Any): StringOp.Insert = a match {
              case x: StringOp.Insert => x
              case _                  => null.asInstanceOf[StringOp.Insert]
            }
          },
          new Matcher[StringOp.Delete] {
            def downcastOrNull(a: Any): StringOp.Delete = a match {
              case x: StringOp.Delete => x
              case _                  => null.asInstanceOf[StringOp.Delete]
            }
          },
          new Matcher[StringOp.Append] {
            def downcastOrNull(a: Any): StringOp.Append = a match {
              case x: StringOp.Append => x
              case _                  => null.asInstanceOf[StringOp.Append]
            }
          },
          new Matcher[StringOp.Modify] {
            def downcastOrNull(a: Any): StringOp.Modify = a match {
              case x: StringOp.Modify => x
              case _                  => null.asInstanceOf[StringOp.Modify]
            }
          }
        )
      ),
      modifiers = Vector.empty
    )
  )

  implicit lazy val primitiveOpIntDeltaSchema: Schema[PrimitiveOp.IntDelta] =
    new Schema(
      reflect = new Reflect.Record[Binding, PrimitiveOp.IntDelta](
        fields = Vector(Schema[Int].reflect.asTerm("delta")),
        typeId = TypeId.of[PrimitiveOp.IntDelta],
        recordBinding = new Binding.Record(
          constructor = new Constructor[PrimitiveOp.IntDelta] {
            def usedRegisters: RegisterOffset                                          = RegisterOffset(ints = 1)
            def construct(in: Registers, offset: RegisterOffset): PrimitiveOp.IntDelta =
              PrimitiveOp.IntDelta(in.getInt(offset))
          },
          deconstructor = new Deconstructor[PrimitiveOp.IntDelta] {
            def usedRegisters: RegisterOffset                                                       = RegisterOffset(ints = 1)
            def deconstruct(out: Registers, offset: RegisterOffset, in: PrimitiveOp.IntDelta): Unit =
              out.setInt(offset, in.delta)
          }
        ),
        modifiers = Vector.empty
      )
    )

  implicit lazy val primitiveOpLongDeltaSchema: Schema[PrimitiveOp.LongDelta] =
    new Schema(
      reflect = new Reflect.Record[Binding, PrimitiveOp.LongDelta](
        fields = Vector(Schema[Long].reflect.asTerm("delta")),
        typeId = TypeId.of[PrimitiveOp.LongDelta],
        recordBinding = new Binding.Record(
          constructor = new Constructor[PrimitiveOp.LongDelta] {
            def usedRegisters: RegisterOffset                                           = RegisterOffset(longs = 1)
            def construct(in: Registers, offset: RegisterOffset): PrimitiveOp.LongDelta =
              PrimitiveOp.LongDelta(in.getLong(offset))
          },
          deconstructor = new Deconstructor[PrimitiveOp.LongDelta] {
            def usedRegisters: RegisterOffset                                                        = RegisterOffset(longs = 1)
            def deconstruct(out: Registers, offset: RegisterOffset, in: PrimitiveOp.LongDelta): Unit =
              out.setLong(offset, in.delta)
          }
        ),
        modifiers = Vector.empty
      )
    )

  implicit lazy val primitiveOpDoubleDeltaSchema: Schema[PrimitiveOp.DoubleDelta] =
    new Schema(
      reflect = new Reflect.Record[Binding, PrimitiveOp.DoubleDelta](
        fields = Vector(Schema[Double].reflect.asTerm("delta")),
        typeId = TypeId.of[PrimitiveOp.DoubleDelta],
        recordBinding = new Binding.Record(
          constructor = new Constructor[PrimitiveOp.DoubleDelta] {
            def usedRegisters: RegisterOffset                                             = RegisterOffset(doubles = 1)
            def construct(in: Registers, offset: RegisterOffset): PrimitiveOp.DoubleDelta =
              PrimitiveOp.DoubleDelta(in.getDouble(offset))
          },
          deconstructor = new Deconstructor[PrimitiveOp.DoubleDelta] {
            def usedRegisters: RegisterOffset                                                          = RegisterOffset(doubles = 1)
            def deconstruct(out: Registers, offset: RegisterOffset, in: PrimitiveOp.DoubleDelta): Unit =
              out.setDouble(offset, in.delta)
          }
        ),
        modifiers = Vector.empty
      )
    )

  implicit lazy val primitiveOpFloatDeltaSchema: Schema[PrimitiveOp.FloatDelta] =
    new Schema(
      reflect = new Reflect.Record[Binding, PrimitiveOp.FloatDelta](
        fields = Vector(Schema[Float].reflect.asTerm("delta")),
        typeId = TypeId.of[PrimitiveOp.FloatDelta],
        recordBinding = new Binding.Record(
          constructor = new Constructor[PrimitiveOp.FloatDelta] {
            def usedRegisters: RegisterOffset                                            = RegisterOffset(floats = 1)
            def construct(in: Registers, offset: RegisterOffset): PrimitiveOp.FloatDelta =
              PrimitiveOp.FloatDelta(in.getFloat(offset))
          },
          deconstructor = new Deconstructor[PrimitiveOp.FloatDelta] {
            def usedRegisters: RegisterOffset                                                         = RegisterOffset(floats = 1)
            def deconstruct(out: Registers, offset: RegisterOffset, in: PrimitiveOp.FloatDelta): Unit =
              out.setFloat(offset, in.delta)
          }
        ),
        modifiers = Vector.empty
      )
    )

  implicit lazy val primitiveOpShortDeltaSchema: Schema[PrimitiveOp.ShortDelta] =
    new Schema(
      reflect = new Reflect.Record[Binding, PrimitiveOp.ShortDelta](
        fields = Vector(Schema[Short].reflect.asTerm("delta")),
        typeId = TypeId.of[PrimitiveOp.ShortDelta],
        recordBinding = new Binding.Record(
          constructor = new Constructor[PrimitiveOp.ShortDelta] {
            def usedRegisters: RegisterOffset                                            = RegisterOffset(shorts = 1)
            def construct(in: Registers, offset: RegisterOffset): PrimitiveOp.ShortDelta =
              PrimitiveOp.ShortDelta(in.getShort(offset))
          },
          deconstructor = new Deconstructor[PrimitiveOp.ShortDelta] {
            def usedRegisters: RegisterOffset                                                         = RegisterOffset(shorts = 1)
            def deconstruct(out: Registers, offset: RegisterOffset, in: PrimitiveOp.ShortDelta): Unit =
              out.setShort(offset, in.delta)
          }
        ),
        modifiers = Vector.empty
      )
    )

  implicit lazy val primitiveOpByteDeltaSchema: Schema[PrimitiveOp.ByteDelta] =
    new Schema(
      reflect = new Reflect.Record[Binding, PrimitiveOp.ByteDelta](
        fields = Vector(Schema[Byte].reflect.asTerm("delta")),
        typeId = TypeId.of[PrimitiveOp.ByteDelta],
        recordBinding = new Binding.Record(
          constructor = new Constructor[PrimitiveOp.ByteDelta] {
            def usedRegisters: RegisterOffset                                           = RegisterOffset(bytes = 1)
            def construct(in: Registers, offset: RegisterOffset): PrimitiveOp.ByteDelta =
              PrimitiveOp.ByteDelta(in.getByte(offset))
          },
          deconstructor = new Deconstructor[PrimitiveOp.ByteDelta] {
            def usedRegisters: RegisterOffset                                                        = RegisterOffset(bytes = 1)
            def deconstruct(out: Registers, offset: RegisterOffset, in: PrimitiveOp.ByteDelta): Unit =
              out.setByte(offset, in.delta)
          }
        ),
        modifiers = Vector.empty
      )
    )

  implicit lazy val primitiveOpBigIntDeltaSchema: Schema[PrimitiveOp.BigIntDelta] =
    new Schema(
      reflect = new Reflect.Record[Binding, PrimitiveOp.BigIntDelta](
        fields = Vector(Schema[BigInt].reflect.asTerm("delta")),
        typeId = TypeId.of[PrimitiveOp.BigIntDelta],
        recordBinding = new Binding.Record(
          constructor = new Constructor[PrimitiveOp.BigIntDelta] {
            def usedRegisters: RegisterOffset                                             = RegisterOffset(objects = 1)
            def construct(in: Registers, offset: RegisterOffset): PrimitiveOp.BigIntDelta =
              PrimitiveOp.BigIntDelta(in.getObject(offset).asInstanceOf[BigInt])
          },
          deconstructor = new Deconstructor[PrimitiveOp.BigIntDelta] {
            def usedRegisters: RegisterOffset                                                          = RegisterOffset(objects = 1)
            def deconstruct(out: Registers, offset: RegisterOffset, in: PrimitiveOp.BigIntDelta): Unit =
              out.setObject(offset, in.delta)
          }
        ),
        modifiers = Vector.empty
      )
    )

  implicit lazy val primitiveOpBigDecimalDeltaSchema: Schema[PrimitiveOp.BigDecimalDelta] =
    new Schema(
      reflect = new Reflect.Record[Binding, PrimitiveOp.BigDecimalDelta](
        fields = Vector(Schema[BigDecimal].reflect.asTerm("delta")),
        typeId = TypeId.of[PrimitiveOp.BigDecimalDelta],
        recordBinding = new Binding.Record(
          constructor = new Constructor[PrimitiveOp.BigDecimalDelta] {
            def usedRegisters: RegisterOffset                                                 = RegisterOffset(objects = 1)
            def construct(in: Registers, offset: RegisterOffset): PrimitiveOp.BigDecimalDelta =
              PrimitiveOp.BigDecimalDelta(in.getObject(offset).asInstanceOf[BigDecimal])
          },
          deconstructor = new Deconstructor[PrimitiveOp.BigDecimalDelta] {
            def usedRegisters: RegisterOffset                                                              = RegisterOffset(objects = 1)
            def deconstruct(out: Registers, offset: RegisterOffset, in: PrimitiveOp.BigDecimalDelta): Unit =
              out.setObject(offset, in.delta)
          }
        ),
        modifiers = Vector.empty
      )
    )

  implicit lazy val primitiveOpStringEditSchema: Schema[PrimitiveOp.StringEdit] =
    new Schema(
      reflect = new Reflect.Record[Binding, PrimitiveOp.StringEdit](
        fields = Vector(Schema[Chunk[StringOp]].reflect.asTerm("ops")),
        typeId = TypeId.of[PrimitiveOp.StringEdit],
        recordBinding = new Binding.Record(
          constructor = new Constructor[PrimitiveOp.StringEdit] {
            def usedRegisters: RegisterOffset                                            = RegisterOffset(objects = 1)
            def construct(in: Registers, offset: RegisterOffset): PrimitiveOp.StringEdit =
              PrimitiveOp.StringEdit(in.getObject(offset).asInstanceOf[Chunk[StringOp]])
          },
          deconstructor = new Deconstructor[PrimitiveOp.StringEdit] {
            def usedRegisters: RegisterOffset                                                         = RegisterOffset(objects = 1)
            def deconstruct(out: Registers, offset: RegisterOffset, in: PrimitiveOp.StringEdit): Unit =
              out.setObject(offset, in.ops)
          }
        ),
        modifiers = Vector.empty
      )
    )

  implicit lazy val primitiveOpInstantDeltaSchema: Schema[PrimitiveOp.InstantDelta] =
    new Schema(
      reflect = new Reflect.Record[Binding, PrimitiveOp.InstantDelta](
        fields = Vector(Schema[java.time.Duration].reflect.asTerm("delta")),
        typeId = TypeId.of[PrimitiveOp.InstantDelta],
        recordBinding = new Binding.Record(
          constructor = new Constructor[PrimitiveOp.InstantDelta] {
            def usedRegisters: RegisterOffset                                              = RegisterOffset(objects = 1)
            def construct(in: Registers, offset: RegisterOffset): PrimitiveOp.InstantDelta =
              PrimitiveOp.InstantDelta(in.getObject(offset).asInstanceOf[java.time.Duration])
          },
          deconstructor = new Deconstructor[PrimitiveOp.InstantDelta] {
            def usedRegisters: RegisterOffset                                                           = RegisterOffset(objects = 1)
            def deconstruct(out: Registers, offset: RegisterOffset, in: PrimitiveOp.InstantDelta): Unit =
              out.setObject(offset, in.delta)
          }
        ),
        modifiers = Vector.empty
      )
    )

  implicit lazy val primitiveOpDurationDeltaSchema: Schema[PrimitiveOp.DurationDelta] =
    new Schema(
      reflect = new Reflect.Record[Binding, PrimitiveOp.DurationDelta](
        fields = Vector(Schema[java.time.Duration].reflect.asTerm("delta")),
        typeId = TypeId.of[PrimitiveOp.DurationDelta],
        recordBinding = new Binding.Record(
          constructor = new Constructor[PrimitiveOp.DurationDelta] {
            def usedRegisters: RegisterOffset                                               = RegisterOffset(objects = 1)
            def construct(in: Registers, offset: RegisterOffset): PrimitiveOp.DurationDelta =
              PrimitiveOp.DurationDelta(in.getObject(offset).asInstanceOf[java.time.Duration])
          },
          deconstructor = new Deconstructor[PrimitiveOp.DurationDelta] {
            def usedRegisters: RegisterOffset                                                            = RegisterOffset(objects = 1)
            def deconstruct(out: Registers, offset: RegisterOffset, in: PrimitiveOp.DurationDelta): Unit =
              out.setObject(offset, in.delta)
          }
        ),
        modifiers = Vector.empty
      )
    )

  implicit lazy val primitiveOpLocalDateDeltaSchema: Schema[PrimitiveOp.LocalDateDelta] =
    new Schema(
      reflect = new Reflect.Record[Binding, PrimitiveOp.LocalDateDelta](
        fields = Vector(Schema[java.time.Period].reflect.asTerm("delta")),
        typeId = TypeId.of[PrimitiveOp.LocalDateDelta],
        recordBinding = new Binding.Record(
          constructor = new Constructor[PrimitiveOp.LocalDateDelta] {
            def usedRegisters: RegisterOffset                                                = RegisterOffset(objects = 1)
            def construct(in: Registers, offset: RegisterOffset): PrimitiveOp.LocalDateDelta =
              PrimitiveOp.LocalDateDelta(in.getObject(offset).asInstanceOf[java.time.Period])
          },
          deconstructor = new Deconstructor[PrimitiveOp.LocalDateDelta] {
            def usedRegisters: RegisterOffset                                                             = RegisterOffset(objects = 1)
            def deconstruct(out: Registers, offset: RegisterOffset, in: PrimitiveOp.LocalDateDelta): Unit =
              out.setObject(offset, in.delta)
          }
        ),
        modifiers = Vector.empty
      )
    )

  implicit lazy val primitiveOpLocalDateTimeDeltaSchema: Schema[PrimitiveOp.LocalDateTimeDelta] =
    new Schema(
      reflect = new Reflect.Record[Binding, PrimitiveOp.LocalDateTimeDelta](
        fields = Vector(
          Schema[java.time.Period].reflect.asTerm("periodDelta"),
          Schema[java.time.Duration].reflect.asTerm("durationDelta")
        ),
        typeId = TypeId.of[PrimitiveOp.LocalDateTimeDelta],
        recordBinding = new Binding.Record(
          constructor = new Constructor[PrimitiveOp.LocalDateTimeDelta] {
            def usedRegisters: RegisterOffset                                                    = RegisterOffset(objects = 2)
            def construct(in: Registers, offset: RegisterOffset): PrimitiveOp.LocalDateTimeDelta =
              PrimitiveOp.LocalDateTimeDelta(
                in.getObject(offset).asInstanceOf[java.time.Period],
                in.getObject(RegisterOffset.incrementObjects(offset)).asInstanceOf[java.time.Duration]
              )
          },
          deconstructor = new Deconstructor[PrimitiveOp.LocalDateTimeDelta] {
            def usedRegisters: RegisterOffset                                                                 = RegisterOffset(objects = 2)
            def deconstruct(out: Registers, offset: RegisterOffset, in: PrimitiveOp.LocalDateTimeDelta): Unit = {
              out.setObject(offset, in.periodDelta)
              out.setObject(RegisterOffset.incrementObjects(offset), in.durationDelta)
            }
          }
        ),
        modifiers = Vector.empty
      )
    )

  implicit lazy val primitiveOpPeriodDeltaSchema: Schema[PrimitiveOp.PeriodDelta] =
    new Schema(
      reflect = new Reflect.Record[Binding, PrimitiveOp.PeriodDelta](
        fields = Vector(Schema[java.time.Period].reflect.asTerm("delta")),
        typeId = TypeId.of[PrimitiveOp.PeriodDelta],
        recordBinding = new Binding.Record(
          constructor = new Constructor[PrimitiveOp.PeriodDelta] {
            def usedRegisters: RegisterOffset                                             = RegisterOffset(objects = 1)
            def construct(in: Registers, offset: RegisterOffset): PrimitiveOp.PeriodDelta =
              PrimitiveOp.PeriodDelta(in.getObject(offset).asInstanceOf[java.time.Period])
          },
          deconstructor = new Deconstructor[PrimitiveOp.PeriodDelta] {
            def usedRegisters: RegisterOffset                                                          = RegisterOffset(objects = 1)
            def deconstruct(out: Registers, offset: RegisterOffset, in: PrimitiveOp.PeriodDelta): Unit =
              out.setObject(offset, in.delta)
          }
        ),
        modifiers = Vector.empty
      )
    )

  implicit lazy val primitiveOpSchema: Schema[PrimitiveOp] = new Schema(
    reflect = new Reflect.Variant[Binding, PrimitiveOp](
      cases = Vector(
        primitiveOpIntDeltaSchema.reflect.asTerm("IntDelta"),
        primitiveOpLongDeltaSchema.reflect.asTerm("LongDelta"),
        primitiveOpDoubleDeltaSchema.reflect.asTerm("DoubleDelta"),
        primitiveOpFloatDeltaSchema.reflect.asTerm("FloatDelta"),
        primitiveOpShortDeltaSchema.reflect.asTerm("ShortDelta"),
        primitiveOpByteDeltaSchema.reflect.asTerm("ByteDelta"),
        primitiveOpBigIntDeltaSchema.reflect.asTerm("BigIntDelta"),
        primitiveOpBigDecimalDeltaSchema.reflect.asTerm("BigDecimalDelta"),
        primitiveOpStringEditSchema.reflect.asTerm("StringEdit"),
        primitiveOpInstantDeltaSchema.reflect.asTerm("InstantDelta"),
        primitiveOpDurationDeltaSchema.reflect.asTerm("DurationDelta"),
        primitiveOpLocalDateDeltaSchema.reflect.asTerm("LocalDateDelta"),
        primitiveOpLocalDateTimeDeltaSchema.reflect.asTerm("LocalDateTimeDelta"),
        primitiveOpPeriodDeltaSchema.reflect.asTerm("PeriodDelta")
      ),
      typeId = TypeId.of[PrimitiveOp],
      variantBinding = new Binding.Variant(
        discriminator = new Discriminator[PrimitiveOp] {
          def discriminate(a: PrimitiveOp): Int = a match {
            case _: PrimitiveOp.IntDelta           => 0
            case _: PrimitiveOp.LongDelta          => 1
            case _: PrimitiveOp.DoubleDelta        => 2
            case _: PrimitiveOp.FloatDelta         => 3
            case _: PrimitiveOp.ShortDelta         => 4
            case _: PrimitiveOp.ByteDelta          => 5
            case _: PrimitiveOp.BigIntDelta        => 6
            case _: PrimitiveOp.BigDecimalDelta    => 7
            case _: PrimitiveOp.StringEdit         => 8
            case _: PrimitiveOp.InstantDelta       => 9
            case _: PrimitiveOp.DurationDelta      => 10
            case _: PrimitiveOp.LocalDateDelta     => 11
            case _: PrimitiveOp.LocalDateTimeDelta => 12
            case _: PrimitiveOp.PeriodDelta        => 13
          }
        },
        matchers = Matchers(
          new Matcher[PrimitiveOp.IntDelta] {
            def downcastOrNull(a: Any): PrimitiveOp.IntDelta = a match {
              case x: PrimitiveOp.IntDelta => x
              case _                       => null.asInstanceOf[PrimitiveOp.IntDelta]
            }
          },
          new Matcher[PrimitiveOp.LongDelta] {
            def downcastOrNull(a: Any): PrimitiveOp.LongDelta = a match {
              case x: PrimitiveOp.LongDelta => x
              case _                        => null.asInstanceOf[PrimitiveOp.LongDelta]
            }
          },
          new Matcher[PrimitiveOp.DoubleDelta] {
            def downcastOrNull(a: Any): PrimitiveOp.DoubleDelta = a match {
              case x: PrimitiveOp.DoubleDelta => x
              case _                          => null.asInstanceOf[PrimitiveOp.DoubleDelta]
            }
          },
          new Matcher[PrimitiveOp.FloatDelta] {
            def downcastOrNull(a: Any): PrimitiveOp.FloatDelta = a match {
              case x: PrimitiveOp.FloatDelta => x
              case _                         => null.asInstanceOf[PrimitiveOp.FloatDelta]
            }
          },
          new Matcher[PrimitiveOp.ShortDelta] {
            def downcastOrNull(a: Any): PrimitiveOp.ShortDelta = a match {
              case x: PrimitiveOp.ShortDelta => x
              case _                         => null.asInstanceOf[PrimitiveOp.ShortDelta]
            }
          },
          new Matcher[PrimitiveOp.ByteDelta] {
            def downcastOrNull(a: Any): PrimitiveOp.ByteDelta = a match {
              case x: PrimitiveOp.ByteDelta => x
              case _                        => null.asInstanceOf[PrimitiveOp.ByteDelta]
            }
          },
          new Matcher[PrimitiveOp.BigIntDelta] {
            def downcastOrNull(a: Any): PrimitiveOp.BigIntDelta = a match {
              case x: PrimitiveOp.BigIntDelta => x
              case _                          => null.asInstanceOf[PrimitiveOp.BigIntDelta]
            }
          },
          new Matcher[PrimitiveOp.BigDecimalDelta] {
            def downcastOrNull(a: Any): PrimitiveOp.BigDecimalDelta = a match {
              case x: PrimitiveOp.BigDecimalDelta => x
              case _                              => null.asInstanceOf[PrimitiveOp.BigDecimalDelta]
            }
          },
          new Matcher[PrimitiveOp.StringEdit] {
            def downcastOrNull(a: Any): PrimitiveOp.StringEdit = a match {
              case x: PrimitiveOp.StringEdit => x
              case _                         => null.asInstanceOf[PrimitiveOp.StringEdit]
            }
          },
          new Matcher[PrimitiveOp.InstantDelta] {
            def downcastOrNull(a: Any): PrimitiveOp.InstantDelta = a match {
              case x: PrimitiveOp.InstantDelta => x
              case _                           => null.asInstanceOf[PrimitiveOp.InstantDelta]
            }
          },
          new Matcher[PrimitiveOp.DurationDelta] {
            def downcastOrNull(a: Any): PrimitiveOp.DurationDelta = a match {
              case x: PrimitiveOp.DurationDelta => x
              case _                            => null.asInstanceOf[PrimitiveOp.DurationDelta]
            }
          },
          new Matcher[PrimitiveOp.LocalDateDelta] {
            def downcastOrNull(a: Any): PrimitiveOp.LocalDateDelta = a match {
              case x: PrimitiveOp.LocalDateDelta => x
              case _                             => null.asInstanceOf[PrimitiveOp.LocalDateDelta]
            }
          },
          new Matcher[PrimitiveOp.LocalDateTimeDelta] {
            def downcastOrNull(a: Any): PrimitiveOp.LocalDateTimeDelta = a match {
              case x: PrimitiveOp.LocalDateTimeDelta => x
              case _                                 => null.asInstanceOf[PrimitiveOp.LocalDateTimeDelta]
            }
          },
          new Matcher[PrimitiveOp.PeriodDelta] {
            def downcastOrNull(a: Any): PrimitiveOp.PeriodDelta = a match {
              case x: PrimitiveOp.PeriodDelta => x
              case _                          => null.asInstanceOf[PrimitiveOp.PeriodDelta]
            }
          }
        )
      ),
      modifiers = Vector.empty
    )
  )

  implicit lazy val seqOpInsertSchema: Schema[SeqOp.Insert] =
    new Schema(
      reflect = new Reflect.Record[Binding, SeqOp.Insert](
        fields = Vector(Schema[Int].reflect.asTerm("index"), Schema[Chunk[DynamicValue]].reflect.asTerm("values")),
        typeId = TypeId.of[SeqOp.Insert],
        recordBinding = new Binding.Record(
          constructor = new Constructor[SeqOp.Insert] {
            def usedRegisters: RegisterOffset                                  = RegisterOffset(ints = 1, objects = 1)
            def construct(in: Registers, offset: RegisterOffset): SeqOp.Insert =
              SeqOp.Insert(in.getInt(offset), in.getObject(offset).asInstanceOf[Chunk[DynamicValue]])
          },
          deconstructor = new Deconstructor[SeqOp.Insert] {
            def usedRegisters: RegisterOffset                                               = RegisterOffset(ints = 1, objects = 1)
            def deconstruct(out: Registers, offset: RegisterOffset, in: SeqOp.Insert): Unit = {
              out.setInt(offset, in.index)
              out.setObject(offset, in.values)
            }
          }
        ),
        modifiers = Vector.empty
      )
    )

  implicit lazy val seqOpAppendSchema: Schema[SeqOp.Append] =
    new Schema(
      reflect = new Reflect.Record[Binding, SeqOp.Append](
        fields = Vector(Schema[Chunk[DynamicValue]].reflect.asTerm("values")),
        typeId = TypeId.of[SeqOp.Append],
        recordBinding = new Binding.Record(
          constructor = new Constructor[SeqOp.Append] {
            def usedRegisters: RegisterOffset                                  = RegisterOffset(objects = 1)
            def construct(in: Registers, offset: RegisterOffset): SeqOp.Append =
              SeqOp.Append(in.getObject(offset).asInstanceOf[Chunk[DynamicValue]])
          },
          deconstructor = new Deconstructor[SeqOp.Append] {
            def usedRegisters: RegisterOffset                                               = RegisterOffset(objects = 1)
            def deconstruct(out: Registers, offset: RegisterOffset, in: SeqOp.Append): Unit =
              out.setObject(offset, in.values)
          }
        ),
        modifiers = Vector.empty
      )
    )

  implicit lazy val seqOpDeleteSchema: Schema[SeqOp.Delete] =
    new Schema(
      reflect = new Reflect.Record[Binding, SeqOp.Delete](
        fields = Vector(Schema[Int].reflect.asTerm("index"), Schema[Int].reflect.asTerm("count")),
        typeId = TypeId.of[SeqOp.Delete],
        recordBinding = new Binding.Record(
          constructor = new Constructor[SeqOp.Delete] {
            def usedRegisters: RegisterOffset                                  = RegisterOffset(ints = 2)
            def construct(in: Registers, offset: RegisterOffset): SeqOp.Delete =
              SeqOp.Delete(in.getInt(offset), in.getInt(RegisterOffset.incrementFloatsAndInts(offset)))
          },
          deconstructor = new Deconstructor[SeqOp.Delete] {
            def usedRegisters: RegisterOffset                                               = RegisterOffset(ints = 2)
            def deconstruct(out: Registers, offset: RegisterOffset, in: SeqOp.Delete): Unit = {
              out.setInt(offset, in.index)
              out.setInt(RegisterOffset.incrementFloatsAndInts(offset), in.count)
            }
          }
        ),
        modifiers = Vector.empty
      )
    )

  implicit lazy val seqOpModifySchema: Schema[SeqOp.Modify] =
    new Schema(
      reflect = new Reflect.Record[Binding, SeqOp.Modify](
        fields = Vector(
          Schema[Int].reflect.asTerm("index"),
          Reflect.Deferred(() => operationSchema.reflect).asTerm("op")
        ),
        typeId = TypeId.of[SeqOp.Modify],
        recordBinding = new Binding.Record(
          constructor = new Constructor[SeqOp.Modify] {
            def usedRegisters: RegisterOffset                                  = RegisterOffset(ints = 1, objects = 1)
            def construct(in: Registers, offset: RegisterOffset): SeqOp.Modify =
              SeqOp.Modify(in.getInt(offset), in.getObject(offset).asInstanceOf[Operation])
          },
          deconstructor = new Deconstructor[SeqOp.Modify] {
            def usedRegisters: RegisterOffset                                               = RegisterOffset(ints = 1, objects = 1)
            def deconstruct(out: Registers, offset: RegisterOffset, in: SeqOp.Modify): Unit = {
              out.setInt(offset, in.index)
              out.setObject(offset, in.op)
            }
          }
        ),
        modifiers = Vector.empty
      )
    )

  implicit lazy val seqOpSchema: Schema[SeqOp] = new Schema(
    reflect = new Reflect.Variant[Binding, SeqOp](
      cases = Vector(
        seqOpInsertSchema.reflect.asTerm("Insert"),
        seqOpAppendSchema.reflect.asTerm("Append"),
        seqOpDeleteSchema.reflect.asTerm("Delete"),
        Reflect.Deferred(() => seqOpModifySchema.reflect).asTerm("Modify")
      ),
      typeId = TypeId.of[SeqOp],
      variantBinding = new Binding.Variant(
        discriminator = new Discriminator[SeqOp] {
          def discriminate(a: SeqOp): Int = a match {
            case _: SeqOp.Insert => 0
            case _: SeqOp.Append => 1
            case _: SeqOp.Delete => 2
            case _: SeqOp.Modify => 3
          }
        },
        matchers = Matchers(
          new Matcher[SeqOp.Insert] {
            def downcastOrNull(a: Any): SeqOp.Insert = a match {
              case x: SeqOp.Insert => x
              case _               => null.asInstanceOf[SeqOp.Insert]
            }
          },
          new Matcher[SeqOp.Append] {
            def downcastOrNull(a: Any): SeqOp.Append = a match {
              case x: SeqOp.Append => x
              case _               => null.asInstanceOf[SeqOp.Append]
            }
          },
          new Matcher[SeqOp.Delete] {
            def downcastOrNull(a: Any): SeqOp.Delete = a match {
              case x: SeqOp.Delete => x
              case _               => null.asInstanceOf[SeqOp.Delete]
            }
          },
          new Matcher[SeqOp.Modify] {
            def downcastOrNull(a: Any): SeqOp.Modify = a match {
              case x: SeqOp.Modify => x
              case _               => null.asInstanceOf[SeqOp.Modify]
            }
          }
        )
      ),
      modifiers = Vector.empty
    )
  )

  implicit lazy val mapOpAddSchema: Schema[MapOp.Add] =
    new Schema(
      reflect = new Reflect.Record[Binding, MapOp.Add](
        fields = Vector(Schema[DynamicValue].reflect.asTerm("key"), Schema[DynamicValue].reflect.asTerm("value")),
        typeId = TypeId.of[MapOp.Add],
        recordBinding = new Binding.Record(
          constructor = new Constructor[MapOp.Add] {
            def usedRegisters: RegisterOffset                               = RegisterOffset(objects = 2)
            def construct(in: Registers, offset: RegisterOffset): MapOp.Add =
              MapOp.Add(
                in.getObject(offset).asInstanceOf[DynamicValue],
                in.getObject(RegisterOffset.incrementObjects(offset)).asInstanceOf[DynamicValue]
              )
          },
          deconstructor = new Deconstructor[MapOp.Add] {
            def usedRegisters: RegisterOffset                                            = RegisterOffset(objects = 2)
            def deconstruct(out: Registers, offset: RegisterOffset, in: MapOp.Add): Unit = {
              out.setObject(offset, in.key)
              out.setObject(RegisterOffset.incrementObjects(offset), in.value)
            }
          }
        ),
        modifiers = Vector.empty
      )
    )

  implicit lazy val mapOpRemoveSchema: Schema[MapOp.Remove] =
    new Schema(
      reflect = new Reflect.Record[Binding, MapOp.Remove](
        fields = Vector(Schema[DynamicValue].reflect.asTerm("key")),
        typeId = TypeId.of[MapOp.Remove],
        recordBinding = new Binding.Record(
          constructor = new Constructor[MapOp.Remove] {
            def usedRegisters: RegisterOffset                                  = RegisterOffset(objects = 1)
            def construct(in: Registers, offset: RegisterOffset): MapOp.Remove =
              MapOp.Remove(in.getObject(offset).asInstanceOf[DynamicValue])
          },
          deconstructor = new Deconstructor[MapOp.Remove] {
            def usedRegisters: RegisterOffset                                               = RegisterOffset(objects = 1)
            def deconstruct(out: Registers, offset: RegisterOffset, in: MapOp.Remove): Unit =
              out.setObject(offset, in.key)
          }
        ),
        modifiers = Vector.empty
      )
    )

  implicit lazy val mapOpModifySchema: Schema[MapOp.Modify] =
    new Schema(
      reflect = new Reflect.Record[Binding, MapOp.Modify](
        fields = Vector(
          Schema[DynamicValue].reflect.asTerm("key"),
          Reflect.Deferred(() => dynamicPatchSchema.reflect).asTerm("patch")
        ),
        typeId = TypeId.of[MapOp.Modify],
        recordBinding = new Binding.Record(
          constructor = new Constructor[MapOp.Modify] {
            def usedRegisters: RegisterOffset                                  = RegisterOffset(objects = 2)
            def construct(in: Registers, offset: RegisterOffset): MapOp.Modify =
              MapOp.Modify(
                in.getObject(offset).asInstanceOf[DynamicValue],
                in.getObject(RegisterOffset.incrementObjects(offset)).asInstanceOf[DynamicPatch]
              )
          },
          deconstructor = new Deconstructor[MapOp.Modify] {
            def usedRegisters: RegisterOffset                                               = RegisterOffset(objects = 2)
            def deconstruct(out: Registers, offset: RegisterOffset, in: MapOp.Modify): Unit = {
              out.setObject(offset, in.key)
              out.setObject(RegisterOffset.incrementObjects(offset), in.patch)
            }
          }
        ),
        modifiers = Vector.empty
      )
    )

  implicit lazy val mapOpSchema: Schema[MapOp] = new Schema(
    reflect = new Reflect.Variant[Binding, MapOp](
      cases = Vector(
        mapOpAddSchema.reflect.asTerm("Add"),
        mapOpRemoveSchema.reflect.asTerm("Remove"),
        Reflect.Deferred(() => mapOpModifySchema.reflect).asTerm("Modify")
      ),
      typeId = TypeId.of[MapOp],
      variantBinding = new Binding.Variant(
        discriminator = new Discriminator[MapOp] {
          def discriminate(a: MapOp): Int = a match {
            case _: MapOp.Add    => 0
            case _: MapOp.Remove => 1
            case _: MapOp.Modify => 2
          }
        },
        matchers = Matchers(
          new Matcher[MapOp.Add] {
            def downcastOrNull(a: Any): MapOp.Add = a match {
              case x: MapOp.Add => x
              case _            => null.asInstanceOf[MapOp.Add]
            }
          },
          new Matcher[MapOp.Remove] {
            def downcastOrNull(a: Any): MapOp.Remove = a match {
              case x: MapOp.Remove => x
              case _               => null.asInstanceOf[MapOp.Remove]
            }
          },
          new Matcher[MapOp.Modify] {
            def downcastOrNull(a: Any): MapOp.Modify = a match {
              case x: MapOp.Modify => x
              case _               => null.asInstanceOf[MapOp.Modify]
            }
          }
        )
      ),
      modifiers = Vector.empty
    )
  )

  implicit lazy val operationSetSchema: Schema[Operation.Set] =
    new Schema(
      reflect = new Reflect.Record[Binding, Operation.Set](
        fields = Vector(Schema[DynamicValue].reflect.asTerm("value")),
        typeId = TypeId.of[Operation.Set],
        recordBinding = new Binding.Record(
          constructor = new Constructor[Operation.Set] {
            def usedRegisters: RegisterOffset                                   = RegisterOffset(objects = 1)
            def construct(in: Registers, offset: RegisterOffset): Operation.Set =
              Operation.Set(in.getObject(offset).asInstanceOf[DynamicValue])
          },
          deconstructor = new Deconstructor[Operation.Set] {
            def usedRegisters: RegisterOffset                                                = RegisterOffset(objects = 1)
            def deconstruct(out: Registers, offset: RegisterOffset, in: Operation.Set): Unit =
              out.setObject(offset, in.value)
          }
        ),
        modifiers = Vector.empty
      )
    )

  implicit lazy val operationPrimitiveDeltaSchema: Schema[Operation.PrimitiveDelta] =
    new Schema(
      reflect = new Reflect.Record[Binding, Operation.PrimitiveDelta](
        fields = Vector(primitiveOpSchema.reflect.asTerm("op")),
        typeId = TypeId.of[Operation.PrimitiveDelta],
        recordBinding = new Binding.Record(
          constructor = new Constructor[Operation.PrimitiveDelta] {
            def usedRegisters: RegisterOffset                                              = RegisterOffset(objects = 1)
            def construct(in: Registers, offset: RegisterOffset): Operation.PrimitiveDelta =
              Operation.PrimitiveDelta(in.getObject(offset).asInstanceOf[PrimitiveOp])
          },
          deconstructor = new Deconstructor[Operation.PrimitiveDelta] {
            def usedRegisters: RegisterOffset                                                           = RegisterOffset(objects = 1)
            def deconstruct(out: Registers, offset: RegisterOffset, in: Operation.PrimitiveDelta): Unit =
              out.setObject(offset, in.op)
          }
        ),
        modifiers = Vector.empty
      )
    )

  implicit lazy val operationSequenceEditSchema: Schema[Operation.SequenceEdit] =
    new Schema(
      reflect = new Reflect.Record[Binding, Operation.SequenceEdit](
        fields = Vector(Reflect.Deferred(() => Schema[Chunk[SeqOp]].reflect).asTerm("ops")),
        typeId = TypeId.of[Operation.SequenceEdit],
        recordBinding = new Binding.Record(
          constructor = new Constructor[Operation.SequenceEdit] {
            def usedRegisters: RegisterOffset                                            = RegisterOffset(objects = 1)
            def construct(in: Registers, offset: RegisterOffset): Operation.SequenceEdit =
              Operation.SequenceEdit(in.getObject(offset).asInstanceOf[Chunk[SeqOp]])
          },
          deconstructor = new Deconstructor[Operation.SequenceEdit] {
            def usedRegisters: RegisterOffset                                                         = RegisterOffset(objects = 1)
            def deconstruct(out: Registers, offset: RegisterOffset, in: Operation.SequenceEdit): Unit =
              out.setObject(offset, in.ops)
          }
        ),
        modifiers = Vector.empty
      )
    )

  implicit lazy val operationMapEditSchema: Schema[Operation.MapEdit] =
    new Schema(
      reflect = new Reflect.Record[Binding, Operation.MapEdit](
        fields = Vector(Reflect.Deferred(() => Schema[Chunk[MapOp]].reflect).asTerm("ops")),
        typeId = TypeId.of[Operation.MapEdit],
        recordBinding = new Binding.Record(
          constructor = new Constructor[Operation.MapEdit] {
            def usedRegisters: RegisterOffset                                       = RegisterOffset(objects = 1)
            def construct(in: Registers, offset: RegisterOffset): Operation.MapEdit =
              Operation.MapEdit(in.getObject(offset).asInstanceOf[Chunk[MapOp]])
          },
          deconstructor = new Deconstructor[Operation.MapEdit] {
            def usedRegisters: RegisterOffset                                                    = RegisterOffset(objects = 1)
            def deconstruct(out: Registers, offset: RegisterOffset, in: Operation.MapEdit): Unit =
              out.setObject(offset, in.ops)
          }
        ),
        modifiers = Vector.empty
      )
    )

  implicit lazy val operationPatchSchema: Schema[Operation.Patch] =
    new Schema(
      reflect = new Reflect.Record[Binding, Operation.Patch](
        fields = Vector(Reflect.Deferred(() => dynamicPatchSchema.reflect).asTerm("patch")),
        typeId = TypeId.of[Operation.Patch],
        recordBinding = new Binding.Record(
          constructor = new Constructor[Operation.Patch] {
            def usedRegisters: RegisterOffset                                     = RegisterOffset(objects = 1)
            def construct(in: Registers, offset: RegisterOffset): Operation.Patch =
              Operation.Patch(in.getObject(offset).asInstanceOf[DynamicPatch])
          },
          deconstructor = new Deconstructor[Operation.Patch] {
            def usedRegisters: RegisterOffset                                                  = RegisterOffset(objects = 1)
            def deconstruct(out: Registers, offset: RegisterOffset, in: Operation.Patch): Unit =
              out.setObject(offset, in.patch)
          }
        ),
        modifiers = Vector.empty
      )
    )

  implicit lazy val operationSchema: Schema[Operation] = new Schema(
    reflect = new Reflect.Variant[Binding, Operation](
      cases = Vector(
        operationSetSchema.reflect.asTerm("Set"),
        operationPrimitiveDeltaSchema.reflect.asTerm("PrimitiveDelta"),
        operationSequenceEditSchema.reflect.asTerm("SequenceEdit"),
        operationMapEditSchema.reflect.asTerm("MapEdit"),
        Reflect.Deferred(() => operationPatchSchema.reflect).asTerm("Patch")
      ),
      typeId = TypeId.of[Operation],
      variantBinding = new Binding.Variant(
        discriminator = new Discriminator[Operation] {
          def discriminate(a: Operation): Int = a match {
            case _: Operation.Set            => 0
            case _: Operation.PrimitiveDelta => 1
            case _: Operation.SequenceEdit   => 2
            case _: Operation.MapEdit        => 3
            case _: Operation.Patch          => 4
          }
        },
        matchers = Matchers(
          new Matcher[Operation.Set] {
            def downcastOrNull(a: Any): Operation.Set = a match {
              case x: Operation.Set => x
              case _                => null.asInstanceOf[Operation.Set]
            }
          },
          new Matcher[Operation.PrimitiveDelta] {
            def downcastOrNull(a: Any): Operation.PrimitiveDelta = a match {
              case x: Operation.PrimitiveDelta => x
              case _                           => null.asInstanceOf[Operation.PrimitiveDelta]
            }
          },
          new Matcher[Operation.SequenceEdit] {
            def downcastOrNull(a: Any): Operation.SequenceEdit = a match {
              case x: Operation.SequenceEdit => x
              case _                         => null.asInstanceOf[Operation.SequenceEdit]
            }
          },
          new Matcher[Operation.MapEdit] {
            def downcastOrNull(a: Any): Operation.MapEdit = a match {
              case x: Operation.MapEdit => x
              case _                    => null.asInstanceOf[Operation.MapEdit]
            }
          },
          new Matcher[Operation.Patch] {
            def downcastOrNull(a: Any): Operation.Patch = a match {
              case x: Operation.Patch => x
              case _                  => null.asInstanceOf[Operation.Patch]
            }
          }
        )
      ),
      modifiers = Vector.empty
    )
  )

  implicit lazy val dynamicPatchOpSchema: Schema[DynamicPatchOp] =
    new Schema(
      reflect = new Reflect.Record[Binding, DynamicPatchOp](
        fields = Vector(
          Schema[DynamicOptic].reflect.asTerm("path"),
          Reflect.Deferred(() => operationSchema.reflect).asTerm("operation")
        ),
        typeId = TypeId.of[DynamicPatchOp],
        recordBinding = new Binding.Record(
          constructor = new Constructor[DynamicPatchOp] {
            def usedRegisters: RegisterOffset                                    = RegisterOffset(objects = 2)
            def construct(in: Registers, offset: RegisterOffset): DynamicPatchOp =
              DynamicPatchOp(
                in.getObject(offset).asInstanceOf[DynamicOptic],
                in.getObject(RegisterOffset.incrementObjects(offset)).asInstanceOf[Operation]
              )
          },
          deconstructor = new Deconstructor[DynamicPatchOp] {
            def usedRegisters: RegisterOffset                                                 = RegisterOffset(objects = 2)
            def deconstruct(out: Registers, offset: RegisterOffset, in: DynamicPatchOp): Unit = {
              out.setObject(offset, in.path)
              out.setObject(RegisterOffset.incrementObjects(offset), in.operation)
            }
          }
        ),
        modifiers = Vector.empty
      )
    )

  implicit lazy val dynamicPatchSchema: Schema[DynamicPatch] =
    new Schema(
      reflect = new Reflect.Record[Binding, DynamicPatch](
        fields = Vector(Reflect.Deferred(() => Schema[Chunk[DynamicPatchOp]].reflect).asTerm("ops")),
        typeId = TypeId.of[DynamicPatch],
        recordBinding = new Binding.Record(
          constructor = new Constructor[DynamicPatch] {
            def usedRegisters: RegisterOffset                                  = RegisterOffset(objects = 1)
            def construct(in: Registers, offset: RegisterOffset): DynamicPatch =
              DynamicPatch(in.getObject(offset).asInstanceOf[Chunk[DynamicPatchOp]])
          },
          deconstructor = new Deconstructor[DynamicPatch] {
            def usedRegisters: RegisterOffset                                               = RegisterOffset(objects = 1)
            def deconstruct(out: Registers, offset: RegisterOffset, in: DynamicPatch): Unit =
              out.setObject(offset, in.ops)
          }
        ),
        modifiers = Vector.empty
      )
    )
}
