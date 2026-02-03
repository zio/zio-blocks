package zio.blocks.schema

trait Reflectable[A] {
  def modifiers: Seq[Modifier]

  def doc: Doc
}
