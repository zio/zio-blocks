package zio.blocks.schema

/**
 * An untyped patch that operates on DynamicValue.
 *
 * DynamicPatch represents structural changes as first-class, serializable data.
 * It provides the core building block for the typed Patch[A] API.
 */
final case class DynamicPatch(ops: Vector[DynamicPatchOp]) {

  /** Compose patches sequentially (monoid operation) */
  def ++(that: DynamicPatch): DynamicPatch =
    DynamicPatch(this.ops ++ that.ops)

  /** Apply patch to a DynamicValue with the specified mode */
  def apply(value: DynamicValue, mode: PatchMode): Either[SchemaError, DynamicValue] = {
    var current = value
    var idx = 0
    val len = ops.length

    while (idx < len) {
      ops(idx).applyTo(current, mode) match {
        case Right(next) => current = next
        case left @ Left(_) => return left
      }
      idx += 1
    }
    Right(current)
  }

  /** Apply patch with default Strict mode */
  def apply(value: DynamicValue): Either[SchemaError, DynamicValue] =
    apply(value, PatchMode.Strict)
}

object DynamicPatch {
  /** Empty patch (monoid identity) */
  val empty: DynamicPatch = DynamicPatch(Vector.empty)

  /** Create a patch with a single operation */
  def single(op: DynamicPatchOp): DynamicPatch = DynamicPatch(Vector(op))

  /** Create a set operation at the root */
  def set(value: DynamicValue): DynamicPatch =
    single(DynamicPatchOp(DynamicOptic.root, Operation.Set(value)))

  /** Create a set operation at a specific path */
  def setAt(optic: DynamicOptic, value: DynamicValue): DynamicPatch =
    single(DynamicPatchOp(optic, Operation.Set(value)))
}

/**
 * A single patch operation targeting a specific location via an optic.
 */
