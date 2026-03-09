package zio.blocks.schema.xml

import zio.blocks.chunk.Chunk
import zio.blocks.schema.DynamicOptic

/**
 * An immutable patch for XML modification operations.
 *
 * XmlPatch provides a composable way to modify XML documents with operations
 * similar to JSON Patch but tailored for XML's structure. Patches can be
 * composed using `++` and applied to XML values using `apply`.
 *
 * @param ops
 *   The sequence of patch operations to apply
 */
final case class XmlPatch(ops: Chunk[XmlPatch.Op]) {

  /**
   * Applies this patch to an XML value.
   *
   * @param xml
   *   The XML value to patch
   * @return
   *   Either an error or the patched XML value
   */
  def apply(xml: Xml): Either[XmlError, Xml] = {
    var current = xml
    val len     = ops.length
    var idx     = 0
    while (idx < len) {
      val op = ops(idx)
      XmlPatch.applyOp(current, op.path.nodes, op.operation) match {
        case Right(updated) => current = updated
        case l              => return l
      }
      idx += 1
    }
    new Right(current)
  }

  /**
   * Composes two patches. The result applies this patch first, then that patch.
   *
   * @param that
   *   The patch to apply after this one
   * @return
   *   A new patch that applies both patches in sequence
   */
  def ++(that: XmlPatch): XmlPatch = new XmlPatch(ops ++ that.ops)

  def isEmpty: Boolean = ops.isEmpty
}

object XmlPatch {

  /** Empty patch - identity element for composition. */
  val empty: XmlPatch = new XmlPatch(Chunk.empty)

  /**
   * Position enum for Add operation - where to insert the new content relative
   * to the target.
   */
  sealed trait Position

  object Position {

    /** Insert before the target element. */
    case object Before extends Position

    /** Insert after the target element. */
    case object After extends Position

    /** Insert as the first child of the target element. */
    case object PrependChild extends Position

    /** Insert as the last child of the target element. */
    case object AppendChild extends Position
  }

  /**
   * Sealed trait representing a single patch operation.
   */
  sealed trait Operation

  object Operation {

    /**
     * Add an element at the specified position relative to the target.
     *
     * @param content
     *   The XML content to add
     * @param position
     *   Where to insert the content (Before, After, PrependChild, AppendChild)
     */
    final case class Add(content: Xml, position: Position) extends Operation

    /**
     * Remove the element at the target path.
     */
    case object Remove extends Operation

    /**
     * Replace the element at the target path with new content.
     *
     * @param content
     *   The replacement XML content
     */
    final case class Replace(content: Xml) extends Operation

    /**
     * Set an attribute on the target element.
     *
     * @param name
     *   The attribute name
     * @param value
     *   The attribute value
     */
    final case class SetAttribute(name: String, value: String) extends Operation

    /**
     * Remove an attribute from the target element.
     *
     * @param name
     *   The attribute name to remove
     */
    final case class RemoveAttribute(name: String) extends Operation
  }

  /**
   * A single patch operation paired with the path to apply it at.
   *
   * @param path
   *   The path indicating where to apply the operation
   * @param operation
   *   The operation to apply at the path
   */
  final case class Op(path: DynamicOptic, operation: Operation)

  // ───────────────────────────────────────────────────────────────────────
  // Factory methods for creating patches
  // ───────────────────────────────────────────────────────────────────────

  /**
   * Creates a patch that adds content at the specified position.
   *
   * @param path
   *   The path to the target element
   * @param content
   *   The content to add
   * @param position
   *   Where to insert the content
   * @return
   *   A new patch with the add operation
   */
  def add(path: DynamicOptic, content: Xml, position: Position): XmlPatch =
    new XmlPatch(Chunk.single(new Op(path, Operation.Add(content, position))))

  /**
   * Creates a patch that removes the element at the path.
   *
   * @param path
   *   The path to the element to remove
   * @return
   *   A new patch with the remove operation
   */
  def remove(path: DynamicOptic): XmlPatch =
    new XmlPatch(Chunk.single(new Op(path, Operation.Remove)))

