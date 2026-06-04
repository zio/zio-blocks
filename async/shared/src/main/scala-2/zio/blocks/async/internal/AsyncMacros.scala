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

    // ---- await element types (from the TYPED body, in transform order) ------
    //
    // The transform runs on an `untypecheck`ed tree, so it cannot read the
    // element type of an awaited `Async[A]`. We recover those types here from
    // the still-typed `body.tree`: the `.await` node's own `tpe` is exactly `A`
    // (its signature is `def await: A`). We record them in the SAME order the
    // CPS transform consumes them — inner awaits before the enclosing await
    // (because `transform(fa)` runs before the outer `asyncBind`) — and the
    // transform dequeues one per real `.await`. This lets us ascribe each
    // generated `flatMap` lambda parameter, which is mandatory for awaited
    // `Async[Nothing]` (see `asyncBindT`).
    val awaitElemTypes: scala.collection.mutable.Queue[Type] = {
      val q = scala.collection.mutable.Queue.empty[Type]
      new Traverser {
        override def traverse(tt: Tree): Unit = tt match {
          case AwaitCall(inner) =>
            traverse(inner) // inner awaits first (matches transform order)
            q.enqueue(if (tt.tpe != null) tt.tpe.dealias.widen else NoType)
          case _ => super.traverse(tt)
        }
      }.traverse(body.tree)
      q
    }

    def nextAwaitTpt(): Tree = {
      val tpe = if (awaitElemTypes.nonEmpty) awaitElemTypes.dequeue() else NoType
      if (tpe == null || tpe == NoType || tpe =:= definitions.AnyTpe) TypeTree() else TypeTree(tpe)
    }

    // ---- pre-flight: reject positions we do not (yet) rewrite ---------------

    def precheck(t: Tree): Unit =
      new Traverser {
        override def traverse(tt: Tree): Unit = tt match {
          case AwaitCall(inner)                           => traverse(inner)
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
        // A throwaway `val _ = rhs` (`_` is renamed to a synthetic `x$N`):
        // evaluate `rhs` for its effect / await and discard the value.
        case (vd: ValDef) :: rest if vd.name == termNames.WILDCARD || vd.name.decodedName.toString.startsWith("x$") =>
          transform(vd.rhs)(_ => transformBlock(rest, expr)(k))
        case (vd: ValDef) :: rest =>
          transform(vd.rhs)(v => safe(q"""{ val ${vd.name} = $v; ${transformBlock(rest, expr)(k)} }"""))
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
        case Select(r, _)               => Some(r)
        case TypeApply(Select(r, _), _) => Some(r)
        case _                          => None
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

          case Typed(expr, _) =>
            // Drop the ascription (the inner expression is re-typechecked).
            transform(expr)(k)

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

    val prepared = c.untypecheck(boxVars(body.tree).duplicate)
    val result   =
      if (!containsAwait(prepared)) q"$AsyncObj.attempt($prepared)"
      else {
        precheck(prepared)
        transform(prepared)(pureReturn)
      }
    // Ascribe the encoded result type (as the Scala 3 backend also does) so any
    // intermediate `Try`-erased value type does not leak into the public type.
    c.Expr[zio.blocks.async.Async[A]](q"$result.asInstanceOf[_root_.zio.blocks.async.Async[${weakTypeOf[A]}]]")
  }
}
