package zio.blocks.schema

/**
 * JVM-specific platform implementation.
 */
trait PlatformSpecific extends Platform {
  override val isJVM: Boolean    = true
  override val isJS: Boolean     = false
  override val isNative: Boolean = false
  override val name: String      = "JVM"
}
