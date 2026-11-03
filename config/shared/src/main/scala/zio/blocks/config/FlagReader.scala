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

import java.util.concurrent.ConcurrentHashMap

import scala.concurrent.duration.{FiniteDuration, DAYS, HOURS, MINUTES, SECONDS, MILLISECONDS}
import scala.util.Try

/** Global registry and diagnostics helpers for resolved flags. */
trait Flag {

  /**
   * Global registry of all resolved flag instances (static and dynamic), keyed
   * by flag name. Delegates to the authoritative store in [[Flag.registry]] so
   * that arbitrary `Flag` mix-ins cannot receive an independent map.
   */
  def registry: ConcurrentHashMap[String, Any] = Flag.registry

  /**
   * Returns a table of every registered flag, sorted by name, with the columns
   * Name, Type, Value and Source, or `(no flags registered)` when there is
   * none. For a [[StaticFlag]], Type is the flag's `Flag.Source`, Value is
   * `displayValue` and Source is the id of its provenance. For a
   * [[DynamicFlag]], Type is `DynamicFlag`, Value is its expression and Source
   * is `dynamic`. A flag object appears once it has initialized.
   */
  def dump(): String = Flag.dump()

  /**
   * Returns a warning for each system property whose name equals `flagName`
   * ignoring case but not exactly, for each environment variable whose name
   * equals the flag's environment name (dots replaced by underscores, upper
   * case) ignoring case but not exactly, and for each key of a registered
   * [[FlagSource]] that spells `flagName` in all lower or all upper case, or
   * with `.` and `_` swapped. The list is empty when none does.
   */
  def nearMissWarnings(flagName: String): List[String] = Flag.nearMissWarnings(flagName)

  /**
   * Parses the string value of a flag into an `A`. A [[StaticFlag]] and a
   * [[DynamicFlag]] need an implicit one; the `Flag.Reader` companion provides
   * readers for `Int`, `Long`, `Double`, `Boolean`, `String`, `Secret`,
   * `FiniteDuration` and, for a `Flag.Reader.Scalar` element, `Seq`.
   */
  trait Reader[A] {

    /**
     * Parse a flag value from a string.
     *
     * @param flagName
     *   the name of the flag (for error messages)
     * @param raw
     *   the raw string value
     * @return
     *   Either a parsed value or a ConfigError
     */
    def parse(flagName: String, raw: String): Either[ConfigError, A]

    /**
     * Human-readable type name for error messages.
     */
    def typeName: String
  }

  /**
   * Constructors and instances for `Flag.Reader`.
   */
  object Reader {

    /**
     * A reader whose values `seqReader` may split on commas: `seqReader`
     * requires one. A value that itself contains a comma, such as a `String`
     * with a comma, cannot be an element of such a sequence.
     */
    trait Scalar[A] extends Reader[A]

    /**
     * Returns a reader that parses with `parseFunc`, which receives the flag
     * name and the raw value, and names its type `typeNameStr` in error
     * messages.
     */
    def apply[A](parseFunc: (String, String) => Either[ConfigError, A], typeNameStr: String): Reader[A] =
      new Reader[A] {
        def parse(flagName: String, raw: String): Either[ConfigError, A] = parseFunc(flagName, raw)
        def typeName: String                                             = typeNameStr
      }

    /**
     * Like `apply`, but returns a `Flag.Reader.Scalar`, so that `seqReader` can
     * use it for the elements of a `Seq`.
     */
    def scalar[A](parseFunc: (String, String) => Either[ConfigError, A], typeNameStr: String): Scalar[A] =
      new Scalar[A] {
        def parse(flagName: String, raw: String): Either[ConfigError, A] = parseFunc(flagName, raw)
        def typeName: String                                             = typeNameStr
      }

