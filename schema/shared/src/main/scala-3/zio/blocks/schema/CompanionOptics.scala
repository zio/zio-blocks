package zio.blocks.schema

import scala.annotation.tailrec
import scala.compiletime.error
import zio.blocks.schema.binding._

transparent trait CompanionOptics[S] {
  extension [A](a: A) {
    inline def when[B <: A]: B = error("Can only be used inside `$(_)` and `optic(_)` macros")

    inline def wrapped[B]: B = error("Can only be used inside `$(_)` and `optic(_)` macros")
  }

  extension [C[_], A](c: C[A]) {
    inline def at(index: Int): A = error("Can only be used inside `$(_)` and `optic(_)` macros")

    inline def atIndices(indices: Int*): A = error("Can only be used inside `$(_)` and `optic(_)` macros")

    inline def each: A = error("Can only be used inside `$(_)` and `optic(_)` macros")
  }

  extension [M[_, _], K, V](m: M[K, V]) {
    inline def atKey(key: K): V = error("Can only be used inside `$(_)` and `optic(_)` macros")

    inline def atKeys(keys: K*): V = error("Can only be used inside `$(_)` and `optic(_)` macros")

    inline def eachKey: K = error("Can only be used inside `$(_)` and `optic(_)` macros")

    inline def eachValue: V = error("Can only be used inside `$(_)` and `optic(_)` macros")
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

    @tailrec
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
      case Apply(TypeApply(elementTerm, _), List(parent)) if hasName(elementTerm, "each") =>
        val parentTpe  = parent.tpe.dealias.widen
        val elementTpe = term.tpe.dealias.widen
        new Some(parentTpe.asType match {
          case '[p] =>
            elementTpe.asType match {
              case '[e] =>
                toOptic(parent).fold {
                  '{
                    $schema.reflect.asSequenceUnknown
                      .map(x => Traversal.seqValues(x.sequence))
                      .getOrElse(sys.error("Expected a sequence"))
                      .asInstanceOf[Traversal[p, e]]
                  }
                } { x =>
                  '{
                    val optic = ${ x.asExprOf[Optic[S, p]] }
                    optic.apply(
                      optic.focus.asSequenceUnknown
                        .map(x => Traversal.seqValues(x.sequence))
                        .getOrElse(sys.error("Expected a sequence"))
                        .asInstanceOf[Traversal[p, e]]
                    )
                  }
                }
            }
        })
      case Apply(TypeApply(keyTerm, _), List(parent)) if hasName(keyTerm, "eachKey") =>
        val parentTpe = parent.tpe.dealias.widen
        val keyTpe    = term.tpe.dealias.widen
        new Some(parentTpe.asType match {
          case '[p] =>
            keyTpe.asType match {
              case '[k] =>
                toOptic(parent).fold {
                  '{
                    $schema.reflect.asMapUnknown
                      .map(x => Traversal.mapKeys(x.map))
                      .getOrElse(sys.error("Expected a map"))
                      .asInstanceOf[Traversal[p, k]]
                  }
                } { x =>
                  '{
                    val optic = ${ x.asExprOf[Optic[S, p]] }
                    optic.apply(
                      optic.focus.asMapUnknown
                        .map(x => Traversal.mapKeys(x.map))
                        .getOrElse(sys.error("Expected a map"))
                        .asInstanceOf[Traversal[p, k]]
                    )
                  }
                }
            }
        })
      case Apply(TypeApply(valueTerm, _), List(parent)) if hasName(valueTerm, "eachValue") =>
        val parentTpe = parent.tpe.dealias.widen
        val valueTpe  = term.tpe.dealias.widen
        new Some(parentTpe.asType match {
          case '[p] =>
            valueTpe.asType match {
              case '[v] =>
                toOptic(parent).fold {
                  '{
                    $schema.reflect.asMapUnknown
                      .map(x => Traversal.mapValues(x.map))
                      .getOrElse(sys.error("Expected a map"))
                      .asInstanceOf[Traversal[p, v]]
                  }
                } { x =>
                  '{
                    val optic = ${ x.asExprOf[Optic[S, p]] }
                    optic.apply(
                      optic.focus.asMapUnknown
                        .map(x => Traversal.mapValues(x.map))
                        .getOrElse(sys.error("Expected a map"))
                        .asInstanceOf[Traversal[p, v]]
                    )
                  }
                }
            }
        })
      case TypeApply(Apply(TypeApply(caseTerm, _), List(parent)), List(typeTree)) if hasName(caseTerm, "when") =>
        val parentTpe = parent.tpe.dealias.widen
        val caseTpe   = typeTree.tpe.dealias
        var caseName  = caseTpe.typeSymbol.name
        if (caseTpe.termSymbol.flags.is(Flags.Enum)) caseName = caseTpe.termSymbol.name
        else if (caseTpe.typeSymbol.flags.is(Flags.Module)) caseName = caseName.substring(0, caseName.length - 1)
        new Some(parentTpe.asType match {
          case '[p] =>
            caseTpe.asType match {
              case '[c] =>
                toOptic(parent).fold {
                  val reflect = '{ $schema.reflect }.asExprOf[Reflect.Bound[p]]
                  '{
                    $reflect.asVariant
                      .flatMap(_.prismByName[c & p](${ Expr(caseName) }))
                      .getOrElse(sys.error("Expected a variant"))
                  }
                } { x =>
                  if (x.isExprOf[Lens[S, p]]) {
                    '{
                      val optic = ${ x.asExprOf[Lens[S, p]] }
                      optic.apply(
                        optic.focus.asVariant
                          .flatMap(_.prismByName[c & p](${ Expr(caseName) }))
                          .getOrElse(sys.error("Expected a variant"))
                      )
                    }
                  } else if (x.isExprOf[Prism[S, p & S]]) {
                    '{
                      val optic = ${ x.asExprOf[Prism[S, p & S]] }
                      optic.apply(
                        optic.focus.asVariant
                          .flatMap(_.prismByName[c & p & S](${ Expr(caseName) }))
                          .getOrElse(sys.error("Expected a variant"))
                      )
                    }
                  } else if (x.isExprOf[Optional[S, p]]) {
                    '{
                      val optic = ${ x.asExprOf[Optional[S, p]] }
                      optic.apply(
                        optic.focus.asVariant
                          .flatMap(_.prismByName[c & p](${ Expr(caseName) }))
                          .getOrElse(sys.error("Expected a variant"))
                      )
                    }
                  } else {
                    '{
                      val optic = ${ x.asExprOf[Traversal[S, p]] }
                      optic.apply(
                        optic.focus.asVariant
                          .flatMap(_.prismByName[c & p](${ Expr(caseName) }))
                          .getOrElse(sys.error("Expected a variant"))
                      )
                    }
                  }
                }
            }
        })
      case TypeApply(Apply(TypeApply(wrapperTerm, _), List(parent)), List(typeTree))
          if hasName(wrapperTerm, "wrapped") =>
        val parentTpe  = parent.tpe.dealias.widen
        val wrapperTpe = typeTree.tpe.dealias
        new Some(parentTpe.asType match {
          case '[p] =>
            wrapperTpe.asType match {
              case '[w] =>
                toOptic(parent).fold {
                  '{
                    $schema.reflect.asWrapperUnknown
                      .map(x => Optional.wrapped(x.wrapper.asInstanceOf[Reflect.Wrapper[Binding, p, w]]))
                      .getOrElse(sys.error("Expected a wrapper"))
                      .asInstanceOf[Optional[p, w]]
                  }
                } { x =>
                  if (x.isExprOf[Lens[S, p]]) {
                    '{
                      val optic = ${ x.asExprOf[Lens[S, p]] }
                      optic.apply(
                        optic.focus.asWrapperUnknown
                          .map(x => Optional.wrapped(x.wrapper.asInstanceOf[Reflect.Wrapper[Binding, p, w]]))
                          .getOrElse(sys.error("Expected a wrapper"))
                          .asInstanceOf[Optional[p, w]]
                      )
                    }
                  } else if (x.isExprOf[Prism[S, p & S]]) {
                    '{
                      val optic = ${ x.asExprOf[Prism[S, p & S]] }
                      optic.apply(
                        optic.focus.asWrapperUnknown
                          .map(x => Optional.wrapped(x.wrapper.asInstanceOf[Reflect.Wrapper[Binding, p, w]]))
                          .getOrElse(sys.error("Expected a wrapper"))
                          .asInstanceOf[Optional[p & S, w]]
                      )
                    }
                  } else if (x.isExprOf[Optional[S, p]]) {
                    '{
                      val optic = ${ x.asExprOf[Optional[S, p]] }
                      optic.apply(
                        optic.focus.asWrapperUnknown
                          .map(x => Optional.wrapped(x.wrapper.asInstanceOf[Reflect.Wrapper[Binding, p, w]]))
                          .getOrElse(sys.error("Expected a wrapper"))
                          .asInstanceOf[Optional[p, w]]
                      )
                    }
                  } else {
                    '{
                      val optic = ${ x.asExprOf[Traversal[S, p]] }
                      optic.apply(
                        optic.focus.asWrapperUnknown
                          .map(x => Optional.wrapped(x.wrapper.asInstanceOf[Reflect.Wrapper[Binding, p, w]]))
                          .getOrElse(sys.error("Expected a wrapper"))
                          .asInstanceOf[Optional[p, w]]
                      )
                    }
                  }
                }
            }
        })
      case Apply(Apply(TypeApply(elementTerm, _), List(parent)), List(index))
          if hasName(elementTerm, "at") && index.tpe.dealias.widen =:= TypeRepr.of[Int] =>
        val parentTpe  = parent.tpe.dealias.widen
        val elementTpe = term.tpe.dealias.widen
        new Some(parentTpe.asType match {
          case '[p] =>
            elementTpe.asType match {
              case '[e] =>
                toOptic(parent).fold {
                  '{
                    $schema.reflect.asSequenceUnknown
                      .map(x => Optional.at(x.sequence, ${ index.asExprOf[Int] }))
                      .getOrElse(sys.error("Expected a sequence"))
                      .asInstanceOf[Optional[p, e]]
                  }
                } { x =>
                  if (x.isExprOf[Lens[S, p]]) {
                    '{
                      val optic = ${ x.asExprOf[Lens[S, p]] }
                      optic.apply(
                        optic.focus.asSequenceUnknown
                          .map(x => Optional.at(x.sequence, ${ index.asExprOf[Int] }))
                          .getOrElse(sys.error("Expected a sequence"))
                          .asInstanceOf[Optional[p, e]]
                      )
                    }
                  } else if (x.isExprOf[Prism[S, p & S]]) {
                    '{
                      val optic = ${ x.asExprOf[Prism[S, p & S]] }
                      optic.apply(
                        optic.focus.asSequenceUnknown
                          .map(x => Optional.at(x.sequence, ${ index.asExprOf[Int] }))
                          .getOrElse(sys.error("Expected a sequence"))
                          .asInstanceOf[Optional[p & S, e]]
                      )
                    }
                  } else if (x.isExprOf[Optional[S, p]]) {
                    '{
                      val optic = ${ x.asExprOf[Optional[S, p]] }
                      optic.apply(
                        optic.focus.asSequenceUnknown
                          .map(x => Optional.at(x.sequence, ${ index.asExprOf[Int] }))
                          .getOrElse(sys.error("Expected a sequence"))
                          .asInstanceOf[Optional[p, e]]
                      )
                    }
                  } else {
                    '{
                      val optic = ${ x.asExprOf[Traversal[S, p]] }
                      optic.apply(
                        optic.focus.asSequenceUnknown
                          .map(x => Optional.at(x.sequence, ${ index.asExprOf[Int] }))
                          .getOrElse(sys.error("Expected a sequence"))
                          .asInstanceOf[Optional[p, e]]
                      )
                    }
                  }
                }
            }
        })
      case Apply(Apply(TypeApply(valueTerm, _), List(parent)), List(key)) if hasName(valueTerm, "atKey") =>
        val parentTpe = parent.tpe.dealias.widen
        val valueTpe  = term.tpe.dealias.widen
        new Some(parentTpe.asType match {
          case '[p] =>
            valueTpe.asType match {
              case '[v] =>
                toOptic(parent).fold {
                  '{
                    $schema.reflect.asMapUnknown
                      .map(x => Optional.atKey(x.map, ${ key.asExprOf[Any] }.asInstanceOf[x.KeyType]))
                      .getOrElse(sys.error("Expected a map"))
                      .asInstanceOf[Optional[p, v]]
                  }
                } { x =>
                  if (x.isExprOf[Lens[S, p]]) {
                    '{
                      val optic = ${ x.asExprOf[Lens[S, p]] }
                      optic.apply(
                        optic.focus.asMapUnknown
                          .map(x => Optional.atKey(x.map, ${ key.asExprOf[Any] }.asInstanceOf[x.KeyType]))
                          .getOrElse(sys.error("Expected a map"))
                          .asInstanceOf[Optional[p, v]]
                      )
                    }
                  } else if (x.isExprOf[Prism[S, p & S]]) {
                    '{
                      val optic = ${ x.asExprOf[Prism[S, p & S]] }
                      optic.apply(
                        optic.focus.asMapUnknown
                          .map(x => Optional.atKey(x.map, ${ key.asExprOf[Any] }.asInstanceOf[x.KeyType]))
                          .getOrElse(sys.error("Expected a map"))
                          .asInstanceOf[Optional[p & S, v]]
                      )
                    }
                  } else if (x.isExprOf[Optional[S, p]]) {
                    '{
                      val optic = ${ x.asExprOf[Optional[S, p]] }
                      optic.apply(
                        optic.focus.asMapUnknown
                          .map(x => Optional.atKey(x.map, ${ key.asExprOf[Any] }.asInstanceOf[x.KeyType]))
                          .getOrElse(sys.error("Expected a map"))
                          .asInstanceOf[Optional[p, v]]
                      )
                    }
                  } else {
                    '{
                      val optic = ${ x.asExprOf[Traversal[S, p]] }
                      optic.apply(
                        optic.focus.asMapUnknown
                          .map(x => Optional.atKey(x.map, ${ key.asExprOf[Any] }.asInstanceOf[x.KeyType]))
                          .getOrElse(sys.error("Expected a map"))
                          .asInstanceOf[Optional[p, v]]
                      )
                    }
                  }
                }
            }
        })
      case Apply(Apply(TypeApply(elementTerm, _), List(parent)), List(Typed(Repeated(indices, _), _)))
          if hasName(elementTerm, "atIndices") && indices.forall(_.tpe.dealias.widen =:= TypeRepr.of[Int]) =>
        val parentTpe  = parent.tpe.dealias.widen
        val elementTpe = term.tpe.dealias.widen
        new Some(parentTpe.asType match {
          case '[p] =>
            elementTpe.asType match {
              case '[e] =>
                toOptic(parent).fold {
                  '{
                    $schema.reflect.asSequenceUnknown
                      .map(x => Traversal.atIndices(x.sequence, ${ Expr.ofSeq(indices.map(_.asExprOf[Int])) }))
                      .getOrElse(sys.error("Expected a sequence"))
                      .asInstanceOf[Traversal[p, e]]
                  }
                } { x =>
                  '{
                    val optic = ${ x.asExprOf[Optic[S, p]] }
                    optic.apply(
                      optic.focus.asSequenceUnknown
                        .map(x => Traversal.atIndices(x.sequence, ${ Expr.ofSeq(indices.map(_.asExprOf[Int])) }))
                        .getOrElse(sys.error("Expected a sequence"))
                        .asInstanceOf[Traversal[p, e]]
                    )
                  }
                }
            }
        })
      case Apply(Apply(TypeApply(valueTerm, _), List(parent)), List(keys)) if hasName(valueTerm, "atKeys") =>
        val parentTpe = parent.tpe.dealias.widen
        val valueTpe  = term.tpe.dealias.widen
        new Some(parentTpe.asType match {
          case '[p] =>
            valueTpe.asType match {
              case '[v] =>
                toOptic(parent).fold {
                  '{
                    $schema.reflect.asMapUnknown
                      .map(x => Traversal.atKeys(x.map, ${ keys.asExprOf[Seq[Any]] }.asInstanceOf[Seq[x.KeyType]]))
                      .getOrElse(sys.error("Expected a map"))
                      .asInstanceOf[Traversal[p, v]]
                  }
                } { x =>
                  '{
                    val optic = ${ x.asExprOf[Optic[S, p]] }
                    optic.apply(
                      optic.focus.asMapUnknown
                        .map(x => Traversal.atKeys(x.map, ${ keys.asExprOf[Seq[Any]] }.asInstanceOf[Seq[x.KeyType]]))
                        .getOrElse(sys.error("Expected a map"))
                        .asInstanceOf[Traversal[p, v]]
                    )
                  }
                }
            }
        })
      case Select(parent, fieldName) =>
        val parentTpe = parent.tpe.dealias.widen
        val childTpe  = term.tpe.dealias.widen
        new Some(parentTpe.asType match {
          case '[p] =>
            childTpe.asType match {
              case '[c] =>
                toOptic(parent).fold {
                  '{
                    $schema.reflect.asRecord
                      .flatMap(_.lensByName[c](${ Expr(fieldName) }))
                      .getOrElse(sys.error("Expected a record"))
                  }
                } { x =>
                  if (x.isExprOf[Lens[S, p]]) {
                    '{
                      val optic = ${ x.asExprOf[Lens[S, p]] }
                      optic.apply(
                        optic.focus.asRecord
                          .flatMap(_.lensByName[c](${ Expr(fieldName) }))
                          .getOrElse(sys.error("Expected a record"))
                      )
                    }
                  } else if (x.isExprOf[Prism[S, p & S]]) {
                    '{
                      val optic = ${ x.asExprOf[Prism[S, p & S]] }
                      optic.apply(
                        optic.focus.asRecord
                          .flatMap(_.lensByName[c](${ Expr(fieldName) }))
                          .getOrElse(sys.error("Expected a record"))
                      )
                    }
                  } else if (x.isExprOf[Optional[S, p]]) {
                    '{
                      val optic = ${ x.asExprOf[Optional[S, p]] }
                      optic.apply(
                        optic.focus.asRecord
                          .flatMap(_.lensByName[c](${ Expr(fieldName) }))
                          .getOrElse(sys.error("Expected a record"))
                      )
                    }
                  } else {
                    '{
                      val optic = ${ x.asExprOf[Traversal[S, p]] }
                      optic.apply(
                        optic.focus.asRecord
                          .flatMap(_.lensByName[c](${ Expr(fieldName) }))
                          .getOrElse(sys.error("Expected a record"))
                      )
                    }
                  }
                }
            }
        })
      case _: Ident =>
        None
      case _ =>
        val (parent, idx) = term match {
          case Apply(Apply(_, List(p)), List(Literal(IntConstant(i))))                => (p, i)
          case Apply(TypeApply(Select(p, "apply"), _), List(Literal(IntConstant(i)))) => (p, i)
          case _                                                                      =>
            fail(
              s"Expected path elements: .<field>, .when[<T>], .at(<index>), .atIndices(<indices>), .atKey(<key>), .atKeys(<keys>), .each, .eachKey, .eachValue, or .wrapped[<T>], got: '${term.show}'"
            )
        }
        val parentTpe = parent.tpe.dealias.widen
        val childTpe  = term.tpe.dealias.widen
        new Some(parentTpe.asType match {
          case '[p] =>
            childTpe.asType match {
              case '[c] =>
                toOptic(parent).fold {
                  '{
                    $schema.reflect.asRecord
                      .flatMap(_.lensByIndex[c](${ Expr(idx) }))
                      .getOrElse(sys.error("Expected a record"))
                  }
                } { x =>
                  if (x.isExprOf[Lens[S, p]]) {
                    '{
                      val optic = ${ x.asExprOf[Lens[S, p]] }
                      optic.apply(
                        optic.focus.asRecord
                          .flatMap(_.lensByIndex[c](${ Expr(idx) }))
                          .getOrElse(sys.error("Expected a record"))
                      )
                    }
                  } else if (x.isExprOf[Prism[S, p & S]]) {
                    '{
                      val optic = ${ x.asExprOf[Prism[S, p & S]] }
                      optic.apply(
                        optic.focus.asRecord
                          .flatMap(_.lensByIndex[c](${ Expr(idx) }))
                          .getOrElse(sys.error("Expected a record"))
                      )
                    }
                  } else if (x.isExprOf[Optional[S, p]]) {
                    '{
                      val optic = ${ x.asExprOf[Optional[S, p]] }
                      optic.apply(
                        optic.focus.asRecord
                          .flatMap(_.lensByIndex[c](${ Expr(idx) }))
                          .getOrElse(sys.error("Expected a record"))
                      )
                    }
                  } else {
                    '{
                      val optic = ${ x.asExprOf[Traversal[S, p]] }
                      optic.apply(
                        optic.focus.asRecord
                          .flatMap(_.lensByIndex[c](${ Expr(idx) }))
                          .getOrElse(sys.error("Expected a record"))
                      )
                    }
                  }
                }
            }
        })
    }

    val optic = toOptic(toPathBody(path.asTerm)).get
    // report.info(s"Generated optic:\n${optic.show}", Position.ofMacroExpansion)
    optic
  }
}
