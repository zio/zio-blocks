package zio.blocks.schema.patch

import zio.blocks.chunk.Chunk
import zio.blocks.schema._
import zio.blocks.schema.binding.Binding

/**
 * A type-safe patch that can be applied to values of type S.
 *
 * Patches represent a sequence of operations (set, increment, append, etc.)
 * that transform values. Because patches use serializable operations and
 * reflective optics for navigation, they can be serialized and applied
 * remotely.
 *
 * Patches can be composed using `++` and applied using the `apply` method with
 * a [[PatchMode]] (Strict, Lenient, or Clobber) to control error handling
 * behavior. Empty patches act as identity (no-op).
 *
 * {{{
 * val patch1 = Patch.set(Person.name, "John")
 * val patch2 = Patch.increment(Person.age, 1)
 *
 * val patch3 = patch1 ++ patch2
 *
 * patch3(Person("Jane", 25)) // Person("John", 26)
 * }}}
 *
 * @param dynamicPatch
 *   The untyped patch operations
 * @param schema
 *   The schema for type S, enabling type-safe conversion
 */
final case class Patch[S] private[schema] (dynamicPatch: DynamicPatch, schema: Schema[S]) {

  // Compose two patches. The result applies this patch first, then that patch.
  def ++(that: Patch[S]): Patch[S] = Patch(this.dynamicPatch ++ that.dynamicPatch, this.schema)

  def apply(value: S, mode: PatchMode): Either[SchemaError, S] = {
    val dynamicValue = schema.toDynamicValue(value)
    dynamicPatch(dynamicValue, mode).flatMap(schema.fromDynamicValue)
  }

  def apply(s: S): S = {
    val dynamicValue = schema.toDynamicValue(s)
    dynamicPatch(dynamicValue, PatchMode.Lenient) match {
      case Right(patched) =>
        schema.fromDynamicValue(patched) match {
          case Right(result) => result
          case Left(_)       => s // Conversion failed, return original
        }
      case Left(_) => s // Patch failed, return original
    }
  }

  // Apply this patch, returning None if any operation fails.
  def applyOption(s: S): Option[S] = {
    val dynamicValue = schema.toDynamicValue(s)
    dynamicPatch(dynamicValue, PatchMode.Strict) match {
      case Right(patched) =>
        schema.fromDynamicValue(patched) match {
          case Right(result) => Some(result)
          case Left(_)       => None
        }
      case Left(_) => None
    }
  }

  // Check if this patch is empty (no operations).
  def isEmpty: Boolean = dynamicPatch.isEmpty

  override def toString: String = dynamicPatch.toString
}

/**
 * Companion object providing smart constructors for type-safe patches.
 *
 * Use these methods to create patches instead of the private constructor.
 */
object Patch {

  /** Creates an empty patch (identity element for composition). */
  def empty[S](implicit schema: Schema[S]): Patch[S] =
    Patch(DynamicPatch.empty, schema)

  /** Sets a field/element to a specific value using an optic. */
  def set[S, A](optic: Optic[S, A], value: A): Patch[S] = {
    val schemaS      = new Schema(optic.source)
    val schemaA      = new Schema(optic.focus)
    val path         = optic.toDynamic
    val dynamicValue = schemaA.toDynamicValue(value)
    val op           = Patch.DynamicPatchOp(path, Patch.Operation.Set(dynamicValue))
    Patch(DynamicPatch(Vector(op)), schemaS)
  }

  // Set a field value using a Lens.
  def set[S, A](lens: Lens[S, A], value: A): Patch[S] =
    set(lens: Optic[S, A], value)

  // Set a value using an Optional.
  def set[S, A](optional: Optional[S, A], value: A): Patch[S] =
    set(optional: Optic[S, A], value)

  // Set all elements using a Traversal.
  def set[S, A](traversal: Traversal[S, A], value: A): Patch[S] =
    set(traversal: Optic[S, A], value)

