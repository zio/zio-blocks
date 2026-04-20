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

package zio.blocks.schema

/**
 * Tag trait used to create the zero-allocation `Maybe` type alias in Scala 2.
 *
 * The companion object holds the implicit `MaybeOps` class so it is always in
 * implicit scope for `A with MaybeTag` — no import required.
 */
sealed trait MaybeTag

object MaybeTag {
  implicit class MaybeOps[A](val self: A with MaybeTag) {
    @inline def isAbsent: Boolean  = self.asInstanceOf[AnyRef] eq null
    @inline def isPresent: Boolean = !(self.asInstanceOf[AnyRef] eq null)
    @inline def isEmpty: Boolean   = self.asInstanceOf[AnyRef] eq null
    @inline def isDefined: Boolean = !(self.asInstanceOf[AnyRef] eq null)

    @inline def get: A =
      if (self.asInstanceOf[AnyRef] eq null) throw new NoSuchElementException("Maybe.absent.get")
      else self.asInstanceOf[A]

    @inline def getOrElse[B >: A](default: => B): B =
      if (self.asInstanceOf[AnyRef] eq null) default else self.asInstanceOf[A]

    @inline def fold[B](ifAbsent: => B)(ifPresent: A => B): B =
      if (self.asInstanceOf[AnyRef] eq null) ifAbsent else ifPresent(self.asInstanceOf[A])

    @inline def toOption: Option[A] =
      if (self.asInstanceOf[AnyRef] eq null) None else Some(self.asInstanceOf[A])

    @inline def map[B](f: A => B): zio.blocks.schema.Maybe[B] =
      if (self.asInstanceOf[AnyRef] eq null) null.asInstanceOf[zio.blocks.schema.Maybe[B]]
      else f(self.asInstanceOf[A]).asInstanceOf[zio.blocks.schema.Maybe[B]]

    @inline def flatMap[B](f: A => zio.blocks.schema.Maybe[B]): zio.blocks.schema.Maybe[B] =
      if (self.asInstanceOf[AnyRef] eq null) null.asInstanceOf[zio.blocks.schema.Maybe[B]]
      else f(self.asInstanceOf[A])
  }
}

/**
 * Zero-allocation optional type that avoids `Some` wrapper allocation.
 *
 * Uses `null` internally to represent absence. In Scala 2 this uses a tagged
 * type (`A with MaybeTag`); in Scala 3 it is an opaque type over `A | Null`.
 *
 * {{{
 * val x: Maybe[String] = Maybe.present("hello") // zero allocation
 * val y: Maybe[String] = Maybe.absent            // null
 * x.getOrElse("default") // "hello"
 * }}}
 */
object Maybe {

  @inline def present[A](a: A): Maybe[A] = a.asInstanceOf[Maybe[A]]

  @inline def absent[A]: Maybe[A] = null.asInstanceOf[Maybe[A]]

  def Absent: Maybe[Nothing] = null.asInstanceOf[Maybe[Nothing]]

  def fromOption[A](opt: Option[A]): Maybe[A] = opt match {
    case Some(a) => a.asInstanceOf[Maybe[A]]
    case None    => null.asInstanceOf[Maybe[A]]
  }

  /**
   * Internal: check if a Maybe value is absent (null). Used by codec
   * infrastructure.
   */
  private[schema] def isAbsent(value: Any): Boolean = value.asInstanceOf[AnyRef] eq null

  /**
   * Internal: unwrap a present Maybe to its underlying value. Used by codec
   * infrastructure.
   */
  private[schema] def unsafeUnwrap[A](value: Maybe[A]): A = value.asInstanceOf[A]

}
