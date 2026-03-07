package zio.blocks.scope

import scala.quoted.*

private[scope] object UseMacros {

  // ── Public inline entry point (N=1 only; N≥2 use applyN* directly) ─────────

  inline def check[A, B](inline f: A => B): Unit =
    ${ validateUse[A, B]('f) }

  // ── Validate-only entry points (N=1, used by infix transparent inline $) ─────

  def validateUse[A: Type, B: Type](f: Expr[A => B])(using q: Quotes): Expr[Unit] = {
    import q.reflect.*
    validateLambdaExpr(f.asTerm, f.asTerm.pos)
  }

  // ── Validate-and-apply entry points (N=2..5) ──────────────────────────────
  //
  // For N≥2, transparent inline + summonFrom in the method body produces a type
  // intersection (B & $[B]) instead of the expected union, because Scala 3's
  // constraint solver can't narrow the two branches when multiple abstract type
  // parameters are simultaneously in play. The fix: do the Unscoped[B] check
  // inside the macro itself (via Expr.summon) and emit only the correct branch,
  // giving the call site an unambiguous precise return type.
  //
  // Each applyNk is a thin public entry point that names the arity-specific types
  // for the type-checker and calls the shared applyNImpl.

  def applyN2[A1: Type, A2: Type, B: Type](
    self: Expr[Scope],
    sa1: Expr[Any],
    sa2: Expr[Any],
    f: Expr[(A1, A2) => B]
  )(using q: Quotes): Expr[Any] = {
    import q.reflect.*
    applyNImpl[B](self, List(sa1, sa2), List(Type.of[A1], Type.of[A2]), f.asTerm)
  }

  def applyN3[A1: Type, A2: Type, A3: Type, B: Type](
    self: Expr[Scope],
    sa1: Expr[Any],
    sa2: Expr[Any],
    sa3: Expr[Any],
    f: Expr[(A1, A2, A3) => B]
  )(using q: Quotes): Expr[Any] = {
    import q.reflect.*
    applyNImpl[B](self, List(sa1, sa2, sa3), List(Type.of[A1], Type.of[A2], Type.of[A3]), f.asTerm)
  }

  def applyN4[A1: Type, A2: Type, A3: Type, A4: Type, B: Type](
    self: Expr[Scope],
    sa1: Expr[Any],
    sa2: Expr[Any],
    sa3: Expr[Any],
    sa4: Expr[Any],
    f: Expr[(A1, A2, A3, A4) => B]
  )(using q: Quotes): Expr[Any] = {
    import q.reflect.*
    applyNImpl[B](
      self,
      List(sa1, sa2, sa3, sa4),
      List(Type.of[A1], Type.of[A2], Type.of[A3], Type.of[A4]),
      f.asTerm
    )
  }

  def applyN5[A1: Type, A2: Type, A3: Type, A4: Type, A5: Type, B: Type](
    self: Expr[Scope],
    sa1: Expr[Any],
    sa2: Expr[Any],
    sa3: Expr[Any],
    sa4: Expr[Any],
    sa5: Expr[Any],
    f: Expr[(A1, A2, A3, A4, A5) => B]
  )(using q: Quotes): Expr[Any] = {
    import q.reflect.*
    applyNImpl[B](
      self,
      List(sa1, sa2, sa3, sa4, sa5),
      List(Type.of[A1], Type.of[A2], Type.of[A3], Type.of[A4], Type.of[A5]),
      f.asTerm
    )
  }

  /**
   * Shared implementation for all N≥2 arities.
   *
   * Validates the lambda, builds the function-application term by casting each
   * scoped argument to its underlying type using reflect-level Apply, then
   * delegates to [[wrapOrUnwrap]] to emit the correct closed-scope check and
   * either the unwrapped `B` or re-wrapped `$[B]` expression.
   *
   * @param self
   *   the scope instance expression
   * @param sas
   *   the scoped-value expressions ($[Ai]) in order
   * @param inputTypes
   *   the underlying types Ai, as `Type[?]` existentials in order — must match
   *   `sas` length
   * @param fTerm
   *   the lambda term (already passed through the inline splice)
   */
  private def applyNImpl[B: Type](using
    q: Quotes
  )(
    self: Expr[Scope],
    sas: List[Expr[Any]],
    inputTypes: List[Type[?]],
    fTerm: q.reflect.Term
  ): Expr[Any] = {
    import quotes.reflect.*

    validateLambdaExpr(fTerm, fTerm.pos)

    // Build f(sa1.asInstanceOf[A1], ..., saN.asInstanceOf[AN]) reflectively.
    // Each cast: '{ sa.asInstanceOf[Ai] } gives a Term typed at Ai.
    val castArgs: List[Term] = sas.zip(inputTypes).map { case (sa, tpe) =>
      tpe match {
        case '[a] => '{ $sa.asInstanceOf[a] }.asTerm
      }
    }

    // To call the FunctionN lambda, we need Select(fTerm, "apply") then Apply.
    // This is equivalent to fTerm.apply(arg1, ..., argN) in reflect terms.
    val applyMethod: Term = Select.unique(fTerm, "apply")
    val call: Expr[B]     = Apply(applyMethod, castArgs).asExprOf[B]

    wrapOrUnwrap[B](self, call)
  }

  /**
   * At macro-expansion time, checks whether `Unscoped[B]` is available.
   *
   * If it is, emits `{ closedCheck; call }` returning `B` (auto-unwrap). If
   * not, emits `{ closedCheck; call.asInstanceOf[self.type#$[B]] }` (re-wrap).
   * The closed-scope check is included in the emitted expression so that the
   * macro body is the sole splice in the transparent inline def.
   *
   * The call site sees the precise return type — no intersection type ambiguity
   * from transparent inline + summonFrom.
   */
  private def wrapOrUnwrap[B: Type](
    self: Expr[Scope],
    call: Expr[B]
  )(using Quotes): Expr[Any] = {
    import quotes.reflect.*

    // Runtime closed-scope guard (same message as N=1 path)
    val closedCheck: Expr[Unit] = '{
      if ($self.isClosed)
        throw new IllegalStateException(
          zio.blocks.scope.internal.ErrorMessages
            .renderUseOnClosedScope($self.scopeDisplayName, color = false)
        )
    }

    Expr.summon[Unscoped[B]] match {
      case Some(_) =>
        // B is Unscoped — return B directly
        '{ $closedCheck; $call }
      case None =>
        // B is not Unscoped — cast result to self.type#$[B]
        // Build TypeRepr for self.type # $[B] via TypeSelect applied to B
        val wrappedTpe = TypeSelect(self.asTerm, "$").tpe.appliedTo(TypeRepr.of[B])
        wrappedTpe.asType match {
          case '[t] => '{ $closedCheck; $call.asInstanceOf[t] }
        }
    }
  }

  // ── Shared internal implementation ────────────────────────────────────────

  /**
   * Extract param symbols + body from any arity lambda, handling all
   * compiler-inserted wrappers.
   */
  private def extractLambdaN(using
    Quotes
  )(tree: quotes.reflect.Term): Option[(List[quotes.reflect.Symbol], quotes.reflect.Term)] = {
    import quotes.reflect.*

    tree match {
      // inline params are always wrapped in Inlined nodes
      case Inlined(_, _, inner) => extractLambdaN(inner)
      // eta-expand / empty-binding wrapper
      case Block(Nil, inner) => extractLambdaN(inner)
      // canonical Scala 3 TASTy form for any-arity lambda: Block(List(DefDef), Closure)
      case Block(
            List(DefDef(_, List(TermParamClause(params)), _, Some(body))),
            _
          ) =>
        Some((params.map(_.symbol), body))
      // Lambda extractor — matches the Block(DefDef, Closure) form for any arity
      case Lambda(params, body) =>
        Some((params.map(_.symbol), body.asInstanceOf[Term]))
      case _ => None
    }
  }

  /**
   * Core validation: extract params from the lambda, build the param-info map,
   * validate the body. All per-arity entry points delegate here.
   */
  private def validateLambdaExpr(using
    Quotes
  )(f: quotes.reflect.Term, pos: quotes.reflect.Position): Expr[Unit] = {
    import quotes.reflect.*

    val (paramSymbols, body) = extractLambdaN(f) match {
      case Some(r) => r
      case None    =>
        report.errorAndAbort(
          "$ requires a lambda literal, e.g. $(x)(a => a.method()). " +
            "Method references and variables are not supported.",
          pos
        )
    }

    // Map each param symbol → (1-based index, source name) for error messages
    val paramInfo: Map[Symbol, (Int, String)] =
      paramSymbols.zipWithIndex.map { case (s, i) => s -> (i + 1, s.name) }.toMap

    validateBody(body, paramInfo)
    '{ () }
  }

  // ── Validation traversals ─────────────────────────────────────────────────

  /**
   * Validates that every reference to any param symbol in `paramInfo` appears
   * only in method-receiver position (qualifier of a Select). All other uses
   * are compile errors.
   */
  private def validateBody(using
    Quotes
  )(tree: quotes.reflect.Term, paramInfo: Map[quotes.reflect.Symbol, (Int, String)]): Unit = {
    import quotes.reflect.*

    def isParamRef(t: Tree): Boolean = t match {
      case Ident(_) => paramInfo.contains(t.symbol)
      case _        => false
    }

    // Detects compiler-inserted boxing/unboxing conversions like
    // Predef.boolean2Boolean, Boolean.unbox, Int.unbox, etc.
    def isBoxingConversion(fn: Term): Boolean = {
      val sym         = fn.symbol
      val name        = sym.name
      val boxingNames = Set(
        "boolean2Boolean",
        "Boolean2boolean",
        "byte2Byte",
        "Byte2byte",
        "short2Short",
        "Short2short",
        "char2Character",
        "Character2char",
        "int2Integer",
        "Integer2int",
        "long2Long",
        "Long2long",
        "float2Float",
        "Float2float",
        "double2Double",
        "Double2double",
        "unbox",
        "box"
      )
      boxingNames.contains(name) && {
        val ownerName = sym.owner.fullName
        ownerName.startsWith("scala.Predef") ||
        ownerName.startsWith("scala.") ||
        ownerName.startsWith("java.lang.")
      }
    }

    // Statement-level traversal (handles non-Term statements in blocks)
    def traverseChildren(t: Tree): Unit = t match {
      case term: Term         => traverseTerm(term)
      case _: TypeTree        => ()
      case _: Import          => ()
      case _: Export          => ()
      case Block(stmts, expr) =>
        stmts.foreach(traverseChildren)
        traverseChildren(expr)
      case vd: ValDef =>
        vd.rhs.foreach(traverseTerm)
      case dd: DefDef =>
        // Nested def — body is a new scope; param must not be captured there
        dd.rhs.foreach(rhs => checkNoCapture(rhs, paramInfo))
      case CaseDef(_, guard, rhs) =>
        guard.foreach(traverseTerm)
        traverseTerm(rhs)
      // Anonymous class — all member defs/vals are capture-checked
      case cd: ClassDef =>
        cd.body.foreach {
          case dd: DefDef => dd.rhs.foreach(rhs => checkNoCapture(rhs, paramInfo))
          case vd: ValDef => vd.rhs.foreach(rhs => checkNoCapture(rhs, paramInfo))
          case _          => ()
        }
      case _ => ()
    }

    def traverseTerm(t: Term): Unit = t match {

      // ── Allowed: param in receiver position of a Select ──────────────────
      case Select(qualifier, _) if isParamRef(qualifier) =>
      // param.method or param.field — valid; do not recurse into qualifier

      // ── Rejected: bare param reference (not in receiver position) ─────────
      case ident @ Ident(_) if paramInfo.contains(ident.symbol) =>
        val (idx, name) = paramInfo(ident.symbol)
        report.errorAndAbort(
          s"Parameter $idx ('$name') must only be used as a method receiver. " +
            s"It cannot be returned, stored, passed as an argument, or captured. " +
            s"Scoped values may only be used as a method receiver (e.g., $name.method()).",
          ident.pos
        )

      // ── Function application ──────────────────────────────────────────────
      case Apply(fn, args) =>
        // Special case: compiler-inserted boxing conversion with param as sole arg.
        // e.g., Predef.int2Integer(param) — the param is boxed but stays in receiver position.
        if (args.length == 1 && isParamRef(args.head) && isBoxingConversion(fn)) {
          // valid — param is immediately boxed by the compiler
          ()
        } else {
          traverseTerm(fn)
          args.foreach { arg =>
            if (isParamRef(arg)) {
              val (idx, name) = paramInfo(arg.symbol)
              report.errorAndAbort(
                s"Parameter $idx ('$name') cannot be passed as an argument to a function or method. " +
                  s"Scoped values may only be used as a method receiver (e.g., $name.method()).",
                arg.pos
              )
            }
            traverseTerm(arg)
          }
        }

      // ── Named argument — param cannot appear as the named value ──────────
      case NamedArg(_, value) =>
        if (isParamRef(value)) {
          val (idx, name) = paramInfo(value.symbol)
          report.errorAndAbort(
            s"Parameter $idx ('$name') cannot be passed as an argument to a function or method. " +
              s"Scoped values may only be used as a method receiver (e.g., $name.method()).",
            value.pos
          )
        }
        traverseTerm(value)

      case TypeApply(fn, _) =>
        traverseTerm(fn)

      // ── Select on non-param — recurse into qualifier ──────────────────────
      case Select(qualifier, _) =>
        traverseTerm(qualifier)

      // ── Block ─────────────────────────────────────────────────────────────
      case Block(stmts, expr) =>
        stmts.foreach(traverseChildren)
        traverseTerm(expr)

      // ── Nested lambda — param captured in closure ─────────────────────────
      // The Closure leaf itself (meth reference) is safe; the DefDef body is
      // capture-checked via traverseChildren above. Lambda(params, body) is the
      // extractor for Block(List(DefDef), Closure) — match it here for clarity.
      case Lambda(_, body) =>
        checkNoCapture(body, paramInfo)

      // ── Assignment ───────────────────────────────────────────────────────
      // Traverse lhs too: d.mutableField = expr has param as lhs receiver.
      // The Select-as-receiver rule allows it; bare Ident lhs is impossible
      // (params are vals). Also reject param on rhs (storing param in a var).
      case Assign(lhs, rhs) =>
        traverseTerm(lhs)
        if (isParamRef(rhs)) {
          val (idx, name) = paramInfo(rhs.symbol)
          report.errorAndAbort(
            s"Parameter $idx ('$name') cannot be assigned to a variable. " +
              s"Scoped values may only be used as a method receiver (e.g., $name.method()).",
            rhs.pos
          )
        }
        traverseTerm(rhs)

      // ── If expression ─────────────────────────────────────────────────────
      case If(cond, thenp, elsep) =>
        traverseTerm(cond)
        traverseTerm(thenp)
        traverseTerm(elsep)

      // ── Match expression ──────────────────────────────────────────────────
      case Match(scrutinee, cases) =>
        traverseTerm(scrutinee)
        cases.foreach { case CaseDef(_, guard, rhs) =>
          guard.foreach(traverseTerm)
          traverseTerm(rhs)
        }

      // ── While loop — previously fell through to case _ => () ─────────────
      case While(cond, body) =>
        traverseTerm(cond)
        traverseTerm(body)

      // ── Try/catch/finally — previously fell through to case _ => () ───────
      case Try(body, cases, finalizer) =>
        traverseTerm(body)
        cases.foreach { case CaseDef(_, guard, rhs) =>
          guard.foreach(traverseTerm)
          traverseTerm(rhs)
        }
        finalizer.foreach(traverseTerm)

      // ── Return — previously fell through to case _ => () ─────────────────
      case Return(expr, _) =>
        if (isParamRef(expr)) {
          val (idx, name) = paramInfo(expr.symbol)
          report.errorAndAbort(
            s"Parameter $idx ('$name') cannot be returned from a function. " +
              s"Scoped values may only be used as a method receiver (e.g., $name.method()).",
            expr.pos
          )
        }
        traverseTerm(expr)

      case Typed(expr, _) =>
        traverseTerm(expr)

      case Inlined(_, bindings, body) =>
        bindings.foreach(traverseChildren)
        traverseTerm(body)

      case Repeated(elems, _) =>
        elems.foreach(traverseTerm)

      // ── Leaf nodes — no param references possible ─────────────────────────
      case _: Literal => ()
      case _: Ident   => () // non-param idents are fine
      case _: This    => ()
      case _: New     => ()
      // Closure leaf: just a method reference to the synthetic DefDef; the DefDef
      // body is checked via traverseChildren. Safe to ignore the leaf itself.
      case _: Closure => ()

      case _ => ()
    }

    traverseTerm(tree)
  }

  /**
   * Deep traversal that rejects any reference to a param symbol anywhere in the
   * tree. Used for nested lambda bodies and anonymous class members where no
   * use of the outer params is permitted at all.
   */
  private def checkNoCapture(using
    Quotes
  )(tree: quotes.reflect.Tree, paramInfo: Map[quotes.reflect.Symbol, (Int, String)]): Unit = {
    import quotes.reflect.*

    tree match {
      case ident @ Ident(_) if paramInfo.contains(ident.symbol) =>
        val (idx, name) = paramInfo(ident.symbol)
        report.errorAndAbort(
          s"Parameter $idx ('$name') cannot be captured in a nested lambda, def, or anonymous class. " +
            s"Scoped values may only be used as a method receiver (e.g., $name.method()).",
          ident.pos
        )

      case term: Term =>
        term match {
          case Apply(fn, args) =>
            checkNoCapture(fn, paramInfo)
            args.foreach(checkNoCapture(_, paramInfo))
          case NamedArg(_, value) =>
            checkNoCapture(value, paramInfo)
          case TypeApply(fn, _) =>
            checkNoCapture(fn, paramInfo)
          case Select(qualifier, _) =>
            checkNoCapture(qualifier, paramInfo)
          case Block(stmts, expr) =>
            stmts.foreach(checkNoCapture(_, paramInfo))
            checkNoCapture(expr, paramInfo)
          case If(cond, thenp, elsep) =>
            checkNoCapture(cond, paramInfo)
            checkNoCapture(thenp, paramInfo)
            checkNoCapture(elsep, paramInfo)
          case Match(scrutinee, cases) =>
            checkNoCapture(scrutinee, paramInfo)
            cases.foreach { case CaseDef(_, guard, rhs) =>
              guard.foreach(checkNoCapture(_, paramInfo))
              checkNoCapture(rhs, paramInfo)
            }
          case While(cond, body) =>
            checkNoCapture(cond, paramInfo)
            checkNoCapture(body, paramInfo)
          case Try(body, cases, finalizer) =>
            checkNoCapture(body, paramInfo)
            cases.foreach { case CaseDef(_, guard, rhs) =>
              guard.foreach(checkNoCapture(_, paramInfo))
              checkNoCapture(rhs, paramInfo)
            }
            finalizer.foreach(checkNoCapture(_, paramInfo))
          case Return(expr, _) =>
            checkNoCapture(expr, paramInfo)
          case Typed(expr, _) =>
            checkNoCapture(expr, paramInfo)
          case Inlined(_, bindings, body) =>
            bindings.foreach(checkNoCapture(_, paramInfo))
            checkNoCapture(body, paramInfo)
          case Lambda(_, body) =>
            checkNoCapture(body, paramInfo)
          case Assign(lhs, rhs) =>
            checkNoCapture(lhs, paramInfo)
            checkNoCapture(rhs, paramInfo)
          case Repeated(elems, _) =>
            elems.foreach(checkNoCapture(_, paramInfo))
          case _: Literal | _: This | _: New | _: Closure => ()
          case _: Ident                                   => () // non-param idents fine
          case _                                          => ()
        }

      case vd: ValDef =>
        vd.rhs.foreach(checkNoCapture(_, paramInfo))
      case dd: DefDef =>
        dd.rhs.foreach(checkNoCapture(_, paramInfo))
      case CaseDef(_, guard, rhs) =>
        guard.foreach(checkNoCapture(_, paramInfo))
        checkNoCapture(rhs, paramInfo)
      case cd: ClassDef =>
        cd.body.foreach(checkNoCapture(_, paramInfo))
      case _ => ()
    }
  }
}