  // Set a variant case using a Prism.
  def set[S, A <: S](prism: Prism[S, A], value: A): Patch[S] =
    set(prism: Optic[S, A], value)

  /** Appends elements to the end of a sequence. */
  def append[S, A](optic: Optic[S, Vector[A]], elements: Vector[A])(implicit
    d: CollectionDummy.ForVector.type
  ): Patch[S] = {
    val schemaS         = new Schema(optic.source)
    val seqReflect      = optic.focus.asInstanceOf[Reflect.Sequence[Binding, A, Vector]]
    val schemaA         = new Schema(seqReflect.element)
    val path            = optic.toDynamic
    val dynamicElements = Chunk.from(elements.map(schemaA.toDynamicValue))
    val seqOp           = Patch.SeqOp.Append(dynamicElements)
    val op              = Patch.DynamicPatchOp(path, Patch.Operation.SequenceEdit(Vector(seqOp)))
    Patch(DynamicPatch(Vector(op)), schemaS)
  }

  // Append elements to the end of a List.
  def append[S, A](optic: Optic[S, List[A]], elements: List[A])(implicit d: CollectionDummy.ForList.type): Patch[S] = {
    val schemaS         = new Schema(optic.source)
    val seqReflect      = optic.focus.asInstanceOf[Reflect.Sequence[Binding, A, List]]
    val schemaA         = new Schema(seqReflect.element)
    val path            = optic.toDynamic
    val dynamicElements = Chunk.from(elements.map(schemaA.toDynamicValue))
    val seqOp           = Patch.SeqOp.Append(dynamicElements)
    val op              = Patch.DynamicPatchOp(path, Patch.Operation.SequenceEdit(Vector(seqOp)))
    Patch(DynamicPatch(Vector(op)), schemaS)
  }

  // Append elements to the end of a Seq.
  def append[S, A](optic: Optic[S, Seq[A]], elements: Seq[A])(implicit d: CollectionDummy.ForSeq.type): Patch[S] = {
    val schemaS         = new Schema(optic.source)
    val seqReflect      = optic.focus.asInstanceOf[Reflect.Sequence[Binding, A, Seq]]
    val schemaA         = new Schema(seqReflect.element)
    val path            = optic.toDynamic
    val dynamicElements = Chunk.from(elements.map(schemaA.toDynamicValue))
    val seqOp           = Patch.SeqOp.Append(dynamicElements)
    val op              = Patch.DynamicPatchOp(path, Patch.Operation.SequenceEdit(Vector(seqOp)))
    Patch(DynamicPatch(Vector(op)), schemaS)
  }

  // Append elements to the end of an IndexedSeq.
  def append[S, A](optic: Optic[S, IndexedSeq[A]], elements: IndexedSeq[A])(implicit
    d: CollectionDummy.ForIndexedSeq.type
  ): Patch[S] = {
    val schemaS         = new Schema(optic.source)
    val seqReflect      = optic.focus.asInstanceOf[Reflect.Sequence[Binding, A, IndexedSeq]]
    val schemaA         = new Schema(seqReflect.element)
    val path            = optic.toDynamic
    val dynamicElements = Chunk.from(elements.map(schemaA.toDynamicValue))
    val seqOp           = Patch.SeqOp.Append(dynamicElements)
    val op              = Patch.DynamicPatchOp(path, Patch.Operation.SequenceEdit(Vector(seqOp)))
    Patch(DynamicPatch(Vector(op)), schemaS)
  }

  // Append elements to the end of a LazyList.
  def append[S, A](optic: Optic[S, LazyList[A]], elements: LazyList[A])(implicit
    d: CollectionDummy.ForLazyList.type
  ): Patch[S] = {
    val schemaS         = new Schema(optic.source)
    val seqReflect      = optic.focus.asInstanceOf[Reflect.Sequence[Binding, A, LazyList]]
    val schemaA         = new Schema(seqReflect.element)
    val path            = optic.toDynamic
    val dynamicElements = Chunk.from(elements.map(schemaA.toDynamicValue))
    val seqOp           = Patch.SeqOp.Append(dynamicElements)
    val op              = Patch.DynamicPatchOp(path, Patch.Operation.SequenceEdit(Vector(seqOp)))
    Patch(DynamicPatch(Vector(op)), schemaS)
  }

