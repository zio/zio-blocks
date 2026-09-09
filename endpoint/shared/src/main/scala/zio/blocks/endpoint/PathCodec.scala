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

import zio.blocks.combinators.Tuples
import zio.http.Path

/**
 * Composable path descriptor. Segments are combined with `/` or `++`, literal
 * alternatives with `orElse`. Use `decode` / `format` for bidirectional path
 * conversion, and `alternatives` to expand `orElse` branches for routing-trie
 * insertion.
 */
sealed trait PathCodec[A] { self =>

  /**
   * Concatenates two path codecs, combining decoded values with
   * `Tuples.Tuples.WithOut` (so `Int / String` decodes to `(Int, String)`).
   */
  final def ++[B, C](that: PathCodec[B])(implicit combiner: Tuples.Tuples.WithOut[A, B, C]): PathCodec[C] =
    PathCodec.combineUnrefined(self, that)(combiner)

  /**
   * Symbolic alias for [[++]] —
   * `PathCodec.int("id") / PathCodec.string("slug")`.
   */
  final def /[B, C](that: PathCodec[B])(implicit combiner: Tuples.Tuples.WithOut[A, B, C]): PathCodec[C] =
    self ++ that

  /**
   * Literal-only alternative (`users` or `posts`). Both branches must be
   * literal-only (validated at runtime by `expand`); `ev` keeps capturing
   * codecs out of alternatives at the type level.
   */
  final def orElse(that: PathCodec[Unit])(implicit ev: A =:= Unit): PathCodec[Unit] = {
    val _ = ev
    PathCodec.Fallback(self.asInstanceOf[PathCodec[Unit]], that)
  }

  final def alternatives: List[PathCodec[A]] =
    PathCodecRuntime.expand(self).asInstanceOf[List[PathCodec[A]]].distinct

  /**
   * Decodes `path`, naming the furthest segment reached on failure (for example
   * `failed at segment 1 ('abc')`). The position is message-level context only
   * — errors stay `Either[String, A]`; a full `Either[SchemaError, A]`
   * migration composing positions à la `SchemaError.atField`/`atIndex` remains
   * future work.
   */
  final def decode(path: Path): Either[String, A] =
    PathCodecRuntime
      .decodeCodec(self, path.segments, 0)
      .collectFirst {
        case (value, end) if end == path.segments.length => value.asInstanceOf[A]
      }
      .toRight {
        val segments = path.segments
        val furthest = PathCodecRuntime.furthestIndex(self, segments, 0)
        val where    =
          if (furthest < segments.length) s"failed at segment $furthest ('${segments(furthest)}')"
          else s"matched $furthest of ${segments.length} segments without a complete match"
        s"Path ${path.encode} did not match ${PathCodec.render(self)}: $where"
      }

  final def format(value: A): Either[String, Path] =
    PathCodecRuntime.formatCodec(self, value.asInstanceOf[Any]).map(_.addLeadingSlash)

  /**
   * Boolean fast path: equivalent to `decode(path).isRight` without building
   * any decoded values or candidate lists (see
   * `PathCodecRuntime.matchesCodec`).
   */
  final def matches(path: Path): Boolean =
    PathCodecRuntime.matchesCodec(self, path.segments)

  final def render: String = PathCodec.render(codec = self)

  /**
   * Maps the decoded path value without changing the underlying path structure.
   *
   * Example: {{ val customerPath =
   * PathCodec.int("id").transform[CustomerId](CustomerId(_), _.value) }}
   *
   * @param decode
   *   maps the decoded path value into the exposed type
   * @param encode
   *   maps the exposed type back into the original path representation used by
   *   `format`
   * @return
   *   a path codec with the transformed value type and the same route shape as
   *   `self`
   */
  final def transform[B](decode: A => B, encode: B => A): PathCodec[B] =
    transformOrFail[B](value => Right(decode(value)), value => Right(encode(value)))

