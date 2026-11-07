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

package zio.blocks.codegen.ir

/**
 * Represents a Scala import statement in the IR. `ScalaEmitter.emit` removes
 * duplicate imports from a file and, when `EmitterConfig.sortImports` is true,
 * sorts them.
 */
sealed trait Import {

  /**
   * The package path of the import (e.g., "com.example").
   */
  def path: String
}

/** The four import forms: single, wildcard, rename and group. */
object Import {

  /**
   * A single-name import.
   *
   * @param path
   *   The package path (e.g., "com.example")
   * @param name
   *   The imported name (e.g., "Foo")
   *
   * @example
   *   {{{
   * import zio.blocks.codegen.ir._
   *
   * val imp = Import.SingleImport("com.example", "Foo")
   * // Represents: import com.example.Foo
   *   }}}
   */
  final case class SingleImport(path: String, name: String) extends Import

  /**
   * A wildcard import that imports all members of a package.
   *
   * @param path
   *   The package path (e.g., "com.example")
   *
   * @example
   *   {{{
   * import zio.blocks.codegen.ir._
   *
   * val imp = Import.WildcardImport("com.example")
   * // Represents: import com.example._ (Scala 2) or import com.example.* (Scala 3)
   *   }}}
   */
  final case class WildcardImport(path: String) extends Import

  /**
   * A rename import that imports a name under an alias.
   *
   * @param path
   *   The package path (e.g., "com.example")
   * @param from
   *   The original name (e.g., "Foo")
   * @param to
   *   The alias name (e.g., "Bar")
   *
   * @example
   *   {{{
   * import zio.blocks.codegen.ir._
   *
   * val imp = Import.RenameImport("com.example", "Foo", "Bar")
   * // Represents: import com.example.{Foo => Bar} (Scala 2) or import com.example.{Foo as Bar} (Scala 3)
   *   }}}
   */
  final case class RenameImport(path: String, from: String, to: String) extends Import

  /**
   * A group import that imports multiple names from a package. Give it at least
   * one name: an empty list is emitted as `import path.{}`, and sorting a
   * file's imports throws `NoSuchElementException` when one of two or more
   * imports is an empty group (a known bug).
   *
   * @param path
   *   The package path (e.g., "com.example")
   * @param names
   *   The imported names (e.g., List("Foo", "Bar"))
   *
   * @example
   *   {{{
   * import zio.blocks.codegen.ir._
   *
   * val imp = Import.GroupImport("com.example", List("Foo", "Bar"))
   * // Represents: import com.example.{Foo, Bar}
   *   }}}
   */
  final case class GroupImport(path: String, names: List[String]) extends Import
}
