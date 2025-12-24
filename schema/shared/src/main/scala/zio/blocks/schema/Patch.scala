package zio.blocks.schema

import zio.blocks.schema.binding.SeqConstructor
import scala.annotation.tailrec

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

  def apply(s: S): S = apply(s, PatchMode.Strict)

  def apply(s: S, mode: PatchMode): S =
    ops.foldLeft[S](s) { (s, single) =>
      single match {
        case LensPair(optic, LensOp.Replace(a))            => optic.replace(s, a)
        case LensPair(optic, LensOp.SequenceUpdate(edits)) =>
          applySequenceUpdate(s, optic, edits, mode)
        case PrismPair(optic, PrismOp.Replace(a))         => optic.replace(s, a)
        case OptionalPair(optic, OptionalOp.Replace(a))   => optic.replace(s, a)
        case TraversalPair(optic, TraversalOp.Replace(a)) => optic.modify(s, _ => a)
      }
    }

  def applyOption(s: S): Option[S] = applyOption(s, PatchMode.Strict)

  def applyOption(s: S, mode: PatchMode): Option[S] = {
    var x   = s
    val len = ops.length
    var idx = 0
    while (idx < len) {
      ops(idx) match {
        case LensPair(optic, LensOp.Replace(a)) =>
          x = optic.replace(x, a)
        case LensPair(optic, LensOp.SequenceUpdate(edits)) =>
          try {
            x = applySequenceUpdate(x, optic, edits, mode)
          } catch {
            case _: Throwable => return None
          }
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

  def applyOrFail(s: S): Either[OpticCheck, S] = applyOrFail(s, PatchMode.Strict)

  def applyOrFail(s: S, mode: PatchMode): Either[OpticCheck, S] = {
    var x   = s
    val len = ops.length
    var idx = 0
    while (idx < len) {
      ops(idx) match {
        case LensPair(optic, LensOp.Replace(a)) =>
          x = optic.replace(x, a)
        case LensPair(optic, LensOp.SequenceUpdate(edits)) =>
          try {
            x = applySequenceUpdate(x, optic, edits, mode)
          } catch {
            case e: Throwable =>
              val error = new OpticCheck.UnexpectedCase("sequence", e.getMessage, optic.toDynamic, optic.toDynamic, x)
              return new Left(new OpticCheck(new ::(error, Nil)))
          }
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

  private def applySequenceUpdate(s: S, optic: Lens[S, _], edits: Vector[SequenceEdit[_]], mode: PatchMode): S =
    optic.focus.asSequenceUnknown match {
      case Some(seqMeta) =>
        val col           = optic.get(s)
        val seq           = seqMeta.sequence
        val deconstructor = seq.seqDeconstructor
        val iter          = deconstructor.deconstruct(col.asInstanceOf[seqMeta.CollectionType[Any]])
        var vec           = Vector.empty[Any]
        while (iter.hasNext) vec = vec :+ iter.next()

        val resultList: List[Any] =
          applyEdits[Any](vec.toList, edits.toList.asInstanceOf[List[SequenceEdit[Any]]], mode)

        type AnyCol[x] = Any
        val constructor = seq.seqConstructor.asInstanceOf[SeqConstructor[AnyCol]]
        val builder     = constructor.newObjectBuilder[Any](resultList.size)
        val resIter     = resultList.iterator
        while (resIter.hasNext) {
          constructor.addObject[Any](builder, resIter.next())
        }
        val newCol = constructor.resultObject(builder)

        optic.asInstanceOf[Lens[S, Any]].replace(s, newCol)

      case None => throw new RuntimeException("Cannot apply SequenceUpdate to non-sequence field")
    }

  private def applyEdits[A](in: List[A], edits: List[SequenceEdit[_]], mode: PatchMode): List[A] = {
    @tailrec
    def calc(in: List[A], edits: List[SequenceEdit[_]], result: List[A]): List[A] = (in, edits) match {
      case (_ :: _, Nil)                                                               => throw new RuntimeException("Incorrect Patch - no instructions for remaining items")
      case (h :: _, SequenceEdit.Delete(s) :: _) if mode == PatchMode.Strict && s != h =>
        throw new RuntimeException(s"Cannot Delete $s - current item is $h")
      case (Nil, SequenceEdit.Delete(s) :: _)                                        => throw new RuntimeException(s"Cannot Delete $s - no items left")
      case (_ :: t, SequenceEdit.Delete(_) :: tail)                                  => calc(t, tail, result)
      case (h :: _, SequenceEdit.Keep(s) :: _) if mode == PatchMode.Strict && s != h =>
        throw new RuntimeException(s"Cannot Keep $s - current item is $h")
      case (Nil, SequenceEdit.Keep(s) :: _)       => throw new RuntimeException(s"Cannot Keep $s - no items left")
      case (h :: t, SequenceEdit.Keep(_) :: tail) => calc(t, tail, result :+ h)
      case (in, SequenceEdit.Insert(s) :: tail)   => calc(in, tail, result :+ s.asInstanceOf[A])
      case (Nil, Nil)                             => result
    }
    calc(in, edits, Nil)
  }

  def mapLens[T](lens: Lens[T, S])(implicit schema: Schema[T]): Patch[T] =
    Patch(
      ops.map { pair =>
        (pair: @unchecked) match {
          case p: LensPair[S, _] =>
            LensPair(lens(p.optic.asInstanceOf[Lens[S, Any]]), p.op.asInstanceOf[LensOp[Any]])
          case p: PrismPair[S, _] =>
            // A is a subtype of S, so we can treat it as S for the composition
            val newOptic = lens(p.optic.asInstanceOf[Prism[S, S]])
            val newOp    = p.op.asInstanceOf[PrismOp[S]] match {
              case PrismOp.Replace(a) => OptionalOp.Replace(a)
            }
            OptionalPair(newOptic, newOp)
          case p: OptionalPair[S, _] =>
            OptionalPair(lens(p.optic.asInstanceOf[Optional[S, Any]]), p.op.asInstanceOf[OptionalOp[Any]])
          case p: TraversalPair[S, _] =>
            TraversalPair(lens(p.optic.asInstanceOf[Traversal[S, Any]]), p.op.asInstanceOf[TraversalOp[Any]])
        }
      }.asInstanceOf[Vector[Patch.Pair[T, ?]]],
      schema
    )
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

  def empty[S](implicit source: Schema[S]): Patch[S] = Patch(Vector.empty, source)

  sealed trait Op[+A]

  sealed trait LensOp[+A] extends Op[A]

  object LensOp {
    case class Replace[+A](a: A)                                  extends LensOp[A]
    case class SequenceUpdate[+A](edits: Vector[SequenceEdit[A]]) extends LensOp[Nothing]
  }

  sealed trait PrismOp[+A] extends Op[A]

  object PrismOp {
    case class Replace[+A](a: A) extends PrismOp[A]
  }

  sealed trait OptionalOp[+A] extends Op[A]

  object OptionalOp {
    case class Replace[+A](a: A) extends OptionalOp[A]
  }

  sealed trait TraversalOp[+A] extends Op[A]

  object TraversalOp {
    case class Replace[+A](a: A) extends TraversalOp[A]
  }

  sealed trait Pair[S, A] {
    def optic: Optic[S, A]

    def op: Op[A]
  }

  case class LensPair[S, A](optic: Lens[S, A], op: LensOp[A]) extends Pair[S, A]

  case class PrismPair[S, A <: S](optic: Prism[S, A], op: PrismOp[A]) extends Pair[S, A]

  case class OptionalPair[S, A](optic: Optional[S, A], op: OptionalOp[A]) extends Pair[S, A]

  case class TraversalPair[S, A](optic: Traversal[S, A], op: TraversalOp[A]) extends Pair[S, A]

  sealed trait SequenceEdit[+A]
  object SequenceEdit {
    case class Keep[+A](a: A)   extends SequenceEdit[A]
    case class Insert[+A](a: A) extends SequenceEdit[A]
    case class Delete[+A](a: A) extends SequenceEdit[A]
  }

  sealed trait PatchMode
  object PatchMode {
    case object Strict  extends PatchMode
    case object Lenient extends PatchMode
  }
}
