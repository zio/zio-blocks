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

/**
 * A flag whose value is resolved exactly once at class-load time and never
 * changes.
 *
 * Intended to be extended by Scala `object` definitions:
 * {{{
 *   object MyFlag extends StaticFlag[Int](42)
 * }}}
 *
 * Resolution order: FlagProvider registry → system property → environment
 * variable → default. Throws at class load on parse/validation failure
 * (fail-fast).
 */
abstract class StaticFlag[A](default: A)(implicit reader: Flag.Reader[A]) {

  val name: String = StaticFlag.deriveName(getClass)

  private val envName: String = name.replace('.', '_').toUpperCase

  private val resolved: (A, Flag.Source, Provenance) = StaticFlag.resolve(name, envName, default, reader)

  val value: A               = resolved._1
  val source: Flag.Source    = resolved._2
  val provenance: Provenance = resolved._3

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
      throw new IllegalArgumentException(
        s"StaticFlag must be defined as a Scala object, but got class name: $className"
      )
    if (className.contains("$$Lambda$") || className.contains("$$anon"))
      throw new IllegalArgumentException(
        s"StaticFlag must be defined as a Scala object, not a lambda or anonymous class: $className"
      )
  }

  private[config] def resolve[A](
    name: String,
    envName: String,
    default: A,
    reader: Flag.Reader[A]
  ): (A, Flag.Source, Provenance) =
    FlagProvider.Registry.resolve(name) match {
      case Some((rawValue, providerId)) =>
        val parsed = reader.parse(name, rawValue) match {
          case Right(v) => v
          case Left(e)  => throw new ExceptionInInitializerError(s"StaticFlag '$name': ${e.message}")
        }
        (parsed, Flag.Source.FlagProviderSource(providerId), Provenance.Resolved(providerId, name, Some(rawValue)))

      case None =>
        val sysProp = System.getProperty(name)
        if (sysProp != null) {
          val parsed = reader.parse(name, sysProp) match {
            case Right(v) => v
            case Left(e)  => throw new ExceptionInInitializerError(s"StaticFlag '$name': ${e.message}")
          }
          (parsed, Flag.Source.SystemProperty, Provenance.Resolved("sysprop", name, Some(sysProp)))
        } else {
          val envVal = System.getenv(envName)
          if (envVal != null) {
            val parsed = reader.parse(name, envVal) match {
              case Right(v) => v
              case Left(e)  => throw new ExceptionInInitializerError(s"StaticFlag '$name': ${e.message}")
            }
            (parsed, Flag.Source.EnvironmentVariable, Provenance.Resolved("env", envName, Some(envVal)))
          } else {
            (default, Flag.Source.Default, Provenance.Default)
          }
        }
    }

  private[config] def register(flag: StaticFlag[_]): Unit = {
    val existing = Flag.registry.putIfAbsent(flag.name, flag)
    if (existing != null && (existing.asInstanceOf[AnyRef] ne flag.asInstanceOf[AnyRef]))
      throw new IllegalStateException(
        s"Duplicate StaticFlag name '${flag.name}': already registered by ${existing.getClass.getName}"
      )
  }
}
