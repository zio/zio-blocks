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

import scala.annotation.implicitNotFound

import zio.blocks.chunk.Chunk
import zio.blocks.combinators.Tuples
import zio.blocks.docs.Doc
import zio.http.Path

/**
 * Typed descriptor for a single URL path segment. Subtypes include
 * [[SegmentCodec.Literal Literal]], [[SegmentCodec.IntSeg Int]],
 * [[SegmentCodec.StringSeg String]], [[SegmentCodec.Combined Combined]]
 * (intra-segment composition via `~`), and [[SegmentCodec.Trailing Trailing]]
 * (captures remaining path).
 */
sealed trait SegmentCodec[A] { self =>
  type Prefix <: SegmentCodec.BoundaryTag
  type Suffix <: SegmentCodec.BoundaryTag

  /**
   * Ordered, purely phantom registry of [[PathVar]] markers contributed by this
   * segment - one marker per captured segment, zero markers for non-capturing
   * segments (`Empty`/`Literal`/`Trailing`). The phantom track parallels the
   * value track:
   *
   * {{{
   * | Track         | Carries                                              | Runtime cost |
   * |---------------|------------------------------------------------------|--------------|
   * | Value (`A`)   | the decoded segment value (`Int`, `String`, ...)  | real         |
   * | Phantom       | `PathVar[Name, Type]` markers, one per capture      | zero         |
   * }}}
   *
   * A single captured segment carries the BARE `PathVar[..]` leaf directly
   * (mirroring how the value track carries the bare decoded type); multiple
   * captured segments are combined into a flat tuple by
   * `zio.blocks.combinators.Tuples`, exactly as the value track is combined.
   * The single [[zio.blocks.endpoint.PathCodec.PathVarsCombiner combiner]] for
   * this track defers to `Tuples.WithOut` identically, so the two tracks can
   * never drift apart. Left unbounded here (rather than `<: Tuple`, which does
   * not exist as a cross-version supertype on Scala 2.13) so this single
   * declaration compiles identically under both Scala 2.13 and Scala 3.
   */
  type PathVars

  def doc: Doc
  def examples: Chunk[(String, A)]

  final def format(value: A): Path =
    Path(s"/${SegmentCodec.formatSegment(self, value)}")

  final def render(prefix: String = "{", suffix: String = "}"): String =
    SegmentCodec.render(self, prefix, suffix)
}

object SegmentCodec extends SegmentCodecPlatformSpecific {

  private[endpoint] type DecodeError                         = String
  type WithBoundaries[A, P <: BoundaryTag, S <: BoundaryTag] = SegmentCodec[A] { type Prefix = P; type Suffix = S }

  @implicitNotFound(
    "Cannot combine these two adjacent path segments (${L} ~ ${R}). Adjacent string ~ string and " +
      "adjacent numeric ~ numeric (Int / Long in any order, including through a flattened combined tail) " +
      "are ambiguous and are rejected at compile time; put a literal, bool, or UUID segment between them instead."
  )
  trait CanCombine[L <: BoundaryTag, R <: BoundaryTag]
  object CanCombine extends CanCombinePlatformSpecific

  sealed trait BoundaryTag
  object BoundaryTag {
    sealed trait Empty    extends BoundaryTag
    sealed trait Literal  extends BoundaryTag
    sealed trait Bool     extends BoundaryTag
    sealed trait Numeric  extends BoundaryTag
    sealed trait Int      extends Numeric
    sealed trait Long     extends Numeric
    sealed trait String   extends BoundaryTag
    sealed trait UUID     extends BoundaryTag
    sealed trait Trailing extends BoundaryTag
    sealed trait Unknown  extends BoundaryTag
  }

  sealed trait Kind
  object Kind {
    case object Empty    extends Kind
    case object Literal  extends Kind
    case object Bool     extends Kind
    case object Int      extends Kind
    case object Long     extends Kind
    case object String   extends Kind
    case object Combined extends Kind
    case object UUID     extends Kind
    case object Trailing extends Kind
  }

