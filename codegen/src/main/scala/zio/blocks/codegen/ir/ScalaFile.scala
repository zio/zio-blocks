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
 * Represents a complete Scala source file in the IR.
 *
 * A Scala file consists of a package declaration, optional imports, and type
 * definitions. This is the top-level IR node for code generation.
 *
 * @param packageDecl
 *   The package declaration for this file
 * @param imports
 *   The import statements (defaults to empty list)
 * @param types
 *   The type definitions in this file (defaults to empty list)
 *
 * @example
 *   {{{
 * val file = ScalaFile(
 *   PackageDecl("com.example"),
 *   imports = List(
 *     Import.SingleImport("scala.collection", "List"),
 *     Import.WildcardImport("zio")
 *   ),
 *   types = List(
 *     CaseClass("Person", List(Field("name", TypeRef.String)))
 *   )
 * )
 *   }}}
 */
final case class ScalaFile(
  packageDecl: PackageDecl,
  imports: List[Import] = Nil,
  types: List[TypeDefinition] = Nil
)
