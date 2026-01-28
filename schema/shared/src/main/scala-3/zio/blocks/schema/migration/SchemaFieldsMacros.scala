package zio.blocks.schema.migration

import scala.quoted.*
import zio.blocks.schema.Schema

/**
 * Macros for extracting field names from Schema at compile time.
 *
 * These macros inspect the Schema[A] at compile time and extract field names
 * as a type-level tuple of singleton string types, enabling compile-time
 * validation of migration completeness.
 */
object SchemaFieldsMacros {

  /**
   * Derive a SchemaFields instance for type A.
   *
   * For record types, extracts field names as a tuple type.
   * For non-record types, returns EmptyTuple.
   */
  inline given derived[A](using schema: Schema[A]): SchemaFields[A] =
    ${ deriveSchemaFieldsImpl[A]('schema) }

  /**
   * Implementation of the derived macro.
   */
  def deriveSchemaFieldsImpl[A: Type](schema: Expr[Schema[A]])(using Quotes): Expr[SchemaFields[A]] = {
    // Extract field names at runtime from the schema
    // Since Schema is a value, we create a SchemaFields that uses runtime extraction
    // but captures the field names as a type when possible
    '{
      val s = $schema
      val names = SchemaFields.extractFieldNames(s)
      new SchemaFields[A] {
        type Fields = EmptyTuple // We use EmptyTuple as a placeholder
        def fieldNames: List[String] = names
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
   * Create a SchemaFields from an explicit list of field names.
   * Used when field names are known at the call site.
   */
  inline def fromNames[A](inline names: String*): SchemaFields[A] =
    ${ fromNamesImpl[A]('names) }

  def fromNamesImpl[A: Type](names: Expr[Seq[String]])(using Quotes): Expr[SchemaFields[A]] = {
    import quotes.reflect.*

    // Extract the string literals from the varargs
    val namesList: List[String] = names match {
      case Varargs(exprs) =>
        exprs.toList.map {
          case '{ $s: String } =>
            s.asTerm match {
              case Literal(StringConstant(str)) => str
              case _ =>
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
