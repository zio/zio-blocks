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
        case LensPair(optic, LensOp.Replace(a))             => optic.replace(s, a)
        case LensPair(optic, LensOp.Increment(delta))       => optic.modify(s, (v: Int) => v + delta)
        case LensPair(optic, LensOp.IncrementLong(delta))   => optic.modify(s, (v: Long) => v + delta)
        case LensPair(optic, LensOp.IncrementDouble(delta)) => optic.modify(s, (v: Double) => v + delta)
        case LensPair(optic, LensOp.EditString(ops))        =>
          optic.modify(s, (v: String) => StringOp.applyAll(v, ops).getOrElse(v))
        case PrismPair(optic, PrismOp.Replace(a))         => optic.replace(s, a)
        case OptionalPair(optic, OptionalOp.Replace(a))   => optic.replace(s, a)
        case TraversalPair(optic, TraversalOp.Replace(a)) => optic.modify(s, _ => a)
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
        case LensPair(optic, LensOp.Increment(delta)) =>
          x = optic.modify(x, (v: Int) => v + delta)
        case LensPair(optic, LensOp.IncrementLong(delta)) =>
          x = optic.modify(x, (v: Long) => v + delta)
        case LensPair(optic, LensOp.IncrementDouble(delta)) =>
          x = optic.modify(x, (v: Double) => v + delta)
        case LensPair(optic, LensOp.EditString(ops)) =>
          x = optic.modify(x, (v: String) => StringOp.applyAll(v, ops).getOrElse(v))
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
        case LensPair(optic, LensOp.Increment(delta)) =>
          x = optic.modify(x, (v: Int) => v + delta)
        case LensPair(optic, LensOp.IncrementLong(delta)) =>
          x = optic.modify(x, (v: Long) => v + delta)
        case LensPair(optic, LensOp.IncrementDouble(delta)) =>
          x = optic.modify(x, (v: Double) => v + delta)
        case LensPair(optic, LensOp.EditString(ops)) =>
          x = optic.modify(x, (v: String) => StringOp.applyAll(v, ops).getOrElse(v))
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

  /** Empty patch (monoid identity) */
  def empty[S](implicit source: Schema[S]): Patch[S] = Patch(Vector.empty, source)

  /** Set a field to a value (alias for replace) */
  def set[S, A](lens: Lens[S, A], value: A)(implicit source: Schema[S]): Patch[S] =
    replace(lens, value)

  /** Increment an Int field by delta */
  def increment[S](lens: Lens[S, Int], delta: Int)(implicit source: Schema[S]): Patch[S] =
    Patch(Vector(LensPair(lens, LensOp.Increment(delta))), source)

  /** Increment a Long field by delta */
  def incrementLong[S](lens: Lens[S, Long], delta: Long)(implicit source: Schema[S]): Patch[S] =
    Patch(Vector(LensPair(lens, LensOp.IncrementLong(delta))), source)

  /** Increment a Double field by delta */
  def incrementDouble[S](lens: Lens[S, Double], delta: Double)(implicit source: Schema[S]): Patch[S] =
    Patch(Vector(LensPair(lens, LensOp.IncrementDouble(delta))), source)

  /** Edit a String field with operations */
  def editString[S](lens: Lens[S, String], edits: Vector[StringOp])(implicit source: Schema[S]): Patch[S] =
    Patch(Vector(LensPair(lens, LensOp.EditString(edits))), source)

  sealed trait Op[A]

  sealed trait LensOp[A] extends Op[A]

  object LensOp {
    case class Replace[A](a: A)                  extends LensOp[A]
    case class Increment(delta: Int)             extends LensOp[Int]
    case class IncrementLong(delta: Long)        extends LensOp[Long]
    case class IncrementDouble(delta: Double)    extends LensOp[Double]
    case class EditString(ops: Vector[StringOp]) extends LensOp[String]
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