final case class DynamicPatchOp(optic: DynamicOptic, operation: Operation) {

  /** Apply this operation to a DynamicValue */
  def applyTo(value: DynamicValue, mode: PatchMode): Either[SchemaError, DynamicValue] = {
    if (optic.nodes.isEmpty) {
      // Root operation - apply directly to value
      operation.applyTo(value, mode)
    } else {
      // Navigate to target and apply
      navigateAndApply(value, optic.nodes.toList, mode)
    }
  }

  private def navigateAndApply(
    value: DynamicValue,
    path: List[DynamicOptic.Node],
    mode: PatchMode
  ): Either[SchemaError, DynamicValue] = {
    path match {
      case Nil =>
        operation.applyTo(value, mode)

      case DynamicOptic.Node.Field(name) :: rest =>
        value match {
          case DynamicValue.Record(fields) =>
            val idx = fields.indexWhere(_._1 == name)
            if (idx < 0) {
              mode match {
                case PatchMode.Strict =>
                  Left(SchemaError.FieldNotFound(name, fields.map(_._1).toList))
                case PatchMode.Lenient =>
                  Right(value)
                case PatchMode.Clobber =>
                  // Add the field with operation result on empty
                  operation.applyTo(DynamicValue.Record(Vector.empty), mode).map { newValue =>
                    DynamicValue.Record(fields :+ (name -> newValue))
                  }
              }
            } else {
              val (fieldName, fieldValue) = fields(idx)
              navigateAndApply(fieldValue, rest, mode).map { newFieldValue =>
                DynamicValue.Record(fields.updated(idx, (fieldName, newFieldValue)))
              }
            }
          case other =>
            Left(SchemaError.TypeMismatch("Record", other.getClass.getSimpleName))
        }

      case DynamicOptic.Node.Case(name) :: rest =>
        value match {
          case DynamicValue.Variant(caseName, innerValue) =>
            if (caseName == name) {
              navigateAndApply(innerValue, rest, mode).map { newInner =>
                DynamicValue.Variant(caseName, newInner)
              }
            } else {
              mode match {
                case PatchMode.Strict =>
                  Left(SchemaError.CaseMismatch(name, caseName))
                case PatchMode.Lenient =>
                  Right(value)
                case PatchMode.Clobber =>
                  operation.applyTo(DynamicValue.Record(Vector.empty), mode).map { newValue =>
                    DynamicValue.Variant(name, newValue)
                  }
              }
            }
          case other =>
            Left(SchemaError.TypeMismatch("Variant", other.getClass.getSimpleName))
        }

      case DynamicOptic.Node.AtIndex(index) :: rest =>
        value match {
          case DynamicValue.Sequence(elements) =>
            if (index >= 0 && index < elements.length) {
              navigateAndApply(elements(index), rest, mode).map { newElement =>
                DynamicValue.Sequence(elements.updated(index, newElement))
              }
            } else {
              mode match {
                case PatchMode.Strict =>
                  Left(SchemaError.IndexOutOfBounds(index, elements.length))
                case PatchMode.Lenient =>
                  Right(value)
                case PatchMode.Clobber =>
                  // Extend sequence if needed
                  val extended = if (index >= elements.length) {
                    elements ++ Vector.fill(index - elements.length + 1)(
                      DynamicValue.Record(Vector.empty)
                    )
                  } else elements
                  navigateAndApply(extended(index), rest, mode).map { newElement =>
                    DynamicValue.Sequence(extended.updated(index, newElement))
                  }
              }
            }
          case other =>
            Left(SchemaError.TypeMismatch("Sequence", other.getClass.getSimpleName))
        }

      case DynamicOptic.Node.AtMapKey(key) :: rest =>
        value match {
          case DynamicValue.Map(entries) =>
            val keyDv = key.asInstanceOf[DynamicValue]
            val idx = entries.indexWhere(_._1 == keyDv)
            if (idx >= 0) {
              navigateAndApply(entries(idx)._2, rest, mode).map { newValue =>
                DynamicValue.Map(entries.updated(idx, (keyDv, newValue)))
              }
            } else {
              mode match {
                case PatchMode.Strict =>
                  Left(SchemaError.KeyNotFound(keyDv.toString))
                case PatchMode.Lenient =>
                  Right(value)
                case PatchMode.Clobber =>
                  operation.applyTo(DynamicValue.Record(Vector.empty), mode).map { newValue =>
                    DynamicValue.Map(entries :+ (keyDv, newValue))
                  }
              }
            }
          case other =>
            Left(SchemaError.TypeMismatch("Map", other.getClass.getSimpleName))
        }

      case DynamicOptic.Node.Elements :: rest =>
        value match {
          case DynamicValue.Sequence(elements) =>
            var idx = 0
            var result = Vector.empty[DynamicValue]
            while (idx < elements.length) {
              navigateAndApply(elements(idx), rest, mode) match {
                case Right(newElement) =>
                  result = result :+ newElement
                case Left(err) =>
                  mode match {
                    case PatchMode.Strict => return Left(err)
                    case PatchMode.Lenient => result = result :+ elements(idx)
                    case PatchMode.Clobber => result = result :+ elements(idx)
                  }
              }
              idx += 1
            }
            Right(DynamicValue.Sequence(result))
          case other =>
            Left(SchemaError.TypeMismatch("Sequence", other.getClass.getSimpleName))
        }

      case DynamicOptic.Node.MapValues :: rest =>
        value match {
          case DynamicValue.Map(entries) =>
            var idx = 0
            var result = Vector.empty[(DynamicValue, DynamicValue)]
            while (idx < entries.length) {
              val (k, v) = entries(idx)
              navigateAndApply(v, rest, mode) match {
                case Right(newValue) =>
                  result = result :+ (k -> newValue)
                case Left(err) =>
                  mode match {
                    case PatchMode.Strict => return Left(err)
                    case PatchMode.Lenient => result = result :+ entries(idx)
                    case PatchMode.Clobber => result = result :+ entries(idx)
                  }
              }
              idx += 1
            }
            Right(DynamicValue.Map(result))
          case other =>
            Left(SchemaError.TypeMismatch("Map", other.getClass.getSimpleName))
        }

      case DynamicOptic.Node.Wrapped :: rest =>
        // Wrapped values are typically just passed through
        navigateAndApply(value, rest, mode)

      case _ :: rest =>
        // Handle other node types with lenient fallback
        Right(value)
    }
  }
}

