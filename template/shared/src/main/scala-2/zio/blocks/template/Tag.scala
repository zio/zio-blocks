package zio.blocks.template

import zio.blocks.chunk.Chunk
import scala.language.implicitConversions

class Tag(val name: String) {

  def apply(): Dom.Element =
    Dom.Element.Generic(name, Chunk.empty, Chunk.empty)

  def apply(modifier: Modifier, modifiers: Modifier*): Dom.Element = {
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
