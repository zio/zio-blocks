/*
 * Copyright 2024-2026 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zio.blocks.html

import zio.blocks.chunk.{Chunk, ChunkBuilder}

/**
 * Typeclass for converting values to `DomModifier` for use in element
 * construction.
 */
trait ToModifier[-A] {

  /**
   * Converts the value to a [[DomModifier]] that adds an attribute, a child or
   * several of them to an element.
   */
  def toModifier(a: A): DomModifier
}

/**
 * Instances of [[ToModifier]] for attributes, DOM nodes, text-like values and
 * collections of them.
 */
object ToModifier {

  /** Summons the implicit [[ToModifier]] instance for `A`. */
  def apply[A](implicit ev: ToModifier[A]): ToModifier[A] = ev

  /**
   * Convenience to construct a DomModifier from a value with a ToModifier
   * instance.
   */
  def mod[A](a: A)(implicit ev: ToModifier[A]): DomModifier = ev.toModifier(a)

  /**
   * Adds the attribute to the element. A boolean attribute that is switched off
   * adds nothing.
   */
  implicit val attrToModifier: ToModifier[Dom.Attribute] = new ToModifier[Dom.Attribute] {
    def toModifier(a: Dom.Attribute): DomModifier = a match {
      case Dom.Attribute.BooleanAttribute(_, enabled) if !enabled => DomModifier.AddChildren(Chunk.empty)
      case _                                                      => DomModifier.AddAttr(a)
    }
  }

  /** Adds the key-value attribute to the element. */
  implicit val keyValueToModifier: ToModifier[Dom.Attribute.KeyValue] = new ToModifier[Dom.Attribute.KeyValue] {
    def toModifier(a: Dom.Attribute.KeyValue): DomModifier = DomModifier.AddAttr(a)
  }

  /** Adds the append-value attribute to the element. */
  implicit val appendValueToModifier: ToModifier[Dom.Attribute.AppendValue] =
    new ToModifier[Dom.Attribute.AppendValue] {
      def toModifier(a: Dom.Attribute.AppendValue): DomModifier = DomModifier.AddAttr(a)
    }

  /**
   * Adds the boolean attribute when it is enabled, and nothing when it is
   * disabled.
   */
  implicit val booleanAttrToModifier: ToModifier[Dom.Attribute.BooleanAttribute] =
    new ToModifier[Dom.Attribute.BooleanAttribute] {
      def toModifier(a: Dom.Attribute.BooleanAttribute): DomModifier =
        if (a.enabled) DomModifier.AddAttr(a)
        else DomModifier.AddChildren(Chunk.empty)
    }

  /** Adds the [[Dom]] node as a child. */
  implicit val domToModifier: ToModifier[Dom] = new ToModifier[Dom] {
    def toModifier(a: Dom): DomModifier = DomModifier.AddChild(a)
  }

  /** Adds the element as a child. */
  implicit val elementToModifier: ToModifier[Dom.Element] = new ToModifier[Dom.Element] {
    def toModifier(a: Dom.Element): DomModifier = DomModifier.AddChild(a)
  }

  /** Adds the text node as a child. */
  implicit val textToModifier: ToModifier[Dom.Text] = new ToModifier[Dom.Text] {
    def toModifier(a: Dom.Text): DomModifier = DomModifier.AddChild(a)
  }

  /**
   * Adds a text child holding the `String`, which escapes it when rendered,
   * except as a child of a `script` or `style` element, which writes text as
   * is.
   */
  implicit val stringToModifier: ToModifier[String] = new ToModifier[String] {
    def toModifier(a: String): DomModifier = DomModifier.AddChild(Dom.Text(a))
  }

  /** Adds a text child holding the decimal form of the `Int`. */
  implicit val intToModifier: ToModifier[Int] = new ToModifier[Int] {
    def toModifier(a: Int): DomModifier = DomModifier.AddChild(Dom.Text(a.toString))
  }

