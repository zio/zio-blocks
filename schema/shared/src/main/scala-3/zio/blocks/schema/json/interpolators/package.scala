package zio.blocks.schema.json

import zio.blocks.schema.DynamicOptic
import zio.blocks.schema.json.Json

package object interpolators {
  extension (inline sc: StringContext) {
    inline def p(inline args: Any*): DynamicOptic = ${ PathMacros.pImpl('sc, 'args) }
    inline def j(inline args: Any*): Json = ${ PathMacros.jImpl('sc, 'args) }
  }
}
