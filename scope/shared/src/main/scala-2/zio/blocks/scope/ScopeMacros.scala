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

  /**
   * Scala 2 macro implementation for Scope.use.
   *
   * Validates that the lambda parameter is only used in method-receiver
   * position, then expands to the unwrap-call-rewrap pattern.
   */
  def useImpl(c: whitebox.Context)(sa: c.Tree)(f: c.Tree): c.Tree = {
    import c.universe._

    val self = c.prefix.tree

    // Extract the underlying type A from $[A] by looking at sa's type
    val saTyped   = c.typecheck(sa)
    val saType    = saTyped.tpe.widen
    val inputType = saType match {
      case TypeRef(_, sym, List(arg)) if sym.name.toString == "$" => arg
      case _                                                      => saType
    }

    // Type-check f with the correct parameter type
    val expectedFnType = appliedType(typeOf[Function1[_, _]].typeConstructor, List(inputType, WildcardType))
    val fTyped         = c.typecheck(f, pt = expectedFnType)

    // Extract the lambda parameter symbol and body
    val (paramSymbol, body) = fTyped match {
      case Function(List(param), body) =>
        (param.symbol, body)
      case Block(_, Function(List(param), body)) =>
        (param.symbol, body)
      case _ =>
        c.abort(
          f.pos,
          "use requires a lambda literal: use(x)(a => a.method()). " +
            "Method references and variables are not supported."
        )
    }

    // Validate that the parameter is only used in receiver position
    validateBody(c)(body, paramSymbol)

    val returnType = body.tpe.widen

    // Expand to: if (isClosed) null.asInstanceOf[B].asInstanceOf[$[B]]
    //            else f(sa.asInstanceOf[A]).asInstanceOf[$[B]]
    // We use asInstanceOf because $[A] = A at runtime
    q"""
      if ($self.isClosed) null.asInstanceOf[$returnType].asInstanceOf[$self.$$[$returnType]]
      else $fTyped($sa.asInstanceOf[$inputType]).asInstanceOf[$self.$$[$returnType]]
    """
  }

  private def validateBody(c: whitebox.Context)(tree: c.Tree, paramSym: c.Symbol): Unit = {
    import c.universe._

    def isParamRef(t: Tree): Boolean = t match {
      case Ident(_) => t.symbol == paramSym
      case _        => false
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
      // param.method(args) or param.field — param is a receiver of Select
      case Select(qualifier, _) if isParamRef(qualifier) =>
        // This is fine — param is used as receiver
        ()

      // A bare reference to the param
      case ident @ Ident(_) if ident.symbol == paramSym =>
        c.abort(
          ident.pos,
          "Unsafe use of scoped value: the lambda parameter must only be used as a method receiver " +
            "(e.g., x.method()). It cannot be returned, stored, passed as an argument, or captured."
        )

      // Function application — check args for param usage
      case Apply(fn, args) =>
        if (args.length == 1 && isParamRef(args.head) && isBoxingConversion(fn)) {
          // Compiler-inserted boxing conversion — treat as valid receiver usage
          ()
        } else {
          check(fn)
          args.foreach { arg =>
            if (isParamRef(arg)) {
              c.abort(
                arg.pos,
                "Unsafe use of scoped value: the lambda parameter cannot be passed as an argument to a function or method."
              )
            }
            check(arg)
          }
        }

      case TypeApply(fn, _) =>
        check(fn)

      // Select on non-param — recurse into qualifier
      case Select(qualifier, _) =>
        check(qualifier)

      // Block
      case Block(stmts, expr) =>
        stmts.foreach(check)
        check(expr)

      // ValDef — check if param is assigned
      case ValDef(_, _, _, rhs) =>
        if (isParamRef(rhs)) {
          c.abort(
            rhs.pos,
            "Unsafe use of scoped value: the lambda parameter cannot be bound to a val or var."
          )
        }
        check(rhs)

      // Assignment
      case Assign(_, rhs) =>
        if (isParamRef(rhs)) {
          c.abort(
            rhs.pos,
            "Unsafe use of scoped value: the lambda parameter cannot be assigned to a variable."
          )
        }
        check(rhs)

      // If expression
      case If(cond, thenp, elsep) =>
        check(cond)
        check(thenp)
        check(elsep)

      // Match
      case Match(scrutinee, cases) =>
        check(scrutinee)
        cases.foreach { case CaseDef(_, guard, body) =>
          if (guard != EmptyTree) check(guard)
          check(body)
        }

      // Nested lambda — param captured in closure
      case Function(_, body) =>
        checkNoCapture(c)(body, paramSym)

      case Typed(expr, _) =>
        check(expr)

      // Tuple constructor or new — check args
      case New(_) => ()

      case _: Literal => ()
      case _: Ident   => () // non-param idents fine
      case _: This    => ()

      case tree =>
        tree.children.foreach(check)
    }

    check(tree)
  }

  private def checkNoCapture(c: whitebox.Context)(tree: c.Tree, paramSym: c.Symbol): Unit = {
    import c.universe._

    tree match {
      case ident @ Ident(_) if ident.symbol == paramSym =>
        c.abort(
          ident.pos,
          "Unsafe use of scoped value: the lambda parameter cannot be captured in a nested lambda or closure."
        )
      case _ =>
        tree.children.foreach(checkNoCapture(c)(_, paramSym))
    }
  }

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
