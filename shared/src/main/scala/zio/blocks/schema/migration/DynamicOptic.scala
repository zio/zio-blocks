package zio.blocks.schema.migration

/** Temporary path representation.
    * In the final implementation this will be replaced by
    * zio.blocks.schema.patch.DynamicOptic from PR #671.
  */
final case class DynamicOptic(segments: Vector[String]) {
  def toList: List[String] = segments.toList
}
object DynamicOptic {
  val root: DynamicOptic = DynamicOptic(Vector.empty)
  // Macro will generate this from _.field selectors
  def fromSelector[A](f: A => Any): DynamicOptic = root
}
