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

package zio.blocks.schema

import scala.annotation.tailrec
import scala.compiletime.{error, summonInline}

/**
 * Mix this trait into a companion object to build optics for `S` from a
 * selector lambda: `$(_.title)`, or the same with a name, `optic(_.title)`. A
 * macro reads the lambda at compile time and builds the optic from the implicit
 * `Schema[S]`. The kind of optic follows from the path: a [[Lens]] when every
 * step is a field, a [[Prism]] when every step is `when`, a [[Traversal]] when
 * any step is `each`, `eachKey`, `eachValue`, `atIndices`, `atKeys` or
 * `searchFor`, and an [[Optional]] otherwise.
 *
 * A path is a chain of these steps:
 *
 *   - `.field`: a field of a record;
 *   - `.when[C]`: the case `C` of a variant;
 *   - `.at(i)`, `.atIndices(i, j)`, `.each`: elements of a sequence;
 *   - `.atKey(k)`, `.atKeys(k1, k2)`, `.eachKey`, `.eachValue`: keys and values
 *     of a map;
 *   - `.wrapped[B]`: the value of type `B` inside a wrapper;
 *   - `.searchFor[T]`: every value at or below this point that decodes as a `T`
 *     (needs an implicit `Schema[T]`; `modify` skips map keys).
 *
 * On Scala 3, a path can also select a tuple element by index, as in `$(_(0))`,
 * and `when` also takes a case of a union type. The step methods compile only
 * inside `$` and `optic`, and any other expression in the lambda is a compile
 * error.
 *
 * The macro checks the path against the types, and the schema is checked when
 * the optic is built: if the schema at a step has another shape than the step
 * needs, for example a `.field` step where the schema is a wrapper built with
 * `Schema#transform`, building the optic throws a `RuntimeException` such as
 * `Expected a record`.
 *
 * {{{
 * sealed trait Format
 * case class Paperback(pages: Int) extends Format
 * case class Ebook(sizeMb: Double) extends Format
 *
 * case class Book(title: String, tags: List[String], format: Format)
 * object Book extends CompanionOptics[Book] {
 *   implicit val schema: Schema[Book] = Schema.derived
 *   val title: Lens[Book, String]     = $(_.title)
 *   val pages: Optional[Book, Int]    = $(_.format.when[Paperback].pages)
 *   val tags: Traversal[Book, String] = optic(_.tags.each)
 * }
 *
 * val zionomicon = Book("Zionomicon", List("zio", "scala"), Paperback(870))
 * Book.pages.getOption(zionomicon)   // Some(870)
 * Book.pages.toString              // Optional(_.format.when[Paperback].pages)
 * }}}
 *
 * @see
 *   [[https://zioblocks.com/reference/schema/optics/ Optics]]
 */
transparent trait CompanionOptics[S] {
  extension [A](a: A) {

    /**
     * In a path, focuses on the case `B` of a variant. Compiles only inside `$`
     * and `optic`.
     */
    inline def when[B <: A]: B = error("Can only be used inside `$(_)` and `optic(_)` macros")

    /**
     * In a path, focuses on the value of type `B` inside a wrapper, such as a
     * schema built with `Schema#transform`. Compiles only inside `$` and
     * `optic`.
     */
    inline def wrapped[B]: B = error("Can only be used inside `$(_)` and `optic(_)` macros")

    /**
     * In a path, focuses on every value at or below this point that decodes as
     * a `B`, found depth first. It needs an implicit `Schema[B]`. Map keys are
     * read by `fold` and `check` but skipped by `modify` (see
     * `docs/dev/issues.md`). Compiles only inside `$` and `optic`.
     */
    inline def searchFor[B]: B = error("Can only be used inside `$(_)` and `optic(_)` macros")
  }

  extension [C[_], A](c: C[A]) {

    /**
     * In a path, focuses on the element at `index` of a sequence. Compiles only
     * inside `$` and `optic`.
     */
    inline def at(index: Int): A = error("Can only be used inside `$(_)` and `optic(_)` macros")

    /**
     * In a path, focuses on the elements at `indices` of a sequence. Compiles
     * only inside `$` and `optic`.
     */
    inline def atIndices(indices: Int*): A = error("Can only be used inside `$(_)` and `optic(_)` macros")

    /**
     * In a path, focuses on every element of a sequence. Compiles only inside
     * `$` and `optic`.
     */
    inline def each: A = error("Can only be used inside `$(_)` and `optic(_)` macros")
  }

  extension [M[_, _], K, V](m: M[K, V]) {

    /**
     * In a path, focuses on the value at `key` in a map. Compiles only inside
     * `$` and `optic`.
     */
    inline def atKey(key: K): V = error("Can only be used inside `$(_)` and `optic(_)` macros")

    /**
     * In a path, focuses on the values at `keys` in a map. Compiles only inside
     * `$` and `optic`.
     */
    inline def atKeys(keys: K*): V = error("Can only be used inside `$(_)` and `optic(_)` macros")

    /**
     * In a path, focuses on every key of a map. Compiles only inside `$` and
     * `optic`.
     */
    inline def eachKey: K = error("Can only be used inside `$(_)` and `optic(_)` macros")

    /**
     * In a path, focuses on every value of a map. Compiles only inside `$` and
     * `optic`.
     */
    inline def eachValue: V = error("Can only be used inside `$(_)` and `optic(_)` macros")
  }

  /**
   * Builds the optic that `path` describes (see [[CompanionOptics]]). The
   * result has the exact optic type, such as `Lens[S, String]`. `$` and `optic`
   * are the same macro.
   */
  transparent inline def $[A](inline path: S => A)(using schema: Schema[S]): Any =
    ${ CompanionOptics.optic('path, 'schema) }

  /**
   * Builds the optic that `path` describes (see [[CompanionOptics]]). The
   * result has the exact optic type, such as `Lens[S, String]`. `$` and `optic`
   * are the same macro.
   */
  transparent inline def optic[A](inline path: S => A)(using schema: Schema[S]): Any =
    ${ CompanionOptics.optic('path, 'schema) }
}

/**
 * Macro implementation of `CompanionOptics#$` and `CompanionOptics#optic`.
 */
private object CompanionOptics {
  import scala.quoted._

  /**
   * Macro implementation of `CompanionOptics#$` and `CompanionOptics#optic`.
   */
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
