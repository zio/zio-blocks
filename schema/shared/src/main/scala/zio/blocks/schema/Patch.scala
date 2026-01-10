package zio.blocks.schema

/**
 * A Patch is a sequence of operations that can be applied to a value to produce
 * a new value. Because patches are described by reflective optics, finite
 * operations on specific types of optics, and values that can be serialized,
 * patches themselves can be serialized.
 *
 * {{{
 * val patch1 = Patch.replace(Person.name, "John")
 * val patch2 = Patch.replace(Person.age, 30)
 *
 * val patch3 = patch1 ++ patch2
 *
 * patch3(Person("Jane", 25)) // Person("John", 30)
 * }}}
 */
final case class Patch[S](ops: Vector[Patch.Pair[S, ?]], source: Schema[S]) {
  import Patch._

  def ++(that: Patch[S]): Patch[S] = Patch(this.ops ++ that.ops, this.source)

  /**
   * Convert this typed Patch to an untyped DynamicPatch.
   * Only works for patches that contain DynamicPatchPair operations.
   * For typed operations, use applyDynamic which applies via Schema conversion.
   */
  def toDynamicPatch: Option[DynamicPatch] = {
    val dynamicOps = ops.flatMap {
      case DynamicPatchPair(dynamicPatch) => dynamicPatch.ops
      case _ => Vector.empty // Typed operations cannot be directly converted
    }
    if (dynamicOps.isEmpty && ops.nonEmpty) None
    else Some(new DynamicPatch(dynamicOps))
  }

  /**
   * Apply this patch via DynamicValue conversion.
   * This is useful when you need PatchMode control.
   * Returns Left(error) if the patch cannot be converted to DynamicPatch or if application fails.
   */
  def applyDynamic(s: S, mode: PatchMode): Either[SchemaError, S] = {
    val dynValue = source.toDynamicValue(s)
    toDynamicPatch match {
      case Some(dynPatch) =>
        dynPatch(dynValue, mode).flatMap { resultDv =>
          source.fromDynamicValue(resultDv)
        }
      case None =>
        // No DynamicPatch available, fall back to direct apply
        Right(apply(s))
    }
  }

  def apply(s: S): S =
    ops.foldLeft[S](s) { (s, single) =>
      single match {
        case LensPair(optic, LensOp.Replace(a))           => optic.replace(s, a)
        case LensPair(optic, op: LensOp.Increment)        => optic.asInstanceOf[Lens[S, Int]].modify(s, v => v + op.delta)
        case LensPair(optic, op: LensOp.IncrementLong)    => optic.asInstanceOf[Lens[S, Long]].modify(s, v => v + op.delta)
        case LensPair(optic, op: LensOp.IncrementDouble)  => optic.asInstanceOf[Lens[S, Double]].modify(s, v => v + op.delta)
        case LensPair(optic, op: LensOp.IncrementFloat)   => optic.asInstanceOf[Lens[S, Float]].modify(s, v => v + op.delta)
        case LensPair(optic, op: LensOp.IncrementShort)   => optic.asInstanceOf[Lens[S, Short]].modify(s, v => (v + op.delta).toShort)
        case LensPair(optic, op: LensOp.IncrementByte)    => optic.asInstanceOf[Lens[S, Byte]].modify(s, v => (v + op.delta).toByte)
        case LensPair(optic, op: LensOp.IncrementBigInt)  => optic.asInstanceOf[Lens[S, BigInt]].modify(s, v => v + op.delta)
        case LensPair(optic, op: LensOp.IncrementBigDecimal) => optic.asInstanceOf[Lens[S, BigDecimal]].modify(s, v => v + op.delta)
        case LensPair(optic, op: LensOp.EditString)       => optic.asInstanceOf[Lens[S, String]].modify(s, v => applyStringOps(v, op.ops))
        case LensPair(optic, op: LensOp.AppendVector[?])  => optic.asInstanceOf[Lens[S, Vector[Any]]].modify(s, v => v ++ op.elements)
        case LensPair(optic, op: LensOp.InsertAtVector[?]) => optic.asInstanceOf[Lens[S, Vector[Any]]].modify(s, v => {
          val (before, after) = v.splitAt(op.index)
          before ++ op.elements ++ after
        })
        case LensPair(optic, op: LensOp.DeleteAtVector[?])   => optic.asInstanceOf[Lens[S, Vector[Any]]].modify(s, v => v.take(op.index) ++ v.drop(op.index + op.count))
        case LensPair(optic, op: LensOp.AddMapKey[?, ?])  => optic.asInstanceOf[Lens[S, Map[Any, Any]]].modify(s, m => m + (op.key -> op.value))
        case LensPair(optic, op: LensOp.RemoveMapKey[?, ?]) => optic.asInstanceOf[Lens[S, Map[Any, Any]]].modify(s, m => m - op.key)
        case PrismPair(optic, PrismOp.Replace(a))         => optic.replace(s, a)
        case OptionalPair(optic, OptionalOp.Replace(a))   => optic.replace(s, a)
        case TraversalPair(optic, TraversalOp.Replace(a)) => optic.modify(s, _ => a)
        case DynamicPatchPair(dynamicPatch) =>
          // Apply via DynamicValue conversion
          val dynValue = source.toDynamicValue(s)
          dynamicPatch(dynValue, PatchMode.Strict) match {
            case Right(resultDv) => source.fromDynamicValue(resultDv).getOrElse(s)
            case Left(_) => s // On error in apply, return original (use applyDynamic for error handling)
          }
      }
    }

  private def applyStringOps(str: String, ops: Vector[StringOp]): String = {
    val sb = new StringBuilder(str)
    var idx = 0
    while (idx < ops.length) {
      ops(idx) match {
        case StringOp.Insert(i, text) => sb.insert(i, text)
        case StringOp.Delete(i, len) => sb.delete(i, i + len)
      }
      idx += 1
    }
    sb.toString
  }

  def applyOption(s: S): Option[S] = {
    var x   = s
    val len = ops.length
    var idx = 0
    while (idx < len) {
      ops(idx) match {
        case LensPair(optic, LensOp.Replace(a)) =>
          x = optic.replace(x, a)
        case PrismPair(optic, PrismOp.Replace(a)) =>
          optic.replaceOption(x, a) match {
            case Some(r) => x = r
            case _       => return None
          }
        case OptionalPair(optic, OptionalOp.Replace(a)) =>
          optic.replaceOption(x, a) match {
            case Some(r) => x = r
            case _       => return None
          }
        case TraversalPair(optic, TraversalOp.Replace(a)) =>
          optic.modifyOption(x, _ => a) match {
            case Some(r) => x = r
            case _       => return None
          }
      }
      idx += 1
    }
    new Some(x)
  }

  def applyOrFail(s: S): Either[OpticCheck, S] = {
    var x   = s
    val len = ops.length
    var idx = 0
    while (idx < len) {
      ops(idx) match {
        case LensPair(optic, LensOp.Replace(a)) =>
          x = optic.replace(x, a)
        case PrismPair(optic, PrismOp.Replace(a)) =>
          optic.replaceOrFail(x, a) match {
            case Right(r) => x = r
            case left     => return left
          }
        case OptionalPair(optic, OptionalOp.Replace(a)) =>
          optic.replaceOrFail(x, a) match {
            case Right(r) => x = r
            case left     => return left
          }
        case TraversalPair(optic, TraversalOp.Replace(a)) =>
          optic.modifyOrFail(x, _ => a) match {
            case Right(r) => x = r
            case left     => return left
          }
      }
      idx += 1
    }
    new Right(x)
  }
}

