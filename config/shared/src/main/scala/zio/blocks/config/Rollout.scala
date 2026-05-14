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

package zio.blocks.config

import scala.util.hashing.MurmurHash3

/**
 * Rollout DSL for selecting values based on path-matching and percentage
 * bucketing.
 *
 * Grammar:
 * {{{
 *   expression = choice { ";" choice }
 *   choice     = value "@" selector | value
 *   selector   = segment { "/" segment } [ percentage ]
 *   segment    = "*" | literal
 *   percentage = digits "%"
 * }}}
 *
 * Left-to-right evaluation, first match wins. A bare value (no `@`) is a
 * catch-all.
 */
object Rollout {

  final case class Choices(entries: List[Choice])

  sealed trait Choice

  object Choice {
    final case class Targeted(value: String, selector: Selector) extends Choice
    final case class CatchAll(value: String)                     extends Choice
  }

  final case class Selector(segments: List[Segment], percentage: Option[Int])

  sealed trait Segment

  object Segment {
    case object Wildcard                    extends Segment
    final case class Literal(value: String) extends Segment
  }

  def select(expression: String, path: String, bucket: Int): Option[String] =
    parseChoices(expression) match {
      case Right(choices) => evaluateIndex(choices, path, bucket)
      case Left(_)        => None
    }

  def parseChoices(expression: String): Either[ConfigError, Choices] = {
    val trimmed = expression.trim
    if (trimmed.isEmpty)
      return Left(ConfigError.InvalidValue("rollout", expression, "non-empty rollout expression", "rollout"))

    val rawParts = trimmed.split(";").map(_.trim).toList
    val parsed   = rawParts.map(parseChoice)
    val errors   = parsed.collect { case Left(e) => e }
    if (errors.nonEmpty) Left(errors.head)
    else Right(Choices(parsed.collect { case Right(c) => c }))
  }

  def evaluateIndex(choices: Choices, path: String, bucket: Int): Option[String] = {
    val pathParts = if (path.isEmpty) Nil else path.split("/").toList

    choices.entries.iterator.collectFirst {
      case Choice.CatchAll(value)                                                   => value
      case Choice.Targeted(value, selector) if matches(selector, pathParts, bucket) => value
    }
  }

  def bucketFor(key: String): Int =
    math.abs(MurmurHash3.stringHash(key) % 100)

  def validate(expression: String): Either[ConfigError, List[String]] =
    parseChoices(expression).map { choices =>
      val warnings = List.newBuilder[String]

      val catchAllIdx = choices.entries.indexWhere(_.isInstanceOf[Choice.CatchAll])
      if (catchAllIdx >= 0 && catchAllIdx < choices.entries.size - 1)
        warnings += s"Choices after index $catchAllIdx are unreachable (catch-all found)"

      val targeted  = choices.entries.collect { case t: Choice.Targeted => t }
      val byPattern = targeted.groupBy(t => segmentsToString(t.selector.segments))
      byPattern.foreach { case (pattern, ts) =>
        val total = ts.flatMap(_.selector.percentage).sum
        if (total > 100)
          warnings += s"Cumulative percentage for pattern '$pattern' is $total% (exceeds 100%)"
      }

      warnings.result()
    }

  private def parseChoice(raw: String): Either[ConfigError, Choice] = {
    val atIdx = raw.indexOf('@')
    if (atIdx < 0) {
      val value = raw.trim
      if (value.isEmpty)
        Left(ConfigError.InvalidValue("rollout", raw, "non-empty choice value", "rollout"))
      else
        Right(Choice.CatchAll(value))
    } else {
      val value       = raw.substring(0, atIdx).trim
      val selectorStr = raw.substring(atIdx + 1).trim

      if (value.isEmpty)
        return Left(ConfigError.InvalidValue("rollout", raw, "non-empty choice value before '@'", "rollout"))
      if (selectorStr.isEmpty)
        return Left(ConfigError.InvalidValue("rollout", raw, "non-empty selector after '@'", "rollout"))

      parseSelector(selectorStr).map(sel => Choice.Targeted(value, sel))
    }
  }

  private val percentPattern = """^(.+?)(\d+)%$""".r

  private def parseSelector(raw: String): Either[ConfigError, Selector] =
    raw match {
      case percentPattern(pathPart, pctStr) =>
        val pct = pctStr.toInt
        if (pct > 100)
          Left(ConfigError.InvalidValue("rollout", raw, "percentage <= 100", "rollout"))
        else
          Right(Selector(parseSegments(pathPart.trim), Some(pct)))
      case _ =>
        Right(Selector(parseSegments(raw), None))
    }

  private def parseSegments(pathStr: String): List[Segment] =
    pathStr.split("/").map(_.trim).toList.map {
      case "*" => Segment.Wildcard
      case s   => Segment.Literal(s)
    }

  private def matches(selector: Selector, pathParts: List[String], bucket: Int): Boolean = {
    val pathMatch = matchSegments(selector.segments, pathParts)
    if (!pathMatch) false
    else
      selector.percentage match {
        case None      => true
        case Some(0)   => false
        case Some(100) => true
        case Some(pct) => bucket < pct
      }
  }

  private def matchSegments(selectorSegs: List[Segment], pathParts: List[String]): Boolean =
    (selectorSegs, pathParts) match {
      case (Nil, Nil)                                     => true
      case (Nil, _)                                       => false
      case (_, Nil)                                       => false
      case (Segment.Wildcard :: restSel, _ :: restPath)   => matchSegments(restSel, restPath)
      case (Segment.Literal(v) :: restSel, p :: restPath) => v == p && matchSegments(restSel, restPath)
    }

  private def segmentsToString(segments: List[Segment]): String =
    segments.map {
      case Segment.Wildcard   => "*"
      case Segment.Literal(v) => v
    }.mkString("/")
}