  /** Adds a text child holding the decimal form of the `Long`. */
  implicit val longToModifier: ToModifier[Long] = new ToModifier[Long] {
    def toModifier(a: Long): DomModifier = DomModifier.AddChild(Dom.Text(a.toString))
  }

  /** Adds a text child holding the `Double#toString` form of the value. */
  implicit val doubleToModifier: ToModifier[Double] = new ToModifier[Double] {
    def toModifier(a: Double): DomModifier = DomModifier.AddChild(Dom.Text(a.toString))
  }

  /** Adds a text child holding the `Float#toString` form of the value. */
  implicit val floatToModifier: ToModifier[Float] = new ToModifier[Float] {
    def toModifier(a: Float): DomModifier = DomModifier.AddChild(Dom.Text(a.toString))
  }

  /** Adds a text child holding `true` or `false`. */
  implicit val booleanToModifier: ToModifier[Boolean] = new ToModifier[Boolean] {
    def toModifier(a: Boolean): DomModifier = DomModifier.AddChild(Dom.Text(a.toString))
  }

  /** Adds a text child holding the character. */
  implicit val charToModifier: ToModifier[Char] = new ToModifier[Char] {
    def toModifier(a: Char): DomModifier = DomModifier.AddChild(Dom.Text(a.toString))
  }

  /**
   * Adds a text child holding the source text of the [[Js]] value, which
   * escapes it when rendered, except as a child of a `script` or `style`
   * element, which writes text as is.
   */
  implicit val jsToModifier: ToModifier[Js] = new ToModifier[Js] {
    def toModifier(a: Js): DomModifier = DomModifier.AddChild(Dom.Text(a.value))
  }

  /**
   * Adds a text child holding the `render` text of the [[Css]] value, which
   * escapes it when rendered, except as a child of a `script` or `style`
   * element, which writes text as is.
   */
  implicit val cssToModifier: ToModifier[Css] = new ToModifier[Css] {
    def toModifier(a: Css): DomModifier = DomModifier.AddChild(Dom.Text(a.render))
  }

  /** Adds a text child holding the decimal form of the `Byte`. */
  implicit val byteToModifier: ToModifier[Byte] = new ToModifier[Byte] {
    def toModifier(a: Byte): DomModifier = DomModifier.AddChild(Dom.Text(a.toString))
  }

  /** Adds a text child holding the decimal form of the `Short`. */
  implicit val shortToModifier: ToModifier[Short] = new ToModifier[Short] {
    def toModifier(a: Short): DomModifier = DomModifier.AddChild(Dom.Text(a.toString))
  }

  /**
   * Applies the instance for `A` to the wrapped value of a `Some`, and adds
   * nothing for `None`.
   */
  implicit def optionToModifier[A](implicit ev: ToModifier[A]): ToModifier[Option[A]] =
    new ToModifier[Option[A]] {
      def toModifier(a: Option[A]): DomModifier = a match {
        case Some(v) => ev.toModifier(v)
        case None    => DomModifier.AddChildren(Chunk.empty)
      }
    }

  /**
   * Applies the instance for `A` to each element in order. An empty chunk adds
   * nothing.
   */
  implicit def chunkToModifier[A](implicit ev: ToModifier[A]): ToModifier[Chunk[A]] =
    new ToModifier[Chunk[A]] {
      def toModifier(a: Chunk[A]): DomModifier =
        if (a.isEmpty) DomModifier.AddChildren(Chunk.empty)
        else if (a.length == 1) ev.toModifier(a(0))
        else buildFromCollection(a.iterator, a.length, ev)
    }

  /**
   * Applies the instance for `A` to each element in order. An empty array adds
   * nothing.
   */
  implicit def arrayToModifier[A](implicit ev: ToModifier[A]): ToModifier[Array[A]] =
    new ToModifier[Array[A]] {
      def toModifier(a: Array[A]): DomModifier =
        if (a.isEmpty) DomModifier.AddChildren(Chunk.empty)
        else if (a.length == 1) ev.toModifier(a(0))
        else buildFromCollection(a.iterator, a.length, ev)
    }

