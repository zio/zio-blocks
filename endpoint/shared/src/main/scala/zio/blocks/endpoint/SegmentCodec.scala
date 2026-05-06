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

import scala.quoted.*

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

  def doc: Doc
  def examples: Chunk[(String, A)]

  final def format(value: A): Path =
    Path(s"/${SegmentCodec.formatSegment(self, value)}")

  final def render(prefix: String = "{", suffix: String = "}"): String =
    SegmentCodec.render(self, prefix, suffix)
}

object SegmentCodec {

  private type DecodeError                                   = String
  type WithBoundaries[A, P <: BoundaryTag, S <: BoundaryTag] = SegmentCodec[A] { type Prefix = P; type Suffix = S }

  sealed trait CanCombine[L <: BoundaryTag, R <: BoundaryTag]
  object CanCombine {
    transparent inline given [L <: BoundaryTag, R <: BoundaryTag]: CanCombine[L, R] =
      ${ SegmentCodecMacros.canCombineImpl[L, R] }
  }

  sealed trait BoundaryTag
  object BoundaryTag {
    sealed trait Empty    extends BoundaryTag
    sealed trait Literal  extends BoundaryTag
    sealed trait Bool     extends BoundaryTag
    sealed trait Int      extends BoundaryTag
    sealed trait Long     extends BoundaryTag
    sealed trait String   extends BoundaryTag
    sealed trait UUID     extends BoundaryTag
    sealed trait Trailing extends BoundaryTag
    sealed trait Unknown  extends BoundaryTag
  }

  enum Kind {
    case Empty
    case Literal
    case Bool
    case Int
    case Long
    case String
    case UUID
    case Combined
    case Trailing
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