object Patch {
  def replace[S, A](lens: Lens[S, A], a: A)(implicit source: Schema[S]): Patch[S] =
    Patch(Vector(LensPair(lens, LensOp.Replace(a))), source)

  def replace[S, A](optional: Optional[S, A], a: A)(implicit source: Schema[S]): Patch[S] =
    Patch(Vector(OptionalPair(optional, OptionalOp.Replace(a))), source)

  def replace[S, A](traversal: Traversal[S, A], a: A)(implicit source: Schema[S]): Patch[S] =
    Patch(Vector(TraversalPair(traversal, TraversalOp.Replace(a))), source)

  def replace[S, A <: S](prism: Prism[S, A], a: A)(implicit source: Schema[S]): Patch[S] =
    Patch(Vector(PrismPair(prism, PrismOp.Replace(a))), source)

  // ============================================================
  // New typed API for DynamicPatch-based operations
  // ============================================================

  /** Empty patch (monoid identity) */
  def empty[S](implicit schema: Schema[S]): Patch[S] =
    Patch(Vector.empty, schema)

  /**
   * Create a Patch[S] that wraps a DynamicPatch.
   * When applied, this patch converts the value to DynamicValue, applies the DynamicPatch,
   * and converts back to the typed value.
   */
  def fromDynamicPatch[S](dynamicPatch: DynamicPatch, schema: Schema[S]): Patch[S] =
    new Patch[S](Vector(DynamicPatchPair(dynamicPatch)), schema)

