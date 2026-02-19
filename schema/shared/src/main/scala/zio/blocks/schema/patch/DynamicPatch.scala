package zio.blocks.schema.patch

import zio.blocks.chunk.Chunk
import zio.blocks.schema._
import zio.blocks.schema.binding._
import zio.blocks.schema.binding.RegisterOffset.RegisterOffset
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
      ops.foreach(op => DynamicPatch.renderOp(sb, op, 1))
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

  private def renderOp(sb: lang.StringBuilder, op: DynamicPatchOp, indent: Int): Unit = {
    appendIndent(sb, indent)
    DynamicOptic.renderString(sb, op.path.nodes)
    op.operation match {
      case s: Operation.Set             => sb.append(" = ").append(s.value).append('\n')
      case pd: Operation.PrimitiveDelta => renderPrimitiveDelta(sb, pd.op, indent)
      case se: Operation.SequenceEdit   =>
        sb.append(":\n")
        se.ops.foreach(so => renderSeqOp(sb, so, indent + 1))
      case me: Operation.MapEdit =>
        sb.append(":\n")
        me.ops.foreach(mo => renderMapOp(sb, mo, indent + 1))
      case p: Operation.Patch =>
        sb.append(":\n")
        p.patch.ops.foreach(op => renderOp(sb, op, indent + 1))
    }
  }

  private[this] def renderPrimitiveDelta(sb: lang.StringBuilder, op: PrimitiveOp, indent: Int): Unit = {
    op match {
      case id: PrimitiveOp.IntDelta =>
        val d = id.delta
        if (d >= 0) sb.append(" += ").append(d)
        else sb.append(" -= ").append(-d)
      case ld: PrimitiveOp.LongDelta =>
        val d = ld.delta
        if (d >= 0) sb.append(" += ").append(d)
        else sb.append(" -= ").append(-d)
      case dd: PrimitiveOp.DoubleDelta =>
        val d = dd.delta
        if (d >= 0) sb.append(" += ").append(d)
        else sb.append(" -= ").append(-d)
      case fd: PrimitiveOp.FloatDelta =>
        val d = fd.delta
        if (d >= 0) sb.append(" += ").append(d)
        else sb.append(" -= ").append(-d)
      case sd: PrimitiveOp.ShortDelta =>
        val d = sd.delta
        if (d >= 0) sb.append(" += ").append(d)
        else sb.append(" -= ").append(-d)
      case bd: PrimitiveOp.ByteDelta =>
        val d = bd.delta
        if (d >= 0) sb.append(" += ").append(d)
        else sb.append(" -= ").append(-d)
      case bid: PrimitiveOp.BigIntDelta =>
        val d = bid.delta
        if (d >= BigInt(0)) sb.append(" += ").append(d)
        else sb.append(" -= ").append(-d)
      case bdd: PrimitiveOp.BigDecimalDelta =>
        val d = bdd.delta
        if (d >= BigDecimal(0)) sb.append(" += ").append(d)
        else sb.append(" -= ").append(-d)
      case id: PrimitiveOp.InstantDelta =>
        sb.append(" += ").append(id.delta)
      case dd: PrimitiveOp.DurationDelta =>
        sb.append(" += ").append(dd.delta)
      case ldd: PrimitiveOp.LocalDateDelta =>
        sb.append(" += ").append(ldd.delta)
      case ldtd: PrimitiveOp.LocalDateTimeDelta =>
        sb.append(" += ").append(ldtd.periodDelta).append(", ").append(ldtd.durationDelta)
      case pd: PrimitiveOp.PeriodDelta =>
        sb.append(" += ").append(pd.delta)
      case se: PrimitiveOp.StringEdit =>
        sb.append(":\n")
        se.ops.foreach {
          var idx = 0
          op =>
            if (idx > 0) sb.append('\n')
            idx += 1
            appendIndent(sb, indent)
            op match {
              case StringOp.Insert(idx, text) =>
                sb.append("  + [").append(idx).append(": ")
                escapeString(sb, text)
                sb.append(']')
              case StringOp.Delete(idx, len) =>
                sb.append("  - [").append(idx).append(", ").append(len).append(']')
              case StringOp.Append(text) =>
                sb.append("  + ")
                escapeString(sb, text)
              case StringOp.Modify(idx, len, text) =>
                sb.append("  ~ [").append(idx).append(", ").append(len).append(": ")
                escapeString(sb, text)
                sb.append(']')
            }
        }
    }
    sb.append('\n')
  }

  private[this] def renderSeqOp(sb: lang.StringBuilder, op: SeqOp, indent: Int): Unit = op match {
    case i: SeqOp.Insert =>
      i.values.foreach {
        var idx = i.index
        v =>
          appendIndent(sb, indent)
          sb.append("+ [").append(idx).append(": ").append(v).append("]\n")
          idx += 1
      }
    case a: SeqOp.Append =>
      a.values.foreach { v =>
        appendIndent(sb, indent)
        sb.append("+ ").append(v).append('\n')
      }
    case d: SeqOp.Delete =>
      appendIndent(sb, indent)
      val index = d.index
      val count = d.count
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
    case m: SeqOp.Modify =>
      appendIndent(sb, indent)
      val nestedOp = m.op
      val index    = m.index
      nestedOp match {
        case Operation.Set(v) => sb.append("~ [").append(index).append(": ").append(v).append("]\n")
        case _                =>
          sb.append("~ [").append(index).append("]:\n")
          renderOp(sb, DynamicPatchOp(DynamicOptic.root, nestedOp), indent + 1)
      }
  }

  private[this] def renderMapOp(sb: lang.StringBuilder, op: MapOp, indent: Int): Unit = {
    appendIndent(sb, indent)
    op match {
      case a: MapOp.Add    => sb.append("+ {").append(a.key).append(": ").append(a.value).append("}\n")
      case r: MapOp.Remove => sb.append("- {").append(r.key).append("}\n")
      case m: MapOp.Modify =>
        sb.append("~ {").append(m.key).append("}:\n")
        m.patch.ops.foreach(op => renderOp(sb, op, indent + 1))
    }
  }

  private[this] def escapeString(sb: java.lang.StringBuilder, s: String): Unit = {
    sb.append('"')
    val len = s.length
    var idx = 0
    while (idx < len) {
      val ch = s.charAt(idx)
      idx += 1
      if (ch >= ' ' && ch != '"' && ch != '\\') sb.append(ch)
      else {
        sb.append('\\')
        ch match {
          case '"'  => sb.append('"')
          case '\\' => sb.append('\\')
          case '\b' => sb.append('b')
          case '\f' => sb.append('f')
          case '\n' => sb.append('n')
          case '\r' => sb.append('r')
          case '\t' => sb.append('t')
          case _    =>
            sb.append('u')
              .append(hexDigit((ch >> 12) & 0xf))
              .append(hexDigit((ch >> 8) & 0xf))
              .append(hexDigit((ch >> 4) & 0xf))
              .append(hexDigit(ch & 0xf))
        }
      }
    }
    sb.append('"')
  }

  private[this] def hexDigit(n: Int): Char = (n + (if (n < 10) 48 else 87)).toChar

  private[this] def appendIndent(sb: lang.StringBuilder, indent: Int): Unit = {
    var idx = indent
    while (idx > 0) {
      sb.append(' ').append(' ')
      idx -= 1
    }
  }

  // Apply a single operation at a path within a value.
  private def applyOp(
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
          case r: DynamicValue.Record =>
            val fields   = r.fields
            val fieldIdx = fields.indexWhere(_._1 == name)
            if (fieldIdx < 0) new Left(SchemaError.missingField(trace, name))
            else {
              val (fieldName, fieldValue) = fields(fieldIdx)
              val trace_                  = f :: trace
              (if (isLast) applyOperation(fieldValue, operation, mode, trace_)
               else navigateAndApply(fieldValue, path, pathIdx + 1, operation, mode, trace_)) match {
                case Right(v) => new Right(new DynamicValue.Record(fields.updated(fieldIdx, (fieldName, v))))
                case l        => l
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
              val element = elements(index)
              val trace_  = ai :: trace
              (if (isLast) applyOperation(element, operation, mode, trace_)
               else navigateAndApply(element, path, pathIdx + 1, operation, mode, trace_)) match {
                case Right(e) => new Right(new DynamicValue.Sequence(elements.updated(index, e)))
                case l        => l
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
              val (k, v) = entries(entryIdx)
              val trace_ = amk :: trace
              (if (isLast) applyOperation(v, operation, mode, trace_)
               else navigateAndApply(v, path, pathIdx + 1, operation, mode, trace_)) match {
                case Right(nv) => new Right(new DynamicValue.Map(entries.updated(entryIdx, (k, nv))))
                case l         => l
              }
            }
          case _ =>
            new Left(SchemaError.expectationMismatch(trace, s"Expected Map but got ${value.getClass.getSimpleName}"))
        }
      case c: DynamicOptic.Node.Case =>
        val expectedCase = c.name
        value match {
          case DynamicValue.Variant(caseName, innerValue) =>
            val trace_ = c :: trace
            if (caseName != expectedCase) {
              // Case doesn't match - this is an error in Strict mode
              new Left(SchemaError.expectationMismatch(trace_, s"Expected case $expectedCase but got $caseName"))
            } else {
              (if (isLast) applyOperation(innerValue, operation, mode, trace_)
               else navigateAndApply(innerValue, path, pathIdx + 1, operation, mode, trace_)) match {
                case Right(v) => new Right(new DynamicValue.Variant(caseName, v))
                case l        => l
              }
            }
          case _ =>
            val msg = s"Expected Variant but got ${value.getClass.getSimpleName}"
            new Left(SchemaError.expectationMismatch(trace, msg))
        }
      case e: DynamicOptic.Node.Elements.type =>
        value match {
          case DynamicValue.Sequence(elements) =>
            val trace_ = e :: trace
            if (elements.isEmpty) {
              // Empty sequence - in Strict mode, this is an error (can't apply patch to empty list)
              // In Lenient/Clobber mode, return unchanged
              if (mode ne PatchMode.Strict) new Right(value)
              else new Left(SchemaError.expectationMismatch(trace_, "encountered an empty sequence"))
            } else if (isLast) applyToAllElements(elements, operation, mode, trace_)
            else navigateAllElements(elements, path, pathIdx + 1, operation, mode, trace_)
          case _ =>
            val msg = s"Expected Sequence but got ${value.getClass.getSimpleName}"
            new Left(SchemaError.expectationMismatch(trace, msg))
        }
      case w: DynamicOptic.Node.Wrapped.type =>
        // Wrappers in DynamicValue are typically represented as the unwrapped value directly
        // or as a Record with a single field. For now, we pass through.
        val trace_ = w :: trace
        if (isLast) applyOperation(value, operation, mode, trace_)
        else navigateAndApply(value, path, pathIdx + 1, operation, mode, trace_)
      case _: DynamicOptic.Node.AtIndices =>
        new Left(SchemaError.expectationMismatch(trace, "AtIndices not supported in patches"))
      case _: DynamicOptic.Node.AtMapKeys =>
        new Left(SchemaError.expectationMismatch(trace, "AtMapKeys not supported in patches"))
      case DynamicOptic.Node.MapKeys =>
        new Left(SchemaError.expectationMismatch(trace, "MapKeys not supported in patches"))
      case DynamicOptic.Node.MapValues =>
        new Left(SchemaError.expectationMismatch(trace, "MapValues not supported in patches"))
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
    case s: Operation.Set             => new Right(s.value)
    case pd: Operation.PrimitiveDelta => applyPrimitiveDelta(value, pd.op, trace)
    case se: Operation.SequenceEdit   => applySequenceEdit(value, se.ops, mode, trace)
    case me: Operation.MapEdit        => applyMapEdit(value, me.ops, mode, trace)
    case p: Operation.Patch           => p.patch.apply(value, mode)
  }

  private[this] def applyPrimitiveDelta(
    value: DynamicValue,
    op: PrimitiveOp,
    trace: List[DynamicOptic.Node]
  ): Either[SchemaError, DynamicValue] =
    value match {
      case p: DynamicValue.Primitive => applyPrimitiveOpToValue(p.value, op, trace)
      case _                         =>
        new Left(SchemaError.expectationMismatch(trace, s"Expected Primitive but got ${value.getClass.getSimpleName}"))
    }

  private[this] def applyPrimitiveOpToValue(
    pv: PrimitiveValue,
    op: PrimitiveOp,
    trace: List[DynamicOptic.Node]
  ): Either[SchemaError, DynamicValue] = (pv, op) match {
    // Numeric deltas
    case (v: PrimitiveValue.Int, d: PrimitiveOp.IntDelta) =>
      new Right(new DynamicValue.Primitive(new PrimitiveValue.Int(v.value + d.delta)))
    case (v: PrimitiveValue.Long, d: PrimitiveOp.LongDelta) =>
      new Right(new DynamicValue.Primitive(new PrimitiveValue.Long(v.value + d.delta)))
    case (v: PrimitiveValue.Double, d: PrimitiveOp.DoubleDelta) =>
      new Right(new DynamicValue.Primitive(new PrimitiveValue.Double(v.value + d.delta)))
    case (v: PrimitiveValue.Float, d: PrimitiveOp.FloatDelta) =>
      new Right(new DynamicValue.Primitive(new PrimitiveValue.Float(v.value + d.delta)))
    case (v: PrimitiveValue.Short, d: PrimitiveOp.ShortDelta) =>
      new Right(new DynamicValue.Primitive(new PrimitiveValue.Short((v.value + d.delta).toShort)))
    case (v: PrimitiveValue.Byte, d: PrimitiveOp.ByteDelta) =>
      new Right(new DynamicValue.Primitive(new PrimitiveValue.Byte((v.value + d.delta).toByte)))
    case (v: PrimitiveValue.BigInt, d: PrimitiveOp.BigIntDelta) =>
      new Right(new DynamicValue.Primitive(new PrimitiveValue.BigInt(v.value + d.delta)))
    case (v: PrimitiveValue.BigDecimal, d: PrimitiveOp.BigDecimalDelta) =>
      new Right(new DynamicValue.Primitive(new PrimitiveValue.BigDecimal(v.value + d.delta)))
    // String edits
    case (v: PrimitiveValue.String, se: PrimitiveOp.StringEdit) => applyStringEdits(v.value, se.ops, trace)
    // Temporal deltas
    case (v: PrimitiveValue.Instant, d: PrimitiveOp.InstantDelta) =>
      new Right(new DynamicValue.Primitive(new PrimitiveValue.Instant(v.value.plus(d.delta))))
    case (v: PrimitiveValue.Duration, d: PrimitiveOp.DurationDelta) =>
      new Right(new DynamicValue.Primitive(new PrimitiveValue.Duration(v.value.plus(d.delta))))
    case (v: PrimitiveValue.LocalDate, d: PrimitiveOp.LocalDateDelta) =>
      new Right(new DynamicValue.Primitive(new PrimitiveValue.LocalDate(v.value.plus(d.delta))))
    case (v: PrimitiveValue.LocalDateTime, d: PrimitiveOp.LocalDateTimeDelta) =>
      new Right(
        new DynamicValue.Primitive(new PrimitiveValue.LocalDateTime(v.value.plus(d.periodDelta).plus(d.durationDelta)))
      )
    case (v: PrimitiveValue.Period, d: PrimitiveOp.PeriodDelta) =>
      new Right(new DynamicValue.Primitive(new PrimitiveValue.Period(v.value.plus(d.delta))))
    case _ =>
      val msg = s"Type mismatch: cannot apply ${op.getClass.getSimpleName} to ${pv.getClass.getSimpleName}"
      new Left(SchemaError.expectationMismatch(trace, msg))
  }

  private[this] def applyStringEdits(
    str: lang.String,
    ops: Chunk[StringOp],
    trace: List[DynamicOptic.Node]
  ): Either[SchemaError, DynamicValue] = {
    var result = str
    val opsLen = ops.length
    var idx    = 0
    while (idx < opsLen) {
      ops(idx) match {
        case i: StringOp.Insert =>
          val index = i.index
          val len   = result.length
          if (index < 0 || index > len) {
            val msg = s"String insert index $index out of bounds for string of length $len"
            return new Left(SchemaError.expectationMismatch(trace, msg))
          } else result = result.substring(0, index) + i.text + result.substring(index)
        case d: StringOp.Delete =>
          val index = d.index
          val len   = result.length
          val limit = index + d.length
          if (index < 0 || limit > len) {
            val msg = s"String delete range [$index, $limit) out of bounds for string of length $len"
            return new Left(SchemaError.expectationMismatch(trace, msg))
          } else result = result.substring(0, index) + result.substring(limit)
        case a: StringOp.Append => result = result + a.text
        case m: StringOp.Modify =>
          val index = m.index
          val len   = result.length
          val limit = index + m.length
          if (index < 0 || limit > len) {
            val msg = s"String modify range [$index, $limit) out of bounds for string of length $len"
            return new Left(SchemaError.expectationMismatch(trace, msg))
          } else result = result.substring(0, index) + m.text + result.substring(limit)
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
    case a: SeqOp.Append => new Right(elements ++ a.values)
    case i: SeqOp.Insert =>
      val index = i.index
      val len   = elements.length
      if ((index < 0 || index > len) && (mode ne PatchMode.Clobber)) {
        val msg = s"Insert index $index out of bounds for sequence of length $len"
        new Left(SchemaError.expectationMismatch(trace, msg))
      } else new Right(elements.take(index) ++ i.values ++ elements.drop(index))
    case d: SeqOp.Delete =>
      val index = d.index
      val len   = elements.length
      val limit = index + d.count
      if ((index < 0 || limit > len) && (mode ne PatchMode.Clobber)) {
        val msg = s"Delete range [$index, $limit) out of bounds for sequence of length $len"
        new Left(SchemaError.expectationMismatch(trace, msg))
      } else new Right(elements.take(index) ++ elements.drop(limit))
    case m: SeqOp.Modify =>
      val index = m.index
      val len   = elements.length
      if (index < 0 || index >= len) {
        val msg = s"Modify index $index out of bounds for sequence of length $len"
        new Left(SchemaError.expectationMismatch(trace, msg))
      } else {
        applyOperation(elements(index), m.op, mode, new DynamicOptic.Node.AtIndex(index) :: trace) match {
          case Right(e) => new Right(elements.updated(index, e))
          case l        => l.asInstanceOf[Either[SchemaError, Chunk[DynamicValue]]]
        }
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
    case a: MapOp.Add =>
      val key         = a.key
      val value       = a.value
      val existingIdx = entries.indexWhere(_._1 == key)
      if (existingIdx >= 0) {
        if (mode eq PatchMode.Clobber) new Right(entries.updated(existingIdx, (key, value)))
        else new Left(SchemaError.expectationMismatch(trace, s"Key already exists in map"))
      } else new Right(entries :+ (key, value))
    case r: MapOp.Remove =>
      val key         = r.key
      val existingIdx = entries.indexWhere(_._1 == key)
      if (existingIdx < 0) {
        if (mode eq PatchMode.Clobber) new Right(entries) // Nothing to remove, return unchanged
        else new Left(SchemaError.expectationMismatch(trace, s"Key not found in map"))
      } else new Right(entries.take(existingIdx) ++ entries.drop(existingIdx + 1))
    case m: MapOp.Modify =>
      val key         = m.key
      val existingIdx = entries.indexWhere(_._1 == key)
      if (existingIdx < 0) new Left(SchemaError.expectationMismatch(trace, s"Key not found in map"))
      else {
        val kv = entries(existingIdx)
        m.patch.apply(kv._2, mode).map(newValue => entries.updated(existingIdx, (kv._1, newValue)))
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
        fields = Chunk(Schema[Int].reflect.asTerm("index"), Schema[String].reflect.asTerm("text")),
        typeId = TypeId.of[StringOp.Insert],
        recordBinding = new Binding.Record(
          constructor = new Constructor[StringOp.Insert] {
            def usedRegisters: RegisterOffset                                     = RegisterOffset(ints = 1, objects = 1)
            def construct(in: Registers, offset: RegisterOffset): StringOp.Insert =
              new StringOp.Insert(in.getInt(offset), in.getObject(offset).asInstanceOf[String])
          },
          deconstructor = new Deconstructor[StringOp.Insert] {
            def usedRegisters: RegisterOffset                                                  = RegisterOffset(ints = 1, objects = 1)
            def deconstruct(out: Registers, offset: RegisterOffset, in: StringOp.Insert): Unit = {
              out.setInt(offset, in.index)
              out.setObject(offset, in.text)
            }
          }
        ),
        modifiers = Chunk.empty
      )
    )

  implicit lazy val stringOpDeleteSchema: Schema[StringOp.Delete] =
    new Schema(
      reflect = new Reflect.Record[Binding, StringOp.Delete](
        fields = Chunk(Schema[Int].reflect.asTerm("index"), Schema[Int].reflect.asTerm("length")),
        typeId = TypeId.of[StringOp.Delete],
        recordBinding = new Binding.Record(
          constructor = new Constructor[StringOp.Delete] {
            def usedRegisters: RegisterOffset                                     = RegisterOffset(ints = 2)
            def construct(in: Registers, offset: RegisterOffset): StringOp.Delete =
              new StringOp.Delete(
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
        modifiers = Chunk.empty
      )
    )

  implicit lazy val stringOpAppendSchema: Schema[StringOp.Append] =
    new Schema(
      reflect = new Reflect.Record[Binding, StringOp.Append](
        fields = Chunk.single(Schema[String].reflect.asTerm("text")),
        typeId = TypeId.of[StringOp.Append],
        recordBinding = new Binding.Record(
          constructor = new Constructor[StringOp.Append] {
            def usedRegisters: RegisterOffset                                     = RegisterOffset(objects = 1)
            def construct(in: Registers, offset: RegisterOffset): StringOp.Append =
              new StringOp.Append(in.getObject(offset).asInstanceOf[String])
          },
          deconstructor = new Deconstructor[StringOp.Append] {
            def usedRegisters: RegisterOffset                                                  = RegisterOffset(objects = 1)
            def deconstruct(out: Registers, offset: RegisterOffset, in: StringOp.Append): Unit =
              out.setObject(offset, in.text)
          }
        ),
        modifiers = Chunk.empty
      )
    )

  implicit lazy val stringOpModifySchema: Schema[StringOp.Modify] =
    new Schema(
      reflect = new Reflect.Record[Binding, StringOp.Modify](
        fields = Chunk(
          Schema[Int].reflect.asTerm("index"),
          Schema[Int].reflect.asTerm("length"),
          Schema[String].reflect.asTerm("text")
        ),
        typeId = TypeId.of[StringOp.Modify],
        recordBinding = new Binding.Record(
          constructor = new Constructor[StringOp.Modify] {
            def usedRegisters: RegisterOffset                                     = RegisterOffset(ints = 2, objects = 1)
            def construct(in: Registers, offset: RegisterOffset): StringOp.Modify =
              new StringOp.Modify(
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
        modifiers = Chunk.empty
      )
    )

  implicit lazy val stringOpSchema: Schema[StringOp] = new Schema(
    reflect = new Reflect.Variant[Binding, StringOp](
      cases = Chunk(
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
      modifiers = Chunk.empty
    )
  )

  implicit lazy val primitiveOpIntDeltaSchema: Schema[PrimitiveOp.IntDelta] =
    new Schema(
      reflect = new Reflect.Record[Binding, PrimitiveOp.IntDelta](
        fields = Chunk.single(Schema[Int].reflect.asTerm("delta")),
        typeId = TypeId.of[PrimitiveOp.IntDelta],
        recordBinding = new Binding.Record(
          constructor = new Constructor[PrimitiveOp.IntDelta] {
            def usedRegisters: RegisterOffset                                          = RegisterOffset(ints = 1)
            def construct(in: Registers, offset: RegisterOffset): PrimitiveOp.IntDelta =
              new PrimitiveOp.IntDelta(in.getInt(offset))
          },
          deconstructor = new Deconstructor[PrimitiveOp.IntDelta] {
            def usedRegisters: RegisterOffset                                                       = RegisterOffset(ints = 1)
            def deconstruct(out: Registers, offset: RegisterOffset, in: PrimitiveOp.IntDelta): Unit =
              out.setInt(offset, in.delta)
          }
        ),
        modifiers = Chunk.empty
      )
    )

  implicit lazy val primitiveOpLongDeltaSchema: Schema[PrimitiveOp.LongDelta] =
    new Schema(
      reflect = new Reflect.Record[Binding, PrimitiveOp.LongDelta](
        fields = Chunk.single(Schema[Long].reflect.asTerm("delta")),
        typeId = TypeId.of[PrimitiveOp.LongDelta],
        recordBinding = new Binding.Record(
          constructor = new Constructor[PrimitiveOp.LongDelta] {
            def usedRegisters: RegisterOffset                                           = RegisterOffset(longs = 1)
            def construct(in: Registers, offset: RegisterOffset): PrimitiveOp.LongDelta =
              new PrimitiveOp.LongDelta(in.getLong(offset))
          },
          deconstructor = new Deconstructor[PrimitiveOp.LongDelta] {
            def usedRegisters: RegisterOffset                                                        = RegisterOffset(longs = 1)
            def deconstruct(out: Registers, offset: RegisterOffset, in: PrimitiveOp.LongDelta): Unit =
              out.setLong(offset, in.delta)
          }
        ),
        modifiers = Chunk.empty
      )
    )

  implicit lazy val primitiveOpDoubleDeltaSchema: Schema[PrimitiveOp.DoubleDelta] =
    new Schema(
      reflect = new Reflect.Record[Binding, PrimitiveOp.DoubleDelta](
        fields = Chunk.single(Schema[Double].reflect.asTerm("delta")),
        typeId = TypeId.of[PrimitiveOp.DoubleDelta],
        recordBinding = new Binding.Record(
          constructor = new Constructor[PrimitiveOp.DoubleDelta] {
            def usedRegisters: RegisterOffset                                             = RegisterOffset(doubles = 1)
            def construct(in: Registers, offset: RegisterOffset): PrimitiveOp.DoubleDelta =
              new PrimitiveOp.DoubleDelta(in.getDouble(offset))
          },
          deconstructor = new Deconstructor[PrimitiveOp.DoubleDelta] {
            def usedRegisters: RegisterOffset                                                          = RegisterOffset(doubles = 1)
            def deconstruct(out: Registers, offset: RegisterOffset, in: PrimitiveOp.DoubleDelta): Unit =
              out.setDouble(offset, in.delta)
          }
        ),
        modifiers = Chunk.empty
      )
    )

  implicit lazy val primitiveOpFloatDeltaSchema: Schema[PrimitiveOp.FloatDelta] =
    new Schema(
      reflect = new Reflect.Record[Binding, PrimitiveOp.FloatDelta](
        fields = Chunk.single(Schema[Float].reflect.asTerm("delta")),
        typeId = TypeId.of[PrimitiveOp.FloatDelta],
        recordBinding = new Binding.Record(
          constructor = new Constructor[PrimitiveOp.FloatDelta] {
            def usedRegisters: RegisterOffset                                            = RegisterOffset(floats = 1)
            def construct(in: Registers, offset: RegisterOffset): PrimitiveOp.FloatDelta =
              new PrimitiveOp.FloatDelta(in.getFloat(offset))
          },
          deconstructor = new Deconstructor[PrimitiveOp.FloatDelta] {
            def usedRegisters: RegisterOffset                                                         = RegisterOffset(floats = 1)
            def deconstruct(out: Registers, offset: RegisterOffset, in: PrimitiveOp.FloatDelta): Unit =
              out.setFloat(offset, in.delta)
          }
        ),
        modifiers = Chunk.empty
      )
    )

  implicit lazy val primitiveOpShortDeltaSchema: Schema[PrimitiveOp.ShortDelta] =
    new Schema(
      reflect = new Reflect.Record[Binding, PrimitiveOp.ShortDelta](
        fields = Chunk.single(Schema[Short].reflect.asTerm("delta")),
        typeId = TypeId.of[PrimitiveOp.ShortDelta],
        recordBinding = new Binding.Record(
          constructor = new Constructor[PrimitiveOp.ShortDelta] {
            def usedRegisters: RegisterOffset                                            = RegisterOffset(shorts = 1)
            def construct(in: Registers, offset: RegisterOffset): PrimitiveOp.ShortDelta =
              new PrimitiveOp.ShortDelta(in.getShort(offset))
          },
          deconstructor = new Deconstructor[PrimitiveOp.ShortDelta] {
            def usedRegisters: RegisterOffset                                                         = RegisterOffset(shorts = 1)
            def deconstruct(out: Registers, offset: RegisterOffset, in: PrimitiveOp.ShortDelta): Unit =
              out.setShort(offset, in.delta)
          }
        ),
        modifiers = Chunk.empty
      )
    )

  implicit lazy val primitiveOpByteDeltaSchema: Schema[PrimitiveOp.ByteDelta] =
    new Schema(
      reflect = new Reflect.Record[Binding, PrimitiveOp.ByteDelta](
        fields = Chunk.single(Schema[Byte].reflect.asTerm("delta")),
        typeId = TypeId.of[PrimitiveOp.ByteDelta],
        recordBinding = new Binding.Record(
          constructor = new Constructor[PrimitiveOp.ByteDelta] {
            def usedRegisters: RegisterOffset                                           = RegisterOffset(bytes = 1)
            def construct(in: Registers, offset: RegisterOffset): PrimitiveOp.ByteDelta =
              new PrimitiveOp.ByteDelta(in.getByte(offset))
          },
          deconstructor = new Deconstructor[PrimitiveOp.ByteDelta] {
            def usedRegisters: RegisterOffset                                                        = RegisterOffset(bytes = 1)
            def deconstruct(out: Registers, offset: RegisterOffset, in: PrimitiveOp.ByteDelta): Unit =
              out.setByte(offset, in.delta)
          }
        ),
        modifiers = Chunk.empty
      )
    )

  implicit lazy val primitiveOpBigIntDeltaSchema: Schema[PrimitiveOp.BigIntDelta] =
    new Schema(
      reflect = new Reflect.Record[Binding, PrimitiveOp.BigIntDelta](
        fields = Chunk.single(Schema[BigInt].reflect.asTerm("delta")),
        typeId = TypeId.of[PrimitiveOp.BigIntDelta],
        recordBinding = new Binding.Record(
          constructor = new Constructor[PrimitiveOp.BigIntDelta] {
            def usedRegisters: RegisterOffset                                             = RegisterOffset(objects = 1)
            def construct(in: Registers, offset: RegisterOffset): PrimitiveOp.BigIntDelta =
              new PrimitiveOp.BigIntDelta(in.getObject(offset).asInstanceOf[BigInt])
          },
          deconstructor = new Deconstructor[PrimitiveOp.BigIntDelta] {
            def usedRegisters: RegisterOffset                                                          = RegisterOffset(objects = 1)
            def deconstruct(out: Registers, offset: RegisterOffset, in: PrimitiveOp.BigIntDelta): Unit =
              out.setObject(offset, in.delta)
          }
        ),
        modifiers = Chunk.empty
      )
    )

  implicit lazy val primitiveOpBigDecimalDeltaSchema: Schema[PrimitiveOp.BigDecimalDelta] =
    new Schema(
      reflect = new Reflect.Record[Binding, PrimitiveOp.BigDecimalDelta](
        fields = Chunk.single(Schema[BigDecimal].reflect.asTerm("delta")),
        typeId = TypeId.of[PrimitiveOp.BigDecimalDelta],
        recordBinding = new Binding.Record(
          constructor = new Constructor[PrimitiveOp.BigDecimalDelta] {
            def usedRegisters: RegisterOffset                                                 = RegisterOffset(objects = 1)
            def construct(in: Registers, offset: RegisterOffset): PrimitiveOp.BigDecimalDelta =
              new PrimitiveOp.BigDecimalDelta(in.getObject(offset).asInstanceOf[BigDecimal])
          },
          deconstructor = new Deconstructor[PrimitiveOp.BigDecimalDelta] {
            def usedRegisters: RegisterOffset                                                              = RegisterOffset(objects = 1)
            def deconstruct(out: Registers, offset: RegisterOffset, in: PrimitiveOp.BigDecimalDelta): Unit =
              out.setObject(offset, in.delta)
          }
        ),
        modifiers = Chunk.empty
      )
    )

  implicit lazy val primitiveOpStringEditSchema: Schema[PrimitiveOp.StringEdit] =
    new Schema(
      reflect = new Reflect.Record[Binding, PrimitiveOp.StringEdit](
        fields = Chunk.single(Schema[Chunk[StringOp]].reflect.asTerm("ops")),
        typeId = TypeId.of[PrimitiveOp.StringEdit],
        recordBinding = new Binding.Record(
          constructor = new Constructor[PrimitiveOp.StringEdit] {
            def usedRegisters: RegisterOffset                                            = RegisterOffset(objects = 1)
            def construct(in: Registers, offset: RegisterOffset): PrimitiveOp.StringEdit =
              new PrimitiveOp.StringEdit(in.getObject(offset).asInstanceOf[Chunk[StringOp]])
          },
          deconstructor = new Deconstructor[PrimitiveOp.StringEdit] {
            def usedRegisters: RegisterOffset                                                         = RegisterOffset(objects = 1)
            def deconstruct(out: Registers, offset: RegisterOffset, in: PrimitiveOp.StringEdit): Unit =
              out.setObject(offset, in.ops)
          }
        ),
        modifiers = Chunk.empty
      )
    )

  implicit lazy val primitiveOpInstantDeltaSchema: Schema[PrimitiveOp.InstantDelta] =
    new Schema(
      reflect = new Reflect.Record[Binding, PrimitiveOp.InstantDelta](
        fields = Chunk.single(Schema[java.time.Duration].reflect.asTerm("delta")),
        typeId = TypeId.of[PrimitiveOp.InstantDelta],
        recordBinding = new Binding.Record(
          constructor = new Constructor[PrimitiveOp.InstantDelta] {
            def usedRegisters: RegisterOffset                                              = RegisterOffset(objects = 1)
            def construct(in: Registers, offset: RegisterOffset): PrimitiveOp.InstantDelta =
              new PrimitiveOp.InstantDelta(in.getObject(offset).asInstanceOf[java.time.Duration])
          },
          deconstructor = new Deconstructor[PrimitiveOp.InstantDelta] {
            def usedRegisters: RegisterOffset                                                           = RegisterOffset(objects = 1)
            def deconstruct(out: Registers, offset: RegisterOffset, in: PrimitiveOp.InstantDelta): Unit =
              out.setObject(offset, in.delta)
          }
        ),
        modifiers = Chunk.empty
      )
    )

  implicit lazy val primitiveOpDurationDeltaSchema: Schema[PrimitiveOp.DurationDelta] =
    new Schema(
      reflect = new Reflect.Record[Binding, PrimitiveOp.DurationDelta](
        fields = Chunk.single(Schema[java.time.Duration].reflect.asTerm("delta")),
        typeId = TypeId.of[PrimitiveOp.DurationDelta],
        recordBinding = new Binding.Record(
          constructor = new Constructor[PrimitiveOp.DurationDelta] {
            def usedRegisters: RegisterOffset                                               = RegisterOffset(objects = 1)
            def construct(in: Registers, offset: RegisterOffset): PrimitiveOp.DurationDelta =
              new PrimitiveOp.DurationDelta(in.getObject(offset).asInstanceOf[java.time.Duration])
          },
          deconstructor = new Deconstructor[PrimitiveOp.DurationDelta] {
            def usedRegisters: RegisterOffset                                                            = RegisterOffset(objects = 1)
            def deconstruct(out: Registers, offset: RegisterOffset, in: PrimitiveOp.DurationDelta): Unit =
              out.setObject(offset, in.delta)
          }
        ),
        modifiers = Chunk.empty
      )
    )

  implicit lazy val primitiveOpLocalDateDeltaSchema: Schema[PrimitiveOp.LocalDateDelta] =
    new Schema(
      reflect = new Reflect.Record[Binding, PrimitiveOp.LocalDateDelta](
        fields = Chunk.single(Schema[java.time.Period].reflect.asTerm("delta")),
        typeId = TypeId.of[PrimitiveOp.LocalDateDelta],
        recordBinding = new Binding.Record(
          constructor = new Constructor[PrimitiveOp.LocalDateDelta] {
            def usedRegisters: RegisterOffset                                                = RegisterOffset(objects = 1)
            def construct(in: Registers, offset: RegisterOffset): PrimitiveOp.LocalDateDelta =
              new PrimitiveOp.LocalDateDelta(in.getObject(offset).asInstanceOf[java.time.Period])
          },
          deconstructor = new Deconstructor[PrimitiveOp.LocalDateDelta] {
            def usedRegisters: RegisterOffset                                                             = RegisterOffset(objects = 1)
            def deconstruct(out: Registers, offset: RegisterOffset, in: PrimitiveOp.LocalDateDelta): Unit =
              out.setObject(offset, in.delta)
          }
        ),
        modifiers = Chunk.empty
      )
    )

  implicit lazy val primitiveOpLocalDateTimeDeltaSchema: Schema[PrimitiveOp.LocalDateTimeDelta] =
    new Schema(
      reflect = new Reflect.Record[Binding, PrimitiveOp.LocalDateTimeDelta](
        fields = Chunk(
          Schema[java.time.Period].reflect.asTerm("periodDelta"),
          Schema[java.time.Duration].reflect.asTerm("durationDelta")
        ),
        typeId = TypeId.of[PrimitiveOp.LocalDateTimeDelta],
        recordBinding = new Binding.Record(
          constructor = new Constructor[PrimitiveOp.LocalDateTimeDelta] {
            def usedRegisters: RegisterOffset                                                    = RegisterOffset(objects = 2)
            def construct(in: Registers, offset: RegisterOffset): PrimitiveOp.LocalDateTimeDelta =
              new PrimitiveOp.LocalDateTimeDelta(
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
        modifiers = Chunk.empty
      )
    )

  implicit lazy val primitiveOpPeriodDeltaSchema: Schema[PrimitiveOp.PeriodDelta] =
    new Schema(
      reflect = new Reflect.Record[Binding, PrimitiveOp.PeriodDelta](
        fields = Chunk.single(Schema[java.time.Period].reflect.asTerm("delta")),
        typeId = TypeId.of[PrimitiveOp.PeriodDelta],
        recordBinding = new Binding.Record(
          constructor = new Constructor[PrimitiveOp.PeriodDelta] {
            def usedRegisters: RegisterOffset                                             = RegisterOffset(objects = 1)
            def construct(in: Registers, offset: RegisterOffset): PrimitiveOp.PeriodDelta =
              new PrimitiveOp.PeriodDelta(in.getObject(offset).asInstanceOf[java.time.Period])
          },
          deconstructor = new Deconstructor[PrimitiveOp.PeriodDelta] {
            def usedRegisters: RegisterOffset                                                          = RegisterOffset(objects = 1)
            def deconstruct(out: Registers, offset: RegisterOffset, in: PrimitiveOp.PeriodDelta): Unit =
              out.setObject(offset, in.delta)
          }
        ),
        modifiers = Chunk.empty
      )
    )

  implicit lazy val primitiveOpSchema: Schema[PrimitiveOp] = new Schema(
    reflect = new Reflect.Variant[Binding, PrimitiveOp](
      cases = Chunk(
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
      modifiers = Chunk.empty
    )
  )

  implicit lazy val seqOpInsertSchema: Schema[SeqOp.Insert] =
    new Schema(
      reflect = new Reflect.Record[Binding, SeqOp.Insert](
        fields = Chunk(Schema[Int].reflect.asTerm("index"), Schema[Chunk[DynamicValue]].reflect.asTerm("values")),
        typeId = TypeId.of[SeqOp.Insert],
        recordBinding = new Binding.Record(
          constructor = new Constructor[SeqOp.Insert] {
            def usedRegisters: RegisterOffset                                  = RegisterOffset(ints = 1, objects = 1)
            def construct(in: Registers, offset: RegisterOffset): SeqOp.Insert =
              new SeqOp.Insert(in.getInt(offset), in.getObject(offset).asInstanceOf[Chunk[DynamicValue]])
          },
          deconstructor = new Deconstructor[SeqOp.Insert] {
            def usedRegisters: RegisterOffset                                               = RegisterOffset(ints = 1, objects = 1)
            def deconstruct(out: Registers, offset: RegisterOffset, in: SeqOp.Insert): Unit = {
              out.setInt(offset, in.index)
              out.setObject(offset, in.values)
            }
          }
        ),
        modifiers = Chunk.empty
      )
    )

  implicit lazy val seqOpAppendSchema: Schema[SeqOp.Append] =
    new Schema(
      reflect = new Reflect.Record[Binding, SeqOp.Append](
        fields = Chunk.single(Schema[Chunk[DynamicValue]].reflect.asTerm("values")),
        typeId = TypeId.of[SeqOp.Append],
        recordBinding = new Binding.Record(
          constructor = new Constructor[SeqOp.Append] {
            def usedRegisters: RegisterOffset                                  = RegisterOffset(objects = 1)
            def construct(in: Registers, offset: RegisterOffset): SeqOp.Append =
              new SeqOp.Append(in.getObject(offset).asInstanceOf[Chunk[DynamicValue]])
          },
          deconstructor = new Deconstructor[SeqOp.Append] {
            def usedRegisters: RegisterOffset                                               = RegisterOffset(objects = 1)
            def deconstruct(out: Registers, offset: RegisterOffset, in: SeqOp.Append): Unit =
              out.setObject(offset, in.values)
          }
        ),
        modifiers = Chunk.empty
      )
    )

  implicit lazy val seqOpDeleteSchema: Schema[SeqOp.Delete] =
    new Schema(
      reflect = new Reflect.Record[Binding, SeqOp.Delete](
        fields = Chunk(Schema[Int].reflect.asTerm("index"), Schema[Int].reflect.asTerm("count")),
        typeId = TypeId.of[SeqOp.Delete],
        recordBinding = new Binding.Record(
          constructor = new Constructor[SeqOp.Delete] {
            def usedRegisters: RegisterOffset                                  = RegisterOffset(ints = 2)
            def construct(in: Registers, offset: RegisterOffset): SeqOp.Delete =
              new SeqOp.Delete(in.getInt(offset), in.getInt(RegisterOffset.incrementFloatsAndInts(offset)))
          },
          deconstructor = new Deconstructor[SeqOp.Delete] {
            def usedRegisters: RegisterOffset                                               = RegisterOffset(ints = 2)
            def deconstruct(out: Registers, offset: RegisterOffset, in: SeqOp.Delete): Unit = {
              out.setInt(offset, in.index)
              out.setInt(RegisterOffset.incrementFloatsAndInts(offset), in.count)
            }
          }
        ),
        modifiers = Chunk.empty
      )
    )

  implicit lazy val seqOpModifySchema: Schema[SeqOp.Modify] =
    new Schema(
      reflect = new Reflect.Record[Binding, SeqOp.Modify](
        fields = Chunk(
          Schema[Int].reflect.asTerm("index"),
          Reflect.Deferred(() => operationSchema.reflect).asTerm("op")
        ),
        typeId = TypeId.of[SeqOp.Modify],
        recordBinding = new Binding.Record(
          constructor = new Constructor[SeqOp.Modify] {
            def usedRegisters: RegisterOffset                                  = RegisterOffset(ints = 1, objects = 1)
            def construct(in: Registers, offset: RegisterOffset): SeqOp.Modify =
              new SeqOp.Modify(in.getInt(offset), in.getObject(offset).asInstanceOf[Operation])
          },
          deconstructor = new Deconstructor[SeqOp.Modify] {
            def usedRegisters: RegisterOffset                                               = RegisterOffset(ints = 1, objects = 1)
            def deconstruct(out: Registers, offset: RegisterOffset, in: SeqOp.Modify): Unit = {
              out.setInt(offset, in.index)
              out.setObject(offset, in.op)
            }
          }
        ),
        modifiers = Chunk.empty
      )
    )

  implicit lazy val seqOpSchema: Schema[SeqOp] = new Schema(
    reflect = new Reflect.Variant[Binding, SeqOp](
      cases = Chunk(
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
      modifiers = Chunk.empty
    )
  )

  implicit lazy val mapOpAddSchema: Schema[MapOp.Add] =
    new Schema(
      reflect = new Reflect.Record[Binding, MapOp.Add](
        fields = Chunk(Schema[DynamicValue].reflect.asTerm("key"), Schema[DynamicValue].reflect.asTerm("value")),
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
        modifiers = Chunk.empty
      )
    )

  implicit lazy val mapOpRemoveSchema: Schema[MapOp.Remove] =
    new Schema(
      reflect = new Reflect.Record[Binding, MapOp.Remove](
        fields = Chunk.single(Schema[DynamicValue].reflect.asTerm("key")),
        typeId = TypeId.of[MapOp.Remove],
        recordBinding = new Binding.Record(
          constructor = new Constructor[MapOp.Remove] {
            def usedRegisters: RegisterOffset                                  = RegisterOffset(objects = 1)
            def construct(in: Registers, offset: RegisterOffset): MapOp.Remove =
              new MapOp.Remove(in.getObject(offset).asInstanceOf[DynamicValue])
          },
          deconstructor = new Deconstructor[MapOp.Remove] {
            def usedRegisters: RegisterOffset                                               = RegisterOffset(objects = 1)
            def deconstruct(out: Registers, offset: RegisterOffset, in: MapOp.Remove): Unit =
              out.setObject(offset, in.key)
          }
        ),
        modifiers = Chunk.empty
      )
    )

  implicit lazy val mapOpModifySchema: Schema[MapOp.Modify] =
    new Schema(
      reflect = new Reflect.Record[Binding, MapOp.Modify](
        fields = Chunk(
          Schema[DynamicValue].reflect.asTerm("key"),
          Reflect.Deferred(() => dynamicPatchSchema.reflect).asTerm("patch")
        ),
        typeId = TypeId.of[MapOp.Modify],
        recordBinding = new Binding.Record(
          constructor = new Constructor[MapOp.Modify] {
            def usedRegisters: RegisterOffset                                  = RegisterOffset(objects = 2)
            def construct(in: Registers, offset: RegisterOffset): MapOp.Modify =
              new MapOp.Modify(
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
        modifiers = Chunk.empty
      )
    )

  implicit lazy val mapOpSchema: Schema[MapOp] = new Schema(
    reflect = new Reflect.Variant[Binding, MapOp](
      cases = Chunk(
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
      modifiers = Chunk.empty
    )
  )

  implicit lazy val operationSetSchema: Schema[Operation.Set] =
    new Schema(
      reflect = new Reflect.Record[Binding, Operation.Set](
        fields = Chunk.single(Schema[DynamicValue].reflect.asTerm("value")),
        typeId = TypeId.of[Operation.Set],
        recordBinding = new Binding.Record(
          constructor = new Constructor[Operation.Set] {
            def usedRegisters: RegisterOffset                                   = RegisterOffset(objects = 1)
            def construct(in: Registers, offset: RegisterOffset): Operation.Set =
              new Operation.Set(in.getObject(offset).asInstanceOf[DynamicValue])
          },
          deconstructor = new Deconstructor[Operation.Set] {
            def usedRegisters: RegisterOffset                                                = RegisterOffset(objects = 1)
            def deconstruct(out: Registers, offset: RegisterOffset, in: Operation.Set): Unit =
              out.setObject(offset, in.value)
          }
        ),
        modifiers = Chunk.empty
      )
    )

  implicit lazy val operationPrimitiveDeltaSchema: Schema[Operation.PrimitiveDelta] =
    new Schema(
      reflect = new Reflect.Record[Binding, Operation.PrimitiveDelta](
        fields = Chunk.single(primitiveOpSchema.reflect.asTerm("op")),
        typeId = TypeId.of[Operation.PrimitiveDelta],
        recordBinding = new Binding.Record(
          constructor = new Constructor[Operation.PrimitiveDelta] {
            def usedRegisters: RegisterOffset                                              = RegisterOffset(objects = 1)
            def construct(in: Registers, offset: RegisterOffset): Operation.PrimitiveDelta =
              new Operation.PrimitiveDelta(in.getObject(offset).asInstanceOf[PrimitiveOp])
          },
          deconstructor = new Deconstructor[Operation.PrimitiveDelta] {
            def usedRegisters: RegisterOffset                                                           = RegisterOffset(objects = 1)
            def deconstruct(out: Registers, offset: RegisterOffset, in: Operation.PrimitiveDelta): Unit =
              out.setObject(offset, in.op)
          }
        ),
        modifiers = Chunk.empty
      )
    )

  implicit lazy val operationSequenceEditSchema: Schema[Operation.SequenceEdit] =
    new Schema(
      reflect = new Reflect.Record[Binding, Operation.SequenceEdit](
        fields = Chunk.single(Reflect.Deferred(() => Schema[Chunk[SeqOp]].reflect).asTerm("ops")),
        typeId = TypeId.of[Operation.SequenceEdit],
        recordBinding = new Binding.Record(
          constructor = new Constructor[Operation.SequenceEdit] {
            def usedRegisters: RegisterOffset                                            = RegisterOffset(objects = 1)
            def construct(in: Registers, offset: RegisterOffset): Operation.SequenceEdit =
              new Operation.SequenceEdit(in.getObject(offset).asInstanceOf[Chunk[SeqOp]])
          },
          deconstructor = new Deconstructor[Operation.SequenceEdit] {
            def usedRegisters: RegisterOffset                                                         = RegisterOffset(objects = 1)
            def deconstruct(out: Registers, offset: RegisterOffset, in: Operation.SequenceEdit): Unit =
              out.setObject(offset, in.ops)
          }
        ),
        modifiers = Chunk.empty
      )
    )

  implicit lazy val operationMapEditSchema: Schema[Operation.MapEdit] =
    new Schema(
      reflect = new Reflect.Record[Binding, Operation.MapEdit](
        fields = Chunk.single(Reflect.Deferred(() => Schema[Chunk[MapOp]].reflect).asTerm("ops")),
        typeId = TypeId.of[Operation.MapEdit],
        recordBinding = new Binding.Record(
          constructor = new Constructor[Operation.MapEdit] {
            def usedRegisters: RegisterOffset                                       = RegisterOffset(objects = 1)
            def construct(in: Registers, offset: RegisterOffset): Operation.MapEdit =
              new Operation.MapEdit(in.getObject(offset).asInstanceOf[Chunk[MapOp]])
          },
          deconstructor = new Deconstructor[Operation.MapEdit] {
            def usedRegisters: RegisterOffset                                                    = RegisterOffset(objects = 1)
            def deconstruct(out: Registers, offset: RegisterOffset, in: Operation.MapEdit): Unit =
              out.setObject(offset, in.ops)
          }
        ),
        modifiers = Chunk.empty
      )
    )

  implicit lazy val operationPatchSchema: Schema[Operation.Patch] =
    new Schema(
      reflect = new Reflect.Record[Binding, Operation.Patch](
        fields = Chunk.single(Reflect.Deferred(() => dynamicPatchSchema.reflect).asTerm("patch")),
        typeId = TypeId.of[Operation.Patch],
        recordBinding = new Binding.Record(
          constructor = new Constructor[Operation.Patch] {
            def usedRegisters: RegisterOffset                                     = RegisterOffset(objects = 1)
            def construct(in: Registers, offset: RegisterOffset): Operation.Patch =
              new Operation.Patch(in.getObject(offset).asInstanceOf[DynamicPatch])
          },
          deconstructor = new Deconstructor[Operation.Patch] {
            def usedRegisters: RegisterOffset                                                  = RegisterOffset(objects = 1)
            def deconstruct(out: Registers, offset: RegisterOffset, in: Operation.Patch): Unit =
              out.setObject(offset, in.patch)
          }
        ),
        modifiers = Chunk.empty
      )
    )

  implicit lazy val operationSchema: Schema[Operation] = new Schema(
    reflect = new Reflect.Variant[Binding, Operation](
      cases = Chunk(
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
      modifiers = Chunk.empty
    )
  )

  implicit lazy val dynamicPatchOpSchema: Schema[DynamicPatchOp] =
    new Schema(
      reflect = new Reflect.Record[Binding, DynamicPatchOp](
        fields = Chunk(
          Schema[DynamicOptic].reflect.asTerm("path"),
          Reflect.Deferred(() => operationSchema.reflect).asTerm("operation")
        ),
        typeId = TypeId.of[DynamicPatchOp],
        recordBinding = new Binding.Record(
          constructor = new Constructor[DynamicPatchOp] {
            def usedRegisters: RegisterOffset                                    = RegisterOffset(objects = 2)
            def construct(in: Registers, offset: RegisterOffset): DynamicPatchOp =
              new DynamicPatchOp(
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
        modifiers = Chunk.empty
      )
    )

  implicit lazy val dynamicPatchSchema: Schema[DynamicPatch] =
    new Schema(
      reflect = new Reflect.Record[Binding, DynamicPatch](
        fields = Chunk.single(Reflect.Deferred(() => Schema[Chunk[DynamicPatchOp]].reflect).asTerm("ops")),
        typeId = TypeId.of[DynamicPatch],
        recordBinding = new Binding.Record(
          constructor = new Constructor[DynamicPatch] {
            def usedRegisters: RegisterOffset                                  = RegisterOffset(objects = 1)
            def construct(in: Registers, offset: RegisterOffset): DynamicPatch =
              new DynamicPatch(in.getObject(offset).asInstanceOf[Chunk[DynamicPatchOp]])
          },
          deconstructor = new Deconstructor[DynamicPatch] {
            def usedRegisters: RegisterOffset                                               = RegisterOffset(objects = 1)
            def deconstruct(out: Registers, offset: RegisterOffset, in: DynamicPatch): Unit =
              out.setObject(offset, in.ops)
          }
        ),
        modifiers = Chunk.empty
      )
    )
}
