import zio.blocks.schema.Diff._
import zio.blocks.schema.StructuralDifference
  def addDifference(difference: StructuralDifference): Unit

object Diff {
  case object V1ToV2 extends Diff {
    override def apply(version: Int): Option[Int] = version match {
      case 1 => Some(2)
      case _ => None
    }

    def addDifference(difference: StructuralDifference): Unit = {
      // Implementation for adding difference
    }
  }

  case object V2ToV3 extends Diff {
    override def apply(version: Int): Option[Int] = version match {
      case 2 => Some(3)
      case _ => None
    }

    def addDifference(difference: StructuralDifference): Unit = {
      // Implementation for adding difference
    }
  }
}