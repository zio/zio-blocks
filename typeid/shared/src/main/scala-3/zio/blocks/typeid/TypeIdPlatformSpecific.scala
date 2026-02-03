package zio.blocks.typeid

import zio.blocks.chunk.Chunk

/**
 * Platform-specific methods for TypeId.
 *
 * On JVM, provides reflection-based capabilities. On JS, returns None for
 * reflection operations.
 */
trait TypeIdPlatformSpecific { self: TypeId[?] =>

  /**
   * Returns the `Class` associated with this TypeId, if available.
   *
   * On the JVM, for nominal types, this returns the `Class` object
   * corresponding to this type's fullName. On JS, always returns `None`.
   *
   * Note: This only works for nominal types (not aliases or opaque types). For
   * generic types, returns the erased class (e.g., `List[Int]` returns
   * `classOf[List[_]]`).
   */
  def clazz: Option[Class[?]]

  /**
   * Constructs an instance of the type represented by this TypeId using the
   * provided arguments.
   *
   * On the JVM, this uses reflection to invoke the primary constructor. For
   * known types (collections, java.time, etc.), optimized construction is used.
   *
   * On JS, always returns `Left` with an error message.
   *
   * @param args
   *   the constructor arguments
   * @return
   *   Right with the constructed instance, or Left with an error message
   */
  def construct(args: Chunk[AnyRef]): Either[String, Any]
}
