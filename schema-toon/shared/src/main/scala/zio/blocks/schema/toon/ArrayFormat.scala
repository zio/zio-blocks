package zio.blocks.schema.toon

/**
 * Specifies how arrays should be encoded in TOON format.
 */
sealed trait ArrayFormat

object ArrayFormat {

  /**
   * Automatically select the most compact format based on array contents:
   *   - Tabular for uniform object arrays with primitive fields
   *   - Inline for primitive arrays
   *   - List for heterogeneous or nested data
   */
  case object Auto extends ArrayFormat

  /**
   * Force tabular format: `items[N]{field1,field2}: val1,val2` Falls back to
   * List if array is not tabular-eligible.
   */
  case object Tabular extends ArrayFormat

  /**
   * Force inline format: `items[N]: val1,val2,val3` Only valid for primitive
   * arrays.
   */
  case object Inline extends ArrayFormat

  /**
   * Force list format with `- ` markers.
   */
  case object List extends ArrayFormat
}
