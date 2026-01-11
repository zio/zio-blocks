package zio.blocks.schema.toon

/**
 * Specifies how arrays/sequences should be formatted in TOON output.
 *
 * TOON supports multiple array formats to balance readability with compactness.
 * The format can be configured via `ToonBinaryCodecDeriver.withArrayFormat()`.
 *
 * ==Format Examples==
 *
 * Given a list `List(1, 2, 3)`:
 *
 * '''Inline format''':
 * {{{
 * [3]: 1,2,3
 * }}}
 *
 * '''List format''':
 * {{{
 * [3]:
 *   - 1
 *   - 2
 *   - 3
 * }}}
 *
 * '''Tabular format''' (for records):
 * {{{
 * [2]:
 *   id: 1, name: Alice
 *   id: 2, name: Bob
 * }}}
 *
 * @see
 *   [[ToonBinaryCodecDeriver.withArrayFormat]] to configure the format
 */
sealed trait ArrayFormat

/**
 * Companion object containing the available array format options.
 */
object ArrayFormat {

  /**
   * Automatically selects the best format based on element type.
   *
   *   - Primitive elements → Inline format
   *   - Complex elements (records) → List format
   *
   * This is the default format.
   */
  case object Auto extends ArrayFormat

  /**
   * Tabular format for records with uniform fields.
   *
   * Each record is on a single line with comma-separated values. Best for
   * arrays of simple records where field names are clear from context.
   *
   * Example:
   * {{{
   * users[2]:
   *   1,Alice
   *   2,Bob
   * }}}
   */
  case object Tabular extends ArrayFormat

  /**
   * Inline format with comma-separated values.
   *
   * All elements on a single line, best for primitive values or short arrays.
   *
   * Example:
   * {{{
   * [3]: 1,2,3
   * }}}
   */
  case object Inline extends ArrayFormat

  /**
   * List format with each element on its own line, prefixed with `-`.
   *
   * Best for arrays of complex objects or when readability is paramount.
   *
   * Example:
   * {{{
   * [2]:
   *   - name: Alice
   *     age: 30
   *   - name: Bob
   *     age: 25
   * }}}
   */
  case object List extends ArrayFormat
}
