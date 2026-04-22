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

package zio.blocks.template

import scala.language.implicitConversions
import zio.blocks.chunk.Chunk

/**
 * Represents a deferred modification to a [[Dom.Element]].
 *
 * `ModifierEffect` values describe additions (attributes, children, or nested
 * effects) that are collected during element construction and then applied in
 * batch by [[ToModifier.buildFromEffects]].
 *
 * Use [[ModifierEffect#applyTo]] for single-effect application to an element.
 */
sealed trait ModifierEffect extends Product with Serializable {

  /**
   * Applies this single effect to the given element, returning a new element
   * with the modification applied.
   *
   * Unlike the batched [[ToModifier.buildFromEffects]], this method creates
   * intermediate elements for each effect. Use `buildFromEffects` when applying
   * multiple effects for better performance.
   *
   * @param element
   *   the element to modify
   * @return
   *   a new element with this effect applied
   */
  def applyTo(element: Dom.Element): Dom.Element = this match {
    case ModifierEffect.AddAttr(attr)    => element.withAttributes(element.attributes :+ attr)
    case ModifierEffect.AddChild(child)  => element.withChildren(element.children :+ child)
    case ModifierEffect.AddChildren(cs)  => element.withChildren(element.children ++ cs)
    case ModifierEffect.AddEffects(effs) =>
      var elem = element
      var i    = 0
      while (i < effs.length) {
        elem = effs(i).applyTo(elem)
        i += 1
      }
      elem
  }
}

object ModifierEffect {
  final case class AddAttr(attr: Dom.Attribute)               extends ModifierEffect
  final case class AddChild(child: Dom)                       extends ModifierEffect
  final case class AddChildren(children: Chunk[Dom])          extends ModifierEffect
  final case class AddEffects(effects: Chunk[ModifierEffect]) extends ModifierEffect
}

trait ModifierEffectConversions {
  implicit def toModifierEffect[A](a: A)(implicit ev: ToModifier[A]): ModifierEffect = ev.toModifier(a)
}
