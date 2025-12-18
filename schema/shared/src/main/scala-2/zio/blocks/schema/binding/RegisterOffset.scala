package zio.blocks.schema.binding

object RegisterOffset {
  type RegisterOffset = Int

  val Zero: RegisterOffset = 0

  def apply(
    booleans: Int = 0,
    bytes: Int = 0,
    shorts: Int = 0,
    ints: Int = 0,
    longs: Int = 0,
    floats: Int = 0,
    doubles: Int = 0,
    chars: Int = 0,
    objects: Int = 0
  ): RegisterOffset = {
    val primitives = booleans + bytes + ((chars + shorts) << 1) + ((floats + ints) << 2) + ((doubles + longs) << 3)
    if ((primitives | objects) >> 16 != 0) {
      throw new IllegalArgumentException("offset overflow")
    }
    primitives << 16 | objects
  }

  def add(left: RegisterOffset, right: RegisterOffset): RegisterOffset = {
    if ((getBytes(left) + getBytes(right) | getObjects(left) + getObjects(right)) >> 16 != 0) {
      throw new IllegalArgumentException("offset sum overflow")
    }
    left + right
  }

  private[schema] def getObjects(offset: RegisterOffset): Int = offset & 0xffff

  private[schema] def getBytes(offset: RegisterOffset): Int = offset >>> 16

  private[schema] def incrementObjects(offset: RegisterOffset): RegisterOffset = offset + 1

  private[schema] def incrementBooleansAndBytes(offset: RegisterOffset): RegisterOffset = offset + 0x10000

  private[schema] def incrementCharsAndShorts(offset: RegisterOffset): RegisterOffset = offset + 0x20000

  private[schema] def incrementFloatsAndInts(offset: RegisterOffset): RegisterOffset = offset + 0x40000

  private[schema] def incrementDoublesAndLongs(offset: RegisterOffset): RegisterOffset = offset + 0x80000
}
