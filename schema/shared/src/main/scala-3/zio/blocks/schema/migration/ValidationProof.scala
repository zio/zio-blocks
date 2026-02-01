package zio.blocks.schema.migration

import scala.quoted.*

/**
 * Type-level proof that a schema migration is complete.
 *
 * A migration from A to B is complete when:
 *   - All field paths in source but not in target are handled (dropped or
 *     renamed from)
 *   - All field paths in target but not in source are provided (added or
 *     renamed to)
 *   - All case names removed from source are handled
 *   - All case names added to target are provided
 *
 * This typeclass is automatically derived at compile time when calling `.build`
 * on a MigrationBuilder, providing compile-time verification of migration
 * completeness.
 *
 * Type parameters:
 *   - A: Source schema type
 *   - B: Target schema type
 *   - Handled: Tuple of field paths and case names handled from source
 *   - Provided: Tuple of field paths and case names provided for target
 */
sealed trait ValidationProof[A, B, Handled <: Tuple, Provided <: Tuple]

object ValidationProof {

  /**
   * Concrete implementation. Public for transparent inline given.
   */
  final class Impl[A, B, Handled <: Tuple, Provided <: Tuple] extends ValidationProof[A, B, Handled, Provided]

  /**
   * Derive a ValidationProof at compile time.
   *
   * The proof is generated when:
   *   1. All paths in A but not in B are subset of Handled
   *   2. All paths in B but not in A are subset of Provided
   *   3. All cases in A but not in B are subset of Handled
   *   4. All cases in B but not in A are subset of Provided
   */
  transparent inline given derive[A, B, Handled <: Tuple, Provided <: Tuple](using
    fpA: FieldPaths[A],
    fpB: FieldPaths[B],
    cpA: CasePaths[A],
    cpB: CasePaths[B]
  ): ValidationProof[A, B, Handled, Provided] =
    scala.compiletime.summonFrom {
      case _: (TypeLevel.IsSubset[TypeLevel.Difference[fpA.Paths, fpB.Paths], Handled] =:= true) =>
        scala.compiletime.summonFrom {
          case _: (TypeLevel.IsSubset[TypeLevel.Difference[fpB.Paths, fpA.Paths], Provided] =:= true) =>
            scala.compiletime.summonFrom {
              case _: (TypeLevel.IsSubset[TypeLevel.Difference[cpA.Cases, cpB.Cases], Handled] =:= true) =>
                scala.compiletime.summonFrom {
                  case _: (TypeLevel.IsSubset[TypeLevel.Difference[cpB.Cases, cpA.Cases], Provided] =:= true) =>
                    new Impl[A, B, Handled, Provided]
                }
            }
        }
    }

  /**
   * Validate migration with detailed compile-time error messages.
   *
   * Use this instead of derive for better diagnostics when validation fails.
   */
  inline def require[A, B, Handled <: Tuple, Provided <: Tuple]: ValidationProof[A, B, Handled, Provided] =
    ${ requireImpl[A, B, Handled, Provided] }

