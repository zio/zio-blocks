package zio.blocks.schema

import scala.language.experimental.macros
import scala.reflect.macros.blackbox

/**
 * A trait that can be extended by companion objects to automatically derive
 * a Schema for the type.
 *
 * Usage:
 * {{{
 * case class Person(name: String, age: Int)
 * object Person extends DerivedOptics[Person] with DerivedSchema[Person]
 * }}}
 *
 * The schema is cached using the same caching mechanism as Schema.derived.
 */
trait DerivedSchema[A] {

  /**
   * Derives a Schema for the type A. The schema derivation uses macro expansion
   * which generates cached schema code at the call site.
   */
  implicit def schema: Schema[A] = macro DerivedSchemaMacro.impl[A]
}

private[schema] object DerivedSchemaMacro {
  def impl[A: c.WeakTypeTag](c: blackbox.Context): c.Expr[Schema[A]] = {
    import c.universe._
    val tpe = weakTypeOf[A]
    c.Expr[Schema[A]](q"_root_.zio.blocks.schema.Schema.derived[$tpe]")
  }
}
