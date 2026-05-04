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

package zio.blocks.endpoint

import scala.language.implicitConversions

import zio.blocks.chunk.Chunk
import zio.blocks.combinators.Tuples
import zio.http.Path

/**
 * Composable path descriptor. Segments are combined with `/` or `++`, literal alternatives with `orElse`.
 * Use [[PathCodec.decode decode]] / [[PathCodec.format format]] for bidirectional path conversion,
 * and [[PathCodec.alternatives alternatives]] to expand `orElse` branches for routing-trie insertion.
 */
sealed trait PathCodec[A]

object PathCodec {

  private type AnyCombiner = Tuples.Tuples.WithOut[Any, Any, Any]

  given unitUnit: Tuples.Tuples.WithOut[Unit, Unit, Unit] = Tuples.Tuples.leftUnit[Unit]

  extension [A](self: PathCodec[A]) {
    def ++[B, C](that: PathCodec[B])(using
      combiner: Tuples.Tuples.WithOut[A, B, C]
    ): PathCodec[C] =
      if (self == empty) that.asInstanceOf[PathCodec[C]]
      else if (that == empty) self.asInstanceOf[PathCodec[C]]
      else Concat(self, that, combiner)

    def /[B, C](that: PathCodec[B])(using
      combiner: Tuples.Tuples.WithOut[A, B, C]
    ): PathCodec[C] =
      self ++ that

    def orElse(that: PathCodec[Unit])(using ev: A =:= Unit): PathCodec[Unit] =
      Fallback(self.asInstanceOf[PathCodec[Unit]], that)

    def alternatives: List[PathCodec[A]] =
      expand(self).asInstanceOf[List[PathCodec[A]]]

    def decode(path: Path): Either[String, A] =
      decodeCodec(self, path.segments, 0).collectFirst {
        case (value, end) if end == path.segments.length => value.asInstanceOf[A]
      }
        .toRight(s"Path ${path.encode} did not match ${PathCodec.render(self)}")

    def format(value: A): Either[String, Path] =
      formatCodec(self, value.asInstanceOf[Any]).map(_.addLeadingSlash)

    def matches(path: Path): Boolean =
      decode(path).isRight

    def render: String = PathCodec.render(codec = self)
  }

  def apply(value: String): PathCodec[Unit] = {
    val path = Path(value)
    if (path.segments.isEmpty) empty
    else path.segments.foldLeft(empty: PathCodec[Unit])((acc, segment) => acc / literal(segment))
  }

  def apply[A](segment: SegmentCodec[A]): PathCodec[A] =
    Segment(segment)

  given Conversion[String, PathCodec[Unit]]            = apply(_)
  given [A]: Conversion[SegmentCodec[A], PathCodec[A]] = Segment(_)

  final case class Segment[A](segment: SegmentCodec[A]) extends PathCodec[A]
  final case class Concat[A, B, C](
    left: PathCodec[A],
    right: PathCodec[B],
    combiner: Tuples.Tuples.WithOut[A, B, C]
  ) extends PathCodec[C]
  final case class Fallback(left: PathCodec[Unit], right: PathCodec[Unit]) extends PathCodec[Unit]

  val empty: PathCodec[Unit] = Segment(SegmentCodec.Empty)

  def literal(value: String): PathCodec[Unit]       = Segment(SegmentCodec.literal(value))
  def bool(name: String): PathCodec[Boolean]        = Segment(SegmentCodec.bool(name))
  def int(name: String): PathCodec[Int]             = Segment(SegmentCodec.int(name))
  def long(name: String): PathCodec[Long]           = Segment(SegmentCodec.long(name))
  def string(name: String): PathCodec[String]       = Segment(SegmentCodec.string(name))
  def uuid(name: String): PathCodec[java.util.UUID] = Segment(SegmentCodec.uuid(name))
  val trailing: PathCodec[Path]                     = Segment(SegmentCodec.Trailing)

  def render(codec: PathCodec[_], prefix: String = "{", suffix: String = "}"): String = {
    def loop(current: PathCodec[_]): String =
      current match {
        case Segment(segment)       => segment.render(prefix, suffix)
        case Concat(left, right, _) => loop(left) + loop(right)
        case Fallback(left, right)  => loop(left) + " | " + loop(right)
      }
    val rendered = loop(codec)
    if (rendered.isEmpty) "/" else rendered
  }