  private def requireImpl[A: Type, B: Type, Handled <: Tuple: Type, Provided <: Tuple: Type](using
    q: Quotes
  ): Expr[ValidationProof[A, B, Handled, Provided]] = {
    import q.reflect.*

    val sourceType = TypeRepr.of[A].dealias
    val targetType = TypeRepr.of[B].dealias

    val pathsA = extractAllPaths(sourceType)
    val pathsB = extractAllPaths(targetType)
    val casesA = extractCaseNames(sourceType).map(c => s"case:$c")
    val casesB = extractCaseNames(targetType).map(c => s"case:$c")

    val handled  = extractTupleStrings(TypeRepr.of[Handled])
    val provided = extractTupleStrings(TypeRepr.of[Provided])

    val requiredHandled  = (pathsA.diff(pathsB) ++ casesA.diff(casesB)).sorted
    val requiredProvided = (pathsB.diff(pathsA) ++ casesB.diff(casesA)).sorted

    val unhandled  = requiredHandled.diff(handled)
    val unprovided = requiredProvided.diff(provided)

    if (unhandled.nonEmpty || unprovided.nonEmpty) {
      val sourceName = sourceType.typeSymbol.name
      val targetName = targetType.typeSymbol.name

      // Separate fields from cases for clearer error messages
      val unhandledFields  = unhandled.filterNot(_.startsWith("case:"))
      val unhandledCases   = unhandled.filter(_.startsWith("case:")).map(_.stripPrefix("case:"))
      val unprovidedFields = unprovided.filterNot(_.startsWith("case:"))
      val unprovidedCases  = unprovided.filter(_.startsWith("case:")).map(_.stripPrefix("case:"))

      val sb = new StringBuilder
      sb.append(s"\n╔═══════════════════════════════════════════════════════════════\n")
      sb.append(s"║ Migration Validation Failed: $sourceName => $targetName\n")
      sb.append(s"╚═══════════════════════════════════════════════════════════════\n\n")

      if (unhandledFields.nonEmpty) {
        sb.append("UNHANDLED FIELD PATHS (fields removed from source, need dropField or renameField):\n")
        unhandledFields.foreach(p => sb.append(s"  ✗ $p\n"))
        sb.append("\n")
      }

      if (unprovidedFields.nonEmpty) {
        sb.append("UNPROVIDED FIELD PATHS (fields added to target, need addField or renameField):\n")
        unprovidedFields.foreach(p => sb.append(s"  ✗ $p\n"))
        sb.append("\n")
      }

      if (unhandledCases.nonEmpty) {
        sb.append("UNHANDLED ENUM CASES (cases removed from source, need dropCase or renameCase):\n")
        unhandledCases.foreach(c => sb.append(s"  ✗ $c\n"))
        sb.append("\n")
      }

      if (unprovidedCases.nonEmpty) {
        sb.append("UNPROVIDED ENUM CASES (cases added to target, need addCase or renameCase):\n")
        unprovidedCases.foreach(c => sb.append(s"  ✗ $c\n"))
        sb.append("\n")
      }

      sb.append("───────────────────────────────────────────────────────────────\n")
      sb.append("HINTS:\n")

      if (unhandledFields.nonEmpty) {
        val example      = unhandledFields.head
        val selectorPath = example.split("\\.").mkString("_.")
        sb.append(s"  → .dropField(_.$selectorPath, defaultForReverse) - remove field from migration\n")
      }
      if (unprovidedFields.nonEmpty) {
        val example      = unprovidedFields.head
        val selectorPath = example.split("\\.").mkString("_.")
        sb.append(s"  → .addField(_.$selectorPath, defaultValue) - add field with default\n")
      }
      if (unhandledFields.nonEmpty && unprovidedFields.nonEmpty) {
        sb.append("  → .renameField(_.oldPath, _.newPath) - when field was renamed, not added/removed\n")
      }
      if (unhandledCases.nonEmpty) {
        sb.append(s"  → .renameCase(_.when[${unhandledCases.head}], \"NewCaseName\") - rename enum case\n")
        sb.append(s"  → .transformCase[${unhandledCases.head}](actions) - transform case contents\n")
      }
      if (unprovidedCases.nonEmpty) {
        sb.append(s"  → .renameCase(_.when[OldCase], \"${unprovidedCases.head}\") - target case from rename\n")
      }

      sb.append("───────────────────────────────────────────────────────────────\n")

      report.errorAndAbort(sb.toString)
    }

    '{ new ValidationProof.Impl[A, B, Handled, Provided] }
  }

