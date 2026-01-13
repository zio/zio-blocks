package zio.blocks.schema.toon

/**
 * Represents the three array formats supported by TOON:
 *
 *   - '''Inline''': Primitive arrays rendered as comma-separated values on one
 *     line: `tags[3]: admin,ops,dev`
 *   - '''Tabular''': Arrays of uniform objects rendered with a header row and
 *     data rows: `items[2]{id,name}: 1,Alice / 2,Bob`
 *   - '''List''': General arrays rendered as indented list items prefixed with
 *     dash: `items[3]: / - item1 / - item2`
 *
 * @see
 *   [[https://github.com/toon-format/spec TOON Specification]]
 */
sealed trait ArrayFormat

object ArrayFormat {

  /**
   * Automatically selects the optimal array format based on element types:
   *   - '''Tabular''' if all elements are objects with identical primitive-only
   *     fields
   *   - '''Inline''' if all elements are primitives
   *   - '''List''' for everything else (nested arrays, mixed types, objects
   *     with nested values)
   */
  case object Auto extends ArrayFormat

  /**
   * Inline format for primitive arrays.
   *
   * Example output:
   * {{{
   * tags[3]: admin,ops,dev
   * scores[4]: 98.5,87.3,92.1,88.7
   * }}}
   */
  case object Inline extends ArrayFormat

  /**
   * Tabular format for arrays of uniform objects with primitive fields.
   *
   * Example output:
   * {{{
   * users[2]{id,name,role}:
   *   1,Alice,admin
   *   2,Bob,user
   * }}}
   */
  case object Tabular extends ArrayFormat

  /**
   * List format for general arrays including nested structures.
   *
   * Example output:
   * {{{
   * items[3]:
   *   - first item
   *   - key: value
   *   - [2]: nested,array
   * }}}
   */
  case object List extends ArrayFormat
}