    /**
     * Parses an `Int` with `String#toInt`, so the value is not trimmed.
     */
    implicit val intReader: Scalar[Int] = scalar(
      (flagName, raw) =>
        Try(raw.toInt).toEither.left.map(e => ConfigError.InvalidValue(flagName, raw, "Int", "flag", Some(e))),
      "Int"
    )

    /**
     * Parses a `Long` with `String#toLong`, so the value is not trimmed.
     */
    implicit val longReader: Scalar[Long] = scalar(
      (flagName, raw) =>
        Try(raw.toLong).toEither.left.map(e => ConfigError.InvalidValue(flagName, raw, "Long", "flag", Some(e))),
      "Long"
    )

    /**
     * Parses a `Double` with `String#toDouble`.
     */
    implicit val doubleReader: Scalar[Double] = scalar(
      (flagName, raw) =>
        Try(raw.toDouble).toEither.left.map(e => ConfigError.InvalidValue(flagName, raw, "Double", "flag", Some(e))),
      "Double"
    )

    /**
     * Parses a `Boolean`: `true`, `1`, `yes` and `on` are true, and `false`,
     * `0`, `no` and `off` are false, in any letter case. Any other value,
     * including one with surrounding spaces, is an error.
     */
    implicit val booleanReader: Scalar[Boolean] = scalar(
      (flagName, raw) => {
        val lower = raw.toLowerCase
        if (lower == "true" || lower == "1" || lower == "yes" || lower == "on") Right(true)
        else if (lower == "false" || lower == "0" || lower == "no" || lower == "off") Right(false)
        else
          Left(ConfigError.InvalidValue(flagName, raw, "Boolean (true/false/1/0/yes/no/on/off)", "flag"))
      },
      "Boolean"
    )

    /**
     * Returns the raw value unchanged.
     */
    implicit val stringReader: Scalar[String] = scalar(
      (_, raw) => Right(raw),
      "String"
    )

    /**
     * Wraps the raw value in a [[Secret]]. It is not a `Flag.Reader.Scalar`, so
     * there is no `Seq[Secret]` reader.
     */
    implicit val secretReader: Reader[Secret] =
      new Reader[Secret] {
        def parse(flagName: String, raw: String): Either[ConfigError, Secret] =
          Right(Secret(raw))

        def typeName: String = "Secret"
      }

    /**
     * Parses a comma-separated list: it splits the value on commas, trims each
     * part and parses it with `reader`. An empty value gives an empty `Seq`, an
     * empty part between two commas is parsed like any other part, and the
     * first part that fails gives the error. A trailing comma with nothing
     * after it, not even a space, adds no element.
     */
    implicit def seqReader[A](implicit reader: Scalar[A]): Reader[Seq[A]] =
      Reader(
        (flagName, raw) => {
          if (raw.isEmpty) Right(Seq.empty)
          else {
            val parts   = raw.split(",").map(_.trim).toSeq
            val results = parts.map(part => reader.parse(flagName, part))
            val errors  = results.collect { case Left(e) => e }
            if (errors.nonEmpty) Left(errors.head)
            else Right(results.collect { case Right(v) => v })
          }
        },
        s"Seq[${reader.typeName}]"
      )

