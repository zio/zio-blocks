package zio.blocks.typeid

object Subtyping {

  final case class Context(
    assumptions: Set[(TypeRepr, TypeRepr)] = Set.empty,
    depth: Int = 0,
    maxDepth: Int = 100
  ) {
    def assume(sub: TypeRepr, sup: TypeRepr): Context =
      copy(assumptions = assumptions + ((sub, sup)))

    def isAssumed(sub: TypeRepr, sup: TypeRepr): Boolean =
      assumptions.contains((sub, sup))

    def deeper: Context  = copy(depth = depth + 1)
    def tooDeep: Boolean = depth >= maxDepth
  }

  /**
   * Check if `sub` is a subtype of `sup`.
   *
   * Implements the Scala 3 subtyping rules including:
   *   - Reflexivity: A <: A
   *   - Top: A <: Any for all A
   *   - Bottom: Nothing <: A for all A
   *   - Variance-aware generic subtyping
   *   - Union/intersection type rules
   *   - Structural subtyping
   */
  def isSubtype(sub: TypeRepr, sup: TypeRepr, ctx: Context = Context()): Boolean = {
    val nc = TypeNormalization.normalize(sub)
    val np = TypeNormalization.normalize(sup)

    def combineIntersectionForStructural(comps: List[TypeRepr]): TypeRepr.Structural = {
      val (structurals, others) = comps.partition(_.isInstanceOf[TypeRepr.Structural])
      val structuralParents     = structurals.collect { case TypeRepr.Structural(ps, _) => ps }.flatten
      val structuralMembers     = structurals.collect { case TypeRepr.Structural(_, ms) => ms }.flatten
      TypeRepr.Structural(structuralParents ++ others, structuralMembers)
    }

    def loop(sub: TypeRepr, sup: TypeRepr, ctx: Context): Boolean = {
      if (TypeEquality.areEqual(sub, sup)) true
      else if (ctx.isAssumed(sub, sup)) true
      else if (ctx.tooDeep) false
      else {
        val nextCtx = ctx.assume(sub, sup).deeper

        def isNothingId(repr: TypeRepr): Boolean = repr match {
          case TypeRepr.Ref(id)     => id.fullName == "scala.Nothing"
          case TypeRepr.NothingType => true
          case _                    => false
        }

        def isAnyId(repr: TypeRepr): Boolean = repr match {
          case TypeRepr.Ref(id) => id.fullName == "scala.Any" || id.fullName == "java.lang.Object"
          case TypeRepr.AnyType => true
          case _                => false
        }

        (sub, sup) match {
          case _ if isAnyId(sup)     => true
          case _ if isNothingId(sub) => true

          case (_, TypeRepr.Union(comps)) =>
            comps.exists(comp => loop(sub, comp, nextCtx))

          case (TypeRepr.Intersection(comps), _) =>
            if (comps.exists(comp => loop(comp, sup, nextCtx))) true
            else
              sup match {
                case _: TypeRepr.Structural =>
                  loop(combineIntersectionForStructural(comps), sup, nextCtx)
                case _ =>
                  false
              }

          case (TypeRepr.Union(comps), _) =>
            comps.forall(comp => loop(comp, sup, nextCtx))

          case (_, TypeRepr.Intersection(comps)) =>
            comps.forall(comp => loop(sub, comp, nextCtx))

          case (TypeRepr.Applied(cTycon, cArgs), TypeRepr.Applied(pTycon, pArgs))
              if cArgs.size == pArgs.size && loop(cTycon, pTycon, nextCtx) =>
            val variances = cTycon match {
              case TypeRepr.Ref(id) => id.typeParams.map(_.variance)
              case _                => List.fill(cArgs.size)(Variance.Invariant)
            }
            cArgs.zip(pArgs).zip(variances).forall { case ((ca, pa), v) =>
              v match {
                case Variance.Invariant =>
                  loop(ca, pa, nextCtx) && loop(pa, ca, nextCtx)
                case Variance.Covariant =>
                  loop(ca, pa, nextCtx)
                case Variance.Contravariant =>
                  loop(pa, ca, nextCtx)
              }
            }

          case (TypeRepr.Applied(TypeRepr.Ref(id), cArgs), _) =>
            if (id.typeParams.size == cArgs.size) {
              val substitutions = id.typeParams.zip(cArgs).toMap
              id.parents.exists { parentRepr =>
                loop(TypeRepr.substitute(parentRepr, substitutions), sup, nextCtx)
              }
            } else false

          case (TypeRepr.Function(paramsC, resC), TypeRepr.Function(paramsP, resP)) =>
            paramsC.size == paramsP.size &&
            loop(resC, resP, nextCtx) &&
            paramsC.zip(paramsP).forall { case (cc, pp) => loop(pp, cc, nextCtx) }

          case (TypeRepr.Tuple(elemsC), TypeRepr.Tuple(elemsP)) =>
            elemsC.size == elemsP.size &&
            elemsC.zip(elemsP).forall { case (cc, pp) => loop(cc.tpe, pp.tpe, nextCtx) }

          case (TypeRepr.ConstantType(cA), TypeRepr.ConstantType(cB)) =>
            cA == cB

          case (TypeRepr.ConstantType(cA), TypeRepr.Ref(idB)) =>
            (cA, idB.fullName) match {
              case (Constant.Int(_), "scala.Int")           => true
              case (Constant.Long(_), "scala.Long")         => true
              case (Constant.String(_), "java.lang.String") => true
              case (Constant.Boolean(_), "scala.Boolean")   => true
              case _                                        => false
            }

          case (TypeRepr.Structural(_, mA), TypeRepr.Structural(pB, mB)) =>
            pB.forall(pb => loop(sub, pb, nextCtx)) &&
            mB.forall(mb => mA.exists(ma => memberConforms(ma, mb)))

          case (TypeRepr.Structural(pA, _), _) =>
            pA.exists(p => loop(p, sup, nextCtx))

          case (TypeRepr.TypeSelect(qA, mA), TypeRepr.TypeSelect(qB, mB)) =>
            qA.asString == qB.asString && mA == mB

          case (TypeRepr.TypeSelect(q, m), TypeRepr.Ref(parentId)) =>
            // Check if TypeSelect refers to the same type as the Ref
            // Compare names first
            m == parentId.name && {
              val pathStr = q.asString.replace(".this", "")
              // Extract the last component from the owner
              val ownerLast = parentId.owner.toString match {
                case s if s.contains("Type(") =>
                  s.substring(s.indexOf("Type(") + 5, s.indexOf(")", s.indexOf("Type(")))
                case _ => ""
              }
              // Check if the path matches the owner's last component
              pathStr == ownerLast || pathStr.endsWith(s".$ownerLast")
            }

          case (TypeRepr.Ref(id), TypeRepr.TypeSelect(q, m)) =>
            // Reverse of the above case
            m == id.name && {
              val pathStr   = q.asString.replace(".this", "")
              val ownerLast = id.owner.toString match {
                case s if s.contains("Type(") =>
                  s.substring(s.indexOf("Type(") + 5, s.indexOf(")", s.indexOf("Type(")))
                case _ => ""
              }
              pathStr == ownerLast || pathStr.endsWith(s".$ownerLast")
            }

          case (TypeRepr.Singleton(pA), TypeRepr.Singleton(pB)) =>
            pA.asString == pB.asString

          case (TypeRepr.Ref(id), TypeRepr.Ref(parentId)) =>
            // Direct reference comparison - check if parent is in the type's parents
            id.parents.exists { p =>
              val normalizedParent = TypeNormalization.normalize(p)
              // Use the full loop with the normalized parent
              loop(normalizedParent, sup, nextCtx)
            } ||
            // For standard library types, check full name hierarchy
            {
              val child  = id.fullName
              val parent = parentId.fullName
              child.startsWith(parent) &&
              (child == parent || child.substring(parent.length).startsWith(".")) &&
              // Check if it's a real inheritance relationship by looking at parents
              id.parents.exists { p =>
                p match {
                  case TypeRepr.Ref(parentRef)      => parentRef.fullName == parentId.fullName
                  case TypeRepr.TypeSelect(_, name) => name == parentId.name
                  case _                            => false
                }
              }
            }

          case _ => false
        }
      }
    }

    loop(nc, np, ctx)
  }

