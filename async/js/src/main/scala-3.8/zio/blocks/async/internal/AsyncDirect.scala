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

package zio.blocks.async.internal

import zio.blocks.async.*

import scala.quoted.*

/**
 * Scala.js 3.8+ direct-style implementation of
 * `Async.async { ... .await ... }`, backed by the JavaScript-native `js.async`
 * / `js.await` primitives (Scala.js 1.19+, ES2017 target) where possible, with
 * the dotty-cps-async transform ([[AsyncDcaTransform]]) as fallback.
 *
 * `js.async`/`js.await` compile to real JavaScript `async`/`await`, the fastest
 * suspension mechanism on the platform — but they are lexically restricted: a
 * `js.await` may not sit under a lambda, by-name argument, or nested method
 * within its `js.async` block. The documented `Async.async` surface is wider
 * (e.g. `.await` inside whitelisted strict-collection HOF closures works via
 * DCA's AsyncShift on every other cell), so [[asyncImpl]] picks per block:
 *
 *   - every `.await` in direct position → native `js.async`/`js.await`;
 *   - any `.await` under a lambda / by-name argument / nested method or class →
 *     the shared DCA transform, identical to the Scala 3 (< 3.8) JS cell.
 *
 * The native arm preserves the library's semantics exactly:
 *
 *   - '''Readiness''': `js.async` always returns a promise, but a body whose
 *     awaits are all ready runs to completion synchronously (ready awaits never
 *     reach `js.await`). The generated wrapper observes that via a completion
 *     flag and returns a ready `Async` — matching the JVM/DCA cells — falling
 *     back to the promise only for genuine suspension. A throw before the first
 *     suspension likewise settles to a ready failure.
 *   - '''Value integrity''': everything crossing the promise transport is boxed
 *     ([[AsyncJsRuntime.Box]]) because JS promise resolution adopts thenables —
 *     a `js.Promise`-as-value success would otherwise be replaced by its
 *     settled value. (Boxing also sidesteps the Scala 3.8.3
 *     `js.await(js.Promise[Unit])` compiler limitation.)
 *   - '''Composability''': `.await` calls are rewritten here, in the enclosing
 *     `Async.async` expansion — never left to self-expand — so a nested,
 *     already-expanded `Async.async` block contains no `.await` tokens and is
 *     treated as the opaque `Async` value it is, on every backend arm.
 *
 * Shares the same `awaitImpl` / `asyncImpl` macro entry points as the DCA
 * implementation, so the package-object syntax (`AsyncSyntaxVersionSpecific`,
 * `AsyncCompanionVersionSpecific`) is identical across every Scala 3 cell.
 */