  extension [A, P <: BoundaryTag, S <: BoundaryTag](self: WithBoundaries[A, P, S]) {
    inline def ~[B, P2 <: BoundaryTag, S2 <: BoundaryTag, C](that: WithBoundaries[B, P2, S2])(using
      combiner: Tuples.Tuples.WithOut[A, B, C],
      canCombine: CanCombine[S, P2]
    ): WithBoundaries[C, P, S2] =
      ${ SegmentCodecMacros.combineImpl[A, B, C, P, S, P2, S2]('self, 'that, 'combiner) }

    inline def ~[C](inline that: String)(using
      combiner: Tuples.Tuples.WithOut[A, Unit, C],
      canCombine: CanCombine[S, BoundaryTag.Literal]
    ): WithBoundaries[C, P, BoundaryTag.Literal] =
      ${ SegmentCodecMacros.combineLiteralImpl[A, C, P, S]('self, 'that, 'combiner) }

    /**
     * Maps the decoded segment value without changing the underlying segment
     * boundary shape.
     *
     * The returned codec keeps the same compile-time `~` combinability metadata
     * as `self`, so transformed codecs still participate in compile-time
     * boundary validation.
     *
     * Example: {{ val customerId =
     * SegmentCodec.uuid("id").transform[CustomerId](CustomerId(_), _.value) }}
     *
     * @param decode
     *   maps the parsed segment value into the exposed type
     * @param encode
     *   maps the exposed type back into the original segment representation
     *   used by [[format]]
     * @return
     *   a segment codec with the transformed value type and the same boundary
     *   semantics as `self`
     */
    inline def transform[B](decode: A => B, encode: B => A): WithBoundaries[B, P, S] =
      ${ SegmentCodecMacros.transformImpl[A, B, P, S]('self, 'decode, 'encode) }

    /**
     * Effectfully maps the decoded segment value without changing the
     * underlying segment boundary shape.
     *
     * `decode` returning `Left` causes path decoding / matching to fail for
     * that segment. Because [[format]] is not effectful, `encode` returning
     * `Left` is surfaced as an `IllegalArgumentException` when formatting the
     * transformed value back into a path.
     *
     * Example: {{ val customerId = SegmentCodec .string("id")
     * .transformOrFail[CustomerId](CustomerId.parse, value =>
     * Right(value.value)) }}
     *
     * @param decode
     *   validates and maps the parsed segment value into the exposed type
     * @param encode
     *   validates and maps the exposed type back into the original segment
     *   representation used by [[format]]
     * @return
     *   a segment codec with the transformed value type and the same boundary
     *   semantics as `self`
     */
    inline def transformOrFail[B](
      decode: A => Either[DecodeError, B],
      encode: B => Either[DecodeError, A]
    ): WithBoundaries[B, P, S] =
      ${ SegmentCodecMacros.transformOrFailImpl[A, B, P, S]('self, 'decode, 'encode) }
  }

  /**
   * Creates a fixed literal delimiter inside a single path segment.
   *
   * The `value` must be a compile-time string literal. Delimiters containing
   * `/` or characters that would require URL encoding are rejected at compile
   * time. For runtime path strings, use
   * [[zio.blocks.endpoint.PathCodec.apply PathCodec.apply]] or build from
   * parsed [[zio.http.Path]] segments.
   */
  inline def literal(inline value: String): Literal =
    ${ SegmentCodecMacros.literalImpl('value) }
  def bool(name: String): BoolSeg     = BoolSeg(name)
  def int(name: String): IntSeg       = IntSeg(name)
  def long(name: String): LongSeg     = LongSeg(name)
  def string(name: String): StringSeg = StringSeg(name)
  def uuid(name: String): UUIDSeg     = UUIDSeg(name)

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

  private[endpoint] def transformValidated[A, B](
    codec: SegmentCodec[A],
    decode: A => Either[DecodeError, B],
    encode: B => Either[DecodeError, A]
  ): SegmentCodec[B] =
    Transform(codec, decode, encode)

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

  given keyOrdering: Ordering[Key] = (a: Key, b: Key) => {
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
          else x.iterator.zip(y.iterator).map { case (l, r) => keyOrdering.compare(l, r) }.find(_ != 0).getOrElse(0)
        case _ => 0
      }
  }

  case object Empty extends SegmentCodec[Unit] {
    type Prefix = BoundaryTag.Empty
    type Suffix = BoundaryTag.Empty
    val doc: Doc                        = Doc.empty
    val examples: Chunk[(String, Unit)] = Chunk.empty
  }

  final case class Literal(value: String, doc: Doc = Doc.empty, examples: Chunk[(String, Unit)] = Chunk.empty)
      extends SegmentCodec[Unit] {
    type Prefix = BoundaryTag.Literal
    type Suffix = BoundaryTag.Literal
  }

  final case class BoolSeg(name: String, doc: Doc = Doc.empty, examples: Chunk[(String, Boolean)] = Chunk.empty)
      extends SegmentCodec[Boolean] {
    type Prefix = BoundaryTag.Bool
    type Suffix = BoundaryTag.Bool
  }

  final case class IntSeg(name: String, doc: Doc = Doc.empty, examples: Chunk[(String, Int)] = Chunk.empty)
      extends SegmentCodec[Int] {
    type Prefix = BoundaryTag.Int
    type Suffix = BoundaryTag.Int
  }

  final case class LongSeg(name: String, doc: Doc = Doc.empty, examples: Chunk[(String, Long)] = Chunk.empty)
      extends SegmentCodec[Long] {
    type Prefix = BoundaryTag.Long
    type Suffix = BoundaryTag.Long
  }

  final case class StringSeg(name: String, doc: Doc = Doc.empty, examples: Chunk[(String, String)] = Chunk.empty)
      extends SegmentCodec[String] {
    type Prefix = BoundaryTag.String
    type Suffix = BoundaryTag.String
  }

  final case class UUIDSeg(
    name: String,
    doc: Doc = Doc.empty,
    examples: Chunk[(String, java.util.UUID)] = Chunk.empty
  ) extends SegmentCodec[java.util.UUID] {
    type Prefix = BoundaryTag.UUID
    type Suffix = BoundaryTag.UUID
  }

  final case class Combined[A, B, C](
    left: SegmentCodec[A],
    right: SegmentCodec[B],
    combiner: Tuples.Tuples.WithOut[A, B, C]
  ) extends SegmentCodec[C] {
    type Prefix = left.Prefix
    type Suffix = right.Suffix
    val doc: Doc                     = left.doc ++ right.doc
    val examples: Chunk[(String, C)] = Chunk.empty
  }

  final case class Transform[A, B](
    codec: SegmentCodec[A],
    decode: A => Either[DecodeError, B],
    encode: B => Either[DecodeError, A]
  ) extends SegmentCodec[B] {
    type Prefix = codec.Prefix
    type Suffix = codec.Suffix
    val doc: Doc                     = codec.doc
    val examples: Chunk[(String, B)] = Chunk.empty
  }

  case object Trailing extends SegmentCodec[Path] {
    type Prefix = BoundaryTag.Trailing
    type Suffix = BoundaryTag.Trailing
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

  def matches(codec: SegmentCodec[_], segments: Chunk[String], index: Int): Int =
    if (index < 0 || index > segments.length) -1
    else
      codec match {
        case Empty                => 0
        case Literal(value, _, _) => if (index < segments.length && segments(index) == value) 1 else -1
        case BoolSeg(_, _, _)     =>
          if (index < segments.length && (segments(index) == "true" || segments(index) == "false")) 1 else -1
        case IntSeg(_, _, _)    => if (index < segments.length && segments(index).toIntOption.isDefined) 1 else -1
        case LongSeg(_, _, _)   => if (index < segments.length && segments(index).toLongOption.isDefined) 1 else -1
        case StringSeg(_, _, _) => if (index < segments.length) 1 else -1
        case UUIDSeg(_, _, _)   =>
          if (index >= segments.length) -1
          else {
            try {
              java.util.UUID.fromString(segments(index))
              1
            } catch {
              case _: IllegalArgumentException => -1
            }
          }
        case transformed: Transform[?, ?] =>
          val inner  = transformed.codec.asInstanceOf[SegmentCodec[Any]]
          val decode = transformed.decode.asInstanceOf[Any => Either[DecodeError, Any]]
          if (index >= segments.length) -1
          else
            decodeCombined(inner, segments(index), 0).exists { case (value, end) =>
              end == segments(index).length && decode(value).isRight
            }.compare(false)
        case Trailing                    => (segments.length - index).max(0)
        case combined: Combined[?, ?, ?] =>
          if (index >= segments.length) -1
          else if (decodeCombined(combined, segments(index), 0).exists(_._2 == segments(index).length)) 1
          else -1
      }

  def decodeCombined(codec: SegmentCodec[_], segment: String, from: Int): List[(Any, Int)] =
    codec match {
      case Empty                => List(((), from))
      case Literal(value, _, _) => if (segment.startsWith(value, from)) List(((), from + value.length)) else Nil
      case BoolSeg(_, _, _)     =>
        List("true" -> true, "false" -> false).collect {
          case (text, value) if segment.startsWith(text, from) => (value, from + text.length)
        }
      case IntSeg(_, _, _)    => numericCandidates(segment, from, _.toIntOption)
      case LongSeg(_, _, _)   => numericCandidates(segment, from, _.toLongOption)
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
        if (segment.length - from < 36) Nil
        else {
          val candidate = segment.substring(from, from + 36)
          try List((java.util.UUID.fromString(candidate), from + 36))
          catch { case _: IllegalArgumentException => Nil }
        }
      case transformed: Transform[?, ?] =>
        val inner  = transformed.codec.asInstanceOf[SegmentCodec[Any]]
        val decode = transformed.decode.asInstanceOf[Any => Either[DecodeError, Any]]
        decodeCombined(inner, segment, from).flatMap { case (value, end) =>
          decode(value).toOption.map(_ -> end)
        }
      case Trailing                    => List((Path(segment.substring(from)).addLeadingSlash, segment.length))
      case combined: Combined[?, ?, ?] =>
        decodeCombined(combined.left, segment, from).flatMap { case (leftValue, next) =>
          decodeCombined(combined.right, segment, next).map { case (rightValue, end) =>
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
      case transformed: Transform[?, ?] =>
        val inner  = transformed.codec.asInstanceOf[SegmentCodec[Any]]
        val encode = transformed.encode.asInstanceOf[Any => Either[DecodeError, Any]]
        encode(value) match {
          case Right(innerValue) => formatSegment(inner, innerValue)
          case Left(message)     => throw new IllegalArgumentException(message)
        }
      case Trailing                    => value.asInstanceOf[Path].render.stripPrefix("/")
      case combined: Combined[?, ?, ?] =>
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

  private def numericCandidates[A](segment: String, from: Int, parse: String => Option[A]): List[(A, Int)] =
    if (from >= segment.length) Nil
    else {
      val start = if (segment.charAt(from) == '-') from + 1 else from
      if (start >= segment.length || !segment.charAt(start).isDigit) Nil
      else {
        var end     = start + 1
        val results = List.newBuilder[(A, Int)]
        while (end <= segment.length && (end == start + 1 || segment.charAt(end - 1).isDigit)) {
          parse(segment.substring(from, end)).foreach(v => results += (v -> end))
          end += 1
        }
        results.result()
      }
    }

  private object SegmentCodecMacros {
    private sealed trait SegmentInfoKind
    private object SegmentInfoKind {
      case object Literal  extends SegmentInfoKind
      case object Bool     extends SegmentInfoKind
      case object Int      extends SegmentInfoKind
      case object Long     extends SegmentInfoKind
      case object String   extends SegmentInfoKind
      case object UUID     extends SegmentInfoKind
      case object Trailing extends SegmentInfoKind
    }

    private final case class SegmentInfo(kind: SegmentInfoKind, name: String)
    private final case class BoundaryInfo(prefix: Option[SegmentInfo], suffix: Option[SegmentInfo]) {
      def ++(that: BoundaryInfo): BoundaryInfo =
        BoundaryInfo(prefix.orElse(that.prefix), that.suffix.orElse(suffix))
    }

    def combineImpl[
      A: Type,
      B: Type,
      C: Type,
      P <: BoundaryTag: Type,
      S <: BoundaryTag: Type,
      P2 <: BoundaryTag: Type,
      S2 <: BoundaryTag: Type
    ](
      leftExpr: Expr[WithBoundaries[A, P, S]],
      rightExpr: Expr[WithBoundaries[B, P2, S2]],
      combinerExpr: Expr[Tuples.Tuples.WithOut[A, B, C]]
    )(using Quotes): Expr[WithBoundaries[C, P, S2]] = {
      import quotes.reflect.*

      val leftInfo  = boundaryInfo(leftExpr.asTerm)
      val rightInfo = boundaryInfo(rightExpr.asTerm)

      (leftInfo, rightInfo) match {
        case (Some(left), Some(right)) =>
          validateBoundary(left.suffix, right.prefix)
          '{
            SegmentCodec.combineValidated($leftExpr, $rightExpr, $combinerExpr).asInstanceOf[WithBoundaries[C, P, S2]]
          }
        case _ =>
          '{
            SegmentCodec.combineValidated($leftExpr, $rightExpr, $combinerExpr).asInstanceOf[WithBoundaries[C, P, S2]]
          }
      }
    }

    def combineLiteralImpl[A: Type, C: Type, P <: BoundaryTag: Type, S <: BoundaryTag: Type](
      leftExpr: Expr[WithBoundaries[A, P, S]],
      rightExpr: Expr[String],
      combinerExpr: Expr[Tuples.Tuples.WithOut[A, Unit, C]]
    )(using Quotes): Expr[WithBoundaries[C, P, BoundaryTag.Literal]] =
      '{
        SegmentCodec
          .combineValidated($leftExpr, SegmentCodec.literal($rightExpr), $combinerExpr)
          .asInstanceOf[WithBoundaries[C, P, BoundaryTag.Literal]]
      }

    def canCombineImpl[L <: BoundaryTag: Type, R <: BoundaryTag: Type](using Quotes): Expr[CanCombine[L, R]] = {
      import quotes.reflect.*

      val left  = TypeRepr.of[L]
      val right = TypeRepr.of[R]

      def is(boundary: TypeRepr, target: TypeRepr): Boolean = boundary <:< target

      def label(boundary: TypeRepr): String =
        if is(boundary, TypeRepr.of[BoundaryTag.Literal]) then "literal"
        else if is(boundary, TypeRepr.of[BoundaryTag.Bool]) then "bool"
        else if is(boundary, TypeRepr.of[BoundaryTag.Int]) then "int"
        else if is(boundary, TypeRepr.of[BoundaryTag.Long]) then "long"
        else if is(boundary, TypeRepr.of[BoundaryTag.String]) then "string"
        else if is(boundary, TypeRepr.of[BoundaryTag.UUID]) then "uuid"
        else if is(boundary, TypeRepr.of[BoundaryTag.Trailing]) then "trailing"
        else if is(boundary, TypeRepr.of[BoundaryTag.Empty]) then "empty"
        else boundary.show

      val leftTrailing  = is(left, TypeRepr.of[BoundaryTag.Trailing])
      val rightTrailing = is(right, TypeRepr.of[BoundaryTag.Trailing])
      val leftString    = is(left, TypeRepr.of[BoundaryTag.String])
      val rightString   = is(right, TypeRepr.of[BoundaryTag.String])
      val leftNumeric   = is(left, TypeRepr.of[BoundaryTag.Int]) || is(left, TypeRepr.of[BoundaryTag.Long])
      val rightNumeric  = is(right, TypeRepr.of[BoundaryTag.Int]) || is(right, TypeRepr.of[BoundaryTag.Long])
      val leftKnown     =
        is(left, TypeRepr.of[BoundaryTag.Empty]) || is(left, TypeRepr.of[BoundaryTag.Literal]) ||
          is(left, TypeRepr.of[BoundaryTag.Bool]) || leftNumeric || leftString ||
          is(left, TypeRepr.of[BoundaryTag.UUID]) || leftTrailing
      val rightKnown =
        is(right, TypeRepr.of[BoundaryTag.Empty]) || is(right, TypeRepr.of[BoundaryTag.Literal]) ||
          is(right, TypeRepr.of[BoundaryTag.Bool]) || rightNumeric || rightString ||
          is(right, TypeRepr.of[BoundaryTag.UUID]) || rightTrailing

      if leftTrailing || rightTrailing then
        report.errorAndAbort(s"Cannot combine trailing path segments with `~`: ${label(left)} ~ ${label(right)}")
      else if leftString && rightString then
        report.errorAndAbort(s"Cannot combine two string segments with `~`: ${label(left)} ~ ${label(right)}")
      else if leftNumeric && rightNumeric then
        report.errorAndAbort(s"Cannot combine two numeric segments with `~`: ${label(left)} ~ ${label(right)}")
      else if !leftKnown || !rightKnown then
        report.errorAndAbort(
          s"Cannot prove segment combination at compile time: ${label(left)} ~ ${label(right)}"
        )
      else '{ new CanCombine[L, R] {} }
    }

    def literalImpl(valueExpr: Expr[String])(using Quotes): Expr[Literal] = {
      import quotes.reflect.*

      valueExpr.value match {
        case Some(value) =>
          validateLiteralValue(value)
          '{ SegmentCodec.literalValidated(${ Expr(value) }) }
        case None =>
          report.errorAndAbort("SegmentCodec.literal requires a string literal known at compile time")
      }
    }

    def transformImpl[A: Type, B: Type, P <: BoundaryTag: Type, S <: BoundaryTag: Type](
      codecExpr: Expr[WithBoundaries[A, P, S]],
      decodeExpr: Expr[A => B],
      encodeExpr: Expr[B => A]
    )(using Quotes): Expr[WithBoundaries[B, P, S]] =
      '{
        SegmentCodec
          .transformValidated[A, B](
            $codecExpr,
            value => Right($decodeExpr(value)),
            value => Right($encodeExpr(value))
          )
          .asInstanceOf[WithBoundaries[B, P, S]]
      }

    def transformOrFailImpl[A: Type, B: Type, P <: BoundaryTag: Type, S <: BoundaryTag: Type](
      codecExpr: Expr[WithBoundaries[A, P, S]],
      decodeExpr: Expr[A => Either[DecodeError, B]],
      encodeExpr: Expr[B => Either[DecodeError, A]]
    )(using Quotes): Expr[WithBoundaries[B, P, S]] =
      '{
        SegmentCodec
          .transformValidated[A, B]($codecExpr, $decodeExpr, $encodeExpr)
          .asInstanceOf[WithBoundaries[B, P, S]]
      }

    private def validateBoundary(using Quotes)(left: Option[SegmentInfo], right: Option[SegmentInfo]): Unit = {
      import quotes.reflect.*

      (left, right) match {
        case (Some(SegmentInfo(SegmentInfoKind.Trailing, _)), _) |
            (_, Some(SegmentInfo(SegmentInfoKind.Trailing, _))) =>
          report.errorAndAbort("Cannot combine trailing path segments with `~`")
        case (
              Some(SegmentInfo(SegmentInfoKind.String, leftName)),
              Some(SegmentInfo(SegmentInfoKind.String, rightName))
            ) =>
          report.errorAndAbort(s"Cannot combine two string segments. Their names are $leftName and $rightName")
        case (
              Some(SegmentInfo(SegmentInfoKind.Int | SegmentInfoKind.Long, leftName)),
              Some(SegmentInfo(SegmentInfoKind.Int | SegmentInfoKind.Long, rightName))
            ) =>
          report.errorAndAbort(s"Cannot combine two numeric segments. Their names are $leftName and $rightName")
        case _ => ()
      }
    }

    private def boundaryInfo(using Quotes)(term: quotes.reflect.Term): Option[BoundaryInfo] = {
      import quotes.reflect.*

      val underlying = term.underlyingArgument
      if (underlying ne term) return boundaryInfo(underlying)

      def stringArg(args: List[Term], default: String): String =
        args.collectFirst { case quotes.reflect.Literal(StringConstant(value)) => value }.getOrElse(default)

      def singleInfo(kind: SegmentInfoKind, name: String): BoundaryInfo = {
        val info = SegmentInfo(kind, name)
        BoundaryInfo(Some(info), Some(info))
      }

      def applyInfo(name: String, args: List[Term]): Option[BoundaryInfo] = name match {
        case "literal" | "Literal"  => Some(singleInfo(SegmentInfoKind.Literal, stringArg(args, "literal")))
        case "bool" | "BoolSeg"     => Some(singleInfo(SegmentInfoKind.Bool, stringArg(args, "bool")))
        case "string" | "StringSeg" => Some(singleInfo(SegmentInfoKind.String, stringArg(args, "string")))
        case "int" | "IntSeg"       => Some(singleInfo(SegmentInfoKind.Int, stringArg(args, "int")))
        case "long" | "LongSeg"     => Some(singleInfo(SegmentInfoKind.Long, stringArg(args, "long")))
        case "uuid" | "UUIDSeg"     => Some(singleInfo(SegmentInfoKind.UUID, stringArg(args, "uuid")))
        case "Trailing"             => Some(singleInfo(SegmentInfoKind.Trailing, "trailing"))
        case "Empty"                => Some(BoundaryInfo(None, None))
        case _                      => None
      }

      def typeInfo(tpe: TypeRepr): Option[BoundaryInfo] = {
        val segmentCodecSym = Symbol.requiredClass("zio.blocks.endpoint.SegmentCodec")
        val base            = tpe.widenTermRefByName.dealias.baseType(segmentCodecSym)

        if base =:= TypeRepr.of[Any] then None
        else {
          val prefixTpe = base.memberType(segmentCodecSym.typeMember("Prefix"))
          val suffixTpe = base.memberType(segmentCodecSym.typeMember("Suffix"))

          def kindOf(boundary: TypeRepr): Option[SegmentInfoKind] =
            if boundary <:< TypeRepr.of[BoundaryTag.Literal] then Some(SegmentInfoKind.Literal)
            else if boundary <:< TypeRepr.of[BoundaryTag.Bool] then Some(SegmentInfoKind.Bool)
            else if boundary <:< TypeRepr.of[BoundaryTag.Int] then Some(SegmentInfoKind.Int)
            else if boundary <:< TypeRepr.of[BoundaryTag.Long] then Some(SegmentInfoKind.Long)
            else if boundary <:< TypeRepr.of[BoundaryTag.String] then Some(SegmentInfoKind.String)
            else if boundary <:< TypeRepr.of[BoundaryTag.UUID] then Some(SegmentInfoKind.UUID)
            else if boundary <:< TypeRepr.of[BoundaryTag.Trailing] then Some(SegmentInfoKind.Trailing)
            else None

          val prefix = kindOf(prefixTpe).map(kind => SegmentInfo(kind, "transformed"))
          val suffix = kindOf(suffixTpe).map(kind => SegmentInfo(kind, "transformed"))
          if (prefix.isEmpty && suffix.isEmpty) None else Some(BoundaryInfo(prefix, suffix))
        }
      }

      def appliedBoundary(fun: Term, args: List[Term]): Option[BoundaryInfo] = {
        val fullName = fun.symbol.fullName
        if (
          fullName == "zio.blocks.endpoint.SegmentCodec.literal" || fullName == "zio.blocks.endpoint.SegmentCodec.string" ||
          fullName == "zio.blocks.endpoint.SegmentCodec.int" || fullName == "zio.blocks.endpoint.SegmentCodec.long" ||
          fullName == "zio.blocks.endpoint.SegmentCodec.bool" || fullName == "zio.blocks.endpoint.SegmentCodec.uuid"
        )
          applyInfo(fun.symbol.name, args)
        else if (
          args.nonEmpty &&
          (fun.symbol.owner.name == "Transform" || fullName.contains("SegmentCodec.Transform"))
        )
          boundaryInfo(args.head)
        else if (
          (fun.symbol.name == "transform" || fun.symbol.name == "transformOrFail" ||
            fun.symbol.name == "transformValidated" ||
            fun.symbol.name == "transform$extension" || fun.symbol.name == "transformOrFail$extension") &&
          args.nonEmpty
        )
          boundaryInfo(args.head)
        else if (
          fullName.endsWith("SegmentCodec.Literal.apply") || fullName.endsWith("SegmentCodec.StringSeg.apply") ||
          fullName.endsWith("SegmentCodec.IntSeg.apply") || fullName.endsWith("SegmentCodec.LongSeg.apply") ||
          fullName.endsWith("SegmentCodec.BoolSeg.apply") || fullName.endsWith("SegmentCodec.UUIDSeg.apply")
        )
          applyInfo(fun.symbol.owner.name, args)
        else if (fullName.endsWith("SegmentCodec.Transform.apply"))
          args.headOption.flatMap(boundaryInfo)
        else None
      }

      term match {
        case Inlined(_, _, inner) => boundaryInfo(inner)
        case Typed(inner, _)      => boundaryInfo(inner)
        case Block(_, expr)       => boundaryInfo(expr)
        case Ident(_)             =>
          term.symbol.tree match {
            case valDef: ValDef => valDef.rhs.flatMap(boundaryInfo)
            case _              => None
          }
        case Apply(TypeApply(Select(_, "combineValidated"), _), List(left, right, _)) =>
          for {
            leftInfo  <- boundaryInfo(left)
            rightInfo <- boundaryInfo(right)
          } yield leftInfo ++ rightInfo
        case Apply(TypeApply(Select(_, "transformValidated"), _), codec :: _) =>
          boundaryInfo(codec)
        case Apply(Select(_, "transformValidated"), codec :: _) =>
          boundaryInfo(codec)
        case Apply(TypeApply(fun, _), args) =>
          appliedBoundary(fun, args)
        case Apply(Select(_, "combineValidated"), List(left, right, _)) =>
          for {
            leftInfo  <- boundaryInfo(left)
            rightInfo <- boundaryInfo(right)
          } yield leftInfo ++ rightInfo
        case Apply(Apply(TypeApply(Select(_, "transform"), _), List(left)), _) =>
          boundaryInfo(left)
        case Apply(Apply(Select(_, "transform"), List(left)), _) =>
          boundaryInfo(left)
        case Apply(TypeApply(Select(left, "transform"), _), _) =>
          boundaryInfo(left)
        case Apply(Select(left, "transform"), _) =>
          boundaryInfo(left)
        case Apply(Apply(TypeApply(Select(_, "transformOrFail"), _), List(left)), _) =>
          boundaryInfo(left)
        case Apply(Apply(Select(_, "transformOrFail"), List(left)), _) =>
          boundaryInfo(left)
        case Apply(TypeApply(Select(left, "transformOrFail"), _), _) =>
          boundaryInfo(left)
        case Apply(Select(left, "transformOrFail"), _) =>
          boundaryInfo(left)
        case Apply(fun, args) =>
          appliedBoundary(fun, args)
        case Select(_, "Empty") => Some(BoundaryInfo(None, None))
        case _                  => typeInfo(term.tpe)
      }
    }

    private def validateLiteralValue(value: String)(using Quotes): Unit = {
      import quotes.reflect.*

      val path          = Path(s"/$value")
      val singleSegment = path.segments.length == 1 && path.segments.headOption.contains(value)
      val preserved     = path.encode.stripPrefix("/") == value

      if value.isEmpty then report.errorAndAbort("SegmentCodec.literal cannot be empty")
      else if !singleSegment || !preserved then
        report.errorAndAbort(
          s"SegmentCodec.literal must be a valid single path segment without `/` or characters that require URL encoding: $value"
        )
    }
  }
}
