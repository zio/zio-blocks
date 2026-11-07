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

package zio.blocks.scope

import zio.blocks.scope.internal.{ErrorMessages, Finalizers}

/**
 * A `Scope` owns resources and releases them when it closes. Open a child scope
 * with `scoped`, acquire resources in it with `allocate`, and use them with
 * `$`. When the `scoped` block returns or throws, the child runs its finalizers
 * in reverse order of registration (last in, first out).
 *
 * Each scope has its own type `$[A]` for the values it owns. The `$` macro lets
 * the block call methods on such a value but not pass it on, and `scoped`
 * returns only a value whose type has an [[Unscoped]] instance, so a resource
 * cannot leave its scope by accident (except, on Scala 3, a case class, sealed
 * trait or enum that extends `AutoCloseable`, a known bug). [[Scope.global]] is
 * the root of every scope tree.
 *
 * {{{
 * final class Connection(val url: String) extends AutoCloseable {
 *   def query(sql: String): Int = sql.length
 *   def close(): Unit           = println("closed " + url)
 * }
 *
 * val rows: Int = Scope.global.scoped { scope =>
 *   import scope._
 *   val conn = allocate(Resource(new Connection("jdbc:h2:mem:shop")))
 *   $(conn)(_.query("SELECT 1"))
 * }
 * // prints: closed jdbc:h2:mem:shop
 * // rows: 8
 * }}}
 *
 * ==Closed scopes==
 *
 * `allocate`, `open` and `$` throw [[java.lang.IllegalStateException]] on a
 * scope that has already closed, for example a scope reference that escaped its
 * `scoped` block. The message explains the mistake and how to fix it. `defer`
 * on a closed scope registers nothing and returns a handle whose `cancel()`
 * does nothing, and `scoped` on a closed scope runs its block in a child that
 * is already closed.
 *
 * @see
 *   [[https://zioblocks.com/reference/resource-management/scope/ Scope]]
 */
sealed abstract class Scope extends Finalizer with ScopeVersionSpecific { self =>

  /**
   * The type of a value that this scope owns, such as the result of `allocate`.
   *
   * Each scope has its own `$`, so a value of one scope cannot be used where
   * another scope's value is expected without an explicit [[lower]]. On
   * [[Scope.global]], `$[A]` is `A`. At run time a `$[A]` is the `A` itself.
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
   * The scope that created this one.
   *
   * [[Scope.global]] is its own parent (`parent == this`). A child from
   * `scoped` closes when its block exits, and a child from [[open]] closes at
   * the latest when its parent closes. Closing an `open()` scope while one of
   * its `scoped` blocks runs breaks this order, and then [[lower]]ed values are
   * already closed.
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
   * Lowers a value of the parent scope into this scope, so that this scope's
   * `$` accepts it.
   *
   * This is safe because a child normally closes before its parent (see
   * [[parent]] for the exception). At run time it is a cast and costs nothing.
   *
   * {{{
   * final class Pool extends AutoCloseable {
   *   def size: Int      = 4
   *   def close(): Unit = ()
   * }
   *
   * Scope.global.scoped { outer =>
   *   import outer._
   *   val pool = allocate(new Pool)
   *   outer.scoped { inner =>
   *     import inner._
   *     val local = lower(pool)
   *     $(local)(_.size)
   *   }
   * }
   * }}}
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

  /** The registry that holds this scope's finalizers. */
  protected def finalizers: Finalizers

  /**
   * Display name for this scope, used in runtime error messages.
   *
   * Returns "Scope.global" for the global scope and "Scope.Child" for child
   * scopes.
   */
  private[scope] def scopeDisplayName: String

  /**
   * Returns whether this scope has closed. Safe to call from any thread.
   *
   * A scope closes when it starts running its finalizers; after that, `$`,
   * `allocate` and `open` throw [[java.lang.IllegalStateException]]. On the
   * JVM, [[Scope.global]] returns `false` until its shutdown hook runs; on
   * Scala.js it never closes.
   *
   * @return
   *   `true` if this scope's finalizers have already been executed
   */
  def isClosed: Boolean = finalizers.isClosed

  /**
   * Returns whether the current thread owns this scope.
   *
   * `scoped` checks it, so that a child scope is not opened from another
   * thread. Always returns `true` for [[Scope.global]] and for scopes created
   * with `open()` (which are ''unowned''). For scopes created with `scoped`, it
   * returns `true` only on the thread that called `scoped`. On Scala.js it
   * always returns `true`.
   *
   * @return
   *   `true` if the calling thread is the owner of this scope
   */
  def isOwner: Boolean

