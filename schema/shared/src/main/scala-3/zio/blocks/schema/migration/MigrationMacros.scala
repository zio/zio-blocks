package zio.blocks.schema.migration

import zio.blocks.schema.{DynamicOptic, Schema}

import scala.quoted.*

/**
 * Scala 3 macro implementations for migration selectors.
 */
object MigrationMacros {

  /**
   * Extract a DynamicOptic path from a selector function like _.name or
   * _.address.street
   */
  inline def selectorToOptic[S, A](inline selector: S => A): DynamicOptic =
    ${ selectorToOpticImpl[S, A]('selector) }

  private def selectorToOpticImpl[S: Type, A: Type](selector: Expr[S => A])(using Quotes): Expr[DynamicOptic] = {
    import quotes.reflect.*

    def extractPath(term: Term): List[Expr[DynamicOptic.Node]] = term match {
      case Ident(_)                     => Nil
      case Select(qualifier, fieldName) =>
        extractPath(qualifier) :+ '{ DynamicOptic.Node.Field(${ Expr(fieldName) }) }
      case Apply(Select(qualifier, "each"), _) =>
        extractPath(qualifier) :+ '{ DynamicOptic.Node.Elements }
      case Apply(Select(qualifier, "eachKey"), _) =>
        extractPath(qualifier) :+ '{ DynamicOptic.Node.MapKeys }
      case Apply(Select(qualifier, "eachValue"), _) =>
        extractPath(qualifier) :+ '{ DynamicOptic.Node.MapValues }
      case _ =>
        report.errorAndAbort(s"Unsupported selector expression: ${term.show}")
    }

    selector.asTerm match {
      case Inlined(_, _, Lambda(_, body)) =>
        val nodes     = extractPath(body)
        val nodesExpr = Expr.ofSeq(nodes)
        '{ DynamicOptic(Vector($nodesExpr*)) }
      case Lambda(_, body) =>
        val nodes     = extractPath(body)
        val nodesExpr = Expr.ofSeq(nodes)
        '{ DynamicOptic(Vector($nodesExpr*)) }
      case other =>
        report.errorAndAbort(s"Expected a selector function, got: ${other.show}")
    }
  }

  /**
   * Extract field name from a simple selector like _.fieldName
   */
  inline def selectorToFieldName[S, A](inline selector: S => A): String =
    ${ selectorToFieldNameImpl[S, A]('selector) }

  private def selectorToFieldNameImpl[S: Type, A: Type](selector: Expr[S => A])(using Quotes): Expr[String] = {
    import quotes.reflect.*

    selector.asTerm match {
      case Inlined(_, _, Lambda(_, Select(_, fieldName))) =>
        Expr(fieldName)
      case Lambda(_, Select(_, fieldName)) =>
        Expr(fieldName)
      case _ =>
        report.errorAndAbort("Expected a simple field selector like _.fieldName")
    }
  }

} // end MigrationMacros

/**
 * Scala 3 extension methods for MigrationBuilder with macro-based selectors.
 */
extension [A, B](builder: MigrationBuilder[A, B]) {

  /**
   * Add a field using a selector for the target field. Example:
   * .addField(_.age, 0)
   */
  inline def addField[T](inline target: B => T, default: T)(using schema: Schema[T]): MigrationBuilder[A, B] = {
    val fieldName = MigrationMacros.selectorToFieldName(target)
    builder.addFieldTyped[T](fieldName, default)
  }

  /**
   * Drop a field using a selector for the source field. Example:
   * .dropField(_.oldField)
   */
  inline def dropField[T](inline source: A => T): MigrationBuilder[A, B] = {
    val fieldName = MigrationMacros.selectorToFieldName(source)
    builder.dropField(fieldName)
  }

  /**
   * Drop a field with a default for reverse. Example: .dropField(_.oldField,
   * defaultValue)
   */
  inline def dropFieldWithDefault[T](inline source: A => T, defaultForReverse: T)(using
    schema: Schema[T]
  ): MigrationBuilder[A, B] = {
    val fieldName = MigrationMacros.selectorToFieldName(source)
    builder.dropFieldWithDefault(fieldName, schema.toDynamicValue(defaultForReverse))
  }

  /**
   * Rename a field using selectors. Example: .renameField(_.oldName, _.newName)
   */
  inline def renameField[T1, T2](inline from: A => T1, inline to: B => T2): MigrationBuilder[A, B] = {
    val fromName = MigrationMacros.selectorToFieldName(from)
    val toName   = MigrationMacros.selectorToFieldName(to)
    builder.renameField(fromName, toName)
  }

  /**
   * Transform a field using a selector and expression. Example:
   * .transformField(_.count, SchemaExpr.add(1))
   */
  inline def transformField[T](inline selector: A => T, transform: SchemaExpr): MigrationBuilder[A, B] = {
    val fieldName = MigrationMacros.selectorToFieldName(selector)
    builder.transformField(fieldName, transform)
  }

  /**
   * Make an optional field required. Example: .mandateField(_.optionalAge,
   * _.age, 0)
   */
  inline def mandateField[T](inline source: A => Option[T], inline target: B => T, default: T)(using
    schema: Schema[T]
  ): MigrationBuilder[A, B] = {
    val sourceFieldName = MigrationMacros.selectorToFieldName(source)
    val _               = MigrationMacros.selectorToFieldName(target) // validate target field exists
    builder.mandateFieldTyped[T](sourceFieldName, default)
  }

  /**
   * Make a required field optional. Example: .optionalizeField(_.requiredName,
   * _.optionalName)
   */
  inline def optionalizeField[T](inline source: A => T, inline target: B => Option[T]): MigrationBuilder[A, B] = {
    val sourceFieldName = MigrationMacros.selectorToFieldName(source)
    val _               = MigrationMacros.selectorToFieldName(target) // validate target field exists
    builder.optionalizeField(sourceFieldName)
  }

  /**
   * Change a field's type. Example: .changeFieldType(_.strAge, _.intAge,
   * SchemaExpr.convert(String, Int))
   */
  inline def changeFieldType[T1, T2](
    inline source: A => T1,
    inline target: B => T2,
    converter: SchemaExpr
  ): MigrationBuilder[A, B] = {
    val sourceFieldName = MigrationMacros.selectorToFieldName(source)
    val _               = MigrationMacros.selectorToFieldName(target) // validate target field exists
    builder.changeFieldType(sourceFieldName, converter)
  }

  /**
   * Transform elements using a selector for a collection field. Example:
   * .transformElements(_.items, SchemaExpr.identity)
   */
  inline def transformElements[T](inline selector: A => Vector[T], transform: SchemaExpr): MigrationBuilder[A, B] = {
    val optic = MigrationMacros.selectorToOptic(selector)
    builder.transformElementsAt(optic, transform)
  }

  /**
   * Transform map keys using a selector.
   */
  inline def transformKeys[K, V](inline selector: A => Map[K, V], transform: SchemaExpr): MigrationBuilder[A, B] = {
    val optic = MigrationMacros.selectorToOptic(selector)
    builder.transformKeysAt(optic, transform)
  }

  /**
   * Transform map values using a selector.
   */
  inline def transformValues[K, V](inline selector: A => Map[K, V], transform: SchemaExpr): MigrationBuilder[A, B] = {
    val optic = MigrationMacros.selectorToOptic(selector)
    builder.transformValuesAt(optic, transform)
  }

  /**
   * Build with full macro validation.
   */
  inline def build: Migration[A, B] =
    // For now, just build without full validation
    // Full validation would check that all source fields are handled
    // and all target fields are populated
    builder.buildPartial
}
