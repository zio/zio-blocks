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

import scala.reflect.macros.blackbox

/**
 * Scala 2.13 direct-style macro powering `Async.async { ... .await ... }`.
 *
 * This is the Scala 2 counterpart of the Scala 3 dotty-cps-async / js-native
 * backends. It is a single-monad CPS/ANF source-to-source transform (modelled
 * on scala-async / monadless, but specialised to our one known `Async` monad —
 * which removes ~80% of a generic `F[_]` rewriter's complexity).
 *
 * ==Why `.await` is not itself a macro on Scala 2==
 *
 * Scala 2 expands macros bottom-up: the argument to `Async.async` is fully
 * typechecked — expanding any inner macros — *before* the `async` macro runs. A
 * marker-macro `.await` would therefore abort before `async` could rewrite it.
 * Instead `.await` is a plain `@compileTimeOnly` method (see
 * `AsyncSyntaxVersionSpecific`): the `async` macro detects and removes every
 * `.await` call, so inside a block it never reaches the `@compileTimeOnly`
 * refchecks phase; used anywhere else it survives and produces a compile error.
 *
 * ==Pipeline==
 *
 *   1. `boxVars` rewrites every block-local mutable `var` into an immutable
 *      `scala.runtime.*Ref` cell (so generated `flatMap` closures only ever
 *      capture immutable vals — Scala 2's LambdaLift crashes GenBCode when
 *      asked to box a captured mutable local in macro-generated code).
 *   2. The whole body is `untypecheck`ed so the transform works purely
 *      syntactically and the compiler re-typechecks (and assigns fresh owners
 *      to) the expansion — no hand-managed symbol owners.
 *   3. `transform(tree)(k)` is continuation-passing: `k` receives a *pure
 *      value* tree and returns the final `Async` tree. Every synchronous
 *      segment is wrapped in `try { ... } catch Async.fail` so a thrown
 *      exception becomes `Async.fail`; `.await` becomes
 *      `qual.flatMap(a => k(a))` so an awaited `Async.fail` short-circuits.
 *      Awaits run in strict source order (left-to-right ANF let-insertion).
 *   4. The result is ascribed `.asInstanceOf[Async[A]]` (as the Scala 3 backend
 *      also does) so intermediate `Try`-erased value types do not leak.
 */
