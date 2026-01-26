package zio.blocks.schema.json

/**
 * Represents the context in which a JSON interpolation occurs.
 *
 * Used by the JSON string interpolator macro to determine type constraints:
 *   - Key: Requires `Keyable[A]` - only types that can be used as JSON keys
 *   - Value: Requires `JsonEncoder[A]` - any type that can be encoded to JSON
 *   - InString: Requires `Keyable[A]` - embedded within a JSON string literal
 */
private[json] sealed trait InterpolationContext

private[json] object InterpolationContext {

  /**
   * Interpolation in key position: `{$key: ...}`
   *
   * Only "keyable" types (those defined in `PrimitiveType`) are allowed. The
   * value will be converted to a string using `Keyable[A].asKey`.
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
   * Only "keyable" types are allowed. The value will be converted to a string
   * using `Keyable[A].asKey` and concatenated into the JSON string.
   */
  case object InString extends InterpolationContext
}
