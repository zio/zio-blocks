package zio.blocks.typeid

import scala.quoted.*

object TypeIdMacros {

  inline def derive[A <: AnyKind]: TypeId[A] = ${ deriveImpl[A] }

  def deriveImpl[A <: AnyKind: Type](using Quotes): Expr[TypeId[A]] = {
    import quotes.reflect.{TypeRepr => ReflTypeRepr, *}

    val tpe = ReflTypeRepr.of[A]

    def processOwner(sym: Symbol): Expr[zio.blocks.typeid.Owner] =
      if (sym == null || sym.isNoSymbol || sym.name == "<root>" || sym.name == "_root_") {
        '{ zio.blocks.typeid.Owner.Root }
      } else {
        val parent  = processOwner(sym.owner)
        val segment = if (sym.isPackageDef) {
          '{ zio.blocks.typeid.Owner.Package(${ Expr(sym.name) }) }
        } else if (sym.isType) {
          '{ zio.blocks.typeid.Owner.Type(${ Expr(sym.name) }) }
        } else {
          '{ zio.blocks.typeid.Owner.Term(${ Expr(sym.name) }) }
        }
        '{ ${ parent } / ${ segment } }
      }

    def processConstant(c: Constant): Expr[zio.blocks.typeid.Constant] = c match {
      case IntConstant(v)     => '{ zio.blocks.typeid.Constant.Int(${ Expr(v) }) }
      case LongConstant(v)    => '{ zio.blocks.typeid.Constant.Long(${ Expr(v) }) }
      case FloatConstant(v)   => '{ zio.blocks.typeid.Constant.Float(${ Expr(v) }) }
      case DoubleConstant(v)  => '{ zio.blocks.typeid.Constant.Double(${ Expr(v) }) }
      case CharConstant(v)    => '{ zio.blocks.typeid.Constant.Char(${ Expr(v) }) }
      case StringConstant(v)  => '{ zio.blocks.typeid.Constant.String(${ Expr(v) }) }
      case BooleanConstant(v) => '{ zio.blocks.typeid.Constant.Boolean(${ Expr(v) }) }
      case UnitConstant()     => '{ zio.blocks.typeid.Constant.Unit }
      case NullConstant()     => '{ zio.blocks.typeid.Constant.Null }
      case ClassOfConstant(t) => '{ zio.blocks.typeid.Constant.ClassOf(${ processTypeRepr(t, Set.empty) }) }
    }

    def processTypeBounds(b: TypeBounds, seen: Set[Symbol]): Expr[zio.blocks.typeid.TypeBounds] = {
      val low = b.low match {
        case _ if b.low =:= ReflTypeRepr.of[Nothing] => '{ None }
        case _                                       => '{ Some(${ processTypeRepr(b.low, seen) }) }
      }
      val high = b.hi match {
        case _ if b.hi =:= ReflTypeRepr.of[Any] => '{ None }
        case _                                  => '{ Some(${ processTypeRepr(b.hi, seen) }) }
      }
      '{ zio.blocks.typeid.TypeBounds(${ low }, ${ high }) }
    }

    def processTypeParam(s: Symbol, idx: Int, seen: Set[Symbol]): Expr[zio.blocks.typeid.TypeParam] = {
      val variance = if (s.flags.is(Flags.Covariant)) '{ zio.blocks.typeid.Variance.Covariant }
      else if (s.flags.is(Flags.Contravariant)) '{ zio.blocks.typeid.Variance.Contravariant }
      else '{ zio.blocks.typeid.Variance.Invariant }

      val boundsExpr = s.tree match {
        case td: TypeDef =>
          td.rhs match {
            case tt: TypeTree =>
              tt.tpe match {
                case b: TypeBounds => processTypeBounds(b, seen)
                case _             => '{ zio.blocks.typeid.TypeBounds.empty }
              }
            case _ => '{ zio.blocks.typeid.TypeBounds.empty }
          }
        case _ => '{ zio.blocks.typeid.TypeBounds.empty }
      }

      '{ zio.blocks.typeid.TypeParam(${ Expr(s.name) }, ${ Expr(idx) }, ${ variance }, ${ boundsExpr }) }
    }

    // basicRef: Identity only, NO parents, NO knownSubtypes. Used for breaking recursion.
    def basicRef(s: Symbol): Expr[zio.blocks.typeid.TypeRepr] = {
      val name  = Expr(s.name)
      val owner = processOwner(s.owner)

      val typeParams     = s.typeMembers.filter(m => m.isType && m.flags.is(Flags.Param))
      val typeParamsExpr = Expr.ofList(typeParams.zipWithIndex.map { case (tp, idx) =>
        processTypeParam(tp, idx, Set.empty)
      })

      val isObject = s.flags.is(Flags.Module)
      val defKind  = if (isObject) '{ zio.blocks.typeid.TypeDefKind.Object }
      else if (s.flags.is(Flags.Trait)) {
        val isSealed = s.flags.is(Flags.Sealed)
        '{ zio.blocks.typeid.TypeDefKind.Trait(${ Expr(isSealed) }, Nil) }
      } else '{ zio.blocks.typeid.TypeDefKind.Class(false, false, false, false) }

      '{
        zio.blocks.typeid.TypeRepr
          .Ref(zio.blocks.typeid.TypeId.nominal(${ name }, ${ owner }, ${ typeParamsExpr }, ${ defKind }, Nil))
      }
    }

