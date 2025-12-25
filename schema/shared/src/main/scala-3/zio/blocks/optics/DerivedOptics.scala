package zio.blocks.optics

import zio.blocks.macros.DerivedOpticsMacro

trait DerivedOptics[T] {
  transparent inline def optics: Any = ${ DerivedOpticsMacro.impl[T]('this, false) }
}

trait DerivedOptics_[T] {
  transparent inline def optics: Any = ${ DerivedOpticsMacro.impl[T]('this, true) }
}
