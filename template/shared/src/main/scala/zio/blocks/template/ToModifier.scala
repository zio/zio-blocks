package zio.blocks.template

import scala.language.implicitConversions
import zio.blocks.chunk.Chunk
sealed trait ModifierEffect extends Product with Serializable

object ModifierEffect {
  final case class AddAttr(attr: Dom.Attribute)      extends ModifierEffect
  final case class AddChild(child: Dom)              extends ModifierEffect
  final case class AddChildren(children: Chunk[Dom]) extends ModifierEffect
}

trait ModifierEffectConversions {
  implicit def toModifierEffect[A](a: A)(implicit ev: ToModifier[A]): ModifierEffect = ev.toModifier(a)
}

trait ToModifier[-A] {
  def toModifier(a: A): ModifierEffect
}

object ToModifier {
  def apply[A](implicit ev: ToModifier[A]): ToModifier[A] = ev

  /**
   * Convenience to construct a ModifierEffect from a value with a ToModifier
   * instance.
   */
  def mod[A](a: A)(implicit ev: ToModifier[A]): ModifierEffect = ev.toModifier(a)

  implicit val attrToModifier: ToModifier[Dom.Attribute] = new ToModifier[Dom.Attribute] {
    def toModifier(a: Dom.Attribute): ModifierEffect = a match {
      case Dom.Attribute.BooleanAttribute(_, enabled) if !enabled => ModifierEffect.AddChildren(Chunk.empty)
      case _                                                      => ModifierEffect.AddAttr(a)
    }
  }

  implicit val keyValueToModifier: ToModifier[Dom.Attribute.KeyValue] = new ToModifier[Dom.Attribute.KeyValue] {
    def toModifier(a: Dom.Attribute.KeyValue): ModifierEffect = ModifierEffect.AddAttr(a)
  }

  implicit val appendValueToModifier: ToModifier[Dom.Attribute.AppendValue] =
    new ToModifier[Dom.Attribute.AppendValue] {
      def toModifier(a: Dom.Attribute.AppendValue): ModifierEffect = ModifierEffect.AddAttr(a)
    }

  implicit val booleanAttrToModifier: ToModifier[Dom.Attribute.BooleanAttribute] =
    new ToModifier[Dom.Attribute.BooleanAttribute] {
      def toModifier(a: Dom.Attribute.BooleanAttribute): ModifierEffect =
        if (a.enabled) ModifierEffect.AddAttr(a)
        else ModifierEffect.AddChildren(Chunk.empty)
    }

  implicit val domToModifier: ToModifier[Dom] = new ToModifier[Dom] {
    def toModifier(a: Dom): ModifierEffect = ModifierEffect.AddChild(a)
  }

  implicit val elementToModifier: ToModifier[Dom.Element] = new ToModifier[Dom.Element] {
    def toModifier(a: Dom.Element): ModifierEffect = ModifierEffect.AddChild(a)
  }

  implicit val textToModifier: ToModifier[Dom.Text] = new ToModifier[Dom.Text] {
    def toModifier(a: Dom.Text): ModifierEffect = ModifierEffect.AddChild(a)
  }

  implicit val stringToModifier: ToModifier[String] = new ToModifier[String] {
    def toModifier(a: String): ModifierEffect = ModifierEffect.AddChild(Dom.Text(a))
  }

  implicit def optionToModifier[A](implicit ev: ToModifier[A]): ToModifier[Option[A]] =
    new ToModifier[Option[A]] {
      def toModifier(a: Option[A]): ModifierEffect = a match {
        case Some(v) => ev.toModifier(v)
        case None    => ModifierEffect.AddChildren(Chunk.empty)
      }
    }

  implicit def iterableToModifier[A](implicit ev: ToModifier[A]): ToModifier[Iterable[A]] =
    new ToModifier[Iterable[A]] {
      def toModifier(a: Iterable[A]): ModifierEffect = {
        val childBuilder = Chunk.newBuilder[Dom]
        val attrBuilder  = Chunk.newBuilder[Dom.Attribute]
        val iter         = a.iterator
        var hasAttrs     = false
        var hasChildren  = false
        while (iter.hasNext) {
          ev.toModifier(iter.next()) match {
            case ModifierEffect.AddChild(c)     => childBuilder += c; hasChildren = true
            case ModifierEffect.AddChildren(cs) => childBuilder ++= cs; hasChildren = true
            case ModifierEffect.AddAttr(a)      => attrBuilder += a; hasAttrs = true
          }
        }
        if (hasAttrs && !hasChildren) {
          val attrs = attrBuilder.result()
          if (attrs.length == 1) ModifierEffect.AddAttr(attrs(0))
          else ModifierEffect.AddChildren(Chunk.empty)
        } else ModifierEffect.AddChildren(childBuilder.result())
      }
    }

  /**
   * Applies a sequence of ModifierEffects to an element. Used by macros as
   * runtime helper.
   */
  def buildFromEffects(element: Dom.Element, effects: Seq[ModifierEffect]): Dom.Element = {
    val attrBuilder  = Chunk.newBuilder[Dom.Attribute]
    val childBuilder = Chunk.newBuilder[Dom]
    attrBuilder ++= element.attributes
    childBuilder ++= element.children

    var i = 0
    while (i < effects.length) {
      effects(i) match {
        case ModifierEffect.AddAttr(a)      => attrBuilder += a
        case ModifierEffect.AddChild(c)     => childBuilder += c
        case ModifierEffect.AddChildren(cs) => childBuilder ++= cs
      }
      i += 1
    }

    element.withAttributes(attrBuilder.result()).withChildren(childBuilder.result())
  }
}