/**
 * Operations that can be performed on a DynamicValue.
 */
sealed trait Operation {
  def applyTo(value: DynamicValue, mode: PatchMode): Either[SchemaError, DynamicValue]
}

object Operation {
  /** Replace the value entirely */
  final case class Set(newValue: DynamicValue) extends Operation {
    def applyTo(value: DynamicValue, mode: PatchMode): Either[SchemaError, DynamicValue] =
      Right(newValue)
  }

  /** Apply a delta operation to a primitive */
  final case class PrimitiveDelta(op: PrimitiveOp) extends Operation {
    def applyTo(value: DynamicValue, mode: PatchMode): Either[SchemaError, DynamicValue] =
      op.applyTo(value, mode)
  }

  /** Apply sequence edit operations */
  final case class SequenceEdit(ops: Vector[SeqOp]) extends Operation {
    def applyTo(value: DynamicValue, mode: PatchMode): Either[SchemaError, DynamicValue] = {
      value match {
        case DynamicValue.Sequence(elements) =>
          var current = elements
          var idx = 0
          while (idx < ops.length) {
            ops(idx).applyTo(current, mode) match {
              case Right(next) => current = next
              case left @ Left(_) => return left.asInstanceOf[Either[SchemaError, DynamicValue]]
            }
            idx += 1
          }
          Right(DynamicValue.Sequence(current))
        case other =>
          Left(SchemaError.TypeMismatch("Sequence", other.getClass.getSimpleName))
      }
    }
  }

  /** Apply map edit operations */
  final case class MapEdit(ops: Vector[MapOp]) extends Operation {
    def applyTo(value: DynamicValue, mode: PatchMode): Either[SchemaError, DynamicValue] = {
      value match {
        case DynamicValue.Map(entries) =>
          var current = entries
          var idx = 0
          while (idx < ops.length) {
            ops(idx).applyTo(current, mode) match {
              case Right(next) => current = next
              case left @ Left(_) => return left.asInstanceOf[Either[SchemaError, DynamicValue]]
            }
            idx += 1
          }
          Right(DynamicValue.Map(current))
        case other =>
          Left(SchemaError.TypeMismatch("Map", other.getClass.getSimpleName))
      }
    }
  }
}

/**
 * Primitive-level operations for numeric deltas, string edits, etc.
 */
sealed trait PrimitiveOp {
  def applyTo(value: DynamicValue, mode: PatchMode): Either[SchemaError, DynamicValue]
}

object PrimitiveOp {
  final case class IntDelta(delta: Int) extends PrimitiveOp {
    def applyTo(value: DynamicValue, mode: PatchMode): Either[SchemaError, DynamicValue] = {
      value match {
        case DynamicValue.Primitive(PrimitiveValue.Int(n)) =>
          Right(DynamicValue.Primitive(PrimitiveValue.Int(n + delta)))
        case other =>
          Left(SchemaError.TypeMismatch("Int", other.getClass.getSimpleName))
      }
    }
  }

  final case class LongDelta(delta: Long) extends PrimitiveOp {
    def applyTo(value: DynamicValue, mode: PatchMode): Either[SchemaError, DynamicValue] = {
      value match {
        case DynamicValue.Primitive(PrimitiveValue.Long(n)) =>
          Right(DynamicValue.Primitive(PrimitiveValue.Long(n + delta)))
        case other =>
          Left(SchemaError.TypeMismatch("Long", other.getClass.getSimpleName))
      }
    }
  }

  final case class DoubleDelta(delta: Double) extends PrimitiveOp {
    def applyTo(value: DynamicValue, mode: PatchMode): Either[SchemaError, DynamicValue] = {
      value match {
        case DynamicValue.Primitive(PrimitiveValue.Double(n)) =>
          Right(DynamicValue.Primitive(PrimitiveValue.Double(n + delta)))
        case other =>
          Left(SchemaError.TypeMismatch("Double", other.getClass.getSimpleName))
      }
    }
  }