  /**
   * Applies the instance for `A` to each element in order. An empty sequence
   * adds nothing.
   */
  implicit def seqToModifier[A](implicit ev: ToModifier[A]): ToModifier[Seq[A]] =
    new ToModifier[Seq[A]] {
      def toModifier(a: Seq[A]): DomModifier =
        if (a.isEmpty) DomModifier.AddChildren(Chunk.empty)
        else if (a.length == 1) ev.toModifier(a(0))
        else buildFromCollection(a.iterator, a.length, ev)
    }

  /**
   * Applies the instance for `A` to each element in order. An empty iterable
   * adds nothing.
   */
  implicit def iterableToModifier[A](implicit ev: ToModifier[A]): ToModifier[Iterable[A]] =
    new ToModifier[Iterable[A]] {
      def toModifier(a: Iterable[A]): DomModifier = {
        val size = a.knownSize
        if (size == 0) DomModifier.AddChildren(Chunk.empty)
        else if (size == 1) ev.toModifier(a.head)
        else buildFromCollection(a.iterator, size, ev)
      }
    }

  private def buildFromCollection[A](iter: Iterator[A], sizeHint: Int, ev: ToModifier[A]): DomModifier = {
    val effectBuilder = Chunk.newBuilder[DomModifier]
    if (sizeHint > 0) effectBuilder.sizeHint(sizeHint)
    while (iter.hasNext) {
      effectBuilder += ev.toModifier(iter.next())
    }
    val result = effectBuilder.result()
    if (result.length == 1) result(0)
    else DomModifier.AddEffects(result)
  }

  /**
   * Applies a sequence of modifiers to an element in one pass. Used by
   * `Dom.Element#whenSome`.
   */
  private[html] def buildFromEffects(element: Dom.Element, effects: Seq[DomModifier]): Dom.Element = {
    val attrBuilder  = Chunk.newBuilder[Dom.Attribute]
    val childBuilder = Chunk.newBuilder[Dom]
    attrBuilder ++= element.attributes
    childBuilder ++= element.children

    var i = 0
    while (i < effects.length) {
      applyEffect(effects(i), attrBuilder, childBuilder)
      i += 1
    }

    element.withAttributes(attrBuilder.result()).withChildren(childBuilder.result())
  }

  /**
   * Applies a first effect plus remaining effects to an element, avoiding the
   * `+:` allocation of prepending to a Seq.
   */
  private[html] def buildFromEffects(
    element: Dom.Element,
    first: DomModifier,
    rest: Seq[DomModifier]
  ): Dom.Element = {
    val attrBuilder  = Chunk.newBuilder[Dom.Attribute]
    val childBuilder = Chunk.newBuilder[Dom]
    attrBuilder ++= element.attributes
    childBuilder ++= element.children

    applyEffect(first, attrBuilder, childBuilder)

    var i = 0
    while (i < rest.length) {
      applyEffect(rest(i), attrBuilder, childBuilder)
      i += 1
    }

    element.withAttributes(attrBuilder.result()).withChildren(childBuilder.result())
  }

  private def applyEffect(
    effect: DomModifier,
    attrBuilder: ChunkBuilder[Dom.Attribute],
    childBuilder: ChunkBuilder[Dom]
  ): Unit =
    effect match {
      case DomModifier.AddAttr(a)       => attrBuilder += a
      case DomModifier.AddChild(c)      => childBuilder += c
      case DomModifier.AddChildren(cs)  => childBuilder ++= cs
      case DomModifier.AddEffects(effs) =>
        var k = 0
        while (k < effs.length) {
          applyEffect(effs(k), attrBuilder, childBuilder)
          k += 1
        }
    }
}
