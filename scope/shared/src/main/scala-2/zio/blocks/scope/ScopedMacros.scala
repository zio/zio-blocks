package zio.blocks.scope

import scala.reflect.macros.whitebox

private[scope] object ScopedMacros {

  /**
   * Checks if a scoped value with tag S can be accessed from a scope with the given Tag type.
   *
   * For path-dependent types with bounds (like `type Tag >: tail.Tag`), we check:
   * 1. Direct subtyping: S <:< Tag
   * 2. Bound subtyping: S <:< Tag's lower bound
   * 3. Hierarchy walking: For :: scopes, walk the tail chain to find matching tags
   */
  private def checkTagAccess(c: whitebox.Context)(sTpe: c.Type, scopeTpe: c.Type): Boolean = {
    import c.universe._

    val tagMember = scopeTpe.member(TypeName("Tag"))
    if (tagMember == NoSymbol) return false

    val tagTpe = tagMember.asType.toType.asSeenFrom(scopeTpe, scopeTpe.typeSymbol)

    // Direct check
    if (sTpe <:< tagTpe) return true

    // Check lower bound of the Tag type member
    val tagTypeInfo = tagMember.asType.typeSignature
    tagTypeInfo match {
      case TypeBounds(lo, _) if lo != NoType && !(lo =:= typeOf[Nothing]) =>
        val lowerBound = lo.asSeenFrom(scopeTpe, scopeTpe.typeSymbol)
        if (sTpe <:< lowerBound) return true
      case _ => // No usable bounds
    }

    // For :: scopes, walk the tail hierarchy
    if (scopeTpe.baseClasses.exists(_.fullName == "zio.blocks.scope.Scope.$colon$colon")) {
      val tailMember = scopeTpe.member(TermName("tail"))
      if (tailMember != NoSymbol) {
        val tailTpe = tailMember.typeSignature.resultType.asSeenFrom(scopeTpe, scopeTpe.typeSymbol)
        if (checkTagAccess(c)(sTpe, tailTpe)) return true
      }
    }

    false
  }

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
    u: c.Expr[ScopeEscape[B, S]]
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

    // Check that Tag >: S (i.e., S <:< Tag) using hierarchy-aware check
    if (!checkTagAccess(c)(sTpe, scopeTpe)) {
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
    u: c.Expr[ScopeEscape[A, S]]
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

    // Check that Tag >: S (i.e., S <:< Tag) using hierarchy-aware check
    if (!checkTagAccess(c)(sTpe, scopeTpe)) {
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
