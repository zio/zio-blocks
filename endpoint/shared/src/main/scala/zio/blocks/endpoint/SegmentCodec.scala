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
import zio.blocks.docs.Doc
import zio.http.Path

/**
 * A typed description of one URL path segment: a literal such as `notes`, a
 * capture of a `Boolean`, `Int`, `Long`, `String` or `UUID` such as `{id}`, the
 * rest of the path ([[SegmentCodec.Trailing]]), or several of these inside one
 * segment, joined with `~` (`v{major}` matches `v2`). Most code uses segments
 * through [[PathCodec]]; build one directly to use `~`.
 *
 * Numbers are ASCII digits with an optional leading `-` (no `+`), in the range
 * of the type; booleans are `true` or `false` in lower case; UUIDs are 36 hex
 * characters in the `8-4-4-4-12` layout, in either case.
 *
 * Join segments with `~` first, then map the value with [[transform]] or
 * [[transformOrFail]]: these return a [[PathCodec]], which has no `~`.
 *
 * {{{
 * import zio.http.Path
 *
 * val version = SegmentCodec.literal("v") ~ SegmentCodec.int("major")
 *
 * version.render()                    // /v{major}
 * PathCodec(version).decode(Path("/v2")) // Right(2)
 * version.format(3)                   // /v3
 * }}}
 *
 * @see
 *   [[https://zioblocks.com/reference/endpoint/segment-codec/ SegmentCodec]]
 */
sealed trait SegmentCodec[A] { self =>

  /**
   * A type-level marker: [[SegmentCodec.NoPathVars]] when this segment captures
   * nothing (empty and literal segments), [[SegmentCodec.HasPathVars]] when it
   * captures a value (bool, int, long, string, UUID, trailing, and any `~`
   * composition that contains one of those). It exists only at compile time and
   * does not change `A`. [[PathCodec.orElse]] reads it to accept only branches
   * that capture nothing.
   */
  type PathVars

  /**
   * Documentation for this segment; for a `~` composition, both sides'
   * documentation joined.
   */
  def doc: Doc

  /**
   * Named example values for this segment; empty for `~` compositions and for
   * `Empty` and `Trailing`.
   */
  def examples: Chunk[(String, A)]

  /**
   * Joins two segment codecs into one segment, as in `int("n") ~ literal("-") ~
   * string("rest")`, which matches `12-ab`. The values join with `Tuples`, so
   * literals add nothing. Two adjacent literals merge into one.
   *
   * A split that would be ambiguous throws `IllegalArgumentException` when the
   * codec is built: two strings side by side ("Cannot combine two string
   * segments. Their names are a and b"), two numbers (`Int` or `Long`, in any
   * order: "Cannot combine two numeric segments. ..."), and
   * [[SegmentCodec.Trailing]] next to any segment ("Cannot combine trailing
   * path segments with `~`: int ~ trailing"). Put a literal, bool or UUID
   * between two strings or two numbers. Other neighbours are split by trying
   * the longest left part first, so `string("s") ~ int("n")` decodes `abc12` as
   * `("abc1", 2)`.
   *
   * The result captures when either side captures.
   *
   * @throws java.lang.IllegalArgumentException
   *   for two adjacent strings, two adjacent numbers, or a trailing segment
   */
  final def ~[B, C, PV2, PVC](that: SegmentCodec[B] { type PathVars = PV2 })(implicit
    combiner: Tuples.Tuples.WithOut[A, B, C],
    pathVarsCombiner: SegmentCodec.CombinePathVars[self.PathVars, PV2, PVC]
  ): SegmentCodec[C] { type PathVars = PVC } = {
    SegmentCodec.validateCombination(self, that)
    SegmentCodec
      .combineValidated(self, that, combiner)
      .asInstanceOf[SegmentCodec[C] { type PathVars = PVC }]
  }

  /**
   * Joins a literal suffix to this segment, as in `string("word") ~ "!"`. The
   * suffix follows the rules of [[SegmentCodec.literal]], but is checked when
   * the code runs, on Scala 3 too: an empty suffix, or one with `/` or a
   * character that needs percent-encoding, throws `IllegalArgumentException`.
   *
   * @throws java.lang.IllegalArgumentException
   *   for an empty suffix or one that is not a plain single segment
   */
  final def ~[C, PVC](that: String)(implicit
    combiner: Tuples.Tuples.WithOut[A, Unit, C],
    pathVarsCombiner: SegmentCodec.CombinePathVars[self.PathVars, SegmentCodec.NoPathVars, PVC]
  ): SegmentCodec[C] { type PathVars = PVC } = {
    SegmentCodec.validateLiteralValue(that)
    (self ~ SegmentCodec.literalValidated(that)).asInstanceOf[SegmentCodec[C] { type PathVars = PVC }]
  }

  /**
   * Maps the decoded segment value to another type, returning a one-segment
   * [[PathCodec]] (see [[PathCodec#transform]]), as in
   * `SegmentCodec.string("slug").transform(NoteSlug(_), (s: NoteSlug) =>
   * s.value)`.
   *
   * @param decode
   *   maps the decoded segment value into the exposed type
   * @param encode
   *   maps the exposed type back into the original segment representation used
   *   by `format`
   * @return
   *   a path codec with the transformed value type and the same route shape as
   *   `self`
   */
  final def transform[B, PV](decode: A => B, encode: B => A)(implicit
    ev: SegmentCodec.CombinePathVars[self.PathVars, SegmentCodec.NoPathVars, PV]
  ): PathCodec[B] { type PathVars = PV } = {
    // Marker reification: solved at the call site against the caller's
    // refined `self` type (identity OR with `NoPathVars`) - precise for
    // refined segments, `HasPathVars` for unknown ones, and always concrete
    // (see `PathCodec.transform`).
    val _ = ev
    PathCodec
      .Segment(self)
      .transform(decode, encode)
      .asInstanceOf[PathCodec[B] { type PathVars = PV }]
  }

