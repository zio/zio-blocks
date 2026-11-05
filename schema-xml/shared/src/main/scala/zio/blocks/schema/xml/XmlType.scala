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

package zio.blocks.schema.xml

import zio.blocks.chunk.Chunk

/**
 * A sealed trait representing the type of XML node.
 *
 * Each case provides two type members:
 *   - `Type`: The corresponding [[Xml]] subtype (e.g., `Xml.Element`)
 *   - `Unwrap`: The underlying Scala type of the value
 *
 * An `XmlType` is also a predicate on [[Xml]] nodes, so it can be passed to
 * `XmlSelection#filter`. Its `toString` is `<function1>`, not the case name, so
 * error messages that print it (from `XmlSelection#as`, `XmlSelection#unwrap`,
 * `XmlSelection#text` and `XmlPatch` paths that reach a non-element) show
 * `<function1>`; this is a known bug.
 */
sealed trait XmlType extends (Xml => Boolean) {

  /** The corresponding [[Xml]] subtype for this XML type. */
  type Type <: Xml

  /** The underlying Scala type of the value contained in this XML type. */
  type Unwrap

  /**
   * Returns the index of this case: 0 for `Element`, 1 for `Text`, 2 for
   * `CData`, 3 for `Comment` and 4 for `ProcessingInstruction`.
   */
  def typeIndex: Int

  /** Returns true if the given XML node is of this type. */
  override def apply(xml: Xml): Boolean = xml.xmlType == this
}

/** The five cases of [[XmlType]], one per [[Xml]] case. */
object XmlType {

  /** The type of [[Xml.Element]]; unwraps to `(name, attributes, children)`. */
  case object Element extends XmlType {
    override final type Type   = Xml.Element
    override final type Unwrap = (XmlName, Chunk[(XmlName, String)], Chunk[Xml])

    /** Returns `0`. */
    val typeIndex = 0
  }

  /** The type of [[Xml.Text]]; unwraps to the text. */
  case object Text extends XmlType {
    override final type Type   = Xml.Text
    override final type Unwrap = String

    /** Returns `1`. */
    val typeIndex = 1
  }

  /** The type of [[Xml.CData]]; unwraps to the CDATA content. */
  case object CData extends XmlType {
    override final type Type   = Xml.CData
    override final type Unwrap = String

    /** Returns `2`. */
    val typeIndex = 2
  }

  /** The type of [[Xml.Comment]]; unwraps to the comment text. */
  case object Comment extends XmlType {
    override final type Type   = Xml.Comment
    override final type Unwrap = String

    /** Returns `3`. */
    val typeIndex = 3
  }

  /**
   * The type of [[Xml.ProcessingInstruction]]; unwraps to `(target, data)`.
   */
  case object ProcessingInstruction extends XmlType {
    override final type Type   = Xml.ProcessingInstruction
    override final type Unwrap = (String, String)

    /** Returns `4`. */
    val typeIndex = 4
  }
}
