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

/**
 * Type-level operations on field sets represented as tuples of singleton string
 * types.
 *
 * This enables compile-time tracking of which fields have been handled in
 * migrations, ensuring all source fields are addressed and all target fields
 * are provided.
 *
 * Example:
 * {{{
 * type PersonFields = ("name", "age", "email")
 * type Remaining = FieldSet.Remove[PersonFields, "name"]  // = ("age", "email")
 * type HasAge = FieldSet.Contains[PersonFields, "age"]    // = true
 * }}}
 */
object FieldSet {

  /**
   * Remove a field name from a tuple of field names. Returns a new tuple with
   * the specified name removed.
   */
  type Remove[Fields <: Tuple, Name <: String] <: Tuple = Fields match {
    case EmptyTuple   => EmptyTuple
    case Name *: tail => tail
    case head *: tail => head *: Remove[tail, Name]
  }

  /**
   * Check if a tuple of field names contains a specific name. Returns true if
   * the name is found, false otherwise.
   */
  type Contains[Fields <: Tuple, Name <: String] <: Boolean = Fields match {
    case EmptyTuple => false
    case Name *: _  => true
    case _ *: tail  => Contains[tail, Name]
  }

  /**
   * Check if a tuple is empty.
   */
  type IsEmpty[Fields <: Tuple] <: Boolean = Fields match {
    case EmptyTuple => true
    case _          => false
  }

  /**
   * Add a field name to a tuple of field names.
   */
  type Add[Fields <: Tuple, Name <: String] = Name *: Fields

  /**
   * Compute the intersection of two field sets.
   */
  type Intersect[A <: Tuple, B <: Tuple] <: Tuple = A match {
    case EmptyTuple   => EmptyTuple
    case head *: tail =>
      Contains[B, head] match {
        case true  => head *: Intersect[tail, B]
        case false => Intersect[tail, B]
      }
  }

  /**
   * Compute the difference of two field sets (A - B).
   */
  type Diff[A <: Tuple, B <: Tuple] <: Tuple = A match {
    case EmptyTuple   => EmptyTuple
    case head *: tail =>
      Contains[B, head] match {
        case true  => Diff[tail, B]
        case false => head *: Diff[tail, B]
      }
  }

  /**
   * Concatenate two tuples.
   */
  type Concat[A <: Tuple, B <: Tuple] <: Tuple = A match {
    case EmptyTuple   => B
    case head *: tail => head *: Concat[tail, B]
  }

  /**
   * Evidence that a field name is contained in a tuple of field names. Used to
   * ensure compile-time validation that a field exists.
   */
  sealed trait ContainsEvidence[Fields <: Tuple, Name <: String]

  object ContainsEvidence {
    given containsHead[Name <: String, Tail <: Tuple]: ContainsEvidence[Name *: Tail, Name] =
      new ContainsEvidence[Name *: Tail, Name] {}

    given containsTail[Head <: String, Tail <: Tuple, Name <: String](using
      ev: ContainsEvidence[Tail, Name]
    ): ContainsEvidence[Head *: Tail, Name] =
      new ContainsEvidence[Head *: Tail, Name] {}
  }

  /**
   * Evidence that a tuple is empty. Used to ensure all fields have been
   * handled.
   */
  sealed trait EmptyEvidence[Fields <: Tuple]

  object EmptyEvidence {
    given emptyTuple: EmptyEvidence[EmptyTuple] = new EmptyEvidence[EmptyTuple] {}
  }

  /**
   * Type class to remove a field from a tuple at the type level. Provides the
   * resulting type after removal.
   */
  sealed trait RemoveField[Fields <: Tuple, Name <: String] {
    type Out <: Tuple
  }

  object RemoveField {
    type Aux[Fields <: Tuple, Name <: String, O <: Tuple] = RemoveField[Fields, Name] { type Out = O }

    given removeHead[Name <: String, Tail <: Tuple]: Aux[Name *: Tail, Name, Tail] =
      new RemoveField[Name *: Tail, Name] { type Out = Tail }

    given removeTail[Head <: String, Tail <: Tuple, Name <: String](using
      ev: RemoveField[Tail, Name]
    ): Aux[Head *: Tail, Name, Head *: ev.Out] =
      new RemoveField[Head *: Tail, Name] { type Out = Head *: ev.Out }
  }
}
