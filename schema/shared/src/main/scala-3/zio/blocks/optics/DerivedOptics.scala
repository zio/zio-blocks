package zio.blocks.optics

import zio.blocks.macros.DerivedOpticsMacro

trait DerivedOptics[T] {
  @volatile private var _opticsCache: Any = _

  transparent inline def optics: Any = ${ DerivedOpticsMacro.impl[T]('this, false) }
}

trait DerivedOptics_[T] {
  @volatile private var _opticsCache: Any = _

  transparent inline def optics: Any = ${ DerivedOpticsMacro.impl[T]('this, true) }
}

