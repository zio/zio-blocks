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

import zio.blocks.maybe.Maybe

/**
 * A flag whose value is read once, when its object initializes (the first time
 * the program uses it), and never changes. Extend it with a Scala `object`; the
 * flag's name is the object's fully qualified name, such as
 * `com.example.Limits.MaxConnections`.
 *
 * The value comes from the first of these that has one:
 *   1. [[FlagSource.Registry]], under the flag's name;
 *   1. the system property with the flag's name;
 *   1. the environment variable named like the flag, with dots replaced by
 *      underscores and in upper case (`COM_EXAMPLE_LIMITS_MAXCONNECTIONS`). On
 *      Scala.js this step finds nothing, because `System.getenv` returns `null`
 *      there, a known bug;
 *   1. the constructor's `default`.
 *
 * A value that the [[Flag.Reader]] cannot parse throws an
 * `ExceptionInInitializerError` whose cause is a
 * [[FlagException.FlagValueParseException]] the first time the flag is used.
 * Later uses throw `NoClassDefFoundError` on the JVM, and the same
 * `ExceptionInInitializerError` again on Scala.js.
 *
 * {{{
 * object MaxConnections extends StaticFlag[Int](100)
 *
 * val limit = MaxConnections()
 * // 100
 * val from  = MaxConnections.source
 * // Default
 * }}}
 *
 * @see
 *   [[https://zioblocks.com/reference/config/flags/ Flags]]
 */
abstract class StaticFlag[A](default: A)(implicit reader: Flag.Reader[A], displayable: Displayable[A]) {

  /**
   * The flag's name: the object's fully qualified class name without the
   * trailing `$`, with each inner `$` replaced by a dot.
   */
  val name: String = StaticFlag.deriveName(getClass)

  private val envName: String = name.replace('.', '_').toUpperCase

  private val resolved: (A, Flag.Source, Provenance) =
    StaticFlag.resolve(name, envName, default, reader)

  /** The flag's value, read once at initialization. */
  val value: A = resolved._1

  /** Which step of the lookup supplied `value`. */
  val source: Flag.Source = resolved._2

  /**
   * Where `value` came from: [[Provenance.Default]] for the constructor
   * default, otherwise a [[Provenance.Resolved]] with the source id, key and
   * raw string.
   */
  val provenance: Provenance = resolved._3

  /**
   * `value` rendered by the flag's [[Displayable]], as `Flag.dump` shows it:
   * `<secret>` for a [[Secret]], `toString` for most other types.
   */
  val displayValue: String = displayable.display(value)

  /** Returns `value`. */
  def apply(): A = value

  StaticFlag.register(this)
}

/**
 * Companion of [[StaticFlag]]. A flag takes its value from the first registered
 * [[FlagSource]] that has it, then from the system property, then from the
 * environment variable, and keeps its default when none has it.
 */
object StaticFlag {

  private[config] def deriveName(clazz: Class[_]): String = {
    val raw = clazz.getName
    validateObjectName(raw)
    raw
      .stripSuffix("$")
      .replace('$', '.')
  }

  private def validateObjectName(className: String): Unit = {
    if (!className.endsWith("$"))
      throw FlagException.FlagNameException(
        className,
        s"must be defined as a Scala object, but got class name: $className"
      )
    if (className.contains("$$Lambda$") || className.contains("$$anon"))
      throw FlagException.FlagNameException(
        className,
        s"must be defined as a Scala object, not a lambda or anonymous class: $className"
      )
  }

  private[config] def resolve[A](
    name: String,
    envName: String,
    default: A,
    reader: Flag.Reader[A]
  ): (A, Flag.Source, Provenance) =
    FlagSource.Registry
      .resolve(name)
      .fold[(A, Flag.Source, Provenance)] {

        val sysProp = System.getProperty(name)
        if (sysProp != null) {
          val parsed = parseOrThrow(name, sysProp, reader)
          (parsed, Flag.Source.SystemProperty, Provenance.Resolved("sysprop", name, Maybe.present(sysProp)))
        } else {
          val envVal = System.getenv(envName)
          if (envVal != null) {
            val parsed = parseOrThrow(name, envVal, reader)
            (parsed, Flag.Source.EnvironmentVariable, Provenance.Resolved("env", envName, Maybe.present(envVal)))
          } else {
            (default, Flag.Source.Default, Provenance.Default)
          }
        }
      } { raw =>
        val SourceValue(rawValue, provenance) = raw
        val parsed                            = parseOrThrow(name, rawValue, reader)
        (parsed, Flag.Source.FlagSourceValue(provenance.sourceId), provenance)
      }

  private def parseOrThrow[A](name: String, rawValue: String, reader: Flag.Reader[A]): A =
    reader.parse(name, rawValue) match {
      case Right(value) => value
      case Left(error)  =>
        throw new ExceptionInInitializerError(
          FlagException.FlagValueParseException(name, rawValue, reader.typeName, Some(error))
        )
    }

  private[config] def register(flag: StaticFlag[_]): Unit = {
    val existing = Flag.registry.putIfAbsent(flag.name, flag)
    if (existing != null && (existing.asInstanceOf[AnyRef] ne flag.asInstanceOf[AnyRef]))
      throw FlagException.FlagDuplicateNameException(flag.name)
  }
}