  /**
   * Maps the decoded segment value to another type with functions that can
   * fail, returning a one-segment [[PathCodec]] (see
   * [[PathCodec#transformOrFail]]). When `decode` returns `Left`, decoding and
   * matching fail; when `encode` returns `Left`, `format` returns it.
   */
  final def transformOrFail[B, PV](
    decode: A => Either[String, B],
    encode: B => Either[String, A]
  )(implicit
    ev: SegmentCodec.CombinePathVars[self.PathVars, SegmentCodec.NoPathVars, PV]
  ): PathCodec[B] { type PathVars = PV } = {
    // Marker reification: see `transform` above.
    val _ = ev
    PathCodec
      .Segment(self)
      .transformOrFail(decode, encode)
      .asInstanceOf[PathCodec[B] { type PathVars = PV }]
  }

  /**
   * Writes `value` as a one-segment path with a leading slash, as in `/42`.
   * Numbers and UUIDs use `toString`, so a UUID is written in lower case. A
   * string is copied as it is; a `/` in it gives more than one segment.
   */
  final def format(value: A): Path =
    Path(s"/${SegmentCodec.formatSegment(self, value)}")

  /**
   * Renders the segment as a template with a leading slash: literals as they
   * are, captures as `{name}` (or between `prefix` and `suffix`), a trailing
   * capture as `...`, and the empty segment as `/`.
   *
   * {{{
   * SegmentCodec.int("id").render()         // /{id}
   * SegmentCodec.int("id").render(":", "")  // /:id
   * }}}
   */
  final def render(prefix: String = "{", suffix: String = "}"): String =
    SegmentCodec.render(self, prefix, suffix)
}

/**
 * The segment kinds, their constructors (`literal`, `bool`, `int`, `long`,
 * `string`, `uuid`) and the matching, decoding and formatting functions that
 * [[PathCodec]] and [[RouteTree]] use.
 *
 * On Scala 3, `literal` checks its argument when the code compiles, so it must
 * be a string literal; on Scala 2 it checks when the code runs and throws
 * `IllegalArgumentException`.
 */
object SegmentCodec extends SegmentCodecPlatformSpecific {

  /**
   * The kind of a segment codec, without its name or literal text.
   */
  sealed trait Kind

  /**
   * The segment kinds.
   */
  object Kind {

    /** The empty segment. */
    case object Empty extends Kind

    /** A literal segment. */
    case object Literal extends Kind

    /** A `Boolean` capture. */
    case object Bool extends Kind

    /** An `Int` capture. */
    case object Int extends Kind

    /** A `Long` capture. */
    case object Long extends Kind

    /** A `String` capture. */
    case object String extends Kind

    /** A `~` composition. */
    case object Combined extends Kind

    /** A `UUID` capture. */
    case object UUID extends Kind

    /** A capture of the rest of the path. */
    case object Trailing extends Kind
  }

  /**
   * The key of a segment codec in a [[RouteTree]] level: its kind, plus the
   * text for a literal and the parts for a `~` composition. Capture names are
   * not part of the key, so `int("id")` and `int("n")` share one.
   * [[keyOrdering]] sorts keys in match order.
   */
  sealed trait Key

  /**
   * The segment keys.
   */
  object Key {

    /** The key of the empty segment. */
    case object Empty extends Key

    /** The key of a literal segment, with its text. */
    final case class Literal(value: String) extends Key

    /** The key of a `Boolean` capture. */
    case object Bool extends Key

    /** The key of an `Int` capture. */
    case object Int extends Key

    /** The key of a `Long` capture. */
    case object Long extends Key

    /** The key of a `String` capture. */
    case object String extends Key

    /** The key of a `UUID` capture. */
    case object UUID extends Key

    /** The key of a `~` composition, with the keys of its parts. */
    final case class Combined(parts: Chunk[Key]) extends Key

    /** The key of a capture of the rest of the path. */
    case object Trailing extends Key
  }

  // The `bool`, `int`, `long`, `string` and `uuid` constructors (in `SegmentCodecPlatformSpecific`)
  // bound the name with `N <: String with Singleton` on Scala 2.13 and `N <: String & Singleton` on
  // Scala 3, which keeps the literal singleton type of the `name` argument in `N` instead of
  // widening it to `String`.

  /**
   * The `PathVars` marker of segments that capture nothing (`Empty`,
   * `Literal`). It is a type only; no value of it exists.
   */
  sealed trait NoPathVars

  /**
   * The `PathVars` marker of segments that capture a value (bool, int, long,
   * string, UUID, trailing, and any composition that contains one of those). It
   * is a type only; no value of it exists.
   */
  sealed trait HasPathVars

