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
 * Shares the same `awaitImpl` / `asyncImpl` macro entry points as the DCA
 * implementation, so the package-object syntax (`AsyncSyntaxVersionSpecific`,
 * `AsyncCompanionVersionSpecific`) is identical across every Scala 3 cell.
 */
private[async] object AsyncDirect {

  /**
   * `qual.await` → `js.await(toJsPromise(qual))` for a suspended value; a ready
   * value short-circuits with no `Promise` allocation. Fires only for `.await`
   * calls left in place by [[asyncImpl]]'s native fast path (the DCA fallback
   * rewrites them to `cps.await` before this inline expands); used outside an
   * `Async.async` block it is a Scala.js compile error ("Illegal use of
   * js.await()").
   */
  def awaitImpl[A: Type](self: Expr[Async[A]])(using Quotes): Expr[A] =
    '{
      val r: Any = $self
      if (r.isInstanceOf[Pollable[?]])
        scala.scalajs.js.await(AsyncInterop.toJsPromise[A](r.asInstanceOf[Async[A]]))
      else AsyncEncoding.deliverSuccess[A](r)
    }

  /**
   * `Async.async { body }` → `fromJsPromise(js.async { body })` when every
   * `.await` is in direct position, [[AsyncDcaTransform.asyncImpl]] when any
   * `.await` sits under a lambda / by-name argument / nested method, and a
   * plain `Async.attempt` when the body contains no `.await` at all (zero
   * suspension, no `Promise` round-trip).
   */
  def asyncImpl[A: Type](body: Expr[A])(using Quotes): Expr[Async[A]] = {
    import quotes.reflect.*

    var hasAwait    = false
    var hasIndirect = false

    def isAwaitCall(t: Tree): Boolean = t match {
      case Apply(TypeApply(fun, _), _) if isAwait(fun) => true
      case _                                           => false
    }

    /**
     * Scan with a context flag: `indirect` is true once we are under any
     * construct `js.await` may not cross (lambda/nested method bodies appear as
     * `DefDef`s, match-lambdas and local classes as `ClassDef`s, and by-name
     * arguments are detected from the applied method's parameter types).
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
    else '{ AsyncInterop.fromJsPromise[A](scala.scalajs.js.async($body)) }
  }

  /**
   * Does `fun` reference our `.await` extension method? Matched by '''symbol'''
   * (name + owner `zio.blocks.async.AsyncSyntaxVersionSpecific`), not by name
   * alone, so a user method that happens to be called `await` is never
   * miscounted (and the DCA backend rewrites on this same predicate — keep them
   * identical).
   */
  private def isAwait(using Quotes)(fun: quotes.reflect.Term): Boolean = {
    import quotes.reflect.*
    val nameMatches = fun match {
      case Ident("await")     => true
      case Select(_, "await") => true
      case _                  => false
    }
    nameMatches && {
      val sym = fun.symbol
      sym != Symbol.noSymbol && sym.owner.fullName == "zio.blocks.async.AsyncSyntaxVersionSpecific"
    }
  }
}
