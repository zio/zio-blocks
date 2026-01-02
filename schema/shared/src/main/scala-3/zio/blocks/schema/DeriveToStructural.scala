package zio.blocks.schema

import scala.quoted._

object DeriveToStructural {
  def derivedImpl[A: Type](using Quotes): Expr[ToStructural[A]] = {
    import quotes.reflect._

    val tpe    = TypeRepr.of[A]
    val symbol = tpe.typeSymbol

    // Entry validation: only case classes and sealed traits are supported
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

    // Helper: Check if type is an opaque type
    def isOpaque(tpe: TypeRepr): Boolean = tpe.typeSymbol.flags.is(Flags.Opaque)

    // Helper: Dealias opaque type to its underlying type using translucentSuperType
    def opaqueDealias(tpe: TypeRepr): TypeRepr = {
      import scala.annotation.tailrec
      @tailrec
      def loop(t: TypeRepr): TypeRepr = t match {
        case tr: TypeRef if tr.isOpaqueAlias => loop(tr.translucentSuperType.dealias)
        case AppliedType(tycon, _)           => loop(tycon.dealias)
        case TypeLambda(_, _, body)          => loop(body.dealias)
        case _                               => t
      }
      loop(tpe)
    }

    // Helper: Dealias type, handling opaque types specially
    def dealiasAll(tpe: TypeRepr): TypeRepr = {
      val dealt = tpe.dealias
      if (isOpaque(dealt)) opaqueDealias(dealt)
      else dealt
    }

    // Single categorization function
    def categorize(t: TypeRepr): TypeCategory = {
      // First dealias, then check for opaque and dealias that too
      val dealt = dealiasAll(t)

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
      val cycle = stack.takeWhile(t => !(t =:= loopType)) :+ loopType

      val structuralTypesInCycle = cycle.filter { t =>
        categorize(t) match {
          case TypeCategory.CaseClassType(_) | TypeCategory.SealedType(_) => true
          case _                                                          => false
        }
      }
        .map(_.typeSymbol.name)
        .distinct

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
      val dealt = dealiasAll(t)

      if (stack.exists(_ =:= dealt)) {
        failRecursion(dealt, stack)
      }

      categorize(dealt) match {
        case TypeCategory.PrimitiveLike => ()

        case TypeCategory.Unknown =>
          report.errorAndAbort(
            s"""Cannot generate structural type for unsupported type: ${dealt.typeSymbol.name}.
               |ToStructural only supports:
               |  - Primitive types (Int, String, Boolean, etc.)
               |  - Case classes
               |  - Sealed traits and enums
               |  - Either types
               |  - Collections (List, Vector, Set, Seq, Option, Map)
               |  - Tuples
               |The type '${dealt.typeSymbol.fullName}' is not supported.
               |If this is a regular class, consider converting it to a case class.""".stripMargin
          )

        case TypeCategory.OptionType(elem) =>
          checkRecursive(elem, dealt :: stack)

        case TypeCategory.ListType(elem) =>
          checkRecursive(elem, dealt :: stack)

        case TypeCategory.VectorType(elem) =>
          checkRecursive(elem, dealt :: stack)

        case TypeCategory.SeqType(elem) =>
          checkRecursive(elem, dealt :: stack)

        case TypeCategory.SetType(elem) =>
          checkRecursive(elem, dealt :: stack)

        case TypeCategory.MapType(key, value) =>
          checkRecursive(key, dealt :: stack)
          checkRecursive(value, dealt :: stack)

        case TypeCategory.EitherType(left, right) =>
          checkRecursive(left, dealt :: stack)
          checkRecursive(right, dealt :: stack)

        case TypeCategory.TupleType(elements) =>
          elements.foreach(elem => checkRecursive(elem, dealt :: stack))

        case TypeCategory.CaseClassType(fields) =>
          fields.foreach { case (_, fieldTpe) =>
            checkRecursive(fieldTpe, dealt :: stack)
          }

        case TypeCategory.SealedType(children) =>
          children.foreach { childSym =>
            if (childSym.isClassDef) {
              val childTpe = if (childSym.flags.is(Flags.Module)) {
                Ref(childSym).tpe
              } else {
                TypeIdent(childSym).tpe
              }
              checkRecursive(childTpe, dealt :: stack)
            }
          }
      }
    }

    // Validate no recursive types before proceeding
    checkRecursive(tpe, Nil)

    def buildStructuralType(t: TypeRepr, seen: Set[TypeRepr]): TypeRepr = {
      val dealt = dealiasAll(t)

      if (seen.exists(_ =:= dealt)) {
        return dealt
      }

      categorize(dealt) match {
        case TypeCategory.PrimitiveLike | TypeCategory.Unknown =>
          dealt

        case TypeCategory.OptionType(elem) =>
          val inner = buildStructuralType(elem, seen + dealt)
          TypeRepr.of[Option].appliedTo(inner)

        case TypeCategory.ListType(elem) =>
          val inner = buildStructuralType(elem, seen + dealt)
          TypeRepr.of[List].appliedTo(inner)

        case TypeCategory.VectorType(elem) =>
          val inner = buildStructuralType(elem, seen + dealt)
          TypeRepr.of[Vector].appliedTo(inner)

        case TypeCategory.SeqType(elem) =>
          val inner = buildStructuralType(elem, seen + dealt)
          TypeRepr.of[Seq].appliedTo(inner)

        case TypeCategory.SetType(elem) =>
          val inner = buildStructuralType(elem, seen + dealt)
          TypeRepr.of[Set].appliedTo(inner)

        case TypeCategory.MapType(key, value) =>
          val k = buildStructuralType(key, seen + dealt)
          val v = buildStructuralType(value, seen + dealt)
          TypeRepr.of[Map].appliedTo(List(k, v))

        case TypeCategory.EitherType(left, right) =>
          // Either → union type: { Tag = "Left"; value: L } | { Tag = "Right"; value: R }
          val leftStructural = {
            val base      = TypeRepr.of[StructuralRecord]
            val withValue = Refinement(base, "value", buildStructuralType(left, seen + dealt))
            val tagType   = ConstantType(StringConstant("Left"))
            Refinement(withValue, "Tag", TypeBounds(tagType, tagType))
          }
          val rightStructural = {
            val base      = TypeRepr.of[StructuralRecord]
            val withValue = Refinement(base, "value", buildStructuralType(right, seen + dealt))
            val tagType   = ConstantType(StringConstant("Right"))
            Refinement(withValue, "Tag", TypeBounds(tagType, tagType))
          }
          OrType(leftStructural, rightStructural)

        case TypeCategory.TupleType(elements) =>
          // Tuple → structural: { def _1: T1; def _2: T2; ... }
          val args = elements.zipWithIndex.map { case (arg, idx) =>
            (s"_${idx + 1}", buildStructuralType(arg, seen + dealt))
          }
          // Use StructuralRecord as base so selectDynamic is visible
          val base = TypeRepr.of[StructuralRecord]
          args.foldLeft(base) { case (parent, (name, fTpe)) =>
            Refinement(parent, name, fTpe)
          }

        case TypeCategory.CaseClassType(fields) =>
          // case class → structural: { def field1: T1; def field2: T2; ... }
          val transformedFields = fields.map { case (fieldSym, fieldTpe) =>
            (fieldSym.name, buildStructuralType(fieldTpe, seen + dealt))
          }
          // Use StructuralRecord as base so selectDynamic is visible
          val base = TypeRepr.of[StructuralRecord]
          transformedFields.foldLeft(base) { case (parent, (name, fTpe)) =>
            Refinement(parent, name, fTpe)
          }

        case TypeCategory.SealedType(children) =>
          // sealed trait → union of structural types with Tag

          val childStructuralTypes = children.map { childSym =>
            // STEP 1: Get the nominal type of this child case
            val childType = if (childSym.isTerm) {
              // Case object like "Cat" → no data fields, just needs Tag
              TypeRepr.of[StructuralRecord]
            } else {
              // Case class like "Dog(name: String)" → needs actual type with fields
              val cType      = TypeIdent(childSym).tpe
              val typeParams =
                cType.typeSymbol.primaryConstructor.paramSymss.headOption.map(_.filter(_.isTypeParam)).getOrElse(Nil)
              // If parent is sealed trait Option[T], child is Some[T] → apply T to Some
              if (typeParams.size == dealt.typeArgs.size && dealt.typeArgs.nonEmpty) {
                cType.appliedTo(dealt.typeArgs)
              } else {
                cType
              }
            }

            // STEP 2: Recursively convert child to structural type
            val structType = buildStructuralType(childType, seen + dealt)

            // STEP 3: Add Tag field to discriminate between cases
            val tagName = childSym.name
            val tagType = ConstantType(StringConstant(tagName))
            Refinement(structType, "Tag", TypeBounds(tagType, tagType))
          }

          // STEP 4: Combine all child structural types into a union type
          if (childStructuralTypes.isEmpty) {
            TypeRepr.of[Nothing]
          } else {
            childStructuralTypes.reduce((a, b) => OrType(a, b))
          }
      }
    }

    // Compute the structural type signature for the root type A
    val structuralTypeRepr = buildStructuralType(tpe, Set.empty)

    def buildValueConverter(fieldTpe: TypeRepr): Expr[Any => Any] = {
      categorize(fieldTpe) match {
        case TypeCategory.PrimitiveLike | TypeCategory.Unknown =>
          // Primitives pass through unchanged: Int stays Int, String stays String
          '{ (x: Any) => x }

        case TypeCategory.CaseClassType(fields) =>
          fieldTpe.asType match {
            case '[ft] =>
              // Build converters for each field recursively (handles nested case classes)
              val fieldConverters: List[(String, Expr[Any => Any])] = fields.map { case (fieldSym, nestedFieldTpe) =>
                (fieldSym.name, buildValueConverter(nestedFieldTpe))
              }
              // Generate runtime code: extract each field, convert it, build Map
              '{ (x: Any) =>
                val v                     = x.asInstanceOf[ft]
                val map: Map[String, Any] = Map(
                  ${
                    val entries = fieldConverters.map { case (name, converter) =>
                      // v.name → "Alice", then apply converter (in case nested)
                      val accessor = Select.unique('v.asTerm, name).asExpr
                      '{ ${ Expr(name) } -> $converter($accessor.asInstanceOf[Any]) }
                    }
                    Expr.ofList(entries)
                  }*
                )
                new StructuralRecord(map)
              }
          }

        case TypeCategory.OptionType(elem) =>
          val elemConverter = buildValueConverter(elem)
          '{ (x: Any) => x.asInstanceOf[Option[Any]].map($elemConverter) }

        case TypeCategory.ListType(elem) =>
          // COLLECTIONS: Keep the collection structure, convert elements
          val elemConverter = buildValueConverter(elem)
          '{ (x: Any) => x.asInstanceOf[List[Any]].map($elemConverter) }

        case TypeCategory.VectorType(elem) =>
          val elemConverter = buildValueConverter(elem)
          '{ (x: Any) => x.asInstanceOf[Vector[Any]].map($elemConverter) }

        case TypeCategory.SeqType(elem) =>
          val elemConverter = buildValueConverter(elem)
          '{ (x: Any) => x.asInstanceOf[Seq[Any]].map($elemConverter) }

        case TypeCategory.SetType(elem) =>
          val elemConverter = buildValueConverter(elem)
          '{ (x: Any) => x.asInstanceOf[Set[Any]].map($elemConverter) }

        case TypeCategory.MapType(key, value) =>
          // MAP: Convert both keys and values
          val keyConverter = buildValueConverter(key)
          val valConverter = buildValueConverter(value)
          '{ (x: Any) => x.asInstanceOf[Map[Any, Any]].map { case (k, v) => ($keyConverter(k), $valConverter(v)) } }

        case TypeCategory.EitherType(left, right) =>
          // EITHER: Pattern match and tag with "Left" or "Right"
          val leftConverter  = buildValueConverter(left)
          val rightConverter = buildValueConverter(right)
          '{ (x: Any) =>
            x.asInstanceOf[Either[Any, Any]] match {
              case Left(l) =>
                new StructuralRecord(Map("Tag" -> "Left", "value" -> $leftConverter(l)))
              case Right(r) =>
                new StructuralRecord(Map("Tag" -> "Right", "value" -> $rightConverter(r)))
            }
          }

        case TypeCategory.TupleType(elements) =>
          // TUPLE: Convert to StructuralRecord with numbered fields
          fieldTpe.asType match {
            case '[ft] =>
              val fieldConverters: List[(String, Expr[Any => Any])] = elements.zipWithIndex.map { case (elemTpe, idx) =>
                (s"_${idx + 1}", buildValueConverter(elemTpe))
              }
              '{ (x: Any) =>
                val v                     = x.asInstanceOf[ft]
                val map: Map[String, Any] = Map(
                  ${
                    val entries = fieldConverters.map { case (name, converter) =>
                      // Access tuple._1, tuple._2, etc.
                      val accessor = Select.unique('v.asTerm, name).asExpr
                      '{ ${ Expr(name) } -> $converter($accessor.asInstanceOf[Any]) }
                    }
                    Expr.ofList(entries)
                  }*
                )
                new StructuralRecord(map)
              }
          }

        case TypeCategory.SealedType(children) =>
          val childConvertersWithTypes: List[(TypeRepr, Expr[Any => Any])] = children.map { childSym =>
            // STEP 1: Get the type of this child (case class or case object)
            val childTpe = if (childSym.isClassDef) {
              TypeIdent(childSym).tpe
            } else {
              // Term symbol (case object, simple enum case)
              Ref(childSym).tpe
            }

            // STEP 2: Build a converter for this specific child
            val converter = categorize(childTpe) match {
              case TypeCategory.CaseClassType(fields) =>
                // Case class with data fields: Dog(name: String)
                // Generate: extract all fields + add Tag
                childTpe.asType match {
                  case '[ct] =>
                    val fieldConverters = fields.map { case (fieldSym, nestedFieldTpe) =>
                      (fieldSym.name, buildValueConverter(nestedFieldTpe))
                    }
                    val tagName = Expr(childSym.name)
                    '{ (x: Any) =>
                      val v                          = x.asInstanceOf[ct]
                      val fieldMap: Map[String, Any] = Map(
                        ${
                          val entries = fieldConverters.map { case (name, conv) =>
                            val accessor = Select.unique('v.asTerm, name).asExpr
                            '{ ${ Expr(name) } -> $conv($accessor.asInstanceOf[Any]) }
                          }
                          Expr.ofList(entries)
                        }*
                      )
                      // Add Tag field to discriminate between cases
                      new StructuralRecord(fieldMap + ("Tag" -> $tagName))
                    }
                }
              case _ =>
                // Case object with no fields: Cat
                // Generate: just create record with Tag
                val tagName = Expr(childSym.name)
                '{ (_: Any) => new StructuralRecord(Map("Tag" -> $tagName)) }
            }
            (childTpe, converter)
          }

          // STEP 3: Generate runtime pattern matching code
          '{ (x: Any) =>
            ${
              // Build a chain of if-else checks using isInstanceOf
              childConvertersWithTypes.foldRight('{ throw new MatchError(x) }: Expr[Any]) {
                case ((childTpe, converter), elseExpr) =>
                  childTpe.asType match {
                    case '[ct] =>
                      '{ if (x.isInstanceOf[ct]) $converter(x) else $elseExpr }
                  }
              }
            }
          }
      }
    }

    // Generate the ToStructural[A] implementation
    if (isCaseType && !isSealedType) {
      val fields = symbol.caseFields

      structuralTypeRepr.asType match {
        case '[st] =>
          // Generate converters for each field
          val fieldConverters: List[(String, Expr[Any => Any])] = fields.map { f =>
            val name     = f.name
            val fieldTpe = tpe.memberType(f)
            (name, buildValueConverter(fieldTpe))
          }

          '{
            new ToStructural[A] {
              type StructuralType = st

              def toStructural(value: A): StructuralType = {
                val map: Map[String, Any] = Map(
                  ${
                    val entries: List[Expr[(String, Any)]] = fieldConverters.map { case (name, converter) =>
                      val accessor = Select.unique('value.asTerm, name).asExpr
                      '{ ${ Expr(name) } -> $converter($accessor.asInstanceOf[Any]) }
                    }
                    Expr.ofList(entries)
                  }*
                )
                new StructuralRecord(map).asInstanceOf[StructuralType]
              }

              def structuralSchema(implicit schema: Schema[A]): Schema[StructuralType] = {
                val structuralReflect = StructuralSchemaTransformer.transform(schema.reflect)
                new Schema[StructuralType](structuralReflect.asInstanceOf[Reflect.Bound[StructuralType]])
              }
            }
          }
      }
    } else {
      // Sealed Trait/Enum Implementation
      val children = symbol.children

      // Complex sealed trait with case class children
      structuralTypeRepr.asType match {
        case '[st] =>
          // Helper to get type for a child symbol (handles both class and term symbols)
          def getChildType(childSym: Symbol): TypeRepr =
            if (childSym.isClassDef) {
              TypeIdent(childSym).tpe
            } else {
              // Term symbol (case object, simple enum case)
              Ref(childSym).tpe
            }

          // Generate converters for each child type
          val childConverters: List[(Symbol, Expr[Any => Any])] = children.map { childSym =>
            val childTpe = getChildType(childSym)

            val converter = categorize(childTpe) match {
              case TypeCategory.CaseClassType(fields) if fields.nonEmpty =>
                // Case class child: extract fields and add Tag
                childTpe.asType match {
                  case '[ct] =>
                    val fieldConverters = fields.map { case (fieldSym, nestedFieldTpe) =>
                      (fieldSym.name, buildValueConverter(nestedFieldTpe))
                    }
                    val tagName = Expr(childSym.name)
                    '{ (x: Any) =>
                      val v                          = x.asInstanceOf[ct]
                      val fieldMap: Map[String, Any] = Map(
                        ${
                          val entries = fieldConverters.map { case (name, conv) =>
                            val accessor = Select.unique('v.asTerm, name).asExpr
                            '{ ${ Expr(name) } -> $conv($accessor.asInstanceOf[Any]) }
                          }
                          Expr.ofList(entries)
                        }*
                      )
                      new StructuralRecord(fieldMap + ("Tag" -> $tagName))
                    }
                }
              case _ =>
                // Case object or other: just Tag, no fields
                val tagName = Expr(childSym.name)
                '{ (_: Any) => new StructuralRecord(Map("Tag" -> $tagName)) }
            }
            (childSym, converter)
          }

          '{
            new ToStructural[A] {
              type StructuralType = st

              def toStructural(value: A): StructuralType = {
                val result: Any = ${
                  // Build a chain of if-else checks using isInstanceOf
                  childConverters.foldRight('{ throw new MatchError(value) }: Expr[Any]) {
                    case ((childSym, converter), elseExpr) =>
                      val childTpe = getChildType(childSym)
                      childTpe.asType match {
                        case '[ct] =>
                          '{ if (value.isInstanceOf[ct]) $converter(value) else $elseExpr }
                      }
                  }
                }
                result.asInstanceOf[StructuralType]
              }

              def structuralSchema(implicit schema: Schema[A]): Schema[StructuralType] = {
                val structuralReflect = StructuralSchemaTransformer.transform(schema.reflect)
                new Schema[StructuralType](structuralReflect.asInstanceOf[Reflect.Bound[StructuralType]])
              }
            }
          }
      }
    }
  }
}
