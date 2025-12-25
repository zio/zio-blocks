package zio.blocks.schema.diff

import zio.blocks.schema._
import zio.blocks.schema.diff.Operation._

object Differ {

  def diff(oldVal: DynamicValue, newVal: DynamicValue): DynamicPatch =
    diffRecursive(DynamicOptic(Vector.empty), oldVal, newVal)

  private def diffRecursive(path: DynamicOptic, oldV: DynamicValue, newV: DynamicValue): DynamicPatch = {
    if (oldV == newV) return DynamicPatch.empty

    (oldV, newV) match {
      case (DynamicValue.Primitive(o), DynamicValue.Primitive(n)) =>
        diffPrimitives(path, o, n)

      case (DynamicValue.Record(oFields), DynamicValue.Record(nFields)) =>
        val oMap    = oFields.toMap
        val nMap    = nFields.toMap
        val allKeys = oMap.keySet ++ nMap.keySet

        val ops = allKeys.toVector.flatMap { key =>
          val subPath = path.field(key)
          (oMap.get(key), nMap.get(key)) match {
            case (Some(ov), Some(nv)) => diffRecursive(subPath, ov, nv).ops
            case (None, Some(nv))     => Vector(DynamicPatchOp(subPath, Set(nv)))
            case (Some(_), None)      => Vector.empty // Deletions in records not supported via simple diff yet
            case _                    => Vector.empty
          }
        }
        DynamicPatch(ops)

      case (DynamicValue.Sequence(oSeq), DynamicValue.Sequence(nSeq)) =>
        if (shouldUseLCS(oSeq, nSeq)) {
          val seqOps = LCS.diffSequence(oSeq, nSeq, diffRecursive(DynamicOptic(Vector.empty), _, _))
          if (seqOps.isEmpty) DynamicPatch.empty
          else DynamicPatch(Vector(DynamicPatchOp(path, SequenceEdit(seqOps))))
        } else {
          DynamicPatch(Vector(DynamicPatchOp(path, Set(newV))))
        }

      case (DynamicValue.Map(oEnt), DynamicValue.Map(nEnt)) =>
        diffMap(path, oEnt, nEnt)

      case (DynamicValue.Variant(oCase, oVal), DynamicValue.Variant(nCase, nVal)) if oCase == nCase =>
        diffRecursive(path.caseOf(oCase), oVal, nVal)

      case _ =>
        DynamicPatch(Vector(DynamicPatchOp(path, Set(newV))))
    }
  }

  private def diffPrimitives(path: DynamicOptic, oldP: PrimitiveValue, newP: PrimitiveValue): DynamicPatch = {
    import PrimitiveValue._
    (oldP, newP) match {
      case (Int(v1), Int(v2)) =>
        DynamicPatch(Vector(DynamicPatchOp(path, PrimitiveDelta(PrimitiveOp.IntDelta(v2 - v1)))))
      case (Long(v1), Long(v2)) =>
        DynamicPatch(Vector(DynamicPatchOp(path, PrimitiveDelta(PrimitiveOp.LongDelta(v2 - v1)))))
      case (Double(v1), Double(v2)) =>
        DynamicPatch(Vector(DynamicPatchOp(path, PrimitiveDelta(PrimitiveOp.DoubleDelta(v2 - v1)))))
      case (String(s1), String(s2)) if shouldUseLCSString(s1, s2) =>
        val ops = LCS.diffString(s1, s2)
        if (ops.nonEmpty) DynamicPatch(Vector(DynamicPatchOp(path, StringEdit(ops)))) else DynamicPatch.empty
      case _ => DynamicPatch(Vector(DynamicPatchOp(path, Set(DynamicValue.Primitive(newP)))))
    }
  }

  private def diffMap(
    path: DynamicOptic,
    oldEnt: Vector[(DynamicValue, DynamicValue)],
    newEnt: Vector[(DynamicValue, DynamicValue)]
  ): DynamicPatch = {
    val oMap = oldEnt.toMap
    val nMap = newEnt.toMap
    val keys = oMap.keySet ++ nMap.keySet

    val mapOps = keys.toVector.flatMap { key =>
      (oMap.get(key), nMap.get(key)) match {
        case (Some(ov), Some(nv)) if ov != nv =>
          val subDiff = diffRecursive(DynamicOptic(Vector.empty), ov, nv)
          if (subDiff.isEmpty) None
          else Some(MapOp.Modify(key, subDiff))
        case (None, Some(nv)) => Some(MapOp.Add(key, nv))
        case (Some(_), None)  => Some(MapOp.Remove(key))
        case _                => None
      }
    }

    if (mapOps.isEmpty) DynamicPatch.empty
    else DynamicPatch(Vector(DynamicPatchOp(path, MapEdit(mapOps))))
  }

  private def shouldUseLCS(oldSeq: Vector[_], newSeq: Vector[_]): Boolean =
    oldSeq.length < 1000 && newSeq.length < 1000

  private def shouldUseLCSString(s1: String, s2: String): Boolean =
    s1.length > 5 && s2.length > 5 && s1.length < 10000 && math.abs(s1.length - s2.length) < s1.length / 2
}

object LCS {
  def diffSequence(
    oldSeq: Vector[DynamicValue],
    newSeq: Vector[DynamicValue],
    diffFn: (DynamicValue, DynamicValue) => DynamicPatch
  ): Vector[SeqOp] = {
    // Simplified diff strategy to avoid complexity of full recursive LCS reconstruction in this snippet
    val minLen = math.min(oldSeq.length, newSeq.length)

    val changes = (0 until minLen).flatMap { idx =>
      val p = diffFn(oldSeq(idx), newSeq(idx))
      if (p.isEmpty) None else Some(SeqOp.Modify(idx, p))
    }

    val extension = if (newSeq.length > oldSeq.length) {
      Some(SeqOp.Append(newSeq.drop(oldSeq.length)))
    } else None

    val deletion = if (oldSeq.length > newSeq.length) {
      Some(SeqOp.Delete(newSeq.length, oldSeq.length - newSeq.length))
    } else None

    (changes ++ extension ++ deletion).toVector
  }

  def diffString(s1: String, s2: String): Vector[StringOp] =
    if (s1 == s2) Vector.empty
    else Vector(StringOp.Delete(0, s1.length), StringOp.Insert(0, s2))
}
