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
 *
 * Packaged separately from the per-backend [[AsyncDirect]] entry points so the
 * Scala 3.8+ JS backend can fall back to this transform for await shapes the
 * native `js.async`/`js.await` primitives cannot express (awaits under a
 * lambda, by-name argument, or nested method).
 */
private[async] object AsyncDcaTransform {

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

    rejectLazyAwaitVals(body.asTerm)

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

          // `while` with `.await` in the condition or body: DCA's WhileHelper
          // recurses through `flatMap` once per iteration, so a loop whose
          // awaits are synchronously ready grows the stack without bound and
          // overflows on real-world iteration counts (the Scala 2 macro's
          // generated loop is iterative). Rewrite the loop ourselves, exactly
          // like the Scala 2 backend: stay in a tight `while` as long as the
          // condition and body settle synchronously, and fall back to a
          // `flatMap` chain (one recursion per *suspension*, driven by the
          // poll loop with a fresh stack) only when one of them suspends. The
          // condition and body become nested `cps.async` blocks so their own
          // awaits are CPS-rewritten in isolation.
          // `while` with `.await` in the CONDITION (with or without body
          // awaits): the general loop — both cond and body are nested
          // `cps.async` thunks evaluated per turn.
          case While(cond, bodyT) if containsAwait(cond) =>
            given Quotes = owner.asQuotes
            val condT    = transformTerm(cond)(owner)
            val bodyT2   = transformTerm(bodyT)(owner)
            val bodyE    =
              if (bodyT2.tpe <:< TypeRepr.of[Unit])
                '{
                  given cps.CpsTryMonadInstanceContext[Async] = AsyncCpsMonad
                  cps.async[Async](${ bodyT2.asExprOf[Unit] })
                }
              else
                '{
                  given cps.CpsTryMonadInstanceContext[Async] = AsyncCpsMonad
                  cps.async[Async] {
                    val _ = ${ bodyT2.asExpr }
                    ()
                  }
                }
            locally {
              val condE = '{
                given cps.CpsTryMonadInstanceContext[Async] = AsyncCpsMonad
                cps.async[Async](${ condT.asExprOf[Boolean] })
              }
              '{
                given cps.CpsMonadContext[Async] =
                  new cps.CpsTryMonadInstanceContextBody[Async](AsyncCpsMonad)
                val condFn: () => Async[Boolean] = () => $condE
                val bodyFn: () => Async[Unit]    = () => $bodyE
                def loop0(): Async[Unit]         = {
                  var out: Async[Unit] = null.asInstanceOf[Async[Unit]]
                  while (out == null) {
                    val c: Any = condFn()
                    if (c.isInstanceOf[Pollable[?]])
                      // suspended (or failed — flatMap short-circuits a Failure)
                      out = c.asInstanceOf[Async[Boolean]].flatMap { (cv: Boolean) =>
                        if (cv) bodyFn().flatMap((_: Unit) => loop0())
                        else Async.succeed(())
                      }
                    else if (!c.asInstanceOf[Boolean]) out = Async.succeed(())
                    else {
                      val b: Any = bodyFn()
                      if (b.isInstanceOf[Pollable[?]])
                        out = b.asInstanceOf[Async[Unit]].flatMap((_: Unit) => loop0())
                    }
                  }
                  out
                }
                cps.await[Async, Unit, Async](loop0())
              }.asTerm
            }

          // `while` with `.await` only in the BODY (the common
          // `while (i < n) { ... fa.await ... }` shape): the condition is read
          // directly each turn — no per-iteration `cps.async` thunk for it.
          case While(cond, bodyT) if containsAwait(bodyT) =>
            given Quotes = owner.asQuotes
            val condT    = transformTerm(cond)(owner)
            val bodyT2   = transformTerm(bodyT)(owner)
            val bodyE    =
              if (bodyT2.tpe <:< TypeRepr.of[Unit])
                '{
                  given cps.CpsTryMonadInstanceContext[Async] = AsyncCpsMonad
                  cps.async[Async](${ bodyT2.asExprOf[Unit] })
                }
              else
                '{
                  given cps.CpsTryMonadInstanceContext[Async] = AsyncCpsMonad
                  cps.async[Async] {
                    val _ = ${ bodyT2.asExpr }
                    ()
                  }
                }
            '{
              given cps.CpsMonadContext[Async] =
                new cps.CpsTryMonadInstanceContextBody[Async](AsyncCpsMonad)
              val bodyFn: () => Async[Unit] = () => $bodyE
              def loop0(): Async[Unit]      = {
                var out: Async[Unit] = null.asInstanceOf[Async[Unit]]
                while (out == null) {
                  if (!${ condT.asExprOf[Boolean] }) out = Async.succeed(())
                  else {
                    val b: Any = bodyFn()
                    if (b.isInstanceOf[Pollable[?]])
                      out = b.asInstanceOf[Async[Unit]].flatMap((_: Unit) => loop0())
                  }
                }
                out
              }
              cps.await[Async, Unit, Async](loop0())
            }.asTerm

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

  /**
   * True when `tree` contains one of our (not-yet-rewritten) `.await` calls.
   */
  private[internal] def containsAwait(using Quotes)(tree: quotes.reflect.Tree): Boolean = {
    import quotes.reflect.*
    val acc = new TreeAccumulator[Boolean] {
      def foldTree(found: Boolean, t: Tree)(owner: Symbol): Boolean =
        if (found) true
        else
          t match {
            case Apply(TypeApply(fun, List(_)), List(_)) if isAwait(fun) => true
            case _                                                       => foldOverTree(found, t)(owner)
          }
    }
    acc.foldTree(false, tree)(Symbol.spliceOwner)
  }

  /**
   * Reject `lazy val`s whose initializer awaits, with a named diagnostic
   * (parity with the Scala 2 macro). `lazy val` initializers must not run
   * eagerly, but no backend can suspend lazy initialization: DCA silently
   * forces the initializer at declaration, and native `js.await` rejects the
   * shape with an error naming machinery the user never wrote. Shared by every
   * Scala 3 backend.
   */
  private[internal] def rejectLazyAwaitVals(using Quotes)(root: quotes.reflect.Tree): Unit = {
    import quotes.reflect.*
    new TreeTraverser {
      override def traverseTree(tree: Tree)(owner: Symbol): Unit = {
        tree match {
          case vd: ValDef if vd.symbol.flags.is(Flags.Lazy) && containsAwait(vd) =>
            report.errorAndAbort(
              "`.await` inside a `lazy val` is not supported (suspending lazy initialization is not supported); " +
                "use a strict `val` inside `Async.async`.",
              vd.pos
            )
          case _ => ()
        }
        traverseTreeChildren(tree)(owner)
      }
    }.traverseTree(root)(Symbol.spliceOwner)
  }

  /**
   * Does `fun` reference our `.await` extension method? Matched purely by
   * '''symbol''' (declared name + owner): the call-site spelling cannot be
   * trusted in either direction — a user method that happens to be called
   * `await` must never be hijacked, and our extension reached through an import
   * rename (`import zio.blocks.async.{await => waitFor}`) must still be
   * rewritten.
   */
  private[internal] def isAwait(using Quotes)(fun: quotes.reflect.Term): Boolean = {
    import quotes.reflect.*
    val sym = fun.symbol
    sym != Symbol.noSymbol &&
    sym.name == "await" &&
    sym.owner.fullName == "zio.blocks.async.AsyncSyntaxVersionSpecific"
  }
}