    /**
     * Parses a duration written as digits followed by a unit: `ms`, `millis` or
     * `milliseconds`; `s`, `sec`, `secs` or `seconds`; `m`, `min`, `mins` or
     * `minutes`; `h`, `hour` or `hours`; `d`, `day` or `days`. The unit is
     * case-insensitive, and no space or fraction is allowed, so `10s` parses
     * and `10 s` and `1.5s` do not.
     */
    implicit val durationReader: Scalar[FiniteDuration] = scalar(
      (flagName, raw) => {
        val pattern = """^(\d+)([a-zA-Z]+)$""".r
        raw match {
          case pattern(numStr, unit) =>
            Try {
              val num = numStr.toLong
              unit.toLowerCase match {
                case "ms" | "millis" | "milliseconds" => new FiniteDuration(num, MILLISECONDS)
                case "s" | "sec" | "secs" | "seconds" => new FiniteDuration(num, SECONDS)
                case "m" | "min" | "mins" | "minutes" => new FiniteDuration(num, MINUTES)
                case "h" | "hour" | "hours"           => new FiniteDuration(num, HOURS)
                case "d" | "day" | "days"             => new FiniteDuration(num, DAYS)
                case _                                =>
                  throw new IllegalArgumentException(s"Unknown time unit: $unit. Supported: ms, s, m, h, d")
              }
            }.toEither.left.map(e =>
              ConfigError.InvalidValue(flagName, raw, "Duration (e.g., 10s, 5m, 1h, 100ms, 2d)", "flag", Some(e))
            )
          case _ =>
            Left(ConfigError.InvalidValue(flagName, raw, "Duration (e.g., 10s, 5m, 1h, 100ms, 2d)", "flag"))
        }
      },
      "FiniteDuration"
    )
  }
}

/**
 * The global [[Flag]]: its `registry`, `dump` and `nearMissWarnings`, and the
 * `Flag.Reader`, `Flag.Source` and `Flag.ReloadResult` types.
 */
object Flag extends Flag {

  /**
   * Authoritative global registry of all resolved flag instances (static and
   * dynamic), keyed by flag name. Stored only here; [[Flag]] trait delegates to
   * this instance so no separate map can exist.
   */
  override val registry: ConcurrentHashMap[String, Any] = new ConcurrentHashMap[String, Any]()

  override def dump(): String = {
    import scala.jdk.CollectionConverters._
    val entries = registry.entrySet().asScala.toList.sortBy(_.getKey)
    if (entries.isEmpty) return "(no flags registered)"

    val rows: List[(String, String, String, String)] = entries.map { entry =>
      val name = entry.getKey
      val flag = entry.getValue
      flag match {
        case sf: StaticFlag[_] =>
          (name, sf.source.toString, sf.displayValue, sf.provenance.sourceId)
        case df: DynamicFlag[_] =>
          (name, "DynamicFlag", df.expression, "dynamic")
        case other =>
          (name, "Unknown", other.toString, "unknown")
      }
    }

    val nameWidth  = math.max("Name".length, rows.map(_._1.length).max)
    val typeWidth  = math.max("Type".length, rows.map(_._2.length).max)
    val valueWidth = math.max("Value".length, rows.map(_._3.length).max)
    val srcWidth   = math.max("Source".length, rows.map(_._4.length).max)

    val sb = new StringBuilder
    sb.append("\u250c")
    sb.append("\u2500" * (nameWidth + 2))
    sb.append("\u252c")
    sb.append("\u2500" * (typeWidth + 2))
    sb.append("\u252c")
    sb.append("\u2500" * (valueWidth + 2))
    sb.append("\u252c")
    sb.append("\u2500" * (srcWidth + 2))
    sb.append("\u2510\n")

    sb.append("\u2502 ")
    sb.append("Name".padTo(nameWidth, ' '))
    sb.append(" \u2502 ")
    sb.append("Type".padTo(typeWidth, ' '))
    sb.append(" \u2502 ")
    sb.append("Value".padTo(valueWidth, ' '))
    sb.append(" \u2502 ")
    sb.append("Source".padTo(srcWidth, ' '))
    sb.append(" \u2502\n")

    sb.append("\u251c")
    sb.append("\u2500" * (nameWidth + 2))
    sb.append("\u253c")
    sb.append("\u2500" * (typeWidth + 2))
    sb.append("\u253c")
    sb.append("\u2500" * (valueWidth + 2))
    sb.append("\u253c")
    sb.append("\u2500" * (srcWidth + 2))
    sb.append("\u2524\n")

    rows.foreach { case (name, tpe, value, src) =>
      sb.append("\u2502 ")
      sb.append(name.padTo(nameWidth, ' '))
      sb.append(" \u2502 ")
      sb.append(tpe.padTo(typeWidth, ' '))
      sb.append(" \u2502 ")
      sb.append(value.padTo(valueWidth, ' '))
      sb.append(" \u2502 ")
      sb.append(src.padTo(srcWidth, ' '))
      sb.append(" \u2502\n")
    }

    sb.append("\u2514")
    sb.append("\u2500" * (nameWidth + 2))
    sb.append("\u2534")
    sb.append("\u2500" * (typeWidth + 2))
    sb.append("\u2534")
    sb.append("\u2500" * (valueWidth + 2))
    sb.append("\u2534")
    sb.append("\u2500" * (srcWidth + 2))
    sb.append("\u2518")

    sb.toString
  }

