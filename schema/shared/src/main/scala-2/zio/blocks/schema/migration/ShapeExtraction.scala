package zio.blocks.schema.migration

import scala.language.experimental.macros
import scala.reflect.macros.blackbox

import TypeLevel._

/**
 * Compile-time shape extraction for migration validation.
 *
 * Provides typeclasses for extracting type structure at compile time:
 *   - ShapeTree[A]: Hierarchical shape representation
 *   - MigrationPaths[A, B]: Computed diff between two types
 */
object ShapeExtraction {

  /**
   * Typeclass for extracting the complete shape tree of a type at compile time.
   * Returns a hierarchical ShapeNode representing the type's structure.
   *
   *   - Product types (case classes) become RecordNode with field shapes
   *   - Sum types (sealed traits) become SealedNode with case shapes
   *   - Either[L, R] becomes SealedNode with "Left" -> L's shape, "Right" ->
   *     R's shape
   *   - Wrapped[A] becomes WrappedNode with A's shape
   *   - Option[A] becomes OptionNode with A's shape
   *   - List/Vector/Set[A] become SeqNode with A's shape
   *   - Map[K, V] becomes MapNode with K's and V's shapes
   *   - Primitives become PrimitiveNode
   */
  private[migration] sealed trait ShapeTree[A] {
    def tree: ShapeNode
  }

  object ShapeTree {

    /** Concrete implementation of ShapeTree. */
    final class Impl[A](val tree: ShapeNode) extends ShapeTree[A]

    /** Implicit derivation macro for ShapeTree. */
    implicit def derived[A]: ShapeTree[A] = macro ShapeExtractionMacros.shapeTreeDerivedImpl[A]
  }

  /**
   * Typeclass for computing migration paths between two types at compile time.
   * Computes the structural diff using ShapeTree and TreeDiff, exposing the
   * removed and added paths as TList type members with structured tuple paths.
   *
   * {{{
   * case class V1(name: String, age: Int)
   * case class V2(name: String, email: String)
   *
   * // MigrationPaths[V1, V2].Removed contains (("field", "age"),)
   * // MigrationPaths[V1, V2].Added contains (("field", "email"),)
   * }}}
   */
  private[migration] sealed trait MigrationPaths[A, B] {
    type Removed <: TList
    type Added <: TList
  }

  object MigrationPaths {

    /** Concrete implementation with computed Removed and Added types. */
    final class Impl[A, B, R <: TList, Add <: TList] extends MigrationPaths[A, B] {
      type Removed = R
      type Added   = Add
    }

    /** Auxiliary type alias for dependent type extraction. */
    type Aux[A, B, R <: TList, Add <: TList] = MigrationPaths[A, B] { type Removed = R; type Added = Add }

    /** Implicit derivation macro for MigrationPaths. */
    implicit def derived[A, B]: MigrationPaths[A, B] = macro ShapeExtractionMacros.migrationPathsDerivedImpl[A, B]
  }

  /**
   * Extract the shape tree from a type at compile time.
   *
   * Returns a hierarchical ShapeNode representing the type's complete
   * structure.
   *
   * {{{
   * case class Address(street: String, city: String)
   * case class Person(name: String, address: Address)
   *
   * extractShapeTree[Person]
   * // Returns: RecordNode(Map(
   * //   "name" -> PrimitiveNode,
   * //   "address" -> RecordNode(Map(
   * //     "street" -> PrimitiveNode,
   * //     "city" -> PrimitiveNode
   * //   ))
   * // ))
   * }}}
   *
   * For recursive types, produces a compile-time error.
   */
  def extractShapeTree[A]: ShapeNode = macro ShapeExtractionMacros.extractShapeTreeMacro[A]
}

