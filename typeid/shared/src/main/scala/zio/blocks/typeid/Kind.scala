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
 * Represents the "kind" of a type - the type of a type.
 *
 * In Scala's type system:
 *   - Proper types like `Int`, `String` have kind `*` (Type)
 *   - Type constructors like `List`, `Option` have kind `* -> *`
 *   - Higher-kinded types like `Monad` have kind `(* -> *) -> *`
 *
 * This is essential for correctly representing higher-kinded types and ensuring
 * kind-correctness in type expressions.
 */
sealed trait Kind {

  /**
   * Returns true if this is a proper type (kind *).
   */
  def isProperType: Boolean = this eq Kind.Type

  /**
   * Returns the arity of this kind (0 for proper types, n for type
   * constructors).
   */
  def arity: Int = this match {
    case a: Kind.Arrow => a.params.size
    case _             => 0
  }
}

object Kind {

  /**
   * A proper type: `Int`, `String`, `List[Int]`. Kind: `*`
   */
  case object Type extends Kind

  /**
   * A type constructor or higher-kinded type. Kind:
   * `k1 -> k2 -> ... -> kn -> result`
   *
   * Examples:
   *   - `List` has kind `* -> *` = Arrow(List(Type), Type)
   *   - `Map` has kind `* -> * -> *` = Arrow(List(Type, Type), Type)
   *   - `Functor` has kind `(* -> *) -> *` = Arrow(List(Arrow(List(Type),
   *     Type)), Type)
   */
  final case class Arrow(params: List[Kind], result: Kind) extends Kind

  // Common kinds for convenience

  /** Kind `*` - proper type */
  val Star: Kind = Type

  /** Kind `* -> *` - unary type constructor (List, Option, etc.) */
  val Star1: Kind = new Arrow(List(Type), Type)

  /** Kind `* -> * -> *` - binary type constructor (Map, Either, etc.) */
  val Star2: Kind = new Arrow(List(Type, Type), Type)

  /** Kind `(* -> *) -> *` - higher-kinded unary (Functor, Monad, etc.) */
  val HigherStar1: Kind = new Arrow(List(Star1), Type)

  /**
   * Creates a simple n-ary type constructor kind. All parameters have kind `*`
   * and result is `*`.
   */
  def constructor(arity: Int): Kind =
    if (arity <= 0) Type
    else new Arrow(List.fill(arity)(Type), Type)
}
