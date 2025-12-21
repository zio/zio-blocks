package zio.blocks.schema

import scala.quoted._
import zio.blocks.schema.binding.StructuralValue

object DeriveToStructural {
  def derivedImpl[A: Type](using Quotes): Expr[ToStructural[A]] = {
    import quotes.reflect._

    val tpe    = TypeRepr.of[A]
    val symbol = tpe.typeSymbol

    // Validate that the type is a case class or sealed trait/class
    val isCaseType   = symbol.flags.is(Flags.Case)
    val isSealedType = symbol.flags.is(Flags.Sealed)

    if (!symbol.isClassDef || (!isCaseType && !isSealedType)) {
      report.errorAndAbort(
        s"ToStructural derivation requires a case class or sealed trait, found: ${tpe.show}"
      )
    }

    // Type categorization ADT - single source of truth for type classification
    enum TypeCategory {
      case PrimitiveLike
      case OptionType(element: TypeRepr)
      case ListType(element: TypeRepr)
      case VectorType(element: TypeRepr)
      case SeqType(element: TypeRepr)
      case SetType(element: TypeRepr)
      case MapType(key: TypeRepr, value: TypeRepr)
      case EitherType(left: TypeRepr, right: TypeRepr)
      case TupleType(elements: List[TypeRepr])
      case CaseClassType(fields: List[(Symbol, TypeRepr)])
      case SealedType(children: List[Symbol])
      case Unknown
    }

    // Single categorization function - replaces all the isX predicates
    def categorize(t: TypeRepr): TypeCategory = {
      val dealt = t.dealias

      // Check primitives first
      if (
        dealt =:= TypeRepr.of[Boolean] ||
        dealt =:= TypeRepr.of[Byte] ||
        dealt =:= TypeRepr.of[Short] ||
        dealt =:= TypeRepr.of[Int] ||
        dealt =:= TypeRepr.of[Long] ||
        dealt =:= TypeRepr.of[Float] ||
        dealt =:= TypeRepr.of[Double] ||
        dealt =:= TypeRepr.of[Char] ||
        dealt =:= TypeRepr.of[Unit] ||
        dealt =:= TypeRepr.of[String] ||
        dealt =:= TypeRepr.of[BigDecimal] ||
        dealt =:= TypeRepr.of[BigInt] ||
        dealt =:= TypeRepr.of[java.util.UUID] ||
        dealt =:= TypeRepr.of[java.util.Currency] ||
        dealt.typeSymbol.fullName.startsWith("java.time.")
      ) {
        return TypeCategory.PrimitiveLike
      }

      // Check containers and structural types
      if (dealt <:< TypeRepr.of[Option[?]]) {
        TypeCategory.OptionType(dealt.typeArgs.head)
      } else if (dealt <:< TypeRepr.of[List[?]]) {
        TypeCategory.ListType(dealt.typeArgs.head)
      } else if (dealt <:< TypeRepr.of[Vector[?]]) {
        TypeCategory.VectorType(dealt.typeArgs.head)
      } else if (dealt <:< TypeRepr.of[Seq[?]]) {
        TypeCategory.SeqType(dealt.typeArgs.head)
      } else if (dealt <:< TypeRepr.of[Set[?]]) {
        TypeCategory.SetType(dealt.typeArgs.head)
      } else if (dealt <:< TypeRepr.of[Map[?, ?]]) {
        TypeCategory.MapType(dealt.typeArgs(0), dealt.typeArgs(1))
      } else if (dealt <:< TypeRepr.of[Either[?, ?]]) {
        TypeCategory.EitherType(dealt.typeArgs(0), dealt.typeArgs(1))
      } else if (dealt.typeSymbol.fullName.startsWith("scala.Tuple")) {
        TypeCategory.TupleType(dealt.typeArgs)
      } else {
        val sym = dealt.typeSymbol
        if (sym.isClassDef && sym.flags.is(Flags.Case) && !sym.flags.is(Flags.Sealed)) {
          val fields = sym.caseFields.map(f => (f, dealt.memberType(f)))
          TypeCategory.CaseClassType(fields)
        } else if (
          sym.flags.is(Flags.Sealed) && (sym.flags.is(Flags.Trait) || sym.flags.is(Flags.Abstract) || sym.isClassDef)
        ) {
          TypeCategory.SealedType(sym.children)
        } else {
          TypeCategory.Unknown
        }
      }
    }

    // Recursion Fail message
    def failRecursion(loopType: TypeRepr, stack: List[TypeRepr]): Nothing = {
      // Extract the cycle: types from when we first saw loopType until now
      val cycle = stack.takeWhile(t => !(t =:= loopType)) :+ loopType

      // Find all structural types (case classes/sealed traits) in the cycle
      // Filter out containers (List, Option, etc.) as they're not the recursive types themselves
      val structuralTypesInCycle = cycle.filter { t =>
        categorize(t) match {
          case TypeCategory.CaseClassType(_) | TypeCategory.SealedType(_) => true
          case _                                                          => false
        }
      }
        .map(_.typeSymbol.name)
        .distinct

      // Multiple types in cycle = mutual recursion, single type = direct recursion
      if (structuralTypesInCycle.size > 1) {
        report.errorAndAbort(
          s"Cannot generate structural type: mutually recursive types detected: ${structuralTypesInCycle.mkString(", ")}. " +
            "Structural types cannot represent cyclic dependencies."
        )
      } else {
        report.errorAndAbort(
          s"Cannot generate structural type: recursive type detected: ${loopType.typeSymbol.name}. " +
            "Structural types cannot represent recursive structures."
        )
      }
    }

    def checkRecursive(t: TypeRepr, stack: List[TypeRepr]): Unit = {
      val dealt = t.dealias

      // If we've seen this type before in our traversal path, we have a cycle
      if (stack.exists(_ =:= dealt)) {
        failRecursion(dealt, stack)
      }

      // Traverse the type structure recursively using pattern matching
      categorize(dealt) match {
        case TypeCategory.PrimitiveLike =>
          // Base case: primitives don't contain other types
          ()

        case TypeCategory.Unknown =>
          // Base case: unknown types don't contain other types
          ()

        case TypeCategory.OptionType(elem) =>
          // Unary container: recurse into the element type
          checkRecursive(elem, dealt :: stack)

        case TypeCategory.ListType(elem) =>
          // Unary container: recurse into the element type
          checkRecursive(elem, dealt :: stack)

        case TypeCategory.VectorType(elem) =>
          // Unary container: recurse into the element type
          checkRecursive(elem, dealt :: stack)

        case TypeCategory.SeqType(elem) =>
          // Unary container: recurse into the element type
          checkRecursive(elem, dealt :: stack)

        case TypeCategory.SetType(elem) =>
          // Unary container: recurse into the element type
          checkRecursive(elem, dealt :: stack)

        case TypeCategory.MapType(key, value) =>
          // Binary container: recurse into both key and value types
          checkRecursive(key, dealt :: stack)
          checkRecursive(value, dealt :: stack)

        case TypeCategory.EitherType(left, right) =>
          // Binary container: recurse into both left and right types
          checkRecursive(left, dealt :: stack)
          checkRecursive(right, dealt :: stack)

        case TypeCategory.TupleType(elements) =>
          // Product type: recurse into all element types
          elements.foreach(elem => checkRecursive(elem, dealt :: stack))

        case TypeCategory.CaseClassType(fields) =>
          // Case class: recurse into all field types
          fields.foreach { case (_, fieldTpe) =>
            checkRecursive(fieldTpe, dealt :: stack)
          }

        case TypeCategory.SealedType(children) =>
          // Sealed trait: recurse into all children (case classes/objects)
          children.foreach { childSym =>
            if (childSym.isClassDef) {
              val childTpe = if (childSym.flags.is(Flags.Module)) {
                Ref(childSym).tpe
              } else {
                TypeIdent(childSym).tpe
              }
              checkRecursive(childTpe, dealt :: stack)
            }
            // Case objects are leaves (no fields to recurse into)
          }
      }
    }

    // Recursion Validation
    checkRecursive(tpe, Nil)

    // Transforms a nominal type into its structural representation.
    def transformType(t: TypeRepr, seen: Set[TypeRepr]): TypeRepr = {
      val dealt = t.dealias

      // If we're already processing this type, return it as-is to prevent infinite loops
      if (seen.exists(_ =:= dealt)) {
        return dealt
      }

      categorize(dealt) match {
        case TypeCategory.PrimitiveLike | TypeCategory.Unknown =>
          // Primitives and unknown types pass through unchanged
          dealt

        case TypeCategory.OptionType(elem) =>
          // Option[T] → Option[structural(T)]
          val inner = transformType(elem, seen + dealt)
          TypeRepr.of[Option].appliedTo(inner)

        case TypeCategory.ListType(elem) =>
          // List[T] → List[structural(T)]
          val inner = transformType(elem, seen + dealt)
          TypeRepr.of[List].appliedTo(inner)

        case TypeCategory.VectorType(elem) =>
          // Vector[T] → Vector[structural(T)]
          val inner = transformType(elem, seen + dealt)
          TypeRepr.of[Vector].appliedTo(inner)

        case TypeCategory.SeqType(elem) =>
          // Seq[T] → Seq[structural(T)]
          val inner = transformType(elem, seen + dealt)
          TypeRepr.of[Seq].appliedTo(inner)

        case TypeCategory.SetType(elem) =>
          // Set[T] → Set[structural(T)]
          val inner = transformType(elem, seen + dealt)
          TypeRepr.of[Set].appliedTo(inner)

        case TypeCategory.MapType(key, value) =>
          // Map[K, V] → Map[structural(K), structural(V)]
          val k = transformType(key, seen + dealt)
          val v = transformType(value, seen + dealt)
          TypeRepr.of[Map].appliedTo(List(k, v))

        case TypeCategory.EitherType(left, right) =>
          // Either[L, R] → Either[structural(L), structural(R)]
          val l = transformType(left, seen + dealt)
          val r = transformType(right, seen + dealt)
          TypeRepr.of[Either].appliedTo(List(l, r))

        case TypeCategory.TupleType(elements) =>
          // (T1, T2, T3) → Selectable { def _1: structural(T1); def _2: structural(T2); def _3: structural(T3) }
          val args = elements.zipWithIndex.map { case (arg, idx) =>
            (s"_${idx + 1}", transformType(arg, seen + dealt))
          }
          // Build refinement type by folding over fields
          val base = TypeRepr.of[Selectable]
          args.foldLeft(base) { case (parent, (name, fTpe)) =>
            Refinement(parent, name, fTpe)
          }

        case TypeCategory.CaseClassType(fields) =>
          // case class Person(name: String, age: Int)
          //   → Selectable { def name: String; def age: Int }
          val transformedFields = fields.map { case (fieldSym, fieldTpe) =>
            (fieldSym.name, transformType(fieldTpe, seen + dealt))
          }
          // Build refinement type by folding over fields
          val base = TypeRepr.of[Selectable]
          transformedFields.foldLeft(base) { case (parent, (name, fTpe)) =>
            Refinement(parent, name, fTpe)
          }

        case TypeCategory.SealedType(children) =>
          // sealed trait Result with children Success(value: Int) and Failure(error: String)
          //   → { def value: Int } | { def error: String }
          val childTypes = children.map { childSym =>
            if (childSym.isTerm) {
              // Case object → empty structural type {}
              TypeRepr.of[Selectable]
            } else {
              // Case class child - handle type parameters if present
              val cType      = TypeIdent(childSym).tpe
              val typeParams =
                cType.typeSymbol.primaryConstructor.paramSymss.headOption.map(_.filter(_.isTypeParam)).getOrElse(Nil)
              // Apply parent's type arguments if child has matching type parameters
              if (typeParams.size == dealt.typeArgs.size && dealt.typeArgs.nonEmpty) {
                cType.appliedTo(dealt.typeArgs)
              } else {
                cType
              }
            }
          }

          if (childTypes.isEmpty) {
            TypeRepr.of[Nothing]
          } else {
            // Transform each child to structural and combine with OrType (union)
            val structuralChildren = childTypes.map(ct => transformType(ct, seen + dealt))
            structuralChildren.reduce((a, b) => OrType(a, b))
          }
      }
    }

    // Compute the structural type for the root type A
    val structuralTypeRepr = transformType(tpe, Set.empty)

    // Generates runtime code to transform a value from nominal to structural form.
    def transformExpr(expr: Expr[Any], tpe: Type[_])(using q: Quotes): Expr[Any] = {
      import q.reflect._
      val dealt = TypeRepr.of(using tpe).dealias

      // Local type categorization for transformExpr's Quotes instance
      enum LocalCategory {
        case PrimitiveLike
        case OptionType
        case ListType
        case VectorType
        case SeqType
        case SetType
        case MapType
        case EitherType
        case TupleType
        case CaseClassType
        case SealedType
        case Unknown
      }

      def categorizeLocal(t: TypeRepr): LocalCategory = {
        // Check primitives first
        if (
          t =:= TypeRepr.of[Boolean] ||
          t =:= TypeRepr.of[Byte] ||
          t =:= TypeRepr.of[Short] ||
          t =:= TypeRepr.of[Int] ||
          t =:= TypeRepr.of[Long] ||
          t =:= TypeRepr.of[Float] ||
          t =:= TypeRepr.of[Double] ||
          t =:= TypeRepr.of[Char] ||
          t =:= TypeRepr.of[Unit] ||
          t =:= TypeRepr.of[String] ||
          t =:= TypeRepr.of[BigDecimal] ||
          t =:= TypeRepr.of[BigInt] ||
          t =:= TypeRepr.of[java.util.UUID] ||
          t =:= TypeRepr.of[java.util.Currency] ||
          t.typeSymbol.fullName.startsWith("java.time.")
        ) LocalCategory.PrimitiveLike
        else if (t <:< TypeRepr.of[Option[?]]) LocalCategory.OptionType
        else if (t <:< TypeRepr.of[List[?]]) LocalCategory.ListType
        else if (t <:< TypeRepr.of[Vector[?]]) LocalCategory.VectorType
        else if (t <:< TypeRepr.of[Seq[?]]) LocalCategory.SeqType
        else if (t <:< TypeRepr.of[Set[?]]) LocalCategory.SetType
        else if (t <:< TypeRepr.of[Map[?, ?]]) LocalCategory.MapType
        else if (t <:< TypeRepr.of[Either[?, ?]]) LocalCategory.EitherType
        else if (t.typeSymbol.fullName.startsWith("scala.Tuple")) LocalCategory.TupleType
        else if (t.typeSymbol.isClassDef && t.typeSymbol.flags.is(Flags.Case) && !t.typeSymbol.flags.is(Flags.Sealed))
          LocalCategory.CaseClassType
        else if (t.typeSymbol.flags.is(Flags.Sealed)) LocalCategory.SealedType
        else LocalCategory.Unknown
      }

      categorizeLocal(dealt) match {
        case LocalCategory.PrimitiveLike | LocalCategory.Unknown =>
          // Primitives and unknown types pass through unchanged
          expr

        case LocalCategory.OptionType =>
          // Generate: optionValue.map(v => transformExpr(v))
          val innerTpe = dealt.typeArgs.head
          tpe match {
            case '[t] =>
              val optExpr = expr.asExprOf[Option[Any]]
              innerTpe.asType match {
                case '[i] =>
                  '{ $optExpr.map(v => ${ transformExpr('v, Type.of[i]) }) }
              }
          }

        case LocalCategory.ListType =>
          // Generate: list.map(v => transformExpr(v)).toList
          val innerTpe = dealt.typeArgs.head
          val colExpr  = expr.asExprOf[Iterable[Any]]
          innerTpe.asType match {
            case '[i] =>
              val mapped = '{ $colExpr.map(v => ${ transformExpr('v, Type.of[i]) }) }
              '{ $mapped.toList }
          }

        case LocalCategory.VectorType =>
          // Generate: vector.map(v => transformExpr(v)).toVector
          val innerTpe = dealt.typeArgs.head
          val colExpr  = expr.asExprOf[Iterable[Any]]
          innerTpe.asType match {
            case '[i] =>
              val mapped = '{ $colExpr.map(v => ${ transformExpr('v, Type.of[i]) }) }
              '{ $mapped.toVector }
          }

        case LocalCategory.SeqType =>
          // Generate: seq.map(v => transformExpr(v)).toSeq
          val innerTpe = dealt.typeArgs.head
          val colExpr  = expr.asExprOf[Iterable[Any]]
          innerTpe.asType match {
            case '[i] =>
              val mapped = '{ $colExpr.map(v => ${ transformExpr('v, Type.of[i]) }) }
              '{ $mapped.toSeq }
          }

        case LocalCategory.SetType =>
          // Generate: set.map(v => transformExpr(v)).toSet
          val innerTpe = dealt.typeArgs.head
          val colExpr  = expr.asExprOf[Iterable[Any]]
          innerTpe.asType match {
            case '[i] =>
              val mapped = '{ $colExpr.map(v => ${ transformExpr('v, Type.of[i]) }) }
              '{ $mapped.toSet }
          }

        case LocalCategory.MapType =>
          // Generate: map.map { case (k, v) => (transformExpr(k), transformExpr(v)) }
          val kTpe = dealt.typeArgs(0)
          val vTpe = dealt.typeArgs(1)
          (kTpe.asType, vTpe.asType) match {
            case ('[k], '[v]) =>
              val mapExpr = expr.asExprOf[Map[k, v]]
              '{
                $mapExpr.map { case (key, value) =>
                  (${ transformExpr('key, Type.of[k]) }, ${ transformExpr('value, Type.of[v]) })
                }
              }
            case _ => report.errorAndAbort("Unexpected type structure for Map")
          }

        case LocalCategory.EitherType =>
          // Generate: either match { case Left(v) => Left(transformExpr(v)); case Right(v) => Right(transformExpr(v)) }
          val lTpe       = dealt.typeArgs(0)
          val rTpe       = dealt.typeArgs(1)
          val eitherExpr = expr.asExprOf[Either[Any, Any]]
          (lTpe.asType, rTpe.asType) match {
            case ('[l], '[r]) =>
              '{
                $eitherExpr match {
                  case Left(v)  => Left(${ transformExpr('v, Type.of[l]) })
                  case Right(v) => Right(${ transformExpr('v, Type.of[r]) })
                }
              }
            case _ => report.errorAndAbort("Unexpected type structure for Either")
          }

        case LocalCategory.TupleType =>
          // Generate: new StructuralValue(Map("_1" -> tuple._1, "_2" -> tuple._2, ...))
          val args      = dealt.typeArgs.zipWithIndex
          val tupleExpr = expr.asTerm

          val entries = args.map { case (argTpe, idx) =>
            val name     = s"_${idx + 1}"
            val fieldVal = Select.unique(tupleExpr, name).asExpr
            argTpe.asType match {
              case '[a] =>
                val converted = transformExpr(fieldVal, Type.of[a])
                '{ ${ Expr(name) } -> $converted }
            }
          }

          '{ new StructuralValue(Map(${ Varargs(entries) }*)) }

        case LocalCategory.CaseClassType =>
          // Nested case class: Delegate to its ToStructural instance
          tpe match {
            case '[d] =>
              Expr.summon[ToStructural[d]] match {
                case Some(ts) => '{ $ts.toStructural($expr.asInstanceOf[d]) }
                case None     =>
                  '{ ToStructural.derived[d].toStructural($expr.asInstanceOf[d]) }
              }
          }

        case LocalCategory.SealedType =>
          // Generate: value match { case child1: Type1 => ...; case child2: Type2 => ... }
          val children = dealt.typeSymbol.children
          if (children.isEmpty) {
            expr
          } else {
            val term = expr.asTerm

            val cases = children.map { childSym =>
              if (childSym.isTerm) {
                // Case object → StructuralValue(Map.empty)
                val pattern = Ref(childSym)
                val body    = '{ new StructuralValue(Map.empty) }.asTerm
                CaseDef(pattern, None, body)
              } else {
                // Case class child → recursively transform it
                val cType = TypeIdent(childSym).tpe
                // Handle type parameters if present
                val typeParams =
                  cType.typeSymbol.primaryConstructor.paramSymss.headOption.map(_.filter(_.isTypeParam)).getOrElse(Nil)
                val childTpe =
                  if (typeParams.size == dealt.typeArgs.size && dealt.typeArgs.nonEmpty) {
                    cType.appliedTo(dealt.typeArgs)
                  } else {
                    cType
                  }

                childTpe.asType match {
                  case '[c] =>
                    val bindName   = "x"
                    val bindSymbol = Symbol.newBind(Symbol.spliceOwner, bindName, Flags.EmptyFlags, childTpe)
                    val pattern    = Bind(bindSymbol, Typed(Wildcard(), TypeTree.of[c]))

                    val body = childTpe.asType match {
                      case '[ct] =>
                        val ref = Ref(bindSymbol).asExprOf[ct]
                        transformExpr(ref, Type.of[ct]).asTerm
                    }

                    CaseDef(pattern, None, body)
                }
              }
            }

            Match(term, cases).asExpr
          }
      }
    }

    // Generate the ToStructural instance
    structuralTypeRepr.asType match {
      case '[st] =>
        '{
          new ToStructural[A] {
            type StructuralType = st

            def toStructural(value: A): StructuralType =
              ${
                if (symbol.fullName.startsWith("scala.Tuple")) {
                  // Tuples are handled via transformExpr,due to special field naming (_1, _2)
                  transformExpr('value, Type.of[A])
                } else if (symbol.flags.is(Flags.Case)) {
                  // Root case class: Manually deconstruct to avoid infinite recursion
                  val fields  = symbol.caseFields
                  val entries = fields.map { f =>
                    val name     = f.name
                    val fieldVal = Select('value.asTerm, f).asExpr
                    val fTpe     = tpe.memberType(f)
                    fTpe.asType match {
                      case '[ft] =>
                        // transformExpr will handle nested types by delegating to their ToStructural
                        val converted = transformExpr(fieldVal, Type.of[ft])
                        '{ ${ Expr(name) } -> $converted }
                    }
                  }
                  '{ new StructuralValue(Map(${ Varargs(entries) }*)) }
                } else {
                  // Root sealed trait: Use transformExpr to generate pattern match
                  transformExpr('value, Type.of[A])
                }
              }.asInstanceOf[StructuralType]

            def structuralSchema(implicit schema: Schema[A]): Schema[StructuralType] =
              Schema.derived[st]
          }
        }
    }
  }
}
