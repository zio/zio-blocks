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

import zio.blocks.chunk.Chunk
import zio.blocks.combinators.Tuples
import zio.http.Path

private[endpoint] object PathCodecRuntime {

  private type DecodeError = String
  private type AnyCombiner = Tuples.Tuples.WithOut[Any, Any, Any]

  def render(codec: PathCodec[_], prefix: String, suffix: String): String = {
    def loop(current: PathCodec[_]): String =
      current match {
        case PathCodec.Segment(segment)       => segment.render(prefix, suffix)
        case PathCodec.Concat(left, right, _) => loop(left) + loop(right)
        case PathCodec.Transform(inner, _, _) => loop(inner)
        case PathCodec.Fallback(left, right)  => loop(left) + " | " + loop(right)
      }

    val rendered = loop(codec)
    if (rendered.isEmpty) "/" else rendered
  }

  def expand(codec: PathCodec[_]): List[PathCodec[_]] =
    codec match {
      case fallback: PathCodec.Fallback =>
        def loop(codecs: List[PathCodec[_]], result: List[PathCodec[_]]): List[PathCodec[_]] =
          codecs match {
            case Nil          => result
            case head :: tail =>
              head match {
                case PathCodec.Segment(SegmentCodec.Empty)            => loop(tail, result)
                case PathCodec.Fallback(left, right)                  => loop(left :: right :: tail, result)
                case PathCodec.Segment(SegmentCodec.Literal(_, _, _)) => loop(tail, result :+ head)
                case other                                            =>
                  throw new IllegalStateException(
                    s"Alternative path segments should only contain literals, found: $other"
                  )
              }
          }

        loop(List(fallback.left, fallback.right), Nil)
      case PathCodec.Concat(left, right, combiner) =>
        for {
          l <- expand(left)
          r <- expand(right)
        } yield PathCodec.Concat(
          l.asInstanceOf[PathCodec[Any]],
          r.asInstanceOf[PathCodec[Any]],
          combiner.asInstanceOf[AnyCombiner]
        )
      case PathCodec.Transform(codec, decode, encode) =>
        expand(codec).map(inner =>
          PathCodec.Transform(
            inner.asInstanceOf[PathCodec[Any]],
            decode.asInstanceOf[Any => Either[DecodeError, Any]],
            encode.asInstanceOf[Any => Either[DecodeError, Any]]
          )
        )
      case PathCodec.Segment(SegmentCodec.Empty) => Nil
      case other                                 => List(other)
    }

  def decodeCodec(codec: PathCodec[_], segments: Chunk[String], index: Int): List[(Any, Int)] =
    codec match {
      case PathCodec.Segment(segment)              => decodeSegment(segment, segments, index)
      case PathCodec.Concat(left, right, combiner) =>
        decodeCodec(left, segments, index).flatMap { case (leftValue, next) =>
          decodeCodec(right, segments, next).map { case (rightValue, end) =>
            val typed = combiner.asInstanceOf[AnyCombiner]
            (typed.combine(leftValue, rightValue), end)
          }
        }
      case transformed: PathCodec.Transform[_, _] =>
        val codec  = transformed.codec.asInstanceOf[PathCodec[Any]]
        val decode = transformed.decode.asInstanceOf[Any => Either[DecodeError, Any]]
        decodeCodec(codec, segments, index).flatMap { case (value, end) =>
          decode(value).toOption.map(_ -> end)
        }
      case PathCodec.Fallback(left, right) => decodeCodec(left, segments, index) ++ decodeCodec(right, segments, index)
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
      case transformed: SegmentCodec.Transform[_, _] =>
        if (index >= segments.length) Nil
        else {
          val segment = segments(index)
          SegmentCodec.decodeCombined(transformed, segment, 0).collect {
            case (value, end) if end == segment.length => (value, index + 1)
          }
        }
      case combined: SegmentCodec.Combined[_, _, _] =>
        if (index >= segments.length) Nil
        else {
          val segment = segments(index)
          SegmentCodec.decodeCombined(combined, segment, 0).collect {
            case (value, end) if end == segment.length => (value, index + 1)
          }
        }
    }

  def formatCodec(codec: PathCodec[_], value: Any): Either[String, Path] =
    codec match {
      case PathCodec.Segment(segment)              => Right(segment.asInstanceOf[SegmentCodec[Any]].format(value))
      case PathCodec.Concat(left, right, combiner) =>
        val typed                   = combiner.asInstanceOf[AnyCombiner]
        val (leftValue, rightValue) = typed.separate(value)
        for {
          leftPath  <- formatCodec(left, leftValue)
          rightPath <- formatCodec(right, rightValue)
        } yield leftPath ++ rightPath.dropLeadingSlash
      case transformed: PathCodec.Transform[_, _] =>
        val codec  = transformed.codec.asInstanceOf[PathCodec[Any]]
        val encode = transformed.encode.asInstanceOf[Any => Either[DecodeError, Any]]
        encode(value).flatMap(inner => formatCodec(codec, inner))
      case PathCodec.Fallback(left, _) => formatCodec(left, value)
    }
}
