package zio.blocks.schema.json

import scala.util.control.NoStackTrace

/**
 * A simple error type for JSON decoding failures.
 *
 * @param message
 *   A descriptive error message.
 */
final case class JsonDecoderError(message: String) extends Exception(message) with NoStackTrace
