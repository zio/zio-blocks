package zio.blocks.schema.migration

/**
 * Hierarchical representation of a type's shape.
 *
 * ShapeNode provides a tree-based view of a type's structure, enabling:
 *   - Structural comparison between types (diff)
 *   - Path-based navigation
 *   - Compile-time validation with precise error messages
 *
 * Each node represents a structural element:
 *   - RecordNode: Product types (case classes) with named fields
 *   - SealedNode: Sum types (sealed traits/enums) with named cases
 *   - SeqNode: Sequence containers (List, Vector, Set, etc.)
 *   - OptionNode: Optional values
 *   - MapNode: Key-value mappings
 *   - PrimitiveNode: Leaf types (String, Int, etc.)
 */
sealed trait ShapeNode

object ShapeNode {

  /** Product type with named fields mapping to their shapes. */
  case class RecordNode(fields: Map[String, ShapeNode]) extends ShapeNode

  /** Sum type with named cases mapping to their shapes. */
  case class SealedNode(cases: Map[String, ShapeNode]) extends ShapeNode

  /** Sequence container with element shape. */
  case class SeqNode(element: ShapeNode) extends ShapeNode

  /** Optional value container with element shape. */
  case class OptionNode(element: ShapeNode) extends ShapeNode

  /** Map container with key and value shapes. */
  case class MapNode(key: ShapeNode, value: ShapeNode) extends ShapeNode

  /** Leaf node for primitive types. */
  case object PrimitiveNode extends ShapeNode
}

/**
 * Path segment for navigating within a ShapeNode tree.
 *
 * Segments represent the different ways to navigate into a type's structure:
 *   - Field: Named field access in a record
 *   - Case: Named case in a sealed trait/enum
 *   - Element: Element access in a sequence or option
 *   - Key: Key access in a map
 *   - Value: Value access in a map
 *   - Wrapped: Access to wrapped value in a newtype
 */
sealed trait Segment {
  def render: String = this match {
    case Segment.Field(name) => s".$name"
    case Segment.Case(name)  => s"[case:$name]"
    case Segment.Element     => "[element]"
    case Segment.Key         => "[key]"
    case Segment.Value       => "[value]"
    case Segment.Wrapped     => "[wrapped]"
  }
}

object Segment {
  case class Field(name: String) extends Segment
  case class Case(name: String)  extends Segment
  case object Element            extends Segment
  case object Key                extends Segment
  case object Value              extends Segment
  case object Wrapped            extends Segment
}

/**
 * A path is a sequence of segments representing a location within a ShapeNode
 * tree.
 */
object Path {

  /**
   * Render a path as a human-readable string (e.g., ".address.city" or
   * "[element].name").
   */
  def render(path: List[Segment]): String =
    if (path.isEmpty) "<root>"
    else path.map(_.render).mkString
}

/**
 * Runtime diff computation for ShapeNode trees.
 *
 * TreeDiff compares two ShapeNode trees and identifies structural differences:
 *   - Removed paths: Present in source but not in target
 *   - Added paths: Present in target but not in source
 *
 * Type changes (same path, different structure) appear in BOTH lists.
 */
object TreeDiff {

  /**
   * Compute the difference between source and target shape trees.
   *
   * @param source
   *   The source ShapeNode tree
   * @param target
   *   The target ShapeNode tree
   * @return
   *   A tuple of (removed paths, added paths)
   *
   * Examples:
   *   - Field removed: path appears in removed list
   *   - Field added: path appears in added list
   *   - Field type changed: path appears in BOTH lists
   *   - Nested changes: paths include full prefix
   */
  def diff(source: ShapeNode, target: ShapeNode): (List[List[Segment]], List[List[Segment]]) =
    diffImpl(source, target, Nil)

  private def diffImpl(
    source: ShapeNode,
    target: ShapeNode,
    prefix: List[Segment]
  ): (List[List[Segment]], List[List[Segment]]) =
    (source, target) match {
      // Both primitives - identical, no diff
      case (ShapeNode.PrimitiveNode, ShapeNode.PrimitiveNode) =>
        (Nil, Nil)

      // Both records - compare fields
      case (ShapeNode.RecordNode(sourceFields), ShapeNode.RecordNode(targetFields)) =>
        diffMaps(sourceFields, targetFields, prefix, Segment.Field.apply)

      // Both sealed - compare cases
      case (ShapeNode.SealedNode(sourceCases), ShapeNode.SealedNode(targetCases)) =>
        diffMaps(sourceCases, targetCases, prefix, Segment.Case.apply)

      // Both sequences - compare elements
      case (ShapeNode.SeqNode(sourceElem), ShapeNode.SeqNode(targetElem)) =>
        diffImpl(sourceElem, targetElem, prefix :+ Segment.Element)

      // Both options - compare elements
      case (ShapeNode.OptionNode(sourceElem), ShapeNode.OptionNode(targetElem)) =>
        diffImpl(sourceElem, targetElem, prefix :+ Segment.Element)

      // Both maps - compare keys and values
      case (ShapeNode.MapNode(sourceKey, sourceVal), ShapeNode.MapNode(targetKey, targetVal)) =>
        val (keyRemoved, keyAdded) = diffImpl(sourceKey, targetKey, prefix :+ Segment.Key)
        val (valRemoved, valAdded) = diffImpl(sourceVal, targetVal, prefix :+ Segment.Value)
        (keyRemoved ++ valRemoved, keyAdded ++ valAdded)

      // Different node types - type changed, path is in both removed and added
      case _ =>
        val path = if (prefix.isEmpty) List(Nil) else List(prefix)
        (path, path)
    }

  private def diffMaps(
    sourceMap: Map[String, ShapeNode],
    targetMap: Map[String, ShapeNode],
    prefix: List[Segment],
    mkSegment: String => Segment
  ): (List[List[Segment]], List[List[Segment]]) = {
    val allKeys = (sourceMap.keySet ++ targetMap.keySet).toList.sorted

    allKeys.foldLeft((List.empty[List[Segment]], List.empty[List[Segment]])) { case ((removedAcc, addedAcc), key) =>
      val segment = mkSegment(key)
      val path    = prefix :+ segment

      (sourceMap.get(key), targetMap.get(key)) match {
        // Only in source - removed
        case (Some(_), None) =>
          (path :: removedAcc, addedAcc)

        // Only in target - added
        case (None, Some(_)) =>
          (removedAcc, path :: addedAcc)

        // In both - recurse to find nested diffs
        case (Some(sourceNode), Some(targetNode)) =>
          val (nestedRemoved, nestedAdded) = diffImpl(sourceNode, targetNode, path)
          (nestedRemoved ++ removedAcc, nestedAdded ++ addedAcc)

        // Neither (shouldn't happen given allKeys construction)
        case (None, None) =>
          (removedAcc, addedAcc)
      }
    }
  }
}
