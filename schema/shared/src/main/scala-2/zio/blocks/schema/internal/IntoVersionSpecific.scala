package zio.blocks.schema.internal

import zio.blocks.schema.Into
import scala.language.experimental.macros

trait IntoVersionSpecific {
  implicit def derive[A, B]: Into[A, B] = macro IntoMacros.deriveImpl[A, B]
}