  sealed trait Key
  object Key {
    case object Empty                            extends Key
    final case class Literal(value: String)      extends Key
    case object Bool                             extends Key
    case object Int                              extends Key
    case object Long                             extends Key
    case object String                           extends Key
    case object UUID                             extends Key
    final case class Combined(parts: Chunk[Key]) extends Key
    case object Trailing                         extends Key
  }

  // `N <: String with Singleton` preserves the literal singleton type of a literal `name` argument
  // (instead of widening it to plain `String`) on both Scala 2.13 and Scala 3 - a plain `N <: String`
  // bound would let the compiler infer the widened `String` type instead. Overload resolution picks
  // this singleton-preserving overload for literal call sites (its parameter type, instantiated at
  // the literal's singleton type, is a subtype of the fallback's plain `String` parameter type).
  def bool[N <: String with Singleton](name: N): BoolSeg[N]     = BoolSeg(name)
  def int[N <: String with Singleton](name: N): IntSeg[N]       = IntSeg(name)
  def long[N <: String with Singleton](name: N): LongSeg[N]     = LongSeg(name)
  def string[N <: String with Singleton](name: N): StringSeg[N] = StringSeg(name)
  def uuid[N <: String with Singleton](name: N): UUIDSeg[N]     = UUIDSeg(name)

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
   * Rejects empty literals and literals carrying `/` or characters requiring
   * URL encoding (see the encoding trio on `PathCodec.apply(String)`): such
   * literals cannot be expressed — use a capturing segment instead.
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

  private[endpoint] def transformValidated[A, B](
    codec: SegmentCodec[A],
    decode: A => Either[DecodeError, B],
    encode: B => Either[DecodeError, A]
  ): SegmentCodec[B] =
    Transform(codec, decode, encode)

  private[endpoint] def kind(codec: SegmentCodec[_]): Kind =
    codec match {
      case Empty                  => Kind.Empty
      case Literal(_, _, _)       => Kind.Literal
      case BoolSeg(_, _, _)       => Kind.Bool
      case IntSeg(_, _, _)        => Kind.Int
      case LongSeg(_, _, _)       => Kind.Long
      case StringSeg(_, _, _)     => Kind.String
      case UUIDSeg(_, _, _)       => Kind.UUID
      case Combined(_, _, _)      => Kind.Combined
      case Transform(inner, _, _) => kind(inner)
      case Trailing               => Kind.Trailing
    }

  private[endpoint] def key(codec: SegmentCodec[_]): Key =
    codec match {
      case Empty                  => Key.Empty
      case Literal(value, _, _)   => Key.Literal(value)
      case BoolSeg(_, _, _)       => Key.Bool
      case IntSeg(_, _, _)        => Key.Int
      case LongSeg(_, _, _)       => Key.Long
      case StringSeg(_, _, _)     => Key.String
      case UUIDSeg(_, _, _)       => Key.UUID
      case Combined(_, _, _)      => Key.Combined(flatten(codec).map(key))
      case Transform(inner, _, _) => key(inner)
      case Trailing               => Key.Trailing
    }

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

  case object Empty extends SegmentCodec[Unit] {
    type Prefix   = BoundaryTag.Empty
    type Suffix   = BoundaryTag.Empty
    type PathVars = NoPathVars
    val doc: Doc                        = Doc.empty
    val examples: Chunk[(String, Unit)] = Chunk.empty
  }

  final case class Literal(value: String, doc: Doc = Doc.empty, examples: Chunk[(String, Unit)] = Chunk.empty)
      extends SegmentCodec[Unit] {
    type Prefix   = BoundaryTag.Literal
    type Suffix   = BoundaryTag.Literal
    type PathVars = NoPathVars
  }

