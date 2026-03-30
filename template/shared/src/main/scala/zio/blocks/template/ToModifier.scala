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

  implicit val intToModifier: ToModifier[Int] = new ToModifier[Int] {
    def toModifier(a: Int): ModifierEffect = ModifierEffect.AddChild(Dom.Text(a.toString))
  }

  implicit val longToModifier: ToModifier[Long] = new ToModifier[Long] {
    def toModifier(a: Long): ModifierEffect = ModifierEffect.AddChild(Dom.Text(a.toString))
  }

  implicit val doubleToModifier: ToModifier[Double] = new ToModifier[Double] {
    def toModifier(a: Double): ModifierEffect = ModifierEffect.AddChild(Dom.Text(a.toString))
  }

  implicit val floatToModifier: ToModifier[Float] = new ToModifier[Float] {
    def toModifier(a: Float): ModifierEffect = ModifierEffect.AddChild(Dom.Text(a.toString))
  }

  implicit val booleanToModifier: ToModifier[Boolean] = new ToModifier[Boolean] {
    def toModifier(a: Boolean): ModifierEffect = ModifierEffect.AddChild(Dom.Text(a.toString))
  }

  implicit val charToModifier: ToModifier[Char] = new ToModifier[Char] {
    def toModifier(a: Char): ModifierEffect = ModifierEffect.AddChild(Dom.Text(a.toString))
  }

  implicit val byteToModifier: ToModifier[Byte] = new ToModifier[Byte] {
    def toModifier(a: Byte): ModifierEffect = ModifierEffect.AddChild(Dom.Text(a.toString))
  }

  implicit val shortToModifier: ToModifier[Short] = new ToModifier[Short] {
    def toModifier(a: Short): ModifierEffect = ModifierEffect.AddChild(Dom.Text(a.toString))
  }

  implicit def optionToModifier[A](implicit ev: ToModifier[A]): ToModifier[Option[A]] =
    new ToModifier[Option[A]] {
      def toModifier(a: Option[A]): ModifierEffect = a match {
        case Some(v) => ev.toModifier(v)
        case None    => ModifierEffect.AddChildren(Chunk.empty)
      }
    }

  implicit def chunkToModifier[A](implicit ev: ToModifier[A]): ToModifier[Chunk[A]] =
    new ToModifier[Chunk[A]] {
      def toModifier(a: Chunk[A]): ModifierEffect =
        if (a.isEmpty) ModifierEffect.AddChildren(Chunk.empty)
        else if (a.length == 1) ev.toModifier(a(0))
        else buildFromCollection(a.iterator, a.length, ev)
    }

  implicit def arrayToModifier[A](implicit ev: ToModifier[A]): ToModifier[Array[A]] =
    new ToModifier[Array[A]] {
      def toModifier(a: Array[A]): ModifierEffect =
        if (a.isEmpty) ModifierEffect.AddChildren(Chunk.empty)
        else if (a.length == 1) ev.toModifier(a(0))
        else buildFromCollection(a.iterator, a.length, ev)
    }

  implicit def seqToModifier[A](implicit ev: ToModifier[A]): ToModifier[Seq[A]] =
    new ToModifier[Seq[A]] {
      def toModifier(a: Seq[A]): ModifierEffect =
        if (a.isEmpty) ModifierEffect.AddChildren(Chunk.empty)
        else if (a.length == 1) ev.toModifier(a(0))
        else buildFromCollection(a.iterator, a.length, ev)
    }

  implicit def iterableToModifier[A](implicit ev: ToModifier[A]): ToModifier[Iterable[A]] =
    new ToModifier[Iterable[A]] {
      def toModifier(a: Iterable[A]): ModifierEffect = {
        val size = a.knownSize
        if (size == 0) ModifierEffect.AddChildren(Chunk.empty)
        else if (size == 1) ev.toModifier(a.head)
        else buildFromCollection(a.iterator, size, ev)
      }
    }

  private def buildFromCollection[A](iter: Iterator[A], sizeHint: Int, ev: ToModifier[A]): ModifierEffect = {
    val childBuilder = Chunk.newBuilder[Dom]
    if (sizeHint > 0) childBuilder.sizeHint(sizeHint)
    while (iter.hasNext) {
      ev.toModifier(iter.next()) match {
        case ModifierEffect.AddChild(c)     => childBuilder += c
        case ModifierEffect.AddChildren(cs) => childBuilder ++= cs
        case ModifierEffect.AddAttr(_)      => () // attrs in collections are skipped (unusual case)
      }
    }
    ModifierEffect.AddChildren(childBuilder.result())
  }

  /**
   * Applies a sequence of ModifierEffects to an element. Used by macros as
   * runtime helper.
   */
  private[template] def buildFromEffects(element: Dom.Element, effects: Seq[ModifierEffect]): Dom.Element = {
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

  /**
   * Applies a first effect plus remaining effects to an element, avoiding the
   * `+:` allocation of prepending to a Seq.
   */
  private[template] def buildFromEffects(
    element: Dom.Element,
    first: ModifierEffect,
    rest: Seq[ModifierEffect]
  ): Dom.Element = {
    val attrBuilder  = Chunk.newBuilder[Dom.Attribute]
    val childBuilder = Chunk.newBuilder[Dom]
    attrBuilder ++= element.attributes
    childBuilder ++= element.children

    first match {
      case ModifierEffect.AddAttr(a)      => attrBuilder += a
      case ModifierEffect.AddChild(c)     => childBuilder += c
      case ModifierEffect.AddChildren(cs) => childBuilder ++= cs
    }

    var i = 0
    while (i < rest.length) {
      rest(i) match {
        case ModifierEffect.AddAttr(a)      => attrBuilder += a
        case ModifierEffect.AddChild(c)     => childBuilder += c
        case ModifierEffect.AddChildren(cs) => childBuilder ++= cs
      }
      i += 1
    }

    element.withAttributes(attrBuilder.result()).withChildren(childBuilder.result())
  }
}
