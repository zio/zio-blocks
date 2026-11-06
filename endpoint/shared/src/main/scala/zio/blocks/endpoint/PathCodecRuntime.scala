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

/**
 * Interpreter behind `PathCodec#render`, `alternatives`, `decode`, `matches`
 * and `format`.
 */
private[endpoint] object PathCodecRuntime {

  private type DecodeError = String
  private type AnyCombiner = Tuples.Tuples.WithOut[Any, Any, Any]

  /**
   * Renders `codec` as a route string, writing each capture name between
   * `prefix` and `suffix` and a `Fallback` as `left | right`; an empty result
   * is `/`.
   */
  def render(codec: PathCodec[_], prefix: String, suffix: String): String = {
    def loop(current: PathCodec[_]): String =
      current match {
        case PathCodec.Segment(segment)       => segment.render(prefix, suffix)
        case PathCodec.Concat(left, right, _) => loop(left) + loop(right)
        case PathCodec.Transform(inner, _, _) => loop(inner)
        case PathCodec.Ignored(inner)         => loop(inner)
        case PathCodec.Fallback(left, right)  => loop(left) + " | " + loop(right)
      }

    val rendered = loop(codec)
    if (rendered.isEmpty) "/" else rendered
  }

  /**
   * Expands every `Fallback` into its alternatives, one codec per combination
   * across a `Concat` (a cross product). An empty alternative is dropped, and a
   * `Fallback` branch that is not a literal throws `IllegalStateException`.
   */
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
                case PathCodec.Ignored(inner)                         => loop(expand(inner) ::: tail, result)
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
      case PathCodec.Ignored(inner) =>
        expand(inner).map(wrapped => PathCodec.Ignored(wrapped.asInstanceOf[PathCodec[Any]]))
      case PathCodec.Segment(SegmentCodec.Empty) => Nil
      case other                                 => List(other)
    }

  /**
   * Returns every way `codec` can decode `segments` from `index`, as pairs of
   * the decoded value and the index after the last segment used; empty when it
   * cannot. A `Transform` whose `decode` returns `Left` gives no candidate, and
   * a `Fallback` lists the left branch's candidates first.
   */
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
      case PathCodec.Ignored(inner) =>
        decodeCodec(inner, segments, index).map { case (_, end) => ((), end) }
      case PathCodec.Fallback(left, right) => decodeCodec(left, segments, index) ++ decodeCodec(right, segments, index)
    }

  /**
   * The candidates for one segment codec; `Trailing` uses all remaining
   * segments.
   */
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
        if (index >= segments.length) Nil
        else {
          val segment = segments(index)
          SegmentCodec.parseIntWindow(segment, 0, segment.length).map(v => List((v, index + 1))).getOrElse(Nil)
        }
      case SegmentCodec.LongSeg(_, _, _) =>
        if (index >= segments.length) Nil
        else {
          val segment = segments(index)
          SegmentCodec.parseLongWindow(segment, 0, segment.length).map(v => List((v, index + 1))).getOrElse(Nil)
        }
      case SegmentCodec.StringSeg(_, _, _) => segments.lift(index).map(v => List((v, index + 1))).getOrElse(Nil)
      case SegmentCodec.UUIDSeg(_, _, _)   =>
        segments
          .lift(index)
          .flatMap { s =>
            // Whole-segment fast path: the layout gate is index-based (no
            // substring), and `java.util.UUID` construction takes the segment
            // itself, so no window copy is made here.
            if (!SegmentCodec.isUuidWindow(s, 0, s.length)) None
            else
              try Some(java.util.UUID.fromString(s))
              catch { case _: IllegalArgumentException => None }
          }
          .map(v => List((v, index + 1)))
          .getOrElse(Nil)
      case SegmentCodec.Trailing =>
        val path =
          if (index >= segments.length) Path.root
          else Path(segments.drop(index), hasLeadingSlash = true, trailingSlash = false)
        List((path, segments.length))
      case combined: SegmentCodec.Combined[_, _, _] =>
        if (index >= segments.length) Nil
        else {
          val segment = segments(index)
          SegmentCodec.decodeComplete(combined, segment).collect {
            case (value, end) if end == segment.length => (value, index + 1)
          }
        }
    }

  /**
   * Tells whether `codec` matches all of `segments`, the fast path behind
   * `PathCodec#matches`. It follows [[decodeCodec]] but tracks only the end
   * index (`-1` for no match) instead of building `(value, end)` candidate
   * lists. A `Fallback` takes the longest branch end, and a path-level
   * `Transform` needs its decoded value, so it calls [[decodeCodec]].
   */
  def matchesCodec(codec: PathCodec[_], segments: Chunk[String]): Boolean =
    matchEnd(codec, segments, 0) == segments.length

  /** The end index of the best match of `codec` from `index`, or `-1`. */
  private def matchEnd(codec: PathCodec[_], segments: Chunk[String], index: Int): Int =
    codec match {
      case PathCodec.Segment(segment)       => matchSegmentEnd(segment, segments, index)
      case PathCodec.Concat(left, right, _) =>
        val mid = matchEnd(left, segments, index)
        if (mid < 0) -1 else matchEnd(right, segments, mid)
      case PathCodec.Ignored(inner)        => matchEnd(inner, segments, index)
      case PathCodec.Fallback(left, right) =>
        math.max(matchEnd(left, segments, index), matchEnd(right, segments, index))
      case transformed: PathCodec.Transform[_, _] =>
        decodeCodec(transformed, segments, index).foldLeft(-1) { case (best, (_, end)) => math.max(best, end) }
    }

  /** The end index of a one-segment match from `index`, or `-1`. */
  private def matchSegmentEnd(codec: SegmentCodec[_], segments: Chunk[String], index: Int): Int =
    codec match {
      case SegmentCodec.Empty    => index
      case SegmentCodec.Trailing => segments.length
      case _                     =>
        if (index >= segments.length) -1
        else if (SegmentCodec.matchesComplete(codec, segments(index))) index + 1
        else -1
    }

  /**
   * Returns how many segments of `segments` the best partial match of `codec`
   * reaches. `PathCodec#decode` calls it after a failed decode, to name the
   * failing segment in the error message.
   */
  def furthestIndex(codec: PathCodec[_], segments: Chunk[String], index: Int): Int =
    codec match {
      case PathCodec.Segment(segment)       => furthestSegment(segment, segments, index)
      case PathCodec.Concat(left, right, _) =>
        val leftEnds = decodeCodec(left, segments, index).map(_._2)
        if (leftEnds.isEmpty) furthestIndex(left, segments, index)
        else leftEnds.map(end => furthestIndex(right, segments, end)).max
      case PathCodec.Ignored(inner) =>
        furthestIndex(inner, segments, index)
      case transformed: PathCodec.Transform[_, _] =>
        furthestIndex(transformed.codec, segments, index)
      case PathCodec.Fallback(left, right) =>
        math.max(furthestIndex(left, segments, index), furthestIndex(right, segments, index))
    }

  /** The furthest index one segment codec reaches from `index`. */
  private def furthestSegment(codec: SegmentCodec[_], segments: Chunk[String], index: Int): Int =
    codec match {
      case SegmentCodec.Trailing => segments.length
      case _                     =>
        if (index >= segments.length) index
        else if (SegmentCodec.matchesComplete(codec, segments(index))) index + 1
        else index
    }

  /**
   * Formats `value` as a path through `codec`. `Left` for an `Ignored` codec,
   * which captures no value, and when a `Transform` encoder returns `Left`; a
   * `Fallback` formats with its left branch.
   */
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
      case PathCodec.Ignored(inner) =>
        Left(s"Cannot format an unused path segment: ${render(inner, "{", "}")} captures no value")
      case PathCodec.Fallback(left, _) => formatCodec(left, value)
    }
}