  final case class BoolSeg[N <: String](name: N, doc: Doc = Doc.empty, examples: Chunk[(String, Boolean)] = Chunk.empty)
      extends SegmentCodec[Boolean] {
    type Prefix   = BoundaryTag.Bool
    type Suffix   = BoundaryTag.Bool
    type PathVars = PathVar[N, Boolean]

    /**
     * Same codec as `this` (identical `A`/`Prefix`/`Suffix`, identical
     * encode/decode behavior) - a pure type-level relabeling of `PathVars` from
     * `PathVar[N, Boolean]` to `PathVar.Ignored[N, Boolean]`, marking this
     * captured segment as intentionally unused (see [[PathVar.Ignored]] for
     * what that distinction means to downstream consumers). Zero runtime cost:
     * implemented as a same-instance type ascription, exactly like every other
     * phantom-type refinement in this file.
     */
    def unused: WithBoundaries[Boolean, BoundaryTag.Bool, BoundaryTag.Bool] {
      type PathVars = PathVar.Ignored[N, Boolean]
    } =
      this.asInstanceOf[
        WithBoundaries[Boolean, BoundaryTag.Bool, BoundaryTag.Bool] {
          type PathVars = PathVar.Ignored[N, Boolean]
        }
      ]
  }

  final case class IntSeg[N <: String](name: N, doc: Doc = Doc.empty, examples: Chunk[(String, Int)] = Chunk.empty)
      extends SegmentCodec[Int] {
    type Prefix   = BoundaryTag.Int
    type Suffix   = BoundaryTag.Int
    type PathVars = PathVar[N, Int]

    /**
     * Pure type-level relabeling of `PathVars` from `PathVar[N, Int]` to
     * `PathVar.Ignored[N, Int]` — same mechanics as [[BoolSeg.unused]] (zero
     * runtime cost; see [[PathVar.Ignored]] for what the tag means).
     */
    def unused: WithBoundaries[Int, BoundaryTag.Int, BoundaryTag.Int] {
      type PathVars = PathVar.Ignored[N, Int]
    } =
      this.asInstanceOf[
        WithBoundaries[Int, BoundaryTag.Int, BoundaryTag.Int] {
          type PathVars = PathVar.Ignored[N, Int]
        }
      ]
  }

  final case class LongSeg[N <: String](name: N, doc: Doc = Doc.empty, examples: Chunk[(String, Long)] = Chunk.empty)
      extends SegmentCodec[Long] {
    type Prefix   = BoundaryTag.Long
    type Suffix   = BoundaryTag.Long
    type PathVars = PathVar[N, Long]

    /**
     * Pure type-level relabeling of `PathVars` from `PathVar[N, Long]` to
     * `PathVar.Ignored[N, Long]` — same mechanics as [[BoolSeg.unused]] (zero
     * runtime cost; see [[PathVar.Ignored]] for what the tag means).
     */
    def unused: WithBoundaries[Long, BoundaryTag.Long, BoundaryTag.Long] {
      type PathVars = PathVar.Ignored[N, Long]
    } =
      this.asInstanceOf[
        WithBoundaries[Long, BoundaryTag.Long, BoundaryTag.Long] {
          type PathVars = PathVar.Ignored[N, Long]
        }
      ]
  }

  final case class StringSeg[N <: String](
    name: N,
    doc: Doc = Doc.empty,
    examples: Chunk[(String, String)] = Chunk.empty
  ) extends SegmentCodec[String] {
    type Prefix   = BoundaryTag.String
    type Suffix   = BoundaryTag.String
    type PathVars = PathVar[N, String]

    /**
     * Pure type-level relabeling of `PathVars` from `PathVar[N, String]` to
     * `PathVar.Ignored[N, String]` — same mechanics as [[BoolSeg.unused]] (zero
     * runtime cost; see [[PathVar.Ignored]] for what the tag means).
     */
    def unused: WithBoundaries[String, BoundaryTag.String, BoundaryTag.String] {
      type PathVars = PathVar.Ignored[N, String]
    } =
      this.asInstanceOf[
        WithBoundaries[String, BoundaryTag.String, BoundaryTag.String] {
          type PathVars = PathVar.Ignored[N, String]
        }
      ]
  }