  private def extractAllPaths(using q: Quotes)(tpe: q.reflect.TypeRepr): List[String] = {
    import q.reflect.*

    def extractFromType(t: TypeRepr, prefix: String, seen: Set[String]): List[String] = {
      val typeName = t.typeSymbol.fullName
      if (seen.contains(typeName)) return Nil
      val newSeen = seen + typeName

      t.dealias match {
        case ref: Refinement =>
          extractRefinement(ref, prefix, newSeen)

        case t if t.typeSymbol.flags.is(Flags.Case) =>
          val fields = t.typeSymbol.caseFields
          fields.flatMap { field =>
            val fieldName = field.name
            val fieldPath = if (prefix.isEmpty) fieldName else s"$prefix.$fieldName"
            val fieldType = t.memberType(field)
            if (isPrimitive(fieldType)) List(fieldPath)
            else fieldPath :: extractFromType(fieldType, fieldPath, newSeen)
          }.toList

        case _ => Nil
      }
    }

    def extractRefinement(ref: Refinement, prefix: String, seen: Set[String]): List[String] = {
      def loop(tpe: TypeRepr, acc: List[String]): List[String] = tpe match {
        case Refinement(parent, name, info) if name != "Tag" =>
          val fieldPath = if (prefix.isEmpty) name else s"$prefix.$name"
          val fieldType = info match {
            case ByNameType(resultType)       => resultType
            case MethodType(_, _, resultType) => resultType
            case other                        => other
          }
          val nestedPaths =
            if (isPrimitive(fieldType)) Nil
            else extractFromType(fieldType, fieldPath, seen)
          loop(parent, fieldPath :: (nestedPaths ++ acc))
        case Refinement(parent, _, _) => loop(parent, acc)
        case _                        => acc
      }
      loop(ref, Nil)
    }

    extractFromType(tpe, "", Set.empty).sorted
  }

  private def extractCaseNames(using q: Quotes)(tpe: q.reflect.TypeRepr): List[String] = {
    import q.reflect.*
    if (tpe.typeSymbol.flags.is(Flags.Sealed)) {
      tpe.typeSymbol.children.map(_.name)
    } else {
      Nil
    }
  }

  private def extractTupleStrings(using q: Quotes)(tpe: q.reflect.TypeRepr): List[String] = {
    import q.reflect.*

    def extractString(t: TypeRepr): Option[String] = t.dealias match {
      case ConstantType(StringConstant(s)) => Some(s)
      case _                               => None
    }

    def extract(t: TypeRepr): List[String] = t.dealias match {
      case AppliedType(tycon, List(head, tail)) if tycon.typeSymbol.fullName.endsWith("*:") =>
        extractString(head).toList ::: extract(tail)
      case AppliedType(tycon, args) if tycon.typeSymbol.fullName.startsWith("scala.Tuple") =>
        args.flatMap(extractString)
      case t if t =:= TypeRepr.of[EmptyTuple] => Nil
      case _                                  => Nil
    }

    extract(tpe)
  }

  private def isPrimitive(using q: Quotes)(tpe: q.reflect.TypeRepr): Boolean = {
    val name = tpe.typeSymbol.fullName
    primitiveNames.exists(name.contains)
  }

  private val primitiveNames = Set(
    "scala.Boolean",
    "scala.Byte",
    "scala.Short",
    "scala.Int",
    "scala.Long",
    "scala.Float",
    "scala.Double",
    "scala.Char",
    "java.lang.String",
    "scala.Predef.String",
    "String",
    "BigInt",
    "BigDecimal",
    "java.util.UUID",
    "java.time.Instant",
    "java.time.LocalDate"
  )
}

/**
 * Typeclass for extracting field paths from a type at compile time.
 *
 * For nested case classes, returns all dot-separated paths as a Tuple type: {{
 * case class Address(street: String, city: String) case class Person(name:
 * String, address: Address)
 *
 * FieldPaths[Person].Paths =:= ("address", "address.city", "address.street",
 * "name") }}
 */
sealed trait FieldPaths[A] {
  type Paths <: Tuple
}

object FieldPaths {

  class Impl[A, P <: Tuple] extends FieldPaths[A] {
    type Paths = P
  }

  transparent inline given derived[A]: FieldPaths[A] = ${ derivedImpl[A] }

  private def derivedImpl[A: Type](using q: Quotes): Expr[FieldPaths[A]] = {
    import q.reflect.*

    val tpe   = TypeRepr.of[A].dealias
    val paths = extractPaths(tpe, "", Set.empty).sorted

    val tupleType = pathsToTupleType(paths)

    tupleType.asType match {
      case '[t] => '{ new FieldPaths.Impl[A, t & Tuple] }
    }
  }

