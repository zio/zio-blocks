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

opaque type Maybe[+A] = A | Null

object Maybe {
  inline def apply[A](a: A): Maybe[A] =
    if (a == null) null else a

  inline def present[A](a: A): Maybe[A] = a
  inline def absent[A]: Maybe[A]        = null
  inline def empty[A]: Maybe[A]         = null
  def Absent: Maybe[Nothing]            = null

  def fromOption[A](opt: Option[A]): Maybe[A] = opt match {
    case Some(a) => a
    case None    => null
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
  private[blocks] def unsafeIsAbsent(x: Maybe[Any]): Boolean = x == null

  /**
   * Low-level unwrap used by schema codecs. Returns the inner value or null if
   * absent.
   */
  private[blocks] def unsafeGet(x: Maybe[Any]): Any = x

  /**
   * Low-level wrap used by schema codecs. Wraps a value (or null for absent)
   * into Maybe.
   */
  private[blocks] def unsafeWrap[A](x: Any): Maybe[A] =
    if (x == null) null else x.asInstanceOf[Maybe[A]]

  extension [A](self: Maybe[A]) {
    inline def isAbsent: Boolean  = self == null
    inline def isPresent: Boolean = self != null
    inline def isEmpty: Boolean   = self == null
    inline def isDefined: Boolean = self != null
    inline def nonEmpty: Boolean  = self != null
    inline def get: A             =
      if (self == null) throw new NoSuchElementException("Maybe.absent.get")
      else self.asInstanceOf[A]
    inline def getOrElse[B >: A](default: => B): B =
      if (self == null) default else self.asInstanceOf[A]
    inline def orElse[B >: A](alternative: => Maybe[B]): Maybe[B] =
      if (self == null) alternative else self
    inline def orNull[B >: A](using ev: Null <:< B): B =
      if (self == null) ev(null) else self.asInstanceOf[A]
    inline def fold[B](ifAbsent: => B)(ifPresent: A => B): B =
      if (self == null) ifAbsent else ifPresent(self.asInstanceOf[A])
    inline def toOption: Option[A] =
      if (self == null) None else Some(self.asInstanceOf[A])
    inline def toList: List[A] =
      if (self == null) Nil else self.asInstanceOf[A] :: Nil
    inline def toSeq: Seq[A] =
      if (self == null) Seq.empty else Seq(self.asInstanceOf[A])
    inline def iterator: Iterator[A] =
      if (self == null) Iterator.empty else Iterator.single(self.asInstanceOf[A])
    inline def map[B](f: A => B): Maybe[B] =
      if (self == null) null else f(self.asInstanceOf[A])
    inline def flatMap[B](f: A => Maybe[B]): Maybe[B] =
      if (self == null) null else f(self.asInstanceOf[A])
    inline def flatten[B](using ev: A <:< Maybe[B]): Maybe[B] =
      if (self == null) null else ev(self.asInstanceOf[A])
    inline def foreach[U](f: A => U): Unit =
      if (self != null) {
        f(self.asInstanceOf[A])
        ()
      }
    inline def contains[A1 >: A](elem: A1): Boolean =
      if (self == null) false else self.asInstanceOf[A] == elem
    inline def exists(p: A => Boolean): Boolean =
      if (self == null) false else p(self.asInstanceOf[A])
    inline def forall(p: A => Boolean): Boolean =
      if (self == null) true else p(self.asInstanceOf[A])
    inline def filter(p: A => Boolean): Maybe[A] =
      if (self == null) null else if (p(self.asInstanceOf[A])) self else null
    inline def filterNot(p: A => Boolean): Maybe[A] =
      if (self == null) null else if (p(self.asInstanceOf[A])) null else self
    inline def collect[B](pf: PartialFunction[A, B]): Maybe[B] =
      if (self == null) null
      else {
        val value = self.asInstanceOf[A]
        if (pf.isDefinedAt(value)) pf(value) else null
      }
    def withFilter(p: A => Boolean): WithFilter[A] =
      new WithFilter(self, p)
    inline def toRight[X](left: => X): Either[X, A] =
      if (self == null) Left(left) else Right(self.asInstanceOf[A])
    inline def toLeft[X](right: => X): Either[A, X] =
      if (self == null) Right(right) else Left(self.asInstanceOf[A])
    inline def zip[B](that: Maybe[B]): Maybe[(A, B)] =
      if (self == null || that == null) null else (self.asInstanceOf[A], that.asInstanceOf[B])
    inline def unzip[A1, A2](using ev: A <:< (A1, A2)): (Maybe[A1], Maybe[A2]) =
      if (self == null) (null, null)
      else {
        val value = ev(self.asInstanceOf[A])
        (value._1, value._2)
      }
    inline def unzip3[A1, A2, A3](using ev: A <:< (A1, A2, A3)): (Maybe[A1], Maybe[A2], Maybe[A3]) =
      if (self == null) (null, null, null)
      else {
        val value = ev(self.asInstanceOf[A])
        (value._1, value._2, value._3)
      }
  }
}
