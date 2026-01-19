package zio.blocks.schema

import scala.quoted.*

/**
 * Enables the `p"..."` string interpolator for DynamicOptic path expressions in Scala 3.
 *
 * This interpolator parses path expressions at compile time, providing syntax validation
 * and generating `DynamicOptic` instances with zero runtime overhead.
 *
 * ==Usage==
 * {{{
 * import zio.blocks.schema._
 *
 * // Field access
 * val path1 = p".users.email"
 *
 * // Array indices and elements
 * val path2 = p".items[0]"           // Single index
 * val path3 = p".items[0,1,2]"       // Multiple indices
 * val path4 = p".items[0:5]"         // Range (0 to 4, exclusive end)
 * val path5 = p".items[*]"           // All elements
 *
 * // Map keys and selectors
 * val path6 = p""".config{"database"}"""  // String key
 * val path7 = p".ports{80}"               // Int key
 * val path8 = p".flags{'x'}"              // Char key
 * val path9 = p".enabled{true}"           // Boolean key
 * val path10 = p".settings{*}"            // All map values
 * val path11 = p".settings{*:}"           // All map keys
 *
 * // Variant cases
 * val path12 = p"<User>.name"
 * }}}
 *
 * ==Syntax Reference==
 *   - `.field` or `field` - field access
 *   - `[N]` - single index
 *   - `[N,M,...]` - multiple indices
 *   - `[N:M]` - range (exclusive end, max 10000 elements)
 *   - `[*]` or `[:*]` - all elements
 *   - `{"key"}` - string map key
 *   - `{N}` - integer map key (supports negative)
 *   - `{'c'}` - char map key
 *   - `{true/false}` - boolean map key
 *   - `{*}` or `{:*}` - all map values
 *   - `{*:}` - all map keys
 *   - `<CaseName>` - variant case
 *
 * ==Implementation Note==
 * This extension uses `inline sc: StringContext` which differs from the typical Scala 3
 * string interpolator pattern. This is required because we use `sc.valueOrAbort` to
 * extract the literal string parts at compile time. The standard non-inline approach
 * with quoted pattern matching was tested and does not work for this use case.
 *
 * @note This interpolator only supports literal strings. Interpolation variables are rejected
 *       at compile time.
 */
extension (inline sc: StringContext) {
  inline def p(inline args: Any*): DynamicOptic = 
    ${ PathMacros.pImpl('sc, 'args) }
}

// Empty trait for cross-compilation compatibility with Scala 2
trait PathInterpolatorSyntax

object PathInterpolatorSyntax extends PathInterpolatorSyntax
