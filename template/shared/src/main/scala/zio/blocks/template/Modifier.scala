package zio.blocks.template

import scala.language.implicitConversions

trait Modifier {
  def applyTo(element: Dom.Element): Dom.Element
}

object Modifier {

  implicit def domToModifier(dom: Dom): Modifier = new Modifier {
    def applyTo(element: Dom.Element): Dom.Element =
      element.copy(children = element.children :+ dom)
  }

  implicit def attributeToModifier(attr: Dom.Attribute): Modifier = new Modifier {
    def applyTo(element: Dom.Element): Dom.Element =
      element.copy(attributes = element.attributes :+ attr)
  }

  implicit def stringToModifier(s: String): Modifier = new Modifier {
    def applyTo(element: Dom.Element): Dom.Element =
      element.copy(children = element.children :+ Dom.Text(s))
  }

  implicit def optionModifierToModifier(opt: Option[Modifier]): Modifier = new Modifier {
    def applyTo(element: Dom.Element): Dom.Element =
      opt match {
        case Some(m) => m.applyTo(element)
        case None    => element
      }
  }
}
