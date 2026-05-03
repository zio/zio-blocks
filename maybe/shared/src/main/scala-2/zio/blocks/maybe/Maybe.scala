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

sealed trait MaybeTag

object MaybeTag {
  implicit class MaybeOps[A](val self: A with MaybeTag) {
    @inline def isAbsent: Boolean  = self.asInstanceOf[AnyRef] eq null
    @inline def isPresent: Boolean = !(self.asInstanceOf[AnyRef] eq null)
    @inline def isEmpty: Boolean   = self.asInstanceOf[AnyRef] eq null
    @inline def isDefined: Boolean = !(self.asInstanceOf[AnyRef] eq null)
    @inline def get: A             =
      if (self.asInstanceOf[AnyRef] eq null) throw new NoSuchElementException("Maybe.absent.get")
      else self.asInstanceOf[A]
    @inline def getOrElse[B >: A](default: => B): B =
      if (self.asInstanceOf[AnyRef] eq null) default else self.asInstanceOf[A]
    @inline def fold[B](ifAbsent: => B)(ifPresent: A => B): B =
      if (self.asInstanceOf[AnyRef] eq null) ifAbsent else ifPresent(self.asInstanceOf[A])
    @inline def toOption: Option[A] =
      if (self.asInstanceOf[AnyRef] eq null) None else Some(self.asInstanceOf[A])
    @inline def map[B](f: A => B): zio.blocks.maybe.Maybe[B] =
      if (self.asInstanceOf[AnyRef] eq null) null.asInstanceOf[zio.blocks.maybe.Maybe[B]]
      else f(self.asInstanceOf[A]).asInstanceOf[zio.blocks.maybe.Maybe[B]]
    @inline def flatMap[B](f: A => zio.blocks.maybe.Maybe[B]): zio.blocks.maybe.Maybe[B] =
      if (self.asInstanceOf[AnyRef] eq null) null.asInstanceOf[zio.blocks.maybe.Maybe[B]]
      else f(self.asInstanceOf[A])
  }
}

object Maybe {
  @inline def present[A](a: A): Maybe[A]      = a.asInstanceOf[Maybe[A]]
  @inline def absent[A]: Maybe[A]             = null.asInstanceOf[Maybe[A]]
  def Absent: Maybe[Nothing]                  = null.asInstanceOf[Maybe[Nothing]]
  def fromOption[A](opt: Option[A]): Maybe[A] = opt match {
    case Some(a) => a.asInstanceOf[Maybe[A]]
    case None    => null.asInstanceOf[Maybe[A]]
  }
}
