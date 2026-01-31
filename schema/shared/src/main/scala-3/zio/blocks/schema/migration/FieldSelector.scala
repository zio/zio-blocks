/*
 * Copyright 2024 John A. De Goes and the ZIO Contributors
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

package zio.blocks.schema.migration

import zio.blocks.schema.DynamicOptic

/**
 * A type-safe field selector that captures the field name as a singleton string type.
 *
 * This enables compile-time validation of field names in migrations.
 *
 * @tparam S The source/record type containing the field
 * @tparam F The field's value type
 * @tparam Name The field name as a singleton string type (e.g., "name")
 *
 * Example:
 * {{{
 * case class Person(name: String, age: Int)
 *
 * // Created via macro:
 * val nameSelector: FieldSelector[Person, String, "name"] = select[Person](_.name)
 * }}}
 */
final class FieldSelector[S, F, Name <: String](
  val name: Name,
  val optic: DynamicOptic
) {

  /**
   * The runtime field name string.
   */
  def fieldName: String = name

  /**
   * Navigate to a nested field, returning a selector for the nested path.
   */
  def /[G, NestedName <: String](nested: FieldSelector[F, G, NestedName]): FieldSelector[S, G, Name] =
    new FieldSelector[S, G, Name](name, optic(nested.optic))

  override def toString: String = s"FieldSelector($name, $optic)"

  override def equals(obj: Any): Boolean = obj match {
    case that: FieldSelector[?, ?, ?] => this.name == that.name && this.optic == that.optic
    case _                            => false
  }

  override def hashCode: Int = (name, optic).hashCode
}

object FieldSelector {

  /**
   * Create a FieldSelector with explicit name (for internal/test use).
   */
  def apply[S, F, Name <: String](name: Name, optic: DynamicOptic): FieldSelector[S, F, Name] =
    new FieldSelector[S, F, Name](name, optic)

  /**
   * Create a FieldSelector from just a name (optic defaults to single field).
   */
  def fromName[S, F, Name <: String](name: Name): FieldSelector[S, F, Name] =
    new FieldSelector[S, F, Name](name, DynamicOptic(Vector(DynamicOptic.Node.Field(name))))

  /**
   * Extract the singleton name type from a FieldSelector.
   */
  type NameOf[Sel] = Sel match {
    case FieldSelector[?, ?, name] => name
  }

  /**
   * Extract the source type from a FieldSelector.
   */
  type SourceOf[Sel] = Sel match {
    case FieldSelector[s, ?, ?] => s
  }

  /**
   * Extract the field type from a FieldSelector.
   */
  type FieldTypeOf[Sel] = Sel match {
    case FieldSelector[?, f, ?] => f
  }
}

/**
 * A path selector for nested field access.
 *
 * This tracks the full path from root to a nested field, enabling
 * nested migrations like `atField(_.address.street)`.
 *
 * @tparam S The root source type
 * @tparam F The final field type at the end of the path
 * @tparam Path A tuple of field names representing the path
 */
final class PathSelector[S, F, Path <: Tuple](
  val path: Path,
  val optic: DynamicOptic
) {

  /**
   * Get the path as a sequence of strings.
   */
  def pathStrings: Seq[String] = {
    def toSeq(t: Tuple): Seq[String] = t match {
      case EmptyTuple      => Seq.empty
      case (h: String) *: tail => h +: toSeq(tail)
      case _               => Seq.empty
    }
    toSeq(path)
  }

  override def toString: String = s"PathSelector(${pathStrings.mkString(".")}, $optic)"
}

object PathSelector {

  /**
   * Create a PathSelector from a single field.
   */
  def single[S, F, Name <: String](selector: FieldSelector[S, F, Name]): PathSelector[S, F, Name *: EmptyTuple] =
    new PathSelector[S, F, Name *: EmptyTuple](
      (selector.name *: EmptyTuple).asInstanceOf[Name *: EmptyTuple],
      selector.optic
    )

  /**
   * Create a PathSelector for nested access.
   */
  def nested[S, M, F, P <: Tuple, Name <: String](
    parent: PathSelector[S, M, P],
    child: FieldSelector[M, F, Name]
  ): PathSelector[S, F, Tuple.Concat[P, Name *: EmptyTuple]] =
    new PathSelector[S, F, Tuple.Concat[P, Name *: EmptyTuple]](
      (parent.path ++ (child.name *: EmptyTuple)).asInstanceOf[Tuple.Concat[P, Name *: EmptyTuple]],
      parent.optic(child.optic)
    )
}
