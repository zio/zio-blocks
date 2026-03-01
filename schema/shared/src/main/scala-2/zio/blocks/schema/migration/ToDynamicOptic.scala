package zio.blocks.schema.migration

import zio.blocks.schema.DynamicOptic
import zio.blocks.schema.migration.macros.AccessorMacros

/**
 * ToDynamicOptic is a type-safe value class that wraps a DynamicOptic. It
 * ensures a 100% pure data representation of schema paths while maintaining
 * compile-time type safety for source (S) and target (A) types. * Performance
 * Note: As an 'AnyVal', this wrapper minimizes heap allocations, though boxing
 * may occur when used within generic contexts.
 */
final case class ToDynamicOptic[S, A](private val value: DynamicOptic) extends AnyVal {

  /**
   * Unwraps the underlying DynamicOptic. Defined as an 'apply' method to
   * maintain compatibility with builders and DSLs that require explicit optic
   * extraction.
   */
  def apply(): DynamicOptic = value
}

object ToDynamicOptic {
  import scala.language.experimental.macros
  import scala.language.implicitConversions

  /**
   * Automatically derives a ToDynamicOptic instance from a selector function (S =>
   * A). This uses compile-time macros to traverse the AST and generate the path
   * nodes.
   */
  implicit def derive[S, A](selector: S => A): ToDynamicOptic[S, A] = macro AccessorMacros.deriveImpl[S, A]
}
