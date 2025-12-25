package zio.blocks.schema.internal

import zio.blocks.schema.{As, AsMacros}

trait AsVersionSpecific {
  inline given [A, B]: As[A, B] = AsMacros.derive[A, B]
}
