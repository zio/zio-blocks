package zio.blocks.schema.derive

import scala.annotation.experimental
import scala.quoted.*
import scala.reflect.Selectable

// NOTE: Legacy reflection-based structural proxy classes
// (`StructuralWrapper`, `StructuralProxy`, `FinalStructuralProxy`,
// and `StructuralBridge`) have been removed in favor of a compile-time
// generated anonymous `Selectable` wrapper that performs hard-coded
// dispatch on method names without using `reflectiveSelectable` or
// Java reflection. See `generateAnonymousSelectable` below.

/**
 * Macros for handling structural type conversions (Selectable) in Into
 * derivation.
 *
 * Structural types allow converting case classes to types with structural
 * refinements, e.g., `case class Point(x: Int, y: Int)` →
 * `{ def x: Int; def y: Int }`
 */
object StructuralMacros {

  /**
   * Attempts to derive an Into[A, B] instance for structural type conversions.
   *
   * Supports:
   *   - Case class → Structural type (Selectable)
   *   - Structural type → Case class (if all required fields match)
   *
   * @return
   *   Some(expr) if conversion is possible, None otherwise
   */
  @experimental
  def structuralTypeConversion[A: Type, B: Type](using
    Quotes
  )(
    source: quotes.reflect.TypeRepr,
    target: quotes.reflect.TypeRepr
  ): Option[Expr[zio.blocks.schema.Into[A, B]]] = {

    val isTargetStructural = isStructuralType(target)
    val isSourceStructural = isStructuralType(source)
    val isSourceProduct    = isProductType(source)
    val isTargetProduct    = isProductType(target)

    // Case 1: Product → Structural type
    if (isSourceProduct && isTargetStructural) {
      return Some(generateProductToStructural[A, B](source, target))
    }

    // Case 2: Structural type → Product
    if (isSourceStructural && isTargetProduct) {
      return Some(generateStructuralToProduct[A, B](source, target))
    }

    // Case 3: Structural type → Structural type
    if (isSourceStructural && isTargetStructural) {
      return Some(generateStructuralToStructural[A, B](source, target))
    }

    None
  }