  /** Inserts elements at a specific index in a sequence. */
  def insertAt[S, A](optic: Optic[S, Vector[A]], index: Int, elements: Vector[A])(implicit
    d: CollectionDummy.ForVector.type
  ): Patch[S] = {
    val schemaS         = new Schema(optic.source)
    val seqReflect      = optic.focus.asInstanceOf[Reflect.Sequence[Binding, A, Vector]]
    val schemaA         = new Schema(seqReflect.element)
    val path            = optic.toDynamic
    val dynamicElements = Chunk.from(elements.map(schemaA.toDynamicValue))
    val seqOp           = Patch.SeqOp.Insert(index, dynamicElements)
    val op              = Patch.DynamicPatchOp(path, Patch.Operation.SequenceEdit(Vector(seqOp)))
    Patch(DynamicPatch(Vector(op)), schemaS)
  }

  // Insert elements at a specific index in a List.
  def insertAt[S, A](optic: Optic[S, List[A]], index: Int, elements: List[A])(implicit
    d: CollectionDummy.ForList.type
  ): Patch[S] = {
    val schemaS         = new Schema(optic.source)
    val seqReflect      = optic.focus.asInstanceOf[Reflect.Sequence[Binding, A, List]]
    val schemaA         = new Schema(seqReflect.element)
    val path            = optic.toDynamic
    val dynamicElements = Chunk.from(elements.map(schemaA.toDynamicValue))
    val seqOp           = Patch.SeqOp.Insert(index, dynamicElements)
    val op              = Patch.DynamicPatchOp(path, Patch.Operation.SequenceEdit(Vector(seqOp)))
    Patch(DynamicPatch(Vector(op)), schemaS)
  }

  // Insert elements at a specific index in a Seq.
  def insertAt[S, A](optic: Optic[S, Seq[A]], index: Int, elements: Seq[A])(implicit
    d: CollectionDummy.ForSeq.type
  ): Patch[S] = {
    val schemaS         = new Schema(optic.source)
    val seqReflect      = optic.focus.asInstanceOf[Reflect.Sequence[Binding, A, Seq]]
    val schemaA         = new Schema(seqReflect.element)
    val path            = optic.toDynamic
    val dynamicElements = Chunk.from(elements.map(schemaA.toDynamicValue))
    val seqOp           = Patch.SeqOp.Insert(index, dynamicElements)
    val op              = Patch.DynamicPatchOp(path, Patch.Operation.SequenceEdit(Vector(seqOp)))
    Patch(DynamicPatch(Vector(op)), schemaS)
  }

  // Insert elements at a specific index in an IndexedSeq.
  def insertAt[S, A](optic: Optic[S, IndexedSeq[A]], index: Int, elements: IndexedSeq[A])(implicit
    d: CollectionDummy.ForIndexedSeq.type
  ): Patch[S] = {
    val schemaS         = new Schema(optic.source)
    val seqReflect      = optic.focus.asInstanceOf[Reflect.Sequence[Binding, A, IndexedSeq]]
    val schemaA         = new Schema(seqReflect.element)
    val path            = optic.toDynamic
    val dynamicElements = Chunk.from(elements.map(schemaA.toDynamicValue))
    val seqOp           = Patch.SeqOp.Insert(index, dynamicElements)
    val op              = Patch.DynamicPatchOp(path, Patch.Operation.SequenceEdit(Vector(seqOp)))
    Patch(DynamicPatch(Vector(op)), schemaS)
  }

