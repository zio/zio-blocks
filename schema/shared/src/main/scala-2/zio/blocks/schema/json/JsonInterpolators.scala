package zio.blocks.schema.json

import scala.language.experimental.macros
import zio.blocks.schema.DynamicOptic

object interpolators {
  implicit class JsonPathInterpolator(val sc: StringContext) extends AnyVal {
    def p(args: Any*): DynamicOptic = macro PathMacros.pathInterpolator
  }
  implicit class JsonLiteralInterpolator(val sc: StringContext) extends AnyVal {
    def j(args: Any*): Json = macro PathMacros.jsonInterpolator
  }
}
