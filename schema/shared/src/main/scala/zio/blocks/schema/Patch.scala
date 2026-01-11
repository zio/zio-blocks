package zio.blocks.schema

import zio.blocks.schema.patch._

final case class Patch[S](dynamicPatch: DynamicPatch, schema: Schema[S]) {

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
}

object Patch {

  // Create an empty patch (identity element for composition).
  def empty[S](implicit schema: Schema[S]): Patch[S] =
    Patch(DynamicPatch.empty, schema)

  // Set a field/element to a value using any optic type.
  def set[S, A](optic: Optic[S, A], value: A)(implicit schemaS: Schema[S], schemaA: Schema[A]): Patch[S] = {
    val path         = dynamicOpticToPatchPath(optic.toDynamic)
    val dynamicValue = schemaA.toDynamicValue(value)
    val op           = DynamicPatchOp(path, Operation.Set(dynamicValue))
    Patch(DynamicPatch(Vector(op)), schemaS)
  }

  // Set a field value using a Lens.
  def set[S, A](lens: Lens[S, A], value: A)(implicit schemaS: Schema[S], schemaA: Schema[A]): Patch[S] =
    set(lens: Optic[S, A], value)

  // Set a value using an Optional.
  def set[S, A](optional: Optional[S, A], value: A)(implicit schemaS: Schema[S], schemaA: Schema[A]): Patch[S] =
    set(optional: Optic[S, A], value)

  // Set all elements using a Traversal.
  def set[S, A](traversal: Traversal[S, A], value: A)(implicit schemaS: Schema[S], schemaA: Schema[A]): Patch[S] =
    set(traversal: Optic[S, A], value)

  // Set a variant case using a Prism.
  def set[S, A <: S](prism: Prism[S, A], value: A)(implicit schemaS: Schema[S], schemaA: Schema[A]): Patch[S] =
    set(prism: Optic[S, A], value)

  // Append elements to the end of a sequence.
  def append[S, A](optic: Optic[S, Vector[A]], elements: Vector[A])(implicit
    schemaS: Schema[S],
    schemaA: Schema[A]
  ): Patch[S] = {
    val path            = dynamicOpticToPatchPath(optic.toDynamic)
    val dynamicElements = elements.map(schemaA.toDynamicValue)
    val seqOp           = SeqOp.Append(dynamicElements)
    val op              = DynamicPatchOp(path, Operation.SequenceEdit(Vector(seqOp)))
    Patch(DynamicPatch(Vector(op)), schemaS)
  }

  // Insert elements at a specific index in a sequence.
  def insertAt[S, A](optic: Optic[S, Vector[A]], index: Int, elements: Vector[A])(implicit
    schemaS: Schema[S],
    schemaA: Schema[A]
  ): Patch[S] = {
    val path            = dynamicOpticToPatchPath(optic.toDynamic)
    val dynamicElements = elements.map(schemaA.toDynamicValue)
    val seqOp           = SeqOp.Insert(index, dynamicElements)
    val op              = DynamicPatchOp(path, Operation.SequenceEdit(Vector(seqOp)))
    Patch(DynamicPatch(Vector(op)), schemaS)
  }

  // Delete elements from a sequence starting at the given index.
  def deleteAt[S, A](optic: Optic[S, Vector[A]], index: Int, count: Int)(implicit
    schemaS: Schema[S]
  ): Patch[S] = {
    val path  = dynamicOpticToPatchPath(optic.toDynamic)
    val seqOp = SeqOp.Delete(index, count)
    val op    = DynamicPatchOp(path, Operation.SequenceEdit(Vector(seqOp)))
    Patch(DynamicPatch(Vector(op)), schemaS)
  }

  // Modify an element at a specific index in a sequence.
  def modifyAt[S, A](optic: Optic[S, Vector[A]], index: Int, elementPatch: Patch[A])(implicit
    schemaS: Schema[S]
  ): Patch[S] = {
    val basePath  = dynamicOpticToPatchPath(optic.toDynamic)
    val nestedOps = elementPatch.dynamicPatch.ops

    if (nestedOps.isEmpty) {
      // Empty patch - return empty
      Patch.empty[S]
    } else {
      // Combine paths: basePath + AtIndex(index) + each nested operation's path
      val combinedOps = nestedOps.map { nestedOp =>
        // Build the full path: sequence field -> index -> nested path
        val fullPath = (basePath :+ PatchPath.AtIndex(index)) ++ nestedOp.path
        DynamicPatchOp(fullPath, nestedOp.operation)
      }
      Patch(DynamicPatch(combinedOps), schemaS)
    }
  }

