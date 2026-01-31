package zio.blocks.docs

/** Table column alignment in GFM tables. */
sealed trait Alignment extends Product with Serializable

object Alignment {

  /** Left-aligned column (:---). */
  case object Left extends Alignment

  /** Right-aligned column (---:). */
  case object Right extends Alignment

  /** Center-aligned column (:---:). */
  case object Center extends Alignment

  /** No explicit alignment (---). */
  case object None extends Alignment
}
