package zio.blocks.optics

import zio.blocks.macros.DerivedOpticsMacro

trait DerivedOptics[T] {
  // lazy val ensures caching per companion object
  lazy val optics: Any = ${ DerivedOpticsMacro.impl[T]('this, false) }
}

trait DerivedOptics_[T] {
  lazy val optics: Any = ${ DerivedOpticsMacro.impl[T]('this, true) }
}