private[async] object AsyncDirect {

  /**
   * Expansion of a bare `.await` that survived to code generation — i.e. one
   * used outside any `Async.async { ... }` block ([[asyncImpl]] rewrites every
   * `.await` it accepts, on both the native and DCA arms). Always a compile
   * error; this is what gives `.await` its lexical restriction.
   */
  def awaitImpl[A: Type](self: Expr[Async[A]])(using Quotes): Expr[A] = {
    import quotes.reflect.*
    report.errorAndAbort(
      "`.await` may only be used directly inside an `Async.async { ... }` block.",
      self.asTerm.pos
    )
  }

  /**
   * `Async.async { body }` → a readiness-preserving `js.async` wrapper when
   * every `.await` is in direct position, [[AsyncDcaTransform.asyncImpl]] when
   * any `.await` sits under a lambda / by-name argument / nested method, and a
   * plain `Async.attempt` when the body contains no `.await` at all (zero
   * suspension, no `Promise` round-trip).
   */
  def asyncImpl[A: Type](body: Expr[A])(using Quotes): Expr[Async[A]] = {
    import quotes.reflect.*

    AsyncDcaTransform.rejectLazyAwaitVals(body.asTerm)

    var hasAwait    = false
    var hasIndirect = false

    def isAwaitCall(t: Tree): Boolean = t match {
      case Apply(TypeApply(fun, _), _) if AsyncDcaTransform.isAwait(fun) => true
      case _                                                             => false
    }

    /**
     * Scan with a context flag: `indirect` is true once we are under any
     * construct `js.await` may not cross (lambda/nested method bodies appear as
     * `DefDef`s, match-lambdas and local classes as `ClassDef`s, and by-name
     * arguments are detected from the applied method's parameter types) — or
     * one whose '''semantics''' the native transport cannot reproduce: a `try`
     * with catch clauses over an awaiting body must use the DCA fallback,
     * because a native throw delivers a `null` failure cause to the user catch
     * as the (internal) `NullCauseMarker` Throwable, which the handler would
     * wrongly match; the DCA cells match handlers against the logical cause.
     */
    final class Scan(indirect: Boolean) extends TreeTraverser {
      override def traverseTree(t: Tree)(owner: Symbol): Unit = t match {
        case term: Term if isAwaitCall(term) =>
          hasAwait = true
          if (indirect) hasIndirect = true
          super.traverseTree(term)(owner) // the qualifier shares this context
        case dd: DefDef =>
          new Scan(true).traverseTreeChildren(dd)(owner)
        case cd: ClassDef =>
          new Scan(true).traverseTreeChildren(cd)(owner)
        case tr: Try
            if (tr.cases.nonEmpty && AsyncDcaTransform.containsAwait(tr.body)) ||
              (tr.finalizer.nonEmpty &&
                (AsyncDcaTransform.containsAwait(tr.body) ||
                  tr.cases.exists(c => AsyncDcaTransform.containsAwait(c)) ||
                  tr.finalizer.exists(f => AsyncDcaTransform.containsAwait(f)))) =>
          // A failure thrown by an await in the body lands in the user catch:
          // only the DCA catch emulation preserves logical-cause matching. And
          // a failing finalizer over an in-flight failure must attach it as a
          // suppressed exception (parity with the other cells), which the raw
          // JS try/finally transport cannot do — so an awaiting try/finally
          // also takes the DCA fallback.
          new Scan(true).traverseTree(tr.body)(owner)
          tr.cases.foreach(c => new Scan(true).traverseTree(c)(owner))
          tr.finalizer.foreach(f => new Scan(true).traverseTree(f)(owner))
        case ap @ Apply(fun, args) =>
          traverseTree(fun)(owner)
          val paramTypes = ap.fun.tpe.widen match {
            case mt: MethodType => mt.paramTypes
            case _              => Nil
          }
          args.zipWithIndex.foreach { case (arg, i) =>
            val byName = i < paramTypes.length && (paramTypes(i) match {
              case ByNameType(_) => true
              case _             => false
            })
            if (byName) new Scan(true).traverseTree(arg)(owner)
            else traverseTree(arg)(owner)
          }
        case _ =>
          super.traverseTree(t)(owner)
      }
    }
    new Scan(false).traverseTree(body.asTerm)(Symbol.spliceOwner)

    if (!hasAwait) '{ Async.attempt[A]($body) }
    else if (hasIndirect) AsyncDcaTransform.asyncImpl(body)
    else {
      // Rewrite every direct `.await` to the boxed native transport: a ready
      // value short-circuits with no Promise allocation; a suspended (or
      // failed) Async is driven via `js.await` of a boxed promise.
      val rewriter = new TreeMap {
        override def transformTerm(term: Term)(owner: Symbol): Term =
          term match {
            case Apply(TypeApply(fun, List(tTpe)), List(qual)) if AsyncDcaTransform.isAwait(fun) =>
              tTpe.tpe.asType match {
                case '[t] =>
                  given Quotes = owner.asQuotes
                  val q        = transformTerm(qual)(owner).asExprOf[Async[t]]
                  '{
                    val r: Any = $q
                    if (r.isInstanceOf[Failure])
                      // A ready failure throws synchronously (DCA parity);
                      // riding the promise transport would cost a mandatory
                      // microtask and forfeit the block's readiness.
                      AsyncJsRuntime.rethrowReadyFailure(r)
                    else if (r.isInstanceOf[Pollable[?]])
                      scala.scalajs.js.await(AsyncJsRuntime.toBoxedPromise[t](r.asInstanceOf[Async[t]])).value
                    else AsyncJsRuntime.deliver[t](r)
                  }.asTerm
              }
            case _ => super.transformTerm(term)(owner)
          }
      }
      val rewritten = rewriter.transformTerm(body.asTerm)(Symbol.spliceOwner).asExprOf[A]
      '{
        // One captured cell instead of four. Every `var` captured by the
        // `js.async` closure is heap-boxed as a `Ref` on Scala.js, so collapsing
        // settled/value/failed/failure into a single `outcome` saves three
        // ref-cell allocations per block. While the body is still running (or
        // suspended) `outcome` is the shared `Unsettled` sentinel; a settled
        // success stores the `Box` (already built as the block's value — and the
        // `Box` disambiguates a success value that is itself a `Throwable` from a
        // real failure, so no separate `failed` flag is needed); a settled
        // failure stores the bare `Throwable`.
        var outcome: Any = AsyncJsRuntime.Unsettled
        val p            = scala.scalajs.js.async {
          try {
            val v   = $rewritten
            val box = new AsyncJsRuntime.Box[A](v)
            outcome = box
            box
          } catch {
            case t: Throwable =>
              outcome = t
              throw t
          }
        }
        // Read once, synchronously after `js.async` returns: still `Unsettled`
        // iff the body suspended — late writes from a resumed body happen after
        // this read and publish via the promise instead.
        val o = outcome
        if (o.asInstanceOf[AnyRef] eq AsyncJsRuntime.Unsettled) AsyncJsRuntime.fromBoxedPromise[A](p)
        else
          o match {
            case t: Throwable =>
              AsyncJsRuntime.discardRejection(p) // reported via readyFailure instead
              AsyncJsRuntime.readyFailure[A](t)
            case box =>
              Async.succeed(box.asInstanceOf[AsyncJsRuntime.Box[A]].value)
          }
      }
    }
  }
}
