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

package zio.blocks.html

import zio.blocks.chunk.Chunk

trait ToElements[-A] {
  def toElements(a: A): Chunk[Dom]
}

object ToElements {

  def apply[A](implicit ev: ToElements[A]): ToElements[A] = ev

  implicit val stringToElements: ToElements[String] = new ToElements[String] {
    def toElements(a: String): Chunk[Dom] = Chunk(Dom.Text(a))
  }

  implicit val intToElements: ToElements[Int] = new ToElements[Int] {
    def toElements(a: Int): Chunk[Dom] = Chunk(Dom.Text(a.toString))
  }

  implicit val longToElements: ToElements[Long] = new ToElements[Long] {
    def toElements(a: Long): Chunk[Dom] = Chunk(Dom.Text(a.toString))
  }

  implicit val doubleToElements: ToElements[Double] = new ToElements[Double] {
    def toElements(a: Double): Chunk[Dom] = Chunk(Dom.Text(a.toString))
  }

  implicit val booleanToElements: ToElements[Boolean] = new ToElements[Boolean] {
    def toElements(a: Boolean): Chunk[Dom] = Chunk(Dom.Text(a.toString))
  }

  implicit val charToElements: ToElements[Char] = new ToElements[Char] {
    def toElements(a: Char): Chunk[Dom] = Chunk(Dom.Text(a.toString))
  }

  implicit val domToElements: ToElements[Dom] = new ToElements[Dom] {
    def toElements(a: Dom): Chunk[Dom] = Chunk(a)
  }

  implicit def optionToElements[A](implicit ev: ToElements[A]): ToElements[Option[A]] =
    new ToElements[Option[A]] {
      def toElements(a: Option[A]): Chunk[Dom] = a match {
        case Some(v) => ev.toElements(v)
        case None    => Chunk.empty
      }
    }

  implicit def iterableToElements[A](implicit ev: ToElements[A]): ToElements[Iterable[A]] =
    new ToElements[Iterable[A]] {
      def toElements(a: Iterable[A]): Chunk[Dom] = {
        val builder = Chunk.newBuilder[Dom]
        val it      = a.iterator
        while (it.hasNext) {
          builder ++= ev.toElements(it.next())
        }
        builder.result()
      }
    }

  implicit def chunkToElements[A](implicit ev: ToElements[A]): ToElements[Chunk[A]] =
    new ToElements[Chunk[A]] {
      def toElements(a: Chunk[A]): Chunk[Dom] = {
        val builder = Chunk.newBuilder[Dom]
        var i       = 0
        while (i < a.length) {
          builder ++= ev.toElements(a(i))
          i += 1
        }
        builder.result()
      }
    }

  implicit def listToElements[A](implicit ev: ToElements[A]): ToElements[List[A]] =
    new ToElements[List[A]] {
      def toElements(a: List[A]): Chunk[Dom] = {
        val builder = Chunk.newBuilder[Dom]
        var rem     = a
        while (rem.nonEmpty) {
          builder ++= ev.toElements(rem.head)
          rem = rem.tail
        }
        builder.result()
      }
    }
}
