package zio.blocks.scope

import zio.blocks.scope.internal.WireCodeGen
import scala.quoted.*

private[scope] trait WireableVersionSpecific {

  /**
   * Derives a [[Wireable]] for type T from its primary constructor.
   *
   * Constructor parameters are analyzed to determine dependencies:
   *   - Regular parameters: become dependencies (part of `In` type)
   *   - `Scope.Has[Y]` parameters: Y becomes a dependency, scope is passed
   *     narrowed
   *   - `Scope.Any` parameters: scope is passed but no dependency added
   *
   * The `In` type is the intersection of all dependencies.
   *
   * For AutoCloseable types, `close()` is automatically registered as a
   * finalizer.
   *
   * @example
   *   {{{
   *   class Service(db: Database, cache: Cache)(using Scope.Any)
   *
   *   val wireable = Wireable.from[Service]
   *   // wireable.wire has type Wire[Database & Cache, Service]
   *   }}}
   */
  transparent inline def from[T]: Wireable[T] = ${ WireableMacros.fromImpl[T] }
}

private[scope] object WireableMacros {

  def fromImpl[T: Type](using Quotes): Expr[Wireable[T]] =
    WireCodeGen.deriveWireable[T]
}