  // Add a key-value pair to a map.
  def addKey[S, K, V](optic: Optic[S, Map[K, V]], key: K, value: V)(implicit
    schemaS: Schema[S],
    schemaK: Schema[K],
    schemaV: Schema[V]
  ): Patch[S] = {
    val path         = dynamicOpticToPatchPath(optic.toDynamic)
    val dynamicKey   = schemaK.toDynamicValue(key)
    val dynamicValue = schemaV.toDynamicValue(value)
    val mapOp        = MapOp.Add(dynamicKey, dynamicValue)
    val op           = DynamicPatchOp(path, Operation.MapEdit(Vector(mapOp)))
    Patch(DynamicPatch(Vector(op)), schemaS)
  }

  // Remove a key from a map.
  def removeKey[S, K, V](optic: Optic[S, Map[K, V]], key: K)(implicit
    schemaS: Schema[S],
    schemaK: Schema[K]
  ): Patch[S] = {
    val path       = dynamicOpticToPatchPath(optic.toDynamic)
    val dynamicKey = schemaK.toDynamicValue(key)
    val mapOp      = MapOp.Remove(dynamicKey)
    val op         = DynamicPatchOp(path, Operation.MapEdit(Vector(mapOp)))
    Patch(DynamicPatch(Vector(op)), schemaS)
  }

  // Modify the value at a key in a map.
  def modifyKey[S, K, V](optic: Optic[S, Map[K, V]], key: K, valuePatch: Patch[V])(implicit
    schemaS: Schema[S],
    schemaK: Schema[K]
  ): Patch[S] = {
    val basePath   = dynamicOpticToPatchPath(optic.toDynamic)
    val dynamicKey = schemaK.toDynamicValue(key)
    val nestedOps  = valuePatch.dynamicPatch.ops

    if (nestedOps.isEmpty) {
      // Empty patch - return empty
      Patch.empty[S]
    } else {
      // Combine paths: basePath + AtMapKey(key) + each nested operation's path
      val combinedOps = nestedOps.map { nestedOp =>
        // Build the full path: map field -> key -> nested path
        val fullPath = (basePath :+ PatchPath.AtMapKey(dynamicKey)) ++ nestedOp.path
        DynamicPatchOp(fullPath, nestedOp.operation)
      }
      Patch(DynamicPatch(combinedOps), schemaS)
    }
  }

  // Increment an Int field by a delta value.
  def increment[S](optic: Optic[S, Int], delta: Int)(implicit schemaS: Schema[S]): Patch[S] = {
    val path = dynamicOpticToPatchPath(optic.toDynamic)
    val op   = DynamicPatchOp(path, Operation.PrimitiveDelta(PrimitiveOp.IntDelta(delta)))
    Patch(DynamicPatch(Vector(op)), schemaS)
  }

  // Increment a Long field by a delta value.
  def incrementLong[S](optic: Optic[S, Long], delta: Long)(implicit schemaS: Schema[S]): Patch[S] = {
    val path = dynamicOpticToPatchPath(optic.toDynamic)
    val op   = DynamicPatchOp(path, Operation.PrimitiveDelta(PrimitiveOp.LongDelta(delta)))
    Patch(DynamicPatch(Vector(op)), schemaS)
  }

  // Increment a Double field by a delta value.
  def incrementDouble[S](optic: Optic[S, Double], delta: Double)(implicit schemaS: Schema[S]): Patch[S] = {
    val path = dynamicOpticToPatchPath(optic.toDynamic)
    val op   = DynamicPatchOp(path, Operation.PrimitiveDelta(PrimitiveOp.DoubleDelta(delta)))
    Patch(DynamicPatch(Vector(op)), schemaS)
  }

  // Increment a Float field by a delta value.
  def incrementFloat[S](optic: Optic[S, Float], delta: Float)(implicit schemaS: Schema[S]): Patch[S] = {
    val path = dynamicOpticToPatchPath(optic.toDynamic)
    val op   = DynamicPatchOp(path, Operation.PrimitiveDelta(PrimitiveOp.FloatDelta(delta)))
    Patch(DynamicPatch(Vector(op)), schemaS)
  }

