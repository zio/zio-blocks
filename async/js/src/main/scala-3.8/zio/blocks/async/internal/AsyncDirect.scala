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
 * Scala.js 3.8+ direct-style implementation of `Async.async { ... .await ... }`,
 * backed by the JavaScript-native `js.async` / `js.await` primitives (Scala.js
 * 1.19+, ES2017 target) rather than dotty-cps-async.
 *
 * `js.async`/`js.await` compile to real JavaScript `async`/`await`, which is the
 * fastest suspension mechanism available on the platform — faster than the
 * DCA `flatMap`-chain path used on older Scala 3 JS cells. The lexical
 * restriction on `.await` (only inside an `Async.async` block, not in a lambda /
 * by-name / nested method) is enforced natively by the Scala.js compiler.
 *
 * Shares the same `awaitImpl` / `asyncImpl` macro entry points as the DCA
 * implementation (`shared/src/main/scala-3-dca`), so the package-object syntax
 * (`AsyncSyntaxVersionSpecific`, `AsyncCompanionVersionSpecific`) is identical
 * across every Scala 3 cell.
 */
private[async] object AsyncDirect {

  /**
   * `qual.await` → `js.await(toJsPromise(qual))` for a suspended value; a ready
   * value short-circuits with no `Promise` allocation. The emitted `js.await`
   * lands directly inside the enclosing `js.async` block (via [[asyncImpl]]), so
   * Scala.js accepts it; used outside an `Async.async` block it is a Scala.js
   * compile error ("Illegal use of js.await()").
   */
  def awaitImpl[A: Type](self: Expr[Async[A]])(using Quotes): Expr[A] =
    '{
      val r: Any = $self
      if (r.isInstanceOf[Pollable[?]])
        scala.scalajs.js.await(AsyncInterop.toJsPromise[A](r.asInstanceOf[Async[A]]))
      else r.asInstanceOf[A]
    }

  /**
   * `Async.async { body }` → `fromJsPromise(js.async { body })`. The body's
   * inlined `.await`s expand to `js.await` calls lexically inside the `js.async`
   * block. When the body contains no `.await`, we skip the `Promise` round-trip
   * entirely and lift the (synchronous) result via [[Async.attempt]], preserving
   * the zero-suspension fast path.
   */
  def asyncImpl[A: Type](body: Expr[A])(using Quotes): Expr[Async[A]] = {
    import quotes.reflect.*

    var hasAwait = false
    val finder = new TreeTraverser {
      override def traverseTree(t: Tree)(owner: Symbol): Unit =
        t match {
          case Apply(TypeApply(fun, _), _) if isAwait(fun) => hasAwait = true
          case _                                           => super.traverseTree(t)(owner)
        }
    }
    finder.traverseTree(body.asTerm)(Symbol.spliceOwner)

    if (hasAwait)
      '{ AsyncInterop.fromJsPromise[A](scala.scalajs.js.async { $body }) }
    else
      '{ Async.attempt[A]($body) }
  }

  /** Does `fun` reference our `.await` extension method? */
  private def isAwait(using Quotes)(fun: quotes.reflect.Term): Boolean = {
    import quotes.reflect.*
    fun match {
      case Ident("await")     => true
      case Select(_, "await") => true
      case _                  => false
    }
  }
}
