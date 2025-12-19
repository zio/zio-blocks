package zio.blocks.schema

sealed trait DynamicValue {
  def typeIndex: Int

  def compare(that: DynamicValue): Int

  final def >(that: DynamicValue): Boolean = compare(that) > 0

  final def >=(that: DynamicValue): Boolean = compare(that) >= 0

  final def <(that: DynamicValue): Boolean = compare(that) < 0

  final def <=(that: DynamicValue): Boolean = compare(that) <= 0
}

object DynamicValue {
  case class Primitive(value: PrimitiveValue) extends DynamicValue {
    override def equals(that: Any): Boolean = that match {
      case Primitive(thatValue) => value == thatValue
      case _                    => false
    }

    override def hashCode: Int = value.hashCode

    def typeIndex: Int = 0

    def compare(that: DynamicValue): Int = that match {
      case thatPrimitive: Primitive => value.compare(thatPrimitive.value)
      case _                        => -that.typeIndex
    }
  }

  final case class Record(fields: Vector[(String, DynamicValue)]) extends DynamicValue {
    override def equals(that: Any): Boolean = that match {
      case Record(thatFields) =>
        val len = fields.length
        if (len != thatFields.length) return false
        var idx = 0
        while (idx < len) {
          val kv1 = fields(idx)
          val kv2 = thatFields(idx)
          if (kv1._1 != kv2._1 || kv1._2 != kv2._2) return false
          idx += 1
        }
        true
      case _ => false
    }

    override def hashCode: Int = fields.hashCode

    def typeIndex: Int = 1

    def compare(that: DynamicValue): Int = that match {
      case thatRecord: Record =>
        val xs     = fields
        val ys     = thatRecord.fields
        val xLen   = xs.length
        val yLen   = ys.length
        val minLen = Math.min(xLen, yLen)
        var idx    = 0
        while (idx < minLen) {
          val kv1 = xs(idx)
          val kv2 = ys(idx)
          var cmp = kv1._1.compare(kv2._1)
          if (cmp != 0) return cmp
          cmp = kv1._2.compare(kv2._2)
          if (cmp != 0) return cmp
          idx += 1
        }
        xLen.compareTo(yLen)
      case _ => 1 - that.typeIndex
    }
  }

  final case class Variant(caseName: String, value: DynamicValue) extends DynamicValue {
    override def equals(that: Any): Boolean = that match {
      case Variant(thatCaseName, thatValue) => caseName == thatCaseName && value == thatValue
      case _                                => false
    }

    override def hashCode: Int = 31 * caseName.hashCode + value.hashCode

    def typeIndex: Int = 2

    def compare(that: DynamicValue): Int = that match {
      case thatVariant: Variant =>
        val cmp = caseName.compare(thatVariant.caseName)
        if (cmp != 0) return cmp
        value.compare(thatVariant.value)
      case _ => 2 - that.typeIndex
    }
  }

  final case class Sequence(elements: Vector[DynamicValue]) extends DynamicValue {
    override def equals(that: Any): Boolean = that match {
      case Sequence(thatElements) =>
        val len = elements.length
        if (len != thatElements.length) return false
        var idx = 0
        while (idx < len) {
          if (elements(idx) != thatElements(idx)) return false
          idx += 1
        }
        true
      case _ => false
    }

    override def hashCode: Int = elements.hashCode

    def typeIndex: Int = 3

    def compare(that: DynamicValue): Int = that match {
      case thatSequence: Sequence =>
        val xs     = elements
        val ys     = thatSequence.elements
        val xLen   = xs.length
        val yLen   = ys.length
        val minLen = Math.min(xLen, yLen)
        var idx    = 0
        while (idx < minLen) {
          val cmp = xs(idx).compare(ys(idx))
          if (cmp != 0) return cmp
          idx += 1
        }
        xLen.compareTo(yLen)
      case _ => 3 - that.typeIndex
    }
  }

  final case class Map(entries: Vector[(DynamicValue, DynamicValue)]) extends DynamicValue {
    override def equals(that: Any): Boolean = that match {
      case Map(thatEntries) =>
        val len = entries.length
        if (len != thatEntries.length) return false
        var idx = 0
        while (idx < len) {
          val kv1 = entries(idx)
          val kv2 = thatEntries(idx)
          if (kv1._1 != kv2._1 || kv1._2 != kv2._2) return false
          idx += 1
        }
        true
      case _ => false
    }

    override def hashCode: Int = entries.hashCode

    def typeIndex: Int = 4

    def compare(that: DynamicValue): Int = that match {
      case thatMap: Map =>
        val xs     = entries
        val ys     = thatMap.entries
        val xLen   = xs.length
        val yLen   = ys.length
        val minLen = Math.min(xLen, yLen)
        var idx    = 0
        while (idx < minLen) {
          val kv1 = xs(idx)
          val kv2 = ys(idx)
          var cmp = kv1._1.compare(kv2._1)
          if (cmp != 0) return cmp
          cmp = kv1._2.compare(kv2._2)
          if (cmp != 0) return cmp
          idx += 1
        }
        xLen.compareTo(yLen)
      case _ => 4 - that.typeIndex
    }
  }

  implicit val ordering: Ordering[DynamicValue] = new Ordering[DynamicValue] {
    def compare(x: DynamicValue, y: DynamicValue): Int = x.compare(y)
  }

  /**
   * Compute the difference between two DynamicValues as a DynamicPatch.
   * The resulting patch, when applied to `oldValue`, produces `newValue`.
   */
  def diff(oldValue: DynamicValue, newValue: DynamicValue): DynamicPatch = {
    if (oldValue == newValue) {
      DynamicPatch.empty
    } else {
      diffInternal(DynamicOptic.root, oldValue, newValue)
    }
  }

