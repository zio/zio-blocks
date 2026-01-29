package zio.blocks.schema.migration

import scala.language.experimental.macros
import scala.reflect.macros.blackbox

import TypeLevel._

/**
 * Compile-time shape extraction for migration validation.
 *
 * Extracts the complete structure of a type at compile time, including:
 *   - All field paths (including nested paths like "address.street")
 *   - Sealed trait case names
 *   - Field paths within each case
 *
 * Also provides typeclasses that extract field paths and case names from types
 * at compile time, encoding them as TList type members for type-level
 * validation.
 */
object ShapeExtraction {

  /**
   * Typeclass for extracting all field paths (including nested paths) from a
   * type at compile time. The Paths type member contains the paths as a TList
   * of string literal types.
   *
   * For nested case classes, returns all dot-separated paths:
   * {{{
   * case class Address(street: String, city: String)
   * case class Person(name: String, address: Address)
   *
   * FieldPaths[Person].Paths =:= "address" :: "address.city" :: "address.street" :: "name" :: TNil
   * }}}
   */
  sealed trait FieldPaths[A] {
    type Paths <: TList
  }

  object FieldPaths {

    /** Concrete implementation of FieldPaths with the Paths type member. */
    class Impl[A, P <: TList] extends FieldPaths[A] {
      type Paths = P
    }

    /** Auxiliary type alias for dependent type extraction. */
    type Aux[A, P <: TList] = FieldPaths[A] { type Paths = P }

    /** Implicit derivation macro for FieldPaths. */
    implicit def derived[A]: FieldPaths[A] = macro ShapeExtractionMacros.fieldPathsDerivedImpl[A]
  }

  /**
   * Typeclass for extracting case names from a sealed trait at compile time.
   * The Cases type member contains the case names as a TList of string literal
   * types with "case:" prefix.
   *
   * For sealed traits:
   * {{{
   * sealed trait Result
   * case class Success(value: Int) extends Result
   * case class Failure(error: String) extends Result
   *
   * CasePaths[Result].Cases =:= "case:Failure" :: "case:Success" :: TNil
   * }}}
   *
   * For non-sealed types, Cases is TNil.
   */
  sealed trait CasePaths[A] {
    type Cases <: TList
  }

  object CasePaths {

    /** Concrete implementation of CasePaths with the Cases type member. */
    class Impl[A, C <: TList] extends CasePaths[A] {
      type Cases = C
    }

    /** Auxiliary type alias for dependent type extraction. */
    type Aux[A, C <: TList] = CasePaths[A] { type Cases = C }

    /** Implicit derivation macro for CasePaths. */
    implicit def derived[A]: CasePaths[A] = macro ShapeExtractionMacros.casePathsDerivedImpl[A]
  }

  /**
   * Represents the complete shape of a type.
   *
   * @param fieldPaths
   *   All dot-separated field paths in sorted order (e.g., List("address",
   *   "address.city", "address.street", "name"))
   * @param caseNames
   *   Case names for sealed traits in sorted order (e.g., List("Failure",
   *   "Success"))
   * @param caseFieldPaths
   *   Field paths for each case, keyed by case name (e.g., Map("Success" ->
   *   List("value"), "Failure" -> List("error")))
   */
  case class Shape(
    fieldPaths: List[String],
    caseNames: List[String],
    caseFieldPaths: Map[String, List[String]]
  )

  /**
   * Extract all field paths from a type at compile time.
   *
   * For nested case classes, returns all dot-separated paths:
   * {{{
   * case class Address(street: String, city: String)
   * case class Person(name: String, address: Address)
   *
   * extractFieldPaths[Person]
   * // Returns: List("address", "address.city", "address.street", "name")
   * }}}
   *
   * For recursive types, produces a compile-time error.
   */
  def extractFieldPaths[A]: List[String] = macro ShapeExtractionMacros.extractFieldPathsMacro[A]

  /**
   * Extract case names from a sealed trait at compile time.
   *
   * {{{
   * sealed trait Result
   * case class Success(value: Int) extends Result
   * case class Failure(error: String) extends Result
   *
   * extractCaseNames[Result]
   * // Returns: List("Failure", "Success")
   * }}}
   *
   * For non-sealed types, returns an empty list.
   */
  def extractCaseNames[A]: List[String] = macro ShapeExtractionMacros.extractCaseNamesMacro[A]

  /**
   * Extract the complete shape of a type at compile time.
   *
   * Combines field paths, case names, and case field paths into a single Shape
   * object. For sealed traits, extracts both the case names and the field paths
   * within each case.
   *
   * {{{
   * sealed trait Payment
   * case class Card(number: String, expiry: String) extends Payment
   * case class Cash(amount: Int) extends Payment
   *
   * extractShape[Payment]
   * // Returns: Shape(
   * //   fieldPaths = List(),
   * //   caseNames = List("Card", "Cash"),
   * //   caseFieldPaths = Map(
   * //     "Card" -> List("expiry", "number"),
   * //     "Cash" -> List("amount")
   * //   )
   * // )
   * }}}
   */
  def extractShape[A]: Shape = macro ShapeExtractionMacros.extractShapeMacro[A]
}

