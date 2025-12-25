package zio.blocks.schema.internal

import zio.blocks.schema.As
import scala.language.experimental.macros

trait AsVersionSpecific {
  implicit def derive[A, B]: As[A, B] = macro AsMacros.deriveImpl[A, B]
}
