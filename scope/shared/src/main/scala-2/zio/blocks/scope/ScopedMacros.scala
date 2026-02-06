package zio.blocks.scope

import scala.reflect.macros.whitebox

private[scope] object ScopedMacros {

  /**
   * Extracts the scope tree directly from the tag type's prefix.
   *
   * When we have a type like `A @@ scope.Tag`, the `scope` is encoded as the
   * prefix of the Tag type. We can extract it directly without implicit search.
   */
  private def extractScopeFromTag(c: whitebox.Context)(sTpe: c.Type): Option[c.Tree] = {
    import c.universe._

    sTpe.dealias match {
      // Match TypeRef where the symbol is named "Tag"
      case TypeRef(pre, sym, _) if sym.name == TypeName("Tag") =>
        pre.dealias match {
          // SingleType means it's a path-dependent type like scope.Tag
          case SingleType(_, scopeSym) if scopeSym != NoSymbol =>
            Some(Ident(scopeSym))
          case ThisType(scopeSym) if scopeSym != NoSymbol =>
            Some(This(scopeSym))
          case _ =>
            // Fallback: try to find any implicit Scope
            val scopeType = typeOf[Scope]
            val anyScope  = c.inferImplicitValue(scopeType, silent = true)
            if (anyScope != EmptyTree) Some(anyScope) else None
        }
      case _ =>
        // Not a Tag type, try implicit search
        val scopeType = typeOf[Scope]
        val anyScope  = c.inferImplicitValue(scopeType, silent = true)
        if (anyScope != EmptyTree) Some(anyScope) else None
    }
  }

  /**
   * Macro implementation for the `$` operator that finds the appropriate
   * implicit scope at compile time.
   *
   * Searches for all implicit Scope values and selects the one whose Tag is
   * compatible with S (Tag >: S). Among compatible scopes, picks the most
   * specific (innermost) one.
   */
  def dollarImpl[A, S, B](c: whitebox.Context)(
    f: c.Expr[A => B]
  )(
    u: c.Expr[ScopeEscape[B, S]]
  )(implicit
    stag: c.WeakTypeTag[S]
  ): c.Tree = {
    import c.universe._

    val sTpe = stag.tpe.dealias

    // Extract the scope from the tag type's prefix
    // This verifies we're inside a valid scope for this tagged value
    extractScopeFromTag(c)(sTpe).getOrElse {
      c.abort(
        c.enclosingPosition,
        s"No scope found for tag $sTpe. " +
          s"Make sure you are inside a scope.use { implicit scope => ... } block."
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
      $u.apply($f(_root_.zio.blocks.scope.@@.unscoped($scopedValue)))
    """
  }

  /**
   * Macro implementation for the `get` method that extracts the scope from the
   * tag type's prefix.
   */
  def getImpl[A, S](c: whitebox.Context)(
    u: c.Expr[ScopeEscape[A, S]]
  )(implicit
    stag: c.WeakTypeTag[S]
  ): c.Tree = {
    import c.universe._

    val sTpe = stag.tpe.dealias

    // Extract the scope from the tag type's prefix
    extractScopeFromTag(c)(sTpe).getOrElse {
      c.abort(
        c.enclosingPosition,
        s"No scope found for tag $sTpe. " +
          s"Make sure you are inside a scope.use { implicit scope => ... } block."
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