  // Insert elements at a specific index in a LazyList.
  def insertAt[S, A](optic: Optic[S, LazyList[A]], index: Int, elements: LazyList[A])(implicit
    d: CollectionDummy.ForLazyList.type
  ): Patch[S] = {
    val schemaS         = new Schema(optic.source)
    val seqReflect      = optic.focus.asInstanceOf[Reflect.Sequence[Binding, A, LazyList]]
    val schemaA         = new Schema(seqReflect.element)
    val path            = optic.toDynamic
    val dynamicElements = Chunk.from(elements.map(schemaA.toDynamicValue))
    val seqOp           = Patch.SeqOp.Insert(index, dynamicElements)
    val op              = Patch.DynamicPatchOp(path, Patch.Operation.SequenceEdit(Vector(seqOp)))
    Patch(DynamicPatch(Vector(op)), schemaS)
  }

  /** Deletes elements from a sequence starting at the given index. */
  def deleteAt[S, A](optic: Optic[S, Vector[A]], index: Int, count: Int)(implicit
    d: CollectionDummy.ForVector.type
  ): Patch[S] = {
    val schemaS = new Schema(optic.source)
    val path    = optic.toDynamic
    val seqOp   = Patch.SeqOp.Delete(index, count)
    val op      = Patch.DynamicPatchOp(path, Patch.Operation.SequenceEdit(Vector(seqOp)))
    Patch(DynamicPatch(Vector(op)), schemaS)
  }

  // Delete elements from a List starting at the given index.
  def deleteAt[S, A](optic: Optic[S, List[A]], index: Int, count: Int)(implicit
    d: CollectionDummy.ForList.type
  ): Patch[S] = {
    val schemaS = new Schema(optic.source)
    val path    = optic.toDynamic
    val seqOp   = Patch.SeqOp.Delete(index, count)
    val op      = Patch.DynamicPatchOp(path, Patch.Operation.SequenceEdit(Vector(seqOp)))
    Patch(DynamicPatch(Vector(op)), schemaS)
  }

  // Delete elements from a Seq starting at the given index.
  def deleteAt[S, A](optic: Optic[S, Seq[A]], index: Int, count: Int)(implicit
    d: CollectionDummy.ForSeq.type
  ): Patch[S] = {
    val schemaS = new Schema(optic.source)
    val path    = optic.toDynamic
    val seqOp   = Patch.SeqOp.Delete(index, count)
    val op      = Patch.DynamicPatchOp(path, Patch.Operation.SequenceEdit(Vector(seqOp)))
    Patch(DynamicPatch(Vector(op)), schemaS)
  }

  // Delete elements from an IndexedSeq starting at the given index.
  def deleteAt[S, A](optic: Optic[S, IndexedSeq[A]], index: Int, count: Int)(implicit
    d: CollectionDummy.ForIndexedSeq.type
  ): Patch[S] = {
    val schemaS = new Schema(optic.source)
    val path    = optic.toDynamic
    val seqOp   = Patch.SeqOp.Delete(index, count)
    val op      = Patch.DynamicPatchOp(path, Patch.Operation.SequenceEdit(Vector(seqOp)))
    Patch(DynamicPatch(Vector(op)), schemaS)
  }

  // Delete elements from a LazyList starting at the given index.
  def deleteAt[S, A](optic: Optic[S, LazyList[A]], index: Int, count: Int)(implicit
    d: CollectionDummy.ForLazyList.type
  ): Patch[S] = {
    val schemaS = new Schema(optic.source)
    val path    = optic.toDynamic
    val seqOp   = Patch.SeqOp.Delete(index, count)
    val op      = Patch.DynamicPatchOp(path, Patch.Operation.SequenceEdit(Vector(seqOp)))
    Patch(DynamicPatch(Vector(op)), schemaS)
  }