  /**
   * Compile-time evidence that combines two `PathVars` markers: `Out` is
   * `HasPathVars` when either side is, `NoPathVars` when both are. `~`,
   * `PathCodec#++` and `/` use it to compute their result's marker. When a
   * marker is unknown (a codec whose static type has no refinement), the
   * low-priority instance gives `HasPathVars`, so `orElse` may reject such a
   * codec but never accepts a capturing one.
   */
  sealed trait CombinePathVars[L, R, Out]

  /**
   * The instances for the four pairs of known markers.
   */
  object CombinePathVars extends CombinePathVarsLowPriority {

    /**
     * Neither side captures, so neither does the result.
     */
    implicit val noNo: CombinePathVars[NoPathVars, NoPathVars, NoPathVars] =
      new CombinePathVars[NoPathVars, NoPathVars, NoPathVars] {}

    /**
     * The right side captures, so the result does.
     */
    implicit val noYes: CombinePathVars[NoPathVars, HasPathVars, HasPathVars] =
      new CombinePathVars[NoPathVars, HasPathVars, HasPathVars] {}

    /**
     * The left side captures, so the result does.
     */
    implicit val yesNo: CombinePathVars[HasPathVars, NoPathVars, HasPathVars] =
      new CombinePathVars[HasPathVars, NoPathVars, HasPathVars] {}

    /**
     * Both sides capture, so the result does.
     */
    implicit val yesYes: CombinePathVars[HasPathVars, HasPathVars, HasPathVars] =
      new CombinePathVars[HasPathVars, HasPathVars, HasPathVars] {}
  }

  /**
   * The fallback instance for unknown markers, found after the precise ones.
   */
  trait CombinePathVarsLowPriority {

    /**
     * Treats a pair with an unknown marker as capturing.
     */
    implicit def unknown[L, R]: CombinePathVars[L, R, HasPathVars] =
      new CombinePathVars[L, R, HasPathVars] {}
  }
  private[endpoint] def combineValidated[A, B, C](
    left: SegmentCodec[A],
    right: SegmentCodec[B],
    combiner: Tuples.Tuples.WithOut[A, B, C]
  ): SegmentCodec[C] =
    (left, right) match {
      case (Empty, _)                                => right.asInstanceOf[SegmentCodec[C]]
      case (_, Empty)                                => left.asInstanceOf[SegmentCodec[C]]
      case (Literal(lv, ld, le), Literal(rv, rd, _)) =>
        Literal(lv + rv, ld ++ rd, le).asInstanceOf[SegmentCodec[C]]
      case (Combined(l, r, existing), Literal(rv, rd, _)) if r.isInstanceOf[Literal] =>
        Combined(
          l.asInstanceOf[SegmentCodec[Any]],
          Literal(r.asInstanceOf[Literal].value + rv, r.asInstanceOf[Literal].doc ++ rd)
            .asInstanceOf[SegmentCodec[Any]],
          existing.asInstanceOf[Tuples.Tuples.WithOut[Any, Any, Any]]
        ).asInstanceOf[SegmentCodec[C]]
      case _ => Combined(left, right, combiner)
    }

  private[endpoint] def literalValidated(value: String): Literal = Literal(value)

  /**
   * Checks a `~` composition (as zio-http's `Combinable` does): adjacent
   * `string ~ string`, numeric ~ numeric, and `Trailing` next to a non-empty
   * segment throw `IllegalArgumentException`. It returns normally otherwise.
   */
  private[endpoint] def validateCombination(left: SegmentCodec[_], right: SegmentCodec[_]): Unit =
    (suffixBoundary(left), prefixBoundary(right)) match {
      case (Some((leftKind, _)), Some((rightKind, _))) if leftKind == Kind.Trailing || rightKind == Kind.Trailing =>
        throw new IllegalArgumentException(
          s"Cannot combine trailing path segments with `~`: ${boundaryLabel(leftKind)} ~ ${boundaryLabel(rightKind)}"
        )
      case (Some((Kind.String, leftName)), Some((Kind.String, rightName))) =>
        throw new IllegalArgumentException(
          s"Cannot combine two string segments. Their names are $leftName and $rightName"
        )
      case (Some((leftKind, leftName)), Some((rightKind, rightName))) if isNumeric(leftKind) && isNumeric(rightKind) =>
        throw new IllegalArgumentException(
          s"Cannot combine two numeric segments. Their names are $leftName and $rightName"
        )
      case _ => ()
    }

  private def prefixBoundary(codec: SegmentCodec[_]): Option[(Kind, String)] =
    codec match {
      case Empty                    => None
      case Literal(value, _, _)     => Some((Kind.Literal, value))
      case BoolSeg(name, _, _)      => Some((Kind.Bool, name))
      case IntSeg(name, _, _)       => Some((Kind.Int, name))
      case LongSeg(name, _, _)      => Some((Kind.Long, name))
      case StringSeg(name, _, _)    => Some((Kind.String, name))
      case UUIDSeg(name, _, _)      => Some((Kind.UUID, name))
      case Trailing                 => Some((Kind.Trailing, "trailing"))
      case Combined(left, right, _) =>
        prefixBoundary(left).orElse(prefixBoundary(right))
    }

  private def suffixBoundary(codec: SegmentCodec[_]): Option[(Kind, String)] =
    codec match {
      case Empty                    => None
      case Literal(value, _, _)     => Some((Kind.Literal, value))
      case BoolSeg(name, _, _)      => Some((Kind.Bool, name))
      case IntSeg(name, _, _)       => Some((Kind.Int, name))
      case LongSeg(name, _, _)      => Some((Kind.Long, name))
      case StringSeg(name, _, _)    => Some((Kind.String, name))
      case UUIDSeg(name, _, _)      => Some((Kind.UUID, name))
      case Trailing                 => Some((Kind.Trailing, "trailing"))
      case Combined(left, right, _) =>
        suffixBoundary(right).orElse(suffixBoundary(left))
    }

