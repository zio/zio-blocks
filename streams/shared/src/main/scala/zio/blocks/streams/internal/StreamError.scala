package zio.blocks.streams.internal

/**
 * Wraps a non-Throwable error value so it can propagate through the
 * Reader/Interpreter stack via exceptions. Used by
 * [[zio.blocks.streams.Stream.fail]] and caught by
 * [[zio.blocks.streams.Stream.run]]. The 4th constructor arg
 * (`writableStackTrace=false`) disables stack trace capture for performance.
 */
final class StreamError(val value: Any) extends Exception(null, null, true, false)
