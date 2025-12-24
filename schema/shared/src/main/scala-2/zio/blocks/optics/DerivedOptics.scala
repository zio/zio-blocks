package zio.blocks.optics

import zio.blocks.macros.DerivedOpticsMacro
import scala.language.experimental.macros

trait DerivedOptics[T] {
  // lazy val ensures caching
  lazy val optics: Any = macro DerivedOpticsMacro.impl[T]
}

trait DerivedOptics_[T] {
  lazy val optics: Any = macro DerivedOpticsMacro.impl[T]
}