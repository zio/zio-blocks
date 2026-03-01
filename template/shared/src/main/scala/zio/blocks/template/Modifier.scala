package zio.blocks.template

import scala.language.implicitConversions

trait Modifier {
  def applyTo(element: Dom.Element): Dom.Element
}

object Modifier {

  implicit def domToModifier(dom: Dom): Modifier = new Modifier {
    def applyTo(element: Dom.Element): Dom.Element =
      element.withChildren(element.children :+ dom)
  }

  implicit def attributeToModifier(attr: Dom.Attribute): Modifier = new Modifier {
    def applyTo(element: Dom.Element): Dom.Element =
      element.withAttributes(element.attributes :+ attr)
  }

  implicit def stringToModifier(s: String): Modifier = new Modifier {
    def applyTo(element: Dom.Element): Dom.Element =
      element.withChildren(element.children :+ Dom.Text(s))
  }

  implicit def optionModifierToModifier(opt: Option[Modifier]): Modifier = new Modifier {
    def applyTo(element: Dom.Element): Dom.Element =
      opt match {
        case Some(m) => m.applyTo(element)
        case None    => element
      }
  }

  implicit def iterableModifierToModifier(modifiers: Iterable[Modifier]): Modifier = new Modifier {
    def applyTo(element: Dom.Element): Dom.Element = {
      var elem = element
      val iter = modifiers.iterator
      while (iter.hasNext) {
        elem = iter.next().applyTo(elem)
      }
      elem
    }
  }
}
