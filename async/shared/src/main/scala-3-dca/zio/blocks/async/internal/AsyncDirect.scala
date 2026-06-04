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
 * Compile-time machinery for the Scala 3 direct-style surface: `Async.async {
 * ... .await ... }`.
 *
 * The design follows the dotty-cps-async delegation pattern used by Kyo's
 * `direct`/`.now`: DCA's macro only recognizes its own `cps.await` token, so we
 * keep full control of the user-facing spelling by:
 *
 *   1. exposing our own `.await` operator that, on its own, is a marker macro
 *      ([[awaitImpl]]) which fails to compile — it is only legal once the
 *      enclosing `Async.async` macro has rewritten it away;
 *   2. having [[asyncImpl]] walk the block body, replace every `qual.await`
 *      with a real `cps.await[Async, T, Async](qual)`, then splice the
 *      rewritten body into a genuine `cps.async[Async] { ... }`.
 *
 * Because DCA never sees our `.await` token (only the `cps.await` we emit), the
 * public API surface stays exactly `Async.async` + `.await`; users never touch
 * `cps.*`.
 */
private[async] object AsyncDirect {

  /**
   * Expansion of a bare `.await` that survived to code generation — i.e. one
   * used outside any `Async.async { ... }` block. Always a compile error. This
   * is what gives `.await` its lexical restriction.
   */
  def awaitImpl[A: Type](self: Expr[Async[A]])(using Quotes): Expr[A] = {
    import quotes.reflect.*
    report.errorAndAbort(
      "`.await` may only be used directly inside an `Async.async { ... }` block.",
      self.asTerm.pos
    )
  }

  /**
   * Rewrite an `Async.async { body }` block. Every `.await` inside `body` is
   * turned into a `cps.await`, and the whole body is wrapped in a real
   * `cps.async[Async]`. The [[AsyncCpsMonad]] instance is brought into scope so
   * DCA can summon `CpsMonad[Async]`.
   */
  def asyncImpl[A: Type](body: Expr[A])(using Quotes): Expr[Async[A]] = {
    import quotes.reflect.*

    val transformer = new TreeMap {
      override def transformTerm(term: Term)(owner: Symbol): Term =
        term match {
          // Our `.await` extension elaborates to `await[T](qual)`:
          //   Apply(TypeApply(Ident("await"), List(tTpe)), List(qual))
          case Apply(TypeApply(fun, List(tTpe)), List(qual)) if isAwait(fun) =>
            tTpe.tpe.asType match {
              case '[t] =>
                given Quotes = owner.asQuotes
                val q        = transformTerm(qual)(owner).asExprOf[Async[t]]
                // Supply the monad context locally so the `cps.await` node
                // type-checks; the enclosing `cps.async` macro then recognizes
                // and rewrites it (the context here is only for elaboration).
                '{
                  given cps.CpsMonadContext[Async] =
                    new cps.CpsTryMonadInstanceContextBody[Async](AsyncCpsMonad)
                  cps.await[Async, t, Async]($q)
                }.asTerm
            }
          case _ => super.transformTerm(term)(owner)
        }
    }

    val rewritten = transformer.transformTerm(body.asTerm)(Symbol.spliceOwner).asExprOf[A]

    '{
      given cps.CpsTryMonadInstanceContext[Async] = AsyncCpsMonad
      cps
        .async[Async] {
          $rewritten
        }
        .asInstanceOf[Async[A]]
    }
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
