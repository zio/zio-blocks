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

package zio.blocks.schema.migration

/**
 * Pure-data path representation for the algebraic migration system.
 * Supports record fields, variant cases, sequence elements, and map keys/values.
 * Used to target migration actions and to compute structural reverses (e.g. Rename
 * reverse targets the new name and renames back to the old).
 */
sealed trait DynamicOptic {
  def append(next: DynamicOptic): DynamicOptic
}

object DynamicOptic {
  final case class Field(name: String, next: Option[DynamicOptic]) extends DynamicOptic {
    def append(n: DynamicOptic): DynamicOptic =
      Field(name, next.map(_.append(n)).orElse(Some(n)))
  }
  final case class Case(name: String, next: Option[DynamicOptic]) extends DynamicOptic {
    def append(n: DynamicOptic): DynamicOptic =
      Case(name, next.map(_.append(n)).orElse(Some(n)))
  }
  final case class Element(next: Option[DynamicOptic]) extends DynamicOptic {
    def append(n: DynamicOptic): DynamicOptic =
      Element(next.map(_.append(n)).orElse(Some(n)))
  }
  final case class Key(next: Option[DynamicOptic]) extends DynamicOptic {
    def append(n: DynamicOptic): DynamicOptic =
      Key(next.map(_.append(n)).orElse(Some(n)))
  }
  final case class Value(next: Option[DynamicOptic]) extends DynamicOptic {
    def append(n: DynamicOptic): DynamicOptic =
      Value(next.map(_.append(n)).orElse(Some(n)))
  }

  /** Extracts the terminal field name for Rename reversals. */
  def terminalName(optic: DynamicOptic): String = optic match {
    case Field(name, None)        => name
    case Field(_, Some(n))        => terminalName(n)
    case _                        => throw new IllegalArgumentException("Terminal optic must be a Field")
  }

  /** Replaces the terminal field name to compute the reverse target path. */
  def replaceTerminal(optic: DynamicOptic, newName: String): DynamicOptic = optic match {
    case Field(_, None)          => Field(newName, None)
    case Field(name, Some(next))  => Field(name, Some(replaceTerminal(next, newName)))
    case _                       => optic
  }
}
