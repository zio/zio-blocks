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
final case class Patch[S](ops: Vector[Patch.Single[?, S, ?]], source: Schema[S]) {
  def ++(that: Patch[S]): Patch[S] = Patch(this.ops ++ that.ops, this.source)

  def apply(s: S): Option[S] = ???

  def applyOrFail(s: S): Either[OpticCheck, S] = ???
}
object Patch {
  import Op._
  import LensOp._

  def set[S, A](lens: Lens[S, A], a: A)(implicit source: Schema[S]): Patch[S] =
    Patch(Vector(Single(lens, Set(a))), source)

  sealed trait Op[T, A]
  object Op {
    sealed trait LensOp[A] extends Op[Optic.Type.Lens, A]
    object LensOp {
      final case class Set[A](a: A) extends LensOp[A]
    }

    sealed trait PrismOp[A] extends Op[Optic.Type.Prism, A]

    sealed trait OptionalOp[A] extends Op[Optic.Type.Optional, A]

    sealed trait TraversalOp[A] extends Op[Optic.Type.Traversal, A]
  }

  final case class Single[T, S, A](optic: Optic[S, A] { type Type = T }, op: Op[T, A])
}
