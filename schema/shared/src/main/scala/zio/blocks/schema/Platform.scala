package zio.blocks.schema

/**
 * Platform detection for zio-blocks-schema.
 * 
 * This trait provides compile-time information about the current runtime platform.
 * Some features (like structural types) require reflection APIs that
 * are only available on the JVM.
 * 
 * Platform-specific implementations are provided in:
 * - jvm/src/main/scala/zio/blocks/schema/PlatformSpecific.scala
 * - js/src/main/scala/zio/blocks/schema/PlatformSpecific.scala
 * - native/src/main/scala/zio/blocks/schema/PlatformSpecific.scala
 */
trait Platform {
  /** Whether this is the JVM platform */
  def isJVM: Boolean
  
  /** Whether this is the JavaScript platform */
  def isJS: Boolean
  
  /** Whether this is the Native platform */
  def isNative: Boolean
  
  /** Human-readable name of the platform */
  def name: String
  
  /** Whether reflection APIs are available on this platform */
  final def supportsReflection: Boolean = isJVM
}

object Platform extends PlatformSpecific

