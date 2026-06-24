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
   * by flag name.
   */
  val registry: ConcurrentHashMap[String, Any] = new ConcurrentHashMap[String, Any]()

  def dump(): String = {
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

  def nearMissWarnings(flagName: String): List[String] = {
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

  object Reader {

    /**
     * Marker subclass for scalar types (types whose string representation
     * contains no commas). Used to enable comma-separated list parsing.
     */
    trait Scalar[A] extends Reader[A]

    def apply[A](parseFunc: (String, String) => Either[ConfigError, A], typeNameStr: String): Reader[A] =
      new Reader[A] {
        def parse(flagName: String, raw: String): Either[ConfigError, A] = parseFunc(flagName, raw)
        def typeName: String                                             = typeNameStr
      }

    def scalar[A](parseFunc: (String, String) => Either[ConfigError, A], typeNameStr: String): Scalar[A] =
      new Scalar[A] {
        def parse(flagName: String, raw: String): Either[ConfigError, A] = parseFunc(flagName, raw)
        def typeName: String                                             = typeNameStr
      }

    implicit val intReader: Scalar[Int] = scalar(
      (flagName, raw) =>
        Try(raw.toInt).toEither.left.map(e => ConfigError.InvalidValue(flagName, raw, "Int", "flag", Some(e))),
      "Int"
    )

    implicit val longReader: Scalar[Long] = scalar(
      (flagName, raw) =>
        Try(raw.toLong).toEither.left.map(e => ConfigError.InvalidValue(flagName, raw, "Long", "flag", Some(e))),
      "Long"
    )

    implicit val doubleReader: Scalar[Double] = scalar(
      (flagName, raw) =>
        Try(raw.toDouble).toEither.left.map(e => ConfigError.InvalidValue(flagName, raw, "Double", "flag", Some(e))),
      "Double"
    )

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

    implicit val stringReader: Scalar[String] = scalar(
      (_, raw) => Right(raw),
      "String"
    )

    implicit val secretReader: Reader[Secret] =
      new Reader[Secret] {
        def parse(flagName: String, raw: String): Either[ConfigError, Secret] =
          Right(Secret(raw))

        def typeName: String = "Secret"
      }

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

object Flag extends Flag {

  /**
   * Identifies where a flag's value was resolved from.
   */
  sealed trait Source

  object Source {
    case object SystemProperty                         extends Source
    case object EnvironmentVariable                    extends Source
    final case class FlagSourceValue(sourceId: String) extends Source
    case object Default                                extends Source
  }

  /**
   * Result of reloading a dynamic flag's expression from its FlagSource.
   */
  sealed trait ReloadResult

  object ReloadResult {
    case object Unchanged                                                  extends ReloadResult
    final case class Updated(oldExpression: String, newExpression: String) extends ReloadResult
    case object NoSource                                                   extends ReloadResult
    final case class Failed(error: ConfigError)                            extends ReloadResult
  }
}