    // shallowRef: Includes parents, but parents are basicRefs.
    def shallowRef(s: Symbol): Expr[zio.blocks.typeid.TypeRepr] = {
      val name  = Expr(s.name)
      val owner = processOwner(s.owner)

      val typeParams     = s.typeMembers.filter(m => m.isType && m.flags.is(Flags.Param))
      val typeParamsExpr = Expr.ofList(typeParams.zipWithIndex.map { case (tp, idx) =>
        processTypeParam(tp, idx, Set.empty)
      })

      val isObject = s.flags.is(Flags.Module)
      val defKind  = if (isObject) '{ zio.blocks.typeid.TypeDefKind.Object }
      else if (s.flags.is(Flags.Trait)) {
        val isSealed = s.flags.is(Flags.Sealed)
        // Use basicRef for subtyping to avoid cycles/bloat
        val subtypesExpr = if (isSealed) Expr.ofList(s.children.map(c => basicRef(c))) else '{ Nil }
        '{ zio.blocks.typeid.TypeDefKind.Trait(${ Expr(isSealed) }, ${ subtypesExpr }) }
      } else '{ zio.blocks.typeid.TypeDefKind.Class(false, false, false, false) }

      val parentsExpr =
        try {
          // Handle both Type and Term symbols (modules)
          val tpe =
            if (s.isType) TypeIdent(s).tpe
            else Ref(s).tpe // For objects/terms

          Expr.ofList(tpe.baseClasses.filterNot { ps =>
            val n = ps.name
            n == "Any" || n == "Object" || n == "Product" || n == "Serializable" || n == "Equals" || n == "Matchable" ||
            n.endsWith("Ops") || n.contains("StrictOptimized") || n == "DefaultSerializable" || ps == s
          }.take(5).map { parentSym =>
            // Use basicRef for parents to avoid cycles and code bloat
            basicRef(parentSym)
          })
        } catch {
          case _: Throwable =>
            '{ Nil }
        }

      '{
        zio.blocks.typeid.TypeRepr.Ref(
          zio.blocks.typeid.TypeId.nominal(${ name }, ${ owner }, ${ typeParamsExpr }, ${ defKind }, ${ parentsExpr })
        )
      }
    }

    def termPathFrom(t: ReflTypeRepr): Option[Expr[zio.blocks.typeid.TermPath]] = {
      def segmentFor(sym: Symbol, name: String): Expr[zio.blocks.typeid.TermPath.Segment] =
        if (sym.flags.is(Flags.Package)) '{ zio.blocks.typeid.TermPath.Package(${ Expr(name) }) }
        else if (sym.flags.is(Flags.Module)) '{ zio.blocks.typeid.TermPath.Module(${ Expr(name) }) }
        else if (sym.flags.is(Flags.Mutable)) '{ zio.blocks.typeid.TermPath.Var(${ Expr(name) }) }
        else if (sym.flags.is(Flags.Lazy)) '{ zio.blocks.typeid.TermPath.LazyVal(${ Expr(name) }) }
        else if (sym.flags.is(Flags.Method)) '{ zio.blocks.typeid.TermPath.Def(${ Expr(name) }) }
        else '{ zio.blocks.typeid.TermPath.Val(${ Expr(name) }) }

      def collect(
        cur: ReflTypeRepr,
        acc: List[Expr[zio.blocks.typeid.TermPath.Segment]]
      ): List[Expr[zio.blocks.typeid.TermPath.Segment]] = cur match {
        case TermRef(q, n) =>
          val seg = segmentFor(cur.termSymbol, n)
          collect(q, seg :: acc)
        case ThisType(tp) =>
          '{ zio.blocks.typeid.TermPath.This(${ Expr(tp.typeSymbol.name) }) } :: acc
        case SuperType(tp, mixin) =>
          val mixinName =
            if (mixin.typeSymbol == Symbol.noSymbol) '{ None } else '{ Some(${ Expr(mixin.typeSymbol.name) }) }
          '{ zio.blocks.typeid.TermPath.Super(${ Expr(tp.typeSymbol.name) }, ${ mixinName }) } :: acc
        case _ => acc
      }

      t match {
        case tr: TermRef =>
          val segs = Expr.ofList(collect(tr, Nil))
          Some('{ zio.blocks.typeid.TermPath(${ segs }) })
        case th: ThisType =>
          val segs = Expr.ofList(collect(th, Nil))
          Some('{ zio.blocks.typeid.TermPath(${ segs }) })
        case _ => None
      }
    }

    def processTypeRepr(t: ReflTypeRepr, seen: Set[Symbol]): Expr[zio.blocks.typeid.TypeRepr] = {
      (t: @unchecked) match {
        case _ if t =:= ReflTypeRepr.of[Any]     => '{ zio.blocks.typeid.TypeRepr.AnyType }
        case _ if t =:= ReflTypeRepr.of[Nothing] => '{ zio.blocks.typeid.TypeRepr.NothingType }
        case _ if t =:= ReflTypeRepr.of[Unit]    => '{ zio.blocks.typeid.TypeRepr.UnitType }
        case _ if t =:= ReflTypeRepr.of[Null]    => '{ zio.blocks.typeid.TypeRepr.NullType }

        case _ if seen.contains(t.typeSymbol) && t.typeSymbol != Symbol.noSymbol =>
          '{ zio.blocks.typeid.TypeRepr.RecThis }

        // Tuple types must be checked before AppliedType to correctly handle
        // both positional tuples (Int, String) and named tuples (name: String, age: Int)
        case _ if t <:< ReflTypeRepr.of[Tuple] =>
          def extractTuple(tp: ReflTypeRepr): Expr[zio.blocks.typeid.TypeRepr.Tuple] = tp match {
            case AppliedType(tycon, args) =>
              // NamedTuple is a Scala 3.5+ feature for labeled tuple types.
              // NamedTuple is represented as NamedTuple[Labels, Values] where:
              // - Labels is a tuple of string literal types (e.g., ("name", "age"))
              // - Values is a tuple of the actual types (e.g., (String, Int))
              if (tycon.typeSymbol.name == "NamedTuple") {
                // Extract label names from the first type argument
                val names = args(0) match {
                  case AppliedType(_, nameArgs) =>
                    nameArgs.map {
                      case ConstantType(StringConstant(s)) => Some(s)
                      case _                               => None
                    }
                  case _ => Nil
                }
                // Extract value types from the second type argument
                val values = args(1) match {
                  case AppliedType(_, valArgs) => valArgs
                  case _                       => Nil
                }
                // Combine labels and values into TupleElement instances
                val elems = Expr.ofList(values.zipWithIndex.map { (v, i) =>
                  val label = if (i < names.size) names(i) else None
                  '{ zio.blocks.typeid.TypeRepr.TupleElement(${ Expr(label) }, ${ processTypeRepr(v, seen) }) }
                })
                '{ zio.blocks.typeid.TypeRepr.Tuple(${ elems }) }
              } else {
                // Positional tuple: all elements have no labels
                val elems = Expr.ofList(
                  args.map(a => '{ zio.blocks.typeid.TypeRepr.TupleElement(None, ${ processTypeRepr(a, seen) }) })
                )
                '{ zio.blocks.typeid.TypeRepr.Tuple(${ elems }) }
              }
            case _ => '{ zio.blocks.typeid.TypeRepr.Tuple(Nil) }
          }
          extractTuple(t)

        case AppliedType(tycon, args) =>
          val tyconExpr = processTypeRepr(tycon, seen)
          val argsExpr  = Expr.ofList(args.map(a => processTypeRepr(a, seen)))
          '{ zio.blocks.typeid.TypeRepr.Applied(${ tyconExpr }, ${ argsExpr }) }

        case AndType(left, right) =>
          '{
            zio.blocks.typeid.TypeRepr.Intersection(${ processTypeRepr(left, seen) }, ${ processTypeRepr(right, seen) })
          }

        case OrType(left, right) =>
          '{ zio.blocks.typeid.TypeRepr.Union(${ processTypeRepr(left, seen) }, ${ processTypeRepr(right, seen) }) }

        case ConstantType(c) =>
          '{ zio.blocks.typeid.TypeRepr.ConstantType(${ processConstant(c) }) }

        case ByNameType(u) =>
          '{ zio.blocks.typeid.TypeRepr.ByName(${ processTypeRepr(u, seen) }) }

        case MatchType(bound, scrut, cases) =>
          // Match types are Scala 3 feature: type Elem[X] = X match { case String => Char; ... }
          // We capture the bound, scrutinee, and all cases with their patterns and results.
          // Bindings in patterns (e.g., 't' in 'case Array[t] => t') are captured as part
          // of the pattern structure and can be extracted during equality/subtyping operations.
          val boundExpr = processTypeRepr(bound, seen)
          val scrutExpr = processTypeRepr(scrut, seen)
          val casesExpr = Expr.ofList(cases.map { c =>
            c match {
              case MatchCase(pattern, rhs) =>
                val pat     = processTypeRepr(pattern, seen)
                val rhsExpr = processTypeRepr(rhs, seen)
                // Bindings are extracted from pattern structure during operations
                // For now, we use Nil as bindings are implicit in the pattern representation
                '{ zio.blocks.typeid.TypeRepr.MatchTypeCase(Nil, ${ pat }, ${ rhsExpr }) }
              case _ =>
                report.errorAndAbort(s"Expected MatchCase, got ${c.show}")
            }
          })
          '{ zio.blocks.typeid.TypeRepr.MatchType(${ boundExpr }, ${ scrutExpr }, ${ casesExpr }) }

        case AnnotatedType(u, _) => processTypeRepr(u, seen)

        case tr: Refinement =>
          def collect(
            t: ReflTypeRepr,
            members: List[Expr[zio.blocks.typeid.Member]]
          ): (ReflTypeRepr, List[Expr[zio.blocks.typeid.Member]]) = t match {
            case Refinement(p, n, i) =>
              val memberExpr = i match {
                case MethodType(paramNames, paramTypes, resTpe) =>
                  val paramPairs: List[(String, ReflTypeRepr)] = paramNames.zip(paramTypes)
                  val params                                   = Expr.ofList(paramPairs.map { case (pName, pTpe) =>
                    val (coreTpe, isRepeated) = pTpe match {
                      case AppliedType(tycon, List(elem)) if tycon.typeSymbol == defn.RepeatedParamClass =>
                        (elem, true)
                      case other => (other, false)
                    }
                    '{
                      zio.blocks.typeid.Param(
                        ${ Expr(pName) },
                        ${ processTypeRepr(coreTpe, seen) },
                        hasDefault = false,
                        isRepeated = ${ Expr(isRepeated) }
                      )
                    }
                  })
                  val clause = '{ zio.blocks.typeid.ParamClause.Regular(${ params }) }
                  val rTpe   = processTypeRepr(resTpe, seen)
                  '{ zio.blocks.typeid.Member.Def(${ Expr(n) }, Nil, List(${ clause }), ${ rTpe }) }
                case bounds: TypeBounds =>
                  val boundsExpr = processTypeBounds(bounds, seen)
                  '{ zio.blocks.typeid.Member.TypeMember(${ Expr(n) }, Nil, ${ boundsExpr }, None) }
                case _ =>
                  '{ zio.blocks.typeid.Member.Val(${ Expr(n) }, ${ processTypeRepr(i, seen) }) }
              }
              collect(p, memberExpr :: members)
            case _ => (t, members)
          }
          val (base, members) = collect(tr, Nil)
          '{ zio.blocks.typeid.TypeRepr.Structural(List(${ processTypeRepr(base, seen) }), ${ Expr.ofList(members) }) }

        case MethodType(_, paramTpes, resultTpe) =>
          val params = Expr.ofList(paramTpes.map(t => processTypeRepr(t, seen)))
          val result = processTypeRepr(resultTpe, seen)
          '{ zio.blocks.typeid.TypeRepr.Function(${ params }, ${ result }) }

        case PolyType(paramNames, _, resultTpe) =>
          // Simplified: PolyType maps to PolyFunction
          val params = Expr.ofList(paramNames.zipWithIndex.map { case (name, idx) =>
            '{
              zio.blocks.typeid.TypeParam(
                ${ Expr(name) },
                ${ Expr(idx) },
                zio.blocks.typeid.Variance.Invariant,
                zio.blocks.typeid.TypeBounds.empty
              )
            }
          })
          val result = processTypeRepr(resultTpe, seen)
          '{ zio.blocks.typeid.TypeRepr.PolyFunction(${ params }, ${ result }) }

        case t @ TypeRef(prefix, _) =>
          termPathFrom(prefix) match {
            case Some(pathExpr) =>
              // For local types (classes/traits defined in local scopes),
              // recursively derive a full TypeId instead of using TypeSelect
              if (t.typeSymbol.isClassDef || t.typeSymbol.flags.is(Flags.Trait)) {
                // Recursively derive the full TypeId for this type
                shallowRef(t.typeSymbol)
              } else {
                '{ zio.blocks.typeid.TypeRepr.TypeSelect(${ pathExpr }, ${ Expr(t.name) }) }
              }
            case None =>
              if (t.typeSymbol.isTypeParam) {
                val idx       = t.typeSymbol.owner.typeMembers.indexOf(t.typeSymbol)
                val paramExpr = processTypeParam(t.typeSymbol, idx, seen)
                '{ zio.blocks.typeid.TypeRepr.ParamRef(${ paramExpr }) }
              } else if (!t.typeSymbol.flags.is(Flags.Opaque) && t.dealias != t) {
                val symbol = t.typeSymbol
                val name   = Expr(symbol.name)
                val owner  = processOwner(symbol.owner)

                val typeParams     = symbol.typeMembers.filter(m => m.isType && m.flags.is(Flags.Param))
                val typeParamsExpr = Expr.ofList(typeParams.zipWithIndex.map { case (tp, idx) =>
                  processTypeParam(tp, idx, Set.empty)
                })

                // Add current symbol to seen set before dealiasing to prevent infinite recursion
                val aliasedExpr = processTypeRepr(t.dealias, seen + symbol)

                '{
                  zio.blocks.typeid.TypeRepr
                    .Ref(zio.blocks.typeid.TypeId.alias(${ name }, ${ owner }, ${ typeParamsExpr }, ${ aliasedExpr }))
                }
              } else {
                shallowRef(t.typeSymbol)
              }
          }

        case TypeLambda(_, _, resType) =>
          // Handle type lambdas (e.g. type constructors like List)
          // If it's a simple application like [A] =>> List[A], extract the base type constructor
          resType match {
            case AppliedType(tycon, _) => processTypeRepr(tycon, seen)
            case _                     => processTypeRepr(resType, seen)
          }

        case t: TermRef =>
          def getSegments(
            cur: ReflTypeRepr,
            acc: List[Expr[zio.blocks.typeid.TermPath.Segment]]
          ): List[Expr[zio.blocks.typeid.TermPath.Segment]] = cur match {
            case TermRef(q, n) =>
              val s   = cur.termSymbol
              val seg = if (s.flags.is(Flags.Package)) '{ zio.blocks.typeid.TermPath.Package(${ Expr(n) }) }
              else if (s.flags.is(Flags.Module)) '{ zio.blocks.typeid.TermPath.Module(${ Expr(n) }) }
              else '{ zio.blocks.typeid.TermPath.Val(${ Expr(n) }) }
              getSegments(q, seg :: acc)
            case ThisType(tp) =>
              '{ zio.blocks.typeid.TermPath.This(${ Expr(tp.typeSymbol.name) }) } :: acc
            case _ => acc
          }
          val segs = Expr.ofList(getSegments(t, Nil))
          '{ zio.blocks.typeid.TypeRepr.Singleton(zio.blocks.typeid.TermPath(${ segs })) }

        case _ =>
          '{ zio.blocks.typeid.TypeRepr.AnyType }
      }
    }

    // Handle composite types (Applied/Intersections/Unions) that describe the type's structure
    tpe match {
      case AppliedType(tycon, _) =>
        val repr   = processTypeRepr(tpe, Set.empty)
        val symbol = tycon.typeSymbol
        return '{
          zio.blocks.typeid.TypeId.alias[A](${ Expr(symbol.name) }, ${ processOwner(symbol.owner) }, Nil, ${ repr })
        }
      case AndType(_, _) =>
        val repr = processTypeRepr(tpe, Set.empty)
        return '{ zio.blocks.typeid.TypeId.alias[A]("Intersection", zio.blocks.typeid.Owner.Root, Nil, ${ repr }) }
      case OrType(_, _) =>
        val repr = processTypeRepr(tpe, Set.empty)
        return '{ zio.blocks.typeid.TypeId.alias[A]("Union", zio.blocks.typeid.Owner.Root, Nil, ${ repr }) }
      case _ => // Continue
    }

    val symbol = tpe match {
      case tr: TermRef => tr.termSymbol
      case _           => tpe.typeSymbol
    }

    val nameExpr  = Expr(symbol.name)
    val ownerExpr = processOwner(symbol.owner)

    val typeParamsExpr = {
      val typeParams = symbol.typeMembers.filter(s => s.isType && s.flags.is(Flags.Param))
      Expr.ofList(typeParams.zipWithIndex.map { case (s, idx) =>
        processTypeParam(s, idx, Set.empty)
      })
    }

    // Determine DefKind and Children
    val children = symbol.children

    val defKindExpr = if (symbol.flags.is(Flags.Enum) && !symbol.flags.is(Flags.Case)) {
      val casesExpr = Expr.ofList(children.zipWithIndex.map { case (c, idx) =>
        val isObjectCase = c.flags.is(Flags.Module)
        val paramsExpr   = if (isObjectCase) '{ Nil }
        else {
          // Extract params for enum case
          val caseParams = c.primaryConstructor.paramSymss.flatten.filter(!_.isTypeParam)
          Expr.ofList(caseParams.map { p =>
            '{
              zio.blocks.typeid
                .EnumCaseParam(${ Expr(p.name) }, ${ processTypeRepr(p.tree.asInstanceOf[ValDef].tpt.tpe, Set.empty) })
            }
          })
        }
        '{ zio.blocks.typeid.EnumCaseInfo(${ Expr(c.name) }, ${ Expr(idx) }, ${ paramsExpr }, ${ Expr(isObjectCase) }) }
      })
      '{ zio.blocks.typeid.TypeDefKind.Enum(${ casesExpr }) }
    } else if (symbol.flags.is(Flags.Trait)) {
      val isSealed     = symbol.flags.is(Flags.Sealed)
      val subtypesExpr = if (isSealed) {
        Expr.ofList(children.map(c => shallowRef(c)))
      } else '{ Nil }
      '{ zio.blocks.typeid.TypeDefKind.Trait(isSealed = ${ Expr(isSealed) }, knownSubtypes = ${ subtypesExpr }) }
    } else if (symbol.flags.is(Flags.Module)) {
      '{ zio.blocks.typeid.TypeDefKind.Object }
    } else {
      '{
        zio.blocks.typeid.TypeDefKind.Class(
          isFinal = ${ Expr(symbol.flags.is(Flags.Final)) },
          isAbstract = ${ Expr(symbol.flags.is(Flags.Abstract)) },
          isCase = ${ Expr(symbol.flags.is(Flags.Case)) },
          isValue = ${ Expr(tpe <:< ReflTypeRepr.of[AnyVal]) }
        )
      }
    }

    // Parents - capture with proper type application
    val parentsExpr = Expr.ofList(tpe.baseClasses.filterNot { s =>
      val n = s.name
      n == "Any" || n == "Object" || n == "Product" || n == "Serializable" || n == "Equals" || n == "Matchable" ||
      n.endsWith("Ops") || n.contains("StrictOptimized") || n == "DefaultSerializable" || s == symbol
    }.take(5).map { parentSym =>
      // Get the actual parent type as applied to this type's parameters
      val parentTpe = tpe.baseType(parentSym)
      if (parentTpe =:= ReflTypeRepr.of[Nothing] || parentTpe =:= ReflTypeRepr.of[Any]) {
        shallowRef(parentSym)
      } else {
        processTypeRepr(parentTpe, Set.empty)
      }
    })

    val isOpaque = symbol.flags.is(Flags.Opaque)

    if (symbol.isType && !isOpaque && tpe.dealias != tpe) {
      val aliasedTpe  = tpe.dealias
      val aliasedExpr = processTypeRepr(aliasedTpe, Set.empty)
      '{
        zio.blocks.typeid.TypeId.alias[A](
          name = ${ nameExpr },
          owner = ${ ownerExpr },
          typeParams = ${ typeParamsExpr },
          aliased = ${ aliasedExpr }
        )
      }
    } else if (isOpaque) {
      val reprTpe  = tpe.dealias
      val reprExpr = processTypeRepr(reprTpe, Set.empty)
      '{
        zio.blocks.typeid.TypeId.opaque[A](
          name = ${ nameExpr },
          owner = ${ ownerExpr },
          typeParams = ${ typeParamsExpr },
          representation = ${ reprExpr }
        )
      }
    } else {
      '{
        zio.blocks.typeid.TypeId.nominal[A](
          name = ${ nameExpr },
          owner = ${ ownerExpr },
          typeParams = ${ typeParamsExpr },
          defKind = ${ defKindExpr },
          parents = ${ parentsExpr }
        )
      }
    }
  }
}
