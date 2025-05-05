package zio.blocks.schema

/**
 * A Patch is a sequence of operations that can be applied to a value to produce
 * a new value. Because patches are described by reflective optics, finite
 * operations on specific types of optics, and values that can be serialized,
 * patches themselves can be serialized.
 *
 * ```scala
 * val patch1 = Patch.set(Person.name, "John")
 * val patch2 = Patch.set(Person.age, 30)
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
            case LensPair(optic, LensOp.Set(a)) => Some(optic.replace(s, a))
            case PrismPair(optic, _)            => Some(s)
            case OptionalPair(optic, _)         => Some(s)
            case TraversalPair(optic, _)        => Some(s)
          }
        case None => None
      }
    }
  }

  def applyOrFail(s: S): Either[OpticCheck, S] = ???
}
object Patch {
  def replace[S, A](lens: Lens[S, A], a: A)(implicit source: Schema[S]): Patch[S] =
    Patch(Vector(LensPair(lens, LensOp.Set(a))), source)

  sealed trait Op[A]

  sealed trait LensOp[A] extends Op[A]
  object LensOp {
    final case class Set[A](a: A) extends LensOp[A]
  }

  sealed trait PrismOp[A] extends Op[A]

  sealed trait OptionalOp[A] extends Op[A]

  sealed trait TraversalOp[A] extends Op[A]

  sealed trait Pair[S, A] {
    def optic: Optic[S, A]
    def op: Op[A]
  }

  final case class LensPair[S, A](optic: Lens[S, A], op: LensOp[A])                extends Pair[S, A]
  final case class PrismPair[S, A <: S](optic: Prism[S, A], op: PrismOp[A])        extends Pair[S, A]
  final case class OptionalPair[S, A](optic: Optional[S, A], op: OptionalOp[A])    extends Pair[S, A]
  final case class TraversalPair[S, A](optic: Traversal[S, A], op: TraversalOp[A]) extends Pair[S, A]
}
