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

import scala.language.implicitConversions
import zio.blocks.chunk.Chunk

/**
 * A change to apply to an element: add an attribute, add a child, add several
 * children, or apply several modifiers in order. You rarely build one directly:
 * an implicit conversion turns any value with a [[ToModifier]] instance
 * (attributes, strings, nodes, numbers, `Option` values and collections) into a
 * `DomModifier` when you pass it to an element, as in
 * `div(id := "cart", "Tea")`.
 *
 * {{{
 * DomModifier.AddAttr(id := "cart").applyTo(div).render
 * // <div id="cart"></div>
 * }}}
 *
 * @see
 *   [[https://zioblocks.com/reference/html/#children Children]]
 */
sealed trait DomModifier extends Product with Serializable {

  /**
   * Returns a copy of `element` with this modifier applied: an attribute is
   * appended to the attributes and nodes to the children. Applying a list of
   * modifiers through `Dom.Element#apply` builds the result in one pass instead
   * of one copy per modifier.
   */
  def applyTo(element: Dom.Element): Dom.Element = this match {
    case DomModifier.AddAttr(attr)    => element.withAttributes(element.attributes :+ attr)
    case DomModifier.AddChild(child)  => element.withChildren(element.children :+ child)
    case DomModifier.AddChildren(cs)  => element.withChildren(element.children ++ cs)
    case DomModifier.AddEffects(effs) =>
      var elem = element
      var i    = 0
      while (i < effs.length) {
        elem = effs(i).applyTo(elem)
        i += 1
      }
      elem
  }
}

/** The modifier cases. */
object DomModifier {

  /** Appends `attr` to the element's attributes. */
  final case class AddAttr(attr: Dom.Attribute) extends DomModifier

  /** Appends `child` to the element's children. */
  final case class AddChild(child: Dom) extends DomModifier

  /**
   * Appends `children` to the element's children, in order. An empty chunk adds
   * nothing, which is how `None` and disabled boolean attributes are encoded.
   */
  final case class AddChildren(children: Chunk[Dom]) extends DomModifier

  /**
   * Applies `effects` in order; a collection of modifiers becomes one of these.
   */
  final case class AddEffects(effects: Chunk[DomModifier]) extends DomModifier
}

/**
 * Provides the implicit conversion from any value with a [[ToModifier]]
 * instance to [[DomModifier]]. The `zio.blocks.html` package object mixes it
 * in.
 */
trait DomModifierConversions {

  /** Converts `a` with its [[ToModifier]] instance. */
  implicit def toDomModifier[A](a: A)(implicit ev: ToModifier[A]): DomModifier = ev.toModifier(a)
}
