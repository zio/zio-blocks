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

package zio.blocks.typeid

/**
 * Represents a path to a term value, used for singleton types.
 *
 * For example, for a singleton type `MyObject.Inner.value.type`:
 * {{{
 * TermPath(List(
 *   TermPath.Package("com"),
 *   TermPath.Package("example"),
 *   TermPath.Term("MyObject"),
 *   TermPath.Term("Inner"),
 *   TermPath.Term("value")
 * ))
 * }}}
 */
final case class TermPath(segments: List[TermPath.Segment]) {

  /**
   * Returns the path as a dot-separated string.
   */
  def asString: String = segments
    .foldLeft(new java.lang.StringBuilder) { (sb, s) =>
      if (sb.length > 0) sb.append('.')
      sb.append(s.name)
    }
    .toString

  /**
   * Appends a term segment to this path.
   */
  def /(name: String): TermPath = new TermPath(segments :+ new TermPath.Term(name))

  /**
   * Returns true if this is an empty path.
   */
  def isEmpty: Boolean = segments.isEmpty
}

object TermPath {

  /**
   * A segment in a term path.
   */
  sealed trait Segment {
    def name: String
  }

  /**
   * A package segment.
   */
  final case class Package(name: String) extends Segment

  /**
   * A term segment (object, value, etc.).
   */
  final case class Term(name: String) extends Segment

  /**
   * An empty term path.
   */
  val Empty: TermPath = TermPath(Nil)

  /**
   * Creates a TermPath from an Owner and a term name.
   */
  def fromOwner(owner: Owner, termName: String): TermPath = {
    val ownerSegments = owner.segments.map {
      case Owner.Package(name) => Package(name)
      case Owner.Term(name)    => Term(name)
      case Owner.Type(name)    => Term(name) // Types are accessed like terms in paths
    }
    TermPath(ownerSegments :+ Term(termName))
  }
}
