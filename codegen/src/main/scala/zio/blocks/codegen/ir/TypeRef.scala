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
 * A reference to a Scala type in the codegen IR: a name plus type arguments, as
 * in `Map[String, List[Int]]`. Use it for field types, return types, parents
 * and type arguments. `zio.blocks.codegen.emit.ScalaEmitter` writes it as
 * `Name[Arg1, Arg2]`. A name that contains a `.` or one of `()[]=>` is written
 * as is, so qualified names (`java.util.UUID`) and hand-written types work; any
 * other name that is a Scala keyword or not a plain identifier is wrapped in
 * backquotes.
 *
 * {{{
 * import zio.blocks.codegen.ir._
 * import zio.blocks.codegen.emit.ScalaEmitter
 *
 * // Simple type
 * val stringType = TypeRef("String")
 *
 * // Generic type with arguments
 * val listOfInt = TypeRef("List", List(TypeRef("Int")))
 *
 * // Using factory methods
 * val optionalString = TypeRef.optional(TypeRef.String)
 * val mapStringInt = TypeRef.map(TypeRef.String, TypeRef.Int)
 *
 * ScalaEmitter.emitTypeRef(TypeRef.map(TypeRef.String, TypeRef.list(TypeRef.Int)))
 * // Map[String, List[Int]]
 * }}}
 *
 * @param name
 *   The name of the type (e.g., "String", "List", "Option")
 * @param typeArgs
 *   Type arguments for generic types (defaults to empty list)
 * @see
 *   [[https://zioblocks.com/reference/codegen/type-ref/ TypeRef]]
 */
final case class TypeRef(
  name: String,
  typeArgs: List[TypeRef] = Nil
)

/** Ready-made references to common types, and factories for generic ones. */
object TypeRef {

  // Primitive types
  /** The `Unit` type. */
  val Unit: TypeRef = TypeRef("Unit")

  /** The `Boolean` type. */
  val Boolean: TypeRef = TypeRef("Boolean")

  /** The `Byte` type. */
  val Byte: TypeRef = TypeRef("Byte")

  /** The `Short` type. */
  val Short: TypeRef = TypeRef("Short")

  /** The `Int` type. */
  val Int: TypeRef = TypeRef("Int")

  /** The `Long` type. */
  val Long: TypeRef = TypeRef("Long")

  /** The `Float` type. */
  val Float: TypeRef = TypeRef("Float")

  /** The `Double` type. */
  val Double: TypeRef = TypeRef("Double")

  /** The `String` type. */
  val String: TypeRef = TypeRef("String")

  /** The `BigInt` type. */
  val BigInt: TypeRef = TypeRef("BigInt")

  /** The `BigDecimal` type. */
  val BigDecimal: TypeRef = TypeRef("BigDecimal")

  /** The `Any` type. */
  val Any: TypeRef = TypeRef("Any")

  /** The `Nothing` type. */
  val Nothing: TypeRef = TypeRef("Nothing")

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
   * import zio.blocks.codegen.ir._
   *
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
   * import zio.blocks.codegen.ir._
   *
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
   * import zio.blocks.codegen.ir._
   *
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
   * import zio.blocks.codegen.ir._
   *
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
   * import zio.blocks.codegen.ir._
   *
   * val mapStringInt = TypeRef.map(TypeRef.String, TypeRef.Int)
   * // Creates: Map[String, Int]
   *   }}}
   */
  def map(key: TypeRef, value: TypeRef): TypeRef =
    TypeRef("Map", List(key, value))

  /**
   * Wraps a TypeRef in `Chunk`, the immutable sequence of ZIO Blocks. The
   * generated file needs an import of `Chunk`.
   *
   * @param typeRef
   *   The element type
   * @return
   *   A TypeRef representing Chunk[typeRef]
   *
   * @example
   *   {{{
   * import zio.blocks.codegen.ir._
   *
   * val chunkOfLongs = TypeRef.chunk(TypeRef.Long)
   * // Creates: Chunk[Long]
   *   }}}
   */
  def chunk(typeRef: TypeRef): TypeRef =
    TypeRef("Chunk", List(typeRef))

  /**
   * Creates a union type reference (Scala 3 `A | B`). The emitter writes
   * `A | B` in both dialects, so the output needs Scala 3.
   *
   * @param types
   *   The types in the union
   * @return
   *   A TypeRef representing the union of the given types
   *
   * @example
   *   {{{
   * import zio.blocks.codegen.ir._
   *
   * val strOrInt = TypeRef.union(TypeRef.String, TypeRef.Int)
   * // Represents: String | Int
   *   }}}
   */
  def union(types: TypeRef*): TypeRef = TypeRef("|", types.toList)

  /**
   * Creates an intersection type reference (Scala 3 `A & B`). The emitter
   * writes `A & B` in both dialects, so the output needs Scala 3.
   *
   * @param types
   *   The types in the intersection
   * @return
   *   A TypeRef representing the intersection of the given types
   *
   * @example
   *   {{{
   * import zio.blocks.codegen.ir._
   *
   * val named = TypeRef.intersection(TypeRef("HasName"), TypeRef("HasId"))
   * // Represents: HasName & HasId
   *   }}}
   */
  def intersection(types: TypeRef*): TypeRef = TypeRef("&", types.toList)

  /**
   * Creates a tuple type, emitted as `TupleN[...]` where N is the number of
   * types (`Tuple2[Int, String]`). Pass at least one type.
   */
  def tuple(types: TypeRef*): TypeRef = TypeRef(s"Tuple${types.length}", types.toList)

  /**
   * Creates a function type, emitted as `FunctionN[...]` with the parameter
   * types followed by the result type (`Function1[Int, String]`).
   */
  def function(params: List[TypeRef], returnType: TypeRef): TypeRef =
    TypeRef(s"Function${params.length}", params :+ returnType)

  /** The wildcard type argument `_`, as in `List[_]`. */
  val Wildcard: TypeRef = TypeRef("_")

}
