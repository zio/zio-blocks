package zio.blocks.scope

import scala.quoted.*

private[scope] trait FactoryCompanionVersionSpecific {

  /**
   * Derives a Factory[T] from T's constructor.
   *
   * If T has dependencies, this won't compile. Use Wire[T] instead and call
   * toFactory.
   */
  inline def apply[T]: Factory[T] = ${ FactoryMacros.deriveFactoryImpl[T] }
}