  // Increment a Short field by a delta value.
  def incrementShort[S](optic: Optic[S, Short], delta: Short)(implicit schemaS: Schema[S]): Patch[S] = {
    val path = dynamicOpticToPatchPath(optic.toDynamic)
    val op   = DynamicPatchOp(path, Operation.PrimitiveDelta(PrimitiveOp.ShortDelta(delta)))
    Patch(DynamicPatch(Vector(op)), schemaS)
  }

  // Increment a Byte field by a delta value.
  def incrementByte[S](optic: Optic[S, Byte], delta: Byte)(implicit schemaS: Schema[S]): Patch[S] = {
    val path = dynamicOpticToPatchPath(optic.toDynamic)
    val op   = DynamicPatchOp(path, Operation.PrimitiveDelta(PrimitiveOp.ByteDelta(delta)))
    Patch(DynamicPatch(Vector(op)), schemaS)
  }

  // Increment a BigInt field by a delta value.
  def incrementBigInt[S](optic: Optic[S, BigInt], delta: BigInt)(implicit schemaS: Schema[S]): Patch[S] = {
    val path = dynamicOpticToPatchPath(optic.toDynamic)
    val op   = DynamicPatchOp(path, Operation.PrimitiveDelta(PrimitiveOp.BigIntDelta(delta)))
    Patch(DynamicPatch(Vector(op)), schemaS)
  }

  // Increment a BigDecimal field by a delta value.
  def incrementBigDecimal[S](optic: Optic[S, BigDecimal], delta: BigDecimal)(implicit schemaS: Schema[S]): Patch[S] = {
    val path = dynamicOpticToPatchPath(optic.toDynamic)
    val op   = DynamicPatchOp(path, Operation.PrimitiveDelta(PrimitiveOp.BigDecimalDelta(delta)))
    Patch(DynamicPatch(Vector(op)), schemaS)
  }

  // Add a duration to an Instant field.
  def addDuration[S](optic: Optic[S, java.time.Instant], duration: java.time.Duration)(implicit
    schemaS: Schema[S]
  ): Patch[S] = {
    val path = dynamicOpticToPatchPath(optic.toDynamic)
    val op   = DynamicPatchOp(path, Operation.PrimitiveDelta(PrimitiveOp.InstantDelta(duration)))
    Patch(DynamicPatch(Vector(op)), schemaS)
  }

  // Add a duration to a Duration field.
  def addDurationToDuration[S](optic: Optic[S, java.time.Duration], duration: java.time.Duration)(implicit
    schemaS: Schema[S]
  ): Patch[S] = {
    val path = dynamicOpticToPatchPath(optic.toDynamic)
    val op   = DynamicPatchOp(path, Operation.PrimitiveDelta(PrimitiveOp.DurationDelta(duration)))
    Patch(DynamicPatch(Vector(op)), schemaS)
  }

  // Add a period to a LocalDate field.
  def addPeriod[S](optic: Optic[S, java.time.LocalDate], period: java.time.Period)(implicit
    schemaS: Schema[S]
  ): Patch[S] = {
    val path = dynamicOpticToPatchPath(optic.toDynamic)
    val op   = DynamicPatchOp(path, Operation.PrimitiveDelta(PrimitiveOp.LocalDateDelta(period)))
    Patch(DynamicPatch(Vector(op)), schemaS)
  }

  // Add a period and duration to a LocalDateTime field.
  def addPeriodAndDuration[S](
    optic: Optic[S, java.time.LocalDateTime],
    period: java.time.Period,
    duration: java.time.Duration
  )(implicit schemaS: Schema[S]): Patch[S] = {
    val path = dynamicOpticToPatchPath(optic.toDynamic)
    val op   = DynamicPatchOp(path, Operation.PrimitiveDelta(PrimitiveOp.LocalDateTimeDelta(period, duration)))
    Patch(DynamicPatch(Vector(op)), schemaS)
  }

  // Add a period to a Period field.
  def addPeriodToPeriod[S](optic: Optic[S, java.time.Period], period: java.time.Period)(implicit
    schemaS: Schema[S]
  ): Patch[S] = {
    val path = dynamicOpticToPatchPath(optic.toDynamic)
    val op   = DynamicPatchOp(path, Operation.PrimitiveDelta(PrimitiveOp.PeriodDelta(period)))
    Patch(DynamicPatch(Vector(op)), schemaS)
  }

