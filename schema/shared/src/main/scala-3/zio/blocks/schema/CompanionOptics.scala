package zio.blocks.schema

trait CompanionOptics[S] {
  import scala.annotation.compileTimeOnly

  extension [A](a: A) {
    @compileTimeOnly("Can only be used inside `$(_)` and `optic(_)` macros")
    def when[B <: A]: B = ???
  }

  extension [C[_], A](a: C[A]) {
    @compileTimeOnly("Can only be used inside `$(_)` and `optic(_)` macros")
    def each: A = ???
  }

  extension [M[_, _], K, V](a: M[K, V]) {
    @compileTimeOnly("Can only be used inside `$(_)` and `optic(_)` macros")
    def eachKey: K = ???

    @compileTimeOnly("Can only be used inside `$(_)` and `optic(_)` macros")
    def eachValue: V = ???
  }

  transparent inline def $[A](inline path: S => A)(using schema: Schema[S]): Any =
    ${ CompanionOptics.optic('path, 'schema) }

  transparent inline def optic[A](inline path: S => A)(using schema: Schema[S]): Any =
    ${ CompanionOptics.optic('path, 'schema) }

  inline def field[A](inline path: S => A)(using schema: Schema[S]): Lens[S, A] =
    ${ CompanionOptics.field('path, 'schema) }

  inline def caseOf[A <: S](using schema: Schema[S]): Prism[S, A] =
    ${ CompanionOptics.caseOf('schema) }
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

    def toOptic(term: Term): Option[Expr[Any]] = term match {
      case Apply(TypeApply(extTerm, _), idents) if extTerm.toString.endsWith("each)") =>
        val parent     = idents.head
        val parentTpe  = parent.tpe.dealias.widen
        val elementTpe = term.tpe.dealias.widen
        Some(parentTpe.asType match {
          case '[p] =>
            elementTpe.asType match {
              case '[e] =>
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
            }
        })
      case Apply(TypeApply(extTerm, _), idents) if extTerm.toString.endsWith("eachKey)") =>
        val parent    = idents.head
        val parentTpe = parent.tpe.dealias.widen
        val keyTpe    = term.tpe.dealias.widen
        Some(parentTpe.asType match {
          case '[p] =>
            keyTpe.asType match {
              case '[k] =>
                toOptic(parent).map { x =>
                  val optic = x.asExprOf[Optic[S, p]]
                  '{
                    $optic.apply(
                      $optic.focus.asMapUnknown.map(x => Traversal.mapKeys(x.map)).get.asInstanceOf[Traversal[p, k]]
                    )
                  }.asExprOf[Any]
                }.getOrElse(fail("Expected a path element preceding `.eachKey`"))
            }
        })
      case Apply(TypeApply(extTerm, _), idents) if extTerm.toString.endsWith("eachValue)") =>
        val parent    = idents.head
        val parentTpe = parent.tpe.dealias.widen
        val valueTpe  = term.tpe.dealias.widen
        Some(parentTpe.asType match {
          case '[c] =>
            valueTpe.asType match {
              case '[v] =>
                toOptic(parent).map { x =>
                  val optic = x.asExprOf[Optic[S, c]]
                  '{
                    $optic.apply(
                      $optic.focus.asMapUnknown.map(x => Traversal.mapValues(x.map)).get.asInstanceOf[Traversal[c, v]]
                    )
                  }.asExprOf[Any]
                }.getOrElse(fail("Expected a path element preceding `.eachValue`"))
            }
        })
      case TypeApply(Apply(TypeApply(extTerm, _), idents), typeTrees) if extTerm.toString.endsWith("when)") =>
        val parent    = idents.head
        val parentTpe = parent.tpe.dealias.widen
        val caseTpe   = typeTrees.head.tpe.dealias
        var caseName  = caseTpe.typeSymbol.name
        if (caseTpe.termSymbol.flags.is(Flags.Enum)) caseName = caseTpe.termSymbol.name
        else if (caseTpe.typeSymbol.flags.is(Flags.Module)) caseName = caseName.substring(0, caseName.length - 1)
        Some(parentTpe.asType match {
          case '[p] =>
            caseTpe.asType match {
              case '[c] =>
                toOptic(parent).fold {
                  val reflect = '{ $schema.reflect }.asExprOf[Reflect.Bound[p]]
                  '{ $reflect.asVariant.flatMap(_.prismByName[c & p](${ Expr(caseName) })).get }.asExprOf[Any]
                } { x =>
                  val optic = x.asExprOf[Optic[S, p]]
                  '{
                    $optic.apply($optic.focus.asVariant.flatMap(_.prismByName[c & p](${ Expr(caseName) })).get)
                  }.asExprOf[Any]
                }
            }
        })
      case Select(parent, fieldName) =>
        val parentTpe = parent.tpe.dealias.widen
        val childTpe  = term.tpe.dealias.widen
        Some(parentTpe.asType match {
          case '[p] =>
            childTpe.asType match {
              case '[c] =>
                toOptic(parent).fold {
                  '{ $schema.reflect.asRecord.flatMap(_.lensByName[c](${ Expr(fieldName) })).get }.asExprOf[Any]
                } { x =>
                  val optic = x.asExprOf[Optic[S, p]]
                  '{
                    $optic.apply($optic.focus.asRecord.flatMap(_.lensByName[c](${ Expr(fieldName) })).get)
                  }.asExprOf[Any]
                }
            }
        })
      case _: Ident =>
        None
      case term =>
        fail(s"Expected a path element, got: ${term.show(using Printer.TreeStructure)}")
    }

    toOptic(toPathBody(path.asTerm)).get
  }

  def field[S: Type, A: Type](path: Expr[S => A], schema: Expr[Schema[S]])(using q: Quotes): Expr[Lens[S, A]] = {
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

    toPathBody(path.asTerm) match {
      case Select(Ident(_), fieldName) =>
        '{ $schema.reflect.asRecord.flatMap(_.lensByName[A](${ Expr(fieldName) })).get }.asExprOf[Lens[S, A]]
      case term =>
        fail(s"Expected a path element, got: ${term.show(using Printer.TreeStructure)}")
    }
  }

  def caseOf[S: Type, A <: S: Type](schema: Expr[Schema[S]])(using q: Quotes): Expr[Prism[S, A]] = {
    import q.reflect._

    val aTpe     = TypeRepr.of[A]
    var caseName = aTpe.typeSymbol.name
    if (aTpe.termSymbol.flags.is(Flags.Enum)) caseName = aTpe.termSymbol.name
    else if (aTpe.typeSymbol.flags.is(Flags.Module)) caseName = caseName.substring(0, caseName.length - 1)
    '{ $schema.reflect.asVariant.flatMap(_.prismByName[A](${ Expr(caseName) })).get }.asExprOf[Prism[S, A]]
  }
}