  private def extractPaths(using
    q: Quotes
  )(
    tpe: q.reflect.TypeRepr,
    prefix: String,
    seen: Set[String]
  ): List[String] = {
    import q.reflect.*

    val typeName = tpe.typeSymbol.fullName
    if (seen.contains(typeName)) return Nil
    val newSeen = seen + typeName

    tpe.dealias match {
      case ref: Refinement =>
        extractRefinementPaths(ref, prefix)

      case t if t.typeSymbol.flags.is(Flags.Case) =>
        val fields = t.typeSymbol.caseFields
        fields.flatMap { field =>
          val fieldName = field.name
          val fieldPath = if (prefix.isEmpty) fieldName else s"$prefix.$fieldName"
          val fieldType = t.memberType(field)
          if (isPrimitive(fieldType)) List(fieldPath)
          else fieldPath :: extractPaths(fieldType, fieldPath, newSeen)
        }.toList

      case _ => Nil
    }
  }

  private def extractRefinementPaths(using
    q: Quotes
  )(
    ref: q.reflect.Refinement,
    prefix: String
  ): List[String] = {
    import q.reflect.*

    def loop(tpe: TypeRepr, acc: List[String]): List[String] = tpe match {
      case Refinement(parent, name, _) if name != "Tag" =>
        val fieldPath = if (prefix.isEmpty) name else s"$prefix.$name"
        loop(parent, fieldPath :: acc)
      case Refinement(parent, _, _) => loop(parent, acc)
      case _                        => acc
    }
    loop(ref, Nil)
  }

  private def isPrimitive(using q: Quotes)(tpe: q.reflect.TypeRepr): Boolean = {
    val name = tpe.typeSymbol.fullName
    Set(
      "scala.Boolean",
      "scala.Byte",
      "scala.Short",
      "scala.Int",
      "scala.Long",
      "scala.Float",
      "scala.Double",
      "scala.Char",
      "java.lang.String",
      "scala.Predef.String",
      "BigInt",
      "BigDecimal"
    ).exists(name.contains)
  }

  private def pathsToTupleType(using q: Quotes)(paths: List[String]): q.reflect.TypeRepr = {
    import q.reflect.*
    paths.foldRight(TypeRepr.of[EmptyTuple]) { (path, acc) =>
      val pathType = ConstantType(StringConstant(path))
      TypeRepr.of[*:].appliedTo(List(pathType, acc))
    }
  }
}

/**
 * Typeclass for extracting case names from sealed traits/enums at compile time.
 *
 * Returns case names as a Tuple type with "case:" prefix.
 */
sealed trait CasePaths[A] {
  type Cases <: Tuple
}

object CasePaths {

  class Impl[A, C <: Tuple] extends CasePaths[A] {
    type Cases = C
  }

  transparent inline given derived[A]: CasePaths[A] = ${ derivedImpl[A] }

  private def derivedImpl[A: Type](using q: Quotes): Expr[CasePaths[A]] = {
    import q.reflect.*

    val tpe       = TypeRepr.of[A].dealias
    val caseNames = extractCaseNames(tpe).sorted.map(name => s"case:$name")

    val tupleType = namesToTupleType(caseNames)

    tupleType.asType match {
      case '[t] => '{ new CasePaths.Impl[A, t & Tuple] }
    }
  }

  private def extractCaseNames(using q: Quotes)(tpe: q.reflect.TypeRepr): List[String] = {
    import q.reflect.*
    if (tpe.typeSymbol.flags.is(Flags.Sealed)) {
      tpe.typeSymbol.children.map(_.name)
    } else {
      Nil
    }
  }

  private def namesToTupleType(using q: Quotes)(names: List[String]): q.reflect.TypeRepr = {
    import q.reflect.*
    names.foldRight(TypeRepr.of[EmptyTuple]) { (name, acc) =>
      val nameType = ConstantType(StringConstant(name))
      TypeRepr.of[*:].appliedTo(List(nameType, acc))
    }
  }
}
