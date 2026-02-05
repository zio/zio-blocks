package zio.blocks.schema

import scala.annotation.tailrec
import scala.compiletime.{error, summonInline}

transparent trait CompanionOptics[S] {
  extension [A](a: A) {
    inline def when[B <: A]: B = error("Can only be used inside `$(_)` and `optic(_)` macros")

    inline def wrapped[B]: B = error("Can only be used inside `$(_)` and `optic(_)` macros")

    inline def searchFor[B]: B = error("Can only be used inside `$(_)` and `optic(_)` macros")
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
    import zio.blocks.schema.CommonMacroOps._

    @tailrec
    def toPathBody(term: Term): Term = term match {
      case Inlined(_, _, inlinedBlock)                     => toPathBody(inlinedBlock)
      case Block(List(DefDef(_, _, _, Some(pathBody))), _) => pathBody
      case _                                               => fail(s"Expected a lambda expression, got '${term.show}'")
    }

    def hasName(using q: Quotes)(term: q.reflect.Term, name: String): Boolean = {
      import q.reflect._

      term match {
        case Ident(s)     => name == s
        case Select(_, s) => name == s
        case _            => false
      }
    }

    def toOptic(using q: Quotes)(term: q.reflect.Term): Option[Expr[Any]] = {
      import q.reflect._

      term match {
        case Apply(TypeApply(elementTerm, _), List(parent)) if hasName(elementTerm, "each") =>
          val parentTpe  = parent.tpe.widen.dealias
          val elementTpe = term.tpe.widen.dealias
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
                    if (x.isExprOf[Optic[S, p]]) {
                      '{
                        val optic = ${ x.asInstanceOf[Expr[Optic[S, p]]] }
                        optic.apply(
                          optic.focus.asSequenceUnknown
                            .map(x => Traversal.seqValues(x.sequence))
                            .getOrElse(sys.error("Expected a sequence"))
                            .asInstanceOf[Traversal[p, e]]
                        )
                      }
                    } else unsupportedOpticType(x)
                  }
              }
          })
        case Apply(TypeApply(keyTerm, _), List(parent)) if hasName(keyTerm, "eachKey") =>
          val parentTpe = parent.tpe.widen.dealias
          val keyTpe    = term.tpe.widen.dealias
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
                    if (x.isExprOf[Optic[S, p]]) {
                      '{
                        val optic = ${ x.asInstanceOf[Expr[Optic[S, p]]] }
                        optic.apply(
                          optic.focus.asMapUnknown
                            .map(x => Traversal.mapKeys(x.map))
                            .getOrElse(sys.error("Expected a map"))
                            .asInstanceOf[Traversal[p, k]]
                        )
                      }
                    } else unsupportedOpticType(x)
                  }
              }
          })
        case Apply(TypeApply(valueTerm, _), List(parent)) if hasName(valueTerm, "eachValue") =>
          val parentTpe = parent.tpe.widen.dealias
          val valueTpe  = term.tpe.widen.dealias
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
                    if (x.isExprOf[Optic[S, p]]) {
                      '{
                        val optic = ${ x.asInstanceOf[Expr[Optic[S, p]]] }
                        optic.apply(
                          optic.focus.asMapUnknown
                            .map(x => Traversal.mapValues(x.map))
                            .getOrElse(sys.error("Expected a map"))
                            .asInstanceOf[Traversal[p, v]]
                        )
                      }
                    } else unsupportedOpticType(x)
                  }
              }
          })
        case TypeApply(Apply(TypeApply(caseTerm, _), List(parent)), List(typeTree)) if hasName(caseTerm, "when") =>
          val parentTpe = parent.tpe.widen.dealias
          val caseTpe   = typeTree.tpe.dealias
          val subTypes  =
            if (isUnion(parentTpe)) allUnionTypes(parentTpe)
            else directSubTypes(parentTpe)
          val caseIdx = subTypes.indexWhere(_ =:= caseTpe, 0)
          new Some(parentTpe.asType match {
            case '[p] =>
              caseTpe.asType match {
                case '[c] =>
                  toOptic(parent).fold {
                    '{
                      ${ schema.asInstanceOf[Expr[Schema[p]]] }.reflect.asVariant
                        .flatMap(_.prismByIndex[c & p](${ Expr(caseIdx) }))
                        .getOrElse(sys.error("Expected a variant"))
                    }
                  } { x =>
                    if (x.isExprOf[Lens[S, p]]) {
                      '{
                        val optic = ${ x.asInstanceOf[Expr[Lens[S, p]]] }
                        optic.apply(
                          optic.focus.asVariant
                            .flatMap(_.prismByIndex[c & p](${ Expr(caseIdx) }))
                            .getOrElse(sys.error("Expected a variant"))
                        )
                      }
                    } else if (x.isExprOf[Prism[S, p & S]]) {
                      '{
                        val optic = ${ x.asInstanceOf[Expr[Prism[S, p & S]]] }
                        optic.apply(
                          optic.focus.asVariant
                            .flatMap(_.prismByIndex[c & p & S](${ Expr(caseIdx) }))
                            .getOrElse(sys.error("Expected a variant"))
                        )
                      }
                    } else if (x.isExprOf[Optional[S, p]]) {
                      '{
                        val optic = ${ x.asInstanceOf[Expr[Optional[S, p]]] }
                        optic.apply(
                          optic.focus.asVariant
                            .flatMap(_.prismByIndex[c & p](${ Expr(caseIdx) }))
                            .getOrElse(sys.error("Expected a variant"))
                        )
                      }
                    } else if (x.isExprOf[Traversal[S, p]]) {
                      '{
                        val optic = ${ x.asInstanceOf[Expr[Traversal[S, p]]] }
                        optic.apply(
                          optic.focus.asVariant
                            .flatMap(_.prismByIndex[c & p](${ Expr(caseIdx) }))
                            .getOrElse(sys.error("Expected a variant"))
                        )
                      }
                    } else unsupportedOpticType(x)
                  }
              }
          })
        case TypeApply(Apply(TypeApply(wrapperTerm, _), List(parent)), List(typeTree))
            if hasName(wrapperTerm, "wrapped") =>
          val parentTpe  = parent.tpe.widen.dealias
          val wrapperTpe = typeTree.tpe.dealias
          new Some(parentTpe.asType match {
            case '[p] =>
              wrapperTpe.asType match {
                case '[w] =>
                  toOptic(parent).fold {
                    '{
                      $schema.reflect.asWrapperUnknown
                        .map(x => Optional.wrapped(x.wrapper))
                        .getOrElse(sys.error("Expected a wrapper"))
                        .asInstanceOf[Optional[p, w]]
                    }
                  } { x =>
                    if (x.isExprOf[Lens[S, p]]) {
                      '{
                        val optic = ${ x.asInstanceOf[Expr[Lens[S, p]]] }
                        optic.apply(
                          optic.focus.asWrapperUnknown
                            .map(x => Optional.wrapped(x.wrapper))
                            .getOrElse(sys.error("Expected a wrapper"))
                            .asInstanceOf[Optional[p, w]]
                        )
                      }
                    } else if (x.isExprOf[Prism[S, p & S]]) {
                      '{
                        val optic = ${ x.asInstanceOf[Expr[Prism[S, p & S]]] }
                        optic.apply(
                          optic.focus.asWrapperUnknown
                            .map(x => Optional.wrapped(x.wrapper))
                            .getOrElse(sys.error("Expected a wrapper"))
                            .asInstanceOf[Optional[p & S, w]]
                        )
                      }
                    } else if (x.isExprOf[Optional[S, p]]) {
                      '{
                        val optic = ${ x.asInstanceOf[Expr[Optional[S, p]]] }
                        optic.apply(
                          optic.focus.asWrapperUnknown
                            .map(x => Optional.wrapped(x.wrapper))
                            .getOrElse(sys.error("Expected a wrapper"))
                            .asInstanceOf[Optional[p, w]]
                        )
                      }
                    } else if (x.isExprOf[Traversal[S, p]]) {
                      '{
                        val optic = ${ x.asInstanceOf[Expr[Traversal[S, p]]] }
                        optic.apply(
                          optic.focus.asWrapperUnknown
                            .map(x => Optional.wrapped(x.wrapper))
                            .getOrElse(sys.error("Expected a wrapper"))
                            .asInstanceOf[Optional[p, w]]
                        )
                      }
                    } else unsupportedOpticType(x)
                  }
              }
          })
        case Apply(Apply(TypeApply(elementTerm, _), List(parent)), List(index))
            if hasName(elementTerm, "at") && index.tpe.widen.dealias <:< TypeRepr.of[Int] =>
          val parentTpe  = parent.tpe.widen.dealias
          val elementTpe = term.tpe.widen.dealias
          val indexExpr  = index.asExpr.asInstanceOf[Expr[Int]]
          new Some(parentTpe.asType match {
            case '[p] =>
              elementTpe.asType match {
                case '[e] =>
                  toOptic(parent).fold {
                    '{
                      $schema.reflect.asSequenceUnknown
                        .map(x => Optional.at(x.sequence, $indexExpr))
                        .getOrElse(sys.error("Expected a sequence"))
                        .asInstanceOf[Optional[p, e]]
                    }
                  } { x =>
                    if (x.isExprOf[Lens[S, p]]) {
                      '{
                        val optic = ${ x.asInstanceOf[Expr[Lens[S, p]]] }
                        optic.apply(
                          optic.focus.asSequenceUnknown
                            .map(x => Optional.at(x.sequence, $indexExpr))
                            .getOrElse(sys.error("Expected a sequence"))
                            .asInstanceOf[Optional[p, e]]
                        )
                      }
                    } else if (x.isExprOf[Prism[S, p & S]]) {
                      '{
                        val optic = ${ x.asInstanceOf[Expr[Prism[S, p & S]]] }
                        optic.apply(
                          optic.focus.asSequenceUnknown
                            .map(x => Optional.at(x.sequence, $indexExpr))
                            .getOrElse(sys.error("Expected a sequence"))
                            .asInstanceOf[Optional[p & S, e]]
                        )
                      }
                    } else if (x.isExprOf[Optional[S, p]]) {
                      '{
                        val optic = ${ x.asInstanceOf[Expr[Optional[S, p]]] }
                        optic.apply(
                          optic.focus.asSequenceUnknown
                            .map(x => Optional.at(x.sequence, $indexExpr))
                            .getOrElse(sys.error("Expected a sequence"))
                            .asInstanceOf[Optional[p, e]]
                        )
                      }
                    } else if (x.isExprOf[Traversal[S, p]]) {
                      '{
                        val optic = ${ x.asInstanceOf[Expr[Traversal[S, p]]] }
                        optic.apply(
                          optic.focus.asSequenceUnknown
                            .map(x => Optional.at(x.sequence, $indexExpr))
                            .getOrElse(sys.error("Expected a sequence"))
                            .asInstanceOf[Optional[p, e]]
                        )
                      }
                    } else unsupportedOpticType(x)
                  }
              }
          })
        case Apply(Apply(TypeApply(valueTerm, _), List(parent)), List(key)) if hasName(valueTerm, "atKey") =>
          val parentTpe = parent.tpe.widen.dealias
          val valueTpe  = term.tpe.widen.dealias
          new Some(parentTpe.asType match {
            case '[p] =>
              valueTpe.asType match {
                case '[v] =>
                  toOptic(parent).fold {
                    '{
                      $schema.reflect.asMapUnknown
                        .map(x => Optional.atKey(x.map, ${ key.asExpr }.asInstanceOf[x.KeyType]))
                        .getOrElse(sys.error("Expected a map"))
                        .asInstanceOf[Optional[p, v]]
                    }
                  } { x =>
                    if (x.isExprOf[Lens[S, p]]) {
                      '{
                        val optic = ${ x.asInstanceOf[Expr[Lens[S, p]]] }
                        optic.apply(
                          optic.focus.asMapUnknown
                            .map(x => Optional.atKey(x.map, ${ key.asExpr }.asInstanceOf[x.KeyType]))
                            .getOrElse(sys.error("Expected a map"))
                            .asInstanceOf[Optional[p, v]]
                        )
                      }
                    } else if (x.isExprOf[Prism[S, p & S]]) {
                      '{
                        val optic = ${ x.asInstanceOf[Expr[Prism[S, p & S]]] }
                        optic.apply(
                          optic.focus.asMapUnknown
                            .map(x => Optional.atKey(x.map, ${ key.asExpr }.asInstanceOf[x.KeyType]))
                            .getOrElse(sys.error("Expected a map"))
                            .asInstanceOf[Optional[p & S, v]]
                        )
                      }
                    } else if (x.isExprOf[Optional[S, p]]) {
                      '{
                        val optic = ${ x.asInstanceOf[Expr[Optional[S, p]]] }
                        optic.apply(
                          optic.focus.asMapUnknown
                            .map(x => Optional.atKey(x.map, ${ key.asExpr }.asInstanceOf[x.KeyType]))
                            .getOrElse(sys.error("Expected a map"))
                            .asInstanceOf[Optional[p, v]]
                        )
                      }
                    } else if (x.isExprOf[Traversal[S, p]]) {
                      '{
                        val optic = ${ x.asInstanceOf[Expr[Traversal[S, p]]] }
                        optic.apply(
                          optic.focus.asMapUnknown
                            .map(x => Optional.atKey(x.map, ${ key.asExpr }.asInstanceOf[x.KeyType]))
                            .getOrElse(sys.error("Expected a map"))
                            .asInstanceOf[Optional[p, v]]
                        )
                      }
                    } else unsupportedOpticType(x)
                  }
              }
          })
        case Apply(Apply(TypeApply(elementTerm, _), List(parent)), List(Typed(Repeated(indices, _), _)))
            if hasName(elementTerm, "atIndices") && indices.forall(_.tpe.widen.dealias <:< TypeRepr.of[Int]) =>
          val parentTpe   = parent.tpe.widen.dealias
          val elementTpe  = term.tpe.widen.dealias
          val indicesExpr = Expr.ofSeq(indices.map(_.asExpr.asInstanceOf[Expr[Int]]))
          new Some(parentTpe.asType match {
            case '[p] =>
              elementTpe.asType match {
                case '[e] =>
                  toOptic(parent).fold {
                    '{
                      $schema.reflect.asSequenceUnknown
                        .map(x => Traversal.atIndices(x.sequence, $indicesExpr))
                        .getOrElse(sys.error("Expected a sequence"))
                        .asInstanceOf[Traversal[p, e]]
                    }
                  } { x =>
                    if (x.isExprOf[Optic[S, p]]) {
                      '{
                        val optic = ${ x.asInstanceOf[Expr[Optic[S, p]]] }
                        optic.apply(
                          optic.focus.asSequenceUnknown
                            .map(x => Traversal.atIndices(x.sequence, $indicesExpr))
                            .getOrElse(sys.error("Expected a sequence"))
                            .asInstanceOf[Traversal[p, e]]
                        )
                      }
                    } else unsupportedOpticType(x)
                  }
              }
          })
        case Apply(Apply(TypeApply(valueTerm, _), List(parent)), List(keys)) if hasName(valueTerm, "atKeys") =>
          val parentTpe = parent.tpe.widen.dealias
          val valueTpe  = term.tpe.widen.dealias
          val keysExpr  = keys.asExpr.asInstanceOf[Expr[Seq[Any]]]
          new Some(parentTpe.asType match {
            case '[p] =>
              valueTpe.asType match {
                case '[v] =>
                  toOptic(parent).fold {
                    '{
                      $schema.reflect.asMapUnknown
                        .map(x => Traversal.atKeys(x.map, $keysExpr.asInstanceOf[Seq[x.KeyType]]))
                        .getOrElse(sys.error("Expected a map"))
                        .asInstanceOf[Traversal[p, v]]
                    }
                  } { x =>
                    if (x.isExprOf[Optic[S, p]]) {
                      '{
                        val optic = ${ x.asInstanceOf[Expr[Optic[S, p]]] }
                        optic.apply(
                          optic.focus.asMapUnknown
                            .map(x => Traversal.atKeys(x.map, $keysExpr.asInstanceOf[Seq[x.KeyType]]))
                            .getOrElse(sys.error("Expected a map"))
                            .asInstanceOf[Traversal[p, v]]
                        )
                      }
                    } else unsupportedOpticType(x)
                  }
              }
          })
        case TypeApply(Apply(TypeApply(searchTerm, _), List(parent)), List(typeTree))
            if hasName(searchTerm, "searchFor") =>
          val parentTpe = parent.tpe.widen.dealias
          val searchTpe = typeTree.tpe.dealias
          new Some(parentTpe.asType match {
            case '[p] =>
              searchTpe.asType match {
                case '[s] =>
                  toOptic(parent).fold {
                    '{
                      SearchTraversal(
                        $schema.reflect.asInstanceOf[Reflect.Bound[p]],
                        summonInline[Schema[s]].reflect
                      ).asInstanceOf[Traversal[p, s]]
                    }
                  } { x =>
                    if (x.isExprOf[Lens[S, p]]) {
                      '{
                        val optic = ${ x.asInstanceOf[Expr[Lens[S, p]]] }
                        optic.apply(
                          SearchTraversal(
                            optic.focus.asInstanceOf[Reflect.Bound[p]],
                            summonInline[Schema[s]].reflect
                          ).asInstanceOf[Traversal[p, s]]
                        )
                      }
                    } else if (x.isExprOf[Prism[S, p & S]]) {
                      '{
                        val optic = ${ x.asInstanceOf[Expr[Prism[S, p & S]]] }
                        optic.apply(
                          SearchTraversal(
                            optic.focus.asInstanceOf[Reflect.Bound[p & S]],
                            summonInline[Schema[s]].reflect
                          ).asInstanceOf[Traversal[p & S, s]]
                        )
                      }
                    } else if (x.isExprOf[Optional[S, p]]) {
                      '{
                        val optic = ${ x.asInstanceOf[Expr[Optional[S, p]]] }
                        optic.apply(
                          SearchTraversal(
                            optic.focus.asInstanceOf[Reflect.Bound[p]],
                            summonInline[Schema[s]].reflect
                          ).asInstanceOf[Traversal[p, s]]
                        )
                      }
                    } else if (x.isExprOf[Traversal[S, p]]) {
                      '{
                        val optic = ${ x.asInstanceOf[Expr[Traversal[S, p]]] }
                        optic.apply(
                          SearchTraversal(
                            optic.focus.asInstanceOf[Reflect.Bound[p]],
                            summonInline[Schema[s]].reflect
                          ).asInstanceOf[Traversal[p, s]]
                        )
                      }
                    } else unsupportedOpticType(x)
                  }
              }
          })
        case Select(parent, fieldName) =>
          val parentTpe = parent.tpe.widen.dealias
          val childTpe  = term.tpe.widen.dealias
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
                        val optic = ${ x.asInstanceOf[Expr[Lens[S, p]]] }
                        optic.apply(
                          optic.focus.asRecord
                            .flatMap(_.lensByName[c](${ Expr(fieldName) }))
                            .getOrElse(sys.error("Expected a record"))
                        )
                      }
                    } else if (x.isExprOf[Prism[S, p & S]]) {
                      '{
                        val optic = ${ x.asInstanceOf[Expr[Prism[S, p & S]]] }
                        optic.apply(
                          optic.focus.asRecord
                            .flatMap(_.lensByName[c](${ Expr(fieldName) }))
                            .getOrElse(sys.error("Expected a record"))
                        )
                      }
                    } else if (x.isExprOf[Optional[S, p]]) {
                      '{
                        val optic = ${ x.asInstanceOf[Expr[Optional[S, p]]] }
                        optic.apply(
                          optic.focus.asRecord
                            .flatMap(_.lensByName[c](${ Expr(fieldName) }))
                            .getOrElse(sys.error("Expected a record"))
                        )
                      }
                    } else if (x.isExprOf[Traversal[S, p]]) {
                      '{
                        val optic = ${ x.asInstanceOf[Expr[Traversal[S, p]]] }
                        optic.apply(
                          optic.focus.asRecord
                            .flatMap(_.lensByName[c](${ Expr(fieldName) }))
                            .getOrElse(sys.error("Expected a record"))
                        )
                      }
                    } else unsupportedOpticType(x)
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
                s"Expected path elements: .<field>, .when[<T>], .at(<index>), .atIndices(<indices>), .atKey(<key>), .atKeys(<keys>), .each, .eachKey, .eachValue, .wrapped[<T>], or .searchFor[<T>], got '${term.show}'."
              )
          }
          var parentTpe = parent.tpe.widen.dealias
          var childTpe  = term.tpe.widen.dealias
          if (isGenericTuple(parentTpe)) {
            val typeArgs = genericTupleTypeArgs(parentTpe)
            parentTpe = normalizeGenericTuple(typeArgs)
            childTpe = typeArgs(idx)
          }
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
                        val optic = ${ x.asInstanceOf[Expr[Lens[S, p]]] }
                        optic.apply(
                          optic.focus.asRecord
                            .flatMap(_.lensByIndex[c](${ Expr(idx) }))
                            .getOrElse(sys.error("Expected a record"))
                        )
                      }
                    } else if (x.isExprOf[Prism[S, p & S]]) {
                      '{
                        val optic = ${ x.asInstanceOf[Expr[Prism[S, p & S]]] }
                        optic.apply(
                          optic.focus.asRecord
                            .flatMap(_.lensByIndex[c](${ Expr(idx) }))
                            .getOrElse(sys.error("Expected a record"))
                        )
                      }
                    } else if (x.isExprOf[Optional[S, p]]) {
                      '{
                        val optic = ${ x.asInstanceOf[Expr[Optional[S, p]]] }
                        optic.apply(
                          optic.focus.asRecord
                            .flatMap(_.lensByIndex[c](${ Expr(idx) }))
                            .getOrElse(sys.error("Expected a record"))
                        )
                      }
                    } else if (x.isExprOf[Traversal[S, p]]) {
                      '{
                        val optic = ${ x.asInstanceOf[Expr[Traversal[S, p]]] }
                        optic.apply(
                          optic.focus.asRecord
                            .flatMap(_.lensByIndex[c](${ Expr(idx) }))
                            .getOrElse(sys.error("Expected a record"))
                        )
                      }
                    } else unsupportedOpticType(x)
                  }
              }
          })
      }
    }

    def unsupportedOpticType(expr: Expr[?]): Nothing = fail(s"Unsupported optic type: ${expr.asTerm.tpe.show}")

    val optic = toOptic(toPathBody(path.asTerm)).get
    // report.info(s"Generated optic:\n${optic.show}", Position.ofMacroExpansion)
    optic
  }
}
