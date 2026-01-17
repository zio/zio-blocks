package zio.blocks.schema.json

import zio.blocks.schema.DynamicOptic
import scala.language.experimental.macros

package object interpolators {
  implicit class JsonStringContext(private val sc: StringContext) extends AnyVal {
    def p(args: Any*): DynamicOptic = macro PathMacros.pImpl
    def j(args: Any*): Json = macro PathMacros.jImpl
  }
}
