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
   * Phantom capture marker: [[SegmentCodec.NoPathVars]] when this path captures
   * nothing (empty paths, literal-only paths), [[SegmentCodec.HasPathVars]]
   * when any segment captures a value. Never affects `A` (the runtime-decoded
   * value type), zero runtime footprint. Threaded through `++`, `/`,
   * `SegmentCodec.~`, and `transform` so [[orElse]] can reject capturing
   * branches at the call site.
   */
  type PathVars

  /**
   * Concatenates two path codecs, combining decoded values with
   * `Tuples.Tuples.WithOut` (so `Int / String` decodes to `(Int, String)`). The
   * result's `PathVars` is the logical OR of both sides (unknown markers
   * default to capturing - see [[SegmentCodec.CombinePathVars]]).
   */
  final def ++[B, C, PV2, PVC](that: PathCodec[B] { type PathVars = PV2 })(implicit
    combiner: Tuples.Tuples.WithOut[A, B, C],
    pathVarsCombiner: SegmentCodec.CombinePathVars[self.PathVars, PV2, PVC]
  ): PathCodec[C] { type PathVars = PVC } =
    PathCodec.combineUnrefined(self, that)(combiner).asInstanceOf[PathCodec[C] { type PathVars = PVC }]

  /**
   * Symbolic alias for [[++]] —
   * `PathCodec.int("id") / PathCodec.string("slug")`.
   */
  final def /[B, C, PV2, PVC](that: PathCodec[B] { type PathVars = PV2 })(implicit
    combiner: Tuples.Tuples.WithOut[A, B, C],
    pathVarsCombiner: SegmentCodec.CombinePathVars[self.PathVars, PV2, PVC]
  ): PathCodec[C] { type PathVars = PVC } =
    self.++(that)(combiner, pathVarsCombiner)

  /**
   * Literal-only alternative (`users` or `posts`). Both branches must be
   * literal-only: `that` is required to carry `NoPathVars`, and the `ev`
   * evidence requires `(A, PathVars)` to equal `(Unit, NoPathVars)` — so a
   * capturing codec mapped to `PathCodec[Unit]` (for example
   * `PathCodec.int("id").transform(_ => (), _ => 0)`, whose marker stays
   * `HasPathVars` through `transform`) is rejected at the call site rather than
   * failing `expand` at runtime. Non-literal `Fallback` branches that slip past
   * the types are still validated at runtime by `expand`.
   */
  final def orElse(that: PathCodec[Unit] { type PathVars = SegmentCodec.NoPathVars })(implicit
    ev: (A, self.PathVars) =:= (Unit, SegmentCodec.NoPathVars)
  ): PathCodec[Unit] { type PathVars = SegmentCodec.NoPathVars } = {
    val _ = ev
    PathCodec
      .Fallback(self.asInstanceOf[PathCodec[Unit]], that)
      .asInstanceOf[PathCodec[Unit] { type PathVars = SegmentCodec.NoPathVars }]
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
   * Example: {{ val customerPath = PathCodec.int("id").transform(CustomerId(_),
   * _.value) }}
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
  final def transform[B, PV](decode: A => B, encode: B => A)(implicit
    ev: SegmentCodec.CombinePathVars[self.PathVars, SegmentCodec.NoPathVars, PV]
  ): PathCodec[B] { type PathVars = PV } = {
    // `ev` reifies `self`'s marker to a concrete type (identity OR with
    // `NoPathVars`): precise when `self` is refined, `HasPathVars` when it is
    // unknown. The result never carries an abstract marker projection, which
    // keeps Scala 2.13 codegen stable (no `stabilizer` backend crash when the
    // result meets a plain type annotation).
    val _ = ev
    PathCodec
      .Transform(self, (value: A) => Right(decode(value)), (value: B) => Right(encode(value)))
      .asInstanceOf[PathCodec[B] { type PathVars = PV }]
  }

  /**
   * Effectfully maps the decoded path value without changing the underlying
   * path structure.
   *
   * `decode` returning `Left` causes path decoding / matching to fail. `format`
   * remains effectful, so `encode` returning `Left` is propagated as the `Left`
   * result of `format`.
   *
   * Example: {{ val customerPath = PathCodec .string("id")
   * .transformOrFail(CustomerId.parse, value => Right(value.value)) }}
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
  final def transformOrFail[B, PV](
    decode: A => Either[String, B],
    encode: B => Either[String, A]
  )(implicit ev: SegmentCodec.CombinePathVars[self.PathVars, SegmentCodec.NoPathVars, PV]): PathCodec[B] {
    type PathVars = PV
  } = {
    // Marker reification: see `transform` (identity OR with `NoPathVars`).
    val _ = ev
    PathCodec.Transform(self, decode, encode).asInstanceOf[PathCodec[B] { type PathVars = PV }]
  }

  /**
   * Marks this path's captured value as intentionally unused: the route keeps
   * matching the same shape and keeps rendering the same `{name}` placeholders,
   * but decoding yields `Unit` instead of the captured value, so no handler
   * parameter is required for it.
   *
   * Example: `PathCodec.int("id").unused` still matches `/42` and still renders
   * `/{id}`, yet the endpoint's path input is `Unit`.
   *
   * Like [[transform]], this reifies the marker (identity OR with
   * `NoPathVars`), so an unused literal-only path stays `NoPathVars` while an
   * unused capturing path stays `HasPathVars`. Formatting an unused path fails:
   * there is no value to encode back into the ignored segment.
   *
   * @return
   *   a path codec with value type `Unit` and the same route shape as `self`
   */
  final def unused[PV](implicit
    ev: SegmentCodec.CombinePathVars[self.PathVars, SegmentCodec.NoPathVars, PV]
  ): PathCodec[Unit] { type PathVars = PV } = {
    // Marker reification: see `transform` (identity OR with `NoPathVars`).
    val _ = ev
    PathCodec.Ignored(self).asInstanceOf[PathCodec[Unit] { type PathVars = PV }]
  }
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
  def apply(value: String): PathCodec[Unit] { type PathVars = SegmentCodec.NoPathVars } = {
    val path                   = Path(value)
    val built: PathCodec[Unit] =
      if (path.segments.isEmpty) empty
      else
        path.segments.foldLeft(empty: PathCodec[Unit])((acc, segment) =>
          combineUnrefined(acc, Segment(SegmentCodec.literalValidated(segment)))(unitUnit)
        )
    built.asInstanceOf[PathCodec[Unit] { type PathVars = SegmentCodec.NoPathVars }]
  }

  def apply[A, PV](segment: SegmentCodec[A] { type PathVars = PV }): PathCodec[A] { type PathVars = PV } =
    Segment(segment).asInstanceOf[PathCodec[A] { type PathVars = PV }]

  implicit def stringToPathCodec(value: String): PathCodec[Unit] { type PathVars = SegmentCodec.NoPathVars } =
    apply(value)

  implicit def segmentToPathCodec[A, PV](
    value: SegmentCodec[A] { type PathVars = PV }
  ): PathCodec[A] { type PathVars = PV } =
    Segment(value).asInstanceOf[PathCodec[A] { type PathVars = PV }]

  final case class Segment[A](segment: SegmentCodec[A]) extends PathCodec[A] {
    // Left abstract: only `apply(segment)` (which binds the segment's own
    // marker) may ascribe a concrete one. See `Combined` in `SegmentCodec`.
    type PathVars
  }
  final case class Concat[A, B, C](
    left: PathCodec[A],
    right: PathCodec[B],
    combiner: Tuples.Tuples.WithOut[A, B, C]
  ) extends PathCodec[C] {
    // Left abstract: only `++`/`/` (which prove the OR of both sides via
    // `CombinePathVars`) may ascribe a concrete marker.
    type PathVars
  }
  final case class Transform[A, B](
    codec: PathCodec[A],
    decode: A => Either[DecodeError, B],
    encode: B => Either[DecodeError, A]
  ) extends PathCodec[B] {
    // Left abstract: only `transform`/`transformOrFail` (which reify the
    // inner codec's own marker) may ascribe a concrete one.
    type PathVars
  }
  final case class Ignored[A](
    codec: PathCodec[A]
  ) extends PathCodec[Unit] {
    // Left abstract: only `unused` (which reifies the inner codec's own
    // marker) may ascribe a concrete one.
    type PathVars
  }
  final case class Fallback(left: PathCodec[Unit], right: PathCodec[Unit]) extends PathCodec[Unit] {
    // Left abstract: only `orElse` (which proves both branches literal-only)
    // may ascribe `NoPathVars`.
    type PathVars
  }

  val empty: PathCodec[Unit] { type PathVars = SegmentCodec.NoPathVars } =
    apply(SegmentCodec.Empty)

  def literal(value: String): PathCodec[Unit] { type PathVars = SegmentCodec.NoPathVars } = {
    SegmentCodec.validateLiteralValue(value)
    apply(SegmentCodec.literalValidated(value))
  }

  // `N <: String with Singleton` preserves the literal singleton type of a literal `name` argument
  // (instead of widening it to plain `String`) on both Scala 2.13 and Scala 3.
  def bool[N <: String with Singleton](name: N): PathCodec[Boolean] { type PathVars = SegmentCodec.HasPathVars } =
    apply(SegmentCodec.bool(name))
  def int[N <: String with Singleton](name: N): PathCodec[Int] { type PathVars = SegmentCodec.HasPathVars } =
    apply(SegmentCodec.int(name))
  def long[N <: String with Singleton](name: N): PathCodec[Long] { type PathVars = SegmentCodec.HasPathVars } =
    apply(SegmentCodec.long(name))
  def string[N <: String with Singleton](name: N): PathCodec[String] { type PathVars = SegmentCodec.HasPathVars } =
    apply(SegmentCodec.string(name))
  def uuid[N <: String with Singleton](
    name: N
  ): PathCodec[java.util.UUID] { type PathVars = SegmentCodec.HasPathVars } =
    apply(SegmentCodec.uuid(name))

  val trailing: PathCodec[Path] { type PathVars = SegmentCodec.HasPathVars } =
    apply(SegmentCodec.Trailing)

  def render(codec: PathCodec[_], prefix: String = "{", suffix: String = "}"): String =
    PathCodecRuntime.render(codec, prefix, suffix)
}
