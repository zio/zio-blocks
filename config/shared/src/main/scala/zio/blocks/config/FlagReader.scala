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

/**
 * Typeclass for parsing flag values from strings.
 *
 * @tparam A the type to parse into
 */
trait Flag {

  /**
   * Identifies where a flag's value was resolved from.
   */
  sealed trait Source

  object Source {
    case object SystemProperty                          extends Source
    case object EnvironmentVariable                     extends Source
    final case class FlagProviderSource(providerId: String) extends Source
    case object Default                                 extends Source
  }

  /**
   * Global registry of all resolved StaticFlag instances, keyed by flag name.
   */
  val registry: ConcurrentHashMap[String, Any] = new ConcurrentHashMap[String, Any]()

  trait Reader[A] {

    /**
     * Parse a flag value from a string.
     *
     * @param flagName the name of the flag (for error messages)
     * @param raw the raw string value
     * @return Either a parsed value or a ConfigError
     */
    def parse(flagName: String, raw: String): Either[ConfigError, A]

    /**
     * Human-readable type name for error messages.
     */
    def typeName: String
  }

  object Reader {

    /**
     * Marker subclass for scalar types (types whose string representation contains no commas).
     * Used to enable comma-separated list parsing.
     */
    trait Scalar[A] extends Reader[A]

    /**
     * Create a Reader from a parse function and type name.
     */
    def apply[A](parseFunc: (String, String) => Either[ConfigError, A], typeNameStr: String): Reader[A] =
      new Reader[A] {
        def parse(flagName: String, raw: String): Either[ConfigError, A] = parseFunc(flagName, raw)
        def typeName: String = typeNameStr
      }

    /**
     * Create a Scalar Reader from a parse function and type name.
     */
    def scalar[A](parseFunc: (String, String) => Either[ConfigError, A], typeNameStr: String): Scalar[A] =
      new Scalar[A] {
        def parse(flagName: String, raw: String): Either[ConfigError, A] = parseFunc(flagName, raw)
        def typeName: String = typeNameStr
      }

    // Built-in readers

    implicit val intReader: Scalar[Int] = scalar(
      (flagName, raw) =>
        Try(raw.toInt).toEither.left.map(e =>
          ConfigError.InvalidValue(flagName, raw, "Int", "flag", Some(e))
        ),
      "Int"
    )

    implicit val longReader: Scalar[Long] = scalar(
      (flagName, raw) =>
        Try(raw.toLong).toEither.left.map(e =>
          ConfigError.InvalidValue(flagName, raw, "Long", "flag", Some(e))
        ),
      "Long"
    )

    implicit val doubleReader: Scalar[Double] = scalar(
      (flagName, raw) =>
        Try(raw.toDouble).toEither.left.map(e =>
          ConfigError.InvalidValue(flagName, raw, "Double", "flag", Some(e))
        ),
      "Double"
    )

    implicit val booleanReader: Scalar[Boolean] = scalar(
      (flagName, raw) => {
        val lower = raw.toLowerCase
        if (lower == "true" || lower == "1") Right(true)
        else if (lower == "false" || lower == "0") Right(false)
        else
          Left(
            ConfigError.InvalidValue(
              flagName,
              raw,
              "Boolean (true/false or 1/0)",
              "flag"
            )
          )
      },
      "Boolean"
    )

    implicit val stringReader: Scalar[String] = scalar(
      (_, raw) => Right(raw),
      "String"
    )

     implicit def seqReader[A](implicit reader: Scalar[A]): Reader[Seq[A]] =
       Reader(
         (flagName, raw) => {
           if (raw.isEmpty) Right(Seq.empty)
           else {
             val parts = raw.split(",").map(_.trim).toSeq
             val results = parts.map(part => reader.parse(flagName, part))
             val errors = results.collect { case Left(e) => e }
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
                case "s" | "sec" | "secs" | "seconds"  => new FiniteDuration(num, SECONDS)
                case "m" | "min" | "mins" | "minutes"  => new FiniteDuration(num, MINUTES)
                case "h" | "hour" | "hours"            => new FiniteDuration(num, HOURS)
                case "d" | "day" | "days"              => new FiniteDuration(num, DAYS)
                case _ =>
                  throw new IllegalArgumentException(
                    s"Unknown time unit: $unit. Supported: ms, s, m, h, d"
                  )
              }
            }.toEither.left.map(e =>
              ConfigError.InvalidValue(
                flagName,
                raw,
                "Duration (e.g., 10s, 5m, 1h, 100ms, 2d)",
                "flag",
                Some(e)
              )
            )
          case _ =>
            Left(
              ConfigError.InvalidValue(
                flagName,
                raw,
                "Duration (e.g., 10s, 5m, 1h, 100ms, 2d)",
                "flag"
              )
            )
        }
      },
      "FiniteDuration"
    )
  }
}

object Flag extends Flag