  private def memberConforms(ma: Member, mb: Member): Boolean = (ma, mb) match {
    case (Member.Val(na, ta, ma_, _, _), Member.Val(nb, tb, mb_, _, _)) if na == nb && ma_ == mb_ =>
      isSubtype(ta, tb)
    case (Member.Def(na, tpa, pca, ra, _, _), Member.Def(nb, tpb, pcb, rb, _, _)) if na == nb && tpa.size == tpb.size =>
      pca.size == pcb.size &&
      pca.zip(pcb).forall { case (ca, cb) => paramClauseConforms(ca, cb) } &&
      isSubtype(ra, rb)
    case (Member.TypeMember(na, _, ba, aa, _), Member.TypeMember(nb, _, bb, ab, _)) if na == nb =>
      typeMemberConforms(ba, aa, bb, ab)
    case _ => false
  }

  private def paramClauseConforms(a: ParamClause, b: ParamClause): Boolean =
    a.getClass == b.getClass &&
      a.params.size == b.params.size &&
      a.params.zip(b.params).forall { case (pa, pb) => paramConforms(pa, pb) }

  private def paramConforms(a: Param, b: Param): Boolean =
    a.isRepeated == b.isRepeated && a.hasDefault == b.hasDefault &&
      isSubtype(b.tpe, a.tpe)