  /**
   * Modifies an element at a specific index in a sequence using a nested patch.
   */
  def modifyAt[S, A](optic: Optic[S, Vector[A]], index: Int, elementPatch: Patch[A])(implicit
    d: CollectionDummy.ForVector.type
  ): Patch[S] = {
    val schemaS   = new Schema(optic.source)
    val basePath  = optic.toDynamic
    val nestedOps = elementPatch.dynamicPatch.ops

    if (nestedOps.isEmpty) {
      // Empty patch - return empty
      Patch.empty[S](schemaS)
    } else {
      // Combine paths: basePath + AtIndex(index) + each nested operation's path
      val combinedOps = nestedOps.map { nestedOp =>
        // Build the full path: sequence field -> index -> nested path
        val fullPath = new DynamicOptic((basePath.nodes :+ DynamicOptic.Node.AtIndex(index)) ++ nestedOp.path.nodes)
        Patch.DynamicPatchOp(fullPath, nestedOp.operation)
      }
      Patch(DynamicPatch(combinedOps), schemaS)
    }
  }

  // Modify an element at a specific index in a List.
  def modifyAt[S, A](optic: Optic[S, List[A]], index: Int, elementPatch: Patch[A])(implicit
    d: CollectionDummy.ForList.type
  ): Patch[S] = {
    val schemaS   = new Schema(optic.source)
    val basePath  = optic.toDynamic
    val nestedOps = elementPatch.dynamicPatch.ops

    if (nestedOps.isEmpty) {
      Patch.empty[S](schemaS)
    } else {
      val combinedOps = nestedOps.map { nestedOp =>
        val fullPath = new DynamicOptic((basePath.nodes :+ DynamicOptic.Node.AtIndex(index)) ++ nestedOp.path.nodes)
        Patch.DynamicPatchOp(fullPath, nestedOp.operation)
      }
      Patch(DynamicPatch(combinedOps), schemaS)
    }
  }

  // Modify an element at a specific index in a Seq.
  def modifyAt[S, A](optic: Optic[S, Seq[A]], index: Int, elementPatch: Patch[A])(implicit
    d: CollectionDummy.ForSeq.type
  ): Patch[S] = {
    val schemaS   = new Schema(optic.source)
    val basePath  = optic.toDynamic
    val nestedOps = elementPatch.dynamicPatch.ops

    if (nestedOps.isEmpty) {
      Patch.empty[S](schemaS)
    } else {
      val combinedOps = nestedOps.map { nestedOp =>
        val fullPath = new DynamicOptic((basePath.nodes :+ DynamicOptic.Node.AtIndex(index)) ++ nestedOp.path.nodes)
        Patch.DynamicPatchOp(fullPath, nestedOp.operation)
      }
      Patch(DynamicPatch(combinedOps), schemaS)
    }
  }

  // Modify an element at a specific index in an IndexedSeq.
  def modifyAt[S, A](optic: Optic[S, IndexedSeq[A]], index: Int, elementPatch: Patch[A])(implicit
    d: CollectionDummy.ForIndexedSeq.type
  ): Patch[S] = {
    val schemaS   = new Schema(optic.source)
    val basePath  = optic.toDynamic
    val nestedOps = elementPatch.dynamicPatch.ops

    if (nestedOps.isEmpty) {
      Patch.empty[S](schemaS)
    } else {
      val combinedOps = nestedOps.map { nestedOp =>
        val fullPath = new DynamicOptic((basePath.nodes :+ DynamicOptic.Node.AtIndex(index)) ++ nestedOp.path.nodes)
        Patch.DynamicPatchOp(fullPath, nestedOp.operation)
      }
      Patch(DynamicPatch(combinedOps), schemaS)
    }
  }

