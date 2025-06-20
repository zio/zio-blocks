package zio.blocks.schema

trait CompanionOptics[S] {
  import scala.annotation.compileTimeOnly

  extension [A](a: A) {
    @compileTimeOnly("Can only be used inside `$(_)` and `optic(_)` macros")
    def when[B <: A]: B = ???
  }

  extension [C[_], A](c: C[A]) {
    @compileTimeOnly("Can only be used inside `$(_)` and `optic(_)` macros")
    def each: A = ???
  }

  extension [M[_, _], K, V](m: M[K, V]) {
    @compileTimeOnly("Can only be used inside `$(_)` and `optic(_)` macros")
    def eachKey: K = ???

    @compileTimeOnly("Can only be used inside `$(_)` and `optic(_)` macros")
    def eachValue: V = ???
  }

  transparent inline def $[A](inline path: S => A)(using schema: Schema[S]): Any =
    ${ CompanionOptics.optic('path, 'schema) }

  transparent inline def optic[A](inline path: S => A)(using schema: Schema[S]): Any =
    ${ CompanionOptics.optic('path, 'schema) }
}

private object CompanionOptics {
  import scala.quoted._

  def optic[S: Type, A: Type](path: Expr[S => A], schema: Expr[Schema[S]])(using q: Quotes): Expr[Any] = {
    import q.reflect._

    def fail(msg: String): Nothing = report.errorAndAbort(msg, Position.ofMacroExpansion)

    def toPathBody(term: Term): Term = term match {
      case Inlined(_, _, inlinedBlock) =>
        toPathBody(inlinedBlock)
      case Block(List(DefDef(_, _, _, Some(pathBody))), _) =>
        pathBody
      case _ =>
        fail(s"Expected a lambda expression, got: ${term.show(using Printer.TreeStructure)}")
    }

    def hasName(term: Term, name: String): Boolean = term match {
      case Ident(s)     => name == s
      case Select(_, s) => name == s
      case _            => false
    }

    def toOptic(term: Term): Option[Expr[Any]] = term match {
      case Apply(TypeApply(subTerm, _), idents) if hasName(subTerm, "each") =>
        val parent     = idents.head
        val parentTpe  = parent.tpe.dealias.widen
        val elementTpe = term.tpe.dealias.widen
        Some((parentTpe.asType, elementTpe.asType) match {
          case ('[p], '[e]) =>
            toOptic(parent).map { x =>
              val optic = x.asExprOf[Optic[S, p]]
              '{
                $optic.apply(
                  $optic.focus.asSequenceUnknown
                    .map(x => Traversal.seqValues(x.sequence))
                    .get
                    .asInstanceOf[Traversal[p, e]]
                )
              }.asExprOf[Any]
            }.getOrElse(fail("Expected a path element preceding `.each`"))
        })
      case Apply(TypeApply(subTerm, _), idents) if hasName(subTerm, "eachKey") =>
        val parent    = idents.head
        val parentTpe = parent.tpe.dealias.widen
        val keyTpe    = term.tpe.dealias.widen
        Some((parentTpe.asType, keyTpe.asType) match {
          case ('[p], '[k]) =>
            toOptic(parent).map { x =>
              val optic = x.asExprOf[Optic[S, p]]
              '{
                $optic.apply(
                  $optic.focus.asMapUnknown.map(x => Traversal.mapKeys(x.map)).get.asInstanceOf[Traversal[p, k]]
                )
              }.asExprOf[Any]
            }.getOrElse(fail("Expected a path element preceding `.eachKey`"))
        })
      case Apply(TypeApply(subTerm, _), idents) if hasName(subTerm, "eachValue") =>
        val parent    = idents.head
        val parentTpe = parent.tpe.dealias.widen
        val valueTpe  = term.tpe.dealias.widen
        Some((parentTpe.asType, valueTpe.asType) match {
          case ('[p], '[v]) =>
            toOptic(parent).map { x =>
              val optic = x.asExprOf[Optic[S, p]]
              '{
                $optic.apply(
                  $optic.focus.asMapUnknown.map(x => Traversal.mapValues(x.map)).get.asInstanceOf[Traversal[p, v]]
                )
              }.asExprOf[Any]
            }.getOrElse(fail("Expected a path element preceding `.eachValue`"))
        })
      case TypeApply(Apply(TypeApply(subTerm, _), idents), typeTrees) if hasName(subTerm, "when") =>
        val parent    = idents.head
        val parentTpe = parent.tpe.dealias.widen
        val caseTpe   = typeTrees.head.tpe.dealias
        var caseName  = caseTpe.typeSymbol.name
        if (caseTpe.termSymbol.flags.is(Flags.Enum)) caseName = caseTpe.termSymbol.name
        else if (caseTpe.typeSymbol.flags.is(Flags.Module)) caseName = caseName.substring(0, caseName.length - 1)
        Some((parentTpe.asType, caseTpe.asType) match {
          case ('[p], '[c]) =>
            toOptic(parent).fold {
              val reflect = '{ $schema.reflect }.asExprOf[Reflect.Bound[p]]
              '{ $reflect.asVariant.flatMap(_.prismByName[c & p](${ Expr(caseName) })).get }.asExprOf[Any]
            } { x =>
              val optic = x.asExprOf[Optic[S, p]]
              '{
                $optic.apply($optic.focus.asVariant.flatMap(_.prismByName[c & p](${ Expr(caseName) })).get)
              }.asExprOf[Any]
            }
        })
      case Select(parent, fieldName) =>
        val parentTpe = parent.tpe.dealias.widen
        val childTpe  = term.tpe.dealias.widen
        Some((parentTpe.asType, childTpe.asType) match {
          case ('[p], '[c]) =>
            toOptic(parent).fold {
              '{ $schema.reflect.asRecord.flatMap(_.lensByName[c](${ Expr(fieldName) })).get }.asExprOf[Any]
            } { x =>
              val optic = x.asExprOf[Optic[S, p]]
              '{ $optic.apply($optic.focus.asRecord.flatMap(_.lensByName[c](${ Expr(fieldName) })).get) }.asExprOf[Any]
            }
        })
      case _: Ident =>
        None
      case term =>
        fail(s"Expected a path element, got: ${term.show(using Printer.TreeStructure)}")
    }

    toOptic(toPathBody(path.asTerm)).get
  }
}
