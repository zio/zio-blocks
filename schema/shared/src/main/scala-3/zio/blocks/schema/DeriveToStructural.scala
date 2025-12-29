package zio.blocks.schema

import scala.quoted._
import zio.blocks.schema.binding._
import zio.blocks.schema.binding.RegisterOffset._

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

    // Recursion Validation
    checkRecursive(tpe, Nil)

    // Transforms a nominal type into its structural representation (type-level only).
    // Opaque types are unwrapped to their underlying type.
    def transformType(t: TypeRepr, seen: Set[TypeRepr]): TypeRepr = {
      val dealt = dealiasAll(t)

      if (seen.exists(_ =:= dealt)) {
        return dealt
      }

      categorize(dealt) match {
        case TypeCategory.PrimitiveLike | TypeCategory.Unknown =>
          dealt

        case TypeCategory.OptionType(elem) =>
          val inner = transformType(elem, seen + dealt)
          TypeRepr.of[Option].appliedTo(inner)

        case TypeCategory.ListType(elem) =>
          val inner = transformType(elem, seen + dealt)
          TypeRepr.of[List].appliedTo(inner)

        case TypeCategory.VectorType(elem) =>
          val inner = transformType(elem, seen + dealt)
          TypeRepr.of[Vector].appliedTo(inner)

        case TypeCategory.SeqType(elem) =>
          val inner = transformType(elem, seen + dealt)
          TypeRepr.of[Seq].appliedTo(inner)

        case TypeCategory.SetType(elem) =>
          val inner = transformType(elem, seen + dealt)
          TypeRepr.of[Set].appliedTo(inner)

        case TypeCategory.MapType(key, value) =>
          val k = transformType(key, seen + dealt)
          val v = transformType(value, seen + dealt)
          TypeRepr.of[Map].appliedTo(List(k, v))

        case TypeCategory.EitherType(left, right) =>
          // Either → union type: { Tag = "Left"; value: L } | { Tag = "Right"; value: R }
          val leftStructural = {
            val base      = TypeRepr.of[StructuralRecord]
            val withValue = Refinement(base, "value", transformType(left, seen + dealt))
            val tagType   = ConstantType(StringConstant("Left"))
            Refinement(withValue, "Tag", TypeBounds(tagType, tagType))
          }
          val rightStructural = {
            val base      = TypeRepr.of[StructuralRecord]
            val withValue = Refinement(base, "value", transformType(right, seen + dealt))
            val tagType   = ConstantType(StringConstant("Right"))
            Refinement(withValue, "Tag", TypeBounds(tagType, tagType))
          }
          OrType(leftStructural, rightStructural)

        case TypeCategory.TupleType(elements) =>
          // Tuple → structural: { def _1: T1; def _2: T2; ... }
          val args = elements.zipWithIndex.map { case (arg, idx) =>
            (s"_${idx + 1}", transformType(arg, seen + dealt))
          }
          // Use StructuralRecord as base so selectDynamic is visible
          val base = TypeRepr.of[StructuralRecord]
          args.foldLeft(base) { case (parent, (name, fTpe)) =>
            Refinement(parent, name, fTpe)
          }

        case TypeCategory.CaseClassType(fields) =>
          // case class → structural: { def field1: T1; def field2: T2; ... }
          val transformedFields = fields.map { case (fieldSym, fieldTpe) =>
            (fieldSym.name, transformType(fieldTpe, seen + dealt))
          }
          // Use StructuralRecord as base so selectDynamic is visible
          val base = TypeRepr.of[StructuralRecord]
          transformedFields.foldLeft(base) { case (parent, (name, fTpe)) =>
            Refinement(parent, name, fTpe)
          }

        case TypeCategory.SealedType(children) =>
          // sealed trait → union of structural types with Tag
          val childStructuralTypes = children.map { childSym =>
            val childType = if (childSym.isTerm) {
              TypeRepr.of[StructuralRecord]
            } else {
              val cType      = TypeIdent(childSym).tpe
              val typeParams =
                cType.typeSymbol.primaryConstructor.paramSymss.headOption.map(_.filter(_.isTypeParam)).getOrElse(Nil)
              if (typeParams.size == dealt.typeArgs.size && dealt.typeArgs.nonEmpty) {
                cType.appliedTo(dealt.typeArgs)
              } else {
                cType
              }
            }

            val structType = transformType(childType, seen + dealt)
            val tagName    = childSym.name
            val tagType    = ConstantType(StringConstant(tagName))
            Refinement(structType, "Tag", TypeBounds(tagType, tagType))
          }

          if (childStructuralTypes.isEmpty) {
            TypeRepr.of[Nothing]
          } else {
            childStructuralTypes.reduce((a, b) => OrType(a, b))
          }
      }
    }

    // Compute the structural type for the root type A
    val structuralTypeRepr = transformType(tpe, Set.empty)

    // Generate a converter function for a given type
    // Returns an Expr[Any => Any] that converts values of that type
    def generateConverter(fieldTpe: TypeRepr): Expr[Any => Any] = {
      categorize(fieldTpe) match {
        case TypeCategory.PrimitiveLike | TypeCategory.Unknown =>
          '{ (x: Any) => x }

        case TypeCategory.CaseClassType(fields) =>
          fieldTpe.asType match {
            case '[ft] =>
              val fieldConverters: List[(String, Expr[Any => Any])] = fields.map { case (fieldSym, nestedFieldTpe) =>
                (fieldSym.name, generateConverter(nestedFieldTpe))
              }
              '{ (x: Any) =>
                val v                     = x.asInstanceOf[ft]
                val map: Map[String, Any] = Map(
                  ${
                    val entries = fieldConverters.map { case (name, converter) =>
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
          val elemConverter = generateConverter(elem)
          '{ (x: Any) => x.asInstanceOf[Option[Any]].map($elemConverter) }

        case TypeCategory.ListType(elem) =>
          val elemConverter = generateConverter(elem)
          '{ (x: Any) => x.asInstanceOf[List[Any]].map($elemConverter) }

        case TypeCategory.VectorType(elem) =>
          val elemConverter = generateConverter(elem)
          '{ (x: Any) => x.asInstanceOf[Vector[Any]].map($elemConverter) }

        case TypeCategory.SeqType(elem) =>
          val elemConverter = generateConverter(elem)
          '{ (x: Any) => x.asInstanceOf[Seq[Any]].map($elemConverter) }

        case TypeCategory.SetType(elem) =>
          val elemConverter = generateConverter(elem)
          '{ (x: Any) => x.asInstanceOf[Set[Any]].map($elemConverter) }

        case TypeCategory.MapType(key, value) =>
          val keyConverter = generateConverter(key)
          val valConverter = generateConverter(value)
          '{ (x: Any) => x.asInstanceOf[Map[Any, Any]].map { case (k, v) => ($keyConverter(k), $valConverter(v)) } }

        case TypeCategory.EitherType(left, right) =>
          // Either → StructuralRecord with Tag = "Left"/"Right" and value field
          val leftConverter  = generateConverter(left)
          val rightConverter = generateConverter(right)
          '{ (x: Any) =>
            x.asInstanceOf[Either[Any, Any]] match {
              case Left(l) =>
                new StructuralRecord(Map("Tag" -> "Left", "value" -> $leftConverter(l)))
              case Right(r) =>
                new StructuralRecord(Map("Tag" -> "Right", "value" -> $rightConverter(r)))
            }
          }

        case TypeCategory.TupleType(elements) =>
          // Convert tuple to StructuralRecord with _1, _2, etc. fields
          fieldTpe.asType match {
            case '[ft] =>
              val fieldConverters: List[(String, Expr[Any => Any])] = elements.zipWithIndex.map { case (elemTpe, idx) =>
                (s"_${idx + 1}", generateConverter(elemTpe))
              }
              '{ (x: Any) =>
                val v                     = x.asInstanceOf[ft]
                val map: Map[String, Any] = Map(
                  ${
                    val entries = fieldConverters.map { case (name, converter) =>
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
          // Sealed type → StructuralRecord with Tag field based on runtime type
          // Generate converters for each child type
          val childConverters: List[(Symbol, Expr[Any => Any])] = children.map { childSym =>
            val childTpe = if (childSym.flags.is(Flags.Module)) {
              Ref(childSym).tpe
            } else {
              TypeIdent(childSym).tpe
            }

            val converter = categorize(childTpe) match {
              case TypeCategory.CaseClassType(fields) =>
                // Case class child: extract fields and add Tag
                childTpe.asType match {
                  case '[ct] =>
                    val fieldConverters = fields.map { case (fieldSym, nestedFieldTpe) =>
                      (fieldSym.name, generateConverter(nestedFieldTpe))
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

          // Generate pattern match expression
          fieldTpe.asType match {
            case '[st] =>
              '{ (x: Any) =>
                ${
                  // Build a chain of if-else checks using isInstanceOf
                  childConverters.foldRight('{ throw new MatchError(x) }: Expr[Any]) {
                    case ((childSym, converter), elseExpr) =>
                      val childTpe = if (childSym.flags.is(Flags.Module)) {
                        Ref(childSym).tpe
                      } else {
                        TypeIdent(childSym).tpe
                      }
                      childTpe.asType match {
                        case '[ct] =>
                          '{ if (x.isInstanceOf[ct]) $converter(x) else $elseExpr }
                      }
                  }
                }
              }
          }
      }
    }

    // For case class: extract fields and create StructuralRecord
    if (isCaseType && !isSealedType) {
      val fields = symbol.caseFields

      structuralTypeRepr.asType match {
        case '[st] =>
          // Generate converters for each field
          val fieldConverters: List[(String, TypeRepr, Expr[Any => Any])] = fields.map { f =>
            val name     = f.name
            val fieldTpe = tpe.memberType(f)
            (name, fieldTpe, generateConverter(fieldTpe))
          }

          // Identify which fields are case classes and need nested ToStructural
          // Generate ToStructural instances for nested case class fields
          val nestedToStructurals: List[(String, TypeRepr, Expr[ToStructural[?]])] = fields.flatMap { f =>
            val name     = f.name
            val fieldTpe = tpe.memberType(f)
            categorize(fieldTpe) match {
              case TypeCategory.CaseClassType(_) =>
                fieldTpe.asType match {
                  case '[ft] =>
                    // Recursively derive ToStructural for nested case class
                    val nestedTs = '{ ToStructural.derived[ft] }
                    Some((name, fieldTpe, nestedTs))
                }
              case _ => None
            }
          }

          // Create a map expression for nested ToStructural instances
          val nestedTsMapExpr: Expr[Map[String, ToStructural[?]]] = {
            val entries = nestedToStructurals.map { case (name, _, tsExpr) =>
              '{ ${ Expr(name) } -> $tsExpr }
            }
            '{ Map(${ Expr.ofList(entries) }*) }
          }

          // Set of field names that are case classes
          val caseClassFieldNames: Set[String] = nestedToStructurals.map(_._1).toSet

          '{
            new ToStructural[A] {
              type StructuralType = st

              // Store nested ToStructural instances for case class fields
              private val nestedToStructuralMap: Map[String, ToStructural[?]] = $nestedTsMapExpr

              def toStructural(value: A): StructuralType = {
                val map: Map[String, Any] = Map(
                  ${
                    val entries: List[Expr[(String, Any)]] = fieldConverters.map { case (name, _, converter) =>
                      val accessor = Select.unique('value.asTerm, name).asExpr
                      '{ ${ Expr(name) } -> $converter($accessor.asInstanceOf[Any]) }
                    }
                    Expr.ofList(entries)
                  }*
                )
                new StructuralRecord(map).asInstanceOf[StructuralType]
              }

              def structuralSchema(implicit schema: Schema[A]): Schema[StructuralType] = {
                // Field names for this structural type
                val fieldNames: IndexedSeq[String] = IndexedSeq(
                  ${
                    val names = fieldConverters.map { case (name, _, _) => Expr(name) }
                    Expr.ofList(names)
                  }*
                )

                // Set of field names that are case classes (computed at compile time)
                val caseClassFields: Set[String] = ${ Expr(caseClassFieldNames) }

                // Get field info from the nominal schema
                val nominalRecord      = schema.reflect.asRecord.get
                val nominalConstructor = nominalRecord.constructor

                // For each field, use structural schema if it's a nested case class
                val fieldSchemas: IndexedSeq[Reflect.Bound[?]] = nominalRecord.fields.map { term =>
                  if (caseClassFields.contains(term.name)) {
                    // Get nested ToStructural and derive structural schema
                    val nestedTs     = nestedToStructuralMap(term.name)
                    val nestedSchema = Schema(term.value.asInstanceOf[Reflect.Bound[Any]])
                    // Get structural schema for the nested type
                    nestedTs
                      .asInstanceOf[ToStructural[Any]]
                      .structuralSchema(nestedSchema)
                      .reflect
                      .asInstanceOf[Reflect.Bound[?]]
                  } else {
                    term.value
                  }
                }

                // Reuse the nominal schema's register layout
                val usedRegs = nominalConstructor.usedRegisters

                // Create Terms for structural record (using SchemaTerm alias to avoid conflict with macro Term)
                val structuralFields: IndexedSeq[zio.blocks.schema.Term[Binding, StructuralType, ?]] =
                  fieldNames.zip(fieldSchemas).zipWithIndex.map { case ((name, fieldReflect), _) =>
                    new zio.blocks.schema.Term[Binding, StructuralType, Any](
                      name,
                      fieldReflect.asInstanceOf[Reflect.Bound[Any]]
                    )
                  }

                // Create TypeName for structural type using common method
                val structuralTypeName: TypeName[StructuralType] =
                  TypeName.structuralFromTypeNames(fieldNames.zip(fieldSchemas.map(_.typeName)))

                // Constructor: Registers → StructuralRecord
                // Build StructuralRecord directly from registers (handles nested case classes)
                val structuralConstructor = new Constructor[StructuralType] {
                  def usedRegisters: RegisterOffset = usedRegs

                  def construct(in: Registers, baseOffset: RegisterOffset): StructuralType = {
                    // Read field values directly from registers
                    val fieldMap = nominalRecord.fields.map { term =>
                      val regIdx     = nominalRecord.fieldIndexByName(term.name)
                      val reg        = nominalRecord.registers(regIdx)
                      val fieldValue = reg.asInstanceOf[Register[Any]].get(in, baseOffset)
                      term.name -> fieldValue
                    }.toMap
                    new StructuralRecord(fieldMap).asInstanceOf[StructuralType]
                  }
                }

                // Deconstructor: StructuralRecord → Registers
                // Extract field values and write them using the nominal deconstructor's layout
                val structuralDeconstructor = new Deconstructor[StructuralType] {
                  def usedRegisters: RegisterOffset = usedRegs

                  def deconstruct(out: Registers, baseOffset: RegisterOffset, in: StructuralType): Unit = {
                    val record = in.asInstanceOf[StructuralRecord]

                    // For each field, extract value and write to registers
                    nominalRecord.fields.foreach { term =>
                      val fieldValue = record.selectDynamic(term.name)
                      val regIdx     = nominalRecord.fieldIndexByName(term.name)
                      val reg        = nominalRecord.registers(regIdx)
                      reg.asInstanceOf[Register[Any]].set(out, baseOffset, fieldValue)
                    }
                  }
                }

                new Schema[StructuralType](
                  new Reflect.Record[Binding, StructuralType](
                    fields = structuralFields,
                    typeName = structuralTypeName,
                    recordBinding = new Binding.Record[StructuralType](
                      constructor = structuralConstructor,
                      deconstructor = structuralDeconstructor
                    )
                  )
                )
              }
            }
          }
      }
    } else {
      // Sealed trait: generate converter that pattern matches on children
      val children = symbol.children

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
                      (fieldSym.name, generateConverter(nestedFieldTpe))
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
                // Child names for Tag-based discrimination
                val childNames: IndexedSeq[String] = IndexedSeq(
                  ${
                    val names = children.map(c => Expr(c.name))
                    Expr.ofList(names)
                  }*
                )

                // Get variant info from the nominal schema
                val nominalVariant = schema.reflect.asVariant.get

                // Create structural cases - each case is a Term with the structural type for that case
                val structuralCases: IndexedSeq[zio.blocks.schema.Term[Binding, StructuralType, ? <: StructuralType]] =
                  childNames.zip(nominalVariant.cases).map { case (name, nominalCase) =>
                    // For each case, we create a Term that points to a Record schema for StructuralRecord
                    // The structural type for each case is StructuralRecord { def Tag: String; ... }
                    val caseReflect = nominalCase.value.asRecord match {
                      case Some(record) =>
                        // Extract everything from record into stable, non-path-dependent vals
                        // This avoids the path-dependent type issue with nominalCase.A in nested closures
                        val fieldNames: IndexedSeq[String]               = record.fields.map(_.name)
                        val fieldSchemas: IndexedSeq[Reflect.Bound[Any]] =
                          record.fields.map(_.value.asInstanceOf[Reflect.Bound[Any]])
                        val registers: IndexedSeq[Register[Any]] =
                          record.registers.asInstanceOf[IndexedSeq[Register[Any]]]
                        val usedRegs: RegisterOffset = record.constructor.usedRegisters

                        // Case class: has fields + Tag
                        val caseFields = fieldNames.zip(fieldSchemas).map { case (fname, fschema) =>
                          new zio.blocks.schema.Term[Binding, StructuralRecord, Any](fname, fschema)
                        }
                        // Add Tag field
                        val tagField = new zio.blocks.schema.Term[Binding, StructuralRecord, String](
                          "Tag",
                          Reflect.string[Binding]
                        )
                        val allFields = tagField +: caseFields

                        val caseTypeName: TypeName[StructuralRecord] = TypeName.taggedCase(name)

                        val caseConstructor = new Constructor[StructuralRecord] {
                          def usedRegisters: RegisterOffset = usedRegs

                          def construct(in: Registers, baseOffset: RegisterOffset): StructuralRecord = {
                            // Extract field values from registers and build StructuralRecord
                            val fieldMap = fieldNames.zipWithIndex.map { case (fname, idx) =>
                              val reg = registers(idx)
                              fname -> reg.get(in, baseOffset)
                            }.toMap + ("Tag" -> name)
                            new StructuralRecord(fieldMap)
                          }
                        }

                        val caseDeconstructor = new Deconstructor[StructuralRecord] {
                          def usedRegisters: RegisterOffset = usedRegs

                          def deconstruct(out: Registers, baseOffset: RegisterOffset, in: StructuralRecord): Unit =
                            fieldNames.zipWithIndex.foreach { case (fname, idx) =>
                              val fieldValue = in.selectDynamic(fname)
                              val reg        = registers(idx)
                              reg.set(out, baseOffset, fieldValue)
                            }
                        }

                        new Reflect.Record[Binding, StructuralRecord](
                          fields = allFields,
                          typeName = caseTypeName,
                          recordBinding = new Binding.Record[StructuralRecord](
                            constructor = caseConstructor,
                            deconstructor = caseDeconstructor
                          )
                        )

                      case None =>
                        // Case object: only Tag field
                        val tagField = new zio.blocks.schema.Term[Binding, StructuralRecord, String](
                          "Tag",
                          Reflect.string[Binding]
                        )

                        val caseTypeName: TypeName[StructuralRecord] = TypeName.taggedCase(name)

                        new Reflect.Record[Binding, StructuralRecord](
                          fields = IndexedSeq(tagField),
                          typeName = caseTypeName,
                          recordBinding = new Binding.Record[StructuralRecord](
                            constructor = new Constructor[StructuralRecord] {
                              def usedRegisters: RegisterOffset = 0

                              def construct(in: Registers, baseOffset: RegisterOffset): StructuralRecord =
                                new StructuralRecord(Map("Tag" -> name))
                            },
                            deconstructor = new Deconstructor[StructuralRecord] {
                              def usedRegisters: RegisterOffset = 0

                              def deconstruct(out: Registers, baseOffset: RegisterOffset, in: StructuralRecord): Unit =
                                ()
                            }
                          )
                        )
                    }

                    new zio.blocks.schema.Term[Binding, StructuralType, StructuralRecord](
                      name,
                      caseReflect
                    ).asInstanceOf[zio.blocks.schema.Term[Binding, StructuralType, ? <: StructuralType]]
                  }

                // Create TypeName for structural variant
                val structuralTypeName: TypeName[StructuralType] = TypeName.variant(childNames)

                // Create tag-to-index map for discriminator
                val tagToIndex: Map[String, Int] = childNames.zipWithIndex.toMap

                // Discriminator: look at Tag field
                val structuralDiscriminator = new Discriminator[StructuralType] {
                  def discriminate(a: StructuralType): Int = {
                    val record = a.asInstanceOf[StructuralRecord]
                    val tag    = record.selectDynamic("Tag").asInstanceOf[String]
                    tagToIndex.getOrElse(tag, -1)
                  }
                }

                // Matchers: each case just returns the StructuralRecord as-is
                val structuralMatchers = Matchers(
                  childNames.map { _ =>
                    new Matcher[StructuralRecord] {
                      def downcastOrNull(a: Any): StructuralRecord = a match {
                        case sr: StructuralRecord => sr
                        case _                    => null
                      }
                    }.asInstanceOf[Matcher[? <: StructuralType]]
                  }*
                )

                new Schema[StructuralType](
                  new Reflect.Variant[Binding, StructuralType](
                    cases = structuralCases,
                    typeName = structuralTypeName,
                    variantBinding = new Binding.Variant[StructuralType](
                      discriminator = structuralDiscriminator,
                      matchers = structuralMatchers
                    )
                  )
                )
              }
            }
          }
      }
    }
  }
}
