package zio.blocks.schema

import scala.language.experimental.macros

trait ToStructuralVersionSpecific {
  def derived[A]: ToStructural[A] = macro DeriveToStructural.derivedImpl[A]
}
