package zio.blocks.schema

/**
 * A Patch is a sequence of operations that can be applied to a value to produce
 * a new value. Because patches are described by reflective optics, finite
 * operations on specific types of optics, and values that can be serialized,
 * patches themselves can be serialized.
 *
 * ```scala
 * val patch1 = Patch.replace(Person.name, "John")
 * val patch2 = Patch.replace(Person.age, 30)
 *
 * val patch3 = patch1 ++ patch2
 *
 * patch3(Person("Jane", 25)) // Some(Person("John", 30))
 * ```
 */
final case class Patch[S](ops: Vector[Patch.Pair[S, ?]], source: Schema[S]) {
  def ++(that: Patch[S]): Patch[S] = Patch(this.ops ++ that.ops, this.source)

  def apply(s: S): Option[S] = {
    import Patch._

    ops.foldLeft[Option[S]](Some(s)) { (acc, single) =>
      acc match {
        case Some(s) =>
          single match {
            case LensPair(optic, LensOp.Set(a))               => Some(optic.replace(s, a))
            case PrismPair(optic, PrismOp.ReverseGet(a))      => Some(optic.reverseGet(a))
            case OptionalPair(optic, OptionalOp.Replace(a))   => Some(optic.replace(s, a))
            case TraversalPair(optic, TraversalOp.Replace(a)) => Some(optic.modify(s, _ => a))
          }
        case _ => None
      }
    }
  }

  def applyOrFail(s: S): Either[OpticCheck, S] = ???
}
object Patch {
  def replace[S, A](lens: Lens[S, A], a: A)(implicit source: Schema[S]): Patch[S] =
    Patch(Vector(LensPair(lens, LensOp.Set(a))), source)

  def replace[S, A](optional: Optional[S, A], a: A)(implicit source: Schema[S]): Patch[S] =
    Patch(Vector(OptionalPair(optional, OptionalOp.Replace(a))), source)

  def replace[S, A](traversal: Traversal[S, A], a: A)(implicit source: Schema[S]): Patch[S] =
    Patch(Vector(TraversalPair(traversal, TraversalOp.Replace(a))), source)

  def reverseGet[S, A <: S](prism: Prism[S, A], a: A)(implicit source: Schema[S]): Patch[S] =
    Patch(Vector(PrismPair(prism, PrismOp.ReverseGet(a))), source)

  sealed trait Op[A]

  sealed trait LensOp[A] extends Op[A]

  object LensOp {
    case class Set[A](a: A) extends LensOp[A]
  }

  sealed trait PrismOp[A] extends Op[A]

  object PrismOp {
    case class ReverseGet[A](a: A) extends PrismOp[A]
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