  private def boundaryLabel(kind: Kind): String =
    kind match {
      case Kind.Literal  => "literal"
      case Kind.Bool     => "bool"
      case Kind.Int      => "int"
      case Kind.Long     => "long"
      case Kind.String   => "string"
      case Kind.UUID     => "uuid"
      case Kind.Combined => "combined"
      case Kind.Trailing => "trailing"
      case Kind.Empty    => "empty"
    }

  private def isNumeric(kind: Kind): Boolean = kind == Kind.Int || kind == Kind.Long

  /**
   * Rejects empty literals and literals carrying `/` or characters that
   * percent-encoding would change, by throwing `IllegalArgumentException`.
   * `PathCodec.apply(String)` and `RoutePattern.apply` do not call it.
   */
  private[endpoint] def validateLiteralValue(value: String): Unit = {
    val path          = Path(s"/$value")
    val singleSegment = path.segments.length == 1 && path.segments.headOption.contains(value)
    val preserved     = path.encode.stripPrefix("/") == value

    if (value.isEmpty) {
      throw new IllegalArgumentException("SegmentCodec.literal cannot be empty")
    } else if (!singleSegment || !preserved) {
      throw new IllegalArgumentException(
        s"SegmentCodec.literal must be a valid single path segment without `/` or characters that require URL encoding: $value"
      )
    }
  }

  private[endpoint] def kind(codec: SegmentCodec[_]): Kind =
    codec match {
      case Empty              => Kind.Empty
      case Literal(_, _, _)   => Kind.Literal
      case BoolSeg(_, _, _)   => Kind.Bool
      case IntSeg(_, _, _)    => Kind.Int
      case LongSeg(_, _, _)   => Kind.Long
      case StringSeg(_, _, _) => Kind.String
      case UUIDSeg(_, _, _)   => Kind.UUID
      case Combined(_, _, _)  => Kind.Combined
      case Trailing           => Kind.Trailing
    }

  private[endpoint] def key(codec: SegmentCodec[_]): Key =
    codec match {
      case Empty                => Key.Empty
      case Literal(value, _, _) => Key.Literal(value)
      case BoolSeg(_, _, _)     => Key.Bool
      case IntSeg(_, _, _)      => Key.Int
      case LongSeg(_, _, _)     => Key.Long
      case StringSeg(_, _, _)   => Key.String
      case UUIDSeg(_, _, _)     => Key.UUID
      case Combined(_, _, _)    => Key.Combined(flatten(codec).map(key))
      case Trailing             => Key.Trailing
    }

  /**
   * Orders segment kinds by match priority: literal, int, long, UUID, bool,
   * string, combined, trailing, then empty.
   */
  implicit val segmentCodecOrdering: Ordering[Kind] = Ordering.by {
    case Kind.Literal  => -1
    case Kind.Int      => 0
    case Kind.Long     => 1
    case Kind.UUID     => 2
    case Kind.Bool     => 3
    case Kind.String   => 4
    case Kind.Combined => 5
    case Kind.Trailing => 6
    case Kind.Empty    => 7
  }

  /**
   * Orders route-tree keys by match priority: literals (alphabetically), int,
   * long, UUID, bool, string, `~` compositions (fewer parts first), trailing,
   * then empty.
   */
  implicit val keyOrdering: Ordering[Key] = new Ordering[Key] {
    def compare(a: Key, b: Key): Int = {
      def rank(k: Key): Int = k match {
        case Key.Literal(_)  => 0
        case Key.Int         => 1
        case Key.Long        => 2
        case Key.UUID        => 3
        case Key.Bool        => 4
        case Key.String      => 5
        case Key.Combined(_) => 6
        case Key.Trailing    => 7
        case Key.Empty       => 8
      }

      val cmp = rank(a) - rank(b)
      if (cmp != 0) cmp
      else
        (a, b) match {
          case (Key.Literal(x), Key.Literal(y))   => x.compareTo(y)
          case (Key.Combined(x), Key.Combined(y)) =>
            val len = x.length.compareTo(y.length)
            if (len != 0) len
            else x.iterator.zip(y.iterator).map { case (l, r) => compare(l, r) }.find(_ != 0).getOrElse(0)
          case _ => 0
        }
    }
  }

  /**
   * The segment that matches nothing and consumes no path segment, used for an
   * empty path. It renders as `/`.
   */
  case object Empty extends SegmentCodec[Unit] {

    /** Captures nothing. */
    type PathVars = NoPathVars

    /** Empty documentation. */
    val doc: Doc = Doc.empty

    /** No examples. */
    val examples: Chunk[(String, Unit)] = Chunk.empty
  }

  /**
   * A literal segment that matches `value` exactly, case included. Build it
   * with `SegmentCodec.literal`, which checks the text.
   */
  final case class Literal(value: String, doc: Doc = Doc.empty, examples: Chunk[(String, Unit)] = Chunk.empty)
      extends SegmentCodec[Unit] {

    /** Captures nothing. */
    type PathVars = NoPathVars
  }

