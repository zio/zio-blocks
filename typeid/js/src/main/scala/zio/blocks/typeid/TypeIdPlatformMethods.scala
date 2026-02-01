package zio.blocks.typeid

import zio.blocks.chunk.Chunk

/**
 * JS-specific implementation of TypeId platform methods.
 *
 * Returns None for all reflection operations since JavaScript does not support
 * Java reflection.
 */
private[typeid] object TypeIdPlatformMethods {
  def getClass(id: TypeId[_]): Option[Class[_]] = None

  def construct(@annotation.unused id: TypeId[_], @annotation.unused args: Chunk[AnyRef]): Either[String, Any] =
    Left("Reflective construction is only supported on the JVM platform")
}
