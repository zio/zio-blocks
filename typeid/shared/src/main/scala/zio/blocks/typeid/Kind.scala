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
  def isProperType: Boolean = this == Kind.Type

  /**
   * Returns the arity of this kind (0 for proper types, n for type
   * constructors).
   */
  def arity: Int = this match {
    case Kind.Type         => 0
    case Kind.Arrow(ps, _) => ps.size
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
  val Star1: Kind = Arrow(List(Type), Type)

  /** Kind `* -> * -> *` - binary type constructor (Map, Either, etc.) */
  val Star2: Kind = Arrow(List(Type, Type), Type)

  /** Kind `(* -> *) -> *` - higher-kinded unary (Functor, Monad, etc.) */
  val HigherStar1: Kind = Arrow(List(Star1), Type)

  /**
   * Creates a simple n-ary type constructor kind. All parameters have kind `*`
   * and result is `*`.
   */
  def constructor(arity: Int): Kind =
    if (arity <= 0) Type
    else Arrow(List.fill(arity)(Type), Type)
}
