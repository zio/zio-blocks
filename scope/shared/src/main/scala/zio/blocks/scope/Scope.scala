package zio.blocks.scope

import zio.blocks.scope.internal.Finalizers

/**
 * A scope that manages resource lifecycle with compile-time verified safety.
 *
 * ==Closed-scope defense==
 *
 * If a scope reference escapes to another thread (e.g. via a `Future`) and the
 * original scope closes while the other thread still holds a reference, all
 * scope operations (`$`, `allocate`, `open`, `lower`) become no-ops that return
 * a default-valued `$[B]` (`null` for reference types, `0` for numeric types,
 * `false` for `Boolean`, etc.). This prevents the escaped thread from
 * interacting with already-released resources, but callers should be aware that
 * these default values may appear if scopes are used across thread boundaries
 * incorrectly. `defer` on a closed scope is silently ignored, and `scoped`
 * creates a born-closed child.
 */
sealed abstract class Scope extends Finalizer with ScopeVersionSpecific { self =>

  implicit val thisScope: this.type = this

  /**
   * Opaque scoped-value wrapper, unique to each scope instance.
   *
   * Different scopes have structurally incompatible `$` types, which prevents
   * accidentally mixing values from one scope with operations on another at
   * compile time. Erased to `A` at runtime (zero-cost).
   *
   * @tparam A
   *   the underlying value type
   */
  type $[+A]

  /**
   * The parent scope type in the scope hierarchy.
   *
   * For [[Scope.global]], this is `global.type` (self-referential). For child
   * scopes, this is the concrete type of the enclosing scope. Used by [[lower]]
   * to safely widen parent-scoped values into this scope.
   */
  type Parent <: Scope

  /**
   * Reference to the parent scope in the hierarchy.
   *
   * [[Scope.global]] is self-referential (`parent == this`). For child scopes,
   * this points to the scope that created this one. The parent always outlives
   * its children, which is what makes [[lower]] safe.
   */
  val parent: Parent

  /**
   * Wraps a raw value into this scope's `$` type.
   *
   * Identity at runtime (zero-cost); exists only for type-level safety.
   * Invariant: `\$unwrap(\$wrap(a)) == a`.
   *
   * @param a
   *   the value to wrap
   * @tparam A
   *   the value type
   * @return
   *   the value tagged with this scope's identity
   */
  protected def $wrap[A](a: A): $[A]

  /**
   * Unwraps a scoped value back to its raw type.
   *
   * Identity at runtime (zero-cost); exists only for type-level safety.
   * Invariant: `\$unwrap(\$wrap(a)) == a`.
   *
   * @param sa
   *   the scoped value to unwrap
   * @tparam A
   *   the value type
   * @return
   *   the underlying raw value
   */
  protected def $unwrap[A](sa: $[A]): A

  /**
   * Lowers a parent-scoped value into this scope.
   *
   * This is safe because a parent scope always outlives its children: the
   * child's finalizers run before the parent closes. The operation is zero-cost
   * at runtime (a cast).
   *
   * @param value
   *   a value scoped to this scope's [[parent]]
   * @tparam A
   *   the underlying value type
   * @return
   *   the same value, re-tagged with this scope's identity
   */
  final def lower[A](value: parent.$[A]): $[A] =
    value.asInstanceOf[$[A]]

  /**
   * Underlying finalizer registry. Package-private; implemented by
   * [[Scope.Child]] and [[Scope.global]].
   */
  protected def finalizers: Finalizers

  /**
   * Thread-safe check for whether this scope has been closed.
   *
   * Once a scope is closed its finalizers have already run and all subsequent
   * scope operations (`$`, `allocate`, `open`, `lower`) become no-ops that
   * return default-valued scoped values (`null` for reference types, zero/false
   * for value types). [[Scope.global]] returns `false` until JVM shutdown.
   *
   * @return
   *   `true` if this scope's finalizers have already been executed
   */
  def isClosed: Boolean = finalizers.isClosed

  /**
   * Returns whether the current thread owns this scope.
   *
   * Ownership is checked to detect cross-thread scope usage. Always returns
   * `true` for [[Scope.global]] and for scopes created with `open()` (which are
   * ''unowned''). For scopes created via `scoped`, returns `true` only on the
   * thread that entered the `scoped` block.
   *
   * @return
   *   `true` if the calling thread is the owner of this scope
   */
  def isOwner: Boolean