private[migration] object ShapeExtractionMacros {

  def extractShapeTreeMacro[A: c.WeakTypeTag](c: blackbox.Context): c.Expr[ShapeNode] = {
    val helper = new ShapeExtractionHelper[c.type](c)
    helper.extractShapeTreeImpl[A]
  }

  def shapeTreeDerivedImpl[A: c.WeakTypeTag](c: blackbox.Context): c.Expr[ShapeExtraction.ShapeTree[A]] = {
    val helper = new ShapeExtractionHelper[c.type](c)
    helper.shapeTreeDerived[A]
  }

  def migrationPathsDerivedImpl[A: c.WeakTypeTag, B: c.WeakTypeTag](
    c: blackbox.Context
  ): c.Expr[ShapeExtraction.MigrationPaths[A, B]] = {
    val helper = new ShapeExtractionHelper[c.type](c)
    helper.migrationPathsDerived[A, B]
  }
}

private[migration] class ShapeExtractionHelper[C <: blackbox.Context](val c: C) extends MacroHelpers {
  import c.universe._

  /**
   * Convert a Segment to a type-level representation.
   *   - Field("name") -> ("field", "name")
   *   - Case("name") -> ("case", "name")
   *   - Element -> "element"
   *   - Key -> "key"
   *   - Value -> "value"
   *   - Wrapped -> "wrapped"
   */
  private def segmentToType(segment: Segment): c.Type = {
    val tuple2Type = typeOf[(_, _)].typeConstructor

    segment match {
      case Segment.Field(name) =>
        val fieldLit = c.internal.constantType(Constant("field"))
        val nameLit  = c.internal.constantType(Constant(name))
        appliedType(tuple2Type, List(fieldLit, nameLit))

      case Segment.Case(name) =>
        val caseLit = c.internal.constantType(Constant("case"))
        val nameLit = c.internal.constantType(Constant(name))
        appliedType(tuple2Type, List(caseLit, nameLit))

      case Segment.Element =>
        c.internal.constantType(Constant("element"))

      case Segment.Key =>
        c.internal.constantType(Constant("key"))

      case Segment.Value =>
        c.internal.constantType(Constant("value"))

      case Segment.Wrapped =>
        c.internal.constantType(Constant("wrapped"))
    }
  }

  /**
   * Convert a Path (List[Segment]) to a type-level tuple representation.
   * List(Field("a"), Field("b")) -> (("field", "a"), ("field", "b"))
   */
  private def pathToTupleType(path: List[Segment]): c.Type =
    if (path.isEmpty) {
      typeOf[Unit] // EmptyTuple equivalent in Scala 2
    } else {
      val tuple2Type = typeOf[(_, _)].typeConstructor

      // Create segment types
      val segmentTypes = path.map(segmentToType)

      // Build nested tuple from right to left
      segmentTypes.reduceRight { (seg, acc) =>
        appliedType(tuple2Type, List(seg, acc))
      }
    }

  /**
   * Convert a list of Paths to a TList of tuple types.
   */
  private def pathsToTupleListType(paths: List[List[Segment]]): c.Type = {
    val tnilType  = typeOf[TNil]
    val tconsType = typeOf[TCons[_, _]].typeConstructor

    paths.foldRight(tnilType) { (path, acc) =>
      val pathType = pathToTupleType(path)
      appliedType(tconsType, List(pathType, acc))
    }
  }

  /**
   * Extract the shape tree from a type and return it as an Expr.
   */
  def extractShapeTreeImpl[A: c.WeakTypeTag]: c.Expr[ShapeNode] = {
    val tpe  = weakTypeOf[A].dealias
    val tree = extractShapeTree(tpe, Set.empty, "Shape tree extraction")
    shapeNodeToExpr(tree)
  }

  /**
   * Derive a ShapeTree typeclass instance for type A.
   */
  def shapeTreeDerived[A: c.WeakTypeTag]: c.Expr[ShapeExtraction.ShapeTree[A]] = {
    val tpe      = weakTypeOf[A].dealias
    val tree     = extractShapeTree(tpe, Set.empty, "ShapeTree derivation")
    val treeExpr = shapeNodeToExpr(tree)

    c.Expr[ShapeExtraction.ShapeTree[A]](
      q"new _root_.zio.blocks.schema.migration.ShapeExtraction.ShapeTree.Impl[$tpe]($treeExpr)"
    )
  }

  /**
   * Derive a MigrationPaths typeclass instance for types A and B. Computes the
   * structural diff using TreeDiff and exposes removed/added paths as TList
   * types.
   */
  def migrationPathsDerived[A: c.WeakTypeTag, B: c.WeakTypeTag]: c.Expr[ShapeExtraction.MigrationPaths[A, B]] = {
    val aType = weakTypeOf[A].dealias
    val bType = weakTypeOf[B].dealias

    // Extract shape trees for both types
    val treeA = extractShapeTree(aType, Set.empty, "MigrationPaths derivation")
    val treeB = extractShapeTree(bType, Set.empty, "MigrationPaths derivation")

    // Compute diff using TreeDiff
    val (removed, added) = TreeDiff.diff(treeA, treeB)

    // Sort paths by their string representation for determinism
    val removedSorted = removed.sortBy(Path.render)
    val addedSorted   = added.sortBy(Path.render)

    // Build TList types from the paths as structured tuples
    val removedType = pathsToTupleListType(removedSorted)
    val addedType   = pathsToTupleListType(addedSorted)

    // Create the instance with correct type
    val implClass  = typeOf[ShapeExtraction.MigrationPaths.Impl[_, _, _, _]].typeSymbol
    val resultType = appliedType(implClass, List(aType, bType, removedType, addedType))

    c.Expr[ShapeExtraction.MigrationPaths[A, B]](q"new $resultType")
  }

  /**
   * Convert a ShapeNode to an Expr at compile time.
   */
  private def shapeNodeToExpr(node: ShapeNode): c.Expr[ShapeNode] =
    node match {
      case ShapeNode.PrimitiveNode =>
        c.Expr[ShapeNode](q"_root_.zio.blocks.schema.migration.ShapeNode.PrimitiveNode")

      case ShapeNode.RecordNode(fields) =>
        val fieldEntries = fields.toList.map { case (name, child) =>
          val childExpr = shapeNodeToExpr(child)
          q"($name, $childExpr)"
        }
        c.Expr[ShapeNode](
          q"_root_.zio.blocks.schema.migration.ShapeNode.RecordNode(_root_.scala.collection.immutable.Map(..$fieldEntries))"
        )

      case ShapeNode.SealedNode(cases) =>
        val caseEntries = cases.toList.map { case (name, child) =>
          val childExpr = shapeNodeToExpr(child)
          q"($name, $childExpr)"
        }
        c.Expr[ShapeNode](
          q"_root_.zio.blocks.schema.migration.ShapeNode.SealedNode(_root_.scala.collection.immutable.Map(..$caseEntries))"
        )

      case ShapeNode.SeqNode(element) =>
        val elementExpr = shapeNodeToExpr(element)
        c.Expr[ShapeNode](q"_root_.zio.blocks.schema.migration.ShapeNode.SeqNode($elementExpr)")

      case ShapeNode.OptionNode(element) =>
        val elementExpr = shapeNodeToExpr(element)
        c.Expr[ShapeNode](q"_root_.zio.blocks.schema.migration.ShapeNode.OptionNode($elementExpr)")

      case ShapeNode.MapNode(key, value) =>
        val keyExpr   = shapeNodeToExpr(key)
        val valueExpr = shapeNodeToExpr(value)
        c.Expr[ShapeNode](q"_root_.zio.blocks.schema.migration.ShapeNode.MapNode($keyExpr, $valueExpr)")

      case ShapeNode.WrappedNode(inner) =>
        val innerExpr = shapeNodeToExpr(inner)
        c.Expr[ShapeNode](q"_root_.zio.blocks.schema.migration.ShapeNode.WrappedNode($innerExpr)")
    }
}
