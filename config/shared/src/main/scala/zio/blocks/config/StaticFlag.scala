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
 * A flag whose value is resolved exactly once at class-load time and never
 * changes.
 *
 * Intended to be extended by Scala `object` definitions:
 * {{{
 *   object MyFlag extends StaticFlag[Int](42)
 * }}}
 *
 * Resolution order: FlagSource registry → system property → environment
 * variable → default. Throws at class load on parse/validation failure
 * (fail-fast).
 */
abstract class StaticFlag[A](default: A)(implicit reader: Flag.Reader[A], displayable: Displayable[A]) {

  val name: String = StaticFlag.deriveName(getClass)

  private val envName: String = name.replace('.', '_').toUpperCase

  private val resolved: (A, Flag.Source, Provenance) =
    StaticFlag.resolve(name, envName, default, reader)

  val value: A               = resolved._1
  val source: Flag.Source    = resolved._2
  val provenance: Provenance = resolved._3
  val displayValue: String   = displayable.display(value)

  def apply(): A = value

  StaticFlag.register(this)
}

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
    FlagSource.Registry.resolve(name) match {
      case raw if raw.isPresent =>
        val SourceValue(rawValue, provenance) = raw.get
        val parsed                            = parseOrThrow(name, rawValue, reader)
        (parsed, Flag.Source.FlagSourceValue(provenance.sourceId), provenance)

      case _ =>
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
