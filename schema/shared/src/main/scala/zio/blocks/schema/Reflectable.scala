package zio.blocks.schema

import zio.blocks.docs.Doc

trait Reflectable[A] {
  def modifiers: Seq[Modifier]

  def doc: Doc
}