  // Modify an element at a specific index in a LazyList.
  def modifyAt[S, A](optic: Optic[S, LazyList[A]], index: Int, elementPatch: Patch[A])(implicit
    d: CollectionDummy.ForLazyList.type
  ): Patch[S] = {
    val schemaS   = new Schema(optic.source)
    val basePath  = optic.toDynamic
    val nestedOps = elementPatch.dynamicPatch.ops

    if (nestedOps.isEmpty) {
      Patch.empty[S](schemaS)
    } else {
      val combinedOps = nestedOps.map { nestedOp =>
        val fullPath = new DynamicOptic((basePath.nodes :+ DynamicOptic.Node.AtIndex(index)) ++ nestedOp.path.nodes)
        Patch.DynamicPatchOp(fullPath, nestedOp.operation)
      }
      Patch(DynamicPatch(combinedOps), schemaS)
    }
  }

  /** Adds a key-value pair to a map. */
  def addKey[S, K, V](optic: Optic[S, Map[K, V]], key: K, value: V): Patch[S] = {
    val schemaS      = new Schema(optic.source)
    val mapReflect   = optic.focus.asInstanceOf[Reflect.Map[Binding, K, V, Map]]
    val schemaK      = new Schema(mapReflect.key)
    val schemaV      = new Schema(mapReflect.value)
    val path         = optic.toDynamic
    val dynamicKey   = schemaK.toDynamicValue(key)
    val dynamicValue = schemaV.toDynamicValue(value)
    val mapOp        = Patch.MapOp.Add(dynamicKey, dynamicValue)
    val op           = Patch.DynamicPatchOp(path, Patch.Operation.MapEdit(Vector(mapOp)))
    Patch(DynamicPatch(Vector(op)), schemaS)
  }

  /** Removes a key from a map. */
  def removeKey[S, K, V](optic: Optic[S, Map[K, V]], key: K): Patch[S] = {
    val schemaS    = new Schema(optic.source)
    val mapReflect = optic.focus.asInstanceOf[Reflect.Map[Binding, K, V, Map]]
    val schemaK    = new Schema(mapReflect.key)
    val path       = optic.toDynamic
    val dynamicKey = schemaK.toDynamicValue(key)
    val mapOp      = Patch.MapOp.Remove(dynamicKey)
    val op         = Patch.DynamicPatchOp(path, Patch.Operation.MapEdit(Vector(mapOp)))
    Patch(DynamicPatch(Vector(op)), schemaS)
  }

  /** Modifies the value at a key in a map using a nested patch. */
  def modifyKey[S, K, V](optic: Optic[S, Map[K, V]], key: K, valuePatch: Patch[V]): Patch[S] = {
    val schemaS    = new Schema(optic.source)
    val mapReflect = optic.focus.asInstanceOf[Reflect.Map[Binding, K, V, Map]]
    val schemaK    = new Schema(mapReflect.key)
    val basePath   = optic.toDynamic
    val dynamicKey = schemaK.toDynamicValue(key)
    val nestedOps  = valuePatch.dynamicPatch.ops

    if (nestedOps.isEmpty) {
      // Empty patch - return empty
      Patch.empty[S](schemaS)
    } else {
      // Combine paths: basePath + AtMapKey(key) + each nested operation's path
      val combinedOps = nestedOps.map { nestedOp =>
        // Build the full path: map field -> key -> nested path
        val fullPath =
          new DynamicOptic((basePath.nodes :+ DynamicOptic.Node.AtMapKey(dynamicKey)) ++ nestedOp.path.nodes)
        Patch.DynamicPatchOp(fullPath, nestedOp.operation)
      }
      Patch(DynamicPatch(combinedOps), schemaS)
    }
  }

  /** Increments a numeric field by a delta value. */
  def increment[S](optic: Optic[S, Int], delta: Int): Patch[S] = {
    val schemaS = new Schema(optic.source)
    val path    = optic.toDynamic
    val op      = Patch.DynamicPatchOp(path, Patch.Operation.PrimitiveDelta(Patch.PrimitiveOp.IntDelta(delta)))
    Patch(DynamicPatch(Vector(op)), schemaS)
  }

