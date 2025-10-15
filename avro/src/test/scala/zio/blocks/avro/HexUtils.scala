package zio.blocks.avro

object HexUtils {
  private[this] val header    = "+----------+-------------------------------------------------+------------------+"
  private[this] val colTitles = "|          |  0  1  2  3  4  5  6  7  8  9  a  b  c  d  e  f | 0123456789abcdef |"

  def hexDump(bytes: Array[Byte]): String = {
    val sb            = new StringBuilder
    val lineSeparator = System.lineSeparator()
    sb.append(header)
      .append(lineSeparator)
      .append(colTitles)
      .append(lineSeparator)
      .append(header)
      .append(lineSeparator)
    bytes.grouped(16).zipWithIndex.foreach { case (chunk, rowIndex) =>
      val offset    = f"${rowIndex * 16}%08x"
      val hexPart   = chunk.map(b => f"$b%02x").mkString(" ")
      val paddedHex = f"$hexPart%-47s"
      val asciiPart = chunk.map { byte =>
        val char = byte.toChar
        if (char >= 32 && char <= 126) char else '.'
      }.mkString
      val paddedAscii = f"$asciiPart%-16s"
      sb.append(f"| $offset | $paddedHex | $paddedAscii |")
        .append(lineSeparator)
    }

    sb.append(header)
      .append(lineSeparator)
      .toString
  }
}