private[migration] object ShapeExtractionMacros {

  def extractFieldPathsMacro[A: c.WeakTypeTag](c: blackbox.Context): c.Expr[List[String]] = {
    val helper = new ShapeExtractionHelper[c.type](c)
    helper.extractFieldPaths[A]
  }

  def extractCaseNamesMacro[A: c.WeakTypeTag](c: blackbox.Context): c.Expr[List[String]] = {
    val helper = new ShapeExtractionHelper[c.type](c)
    helper.extractCaseNames[A]
  }

  def extractShapeMacro[A: c.WeakTypeTag](c: blackbox.Context): c.Tree = {
    val helper = new ShapeExtractionHelper[c.type](c)
    helper.extractShape[A]
  }

  def fieldPathsDerivedImpl[A: c.WeakTypeTag](c: blackbox.Context): c.Expr[ShapeExtraction.FieldPaths[A]] = {
    val helper = new ShapeExtractionHelper[c.type](c)
    helper.fieldPathsDerived[A]
  }

  def casePathsDerivedImpl[A: c.WeakTypeTag](c: blackbox.Context): c.Expr[ShapeExtraction.CasePaths[A]] = {
    val helper = new ShapeExtractionHelper[c.type](c)
    helper.casePathsDerived[A]
  }
}

private[migration] class ShapeExtractionHelper[C <: blackbox.Context](val c: C) extends MacroHelpers {
  import c.universe._

  def extractFieldPaths[A: c.WeakTypeTag]: c.Expr[List[String]] = {
    val tpe    = weakTypeOf[A].dealias
    val paths  = extractFieldPathsFromType(tpe, "", Set.empty, "Migration shape extraction")
    val sorted = paths.sorted

    c.Expr[List[String]](q"${sorted.toList}")
  }

  def extractCaseNames[A: c.WeakTypeTag]: c.Expr[List[String]] = {
    val tpe   = weakTypeOf[A].dealias
    val names = extractCaseNamesFromType(tpe)

    c.Expr[List[String]](q"${names.sorted.toList}")
  }

  def extractShape[A: c.WeakTypeTag]: c.Tree = {
    val tpe = weakTypeOf[A].dealias

    // Extract field paths (for product types)
    val fieldPaths = extractFieldPathsFromType(tpe, "", Set.empty, "Migration shape extraction").sorted.toList

    // Extract case names (for sum types)
    val caseNames = extractCaseNamesFromType(tpe).sorted.toList

    // Extract field paths for each case
    val caseFieldPaths: Map[String, List[String]] =
      if (isSealedTrait(tpe)) {
        val subTypes = directSubTypes(tpe)
        subTypes.map { subTpe =>
          val caseName  = getCaseName(subTpe)
          val casePaths = extractFieldPathsFromType(subTpe, "", Set.empty, "Migration shape extraction").sorted.toList
          caseName -> casePaths
        }.toMap
      } else {
        Map.empty
      }

    // Build literal map entries for caseFieldPaths
    val mapEntries = caseFieldPaths.toList.map { case (k, v) =>
      q"($k, ${v.toList})"
    }

    q"_root_.zio.blocks.schema.migration.ShapeExtraction.Shape($fieldPaths, $caseNames, _root_.scala.collection.immutable.Map(..$mapEntries))"
  }

  def fieldPathsDerived[A: c.WeakTypeTag]: c.Expr[ShapeExtraction.FieldPaths[A]] = {
    val tpe   = weakTypeOf[A].dealias
    val paths = extractFieldPathsFromType(tpe, "", Set.empty).sorted

    // Build TList type from paths
    val tlistType = pathsToTListType(paths)

    // Create the instance with correct type
    val implClass  = typeOf[ShapeExtraction.FieldPaths.Impl[_, _]].typeSymbol
    val resultType = appliedType(implClass, List(tpe, tlistType))

    c.Expr[ShapeExtraction.FieldPaths[A]](q"new $resultType")
  }

  def casePathsDerived[A: c.WeakTypeTag]: c.Expr[ShapeExtraction.CasePaths[A]] = {
    val tpe       = weakTypeOf[A].dealias
    val caseNames = extractCaseNamesFromType(tpe).sorted.map(name => s"case:$name")

    // Build TList type from case names
    val tlistType = pathsToTListType(caseNames)

    // Create the instance with correct type
    val implClass  = typeOf[ShapeExtraction.CasePaths.Impl[_, _]].typeSymbol
    val resultType = appliedType(implClass, List(tpe, tlistType))

    c.Expr[ShapeExtraction.CasePaths[A]](q"new $resultType")
  }

  /**
   * Convert a list of path strings to a TList type. List("a", "b", "c") => "a"
   * :: "b" :: "c" :: TNil
   */
  private def pathsToTListType(paths: List[String]): c.Type = {
    val tnilType  = typeOf[TNil]
    val tconsType = typeOf[TCons[_, _]].typeConstructor

    paths.foldRight(tnilType) { (path, acc) =>
      val pathType = c.internal.constantType(Constant(path))
      appliedType(tconsType, List(pathType, acc))
    }
  }
}