  // Increment a Long field by a delta value.
  def increment[S](optic: Optic[S, Long], delta: Long): Patch[S] = {
    val schemaS = new Schema(optic.source)
    val path    = optic.toDynamic
    val op      = Patch.DynamicPatchOp(path, Patch.Operation.PrimitiveDelta(Patch.PrimitiveOp.LongDelta(delta)))
    Patch(DynamicPatch(Vector(op)), schemaS)
  }

  // Increment a Double field by a delta value.
  def increment[S](optic: Optic[S, Double], delta: Double): Patch[S] = {
    val schemaS = new Schema(optic.source)
    val path    = optic.toDynamic
    val op      = Patch.DynamicPatchOp(path, Patch.Operation.PrimitiveDelta(Patch.PrimitiveOp.DoubleDelta(delta)))
    Patch(DynamicPatch(Vector(op)), schemaS)
  }

  // Increment a Float field by a delta value.
  def increment[S](optic: Optic[S, Float], delta: Float): Patch[S] = {
    val schemaS = new Schema(optic.source)
    val path    = optic.toDynamic
    val op      = Patch.DynamicPatchOp(path, Patch.Operation.PrimitiveDelta(Patch.PrimitiveOp.FloatDelta(delta)))
    Patch(DynamicPatch(Vector(op)), schemaS)
  }

  // Increment a Short field by a delta value.
  def increment[S](optic: Optic[S, Short], delta: Short): Patch[S] = {
    val schemaS = new Schema(optic.source)
    val path    = optic.toDynamic
    val op      = Patch.DynamicPatchOp(path, Patch.Operation.PrimitiveDelta(Patch.PrimitiveOp.ShortDelta(delta)))
    Patch(DynamicPatch(Vector(op)), schemaS)
  }

  // Increment a Byte field by a delta value.
  def increment[S](optic: Optic[S, Byte], delta: Byte): Patch[S] = {
    val schemaS = new Schema(optic.source)
    val path    = optic.toDynamic
    val op      = Patch.DynamicPatchOp(path, Patch.Operation.PrimitiveDelta(Patch.PrimitiveOp.ByteDelta(delta)))
    Patch(DynamicPatch(Vector(op)), schemaS)
  }

  // Increment a BigInt field by a delta value.
  def increment[S](optic: Optic[S, BigInt], delta: BigInt): Patch[S] = {
    val schemaS = new Schema(optic.source)
    val path    = optic.toDynamic
    val op      = Patch.DynamicPatchOp(path, Patch.Operation.PrimitiveDelta(Patch.PrimitiveOp.BigIntDelta(delta)))
    Patch(DynamicPatch(Vector(op)), schemaS)
  }

  // Increment a BigDecimal field by a delta value.
  def increment[S](optic: Optic[S, BigDecimal], delta: BigDecimal): Patch[S] = {
    val schemaS = new Schema(optic.source)
    val path    = optic.toDynamic
    val op      = Patch.DynamicPatchOp(path, Patch.Operation.PrimitiveDelta(Patch.PrimitiveOp.BigDecimalDelta(delta)))
    Patch(DynamicPatch(Vector(op)), schemaS)
  }

  /** Adds a duration to a temporal field. */
  def addDuration[S](optic: Optic[S, java.time.Instant], duration: java.time.Duration): Patch[S] = {
    val schemaS = new Schema(optic.source)
    val path    = optic.toDynamic
    val op      = Patch.DynamicPatchOp(path, Patch.Operation.PrimitiveDelta(Patch.PrimitiveOp.InstantDelta(duration)))
    Patch(DynamicPatch(Vector(op)), schemaS)
  }

  // Add a duration to a Duration field.
  def addDuration[S](optic: Optic[S, java.time.Duration], duration: java.time.Duration)(implicit
    d: DurationDummy.ForDuration.type
  ): Patch[S] = {
    val schemaS = new Schema(optic.source)
    val path    = optic.toDynamic
    val op      = Patch.DynamicPatchOp(path, Patch.Operation.PrimitiveDelta(Patch.PrimitiveOp.DurationDelta(duration)))
    Patch(DynamicPatch(Vector(op)), schemaS)
  }

