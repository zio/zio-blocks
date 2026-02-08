package zio.blocks.scope

import scala.quoted.*

private[scope] trait ResourceCompanionVersionSpecific {

  /**
   * Derives a Resource[T] from T's constructor.
   *
   * Only works for types with no dependencies. If T has constructor parameters
   * (other than an implicit Scope), use [[Wire]][T] and call
   * `.toResource(deps)`.
   *
   * If T extends `AutoCloseable`, its `close()` method is automatically
   * registered as a finalizer.
   *
   * @tparam T
   *   the type to construct (must be a class with no dependencies)
   * @return
   *   a resource that creates T instances
   */
  inline def apply[T]: Resource[T] = ${ ResourceMacros.deriveResourceImpl[T] }
}
