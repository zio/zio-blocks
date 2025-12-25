package zio.blocks.optics

import scala.language.experimental.macros
import zio.blocks.macros.DerivedOpticsMacro

trait DerivedOptics[T] {
  def optics: Any = macro DerivedOpticsMacro.impl[T]
}

trait DerivedOptics_[T] {
  def optics: Any = macro DerivedOpticsMacro.implUnderscore[T]
}