  /**
   * Creates a patch that replaces the element at the path.
   *
   * @param path
   *   The path to the element to replace
   * @param content
   *   The replacement content
   * @return
   *   A new patch with the replace operation
   */
  def replace(path: DynamicOptic, content: Xml): XmlPatch =
    new XmlPatch(Chunk.single(new Op(path, Operation.Replace(content))))

  /**
   * Creates a patch that sets an attribute on the target element.
   *
   * @param path
   *   The path to the target element
   * @param name
   *   The attribute name
   * @param value
   *   The attribute value
   * @return
   *   A new patch with the set attribute operation
   */
  def setAttribute(path: DynamicOptic, name: String, value: String): XmlPatch =
    new XmlPatch(Chunk.single(new Op(path, Operation.SetAttribute(name, value))))

  /**
   * Creates a patch that removes an attribute from the target element.
   *
   * @param path
   *   The path to the target element
   * @param name
   *   The attribute name to remove
   * @return
   *   A new patch with the remove attribute operation
   */
  def removeAttribute(path: DynamicOptic, name: String): XmlPatch =
    new XmlPatch(Chunk.single(new Op(path, Operation.RemoveAttribute(name))))

  // ───────────────────────────────────────────────────────────────────────
  // Apply Implementation
  // ───────────────────────────────────────────────────────────────────────

  /**
   * Apply a single operation at a path within a value.
   */
  private def applyOp(
    value: Xml,
    path: IndexedSeq[DynamicOptic.Node],
    operation: Operation
  ): Either[XmlError, Xml] =
    if (path.isEmpty) applyOperation(value, operation, Nil)
    else navigateAndApply(value, path, 0, operation, Nil)

  /**
   * Navigate to the target location and apply the operation. Uses a recursive
   * approach that rebuilds the structure on the way back.
   */
  private[this] def navigateAndApply(
    value: Xml,
    path: IndexedSeq[DynamicOptic.Node],
    pathIdx: Int,
    operation: Operation,
    trace: List[DynamicOptic.Node]
  ): Either[XmlError, Xml] = {
    val node   = path(pathIdx)
    val isLast = pathIdx == path.length - 1
    node match {
      case f: DynamicOptic.Node.Field =>
        val name = f.name
        value match {
          case elem: Xml.Element =>
            val children = elem.children
            val childIdx = children.indexWhere {
              case e: Xml.Element => e.name.localName == name
              case _              => false
            }
            if (childIdx < 0) new Left(XmlError.patchError(s"Element '$name' not found").atSpan(f))
            else {
              val newTrace = f :: trace
              if (isLast) {
                operation match {
                  case Operation.Add(content, Position.Before) =>
                    new Right(elem.copy(children = children.take(childIdx) ++ (content +: children.drop(childIdx))))
                  case Operation.Add(content, Position.After) =>
                    new Right(
                      elem.copy(children = children.take(childIdx + 1) ++ (content +: children.drop(childIdx + 1)))
                    )
                  case Operation.Remove =>
                    new Right(elem.copy(children = children.take(childIdx) ++ children.drop(childIdx + 1)))
                  case _ =>
                    applyOperation(children(childIdx), operation, newTrace) match {
                      case Right(newChild) => new Right(elem.copy(children = children.updated(childIdx, newChild)))
                      case l               => l
                    }
                }
              } else {
                navigateAndApply(children(childIdx), path, pathIdx + 1, operation, newTrace) match {
                  case Right(newChild) => new Right(elem.copy(children = children.updated(childIdx, newChild)))
                  case l               => l
                }
              }
            }
          case _ => new Left(XmlError.patchError(s"Expected Element but got ${value.xmlType}"))
        }
      case ai: DynamicOptic.Node.AtIndex =>
        val index = ai.index
        value match {
          case elem: Xml.Element =>
            val children = elem.children
            if (index < 0 || index >= children.length) {
              new Left(XmlError.patchError(s"Index $index out of bounds for element with ${children.length} children"))
            } else {
              val newTrace = ai :: trace
              if (isLast) {
                operation match {
                  case Operation.Add(content, Position.Before) =>
                    new Right(elem.copy(children = children.take(index) ++ (content +: children.drop(index))))
                  case Operation.Add(content, Position.After) =>
                    new Right(elem.copy(children = children.take(index + 1) ++ (content +: children.drop(index + 1))))
                  case Operation.Remove =>
                    new Right(elem.copy(children = children.take(index) ++ children.drop(index + 1)))
                  case _ =>
                    applyOperation(children(index), operation, newTrace) match {
                      case Right(newChild) => new Right(elem.copy(children = children.updated(index, newChild)))
                      case l               => l
                    }
                }
              } else {
                navigateAndApply(children(index), path, pathIdx + 1, operation, newTrace) match {
                  case Right(newChild) => new Right(elem.copy(children = children.updated(index, newChild)))
                  case l               => l
                }
              }
            }
          case _ => new Left(XmlError.patchError(s"Expected Element but got ${value.xmlType}"))
        }
      case _ => new Left(XmlError.patchError(s"Unsupported path node: $node"))
    }
  }

