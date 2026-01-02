package zio.blocks.schema

import scala.reflect.macros.whitebox

/**
 * Macro-based derivation for ToStructural in Scala 2.
 *
 * ToStructural converts nominal types (case classes, tuples) into structural
 * types represented at runtime by StructuralRecord.
 *
 * Three-Phase Architecture:
 * ═════════════════════════════════════════════════════════════════════════════
 *
 * PHASE 1: Type Validation (Compile-time)
 * ─────────────────────────────────────────────────────────────────────────────
 * Goal: Validate input types can be converted to structural form
 *
 *   - categorize(): Classifies types into categories (primitive, case class,
 *     etc.)
 *   - checkRecursive(): Validates no recursive types (structural types can't
 *     represent cycles)
 *
 * Note: Unlike Scala 3, Scala 2 does NOT build refined structural types because
 * Scala 2's refinement types use reflection (incompatible with Dynamic trait).
 * All structural types are simply StructuralRecord.
 *
 * PHASE 2: Value Converter Code Generation (Compile-time → Runtime)
 * ─────────────────────────────────────────────────────────────────────────────
 * Goal: Generate code that converts runtime values from nominal to structural
 *
 *   - buildValueConverter(): Generates quasiquote Tree expressions that convert
 *     values Example: Person("Alice") → new StructuralRecord(Map("name" ->
 *     "Alice"))
 *
 * Limitations: Scala 2 does not support Either or sealed traits (no union
 * types).
 *
 * PHASE 3: Schema Transformation (Runtime)
 * ─────────────────────────────────────────────────────────────────────────────
 * Goal: Transform Schema[Nominal] to Schema[StructuralRecord] for serialization
 *
 *   - structuralSchema(): Delegates to StructuralSchemaTransformer.transform()
 *   - Transforms bindings (constructor/deconstructor) to work with
 *     StructuralRecord
 *   - Enables toDynamicValue/fromDynamicValue to work with structural values
 *
 * Why 3 Phases?
 * ─────────────────────────────────────────────────────────────────────────────
 *   1. Validation: Ensure types are convertible (prevent runtime errors)
 *   2. Value-level: Runtime needs to convert actual data
 *   3. Schema-level: Serialization needs metadata about the structural form
 */
