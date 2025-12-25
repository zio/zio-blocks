package zio.blocks.optics

import zio.blocks.macros.DerivedOpticsMacro

trait DerivedOptics[T] {
<<<<<<< HEAD
=======
  @volatile private var _opticsCache: Any = _

>>>>>>> ed52d95be7cc3dfb047242f0180ce3219f9672b1
  transparent inline def optics: Any = ${ DerivedOpticsMacro.impl[T]('this, false) }
}

trait DerivedOptics_[T] {
<<<<<<< HEAD
  transparent inline def optics: Any = ${ DerivedOpticsMacro.impl[T]('this, true) }
}
=======
  @volatile private var _opticsCache: Any = _

  transparent inline def optics: Any = ${ DerivedOpticsMacro.impl[T]('this, true) }
}
<<<<<<< HEAD
=======

>>>>>>> ed52d95be7cc3dfb047242f0180ce3219f9672b1
>>>>>>> bb2e9105298a9cee5e15b90dc2291ed27f32f238
