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

sealed trait MaybeTag

object MaybeTag {
  implicit class MaybeOps[A](val self: A with MaybeTag) {
    @inline def isAbsent: Boolean  = self.asInstanceOf[AnyRef] eq null
    @inline def isPresent: Boolean = !(self.asInstanceOf[AnyRef] eq null)
    @inline def isEmpty: Boolean   = self.asInstanceOf[AnyRef] eq null
    @inline def isDefined: Boolean = !(self.asInstanceOf[AnyRef] eq null)
    @inline def nonEmpty: Boolean  = !(self.asInstanceOf[AnyRef] eq null)
    @inline def get: A             =
      if (self.asInstanceOf[AnyRef] eq null) throw new NoSuchElementException("Maybe.absent.get")
      else self.asInstanceOf[A]
    @inline def getOrElse[B >: A](default: => B): B =
      if (self.asInstanceOf[AnyRef] eq null) default else self.asInstanceOf[A]
    @inline def orElse[B >: A](alternative: => zio.blocks.maybe.Maybe[B]): zio.blocks.maybe.Maybe[B] =
      if (self.asInstanceOf[AnyRef] eq null) alternative else self.asInstanceOf[zio.blocks.maybe.Maybe[B]]
    @inline def orNull[B >: A](implicit ev: Null <:< B): B =
      if (self.asInstanceOf[AnyRef] eq null) null else self.asInstanceOf[A]
    @inline def fold[B](ifAbsent: => B)(ifPresent: A => B): B =
      if (self.asInstanceOf[AnyRef] eq null) ifAbsent else ifPresent(self.asInstanceOf[A])
    @inline def toOption: Option[A] =
      if (self.asInstanceOf[AnyRef] eq null) None else Some(self.asInstanceOf[A])
    @inline def toList: List[A] =
      if (self.asInstanceOf[AnyRef] eq null) Nil else self.asInstanceOf[A] :: Nil
    @inline def toSeq: Seq[A] =
      if (self.asInstanceOf[AnyRef] eq null) Seq.empty else Seq(self.asInstanceOf[A])
    @inline def iterator: Iterator[A] =
      if (self.asInstanceOf[AnyRef] eq null) Iterator.empty else Iterator.single(self.asInstanceOf[A])
    @inline def map[B](f: A => B): zio.blocks.maybe.Maybe[B] =
      if (self.asInstanceOf[AnyRef] eq null) null.asInstanceOf[zio.blocks.maybe.Maybe[B]]
      else f(self.asInstanceOf[A]).asInstanceOf[zio.blocks.maybe.Maybe[B]]
    @inline def flatMap[B](f: A => zio.blocks.maybe.Maybe[B]): zio.blocks.maybe.Maybe[B] =
      if (self.asInstanceOf[AnyRef] eq null) null.asInstanceOf[zio.blocks.maybe.Maybe[B]]
      else f(self.asInstanceOf[A])
    @inline def flatten[B](implicit ev: A <:< zio.blocks.maybe.Maybe[B]): zio.blocks.maybe.Maybe[B] =
      if (self.asInstanceOf[AnyRef] eq null) null.asInstanceOf[zio.blocks.maybe.Maybe[B]]
      else ev(self.asInstanceOf[A])
    @inline def foreach[U](f: A => U): Unit =
      if (!(self.asInstanceOf[AnyRef] eq null)) {
        f(self.asInstanceOf[A])
        ()
      }
    @inline def contains[A1 >: A](elem: A1): Boolean =
      !(self.asInstanceOf[AnyRef] eq null) && self.asInstanceOf[A] == elem
    @inline def exists(p: A => Boolean): Boolean =
      !(self.asInstanceOf[AnyRef] eq null) && p(self.asInstanceOf[A])
    @inline def forall(p: A => Boolean): Boolean =
      (self.asInstanceOf[AnyRef] eq null) || p(self.asInstanceOf[A])
    @inline def filter(p: A => Boolean): zio.blocks.maybe.Maybe[A] =
      if (self.asInstanceOf[AnyRef] eq null) null.asInstanceOf[zio.blocks.maybe.Maybe[A]]
      else if (p(self.asInstanceOf[A])) self.asInstanceOf[zio.blocks.maybe.Maybe[A]]
      else null.asInstanceOf[zio.blocks.maybe.Maybe[A]]
    @inline def filterNot(p: A => Boolean): zio.blocks.maybe.Maybe[A] =
      if (self.asInstanceOf[AnyRef] eq null) null.asInstanceOf[zio.blocks.maybe.Maybe[A]]
      else if (p(self.asInstanceOf[A])) null.asInstanceOf[zio.blocks.maybe.Maybe[A]]
      else self.asInstanceOf[zio.blocks.maybe.Maybe[A]]
    @inline def collect[B](pf: PartialFunction[A, B]): zio.blocks.maybe.Maybe[B] =
      if (self.asInstanceOf[AnyRef] eq null) null.asInstanceOf[zio.blocks.maybe.Maybe[B]]
      else {
        val value = self.asInstanceOf[A]
        if (pf.isDefinedAt(value)) pf(value).asInstanceOf[zio.blocks.maybe.Maybe[B]]
        else null.asInstanceOf[zio.blocks.maybe.Maybe[B]]
      }
    @inline def withFilter(p: A => Boolean): MaybeWithFilter[A] =
      new MaybeWithFilter[A](self.asInstanceOf[zio.blocks.maybe.Maybe[A]], p)
    @inline def toRight[X](left: => X): Either[X, A] =
      if (self.asInstanceOf[AnyRef] eq null) Left(left) else Right(self.asInstanceOf[A])
    @inline def toLeft[X](right: => X): Either[A, X] =
      if (self.asInstanceOf[AnyRef] eq null) Right(right) else Left(self.asInstanceOf[A])
    @inline def zip[B](that: zio.blocks.maybe.Maybe[B]): zio.blocks.maybe.Maybe[(A, B)] =
      if ((self.asInstanceOf[AnyRef] eq null) || (that.asInstanceOf[AnyRef] eq null)) null.asInstanceOf[zio.blocks.maybe.Maybe[(A, B)]]
      else (self.asInstanceOf[A], that.asInstanceOf[B]).asInstanceOf[zio.blocks.maybe.Maybe[(A, B)]]
    @inline def unzip[A1, A2](implicit ev: A <:< (A1, A2)): (zio.blocks.maybe.Maybe[A1], zio.blocks.maybe.Maybe[A2]) =
      if (self.asInstanceOf[AnyRef] eq null)
        (null.asInstanceOf[zio.blocks.maybe.Maybe[A1]], null.asInstanceOf[zio.blocks.maybe.Maybe[A2]])
      else {
        val value = ev(self.asInstanceOf[A])
        (
          value._1.asInstanceOf[zio.blocks.maybe.Maybe[A1]],
          value._2.asInstanceOf[zio.blocks.maybe.Maybe[A2]]
        )
      }
    @inline def unzip3[A1, A2, A3](implicit ev: A <:< (A1, A2, A3)): (zio.blocks.maybe.Maybe[A1], zio.blocks.maybe.Maybe[A2], zio.blocks.maybe.Maybe[A3]) =
      if (self.asInstanceOf[AnyRef] eq null)
        (
          null.asInstanceOf[zio.blocks.maybe.Maybe[A1]],
          null.asInstanceOf[zio.blocks.maybe.Maybe[A2]],
          null.asInstanceOf[zio.blocks.maybe.Maybe[A3]]
        )
      else {
        val value = ev(self.asInstanceOf[A])
        (
          value._1.asInstanceOf[zio.blocks.maybe.Maybe[A1]],
          value._2.asInstanceOf[zio.blocks.maybe.Maybe[A2]],
          value._3.asInstanceOf[zio.blocks.maybe.Maybe[A3]]
        )
      }
  }