  final case class FloatDelta(delta: Float) extends PrimitiveOp {
    def applyTo(value: DynamicValue, mode: PatchMode): Either[SchemaError, DynamicValue] = {
      value match {
        case DynamicValue.Primitive(PrimitiveValue.Float(n)) =>
          Right(DynamicValue.Primitive(PrimitiveValue.Float(n + delta)))
        case other =>
          Left(SchemaError.TypeMismatch("Float", other.getClass.getSimpleName))
      }
    }
  }

  final case class ShortDelta(delta: Short) extends PrimitiveOp {
    def applyTo(value: DynamicValue, mode: PatchMode): Either[SchemaError, DynamicValue] = {
      value match {
        case DynamicValue.Primitive(PrimitiveValue.Short(n)) =>
          Right(DynamicValue.Primitive(PrimitiveValue.Short((n + delta).toShort)))
        case other =>
          Left(SchemaError.TypeMismatch("Short", other.getClass.getSimpleName))
      }
    }
  }

  final case class ByteDelta(delta: Byte) extends PrimitiveOp {
    def applyTo(value: DynamicValue, mode: PatchMode): Either[SchemaError, DynamicValue] = {
      value match {
        case DynamicValue.Primitive(PrimitiveValue.Byte(n)) =>
          Right(DynamicValue.Primitive(PrimitiveValue.Byte((n + delta).toByte)))
        case other =>
          Left(SchemaError.TypeMismatch("Byte", other.getClass.getSimpleName))
      }
    }
  }

  final case class BigIntDelta(delta: BigInt) extends PrimitiveOp {
    def applyTo(value: DynamicValue, mode: PatchMode): Either[SchemaError, DynamicValue] = {
      value match {
        case DynamicValue.Primitive(PrimitiveValue.BigInt(n)) =>
          Right(DynamicValue.Primitive(PrimitiveValue.BigInt(n + delta)))
        case other =>
          Left(SchemaError.TypeMismatch("BigInt", other.getClass.getSimpleName))
      }
    }
  }

  final case class BigDecimalDelta(delta: BigDecimal) extends PrimitiveOp {
    def applyTo(value: DynamicValue, mode: PatchMode): Either[SchemaError, DynamicValue] = {
      value match {
        case DynamicValue.Primitive(PrimitiveValue.BigDecimal(n)) =>
          Right(DynamicValue.Primitive(PrimitiveValue.BigDecimal(n + delta)))
        case other =>
          Left(SchemaError.TypeMismatch("BigDecimal", other.getClass.getSimpleName))
      }
    }
  }

  /** String edit operations (LCS-based) */
  final case class StringEdit(ops: Vector[StringOp]) extends PrimitiveOp {
    def applyTo(value: DynamicValue, mode: PatchMode): Either[SchemaError, DynamicValue] = {
      value match {
        case DynamicValue.Primitive(PrimitiveValue.String(s)) =>
          val result = StringOp.apply(s, ops)
          Right(DynamicValue.Primitive(PrimitiveValue.String(result)))
        case other =>
          Left(SchemaError.TypeMismatch("String", other.getClass.getSimpleName))
      }
    }
  }
}

/**
 * String edit operations for LCS-based diffing.
 */
sealed trait StringOp

object StringOp {
  final case class Insert(index: Int, text: String) extends StringOp
  final case class Delete(index: Int, length: Int) extends StringOp

  /** Apply a sequence of string operations */
  def apply(s: String, ops: Vector[StringOp]): String = {
    val sb = new StringBuilder(s)
    // Sort operations by index descending to avoid offset issues
    val sortedOps = ops.sortBy {
      case Insert(idx, _) => -idx
      case Delete(idx, _) => -idx
    }
    sortedOps.foreach {
      case Insert(idx, text) =>
        if (idx >= 0 && idx <= sb.length) {
          sb.insert(idx, text)
        }
      case Delete(idx, length) =>
        if (idx >= 0 && idx + length <= sb.length) {
          sb.delete(idx, idx + length)
        }
    }
    sb.toString
  }

