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

package zio.blocks.codegen.emit

/**
 * Configuration for [[ScalaEmitter]]: indentation, import sorting, the Scala
 * dialect of the output and trailing commas. Start from
 * [[EmitterConfig.default]] (Scala 3) or [[EmitterConfig.scala2]] and change
 * fields with `copy`.
 *
 * {{{
 * import zio.blocks.codegen.ir._
 * import zio.blocks.codegen.emit.{EmitterConfig, ScalaEmitter}
 *
 * val status = Enum("Status", List(EnumCase.SimpleCase("Active"), EnumCase.SimpleCase("Suspended")))
 *
 * ScalaEmitter.emitEnum(status, EmitterConfig.default)
 * // enum Status {
 * //   case Active, Suspended
 * // }
 *
 * ScalaEmitter.emitEnum(status, EmitterConfig.scala2)
 * // sealed trait Status
 * //
 * // object Status {
 * //   case object Active extends Status
 * //   case object Suspended extends Status
 * // }
 * }}}
 *
 * @param indentWidth
 *   Number of spaces per indentation level (default: 2)
 * @param sortImports
 *   Whether `ScalaEmitter.emit` sorts a file's imports (default: true). They
 *   are sorted by a text key in plain string order, so uppercase letters come
 *   before lowercase ones: `path.name` for a single import, `path.*` for a
 *   wildcard, `path.from` for a rename and the path plus the first name for a
 *   group. Duplicates are removed either way.
 * @param scala3Syntax
 *   Whether to emit Scala 3 syntax (default: true): `*` and `as` in imports,
 *   `enum`, `opaque type`, `derives`, `using`, `given` and `extension`. When
 *   false, the output uses `_` and `=>` in imports, sealed traits for enums,
 *   plain type aliases for opaque types, `implicit` and implicit classes, and
 *   drops `derives` clauses.
 * @param trailingCommas
 *   Whether to put a comma after the last field of a case class or abstract
 *   class (default: true). Other constructs never get trailing commas.
 * @see
 *   [[https://zioblocks.com/reference/codegen/emitter-config/ EmitterConfig]]
 */
final case class EmitterConfig(
  indentWidth: Int = 2,
  sortImports: Boolean = true,
  scala3Syntax: Boolean = true,
  trailingCommas: Boolean = true
)

/** Ready-made configurations. */
object EmitterConfig {

  /**
   * Scala 3 output: 2-space indentation, sorted imports, trailing commas.
   */
  val default: EmitterConfig = EmitterConfig()

  /**
   * Scala 2 output: 2-space indentation, sorted imports, no Scala 3 syntax and
   * no trailing commas.
   */
  val scala2: EmitterConfig = EmitterConfig(scala3Syntax = false, trailingCommas = false)
}
