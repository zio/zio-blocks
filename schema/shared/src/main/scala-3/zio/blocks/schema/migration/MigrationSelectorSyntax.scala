package zio.blocks.schema.migration

import scala.compiletime.error
import zio.blocks.schema.Schema

/**
 * Provides selector syntax extensions for use in migration builder methods.
 *
 * These extension methods allow writing selectors like:
 *   - `_.field.when[CaseType]`
 *   - `_.items.each`
 *   - `_.map.eachKey`
 *   - `_.map.eachValue`
 *   - `_.seq.at(0)`
 *   - `_.map.atKey(key)`
 */
trait MigrationSelectorSyntax {

  extension [A](a: A) {

    /** Select a specific case of a sum type. */
    inline def when[B <: A]: B = error("Can only be used inside migration selector macros")

    /** Access a wrapped value (newtype/opaque type). */
    inline def wrapped[B]: B = error("Can only be used inside migration selector macros")
  }

  extension [C[_], A](c: C[A]) {

    /** Access element at a specific index. */
    inline def at(index: Int): A = error("Can only be used inside migration selector macros")

    /** Access elements at multiple indices. */
    inline def atIndices(indices: Int*): A = error("Can only be used inside migration selector macros")

    /** Traverse all elements in a collection. */
    inline def each: A = error("Can only be used inside migration selector macros")
  }

  extension [M[_, _], K, V](m: M[K, V]) {

    /** Access value at a specific key. */
    inline def atKey(key: K)(using Schema[K]): V = error("Can only be used inside migration selector macros")

    /** Access values at multiple keys. */
    inline def atKeys(keys: K*)(using Schema[K]): V = error("Can only be used inside migration selector macros")

    /** Traverse all keys in a map. */
    inline def eachKey: K = error("Can only be used inside migration selector macros")

    /** Traverse all values in a map. */
    inline def eachValue: V = error("Can only be used inside migration selector macros")
  }
}

object MigrationSelectorSyntax extends MigrationSelectorSyntax