  /**
   * Generate conversion from product type (case class) to structural type.
   *
   * New implementation: generates an anonymous Selectable wrapper whose
   * `applyDynamic` implementation performs a hard-coded dispatch on the method
   * name. This avoids `reflectiveSelectable` and Java reflection and ensures
   * that structural calls are resolved via a compile-time generated match
   * expression (implemented here come catena di `if/else`).
   */
  @experimental
  private def generateProductToStructural[A: Type, B: Type](using
    Quotes
  )(
    source: quotes.reflect.TypeRepr,
    target: quotes.reflect.TypeRepr
  ): Expr[zio.blocks.schema.Into[A, B]] = {
    import quotes.reflect.*

    // Extract required methods from structural type with return types
    val requiredMethods = extractStructuralMethodsWithTypes(target)

    // Verify source has all required methods and check type compatibility
    val sourceFields   = extractProductFieldsWithTypes(source)
    val missingMethods = requiredMethods.filter { case (methodName, _, methodReturnType) =>
      !sourceFields.exists { case (fieldName, fieldReturnType) =>
        fieldName == methodName && isTypeCompatible(fieldReturnType, methodReturnType)
      }
    }

    if (missingMethods.nonEmpty) {
      val missingNames = missingMethods.map(_._1).mkString(", ")
      report.errorAndAbort(
        s"Cannot convert ${source.show} to structural type ${target.show}. " +
          s"Missing required methods: $missingNames"
      )
    }

    '{
      new zio.blocks.schema.Into[A, B] {
        def into(input: A): Either[zio.blocks.schema.SchemaError, B] =
          try {
            val wrapper =
              ${
                generateAnonymousSelectable[A](
                  'input,
                  requiredMethods.map { case (name, paramCount, _) => (name, paramCount) }
                )
              }
            Right(wrapper.asInstanceOf[B])
          } catch {
            case e: Exception =>
              Left(
                zio.blocks.schema.SchemaError.expectationMismatch(
                  Nil,
                  "Failed to convert " + ${ Expr(source.show) } + " to structural type " + ${
                    Expr(target.show)
                  } + ": " + e.getMessage
                )
              )
          }
      }
    }
  }

  /**
   * Generate conversion from structural type to product type (case class).
   */
  private def generateStructuralToProduct[A: Type, B: Type](using
    Quotes
  )(
    source: quotes.reflect.TypeRepr,
    target: quotes.reflect.TypeRepr
  ): Expr[zio.blocks.schema.Into[A, B]] = {
    import quotes.reflect.*

    // Extract required fields from target product type with types
    val targetFields = extractProductFieldsWithTypes(target)

    // Extract available methods from source structural type with types
    val sourceMethods = extractStructuralMethodsWithTypes(source)

    // Verify all target fields have corresponding methods in source with compatible types
    val missingFields = targetFields.filter { case (fieldName, fieldReturnType) =>
      !sourceMethods.exists { case (methodName, _, methodReturnType) =>
        methodName == fieldName && isTypeCompatible(methodReturnType, fieldReturnType)
      }
    }

    if (missingFields.nonEmpty) {
      val missingNames = missingFields.map(_._1).mkString(", ")
      report.errorAndAbort(
        s"Cannot convert structural type ${source.show} to ${target.show}. " +
          s"Missing required methods: $missingNames"
      )
    }

    // Get target companion for construction
    val targetClass = target.classSymbol.getOrElse {
      report.errorAndAbort(s"Target type ${target.show} is not a case class")
    }

    // Build field extraction - generate code to extract each field
    // Use Java reflection which works on all platforms (JVM, JS, Native)
    // Generate the conversion code
    target.asType match {
      case '[t] =>
        // Generate field names as a list for iteration
        val fieldNames     = targetFields.map(_._1)
        val fieldNamesExpr = Expr.ofList(fieldNames.map(Expr(_)))

        '{
          new zio.blocks.schema.Into[A, B] {
            def into(input: A): Either[zio.blocks.schema.SchemaError, B] =
              try {
                // Extract field values using Java reflection (works on all platforms)
                // Note: This requires the structural type to have actual methods, not just Selectable
                val fieldValues = ${ fieldNamesExpr }.map { fieldName =>
                  // Use Java reflection to invoke the method
                  // This works when the structural type is backed by a real object with methods
                  val method = input.getClass.getMethod(fieldName)
                  method.invoke(input)
                }

                // Use runtime reflection to construct the case class
                val companion      = ${ Ref(targetClass.companionModule).asExpr }
                val companionClass = companion.getClass
                val applyMethod    = companionClass.getMethods.find(_.getName == "apply").get
                val result         = applyMethod.invoke(companion, fieldValues: _*)

                Right(result.asInstanceOf[t].asInstanceOf[B])
              } catch {
                case e: Exception =>
                  Left(
                    zio.blocks.schema.SchemaError.expectationMismatch(
                      Nil,
                      "Failed to convert structural type to " + ${ Expr(target.show) } + ": " + e.getMessage
                    )
                  )
              }
          }
        }
    }
  }

  /**
   * Generate conversion from structural type to structural type.
   */
  private def generateStructuralToStructural[A: Type, B: Type](using
    Quotes
  )(
    source: quotes.reflect.TypeRepr,
    target: quotes.reflect.TypeRepr
  ): Expr[zio.blocks.schema.Into[A, B]] = {
    import quotes.reflect.*

    // Extract required methods from target structural type
    val requiredMethods = extractStructuralMethodsWithTypes(target)

    // Extract available methods from source structural type
    val sourceMethods = extractStructuralMethodsWithTypes(source)

    // Verify all target methods have corresponding methods in source with compatible types
    val missingMethods = requiredMethods.filter { case (methodName, _, methodReturnType) =>
      !sourceMethods.exists { case (sourceMethodName, _, sourceMethodReturnType) =>
        sourceMethodName == methodName && isTypeCompatible(sourceMethodReturnType, methodReturnType)
      }
    }

    if (missingMethods.nonEmpty) {
      val missingNames = missingMethods.map(_._1).mkString(", ")
      report.errorAndAbort(
        s"Cannot convert structural type ${source.show} to ${target.show}. " +
          s"Missing required methods: $missingNames"
      )
    }

    '{
      new zio.blocks.schema.Into[A, B] {
        def into(input: A): Either[zio.blocks.schema.SchemaError, B] =
          try {
            val wrapper =
              ${
                generateAnonymousSelectable[A](
                  'input,
                  requiredMethods.map { case (name, paramCount, _) => (name, paramCount) }
                )
              }
            Right(wrapper.asInstanceOf[B])
          } catch {
            case e: Exception =>
              Left(
                zio.blocks.schema.SchemaError.expectationMismatch(
                  Nil,
                  "Failed to convert structural type " + ${ Expr(source.show) } + " to " + ${
                    Expr(target.show)
                  } + ": " + e.getMessage
                )
              )
          }
      }
    }
  }

  /**
   * Generate an anonymous `scala.reflect.Selectable` that forwards structural
   * method calls to the given `sourceExpr` by emitting a hard-coded dispatch on
   * the method name.
   *
   * Currently supports only zero-argument methods (which matches our test suite
   * usage). If any required method has parameters, derivation aborts with a
   * clear error.
   */
  private def generateAnonymousSelectable[A: Type](using
    Quotes
  )(
    sourceExpr: Expr[A],
    requiredMethods: List[(String, Int)]
  ): Expr[scala.reflect.Selectable] = {
    import quotes.reflect.*

    val (zeroArg, withArgs) = requiredMethods.partition(_._2 == 0)

    if (withArgs.nonEmpty) {
      val names = withArgs.map { case (n, c) => s"$n($c params)" }.mkString(", ")
      report.errorAndAbort(
        s"Structural Into conversion currently supports only methods with zero parameters. " +
          s"Methods with parameters are not supported: $names"
      )
    }

    '{
      new scala.reflect.Selectable {
        private val src: A = $sourceExpr

        def applyDynamic(name: String)(args: Any*): Any =
          if (args.nonEmpty)
            throw new NoSuchMethodException(
              s"Structural method '$$name' with arguments is not supported by generated Selectable wrapper"
            )
          else {
            ${
              val body: Expr[Any] =
                zeroArg.foldRight[Expr[Any]](
                  '{ throw new NoSuchMethodException("Unknown structural member: " + name) }
                ) { case ((mName, _), acc) =>
                  val selected: Expr[Any] =
                    Select.unique('{ src }.asTerm, mName).asExpr
                  '{
                    if (name == ${ Expr(mName) }) $selected
                    else $acc
                  }
                }
              body
            }
          }
      }
    }
  }

  /**
   * Extract methods from a structural type (refinement). Wrapper around
   * extractStructuralMethodsWithTypes for simpler API.
   * @unused
   *   Kept for potential future use or API compatibility
   */
  @annotation.unused
  private def extractStructuralMethods(using Quotes)(tpe: quotes.reflect.TypeRepr): List[MethodInfo] =
    extractStructuralMethodsWithTypes(tpe).map { case (name, paramCount, _) => MethodInfo(name, paramCount) }

  /**
   * Extract methods from a structural type (refinement) with return types.
   * Returns a list of tuples (name, paramCount, returnType) for inline
   * comparison.
   */
  private def extractStructuralMethodsWithTypes(using
    Quotes
  )(tpe: quotes.reflect.TypeRepr): List[(String, Int, quotes.reflect.TypeRepr)] = {
    import quotes.reflect.*

    /**
     * Recursively extract methods from a structural type (refinement). Dealias
     * is called at each recursive step to handle nested type aliases and ensure
     * we properly traverse the "onion" of Refinement nodes.
     */
    def extractFromRefinement(acc: List[(String, Int, TypeRepr)], current: TypeRepr): List[(String, Int, TypeRepr)] =
      current.dealias match {
        case Refinement(parent, name, info) =>
          val methodInfo = info match {
            // MethodType: MethodType(paramNames, paramTypes, returnType)
            // This handles methods with parameters
            case MethodType(_, paramTypes, returnType) =>
              (name, paramTypes.length, returnType)
            // ByNameType: method without parameters represented as => ReturnType
            case ByNameType(returnType) =>
              (name, 0, returnType)
            // In Scala 3, methods without parameters in structural types are represented
            // directly as their return type (not as MethodType)
            // Examples: "def x: Int" is represented as TypeRef pointing to Int, or directly as the type
            // We treat any other TypeRepr as a method without parameters returning that type
            // This is the key fix: methods like "def x: Int" are represented as just "Int"
            case otherTypeRepr =>
              // Treat as method without parameters returning this type
              // This catches TypeRef, AppliedType, and other type representations
              (name, 0, otherTypeRepr)
          }
          // Recursively process parent, ensuring dealias is applied at each step
          extractFromRefinement(methodInfo :: acc, parent)
        case AndType(left, right) =>
          // Handle compound types by processing both sides
          extractFromRefinement(extractFromRefinement(acc, left), right)
        case _ =>
          acc
      }

    extractFromRefinement(Nil, tpe).reverse
  }

  /**
   * Extract fields from a product type (case class). Wrapper around
   * extractProductFieldsWithTypes for simpler API.
   * @unused
   *   Kept for potential future use or API compatibility
   */
  @annotation.unused
  private def extractProductFields(using Quotes)(tpe: quotes.reflect.TypeRepr): List[SimpleFieldInfo] =
    extractProductFieldsWithTypes(tpe).map { case (name, returnType) => SimpleFieldInfo(name, returnType.show) }

  /**
   * Extract fields from a product type (case class) with types. Returns a list
   * of tuples (name, returnType) for inline comparison.
   */
  private def extractProductFieldsWithTypes(using
    Quotes
  )(tpe: quotes.reflect.TypeRepr): List[(String, quotes.reflect.TypeRepr)] = {
    import quotes.reflect.*

    tpe.classSymbol match {
      case Some(classSymbol) if classSymbol.flags.is(Flags.Case) =>
        val constructor = classSymbol.primaryConstructor
        val params      = constructor.paramSymss.flatten.filterNot(_.isTypeParam)
        params.map { param =>
          val fieldType = tpe.memberType(param)
          (param.name, fieldType)
        }.toList
      case _ =>
        Nil
    }
  }

  /**
   * Check if two types are compatible for conversion (allowing
   * widening/narrowing with validation).
   */
  private def isTypeCompatible(using
    Quotes
  )(source: quotes.reflect.TypeRepr, target: quotes.reflect.TypeRepr): Boolean = {
    import quotes.reflect.*

    // Exact match
    if (source =:= target) return true

    // Check if source can be converted to target (using Into logic)
    // For now, we allow same base types (Int/Long, Float/Double, etc.)
    // This is a simplified check - full type coercion would require Into derivation
    val sourceBase = source.dealias
    val targetBase = target.dealias

    // Allow numeric widening/narrowing
    (sourceBase, targetBase) match {
      case (TypeRef(_, sourceName), TypeRef(_, targetName)) =>
        val numericTypes = Set("Byte", "Short", "Int", "Long", "Float", "Double")
        if (numericTypes.contains(sourceName) && numericTypes.contains(targetName)) {
          return true
        }
      case _ => ()
    }

    // Allow Option widening
    (sourceBase, targetBase) match {
      case (AppliedType(sourceOpt, sourceArgs), AppliedType(targetOpt, targetArgs)) =>
        if (sourceOpt.typeSymbol.name == "Option" && targetOpt.typeSymbol.name == "Option") {
          return isTypeCompatible(sourceArgs.head, targetArgs.head)
        }
      case _ => ()
    }

    false
  }

  // Field info without TypeRepr to avoid Quotes context issues
  private case class SimpleFieldInfo(name: String, tpeString: String)

  private case class MethodInfo(name: String, paramCount: Int)

  private def isStructuralType(using Quotes)(tpe: quotes.reflect.TypeRepr): Boolean = {
    import quotes.reflect.*

    // Check if type is a refinement type (structural type)
    tpe.dealias match {
      case Refinement(_, _, _)                => true
      case AndType(base, Refinement(_, _, _)) =>
        base <:< TypeRepr.of[Selectable] || base =:= TypeRepr.of[Selectable]
      case AndType(left, right) =>
        isStructuralType(left) || isStructuralType(right)
      case _ => false
    }
  }

  private def isProductType(using Quotes)(tpe: quotes.reflect.TypeRepr): Boolean = {
    import quotes.reflect.*
    tpe.classSymbol.exists(_.flags.is(Flags.Case))
  }

}