  // Edit a string field using a sequence of insert/delete operations.
  def editString[S](optic: Optic[S, String], edits: Vector[StringOp])(implicit schemaS: Schema[S]): Patch[S] = {
    val path = dynamicOpticToPatchPath(optic.toDynamic)
    val op   = DynamicPatchOp(path, Operation.PrimitiveDelta(PrimitiveOp.StringEdit(edits)))
    Patch(DynamicPatch(Vector(op)), schemaS)
  }

  // Replace a field value using a Lens.
  def replace[S, A](lens: Lens[S, A], a: A)(implicit source: Schema[S]): Patch[S] = {
    // Get schema for A from the lens focus
    implicit val schemaA: Schema[A] = new Schema(lens.focus)
    set(lens, a)
  }

  // Replace a value using an Optional.
  def replace[S, A](optional: Optional[S, A], a: A)(implicit source: Schema[S]): Patch[S] = {
    implicit val schemaA: Schema[A] = new Schema(optional.focus)
    set(optional, a)
  }

  // Replace all elements using a Traversal.
  def replace[S, A](traversal: Traversal[S, A], a: A)(implicit source: Schema[S]): Patch[S] = {
    implicit val schemaA: Schema[A] = new Schema(traversal.focus)
    set(traversal, a)
  }

  // Replace a variant case using a Prism.
  def replace[S, A <: S](prism: Prism[S, A], a: A)(implicit source: Schema[S]): Patch[S] = {
    implicit val schemaA: Schema[A] = new Schema(prism.focus)
    set(prism, a)
  }

  // Convert a DynamicOptic to a Vector[PatchPath].
  private[schema] def dynamicOpticToPatchPath(optic: DynamicOptic): Vector[PatchPath] =
    optic.nodes.map {
      case DynamicOptic.Node.Field(name)    => PatchPath.Field(name)
      case DynamicOptic.Node.Case(name)     => PatchPath.Case(name)
      case DynamicOptic.Node.AtIndex(index) => PatchPath.AtIndex(index)
      case DynamicOptic.Node.AtMapKey(key)  => PatchPath.AtMapKey(anyToDynamicValue(key))
      case DynamicOptic.Node.Elements       => PatchPath.Elements
      case DynamicOptic.Node.Wrapped        => PatchPath.Wrapped
      case DynamicOptic.Node.AtIndices(_)   =>
        throw new UnsupportedOperationException("AtIndices not supported in patches")
      case DynamicOptic.Node.AtMapKeys(_) =>
        throw new UnsupportedOperationException("AtMapKeys not supported in patches")
      case DynamicOptic.Node.MapKeys   => throw new UnsupportedOperationException("MapKeys not supported in patches")
      case DynamicOptic.Node.MapValues => throw new UnsupportedOperationException("MapValues not supported in patches")
    }.toVector

  // Convert an arbitrary key to DynamicValue.
  private def anyToDynamicValue(key: Any): DynamicValue = key match {
    case s: String            => DynamicValue.Primitive(PrimitiveValue.String(s))
    case i: Int               => DynamicValue.Primitive(PrimitiveValue.Int(i))
    case l: Long              => DynamicValue.Primitive(PrimitiveValue.Long(l))
    case b: Boolean           => DynamicValue.Primitive(PrimitiveValue.Boolean(b))
    case d: Double            => DynamicValue.Primitive(PrimitiveValue.Double(d))
    case f: Float             => DynamicValue.Primitive(PrimitiveValue.Float(f))
    case s: Short             => DynamicValue.Primitive(PrimitiveValue.Short(s))
    case b: Byte              => DynamicValue.Primitive(PrimitiveValue.Byte(b))
    case c: Char              => DynamicValue.Primitive(PrimitiveValue.Char(c))
    case bi: BigInt           => DynamicValue.Primitive(PrimitiveValue.BigInt(bi))
    case bd: BigDecimal       => DynamicValue.Primitive(PrimitiveValue.BigDecimal(bd))
    case uuid: java.util.UUID => DynamicValue.Primitive(PrimitiveValue.UUID(uuid))
    case dv: DynamicValue     => dv
    case other                =>
      throw new UnsupportedOperationException(s"Cannot convert ${other.getClass.getName} to DynamicValue for map key")
  }
}
