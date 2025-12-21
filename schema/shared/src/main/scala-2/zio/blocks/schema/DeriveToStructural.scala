package zio.blocks.schema

import scala.reflect.macros.whitebox

object DeriveToStructural {
  def derivedImpl[A: c.WeakTypeTag](c: whitebox.Context): c.Expr[ToStructural[A]] = {
    import c.universe._

    def fail(msg: String): Nothing =
      c.abort(c.enclosingPosition, msg)

    val tpe    = weakTypeOf[A]
    val symbol = tpe.typeSymbol

    // Validate that the type is a case class (sealed traits not supported in Scala 2)
    if (symbol.isClass && symbol.asClass.isSealed) {
      fail(
        s"""Cannot generate structural type for sum types in Scala 2 ($tpe).
           |Structural representation of sum types requires union types,
           |which are only available in Scala 3.
           |Consider upgrading to Scala 3 or using a different approach.""".stripMargin
      )
    }

    if (!symbol.isClass || !symbol.asClass.isCaseClass) {
      fail(s"ToStructural derivation only supports case classes, found: $tpe")
    }

    // Type categorization ADT - single source of truth for type classification
    sealed trait TypeCategory
    object TypeCategory {
      case object PrimitiveLike                                        extends TypeCategory
      case class OptionType(element: Type)                             extends TypeCategory
      case class ListType(element: Type)                               extends TypeCategory
      case class VectorType(element: Type)                             extends TypeCategory
      case class SeqType(element: Type)                                extends TypeCategory
      case class SetType(element: Type)                                extends TypeCategory
      case class MapType(key: Type, value: Type)                       extends TypeCategory
      case class EitherType(left: Type, right: Type)                   extends TypeCategory
      case class TupleType(elements: List[Type])                       extends TypeCategory
      case class CaseClassType(fields: List[(MethodSymbol, Type)])     extends TypeCategory
      case object Unknown                                              extends TypeCategory
    }

    // Single categorization function - replaces all the isX predicates
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
        if (sym.isClass && sym.asClass.isCaseClass && !sym.asClass.isSealed) {
          val fields = dealt.decls.collect {
            case m: MethodSymbol if m.isCaseAccessor => m
          }.toList.map(f => (f, f.returnType.asSeenFrom(dealt, sym)))
          TypeCategory.CaseClassType(fields)
        } else {
          TypeCategory.Unknown
        }
      }
    }

    val toStructuralType = typeOf[ToStructural[_]].typeConstructor

    def deriveToStructural(t: Type): Tree = {
      val wanted = appliedType(toStructuralType, List(t))
      c.inferImplicitValue(wanted) match {
        case EmptyTree => q"_root_.zio.blocks.schema.ToStructural.derived[$t]"
        case other     => other
      }
    }

    // Recursion Fail message
    def failRecursion(loopType: Type, stack: List[Type]): Nothing = {
      // Extract the cycle: types from when we first saw loopType until now
      val cycle = stack.takeWhile(t => !(t =:= loopType)) :+ loopType

      // Find all structural types (case classes) in the cycle
      // Filter out containers (List, Option, etc.) as they're not the recursive types themselves
      val structuralTypesInCycle = cycle.filter { t =>
        categorize(t) match {
          case TypeCategory.CaseClassType(_) => true
          case _                             => false
        }
      }.map(_.typeSymbol.name.decodedName.toString).distinct

      // Multiple types in cycle = mutual recursion, single type = direct recursion
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
      }
    }

    // Recursion Validation
    checkRecursive(tpe, Nil)

    // Transforms a nominal type into its structural representation.
    def structuralTypeTree(t: Type, seen: Set[Type]): Tree = {
      val dealt = t.dealias

      // If we're already processing this type, return it as-is to prevent infinite loops
      if (seen.contains(dealt)) {
        return tq"$dealt"
      }

      categorize(dealt) match {
        case TypeCategory.PrimitiveLike | TypeCategory.Unknown =>
          // Primitives and unknown types pass through unchanged
          tq"$dealt"

        case TypeCategory.OptionType(elem) =>
          // Option[T] → Option[structural(T)]
          val inner = structuralTypeTree(elem, seen + dealt)
          tq"_root_.scala.Option[$inner]"

        case TypeCategory.ListType(elem) =>
          // List[T] → List[structural(T)]
          val inner = structuralTypeTree(elem, seen + dealt)
          tq"_root_.scala.List[$inner]"

        case TypeCategory.VectorType(elem) =>
          // Vector[T] → Vector[structural(T)]
          val inner = structuralTypeTree(elem, seen + dealt)
          tq"_root_.scala.Vector[$inner]"

        case TypeCategory.SeqType(elem) =>
          // Seq[T] → Seq[structural(T)]
          val inner = structuralTypeTree(elem, seen + dealt)
          tq"_root_.scala.Seq[$inner]"

        case TypeCategory.SetType(elem) =>
          // Set[T] → Set[structural(T)]
          val inner = structuralTypeTree(elem, seen + dealt)
          tq"_root_.scala.collection.immutable.Set[$inner]"

        case TypeCategory.MapType(key, value) =>
          // Map[K, V] → Map[structural(K), structural(V)]
          val k = structuralTypeTree(key, seen + dealt)
          val v = structuralTypeTree(value, seen + dealt)
          tq"_root_.scala.collection.immutable.Map[$k,$v]"

        case TypeCategory.EitherType(left, right) =>
          // Either[L, R] → Either[structural(L), structural(R)]
          val l = structuralTypeTree(left, seen + dealt)
          val r = structuralTypeTree(right, seen + dealt)
          tq"_root_.scala.Either[$l,$r]"

        case TypeCategory.TupleType(elements) =>
          // (T1, T2, T3) → StructuralValue { def _1: structural(T1); def _2: structural(T2); def _3: structural(T3) }
          val decls = elements.zipWithIndex.map { case (arg, idx) =>
            val st = structuralTypeTree(arg, seen + dealt)
            q"def ${TermName(s"_${idx + 1}")}: $st"
          }
          tq"_root_.zio.blocks.schema.binding.StructuralValue { ..$decls }"

        case TypeCategory.CaseClassType(fields) =>
          // case class Person(name: String, age: Int)
          //   → StructuralValue { def name: String; def age: Int }
          val decls = fields.map { case (f, fieldTpe) =>
            val st = structuralTypeTree(fieldTpe, seen + dealt)
            q"def ${f.name}: $st"
          }
          tq"_root_.zio.blocks.schema.binding.StructuralValue { ..$decls }"
      }
    }

    // Generates runtime code to transform a value from nominal to structural form.
    def convertExpr(expr: Tree, t: Type, seen: Set[Type]): Tree = {
      val dealt = t.dealias

      // If we're already processing this type, return it as-is to prevent infinite loops
      if (seen.contains(dealt)) {
        return expr
      }

      categorize(dealt) match {
        case TypeCategory.PrimitiveLike | TypeCategory.Unknown =>
          // Primitives and unknown types pass through unchanged
          expr

        case TypeCategory.OptionType(elem) =>
          // Generate: optionValue.map(v => convertExpr(v))
          val body = convertExpr(Ident(TermName("v")), elem, seen + dealt)
          q"$expr.map(v => $body)"

        case TypeCategory.ListType(elem) =>
          // Generate: list.map(v => convertExpr(v))
          val body = convertExpr(Ident(TermName("v")), elem, seen + dealt)
          q"$expr.map(v => $body)"

        case TypeCategory.VectorType(elem) =>
          // Generate: vector.map(v => convertExpr(v))
          val body = convertExpr(Ident(TermName("v")), elem, seen + dealt)
          q"$expr.map(v => $body)"

        case TypeCategory.SeqType(elem) =>
          // Generate: seq.map(v => convertExpr(v))
          val body = convertExpr(Ident(TermName("v")), elem, seen + dealt)
          q"$expr.map(v => $body)"

        case TypeCategory.SetType(elem) =>
          // Generate: set.map(v => convertExpr(v))
          val body = convertExpr(Ident(TermName("v")), elem, seen + dealt)
          q"$expr.map(v => $body)"

        case TypeCategory.MapType(key, value) =>
          // Generate: map.map { case (k, v) => (convertExpr(k), convertExpr(v)) }
          val kbody = convertExpr(Ident(TermName("k")), key, seen + dealt)
          val vbody = convertExpr(Ident(TermName("v")), value, seen + dealt)
          q"$expr.map { case (k, v) => ($kbody, $vbody) }"

        case TypeCategory.EitherType(left, right) =>
          // Generate: either match { case Left(v) => Left(convertExpr(v)); case Right(v) => Right(convertExpr(v)) }
          val lbody = convertExpr(Ident(TermName("l")), left, seen + dealt)
          val rbody = convertExpr(Ident(TermName("r")), right, seen + dealt)
          q"$expr match { case scala.util.Left(l) => scala.util.Left($lbody); case scala.util.Right(r) => scala.util.Right($rbody) }"

        case TypeCategory.TupleType(elements) =>
          // Generate: new StructuralValue(Map("_1" -> tuple._1, "_2" -> tuple._2, ...))
          val entries = elements.zipWithIndex.map { case (argTpe, idx) =>
            val nameLit = Literal(Constant(s"_${idx + 1}"))
            val value   = q"$expr.${TermName(s"_${idx + 1}")}"
            val conv    = convertExpr(value, argTpe, seen + dealt)
            q"$nameLit -> $conv"
          }
          q"new _root_.zio.blocks.schema.binding.StructuralValue(_root_.scala.collection.immutable.Map[String, Any](..$entries))"

        case TypeCategory.CaseClassType(_) =>
          // Nested case class: Delegate to its ToStructural instance
          val ts = deriveToStructural(dealt)
          q"$ts.toStructural($expr)"
      }
    }

    // Generate the ToStructural instance
    val tree = q"""
      new _root_.zio.blocks.schema.ToStructural[$tpe] {
        type StructuralType = ${structuralTypeTree(tpe, Set.empty)}

        def toStructural(value: $tpe): StructuralType = {
          val values = _root_.scala.collection.immutable.Map[String, Any](..${tpe.decls.collect {
        case m: MethodSymbol if m.isCaseAccessor =>
          val decoded = m.name.decodedName.toString.trim
          q"$decoded -> ${convertExpr(q"value.${m.name}", m.returnType.asSeenFrom(tpe, symbol), Set(tpe))}"
      }.toList})
          new _root_.zio.blocks.schema.binding.StructuralValue(values).asInstanceOf[StructuralType]
        }

        def structuralSchema(implicit schema: _root_.zio.blocks.schema.Schema[$tpe]): _root_.zio.blocks.schema.Schema[StructuralType] = {
          _root_.zio.blocks.schema.Schema.derived[StructuralType]
        }
      }
    """

    c.Expr[ToStructural[A]](tree)
  }
}
