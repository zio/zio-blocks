package zio.blocks.schema.toon

/**
 * Platform-specific utilities for JVM.
 */
private[toon] object PlatformSpecific {

  /**
   * Convert BigDecimal to plain string without scientific notation. On JVM,
   * toPlainString works correctly for all values.
   */
  def bigDecimalToPlainString(bd: java.math.BigDecimal): String =
    bd.toPlainString

}