  private def expand(codec: PathCodec[_]): List[PathCodec[_]] =
    codec match {
      case fallback: Fallback =>
        def loop(codecs: List[PathCodec[_]], result: List[PathCodec[_]]): List[PathCodec[_]] =
          codecs match {
            case Nil          => result
            case head :: tail =>
              head match {
                case Segment(SegmentCodec.Empty)            => loop(tail, result)
                case Fallback(left, right)                  => loop(left :: right :: tail, result)
                case Segment(SegmentCodec.Literal(_, _, _)) => loop(tail, result :+ head)
                case other                                  =>
                  throw new IllegalStateException(
                    s"Alternative path segments should only contain literals, found: $other"
                  )
              }
          }
        loop(List(fallback.left, fallback.right), Nil)
      case Concat(left, right, combiner) =>
        for {
          l <- expand(left)
          r <- expand(right)
        } yield Concat(
          l.asInstanceOf[PathCodec[Any]],
          r.asInstanceOf[PathCodec[Any]],
          combiner.asInstanceOf[AnyCombiner]
        )
      case Segment(SegmentCodec.Empty) => Nil
      case other                       => List(other)
    }

  private def decodeCodec(codec: PathCodec[_], segments: Chunk[String], index: Int): List[(Any, Int)] =
    codec match {
      case Segment(segment)              => decodeSegment(segment, segments, index)
      case Concat(left, right, combiner) =>
        decodeCodec(left, segments, index).flatMap { case (leftValue, next) =>
          decodeCodec(right, segments, next).map { case (rightValue, end) =>
            val typed = combiner.asInstanceOf[AnyCombiner]
            (typed.combine(leftValue, rightValue), end)
          }
        }
      case Fallback(left, right) => decodeCodec(left, segments, index) ++ decodeCodec(right, segments, index)
    }

  private def decodeSegment(codec: SegmentCodec[_], segments: Chunk[String], index: Int): List[(Any, Int)] =
    codec match {
      case SegmentCodec.Empty                => List(((), index))
      case SegmentCodec.Literal(value, _, _) =>
        if (index < segments.length && segments(index) == value) List(((), index + 1)) else Nil
      case SegmentCodec.BoolSeg(_, _, _) =>
        if (index >= segments.length) Nil
        else
          segments(index) match {
            case "true"  => List((true, index + 1))
            case "false" => List((false, index + 1))
            case _       => Nil
          }
      case SegmentCodec.IntSeg(_, _, _) =>
        segments.lift(index).flatMap(_.toIntOption).map(v => List((v, index + 1))).getOrElse(Nil)
      case SegmentCodec.LongSeg(_, _, _) =>
        segments.lift(index).flatMap(_.toLongOption).map(v => List((v, index + 1))).getOrElse(Nil)
      case SegmentCodec.StringSeg(_, _, _) => segments.lift(index).map(v => List((v, index + 1))).getOrElse(Nil)
      case SegmentCodec.UUIDSeg(_, _, _)   =>
        segments
          .lift(index)
          .flatMap { segment =>
            try Some(java.util.UUID.fromString(segment))
            catch { case _: IllegalArgumentException => None }
          }
          .map(v => List((v, index + 1)))
          .getOrElse(Nil)
      case SegmentCodec.Trailing =>
        val path =
          if (index >= segments.length) Path.root
          else Path(segments.drop(index), hasLeadingSlash = true, trailingSlash = false)
        List((path, segments.length))
      case combined: SegmentCodec.Combined[?, ?, ?] =>
        if (index >= segments.length) Nil
        else {
          val segment = segments(index)
          SegmentCodec.decodeCombined(combined, segment, 0).collect {
            case (value, end) if end == segment.length => (value, index + 1)
          }
        }
    }

  private def formatCodec(codec: PathCodec[_], value: Any): Either[String, Path] =
    codec match {
      case Segment(segment)              => Right(segment.asInstanceOf[SegmentCodec[Any]].format(value))
      case Concat(left, right, combiner) =>
        val typed                   = combiner.asInstanceOf[AnyCombiner]
        val (leftValue, rightValue) = typed.separate(value)
        for {
          leftPath  <- formatCodec(left, leftValue)
          rightPath <- formatCodec(right, rightValue)
        } yield leftPath ++ rightPath.dropLeadingSlash
      case Fallback(left, _) => formatCodec(left, value)
    }
}