  /**
   * Apply an operation to a value (at the current location).
   */
  private[this] def applyOperation(
    value: Xml,
    operation: Operation,
    @scala.annotation.unused _trace: List[DynamicOptic.Node]
  ): Either[XmlError, Xml] = operation match {
    case a: Operation.Add         => applyAdd(value, a.content, a.position)
    case _: Operation.Remove.type =>
      new Left(XmlError.patchError("Remove operation requires parent context"))
    case r: Operation.Replace          => new Right(r.content)
    case sa: Operation.SetAttribute    => applySetAttribute(value, sa.name, sa.value)
    case ra: Operation.RemoveAttribute => applyRemoveAttribute(value, ra.name)
  }

  /**
   * Apply an Add operation.
   */
  private[this] def applyAdd(
    value: Xml,
    content: Xml,
    position: Position
  ): Either[XmlError, Xml] = position match {
    case Position.PrependChild | Position.AppendChild =>
      value match {
        case e: Xml.Element =>
          new Right(e.copy(children = position match {
            case Position.PrependChild => content +: e.children
            case Position.AppendChild  => e.children :+ content
            case _                     => e.children
          }))
        case _ => new Left(XmlError.patchError("Add with PrependChild/AppendChild requires Element target"))
      }
    case _ => new Left(XmlError.patchError("Add with Before/After requires parent context"))
  }

  /**
   * Apply a SetAttribute operation.
   */
  private[this] def applySetAttribute(
    value: Xml,
    name: String,
    attrValue: String
  ): Either[XmlError, Xml] = value match {
    case elem: Xml.Element =>
      val attrName    = XmlName(name)
      val existingIdx = elem.attributes.indexWhere { case (n, _) => n.localName == name }
      val newAttrs    =
        if (existingIdx >= 0) elem.attributes.updated(existingIdx, (attrName, attrValue))
        else elem.attributes :+ (attrName, attrValue)
      new Right(elem.copy(attributes = newAttrs))
    case _ => new Left(XmlError.patchError("SetAttribute requires Element target"))
  }

  /**
   * Apply a RemoveAttribute operation.
   */
  private[this] def applyRemoveAttribute(value: Xml, name: String): Either[XmlError, Xml] = value match {
    case elem: Xml.Element =>
      val existingIdx = elem.attributes.indexWhere { case (n, _) => n.localName == name }
      if (existingIdx < 0) new Left(XmlError.patchError(s"Attribute '$name' not found"))
      else {
        new Right(elem.copy(attributes = elem.attributes.take(existingIdx) ++ elem.attributes.drop(existingIdx + 1)))
      }
    case _ => new Left(XmlError.patchError("RemoveAttribute requires Element target"))
  }
}
