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
   *   - `Finalizer` parameters: finalizer is passed but no dependency added
   *
   * The `In` type is the intersection of all dependencies.
   *
   * For AutoCloseable types, `close()` is automatically registered as a
   * finalizer.
   *
   * @example
   *   {{{
   *   class Service(db: Database, cache: Cache)(using Finalizer)
   *
   *   val wireable = Wireable.from[Service]
   *   // wireable.wire has type Wire[Database & Cache, Service]
   *   }}}
   */
  transparent inline def from[T]: Wireable[T] = ${ WireableMacros.fromImpl[T] }

  /**
   * Derives a [[Wireable]] for type T with wire overrides for some
   * dependencies.
   *
   * Wire overrides allow you to provide specific wires for some of T's
   * dependencies. The overridden dependencies are resolved using the provided
   * wires, and the remaining dependencies become the `In` type.
   *
   * @example
   *   {{{
   *   class Service(db: Database, cache: Cache, config: Config)
   *
   *   // Provide a wire for Config, so only Database & Cache remain as dependencies
   *   val wireable = Wireable.from[Service](Wire(Config("localhost", 8080)))
   *   // wireable.wire has type Wire[Database & Cache, Service]
   *   }}}
   */
  transparent inline def from[T](inline wires: Wire[?, ?]*): Wireable[T] =
    ${ WireableMacros.fromWithOverridesImpl[T]('wires) }
}

private[scope] object WireableMacros {

  def fromImpl[T: Type](using Quotes): Expr[Wireable[T]] =
    WireCodeGen.deriveWireable[T]

  def fromWithOverridesImpl[T: Type](wires: Expr[Seq[Wire[?, ?]]])(using Quotes): Expr[Wireable[T]] =
    WireCodeGen.deriveWireableWithOverrides[T](wires)
}
