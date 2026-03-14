package zio.blocks.template

import zio.blocks.chunk.Chunk
import scala.language.implicitConversions

class Tag(val name: String) {

  def apply(): Dom.Element =
    Dom.Element.Generic(name, Chunk.empty, Chunk.empty)

  inline def apply(inline modifier: Modifier, inline modifiers: Modifier*): Dom.Element =
    ${ TagMacro.applyImpl('this, 'modifier, 'modifiers) }

  private[template] def runtimeApply(modifier: Modifier, modifiers: Modifier*): Dom.Element = {
    var elem: Dom.Element = Dom.Element.Generic(name, Chunk.empty, Chunk.empty)
    elem = modifier.applyTo(elem)
    var i = 0
    while (i < modifiers.length) {
      elem = modifiers(i).applyTo(elem)
      i += 1
    }
    elem
  }

  def toElement: Dom.Element = Dom.Element.Generic(name, Chunk.empty, Chunk.empty)
}

object Tag {
  implicit def tagToElement(tag: Tag): Dom.Element = tag.toElement
}