  private def typeMemberConforms(
    boundsA: TypeBounds,
    aliasA: Option[TypeRepr],
    boundsB: TypeBounds,
    aliasB: Option[TypeRepr]
  ): Boolean = {
    val aliasOk = (aliasA, aliasB) match {
      case (Some(a), Some(b)) => isSubtype(a, b) && isSubtype(b, a)
      case (None, None)       => true
      case _                  => false
    }
    val lowerOk = (boundsA.lower, boundsB.lower) match {
      case (Some(a), Some(b)) => isSubtype(b, a)
      case (None, None)       => true
      case (None, Some(_))    => false
      case (Some(_), None)    => true
    }
    val upperOk = (boundsA.upper, boundsB.upper) match {
      case (Some(a), Some(b)) => isSubtype(a, b)
      case (None, None)       => true
      case (Some(_), None)    => false
      case (None, Some(_))    => true
    }
    aliasOk && lowerOk && upperOk
  }

  def isEquivalent(a: TypeRepr, b: TypeRepr): Boolean =
    isSubtype(a, b) && isSubtype(b, a)

  /**
   * Check if a type conforms to given bounds.
   */
  def conformsToBounds(tpe: TypeRepr, bounds: TypeBounds, ctx: Context = Context()): Boolean = {
    val lowerOk = bounds.lower.forall(lo => isSubtype(lo, tpe, ctx))
    val upperOk = bounds.upper.forall(hi => isSubtype(tpe, hi, ctx))
    lowerOk && upperOk
  }