  /** Compute LCS-based string diff operations */
  def diff(oldStr: String, newStr: String): Vector[StringOp] = {
    if (oldStr == newStr) return Vector.empty
    if (oldStr.isEmpty) return Vector(Insert(0, newStr))
    if (newStr.isEmpty) return Vector(Delete(0, oldStr.length))

    // Simple LCS-based diff
    val lcs = longestCommonSubsequence(oldStr, newStr)
    if (lcs.isEmpty) {
      // No common subsequence - replace entirely
      Vector(Delete(0, oldStr.length), Insert(0, newStr))
    } else {
      computeEditOps(oldStr, newStr, lcs)
    }
  }

  private def longestCommonSubsequence(a: String, b: String): String = {
    val m = a.length
    val n = b.length
    val dp = Array.ofDim[Int](m + 1, n + 1)

    for (i <- 1 to m; j <- 1 to n) {
      if (a(i - 1) == b(j - 1)) {
        dp(i)(j) = dp(i - 1)(j - 1) + 1
      } else {
        dp(i)(j) = Math.max(dp(i - 1)(j), dp(i)(j - 1))
      }
    }

    // Backtrack to find the LCS string
    val sb = new StringBuilder
    var i = m
    var j = n
    while (i > 0 && j > 0) {
      if (a(i - 1) == b(j - 1)) {
        sb.insert(0, a(i - 1))
        i -= 1
        j -= 1
      } else if (dp(i - 1)(j) > dp(i)(j - 1)) {
        i -= 1
      } else {
        j -= 1
      }
    }
    sb.toString
  }

  private def computeEditOps(oldStr: String, newStr: String, lcs: String): Vector[StringOp] = {
    val ops = Vector.newBuilder[StringOp]
    var oldIdx = 0
    var newIdx = 0
    var lcsIdx = 0

    while (lcsIdx < lcs.length) {
      val lcsChar = lcs(lcsIdx)

      // Delete characters from old that don't match LCS
      var deleteStart = oldIdx
      while (oldIdx < oldStr.length && oldStr(oldIdx) != lcsChar) {
        oldIdx += 1
      }
      if (oldIdx > deleteStart) {
        ops += Delete(deleteStart, oldIdx - deleteStart)
      }

      // Insert characters from new that don't match LCS
      var insertStart = newIdx
      while (newIdx < newStr.length && newStr(newIdx) != lcsChar) {
        newIdx += 1
      }
      if (newIdx > insertStart) {
        ops += Insert(deleteStart, newStr.substring(insertStart, newIdx))
      }

      // Move past the matching LCS character
      oldIdx += 1
      newIdx += 1
      lcsIdx += 1
    }

    // Handle remaining characters after LCS
    if (oldIdx < oldStr.length) {
      ops += Delete(oldIdx, oldStr.length - oldIdx)
    }
    if (newIdx < newStr.length) {
      ops += Insert(oldIdx, newStr.substring(newIdx))
    }

    ops.result()
  }
}

/**
 * Sequence edit operations.
 */
sealed trait SeqOp {
  def applyTo(elements: Vector[DynamicValue], mode: PatchMode): Either[SchemaError, Vector[DynamicValue]]
}

object SeqOp {
  final case class Insert(index: Int, values: Vector[DynamicValue]) extends SeqOp {
    def applyTo(elements: Vector[DynamicValue], mode: PatchMode): Either[SchemaError, Vector[DynamicValue]] = {
      if (index >= 0 && index <= elements.length) {
        val (before, after) = elements.splitAt(index)
        Right(before ++ values ++ after)
      } else {
        mode match {
          case PatchMode.Strict =>
            Left(SchemaError.IndexOutOfBounds(index, elements.length))
          case PatchMode.Lenient =>
            Right(elements)
          case PatchMode.Clobber =>
            Right(elements ++ values)
        }
      }
    }
  }

  final case class Append(values: Vector[DynamicValue]) extends SeqOp {
    def applyTo(elements: Vector[DynamicValue], mode: PatchMode): Either[SchemaError, Vector[DynamicValue]] =
      Right(elements ++ values)
  }