  /**
   * A special Pair that wraps a DynamicPatch for application via DynamicValue conversion.
   */
  private[schema] case class DynamicPatchPair[S](dynamicPatch: DynamicPatch) extends Pair[S, Any] {
    def optic: Optic[S, Any] = null // Not used for DynamicPatchPair
    def op: Op[Any] = null // Not used for DynamicPatchPair
  }

  /** Set a field/element to a value using dynamic patch (clobber semantics) */
  def set[S, A](optic: Optic[S, A], value: A)(implicit schema: Schema[S], targetSchema: Schema[A]): Patch[S] =
    Patch.replace(optic.asInstanceOf[Lens[S, A]], value)

  /** Increment an Int field by a delta */
  def increment[S](optic: Lens[S, Int], delta: Int)(implicit schema: Schema[S]): Patch[S] =
    Patch(Vector(LensPair(optic, LensOp.Increment(delta))), schema)

  /** Increment a Long field by a delta */
  def incrementLong[S](optic: Lens[S, Long], delta: Long)(implicit schema: Schema[S]): Patch[S] =
    Patch(Vector(LensPair(optic, LensOp.IncrementLong(delta))), schema)

  /** Increment a Double field by a delta */
  def incrementDouble[S](optic: Lens[S, Double], delta: Double)(implicit schema: Schema[S]): Patch[S] =
    Patch(Vector(LensPair(optic, LensOp.IncrementDouble(delta))), schema)

  /** Increment a Float field by a delta */
  def incrementFloat[S](optic: Lens[S, Float], delta: Float)(implicit schema: Schema[S]): Patch[S] =
    Patch(Vector(LensPair(optic, LensOp.IncrementFloat(delta))), schema)

  /** Increment a Short field by a delta */
  def incrementShort[S](optic: Lens[S, Short], delta: Short)(implicit schema: Schema[S]): Patch[S] =
    Patch(Vector(LensPair(optic, LensOp.IncrementShort(delta))), schema)

  /** Increment a Byte field by a delta */
  def incrementByte[S](optic: Lens[S, Byte], delta: Byte)(implicit schema: Schema[S]): Patch[S] =
    Patch(Vector(LensPair(optic, LensOp.IncrementByte(delta))), schema)

  /** Increment a BigInt field by a delta */
  def incrementBigInt[S](optic: Lens[S, BigInt], delta: BigInt)(implicit schema: Schema[S]): Patch[S] =
    Patch(Vector(LensPair(optic, LensOp.IncrementBigInt(delta))), schema)

  /** Increment a BigDecimal field by a delta */
  def incrementBigDecimal[S](optic: Lens[S, BigDecimal], delta: BigDecimal)(implicit schema: Schema[S]): Patch[S] =
    Patch(Vector(LensPair(optic, LensOp.IncrementBigDecimal(delta))), schema)

  /** Edit a String field using string operations */
  def editString[S](optic: Lens[S, String], edits: Vector[StringOp])(implicit schema: Schema[S]): Patch[S] =
    Patch(Vector(LensPair(optic, LensOp.EditString(edits))), schema)

