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

opaque type Maybe[+A] = A | Null

object Maybe {
  inline def present[A](a: A): Maybe[A] = a
  inline def absent[A]: Maybe[A]        = null
  def Absent: Maybe[Nothing]            = null

  def fromOption[A](opt: Option[A]): Maybe[A] = opt match {
    case Some(a) => a
    case None    => null
  }

  extension [A](self: Maybe[A]) {
    inline def isAbsent: Boolean  = self == null
    inline def isPresent: Boolean = self != null
    inline def isEmpty: Boolean   = self == null
    inline def isDefined: Boolean = self != null
    inline def get: A             =
      if (self == null) throw new NoSuchElementException("Maybe.absent.get")
      else self.asInstanceOf[A]
    inline def getOrElse[B >: A](default: => B): B =
      if (self == null) default else self.asInstanceOf[A]
    inline def fold[B](ifAbsent: => B)(ifPresent: A => B): B =
      if (self == null) ifAbsent else ifPresent(self.asInstanceOf[A])
    inline def toOption: Option[A] =
      if (self == null) None else Some(self.asInstanceOf[A])
    inline def map[B](f: A => B): Maybe[B] =
      if (self == null) null else f(self.asInstanceOf[A])
    inline def flatMap[B](f: A => Maybe[B]): Maybe[B] =
      if (self == null) null else f(self.asInstanceOf[A])
  }
}