  /**
   * Captures a `Boolean`: the segment `true` or `false`, in lower case. Build
   * it with `SegmentCodec.bool(name)`.
   */
  final case class BoolSeg[N <: String](name: N, doc: Doc = Doc.empty, examples: Chunk[(String, Boolean)] = Chunk.empty)
      extends SegmentCodec[Boolean] {

    /** Captures a value. */
    type PathVars = HasPathVars

    /**
     * Marks this capture as intentionally unused: the route keeps matching the
     * same `true`/`false` shape and keeps rendering `{name}`, but decoding
     * yields `Unit`, so no handler parameter is required for it. Lifts into a
     * [[PathCodec]] exactly like [[SegmentCodec.transform]].
     */
    def unused: PathCodec[Unit] { type PathVars = HasPathVars } =
      PathCodec(this).unused
  }

  /**
   * Captures an `Int`: ASCII digits with an optional `-`, in the `Int` range
   * (`-0` and leading zeros are accepted, `+` is not). Build it with
   * `SegmentCodec.int(name)`.
   */
  final case class IntSeg[N <: String](name: N, doc: Doc = Doc.empty, examples: Chunk[(String, Int)] = Chunk.empty)
      extends SegmentCodec[Int] {

    /** Captures a value. */
    type PathVars = HasPathVars

    /**
     * Marks this capture as intentionally unused: the route keeps matching the
     * same integer shape and keeps rendering `{name}`, but decoding yields
     * `Unit`, so no handler parameter is required for it. Lifts into a
     * [[PathCodec]] exactly like [[SegmentCodec.transform]].
     */
    def unused: PathCodec[Unit] { type PathVars = HasPathVars } =
      PathCodec(this).unused
  }

  /**
   * Captures a `Long`, with the same rules as [[IntSeg]] in the `Long` range.
   * Build it with `SegmentCodec.long(name)`.
   */
  final case class LongSeg[N <: String](name: N, doc: Doc = Doc.empty, examples: Chunk[(String, Long)] = Chunk.empty)
      extends SegmentCodec[Long] {

    /** Captures a value. */
    type PathVars = HasPathVars

    /**
     * Marks this capture as intentionally unused: the route keeps matching the
     * same long shape and keeps rendering `{name}`, but decoding yields `Unit`,
     * so no handler parameter is required for it. Lifts into a [[PathCodec]]
     * exactly like [[SegmentCodec.transform]].
     */
    def unused: PathCodec[Unit] { type PathVars = HasPathVars } =
      PathCodec(this).unused
  }

  /**
   * Captures a `String`: any segment, the empty one too. Build it with
   * `SegmentCodec.string(name)`.
   */
  final case class StringSeg[N <: String](
    name: N,
    doc: Doc = Doc.empty,
    examples: Chunk[(String, String)] = Chunk.empty
  ) extends SegmentCodec[String] {

    /** Captures a value. */
    type PathVars = HasPathVars

    /**
     * Marks this capture as intentionally unused: the route keeps matching any
     * segment and keeps rendering `{name}`, but decoding yields `Unit`, so no
     * handler parameter is required for it. Lifts into a [[PathCodec]] exactly
     * like [[SegmentCodec.transform]].
     */
    def unused: PathCodec[Unit] { type PathVars = HasPathVars } =
      PathCodec(this).unused
  }

  /**
   * Captures a `java.util.UUID`: 36 hex characters in the `8-4-4-4-12` layout,
   * upper or lower case, with any version. Build it with
   * `SegmentCodec.uuid(name)`.
   */
  final case class UUIDSeg[N <: String](
    name: N,
    doc: Doc = Doc.empty,
    examples: Chunk[(String, java.util.UUID)] = Chunk.empty
  ) extends SegmentCodec[java.util.UUID] {

    /** Captures a value. */
    type PathVars = HasPathVars

    /**
     * Marks this capture as intentionally unused: the route keeps matching the
     * same UUID shape and keeps rendering `{name}`, but decoding yields `Unit`,
     * so no handler parameter is required for it. Lifts into a [[PathCodec]]
     * exactly like [[SegmentCodec.transform]].
     */
    def unused: PathCodec[Unit] { type PathVars = HasPathVars } =
      PathCodec(this).unused
  }

  /**
   * Two segment codecs inside one segment, built by `~`; `combiner` joins their
   * values.
   */
  final case class Combined[A, B, C](
    left: SegmentCodec[A],
    right: SegmentCodec[B],
    combiner: Tuples.Tuples.WithOut[A, B, C]
  ) extends SegmentCodec[C] {

    /**
     * Left abstract: only `~`, which combines both sides' markers with
     * `CombinePathVars`, gives it a concrete one. A `Combined` built directly
     * keeps an unknown marker, which counts as capturing.
     */
    type PathVars

    /** The documentation of both sides, joined. */
    val doc: Doc = left.doc ++ right.doc

    /** No examples. */
    val examples: Chunk[(String, C)] = Chunk.empty
  }

  /**
   * Captures the rest of the path, zero or more segments, as a
   * [[zio.http.Path]]. It renders as `...` and cannot be joined with `~`.
   */
  case object Trailing extends SegmentCodec[Path] {

    /** Captures a value. */
    type PathVars = HasPathVars

    /** Empty documentation. */
    val doc: Doc = Doc.empty

    /** No examples. */
    val examples: Chunk[(String, Path)] = Chunk.empty
  }

