package zio.blocks.schema.toon

/**
 * Platform-specific utilities for Scala Native.
 */
private[toon] object PlatformSpecific {

  /**
   * Convert BigDecimal to plain string without scientific notation. On Scala
   * Native, both toPlainString and toString have issues with large numbers
   * (either zeroing out the integer part or using scientific notation), so we
   * implement custom formatting.
   */
  def bigDecimalToPlainString(bd: java.math.BigDecimal): String = {
    val str = bd.toString
    // If it contains 'E' or 'e', it's in scientific notation - expand it
    if (str.contains('E') || str.contains('e')) {
      expandScientificNotation(str)
    } else {
      str
    }
  }

  private def expandScientificNotation(scientificStr: String): String = {
    // Parse scientific notation: coefficient E exponent
    val parts = scientificStr.toUpperCase.split('E')
    if (parts.length != 2) return scientificStr

    val coefficient = parts(0)
    val exponent    = parts(1).toInt

    // Handle the coefficient's decimal point
    val dotIndex   = coefficient.indexOf('.')
    val hasDecimal = dotIndex >= 0

    if (!hasDecimal) {
      // No decimal point - just append zeros
      if (exponent >= 0) {
        coefficient + ("0" * exponent)
      } else {
        // Negative exponent - add decimal point and leading zeros
        "0." + ("0" * (-exponent - 1)) + coefficient
      }
    } else {
      // Has decimal point - shift it
      val digits     = coefficient.replace(".", "").replace("-", "")
      val isNegative = coefficient.startsWith("-")
      val newDotPos  = dotIndex + exponent

      if (newDotPos <= 0) {
        // Decimal point moves left of all digits
        val prefix = if (isNegative) "-0." else "0."
        prefix + ("0" * -newDotPos) + digits
      } else if (newDotPos >= digits.length) {
        // Decimal point moves right of all digits
        val prefix = if (isNegative) "-" else ""
        prefix + digits + ("0" * (newDotPos - digits.length))
      } else {
        // Decimal point is within the digits
        val prefix = if (isNegative) "-" else ""
        prefix + digits.substring(0, newDotPos) + "." + digits.substring(newDotPos)
      }
    }
  }

}
