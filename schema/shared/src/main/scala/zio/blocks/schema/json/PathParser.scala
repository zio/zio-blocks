package zio.blocks.schema.json

import zio.blocks.schema.DynamicOptic

/**
 * Parser for JSON path strings used by interpolators.
 *
 * Supports the following syntax:
 *   - `field` or `.field` - field access
 *   - `[0]` - array index access
 *   - `[*]` - all array elements
 *   - `field.subfield` - nested field access
 *   - `field[0].subfield` - mixed access
 */
object PathParser {

  /**
   * Parses a path string into a [[DynamicOptic]].
   *
   * @param path
   *   The path string to parse
   * @return
   *   The parsed [[DynamicOptic]]
   * @throws IllegalArgumentException
   *   if the path is invalid
   */
  def parsePath(path: String): DynamicOptic = {
    if (path.isEmpty || path == ".") return DynamicOptic.root

    var optic = DynamicOptic.root
    var pos   = 0

    // Skip leading dot if present
    if (path.charAt(0) == '.') pos = 1

    while (pos < path.length) {
      path.charAt(pos) match {
        case '[' =>
          // Array access
          val endBracket = path.indexOf(']', pos)
          if (endBracket < 0) {
            throw new IllegalArgumentException(s"Unclosed bracket in path: $path")
          }
          val content = path.substring(pos + 1, endBracket)
          if (content == "*") {
            optic = optic.elements
          } else {
            try {
              val index = content.toInt
              optic = optic.at(index)
            } catch {
              case _: NumberFormatException =>
                throw new IllegalArgumentException(s"Invalid array index: $content in path: $path")
            }
          }
          pos = endBracket + 1
          // Skip trailing dot after bracket if present
          if (pos < path.length && path.charAt(pos) == '.') pos += 1

        case '.' =>
          // Skip dot
          pos += 1

        case _ =>
          // Field name
          val start = pos
          while (pos < path.length && path.charAt(pos) != '.' && path.charAt(pos) != '[') {
            pos += 1
          }
          val fieldName = path.substring(start, pos)
          if (fieldName.isEmpty) {
            throw new IllegalArgumentException(s"Empty field name in path: $path")
          }
          optic = optic.field(fieldName)
          // Skip trailing dot
          if (pos < path.length && path.charAt(pos) == '.') pos += 1
      }
    }

    optic
  }
}
