package zio.blocks.schema

trait PathInterpolator {
  
  extension (inline sc: StringContext) {
    transparent inline def p(inline args: Any*): DynamicOptic = ${ PathMacros.pImpl('sc, 'args) }
  }
}