  /**
   * Find the least upper bound (LUB) of two types. Returns the most specific
   * type that is a supertype of both.
   *
   * The LUB algorithm:
   *   1. If a <: b, return b
   *   2. If b <: a, return a
   *   3. For applied types with the same constructor, compute LUB element-wise
   *      with variance
   *   4. For union types, flatten and compute LUB of all components
   *   5. Otherwise, return Union(a, b) (the trivial LUB)
   */
  def lub(a: TypeRepr, b: TypeRepr): TypeRepr = {
    val aNorm = TypeNormalization.normalize(a)
    val bNorm = TypeNormalization.normalize(b)

    if (TypeEquality.areEqual(aNorm, bNorm)) aNorm
    else if (isSubtype(aNorm, bNorm)) bNorm
    else if (isSubtype(bNorm, aNorm)) aNorm
    else {
      (aNorm, bNorm) match {
        // LUB of Nothing with anything is the other type
        case (TypeRepr.NothingType, other)                           => other
        case (other, TypeRepr.NothingType)                           => other
        case (TypeRepr.Ref(id), _) if id.fullName == "scala.Nothing" => bNorm
        case (_, TypeRepr.Ref(id)) if id.fullName == "scala.Nothing" => aNorm

        // LUB of Any with anything is Any
        case (TypeRepr.AnyType, _) | (_, TypeRepr.AnyType)       => TypeRepr.AnyType
        case (TypeRepr.Ref(id), _) if id.fullName == "scala.Any" => aNorm
        case (_, TypeRepr.Ref(id)) if id.fullName == "scala.Any" => bNorm

        // LUB of unions: flatten and compute
        case (TypeRepr.Union(compsA), TypeRepr.Union(compsB)) =>
          TypeRepr.Union(compsA ++ compsB)
        case (TypeRepr.Union(comps), other) =>
          TypeRepr.Union(comps :+ other)
        case (other, TypeRepr.Union(comps)) =>
          TypeRepr.Union(other :: comps)

        // LUB of applied types with the same type constructor
        case (TypeRepr.Applied(tcA, argsA), TypeRepr.Applied(tcB, argsB))
            if TypeEquality.areEqual(tcA, tcB) && argsA.size == argsB.size =>
          val variances = tcA match {
            case TypeRepr.Ref(id) => id.typeParams.map(_.variance)
            case _                => List.fill(argsA.size)(Variance.Invariant)
          }
          val lubArgs = argsA.zip(argsB).zip(variances).map { case ((argA, argB), v) =>
            v match {
              case Variance.Covariant     => lub(argA, argB)
              case Variance.Contravariant => glb(argA, argB)
              case Variance.Invariant     =>
                if (TypeEquality.areEqual(argA, argB)) argA
                else TypeRepr.Union(argA, argB) // Approximation for invariant
            }
          }
          TypeRepr.Applied(tcA, lubArgs)

        // LUB of tuples with the same arity
        case (TypeRepr.Tuple(elemsA), TypeRepr.Tuple(elemsB)) if elemsA.size == elemsB.size =>
          val lubElems = elemsA.zip(elemsB).map { case (eA, eB) =>
            TypeRepr.TupleElement(eA.label.orElse(eB.label), lub(eA.tpe, eB.tpe))
          }
          TypeRepr.Tuple(lubElems)

        // LUB of functions
        case (TypeRepr.Function(paramsA, resA), TypeRepr.Function(paramsB, resB)) if paramsA.size == paramsB.size =>
          val glbParams = paramsA.zip(paramsB).map { case (pA, pB) => glb(pA, pB) }
          val lubResult = lub(resA, resB)
          TypeRepr.Function(glbParams, lubResult)

        // Try to find common parent from the type hierarchy
        case (TypeRepr.Ref(idA), TypeRepr.Ref(idB)) =>
          findCommonParent(idA, idB).getOrElse(TypeRepr.Union(aNorm, bNorm))

        // Default: form a union (trivial LUB)
        case _ => TypeRepr.Union(aNorm, bNorm)
      }
    }
  }

