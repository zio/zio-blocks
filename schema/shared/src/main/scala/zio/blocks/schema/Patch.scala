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

  def apply(s: S): S =
    ops.foldLeft[S](s) { (s, single) =>
      single match {
        case LensPair(optic, LensOp.Replace(a))           => optic.replace(s, a)
        case PrismPair(optic, PrismOp.Replace(a))         => optic.replace(s, a)
        case OptionalPair(optic, OptionalOp.Replace(a))   => optic.replace(s, a)
        case TraversalPair(optic, TraversalOp.Replace(a)) => optic.modify(s, _ => a)
      }
    }

  def applyOption(s: S): Option[S] = {
    var res: Option[S] = new Some(s)
    val len            = ops.length
    var idx            = 0
    while (idx < len) {
      res = res match {
        case Some(s) =>
          ops(idx) match {
            case LensPair(optic, LensOp.Replace(a))           => new Some(optic.replace(s, a))
            case PrismPair(optic, PrismOp.Replace(a))         => optic.replaceOption(s, a)
            case OptionalPair(optic, OptionalOp.Replace(a))   => optic.replaceOption(s, a)
            case TraversalPair(optic, TraversalOp.Replace(a)) => optic.modifyOption(s, _ => a)
          }
        case _ => return None
      }
      idx += 1
    }
    res
  }

  def applyOrFail(s: S): Either[OpticCheck, S] = {
    var res: Either[OpticCheck, S] = new Right(s)
    val len                        = ops.length
    var idx                        = 0
    while (idx < len) {
      res = res match {
        case Right(s) =>
          ops(idx) match {
            case LensPair(optic, LensOp.Replace(a))           => new Right(optic.replace(s, a))
            case PrismPair(optic, PrismOp.Replace(a))         => optic.replaceOrFail(s, a)
            case OptionalPair(optic, OptionalOp.Replace(a))   => optic.replaceOrFail(s, a)
            case TraversalPair(optic, TraversalOp.Replace(a)) => optic.modifyOrFail(s, _ => a)
          }
        case left => return left
      }
      idx += 1
    }
    res
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
