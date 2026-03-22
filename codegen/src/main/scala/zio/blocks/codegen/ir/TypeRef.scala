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
 * Represents a Scala type reference in the IR, supporting generic types.
 *
 * @param name
 *   The name of the type (e.g., "String", "List", "Option")
 * @param typeArgs
 *   Type arguments for generic types (defaults to empty list)
 *
 * @example
 *   {{{
 * // Simple type
 * val stringType = TypeRef("String")
 *
 * // Generic type with arguments
 * val listOfInt = TypeRef("List", List(TypeRef("Int")))
 *
 * // Using factory methods
 * val optionalString = TypeRef.optional(TypeRef.String)
 * val mapStringInt = TypeRef.map(TypeRef.String, TypeRef.Int)
 *   }}}
 */
final case class TypeRef(
  name: String,
  typeArgs: List[TypeRef] = Nil
)

object TypeRef {

  // Primitive types
  val Unit: TypeRef       = TypeRef("Unit")
  val Boolean: TypeRef    = TypeRef("Boolean")
  val Byte: TypeRef       = TypeRef("Byte")
  val Short: TypeRef      = TypeRef("Short")
  val Int: TypeRef        = TypeRef("Int")
  val Long: TypeRef       = TypeRef("Long")
  val Float: TypeRef      = TypeRef("Float")
  val Double: TypeRef     = TypeRef("Double")
  val String: TypeRef     = TypeRef("String")
  val BigInt: TypeRef     = TypeRef("BigInt")
  val BigDecimal: TypeRef = TypeRef("BigDecimal")
  val Any: TypeRef        = TypeRef("Any")
  val Nothing: TypeRef    = TypeRef("Nothing")

  /**
   * Creates a TypeRef with the given name and type arguments.
   *
   * @param name
   *   The name of the type
   * @param args
   *   Type arguments (variable arguments)
   * @return
   *   A TypeRef with the given name and arguments
   *
   * @example
   *   {{{
   * val either = TypeRef.of("Either", TypeRef.String, TypeRef.Int)
   * val list = TypeRef.of("List", TypeRef.String)
   *   }}}
   */
  def of(name: String, args: TypeRef*): TypeRef =
    TypeRef(name, args.toList)

  /**
   * Wraps a TypeRef in Option, representing an optional type.
   *
   * @param typeRef
   *   The type to wrap
   * @return
   *   A TypeRef representing Option[typeRef]
   *
   * @example
   *   {{{
   * val optString = TypeRef.optional(TypeRef.String)
   * // Creates: Option[String]
   *   }}}
   */
  def optional(typeRef: TypeRef): TypeRef =
    TypeRef("Option", List(typeRef))

  /**
   * Wraps a TypeRef in List, representing a list of elements.
   *
   * @param typeRef
   *   The element type
   * @return
   *   A TypeRef representing List[typeRef]
   *
   * @example
   *   {{{
   * val listOfInt = TypeRef.list(TypeRef.Int)
   * // Creates: List[Int]
   *   }}}
   */
  def list(typeRef: TypeRef): TypeRef =
    TypeRef("List", List(typeRef))

  /**
   * Wraps a TypeRef in Set, representing a set of unique elements.
   *
   * @param typeRef
   *   The element type
   * @return
   *   A TypeRef representing Set[typeRef]
   *
   * @example
   *   {{{
   * val setOfStrings = TypeRef.set(TypeRef.String)
   * // Creates: Set[String]
   *   }}}
   */
  def set(typeRef: TypeRef): TypeRef =
    TypeRef("Set", List(typeRef))

  /**
   * Creates a TypeRef representing a Map with key and value types.
   *
   * @param key
   *   The key type
   * @param value
   *   The value type
   * @return
   *   A TypeRef representing Map[key, value]
   *
   * @example
   *   {{{
   * val mapStringInt = TypeRef.map(TypeRef.String, TypeRef.Int)
   * // Creates: Map[String, Int]
   *   }}}
   */
  def map(key: TypeRef, value: TypeRef): TypeRef =
    TypeRef("Map", List(key, value))

  /**
   * Wraps a TypeRef in Chunk, representing a high-performance immutable
   * sequence.
   *
   * @param typeRef
   *   The element type
   * @return
   *   A TypeRef representing Chunk[typeRef]
   *
   * @example
   *   {{{
   * val chunkOfLongs = TypeRef.chunk(TypeRef.Long)
   * // Creates: Chunk[Long]
   *   }}}
   */
  def chunk(typeRef: TypeRef): TypeRef =
    TypeRef("Chunk", List(typeRef))

  /**
   * Creates a union type reference (Scala 3 `A | B`).
   *
   * @param types
   *   The types in the union
   * @return
   *   A TypeRef representing the union of the given types
   *
   * @example
   *   {{{
   * val strOrInt = TypeRef.union(TypeRef.String, TypeRef.Int)
   * // Represents: String | Int
   *   }}}
   */
  def union(types: TypeRef*): TypeRef = TypeRef("|", types.toList)

  /**
   * Creates an intersection type reference (Scala 3 `A & B`).
   *
   * @param types
   *   The types in the intersection
   * @return
   *   A TypeRef representing the intersection of the given types
   *
   * @example
   *   {{{
   * val named = TypeRef.intersection(TypeRef("HasName"), TypeRef("HasId"))
   * // Represents: HasName & HasId
   *   }}}
   */
  def intersection(types: TypeRef*): TypeRef = TypeRef("&", types.toList)

  def tuple(types: TypeRef*): TypeRef = TypeRef(s"Tuple${types.length}", types.toList)

  def function(params: List[TypeRef], returnType: TypeRef): TypeRef =
    TypeRef(s"Function${params.length}", params :+ returnType)

  val Wildcard: TypeRef = TypeRef("_")

}
