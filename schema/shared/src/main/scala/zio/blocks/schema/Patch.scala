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
        case LensPair(optic, LensOp.Increment(delta)) => 
          optic.asInstanceOf[Lens[S, Int]].modify(s, _ + delta)
        case LensPair(optic, LensOp.IncrementLong(delta)) => 
          optic.asInstanceOf[Lens[S, Long]].modify(s, _ + delta)
        case LensPair(optic, LensOp.IncrementDouble(delta)) => 
          optic.asInstanceOf[Lens[S, Double]].modify(s, _ + delta)
        case LensPair(optic, LensOp.EditString(ops)) => 
          optic.asInstanceOf[Lens[S, String]].modify(s, str => StringOp.applyOps(str, ops))
        case LensPair(optic, LensOp.AppendVector(elements)) =>
          optic.asInstanceOf[Lens[S, Vector[Any]]].modify(s, vec => vec ++ elements.asInstanceOf[Vector[Any]])
        case LensPair(optic, LensOp.InsertAt(index, elements)) =>
          optic.asInstanceOf[Lens[S, Vector[Any]]].modify(s, vec => {
            val (before, after) = vec.splitAt(index)
            before ++ elements.asInstanceOf[Vector[Any]] ++ after
          })
        case LensPair(optic, LensOp.DeleteAt(index, count)) =>
          optic.asInstanceOf[Lens[S, Vector[Any]]].modify(s, vec => 
            vec.take(index) ++ vec.drop(index + count))
        case LensPair(optic, LensOp.AddMapKey(key, value)) =>
          optic.asInstanceOf[Lens[S, Map[Any, Any]]].modify(s, map => 
            map + (key.asInstanceOf[Any] -> value.asInstanceOf[Any]))
        case LensPair(optic, LensOp.RemoveMapKey(key)) =>
          optic.asInstanceOf[Lens[S, Map[Any, Any]]].modify(s, map => 
            map - key.asInstanceOf[Any])
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
          x = optic.asInstanceOf[Lens[S, Int]].modify(x, _ + delta)
        case LensPair(optic, LensOp.IncrementLong(delta)) =>
          x = optic.asInstanceOf[Lens[S, Long]].modify(x, _ + delta)
        case LensPair(optic, LensOp.IncrementDouble(delta)) =>
          x = optic.asInstanceOf[Lens[S, Double]].modify(x, _ + delta)
        case LensPair(optic, LensOp.EditString(strOps)) =>
          x = optic.asInstanceOf[Lens[S, String]].modify(x, str => StringOp.applyOps(str, strOps))
        case LensPair(optic, LensOp.AppendVector(elements)) =>
          x = optic.asInstanceOf[Lens[S, Vector[Any]]].modify(x, vec => vec ++ elements.asInstanceOf[Vector[Any]])
        case LensPair(optic, LensOp.InsertAt(index, elements)) =>
          x = optic.asInstanceOf[Lens[S, Vector[Any]]].modify(x, vec => {
            val (before, after) = vec.splitAt(index)
            before ++ elements.asInstanceOf[Vector[Any]] ++ after
          })
        case LensPair(optic, LensOp.DeleteAt(index, count)) =>
          x = optic.asInstanceOf[Lens[S, Vector[Any]]].modify(x, vec => 
            vec.take(index) ++ vec.drop(index + count))
        case LensPair(optic, LensOp.AddMapKey(key, value)) =>
          x = optic.asInstanceOf[Lens[S, Map[Any, Any]]].modify(x, map => 
            map + (key.asInstanceOf[Any] -> value.asInstanceOf[Any]))
        case LensPair(optic, LensOp.RemoveMapKey(key)) =>
          x = optic.asInstanceOf[Lens[S, Map[Any, Any]]].modify(x, map => 
            map - key.asInstanceOf[Any])
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
          x = optic.asInstanceOf[Lens[S, Int]].modify(x, _ + delta)
        case LensPair(optic, LensOp.IncrementLong(delta)) =>
          x = optic.asInstanceOf[Lens[S, Long]].modify(x, _ + delta)
        case LensPair(optic, LensOp.IncrementDouble(delta)) =>
          x = optic.asInstanceOf[Lens[S, Double]].modify(x, _ + delta)
        case LensPair(optic, LensOp.EditString(strOps)) =>
          x = optic.asInstanceOf[Lens[S, String]].modify(x, str => StringOp.applyOps(str, strOps))
        case LensPair(optic, LensOp.AppendVector(elements)) =>
          x = optic.asInstanceOf[Lens[S, Vector[Any]]].modify(x, vec => vec ++ elements.asInstanceOf[Vector[Any]])
        case LensPair(optic, LensOp.InsertAt(index, elements)) =>
          x = optic.asInstanceOf[Lens[S, Vector[Any]]].modify(x, vec => {
            val (before, after) = vec.splitAt(index)
            before ++ elements.asInstanceOf[Vector[Any]] ++ after
          })
        case LensPair(optic, LensOp.DeleteAt(index, count)) =>
          x = optic.asInstanceOf[Lens[S, Vector[Any]]].modify(x, vec => 
            vec.take(index) ++ vec.drop(index + count))
        case LensPair(optic, LensOp.AddMapKey(key, value)) =>
          x = optic.asInstanceOf[Lens[S, Map[Any, Any]]].modify(x, map => 
            map + (key.asInstanceOf[Any] -> value.asInstanceOf[Any]))
        case LensPair(optic, LensOp.RemoveMapKey(key)) =>
          x = optic.asInstanceOf[Lens[S, Map[Any, Any]]].modify(x, map => 
            map - key.asInstanceOf[Any])
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
  
  /**
   * Empty patch (monoid identity).
   */
  def empty[S](implicit source: Schema[S]): Patch[S] =
    Patch(Vector.empty, source)
  
  /**
   * Set a field/element to a value via a lens.
   */
  def set[S, A](lens: Lens[S, A], a: A)(implicit source: Schema[S]): Patch[S] =
    Patch(Vector(LensPair(lens, LensOp.Replace(a))), source)
  
  /**
   * Set a field/element to a value via an optional.
   */
  def set[S, A](optional: Optional[S, A], a: A)(implicit source: Schema[S]): Patch[S] =
    Patch(Vector(OptionalPair(optional, OptionalOp.Replace(a))), source)
  
  /**
   * Set elements to a value via a traversal.
   */
  def set[S, A](traversal: Traversal[S, A], a: A)(implicit source: Schema[S]): Patch[S] =
    Patch(Vector(TraversalPair(traversal, TraversalOp.Replace(a))), source)
  
  /**
   * Set a case to a value via a prism.
   */
  def set[S, A <: S](prism: Prism[S, A], a: A)(implicit source: Schema[S]): Patch[S] =
    Patch(Vector(PrismPair(prism, PrismOp.Replace(a))), source)
  
  // Legacy replace methods for backward compatibility
  def replace[S, A](lens: Lens[S, A], a: A)(implicit source: Schema[S]): Patch[S] =
    Patch(Vector(LensPair(lens, LensOp.Replace(a))), source)

  def replace[S, A](optional: Optional[S, A], a: A)(implicit source: Schema[S]): Patch[S] =
    Patch(Vector(OptionalPair(optional, OptionalOp.Replace(a))), source)

  def replace[S, A](traversal: Traversal[S, A], a: A)(implicit source: Schema[S]): Patch[S] =
    Patch(Vector(TraversalPair(traversal, TraversalOp.Replace(a))), source)

  def replace[S, A <: S](prism: Prism[S, A], a: A)(implicit source: Schema[S]): Patch[S] =
    Patch(Vector(PrismPair(prism, PrismOp.Replace(a))), source)
  
  // Numeric increment operations
  
  /**
   * Increment an Int field by the specified delta.
   */
  def increment[S](lens: Lens[S, Int], delta: Int)(implicit source: Schema[S]): Patch[S] =
    Patch(Vector(LensPair(lens, LensOp.Increment(delta))), source)
  
  /**
   * Increment a Long field by the specified delta.
   */
  def incrementLong[S](lens: Lens[S, Long], delta: Long)(implicit source: Schema[S]): Patch[S] =
    Patch(Vector(LensPair(lens, LensOp.IncrementLong(delta))), source)
  
  /**
   * Increment a Double field by the specified delta.
   */
  def incrementDouble[S](lens: Lens[S, Double], delta: Double)(implicit source: Schema[S]): Patch[S] =
    Patch(Vector(LensPair(lens, LensOp.IncrementDouble(delta))), source)
  
  /**
   * Edit a String field with a sequence of string operations.
   */
  def editString[S](lens: Lens[S, String], ops: Vector[StringOp])(implicit source: Schema[S]): Patch[S] =
    Patch(Vector(LensPair(lens, LensOp.EditString(ops))), source)
  
  // Sequence operations
  
  /**
   * Append elements to a Vector field.
   */
  def append[S, A](lens: Lens[S, Vector[A]], elements: Vector[A])(implicit source: Schema[S]): Patch[S] =
    Patch(Vector(LensPair(lens, LensOp.AppendVector(elements))), source)
  
  /**
   * Insert elements at the specified index in a Vector field.
   */
  def insertAt[S, A](lens: Lens[S, Vector[A]], index: Int, elements: Vector[A])(implicit source: Schema[S]): Patch[S] =
    Patch(Vector(LensPair(lens, LensOp.InsertAt(index, elements))), source)
  
  /**
   * Delete elements starting at the specified index in a Vector field.
   */
  def deleteAt[S, A](lens: Lens[S, Vector[A]], index: Int, count: Int)(implicit source: Schema[S]): Patch[S] =
    Patch(Vector(LensPair(lens, LensOp.DeleteAt(index, count))), source)
  
  // Map operations
  
  /**
   * Add a key-value pair to a Map field.
   */
  def addKey[S, K, V](lens: Lens[S, Map[K, V]], key: K, value: V)(implicit source: Schema[S]): Patch[S] =
    Patch(Vector(LensPair(lens, LensOp.AddMapKey(key, value))), source)
  
  /**
   * Remove a key from a Map field.
   */
  def removeKey[S, K, V](lens: Lens[S, Map[K, V]], key: K)(implicit source: Schema[S]): Patch[S] =
    Patch(Vector(LensPair(lens, LensOp.RemoveMapKey(key))), source)

  sealed trait Op[A]


  sealed trait LensOp[A] extends Op[A]

  object LensOp {
    case class Replace[A](a: A) extends LensOp[A]
    case class Increment(delta: Int) extends LensOp[Int]
    case class IncrementLong(delta: Long) extends LensOp[Long]
    case class IncrementDouble(delta: Double) extends LensOp[Double]
    case class EditString(ops: Vector[StringOp]) extends LensOp[String]
    // Sequence operations
    case class AppendVector[A](elements: Vector[A]) extends LensOp[Vector[A]]
    case class InsertAt[A](index: Int, elements: Vector[A]) extends LensOp[Vector[A]]
    case class DeleteAt[A](index: Int, count: Int) extends LensOp[Vector[A]]
    // Map operations
    case class AddMapKey[K, V](key: K, value: V) extends LensOp[Map[K, V]]
    case class RemoveMapKey[K, V](key: K) extends LensOp[Map[K, V]]
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