  override def nearMissWarnings(flagName: String): List[String] = {
    val lowerName = flagName.toLowerCase
    val envName   = flagName.replace('.', '_').toUpperCase
    val warnings  = scala.collection.mutable.LinkedHashSet.empty[String]

    val props    = System.getProperties
    val propIter = props.stringPropertyNames().iterator()
    while (propIter.hasNext) {
      val prop = propIter.next()
      if (prop.toLowerCase == lowerName && prop != flagName)
        warnings += s"Near-miss: system property '$prop' looks similar to flag '$flagName' (case mismatch)"
    }

    val envIter = System.getenv().entrySet().iterator()
    while (envIter.hasNext) {
      val envVar = envIter.next().getKey
      if (envVar.equalsIgnoreCase(envName) && envVar != envName)
        warnings += s"Near-miss: environment variable '$envVar' looks similar to flag '$flagName' (case mismatch)"
    }

    val candidates = List(
      flagName,
      lowerName,
      flagName.toUpperCase,
      flagName.replace('.', '_'),
      flagName.replace('.', '_').toLowerCase,
      envName,
      flagName.replace('_', '.'),
      flagName.replace('_', '.').toLowerCase,
      flagName.replace('_', '.').toUpperCase
    ).distinct

    FlagSource.Registry.all.foreach { source =>
      candidates.foreach { candidate =>
        if (candidate != flagName && source.get(candidate).isPresent)
          warnings +=
            s"Near-miss: FlagSource '${source.sourceId}' contains key '$candidate' similar to flag '$flagName'"
      }
    }

    warnings.toList
  }

  /**
   * Identifies where a flag's value was resolved from.
   */
  sealed trait Source

  /**
   * The `Flag.Source` cases.
   */
  object Source {

    /**
     * The value came from the system property with the flag's name.
     */
    case object SystemProperty extends Source

    /**
     * The value came from the environment variable named like the flag, with
     * each dot replaced by an underscore and in upper case.
     */
    case object EnvironmentVariable extends Source

    /**
     * The value came from a source in [[FlagSource.Registry]]; `sourceId` is
     * the id in the value's provenance, which for a source built with `orElse`
     * is the inner source that held the value.
     */
    final case class FlagSourceValue(sourceId: String) extends Source

    /**
     * No source had a value, so the flag kept its default.
     */
    case object Default extends Source
  }

  /**
   * Result of [[DynamicFlag#reload]], which reads the flag's expression from
   * [[FlagSource.Registry]].
   */
  sealed trait ReloadResult

  /**
   * The `Flag.ReloadResult` cases.
   */
  object ReloadResult {

    /**
     * The source's expression, trimmed, equals the current one.
     */
    case object Unchanged extends ReloadResult

    /**
     * The flag now uses `newExpression`, which replaced `oldExpression`.
     */
    final case class Updated(oldExpression: String, newExpression: String) extends ReloadResult

    /**
     * No registered [[FlagSource]] has a value for the flag's name.
     */
    case object NoSource extends ReloadResult

    /**
     * The source's expression did not parse, so the flag keeps its current one;
     * `error` says why.
     */
    final case class Failed(error: ConfigError) extends ReloadResult
  }
}
