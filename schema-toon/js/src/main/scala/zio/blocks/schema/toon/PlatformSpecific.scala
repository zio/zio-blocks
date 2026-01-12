package zio.blocks.schema.toon

/**
 * Platform-specific utilities for Scala.js.
 */
private[toon] object PlatformSpecific {

  /**
   * Convert BigDecimal to plain string without scientific notation. On
   * Scala.js, toPlainString works correctly for all values.
   */
  def bigDecimalToPlainString(bd: java.math.BigDecimal): String =
    bd.toPlainString

}