  /**
   * Acquires a resource in this scope, registering its finalizer.
   *
   * The resource's `make` method is called to produce a value and register any
   * cleanup actions with this scope's finalizer registry. If this scope is
   * already closed, no acquisition occurs and a default-valued `$[A]` is
   * returned.
   *
   * @param resource
   *   the [[Resource]] describing how to acquire and release the value
   * @tparam A
   *   the resource value type
   * @return
   *   the acquired value wrapped as `$[A]`, or a default-valued `$[A]` if
   *   closed
   */
  def allocate[A](resource: Resource[A]): $[A] =
    if (isClosed) $wrap(null.asInstanceOf[A])
    else {
      val value = resource.make(this)
      $wrap(value)
    }

  /**
   * Convenience overload that acquires an `AutoCloseable` value directly.
   *
   * Equivalent to `allocate(Resource(value))`; the value's `close()` method is
   * registered as a finalizer. If this scope is already closed, no acquisition
   * occurs and a default-valued `$[A]` is returned.
   *
   * @param value
   *   a by-name expression producing the `AutoCloseable` value
   * @tparam A
   *   the `AutoCloseable` subtype
   * @return
   *   the acquired value wrapped as `$[A]`, or a default-valued `$[A]` if
   *   closed
   */
  def allocate[A <: AutoCloseable](value: => A): $[A] =
    allocate(Resource(value))

  /**
   * Registers a finalizer to run when this scope closes.
   *
   * Finalizers are executed in LIFO order when the scope closes. If the scope
   * is already closed, the finalizer is silently ignored and a no-op
   * `DeferHandle` is returned.
   *
   * @param f
   *   the cleanup action to run on scope closure
   * @return
   *   a [[DeferHandle]] that can be used to cancel the registration, or a no-op
   *   handle if the scope is already closed
   */
  override def defer(f: => Unit): DeferHandle = finalizers.add(f)

  /**
   * Creates a child scope that must be explicitly closed.
   *
   * The returned [[Scope.OpenScope]] contains the child scope and a `close()`
   * function. The child's finalizers are linked to this (parent) scope so that
   * if the parent closes first, the child's finalizers also run. Calling
   * `close()` on the [[Scope.OpenScope]] detaches the child from the parent,
   * runs its finalizers, and returns a [[Finalization]].
   *
   * @return
   *   a scoped [[Scope.OpenScope]] wrapping the new child, or a default-valued
   *   scoped value if this scope is already closed
   */
  def open(): $[Scope.OpenScope] = {
    val fins                        = new internal.Finalizers
    val owner                       = PlatformScope.captureOwner()
    val childScope                  = new Scope.Child(self, fins, owner, unowned = true)
    val handle                      = self.defer(fins.runAll().orThrow())
    val closeFn: () => Finalization = () => {
      handle.cancel()
      fins.runAll()
    }
    if (isClosed) $wrap(null.asInstanceOf[Scope.OpenScope])
    else $wrap(Scope.OpenScope(childScope, closeFn))
  }

  /**
   * Enrichment for `$[A]` scoped values.
   *
   * Provides `get` for extracting pure-data values from `$[A]` when the type
   * has an [[Unscoped]] instance.
   *
   * @tparam A
   *   the underlying value type
   */
  implicit class ScopedOps[A](private val sa: $[A]) {

    /**
     * Extracts the underlying value from a `$[A]`.
     *
     * Only available when `A` has an [[Unscoped]] instance, ensuring only pure
     * data (not resources) can be extracted. This is sound because the
     * macro-enforced `$` prevents creating `$[A]` values where `A: Unscoped`
     * but the value secretly holds a resource reference.
     *
     * @param ev
     *   evidence that `A` is safe to extract (pure data, not a resource)
     * @return
     *   the underlying value of type `A`
     */
    def get(implicit ev: Unscoped[A]): A = $unwrap(sa)
  }

