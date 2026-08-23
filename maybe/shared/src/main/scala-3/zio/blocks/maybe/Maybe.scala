/*
 * Copyright 2024-2026 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zio.blocks.maybe

import scala.Conversion
import scala.language.implicitConversions

/**
 * Wraps a value so that "present of absent" is distinguishable from absent.
 *
 * `Maybe[+A]` is an opaque type alias for `A | Absent.type | Present[A]`. A
 * plain non-absent value of type `A` is a present `Maybe[A]` with zero
 * allocation; the `Absent` singleton is `Maybe.absent`; and `Present(a)` is a
 * present `Maybe[A]` whose value is `a` — used to represent "present of absent"
 * in nested `Maybe`s (e.g. `Maybe.present(Maybe.absent)`), which a bare
 * `Absent` cannot express.
 *
 * NOTE on soundness: `Maybe.present` (and only `present`) wraps a user-supplied
 * `Present[_]` argument in an *extra* `Present` layer (`present(Present(x))` ==
 * `Present(Present(x))`). This ensures `unsafeGet` (used by codecs) returns the
 * correct inner `Present(x)` without double-unwrapping. The public
 * `case Present(v)` / `case Absent` matching idiom is unaffected.
 */
final class Present[+A](val value: A) {
  override def equals(other: Any): Boolean = other match {
    case p: Present[?] => value == p.value
    case _             => false
  }
  override def hashCode: Int    = if (value == null) 0 else value.hashCode // value CAN be null (Present(null))
  override def toString: String = s"Present($value)"
}

object Present {
  def apply[A](value: A): Present[A] = new Present(value)

  /**
   * Matches BOTH present shapes: a raw non-Absent value and a Present(...)
   * wrapper.
   */
  def unapply[A](maybe: Maybe[A]): Option[A] =
    if (maybe.asInstanceOf[AnyRef] eq zio.blocks.maybe.Absent) None
    else Some(Maybe.unsafeGet(maybe).asInstanceOf[A])
}

/**
 * Plain object (NOT case object) — enables `case Absent` as a stable-identifier
 * pattern.
 */
object Absent

opaque type Maybe[+A] = A | Absent.type | Present[A]

object Maybe {

  /**
   * Wraps a value in `Maybe`, treating `null` (and the Absent sentinel) as
   * absent.
   *
   * Unlike `present`, `apply` collapses `null`/`Absent` to `Maybe.absent`. Use
   * `present` when the value being wrapped is itself a `Maybe` that may be
   * absent.
   */
  inline def apply[A](a: A): Maybe[A] =
    if (a == null) zio.blocks.maybe.Absent.asInstanceOf[Maybe[A]] else a.asInstanceOf[Maybe[A]]

  /**
   * Wraps a value in `Maybe`, preserving present-ness even for `null` or
   * `Absent`.
   *
   * A non-null/non-Absent value is returned as-is (zero allocation). A `null`
   * value is wrapped as `Present(null)`, and the `Absent` sentinel is wrapped
   * as `Present(Absent)`; both are distinguishable from `Maybe.absent` (the
   * `Absent` singleton). This is what makes nested `Maybe`s sound:
   * `Maybe.present(Maybe.absent)` is `Present(Absent)`, not `Maybe.absent`.
   *
   * Special case for soundness (see Present scaladoc): if the argument is
   * itself a `Present[_]`, it is wrapped in an extra `Present` layer so that
   * `unsafeGet` returns the original `Present` (preventing CCE when
   * `A = Present[T]`). Normal `present(v)` for non-Present v remains
   * zero-alloc.
   */
  inline def present[A](a: A): Maybe[A] =
    if (a == null) zio.blocks.maybe.Present(null).asInstanceOf[Maybe[A]]
    else if (a.asInstanceOf[AnyRef] eq zio.blocks.maybe.Absent)
      zio.blocks.maybe.Present(zio.blocks.maybe.Absent).asInstanceOf[Maybe[A]]
    else
      a.asInstanceOf[AnyRef] match {
        case p: zio.blocks.maybe.Present[?] => zio.blocks.maybe.Present(p).asInstanceOf[Maybe[A]]
        case _                              => a.asInstanceOf[Maybe[A]]
      }
  inline def absent[A]: Maybe[A] = zio.blocks.maybe.Absent.asInstanceOf[Maybe[A]]
  inline def empty[A]: Maybe[A]  = zio.blocks.maybe.Absent.asInstanceOf[Maybe[A]]

  def fromOption[A](opt: Option[A]): Maybe[A] = opt match {
    case Some(a) => present(a)
    case None    => zio.blocks.maybe.Absent.asInstanceOf[Maybe[A]]
  }

  given [A]: Conversion[Option[A], Maybe[A]] with {
    def apply(opt: Option[A]): Maybe[A] = fromOption(opt)
  }

