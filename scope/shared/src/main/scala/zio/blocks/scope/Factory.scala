package zio.blocks.scope

/**
 * A fully resolved recipe for creating a value.
 *
 * Unlike [[Wire]], a Factory has all dependencies resolved. When `make` is
 * called, it creates the value and registers any finalizers (e.g., for
 * AutoCloseable).
 *
 * Factories can be created anywhere, stored, and passed around. They only
 * execute when given to a Scope via `scope.create(factory)`.
 *
 * ==Creation==
 *
 * Use the `Factory[T]` macro (in Scala 3) for automatic derivation, or create
 * manually for custom logic.
 *
 * @example
 *   {{{
 *   Scope.global.scoped { scope =>
 *     val app = scope.create(Factory[App])
 *     scope.$(app)(_.run())
 *   }
 *   }}}
 *
 * @tparam A
 *   the type of value this factory creates
 */
sealed trait Factory[+A] {

  /**
   * Creates an instance of A using the given scope for finalizer registration.
   *
   * @param scope
   *   the scope to register finalizers with
   * @return
   *   the created instance
   */
  def make(scope: Scope[?, ?]): A
}

object Factory extends FactoryCompanionVersionSpecific {

  /**
   * A factory that produces shared (memoized) instances within a scope.
   */
  final class Shared[+A] private[scope] (
    private[scope] val makeFn: Scope[?, ?] => A
  ) extends Factory[A] {
    def make(scope: Scope[?, ?]): A = makeFn(scope)
  }

  /**
   * A factory that produces unique instances each time.
   */
  final class Unique[+A] private[scope] (
    private[scope] val makeFn: Scope[?, ?] => A
  ) extends Factory[A] {
    def make(scope: Scope[?, ?]): A = makeFn(scope)
  }

  /**
   * Creates a shared factory from a function.
   */
  def shared[A](f: Scope[?, ?] => A): Factory.Shared[A] = new Shared(f)

  /**
   * Creates a unique factory from a function.
   */
  def unique[A](f: Scope[?, ?] => A): Factory.Unique[A] = new Unique(f)

  /**
   * Creates a factory that returns a pre-existing value (no cleanup needed).
   */
  def apply[A](value: A): Factory.Shared[A] = new Shared(_ => value)
}