  final case class Delete(index: Int, count: Int) extends SeqOp {
    def applyTo(elements: Vector[DynamicValue], mode: PatchMode): Either[SchemaError, Vector[DynamicValue]] = {
      if (index >= 0 && index + count <= elements.length) {
        Right(elements.take(index) ++ elements.drop(index + count))
      } else {
        mode match {
          case PatchMode.Strict =>
            Left(SchemaError.IndexOutOfBounds(index, elements.length))
          case PatchMode.Lenient =>
            Right(elements)
          case PatchMode.Clobber =>
            val safeIndex = Math.max(0, Math.min(index, elements.length))
            val safeEnd = Math.min(safeIndex + count, elements.length)
            Right(elements.take(safeIndex) ++ elements.drop(safeEnd))
        }
      }
    }
  }

  final case class Modify(index: Int, op: Operation) extends SeqOp {
    def applyTo(elements: Vector[DynamicValue], mode: PatchMode): Either[SchemaError, Vector[DynamicValue]] = {
      if (index >= 0 && index < elements.length) {
        op.applyTo(elements(index), mode).map { newValue =>
          elements.updated(index, newValue)
        }
      } else {
        mode match {
          case PatchMode.Strict =>
            Left(SchemaError.IndexOutOfBounds(index, elements.length))
          case PatchMode.Lenient =>
            Right(elements)
          case PatchMode.Clobber =>
            Right(elements)
        }
      }
    }
  }

  /** Compute LCS-based sequence diff operations */
  def diff(oldSeq: Vector[DynamicValue], newSeq: Vector[DynamicValue]): Vector[SeqOp] = {
    if (oldSeq == newSeq) return Vector.empty
    if (oldSeq.isEmpty) return Vector(Append(newSeq))
    if (newSeq.isEmpty) return Vector(Delete(0, oldSeq.length))

    // LCS-based sequence diff
    val lcs = longestCommonSubsequence(oldSeq, newSeq)
    computeSeqOps(oldSeq, newSeq, lcs)
  }

  private def longestCommonSubsequence(
    a: Vector[DynamicValue],
    b: Vector[DynamicValue]
  ): Vector[DynamicValue] = {
    val m = a.length
    val n = b.length
    val dp = Array.ofDim[Int](m + 1, n + 1)

    for (i <- 1 to m; j <- 1 to n) {
      if (a(i - 1) == b(j - 1)) {
        dp(i)(j) = dp(i - 1)(j - 1) + 1
      } else {
        dp(i)(j) = Math.max(dp(i - 1)(j), dp(i)(j - 1))
      }
    }

    // Backtrack
    val result = Vector.newBuilder[DynamicValue]
    var i = m
    var j = n
    while (i > 0 && j > 0) {
      if (a(i - 1) == b(j - 1)) {
        result += a(i - 1)
        i -= 1
        j -= 1
      } else if (dp(i - 1)(j) > dp(i)(j - 1)) {
        i -= 1
      } else {
        j -= 1
      }
    }
    result.result().reverse
  }

  private def computeSeqOps(
    oldSeq: Vector[DynamicValue],
    newSeq: Vector[DynamicValue],
    lcs: Vector[DynamicValue]
  ): Vector[SeqOp] = {
    val ops = Vector.newBuilder[SeqOp]
    var oldIdx = 0
    var newIdx = 0
    var lcsIdx = 0
    var offset = 0 // Tracks position shifts from previous operations

    while (lcsIdx < lcs.length) {
      val lcsElem = lcs(lcsIdx)

      // Delete elements from old that don't match LCS
      var deleteCount = 0
      while (oldIdx < oldSeq.length && oldSeq(oldIdx) != lcsElem) {
        deleteCount += 1
        oldIdx += 1
      }
      if (deleteCount > 0) {
        ops += Delete(oldIdx - deleteCount + offset, deleteCount)
        offset -= deleteCount
      }

      // Insert elements from new that don't match LCS
      val insertElements = Vector.newBuilder[DynamicValue]
      while (newIdx < newSeq.length && newSeq(newIdx) != lcsElem) {
        insertElements += newSeq(newIdx)
        newIdx += 1
      }
      val toInsert = insertElements.result()
      if (toInsert.nonEmpty) {
        ops += Insert(oldIdx + offset, toInsert)
        offset += toInsert.length
      }

      // Move past the matching LCS element
      oldIdx += 1
      newIdx += 1
      lcsIdx += 1
    }

    // Handle remaining elements after LCS
    if (oldIdx < oldSeq.length) {
      ops += Delete(oldIdx + offset, oldSeq.length - oldIdx)
    }
    if (newIdx < newSeq.length) {
      ops += Append(newSeq.drop(newIdx))
    }

    ops.result()
  }
}