  /**
   * Renders `codec` as a template (see `SegmentCodec#render`).
   */
  def render(codec: SegmentCodec[_], prefix: String = "{", suffix: String = "}"): String = {
    val out                                  = new StringBuilder
    def loop(current: SegmentCodec[_]): Unit =
      current match {
        case Empty                    => ()
        case Literal(value, _, _)     => out.append(value)
        case BoolSeg(name, _, _)      => out.append(prefix).append(name).append(suffix)
        case IntSeg(name, _, _)       => out.append(prefix).append(name).append(suffix)
        case LongSeg(name, _, _)      => out.append(prefix).append(name).append(suffix)
        case StringSeg(name, _, _)    => out.append(prefix).append(name).append(suffix)
        case UUIDSeg(name, _, _)      => out.append(prefix).append(name).append(suffix)
        case Combined(left, right, _) =>
          loop(left)
          loop(right)
        case Trailing => out.append("...")
      }
    loop(codec)
    if (out.isEmpty) "/" else s"/${out.result()}"
  }

  /**
   * Returns how many of `segments` `codec` consumes from `index`: `1` when the
   * segment there matches, `-1` when it does not or `index` is out of range,
   * `0` for [[Empty]], and all the remaining segments (possibly `0`) for
   * [[Trailing]]. It checks the segment's shape without building the decoded
   * value; [[RouteTree]] uses it to choose a branch.
   */
  def matches(codec: SegmentCodec[_], segments: Chunk[String], index: Int): Int =
    if (index < 0 || index > segments.length) -1
    else
      codec match {
        case Empty    => 0
        case Trailing => (segments.length - index).max(0)
        case _        =>
          if (index >= segments.length) -1
          else if (matchesComplete(codec, segments(index))) 1
          else -1
      }

  /**
   * Tests whether `codec` matches the whole of `segment`, without building the
   * decoded value: the boolean counterpart of [[decodeComplete]], which accepts
   * the same segments. A string capture matches any segment, the empty one too.
   */
  def matchesComplete(codec: SegmentCodec[_], segment: String): Boolean =
    matchesWindow(codec, segment, 0, segment.length)

  /**
   * Decodes the whole of `segment`: the results of `decodeCombined(codec,
   * segment, 0)` that end at `segment.length`, each paired with that end, the
   * longest left part first. An empty list means no match.
   */
  def decodeComplete(codec: SegmentCodec[_], segment: String): List[(Any, Int)] =
    decodeCombined(codec, segment, 0).filter(_._2 == segment.length)

  /**
   * Exact-window boolean check over `[from, end)`: does `codec` match exactly
   * that slice? Leaves validate the whole window with the allocation-free index
   * scanners (never a substring, never a stdlib parser, never thrown
   * exceptions); `Combined` enumerates every candidate split point `mid` in
   * `[from, end]` (bounded by the segment length - no backtracking explosion,
   * since `~` already rejects the ambiguous `string ~ string` and numeric ~
   * numeric shapes) and accepts when both sides match exactly. Agrees with
   * [[decodeCombined]] by construction: the same windows that validate here are
   * the windows `decodeCombined` decodes.
   */
  private def matchesWindow(codec: SegmentCodec[_], segment: String, from: Int, end: Int): Boolean =
    if (from < 0 || end > segment.length || from > end) false
    else
      codec match {
        case Empty                       => from == end
        case Literal(value, _, _)        => end - from == value.length && segment.startsWith(value, from)
        case BoolSeg(_, _, _)            => parseBoolWindow(segment, from, end).isDefined
        case IntSeg(_, _, _)             => isIntWindow(segment, from, end)
        case LongSeg(_, _, _)            => isLongWindow(segment, from, end)
        case StringSeg(_, _, _)          => true
        case UUIDSeg(_, _, _)            => isUuidWindow(segment, from, end)
        case Trailing                    => true
        case combined: Combined[_, _, _] =>
          var mid   = from
          var found = false
          while (!found && mid <= end) {
            found = matchesWindow(combined.left, segment, from, mid) &&
              matchesWindow(combined.right, segment, mid, end)
            mid += 1
          }
          found
      }

  private def parseBoolWindow(segment: String, from: Int, end: Int): Option[Boolean] = {
    val length = end - from
    if (length == 4 && segment.startsWith("true", from)) Some(true)
    else if (length == 5 && segment.startsWith("false", from)) Some(false)
    else None
  }

  /**
   * Scans the greedy numeric run starting at `from`: an optional `-` followed
   * by ASCII digits, stopping at the first non-digit or `until`.
   *
   * Returns the end index of the run, or `-1` when there is no run at all
   * (empty window, a lone `-`, a `+` sign, or a non-digit first character).
   * Never allocates, never throws: a pure `while` + `var` scan over `charAt`.
   */
  private def scanNumericRun(segment: String, from: Int, until: Int): Int =
    if (from < 0 || until > segment.length || from >= until) -1
    else {
      var i = from
      if (segment.charAt(i) == '-') i += 1
      val digitsStart = i
      while (i < until && segment.charAt(i) >= '0' && segment.charAt(i) <= '9') i += 1
      if (i == digitsStart) -1 else i
    }