  /**
   * Find the greatest lower bound (GLB) of two types. Returns the most general
   * type that is a subtype of both.
   *
   * The GLB algorithm:
   *   1. If a <: b, return a
   *   2. If b <: a, return b
   *   3. For applied types with the same constructor, compute GLB element-wise
   *      with variance
   *   4. For intersection types, flatten and compute GLB of all components
   *   5. Otherwise, return Intersection(a, b) (the trivial GLB)
   */
  def glb(a: TypeRepr, b: TypeRepr): TypeRepr = {
    val aNorm = TypeNormalization.normalize(a)
    val bNorm = TypeNormalization.normalize(b)

    if (TypeEquality.areEqual(aNorm, bNorm)) aNorm
    else if (isSubtype(aNorm, bNorm)) aNorm
    else if (isSubtype(bNorm, aNorm)) bNorm
    else {
      (aNorm, bNorm) match {
        // GLB of Any with anything is the other type
        case (TypeRepr.AnyType, other)                           => other
        case (other, TypeRepr.AnyType)                           => other
        case (TypeRepr.Ref(id), _) if id.fullName == "scala.Any" => bNorm
        case (_, TypeRepr.Ref(id)) if id.fullName == "scala.Any" => aNorm

        // GLB of Nothing with anything is Nothing
        case (TypeRepr.NothingType, _) | (_, TypeRepr.NothingType)   => TypeRepr.NothingType
        case (TypeRepr.Ref(id), _) if id.fullName == "scala.Nothing" => aNorm
        case (_, TypeRepr.Ref(id)) if id.fullName == "scala.Nothing" => bNorm

        // GLB of intersections: flatten and compute
        case (TypeRepr.Intersection(compsA), TypeRepr.Intersection(compsB)) =>
          TypeRepr.Intersection(compsA ++ compsB)
        case (TypeRepr.Intersection(comps), other) =>
          TypeRepr.Intersection(comps :+ other)
        case (other, TypeRepr.Intersection(comps)) =>
          TypeRepr.Intersection(other :: comps)

        // GLB of applied types with the same type constructor
        case (TypeRepr.Applied(tcA, argsA), TypeRepr.Applied(tcB, argsB))
            if TypeEquality.areEqual(tcA, tcB) && argsA.size == argsB.size =>
          val variances = tcA match {
            case TypeRepr.Ref(id) => id.typeParams.map(_.variance)
            case _                => List.fill(argsA.size)(Variance.Invariant)
          }
          val glbArgs = argsA.zip(argsB).zip(variances).map { case ((argA, argB), v) =>
            v match {
              case Variance.Covariant     => glb(argA, argB)
              case Variance.Contravariant => lub(argA, argB)
              case Variance.Invariant     =>
                if (TypeEquality.areEqual(argA, argB)) argA
                else TypeRepr.Intersection(argA, argB) // Approximation for invariant
            }
          }
          TypeRepr.Applied(tcA, glbArgs)

        // GLB of tuples with the same arity
        case (TypeRepr.Tuple(elemsA), TypeRepr.Tuple(elemsB)) if elemsA.size == elemsB.size =>
          val glbElems = elemsA.zip(elemsB).map { case (eA, eB) =>
            TypeRepr.TupleElement(eA.label.orElse(eB.label), glb(eA.tpe, eB.tpe))
          }
          TypeRepr.Tuple(glbElems)

        // GLB of functions
        case (TypeRepr.Function(paramsA, resA), TypeRepr.Function(paramsB, resB)) if paramsA.size == paramsB.size =>
          val lubParams = paramsA.zip(paramsB).map { case (pA, pB) => lub(pA, pB) }
          val glbResult = glb(resA, resB)
          TypeRepr.Function(lubParams, glbResult)

        // Default: form an intersection (trivial GLB)
        case _ => TypeRepr.Intersection(aNorm, bNorm)
      }
    }
  }

  /**
   * Try to find a common parent type for two TypeIds by walking up the parent
   * hierarchy.
   */
  private def findCommonParent(idA: TypeId[_], idB: TypeId[_]): Option[TypeRepr] = {
    def collectParents(id: TypeId[_], visited: Set[String]): Set[String] =
      if (visited.contains(id.fullName)) visited
      else {
        val newVisited = visited + id.fullName
        id.parents.foldLeft(newVisited) { (acc, parent) =>
          parent match {
            case TypeRepr.Ref(parentId)                      => collectParents(parentId, acc)
            case TypeRepr.Applied(TypeRepr.Ref(parentId), _) => collectParents(parentId, acc)
            case _                                           => acc
          }
        }
      }

    val parentsA = collectParents(idA, Set.empty)
    val parentsB = collectParents(idB, Set.empty)
    val common   = parentsA.intersect(parentsB)

    // Return the first common parent (could be improved to find the most specific)
    common.headOption.flatMap { name =>
      if (name == "scala.Any") Some(TypeRepr.AnyType)
      else if (name == "scala.AnyRef" || name == "java.lang.Object")
        Some(TypeRepr.Ref(TypeId.AnyRef))
      else None
    }
  }

