package zio.blocks.scope

/**
 * Module that provides opaque-like scoping for Scala 2.
 *
 * Uses the "module pattern" to emulate Scala 3's opaque types with zero runtime
 * overhead. The type `A @@ S` is a value of type `A` that is "locked" to a
 * scope with tag `S`. The abstract type hides all methods on `A`, so the only
 * way to use the value is through the `$` operator, which requires the matching
 * scope capability.
 */
private[scope] sealed trait ScopedModule {
  type @@[+A, +S]

  def scoped[A, S](a: A): A @@ S
  private[scope] def unscoped[A, S](scoped: A @@ S): A
}

private[scope] object ScopedModule {
  val instance: ScopedModule = new ScopedModule {
    type @@[+A, +S] = A

    def scoped[A, S](a: A): A @@ S                       = a
    private[scope] def unscoped[A, S](scoped: A @@ S): A = scoped
  }
}
