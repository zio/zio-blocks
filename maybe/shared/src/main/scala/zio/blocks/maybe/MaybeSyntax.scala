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

trait MaybeSyntax[A] {
  def isAbsent: Boolean
  def isPresent: Boolean
  def isEmpty: Boolean
  def isDefined: Boolean
  def nonEmpty: Boolean
  def get: A
  def getOrElse[B >: A](default: => B): B
  def orElse[B >: A](alternative: => Maybe[B]): Maybe[B]
  def orNull[B >: A](implicit ev: Null <:< B): B
  def fold[B](ifAbsent: => B)(ifPresent: A => B): B
  def toOption: Option[A]
  def toList: List[A]
  def toSeq: Seq[A]
  def iterator: Iterator[A]
  def map[B](f: A => B): Maybe[B]
  def flatMap[B](f: A => Maybe[B]): Maybe[B]
  def flatten[B](implicit ev: A <:< Maybe[B]): Maybe[B]
  def foreach[U](f: A => U): Unit
  def contains[A1 >: A](elem: A1): Boolean
  def exists(p: A => Boolean): Boolean
  def forall(p: A => Boolean): Boolean
  def filter(p: A => Boolean): Maybe[A]
  def filterNot(p: A => Boolean): Maybe[A]
  def collect[B](pf: PartialFunction[A, B]): Maybe[B]
  def withFilter(p: A => Boolean): MaybeWithFilter[A]
  def toRight[X](left: => X): Either[X, A]
  def toLeft[X](right: => X): Either[A, X]
  def zip[B](that: Maybe[B]): Maybe[(A, B)]
  def unzip[A1, A2](implicit ev: A <:< (A1, A2)): (Maybe[A1], Maybe[A2])
  def unzip3[A1, A2, A3](implicit ev: A <:< (A1, A2, A3)): (Maybe[A1], Maybe[A2], Maybe[A3])
}

trait MaybeSyntaxCompat {
  type MaybeWithFilter[A]
}
