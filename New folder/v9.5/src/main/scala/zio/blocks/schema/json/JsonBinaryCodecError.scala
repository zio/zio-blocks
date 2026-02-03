package zio.blocks.schema.json

import zio.blocks.schema.DynamicOptic

/**
 * A custom exception class without the stack trace representing errors that
 * occur during JSON binary codec operations.
 *
 * The `JsonBinaryCodecError` class extends `Throwable` to provide enhanced
 * error reporting for scenarios involving JSON encoding or decoding errors
 * where dynamic data paths (spans) are involved. These spans represent the
 * stack of nodes traversed within the data structure when the error occurred.
 * So that top-level spans are at the end of the list.
 *
 * @param spans
 *   A list of `DynamicOptic.Node` objects representing the traversal path
 *   within the data structure where the error occurred. Each node encapsulates
 *   information about a specific element (field, index, etc.).
 * @param message
 *   A descriptive message providing additional context or details about the
 *   error.
 */
class JsonBinaryCodecError(var spans: List[DynamicOptic.Node], message: String)
    extends Throwable(message, null, false, false) {
  override def getMessage: String = message
}