  /** Adds a period to a date field. */
  def addPeriod[S](optic: Optic[S, java.time.LocalDate], period: java.time.Period): Patch[S] = {
    val schemaS = new Schema(optic.source)
    val path    = optic.toDynamic
    val op      = Patch.DynamicPatchOp(path, Patch.Operation.PrimitiveDelta(Patch.PrimitiveOp.LocalDateDelta(period)))
    Patch(DynamicPatch(Vector(op)), schemaS)
  }

  // Add a period and duration to a LocalDateTime field.
  def addPeriodAndDuration[S](
    optic: Optic[S, java.time.LocalDateTime],
    period: java.time.Period,
    duration: java.time.Duration
  ): Patch[S] = {
    val schemaS = new Schema(optic.source)
    val path    = optic.toDynamic
    val op      =
      Patch.DynamicPatchOp(path, Patch.Operation.PrimitiveDelta(Patch.PrimitiveOp.LocalDateTimeDelta(period, duration)))
    Patch(DynamicPatch(Vector(op)), schemaS)
  }

  // Add a period to a Period field.
  def addPeriod[S](optic: Optic[S, java.time.Period], period: java.time.Period)(implicit
    d: PeriodDummy.ForPeriod.type
  ): Patch[S] = {
    val schemaS = new Schema(optic.source)
    val path    = optic.toDynamic
    val op      = Patch.DynamicPatchOp(path, Patch.Operation.PrimitiveDelta(Patch.PrimitiveOp.PeriodDelta(period)))
    Patch(DynamicPatch(Vector(op)), schemaS)
  }

  /** Edits a string field using a sequence of insert/delete operations. */
  def editString[S](optic: Optic[S, String], edits: Vector[Patch.StringOp]): Patch[S] = {
    val schemaS = new Schema(optic.source)
    val path    = optic.toDynamic
    val op      = Patch.DynamicPatchOp(path, Patch.Operation.PrimitiveDelta(Patch.PrimitiveOp.StringEdit(edits)))
    Patch(DynamicPatch(Vector(op)), schemaS)
  }

  // Replace a field value using a Lens.
  def replace[S, A](lens: Lens[S, A], a: A): Patch[S] =
    set(lens, a)

  // Replace a value using an Optional.
  def replace[S, A](optional: Optional[S, A], a: A): Patch[S] =
    set(optional, a)

  // Replace all elements using a Traversal.
  def replace[S, A](traversal: Traversal[S, A], a: A): Patch[S] =
    set(traversal, a)

  // Replace a variant case using a Prism.
  def replace[S, A <: S](prism: Prism[S, A], a: A): Patch[S] =
    set(prism, a)

  // Type aliases for convenience
  type DurationDummy = DynamicPatch.DurationDummy
  val DurationDummy = DynamicPatch.DurationDummy

  type PeriodDummy = DynamicPatch.PeriodDummy
  val PeriodDummy = DynamicPatch.PeriodDummy

  type CollectionDummy = DynamicPatch.CollectionDummy
  val CollectionDummy = DynamicPatch.CollectionDummy

  type DynamicPatchOp = DynamicPatch.DynamicPatchOp
  val DynamicPatchOp = DynamicPatch.DynamicPatchOp

  type Operation = DynamicPatch.Operation
  val Operation = DynamicPatch.Operation

  type PrimitiveOp = DynamicPatch.PrimitiveOp
  val PrimitiveOp = DynamicPatch.PrimitiveOp

  type SeqOp = DynamicPatch.SeqOp
  val SeqOp = DynamicPatch.SeqOp

  type StringOp = DynamicPatch.StringOp
  val StringOp = DynamicPatch.StringOp

  type MapOp = DynamicPatch.MapOp
  val MapOp = DynamicPatch.MapOp
}