  /** Append elements to a Vector field */
  def append[S, A](optic: Lens[S, Vector[A]], elements: Vector[A])(implicit schema: Schema[S], elemSchema: Schema[A]): Patch[S] =
    Patch(Vector(LensPair(optic, LensOp.AppendVector(elements, elemSchema))), schema)

  /** Insert elements at a specific index in a Vector field */
  def insertAt[S, A](optic: Lens[S, Vector[A]], index: Int, elements: Vector[A])(implicit schema: Schema[S], elemSchema: Schema[A]): Patch[S] =
    Patch(Vector(LensPair(optic, LensOp.InsertAtVector(index, elements, elemSchema))), schema)

  /** Delete elements from a Vector field starting at index */
  def deleteAt[S, A](optic: Lens[S, Vector[A]], index: Int, count: Int)(implicit schema: Schema[S]): Patch[S] =
    Patch(Vector(LensPair(optic, LensOp.DeleteAtVector[A](index, count))), schema)

  /** Add a key-value pair to a Map field */
  def addKey[S, K, V](optic: Lens[S, Map[K, V]], key: K, value: V)(implicit schema: Schema[S], keySchema: Schema[K], valueSchema: Schema[V]): Patch[S] =
    Patch(Vector(LensPair(optic, LensOp.AddMapKey(key, value, keySchema, valueSchema))), schema)

  /** Remove a key from a Map field */
  def removeKey[S, K, V](optic: Lens[S, Map[K, V]], key: K)(implicit schema: Schema[S], keySchema: Schema[K]): Patch[S] =
    Patch(Vector(LensPair(optic, LensOp.RemoveMapKey(key, keySchema))), schema)

  sealed trait Op[A]

  sealed trait LensOp[A] extends Op[A]

  object LensOp {
    case class Replace[A](a: A) extends LensOp[A]
    
    // Numeric increment operations
    case class Increment(delta: Int) extends LensOp[Int]
    case class IncrementLong(delta: Long) extends LensOp[Long]
    case class IncrementDouble(delta: Double) extends LensOp[Double]
    case class IncrementFloat(delta: Float) extends LensOp[Float]
    case class IncrementShort(delta: Short) extends LensOp[Short]
    case class IncrementByte(delta: Byte) extends LensOp[Byte]
    case class IncrementBigInt(delta: BigInt) extends LensOp[BigInt]
    case class IncrementBigDecimal(delta: BigDecimal) extends LensOp[BigDecimal]
    
    // String edit operation
    case class EditString(ops: Vector[StringOp]) extends LensOp[String]
    
    // Vector operations
    case class AppendVector[A](elements: Vector[A], schema: Schema[A]) extends LensOp[Vector[A]]
    case class InsertAtVector[A](index: Int, elements: Vector[A], schema: Schema[A]) extends LensOp[Vector[A]]
    case class DeleteAtVector[A](index: Int, count: Int) extends LensOp[Vector[A]]
    
    // Map operations
    case class AddMapKey[K, V](key: K, value: V, keySchema: Schema[K], valueSchema: Schema[V]) extends LensOp[Map[K, V]]
    case class RemoveMapKey[K, V](key: K, keySchema: Schema[K]) extends LensOp[Map[K, V]]
  }

  sealed trait PrismOp[A] extends Op[A]

  object PrismOp {
    case class Replace[A](a: A) extends PrismOp[A]
  }

  sealed trait OptionalOp[A] extends Op[A]

  object OptionalOp {
    case class Replace[A](a: A) extends OptionalOp[A]
  }

  sealed trait TraversalOp[A] extends Op[A]

  object TraversalOp {
    case class Replace[A](a: A) extends TraversalOp[A]
  }

  sealed trait Pair[S, A] {
    def optic: Optic[S, A]

    def op: Op[A]
  }

  case class LensPair[S, A](optic: Lens[S, A], op: LensOp[A]) extends Pair[S, A]

  case class PrismPair[S, A <: S](optic: Prism[S, A], op: PrismOp[A]) extends Pair[S, A]

  case class OptionalPair[S, A](optic: Optional[S, A], op: OptionalOp[A]) extends Pair[S, A]

  case class TraversalPair[S, A](optic: Traversal[S, A], op: TraversalOp[A]) extends Pair[S, A]
}
