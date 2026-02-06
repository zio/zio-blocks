package zio.blocks.scope

import scala.reflect.macros.whitebox

private[scope] object ScopedMacros {

  /**
   * Macro implementation for the `$` operator that verifies at compile time
   * that the scope's Tag is a supertype of S.
   *
   * This matches the Scala 3 constraint: `Scope[?] { type Tag >: S }`
   */
  def dollarImpl[A, S, B](c: whitebox.Context)(
    f: c.Expr[A => B]
  )(
    scope: c.Expr[Scope.Any],
    u: c.Expr[AutoUnscoped[B, S]]
  )(implicit
    stag: c.WeakTypeTag[S]
  ): c.Tree = {
    import c.universe._

    val sTpe     = stag.tpe
    val scopeTpe = scope.actualType

    // Extract the Tag type member from the scope
    val tagMember = scopeTpe.member(TypeName("Tag"))
    if (tagMember == NoSymbol) {
      c.abort(c.enclosingPosition, s"Scope type $scopeTpe does not have a Tag member")
    }

    val tagTpe = tagMember.asType.toType.asSeenFrom(scopeTpe, scopeTpe.typeSymbol)

    // Check that Tag >: S (i.e., S <:< Tag)
    if (!(sTpe <:< tagTpe)) {
      c.abort(
        c.enclosingPosition,
        s"Scoped value with tag $sTpe cannot be accessed with scope having Tag = $tagTpe. " +
          s"The scope's Tag must be a supertype of the scoped value's tag."
      )
    }

    // For value classes, the prefix tree contains the underlying value.
    // ScopedOps wraps A @@ S, which is just A at runtime.
    // The prefix might be:
    //   - new ScopedOps[A, S](value) if constructed explicitly
    //   - toScopedOps[A, S](value) from implicit conversion
    // We need to extract the underlying scoped value.
    val prefix = c.prefix.tree

    // Extract the scoped value from the prefix
    // For value class: new ScopedOps(x) or implicit conversion result
    val scopedValue = prefix match {
      case Apply(_, List(arg)) => arg
      case _                   => q"$prefix.scoped"
    }

    q"""
      $u.apply($f(_root_.zio.blocks.scope.@@.unscoped($scopedValue)))
    """
  }

  /**
   * Macro implementation for the `get` method that verifies at compile time
   * that the scope's Tag is a supertype of S.
   *
   * This matches the Scala 3 constraint: `Scope[?] { type Tag >: S }`
   */
  def getImpl[A, S](c: whitebox.Context)(
    scope: c.Expr[Scope.Any],
    u: c.Expr[AutoUnscoped[A, S]]
  )(implicit
    stag: c.WeakTypeTag[S]
  ): c.Tree = {
    import c.universe._

    val sTpe     = stag.tpe
    val scopeTpe = scope.actualType

    // Extract the Tag type member from the scope
    val tagMember = scopeTpe.member(TypeName("Tag"))
    if (tagMember == NoSymbol) {
      c.abort(c.enclosingPosition, s"Scope type $scopeTpe does not have a Tag member")
    }

    val tagTpe = tagMember.asType.toType.asSeenFrom(scopeTpe, scopeTpe.typeSymbol)

    // Check that Tag >: S (i.e., S <:< Tag)
    if (!(sTpe <:< tagTpe)) {
      c.abort(
        c.enclosingPosition,
        s"Scoped value with tag $sTpe cannot be accessed with scope having Tag = $tagTpe. " +
          s"The scope's Tag must be a supertype of the scoped value's tag."
      )
    }

    // For value classes, the prefix tree contains the underlying value.
    val prefix = c.prefix.tree

    // Extract the scoped value from the prefix
    val scopedValue = prefix match {
      case Apply(_, List(arg)) => arg
      case _                   => q"$prefix.scoped"
    }

    q"""
      $u.apply(_root_.zio.blocks.scope.@@.unscoped($scopedValue))
    """
  }
}
