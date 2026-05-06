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

import scala.language.implicitConversions

sealed trait MaybeValue[+A]

object MaybeValue {
  final case class Present[+A](value: A) extends MaybeValue[A]
  case object Absent                     extends MaybeValue[Nothing]

  implicit final class MaybeOps[A](private val self: zio.blocks.maybe.Maybe[A]) extends AnyVal {
    @inline def isAbsent: Boolean  = self eq Absent
    @inline def isPresent: Boolean = self ne Absent
    @inline def isEmpty: Boolean   = self eq Absent
    @inline def isDefined: Boolean = self ne Absent
    @inline def nonEmpty: Boolean  = self ne Absent

    @inline def get: A =
      self match {
        case Present(value) => value
        case Absent         => throw new NoSuchElementException("Maybe.absent.get")
      }

    @inline def getOrElse[B >: A](default: => B): B =
      self match {
        case Present(value) => value
        case Absent         => default
      }

    @inline def orElse[B >: A](alternative: => zio.blocks.maybe.Maybe[B]): zio.blocks.maybe.Maybe[B] =
      self match {
        case Present(_) => self
        case Absent     => alternative
      }

    @inline def orNull[B >: A](implicit ev: Null <:< B): B =
      self match {
        case Present(value) => value
        case Absent         => ev(null)
      }

    @inline def fold[B](ifAbsent: => B)(ifPresent: A => B): B =
      self match {
        case Present(value) => ifPresent(value)
        case Absent         => ifAbsent
      }

    @inline def toOption: Option[A] =
      self match {
        case Present(value) => Some(value)
        case Absent         => None
      }

    @inline def toList: List[A] =
      self match {
        case Present(value) => value :: Nil
        case Absent         => Nil
      }

    @inline def toSeq: Seq[A] =
      self match {
        case Present(value) => Seq(value)
        case Absent         => Seq.empty
      }

    @inline def iterator: Iterator[A] =
      self match {
        case Present(value) => Iterator.single(value)
        case Absent         => Iterator.empty
      }

    @inline def map[B](f: A => B): zio.blocks.maybe.Maybe[B] =
      self match {
        case Present(value) => Maybe.present(f(value))
        case Absent         => Maybe.absent
      }

    @inline def flatMap[B](f: A => zio.blocks.maybe.Maybe[B]): zio.blocks.maybe.Maybe[B] =
      self match {
        case Present(value) => f(value)
        case Absent         => Maybe.absent
      }

    @inline def flatten[B](implicit ev: A <:< zio.blocks.maybe.Maybe[B]): zio.blocks.maybe.Maybe[B] =
      self match {
        case Present(value) => ev(value)
        case Absent         => Maybe.absent
      }

    @inline def foreach[U](f: A => U): Unit =
      self match {
        case Present(value) =>
          f(value)
          ()
        case Absent => ()
      }

    @inline def contains[A1 >: A](elem: A1): Boolean =
      self match {
        case Present(value) => value == elem
        case Absent         => false
      }

    @inline def exists(p: A => Boolean): Boolean =
      self match {
        case Present(value) => p(value)
        case Absent         => false
      }

    @inline def forall(p: A => Boolean): Boolean =
      self match {
        case Present(value) => p(value)
        case Absent         => true
      }

    @inline def filter(p: A => Boolean): zio.blocks.maybe.Maybe[A] =
      self match {
        case Present(value) if p(value) => self
        case _                          => Maybe.absent
      }

    @inline def filterNot(p: A => Boolean): zio.blocks.maybe.Maybe[A] =
      self match {
        case Present(value) if p(value) => Maybe.absent
        case Present(_)                 => self
        case Absent                     => Maybe.absent
      }

    @inline def collect[B](pf: PartialFunction[A, B]): zio.blocks.maybe.Maybe[B] =
      self match {
        case Present(value) if pf.isDefinedAt(value) => Maybe.present(pf(value))
        case _                                       => Maybe.absent
      }

    @inline def withFilter(p: A => Boolean): MaybeWithFilter[A] =
      new MaybeWithFilter[A](self, p)

    @inline def toRight[X](left: => X): Either[X, A] =
      self match {
        case Present(value) => Right(value)
        case Absent         => Left(left)
      }

    @inline def toLeft[X](right: => X): Either[A, X] =
      self match {
        case Present(value) => Left(value)
        case Absent         => Right(right)
      }

    @inline def zip[B](that: zio.blocks.maybe.Maybe[B]): zio.blocks.maybe.Maybe[(A, B)] =
      (self, that) match {
        case (Present(left), Present(right)) => Maybe.present((left, right))
        case _                               => Maybe.absent
      }

    @inline def unzip[A1, A2](implicit ev: A <:< (A1, A2)): (zio.blocks.maybe.Maybe[A1], zio.blocks.maybe.Maybe[A2]) =
      self match {
        case Present(value) =>
          val tuple = ev(value)
          (Maybe.present(tuple._1), Maybe.present(tuple._2))
        case Absent => (Maybe.absent, Maybe.absent)
      }

    @inline def unzip3[A1, A2, A3](implicit
      ev: A <:< (A1, A2, A3)
    ): (zio.blocks.maybe.Maybe[A1], zio.blocks.maybe.Maybe[A2], zio.blocks.maybe.Maybe[A3]) =
      self match {
        case Present(value) =>
          val tuple = ev(value)
          (Maybe.present(tuple._1), Maybe.present(tuple._2), Maybe.present(tuple._3))
        case Absent => (Maybe.absent, Maybe.absent, Maybe.absent)
      }
  }

  final class MaybeWithFilter[A](self: zio.blocks.maybe.Maybe[A], predicate: A => Boolean) {
    def map[B](f: A => B): zio.blocks.maybe.Maybe[B]                             = self.filter(predicate).map(f)
    def flatMap[B](f: A => zio.blocks.maybe.Maybe[B]): zio.blocks.maybe.Maybe[B] = self.filter(predicate).flatMap(f)
    def foreach[U](f: A => U): Unit                                              = self.filter(predicate).foreach(f)
    def withFilter(q: A => Boolean): MaybeWithFilter[A]                          = new MaybeWithFilter[A](self, x => predicate(x) && q(x))
  }
}

object Maybe {
  @inline def apply[A](a: A): Maybe[A]        = if (a == null) absent else present(a)
  @inline def present[A](a: A): Maybe[A]      = MaybeValue.Present(a)
  @inline def absent[A]: Maybe[A]             = MaybeValue.Absent
  @inline def empty[A]: Maybe[A]              = MaybeValue.Absent
  def Absent: Maybe[Nothing]                  = MaybeValue.Absent
  def fromOption[A](opt: Option[A]): Maybe[A] = opt match {
    case Some(a) => present(a)
    case None    => absent
  }

  implicit def optionToMaybe[A](opt: Option[A]): Maybe[A] = fromOption(opt)

  /** Low-level check used by schema codecs. Not for public use. */
  private[blocks] def unsafeIsAbsent(x: Maybe[Any]): Boolean = x eq MaybeValue.Absent

  /** Low-level unwrap used by schema codecs. Returns the inner value or null if absent. */
  private[blocks] def unsafeGet(x: Maybe[Any]): Any = x match {
    case MaybeValue.Present(value) => value
    case MaybeValue.Absent         => null
  }

  /** Low-level wrap used by schema codecs. Wraps a value (or null for absent) into Maybe. */
  private[blocks] def unsafeWrap[A](x: Any): Maybe[A] =
    if (x == null) MaybeValue.Absent else MaybeValue.Present(x.asInstanceOf[A])
}
