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
 * Typed descriptor for a single URL path segment. Subtypes include
 * [[SegmentCodec.Literal Literal]], [[SegmentCodec.IntSeg Int]],
 * [[SegmentCodec.StringSeg String]], [[SegmentCodec.Combined Combined]]
 * (intra-segment composition via `~`), and [[SegmentCodec.Trailing Trailing]]
 * (captures remaining path).
 *
 * Combine representation first, then transform: `~` composes only physical
 * `SegmentCodec` structure, while [[transform]] / [[transformOrFail]] lift the
 * segment into a [[PathCodec]] (making `~` unavailable afterwards — a
 * transformed wire codec is a domain value, not intra-segment structure).
 */
sealed trait SegmentCodec[A] { self =>
  def doc: Doc
  def examples: Chunk[(String, A)]

  /**
   * Composes two physical segments into one (for example
   * `int("n") ~ literal("-") ~ string("rest")`).
   *
   * Adjacent `string ~ string`, numeric ~ numeric (`Int`/`Long` in any order,
   * including through a flattened combined tail), and anything involving
   * `Trailing` are rejected at runtime with `IllegalArgumentException`
   * (ambiguous splits); put a literal, bool, or UUID segment between them.
   */
  final def ~[B, C](that: SegmentCodec[B])(implicit combiner: Tuples.Tuples.WithOut[A, B, C]): SegmentCodec[C] = {
    SegmentCodec.validateCombination(self, that)
    SegmentCodec.combineValidated(self, that, combiner)
  }

  /**
   * Composes a physical segment with a literal suffix (for example
   * `string("word") ~ "!"`). The literal is validated exactly like
   * [[SegmentCodec.literal]] (empty, `/`-carrying, or encoding-requiring values
   * are rejected with `IllegalArgumentException`).
   */
  final def ~[C](that: String)(implicit combiner: Tuples.Tuples.WithOut[A, Unit, C]): SegmentCodec[C] = {
    SegmentCodec.validateLiteralValue(that)
    self ~ SegmentCodec.literalValidated(that)
  }

  /**
   * Maps the decoded segment value into a domain type, lifting into a
   * [[PathCodec]].
   *
   * Example: `SegmentCodec.string("id").transform(CustomerId(_), _.value)`
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
  final def transform[B](decode: A => B, encode: B => A): PathCodec[B] =
    PathCodec.Segment(self).transform(decode, encode)

  /**
   * Effectfully maps the decoded segment value into a domain type, lifting into
   * a [[PathCodec]]. `decode` returning `Left` causes path decoding / matching
   * to fail; `encode` returning `Left` is propagated as the `Left` result of
   * `format`.
   *
   * Example:
   * `SegmentCodec.string("id").transformOrFail(CustomerId.parse, id => Right(id.value))`
   */
  final def transformOrFail[B](
    decode: A => Either[String, B],
    encode: B => Either[String, A]
  ): PathCodec[B] =
    PathCodec.Segment(self).transformOrFail(decode, encode)

  final def format(value: A): Path =
    Path(s"/${SegmentCodec.formatSegment(self, value)}")

  final def render(prefix: String = "{", suffix: String = "}"): String =
    SegmentCodec.render(self, prefix, suffix)
}

object SegmentCodec extends SegmentCodecPlatformSpecific {

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
   * Runtime composition guard for [[SegmentCodec.~]] (mirroring zio-http's
   * `Combinable`): adjacent `string ~ string`, numeric ~ numeric, and anything
   * involving `Trailing` are ambiguous splits and fail fast with
   * `IllegalArgumentException`. Never allocates, never throws otherwise.
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
    val doc: Doc                        = Doc.empty
    val examples: Chunk[(String, Unit)] = Chunk.empty
  }

  final case class Literal(value: String, doc: Doc = Doc.empty, examples: Chunk[(String, Unit)] = Chunk.empty)
      extends SegmentCodec[Unit]

  final case class BoolSeg[N <: String](name: N, doc: Doc = Doc.empty, examples: Chunk[(String, Boolean)] = Chunk.empty)
      extends SegmentCodec[Boolean]

  final case class IntSeg[N <: String](name: N, doc: Doc = Doc.empty, examples: Chunk[(String, Int)] = Chunk.empty)
      extends SegmentCodec[Int]

  final case class LongSeg[N <: String](name: N, doc: Doc = Doc.empty, examples: Chunk[(String, Long)] = Chunk.empty)
      extends SegmentCodec[Long]

  final case class StringSeg[N <: String](
    name: N,
    doc: Doc = Doc.empty,
    examples: Chunk[(String, String)] = Chunk.empty
  ) extends SegmentCodec[String]

  final case class UUIDSeg[N <: String](
    name: N,
    doc: Doc = Doc.empty,
    examples: Chunk[(String, java.util.UUID)] = Chunk.empty
  ) extends SegmentCodec[java.util.UUID]

  final case class Combined[A, B, C](
    left: SegmentCodec[A],
    right: SegmentCodec[B],
    combiner: Tuples.Tuples.WithOut[A, B, C]
  ) extends SegmentCodec[C] {
    val doc: Doc                     = left.doc ++ right.doc
    val examples: Chunk[(String, C)] = Chunk.empty
  }

  case object Trailing extends SegmentCodec[Path] {
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
   * exceptions thrown), and `Combined` goes through the deterministic
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
   * Correct wherever callers already filter `end == segment.length` (`Combined`
   * decode); nested splits inside a `Combined` go through `decodeBounded` with
   * the split point as the bound.
   */
  def decodeComplete(codec: SegmentCodec[_], segment: String): List[(Any, Int)] =
    decodeBounded(codec, segment, 0, segment.length)

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
        case Trailing                    => List((Path(segment.substring(from)).addLeadingSlash, segment.length))
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

  def flatten(codec: SegmentCodec[_]): Chunk[SegmentCodec[_]] =
    codec match {
      case Combined(left, right, _) => flatten(left) ++ flatten(right)
      case other                    => Chunk(other)
    }

  /**
   * First syntactic leaf of a (possibly nested) codec, used only to compute
   * deterministic string lookahead in `stringSplitHead`. Values still decode
   * through the tree recursion, never through this helper.
   */
  private def firstLeaf(codec: SegmentCodec[_]): SegmentCodec[_] =
    codec match {
      case Combined(left, _, _) => firstLeaf(left)
      case other                => other
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
   *     behaves as the flat `a ~ b ~ c` sequence).
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
        case Combined(a, b, _) =>
          val midA = splitMid(a, b, segment, from, end)
          if (midA < 0) -1 else splitMid(b, right, segment, midA, end)
        case Trailing => -1
      }
}
