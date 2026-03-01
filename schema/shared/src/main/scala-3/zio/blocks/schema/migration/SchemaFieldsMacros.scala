package zio.blocks.schema.migration

import scala.quoted.*

/**
 * Macros for extracting field names from case class types at compile time.
 *
 * Inspects type A and extracts field names as a type-level tuple of singleton
 * string types, enabling compile-time validation of migration completeness.
 */
object SchemaFieldsMacros {

  /**
   * Derive a SchemaFields instance for type A with concrete field types.
   *
   * For case class types, extracts field names as a tuple type like: ("name" *:
   * "age" *: EmptyTuple)
   *
   * For non-case-class types, returns EmptyTuple.
   *
   * This is the primary way to get SchemaFields with proper type information.
   */
  inline def derived[A]: SchemaFields[A] = ${ derivedImpl[A] }

  private def derivedImpl[A: Type](using Quotes): Expr[SchemaFields[A]] = {
    import quotes.reflect.*

    val tpe = TypeRepr.of[A]

    // Extract field names from case class
    val fieldNames: List[String] = tpe.classSymbol match {
      case Some(cls) if cls.flags.is(Flags.Case) =>
        cls.primaryConstructor.paramSymss.flatten
          .filter(_.isValDef)
          .map(_.name)
      case _ =>
        Nil
    }

    if (fieldNames.isEmpty) {
      '{ SchemaFields.emptyWith[A, EmptyTuple] }
    } else {
      // Build the tuple type from field names
      val tupleType = fieldNames.foldRight(TypeRepr.of[EmptyTuple]) { (name, acc) =>
        val nameType = ConstantType(StringConstant(name))
        TypeRepr.of[*:].appliedTo(List(nameType, acc))
      }

      tupleType.asType match {
        case '[t] =>
          val namesExpr = Expr(fieldNames)
          '{ SchemaFields[A, t]($namesExpr) }
      }
    }
  }

  /**
   * Create a SchemaFields with field names extracted at macro expansion time
   * when we know the structure statically.
   *
   * This version is used when we can determine fields from the type structure.
   */
  inline def fromType[A]: SchemaFields[A] = ${ fromTypeImpl[A] }

  def fromTypeImpl[A: Type](using Quotes): Expr[SchemaFields[A]] = {
    import quotes.reflect.*

    val tpe = TypeRepr.of[A]

    // Try to extract field names from the type if it's a case class
    val fieldNames: List[String] = tpe.classSymbol match {
      case Some(cls) if cls.flags.is(Flags.Case) =>
        // Get primary constructor parameters
        cls.primaryConstructor.paramSymss.flatten
          .filter(_.isValDef)
          .map(_.name)
      case _ =>
        Nil
    }

    if (fieldNames.isEmpty) {
      '{ SchemaFields.emptyWith[A, EmptyTuple] }
    } else {
      // Build the tuple type from field names
      val tupleType = fieldNames.foldRight(TypeRepr.of[EmptyTuple]) { (name, acc) =>
        val nameType = ConstantType(StringConstant(name))
        TypeRepr.of[*:].appliedTo(List(nameType, acc))
      }

      tupleType.asType match {
        case '[t] =>
          val namesExpr = Expr(fieldNames)
          '{ SchemaFields[A, t]($namesExpr) }
      }
    }
  }

  /**
   * Create a SchemaFields from an explicit list of field names. Used when field
   * names are known at the call site.
   */
  inline def fromNames[A](inline names: String*): SchemaFields[A] =
    ${ fromNamesImpl[A]('names) }

  def fromNamesImpl[A: Type](names: Expr[Seq[String]])(using Quotes): Expr[SchemaFields[A]] = {
    import quotes.reflect.*

    // Extract the string literals from the varargs
    val namesList: List[String] = names match {
      case Varargs(exprs) =>
        exprs.toList.map { case '{ $s: String } =>
          s.asTerm match {
            case Literal(StringConstant(str)) => str
            case _                            =>
              report.errorAndAbort("fromNames requires string literals")
          }
        }
      case _ =>
        report.errorAndAbort("fromNames requires string literals")
    }

    if (namesList.isEmpty) {
      '{ SchemaFields.emptyWith[A, EmptyTuple] }
    } else {
      // Build the tuple type from field names
      val tupleType = namesList.foldRight(TypeRepr.of[EmptyTuple]) { (name, acc) =>
        val nameType = ConstantType(StringConstant(name))
        TypeRepr.of[*:].appliedTo(List(nameType, acc))
      }

      tupleType.asType match {
        case '[t] =>
          val namesExpr = Expr(namesList)
          '{ SchemaFields[A, t]($namesExpr) }
      }
    }
  }
}