object DeriveToStructural {
  def derivedImpl[A: c.WeakTypeTag](c: whitebox.Context): c.Expr[ToStructural[A]] = {
    import c.universe._

    def fail(msg: String): Nothing =
      c.abort(c.enclosingPosition, msg)

    val tpe    = weakTypeOf[A]
    val symbol = tpe.typeSymbol

    // Entry validation: sealed traits not supported (no union types in Scala 2)
    if (symbol.isClass && symbol.asClass.isSealed) {
      fail(
        s"""Cannot generate structural type for sum types in Scala 2 ($tpe).
           |Structural representation of sum types requires union types,
           |which are only available in Scala 3.
           |Consider upgrading to Scala 3 or using a different approach.""".stripMargin
      )
    }

    // Entry validation: must be a case class
    if (!symbol.isClass || !symbol.asClass.isCaseClass) {
      fail(s"ToStructural derivation only supports case classes, found: $tpe")
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // PHASE 1: TYPE VALIDATION
    // ═══════════════════════════════════════════════════════════════════════════

    // Type categorization ADT - single source of truth for type classification
    sealed trait TypeCategory
    object TypeCategory {
      case object PrimitiveLike                                    extends TypeCategory
      case class OptionType(element: Type)                         extends TypeCategory
      case class ListType(element: Type)                           extends TypeCategory
      case class VectorType(element: Type)                         extends TypeCategory
      case class SeqType(element: Type)                            extends TypeCategory
      case class SetType(element: Type)                            extends TypeCategory
      case class MapType(key: Type, value: Type)                   extends TypeCategory
      case class EitherType(left: Type, right: Type)               extends TypeCategory
      case class TupleType(elements: List[Type])                   extends TypeCategory
      case class CaseClassType(fields: List[(MethodSymbol, Type)]) extends TypeCategory
      case object SealedType                                       extends TypeCategory
      case object Unknown                                          extends TypeCategory
    }

    // Single categorization function
    def categorize(t: Type): TypeCategory = {
      val dealt = t.dealias

      // Check primitives first
      if (
        dealt <:< definitions.BooleanTpe ||
        dealt <:< definitions.ByteTpe ||
        dealt <:< definitions.ShortTpe ||
        dealt <:< definitions.IntTpe ||
        dealt <:< definitions.LongTpe ||
        dealt <:< definitions.FloatTpe ||
        dealt <:< definitions.DoubleTpe ||
        dealt <:< definitions.CharTpe ||
        dealt <:< definitions.UnitTpe ||
        dealt =:= typeOf[String] ||
        dealt =:= typeOf[BigDecimal] ||
        dealt =:= typeOf[BigInt] ||
        dealt =:= typeOf[java.util.UUID] ||
        dealt =:= typeOf[java.util.Currency] ||
        dealt.typeSymbol.fullName.startsWith("java.time.")
      ) {
        return TypeCategory.PrimitiveLike
      }

      // Check containers and structural types
      if (dealt.typeConstructor =:= typeOf[Option[Any]].typeConstructor) {
        TypeCategory.OptionType(dealt.typeArgs.head)
      } else if (dealt.typeConstructor =:= typeOf[List[Any]].typeConstructor) {
        TypeCategory.ListType(dealt.typeArgs.head)
      } else if (dealt.typeConstructor =:= typeOf[Vector[Any]].typeConstructor) {
        TypeCategory.VectorType(dealt.typeArgs.head)
      } else if (dealt.typeConstructor =:= typeOf[Seq[Any]].typeConstructor) {
        TypeCategory.SeqType(dealt.typeArgs.head)
      } else if (dealt.typeConstructor =:= typeOf[Set[Any]].typeConstructor) {
        TypeCategory.SetType(dealt.typeArgs.head)
      } else if (dealt.typeConstructor =:= typeOf[Map[Any, Any]].typeConstructor) {
        TypeCategory.MapType(dealt.typeArgs(0), dealt.typeArgs(1))
      } else if (dealt.typeConstructor =:= typeOf[Either[Any, Any]].typeConstructor) {
        TypeCategory.EitherType(dealt.typeArgs(0), dealt.typeArgs(1))
      } else if (dealt.typeSymbol.fullName.startsWith("scala.Tuple")) {
        TypeCategory.TupleType(dealt.typeArgs)
      } else {
        val sym = dealt.typeSymbol
        if (sym.isClass && sym.asClass.isSealed) {
          TypeCategory.SealedType
        } else if (sym.isClass && sym.asClass.isCaseClass) {
          val fields = dealt.decls.collect {
            case m: MethodSymbol if m.isCaseAccessor => m
          }.toList.map(f => (f, f.returnType.asSeenFrom(dealt, sym)))
          TypeCategory.CaseClassType(fields)
        } else {
          TypeCategory.Unknown
        }
      } 
    }

    // Recursion detection
    def failRecursion(loopType: Type, stack: List[Type]): Nothing = {
      val cycle = stack.takeWhile(t => !(t =:= loopType)) :+ loopType

      val structuralTypesInCycle = cycle.filter { t =>
        categorize(t) match {
          case TypeCategory.CaseClassType(_) => true
          case _                             => false
        }
      }.map(_.typeSymbol.name.decodedName.toString).distinct

      if (structuralTypesInCycle.size > 1) {
        fail(
          s"Cannot generate structural type: mutually recursive types detected: ${structuralTypesInCycle.mkString(", ")}. " +
            "Structural types cannot represent cyclic dependencies."
        )
      } else {
        fail(
          s"Cannot generate structural type: recursive type detected: ${loopType.typeSymbol.name.decodedName.toString}. " +
            "Structural types cannot represent recursive structures."
        )
      }
    }

    def checkRecursive(t: Type, stack: List[Type]): Unit = {
      val dealt = t.dealias

      if (stack.exists(_ =:= dealt)) {
        failRecursion(dealt, stack)
      }

      categorize(dealt) match {
        case TypeCategory.PrimitiveLike =>
          ()

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

        case TypeCategory.TupleType(elements) =>
          elements.foreach(elem => checkRecursive(elem, dealt :: stack))

        case TypeCategory.CaseClassType(fields) =>
          fields.foreach { case (_, fieldTpe) =>
            checkRecursive(fieldTpe, dealt :: stack)
          }
        
        case TypeCategory.EitherType(_, _) =>
          fail(
            s"""Cannot generate structural type for Either in Scala 2.
               |Either structural representation requires union types,
               |which are only available in Scala 3.
               |Consider upgrading to Scala 3 or using a different approach.""".stripMargin
          )  

        case TypeCategory.SealedType =>
          fail(
            s"""Cannot generate structural type for sum types in Scala 2 (${dealt.typeSymbol.name.decodedName.toString}).
               |Structural representation of sum types requires union types,
               |which are only available in Scala 3.
               |Consider upgrading to Scala 3 or using a different approach.""".stripMargin
          )

        case TypeCategory.Unknown =>
          fail(
            s"""Cannot generate structural type for unsupported type: ${dealt.typeSymbol.name.decodedName.toString}.
               |ToStructural only supports:
               |  - Primitive types (Int, String, Boolean, etc.)
               |  - Case classes
               |  - Collections (List, Vector, Set, Seq, Option, Map)
               |  - Tuples
               |The type '${dealt.typeSymbol.fullName}' is not supported.
               |If this is a regular class, consider converting it to a case class.""".stripMargin
          )  
      }
    }

    // Validate no recursive types before proceeding
    checkRecursive(tpe, Nil)

    // ═══════════════════════════════════════════════════════════════════════════
    // PHASE 2: VALUE CONVERTER CODE GENERATION
    // ═══════════════════════════════════════════════════════════════════════════

    // Get case class fields
    val fields: List[(MethodSymbol, Type)] = tpe.decls.collect {
      case m: MethodSymbol if m.isCaseAccessor => m
    }.toList.map(f => (f, f.returnType.asSeenFrom(tpe, symbol)))

    /**
     * Generates code that converts runtime values from nominal to structural
     * form.
     *
     * This creates Tree expressions (quasiquotes) that run at runtime to
     * extract field values from nominal types and construct StructuralRecord
     * instances.
     *
     * Example: For Person(name: String, age: Int), generates:
     * ```
     * new StructuralRecord(Map("name" -> person.name, "age" -> person.age))
     * ```
     *
     * Handles nested conversions recursively (collections, tuples, case
     * classes). Does NOT support Either or sealed traits (Scala 2 limitation).
     */
    def buildValueConverter(expr: Tree, t: Type, seen: Set[Type]): Tree = {
      val dealt = t.dealias

      if (seen.contains(dealt)) {
        return expr
      }

      categorize(dealt) match {
        case TypeCategory.PrimitiveLike =>
          expr

        case TypeCategory.OptionType(elem) =>
          val body = buildValueConverter(Ident(TermName("v")), elem, seen + dealt)
          q"$expr.map(v => $body)"

        case TypeCategory.ListType(elem) =>
          val body = buildValueConverter(Ident(TermName("v")), elem, seen + dealt)
          q"$expr.map(v => $body)"

        case TypeCategory.VectorType(elem) =>
          val body = buildValueConverter(Ident(TermName("v")), elem, seen + dealt)
          q"$expr.map(v => $body)"

        case TypeCategory.SeqType(elem) =>
          val body = buildValueConverter(Ident(TermName("v")), elem, seen + dealt)
          q"$expr.map(v => $body)"

        case TypeCategory.SetType(elem) =>
          val body = buildValueConverter(Ident(TermName("v")), elem, seen + dealt)
          q"$expr.map(v => $body)"

        case TypeCategory.MapType(key, value) =>
          val kbody = buildValueConverter(Ident(TermName("k")), key, seen + dealt)
          val vbody = buildValueConverter(Ident(TermName("v")), value, seen + dealt)
          q"$expr.map { case (k, v) => ($kbody, $vbody) }"

        case TypeCategory.TupleType(elements) =>
          // Convert tuple to StructuralRecord with _1, _2, etc. fields
          val entries = elements.zipWithIndex.map { case (argTpe, idx) =>
            val nameLit = Literal(Constant(s"_${idx + 1}"))
            val value   = q"$expr.${TermName(s"_${idx + 1}")}"
            val conv    = buildValueConverter(value, argTpe, seen + dealt)
            q"$nameLit -> $conv"
          }
          q"new _root_.zio.blocks.schema.StructuralRecord(_root_.scala.collection.immutable.Map[String, Any](..$entries))"

        case TypeCategory.CaseClassType(cFields) =>
          // Nested case class: convert to StructuralRecord
          val entries = cFields.map { case (f, fieldTpe) =>
            val nameLit   = Literal(Constant(f.name.decodedName.toString.trim))
            val fieldExpr = q"$expr.${f.name}"
            val conv      = buildValueConverter(fieldExpr, fieldTpe, seen + dealt)
            q"$nameLit -> $conv"
          }
          q"new _root_.zio.blocks.schema.StructuralRecord(_root_.scala.collection.immutable.Map[String, Any](..$entries))"
      
        case TypeCategory.EitherType(_, _) =>
          // Should never reach here - Either is rejected in checkRecursive
          fail("Either types are not supported in Scala 2 structural types")    

        case TypeCategory.SealedType =>
          // Should never reach here - sealed types are rejected in checkRecursive
          fail("Sealed types are not supported in Scala 2 structural types")

        case TypeCategory.Unknown =>
          // Should never reach here - Unknown types are rejected in checkRecursive
          fail("Cannot generate structural converter for unsupported type")
      
      }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // TOSTRUCTURAL INSTANCE GENERATION
    // ═══════════════════════════════════════════════════════════════════════════
    // Generate the ToStructural[A] implementation with:
    // 1. type StructuralType = StructuralRecord (always, no refinements in Scala 2)
    // 2. toStructural(): Uses Phase 2 converters to transform values
    // 3. structuralSchema(): Delegates to Phase 3 (StructuralSchemaTransformer)

    // Generate field conversion entries for the root case class
    val fieldEntries = fields.map { case (f, fieldTpe) =>
      val nameLit   = Literal(Constant(f.name.decodedName.toString.trim))
      val fieldExpr = q"value.${f.name}"
      val conv      = buildValueConverter(fieldExpr, fieldTpe, Set(tpe))
      q"$nameLit -> $conv"
    }

    // Generate the ToStructural instance code
    val tree = q"""
      new _root_.zio.blocks.schema.ToStructural[$tpe] {
        type StructuralType = _root_.zio.blocks.schema.StructuralRecord

        def toStructural(value: $tpe): StructuralType = {
          new _root_.zio.blocks.schema.StructuralRecord(
            _root_.scala.collection.immutable.Map[String, Any](..$fieldEntries)
          )
        }

        def structuralSchema(implicit schema: _root_.zio.blocks.schema.Schema[$tpe]): _root_.zio.blocks.schema.Schema[StructuralType] = {
          // PHASE 3: Transform the schema's bindings to work with StructuralRecord
          // The nominal schema expects case class objects with field accessors
          // We transform it to expect StructuralRecord with selectDynamic access (via Dynamic trait)
          // This enables serialization (toDynamicValue) and deserialization (fromDynamicValue)
          _root_.zio.blocks.schema.StructuralSchemaTransformer.transform[$tpe](schema, this)
        }
      }
    """

    c.Expr[ToStructural[A]](tree)
  }
}
