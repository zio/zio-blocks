package zio.blocks.schema

/**
 * A DynamicPatch is a non-generic representation of a Patch that can be
 * serialized and applied to DynamicValues.
 */
case class DynamicPatch(ops: Vector[DynamicPatch.Op]) {
  def ++(that: DynamicPatch): DynamicPatch = DynamicPatch(ops ++ that.ops)

  def apply(value: DynamicValue): DynamicValue =
    ops.foldLeft(value) { (v, op) =>
      op match {
        case DynamicPatch.Op.Replace(optic, newValue) =>
          set(v, optic, newValue)
        case DynamicPatch.Op.Insert(optic, newValue) =>
          // For now, treat insert as set if the location doesn't exist?
          set(v, optic, newValue)
        case DynamicPatch.Op.Remove(optic) =>
          // Logic for removing from collections/records.
          v
      }
    }

  private def set(target: DynamicValue, optic: DynamicOptic, newValue: DynamicValue): DynamicValue = {
    def loop(current: DynamicValue, nodes: List[DynamicOptic.Node]): DynamicValue = nodes match {
      case Nil          => newValue
      case head :: tail =>
        head match {
          case DynamicOptic.Node.Field(name) =>
            current match {
              case DynamicValue.Record(fields) =>
                DynamicValue.Record(fields.map {
                  case (n, v) if n == name => (n, loop(v, tail))
                  case other               => other
                })
              case _ => current
            }
          case DynamicOptic.Node.Case(name) =>
            current match {
              case DynamicValue.Variant(c, v) if c == name =>
                DynamicValue.Variant(c, loop(v, tail))
              case _ => current
            }
          case DynamicOptic.Node.AtIndex(index) =>
            current match {
              case DynamicValue.Sequence(elements) =>
                if (index >= 0 && index < elements.length) {
                  DynamicValue.Sequence(elements.updated(index, loop(elements(index), tail)))
                } else current
              case _ => current
            }
          case _ => current
        }
    }
    loop(target, optic.nodes.toList)
  }
}

object DynamicPatch {
  sealed trait Op
  object Op {
    case class Replace(optic: DynamicOptic, value: DynamicValue) extends Op
    case class Insert(optic: DynamicOptic, value: DynamicValue)  extends Op
    case class Remove(optic: DynamicOptic)                       extends Op
  }

  def empty: DynamicPatch = DynamicPatch(Vector.empty)

  implicit val schema: Schema[DynamicPatch] = ??? // TODO: Implement schema for DynamicPatch
}