  /**
   * Effectfully maps the decoded path value without changing the underlying
   * path structure.
   *
   * `decode` returning `Left` causes path decoding / matching to fail. `format`
   * remains effectful, so `encode` returning `Left` is propagated as the `Left`
   * result of `format`.
   *
   * Example: {{ val customerPath = PathCodec .string("id")
   * .transformOrFail[CustomerId](CustomerId.parse, value => Right(value.value))
   * }}
   *
   * @param decode
   *   validates and maps the decoded path value into the exposed type
   * @param encode
   *   validates and maps the exposed type back into the original path
   *   representation used by `format`
   * @return
   *   a path codec with the transformed value type and the same route shape as
   *   `self`
   */
  final def transformOrFail[B](
    decode: A => Either[String, B],
    encode: B => Either[String, A]
  ): PathCodec[B] =
    PathCodec.Transform(self, decode, encode)
}

object PathCodec {

  private type DecodeError = String

  implicit val unitUnit: Tuples.Tuples.WithOut[Unit, Unit, Unit] = Tuples.Tuples.leftUnit[Unit]

  private[endpoint] def combineUnrefined[A, B, C](left: PathCodec[A], right: PathCodec[B])(implicit
    combiner: Tuples.Tuples.WithOut[A, B, C]
  ): PathCodec[C] =
    if (left == empty) right.asInstanceOf[PathCodec[C]]
    else if (right == empty) left.asInstanceOf[PathCodec[C]]
    else Concat(left, right, combiner)

  /**
   * Builds a literal-only path from a raw string (for example
   * `PathCodec("users/42")`).
   *
   * Encoding trio, documented once here: `Path.apply` does NOT percent-decode
   * (a raw `%20` stays a literal `%20` segment), so neither does this
   * constructor — `Path.fromEncoded` is the decoding counterpart (both live in
   * `http-model`). Encoded literals cannot be expressed at all: a literal
   * carrying `/` or characters requiring URL encoding is rejected by
   * `SegmentCodec.validateLiteralValue`. `RoutePattern.apply(method,
   * pathString)` funnels through this same constructor.
   */
  def apply(value: String): PathCodec[Unit] = {
    val path                   = Path(value)
    val built: PathCodec[Unit] =
      if (path.segments.isEmpty) empty
      else
        path.segments.foldLeft(empty: PathCodec[Unit])((acc, segment) =>
          combineUnrefined(acc, Segment(SegmentCodec.literalValidated(segment)))(unitUnit)
        )
    built
  }

  def apply[A](segment: SegmentCodec[A]): PathCodec[A] =
    Segment(segment)

  implicit def stringToPathCodec(value: String): PathCodec[Unit] =
    apply(value)

  implicit def segmentToPathCodec[A](value: SegmentCodec[A]): PathCodec[A] =
    Segment(value)

  final case class Segment[A](segment: SegmentCodec[A]) extends PathCodec[A]
  final case class Concat[A, B, C](
    left: PathCodec[A],
    right: PathCodec[B],
    combiner: Tuples.Tuples.WithOut[A, B, C]
  ) extends PathCodec[C]
  final case class Transform[A, B](
    codec: PathCodec[A],
    decode: A => Either[DecodeError, B],
    encode: B => Either[DecodeError, A]
  ) extends PathCodec[B]
  final case class Fallback(left: PathCodec[Unit], right: PathCodec[Unit]) extends PathCodec[Unit]

  val empty: PathCodec[Unit] =
    apply(SegmentCodec.Empty)

  def literal(value: String): PathCodec[Unit] = {
    SegmentCodec.validateLiteralValue(value)
    apply(SegmentCodec.literalValidated(value))
  }

  // `N <: String with Singleton` preserves the literal singleton type of a literal `name` argument
  // (instead of widening it to plain `String`) on both Scala 2.13 and Scala 3.
  def bool[N <: String with Singleton](name: N): PathCodec[Boolean] =
    apply(SegmentCodec.bool(name))
  def int[N <: String with Singleton](name: N): PathCodec[Int] =
    apply(SegmentCodec.int(name))
  def long[N <: String with Singleton](name: N): PathCodec[Long] =
    apply(SegmentCodec.long(name))
  def string[N <: String with Singleton](name: N): PathCodec[String] =
    apply(SegmentCodec.string(name))
  def uuid[N <: String with Singleton](name: N): PathCodec[java.util.UUID] =
    apply(SegmentCodec.uuid(name))

  val trailing: PathCodec[Path] =
    apply(SegmentCodec.Trailing)

  def render(codec: PathCodec[_], prefix: String = "{", suffix: String = "}"): String =
    PathCodecRuntime.render(codec, prefix, suffix)
}
