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

    def toOptic(term: Term)(using q: Quotes): Option[Expr[Any]] = term match {
      case Apply(TypeApply(elementTerm, _), idents) if hasName(elementTerm, "each") =>
        val parent     = idents.head
        val parentTpe  = parent.tpe.dealias.widen
        val elementTpe = term.tpe.dealias.widen
        Some((parentTpe.asType, elementTpe.asType) match {
          case ('[p], '[e]) =>
            toOptic(parent).fold {
              '{
                $schema.reflect.asSequenceUnknown
                  .map(x => Traversal.seqValues(x.sequence))
                  .getOrElse(sys.error("Expected a sequence"))
                  .asInstanceOf[Traversal[p, e]]
              }.asExprOf[Any]
            } { x =>
              val optic = x.asExprOf[Optic[S, p]]
              '{
                $optic.apply(
                  $optic.focus.asSequenceUnknown
                    .map(x => Traversal.seqValues(x.sequence))
                    .getOrElse(sys.error("Expected a sequence"))
                    .asInstanceOf[Traversal[p, e]]
                )
              }.asExprOf[Any]
            }
        })
      case Apply(TypeApply(keyTerm, _), idents) if hasName(keyTerm, "eachKey") =>
        val parent    = idents.head
        val parentTpe = parent.tpe.dealias.widen
        val keyTpe    = term.tpe.dealias.widen
        Some((parentTpe.asType, keyTpe.asType) match {
          case ('[p], '[k]) =>
            toOptic(parent).fold {
              '{
                $schema.reflect.asMapUnknown
                  .map(x => Traversal.mapKeys(x.map))
                  .getOrElse(sys.error("Expected a map"))
                  .asInstanceOf[Traversal[p, k]]
              }.asExprOf[Any]
            } { x =>
              val optic = x.asExprOf[Optic[S, p]]
              '{
                $optic.apply(
                  $optic.focus.asMapUnknown
                    .map(x => Traversal.mapKeys(x.map))
                    .getOrElse(sys.error("Expected a map"))
                    .asInstanceOf[Traversal[p, k]]
                )
              }.asExprOf[Any]
            }
        })
      case Apply(TypeApply(valueTerm, _), idents) if hasName(valueTerm, "eachValue") =>
        val parent    = idents.head
        val parentTpe = parent.tpe.dealias.widen
        val valueTpe  = term.tpe.dealias.widen
        Some((parentTpe.asType, valueTpe.asType) match {
          case ('[p], '[v]) =>
            toOptic(parent).fold {
              '{
                $schema.reflect.asMapUnknown
                  .map(x => Traversal.mapValues(x.map))
                  .getOrElse(sys.error("Expected a map"))
                  .asInstanceOf[Traversal[p, v]]
              }.asExprOf[Any]
            } { x =>
              val optic = x.asExprOf[Optic[S, p]]
              '{
                $optic.apply(
                  $optic.focus.asMapUnknown
                    .map(x => Traversal.mapValues(x.map))
                    .getOrElse(sys.error("Expected a map"))
                    .asInstanceOf[Traversal[p, v]]
                )
              }.asExprOf[Any]
            }
        })
      case TypeApply(Apply(TypeApply(caseTerm, _), idents), typeTrees) if hasName(caseTerm, "when") =>
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
              '{
                $reflect.asVariant
                  .flatMap(_.prismByName[c & p](${ Expr(caseName) }))
                  .getOrElse(sys.error("Expected a variant"))
              }.asExprOf[Any]
            } { x =>
              val optic = x.asExprOf[Optic[S, p]]
              '{
                $optic.apply(
                  $optic.focus.asVariant
                    .flatMap(_.prismByName[c & p](${ Expr(caseName) }))
                    .getOrElse(sys.error("Expected a variant"))
                )
              }.asExprOf[Any]
            }
        })
      case Select(parent, fieldName) =>
        val parentTpe = parent.tpe.dealias.widen
        val childTpe  = term.tpe.dealias.widen
        Some((parentTpe.asType, childTpe.asType) match {
          case ('[p], '[c]) =>
            toOptic(parent).fold {
              '{
                $schema.reflect.asRecord
                  .flatMap(_.lensByName[c](${ Expr(fieldName) }))
                  .getOrElse(sys.error("Expected a record"))
              }.asExprOf[Any]
            } { x =>
              val optic = x.asExprOf[Optic[S, p]]
              '{
                $optic.apply(
                  $optic.focus.asRecord
                    .flatMap(_.lensByName[c](${ Expr(fieldName) }))
                    .getOrElse(sys.error("Expected a record"))
                )
              }.asExprOf[Any]
            }
        })
      case _: Ident =>
        None
      case term =>
        fail(s"Expected path elements: .<field>, .when[T], .each, .eachKey, or .eachValue, got: '${term.show}'")
    }

    toOptic(toPathBody(path.asTerm)).get
  }
}