  final case class UUIDSeg[N <: String](
    name: N,
    doc: Doc = Doc.empty,
    examples: Chunk[(String, java.util.UUID)] = Chunk.empty
  ) extends SegmentCodec[java.util.UUID] {
    type Prefix   = BoundaryTag.UUID
    type Suffix   = BoundaryTag.UUID
    type PathVars = PathVar[N, java.util.UUID]

    /**
     * Pure type-level relabeling of `PathVars` from `PathVar[N, UUID]` to
     * `PathVar.Ignored[N, UUID]` — same mechanics as [[BoolSeg.unused]] (zero
     * runtime cost; see [[PathVar.Ignored]] for what the tag means).
     */
    def unused: WithBoundaries[java.util.UUID, BoundaryTag.UUID, BoundaryTag.UUID] {
      type PathVars = PathVar.Ignored[N, java.util.UUID]
    } =
      this.asInstanceOf[
        WithBoundaries[java.util.UUID, BoundaryTag.UUID, BoundaryTag.UUID] {
          type PathVars = PathVar.Ignored[N, java.util.UUID]
        }
      ]
  }

  final case class Combined[A, B, C](
    left: SegmentCodec[A],
    right: SegmentCodec[B],
    combiner: Tuples.Tuples.WithOut[A, B, C]
  ) extends SegmentCodec[C] {
    type Prefix = left.Prefix
    type Suffix = right.Suffix
    // Best-effort placeholder (see `PathVars` above for the phantom-track table): `left`/`right`
    // are unrefined, so no expression here can be precise. The REAL flat ordered concatenation is
    // carried by the `~` extension method's own refined return type (via `Tuples.Tuples.WithOut`).
    type PathVars = (left.PathVars, right.PathVars)
    val doc: Doc                     = left.doc ++ right.doc
    val examples: Chunk[(String, C)] = Chunk.empty
  }

  final case class Transform[A, B](
    codec: SegmentCodec[A],
    decode: A => Either[DecodeError, B],
    encode: B => Either[DecodeError, A]
  ) extends SegmentCodec[B] {
    type Prefix   = codec.Prefix
    type Suffix   = codec.Suffix
    type PathVars = codec.PathVars
    val doc: Doc                     = codec.doc
    val examples: Chunk[(String, B)] = Chunk.empty
  }

