package zio.blocks.schema

/**
 * JSON data type and utilities for ZIO Blocks.
 *
 * ==Quick Start==
 *
 * {{{
 * import zio.blocks.schema.json._
 * import zio.blocks.schema.json.interpolators._
 *
 * // Parse JSON
 * val json = Json.parse("""{"name": "Alice", "age": 30}""")
 *
 * // Navigate with path interpolator
 * val name = json.get(p"name")
 *
 * // Pattern match
 * json match {
 *   case Json.Object(fields) => // ...
 *   case Json.Array(elements) => // ...
 *   case _ => // ...
 * }
 * }}}
 */
package object json extends PathInterpolator {

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
   * The `p` interpolator creates [[DynamicOptic]] paths using a JSONPath-compatible dialect:
   *
   * {{{
   * p"foo.bar"           // fields "foo" then "bar"
   * p"users[0]"          // field "users", then index 0
   * p"users[0].name"     // field "users", index 0, field "name"
   * p"items[*]"          // field "items", then all array elements
   * p"config{*}"         // field "config", then all object values
   * p"config{*:}"        // field "config", then all object keys
   * p"[0,2,5]"           // indices 0, 2, and 5
   * p"[0:5]"             // slice: indices 0 through 4
   * p"[::2]"             // slice: every other element
   * p"`field.name`"      // field with dots in name (backtick escaping)
   * p"""["field"]"""     // alternate field syntax (bracket notation)
   * }}}
   *
   * ===JSONPath Compatibility===
   *
   * This syntax is a dialect of JSONPath (RFC 9535). Most JSONPath expressions work:
   *  - `$.foo.bar` - root prefix is optional and ignored
   *  - `.field`, `["field"]` - field access
   *  - `[n]`, `[*]`, `[m,n]`, `[m:n]` - array access
   *
   * '''Not supported:'''
   *  - `..` (recursive descent)
   *  - `[?()]` (filter expressions)
   *
   * ===Extensions beyond JSONPath:===
   *  - `{*}` - all object values (explicit, vs `[*]` which is array-focused in JSONPath)
   *  - `{*:}` - all object keys (not expressible in standard JSONPath)
   *  - Backtick escaping for field names
   */
  object interpolators extends PathInterpolator
}

