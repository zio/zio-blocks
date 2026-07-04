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
sealed trait PathCodec[A] {

  /**
   * Ordered, purely phantom registry of [[PathVar]] markers contributed by this
   * path (the concatenation, in order, of every captured segment's own
   * `SegmentCodec.PathVars`). This is a second, parallel type track: it never
   * affects `A` (the existing runtime-extracted value type) and has zero
   * runtime footprint. Left unbounded (mirrors `SegmentCodec.PathVars` -
   * `scala.Tuple` does not exist as a cross-version supertype on Scala 2.13).
   */
  type PathVars
}

object PathCodec {

  private type DecodeError = String

  /**
   * Type alias capturing a `PathCodec[A]`'s precise `PathVars` member,
   * mirroring `SegmentCodec.WithBoundaries`.
   */
  type WithPathVars[A, PV] = PathCodec[A] { type PathVars = PV }

  implicit val unitUnit: Tuples.Tuples.WithOut[Unit, Unit, Unit] = Tuples.Tuples.leftUnit[Unit]

  implicit final class PathCodecOps[A, PV](private val self: PathCodec[A] { type PathVars = PV }) extends AnyVal {
    def ++[B, PV2, C, PVC](that: PathCodec[B] { type PathVars = PV2 })(implicit
      combiner: Tuples.Tuples.WithOut[A, B, C],
      _pathVarsCombiner: PathVarTuples.Combine.WithOut[PV, PV2, PVC]
    ): PathCodec[C] { type PathVars = PVC } = {
      val _ = _pathVarsCombiner
      combineUnrefined(self, that)(combiner).asInstanceOf[PathCodec[C] { type PathVars = PVC }]
    }

    def /[B, PV2, C, PVC](that: PathCodec[B] { type PathVars = PV2 })(implicit
      combiner: Tuples.Tuples.WithOut[A, B, C],
      _pathVarsCombiner: PathVarTuples.Combine.WithOut[PV, PV2, PVC]
    ): PathCodec[C] { type PathVars = PVC } = {
      val _ = _pathVarsCombiner
      self ++ that
    }

    def orElse(that: PathCodec[Unit])(implicit ev: A =:= Unit): PathCodec[Unit] {
      type PathVars = SegmentCodec.NoPathVars
    } =
      Fallback(self.asInstanceOf[PathCodec[Unit]], that)
        .asInstanceOf[PathCodec[Unit] { type PathVars = SegmentCodec.NoPathVars }]

    def alternatives: List[PathCodec[A]] =
      PathCodecRuntime.expand(self).asInstanceOf[List[PathCodec[A]]]

    def decode(path: Path): Either[String, A] =
      PathCodecRuntime
        .decodeCodec(self, path.segments, 0)
        .collectFirst {
          case (value, end) if end == path.segments.length => value.asInstanceOf[A]
        }
        .toRight(s"Path ${path.encode} did not match ${PathCodec.render(self)}")

    def format(value: A): Either[String, Path] =
      PathCodecRuntime.formatCodec(self, value.asInstanceOf[Any]).map(_.addLeadingSlash)

    def matches(path: Path): Boolean =
      decode(path).isRight

    def render: String = PathCodec.render(codec = self)

    /**
     * Maps the decoded path value without changing the underlying path
     * structure.
     *
     * Example: {{ val customerPath =
     * PathCodec.int("id").transform[CustomerId](CustomerId(_), _.value) }}
     *
     * @param decode
     *   maps the decoded path value into the exposed type
     * @param encode
     *   maps the exposed type back into the original path representation used
     *   by `format`
     * @return
     *   a path codec with the transformed value type and the same route shape
     *   as `self`
     */
    def transform[B](decode: A => B, encode: B => A): PathCodec[B] { type PathVars = PV } =
      transformOrFail[B](value => Right(decode(value)), value => Right(encode(value)))

    /**
     * Effectfully maps the decoded path value without changing the underlying
     * path structure.
     *
     * `decode` returning `Left` causes path decoding / matching to fail.
     * `format` remains effectful, so `encode` returning `Left` is propagated as
     * the `Left` result of `format`.
     *
     * Example: {{ val customerPath = PathCodec .string("id")
     * .transformOrFail[CustomerId](CustomerId.parse, value =>
     * Right(value.value)) }}
     *
     * @param decode
     *   validates and maps the decoded path value into the exposed type
     * @param encode
     *   validates and maps the exposed type back into the original path
     *   representation used by `format`
     * @return
     *   a path codec with the transformed value type and the same route shape
     *   as `self`
     */
    def transformOrFail[B](
      decode: A => Either[DecodeError, B],
      encode: B => Either[DecodeError, A]
    ): PathCodec[B] { type PathVars = PV } =
      Transform(self, decode, encode).asInstanceOf[PathCodec[B] { type PathVars = PV }]
  }

  implicit final class SinglePathVarPathCodecOps[A, N <: String with Singleton, T](
    private val self: PathCodec[A] { type PathVars = SegmentCodec.OnePathVar[PathVar[N, T]] }
  ) extends AnyVal {

    /**
     * Same path codec as `self` (identical decoded value type and identical
     * encode/decode behavior) - a pure type-level relabeling of a single
     * captured path variable from `PathVar[N, T]` to `PathVar.Ignored[N, T]`.
     * This mirrors [[SegmentCodec.IntSeg.unused]] and makes the public
     * `PathCodec.int/string/long/bool/uuid` smart constructors support the same
     * `.unused` escape hatch directly.
     */
    def unused: PathCodec[A] { type PathVars = SegmentCodec.OnePathVar[PathVar.Ignored[N, T]] } =
      self.asInstanceOf[PathCodec[A] { type PathVars = SegmentCodec.OnePathVar[PathVar.Ignored[N, T]] }]
  }