  /**
   * Allocation-free exact-window `Int` validation over `(segment, from, end)`:
   * an optional `-`, then one or more ASCII digits covering the whole window.
   * `+` is rejected everywhere (including whole-segment matches), `-0` and
   * leading zeroes are allowed, overflow is rejected, and empty or
   * whitespace-containing windows are rejected. No substring, no stdlib parse
   * helpers, no `Try`, no regex; negative accumulation handles `Int.MinValue`
   * exactly.
   */
  private[endpoint] def isIntWindow(segment: String, from: Int, end: Int): Boolean =
    if (from < 0 || end > segment.length || from >= end) false
    else {
      var i        = from
      var negative = false
      var valid    = true
      val first    = segment.charAt(i)
      if (first == '-') {
        negative = true
        i += 1
        if (i >= end) valid = false
      } else if (first < '0' || first > '9') valid = false
      var result = 0
      while (valid && i < end) {
        val d = segment.charAt(i) - '0'
        if (d < 0 || d > 9 || result < (Int.MinValue + d) / 10) valid = false
        else {
          result = result * 10 - d
          i += 1
        }
      }
      valid && (negative || result != Int.MinValue)
    }

  /**
   * Value-carrying counterpart to [[isIntWindow]] with identical acceptance:
   * returns the decoded value for decode paths (which box into `Any` anyway),
   * while [[isIntWindow]] stays allocation-free for the boolean match paths.
   */
  private[endpoint] def parseIntWindow(segment: String, from: Int, end: Int): Option[Int] =
    if (from < 0 || end > segment.length || from >= end) None
    else {
      var i        = from
      var negative = false
      var valid    = true
      val first    = segment.charAt(i)
      if (first == '-') {
        negative = true
        i += 1
        if (i >= end) valid = false
      } else if (first < '0' || first > '9') valid = false
      var result = 0
      while (valid && i < end) {
        val d = segment.charAt(i) - '0'
        if (d < 0 || d > 9 || result < (Int.MinValue + d) / 10) valid = false
        else {
          result = result * 10 - d
          i += 1
        }
      }
      if (!valid || (!negative && result == Int.MinValue)) None
      else if (negative) Some(result)
      else Some(-result)
    }

  /**
   * Allocation-free exact-window `Long` validation, same contract as
   * [[isIntWindow]] (optional `-`, ASCII digits, `+` rejected, `-0` and leading
   * zeroes allowed, overflow rejected, empty rejected).
   */
  private[endpoint] def isLongWindow(segment: String, from: Int, end: Int): Boolean =
    if (from < 0 || end > segment.length || from >= end) false
    else {
      var i        = from
      var negative = false
      var valid    = true
      val first    = segment.charAt(i)
      if (first == '-') {
        negative = true
        i += 1
        if (i >= end) valid = false
      } else if (first < '0' || first > '9') valid = false
      var result = 0L
      while (valid && i < end) {
        val d = segment.charAt(i) - '0'
        if (d < 0 || d > 9 || result < (Long.MinValue + d) / 10L) valid = false
        else {
          result = result * 10L - d
          i += 1
        }
      }
      valid && (negative || result != Long.MinValue)
    }

  /**
   * Value-carrying counterpart to [[isLongWindow]] with identical acceptance.
   */
  private[endpoint] def parseLongWindow(segment: String, from: Int, end: Int): Option[Long] =
    if (from < 0 || end > segment.length || from >= end) None
    else {
      var i        = from
      var negative = false
      var valid    = true
      val first    = segment.charAt(i)
      if (first == '-') {
        negative = true
        i += 1
        if (i >= end) valid = false
      } else if (first < '0' || first > '9') valid = false
      var result = 0L
      while (valid && i < end) {
        val d = segment.charAt(i) - '0'
        if (d < 0 || d > 9 || result < (Long.MinValue + d) / 10L) valid = false
        else {
          result = result * 10L - d
          i += 1
        }
      }
      if (!valid || (!negative && result == Long.MinValue)) None
      else if (negative) Some(result)
      else Some(-result)
    }

  /**
   * Index-based canonical UUID layout check over `(segment, from, end)`:
   * exactly 36 chars in `8-4-4-4-12` ASCII-hex shape. Version-neutral by
   * construction — v1/v4/v7/nil differ only in nibble values, never in layout —
   * so no version or variant nibble is constrained here. Never allocates (no
   * substring), never throws; malformed or short windows are simply `false`.
   */
  private[endpoint] def isUuidWindow(segment: String, from: Int, end: Int): Boolean = {
    def isHex(char: Char): Boolean =
      (char >= '0' && char <= '9') || (char >= 'a' && char <= 'f') || (char >= 'A' && char <= 'F')

    if (end - from != 36) false
    else {
      var i     = 0
      var valid = true
      while (valid && i < 36) {
        val char = segment.charAt(from + i)
        if (i == 8 || i == 13 || i == 18 || i == 23) { if (char != '-') valid = false }
        else if (!isHex(char)) valid = false
        i += 1
      }
      valid
    }
  }

