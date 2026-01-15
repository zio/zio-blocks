package zio.blocks.schema.json

import zio.blocks.schema.DynamicOptic

/**
 * Provides string interpolators for JSON paths and literals.
 *
 * Import with:
 * {{{
 * import zio.blocks.schema.json.interpolators._
 * }}}
 *
 * ==Path Syntax==
 *
 * The `p` interpolator creates [[DynamicOptic]] paths using a
 * JSONPath-compatible dialect:
 *
 * {{{
 * p"foo.bar"           // fields "foo" then "bar"
 * p"users[0]"          // field "users", then index 0
 * p"users[0].name"     // field "users", index 0, field "name"
 * p"items[*]"          // field "items", then all array elements
 * }}}
 */
object interpolators {

  extension (inline sc: StringContext) {

    /**
     * Creates a [[DynamicOptic]] from a path string at compile time.
     *
     * @return
     *   The parsed [[DynamicOptic]]
     */
    inline def p(inline args: Any*): DynamicOptic = ${ PathMacros.pathImpl('sc, 'args) }
  }

  extension (inline sc: StringContext) {

    /**
     * Creates a [[Json]] value from a JSON literal at compile time.
     *
     * {{{
     * j"""{"name": "Alice", "age": 30}"""
     * j"[1, 2, 3]"
     * j"null"
     * }}}
     *
     * Interpolated values are converted to JSON:
     * {{{
     * val name = "Bob"
     * val age = 25
     * j"""{"name": $name, "age": $age}"""
     * }}}
     *
     * @return
     *   The parsed [[Json]] value
     */
    inline def j(inline args: Any*): Json = ${ PathMacros.jsonImpl('sc, 'args) }
  }
}