  /**
   * Enrichment for `$[Resource[A]]` scoped values.
   *
   * Provides `allocate` for acquiring a resource that is itself scoped, without
   * needing to extract the `Resource` from `$` via `.get`. This is sound
   * because the `Resource` never leaves the scope wrapper; only its *result*
   * becomes scoped.
   *
   * @tparam A
   *   the underlying resource value type
   */
  implicit class ScopedResourceOps[A](private val sr: $[Resource[A]]) {

    /**
     * Allocates the scoped [[Resource]] within this scope, returning the
     * acquired value as `$[A]`.
     *
     * The [[Resource]] is never extracted from `$`; only its acquired *result*
     * becomes a new scoped value. Equivalent to `self.allocate(\$unwrap(sr))`.
     *
     * @return
     *   the acquired value wrapped as `$[A]`, or a default-valued `$[A]` if
     *   the scope is already closed
     */
    def allocate: $[A] = self.allocate($unwrap(sr))
  }
}

/**
 * Companion object for [[Scope]], providing the global scope, child scope
 * implementation, and the [[OpenScope]] handle.
 */
object Scope {

  /**
   * The root of all scope hierarchies.
   *
   * `global` is self-referential (`parent == this`) and never closes under
   * normal operation. Its `$[A]` is simply `A` (zero-cost identity). Finalizers
   * registered with `global.defer` run on JVM shutdown via a shutdown hook.
   * `isOwner` always returns `true`.
   */
  object global extends Scope { self =>
    type $[+A]  = A
    type Parent = global.type
    val parent: Parent = this

    protected def $wrap[A](a: A): $[A]    = a
    protected def $unwrap[A](sa: $[A]): A = sa

    protected val finalizers: Finalizers = {
      val f = new Finalizers
      PlatformScope.registerShutdownHook { () =>
        f.runAll().orThrow()
      }
      f
    }

    def isOwner: Boolean = true

    private[scope] def runFinalizers(): Finalization = finalizers.runAll()
  }

  /**
   * A handle returned by [[Scope.open]] representing an explicitly-managed
   * child scope.
   *
   * @param scope
   *   the child scope that was created
   * @param close
   *   a function that detaches the child from its parent, runs the child's
   *   finalizers in LIFO order, and returns a [[Finalization]] collecting any
   *   errors
   */
  case class OpenScope private[scope] (scope: Scope, close: () => Finalization)

  /**
   * A child scope created by `scoped { ... }` or [[Scope.open]].
   *
   * Child scopes have their own finalizer registry and are linked to a parent
   * scope. When the child closes, its finalizers run in LIFO order. The `$[A]`
   * type is structurally distinct from the parent's, preventing accidental
   * value mixing at compile time (use [[Scope.lower]] to convert explicitly).
   *
   * @tparam P
   *   the concrete type of the parent scope
   * @param parent
   *   the parent scope that created this child
   * @param finalizers
   *   the finalizer registry for this child
   * @param owner
   *   opaque reference to the creating thread (used by [[isOwner]])
   * @param unowned
   *   if `true`, [[isOwner]] always returns `true` (used by [[Scope.open]])
   */
  final class Child[P <: Scope] private[scope] (
    val parent: P,
    protected val finalizers: Finalizers,
    private[scope] val owner: AnyRef,
    private[scope] val unowned: Boolean = false
  ) extends Scope { self =>
    type Parent = P

    /**
     * Returns whether the current thread owns this scope.
     *
     * Always returns `true` for unowned scopes (created via [[Scope.open]]).
     * For scopes created via `scoped`, returns `true` only on the creating
     * thread.
     *
     * @return
     *   `true` if the calling thread is the owner of this scope
     */
    def isOwner: Boolean = if (unowned) true else PlatformScope.isOwner(owner)

    private[scope] def close(): Finalization = finalizers.runAll()

    // $[A] type and $wrap/$unwrap are version-specific
    // Scala 3: opaque type $[+A] = A
    // Scala 2: module pattern
    //
    // For cross-compilation, we use asInstanceOf which is sound
    // because $[A] = A at runtime for both Scala 2 and 3
    type $[+A]
    protected def $wrap[A](a: A): $[A]    = a.asInstanceOf[$[A]]
    protected def $unwrap[A](sa: $[A]): A = sa.asInstanceOf[A]
  }
}
