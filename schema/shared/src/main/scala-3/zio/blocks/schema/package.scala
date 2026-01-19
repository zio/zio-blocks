package zio.blocks.schema

extension (inline sc: StringContext) {
  inline def p(inline args: Any*): DynamicOptic =
    PathMacros.pImpl(sc, args)
}