  /**
   * Decodes a prefix of `segment` from `from`, listing every way it can: each
   * result pairs a value with the index where it ends, the longest first. A
   * string yields every prefix of the rest, a number every valid digit prefix,
   * and a literal or boolean at most one. [[decodeComplete]] keeps the ones
   * that reach the end of the segment.
   *
   * {{{
   * SegmentCodec.decodeCombined(SegmentCodec.int("n"), "123x", 0) // List((123,3), (12,2), (1,1))
   * }}}
   */
  def decodeCombined(codec: SegmentCodec[_], segment: String, from: Int): List[(Any, Int)] =
    codec match {
      case Empty                => List(((), from))
      case Literal(value, _, _) => if (segment.startsWith(value, from)) List(((), from + value.length)) else Nil
      case BoolSeg(_, _, _)     =>
        List("true" -> true, "false" -> false).collect {
          case (text, value) if segment.startsWith(text, from) => (value, from + text.length)
        }
      case IntSeg(_, _, _)    => intCandidates(segment, from)
      case LongSeg(_, _, _)   => longCandidates(segment, from)
      case StringSeg(_, _, _) =>
        if (from > segment.length) Nil
        else {
          val results = List.newBuilder[(Any, Int)]
          var end     = segment.length
          while (end >= from) {
            results += ((segment.substring(from, end), end))
            end -= 1
          }
          results.result()
        }
      case UUIDSeg(_, _, _) =>
        if (segment.length - from < 36 || !isUuidWindow(segment, from, from + 36)) Nil
        else {
          // Decode-only allocation boundary: the 36-char window is validated
          // index-by-index above, and a String is materialized only here, where
          // `java.util.UUID` construction requires it. Match paths never reach
          // this branch (see `matchesWindow`, which uses `isUuidWindow` alone).
          val candidate = segment.substring(from, from + 36)
          try List((java.util.UUID.fromString(candidate), from + 36))
          catch { case _: IllegalArgumentException => Nil }
        }
      case Trailing                    => List((Path(segment.substring(from)).addLeadingSlash, segment.length))
      case combined: Combined[_, _, _] =>
        // Exhaustive split enumeration (no backtracking cap beyond the segment
        // length): every `(leftValue, next)` candidate pairs with every
        // right-side decode from `next`. Leaves enumerate longest-first
        // (`StringSeg` substrings descending, numerics longest valid prefix
        // first), so the first full-length survivor is the longest viable left
        // split. Callers keep only `end == segment.length` pairs
        // (`decodeComplete`), which is what makes `"a!b!"` decode as `"a!b"`
        // for `string ~ literal("!")` and `"9999999999rest"` as
        // `(999999999, "9rest")` for `int ~ string` (the overflowing full digit
        // run validates per prefix, longest valid prefix first). The `~` guard
        // already rejects `string ~ string` and numeric ~ numeric, so this
        // enumeration stays linear-ish on real segments instead of exploding.
        decodeCombined(combined.left, segment, from).flatMap { case (leftValue, next) =>
          decodeCombined(combined.right, segment, next).map { case (rightValue, end) =>
            val typed = combined.combiner.asInstanceOf[Tuples.Tuples.WithOut[Any, Any, Any]]
            (typed.combine(leftValue, rightValue), end)
          }
        }
    }

  /**
   * Longest-first `Int` candidates over the digit run starting at `from`: the
   * greedy ASCII run (`scanNumericRun`) is walked back towards `from`, and each
   * prefix is validated by the allocation-free [[isIntWindow]] scanner and
   * decoded by [[parseIntWindow]] — same acceptance, value included. An
   * overflowing full run is simply skipped (yields `None`) while shorter valid
   * prefixes still decode, which is what lets `int("n") ~ string("s")` decode
   * `"9999999999rest"` as `(999999999, "9rest")`. Never allocates a substring,
   * never touches a stdlib parser.
   */
  private def intCandidates(segment: String, from: Int): List[(Int, Int)] = {
    val runEnd = scanNumericRun(segment, from, segment.length)
    if (runEnd < 0) Nil
    else {
      val results = List.newBuilder[(Int, Int)]
      var end     = runEnd
      while (end > from) {
        parseIntWindow(segment, from, end).foreach(value => results += ((value, end)))
        end -= 1
      }
      results.result()
    }
  }

  /**
   * Longest-first `Long` candidates, same contract as [[intCandidates]] over
   * [[isLongWindow]] / [[parseLongWindow]].
   */
  private def longCandidates(segment: String, from: Int): List[(Long, Int)] = {
    val runEnd = scanNumericRun(segment, from, segment.length)
    if (runEnd < 0) Nil
    else {
      val results = List.newBuilder[(Long, Int)]
      var end     = runEnd
      while (end > from) {
        parseLongWindow(segment, from, end).foreach(value => results += ((value, end)))
        end -= 1
      }
      results.result()
    }
  }

  /**
   * Writes `value` as the text of one segment, without a slash (see
   * `SegmentCodec#format`).
   */
  def formatSegment(codec: SegmentCodec[_], value: Any): String =
    codec match {
      case Empty                       => ""
      case Literal(value, _, _)        => value
      case BoolSeg(_, _, _)            => value.toString
      case IntSeg(_, _, _)             => value.toString
      case LongSeg(_, _, _)            => value.toString
      case StringSeg(_, _, _)          => value.asInstanceOf[String]
      case UUIDSeg(_, _, _)            => value.toString
      case Trailing                    => value.asInstanceOf[Path].render.stripPrefix("/")
      case combined: Combined[_, _, _] =>
        val typed                   = combined.combiner.asInstanceOf[Tuples.Tuples.WithOut[Any, Any, Any]]
        val (leftValue, rightValue) = typed.separate(value)
        formatSegment(combined.left, leftValue) + formatSegment(combined.right, rightValue)
    }

  /**
   * Lists the parts of a `~` composition from left to right; any other codec
   * gives itself.
   */
  def flatten(codec: SegmentCodec[_]): Chunk[SegmentCodec[_]] =
    codec match {
      case Combined(left, right, _) => flatten(left) ++ flatten(right)
      case other                    => Chunk(other)
    }
}
