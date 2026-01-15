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
 * The `p` interpolator creates [[DynamicOptic]] paths using a JSONPath-compatible
 * dialect:
 *
 * {{{
 * p"foo.bar"           // fields "foo" then "bar"
 * p"users[0]"          // field "users", then index 0
 * p"users[0].name"     // field "users", index 0, field "name"
 * p"items[*]"          // field "items", then all array elements
 * }}}
 */
object interpolators {

  implicit class JsonPathInterpolator(val sc: StringContext) extends AnyVal {

    /**
     * Creates a [[DynamicOptic]] from a path string at compile time.
     *
     * @return
     *   The parsed [[DynamicOptic]]
     */
    def p(args: Any*): DynamicOptic = macro PathMacros.pathInterpolator
  }

  implicit class JsonLiteralInterpolator(val sc: StringContext) extends AnyVal {

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
    def j(args: Any*): Json = macro PathMacros.jsonInterpolator
  }
}