/**
 * Map edit operations.
 */
sealed trait MapOp {
  def applyTo(entries: Vector[(DynamicValue, DynamicValue)], mode: PatchMode): Either[SchemaError, Vector[(DynamicValue, DynamicValue)]]
}

object MapOp {
  final case class Add(key: DynamicValue, value: DynamicValue) extends MapOp {
    def applyTo(entries: Vector[(DynamicValue, DynamicValue)], mode: PatchMode): Either[SchemaError, Vector[(DynamicValue, DynamicValue)]] = {
      val existingIdx = entries.indexWhere(_._1 == key)
      if (existingIdx >= 0) {
        mode match {
          case PatchMode.Strict =>
            Left(SchemaError.KeyAlreadyExists(key.toString))
          case PatchMode.Lenient =>
            Right(entries)
          case PatchMode.Clobber =>
            Right(entries.updated(existingIdx, (key, value)))
        }
      } else {
        Right(entries :+ (key, value))
      }
    }
  }

  final case class Remove(key: DynamicValue) extends MapOp {
    def applyTo(entries: Vector[(DynamicValue, DynamicValue)], mode: PatchMode): Either[SchemaError, Vector[(DynamicValue, DynamicValue)]] = {
      val existingIdx = entries.indexWhere(_._1 == key)
      if (existingIdx >= 0) {
        Right(entries.take(existingIdx) ++ entries.drop(existingIdx + 1))
      } else {
        mode match {
          case PatchMode.Strict =>
            Left(SchemaError.KeyNotFound(key.toString))
          case PatchMode.Lenient =>
            Right(entries)
          case PatchMode.Clobber =>
            Right(entries)
        }
      }
    }
  }

  final case class Modify(key: DynamicValue, op: Operation) extends MapOp {
    def applyTo(entries: Vector[(DynamicValue, DynamicValue)], mode: PatchMode): Either[SchemaError, Vector[(DynamicValue, DynamicValue)]] = {
      val existingIdx = entries.indexWhere(_._1 == key)
      if (existingIdx >= 0) {
        op.applyTo(entries(existingIdx)._2, mode).map { newValue =>
          entries.updated(existingIdx, (key, newValue))
        }
      } else {
        mode match {
          case PatchMode.Strict =>
            Left(SchemaError.KeyNotFound(key.toString))
          case PatchMode.Lenient =>
            Right(entries)
          case PatchMode.Clobber =>
            Right(entries)
        }
      }
    }
  }

  /** Compute diff operations between two maps */
  def diff(
    oldMap: Vector[(DynamicValue, DynamicValue)],
    newMap: Vector[(DynamicValue, DynamicValue)]
  ): Vector[MapOp] = {
    val ops = Vector.newBuilder[MapOp]
    val oldKeys = oldMap.map(_._1).toSet
    val newKeys = newMap.map(_._1).toSet

    // Remove keys that no longer exist
    for ((k, _) <- oldMap if !newKeys.contains(k)) {
      ops += Remove(k)
    }

    // Add new keys or modify existing
    for ((k, v) <- newMap) {
      val oldValue = oldMap.find(_._1 == k).map(_._2)
      oldValue match {
        case Some(oldV) if oldV != v =>
          // Value changed - use Set operation
          ops += Modify(k, Operation.Set(v))
        case None =>
          // New key
          ops += Add(k, v)
        case _ =>
          // No change
      }
    }

    ops.result()
  }
}

/**
 * Patch application mode controlling behavior on precondition violations.
 */
sealed trait PatchMode

object PatchMode {
  /** Fail on precondition violations (e.g. modifying non-existent key) */
  case object Strict extends PatchMode

  /** Skip operations that fail preconditions */
  case object Lenient extends PatchMode

  /** Replace/overwrite on conflicts */
  case object Clobber extends PatchMode
}
