/*
 * Copyright 2023 ZIO Blocks Maintainers
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

package zio.blocks.schema

import scala.collection.immutable.ArraySeq

sealed trait Doc {
  def +(that: Doc): Doc = Doc.Concat(this.flatten ++ that.flatten)

  def flatten: IndexedSeq[Doc.Leaf]

  override def hashCode: Int = flatten.hashCode

  override def equals(that: Any): Boolean = that match {
    case that: Doc => flatten.equals(that.flatten)
    case _         => false
  }
}

object Doc {
  sealed trait Leaf extends Doc

  case object Empty extends Leaf {
    def flatten: IndexedSeq[Leaf] = IndexedSeq.empty
  }

  case class Text(value: String) extends Leaf {
    lazy val flatten: IndexedSeq[Leaf] = ArraySeq(this)

    override def hashCode: Int = value.hashCode

    override def equals(that: Any): Boolean = that match {
      case that: Text => value.equals(that.value)
      case that: Doc  => flatten.equals(that.flatten)
      case _          => false
    }
  }

  case class Concat(flatten: IndexedSeq[Leaf]) extends Doc

  object Concat {
    def apply(docs: Doc*): Concat = new Concat(docs.toIndexedSeq.flatMap(_.flatten))
  }

  def apply(value: String): Doc = new Text(value)
}
