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
import zio.blocks.maybe.Maybe

/**
 * The rollout expression language of [[DynamicFlag]]: a list of choices that
 * selects a value for a slash-separated path and a percentage bucket. For
 * example, `on@beta; off` gives `on` to the path `beta` and `off` to every
 * other path.
 *
 * Grammar:
 * {{{
 * // no-check: a grammar, not Scala code
 *   expression = choice { ";" choice }
 *   choice     = value "@" selector | value
 *   selector   = path [ "/" percentage ]
 *   path       = segment { "/" segment }
 *   segment    = "*" | literal
 *   percentage = digits "%"
 * }}}
 *
 * Choices are tried left to right and the first match wins. A bare value (no
 * `@`) is a catch-all and should appear last; later entries are unreachable.
 *
 * A selector matches a path with the same number of segments, where `*` matches
 * any one segment. A percentage `n%` also requires the bucket to be below `n`.
 * Spaces around segments are ignored, so `* / eu / 50%` is the selector `*`,
 * `eu`, `50%`.
 *
 * {{{
 * val bucket  = Rollout.bucketFor("user-42")
 * // 85
 * val forBeta = Rollout.select("on@beta; off", "beta", bucket).toOption
 * // Some(on)
 * val forProd = Rollout.select("on@beta; off", "prod", bucket).toOption
 * // Some(off)
 * }}}
 *
 * @see
 *   [[https://zioblocks.com/reference/config/rollout/ Rollout]]
 */
object Rollout {

  /** A parsed expression: its choices in order. */
  final case class Choices(entries: List[Choice])

  /** One choice of an expression. */
  sealed trait Choice

  /** The [[Choice]] cases. */
  object Choice {

    /**
     * `value@selector`: `value` for the paths and buckets `selector` matches.
     */
    final case class Targeted(value: String, selector: Selector) extends Choice

    /** A bare `value`, which matches every path and bucket. */
    final case class CatchAll(value: String) extends Choice
  }

  /**
   * The path pattern of a targeted choice and its percentage, if it has one.
   */
  final case class Selector(segments: List[Segment], percentage: Maybe[Int])

  /** One segment of a selector's path pattern. */
  sealed trait Segment

  /** The [[Segment]] cases. */
  object Segment {

    /** `*`, which matches any one path segment. */
    case object Wildcard extends Segment

    /** A segment that matches only a path segment equal to `value`. */
    final case class Literal(value: String) extends Segment
  }

  /**
   * Parses `expression` and evaluates it for `path` and `bucket`. Returns
   * `Maybe.absent` when no choice matches, and also when the expression does
   * not parse.
   */
  def select(expression: String, path: String, bucket: Int): Maybe[String] =
    parseChoices(expression) match {
      case Right(choices) => evaluateIndex(choices, path, bucket)
      case Left(_)        => Maybe.absent
    }

  /**
   * Parses `expression`, or returns the first error as a
   * [[ConfigError.InvalidValue]] whose key and source are `rollout`. An empty
   * expression, an empty choice (as in `a;;b`), a selector that is only a
   * percentage, and a percentage above 100 are errors. A selector that does not
   * end in `%` is a path, so `true@x/50` is the two-segment path `x`, `50`.
   *
   * {{{
   * val parsed = Rollout.parseChoices("v2@canary; v1").map(_.entries.map {
   *   case Rollout.Choice.Targeted(value, selector) => value + " for " + selector.segments.mkString("/")
   *   case Rollout.Choice.CatchAll(value)           => value + " otherwise"
   * })
   * // Right(List(v2 for Literal(canary), v1 otherwise))
   * val failed = Rollout.parseChoices("v2@50%").left.map(_.message)
   * // Left(Invalid value '50%' for key 'rollout' (expected selector path before /NN%) in source 'rollout')
   * }}}
   */
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

