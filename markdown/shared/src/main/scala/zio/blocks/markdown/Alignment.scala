package zio.blocks.markdown

sealed trait Alignment extends Product with Serializable

object Alignment {
  case object Left   extends Alignment
  case object Right  extends Alignment
  case object Center extends Alignment
  case object None   extends Alignment
}