  final class WithFilter[A](self: Maybe[A], predicate: A => Boolean) {
    def map[B](f: A => B): Maybe[B]                = self.filter(predicate).map(f)
    def flatMap[B](f: A => Maybe[B]): Maybe[B]     = self.filter(predicate).flatMap(f)
    def foreach[U](f: A => U): Unit                = self.filter(predicate).foreach(f)
    def withFilter(q: A => Boolean): WithFilter[A] = new WithFilter(self, x => predicate(x) && q(x))
  }

  /** Low-level check used by schema codecs. Not for public use. */
  private[blocks] def unsafeIsAbsent(x: Maybe[Any]): Boolean =
    x.asInstanceOf[AnyRef] eq zio.blocks.maybe.Absent

  /**
   * Low-level unwrap used by schema codecs. Returns the inner value or null if
   * absent (Absent maps to null for codec compatibility).
   *
   * Inline so hot paths pay no virtual dispatch: absent and raw-value cases
   * cost one reference comparison; only a Present wrapper pays a type test.
   */
  private[blocks] inline def unsafeGet(x: Maybe[Any]): Any = {
    val ref = x.asInstanceOf[AnyRef]
    if (ref eq zio.blocks.maybe.Absent) null
    else if (ref.isInstanceOf[zio.blocks.maybe.Present[?]])
      ref.asInstanceOf[zio.blocks.maybe.Present[?]].value
    else x
  }

  /**
   * Low-level wrap used by schema codecs. Wraps a value (or null for absent)
   * into Maybe. null becomes the Absent singleton.
   */
  private[blocks] def unsafeWrap[A](x: Any): Maybe[A] =
    if (x == null) zio.blocks.maybe.Absent.asInstanceOf[Maybe[A]] else x.asInstanceOf[Maybe[A]]

