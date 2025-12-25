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
 *
 * Patches can also be created by diffing two values:
 * {{{
 * val person1 = Person("Jane", 25)
 * val person2 = Person("John", 30)
 * val patch = Schema[Person].diff(person1, person2)
 * patch(person1) // Person("John", 30)
 * }}}
 */
final case class Patch[S](
  ops: Vector[Patch.Pair[S, ?]],
  source: Schema[S],
  dynamicPatch: Option[DynamicPatch] = None
) {
  import Patch._

  def ++(that: Patch[S]): Patch[S] = {
    val mergedOps = this.ops ++ that.ops
    val mergedDynamic = (this.dynamicPatch, that.dynamicPatch) match {
      case (Some(d1), Some(d2)) => Some(d1 ++ d2)
      case (Some(d1), None) => Some(d1)
      case (None, Some(d2)) => Some(d2)
      case (None, None) => None
    }
    Patch(mergedOps, this.source, mergedDynamic)
  }

  def apply(s: S): S = {
    // First apply typed ops
    val afterTyped = ops.foldLeft[S](s) { (s, single) =>
      single match {
        case LensPair(optic, LensOp.Replace(a))           => optic.replace(s, a)
        case PrismPair(optic, PrismOp.Replace(a))         => optic.replace(s, a)
        case OptionalPair(optic, OptionalOp.Replace(a))   => optic.replace(s, a)
        case TraversalPair(optic, TraversalOp.Replace(a)) => optic.modify(s, _ => a)
      }
    }
    // Then apply dynamic patch if present
    dynamicPatch match {
      case Some(dp) =>
        val dv = source.toDynamicValue(afterTyped)
        dp.apply(dv, DynamicPatch.PatchMode.Lenient) match {
          case Right(newDv) => source.fromDynamicValue(newDv).getOrElse(afterTyped)
          case Left(_)      => afterTyped
        }
      case None => afterTyped
    }
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
    // Apply dynamic patch if present
    dynamicPatch match {
      case Some(dp) =>
        val dv = source.toDynamicValue(x)
        dp.apply(dv, DynamicPatch.PatchMode.Strict) match {
          case Right(newDv) => source.fromDynamicValue(newDv).toOption
          case Left(_)      => None
        }
      case None => new Some(x)
    }
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

  /**
   * Apply this patch with explicit control over the patch mode.
   * Returns Left(SchemaError) on failure, Right(result) on success.
   */
  def applyWithMode(s: S, mode: DynamicPatch.PatchMode): Either[SchemaError, S] = {
    // First apply typed ops
    var x   = s
    val len = ops.length
    var idx = 0
    while (idx < len) {
      ops(idx) match {
        case LensPair(optic, LensOp.Replace(a)) =>
          x = optic.replace(x, a)
        case PrismPair(optic, PrismOp.Replace(a)) =>
          x = optic.replace(x, a)
        case OptionalPair(optic, OptionalOp.Replace(a)) =>
          x = optic.replace(x, a)
        case TraversalPair(optic, TraversalOp.Replace(a)) =>
          x = optic.modify(x, _ => a)
      }
      idx += 1
    }
    // Then apply dynamic patch if present
    dynamicPatch match {
      case Some(dp) =>
        val dv = source.toDynamicValue(x)
        dp.apply(dv, mode).flatMap(newDv => source.fromDynamicValue(newDv))
      case None => Right(x)
    }
  }

  /**
   * Check if this patch has any operations.
   */
  def isEmpty: Boolean = ops.isEmpty && dynamicPatch.forall(_.ops.isEmpty)

  /**
   * Check if this patch has operations.
   */
  def nonEmpty: Boolean = !isEmpty

  /**
   * Get the dynamic representation of this patch.
   */
  def toDynamicPatch: Option[DynamicPatch] = dynamicPatch
}

object Patch {
  /**
   * Create a Patch from a DynamicPatch.
   * The resulting patch applies the dynamic operations when executed.
   */
  def apply[S](dynamicPatch: DynamicPatch, schema: Schema[S]): Patch[S] =
    new Patch(Vector.empty, schema, Some(dynamicPatch))

  /**
   * Create an empty patch that does nothing.
   */
  def empty[S](implicit schema: Schema[S]): Patch[S] =
    new Patch(Vector.empty, schema, None)

  def replace[S, A](lens: Lens[S, A], a: A)(implicit source: Schema[S]): Patch[S] =
    Patch(Vector(LensPair(lens, LensOp.Replace(a))), source)

  def replace[S, A](optional: Optional[S, A], a: A)(implicit source: Schema[S]): Patch[S] =
    Patch(Vector(OptionalPair(optional, OptionalOp.Replace(a))), source)

  def replace[S, A](traversal: Traversal[S, A], a: A)(implicit source: Schema[S]): Patch[S] =
    Patch(Vector(TraversalPair(traversal, TraversalOp.Replace(a))), source)

  def replace[S, A <: S](prism: Prism[S, A], a: A)(implicit source: Schema[S]): Patch[S] =
    Patch(Vector(PrismPair(prism, PrismOp.Replace(a))), source)

  sealed trait Op[A]

  sealed trait LensOp[A] extends Op[A]

  object LensOp {
    case class Replace[A](a: A) extends LensOp[A]
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