  /**
   * Combines `left`/`right` at the VALUE level only
   * (`Tuples.Tuples.WithOut[A,B,C]`), with no `PathVars`-combining implicit
   * requirement. Used internally, where at least one operand's `PathVars` is
   * not statically known to be a concrete `Unit`/`TupleN` shape (e.g. `nest`'s
   * `prefix: PathCodec[Unit]` parameter, or a fold's running accumulator) - the
   * Scala 2.13 `PathVarTuples.Combine` whitebox macro can only compute a
   * concrete `Out` when both sides are `Unit` (identity) or `TupleN` shapes, so
   * it must never be required for internal plumbing that operates on unrefined
   * `PathCodec` values. Public composition (`PathCodecOps.++`/`/`) is built on
   * top of this helper and adds the real, precise `PathVars` combine on top via
   * a final cast.
   */
  private[endpoint] def combineUnrefined[A, B, C](left: PathCodec[A], right: PathCodec[B])(implicit
    combiner: Tuples.Tuples.WithOut[A, B, C]
  ): PathCodec[C] =
    if (left == empty) right.asInstanceOf[PathCodec[C]]
    else if (right == empty) left.asInstanceOf[PathCodec[C]]
    else Concat(left, right, combiner)

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
    type PathVars = segment.PathVars
  }
  final case class Concat[A, B, C](
    left: PathCodec[A],
    right: PathCodec[B],
    combiner: Tuples.Tuples.WithOut[A, B, C]
  ) extends PathCodec[C] {
    // Ordered concatenation of left.PathVars and right.PathVars via the endpoint-scoped
    // PathVarTuples combinator (NOT Tuples.Tuples) - a best-effort placeholder for the same
    // reason as SegmentCodec.Combined's own PathVars declaration (see that type's scaladoc):
    // `left`/`right` are typed as the plain, unrefined PathCodec[A]/PathCodec[B], so no
    // expression here can be more precise. The REAL, precisely-computed, flat, ordered
    // concatenation is carried by PathCodecOps.++`/`/`'s own refined return type, which IS
    // externally observable and is what every acceptance test asserts against.
    type PathVars = PathVarTuples.Concat[left.PathVars, right.PathVars]
  }
  final case class Transform[A, B](
    codec: PathCodec[A],
    decode: A => Either[DecodeError, B],
    encode: B => Either[DecodeError, A]
  ) extends PathCodec[B] {
    type PathVars = codec.PathVars
  }
  final case class Fallback(left: PathCodec[Unit], right: PathCodec[Unit]) extends PathCodec[Unit] {
    // orElse alternatives are validated (via `expand`) to only ever contain literal segments, so
    // a Fallback never contributes any captured path variable - NoPathVars is the correct,
    // precise (not merely best-effort) value here.
    type PathVars = SegmentCodec.NoPathVars
  }

  val empty: PathCodec[Unit] { type PathVars = SegmentCodec.NoPathVars } =
    apply(SegmentCodec.Empty)

  def literal(value: String): PathCodec[Unit] { type PathVars = SegmentCodec.NoPathVars } = {
    SegmentCodec.validateLiteralValue(value)
    apply(SegmentCodec.literalValidated(value))
  }
  // `N <: String with Singleton` preserves the literal singleton type of a literal `name`
  // argument (mirrors SegmentCodec.bool/int/long/string/uuid, PathCodec.scala:105-112) so that
  // every PathCodec built from these smart constructors carries a CONCRETE (never abstract)
  // PathVars all the way through - this is load-bearing for `/`/`++`: the Scala 2.13
  // `PathVarTuples.Combine` whitebox macro can only compute a concrete `Out` type when both
  // sides it is asked to combine are concrete (`Unit`/`TupleN`) shapes, so an abstract PathVars
  // anywhere in an existing call chain (e.g. RouteTreeSpec's `literal(...) / int(...) /
  // literal(...) / int(...)`) would abort compilation on Scala 2.13 once two abstract operands
  // meet.
  def bool[N <: String with Singleton](name: N): PathCodec[Boolean] {
    type PathVars = SegmentCodec.OnePathVar[PathVar[N, Boolean]]
  } =
    apply(SegmentCodec.bool(name))
  def int[N <: String with Singleton](name: N): PathCodec[Int] {
    type PathVars = SegmentCodec.OnePathVar[PathVar[N, Int]]
  } =
    apply(SegmentCodec.int(name))
  def long[N <: String with Singleton](name: N): PathCodec[Long] {
    type PathVars = SegmentCodec.OnePathVar[PathVar[N, Long]]
  } =
    apply(SegmentCodec.long(name))
  def string[N <: String with Singleton](
    name: N
  ): PathCodec[String] { type PathVars = SegmentCodec.OnePathVar[PathVar[N, String]] } =
    apply(SegmentCodec.string(name))
  def uuid[N <: String with Singleton](
    name: N
  ): PathCodec[java.util.UUID] { type PathVars = SegmentCodec.OnePathVar[PathVar[N, java.util.UUID]] } =
    apply(SegmentCodec.uuid(name))
  val trailing: PathCodec[Path] = Segment(SegmentCodec.Trailing)

  def render(codec: PathCodec[_], prefix: String = "{", suffix: String = "}"): String =
    PathCodecRuntime.render(codec, prefix, suffix)
}
