package zio.blocks.schema.migration

import scala.annotation.compileTimeOnly
import zio.blocks.schema.Schema

/**
 * Provides selector syntax extensions for use in migration builder methods.
 * Scala 2 version using implicit classes.
 */
trait MigrationSelectorSyntax {

  implicit class MigrationValueExtension[A](a: A) {
    @compileTimeOnly("Can only be used inside migration selector macros")
    def when[B <: A]: B = ???

    @compileTimeOnly("Can only be used inside migration selector macros")
    def wrapped[B]: B = ???
  }

  implicit class MigrationSequenceExtension[C[_], A](c: C[A]) {
    @compileTimeOnly("Can only be used inside migration selector macros")
    def at(index: Int): A = ???

    @compileTimeOnly("Can only be used inside migration selector macros")
    def atIndices(indices: Int*): A = ???

    @compileTimeOnly("Can only be used inside migration selector macros")
    def each: A = ???
  }

  implicit class MigrationMapExtension[M[_, _], K, V](m: M[K, V]) {
    @compileTimeOnly("Can only be used inside migration selector macros")
    def atKey(key: K)(implicit schema: Schema[K]): V = ???

    @compileTimeOnly("Can only be used inside migration selector macros")
    def atKeys(keys: K*)(implicit schema: Schema[K]): V = ???

    @compileTimeOnly("Can only be used inside migration selector macros")
    def eachKey: K = ???

    @compileTimeOnly("Can only be used inside migration selector macros")
    def eachValue: V = ???
  }
}

object MigrationSelectorSyntax extends MigrationSelectorSyntax