  /**
   * Acquires a resource in this scope and returns it as a value of this scope.
   *
   * The resource is acquired now, and its finalizers are registered with this
   * scope, so they run when this scope closes.
   *
   * {{{
   * final class Connection extends AutoCloseable {
   *   def query(sql: String): Int = sql.length
   *   def close(): Unit           = println("connection closed")
   * }
   *
   * Scope.global.scoped { scope =>
   *   import scope._
   *   val conn: $[Connection] = allocate(Resource(new Connection))
   *   $(conn)(_.query("SELECT 1"))
   * }
   * // prints: connection closed
   * }}}
   *
   * @param resource
   *   the [[Resource]] describing how to acquire and release the value
   * @tparam A
   *   the resource value type
   * @return
   *   the acquired value wrapped as `$[A]`
   * @throws java.lang.IllegalStateException
   *   if this scope is already closed
   */
  def allocate[A](resource: Resource[A]): $[A] =
    if (isClosed)
      throw new IllegalStateException(
        ErrorMessages.renderAllocateOnClosedScope(scopeDisplayName, color = false)
      )
    else {
      val value = resource.make(this)
      $wrap(value)
    }

  /**
   * Acquires an `AutoCloseable` value in this scope and registers its `close()`
   * as a finalizer.
   *
   * Equivalent to `allocate(Resource(value))`.
   *
   * @param value
   *   a by-name expression producing the `AutoCloseable` value
   * @tparam A
   *   the `AutoCloseable` subtype
   * @return
   *   the acquired value wrapped as `$[A]`
   * @throws java.lang.IllegalStateException
   *   if this scope is already closed
   */
  def allocate[A <: AutoCloseable](value: => A): $[A] =
    allocate(Resource(value))

  /**
   * Registers a finalizer to run when this scope closes.
   *
   * Finalizers run in reverse order of registration (last in, first out). If a
   * finalizer throws, the others still run, and the errors are reported when
   * the scope closes. If the scope is already closed, the finalizer is never
   * run and the returned handle does nothing.
   *
   * {{{
   * Scope.global.scoped { scope =>
   *   import scope._
   *   defer(println("closed second"))
   *   defer(println("closed first"))
   *   ()
   * }
   * // prints: closed first
   * // prints: closed second
   * }}}
   *
   * @param f
   *   the cleanup action to run on scope closure
   * @return
   *   a [[DeferHandle]] that can be used to cancel the registration, or a no-op
   *   handle if the scope is already closed
   */
  override def defer(f: => Unit): DeferHandle =
    if (finalizers.isClosed) DeferHandle.Noop
    else finalizers.addFn(() => f)

  /**
   * Creates a child scope that stays open until its `close()` is called.
   *
   * The returned [[Scope.OpenScope]] holds the child scope and a `close()`
   * function. The child is linked to this (parent) scope, so if the parent
   * closes first, the child's finalizers run as part of the parent's. Calling
   * `close()` detaches the child from the parent, runs its finalizers, and
   * returns a [[Finalization]] with their errors instead of throwing them. The
   * child is unowned: any thread can use it.
   *
   * {{{
   * final class Session extends AutoCloseable {
   *   def close(): Unit = println("session closed")
   * }
   *
   * val handle  = Scope.global.open()
   * val session = handle.scope.allocate(new Session)
   * val result  = handle.close()
   * // prints: session closed
   * // result.isEmpty: true
   * }}}
   *
   * @return
   *   a scoped [[Scope.OpenScope]] wrapping the new child
   * @throws java.lang.IllegalStateException
   *   if this scope is already closed
   */
  def open(): $[Scope.OpenScope] = {
    if (isClosed)
      throw new IllegalStateException(
        ErrorMessages.renderOpenOnClosedScope(scopeDisplayName, color = false)
      )
    val fins        = new internal.Finalizers
    val childScope  = new Scope.Child(self, fins, owner = null, unowned = true)
    val closeHandle = new Scope.CloseHandle(fins, self.finalizers)
    if (!self.finalizers.addNode(closeHandle))
      throw new IllegalStateException(
        ErrorMessages.renderOpenOnClosedScope(scopeDisplayName, color = false)
      )
    $wrap(Scope.OpenScope(childScope, closeHandle))
  }

  /**
   * Enrichment for `$[Resource[A]]` scoped values.
   *
   * Provides `allocate` for acquiring a resource that is itself scoped. The
   * `Resource` never leaves the scope wrapper; only its *result* becomes
   * scoped.
   *
   * @tparam A
   *   the underlying resource value type
   */
  implicit class ScopedResourceOps[A](private val sr: $[Resource[A]]) {

    /**
     * Allocates the scoped [[Resource]] within this scope, returning the
     * acquired value as `$[A]`.
     *
     * The [[Resource]] itself stays inside `$`; only its acquired value becomes
     * a new scoped value. Equivalent to `self.allocate(\$unwrap(sr))`.
     *
     * @return
     *   the acquired value wrapped as `$[A]`
     * @throws java.lang.IllegalStateException
     *   if this scope is already closed
     */
    def allocate: $[A] = self.allocate($unwrap(sr))
  }