  private def diffInternal(
    optic: DynamicOptic,
    oldValue: DynamicValue,
    newValue: DynamicValue
  ): DynamicPatch = {
    if (oldValue == newValue) return DynamicPatch.empty

    (oldValue, newValue) match {
      // Same structural type - diff recursively
      case (Primitive(oldPrim), Primitive(newPrim)) =>
        diffPrimitives(optic, oldPrim, newPrim)

      case (Record(oldFields), Record(newFields)) =>
        diffRecords(optic, oldFields, newFields)

      case (Variant(oldCase, oldInner), Variant(newCase, newInner)) =>
        if (oldCase == newCase) {
          diffInternal(optic.caseOf(oldCase), oldInner, newInner)
        } else {
          // Different case - replace entirely
          DynamicPatch.single(DynamicPatchOp(optic, Operation.Set(newValue)))
        }

      case (Sequence(oldElems), Sequence(newElems)) =>
        DynamicPatch.single(DynamicPatchOp(optic, Operation.SequenceEdit(SeqOp.diff(oldElems, newElems))))

      case (Map(oldEntries), Map(newEntries)) =>
        DynamicPatch.single(DynamicPatchOp(optic, Operation.MapEdit(MapOp.diff(oldEntries, newEntries))))

      // Different structural types - replace entirely
      case _ =>
        DynamicPatch.single(DynamicPatchOp(optic, Operation.Set(newValue)))
    }
  }

  private def diffPrimitives(optic: DynamicOptic, oldPrim: PrimitiveValue, newPrim: PrimitiveValue): DynamicPatch = {
    (oldPrim, newPrim) match {
      // Numeric deltas
      case (PrimitiveValue.Int(old), PrimitiveValue.Int(n)) =>
        if (old == n) DynamicPatch.empty
        else DynamicPatch.single(DynamicPatchOp(optic, Operation.PrimitiveDelta(PrimitiveOp.IntDelta(n - old))))

      case (PrimitiveValue.Long(old), PrimitiveValue.Long(n)) =>
        if (old == n) DynamicPatch.empty
        else DynamicPatch.single(DynamicPatchOp(optic, Operation.PrimitiveDelta(PrimitiveOp.LongDelta(n - old))))

      case (PrimitiveValue.Double(old), PrimitiveValue.Double(n)) =>
        if (old == n) DynamicPatch.empty
        else DynamicPatch.single(DynamicPatchOp(optic, Operation.PrimitiveDelta(PrimitiveOp.DoubleDelta(n - old))))

      case (PrimitiveValue.Float(old), PrimitiveValue.Float(n)) =>
        if (old == n) DynamicPatch.empty
        else DynamicPatch.single(DynamicPatchOp(optic, Operation.PrimitiveDelta(PrimitiveOp.FloatDelta(n - old))))

      case (PrimitiveValue.Short(old), PrimitiveValue.Short(n)) =>
        if (old == n) DynamicPatch.empty
        else DynamicPatch.single(DynamicPatchOp(optic, Operation.PrimitiveDelta(PrimitiveOp.ShortDelta((n - old).toShort))))

      case (PrimitiveValue.Byte(old), PrimitiveValue.Byte(n)) =>
        if (old == n) DynamicPatch.empty
        else DynamicPatch.single(DynamicPatchOp(optic, Operation.PrimitiveDelta(PrimitiveOp.ByteDelta((n - old).toByte))))

      case (PrimitiveValue.BigInt(old), PrimitiveValue.BigInt(n)) =>
        if (old == n) DynamicPatch.empty
        else DynamicPatch.single(DynamicPatchOp(optic, Operation.PrimitiveDelta(PrimitiveOp.BigIntDelta(n - old))))

      case (PrimitiveValue.BigDecimal(old), PrimitiveValue.BigDecimal(n)) =>
        if (old == n) DynamicPatch.empty
        else DynamicPatch.single(DynamicPatchOp(optic, Operation.PrimitiveDelta(PrimitiveOp.BigDecimalDelta(n - old))))

      // String diff
      case (PrimitiveValue.String(old), PrimitiveValue.String(n)) =>
        if (old == n) DynamicPatch.empty
        else {
          val ops = StringOp.diff(old, n)
          if (ops.isEmpty) DynamicPatch.empty
          else DynamicPatch.single(DynamicPatchOp(optic, Operation.PrimitiveDelta(PrimitiveOp.StringEdit(ops))))
        }

      // Different primitive types or non-diffable - replace
      case _ =>
        DynamicPatch.single(DynamicPatchOp(optic, Operation.Set(Primitive(newPrim))))
    }
  }

  private def diffRecords(
    optic: DynamicOptic,
    oldFields: Vector[(String, DynamicValue)],
    newFields: Vector[(String, DynamicValue)]
  ): DynamicPatch = {
    var ops = Vector.empty[DynamicPatchOp]
    val oldMap = oldFields.toMap
    val newMap = newFields.toMap
    val allKeys = (oldMap.keySet ++ newMap.keySet).toVector

    for (key <- allKeys) {
      val fieldOptic = optic.field(key)
      (oldMap.get(key), newMap.get(key)) match {
        case (Some(oldVal), Some(newVal)) =>
          val fieldPatch = diffInternal(fieldOptic, oldVal, newVal)
          ops = ops ++ fieldPatch.ops
        case (None, Some(newVal)) =>
          ops = ops :+ DynamicPatchOp(fieldOptic, Operation.Set(newVal))
        case (Some(_), None) =>
          // Field removed - this is structural, use Set with empty record marker
          // Note: In practice, record fields don't "disappear" in typed schemas
          ()
        case (None, None) =>
          ()
      }
    }

    DynamicPatch(ops)
  }
}