  /**
   * Reduce a match type to its result, if possible. Returns None if the match
   * type cannot be reduced (e.g., abstract scrutinee).
   *
   * Match type reduction:
   *   1. Normalize the scrutinee
   *   2. Try each case in order:
   *      a. Check if scrutinee matches the pattern
   *      b. If so, extract bindings and substitute into result
   *   3. If no case matches and scrutinee is abstract, return None
   *   4. If no case matches and scrutinee is concrete, return the bound
   */
  @SuppressWarnings(Array("org.wartremover.warts.UnusedParameter"))
  def reduceMatchType(mt: TypeRepr.MatchType, ctx: Context = Context()): Option[TypeRepr] = {
    val _         = ctx // Suppress unused warning - ctx reserved for future recursive type handling
    val scrutinee = TypeNormalization.normalize(mt.scrutinee)

    // Check if scrutinee is abstract (contains type parameters or abstract types)
    def isAbstract(tpe: TypeRepr): Boolean = tpe match {
      case TypeRepr.ParamRef(_, _)           => true
      case TypeRepr.TypeSelect(_, _)         => true
      case TypeRepr.Ref(id) if id.isAbstract => true
      case TypeRepr.Applied(tc, args)        => isAbstract(tc) || args.exists(isAbstract)
      case _                                 => false
    }

    // Try to match scrutinee against a pattern and extract bindings
    def tryMatch(
      scrutinee: TypeRepr,
      pattern: TypeRepr,
      bindings: List[TypeParam]
    ): Option[Map[TypeParam, TypeRepr]] = {
      val bindingSet = bindings.toSet

      def matchRec(s: TypeRepr, p: TypeRepr): Option[Map[TypeParam, TypeRepr]] = (s, p) match {
        // Pattern variable: bind it
        case (_, TypeRepr.ParamRef(param, 0)) if bindingSet.contains(param) =>
          Some(Map(param -> s))

        // Same type references
        case (TypeRepr.Ref(idS), TypeRepr.Ref(idP)) if idS.fullName == idP.fullName =>
          Some(Map.empty)

        // Applied types must match constructor and args
        case (TypeRepr.Applied(tcS, argsS), TypeRepr.Applied(tcP, argsP)) if argsS.size == argsP.size =>
          for {
            tcMatch   <- matchRec(tcS, tcP)
            argsMatch <- argsS.zip(argsP).foldLeft(Option(Map.empty[TypeParam, TypeRepr])) {
                           case (None, _)                 => None
                           case (Some(acc), (argS, argP)) =>
                             matchRec(argS, argP).map(m => acc ++ m)
                         }
          } yield tcMatch ++ argsMatch

        // Type equality after normalization
        case _ if TypeEquality.areEqual(s, p) =>
          Some(Map.empty)

        case _ => None
      }

      matchRec(scrutinee, pattern)
    }

    // Try each case in order
    mt.cases.iterator.flatMap { matchCase =>
      tryMatch(scrutinee, matchCase.pattern, matchCase.bindings).map { bindings =>
        TypeRepr.substitute(matchCase.result, bindings)
      }
    }.nextOption() match {
      case Some(result) => Some(TypeNormalization.normalize(result))
      case None         =>
        // No case matched
        if (isAbstract(scrutinee)) None // Cannot reduce abstract scrutinee
        else Some(mt.bound)             // Return bound for unmatched concrete type
    }
  }
}