  case object Trailing extends SegmentCodec[Path] {
    type Prefix   = BoundaryTag.Trailing
    type Suffix   = BoundaryTag.Trailing
    type PathVars = NoPathVars
    val doc: Doc                        = Doc.empty
    val examples: Chunk[(String, Path)] = Chunk.empty
  }

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
        case Transform(inner, _, _) =>
          loop(inner)
        case Trailing => out.append("...")
      }
    loop(codec)
    if (out.isEmpty) "/" else s"/${out.result()}"
  }

  /**
   * Returns how many segments `codec` consumes at `index` (`-1` for no match).
   *
   * This is the boolean fast path for routing: `Literal` is a single `==`,
   * `IntSeg`/`LongSeg` a single allocation-free ASCII window scan
   * (`isIntWindow`/`isLongWindow`), `UUIDSeg` a hand-rolled shape check (no
   * exceptions thrown), and `Combined`/`Transform` go through the deterministic
   * single-split `matchesWindow` below. Candidate lists are never built here
   * (unlike [[decodeCombined]]), so per-request trie lookups pay no
   * per-candidate tuple or substring-enumeration cost. The subsequent handler
   * `decode` re-parses the matched segment once — that match-then-decode double
   * parse is accepted deliberately (decode happens once per matched request,
   * while `matches` runs per dynamic branch).
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
   * Boolean fast path for a whole segment: does `codec` match `segment` exactly
   * (deterministic single split)? Never builds candidate lists and never
   * allocates — `StringSeg` is a constant-time accept, numerics validate the
   * whole window with the allocation-free ASCII scanners (no substring),
   * `UUIDSeg` is validated index-by-index without exceptions. Used by
   * [[matches]] and `PathCodecRuntime.matchesCodec`; the allocating
   * [[decodeCombined]] remains for callers that need decoded values at every
   * split.
   */
  def matchesComplete(codec: SegmentCodec[_], segment: String): Boolean =
    matchesWindow(codec, segment, 0, segment.length)

  /**
   * Value-carrying counterpart to [[matchesComplete]]: like
   * `decodeCombined(codec, segment, 0)` but keeps the deterministic single
   * split only (`end == segment.length`), so `StringSeg` materializes one
   * substring instead of O(n) and numerics parse once instead of per prefix.
   * Correct wherever callers already filter `end == segment.length`
   * (segment-level `Transform`/`Combined` decode); nested splits inside a
   * `Combined` go through `decodeBounded` with the split point as the bound.
   */
  def decodeComplete(codec: SegmentCodec[_], segment: String): List[(Any, Int)] =
    decodeBounded(codec, segment, 0, segment.length)

  private def matchesWindow(codec: SegmentCodec[_], segment: String, from: Int, end: Int): Boolean =
    if (from < 0 || end > segment.length || from > end) false
    else
      codec match {
        case Empty                        => from == end
        case Literal(value, _, _)         => end - from == value.length && segment.startsWith(value, from)
        case BoolSeg(_, _, _)             => parseBoolWindow(segment, from, end).isDefined
        case IntSeg(_, _, _)              => isIntWindow(segment, from, end)
        case LongSeg(_, _, _)             => isLongWindow(segment, from, end)
        case StringSeg(_, _, _)           => true
        case UUIDSeg(_, _, _)             => isUuidWindow(segment, from, end)
        case Trailing                     => true
        case transformed: Transform[_, _] =>
          // `decode` needs the decoded value, so this one node type falls back to the bounded
          // decode and checks acceptance only (intra-segment `Transform` is rare).
          val inner  = transformed.codec.asInstanceOf[SegmentCodec[Any]]
          val decode = transformed.decode.asInstanceOf[Any => Either[DecodeError, Any]]
          decodeBounded(inner, segment, from, end).exists { case (value, _) => decode(value).isRight }
        case combined: Combined[_, _, _] =>
          val mid = splitMid(combined.left, combined.right, segment, from, end)
          mid >= 0 && matchesWindow(combined.left, segment, from, mid) &&
          matchesWindow(combined.right, segment, mid, end)
      }

  /**
   * Bounded variant of [[decodeCombined]] keeping only `(value, end)` pairs
   * whose end equals the `end` bound. Every leaf yields at most one pair, so no
   * garbage candidates are ever built; `Combined` uses the single deterministic
   * split (see `splitMid`: greedy numerics, fixed-width UUID, first-valid
   * string lookahead, no backtracking) with both sides bounded. Per-pattern
   * memoization was considered and skipped: patterns are immutable descriptors
   * shared across requests, but results depend on the input segment, so a cache
   * keyed by input would grow without bound for negligible gain on short
   * segments.
   */
  private[endpoint] def decodeBounded(
    codec: SegmentCodec[_],
    segment: String,
    from: Int,
    end: Int
  ): List[(Any, Int)] =
    if (from < 0 || end > segment.length || from > end) Nil
    else
      codec match {
        case Empty                => if (from == end) List(((), from)) else Nil
        case Literal(value, _, _) =>
          if (end - from == value.length && segment.startsWith(value, from)) List(((), end)) else Nil
        case BoolSeg(_, _, _)   => parseBoolWindow(segment, from, end).map(value => List((value, end))).getOrElse(Nil)
        case IntSeg(_, _, _)    => parseIntWindow(segment, from, end).map(value => List((value, end))).getOrElse(Nil)
        case LongSeg(_, _, _)   => parseLongWindow(segment, from, end).map(value => List((value, end))).getOrElse(Nil)
        case StringSeg(_, _, _) => List((segment.substring(from, end), end))
        case UUIDSeg(_, _, _)   =>
          if (!isUuidWindow(segment, from, end)) Nil
          else
            try List((java.util.UUID.fromString(segment.substring(from, end)), end))
            catch { case _: IllegalArgumentException => Nil }
        case Trailing                     => List((Path(segment.substring(from)).addLeadingSlash, segment.length))
        case transformed: Transform[_, _] =>
          val inner  = transformed.codec.asInstanceOf[SegmentCodec[Any]]
          val decode = transformed.decode.asInstanceOf[Any => Either[DecodeError, Any]]
          decodeBounded(inner, segment, from, end).flatMap { case (value, _) =>
            decode(value).toOption.map(_ -> end)
          }
        case combined: Combined[_, _, _] =>
          val mid = splitMid(combined.left, combined.right, segment, from, end)
          if (mid < 0) Nil
          else
            decodeBounded(combined.left, segment, from, mid).flatMap { case (leftValue, _) =>
              decodeBounded(combined.right, segment, mid, end).map { case (rightValue, _) =>
                val typed = combined.combiner.asInstanceOf[Tuples.Tuples.WithOut[Any, Any, Any]]
                (typed.combine(leftValue, rightValue), end)
              }
            }
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

  def decodeCombined(codec: SegmentCodec[_], segment: String, from: Int): List[(Any, Int)] =
    codec match {
      case Empty                => List(((), from))
      case Literal(value, _, _) => if (segment.startsWith(value, from)) List(((), from + value.length)) else Nil
      case BoolSeg(_, _, _)     =>
        List("true" -> true, "false" -> false).collect {
          case (text, value) if segment.startsWith(text, from) => (value, from + text.length)
        }
      case IntSeg(_, _, _) =>
        val runEnd = scanNumericRun(segment, from, segment.length)
        if (runEnd < 0) Nil
        else parseIntWindow(segment, from, runEnd).map(value => List((value, runEnd))).getOrElse(Nil)
      case LongSeg(_, _, _) =>
        val runEnd = scanNumericRun(segment, from, segment.length)
        if (runEnd < 0) Nil
        else parseLongWindow(segment, from, runEnd).map(value => List((value, runEnd))).getOrElse(Nil)
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
      case transformed: Transform[_, _] =>
        val inner  = transformed.codec.asInstanceOf[SegmentCodec[Any]]
        val decode = transformed.decode.asInstanceOf[Any => Either[DecodeError, Any]]
        decodeCombined(inner, segment, from).flatMap { case (value, end) =>
          decode(value).toOption.map(_ -> end)
        }
      case Trailing                    => List((Path(segment.substring(from)).addLeadingSlash, segment.length))
      case combined: Combined[_, _, _] =>
        // Deterministic single split over the whole remaining segment (no
        // backtracking): `splitMid` fixes the left/right boundary, then each
        // side decodes at most one value — numerics greedily, UUID fixed-width,
        // string-before-X by first-valid lookahead.
        val mid = splitMid(combined.left, combined.right, segment, from, segment.length)
        if (mid < 0) Nil
        else
          decodeBounded(combined.left, segment, from, mid).flatMap { case (leftValue, _) =>
            decodeBounded(combined.right, segment, mid, segment.length).map { case (rightValue, end) =>
              val typed = combined.combiner.asInstanceOf[Tuples.Tuples.WithOut[Any, Any, Any]]
              (typed.combine(leftValue, rightValue), end)
            }
          }
    }

  def formatSegment(codec: SegmentCodec[_], value: Any): String =
    codec match {
      case Empty                        => ""
      case Literal(value, _, _)         => value
      case BoolSeg(_, _, _)             => value.toString
      case IntSeg(_, _, _)              => value.toString
      case LongSeg(_, _, _)             => value.toString
      case StringSeg(_, _, _)           => value.asInstanceOf[String]
      case UUIDSeg(_, _, _)             => value.toString
      case transformed: Transform[_, _] =>
        val inner  = transformed.codec.asInstanceOf[SegmentCodec[Any]]
        val encode = transformed.encode.asInstanceOf[Any => Either[DecodeError, Any]]
        encode(value) match {
          case Right(innerValue) => formatSegment(inner, innerValue)
          case Left(message)     => throw new IllegalArgumentException(message)
        }
      case Trailing                    => value.asInstanceOf[Path].render.stripPrefix("/")
      case combined: Combined[_, _, _] =>
        val typed                   = combined.combiner.asInstanceOf[Tuples.Tuples.WithOut[Any, Any, Any]]
        val (leftValue, rightValue) = typed.separate(value)
        formatSegment(combined.left, leftValue) + formatSegment(combined.right, rightValue)
    }

  def flatten(codec: SegmentCodec[_]): Chunk[SegmentCodec[_]] =
    codec match {
      case Combined(left, right, _) => flatten(left) ++ flatten(right)
      case Transform(inner, _, _)   => flatten(inner)
      case other                    => Chunk(other)
    }

  /**
   * First syntactic leaf of a (possibly nested or transformed) codec, used only
   * to compute deterministic string lookahead in `stringSplitHead`. `Transform`
   * preserves boundaries, so unwrapping it is shape-correct; values still
   * decode through the tree recursion, never through this helper.
   */
  private def firstLeaf(codec: SegmentCodec[_]): SegmentCodec[_] =
    codec match {
      case Combined(left, _, _)   => firstLeaf(left)
      case Transform(inner, _, _) => firstLeaf(inner)
      case other                  => other
    }

  /**
   * Deterministic string-before-X split: the end index of the string part, or
   * `-1` when no deterministic split exists. `head` is the first leaf of what
   * follows the string. Literals use first occurrence, numerics split at the
   * first ASCII digit, UUID takes the first layout-valid 36-char window, bools
   * split at the first `true`/`false` keyword. Never allocates, never throws.
   */
  private def stringSplitHead(head: SegmentCodec[_], segment: String, from: Int, end: Int): Int =
    head match {
      case Literal(value, _, _) =>
        val idx = segment.indexOf(value, from)
        if (idx < 0 || idx + value.length > end) -1 else idx
      case IntSeg(_, _, _) | LongSeg(_, _, _) =>
        var i = from
        while (i < end && (segment.charAt(i) < '0' || segment.charAt(i) > '9')) i += 1
        if (i >= end) -1 else i
      case UUIDSeg(_, _, _) =>
        var m     = from
        var found = -1
        while (found < 0 && m + 36 <= end) {
          if (isUuidWindow(segment, m, m + 36)) found = m else m += 1
        }
        found
      case BoolSeg(_, _, _) =>
        var m     = from
        var found = -1
        while (found < 0 && m < end) {
          if ((m + 4 <= end && segment.startsWith("true", m)) || (m + 5 <= end && segment.startsWith("false", m)))
            found = m
          else m += 1
        }
        found
      case Empty => end
      case _     => -1
    }

  /**
   * Deterministic intra-segment split for `Combined(left, right)` over
   * `[from, end)`: the index where `left` ends and `right` begins, or `-1`.
   *
   * Rules (no backtracking — at most one split is ever produced):
   *   - fixed-extent leaves (`Empty`, `Literal`, `Bool`, `UUID`) match at
   *     `from`;
   *   - numerics greedily consume the ASCII digit run (`scanNumericRun`) and
   *     the window must additionally satisfy the overflow-checked validators;
   *   - `String` uses first-valid lookahead into the first leaf of `right`;
   *   - nested `Combined` on the left resolves inside-out (`(a ~ b) ~ c`
   *     behaves as the flat `a ~ b ~ c` sequence), which covers flattened
   *     combined tails because every `~` already rejected ambiguous adjacency.
   */
  private def splitMid(
    left: SegmentCodec[_],
    right: SegmentCodec[_],
    segment: String,
    from: Int,
    end: Int
  ): Int =
    if (from < 0 || end > segment.length || from > end) -1
    else
      left match {
        case Empty                => from
        case Literal(value, _, _) =>
          if (from + value.length <= end && segment.startsWith(value, from)) from + value.length else -1
        case BoolSeg(_, _, _) =>
          if (from + 4 <= end && segment.startsWith("true", from)) from + 4
          else if (from + 5 <= end && segment.startsWith("false", from)) from + 5
          else -1
        case IntSeg(_, _, _) =>
          val runEnd = scanNumericRun(segment, from, end)
          if (runEnd < 0 || !isIntWindow(segment, from, runEnd)) -1 else runEnd
        case LongSeg(_, _, _) =>
          val runEnd = scanNumericRun(segment, from, end)
          if (runEnd < 0 || !isLongWindow(segment, from, runEnd)) -1 else runEnd
        case UUIDSeg(_, _, _) =>
          if (end - from >= 36 && isUuidWindow(segment, from, from + 36)) from + 36 else -1
        case StringSeg(_, _, _) =>
          stringSplitHead(firstLeaf(right), segment, from, end)
        case Transform(inner, _, _) =>
          splitMid(inner, right, segment, from, end)
        case Combined(a, b, _) =>
          val midA = splitMid(a, b, segment, from, end)
          if (midA < 0) -1 else splitMid(b, right, segment, midA, end)
        case Trailing => -1
      }
}