  final class MaybeWithFilter[A](self: zio.blocks.maybe.Maybe[A], predicate: A => Boolean) {
    def map[B](f: A => B): zio.blocks.maybe.Maybe[B] = self.filter(predicate).map(f)
    def flatMap[B](f: A => zio.blocks.maybe.Maybe[B]): zio.blocks.maybe.Maybe[B] = self.filter(predicate).flatMap(f)
    def foreach[U](f: A => U): Unit = self.filter(predicate).foreach(f)
    def withFilter(q: A => Boolean): MaybeWithFilter[A] = new MaybeWithFilter[A](self, x => predicate(x) && q(x))
  }
}

object Maybe {
  @inline def apply[A](a: A): Maybe[A]             = if (a == null) null.asInstanceOf[Maybe[A]] else a.asInstanceOf[Maybe[A]]
  @inline def present[A](a: A): Maybe[A]      = a.asInstanceOf[Maybe[A]]
  @inline def absent[A]: Maybe[A]             = null.asInstanceOf[Maybe[A]]
  @inline def empty[A]: Maybe[A]              = null.asInstanceOf[Maybe[A]]
  def Absent: Maybe[Nothing]                  = null.asInstanceOf[Maybe[Nothing]]
  def fromOption[A](opt: Option[A]): Maybe[A] = opt match {
    case Some(a) => a.asInstanceOf[Maybe[A]]
    case None    => null.asInstanceOf[Maybe[A]]
  }

  implicit def optionToMaybe[A](opt: Option[A]): Maybe[A] = fromOption(opt)
}
