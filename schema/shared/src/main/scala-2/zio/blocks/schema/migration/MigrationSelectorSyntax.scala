package zio.blocks.schema.migration

import scala.annotation.compileTimeOnly

/**
 * Scala 2 extension methods for selector syntax in migrations. These are
 * compile-time-only placeholders that get transformed by the macros.
 *
 * Provides syntax sugar for selector expressions like:
 *   - `_.field.each` for collection traversal
 *   - `_.variant.when[Type]` for case selection
 *   - `_.wrapper.wrapped[Inner]` for wrapper unwrapping
 *   - `_.seq.at(0)` for index access
 *   - `_.map.atKey(key)` for map key access
 */
trait MigrationSelectorSyntax {

  implicit class ValueExtension[A](a: A) {
    @compileTimeOnly("Can only be used inside migration selector macros")
    def when[B <: A]: B = ???

    @compileTimeOnly("Can only be used inside migration selector macros")
    def wrapped[B]: B = ???
  }

  implicit class SequenceExtension[C[_], A](c: C[A]) {
    @compileTimeOnly("Can only be used inside migration selector macros")
    def at(index: Int): A = ???

    @compileTimeOnly("Can only be used inside migration selector macros")
    def each: A = ???
  }

  implicit class MapExtension[M[_, _], K, V](m: M[K, V]) {
    @compileTimeOnly("Can only be used inside migration selector macros")
    def atKey(key: K): V = ???

    @compileTimeOnly("Can only be used inside migration selector macros")
    def eachKey: K = ???

    @compileTimeOnly("Can only be used inside migration selector macros")
    def eachValue: V = ???
  }
}

object MigrationSelectorSyntax extends MigrationSelectorSyntax
