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
  def doc: Doc
  def examples: Chunk[(String, A)]

  final def format(value: A): Path =
    Path(s"/${SegmentCodec.formatSegment(self, value)}")

  final def render(prefix: String = "{", suffix: String = "}"): String =
    SegmentCodec.render(self, prefix, suffix)
}

object SegmentCodec {

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

  extension [A](self: SegmentCodec[A]) {
    inline def ~[B, C](that: SegmentCodec[B])(using
      combiner: Tuples.Tuples.WithOut[A, B, C]
    ): SegmentCodec[C] =
      ${ SegmentCodecMacros.combineImpl[A, B, C]('self, 'that, 'combiner) }

    inline def ~[C](that: String)(using combiner: Tuples.Tuples.WithOut[A, Unit, C]): SegmentCodec[C] =
      ${ SegmentCodecMacros.combineImpl[A, Unit, C]('self, '{ literal(that) }, 'combiner) }
  }

  def literal(value: String): Literal = Literal(value)
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
      case _ =>
        validateAmbiguousChain(flatten(left) ++ flatten(right))
        Combined(left, right, combiner)
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
    val doc: Doc                        = Doc.empty
    val examples: Chunk[(String, Unit)] = Chunk.empty
  }

  final case class Literal(value: String, doc: Doc = Doc.empty, examples: Chunk[(String, Unit)] = Chunk.empty)
      extends SegmentCodec[Unit]

  final case class BoolSeg(name: String, doc: Doc = Doc.empty, examples: Chunk[(String, Boolean)] = Chunk.empty)
      extends SegmentCodec[Boolean]

  final case class IntSeg(name: String, doc: Doc = Doc.empty, examples: Chunk[(String, Int)] = Chunk.empty)
      extends SegmentCodec[Int]

  final case class LongSeg(name: String, doc: Doc = Doc.empty, examples: Chunk[(String, Long)] = Chunk.empty)
      extends SegmentCodec[Long]

  final case class StringSeg(name: String, doc: Doc = Doc.empty, examples: Chunk[(String, String)] = Chunk.empty)
      extends SegmentCodec[String]

  final case class UUIDSeg(
    name: String,
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
      case Empty                       => ""
      case Literal(value, _, _)        => value
      case BoolSeg(_, _, _)            => value.toString
      case IntSeg(_, _, _)             => value.toString
      case LongSeg(_, _, _)            => value.toString
      case StringSeg(_, _, _)          => value.asInstanceOf[String]
      case UUIDSeg(_, _, _)            => value.toString
      case Trailing                    => value.asInstanceOf[Path].render.stripPrefix("/")
      case combined: Combined[?, ?, ?] =>
        val typed                   = combined.combiner.asInstanceOf[Tuples.Tuples.WithOut[Any, Any, Any]]
        val (leftValue, rightValue) = typed.separate(value)
        formatSegment(combined.left, leftValue) + formatSegment(combined.right, rightValue)
    }

  def flatten(codec: SegmentCodec[_]): Chunk[SegmentCodec[_]] =
    codec match {
      case Combined(left, right, _) => flatten(left) ++ flatten(right)
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

  private def validateAmbiguousChain(codecs: Chunk[SegmentCodec[_]]): Unit = {
    var firstString: Option[String]  = None
    var firstNumeric: Option[String] = None

    codecs.foreach {
      case StringSeg(name, _, _) =>
        firstString match {
          case Some(existing) =>
            throw new IllegalArgumentException(
              s"Cannot combine two string segments. Their names are $existing and $name"
            )
          case None =>
            firstString = Some(name)
        }
      case IntSeg(name, _, _) =>
        firstNumeric match {
          case Some(existing) =>
            throw new IllegalArgumentException(
              s"Cannot combine two numeric segments. Their names are $existing and $name"
            )
          case None =>
            firstNumeric = Some(name)
        }
      case LongSeg(name, _, _) =>
        firstNumeric match {
          case Some(existing) =>
            throw new IllegalArgumentException(
              s"Cannot combine two numeric segments. Their names are $existing and $name"
            )
          case None =>
            firstNumeric = Some(name)
        }
      case _ => ()
    }
  }

  private object SegmentCodecMacros {
    private sealed trait SegmentInfoKind
    private object SegmentInfoKind {
      case object String  extends SegmentInfoKind
      case object Numeric extends SegmentInfoKind
    }

    private final case class SegmentInfo(kind: SegmentInfoKind, name: String)

    def combineImpl[A: Type, B: Type, C: Type](
      leftExpr: Expr[SegmentCodec[A]],
      rightExpr: Expr[SegmentCodec[B]],
      combinerExpr: Expr[Tuples.Tuples.WithOut[A, B, C]]
    )(using Quotes): Expr[SegmentCodec[C]] = {
      import quotes.reflect.*

      val leftInfos  = segmentInfos(leftExpr.asTerm)
      val rightInfos = segmentInfos(rightExpr.asTerm)

      (leftInfos, rightInfos) match {
        case (Some(left), Some(right)) =>
          validateInfos(left ++ right)
          '{ SegmentCodec.combineValidated($leftExpr, $rightExpr, $combinerExpr) }
        case _ =>
          '{ SegmentCodec.combineValidated($leftExpr, $rightExpr, $combinerExpr) }
      }
    }

    private def validateInfos(using Quotes)(infos: List[SegmentInfo]): Unit = {
      import quotes.reflect.*

      var firstString: Option[String]  = None
      var firstNumeric: Option[String] = None

      infos.foreach {
        case SegmentInfo(SegmentInfoKind.String, name) =>
          firstString match {
            case Some(existing) =>
              report.errorAndAbort(s"Cannot combine two string segments. Their names are $existing and $name")
            case None =>
              firstString = Some(name)
          }
        case SegmentInfo(SegmentInfoKind.Numeric, name) =>
          firstNumeric match {
            case Some(existing) =>
              report.errorAndAbort(s"Cannot combine two numeric segments. Their names are $existing and $name")
            case None =>
              firstNumeric = Some(name)
          }
      }
    }

    private def segmentInfos(using Quotes)(term: quotes.reflect.Term): Option[List[SegmentInfo]] = {
      import quotes.reflect.*

      def stringArg(args: List[Term], default: String): String =
        args.collectFirst { case quotes.reflect.Literal(StringConstant(value)) => value }.getOrElse(default)

      def applyInfos(name: String, args: List[Term]): Option[List[SegmentInfo]] = name match {
        case "string" | "StringSeg"                                                    => Some(List(SegmentInfo(SegmentInfoKind.String, stringArg(args, "string"))))
        case "int" | "IntSeg"                                                          => Some(List(SegmentInfo(SegmentInfoKind.Numeric, stringArg(args, "int"))))
        case "long" | "LongSeg"                                                        => Some(List(SegmentInfo(SegmentInfoKind.Numeric, stringArg(args, "long"))))
        case "literal" | "Literal" | "bool" | "BoolSeg" | "uuid" | "UUIDSeg" | "Empty" => Some(Nil)
        case _                                                                         => None
      }

      term match {
        case Inlined(_, _, inner) => segmentInfos(inner)
        case Typed(inner, _)      => segmentInfos(inner)
        case Block(_, expr)       => segmentInfos(expr)
        case Ident(_)             =>
          term.symbol.tree match {
            case valDef: ValDef => valDef.rhs.flatMap(segmentInfos)
            case _              => None
          }
        case Apply(TypeApply(Select(_, "combineValidated"), _), List(left, right, _)) =>
          for {
            leftInfos  <- segmentInfos(left)
            rightInfos <- segmentInfos(right)
          } yield leftInfos ++ rightInfos
        case Apply(Select(_, "combineValidated"), List(left, right, _)) =>
          for {
            leftInfos  <- segmentInfos(left)
            rightInfos <- segmentInfos(right)
          } yield leftInfos ++ rightInfos
        case Apply(fun, args) =>
          val fullName = fun.symbol.fullName
          if (
            fullName == "zio.blocks.endpoint.SegmentCodec.literal" || fullName == "zio.blocks.endpoint.SegmentCodec.string" ||
            fullName == "zio.blocks.endpoint.SegmentCodec.int" || fullName == "zio.blocks.endpoint.SegmentCodec.long" ||
            fullName == "zio.blocks.endpoint.SegmentCodec.bool" || fullName == "zio.blocks.endpoint.SegmentCodec.uuid"
          )
            applyInfos(fun.symbol.name, args)
          else if (
            fullName.endsWith("SegmentCodec.Literal.apply") || fullName.endsWith("SegmentCodec.StringSeg.apply") ||
            fullName.endsWith("SegmentCodec.IntSeg.apply") || fullName.endsWith("SegmentCodec.LongSeg.apply") ||
            fullName.endsWith("SegmentCodec.BoolSeg.apply") || fullName.endsWith("SegmentCodec.UUIDSeg.apply")
          )
            applyInfos(fun.symbol.owner.name, args)
          else None
        case Select(_, "Empty") => Some(Nil)
        case _                  => None
      }
    }
  }
}