  /**
   * Returns the value of the first choice that matches `path` (split on `/`)
   * and `bucket`, or `Maybe.absent`. A percentage of 0% never matches, 100%
   * always does, and `n%` matches when `bucket` is below `n`.
   */
  def evaluateIndex(choices: Choices, path: String, bucket: Int): Maybe[String] = {
    val pathParts = if (path.isEmpty) Array.empty[String] else path.split("/")
    val entries   = choices.entries
    var idx       = 0
    while (idx < entries.size) {
      entries(idx) match {
        case Choice.CatchAll(value)                                                          => return Maybe.present(value)
        case Choice.Targeted(value, selector) if matches(selector, pathParts.toList, bucket) =>
          return Maybe.present(value)
        case _ =>
      }
      idx += 1
    }
    Maybe.absent
  }

  /**
   * Returns the bucket of `key`, from 0 to 99, computed from the key's
   * MurmurHash3 hash. A key always gets the same bucket, whatever the flag, so
   * percentage rollouts of different flags pick the same keys, a known bug.
   */
  def bucketFor(key: String): Int =
    math.abs(MurmurHash3.stringHash(key) % 100)

  /**
   * Parses `expression` and returns warnings: choices after a catch-all, and
   * selector patterns whose percentages add up to more than 100%. Evaluation
   * treats each percentage as a threshold on the same bucket, not as a share,
   * so the second warning fires on a valid split, a known bug.
   *
   * {{{
   * val warnings = Rollout.validate("v3; v2@canary")
   * // Right(List(Choices after index 0 are unreachable (catch-all found)))
   * }}}
   */
  def validate(expression: String): Either[ConfigError, List[String]] =
    parseChoices(expression).map { choices =>
      val warnings = List.newBuilder[String]

      val catchAllIdx = choices.entries.indexWhere(_.isInstanceOf[Choice.CatchAll])
      if (catchAllIdx >= 0 && catchAllIdx < choices.entries.size - 1)
        warnings += s"Choices after index $catchAllIdx are unreachable (catch-all found)"

      val targeted  = choices.entries.collect { case t: Choice.Targeted => t }
      val byPattern = targeted.groupBy(t => segmentsToString(t.selector.segments))
      byPattern.foreach { case (pattern, ts) =>
        val total = ts.foldLeft(0)((sum, targeted) => sum + targeted.selector.percentage.getOrElse(0))
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

  private val percentOnlyPattern = """^\d+%$""".r

  private def parseSelector(raw: String): Either[ConfigError, Selector] = {
    val trimmed = raw.trim
    if (trimmed.isEmpty)
      Left(ConfigError.InvalidValue("rollout", raw, "non-empty selector after '@'", "rollout"))
    else
      trimmed match {
        case percentOnlyPattern() =>
          Left(ConfigError.InvalidValue("rollout", raw, "selector path before /NN%", "rollout"))
        case _ if trimmed.endsWith("%") =>
          val slashIdx = trimmed.lastIndexOf('/')
          if (slashIdx <= 0 || slashIdx == trimmed.length - 1)
            Left(ConfigError.InvalidValue("rollout", raw, "selector path using /NN% percentage syntax", "rollout"))
          else {
            val pathPart = trimmed.substring(0, slashIdx).trim
            val pctPart  = trimmed.substring(slashIdx + 1).trim
            if (pathPart.isEmpty || !pctPart.endsWith("%"))
              Left(ConfigError.InvalidValue("rollout", raw, "selector path using /NN% percentage syntax", "rollout"))
            else {
              val digits = pctPart.dropRight(1)
              if (!digits.nonEmpty || !digits.forall(_.isDigit))
                Left(ConfigError.InvalidValue("rollout", raw, "selector path using /NN% percentage syntax", "rollout"))
              else {
                val pct = digits.toInt
                if (pct > 100)
                  Left(ConfigError.InvalidValue("rollout", raw, "percentage <= 100", "rollout"))
                else
                  Right(Selector(parseSegments(pathPart), Maybe.present(pct)))
              }
            }
          }
        case _ =>
          Right(Selector(parseSegments(trimmed), Maybe.absent))
      }
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
        case pct if pct.isAbsent      => true
        case pct if pct.contains(0)   => false
        case pct if pct.contains(100) => true
        case pct                      => bucket < pct.get
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
