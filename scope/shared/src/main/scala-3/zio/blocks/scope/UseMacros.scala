package zio.blocks.scope

import scala.quoted.*

private[scope] object UseMacros {

  inline def check[A, B](inline f: A => B): Unit =
    ${ validateUse[A, B]('f) }

  def validateUse[A: Type, B: Type](
    f: Expr[A => B]
  )(using Quotes): Expr[Unit] = {
    import quotes.reflect.*

    // Extract the lambda from the expression - handle various tree shapes
    val fTerm = f.asTerm

    val (paramSymbol, body) = extractLambda(fTerm) match {
      case Some(result) => result
      case None         =>
        report.errorAndAbort(
          "$ requires a lambda literal: (scope $ x)(a => a.method()). " +
            "Method references and variables are not supported.",
          f.asTerm.pos
        )
    }

    // Validate all references to the parameter
    validateBody(body, paramSymbol)

    '{ () }
  }

  private def extractLambda(using
    Quotes
  )(tree: quotes.reflect.Term): Option[(quotes.reflect.Symbol, quotes.reflect.Term)] = {
    import quotes.reflect.*

    tree match {
      case Inlined(_, _, inner) =>
        extractLambda(inner)
      case Block(List(DefDef(_, List(TermParamClause(List(param))), _, Some(body))), _) =>
        Some((param.symbol, body))
      case Block(Nil, inner) =>
        extractLambda(inner)
      case Lambda(List(param), body) =>
        Some((param.symbol, body.asInstanceOf[Term]))
      case _ =>
        None
    }
  }

  private def validateBody(using Quotes)(tree: quotes.reflect.Tree, paramSym: quotes.reflect.Symbol): Unit = {
    import quotes.reflect.*

    def isParamRef(t: Tree): Boolean = t match {
      case Ident(_) => t.symbol == paramSym
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
        dd.rhs.foreach(rhs => checkNoCapture(rhs, paramSym))
      case CaseDef(_, guard, rhs) =>
        guard.foreach(traverseTerm)
        traverseTerm(rhs)
      case _ => ()
    }

    def traverseTerm(t: Term): Unit = t match {
      // param.method(args) or param.field — the param is in receiver position of a Select
      case Select(qualifier, _) if isParamRef(qualifier) =>
        // This is fine — param is a receiver
        ()

      // A bare reference to the param (not in Select receiver position)
      case ident @ Ident(_) if ident.symbol == paramSym =>
        report.errorAndAbort(
          "Unsafe use of scoped value: the lambda parameter must only be used as a method receiver " +
            "(e.g., x.method()). It cannot be returned, stored, passed as an argument, or captured.",
          ident.pos
        )

      // For Apply nodes, check the function part and arguments separately
      case Apply(fn, args) =>
        // Check if fn is a compiler-inserted boxing/unboxing conversion
        // (e.g., Predef.boolean2Boolean(param).booleanValue() desugars so
        // the param appears as an arg to the conversion function)
        if (args.length == 1 && isParamRef(args.head) && isBoxingConversion(fn)) {
          // Treat the entire Apply as a valid receiver-position usage —
          // the param is just being boxed by the compiler
          ()
        } else {
          traverseTerm(fn)
          args.foreach { arg =>
            if (isParamRef(arg)) {
              report.errorAndAbort(
                "Unsafe use of scoped value: the lambda parameter cannot be passed as an argument to a function or method.",
                arg.pos
              )
            }
            traverseTerm(arg)
          }
        }

      case TypeApply(fn, _) =>
        traverseTerm(fn)

      // Select on something that is NOT the param — recurse into the qualifier
      case Select(qualifier, _) =>
        traverseTerm(qualifier)

      // Block: check all statements and the final expression
      case Block(stmts, expr) =>
        stmts.foreach(traverseChildren)
        traverseTerm(expr)

      // Lambda body — param captured in nested lambda
      case Lambda(_, body) =>
        checkNoCapture(body, paramSym)

      // ValDef: check if param is assigned
      case Assign(_, rhs) =>
        if (isParamRef(rhs)) {
          report.errorAndAbort(
            "Unsafe use of scoped value: the lambda parameter cannot be assigned to a variable.",
            rhs.pos
          )
        }
        traverseTerm(rhs)

      // If expression
      case If(cond, thenp, elsep) =>
        traverseTerm(cond)
        traverseTerm(thenp)
        traverseTerm(elsep)

      // Match expression
      case Match(scrutinee, cases) =>
        traverseTerm(scrutinee)
        cases.foreach { case CaseDef(_, guard, rhs) =>
          guard.foreach(traverseTerm)
          traverseTerm(rhs)
        }

      case Typed(expr, _) =>
        traverseTerm(expr)

      case Inlined(_, bindings, body) =>
        bindings.foreach(traverseChildren)
        traverseTerm(body)

      case Repeated(elems, _) =>
        elems.foreach(traverseTerm)

      // Leaf nodes — no param references possible
      case _: Literal => ()
      case _: Ident   => () // non-param idents are fine
      case _: This    => ()
      case _: New     => ()
      case _: Closure => ()

      case _ => ()
    }

    traverseTerm(tree.asInstanceOf[Term])
  }

  private def checkNoCapture(using Quotes)(tree: quotes.reflect.Tree, paramSym: quotes.reflect.Symbol): Unit = {
    import quotes.reflect.*

    tree match {
      case ident @ Ident(_) if ident.symbol == paramSym =>
        report.errorAndAbort(
          "Unsafe use of scoped value: the lambda parameter cannot be captured in a nested lambda or closure.",
          ident.pos
        )
      case term: Term =>
        term match {
          case Apply(fn, args) =>
            checkNoCapture(fn, paramSym)
            args.foreach(checkNoCapture(_, paramSym))
          case TypeApply(fn, _) =>
            checkNoCapture(fn, paramSym)
          case Select(qualifier, _) =>
            checkNoCapture(qualifier, paramSym)
          case Block(stmts, expr) =>
            stmts.foreach(checkNoCapture(_, paramSym))
            checkNoCapture(expr, paramSym)
          case If(cond, thenp, elsep) =>
            checkNoCapture(cond, paramSym)
            checkNoCapture(thenp, paramSym)
            checkNoCapture(elsep, paramSym)
          case Match(scrutinee, cases) =>
            checkNoCapture(scrutinee, paramSym)
            cases.foreach { case CaseDef(_, guard, rhs) =>
              guard.foreach(checkNoCapture(_, paramSym))
              checkNoCapture(rhs, paramSym)
            }
          case Typed(expr, _) =>
            checkNoCapture(expr, paramSym)
          case Inlined(_, bindings, body) =>
            bindings.foreach(checkNoCapture(_, paramSym))
            checkNoCapture(body, paramSym)
          case Lambda(_, body) =>
            checkNoCapture(body, paramSym)
          case Assign(_, rhs) =>
            checkNoCapture(rhs, paramSym)
          case Repeated(elems, _) =>
            elems.foreach(checkNoCapture(_, paramSym))
          case _: Literal | _: This | _: New | _: Closure => ()
          case _: Ident                                   => () // non-param idents fine
          case _                                          => ()
        }
      case vd: ValDef =>
        vd.rhs.foreach(checkNoCapture(_, paramSym))
      case dd: DefDef =>
        dd.rhs.foreach(checkNoCapture(_, paramSym))
      case CaseDef(_, guard, rhs) =>
        guard.foreach(checkNoCapture(_, paramSym))
        checkNoCapture(rhs, paramSym)
      case _ => ()
    }
  }
}
