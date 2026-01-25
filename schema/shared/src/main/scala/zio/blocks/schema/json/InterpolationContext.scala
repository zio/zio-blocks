package zio.blocks.schema.json

/**
 * Represents the context in which a JSON interpolation occurs.
 *
 * Used by the JSON string interpolator macro to determine type constraints:
 *   - Key: Requires `Stringable[A]` - only types with canonical string
 *     representation
 *   - Value: Requires `JsonEncoder[A]` - any type that can be encoded to JSON
 *   - InString: Requires `Stringable[A]` - embedded within a JSON string
 *     literal
 */
sealed trait InterpolationContext

object InterpolationContext {

  /**
   * Interpolation in key position: `{$key: ...}`
   *
   * Only "stringable" types (those defined in `PrimitiveType`) are allowed. The
   * value will be converted to a string using `Stringable[A].asString`.
   */
  case object Key extends InterpolationContext

  /**
   * Interpolation in value position: `{...: $value}` or `[$value, ...]`
   *
   * Any type with a `JsonEncoder[A]` instance is allowed.
   */
  case object Value extends InterpolationContext

  /**
   * Interpolation inside a string literal: `{...: "hello $name"}`
   *
   * Only "stringable" types are allowed. The value will be converted to a
   * string using `Stringable[A].asString` and concatenated into the JSON
   * string.
   */
  case object InString extends InterpolationContext
}
