package zio.blocks.schema

/**
 * Native-specific platform implementation.
 */
trait PlatformSpecific extends Platform {
  override val isJVM: Boolean    = false
  override val isJS: Boolean     = false
  override val isNative: Boolean = true
  override val name: String      = "Native"
}
