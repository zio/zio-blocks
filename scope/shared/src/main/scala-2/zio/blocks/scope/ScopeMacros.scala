package zio.blocks.scope

import scala.reflect.macros.whitebox
import zio.blocks.scope.internal.{MacroCore => MC}

private[scope] object ScopeMacros {

  /**
   * Scala 2 macro implementation for Scope.leak.
   *
   * Emits a compiler warning via MacroCore.warnLeak and returns the unwrapped
   * value (using asInstanceOf which is sound since $[A] = A at runtime).
   */
  def leakImpl(c: whitebox.Context)(sa: c.Tree): c.Tree = {
    import c.universe._

    val self = c.prefix.tree

    // Extract source code for the warning message
    val sourceCode = sa match {
      case Ident(name)     => name.toString
      case Select(_, name) => name.toString
      case _               => show(sa)
    }

    // Extract scope name for the warning message
    val scopeName = self.tpe.widen.toString

    MC.warnLeak(c)(sa.pos, sourceCode, scopeName)

    // Extract the underlying type A from $[A]
    val saType         = sa.tpe.widen
    val underlyingType = saType match {
      case TypeRef(_, sym, List(arg)) if sym.name.toString == "$" => arg
      case _                                                      => saType
    }

    q"$sa.asInstanceOf[$underlyingType]"
  }

  // ── N-ary $ macro implementations ─────────────────────────────────────────
  //
  // All use whitebox.Context — the declared return type Any is refined at
  // expansion time to B (when Unscoped[B]) or $[B] (otherwise).

  /** N=1: macro implementation for Scope.$[A,B](sa)(f). */
  def useImpl(c: whitebox.Context)(sa: c.Tree)(f: c.Tree): c.Tree =
    useNImpl(c)(List(sa), f)

  /** N=2: macro implementation for Scope.$[A1,A2,B](sa1,sa2)(f). */
  def use2Impl(c: whitebox.Context)(sa1: c.Tree, sa2: c.Tree)(f: c.Tree): c.Tree =
    useNImpl(c)(List(sa1, sa2), f)

  /** N=3: macro implementation for Scope.$[A1,A2,A3,B](sa1,sa2,sa3)(f). */
  def use3Impl(c: whitebox.Context)(sa1: c.Tree, sa2: c.Tree, sa3: c.Tree)(f: c.Tree): c.Tree =
    useNImpl(c)(List(sa1, sa2, sa3), f)

  /**
   * N=4: macro implementation for Scope.$[A1,A2,A3,A4,B](sa1,sa2,sa3,sa4)(f).
   */
  def use4Impl(c: whitebox.Context)(sa1: c.Tree, sa2: c.Tree, sa3: c.Tree, sa4: c.Tree)(
    f: c.Tree
  ): c.Tree =
    useNImpl(c)(List(sa1, sa2, sa3, sa4), f)

  /** N=5: macro implementation for Scope.$[A1,...,A5,B](sa1,...,sa5)(f). */
  def use5Impl(c: whitebox.Context)(
    sa1: c.Tree,
    sa2: c.Tree,
    sa3: c.Tree,
    sa4: c.Tree,
    sa5: c.Tree
  )(f: c.Tree): c.Tree =
    useNImpl(c)(List(sa1, sa2, sa3, sa4, sa5), f)

  // ── Shared implementation for all arities ─────────────────────────────────

  private def useNImpl(c: whitebox.Context)(sas: List[c.Tree], f: c.Tree): c.Tree = {
    import c.universe._

    val self = c.prefix.tree
    val n    = sas.length

    // Extract the underlying type from $[A] (or the type itself if not wrapped)
    def extractUnderlying(saTree: c.Tree): Type = {
      val saTyped = c.typecheck(saTree)
      saTyped.tpe.widen match {
        case TypeRef(_, sym, List(arg)) if sym.name.toString == "$" => arg
        case t                                                      => t
      }
    }

    val inputTypes: List[Type] = sas.map(extractUnderlying)

    // Build the expected FunctionN type for typechecking f
    val fnTypeConstructor = n match {
      case 1 => typeOf[Function1[_, _]].typeConstructor
      case 2 => typeOf[Function2[_, _, _]].typeConstructor
      case 3 => typeOf[Function3[_, _, _, _]].typeConstructor
      case 4 => typeOf[Function4[_, _, _, _, _]].typeConstructor
      case 5 => typeOf[Function5[_, _, _, _, _, _]].typeConstructor
      case _ => c.abort(c.enclosingPosition, s"$$ does not support arity $n (max 5)")
    }
    val expectedFnType =
      appliedType(fnTypeConstructor, inputTypes :+ WildcardType)
    val fTyped = c.typecheck(f, pt = expectedFnType)

    // Extract lambda params and body — handle all Scala 2 compiler wrapper forms
    val (paramSymbols, body) = fTyped match {
      case Function(params, body) =>
        (params.map(_.symbol), body)
      case Block(_, Function(params, body)) =>
        (params.map(_.symbol), body)
      case Typed(Function(params, body), _) =>
        (params.map(_.symbol), body)
      case _ =>
        c.abort(
          f.pos,
          s"$$ requires a lambda literal with $n parameter(s), e.g. $$(x)(a => a.method()). " +
            "Method references and variables are not supported."
        )
    }

    if (paramSymbols.length != n) {
      c.abort(
        f.pos,
        s"$$ expected a lambda with $n parameter(s) but found ${paramSymbols.length}."
      )
    }

    // Build param-info map: symbol → (1-based index, name)
    val paramInfos: Map[Symbol, (Int, String)] =
      paramSymbols.zipWithIndex.map { case (s, i) => s -> (i + 1, s.name.toString) }.toMap

    validateBody(c)(body, paramInfos)

    val returnType = body.tpe.widen

    // Check if B has an Unscoped instance — if so, auto-unwrap the result
    val unscopedTpe = appliedType(typeOf[Unscoped[_]].typeConstructor, List(returnType))
    val hasUnscoped = c.inferImplicitValue(unscopedTpe, silent = true) != EmptyTree

    // Build the cast arguments: each sa.asInstanceOf[Ai]
    val castArgs = sas.zip(inputTypes).map { case (sa, t) =>
      q"$sa.asInstanceOf[$t]"
    }

    val closedCheck = q"""
      if ($self.isClosed)
        throw new _root_.java.lang.IllegalStateException(
          _root_.zio.blocks.scope.internal.ErrorMessages
            .renderUseOnClosedScope($self.scopeDisplayName, color = false)
        )
    """

    if (hasUnscoped) {
      q"{ $closedCheck; $fTyped(..$castArgs) }"
    } else {
      q"{ $closedCheck; $fTyped(..$castArgs).asInstanceOf[$self.$$[$returnType]] }"
    }
  }

  // ── Lambda body validation ─────────────────────────────────────────────────

  /**
   * Validates that every reference to a param in `paramInfos` appears only in
   * method-receiver position (qualifier of a Select). All other uses are
   * compile-time errors.
   *
   * Note: Scala 2's catch-all `case tree => tree.children.foreach(check)`
   * safely traverses While, Try, Return, and other nodes that don't have
   * explicit cases.
   */
  private def validateBody(c: whitebox.Context)(
    tree: c.Tree,
    paramInfos: Map[c.Symbol, (Int, String)]
  ): Unit = {
    import c.universe._

    def isParamRef(t: Tree): Boolean = t match {
      case Ident(_) => paramInfos.contains(t.symbol)
      case _        => false
    }

    def paramErrMsg(sym: c.Symbol, detail: String): String = {
      val (idx, name) = paramInfos.getOrElse(sym, (0, sym.name.toString))
      s"Parameter $idx ('$name') $detail. " +
        s"Scoped values may only be used as a method receiver (e.g., $name.method())."
    }

    def isBoxingConversion(fn: Tree): Boolean = {
      val sym = fn.symbol
      if (sym == null || sym == NoSymbol) return false
      val name        = sym.name.toString
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

    def check(t: Tree): Unit = t match {

      // ── Allowed: param in receiver position of a Select ──────────────────
      case Select(qualifier, _) if isParamRef(qualifier) =>
      // valid — param used as receiver

      // ── Rejected: bare param reference ───────────────────────────────────
      case ident @ Ident(_) if paramInfos.contains(ident.symbol) =>
        c.abort(
          ident.pos,
          paramErrMsg(
            ident.symbol,
            "must only be used as a method receiver. It cannot be returned, stored, passed as an argument, or captured"
          )
        )

      // ── Function application ──────────────────────────────────────────────
      case Apply(fn, args) =>
        if (args.length == 1 && isParamRef(args.head) && isBoxingConversion(fn)) {
          // Compiler-inserted boxing — valid receiver usage
          ()
        } else {
          check(fn)
          args.foreach { arg =>
            if (isParamRef(arg)) {
              c.abort(
                arg.pos,
                paramErrMsg(arg.symbol, "cannot be passed as an argument to a function or method")
              )
            }
            check(arg)
          }
        }

      case TypeApply(fn, _) =>
        check(fn)

      // ── Select on non-param ───────────────────────────────────────────────
      case Select(qualifier, _) =>
        check(qualifier)

      // ── Block ─────────────────────────────────────────────────────────────
      case Block(stmts, expr) =>
        stmts.foreach(check)
        check(expr)

      // ── ValDef — param cannot be bound to a val ───────────────────────────
      case ValDef(_, _, _, rhs) =>
        if (isParamRef(rhs)) {
          c.abort(
            rhs.pos,
            paramErrMsg(rhs.symbol, "cannot be bound to a val or var")
          )
        }
        check(rhs)

      // ── Assignment — traverse lhs too (param may be lhs receiver) ─────────
      // e.g., d.mutableField = x — lhs is Select(d, field), allowed by the
      // Select-as-receiver rule above. Also reject param on rhs.
      case Assign(lhs, rhs) =>
        check(lhs)
        if (isParamRef(rhs)) {
          c.abort(
            rhs.pos,
            paramErrMsg(rhs.symbol, "cannot be assigned to a variable")
          )
        }
        check(rhs)

      // ── If expression ─────────────────────────────────────────────────────
      case If(cond, thenp, elsep) =>
        check(cond)
        check(thenp)
        check(elsep)

      // ── Match ─────────────────────────────────────────────────────────────
      case Match(scrutinee, cases) =>
        check(scrutinee)
        cases.foreach { case CaseDef(_, guard, body) =>
          if (guard != EmptyTree) check(guard)
          check(body)
        }

      // ── Nested lambda — param captured in closure ─────────────────────────
      case Function(_, body) =>
        checkNoCapture(c)(body, paramInfos)

      case Typed(expr, _) =>
        check(expr)

      case New(_)     => ()
      case _: Literal => ()
      case _: Ident   => () // non-param idents
      case _: This    => ()

      // Catch-all uses .children which safely traverses While, Try, Return, etc.
      case tree =>
        tree.children.foreach(check)
    }

    check(tree)
  }

  /**
   * Deep traversal rejecting any reference to a param symbol. Used for nested
   * lambda bodies where no use of outer params is permitted.
   *
   * Uses .children as catch-all so While/Try/Return/etc. are all covered.
   */
  private def checkNoCapture(c: whitebox.Context)(
    tree: c.Tree,
    paramInfos: Map[c.Symbol, (Int, String)]
  ): Unit = {
    import c.universe._

    tree match {
      case ident @ Ident(_) if paramInfos.contains(ident.symbol) =>
        val (idx, name) = paramInfos(ident.symbol)
        c.abort(
          ident.pos,
          s"Parameter $idx ('$name') cannot be captured in a nested lambda or closure."
        )
      case _ =>
        tree.children.foreach(checkNoCapture(c)(_, paramInfos))
    }
  }

  // ── Wire derivation macros (unchanged) ────────────────────────────────────

  private sealed trait WireKind
  private object WireKind {
    case object Shared extends WireKind
    case object Unique extends WireKind
  }

  def sharedImpl[T: c.WeakTypeTag](c: whitebox.Context): c.Expr[Wire.Shared[_, T]] =
    deriveWire[T](c)(WireKind.Shared).asInstanceOf[c.Expr[Wire.Shared[_, T]]]

  def uniqueImpl[T: c.WeakTypeTag](c: whitebox.Context): c.Expr[Wire.Unique[_, T]] =
    deriveWire[T](c)(WireKind.Unique).asInstanceOf[c.Expr[Wire.Unique[_, T]]]

  private def deriveWire[T: c.WeakTypeTag](c: whitebox.Context)(kind: WireKind): c.Expr[Wire[_, T]] = {
    import c.universe._

    val tpe = weakTypeOf[T]
    val sym = tpe.typeSymbol

    if (!sym.isClass || sym.asClass.isTrait || sym.asClass.isAbstract) {
      MC.abortNotAClass(c)(tpe.toString)
    }

    val ctor = tpe.decls.collectFirst {
      case m: MethodSymbol if m.isPrimaryConstructor => m
    }.getOrElse(MC.abortNoPrimaryCtor(c)(tpe.toString))

    val paramLists = ctor.paramLists

    val allDepTypes: List[Type] = paramLists.flatten.flatMap { param =>
      val paramType = param.typeSignature
      MC.classifyAndExtractDep(c)(paramType)
    }

    MC.checkSubtypeConflicts(c)(allDepTypes) match {
      case Some((subtype, supertype)) => MC.abortSubtypeConflict(c)(tpe.toString, subtype, supertype)
      case None                       =>
    }

    val isAutoCloseable = tpe <:< typeOf[AutoCloseable]
    val inType          =
      if (allDepTypes.isEmpty) typeOf[Any]
      else allDepTypes.reduceLeft((a, b) => c.universe.internal.refinedType(List(a, b), NoSymbol))

    def generateArgs(params: List[Symbol]): List[Tree] =
      params.map { param =>
        val paramType = param.typeSignature
        if (MC.isFinalizerType(c)(paramType)) {
          q"scope"
        } else {
          q"ctx.get[$paramType]"
        }
      }

    val argLists = paramLists.map(generateArgs)
    val ctorCall = if (argLists.isEmpty) {
      q"new $tpe()"
    } else {
      argLists.foldLeft[Tree](Select(New(TypeTree(tpe)), termNames.CONSTRUCTOR)) { (acc, args) =>
        Apply(acc, args)
      }
    }

    val wireFactory = kind match {
      case WireKind.Shared => q"_root_.zio.blocks.scope.Wire.Shared"
      case WireKind.Unique => q"_root_.zio.blocks.scope.Wire.Unique"
    }

    val wireBody = if (isAutoCloseable) {
      q"""
        val instance = $ctorCall
        scope.defer(instance.asInstanceOf[AutoCloseable].close())
        instance
      """
    } else {
      q"""
        $ctorCall
      """
    }

    val result = q"$wireFactory.apply[$inType, $tpe] { (scope: _root_.zio.blocks.scope.Scope, ctx) => $wireBody }"
    c.Expr[Wire[_, T]](result)
  }
}