private[async] object AsyncMacros {

  def asyncImpl[A: c.WeakTypeTag](c: blackbox.Context)(body: c.Expr[A]): c.Expr[zio.blocks.async.Async[A]] = {
    import c.universe._

    val AsyncObj = q"_root_.zio.blocks.async.Async"
    val OpsObj   = q"_root_.zio.blocks.async.AsyncOps"
    val Pollable = tq"_root_.zio.blocks.async.Pollable[_]"

    def fresh(p: String): TermName = TermName(c.freshName(p))

    // ---- await detection (syntactic, on the untyped tree) -------------------

    def isAwaitName(n: Name): Boolean = n.decodedName.toString == "await"

    // `untypecheck` drops the inferred `AsyncOps(..)` implicit view, leaving a
    // bare `fa.await`; we still unwrap an explicit `AsyncOps(fa)` defensively.
    def unwrap(qual: Tree): Tree = qual match {
      case Apply(fun, List(fa)) if fun.toString.endsWith("AsyncOps")               => fa
      case Apply(TypeApply(fun, _), List(fa)) if fun.toString.endsWith("AsyncOps") => fa
      case other                                                                   => other
    }

    // In the *typed* tree a legitimate `.await` is always the `AsyncOps`
    // implicit-class method, i.e. `AsyncOps(fa).await`. A `.await` that is NOT
    // `AsyncOps`-wrapped is some other user method named `await`; the untyped
    // transform cannot tell them apart (untypecheck strips the wrapper), so we
    // detect and reject those against the typed tree (see `awaitElemTypes`).
    def isAsyncOpsWrap(qual: Tree): Boolean = qual match {
      case Apply(fun, List(_)) if fun.toString.endsWith("AsyncOps")               => true
      case Apply(TypeApply(fun, _), List(_)) if fun.toString.endsWith("AsyncOps") => true
      case _                                                                      => false
    }

    object AwaitCall {
      def unapply(t: Tree): Option[Tree] = t match {
        case Select(qual, n) if isAwaitName(n)             => Some(unwrap(qual))
        case Apply(Select(qual, n), Nil) if isAwaitName(n) => Some(unwrap(qual))
        case _                                             => None
      }
    }

    def containsAwait(t: Tree): Boolean = {
      var found = false
      new Traverser {
        override def traverse(tt: Tree): Unit =
          if (!found) tt match {
            case AwaitCall(_) => found = true
            case _            => super.traverse(tt)
          }
      }.traverse(t)
      found
    }

    // ---- higher-order-function (HOF) closure awaits (Phase 5c) ---------------
    //
    // `.await` inside a `List` HOF closure is rewritten to match the Scala 3
    // cells (DCA on JVM / older JS, native `js.await` on JS 3.8+) EXACTLY. The
    // per-HOF semantics differ — and were each verified empirically against all
    // three Scala 3 backends — so each HOF has its own emit strategy:
    //
    //   - `List.map`: EAGER. Strict `map` applies the CPS-transformed closure to
    //     every element first (building `List[Async[B]]`), then sequences the
    //     awaits left-to-right via `Async.collectAll` (fail-fast). Matches DCA's
    //     eager `ListAsyncShift.map` and `Array.map(async ...)` in JS. See
    //     `emitHofMap`.
    //   - `List.foreach`: LAZY / sequential. The closure for element `n+1` runs
    //     only after element `n`'s await completes successfully; a failed await
    //     short-circuits the rest. Matches DCA's sequential
    //     `IterableAsyncShift.foreach` and the JS-native loop suspension. See
    //     `emitHofForeach`.
    //   - `List.flatMap`: LAZY / sequential (like `foreach`) but accumulating —
    //     each element's closure yields an `IterableOnce[B]` that is concatenated
    //     into the result `List[B]`. The closure for element `n+1` runs only
    //     after element `n`'s await completes; a failed await short-circuits the
    //     rest. Enables multi-generator for-comprehensions. See `emitHofFlatMap`.
    //
    // Supported HOFs are whitelisted by method name here AND validated by
    // receiver type in `hofElemTypes` (typed pass).
    val supportedHofMethods: Set[String] =
      Set("map", "foreach", "flatMap", "find", "exists", "forall", "filter", "filterNot", "takeWhile", "dropWhile")

    /**
     * Whitelisted HOFs whose semantics are PREFIX-ORDERED: `takeWhile` keeps the
     * longest leading run satisfying the predicate, `dropWhile` drops it. These
     * are only meaningful on an ordered (`Seq`-like) receiver — on an unordered
     * `Set` / `Map` the "prefix" is implementation-defined — so the typed pass
     * restricts them to `Seq` receivers (`List` / `Vector`), rejecting `Set` /
     * `Map` / `Option` with a good position.
     */
    val prefixOrderedHofMethods: Set[String] = Set("takeWhile", "dropWhile")

    /**
     * Single-argument function literal, unwrapping a `Block(Nil, Function)`
     * that `untypecheck` occasionally introduces.
     */
    object SingleArgFunction {
      def unapply(t: Tree): Option[(ValDef, Tree)] = t match {
        case Function(List(p), fbody)             => Some((p, fbody))
        case Block(Nil, Function(List(p), fbody)) => Some((p, fbody))
        case _                                    => None
      }
    }

    /**
     * Two-argument function literal (the `op: (B, A) => B` of `foldLeft`),
     * unwrapping a `Block(Nil, Function)` that `untypecheck` may introduce.
     */
    object TwoArgFunction {
      def unapply(t: Tree): Option[(ValDef, ValDef, Tree)] = t match {
        case Function(List(p1, p2), fbody)             => Some((p1, p2, fbody))
        case Block(Nil, Function(List(p1, p2), fbody)) => Some((p1, p2, fbody))
        case _                                         => None
      }
    }

    /**
     * A `recv.foldLeft(z)(op)` call whose two-argument `op` body contains a
     * `.await`. `foldLeft` is curried (`foldLeft[B](z: B)(op: (B, A) => B)`),
     * so the tree is a double `Apply` (optionally with a `TypeApply` for
     * `[B]`). Matched only when the OP BODY awaits — a `.await` solely in `z`
     * is an ordinary application-spine await handled generically.
     *
     * `foldLeft` is receiver-agnostic: it is a strict left fold over the
     * receiver's iteration order, so any `IterableOnce` works through
     * `.iterator`. Yields `(recv, z, accParam, elemParam, opBody)`.
     */
    object FoldLeftAwaitCall {
      def unapply(t: Tree): Option[(Tree, Tree, ValDef, ValDef, Tree)] = t match {
        case Apply(Apply(Select(recv, m), List(z)), List(fn))               => check(recv, m, z, fn)
        case Apply(Apply(TypeApply(Select(recv, m), _), List(z)), List(fn)) => check(recv, m, z, fn)
        case _                                                              => None
      }
      private def check(recv: Tree, m: Name, z: Tree, fn: Tree): Option[(Tree, Tree, ValDef, ValDef, Tree)] =
        if (m.decodedName.toString != "foldLeft") None
        else
          fn match {
            case TwoArgFunction(acc, x, fbody) if containsAwait(fbody) => Some((recv, z, acc, x, fbody))
            case _                                                     => None
          }
    }

    /**
     * A `recv.reduce(op)` / `recv.reduceLeft(op)` call whose two-argument `op`
     * body contains a `.await`. Unlike `foldLeft`, `reduce` takes no initial
     * value — it is seeded by the FIRST element and folds the rest left-to-right
     * (`reduce` on an ordered/iterable collection is `reduceLeft`), and an empty
     * receiver throws `UnsupportedOperationException`. The tree is a single
     * `Apply` (optionally with a `TypeApply` for the `[B >: A]` widening).
     * Matched only when the OP BODY awaits. Yields `(recv, accParam, elemParam,
     * opBody)`.
     */
    object ReduceAwaitCall {
      def unapply(t: Tree): Option[(Tree, ValDef, ValDef, Tree)] = t match {
        case Apply(Select(recv, m), List(fn))               => check(recv, m, fn)
        case Apply(TypeApply(Select(recv, m), _), List(fn)) => check(recv, m, fn)
        case _                                              => None
      }
      private def check(recv: Tree, m: Name, fn: Tree): Option[(Tree, ValDef, ValDef, Tree)] = {
        val ms = m.decodedName.toString
        if (ms != "reduce" && ms != "reduceLeft") None
        else
          fn match {
            case TwoArgFunction(acc, x, fbody) if containsAwait(fbody) => Some((recv, acc, x, fbody))
            case _                                                     => None
          }
      }
    }

    /**
     * A `recv.foldRight(z)(op)` call whose two-argument `op` body contains a
     * `.await`. Like `foldLeft` it is curried (`foldRight[B](z: B)(op: (A, B) =>
     * B)`), so the tree is a double `Apply` (optionally with a `TypeApply` for
     * `[B]`), but it is RIGHT-associative: the op runs right-to-left
     * (`op(x1, op(x2, ..., op(xn, z)))`), so the rightmost element's await
     * happens first (empirically confirmed against DCA). The op's FIRST
     * parameter is the element, the SECOND is the accumulator (opposite of
     * `foldLeft`). Matched only when the OP BODY awaits. Yields
     * `(recv, z, elemParam, accParam, opBody)`.
     */
    object FoldRightAwaitCall {
      def unapply(t: Tree): Option[(Tree, Tree, ValDef, ValDef, Tree)] = t match {
        case Apply(Apply(Select(recv, m), List(z)), List(fn))               => check(recv, m, z, fn)
        case Apply(Apply(TypeApply(Select(recv, m), _), List(z)), List(fn)) => check(recv, m, z, fn)
        case _                                                              => None
      }
      private def check(recv: Tree, m: Name, z: Tree, fn: Tree): Option[(Tree, Tree, ValDef, ValDef, Tree)] =
        if (m.decodedName.toString != "foldRight") None
        else
          fn match {
            case TwoArgFunction(x, acc, fbody) if containsAwait(fbody) => Some((recv, z, x, acc, fbody))
            case _                                                     => None
          }
    }

    /**
     * A partial-function literal `{ case ... }`. After `untypecheck` (and in the
     * typed tree) a PF literal is an anonymous `AbstractPartialFunction`
     * subclass: `Typed(Block(List(ClassDef($anonfun)), New $anonfun()), _)`,
     * whose `applyOrElse` method holds a `Match` over the user `case`s plus one
     * trailing SYNTHETIC fallthrough (`case defaultCase$ => default.apply(x1)`).
     * Unwraps to the USER cases (the synthetic default dropped). Yields
     * `List[CaseDef]`.
     */
    object PartialFunctionLiteral {
      def unapply(t: Tree): Option[List[CaseDef]] = {
        val inner = t match {
          case Typed(e, _) => e
          case _           => t
        }
        inner match {
          case Block(stats, _) =>
            stats.collectFirst { case cd: ClassDef => cd }.flatMap { cd =>
              cd.impl.body.collectFirst {
                case dd: DefDef if dd.name.decodedName.toString == "applyOrElse" => dd
              }.flatMap { dd =>
                dd.rhs match {
                  case Match(_, cases) => Some(cases.filterNot(isSyntheticDefault))
                  case _               => None
                }
              }
            }
          case _ => None
        }
      }
      private def isSyntheticDefault(cd: CaseDef): Boolean = cd.pat match {
        case Bind(n, Ident(termNames.WILDCARD)) => n.decodedName.toString.startsWith("defaultCase$")
        case _                                  => false
      }
    }

    /**
     * A `recv.collect(pf)` call whose partial-function `pf` has at least one
     * `case` BODY containing a `.await`. `collect` keeps the elements the `pf`
     * is defined at, mapping them through it. Matched only when a case body
     * awaits (a `.await` solely in the receiver is handled generically). Yields
     * `(recv, userCases)`.
     */
    object CollectAwaitCall {
      def unapply(t: Tree): Option[(Tree, List[CaseDef])] = t match {
        case Apply(Select(recv, m), List(pf))               => check(recv, m, pf)
        case Apply(TypeApply(Select(recv, m), _), List(pf)) => check(recv, m, pf)
        case _                                              => None
      }
      private def check(recv: Tree, m: Name, pf: Tree): Option[(Tree, List[CaseDef])] =
        if (m.decodedName.toString != "collect") None
        else
          pf match {
            case PartialFunctionLiteral(cases) if cases.exists(cd => containsAwait(cd.body)) => Some((recv, cases))
            case _                                                                           => None
          }
    }

    /** The erased `List[Any]` type, used to validate HOF receivers. */
    val ListAnyTpe: Type = typeOf[List[Any]]

    /** The erased `Option[Any]` type, used to validate HOF receivers. */
    val OptionAnyTpe: Type = typeOf[Option[Any]]

    /**
     * The erased immutable `Seq[Any]` type, used to restrict the prefix-ordered
     * HOFs (`takeWhile` / `dropWhile`) to ordered receivers (`List` / `Vector`).
     */
    val SeqAnyTpe: Type = typeOf[scala.collection.immutable.Seq[Any]]

    /**
     * Erased types for the additional STRICT standard collections the HOF
     * rewrite supports through the generic builder-drain emit (`emitCollMap` /
     * `emitCollFlatMap`, plus the already-generic `emitHofForeach`). These are
     * deliberately whitelisted (not a catch-all `Iterable`): lazy / one-shot
     * collections (`LazyList`, `View`, `Iterator`) and broad interface static
     * types are excluded because a strict iterator drain would change their
     * semantics.
     */
    val VectorAnyTpe: Type = typeOf[Vector[Any]]
    // Additional strict immutable `Seq` families (covariant, so `<:<` works):
    // ordered and builder-backed via `iterableFactory`, exactly like `Vector`.
    val QueueAnyTpe: Type    = typeOf[scala.collection.immutable.Queue[Any]]
    val ArraySeqAnyTpe: Type = typeOf[scala.collection.immutable.ArraySeq[Any]]
    // `scala.collection.immutable.Set` is INVARIANT, so `Set[Int] <:< Set[Any]`
    // is false; classify it by base-type symbol instead (variance-agnostic).
    val SetSym: Symbol = typeOf[scala.collection.immutable.Set[Any]].typeSymbol
    // `scala.collection.immutable.Map` is INVARIANT in its key type, so
    // `Map[Int, String] <:< Map[Any, Any]` is false; classify by base-type
    // symbol (variance-agnostic), exactly like `Set`.
    val MapSym: Symbol = typeOf[scala.collection.immutable.Map[Any, Any]].typeSymbol

    /**
     * A (possibly nested) `recv.withFilter(g)` chain — the desugaring of
     * for-comprehension guards (`for { x <- xs if g(x) } ...` becomes
     * `xs.withFilter(x => g(x)).map/flatMap/foreach(...)`). Unwraps to the
     * underlying receiver and the guards in source order (outermost last), so
     * `xs.withFilter(a).withFilter(b)` yields `(xs, List(a, b))`.
     */
    object WithFilterChain {
      def unapply(t: Tree): Option[(Tree, List[Tree])] = t match {
        case Apply(Select(inner, n), List(g)) if n.decodedName.toString == "withFilter" =>
          inner match {
            case WithFilterChain(base, gs) => Some((base, gs :+ g))
            case _                         => Some((inner, List(g)))
          }
        case _ => None
      }
    }

    /**
     * Classify the HOF receiver type (looking through a `withFilter` chain, the
     * for-comprehension-guard desugaring, which we later materialize to a
     * strict `filter`). The HOF rewrite is collection-specific, so each
     * supported receiver kind has its own emit strategy:
     *   - `"list"` — any `List` (eager `map`, lazy `foreach`/`flatMap`).
     *   - `"option"` — any `Option` (single element: eager/lazy collapse, so
     *     `map`/`flatMap`/`foreach` all reduce to a `Some`/`None` branch).
     *   - `"iterable"` — a whitelisted strict standard collection (`Vector`,
     *     immutable `Set`): lazy / sequential `map`/`flatMap`/`foreach`, with
     *     the result collection type preserved via the receiver's own builder.
     *   - `"map"` — an immutable `Map` (entries are `(K, V)` tuples): lazy /
     *     sequential `map`/`flatMap`/`foreach`. `map`/`flatMap` rebuild a
     *     `Map[K2, V2]` via the receiver's `mapFactory` when the closure yields
     *     pairs, and fall back to the generic `iterableFactory` builder when it
     *     yields non-pair elements (matching standard-library overload choice).
     * Returns `None` for an unsupported receiver, which the caller rejects.
     *
     * Order matters: `List` is checked before the generic `"iterable"` branch
     * because `List.map` is the special EAGER case (DCA's `ListAsyncShift`),
     * whereas `Vector`/`Set` `map` is lazy/sequential on every backend
     * (verified empirically against all three Scala 3 backends).
     */
    def receiverKind(recv: Tree): Option[String] = {
      val underlying = recv match {
        case WithFilterChain(base, _) => base
        case _                        => recv
      }
      val t = if (underlying.tpe != null) underlying.tpe.dealias.widen else NoType
      if (t == NoType) None
      else if (t <:< ListAnyTpe) Some("list")
      else if (t <:< OptionAnyTpe) Some("option")
      else if (t.baseType(MapSym) != NoType) Some("map")
      else if (
        t <:< VectorAnyTpe || t <:< QueueAnyTpe || t <:< ArraySeqAnyTpe || t.baseType(SetSym) != NoType
      ) Some("iterable")
      else None
    }

    /**
     * Replace `recv.withFilter(g)` chains with strict `recv.filter(g)` so the
     * emitted HOF rewrite can iterate the (now eager `List`) receiver. The
     * guard is pure (awaits in a guard are rejected by `precheck`), so
     * `withFilter` and `filter` agree on the result; we only lose
     * `withFilter`'s laziness, which is unobservable here because we consume
     * the whole collection anyway.
     */
    def defilterReceiver(recv: Tree): Tree = recv match {
      case WithFilterChain(base, guards) => guards.foldLeft(base)((acc, g) => q"$acc.filter($g)")
      case _                             => recv
    }

    /**
     * A whitelisted HOF call whose single-argument closure body contains a
     * `.await`. Matches both the untyped `Apply(Select(recv, m), fn)` shape and
     * the typed `Apply(TypeApply(Select(recv, m), _), fn)` shape.
     */
    object HofAwaitCall {
      def unapply(t: Tree): Option[(Tree, String, ValDef, Tree)] = t match {
        case Apply(Select(recv, m), List(fn))               => check(recv, m, fn)
        case Apply(TypeApply(Select(recv, m), _), List(fn)) => check(recv, m, fn)
        case _                                              => None
      }
      private def check(recv: Tree, m: Name, fn: Tree): Option[(Tree, String, ValDef, Tree)] = {
        val ms = m.decodedName.toString
        if (!supportedHofMethods(ms)) None
        else
          fn match {
            case SingleArgFunction(p, fbody) if containsAwait(fbody) => Some((recv, ms, p, fbody))
            case _                                                   => None
          }
      }
    }

    // ---- await element types (from the TYPED body, in transform order) ------
    //
    // The transform runs on an `untypecheck`ed tree, so it cannot read the
    // element type of an awaited `Async[A]`. We recover those types here from
    // the still-typed `body.tree`: the `.await` node's own `tpe` is exactly `A`
    // (its signature is `def await: A`). We record them in the SAME order the
    // CPS transform consumes them — inner awaits before the enclosing await
    // (because `transform(fa)` runs before the outer `asyncBind`) — and the
    // transform dequeues one per real `.await`.
    //
    // This traversal is also where we reject a non-Async `.await` (a user method
    // named `await` that is not the `AsyncOps` extension): such a call is not
    // distinguishable from a real await once the tree is untypechecked.
    val awaitElemTypes: scala.collection.mutable.Queue[Type] = {
      val q                                  = scala.collection.mutable.Queue.empty[Type]
      def record(tt: Tree, qual: Tree): Unit = {
        if (!isAsyncOpsWrap(qual))
          c.abort(
            tt.pos,
            "`Async.async` reserves `.await` for `zio.blocks.async.Async`; a different method named `await` is " +
              "not supported inside the block. Rename it, or move the call outside `Async.async`."
          )
        traverse(qual) // inner awaits first (matches transform order)
        q.enqueue(if (tt.tpe != null) tt.tpe.dealias.widen else NoType)
      }
      lazy val traverser: Traverser = new Traverser {
        override def traverse(tt: Tree): Unit = tt match {
          case Select(qual, n) if isAwaitName(n)             => record(tt, qual)
          case Apply(Select(qual, n), Nil) if isAwaitName(n) => record(tt, qual)
          case _                                             => super.traverse(tt)
        }
      }
      def traverse(t: Tree): Unit = traverser.traverse(t)
      traverse(body.tree)
      q
    }

    // ---- HOF result element types (from the TYPED body, in transform order) --
    //
    // For each whitelisted HOF-closure await (e.g. `List[T].map(x => ...await)`)
    // we record the RESULT element type `B` (so `recv.map(...)` has result
    // `List[B]`), recovered from the typed call node's `tpe` (`List[B]`). This
    // is the only rep-aware type the untyped HOF rewrite needs; the closure
    // parameter type is recovered by binding it to `iterator.next()`.
    //
    // This is also where the RECEIVER type is validated and CLASSIFIED (via
    // `receiverKind`): the untyped rewrite is collection-specific, so a
    // whitelisted method on an unsupported receiver is rejected here (with good
    // positions) rather than producing an opaque retypecheck error from the
    // emitted code, and the recovered kind (`"list"` / `"option"`) is recorded
    // in `hofRecvKinds` (parallel to `hofElemTypes`) so the untyped dispatch can
    // pick the right emit strategy. The FULL result type (`Vector[B]` / `Set[B]`
    // / `Unit`) is recorded in `hofResultTypes` for the generic `"iterable"`
    // emit, whose recursive drain `def` needs an explicit return type that a
    // collection-family builder's `result()` produces.
    val hofRecvKinds: scala.collection.mutable.Queue[String] = scala.collection.mutable.Queue.empty[String]
    val hofResultTypes: scala.collection.mutable.Queue[Type] = scala.collection.mutable.Queue.empty[Type]
    val hofElemTypes: scala.collection.mutable.Queue[Type]   = {
      val q = scala.collection.mutable.Queue.empty[Type]
      object TypedHofMap {
        def unapply(tt: Tree): Option[(Tree, String, Tree)] = tt match {
          case Apply(Select(recv, m), List(fn)) if isHof(m, fn) => Some((recv, m.decodedName.toString, fn))
          case Apply(TypeApply(Select(recv, m), _), List(fn)) if isHof(m, fn) =>
            Some((recv, m.decodedName.toString, fn))
          case _ => None
        }
        private def isHof(m: Name, fn: Tree): Boolean =
          supportedHofMethods(m.decodedName.toString) && (fn match {
            case SingleArgFunction(_, fbody) => containsAwait(fbody)
            case _                           => false
          })
      }
      lazy val traverser: Traverser = new Traverser {
        override def traverse(tt: Tree): Unit = tt match {
          case TypedHofMap(recv, m, fn) =>
            val kind = receiverKind(recv).getOrElse(
              c.abort(
                tt.pos,
                "`.await` inside a higher-order-function closure is currently supported only for `List`, " +
                  "`Option`, `Vector`, immutable `Set`, and immutable `Map` (and for-comprehension guards over them) " +
                  "in the Scala 2 `Async.async` macro (other collections coming); convert the receiver to one of those " +
                  "first, or bind the awaited values before the lambda."
              )
            )
            // Prefix-ordered HOFs (`takeWhile` / `dropWhile`) are only meaningful
            // on an ordered receiver; reject unordered `Set` / `Map` / `Option`
            // here (typed pass) so the position points at the offending call.
            if (prefixOrderedHofMethods(m)) {
              val underlying = recv match {
                case WithFilterChain(base, _) => base
                case _                        => recv
              }
              val t = if (underlying.tpe != null) underlying.tpe.dealias.widen else NoType
              if (!(t <:< SeqAnyTpe))
                c.abort(
                  tt.pos,
                  s"`.await` inside a `$m` closure is currently supported only for ordered `Seq` receivers " +
                    "(`List` / `Vector`) in the Scala 2 `Async.async` macro: a leading-prefix predicate is " +
                    "ill-defined on an unordered `Set` / `Map` (and `Option` does not provide it). Convert the " +
                    "receiver to a `List` or `Vector` first, or bind the awaited values before the lambda."
                )
            }
            hofRecvKinds.enqueue(kind)
            val resultTpe = if (tt.tpe != null) tt.tpe.dealias.widen else NoType
            hofResultTypes.enqueue(resultTpe)
            // The closure result element type `B`. For a `Map` whose `map`/
            // `flatMap` rebuilds a `Map[K2, V2]` (result has two type args), the
            // closure yields entry tuples `(K2, V2)`, so the element type is that
            // tuple — not `resultTpe.typeArgs.head` (which would be just `K2`).
            val b =
              if (kind == "map" && resultTpe != NoType && resultTpe.typeArgs.lengthCompare(2) == 0)
                appliedType(definitions.TupleClass(2), resultTpe.typeArgs)
              else if (resultTpe != NoType) resultTpe.typeArgs.headOption.getOrElse(NoType)
              else NoType
            q.enqueue(b)
            traverse(recv) // nested HOFs in the receiver, in transform order
            traverse(fn)   // then nested HOFs / awaits in the closure body
          case _ => super.traverse(tt)
        }
      }
      traverser.traverse(body.tree)
      q
    }

    // ---- foldLeft result types (from the TYPED body, in transform order) ----
    //
    // `foldLeft[B](z)(op)` returns `B` directly (not a collection wrapper), so
    // its result type is the typed call node's own `tpe`. The recursive drain
    // `def` the rewrite emits needs an explicit `Async[B]` return type, so we
    // recover `B` here. Recorded in transform order (outer fold before the folds
    // nested in its receiver / initial value / op body), matching the order the
    // dispatch dequeues them. Independent of `hofElemTypes` (single-arg HOFs).
    val foldResultTypes: scala.collection.mutable.Queue[Type] = {
      val q                         = scala.collection.mutable.Queue.empty[Type]
      lazy val traverser: Traverser = new Traverser {
        override def traverse(tt: Tree): Unit = tt match {
          case FoldLeftAwaitCall(recv, z, _, _, fbody) =>
            // SOUNDNESS: `foldLeft` is matched syntactically by method name, so
            // validate the receiver here (typed pass) — the rewrite drains the
            // receiver via `.iterator`, which is only semantics-preserving for
            // the whitelisted standard collections. A custom `foldLeft` (or one
            // over a non-whitelisted receiver) is rejected with a good position
            // rather than silently rewritten into an iterator drain.
            if (receiverKind(recv).isEmpty)
              c.abort(
                tt.pos,
                "`.await` inside a `foldLeft` op closure is currently supported only for `List`, `Option`, " +
                  "`Vector`, immutable `Set`, and immutable `Map` receivers in the Scala 2 `Async.async` macro; " +
                  "convert the receiver to one of those first, or bind the awaited values before the lambda."
              )
            q.enqueue(if (tt.tpe != null) tt.tpe.dealias.widen else NoType)
            traverse(recv)  // nested folds/HOFs in the receiver
            traverse(z)     // nested in the initial accumulator
            traverse(fbody) // nested in the op body
          case _ => super.traverse(tt)
        }
      }
      traverser.traverse(body.tree)
      q
    }

    /** The recorded result type `B` for the next `foldLeft` rewrite. */
    def dequeueFoldResult(): Type =
      if (foldResultTypes.isEmpty)
        c.abort(
          body.tree.pos,
          "internal `Async.async` macro error: rewrote more `foldLeft`s than were typed; please report this."
        )
      else foldResultTypes.dequeue()

    // ---- reduce result types (from the TYPED body, in transform order) ------
    //
    // `reduce[B >: A](op)` / `reduceLeft` returns `B` directly (the element type,
    // possibly widened), recovered from the typed call node's own `tpe`. Like
    // `foldLeft`, the emitted recursive drain `def` needs an explicit `Async[B]`
    // return type. Recorded in transform order and validated the same way (the
    // rewrite drains the receiver via `.iterator`, so the receiver must be a
    // whitelisted standard collection).
    val reduceResultTypes: scala.collection.mutable.Queue[Type] = {
      val q                         = scala.collection.mutable.Queue.empty[Type]
      lazy val traverser: Traverser = new Traverser {
        override def traverse(tt: Tree): Unit = tt match {
          case ReduceAwaitCall(recv, _, _, fbody) =>
            if (receiverKind(recv).isEmpty)
              c.abort(
                tt.pos,
                "`.await` inside a `reduce` / `reduceLeft` op closure is currently supported only for `List`, " +
                  "`Option`, `Vector`, immutable `Set`, and immutable `Map` receivers in the Scala 2 `Async.async` " +
                  "macro; convert the receiver to one of those first, or bind the awaited values before the lambda."
              )
            q.enqueue(if (tt.tpe != null) tt.tpe.dealias.widen else NoType)
            traverse(recv)  // nested folds/HOFs in the receiver
            traverse(fbody) // nested in the op body
          case _ => super.traverse(tt)
        }
      }
      traverser.traverse(body.tree)
      q
    }

    /** The recorded result type `B` for the next `reduce` / `reduceLeft` rewrite. */
    def dequeueReduceResult(): Type =
      if (reduceResultTypes.isEmpty)
        c.abort(
          body.tree.pos,
          "internal `Async.async` macro error: rewrote more `reduce`s than were typed; please report this."
        )
      else reduceResultTypes.dequeue()

    // ---- foldRight result types (from the TYPED body, in transform order) ----
    //
    // `foldRight[B](z)(op)` returns `B` directly, recovered from the typed call
    // node's own `tpe`. Like `foldLeft`, the emitted recursive reverse-drain
    // `def` needs an explicit `Async[B]` return type. Recorded in transform
    // order and validated the same way (the rewrite materializes the receiver
    // via `.toVector` and drains it in reverse, so the receiver must be a
    // whitelisted standard collection).
    val foldRightResultTypes: scala.collection.mutable.Queue[Type] = {
      val q                         = scala.collection.mutable.Queue.empty[Type]
      lazy val traverser: Traverser = new Traverser {
        override def traverse(tt: Tree): Unit = tt match {
          case FoldRightAwaitCall(recv, z, _, _, fbody) =>
            if (receiverKind(recv).isEmpty)
              c.abort(
                tt.pos,
                "`.await` inside a `foldRight` op closure is currently supported only for `List`, `Option`, " +
                  "`Vector`, immutable `Set`, and immutable `Map` receivers in the Scala 2 `Async.async` macro; " +
                  "convert the receiver to one of those first, or bind the awaited values before the lambda."
              )
            q.enqueue(if (tt.tpe != null) tt.tpe.dealias.widen else NoType)
            traverse(recv)  // nested folds/HOFs in the receiver
            traverse(z)     // nested in the initial accumulator
            traverse(fbody) // nested in the op body
          case _ => super.traverse(tt)
        }
      }
      traverser.traverse(body.tree)
      q
    }

    /** The recorded result type `B` for the next `foldRight` rewrite. */
    def dequeueFoldRightResult(): Type =
      if (foldRightResultTypes.isEmpty)
        c.abort(
          body.tree.pos,
          "internal `Async.async` macro error: rewrote more `foldRight`s than were typed; please report this."
        )
      else foldRightResultTypes.dequeue()

    // ---- collect result types (from the TYPED body, in transform order) ------
    //
    // `collect[B, That](pf)` returns the receiver's collection family of `B`
    // (e.g. `List[B]`), recovered from the typed call node's own `tpe`. The
    // emitted builder-drain `def` needs that explicit return type, and the
    // element type `B` (its `typeArgs.head`) for the receiver's builder. Recorded
    // in transform order and validated here: `collect` with `.await` is
    // supported (Scala 2) only for `List` / `Vector` / immutable `Set` (the
    // builder-backed iterable receivers) — `Option` and `Map` are rejected (they
    // are DCA-only on Scala 3).
    val collectResultTypes: scala.collection.mutable.Queue[Type] = {
      val q                         = scala.collection.mutable.Queue.empty[Type]
      lazy val traverser: Traverser = new Traverser {
        override def traverse(tt: Tree): Unit = tt match {
          case CollectAwaitCall(recv, cases) =>
            val kind = receiverKind(recv).getOrElse(
              c.abort(
                tt.pos,
                "`.await` inside a `collect` case is currently supported only for `List`, `Vector`, and immutable " +
                  "`Set` receivers in the Scala 2 `Async.async` macro; convert the receiver to one of those first, " +
                  "or bind the awaited values before the partial function."
              )
            )
            if (kind == "option" || kind == "map")
              c.abort(
                tt.pos,
                "`.await` inside a `collect` case is currently supported only for `List`, `Vector`, and immutable " +
                  "`Set` receivers in the Scala 2 `Async.async` macro (an `Option` / `Map` receiver is not yet " +
                  "supported here, though it is on Scala 3); convert the receiver to a `List` / `Vector` / `Set` " +
                  "first, or bind the awaited values before the partial function."
              )
            q.enqueue(if (tt.tpe != null) tt.tpe.dealias.widen else NoType)
            traverse(recv)                       // nested folds/HOFs in the receiver
            cases.foreach(cd => traverse(cd.body)) // nested awaits/HOFs in the case bodies
          case _ => super.traverse(tt)
        }
      }
      traverser.traverse(body.tree)
      q
    }

    /** The recorded result type for the next `collect` rewrite. */
    def dequeueCollectResult(): Type =
      if (collectResultTypes.isEmpty)
        c.abort(
          body.tree.pos,
          "internal `Async.async` macro error: rewrote more `collect`s than were typed; please report this."
        )
      else collectResultTypes.dequeue()

    /** The recorded result element type `B` for the next HOF rewrite. */
    def dequeueHofElem(): Type =
      if (hofElemTypes.isEmpty)
        c.abort(
          body.tree.pos,
          "internal `Async.async` macro error: rewrote more HOF closures than were typed; please report this."
        )
      else hofElemTypes.dequeue()

    /**
     * The recorded receiver kind (`"list"` / `"option"`) for the next HOF
     * rewrite.
     */
    def dequeueHofKind(): String =
      if (hofRecvKinds.isEmpty)
        c.abort(
          body.tree.pos,
          "internal `Async.async` macro error: rewrote more HOF closures than were typed; please report this."
        )
      else hofRecvKinds.dequeue()

    /**
     * The recorded full result type (`Vector[B]` / `Set[B]` / `Unit`) for the
     * next HOF rewrite — consumed only by the generic `"iterable"` emit.
     */
    def dequeueHofResult(): Type =
      if (hofResultTypes.isEmpty)
        c.abort(
          body.tree.pos,
          "internal `Async.async` macro error: rewrote more HOF closures than were typed; please report this."
        )
      else hofResultTypes.dequeue()

    /**
     * The explicit element type for the next real `.await`'s generated
     * `flatMap` lambda parameter. We only ascribe `Nothing` (e.g.
     * `Async.fail(_).await`), which Scala 2 refuses to infer ("missing
     * parameter type"); every other element type infers correctly, and
     * ascribing a path-dependent / block-local type here would risk leaking
     * stale typed symbols across the macro's untypecheck/retypecheck boundary.
     * Underflow means the transform rewrote more `.await`s than were typed — an
     * internal invariant violation.
     */
    def nextAwaitTpt(): Tree = {
      if (awaitElemTypes.isEmpty)
        c.abort(
          body.tree.pos,
          "internal `Async.async` macro error: rewrote more `.await`s than were typed; please report this."
        )
      val tpe = awaitElemTypes.dequeue()
      if (tpe != null && tpe != NoType && tpe =:= definitions.NothingTpe) TypeTree(definitions.NothingTpe)
      else TypeTree()
    }

    // ---- pre-flight: reject positions we do not (yet) rewrite ---------------

    def precheck(t: Tree): Unit =
      new Traverser {
        override def traverse(tt: Tree): Unit = tt match {
          case AwaitCall(inner) => traverse(inner)
          // A supported HOF closure (`recv.map(x => ...await)`) is rewritten, not
          // rejected. Recurse into the receiver and the closure BODY so any
          // nested unsupported position (a further plain lambda, a local def,
          // etc.) is still caught — but do not descend into the `Function` node
          // itself, which would trip the generic function-literal rejection.
          case HofAwaitCall(recv, _, _, fbody) =>
            traverse(recv)
            traverse(fbody)
          // A supported `foldLeft(z)(op)` await is rewritten, not rejected.
          // Recurse into the receiver, the initial value, and the op BODY (so
          // nested unsupported positions are still caught) — but not the `op`
          // Function node, which would trip the function-literal rejection.
          case FoldLeftAwaitCall(recv, z, _, _, fbody) =>
            traverse(recv)
            traverse(z)
            traverse(fbody)
          // A supported `reduce(op)` / `reduceLeft(op)` await is rewritten, not
          // rejected. Recurse into the receiver and the op BODY (so nested
          // unsupported positions are still caught) — but not the `op` Function
          // node, which would trip the function-literal rejection.
          case ReduceAwaitCall(recv, _, _, fbody) =>
            traverse(recv)
            traverse(fbody)
          // A supported `foldRight(z)(op)` await is rewritten, not rejected.
          // Recurse into the receiver, the initial value, and the op BODY — but
          // not the `op` Function node.
          case FoldRightAwaitCall(recv, z, _, _, fbody) =>
            traverse(recv)
            traverse(z)
            traverse(fbody)
          // A supported `collect(pf)` await is rewritten, not rejected. Recurse
          // into the receiver and each case BODY (so nested unsupported positions
          // are still caught) — but NOT the synthetic `$anonfun` `ClassDef`,
          // which would trip the local-class rejection. A `.await` in a case
          // GUARD is rejected (the guard becomes an ordinary `if` in the emitted
          // match, which cannot host a suspension).
          case CollectAwaitCall(recv, cases) =>
            traverse(recv)
            cases.foreach { cd =>
              if (cd.guard.nonEmpty && containsAwait(cd.guard))
                c.abort(
                  cd.guard.pos,
                  "`.await` in a `collect` case guard is not supported; compute the awaited value before the " +
                    "partial function, or move it into the case body."
                )
              traverse(cd.body)
            }
          case Function(_, fbody) if containsAwait(fbody) =>
            c.abort(
              tt.pos,
              "`.await` inside a function literal / higher-order-function argument is not supported by the " +
                "Scala 2 `Async.async` macro; bind the awaited value before the lambda, or move the lambda body " +
                "into its own `Async.async` block."
            )
          case d: DefDef if containsAwait(d.rhs) =>
            c.abort(
              tt.pos,
              "`.await` inside a local def is not supported by the Scala 2 `Async.async` macro; make the def " +
                "return `Async[...]` and await it at the call site, or inline the body."
            )
          case _: ClassDef | _: ModuleDef if containsAwait(tt) =>
            c.abort(tt.pos, "`.await` inside a local class/object is not supported by the Scala 2 `Async.async` macro.")
          case v: ValDef if v.mods.hasFlag(Flag.LAZY) && containsAwait(v.rhs) =>
            c.abort(
              tt.pos,
              "`.await` inside a `lazy val` is not supported (suspending lazy initialization is not supported); " +
                "use a strict `val` inside `Async.async`."
            )
          case _: Return =>
            c.abort(tt.pos, "`return` is not supported inside `Async.async`; use the block's final expression instead.")
          case Apply(Select(_, n), List(arg)) if n.decodedName.toString == "synchronized" && containsAwait(arg) =>
            c.abort(
              tt.pos,
              "`.await` inside `synchronized` is not supported (awaiting while holding a monitor can deadlock); " +
                "move the await outside the synchronized block."
            )
          case _ => super.traverse(tt)
        }
      }.traverse(t)

    // ---- emit helpers -------------------------------------------------------

    /** A single-arg function literal with an inferred parameter type. */
    def lam(name: TermName, lbody: Tree): Tree =
      lamT(name, TypeTree(), lbody)

    /** A single-arg function literal with an explicit parameter type tree. */
    def lamT(name: TermName, tpt: Tree, lbody: Tree): Tree =
      Function(List(ValDef(Modifiers(Flag.PARAM), name, tpt, EmptyTree)), lbody)

    /** `try { <asyncTree> } catch { case t => Async.fail(t) }` */
    def safe(asyncTree: Tree): Tree = {
      val t = fresh("asyncErr$")
      q"""try { $asyncTree } catch { case $t: _root_.java.lang.Throwable => $AsyncObj.fail($t) }"""
    }

    /**
     * Evaluate `expr` (a value), catching throws, pass the bound value to `k`.
     */
    def bind(expr: Tree)(k: Tree => Tree): Tree = {
      val tmp = fresh("v$")
      safe(q"""{ val $tmp = $expr; ${k(q"$tmp")} }""")
    }

    /** Bind an `Async` value: `fa.flatMap(a => k(a))`, throw-safe. */
    def asyncBind(fa: Tree)(k: Tree => Tree): Tree =
      asyncBindT(fa, TypeTree())(k)

    /**
     * Bind an `Async` value with an explicit element type for the `flatMap`
     * lambda parameter: `fa.flatMap((a: T) => k(a))`, throw-safe.
     *
     * The explicit `T` is essential for awaited `Async[Nothing]` values (e.g.
     * `Async.fail(_).await`): Scala 2 refuses to infer a `flatMap` lambda
     * parameter type of `Nothing` ("missing parameter type"). For every other
     * element type inference also works, but ascribing it costs nothing and
     * keeps the generated code robust.
     */
    def asyncBindT(fa: Tree, tpt: Tree)(k: Tree => Tree): Tree = {
      val a = fresh("a$")
      safe(q"""$OpsObj($fa).flatMap(${lamT(a, tpt, k(q"$a"))})""")
    }

    /** Terminal continuation: lift a pure value into a ready `Async`. */
    val pureReturn: Tree => Tree = v => q"$AsyncObj.succeed($v)"

    /**
     * Rewrite `recvVal.map(pname => fbody-with-await)` (receiver already a
     * bound `List` value) with EAGER semantics producing `Async[List[B]]`, then
     * continue with `k`.
     *
     * Strict `List.map` applies the closure to EVERY element first — running
     * all construction-time side effects and ready-await fast paths — to build
     * a `List[Async[B]]`; the resulting `Async` values are then sequenced
     * left-to-right by [[zio.blocks.async.Async.collectAll]], which is
     * fail-fast (the first failure short-circuits the rest). This matches the
     * Scala 3 cells exactly: dotty-cps-async's eager `ListAsyncShift` and the
     * JS-native `js.await` backend, where `Array.map(async ...)` likewise
     * invokes every callback before any await resolves.
     */
    def emitHofMap(recvVal: Tree, pname: TermName, fbody: Tree, bTpe: Type)(k: Tree => Tree): Tree = {
      val bTpt: Tree = if (bTpe != null && bTpe != NoType) TypeTree(bTpe) else TypeTree()
      // The closure body is CPS-transformed to an `Async[B]`; strict `List.map`
      // applies it to every element, yielding `List[Async[B]]`, which
      // `Async.collectAll` then sequences (fail-fast).
      val bodyA   = transform(fbody)(pureReturn)
      val effects =
        q"""$recvVal.map(${lamT(pname, TypeTree(), q"$bodyA.asInstanceOf[_root_.zio.blocks.async.Async[$bTpt]]")})"""
      val collected =
        if (bTpe != null && bTpe != NoType) q"$AsyncObj.collectAll[$bTpt]($effects)"
        else q"$AsyncObj.collectAll($effects)"
      asyncBind(safe(collected))(k)
    }

    /**
     * Rewrite `recvVal.foreach(pname => fbody-with-await)` (receiver already a
     * bound `List` value) with LAZY / sequential semantics producing
     * `Async[Unit]`, then continue with `k`.
     *
     * Unlike `map`, `foreach` discards the closure results, and every Scala 3
     * backend evaluates it strictly left-to-right and lazily: the closure for
     * element `n+1` is NOT invoked until element `n`'s `Async` has completed
     * successfully. We mirror that with a tight `while` over the iterator while
     * results are ready (no `flatMap` allocation), switching to a `flatMap`
     * continuation on the first suspended (or failed) element — a failure
     * short-circuits the remaining elements via `flatMap`'s `Failure` path.
     */
    def emitHofForeach(recvVal: Tree, pname: TermName, fbody: Tree)(k: Tree => Tree): Tree = {
      val drain     = fresh("drainForeach$")
      val it        = fresh("it$")
      val r0        = fresh("r$")
      val bodyA     = transform(fbody)(pureReturn)
      val whileBody = q"""
        {
          while ($it.hasNext) {
            val $pname   = $it.next()
            val $r0: Any = $bodyA
            if ($r0.isInstanceOf[$Pollable]) {
              return $OpsObj($r0.asInstanceOf[_root_.zio.blocks.async.Async[Any]]).flatMap { (_: Any) => $drain() }
            }
          }
          $AsyncObj.succeed(())
        }
      """
      val loop = q"""
        {
          val $it = $recvVal.iterator
          def $drain(): _root_.zio.blocks.async.Async[_root_.scala.Unit] = ${safe(whileBody)}
          $drain()
        }
      """
      asyncBind(loop)(k)
    }

    /**
     * Rewrite `recvVal.foldLeft(zVal)((accName, xName) => fbody-with-await)`
     * (receiver and initial value already bound) producing `Async[B]`, then
     * continue with `k`.
     *
     * A left fold is inherently sequential — element `n+1`'s `op` needs `n`'s
     * accumulator — so this is LAZY / sequential on every backend (no
     * eager/lazy divergence to reconcile). A tight `while` threads the
     * accumulator in a local `var` while each `op` result is ready (no
     * `flatMap` allocation); on the first suspended (or failed) `op` it
     * switches to a recursive `flatMap` continuation that resumes the same
     * iterator with the new accumulator. A failed `op` short-circuits the rest
     * via `flatMap`'s `Failure` path. The recursive drain `def` requires the
     * explicit `Async[B]` return type recovered into `foldResultTypes`.
     */
    def emitHofFoldLeft(
      recvVal: Tree,
      zVal: Tree,
      accName: TermName,
      xName: TermName,
      fbody: Tree,
      bTpe: Type
    )(k: Tree => Tree): Tree = {
      val drain      = fresh("drainFold$")
      val it         = fresh("it$")
      val acc0       = fresh("acc$")                // drain parameter: incoming accumulator
      val accVar     = fresh("accv$")               // mutable accumulator within the while
      val r0         = fresh("r$")
      val nv         = fresh("nv$")
      val bTpt: Tree = if (bTpe != null && bTpe != NoType) TypeTree(bTpe) else TypeTree()
      val bodyA      = transform(fbody)(pureReturn) // references accName and xName; yields Async[B]
      val whileBody  = q"""
        {
          var $accVar: $bTpt = $acc0
          while ($it.hasNext) {
            val $xName   = $it.next()
            val $accName = $accVar
            val $r0: Any = $bodyA
            if ($r0.isInstanceOf[$Pollable]) {
              return $OpsObj($r0.asInstanceOf[_root_.zio.blocks.async.Async[$bTpt]])
                .flatMap { ($nv: $bTpt) => $drain($nv) }
            }
            $accVar = $r0.asInstanceOf[$bTpt]
          }
          $AsyncObj.succeed($accVar)
        }
      """
      val loop = q"""
        {
          val $it = $recvVal.iterator
          def $drain($acc0: $bTpt): _root_.zio.blocks.async.Async[$bTpt] = ${safe(whileBody)}
          $drain($zVal)
        }
      """
      asyncBind(loop)(k)
    }

    /**
     * Rewrite `recvVal.reduce((accName, xName) => fbody-with-await)` /
     * `reduceLeft` (receiver already bound) producing `Async[B]`, then continue
     * with `k`.
     *
     * `reduce` is `foldLeft` seeded by the FIRST element instead of an initial
     * value: it folds the rest left-to-right (so it is LAZY / sequential on
     * every backend, exactly like `foldLeft`), and an EMPTY receiver fails with
     * `UnsupportedOperationException` (matching the standard library; surfaced
     * as an `Async.fail` so it is catchable via `catchAll` and rethrown by
     * `.block`). The recursive drain `def` requires the explicit `Async[B]`
     * return type recovered into `reduceResultTypes`.
     */
    def emitHofReduce(
      recvVal: Tree,
      accName: TermName,
      xName: TermName,
      fbody: Tree,
      bTpe: Type
    )(k: Tree => Tree): Tree = {
      val drain      = fresh("drainReduce$")
      val it         = fresh("it$")
      val acc0       = fresh("acc$")
      val accVar     = fresh("accv$")
      val r0         = fresh("r$")
      val nv         = fresh("nv$")
      val bTpt: Tree = if (bTpe != null && bTpe != NoType) TypeTree(bTpe) else TypeTree()
      val bodyA      = transform(fbody)(pureReturn) // references accName and xName; yields Async[B]
      val whileBody  = q"""
        {
          var $accVar: $bTpt = $acc0
          while ($it.hasNext) {
            val $xName   = $it.next()
            val $accName = $accVar
            val $r0: Any = $bodyA
            if ($r0.isInstanceOf[$Pollable]) {
              return $OpsObj($r0.asInstanceOf[_root_.zio.blocks.async.Async[$bTpt]])
                .flatMap { ($nv: $bTpt) => $drain($nv) }
            }
            $accVar = $r0.asInstanceOf[$bTpt]
          }
          $AsyncObj.succeed($accVar)
        }
      """
      val loop = q"""
        {
          val $it = $recvVal.iterator
          if (!$it.hasNext)
            $AsyncObj.fail(new _root_.java.lang.UnsupportedOperationException("empty.reduceLeft"))
          else {
            def $drain($acc0: $bTpt): _root_.zio.blocks.async.Async[$bTpt] = ${safe(whileBody)}
            $drain($it.next().asInstanceOf[$bTpt])
          }
        }
      """
      asyncBind(loop)(k)
    }

    /**
     * Rewrite `recvVal.foldRight(zVal)((xName, accName) => fbody-with-await)`
     * (receiver and initial value already bound) producing `Async[B]`, then
     * continue with `k`.
     *
     * `foldRight` is RIGHT-associative — `op(x1, op(x2, ..., op(xn, z)))` — so the
     * op for the RIGHTMOST element runs first (empirically confirmed against
     * DCA). To make the await-ordering match and stay sequential, the receiver
     * is materialized via `.toVector` and drained in REVERSE: starting from the
     * last index with the accumulator seeded to `z`, each step is `op(buf(i),
     * acc)` walking down to index `0`. A tight `while` threads the accumulator
     * in a local `var` while each `op` result is ready (no `flatMap`
     * allocation); the first suspended (or failed) `op` switches to a recursive
     * `flatMap` continuation that resumes at the next-lower index. An empty
     * receiver yields `z` (the op never runs). The recursive drain `def`
     * requires the explicit `Async[B]` return type recovered into
     * `foldRightResultTypes`.
     */
    def emitHofFoldRight(
      recvVal: Tree,
      zVal: Tree,
      xName: TermName,
      accName: TermName,
      fbody: Tree,
      bTpe: Type
    )(k: Tree => Tree): Tree = {
      val drain      = fresh("drainFoldR$")
      val buf        = fresh("buf$")
      val idx0       = fresh("i$")                 // drain parameter: current (descending) index
      val acc0       = fresh("acc$")               // drain parameter: incoming accumulator
      val idxVar     = fresh("iv$")                // mutable index within the while
      val accVar     = fresh("accv$")              // mutable accumulator within the while
      val r0         = fresh("r$")
      val nv         = fresh("nv$")
      val ni         = fresh("ni$")
      val bTpt: Tree = if (bTpe != null && bTpe != NoType) TypeTree(bTpe) else TypeTree()
      val bodyA      = transform(fbody)(pureReturn) // references xName and accName; yields Async[B]
      val whileBody  = q"""
        {
          var $idxVar: _root_.scala.Int = $idx0
          var $accVar: $bTpt            = $acc0
          while ($idxVar >= 0) {
            val $xName   = $buf($idxVar)
            val $accName = $accVar
            val $r0: Any = $bodyA
            if ($r0.isInstanceOf[$Pollable]) {
              val $ni: _root_.scala.Int = $idxVar - 1
              return $OpsObj($r0.asInstanceOf[_root_.zio.blocks.async.Async[$bTpt]])
                .flatMap { ($nv: $bTpt) => $drain($ni, $nv) }
            }
            $accVar = $r0.asInstanceOf[$bTpt]
            $idxVar = $idxVar - 1
          }
          $AsyncObj.succeed($accVar)
        }
      """
      val loop = q"""
        {
          val $buf = $recvVal.toVector
          def $drain($idx0: _root_.scala.Int, $acc0: $bTpt): _root_.zio.blocks.async.Async[$bTpt] = ${safe(whileBody)}
          $drain($buf.length - 1, $zVal)
        }
      """
      asyncBind(loop)(k)
    }

    /**
     * Rewrite `recvVal.flatMap(pname => fbody-with-await)` (receiver already a
     * bound `List` value) with LAZY / sequential semantics producing
     * `Async[List[B]]`, then continue with `k`.
     *
     * Like `foreach`, the closure for element `n+1` runs only after element
     * `n`'s await completes successfully (so effects and awaits are strictly
     * left-to-right and a failure short-circuits the rest), but each closure
     * yields an `IterableOnce[B]` that is concatenated into the result. A tight
     * `while` accumulates ready elements; the first suspended element switches
     * to a `flatMap` continuation.
     */
    def emitHofFlatMap(recvVal: Tree, pname: TermName, fbody: Tree, bTpe: Type)(k: Tree => Tree): Tree = {
      val drain      = fresh("drainFlatMap$")
      val it         = fresh("it$")
      val buf        = fresh("buf$")
      val r0         = fresh("r$")
      val x0         = fresh("x$")
      val bTpt: Tree = if (bTpe != null && bTpe != NoType) TypeTree(bTpe) else TypeTree()
      val bodyA      = transform(fbody)(pureReturn)
      val whileBody  = q"""
        {
          while ($it.hasNext) {
            val $pname   = $it.next()
            val $r0: Any = $bodyA
            if ($r0.isInstanceOf[$Pollable]) {
              return $OpsObj($r0.asInstanceOf[_root_.zio.blocks.async.Async[_root_.scala.collection.IterableOnce[$bTpt]]])
                .flatMap { ($x0: _root_.scala.collection.IterableOnce[$bTpt]) =>
                  $buf ++= $x0
                  $drain()
                }
            }
            $buf ++= $r0.asInstanceOf[_root_.scala.collection.IterableOnce[$bTpt]]
          }
          $AsyncObj.succeed($buf.toList)
        }
      """
      val loop = q"""
        {
          val $it  = $recvVal.iterator
          val $buf = _root_.scala.collection.mutable.ListBuffer.empty[$bTpt]
          def $drain(): _root_.zio.blocks.async.Async[_root_.scala.collection.immutable.List[$bTpt]] =
            ${safe(whileBody)}
          $drain()
        }
      """
      asyncBind(loop)(k)
    }

    /**
     * Rewrite `recvVal.map(pname => fbody-with-await)` for an `Option`
     * receiver, producing `Async[Option[B]]`, then continue with `k`.
     *
     * An `Option` holds at most one element, so the eager/lazy distinction that
     * separates `List.map` from `List.foreach`/`flatMap` collapses: there is a
     * single `Some`/`None` branch. `None` short-circuits to
     * `Async.succeed(None)` (the closure never runs); `Some(x)` runs the
     * CPS-transformed closure body (an `Async[B]`) and wraps the result back in
     * `Some`. This matches all three Scala 3 backends (DCA's `OptionAsyncShift`
     * and strict `Option.map` under `js.await`), verified empirically.
     */
    def emitOptionMap(recvVal: Tree, pname: TermName, fbody: Tree, bTpe: Type)(k: Tree => Tree): Tree = {
      val bTpt: Tree = if (bTpe != null && bTpe != NoType) TypeTree(bTpe) else TypeTree()
      val bn         = fresh("b$")
      val bodyA      = transform(fbody)(pureReturn)
      val someBranch =
        q"""$OpsObj($bodyA).map(($bn: $bTpt) => (_root_.scala.Some($bn): _root_.scala.Option[$bTpt]))"""
      val noneBranch =
        if (bTpe != null && bTpe != NoType) q"$AsyncObj.succeed(_root_.scala.None: _root_.scala.Option[$bTpt])"
        else q"$AsyncObj.succeed(_root_.scala.None)"
      val resultAsync = q"""
        if ($recvVal.isEmpty) $noneBranch
        else { val $pname = $recvVal.get; $someBranch }
      """
      asyncBind(safe(resultAsync))(k)
    }

    /**
     * Rewrite `recvVal.flatMap(pname => fbody-with-await)` for an `Option`
     * receiver, producing `Async[Option[B]]`, then continue with `k`. The
     * closure body yields an `Option[B]` (after awaits), so the CPS-transformed
     * body is already an `Async[Option[B]]`; `None` short-circuits.
     */
    def emitOptionFlatMap(recvVal: Tree, pname: TermName, fbody: Tree, bTpe: Type)(k: Tree => Tree): Tree = {
      val bTpt: Tree = if (bTpe != null && bTpe != NoType) TypeTree(bTpe) else TypeTree()
      val bodyA      = transform(fbody)(pureReturn)
      val noneBranch =
        if (bTpe != null && bTpe != NoType) q"$AsyncObj.succeed(_root_.scala.None: _root_.scala.Option[$bTpt])"
        else q"$AsyncObj.succeed(_root_.scala.None)"
      val resultAsync = q"""
        if ($recvVal.isEmpty) $noneBranch
        else { val $pname = $recvVal.get; $bodyA }
      """
      asyncBind(safe(resultAsync))(k)
    }

    /**
     * Rewrite `recvVal.foreach(pname => fbody-with-await)` for an `Option`
     * receiver, producing `Async[Unit]`, then continue with `k`. `None` runs
     * nothing; `Some(x)` runs the closure for its await/effects and discards
     * the result.
     */
    def emitOptionForeach(recvVal: Tree, pname: TermName, fbody: Tree)(k: Tree => Tree): Tree = {
      val bodyA       = transform(fbody)(pureReturn)
      val resultAsync = q"""
        if ($recvVal.isEmpty) $AsyncObj.succeed(())
        else { val $pname = $recvVal.get; $OpsObj($bodyA).map((_: _root_.scala.Any) => ()) }
      """
      asyncBind(safe(resultAsync))(k)
    }

    /**
     * Shared builder-drain rewrite for a strict standard collection (`Vector`,
     * immutable `Set`, immutable `Map`, …) with LAZY / sequential semantics,
     * producing `Async[CC[…]]` (result collection type preserved), then
     * continue with `k`.
     *
     * Unlike `List.map` (the EAGER special case), these collections' `map` /
     * `flatMap` are sequential on every Scala 3 backend (verified empirically):
     * the closure for element `n+1` runs only after element `n`'s `.await`
     * completes successfully, and a failed await short-circuits the rest. We
     * drain the source iterator and accumulate into `builder` (the receiver's
     * OWN builder, supplied by the caller — `iterableFactory.newBuilder` for
     * `Vector`/`Set`/non-pair `Map` results, `mapFactory.newBuilder` for
     * pair-producing `Map` results), which preserves the collection family (and
     * deduplicates a `Set`'s, or collapses a `Map`'s, *awaited* values — never
     * an intermediate `CC[Async[…]]`). A tight `while` advances ready elements;
     * the first suspended element switches to a `flatMap` continuation.
     *
     * `elemTpt` is the closure result element type `E`; when `flat` is true
     * each element yields an `IterableOnce[E]` concatenated (`++=`) into the
     * builder, otherwise a single `E` is appended (`+=`).
     */
    def builderDrain(
      recvVal: Tree,
      pname: TermName,
      fbody: Tree,
      elemTpt: Tree,
      resTpt: Tree,
      builder: Tree,
      flat: Boolean
    )(k: Tree => Tree): Tree = {
      val drain                  = fresh("drainColl$")
      val it                     = fresh("it$")
      val bld                    = fresh("bld$")
      val r0                     = fresh("r$")
      val x0                     = fresh("x$")
      val bodyA                  = transform(fbody)(pureReturn)
      val awaitedTpt: Tree       = if (flat) tq"_root_.scala.collection.IterableOnce[$elemTpt]" else elemTpt
      def add(value: Tree): Tree = if (flat) q"$bld ++= $value" else q"$bld += $value"
      val whileBody              = q"""
        {
          while ($it.hasNext) {
            val $pname   = $it.next()
            val $r0: Any = $bodyA
            if ($r0.isInstanceOf[$Pollable]) {
              return $OpsObj($r0.asInstanceOf[_root_.zio.blocks.async.Async[$awaitedTpt]])
                .flatMap { ($x0: $awaitedTpt) =>
                  ${add(q"$x0")}
                  $drain()
                }
            }
            ${add(q"$r0.asInstanceOf[$awaitedTpt]")}
          }
          $AsyncObj.succeed($bld.result())
        }
      """
      val loop = q"""
        {
          val $it  = $recvVal.iterator
          val $bld = $builder
          def $drain(): _root_.zio.blocks.async.Async[$resTpt] = ${safe(whileBody)}
          $drain()
        }
      """
      asyncBind(loop)(k)
    }

    /**
     * `map`/`flatMap` for a generic strict collection (`Vector`, immutable
     * `Set`), accumulating into the receiver's own `iterableFactory` builder.
     */
    def emitCollMap(recvVal: Tree, pname: TermName, fbody: Tree, bTpe: Type, resultTpe: Type)(k: Tree => Tree): Tree = {
      val bTpt: Tree   = if (bTpe != null && bTpe != NoType) TypeTree(bTpe) else TypeTree()
      val resTpt: Tree = if (resultTpe != null && resultTpe != NoType) TypeTree(resultTpe) else TypeTree()
      builderDrain(recvVal, pname, fbody, bTpt, resTpt, q"$recvVal.iterableFactory.newBuilder[$bTpt]", flat = false)(k)
    }

    def emitCollFlatMap(recvVal: Tree, pname: TermName, fbody: Tree, bTpe: Type, resultTpe: Type)(
      k: Tree => Tree
    ): Tree = {
      val bTpt: Tree   = if (bTpe != null && bTpe != NoType) TypeTree(bTpe) else TypeTree()
      val resTpt: Tree = if (resultTpe != null && resultTpe != NoType) TypeTree(resultTpe) else TypeTree()
      builderDrain(recvVal, pname, fbody, bTpt, resTpt, q"$recvVal.iterableFactory.newBuilder[$bTpt]", flat = true)(k)
    }

    /**
     * `map`/`flatMap` for an immutable `Map` receiver. The element type `bTpe`
     * is the closure's result element: an entry tuple `(K2, V2)` when the
     * result is a `Map[K2, V2]` (rebuilt via `mapFactory`), or a plain `B` when
     * the closure yields non-pair elements and the standard library widens the
     * result to an `Iterable[B]` (rebuilt via `iterableFactory`). `flat`
     * selects `flatMap` (`++=` of `IterableOnce`) over `map` (`+=`).
     */
    def emitMapMapLike(recvVal: Tree, pname: TermName, fbody: Tree, bTpe: Type, resultTpe: Type, flat: Boolean)(
      k: Tree => Tree
    ): Tree = {
      val bTpt: Tree    = if (bTpe != null && bTpe != NoType) TypeTree(bTpe) else TypeTree()
      val resTpt: Tree  = if (resultTpe != null && resultTpe != NoType) TypeTree(resultTpe) else TypeTree()
      val builder: Tree =
        if (resultTpe != null && resultTpe.baseType(MapSym) != NoType && resultTpe.typeArgs.lengthCompare(2) == 0) {
          val k2 = TypeTree(resultTpe.typeArgs(0))
          val v2 = TypeTree(resultTpe.typeArgs(1))
          q"$recvVal.mapFactory.newBuilder[$k2, $v2]"
        } else q"$recvVal.iterableFactory.newBuilder[$bTpt]"
      builderDrain(recvVal, pname, fbody, bTpt, resTpt, builder, flat)(k)
    }

    /**
     * Rewrite a short-circuiting predicate scan — `find` / `exists` / `forall`
     * — whose `A => Boolean` closure body contains `.await`, for any
     * whitelisted receiver (`List`, `Option`, `Vector`, `Set`, `Map` — all have
     * `.iterator`), then continue with `k`. These are inherently **lazy /
     * sequential** (and so identical on every backend, verified empirically):
     * the predicate for element `n+1` runs only after element `n`'s `.await`
     * completes, and the scan stops at the first decisive element.
     *
     * `mode` selects the result shape:
     *   - `"exists"` → `Async[Boolean]`; short-circuits `true` on the first
     *     `true`, else `false`.
     *   - `"forall"` → `Async[Boolean]`; short-circuits `false` on the first
     *     `false`, else `true`.
     *   - `"find"` → `Async[Option[A]]`; short-circuits `Some(elem)` on the
     *     first `true`, else `None`.
     *
     * A tight `while` advances ready elements; the first suspended predicate
     * switches to a `flatMap` continuation. `resultTpe` is the full result type
     * (`Boolean` or `Option[A]`), used for the recursive drain `def`'s return.
     */
    def emitPredicateScan(recvVal: Tree, pname: TermName, fbody: Tree, mode: String, resultTpe: Type)(
      k: Tree => Tree
    ): Tree = {
      val drain                       = fresh("drainScan$")
      val it                          = fresh("it$")
      val r0                          = fresh("r$")
      val bn                          = fresh("b$")
      val resTpt: Tree                = if (resultTpe != null && resultTpe != NoType) TypeTree(resultTpe) else TypeTree()
      val bodyA                       = transform(fbody)(pureReturn)
      val (onHit, onEnd, hitWhenTrue) = mode match {
        case "exists" => (q"$AsyncObj.succeed(true)", q"$AsyncObj.succeed(false)", true)
        case "forall" => (q"$AsyncObj.succeed(false)", q"$AsyncObj.succeed(true)", false)
        case _        => // "find"
          (
            q"$AsyncObj.succeed((_root_.scala.Some($pname): $resTpt))",
            q"$AsyncObj.succeed((_root_.scala.None: $resTpt))",
            true
          )
      }
      def hit(b: Tree): Tree = if (hitWhenTrue) b else q"!$b"
      val whileBody          = q"""
        {
          while ($it.hasNext) {
            val $pname   = $it.next()
            val $r0: Any = $bodyA
            if ($r0.isInstanceOf[$Pollable]) {
              return $OpsObj($r0.asInstanceOf[_root_.zio.blocks.async.Async[_root_.scala.Boolean]])
                .flatMap { ($bn: _root_.scala.Boolean) =>
                  if (${hit(q"$bn")}) $onHit else $drain()
                }
            }
            if (${hit(q"$r0.asInstanceOf[_root_.scala.Boolean]")}) return $onHit
          }
          $onEnd
        }
      """
      val loop = q"""
        {
          val $it = $recvVal.iterator
          def $drain(): _root_.zio.blocks.async.Async[$resTpt] = ${safe(whileBody)}
          $drain()
        }
      """
      asyncBind(loop)(k)
    }

    /**
     * Rewrite a result-collection-preserving predicate filter — `filter` /
     * `filterNot` — whose `A => Boolean` closure body contains `.await`, for a
     * builder-backed receiver (`List` / `Vector` / immutable `Set` / `Map`),
     * then continue with `k`. **Lazy / sequential** on every backend (verified
     * empirically): the predicate for element `n+1` runs only after element
     * `n`'s `.await` completes, and a failed await short-circuits the rest. The
     * SOURCE element (not the predicate result) is accumulated into the
     * receiver's own `builder` (so the collection family is preserved —
     * `iterableFactory.newBuilder` for `List`/`Vector`/`Set`, `mapFactory` for
     * `Map`); `negate` selects `filterNot` (keep when the predicate is
     * `false`).
     *
     * `elemTpt` is the SOURCE element type (for `Map`, the entry tuple
     * `(K, V)`) — `filter` does not transform elements, so the result element
     * type equals the source element type; `resTpt` is the full result
     * collection type, the recursive drain `def`'s return type.
     */
    def emitFilterLike(
      recvVal: Tree,
      pname: TermName,
      fbody: Tree,
      negate: Boolean,
      elemTpt: Tree,
      resTpt: Tree,
      builder: Tree
    )(k: Tree => Tree): Tree = {
      val drain               = fresh("drainFilter$")
      val it                  = fresh("it$")
      val bld                 = fresh("bld$")
      val r0                  = fresh("r$")
      val bn                  = fresh("b$")
      val bodyA               = transform(fbody)(pureReturn)
      def keep(b: Tree): Tree = if (negate) q"!$b" else b
      val whileBody           = q"""
        {
          while ($it.hasNext) {
            val $pname: $elemTpt = $it.next()
            val $r0: Any         = $bodyA
            if ($r0.isInstanceOf[$Pollable]) {
              return $OpsObj($r0.asInstanceOf[_root_.zio.blocks.async.Async[_root_.scala.Boolean]])
                .flatMap { ($bn: _root_.scala.Boolean) =>
                  if (${keep(q"$bn")}) $bld += $pname
                  $drain()
                }
            }
            if (${keep(q"$r0.asInstanceOf[_root_.scala.Boolean]")}) $bld += $pname
          }
          $AsyncObj.succeed($bld.result())
        }
      """
      val loop = q"""
        {
          val $it  = $recvVal.iterator
          val $bld = $builder
          def $drain(): _root_.zio.blocks.async.Async[$resTpt] = ${safe(whileBody)}
          $drain()
        }
      """
      asyncBind(loop)(k)
    }

    /**
     * Rewrite `recvVal.filter` / `recvVal.filterNot` for an `Option` receiver,
     * producing `Async[Option[A]]`, then continue with `k`. An `Option` holds
     * at most one element: `None` short-circuits to `Async.succeed(None)` (the
     * predicate never runs); `Some(x)` runs the CPS-transformed predicate and
     * keeps `Some(x)` when it matches (`negate` selects `filterNot`), else
     * `None`. Matches all three Scala 3 backends.
     */
    def emitOptionFilter(recvVal: Tree, pname: TermName, fbody: Tree, negate: Boolean, elemTpt: Tree)(
      k: Tree => Tree
    ): Tree = {
      val bn                  = fresh("b$")
      val bodyA               = transform(fbody)(pureReturn)
      def keep(b: Tree): Tree = if (negate) q"!$b" else b
      val noneOpt             = q"(_root_.scala.None: _root_.scala.Option[$elemTpt])"
      val someBranch          = q"""
        { val $pname: $elemTpt = $recvVal.get
          $OpsObj($bodyA).map { ($bn: _root_.scala.Boolean) =>
            if (${keep(q"$bn")}) (_root_.scala.Some($pname): _root_.scala.Option[$elemTpt]) else $noneOpt
          }
        }
      """
      val resultAsync = q"""
        if ($recvVal.isEmpty) $AsyncObj.succeed($noneOpt)
        else $someBranch
      """
      asyncBind(safe(resultAsync))(k)
    }

    /**
     * Dispatch a `filter` / `filterNot` rewrite by receiver kind: `Option` uses
     * the single-element `Some`/`None` emit; every builder-backed receiver
     * (`List` / `Vector` / `Set` / `Map`) uses [[emitFilterLike]] with the
     * receiver's own factory (`mapFactory.newBuilder[K, V]` for a `Map`,
     * `iterableFactory.newBuilder[A]` otherwise) so the collection family is
     * preserved.
     */
    def emitFilter(
      recvVal: Tree,
      kind: String,
      pname: TermName,
      fbody: Tree,
      negate: Boolean,
      bTpe: Type,
      resultTpe: Type
    )(k: Tree => Tree): Tree = {
      val bTpt: Tree = if (bTpe != null && bTpe != NoType) TypeTree(bTpe) else TypeTree()
      if (kind == "option") emitOptionFilter(recvVal, pname, fbody, negate, bTpt)(k)
      else {
        val resTpt: Tree  = if (resultTpe != null && resultTpe != NoType) TypeTree(resultTpe) else TypeTree()
        val builder: Tree =
          if (
            kind == "map" && resultTpe != null && resultTpe.baseType(MapSym) != NoType &&
            resultTpe.typeArgs.lengthCompare(2) == 0
          ) {
            val k2 = TypeTree(resultTpe.typeArgs(0))
            val v2 = TypeTree(resultTpe.typeArgs(1))
            q"$recvVal.mapFactory.newBuilder[$k2, $v2]"
          } else q"$recvVal.iterableFactory.newBuilder[$bTpt]"
        emitFilterLike(recvVal, pname, fbody, negate, bTpt, resTpt, builder)(k)
      }
    }

    /**
     * Rewrite `recvVal.takeWhile` / `recvVal.dropWhile` (whose predicate awaits)
     * for an ordered `Seq` receiver (`List` / `Vector`; enforced in the typed
     * pass), then continue with `k`.
     *
     * Both keep the receiver's collection family via its own builder
     * (`iterableFactory.newBuilder[A]` — `takeWhile`/`dropWhile` do not transform
     * elements, so the result element type equals the source element type
     * `elemTpt`). The predicate is CPS-transformed; a predicate that awaits
     * suspends and resumes at the next element, and a failed await
     * short-circuits the whole rewrite (via `flatMap`'s `Failure` path).
     *
     *   - `takeWhile`: drain the iterator, appending each element while the
     *     predicate holds; the FIRST element whose predicate is `false` STOPS
     *     the scan (it and the rest are discarded).
     *   - `dropWhile`: drain in two phases — phase one SKIPS elements while the
     *     predicate holds; the FIRST element whose predicate is `false` (and
     *     every element after it) is appended UNCONDITIONALLY (the predicate is
     *     never evaluated again), so only the leading run is dropped.
     */
    def emitTakeWhile(
      recvVal: Tree,
      pname: TermName,
      fbody: Tree,
      drop: Boolean,
      elemTpt: Tree,
      resTpt: Tree,
      builder: Tree
    )(k: Tree => Tree): Tree = {
      val drain = fresh("drainTakeDrop$")
      val it    = fresh("it$")
      val bld   = fresh("bld$")
      val r0    = fresh("r$")
      val bn    = fresh("b$")
      val bodyA = transform(fbody)(pureReturn)
      val loop  =
        if (drop) {
          // dropWhile: a second, predicate-free drain appends the remainder.
          val rest     = fresh("drainRest$")
          val restBody = q"""
            {
              while ($it.hasNext) $bld += $it.next()
              $AsyncObj.succeed($bld.result())
            }
          """
          val whileBody = q"""
            {
              while ($it.hasNext) {
                val $pname: $elemTpt = $it.next()
                val $r0: Any         = $bodyA
                if ($r0.isInstanceOf[$Pollable]) {
                  return $OpsObj($r0.asInstanceOf[_root_.zio.blocks.async.Async[_root_.scala.Boolean]])
                    .flatMap { ($bn: _root_.scala.Boolean) =>
                      if ($bn) $drain()
                      else { $bld += $pname; $rest() }
                    }
                }
                if (!$r0.asInstanceOf[_root_.scala.Boolean]) { $bld += $pname; return $rest() }
              }
              $AsyncObj.succeed($bld.result())
            }
          """
          q"""
            {
              val $it  = $recvVal.iterator
              val $bld = $builder
              def $rest(): _root_.zio.blocks.async.Async[$resTpt] = ${safe(restBody)}
              def $drain(): _root_.zio.blocks.async.Async[$resTpt] = ${safe(whileBody)}
              $drain()
            }
          """
        } else {
          val whileBody = q"""
            {
              while ($it.hasNext) {
                val $pname: $elemTpt = $it.next()
                val $r0: Any         = $bodyA
                if ($r0.isInstanceOf[$Pollable]) {
                  return $OpsObj($r0.asInstanceOf[_root_.zio.blocks.async.Async[_root_.scala.Boolean]])
                    .flatMap { ($bn: _root_.scala.Boolean) =>
                      if ($bn) { $bld += $pname; $drain() }
                      else $AsyncObj.succeed($bld.result())
                    }
                }
                if ($r0.asInstanceOf[_root_.scala.Boolean]) $bld += $pname
                else return $AsyncObj.succeed($bld.result())
              }
              $AsyncObj.succeed($bld.result())
            }
          """
          q"""
            {
              val $it  = $recvVal.iterator
              val $bld = $builder
              def $drain(): _root_.zio.blocks.async.Async[$resTpt] = ${safe(whileBody)}
              $drain()
            }
          """
        }
      asyncBind(loop)(k)
    }

    /**
     * Rewrite `recvVal.collect(pf)` (whose `pf` has at least one awaiting case
     * body) for a builder-backed receiver (`List` / `Vector` / immutable `Set`;
     * enforced in the typed pass) producing the receiver's collection family of
     * `B`, then continue with `k`.
     *
     * `collect` keeps the elements the `pf` is defined at, mapping them through
     * it — so each element is matched against the USER `case`s; a match appends
     * the (possibly awaited) body to the receiver's own builder, a non-match is
     * skipped. Membership and the mapped value are computed in a SINGLE match
     * per element (the guard runs exactly once), distinguishing "no match" with
     * a fresh sentinel (`skip`) appended by a trailing `case _`. The case body
     * is CPS-transformed; an awaiting body suspends and resumes at the next
     * element, and a failed await short-circuits the rest. The recursive
     * builder-drain `def` needs the explicit `Async[result]` return type.
     */
    def emitCollect(
      recvVal: Tree,
      cases: List[CaseDef],
      bTpe: Type,
      resultTpe: Type
    )(k: Tree => Tree): Tree = {
      val drain        = fresh("drainCollect$")
      val it           = fresh("it$")
      val bld          = fresh("bld$")
      val skip         = fresh("skip$")
      val elem         = fresh("el$")
      val r0           = fresh("r$")
      val bn           = fresh("b$")
      val bTpt: Tree   = if (bTpe != null && bTpe != NoType) TypeTree(bTpe) else TypeTree()
      val resTpt: Tree = if (resultTpe != null && resultTpe != NoType) TypeTree(resultTpe) else TypeTree()
      // User cases, each body CPS-transformed; a trailing `case _ => skip` marks
      // "not defined here" (the partial-function fallthrough). The default is
      // appended ONLY when the user cases are not already TOTAL (an unguarded
      // irrefutable last case) — otherwise the trailing `case _` is unreachable
      // (a fatal warning).
      def irrefutable(pat: Tree): Boolean = pat match {
        case Ident(termNames.WILDCARD)  => true
        case Bind(_, inner)             => irrefutable(inner)
        case _                          => false
      }
      val isTotal     = cases.lastOption.exists(cd => cd.guard.isEmpty && irrefutable(cd.pat))
      val newCases    = cases.map(cd => CaseDef(cd.pat, cd.guard, transform(cd.body)(pureReturn)))
      val defaultCase = cq"_ => $skip"
      val allCases    = if (isTotal) newCases else newCases :+ defaultCase
      val whileBody   = q"""
        {
          while ($it.hasNext) {
            val $elem        = $it.next()
            val $r0: _root_.scala.Any = $elem match { case ..$allCases }
            if (!($r0.asInstanceOf[_root_.scala.AnyRef] eq $skip)) {
              if ($r0.isInstanceOf[$Pollable]) {
                return $OpsObj($r0.asInstanceOf[_root_.zio.blocks.async.Async[$bTpt]])
                  .flatMap { ($bn: $bTpt) => $bld += $bn; $drain() }
              }
              $bld += $r0.asInstanceOf[$bTpt]
            }
          }
          $AsyncObj.succeed($bld.result())
        }
      """
      val loop = q"""
        {
          val $it   = $recvVal.iterator
          val $bld  = $recvVal.iterableFactory.newBuilder[$bTpt]
          val $skip: _root_.scala.AnyRef = new _root_.scala.AnyRef
          def $drain(): _root_.zio.blocks.async.Async[$resTpt] = ${safe(whileBody)}
          $drain()
        }
      """
      asyncBind(loop)(k)
    }

    // ---- the transform ------------------------------------------------------

    def transformParts(parts: List[Tree])(rebuild: List[Tree] => Tree): Tree = {
      def loop(todo: List[Tree], acc: List[Tree]): Tree = todo match {
        case Nil       => rebuild(acc.reverse)
        case p :: rest => transform(p)(v => loop(rest, v :: acc))
      }
      loop(parts, Nil)
    }

    def transformBlock(stmts: List[Tree], expr: Tree)(k: Tree => Tree): Tree =
      stmts match {
        case Nil => transform(expr)(k)
        // A true throwaway `val _ = rhs`: evaluate `rhs` for its effect / await
        // and discard the value. NOTE: only the genuine wildcard is dropped.
        // Compiler-synthesized `x$N` vals (e.g. the carrier of a `val (a, b) =`
        // pattern destructuring) must be BOUND, not dropped — dropping them
        // breaks subsequent accessors.
        case (vd: ValDef) :: rest if vd.name == termNames.WILDCARD =>
          transform(vd.rhs)(_ => transformBlock(rest, expr)(k))
        case (vd: ValDef) :: rest =>
          // Preserve the val's declared type tree. A source `val n: Long =
          // int.await` must keep `n` typed as `Long` after the CPS rewrite, or
          // overload resolution / widening could diverge from Scala 3 / DCA.
          transform(vd.rhs) { v =>
            val rebound = ValDef(Modifiers(), vd.name, vd.tpt.duplicate, v)
            safe(q"""{ $rebound; ${transformBlock(rest, expr)(k)} }""")
          }
        case stmt :: rest =>
          transform(stmt)(_ => transformBlock(rest, expr)(k))
      }

    def transformApplySpine(tree: Tree)(k: Tree => Tree): Tree = {
      def flatten(t: Tree): (Tree, List[List[Tree]]) = t match {
        case Apply(fun, args) => val (h, ls) = flatten(fun); (h, ls :+ args)
        case other            => (other, Nil)
      }
      val (head, argLists) = flatten(tree)
      val recvOpt          = head match {
        // `new T` is not an ANF-bindable value: a `Select(New(T), <init>)` head
        // must be kept verbatim and only the constructor arguments transformed.
        case Select(New(_), _)               => None
        case TypeApply(Select(New(_), _), _) => None
        case Select(r, _)                    => Some(r)
        case TypeApply(Select(r, _), _)      => Some(r)
        case _                               => None
      }
      val parts = recvOpt.toList ++ argLists.flatten
      transformParts(parts) { vals =>
        val (newHead, argVals) = recvOpt match {
          case Some(_) =>
            val rv = vals.head
            val nh = head match {
              case Select(_, n)                   => Select(rv, n)
              case TypeApply(Select(_, n), targs) => TypeApply(Select(rv, n), targs)
              case h                              => h
            }
            (nh, vals.tail)
          case None => (head, vals)
        }
        var rest    = argVals
        val rebuilt = argLists.foldLeft(newHead) { (acc, al) =>
          val (these, more) = rest.splitAt(al.length)
          rest = more
          Apply(acc, these)
        }
        bind(rebuilt)(k)
      }
    }

    def transformWhile(cond: Tree, bodyT: Tree)(k: Tree => Tree): Tree = {
      val loop      = fresh("asyncLoop$")
      val out       = fresh("loopOut$")
      val c0        = fresh("cond0$")
      val b0        = fresh("body0$")
      val cv        = fresh("c$")
      val condA     = transform(cond)(pureReturn)
      val bodyA     = transform(bodyT)(_ => q"$AsyncObj.succeed(())")
      val loopAsync =
        q"""
          {
            def $loop(): _root_.zio.blocks.async.Async[Unit] = {
              var $out: _root_.zio.blocks.async.Async[Unit] =
                null.asInstanceOf[_root_.zio.blocks.async.Async[Unit]]
              try {
                while ($out == null) {
                  val $c0: Any = ${condA.duplicate}
                  if ($c0.isInstanceOf[$Pollable]) {
                    $out = $OpsObj($c0.asInstanceOf[_root_.zio.blocks.async.Async[Boolean]]).flatMap { ($cv: Boolean) =>
                      if ($cv) $OpsObj(${bodyA.duplicate}).flatMap { (_: Unit) => $loop() }
                      else $AsyncObj.succeed(())
                    }
                  } else if (!$c0.asInstanceOf[Boolean]) {
                    $out = $AsyncObj.succeed(())
                  } else {
                    val $b0: Any = ${bodyA.duplicate}
                    if ($b0.isInstanceOf[$Pollable]) {
                      $out = $OpsObj($b0.asInstanceOf[_root_.zio.blocks.async.Async[Unit]]).flatMap { (_: Unit) => $loop() }
                    }
                  }
                }
                $out
              } catch { case t: _root_.java.lang.Throwable => $AsyncObj.fail(t) }
            }
            $loop()
          }
        """
      asyncBind(loopAsync)(_ => k(q"()"))
    }

    def transformTry(block: Tree, catches: List[CaseDef], finalizer: Tree)(k: Tree => Tree): Tree = {
      val bodyAsync = transform(block)(pureReturn)
      val caught    =
        if (catches.isEmpty) bodyAsync
        else {
          val err    = fresh("err$")
          val ccases = catches.map { cd =>
            if (cd.guard.nonEmpty && containsAwait(cd.guard))
              c.abort(
                cd.guard.pos,
                "`.await` in a catch guard is not supported; compute the awaited value before the try/catch."
              )
            CaseDef(cd.pat, cd.guard, transform(cd.body)(pureReturn))
          } :+ cq"_ => $AsyncObj.fail($err)"
          q"""$OpsObj($bodyAsync).catchAll { ($err: _root_.java.lang.Throwable) => ${safe(
              q"$err match { case ..$ccases }"
            )} }"""
        }
      val withFin =
        if (finalizer.isEmpty) caught
        else {
          val runFin = fresh("runFinally$")
          val tr     = fresh("tr$")
          val v      = fresh("v$")
          val e      = fresh("e$")
          val mat    = fresh("materialized$")
          val finA   = transform(finalizer)(_ => q"$AsyncObj.succeed(())")
          // Materialise the body outcome as a `Try`, run the finalizer exactly
          // once, then restore the outcome (so a finalizer failure overrides).
          safe(q"""
            {
              def $runFin(): _root_.zio.blocks.async.Async[Unit] = $finA
              val $mat =
                $OpsObj($OpsObj($caught).map(${lam(v, q"_root_.scala.util.Success($v): _root_.scala.util.Try[Any]")}))
                  .catchAll { ($e: _root_.java.lang.Throwable) => $AsyncObj.succeed(_root_.scala.util.Failure($e): _root_.scala.util.Try[Any]) }
              $OpsObj($mat).flatMap { ($tr: _root_.scala.util.Try[Any]) =>
                $tr match {
                  case _root_.scala.util.Success($v) => $OpsObj($runFin()).flatMap { (_: Unit) => $AsyncObj.succeed($v) }
                  case _root_.scala.util.Failure($e) => $OpsObj($runFin()).flatMap { (_: Unit) => $AsyncObj.fail($e) }
                }
              }
            }
          """)
        }
      asyncBind(withFin)(k)
    }

    def transform(tree: Tree)(k: Tree => Tree): Tree =
      if (!containsAwait(tree)) bind(tree)(k)
      else
        tree match {
          case AwaitCall(fa) =>
            transform(fa) { faVal =>
              val tpt = nextAwaitTpt() // dequeued AFTER inner awaits in `fa`, matching queue order
              asyncBindT(faVal, tpt)(k)
            }

          case Block(stmts, expr) =>
            transformBlock(stmts, expr)(k)

          case If(cond, thenp, elsep) =>
            transform(cond) { c0 =>
              asyncBind(q"if ($c0) ${transform(thenp)(pureReturn)} else ${transform(elsep)(pureReturn)}")(k)
            }

          case Match(scrut, cases) =>
            transform(scrut) { s0 =>
              val newCases = cases.map { cd =>
                if (cd.guard.nonEmpty && containsAwait(cd.guard))
                  c.abort(
                    cd.guard.pos,
                    "`.await` in a pattern guard is not supported; compute the awaited value before the match."
                  )
                CaseDef(cd.pat, cd.guard, transform(cd.body)(pureReturn))
              }
              asyncBind(q"$s0 match { case ..$newCases }")(k)
            }

          case Try(block, catches, finalizer) =>
            transformTry(block, catches, finalizer)(k)

          case Throw(ex) =>
            transform(ex)(v => q"$AsyncObj.fail($v)")

          // `foldLeft(z)(op)` whose `op` body awaits. A left fold is sequential
          // on every backend, so emit the threaded-accumulator drain. Transform
          // the receiver, then the initial accumulator (consuming their own
          // awaits in queue order — recv before z before the op body, which the
          // emit consumes), then emit. Must precede `HofAwaitCall` (whose outer
          // shape is a single `Apply`) and the generic application-spine case.
          case FoldLeftAwaitCall(recv, z, acc, x, fbody) =>
            val bTpe = dequeueFoldResult()
            transform(recv)(rv => transform(z)(zv => emitHofFoldLeft(rv, zv, acc.name, x.name, fbody, bTpe)(k)))

          // `foldRight(z)(op)` whose `op` body awaits — right-associative, so the
          // receiver is materialized and drained in reverse (op runs right-to-
          // left). Double `Apply` shape (curried), so it must precede
          // `HofAwaitCall` and the generic application-spine case.
          case FoldRightAwaitCall(recv, z, x, acc, fbody) =>
            val bTpe = dequeueFoldRightResult()
            transform(recv)(rv => transform(z)(zv => emitHofFoldRight(rv, zv, x.name, acc.name, fbody, bTpe)(k)))

          // `reduce(op)` / `reduceLeft(op)` whose `op` body awaits — `foldLeft`
          // seeded by the first element (an empty receiver fails with
          // `UnsupportedOperationException`). Single `Apply` shape, so it must
          // precede `HofAwaitCall` and the generic application-spine case.
          case ReduceAwaitCall(recv, acc, x, fbody) =>
            val bTpe = dequeueReduceResult()
            transform(recv)(rv => emitHofReduce(rv, acc.name, x.name, fbody, bTpe)(k))

          // `collect(pf)` whose partial function has an awaiting case body. The
          // PF literal is an anonymous-class `Apply`, so it must precede
          // `HofAwaitCall` and the generic application-spine case. Restricted to
          // builder-backed receivers (`List` / `Vector` / `Set`) in the typed
          // pass; the element type `B` is the result collection's `typeArgs.head`.
          case CollectAwaitCall(recv, cases) =>
            val resultTpe = dequeueCollectResult()
            val bTpe =
              if (resultTpe != null && resultTpe != NoType) resultTpe.typeArgs.headOption.getOrElse(NoType)
              else NoType
            transform(recv)(rv => emitCollect(rv, cases, bTpe, resultTpe)(k))

          // HOF-closure await (Phase 5c): `recv.map(...)` / `recv.foreach(...)`.
          // Dequeue the recorded result element type (kept in sync with the typed
          // traversal order; `Unit`/`NoType` for `foreach`), transform the
          // receiver, then emit the per-HOF rewrite — eager `map` (strict-map +
          // `collectAll`) or lazy sequential `foreach`. Must precede the generic
          // application-spine case.
          case HofAwaitCall(recv, m, param, fbody) =>
            val bTpe   = dequeueHofElem()
            val kind   = dequeueHofKind()
            val resTpe = dequeueHofResult()
            // Materialize any for-comprehension guard (`xs.withFilter(g)`) into a
            // strict `xs.filter(g)` so the emitted rewrite can iterate the
            // collection (`List` / `Option` / `Vector` / `Set`).
            val recv0 = defilterReceiver(recv)
            (kind, m) match {
              // Short-circuiting predicate scans (`find`/`exists`/`forall`) are
              // receiver-kind-agnostic: every whitelisted receiver has `.iterator`,
              // and the scan stops at the first decisive element. Must precede the
              // kind-specific catch-alls (e.g. `("option", _)`).
              case (_, "exists") =>
                transform(recv0)(rv => emitPredicateScan(rv, param.name, fbody, "exists", resTpe)(k))
              case (_, "forall") =>
                transform(recv0)(rv => emitPredicateScan(rv, param.name, fbody, "forall", resTpe)(k))
              case (_, "find") => transform(recv0)(rv => emitPredicateScan(rv, param.name, fbody, "find", resTpe)(k))
              // Result-collection-preserving predicate filters (`filter` /
              // `filterNot`): receiver-kind-aware (Option vs builder-backed),
              // dispatched inside `emitFilter`. Must precede the kind-specific
              // catch-alls. The recorded element type `bTpe` is the SOURCE
              // element type (filter does not transform), `resTpe` the result
              // collection type.
              case (_, "filter") =>
                transform(recv0)(rv => emitFilter(rv, kind, param.name, fbody, negate = false, bTpe, resTpe)(k))
              case (_, "filterNot") =>
                transform(recv0)(rv => emitFilter(rv, kind, param.name, fbody, negate = true, bTpe, resTpe)(k))
              // Prefix-ordered predicates (`takeWhile` / `dropWhile`) — restricted
              // to ordered `Seq` receivers (`List` / `Vector`) in the typed pass,
              // so both kinds (`"list"` / `"iterable"`) use the receiver's own
              // `iterableFactory` builder (element type unchanged). Must precede
              // the kind-specific catch-alls.
              case (_, "takeWhile") =>
                val bTpt: Tree   = if (bTpe != null && bTpe != NoType) TypeTree(bTpe) else TypeTree()
                val resTpt: Tree = if (resTpe != null && resTpe != NoType) TypeTree(resTpe) else TypeTree()
                transform(recv0) { rv =>
                  emitTakeWhile(rv, param.name, fbody, drop = false, bTpt, resTpt, q"$rv.iterableFactory.newBuilder[$bTpt]")(
                    k
                  )
                }
              case (_, "dropWhile") =>
                val bTpt: Tree   = if (bTpe != null && bTpe != NoType) TypeTree(bTpe) else TypeTree()
                val resTpt: Tree = if (resTpe != null && resTpe != NoType) TypeTree(resTpe) else TypeTree()
                transform(recv0) { rv =>
                  emitTakeWhile(rv, param.name, fbody, drop = true, bTpt, resTpt, q"$rv.iterableFactory.newBuilder[$bTpt]")(
                    k
                  )
                }
              case ("option", "foreach") => transform(recv0)(rv => emitOptionForeach(rv, param.name, fbody)(k))
              case ("option", "flatMap") => transform(recv0)(rv => emitOptionFlatMap(rv, param.name, fbody, bTpe)(k))
              case ("option", _)         => transform(recv0)(rv => emitOptionMap(rv, param.name, fbody, bTpe)(k))
              // Generic strict collections (`Vector`, immutable `Set`): lazy
              // sequential map/flatMap (builder-drain, result type preserved) and
              // the already-generic iterator `foreach`.
              case ("iterable", "foreach") => transform(recv0)(rv => emitHofForeach(rv, param.name, fbody)(k))
              case ("iterable", "flatMap") =>
                transform(recv0)(rv => emitCollFlatMap(rv, param.name, fbody, bTpe, resTpe)(k))
              case ("iterable", _) =>
                transform(recv0)(rv => emitCollMap(rv, param.name, fbody, bTpe, resTpe)(k))
              // Immutable `Map`: entries are `(K, V)` tuples; `foreach` drains
              // them through the generic iterator, while `map`/`flatMap` rebuild
              // a `Map[K2, V2]` (or widen to an `Iterable` for non-pair results)
              // via the receiver's own factory.
              case ("map", "foreach") => transform(recv0)(rv => emitHofForeach(rv, param.name, fbody)(k))
              case ("map", "flatMap") =>
                transform(recv0)(rv => emitMapMapLike(rv, param.name, fbody, bTpe, resTpe, flat = true)(k))
              case ("map", _) =>
                transform(recv0)(rv => emitMapMapLike(rv, param.name, fbody, bTpe, resTpe, flat = false)(k))
              case (_, "foreach") => transform(recv0)(rv => emitHofForeach(rv, param.name, fbody)(k))
              case (_, "flatMap") => transform(recv0)(rv => emitHofFlatMap(rv, param.name, fbody, bTpe)(k))
              case _              => transform(recv0)(rv => emitHofMap(rv, param.name, fbody, bTpe)(k))
            }

          // Short-circuiting `&&` / `||` must NOT eagerly evaluate the
          // right-hand side. An ANF application spine would; rewrite them to
          // `if` so the RHS (which may await and/or have side effects) runs only
          // when the left operand demands it.
          case Apply(Select(lhs, n), List(rhs)) if n.decodedName.toString == "&&" =>
            transform(lhs)(l => asyncBind(q"if ($l) ${transform(rhs)(pureReturn)} else $AsyncObj.succeed(false)")(k))

          case Apply(Select(lhs, n), List(rhs)) if n.decodedName.toString == "||" =>
            transform(lhs)(l => asyncBind(q"if ($l) $AsyncObj.succeed(true) else ${transform(rhs)(pureReturn)}")(k))

          case Typed(expr, tpt) if !tpt.isEmpty && !containsAwait(tpt) =>
            // Preserve a genuine ascription: it can drive overload resolution /
            // widening. Skip ascriptions whose tree carries an await (e.g. the
            // `: @unchecked` wrapper the typer synthesizes for `val _ = e`),
            // which would otherwise replant the await in type position.
            transform(expr)(v => bind(q"($v: $tpt)")(k))

          case Typed(expr, _) =>
            transform(expr)(k)

          case Assign(lhs, _) if containsAwait(lhs) =>
            c.abort(lhs.pos, "`.await` in the left-hand side of an assignment is not supported by `Async.async`.")

          case Assign(lhs, rhs) =>
            transform(rhs)(v => bind(q"$lhs = $v")(k))

          case LabelDef(_, _, If(cond, Block(List(loopBody), _), _)) =>
            transformWhile(cond, loopBody)(k)
          case LabelDef(_, _, Block(List(If(cond, Block(stmts, _), _)), _)) =>
            transformWhile(cond, Block(stmts.init, stmts.last))(k)

          case Apply(_, _) | TypeApply(_, _) | Select(_, _) =>
            transformApplySpine(tree)(k)

          case _ =>
            c.abort(tree.pos, "This expression position is not supported by the Scala 2 `Async.async` macro.")
        }

    // ---- mutable-var boxing (pre-pass, on the typed tree) -------------------

    def refModule(tpe: Type): (Tree, List[Tree]) = {
      val t            = tpe.dealias.widen
      val d            = definitions
      def m(n: String) = (q"_root_.scala.runtime.${TermName(n)}", List.empty[Tree])
      if (t =:= d.IntTpe) m("IntRef")
      else if (t =:= d.LongTpe) m("LongRef")
      else if (t =:= d.DoubleTpe) m("DoubleRef")
      else if (t =:= d.FloatTpe) m("FloatRef")
      else if (t =:= d.ShortTpe) m("ShortRef")
      else if (t =:= d.CharTpe) m("CharRef")
      else if (t =:= d.ByteTpe) m("ByteRef")
      else if (t =:= d.BooleanTpe) m("BooleanRef")
      else (q"_root_.scala.runtime.ObjectRef", List[Tree](TypeTree(t)))
    }

    def varValueType(vd: ValDef): Type =
      if (vd.tpt.tpe != null && vd.tpt.tpe != NoType) vd.tpt.tpe.dealias.widen
      else vd.symbol.asTerm.info.resultType.dealias.widen

    def isLocalVar(vd: ValDef): Boolean =
      vd.symbol != null && vd.symbol != NoSymbol && vd.symbol.isTerm &&
        vd.symbol.asTerm.isVar && !vd.symbol.isParameter && !vd.symbol.owner.isClass

    def boxVars(root: Tree): Tree = {
      val cells = scala.collection.mutable.Map.empty[Symbol, TermName]
      new Traverser {
        override def traverse(t: Tree): Unit = t match {
          case vd: ValDef if isLocalVar(vd) =>
            cells(vd.symbol) = fresh(vd.name.decodedName.toString + "$ref")
            traverse(vd.rhs)
          case _ => super.traverse(t)
        }
      }.traverse(root)
      if (cells.isEmpty) root
      else {
        val rewritten = new Transformer {
          override def transform(t: Tree): Tree = t match {
            case vd: ValDef if cells.contains(vd.symbol) =>
              val (module, targs) = refModule(varValueType(vd))
              val init            = transform(vd.rhs)
              val create          = if (targs.isEmpty) q"$module.create($init)" else q"$module.create[..$targs]($init)"
              ValDef(Modifiers(), cells(vd.symbol), TypeTree(), create)
            case Assign(lhs, rhs) if lhs.symbol != null && cells.contains(lhs.symbol) =>
              q"${Ident(cells(lhs.symbol))}.elem = ${transform(rhs)}"
            case id: Ident if id.symbol != null && cells.contains(id.symbol) =>
              q"${Ident(cells(id.symbol))}.elem"
            case _ => super.transform(t)
          }
        }.transform(root)
        c.typecheck(c.untypecheck(rewritten))
      }
    }

    // ---- entry --------------------------------------------------------------

    // `awaitElemTypes` is built (and non-Async `.await`s rejected) above, from
    // the typed body. A body with no `.await` is the zero-suspension fast path:
    // it never needs var-boxing and collapses to `Async.attempt`.
    val result =
      if (awaitElemTypes.isEmpty)
        q"$AsyncObj.attempt(${c.untypecheck(body.tree.duplicate)})"
      else {
        val prepared = c.untypecheck(boxVars(body.tree).duplicate)
        precheck(prepared)
        val out = transform(prepared)(pureReturn)
        if (awaitElemTypes.nonEmpty)
          c.abort(
            body.tree.pos,
            "internal `Async.async` macro error: not all `.await`s were rewritten (unsupported await position); " +
              "please report this."
          )
        out
      }
    // Ascribe the encoded result type (as the Scala 3 backend also does) so any
    // intermediate `Try`-erased value type does not leak into the public type.
    c.Expr[zio.blocks.async.Async[A]](q"$result.asInstanceOf[_root_.zio.blocks.async.Async[${weakTypeOf[A]}]]")
  }
}
