package zio.blocks.schema

import scala.annotation.StaticAnnotation

/**
 * @deprecated
 *   Use `extends DerivedOptics[T]` pattern instead for cross-platform
 *   compatibility.
 *
 * Example:
 * {{{
 * case class Person(name: String, age: Int)
 * object Person extends DerivedOptics[Person]
 *
 * // Usage:
 * val nameLens: Lens[Person, String] = Person.optics.name
 * }}}
 */
@deprecated("Use `extends DerivedOptics[T]` pattern instead", "1.0.0")
class deriveOptics extends StaticAnnotation

/**
 * @deprecated
 *   Use `extends DerivedOptics[T]` pattern instead for cross-platform
 *   compatibility.
 */
@deprecated("Use `extends DerivedOptics[T]` pattern instead", "1.0.0")
class deriveOptics_ extends StaticAnnotation

/**
 * @deprecated
 *   Use `derives Schema` instead.
 *
 * Example:
 * {{{
 * case class Person(name: String, age: Int) derives Schema
 * }}}
 */
@deprecated("Use `derives Schema` instead", "1.0.0")
class deriveSchema extends StaticAnnotation
