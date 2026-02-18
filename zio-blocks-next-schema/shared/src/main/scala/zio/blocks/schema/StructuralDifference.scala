package zio.blocks.schema

sealed trait StructuralDifference {
  def apply(version: Int): Option[Int]
}

object StructuralDifference {
  case object V1ToV2 extends StructuralDifference {
    override def apply(version: Int): Option[Int] = version match {
      case 1 => Some(2)
      case _ => None
    }
  }

  // Add more structural differences as needed
}