  extension [A](self: Maybe[A]) {
    inline def isAbsent: Boolean  = self.asInstanceOf[AnyRef] eq zio.blocks.maybe.Absent
    inline def isPresent: Boolean = !(self.asInstanceOf[AnyRef] eq zio.blocks.maybe.Absent)
    inline def isEmpty: Boolean   = self.asInstanceOf[AnyRef] eq zio.blocks.maybe.Absent
    inline def isDefined: Boolean = !(self.asInstanceOf[AnyRef] eq zio.blocks.maybe.Absent)
    inline def nonEmpty: Boolean  = !(self.asInstanceOf[AnyRef] eq zio.blocks.maybe.Absent)
    inline def get: A             =
      if (self.asInstanceOf[AnyRef] eq zio.blocks.maybe.Absent) throw new NoSuchElementException("Maybe.absent.get")
      else unsafeGet(self).asInstanceOf[A]
    inline def getOrNull: A | Null                 = unsafeGet(self).asInstanceOf[A | Null]
    inline def getOrElse[B >: A](default: => B): B =
      if (self.asInstanceOf[AnyRef] eq zio.blocks.maybe.Absent) default else unsafeGet(self).asInstanceOf[A]
    inline def orElse[B >: A](alternative: => Maybe[B]): Maybe[B] =
      if (self.asInstanceOf[AnyRef] eq zio.blocks.maybe.Absent) alternative else self
    inline def orNull[B >: A](using ev: Null <:< B): B =
      if (self.asInstanceOf[AnyRef] eq zio.blocks.maybe.Absent) ev(null) else unsafeGet(self).asInstanceOf[A]
    inline def fold[B](ifAbsent: => B)(ifPresent: A => B): B =
      if (self.asInstanceOf[AnyRef] eq zio.blocks.maybe.Absent) ifAbsent else ifPresent(unsafeGet(self).asInstanceOf[A])
    inline def toOption: Option[A] =
      if (self.asInstanceOf[AnyRef] eq zio.blocks.maybe.Absent) None else Some(unsafeGet(self).asInstanceOf[A])
    inline def toList: List[A] =
      if (self.asInstanceOf[AnyRef] eq zio.blocks.maybe.Absent) Nil else unsafeGet(self).asInstanceOf[A] :: Nil
    inline def toSeq: Seq[A] =
      if (self.asInstanceOf[AnyRef] eq zio.blocks.maybe.Absent) Seq.empty else Seq(unsafeGet(self).asInstanceOf[A])
    inline def iterator: Iterator[A] =
      if (self.asInstanceOf[AnyRef] eq zio.blocks.maybe.Absent) Iterator.empty
      else Iterator.single(unsafeGet(self).asInstanceOf[A])
    inline def map[B](f: A => B): Maybe[B] =
      if (self.asInstanceOf[AnyRef] eq zio.blocks.maybe.Absent) zio.blocks.maybe.Absent.asInstanceOf[Maybe[B]]
      else present(f(unsafeGet(self).asInstanceOf[A]))
    inline def flatMap[B](f: A => Maybe[B]): Maybe[B] =
      if (self.asInstanceOf[AnyRef] eq zio.blocks.maybe.Absent) zio.blocks.maybe.Absent.asInstanceOf[Maybe[B]]
      else f(unsafeGet(self).asInstanceOf[A])
    inline def flatten[B](using ev: A <:< Maybe[B]): Maybe[B] =
      if (self.asInstanceOf[AnyRef] eq zio.blocks.maybe.Absent) zio.blocks.maybe.Absent.asInstanceOf[Maybe[B]]
      else ev(unsafeGet(self).asInstanceOf[A])
    inline def foreach[U](f: A => U): Unit =
      if (!(self.asInstanceOf[AnyRef] eq zio.blocks.maybe.Absent)) {
        f(unsafeGet(self).asInstanceOf[A])
        ()
      }
    inline def contains[A1 >: A](elem: A1): Boolean =
      if (self.asInstanceOf[AnyRef] eq zio.blocks.maybe.Absent) false else unsafeGet(self).asInstanceOf[A] == elem
    inline def exists(p: A => Boolean): Boolean =
      if (self.asInstanceOf[AnyRef] eq zio.blocks.maybe.Absent) false else p(unsafeGet(self).asInstanceOf[A])
    inline def forall(p: A => Boolean): Boolean =
      if (self.asInstanceOf[AnyRef] eq zio.blocks.maybe.Absent) true else p(unsafeGet(self).asInstanceOf[A])
    inline def filter(p: A => Boolean): Maybe[A] =
      if (self.asInstanceOf[AnyRef] eq zio.blocks.maybe.Absent) zio.blocks.maybe.Absent.asInstanceOf[Maybe[A]]
      else if (p(unsafeGet(self).asInstanceOf[A])) self
      else zio.blocks.maybe.Absent.asInstanceOf[Maybe[A]]
    inline def filterNot(p: A => Boolean): Maybe[A] =
      if (self.asInstanceOf[AnyRef] eq zio.blocks.maybe.Absent) zio.blocks.maybe.Absent.asInstanceOf[Maybe[A]]
      else if (p(unsafeGet(self).asInstanceOf[A])) zio.blocks.maybe.Absent.asInstanceOf[Maybe[A]]
      else self
    inline def collect[B](pf: PartialFunction[A, B]): Maybe[B] =
      if (self.asInstanceOf[AnyRef] eq zio.blocks.maybe.Absent) zio.blocks.maybe.Absent.asInstanceOf[Maybe[B]]
      else {
        val value = unsafeGet(self).asInstanceOf[A]
        if (pf.isDefinedAt(value)) present(pf(value)) else zio.blocks.maybe.Absent.asInstanceOf[Maybe[B]]
      }
    def withFilter(p: A => Boolean): WithFilter[A] =
      new WithFilter(self, p)
    inline def toRight[X](left: => X): Either[X, A] =
      if (self.asInstanceOf[AnyRef] eq zio.blocks.maybe.Absent) Left(left) else Right(unsafeGet(self).asInstanceOf[A])
    inline def toLeft[X](right: => X): Either[A, X] =
      if (self.asInstanceOf[AnyRef] eq zio.blocks.maybe.Absent) Right(right) else Left(unsafeGet(self).asInstanceOf[A])
    inline def zip[B](that: Maybe[B]): Maybe[(A, B)] =
      if (
        (self.asInstanceOf[AnyRef] eq zio.blocks.maybe.Absent) || (that.asInstanceOf[AnyRef] eq zio.blocks.maybe.Absent)
      ) zio.blocks.maybe.Absent.asInstanceOf[Maybe[(A, B)]]
      else present((unsafeGet(self).asInstanceOf[A], unsafeGet(that).asInstanceOf[B]))
    inline def unzip[A1, A2](using ev: A <:< (A1, A2)): (Maybe[A1], Maybe[A2]) =
      if (self.asInstanceOf[AnyRef] eq zio.blocks.maybe.Absent)
        (zio.blocks.maybe.Absent.asInstanceOf[Maybe[A1]], zio.blocks.maybe.Absent.asInstanceOf[Maybe[A2]])
      else {
        val value = ev(unsafeGet(self).asInstanceOf[A])
        (present(value._1), present(value._2))
      }
    inline def unzip3[A1, A2, A3](using ev: A <:< (A1, A2, A3)): (Maybe[A1], Maybe[A2], Maybe[A3]) =
      if (self.asInstanceOf[AnyRef] eq zio.blocks.maybe.Absent)
        (
          zio.blocks.maybe.Absent.asInstanceOf[Maybe[A1]],
          zio.blocks.maybe.Absent.asInstanceOf[Maybe[A2]],
          zio.blocks.maybe.Absent.asInstanceOf[Maybe[A3]]
        )
      else {
        val value = ev(unsafeGet(self).asInstanceOf[A])
        (present(value._1), present(value._2), present(value._3))
      }
  }
}
