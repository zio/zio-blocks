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
 * A typed description of a URL path: literal segments such as `notes`,
 * capturing segments such as `{id}`, and their order. It decodes a
 * [[zio.http.Path]] into an `A`, formats an `A` back into a path, and renders
 * as a template such as `/notes/{id}`.
 *
 * Build one from a string (`PathCodec("notes")`), from the capturing
 * constructors (`PathCodec.int("id")`, `string`, `long`, `bool`, `uuid`,
 * `trailing`) or from a [[SegmentCodec]], and join them with `/`. The captured
 * values form a tuple, and literal segments add nothing to it. `orElse` offers
 * literal alternatives, `transform` maps the value to a domain type, and
 * `unused` drops it.
 *
 * {{{
 * import zio.http.Path
 *
 * val notePath = PathCodec("notes") / PathCodec.int("id")
 *
 * notePath.render                   // /notes/{id}
 * notePath.decode(Path("/notes/42")) // Right(42)
 * notePath.format(7)                 // Right(/notes/7)
 * }}}
 *
 * @see
 *   [[https://zioblocks.com/reference/endpoint/path-codec/ PathCodec]]
 */
sealed trait PathCodec[A] { self =>

  /**
   * A type-level marker: [[SegmentCodec.NoPathVars]] when this path captures
   * nothing (the empty path and literal-only paths),
   * [[SegmentCodec.HasPathVars]] when a segment captures a value. It exists
   * only at compile time and does not change `A`. `++`, `/`, `SegmentCodec.~`,
   * `transform`, `transformOrFail` and `unused` carry it along, so [[orElse]]
   * can reject a capturing branch at compile time. A codec whose marker is
   * unknown counts as capturing.
   */
  type PathVars

  /**
   * Appends the segments of `that` to this path. The decoded values join with
   * `Tuples`: a literal side adds nothing, so `int("id") ++ string("slug")`
   * decodes to `(Int, String)`. The result captures when either side captures
   * (see [[SegmentCodec.CombinePathVars]]).
   */
  final def ++[B, C, PV2, PVC](that: PathCodec[B] { type PathVars = PV2 })(implicit
    combiner: Tuples.Tuples.WithOut[A, B, C],
    pathVarsCombiner: SegmentCodec.CombinePathVars[self.PathVars, PV2, PVC]
  ): PathCodec[C] { type PathVars = PVC } =
    PathCodec.combineUnrefined(self, that)(combiner).asInstanceOf[PathCodec[C] { type PathVars = PVC }]

  /**
   * Same as `++`: `PathCodec("notes") / PathCodec.int("id")` matches
   * `/notes/42`.
   */
  final def /[B, C, PV2, PVC](that: PathCodec[B] { type PathVars = PV2 })(implicit
    combiner: Tuples.Tuples.WithOut[A, B, C],
    pathVarsCombiner: SegmentCodec.CombinePathVars[self.PathVars, PV2, PVC]
  ): PathCodec[C] { type PathVars = PVC } =
    self.++(that)(combiner, pathVarsCombiner)

  /**
   * Matches this path or `that` one, both literal-only (`notes` or `memos`).
   * Decoding tries both; formatting writes this path. Both sides must capture
   * nothing, which the compiler checks: a capturing codec mapped to `Unit`,
   * such as `PathCodec.int("id").transform(_ => (), _ => 0)`, does not compile
   * here.
   *
   * Use one segment per branch. A branch of two or more literal segments, such
   * as `PathCodec("api/v1")`, compiles and decodes, but `alternatives` (and so
   * [[RoutePattern.alternatives]] and [[RouteTree.add]]) throws
   * `IllegalStateException` for it: a known bug.
   *
   * {{{
   * import zio.http.Path
   *
   * val people = PathCodec("users").orElse(PathCodec("members"))
   *
   * people.render                    // /users | /members
   * people.decode(Path("/members"))  // Right(())
   * people.alternatives.map(_.render) // List(/users, /members)
   * }}}
   */
  final def orElse(that: PathCodec[Unit] { type PathVars = SegmentCodec.NoPathVars })(implicit
    ev: (A, self.PathVars) =:= (Unit, SegmentCodec.NoPathVars)
  ): PathCodec[Unit] { type PathVars = SegmentCodec.NoPathVars } = {
    val _ = ev
    PathCodec
      .Fallback(self.asInstanceOf[PathCodec[Unit]], that)
      .asInstanceOf[PathCodec[Unit] { type PathVars = SegmentCodec.NoPathVars }]
  }

  /**
   * Expands `orElse` branches into one literal-only path per branch, without
   * duplicates. A path without `orElse` gives itself, and the empty path gives
   * an empty list. Throws `IllegalStateException` when a branch has more than
   * one segment (see `orElse`).
   */
  final def alternatives: List[PathCodec[A]] =
    PathCodecRuntime.expand(self).asInstanceOf[List[PathCodec[A]]].distinct

  /**
   * Decodes `path`, which must match every segment: a missing or extra segment
   * fails. Segments are compared as they are stored in the `Path`, so build it
   * with `Path.fromEncoded` when it is still percent-encoded. The leading and
   * trailing slash are ignored.
   *
   * The error names the first segment that did not match, as in `Path
   * /notes/abc did not match /notes/{id}: failed at segment 1 ('abc')`, or says
   * `matched N of M segments without a complete match` when the path ran out or
   * a `transformOrFail` decoder failed. The decoder's own message is not
   * included.
   *
   * {{{
   * import zio.http.Path
   *
   * val notePath = PathCodec("notes") / PathCodec.int("id")
   *
   * notePath.decode(Path("/notes/42"))  // Right(42)
   * notePath.decode(Path("/notes/abc")) // Left(Path /notes/abc did not match /notes/{id}: failed at segment 1 ('abc'))
   * }}}
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

  /**
   * Writes `value` into the path, with a leading slash. Captured strings are
   * copied into the path as they are, without checks, so validate values that
   * come from users. It returns `Left` when a `transformOrFail` encoder fails
   * or the path contains an `unused` part. For an `orElse` path it writes the
   * first branch.
   *
   * {{{
   * val notePath = PathCodec("notes") / PathCodec.int("id")
   *
   * notePath.format(42) // Right(/notes/42)
   * }}}
   */
  final def format(value: A): Either[String, Path] =
    PathCodecRuntime.formatCodec(self, value.asInstanceOf[Any]).map(_.addLeadingSlash)

  /**
   * Tests whether `path` matches, like `decode(path).isRight` but without
   * building the decoded value for literal and capturing segments; a
   * `transform` or `transformOrFail` still runs its decoder. They agree for the
   * paths `orElse` supports; with an `orElse` branch of several segments,
   * `matches` can return `false` where `decode` succeeds.
   */
  final def matches(path: Path): Boolean =
    PathCodecRuntime.matchesCodec(self, path.segments)

  /**
   * Renders the path as a template: literals as they are, captures as `{name}`,
   * a trailing capture as `...`, `orElse` branches joined by ` | `, and the
   * empty path as `/`.
   */
  final def render: String = PathCodec.render(codec = self)

  /**
   * Maps the decoded path value to another type without changing the path it
   * matches or renders.
   *
   * {{{
   * import zio.http.Path
   *
   * final case class NoteId(value: Int)
   *
   * val noteId = PathCodec.int("id").transform(NoteId(_), (id: NoteId) => id.value)
   *
   * noteId.decode(Path("/42")) // Right(NoteId(42))
   * noteId.format(NoteId(7))   // Right(/7)
   * }}}
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
   * Maps the decoded path value to another type with functions that can fail,
   * without changing the path it matches or renders.
   *
   * When `decode` returns `Left`, decoding and matching fail; the error from
   * `PathCodec#decode` does not include the `Left` message. When `encode`
   * returns `Left`, `format` returns that `Left`. A [[RouteTree]] does not run
   * `decode`: it routes on the segment shape only.
   *
   * {{{
   * import zio.http.Path
   *
   * val positive = PathCodec.int("id").transformOrFail(
   *   (id: Int) => if (id > 0) Right(id) else Left("not positive: " + id),
   *   (id: Int) => if (id > 0) Right(id) else Left("not positive")
   * )
   *
   * positive.decode(Path("/5"))  // Right(5)
   * positive.matches(Path("/-1")) // false
   * positive.format(0)            // Left(not positive)
   * }}}
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
   * Keeps matching and rendering the same path but decodes to `Unit`, for a
   * captured value the handler does not need. `PathCodec.int("id").unused`
   * still matches only integer segments such as `/42` and renders `/{id}`.
   *
   * The marker is kept: an unused literal-only path stays `NoPathVars`, an
   * unused capturing path stays `HasPathVars`. `format` always fails on the
   * unused part, as there is no value to write into it: `Left(Cannot format an
   * unused path segment: /{id} captures no value)`.
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

/**
 * Constructors for path codecs: from a string, from a [[SegmentCodec]], and the
 * capturing constructors `bool`, `int`, `long`, `string` and `uuid`. These take
 * the capture name as a string literal or a `val`; a `def` returning `String`
 * does not compile.
 */
object PathCodec extends PathCodecPlatformSpecific {

  private type DecodeError = String

  /**
   * The `Tuples` instance that joins two `Unit` values into `Unit`, for paths
   * of literal segments only.
   */
  implicit val unitUnit: Tuples.Tuples.WithOut[Unit, Unit, Unit] = Tuples.Tuples.leftUnit[Unit]

  private[endpoint] def combineUnrefined[A, B, C](left: PathCodec[A], right: PathCodec[B])(implicit
    combiner: Tuples.Tuples.WithOut[A, B, C]
  ): PathCodec[C] =
    if (left == empty) right.asInstanceOf[PathCodec[C]]
    else if (right == empty) left.asInstanceOf[PathCodec[C]]
    else Concat(left, right, combiner)

  /**
   * Builds a literal-only path from a string, one literal segment per part
   * between slashes: `PathCodec("api/notes")` matches `/api/notes`. Leading and
   * trailing slashes are dropped, and `""` or `"/"` gives the empty path.
   *
   * The string is split with `Path.apply`, which does not percent-decode, and
   * the parts are not checked: `PathCodec("a b")` and `PathCodec("a%20b")` are
   * accepted, and `"notes//42"` makes an empty middle segment. A request path
   * built with `Path.fromEncoded` matches the first of these (`/a%20b` decodes
   * to `a b`), and one built with `Path.apply` matches the second.
   * [[PathCodec.literal]] and [[SegmentCodec.literal]] reject such text; that
   * this constructor does not is a known bug.
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

  /**
   * Wraps a single [[SegmentCodec]], such as one built with `~`, as a path,
   * keeping its capture marker.
   */
  def apply[A, PV](segment: SegmentCodec[A] { type PathVars = PV }): PathCodec[A] { type PathVars = PV } =
    Segment(segment).asInstanceOf[PathCodec[A] { type PathVars = PV }]

  /**
   * Converts a string to a literal-only path with `PathCodec.apply(String)`, so
   * `Method.GET / "notes"` and `PathCodec.int("id") / "tags"` compile.
   */
  implicit def stringToPathCodec(value: String): PathCodec[Unit] { type PathVars = SegmentCodec.NoPathVars } =
    apply(value)

  /**
   * Converts a [[SegmentCodec]] to a path with one segment, so a segment codec
   * can follow `/`.
   */
  implicit def segmentToPathCodec[A, PV](
    value: SegmentCodec[A] { type PathVars = PV }
  ): PathCodec[A] { type PathVars = PV } =
    Segment(value).asInstanceOf[PathCodec[A] { type PathVars = PV }]

  /**
   * A path of one segment.
   */
  final case class Segment[A](segment: SegmentCodec[A]) extends PathCodec[A] {

    /**
     * Left abstract: only `PathCodec.apply(segment)`, which knows the segment's
     * own marker, gives it a concrete one.
     */
    type PathVars
  }

  /**
   * Two paths one after the other, built by `++` and `/`; `combiner` joins
   * their values.
   */
  final case class Concat[A, B, C](
    left: PathCodec[A],
    right: PathCodec[B],
    combiner: Tuples.Tuples.WithOut[A, B, C]
  ) extends PathCodec[C] {

    /**
     * Left abstract: only `++` and `/`, which combine both sides' markers with
     * `CombinePathVars`, give it a concrete one.
     */
    type PathVars
  }

  /**
   * A path whose value is mapped by `decode` and `encode`, built by `transform`
   * and `transformOrFail`.
   */
  final case class Transform[A, B](
    codec: PathCodec[A],
    decode: A => Either[DecodeError, B],
    encode: B => Either[DecodeError, A]
  ) extends PathCodec[B] {

    /**
     * Left abstract: only `transform` and `transformOrFail`, which carry the
     * inner codec's marker over, give it a concrete one.
     */
    type PathVars
  }

  /**
   * A path whose value is dropped, built by `unused`.
   */
  final case class Ignored[A](
    codec: PathCodec[A]
  ) extends PathCodec[Unit] {

    /**
     * Left abstract: only `unused`, which carries the inner codec's marker
     * over, gives it a concrete one.
     */
    type PathVars
  }

  /**
   * Two literal-only alternatives, built by `orElse`.
   */
  final case class Fallback(left: PathCodec[Unit], right: PathCodec[Unit]) extends PathCodec[Unit] {

    /**
     * Left abstract: only `orElse`, which checks that both branches capture
     * nothing, gives it `NoPathVars`.
     */
    type PathVars
  }

  /**
   * The path with no segments. It matches `/` and the empty path, renders as
   * `/`, and disappears when joined to another path with `/`.
   */
  val empty: PathCodec[Unit] { type PathVars = SegmentCodec.NoPathVars } =
    apply(SegmentCodec.Empty)

  /**
   * Builds a path of one literal segment. It throws
   * `java.lang.IllegalArgumentException` for an empty value
   * ("SegmentCodec.literal cannot be empty") and for a value with `/` or a
   * character that needs percent-encoding, such as a space or `é`
   * ("SegmentCodec.literal must be a valid single path segment without `/` or
   * characters that require URL encoding: <value>").
   *
   * @throws java.lang.IllegalArgumentException
   *   for an empty value or one that is not a plain single segment
   */
  def literal(value: String): PathCodec[Unit] { type PathVars = SegmentCodec.NoPathVars } = {
    SegmentCodec.validateLiteralValue(value)
    apply(SegmentCodec.literalValidated(value))
  }

  /**
   * Captures the rest of the path, zero or more segments, as a
   * [[zio.http.Path]] with a leading slash. It renders as `...`.
   *
   * {{{
   * import zio.http.Path
   *
   * val assets = PathCodec("assets") / PathCodec.trailing
   *
   * assets.decode(Path("/assets/css/site.css")) // Right(/css/site.css)
   * assets.decode(Path("/assets"))              // Right(/)
   * }}}
   */
  val trailing: PathCodec[Path] { type PathVars = SegmentCodec.HasPathVars } =
    apply(SegmentCodec.Trailing)

  /**
   * Renders `codec` as a template, writing each capture name between `prefix`
   * and `suffix` (`{` and `}` by default).
   */
  def render(codec: PathCodec[_], prefix: String = "{", suffix: String = "}"): String =
    PathCodecRuntime.render(codec, prefix, suffix)
}