  /**
   * Enrichment for bare `Resource[A]` values.
   *
   * Provides `.allocate` as syntax sugar for `scope.allocate(resource)`,
   * enabling uniform `resource.allocate` syntax whether the resource is raw or
   * scoped.
   *
   * @tparam A
   *   the resource value type
   */
  implicit class ResourceOps[A](private val r: Resource[A]) { // not AnyVal: nested in Scope

    /**
     * Allocates this resource in the enclosing scope; the same as
     * `scope.allocate(resource)`.
     *
     * @throws java.lang.IllegalStateException
     *   if the scope is already closed
     */
    def allocate: $[A] = self.allocate(r)
  }
}

/**
 * Holds [[Scope.global]], the root of every scope tree, the child scope class
 * [[Scope.Child]], and the [[OpenScope]] handle that [[Scope.open]] returns.
 */
object Scope {

  /**
   * The root of every scope tree.
   *
   * `global` is its own parent (`parent == this`), and its `$[A]` is `A`, so
   * its values need no unwrapping. It is the usual starting point:
   * `Scope.global.scoped { scope => ... }`. Any thread may use it (`isOwner` is
   * always `true`). On the JVM, its finalizers, such as those of
   * `Scope.global.defer` or of a value allocated directly in it, run in a
   * shutdown hook when the JVM exits; on Scala.js they never run.
   *
   * {{{
   * val greeting: String = Scope.global.scoped { scope =>
   *   import scope._
   *   defer(println("scope closed"))
   *   "hello"
   * }
   * // prints: scope closed
   * // greeting: hello
   * }}}
   */
  object global extends Scope { self =>

    /** In `global`, a scoped value is the value itself: `$[A]` is `A`. */
    type $[+A] = A

    /** The type of `global` itself: it is its own parent. */
    type Parent = global.type

    /** `global` itself. */
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

    /** Returns `true` for every thread, because any thread may use `global`. */
    def isOwner: Boolean = true

    private[scope] val scopeDisplayName: String = "Scope.global"

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
   *   errors; a second call runs nothing and returns an empty [[Finalization]]
   */
  case class OpenScope private[scope] (scope: Scope, close: () => Finalization)

  /**
   * A combined node + close function for child scopes opened via
   * [[Scope.open]].
   *
   * This object lives in the parent scope's finalizer list (as a Node whose
   * thunk runs the child's finalizers) and also serves as the close function
   * for [[OpenScope]]. When invoked as `close()`, it cancels itself from the
   * parent and runs the child's finalizers.
   */
  private[scope] final class CloseHandle(
    childFinalizers: internal.Finalizers,
    parentFinalizers: internal.Finalizers
  ) extends internal.Finalizers.Node(() => ())
      with (() => Finalization) {
    override def run(): Unit  = childFinalizers.runAll().orThrow()
    def apply(): Finalization = {
      parentFinalizers.remove(this)
      childFinalizers.runAll()
    }
  }

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
   *   the thread that created this child (used by [[isOwner]]); `null` for a
   *   child from [[Scope.open]], and on Scala.js
   * @param unowned
   *   if `true`, [[isOwner]] always returns `true` (used by [[Scope.open]])
   */
  final class Child[P <: Scope] private[scope] (
    val parent: P,
    protected val finalizers: Finalizers,
    private[scope] val owner: AnyRef,
    private[scope] val unowned: Boolean = false
  ) extends Scope { self =>

    /** The type of the parent scope, the type argument `P`. */
    type Parent = P

    /**
     * Returns whether the current thread owns this scope.
     *
     * Always returns `true` for unowned scopes (created via [[Scope.open]]).
     * For scopes created via `scoped`, returns `true` only on the creating
     * thread. On Scala.js it always returns `true`.
     *
     * @return
     *   `true` if the calling thread is the owner of this scope
     */
    def isOwner: Boolean = if (unowned) true else PlatformScope.isOwner(owner)

    private[scope] val scopeDisplayName: String = "Scope.Child"

    private[scope] def close(): Finalization = finalizers.runAll()

    // $[A] is abstract here on both Scala 2 and 3; $wrap/$unwrap are casts,
    // which is sound because $[A] = A at runtime.
    /**
     * The type of a value scoped to this child. It is abstract, so a
     * `child.$[A]` is a different type from the `$[A]` of any other scope; at
     * runtime it is the `A` itself.
     */
    type $[+A]
    protected def $wrap[A](a: A): $[A]    = a.asInstanceOf[$[A]]
    protected def $unwrap[A](sa: $[A]): A = sa.asInstanceOf[A]
  }
}
