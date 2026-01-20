package zio.blocks.schema.json

import zio.blocks.schema.DynamicOptic

object interpolators {

  implicit class JsonPathInterpolator(val sc: StringContext) extends AnyVal {
    inline def p(inline args: Any*): DynamicOptic = ${ PathMacros.pathInterpolator('sc, 'args) }
  }

  implicit class JsonLiteralInterpolator(val sc: StringContext) extends AnyVal {
    inline def j(inline args: Any*): Json = ${ PathMacros.jsonInterpolator('sc, 'args) }
  }
}
