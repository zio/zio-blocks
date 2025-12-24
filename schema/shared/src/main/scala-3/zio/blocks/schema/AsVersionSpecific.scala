package zio.blocks.schema

/**
 * Version-specific trait for Scala 3 macro implementations of `As` type class.
 *
 * This trait provides the `derived` method for `As[A, B]` that delegates to
 * `derivedAs` macro implementation.
 */
trait AsVersionSpecific {
  inline def derived[A, B]: As[A, B] = ${ IntoAsVersionSpecificImpl.derivedAsImpl[A, B] }
  inline def derivedAs[A, B]: As[A, B] = ${ IntoAsVersionSpecificImpl.derivedAsImpl[A, B] }
}
