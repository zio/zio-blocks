package zio.blocks.schema.internal

import zio.blocks.schema.{Into, IntoMacros}

trait IntoVersionSpecific {
  inline given [A, B]: Into[A, B] = IntoMacros.derive[A, B]
}
